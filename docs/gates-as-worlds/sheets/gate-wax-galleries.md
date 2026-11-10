# Build sheet: The Wax Galleries (gate 11, E and D, The Deep, Hive), rebuilt from scratch

Read `BUILDER.md` first. Paintings: `docs/gates-as-worlds/img/paint-wax-galleries.jpg` (the hall) and
`paint-wax-galleries-lord.jpg` (the hanging seat). Open both before you write a line. Roster line: "Warm wax slows you; cold wax
holds your weight. A hollow lit by a slowly dripping comb. The Lord's place is a hanging comb over a drop."

A first pass exists on branch `claude/gates-builder-a` (commit `7be82cba0`). Its land was a wall and a flat floor and is thrown
away. Its rule, blocks and data are kept (see "What to bring over").

## What it is, in one breath

You come out of a dark rock tunnel onto a ledge, and the whole hollow is in front of you: a round hall as big as a cathedral, five
tiers of arcades all the way round, every arch full of candles and hung with a thousand years of drips, and from the roof five
combs the size of towers, glowing gold, each letting one thin thread of wax fall into a lake that never sets. Pale floes of cold
wax make the paths across it.

## The big shapes are fixed: the blockout

`src/main/resources/data/awaken/realm_blockouts/wax_galleries.json` is done and checked against the painting. Don't move a mass.
Your land is `land/WaxGalleries.java`, a `SheetLand<M>` on that blockout (kit sheet C). It is `indoors()`. You arrive in
`mouth_tunnel` at (0, 52, -100) facing +z; the hall's middle is (0, 0); the lake's surface is y 40; the gallery floors are y 44, 54,
64, 74 and 84 (each `galleryN` is a ring corridor 6 wide behind a wall 2 thick, radius 60 to 62); the Lord's `seat` is north at
(0, 128), reached from gallery 2 through `bridgehead` and over `seat_bridge`.

## What to bring over from the first pass

From commit `7be82cba0`, with `git checkout 7be82cba0 -- <path>`: `src/main/java/dev/awaken/gates/realms/RealmWax.java`,
`WarmWaxBlock.java`, `tools/art/specs/realm_wax.py` and every asset it generated (`git show --stat 7be82cba0` lists them), the two
biome JSONs `wax_hollow.json` and `wax_seat.json`, and the blueprint `data/awaken/realms/wax_galleries.json`. Then add by hand the
shared lines that commit made (`git show 7be82cba0 -- <file>`): the wax `Def`s in `ThemeBlocks.java`, the line in `RealmLive.java`,
the field in `RealmState.java`, the two biomes in `data/awaken/dimension/realms.json`, and its lang and tag lines, which go in your
own merge folder `src/main/merge/gates_wax/`. Do not bring `WaxGalleries.java`, its tests or its tour.
In the blueprint: creature ids `wax_moth` and `comb_guard` don't exist. Use herds `pollen_grazer`, predators `drone_bee`,
ambient `amberfly`, `lumen_moth`.

## Materials

Amber family for stone and timber (`Mats("amber", ...)` as `AmberReach.M`), plus the wax blocks `realm_wax.py` makes. Read that spec
to see what exists. Use roles, not colours. Add to it what is missing from this list (loot or `Sources.declare` for each; run
`tools/art/tone_check.py wax_` if kit B has landed):

| id | what | shape |
|---|---|---|
| `wax_cold` | pale set wax, the floes and all the built wax | cube, with slab and stairs |
| `wax_drip` | a hanging drip, three lengths as three blocks or one block with a `length` state: 6 px wide at the top tapering to 2 | HANG, several model elements |
| `wax_arch` | the head of an arcade arch: a stepped quarter curve filling one top corner of an opening, with `facing` | PROP, several elements |
| `wax_candle_bank` | a bank of melted candle stubs of five heights, lit | PROP, several elements, light 13 |
| `wax_comb_cell` | one open comb cell: a hex frame 3 px proud with the back recessed 5 px | cube-sized, several elements |
| `wax_comb_cell_lit` | the same with honey glowing in it | light 12 |
| `wax_fall` | the falling thread of wax | FALL, as `amber`'s or `salt_fall` |

## Passes, in this order

1. **Rock.** Everything `rock` in the blockout that isn't drawn otherwise is the Amber family's dark stone (`deep` with `stoneA`
   strata every 5 to 8 in y). The tunnel and the ledge are bare rock with two `wax_candle_bank` and long drips at the mouth of the
   tunnel, so the hall is framed by a dark arch the way the painting is.
2. **The hall wall, tier by tier.** Sixty bays round the circle (6 degrees each). In each bay of each gallery: a pier two cells
   wide, then an opening four wide and six high through the 2-thick wall into the corridor, its two top corners closed with
   `wax_arch`; a parapet of `wax_cold` slab across the foot of the opening with a gap in every fourth bay; the floor edge of every
   tier steps out one cell as a cornice of upside-down stairs. Piers are `wax_cold` over stone: stone shows at the foot and in
   patches (noise over 0.7).
3. **Drips.** From every arch soffit, cornice lip and the dome: `wax_drip`, lengths by noise, about half of all possible hanging
   cells along a lip, longer in the middle of each bay. At eight places round the hall a thick fall of `wax_cold` runs from the
   dome to the shore (a column 2 to 4 wide that widens at the foot into a stepped skirt).
4. **Candles.** Every third bay is a lit bay: a `wax_candle_bank` on the parapet and a cluster of five candles (`crypt_candles` if
   the Hive has no candle of its own, check `AmberReach.M`) at the back of the corridor. The two bays between hold one stub or
   none. This rhythm is the point: pools of gold with dark between.
5. **The dome.** Ribs every 12 degrees stepping out one cell, `wax_cold`; curtains of long drips between them.
6. **Pendants.** The five `pendant_*` cones: skin of `wax_comb_cell` on a staggered hex pattern, 40% of them `wax_comb_cell_lit`
   in drifts, the core `wax_cold`. From each tip, `wax_fall` straight down to the lake, and a patch of `wax_cold` slabs spreading
   three out on the surface below it. A hidden light of 14 one cell under each tip.
7. **The lake.** `WarmWaxBlock` two deep where the blockout's `lake` is. Floes of `wax_cold` on a hex grid from `kit/N` (cell size
   5): a cell is a floe when its noise is over 0.55, and always along these lines so no one has to wade: south shore (0, -54) to
   the isle (0, 6) to the north shore (0, 54), and the isle to the east shore (54, 0) and to the west shore (-54, 0). Floe tops are
   level with the shore, their edges one slab lower. The shore ring is `wax_cold` over stone.
8. **Ways between tiers.** Three steps up from the shore into gallery 0 at six places. Inside the ring corridor, stairs from each
   tier to the next at 30, 150, 210 and 330 degrees: `wax_cold` stairs rising 10 over 14 cells of arc, hugging the outer wall.
9. **The village cove.** `village_cove` on the west shore: four domed wax houses (`kit/piece/Hut` with these mats if it fits, else
   your own: round, radius 3, a door, a lit window) and two `kit/piece/Stall`. Folk kit `wax_folk`.
10. **Wardens' places.** `chandlery`: a hall off gallery 1 with six great vats (cylinders radius 2, three high, `timber` hoops,
    `WarmWaxBlock` inside) and racks of candle banks. The isle: bare, ringed by eight candle banks. `bridgehead`: piers and two
    braziers, the door to the bridge.
11. **The seat** (open the Lord painting). `seat`: a disc of `wax_cold`, its top paved in hex cells (`hexstone` of the family) with
    `gold` rings at radius 4, 8 and 21 and a boss in the middle; a double ring of candle banks on the rim; under it, drips 3 to 10
    long from every underside cell. Four chains from the rim (at 45, 135, 225 and 315 degrees) up and out to the shaft wall at
    y 112: links of full `wax_cold` blocks, each link 3 by 5 with a hole, alternate links turned a quarter. `seat_comb` above:
    skinned like the pendants, with forty thin strings of `wax_drip` beads hanging 10 to 30 below it. `seat_glow` is honey glow
    (the family's `glow`), with light.
12. **Sites.** The blockout gives Mouth, Portal, Lord, three Wardens and the village. Add: Waystones on the ledge (6, -54) "the
    ledge", on the isle "under the drip", in gallery 2 at (0, 65) north "the bridgehead"; DENs in six gallery corridors spread round
    tiers 0 to 3; 14 FINDs in corridor bays and on far floes. Then `clearSites()`.

## Light, air

- `wax_hollow.json`: `fog_color #6a4a1c` (lighter than the rock, so the far wall glows through haze), `fog_start_distance 18`,
  `fog_end_distance 230`, `ambient_light_color #2e2416`, `block_light_tint #ffc060`. `wax_seat.json`: `fog_color #4a3210`,
  `ambient_light_color #261c10`. `biome()` returns the seat's inside `seat_shaft`.
- `air()`: `AirPlan().mist(42, 4, 0xd8a040, 0.5F)`, a light at each pendant tip (radius 18, 0xffc050, strength 0.9) and a shaft
  down each pendant's thread from tip to lake (radius 2, 0xffd070, 0.7).
- Blueprint `sky.air`: `mist embers shaft_dust dust drips moths`.
- Lens looks in `looks_gates.json`: `wax_hollow` from `amber_citadel`'s numbers with `exposure 1.05`, `bloom 1.1`,
  `hazeTint .90 .62 .20`, `fogGlow 1.0`, `shafts 0.4`; `wax_seat` the same with `exposure 1.12`, `vignette 0.4`.

## The rule, and the fight

The rule is `RealmWax` and `WarmWaxBlock` as brought over: warm wax drags, cold wax holds. Make sure the walk check treats warm wax
as crossable (its first pass has a test; read it) and that every site is reached over floes alone.
The seat changes during the fight the way The Hour Crypt's floor does (`HourCrypt.change`, `floorBreak`): moment `seat_melt_1`
takes the rim from radius 22 to 18, `seat_melt_2` to 14. Cells with something standing on them stay. `heart_open` opens the door
at `bridgehead`. `heartClosedLine()`: "The door to the bridge is sealed with wax as thick as your arm."

## Data and words

Blueprint as brought over, with the creature ids fixed, `"seat": "hanging_comb"`, law kind `wax`. `LandPrograms` entry
`awaken:wax_galleries`. Tests by the shared pattern (`realm_wax_galleries_walks_to_everything`, `_nothing_floats`). Tour
`tools/autopilot/realm-wax_galleries.txt`: `cmd awaken gate realm_enter E awaken:wax_galleries` and `need realm` if kit A has
landed, one shot per camera in the blockout, same names, `quit` last.

## Checks

Bench until floats are 0 and every site is reached, then `-Ptests=realm_wax,journey_ledger` once. Report rounds and minutes.

## Your files

`land/WaxGalleries.java` (and `land/wax/*` if you split pieces out); the files brought over; `tools/art/specs/realm_wax.py` and what
it generates; the tour; `src/main/merge/gates_wax/**`; one-line additions to `LandPrograms.java`, `RealmLive.java`,
`RealmState.java`, `ThemeBlocks.java`, `data/awaken/dimension/realms.json`, `looks_gates.json`. Not the blockout.
