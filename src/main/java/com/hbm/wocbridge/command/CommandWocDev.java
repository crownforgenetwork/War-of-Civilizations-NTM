package com.hbm.wocbridge.command;

import java.io.File;
import java.util.Collections;
import java.util.List;

import com.hbm.main.MainRegistry;
import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.debug.ContentExporter;
import com.hbm.wocbridge.debug.ContentExporter.ExportSummary;
import com.hbm.wocbridge.progression.ProgressionExporter;
import com.hbm.wocbridge.progression.ProgressionExporterFixtures;

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
		return "/wocdev <export-content|export-progression [fixtures]|profile ...|content ...|enforcement ...|taxonomy <status|validate|report|explain|family|unresolved|generate-profiles|dry-run-firearms>>";
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

		if(args.length >= 1 && "export-progression".equals(args[0])) {
			if(!WocDevelopmentConfig.enableProgressionExport) {
				sender.addChatMessage(new ChatComponentText(
						EnumChatFormatting.RED
								+ "WOC progression export is disabled in hbm.cfg."));
				return;
			}
			if(args.length == 2 && "fixtures".equals(args[1])) {
				try {
					ProgressionExporterFixtures.FixtureSummary summary =
							ProgressionExporterFixtures.run();
					sender.addChatMessage(new ChatComponentText(
							EnumChatFormatting.GREEN
									+ "WOC progression fixtures passed: "
									+ summary.getPassedCount()));
				} catch(Exception ex) {
					MainRegistry.logger.error("WOC progression fixtures failed", ex);
					sender.addChatMessage(new ChatComponentText(
							EnumChatFormatting.RED
									+ "WOC progression fixtures failed. See the server log."));
				}
				return;
			}
			if(args.length == 1) {
				sender.addChatMessage(new ChatComponentText(
						EnumChatFormatting.YELLOW
								+ "Exporting read-only HBM progression evidence..."));
				try {
					ProgressionExporter.ExportSummary summary =
							ProgressionExporter.export();
					sender.addChatMessage(new ChatComponentText(
							EnumChatFormatting.GREEN
									+ "WOC progression export complete: "
									+ summary.getRecipeCount() + " recipes, "
									+ summary.getComponentCount() + " components in "
									+ summary.getOutputDirectory().getAbsolutePath()));
				} catch(Exception ex) {
					MainRegistry.logger.error("WOC progression export failed", ex);
					sender.addChatMessage(new ChatComponentText(
							EnumChatFormatting.RED
									+ "WOC progression export failed. See the server log."));
				}
				return;
			}
			throw new WrongUsageException(getCommandUsage(sender), new Object[0]);
		}

		if(WocProfileCommands.process(sender, args)) return;
		if(WocEnforcementCommands.process(sender, args)) return;
		if(WocTaxonomyCommands.process(sender, args)) return;
		throw new WrongUsageException(getCommandUsage(sender), new Object[0]);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
		if(args.length == 1) {
			return getListOfStringsMatchingLastWord(args,
					"export-content", "export-progression", "profile", "content",
					"enforcement", "taxonomy");
		}
		if(args.length == 2 && "export-progression".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args, "fixtures");
		}
		if(args.length == 2 && "profile".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args, "validate", "reload", "status");
		}
		if(args.length == 2 && "content".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args, "held");
		}
		if(args.length == 2 && "enforcement".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args,
					"status", "audit", "reload", "dry-run", "explain");
		}
		if(args.length == 3 && "enforcement".equals(args[0])
				&& "explain".equals(args[1])) {
			return getListOfStringsMatchingLastWord(args, "held");
		}
		if(args.length == 2 && "taxonomy".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args, "status", "validate", "report",
					"explain", "family", "unresolved", "generate-profiles",
					"dry-run-firearms");
		}
		if(args.length == 3 && "taxonomy".equals(args[0])
				&& "explain".equals(args[1])) {
			return getListOfStringsMatchingLastWord(args, "held");
		}
		return Collections.emptyList();
	}
}
