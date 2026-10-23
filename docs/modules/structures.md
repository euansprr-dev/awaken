# Structures: one visual language for the overworld

The overworld's built things are the Signal's gates, the Precursors' ruins and their Well, and the named beasts' lairs.
They were add-ons: small, flat, single-material boxes. This page is the art direction they are now built to; the code
follows it (`gates/Overworld`, `gates/GateVortex*`, `gates/client/GateVortexRenderer`, `above/ruin/RuinBuilder`,
`descent/well/Well`, `world/fauna/lair/LairBuilder`).

## Three families, three lights

| Family | Who made it | Story it tells | Light that names it |
|---|---|---|---|
| **Gates** | the Signal | a wound in the world, tearing the ground toward itself | the **rank colour** (E pale, D green, C blue, B violet, A amber, S red, Sovereign gold on black) |
| **Precursor** (ruins, the Well) | the Ascendants, Keepers and Sleepers | engineers who studied the gates and lost; their machines still hum under moss | **signal blue** for anything still alive, **amber** for warnings and sealed doors |
| **Lairs** | the named beasts | a home: where it sleeps, eats, hoards and marks its ground | warm natural light (lanterns, froglights, shroomlights, glow lichen) |

Dark slate, amber lantern and signal blue stay the mod's identity: every Precursor build is slate-blue plating with brass
and blue light lines; amber is never decoration, it marks a door, a warning or something you can open.

## Palette by kind

| Kind | Body | Accent | Light | Weathering |
|---|---|---|---|---|
| Gate | `gate_frame` (obsidian with a signal glyph), blackstone, basalt, polished blackstone | `gate_frame_cracked` (broken), cracked deepslate tiles | the vortex itself; `gate_fissure` (ground cracks in rank colour, red when broken) | scorched ground: coarse dirt, tuff, blackstone, basalt, gravel, by noise |
| Vault | `precursor_plating`, `ruin_pillar`, deepslate tiles | `precursor_trim` (brass), `ruin_glyph` | `precursor_glow_panel` lines, `precursor_warning_light` at the door | `ruin_plating_cracked`, `ruin_plating_mossy`, moss, vines, cracked deepslate |
| Monolith | `precursor_plating`, `ruin_pillar` | brass bands, `ruin_glyph` faces | amber crown light, blue glyph slots | buried in sandstone: cut/smooth/chiselled sandstone, sand drifts |
| Ocean lab | `precursor_void_glass` domes on plating rings | `precursor_trim` | glow panels and sea lanterns inside every dome | prismarine and dark prismarine creep, kelp, coral fans |
| Sky platform | plating disc and a glowing keel | brass rails, chains | glow underside (seen from the ground), cabin light | floating islands of stone, dirt and grass torn up with it, chains hanging |
| The Well | `well_stone`, deepslate tiles, polished deepslate | `well_glyph`, chiselled deepslate | `well_light` lanterns at the pillar tops and in the shaft | cracked deepslate tiles, moss on the plaza edge |
| Lairs | the biome's own materials | the beast's sign: bones, silk, petals, horn, mycelium | one warm light at the den | trampled ground, scattered remains |

## Motifs (what ties them together)

- **The ring.** The Well's ring of pillars, the vault's door ring, the sky platform's disc, the ocean lab's domes and the
  gate's vortex are all circles: the Signal's shape, which the Precursors copied.
- **The glyph.** The signal glyph on `gate_frame` is the same mark as `well_glyph` and `ruin_glyph`: the Precursors wrote
  in the gates' alphabet. It glows blue on Precursor stone, in rank colour on a gate.
- **Torn ground.** Gates and the sky platform both rip the ground up: scorched craters and floating rock.
- **Light guides the eye.** Lights mark the way in and the goal (the door, the cache, the den). By night a structure is
  its silhouette plus its light lines; by day its shape.

## Scale rules

- Readable from 150+ blocks: ruins and the Well have one vertical element at least twice the height of the trees
  around (12+ blocks); gates grow with rank; lairs are homes (radius 8-11, one focal element 5-9 blocks tall).
- Depth: no flat wall of one material larger than 5x5. Walls get a plinth, a cornice and at least one inset or
  buttress; openings get a frame one block deep.
- Weathering: 10-25% of surface blocks swapped for a weathered variant, placed by smooth noise (patches, not speckle),
  more at the base and on the tops. Nature takes the edges: moss and vines on Precursor stone, ash on gate ground.
- Terrain: foundations go down to real ground; the outer ring follows the terrain column by column; nothing floats
  unless it is meant to (sky platform, gate shards).

## Gates

The vortex hangs a block above a scorched pad; the bottom of the opening is where you walk in. Sizes (radii of the
ellipse, in blocks): E 2.6 x 3.3, D 3.2 x 4.0, C 3.9 x 4.9, B 4.6 x 5.8, A 5.4 x 6.8, S 6.5 x 8.2, Sovereign 7.5 x 9.5.

- **Surface:** one continuous surface drawn by a block entity renderer on the anchor core (`GateVortexRenderer`): a dark
  heart, layered spiral arms (a logarithmic spiral turning smoothly, so the arms flow inward), a bright rim in the rank
  colour, a soft glow halo, motes spiralling in, and rock shards orbiting the rim. One texture atlas
  (`textures/entity/gate_vortex.png`, filtered) so every layer is sorted together. The core blocks are invisible and
  still carry the interaction.
- **Ground:** a flat scorched pad (radius = vortex width + 2) with fissures radiating from the base in rank colour, a
  ragged crater ring outside it that follows the terrain, and rock spurs (frame stone with blackstone and basalt)
  leaning out from both sides of the opening, taller with rank.
- **States:** forming (grows from a point), open, about to break (pulses, the rim flickers, cracks spread over the
  ground), broken (red-black, turbulent, ash, cracked spurs, shards fallen), calm (the story's ending: slow, pale,
  still), spent (cleared: shrinks and dims).
- **Sound:** a looping hum that swells as you come near (within 48 blocks), lower for higher ranks, harsher when broken,
  soft when calm.

## Decisions (for SPEC)

See the end of this page once the build is complete.
