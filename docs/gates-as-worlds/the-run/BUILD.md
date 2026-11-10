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
  - Blocks in a gate can be broken by hand (no protection found in `gates/`), so a skin or bars can be dug through. Not
    changed here; a follow-up for whoever owns gate rules.
  New: `Land.Leg/Trap/Course`, `RealmRun`, `RealmTraps`, `check/RouteCheck`, `kit/crawl/CrawlLand`, `land/Broodwarren`, the
  Brood block family (13 blocks), SPEC 50.9. Changed: `RealmPacks`, `RealmLive`, `RealmToll`, `WalkCheck`, `Bench`, `HourCrypt`.

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
