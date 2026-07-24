package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ContentTaxonomyClassifier {

	private static final class Match {
		private final ContentTaxonomyFamily family;
		private final ContentTaxonomySelector selector;

		private Match(ContentTaxonomyFamily family, ContentTaxonomySelector selector) {
			this.family = family;
			this.selector = selector;
		}
	}

	private final ContentTaxonomy taxonomy;
	private final Map<String, ContentTaxonomyResult> resultIndex =
			new LinkedHashMap<String, ContentTaxonomyResult>();

	public ContentTaxonomyClassifier(ContentTaxonomy taxonomy) {
		this.taxonomy = taxonomy;
	}

	public List<ContentTaxonomyResult> classify(ContentInventory inventory) {
		List<ContentTaxonomyResult> results =
				new ArrayList<ContentTaxonomyResult>();
		resultIndex.clear();
		for(ContentInventory.Entry entry : inventory.getEntries()) {
			ContentTaxonomyResult result = classify(entry);
			results.add(result);
			resultIndex.put(entry.getCanonicalKey(), result);
		}
		return Collections.unmodifiableList(results);
	}

	public ContentTaxonomyResult get(String canonicalKey) {
		return resultIndex.get(canonicalKey);
	}

	private ContentTaxonomyResult classify(ContentInventory.Entry entry) {
		List<String> exclusions = new ArrayList<String>();
		List<Match> matches = new ArrayList<Match>();
		for(ContentTaxonomyFamily family : taxonomy.getFamilies()) {
			List<ContentTaxonomySelector> excludedBy =
					matching(family.getExcludeSelectors(), entry);
			if(!excludedBy.isEmpty()) {
				for(ContentTaxonomySelector selector : excludedBy) {
					exclusions.add(family.getFamilyId() + ":" + selector.getSelectorId());
				}
				continue;
			}
			for(ContentTaxonomySelector selector :
					matching(family.getIncludeSelectors(), entry)) {
				matches.add(new Match(family, selector));
			}
		}

		ContentTaxonomyRule override =
				taxonomy.getExactOverride(entry.getCanonicalKey());
		if(override != null) return fromOverride(entry, override, matches, exclusions);
		if(matches.isEmpty()) return unresolved(entry, exclusions);

		int precedence = 0;
		for(Match match : matches) {
			precedence = Math.max(precedence, match.selector.getPrecedence());
		}
		List<Match> winners = new ArrayList<Match>();
		for(Match match : matches) {
			if(match.selector.getPrecedence() == precedence) winners.add(match);
		}
		Collections.sort(winners, new Comparator<Match>() {
			@Override
			public int compare(Match left, Match right) {
				int family = left.family.getFamilyId().compareTo(right.family.getFamilyId());
				return family != 0 ? family
						: left.selector.getSelectorId().compareTo(right.selector.getSelectorId());
			}
		});

		Set<String> dispositions = new TreeSet<String>();
		Set<String> tags = new TreeSet<String>();
		Set<String> familyIds = new TreeSet<String>();
		List<String> producingRules = new ArrayList<String>();
		for(Match match : matches) {
			tags.addAll(match.family.getTags());
			familyIds.add(match.family.getFamilyId());
		}
		boolean allReviewed = true;
		for(Match match : winners) {
			dispositions.add(match.family.getDefaultDisposition());
			producingRules.add(match.family.getFamilyId() + ":"
					+ match.selector.getSelectorId());
			allReviewed &= "reviewed".equals(match.family.getReviewStatus());
		}
		boolean conflicted = dispositions.size() > 1;
		Match primary = winners.get(0);
		String disposition = conflicted ? "unresolved"
				: dispositions.iterator().next();
		String review = conflicted ? "unreviewed"
				: allReviewed ? "reviewed" : primary.family.getReviewStatus();
		boolean ambiguous = conflicted || !"reviewed".equals(review)
				|| "unresolved".equals(disposition);
		return new ContentTaxonomyResult(entry, new ArrayList<String>(familyIds),
				producingRules, exclusions,
				tags, disposition, primary.family.getResearchDomain(),
				primary.family.getResearchTier(), primary.family.getStrategicClass(),
				primary.family.getReplacementPolicy(), primary.family.getFutureOwnerModule(),
				primary.family.getStrategicSensitivity(), review,
				primary.family.getRationale(), true, ambiguous, conflicted, precedence);
	}

	private static ContentTaxonomyResult fromOverride(ContentInventory.Entry entry,
			ContentTaxonomyRule override, List<Match> matches, List<String> exclusions) {
		Set<String> tags = new TreeSet<String>(override.getTags());
		Set<String> matchedFamilies = new TreeSet<String>();
		for(Match match : matches) {
			tags.addAll(match.family.getTags());
			matchedFamilies.add(match.family.getFamilyId());
		}
		return new ContentTaxonomyResult(entry,
				new ArrayList<String>(matchedFamilies),
				Collections.singletonList("exact:" + override.getRuleId()), exclusions,
				tags, override.getDisposition(), override.getResearchDomain(),
				override.getResearchTier(), override.getStrategicClass(),
				override.getReplacementPolicy(), override.getFutureOwnerModule(),
				override.getStrategicSensitivity(), override.getReviewStatus(),
				override.getRationale(), true,
				!"reviewed".equals(override.getReviewStatus())
						|| "unresolved".equals(override.getDisposition()),
				false, 5);
	}

	private ContentTaxonomyResult unresolved(ContentInventory.Entry entry,
			List<String> exclusions) {
		String disposition = taxonomy.getDefaultDisposition().isEmpty()
				? "unresolved" : taxonomy.getDefaultDisposition();
		return new ContentTaxonomyResult(entry, Collections.<String>emptyList(),
				Collections.singletonList("taxonomy-default"), exclusions,
				Collections.<String>emptySet(), disposition, "", "",
				"administrative", "none", "unresolved", "unresolved",
				"unreviewed", "No reviewed selector matched this exported key.",
				false, true, false, 1);
	}

	private static List<ContentTaxonomySelector> matching(
			List<ContentTaxonomySelector> selectors, ContentInventory.Entry entry) {
		List<ContentTaxonomySelector> result =
				new ArrayList<ContentTaxonomySelector>();
		for(ContentTaxonomySelector selector : selectors) {
			if(selector.matches(entry)) result.add(selector);
		}
		return result;
	}
}
