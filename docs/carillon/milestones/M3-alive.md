# Milestone 3 · Alive (Citizens builder)

Branch `carillon/m3-alive`, worktree /Volumes/MEDIA/Awaken-alive. Brief: docs/carillon/briefs/M3-alive.md. Date
2026-10-25, Apple M4, load 4-9 most of the session (other sessions' builds). Code in `dev.awaken.citizens`
(+ `.client`), one line each in Awaken/AwakenClient, one core change (below).

## What's built

- **Roster as data** (Sonnet 1, `tools/city/citizens.py`, brief M3-sonnet-1.md): `data/awaken/city/citizens.json`
  (schemaVersion 1, pinned to plan.json's sha256). 240 citizens in 96 households, 47 workplace groups, 14 taverns,
  20 jobs (the twelve plus shopkeeper, tavernkeeper, clerk, archivist, healer, miller, child, resident) and 8 thieves
  kept apart. Oren the smith, Mira the Watch scout, Teodor the archivist and Sun the healer at the infirmary are named records.
  501 places (doors, stalls, lamps, posts, spots), patrol and lamp routes, keep-right lane offsets per edge, a seeded
  schedule each on the vanilla clock, checked feasible worst case by the generator.
- **Ledger** (SavedData `awaken:citizens`, schemaVersion 1): struct-of-arrays, a twentieth of the records per tick,
  abstract walking on the street graph with lazily baked next-hop columns (≤ 3 Dijkstra bakes a tick), leaving
  early by distance, reserved spots, clock-jump reseating, role overrides for events and masons, world state (lamps,
  stalls, stock, laundry, safety, gratitude, repairs), memories per player. Loads back identically (test).
- **Bodies** on `SkinnedMobRenderer`: spawn at 56 out of doors (the door opens), removed past 64, ≤ 40 a player and
  96 a world, never saved, 16-tick fade in and 8 out, keep-right lanes, rejoin node, vanilla path tokens, stuck →
  path → rescue unseen (or after 15 s in view). Hurt, never killed: they limp to the infirmary. 244 skins from
  `tools/art/specs/citizens.py` (Sonnet 2, M3-sonnet-2.md): base, job clothes, hair or hat, palettes, so no two in a
  frame look alike (docs/carillon/milestones/m3/citizens_front.jpg, _back.jpg). Job clips from
  `tools/art/specs/citizen_clips.py` (body_moves' figure and bake), 32 clips: hammer, trowel, scrub, hang, haul,
  carry_back, set_down, shutter, serve, bell_pull, lamp_light, proclaim, halberd, bucket_pass, thief_run, point_shout,
  hurt, wave, nod, talk and idle stances, crossfaded on our own model.
- **The jobs, with visible work.** Lamplighters run their rounds with a pole: our street lamp (lit, out, broken)
  replaces M1's 78 lanterns and the avenues go dark at dawn and light at dusk; a broken lamp left dark lets monsters
  spawn near it at night (the only natural spawns inside the wall). Bellringers ring the hour on the six bells (one
  peal, the hour's count; alarm on events). Porters walk crates from the Quay's stacks to the stalls (stock). Stall-
  keepers open spruce shutters and lay wares at 7, shut at dusk. Bakers' chimneys smoke at dawn, forge chimneys all day,
  smiths hammer at real anvils with sparks. The Watch patrols routes, with lanterns at night that light the street
  (moving light blocks, ≤ 12), and calls the hour. Criers read the day's news (events, the player's deeds). Washers
  hang and take in laundry (seven fill states), masons repair damage block by block. Boatmen work as quay hands (HAUL):
  boats on canals wait for Detail. Rooftop runners are street couriers until the Ascent lands.
- **Events**, seeded per play-day: burglary (85 %, the thief flees through the streets with a bag, the Watch gives
  chase, the bag can be returned), pickpocket (0-2), fire in the Works (20 %, flames on a roof, a bucket line from
  the nearest water, masons repair), runaway cart (14 %, a jam closes edges), gate tremor (33 % with a D-rank player
  in the city: a wandering gate outside the wall, the Watch sent out). Each resolves with or without the player;
  consequences only in world state; clock jumps never fire planned events.
- **Recognition**: witnessing in line of sight within 24 blocks, gossip once a play-day (household, group, tavern),
  tiers Stranger, Familiar face, Known; at Known people greet you by name. 241 authored lines from `tools/city/lines.py`
  (greetings per job and tier, named lines, calls, news, event lines), all seeded. `CitizenHooks` installed, and a
  minimal `RenownHooks` (city tier from the citizens' memories) until the renown module lands.
- **Commands**: `/awaken citizens info|who|show <job|name|stall|event|lamps|laundry|crowd_<district>>|event|renown|
  greet|bodies|cap|window`.
- **GameTests** (`-Ptests=citizens_`, 4 pass): `citizens_day` (two play-days, every record reaches every target),
  `citizens_stations` (every station reachable), `citizens_events` (every event resolves, no thief stuck),
  `citizens_ledger_roundtrip`.

## Numbers

Server, from the in-game rolling log (`[citizens] last 1200t`, and a timing window over the busiest scene):

| | budget | measured |
|---|---|---|
| ledger (240 records + 8 thieves), tour minutes | ≤ 0.5 ms/tick | p50 0.005-0.012, p95 0.019-0.06 ms (p95 0.3 in the minute a new world bakes its columns) |
| bodies, noon square, 40 bodies, load 4.4-5.4 | ≤ 1.5 ms/tick | p50 0.358, p95 0.590, p99 0.752 ms (other runs: 0.339 / 0.596 / 1.057 and 0.354 / 0.654 / 1.005) |
| bodies, whole tour minutes | | p95 0.18-0.47 ms, p99 ≤ 1.2 ms; the odd spike is a chunk load or GC tick |
| next-hop columns | | 221-354 baked a session, ~0.2 ms each, ≤ 3 a tick |
| GameTest day | | two play-days for all records in 67-170 ms |

`mspt` during the window p50 2.4, p95 3.2 ms with the client's integrated server. The frame cost of 40 citizens
was not measured (the brief leaves it to the quiet pass in Detail).

CI (full GameTest suite): **green on 4701a6d** (run 40135078966, all 401 required tests). Earlier green on 374a563;
the two pushes between failed `gates_hooks_view_stabilise_calm` ("no dry ground"): the events test's borrowed city
had furnished stalls, lines, anvils and crates over neighbouring tests' ground in the shared test world. Fixed in
4701a6d (the test's city changes no blocks; a broken lamp only ever replaces a lamp).

## Shots

Final tour: `tools/autopilot/carillon-m3.txt` (one `slot.sh light` run, ~8 min, world seed 20261024 as M1's,
pack perf+world, Tectonic awakened). Full size (gitignored): /Volumes/MEDIA/Awaken-alive/run-autopilot/screenshots/
(56 shots: dawn, noon, dusk and night in all seven districts, jobs, events, greetings). Curated 800-px copies in
docs/carillon/milestones/m3/:

- the day: m3_dawn_sky, m3_dawn_lamplighter, m3_dawn_quay, m3_noon_square, m3_noon_market, m3_noon_quay,
  m3_noon_works, m3_noon_wallring, m3_dusk_quay, m3_night_garden, m3_night_sky;
- a stall opening as the clock runs: m3_stall_before, m3_stall_opening, m3_stall_open;
- jobs: m3_job_porter (crates on their backs from the Quay), m3_job_smith ("There. Good iron." at the anvil),
  m3_job_crier, m3_job_washer, m3_night_watch and m3_night_quay (the Watch with lanterns calling the hour);
- the lamplighter's avenue: m3_lamps_before, m3_dusk_lamplighter, m3_lamps_after;
- events: m3_noon_pickpocket ("Pickpocket!"), m3_fire_line and m3_fire_later (flames on a Works roof, the bucket
  line in the street), m3_dusk_burglary and m3_dusk_chase (the hooded thief running with the bag);
- a name said back: m3_greet_oren, m3_greet_stallkeeper;
- skins: citizens_front.jpg, citizens_back.jpg (all 244 generated skins by job, front and back).

## The living town, honestly

The market quarter at noon passes: open stalls with keepers behind them, shoppers browsing, the crier in scarlet,
porters walking crates up from the Quay, the smith hammering sparks at a real anvil, and when you've been around,
someone says your name. Night passes on the lamp lines: the avenues go dark at dawn and light at dusk because a
person lit them, and the Watch comes by with a lantern that lights the street, calling "11 o'clock, and all's well!".
The events read without help: a cry of "Pickpocket!" at a stall, flames on a Works roof with a bucket line under
them, a hooded thief running through the Works at dusk with the Watch after him.

Away from the market it is a town with people in it rather than a living town. 240 records over 779 plots leave
Bellwatch, the Garden, the Wallring and the Terrace at one to three people per street at noon, and nearly empty at
night beyond the lamps (no lit windows yet). People vanish into doors with nothing behind them, wares and laundry
are vanilla stand-ins, there is no crowd murmur, and speech is small at screenshot scale. Density (400-480 records),
lit windows and the kit are what close the gap, not more systems: the ledger and bodies have the headroom.

## Decisions taken (SPEC §16 proposals for the Carillon session)

- **Spawning**: inside the wall, natural monster spawns only within reach of a dark (broken or unlit) street lamp at
  night; everywhere else in the city they are refused. This makes "a broken lamp can spawn mobs" true and keeps the
  rest of the city calm.
- **Recognition numbers** (provisional, for tuning in play): personal Familiar at 20 and Known at 55 points; the city's
  tier from how many people know you (30 who find you familiar, 24 who know you). Only being seen about stops at 35
  (familiar, never known); deeds and gossip go further. Gossip carries 0.5 through a household, 0.34 through a
  workplace, 0.25 through a tavern, one step a play-day.
- **RenownHooks** is installed by citizens as a minimal reader of the city's memories until W5's renown lands; the
  interface is the one in §12.
- **Core**: `SkinnedMobRenderer` got a protected constructor taking the wide and slim models, so a module can pose its
  own `SkinModel` subclass (job clips play on our model; PAL only animates players).
- **Boatmen** are quay hands (hauling, loading) for now; boats on the canals go to Detail. **Rooftop runners** run
  street errands until the Ascent's moves land. **Lamplighters** run their rounds (they're the ones in a hurry at
  dusk).
- **Density**: 240 records leave a 480-block city thin away from the market at noon (market ~76 out, Bellwatch ~12,
  Garden ~6, Wallring ~7). Propose 400-480 records for Detail; the ledger has the headroom (p95 < 0.06 ms at 248).
- **Tremor** opens a wandering gate through `GateHooks.openWandering` outside the wall; the gate id is not saved with
  the event (a reload mid-tremor ends it quietly).
- **M1 quirk**: 46 house plots have doors whose graph node hangs off another door; the roster leaves them empty
  (fixable in plan.py).
- **Moving light**: the Watch's lanterns place light blocks (level 11, at most 12 at once, tracked and cleared
  as chunks load after a stop), so night patrols really light the street.
- **Bell Festival** is not built (not in this brief).

## Gaps

- **M2**: rooftop runners and rooftop chases (the thief's escape over the roofs, the Watch on ladders) once the Ascent
  lands; the Bell Bridge break closing graph edges (StreetNet can close edges, as the cart jam does); the opening's
  walk past working citizens and the Registry; the infirmary interior (the hurt walk in and come out healed); Lens
  Carillon light at night.
- **M4**: lit windows and more lamps beyond the avenues (night scenes are dark away from the lamp lines), stall wares
  and awnings in the kit, cloth laundry instead of carpet, interiors behind the doors people use, a crowd murmur
  ambience, boats on the canals, and the quiet frame measurement for 40 bodies.
- **Here**: some district framings still land on thin streets at dawn and night; the 240-record roster is thin outside
  the market (see Density). `citizens_events` sprints the server for ~9,000 ticks (about 1 s) to let every event run
  its course; other tests running then see their tick timeouts pass in wall-clock haste (gate builds waiting on chunk
  generation). A simulated clock for the events test would remove that; worth doing if a gate test flakes during it.
