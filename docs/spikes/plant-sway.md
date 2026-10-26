# Swaying plants: proof and cost (SPEC 15, 2026-10-26)

Grass, flowers, crops, vines, kelp and pads sway in the same wind and gust fronts as the waving leaves, through the
same Sodium shader override (docs/spikes/waving-leaves.md). How it works is in SPEC 15, "Swaying plants". This page
holds what the tours showed.

## Proof (docs/spikes/plant-sway/, built by strip.py and front_trace.py)

Final tour (`tools/autopilot/plant-sway.txt`, Vulkan on MoltenVK 1.4.2, `PACK=perf`, render distance 16, 2880 x 1800):
a studio at y 200 (a 225 x 225 meadow with tall grass and flower patches, a grove east of it, a hanging garden west).
Each strip is 8 frames 1 s apart from a fixed camera, with a showcase front (`/awaken terrain wind front`).

- `meadow_strip.jpg`: the open meadow, a front rolling across the view; 16-20% of pixels change between frames.
  `meadow_front.jpg` is each frame's difference from the run's mean (x12): the band the front has passed is lighter,
  and its edge steps left to right. `meadow_crop.jpg`: full-size crops of the same patch, frames 1-8.
- `edge_strip.jpg`: the grove, a front from behind the camera over the grass and on into the trees.
- `vines_strip.jpg`, `vines_crop.jpg`: vines on a log wall; weeping and cave vines, pale hanging moss, lumen vines,
  weepwood tendrils, hanging roots, a spore blossom. Strands swing from their anchors, with no gap between blocks.
- `feet.jpg`: the player standing in grass, push on (blades fan out round the legs) and off (they stand through them).
- `natural_meadow_crop.jpg` (first tour, OpenGL, a natural meadow): tall grass leaning frame to frame, halves joined,
  bases on the block.

## Cost (p50 frame time, 10 s windows)

| Scene | Comparison | Deltas |
|---|---|---|
| Studio meadow (every plant within the 112-block sway radius), Vulkan, after the fade | waving on vs the shader finding no wind (debug 1), alternated | +0.05, -0.03 ms |
| same | waving on vs Sodium's own shader (each switch reloads), alternated | +0.13, +0.19 ms |
| Flower forest at eye level, Vulkan, after the fade | on vs debug 1 | +0.30, +0.16 ms |
| Sparse hillside meadow, OpenGL, before the fade | on vs debug 1 | +0.36, +0.26 ms |
| Flower forest (camera in a crown), OpenGL, before the fade | on vs debug 1; on vs Sodium's own | +0.17, +0.30; +0.18 ms |

The machine was at load 12-18 during the final tour (other builders), so single windows swing by about 0.1-0.3 ms,
and frames speed up by about 0.5 ms over a minute as chunks settle. Hence the alternation, and the first window after
each arrival is not trusted.

Reading: before the fade, plants cost about 0.3 ms in a sparse meadow. Cross models cull no faces, so every plant
quad is drawn, and far ones are most of a meadow's vertices. Fading the sway out between 72 and 112 blocks (no texel
read past 112) and cutting the reads from 14 to 9 put the plants' own work in the densest meadow within noise. Against
Sodium's own shader the whole override costs about 0.16 ms there: the fixed cost of Awaken's larger vertex shader on
every terrain vertex. The forest's +0.16 to 0.30 includes the leaves' sway, which is unchanged and has no distance
fade. `/awaken terrain waving debug 3` (plants still and skipped, leaves waving) splits the two on the next perf run.
It was added after the final tour, so it is not measured yet.

## Tours

Five runs of about 5-6 min each, more than the one-tour budget:
1. OpenGL (a fresh game folder's first launch). `visit` put the meadow camera on a sparse hillside, the forest camera
   inside a tree crown and the jungle camera inside a bush. Motion looked right; cost over budget, which led to the fade.
2-4. `awaken admin awaken` was left out of a saved world, so the opening cinematic held the camera on a Carillon balcony
   all run. Every frame and perf window shows the city. Only the hanging garden, built near that balcony, filmed
   properly. Discarded (the tour-gotchas memory already says every tour needs that line).
5. The final run above: a studio, so no biome heart can drop the camera into Carillon's basin or a tree.
