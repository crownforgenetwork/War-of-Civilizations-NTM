package com.hbm.wocbridge.progression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.hbm.wocbridge.progression.ProgressionAdapters.HandlerSpec;
import com.hbm.wocbridge.progression.ProgressionAdapters.Shape;
import com.hbm.wocbridge.progression.ProgressionExporter.RuntimeSnapshot;
import com.hbm.wocbridge.taxonomy.TaxonomyIo;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;

/**
 * Small structural fixtures runnable in an initialized server environment.
 */
public final class ProgressionExporterFixtures {

	private ProgressionExporterFixtures() { }

	public static FixtureSummary run() throws IOException {
		RuntimeSnapshot before = ProgressionExporter.snapshotForFixture();
		List<String> passed = new ArrayList<String>();
		testAchievementIconsAndParent(passed);
		testTypedComponents(passed);
		testAlternativesCatalystsContainersAndChance(passed);
		testMachineAssociations(passed);
		testEnergyContracts(passed);
		testDynamicHandlers(passed);
		testDuplicateIdentity(passed);
		testStableSortingCsvAndUnicode(passed);
		testAtomicReplacement(passed);
		RuntimeSnapshot after = ProgressionExporter.snapshotForFixture();
		require(before.equals(after),
				"Fixture execution changed runtime registries: "
						+ before.describeDifference(after));
		passed.add("no-registry-mutation");
		return new FixtureSummary(passed);
	}

	private static void testAchievementIconsAndParent(List<String> passed) {
		String[] exact = ProgressionExporter.resolveIconForFixture(
				new ItemStack(Items.iron_ingot, 1, 0));
		require("ITEM".equals(exact[0]), "Expected exact item icon kind.");
		require("minecraft:iron_ingot".equals(exact[1]),
				"Expected canonical iron-ingot icon registry ID.");
		require("true".equals(exact[3]), "Expected exact icon resolution.");
		String[] unresolved = ProgressionExporter.resolveIconForFixture(null);
		require("false".equals(unresolved[3])
				&& !unresolved[4].isEmpty(),
				"Expected explicit unresolved icon reason.");

		Achievement parent = new Achievement("fixture.parent", "fixtureParent",
				0, 0, Items.iron_ingot, null);
		Achievement child = new Achievement("fixture.child", "fixtureChild",
				1, 0, Items.gold_ingot, parent);
		require("fixture.parent".equals(
				ProgressionExporter.parentIdForFixture(child)),
				"Parent achievement ID was not preserved.");
		passed.add("achievement-icon-exact");
		passed.add("achievement-icon-unresolved");
		passed.add("achievement-parent");
	}

	private static void testTypedComponents(List<String> passed) {
		HandlerSpec spec = new HandlerSpec("FixtureTyped", "machine:fixture",
				"family:fixture", "", "", "EXACT", "",
				"", "", "", "")
				.field("itemInput", "INPUT", Shape.ASTACK)
				.field("fluidInput", "INPUT", Shape.FLUID_STACK)
				.field("oreInput", "INPUT", Shape.ASTACK)
				.field("itemOutput", "OUTPUT", Shape.ITEM_STACK)
				.field("fluidOutput", "OUTPUT", Shape.FLUID_STACK);
		JsonObject recipe = new JsonObject();
		recipe.add("itemInput", array("item", "minecraft:iron_ingot", 2, 3));
		recipe.add("fluidInput", array("water", 1000));
		recipe.add("oreInput", array("dict", "ingotSteel", 4));
		recipe.add("itemOutput", array("minecraft:gold_ingot", 2, 1));
		recipe.add("fluidOutput", array("steam", 500));
		List<String[]> rows = ProgressionExporter.parseFixtureComponents(
				"fixture:typed", spec, recipe);
		require(rows.size() == 5, "Expected five typed component rows.");
		require(has(rows, 3, "INPUT", 4, "ITEM", 5,
				"minecraft:iron_ingot"), "Exact item input was not normalized.");
		require(has(rows, 3, "INPUT", 4, "FLUID", 5, "water"),
				"Exact fluid input was not normalized.");
		require(has(rows, 4, "ORE_DICTIONARY", 5, "ingotSteel"),
				"Ore-dictionary input was not normalized.");
		require(has(rows, 3, "OUTPUT", 4, "ITEM", 5,
				"minecraft:gold_ingot"), "Exact item output was not normalized.");
		require(has(rows, 3, "OUTPUT", 4, "FLUID", 5, "steam"),
				"Exact fluid output was not normalized.");
		passed.add("typed-item-input-output");
		passed.add("typed-fluid-input-output");
		passed.add("ore-dictionary-input");
	}

	private static void testAlternativesCatalystsContainersAndChance(
			List<String> passed) {
		HandlerSpec spec = new HandlerSpec("FixtureRoles", "machine:fixture",
				"family:fixture", "", "", "EXACT", "",
				"", "", "", "")
				.field("outputs", "CHANCE_OUTPUT", Shape.GENERIC_OUTPUT_LIST)
				.field("catalyst", "CATALYST", Shape.ASTACK, "false", "false")
				.field("container", "CONTAINER_OUTPUT", Shape.ITEM_STACK,
						"", "true");
		JsonObject recipe = new JsonObject();
		JsonArray outputs = new JsonArray();
		JsonArray multi = new JsonArray();
		multi.add(new JsonPrimitive("multi"));
		multi.add(genericSingle("minecraft:iron_ingot", 1.0F));
		multi.add(genericSingle("minecraft:gold_ingot", 0.5F));
		outputs.add(multi);
		recipe.add("outputs", outputs);
		recipe.add("catalyst", array("item", "minecraft:diamond", 1));
		recipe.add("container", array("minecraft:bucket", 1));
		List<String[]> rows = ProgressionExporter.parseFixtureComponents(
				"fixture:roles", spec, recipe);
		require(count(rows, 4, "ALTERNATIVE_GROUP") == 2,
				"Expected two members in one alternative group.");
		String group = first(rows, 10, 4, "ALTERNATIVE_GROUP");
		require(!group.isEmpty() && count(rows, 10, group) == 2,
				"Alternative group identity was not stable.");
		require(has(rows, 3, "CATALYST", 11, "false"),
				"Non-consumed catalyst contract was not preserved.");
		require(has(rows, 3, "CONTAINER_OUTPUT", 12, "true"),
				"Returned-container contract was not preserved.");
		require(has(rows, 3, "CHANCE_OUTPUT", 9, "0.5"),
				"Explicit chance output was not preserved.");
		passed.add("alternative-group");
		passed.add("catalyst-non-consumed");
		passed.add("container-return");
		passed.add("chance-output");
	}

	private static void testMachineAssociations(List<String> passed) {
		HandlerSpec exact = ProgressionAdapters.forHandlerName("PressRecipes");
		HandlerSpec partial = ProgressionAdapters.forHandlerName(
				"ParticleAcceleratorRecipes");
		require(exact != null && "EXACT".equals(exact.associationConfidence)
				&& !exact.blockRegistryId.isEmpty(),
				"Exact machine association fixture is missing.");
		require(partial != null
				&& "PARTIAL".equals(partial.associationConfidence)
				&& partial.blockRegistryId.isEmpty()
				&& !partial.associationReason.isEmpty(),
				"Partial machine association fixture is missing.");
		passed.add("machine-association-exact");
		passed.add("machine-association-partial");
	}

	private static void testEnergyContracts(List<String> passed) {
		boolean[] consumer = ProgressionExporter.energyFlagsForFixture(
				FixtureConsumer.class);
		boolean[] producer = ProgressionExporter.energyFlagsForFixture(
				FixtureProducer.class);
		boolean[] unknown = ProgressionExporter.energyFlagsForFixture(
				FixtureUnknown.class);
		require(consumer[0] && !consumer[1],
				"Consumer energy contract classification failed.");
		require(!producer[0] && producer[1],
				"Producer energy contract classification failed.");
		require(!unknown[0] && !unknown[1],
				"Unknown energy contract classification failed.");
		passed.add("energy-consumer");
		passed.add("energy-producer");
		passed.add("energy-unknown");
	}

	private static void testDynamicHandlers(List<String> passed) {
		boolean supported = false;
		boolean unsupported = false;
		for(ProgressionAdapters.DynamicHandlerSpec spec
				: ProgressionAdapters.dynamicHandlers()) {
			if("SUPPORTED_PARTIAL".equals(spec.status)) supported = true;
			if("UNSUPPORTED".equals(spec.status)
					&& !spec.futureWork.isEmpty()) unsupported = true;
		}
		require(supported, "Supported dynamic-handler fixture is missing.");
		require(unsupported, "Unsupported dynamic-handler fixture is missing.");
		passed.add("dynamic-handler-supported");
		passed.add("dynamic-handler-unsupported");
	}

	private static void testDuplicateIdentity(List<String> passed)
			throws IOException {
		JsonObject recipe = new JsonObject();
		recipe.add("output", array("minecraft:iron_ingot"));
		String id = ProgressionExporter.recipeIdForFixture(
				"hbm:fixture", "fixture", recipe);
		boolean rejected = false;
		try {
			ProgressionExporter.requireUniqueRecipeIdsForFixture(
					Arrays.asList(id, id));
		} catch(IOException expected) {
			rejected = true;
		}
		require(rejected, "Duplicate normalized recipe ID was not rejected.");
		passed.add("duplicate-recipe-identity");
	}

	private static void testStableSortingCsvAndUnicode(List<String> passed) {
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] {"schema", "z"});
		rows.add(new String[] {"schema", "a"});
		ProgressionExporter.sortRowsForFixture(rows);
		require("a".equals(rows.get(0)[1]), "Stable row ordering failed.");
		require("\"comma,\"\"quote\"\"\"".equals(
				ProgressionExporter.csvForFixture("comma,\"quote\"")),
				"CSV escaping failed.");
		String unicode = "Schr\u00f6dinger \u03b1";
		require(unicode.equals(ProgressionExporter.csvForFixture(unicode)),
				"Unicode CSV value changed.");
		JsonObject left = new JsonObject();
		left.addProperty("b", 2);
		left.addProperty("a", 1);
		JsonObject right = new JsonObject();
		right.addProperty("a", 1);
		right.addProperty("b", 2);
		require(TaxonomyIo.canonicalJson(left).equals(
				TaxonomyIo.canonicalJson(right)),
				"Canonical JSON ordering failed.");
		passed.add("stable-sorting");
		passed.add("csv-escaping");
		passed.add("unicode");
	}

	private static void testAtomicReplacement(List<String> passed)
			throws IOException {
		File repository = ProgressionExporter.repositoryRootForFixture();
		File fixtureRoot = new File(repository,
				"build/reports/woc/.progression-fixtures");
		if(fixtureRoot.exists()) ProgressionExporter.deleteTree(fixtureRoot.toPath());
		Files.createDirectories(fixtureRoot.toPath());
		File destination = new File(fixtureRoot, "published");
		File marker = new File(destination, "marker.txt");
		Files.createDirectories(destination.toPath());
		Files.write(marker.toPath(), "prior\n".getBytes(StandardCharsets.UTF_8));

		File failedStage = new File(fixtureRoot, "failed-stage");
		Files.createDirectories(failedStage.toPath());
		Files.write(new File(failedStage, "marker.txt").toPath(),
				"partial\n".getBytes(StandardCharsets.UTF_8));
		boolean rejected = false;
		try {
			ProgressionExporter.replaceDirectory(
					failedStage, destination, true);
		} catch(IOException expected) {
			rejected = true;
		}
		require(rejected, "Injected publication failure was not propagated.");
		require("prior\n".equals(read(marker)),
				"Failed export did not preserve the prior report set.");

		File successfulStage = new File(fixtureRoot, "successful-stage");
		Files.createDirectories(successfulStage.toPath());
		Files.write(new File(successfulStage, "marker.txt").toPath(),
				"replacement\n".getBytes(StandardCharsets.UTF_8));
		ProgressionExporter.replaceDirectory(
				successfulStage, destination, false);
		require("replacement\n".equals(read(new File(destination, "marker.txt"))),
				"Atomic report replacement did not publish the staged set.");
		ProgressionExporter.deleteTree(fixtureRoot.toPath());
		passed.add("atomic-output-replacement");
		passed.add("failed-export-preserves-prior");
	}

	private static String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()),
				StandardCharsets.UTF_8);
	}

	private static JsonArray genericSingle(String item, float chance) {
		JsonArray single = new JsonArray();
		single.add(new JsonPrimitive("single"));
		single.add(array(item));
		single.add(new JsonPrimitive(chance));
		return single;
	}

	private static JsonArray array(Object... values) {
		JsonArray result = new JsonArray();
		for(Object value : values) {
			if(value instanceof String) result.add(new JsonPrimitive((String) value));
			else if(value instanceof Integer) result.add(new JsonPrimitive((Integer) value));
			else if(value instanceof Float) result.add(new JsonPrimitive((Float) value));
			else if(value instanceof JsonArray) result.add((JsonArray) value);
			else throw new IllegalArgumentException("Unsupported fixture value " + value);
		}
		return result;
	}

	private static boolean has(List<String[]> rows, Object... pairs) {
		for(String[] row : rows) {
			boolean matches = true;
			for(int index = 0; index < pairs.length; index += 2) {
				int column = (Integer) pairs[index];
				String value = (String) pairs[index + 1];
				if(!value.equals(row[column])) {
					matches = false;
					break;
				}
			}
			if(matches) return true;
		}
		return false;
	}

	private static int count(List<String[]> rows, int column, String value) {
		int count = 0;
		for(String[] row : rows) if(value.equals(row[column])) count++;
		return count;
	}

	private static String first(List<String[]> rows, int resultColumn,
			int matchColumn, String matchValue) {
		for(String[] row : rows) {
			if(matchValue.equals(row[matchColumn])) return row[resultColumn];
		}
		return "";
	}

	private static void require(boolean condition, String message) {
		if(!condition) throw new IllegalStateException(message);
	}

	private abstract static class FixtureEnergy {
		private long power;
		@SuppressWarnings("unused")
		private boolean loaded = true;

		public long getPower() { return power; }
		public void setPower(long power) { this.power = power; }
		public long getMaxPower() { return 100; }
		public boolean isLoaded() { return true; }
	}

	private static final class FixtureConsumer extends FixtureEnergy
			implements IEnergyReceiverMK2 { }

	private static final class FixtureProducer extends FixtureEnergy
			implements IEnergyProviderMK2 { }

	private static final class FixtureUnknown extends FixtureEnergy { }

	public static final class FixtureSummary {
		private final List<String> passed;

		FixtureSummary(List<String> passed) {
			this.passed = new ArrayList<String>(passed);
		}

		public int getPassedCount() { return passed.size(); }
		public List<String> getPassed() {
			return new ArrayList<String>(passed);
		}
	}
}
