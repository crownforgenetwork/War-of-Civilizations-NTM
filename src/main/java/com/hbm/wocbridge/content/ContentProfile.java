package com.hbm.wocbridge.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContentProfile {

	private final int schemaVersion;
	private final String profileId;
	private final ContentState defaultState;
	private final List<ContentRule> rules;
	private final Map<String, ContentRule> ruleIndex;
	private final Map<String, List<ContentKey>> tags;
	private final Map<String, Set<ContentKey>> tagIndex;
	private final Map<String, Set<String>> reverseTagIndex;
	private final String notes;

	public ContentProfile(int schemaVersion, String profileId, ContentState defaultState,
			List<ContentRule> rules, Map<String, List<ContentKey>> tags, String notes) {
		this.schemaVersion = schemaVersion;
		this.profileId = profileId == null ? "" : profileId;
		this.defaultState = defaultState == null ? ContentState.AVAILABLE : defaultState;
		this.rules = Collections.unmodifiableList(new ArrayList<ContentRule>(rules));
		this.notes = notes == null ? "" : notes;

		Map<String, ContentRule> indexedRules = new LinkedHashMap<String, ContentRule>();
		for(ContentRule rule : rules) {
			String key = rule.getKey().getValue();
			if(!indexedRules.containsKey(key)) indexedRules.put(key, rule);
		}
		this.ruleIndex = Collections.unmodifiableMap(indexedRules);

		Map<String, List<ContentKey>> copiedTags = new LinkedHashMap<String, List<ContentKey>>();
		Map<String, Set<ContentKey>> indexedTags = new LinkedHashMap<String, Set<ContentKey>>();
		Map<String, Set<String>> reverseTags = new LinkedHashMap<String, Set<String>>();
		for(Map.Entry<String, List<ContentKey>> entry : tags.entrySet()) {
			List<ContentKey> members = Collections.unmodifiableList(
					new ArrayList<ContentKey>(entry.getValue()));
			copiedTags.put(entry.getKey(), members);

			Map<String, ContentKey> membersByValue =
					new LinkedHashMap<String, ContentKey>();
			for(ContentKey member : members) {
				if(!membersByValue.containsKey(member.getValue())) {
					membersByValue.put(member.getValue(), member);
				}
			}
			Set<ContentKey> uniqueMembers =
					new LinkedHashSet<ContentKey>(membersByValue.values());
			indexedTags.put(entry.getKey(), Collections.unmodifiableSet(uniqueMembers));
			for(ContentKey member : uniqueMembers) {
				String memberKey = member.getValue();
				Set<String> memberTags = reverseTags.get(memberKey);
				if(memberTags == null) {
					memberTags = new LinkedHashSet<String>();
					reverseTags.put(memberKey, memberTags);
				}
				memberTags.add(entry.getKey());
			}
		}
		this.tags = Collections.unmodifiableMap(copiedTags);
		this.tagIndex = Collections.unmodifiableMap(indexedTags);

		Map<String, Set<String>> immutableReverseTags =
				new LinkedHashMap<String, Set<String>>();
		for(Map.Entry<String, Set<String>> entry : reverseTags.entrySet()) {
			immutableReverseTags.put(entry.getKey(),
					Collections.unmodifiableSet(new LinkedHashSet<String>(entry.getValue())));
		}
		this.reverseTagIndex = Collections.unmodifiableMap(immutableReverseTags);
	}

	public static ContentProfile availableFallback() {
		return new ContentProfile(1, "woc_fallback_available", ContentState.AVAILABLE,
				Collections.<ContentRule>emptyList(),
				Collections.<String, List<ContentKey>>emptyMap(),
				"In-memory fallback used when no accepted content profile is available.");
	}

	public ContentProfile effectiveCopy() {
		return new ContentProfile(schemaVersion, profileId, defaultState,
				new ArrayList<ContentRule>(ruleIndex.values()), tags, notes);
	}

	public int getSchemaVersion() {
		return schemaVersion;
	}

	public String getProfileId() {
		return profileId;
	}

	public ContentState getDefaultState() {
		return defaultState;
	}

	public List<ContentRule> getRules() {
		return rules;
	}

	public Collection<ContentRule> getEffectiveRules() {
		return ruleIndex.values();
	}

	public ContentRule getRule(ContentKey key) {
		return key == null ? null : ruleIndex.get(key.getValue());
	}

	public Map<String, List<ContentKey>> getRawTags() {
		return tags;
	}

	public Set<ContentKey> getKeysForTag(String tag) {
		Set<ContentKey> keys = tagIndex.get(tag);
		return keys == null ? Collections.<ContentKey>emptySet() : keys;
	}

	public Set<String> getTagsForKey(ContentKey key) {
		if(key == null) return Collections.emptySet();
		Set<String> memberTags = reverseTagIndex.get(key.getValue());
		return memberTags == null ? Collections.<String>emptySet() : memberTags;
	}

	public int getRuleCount() {
		return ruleIndex.size();
	}

	public int getTagCount() {
		return tagIndex.size();
	}

	public String getNotes() {
		return notes;
	}
}
