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
nearest unknown house to a player every other tick (~X ms each, measured in the tour), three houses a tick brought to
their wanted state.

**Citizens**: TODO roster numbers. Stalls are props (the keeper drops the counter's board and lays the kit's wares by
stock; no vanilla stand-ins). Creature life's layer on CitizenModel/CitizenRenderer: breath, blinks (a closed-eyes skin
for every citizen), the slow head with idle glances that turns to you only within 6 blocks in front or while they speak;
job clips win.

## Tests
TODO

## Before/after
TODO

## CI
TODO

## Does it feel like a live city?
TODO

## For the Ascent: the movement tags
- `runner_lines`: `awaken:hang_line` (a 3/16 bar at y 6.5-9.5 of its cell, the cloth under it has no collision).
- `runner_soft`: `minecraft:hay_block`, `awaken:awning`, `awaken:hay_cart`.
- `runner_swings`: `awaken:hoist_beam`, `awaken:sign_bracket` (bar y 12-15 at row 5), `awaken:flagpole` (row 9-13).
- `runner_ledges` gains `awaken:awning`; `runner_climbs` and `minecraft:climbable` gain `awaken:line_pole`.
- A hang line's ends meet a course (bible §3.7): at the 43 sampled holds where one ties on, the hanging body can't fit
  under that course; the line is the hold there. If the Ascent's line move wants the course too, lines can start one
  cell further out.

## For B3b and B4
TODO

## Decisions to propose (SPEC §16)
TODO
