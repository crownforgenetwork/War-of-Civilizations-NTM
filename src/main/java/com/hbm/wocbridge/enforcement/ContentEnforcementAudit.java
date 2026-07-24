package com.hbm.wocbridge.enforcement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.content.ContentKind;
import com.hbm.wocbridge.content.ContentProfileManager;

public final class ContentEnforcementAudit {

	private static ContentEnforcementPolicy policy =
			ContentEnforcementPolicy.forFixture(false, "not initialized");
	private static final Map<String, EnforcementAdapterResult> RESULTS =
			new TreeMap<String, EnforcementAdapterResult>();
	private static final Map<String, Integer> DENIAL_COUNTS =
			new TreeMap<String, Integer>();
	private static final Map<String, Integer> DENIAL_KEY_COUNTS =
			new TreeMap<String, Integer>();
	private static final Set<String> RESTART_REASONS =
			new java.util.TreeSet<String>();
	private static boolean eventsRegistered;

	private ContentEnforcementAudit() { }

	public static synchronized void reset(ContentEnforcementPolicy newPolicy) {
		policy = newPolicy;
		RESULTS.clear();
		DENIAL_COUNTS.clear();
		DENIAL_KEY_COUNTS.clear();
		RESTART_REASONS.clear();
		eventsRegistered = false;
	}

	public static synchronized void recordAdapter(EnforcementAdapterResult result) {
		if(result != null) RESULTS.put(result.getCategory(), result);
	}

	public static synchronized void setEventsRegistered(boolean registered) {
		eventsRegistered = registered;
	}

	public static synchronized void requireRestart(String reason) {
		if(reason != null && !reason.isEmpty()) RESTART_REASONS.add(reason);
	}

	public static synchronized void recordDenial(String category, String key) {
		increment(DENIAL_COUNTS, category);
		increment(DENIAL_KEY_COUNTS, category + ":" + key);
	}

	public static synchronized File write() throws IOException {
		File destination = getAuditFile();
		writeAtomic(destination, render());
		return destination;
	}

	public static synchronized String render() {
		StringBuilder audit = new StringBuilder();
		audit.append("WOC Phase 3 DISABLED Content Enforcement Audit\n");
		audit.append("formatVersion=1\n");
		audit.append("profilePath=")
				.append(ContentProfileManager.getProfileFile().getAbsolutePath()).append('\n');
		audit.append("profileId=").append(policy.getProfileId()).append('\n');
		audit.append("profileChecksum=").append(policy.getChecksum()).append('\n');
		audit.append("profileAcceptedFromDisk=")
				.append(ContentProfileManager.isAcceptedFromDisk()).append('\n');
		audit.append("profileFallbackActive=")
				.append(ContentProfileManager.isFallbackActive()).append('\n');
		audit.append("schemaVersion=").append(ContentProfileManager.getSchemaVersion())
				.append('\n');
		audit.append("enforcementActive=").append(policy.isActive()).append('\n');
		audit.append("inactiveReason=")
				.append(policy.isActive() ? "-" : policy.getInactiveReason()).append('\n');
		audit.append("eventsRegistered=").append(eventsRegistered).append('\n');
		audit.append("restartRequired=").append(!RESTART_REASONS.isEmpty()).append('\n');
		audit.append("config.enableContentEnforcement=")
				.append(WocDevelopmentConfig.enableContentEnforcement).append('\n');
		audit.append("config.enforceDisabledCrafting=")
				.append(WocDevelopmentConfig.enforceDisabledCrafting).append('\n');
		audit.append("config.enforceDisabledSmelting=")
				.append(WocDevelopmentConfig.enforceDisabledSmelting).append('\n');
		audit.append("config.enforceDisabledMachineRecipes=")
				.append(WocDevelopmentConfig.enforceDisabledMachineRecipes).append('\n');
		audit.append("config.enforceDisabledItemUse=")
				.append(WocDevelopmentConfig.enforceDisabledItemUse).append('\n');
		audit.append("config.enforceDisabledBlockPlacement=")
				.append(WocDevelopmentConfig.enforceDisabledBlockPlacement).append('\n');
		audit.append("config.enforceDisabledLoot=")
				.append(WocDevelopmentConfig.enforceDisabledLoot).append('\n');
		audit.append("config.enforceDisabledWorldgen=")
				.append(WocDevelopmentConfig.enforceDisabledWorldgen).append('\n');
		audit.append("config.hideDisabledFromNEI=")
				.append(WocDevelopmentConfig.hideDisabledFromNEI).append('\n');
		audit.append("config.sanitizeDisabledInventory=")
				.append(WocDevelopmentConfig.sanitizeDisabledInventory)
				.append(" (not implemented in Phase 3)\n");
		audit.append("config.strictEnforcementStartup=")
				.append(WocDevelopmentConfig.strictEnforcementStartup).append('\n');
		audit.append("config.writeEnforcementAudit=")
				.append(WocDevelopmentConfig.writeEnforcementAudit).append('\n');

		Map<ContentKind, Integer> disabledByKind =
				new java.util.EnumMap<ContentKind, Integer>(ContentKind.class);
		for(ContentKind kind : ContentKind.values()) disabledByKind.put(kind, 0);
		for(String key : policy.getDisabledKeys()) {
			ContentKind kind = ContentKind.inferFromKey(key);
			if(kind != null) disabledByKind.put(kind, disabledByKind.get(kind) + 1);
		}
		audit.append("disabledRules.total=").append(policy.getDisabledKeys().size())
				.append('\n');
		for(ContentKind kind : ContentKind.values()) {
			audit.append("disabledRules.").append(kind.name()).append('=')
					.append(disabledByKind.get(kind)).append('\n');
		}

		audit.append("adapters:\n");
		for(EnforcementAdapterResult result : RESULTS.values()) {
			audit.append(result.getCategory())
					.append(" reload=").append(result.getReloadClassification())
					.append(" examined=").append(result.getExamined())
					.append(" planned=").append(result.getPlanned())
					.append(" mutated=").append(result.getMutated()).append('\n');
			appendValues(audit, "  action=", result.getActionKeys());
			appendValues(audit, "  covered=", result.getCoveredManagers());
			appendValues(audit, "  unsupported=", result.getUnsupportedManagers());
			appendValues(audit, "  warning=", result.getWarnings());
		}

		audit.append("disabledKeys:\n");
		appendValues(audit, "", policy.getDisabledKeys());
		audit.append("denials:\n");
		for(Map.Entry<String, Integer> entry : DENIAL_COUNTS.entrySet()) {
			audit.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		audit.append("denialKeys:\n");
		for(Map.Entry<String, Integer> entry : DENIAL_KEY_COUNTS.entrySet()) {
			audit.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		audit.append("restartReasons:\n");
		appendValues(audit, "", RESTART_REASONS);
		audit.append("knownOmissions:\n");
		audit.append("inventorySanitization=not implemented; existing stacks remain untouched\n");
		audit.append("researchProjectNationLocks=not enforced in Phase 3\n");
		audit.append("nonPlayerItemUse=packets, dispensers, and bespoke machine paths are not intercepted\n");
		audit.append("loot=only com.hbm.itempool.ItemPool is filtered\n");
		audit.append("worldgen=coordinate-predicate NBT structures are reported but not disabled\n");
		return audit.toString();
	}

	public static synchronized List<EnforcementAdapterResult> getResults() {
		return Collections.unmodifiableList(
				new ArrayList<EnforcementAdapterResult>(RESULTS.values()));
	}

	public static File getAuditFile() {
		return new File(resolveReportDirectory(), "content_enforcement_audit.txt");
	}

	private static void increment(Map<String, Integer> counts, String key) {
		Integer previous = counts.get(key);
		counts.put(key, previous == null ? 1 : previous + 1);
	}

	private static void appendValues(StringBuilder target, String prefix,
			Iterable<String> values) {
		for(String value : values) target.append(prefix).append(value).append('\n');
	}

	private static File resolveReportDirectory() {
		try {
			File workingDirectory =
					new File(System.getProperty("user.dir", ".")).getCanonicalFile();
			File candidate = workingDirectory;
			for(int depth = 0; depth < 6 && candidate != null; depth++) {
				if(new File(candidate, "build.gradle").isFile()) {
					return new File(candidate,
							"build" + File.separator + "reports" + File.separator + "woc");
				}
				candidate = candidate.getParentFile();
			}
			return new File(workingDirectory,
					"build" + File.separator + "reports" + File.separator + "woc");
		} catch(IOException ex) {
			return new File("build" + File.separator + "reports" + File.separator + "woc");
		}
	}

	private static void writeAtomic(File destination, String content) throws IOException {
		File parent = destination.getParentFile();
		Files.createDirectories(parent.toPath());
		Path temporary = Files.createTempFile(
				parent.toPath(), "." + destination.getName() + ".", ".tmp");
		boolean moved = false;
		try {
			try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					Files.newOutputStream(temporary), StandardCharsets.UTF_8))) {
				writer.write(content);
			}
			try {
				Files.move(temporary, destination.toPath(),
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch(AtomicMoveNotSupportedException ex) {
				Files.move(temporary, destination.toPath(),
						StandardCopyOption.REPLACE_EXISTING);
			}
			moved = true;
		} finally {
			if(!moved) Files.deleteIfExists(temporary);
		}
	}
}
