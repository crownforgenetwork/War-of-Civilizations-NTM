package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ContentTaxonomyResult {

	private final ContentInventory.Entry entry;
	private final List<String> familyIds;
	private final List<String> producingRules;
	private final List<String> exclusions;
	private final Set<String> tags;
	private final String disposition;
	private final String researchDomain;
	private final String researchTier;
	private final String strategicClass;
	private final String replacementPolicy;
	private final String futureOwnerModule;
	private final String strategicSensitivity;
	private final String reviewStatus;
	private final String rationale;
	private final boolean classified;
	private final boolean ambiguous;
	private final boolean conflicted;
	private final int precedence;

	public ContentTaxonomyResult(ContentInventory.Entry entry, List<String> familyIds,
			List<String> producingRules, List<String> exclusions, Set<String> tags,
			String disposition, String researchDomain, String researchTier,
			String strategicClass, String replacementPolicy, String futureOwnerModule,
			String strategicSensitivity, String reviewStatus, String rationale,
			boolean classified, boolean ambiguous, boolean conflicted, int precedence) {
		this.entry = entry;
		this.familyIds = sorted(familyIds);
		this.producingRules = sorted(producingRules);
		this.exclusions = sorted(exclusions);
		this.tags = Collections.unmodifiableSet(new TreeSet<String>(tags));
		this.disposition = value(disposition);
		this.researchDomain = value(researchDomain);
		this.researchTier = value(researchTier);
		this.strategicClass = value(strategicClass);
		this.replacementPolicy = value(replacementPolicy);
		this.futureOwnerModule = value(futureOwnerModule);
		this.strategicSensitivity = value(strategicSensitivity);
		this.reviewStatus = value(reviewStatus);
		this.rationale = value(rationale);
		this.classified = classified;
		this.ambiguous = ambiguous;
		this.conflicted = conflicted;
		this.precedence = precedence;
	}

	public ContentInventory.Entry getEntry() {
		return entry;
	}

	public List<String> getFamilyIds() {
		return familyIds;
	}

	public List<String> getProducingRules() {
		return producingRules;
	}

	public List<String> getExclusions() {
		return exclusions;
	}

	public Set<String> getTags() {
		return tags;
	}

	public String getDisposition() {
		return disposition;
	}

	public String getResearchDomain() {
		return researchDomain;
	}

	public String getResearchTier() {
		return researchTier;
	}

	public String getStrategicClass() {
		return strategicClass;
	}

	public String getReplacementPolicy() {
		return replacementPolicy;
	}

	public String getFutureOwnerModule() {
		return futureOwnerModule;
	}

	public String getStrategicSensitivity() {
		return strategicSensitivity;
	}

	public String getReviewStatus() {
		return reviewStatus;
	}

	public String getRationale() {
		return rationale;
	}

	public boolean isClassified() {
		return classified;
	}

	public boolean isAmbiguous() {
		return ambiguous;
	}

	public boolean isConflicted() {
		return conflicted;
	}

	public int getPrecedence() {
		return precedence;
	}

	public boolean isReviewed() {
		return "reviewed".equals(reviewStatus) && !ambiguous && !conflicted;
	}

	public boolean isWeaponLike() {
		String value = (entry.getCanonicalKey() + " " + entry.getRegistryName())
				.toLowerCase(java.util.Locale.US);
		return value.contains("gun") || value.contains("ammo") || value.contains("weapon")
				|| value.contains("missile") || value.contains("rocket")
				|| value.contains("grenade") || value.contains("bomb")
				|| value.contains("warhead") || value.contains("launcher");
	}

	private static List<String> sorted(List<String> source) {
		List<String> result = new ArrayList<String>(source);
		Collections.sort(result);
		return Collections.unmodifiableList(result);
	}

	private static String value(String source) {
		return source == null ? "" : source;
	}
}
