# WOC-Tools integration guidance for HBM progression schema v1

WOC-Tools should ingest `woc-hbm-progression-v1` as evidence alongside, not as
a replacement for, its R1 census. H1 deliberately does not modify WOC-Tools.

## Stable joins

- Join achievements by `achievement_id`.
- Join machines/processes by `machine_id` and families by `machine_family_id`.
- Join components to recipes by `recipe_id`.
- Join prerequisite targets and producers by `machine_id`.
- Treat `handler_id` as an adapter identity, not a gameplay technology.

Recipe identity is a SHA-256 of the handler ID and canonical HBM serialization.
It changes only when that normalized recipe evidence changes. Consumers should
not derive meaning from registration order or row number.

## Semantic rules

- `INPUT` is directional; `TOOL` or a false `consumed` value must not become a
  material cost.
- Rows sharing `alternative_group_id` are mutually alternative members, never
  simultaneous requirements.
- `CHANCE_OUTPUT.chance` is an exact serialized probability when present.
- `TAG_OR_HANDLER_SPECIFIC` is opaque until a dedicated WOC-Tools adapter
  understands that namespace.
- Blank energy fields are unknown/inapplicable. They are not zero.
- `energy_per_tick` and `energy_per_operation` must remain distinct.
- No energy tier may be inferred from magnitude.
- `TRANSITIVE_PRODUCTION_DEPENDENCY` is mechanical evidence only and must not
  be compiled directly into a research edge.

## R1 gaps addressed

H1 supplies canonical achievement icons, typed directional components for
reviewed machine handlers, explicit chance/alternative semantics, runtime
custom-handler states, common-side machine associations, confirmed energy
network direction, and direct/transitive prerequisite evidence.

Remaining gaps include configurable/upgrade-dependent energy numbers,
world-seeded MKU inputs, intentional technology prerequisites, shared
multiblock identity, and stable identity for externally configured custom
machines.

## Recommended ingestion phase

Add a schema-v1 HBM progression reader that:

1. verifies the SHA manifest and payload hashes;
2. rejects unknown schema or adapter versions;
3. imports exact evidence automatically;
4. retains partial/unresolved rows as review findings;
5. preserves alternative groups and component roles;
6. compares H1 runtime counts with the R1 census;
7. emits a review delta without generating tiers, costs, or final research
   dependencies.

