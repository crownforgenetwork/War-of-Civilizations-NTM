package com.hbm.wocbridge.taxonomy;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TaxonomyVocabulary {

	public static final Set<String> TAGS = values(
			"material.raw", "material.processed", "material.strategic", "material.nuclear",
			"component.mechanical", "component.electrical", "component.electronic",
			"component.nuclear", "fuel.solid", "fuel.liquid", "fuel.gaseous",
			"fuel.nuclear", "waste", "chemical", "tool", "utility", "decorative",
			"food", "medical", "protective.armor", "protective.hazmat",
			"weapon.firearm", "weapon.ammunition", "weapon.explosive", "weapon.missile",
			"weapon.nuclear", "weapon.energy", "weapon.melee", "weapon.launcher",
			"weapon.incendiary", "weapon.chemical", "weapon.artillery", "weapon.special",
			"machine.processing",
			"machine.power", "machine.nuclear", "machine.logistics", "machine.storage",
			"machine.military", "infrastructure", "worldgen.ore", "worldgen.structure",
			"loot", "debug", "test", "deprecated", "unobtainable", "creative_only");

	public static final Set<String> DISPOSITIONS = values(
			"keep", "future_research", "future_project", "future_event",
			"disable_candidate", "replacement_candidate", "debug_disable", "unresolved");

	public static final Set<String> STRATEGIC_CLASSES = values(
			"civilian", "dual_use", "military", "strategic", "nuclear",
			"administrative", "development_only");

	public static final Set<String> REPLACEMENT_POLICIES = values(
			"none", "external_gun_mod", "external_vehicle_mod", "WOC_system",
			"server_event_only", "admin_only");

	public static final Set<String> FUTURE_OWNER_MODULES = values(
			"HBM", "WOC-Core", "WOC-Research", "WOC-Territory", "WOC-Diplomacy",
			"WOC-Operations", "WOC-Economy", "WOC-NPC", "external_mod", "unresolved");

	public static final Set<String> RESEARCH_DOMAINS = values(
			"", "metallurgy", "industrial_processing", "electrical_engineering",
			"electronics", "chemistry", "conventional_weapons", "rocketry",
			"nuclear_engineering", "reactor_engineering", "radiation_protection",
			"logistics", "advanced_materials");

	public static final Set<String> REVIEW_STATUSES = values(
			"reviewed", "provisional", "unreviewed");

	public static final Set<String> STRATEGIC_SENSITIVITIES = values(
			"routine", "controlled", "sensitive", "strategic", "catastrophic",
			"unresolved");

	public static final Set<String> CONTENT_KINDS = values(
			"ITEM", "BLOCK", "ENTITY", "FLUID", "CRAFTING_RECIPE", "SMELTING_RECIPE",
			"MACHINE_RECIPE", "CREATIVE_TAB", "STRUCTURE", "STRUCTURE_LOOT",
			"LOOT_ENTRY", "WORLDGEN_FEATURE");

	private TaxonomyVocabulary() { }

	private static Set<String> values(String... values) {
		return Collections.unmodifiableSet(
				new LinkedHashSet<String>(Arrays.asList(values)));
	}
}
