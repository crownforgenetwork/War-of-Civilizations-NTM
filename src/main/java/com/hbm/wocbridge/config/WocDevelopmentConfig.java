package com.hbm.wocbridge.config;

import net.minecraftforge.common.config.Configuration;

public final class WocDevelopmentConfig {

	public static final String CATEGORY = "11_war_of_civilizations";

	public static boolean enableDevelopmentTools = true;
	public static boolean strictContentProfileValidation = false;
	public static boolean writeContentProfileExample = true;

	private WocDevelopmentConfig() { }

	public static void loadFromConfig(Configuration config) {
		config.addCustomCategoryComment(CATEGORY,
				"War of Civilizations development tools and read-only content profile settings.");
		enableDevelopmentTools = config.get(
				CATEGORY,
				"enableDevelopmentTools",
				true,
				"Enables operator-only WOC development commands such as /wocdev export-content. "
						+ "Disable this and restart before using the mod on a production server.")
				.getBoolean(true);
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
	}
}
