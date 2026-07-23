package com.hbm.wocbridge.content;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.main.MainRegistry;
import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.content.ContentProfileLoader.LoadResult;
import com.hbm.wocbridge.content.ProfileValidationIssue.Severity;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

public final class ContentProfileManager {

	public static final class ValidationRun {
		private final boolean accepted;
		private final boolean previousProfilePreserved;
		private final boolean missing;
		private final ProfileValidationResult validation;
		private final File reportFile;

		private ValidationRun(boolean accepted, boolean previousProfilePreserved, boolean missing,
				ProfileValidationResult validation, File reportFile) {
			this.accepted = accepted;
			this.previousProfilePreserved = previousProfilePreserved;
			this.missing = missing;
			this.validation = validation;
			this.reportFile = reportFile;
		}

		public boolean isAccepted() {
			return accepted;
		}

		public boolean isPreviousProfilePreserved() {
			return previousProfilePreserved;
		}

		public boolean isMissing() {
			return missing;
		}

		public ProfileValidationResult getValidation() {
			return validation;
		}

		public File getReportFile() {
			return reportFile;
		}
	}

	private static final class Snapshot {
		private final ContentProfile profile;
		private final List<ProfileValidationIssue> issues;
		private final Map<String, List<ProfileValidationIssue>> issueIndex;
		private final String checksum;
		private final boolean strict;
		private final RegistryResolver registryResolver;

		private Snapshot(ContentProfile profile, List<ProfileValidationIssue> issues,
				String checksum, boolean strict, RegistryResolver registryResolver) {
			this.profile = profile;
			this.issues = Collections.unmodifiableList(
					new ArrayList<ProfileValidationIssue>(issues));
			Map<String, List<ProfileValidationIssue>> indexedIssues =
					new LinkedHashMap<String, List<ProfileValidationIssue>>();
			for(ProfileValidationIssue issue : issues) {
				String affectedKey = issue.getAffectedKey();
				List<ProfileValidationIssue> keyIssues = indexedIssues.get(affectedKey);
				if(keyIssues == null) {
					keyIssues = new ArrayList<ProfileValidationIssue>();
					indexedIssues.put(affectedKey, keyIssues);
				}
				keyIssues.add(issue);
			}
			Map<String, List<ProfileValidationIssue>> immutableIndex =
					new LinkedHashMap<String, List<ProfileValidationIssue>>();
			for(Map.Entry<String, List<ProfileValidationIssue>> entry : indexedIssues.entrySet()) {
				immutableIndex.put(entry.getKey(), Collections.unmodifiableList(
						new ArrayList<ProfileValidationIssue>(entry.getValue())));
			}
			this.issueIndex = Collections.unmodifiableMap(immutableIndex);
			this.checksum = checksum;
			this.strict = strict;
			this.registryResolver = registryResolver;
		}
	}

	private static final class Attempt {
		private final ProfileValidationResult validation;
		private final ContentProfile effectiveProfile;
		private final String checksum;
		private final boolean missing;
		private final RegistryResolver registryResolver;

		private Attempt(ProfileValidationResult validation, ContentProfile effectiveProfile,
				String checksum, boolean missing, RegistryResolver registryResolver) {
			this.validation = validation;
			this.effectiveProfile = effectiveProfile;
			this.checksum = checksum;
			this.missing = missing;
			this.registryResolver = registryResolver;
		}

		private boolean isAccepted() {
			return !missing && effectiveProfile != null && !validation.hasErrors();
		}
	}

	private static final ContentProfile FALLBACK_PROFILE = ContentProfile.availableFallback();
	private static volatile Snapshot active = new Snapshot(FALLBACK_PROFILE,
			Collections.<ProfileValidationIssue>emptyList(),
			ContentProfileChecksum.sha256(FALLBACK_PROFILE), false, null);
	private static volatile boolean initialized;

	private ContentProfileManager() { }

	public static synchronized void initialize() {
		boolean strict = WocDevelopmentConfig.strictContentProfileValidation;
		File profileFile = getProfileFile();
		Attempt attempt = evaluate(profileFile, strict);
		boolean accepted = attempt.isAccepted();

		if(accepted) {
			install(attempt, strict);
		} else if(attempt.missing) {
			active = fallbackSnapshot(attempt.validation.getIssues(), strict);
			MainRegistry.logger.warn("WOC content profile is missing at {}; using the in-memory "
					+ "AVAILABLE fallback.", profileFile.getAbsolutePath());
			if(WocDevelopmentConfig.writeContentProfileExample) writeMinimalExample();
		} else {
			active = fallbackSnapshot(attempt.validation.getIssues(), strict);
			MainRegistry.logger.error("WOC content profile was rejected at startup; using the "
					+ "in-memory AVAILABLE fallback.");
		}
		initialized = true;

		File report = writeValidationReport(profileFile, strict, attempt, accepted, false);
		logSummary("load", profileFile, strict, attempt, accepted);
		if(report == null) {
			MainRegistry.logger.error("WOC content profile validation report could not be written.");
		}
	}

	public static synchronized ValidationRun validateConfiguredProfile() {
		boolean strict = WocDevelopmentConfig.strictContentProfileValidation;
		File profileFile = getProfileFile();
		Attempt attempt = evaluate(profileFile, strict);
		File report = writeValidationReport(profileFile, strict, attempt,
				attempt.isAccepted(), false);
		return new ValidationRun(attempt.isAccepted(), false, attempt.missing,
				attempt.validation, report);
	}

	public static synchronized ValidationRun reload() {
		boolean strict = WocDevelopmentConfig.strictContentProfileValidation;
		File profileFile = getProfileFile();
		Attempt attempt = evaluate(profileFile, strict);
		boolean accepted = attempt.isAccepted();
		boolean preserved = false;
		if(accepted) {
			install(attempt, strict);
		} else {
			preserved = initialized;
		}
		File report = writeValidationReport(
				profileFile, strict, attempt, accepted, preserved);
		logSummary("reload", profileFile, strict, attempt, accepted);
		return new ValidationRun(accepted, preserved, attempt.missing,
				attempt.validation, report);
	}

	private static Attempt evaluate(File profileFile, boolean strict) {
		ContentProfileLoader loader = new ContentProfileLoader();
		LoadResult loaded = loader.load(profileFile, strict);
		if(loaded.isMissing()) {
			ProfileValidationResult validation = new ProfileValidationResult(
					null, loaded.getIssues());
			return new Attempt(validation, null, "", true, null);
		}
		if(loaded.getProfile() == null) {
			ProfileValidationResult validation = new ProfileValidationResult(
					null, loaded.getIssues());
			return new Attempt(validation, null, "", false, null);
		}

		RegistryResolver resolver = new RegistryResolver();
		ProfileValidationResult validation = ContentProfileValidator.validate(
				loaded.getProfile(), loaded.getIssues(), resolver, strict);
		ContentProfile effective = validation.getProfile().effectiveCopy();
		return new Attempt(validation, effective,
				ContentProfileChecksum.sha256(effective), false, resolver);
	}

	private static void install(Attempt attempt, boolean strict) {
		active = new Snapshot(attempt.effectiveProfile,
				attempt.validation.getIssues(), attempt.checksum, strict,
				attempt.registryResolver);
	}

	private static Snapshot fallbackSnapshot(List<ProfileValidationIssue> issues,
			boolean strict) {
		return new Snapshot(FALLBACK_PROFILE, issues,
				ContentProfileChecksum.sha256(FALLBACK_PROFILE), strict, null);
	}

	public static ContentState getState(ContentKey key) {
		ContentRule rule = active.profile.getRule(key);
		return rule == null ? active.profile.getDefaultState() : rule.getState();
	}

	public static ContentRule getRule(ContentKey key) {
		return active.profile.getRule(key);
	}

	public static ContentState getDefaultState() {
		return active.profile.getDefaultState();
	}

	public static String getProfileId() {
		return active.profile.getProfileId();
	}

	public static int getSchemaVersion() {
		return active.profile.getSchemaVersion();
	}

	public static String getChecksum() {
		return active.checksum;
	}

	public static List<ProfileValidationIssue> getValidationIssues() {
		return active.issues;
	}

	public static List<ProfileValidationIssue> getValidationIssues(ContentKey key) {
		if(key == null) return Collections.emptyList();
		List<ProfileValidationIssue> issues = active.issueIndex.get(key.getValue());
		return issues == null ? Collections.<ProfileValidationIssue>emptyList() : issues;
	}

	public static Set<ContentKey> getKeysForTag(String tag) {
		return active.profile.getKeysForTag(tag);
	}

	public static Set<String> getTagsForKey(ContentKey key) {
		return active.profile.getTagsForKey(key);
	}

	public static boolean isReviewed(ContentKey key) {
		return getState(key).isReviewed();
	}

	public static ContentState resolveItemStack(ItemStack stack) {
		return getState(ContentKey.fromItemStack(stack));
	}

	public static ContentState resolveBlock(Block block, int metadata) {
		return getState(ContentKey.fromBlock(block, metadata));
	}

	public static String explain(ContentKey key) {
		Snapshot snapshot = active;
		ContentRule rule = snapshot.profile.getRule(key);
		ContentState state = rule == null
				? snapshot.profile.getDefaultState() : rule.getState();
		List<String> tags = new ArrayList<String>(snapshot.profile.getTagsForKey(key));
		Collections.sort(tags);

		List<ProfileValidationIssue> keyIssues = snapshot.issueIndex.get(key.getValue());
		int relatedIssues = keyIssues == null ? 0 : keyIssues.size();

		StringBuilder explanation = new StringBuilder();
		explanation.append("key=").append(key.getValue())
				.append(" kind=").append(rule == null ? key.getKind() : rule.getKind())
				.append(" state=").append(state)
				.append(" source=").append(rule == null ? "default" : "rule")
				.append(" tags=").append(tags.isEmpty() ? "[]" : tags);
		if(rule != null) {
			explanation.append(" unlockTechnology=")
					.append(valueOrDash(rule.getUnlockTechnology()))
					.append(" requiredProject=")
					.append(valueOrDash(rule.getRequiredProject()))
					.append(" replacementKey=")
					.append(valueOrDash(rule.getReplacementKey()))
					.append(" salvageKey=")
					.append(valueOrDash(rule.getSalvageKey()));
		} else {
			explanation.append(" unlockTechnology=- requiredProject=- replacementKey=- salvageKey=-");
		}
		explanation.append(" issues=").append(relatedIssues);
		return explanation.toString();
	}

	public static int getRuleCount() {
		return active.profile.getRuleCount();
	}

	public static int getTagCount() {
		return active.profile.getTagCount();
	}

	public static String getModeName() {
		return active.strict ? "STRICT" : "PERMISSIVE";
	}

	public static File getProfileFile() {
		File root = MainRegistry.configHbmDir;
		if(root == null) root = new File("config" + File.separator + "hbmConfig");
		return new File(new File(root, "woc"), "content_profile.json");
	}

	private static File writeValidationReport(File profileFile, boolean strict, Attempt attempt,
			boolean accepted, boolean previousPreserved) {
		File report;
		try {
			report = new File(resolveReportDirectory(), "content_profile_validation.txt");
			ContentProfile reportProfile = attempt.effectiveProfile == null
					? FALLBACK_PROFILE : attempt.effectiveProfile;
			String checksum = attempt.checksum.isEmpty()
					? ContentProfileChecksum.sha256(reportProfile) : attempt.checksum;
			writeTextAtomic(report, buildValidationReport(profileFile, strict, attempt.validation,
					reportProfile, checksum, accepted, previousPreserved), true);
			return report;
		} catch(IOException ex) {
			MainRegistry.logger.error("Unable to write WOC profile validation report", ex);
			return null;
		}
	}

	private static String buildValidationReport(File profileFile, boolean strict,
			ProfileValidationResult validation, ContentProfile profile, String checksum,
			boolean accepted, boolean previousPreserved) {
		Map<ContentState, Integer> stateCounts = new EnumMap<ContentState, Integer>(
				ContentState.class);
		Map<ContentKind, Integer> kindCounts = new EnumMap<ContentKind, Integer>(
				ContentKind.class);
		for(ContentState state : ContentState.values()) stateCounts.put(state, 0);
		for(ContentKind kind : ContentKind.values()) kindCounts.put(kind, 0);
		for(ContentRule rule : profile.getEffectiveRules()) {
			stateCounts.put(rule.getState(), stateCounts.get(rule.getState()) + 1);
			kindCounts.put(rule.getKind(), kindCounts.get(rule.getKind()) + 1);
		}

		StringBuilder report = new StringBuilder();
		report.append("profilePath=").append(profileFile.getAbsolutePath()).append('\n');
		report.append("profileId=").append(profile.getProfileId()).append('\n');
		report.append("schemaVersion=").append(profile.getSchemaVersion()).append('\n');
		report.append("mode=").append(strict ? "STRICT" : "PERMISSIVE").append('\n');
		report.append("accepted=").append(accepted).append('\n');
		report.append("previousProfilePreserved=").append(previousPreserved).append('\n');
		report.append("rules=").append(profile.getRuleCount()).append('\n');
		report.append("tags=").append(profile.getTagCount()).append('\n');
		report.append("checksum=").append(checksum).append('\n');
		report.append("issues.info=").append(validation.count(Severity.INFO)).append('\n');
		report.append("issues.warning=").append(validation.count(Severity.WARNING)).append('\n');
		report.append("issues.error=").append(validation.count(Severity.ERROR)).append('\n');
		for(ContentState state : ContentState.values()) {
			report.append("state.").append(state.name()).append('=')
					.append(stateCounts.get(state)).append('\n');
		}
		for(ContentKind kind : ContentKind.values()) {
			report.append("kind.").append(kind.name()).append('=')
					.append(kindCounts.get(kind)).append('\n');
		}
		report.append("issues:\n");
		for(ProfileValidationIssue issue : validation.getIssues()) {
			report.append(issue.toString()).append('\n');
		}
		return report.toString();
	}

	private static File resolveReportDirectory() throws IOException {
		File workingDirectory = new File(System.getProperty("user.dir", ".")).getCanonicalFile();
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
	}

	private static void writeMinimalExample() {
		File example = new File(getProfileFile().getParentFile(),
				"content_profile.example.json");
		if(example.exists()) return;
		String json = "{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"profileId\": \"example_available_profile\",\n"
				+ "  \"defaultState\": \"AVAILABLE\",\n"
				+ "  \"rules\": [],\n"
				+ "  \"tags\": {},\n"
				+ "  \"notes\": \"Copy to content_profile.json and edit; this file is not loaded.\"\n"
				+ "}\n";
		try {
			writeTextAtomic(example, json, false);
			MainRegistry.logger.info("Wrote WOC content profile example to {}",
					example.getAbsolutePath());
		} catch(FileAlreadyExistsException ex) {
			// A concurrent startup created the example; never overwrite it.
		} catch(IOException ex) {
			MainRegistry.logger.warn("Unable to write WOC content profile example at {}: {}",
					example.getAbsolutePath(), ex.getMessage());
		}
	}

	private static void writeTextAtomic(File destination, String content, boolean overwrite)
			throws IOException {
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
				move(temporary, destination.toPath(), overwrite, true);
			} catch(AtomicMoveNotSupportedException ex) {
				move(temporary, destination.toPath(), overwrite, false);
			}
			moved = true;
		} finally {
			if(!moved) Files.deleteIfExists(temporary);
		}
	}

	private static void move(Path source, Path destination, boolean overwrite, boolean atomic)
			throws IOException {
		if(atomic && overwrite) {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} else if(atomic) {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} else if(overwrite) {
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		} else {
			Files.move(source, destination);
		}
	}

	private static void logSummary(String action, File path, boolean strict, Attempt attempt,
			boolean accepted) {
		ContentProfile profile = attempt.effectiveProfile == null
				? active.profile : attempt.effectiveProfile;
		String checksum = attempt.checksum.isEmpty() ? active.checksum : attempt.checksum;
		MainRegistry.logger.info("WOC content profile {}: path={} mode={} accepted={} profile={} "
						+ "rules={} tags={} checksum={} issues={}/{}/{}",
				action, path.getAbsolutePath(), strict ? "STRICT" : "PERMISSIVE", accepted,
				profile.getProfileId(), profile.getRuleCount(), profile.getTagCount(), checksum,
				attempt.validation.count(Severity.INFO),
				attempt.validation.count(Severity.WARNING),
				attempt.validation.count(Severity.ERROR));
	}

	private static String valueOrDash(String value) {
		return value == null || value.isEmpty() ? "-" : value;
	}
}
