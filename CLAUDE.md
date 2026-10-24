# AWAKEN — engineering guide

A NeoForge mod for Minecraft Java **26.3** (NeoForge 26.3.0.95, Java 25). Mod id `awaken`, base package `dev.awaken`.
Design: `docs/plan.html` (player-facing plan) and `docs/SPEC.md` (implementation spec, the source of truth).

## Build and test
- Always build through the watchdog script: `tools/gradle.sh <timeout-seconds> <gradle args>`.
  It sets `JAVA_HOME` (JDK 25 on the MEDIA SSD), `GRADLE_USER_HOME=/Volumes/MEDIA/.gradle-home`, logs to `logs/`.
  - Compile + jar: `tools/gradle.sh 900 build`
  - GameTests (headless server, no window, no sound): `tools/gradle.sh 1200 runGameTestServer`
  - While you iterate, run only your tests: `tools/gradle.sh 900 runGameTestServer -Ptests=terrain_,anomaly_`
    (comma-separated name prefixes; a filter that matches nothing fails with a clear message). Run the full suite
    once at hand-off; CI also runs it on every push (`.github/workflows/gametests.yml`).
  - In a GameTest never wait a fixed number of ticks for chunks or light: the test server never waits between ticks.
    Tests start on entity-ticking ground; read light after `AwakenTests.awaitLight` (SPEC §13, "Tests").
- Never launch the game client (`runClient`) without asking the owner first: it steals focus and plays sound on his Mac.
- The project lives on the external SSD. Don't write big files to the internal disk.

## Verifying APIs (important)
26.3 is NOT 1.21.1. Many names changed (`ResourceLocation` → `Identifier`, `hurt` → `hurtServer`, ValueInput/ValueOutput
saves, render states, data-driven dimension attributes, SDL3 input...). Before using any Minecraft or NeoForge API:
1. Check the cheat sheets in `docs/api/` (core, client, worldgen, entities).
2. Grep the real sources: `/Volumes/MEDIA/Awaken/.ref/src` (Minecraft + NeoForge patches),
   `/Volumes/MEDIA/Awaken/.ref/neo` (NeoForge API), `/Volumes/MEDIA/Awaken/.ref/assets` (vanilla assets + data JSON).
   (`.ref/` is gitignored; in a worktree use these absolute paths.)
Never guess a signature. A compile error costs more than a grep.

## Layout
```
src/main/java/dev/awaken/
  Awaken.java            mod entry: calls every XxxModule.init(modBus)
  AwakenClient.java      client entry: calls every XxxClient.init(modBus)
  core/ signal/ progression/ powers/ mobs/ gates/ descent/ lucidium/ veil/ above/ hearth/ story/ world/
src/main/resources/      assets/awaken/... and data/awaken/... (namespaced file names per module)
src/main/merge/<module>/ shared JSON contributions (lang en_us.json, sounds.json, tags) merged at build time
src/main/templates/      neoforge.mods.toml
docs/                    plan.html, SPEC.md, api/*.md, BUILD-LOG.md
tools/                   gradle.sh, sim/ (pacing simulator), art/ (texture + sound generators)
```

## Module rules
- Each module owns its package and registers its own DeferredRegisters, attachments, payloads (its own
  `RegisterPayloadHandlersEvent` listener), client renderers, commands (`/awaken <module> ...`) and GameTests.
- Only touch your module's package, your own resource files, and `src/main/merge/<module>/`.
- Cross-module calls go through the public API classes in SPEC §12. Keep those signatures stable.
- Content numbers and behaviour come from `docs/SPEC.md`. If you must decide something new, add it to SPEC in the same commit.

## Code style
- Java 25, records for data, `final` classes where sensible, no reflection hacks, no mixins unless unavoidable
  (if needed: `src/main/resources/awaken.mixins.json`, coordinate first).
- **The Turbo contract** (Far Sight plan, approved 2026-10-24: "I'm open to all of them … I'm really open to anything
  for making this absolutely peak", then "Go ahead with Far Sight build out along with pixels and turbo bridge").
  Minecraft internals are allowed only under these rules (SPEC §16):
  1. One place: internals live only in `dev.awaken.turbo` (and its subpackages), with their own mixin configs
     (`awaken.turbo.mixins.json`, …) and the access-transformer lines in `META-INF/accesstransformer.cfg`.
  2. Never required: every feature has a public-API fallback and switches itself off when its probe fails (wrong
     backend, unknown version, failed self-test). The game never crashes because of Turbo.
  3. Version-locked: Turbo turns on only for the Minecraft and NeoForge versions in `TurboVersions.VERIFIED`. After an update
     it stays off until its tests and tours pass again and the versions are added.
  4. Earned: a feature stays only with a measured win on the owner's Mac (docs/perf/turbo/REPORT.md).
  5. Least force: an access transformer before a mixin, never reflection.
- Codecs for everything saved; `StreamCodec` for everything sent. Version every saved structure (`schemaVersion`).
- Server is authoritative. Client code lives in `...client` subpackages or `XxxClient` classes, never referenced from
  common code paths that run on a dedicated server.
- Performance budgets: SPEC header. No per-tick allocations in hot paths; spread heavy work across ticks.
- Comments: short, explain why. Match surrounding code.

## Commits
Small, focused commits on the current branch. End every commit message with:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
