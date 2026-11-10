# The Run: audit (10 Nov 2026). [seen] = read in the code today; [check] = still to verify in stage 0.

## The old gates already hold the play layer [seen]

| System | Where | What it does |
|---|---|---|
| Waiting packs | `gates/run/Encounters.java`, `Rooms.java` | Monsters wait where you can see them, idle, wake when you come in, turn toward you, then fight. Arenas seal until empty; the Lord's seals until it falls; shrines open when a floor's condition is met |
| Roster of who is alive where | `gates/run/GateMobs.java` | Room clears, arena waves, the entity budget, kill XP |
| Pressure | `gates/run/Pressure.java` | After 4 minutes on a floor a wave every 45 s, each bigger; at 8 minutes the theme's Warden arrives |
| Loot | `gates/run/Loot.java`, `Spoils.java` | By rank and theme: essence, cores, parts, Hearts, gear; the Satchel |
| Layouts | `gates/content/Layout.java` | LABYRINTH, SPIRAL_TOWER, SHAFT, HUB_AND_SPOKES, ARENA_CHAIN, STEALTH_HALLS, PUZZLE_VAULT, OPEN_CAVERN |
| Twists and modifiers | `Twist.java`, `Modifier.java` | 8 twists (Double, Red, Hollow, Treasure...), 14 modifiers (Lightless, Low Gravity, Swarm, Elite Pack, Flooding, Collapse Timer...) |
| Living rooms (the Gate Rig) | `gates/world/RigRoom.java`, `RigGauntlet.java`, `gates/run/RigDirector.java` | GAUNTLET (gaps, ledges that crack and fall, presses that slam on a beat, a wall to run along over the drop, checkpoint rings, a clock and medals), COLLAPSE_RUN, MIRROR_HALL, GRAVITY_WELLS |
| Movement courses | `lucidium/trial/CourseKit.java` | Lays a course piece by piece and proves it with the body's own probes (`RunnerRules`): a wall you're meant to run really is runnable |
| Arenas | `gates/world/PiecesArenas.java` | One per Lord, built for its moves, cover at 0.55 of the radius |
| Engines | `gates/world/` (27,565 lines) | `PassageEngine`, `ShaftEngine`, `CaveEngine`, `HallEngine`, `FloorBuilder`, `PiecesRooms` |
| Mobs | `mobs/def/GateBestiary.java`, `RealmBestiary.java`, `Rosters.java` | A roster per theme (`Rosters.forTheme`), roles NORMAL, ELITE, WARDEN; 47 fights in `data/awaken/kit/fights` |

## What a new gate (a Realm) uses of that [seen]

- `RealmLife`: peaceful herds, flies and folk only.
- `RealmLive.wake`: a DEN site, once, when a player comes within 56 blocks: 3 to 5 monsters (`3 + rank/2 + 0..1`), the first an
  elite when there are four, each at a random spot within 6 blocks of the site. A WARDEN site: one warden-role monster at the point.
  All wardens down opens the Lord's door; each banks a sixth of the non-kill pay.
- Pay: `RealmLive.pool` = what the same gate's floors would have paid (`Runs.floorReward`), split into shares of 5 per den and 4 per
  warden; `Curve.gatePay` E 2, D 3, C 4, B 2, A/S 1.25; 60% lands on kills, 40% rides in the Satchel.
- Nothing waits visibly, nothing is placed to the place, no room seals, no pressure, no rig rooms, no traps, no route.
- Every tour runs `difficulty peaceful`, so no shot has ever had a monster in it.

## Why the old gates felt wrong (measured 9 Nov, `docs/gates-as-worlds/PLAN.md`)

66% of walking in passages 7 wide or less; 21% of doors walked twice; median sight 6 blocks; height change along the way 3 blocks.
The layouts were trees with dead ends. So the crawl comes back with loops, height and shortcuts, not as those layouts.

## Still to check [check]

- How many different monsters each theme's roster really has, and which read as "custom" by eye.
- Whether a rig room (`RigDirector`, `RigState`) can run in a Realm, which has no floor room grid.
- Which traps exist besides the Gauntlet's presses and cracking ledges.
- What movement an E-rank player has, so a movement leg never asks for more.
- Levels gained in one new gate at level: run `tools/sim/pacing_sim.py` with a Realm's shares.
