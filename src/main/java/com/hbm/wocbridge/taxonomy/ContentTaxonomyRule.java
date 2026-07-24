package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContentTaxonomyRule {

	private final String ruleId;
	private final String canonicalKey;
	private final List<String> tags;
	private final String disposition;
	private final String researchDomain;
	private final String researchTier;
	private final String strategicClass;
	private final String replacementPolicy;
	private final String futureOwnerModule;
	private final String strategicSensitivity;
	private final String reviewStatus;
	private final String rationale;

	public ContentTaxonomyRule(String ruleId, String canonicalKey, List<String> tags,
			String disposition, String researchDomain, String researchTier,
			String strategicClass, String replacementPolicy, String futureOwnerModule,
			String strategicSensitivity, String reviewStatus, String rationale) {
		this.ruleId = value(ruleId);
		this.canonicalKey = value(canonicalKey);
		this.tags = Collections.unmodifiableList(new ArrayList<String>(tags));
		this.disposition = value(disposition);
		this.researchDomain = value(researchDomain);
		this.researchTier = value(researchTier);
		this.strategicClass = value(strategicClass);
		this.replacementPolicy = value(replacementPolicy);
		this.futureOwnerModule = value(futureOwnerModule);
		this.strategicSensitivity = value(strategicSensitivity);
		this.reviewStatus = value(reviewStatus);
		this.rationale = value(rationale);
	}

	public String getRuleId() {
		return ruleId;
	}

	public String getCanonicalKey() {
		return canonicalKey;
	}

	public List<String> getTags() {
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

	private static String value(String source) {
		return source == null ? "" : source;
	}
}
