package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContentTaxonomyValidator {

	public static final int SUPPORTED_SCHEMA_VERSION = 1;

	public static final class Result {
		private final List<String> errors;
		private final List<String> warnings;

		private Result(List<String> errors, List<String> warnings) {
			this.errors = immutableSorted(errors);
			this.warnings = immutableSorted(warnings);
		}

		public List<String> getErrors() {
			return errors;
		}

		public List<String> getWarnings() {
			return warnings;
		}

		public boolean isValid() {
			return errors.isEmpty();
		}
	}

	private ContentTaxonomyValidator() { }

	public static Result validate(ContentTaxonomy taxonomy,
			List<String> loaderErrors, boolean strict) {
		List<String> errors = new ArrayList<String>(loaderErrors);
		List<String> warnings = new ArrayList<String>();
		if(taxonomy == null) return new Result(errors, warnings);
		if(taxonomy.getTaxonomySchemaVersion() != SUPPORTED_SCHEMA_VERSION) {
			errors.add("UNSUPPORTED_SCHEMA: taxonomySchemaVersion must be 1");
		}
		if(taxonomy.getTaxonomyId().isEmpty()) errors.add("MISSING_ID: taxonomyId is required");
		vocabulary("root.reviewStatus", taxonomy.getReviewStatus(),
				TaxonomyVocabulary.REVIEW_STATUSES, errors);
		vocabulary("root.defaultDisposition", taxonomy.getDefaultDisposition(),
				TaxonomyVocabulary.DISPOSITIONS, errors);

		Map<String, ContentTaxonomyFamily> familyIndex =
				new HashMap<String, ContentTaxonomyFamily>();
		Set<String> selectorIds = new HashSet<String>();
		for(ContentTaxonomyFamily family : taxonomy.getFamilies()) {
			String path = "family " + family.getFamilyId();
			if(family.getFamilyId().isEmpty()) errors.add("MISSING_FAMILY_ID");
			if(familyIndex.put(family.getFamilyId(), family) != null) {
				errors.add("DUPLICATE_FAMILY: " + family.getFamilyId());
			}
			for(String tag : family.getTags()) {
				vocabulary(path + ".tags", tag, TaxonomyVocabulary.TAGS, errors);
			}
			for(String kind : family.getContentKinds()) {
				vocabulary(path + ".contentKinds", kind,
						TaxonomyVocabulary.CONTENT_KINDS, errors);
			}
			vocabulary(path + ".defaultDisposition", family.getDefaultDisposition(),
					TaxonomyVocabulary.DISPOSITIONS, errors);
			vocabulary(path + ".researchDomain", family.getResearchDomain(),
					TaxonomyVocabulary.RESEARCH_DOMAINS, errors);
			vocabulary(path + ".strategicClass", family.getStrategicClass(),
					TaxonomyVocabulary.STRATEGIC_CLASSES, errors);
			vocabulary(path + ".replacementPolicy", family.getReplacementPolicy(),
					TaxonomyVocabulary.REPLACEMENT_POLICIES, errors);
			vocabulary(path + ".futureOwnerModule", family.getFutureOwnerModule(),
					TaxonomyVocabulary.FUTURE_OWNER_MODULES, errors);
			vocabulary(path + ".reviewStatus", family.getReviewStatus(),
					TaxonomyVocabulary.REVIEW_STATUSES, errors);
			vocabulary(path + ".strategicSensitivity", family.getStrategicSensitivity(),
					TaxonomyVocabulary.STRATEGIC_SENSITIVITIES, errors);
			validateSelectors(path, family.getIncludeSelectors(), selectorIds, errors);
			validateSelectors(path, family.getExcludeSelectors(), selectorIds, errors);
			if("weapons.nuclear_strategic".equals(family.getFamilyId())) {
				if(family.getRationale().isEmpty()) {
					errors.add("NUCLEAR_EVIDENCE_REQUIRED: family rationale is empty");
				}
				for(ContentTaxonomySelector selector : family.getIncludeSelectors()) {
					if(!selector.isExplicitMembership()
							&& selector.predicateCount() < 2) {
						errors.add("NUCLEAR_SELECTOR_TOO_BROAD: "
								+ selector.getSelectorId());
					}
				}
			}
			if(family.getIncludeSelectors().isEmpty()) {
				warnings.add("EMPTY_FAMILY: " + family.getFamilyId()
						+ " has no include selectors");
			}
		}

		for(ContentTaxonomyFamily family : taxonomy.getFamilies()) {
			if(!family.getParentFamilyId().isEmpty()
					&& !familyIndex.containsKey(family.getParentFamilyId())) {
				errors.add("UNKNOWN_PARENT: " + family.getFamilyId() + " -> "
						+ family.getParentFamilyId());
			}
			for(String hint : family.getPrerequisiteFamilyHints()) {
				if(!familyIndex.containsKey(hint)) {
					errors.add("UNKNOWN_PREREQUISITE_HINT: " + family.getFamilyId()
							+ " -> " + hint);
				}
			}
		}

		Set<String> exactKeys = new HashSet<String>();
		Set<String> ruleIds = new HashSet<String>();
		for(ContentTaxonomyRule rule : taxonomy.getExactOverrides()) {
			String path = "override " + rule.getRuleId();
			if(!ruleIds.add(rule.getRuleId())) {
				errors.add("DUPLICATE_RULE_ID: " + rule.getRuleId());
			}
			if(!exactKeys.add(rule.getCanonicalKey())) {
				errors.add("DUPLICATE_EXACT_OVERRIDE: " + rule.getCanonicalKey());
			}
			if(!isCanonicalKey(rule.getCanonicalKey())) {
				errors.add("INVALID_CANONICAL_KEY: " + rule.getCanonicalKey());
			}
			for(String tag : rule.getTags()) {
				vocabulary(path + ".tags", tag, TaxonomyVocabulary.TAGS, errors);
			}
			vocabulary(path + ".disposition", rule.getDisposition(),
					TaxonomyVocabulary.DISPOSITIONS, errors);
			vocabulary(path + ".researchDomain", rule.getResearchDomain(),
					TaxonomyVocabulary.RESEARCH_DOMAINS, errors);
			vocabulary(path + ".strategicClass", rule.getStrategicClass(),
					TaxonomyVocabulary.STRATEGIC_CLASSES, errors);
			vocabulary(path + ".replacementPolicy", rule.getReplacementPolicy(),
					TaxonomyVocabulary.REPLACEMENT_POLICIES, errors);
			vocabulary(path + ".futureOwnerModule", rule.getFutureOwnerModule(),
					TaxonomyVocabulary.FUTURE_OWNER_MODULES, errors);
			vocabulary(path + ".reviewStatus", rule.getReviewStatus(),
					TaxonomyVocabulary.REVIEW_STATUSES, errors);
			vocabulary(path + ".strategicSensitivity", rule.getStrategicSensitivity(),
					TaxonomyVocabulary.STRATEGIC_SENSITIVITIES, errors);
		}

		if(strict) {
			for(String warning : warnings) errors.add("STRICT_" + warning);
			warnings.clear();
		}
		return new Result(errors, warnings);
	}

	private static void validateSelectors(String path,
			List<ContentTaxonomySelector> selectors, Set<String> selectorIds,
			List<String> errors) {
		for(ContentTaxonomySelector selector : selectors) {
			if(selector.getSelectorId().isEmpty()) {
				errors.add("MISSING_SELECTOR_ID: " + path);
			} else if(!selectorIds.add(selector.getSelectorId())) {
				errors.add("DUPLICATE_SELECTOR_ID: " + selector.getSelectorId());
			}
			if(selector.predicateCount() == 0) {
				errors.add("EMPTY_SELECTOR: " + selector.getSelectorId());
			}
			if(!selector.getContentKind().isEmpty()) {
				vocabulary("selector " + selector.getSelectorId() + ".contentKind",
						selector.getContentKind(), TaxonomyVocabulary.CONTENT_KINDS, errors);
			}
			for(String key : selector.getCanonicalKeys()) {
				if(!isCanonicalKey(key)) {
					errors.add("INVALID_SELECTOR_KEY: " + selector.getSelectorId()
							+ " -> " + key);
				}
			}
			Integer min = selector.getMetadataMin();
			Integer max = selector.getMetadataMax();
			if(min != null && max != null && min.intValue() > max.intValue()) {
				errors.add("INVALID_METADATA_RANGE: " + selector.getSelectorId());
			}
		}
	}

	private static void vocabulary(String path, String value,
			Set<String> vocabulary, List<String> errors) {
		if(!vocabulary.contains(value)) {
			errors.add("UNKNOWN_VOCABULARY: " + path + "='" + value + "'");
		}
	}

	private static boolean isCanonicalKey(String value) {
		if(value == null || value.isEmpty() || !value.equals(value.trim())) return false;
		for(String kind : TaxonomyVocabulary.CONTENT_KINDS) {
			String prefix = kind.toLowerCase().replace('_', '-') + ":";
			if("structure-loot:".equals(prefix) || "loot-entry:".equals(prefix)) {
				prefix = "loot:";
			}
			if(value.startsWith(prefix)) return true;
		}
		return false;
	}

	private static List<String> immutableSorted(List<String> source) {
		List<String> copy = new ArrayList<String>(source);
		Collections.sort(copy);
		return Collections.unmodifiableList(copy);
	}
}
