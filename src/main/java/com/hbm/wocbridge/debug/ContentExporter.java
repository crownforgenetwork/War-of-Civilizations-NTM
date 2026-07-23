package com.hbm.wocbridge.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.hbm.entity.EntityMappings;
import com.hbm.entity.ModEntityList;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.itempool.ItemPool;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;
import com.hbm.util.Tuple.Quartet;
import com.hbm.world.gen.nbt.NBTStructure;
import com.hbm.world.gen.nbt.SpawnCondition;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

public final class ContentExporter {

	private static final String MOD_PREFIX = RefStrings.MODID + ":";
	private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ContentExporter() { }

	public static ExportSummary export() throws IOException {
		File outputDirectory = resolveOutputDirectory();
		Files.createDirectories(outputDirectory.toPath());
		ContentEnrichment enrichment = createEnrichment();
		ServerVariantIndex serverVariants = collectServerVariants();

		MainRegistry.logger.info("Starting WOC content inventory export to {}", outputDirectory.getAbsolutePath());

		List<Report> reports = new ArrayList<Report>();
		reports.add(exportItems(enrichment, serverVariants));
		reports.add(exportBlocks(enrichment, serverVariants));
		reports.add(exportEntities());
		reports.add(exportCraftingRecipes());
		reports.add(exportSmeltingRecipes());
		reports.add(exportMachineRecipes());
		reports.add(exportCreativeTabs(enrichment));
		reports.add(exportWorldgenFeatures());
		reports.add(exportStructureLoot(enrichment));
		reports.add(exportFluids());

		List<ProfileEntry> profileEntries = new ArrayList<ProfileEntry>();
		LinkedHashMap<String, Integer> counts = new LinkedHashMap<String, Integer>();

		for(Report report : reports) {
			Collections.sort(report.rows, Row.ORDERING);
			writeCsv(new File(outputDirectory, report.fileName), report.header, report.rows);
			counts.put(report.fileName, report.rows.size());
			for(Row row : report.rows) {
				profileEntries.add(new ProfileEntry(row.values[0], report.kind));
			}
			MainRegistry.logger.info("WOC content export wrote {} rows to {}",
					report.rows.size(), new File(outputDirectory, report.fileName).getAbsolutePath());
		}

		Collections.sort(profileEntries, ProfileEntry.ORDERING);
		writeProfileSkeleton(new File(outputDirectory, "content_profile_skeleton.json"), profileEntries);
		counts.put("content_profile_skeleton.json", profileEntries.size());
		MainRegistry.logger.info("WOC content export wrote {} entries to {}",
				profileEntries.size(), new File(outputDirectory, "content_profile_skeleton.json").getAbsolutePath());

		ExportSummary summary = new ExportSummary(outputDirectory, counts);
		MainRegistry.logger.info("WOC content inventory export complete: {} rows across {} reports in {}",
				summary.getTotalRows(), counts.size(), outputDirectory.getAbsolutePath());
		String enrichmentSummary = enrichment.getSummary();
		if(!enrichmentSummary.isEmpty()) {
			MainRegistry.logger.info("WOC content export enrichment summary: {}", enrichmentSummary);
		}
		return summary;
	}

	private static Report exportItems(ContentEnrichment enrichment,
			ServerVariantIndex serverVariants) {
		Report report = new Report(
				"items.csv",
				"ITEM",
				"content_key", "registry_name", "metadata", "java_class", "unlocalized_name",
				"display_name", "creative_tab", "source_category", "inferred_family");

		for(Object object : Item.itemRegistry) {
			if(!(object instanceof Item)) continue;
			Item item = (Item) object;
			String registryName = registryName(item);
			if(!isHbmRegistryName(registryName)) continue;

			for(ItemStack stack : enumerateItemVariants(item, enrichment, serverVariants)) {
				int metadata = stack.getItemDamage();
				String contentKey = "item:" + registryName + "#" + metadata;
				report.add(contentKey, registryName, Integer.toString(metadata), item.getClass().getName(),
						safeUnlocalizedName(stack), enrichment.getItemDisplayName(stack),
						enrichment.getItemCreativeTabLabel(item),
						sourceCategory(item.getClass()), inferredFamily(registryName));
			}
		}
		return report;
	}

	private static Report exportBlocks(ContentEnrichment enrichment,
			ServerVariantIndex serverVariants) {
		Report report = new Report(
				"blocks.csv",
				"BLOCK",
				"content_key", "registry_name", "metadata", "java_class", "unlocalized_name",
				"display_name", "creative_tab", "source_category", "inferred_family");

		for(Object object : Block.blockRegistry) {
			if(!(object instanceof Block)) continue;
			Block block = (Block) object;
			String registryName = registryName(block);
			if(!isHbmRegistryName(registryName)) continue;

			Item item = Item.getItemFromBlock(block);
			List<ItemStack> variants = item == null
					? Collections.<ItemStack>emptyList()
					: enumerateItemVariants(item, enrichment, serverVariants);

			if(variants.isEmpty()) {
				String contentKey = "block:" + registryName + "#0";
				report.add(contentKey, registryName, "0", block.getClass().getName(),
						safeBlockUnlocalizedName(block), enrichment.getBlockDisplayName(block),
						enrichment.getBlockCreativeTabLabel(block), sourceCategory(block.getClass()),
						inferredFamily(registryName));
			} else {
				for(ItemStack stack : variants) {
					int metadata = stack.getItemDamage();
					String contentKey = "block:" + registryName + "#" + metadata;
					report.add(contentKey, registryName, Integer.toString(metadata), block.getClass().getName(),
							safeUnlocalizedName(stack), enrichment.getItemDisplayName(stack),
							enrichment.getItemCreativeTabLabel(item), sourceCategory(block.getClass()),
							inferredFamily(registryName));
				}
			}
		}
		return report;
	}

	private static Report exportEntities() {
		Report report = new Report(
				"entities.csv",
				"ENTITY",
				"content_key", "registry_name", "registration_name", "mod_entity_id", "entity_kind",
				"java_class", "tracking_range", "velocity_updates", "egg_primary_color",
				"egg_secondary_color", "source_category");

		int id = 0;
		for(Quartet<Class<? extends Entity>, String, Integer, Boolean> mapping : EntityMappings.entityMappings) {
			ModEntityList.EntityData data = ModEntityList.getData(id);
			String registeredName = data == null ? mapping.getX() : data.name;
			String registryName = ModEntityList.getName(id);
			if(registryName == null) registryName = RefStrings.MODID + "." + registeredName;
			String contentKey = "entity:" + registryName + "#" + id;
			report.add(contentKey, registryName, registeredName, Integer.toString(id), "ENTITY",
					mapping.getW().getName(), Integer.toString(mapping.getY()),
					Boolean.toString(mapping.getZ()), "", "", sourceCategory(mapping.getW()));
			id++;
		}

		for(Quartet<Class<? extends Entity>, String, Integer, Integer> mapping : EntityMappings.mobMappings) {
			ModEntityList.EntityData data = ModEntityList.getData(id);
			String registeredName = data == null ? mapping.getX() : data.name;
			String registryName = ModEntityList.getName(id);
			if(registryName == null) registryName = RefStrings.MODID + "." + registeredName;
			String contentKey = "entity:" + registryName + "#" + id;
			report.add(contentKey, registryName, registeredName, Integer.toString(id), "MOB",
					mapping.getW().getName(), "80", "true", color(mapping.getY()), color(mapping.getZ()),
					sourceCategory(mapping.getW()));
			id++;
		}
		return report;
	}

	@SuppressWarnings("unchecked")
	private static Report exportCraftingRecipes() {
		Report report = new Report(
				"crafting_recipes.csv",
				"CRAFTING_RECIPE",
				"content_key", "recipe_class", "output_registry_name", "output_metadata",
				"output_count", "normalized_inputs", "recipe_manager", "status");

		List<RecipeRow> recipeRows = new ArrayList<RecipeRow>();
		for(Object object : (List<Object>) CraftingManager.getInstance().getRecipeList()) {
			if(!(object instanceof IRecipe)) continue;
			IRecipe recipe = (IRecipe) object;
			ItemStack output;
			try {
				output = recipe.getRecipeOutput();
			} catch(Throwable ex) {
				continue;
			}
			if(output == null || !isHbmRegistryName(registryName(output))) continue;

			String normalizedInputs = normalizeCraftingInputs(recipe);
			String status = normalizedInputs.startsWith("unserializable:")
					? normalizedInputs
					: "OK";
			recipeRows.add(new RecipeRow(
					"crafting-recipe",
					recipe.getClass().getName(),
					registryName(output),
					output.getItemDamage(),
					output.stackSize,
					normalizedInputs,
					"minecraft:crafting",
					status));
		}

		addRecipeRows(report, recipeRows);
		return report;
	}

	@SuppressWarnings("unchecked")
	private static Report exportSmeltingRecipes() {
		Report report = new Report(
				"smelting_recipes.csv",
				"SMELTING_RECIPE",
				"content_key", "recipe_class", "output_registry_name", "output_metadata",
				"output_count", "normalized_inputs", "recipe_manager", "experience", "status");

		List<SmeltingRow> rows = new ArrayList<SmeltingRow>();
		Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.smelting().getSmeltingList();
		for(Map.Entry<ItemStack, ItemStack> entry : smeltingList.entrySet()) {
			ItemStack input = entry.getKey();
			ItemStack output = entry.getValue();
			if(input == null || output == null) continue;
			if(!isHbmRegistryName(registryName(input)) && !isHbmRegistryName(registryName(output))) continue;

			String normalizedInput = normalizeIngredient(input);
			String fingerprint = "smelting\n" + normalizeStack(output) + "\n" + normalizedInput;
			String contentKey = "smelting-recipe:" + keyOutput(output) + ":" + sha256(fingerprint);
			float experience = FurnaceRecipes.smelting().func_151398_b(output);
			rows.add(new SmeltingRow(
					contentKey,
					FurnaceRecipes.class.getName(),
					registryName(output),
					Integer.toString(output.getItemDamage()),
					Integer.toString(output.stackSize),
					normalizedInput,
					"minecraft:furnace",
					String.format(Locale.US, "%.6f", experience),
					"OK"));
		}

		Collections.sort(rows, SmeltingRow.ORDERING);
		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		for(SmeltingRow row : rows) {
			String key = withOccurrence(row.contentKey, occurrences);
			report.add(key, row.recipeClass, row.outputRegistry, row.outputMetadata, row.outputCount,
					row.normalizedInputs, row.manager, row.experience, row.status);
		}
		return report;
	}

	private static Report exportMachineRecipes() {
		Report report = new Report(
				"machine_recipes.csv",
				"MACHINE_RECIPE",
				"content_key", "recipe_class", "recipe_object_class", "output_registry_name",
				"output_metadata", "output_count", "normalized_inputs", "recipe_manager",
				"machine_family", "normalized_recipe", "status");

		List<MachineRow> rows = new ArrayList<MachineRow>();
		List<SerializableRecipe> handlers = new ArrayList<SerializableRecipe>(SerializableRecipe.recipeHandlers);
		Collections.sort(handlers, new Comparator<SerializableRecipe>() {
			@Override
			public int compare(SerializableRecipe left, SerializableRecipe right) {
				int fileCompare = safe(left.getFileName()).compareTo(safe(right.getFileName()));
				if(fileCompare != 0) return fileCompare;
				return left.getClass().getName().compareTo(right.getClass().getName());
			}
		});

		for(SerializableRecipe handler : handlers) {
			Object recipeContainer;
			try {
				recipeContainer = handler.getRecipeObject();
			} catch(Throwable ex) {
				rows.add(machineFallback(handler, null, ex));
				continue;
			}

			List<Object> recipeObjects = flattenRecipeContainer(recipeContainer);
			if(recipeObjects == null) {
				rows.add(machineFallback(handler, recipeContainer,
						new IllegalArgumentException("Unsupported recipe container")));
				continue;
			}

			for(Object recipeObject : recipeObjects) {
				try {
					JsonObject serialized = serializeMachineRecipe(handler, recipeObject);
					List<JsonObject> flattened = flattenNestedMachineRecipes(serialized);
					for(JsonObject recipeJson : flattened) {
						String canonical = canonicalJson(recipeJson);
						List<OutputStack> outputs = findOutputStacks(recipeJson);
						String normalizedInputs = findNormalizedInputs(recipeJson);
						rows.add(new MachineRow(
								handler.getClass().getName(),
								className(recipeObject),
								joinOutputField(outputs, OutputField.REGISTRY),
								joinOutputField(outputs, OutputField.METADATA),
								joinOutputField(outputs, OutputField.COUNT),
								normalizedInputs,
								safe(handler.getFileName()),
								machineFamily(handler),
								canonical,
								"OK"));
					}
				} catch(Throwable ex) {
					rows.add(machineFallback(handler, recipeObject, ex));
				}
			}
		}

		Collections.sort(rows, MachineRow.ORDERING);
		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		for(MachineRow row : rows) {
			String fingerprint = row.recipeClass + "\n" + row.recipeObjectClass + "\n"
					+ row.manager + "\n" + row.normalizedRecipe;
			String outputKey = row.outputRegistry.isEmpty()
					? row.machineFamily
					: row.outputRegistry.replace(';', '+');
			String baseKey = "machine-recipe:" + outputKey + ":" + sha256(fingerprint);
			String contentKey = withOccurrence(baseKey, occurrences);
			report.add(contentKey, row.recipeClass, row.recipeObjectClass, row.outputRegistry,
					row.outputMetadata, row.outputCount, row.normalizedInputs, row.manager,
					row.machineFamily, row.normalizedRecipe, row.status);
		}
		return report;
	}

	private static Report exportCreativeTabs(ContentEnrichment enrichment) {
		Report report = new Report(
				"creative_tabs.csv",
				"CREATIVE_TAB",
				"content_key", "tab_label", "translated_label", "java_class", "icon_registry_name",
				"registered_item_count", "source_category");

		for(CreativeTabData tab : enrichment.getCreativeTabs()) {
			report.add("creative-tab:" + tab.label, tab.label, tab.translatedLabel,
					tab.javaClass, tab.iconRegistryName,
					Integer.toString(tab.registeredItemCount), tab.sourceCategory);
		}
		return report;
	}

	private static Report exportWorldgenFeatures() {
		Report report = new Report(
				"worldgen_features.csv",
				"WORLDGEN_FEATURE",
				"content_key", "feature_name", "feature_type", "spawn_weight", "size_limit",
				"range_limit", "min_height", "max_height", "has_biome_predicate",
				"has_coordinate_predicate", "jigsaw_pool_count", "source_class");

		for(String name : NBTStructure.listStructures()) {
			SpawnCondition spawn = NBTStructure.getStructure(name);
			if(spawn == null) continue;
			String type;
			if(spawn.structure != null) {
				type = "NBT_STRUCTURE";
			} else if(spawn.start != null) {
				type = "COMPONENT_STRUCTURE";
			} else if(spawn.pools != null) {
				type = "JIGSAW_STRUCTURE";
			} else {
				type = "REGISTERED_STRUCTURE";
			}
			int poolCount = spawn.pools == null ? 0 : spawn.pools.size();
			report.add("worldgen:" + name, name, type, Integer.toString(spawn.spawnWeight),
					Integer.toString(spawn.sizeLimit), Integer.toString(spawn.rangeLimit),
					Integer.toString(spawn.minHeight), Integer.toString(spawn.maxHeight),
					Boolean.toString(spawn.canSpawn != null), Boolean.toString(spawn.checkCoordinates != null),
					Integer.toString(poolCount), spawn.getClass().getName());
		}
		return report;
	}

	private static Report exportStructureLoot(ContentEnrichment enrichment) {
		Report report = new Report(
				"structure_loot.csv",
				"STRUCTURE_LOOT",
				"content_key", "pool_name", "item_registry_name", "metadata", "minimum_count",
				"maximum_count", "weight", "nbt", "display_name", "source_category");

		List<LootRow> rows = new ArrayList<LootRow>();
		for(Map.Entry<String, ItemPool> poolEntry : ItemPool.pools.entrySet()) {
			ItemPool pool = poolEntry.getValue();
			if(pool == null || pool.pool == null) continue;
			for(WeightedRandomChestContent content : pool.pool) {
				if(content == null || content.theItemId == null) continue;
				ItemStack stack = content.theItemId;
				String nbt = stack.hasTagCompound() ? stack.stackTagCompound.toString() : "";
				String fingerprint = poolEntry.getKey() + "\n" + normalizeStack(stack) + "\n"
						+ content.theMinimumChanceToGenerateItem + "\n"
						+ content.theMaximumChanceToGenerateItem + "\n" + content.itemWeight;
				String contentKey = "loot:" + poolEntry.getKey() + ":" + keyOutput(stack)
						+ ":" + sha256(fingerprint);
				rows.add(new LootRow(contentKey, poolEntry.getKey(), registryName(stack),
						Integer.toString(stack.getItemDamage()),
						Integer.toString(content.theMinimumChanceToGenerateItem),
						Integer.toString(content.theMaximumChanceToGenerateItem),
						Integer.toString(content.itemWeight), nbt,
						enrichment.getItemDisplayName(stack),
						sourceCategory(stack.getItem().getClass())));
			}
		}

		Collections.sort(rows, LootRow.ORDERING);
		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		for(LootRow row : rows) {
			report.add(withOccurrence(row.contentKey, occurrences), row.poolName, row.registryName,
					row.metadata, row.minimumCount, row.maximumCount, row.weight, row.nbt,
					row.displayName, row.sourceCategory);
		}
		return report;
	}

	private static Report exportFluids() {
		Report report = new Report(
				"fluids.csv",
				"FLUID",
				"content_key", "fluid_name", "numeric_id", "java_class", "unlocalized_name",
				"conditional_name", "temperature", "color", "traits", "source_category");

		FluidType[] fluids = Fluids.getAll();
		if(fluids == null) return report;
		for(FluidType fluid : fluids) {
			if(fluid == null) continue;
			List<String> traits = new ArrayList<String>();
			for(Class<?> trait : fluid.traits.keySet()) {
				traits.add(trait.getName());
			}
			Collections.sort(traits);

			String category = "built_in";
			if(Fluids.customFluids.contains(fluid)) category = "custom";
			if(Fluids.foreignFluids.contains(fluid)) category = "foreign";

			report.add("fluid:" + fluid.getName(), fluid.getName(), Integer.toString(fluid.getID()),
					fluid.getClass().getName(), fluid.getUnlocalizedName(), fluid.getConditionalName(),
					Integer.toString(fluid.temperature), String.format(Locale.US, "#%06X", fluid.getColor() & 0xFFFFFF),
					join(traits, ";"), category);
		}
		return report;
	}

	private static void addRecipeRows(Report report, List<RecipeRow> rows) {
		Collections.sort(rows, RecipeRow.ORDERING);
		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		for(RecipeRow row : rows) {
			String fingerprint = row.recipeClass + "\n" + row.outputRegistry + "\n"
					+ row.outputMetadata + "\n" + row.outputCount + "\n" + row.normalizedInputs;
			String baseKey = row.kind + ":" + row.outputRegistry + "#" + row.outputMetadata
					+ ":" + sha256(fingerprint);
			String contentKey = withOccurrence(baseKey, occurrences);
			report.add(contentKey, row.recipeClass, row.outputRegistry,
					Integer.toString(row.outputMetadata), Integer.toString(row.outputCount),
					row.normalizedInputs, row.manager, row.status);
		}
	}

	private static String normalizeCraftingInputs(IRecipe recipe) {
		try {
			if(recipe instanceof ShapedRecipes) {
				ShapedRecipes shaped = (ShapedRecipes) recipe;
				return normalizeShaped(Arrays.asList(shaped.recipeItems));
			}
			if(recipe instanceof ShapelessRecipes) {
				ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
				return normalizeShapeless(shapeless.recipeItems);
			}
			if(recipe instanceof ShapedOreRecipe) {
				return normalizeShaped(Arrays.asList(((ShapedOreRecipe) recipe).getInput()));
			}
			if(recipe instanceof ShapelessOreRecipe) {
				return normalizeShapeless(((ShapelessOreRecipe) recipe).getInput());
			}
		} catch(Throwable ex) {
			return "unserializable:" + recipe.getClass().getName();
		}
		return "unserializable:" + recipe.getClass().getName();
	}

	private static String normalizeShaped(Collection<?> ingredients) {
		List<String> normalized = new ArrayList<String>();
		for(Object ingredient : ingredients) {
			normalized.add(normalizeIngredient(ingredient));
		}
		return "[" + join(normalized, ";") + "]";
	}

	private static String normalizeShapeless(Collection<?> ingredients) {
		List<String> normalized = new ArrayList<String>();
		for(Object ingredient : ingredients) {
			normalized.add(normalizeIngredient(ingredient));
		}
		Collections.sort(normalized);
		return "[" + join(normalized, ";") + "]";
	}

	private static String normalizeIngredient(Object ingredient) {
		if(ingredient == null) return "empty";
		if(ingredient instanceof ItemStack) return normalizeStack((ItemStack) ingredient);
		if(ingredient instanceof Item) return registryName((Item) ingredient) + "#0x1";
		if(ingredient instanceof Block) return registryName((Block) ingredient) + "#0x1";
		if(ingredient instanceof String) return "ore:" + ingredient;
		if(ingredient instanceof Collection) {
			List<String> alternatives = new ArrayList<String>();
			for(Object alternative : (Collection<?>) ingredient) {
				alternatives.add(normalizeIngredient(alternative));
			}
			Collections.sort(alternatives);
			return "anyOf(" + join(alternatives, "|") + ")";
		}
		if(ingredient.getClass().isArray()) {
			List<String> values = new ArrayList<String>();
			for(int index = 0; index < Array.getLength(ingredient); index++) {
				values.add(normalizeIngredient(Array.get(ingredient, index)));
			}
			return "[" + join(values, ";") + "]";
		}
		return "unserializable:" + ingredient.getClass().getName();
	}

	private static String normalizeStack(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return "empty";
		StringBuilder value = new StringBuilder();
		value.append(registryName(stack)).append('#').append(stack.getItemDamage())
				.append('x').append(stack.stackSize);
		if(stack.hasTagCompound()) value.append("{nbt=").append(stack.stackTagCompound.toString()).append('}');
		return value.toString();
	}

	private static ContentEnrichment createEnrichment() {
		if(!FMLCommonHandler.instance().getSide().isClient()) {
			return new ServerContentEnrichment("");
		}
		try {
			Class<?> helperClass = Class.forName(
					"com.hbm.wocbridge.debug.ContentExporterClientEnrichment",
					true, ContentExporter.class.getClassLoader());
			return (ContentEnrichment) helperClass.newInstance();
		} catch(Throwable ex) {
			MainRegistry.logger.warn("WOC client-only export enrichment is unavailable; "
					+ "continuing with server-safe fields only: {}", ex.toString());
			return new ServerContentEnrichment(ex.getClass().getName());
		}
	}

	@SuppressWarnings("unchecked")
	private static ServerVariantIndex collectServerVariants() {
		ServerVariantIndex variants = new ServerVariantIndex();
		for(Object object : Item.itemRegistry) {
			if(object instanceof Item) variants.add(new ItemStack((Item) object, 1, 0));
		}

		for(Object object : (List<Object>) CraftingManager.getInstance().getRecipeList()) {
			if(!(object instanceof IRecipe)) continue;
			IRecipe recipe = (IRecipe) object;
			try {
				variants.add(recipe.getRecipeOutput());
				if(recipe instanceof ShapedRecipes) {
					variants.addAll(Arrays.asList(((ShapedRecipes) recipe).recipeItems));
				} else if(recipe instanceof ShapelessRecipes) {
					variants.addAll(((ShapelessRecipes) recipe).recipeItems);
				} else if(recipe instanceof ShapedOreRecipe) {
					variants.addAll(Arrays.asList(((ShapedOreRecipe) recipe).getInput()));
				} else if(recipe instanceof ShapelessOreRecipe) {
					variants.addAll(((ShapelessOreRecipe) recipe).getInput());
				}
			} catch(Throwable ex) { }
		}

		try {
			Map<ItemStack, ItemStack> smeltingList =
					FurnaceRecipes.smelting().getSmeltingList();
			for(Map.Entry<ItemStack, ItemStack> entry : smeltingList.entrySet()) {
				variants.add(entry.getKey());
				variants.add(entry.getValue());
			}
		} catch(Throwable ex) { }

		for(ItemPool pool : ItemPool.pools.values()) {
			if(pool == null || pool.pool == null) continue;
			for(WeightedRandomChestContent content : pool.pool) {
				if(content != null) variants.add(content.theItemId);
			}
		}
		return variants;
	}

	private static List<ItemStack> enumerateItemVariants(Item item,
			ContentEnrichment enrichment, ServerVariantIndex serverVariants) {
		TreeMap<Integer, ItemStack> byMetadata = new TreeMap<Integer, ItemStack>();
		if(enrichment.hasClientEnrichment()) {
			enrichment.addItemVariants(item, byMetadata);
		}
		if(byMetadata.isEmpty()) {
			for(ItemStack stack : serverVariants.get(item)) {
				int metadata = stack.getItemDamage();
				if(!byMetadata.containsKey(metadata)) {
					byMetadata.put(metadata, stack.copy());
				}
			}
		}
		if(byMetadata.isEmpty()) byMetadata.put(0, new ItemStack(item, 1, 0));
		return new ArrayList<ItemStack>(byMetadata.values());
	}

	private static JsonObject serializeMachineRecipe(SerializableRecipe handler, Object recipe) throws IOException {
		StringWriter stringWriter = new StringWriter();
		JsonWriter writer = new JsonWriter(stringWriter);
		writer.beginObject();
		handler.writeRecipe(recipe, writer);
		writer.endObject();
		writer.close();
		JsonElement parsed = new JsonParser().parse(stringWriter.toString());
		if(!parsed.isJsonObject()) {
			throw new IOException("Recipe serializer did not produce an object");
		}
		return parsed.getAsJsonObject();
	}

	private static List<JsonObject> flattenNestedMachineRecipes(JsonObject serialized) {
		if(serialized.has("recipeKey") && serialized.has("recipes")
				&& serialized.get("recipes").isJsonArray()) {
			List<JsonObject> flattened = new ArrayList<JsonObject>();
			String recipeKey = serialized.get("recipeKey").getAsString();
			for(JsonElement nested : serialized.getAsJsonArray("recipes")) {
				JsonObject wrapper = new JsonObject();
				wrapper.addProperty("recipeKey", recipeKey);
				wrapper.add("recipe", nested);
				flattened.add(wrapper);
			}
			if(!flattened.isEmpty()) return flattened;
		}
		return Collections.singletonList(serialized);
	}

	private static List<Object> flattenRecipeContainer(Object container) {
		if(container == null) return new ArrayList<Object>();
		List<Object> values = new ArrayList<Object>();
		if(container instanceof Collection) {
			values.addAll((Collection<?>) container);
			return values;
		}
		if(container instanceof Map) {
			values.addAll(((Map<?, ?>) container).entrySet());
			return values;
		}
		if(container.getClass().isArray()) {
			for(int index = 0; index < Array.getLength(container); index++) {
				values.add(Array.get(container, index));
			}
			return values;
		}
		return null;
	}

	private static MachineRow machineFallback(SerializableRecipe handler, Object recipeObject, Throwable ex) {
		String marker = "unserializable:" + ex.getClass().getName();
		return new MachineRow(
				handler.getClass().getName(),
				className(recipeObject),
				"",
				"",
				"",
				marker,
				safe(handler.getFileName()),
				machineFamily(handler),
				"{\"fallback\":\"" + jsonEscape(marker) + "\"}",
				marker);
	}

	private static String findNormalizedInputs(JsonElement recipe) {
		List<String> inputs = new ArrayList<String>();
		collectInputFields(recipe, "", inputs);
		Collections.sort(inputs);
		return inputs.isEmpty() ? "unserializable:no-explicit-input-field" : join(inputs, ";");
	}

	private static void collectInputFields(JsonElement element, String path, List<String> inputs) {
		if(element == null || !element.isJsonObject()) return;
		for(Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
			String field = entry.getKey().toLowerCase(Locale.US);
			if(isInputField(field)) {
				inputs.add(childPath + "=" + canonicalJson(entry.getValue()));
			} else if(!isOutputField(field)) {
				collectInputFields(entry.getValue(), childPath, inputs);
			}
		}
	}

	private static boolean isInputField(String field) {
		return field.contains("input") || field.contains("ingredient") || field.contains("catalyst")
				|| field.contains("stamp") || field.equals("fuel");
	}

	private static boolean isOutputField(String field) {
		return field.contains("output") || field.contains("result") || field.contains("payout");
	}

	private static List<OutputStack> findOutputStacks(JsonElement recipe) {
		List<OutputStack> outputs = new ArrayList<OutputStack>();
		collectOutputStacks(recipe, false, false, outputs);
		Collections.sort(outputs, OutputStack.ORDERING);
		return outputs;
	}

	private static void collectOutputStacks(JsonElement element, boolean outputContext,
			boolean chanceStackContext, List<OutputStack> outputs) {
		if(element == null || element.isJsonNull()) return;
		if(element.isJsonObject()) {
			for(Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				String field = entry.getKey().toLowerCase(Locale.US);
				boolean childOutput = outputContext || isOutputField(field);
				boolean childChanceStack = chanceStackContext || "outputitems".equals(field);
				collectOutputStacks(entry.getValue(), childOutput, childChanceStack, outputs);
			}
			return;
		}
		if(!element.isJsonArray()) return;

		JsonArray array = element.getAsJsonArray();
		if(outputContext) {
			OutputStack direct = parseOutputStack(array, chanceStackContext);
			if(direct != null) {
				outputs.add(direct);
				return;
			}
		}
		for(JsonElement child : array) {
			collectOutputStacks(child, outputContext, chanceStackContext, outputs);
		}
	}

	private static OutputStack parseOutputStack(JsonArray array, boolean chanceStack) {
		if(array.size() == 0 || !array.get(0).isJsonPrimitive()) return null;
		JsonPrimitive first = array.get(0).getAsJsonPrimitive();
		if(!first.isString()) return null;
		String type = first.getAsString();
		if("single".equals(type) || "multi".equals(type)) return null;
		if(!type.contains(":")) return null;
		if(!(Item.itemRegistry.getObject(type) instanceof Item)) return null;

		int count = chanceStack ? integerAt(array, array.size() >= 3 ? 1 : -1, 1)
				: integerAt(array, 1, 1);
		int metadata = chanceStack ? integerAt(array, array.size() >= 4 ? 2 : -1, 0)
				: integerAt(array, 2, 0);
		return new OutputStack(type, metadata, count);
	}

	private static int integerAt(JsonArray array, int index, int fallback) {
		if(index < 0 || index >= array.size() || !array.get(index).isJsonPrimitive()) return fallback;
		JsonPrimitive value = array.get(index).getAsJsonPrimitive();
		if(!value.isNumber()) return fallback;
		try {
			return value.getAsInt();
		} catch(NumberFormatException ex) {
			return fallback;
		}
	}

	private static String canonicalJson(JsonElement element) {
		if(element == null || element.isJsonNull()) return "null";
		if(element.isJsonPrimitive()) return element.toString();
		if(element.isJsonArray()) {
			List<String> values = new ArrayList<String>();
			for(JsonElement child : element.getAsJsonArray()) values.add(canonicalJson(child));
			return "[" + join(values, ",") + "]";
		}

		List<String> names = new ArrayList<String>();
		for(Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			names.add(entry.getKey());
		}
		Collections.sort(names);
		List<String> values = new ArrayList<String>();
		for(String name : names) {
			values.add("\"" + jsonEscape(name) + "\":" + canonicalJson(element.getAsJsonObject().get(name)));
		}
		return "{" + join(values, ",") + "}";
	}

	private static String joinOutputField(List<OutputStack> outputs, OutputField field) {
		List<String> values = new ArrayList<String>();
		for(OutputStack output : outputs) {
			switch(field) {
			case REGISTRY:
				values.add(output.registryName);
				break;
			case METADATA:
				values.add(Integer.toString(output.metadata));
				break;
			case COUNT:
				values.add(Integer.toString(output.count));
				break;
			default:
				break;
			}
		}
		return join(values, ";");
	}

	private static String machineFamily(SerializableRecipe handler) {
		String fileName = safe(handler.getFileName());
		if(fileName.toLowerCase(Locale.US).endsWith(".json")) {
			fileName = fileName.substring(0, fileName.length() - 5);
		}
		if(fileName.startsWith("hbm")) fileName = fileName.substring(3);
		return fileName.isEmpty() ? handler.getClass().getSimpleName() : fileName;
	}

	private static File resolveOutputDirectory() throws IOException {
		File workingDirectory = new File(System.getProperty("user.dir", ".")).getCanonicalFile();
		File candidate = workingDirectory;
		for(int depth = 0; depth < 6 && candidate != null; depth++) {
			if(new File(candidate, "build.gradle").isFile()) {
				return new File(candidate, "build" + File.separator + "reports" + File.separator + "woc");
			}
			candidate = candidate.getParentFile();
		}
		return new File(workingDirectory, "build" + File.separator + "reports" + File.separator + "woc");
	}

	private static void writeCsv(File destination, String[] header, List<Row> rows) throws IOException {
		atomicWrite(destination, new ContentWriter() {
			@Override
			public void write(BufferedWriter writer) throws IOException {
				writeCsvLine(writer, header);
				for(Row row : rows) writeCsvLine(writer, row.values);
			}
		});
	}

	private static void writeCsvLine(BufferedWriter writer, String[] values) throws IOException {
		for(int index = 0; index < values.length; index++) {
			if(index > 0) writer.write(',');
			writer.write(csv(values[index]));
		}
		writer.write('\n');
	}

	private static void writeProfileSkeleton(File destination, List<ProfileEntry> entries) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", 1);
		root.addProperty("profileId", "war_of_civilizations_inventory");
		root.addProperty("defaultState", "UNREVIEWED");
		JsonArray content = new JsonArray();
		for(ProfileEntry entry : entries) {
			JsonObject rule = new JsonObject();
			rule.addProperty("key", entry.key);
			rule.addProperty("kind", entry.kind);
			rule.addProperty("state", "UNREVIEWED");
			content.add(rule);
		}
		root.add("entries", content);
		String json = PRETTY_GSON.toJson(root) + "\n";
		atomicWrite(destination, new ContentWriter() {
			@Override
			public void write(BufferedWriter writer) throws IOException {
				writer.write(json);
			}
		});
	}

	private static void atomicWrite(File destination, ContentWriter contentWriter) throws IOException {
		File parent = destination.getParentFile();
		Files.createDirectories(parent.toPath());
		Path temporary = Files.createTempFile(parent.toPath(), "." + destination.getName() + ".", ".tmp");
		boolean moved = false;
		try {
			try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					Files.newOutputStream(temporary), StandardCharsets.UTF_8))) {
				contentWriter.write(writer);
			}
			try {
				Files.move(temporary, destination.toPath(),
						StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch(AtomicMoveNotSupportedException ex) {
				Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			moved = true;
		} finally {
			if(!moved) Files.deleteIfExists(temporary);
		}
	}

	private static String csv(String value) {
		String safeValue = safe(value);
		if(safeValue.indexOf(',') >= 0 || safeValue.indexOf('"') >= 0
				|| safeValue.indexOf('\n') >= 0 || safeValue.indexOf('\r') >= 0) {
			return "\"" + safeValue.replace("\"", "\"\"") + "\"";
		}
		return safeValue;
	}

	private static String withOccurrence(String baseKey, Map<String, Integer> occurrences) {
		Integer previous = occurrences.get(baseKey);
		int occurrence = previous == null ? 1 : previous + 1;
		occurrences.put(baseKey, occurrence);
		return baseKey + ":" + occurrence;
	}

	private static String keyOutput(ItemStack stack) {
		return registryName(stack) + "#" + stack.getItemDamage();
	}

	private static String registryName(ItemStack stack) {
		return stack == null ? "" : registryName(stack.getItem());
	}

	private static String registryName(Item item) {
		if(item == null) return "";
		Object name = Item.itemRegistry.getNameForObject(item);
		return name == null ? "" : name.toString();
	}

	private static String registryName(Block block) {
		if(block == null) return "";
		Object name = Block.blockRegistry.getNameForObject(block);
		return name == null ? "" : name.toString();
	}

	private static boolean isHbmRegistryName(String name) {
		return name != null && name.startsWith(MOD_PREFIX);
	}

	private static String safeUnlocalizedName(ItemStack stack) {
		try {
			return safe(stack.getUnlocalizedName());
		} catch(Throwable ex) {
			return "unavailable:" + ex.getClass().getName();
		}
	}

	private static String safeBlockUnlocalizedName(Block block) {
		try {
			return safe(block.getUnlocalizedName());
		} catch(Throwable ex) {
			return "unavailable:" + ex.getClass().getName();
		}
	}

	private static String sourceCategory(Class<?> type) {
		if(type == null || type.getPackage() == null) return "";
		String packageName = type.getPackage().getName();
		if(packageName.startsWith("com.hbm.")) return packageName.substring("com.hbm.".length());
		return packageName;
	}

	private static String inferredFamily(String registryName) {
		if(registryName == null) return "";
		int namespace = registryName.indexOf(':');
		String path = namespace >= 0 ? registryName.substring(namespace + 1) : registryName;
		int dot = path.lastIndexOf('.');
		if(dot >= 0) path = path.substring(dot + 1);
		int underscore = path.indexOf('_');
		return underscore < 0 ? path : path.substring(0, underscore);
	}

	private static String color(int value) {
		return String.format(Locale.US, "#%06X", value & 0xFFFFFF);
	}

	private static String className(Object object) {
		return object == null ? "" : object.getClass().getName();
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for(byte current : hash) hex.append(String.format(Locale.US, "%02x", current & 0xFF));
			return hex.toString();
		} catch(NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private static String jsonEscape(String value) {
		return new Gson().toJson(safe(value)).replaceFirst("^\"", "").replaceFirst("\"$", "");
	}

	private static String join(Collection<String> values, String delimiter) {
		StringBuilder joined = new StringBuilder();
		for(String value : values) {
			if(joined.length() > 0) joined.append(delimiter);
			joined.append(safe(value));
		}
		return joined.toString();
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private interface ContentWriter {
		void write(BufferedWriter writer) throws IOException;
	}

	private enum OutputField {
		REGISTRY,
		METADATA,
		COUNT
	}

	private static final class Report {
		private final String fileName;
		private final String kind;
		private final String[] header;
		private final List<Row> rows = new ArrayList<Row>();

		private Report(String fileName, String kind, String... header) {
			this.fileName = fileName;
			this.kind = kind;
			this.header = header;
		}

		private void add(String... values) {
			if(values.length != header.length) {
				throw new IllegalArgumentException("Expected " + header.length + " columns for "
						+ fileName + " but received " + values.length);
			}
			rows.add(new Row(values));
		}
	}

	private static final class Row {
		private static final Comparator<Row> ORDERING = new Comparator<Row>() {
			@Override
			public int compare(Row left, Row right) {
				for(int index = 0; index < Math.min(left.values.length, right.values.length); index++) {
					int comparison = safe(left.values[index]).compareTo(safe(right.values[index]));
					if(comparison != 0) return comparison;
				}
				return Integer.compare(left.values.length, right.values.length);
			}
		};

		private final String[] values;

		private Row(String[] values) {
			this.values = values;
		}
	}

	private static final class RecipeRow {
		private static final Comparator<RecipeRow> ORDERING = new Comparator<RecipeRow>() {
			@Override
			public int compare(RecipeRow left, RecipeRow right) {
				int comparison = left.outputRegistry.compareTo(right.outputRegistry);
				if(comparison != 0) return comparison;
				comparison = Integer.compare(left.outputMetadata, right.outputMetadata);
				if(comparison != 0) return comparison;
				comparison = left.recipeClass.compareTo(right.recipeClass);
				if(comparison != 0) return comparison;
				return left.normalizedInputs.compareTo(right.normalizedInputs);
			}
		};

		private final String kind;
		private final String recipeClass;
		private final String outputRegistry;
		private final int outputMetadata;
		private final int outputCount;
		private final String normalizedInputs;
		private final String manager;
		private final String status;

		private RecipeRow(String kind, String recipeClass, String outputRegistry, int outputMetadata,
				int outputCount, String normalizedInputs, String manager, String status) {
			this.kind = kind;
			this.recipeClass = recipeClass;
			this.outputRegistry = outputRegistry;
			this.outputMetadata = outputMetadata;
			this.outputCount = outputCount;
			this.normalizedInputs = normalizedInputs;
			this.manager = manager;
			this.status = status;
		}
	}

	private static final class SmeltingRow {
		private static final Comparator<SmeltingRow> ORDERING = new Comparator<SmeltingRow>() {
			@Override
			public int compare(SmeltingRow left, SmeltingRow right) {
				int comparison = left.contentKey.compareTo(right.contentKey);
				if(comparison != 0) return comparison;
				return left.normalizedInputs.compareTo(right.normalizedInputs);
			}
		};

		private final String contentKey;
		private final String recipeClass;
		private final String outputRegistry;
		private final String outputMetadata;
		private final String outputCount;
		private final String normalizedInputs;
		private final String manager;
		private final String experience;
		private final String status;

		private SmeltingRow(String contentKey, String recipeClass, String outputRegistry,
				String outputMetadata, String outputCount, String normalizedInputs, String manager,
				String experience, String status) {
			this.contentKey = contentKey;
			this.recipeClass = recipeClass;
			this.outputRegistry = outputRegistry;
			this.outputMetadata = outputMetadata;
			this.outputCount = outputCount;
			this.normalizedInputs = normalizedInputs;
			this.manager = manager;
			this.experience = experience;
			this.status = status;
		}
	}

	private static final class MachineRow {
		private static final Comparator<MachineRow> ORDERING = new Comparator<MachineRow>() {
			@Override
			public int compare(MachineRow left, MachineRow right) {
				int comparison = left.manager.compareTo(right.manager);
				if(comparison != 0) return comparison;
				comparison = left.outputRegistry.compareTo(right.outputRegistry);
				if(comparison != 0) return comparison;
				return left.normalizedRecipe.compareTo(right.normalizedRecipe);
			}
		};

		private final String recipeClass;
		private final String recipeObjectClass;
		private final String outputRegistry;
		private final String outputMetadata;
		private final String outputCount;
		private final String normalizedInputs;
		private final String manager;
		private final String machineFamily;
		private final String normalizedRecipe;
		private final String status;

		private MachineRow(String recipeClass, String recipeObjectClass, String outputRegistry,
				String outputMetadata, String outputCount, String normalizedInputs, String manager,
				String machineFamily, String normalizedRecipe, String status) {
			this.recipeClass = recipeClass;
			this.recipeObjectClass = recipeObjectClass;
			this.outputRegistry = outputRegistry;
			this.outputMetadata = outputMetadata;
			this.outputCount = outputCount;
			this.normalizedInputs = normalizedInputs;
			this.manager = manager;
			this.machineFamily = machineFamily;
			this.normalizedRecipe = normalizedRecipe;
			this.status = status;
		}
	}

	private static final class LootRow {
		private static final Comparator<LootRow> ORDERING = new Comparator<LootRow>() {
			@Override
			public int compare(LootRow left, LootRow right) {
				int comparison = left.contentKey.compareTo(right.contentKey);
				if(comparison != 0) return comparison;
				return left.displayName.compareTo(right.displayName);
			}
		};

		private final String contentKey;
		private final String poolName;
		private final String registryName;
		private final String metadata;
		private final String minimumCount;
		private final String maximumCount;
		private final String weight;
		private final String nbt;
		private final String displayName;
		private final String sourceCategory;

		private LootRow(String contentKey, String poolName, String registryName, String metadata,
				String minimumCount, String maximumCount, String weight, String nbt,
				String displayName, String sourceCategory) {
			this.contentKey = contentKey;
			this.poolName = poolName;
			this.registryName = registryName;
			this.metadata = metadata;
			this.minimumCount = minimumCount;
			this.maximumCount = maximumCount;
			this.weight = weight;
			this.nbt = nbt;
			this.displayName = displayName;
			this.sourceCategory = sourceCategory;
		}
	}

	private static final class OutputStack {
		private static final Comparator<OutputStack> ORDERING = new Comparator<OutputStack>() {
			@Override
			public int compare(OutputStack left, OutputStack right) {
				int comparison = left.registryName.compareTo(right.registryName);
				if(comparison != 0) return comparison;
				comparison = Integer.compare(left.metadata, right.metadata);
				if(comparison != 0) return comparison;
				return Integer.compare(left.count, right.count);
			}
		};

		private final String registryName;
		private final int metadata;
		private final int count;

		private OutputStack(String registryName, int metadata, int count) {
			this.registryName = registryName;
			this.metadata = metadata;
			this.count = count;
		}
	}

	private static final class ProfileEntry {
		private static final Comparator<ProfileEntry> ORDERING = new Comparator<ProfileEntry>() {
			@Override
			public int compare(ProfileEntry left, ProfileEntry right) {
				int comparison = left.key.compareTo(right.key);
				if(comparison != 0) return comparison;
				return left.kind.compareTo(right.kind);
			}
		};

		private final String key;
		private final String kind;

		private ProfileEntry(String key, String kind) {
			this.key = key;
			this.kind = kind;
		}
	}

	public interface ContentEnrichment {
		boolean hasClientEnrichment();
		void addItemVariants(Item item, Map<Integer, ItemStack> variants);
		String getItemDisplayName(ItemStack stack);
		String getBlockDisplayName(Block block);
		String getItemCreativeTabLabel(Item item);
		String getBlockCreativeTabLabel(Block block);
		List<CreativeTabData> getCreativeTabs();
		String getSummary();
	}

	public static final class CreativeTabData {
		private final String label;
		private final String translatedLabel;
		private final String javaClass;
		private final String iconRegistryName;
		private final int registeredItemCount;
		private final String sourceCategory;

		public CreativeTabData(String label, String translatedLabel, String javaClass,
				String iconRegistryName, int registeredItemCount, String sourceCategory) {
			this.label = safe(label);
			this.translatedLabel = safe(translatedLabel);
			this.javaClass = safe(javaClass);
			this.iconRegistryName = safe(iconRegistryName);
			this.registeredItemCount = registeredItemCount;
			this.sourceCategory = safe(sourceCategory);
		}
	}

	private static final class ServerContentEnrichment implements ContentEnrichment {
		private final String clientFailure;

		private ServerContentEnrichment(String clientFailure) {
			this.clientFailure = safe(clientFailure);
		}

		@Override
		public boolean hasClientEnrichment() {
			return false;
		}

		@Override
		public void addItemVariants(Item item, Map<Integer, ItemStack> variants) { }

		@Override
		public String getItemDisplayName(ItemStack stack) {
			return "";
		}

		@Override
		public String getBlockDisplayName(Block block) {
			return "";
		}

		@Override
		public String getItemCreativeTabLabel(Item item) {
			return "";
		}

		@Override
		public String getBlockCreativeTabLabel(Block block) {
			return "";
		}

		@Override
		public List<CreativeTabData> getCreativeTabs() {
			return Collections.emptyList();
		}

		@Override
		public String getSummary() {
			String reason = clientFailure.isEmpty()
					? "dedicated-server mode"
					: "client helper unavailable (" + clientFailure + ")";
			return reason + " omitted display names, creative-tab labels/icons/membership, "
					+ "and client creative subtype enumeration; metadata variants came from "
					+ "registry defaults, crafting/smelting recipes, and structure loot.";
		}
	}

	private static final class ServerVariantIndex {
		private final Map<Item, TreeMap<Integer, ItemStack>> variants =
				new HashMap<Item, TreeMap<Integer, ItemStack>>();

		private void add(Object value) {
			if(value == null) return;
			if(value instanceof ItemStack) {
				addStack((ItemStack) value);
				return;
			}
			if(value instanceof Item) {
				addStack(new ItemStack((Item) value, 1, 0));
				return;
			}
			if(value instanceof Block) {
				Item item = Item.getItemFromBlock((Block) value);
				if(item != null) addStack(new ItemStack(item, 1, 0));
				return;
			}
			if(value instanceof Collection) {
				addAll((Collection<?>) value);
				return;
			}
			if(value.getClass().isArray()) {
				for(int index = 0; index < Array.getLength(value); index++) {
					add(Array.get(value, index));
				}
			}
		}

		private void addAll(Collection<?> values) {
			if(values == null) return;
			for(Object value : values) add(value);
		}

		private void addStack(ItemStack stack) {
			if(stack == null || stack.getItem() == null) return;
			int metadata = stack.getItemDamage();
			if(metadata < 0 || metadata == OreDictionary.WILDCARD_VALUE || metadata > 32767) return;

			TreeMap<Integer, ItemStack> itemVariants = variants.get(stack.getItem());
			if(itemVariants == null) {
				itemVariants = new TreeMap<Integer, ItemStack>();
				variants.put(stack.getItem(), itemVariants);
			}
			if(!itemVariants.containsKey(metadata)) {
				itemVariants.put(metadata, new ItemStack(stack.getItem(), 1, metadata));
			}
		}

		private Collection<ItemStack> get(Item item) {
			TreeMap<Integer, ItemStack> itemVariants = variants.get(item);
			return itemVariants == null
					? Collections.<ItemStack>emptyList()
					: itemVariants.values();
		}
	}

	public static final class ExportSummary {
		private final File outputDirectory;
		private final LinkedHashMap<String, Integer> counts;

		private ExportSummary(File outputDirectory, LinkedHashMap<String, Integer> counts) {
			this.outputDirectory = outputDirectory;
			this.counts = counts;
		}

		public File getOutputDirectory() {
			return outputDirectory;
		}

		public Map<String, Integer> getCounts() {
			return Collections.unmodifiableMap(counts);
		}

		public int getTotalRows() {
			int total = 0;
			for(Map.Entry<String, Integer> entry : counts.entrySet()) {
				if(!"content_profile_skeleton.json".equals(entry.getKey())) total += entry.getValue();
			}
			return total;
		}
	}
}
