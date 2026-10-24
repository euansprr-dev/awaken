# Awakened Lands: the overworld's own look

Plan: `docs/plan-lands.html` (approved 2026-10-24). Contract: `docs/SPEC.md` §15. Code: `dev.awaken.terrain`
(studio commands, the lands pack, biome table API), `dev.awaken.terrain.client` (wind, falling leaves, waving).
Data: `src/main/resources/datapacks/awakened_lands/` (new worlds only). Art: `tools/art/specs/lands.py`.
Studio: `tools/terrain/studio.sh`, `tools/terrain/survey.py`, `tools/autopilot/terrain-studio.txt`.

## The story the land tells

The gates are not new. Long before the player, the Signal's energy soaked into the land wherever its old gates stood,
and the land woke a little: trees grew past any natural size, moss learned to glow, sand ran to glass, mountains split
along lines of light. Those places are the **Awakened lands**. They keep the climate of what they used to be (an
Elderwood was a birch wood, a Lumen Forest a dark forest), so they sit naturally among vanilla biomes, and they come in
regions you walk *into*: the `awaken:lands/awakening` field is a slow noise, so Awakened lands are patches hundreds of
blocks across, never confetti.

## Six signatures every Awakened biome has

A biome is only done when it has all six, each readable at its own distance:

| Signature | Read from | Rule |
|---|---|---|
| **Silhouette** | the horizon (far terrain shows logs and leaves only) | one tree or landform shape no vanilla biome has |
| **Ground** | a mountain top (far terrain shows the main ground block) | a main surface colour distinct from grass, sand and snow |
| **Night light** | 100+ blocks at night | a glow of its own, or a deliberate darkness |
| **Air** | standing in it | something drifting: leaves, petals, motes, ash, dust, snow |
| **Sound** | eyes closed | an ambience bed of its own |
| **Light** | the screen's mood | a Lens look that eases in as you enter |

## Trees

- **Data trees** (vanilla trunk and foliage placers, Awaken logs and leaves): the common trees. Canopies stay within
  about 12 blocks of the trunk (a feature may write one chunk beyond its own). Variety comes from 2–3 sizes and shapes
  per biome, never one tree copied.
- **Hero trees** are structures (placed like lairs, `structure_set` in the lands pack, biome tag from the table): the
  giants you remember and steer by. Rare: roughly one per 300–500 blocks in their biome. Each one costs at most 2 ms to
  build and never writes outside its reserved box.
- Logs join `#minecraft:logs` (and `#minecraft:logs_that_burn` where they burn), leaves `#minecraft:leaves`, so gates,
  stars and every vanilla rule treat them as trees. Leaves decay like vanilla.
- Fuller trees come from art and silhouette, not extra leaf layers: every see-through leaf pixel is costly on Apple
  GPUs. Canopy masses are clusters with hollow cores (MoreCulling then culls the inside faces).
- No ticking or growing trees, no block entities in trees.

## Leaves in the air

Wind (`terrain.Wind`): direction, strength and gusts from world time and seed, the same on every client, no packets.
Falling leaves come from Awaken's own particle pool, not the vanilla particle: coloured from the leaf block (its tint
or its texture), sized and shaped per tree family, tumbling, carried by the wind, streaming in gusts, settling for a
moment on the ground before fading. Capped (about 150 at once), scaled down by the frame budget, spawned only from
leaves with air below within ~32 blocks. Vanilla leaves get them too (subtler); Awakened trees get their own colours.

## The first two (the vertical slice)

### Elderwood (Greenwild; replaces forest, birch forest, old-growth birch forest and flower forest in the field)

- **Silhouette:** towering pale trunks under a high gold-green canopy; once in a while an **Elder Colossus**.
- **Trees:** *Elder* (2×2 trunk, 18–26 tall, layered canopy of 2–3 masses, gold-green), *Young elder* (1×1, 9–13 tall,
  leaning), *Elder Colossus* (structure: trunk 6–9 across, 55–85 tall, buttress roots arching 8–14 blocks out,
  4–6 great limbs reaching 14–22 blocks, canopy clusters; a landmark from far terrain).
- **Bark:** pale silver-cream with dark vertical fissures (`elder_log`); planks warm cream.
- **Leaves:** gold-green, warmer at the canopy top (`elder_leaves`, fixed colours, not biome-tinted).
- **Ground:** moss and moss carpet in patches, gold leaf litter (the biome's dry-foliage colour is gold), ferns, a few
  fallen elder logs.
- **Air:** gold leaves drifting all day, thicker in gusts; dust motes in the light shafts.
- **Night:** fireflies near water; otherwise dark and calm.
- **Sound:** deep wind high in the canopy, slow creaks, birdsong by day.
- **Lens:** warm gold grade, soft haze, light shafts (Cinematic).

### Lumen Forest (Duskwood; replaces dark forest in the field)

- **Silhouette:** broad flat canopies on pale trunks, hanging vines; once in a while a **Lumen Spire** (structure,
  30–45 tall, a crown of hanging glowing vines).
- **Trees:** *Lumen tree* (2×2 trunk 10–16, broad flat dark-oak-like canopy), *Lumen sapling-size* (1×1, 6–9).
- **Bark:** ash white with faint blue veins (`lumen_log`).
- **Leaves:** deep teal to cyan with glowing specks (`lumen_leaves`: cutout leaves plus an emissive speck layer using
  model `light_emission`, no real light, so worldgen never runs light updates for them).
- **Ground:** lumen moss carpet (emissive specks), lumen caps (small glowing mushrooms), dense firefly bushes.
- **Air:** slow lumen motes at night (glowing, drifting up), pale spores by day.
- **Night light:** the whole forest glows softly blue-cyan; you can see it from a ridge.
- **Sound:** soft chimes, a low hum, owls.
- **Lens:** cool teal at night, bloom picks up the specks.

## How to check

- Studio: `tools/terrain/studio.sh <preset>` for maps and numbers; `/awaken terrain visit awaken:<biome>` to stand in one.
- Every Awakened biome: census test (archetype, fauna, cottage, spirits), a worldgen test that its trees and hero
  structures place on ground without floating or cutting, a cost bench, and a contact sheet (noon, golden hour, night,
  from the ground and from 60 blocks up).
