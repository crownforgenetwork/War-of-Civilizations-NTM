package com.hbm.wocbridge.taxonomy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ContentTaxonomyAudit {

	public static final class Summary {
		private final int total;
		private final int classified;
		private final int reviewed;
		private final int unreviewed;
		private final int ambiguous;
		private final int conflicted;
		private final int unresolvedWeaponLike;

		private Summary(int total, int classified, int reviewed, int unreviewed,
				int ambiguous, int conflicted, int unresolvedWeaponLike) {
			this.total = total;
			this.classified = classified;
			this.reviewed = reviewed;
			this.unreviewed = unreviewed;
			this.ambiguous = ambiguous;
			this.conflicted = conflicted;
			this.unresolvedWeaponLike = unresolvedWeaponLike;
		}

		public int getTotal() {
			return total;
		}

		public int getClassified() {
			return classified;
		}

		public int getReviewed() {
			return reviewed;
		}

		public int getUnreviewed() {
			return unreviewed;
		}

		public int getAmbiguous() {
			return ambiguous;
		}

		public int getConflicted() {
			return conflicted;
		}

		public int getUnresolvedWeaponLike() {
			return unresolvedWeaponLike;
		}
	}

	private ContentTaxonomyAudit() { }

	public static Summary summarize(List<ContentTaxonomyResult> results) {
		int classified = 0;
		int reviewed = 0;
		int ambiguous = 0;
		int conflicted = 0;
		int unresolvedWeaponLike = 0;
		for(ContentTaxonomyResult result : results) {
			if(result.isClassified()) classified++;
			if(result.isReviewed()) reviewed++;
			if(result.isAmbiguous()) ambiguous++;
			if(result.isConflicted()) conflicted++;
			if(result.isWeaponLike() && (!result.isReviewed()
					|| "unresolved".equals(result.getDisposition()))) {
				unresolvedWeaponLike++;
			}
		}
		return new Summary(results.size(), classified, reviewed,
				results.size() - reviewed, ambiguous, conflicted,
				unresolvedWeaponLike);
	}

	public static List<String> reconciliationFailures(
			List<ContentTaxonomyResult> results,
			ContentProfileDraftGenerator.Draft wocDraft,
			ContentProfileDraftGenerator.Draft firearmsDraft) {
		List<String> failures = new ArrayList<String>();
		if(sum(wocDraft.getStateCounts()) != results.size()) {
			failures.add("WOC_DRAFT_EFFECTIVE_TOTAL_MISMATCH");
		}
		if(sum(firearmsDraft.getStateCounts()) != results.size()) {
			failures.add("CONVENTIONAL_FIREARMS_EFFECTIVE_TOTAL_MISMATCH");
		}
		if(sum(wocDraft.getRuleStateCounts()) != wocDraft.getExactKeys().size()) {
			failures.add("WOC_DRAFT_RULE_TOTAL_MISMATCH");
		}
		if(sum(firearmsDraft.getRuleStateCounts())
				!= firearmsDraft.getExactKeys().size()) {
			failures.add("CONVENTIONAL_FIREARMS_RULE_TOTAL_MISMATCH");
		}
		if(ContentProfileDraftGenerator.COMPLETE.equals(
				firearmsDraft.getCompleteness())
				&& !firearmsDraft.getUnresolvedBoundaryKeys().isEmpty()) {
			failures.add("COMPLETE_PROFILE_HAS_UNRESOLVED_BOUNDARY_KEYS");
		}
		Collections.sort(failures);
		return failures;
	}

	public static void write(File reportDirectory, ContentTaxonomy taxonomy,
			String taxonomyChecksum, ContentInventory inventory,
			List<ContentTaxonomyResult> results,
			ContentProfileDraftGenerator.Draft wocDraft,
			ContentProfileDraftGenerator.Draft firearmsDraft,
			ContentTaxonomyValidator.Result validation) throws IOException {
		TaxonomyIo.writeAtomic(new File(reportDirectory, "content_taxonomy_audit.txt"),
				audit(taxonomy, taxonomyChecksum, inventory, results,
						wocDraft, firearmsDraft, validation));
		TaxonomyIo.writeAtomic(new File(reportDirectory, "content_taxonomy_unresolved.txt"),
				unresolved(taxonomy, taxonomyChecksum, inventory, results));
		TaxonomyIo.writeAtomic(new File(reportDirectory, "content_taxonomy_families.csv"),
				families(taxonomy, results));
	}

	private static String audit(ContentTaxonomy taxonomy, String taxonomyChecksum,
			ContentInventory inventory, List<ContentTaxonomyResult> results,
			ContentProfileDraftGenerator.Draft wocDraft,
			ContentProfileDraftGenerator.Draft firearmsDraft,
			ContentTaxonomyValidator.Result validation) {
		Summary summary = summarize(results);
		StringBuilder report = new StringBuilder();
		report.append("WOC Phase 4 Content Taxonomy Audit\n");
		report.append("formatVersion=1\n");
		report.append("taxonomyId=").append(taxonomy.getTaxonomyId()).append('\n');
		report.append("taxonomySchemaVersion=")
				.append(taxonomy.getTaxonomySchemaVersion()).append('\n');
		report.append("taxonomyChecksum=").append(taxonomyChecksum).append('\n');
		report.append("inventoryChecksum=").append(inventory.getChecksum()).append('\n');
		report.append("totalCanonicalEntries=").append(summary.getTotal()).append('\n');
		report.append("classified=").append(summary.getClassified()).append('\n');
		report.append("reviewed=").append(summary.getReviewed()).append('\n');
		report.append("unreviewed=").append(summary.getUnreviewed()).append('\n');
		report.append("ambiguous=").append(summary.getAmbiguous()).append('\n');
		report.append("conflicted=").append(summary.getConflicted()).append('\n');
		report.append("unresolvedWeaponLike=")
				.append(summary.getUnresolvedWeaponLike()).append('\n');
		report.append("validationErrors=").append(validation.getErrors().size()).append('\n');
		report.append("validationWarnings=").append(validation.getWarnings().size()).append('\n');
		report.append("profileCrosswalk.keep=AVAILABLE\n");
		report.append("profileCrosswalk.debug_disable=DISABLED\n");
		report.append("profileCrosswalk.future_research=RESEARCH_LOCKED\n");
		report.append("profileCrosswalk.future_project=PROJECT_LOCKED\n");
		report.append("profileCrosswalk.future_event=EVENT_ONLY\n");
		report.append("profileCrosswalk.disable_candidate=UNREVIEWED\n");
		report.append("profileCrosswalk.replacement_candidate=UNREVIEWED\n");
		report.append("profileCrosswalk.unresolved=UNREVIEWED\n");
		appendCounts(report, "contentKind", count(results, "kind"));
		appendCounts(report, "disposition", count(results, "disposition"));
		appendCounts(report, "strategicClass", count(results, "strategic"));
		appendCounts(report, "futureOwner", count(results, "owner"));
		appendTagCounts(report, results);
		appendStateCounts(report, "wocDraft", wocDraft.getStateCounts());
		appendStateCounts(report, "wocDraft.rule", wocDraft.getRuleStateCounts());
		appendStateCounts(report, "noConventionalFirearmsDraft",
				firearmsDraft.getStateCounts());
		appendStateCounts(report, "noConventionalFirearmsDraft.rule",
				firearmsDraft.getRuleStateCounts());
		report.append("wocDraftChecksum=").append(wocDraft.getChecksum()).append('\n');
		report.append("noConventionalFirearmsDraftChecksum=")
				.append(firearmsDraft.getChecksum()).append('\n');
		report.append("noConventionalFirearmsDraftCompleteness=")
				.append(firearmsDraft.getCompleteness()).append('\n');
		report.append("noConventionalFirearmsDraftExplicitRules=")
				.append(firearmsDraft.getExactKeys().size()).append('\n');
		report.append("noConventionalFirearmsDraftUnresolvedBoundaryKeys=")
				.append(firearmsDraft.getUnresolvedBoundaryKeys().size()).append('\n');
		report.append("noConventionalFirearmsDraftBoundary=")
				.append("39 reviewed handheld firearms + 55 ordinary EnumAmmo variants\n");
		report.append("noConventionalFirearmsDraftExcludedCategories=")
				.append("explosive ammunition|launchers|missiles|incendiary/chemical|")
				.append("energy|nuclear|unusual special weapons|utility devices\n");
		report.append("worldgenFeatureCoverage=")
				.append(familyKindCount(results, "worldgen.ores",
						"WORLDGEN_FEATURE") == 0
						? "UNSUPPORTED_EXPORT_COVERAGE" : "OBSERVED")
				.append('\n');
		List<String> reconciliation =
				reconciliationFailures(results, wocDraft, firearmsDraft);
		report.append("reconciliationStatus=")
				.append(reconciliation.isEmpty() ? "PASS" : "FAIL").append('\n');
		report.append("reconciliation.totalCanonicalEntries=")
				.append(results.size()).append('\n');
		report.append("reconciliation.contentKindTotal=")
				.append(sum(count(results, "kind"))).append('\n');
		report.append("reconciliation.dispositionTotal=")
				.append(sum(count(results, "disposition"))).append('\n');
		report.append("reconciliation.reviewTotal=")
				.append(summary.getReviewed() + summary.getUnreviewed()).append('\n');
		report.append("reconciliation.classificationTotal=")
				.append(summary.getClassified()
						+ (summary.getTotal() - summary.getClassified())).append('\n');
		report.append("reconciliation.wocUnreviewedCrosswalk=")
				.append(value(count(results, "disposition"), "unresolved")
						+ value(count(results, "disposition"), "disable_candidate")
						+ value(count(results, "disposition"),
								"replacement_candidate"))
				.append('\n');
		report.append("reconciliation.wocDraftEffectiveStateTotal=")
				.append(sum(wocDraft.getStateCounts())).append('\n');
		report.append("reconciliation.wocDraftRuleTotal=")
				.append(sum(wocDraft.getRuleStateCounts())).append('\n');
		report.append("reconciliation.noConventionalFirearmsEffectiveStateTotal=")
				.append(sum(firearmsDraft.getStateCounts())).append('\n');
		report.append("reconciliation.noConventionalFirearmsRuleTotal=")
				.append(sum(firearmsDraft.getRuleStateCounts())).append('\n');
		for(String failure : reconciliation) {
			report.append("reconciliationFailure=").append(failure).append('\n');
		}
		appendKeySection(report, "reviewedConventionalFirearmItems",
				ContentProfileDraftGenerator.reviewedSourceKeys(
						taxonomy, ContentProfileDraftGenerator.FIREARM_FAMILY));
		List<String> ammunitionKeys =
				ContentProfileDraftGenerator.reviewedSourceKeys(
						taxonomy, ContentProfileDraftGenerator.AMMUNITION_FAMILY);
		appendKeySection(report, "reviewedConventionalAmmunitionItems",
				ammunitionKeys);
		Set<String> selected = new TreeSet<String>();
		selected.addAll(ContentProfileDraftGenerator.reviewedSourceKeys(
				taxonomy, ContentProfileDraftGenerator.FIREARM_FAMILY));
		selected.addAll(ammunitionKeys);
		appendKeySection(report, "unresolvedConventionalFirearmLikeItems",
				ContentProfileDraftGenerator.unresolvedConventionalGunKeys(
						results, selected));
		appendKeySection(report, "unresolvedConventionalAmmunitionBoundaryItems",
				ContentProfileDraftGenerator.unresolvedConventionalAmmunitionKeys(
						results, new TreeSet<String>(ammunitionKeys)));
		appendKeySection(report, "unresolvedAmmunitionLikeItems",
				ContentProfileDraftGenerator.unresolvedAmmunitionLikeKeys(results));
		report.append("excludedNonFirearmWeaponCategories:\n");
		for(String familyId : new String[] {
				"ammunition.explosive_special",
				"ammunition.launcher",
				"ammunition.incendiary_chemical",
				"ammunition.energy",
				"ammunition.nuclear",
				"ammunition.utility_special",
				"weapons.missiles_rockets",
				"weapons.nuclear_strategic",
				"weapons.energy_prototypes",
				"weapons.launchers.conventional",
				"weapons.incendiary_chemical",
				"weapons.special_unusual",
				"weapons.utility_devices" }) {
			appendFamilyMemberLine(report, familyId, results);
		}
		report.append("weapons.nuclear_strategic.members:\n");
		for(ContentTaxonomyResult result : results) {
			if(result.getFamilyIds().contains("weapons.nuclear_strategic")) {
				report.append("  ").append(result.getEntry().getCanonicalKey())
						.append(" review=").append(result.getReviewStatus())
						.append(" ambiguous=").append(result.isAmbiguous())
						.append(" conflicted=").append(result.isConflicted())
						.append('\n');
			}
		}
		report.append("inventoryWarnings:\n");
		for(String warning : inventory.getWarnings()) {
			report.append("  ").append(warning).append('\n');
		}
		report.append("validationWarnings:\n");
		for(String warning : validation.getWarnings()) {
			report.append("  ").append(warning).append('\n');
		}
		report.append("validationErrors:\n");
		for(String error : validation.getErrors()) {
			report.append("  ").append(error).append('\n');
		}
		report.append("entries:\n");
		for(ContentTaxonomyResult result : results) {
			report.append("  key=").append(result.getEntry().getCanonicalKey())
					.append(" kind=").append(result.getEntry().getContentKind())
					.append(" disposition=").append(result.getDisposition())
					.append(" review=").append(result.getReviewStatus())
					.append(" precedence=").append(result.getPrecedence())
					.append(" families=").append(join(result.getFamilyIds(), "|"))
					.append(" tags=").append(join(result.getTags(), "|"))
					.append(" rules=").append(join(result.getProducingRules(), "|"))
					.append(" exclusions=").append(join(result.getExclusions(), "|"))
					.append(" ambiguous=").append(result.isAmbiguous())
					.append(" conflicted=").append(result.isConflicted()).append('\n');
		}
		return report.toString();
	}

	private static String unresolved(ContentTaxonomy taxonomy, String taxonomyChecksum,
			ContentInventory inventory, List<ContentTaxonomyResult> results) {
		StringBuilder report = new StringBuilder();
		report.append("WOC Phase 4 Unresolved and Ambiguous Content\n");
		report.append("formatVersion=1\n");
		report.append("taxonomyId=").append(taxonomy.getTaxonomyId()).append('\n');
		report.append("taxonomyChecksum=").append(taxonomyChecksum).append('\n');
		report.append("inventoryChecksum=").append(inventory.getChecksum()).append('\n');
		report.append("entries:\n");
		for(ContentTaxonomyResult result : results) {
			if(!result.isAmbiguous() && result.isClassified()) continue;
			report.append("  ").append(result.getEntry().getCanonicalKey())
					.append(" kind=").append(result.getEntry().getContentKind())
					.append(" disposition=").append(result.getDisposition())
					.append(" review=").append(result.getReviewStatus())
					.append(" families=").append(join(result.getFamilyIds(), "|"))
					.append(" rules=").append(join(result.getProducingRules(), "|"))
					.append(" reason=").append(result.getRationale()).append('\n');
		}
		report.append("unresolvedWeaponLike:\n");
		for(ContentTaxonomyResult result : results) {
			if(result.isWeaponLike() && (!result.isReviewed()
					|| "unresolved".equals(result.getDisposition()))) {
				report.append("  ").append(result.getEntry().getCanonicalKey())
						.append(" families=").append(join(result.getFamilyIds(), "|"))
						.append(" disposition=").append(result.getDisposition()).append('\n');
			}
		}
		return report.toString();
	}

	private static String families(ContentTaxonomy taxonomy,
			List<ContentTaxonomyResult> results) {
		StringBuilder csv = new StringBuilder();
		csv.append("family_id,display_name,family_definition_review_status,")
				.append("default_disposition,future_owner,replacement_policy,total,")
				.append("member_reviewed,member_unreviewed,member_ambiguous,")
				.append("member_conflicted,excluded,item,block,entity,fluid,crafting_recipe,")
				.append("smelting_recipe,machine_recipe,structure_loot,")
				.append("worldgen_feature,effective_coverage_percent,export_coverage,")
				.append("disable_safety\n");
		for(ContentTaxonomyFamily family : taxonomy.getFamilies()) {
			Map<String, Integer> kinds = new TreeMap<String, Integer>();
			int total = 0;
			int reviewed = 0;
			int ambiguous = 0;
			int conflicted = 0;
			int excluded = 0;
			for(ContentTaxonomyResult result : results) {
				if(result.getFamilyIds().contains(family.getFamilyId())) {
					total++;
					if(result.isReviewed()) reviewed++;
					if(result.isAmbiguous()) ambiguous++;
					if(result.isConflicted()) conflicted++;
					increment(kinds, result.getEntry().getContentKind());
				}
				for(String exclusion : result.getExclusions()) {
					if(exclusion.startsWith(family.getFamilyId() + ":")) excluded++;
				}
			}
			csv.append(TaxonomyIo.csv(family.getFamilyId())).append(',')
					.append(TaxonomyIo.csv(family.getDisplayName())).append(',')
					.append(family.getReviewStatus()).append(',')
					.append(family.getDefaultDisposition()).append(',')
					.append(family.getFutureOwnerModule()).append(',')
					.append(family.getReplacementPolicy()).append(',')
					.append(total).append(',').append(reviewed).append(',')
					.append(total - reviewed).append(',').append(ambiguous).append(',')
					.append(conflicted).append(',').append(excluded);
			for(String kind : new String[] { "ITEM", "BLOCK", "ENTITY", "FLUID",
					"CRAFTING_RECIPE", "SMELTING_RECIPE", "MACHINE_RECIPE",
					"STRUCTURE_LOOT", "WORLDGEN_FEATURE" }) {
				csv.append(',').append(value(kinds, kind));
			}
			String exportCoverage = "worldgen.ores".equals(family.getFamilyId())
					&& value(kinds, "WORLDGEN_FEATURE") == 0
					? "UNSUPPORTED_EXPORT_COVERAGE" : "OBSERVED";
			String coverage = total == 0 ? "0.00" : String.format(Locale.US,
					"%.2f", Double.valueOf(reviewed * 100.0D / total));
			csv.append(',').append(coverage).append(',')
					.append(exportCoverage).append(',')
					.append(TaxonomyIo.csv(disableSafety(family))).append('\n');
		}
		return csv.toString();
	}

	private static String disableSafety(ContentTaxonomyFamily family) {
		if("debug_disable".equals(family.getDefaultDisposition())) {
			return "reviewed exact development-only entries only";
		}
		if("replacement_candidate".equals(family.getDefaultDisposition())) {
			return "draft only; requires replacement integration and operator review";
		}
		return "not approved for disabling";
	}

	private static Map<String, Integer> count(
			List<ContentTaxonomyResult> results, String dimension) {
		Map<String, Integer> counts = new TreeMap<String, Integer>();
		for(ContentTaxonomyResult result : results) {
			String value;
			if("kind".equals(dimension)) value = result.getEntry().getContentKind();
			else if("disposition".equals(dimension)) value = result.getDisposition();
			else if("strategic".equals(dimension)) value = result.getStrategicClass();
			else value = result.getFutureOwnerModule();
			increment(counts, value);
		}
		return counts;
	}

	private static void appendTagCounts(StringBuilder report,
			List<ContentTaxonomyResult> results) {
		Map<String, Integer> counts = new TreeMap<String, Integer>();
		for(ContentTaxonomyResult result : results) {
			for(String tag : result.getTags()) increment(counts, tag);
		}
		appendCounts(report, "domainTag", counts);
	}

	private static void appendCounts(StringBuilder report, String prefix,
			Map<String, Integer> counts) {
		for(Map.Entry<String, Integer> entry : counts.entrySet()) {
			report.append(prefix).append('.')
					.append(entry.getKey().isEmpty() ? "(empty)" : entry.getKey())
					.append('=').append(entry.getValue()).append('\n');
		}
	}

	private static void appendStateCounts(StringBuilder report, String prefix,
			Map<String, Integer> counts) {
		for(Map.Entry<String, Integer> entry : counts.entrySet()) {
			report.append(prefix).append(".state.").append(entry.getKey())
					.append('=').append(entry.getValue()).append('\n');
		}
	}

	private static void increment(Map<String, Integer> counts, String key) {
		Integer count = counts.get(key);
		counts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
	}

	private static int value(Map<String, Integer> counts, String key) {
		Integer result = counts.get(key);
		return result == null ? 0 : result.intValue();
	}

	private static int familyKindCount(List<ContentTaxonomyResult> results,
			String familyId, String kind) {
		int total = 0;
		for(ContentTaxonomyResult result : results) {
			if(kind.equals(result.getEntry().getContentKind())
					&& result.getFamilyIds().contains(familyId)) total++;
		}
		return total;
	}

	private static int sum(Map<String, Integer> counts) {
		int total = 0;
		for(Integer count : counts.values()) total += count.intValue();
		return total;
	}

	private static void appendKeySection(
			StringBuilder report, String name, Iterable<String> keys) {
		int count = 0;
		StringBuilder members = new StringBuilder();
		for(String key : keys) {
			count++;
			members.append("  ").append(key).append('\n');
		}
		report.append(name).append(".count=").append(count).append('\n');
		report.append(name).append(":\n").append(members);
	}

	private static void appendFamilyMemberLine(StringBuilder report,
			String familyId, List<ContentTaxonomyResult> results) {
		List<String> keys = new ArrayList<String>();
		for(ContentTaxonomyResult result : results) {
			if("ITEM".equals(result.getEntry().getContentKind())
					&& result.getFamilyIds().contains(familyId)) {
				keys.add(result.getEntry().getCanonicalKey());
			}
		}
		Collections.sort(keys);
		report.append("  family=").append(familyId)
				.append(" itemCount=").append(keys.size())
				.append(" items=").append(join(keys, "|")).append('\n');
	}

	private static String join(Iterable<String> values, String delimiter) {
		StringBuilder result = new StringBuilder();
		for(String value : values) {
			if(result.length() > 0) result.append(delimiter);
			result.append(value);
		}
		return result.toString();
	}
}
