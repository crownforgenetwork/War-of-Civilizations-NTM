package com.hbm.wocbridge.enforcement;

import com.hbm.wocbridge.enforcement.EnforcementAdapterResult.ReloadClassification;
import com.hbm.world.gen.nbt.NBTStructure;
import com.hbm.world.gen.nbt.SpawnCondition;

public final class WorldgenEnforcer {

	private WorldgenEnforcer() { }

	public static EnforcementAdapterResult apply(ContentEnforcementPolicy policy,
			boolean mutate) {
		EnforcementAdapterResult result = new EnforcementAdapterResult(
				"worldgen", ReloadClassification.RESTART_REQUIRED);
		result.covered("hbm:NBTStructure weighted spawn registry");
		if(!policy.isActive()) return result;

		for(String name : NBTStructure.listStructures()) {
			SpawnCondition spawn = NBTStructure.getStructure(name);
			if(spawn == null) continue;
			result.examined();
			String key = "worldgen:" + name;
			if(spawn.checkCoordinates != null) {
				if(policy.isDisabled(key)) {
					result.unsupported(key,
							"coordinate-predicate structures bypass spawnWeight");
				}
				continue;
			}
			if(!policy.isDisabled(key)) continue;
			result.planned(key);
			if(mutate && spawn.spawnWeight != 0) {
				if(NBTStructure.disableWeightedStructure(name)) {
					result.mutated();
				} else {
					result.unsupported(key, "weighted registry rejected disable");
				}
			}
		}
		return result;
	}
}
