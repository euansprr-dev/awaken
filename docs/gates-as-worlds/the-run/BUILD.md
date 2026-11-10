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
