# pack/throughput: hand-off (2026-10-24, cloud session)

Deterministic GameTests and a name filter for fast module runs. Branched from Euan's WIP af4564a, with
`origin/build/awaken-v1` (2e05a76) merged in.

## Done

**`-Ptests=<comma-separated name prefixes>`** registers only the GameTests whose names start with one of them
(`AwakenTests.register`/`registerIsolated`/`registerBare`, wired in `build.gradle`'s `gameTestServer`, documented in
CLAUDE.md). It logs `Test filter -Ptests=...: N of 324 GameTests registered`. A filter that matches nothing throws at
registration with `TEST FILTER MATCHES NOTHING: -Ptests=x matches none of the 324 AWAKEN GameTests ...`, and the
Gradle task fails (exit 255) instead of the server's bare "found no tests".

```
./gradlew runGameTestServer -Ptests=anomaly_,fauna_,gates_interior_build_budget,gates_surface,powers_hits
  -> 55 of 324 registered, 55 passed in 24.6 s
```

**The flakes, each fixed at its cause.** The common root: `GameTestServer.waitUntilNextTick` only runs pending
tasks and never waits, so the server ticks as fast as it can. A tick count is not time. The chunk workers and the
light engine run on other threads and are scheduled from the main-thread queue; on a busy machine they fall
seconds behind while thousands of ticks go by. (CI logs show "Can't keep up" stalls of up to 79 s.)

| Test(s) | What failed on CI | Cause | Fix |
|---|---|---|---|
| every `anomaly_*` test that waited for "the platform ticks" (`rift_close_pays_once`, `star_lands_guards_pays_once`, ...) | `the platform ticks on tick 2402` | Entity ticking needs the 5x5 chunks round each forced chunk loaded to FULL. Only the forced 5x5 were loaded at once. The ring around them is generated in the background, then promoted on the main-thread queue, and that took longer in real time than 2400 fast ticks. | `AwakenTests.withLoadedArea` starts every test once its whole force-loaded 5x5 area entity-ticks. `settle` runs the chunk system's main-thread queue (as `getChunk` does while it waits) for at most 30 s of real time. `platform()`/`meadow()` now assert that their whole square ticks, so the waits are gone. |
| `powers_hits_ground_slam_lineup`, `powers_hits_shockwave_lineup` | `ravager did not move; iron golem did not move; mossback did not move on tick 55` | Same cause: the bodies stood in chunks that weren't entity-ticking yet, so they took the knockback but never moved. The ones that did move were in ticking chunks. | Same fix: the line-up's area ticks before the test starts. |
| `fauna_p3_caves_spawn` | `no hunter spawns in torchlight: [wisp_hound, drip_lurker, chime_cricket] on tick 75` (6 of 13 failed runs) | Two causes. (1) A 30-tick idle after the torch, but the light engine lights on its own thread, so torchlight could still be missing. (2) One torch in the middle of the 15-wide grotto leaves its four corners 14 blocks away at block light 0. Hunters may rightly spawn there under vanilla's monster light rule, so the test was wrong even with settled light. | `AwakenTests.awaitLight` waits until the light engine has taken every block change in a box (its per-chunk "pending tasks" marker runs after the update that took them). Then `grottoLight` asserts the real values: no sky light, and either no block light or some on every floor block. Two torches light the whole floor. |
| `fauna_p3_talents` | `the grotto is dark on tick 66`, `in torchlight the player is safe from the hunters (the light rule) on tick 86` | Fixed idles (30 and 20 ticks) before reading light the engine hadn't computed yet. | `grottoLight(..., false)` after the grotto, and `awaitLight` on the torch before `inDarkness`. |
| `fauna_p2_talents_dark`, `fauna_spawn_rules_in_world`, `fauna_hunters_keep_the_light_rule` | `the rooms are dark on tick 302`, `the sealed room is dark on tick 302` | They polled the light value with `thenWaitUntil`, and the 300-400 tick budget ran out before the light engine caught up. | `awaitLight` over the rooms, then assert. The WIP's 1200-tick budgets went back to 300/400: waiting no longer spends ticks. |
| `terrain_hero_builds_in_box` (protects the light tests after it) | — | A fixed 200-tick idle meant to let ten thousand blocks of light settle. | `awaitLight` over both trees' boxes. |
| `gates_interior_build_budget` | (known flake on the Mac under build load) | Single wall-clock samples on a shared machine count GC pauses and preemption as cost. The WIP re-timed slow chunks but still averaged the first paints. | The layout build is the fastest of 3 (uncached `FloorBuilder.build`). A chunk slower than the 4 ms mean budget is painted up to 4 more times and costs its fastest paint, in the mean and the worst both. Noise only adds time, so a chunk counts at most 4 ms unless all 5 paints are slow: the budgets (mean 4, worst 40, build 50 ms) fail only on real cost. The first-run figures are logged too. |
| `gates_surface_respects_builds` | (shared ground) | `Overworld.untouched` looks 14 blocks round (64 for a Hearth). Test plots sit 6 blocks apart on a grid that earlier tests leave chests, planks, torches and Hearths on. | Checks fresh ground 3000 blocks off the grid, loaded with `getChunk` for the synchronous check (`untouched` skips unloaded columns). |

Also from Euan's WIP, kept after review: `anomaly_grove_bends_the_rules` opens the sky over its meadow (flowers grow
at the heightmap, under whatever neighbouring plots built above). `fauna_p2_caps_hold_with_every_family` gives every
family single spawns before the packs, so the first two patches can't fill every cap.

Rules for new tests are in SPEC §13 "Tests" and CLAUDE.md. Never wait a fixed number of ticks for chunks or light.

## How the fixes were checked

The VM is fast and idle, so the plain suite passes there either way. To reproduce CI, the suite ran pinned to 2
cores with a busy loop on each (`taskset -c 0,1` plus two spinning shells):

- build/awaken-v1 (2e05a76): **2 failed**, `powers_hits_shockwave_lineup` ("zombie/ravager/spider/cow did not move
  on tick 55") and `fauna_p3_caves_spawn` ("no hunter spawns in torchlight: [drip_lurker, chime_cricket]").
- this branch: **324/324 passed** (4.24 min against 4.09). The log shows test areas that needed 1.5-2 s of real
  time to entity-tick: the time the old tick budgets didn't have.
- this branch on one core shared with a busy loop (much harsher than CI): 323/324. Every fixed test passed. The one
  failure was `terrain_hero_blueprints` (see Left). `gates_interior_build_budget` stayed far inside its budgets
  there: mean at most 0.44 ms (budget 4), worst at most 4 ms (budget 40), build at most 3.3 ms (budget 50).

## Left

- Seen once each in the 13 failed CI runs before this branch, cause not established, not touched here:
  - `hearth_why_not_reasons`: an opening was reported as a roof hole (y 126). Its room sits at a chunk-aligned
    spot (`(o & ~15) + 4`, y+120) that other plots in the same chunk share, so leftovers outside the room may steer
    the flood.
  - `fauna_mount_taming`: "one feeding counts and eats one berry on tick 0". It failed in the same run as two
    tick-0 failures next to it on the grid, both now fixed.
  - A `NullPointerException` in `DistanceManager.runAllUpdates` while the server stopped after the summary. It
    didn't change the result.
- `terrain_hero_blueprints` (terrain's test, not touched here) asserts the Elder Colossus blueprint at its fastest
  seed under 8 ms. CI logs show 5.3 ms fastest; this VM 2.8-3.0 ms; one core shared with a busy loop 11.9 ms. It
  hasn't failed on CI, but sustained contention (the Mac with builds running beside the tests) can push it over.
  Fastest-of-N removes pauses, not a machine at half speed. If it flakes there, time it with thread CPU time.
- No screenshot tours: nothing visible changed.

## CI

Two consecutive green GameTests runs on 1deaf2e (the code; this doc came after):
- [40101221504](https://github.com/euansprr-dev/awaken/actions/runs/40101221504) (push): 324/324 passed in 4.05 min.
- [40101972766](https://github.com/euansprr-dev/awaken/actions/runs/40101972766) (workflow_dispatch, same commit): 324/324 passed in 4.75 min.
