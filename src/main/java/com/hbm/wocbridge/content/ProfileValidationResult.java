package com.hbm.wocbridge.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hbm.wocbridge.content.ProfileValidationIssue.Severity;

public final class ProfileValidationResult {

	private final ContentProfile profile;
	private final List<ProfileValidationIssue> issues;

	public ProfileValidationResult(ContentProfile profile, List<ProfileValidationIssue> issues) {
		this.profile = profile;
		List<ProfileValidationIssue> sorted = new ArrayList<ProfileValidationIssue>(issues);
		Collections.sort(sorted);
		this.issues = Collections.unmodifiableList(sorted);
	}

	public ContentProfile getProfile() {
		return profile;
	}

	public List<ProfileValidationIssue> getIssues() {
		return issues;
	}

	public boolean hasErrors() {
		return count(Severity.ERROR) > 0;
	}

	public int count(Severity severity) {
		int count = 0;
		for(ProfileValidationIssue issue : issues) {
			if(issue.getSeverity() == severity) count++;
		}
		return count;
	}
}
