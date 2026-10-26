# M4 Commons · Sonnet 1: the commons kit's art

You draw the art for four new Carillon kit blocks for the countryside outside the city wall. The Commons builder (Opus)
writes all Java and the painters; you write only `tools/art/specs/city_commons.py` and the assets it generates. Work in
the worktree `/Volumes/MEDIA/Awaken/.claude/worktrees/walls-74d4bc` (absolute paths; start every Bash command with
`cd /Volumes/MEDIA/Awaken/.claude/worktrees/walls-74d4bc &&`). Never run Gradle, never edit Java, never commit, never
touch another worktree. Effort: extra high.

## Read first
- `tools/art/specs/city_wall.py` (the wall kit, the same builder's last kit): your template for structure: the World /
  Atlas / MB model builder from `city_street.py`, `rot_variant`, `weighted`, the `content`, `check`, `sheet`, `mock`,
  `view` subcommands, lang and mineable tag output. Copy its conventions exactly and import helpers the same way.
  `tools/art/build_all.py` discovers specs by file name.
- `src/main/java/dev/awaken/city/kit/CommonsBlocks.java`: the block classes. Your blockstates must cover exactly these
  properties and values (lower-case names), nothing more.
- `docs/carillon/briefs/RULES.md`, "The repo is public": never copy Mojang textures. Referencing a vanilla texture by
  id in a model (`"minecraft:block/hay_block_side"`) is fine.

## The register
Same as the street and wall kits: 16x16 per face, crisp, 4–6 tone ramps, light from the top-left, colours anchored on
real vanilla textures. It must sit beside vanilla grass, farmland, wheat, hay bales, cobblestone and spruce without
looking like an add-on. Models are drawn for facing=north and rotated by the blockstate (rot_variant). Everything is
seen at noon in fields and on a road, from 2 to 60 blocks away: shapes must read in silhouette.

## The four blocks (ids `awaken:<name>`)

1. **milestone** — prop `facing`. A squat roadside milestone: a weathered limestone post 8×8 px (x 4–12, z 4–12),
   13 px tall with a rounded (domed or chamfered) head, slightly battered foot. On its north face (the road side) a
   sunk panel with the city's bell cut into it (simple bell silhouette, shadowed recess), and a pale band under the head.
   Lichen (pale yellow-green specks) and moss low on the side, a darker damp foot. Limestone close to the wall kit's
   weathered limestone ramp. Render solid. Mineable pickaxe.

2. **fingerpost** — props `facing`, `half` lower|upper, `to` camp|mine. A two-block oak fingerpost, weathered grey-brown
   timber. lower: a square post 4×4 px (x 6–10, z 6–10) full height, a little darker and mossy at the foot. upper: the
   post continues to y 13 under a small pyramidal cap (y 13–16, 6×6 px). Two arm boards at y 7–11 and y 2–6:
   - the upper arm points north (z 0 side, out from the post to z 0, about 9 px long, 3–4 px tall, 1 px thick on the
     x axis, x 7.5–8.5... make it 1.5–2 px thick so it reads), ending in a pointed tip; painted on both faces with the
     destination's pictograph in faded paint: `to=camp` a small campfire (crossed logs and a flame) in warm orange,
     `to=mine` crossed picks in dull grey on the board;
   - the lower arm points south (to z 16), the same shape, painted with the city's bell in faded gold on blue.
   The boards' paint is chipped; their ends show end grain. No text (pictographs only). Render cutout if needed.
   Mineable axe. The lower half's item/blockstate need not show arms.

3. **scarecrow** — props `facing`, `half` lower|upper. A farm scarecrow on a stake, two blocks tall, facing north.
   lower: a rough stake 2×2 px from y 0 to 16, with straw-stuffed trouser legs (two short legs hanging from about y 9,
   tied at the ankles, straw tufts sticking out) and the hem of a ragged long coat (y 10–16, about 8×4 px, patched
   brown and faded green). upper: the coat's body (y 0–7) with a rope belt; a crossbar arm (y 6–8) the full width x 0–16
   with the coat's sleeves on it and straw bursting from the cuffs; a sacking head (y 8–14, 6×6 px, stitched face:
   two button eyes and a stitched mouth) under a battered wide-brimmed straw hat (y 13–16, brim 10–12 px wide).
   Render cutout. Mineable axe.

4. **stook** — prop `facing`. A stook of wheat: six to eight sheaves stood leaning in together (a cone/tent shape about
   12 px across at the foot, 14 px tall), each sheaf a bundle of golden straw stalks tied with a band at its waist, the
   ears heavy at the top. Built from a few crossed/angled planes and boxes so it reads as a bundle, not a flat sprite.
   Gold to pale straw ramp matching vanilla wheat stage 7 and the hay bale. Render cutout. Mineable hoe (or axe if the
   kit uses none).

Lang names (en_us, merged in `src/main/merge/city/assets/awaken/lang/en_us.json` the wall kit's way): Milestone,
Fingerpost, Scarecrow, Wheat Stook. Mineable tags in `src/main/merge/city/data/...` the way city_wall.py writes them
(append, don't overwrite others' entries).

## Deliver
1. `python3 -I tools/art/build_all.py city_commons` and `python3 -I tools/art/specs/city_commons.py content` run clean;
   `check` passes.
2. `sheet` → `docs/carillon/milestones/m4commons/kit_sheet.png` and `mock` →
   `docs/carillon/milestones/m4commons/kit_mock.png` (every model, a few states each, at a size I can judge). Look at
   them yourself at full size and fix what reads poorly before you report.
3. Report in under 200 words: files written, state counts, anything you couldn't do. Do not commit; I review and commit.
