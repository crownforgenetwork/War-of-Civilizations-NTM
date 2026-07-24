package com.hbm.wocbridge.enforcement;

import com.hbm.main.MainRegistry;
import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.content.ContentKey;
import com.hbm.wocbridge.content.ContentKind;
import com.hbm.wocbridge.content.ContentProfileManager;

import codechicken.nei.api.API;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

@SideOnly(Side.CLIENT)
public final class WocNeiIntegration {

	private WocNeiIntegration() { }

	public static void hideDisabledVariants() {
		if(!WocDevelopmentConfig.enableContentEnforcement
				|| !WocDevelopmentConfig.hideDisabledFromNEI) return;

		ContentProfileManager.initialize();
		ContentEnforcementPolicy policy = ContentEnforcementPolicy.fromActiveProfile();
		if(!policy.isActive()) return;

		int hidden = 0;
		for(String value : policy.getDisabledKeys()) {
			try {
				ContentKey key = ContentKey.parse(value);
				if(key.getKind() == ContentKind.ITEM) {
					Object item = Item.itemRegistry.getObject(key.getRegistryName());
					if(item instanceof Item) {
						API.hideItem(new ItemStack((Item) item, 1, key.getMetadata()));
						hidden++;
					}
				} else if(key.getKind() == ContentKind.BLOCK) {
					Object block = Block.blockRegistry.getObject(key.getRegistryName());
					if(block instanceof Block) {
						API.hideItem(new ItemStack((Block) block, 1, key.getMetadata()));
						hidden++;
					}
				}
			} catch(IllegalArgumentException ex) {
				// Accepted profiles are validated; malformed entries are ignored defensively.
			}
		}
		MainRegistry.logger.info("WOC NEI enforcement hid {} exact disabled variants.", hidden);
	}
}
