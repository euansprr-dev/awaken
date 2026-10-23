# AWAKEN v2 — brief for builders ("The Awakened World")

**Status:** P1 (the first-hour slice) is merged: System v2 (`docs/modules/system-ui.md`), onboarding and the level road
(`docs/modules/onboarding.md`), overworld creatures (`docs/modules/fauna.md`), anomalies (`docs/modules/anomalies.md`).
You are now building **P2/P3** (the living world, the road to 50). Read the module docs of anything you extend.

The owner played build 1 and said: the overworld feels like vanilla, the start is "go find iron", the first dungeon
kills you after the prologue, the pop-ups are huge and confusing, and he never knows what to do next. He approved
plan v2: `docs/plan-v2.html` (read sections 1, 4 and 6; grep the rest). You are building one part of **P1, the
first-hour slice**. The owner will playtest a fresh world for 60 minutes; these targets are the bar:

- first power in hand within ~10 minutes; level 3–4 at 30 min, 5–6 at 60 min
- at least 6 new creature types seen in the first 30 minutes, without travelling far
- no System prompt over the crosshair; nothing taller than ~20% of the screen; every choice says what each key does
- at any moment one glance answers "what could I do now?"
- calm days, dangerous nights, light still means safety; nothing interrupts building at home
- 120 fps on an M4 MacBook: no measurable drop

Read `docs/BUILDER-BRIEF.md` too (rules, assets toolkit, quality bar). Everything there applies, with the changes below.

## What changed since build 1 (use these, they are on your branch)
- `signal.SignalWindow`: `Presentation` {AUTO, TOAST, CHOICE, STORY, PANEL} and `Builder.choice(actionId, yesDoes, noDoes)`.
  For a real decision always use `choice(...)` with plain words ("Leave with your loot" / "Keep going"). Information is a
  toast. The Signal's voice and revelations are STORY. Never write key hints into footers yourself.
- `core.api.NowHooks`: register a `Source` that returns up to a few `Suggestion`s for one player ("Rift tear · 40 m
  north"). Only concrete, doable things; empty is fine. Priority: urgent 90+, clear next step 50–80, idea 10–40.
- `Progression.onAwakened(consumer)`, `Progression.onLevelReached((player, level) -> ...)`,
  `Progression.discover(player, key, fractionOfLevel)` (first-time XP, returns whether it was new).
- `GateHooks.openWandering(player, near, floors, lifetimeTicks)` and `GateHooks.release(level, id)`: short gates at the
  player's rank that fade unless entered, never break.
- `Later.run(server, ticks, runnable)` for next-tick work (never teleport or setBlock inside a running event).
- Tools: `tools/autopilot.sh <script>` runs a scripted client and saves screenshots. **Builders must NOT run it or any
  client** (it opens a window on the owner's Mac). Write an autopilot script for your feature in
  `tools/autopilot/<your-part>.txt` (see `tools/autopilot/smoke.txt` and `core/client/dev/Autopilot.java` for the
  commands); the integrator runs them after merging.

## Testing lessons from build 1 (they cost hours; don't relearn them)
- The GameTest server creates **no datapack dimensions**. Code that needs the gate world uses
  `GateService.useTestLevel(level)` for one tick inside try/finally (see `story/StoryTests` prologue test).
- Mock players (`makeMockServerPlayerInLevel`) are **creative** and **can't be hurt** for 60 ticks: call
  `p.setGameMode(GameType.SURVIVAL)` and `p.connection.markClientLoaded()` before damaging them.
- Tests that change shared world state (story/world flags, gates, global hooks, spawning) must use
  `AwakenTests.registerIsolated` so they run in their own batch.
- Never schedule from inside a scheduled GameTest callback; use `startSequence()` / `succeedWhen`.
- Neighbouring chunks may not be entity-ticking in tests: keep test mobs inside one chunk.
- One fully green run of the whole suite on your final code is enough; the integrator re-runs it after merging.
  Tests that wait for chunks or time of day must not depend on what other tests leave behind (see the fauna and
  anomaly tests for patterns: switch natural spawning off, filter by distance, generous wait budgets).

## Boundaries for v2
Write only inside the paths your prompt lists. Shared files you may append to: your module's `src/main/merge/<folder>/`
(lang, sounds, tags). If you need something outside your paths, implement a local workaround if it's safe, and list the
request in your report. Commit small steps on your branch with the trailer
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Document in `docs/modules/<your-part>.md` and list
SPEC-worthy decisions at the end of it (the integrator folds them into SPEC).

## Report (under 400 words)
What's complete, what's partial and why, test results, autopilot script path, requests.
