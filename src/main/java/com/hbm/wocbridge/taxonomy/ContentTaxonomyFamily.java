package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContentTaxonomyFamily {

	private final String familyId;
	private final String displayName;
	private final String description;
	private final String parentFamilyId;
	private final List<String> aliases;
	private final List<String> tags;
	private final List<String> contentKinds;
	private final List<ContentTaxonomySelector> includeSelectors;
	private final List<ContentTaxonomySelector> excludeSelectors;
	private final String defaultDisposition;
	private final String researchDomain;
	private final String researchTier;
	private final List<String> prerequisiteFamilyHints;
	private final String strategicClass;
	private final String replacementPolicy;
	private final String futureOwnerModule;
	private final boolean projectCandidate;
	private final boolean licenseCandidate;
	private final boolean prototypeEventCandidate;
	private final String strategicSensitivity;
	private final String reviewStatus;
	private final String rationale;
	private final List<String> notes;

	public ContentTaxonomyFamily(String familyId, String displayName, String description,
			String parentFamilyId, List<String> aliases, List<String> tags,
			List<String> contentKinds, List<ContentTaxonomySelector> includeSelectors,
			List<ContentTaxonomySelector> excludeSelectors, String defaultDisposition,
			String researchDomain, String researchTier,
			List<String> prerequisiteFamilyHints, String strategicClass,
			String replacementPolicy, String futureOwnerModule, boolean projectCandidate,
			boolean licenseCandidate, boolean prototypeEventCandidate,
			String strategicSensitivity, String reviewStatus, String rationale,
			List<String> notes) {
		this.familyId = value(familyId);
		this.displayName = value(displayName);
		this.description = value(description);
		this.parentFamilyId = value(parentFamilyId);
		this.aliases = copy(aliases);
		this.tags = copy(tags);
		this.contentKinds = copy(contentKinds);
		this.includeSelectors = Collections.unmodifiableList(
				new ArrayList<ContentTaxonomySelector>(includeSelectors));
		this.excludeSelectors = Collections.unmodifiableList(
				new ArrayList<ContentTaxonomySelector>(excludeSelectors));
		this.defaultDisposition = value(defaultDisposition);
		this.researchDomain = value(researchDomain);
		this.researchTier = value(researchTier);
		this.prerequisiteFamilyHints = copy(prerequisiteFamilyHints);
		this.strategicClass = value(strategicClass);
		this.replacementPolicy = value(replacementPolicy);
		this.futureOwnerModule = value(futureOwnerModule);
		this.projectCandidate = projectCandidate;
		this.licenseCandidate = licenseCandidate;
		this.prototypeEventCandidate = prototypeEventCandidate;
		this.strategicSensitivity = value(strategicSensitivity);
		this.reviewStatus = value(reviewStatus);
		this.rationale = value(rationale);
		this.notes = copy(notes);
	}

	public String getFamilyId() {
		return familyId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}

	public String getParentFamilyId() {
		return parentFamilyId;
	}

	public List<String> getAliases() {
		return aliases;
	}

	public List<String> getTags() {
		return tags;
	}

	public List<String> getContentKinds() {
		return contentKinds;
	}

	public List<ContentTaxonomySelector> getIncludeSelectors() {
		return includeSelectors;
	}

	public List<ContentTaxonomySelector> getExcludeSelectors() {
		return excludeSelectors;
	}

	public String getDefaultDisposition() {
		return defaultDisposition;
	}

	public String getResearchDomain() {
		return researchDomain;
	}

	public String getResearchTier() {
		return researchTier;
	}

	public List<String> getPrerequisiteFamilyHints() {
		return prerequisiteFamilyHints;
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

	public boolean isProjectCandidate() {
		return projectCandidate;
	}

	public boolean isLicenseCandidate() {
		return licenseCandidate;
	}

	public boolean isPrototypeEventCandidate() {
		return prototypeEventCandidate;
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

	public List<String> getNotes() {
		return notes;
	}

	private static List<String> copy(List<String> source) {
		return Collections.unmodifiableList(new ArrayList<String>(source));
	}

	private static String value(String source) {
		return source == null ? "" : source;
	}
}
