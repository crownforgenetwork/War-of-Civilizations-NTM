# WOC HBM progression evidence exporter

The H1 exporter reads initialized, server-side HBM registrations and publishes
development evidence under `build/reports/woc/progression`. It does not define
research, change recipes or machines, activate profiles, enforce content, or
write world data.

## Command and configuration

The command is permission level 4:

```text
/wocdev export-progression
/wocdev export-progression fixtures
```

Both the existing `enableDevelopmentTools` setting and the independent
`enableProgressionExport` setting must be true. The latter defaults to false:

```properties
11_war_of_civilizations {
    B:enableDevelopmentTools=true
    B:enableProgressionExport=false
}
```

The independent default prevents a server that exposes other WOC development
commands from exposing progression evidence accidentally.

## Runtime boundary

The exporter uses common-side registries, `AchievementPage`, crafting and
smelting managers, `SerializableRecipe` handlers, and reviewed tile-class
interface checks. It does not call creative-tab, icon rendering, tooltip,
display-name, texture, model, GUI, or other client-only enrichment. It never
constructs a tile entity or invokes a recipe against a world.

Before and after each export it records counts and a deterministic fingerprint
for items, blocks, crafting recipes, smelting recipes, machine handlers, and
machine recipes. Publication fails if any value changes.

## Publication

The complete report set is written to a sibling staging directory, validated,
and swapped into place only after all files and hashes succeed. A failed export
restores the prior directory. All content is UTF-8 with LF line endings and no
timestamps, random identifiers, absolute paths, or world-derived values.

`progression-sha256-manifest.txt` hashes every output other than itself.
`progression-output-manifest.json` hashes the ten payload reports; excluding
itself and the SHA manifest avoids a recursive hash definition.

Generated build reports are ignored by Git. The stable schema identifier is
`woc-hbm-progression-v1`; the reviewed adapter identifier is
`hbm-progression-adapters-v1`.

## Recipe identity

Recipe IDs have this form:

```text
machine-recipe:<family>:sha256(<handler-id> LF <canonical-serialized-recipe>)
```

Object keys are canonicalized lexically. Registration order is recorded for
ordered containers but is not used as the primary identity. Duplicate
normalized IDs abort publication.

## Limitations

- Shared and multiblock handlers are process records, not invented single
  machines.
- Upgrade-dependent or configured energy fields stay blank unless the recipe
  serializer exposes an exact value.
- The world-seeded MKU input layout is not evaluated.
- Custom-machine recipe data is typed, while physical machine identity remains
  configuration-dependent.
- Transitive production relationships are mechanical evidence and are not
  deliberate research prerequisites.

