package com.hbm.wocbridge.command;

import java.io.File;
import java.util.List;

import com.hbm.wocbridge.config.WocDevelopmentConfig;
import com.hbm.wocbridge.content.ContentKey;
import com.hbm.wocbridge.content.ContentProfileManager;
import com.hbm.wocbridge.content.ContentProfileManager.ToolingValidation;
import com.hbm.wocbridge.enforcement.ContentEnforcementManager;
import com.hbm.wocbridge.enforcement.EnforcementAdapterResult;
import com.hbm.wocbridge.taxonomy.ContentTaxonomyAudit;
import com.hbm.wocbridge.taxonomy.ContentTaxonomyFamily;
import com.hbm.wocbridge.taxonomy.ContentTaxonomyResult;
import com.hbm.wocbridge.taxonomy.ContentTaxonomyService;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public final class WocTaxonomyCommands {

	private WocTaxonomyCommands() { }

	public static boolean process(ICommandSender sender, String[] args) {
		if(args.length < 2 || !"taxonomy".equals(args[0])) return false;
		if(!WocDevelopmentConfig.enableContentTaxonomyTools) {
			send(sender, EnumChatFormatting.RED
					+ "WOC taxonomy tools are disabled in hbm.cfg.");
			return true;
		}
		if(args.length == 2 && "status".equals(args[1])) {
			reportRun(sender, ContentTaxonomyService.validate(), "status");
			return true;
		}
		if(args.length == 2 && "validate".equals(args[1])) {
			reportRun(sender, ContentTaxonomyService.validate(), "validation");
			return true;
		}
		if(args.length == 2 && "report".equals(args[1])) {
			ContentTaxonomyService.Run run = ContentTaxonomyService.report();
			reportRun(sender, run, "report");
			if(run.isValid()) {
				send(sender, EnumChatFormatting.GRAY + "Reports: "
						+ run.getReportDirectory().getAbsolutePath());
			}
			return true;
		}
		if(args.length == 2 && "generate-profiles".equals(args[1])) {
			generateProfiles(sender);
			return true;
		}
		if(args.length == 2 && "dry-run-firearms".equals(args[1])) {
			dryRunFirearms(sender);
			return true;
		}
		if(args.length == 2 && "unresolved".equals(args[1])) {
			unresolved(sender);
			return true;
		}
		if(args.length == 3 && "explain".equals(args[1])) {
			explain(sender, args[2]);
			return true;
		}
		if(args.length == 3 && "family".equals(args[1])) {
			family(sender, args[2]);
			return true;
		}
		return false;
	}

	private static void reportRun(ICommandSender sender,
			ContentTaxonomyService.Run run, String operation) {
		if(!run.isValid()) {
			send(sender, EnumChatFormatting.RED + "WOC taxonomy " + operation
					+ " failed: errors=" + run.getErrors().size() + ".");
			for(int index = 0; index < Math.min(5, run.getErrors().size()); index++) {
				send(sender, EnumChatFormatting.RED + run.getErrors().get(index));
			}
			return;
		}
		ContentTaxonomyAudit.Summary summary = run.getSummary();
		send(sender, EnumChatFormatting.GREEN + "WOC taxonomy " + operation
				+ " complete: total=" + summary.getTotal()
				+ " reviewedMembers=" + summary.getReviewed()
				+ " ambiguousMembers=" + summary.getAmbiguous()
				+ " conflictedMembers=" + summary.getConflicted() + ".");
		send(sender, EnumChatFormatting.GRAY + "Taxonomy="
				+ run.getTaxonomy().getTaxonomyId()
				+ " checksum=" + run.getTaxonomyChecksum());
		send(sender, EnumChatFormatting.GRAY + "Inventory checksum="
				+ run.getInventory().getChecksum()
				+ " fixtureFailures=" + run.getFixtureFailures().size());
	}

	private static void generateProfiles(ICommandSender sender) {
		ContentTaxonomyService.Run run = ContentTaxonomyService.generateProfiles();
		reportRun(sender, run, "profile generation");
		if(!run.isValid()) return;
		File examples = new File(ContentTaxonomyService.repositoryRoot(),
				"docs" + File.separator + "woc" + File.separator + "examples");
		File woc = new File(examples, "content_profile.woc_server.draft.json");
		File firearms = new File(examples,
				"content_profile.no_hbm_conventional_firearms.draft.json");
		ToolingValidation wocValidation =
				ContentProfileManager.validateExternalProfile(woc, false);
		ToolingValidation firearmValidation =
				ContentProfileManager.validateExternalProfile(firearms, true);
		send(sender, (wocValidation.isAccepted()
				? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
				+ "WOC server draft Phase 2 validation accepted="
				+ wocValidation.isAccepted() + " errors="
				+ wocValidation.getValidation().count(
						com.hbm.wocbridge.content.ProfileValidationIssue.Severity.ERROR));
		send(sender, (firearmValidation.isAccepted()
				? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
				+ "No-conventional-firearms draft strict validation accepted="
				+ firearmValidation.isAccepted() + " exactDisabled="
				+ run.getFirearmsDraft().getExactKeys().size()
				+ " completeness=" + run.getFirearmsDraft().getCompleteness());
		send(sender, EnumChatFormatting.YELLOW
				+ "Drafts were documented only; no runtime profile was installed.");
	}

	private static void dryRunFirearms(ICommandSender sender) {
		ContentTaxonomyService.Run run = ContentTaxonomyService.generateProfiles();
		if(!run.isValid()) {
			reportRun(sender, run, "firearms dry-run preparation");
			return;
		}
		File profile = new File(run.getReportDirectory(),
				"content_profile_no_hbm_conventional_firearms_draft.json");
		ToolingValidation validation =
				ContentProfileManager.validateExternalProfile(profile, true);
		if(!validation.isAccepted()) {
			send(sender, EnumChatFormatting.RED
					+ "No-conventional-firearms draft failed strict Phase 2 validation.");
			return;
		}
		int planned = 0;
		int mutated = 0;
		for(EnforcementAdapterResult result :
				ContentEnforcementManager.dryRunProfile(profile, true)) {
			planned += result.getPlanned();
			mutated += result.getMutated();
		}
		send(sender, (mutated == 0 ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
				+ "No-conventional-firearms Phase 3 dry-run: exactDisabled="
				+ run.getFirearmsDraft().getExactKeys().size()
				+ " plannedAdapterActions=" + planned
				+ " registryMutations=" + mutated + ".");
		send(sender, EnumChatFormatting.YELLOW
				+ "The draft was not installed and enforcement was not activated.");
	}

	private static void unresolved(ICommandSender sender) {
		ContentTaxonomyService.Run run = ContentTaxonomyService.validate();
		if(!run.isValid()) {
			reportRun(sender, run, "unresolved query");
			return;
		}
		int shown = 0;
		for(ContentTaxonomyResult result : run.getResults()) {
			if(!result.isAmbiguous()) continue;
			if(shown < 8) {
				send(sender, EnumChatFormatting.GRAY
						+ result.getEntry().getCanonicalKey()
						+ " families=" + result.getFamilyIds()
						+ " disposition=" + result.getDisposition());
			}
			shown++;
		}
		send(sender, EnumChatFormatting.YELLOW + "Ambiguous/unreviewed entries="
				+ shown + "; first " + Math.min(8, shown)
				+ " shown. Run taxonomy report for the complete list.");
	}

	private static void explain(ICommandSender sender, String key) {
		if("held".equals(key)) {
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
				key = ContentKey.fromItemStack(held).getValue();
			} catch(IllegalArgumentException ex) {
				send(sender, EnumChatFormatting.RED
						+ "Unable to identify held item: " + ex.getMessage());
				return;
			}
		}
		ContentTaxonomyService.Run run = ContentTaxonomyService.validate();
		if(!run.isValid()) {
			reportRun(sender, run, "explain");
			return;
		}
		ContentTaxonomyResult result = run.getResult(key);
		if(result == null) {
			send(sender, EnumChatFormatting.RED
					+ "Canonical key is not present in the current inventory.");
			return;
		}
		send(sender, EnumChatFormatting.GRAY + "key=" + key
				+ " families=" + result.getFamilyIds()
				+ " tags=" + result.getTags());
		send(sender, EnumChatFormatting.GRAY + "disposition="
				+ result.getDisposition() + " review=" + result.getReviewStatus()
				+ " rules=" + result.getProducingRules()
				+ " profileState=" + profileState(result.getDisposition()));
	}

	private static void family(ICommandSender sender, String familyId) {
		ContentTaxonomyService.Run run = ContentTaxonomyService.validate();
		if(!run.isValid()) {
			reportRun(sender, run, "family query");
			return;
		}
		ContentTaxonomyFamily selected = null;
		for(ContentTaxonomyFamily family : run.getTaxonomy().getFamilies()) {
			if(family.getFamilyId().equals(familyId)) selected = family;
		}
		if(selected == null) {
			send(sender, EnumChatFormatting.RED + "Unknown taxonomy family: " + familyId);
			return;
		}
		int count = 0;
		int reviewed = 0;
		int ambiguous = 0;
		int conflicted = 0;
		for(ContentTaxonomyResult result : run.getResults()) {
			if(result.getFamilyIds().contains(familyId)) {
				count++;
				if(result.isReviewed()) reviewed++;
				if(result.isAmbiguous()) ambiguous++;
				if(result.isConflicted()) conflicted++;
			}
		}
		send(sender, EnumChatFormatting.GRAY + "family=" + familyId
				+ " expandedMembers=" + count + " reviewedMembers=" + reviewed
				+ " ambiguousMembers=" + ambiguous
				+ " conflictedMembers=" + conflicted
				+ " disposition=" + selected.getDefaultDisposition()
				+ " definitionReview=" + selected.getReviewStatus());
		send(sender, EnumChatFormatting.GRAY + "tags=" + selected.getTags()
				+ " owner=" + selected.getFutureOwnerModule()
				+ " replacement=" + selected.getReplacementPolicy());
	}

	private static String profileState(String disposition) {
		if("future_research".equals(disposition)) return "RESEARCH_LOCKED";
		if("future_project".equals(disposition)) return "PROJECT_LOCKED";
		if("future_event".equals(disposition)) return "EVENT_ONLY";
		if("debug_disable".equals(disposition)) return "DISABLED";
		if("keep".equals(disposition)) return "AVAILABLE";
		return "UNREVIEWED";
	}

	private static void send(ICommandSender sender, String message) {
		sender.addChatMessage(new ChatComponentText(message));
	}
}
