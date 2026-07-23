package com.hbm.wocbridge.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ContentProfileChecksum {

	private ContentProfileChecksum() { }

	public static String sha256(ContentProfile profile) {
		StringBuilder canonical = new StringBuilder();
		append(canonical, Integer.toString(profile.getSchemaVersion()));
		append(canonical, profile.getProfileId());
		append(canonical, profile.getDefaultState().name());
		append(canonical, profile.getNotes());

		List<ContentRule> rules = new ArrayList<ContentRule>(profile.getEffectiveRules());
		Collections.sort(rules, new Comparator<ContentRule>() {
			@Override
			public int compare(ContentRule left, ContentRule right) {
				return left.getKey().compareTo(right.getKey());
			}
		});
		for(ContentRule rule : rules) {
			append(canonical, "rule");
			append(canonical, rule.getKey().getValue());
			append(canonical, rule.getKind().name());
			append(canonical, rule.getState().name());
			append(canonical, rule.getNotes());
			append(canonical, rule.getUnlockTechnology());
			append(canonical, rule.getRequiredProject());
			append(canonical, rule.getReplacementKey());
			append(canonical, rule.getSalvageKey());
			append(canonical, rule.getEventMetadata());
			append(canonical, rule.getAdminMetadata());
		}

		List<String> tagNames = new ArrayList<String>(profile.getRawTags().keySet());
		Collections.sort(tagNames);
		for(String tagName : tagNames) {
			append(canonical, "tag");
			append(canonical, tagName);
			List<ContentKey> members = new ArrayList<ContentKey>(profile.getKeysForTag(tagName));
			Collections.sort(members);
			for(ContentKey member : members) append(canonical, member.getValue());
		}
		return sha256(canonical.toString());
	}

	private static void append(StringBuilder target, String value) {
		String safeValue = value == null ? "" : value;
		target.append(safeValue.length()).append(':').append(safeValue).append('\n');
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for(byte current : hash) {
				hex.append(String.format(Locale.US, "%02x", current & 0xFF));
			}
			return hex.toString();
		} catch(NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}
