package com.hbm.wocbridge.enforcement;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;

public final class BlockPlacementEnforcer {

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlace(BlockEvent.PlaceEvent event) {
		if(event.world == null || event.world.isRemote) return;
		ContentEnforcementPolicy policy = ContentEnforcementManager.getPolicy();
		if(!policy.isActive() || !ContentEnforcementManager.isBlockPlacementEnabled()) return;

		String matched = disabledItemKey(policy, event.itemInHand);
		if(matched.isEmpty() && event instanceof BlockEvent.MultiPlaceEvent) {
			for(BlockSnapshot snapshot
					: ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
				Block block = event.world.getBlock(snapshot.x, snapshot.y, snapshot.z);
				int metadata = event.world.getBlockMetadata(
						snapshot.x, snapshot.y, snapshot.z);
				matched = disabledBlockKey(policy, block, metadata);
				if(!matched.isEmpty()) break;
			}
		}
		if(matched.isEmpty()) {
			Block block = event.world.getBlock(event.x, event.y, event.z);
			int metadata = event.world.getBlockMetadata(event.x, event.y, event.z);
			matched = disabledBlockKey(policy, block, metadata);
		}
		if(matched.isEmpty()) return;

		event.setCanceled(true);
		ContentEnforcementAudit.recordDenial("blockPlacement", matched);
		send(event.player);
	}

	private static String disabledItemKey(ContentEnforcementPolicy policy, ItemStack stack) {
		String key = RuntimeContentKeys.itemKey(stack);
		return policy.isDisabled(key) ? key : "";
	}

	private static String disabledBlockKey(ContentEnforcementPolicy policy, Block block,
			int metadata) {
		String key = RuntimeContentKeys.blockKey(block, metadata);
		return policy.isDisabled(key) ? key : "";
	}

	private static void send(EntityPlayer player) {
		if(player != null) {
			player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
					+ "This block is disabled by the active WOC content profile."));
		}
	}
}
