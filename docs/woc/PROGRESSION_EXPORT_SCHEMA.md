# HBM progression export schema v1

Every CSV begins with `schema_version=woc-hbm-progression-v1`. Empty cells mean
unknown or inapplicable; a partial or unresolved row supplies
`unresolved_reason`. CSV files are UTF-8, LF-only, deterministically sorted, and
RFC-style escaped.

## Controlled vocabulary

Evidence origins:

- `RUNTIME_REGISTRY`: initialized common-side registration data.
- `STATIC_REGISTRATION`: reviewed tracked source evidence.
- `EXISTING_EXPORT`: a prior generated export.
- `REVIEWED_MANUAL_ADAPTER`: an explicit reviewed mapping.
- `PARTIAL_RUNTIME`: runtime evidence that lacks a complete typed contract.
- `UNRESOLVED`: safe extraction is unavailable.

Confidence values are `EXACT`, `HIGH`, `PARTIAL`, and `UNRESOLVED`.

Component roles are `INPUT`, `OUTPUT`, `CATALYST`, `CONTAINER_INPUT`,
`CONTAINER_OUTPUT`, `BYPRODUCT`, `CHANCE_OUTPUT`, `TOOL`, and `OTHER`.
Component kinds are `ITEM`, `BLOCK`, `FLUID`, `ORE_DICTIONARY`,
`ALTERNATIVE_GROUP`, `TAG_OR_HANDLER_SPECIFIC`, and `UNRESOLVED`.

## Reports

### `achievement-progression.csv`

Stable achievement and localization/registration IDs, parent, page, page order,
coordinates, registered icon kind/registry ID/metadata, exact-resolution flag,
adapter, confidence, origin, and reason. The icon is resolved from the
registered `ItemStack`; source field names are not used.

### `machines.csv`

Stable machine/process and family IDs, runtime block/item IDs, variant, reviewed
tile class, handler IDs, confirmed HE MK2 interface direction, blank nominal
energy values, supported roles, adapter, confidence, origin, and reason. A
`process:` ID identifies a shared or non-physical recipe system.

### `machine-recipes.csv`

Stable recipe, machine, family, and handler IDs; registration order when the
container is ordered; directly serialized duration and energy fields; directly
represented temperature/pressure/tier/other requirements; confidence, adapter,
origin, and reason. `power` and `consumption` are recorded as energy per tick
only for reviewed handler contracts that use those serialized fields that way.

### `machine-recipe-components.csv`

One row per typed component: recipe ID, order, role, kind, canonical ID,
metadata, quantity, fluid amount, exact chance, alternative-group ID,
consumption/container flags, confidence, adapter, origin, and reason.
Alternative-group rows are alternatives, never simultaneous mandatory inputs.
HBM material stacks remain `TAG_OR_HANDLER_SPECIFIC`.

### `machine-energy-contracts.csv`

Machine, network/kind, direction, idle/active/operation/storage/minimum values,
adapter, evidence location, confidence, origin, and reason. Direction is
confirmed from reviewed tile classes implementing `IEnergyReceiverMK2` or
`IEnergyProviderMK2`. No numerical tier is inferred.

### `dynamic-recipe-handlers.csv`

Handler ID/class, runtime registration and recipe counts, adapter state
(`SUPPORTED_EXACT`, `SUPPORTED_PARTIAL`, or `UNSUPPORTED`), reason, iteration
safety, output/input determinability, future work, adapter, and origin.

### `machine-prerequisites.csv`

Target machine, crafting recipe, component, optional producing machine,
evidence path, and confidence. Relationship type is either
`DIRECT_CRAFTING_DEPENDENCY` or `TRANSITIVE_PRODUCTION_DEPENDENCY`. The latter
is a mechanical output/input join and never asserts design intent.

### Coverage, gap, and manifests

`progression-coverage.md` is a human summary.
`progression-gap-report.json` contains deterministic coverage counts and gaps.
`progression-input-manifest.json` hashes relevant tracked sources and any
existing Phase 1 reports.
`progression-output-manifest.json` hashes payloads and records before/after
runtime snapshots.
`progression-sha256-manifest.txt` provides set-level SHA-256 verification.

