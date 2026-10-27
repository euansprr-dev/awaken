# Premium test, Look A: result

Branch `pack/armour-premium-test` (never merged). Veilwoven, the B set, on three mannequins, in the game on GitHub's macOS runners
(`tools/autopilot/gear-premium.txt`; Lens Swift, Lens Cinematic and Lens off). Three levels, switched live by `armour fidelity 0|1|2`:
Today (as it ships), Level 1 (the planned look: a model of real parts from `gear_premium/veilwoven.json`, vanilla's armour light, a glow
pass) and Level 2 (the same model drawn by our own shader, `arms_armour`). Pictures: `look-a/`.

## Verdict (Euan, 2026-10-27)

"These look still so clunky. I think the entire approach is wrong. It looks so weird and clunky. It doesn't look cool at all."

I agree with the read. Level 1 and 2 are a clear step up from Today in silhouette and colour, but neither looks premium:

- The forms are inflated boxes. The hood is a bucket with a peg on top, the mantle a slab, the cuffs grey-white blocks like tape. On a body
  that fills the screen that reads as a toy figure.
- The surfaces carry no information. One texel is one skin pixel, so a face of a part has 8 by 8 texels to say "silk" or "silver". The
  shader can only brighten and tint what is painted; it cannot add weave, grain, depth or wear.
- Level 2 over Level 1 is a small step by day (whiter metal, a hot emblem) and a visible one only at night (the emblem and gems glow white).
  It does not change how the set reads.
- So the weapons' rule (1 texel = 1 skin pixel, bevel bands) does not carry over to armour. Weapons are small objects; armour is the
  player's whole body. Copying the rule was the mistake, and my proof renders (a software renderer) flattered it.

## What the test measured

- Level 1 and 2 build and draw in the game from the spec; the face stays open (eyes show); fallbacks were not needed (the shader compiled).
- Level 1's glow pass drew nothing at first: `entityTranslucentEmissive` goes through the order-independent path, which drew nothing on
  CI's Vulkan. A full-light cutout pass fixed it (the second round of shots).
- Lens bloom: with Lens Swift there is a faint soft halo around the brightest gem; with Cinematic almost none; with Lens off a hard edge.
  Armour pixels get at most a trace of bloom. A set that should glow needs its own halo (a soft quad, or a glow in the shader's own pass).
- Not measured: the cost of 60 mannequins (needs the Mac), a walking film, Level 3 (non-box polygons, a chain cape). Not built, on purpose.

## What I would try instead (not started; Euan's call)

Change the form language and the texel density together, and judge one piece in the game before anything else:

1. Real shapes, not boxes: rounded and faceted meshes (our own polygons are possible: `ModelPart`'s public constructor, `Cube` with a
   polygon array), a hood that drapes, a mantle that tapers, plates with curved edges.
2. A denser texture (four texels to a skin pixel or more), with ambient shade baked from the geometry and a material map, so fabric,
   leather and metal have different surfaces.
3. Lighting that gives shape: a matcap or normal-map term in `arms_armour`, so metal and silk catch light differently.
4. One hero piece first (helmet and chest of Veilwoven), on the same CI tour, before any set is planned again.

Main plan (`PLAN.md`, decisions 1-39): unchanged and not approved; the sets in it are built from the box language this test rejected.
