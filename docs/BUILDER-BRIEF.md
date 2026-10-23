# Brief for module builders

You are building one module of AWAKEN, a large NeoForge mod for Minecraft Java 26.3. The owner asked for the full game,
built "the best possible way". Aim for complete, polished, working features: no stubs or TODOs for anything in your scope
unless it is genuinely impossible, in which case say so in your report. Correctness first, then breadth, then polish.

## Read first (in this order)
1. `CLAUDE.md` — build commands, layout, rules. **Always build with `tools/gradle.sh`.** Never run the game client.
2. `docs/SPEC.md` — the implementation spec. Your module's section is the contract. Hard rules at the top apply to you.
3. `docs/api/core.md`, `docs/api/client.md`, `docs/api/worldgen.md`, `docs/api/entities.md` — exact 26.3 APIs.
   26.3 is NOT 1.21.1. When unsure, grep the real sources in `/Volumes/MEDIA/Awaken/.ref/{src,neo,assets}` (absolute paths;
   `.ref` is not in your worktree). Never guess a signature.
4. `docs/COUNTERS.md` — progression counters your module must report with `Progression.count(...)` /
   `Progression.setCounter(...)` / `Progression.record(...)`. Content (Truths, Titles, Directives, Paths) depends on them.
5. `docs/plan.html` is the player-facing design (165 KB). Grep it for your topic rather than reading it all.

## What already exists (use it, don't duplicate it)
- `dev.awaken.Awaken` (mod entry; already calls your `XxxModule.init`) and `dev.awaken.AwakenClient` (calls `XxxClient.init`).
- `core`: `AwakenConfig` (difficulty, story length, feature flags; client prefs in `AwakenConfig.Client`), `PlayDays`
  (play-day clock, `onMorning`, `onNewPlayDay`), `Forces` (damage types `awaken:heat|charge|gravity|spirit|frost|kinetic`,
  entity tag bitmask), `AwakenNet.send` (safe send — always use it for server→client), `AwakenCommands.add` (/awaken subcommands,
  `AwakenCommands.admin()` for game-master ones), `DirectorLog.log` (explain decisions for /awaken why), `CoreModule.TAB_KEY`
  (shared creative tab: add your items via `BuildCreativeModeTabContentsEvent` when `getTabKey().equals(CoreModule.TAB_KEY)`),
  `CoreModule.sound(...)` pattern, `core.test.AwakenTests.register(name, maxTicks, helper -> ...)` for GameTests,
  `core.client.AwakenKeys` (all key mappings: POWER_1..4 Z X C V, ULTIMATE R, MOVEMENT LAlt, STATUS K, FOCUS G,
  REALITY_CHECK H, ACCEPT Y, DISMISS N, BIND B).
- `core.api`: `MobRole` (+ attachment `CoreApi.MOB_ROLE`), `MobHooks` (spawnThemed / spawnBoss / scale; fallback spawns vanilla
  mobs), `PowerCoreHooks` (roll / forPower), `HearthHooks` (nearest / hearthOf), `CoreApi.ESSENCE_SHARD`, `CoreApi.LORD_HEART`.
- `signal.Signal` — `send(player, SignalWindow.builder(kind, title).row(..).body(..).footer(..).action("prefix:id").build())`,
  `toast(player, text)`, `onAction(prefix, handler)`. The client HUD that draws windows is being built in parallel.
- `progression.Progression` — profile, grantXp/grantLevels (with `XpSource`), count/setCounter/record, perk(player, id),
  markNewThing, awaken, passReassessment, offerTruths, offerPaths/choosePath, addInsight, fracture, bestMoment,
  applyAttributes. `Rank` (levels, contentLevel, share, colour), `Stat`, `Curve` (xpToNext, floorReward, mob scaling,
  floorsFor, breakDays, modifierBudget/Max), `Perks` ids, `RankHooks.onRankUp`, `DaysEnd.addWaiting`,
  `RewardHooks.powerLearner`. Client: `progression.client.StatusSections.add(Tab, Section)` and `CodexEntries.addSource`.

## Your boundaries
- Write only inside: your package `src/main/java/dev/awaken/<module>/`, your own resource files under
  `src/main/resources/{assets,data}/awaken/...` (names that clearly belong to your module), `src/main/merge/<module>/...`
  (your lang `assets/awaken/lang/en_us.json`, `assets/awaken/sounds.json`, tags), `tools/art/specs/<module>.py`,
  `tools/content/<module>*.py`, and `docs/modules/<module>.md`.
- Do NOT edit other modules, `core`, `signal`, `progression`, `build.gradle`, `CLAUDE.md` or `docs/SPEC.md`. If you need a
  change there, implement a local workaround if it's safe and list the request in your report.
- Other modules are being built at the same time in other worktrees. Integrate through the APIs above and registry ids, not
  through their classes.

## Assets
- Textures: generate them with the shared palette toolkit `tools/art/pixelart.py` (see its docstring and
  `tools/art/specs/_sample.py`). Put your spec in `tools/art/specs/<module>.py`, run `python3 tools/art/build_all.py <module>`,
  and look at a contact sheet: `python3 tools/art/preview.py /tmp/<module>.png block` (then Read the PNG). Keep the look
  coherent: dark slate + amber lantern + signal blue are the mod's identity; each theme/stratum gets its own ramp.
- Entity textures for mobs that reuse vanilla models: reference the vanilla texture path and tint it in the renderer instead
  of copying Mojang files. Your own models get generated textures.
- Sounds: point `sounds.json` entries at vanilla sound files with pitch/volume changes (`minecraft:block/amethyst/chime1`).
- Models, blockstates, client item definitions, recipes, loot tables: 26.3 JSON formats are in the API sheets. Every
  block/item needs a model, an `items/<name>.json` client definition, a lang name, and (blocks) a loot table.

## Quality bar and tests
- Must compile: `tools/gradle.sh 900 build`. Must pass ALL GameTests: `tools/gradle.sh 1200 runGameTestServer`.
- Add GameTests for your logic (pure logic and in-world behaviour). They run headless on a server with fake players.
- Respect the performance budgets in SPEC. No per-tick allocations in hot paths; budget heavy work across ticks.
- Server is authoritative; client-only code lives in `...client` classes that common code never touches.
- Commit in small steps on your branch with this trailer on every commit:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

## When you finish
Write `docs/modules/<module>.md`: what you built, how to try it in game (commands, items), decisions you made, known gaps.
Your final report (under 400 words): what's complete, what's partial and why, test results, and any requests for core/other
modules. Make sure everything is committed on your branch.
