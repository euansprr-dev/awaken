# M4 Walls · Sonnet 1: the wall kit's art

You draw the art for six new Carillon kit blocks. The Walls builder (Opus) writes all Java and the painters; you write
only `tools/art/specs/city_wall.py` and the assets it generates. Work in the worktree
`/Volumes/MEDIA/Awaken/.claude/worktrees/walls-74d4bc` (absolute paths, start every Bash command with
`cd /Volumes/MEDIA/Awaken/.claude/worktrees/walls-74d4bc &&`). Never run Gradle, never edit Java, never commit, never
touch another worktree. Effort: extra high.

## Read first
- `tools/art/specs/city_street.py` (the street kit, B3a): your template for structure, the World / Atlas / MB model
  builder, `E(...)` with `emit` for emissive faces, animated textures (.mcmeta), `content`, `check`, `sheet`, `mock`,
  `view` subcommands, lang and mineable tag output. Copy its conventions exactly; import helpers from `city_kit.py` and
  `pixelart.py` the same way. Also how `tools/art/build_all.py` discovers specs (add `city_wall` the same way
  `city_street` is wired).
- `src/main/java/dev/awaken/city/kit/WallBlocks.java`: the block classes. Your blockstates must cover exactly these
  properties and values (lower-case names), nothing more.
- `docs/carillon/briefs/RULES.md`, "The repo is public": never copy Mojang textures. Referencing a vanilla texture by
  id in a model (`"minecraft:block/stone_bricks"`) is fine and is what the arrow loop must do.

## The register
Same as the street kit: 16x16 per face, crisp, 4–6 tone ramps, light from the top-left, colours anchored on real
vanilla textures. It must sit beside vanilla stone bricks, deepslate, spruce and iron bars without looking like an
add-on. Models drawn for facing=north with the wall at z=16, rotated by the blockstate (rot_variant).

## The six blocks (ids `awaken:<name>`)

1. **arrow_loop** — props `facing` (4), `style` slit|cross|oillet|window, `stone`
   stone_bricks|mossy|deepslate|calcite|bricks|tuff, `part` single|lower|upper. A FULL cube of the stone with an
   opening cut through its north face, using that stone's vanilla texture by id (stone_bricks → `block/stone_bricks`,
   mossy → `block/mossy_stone_bricks`, deepslate → `block/deepslate_bricks`, calcite → `block/calcite`, bricks →
   `block/bricks`, tuff → `block/tuff_bricks`) with UVs that continue the pattern, so the block is seamless in a wall of
   that stone. Build it from boxes around the opening; the opening's reveals (its side, top and bottom faces) use the
   stone texture too, and its back (about 12 px deep) is a dark recess texture you draw (deep shadow, a hint of a timber
   shutter or iron grille far inside, not flat black). The other five faces are the plain stone.
   - slit: 2 px wide; single from y 2 to 14; lower from y 3 to 16; upper from y 0 to 13 (two stacked read as one long
     loop with a splayed foot and head: widen to 4 px over the last 2 px at the ends).
   - cross: the slit plus a cross arm 2 px tall, 8 px wide at mid-height (single and upper; lower is a plain slit).
   - oillet: the slit ending in a round 4 px hole at its foot (single and lower; upper plain).
   - window: a small arched window for tower rooms, 6 px wide, 10 px tall in single (round arch head), with a stone
     sill. lower/upper make a 2-block tall window (head in upper, sill in lower). Interior dark as above.
   Render: solid (cutout not needed: the recess is geometry). Mineable pickaxe.

2. **wall_ivy** — props `facing`, `density` 0|1|2, `tone` green|dark|autumn. Ivy grown flat over a wall (hedera:
   3–5-lobed leaves, glossy dark green with paler veins, woody brown stems). Model: a plane 0.2 px off the wall face
   (z 15.8) with the leaf texture (cutout), plus 6–12 small leaf clumps as boxes 1–2 px proud for depth (fewer at
   density 0). density 0: mostly stems with sparse leaves (the edge of a patch); 1: patchy, stone shows through;
   2: a full mat. Tones: green, dark (older growth, deeper and bluer), autumn (a mix of deep red, rust and some green).
   Two texture variants per density/tone picked by weighted blockstate variants so a big patch doesn't tile. No tint
   index (own colours). Render type cutout. Mineable axe (or none, as the trellis).

3. **portcullis** — props `axis` x|z, `part` top|middle|bottom. A gate grid of dark weathered oak bars with iron
   strapping at every crossing: vertical bars 2 px wide every 4 px (centres at 2, 6, 10, 14), horizontal bars 2 px tall
   at y 3 and y 11, the whole lattice 2 px thick centred (z 7–9 for axis x). Open squares are see-through (cutout).
   bottom: the vertical bars end in forged iron spikes (pointed, 3 px) below y 3, no lower horizontal bar.
   top: a heavier iron-bound head beam at y 13–16. Mineable axe.

4. **brazier** — props `facing`, `mount` floor|wall. floor: a black wrought-iron tripod (1 px legs splayed) holding a
   basket bowl (rim about y 9–12, 10–12 px across, iron straps) heaped with glowing coals, and a flame rising from it to
   about y 16+ (two crossed planes, may rise above the block a little). wall: an iron bracket from a small wall plate at
   z 16 holding the same basket (centre about z 9, rim y 10–13) with its flame. Flame and coals are emissive (full
   bright, as the street lanterns' glass) and animated: 8–12 frames, a slow, irregular lick (no strobe), warm orange to
   yellow with a white-hot core, a few sparks. This is the city's beacon on gate towers and the wall walk at night:
   make it read from 60 blocks away. Mineable pickaxe.

5. **wall_spout** — prop `facing`. A carved stone drain spout thrown clear of the wall: a trough 6 px wide (x 5–11),
   y 4–10, running the full depth from the wall (z 16) out to z 0, open on top with a dark wet channel, ending in a
   small grotesque head (a snarling beast mouth) at z 0. Pale weathered limestone grey to sit with stone bricks, moss and
   a dark water stain. Mineable pickaxe.

6. **wall_shield** — props `facing`, `arms` carillon|bellwatch|quay|market|terrace|works|garden|wallring|association.
   A painted heater shield 14 px wide (x 1–15), full height, 2 px proud of the wall, with a bronze rim and weathered,
   chipped paint. The district arms match the banner_cloth designs in city_street.py (same colours and emblem idea, so
   a banner and a shield of one district read as one family). carillon, the city's arms: a gold bell on deep blue over
   a white wavy band (the river). association: the Hunter Association's (as its banner). No collision. Mineable axe.

Lang names (en_us, merged in `src/main/merge/city/assets/awaken/lang/en_us.json`, the street kit's way): Arrow Loop,
Wall Ivy, Portcullis, Brazier, Wall Spout, Wall Shield. Mineable tags as listed, in `src/main/merge/city/data/...`
the way city_street.py writes them (append, don't overwrite others' entries).

## Deliver
1. `python3 -I tools/art/build_all.py city_wall` and `python3 -I tools/art/specs/city_wall.py content` run clean;
   `check` passes.
2. `sheet` → `docs/carillon/milestones/m4walls/kit_sheet.png` and `mock` →
   `docs/carillon/milestones/m4walls/kit_mock.png` (every model, a few states each, at a size I can judge).
3. Report in under 200 words: files written, state counts, anything you couldn't do. Do not commit; I review and commit.
