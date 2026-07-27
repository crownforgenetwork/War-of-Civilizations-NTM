# HBM progression export H1 acceptance

H1 is accepted only after the Java 8 build, structural fixtures, actual
dedicated-server command gates, deterministic repeat export, and repository
hygiene checks pass.

## Structural fixtures

Run on an initialized server with `enableProgressionExport=true`:

```text
wocdev export-progression fixtures
```

The fixture set covers exact and unresolved achievement icons, parent
preservation, item/fluid/ore inputs and outputs, alternatives, catalyst/tool
consumption semantics, container return, chance output, exact/partial machine
association, consumer/producer/unknown energy interfaces, supported and
unsupported dynamic handlers, duplicate IDs, sorting, CSV escaping, Unicode,
atomic replacement, failure rollback, and no registry mutation.

## Dedicated-server procedure

1. Build with Java 8.
2. Start a dedicated development server with
   `enableProgressionExport=false`; verify the command is explicitly rejected.
3. Stop normally, enable the setting, and restart.
4. Run the fixtures.
5. Run `wocdev export-progression`.
6. Preserve the first report set outside the report directory.
7. Run the same command again and compare every file byte-for-byte.
8. Verify the output manifest's before/after item, block, crafting, smelting,
   handler, and machine-recipe counts and identical fingerprints.
9. Validate the existing content/taxonomy tooling.
10. Stop normally and move runtime worlds/logs outside the repository.
11. Run `git diff --check` and audit every sibling repository.

Do not claim a runtime result unless the corresponding server command actually
ran. No test may place content, generate progression, activate a profile, or
change gameplay configuration beyond the ignored development-run command gate.

## Scope stop

Acceptance ends H1. WOC-Tools ingestion, research authoring, research gameplay,
profiles, enforcement, balance, registry changes, recipe changes, and machine
changes are separate work.

