package com.hbm.wocbridge.enforcement;

public final class ContentEnforcementDecision {

	private static final ContentEnforcementDecision ALLOW =
			new ContentEnforcementDecision(false, "", "not explicitly DISABLED");

	private final boolean denied;
	private final String matchedKey;
	private final String reason;

	private ContentEnforcementDecision(boolean denied, String matchedKey, String reason) {
		this.denied = denied;
		this.matchedKey = matchedKey;
		this.reason = reason;
	}

	public static ContentEnforcementDecision allow() {
		return ALLOW;
	}

	public static ContentEnforcementDecision deny(String matchedKey, String reason) {
		return new ContentEnforcementDecision(true, matchedKey, reason);
	}

	public boolean isDenied() {
		return denied;
	}

	public String getMatchedKey() {
		return matchedKey;
	}

	public String getReason() {
		return reason;
	}
}
