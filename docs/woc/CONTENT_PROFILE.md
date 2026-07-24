# WOC Content Profile Foundation

## Phase 2 scope

Phase 2 is descriptive and read-only. Loading a profile does not unregister, hide, disable,
replace, gate, or otherwise change any item, block, entity, fluid, recipe, loot entry, creative
tab, or world-generation feature. The profile service only parses data, validates references,
indexes rules and tags, computes a checksum, and answers queries.

The Phase 2 API remains descriptive. The separate Phase 3 enforcement consumer is documented in
[`CONTENT_ENFORCEMENT.md`](CONTENT_ENFORCEMENT.md) and acts only on exact `DISABLED` rules when
its independent safety gates pass.

## Location and startup behavior

The production profile path is:

```text
config/hbmConfig/woc/content_profile.json
```

The profile is loaded once during server startup after HBM's late recipe registration. There are
no per-tick file reads. A successful `/wocdev profile reload` builds and validates a replacement
snapshot before atomically publishing it. A failed reload leaves the prior accepted snapshot
unchanged.

If the production file is absent, the server logs a warning and uses an in-memory profile whose
default is `AVAILABLE`. When `writeContentProfileExample` is enabled, the server may atomically
create `content_profile.example.json` beside the missing production file. That example is never
loaded and an existing file is never overwritten.

## Schema version 1

The canonical Phase 2 collection name is `rules`:

```json
{
  "schemaVersion": 1,
  "profileId": "woc_development",
  "defaultState": "AVAILABLE",
  "rules": [
    {
      "key": "item:hbm:item.acetylene_torch#0",
      "kind": "ITEM",
      "state": "AVAILABLE",
      "notes": "Optional author notes",
      "unlockTechnology": "woc:industrial_chemistry",
      "requiredProject": "woc:example_project",
      "replacementKey": "item:hbm:tile.machine_assembly_machine#0",
      "salvageKey": "item:hbm:item.acetylene_torch#0",
      "eventMetadata": {},
      "adminMetadata": {}
    }
  ],
  "tags": {
    "woc:example": [
      "item:hbm:item.acetylene_torch#0"
    ]
  },
  "notes": "Optional profile notes"
}
```

`unlockTechnology`, `requiredProject`, `replacementKey`, `salvageKey`, `eventMetadata`, and
`adminMetadata` are reserved data only. Phase 2 validates their shape or references but gives them
no behavior. `replacement` and `salvage` are accepted aliases; new profiles should use the
explicit `replacementKey` and `salvageKey` names.

Tags are named sets of canonical keys. Tag IDs and technology/project IDs use lowercase
namespaced identifiers, such as `woc:industrial_chemistry`. Every tag member must have a rule.

## Phase 1 skeleton compatibility

The Phase 1 exporter emits schema version 1 with a collection named `entries`, exact exporter
kinds, and `UNREVIEWED` states. Phase 2 accepts `entries` as a legacy alias for `rules` and emits
the deterministic `LEGACY_ENTRIES_MIGRATED` warning. It does not rename keys, kinds, or states.

For a normal authoring workflow:

1. Copy `build/reports/woc/content_profile_skeleton.json` to the production profile path.
2. Optionally rename `entries` to `rules`; both forms load.
3. Review rules and replace every `UNREVIEWED` state before enabling strict mode.
4. Run `/wocdev profile validate`.

If both `rules` and `entries` are present, `rules` is used and validation reports the ambiguity.

## Canonical keys and kinds

Keys are exact, case-sensitive exporter identifiers. Localized display names are never
identifiers. Metadata is retained after `#` for items, blocks, and entities.

| Kind | Key form | Runtime validation |
| --- | --- | --- |
| `ITEM` | `item:<registry-name>#<metadata>` | Item registry |
| `BLOCK` | `block:<registry-name>#<metadata>` | Block registry |
| `ENTITY` | `entity:<registered-name>#<mod-id>` | HBM entity table |
| `FLUID` | `fluid:<fluid-name>` | HBM fluid registry |
| `CRAFTING_RECIPE` | `crafting-recipe:<output>:<sha256>:<occurrence>` | Shape only |
| `SMELTING_RECIPE` | `smelting-recipe:<output>:<sha256>:<occurrence>` | Shape only |
| `MACHINE_RECIPE` | `machine-recipe:<output-or-family>:<sha256>:<occurrence>` | Shape only |
| `CREATIVE_TAB` | `creative-tab:<tab-label>` | Creative tab table |
| `STRUCTURE` | `structure:<registered-name>` | HBM structure table |
| `STRUCTURE_LOOT` | `loot:<pool-and-item>:<sha256>:<occurrence>` | Shape only |
| `LOOT_ENTRY` | `loot:<pool-and-item>:<sha256>:<occurrence>` | Shape only |
| `WORLDGEN_FEATURE` | `worldgen:<registered-name>` | HBM structure table |

`CREATIVE_TAB` and `STRUCTURE_LOOT` are the exact names currently emitted by Phase 1.
`STRUCTURE` and `LOOT_ENTRY` are also recognized for forward-compatible authoring. Recipe and loot
rows do not have stable Forge registries in this HBM version, so Phase 2 records them as safely
non-runtime-resolvable instead of guessing an identity.

## States

| State | Meaning in the profile | Phase 2 behavior |
| --- | --- | --- |
| `AVAILABLE` | Intended to be normally usable | Descriptive only |
| `RESEARCH_LOCKED` | Intended to require technology | Descriptive only |
| `PROJECT_LOCKED` | Intended to require a project | Descriptive only |
| `EVENT_ONLY` | Intended for event/admin distribution | Descriptive only |
| `DISABLED` | Intended to be unavailable | Descriptive only |
| `UNREVIEWED` | Authoring placeholder from the inventory export | Warning or rejection only |

`UNREVIEWED` is allowed with a warning in permissive mode and rejected in strict mode. None of
these values affects gameplay in Phase 2, including `DISABLED`.

## Validation modes and configuration

The settings are in HBM's `11_war_of_civilizations` configuration category:

- `strictContentProfileValidation=false` is the development default. Unknown registry keys,
  malformed entries that can be skipped safely, duplicates, and `UNREVIEWED` are warnings.
- `writeContentProfileExample=true` is the development default. It controls only the
  non-production example written when the real file is absent.
- `enableDevelopmentTools=true` continues to control registration of `/wocdev`.

Production recommendation: finish review, validate with zero errors and zero unexpected warnings,
then set `strictContentProfileValidation=true`. Also set `enableDevelopmentTools=false` if operator
inspection/export commands are not desired in production. Disabling the command does not disable
the read-only profile service.

Strict mode rejects unsupported schemas, malformed rules, duplicate/conflicting keys, unknown
kinds or states, `UNREVIEWED`, unresolved runtime registry keys, malformed metadata,
malformed technology/project IDs, bad tag members, and invalid replacement/salvage references.
A rejected reload preserves the last accepted in-memory profile.

Every issue exposes a severity, stable code, affected key or location, and readable message.
Malformed permissive entries are skipped rather than guessed.

## Commands

All commands require permission level 4 and are registered only while development tools are
enabled:

```text
/wocdev export-content
/wocdev profile validate
/wocdev profile reload
/wocdev profile status
/wocdev content held
/wocdev content <canonical-key>
```

`validate` checks the disk file without replacing the active snapshot. `reload` installs only an
accepted snapshot. `status` reports profile ID, schema, mode, rule/tag counts, issue count, and
checksum. `content` reports key, kind, effective descriptive state, tags, future unlock/project
fields, and issues for the key.

Validation writes a deterministic report to:

```text
build/reports/woc/content_profile_validation.txt
```

The report contains the source path, ID, schema, mode, acceptance status, counts by state and kind,
rule/tag counts, checksum, and sorted issues. It has no timestamp.

## Query API and checksum

`ContentProfileManager` exposes read-only lookups for state, rule, default, profile ID, checksum,
validation issues, tag members, review status, `ItemStack`, and block/metadata pairs. `explain`
provides the same diagnostic information used by the command. Unknown keys use the configured
default state; a missing or unusable startup profile therefore resolves unknown keys to
`AVAILABLE`.

The checksum is SHA-256 over the canonical effective snapshot. Rules and tags are sorted, fields
are length-delimited, JSON metadata objects have sorted property names, and no timestamps or map
iteration order are included. Property order and file line endings cannot change the checksum.

## Known limitations

- Recipe and structure-loot identities can be validated structurally but not resolved back through
  a stable runtime registry in this HBM version.
- Item/block metadata is preserved and range-checked, but Phase 2 does not infer subtype validity
  by calling client-only display or creative enumeration code.
- Reserved unlock, project, replacement, salvage, event, and admin fields have no behavior.
- Profile state is server-memory diagnostic data only; there is no client synchronization or UI.
- The profile layer itself does not mutate content. Phase 3's separately configured consumer can
  enforce exact `DISABLED` rules; it does not enforce research or project fields.

See `CONTENT_ENFORCEMENT.md` for adapter coverage, reload behavior, and server/client boundaries.

## Phase 4 taxonomy drafts

Phase 4 can generate two schema-v1 preparation artifacts under
`docs/woc/examples`: a broad WOC server draft and an optional
no-HBM-conventional-firearms draft. They use exact rules,
`defaultState: AVAILABLE`, and prominent
`DRAFT / NOT FOR PRODUCTION` notes. Generation never copies either file to the
runtime `eclipse/config/hbmConfig/woc/content_profile.json` location.

The taxonomy tooling validates the WOC server draft permissively because
descriptive `UNREVIEWED` entries are intentional. It validates the
94-rule no-HBM-conventional-firearms draft strictly. `COMPLETE` applies only to
its stated reviewed conventional handheld-firearm and ordinary small-arms
ammunition boundary, not to artillery, launchers, missiles, explosive,
incendiary/chemical, energy, nuclear, unusual, or utility weapon systems.
Validation is non-installing and cannot replace the active profile snapshot.
See `CONTENT_TAXONOMY.md` for selector precedence, controlled vocabulary,
report checksums, and review workflow.
