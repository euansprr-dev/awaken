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
  focus `UiHooks.setFocus(owner, value)` / `clearFocus(owner)` (max wins; Lens reads focus() into texel 14).
  Signatures (holo merge 2, pack/holo-ui-merge2 05e61bf, pending the main session's merge into build/awaken-v1):
  `dev.awaken.signal.client.UiCanvas.texture(Identifier texture, int x, int y, int w, int h, float u0, float v0,
  float u1, float v1, int argb)` (≤ 16 per frame, no allocation; HoloCanvas also has `textureF(...)` with float
  coordinates; GuiCanvas blits the same UV); the texture is an Identifier'd DynamicTexture or a registered render
  target, updated only when the diorama changes. `dev.awaken.core.api.UiHooks.setFocus(Object owner, float value)`,
  `clearFocus(Object owner)`, `focus()` (client only; owners by identity, ≤ 8; max wins; 0 when none). The Atlas
  screen calls setFocus(this, 0..1) while open and clearFocus(this) on close.
- Tours: the vanilla tutorial toast covered spike shots; tools/autopilot.sh already writes tutorialStep:none on carillon/main (from build/awaken-v1 a205b77), only the spike branches predate it.

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
  leaves into city chunks: keep features out of the reservation (terrain session's reserved()).

## S4 Crowd: GO (W4)
Checked: 7 commits on carillon/spike-crowd (…3dd907d), tree clean; shots in docs/carillon/spikes/s4/ on that branch
reviewed (street, roof, plaza, cap 96, dusk, night). Citizens render with their own skins (the state.skin fix); crowds
gather at stalls; the time-lapse empties the streets at night.
- Ledger: 240 records p95 0.015–0.04 ms/tick in game (2,400 records p95 0.006 ms on the bench): effectively free.
- Bodies (40 at noon): tick p50 0.14 ms headless (load 5) / 0.30 ms in game (load 12–13), p95 up to 1.3 ms in game;
  ~3.5 µs per body, all vanilla movement and collision. 96 bodies p95 0.45–0.69 ms. Budget 1.5 ms holds.
- Frame: 40 citizens in view cost ≤ ~1.2 ms p50 (budget 2.0); frame time tracked machine load more than citizens.
- Stuck: 2 per sprinted play-day after two fixes (from 12): walk back onto the street node before an edge; pause on
  reserved spots. Keep-right lanes cut head-on blocks from 4 to 0–1 per noon window.
- Accepted for W4 (now in SPEC §16.5): 6 path slots above ~60 bodies in a world (4 below; walk straight after 2 s
  without one); reserved standing spots per node sized to each station's staff; always return to the node before the
  next edge; leave early by route distance; route tables baked per destination on first use (≈2,500 nodes); bodies
  never saved (the ledger is the truth); goal-less bodies on keep-right lanes, vanilla pathing only for the last few
  blocks or to get unstuck; rescue teleport after 10 s only out of the player's view; a reachability GameTest for
  every door and stall (the grid graph missed 47 houses until doors were counted).
- Not to reuse from TownNpc: its renderer (Steve; a String per entity per frame), random-stroll / walk-home / door goals
  (full pathfinds), follow range 24 / tracking 10, persistence (duplicates on reload), always-on nameplates,
  invulnerability (SPEC §16.0.7: hurt, never killed).
- Look gaps (W4/W8): 4 skins over 240 people read as clones (seeded skin layers needed), vanilla walk, no job props,
  pop-in at 56–64 blocks down straight streets (fade in, or spawn just out of sight).
- Budget note for G1: citizens ≈ 1.2 ms + city blocks with solid cores ≈ 3.5 ms (S1, noisy) + platform 5.3 + life 0.4
  + Holo 0.3 ≈ 10.7 ms against the market's 10 ms: re-measure quiet in W1/W4 before deciding where to give.
