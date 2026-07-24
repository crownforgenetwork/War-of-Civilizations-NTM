package com.hbm.wocbridge.taxonomy;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ContentTaxonomyLoader {

	public static final class LoadResult {
		private final ContentTaxonomy taxonomy;
		private final List<String> errors;

		private LoadResult(ContentTaxonomy taxonomy, List<String> errors) {
			this.taxonomy = taxonomy;
			this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
		}

		public ContentTaxonomy getTaxonomy() {
			return taxonomy;
		}

		public List<String> getErrors() {
			return errors;
		}
	}

	public LoadResult load(File file) {
		List<String> errors = new ArrayList<String>();
		if(file == null || !file.isFile()) {
			errors.add("TAXONOMY_MISSING: taxonomy definition is missing");
			return new LoadResult(null, errors);
		}
		JsonElement parsed;
		try(Reader reader = new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8)) {
			parsed = new JsonParser().parse(reader);
		} catch(Exception ex) {
			errors.add("MALFORMED_JSON: " + conciseMessage(ex));
			return new LoadResult(null, errors);
		}
		if(parsed == null || !parsed.isJsonObject()) {
			errors.add("INVALID_ROOT: taxonomy root must be an object");
			return new LoadResult(null, errors);
		}

		JsonObject root = parsed.getAsJsonObject();
		ContentTaxonomy taxonomy = new ContentTaxonomy(
				integer(root, "taxonomySchemaVersion", 0, errors, ""),
				string(root, "taxonomyId", errors, ""),
				string(root, "displayName", errors, ""),
				string(root, "description", errors, ""),
				string(root, "source", errors, ""),
				string(root, "reviewStatus", errors, ""),
				string(root, "defaultDisposition", errors, ""),
				families(root, errors),
				overrides(root, errors),
				strings(root, "notes", errors, ""));
		Collections.sort(errors);
		return new LoadResult(taxonomy, errors);
	}

	private static List<ContentTaxonomyFamily> families(
			JsonObject root, List<String> errors) {
		List<ContentTaxonomyFamily> result =
				new ArrayList<ContentTaxonomyFamily>();
		JsonArray array = array(root, "families", errors, "");
		for(int index = 0; index < array.size(); index++) {
			String path = "families[" + index + "]";
			JsonObject object = object(array.get(index), errors, path);
			if(object == null) continue;
			result.add(new ContentTaxonomyFamily(
					string(object, "familyId", errors, path),
					string(object, "displayName", errors, path),
					string(object, "description", errors, path),
					optionalString(object, "parentFamilyId", errors, path),
					strings(object, "aliases", errors, path),
					strings(object, "tags", errors, path),
					strings(object, "contentKinds", errors, path),
					selectors(object, "includeSelectors", errors, path),
					selectors(object, "excludeSelectors", errors, path),
					string(object, "defaultDisposition", errors, path),
					optionalString(object, "researchDomain", errors, path),
					optionalString(object, "researchTier", errors, path),
					strings(object, "prerequisiteFamilyHints", errors, path),
					string(object, "strategicClass", errors, path),
					string(object, "replacementPolicy", errors, path),
					string(object, "futureOwnerModule", errors, path),
					bool(object, "projectCandidate", false, errors, path),
					bool(object, "licenseCandidate", false, errors, path),
					bool(object, "prototypeEventCandidate", false, errors, path),
					string(object, "strategicSensitivity", errors, path),
					string(object, "reviewStatus", errors, path),
					string(object, "rationale", errors, path),
					strings(object, "notes", errors, path)));
		}
		return result;
	}

	private static List<ContentTaxonomyRule> overrides(
			JsonObject root, List<String> errors) {
		List<ContentTaxonomyRule> result = new ArrayList<ContentTaxonomyRule>();
		JsonArray array = array(root, "exactOverrides", errors, "");
		for(int index = 0; index < array.size(); index++) {
			String path = "exactOverrides[" + index + "]";
			JsonObject object = object(array.get(index), errors, path);
			if(object == null) continue;
			result.add(new ContentTaxonomyRule(
					string(object, "ruleId", errors, path),
					string(object, "canonicalKey", errors, path),
					strings(object, "tags", errors, path),
					string(object, "disposition", errors, path),
					optionalString(object, "researchDomain", errors, path),
					optionalString(object, "researchTier", errors, path),
					string(object, "strategicClass", errors, path),
					string(object, "replacementPolicy", errors, path),
					string(object, "futureOwnerModule", errors, path),
					string(object, "strategicSensitivity", errors, path),
					string(object, "reviewStatus", errors, path),
					string(object, "rationale", errors, path)));
		}
		return result;
	}

	private static List<ContentTaxonomySelector> selectors(
			JsonObject object, String name, List<String> errors, String parentPath) {
		List<ContentTaxonomySelector> result =
				new ArrayList<ContentTaxonomySelector>();
		JsonArray array = array(object, name, errors, parentPath);
		for(int index = 0; index < array.size(); index++) {
			String path = qualify(parentPath, name) + "[" + index + "]";
			JsonObject selector = object(array.get(index), errors, path);
			if(selector == null) continue;
			List<String> canonicalKeys = strings(selector, "canonicalKeys", errors, path);
			String canonicalKey = optionalString(
					selector, "canonicalKey", errors, path);
			if(!canonicalKey.isEmpty()) canonicalKeys.add(canonicalKey);
			result.add(new ContentTaxonomySelector(
					string(selector, "selectorId", errors, path),
					canonicalKeys,
					strings(selector, "registryNames", errors, path),
					optionalString(selector, "namespace", errors, path),
					optionalString(selector, "contentKind", errors, path),
					optionalString(selector, "registryName", errors, path),
					optionalString(selector, "registryNamePrefix", errors, path),
					optionalString(selector, "registryNameSuffix", errors, path),
					optionalInteger(selector, "metadata", errors, path),
					optionalInteger(selector, "metadataMin", errors, path),
					optionalInteger(selector, "metadataMax", errors, path),
					optionalString(selector, "exporterCategory", errors, path),
					optionalString(selector, "recipeManager", errors, path),
					optionalString(selector, "machineFamily", errors, path),
					optionalString(selector, "javaClass", errors, path),
					optionalString(selector, "javaClassPrefix", errors, path),
					optionalString(selector, "sourceClass", errors, path),
					optionalString(selector, "sourceClassPrefix", errors, path)));
			for(Map.Entry<String, JsonElement> entry : selector.entrySet()) {
				if(entry.getKey().toLowerCase().contains("regex")) {
					errors.add(path + ": regex selectors are not supported");
				}
			}
		}
		return result;
	}

	private static JsonArray array(JsonObject object, String name,
			List<String> errors, String path) {
		JsonElement element = object.get(name);
		if(element == null) return new JsonArray();
		if(!element.isJsonArray()) {
			errors.add(qualify(path, name) + ": must be an array");
			return new JsonArray();
		}
		return element.getAsJsonArray();
	}

	private static JsonObject object(JsonElement element,
			List<String> errors, String path) {
		if(element == null || !element.isJsonObject()) {
			errors.add(path + ": must be an object");
			return null;
		}
		return element.getAsJsonObject();
	}

	private static List<String> strings(JsonObject object, String name,
			List<String> errors, String path) {
		List<String> result = new ArrayList<String>();
		JsonElement element = object.get(name);
		if(element == null) return result;
		if(!element.isJsonArray()) {
			errors.add(qualify(path, name) + ": must be an array");
			return result;
		}
		int index = 0;
		for(JsonElement value : element.getAsJsonArray()) {
			if(value == null || !value.isJsonPrimitive()
					|| !value.getAsJsonPrimitive().isString()) {
				errors.add(qualify(path, name) + "[" + index + "]: must be a string");
			} else {
				result.add(value.getAsString());
			}
			index++;
		}
		return result;
	}

	private static String string(JsonObject object, String name,
			List<String> errors, String path) {
		String value = optionalString(object, name, errors, path);
		if(value.isEmpty()) errors.add(qualify(path, name) + ": is required");
		return value;
	}

	private static String optionalString(JsonObject object, String name,
			List<String> errors, String path) {
		JsonElement element = object.get(name);
		if(element == null || element.isJsonNull()) return "";
		if(!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			errors.add(qualify(path, name) + ": must be a string");
			return "";
		}
		return element.getAsString();
	}

	private static int integer(JsonObject object, String name, int fallback,
			List<String> errors, String path) {
		Integer value = optionalInteger(object, name, errors, path);
		if(value == null) {
			errors.add(qualify(path, name) + ": is required");
			return fallback;
		}
		return value.intValue();
	}

	private static Integer optionalInteger(JsonObject object, String name,
			List<String> errors, String path) {
		JsonElement element = object.get(name);
		if(element == null || element.isJsonNull()) return null;
		try {
			return Integer.valueOf(element.getAsInt());
		} catch(Exception ex) {
			errors.add(qualify(path, name) + ": must be an integer");
			return null;
		}
	}

	private static boolean bool(JsonObject object, String name, boolean fallback,
			List<String> errors, String path) {
		JsonElement element = object.get(name);
		if(element == null) return fallback;
		if(!element.isJsonPrimitive()
				|| !element.getAsJsonPrimitive().isBoolean()) {
			errors.add(qualify(path, name) + ": must be a boolean");
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static String qualify(String path, String name) {
		return path.isEmpty() ? name : path + "." + name;
	}

	private static String conciseMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null || message.isEmpty()
				? exception.getClass().getSimpleName() : message;
	}
}
