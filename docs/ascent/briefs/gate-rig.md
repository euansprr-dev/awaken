# Brief: The Gate Rig and living rooms (the Ascent, Phase 3) — branch `pack/ascent-rig`

You build Phase 3 of the Ascent (docs/plan-ascent.html §7, "Gate rooms: the room is the puzzle") for AWAKEN, a NeoForge
26.3 mod. Read first, in order: docs/ascent/BRIEF.md (Euan's words and the 9-point "genuinely magical" bar: the
contract; he asked for "mirror rooms or gravity flips or collapsing stuff", chases and parkour gauntlets),
docs/plan-ascent.html §7, docs/ascent/AUDIT.md "Gates today", CLAUDE.md, docs/SPEC.md (§7 gates, §12 API, §17 the
Ascent), docs/HANDOFF-cloud.md (cloud rules, incl. Euan's token budget: few tests, build first). Start from
`origin/pack/ascent`, then merge `origin/build/awaken-v1`.

## The map (from a code survey; paths under src/main/java/dev/awaken/)
- Plan/version: gates/content/GatePlan.java (record; `CONTENT_VERSION = 3` :25; codec `content_version` :35;
  `rebuilt` :80). Stamped in Director.java:137, Gates.java:136,145, GateExterior.java:93, GateAccess.java:92;
  GateService.java:380-387 rebuilds unentered READY gates below the current version (decide: version 4 must not
  rebuild a gate someone already entered; old gates keep version 3 and their rooms).
- Generation: gates/world/FloorBuilder.java chooses room types per layout (:206-216 dispatch; labyrinth :219, spiral
  :261, hub :290, arenaChain :316, stealthHalls :329, puzzleVault :384, cavern :398; deadEnds :252; bossFloor :428;
  pieces() :543); Room.java types :13-31; RoomGeometry.java; Cells.java (floors at Y 200 − 32(f−1)); GatePainter.paint
  :42 (pure per chunk); Kit.java (theme dressing); Pieces*.java (set pieces; PiecesArenas one painter per theme).
  Layout cache: GateLayout.java:42 (`seed*31+contentVersion`).
- Runtime state: gates/FloorState.java (sets, maps, flags; codec :74; NO schemaVersion: add one when you add the rig
  state); GateRecord.java floors map :45; GateRegistry.SCHEMA = 2.
- The one puzzle today: Rooms.pressRune (gates/run/Rooms.java:447) + Puzzles.java (pure solvability).
- Machinery to reuse: story/Ritual.java (beat timelines that catch up after lag :288-551, plates :669-722, telegraphed
  gaze :758-781, pits that open/close with blocks restored from pure geometry :898-970), gates/RitualHall.java,
  mobs/entity/Telegraph.java; Modifiers.flood (gates/run/Modifiers.java:365-393: queued per-tick block placements saved
  in FloorState.water) is the pattern for staged block swaps; Collapse Timer (:293-348) changes no blocks today.
- Protection: gates/GateEvents.java fabric() :200-216 + onBreak :219; no place handler.
- Lords: mobs/entity/AwakenBoss.java checkPhase :60 → enterPhase :70-94; no external phase hook (add one).

## What to build (plan §7; numbers you decide go into SPEC §17.5 in the same commit)
1. **The Gate Rig**: a small layer under the painter. Pure geometry per room (like PiecesRitual) + a runtime timeline in
   `gates.run` (`Rig`): staged, saved-and-restored block swaps with smooth block-display animation on top (a collapse
   should read as falling stone, not blocks blinking out); triggers from plates, positions, a light beam reaching a
   block, runes, an encounter's state. Deterministic from the gate plan; survives a relog (FloorState rig state with a
   schemaVersion). Perf: spread swaps over ticks (≤ 3 ms/tick, SPEC header); no per-tick allocation.
2. **Archetypes, six first** (then Rising Tide, Toll Bridges, Lantern Dark): **Mirror Hall** (rotate mirrors to route a
   light beam to the seal; the beam burns mobs; drawn with real light, Lens bloom via its existing passes; also fix
   gate_mirror missing from the awaken:mirror tag so the spirit walks through), **Gravity Wells** (switch which way is
   down: walk the ceiling, fall "up"; stratum 7's tides in descent/rules/Zones.java:256-310 are the precedent),
   **Collapse Run** (the floor crumbles behind and under you on a beat; Collapse Timer finally drops real blocks),
   **Chase** (a Hoarder or thief flees along a route that needs movement; catch it before the exit), **Gauntlet**
   (timed crushers, collapsing ledges, wall-run walls; checkpoint rings, a timer and a medal; reuse hearth/training's
   rings and ghost), **Tether Vault** (an abyss with anchor rings; swing across while the guard fights you midair).
   Every archetype has a pure solvability check, run across 1,000 seeds in a GameTest; the generator rerolls a room
   that fails.
3. **Lords change their arenas** through the Rig: phase 2 cracks the floor into a collapse run, phase 3 flips gravity or
   floods (a phase hook on AwakenBoss).
4. **Movement-keyed gates**: once a player holds a tier (`dev.awaken.body.ascent.Ascent.has(p, tier)`), the Director
   starts rolling gates built around it (Sky gates: islands in the void; Tether gates: vertical shafts; Gauntlet gates:
   speed tests); the gate card says what it needs; you can see them before you can finish them.
5. **Ascension trials**, staged like the Ritual of the Bell (story/Ritual.java's beats): the Tether Vow (a short trial in
   the first vault you find; grants TETHER), the Leaper's Gauntlet (LEAPER), the Wingborn (WINGS, at a sky platform's
   core; coordinate with the Wilds' core via `Ascent.grant`). First times are scenes, not toasts (BRIEF bar 6).
6. **Gate rules fixed**: every painted block is protected, not just the palette; placing inside a gate is limited to
   the player's own constructs.
7. New rooms ship as **content version 4**; version 3 gates are unchanged (a GameTest proves it).

## The bar (BRIEF.md, short)
A player should stop in a room and work out how it works; each archetype's look and sound are its own (no vanilla
particles; synthesised sounds via tools/art/audiokit.py); smooth animation on block changes; 120 fps on the M4 in a full
gate fight. No mixins, no reflection.

## Rules
Commit small (end each message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`), push
`pack/ascent-rig` (CI green is the gate), one GameTest per mechanism (solvability ×1,000 seeds per archetype; v3
unchanged; the rig restores after a relog). Tours need the Mac: list them in docs/handoff/pack-ascent-rig.md with
what's done and left and the CI run id. Never push build/awaken-v1. Don't edit `dev.awaken.body.*` (the movement
session's); ask in your hand-off for any movement hook (e.g. a gravity-flip force on the body).
