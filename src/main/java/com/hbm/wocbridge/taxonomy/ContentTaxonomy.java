package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContentTaxonomy {

	private final int taxonomySchemaVersion;
	private final String taxonomyId;
	private final String displayName;
	private final String description;
	private final String source;
	private final String reviewStatus;
	private final String defaultDisposition;
	private final List<ContentTaxonomyFamily> families;
	private final List<ContentTaxonomyRule> exactOverrides;
	private final List<String> notes;
	private final Map<String, ContentTaxonomyRule> exactOverrideIndex;

	public ContentTaxonomy(int taxonomySchemaVersion, String taxonomyId,
			String displayName, String description, String source, String reviewStatus,
			String defaultDisposition, List<ContentTaxonomyFamily> families,
			List<ContentTaxonomyRule> exactOverrides, List<String> notes) {
		this.taxonomySchemaVersion = taxonomySchemaVersion;
		this.taxonomyId = value(taxonomyId);
		this.displayName = value(displayName);
		this.description = value(description);
		this.source = value(source);
		this.reviewStatus = value(reviewStatus);
		this.defaultDisposition = value(defaultDisposition);
		this.families = Collections.unmodifiableList(
				new ArrayList<ContentTaxonomyFamily>(families));
		this.exactOverrides = Collections.unmodifiableList(
				new ArrayList<ContentTaxonomyRule>(exactOverrides));
		this.notes = Collections.unmodifiableList(new ArrayList<String>(notes));
		Map<String, ContentTaxonomyRule> index =
				new LinkedHashMap<String, ContentTaxonomyRule>();
		for(ContentTaxonomyRule rule : exactOverrides) {
			if(!index.containsKey(rule.getCanonicalKey())) {
				index.put(rule.getCanonicalKey(), rule);
			}
		}
		this.exactOverrideIndex = Collections.unmodifiableMap(index);
	}

	public int getTaxonomySchemaVersion() {
		return taxonomySchemaVersion;
	}

	public String getTaxonomyId() {
		return taxonomyId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}

	public String getSource() {
		return source;
	}

	public String getReviewStatus() {
		return reviewStatus;
	}

	public String getDefaultDisposition() {
		return defaultDisposition;
	}

	public List<ContentTaxonomyFamily> getFamilies() {
		return families;
	}

	public List<ContentTaxonomyRule> getExactOverrides() {
		return exactOverrides;
	}

	public ContentTaxonomyRule getExactOverride(String canonicalKey) {
		return exactOverrideIndex.get(canonicalKey);
	}

	public List<String> getNotes() {
		return notes;
	}

	private static String value(String source) {
		return source == null ? "" : source;
	}
}
