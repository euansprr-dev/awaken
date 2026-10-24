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

## S1 City: ADJUST (W1, W8, W3)
Checked: 6 commits on carillon/spike-city (…c36561c), tree clean; tour-3 shots reviewed (s_bell, s_dusk_bell, s_square,
s_laundry, r_sky, r_bell_solid). Machine load 5–32 all session, so frame numbers are indicative; W1 re-measures quiet.
- Generation: GO. Grammar 0.08–0.11 ms/chunk p50; block writes 0.34–0.70 ms p50 dominate. 1,250 chunks ≈ 1–4 s CPU,
  0.1–0.3 s wall across worldgen threads. Full pregen (terrain + city, 1,225 chunks) took 11.2 / 28.4 s, and 38.9 s
  with Distant Horizons generating alongside; vanilla terrain alone 19–23 s. The city is 2–5% of it. W1: hold DH's
  LOD building until the pregen finishes; budget ≤ 30 s is judged on a quiet machine.
- Frame: ADJUST. A 480-block city fits inside RD 16 from anywhere in it, so all of it is always drawn. Full city vs
  plate (perf, p50 street / roof / 200 up): +3.9 / +6.8 / +11.3 ms; horizon +6.0 / +4.6 / +10.7. Hollow glazed houses
  defeat section occlusion culling: solid cores (dark fill behind windows, sealed attics) cut it to +3.5 / +2.1 / +4.6
  (horizon). Sky shot p99 26–29 ms on perf (target ≥ 60 fps p99). Required for W1: solid cores everywhere except the
  ~60 enterable buildings; geometry density varies by district (dense Bellwatch only where the run needs it); writes
  straight into chunk sections (setBlock is 2–14× slower, spikes to 160 ms); pieces ≤ 14×14 with a 2-block margin;
  connections (panes, fences, stairs) set by the painter; no block entities in bulk; ≤ 4–5 light sources per chunk.
  Sky shot (W3 + W1): fly it at RD ≤ 12 with Distant Horizons drawing the far districts, and pick the path so the
  near view stays under budget. Component budget for city blocks stays a G1 question: re-measure quiet with solid
  cores, then set it together with S4's citizen numbers so the end-to-end targets (market ≥ 100 fps, elsewhere
  ≥ 110 fps on perf) hold.
- Look: ADJUST, and the overhead view is the priority because the opening starts there. Street level reads as an old
  town (stone ground floors, jettied timber storeys, bracket lanterns, flower boxes). From above it is a uniform dark
  slate grid with no hierarchy. W1/W8: roof palette per district (slate, red clay, copper, green), organic plots and
  street hierarchy from the authored plan (avenues, squares, courtyards, gardens and trees), hand-made landmarks
  (the copper spire read as a stepped tree), facade modules instead of window grids, kit blocks for cloth laundry
  (glass panes read as glass; tripwire clicks), lit windows, roof variety; chimney smoke as client particles; night
  needs more than lanterns. Everything vanilla reads as "a good vanilla build", not a mod: the Carillon kit is required.
- Other: bee nests in cleared trees left orphan block entities (fixed in the writer). Neighbouring vanilla trees leak
  leaves into city chunks: keep features out of the reservation (terrain session's reserved()). Tours: the tutorial
  toast covers shots (W9: tutorialStep:none).
