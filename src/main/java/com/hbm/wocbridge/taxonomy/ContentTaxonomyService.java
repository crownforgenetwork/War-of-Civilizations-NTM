package com.hbm.wocbridge.taxonomy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonParser;
import com.hbm.wocbridge.config.WocDevelopmentConfig;

public final class ContentTaxonomyService {

	public static final class Run {
		private final File taxonomyFile;
		private final File inventoryDirectory;
		private final File reportDirectory;
		private final ContentTaxonomy taxonomy;
		private final String taxonomyChecksum;
		private final ContentTaxonomyValidator.Result validation;
		private final ContentInventory inventory;
		private final List<ContentTaxonomyResult> results;
		private final ContentProfileDraftGenerator.Draft wocDraft;
		private final ContentProfileDraftGenerator.Draft firearmsDraft;
		private final ContentTaxonomyAudit.Summary summary;
		private final List<String> fixtureFailures;
		private final List<String> errors;

		private Run(File taxonomyFile, File inventoryDirectory, File reportDirectory,
				ContentTaxonomy taxonomy, String taxonomyChecksum,
				ContentTaxonomyValidator.Result validation, ContentInventory inventory,
				List<ContentTaxonomyResult> results,
				ContentProfileDraftGenerator.Draft wocDraft,
				ContentProfileDraftGenerator.Draft firearmsDraft,
				ContentTaxonomyAudit.Summary summary, List<String> fixtureFailures,
				List<String> errors) {
			this.taxonomyFile = taxonomyFile;
			this.inventoryDirectory = inventoryDirectory;
			this.reportDirectory = reportDirectory;
			this.taxonomy = taxonomy;
			this.taxonomyChecksum = value(taxonomyChecksum);
			this.validation = validation;
			this.inventory = inventory;
			this.results = immutable(results);
			this.wocDraft = wocDraft;
			this.firearmsDraft = firearmsDraft;
			this.summary = summary;
			this.fixtureFailures = immutable(fixtureFailures);
			this.errors = immutable(errors);
		}

		public boolean isValid() {
			return errors.isEmpty() && validation != null && validation.isValid();
		}

		public File getTaxonomyFile() {
			return taxonomyFile;
		}

		public File getInventoryDirectory() {
			return inventoryDirectory;
		}

		public File getReportDirectory() {
			return reportDirectory;
		}

		public ContentTaxonomy getTaxonomy() {
			return taxonomy;
		}

		public String getTaxonomyChecksum() {
			return taxonomyChecksum;
		}

		public ContentTaxonomyValidator.Result getValidation() {
			return validation;
		}

		public ContentInventory getInventory() {
			return inventory;
		}

		public List<ContentTaxonomyResult> getResults() {
			return results;
		}

		public ContentTaxonomyResult getResult(String key) {
			for(ContentTaxonomyResult result : results) {
				if(result.getEntry().getCanonicalKey().equals(key)) return result;
			}
			return null;
		}

		public ContentProfileDraftGenerator.Draft getWocDraft() {
			return wocDraft;
		}

		public ContentProfileDraftGenerator.Draft getFirearmsDraft() {
			return firearmsDraft;
		}

		public ContentTaxonomyAudit.Summary getSummary() {
			return summary;
		}

		public List<String> getFixtureFailures() {
			return fixtureFailures;
		}

		public List<String> getErrors() {
			return errors;
		}

		private static <T> List<T> immutable(List<T> source) {
			return Collections.unmodifiableList(new ArrayList<T>(source));
		}
	}

	private static volatile Run latest;

	private ContentTaxonomyService() { }

	public static synchronized Run validate() {
		return execute(false, false);
	}

	public static synchronized Run report() {
		return execute(true, false);
	}

	public static synchronized Run generateProfiles() {
		return execute(true, true);
	}

	public static Run getLatest() {
		return latest;
	}

	private static Run execute(boolean writeReports, boolean writeDocumentationDrafts) {
		File root = repositoryRoot();
		File taxonomyFile = resolve(root, WocDevelopmentConfig.taxonomyDefinitionPath);
		File reportDirectory = new File(root,
				"build" + File.separator + "reports" + File.separator + "woc");
		File inventoryDirectory = reportDirectory;
		List<String> errors = new ArrayList<String>();

		ContentTaxonomyLoader.LoadResult loaded =
				new ContentTaxonomyLoader().load(taxonomyFile);
		ContentTaxonomyValidator.Result validation =
				ContentTaxonomyValidator.validate(loaded.getTaxonomy(),
						loaded.getErrors(),
						WocDevelopmentConfig.taxonomyStrictValidation);
		if(!validation.isValid()) errors.addAll(validation.getErrors());
		if(loaded.getTaxonomy() == null) {
			Run failed = new Run(taxonomyFile, inventoryDirectory, reportDirectory,
					null, "", validation, null,
					Collections.<ContentTaxonomyResult>emptyList(), null, null, null,
					Collections.<String>emptyList(), errors);
			latest = failed;
			return failed;
		}

		String taxonomyChecksum = "";
		try {
			taxonomyChecksum = TaxonomyIo.sha256(TaxonomyIo.canonicalJson(
					new JsonParser().parse(TaxonomyIo.readUtf8(taxonomyFile))));
		} catch(Exception ex) {
			errors.add("TAXONOMY_CHECKSUM_FAILED: " + conciseMessage(ex));
		}
		ContentInventory inventory = new ContentInventoryLoader().load(inventoryDirectory);
		if(inventory.size() == 0) {
			errors.add("INVENTORY_EMPTY: run /wocdev export-content first");
		}
		ContentTaxonomyClassifier classifier =
				new ContentTaxonomyClassifier(loaded.getTaxonomy());
		List<ContentTaxonomyResult> results = classifier.classify(inventory);
		ContentProfileDraftGenerator.Draft wocDraft =
				ContentProfileDraftGenerator.generateWocServer(
						results, taxonomyChecksum, inventory.getChecksum());
		ContentProfileDraftGenerator.Draft firearmsDraft =
				ContentProfileDraftGenerator.generateNoConventionalFirearms(
						loaded.getTaxonomy(), results, taxonomyChecksum,
						inventory.getChecksum());
		ContentTaxonomyAudit.Summary summary = ContentTaxonomyAudit.summarize(results);
		for(String failure : ContentTaxonomyAudit.reconciliationFailures(
				results, wocDraft, firearmsDraft)) {
			errors.add("RECONCILIATION_FAILED: " + failure);
		}
		if(!ContentProfileDraftGenerator.COMPLETE.equals(
				firearmsDraft.getCompleteness())) {
			errors.add("CONVENTIONAL_FIREARMS_BOUNDARY_INCOMPLETE: "
					+ firearmsDraft.getUnresolvedBoundaryKeys());
		}
		List<String> fixtureFailures =
				ContentTaxonomyFixtures.run(loaded.getTaxonomy(), results);
		if(!fixtureFailures.isEmpty()) errors.add("FIXTURE_FAILURES: "
				+ fixtureFailures.size());

		if(writeReports && WocDevelopmentConfig.writeTaxonomyReports && errors.isEmpty()) {
			try {
				ContentTaxonomyAudit.write(reportDirectory, loaded.getTaxonomy(),
						taxonomyChecksum, inventory, results, wocDraft,
						firearmsDraft, validation);
				TaxonomyIo.writeAtomic(new File(reportDirectory,
						"content_profile_woc_server_draft.json"), wocDraft.getJson());
				TaxonomyIo.writeAtomic(new File(reportDirectory,
						"content_profile_no_hbm_conventional_firearms_draft.json"),
						firearmsDraft.getJson());
			} catch(IOException ex) {
				errors.add("REPORT_WRITE_FAILED: " + conciseMessage(ex));
			}
		}
		if(writeDocumentationDrafts && WocDevelopmentConfig.writeDraftProfiles
				&& errors.isEmpty()) {
			File exampleDirectory = new File(root,
					"docs" + File.separator + "woc" + File.separator + "examples");
			try {
				TaxonomyIo.writeAtomic(new File(exampleDirectory,
						"content_profile.woc_server.draft.json"), wocDraft.getJson());
				TaxonomyIo.writeAtomic(new File(exampleDirectory,
						"content_profile.no_hbm_conventional_firearms.draft.json"),
						firearmsDraft.getJson());
			} catch(IOException ex) {
				errors.add("DRAFT_WRITE_FAILED: " + conciseMessage(ex));
			}
		}

		Collections.sort(errors);
		Run run = new Run(taxonomyFile, inventoryDirectory, reportDirectory,
				loaded.getTaxonomy(), taxonomyChecksum, validation, inventory,
				results, wocDraft, firearmsDraft, summary, fixtureFailures, errors);
		latest = run;
		return run;
	}

	public static File repositoryRoot() {
		File candidate;
		try {
			candidate = new File(System.getProperty("user.dir", ".")).getCanonicalFile();
		} catch(IOException ex) {
			candidate = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
		}
		for(int depth = 0; depth < 8 && candidate != null; depth++) {
			if(new File(candidate, "build.gradle").isFile()) return candidate;
			candidate = candidate.getParentFile();
		}
		return new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
	}

	private static File resolve(File root, String path) {
		File file = new File(path);
		return file.isAbsolute() ? file : new File(root, path);
	}

	private static String conciseMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null || message.isEmpty()
				? exception.getClass().getSimpleName() : message;
	}

	private static String value(String source) {
		return source == null ? "" : source;
	}
}
