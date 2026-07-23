# WOC Content Inventory Exporter

The Phase 1 exporter reads the initialized HBM registries without modifying them and writes a deterministic inventory to `build/reports/woc`.

## Enable the command

The exporter is controlled by the Forge configuration file `hbm.cfg`:

```properties
"11_war_of_civilizations" {
    B:enableDevelopmentTools=true
}
```

The development default is `true`. Set it to `false` and restart before deploying HBM to a production server. When disabled, `/wocdev` is not registered. The command also checks the toggle again before exporting.

`/wocdev` requires Minecraft command permission level 4. Only operators (or the integrated-server owner when commands are permitted) can run it.

## Run from `runClient`

1. Start the development client with Java 8:

   ```powershell
   $env:JAVA_HOME = 'D:\.jdks\temurin-1.8.0_492'
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   .\gradlew.bat runClient
   ```

2. Confirm `enableDevelopmentTools=true` in `eclipse/config/hbm.cfg`.
3. Open a world with commands enabled.
4. Run:

   ```text
   /wocdev export-content
   ```

When Gradle launches from the configured `eclipse` run directory, the exporter finds the parent repository and writes to the repository-level `build/reports/woc`.

## Run from a dedicated `runServer`

1. Start the development server with Java 8:

   ```powershell
   $env:JAVA_HOME = 'D:\.jdks\temurin-1.8.0_492'
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   .\gradlew.bat runServer
   ```

2. Accept the development EULA if Gradle requests it, then restart.
3. Confirm `enableDevelopmentTools=true` in `eclipse/config/hbm.cfg`.
4. Grant the invoking player operator status or run the command from the server console.
5. Run `/wocdev export-content`.

In a standalone server without a parent `build.gradle`, the reports are written beneath that server's working directory at `build/reports/woc`.

## Generated files

Every CSV is UTF-8 with RFC-style quoting for commas, quotes, and line breaks. `content_key` is the identifier written to the profile skeleton.

### `items.csv`

| Column | Meaning |
|---|---|
| `content_key` | Stable `item:<registry>#<metadata>` key |
| `registry_name` | Live item registry name |
| `metadata` | Exported creative/sub-item metadata |
| `java_class` | Registered item implementation |
| `unlocalized_name` | Metadata-aware unlocalized name, when safe |
| `display_name` | Server-side localized display name, when safe |
| `creative_tab` | Assigned creative-tab label |
| `source_category` | Java package below `com.hbm` |
| `inferred_family` | First stable registry-path segment |

### `blocks.csv`

The columns match `items.csv`; keys use `block:<registry>#<metadata>`. Metadata comes from the block's registered item form when one exists. Blocks without an item form receive a metadata-zero row.

### `entities.csv`

| Column | Meaning |
|---|---|
| `content_key` | Registered name plus HBM mod-local entity ID |
| `registry_name` | Name returned by HBM's live `ModEntityList` |
| `registration_name` | Name passed to the Forge mod-entity registration |
| `mod_entity_id` | HBM's mod-local entity ID |
| `entity_kind` | `ENTITY` or spawn-egg `MOB` |
| `java_class` | Entity implementation |
| `tracking_range` | Registered tracking range where represented |
| `velocity_updates` | Registered velocity-update behavior |
| `egg_primary_color` | Spawn-egg primary color for mobs |
| `egg_secondary_color` | Spawn-egg secondary color for mobs |
| `source_category` | Java package below `com.hbm` |

### `crafting_recipes.csv`

| Column | Meaning |
|---|---|
| `content_key` | Output registry/metadata plus a SHA-256 recipe fingerprint |
| `recipe_class` | Live `IRecipe` implementation |
| `output_registry_name` | Registered output item |
| `output_metadata` | Output metadata |
| `output_count` | Output stack size |
| `normalized_inputs` | Slot-ordered shaped inputs or sorted shapeless inputs |
| `recipe_manager` | `minecraft:crafting` |
| `status` | `OK` or a readable unsupported-class marker |

The report includes live crafting recipes whose output is in the `hbm:` namespace. Item stacks, blocks, items, ore-dictionary alternatives, and empty shaped slots are normalized explicitly.

### `smelting_recipes.csv`

| Column | Meaning |
|---|---|
| `content_key` | Output and SHA-256 input/output fingerprint |
| `recipe_class` | Furnace recipe-manager class |
| `output_registry_name` | Registered output item |
| `output_metadata` | Output metadata |
| `output_count` | Output stack size |
| `normalized_inputs` | Registered input stack, metadata, count, and NBT |
| `recipe_manager` | `minecraft:furnace` |
| `experience` | Furnace experience formatted with a fixed locale |
| `status` | Serialization status |

The report includes recipes where either the input or output belongs to HBM.

### `machine_recipes.csv`

| Column | Meaning |
|---|---|
| `content_key` | Machine family/output plus a SHA-256 canonical-recipe fingerprint |
| `recipe_class` | HBM `SerializableRecipe` handler implementation |
| `recipe_object_class` | Concrete entry/wrapper class supplied to the handler |
| `output_registry_name` | Sorted item outputs found in output fields |
| `output_metadata` | Semicolon-aligned metadata values |
| `output_count` | Semicolon-aligned output counts |
| `normalized_inputs` | Canonical values of explicit input/ingredient/catalyst/stamp fields |
| `recipe_manager` | Existing HBM recipe JSON filename |
| `machine_family` | Stable family derived from that filename |
| `normalized_recipe` | Full canonical JSON emitted by the existing HBM serializer |
| `status` | `OK` or `unserializable:<class>` |

Fluid-only outputs remain available in `normalized_recipe` even when the item-output columns are blank. Custom-machine entries containing nested recipes are expanded into individual rows.

### `creative_tabs.csv`

| Column | Meaning |
|---|---|
| `content_key` | `creative-tab:<tab label>` |
| `tab_label` | Internal creative-tab label |
| `translated_label` | Server-side translated label |
| `java_class` | HBM creative-tab implementation |
| `icon_registry_name` | Registered icon item |
| `registered_item_count` | Unique directly assigned registered items |
| `source_category` | Java package below `com.hbm` |

### `worldgen_features.csv`

| Column | Meaning |
|---|---|
| `content_key` | `worldgen:<registered structure name>` |
| `feature_name` | Live `NBTStructure` registration name |
| `feature_type` | NBT, component, jigsaw, or generic registered structure |
| `spawn_weight` | Relative registered weight |
| `size_limit` | Jigsaw/component limit |
| `range_limit` | Horizontal range limit |
| `min_height` | Minimum configured height |
| `max_height` | Maximum configured height |
| `has_biome_predicate` | Whether a biome predicate exists |
| `has_coordinate_predicate` | Whether a coordinate predicate exists |
| `jigsaw_pool_count` | Number of registered jigsaw pools |
| `source_class` | Spawn-condition implementation |

### `structure_loot.csv`

| Column | Meaning |
|---|---|
| `content_key` | Pool, stack, and SHA-256 pool-entry fingerprint |
| `pool_name` | Live HBM `ItemPool` name |
| `item_registry_name` | Registered item in the pool |
| `metadata` | Item metadata |
| `minimum_count` | Minimum generated count |
| `maximum_count` | Maximum generated count |
| `weight` | Weighted-random entry weight |
| `nbt` | Stack NBT, when present |
| `display_name` | Server-side display name |
| `source_category` | Item Java package below `com.hbm` |

### `fluids.csv`

| Column | Meaning |
|---|---|
| `content_key` | `fluid:<HBM fluid name>` |
| `fluid_name` | HBM's live internal fluid name |
| `numeric_id` | HBM fluid ID |
| `java_class` | Fluid implementation |
| `unlocalized_name` | Fluid translation key |
| `conditional_name` | Custom display override or translation key |
| `temperature` | Registered temperature |
| `color` | Six-digit RGB color |
| `traits` | Sorted trait implementation classes |
| `source_category` | Built-in, custom, or foreign registration |

### `content_profile_skeleton.json`

The skeleton contains:

- `schemaVersion: 1`;
- `defaultState: "UNREVIEWED"`;
- one entry for every CSV `content_key`;
- `state: "UNREVIEWED"` on every entry;
- a stable `kind` identifying the source report.

Keys use registry names, metadata, registered manager/family names, IDs, and deterministic hashes. Display names are never used as primary keys.

## Deterministic ordering

- Registry rows sort by identifier-based `content_key`.
- Shaped crafting inputs preserve slot order; shapeless and ore-dictionary alternatives sort by normalized identifier.
- Smelting, loot, and machine maps are copied out of hash-backed registries and explicitly sorted.
- Machine recipe JSON object properties are recursively sorted; arrays retain their meaningful recipe order.
- Duplicate identical recipes receive a deterministic occurrence suffix after sorting.
- Numeric formatting uses `Locale.US`.
- Reports contain no timestamps, absolute source paths, random values, or object identity strings.
- Each file is written to a temporary sibling and atomically replaces the prior report when supported by the filesystem.

To check repeatability, run the command twice without changing registries or configuration and compare file hashes:

```powershell
Get-FileHash .\build\reports\woc\*
```

## Known omissions

- Item and block metadata is limited to variants returned by each registered item's `getSubItems` implementation. Hidden NBT-only states that are not exposed there are not invented.
- Crafting serializers explicitly understand vanilla shaped/shapeless and Forge ore-dictionary recipes. Other `IRecipe` implementations remain listed with a readable `unserializable:<class>` input marker.
- Machine enumeration covers every handler in `SerializableRecipe.recipeHandlers`. Separate or dynamically calculated families without that common enumerable interface are not fabricated; this includes the post-initialization `MagicRecipes`, `LemegetonRecipes`, `SILEXRecipes`, `GasCentrifugeRecipes`, and `RadiolysisRecipes` paths.
- Item-output summary columns in `machine_recipes.csv` are best-effort. The canonical serialized recipe remains authoritative for multiple-choice, fluid, material, heat, flux, and other non-item outputs.
- `worldgen_features.csv` covers the live named `NBTStructure` registry. Ore veins, bedrock deposits, biome decorators, and legacy generators without a common public registry are not enumerated.
- `structure_loot.csv` inventories HBM `ItemPool` contents. It does not infer every structure-to-pool call site, bespoke `LootGenerator` output, or external/vanilla `ChestGenHooks` table.
- Entity export covers HBM's mod-entity mappings, not the entire global vanilla/modded entity universe.
- Localized labels reflect the server's active localization data. They are descriptive columns and do not affect keys or ordering.
- The exporter does not load a content profile, assign gameplay states, hide content, remove recipes, or enforce research. Those are later phases.
