# The Run: build log and standing orders (the orchestrator's own working file)

## Approval (Euan, 10 Nov, about 15:40 local)

"let's go ahead with the entire plan, as you said it here." So every decision on the page stands at my pick: option B (the Run,
with the switch-on as its first stage); proof = The Hour Crypt as a run plus one short new dungeon crawl; mix 3 in 10 short, half
standard, 1 in 6 great; pressure on; as hard as the old gates at the same rank; use each theme's existing monsters and bosses.

His conditions and working orders, in his words:
- "we already have a ton of mobs... most of the mobs have already been put through a lot of testing and a lot of improving in terms
  of their animation... the boss fights are already really, really good... we need to use those bosses inside of these gates."
- "As long as there's a ton of different variations, so I never feel like I'm repeating the same gates. And then the immersion is
  truly there, so each gate feels like its own atmosphere. Even if it's a smaller dungeon where it's multiple hallways and stuff
  that go to one place, but it's still a very well-built dungeon that has atmosphere, that goes up, that goes down, that has good
  fighting... Sometimes we have these amazing great worlds. Sometimes we're underground. Sometimes we're in caves... you get creative."
- "So go ahead and build this. If you need to give some stuff to sub agents to make it go much faster, that'd be decent, but
  ideally do all the work yourself right now, and then we'll see just how fast things are going."
- "Try to avoid so many tests and builds and tests and builds. Focus on the actual coding, because that's what you're good at...
  trust yourself more... stop relying on the tests as a way to catch mistakes, and start thinking of everything beforehand."

How I apply that: I do the work myself in this session. Read the code I'm about to change once, design the whole change, write it,
then ONE compile and ONE targeted test run per stage, not per edit. The bench (seconds) is for pictures and floats at the end of a
gate, not a loop. The three builder sessions stay idle unless a stage is clearly parallel work; say so to Euan if I use them.

## Stages (from the approved page)

0. **Switch on.** In `gates/realms/RealmLive.java` (`wake`, `den`, the WARDEN case): packs wait in view at their den (use
   `gates/run/Encounters` and `Rooms.spawn` so they idle, wake and turn as in the old gates), each warden gets its own pack and a
   ring that seals while the fight is on, and `gates/run/Pressure` runs in a Realm. Check the four unknowns in `AUDIT.md`.
   Tours must stop setting `difficulty peaceful` for at least one walk so shots have monsters in them.
1. **Legs.** A `route` in the sheet and in data (legs: id, kind, where, monsters, how they wait, pay share, exits); leg parts for
   crawls (tunnel, stair, cave, trap room, arena) as kit pieces a sheet places by name; Gauntlet / course pieces usable in a Realm;
   bench checks: movement legs doable with E-rank moves, fight space big enough, no leg without a job, first fight within 90 s of
   the Mouth, no walk over a minute, at least one loop, a crawl changes height by 20+.
2. **Two gates to play.** The Hour Crypt as the twelve-leg run sketched on the page; one short new dungeon crawl from parts
   (candidate: The Broodwarren, gate 14, Dungeon, Hive: "rooms open only while their larvae are asleep; a nursery of hanging cells;
   brood chamber"). Then Euan plays both.
3. **Roll out.** Routes for the Quiet Barrow, the Wax Galleries; then the E wave in the mix.

## What I know of the code (see `AUDIT.md` for the table)

- `RealmLive.wake(server, level, g, plan, ref, lx, lz)` runs twice a second per player in a Realm; it ticks each gate's rule
  (`RealmToll`, `RealmRoots`, `RealmSalt`, `RealmHush`, `RealmWax`), then loops `land.sites()`: DEN within 56 blocks, once ->
  `den()` (3 + rank/2 + 0..1 monsters at random spots within 6, `Rooms.spawn(level, g, f, -1, role, pos)`); WARDEN within 56, once
  -> one `Rooms.spawn(..., MobRole.Role.WARDEN, at)` tagged with its site index. `mobDied` banks a sixth per warden and opens the
  heart when all are down. `heart(...)` wakes the Lord when someone comes in and runs the hall's changes.
- Pay: `RealmLive.pool` (sum of `Runs.floorReward`), `units` = 5 per den + 4 per warden.
- State per gate: `RealmState` (`woken` set, `wardensDown`, `heartOpen`, rule fields), saved in `RealmData`.
- Lands: `kit/SheetLand` on a `blockout/Blockout` (new gates); `HourCrypt` is its own class with rooms, six toll doors, `floorBreak`.
- Tools: `tools/bench/bench.sh check <tour>`; `tools/tour-ci.sh <tour> 1500 LENS=cinematic RD=28` (6 minutes, GitHub);
  `awaken gate realm_enter E <id>` + `need realm` in tours.

## Log

- 15:45 approved; starting stage 0.
- 16:20 stage 0 written in one pass, no intermediate builds: new `gates/run/RealmPacks.java` (den, warden, notice, fighting,
  wave); `Encounters.holdFree/releaseFree`; `RealmLive` (WAKE 80, `RealmPacks.den/warden` in `wake`, `notice` + `seal` +
  `pressure` in `tickPlayer`, `madeHeadway` in `mobDied`, units 5 per den and 7 per warden); tour
  `tools/autopilot/realm-hour-packs.txt` (normal difficulty, creative camera so packs stay waiting).
  One background chain is compiling, running `realm_hour,realm_enter,realm_quiet_barrow_walks`, then committing, pushing and
  walking the packs tour on GitHub (log: scratchpad `stage0-build.log`, `ci-packs.log`). If it says STAGE0 NOT GREEN, fix the
  named error and rerun the same chain once.
  Not checked: no GameTest spawns a pack (it needs a live Realm); the seal and pressure have never run. Euan's play is the test.

- 17:00 stage 0 green first time: 6 tests, and the real-game walk (run 42780890866, all shots inside): packs stand waiting in
  the nave and the Ossuary, each Warden on its plate with its company. One fault seen: nothing stood in the Sunk Aisle, because
  a pack's spot had to be dry. Fixed in stage 1 (a monster may stand wading one deep).
- 17:10 to 19:30 stages 1 and 2 written in one pass, no intermediate builds. Decisions taken while reading:
  - The old rig (`RigDirector`, the Gauntlet) is tied to a floor's room grid and its own state per room. Not ported. Movement
    legs are plain geometry a sprint clears, with a fall catch (`Land.Course`); traps are `RealmTraps` (press, spit, crumble),
    stateless on the Realm's clock, hurting exactly as the Gauntlet does.
  - Each theme has three or four ordinary monsters, one elite, one Warden kind and one Lord. "Different herds" therefore come
    from a leg naming its pack (`Rooms.signature` spawns by id), not from new creatures.
  - Doors: a leg's `seal_`/`unseal_` moments; which are shut is saved (`RealmState.sealed`) so a restart can't strand a door.
  - Pay: kill share untouched; hidden legs hold 15% of the rest, Wardens 40% beside one (50% without), the Lord the remainder.
  - `crypt_bell_rope` is climbable now (tag, merge folder `gates_run`): the Crypt's shortcut is a rope from the bridge.
  - CORRECTED 10 Nov evening: I wrote here that blocks in a gate can be broken by hand. Wrong. `GateEvents.onBreak`,
    `onPlace`, `onUseItem` and `onExplode` already protect every gate, Realms included (`GatesRegistry.isGateDim` counts the
    Realms dimension, `GateService.inGate` counts a Realm's cell). My search for it had missed the file. Euan was asked
    whether to protect them and said yes; nothing needed changing. Test `realm_blocks_cannot_be_dug` now proves it for a Realm.
  New: `Land.Leg/Trap/Course`, `RealmRun`, `RealmTraps`, `check/RouteCheck`, `kit/crawl/CrawlLand`, `land/Broodwarren`, the
  Brood block family (13 blocks), SPEC 50.9. Changed: `RealmPacks`, `RealmLive`, `RealmToll`, `WalkCheck`, `Bench`, `HourCrypt`.

- Stages 1 and 2 compiled first time; 44 tests green; both gates clean on the bench in 3 to 6 s (commit 54cbb8c4c). One look
  round on the Broodwarren (lit comb read as pale boxes, walls were all comb, two cameras behind pillars).
- **Euan, 10 Nov, going out for two hours:** "don't wait on me... surprise me, focus on coding more than testing and trust
  yourself to build something incredible... so the gate experiences and progression are INCREDIBLE and IMMERSIVE and SO FUN to
  play with custom mobs, insanely good animations on par with fresh animations resource pack... ideally without needing to do it
  all one by one."
  What I did with that, in order:
  1. **The mod's own-bodied creatures in the packs** (4c4475068, then the spawn fix below). Drip Lurkers, Hushlings and the
     Thousandfoot already have their own bodies and the motion work of SPEC 23; a pack can name them. I did not build new
     creature bodies or animation: that cannot be judged without watching it move, and the creatures plan owns it.
  2. **A cache per herd** (same commit).
  3. **Rolled crawls** (747a419a2): `kit/crawl/GeneratedCrawl` and two skins, The Lower Combs and The Bone Roads. Brood passed
     six seeds first time; the catacombs failed seed 1 because my candle rule took a stair's next step for a wall and put
     candles (which have a body) across ledges and narrow ways. Rule now: a wall is two solid cells high.
  4. **Three more skins from the three idle builders**, briefed from the generator: The Root Cellars (Fane, branch
     `claude/skin-roots`), The Hollow Hills (Barrow, `claude/skin-barrow`), The Tallow Runs (Wax, `claude/skin-wax`). One file
     each plus a blueprint, a program line and a tour; bench only.
  5. **The real-game walk of the Broodwarren** (run 42789486172, all 13 shots inside) showed named packs were not spawning
     who they named: `MobHooks.spawnCreature` only makes a Realm's peaceful life, so every name fell back to a roll from the
     theme. Added `MobHooks.spawnNamed` (any creature by id, in its tier's role, a monster of the gate) and a test.
  6. All three builder skins came back clean on three bench seeds each (Hollow Hills in 9 minutes) and were merged
     (bf8b07601, 4c2619c16, 3293dd5c2; each merge conflicted on the one line all three add to `LandPrograms`). Herds by theme
     for the six older lands (`gates/run/Herds`), the run line on the HUD (`RealmLive.hint`), and `PLAY.md` for Euan.
  7. Real-game walks: the Crypt's run (42789489026: packs in the nave and Ossuary, monsters wading in the Sunk Aisle, a row of
     teeth up across the gallery, the reliquary's door shut, the break in the tower stair). The Broodwarren with named
     monsters (42794222878): the Thousandfoot in the comb ring with its guard, Lurkers in the larder (on the floor in the shot,
     not yet hung), and in the played moment the larder's ambush sprang, its sacs burst and the skin sealed the way out.
- **Euan, 10 Nov evening:** the rolled dungeons "look great, actually... maybe 20 or 30... some go up and down more, some
  have a different style... sections where you have to do some parkour... the bigger, more intense ones from time to time".
  Done for that: sizes and shapes in the generator, the chasm, and a skin per gate theme from the builders in rounds of three
  (round 1: Cinder Workings, Sunken Stacks, Sporewells). Two faults the wider tests caught: a skin's roots grew across the
  chasm (the generator now clears every chasm's crossing), and a shelf held on to a thing drawn only in a chunk's margin and
  floated (`adorn` is now told only about the dungeon's own rock).
- **Euan, same evening, on the monsters:** "all the mobs are not custom... I basically want like really high quality mobs."
  Counted: 95 of the 100 gate monsters wear a re-tinted vanilla body. My answer: the gates need their own bodies, built as a
  few animated families with per-theme bodies, proved on one theme with clips first; a card proposes a session to plan it
  by his process (not started by me).
- **10 Nov, about 20:20 local: fourteen kinds, three more with the builders.** Round 2 (Frozen Cells, Glass Warrens, Stopped
  Works) and round 3 (Silvered Halls, Sealed Wing, Sleepless Ward) are merged; round 4 is briefed (The Unlit Stair for the
  Abyssal Shrine on `claude/skin-abyss`, The Rime Hollows as a second Frost kind on `claude/skin-rime`, The Green Dark as a
  second Overgrown kind on `claude/skin-green`). After those every theme that suits a crawl has a kind; Sky Archipelago
  (fliers, open sky) and the Night Market (neutral monsters, a Lord who trades) do not suit one as the generator stands.
  What the real-game walks of rounds 1 and 2 showed, and what changed:
  - The Sunken Stacks, The Cinder Workings and The Stopped Works read well. The Glass Warrens glowed over half its walls (less
    now). The Sporewells was a chequer of five blocks; rewritten to the rule now in SPEC 50.9 ("a wall is one thing with
    things in it"), and briefs carry that rule.
  - A great run's chasm was near black, and two skins lit no shaft at all. The generator now lights every climb itself.
  - A layout comes from the seed alone, whatever the kind, so the test had only ever tried eight layouts. It tries 86 now
    (seeds 1 and 2 for every kind, six more that no other kind uses), and all pass. Seed 1 is a great delve with a shaft at
    about -33 181 78 and a chasm at -184 124 189 in every kind; seed 2 is a short plunge.
  - The Lord's camera in a rolled kind's tour stands eight blocks from the middle (inside the pillar ring).
- Still true and not fixed: nothing has been played by a person. Ambush, doors, traps, the fall catch, hidden pay and the
  shortcut have only been compiled, unit-checked and (ambush, hidden) touched by a tour.

- 11 Nov, about 02:10 local. The first real-game walk with depth, air and the gate monsters in it (The Stopped Works, run
  42875088993) read like this: rooms and monsters good, but the fog too far out to see indoors, nothing in a room's air, and
  the long walls of the gulf and the stairwell dead flat. Fixed in three commits: every kind's fog nearer and its haze
  thicker (`kinds_air.py`); a well of light in every room with a beam down it, and mist on the biggest floors
  (`GeneratedCrawl.wells`, `airOf`); bays or scoops cut one cell back into gulf and stairwell walls (`Chasm.walls`,
  `Shaft.walls`), never beside anything to stand on. The offline bench on five kinds: nothing floats, every site reached.
  All seventeen kinds walked again on that tip (tag w2).
  - The bench's picture maker has flat light, so bays barely show in its pictures; judge them in the real game's.
  - The bench's "settled: N in the open" line is the kit tidying away a few hanging things whose roof block is not one it
    counts as holding (a lamp, a glass). It is not a fault and was there before this pass.

- 11 Nov, about 03:25 local. The first two walks with wells in them (The Root Cellars 42882402964, The Lower Combs
  42882379885) showed a lit slot in each roof and nothing under it. Two causes, both fixed in `868404dfc`:
  - A light at the roof of a tall room never reaches its floor. A well now has unseen lights down its middle.
  - **Nothing in Lens reads Gate Air in this build.** A land's shafts, mist layers and lights reach the client and only its
    small life uses them (motes, moths, wisps over water). SPEC 38.3 says Lens marches them; no code does. So a beam is now
    glowing wisps (`beams`) and room mist is wisps on the floor (`floor_mist`), both new small-life kinds in
    `gates/client/WorldAir`. If the shader work wants real beams in gates, the data is already sent.
  - The Sporewells' room walls had too many lit brackets once rooms had lumps and piers; halved (`ee482d836`).
  - One shot of seventy (The Sunken Stacks' gallery, run 42875164141) has four blank squares on a 400 pixel screen grid:
    Lens's tiled passes on the CI runner, not the world. Passed to the manager; nothing here touches Lens.
  - A kind whose seed rolls one fight or no trap repeats a shot in its tour (The Cinder Workings on 383). Harmless.

- 11 Nov, about 05:15 local. **Beams and floor mist as sprites: tried three ways, all taken out** (`302155533`).
  1. Faint wisps: nothing drew. Minecraft's particle shader throws away anything under a tenth opaque. (It also took a
     round to find that the client stops at 260 sprites and takes a land's list in order, so what is late in the list of
     a kind full of fireflies is never reached. Shaft dust is first in every list now.)
  2. Upright bars and wisps just over a tenth, 16 pixel sprites: sheets of glass with stepped edges, a veil when one
     stood on you (run 42904471985).
  3. Fine-grained 64 pixel sprites: patches of static (run 42909997245).
  What stays: unseen lights down each well, so the pool reaches the floor; shaft dust's fairer share; the rolled kinds'
  tours wait ten seconds before each shot so the air has filled. A real beam is Lens marching the shafts it is already
  sent, or a renderer of the gates' own with its own pipeline: Euan's call, on the page.
- The same morning, all seventeen kinds were read on the final geometry (tag w4, runs 42896117252 to 42896313750, 187
  shots, no tour errors): no broken builds. Dimmest: the crossings of The Hollow Hills, The Green Dark and The Unlit
  Stair. Camera faults, not world faults: the ambush shots of The Green Dark and The Hollow Hills and the Warden shot of
  The Tallow Runs stand with a rock or pillar in their face.
- The results page is version 9; its second half is built from `page/` (see `page/README.md`).

## Stage 1 design (as first thought through; what was built is in SPEC 50.9)

- **Legs are data on the land.** `Land.Leg(String id, Kind kind, int x, int y, int z, int radius, int pack, List<String> exits)`,
  kinds ARRIVE, FIGHT, AMBUSH, MOVEMENT, TRAP, WARDEN, BREATHER, SHORTCUT, LORD, HIDDEN. `Land.route()` has a default that derives
  legs from sites (each DEN a FIGHT, each WARDEN a WARDEN, the LORD), so every land already has a route; a `SheetLand` reads
  `"route": [...]` from its blockout JSON; `HourCrypt` overrides with the twelve legs on the approval page.
- **What runs each kind.** FIGHT and WARDEN are today's DEN and WARDEN sites through `RealmPacks`. AMBUSH: when a gate's rule fires
  (`RealmToll`, `RealmHush`...) and a player stands inside an AMBUSH leg, `RealmPacks.wave` there, awake. MOVEMENT and TRAP need the
  rig (`RigDirector`, `RigGauntlet`) or `lucidium/trial/CourseKit` in a Realm: first read whether `RigDirector` needs a floor room
  grid (`RigState` is per floor); if it does, build movement legs as plain geometry proven by `CourseKit.solve`, and traps as
  timed block flips through `Land.change(moment)` like the toll doors (presses = a slab column that drops on a beat).
- **Pay by leg.** Starting split: packs 45%, wardens 25%, Lord 20%, hidden 10%; today's `units` already spreads kill pay.
- **Bench route checks** (in `realms/bench/Bench.java`): every leg reached (legs join the sites `WalkCheck` walks to); the exits
  graph has a cycle; consecutive legs no more than 260 blocks apart; a crawl's legs span 20+ in y; each FIGHT and WARDEN leg has at
  least 12 standable cells per monster inside its radius; no leg without a kind.
- **Crawl parts** (kit pieces a sheet places by name): Tunnel (from, to, width, height, ribs), Stair (rise over run), CaveRoom
  (blob with a floor), TrapRoom, Arena (round, cover ring at 0.55 of the radius as `PiecesArenas` does).
- **The short crawl:** The Broodwarren (gate 14, Dungeon, Hive; "rooms open only while their larvae are asleep; a nursery of
  hanging cells; brood chamber"). About five legs, 100 across, 30 down.
