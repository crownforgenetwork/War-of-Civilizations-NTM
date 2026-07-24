package com.hbm.wocbridge.command;

import java.io.File;

import com.hbm.wocbridge.content.ContentKey;
import com.hbm.wocbridge.content.ContentProfileManager;
import com.hbm.wocbridge.content.ContentProfileManager.ValidationRun;
import com.hbm.wocbridge.content.ProfileValidationIssue;
import com.hbm.wocbridge.content.ProfileValidationIssue.Severity;
import com.hbm.wocbridge.content.ProfileValidationResult;
import com.hbm.wocbridge.enforcement.ContentEnforcementManager;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public final class WocProfileCommands {

	private WocProfileCommands() { }

	public static boolean process(ICommandSender sender, String[] args) {
		if(args.length == 2 && "profile".equals(args[0])) {
			if("validate".equals(args[1])) {
				reportValidation(sender, ContentProfileManager.validateConfiguredProfile(), false);
				return true;
			}
			if("reload".equals(args[1])) {
				reportValidation(sender, ContentEnforcementManager.reloadProfile(), true);
				return true;
			}
			if("status".equals(args[1])) {
				reportStatus(sender);
				return true;
			}
		}

		if(args.length == 2 && "content".equals(args[0])) {
			if("held".equals(args[1])) {
				reportHeldContent(sender);
			} else {
				reportContent(sender, args[1]);
			}
			return true;
		}
		return false;
	}

	private static void reportValidation(ICommandSender sender, ValidationRun run,
			boolean reload) {
		ProfileValidationResult validation = run.getValidation();
		String operation = reload ? "reload" : "validation";
		EnumChatFormatting color = run.isAccepted()
				? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
		String outcome;
		if(run.isAccepted()) {
			outcome = reload ? "accepted and installed" : "accepted";
		} else if(run.isPreviousProfilePreserved()) {
			outcome = "rejected; previous profile preserved";
		} else if(run.isMissing()) {
			outcome = "profile missing; AVAILABLE fallback remains active";
		} else {
			outcome = "rejected";
		}
		send(sender, color + "WOC profile " + operation + ": " + outcome + ".");
		send(sender, EnumChatFormatting.GRAY + "Issues: "
				+ validation.count(Severity.INFO) + " info, "
				+ validation.count(Severity.WARNING) + " warning, "
				+ validation.count(Severity.ERROR) + " error.");
		File report = run.getReportFile();
		send(sender, EnumChatFormatting.GRAY + "Report: "
				+ (report == null ? "write failed; see server log" : report.getAbsolutePath()));
	}

	private static void reportStatus(ICommandSender sender) {
		send(sender, EnumChatFormatting.GREEN + "WOC profile: id="
				+ ContentProfileManager.getProfileId()
				+ " schema=" + ContentProfileManager.getSchemaVersion()
				+ " mode=" + ContentProfileManager.getModeName());
		send(sender, EnumChatFormatting.GRAY + "Rules="
				+ ContentProfileManager.getRuleCount()
				+ " tags=" + ContentProfileManager.getTagCount()
				+ " issues=" + ContentProfileManager.getValidationIssues().size());
		send(sender, EnumChatFormatting.GRAY + "Checksum="
				+ ContentProfileManager.getChecksum());
	}

	private static void reportHeldContent(ICommandSender sender) {
		if(!(sender instanceof EntityPlayer)) {
			send(sender, EnumChatFormatting.RED
					+ "The held-content form requires an in-game player.");
			return;
		}
		ItemStack held = ((EntityPlayer) sender).getHeldItem();
		if(held == null || held.getItem() == null) {
			send(sender, EnumChatFormatting.RED + "No item is currently held.");
			return;
		}
		try {
			reportContent(sender, ContentKey.fromItemStack(held));
		} catch(IllegalArgumentException ex) {
			send(sender, EnumChatFormatting.RED + "Unable to identify held item: "
					+ ex.getMessage());
		}
	}

	private static void reportContent(ICommandSender sender, String rawKey) {
		try {
			reportContent(sender, ContentKey.parse(rawKey));
		} catch(IllegalArgumentException ex) {
			send(sender, EnumChatFormatting.RED + "Malformed content key: " + ex.getMessage());
		}
	}

	private static void reportContent(ICommandSender sender, ContentKey key) {
		send(sender, EnumChatFormatting.GRAY + ContentProfileManager.explain(key));
		for(ProfileValidationIssue issue : ContentProfileManager.getValidationIssues(key)) {
			send(sender, issue.getSeverity() == Severity.ERROR
					? EnumChatFormatting.RED + issue.toString()
					: EnumChatFormatting.YELLOW + issue.toString());
		}
	}

	private static void send(ICommandSender sender, String message) {
		sender.addChatMessage(new ChatComponentText(message));
	}
}
