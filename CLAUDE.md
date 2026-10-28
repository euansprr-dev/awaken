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
- Tours (autopilot screenshots): look tours run on GitHub's macOS runners, several at once, never in the Mac's queue.
  Push your branch, then `tools/tour-ci.sh tools/autopilot/<x>.txt [timeout] [KEY=VAL ...]` (tools/autopilot.sh's knobs;
  shots, log and summary land in `run-autopilot/screenshots-ci/<x>-<run id>/`). The runner starts from a fresh game
  folder on a virtual display like the MacBook's (same pixels, same GUI scale), waits for the terrain before each shot,
  and always has Far Sight off. These stay on the Mac under `slot.sh tour`: perf and frame-time tours (`perf-*`, the Lens
  bench, anything measuring ms), Far Sight's tours, sound listening, and anything needing a real GPU's timing (SPEC §13,
  "Tours"). Every tour script picks a starter power (`cmd awaken powers starter 1`), or a slower machine shows its screen.
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
  (if needed: the Turbo contract below, coordinate first).
- Codecs for everything saved; `StreamCodec` for everything sent. Version every saved structure (`schemaVersion`).
- Server is authoritative. Client code lives in `...client` subpackages or `XxxClient` classes, never referenced from
  common code paths that run on a dedicated server.
- Performance budgets: SPEC header. No per-tick allocations in hot paths; spread heavy work across ticks.
- Comments: short, explain why. Match surrounding code.

## Turbo (reaching into the renderer)
Mixins only under the Turbo contract, each feature approved first: internals only in `dev.awaken.turbo.<feature>`, its
own client mixin config (not required, `defaultRequire` 0) listed in `neoforge.mods.toml`, `@Pseudo` when the target is
another mod's, version-locked to the exact Minecraft, NeoForge (and target mod) it was read against, a probe that the
mixin applied, a client setting, off on the first failure, and with it off the game behaves exactly as without it.
Kept only with a measured win; access transformer before mixin, never reflection. Only mixin classes in the mixin
package (anything else there can't be referenced and crashes the client). Record each in SPEC. Today: Pixels (§18,
`awaken.pixels.mixins.json`), Water edges (§20, `awaken.wateredge.mixins.json`, in Sodium's fluid renderer),
Tiles (§22 "Lens on Apple", `awaken.tiles.mixins.json`, in PostChain and PostPass for Lens's chains only),
Road starts (§28.9, `awaken.roadstarts.mixins.json`, common, in structure starts; AT `META-INF/accesstransformer.cfg`).

## Commits
Small, focused commits on the current branch. End every commit message with:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
