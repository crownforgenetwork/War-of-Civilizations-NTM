package com.hbm.wocbridge.command;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.hbm.main.MainRegistry;
import com.hbm.wocbridge.content.ContentKey;
import com.hbm.wocbridge.content.ContentProfileManager.ValidationRun;
import com.hbm.wocbridge.enforcement.ContentEnforcementAudit;
import com.hbm.wocbridge.enforcement.ContentEnforcementFixtures;
import com.hbm.wocbridge.enforcement.ContentEnforcementManager;
import com.hbm.wocbridge.enforcement.ContentEnforcementPolicy;
import com.hbm.wocbridge.enforcement.EnforcementAdapterResult;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public final class WocEnforcementCommands {

	private WocEnforcementCommands() { }

	public static boolean process(ICommandSender sender, String[] args) {
		if(args.length < 2 || !"enforcement".equals(args[0])) return false;
		if(args.length == 2 && "status".equals(args[1])) {
			reportStatus(sender);
			return true;
		}
		if(args.length == 2 && "audit".equals(args[1])) {
			writeAudit(sender);
			return true;
		}
		if(args.length == 2 && "reload".equals(args[1])) {
			reload(sender);
			return true;
		}
		if(args.length == 2 && "dry-run".equals(args[1])) {
			dryRun(sender);
			return true;
		}
		if(args.length == 3 && "explain".equals(args[1])) {
			explain(sender, args[2]);
			return true;
		}
		return false;
	}

	private static void reportStatus(ICommandSender sender) {
		ContentEnforcementPolicy policy = ContentEnforcementManager.getPolicy();
		send(sender, (policy.isActive() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
				+ "WOC DISABLED enforcement: "
				+ (policy.isActive() ? "ACTIVE" : "INACTIVE")
				+ (policy.isActive() ? "" : " (" + policy.getInactiveReason() + ")"));
		send(sender, EnumChatFormatting.GRAY + "Profile=" + policy.getProfileId()
				+ " disabledRules=" + policy.getDisabledKeys().size()
				+ " checksum=" + policy.getChecksum());
		send(sender, EnumChatFormatting.GRAY + "Audit="
				+ ContentEnforcementAudit.getAuditFile().getAbsolutePath());
	}

	private static void writeAudit(ICommandSender sender) {
		try {
			File audit = ContentEnforcementAudit.write();
			send(sender, EnumChatFormatting.GREEN + "WOC enforcement audit written: "
					+ audit.getAbsolutePath());
		} catch(IOException ex) {
			MainRegistry.logger.error("Unable to write WOC enforcement audit", ex);
			send(sender, EnumChatFormatting.RED
					+ "WOC enforcement audit write failed; see server log.");
		}
	}

	private static void reload(ICommandSender sender) {
		ValidationRun run = ContentEnforcementManager.reloadProfile();
		if(run.isAccepted()) {
			send(sender, EnumChatFormatting.GREEN
					+ "WOC profile accepted. Live item-use/placement policy updated; "
					+ "registry filters require restart.");
		} else {
			send(sender, EnumChatFormatting.RED + "WOC profile rejected; "
					+ (run.isPreviousProfilePreserved()
							? "previous policy preserved." : "fallback remains active."));
		}
	}

	private static void dryRun(ICommandSender sender) {
		List<String> failures = ContentEnforcementFixtures.run();
		int planned = 0;
		int mutated = 0;
		for(EnforcementAdapterResult result : ContentEnforcementManager.dryRun()) {
			planned += result.getPlanned();
			mutated += result.getMutated();
			send(sender, EnumChatFormatting.GRAY + result.getCategory()
					+ ": examined=" + result.getExamined()
					+ " planned=" + result.getPlanned()
					+ " mutations=" + result.getMutated()
					+ " reload=" + result.getReloadClassification());
		}
		send(sender, (failures.isEmpty() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
				+ "Dry-run complete: planned=" + planned + " mutations=" + mutated
				+ " fixtureFailures=" + failures.size() + ".");
		for(String failure : failures) {
			send(sender, EnumChatFormatting.RED + "Fixture failure: " + failure);
		}
	}

	private static void explain(ICommandSender sender, String value) {
		if("held".equals(value)) {
			if(!(sender instanceof EntityPlayer)) {
				send(sender, EnumChatFormatting.RED
						+ "The held form requires an in-game player.");
				return;
			}
			ItemStack held = ((EntityPlayer) sender).getHeldItem();
			if(held == null || held.getItem() == null) {
				send(sender, EnumChatFormatting.RED + "No item is currently held.");
				return;
			}
			try {
				value = ContentKey.fromItemStack(held).getValue();
			} catch(IllegalArgumentException ex) {
				send(sender, EnumChatFormatting.RED
						+ "Unable to identify held item: " + ex.getMessage());
				return;
			}
		}
		try {
			ContentKey.parse(value);
			send(sender, EnumChatFormatting.GRAY
					+ ContentEnforcementManager.explain(value));
		} catch(IllegalArgumentException ex) {
			send(sender, EnumChatFormatting.RED + "Malformed content key: "
					+ ex.getMessage());
		}
	}

	private static void send(ICommandSender sender, String message) {
		sender.addChatMessage(new ChatComponentText(message));
	}
}
