package com.hbm.wocbridge.content;

public enum ContentKind {

	ITEM("item:", true),
	BLOCK("block:", true),
	ENTITY("entity:", true),
	FLUID("fluid:", true),
	CRAFTING_RECIPE("crafting-recipe:", false),
	SMELTING_RECIPE("smelting-recipe:", false),
	MACHINE_RECIPE("machine-recipe:", false),
	CREATIVE_TAB("creative-tab:", true),
	STRUCTURE("structure:", true),
	STRUCTURE_LOOT("loot:", false),
	LOOT_ENTRY("loot:", false),
	WORLDGEN_FEATURE("worldgen:", true);

	private final String keyPrefix;
	private final boolean runtimeResolvable;

	ContentKind(String keyPrefix, boolean runtimeResolvable) {
		this.keyPrefix = keyPrefix;
		this.runtimeResolvable = runtimeResolvable;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public boolean isRuntimeResolvable() {
		return runtimeResolvable;
	}

	public boolean matchesKey(String key) {
		return key != null && key.startsWith(keyPrefix);
	}

	public static ContentKind fromName(String value) {
		if(value == null) return null;
		try {
			return valueOf(value);
		} catch(IllegalArgumentException ex) {
			return null;
		}
	}

	public static ContentKind inferFromKey(String key) {
		if(key == null) return null;
		for(ContentKind kind : values()) {
			if(kind == LOOT_ENTRY) continue;
			if(kind.matchesKey(key)) return kind;
		}
		return null;
	}
}
