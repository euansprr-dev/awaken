# Gate G0 notes (orchestrator)

Each spike's report is checked against its commits and shots before a verdict. Verdicts: GO / ADJUST / CUT.

## S5 Atlas: GO, build our own (W6)
Checked: 6 commits on carillon/spike-atlas (2fe9d97..2a4672f), nothing uncommitted; shots in
/Volumes/MEDIA/Awaken-c-atlas/run-autopilot/s5-final/ reviewed (overview, orbit, night, before/parted, close-up).
It reads as the plan's diorama: voxel trees, water by depth, sand terraces, clouds ringing the explored island, markers.
- Cost: +0.7–1.0 ms p50 open and static, +1.4–1.7 ms orbiting, ~0 when cached (budget 2 ms). p99 +0.8–2.0 ms on a busy
  machine: re-measure quiet in W6. Open 83 ms cold / 4–10 ms warm (budget 150). Sampler 22–31 µs/chunk p50.
- Risk found: mesh memory ~99 B per explored column (~25 MB per fully explored 512×512 tile). W6 must mesh a window
  around the view with coarser detail when zoomed out and a compact vertex format (or a GPU heightfield), build meshes
  off the render thread and keep them warm, and record uploads before the render pass opens.
- Diorama reuse rejected: a CPU ray tracer baking fixed-angle 2D pictures (~0.4 s per tile per angle; no orbit),
  reflection and mixins, no schema versions, very high churn. Take its ideas only (sky visibility, corner AO, sprite-
  averaged colours). MIT confirmed; nothing copied.
- Look gaps for W6/W8: cloud sprite variants + a soft cloud floor; a "lit" bit per column for night windows and lamps;
  AO for dense forest; markers occluded by terrain; city-scale buildings untested.
- Wrist glance: render to a small texture on change, drawn by Holo as one textured quad. Agreed with the Holo session
  (2026-10-24): holo merge 2 adds `HoloCanvas.texture(Identifier tex, x, y, w, h, u0, v0, u1, v1, argb)` (one quad, UV +
  tint, no per-frame allocation; DynamicTexture or render target; a GuiCanvas twin where it fits) and multi-owner
  focus `UiHooks.setFocus(owner, value)` / `clearFocus(owner)` (max wins; Lens reads focus() into texel 14). Exact
  signatures arrive when merge 2 lands on build/awaken-v1.
- Tours: the vanilla tutorial toast ("Move with W, A, S and D") covers shots; W9 sets tutorialStep:none for tours.
