# The premium test: one hero set at three levels of fidelity (plan for Euan's approval, 2026-10-27)

Status: **plan only, nothing built.** Branch `pack/armour-plan`. It follows Euan's question "why do they all look so chunky and blocky instead of
premium? ... is this the best level of premiumness we can get to?" and his answer: "Plan the premium test: one hero set at three fidelity levels (as
planned; plus the weapon shader and bloom; plus rigid-mesh hero parts and a chain cape), judged on a CI look tour. Plan only, tell me the cost first."
Labels: **READ** (read in this repo or in the 26.3 / NeoForge sources), **PROPOSAL**, **UNVERIFIED** (the first builder must check it).

## 1. The cost, first

| Step | Builder hours | Machine time | Is it work the plan needs anyway? |
|---|---|---|---|
| **0. Level 1, as planned:** the hero set through the real model path (a model built from the spec for all four slots, its textures and equipment JSON, a minimal glow layer, a dev switch `awaken gear fidelity 1/2/3`) | 3 | a build and the gear GameTests, about 20 min | **Yes**: it is a slice of Phases 0, 1 and 2 |
| **1. Level 2, plus the weapon shader and bloom:** an armour pipeline from `arms_weapon`, a material map in the generator, a layer that draws with it, bloom tuning | 3 | CI tour runs | **Yes if adopted**: it is the Phase 5 shader, moved up |
| **2. Level 3, plus rigid-mesh hero parts and a chain cape:** a model builder with non-box polygons, generator shapes (tapered plates, chamfers, cut gems), a four-link cape with a small simulation, the hero parts redrawn | 4.5 | CI tour runs | **No**: new |
| **3. The tour and the comparison sheet:** `gear-premium.txt` (three levels, same camera, day and night, Lens Swift and Cinematic, a walking film), a script that lays them side by side | 1.5 | two CI dispatches, about 10 min each | no |
| **4. The cost check:** 60 mannequins at each level on the Mac | 0.5 | about 30 min in the Mac's perf slot | no |
| **Total** | **12.5 builder hours** | **about 2 hours of CI and Mac time** | |

**Staged, so Euan can stop early.** Steps 0 and 1 and a tour give **Look A** (Level 1 beside Level 2) after about 7 builder hours, and Level 2 is the cheap, big
change. Step 2 starts only if Euan wants it after Look A, or runs beside step 1 with a second builder if he would rather not wait (about 9 hours of wall clock to the
three-up sheet, because Levels 2 and 3 both build on Level 1's model plumbing but not on each other).

**Net of the main plan:** Level 1 is already inside the 33 to 43 hours. Level 2 is the armour shader that was in Phase 5 (4 to 8 hours with the cuts and Marked armour),
so adopting it costs nothing extra beyond moving it up. Level 3 is genuinely new: about 4.5 hours for the proof, more for every set (a mesh pass is slower than a box pass,
perhaps 20 to 30 minutes more a set). If both are adopted and the test says so, expect about **5 to 8 builder hours on top of the main plan's total** for the kit, then
the per-set cost above. If neither is adopted, nothing is added.

## 2. Understanding and success contract

**The job.** Find out, by looking, how much of "premium" is in the lighting and material shading, and how much is in the shapes, before the plan is locked on the
cheapest path. Euan's bar, in his words: "someone thinks WTF I didn't even know this game could look so good."

**What decides it.** Euan, looking at one sheet. The test succeeds if it lets him choose, not if one level wins. A level fails if it looks worse than the one before it
at any camera, or if it costs more than the gate below.

**Gates.** (1) The Level 1 set passes the existing checks (overlap, face, rooted, connection: `docs/armour/proof/audit.py`). (2) At 60 mannequins in the hero set, Level 2
and Level 3 add at most **+1.0 ms** against Level 1, and Level 1 at most +1.0 ms against vanilla netherite (the main plan's gate), measured on the Mac. (3) Every level has a
plain fallback (a pipeline that fails to build draws the level below). (4) Nothing the test builds changes a stat, a recipe or a saved structure.

**Non-goals.** Final art for the set; the other sets; the garments; the sound; the first-person arm; real cloth physics beyond a four-link chain; shipping any of it.

## 3. What is verified (READ), and what is not

| Question | Answer | Where |
|---|---|---|
| Can our own shader draw armour? | Yes, in principle. The weapon pipeline is `ENTITY_SNIPPET` with our shaders, registered as an *optional* pipeline with a checked fallback; a render type is built with `RenderType.create(name, RenderSetup.builder(pipeline).withTexture("Sampler0", tex).useLightmap()...)`. A model part submits through `submitModel(model, state, pose, renderType, ...)` with any render type (the mobs' glow layer does). Armour is the same entity vertex format. | `WeaponPipelines.java:32-124`, `AwLayers.java:57-80`, `EquipmentLayerRenderer.java:88` |
| What changes in the shader? | The weapon's two custom vertex inputs (Color alpha as "how far along", UV1 as the blade's state) are replaced by what a model part already sends (the tint, the overlay). Quads, not triangles. The material map stays in the texture's right half (r metal, g gloss, b emissive, a flow). The sheen "along" the blade becomes the flow channel. | `arms_weapon.vsh`, `arms_weapon.fsh` |
| Can the vanilla layer be switched off for our own? | Yes. `getArmorLayerTintColor` returning 0 skips a layer entirely. | `IClientItemExtensions.java:196`, `EquipmentLayerRenderer.java:76-78` |
| Does bloom pick it up? | **UNVERIFIED.** Lens's prefilter blooms bright, saturated, non-warm glows up to 2.5× lit surfaces, and the weapon's emissive and halos feed it; whether the armour's emissive pixels pass the same test is the first thing the Level 2 builder checks. | SPEC §22 "Emissive glow", §25 |
| Do CI tours show the shader and Lens? | Yes. CI shows Arms' weapons, the shader and Lens Swift and Cinematic as on the Mac; its timings are meaningless, so the cost check stays on the Mac. | `ci-tours.md` |
| Can a model part hold non-box geometry without a mixin? | Yes. `ModelPart` has a public constructor taking its cubes and children; `ModelPart.Cube` is a non-final class whose polygon array is public; `Polygon(Vertex[] vertices, Vector3fc normal)` and `Vertex(x, y, z, u, v)` are public records, so a cube's faces can be any quads (tapered, chamfered, angled). A cube holds at most six polygons, so a part uses several cubes. The tree is built bottom-up in our own code (children first), so no access transformer and no reflection. **Compile-level, not yet run in game.** | `ModelPart.java:45, 233-247, 347, 372` |
| Is a rigid mesh part posed with the body? | Yes if it is a `ModelPart` in the armour model: the model's `setupAnim` poses it (called at draw time), the same as every box in `Silhouettes`. | `ModelFeatureRenderer.java:38` |
| Can a cape be a chain? | Yes: a model subclass poses link parts in `setupAnim` from a per-wearer state keyed by the entity id, updated once per client tick (the `WingLayer` pattern). | `WingLayer.java` |
| Is there a tour for armour on figures? | Yes: `awaken gear lineup` puts every tier on vanilla mannequins (avatar-format bodies, so our layers draw on them), `tools/autopilot/gear.txt` is the model, and the clip recorder with `anim_lab.py film` films motion. | `GearCommands.java:111-152`, `arms-film.txt` |

## 4. The three levels (what each one is, exactly)

The hero set is **Veilwoven (B)** (35 boxes, clean in the proof). It has silk (cloth), silver (metal), glass (emissive) and a hood with a peak, a cape and cuffs, so it
shows what each level does to every material. (Wyrmscale is the alternative: more glass and a tail, less cloth; Decision 35.)

**Level 1: as planned.** A model built from the spec (generalised `Silhouettes`, all four slots), the vanilla armour render type and vanilla light, plus the planned glow layer (the
emissive pass, `entityTranslucentEmissive`). Cloth parts are boxes; the cape is two links. This is what the clean proof renders show, in the real game.

**Level 2: plus the weapon shader and bloom.**
- A pipeline `arms_armour` (optional, with the fallback to Level 1) running a port of `arms_weapon.fsh`: painted two-light banding, a studio reflection on metals (silver cuffs
  and clasp look like metal), a gloss highlight, an accent rim (the tier's colour), the emissive channel pulsing along its flow (the thread and gems), the world's light on everything
  that doesn't glow, fog.
- The generator writes the **material half** beside the albedo: per material, metal (plate 0.5, silver 0.55, silk 0, glass 0.2), gloss, emissive (glass, thread) and a flow phase (so the
  thread lights in turn up the robe).
- An armour layer that draws the set with it, with the vanilla layer skipped by returning tint 0 at Level 2 and above.
- Bloom: the emissive output is tuned (brighter, saturated, not warm) to pass the prefilter; the builder checks it on a tour with `LENS=cinematic` and `LENS=swift`.

**Level 3: plus rigid-mesh hero parts and a chain cape.**
- A model builder that makes the armour model's `ModelPart` tree from the spec in our own code, with boxes where they serve and **polygon cubes** where they don't: a hood with a
  tapered brim and a peaked, curved tip; a capelet with a scalloped edge; chamfered cuffs; a cut-gem brow stone (facets, not a cube).
- Generator primitives (`wedge`, `chamfer`, `cut`) beside the box ones, with the same painted-bevel rules, and the audit extended to polygon parts (it already tests boxes by their
  oriented bounds; a tapered part is tested by its bounding box plus a point-in-hull check, UNVERIFIED how tight).
- **A four-link cape** (instead of the two): each link follows the one above with a lag, a spring and a damper, driven by velocity and turn rate, clamped so it never enters the legs or
  the wings, reset on teleport, level change and respawn, animated for the nearest 32 wearers only.
- Asymmetry: one pauldron larger, a hood tail on one side. (Everything so far has been mirrored.)

## 5. How it is judged

- **The tour** `tools/autopilot/gear-premium.txt` (on a branch `pack/armour-premium-test`, never merged; Euan's rule: builders never launch the client, so it runs on CI with `tools/tour-ci.sh`):
  the hero set on mannequins at fidelity 1, 2 and 3 in turn, **the same camera for all three**, day (noon) and night, front, three-quarter, back and a close-up of the hood and cape,
  each with Lens Swift and with Lens Cinematic; and a **walking film** of the player wearing it in third person (walk, sprint, turn, jump), recorded with the clip recorder.
- **The sheet:** a script lays the frames out three across (A, B, C, unlabelled until Euan has chosen), per camera, plus the film strips. Euan answers one question for each camera: which would
  make a stranger say "WTF".
- **What the sheet cannot show:** CI draws about 10 frames a second, so the cape shows as poses, not motion (the same limit as the Arms sheets); the film is strips, not video. If Euan wants to
  see the cape move, that is one Mac tour in the perf slot.

## 6. Failure register for the test

| # | Case | Detection | Containment | Recovery |
|---|---|---|---|---|
| 1 | the armour pipeline fails to build (a driver, a shader pack) | `RenderSystem.getCompiledPipelineNullable` check, as the weapons do | the level below draws | none needed; logged once |
| 2 | bloom does not pick up the emissive pixels | the tour's Cinematic frames | tune the glow colour and brightness; if it still fails, the Level 2 glow draws as the halo fans the weapons use (additive, the weapon glow pipeline) | +1 h |
| 3 | custom polygons do not render or bake in game | the first tour frame | Level 3 falls back to boxes for that part | +1 to 2 h; if it cannot work, Level 3 is dropped and the plan says so |
| 4 | the cape clips legs or the Ascent wings | the walking film | angle limits and the fold-away pose (as the main plan's 4.4) | tune |
| 5 | a mannequin's pose differs from a player's | the first frames | use the player in third person for the walking film only | none |
| 6 | the second level costs too much at 60 | the Mac cost check | report the number; Euan decides whether the look is worth it, or whether it is only for the top tiers | none |
| 7 | the test branch drifts from the plan | it is cut from `pack/armour-plan` at one commit | nothing merges back except cherry-picked, reviewed parts | none |

## 7. What happens next

- **If Level 2 is the leap:** the armour shader moves from Phase 5 to Phase 2 in the main plan, the material map joins the generator's kit, and the per-set painters write the metal, gloss and
  emissive channels from the start.
- **If Level 3 is the leap:** a new Phase 2b ("hero parts as meshes") joins the main plan, the kit gets its polygon primitives, and the hero parts of the top tiers (and the capes) are drawn that way;
  boxes stay for the shells and the rest.
- **If neither moves Euan:** the main plan stands and the next lever is hand-pixelled hero textures and the equip and set moments (the E and D items in my answer), which need no test.
- **If both do:** the three-up sheet becomes the Look 1 bar for every set.

## 8. Decisions for Euan

| # | Decision | Recommended | Why |
|---|---|---|---|
| 35 | The hero set is **Veilwoven**, not Wyrmscale | **Veilwoven** | cloth, metal, glass and a cape in one set shows what each level does to each material; Wyrmscale is the second choice (more glass, a tail, no cloth) |
| 36 | **Staged:** Look A (Level 1 beside Level 2) first, then Level 3 only if you want it; or both at once with two builders | **Staged** | Level 2 is cheap and likely the big step; Level 3 is the costly, uncertain one (about 7 hours to Look A, then about 5 more) |
| 37 | The test lives on its own branch `pack/armour-premium-test` and is **never merged**; only reviewed parts are cherry-picked into the real build | **Yes** | a spike must not leave half-finished code in the mod |
| 38 | If Level 2 wins, **the armour shader moves from Phase 5 to Phase 2**, with the weapon shader's fallback | **Yes** | it is the largest single quality step and already proven in the repo |
| 39 | You judge on the CI sheet; a **Mac film of the cape moving** is added only if you ask | **Yes** | CI frames are poses; a film costs one perf-slot tour |
