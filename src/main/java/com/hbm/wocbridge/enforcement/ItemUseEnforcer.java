package com.hbm.wocbridge.enforcement;

import java.util.Map;
import java.util.WeakHashMap;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

public final class ItemUseEnforcer {

	private static final long MESSAGE_COOLDOWN_TICKS = 40L;
	private static final Map<EntityPlayer, Long> LAST_MESSAGE_TICK =
			new WeakHashMap<EntityPlayer, Long>();

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(event.world == null || event.world.isRemote) return;
		if(event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
				&& event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
		deny(event.entityPlayer, event.entityPlayer.getHeldItem(), new Cancellation() {
			@Override
			public void cancel() {
				event.setCanceled(true);
			}
		});
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onUseStart(PlayerUseItemEvent.Start event) {
		if(event.entityPlayer == null || event.entityPlayer.worldObj == null
				|| event.entityPlayer.worldObj.isRemote) return;
		deny(event.entityPlayer, event.item, new Cancellation() {
			@Override
			public void cancel() {
				event.setCanceled(true);
				event.duration = 0;
			}
		});
	}

	private static void deny(EntityPlayer player, ItemStack stack, Cancellation cancellation) {
		if(player == null || stack == null || stack.getItem() == null) return;
		ContentEnforcementPolicy policy = ContentEnforcementManager.getPolicy();
		if(!policy.isActive() || !ContentEnforcementManager.isItemUseEnabled()) return;
		String key = RuntimeContentKeys.itemKey(stack);
		ContentEnforcementDecision decision = policy.decide(key);
		if(!decision.isDenied()) return;

		cancellation.cancel();
		ContentEnforcementAudit.recordDenial("itemUse", decision.getMatchedKey());
		sendThrottled(player, "This item is disabled by the active WOC content profile.");
	}

	private static void sendThrottled(EntityPlayer player, String message) {
		long now = player.worldObj.getTotalWorldTime();
		synchronized(LAST_MESSAGE_TICK) {
			Long previous = LAST_MESSAGE_TICK.get(player);
			if(previous != null && now - previous < MESSAGE_COOLDOWN_TICKS) return;
			LAST_MESSAGE_TICK.put(player, now);
		}
		player.addChatMessage(new ChatComponentText(
				EnumChatFormatting.RED + message));
	}

	private interface Cancellation {
		void cancel();
	}
}
