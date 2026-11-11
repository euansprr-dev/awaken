# Brief 03: light you can see in the air

**Why.** Euan, 10 Nov: "the atmosphere in the air. Maybe we can see with the new shader we build how that can be adapted to all of
these gates as well, to increase and massively like improve the atmosphere of each gate, and then really push this to the maximum
immersion level." Every room of a rolled dungeon now has a well of light: a slot in the roof, real light pooling on the floor. But
between the slot and the pool there is nothing. A beam down each well and mist on the floors were tried as particle sprites on 11
Nov, three ways, each walked in the real game, and all three were taken out (SPEC §50.9 "Air" has the whole story): Minecraft's
particle shader throws away anything under a tenth opaque, so a faint sprite is never drawn, a coarse one reads as sheets of
glass, and a fine-grained one as static. And nothing in Lens reads a gate's shafts or mist. So this needs a small renderer of the
gates' own.

**Done looks like.** In a real-game shot of a Lord's hall, a soft beam of light stands from each roof slot to its pool, brightest
down its middle, fading smoothly to nothing at its edge, with no hard outline anywhere; low mist lies on the floors that have a
mist layer, thinning smoothly at its top and where it meets a wall. Walking through a beam does not veil the whole view. The Hour
Crypt and Amber Reach (the two gates Euan called the bar) look the same or better. With Lens off it still looks right. It costs
under half a millisecond a frame.

## What already exists (read these)

- `gates/world/FloorAir.java`: what a land says about its air. `Shaft(x0,y0,z0,x1,y1,z1,radius,rgb,strength)`, `Mist(y,thickness,
  rgb,density)`, `Light`, `Water`. Sent to the client on arrival (`gates/net/WorldPayloads.Arrival`: `air()`, `origin()`; land
  coordinates are relative to `origin`), held by `gates/client/GateWorldClient` (`air()`, `origin()`; empty outside a gate).
- `gates/realms/kit/AirPlan.java` builds it; `GeneratedCrawl.airOf` adds a shaft down every well and mist layers on the biggest
  rooms' floors; hand-built lands add their own (The Hour Crypt's moonlight shafts, Amber Reach's valley mist).
- `gates/client/WorldAir.java`: the small life (motes in shafts, wisps over water). Leave it as it is.
- The Lens post chain (`dev.awaken.lens`, `assets/awaken/shaders/post/lens_*.fsh`) blooms and hazes what is bright. It is another
  module: do not edit it. Your beams are drawn into the world before it, so it will pick them up.

## What to build

A client-only class, `gates/client/AirLight.java`, registered from the gates client entry, that each frame draws the nearest
shafts and mist layers of `GateWorldClient.air()` with **its own render pipeline and its own shaders**, so that nothing passes
through the particle shader.

1. **Find the right hooks in 26.3 before writing anything.** The render API changed (render pipelines, render states). In
   `/Volumes/MEDIA/Awaken/.ref/neo` and `.ref/src` find: how a mod registers a `RenderPipeline` and core shaders
   (`assets/awaken/shaders/core/`), which level render stage or event lets a mod draw translucent world geometry after the
   translucent terrain and particles, how to get the camera position and the view and projection matrices there, and how to submit
   a quad buffer with a custom vertex format or with position, colour and UV. Look at how this repo already draws custom world
   geometry on the client (grep `RenderPipeline`, `RenderLevelStageEvent`, `submitCustomGeometry` under `src/main/java/dev/awaken`)
   and follow the house pattern. Write the hooks you chose and why at the top of the class.
2. **A beam** is a ribbon along the shaft's line that turns about that line to face the camera (an axial billboard), `radius` wide
   each side. The fragment shader, not a texture, makes it soft: brightness falls smoothly from the middle to nothing at the edge
   (a squared cosine or a gaussian across it), fades in over the top eighth and out over the bottom fifth where it meets the pool,
   and drifts very slowly with two octaves of noise so it looks like dust in light, not a tube. **Additive** blending, depth test
   on, depth write off, no culling. Draw two or three ribbons per shaft at slightly different widths and brightnesses so the core
   reads brighter. Colour is the shaft's `rgb`, brightness its `strength` times a client setting.
   When the camera is inside the beam's radius, fade the beam toward nothing over the last two blocks, so it never veils the view.
3. **Mist** is a flat disc of fog centred under the camera at each mist layer whose height is within its thickness plus eight
   blocks of the camera: a few stacked horizontal sheets through the layer's thickness, alpha from `density`, fading smoothly to
   nothing at forty blocks out, lightly animated by noise, ordinary alpha blending, depth test on, depth write off. Rock hides it
   where there is rock, because it is depth tested. Looking straight down at it must not show a hard disc edge.
4. **Soft where it meets the world**, if the stage you draw at lets you read the depth buffer cheaply: fade a fragment as it nears
   the geometry behind it. If not, say so and leave it; do not copy the depth buffer every frame to get it.
5. **Budget and limits.** At most the twelve nearest shafts within sixty-four blocks and the layers near the camera. No allocation
   per frame. Measure it (the repo has frame-time probes in other client features: follow one) and report the number.
6. **A client setting**, `gateAirLight` (on; a strength from 0 to 1.5), in the client config beside the other gate client options.
   Off must mean exactly today's picture.
7. **The mist layers in rolled dungeons** are already written down for the biggest rooms (`airOf`). Once mist is drawn, look at
   whether three blocks deep at 0.4 is right in the shots and say what you would change; do not change `airOf` (it is not yours).

## Checks

- `tools/check-ci.sh compile`.
- Real-game walks, read shot by shot, each **with and without** the setting so the pairs can be laid side by side:
  `realm-catacombs` (two wells in the Lord's hall), `realm-root_cellars` (dark green, beams must not wash it out), `realm-rime_hollows`
  (the brightest kind: beams must not blow out), `realm-hour` (The Hour Crypt's moon shafts) and `realm-amber` (open land, valley
  mist). One of them again with `LENS=off`.
- In every shot look for: a hard edge, a visible rectangle, banding, a beam seen through a wall, mist seen through a floor, a
  whole-screen veil. Any of those is broken, not taste.
- If the 26.3 API cannot give you an additive pass with your own shader at a sane stage, **stop and hand back BLOCKED** with what
  you found. Do not fall back to particles.

## Yours

`gates/client/AirLight.java`, its shaders under `assets/awaken/shaders/core/gate_air_*`, one registration line in the gates client
entry, the one client option in `core/AwakenConfig.java`. SPEC §38.9 (a paragraph "Light and mist in the air") and §50.9 "Air"
(replace "Until one of those exists" with what now exists). Nothing in `dev.awaken.lens`, nothing in `WorldAir`, no mixin.

## Traps

- Additive light over a bright scene blows out. Scale brightness down where the shaft's colour is already near the scene's.
- A ribbon seen end-on (looking straight up a well) is a line. Add a small soft disc at each end facing the camera, or accept it
  and say so.
- Land coordinates are relative to `GateWorldClient.origin()`. The Hour Crypt's shafts run from the rock's top to a floor far
  below: clip a beam to sixty-four blocks from the camera.
- Old gate worlds (the legacy floors) also send shafts. Walk one (`tools/autopilot/` has their tours) and make sure they look
  right too, or draw only in Realms and say why.
