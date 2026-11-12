# Gates in Motion: the build, file by file

Approved 12 Nov 2026 (PLAN.md status line). This is how it is built; SPEC §72 records what was built. Code it whole, then
check once on CI (compile, named tests through `tools/check-ci.sh`, CI tours). No local Java, Gradle, tours or GameTests.

## The numbers every beat is sized by (from the body's own rules, simulated)
- Sprint jump (vanilla air: +0.026 a tick, x0.91, vy 0.42, gravity (v-0.08)x0.98): feet travel 4.8 level, 5.5 to one
  lower, 5.8 to two lower, 3.7 to one higher; the body's 0.3 overhang at both ends adds 0.6. So a gap of 6 or more, level
  or down to two, is never a sprint jump: that is what makes a wall-run gulf need the run. WalkCheck's own jump is 1-3 cells.
- Wall run (RunnerRules, mirrored from RunnerMoves.runApply): 35 ticks at E, 40 from D; speed 0.30-0.42. At 80% of the
  ticks and the slowest speed it covers 8.4 (E) / 9.6 (D+); the feet rise about +0.9 by tick 12 and are back level by tick
  28. So gaps: 6-7 at E, 6-8 at D, 7-8 at C, 8-9 at B, A and S. The far shelf is level with the near one or one lower.
- Wall-jump chain in a two-wide corridor: about +1.3 a link, so four links (E-B) climb +4.3, five (C+) +5.6 before the last
  run; a lip at +5 to +6 is caught from it (HANG 1.2-2.4 over the feet).
- Slide: 4.09 in all (3.27 at 80%); a slide that ends under a one-high roof goes on as a crawl (vanilla's pose), so a low
  stretch is never a trap; low stretches are 1 cell (E, D) or 2 (C+).
- Mantle: a rise of 1.5 (a block and a bottom slab) is past vanilla's jump (1.25) and inside MANTLE_MAX 1.6.
- Hang: lip 1.2-2.4 over the feet; a jump from the floor catches lips up to 3.5 over it; climbOut needs 2 of headroom.
- A fall the Course catches: more than 6, or below a line's catch height (new: `Course.catchY`).

## New files
- `gates/realms/kit/crawl/Sculpt.java`: a part made of columns (feet, roof, water) plus solid boxes with a role (beam, lip,
  cap, rail, line, plinth, step, deck, scuff, grate), air boxes, and the boxes a move needs kept clear ("keep") or solid
  ("wall"). `open`, `carve`, `lay` like Chasm; `enforce(V)` runs last in GeneratedCrawl.finish so no skin, trim or other
  part can fill a slide gap, cut a run wall or grow on a lip. `view()` is a BlockGetter of the sculpt alone (the proof at
  generation, with no painting).
- `gates/realms/kit/crawl/Line.java`: a builder over a Sculpt in a local frame (along, across, up) with a cursor; every
  beat writes into one.
- `gates/realms/kit/crawl/Beat.java`: the 24 beats (enum: family, the moves each asks for, the lowest rank, up or down,
  footprint), and `Beats.java`: each beat's geometry for a size from 0 (E) to 1 (S), its Moves, catch ledges, signs, hold
  spots and lesson points.
- `gates/realms/kit/crawl/BeatSkin.java`: the roles in a kind's own blocks; every rolled skin returns one.
- `gates/realms/kit/crawl/Composer.java`: picks a leg's beats (rank table, kind accents, the leg's height change, never
  the same beat twice running, two families from C, no family over 40% of a run, the signature once), proves each line,
  rerolls up to six times, then takes a simpler beat, then today's crossing; logs `beats.fallback`.
- `gates/realms/kit/crawl/Well.java`: the shaft at plan 3, square, built of Line flights round its walls with landings
  at the corners (holds on them). Six forms: spiral with landings, broken spiral, chimney, chain fall, ledges down,
  square well; up shafts get the climbing forms.
- `gates/realms/check/MoveProof.java`: proves each `Land.Move` with RunnerRules / BodyRules / GripRules probes on any
  BlockGetter (a sculpt's view at generation; the painted land in tests), at 80% of reach and over a slow and a fast
  timing. `PaintedView.java`: a lazy BlockGetter over a land's painted chunks for tests.
- `gates/run/Holds.java`: holds, perches and the chase waking and leashing (RealmLive calls it).
- `gates/net/BeatPayloads.java` + `gates/client/BeatLessons.java`: the first time a family is met, the gold figure and
  the plain line; a clean line's chime and Flow.
- `body/ascent/client/GateLessons.java`: the public hook into the package-private `Lesson` (SPEC §12).
- Tests: `gates/realms/BeatTests.java` (every beat at its smallest and largest size, proved on its own and placed in the
  test level and probed there; the sweep over 17 kinds x seeds x ranks: walk, float, moves, needless, families, variety,
  fallbacks, time; plan 2 unchanged).

## Changed files
- `Land.java`: `Move(move, family, needed, x0..z1)` and `moves()`; `Hold(leg, kind, x, y, z, radius, count)` and
  `holds()`; `Course.catchY`; `Lesson(family, x, y, z, path)` and `lessons()`.
- `check/WalkCheck.java`: cells painted once per land; move edges (Leap's rule); `needlessMoves` (a walk with no moves
  from each needed move's start, inside its course, must not reach its end).
- `kit/crawl/CrawlLand.java`: `beatBlock(M, role, ...)` for sculpts; a sculpt's cells never adorned where a move needs them.
- `kit/crawl/GeneratedCrawl.java`: at plan 3 every way but the Lord's is lead tunnel, a beat line, tail tunnel (its own
  MOVEMENT leg and Course); a chasm leg is a gulf line; a shaft leg is a Well; dens get mantle plinths and a raised shelf;
  a stand gets plinths, a slide gap and a run wall; holds, perches, the chase; the bench hall (`beat_hall` param).
- The 17 skins: `beatSkin(M)` and their accents (`accents()`).
- `RealmPlan.CURRENT_VERSION` 3; `Realms.attach` gives 3 only with `gateBeats` and the Runner's Kit on, else 2;
  `Realms.openLandOff` compares to 2 (the open-land fix stays tied to plan 2).
- `AwakenConfig`: `gateBeats` (default true).
- `RealmRun.course`: free at E and D, half for a player on Gentle, catch below `catchY`, ledges per beat.
- `RealmLive`: holds wake at 12, perches, the chase; lessons; clean lines.
- `RealmPacks.hold(...)`; `MobHooks.spawnMarksman` (default: an ordinary themed one; MobsSpawner picks a RANGED normal).
- No knock-off: `LivingKnockBackEvent` cancelled for a player inside a movement Course of a Realm.
- `GateMemory`: record D (`taught` families), schema 4, reads 3.
- `GateCommands`: `/awaken gate beats <kind> [rank]` (the bench hall), `realm_line <n> <back> <up>` for tours.
- Every other level: the Broodwarren's ways (hand-placed lines); designed realms' ways get outdoor beat spots
  (`OutdoorBeats`: mantle rocks, a slide under a fallen trunk, a run along a cliff face), placed by hand, plan 3 only;
  rolled open lands get them too, off with the open lands. The Leaper's Gauntlet is untouched.

## Order
1. Land records, Sculpt, Line, BeatSkin, the 24 beats, MoveProof, WalkCheck moves.
2. Composer, Well, GeneratedCrawl at plan 3, version, config, fallback.
3. Holds, perches, chase, rooms and stands, no knock-off.
4. Teaching, flow, fall cost, GateMemory.
5. Bench hall and command; Broodwarren; designed realms; outdoor beats.
6. Tests; push; `tools/check-ci.sh compile`, then named tests; CI tours of every kind and the bench hall; seed list.
