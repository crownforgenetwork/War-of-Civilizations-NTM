package com.hbm.wocbridge.taxonomy;

public final class ContentTaxonomyTool {

	private ContentTaxonomyTool() { }

	public static void main(String[] args) {
		String operation = args.length == 0 ? "validate" : args[0];
		ContentTaxonomyService.Run run;
		if("report".equals(operation)) {
			run = ContentTaxonomyService.report();
		} else if("generate-profiles".equals(operation)) {
			run = ContentTaxonomyService.generateProfiles();
		} else if("fixtures".equals(operation)) {
			java.util.List<String> failures = ContentTaxonomyFixtures.run();
			System.out.println("fixtureFailures=" + failures.size());
			for(String failure : failures) System.out.println("  " + failure);
			if(!failures.isEmpty()) throw new IllegalStateException("Fixture failures");
			return;
		} else {
			run = ContentTaxonomyService.validate();
		}
		System.out.println("operation=" + operation);
		System.out.println("valid=" + run.isValid());
		System.out.println("errors=" + run.getErrors().size());
		if(run.getSummary() != null) {
			System.out.println("total=" + run.getSummary().getTotal());
			System.out.println("reviewed=" + run.getSummary().getReviewed());
			System.out.println("ambiguous=" + run.getSummary().getAmbiguous());
			System.out.println("taxonomyChecksum=" + run.getTaxonomyChecksum());
			System.out.println("inventoryChecksum=" + run.getInventory().getChecksum());
		}
		for(String error : run.getErrors()) System.out.println("  " + error);
		if(!run.isValid()) throw new IllegalStateException("Taxonomy operation failed");
	}
}
