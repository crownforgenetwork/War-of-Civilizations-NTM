package com.hbm.wocbridge.content;

public final class ContentRule {

	private final ContentKey key;
	private final ContentState state;
	private final String notes;
	private final String unlockTechnology;
	private final String requiredProject;
	private final String replacementKey;
	private final String salvageKey;
	private final String eventMetadata;
	private final String adminMetadata;

	public ContentRule(ContentKey key, ContentState state, String notes, String unlockTechnology,
			String requiredProject, String replacementKey, String salvageKey,
			String eventMetadata, String adminMetadata) {
		this.key = key;
		this.state = state;
		this.notes = emptyIfNull(notes);
		this.unlockTechnology = emptyIfNull(unlockTechnology);
		this.requiredProject = emptyIfNull(requiredProject);
		this.replacementKey = emptyIfNull(replacementKey);
		this.salvageKey = emptyIfNull(salvageKey);
		this.eventMetadata = emptyIfNull(eventMetadata);
		this.adminMetadata = emptyIfNull(adminMetadata);
	}

	private static String emptyIfNull(String value) {
		return value == null ? "" : value;
	}

	public ContentKey getKey() {
		return key;
	}

	public ContentKind getKind() {
		return key.getKind();
	}

	public ContentState getState() {
		return state;
	}

	public String getNotes() {
		return notes;
	}

	public String getUnlockTechnology() {
		return unlockTechnology;
	}

	public String getRequiredProject() {
		return requiredProject;
	}

	public String getReplacementKey() {
		return replacementKey;
	}

	public String getSalvageKey() {
		return salvageKey;
	}

	public String getEventMetadata() {
		return eventMetadata;
	}

	public String getAdminMetadata() {
		return adminMetadata;
	}
}
