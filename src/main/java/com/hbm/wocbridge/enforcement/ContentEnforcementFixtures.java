package com.hbm.wocbridge.enforcement;

import java.util.ArrayList;
import java.util.List;

public final class ContentEnforcementFixtures {

	private ContentEnforcementFixtures() { }

	public static List<String> run() {
		List<String> failures = new ArrayList<String>();
		String disabled = "item:hbm:fixture#2";
		ContentEnforcementPolicy active = ContentEnforcementPolicy.forFixture(
				true, "", disabled);
		assertDecision(failures, active, disabled, true,
				"exact DISABLED rule must deny");
		assertDecision(failures, active, "item:hbm:fixture#3", false,
				"metadata sibling must remain allowed");
		assertDecision(failures, active, "item:hbm:available#0", false,
				"non-DISABLED rule must remain allowed");
		assertDecision(failures, active, "item:hbm:research_locked#0", false,
				"research state must not be enforced in Phase 3");
		assertDecision(failures, active, "item:hbm:project_locked#0", false,
				"project state must not be enforced in Phase 3");

		ContentEnforcementPolicy inactive = ContentEnforcementPolicy.forFixture(
				false, "fallback", disabled);
		assertDecision(failures, inactive, disabled, false,
				"inactive/fallback policy must fail open");

		String first = active.getDisabledKeys().toString();
		String second = active.getDisabledKeys().toString();
		if(!first.equals(second)) failures.add("disabled-key ordering is not deterministic");
		return failures;
	}

	private static void assertDecision(List<String> failures,
			ContentEnforcementPolicy policy, String key, boolean expectedDenied,
			String message) {
		if(policy.decide(key).isDenied() != expectedDenied) failures.add(message);
	}
}
