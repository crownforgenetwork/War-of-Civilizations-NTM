# War of Civilizations Future Module Architecture

## Repository boundaries

War of Civilizations is planned as coordinated Forge mods with independent Git
histories, versions, builds, releases, and commits under
`D:\War of Civilizations\Mods`. The parent directory must not become a
monolithic repository.

- **HBM** owns HBM content, machines, materials, recipes, content filtering,
  HBM-specific enforcement, and narrow optional integration hooks.
- **WOC-Core** will own persistent nation UUIDs, identity, membership, roles,
  permissions, common networking, GUI shell, audits, and common APIs.
- **WOC-Territory** will own named claimed territories, chunk ownership,
  inherited policy, exceptions, and map UI.
- **WOC-Research** will own nation research ledgers, research definitions,
  laboratories, permissions, projects, licenses, blueprints, and cooperation.
- **WOC-Diplomacy** will own alliances, treaties, truces, wars,
  justifications, war goals, ideologies, doctrines, sanctions, and its UI.
- **WOC-Operations** will own Major Orders, item/research/control objectives,
  capture and defense events, hidden bases, and nation rewards.
- **WOC-Bridge** will be a narrow Minecraft-side bridge to a modern sidecar
  for Discord, website, account linking, approved flag assets, announcements,
  and event synchronization.
- **WOC-Economy** and **WOC-NPC** remain later modules whose requirements must
  be made concrete before implementation.

None of these future modules is implemented by Phase 4.

## Nation identity and research ownership

Research belongs to an immutable nation UUID, never permanently to a player:

```text
player UUID -> current nation -> nation UUID -> nation research ledger
```

Leadership controls a nation but does not define its identity. Leadership
transfer preserves research, claims, treaties, modifiers, and Major Order
progress. A departing player takes no national research into another nation.
Laboratories operate against the authoritative nation ledger with delegated
roles and permissions; block NBT is not the sole permanent research store.

Allies may license or cooperate on technology but do not automatically merge
research trees.

## Unified command interface

Separate mods should appear as one server-authoritative interface with tabs
such as Overview, Members and Roles, Territory, Research, Diplomacy,
Modifiers, Major Orders, and Nation Appearance. Module blocks may open a
relevant tab, but GUIs submit requests and never authoritatively mutate state.

Nation appearance should use a persistent UUID, display name, unique tag,
primary/secondary colors, approved flag asset ID, leader, members, roles,
defaults, and modifiers. Chat or scoreboard output is a projection, never the
authoritative nation store. Worlds should store approved asset IDs, not
arbitrary image bytes or URLs.

## Territory and permissions

A territory is a named collection of claimed chunks. Policy resolves in this
order:

```text
server policy -> nation default -> territory policy -> chunk override
-> role/player exception
```

Subjects may include leader, role, member, ally, neutral, enemy, player, or
everyone. Actions may include entry, build/break, doors, containers, machines,
redstone, fluids, vehicles, PvP, fire, explosions, teleport, and spawn.

HBM-specific explosion code may later query one narrow territory/explosion
policy. Territory ownership and permission logic do not belong in HBM.

## Modifiers and Major Orders

Nation modifiers should be data-driven rather than scattered hardcoded
effects. Potential stats include research speed, machine throughput, gun
damage, mining speed, movement, defense, fuel efficiency, cost, war
exhaustion, and sanctions. Sources may include ideology, doctrine, Major
Orders, events, occupation, alliances, sanctions, or audited administration.
HBM may later expose narrow HBM-specific modifier queries.

Major Orders may gather or produce items, research technology, hold/capture/
defend points, restore facilities, and run multi-stage objectives. Progress
and rewards belong to nation UUIDs. Permanent runaway snowball rewards should
be avoided.

## Diplomacy and external services

Diplomacy owns alliances, treaties, truces, justifications, war goals,
declarations, research pacts, licenses, blueprint transfers, and joint
projects.

A Java 8 Forge mod should not embed a modern Discord bot. The preferred path
is:

```text
Minecraft server <-> authenticated local versioned protocol <-> modern sidecar
<-> Discord bot / website backend
```

Minecraft remains authoritative and validates identity, roles, state, nonces,
and protocol version.

## Optional HBM provider boundary

Future optional contracts may include:

```text
IWocNationProvider
IWocResearchProvider
IWocTerritoryProvider
IWocExplosionPolicy
IWocModifierProvider
```

These contracts must be narrow, versioned, server-authoritative, and optional.
HBM must launch and preserve its existing behavior when every provider is
absent. Provider contracts, their consumers, and all sibling modules are
outside Phase 4 and must not be inferred from taxonomy metadata.
