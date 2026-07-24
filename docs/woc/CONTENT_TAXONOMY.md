# WOC HBM Content Taxonomy

## Scope

Phase 4 turns the Phase 1 inventory into reviewable classifications and two
inactive draft content profiles. It does not remove Java registrations, mutate
registries or recipes, activate a profile, enable enforcement, or implement
research, nations, projects, events, licensing, or replacement-mod integration.

The definition is:

```text
docs/woc/taxonomy/hbm_taxonomy.v1.json
```

It is loaded only by an explicit taxonomy command or the pure-Java tooling entry
point. Normal gameplay does not depend on the taxonomy file. If the file is
missing or invalid, the tooling operation fails and gameplay is unchanged.

## Schema version 1

The top level contains:

- `taxonomySchemaVersion`, `taxonomyId`, display metadata, source and review
  status;
- `defaultDisposition`, currently `unresolved`;
- ordered `families`;
- exact-key `exactOverrides`;
- maintenance notes.

A family records a stable ID, name, description, optional parent and aliases,
domain tags, applicable exporter content kinds, include and exclude selectors,
default disposition, research-candidate hints, strategic class, replacement
policy, future owner, project/license/prototype flags, strategic sensitivity,
review status, rationale, and notes.

An exact override records the canonical key and the same final classification
metadata. The loader builds an exact-key index for constant-time override
lookup.

Selectors are data, not Java predicates. Supported fields are:

- one exact `canonicalKey`, explicit `canonicalKeys`, or exact
  `registryNames`;
- `namespace` and `contentKind`;
- exact registry name, registry-name prefix, or suffix;
- exact metadata or an inclusive metadata range;
- exporter source category;
- recipe manager or machine family;
- exact or prefix forms of stable Java/source class names.

Fields in one selector are ANDed. Selectors in a family are ORed. Regex is not
supported. Every expanded key and its producing selector is written to the
audit.

## Controlled vocabulary

Strict mode accepts only these domain tags:

```text
material.raw material.processed material.strategic material.nuclear
component.mechanical component.electrical component.electronic component.nuclear
fuel.solid fuel.liquid fuel.gaseous fuel.nuclear
waste chemical tool utility decorative food medical
protective.armor protective.hazmat
weapon.firearm weapon.ammunition weapon.explosive weapon.missile
weapon.nuclear weapon.energy weapon.melee weapon.launcher weapon.incendiary
weapon.chemical weapon.artillery weapon.special
machine.processing machine.power machine.nuclear machine.logistics
machine.storage machine.military infrastructure
worldgen.ore worldgen.structure loot debug test deprecated unobtainable creative_only
```

Other controlled values are:

- dispositions: `keep`, `future_research`, `future_project`, `future_event`,
  `disable_candidate`, `replacement_candidate`, `debug_disable`, `unresolved`;
- strategic classes: `civilian`, `dual_use`, `military`, `strategic`,
  `nuclear`, `administrative`, `development_only`;
- replacement policies: `none`, `external_gun_mod`,
  `external_vehicle_mod`, `WOC_system`, `server_event_only`, `admin_only`;
- future owners: `HBM`, `WOC-Core`, `WOC-Research`, `WOC-Territory`,
  `WOC-Diplomacy`, `WOC-Operations`, `WOC-Economy`, `WOC-NPC`,
  `external_mod`, `unresolved`;
- research domains: `metallurgy`, `industrial_processing`,
  `electrical_engineering`, `electronics`, `chemistry`,
  `conventional_weapons`, `rocketry`, `nuclear_engineering`,
  `reactor_engineering`, `radiation_protection`, `logistics`,
  `advanced_materials`, or empty;
- review status: `reviewed`, `provisional`, `unreviewed`;
- strategic sensitivity: `routine`, `controlled`, `sensitive`, `strategic`,
  `catastrophic`, `unresolved`.

Spelling and case are exact. Strict validation rejects synonyms, duplicates,
unknown parents/prerequisite hints, empty selectors, invalid metadata ranges,
and conflicting exact overrides.

## Deterministic precedence

Classification applies the following order:

1. exact canonical-key override;
2. explicit family membership using canonical keys;
3. a narrow selector containing at least two scope predicates;
4. a broad selector;
5. taxonomy default;
6. unresolved fallback.

An exclusion matching an entry removes that family before inclusion precedence
is evaluated. At equal precedence, compatible tags merge. Different
dispositions produce a conflict and the result becomes `unresolved`. A result
is a reviewed member only when every winning rule is reviewed and the result is
neither ambiguous nor conflicted. A family definition's `reviewStatus` and its
expanded members are reported separately: a reviewed definition does not
promote an ambiguous or conflicted member to reviewed.

## Initial reviewed families

The manifest covers conventional firearms and ammunition, conventional
explosives, missiles, strategic/nuclear and energy weapons, melee weapons,
armor and hazmat, vehicle-related entities, raw/processed/strategic/nuclear
materials, mechanical/electrical/electronic/nuclear components, conventional
and nuclear fuels, wastes, reviewed chemicals, processing/power/nuclear/
logistics/storage/military machines, decoration, food, medicine, utility
tools, worldgen ores and structures, structure loot, debug/test entries,
deprecated/unobtainable sentinels, and creative-only content.

The taxonomy is deliberately conservative:

- conventional firearms use 39 exact source-reviewed item keys and exact
  recipe-output registry names from the conventional Sedna factories;
- conventional `ammo_standard` uses 55 exact ordinary small-arms `EnumAmmo`
  keys. The remaining 40 ordinals are explicitly divided into explosive,
  launcher, incendiary/chemical, energy, nuclear, and utility/mortar families;
- explosives, missiles, nuclear weapons, armor, tools, and military machines
  are not part of the firearm replacement family;
- nuclear devices use only the stable `hbm:tile.nuke_` block/item/recipe
  namespace and exact source-proven devices. The `blocks.bomb` exporter
  category is expressly insufficient evidence: C4, Semtex, dynamite,
  fireworks, mining charges, and detonation cord are classified separately;
- worldgen ore expansion is zero and marked `UNSUPPORTED_EXPORT_COVERAGE`
  because Phase 1 currently exports named NBT structures, not ore-generator
  registrations;
- broad armor, tool, energy-weapon, vehicle, and sentinel groups remain
  provisional where their internal policy is not sufficiently reviewed;
- fluids are not broadly inferred from names; only an exact starter chemical
  set is classified.

The authoritative expansion counts and exclusions are generated in
`build/reports/woc/content_taxonomy_families.csv`. It has separate family
definition and member-review columns, conflicted/ambiguous counts, effective
coverage percentage, and exporter-coverage status. A zero count is retained
rather than hidden.

## Research-candidate metadata

`researchDomain`, `researchTier`, prerequisite-family hints, project/license/
prototype flags, and strategic sensitivity are descriptive planning metadata.
They do not define a final technology tree, costs, timings, laboratories,
player unlocks, or runtime checks.

Future WOC-Research may consume reviewed exports using:

```text
player UUID -> current nation -> persistent nation UUID -> nation research ledger
```

HBM must consume that future state only through a narrow optional provider.
Taxonomy is not the nation ledger and is not runtime research.

## Draft profiles

Generation writes:

```text
docs/woc/examples/content_profile.woc_server.draft.json
docs/woc/examples/content_profile.no_hbm_conventional_firearms.draft.json
```

Both use schema-v1, `defaultState: AVAILABLE`, exact rules, and prominent
draft warnings. They are never copied to `eclipse/config`.

The WOC server draft maps dispositions as follows:

| Taxonomy disposition | Draft profile state |
| --- | --- |
| `keep` | `AVAILABLE` |
| `future_research` | `RESEARCH_LOCKED` |
| `future_project` | `PROJECT_LOCKED` |
| `future_event` | `EVENT_ONLY` |
| `debug_disable` | `DISABLED` |
| `replacement_candidate`, `disable_candidate`, unresolved, or conflicted | `UNREVIEWED` |

Only `DISABLED` has Phase 3 behavior, and enforcement remains default-off.

The no-HBM-conventional-firearms draft is `COMPLETE` only for its stated,
source-reviewed replacement boundary: 39 conventional handheld firearm item
keys plus all 55 ordinary small-arms `EnumAmmo` keys, for 94 exact `DISABLED`
rules. It intentionally excludes explosive ammunition, launchers, missiles,
incendiary/chemical projectors, energy weapons, nuclear weapons, unusual
special weapons, armor, tools, machines, turrets, and artillery. The generator
refuses to label the draft complete if an inventory `gun_*` item lacks either
membership in the boundary or a reviewed non-conventional assignment.

The generated audit separately lists all reviewed firearm keys, reviewed
ordinary-ammunition keys, unresolved conventional-boundary keys, broader
unresolved ammunition-like entries, excluded non-firearm categories, and every
corrected nuclear-family member. The current dedicated-server inventory exposes
56 of the 94 source keys, so the audit distinguishes 94 explicit profile rules
from 56 effective inventory matches; both totals are reconciled.

## Reports and commands

Explicit reporting writes deterministic, timestamp-free files:

```text
build/reports/woc/content_taxonomy_audit.txt
build/reports/woc/content_taxonomy_unresolved.txt
build/reports/woc/content_taxonomy_families.csv
build/reports/woc/content_profile_woc_server_draft.json
build/reports/woc/content_profile_no_hbm_conventional_firearms_draft.json
```

The audit records canonical taxonomy and byte-level inventory checksums,
coverage counts, vocabulary validation, counts by kind/tag/disposition/
strategic class/owner, profile state counts, family expansion, every producing
rule, exclusions, conflicts, reviewed replacement-boundary keys, unresolved
weapon/ammunition-like keys, corrected nuclear members, the profile crosswalk,
and exact count reconciliation.

Operator-only, development-gated commands are:

```text
/wocdev taxonomy status
/wocdev taxonomy validate
/wocdev taxonomy report
/wocdev taxonomy explain held
/wocdev taxonomy explain <canonical-key>
/wocdev taxonomy family <family-id>
/wocdev taxonomy unresolved
/wocdev taxonomy generate-profiles
/wocdev taxonomy dry-run-firearms
```

`generate-profiles` validates the WOC draft permissively and the
no-HBM-conventional-firearms draft strictly through the Phase 2 parser.
`dry-run-firearms` passes that validated draft as an in-memory policy to Phase
3 adapters with mutation disabled. Neither command installs a profile or
changes the active enforcement policy.

The pure-Java entry point is
`com.hbm.wocbridge.taxonomy.ContentTaxonomyTool` with `validate`, `report`,
`generate-profiles`, or `fixtures`.

## Client/server inventory differences

Dedicated-server export intentionally omits creative-tab enumeration,
client-only subtype discovery, display names, icons, rendering and tooltips.
Its taxonomy can classify only metadata variants present in server-safe
registries, recipes, loot, worldgen, and other exported runtime data. A client
inventory therefore has more item/block rows. The conventional replacement
draft still contains the same 94 source-reviewed exact rules; only the audit's
effective inventory-match count can differ.

Every report embeds the inventory checksum. Reviewers must not compare counts
without also comparing that checksum and the source-side export mode.

## Review and maintenance workflow

1. Keep `enableContentEnforcement=false` and remove any runtime
   `content_profile.json`.
2. Export a fresh inventory from the intended server or client environment.
3. Run strict taxonomy validation and fixtures.
4. Generate reports twice and compare bytes/hashes.
5. Review unresolved, conflicted, zero-expansion, and weapon-like lists.
6. Prefer exact keys or narrow stable selectors. Add exclusions before
   widening a selector.
7. Record rationale and review status; do not promote heuristic output.
8. Validate both draft profiles and run the mutation-free Phase 3 dry-run.
9. Review the source-controlled draft diff. Never deploy a draft merely because
   it validates.

Unknown content stays explicitly unresolved. Reclassification is a review
change, not an opportunity to guess.
