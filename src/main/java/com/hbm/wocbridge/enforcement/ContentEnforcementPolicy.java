package com.hbm.wocbridge.enforcement;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.content.ContentKind;
import com.hbm.wocbridge.content.ContentProfileManager;
import com.hbm.wocbridge.content.ContentRule;
import com.hbm.wocbridge.content.ContentState;

public final class ContentEnforcementPolicy {

	public static final int SUPPORTED_SCHEMA_VERSION = 1;

	private final boolean active;
	private final String inactiveReason;
	private final String profileId;
	private final String checksum;
	private final Set<String> disabledKeys;

	private ContentEnforcementPolicy(boolean active, String inactiveReason, String profileId,
			String checksum, Set<String> disabledKeys) {
		this.active = active;
		this.inactiveReason = inactiveReason;
		this.profileId = profileId;
		this.checksum = checksum;
		this.disabledKeys = Collections.unmodifiableSet(new TreeSet<String>(disabledKeys));
	}

	public static ContentEnforcementPolicy fromActiveProfile() {
		TreeSet<String> disabled = new TreeSet<String>();
		for(ContentRule rule : ContentProfileManager.getEffectiveRules()) {
			if(rule.getState() == ContentState.DISABLED) {
				disabled.add(rule.getKey().getValue());
			}
		}

		String reason = "";
		if(!WocDevelopmentConfig.enableContentEnforcement) {
			reason = "enableContentEnforcement=false";
		} else if(!ContentProfileManager.isAcceptedFromDisk()) {
			reason = "no accepted on-disk profile";
		} else if(ContentProfileManager.getSchemaVersion() != SUPPORTED_SCHEMA_VERSION) {
			reason = "unsupported schema version " + ContentProfileManager.getSchemaVersion();
		}
		return new ContentEnforcementPolicy(reason.isEmpty(), reason,
				ContentProfileManager.getProfileId(), ContentProfileManager.getChecksum(), disabled);
	}

	static ContentEnforcementPolicy forFixture(boolean active, String reason, String... keys) {
		TreeSet<String> disabled = new TreeSet<String>();
		if(keys != null) Collections.addAll(disabled, keys);
		return new ContentEnforcementPolicy(active, reason, "fixture", "fixture", disabled);
	}

	public ContentEnforcementDecision decide(String contentKey) {
		if(!active || contentKey == null || !disabledKeys.contains(contentKey)) {
			return ContentEnforcementDecision.allow();
		}
		return ContentEnforcementDecision.deny(contentKey,
				"exact profile rule state is DISABLED");
	}

	public boolean isDisabled(String contentKey) {
		return decide(contentKey).isDenied();
	}

	public boolean hasDisabledKind(ContentKind kind) {
		if(kind == null) return false;
		for(String key : disabledKeys) {
			if(kind.matchesKey(key)) return true;
		}
		return false;
	}

	public boolean isActive() {
		return active;
	}

	public String getInactiveReason() {
		return inactiveReason;
	}

	public String getProfileId() {
		return profileId;
	}

	public String getChecksum() {
		return checksum;
	}

	public Set<String> getDisabledKeys() {
		return disabledKeys;
	}
}
