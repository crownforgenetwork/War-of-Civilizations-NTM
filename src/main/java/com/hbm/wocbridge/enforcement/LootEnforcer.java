package com.hbm.wocbridge.enforcement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbm.itempool.ItemPool;
import com.hbm.wocbridge.enforcement.EnforcementAdapterResult.ReloadClassification;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

public final class LootEnforcer {

	private static final class LootRecord {
		private static final Comparator<LootRecord> ORDERING =
				new Comparator<LootRecord>() {
					@Override
					public int compare(LootRecord left, LootRecord right) {
						return left.baseKey.compareTo(right.baseKey);
					}
				};

		private final String poolName;
		private final ItemPool pool;
		private final WeightedRandomChestContent entry;
		private final String baseKey;
		private final String outputKey;
		private String exactKey;

		private LootRecord(String poolName, ItemPool pool,
				WeightedRandomChestContent entry) {
			this.poolName = poolName;
			this.pool = pool;
			this.entry = entry;
			ItemStack stack = entry.theItemId;
			this.baseKey = RuntimeContentKeys.lootKey(poolName, stack,
					entry.theMinimumChanceToGenerateItem,
					entry.theMaximumChanceToGenerateItem, entry.itemWeight);
			this.outputKey = RuntimeContentKeys.itemKey(stack);
		}
	}

	private LootEnforcer() { }

	public static EnforcementAdapterResult apply(ContentEnforcementPolicy policy,
			boolean mutate) {
		EnforcementAdapterResult result = new EnforcementAdapterResult(
				"loot", ReloadClassification.RESTART_REQUIRED);
		result.covered("hbm:ItemPool");
		result.unsupported("net.minecraftforge.common.ChestGenHooks",
				"outside HBM ItemPool");
		result.unsupported("com.hbm.lib.HbmChestContents",
				"legacy/bespoke tables outside ItemPool");
		if(!policy.isActive()) return result;

		List<LootRecord> records = collectRecords();
		Map<String, Integer> occurrences = new HashMap<String, Integer>();
		Map<ItemPool, List<WeightedRandomChestContent>> selected =
				new HashMap<ItemPool, List<WeightedRandomChestContent>>();
		for(LootRecord record : records) {
			result.examined();
			Integer previous = occurrences.get(record.baseKey);
			int occurrence = previous == null ? 1 : previous + 1;
			occurrences.put(record.baseKey, occurrence);
			record.exactKey = record.baseKey + ":" + occurrence;
			String matched = firstDisabled(policy, record.exactKey, record.outputKey);
			if(matched.isEmpty()) continue;
			result.planned(matched);
			List<WeightedRandomChestContent> entries = selected.get(record.pool);
			if(entries == null) {
				entries = new ArrayList<WeightedRandomChestContent>();
				selected.put(record.pool, entries);
			}
			entries.add(record.entry);
		}
		if(!mutate) return result;

		for(Map.Entry<ItemPool, List<WeightedRandomChestContent>> selection
				: selected.entrySet()) {
			ItemPool pool = selection.getKey();
			List<WeightedRandomChestContent> retained =
					new ArrayList<WeightedRandomChestContent>();
			for(WeightedRandomChestContent entry : pool.pool) {
				if(!containsIdentity(selection.getValue(), entry)) {
					retained.add(entry);
				} else {
					result.mutated();
				}
			}
			pool.pool = retained.toArray(
					new WeightedRandomChestContent[retained.size()]);
		}
		return result;
	}

	private static List<LootRecord> collectRecords() {
		List<String> poolNames = new ArrayList<String>(ItemPool.pools.keySet());
		Collections.sort(poolNames);
		List<LootRecord> records = new ArrayList<LootRecord>();
		for(String poolName : poolNames) {
			ItemPool pool = ItemPool.pools.get(poolName);
			if(pool == null || pool.pool == null) continue;
			for(WeightedRandomChestContent entry : pool.pool) {
				if(entry != null && entry.theItemId != null) {
					records.add(new LootRecord(poolName, pool, entry));
				}
			}
		}
		Collections.sort(records, LootRecord.ORDERING);
		return records;
	}

	private static boolean containsIdentity(List<WeightedRandomChestContent> entries,
			WeightedRandomChestContent target) {
		for(WeightedRandomChestContent entry : entries) {
			if(entry == target) return true;
		}
		return false;
	}

	private static String firstDisabled(ContentEnforcementPolicy policy, String lootKey,
			String outputKey) {
		if(policy.isDisabled(lootKey)) return lootKey;
		if(policy.isDisabled(outputKey)) return outputKey;
		return "";
	}
}
