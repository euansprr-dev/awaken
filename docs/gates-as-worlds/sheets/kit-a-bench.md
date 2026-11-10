# Kit sheet A: the bench (one fast check) and the straight-in walk

Read `BUILDER.md` in this folder first.

## Why

Measured on 10 Nov: builders ran the float and reach tests 13 to 16 times per gate and the preview 7 to 12 times, each a separate
Gradle run. In a 32 s preview run of The Hour Crypt, painting every block took 354 ms and the 15 pictures 2.1 s; the rest was Gradle
and the server starting. And 7 of 21 real-game walks never entered their Realm and photographed the overworld. You fix both.

## A1. One command, one report (do this first, commit it)

New package `src/main/java/dev/awaken/gates/realms/bench/`.

- `Bench.check(...)`: for one land, in one process: the preview's pictures and its report (call `Preview`; today's entry is private,
  expose what you need without changing how it paints or shades), `FloatCheck.scan` over the whole land (square of
  `halfSize() + 16`), and `WalkCheck.unreachable(land)`. Read `check/FloatCheck.java`, `check/WalkCheck.java`, `preview/Preview.java`
  and how `RealmTests` calls them (`realm_hour_walks_to_everything`, `realm_hour_nothing_floats`).
- It writes `<out>/bench.txt` in this shape, and `<out>/sheet.png` plus the pictures:

```
BENCH awaken:hour_crypt seed 1   total 3120 ms  (paint 350, floats 900, reach 400, shots 2100)
floats: 0
reach: all 23 sites
shots: 15, flagged 3: hour_tower FLAT PLAIN; hour_water PLAIN; hour_chamber PLAIN
sheet: /abs/path/sheet.png
```

  When something floats, group by block id and nearest piece label, and give the first 12 with coordinates. When a site can't be
  reached, name its kind, index and position. If `Settle` (kit sheet C) has been merged and reports removals, print them as a line
  `settled: ...`; don't depend on it.
- GameTest `realm_bench`: does nothing unless the environment asks (`AWAKEN_BENCH` = a tour file or a blueprint id,
  `AWAKEN_BENCH_OUT`, `AWAKEN_BENCH_ROOT`, `AWAKEN_BENCH_SEED`), like `realm_preview` does today. Register it with one line where
  `RealmTests.register()` registers `realm_preview`.
- Cameras: from the tour file when one is given (same parsing as `Preview.shots`). With only an id: one shot from the Mouth along
  `mouthYaw()`.
- `tools/bench/bench.sh once <tour.txt | blueprint id> [seed]`: one Gradle run through the slot wrapper, prints `bench.txt`, exits 0
  only when floats are 0 and every site is reached. Target: 45 s or less.

## A2. The checker that stays running

Goal: `tools/bench/bench.sh check <tour.txt | id>` answers in **5 s or less** once warm, and
`tools/bench/bench.sh shot <id> <x> <y> <z> <yaw> <pitch> [name]` (one camera, land coordinates) in about 1.5 s.

- `bench.sh up`: prepare once through the slot wrapper (compile, and whatever task writes `build/moddev/gameTestServerRunVmArgs.txt`
  and `gameTestServerRunProgramArgs.txt`), then start the same headless GameTest server **outside Gradle** with plain `java` and
  those argument files, so it never holds a build slot. Read `build.gradle` and the generated run configuration to get the main
  class, classpath and how `-Ptests` reaches the JVM. Heap cap `-Xmx1536m`. PID in `run-bench/pid`, log in `run-bench/log.txt`.
- In that JVM, `realm_bench` sees `AWAKEN_BENCH_SERVE=<worktree>/run-bench` and serves: poll `run-bench/req/*.json` every 50 ms, run
  the request with `Bench.check`, write `run-bench/out/<name>/` and `<name>.done`. Exit when `run-bench/stop` appears or after 20
  minutes with no request.
- Hot reload of a gate's code: before each request, `bench.sh` compiles the changed files under
  `src/main/java/dev/awaken/gates/realms/land/` with `javac -d run-bench/classes` against a classpath file (add one small Gradle task
  `benchClasspath` to `build.gradle` that writes the main compile classpath plus the main classes output). The server loads the
  package `dev.awaken.gates.realms.land` child-first from `run-bench/classes` in a fresh `URLClassLoader` per request, with the mod's
  own loader as parent, and builds the land by its class name with a `Land.Ctx`. The blueprint JSON
  (`src/main/resources/data/awaken/realms/<id>.json`) and biome JSON are read from the source tree on every request, so a gate that
  isn't in the running datapack still works. Anything outside the `land` package, and any new block, needs `bench.sh restart`.
  This reflective loading is dev tooling behind an environment variable: keep it inside `bench/`, never reachable from game code.
- `bench.sh down` stops it. `bench.sh status` says whether it's up and its memory (RSS).
- Print the timings in every report. Report the JVM's RSS in your hand-back: three of these must fit beside three Gradle runs on 16 GB.
- If A2 isn't reliable after about 90 minutes of work, stop, keep A1, and report exactly what blocked it.

## A3. The straight-in walk

- Command `awaken gate realm_enter <rank> <id> [seed]` (admin), next to `realm` in `gates/GateCommands.java`: puts the player inside
  that Realm at once, at the Mouth, facing `mouthYaw()`, with no gate forming in the overworld first. Read how
  `awaken gate realm` + `awaken gate interior enter` get there today (`Realms`, `RealmLive`, `run/Runs`, `GateRegistry`) and take the
  same path without the waiting. It must work from any spot in the overworld. A failure says why.
- Autopilot (`core/client/dev/Autopilot.java`): a new script line `need realm`. If the player isn't in the Realms dimension, the tour
  writes `FAIL not in a Realm` to its summary, takes no more shots and quits the game. Any `cmd` answered with "You're not in a
  Realm." fails the tour the same way.
- After `hud off`, a shot must show no HUD and no hand or held item. Check what `hud off` hides today and close the gap.
- Rewrite `tools/autopilot/realm-amber.txt`, `realm-hour.txt`, `realm-vine.txt`, `realm-kingsroot.txt`, `realm-saltcomb.txt`: enter
  with `cmd awaken gate realm_enter E <id>`, then `need realm`, `hud off` before the first shot, the same cameras and shot names as
  now, and `quit` as the last line. `Preview.shots` finds the land from the `cmd awaken gate realm RANK id` line: make it accept
  `realm_enter` too.
- `tools/tour-ci.sh` and whatever writes `summary.md`: a run whose log has "not in a Realm" is a FAIL, named as such on the first line.
- You don't run tours. The orchestrator runs the walks on GitHub after merging.

## Your files

`tools/bench/**`; `src/main/java/dev/awaken/gates/realms/bench/**`; `build.gradle` (the `benchClasspath` task only);
`src/main/java/dev/awaken/gates/GateCommands.java` (the new command only); `src/main/java/dev/awaken/core/client/dev/Autopilot.java`;
`tools/autopilot/realm-*.txt`; `tools/tour-ci.sh` and the summary writer; `preview/Preview.java` (expose an entry point and accept
`realm_enter`; **no change to painting or shading**, another builder owns shading); the one registration line. `.gitignore` may gain
`run-bench/`.

## Done when

`bench.sh once tools/autopilot/realm-hour.txt` and `bench.sh check tools/autopilot/realm-hour.txt` both print a report with timings;
`check` is 5 s or less warm (or you've said why not); Amber's hashes are unchanged; `-Ptests=realm_` is green once; committed.
