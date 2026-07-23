package com.hbm.wocbridge.content;

public final class ProfileValidationIssue implements Comparable<ProfileValidationIssue> {

	public enum Severity {
		INFO,
		WARNING,
		ERROR
	}

	private final Severity severity;
	private final String code;
	private final String affectedKey;
	private final String message;

	public ProfileValidationIssue(Severity severity, String code, String affectedKey, String message) {
		this.severity = severity;
		this.code = code == null ? "" : code;
		this.affectedKey = affectedKey == null ? "" : affectedKey;
		this.message = message == null ? "" : message;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getCode() {
		return code;
	}

	public String getAffectedKey() {
		return affectedKey;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public int compareTo(ProfileValidationIssue other) {
		int comparison = severity.name().compareTo(other.severity.name());
		if(comparison != 0) return comparison;
		comparison = code.compareTo(other.code);
		if(comparison != 0) return comparison;
		comparison = affectedKey.compareTo(other.affectedKey);
		if(comparison != 0) return comparison;
		return message.compareTo(other.message);
	}

	@Override
	public String toString() {
		return severity + " [" + code + "]"
				+ (affectedKey.isEmpty() ? "" : " " + affectedKey)
				+ " - " + message;
	}
}
