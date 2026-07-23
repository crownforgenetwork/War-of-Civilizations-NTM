package com.hbm.wocbridge.content;

public enum ContentState {

	AVAILABLE(true),
	RESEARCH_LOCKED(true),
	PROJECT_LOCKED(true),
	EVENT_ONLY(true),
	DISABLED(true),
	UNREVIEWED(false);

	private final boolean runtimeState;

	ContentState(boolean runtimeState) {
		this.runtimeState = runtimeState;
	}

	public boolean isRuntimeState() {
		return runtimeState;
	}

	public boolean isReviewed() {
		return this != UNREVIEWED;
	}

	public static ContentState fromName(String value) {
		if(value == null) return null;
		try {
			return valueOf(value);
		} catch(IllegalArgumentException ex) {
			return null;
		}
	}
}
