# Build sheet: The Quiet Barrow (gate 6, E and D, Land, Bell Crypt)

Read `BUILDER.md` first. Paintings: `docs/gates-as-worlds/img/paint-quiet-barrow.jpg` (the land) and `paint-quiet-barrow-lord.jpg`
(the ship's deck). Open both before you write a line. Roster line: "Sound carries: running wakes what sleeps under the mounds. A
burial hill with a stone ship on top. The Lord's place is the ship's deck."

## What it is, in one breath

A moor at dusk. A broken paved path runs from where you arrive, between stepped burial mounds with lit doorways and standing stones,
through ground mist and purple heather, to a terraced hill. On the hill sits a stone longship as long as a street, mast up, sail in
rags, smoke rising from it. Everything is quiet. If you run, the mounds hear you.

## The big shapes are fixed: the blockout

`src/main/resources/data/awaken/realm_blockouts/quiet_barrow.json` is done and checked against the painting. Don't move a mass. Your
land is `land/QuietBarrow.java`, a `SheetLand<M>` built on that blockout (kit sheet C: `kit/SheetLand`, `blockout/Blockout`,
`Blockout.mass(name)`), so `ground`, `halfSize`, `mouthYaw` and the listed sites come from the data. You arrive on `mouth_pad` at (0, -205) facing
+z. East is +x, which is on your left. The hill is `hill0`..`hill7`, eight terraces four high, centred on (-37, -50), 280 across and
32 high. The ship sits on top along x, 100 long, its prow to the west (-x, the right of the arriving view): `hull`, `bow*`,
`stern*`, `prow*`, `sternpost*`, `mast`, `yard`, `sail`. The way up is `stair`, nine wide, from `stair_foot` at (-37, -146) to the
top terrace at z -70. The orchestrator checked this against the painting from the `painting` camera; the cameras in the blockout
are the tour's shots.

One rule from the kit: the moor is `rough`, so its top moves by two blocks from seed to seed. Anything you stand on it (stones,
barrows, houses, kerbs) must be footed down to the real ground with `V.foot` or drawn from `ground(x, z)`, never from a fixed y.

## Materials

Knell family (`Mats("knell", ...)`, copy `HourCrypt.M` for the accents and the `crypt_*` props). Another builder has re-toned Knell
brighter, so use the roles, never pick by colour: `stoneA worn deep pale hexstone flag brick tile timber planks gold moss bed`, the
slabs and stairs, `crypt_candles` (1 to 5), `crypt_bones`, `gate_ossuary`, `gate_bell`, `crypt_vestment` (cloth).

New blocks, all in a new art spec `tools/art/specs/realm_barrow.py` (copy the shape of `realm_knell.py`; `Def` lines in
`ThemeBlocks`; loot or `Sources.declare` for each; run `tools/art/tone_check.py barrow_` if kit B has landed):

| id | what | shape |
|---|---|---|
| `barrow_heather` | purple heather, knee high, cross plant | PLANT |
| `barrow_heather_pale` | the same gone white and dry | PLANT |
| `barrow_bent` | pale moor grass | PLANT |
| `barrow_turf` | dark peaty turf with a heather-root top | cube |
| `barrow_menhir_cap` | the top of a standing stone: three steps in, 16, 12 and 6 px wide, so no stone ends in a cube | PROP, several model elements |
| `barrow_kerb` | a low rounded kerb stone, 10 px high, 12 px wide | PROP, several elements |
| `barrow_shield` | a round shield on the ship's side: an eight-sided plate 14 px across and 3 px thick with a boss, facing out | PROP with `facing` |
| `barrow_urn` | a burial urn, 6 px foot, 12 px belly, 8 px neck | PROP, several elements |
| `barrow_grave_glow` | the pale green light in the hold | cube, light 11 |

## Passes, in this order

1. **Moor.** Top two cells of every `ground` column are `barrow_turf`, `bed` under. Cover: 55% `barrow_heather`, 12%
   `barrow_heather_pale`, 14% `barrow_bent`, the rest bare, in drifts (two noise fields from `kit/N`, scale 9 and 31), never in an
   even sprinkle. No plants within 2 of the path, a doorway or a site.
2. **The path.** Five wide along (0,-205) (6,-185) (-8,-168) (-30,-156) (-37,-150), as a `ways` line, graded: its height changes by at most one block every two cells, cutting or filling the moor under it, and it meets `stair_foot` level. Paving is `flag` 50%,
   `worn` 25%, `hexstone` 15%, 10% missing (turf shows). `barrow_kerb` on both edges, two out of three cells. Within 7 of the centre
   line scatter loose cut stones: single `stoneA` or `deep` cubes sunk one cell into the turf, `stoneSlab` pieces, about one per 14 cells.
3. **Standing stones.** About 150. Along the path every 9 to 14 blocks, alternating sides, 5 to 8 out from the centre line. A ring of
   6 to 10 round every barrow at its radius + 3. On the outer lip of hill terraces 0, 2, 4 and 6, one every 14 to 20 blocks of
   lip. Twelve in a true ring of radius 16 on `ring_mound`. Height 3 to 7. Footprint 1x1 (70%), 2x1 (25%), 2x2 (5%). Body `stoneA`,
   `worn` and `pale` mixed by course, `moss` on one side low down, `barrow_menhir_cap` on top. Each one footed with `V.foot`.
4. **Barrows.** One piece class, `Barrow(x, z, r, h, doorYaw)`: three steps of masonry (`stoneA`, `worn`, `deep` courses, a kerb
   course of `pale` at each step's lip), turf and heather on the treads. A doorway facing `doorYaw`: two `pale` jambs three high, a
   `paleSlab` lintel five wide, a passage 3 wide and 3 high roofed with upside-down `stoneStairs` (corbelled), into a round chamber
   radius 4 with a stone coffin (`pale` and `paleSlab`), `crypt_bones`, and unlit `crypt_candles`. The doorway is walled up two deep
   with `deep` (the seal). Outside each door, two clusters of 3 lit candles on the ground. Place them at:

   | x | z | r | h | door faces |
   |---|---|---|---|---|
   | 48 | -152 | 17 | 9 | west (-x), toward the path |
   | -80 | -158 | 15 | 8 | east |
   | 96 | -120 | 13 | 7 | west |
   | -130 | -126 | 12 | 7 | east |
   | 130 | -60 | 12 | 7 | west |
   | 150 | -110 | 13 | 7 | south |
   | -190 | -60 | 13 | 7 | east |
   | 60 | 90 | 14 | 8 | south |
   | -40 | 110 | 13 | 7 | south |

   The first two are the pair the painting shows either side of the path, about 55 blocks in front of where you arrive.

5. **The hill.** Each terrace riser is masonry: the courses above, every fifth cell along the lip steps out one as a buttress stub
   with a `stoneStairs` cap, `barrow_kerb` along the lip. Treads are turf and heather. Clusters of 2 to 4 candles on the lips, one
   cluster every 10 to 16 blocks on the south half only (the side you see). The stair: `flagStairs` treads, `pale` cheek walls one
   high, a landing every 8 steps, a pair of standing stones at each landing.
6. **The ship.** Hull of `stoneA`, `worn`, `deep` courses; every second course steps out with upside-down `stoneStairs` so the sides
   read as overlapping strakes. `barrow_shield` every 4 blocks along both gunwales, outside, bosses alternating `gold`, `bronze` and
   `patina` if you give the shield three variants (else one). Deck `flag` and `tile` with a `pale` border; the hold's rim in
   `paleSlab`; `barrow_grave_glow` on the hold floor. On deck: 24 `barrow_urn`, 10 `crypt_bones`, 12 candle clusters of 4 to 5 (these
   are the light pools), three iron braziers. The prow, at the west end, curls back toward the ship like the painting's, with a `gold` eye, and a
   `gate_bell` hanging two links below its jaw. Round off the blockout's boxes at bow, stern and prow by a cell or two so the hull
   reads as a curve. The mast is `timber` 3x3 with `bronze` bands every 8; the yard `timber`; the sail
   `crypt_vestment` with holes (a noise field over 0.62 leaves a gap) and a ragged bottom edge, each column 0 to 4 shorter.
7. **Wardens' places.** `long_barrow`: a Barrow written long, doorway at its west end, chamber the full hall. `ring_mound`: the ring
   of twelve, a flat `pale` slab in the middle. `tarn`: dark water, reeds (`barrow_bent`) at the edge, the islet a small barrow with
   its door toward the causeway.
8. **The village.** On `village_green`: five low houses of `stoneA` with turf roofs (`kit/piece/Hut` if it takes Knell mats, else a
   small piece of your own: 7x7, walls 3 high, a corbelled roof, a door, a candle in a window slit) and two `kit/piece/Stall`. Folk
   kit `candle_keepers`, as The Hour Crypt.
9. **Sites.** The blockout gives Mouth, Portal, Lord, three Wardens and the village. Add: a DEN in every barrow chamber (9);
   Waystones at (-28, -146) "the foot of the hill", (92, 104) "the stone ring", (14, 118) "the tarn"; 16 FINDs, none inside a sealed
   barrow: on barrow tops, at the foot of tall stones off the path, two on the hill's north side, one on the tarn's islet, one behind the long barrow. Then
   `clearSites()`.

## Light, air, sky

- Outdoors, dusk. Biome `awaken:realm/barrow_moor` (copy `knell_crypt.json`, then): `sky_color #4a5870`, `fog_color #7d8aa0` (mist is
  lighter than the land, so distance fades up, not down), `fog_start_distance 24`, `fog_end_distance 420`,
  `ambient_light_color #2a3038`, `sky_light_factor 0.62`, `sky_light_color #a8b8d8`, `block_light_tint #ffb870`, grass and foliage
  `#5a5560`. Set `sun_angle` so the sun sits low to the arriving player's left (+x); read how Amber's biome uses it.
- Second biome `awaken:realm/barrow_ship` for the deck (the Lord's place): the same but `fog_end_distance 230`, `sky_color #2c3850`.
  `biome()` returns it above y 100 within 60 of (-37, -50).
- `air()`: `AirPlan().mist(66, 5, 0xa0acc0, 0.55F).mist(74, 4, 0x8c98b0, 0.3F)`. Blueprint `sky.air`:
  `mist wind smoke far_toll moths bats`.
- Add both biomes to `data/awaken/dimension/realms.json`. Add a Lens look for each to `looks_gates.json`, starting from
  `knell_crypt`'s numbers (kit sheet B) with `exposure 1.12`, `saturation 0.96`, `shafts 0.3`, `haze 0.5`,
  `hazeTint .62 .68 .80`, `sunsetWarmth 0.7`, `mist 0.6`.
- Life: herds `old_antler`, predators `wisp_hound`, ambient `hollow_owl`, `lumen_moth`. Check each id exists; drop and report any that don't.

## The rule: sound carries

`RealmHush.java`, one line in `RealmLive.wake`, its fields in `RealmState` (a bit mask of woken barrows, a told flag). Twice a second
per player: if the player is sprinting and within 22 blocks of a barrow that's still asleep, that barrow wakes. Waking applies
`land.change("wake_" + i)`: the seal comes out of the doorway, the candles in the chamber light. Play a low bell and stone grinding
(named vanilla sounds, as `RealmSalt` does). The first time, the System says: "Something under that mound heard you running. Walk
past the others." Walking and sneaking never wake anything. The long barrow, the ring and the tarn don't sleep.
Read `RealmLife` for how a DEN site spawns: a sealed den must stay quiet until its barrow wakes. If dens can't be held back without
changing `RealmLife`, leave the creatures as they are, keep the seal and the waking, and say so.

The Lord's door: `heart_open` adds eight steps up from the top terrace to the deck at x -39 to -35, z -67 to -60, and opens the south gunwale there.
`heartClosedLine()`: "The ship's sides are too high to climb, and there's no way aboard yet."

## Data and words

- `data/awaken/realms/quiet_barrow.json`: name "The Quiet Barrow", line "A moor of burial mounds under a stone ship. Nothing here
  likes to be woken.", form `land`, theme `bell_crypt`, ranks E and D, program `awaken:quiet_barrow`, biomes as above, law kind
  `hush`, lord `bell_warden` unless the roster's fight list in `data/awaken/kit/fights` has a better Bell Crypt lord, seat
  `ship_deck`, moods `["dusk"]`.
- `LandPrograms` entry, tests through the shared pattern (`realm_quiet_barrow_walks_to_everything`, `_nothing_floats`).
- Tour `tools/autopilot/realm-quiet_barrow.txt`: enter with `cmd awaken gate realm_enter E awaken:quiet_barrow` and `need realm` if
  kit A has landed (else as `realm-hour.txt` does), one shot per camera in the blockout, same names, `quit` last.
- Lang lines for the new blocks in your own merge folder `src/main/merge/gates_barrow/`.

## Checks

Run the bench (`tools/bench/bench.sh check tools/autopilot/realm-quiet_barrow.txt`, or `once` if `check` isn't there) until floats
are 0 and every site is reached. Then `-Ptests=realm_quiet_barrow,journey_ledger` once. You don't judge the pictures; the
orchestrator looks at `painting` beside the painting. Report your rounds and minutes.

## Your files

`land/QuietBarrow.java` (and `land/barrow/*` if you split pieces out); `RealmHush.java`; `tools/art/specs/realm_barrow.py` and what it
generates; the two biome JSONs; the blueprint; the tour; `src/main/merge/gates_barrow/**`; and one-line additions to
`LandPrograms.java`, `RealmLive.java`, `RealmState.java`, `ThemeBlocks.java`, `data/awaken/dimension/realms.json`,
`looks_gates.json`. Not the blockout.
