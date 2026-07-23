package com.hbm.wocbridge.content;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hbm.entity.ModEntityList;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.world.gen.nbt.NBTStructure;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public final class RegistryResolver {

	public enum Status {
		RESOLVED,
		MISSING,
		MALFORMED,
		NOT_RUNTIME_RESOLVABLE
	}

	public static final class Resolution {
		private final Status status;
		private final String message;

		private Resolution(Status status, String message) {
			this.status = status;
			this.message = message;
		}

		public Status getStatus() {
			return status;
		}

		public String getMessage() {
			return message;
		}
	}

	private final Set<String> fluidNames;
	private final Set<String> creativeTabNames;
	private final Set<String> structureNames;
	private final ConcurrentMap<ContentKey, Resolution> cache =
			new ConcurrentHashMap<ContentKey, Resolution>();

	public RegistryResolver() {
		this.fluidNames = collectFluidNames();
		this.creativeTabNames = collectCreativeTabNames();
		this.structureNames = Collections.unmodifiableSet(
				new HashSet<String>(NBTStructure.listStructures()));
	}

	public Resolution resolve(ContentKey key) {
		if(key == null) return new Resolution(Status.MALFORMED, "Content key is missing");
		Resolution cached = cache.get(key);
		if(cached != null) return cached;
		Resolution resolved = resolveUncached(key);
		Resolution previous = cache.putIfAbsent(key, resolved);
		return previous == null ? resolved : previous;
	}

	public Resolution resolve(String value, ContentKind kind) {
		try {
			return resolve(ContentKey.parse(value, kind));
		} catch(IllegalArgumentException ex) {
			return new Resolution(Status.MALFORMED, ex.getMessage());
		}
	}

	private Resolution resolveUncached(ContentKey key) {
		switch(key.getKind()) {
		case ITEM:
			return Item.itemRegistry.getObject(key.getRegistryName()) instanceof Item
					? resolved() : missing("Item registry entry was not found");
		case BLOCK:
			return Block.blockRegistry.getObject(key.getRegistryName()) instanceof Block
					? resolved() : missing("Block registry entry was not found");
		case ENTITY:
			String entityName = ModEntityList.getName(key.getMetadata());
			return key.getRegistryName().equals(entityName)
					? resolved() : missing("Entity name/id pair was not found");
		case FLUID:
			return fluidNames.contains(key.getRegistryName())
					? resolved() : missing("Fluid registry entry was not found");
		case CREATIVE_TAB:
			return creativeTabNames.contains(key.getRegistryName())
					? resolved() : missing("Creative tab was not found");
		case STRUCTURE:
			return structureNames.contains(key.getRegistryName())
					? resolved() : missing("Structure was not found");
		case WORLDGEN_FEATURE:
			return structureNames.contains(key.getRegistryName())
					? resolved() : missing("World-generation feature was not found");
		default:
			return new Resolution(Status.NOT_RUNTIME_RESOLVABLE,
					"This content kind has no stable runtime registry in Phase 2");
		}
	}

	private static Set<String> collectFluidNames() {
		Set<String> names = new HashSet<String>();
		FluidType[] fluids = Fluids.getAll();
		if(fluids != null) {
			for(FluidType fluid : fluids) {
				if(fluid != null) names.add(fluid.getName());
			}
		}
		return Collections.unmodifiableSet(names);
	}

	private static Set<String> collectCreativeTabNames() {
		Set<String> names = new HashSet<String>();
		for(CreativeTabs tab : CreativeTabs.creativeTabArray) {
			if(tab != null) names.add(tab.getTabLabel());
		}
		return Collections.unmodifiableSet(names);
	}

	private static Resolution resolved() {
		return new Resolution(Status.RESOLVED, "Resolved");
	}

	private static Resolution missing(String message) {
		return new Resolution(Status.MISSING, message);
	}
}
