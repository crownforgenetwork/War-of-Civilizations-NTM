# HBM progression adapter inventory

H1 uses an explicit allow-list in `ProgressionAdapters`. A handler is serialized
through HBM's public `SerializableRecipe.writeRecipe` contract; only
handler-reviewed paths are parsed. There is no arbitrary reflection crawler.

## Typed shapes

- HBM `AStack`: exact item/block, ore-dictionary, quantity, and metadata.
- HBM `ItemStack`: exact registry ID, quantity, metadata, and serialized chance
  when the handler declares a chance output.
- HBM `FluidStack`: exact fluid name and amount; pressure is not promoted to a
  machine operating requirement.
- Generic outputs: single/chance and weighted alternative groups.
- Material stacks: retained as `TAG_OR_HANDLER_SPECIFIC`, not converted to
  false item or fluid dependencies.

## Reviewed handler families

The inventory covers the handlers registered by `SerializableRecipe`, including
press, blast furnace, shredder, soldering, combination oven, centrifuge,
crystallizer, refinery/oil processing, liquefaction, solidification, coking,
pyrolysis, breeding, cyclotron, fuel pool, mixer, outgasser, compressor,
electrolysis, welding, rotary furnace, exposure chamber, particle accelerator,
ammo press, anvil, pedestal, annihilator, crucible, assembly, chemical plant,
PUREX, fusion, precision assembly, plasma forge, industrial blast furnace,
material distribution, custom machines, and arc furnace.

Generic handlers share the reviewed `inputItem`, `inputFluid`, `outputItem`,
`outputFluid`, `duration`, and `power` contract. Mixer and custom-machine
wrappers are flattened only through their declared `recipes` member while
preserving `outputType` or `recipeKey`.

## Machine associations

Single reviewed machine blocks and tile classes use `machine:` IDs. Fuel pool,
particle accelerator, anvil tiers, fusion, material distribution, custom
machines, and arc-furnace/foundry processing use `process:` IDs when a
one-to-one physical association is not defensible. This distinction makes those
families partial without discarding their exact typed recipe data.

Tile classes are loaded without initialization and inspected only for the
common-side HE MK2 receiver/provider interfaces. Instances are never created.

## Dynamic crafting handlers

- Cargo shell: partial; shell and output are exact, cargo is arbitrary.
- Grenade: partial; item families and output are exact, metadata compatibility
  is enum logic.
- MKU: unsupported inputs; layout depends on a world seed, so it is not run.
- Scraps: partial; item type is exact, material quantity/output NBT are dynamic.
- Custom-machine runtime registry: typed recipes are supported; physical
  configuration identity is partial.

Unsupported and partial entries are exported once with aggregate reasons; H1
does not emit per-recipe warning floods.

