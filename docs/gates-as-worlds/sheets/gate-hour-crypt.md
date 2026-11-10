# Build sheet: The Hour Crypt, remade (gate 2): small shapes, real light, and a bell that moves

Read `BUILDER.md` first. Paintings: `docs/gates-as-worlds/img/paint-hour-crypt.jpg` and `paint-hour-crypt-lord.jpg`. Open both, then
`run-preview` won't exist in your worktree, so run `tools/preview/preview.sh tools/autopilot/realm-hour.txt` once to see the land as
it stands. The land is `land/HourCrypt.java` (1,242 lines). Its layout is right and stays: the drum with the bell, the nave, the
Ossuary, the Sunk Aisle, the tower, the cloister. You change four things.

Why: Euan's verdict on gates is "it should never be vanilla blocky", and the audit found the paintings' forms are smaller than a
block. The Crypt is also near-black in the real game. Kit sheet B has re-toned the Knell textures brighter and made the preview
show the game's own light; this sheet gives the Crypt shapes smaller than a block, light in pools, and its first moving part. It is
the one gate Euan will play to judge the top of the range.

## 1. A maker for small shapes, and Knell's own five

In `tools/art/specs/_gw_kit.py` add a small maker so any family can have carved blocks: given a name, a base texture and a list of
boxes on a 16-px grid (2-px steps, no rotated elements: the preview can't draw those), it writes the model with one element per box
and UVs that follow position so the stone's texture runs straight through, the blockstate (with `facing` or `axis` when asked,
by blockstate rotation), the item model, loot, lang and tags, the way the spec does for a cube. On the Java side a carved block is a
`Def` whose outline is the same boxes (extend `Def` from one box to a list, keep old `Def`s working), `noOcclusion`, and its light
passes like a stair's. Read how `Shape.PROP` and `box` are handled in `gates/block/ThemeBlocks.java` and the spec, and extend that.

Then in `tools/art/specs/realm_knell.py`, Knell's five (base texture in brackets):

| id | what it is, exactly |
|---|---|
| `knell_arch` (pale stone) | One top corner of an arched opening, with `facing`: steps of 2 px making a quarter curve of radius 16, so two of them mirrored close a 2-wide gap into a pointed arch |
| `knell_flute` (pale stone) | A pier face: the full block with three vertical grooves 2 px wide and 2 px deep on one face, with `facing`. Stacks into a fluted pier |
| `knell_rib` (pale stone) | A vault rib: a bar 6 px wide and 6 px deep running the length of the block along `axis`, hung from the top face, with a 2-px chamfer step each side |
| `knell_corbel` (stone) | A bracket under a ledge, with `facing`: three steps out, each 4 px deep and 4 px lower than the last, 10 px wide |
| `knell_link` (iron) | One link of a great chain: a ring 12 px tall, 8 px wide, 4 px thick, with `axis`, so alternate links turn a quarter. HANG kind for the float check |

`tools/art/tone_check.py knell_` must still pass. Loot or `Sources.declare` for each.

## 2. Put them on the walls

All in `HourCrypt.java`, using its own names (`niche`, `TIER`, `BAY`, `DEPTH`, `Nave.column`, `vault`, `Drum`, `Hall`):

- **Niches.** In `niche`, every opening that isn't walled up (`kind != 13`) gets `knell_arch` in its two top corners at depth 0,
  facing each other, so each bay reads as a small pointed arch, not a square hole.
- **Piers.** Where `Nave.column` and the drum draw a pier's face (`d == 0` on a pier), the cells between the pale corner stones are
  `knell_flute` facing the room, except every twelfth course, which stays the `band`.
- **Ledges.** Under the lip where each tier steps out (`hh == 0`, one cell below, proud of the face), a `knell_corbel` at both
  sides of every bay.
- **Vaults.** In `Nave.vault` and the halls' ceilings, a line of `knell_rib` on the underside along each pier band (`band == 0`
  and `band == 6`), axis across the nave, and one along the crown, axis along it.
- **Chains.** `GreatBell.chain` and every hanging `link` use `knell_link`, alternate links turned, instead of the vanilla chain.

Nothing about the layout, the doors, the sites or the toll changes. Run the bench after each of these, not at the end.

## 3. Light in pools

The painting is dark stone with gold pools at the candles and blue light falling from above. Today most niche candles stand in
ones and twos, which light two to five blocks.

- **Lit bays.** Every third bay along a wall is a lit bay (`Math.floorMod(bay, 3) == 0`): its candles are clusters of 4 or 5, on
  the ledge in front and inside the recess. The two bays between it have none, except one single candle in about one bay in five.
  Same total feel, gathered into pools.
- **Carry the pool to the floor.** One hidden `Blocks.LIGHT` of level 12 in the air one cell out from every lit bay's face, at
  the middle tier of the wall. Keep the low hidden light over the Lord's floor as it is.
- **Moonlight.** `shafts` cuts cracks in the roof (`SHAFTS`). Widen each to 3 to 5 cells and add two over the nave and one over
  the stair head, so real sky light reaches the floor in patches. `air()` already draws a shaft for each: keep them in step.
- `data/awaken/worldgen/biome/realm/knell_crypt.json`: `fog_color #24365a` (lighter than the stone, so the far end of the nave
  glows blue as it does in the painting, not black), `ambient_light_color #20283a`. Leave the rest.
- Target, read from the preview's new numbers (kit sheet B): on `hour_painting`, `hour_nave` and `hour_lord_floor`, `lum` between
  40 and 90, `dark` under 45, `bright` at least 1. Say the numbers before and after in your hand-back.

## 4. The bell moves

The bell is the size of a house and built of blocks, so it stays still. Its clapper and its ropes move.

- Take the clapper (the tongue under the bell: read `GreatBell.draw` and `body`) out of the painted blocks and make it a small set
  of block display entities (`Display.BlockDisplay`: check `/Volumes/MEDIA/Awaken/.ref/src/net/minecraft/world/entity/Display.java`
  for the transformation and interpolation setters), spawned once when the Realm wakes and kept with the Realm (read `RealmLive.wake`
  and how `RealmLife` keeps its folk; the gate's region is erased when the gate closes, so they go with it).
- On each toll (`RealmToll`), for the 180 ticks the doors are down, swing the clapper about its pivot: plus and minus 14 degrees,
  one swing every 30 ticks, eased by the display's own interpolation, and at each end of a swing play the existing toll sound once,
  quieter. At rest it hangs straight.
- While it tolls, a fall of dust under the yoke (the theme's `crypt_dust` particle or block if one exists; no vanilla-looking
  particles) and the eight nearest rope ends (`crypt_bell_rope`) sway the same way if they can be displays too; if that doubles
  the work, leave the ropes.
- Server decides, clients only draw. No per-tick allocation: set a new target once per swing.
- One GameTest, `realm_hour_clapper_swings`: wake a Crypt, fire a toll, the clapper displays exist and their rotation target changes.
- If this part passes 45 minutes of work, stop, keep parts 1 to 3, and report.

## Checks

Bench on `tools/autopilot/realm-hour.txt` until floats are 0 and every site is reached. `-Ptests=realm_hour,journey_ledger` once.
Amber's three hashes unchanged (you touch shared art and `ThemeBlocks`). Report rounds, minutes, and the light numbers.

## Your files

`land/HourCrypt.java`; `RealmToll.java`; `tools/art/specs/_gw_kit.py` (the maker), `tools/art/specs/realm_knell.py` and what it
generates; `gates/block/ThemeBlocks.java` (carved `Def`s and the Knell five); `knell_crypt.json`; a new class for the clapper rig
beside `RealmToll.java`; one test in `RealmTests.java`; lang lines in `src/main/merge/gates_knell_shapes/`.
