# WOC DISABLED Content Enforcement

## Scope and activation

Phase 3 enforces only rules whose exact state is `DISABLED`. `AVAILABLE`,
`RESEARCH_LOCKED`, `PROJECT_LOCKED`, `EVENT_ONLY`, and `UNREVIEWED` do not
produce enforcement decisions. There are no nations, player-owned research,
project locks, or mass HBM classifications in this phase.

Enforcement becomes active only when all of these gates pass:

1. `enableContentEnforcement=true`;
2. `config/hbmConfig/woc/content_profile.json` exists and is accepted;
3. the active profile is not the in-memory fallback;
4. its schema version is supported;
5. the targeted rule itself is explicitly `DISABLED`.

A missing or rejected startup profile fails open. A rejected reload preserves
the previous profile and enforcement policy. The default configuration leaves
gameplay identical to HBM without this layer.

## Configuration

All settings use the existing `11_war_of_civilizations` category in `hbm.cfg`.

| Setting | Default | Meaning |
| --- | --- | --- |
| `enableContentEnforcement` | `false` | Master gate for Phase 3. |
| `enforceDisabledCrafting` | `true` | Filter exact disabled crafting recipes/outputs at startup. |
| `enforceDisabledSmelting` | `true` | Filter exact disabled furnace recipes/outputs at startup. |
| `enforceDisabledMachineRecipes` | `true` | Filter supported HBM serializable machine registries at startup. |
| `enforceDisabledItemUse` | `true` | Cancel disabled held-item use on the logical server. |
| `enforceDisabledBlockPlacement` | `true` | Cancel disabled exact-metadata placements on the logical server. |
| `enforceDisabledLoot` | `true` | Filter HBM `ItemPool` arrays at startup. |
| `enforceDisabledWorldgen` | `true` | Give supported weighted NBT structures zero weight at startup. |
| `hideDisabledFromNEI` | `true` | Hide exact item/block variants through a client-only helper. |
| `sanitizeDisabledInventory` | `false` | Reserved; Phase 3 never scans or mutates inventories. |
| `strictEnforcementStartup` | `false` | Fail startup if an explicit disabled rule has no safe enabled adapter. |
| `writeEnforcementAudit` | `true` | Write the deterministic audit report. |

For production, validate a deliberately reviewed small profile first, set
`strictContentProfileValidation=true`, then enable enforcement. Keep
`strictEnforcementStartup=true` when unsupported disabled targets must be a
hard error. Do not use the canary profile in a valuable world.

## Adapter coverage and reload behavior

| Adapter | Boundary | Reload class |
| --- | --- | --- |
| Crafting | `CraftingManager` recipe list; explicit key or exact output | `RESTART_REQUIRED` |
| Smelting | `FurnaceRecipes` map; explicit key or exact output | `RESTART_REQUIRED` |
| Machine recipes | Top-level mutable `Map`/`Collection` from each `SerializableRecipe` handler | `RESTART_REQUIRED` |
| Item use | Server `PlayerInteractEvent` and `PlayerUseItemEvent.Start` | `LIVE_RELOAD_SAFE` |
| Block placement | Server `BlockEvent.PlaceEvent`, including multi-place snapshots | `LIVE_RELOAD_SAFE` |
| Structure loot | HBM `com.hbm.itempool.ItemPool` arrays | `RESTART_REQUIRED` |
| World generation | Ordinary weighted named `NBTStructure` spawn conditions | `RESTART_REQUIRED` |
| NEI | Client-only exact-metadata `API.hideItem` calls | `RESTART_REQUIRED` (client restart/reconnect) |

Recipe, loot, and worldgen adapters run once after HBM and other mods finish
late registration. They do not scan every tick. Reload atomically changes only
the live event policy; it does not partially mutate startup registries and
reports that a restart is required.

Machine enforcement uses each handler's public serializer and actual returned
container. It does not use reflection to guess private registries. Array
containers, serialization failures, and one owner that expands into multiple
nested recipe rows are reported as unsupported instead of being broadly
removed.

Placement cancellation uses Forge's captured block snapshots. Forge restores
the original world state and the pre-placement stack when the event is
canceled. Existing placed blocks and all existing inventory stacks remain
untouched.

## Canary

`docs/woc/examples/content_profile.phase3_canary.json` is a non-production,
manual-only profile. It defaults to `AVAILABLE` and disables seven disposable
targets: debug ammunition, the event tester block, one template-folder crafting
recipe, one optional food smelt, one optional press recipe, one redundant scrap
loot entry, and the decorative `ruinJ` feature.

To test, copy it manually to the production profile path in an expendable
server, set `enableContentEnforcement=true`, and restart. Nothing copies it
automatically.

## Commands and audit

Operator-only commands remain gated by `enableDevelopmentTools`:

```text
/wocdev enforcement status
/wocdev enforcement audit
/wocdev enforcement explain held
/wocdev enforcement explain <canonical-key>
/wocdev enforcement reload
/wocdev enforcement dry-run
```

`dry-run` computes intended actions with zero registry mutations and also runs
small decision/metadata/determinism fixtures. `reload` reapplies only live-safe
event decisions. `explain` reports the safety gate, matching adapters, and
final allow/deny decision.

The deterministic audit is:

```text
build/reports/woc/content_enforcement_audit.txt
```

It contains no timestamp or object identity. It records activation gates,
profile/checksum, all config values, disabled-rule counts, per-adapter examined,
planned, and mutated counts, exact action keys, manager coverage, unsupported
paths, aggregate denials, reload requirements, and omissions.

## Server/client separation and omissions

Common enforcement code has no NEI, rendering, icon, tooltip, creative subtype,
or other stripped client API reference. NEI integration is isolated in a
`@SideOnly(CLIENT)` helper. Hiding is presentation only; the server event and
registry adapters are the authority. Client profile changes require restarting
the client (or another NEI configuration cycle); no profile synchronization
protocol is added in Phase 3.

Known omissions:

- inventory sanitation is intentionally inert;
- non-player item paths such as dispensers, bespoke packets, and machine-owned
  actions are not universally intercepted;
- vanilla/Forge `ChestGenHooks`, legacy `HbmChestContents`, mob drops, and
  bespoke dynamic loot are not filtered;
- coordinate-predicate NBT structures bypass weighted selection and are
  reported unsupported;
- unsupported/non-serializable machine registry shapes are reported;
- already generated chunks and existing structures are never changed.

Giving a weighted structure zero weight changes the weighted selection bound
for future chunks. That can change which remaining structure is selected for a
given seed, although unrelated generation systems are not directly mutated.
