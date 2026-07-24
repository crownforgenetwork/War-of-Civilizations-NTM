package com.hbm.wocbridge.enforcement;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

public final class RuntimeContentKeys {

	public static final class CraftingRecord {
		public final IRecipe recipe;
		public final String recipeKey;
		public final String outputKey;

		private CraftingRecord(IRecipe recipe, String recipeKey, String outputKey) {
			this.recipe = recipe;
			this.recipeKey = recipeKey;
			this.outputKey = outputKey;
		}
	}

	public static final class SmeltingRecord {
		public final ItemStack input;
		public final ItemStack output;
		public final String recipeKey;
		public final String outputKey;

		private SmeltingRecord(ItemStack input, ItemStack output, String recipeKey,
				String outputKey) {
			this.input = input;
			this.output = output;
			this.recipeKey = recipeKey;
			this.outputKey = outputKey;
		}
	}

	private static final class CraftingRow {
		private static final Comparator<CraftingRow> ORDERING =
				new Comparator<CraftingRow>() {
					@Override
					public int compare(CraftingRow left, CraftingRow right) {
						int comparison = left.outputRegistry.compareTo(right.outputRegistry);
						if(comparison != 0) return comparison;
						comparison = Integer.compare(left.outputMetadata, right.outputMetadata);
						if(comparison != 0) return comparison;
						comparison = left.recipeClass.compareTo(right.recipeClass);
						if(comparison != 0) return comparison;
						return left.normalizedInputs.compareTo(right.normalizedInputs);
					}
				};

		private final IRecipe recipe;
		private final String recipeClass;
		private final String outputRegistry;
		private final int outputMetadata;
		private final int outputCount;
		private final String normalizedInputs;

		private CraftingRow(IRecipe recipe, ItemStack output, String normalizedInputs) {
			this.recipe = recipe;
			this.recipeClass = recipe.getClass().getName();
			this.outputRegistry = registryName(output);
			this.outputMetadata = output.getItemDamage();
			this.outputCount = output.stackSize;
			this.normalizedInputs = normalizedInputs;
		}
	}

	private static final class SmeltingRow {
		private static final Comparator<SmeltingRow> ORDERING =
				new Comparator<SmeltingRow>() {
					@Override
					public int compare(SmeltingRow left, SmeltingRow right) {
						int comparison = left.baseKey.compareTo(right.baseKey);
						if(comparison != 0) return comparison;
						return left.normalizedInput.compareTo(right.normalizedInput);
					}
				};

		private final ItemStack input;
		private final ItemStack output;
		private final String normalizedInput;
		private final String baseKey;

		private SmeltingRow(ItemStack input, ItemStack output) {
			this.input = input;
			this.output = output;
			this.normalizedInput = normalizeIngredient(input);
			String fingerprint = "smelting\n" + normalizeStack(output) + "\n"
					+ normalizedInput;
			this.baseKey = "smelting-recipe:" + keyOutput(output) + ":"
					+ sha256(fingerprint);
		}
	}

	private RuntimeContentKeys() { }

	@SuppressWarnings("unchecked")
	public static List<CraftingRecord> craftingRecords() {
		List<CraftingRow> rows = new ArrayList<CraftingRow>();
		for(Object object : (List<Object>) net.minecraft.item.crafting.CraftingManager
				.getInstance().getRecipeList()) {
			if(!(object instanceof IRecipe)) continue;
			IRecipe recipe = (IRecipe) object;
			ItemStack output;
			try {
				output = recipe.getRecipeOutput();
			} catch(Throwable ex) {
				continue;
			}
			if(output == null || !isHbmRegistryName(registryName(output))) continue;
			rows.add(new CraftingRow(recipe, output, normalizeCraftingInputs(recipe)));
		}
		Collections.sort(rows, CraftingRow.ORDERING);

		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		List<CraftingRecord> records = new ArrayList<CraftingRecord>();
		for(CraftingRow row : rows) {
			String fingerprint = row.recipeClass + "\n" + row.outputRegistry + "\n"
					+ row.outputMetadata + "\n" + row.outputCount + "\n"
					+ row.normalizedInputs;
			String baseKey = "crafting-recipe:" + row.outputRegistry + "#"
					+ row.outputMetadata + ":" + sha256(fingerprint);
			records.add(new CraftingRecord(row.recipe,
					withOccurrence(baseKey, occurrences),
					"item:" + row.outputRegistry + "#" + row.outputMetadata));
		}
		return records;
	}

	@SuppressWarnings("unchecked")
	public static List<SmeltingRecord> smeltingRecords() {
		List<SmeltingRow> rows = new ArrayList<SmeltingRow>();
		Map<ItemStack, ItemStack> recipes = FurnaceRecipes.smelting().getSmeltingList();
		for(Map.Entry<ItemStack, ItemStack> entry : recipes.entrySet()) {
			ItemStack input = entry.getKey();
			ItemStack output = entry.getValue();
			if(input == null || output == null) continue;
			if(!isHbmRegistryName(registryName(input))
					&& !isHbmRegistryName(registryName(output))) continue;
			rows.add(new SmeltingRow(input, output));
		}
		Collections.sort(rows, SmeltingRow.ORDERING);

		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		List<SmeltingRecord> records = new ArrayList<SmeltingRecord>();
		for(SmeltingRow row : rows) {
			records.add(new SmeltingRecord(row.input, row.output,
					withOccurrence(row.baseKey, occurrences),
					"item:" + keyOutput(row.output)));
		}
		return records;
	}

	public static String itemKey(ItemStack stack) {
		return stack == null || stack.getItem() == null
				? "" : "item:" + keyOutput(stack);
	}

	public static String blockKey(Block block, int metadata) {
		String name = registryName(block);
		return name.isEmpty() ? "" : "block:" + name + "#" + metadata;
	}

	public static String lootKey(String poolName, ItemStack stack, int minimum, int maximum,
			int weight) {
		String fingerprint = poolName + "\n" + normalizeStack(stack) + "\n"
				+ minimum + "\n" + maximum + "\n" + weight;
		return "loot:" + poolName + ":" + keyOutput(stack) + ":" + sha256(fingerprint);
	}

	public static String normalizeStack(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return "empty";
		StringBuilder value = new StringBuilder();
		value.append(registryName(stack)).append('#').append(stack.getItemDamage())
				.append('x').append(stack.stackSize);
		if(stack.hasTagCompound()) {
			value.append("{nbt=").append(stack.stackTagCompound.toString()).append('}');
		}
		return value.toString();
	}

	public static String registryName(ItemStack stack) {
		return stack == null ? "" : registryName(stack.getItem());
	}

	public static String registryName(Item item) {
		if(item == null) return "";
		Object name = Item.itemRegistry.getNameForObject(item);
		return name == null ? "" : name.toString();
	}

	public static String registryName(Block block) {
		if(block == null) return "";
		Object name = Block.blockRegistry.getNameForObject(block);
		return name == null ? "" : name.toString();
	}

	public static String canonicalJson(JsonElement element) {
		if(element == null || element.isJsonNull()) return "null";
		if(element.isJsonPrimitive()) return element.toString();
		if(element.isJsonArray()) {
			List<String> values = new ArrayList<String>();
			for(JsonElement child : element.getAsJsonArray()) {
				values.add(canonicalJson(child));
			}
			return "[" + join(values, ",") + "]";
		}
		List<String> names = new ArrayList<String>();
		for(Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			names.add(entry.getKey());
		}
		Collections.sort(names);
		List<String> values = new ArrayList<String>();
		for(String name : names) {
			values.add("\"" + jsonEscape(name) + "\":"
					+ canonicalJson(element.getAsJsonObject().get(name)));
		}
		return "{" + join(values, ",") + "}";
	}

	public static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for(byte current : hash) {
				hex.append(String.format(Locale.US, "%02x", current & 0xFF));
			}
			return hex.toString();
		} catch(NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private static String normalizeCraftingInputs(IRecipe recipe) {
		try {
			if(recipe instanceof ShapedRecipes) {
				return normalizeShaped(Arrays.asList(((ShapedRecipes) recipe).recipeItems));
			}
			if(recipe instanceof ShapelessRecipes) {
				return normalizeShapeless(((ShapelessRecipes) recipe).recipeItems);
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
		for(Object ingredient : ingredients) normalized.add(normalizeIngredient(ingredient));
		return "[" + join(normalized, ";") + "]";
	}

	private static String normalizeShapeless(Collection<?> ingredients) {
		List<String> normalized = new ArrayList<String>();
		for(Object ingredient : ingredients) normalized.add(normalizeIngredient(ingredient));
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

	private static String keyOutput(ItemStack stack) {
		return registryName(stack) + "#" + stack.getItemDamage();
	}

	private static boolean isHbmRegistryName(String name) {
		return name != null && name.startsWith("hbm:");
	}

	private static String withOccurrence(String baseKey, Map<String, Integer> occurrences) {
		Integer previous = occurrences.get(baseKey);
		int occurrence = previous == null ? 1 : previous + 1;
		occurrences.put(baseKey, occurrence);
		return baseKey + ":" + occurrence;
	}

	private static String jsonEscape(String value) {
		return new Gson().toJson(safe(value))
				.replaceFirst("^\"", "").replaceFirst("\"$", "");
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
}
