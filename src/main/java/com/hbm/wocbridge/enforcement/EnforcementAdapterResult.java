package com.hbm.wocbridge.enforcement;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class EnforcementAdapterResult {

	public enum ReloadClassification {
		LIVE_RELOAD_SAFE,
		RESTART_REQUIRED,
		PRESENTATION_RELOAD_SAFE,
		UNSUPPORTED
	}

	private final String category;
	private final ReloadClassification reloadClassification;
	private int examined;
	private int planned;
	private int mutated;
	private final Set<String> actionKeys = new TreeSet<String>();
	private final Set<String> coveredManagers = new TreeSet<String>();
	private final Set<String> unsupportedManagers = new TreeSet<String>();
	private final Set<String> warnings = new TreeSet<String>();

	public EnforcementAdapterResult(String category,
			ReloadClassification reloadClassification) {
		this.category = category;
		this.reloadClassification = reloadClassification;
	}

	public void examined() {
		examined++;
	}

	public void planned(String key) {
		planned++;
		if(key != null && !key.isEmpty()) actionKeys.add(key);
	}

	public void mutated() {
		mutated++;
	}

	public void covered(String manager) {
		if(manager != null && !manager.isEmpty()) coveredManagers.add(manager);
	}

	public void unsupported(String manager, String reason) {
		String value = manager == null ? "" : manager;
		if(reason != null && !reason.isEmpty()) value += ": " + reason;
		unsupportedManagers.add(value);
	}

	public void warning(String warning) {
		if(warning != null && !warning.isEmpty()) warnings.add(warning);
	}

	public String getCategory() {
		return category;
	}

	public ReloadClassification getReloadClassification() {
		return reloadClassification;
	}

	public int getExamined() {
		return examined;
	}

	public int getPlanned() {
		return planned;
	}

	public int getMutated() {
		return mutated;
	}

	public Set<String> getActionKeys() {
		return Collections.unmodifiableSet(actionKeys);
	}

	public Set<String> getCoveredManagers() {
		return Collections.unmodifiableSet(coveredManagers);
	}

	public Set<String> getUnsupportedManagers() {
		return Collections.unmodifiableSet(unsupportedManagers);
	}

	public Set<String> getWarnings() {
		return Collections.unmodifiableSet(warnings);
	}
}
