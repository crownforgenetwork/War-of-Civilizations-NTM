package com.hbm.wocbridge.enforcement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hbm.main.MainRegistry;
import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.content.ContentKind;
import com.hbm.wocbridge.content.ContentProfileManager;
import com.hbm.wocbridge.content.ContentProfileManager.ValidationRun;
import com.hbm.wocbridge.content.ContentProfileManager.ToolingValidation;
import com.hbm.wocbridge.enforcement.EnforcementAdapterResult.ReloadClassification;

import net.minecraftforge.common.MinecraftForge;

public final class ContentEnforcementManager {

	private static volatile ContentEnforcementPolicy policy =
			ContentEnforcementPolicy.forFixture(false, "not initialized");
	private static boolean eventHandlersRegistered;

	private ContentEnforcementManager() { }

	public static synchronized void initialize() {
		policy = ContentEnforcementPolicy.fromActiveProfile();
		ContentEnforcementAudit.reset(policy);
		registerEventHandlers();

		List<EnforcementAdapterResult> results;
		if(WocDevelopmentConfig.strictEnforcementStartup && policy.isActive()) {
			results = runAdapters(policy, false);
			List<String> failures = strictFailures(policy, results);
			if(!failures.isEmpty()) {
				throw new IllegalStateException("WOC strict enforcement startup rejected: "
						+ failures);
			}
		}
		results = runAdapters(policy, true);
		for(EnforcementAdapterResult result : results) {
			ContentEnforcementAudit.recordAdapter(result);
		}

		File audit = null;
		if(WocDevelopmentConfig.writeEnforcementAudit) {
			try {
				audit = ContentEnforcementAudit.write();
			} catch(IOException ex) {
				MainRegistry.logger.error("Unable to write WOC enforcement audit", ex);
			}
		}
		int mutated = totalMutated(results);
		if(policy.isActive()) {
			MainRegistry.logger.info("WOC DISABLED enforcement: active=true rules={} "
							+ "mutations={} audit={}",
					policy.getDisabledKeys().size(), mutated,
					audit == null ? "disabled-or-write-failed" : audit.getAbsolutePath());
		} else {
			MainRegistry.logger.warn("WOC DISABLED enforcement is inactive and HBM gameplay "
							+ "is unchanged: {}",
					policy.getInactiveReason());
		}
	}

	public static synchronized ValidationRun reloadProfile() {
		ValidationRun run = ContentProfileManager.reload();
		if(!run.isAccepted()) return run;

		policy = ContentEnforcementPolicy.fromActiveProfile();
		ContentEnforcementAudit.reset(policy);
		registerEventHandlers();
		for(EnforcementAdapterResult result : runAdapters(policy, false)) {
			ContentEnforcementAudit.recordAdapter(result);
			if(result.getReloadClassification() != ReloadClassification.LIVE_RELOAD_SAFE
					&& hasDisabledRulesForCategory(policy, result.getCategory())) {
				ContentEnforcementAudit.requireRestart(result.getCategory()
						+ " registry changes are startup-only");
			}
		}
		if(WocDevelopmentConfig.writeEnforcementAudit) {
			try {
				ContentEnforcementAudit.write();
			} catch(IOException ex) {
				MainRegistry.logger.error("Unable to write WOC enforcement audit after reload", ex);
			}
		}
		return run;
	}

	public static List<EnforcementAdapterResult> dryRun() {
		return Collections.unmodifiableList(runAdapters(
				ContentEnforcementPolicy.fromActiveProfile(), false));
	}

	public static List<EnforcementAdapterResult> dryRunProfile(
			File profileFile, boolean strict) {
		ToolingValidation validation =
				ContentProfileManager.validateExternalProfile(profileFile, strict);
		if(!validation.isAccepted() || validation.getProfile() == null) {
			return Collections.emptyList();
		}
		ContentEnforcementPolicy toolingPolicy =
				ContentEnforcementPolicy.forTooling(
						validation.getProfile(), validation.getChecksum());
		return Collections.unmodifiableList(runAdapters(toolingPolicy, false));
	}

	public static ContentEnforcementPolicy getPolicy() {
		return policy;
	}

	public static boolean isItemUseEnabled() {
		return WocDevelopmentConfig.enforceDisabledItemUse;
	}

	public static boolean isBlockPlacementEnabled() {
		return WocDevelopmentConfig.enforceDisabledBlockPlacement;
	}

	public static String explain(String key) {
		ContentEnforcementDecision decision = policy.decide(key);
		ContentKind kind = ContentKind.inferFromKey(key);
		return "key=" + key
				+ " enforcementActive=" + policy.isActive()
				+ " adapters=" + adaptersFor(kind)
				+ " decision=" + (decision.isDenied() ? "DENY" : "ALLOW")
				+ " reason=" + (decision.isDenied()
						? decision.getReason() : policy.isActive()
								? "not explicitly DISABLED" : policy.getInactiveReason());
	}

	private static void registerEventHandlers() {
		if(eventHandlersRegistered) {
			ContentEnforcementAudit.setEventsRegistered(true);
			return;
		}
		MinecraftForge.EVENT_BUS.register(new ItemUseEnforcer());
		MinecraftForge.EVENT_BUS.register(new BlockPlacementEnforcer());
		eventHandlersRegistered = true;
		ContentEnforcementAudit.setEventsRegistered(true);
	}

	private static List<EnforcementAdapterResult> runAdapters(
			ContentEnforcementPolicy activePolicy, boolean mutate) {
		List<EnforcementAdapterResult> results =
				new ArrayList<EnforcementAdapterResult>();
		if(WocDevelopmentConfig.enforceDisabledCrafting) {
			results.add(CraftingRecipeEnforcer.apply(activePolicy, mutate));
		}
		if(WocDevelopmentConfig.enforceDisabledSmelting) {
			results.add(SmeltingRecipeEnforcer.apply(activePolicy, mutate));
		}
		if(WocDevelopmentConfig.enforceDisabledMachineRecipes) {
			results.add(MachineRecipeEnforcer.apply(activePolicy, mutate));
		}
		if(WocDevelopmentConfig.enforceDisabledLoot) {
			results.add(LootEnforcer.apply(activePolicy, mutate));
		}
		if(WocDevelopmentConfig.enforceDisabledWorldgen) {
			results.add(WorldgenEnforcer.apply(activePolicy, mutate));
		}
		results.add(describeLiveAdapter(activePolicy, "itemUse",
				ContentKind.ITEM, WocDevelopmentConfig.enforceDisabledItemUse));
		results.add(describeLiveAdapter(activePolicy, "blockPlacement",
				ContentKind.BLOCK, WocDevelopmentConfig.enforceDisabledBlockPlacement));
		results.add(describePresentationAdapter(activePolicy));
		return results;
	}

	private static EnforcementAdapterResult describeLiveAdapter(
			ContentEnforcementPolicy activePolicy, String category, ContentKind kind,
			boolean enabled) {
		EnforcementAdapterResult result = new EnforcementAdapterResult(
				category, ReloadClassification.LIVE_RELOAD_SAFE);
		result.covered("MinecraftForge server event bus");
		if(!activePolicy.isActive() || !enabled) return result;
		for(String key : activePolicy.getDisabledKeys()) {
			if(kind.matchesKey(key)) {
				result.examined();
				result.planned(key);
			}
		}
		return result;
	}

	private static EnforcementAdapterResult describePresentationAdapter(
			ContentEnforcementPolicy activePolicy) {
		EnforcementAdapterResult result = new EnforcementAdapterResult(
				"nei", ReloadClassification.RESTART_REQUIRED);
		result.covered("client-only exact-metadata NEI hide helper");
		if(!activePolicy.isActive() || !WocDevelopmentConfig.hideDisabledFromNEI) return result;
		for(String key : activePolicy.getDisabledKeys()) {
			ContentKind kind = ContentKind.inferFromKey(key);
			if(kind == ContentKind.ITEM || kind == ContentKind.BLOCK) {
				result.examined();
				result.planned(key);
			}
		}
		return result;
	}

	private static List<String> strictFailures(ContentEnforcementPolicy activePolicy,
			List<EnforcementAdapterResult> results) {
		List<String> failures = new ArrayList<String>();
		for(String key : activePolicy.getDisabledKeys()) {
			ContentKind kind = ContentKind.inferFromKey(key);
			if(kind == ContentKind.ITEM) {
				if(!WocDevelopmentConfig.enforceDisabledItemUse) {
					failures.add(key + " has no enabled server item-use path");
				}
				continue;
			}
			if(kind == ContentKind.BLOCK) {
				if(!WocDevelopmentConfig.enforceDisabledBlockPlacement) {
					failures.add(key + " has no enabled server placement path");
				}
				continue;
			}
			String category = categoryFor(kind);
			if(category == null || !hasPlannedKey(results, category, key)) {
				failures.add(key + " is unsupported or its adapter is disabled");
			}
		}
		Collections.sort(failures);
		return failures;
	}

	private static String categoryFor(ContentKind kind) {
		if(kind == ContentKind.CRAFTING_RECIPE) return "crafting";
		if(kind == ContentKind.SMELTING_RECIPE) return "smelting";
		if(kind == ContentKind.MACHINE_RECIPE) return "machine";
		if(kind == ContentKind.STRUCTURE_LOOT || kind == ContentKind.LOOT_ENTRY) return "loot";
		if(kind == ContentKind.WORLDGEN_FEATURE) return "worldgen";
		return null;
	}

	private static boolean hasPlannedKey(List<EnforcementAdapterResult> results,
			String category, String key) {
		for(EnforcementAdapterResult result : results) {
			if(category.equals(result.getCategory())
					&& result.getActionKeys().contains(key)) return true;
		}
		return false;
	}

	private static int totalMutated(List<EnforcementAdapterResult> results) {
		int total = 0;
		for(EnforcementAdapterResult result : results) total += result.getMutated();
		return total;
	}

	private static boolean hasDisabledRulesForCategory(
			ContentEnforcementPolicy activePolicy, String category) {
		if("crafting".equals(category)) {
			return activePolicy.hasDisabledKind(ContentKind.CRAFTING_RECIPE);
		}
		if("smelting".equals(category)) {
			return activePolicy.hasDisabledKind(ContentKind.SMELTING_RECIPE);
		}
		if("machine".equals(category)) {
			return activePolicy.hasDisabledKind(ContentKind.MACHINE_RECIPE);
		}
		if("loot".equals(category)) {
			return activePolicy.hasDisabledKind(ContentKind.STRUCTURE_LOOT)
					|| activePolicy.hasDisabledKind(ContentKind.LOOT_ENTRY);
		}
		if("worldgen".equals(category)) {
			return activePolicy.hasDisabledKind(ContentKind.WORLDGEN_FEATURE);
		}
		if("nei".equals(category)) {
			return activePolicy.hasDisabledKind(ContentKind.ITEM)
					|| activePolicy.hasDisabledKind(ContentKind.BLOCK);
		}
		return false;
	}

	private static String adaptersFor(ContentKind kind) {
		if(kind == ContentKind.ITEM) return "[itemUse,recipeOutputs,lootOutputs,nei]";
		if(kind == ContentKind.BLOCK) return "[blockPlacement,nei]";
		if(kind == ContentKind.CRAFTING_RECIPE) return "[crafting]";
		if(kind == ContentKind.SMELTING_RECIPE) return "[smelting]";
		if(kind == ContentKind.MACHINE_RECIPE) return "[machine]";
		if(kind == ContentKind.STRUCTURE_LOOT || kind == ContentKind.LOOT_ENTRY) {
			return "[loot]";
		}
		if(kind == ContentKind.WORLDGEN_FEATURE) return "[worldgen]";
		return "[unsupported-in-phase3]";
	}
}
