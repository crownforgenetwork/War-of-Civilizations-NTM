package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class ContentProfileDraftGenerator {

	public static final String FIREARM_FAMILY = "weapons.firearms.conventional";
	public static final String AMMUNITION_FAMILY = "weapons.ammunition.conventional";
	public static final String COMPLETE = "COMPLETE";
	public static final String PARTIAL = "PARTIAL";

	private static final Set<String> REVIEWED_NON_CONVENTIONAL_GUN_FAMILIES =
			Collections.unmodifiableSet(new TreeSet<String>(java.util.Arrays.asList(
					"weapons.explosives.conventional",
					"weapons.missiles_rockets",
					"weapons.nuclear_strategic",
					"weapons.energy_prototypes",
					"weapons.launchers.conventional",
					"weapons.incendiary_chemical",
					"weapons.special_unusual",
					"weapons.utility_devices",
					"development.debug_test")));

	public static final class Draft {
		private final String profileId;
		private final String json;
		private final Map<String, Integer> stateCounts;
		private final Map<String, Integer> ruleStateCounts;
		private final List<String> exactKeys;
		private final String completeness;
		private final List<String> unresolvedBoundaryKeys;

		private Draft(String profileId, String json,
				Map<String, Integer> stateCounts,
				Map<String, Integer> ruleStateCounts, List<String> exactKeys,
				String completeness, List<String> unresolvedBoundaryKeys) {
			this.profileId = profileId;
			this.json = json;
			this.stateCounts = Collections.unmodifiableMap(
					new LinkedHashMap<String, Integer>(stateCounts));
			this.ruleStateCounts = Collections.unmodifiableMap(
					new LinkedHashMap<String, Integer>(ruleStateCounts));
			this.exactKeys = Collections.unmodifiableList(
					new ArrayList<String>(exactKeys));
			this.completeness = completeness;
			this.unresolvedBoundaryKeys = Collections.unmodifiableList(
					new ArrayList<String>(unresolvedBoundaryKeys));
		}

		public String getProfileId() {
			return profileId;
		}

		public String getJson() {
			return json;
		}

		public Map<String, Integer> getStateCounts() {
			return stateCounts;
		}

		public Map<String, Integer> getRuleStateCounts() {
			return ruleStateCounts;
		}

		public List<String> getExactKeys() {
			return exactKeys;
		}

		public String getCompleteness() {
			return completeness;
		}

		public List<String> getUnresolvedBoundaryKeys() {
			return unresolvedBoundaryKeys;
		}

		public String getChecksum() {
			return TaxonomyIo.sha256(json);
		}
	}

	private ContentProfileDraftGenerator() { }

	public static Draft generateWocServer(
			List<ContentTaxonomyResult> results, String taxonomyChecksum,
			String inventoryChecksum) {
		JsonObject root = root("woc_server_phase4_draft",
				"DRAFT / NOT FOR PRODUCTION. Generated from Phase 4 taxonomy "
				+ taxonomyChecksum + " and inventory " + inventoryChecksum
				+ ". Only exact DISABLED is enforced by Phase 3; all other states "
				+ "are descriptive. This file is never installed automatically.");
		JsonArray rules = new JsonArray();
		Map<String, Integer> counts = newStateCounts();
		Map<String, Integer> ruleCounts = newStateCounts();
		List<String> keys = new ArrayList<String>();
		for(ContentTaxonomyResult result : results) {
			String state = stateFor(result.getDisposition());
			increment(counts, state);
			if(!"AVAILABLE".equals(state)) {
				rules.add(rule(result.getEntry().getCanonicalKey(),
						result.getEntry().getContentKind(), state,
						"taxonomy disposition=" + result.getDisposition()
						+ "; families=" + join(result.getFamilyIds(), "|")
						+ "; review=" + result.getReviewStatus()));
				keys.add(result.getEntry().getCanonicalKey());
				increment(ruleCounts, state);
			}
		}
		root.add("rules", rules);
		root.add("tags", new JsonObject());
		return new Draft(root.get("profileId").getAsString(),
				TaxonomyIo.pretty(root), counts, ruleCounts, keys,
				"NOT_APPLICABLE", Collections.<String>emptyList());
	}

	public static Draft generateNoConventionalFirearms(ContentTaxonomy taxonomy,
			List<ContentTaxonomyResult> results, String taxonomyChecksum,
			String inventoryChecksum) {
		List<String> firearmKeys = reviewedSourceKeys(taxonomy, FIREARM_FAMILY);
		List<String> ammunitionKeys =
				reviewedSourceKeys(taxonomy, AMMUNITION_FAMILY);
		Set<String> selected = new TreeSet<String>();
		selected.addAll(firearmKeys);
		selected.addAll(ammunitionKeys);
		List<String> unresolved = new ArrayList<String>(
				unresolvedConventionalGunKeys(results, selected));
		unresolved.addAll(unresolvedConventionalAmmunitionKeys(
				results, new TreeSet<String>(ammunitionKeys)));
		Collections.sort(unresolved);
		boolean complete = firearmKeys.size() == 39
				&& ammunitionKeys.size() == 55 && unresolved.isEmpty();
		String completeness = complete ? COMPLETE : PARTIAL;

		JsonObject root = root("no_hbm_conventional_firearms_phase4_draft",
				"DRAFT / NOT FOR PRODUCTION. " + completeness
				+ " only for the source-reviewed boundary of 39 conventional "
				+ "handheld HBM firearms and all 55 ordinary small-arms EnumAmmo "
				+ "variants. Explosive ammunition, launchers, missiles, incendiary "
				+ "and chemical projectors, energy weapons, nuclear weapons, unusual "
				+ "special weapons, armor, tools, and machines are intentionally "
				+ "outside this boundary. Generated from taxonomy "
				+ taxonomyChecksum + " and inventory " + inventoryChecksum
				+ ". Never installed or activated automatically.");
		root.addProperty("profileCompleteness", completeness);
		root.addProperty("profileBoundary",
				"reviewed conventional handheld firearms and ordinary small-arms ammunition");
		root.addProperty("reviewedFirearmRuleCount", firearmKeys.size());
		root.addProperty("reviewedAmmunitionRuleCount", ammunitionKeys.size());
		JsonArray unresolvedJson = new JsonArray();
		for(String key : unresolved) unresolvedJson.add(new JsonPrimitive(key));
		root.add("unresolvedBoundaryKeys", unresolvedJson);
		JsonArray rules = new JsonArray();
		JsonArray members = new JsonArray();
		Map<String, Integer> counts = newStateCounts();
		Map<String, Integer> ruleCounts = newStateCounts();
		List<String> keys = new ArrayList<String>();
		for(ContentTaxonomyResult result : results) {
			if(!selected.contains(result.getEntry().getCanonicalKey())) {
				increment(counts, "AVAILABLE");
				continue;
			}
			increment(counts, "DISABLED");
		}
		for(String key : selected) {
			rules.add(rule(key, contentKind(key), "DISABLED",
					"Phase 4 source-reviewed conventional external-gun-mod "
					+ "replacement candidate; inactive draft only."));
			members.add(new JsonPrimitive(key));
			increment(ruleCounts, "DISABLED");
			keys.add(key);
		}
		JsonObject tags = new JsonObject();
		tags.add("woc:draft.no_hbm_conventional_firearms", members);
		root.add("rules", rules);
		root.add("tags", tags);
		return new Draft(root.get("profileId").getAsString(),
				TaxonomyIo.pretty(root), counts, ruleCounts, keys,
				completeness, unresolved);
	}

	public static List<String> reviewedSourceKeys(
			ContentTaxonomy taxonomy, String familyId) {
		Set<String> result = new TreeSet<String>();
		for(ContentTaxonomyFamily family : taxonomy.getFamilies()) {
			if(!familyId.equals(family.getFamilyId())
					|| !"reviewed".equals(family.getReviewStatus())) continue;
			for(ContentTaxonomySelector selector : family.getIncludeSelectors()) {
				result.addAll(selector.getCanonicalKeys());
			}
		}
		return Collections.unmodifiableList(new ArrayList<String>(result));
	}

	public static List<String> unresolvedConventionalGunKeys(
			List<ContentTaxonomyResult> results, Set<String> selected) {
		List<String> unresolved = new ArrayList<String>();
		for(ContentTaxonomyResult result : results) {
			ContentInventory.Entry entry = result.getEntry();
			if(!"ITEM".equals(entry.getContentKind())
					|| !entry.getRegistryName().startsWith("hbm:item.gun_")
					|| selected.contains(entry.getCanonicalKey())) continue;
			boolean reviewedExclusion = false;
			if(result.isReviewed()) {
				for(String family : result.getFamilyIds()) {
					if(REVIEWED_NON_CONVENTIONAL_GUN_FAMILIES.contains(family)) {
						reviewedExclusion = true;
					}
				}
			}
			if(!reviewedExclusion) unresolved.add(entry.getCanonicalKey());
		}
		Collections.sort(unresolved);
		return Collections.unmodifiableList(unresolved);
	}

	public static List<String> unresolvedConventionalAmmunitionKeys(
			List<ContentTaxonomyResult> results, Set<String> selected) {
		List<String> unresolved = new ArrayList<String>();
		for(ContentTaxonomyResult result : results) {
			ContentInventory.Entry entry = result.getEntry();
			if(!"ITEM".equals(entry.getContentKind())
					|| !"hbm:item.ammo_standard".equals(entry.getRegistryName())
					|| selected.contains(entry.getCanonicalKey())) continue;
			boolean reviewedExclusion = false;
			if(result.isReviewed()) {
				for(String family : result.getFamilyIds()) {
					if(family.startsWith("ammunition.")) reviewedExclusion = true;
				}
			}
			if(!reviewedExclusion) unresolved.add(entry.getCanonicalKey());
		}
		Collections.sort(unresolved);
		return Collections.unmodifiableList(unresolved);
	}

	public static List<String> unresolvedAmmunitionLikeKeys(
			List<ContentTaxonomyResult> results) {
		List<String> unresolved = new ArrayList<String>();
		for(ContentTaxonomyResult result : results) {
			ContentInventory.Entry entry = result.getEntry();
			String name = entry.getRegistryName();
			if(!"ITEM".equals(entry.getContentKind())
					|| !(name.startsWith("hbm:item.ammo_")
							|| name.contains("_ammo"))
					|| (result.isReviewed()
							&& !"unresolved".equals(result.getDisposition()))) continue;
			unresolved.add(entry.getCanonicalKey());
		}
		Collections.sort(unresolved);
		return Collections.unmodifiableList(unresolved);
	}

	private static JsonObject root(String profileId, String notes) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", 1);
		root.addProperty("profileId", profileId);
		root.addProperty("defaultState", "AVAILABLE");
		root.addProperty("notes", notes);
		return root;
	}

	private static JsonObject rule(String key, String kind,
			String state, String notes) {
		JsonObject rule = new JsonObject();
		rule.addProperty("key", key);
		rule.addProperty("kind", kind);
		rule.addProperty("state", state);
		rule.addProperty("notes", notes);
		return rule;
	}

	private static String contentKind(String canonicalKey) {
		int separator = canonicalKey.indexOf(':');
		if(separator <= 0) return "";
		return canonicalKey.substring(0, separator).toUpperCase().replace('-', '_');
	}

	private static String stateFor(String disposition) {
		if("future_research".equals(disposition)) return "RESEARCH_LOCKED";
		if("future_project".equals(disposition)) return "PROJECT_LOCKED";
		if("future_event".equals(disposition)) return "EVENT_ONLY";
		if("debug_disable".equals(disposition)) return "DISABLED";
		if("keep".equals(disposition)) return "AVAILABLE";
		return "UNREVIEWED";
	}

	private static Map<String, Integer> newStateCounts() {
		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		counts.put("AVAILABLE", Integer.valueOf(0));
		counts.put("DISABLED", Integer.valueOf(0));
		counts.put("RESEARCH_LOCKED", Integer.valueOf(0));
		counts.put("PROJECT_LOCKED", Integer.valueOf(0));
		counts.put("EVENT_ONLY", Integer.valueOf(0));
		counts.put("UNREVIEWED", Integer.valueOf(0));
		return counts;
	}

	private static void increment(Map<String, Integer> counts, String state) {
		counts.put(state, Integer.valueOf(counts.get(state).intValue() + 1));
	}

	private static String join(List<String> values, String delimiter) {
		StringBuilder result = new StringBuilder();
		for(int index = 0; index < values.size(); index++) {
			if(index > 0) result.append(delimiter);
			result.append(values.get(index));
		}
		return result.toString();
	}
}
