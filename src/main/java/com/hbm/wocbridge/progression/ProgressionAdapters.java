package com.hbm.wocbridge.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reviewed HBM progression-export contracts. This is deliberately an explicit
 * allow-list: an unknown handler or field is reported, never guessed.
 */
final class ProgressionAdapters {

	static final String ADAPTER_VERSION = "hbm-progression-adapters-v1";

	enum Shape {
		ASTACK,
		ASTACK_LIST,
		ITEM_STACK,
		ITEM_STACK_LIST,
		CHANCE_ITEM_LIST,
		GENERIC_OUTPUT_LIST,
		FLUID_STACK,
		FLUID_STACK_LIST,
		MATERIAL_STACK_LIST,
		ITEM_IDENTIFIER,
		FLUID_IDENTIFIER,
		ORE_IDENTIFIER,
		TAG_IDENTIFIER,
		HANDLER_SPECIFIC
	}

	static final class FieldRule {
		final String path;
		final String role;
		final Shape shape;
		final String consumed;
		final String returnedContainer;

		FieldRule(String path, String role, Shape shape,
				String consumed, String returnedContainer) {
			this.path = path;
			this.role = role;
			this.shape = shape;
			this.consumed = consumed;
			this.returnedContainer = returnedContainer;
		}
	}

	static final class HandlerSpec {
		final String handlerClass;
		final String handlerId;
		final String machineId;
		final String familyId;
		final String blockRegistryId;
		final String tileEntityClass;
		final String associationConfidence;
		final String associationReason;
		final String durationPath;
		final String energyOperationPath;
		final String energyTickPath;
		final String requirements;
		final List<FieldRule> fields = new ArrayList<FieldRule>();

		HandlerSpec(String handlerClass, String machineId, String familyId,
				String blockRegistryId, String tileEntityClass,
				String associationConfidence, String associationReason,
				String durationPath, String energyOperationPath,
				String energyTickPath, String requirements) {
			this.handlerClass = handlerClass;
			this.handlerId = "hbm:" + handlerClass;
			this.machineId = machineId;
			this.familyId = familyId;
			this.blockRegistryId = blockRegistryId;
			this.tileEntityClass = tileEntityClass;
			this.associationConfidence = associationConfidence;
			this.associationReason = associationReason;
			this.durationPath = durationPath;
			this.energyOperationPath = energyOperationPath;
			this.energyTickPath = energyTickPath;
			this.requirements = requirements;
		}

		HandlerSpec field(String path, String role, Shape shape) {
			return field(path, role, shape, defaultConsumed(role), "");
		}

		HandlerSpec field(String path, String role, Shape shape,
				String consumed, String returnedContainer) {
			fields.add(new FieldRule(path, role, shape, consumed, returnedContainer));
			return this;
		}
	}

	static final class DynamicHandlerSpec {
		final String className;
		final String status;
		final String reason;
		final boolean safeIteration;
		final boolean outputsKnown;
		final boolean typedInputsKnown;
		final String futureWork;

		DynamicHandlerSpec(String className, String status, String reason,
				boolean safeIteration, boolean outputsKnown,
				boolean typedInputsKnown, String futureWork) {
			this.className = className;
			this.status = status;
			this.reason = reason;
			this.safeIteration = safeIteration;
			this.outputsKnown = outputsKnown;
			this.typedInputsKnown = typedInputsKnown;
			this.futureWork = futureWork;
		}
	}

	private static final Map<String, HandlerSpec> HANDLERS = createHandlers();
	private static final List<DynamicHandlerSpec> DYNAMIC = createDynamicHandlers();

	private ProgressionAdapters() { }

	static HandlerSpec forHandler(Class<?> handlerClass) {
		return HANDLERS.get(handlerClass.getSimpleName());
	}

	static HandlerSpec forHandlerName(String handlerClass) {
		return HANDLERS.get(handlerClass);
	}

	static List<HandlerSpec> handlers() {
		return Collections.unmodifiableList(new ArrayList<HandlerSpec>(HANDLERS.values()));
	}

	static List<DynamicHandlerSpec> dynamicHandlers() {
		return DYNAMIC;
	}

	private static Map<String, HandlerSpec> createHandlers() {
		Map<String, HandlerSpec> specs = new LinkedHashMap<String, HandlerSpec>();

		add(specs, machine("PressRecipes", "press", "press",
				"hbm:tile.machine_press", "com.hbm.tileentity.machine.TileEntityMachinePress")
				.field("input", "INPUT", Shape.ASTACK)
				.field("stamp", "TOOL", Shape.TAG_IDENTIFIER, "false", "false")
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("BlastFurnaceRecipes", "blast-furnace", "blast-furnace",
				"hbm:tile.machine_blast_furnace", "com.hbm.tileentity.machine.TileEntityMachineBlastFurnace")
				.field("input1", "INPUT", Shape.ASTACK)
				.field("input2", "INPUT", Shape.ASTACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("ShredderRecipes", "shredder", "shredder",
				"hbm:tile.machine_shredder", "com.hbm.tileentity.machine.TileEntityMachineShredder")
				.field("input", "INPUT", Shape.ITEM_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("SolderingRecipes", "soldering-station", "soldering",
				"hbm:tile.machine_soldering_station", "com.hbm.tileentity.machine.TileEntityMachineSolderingStation",
				"duration", "", "consumption")
				.field("toppings", "INPUT", Shape.ASTACK_LIST)
				.field("pcb", "INPUT", Shape.ASTACK_LIST)
				.field("solder", "INPUT", Shape.ASTACK_LIST)
				.field("fluid", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("CombinationRecipes", "combination-oven", "combination",
				"hbm:tile.furnace_combination", "com.hbm.tileentity.machine.TileEntityFurnaceCombination")
				.field("input", "INPUT", Shape.ASTACK)
				.field("fluid", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("CentrifugeRecipes", "centrifuge", "centrifuge",
				"hbm:tile.machine_centrifuge", "com.hbm.tileentity.machine.TileEntityMachineCentrifuge")
				.field("input", "INPUT", Shape.ASTACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK_LIST));
		add(specs, machine("CrystallizerRecipes", "crystallizer", "crystallizer",
				"hbm:tile.machine_crystallizer", "com.hbm.tileentity.machine.TileEntityMachineCrystallizer",
				"duration", "", "")
				.field("input", "INPUT", Shape.ASTACK)
				.field("fluid", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("RefineryRecipes", "refinery", "refinery",
				"hbm:tile.machine_refinery", "com.hbm.tileentity.machine.oil.TileEntityMachineRefinery")
				.field("input", "INPUT", Shape.FLUID_IDENTIFIER)
				.field("output0", "OUTPUT", Shape.FLUID_STACK)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK)
				.field("output3", "OUTPUT", Shape.FLUID_STACK)
				.field("solid", "BYPRODUCT", Shape.ITEM_STACK));
		add(specs, machine("VacuumRefineryRecipes", "vacuum-distillation", "vacuum-refinery",
				"hbm:tile.machine_vacuum_distill", "com.hbm.tileentity.machine.oil.TileEntityMachineVacuumDistill")
				.field("input", "INPUT", Shape.FLUID_IDENTIFIER)
				.field("output0", "OUTPUT", Shape.FLUID_STACK)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK)
				.field("output3", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("FractionRecipes", "fraction-tower", "fractionation",
				"hbm:tile.machine_fraction_tower", "com.hbm.tileentity.machine.oil.TileEntityMachineFractionTower")
				.field("input", "INPUT", Shape.FLUID_IDENTIFIER)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("CrackingRecipes", "catalytic-cracker", "cracking",
				"hbm:tile.machine_catalytic_cracker", "com.hbm.tileentity.machine.oil.TileEntityMachineCatalyticCracker")
				.field("input", "INPUT", Shape.FLUID_IDENTIFIER)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("ReformingRecipes", "catalytic-reformer", "reforming",
				"hbm:tile.machine_catalytic_reformer", "com.hbm.tileentity.machine.oil.TileEntityMachineCatalyticReformer")
				.field("input", "INPUT", Shape.FLUID_IDENTIFIER)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK)
				.field("output3", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("HydrotreatingRecipes", "hydrotreater", "hydrotreating",
				"hbm:tile.machine_hydrotreater", "com.hbm.tileentity.machine.oil.TileEntityMachineHydrotreater")
				.field("input", "INPUT", Shape.FLUID_IDENTIFIER)
				.field("hydrogen", "INPUT", Shape.FLUID_STACK)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("LiquefactionRecipes", "liquefactor", "liquefaction",
				"hbm:tile.machine_liquefactor", "com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor")
				.field("input", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("SolidificationRecipes", "solidifier", "solidification",
				"hbm:tile.machine_solidifier", "com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier")
				.field("input", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("CokerRecipes", "coker", "coking",
				"hbm:tile.machine_coker", "com.hbm.tileentity.machine.oil.TileEntityMachineCoker")
				.field("input", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK)
				.field("byproduct", "BYPRODUCT", Shape.FLUID_STACK));
		add(specs, machine("PyroOvenRecipes", "pyrolysis-oven", "pyrolysis",
				"hbm:tile.machine_pyrooven", "com.hbm.tileentity.machine.oil.TileEntityMachinePyroOven",
				"duration", "", "")
				.field("inputFluid", "INPUT", Shape.FLUID_STACK)
				.field("inputItem", "INPUT", Shape.ASTACK)
				.field("outputFluid", "OUTPUT", Shape.FLUID_STACK)
				.field("outputItem", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("BreederRecipes", "breeding-reactor", "breeding",
				"hbm:tile.machine_reactor", "com.hbm.tileentity.machine.TileEntityMachineReactorBreeding",
				"", "", "", "flux")
				.field("input", "INPUT", Shape.ITEM_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("CyclotronRecipes", "cyclotron", "cyclotron",
				"hbm:tile.machine_cyclotron", "com.hbm.tileentity.machine.TileEntityMachineCyclotron",
				"", "", "", "particle")
				.field("particle", "INPUT", Shape.ITEM_STACK)
				.field("input", "INPUT", Shape.ASTACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, process("FuelPoolRecipes", "fuel-pool", "fuel-pool")
				.field("input", "INPUT", Shape.ITEM_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("MixerRecipes", "industrial-mixer", "mixer",
				"hbm:tile.machine_mixer", "com.hbm.tileentity.machine.TileEntityMachineMixer",
				"duration", "", "")
				.field("input1", "INPUT", Shape.FLUID_STACK)
				.field("input2", "INPUT", Shape.FLUID_STACK)
				.field("solidInput", "INPUT", Shape.ASTACK)
				.field("outputType", "OUTPUT", Shape.FLUID_IDENTIFIER));
		add(specs, machine("OutgasserRecipes", "rbmk-outgasser", "outgasser",
				"hbm:tile.rbmk_outgasser", "com.hbm.tileentity.machine.rbmk.TileEntityRBMKOutgasser")
				.field("input", "INPUT", Shape.ASTACK)
				.field("solidOutput", "OUTPUT", Shape.ITEM_STACK)
				.field("fluidOutput", "OUTPUT", Shape.MATERIAL_STACK_LIST));
		add(specs, machine("FluidBreederRecipes", "fusion-breeder", "fluid-breeder",
				"hbm:tile.fusion_breeder", "com.hbm.tileentity.machine.fusion.TileEntityFusionBreeder")
				.field("input", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("CompressorRecipes", "compressor", "compressor",
				"hbm:tile.machine_compressor", "com.hbm.tileentity.machine.TileEntityMachineCompressor")
				.field("input", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.FLUID_STACK));
		add(specs, machine("ElectrolyserFluidRecipes", "electrolyser", "electrolysis-fluid",
				"hbm:tile.machine_electrolyser", "com.hbm.tileentity.machine.TileEntityElectrolyser",
				"duration", "", "")
				.field("input", "INPUT", Shape.FLUID_STACK)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK)
				.field("byproducts", "BYPRODUCT", Shape.CHANCE_ITEM_LIST));
		add(specs, machine("ElectrolyserMetalRecipes", "electrolyser", "electrolysis-metal",
				"hbm:tile.machine_electrolyser", "com.hbm.tileentity.machine.TileEntityElectrolyser",
				"duration", "", "")
				.field("input", "INPUT", Shape.ASTACK)
				.field("output1", "OUTPUT", Shape.FLUID_STACK)
				.field("output2", "OUTPUT", Shape.FLUID_STACK)
				.field("byproducts", "BYPRODUCT", Shape.CHANCE_ITEM_LIST));
		add(specs, machine("ArcWelderRecipes", "arc-welder", "arc-welding",
				"hbm:tile.machine_arc_welder", "com.hbm.tileentity.machine.TileEntityMachineArcWelder",
				"duration", "", "consumption")
				.field("inputs", "INPUT", Shape.ASTACK_LIST)
				.field("fluid", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("RotaryFurnaceRecipes", "rotary-furnace", "rotary-furnace",
				"hbm:tile.machine_rotary_furnace", "com.hbm.tileentity.machine.TileEntityMachineRotaryFurnace",
				"duration", "", "")
				.field("inputs", "INPUT", Shape.ASTACK_LIST)
				.field("fluid", "INPUT", Shape.FLUID_STACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("ExposureChamberRecipes", "exposure-chamber", "exposure",
				"hbm:tile.machine_exposure_chamber", "com.hbm.tileentity.machine.TileEntityMachineExposureChamber",
				"", "", "", "particle")
				.field("ingredient", "INPUT", Shape.ASTACK)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, process("ParticleAcceleratorRecipes", "particle-accelerator", "particle-accelerator",
				"Physical accelerator assembly is multiblock and has no single reviewed block association.")
				.field("inputs", "INPUT", Shape.ASTACK_LIST)
				.field("outputs", "OUTPUT", Shape.ITEM_STACK_LIST));
		add(specs, machine("AmmoPressRecipes", "ammo-press", "ammo-press",
				"hbm:tile.machine_ammo_press", "com.hbm.tileentity.machine.TileEntityMachineAmmoPress")
				.field("input", "INPUT", Shape.ASTACK_LIST)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, process("AnvilRecipes", "ntm-anvil", "anvil",
				"One recipe handler is shared by multiple anvil tier block variants.",
				"", "", "", "tierLower;tierUpper;overlay")
				.field("inputs", "INPUT", Shape.ASTACK_LIST)
				.field("outputs", "CHANCE_OUTPUT", Shape.CHANCE_ITEM_LIST));
		add(specs, machine("PedestalRecipes", "pedestal", "pedestal",
				"hbm:tile.pedestal", "com.hbm.blocks.generic.BlockPedestal$TileEntityPedestal",
				"", "", "", "extra;set")
				.field("input", "INPUT", Shape.ASTACK_LIST)
				.field("output", "OUTPUT", Shape.ITEM_STACK));
		add(specs, machine("AnnihilatorRecipes", "annihilator", "annihilator",
				"hbm:tile.machine_annihilator", "com.hbm.tileentity.machine.TileEntityMachineAnnihilator",
				"", "", "", "milestones[].amount")
				.field("key.item", "INPUT", Shape.ITEM_IDENTIFIER)
				.field("key.fluid", "INPUT", Shape.FLUID_IDENTIFIER)
				.field("key.dict", "INPUT", Shape.ORE_IDENTIFIER)
				.field("milestones[].payout", "OUTPUT", Shape.ITEM_STACK));

		add(specs, genericMachine("CrucibleRecipes", "crucible", "crucible",
				"hbm:tile.machine_crucible", "com.hbm.tileentity.machine.TileEntityCrucible",
				"", "", "", "frequency"));
		add(specs, genericMachine("AssemblyMachineRecipes", "assembly-machine", "assembly",
				"hbm:tile.machine_assembly_machine", "com.hbm.tileentity.machine.TileEntityMachineAssemblyMachine",
				"duration", "", "power", ""));
		add(specs, genericMachine("ChemicalPlantRecipes", "chemical-plant", "chemical",
				"hbm:tile.machine_chemical_plant", "com.hbm.tileentity.machine.TileEntityMachineChemicalPlant",
				"duration", "", "power", ""));
		add(specs, genericMachine("PUREXRecipes", "purex", "purex",
				"hbm:tile.machine_purex", "com.hbm.tileentity.machine.TileEntityMachinePUREX",
				"duration", "", "power", ""));
		add(specs, genericProcess("FusionRecipes", "fusion-reactor", "fusion",
				"Fusion recipes are shared across a multiblock reactor rather than a single machine block.",
				"duration", "", "power", "ignitionTemp;outputTemp;outputFlux"));
		add(specs, genericMachine("PrecAssRecipes", "precision-assembler", "precision-assembly",
				"hbm:tile.machine_precass", "com.hbm.tileentity.machine.TileEntityMachinePrecAss",
				"duration", "", "power", ""));
		add(specs, genericMachine("PlasmaForgeRecipes", "plasma-forge", "plasma-forge",
				"hbm:tile.fusion_plasma_forge", "com.hbm.tileentity.machine.fusion.TileEntityFusionPlasmaForge",
				"duration", "", "power", "ignitionTemp"));
		add(specs, genericMachine("BlastFurnaceRecipesNT", "industrial-blast-furnace",
				"industrial-blast-furnace", "hbm:tile.machine_blast_furnace",
				"com.hbm.tileentity.machine.TileEntityMachineBlastFurnace",
				"duration", "", "power", ""));
		add(specs, process("MatDistribution", "material-distribution", "material-distribution",
				"Material distribution is a conversion registry, not one physical machine.")
				.field("input", "INPUT", Shape.MATERIAL_STACK_LIST)
				.field("output", "OUTPUT", Shape.MATERIAL_STACK_LIST));
		add(specs, process("CustomMachineRecipes", "custom-machine", "custom-machine",
				"Runtime recipe keys may be consumed by externally configured custom-machine definitions.",
				"duration", "", "consumptionPerTick",
				"pollutionType;pollutionAmount;radiationAmount;flux;heat")
				.field("inputItems", "INPUT", Shape.ASTACK_LIST)
				.field("inputFluids", "INPUT", Shape.FLUID_STACK_LIST)
				.field("outputItems", "CHANCE_OUTPUT", Shape.CHANCE_ITEM_LIST)
				.field("outputFluids", "OUTPUT", Shape.FLUID_STACK_LIST));
		add(specs, process("ArcFurnaceRecipes", "arc-furnace", "arc-furnace",
				"One recipe family is used by foundry/arc-furnace processing and lacks a one-to-one reviewed block association.")
				.field("input", "INPUT", Shape.ASTACK)
				.field("solid", "OUTPUT", Shape.ITEM_STACK)
				.field("fluid", "OUTPUT", Shape.MATERIAL_STACK_LIST));

		return Collections.unmodifiableMap(specs);
	}

	private static HandlerSpec genericMachine(String handler, String machine,
			String family, String block, String tile, String duration,
			String operation, String tick, String requirements) {
		return genericFields(machine(handler, machine, family, block, tile,
				duration, operation, tick, requirements));
	}

	private static HandlerSpec genericProcess(String handler, String machine,
			String family, String reason, String duration, String operation,
			String tick, String requirements) {
		return genericFields(process(handler, machine, family, reason,
				duration, operation, tick, requirements));
	}

	private static HandlerSpec genericFields(HandlerSpec spec) {
		return spec.field("inputItem", "INPUT", Shape.ASTACK_LIST)
				.field("inputFluid", "INPUT", Shape.FLUID_STACK_LIST)
				.field("outputItem", "CHANCE_OUTPUT", Shape.GENERIC_OUTPUT_LIST)
				.field("outputFluid", "OUTPUT", Shape.FLUID_STACK_LIST);
	}

	private static HandlerSpec machine(String handler, String machine,
			String family, String block, String tile) {
		return machine(handler, machine, family, block, tile, "", "", "", "");
	}

	private static HandlerSpec machine(String handler, String machine,
			String family, String block, String tile, String duration,
			String operation, String tick) {
		return machine(handler, machine, family, block, tile,
				duration, operation, tick, "");
	}

	private static HandlerSpec machine(String handler, String machine,
			String family, String block, String tile, String duration,
			String operation, String tick, String requirements) {
		return new HandlerSpec(handler, "machine:" + machine, "family:" + family,
				block, tile, "EXACT", "", duration, operation, tick, requirements);
	}

	private static HandlerSpec process(String handler, String machine,
			String family) {
		return process(handler, machine, family,
				"No reviewed one-to-one physical machine association.");
	}

	private static HandlerSpec process(String handler, String machine,
			String family, String reason) {
		return process(handler, machine, family, reason, "", "", "", "");
	}

	private static HandlerSpec process(String handler, String machine,
			String family, String reason, String duration, String operation,
			String tick, String requirements) {
		return new HandlerSpec(handler, "process:" + machine, "family:" + family,
				"", "", "PARTIAL", reason, duration, operation, tick, requirements);
	}

	private static void add(Map<String, HandlerSpec> specs, HandlerSpec spec) {
		if(specs.put(spec.handlerClass, spec) != null) {
			throw new IllegalStateException("Duplicate progression adapter " + spec.handlerClass);
		}
	}

	private static String defaultConsumed(String role) {
		if("INPUT".equals(role) || "CATALYST".equals(role)
				|| "CONTAINER_INPUT".equals(role)) return "true";
		if("TOOL".equals(role)) return "false";
		return "";
	}

	private static List<DynamicHandlerSpec> createDynamicHandlers() {
		List<DynamicHandlerSpec> specs = new ArrayList<DynamicHandlerSpec>();
		specs.add(new DynamicHandlerSpec(
				"com.hbm.crafting.handlers.CargoShellCraftingHandler",
				"SUPPORTED_PARTIAL",
				"The shell input and registered output are exact; the cargo slot accepts arbitrary non-container content.",
				true, true, false,
				"Model the arbitrary cargo payload as a reviewed tagged input contract."));
		specs.add(new DynamicHandlerSpec(
				"com.hbm.crafting.handlers.GrenadeCraftingHandler",
				"SUPPORTED_PARTIAL",
				"Input item families and output are exact, but metadata compatibility is runtime enum logic.",
				true, true, false,
				"Export reviewed shell/filling compatibility groups without creative-tab enumeration."));
		specs.add(new DynamicHandlerSpec(
				"com.hbm.crafting.handlers.MKUCraftingHandler",
				"UNSUPPORTED",
				"The input layout is derived from the world seed; export must not inspect or mutate a world.",
				true, true, false,
				"Define a world-independent public recipe contract if research ingestion requires it."));
		specs.add(new DynamicHandlerSpec(
				"com.hbm.crafting.handlers.ScrapsCraftingHandler",
				"SUPPORTED_PARTIAL",
				"The scraps item is exact, but material quantity and output NBT are computed from input NBT.",
				true, true, false,
				"Add a typed material-stack recipe descriptor independent of InventoryCrafting."));
		return Collections.unmodifiableList(specs);
	}
}
