# Milestone 4 · B3a: street life (Street Life builder)

Branch `carillon/m4-b3a` (from carillon/main after B2, merged up to 7ad4f622), worktree /Volumes/MEDIA/Awaken-m4-b3a.
Brief: docs/carillon/briefs/M4-B3a-street-life.md; continued from docs/carillon/briefs/M4-B3a-continue.md after the first
builder handed back before compiling. Date 2026-10-25, Apple M4. Sonnet 1 drew the kit (briefs/M4-B3a-sonnet-1.md),
Sonnet 2 grew the roster (briefs/M4-B3a-sonnet-2.md).

## What changed

**The street-life layer, as plan data** (`tools/city/props.py`, called from plan.py; plan.json gains `props` and
`propFields`, crossings gain `hung`; hash re-pinned to 575dde8a…). 841 props: 34 stalls (24 in clusters of three on the
bowl's −2 ring between the eight winds' aisles, 10 on district plazas), 2 fountains either side of the Bell Stone, 17
wells, 197 lamps (a lamp at every junction but 9, at most 3 of the plan's lamps a chunk, never in a facade's plane), 16
hay carts (every bell tower's foot, under the Quay's hoists), 61 Market displays under the awnings, 60 benches, 53 carts,
197 door stacks (crates, sacks, casks, produce), 86 planters, 8 notice boards, 30 Works heaps, 10 nets, 6 tavern tables,
and 261 hang lines (139 laundry, 51 bunting, 42 lantern strings, 29 bare rope) across lanes on the grid (rows 9 and 13)
plus all 13 avenue crossing sites and 4 lantern strings over the canal. Street-level props keep the citizens'
keep-right lanes and every door's way in clear. Choir Square's street ends join its r 30 ring path, so the eight winds
are its only aisles.

**The street kit: 25 blocks** (`city.kit.StreetBlocks`/`StreetKit`; art `tools/art/specs/city_street.py`, 220 models,
221 textures, 44 animated; the mill wheel `tools/art/specs/city_wheel.py`): awning (6 colours × slope/upper/lower/flat),
sign bracket, signboard (14 trade icons, hanging or flat), hang line (rope, laundry, bunting, lanterns × 4 sets × 3
sags), line pole, banner cloth (8 district designs × wall or pole), flagpole, crate (6 contents), sack, wares (10 goods),
stall counter (open/shut), cask, cart (5 loads), hay cart, produce, goods shelf, bench, market table, planter, wall
lantern, notice board, fishing net, well windlass, mooring ring, wheel face. With B1's kit that is ~45 blocks, ~2,100
states. No block entities, no light sources; glows are emissive layers. Awnings bounce (0.75) and halve a fall; hay
carts take a fall as hay does (×0.2).

**Movement for free**: laundry, bunting and banners sway (8-frame animated textures, each set out of step); lanterns,
wall lanterns and the street lamps' glass flicker; the mill wheels turn (animated cells of the wheel's middle plane);
house smoke through the evening (one house in four keeps a fire all day); fountain spray; lit windows by time of day.

**On the facades** (`CityFacadeLife`): awnings replace B1's canopies over every shopfront and over the doors of shops and
taverns; trade signs hang from wrought-iron brackets at row 5, two and three out over streets 5+ wide (flat on the
fascia elsewhere), preferring a column with no window over it; long banners down the Terrace's formal fronts; flagpoles
with banners on the Market's tall houses; emissive wall lanterns by doors. Plots are indexed with a margin of 3 (the
bracket's end).

**The city's own touches**: paving wear by street rank (gutters at the facades, moss at lane walls, a worn middle line);
mooring rings on the quay walls; the High Gate's yard cleared of tree pieces once a server start (`CityYard`).

**Lit windows** (`CityLights`): each house has its own evening (on 17:36-19:12, off 21:12-01:00, upstairs last, a little
over half its rooms; night owls till dawn; early risers' kitchens). Window lists are found by painting a plot alone; the
nearest unknown house to a player every other tick (0.087 ms a house on average, 0.14 ms the worst, over 60 houses and
5,061 windows, logged by city_lit_windows: about 0.05 ms a tick until every list is known, so not worth baking into the
plan), three houses a tick brought to their wanted state. The lists are reused, not allocated per house.

**Citizens** (`tools/city/citizens.py` → `citizens.json`, 460 records, 841 places, 1,417 spots, 136 routes, 180
households): lamplighter 20 (197 lamps in 20 rounds), bellringer 7, porter 26, stallkeeper 48 (34 openers + 14 helpers on
the square), baker 10, smith 8, watch 32, crier 7, boatman 10, washer 14, mason 12, runner 11, shopkeeper 30, tavernkeeper
28 (14 + 14 hands at the tables 11500-17000), clerk 11, archivist 2, healer 4, miller 4, child 36, resident 140 (60
market-goers, 50 neighbours, 30 elders). Homes: Market 111, Bellwatch 86, Works 71, Quay 64, Garden 64, Terrace 37,
Wallring 27. Outdoors by district (Market/Bellwatch/Quay/Terrace/Works/Garden/Wallring): 06:00 9/10/4/5/7/6/2, 12:00
169/25/55/21/41/16/21, 18:00 68/42/29/13/28/20/20, 22:00 10/11/8/8/9/8/4. **169 outdoors in the Market at noon** (16 more
indoors there), **108 at tavern places at 20:00** (87 more inside), 26 sitting in the bowl at noon. Places from the props:
34 stall keeper + 34 stall front, 58 bench sits (2 benches stand on yard ground: scenery), 16 well stands (of 17), 2
fountain rims, 4 runs of the bowl's steps, 8 notice boards, 6 tavern tables, 14 tavern lane overflows, 4 docks. The named
**Kestrel** (Roostmaster, at the Roost's door 600-12500) and **Bram** (Association officer, inside it with a spell at its
notice board) have skins; no lines yet (B6). Every skin has a `_blink` copy (924 files). Stalls are props (the keeper drops the counter's board and lays the kit's wares by
stock; no vanilla stand-ins). Creature life's layer on CitizenModel/CitizenRenderer: breath, blinks (a closed-eyes skin
for every citizen), the slow head with idle glances that turns to you only within 6 blocks in front or while they speak;
job clips win.

## Tests
`-Ptests=city_,citizens_`: all 25 pass (21 city, 4 citizens). city_grid_rows and city_grid_catchable hold with the
awnings, props and banners: 0 outside cells without a hold, 7,339/7,481 holds and 1,482/1,506 hoods catchable from the
street. city_street_lines: 13 sites, 121/122 cells held, 242/271 lane cells carry a line, 262 soft blocks in the Market.
citizens_day: 2,236 segments, 0 missed, 0 noRoute; citizens_stations: 841 places, 0 problems; 460 citizens up in 6.7 ms.
One limit moved: a spot the game must path to may be 14.2 from its node (was 14): two cells of the bowl (a fountain rim, a
stall's back row) are 14.09 from the nearest junction; the navigator follows to 16. Changed in the generator
(`PATH_REACH`) and citizens_stations together.

## Before/after
One light tour (tools/autopilot/carillon-m4b3a.txt, 53 shots, run by the orchestrator), the same world, settings and
cameras as B2's. Pairs B2 (left) → B3a (right) in docs/carillon/milestones/m4b3a/: `pair_sky_*` (six), `pair_spawn_roost`,
`pair_roost_start`, `pair_square`, `pair_market`, the six districts, `pair_facade_*` (seven), `pair_roofs_*` (two),
`pair_studio_*` (seven) and B2's relief shots (`pair_knoll`, `pair_bowl`, `pair_terrace_wall`, `pair_gate_yard`,
`pair_cellar`, `pair_quay_ladder`). New at B3a (no B2 framing), as `b3a_*.jpg`: the noon street life (`noon_bowl`,
`noon_bowl_stalls`, `noon_square`, `noon_market_street`, `noon_market`, `noon_facade_market`, `noon_laundry_lane`,
`noon_quay`), dusk (`dusk_laundry_lane`, `dusk_bellwatch`, `dusk_canal`) and night (`night_sky`, `night_market_street`,
`night_laundry_lane`, `night_square`).

The best: `pair_facade_market` (bare fascias → awnings, a hanging trade sign on its bracket, bunting and lantern strings
across the street), `pair_market` (awnings both sides, signs, bunting overhead, more people in the street),
`pair_square` (B2's stand-in canopies and stray blocks → the kit's striped stalls round the bowl), `b3a_noon_bowl` (the
stalls in clusters of three between the winds' aisles, people at them), `b3a_dusk_canal` (lantern strings over the water,
windows lit house by house) and `b3a_night_sky` (the city's night as scattered lit windows, not a uniform glow).

## Does it feel like a live city?
In places, yes. The Market's streets read as lived in: something at every storey (awnings, signs on brackets, flower
boxes, bunting and laundry overhead) and people walking under it. The bowl at noon has its stalls and keepers, and the
dusk canal, with its lantern strings and windows coming on one house at a time, is the most convincing shot of the tour.
From the sky at night the lit windows give the city a pattern instead of a glow.

Where it falls short:
- **The laundry lane shots frame into a wall.** `noon_laundry_lane`, `dusk_laundry_lane` and `night_laundry_lane` have a
  wall filling the left half, so the lines this step is proudest of aren't really seen. The camera needs to move.
- **The square's floor.** At noon the stalls are there but the open floor between them is sparse: about a dozen people in
  the bowl where the bible wants a crowd. The roster puts 169 outdoors in the Market district at noon, but much of that
  is in its streets and at stall fronts, and the bowl's floor is big. At night the square has few lamps on its floor
  and reads dark (`night_square`).
- **The green beam** over the city in `sky_dusk` and `night_sky` is a D-rank gate's light pillar (gates/client/Pillars.java,
  rank colour 0x5FD08F). The thin white line over the square in `noon_square` and `night_square` is an E-rank pillar.
  Both were already in B2's shots: gates near the tour world's spawn, not street life.
- Still missing until B3b: animals and sound. The streets look busy but are silent.

The orchestrator assigned these to B3b; none is fixed here.

## CI
GameTests run 40183467058 on 43034a9d (the full suite): success in 4m49s.

## For the Ascent: the movement tags
- `runner_lines`: `awaken:hang_line` (a 3/16 bar at y 6.5-9.5 of its cell, the cloth under it has no collision).
- `runner_soft`: `minecraft:hay_block`, `awaken:awning`, `awaken:hay_cart`.
- `runner_swings`: `awaken:hoist_beam`, `awaken:sign_bracket` (bar y 12-15 at row 5), `awaken:flagpole` (row 9-13).
- `runner_ledges` gains `awaken:awning`; `runner_climbs` and `minecraft:climbable` gain `awaken:line_pole`.
- A hang line's ends meet a course (bible §3.7): at the 43 sampled holds where one ties on, the hanging body can't fit
  under that course; the line is the hold there. If the Ascent's line move wants the course too, lines can start one
  cell further out.

## For B3b and B4
- **Animals (B3b)**: the props give them homes: 16 hay carts (bell towers' feet, the Quay's hoists), 10 nets and the 4
  docks for gulls and cats, 30 Works heaps, the 2 fountains and 17 wells for pigeons. Prop cells are obstacles in the
  roster's raster (`City.prop_mask`); animals' spots should reuse it.
- **Soundscape (B3b)**: the busiest places by time are known from the roster: the Market at noon (169), the taverns at
  20:00 (108 outside), the bowl's steps (26 at noon). The stalls' goods (`stalls[].good`) can pick the criers' calls.
- **Kestrel and Bram** are silent until B6 writes their lines (`tools/city/lines.py`). Kestrel's household has plot -1
  (she lives in the Roost): the burglary event matches households by plot, so she is never burgled.
- **The Watch's lanterns** (from the city-fps session, Euan-approved): `citizens/Lanterns.java` moves up to 12 light blocks
  every 5 ticks, re-meshing 60-100 client sections a move. Approved fix: a client-drawn glow (Lens point light or emissive
  billboard) and no light blocks; interim level 7, a move only after ≥3 blocks, at most 4 carriers. Not built in B3a.
- **Frame cost at dusk** (the city-fps session asked): CityLights' setBlock section rebuilds and the 193 animated wheel
  cells need a frame-timed `slot.sh tour`; the B3a tour is shots only.
- **B4**: 2 benches and a well stand on yard ground and give no places; 4 of the 6 tavern tables got only 2-3 of their
  bench cells (the rest yard or lane). Moving those props in props.py would let more people sit.

## Decisions to propose (SPEC §16)
1. The street-life layer is plan data (`props`, `propFields`; crossings `hung`), painted into open air only.
2. Choir Square's street ends join its r30 ring path: the eight winds are the bowl's only aisles.
3. The plot margin is 3 (a sign bracket's end).
4. Awnings stand one block out, bounce 0.75 and halve a fall; hay carts take a fall as hay (×0.2).
5. Door and wall lanterns are emissive layers, never light sources.
6. The lit-window schedule (CityLights.wanted): on 17:36-19:12, off 21:12-01:00, about 58% of rooms, 6% night owls, early
   risers from 04:48; a house's window list is found by painting it alone, one every other tick.
7. Wall stacks are never two full blocks high.
8. Every street-level prop cell is an obstacle for citizens' places and keep-right lanes; a spot the game must path to
   may be 14.2 from its node.
9. The roster is 430-470 (SPEC §16.5's 400-480) and clusters by day pattern, not by home: at least 120 in the Market at
   noon and 70 at tavern places at 20:00.
10. Every citizen skin has a closed-eyes `_blink` copy.
