package com.hbm.wocbridge.enforcement;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.wocbridge.enforcement.EnforcementAdapterResult.ReloadClassification;

import net.minecraft.item.Item;

public final class MachineRecipeEnforcer {

	private static final class OutputStack {
		private static final Comparator<OutputStack> ORDERING =
				new Comparator<OutputStack>() {
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

	private static final class MachineRecord {
		private static final Comparator<MachineRecord> ORDERING =
				new Comparator<MachineRecord>() {
					@Override
					public int compare(MachineRecord left, MachineRecord right) {
						int comparison = left.manager.compareTo(right.manager);
						if(comparison != 0) return comparison;
						comparison = left.outputRegistry.compareTo(right.outputRegistry);
						if(comparison != 0) return comparison;
						return left.normalizedRecipe.compareTo(right.normalizedRecipe);
					}
				};

		private final SerializableRecipe handler;
		private final Object container;
		private final Object owner;
		private final String recipeClass;
		private final String recipeObjectClass;
		private final String manager;
		private final String machineFamily;
		private final String outputRegistry;
		private final String normalizedRecipe;
		private final List<String> outputKeys;
		private final boolean safelyRemovable;
		private String exactKey;

		private MachineRecord(SerializableRecipe handler, Object container, Object owner,
				JsonObject recipe, boolean safelyRemovable) {
			this.handler = handler;
			this.container = container;
			this.owner = owner;
			this.recipeClass = handler.getClass().getName();
			this.recipeObjectClass = owner == null ? "" : owner.getClass().getName();
			this.manager = safe(handler.getFileName());
			this.machineFamily = machineFamily(handler);
			this.normalizedRecipe = RuntimeContentKeys.canonicalJson(recipe);
			List<OutputStack> outputs = findOutputStacks(recipe);
			this.outputRegistry = joinOutputField(outputs);
			this.outputKeys = new ArrayList<String>();
			for(OutputStack output : outputs) {
				outputKeys.add("item:" + output.registryName + "#" + output.metadata);
			}
			this.safelyRemovable = safelyRemovable;
		}
	}

	private MachineRecipeEnforcer() { }

	public static EnforcementAdapterResult apply(ContentEnforcementPolicy policy,
			boolean mutate) {
		EnforcementAdapterResult result = new EnforcementAdapterResult(
				"machine", ReloadClassification.RESTART_REQUIRED);
		if(!policy.isActive()) return result;

		List<MachineRecord> records = collectRecords(result);
		Collections.sort(records, MachineRecord.ORDERING);
		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		List<MachineRecord> selected = new ArrayList<MachineRecord>();
		for(MachineRecord record : records) {
			result.examined();
			String fingerprint = record.recipeClass + "\n" + record.recipeObjectClass + "\n"
					+ record.manager + "\n" + record.normalizedRecipe;
			String output = record.outputRegistry.isEmpty()
					? record.machineFamily : record.outputRegistry.replace(';', '+');
			String baseKey = "machine-recipe:" + output + ":"
					+ RuntimeContentKeys.sha256(fingerprint);
			Integer previous = occurrences.get(baseKey);
			int occurrence = previous == null ? 1 : previous + 1;
			occurrences.put(baseKey, occurrence);
			record.exactKey = baseKey + ":" + occurrence;

			String matched = firstDisabled(policy, record);
			if(matched.isEmpty()) continue;
			if(!record.safelyRemovable) {
				result.unsupported(record.manager + " -> " + matched,
						"serialized owner expands to multiple recipe rows");
				continue;
			}
			result.planned(matched);
			selected.add(record);
		}
		if(!mutate) return result;

		for(MachineRecord record : selected) {
			try {
				if(remove(record.container, record.owner)) {
					result.mutated();
				} else {
					result.warning(record.manager + ": selected recipe was not removed");
				}
			} catch(UnsupportedOperationException ex) {
				result.unsupported(record.manager,
						"recipe container rejected removal");
			} catch(Throwable ex) {
				result.warning(record.manager + ": removal failed with "
						+ ex.getClass().getName());
			}
		}
		return result;
	}

	private static List<MachineRecord> collectRecords(EnforcementAdapterResult result) {
		List<SerializableRecipe> handlers =
				new ArrayList<SerializableRecipe>(SerializableRecipe.recipeHandlers);
		Collections.sort(handlers, new Comparator<SerializableRecipe>() {
			@Override
			public int compare(SerializableRecipe left, SerializableRecipe right) {
				int comparison = safe(left.getFileName()).compareTo(safe(right.getFileName()));
				if(comparison != 0) return comparison;
				return left.getClass().getName().compareTo(right.getClass().getName());
			}
		});

		List<MachineRecord> records = new ArrayList<MachineRecord>();
		for(SerializableRecipe handler : handlers) {
			String manager = safe(handler.getFileName());
			Object container;
			try {
				container = handler.getRecipeObject();
			} catch(Throwable ex) {
				result.unsupported(manager, "getRecipeObject failed: "
						+ ex.getClass().getName());
				continue;
			}
			List<Object> owners = flattenContainer(container);
			if(owners == null) {
				result.unsupported(manager, container == null
						? "null recipe container"
						: "unsupported container " + container.getClass().getName());
				continue;
			}
			result.covered(manager);
			for(Object owner : owners) {
				try {
					JsonObject serialized = serialize(handler, owner);
					List<JsonObject> flattened = flattenNested(serialized);
					boolean safelyRemovable = flattened.size() == 1;
					for(JsonObject recipe : flattened) {
						records.add(new MachineRecord(handler, container, owner,
								recipe, safelyRemovable));
					}
				} catch(Throwable ex) {
					result.unsupported(manager, "serializer failed for "
							+ (owner == null ? "null" : owner.getClass().getName())
							+ ": " + ex.getClass().getName());
				}
			}
		}
		return records;
	}

	private static List<Object> flattenContainer(Object container) {
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
			return null;
		}
		return null;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean remove(Object container, Object owner) {
		if(container instanceof Map && owner instanceof Map.Entry) {
			return ((Map) container).entrySet().remove(owner);
		}
		if(container instanceof Collection) {
			return ((Collection) container).remove(owner);
		}
		return false;
	}

	private static JsonObject serialize(SerializableRecipe handler, Object recipe)
			throws IOException {
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

	private static List<JsonObject> flattenNested(JsonObject serialized) {
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
				boolean childChance = chanceStackContext || "outputitems".equals(field);
				collectOutputStacks(entry.getValue(), childOutput, childChance, outputs);
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

	private static boolean isOutputField(String field) {
		return field.contains("output") || field.contains("result")
				|| field.contains("payout");
	}

	private static OutputStack parseOutputStack(JsonArray array, boolean chanceStack) {
		if(array.size() == 0 || !array.get(0).isJsonPrimitive()) return null;
		JsonPrimitive first = array.get(0).getAsJsonPrimitive();
		if(!first.isString()) return null;
		String type = first.getAsString();
		if("single".equals(type) || "multi".equals(type) || !type.contains(":")) return null;
		if(!(Item.itemRegistry.getObject(type) instanceof Item)) return null;
		int count = chanceStack ? integerAt(array, array.size() >= 3 ? 1 : -1, 1)
				: integerAt(array, 1, 1);
		int metadata = chanceStack ? integerAt(array, array.size() >= 4 ? 2 : -1, 0)
				: integerAt(array, 2, 0);
		return new OutputStack(type, metadata, count);
	}

	private static int integerAt(JsonArray array, int index, int fallback) {
		if(index < 0 || index >= array.size() || !array.get(index).isJsonPrimitive()) {
			return fallback;
		}
		JsonPrimitive value = array.get(index).getAsJsonPrimitive();
		if(!value.isNumber()) return fallback;
		try {
			return value.getAsInt();
		} catch(NumberFormatException ex) {
			return fallback;
		}
	}

	private static String joinOutputField(List<OutputStack> outputs) {
		StringBuilder value = new StringBuilder();
		for(OutputStack output : outputs) {
			if(value.length() > 0) value.append(';');
			value.append(output.registryName);
		}
		return value.toString();
	}

	private static String firstDisabled(ContentEnforcementPolicy policy,
			MachineRecord record) {
		if(policy.isDisabled(record.exactKey)) return record.exactKey;
		for(String outputKey : record.outputKeys) {
			if(policy.isDisabled(outputKey)) return outputKey;
		}
		return "";
	}

	private static String machineFamily(SerializableRecipe handler) {
		String fileName = safe(handler.getFileName());
		if(fileName.toLowerCase(Locale.US).endsWith(".json")) {
			fileName = fileName.substring(0, fileName.length() - 5);
		}
		if(fileName.startsWith("hbm")) fileName = fileName.substring(3);
		return fileName.isEmpty() ? handler.getClass().getSimpleName() : fileName;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
