package com.hbm.wocbridge.enforcement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hbm.wocbridge.enforcement.EnforcementAdapterResult.ReloadClassification;
import com.hbm.wocbridge.enforcement.RuntimeContentKeys.SmeltingRecord;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

public final class SmeltingRecipeEnforcer {

	private SmeltingRecipeEnforcer() { }

	public static EnforcementAdapterResult apply(ContentEnforcementPolicy policy,
			boolean mutate) {
		EnforcementAdapterResult result = new EnforcementAdapterResult(
				"smelting", ReloadClassification.RESTART_REQUIRED);
		result.covered("minecraft:furnace");
		if(!policy.isActive()) return result;

		List<SmeltingRecord> selected = new ArrayList<SmeltingRecord>();
		for(SmeltingRecord record : RuntimeContentKeys.smeltingRecords()) {
			result.examined();
			String matched = firstDisabled(policy, record.recipeKey, record.outputKey);
			if(matched.isEmpty()) continue;
			result.planned(matched);
			selected.add(record);
		}
		if(!mutate) return result;

		Map<ItemStack, ItemStack> recipes = FurnaceRecipes.smelting().getSmeltingList();
		for(SmeltingRecord record : selected) {
			if(recipes.remove(record.input) != null) result.mutated();
		}
		return result;
	}

	private static String firstDisabled(ContentEnforcementPolicy policy, String recipeKey,
			String outputKey) {
		if(policy.isDisabled(recipeKey)) return recipeKey;
		if(policy.isDisabled(outputKey)) return outputKey;
		return "";
	}
}
