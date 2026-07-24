package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContentTaxonomyFixtures {

	private ContentTaxonomyFixtures() { }

	public static List<String> run() {
		List<String> failures = new ArrayList<String>();
		ContentTaxonomyLoader.LoadResult missing =
				new ContentTaxonomyLoader().load(
						new java.io.File("__woc_missing_taxonomy_fixture__.json"));
		check(missing.getTaxonomy() == null && !missing.getErrors().isEmpty(),
				failures, "missing taxonomy must fail tooling without a fallback policy");
		ContentInventory inventory = fixtureInventory();
		ContentTaxonomy taxonomy = fixtureTaxonomy();
		ContentTaxonomyValidator.Result validation =
				ContentTaxonomyValidator.validate(taxonomy,
						Collections.<String>emptyList(), true);
		check(validation.isValid(), failures,
				"strict vocabulary fixture should validate");

		ContentTaxonomyClassifier classifier = new ContentTaxonomyClassifier(taxonomy);
		List<ContentTaxonomyResult> first = classifier.classify(inventory);
		List<ContentTaxonomyResult> second =
				new ContentTaxonomyClassifier(taxonomy).classify(inventory);
		check(signature(first).equals(signature(second)), failures,
				"family expansion must be deterministic");
		ContentTaxonomyResult override = find(first, "item:hbm:item.gun_fixture#0");
		check(override != null && "keep".equals(override.getDisposition())
				&& override.getPrecedence() == 5, failures,
				"exact override must beat family disposition");
		ContentTaxonomyResult excluded = find(first, "item:hbm:item.gun_fixture#1");
		check(excluded != null && !excluded.getExclusions().isEmpty()
				&& "unresolved".equals(excluded.getDisposition()), failures,
				"scoped exclusion must beat family inclusion");
		ContentTaxonomyResult sibling = find(first, "item:hbm:item.ammo_fixture#2");
		check(sibling != null && "unresolved".equals(sibling.getDisposition()), failures,
				"metadata-specific selector must not capture siblings");
		ContentTaxonomyResult unreviewed = find(first, "item:hbm:item.suggestion#0");
		check(unreviewed != null && !unreviewed.isReviewed(), failures,
				"unreviewed suggestions must never become reviewed");
		ContentTaxonomyResult conflict = find(first, "item:hbm:item.conflict#0");
		check(conflict != null && conflict.isConflicted()
				&& "unresolved".equals(conflict.getDisposition()), failures,
				"equal-precedence disposition conflicts must remain unresolved");

		ContentProfileDraftGenerator.Draft noFirearms =
				ContentProfileDraftGenerator.generateNoConventionalFirearms(
						taxonomy, first, "fixture-taxonomy", "fixture-inventory");
		check(!noFirearms.getExactKeys().contains("item:hbm:item.grenade_fixture#0"),
				failures, "no-conventional-firearms draft must exclude explosives");
		check(!noFirearms.getExactKeys().contains("block:hbm:tile.machine_fixture#0"),
				failures, "no-conventional-firearms draft must exclude machines");

		ContentTaxonomy invalid = new ContentTaxonomy(1, "invalid", "Invalid", "",
				"", "reviewed", "unresolved",
				Collections.singletonList(family("invalid", "invalid.tag", "keep",
						"reviewed", selector("invalid", "ITEM", "hbm:item.none", null))),
				Collections.<ContentTaxonomyRule>emptyList(),
				Collections.<String>emptyList());
		check(!ContentTaxonomyValidator.validate(invalid,
				Collections.<String>emptyList(), true).isValid(), failures,
				"strict validation must reject unknown vocabulary");
		Collections.sort(failures);
		return failures;
	}

	public static List<String> run(ContentTaxonomy taxonomy,
			List<ContentTaxonomyResult> results) {
		List<String> failures = new ArrayList<String>(run());
		ContentProfileDraftGenerator.Draft draft =
				ContentProfileDraftGenerator.generateNoConventionalFirearms(
						taxonomy, results, "semantic-fixture-taxonomy",
						"semantic-fixture-inventory");
		check(ContentProfileDraftGenerator.COMPLETE.equals(draft.getCompleteness()),
				failures, "complete conventional-firearm boundary has unresolved entries: "
						+ draft.getUnresolvedBoundaryKeys());
		check(ContentProfileDraftGenerator.reviewedSourceKeys(
				taxonomy, ContentProfileDraftGenerator.FIREARM_FAMILY).size() == 39,
				failures, "conventional firearm boundary must contain 39 source-reviewed keys");
		check(ContentProfileDraftGenerator.reviewedSourceKeys(
				taxonomy, ContentProfileDraftGenerator.AMMUNITION_FAMILY).size() == 55,
				failures, "conventional ammunition boundary must contain 55 EnumAmmo keys");
		check(draft.getExactKeys().size() == 94, failures,
				"complete conventional-firearm profile must contain 94 exact rules");
		check(draft.getUnresolvedBoundaryKeys().isEmpty(), failures,
				"a profile labeled COMPLETE must not contain unresolved boundary keys");
		for(String key : draft.getExactKeys()) {
			check(key.startsWith("item:hbm:item.gun_")
					|| key.startsWith("item:hbm:item.ammo_standard#"),
					failures, "conventional-firearm profile contains unrelated key: " + key);
			ContentTaxonomyResult member = find(results, key);
			if(member == null) continue;
			check("ITEM".equals(member.getEntry().getContentKind()), failures,
					"conventional-firearm profile contains non-item key: " + key);
			for(String forbiddenTag : new String[] { "weapon.explosive",
					"weapon.missile", "weapon.nuclear", "weapon.energy",
					"weapon.launcher", "weapon.incendiary", "weapon.chemical",
					"weapon.artillery", "weapon.special", "protective.armor",
					"machine.military" }) {
				check(!member.getTags().contains(forbiddenTag), failures,
						"conventional-firearm profile contains excluded tag "
								+ forbiddenTag + ": " + key);
			}
		}

		List<ContentInventory.Entry> semanticEntries =
				new ArrayList<ContentInventory.Entry>();
		for(String name : new String[] { "block_c4", "c4", "block_semtex",
				"charge_dynamite", "charge_miner", "det_cord", "det_miner",
				"dynamite", "fireworks" }) {
			semanticEntries.add(entry("block:hbm:tile." + name + "#0", "BLOCK",
					"hbm:tile." + name, 0, "blocks.bomb"));
		}
		semanticEntries.add(entry("block:hbm:tile.nuke_boy#0", "BLOCK",
				"hbm:tile.nuke_boy", 0, "blocks.bomb"));
		semanticEntries.add(entry("item:hbm:tile.nuke_boy#0", "ITEM",
				"hbm:tile.nuke_boy", 0, "items.block"));
		semanticEntries.add(entry("item:hbm:item.gun_fatman#0", "ITEM",
				"hbm:item.gun_fatman", 0, "items.weapon.sedna"));
		ContentInventory semanticInventory = new ContentInventory(semanticEntries,
				Collections.singletonMap("semantic-fixture.csv",
						Integer.valueOf(semanticEntries.size())),
				"semantic-fixture", Collections.<String>emptyList());
		List<ContentTaxonomyResult> classified =
				new ContentTaxonomyClassifier(taxonomy).classify(semanticInventory);
		for(String name : new String[] { "block_c4", "c4", "block_semtex",
				"charge_dynamite", "charge_miner", "det_cord", "det_miner",
				"dynamite", "fireworks" }) {
			ContentTaxonomyResult result =
					find(classified, "block:hbm:tile." + name + "#0");
			check(result != null && !result.getTags().contains("weapon.nuclear"),
					failures, "false nuclear classification returned for " + name);
		}
		for(String key : new String[] { "block:hbm:tile.nuke_boy#0",
				"item:hbm:tile.nuke_boy#0", "item:hbm:item.gun_fatman#0" }) {
			ContentTaxonomyResult result = find(classified, key);
			check(result != null && result.getTags().contains("weapon.nuclear")
					&& result.isReviewed(), failures,
					"reviewed nuclear fixture was not classified: " + key);
		}
		Collections.sort(failures);
		return failures;
	}

	private static ContentInventory fixtureInventory() {
		List<ContentInventory.Entry> entries =
				new ArrayList<ContentInventory.Entry>();
		entries.add(entry("item:hbm:item.gun_fixture#0", "ITEM",
				"hbm:item.gun_fixture", 0));
		entries.add(entry("item:hbm:item.gun_fixture#1", "ITEM",
				"hbm:item.gun_fixture", 1));
		entries.add(entry("item:hbm:item.ammo_fixture#1", "ITEM",
				"hbm:item.ammo_fixture", 1));
		entries.add(entry("item:hbm:item.ammo_fixture#2", "ITEM",
				"hbm:item.ammo_fixture", 2));
		entries.add(entry("item:hbm:item.grenade_fixture#0", "ITEM",
				"hbm:item.grenade_fixture", 0));
		entries.add(entry("block:hbm:tile.machine_fixture#0", "BLOCK",
				"hbm:tile.machine_fixture", 0));
		entries.add(entry("item:hbm:item.suggestion#0", "ITEM",
				"hbm:item.suggestion", 0));
		entries.add(entry("item:hbm:item.conflict#0", "ITEM",
				"hbm:item.conflict", 0));
		return new ContentInventory(entries,
				Collections.singletonMap("fixture.csv", Integer.valueOf(entries.size())),
				"fixture", Collections.<String>emptyList());
	}

	private static ContentTaxonomy fixtureTaxonomy() {
		List<ContentTaxonomyFamily> families =
				new ArrayList<ContentTaxonomyFamily>();
		families.add(family(ContentProfileDraftGenerator.FIREARM_FAMILY,
				"weapon.firearm", "replacement_candidate", "reviewed",
				selector("firearms", "ITEM", "hbm:item.gun_fixture", null),
				selector("firearm-conflict", "ITEM", "hbm:item.conflict", null),
				selector("exclude-metadata-one", "ITEM", "hbm:item.gun_fixture",
						Integer.valueOf(1))));
		families.add(family(ContentProfileDraftGenerator.AMMUNITION_FAMILY,
				"weapon.ammunition", "replacement_candidate", "reviewed",
				selector("ammunition-one", "ITEM", "hbm:item.ammo_fixture",
						Integer.valueOf(1))));
		families.add(family("weapons.explosives.conventional",
				"weapon.explosive", "future_research", "reviewed",
				selector("grenade", "ITEM", "hbm:item.grenade_fixture", null)));
		families.add(family("machines.processing", "machine.processing",
				"keep", "reviewed",
				selector("machine", "BLOCK", "hbm:tile.machine_fixture", null)));
		families.add(family("suggestions.unreviewed", "utility",
				"keep", "unreviewed",
				selector("suggestion", "ITEM", "hbm:item.suggestion", null)));
		families.add(family("conflict.second", "weapon.energy",
				"future_event", "reviewed",
				selector("conflict-second", "ITEM", "hbm:item.conflict", null)));

		ContentTaxonomyRule override = new ContentTaxonomyRule(
				"override-fixture-gun", "item:hbm:item.gun_fixture#0",
				Collections.singletonList("weapon.firearm"), "keep",
				"conventional_weapons", "1", "military", "none", "HBM",
				"controlled", "reviewed", "override fixture");
		return new ContentTaxonomy(1, "fixture", "Fixture", "", "",
				"reviewed", "unresolved", families,
				Collections.singletonList(override), Collections.<String>emptyList());
	}

	private static ContentTaxonomyFamily family(String id, String tag,
			String disposition, String review, ContentTaxonomySelector... selectors) {
		List<ContentTaxonomySelector> include =
				new ArrayList<ContentTaxonomySelector>(Arrays.asList(selectors));
		List<ContentTaxonomySelector> exclude =
				new ArrayList<ContentTaxonomySelector>();
		if(id.equals(ContentProfileDraftGenerator.FIREARM_FAMILY)
				&& include.size() == 3) {
			exclude.add(include.remove(2));
		}
		return new ContentTaxonomyFamily(id, id, "", "",
				Collections.<String>emptyList(), Collections.singletonList(tag),
				Collections.<String>emptyList(), include, exclude, disposition,
				"conventional_weapons", "1", Collections.<String>emptyList(),
				"military",
				disposition.equals("replacement_candidate")
						? "external_gun_mod" : "none",
				"HBM", false, false, false, "controlled", review,
				"fixture", Collections.<String>emptyList());
	}

	private static ContentTaxonomySelector selector(String id, String kind,
			String registryName, Integer metadata) {
		return new ContentTaxonomySelector(id, Collections.<String>emptyList(),
				Collections.<String>emptyList(),
				"", kind, registryName, "", "", metadata, null, null,
				"", "", "", "", "", "", "");
	}

	private static ContentInventory.Entry entry(String key, String kind,
			String registryName, int metadata) {
		return entry(key, kind, registryName, metadata, "");
	}

	private static ContentInventory.Entry entry(String key, String kind,
			String registryName, int metadata, String exporterCategory) {
		return new ContentInventory.Entry(key, kind, registryName, metadata,
				exporterCategory, "", "", "", "", "fixture.csv",
				new LinkedHashMap<String, String>());
	}

	private static ContentTaxonomyResult find(
			List<ContentTaxonomyResult> results, String key) {
		for(ContentTaxonomyResult result : results) {
			if(key.equals(result.getEntry().getCanonicalKey())) return result;
		}
		return null;
	}

	private static String signature(List<ContentTaxonomyResult> results) {
		StringBuilder result = new StringBuilder();
		for(ContentTaxonomyResult classification : results) {
			result.append(classification.getEntry().getCanonicalKey()).append('=')
					.append(classification.getDisposition()).append(':')
					.append(classification.getFamilyIds()).append(':')
					.append(classification.getExclusions()).append('\n');
		}
		return result.toString();
	}

	private static void check(boolean condition,
			List<String> failures, String message) {
		if(!condition) failures.add(message);
	}
}
