package com.hbm.wocbridge.config;

import net.minecraftforge.common.config.Configuration;

public final class WocDevelopmentConfig {

	public static final String CATEGORY = "11_war_of_civilizations";

	public static boolean enableDevelopmentTools = true;
	public static boolean enableProgressionExport = false;
	public static boolean strictContentProfileValidation = false;
	public static boolean writeContentProfileExample = true;
	public static boolean enableContentEnforcement = false;
	public static boolean enforceDisabledCrafting = true;
	public static boolean enforceDisabledSmelting = true;
	public static boolean enforceDisabledMachineRecipes = true;
	public static boolean enforceDisabledItemUse = true;
	public static boolean enforceDisabledBlockPlacement = true;
	public static boolean enforceDisabledLoot = true;
	public static boolean enforceDisabledWorldgen = true;
	public static boolean hideDisabledFromNEI = true;
	public static boolean sanitizeDisabledInventory = false;
	public static boolean strictEnforcementStartup = false;
	public static boolean writeEnforcementAudit = true;
	public static boolean enableContentTaxonomyTools = true;
	public static String taxonomyDefinitionPath =
			"docs/woc/taxonomy/hbm_taxonomy.v1.json";
	public static boolean writeTaxonomyReports = true;
	public static boolean allowTaxonomyHeuristicSuggestions = false;
	public static boolean taxonomyStrictValidation = true;
	public static boolean writeDraftProfiles = true;

	private WocDevelopmentConfig() { }

	public static void loadFromConfig(Configuration config) {
		config.addCustomCategoryComment(CATEGORY,
				"War of Civilizations development tools, content profiles, and DISABLED-only enforcement.");
		enableDevelopmentTools = config.get(
				CATEGORY,
				"enableDevelopmentTools",
				true,
				"Enables operator-only WOC development commands such as /wocdev export-content. "
						+ "Disable this and restart before using the mod on a production server.")
				.getBoolean(true);
		enableProgressionExport = config.get(
				CATEGORY,
				"enableProgressionExport",
				false,
				"Enables the operator-only, read-only /wocdev export-progression "
						+ "command and its structural fixtures. Disabled by default.")
				.getBoolean(false);
		strictContentProfileValidation = config.get(
				CATEGORY,
				"strictContentProfileValidation",
				false,
				"Rejects unresolved, duplicate, malformed, or UNREVIEWED content-profile rules. "
						+ "Enable this on production servers after the profile validates cleanly.")
				.getBoolean(false);
		writeContentProfileExample = config.get(
				CATEGORY,
				"writeContentProfileExample",
				true,
				"Writes content_profile.example.json atomically when the real profile is missing. "
						+ "The example is never loaded and is never overwritten.")
				.getBoolean(true);
		enableContentEnforcement = config.get(
				CATEGORY, "enableContentEnforcement", false,
				"Enables Phase 3 enforcement for rules whose exact state is DISABLED. "
						+ "Missing or rejected profiles fail open.").getBoolean(false);
		enforceDisabledCrafting = config.get(
				CATEGORY, "enforceDisabledCrafting", true,
				"Removes disabled crafting recipes during server startup.").getBoolean(true);
		enforceDisabledSmelting = config.get(
				CATEGORY, "enforceDisabledSmelting", true,
				"Removes disabled furnace recipes during server startup.").getBoolean(true);
		enforceDisabledMachineRecipes = config.get(
				CATEGORY, "enforceDisabledMachineRecipes", true,
				"Removes disabled recipes from supported HBM machine registries at startup.")
				.getBoolean(true);
		enforceDisabledItemUse = config.get(
				CATEGORY, "enforceDisabledItemUse", true,
				"Denies player use of exact disabled item metadata variants on the server.")
				.getBoolean(true);
		enforceDisabledBlockPlacement = config.get(
				CATEGORY, "enforceDisabledBlockPlacement", true,
				"Denies placement of exact disabled block metadata variants on the server.")
				.getBoolean(true);
		enforceDisabledLoot = config.get(
				CATEGORY, "enforceDisabledLoot", true,
				"Removes disabled entries from HBM ItemPool structure loot at startup.")
				.getBoolean(true);
		enforceDisabledWorldgen = config.get(
				CATEGORY, "enforceDisabledWorldgen", true,
				"Disables supported weighted HBM NBT structures before new chunk generation.")
				.getBoolean(true);
		hideDisabledFromNEI = config.get(
				CATEGORY, "hideDisabledFromNEI", true,
				"Hides exact disabled item/block metadata variants from NEI on clients.")
				.getBoolean(true);
		sanitizeDisabledInventory = config.get(
				CATEGORY, "sanitizeDisabledInventory", false,
				"Reserved safety switch. Phase 3 does not scan or mutate inventories.")
				.getBoolean(false);
		strictEnforcementStartup = config.get(
				CATEGORY, "strictEnforcementStartup", false,
				"Stops startup if an active DISABLED rule targets an unsupported enforcement path.")
				.getBoolean(false);
		writeEnforcementAudit = config.get(
				CATEGORY, "writeEnforcementAudit", true,
				"Writes a deterministic Phase 3 enforcement audit under build/reports/woc.")
				.getBoolean(true);
		enableContentTaxonomyTools = config.get(
				CATEGORY, "enableContentTaxonomyTools", true,
				"Enables explicit Phase 4 taxonomy validation, reports, and draft generation. "
						+ "Taxonomy is not loaded during normal gameplay.")
				.getBoolean(true);
		taxonomyDefinitionPath = config.get(
				CATEGORY, "taxonomyDefinitionPath",
				"docs/woc/taxonomy/hbm_taxonomy.v1.json",
				"Repository-relative or absolute Phase 4 taxonomy definition path.")
				.getString();
		writeTaxonomyReports = config.get(
				CATEGORY, "writeTaxonomyReports", true,
				"Writes deterministic taxonomy reports under build/reports/woc only "
						+ "when an explicit taxonomy command is run.")
				.getBoolean(true);
		allowTaxonomyHeuristicSuggestions = config.get(
				CATEGORY, "allowTaxonomyHeuristicSuggestions", false,
				"Reserved for optional unreviewed suggestions. Phase 4 never promotes "
						+ "heuristic suggestions to reviewed classifications.")
				.getBoolean(false);
		taxonomyStrictValidation = config.get(
				CATEGORY, "taxonomyStrictValidation", true,
				"Rejects inconsistent Phase 4 vocabulary, duplicate rules, and unsafe "
						+ "selector definitions.")
				.getBoolean(true);
		writeDraftProfiles = config.get(
				CATEGORY, "writeDraftProfiles", true,
				"Allows explicit taxonomy generation to refresh documented draft profiles. "
						+ "Generated profiles are never installed into config.")
				.getBoolean(true);
	}
}
