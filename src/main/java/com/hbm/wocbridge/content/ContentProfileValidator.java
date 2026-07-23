package com.hbm.wocbridge.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.hbm.wocbridge.content.ProfileValidationIssue.Severity;
import com.hbm.wocbridge.content.RegistryResolver.Resolution;
import com.hbm.wocbridge.content.RegistryResolver.Status;

public final class ContentProfileValidator {

	private static final Pattern PROFILE_ID = Pattern.compile("[A-Za-z0-9_.:-]+");
	private static final Pattern NAMESPACED_ID =
			Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	private ContentProfileValidator() { }

	public static ProfileValidationResult validate(ContentProfile profile,
			List<ProfileValidationIssue> loaderIssues, RegistryResolver resolver,
			boolean strict) {
		List<ProfileValidationIssue> issues =
				new ArrayList<ProfileValidationIssue>(loaderIssues);
		if(profile == null) return new ProfileValidationResult(null, issues);

		validateProfile(profile, strict, issues);
		validateRules(profile, resolver, strict, issues);
		validateTags(profile, strict, issues);
		return new ProfileValidationResult(profile, issues);
	}

	private static void validateProfile(ContentProfile profile, boolean strict,
			List<ProfileValidationIssue> issues) {
		if(profile.getSchemaVersion() != 1) {
			addOnce(issues, new ProfileValidationIssue(Severity.ERROR, "UNSUPPORTED_SCHEMA", "",
					"Only schemaVersion 1 is supported."));
		}
		if(profile.getProfileId().isEmpty()
				|| !PROFILE_ID.matcher(profile.getProfileId()).matches()) {
			issues.add(new ProfileValidationIssue(recoverable(strict), "INVALID_PROFILE_ID", "",
					"profileId may contain only letters, digits, underscore, dot, colon, and dash."));
		}
		if(profile.getDefaultState() == ContentState.UNREVIEWED) {
			issues.add(new ProfileValidationIssue(recoverable(strict),
					"UNREVIEWED_DEFAULT_STATE", "",
					"UNREVIEWED is authoring-only and should not be a production default."));
		}
	}

	private static void validateRules(ContentProfile profile, RegistryResolver resolver,
			boolean strict, List<ProfileValidationIssue> issues) {
		Map<String, ContentRule> seen = new HashMap<String, ContentRule>();
		Set<String> knownValues = new HashSet<String>();
		for(ContentRule rule : profile.getRules()) knownValues.add(rule.getKey().getValue());

		for(ContentRule rule : profile.getRules()) {
			String key = rule.getKey().getValue();
			ContentRule previous = seen.put(key, rule);
			if(previous != null) {
				boolean conflicting = previous.getKind() != rule.getKind()
						|| previous.getState() != rule.getState();
				issues.add(new ProfileValidationIssue(recoverable(strict),
						conflicting ? "CONFLICTING_RULE" : "DUPLICATE_RULE", key,
						conflicting
								? "Multiple rules for this key disagree; the first rule is effective."
								: "Duplicate rule was found; the first rule is effective."));
				continue;
			}

			if(rule.getState() == ContentState.UNREVIEWED) {
				issues.add(new ProfileValidationIssue(recoverable(strict), "UNREVIEWED_RULE", key,
						"UNREVIEWED is authoring-only."));
			}
			validateNamespacedId(rule.getUnlockTechnology(), "MALFORMED_TECHNOLOGY_ID",
					"unlockTechnology", key, strict, issues);
			validateNamespacedId(rule.getRequiredProject(), "MALFORMED_PROJECT_ID",
					"requiredProject", key, strict, issues);

			Resolution resolution = resolver.resolve(rule.getKey());
			if(resolution.getStatus() == Status.MISSING) {
				issues.add(new ProfileValidationIssue(recoverable(strict),
						"UNKNOWN_REGISTRY_KEY", key, resolution.getMessage()));
			} else if(resolution.getStatus() == Status.MALFORMED) {
				issues.add(new ProfileValidationIssue(recoverable(strict),
						"MALFORMED_KEY", key, resolution.getMessage()));
			}

			validateReservedReference(rule.getReplacementKey(), "INVALID_REPLACEMENT_REFERENCE",
					key, knownValues, resolver, strict, issues);
			validateReservedReference(rule.getSalvageKey(), "INVALID_SALVAGE_REFERENCE",
					key, knownValues, resolver, strict, issues);
		}
	}

	private static void validateTags(ContentProfile profile, boolean strict,
			List<ProfileValidationIssue> issues) {
		Set<String> knownValues = new HashSet<String>();
		for(ContentRule rule : profile.getRules()) knownValues.add(rule.getKey().getValue());

		for(Map.Entry<String, List<ContentKey>> entry : profile.getRawTags().entrySet()) {
			String tagName = entry.getKey();
			if(!NAMESPACED_ID.matcher(tagName).matches()) {
				issues.add(new ProfileValidationIssue(recoverable(strict), "MALFORMED_TAG_ID",
						tagName, "Tag IDs must be lowercase namespaced identifiers."));
			}
			Set<String> members = new HashSet<String>();
			for(ContentKey member : entry.getValue()) {
				String value = member.getValue();
				if(!members.add(value)) {
					issues.add(new ProfileValidationIssue(recoverable(strict),
							"DUPLICATE_TAG_MEMBER", value,
							"Tag '" + tagName + "' contains this member more than once."));
				}
				if(!knownValues.contains(value)) {
					issues.add(new ProfileValidationIssue(recoverable(strict),
							"MISSING_TAG_MEMBER", value,
							"Tag '" + tagName + "' references a key with no rule."));
				}
			}
		}
	}

	private static void validateNamespacedId(String value, String code, String field,
			String key, boolean strict, List<ProfileValidationIssue> issues) {
		if(value.isEmpty()) return;
		if(!NAMESPACED_ID.matcher(value).matches()) {
			issues.add(new ProfileValidationIssue(recoverable(strict), code, key,
					field + " must be a lowercase namespaced identifier."));
		}
	}

	private static void validateReservedReference(String value, String code, String ownerKey,
			Set<String> knownValues, RegistryResolver resolver, boolean strict,
			List<ProfileValidationIssue> issues) {
		if(value.isEmpty()) return;
		ContentKey reference;
		try {
			reference = ContentKey.parse(value);
		} catch(IllegalArgumentException ex) {
			issues.add(new ProfileValidationIssue(recoverable(strict), code, ownerKey,
					"Reserved reference '" + value + "' is malformed: " + ex.getMessage()));
			return;
		}
		if(knownValues.contains(value)) return;
		Resolution resolution = resolver.resolve(reference);
		if(resolution.getStatus() != Status.RESOLVED) {
			issues.add(new ProfileValidationIssue(recoverable(strict), code, ownerKey,
					"Reserved reference '" + value + "' does not resolve."));
		}
	}

	private static Severity recoverable(boolean strict) {
		return strict ? Severity.ERROR : Severity.WARNING;
	}

	private static void addOnce(List<ProfileValidationIssue> issues,
			ProfileValidationIssue candidate) {
		for(ProfileValidationIssue issue : issues) {
			if(issue.getSeverity() == candidate.getSeverity()
					&& issue.getCode().equals(candidate.getCode())
					&& issue.getAffectedKey().equals(candidate.getAffectedKey())) return;
		}
		issues.add(candidate);
	}
}
