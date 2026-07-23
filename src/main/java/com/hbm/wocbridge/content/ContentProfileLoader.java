package com.hbm.wocbridge.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hbm.wocbridge.content.ProfileValidationIssue.Severity;

public final class ContentProfileLoader {

	public static final class LoadResult {
		private final ContentProfile profile;
		private final List<ProfileValidationIssue> issues;
		private final boolean missing;

		private LoadResult(ContentProfile profile, List<ProfileValidationIssue> issues,
				boolean missing) {
			this.profile = profile;
			this.issues = Collections.unmodifiableList(
					new ArrayList<ProfileValidationIssue>(issues));
			this.missing = missing;
		}

		public ContentProfile getProfile() {
			return profile;
		}

		public List<ProfileValidationIssue> getIssues() {
			return issues;
		}

		public boolean isMissing() {
			return missing;
		}
	}

	public LoadResult load(File file, boolean strict) {
		List<ProfileValidationIssue> issues = new ArrayList<ProfileValidationIssue>();
		if(file == null || !file.isFile()) {
			issues.add(new ProfileValidationIssue(Severity.WARNING, "PROFILE_MISSING", "",
					"Profile file is missing; the in-memory AVAILABLE fallback will be used."));
			return new LoadResult(null, issues, true);
		}

		JsonElement parsed;
		try(Reader reader = new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8)) {
			parsed = new JsonParser().parse(reader);
		} catch(Exception ex) {
			issues.add(new ProfileValidationIssue(Severity.ERROR, "MALFORMED_JSON", "",
					"Unable to parse profile JSON: " + conciseMessage(ex)));
			return new LoadResult(null, issues, false);
		}
		if(parsed == null || !parsed.isJsonObject()) {
			issues.add(new ProfileValidationIssue(Severity.ERROR, "INVALID_ROOT", "",
					"Profile root must be a JSON object."));
			return new LoadResult(null, issues, false);
		}

		JsonObject root = parsed.getAsJsonObject();
		int schemaVersion = readSchemaVersion(root, issues);
		String profileId = readProfileId(root, strict, issues);
		ContentState defaultState = readDefaultState(root, strict, issues);
		String notes = readOptionalString(root, "notes", strict, "", issues);
		List<ContentRule> rules = readRules(root, strict, issues);
		Map<String, List<ContentKey>> tags = readTags(root, strict, issues);

		return new LoadResult(new ContentProfile(schemaVersion, profileId, defaultState,
				rules, tags, notes), issues, false);
	}

	private static int readSchemaVersion(JsonObject root, List<ProfileValidationIssue> issues) {
		JsonElement value = root.get("schemaVersion");
		if(value == null || !value.isJsonPrimitive()
				|| !value.getAsJsonPrimitive().isNumber()) {
			issues.add(new ProfileValidationIssue(Severity.ERROR, "INVALID_SCHEMA_VERSION", "",
					"schemaVersion must be the number 1."));
			return 1;
		}
		try {
			int version = value.getAsInt();
			if(version != 1) {
				issues.add(new ProfileValidationIssue(Severity.ERROR, "UNSUPPORTED_SCHEMA", "",
						"Unsupported schemaVersion " + version + "; only version 1 is supported."));
			}
			return version;
		} catch(Exception ex) {
			issues.add(new ProfileValidationIssue(Severity.ERROR, "INVALID_SCHEMA_VERSION", "",
					"schemaVersion must be the number 1."));
			return 1;
		}
	}

	private static String readProfileId(JsonObject root, boolean strict,
			List<ProfileValidationIssue> issues) {
		JsonElement value = root.get("profileId");
		if(value == null || !isString(value) || value.getAsString().isEmpty()) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "INVALID_PROFILE_ID", "",
					"profileId must be a non-empty string."));
			return "unnamed_profile";
		}
		return value.getAsString();
	}

	private static ContentState readDefaultState(JsonObject root, boolean strict,
			List<ProfileValidationIssue> issues) {
		JsonElement value = root.get("defaultState");
		if(value == null) return ContentState.AVAILABLE;
		if(!isString(value)) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "INVALID_DEFAULT_STATE", "",
					"defaultState must be a recognized state name."));
			return ContentState.AVAILABLE;
		}
		ContentState state = ContentState.fromName(value.getAsString());
		if(state == null) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "INVALID_DEFAULT_STATE", "",
					"Unknown defaultState '" + value.getAsString()
							+ "'; AVAILABLE was substituted."));
			return ContentState.AVAILABLE;
		}
		return state;
	}

	private static List<ContentRule> readRules(JsonObject root, boolean strict,
			List<ProfileValidationIssue> issues) {
		JsonElement rulesElement = root.get("rules");
		JsonElement entriesElement = root.get("entries");
		if(rulesElement != null && entriesElement != null) {
			issues.add(new ProfileValidationIssue(recoverable(strict),
					"AMBIGUOUS_RULE_COLLECTION", "",
					"Both rules and legacy entries are present; rules was used."));
		}
		if(rulesElement == null && entriesElement != null) {
			rulesElement = entriesElement;
			issues.add(new ProfileValidationIssue(Severity.WARNING, "LEGACY_ENTRIES_MIGRATED", "",
					"Phase 1 entries was accepted as schema-v1 rules without renaming keys or kinds."));
		}
		if(rulesElement == null) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "MISSING_RULES", "",
					"Profile has no rules array; an empty rule set was used."));
			return new ArrayList<ContentRule>();
		}
		if(!rulesElement.isJsonArray()) {
			issues.add(new ProfileValidationIssue(Severity.ERROR, "INVALID_RULES", "",
					"rules must be a JSON array."));
			return new ArrayList<ContentRule>();
		}

		List<ContentRule> rules = new ArrayList<ContentRule>();
		JsonArray array = rulesElement.getAsJsonArray();
		for(int index = 0; index < array.size(); index++) {
			JsonElement element = array.get(index);
			String location = "rules[" + index + "]";
			if(element == null || !element.isJsonObject()) {
				issues.add(new ProfileValidationIssue(recoverable(strict),
						"MALFORMED_RULE", location,
						"Rule must be a JSON object and was skipped."));
				continue;
			}
			ContentRule rule = readRule(element.getAsJsonObject(), strict, location, issues);
			if(rule != null) rules.add(rule);
		}
		return rules;
	}

	private static ContentRule readRule(JsonObject object, boolean strict, String location,
			List<ProfileValidationIssue> issues) {
		String rawKey = readRequiredString(object, "key", strict, location, issues);
		String rawKind = readRequiredString(object, "kind", strict, location, issues);
		String rawState = readRequiredString(object, "state", strict, location, issues);
		if(rawKey == null || rawKind == null || rawState == null) return null;

		ContentKind kind = ContentKind.fromName(rawKind);
		if(kind == null) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "UNKNOWN_KIND", rawKey,
					"Unknown content kind '" + rawKind + "'; rule was skipped."));
			return null;
		}
		ContentState state = ContentState.fromName(rawState);
		if(state == null) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "UNKNOWN_STATE", rawKey,
					"Unknown content state '" + rawState + "'; rule was skipped."));
			return null;
		}

		ContentKey key;
		try {
			key = ContentKey.parse(rawKey, kind);
		} catch(IllegalArgumentException ex) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "MALFORMED_KEY", rawKey,
					ex.getMessage() + "; rule was skipped."));
			return null;
		}

		String notes = readOptionalString(object, "notes", strict, rawKey, issues);
		String unlockTechnology = readOptionalString(
				object, "unlockTechnology", strict, rawKey, issues);
		String requiredProject = readOptionalString(
				object, "requiredProject", strict, rawKey, issues);
		String replacementKey = readReservedKey(
				object, "replacementKey", "replacement", strict, rawKey, issues);
		String salvageKey = readReservedKey(
				object, "salvageKey", "salvage", strict, rawKey, issues);
		String eventMetadata = readMetadata(
				object, "eventMetadata", strict, rawKey, issues);
		String adminMetadata = readMetadata(
				object, "adminMetadata", strict, rawKey, issues);

		return new ContentRule(key, state, notes, unlockTechnology, requiredProject,
				replacementKey, salvageKey, eventMetadata, adminMetadata);
	}

	private static Map<String, List<ContentKey>> readTags(JsonObject root, boolean strict,
			List<ProfileValidationIssue> issues) {
		Map<String, List<ContentKey>> tags = new LinkedHashMap<String, List<ContentKey>>();
		JsonElement tagsElement = root.get("tags");
		if(tagsElement == null) return tags;
		if(!tagsElement.isJsonObject()) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "INVALID_TAGS", "",
					"tags must be a JSON object; tags were skipped."));
			return tags;
		}

		for(Map.Entry<String, JsonElement> entry : tagsElement.getAsJsonObject().entrySet()) {
			String tagName = entry.getKey();
			JsonElement membersElement = entry.getValue();
			if(!membersElement.isJsonArray()) {
				issues.add(new ProfileValidationIssue(recoverable(strict),
						"INVALID_TAG_MEMBERS", tagName,
						"Tag members must be a JSON array; tag was skipped."));
				continue;
			}
			List<ContentKey> members = new ArrayList<ContentKey>();
			JsonArray memberArray = membersElement.getAsJsonArray();
			for(int index = 0; index < memberArray.size(); index++) {
				JsonElement memberElement = memberArray.get(index);
				if(!isString(memberElement)) {
					issues.add(new ProfileValidationIssue(recoverable(strict),
							"MALFORMED_TAG_MEMBER", tagName,
							"Tag member " + index + " is not a string and was skipped."));
					continue;
				}
				String rawMember = memberElement.getAsString();
				try {
					members.add(ContentKey.parse(rawMember));
				} catch(IllegalArgumentException ex) {
					issues.add(new ProfileValidationIssue(recoverable(strict),
							"MALFORMED_TAG_MEMBER", rawMember,
							"Tag '" + tagName + "': " + ex.getMessage() + "; member was skipped."));
				}
			}
			tags.put(tagName, members);
		}
		return tags;
	}

	private static String readRequiredString(JsonObject object, String name, boolean strict,
			String affectedKey, List<ProfileValidationIssue> issues) {
		JsonElement value = object.get(name);
		if(value == null || !isString(value) || value.getAsString().isEmpty()) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "MISSING_RULE_FIELD",
					affectedKey, "Rule field '" + name + "' must be a non-empty string."));
			return null;
		}
		return value.getAsString();
	}

	private static String readOptionalString(JsonObject object, String name, boolean strict,
			String affectedKey, List<ProfileValidationIssue> issues) {
		JsonElement value = object.get(name);
		if(value == null || value.isJsonNull()) return "";
		if(!isString(value)) {
			issues.add(new ProfileValidationIssue(recoverable(strict),
					"INVALID_" + name.toUpperCase(Locale.US), affectedKey,
					"Optional field '" + name + "' must be a string and was ignored."));
			return "";
		}
		return value.getAsString();
	}

	private static String readReservedKey(JsonObject object, String canonicalName,
			String aliasName, boolean strict, String affectedKey,
			List<ProfileValidationIssue> issues) {
		String canonical = readOptionalString(
				object, canonicalName, strict, affectedKey, issues);
		String alias = readOptionalString(object, aliasName, strict, affectedKey, issues);
		if(!canonical.isEmpty() && !alias.isEmpty() && !canonical.equals(alias)) {
			issues.add(new ProfileValidationIssue(recoverable(strict),
					"CONFLICTING_RESERVED_FIELD", affectedKey,
					"Fields '" + canonicalName + "' and '" + aliasName
							+ "' conflict; " + canonicalName + " was used."));
		}
		return canonical.isEmpty() ? alias : canonical;
	}

	private static String readMetadata(JsonObject object, String name, boolean strict,
			String affectedKey, List<ProfileValidationIssue> issues) {
		JsonElement value = object.get(name);
		if(value == null || value.isJsonNull()) return "";
		if(!value.isJsonObject()) {
			issues.add(new ProfileValidationIssue(recoverable(strict),
					"INVALID_" + name.toUpperCase(Locale.US), affectedKey,
					"Reserved field '" + name + "' must be a JSON object and was ignored."));
			return "";
		}
		return canonicalJson(value);
	}

	private static boolean isString(JsonElement element) {
		if(element == null || !element.isJsonPrimitive()) return false;
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		return primitive.isString();
	}

	private static Severity recoverable(boolean strict) {
		return strict ? Severity.ERROR : Severity.WARNING;
	}

	private static String conciseMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null || message.isEmpty()
				? exception.getClass().getSimpleName() : message;
	}

	static String canonicalJson(JsonElement element) {
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
			values.add(quote(name) + ":" + canonicalJson(element.getAsJsonObject().get(name)));
		}
		return "{" + join(values, ",") + "}";
	}

	private static String quote(String value) {
		StringBuilder quoted = new StringBuilder("\"");
		for(int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			switch(current) {
			case '"':
				quoted.append("\\\"");
				break;
			case '\\':
				quoted.append("\\\\");
				break;
			case '\b':
				quoted.append("\\b");
				break;
			case '\f':
				quoted.append("\\f");
				break;
			case '\n':
				quoted.append("\\n");
				break;
			case '\r':
				quoted.append("\\r");
				break;
			case '\t':
				quoted.append("\\t");
				break;
			default:
				if(current < 0x20) {
					quoted.append(String.format("\\u%04x", (int) current));
				} else {
					quoted.append(current);
				}
			}
		}
		return quoted.append('"').toString();
	}

	private static String join(List<String> values, String delimiter) {
		StringBuilder result = new StringBuilder();
		for(String value : values) {
			if(result.length() > 0) result.append(delimiter);
			result.append(value);
		}
		return result.toString();
	}
}
