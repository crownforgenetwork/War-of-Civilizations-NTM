package com.hbm.wocbridge.progression;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.hbm.config.CustomMachineConfigJSON;
import com.hbm.inventory.recipes.CustomMachineRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.main.MainRegistry;
import com.hbm.wocbridge.progression.ProgressionAdapters.DynamicHandlerSpec;
import com.hbm.wocbridge.progression.ProgressionAdapters.FieldRule;
import com.hbm.wocbridge.progression.ProgressionAdapters.HandlerSpec;
import com.hbm.wocbridge.progression.ProgressionAdapters.Shape;
import com.hbm.wocbridge.taxonomy.TaxonomyIo;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.stats.Achievement;
import net.minecraftforge.common.AchievementPage;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Dedicated-server-safe, read-only progression evidence export.
 */
public final class ProgressionExporter {

	public static final String SCHEMA_VERSION = "woc-hbm-progression-v1";
	private static final String MANIFEST_VERSION = "woc-hbm-progression-manifest-v1";
	private static final String OUTPUT_DIRECTORY_NAME = "progression";
	private static final String[] PAYLOAD_FILES = {
			"achievement-progression.csv",
			"machines.csv",
			"machine-recipes.csv",
			"machine-recipe-components.csv",
			"machine-energy-contracts.csv",
			"dynamic-recipe-handlers.csv",
			"machine-prerequisites.csv",
			"progression-coverage.md",
			"progression-gap-report.json",
			"progression-input-manifest.json"
	};

	private ProgressionExporter() { }

	public static ExportSummary export() throws IOException {
		File repository = resolveRepositoryRoot();
		File reportParent = new File(repository,
				"build" + File.separator + "reports" + File.separator + "woc");
		Files.createDirectories(reportParent.toPath());
		File destination = new File(reportParent, OUTPUT_DIRECTORY_NAME);
		File staged = Files.createTempDirectory(reportParent.toPath(),
				"." + OUTPUT_DIRECTORY_NAME + ".").toFile();

		RuntimeSnapshot before = RuntimeSnapshot.capture();
		boolean published = false;
		try {
			ExportData data = collect(repository, before);
			RuntimeSnapshot after = RuntimeSnapshot.capture();
			if(!before.equals(after)) {
				throw new IOException("Runtime registries or recipe handlers changed during export: "
						+ before.describeDifference(after));
			}
			data.after = after;
			data.buildCoverageAndGapReports();
			data.buildInputManifest(repository);
			writePayloadFiles(staged, data);
			data.buildOutputManifest(staged);
			writeUtf8(new File(staged, "progression-output-manifest.json"),
					TaxonomyIo.pretty(data.outputManifest));
			writeShaManifest(staged);
			validateReportSet(staged);
			replaceDirectory(staged, destination, false);
			published = true;
			ExportSummary summary = new ExportSummary(destination, data);
			MainRegistry.logger.info(
					"WOC progression export complete: {} achievements, {} machine families, "
					+ "{} recipes, {} components; runtime fingerprint {}",
					summary.achievementCount, summary.machineFamilyCount,
					summary.recipeCount, summary.componentCount, before.fingerprint);
			return summary;
		} finally {
			if(!published && staged.exists()) deleteTree(staged.toPath());
		}
	}

	private static ExportData collect(File repository, RuntimeSnapshot before)
			throws IOException {
		ExportData data = new ExportData();
		data.before = before;
		exportAchievements(data);
		exportMachines(data);
		exportMachineRecipes(data);
		exportDynamicHandlers(data);
		exportPrerequisites(data);
		data.sortAndValidate();
		return data;
	}

	private static void exportAchievements(ExportData data) {
		List<AchievementPage> pages =
				new ArrayList<AchievementPage>(AchievementPage.getAchievementPages());
		Collections.sort(pages, new Comparator<AchievementPage>() {
			@Override
			public int compare(AchievementPage left, AchievementPage right) {
				return safe(left.getName()).compareTo(safe(right.getName()));
			}
		});
		for(AchievementPage page : pages) {
			List<Achievement> achievements = page.getAchievements();
			for(int order = 0; order < achievements.size(); order++) {
				Achievement achievement = achievements.get(order);
				IconResolution icon = resolveIcon(achievement.theItemStack);
				String parent = achievement.parentAchievement == null
						? "" : safe(achievement.parentAchievement.statId);
				data.achievements.add(row(
						SCHEMA_VERSION,
						safe(achievement.statId),
						safe(achievement.statId),
						parent,
						safe(page.getName()),
						Integer.toString(order),
						Integer.toString(achievement.displayColumn),
						Integer.toString(achievement.displayRow),
						icon.kind,
						icon.registryId,
						icon.metadata,
						Boolean.toString(icon.exact),
						"achievement-page-runtime-v1",
						icon.exact ? "EXACT" : "UNRESOLVED",
						"RUNTIME_REGISTRY",
						icon.reason));
			}
		}
	}

	private static IconResolution resolveIcon(ItemStack stack) {
		if(stack == null || stack.getItem() == null) {
			return new IconResolution("", "", "", false,
					"Registered achievement icon stack is absent.");
		}
		Item item = stack.getItem();
		String itemName = registryName(item);
		if(itemName.isEmpty()) {
			return new IconResolution("ITEM", "", Integer.toString(stack.getItemDamage()),
					false, "Registered icon item has no registry identifier.");
		}
		if(item instanceof ItemBlock) {
			Block block = Block.getBlockFromItem(item);
			if(block != null && block != Blocks.air) {
				String blockName = registryName(block);
				if(!blockName.isEmpty()) {
					return new IconResolution("BLOCK", blockName,
							Integer.toString(stack.getItemDamage()), true, "");
				}
			}
		}
		return new IconResolution("ITEM", itemName,
				Integer.toString(stack.getItemDamage()), true, "");
	}

	private static void exportMachines(ExportData data) {
		Map<String, List<HandlerSpec>> grouped =
				new TreeMap<String, List<HandlerSpec>>();
		for(HandlerSpec spec : ProgressionAdapters.handlers()) {
			List<HandlerSpec> list = grouped.get(spec.machineId);
			if(list == null) {
				list = new ArrayList<HandlerSpec>();
				grouped.put(spec.machineId, list);
			}
			list.add(spec);
		}
		for(Map.Entry<String, List<HandlerSpec>> entry : grouped.entrySet()) {
			List<HandlerSpec> specs = entry.getValue();
			Collections.sort(specs, HANDLER_SPEC_ORDER);
			HandlerSpec first = specs.get(0);
			Set<String> families = new LinkedHashSet<String>();
			Set<String> handlers = new LinkedHashSet<String>();
			Set<String> rolesIn = new LinkedHashSet<String>();
			Set<String> rolesOut = new LinkedHashSet<String>();
			for(HandlerSpec spec : specs) {
				families.add(spec.familyId);
				handlers.add(spec.handlerId);
				for(FieldRule field : spec.fields) {
					if("INPUT".equals(field.role) || "CATALYST".equals(field.role)
							|| "TOOL".equals(field.role)
							|| "CONTAINER_INPUT".equals(field.role)) {
						rolesIn.add(field.role);
					} else {
						rolesOut.add(field.role);
					}
				}
			}

			MachineResolution machine = resolveMachine(first);
			String confidence = first.associationConfidence;
			String reason = first.associationReason;
			if(!machine.exact) {
				confidence = first.blockRegistryId.isEmpty() ? "PARTIAL" : "UNRESOLVED";
				reason = appendReason(reason, machine.reason);
			}
			data.machines.add(row(
					SCHEMA_VERSION,
					first.machineId,
					join(families, ";"),
					machine.blockRegistry,
					machine.itemRegistry,
					machine.metadata,
					first.tileEntityClass,
					join(handlers, ";"),
					machine.powerSystem,
					machine.consumes,
					machine.produces,
					"",
					"",
					join(rolesIn, ";"),
					join(rolesOut, ";"),
					"reviewed-machine-catalog-v1",
					confidence,
					first.blockRegistryId.isEmpty()
							? "REVIEWED_MANUAL_ADAPTER" : "RUNTIME_REGISTRY",
					reason));

			if(!first.tileEntityClass.isEmpty()) {
				String direction = "";
				if("true".equals(machine.consumes) && "true".equals(machine.produces)) {
					direction = "CONSUMES_AND_PRODUCES";
				} else if("true".equals(machine.consumes)) {
					direction = "CONSUMES";
				} else if("true".equals(machine.produces)) {
					direction = "PRODUCES";
				} else if(machine.classResolved) {
					direction = "NONE";
				}
				data.energyContracts.add(row(
						SCHEMA_VERSION,
						first.machineId,
						machine.powerSystem,
						direction,
						"",
						"",
						"",
						"",
						"",
						"tile-energy-interface-v1",
						first.tileEntityClass,
						machine.classResolved ? "EXACT" : "UNRESOLVED",
						machine.classResolved ? "REVIEWED_MANUAL_ADAPTER" : "UNRESOLVED",
						machine.reason));
			}
		}
	}

	private static MachineResolution resolveMachine(HandlerSpec spec) {
		String blockName = "";
		String itemName = "";
		String reason = "";
		boolean blockExact = spec.blockRegistryId.isEmpty();
		if(!spec.blockRegistryId.isEmpty()) {
			Object object = Block.blockRegistry.getObject(spec.blockRegistryId);
			if(object instanceof Block && object != Blocks.air) {
				Block block = (Block) object;
				blockName = registryName(block);
				Item item = Item.getItemFromBlock(block);
				itemName = registryName(item);
				blockExact = !blockName.isEmpty();
			} else {
				reason = "Reviewed block registry ID was not present at runtime: "
						+ spec.blockRegistryId;
			}
		}

		boolean classResolved = false;
		String consumes = "";
		String produces = "";
		String network = "";
		if(!spec.tileEntityClass.isEmpty()) {
			try {
				Class<?> tile = Class.forName(spec.tileEntityClass, false,
						ProgressionExporter.class.getClassLoader());
				classResolved = true;
				boolean receiver = IEnergyReceiverMK2.class.isAssignableFrom(tile);
				boolean provider = IEnergyProviderMK2.class.isAssignableFrom(tile);
				consumes = Boolean.toString(receiver);
				produces = Boolean.toString(provider);
				if(receiver || provider) network = "hbm:he-mk2";
			} catch(Throwable ex) {
				reason = appendReason(reason,
						"Reviewed tile class could not be resolved without initialization: "
								+ spec.tileEntityClass + " (" + ex.getClass().getSimpleName() + ").");
			}
		}
		return new MachineResolution(blockName, itemName,
				spec.blockRegistryId.isEmpty() ? "" : "0", network,
				consumes, produces, blockExact && classResolved,
				classResolved, reason);
	}

	private static void exportMachineRecipes(ExportData data) throws IOException {
		List<HandlerRecord> handlers = new ArrayList<HandlerRecord>();
		for(int index = 0; index < SerializableRecipe.recipeHandlers.size(); index++) {
			handlers.add(new HandlerRecord(
					SerializableRecipe.recipeHandlers.get(index), index));
		}
		Collections.sort(handlers, new Comparator<HandlerRecord>() {
			@Override
			public int compare(HandlerRecord left, HandlerRecord right) {
				int byFile = safe(left.handler.getFileName())
						.compareTo(safe(right.handler.getFileName()));
				if(byFile != 0) return byFile;
				return left.handler.getClass().getName()
						.compareTo(right.handler.getClass().getName());
			}
		});

		Set<String> recipeIds = new HashSet<String>();
		for(HandlerRecord handlerRecord : handlers) {
			SerializableRecipe handler = handlerRecord.handler;
			HandlerSpec spec = ProgressionAdapters.forHandler(handler.getClass());
			if(spec == null) {
				data.unsupportedFamilies.add(handler.getClass().getName());
				continue;
			}
			data.inspectedFamilies.add(spec.familyId);

			Object container;
			try {
				container = handler.getRecipeObject();
			} catch(Throwable ex) {
				data.unsupportedFamilies.add(spec.familyId);
				data.extractionErrors.add(spec.handlerId + ": getRecipeObject failed: "
						+ ex.getClass().getName());
				continue;
			}
			List<ContainerValue> values = flattenContainer(container);
			if(values == null) {
				data.unsupportedFamilies.add(spec.familyId);
				data.extractionErrors.add(spec.handlerId
						+ ": unsupported top-level recipe container "
						+ className(container));
				continue;
			}

			boolean familyPartial = !"EXACT".equals(spec.associationConfidence);
			for(ContainerValue value : values) {
				JsonObject serialized;
				try {
					serialized = serialize(handler, value.value);
				} catch(Throwable ex) {
					familyPartial = true;
					data.extractionErrors.add(spec.handlerId + ": serializer failed for "
							+ className(value.value) + ": " + ex.getClass().getName());
					continue;
				}
				List<JsonObject> flattened = flattenNested(serialized);
				for(int nestedIndex = 0; nestedIndex < flattened.size(); nestedIndex++) {
					JsonObject recipe = flattened.get(nestedIndex);
					String canonical = TaxonomyIo.canonicalJson(recipe);
					String recipeId = "machine-recipe:" + spec.familyId.substring(7)
							+ ":" + TaxonomyIo.sha256(spec.handlerId + "\n" + canonical);
					if(!recipeIds.add(recipeId)) {
						throw new IOException("Duplicate normalized progression recipe ID: "
								+ recipeId);
					}

					List<ComponentRow> components =
							parseComponents(recipeId, spec, recipe);
					boolean componentPartial = components.isEmpty();
					for(ComponentRow component : components) {
						data.components.add(component.values);
						if("UNRESOLVED".equals(component.confidence)) componentPartial = true;
						if("ALTERNATIVE_GROUP".equals(component.kind)) {
							data.alternativeGroupIds.add(component.alternativeGroup);
						}
					}
					String unresolved = "";
					if(components.isEmpty()) {
						unresolved = "Reviewed adapter found no typed component fields in serialized recipe.";
					}
					if(!"EXACT".equals(spec.associationConfidence)) {
						unresolved = appendReason(unresolved, spec.associationReason);
					}
					String confidence = componentPartial
							|| !"EXACT".equals(spec.associationConfidence)
									? "PARTIAL" : "EXACT";
					if(!"EXACT".equals(confidence)) familyPartial = true;
					String registrationOrder = value.order < 0 ? ""
							: Integer.toString(value.order
									+ (nestedIndex == 0 ? 0 : nestedIndex));
					data.recipes.add(row(
							SCHEMA_VERSION,
							recipeId,
							spec.machineId,
							spec.familyId,
							spec.handlerId,
							registrationOrder,
							valueAt(recipe, spec.durationPath),
							valueAt(recipe, spec.energyOperationPath),
							valueAt(recipe, spec.energyTickPath),
							firstRequirement(recipe, spec.requirements,
									"temperature", "ignitionTemp", "outputTemp"),
							firstRequirement(recipe, spec.requirements,
									"pressure", "pressure"),
							joinedRequirements(recipe, spec.requirements,
									"tierLower", "tierUpper"),
							otherRequirements(recipe, spec.requirements),
							confidence,
							ProgressionAdapters.ADAPTER_VERSION,
							"RUNTIME_REGISTRY",
							unresolved));
					data.recipeMachineById.put(recipeId, spec.machineId);
				}
			}
			if(familyPartial) data.partialFamilies.add(spec.familyId);
			else data.exactFamilies.add(spec.familyId);
		}
		data.partialFamilies.removeAll(data.exactFamilies);
	}

	private static List<ComponentRow> parseComponents(String recipeId,
			HandlerSpec spec, JsonObject recipe) {
		List<ComponentRow> rows = new ArrayList<ComponentRow>();
		int order = 0;
		for(FieldRule rule : spec.fields) {
			List<JsonElement> values = valuesAt(recipe, rule.path);
			for(JsonElement value : values) {
				List<ComponentValue> parsed = parseValue(rule, value, recipe);
				for(ComponentValue component : parsed) {
					String unresolved = component.reason;
					String confidence = component.exact ? "EXACT" : "UNRESOLVED";
					rows.add(new ComponentRow(row(
							SCHEMA_VERSION,
							recipeId,
							Integer.toString(order++),
							rule.role,
							component.kind,
							component.identifier,
							component.metadata,
							component.quantity,
							component.fluidAmount,
							component.chance,
							component.alternativeGroup,
							rule.consumed,
							rule.returnedContainer,
							confidence,
							ProgressionAdapters.ADAPTER_VERSION,
							"RUNTIME_REGISTRY",
							unresolved),
							component.kind, component.alternativeGroup,
							confidence));
				}
			}
		}
		return rows;
	}

	private static List<ComponentValue> parseValue(FieldRule rule,
			JsonElement value, JsonObject root) {
		if(value == null || value.isJsonNull()) return Collections.emptyList();
		switch(rule.shape) {
		case ASTACK:
			return singleton(parseAStack(value));
		case ASTACK_LIST:
			return parseArray(value, new ElementParser() {
				@Override public ComponentValue parse(JsonElement element) {
					return parseAStack(element);
				}
			});
		case ITEM_STACK:
			return singleton(parseItemStack(value, false));
		case ITEM_STACK_LIST:
			return parseArray(value, new ElementParser() {
				@Override public ComponentValue parse(JsonElement element) {
					return parseItemStack(element, false);
				}
			});
		case CHANCE_ITEM_LIST:
			return parseArray(value, new ElementParser() {
				@Override public ComponentValue parse(JsonElement element) {
					return parseItemStack(element, true);
				}
			});
		case GENERIC_OUTPUT_LIST:
			return parseGenericOutputs(value);
		case FLUID_STACK:
			return singleton(parseFluid(value));
		case FLUID_STACK_LIST:
			return parseArray(value, new ElementParser() {
				@Override public ComponentValue parse(JsonElement element) {
					return parseFluid(element);
				}
			});
		case MATERIAL_STACK_LIST:
			return parseMaterialValues(value);
		case ITEM_IDENTIFIER:
			return singleton(identifierValue(value, "ITEM", root, true));
		case FLUID_IDENTIFIER:
			ComponentValue fluid = identifierValue(value, "FLUID", root, false);
			if(root.has("outputAmount")) {
				fluid = fluid.withFluidAmount(safePrimitive(root.get("outputAmount")));
			}
			return singleton(fluid);
		case ORE_IDENTIFIER:
			return singleton(identifierValue(value, "ORE_DICTIONARY", root, false));
		case TAG_IDENTIFIER:
			return singleton(identifierValue(value,
					"TAG_OR_HANDLER_SPECIFIC", root, false));
		default:
			return singleton(ComponentValue.unresolved(TaxonomyIo.canonicalJson(value),
					"Reviewed handler-specific field is not safely normalizable."));
		}
	}

	private static ComponentValue parseAStack(JsonElement element) {
		if(!element.isJsonArray()) {
			return ComponentValue.unresolved(TaxonomyIo.canonicalJson(element),
					"Expected an HBM AStack array.");
		}
		JsonArray array = element.getAsJsonArray();
		if(array.size() < 2 || !array.get(0).isJsonPrimitive()) {
			return ComponentValue.unresolved(TaxonomyIo.canonicalJson(element),
					"Malformed HBM AStack array.");
		}
		String type = array.get(0).getAsString();
		String identifier = safePrimitive(array.get(1));
		String quantity = array.size() > 2 ? safePrimitive(array.get(2)) : "1";
		if("dict".equals(type)) {
			return new ComponentValue("ORE_DICTIONARY", identifier, "", quantity,
					"", "", "", true, "");
		}
		if("item".equals(type) || "nbt".equals(type)) {
			String metadata = array.size() > 3 ? safePrimitive(array.get(3)) : "0";
			return new ComponentValue(kindForItem(identifier), identifier, metadata,
					quantity, "", "", "", true, "");
		}
		if("dictframe".equals(type)) {
			return new ComponentValue("TAG_OR_HANDLER_SPECIFIC",
					"ore-dictionary-material-suffix:" + identifier, "", "1",
					"", "", "", true, "");
		}
		return ComponentValue.unresolved(TaxonomyIo.canonicalJson(element),
				"Unknown HBM AStack discriminator: " + type);
	}

	private static ComponentValue parseItemStack(JsonElement element,
			boolean chance) {
		if(!element.isJsonArray()) {
			return ComponentValue.unresolved(TaxonomyIo.canonicalJson(element),
					"Expected an HBM ItemStack array.");
		}
		JsonArray array = element.getAsJsonArray();
		if(array.size() < 1 || !array.get(0).isJsonPrimitive()) {
			return ComponentValue.unresolved(TaxonomyIo.canonicalJson(element),
					"Malformed HBM ItemStack array.");
		}
		String identifier = safePrimitive(array.get(0));
		int chanceOffset = chance ? 1 : 0;
		int dataSize = array.size() - chanceOffset;
		String quantity = dataSize > 1 ? safePrimitive(array.get(1)) : "1";
		String metadata = dataSize > 2 ? safePrimitive(array.get(2)) : "0";
		String chanceValue = chance && array.size() > 1
				? safePrimitive(array.get(array.size() - 1)) : "";
		return new ComponentValue(kindForItem(identifier), identifier, metadata,
				quantity, "", chanceValue, "", true, "");
	}

	private static ComponentValue parseFluid(JsonElement element) {
		if(!element.isJsonArray()) {
			return ComponentValue.unresolved(TaxonomyIo.canonicalJson(element),
					"Expected an HBM FluidStack array.");
		}
		JsonArray array = element.getAsJsonArray();
		if(array.size() < 2) {
			return ComponentValue.unresolved(TaxonomyIo.canonicalJson(element),
					"Malformed HBM FluidStack array.");
		}
		return new ComponentValue("FLUID", safePrimitive(array.get(0)), "", "",
				safePrimitive(array.get(1)), "", "", true, "");
	}

	private static List<ComponentValue> parseMaterialValues(JsonElement value) {
		if(!value.isJsonArray()) {
			return singleton(ComponentValue.unresolved(TaxonomyIo.canonicalJson(value),
					"Expected a material-stack array."));
		}
		JsonArray array = value.getAsJsonArray();
		if(array.size() >= 2 && array.get(0).isJsonPrimitive()
				&& array.get(1).isJsonPrimitive()) {
			return singleton(new ComponentValue("TAG_OR_HANDLER_SPECIFIC",
					"hbm-material:" + safePrimitive(array.get(0)), "",
					safePrimitive(array.get(1)), "", "", "", true, ""));
		}
		List<ComponentValue> result = new ArrayList<ComponentValue>();
		for(JsonElement child : array) result.addAll(parseMaterialValues(child));
		return result;
	}

	private static List<ComponentValue> parseGenericOutputs(JsonElement value) {
		List<ComponentValue> result = new ArrayList<ComponentValue>();
		if(!value.isJsonArray()) {
			result.add(ComponentValue.unresolved(TaxonomyIo.canonicalJson(value),
					"Expected a generic output array."));
			return result;
		}
		for(JsonElement output : value.getAsJsonArray()) {
			parseGenericOutput(output, result, "");
		}
		return result;
	}

	private static void parseGenericOutput(JsonElement value,
			List<ComponentValue> result, String groupId) {
		if(!value.isJsonArray()) {
			result.add(ComponentValue.unresolved(TaxonomyIo.canonicalJson(value),
					"Malformed generic chance output."));
			return;
		}
		JsonArray array = value.getAsJsonArray();
		if(array.size() == 0) return;
		if(array.get(0).isJsonPrimitive()
				&& "multi".equals(array.get(0).getAsString())) {
			String canonical = TaxonomyIo.canonicalJson(array);
			String alternative = "alternative-group:"
					+ TaxonomyIo.sha256(canonical).substring(0, 16);
			for(int index = 1; index < array.size(); index++) {
				parseGenericOutput(array.get(index), result, alternative);
			}
			return;
		}
		int stackIndex = array.get(0).isJsonPrimitive()
				&& "single".equals(array.get(0).getAsString()) ? 1 : 0;
		if(stackIndex >= array.size() || !array.get(stackIndex).isJsonArray()) {
			result.add(ComponentValue.unresolved(TaxonomyIo.canonicalJson(value),
					"Generic output did not contain a serialized ItemStack."));
			return;
		}
		ComponentValue stack = parseItemStack(array.get(stackIndex), false);
		String chance = array.size() > stackIndex + 1
				? safePrimitive(array.get(stackIndex + 1)) : "1";
		String kind = groupId.isEmpty() ? stack.kind : "ALTERNATIVE_GROUP";
		result.add(new ComponentValue(kind, stack.identifier, stack.metadata,
				stack.quantity, "", chance, groupId, stack.exact, stack.reason));
	}

	private static ComponentValue identifierValue(JsonElement value, String kind,
			JsonObject root, boolean withMeta) {
		if(!value.isJsonPrimitive()) {
			return ComponentValue.unresolved(TaxonomyIo.canonicalJson(value),
					"Expected a canonical identifier primitive.");
		}
		String identifier = value.getAsString();
		String metadata = withMeta && root.has("key")
				&& root.get("key").isJsonObject()
				&& root.getAsJsonObject("key").has("meta")
						? safePrimitive(root.getAsJsonObject("key").get("meta")) : "";
		return new ComponentValue(kind, identifier, metadata, "", "", "", "",
				!identifier.isEmpty(), identifier.isEmpty()
						? "Identifier was empty." : "");
	}

	private static List<ComponentValue> parseArray(JsonElement value,
			ElementParser parser) {
		if(!value.isJsonArray()) {
			return singleton(ComponentValue.unresolved(TaxonomyIo.canonicalJson(value),
					"Expected an array for reviewed repeated field."));
		}
		List<ComponentValue> result = new ArrayList<ComponentValue>();
		for(JsonElement element : value.getAsJsonArray()) {
			if(element == null || element.isJsonNull()) continue;
			result.add(parser.parse(element));
		}
		return result;
	}

	private static String kindForItem(String identifier) {
		Object item = Item.itemRegistry.getObject(identifier);
		return item instanceof ItemBlock ? "BLOCK" : "ITEM";
	}

	private static List<JsonElement> valuesAt(JsonObject root, String path) {
		if(path == null || path.isEmpty()) return Collections.emptyList();
		List<JsonElement> current = new ArrayList<JsonElement>();
		current.add(root);
		for(String part : path.split("\\.")) {
			boolean repeat = part.endsWith("[]");
			String name = repeat ? part.substring(0, part.length() - 2) : part;
			List<JsonElement> next = new ArrayList<JsonElement>();
			for(JsonElement element : current) {
				if(!element.isJsonObject()) continue;
				JsonElement child = element.getAsJsonObject().get(name);
				if(child == null || child.isJsonNull()) continue;
				if(repeat) {
					if(child.isJsonArray()) {
						for(JsonElement nested : child.getAsJsonArray()) next.add(nested);
					}
				} else {
					next.add(child);
				}
			}
			current = next;
		}
		return current;
	}

	private static JsonObject serialize(SerializableRecipe handler, Object recipe)
			throws IOException {
		StringWriter output = new StringWriter();
		JsonWriter writer = new JsonWriter(output);
		writer.beginObject();
		handler.writeRecipe(recipe, writer);
		writer.endObject();
		writer.close();
		JsonElement parsed = new JsonParser().parse(output.toString());
		if(!parsed.isJsonObject()) {
			throw new IOException("Recipe serializer did not produce an object.");
		}
		return parsed.getAsJsonObject();
	}

	private static List<JsonObject> flattenNested(JsonObject serialized) {
		String wrapperName = null;
		if(serialized.has("recipes") && serialized.get("recipes").isJsonArray()) {
			if(serialized.has("recipeKey")) wrapperName = "recipeKey";
			else if(serialized.has("outputType")) wrapperName = "outputType";
		}
		if(wrapperName == null) return Collections.singletonList(serialized);
		List<JsonObject> result = new ArrayList<JsonObject>();
		for(JsonElement element : serialized.getAsJsonArray("recipes")) {
			if(!element.isJsonObject()) continue;
			JsonObject flattened = new JsonObject();
			flattened.add(wrapperName, serialized.get(wrapperName));
			for(Map.Entry<String, JsonElement> field
					: element.getAsJsonObject().entrySet()) {
				flattened.add(field.getKey(), field.getValue());
			}
			result.add(flattened);
		}
		return result;
	}

	private static List<ContainerValue> flattenContainer(Object container) {
		if(container == null) return new ArrayList<ContainerValue>();
		List<ContainerValue> values = new ArrayList<ContainerValue>();
		if(container instanceof List) {
			List<?> list = (List<?>) container;
			for(int index = 0; index < list.size(); index++) {
				values.add(new ContainerValue(list.get(index), index));
			}
			return values;
		}
		if(container instanceof Collection) {
			for(Object value : (Collection<?>) container) {
				values.add(new ContainerValue(value, -1));
			}
			return values;
		}
		if(container instanceof Map) {
			for(Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
				values.add(new ContainerValue(entry, -1));
			}
			return values;
		}
		if(container.getClass().isArray()) {
			for(int index = 0; index < Array.getLength(container); index++) {
				values.add(new ContainerValue(Array.get(container, index), index));
			}
			return values;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static void exportDynamicHandlers(ExportData data) {
		Map<String, Integer> liveCounts = new HashMap<String, Integer>();
		for(Object object : (List<Object>) CraftingManager.getInstance().getRecipeList()) {
			String name = className(object);
			Integer count = liveCounts.get(name);
			liveCounts.put(name, count == null ? 1 : count + 1);
		}
		for(DynamicHandlerSpec spec : ProgressionAdapters.dynamicHandlers()) {
			int count = liveCounts.containsKey(spec.className)
					? liveCounts.get(spec.className) : 0;
			data.dynamicHandlers.add(row(
					SCHEMA_VERSION,
					"crafting-handler:" + spec.className.substring(
							spec.className.lastIndexOf('.') + 1),
					spec.className,
					Integer.toString(count),
					spec.status,
					spec.reason,
					Integer.toString(count),
					Boolean.toString(spec.safeIteration),
					Boolean.toString(spec.outputsKnown),
					Boolean.toString(spec.typedInputsKnown),
					spec.futureWork,
					"dynamic-crafting-handler-v1",
					count > 0 ? "RUNTIME_REGISTRY" : "UNRESOLVED"));
		}

		int recipeKeys = CustomMachineRecipes.recipes.size();
		int customRecipeCount = 0;
		for(List<?> recipes : CustomMachineRecipes.recipes.values()) {
			customRecipeCount += recipes.size();
		}
		int configurations = CustomMachineConfigJSON.niceList.size();
		data.dynamicHandlers.add(row(
				SCHEMA_VERSION,
				"machine-handler:custom-machine-runtime",
				CustomMachineRecipes.class.getName(),
				Integer.toString(configurations),
				"SUPPORTED_PARTIAL",
				"Runtime custom-machine recipes are typed; physical machine associations are external configuration.",
				Integer.toString(customRecipeCount),
				"true",
				"true",
				"true",
				"Join recipeKey to reviewed custom-machine configuration identities.",
				"custom-machine-runtime-v1",
				"RUNTIME_REGISTRY"));
		data.customMachineRegistrationCount = configurations;
		data.customMachineRecipeKeyCount = recipeKeys;
	}

	@SuppressWarnings("unchecked")
	private static void exportPrerequisites(ExportData data) {
		Set<String> targets = new HashSet<String>();
		Map<String, String> targetMachine = new HashMap<String, String>();
		for(HandlerSpec spec : ProgressionAdapters.handlers()) {
			if(!spec.blockRegistryId.isEmpty()) {
				targets.add(spec.blockRegistryId);
				if(!targetMachine.containsKey(spec.blockRegistryId)) {
					targetMachine.put(spec.blockRegistryId, spec.machineId);
				}
			}
		}

		Map<String, List<String>> producers = new HashMap<String, List<String>>();
		for(String[] component : data.components) {
			if(!"OUTPUT".equals(component[3])
					&& !"BYPRODUCT".equals(component[3])
					&& !"CHANCE_OUTPUT".equals(component[3])) continue;
			if(component[5].isEmpty()) continue;
			String recipeId = component[1];
			String machineId = data.recipeMachineById.get(recipeId);
			if(machineId == null) continue;
			List<String> list = producers.get(component[5]);
			if(list == null) {
				list = new ArrayList<String>();
				producers.put(component[5], list);
			}
			if(!list.contains(machineId)) list.add(machineId);
		}

		for(Object object : (List<Object>) CraftingManager.getInstance().getRecipeList()) {
			if(!(object instanceof IRecipe)) continue;
			IRecipe recipe = (IRecipe) object;
			ItemStack output;
			try {
				output = recipe.getRecipeOutput();
			} catch(Throwable ex) {
				continue;
			}
			if(output == null) continue;
			String outputId = registryName(output.getItem());
			if(!targets.contains(outputId)) continue;
			List<CraftingIngredient> ingredients = craftingIngredients(recipe);
			if(ingredients == null) continue;
			String fingerprint = outputId + "#" + output.getItemDamage()
					+ "\n" + canonicalIngredients(ingredients);
			String craftingId = "crafting-recipe:" + outputId + ":"
					+ TaxonomyIo.sha256(fingerprint);
			for(int index = 0; index < ingredients.size(); index++) {
				CraftingIngredient ingredient = ingredients.get(index);
				if(ingredient.empty) continue;
				String evidence = recipe.getClass().getName() + "#ingredient[" + index + "]";
				data.prerequisites.add(row(
						SCHEMA_VERSION,
						targetMachine.get(outputId),
						outputId,
						craftingId,
						"DIRECT_CRAFTING_DEPENDENCY",
						ingredient.kind,
						ingredient.identifier,
						"",
						evidence,
						ingredient.exact ? "EXACT" : "PARTIAL",
						"crafting-registry-v1",
						"RUNTIME_REGISTRY",
						ingredient.reason));
				if(ingredient.exact && producers.containsKey(ingredient.identifier)) {
					List<String> machines = producers.get(ingredient.identifier);
					Collections.sort(machines);
					for(String prerequisiteMachine : machines) {
						data.prerequisites.add(row(
								SCHEMA_VERSION,
								targetMachine.get(outputId),
								outputId,
								craftingId,
								"TRANSITIVE_PRODUCTION_DEPENDENCY",
								ingredient.kind,
								ingredient.identifier,
								prerequisiteMachine,
								evidence + " -> machine recipe output",
								"EXACT",
								"mechanical-production-join-v1",
								"RUNTIME_REGISTRY",
								"Mechanical production evidence only; not a deliberate design prerequisite."));
					}
				}
			}
		}
	}

	private static List<CraftingIngredient> craftingIngredients(IRecipe recipe) {
		Object[] inputs;
		if(recipe instanceof ShapedRecipes) {
			inputs = ((ShapedRecipes) recipe).recipeItems;
		} else if(recipe instanceof ShapelessRecipes) {
			inputs = ((ShapelessRecipes) recipe).recipeItems.toArray();
		} else if(recipe instanceof ShapedOreRecipe) {
			inputs = ((ShapedOreRecipe) recipe).getInput();
		} else if(recipe instanceof ShapelessOreRecipe) {
			inputs = ((ShapelessOreRecipe) recipe).getInput().toArray();
		} else {
			return null;
		}
		List<CraftingIngredient> result = new ArrayList<CraftingIngredient>();
		for(Object input : inputs) result.add(craftingIngredient(input));
		return result;
	}

	private static CraftingIngredient craftingIngredient(Object input) {
		if(input == null) return CraftingIngredient.empty();
		if(input instanceof ItemStack) {
			ItemStack stack = (ItemStack) input;
			return new CraftingIngredient(kindForItem(registryName(stack.getItem())),
					registryName(stack.getItem()), true, "", false);
		}
		if(input instanceof Item) {
			String id = registryName((Item) input);
			return new CraftingIngredient(kindForItem(id), id, true, "", false);
		}
		if(input instanceof Block) {
			String id = registryName((Block) input);
			return new CraftingIngredient("BLOCK", id, true, "", false);
		}
		if(input instanceof List) {
			List<String> alternatives = new ArrayList<String>();
			for(Object child : (List<?>) input) {
				if(child instanceof ItemStack) {
					ItemStack stack = (ItemStack) child;
					alternatives.add(registryName(stack.getItem()) + "#"
							+ stack.getItemDamage());
				}
			}
			Collections.sort(alternatives);
			String canonical = join(alternatives, "|");
			return new CraftingIngredient("ALTERNATIVE_GROUP",
					"alternative-group:"
							+ TaxonomyIo.sha256(canonical).substring(0, 16),
					false,
					"Members: " + canonical,
					false);
		}
		return new CraftingIngredient("UNRESOLVED", className(input), false,
				"Unsupported crafting ingredient type.", false);
	}

	private static String canonicalIngredients(List<CraftingIngredient> values) {
		List<String> parts = new ArrayList<String>();
		for(CraftingIngredient value : values) {
			parts.add(value.empty ? "empty" : value.kind + ":" + value.identifier);
		}
		return join(parts, ";");
	}

	private static void writePayloadFiles(File staged, ExportData data)
			throws IOException {
		writeCsv(new File(staged, "achievement-progression.csv"),
				new String[] {"schema_version", "achievement_id",
						"registration_or_localization_key", "parent_achievement_id",
						"page_category", "registration_order", "display_column",
						"display_row", "icon_kind", "icon_registry_id",
						"icon_metadata", "icon_resolution_exact", "source_adapter",
						"confidence", "evidence_origin", "unresolved_reason"},
				data.achievements);
		writeCsv(new File(staged, "machines.csv"),
				new String[] {"schema_version", "machine_id", "machine_family_id",
						"machine_block_registry_id", "machine_item_registry_id",
						"metadata_variant", "tile_entity_class_id",
						"recipe_handler_id", "power_system_id",
						"consumes_power", "produces_power",
						"nominal_configured_consumption",
						"nominal_configured_production",
						"supported_input_roles", "supported_output_roles",
						"source_adapter", "confidence", "evidence_origin",
						"unresolved_reason"},
				data.machines);
		writeCsv(new File(staged, "machine-recipes.csv"),
				new String[] {"schema_version", "recipe_id", "machine_id",
						"machine_family_id", "handler_id", "registration_order",
						"duration", "energy_per_operation", "energy_per_tick",
						"temperature_requirement", "pressure_requirement",
						"tier_requirement", "other_requirements", "confidence",
						"source_adapter", "evidence_origin", "unresolved_reason"},
				data.recipes);
		writeCsv(new File(staged, "machine-recipe-components.csv"),
				new String[] {"schema_version", "recipe_id", "component_order",
						"direction_or_role", "value_kind", "canonical_identifier",
						"metadata", "quantity", "fluid_amount", "chance",
						"alternative_group_id", "consumed", "returned_container",
						"confidence", "source_adapter", "evidence_origin",
						"unresolved_reason"},
				data.components);
		writeCsv(new File(staged, "machine-energy-contracts.csv"),
				new String[] {"schema_version", "machine_id",
						"power_network_or_energy_kind", "direction", "idle_use",
						"active_use", "operation_cost", "storage_capacity",
						"minimum_operating_requirement", "source_adapter",
						"evidence_location", "confidence", "evidence_origin",
						"unresolved_reason"},
				data.energyContracts);
		writeCsv(new File(staged, "dynamic-recipe-handlers.csv"),
				new String[] {"schema_version", "handler_id",
						"handler_class_or_registration_id", "registration_count",
						"adapter_status", "reason", "recipe_count",
						"runtime_iteration_safe", "recipe_outputs_determinable",
						"typed_inputs_determinable", "required_future_work",
						"source_adapter", "evidence_origin"},
				data.dynamicHandlers);
		writeCsv(new File(staged, "machine-prerequisites.csv"),
				new String[] {"schema_version", "machine_id",
						"machine_item_or_block", "crafting_recipe_id",
						"relationship_type", "dependency_kind",
						"direct_material_or_component",
						"prerequisite_machine_id", "evidence_path", "confidence",
						"source_adapter", "evidence_origin", "unresolved_reason"},
				data.prerequisites);
		writeUtf8(new File(staged, "progression-coverage.md"), data.coverageMarkdown);
		writeUtf8(new File(staged, "progression-gap-report.json"),
				TaxonomyIo.pretty(data.gapReport));
		writeUtf8(new File(staged, "progression-input-manifest.json"),
				TaxonomyIo.pretty(data.inputManifest));
	}

	private static void writeCsv(File file, String[] header, List<String[]> rows)
			throws IOException {
		StringBuilder content = new StringBuilder();
		appendCsvLine(content, header);
		for(String[] row : rows) appendCsvLine(content, row);
		writeUtf8(file, content.toString());
	}

	private static void appendCsvLine(StringBuilder output, String[] values) {
		for(int index = 0; index < values.length; index++) {
			if(index > 0) output.append(',');
			output.append(TaxonomyIo.csv(normalizeLineEndings(values[index])));
		}
		output.append('\n');
	}

	private static void writeUtf8(File file, String content) throws IOException {
		Files.createDirectories(file.getParentFile().toPath());
		try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
			writer.write(normalizeLineEndings(content));
		}
	}

	private static void writeShaManifest(File staged) throws IOException {
		List<File> files = reportFiles(staged);
		Collections.sort(files, FILE_NAME_ORDER);
		StringBuilder manifest = new StringBuilder();
		manifest.append("# ").append(MANIFEST_VERSION).append('\n');
		for(File file : files) {
			if("progression-sha256-manifest.txt".equals(file.getName())) continue;
			manifest.append(TaxonomyIo.sha256(file)).append("  ")
					.append(file.getName()).append('\n');
		}
		writeUtf8(new File(staged, "progression-sha256-manifest.txt"),
				manifest.toString());
	}

	private static void validateReportSet(File staged) throws IOException {
		List<String> expected = new ArrayList<String>(Arrays.asList(PAYLOAD_FILES));
		expected.add("progression-output-manifest.json");
		expected.add("progression-sha256-manifest.txt");
		Collections.sort(expected);
		List<String> actual = new ArrayList<String>();
		for(File file : reportFiles(staged)) actual.add(file.getName());
		Collections.sort(actual);
		if(!expected.equals(actual)) {
			throw new IOException("Progression report set is incomplete: expected "
					+ expected + " but found " + actual);
		}
		for(File file : reportFiles(staged)) {
			byte[] bytes = Files.readAllBytes(file.toPath());
			for(int index = 0; index < bytes.length; index++) {
				if(bytes[index] == '\r') {
					throw new IOException("Report contains a CR byte: " + file.getName());
				}
			}
		}
	}

	static void replaceDirectory(File staged, File destination,
			boolean failBeforePublish) throws IOException {
		File parent = destination.getParentFile();
		Files.createDirectories(parent.toPath());
		File backup = new File(parent, "." + destination.getName() + ".backup");
		if(backup.exists()) deleteTree(backup.toPath());
		boolean oldMoved = false;
		try {
			if(destination.exists()) {
				move(destination.toPath(), backup.toPath());
				oldMoved = true;
			}
			if(failBeforePublish) {
				throw new IOException("Injected fixture failure before publication.");
			}
			move(staged.toPath(), destination.toPath());
			if(oldMoved) deleteTree(backup.toPath());
		} catch(IOException ex) {
			if(!destination.exists() && oldMoved && backup.exists()) {
				move(backup.toPath(), destination.toPath());
			}
			throw ex;
		}
	}

	private static void move(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch(AtomicMoveNotSupportedException ex) {
			Files.move(source, destination);
		}
	}

	static void deleteTree(Path path) throws IOException {
		if(!Files.exists(path)) return;
		List<Path> paths = new ArrayList<Path>();
		Files.walk(path).forEach(paths::add);
		Collections.sort(paths, Collections.reverseOrder());
		for(Path value : paths) Files.deleteIfExists(value);
	}

	private static List<File> reportFiles(File directory) {
		File[] files = directory.listFiles();
		List<File> result = new ArrayList<File>();
		if(files != null) {
			for(File file : files) if(file.isFile()) result.add(file);
		}
		return result;
	}

	private static File resolveRepositoryRoot() throws IOException {
		File current = new File(System.getProperty("user.dir", ".")).getCanonicalFile();
		for(int depth = 0; depth < 8 && current != null; depth++) {
			if(new File(current, "build.gradle").isFile()
					&& new File(current, "src/main/java/com/hbm").isDirectory()) {
				return current;
			}
			current = current.getParentFile();
		}
		throw new IOException("Unable to locate the HBM repository root.");
	}

	private static String valueAt(JsonObject object, String path) {
		List<JsonElement> values = valuesAt(object, path);
		if(values.isEmpty()) return "";
		List<String> strings = new ArrayList<String>();
		for(JsonElement value : values) strings.add(safePrimitive(value));
		return join(strings, ";");
	}

	private static String firstRequirement(JsonObject object, String declared,
			String category, String... names) {
		Set<String> fields = splitSet(declared);
		for(String name : names) {
			if(fields.contains(name) && object.has(name)) {
				return safePrimitive(object.get(name));
			}
		}
		return "";
	}

	private static String joinedRequirements(JsonObject object, String declared,
			String... names) {
		Set<String> fields = splitSet(declared);
		List<String> result = new ArrayList<String>();
		for(String name : names) {
			if(fields.contains(name) && object.has(name)) {
				result.add(name + "=" + safePrimitive(object.get(name)));
			}
		}
		return join(result, ";");
	}

	private static String otherRequirements(JsonObject object, String declared) {
		Set<String> excluded = new HashSet<String>(Arrays.asList(
				"ignitionTemp", "outputTemp", "pressure", "tierLower", "tierUpper"));
		List<String> result = new ArrayList<String>();
		for(String path : splitSet(declared)) {
			if(excluded.contains(path)) continue;
			String value = valueAt(object, path);
			if(!value.isEmpty()) result.add(path + "=" + value);
		}
		return join(result, ";");
	}

	private static Set<String> splitSet(String value) {
		Set<String> result = new LinkedHashSet<String>();
		if(value != null && !value.isEmpty()) {
			for(String part : value.split(";")) {
				if(!part.isEmpty()) result.add(part);
			}
		}
		return result;
	}

	private static String safePrimitive(JsonElement value) {
		if(value == null || value.isJsonNull()) return "";
		if(value.isJsonPrimitive()) return value.getAsString();
		return TaxonomyIo.canonicalJson(value);
	}

	private static String normalizeLineEndings(String value) {
		return safe(value).replace("\r\n", "\n").replace('\r', '\n');
	}

	static String csvForFixture(String value) {
		return TaxonomyIo.csv(normalizeLineEndings(value));
	}

	static String recipeIdForFixture(String handlerId, String family,
			JsonObject recipe) {
		return "machine-recipe:" + family + ":"
				+ TaxonomyIo.sha256(handlerId + "\n"
						+ TaxonomyIo.canonicalJson(recipe));
	}

	static void requireUniqueRecipeIdsForFixture(List<String> ids)
			throws IOException {
		Set<String> unique = new HashSet<String>();
		for(String id : ids) {
			if(!unique.add(id)) throw new IOException("Duplicate recipe ID: " + id);
		}
	}

	static List<String[]> parseFixtureComponents(String recipeId,
			HandlerSpec spec, JsonObject recipe) {
		List<String[]> result = new ArrayList<String[]>();
		for(ComponentRow row : parseComponents(recipeId, spec, recipe)) {
			result.add(row.values);
		}
		return result;
	}

	static RuntimeSnapshot snapshotForFixture() {
		return RuntimeSnapshot.capture();
	}

	static String[] resolveIconForFixture(ItemStack stack) {
		IconResolution icon = resolveIcon(stack);
		return new String[] {icon.kind, icon.registryId, icon.metadata,
				Boolean.toString(icon.exact), icon.reason};
	}

	static String parentIdForFixture(Achievement achievement) {
		return achievement == null || achievement.parentAchievement == null
				? "" : safe(achievement.parentAchievement.statId);
	}

	static boolean[] energyFlagsForFixture(Class<?> type) {
		return new boolean[] {
				IEnergyReceiverMK2.class.isAssignableFrom(type),
				IEnergyProviderMK2.class.isAssignableFrom(type)
		};
	}

	static File repositoryRootForFixture() throws IOException {
		return resolveRepositoryRoot();
	}

	static void sortRowsForFixture(List<String[]> rows) {
		Collections.sort(rows, ROW_ORDER);
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

	private static String className(Object object) {
		return object == null ? "" : object.getClass().getName();
	}

	private static String appendReason(String left, String right) {
		if(left == null || left.isEmpty()) return safe(right);
		if(right == null || right.isEmpty()) return left;
		return left + " " + right;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static String join(Collection<String> values, String delimiter) {
		StringBuilder result = new StringBuilder();
		int index = 0;
		for(String value : values) {
			if(index++ > 0) result.append(delimiter);
			result.append(safe(value));
		}
		return result.toString();
	}

	private static String[] row(String... values) {
		return values;
	}

	private static List<ComponentValue> singleton(ComponentValue value) {
		return Collections.singletonList(value);
	}

	private static int compareRows(String[] left, String[] right) {
		int length = Math.min(left.length, right.length);
		for(int index = 0; index < length; index++) {
			int compare = safe(left[index]).compareTo(safe(right[index]));
			if(compare != 0) return compare;
		}
		return left.length - right.length;
	}

	private static final Comparator<String[]> ROW_ORDER =
			new Comparator<String[]>() {
		@Override
		public int compare(String[] left, String[] right) {
			return compareRows(left, right);
		}
	};

	private static final Comparator<File> FILE_NAME_ORDER =
			new Comparator<File>() {
		@Override
		public int compare(File left, File right) {
			return left.getName().compareTo(right.getName());
		}
	};

	private static final Comparator<HandlerSpec> HANDLER_SPEC_ORDER =
			new Comparator<HandlerSpec>() {
		@Override
		public int compare(HandlerSpec left, HandlerSpec right) {
			return left.handlerId.compareTo(right.handlerId);
		}
	};

	private interface ElementParser {
		ComponentValue parse(JsonElement element);
	}

	private static final class IconResolution {
		final String kind;
		final String registryId;
		final String metadata;
		final boolean exact;
		final String reason;

		IconResolution(String kind, String registryId, String metadata,
				boolean exact, String reason) {
			this.kind = kind;
			this.registryId = registryId;
			this.metadata = metadata;
			this.exact = exact;
			this.reason = reason;
		}
	}

	private static final class MachineResolution {
		final String blockRegistry;
		final String itemRegistry;
		final String metadata;
		final String powerSystem;
		final String consumes;
		final String produces;
		final boolean exact;
		final boolean classResolved;
		final String reason;

		MachineResolution(String blockRegistry, String itemRegistry,
				String metadata, String powerSystem, String consumes,
				String produces, boolean exact, boolean classResolved,
				String reason) {
			this.blockRegistry = blockRegistry;
			this.itemRegistry = itemRegistry;
			this.metadata = metadata;
			this.powerSystem = powerSystem;
			this.consumes = consumes;
			this.produces = produces;
			this.exact = exact;
			this.classResolved = classResolved;
			this.reason = reason;
		}
	}

	private static final class HandlerRecord {
		final SerializableRecipe handler;
		@SuppressWarnings("unused")
		final int registrationOrder;

		HandlerRecord(SerializableRecipe handler, int registrationOrder) {
			this.handler = handler;
			this.registrationOrder = registrationOrder;
		}
	}

	private static final class ContainerValue {
		final Object value;
		final int order;

		ContainerValue(Object value, int order) {
			this.value = value;
			this.order = order;
		}
	}

	private static final class ComponentValue {
		final String kind;
		final String identifier;
		final String metadata;
		final String quantity;
		final String fluidAmount;
		final String chance;
		final String alternativeGroup;
		final boolean exact;
		final String reason;

		ComponentValue(String kind, String identifier, String metadata,
				String quantity, String fluidAmount, String chance,
				String alternativeGroup, boolean exact, String reason) {
			this.kind = kind;
			this.identifier = identifier;
			this.metadata = metadata;
			this.quantity = quantity;
			this.fluidAmount = fluidAmount;
			this.chance = chance;
			this.alternativeGroup = alternativeGroup;
			this.exact = exact;
			this.reason = reason;
		}

		ComponentValue withFluidAmount(String amount) {
			return new ComponentValue(kind, identifier, metadata, quantity,
					amount, chance, alternativeGroup, exact, reason);
		}

		static ComponentValue unresolved(String identifier, String reason) {
			return new ComponentValue("UNRESOLVED", identifier, "", "", "",
					"", "", false, reason);
		}
	}

	private static final class ComponentRow {
		final String[] values;
		final String kind;
		final String alternativeGroup;
		final String confidence;

		ComponentRow(String[] values, String kind, String alternativeGroup,
				String confidence) {
			this.values = values;
			this.kind = kind;
			this.alternativeGroup = alternativeGroup;
			this.confidence = confidence;
		}
	}

	private static final class CraftingIngredient {
		final String kind;
		final String identifier;
		final boolean exact;
		final String reason;
		final boolean empty;

		CraftingIngredient(String kind, String identifier, boolean exact,
				String reason, boolean empty) {
			this.kind = kind;
			this.identifier = identifier;
			this.exact = exact;
			this.reason = reason;
			this.empty = empty;
		}

		static CraftingIngredient empty() {
			return new CraftingIngredient("", "", true, "", true);
		}
	}

	static final class RuntimeSnapshot {
		final int itemCount;
		final int blockCount;
		final int craftingRecipeCount;
		final int smeltingRecipeCount;
		final int handlerCount;
		final int machineRecipeCount;
		final String fingerprint;

		RuntimeSnapshot(int itemCount, int blockCount, int craftingRecipeCount,
				int smeltingRecipeCount, int handlerCount,
				int machineRecipeCount, String fingerprint) {
			this.itemCount = itemCount;
			this.blockCount = blockCount;
			this.craftingRecipeCount = craftingRecipeCount;
			this.smeltingRecipeCount = smeltingRecipeCount;
			this.handlerCount = handlerCount;
			this.machineRecipeCount = machineRecipeCount;
			this.fingerprint = fingerprint;
		}

		@SuppressWarnings("unchecked")
		static RuntimeSnapshot capture() {
			List<String> evidence = new ArrayList<String>();
			int items = 0;
			for(Object object : Item.itemRegistry) {
				if(object instanceof Item) {
					items++;
					evidence.add("item:" + registryName((Item) object));
				}
			}
			int blocks = 0;
			for(Object object : Block.blockRegistry) {
				if(object instanceof Block) {
					blocks++;
					evidence.add("block:" + registryName((Block) object));
				}
			}
			List<Object> crafting = (List<Object>) CraftingManager.getInstance()
					.getRecipeList();
			for(Object object : crafting) {
				String output = "";
				if(object instanceof IRecipe) {
					try {
						ItemStack stack = ((IRecipe) object).getRecipeOutput();
						output = stack == null ? "" : registryName(stack.getItem())
								+ "#" + stack.getItemDamage();
					} catch(Throwable ex) {
						output = "unavailable:" + ex.getClass().getName();
					}
				}
				evidence.add("crafting:" + className(object) + ":" + output);
			}
			Map<ItemStack, ItemStack> smelting = FurnaceRecipes.smelting()
					.getSmeltingList();
			for(Map.Entry<ItemStack, ItemStack> entry : smelting.entrySet()) {
				evidence.add("smelting:" + stackKey(entry.getKey()) + "->"
						+ stackKey(entry.getValue()));
			}
			int machineRecipes = 0;
			for(SerializableRecipe handler : SerializableRecipe.recipeHandlers) {
				int count = containerSize(handler.getRecipeObject());
				machineRecipes += count;
				evidence.add("handler:" + handler.getClass().getName() + ":"
						+ safe(handler.getFileName()) + ":" + count);
			}
			Collections.sort(evidence);
			return new RuntimeSnapshot(items, blocks, crafting.size(),
					smelting.size(), SerializableRecipe.recipeHandlers.size(),
					machineRecipes, TaxonomyIo.sha256(join(evidence, "\n")));
		}

		private static int containerSize(Object value) {
			if(value == null) return 0;
			if(value instanceof Collection) return ((Collection<?>) value).size();
			if(value instanceof Map) {
				int count = 0;
				for(Object nested : ((Map<?, ?>) value).values()) {
					if(nested instanceof Collection) count += ((Collection<?>) nested).size();
					else if(nested != null && nested.getClass().isArray()) {
						count += Array.getLength(nested);
					} else count++;
				}
				return count;
			}
			if(value.getClass().isArray()) return Array.getLength(value);
			return 1;
		}

		private static String stackKey(ItemStack stack) {
			return stack == null ? "" : registryName(stack.getItem()) + "#"
					+ stack.getItemDamage() + "x" + stack.stackSize;
		}

		@Override
		public boolean equals(Object object) {
			if(!(object instanceof RuntimeSnapshot)) return false;
			RuntimeSnapshot other = (RuntimeSnapshot) object;
			return itemCount == other.itemCount
					&& blockCount == other.blockCount
					&& craftingRecipeCount == other.craftingRecipeCount
					&& smeltingRecipeCount == other.smeltingRecipeCount
					&& handlerCount == other.handlerCount
					&& machineRecipeCount == other.machineRecipeCount
					&& fingerprint.equals(other.fingerprint);
		}

		@Override
		public int hashCode() {
			return fingerprint.hashCode();
		}

		String describeDifference(RuntimeSnapshot other) {
			return "items " + itemCount + "->" + other.itemCount
					+ ", blocks " + blockCount + "->" + other.blockCount
					+ ", crafting " + craftingRecipeCount + "->"
					+ other.craftingRecipeCount + ", smelting "
					+ smeltingRecipeCount + "->" + other.smeltingRecipeCount
					+ ", handlers " + handlerCount + "->" + other.handlerCount
					+ ", machine recipes " + machineRecipeCount + "->"
					+ other.machineRecipeCount + ", fingerprint "
					+ fingerprint + "->" + other.fingerprint;
		}
	}

	private static final class ExportData {
		final List<String[]> achievements = new ArrayList<String[]>();
		final List<String[]> machines = new ArrayList<String[]>();
		final List<String[]> recipes = new ArrayList<String[]>();
		final List<String[]> components = new ArrayList<String[]>();
		final List<String[]> energyContracts = new ArrayList<String[]>();
		final List<String[]> dynamicHandlers = new ArrayList<String[]>();
		final List<String[]> prerequisites = new ArrayList<String[]>();
		final Set<String> inspectedFamilies = new HashSet<String>();
		final Set<String> exactFamilies = new HashSet<String>();
		final Set<String> partialFamilies = new HashSet<String>();
		final Set<String> unsupportedFamilies = new HashSet<String>();
		final Set<String> alternativeGroupIds = new HashSet<String>();
		final List<String> extractionErrors = new ArrayList<String>();
		final Map<String, String> recipeMachineById =
				new HashMap<String, String>();
		RuntimeSnapshot before;
		RuntimeSnapshot after;
		int customMachineRegistrationCount;
		int customMachineRecipeKeyCount;
		String coverageMarkdown;
		JsonObject gapReport;
		JsonObject inputManifest;
		JsonObject outputManifest;

		void sortAndValidate() throws IOException {
			for(List<String[]> report : Arrays.asList(achievements, machines,
					recipes, components, energyContracts, dynamicHandlers,
					prerequisites)) {
				Collections.sort(report, ROW_ORDER);
				for(String[] row : report) {
					for(String value : row) {
						if(value != null && value.indexOf('\r') >= 0) {
							throw new IOException("A report value contained an unnormalized CR.");
						}
					}
				}
			}
		}

		void buildCoverageAndGapReports() {
			Map<String, Integer> recipeConfidence = countBy(recipes, 13);
			Map<String, Integer> componentRole = countBy(components, 3);
			Map<String, Integer> componentKind = countBy(components, 4);
			Map<String, Integer> dynamicState = countBy(dynamicHandlers, 4);
			int iconsExact = countValue(achievements, 11, "true");
			int energyResolved = countValue(energyContracts, 11, "EXACT");
			int energyUnresolved = energyContracts.size() - energyResolved;
			int directPrerequisites = countValue(prerequisites, 4,
					"DIRECT_CRAFTING_DEPENDENCY");
			int transitivePrerequisites = countValue(prerequisites, 4,
					"TRANSITIVE_PRODUCTION_DEPENDENCY");

			StringBuilder markdown = new StringBuilder();
			markdown.append("# HBM progression export coverage\n\n");
			markdown.append("Schema: `").append(SCHEMA_VERSION).append("`\n\n");
			markdown.append("This report is extraction evidence only. It defines no research tiers, ")
					.append("locks, balance, or gameplay progression.\n\n");
			markdown.append("## Totals\n\n");
			markdown.append("- Achievements: ").append(achievements.size())
					.append(" (exact icons: ").append(iconsExact)
					.append(", unresolved icons: ")
					.append(achievements.size() - iconsExact).append(")\n");
			markdown.append("- Machine families inspected: ")
					.append(inspectedFamilies.size()).append("\n");
			markdown.append("- Machine families exact: ")
					.append(exactFamilies.size()).append("\n");
			markdown.append("- Machine families partial: ")
					.append(partialFamilies.size()).append("\n");
			markdown.append("- Machine families unsupported: ")
					.append(unsupportedFamilies.size()).append("\n");
			markdown.append("- Recipes by confidence: ")
					.append(formatCounts(recipeConfidence)).append("\n");
			markdown.append("- Components by role: ")
					.append(formatCounts(componentRole)).append("\n");
			markdown.append("- Components by kind: ")
					.append(formatCounts(componentKind)).append("\n");
			markdown.append("- Alternative groups: ")
					.append(alternativeGroupIds.size()).append("\n");
			markdown.append("- Energy contracts resolved: ")
					.append(energyResolved).append("\n");
			markdown.append("- Energy contracts unresolved: ")
					.append(energyUnresolved).append("\n");
			markdown.append("- Dynamic handlers by state: ")
					.append(formatCounts(dynamicState)).append("\n");
			markdown.append("- Runtime custom-machine configurations: ")
					.append(customMachineRegistrationCount).append("\n");
			markdown.append("- Runtime custom-machine recipe keys: ")
					.append(customMachineRecipeKeyCount).append("\n");
			markdown.append("- Direct prerequisite evidence: ")
					.append(directPrerequisites).append("\n");
			markdown.append("- Transitive production evidence: ")
					.append(transitivePrerequisites).append("\n\n");
			markdown.append("## Remaining safe-export gaps\n\n");
			markdown.append("- Runtime-configured custom machines do not have a stable one-to-one block identity.\n");
			markdown.append("- Shared and multiblock process handlers are not presented as one physical machine.\n");
			markdown.append("- Upgrade-dependent, idle, storage, and configurable machine energy values remain blank unless directly serialized.\n");
			markdown.append("- World-seeded MKU crafting inputs are not evaluated.\n");
			markdown.append("- Handler-specific scalar requirements remain labeled instead of being converted into item dependencies.\n");
			markdown.append("- Transitive production rows are mechanical evidence, not intentional design prerequisites.\n");
			if(!extractionErrors.isEmpty()) {
				markdown.append("\n## Aggregated extraction errors\n\n");
				List<String> sorted = new ArrayList<String>(extractionErrors);
				Collections.sort(sorted);
				for(String error : sorted) markdown.append("- ").append(error).append('\n');
			}
			coverageMarkdown = markdown.toString();

			JsonObject gap = new JsonObject();
			gap.addProperty("schema_version", SCHEMA_VERSION);
			gap.addProperty("adapter_version", ProgressionAdapters.ADAPTER_VERSION);
			gap.add("achievement_icons", totalsObject(achievements.size(),
					iconsExact, achievements.size() - iconsExact));
			JsonObject families = new JsonObject();
			families.addProperty("inspected", inspectedFamilies.size());
			families.addProperty("exact", exactFamilies.size());
			families.addProperty("partial", partialFamilies.size());
			families.addProperty("unsupported", unsupportedFamilies.size());
			families.add("unsupported_ids", jsonStrings(unsupportedFamilies));
			gap.add("machine_families", families);
			gap.add("recipes_by_confidence", jsonCounts(recipeConfidence));
			gap.add("components_by_role", jsonCounts(componentRole));
			gap.add("components_by_kind", jsonCounts(componentKind));
			gap.addProperty("alternative_group_count", alternativeGroupIds.size());
			JsonObject energy = new JsonObject();
			energy.addProperty("resolved", energyResolved);
			energy.addProperty("unresolved", energyUnresolved);
			gap.add("energy_contracts", energy);
			gap.add("dynamic_handlers_by_state", jsonCounts(dynamicState));
			gap.addProperty("custom_machine_registration_count",
					customMachineRegistrationCount);
			gap.addProperty("custom_machine_recipe_key_count",
					customMachineRecipeKeyCount);
			JsonObject prerequisite = new JsonObject();
			prerequisite.addProperty("direct", directPrerequisites);
			prerequisite.addProperty("transitive", transitivePrerequisites);
			gap.add("prerequisite_evidence", prerequisite);
			gap.add("extraction_errors", jsonStrings(extractionErrors));
			JsonArray remaining = new JsonArray();
			remaining.add(new JsonPrimitive("upgrade-dependent-energy"));
			remaining.add(new JsonPrimitive("world-seeded-dynamic-inputs"));
			remaining.add(new JsonPrimitive("shared-multiblock-machine-identity"));
			remaining.add(new JsonPrimitive("custom-machine-configuration-identity"));
			remaining.add(new JsonPrimitive("research-design-prerequisite-intent"));
			gap.add("remaining_gaps", remaining);
			gapReport = gap;
		}

		void buildInputManifest(File repository) {
			JsonObject manifest = new JsonObject();
			manifest.addProperty("schema_version", MANIFEST_VERSION);
			manifest.addProperty("progression_schema_version", SCHEMA_VERSION);
			manifest.addProperty("adapter_version", ProgressionAdapters.ADAPTER_VERSION);
			JsonArray sources = new JsonArray();
			Set<String> paths = new HashSet<String>();
			paths.add("src/main/java/com/hbm/main/MainRegistry.java");
			paths.add("src/main/java/com/hbm/main/CraftingManager.java");
			paths.add("src/main/java/com/hbm/inventory/recipes/loader/SerializableRecipe.java");
			paths.add("src/main/java/com/hbm/config/CustomMachineConfigJSON.java");
			for(HandlerSpec spec : ProgressionAdapters.handlers()) {
				paths.add("src/main/java/"
						+ handlerSourcePath(spec.handlerClass, repository));
			}
			for(DynamicHandlerSpec spec : ProgressionAdapters.dynamicHandlers()) {
				paths.add("src/main/java/" + spec.className.replace('.', '/') + ".java");
			}
			List<String> sorted = new ArrayList<String>(paths);
			Collections.sort(sorted);
			for(String path : sorted) {
				File file = new File(repository, path);
				JsonObject source = new JsonObject();
				source.addProperty("path", path.replace('\\', '/'));
				source.addProperty("present", file.isFile());
				if(file.isFile()) {
					try {
						source.addProperty("sha256", TaxonomyIo.sha256(file));
					} catch(IOException ex) {
						source.addProperty("sha256", "unavailable");
					}
				} else {
					source.addProperty("sha256", "");
				}
				sources.add(source);
			}
			manifest.add("source_files", sources);

			JsonArray existing = new JsonArray();
			File parent = new File(repository,
					"build" + File.separator + "reports" + File.separator + "woc");
			for(String name : Arrays.asList("items.csv", "blocks.csv",
					"crafting_recipes.csv", "smelting_recipes.csv",
					"machine_recipes.csv", "content_profile_skeleton.json")) {
				File file = new File(parent, name);
				JsonObject record = new JsonObject();
				record.addProperty("path", "build/reports/woc/" + name);
				record.addProperty("present", file.isFile());
				if(file.isFile()) {
					try {
						record.addProperty("sha256", TaxonomyIo.sha256(file));
					} catch(IOException ex) {
						record.addProperty("sha256", "unavailable");
					}
				} else {
					record.addProperty("sha256", "");
				}
				existing.add(record);
			}
			manifest.add("existing_exports", existing);
			manifest.add("runtime_snapshot_before", snapshotJson(before));
			inputManifest = manifest;
		}

		void buildOutputManifest(File staged) throws IOException {
			JsonObject manifest = new JsonObject();
			manifest.addProperty("schema_version", MANIFEST_VERSION);
			manifest.addProperty("progression_schema_version", SCHEMA_VERSION);
			manifest.addProperty("adapter_version", ProgressionAdapters.ADAPTER_VERSION);
			JsonArray outputs = new JsonArray();
			for(String name : PAYLOAD_FILES) {
				File file = new File(staged, name);
				JsonObject output = new JsonObject();
				output.addProperty("file", name);
				output.addProperty("sha256", TaxonomyIo.sha256(file));
				output.addProperty("row_count", rowCount(name));
				outputs.add(output);
			}
			manifest.add("payload_outputs", outputs);
			manifest.add("runtime_snapshot_before", snapshotJson(before));
			manifest.add("runtime_snapshot_after", snapshotJson(after));
			manifest.addProperty("runtime_mutation_detected", !before.equals(after));
			JsonObject counts = new JsonObject();
			counts.addProperty("achievements", achievements.size());
			counts.addProperty("machines", machines.size());
			counts.addProperty("machine_recipes", recipes.size());
			counts.addProperty("machine_recipe_components", components.size());
			counts.addProperty("energy_contracts", energyContracts.size());
			counts.addProperty("dynamic_handlers", dynamicHandlers.size());
			counts.addProperty("prerequisites", prerequisites.size());
			manifest.add("deterministic_counts", counts);
			outputManifest = manifest;
		}

		private int rowCount(String name) {
			if("achievement-progression.csv".equals(name)) return achievements.size();
			if("machines.csv".equals(name)) return machines.size();
			if("machine-recipes.csv".equals(name)) return recipes.size();
			if("machine-recipe-components.csv".equals(name)) return components.size();
			if("machine-energy-contracts.csv".equals(name)) return energyContracts.size();
			if("dynamic-recipe-handlers.csv".equals(name)) return dynamicHandlers.size();
			if("machine-prerequisites.csv".equals(name)) return prerequisites.size();
			return 1;
		}
	}

	private static String handlerSourcePath(String simpleName, File repository) {
		List<String> candidates = Arrays.asList(
				"com/hbm/inventory/recipes/" + simpleName + ".java",
				"com/hbm/inventory/recipes/anvil/" + simpleName + ".java",
				"com/hbm/inventory/recipes/loader/" + simpleName + ".java",
				"com/hbm/inventory/material/" + simpleName + ".java");
		for(String candidate : candidates) {
			if(new File(repository, "src/main/java/" + candidate).isFile()) {
				return candidate;
			}
		}
		return candidates.get(0);
	}

	private static JsonObject snapshotJson(RuntimeSnapshot snapshot) {
		JsonObject json = new JsonObject();
		json.addProperty("item_count", snapshot.itemCount);
		json.addProperty("block_count", snapshot.blockCount);
		json.addProperty("crafting_recipe_count", snapshot.craftingRecipeCount);
		json.addProperty("smelting_recipe_count", snapshot.smeltingRecipeCount);
		json.addProperty("machine_handler_count", snapshot.handlerCount);
		json.addProperty("machine_recipe_count", snapshot.machineRecipeCount);
		json.addProperty("fingerprint", snapshot.fingerprint);
		return json;
	}

	private static Map<String, Integer> countBy(List<String[]> rows, int index) {
		Map<String, Integer> counts = new TreeMap<String, Integer>();
		for(String[] row : rows) {
			String key = row[index].isEmpty() ? "BLANK" : row[index];
			Integer count = counts.get(key);
			counts.put(key, count == null ? 1 : count + 1);
		}
		return counts;
	}

	private static int countValue(List<String[]> rows, int index, String value) {
		int count = 0;
		for(String[] row : rows) if(value.equals(row[index])) count++;
		return count;
	}

	private static String formatCounts(Map<String, Integer> counts) {
		List<String> values = new ArrayList<String>();
		for(Map.Entry<String, Integer> entry : counts.entrySet()) {
			values.add(entry.getKey() + "=" + entry.getValue());
		}
		return join(values, ", ");
	}

	private static JsonObject jsonCounts(Map<String, Integer> counts) {
		JsonObject result = new JsonObject();
		for(Map.Entry<String, Integer> entry : counts.entrySet()) {
			result.addProperty(entry.getKey(), entry.getValue());
		}
		return result;
	}

	private static JsonArray jsonStrings(Collection<String> values) {
		List<String> sorted = new ArrayList<String>(values);
		Collections.sort(sorted);
		JsonArray result = new JsonArray();
		for(String value : sorted) result.add(new JsonPrimitive(value));
		return result;
	}

	private static JsonObject totalsObject(int total, int exact, int unresolved) {
		JsonObject result = new JsonObject();
		result.addProperty("total", total);
		result.addProperty("exact", exact);
		result.addProperty("unresolved", unresolved);
		return result;
	}

	public static final class ExportSummary {
		private final File outputDirectory;
		private final int achievementCount;
		private final int machineCount;
		private final int machineFamilyCount;
		private final int recipeCount;
		private final int componentCount;
		private final int energyContractCount;
		private final int dynamicHandlerCount;
		private final int prerequisiteCount;
		private final String runtimeFingerprint;

		ExportSummary(File outputDirectory, ExportData data) {
			this.outputDirectory = outputDirectory;
			this.achievementCount = data.achievements.size();
			this.machineCount = data.machines.size();
			this.machineFamilyCount = data.inspectedFamilies.size();
			this.recipeCount = data.recipes.size();
			this.componentCount = data.components.size();
			this.energyContractCount = data.energyContracts.size();
			this.dynamicHandlerCount = data.dynamicHandlers.size();
			this.prerequisiteCount = data.prerequisites.size();
			this.runtimeFingerprint = data.before.fingerprint;
		}

		public File getOutputDirectory() { return outputDirectory; }
		public int getAchievementCount() { return achievementCount; }
		public int getMachineCount() { return machineCount; }
		public int getMachineFamilyCount() { return machineFamilyCount; }
		public int getRecipeCount() { return recipeCount; }
		public int getComponentCount() { return componentCount; }
		public int getEnergyContractCount() { return energyContractCount; }
		public int getDynamicHandlerCount() { return dynamicHandlerCount; }
		public int getPrerequisiteCount() { return prerequisiteCount; }
		public String getRuntimeFingerprint() { return runtimeFingerprint; }
	}
}
