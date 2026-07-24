package com.hbm.wocbridge.enforcement;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.hbm.wocbridge.enforcement.EnforcementAdapterResult.ReloadClassification;
import com.hbm.wocbridge.enforcement.RuntimeContentKeys.CraftingRecord;

import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

public final class CraftingRecipeEnforcer {

	private CraftingRecipeEnforcer() { }

	@SuppressWarnings("unchecked")
	public static EnforcementAdapterResult apply(ContentEnforcementPolicy policy,
			boolean mutate) {
		EnforcementAdapterResult result = new EnforcementAdapterResult(
				"crafting", ReloadClassification.RESTART_REQUIRED);
		result.covered("minecraft:crafting");
		if(!policy.isActive()) return result;

		Set<IRecipe> selected = Collections.newSetFromMap(
				new IdentityHashMap<IRecipe, Boolean>());
		for(CraftingRecord record : RuntimeContentKeys.craftingRecords()) {
			result.examined();
			String matched = firstDisabled(policy, record.recipeKey, record.outputKey);
			if(matched.isEmpty()) continue;
			result.planned(matched);
			selected.add(record.recipe);
		}
		if(!mutate || selected.isEmpty()) return result;

		List<Object> recipes = (List<Object>) CraftingManager.getInstance().getRecipeList();
		for(Iterator<Object> iterator = recipes.iterator(); iterator.hasNext();) {
			Object recipe = iterator.next();
			if(recipe instanceof IRecipe && selected.contains(recipe)) {
				iterator.remove();
				result.mutated();
			}
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
