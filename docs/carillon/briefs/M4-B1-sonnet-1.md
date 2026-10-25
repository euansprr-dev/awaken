# M4-B1 · Sonnet 1: the core kit's textures, models and blockstates

You are a Sonnet subagent of the M4-B1 City Architect. You write ONE generator, `tools/art/specs/city_kit.py`, run it,
and hand back a contact sheet and a facade mock. You do not build, run Gradle, run tours or touch Java.

## Where you work
- Worktree `/Volumes/MEDIA/Awaken-m4-b1` only (branch carillon/m4-b1). Start every Bash command with
  `cd /Volumes/MEDIA/Awaken-m4-b1 &&`. Never touch /Volumes/MEDIA/Awaken, /Volumes/MEDIA/Awaken-play,
  /Volumes/MEDIA/Awaken-carillon or another worktree (reading `/Volumes/MEDIA/Awaken/.ref/assets` is fine: vanilla
  assets for palette anchors and blockstate templates).
- Never run Gradle, `tools/gradle.sh`, `slot.sh` or the game. Python 3 + PIL only (`python3 -I ...`).
- Commit your work at the end (one commit), message ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Don't push.

## Read first
1. `tools/art/pixelart.py` (Canvas, ramp, rng_for, Out) and `tools/art/specs/ascent_blocks.py` (a block spec with an
   emissive overlay element, content() writing blockstates/models/loot/lang/tags). Copy its structure.
2. `src/main/java/dev/awaken/city/kit/KitBlocks.java` and `CityKit.java`: the block ids and every property and value.
   Every combination of property values must map to a model in the blockstate (no missing variants).
3. `docs/carillon/m4/look-bible.md` §5 (district palettes) and §8 (the kit), and look at
   `docs/carillon/m4/concept/03-bellwatch-lane.jpg`, `04-lantern-quay.jpg`, `05-market-street.jpg`,
   `08-affordance-sheet.jpg` for mood.

## The art direction (the bar: it must read as vanilla Minecraft, not an add-on)
- 16×16 per face, crisp pixels, no anti-aliasing, no outlines round shapes, 4–6 tone ramps per material, light from the
  top-left (highlights top/left edges, shadows bottom/right). Match vanilla's saturation and value range: sample anchors
  from `/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/block/*.png` (e.g. stripped_dark_oak_log
  avg (72,56,36), spruce_planks (114,84,48), dark_oak_planks (66,43,20), iron_bars (136,139,135), deepslate_tiles
  (54,54,55), stone_bricks (122,121,122), calcite (223,224,220), cut_copper (191,106,80), bricks (150,97,83)).
- Every texture deterministic (rng_for(name)). Tileable where it repeats (tiles, pipe, trellis, ivy, rope).
- No text, no logos.

### Textures (all under `assets/awaken/textures/block/`, names exactly as listed)
1. `slate_tiles`: blue-grey roof slates, clearly LIGHTER and BLUER than vanilla deepslate_tiles (target average about
   (98,106,120)). Staggered courses of rectangular slates 4 px tall, each slate 4–6 px wide, a 1 px dark shadow line
   under every course, a lighter top-left edge on each slate, ±1 ramp step of variation per slate and one or two slates in
   a slightly greener or more purple grey (old Welsh slate). Tileable both ways.
2. `clay_tiles`: warm orange clay roof tiles (pantiles), average about (178,98,60): vertical columns of rounded tiles 4 px
   wide (a highlight stripe left of centre, a dark shadow at the right edge), a horizontal overlap line every 4 px (a
   darker line with a lighter lip just under it), a few tiles browner or redder. Clearly not flat terracotta, not copper.
3. Flower box (model: a planter at the bottom of the cell against the wall, flowers above it):
   `flower_box_wood` (dark-stained spruce boards with two black iron bands, top edge lighter), `flower_box_iron`
   (a black wrought-iron balconette basket: a frame, vertical bars 1 px wide with 1 px gaps, a curled top rail; cutout,
   so the soil and the plants show through the bars), `flower_box_stone` (a pale carved stone sill box, calcite/quartz
   tones with a moulded lip), `flower_box_soil` (dark loam, a few pebbles). Blooms, cutout crosses like vanilla flowers
   (a bushy clump of stems and leaves with blossoms, bottom 2 px stems only so it sits in the soil):
   `bloom_red` (geraniums), `bloom_yellow` (marigolds), `bloom_blue` (cornflower/lobelia), `bloom_pink`,
   `bloom_white` (daisies), `bloom_herbs` (green herbs, no blossoms). Bloom `none` = soil only, no cross.
4. Shutters, drawn as seen from the street, the 2-block shutter as a lower and an upper texture (16×16 each, together
   one 16×32 design), hinges on the image's LEFT edge (the model mirrors the UV for hinge=right):
   `shutter_<colour>_lower`, `shutter_<colour>_upper` for colour in blue (faded cornflower paint with worn edges showing
   wood), sage (grey-green), oxblood (dark red-brown), natural (stained oak boards with a Z brace), green (bottle green),
   cream (pale cream with a slightly darker frame). Design: a 1 px frame a shade darker; the upper half louvred
   (horizontal slats, a shadow line every 2 px); the lower half two recessed panels (natural: vertical boards with a
   Z brace instead); a black iron strap hinge near the top of each half on the left edge (2 px tall, 5 px long).
5. `drainpipe`: lead-grey pipe (dull blue-grey, about (86,90,99) average, a 1 px highlight column on the left of the
   pipe, a seam ring every 8 px), used on the pipe's faces via UVs; `drainpipe_head`: the hopper head (same metal, a
   moulded rim, a dark top opening).
6. `trellis`: a diamond lattice of weathered pale timber laths 1–2 px wide at 45°, about 5 px apart, transparent
   between (cutout), tileable vertically and horizontally. `trellis_ivy`: ivy leaves (dark and mid greens, NOT biome
   tinted, baked colours like (38,74,34)…(92,140,64)), clusters covering about 60% with gaps, tileable vertically.
7. Lit windows. Each 2-tall style is a `_lower` and an `_upper` texture forming one 16×32 window; the window fills the
   whole face (the wall is the neighbouring blocks), so the outer 1 px is the reveal/frame, never wall material. Bottom
   3–4 px of `_lower` is the sill (it sits behind a flower box). Glass shows a DIM interior (deep warm brown-black, a hint
   of a curtain at one side, a faint sky reflection: a lighter diagonal streak or a lighter top), never a flat colour.
   Styles:
   - `window_leaded_*` (Bellwatch, Wallring): dark timber frame 2 px, leaded diamond panes (1 px dark lead lines on
     greenish-grey glass), a red or cream curtain edge.
   - `window_sash_*` (Market): cream-white painted frame, glazing bars (6 over 6 across the two halves), the meeting
     rail at the top of the lower half, a lace curtain hint high in the upper half.
   - `window_arched_*` (High Terrace): pale stone (calcite/quartz) surround 2 px; the upper half's top is a round arch
     (the corners outside the arch are the same pale stone), a keystone at the top centre, glazing bars, a blue curtain.
   - `window_cottage_*` (Garden Ward): white frame with a verdigris-green outer frame, a cross mullion per half (four
     panes), a cheerful red-and-white curtain.
   - `window_warehouse_*` (Lantern Quay upper storeys): dark timber frame, 3×3 small panes per half, dusty grey-brown
     glass.
   - `window_barred_*` (Quay and Works ground storeys): a dark stone/iron frame, three vertical iron bars in front of
     dark grimy glass.
   - `window_works_*` (the Works): steel-grey iron frame, a grid of small panes (4×4 per half), sooty glass, one or two
     panes darker as if broken.
   - `window_shop_*` (shopfronts, ground floor rows 1–2): a dark green-black painted frame; `_lower` has a 3 px stall
     board at the bottom, then glass showing two shelves of goods (loaves, jars, bottles, cloth: small coloured pixel
     groups); `_upper` has a transom bar 3 px from the top and the shop's warm dark interior.
   - `window_dormer_single` (dormers, gables): a small four-pane casement in dark timber.
   - `window_round_single` (Market attics): a round window (radius 6) in a pale stone square surround, glazing cross.
   For every texture above also draw `<name>_glow`: transparent except the glass area, painted warm amber
   (lamp light: about (255,196,110) brightest at the centre to (214,128,52) at the edges, the curtain a little darker),
   so the lit window glows through the same bars and leading.
8. Loading door (Lantern Quay's middle bay; 2 wide × 2 tall, seen from the street):
   `loading_door_lower_left`, `_lower_right`, `_upper_left`, `_upper_right`: weathered vertical spruce/dark oak boards,
   two leaves meeting at the centre (a dark 1 px gap at the right edge of the left quarters), a horizontal ledger board at
   mid-height of each leaf and a diagonal brace, black iron strap hinges on the outer edges, a ring handle on each leaf by
   the centre at the lower quarter's top.
9. `rope`: twisted hemp (diagonal alternating tan and darker tan stripes, 2 px wide strand pattern), tileable
   vertically. `hoist_pulley`: an iron pulley wheel's face (dark iron rim, spokes).
10. Chimney crown: `chimney_flue_<m>` (top face: the material's bricks round a black soot flue 6×6 in the middle) and
    `chimney_band_<m>` (the oversailing band's side: the material a shade darker with soot rising in the top 3 px) for
    m in brick (vanilla bricks colours), mud (mud_bricks), stone (stone_bricks).

### Models and blockstates (`content()`, written by `python3 tools/art/specs/city_kit.py content`)
Convention (as vanilla's ladder): every wall piece is modelled facing NORTH with its wall at z = 16 (the south side);
blockstates rotate y 0/90/180/270 for north/east/south/west. Add `"render_type": "minecraft:cutout"` to every model with
transparency. Use `"ambientocclusion": false` on thin pieces.
- `slate_tiles`, `clay_tiles`: cube_all. Stairs and slabs: copy the variant tables of vanilla
  `blockstates/deepslate_tile_stairs.json` and `deepslate_tile_slab.json` from the .ref assets, with models parented to
  `minecraft:block/stairs`, `inner_stairs`, `outer_stairs`, `slab`, `slab_top` (textures bottom/top/side = the tiles);
  the double slab uses the full block's model.
- `flower_box` (facing, style, bloom): planter element from (1,0,5) to (15,7,16) (wood/stone: textured box; iron: the
  basket as a cutout box with the soil element inside at (2,0,6)–(14,5,15), soil top at y 5), soil top face for
  wood/stone at y 6.5 inside the planter (an element (2,6,6)–(14,6.5,15) up face only), bloom: two crossed planes from
  y 5 to y 15 centred at z 10.5 spanning x 1–15 (not rotated by facing beyond the blockstate's y rotation). Bloom `none`:
  no cross. One model per style×bloom (21 models), rotated by facing in the blockstate.
- `shutter` (facing, colour, hinge, half): one element (0,0,14)–(16,16,16), north face = the texture (hinge right:
  uv [16,0,0,16]), other faces a 2 px strip of the same texture. 24 models (colour × half × hinge).
- `drainpipe` (facing, part): pipe = a 4×4 px column (6,0,11)–(10,16,15) plus a bracket band (5,12,10)–(11,13,16);
  head = a box (4,6,9)–(12,14,16) with a rim (3,13,8)–(13,16,16) and the pipe below it (6,0,11)–(10,6,15); shoe = the
  pipe from y 4 to 16 plus the kick-out (6,0,6)–(10,4,15) pointing away from the wall.
- `trellis` (facing, ivy): the lattice plane at z 15 (element (0,0,15)–(16,16,15), north and south faces); ivy adds a
  second plane at z 14 with `trellis_ivy`.
- `lit_window` (style, half, lit): a full cube; north/east/south/west faces = the style's half texture (single halves:
  `window_dormer_single`/`window_round_single`; a 2-tall style asked for `single` uses its `_upper`; dormer/round asked
  for lower/upper use their single), up/down = `minecraft:block/dark_oak_planks`. Lit adds a second full-cube element
  with the `_glow` textures, `"light_emission": 15`, `"shade_direction_override": "up"` (as ascent_blocks.py does).
  Render type cutout for the lit ones.
- `loading_door` (facing, part): a full cube, north face the part's texture, other faces `minecraft:block/spruce_planks`.
- `hoist_beam` (facing, pulley): beam (5,8,0)–(11,14,16) textured `minecraft:block/stripped_dark_oak_log` (ends:
  `stripped_dark_oak_log_top`); pulley: a wheel (7,1,2)–(9,8,7) with `hoist_pulley` on east/west faces and a hook strap
  (7.5,7,3.5)–(8.5,8,5.5).
- `rope`: element (7,0,7)–(9,16,9), cutout not needed.
- `chimney_bricks` (material): a full cube of the vanilla material texture (`minecraft:block/bricks`,
  `mud_bricks`, `stone_bricks`), plus the band (-1,12,-1)–(17,15,17) with `chimney_band_<m>` on its sides and
  `chimney_flue_<m>` on its top (the cube's own top face is hidden under it).
- Lang: merge into `src/main/merge/city/assets/awaken/lang/en_us.json` (`block.awaken.<id>`: "Slate Tiles", "Slate Tile
  Stairs", "Slate Tile Slab", "Clay Tiles", "Clay Tile Stairs", "Clay Tile Slab", "Flower Box", "Shutter", "Drainpipe",
  "Trellis", "Lit Window", "Loading Door", "Hoist Beam", "Rope", "Chimney Crown").
- Mineable tags (merge into `src/main/merge/city/data/minecraft/tags/block/mineable/pickaxe.json` and `axe.json`):
  pickaxe: slate/clay tiles, stairs, slabs, chimney_bricks, drainpipe; axe: flower_box, shutter, trellis, lit_window,
  loading_door, hoist_beam, rope. No loot tables (the blocks have none), no item models (no items).
- Do NOT write `#minecraft:climbable` or the runner tags: the architect writes those.

## Checks before you hand back
1. `python3 -I tools/art/build_all.py city_kit` and `python3 -I tools/art/specs/city_kit.py content` both run clean.
2. A script check: every blockstate variant key covers every property combination of the Java block (parse
   KitBlocks.java's enums or hard-code them), every model referenced exists, every texture referenced exists
   (`awaken:block/...` under textures/block, `minecraft:block/...` in the .ref assets).
3. Contact sheet: `python3 -I tools/art/preview.py /private/tmp/claude-501/-Volumes-MEDIA-Awaken/fe8b1e52-f061-4fc5-9684-cee87d25f5d1/scratchpad/kit-sheet.png`
   then crop it to the kit textures only (write your own sheet if simpler: only the files you wrote).
4. A facade mock `mock()` in the spec (run with `python3 -I tools/art/specs/city_kit.py mock <out.png>`): for each of
   Bellwatch, Market, Quay, Terrace, Garden, Works, a 7-wide × 13-tall elevation at 8× scale drawn from textures
   (vanilla wall textures + the kit): Bellwatch = stone ground storey (stone_bricks) + timber-framed upper storeys
   (stripped_dark_oak_log posts every 3, calcite panels, a leaded window per bay with shutters either side and a wood
   flower box in front of each window's lower half) + slate roof edge; Market = cut_sandstone ground with shop windows +
   white_terracotta upper storeys with sash windows and iron balconettes; Quay = stone ground with barred windows + bricks
   with warehouse windows either side of a loading-door stack; Terrace = calcite with arched windows and stone boxes;
   Garden = calcite with cottage windows, green shutters and a trellis with ivy; Works = bricks with works windows. Save
   it to the scratchpad as `kit-mock.png`.
5. Look at both images yourself (Read tool) and fix anything that reads as flat, muddy, too saturated or too smooth
   before handing back.

## Hand back (your final message, under 300 words)
The files you wrote, the commit hash, the two image paths, and anything you were unsure about.
