package com.hbm.wocbridge.config;

import net.minecraftforge.common.config.Configuration;

public final class WocDevelopmentConfig {

	public static final String CATEGORY = "11_war_of_civilizations";

	public static boolean enableDevelopmentTools = true;

	private WocDevelopmentConfig() { }

	public static void loadFromConfig(Configuration config) {
		config.addCustomCategoryComment(CATEGORY,
				"War of Civilizations development tools. Disable these tools on production servers.");
		enableDevelopmentTools = config.get(
				CATEGORY,
				"enableDevelopmentTools",
				true,
				"Enables operator-only WOC development commands such as /wocdev export-content. "
						+ "Disable this and restart before using the mod on a production server.")
				.getBoolean(true);
	}
}
