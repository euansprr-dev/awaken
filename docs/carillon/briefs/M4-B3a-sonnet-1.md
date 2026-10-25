# M4-B3a · Sonnet 1: the street kit's textures, models and blockstates

You are a Sonnet subagent of the M4-B3a street-life builder. You write ONE generator,
`tools/art/specs/city_street.py`, run it, and hand back contact sheets. You do not build, run Gradle, run tours or touch
Java. The builder writes the Java block classes to the table below; your blockstates must cover exactly those
properties and values.

## Where you work
- Worktree `/Volumes/MEDIA/Awaken-m4-b3a` only (branch carillon/m4-b3a). Start every Bash command with
  `cd /Volumes/MEDIA/Awaken-m4-b3a &&`. Never touch /Volumes/MEDIA/Awaken, /Volumes/MEDIA/Awaken-play,
  /Volumes/MEDIA/Awaken-carillon or another worktree (reading `/Volumes/MEDIA/Awaken/.ref/assets` is fine: vanilla
  assets for palette anchors and templates).
- Never run Gradle, `tools/gradle.sh`, `slot.sh` or the game. Python 3 + PIL only (`python3 -I ...`).
- Only write: `tools/art/specs/city_street.py`, the textures/models/blockstates it generates under
  `src/main/resources/assets/awaken/` (new names only, listed below, plus the re-skinned `laundry_*` and
  `street_lamp_glass_lit` textures named in §C), and merges into `src/main/merge/city/` (lang, mineable tags). Do not
  edit `city_kit.py`, `citizens.py` or anything in `src/main/java`. Import helpers from `city_kit.py` (R, rgb, lerp,
  mul, tint, box, put, noisy, fit, face, rot_variant, weighted, _write/_merge pattern) instead of copying them.
- Do NOT commit. The builder reviews and commits. When done, list every file you wrote.

## Read first
1. `tools/art/pixelart.py` (Canvas, rng_for, Out; note `Out._save` writes an animation .mcmeta with interpolate true:
   write your own .mcmeta after it where the table says interpolate false).
2. `tools/art/specs/city_kit.py`: the structure to copy (textures() / build(out) for `tools/art/build_all.py`, a
   `content` command writing blockstates, models, lang and mineable tags, a `check` command that resolves every
   blockstate variant, model parent and texture, and a `sheet` command). Your file mirrors it.
3. `docs/carillon/m4/look-bible.md` §4, §5 and §7, and the concept images
   `docs/carillon/m4/concept/05-market-street.jpg`, `06-choir-square-noon.jpg`, `03-bellwatch-lane.jpg`,
   `04-lantern-quay.jpg` (stalls, striped awnings, iron sign brackets with painted signs, bunting, laundry, carts with
   spoked wheels, crates of produce, casks, planters, lamp posts, a well).
4. The existing citizens' art for the same world: `tools/art/specs/citizens.py` `laundry_*` and `street_lamp_*`.

## The art direction (the bar: it reads as vanilla Minecraft with a shader, never as an add-on)
- 16×16 per face, crisp pixels, no anti-aliasing, no black outlines round shapes, 4–6 tone ramps per material, light
  from the top-left. Anchor every material on a real vanilla texture's average (sample
  `/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/block/*.png`): spruce_planks (114,84,48),
  oak_planks (162,130,78), dark_oak_planks (66,43,20), stripped_spruce_log, iron_bars (136,139,135), chain,
  lantern, hay_block, white_wool (234,236,236), the dyed wools for stripe colours (desaturate them ~15% and warm them:
  canvas, not plastic).
- Everything deterministic (`rng_for("city_street", name)`); no text, no letters, no logos. Trade signs are ICONS.
- Small things are made of a few real elements (boxes) with sensible UVs, the way vanilla's lantern, chain, campfire,
  composter and bed are. Keep each model to the quads it needs (a market stall is seen 40 at a time). Flat cutout
  planes (zero thickness, both faces) for cloth, pennants, nets and the signboard's hanging chains.
- Use `"render_type": "minecraft:cutout"` on every model with transparent pixels; `"ambientocclusion": false` on thin
  things. Glow: an extra element with `"light_emission": 15` and `"shade_direction_override": "up"` drawing a glow
  texture that is transparent except the glass/flame (see `lit_window_model` in city_kit.py).
- Model orientation: draw every directional model for facing=north; blockstates rotate it with y = 0/90/180/270 for
  north/east/south/west (`rot_variant`). For a wall piece, "facing" points AWAY from the wall it hangs on (a flower box
  on a north wall faces north): in the north model the wall is at z = 16 and the street is toward z = 0.
- Elements may extend outside the cell (-16..32) where a thing hangs below its block (laundry, a signboard's board,
  cart wheels). Keep within -16..32.

## A. The blocks (ids `awaken:<name>`; properties and values EXACTLY as written; every combination needs a model)

| id | properties | model (north) | collision the Java gives it (match the visual to it) |
|---|---|---|---|
| `awning` | facing N/E/S/W (the way it slopes down, away from the wall), colour red, blue, yellow, green, orange, plum, shape slope, upper, lower, flat | striped canvas. **slope**: one block out from the wall, the canvas falls from y 16 at z 16 to y 9 at z 0 (a sloped element: use a box rotated 22.5° about x, or a stair of 4 thin boxes 2 px thick stepping down; the slope reads better) with a scalloped valance hanging 4 px at the front edge (z 0..1, y 5..9, cutout scallops). **upper**: the first of a two-block awning: y 16 at z 16 to y 12 at z 0, no valance. **lower**: the second: y 12 at z 16 to y 8 at z 0, valance. **flat**: a stall canopy's middle: canvas level at y 14..16, no valance. No support rods needed (it hangs on the facade's hood row) except two thin dark iron rods under `slope` and `lower` from the wall (z 16, y 6) to the front edge (z 1, y 8). | slope/upper/flat: y 8..16 (a top slab); lower: y 4..12. |
| `sign_bracket` | facing (outward), part arm, end | black wrought iron (iron_bars palette, darker). **arm**: a horizontal bar 2×2 px at y 13..15 running the whole cell along z, a curled scroll brace under it from the wall (z 16, y 4) up to the bar at z 6 (a few 1 px boxes or a cutout plane in the x = 8 plane with a scroll drawn in it), a wall plate at z 15..16. **end**: the bar ends at z 4 in a small curl/finial; two short hooks hang under it at z 5 and z 13 (where the signboard's chains meet it). | a bar: x 6..10, y 12..15, the full cell along the facing axis. |
| `signboard` | facing, trade bread, fish, produce, cloth, pottery, candles, flowers, spice, tools, cheese, tavern, boots, books, keys, mount hanging, flat | **hanging**: a painted board in the plane x = 7.5..8.5 (1 px thick), z 2..14 (12 wide), y 2..11, with two 1 px chains from its top corners up to y 16 (they meet the bracket's hooks in the block above); the icon on both faces. **flat**: a board 16 wide × 8 tall (y 4..12) at z 15..16 against the wall, the icon in the middle, end scrolls. Boards: dark green, oxblood, navy, ochre or black painted wood with a 1 px gilt or cream border; the icon in 2–4 colours, 7–9 px. Icons: a loaf (bread), a fish (fish), an apple/pear (produce), shears over a bolt (cloth), a jug (pottery), a lit candle (candles), a posy (flowers), a mortar and pestle (spice), crossed hammer and tongs (tools), a cheese wedge (cheese), a foaming tankard (tavern), a boot (boots), a closed book (books), a key (keys). | none |
| `hang_line` | axis x, z; hang rope, laundry, bunting, lanterns; set 0..3; sag 0..2 | a hemp rope (reuse the look of `awaken:block/rope`, laid horizontal) along the axis at y 9 − sag (sag 0 at the ends of a line, 2 in its middle: the builder sets it so the rope droops), 1×1 px section, plus what hangs on it in the plane z = 8 (axis x): **rope** nothing; **laundry** a 16×16 cutout plane from y (9 − sag) down 14 px with 1–3 pegged garments (shirts, sheets, aprons, stockings, towels, a dress, trousers) in 4 different sets, linen whites, blues, ochres, a red, faded, with a darker hem and fold shading, small wooden pegs at the rope; **bunting** 2 pennants per block (triangles 6 wide × 6 tall) hanging from the rope, alternating colours, 4 colourways (set 0 red/yellow/blue/white, 1 market brights, 2 green/white/ochre, 3 Association navy/bronze/cream); **lanterns** one small paper or iron-and-glass lantern (4×5 px) hanging 2 px under the rope at the cell's middle, its glass emissive (a glow element) — set 0..3 vary the lantern's colour (warm amber, paper red, paper cream, iron and amber). Axis z: the same rotated (y 90). | a bar: axis x: x 0..16, y 6.5..9.5, z 6.5..9.5 (like a chain). |
| `banner_cloth` | facing, design bellwatch, quay, market, terrace, works, garden, wallring, association; part top, middle, bottom; mount wall, pole | a long cloth banner 3 blocks tall (top over middle over bottom). **wall**: the cloth plane at z 15.5 (just off the wall), x 2..14; top carries a dark rod across at y 14..15 with knobs; bottom ends in a swallowtail or fringe. **pole**: the same cloth in the plane x = 8, z 2..14, hung from a rod under a flagpole's tip (top's rod along z). District colours: bellwatch slate blue + cream with a bell, quay brick red + navy with a lantern, market saffron + scarlet with scales, terrace white + Association blue with a laurel, works soot black + ember orange with a hammer, garden green + rose with a blossom, wallring grey + rust with a tower, association navy + bronze with crossed blades under a bell. The emblem sits on `middle`. | none |
| `flagpole` | facing (outward) | a dark timber pole 2×2 px from a wall plate at (z 16, y 2) rising outward at 22.5° to a brass finial past the cell's front (to about z −10, y 12: let the element extend out of the cell). | a bar x 6..10, y 8..12, z 0..16. |
| `line_pole` | top true, false | a round-ish timber pole 4×4 px in the middle of the cell, full height; top=true ends at y 12 in an iron cap with a hook ring on two sides (the line ties on). | none (it is climbable, like vines). |
| `crate` | content lid, apples, cabbages, fish, straw, bottles | a full cube crate: plank sides with a dark frame and two cross-nailed battens (different from `cargo_crate`: lighter, market crates, spruce/oak mixed), and the top showing the content heaped inside a 1 px rim: lid (closed boards with nail heads), red/green apples, cabbages, silver fish on straw, golden straw, green and brown bottles packed in straw. | full block |
| `sack` | count 1, 2, 3; fill grain, flour, wool | burlap sacks tied at the neck: count 1 one plump sack (8×10 footprint, 10 tall); 2 two leaning together; 3 a heap of three (one lying on two). grain: brown hessian; flour: pale stencilled cream; wool: grey-white bulging fleece. | count 1: y 0..10; 2: y 0..10; 3: y 0..12 |
| `wares` | good bread, fish, produce, cloth, pottery, candles, flowers, spice, tools, cheese | goods laid out on a counter: a shallow tray or basket 2–3 px deep with goods heaped above it as small boxes: loaves and rolls; fish on ice; apples, pears, a melon; folded bolts of coloured cloth; jugs and bowls; bundles of candles; posies in a bucket; little sacks of spice in rows; a hammer, tongs and nails; cheese wheels and a wedge. Up to ~7 px tall. | none |
| `stall_counter` | facing (toward the customers), open true, false | a wooden market counter, full block: a plank top (y 14..16) with a lip, a body of dark boards, a cloth skirt on the front face in the stall colour family (use a warm red-brown cloth; the stall's colour shows on its awning). open=false: a hinged board is folded up against the front (darker, with an iron hasp). | full block |
| `cask` | orient upright, x, z | a wooden cask (no block entity): bulging staves (a 14 px column with 12 px ends), two dark iron hoops, a round lid with a bung; x/z lying on its side on two small chocks. | upright: x 1..15 column full height; lying: full block |
| `cart` | facing (the shafts' side), part body, shafts; load empty, crates, sacks, casks, produce | a two-wheeled market cart, two blocks long: **body** a plank bed at y 6..8 with low side boards to y 12, a big spoked wheel (14 px across, 12 spokes, dark iron rim) on each side (x −1..1 and x 15..17, centred at the body's middle, y 0..14), the load on the bed (two crates; three sacks; two casks lying; a heap of produce in baskets). **shafts**: two long thin shafts (1.5×1.5 px) from the bed's front (z 16, y 7) sloping down to the ground at z 2, with a crossbar. In the north model the shafts point north (the shafts block is north of the body). | body: y 0..14; shafts: y 0..4 |
| `hay_cart` | facing, part body, shafts | the same cart loaded with loose hay heaped over the sides to y 16 (hay_block's palette, with stray straws). The leap-of-faith cart under the bell towers. | body: full block; shafts: y 0..4 |
| `bench` | facing (the way a sitter looks) | a plank seat (y 7..9, z 4..12) on two trestle legs, a backrest board on the south side (y 10..14, z 12..14) on two posts. Oak or weathered spruce. | y 0..9 seat box |
| `market_table` | axis x, z | a trestle table: a plank top y 14..16 across the whole cell, two X-trestles; a linen runner across the middle of the top. | full block |
| `goods_shelf` | facing (outward), goods jars, cloth, bread, pots, books, tools | a two-shelf open rack against the wall (z 8..16), its shelves full of: jars of preserves; folded cloth bolts; loaves and baskets; jugs and pots; books; tools hung and laid. | z 8..16 full height |
| `produce` | kind apples, cabbages, carrots, pumpkins, potatoes, flowers | a low slatted crate or basket (12 px tall) on the ground heaped with: red apples; cabbages; carrots with green tops; small pumpkins; potatoes; cut flowers in buckets. | y 0..12 |
| `planter` | style wood, stone; bloom red, yellow, blue, pink, white, herbs, shrub | a street planter, full width (x 1..15), y 0..10: wood (dark-stained boards and iron bands, like the flower box's) or stone (a pale moulded trough). Above it the bloom as cutout crosses rising to about y 22 (reuse city_kit's bloom textures: `awaken:block/bloom_<colour>`; `shrub` is a new clipped box-hedge ball texture). | y 0..10 |
| `wall_lantern` | facing (outward) | a black iron lantern on a curled wall bracket: bracket from the wall plate (z 16, y 12) out to z 9, the lantern hanging under it (x 5..11, z 5..11, y 3..11) with an amber emissive glass (glow element). | none |
| `notice_board` | facing | a board on two posts: posts at x 1 and x 14 from y 0 to y 16, a framed board y 4..15 at z 7..9 covered in pinned papers (cream, white, one red seal), a little roof strip on top. | x 0..16, y 0..16, z 6..10 |
| `fishing_net` | axis x, z | a fishing net hung to dry on a line in the plane z = 8: diamond mesh (1 px cords, 3 px holes), cork floats along the top, sagging; cutout. | none |
| `well_windlass` | axis x, z | the well's winding gear spanning the cell along the axis: a round timber drum (6 px) at y 2..8 with rope wound on it, a crank handle on one end, a bucket hanging on a rope down to y −6. | x 0..16, y 2..8, z 5..11 |
| `mooring_ring` | facing (outward) | a black iron ring (8 px across, 1 px thick) hanging from a staple at the wall, at y 4..12. | none |

## B. Movement for free (animated textures)
- `hang_line` laundry and bunting: 8 frames each, frametime 4, **interpolate false**: the cloth's lower edge sways
  (garment bottoms and pennant tips shift 1 px left and right through the cycle, the fold shading moves with them), as
  if a breeze runs along the line. Each set starts its cycle at a different frame (draw the frames shifted) so a street of
  lines never moves in step.
- `banner_cloth`: 8 frames, frametime 4, interpolate false: a slow ripple travelling down the cloth (shading bands move
  down 1 px a frame; the bottom's tail sways 1 px).
- Lantern glows (`hang_line` lanterns, `wall_lantern`): the glow texture flickers: 6 frames, frametime 3, interpolate
  true, brightness varying ±12%, the flame's hot pixel moving by 1 px.
- `street_lamp_glass_lit` (the citizens' street lamp, already in the game): rewrite it from your file as the same design
  with the same 6-frame flicker (keep the name; the model already uses it; look at citizens.py `street_lamp_glass_lit`
  first and keep its look, only animate it).
- Awnings, signs, carts, crates: static.

## C. Re-skins (same names, better art)
- `laundry_1` .. `laundry_6` (the citizens' washers' line, cloth states): redraw them in the same style as your laundry
  sets so the washers' lines and the street lines match; keep 16×16 each and the model's UVs (they map y 1..12). Keep
  `laundry_line` as is. Animate them the same way (8 frames, interpolate false).

## D. Lang and tags
- en_us names for every new block (e.g. "Awning", "Sign Bracket", "Signboard", "Hang Line", "Banner Cloth", "Flagpole",
  "Line Pole", "Crate", "Sack", "Wares", "Stall Counter", "Cask", "Cart", "Hay Cart", "Bench", "Market Table",
  "Goods Shelf", "Produce", "Planter", "Wall Lantern", "Notice Board", "Fishing Net", "Well Windlass", "Mooring Ring"),
  merged into `src/main/merge/city/assets/awaken/lang/en_us.json`.
- `mineable/axe`: every wooden one (planter too); `mineable/pickaxe`:
  sign_bracket, wall_lantern, mooring_ring. Merge into `src/main/merge/city/data/minecraft/tags/block/mineable/*.json`.
  The builder writes the movement tags (runner_*), not you.

## E. Checks and what you hand back
1. `python3 -I tools/art/build_all.py city_street` (textures), then `python3 -I tools/art/specs/city_street.py content`
   (blockstates, models, lang, tags), then `python3 -I tools/art/specs/city_street.py check`: every blockstate key covers
   every combination of the table's properties (compute the product and compare), every model parent and texture file
   exists. Fix until clean.
2. `python3 -I tools/art/specs/city_street.py sheet <path>`: a contact sheet of every new texture at 4×, labelled, frames
   of animations side by side. Write it to `build/art/city_street_sheet.png`.
3. `python3 -I tools/art/specs/city_street.py mock <path>`: a small isometric or front render of a few assembled models
   (a stall: counter + wares + flat/slope awning canopy on posts; a shopfront awning row in all six colours; a bracket
   with a hanging sign; a laundry line and a bunting line; a cart and a hay cart; crates, sacks, casks, produce, a planter,
   a bench) using the model JSON elements you wrote (front orthographic projection of the elements with their textures
   is enough: draw each element's north and up faces, nearest last). Write it to `build/art/city_street_mock.png`.
4. Look at both images yourself (Read tool) and fix anything that reads as flat, noisy, plastic or wrong in scale before
   handing back. Report: the files written, any deviation from the table (there should be none in properties), and the
   two image paths.
