package com.hbm.wocbridge.command;

import java.io.File;
import java.util.Collections;
import java.util.List;

import com.hbm.main.MainRegistry;
import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.debug.ContentExporter;
import com.hbm.wocbridge.debug.ContentExporter.ExportSummary;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class CommandWocDev extends CommandBase {

	@Override
	public String getCommandName() {
		return "wocdev";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/wocdev <export-content|profile <validate|reload|status>|content <held|canonical-key>>";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 4;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if(!WocDevelopmentConfig.enableDevelopmentTools) {
			sender.addChatMessage(new ChatComponentText(
					EnumChatFormatting.RED + "WOC development tools are disabled in hbm.cfg."));
			return;
		}

		if(args.length == 1 && "export-content".equals(args[0])) {
			sender.addChatMessage(new ChatComponentText(
					EnumChatFormatting.YELLOW + "Exporting the live HBM content inventory..."));

			try {
				ExportSummary summary = ContentExporter.export();
				File outputDirectory = summary.getOutputDirectory();
				sender.addChatMessage(new ChatComponentText(
						EnumChatFormatting.GREEN + "WOC content export complete: "
								+ summary.getTotalRows() + " rows in "
								+ outputDirectory.getAbsolutePath()));
			} catch(Exception ex) {
				MainRegistry.logger.error("WOC content export failed", ex);
				sender.addChatMessage(new ChatComponentText(
						EnumChatFormatting.RED
								+ "WOC content export failed. See the server log for details."));
			}
			return;
		}

		if(WocProfileCommands.process(sender, args)) return;
		throw new WrongUsageException(getCommandUsage(sender), new Object[0]);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
		if(args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "export-content", "profile", "content");
		}
		if(args.length == 2 && "profile".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args, "validate", "reload", "status");
		}
		if(args.length == 2 && "content".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args, "held");
		}
		return Collections.emptyList();
	}
}
