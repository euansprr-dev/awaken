# Steady Light: the ImmersiveVFX look at a steady 60 (plan, 10 Nov 2026)

Status: a plan waiting for Euan's yes. Nothing here is built except the measuring tools and a proof-of-concept look
behind a debug switch, both on `claude/look-steady-60-plan-889268` and neither for merging as they are.
How the numbers were taken: `docs/perf/steady60/MEASURING.md`; every window's table is in `data/<tour>/report.txt` and
its frames in `data/<tour>/perf-traces/`. The page for Euan: https://claude.ai/artifact/4UjwicVDJEoS9vceUanMyQ (its source is
`page/`: `python3 -I page/build.py <worktree> <end time>` fills `template.html` from the traces).

## 1. The brief

- **The job.** Play AWAKEN on the M4 with the 4K Dell and get a look he loves at a frame rate that doesn't move.
- **The pain.** ImmersiveVFX (the VFX Look) has the look and swings between 60 and 20-30 fps. Lens Cinematic "looks
  like shit" beside it and ran 40-55 fps on 8 Nov.
- **What he wants.** Nearly ImmersiveVFX's look (its far blue fog, shadows, sun rays, atmosphere; a little under is
  fine) at a stable 60. Soft 3D clouds that still look vanilla in place of Lens's volumetric ones. The far blue fog in
  place of Far Sight's far detail.
- **His ideas, to weigh and not to assume.** Lens as a mod of its own; working the way ImmersiveVFX works; "a
  revolutionary approach to shaders on macOS".
- **Fixed.** Vulkan on Apple Silicon; Minecraft 26.3; the Turbo contract for anything inside the renderer;
  ImmersiveVFX is All Rights Reserved (learn from what it shows, never from its code).
- **"Steady 60", measured.** At his setup, as he plays (vsync on, 60 cap, the Dell at 120 Hz), a frame is on time when
  it is shown for two refreshes (16.7 ms). Target: 99% or more of frames on time standing, turning and in the city;
  98% or more flying over land already seen; no run of three late frames; uncapped p99 at or under 15 ms.
- **"Nearly on par", judged.** Same-spot, same-moment shots of the new look and ImmersiveVFX at five moments (morning,
  noon, dusk, night, the open view). His eye decides; the contract is that he can't call ImmersiveVFX clearly better
  in at least four of the five.
- **Not goals.** Beating ImmersiveVFX's water or its particle count; a look for shader packs; changing Swift's cost.

## 2. What was measured (his setup, 10 Nov)

GPU world pass p50 in ms (a frame is that plus about 1.6 ms); uncapped fps; quiet windows.

| | his clearing | flying, open view | Carillon from the Roost |
|---|---|---|---|
| Lens off (mod asleep) | 2.8 ms, 220 fps | | |
| Swift | 4.8 ms, 157 fps | 4.8 ms, 153 fps | 3.8 ms, 187 fps |
| Cinematic | 9.4 ms, 90 fps | 8.7 ms, 97 fps | 6.9 ms, 116 fps |
| Cinematic + Far Sight (near) | 13.6 ms, 65 fps | | |
| Ultra | 12.5 ms, 70 fps | | |
| ImmersiveVFX, his settings | 11.3 ms, 77 fps | **16.7 ms, 57 fps** | 9.2 ms, 80 fps |
| ImmersiveVFX, shadows off | 8.6 ms, 96 fps | 7.9 ms, 98 fps | |
| ImmersiveVFX, lighter shadows, no reflections | 11.0 ms, 78 fps | 15.6 ms, 63 fps | |
| ImmersiveVFX, fog, sky and clouds only | 5.9 ms, 119 fps | 6.2 ms, 125 fps | |
| The mod's sky, fog and clouds under Lens Cinematic (hybrid) | 11.9 ms, 74 fps | | |
| The proof-of-concept look (Lens) | 9.3 ms, 92 fps (Cinematic on the same frames: 8.9 ms, 95 fps) | | |

As played (vsync, 60 cap), the share of frames on time:

| | standing | turning | flying | Roost |
|---|---|---|---|---|
| Cinematic | 99.7% | 99.6% | 96.1% | 99.9% |
| Cinematic + Far Sight (near) | 99.4% | 98.9% | | |
| ImmersiveVFX, the first minute after joining (load 22) | 59% | 57% | | |
| ImmersiveVFX, quiet | 99.3% | 98.7% | 81.6% | 98.7% |

Other findings:
- ImmersiveVFX's shadows are a real shadow pass before the main pass (its own log line, 9 Nov). They cost about 3 ms
  in the clearing and about 9 ms flying. Flying, the GPU's p99 was 20-25 ms with them on and 8-12 with them off; standing,
  it reached about 20 ms in nine of fifteen windows with them on and in neither window with them off.
- In VFX about half of the render thread's CPU samples were inside the mod, 38% of all of them in its particle
  runtime (at his "maximum" particle setting).
- ImmersiveVFX asleep costs nothing measurable: with its jar taken out and with it installed asleep, Swift ran 4.85 and
  4.68 ms of GPU time, Cinematic 8.97 and 8.94, Lens off 2.84 and 2.82.
- Its particles: uncapped in the clearing, 7.3% of frames ran over 16.7 ms on "maximum", 3.3% on "reduced", 2.0% off.
- GC is not a cause: ZGC paused 4.75 ms in total over the eleven-minute tour.
- Lens Cinematic's light was nearly absent in his first shots: a Rolling Storm cell stood over the spot (Lens takes
  the sun away under it and draws it as a dark blob). With the storm ended, the shadows are there but soft and about
  20% dark, where ImmersiveVFX's are crisp and about twice as deep.
- His video settings have Clouds off in the pack's game folder (on in the other one), so Lens has drawn no clouds for
  him at all; Lens only draws its own where the game would.
- The 8 Nov "40-55 fps on Cinematic" was with Far Sight on: it adds 4.2 ms here and leaves 1.4 ms of room.
- In the first two minutes after joining, Awaken's own road planner kept about one core busy and the load average
  was 16-24; every Look's late frames were worst then.

## 3. Root causes

| symptom | cause | evidence | status |
|---|---|---|---|
| VFX falls to 40 and 30 in open views | its GPU cost goes over 16.7 ms there; the shadow pass is about 9 ms of it | flying: 16.7 ms with shadows, 7.9 without | shown |
| VFX spikes while standing | the shadow pass's own slow frames; particles on the render thread | GPU p99 about 20 ms in 9 of 15 windows with shadows, 0 of 2 without; 38% of samples | shown in part |
| a miss shows as 40 or 30 fps, not 55 | vsync on a 120 Hz screen: a late frame waits a whole refresh | as-played traces sit at 16.7, 25 and 33 ms | shown |
| Cinematic ran 40-55 on 8 Nov | Far Sight's 4.2 ms on top of Cinematic's 9.4 | 13.6 ms, 65 fps uncapped today | partly (Far Sight "full" and that day's builders not re-run) |
| Lens looks flat beside VFX | by design: 17% haze at most, a vanilla sky, a 20% shade, soft shadows at a third of the resolution; no clouds on his settings; the storm blob | shader constants, shots | shown |
| late frames flying, any Look | chunks arriving: meshing and worldgen beside the render thread | Cinematic flying 96.1% on time, worst frame 69 ms | shown, not yet split by cause |

Still a hypothesis: that our own shadow casters can be meshed fast enough in flight (stage 3's first gate).

## 4. Alternatives

- **A. Keep ImmersiveVFX, tune it.** Its shadows off, or its lighter settings. Cost: an hour. It cannot hold 60 with
  its shadows on at his resolution, and we can't fix its shadow pass or particles: closed, alpha, All Rights Reserved,
  and Lens's hundred-odd looks (gates, lands, caves, powers, story scenes) stay switched off under it.
- **A2. The mod's sky, fog and clouds under Lens's light.** A hybrid Look. Tried for a minute: 74 fps and 11.9 ms in
  the clearing, which is what ImmersiveVFX costs whole (11.3). No gain, two renderers stacked, and still not shippable.
- **B. Steady Light (recommended).** Lens takes the look: the air, sunlight that reads, soft vanilla-shaped clouds,
  inside a frame budget with a governor. Then real shadows, cached. Stages below.
- **C. Lens inside the renderer.** Mixins into Sodium to draw its chunks from the sun every frame and to light and fog
  in the terrain shader, as ImmersiveVFX does. The most exact, the most fragile (every Sodium update), and it pays the
  per-frame geometry cost that sinks ImmersiveVFX here. Held as stage 3's fallback path for casters only.
- **Lens as its own mod.** Packaging only: the same code in a second jar draws the same frame at the same cost. Worth
  doing only to publish Lens for other packs; not part of this plan.

## 5. The design

Three rules, each from a measurement:
1. **Spread what moves slowly.** The sun crosses a third of a degree a second and shadows are drawn on the texel grid,
   so a shadow map a quarter of a second old looks the same as a fresh one. Shadows, the sun map, the sky and the
   clouds refresh over several frames; each frame only looks them up. (ImmersiveVFX redraws its shadows every frame:
   that is its 9 ms.)
2. **One pass at full size.** On the M4 a full-size pass costs by its memory traffic. Everything heavy runs at reduced
   size; one composite touches every pixel, and where Pixels is on, its upscale is that pass's output.
3. **A governor holds the 16.7 ms.** It reads the GPU's time for the last frames and trades what can't be seen first.

### 5.1 The Air (stage 1)
- Fog: every tier. By distance over the render distance, `1 - exp(-3.6 t²)`, toward the game's own horizon colour
  pulled to blue by day and to rose and violet at dusk; the sky's horizon band takes the same colour, so the land's
  end is never a line. Thick-fog places (underwater, gates with their own fog) keep theirs. Per-look fields replace
  the proof's constants: `air` (strength), `airTint`, `airDusk`.
- Sky: a deeper zenith by day, a dusk gradient, the stars left alone. In the composite, for sky pixels only.
- Sunlight: the key brighter and warmer, the shade about twice as deep (look fields `light`, `shade`, already there).
- Shadows on the texel grid: a point on a block face asks from the centre of its 1/16-block texel.
- Clouds: Lens's volumetric march goes. Stage 1a keeps the game's own fancy clouds, lit and fogged by Lens, drawn
  whatever the player's Clouds setting says while a Lens tier is on. Stage 1b is Lens's own soft clouds: the vanilla
  pattern in two or three stacked translucent layers that narrow upward, lit top and shaded base, as a mesh
  (NeoForge's `CustomCloudsRenderer`), no ray march.
- Storm cells: the fallback's box and Lens's dark blob both go to the new cloud's look (a taller, darker stack).
- Far Sight: off by default on Cinematic and Ultra (the fog reaches the edge); "near" and "full" stay as choices.
- Small life: fireflies at dusk and night, pollen by day, through `LensParticles` (its sprites exist).

### 5.2 The Budget (stage 2)
- Always-on frame record: the Look page's 60 s ring also counts frames by slot; the page shows "on time 99.4%";
  `/awaken look report` writes the last minute to the log. This is the acceptance measure from then on.
- Trims, each measured alone: `lensTemporal` on with the new shadows; the cloud pass and blend gone (about 0.65 ms);
  the composite's light term moved into the reduced-size pass; with Pixels on, the composite writes the target the
  upscaler reads, so the full-size copy goes.
- The governor: target 14.5 ms. Steps, in order, one at a time, down after three late frames in a second and up after
  two seconds of room: the refresh rates (shadows, clouds), the light buffer's size, the volumetric steps, then, as a
  later experiment only, one notch of render scale from targets made in advance. It never changes what a still frame
  looks like by more than its own table says, and the Look page names the step it is on.
- Background work gives way: Awaken's own worker pools (road planner, cave horizon, Far Sight) pause while frames run
  late. macOS ignores Java thread priorities, so this is a gate they check, not a priority.

### 5.3 The Sun Pass (stage 3)
- Casters: Awaken's own simplified meshes of the chunks around the player, from Far Sight's capture and mesher
  (`farsight.voxel`), public API only. Whole blocks merged; slabs, stairs, fences and walls as their boxes; leaves as
  whole blocks; plants left to the contact shadows at first.
- Maps: a world-anchored clip map along the sun, two levels: 128 blocks across at 1/16 block (texel-exact) and 512
  across at 1/4 block. Beyond that, today's heightfield sun map. Each level is redrawn in strips over 8 and 32 frames
  into a back buffer with the sun's direction frozen, then swapped.
- Look-up: in the reduced-size light pass, one depth compare at the snapped point; the heightfield march only past the
  maps. Contact shadows stay for mobs and plants.
- Budget: 0.8 ms of GPU a frame on average, 1.5 at worst, 48 MB; one worker, which gives way.
- Falls back to the heightfield path if a pipeline doesn't compile, the dimension has no sky, or the budget is broken
  for a second.
- If our own casters can't be meshed fast enough in flight: the Turbo path (Sodium's own section buffers, `@Pseudo`,
  version-locked), which needs Euan's separate yes.

### 5.4 Contract
Trigger: a Lens tier on (Swift gets the air only). Inputs: the frame's colour and depth, the heightfield ring, the
shadow maps, the look for the place and hour. Output: the frame. Invariants: never darker than the game under cover
or within arm's reach (unchanged); a story scene's look still wins; the same pixels with the governor at rest; nothing
allocated per frame; with the feature's setting off, today's path exactly.

## 6. Stages and gates

| stage | what | gate to pass | back out |
|---|---|---|---|
| 0 | the frame record on the Look page; the measuring tours kept as the acceptance tours | its numbers agree with the tour's within 1% | a setting |
| 1 | the Air: fog, sky, sunlight, texel shadows, the game's clouds lit (1a), then Lens's soft clouds (1b) | Euan's eye on the five same-spot pairs; Cinematic no slower than today (9.4 ms in the clearing) | look fields back to today's values; `lensAir` off |
| 2 | the Budget: trims and the governor | 99% on time standing, turning, Roost; 98% flying over seen land; Cinematic at or under 8.5 ms in the clearing | each trim and the governor have their own switch |
| 3 | the Sun Pass | door, arch and forest shots beside ImmersiveVFX; at or under 0.8 ms; stage 2's gate still passes | `lensSunPass` off: the heightfield path |
| later | mob and hand shadows from a small per-frame map; water; a render-scale notch in the governor | each on its own evidence | |

Order: 0, then 1a, then Euan looks. 1b and 2 can run side by side after that. 3 starts only if he wants more from the
shadows than stage 1 gives.

## 7. Evaluation

- Frame pacing: `tools/steady60-tour.sh tools/autopilot/steady60-dell.txt` on the Dell, before and after each stage,
  on a quiet machine (load under 6), the report's "on time" columns. From stage 0, the Look page's own line in his
  real play is the measure that counts.
- Look: `tools/autopilot/steady60-proto.txt`'s five moments, ImmersiveVFX beside the new look, plus a door, an arch
  and a forest floor for stage 3. CI can't stand in for these: its GPU is paravirtual.
- Must not regress: gate looks, cave floors ("never darker than the game"), the held torch, story scenes, the Holo
  panels, `lens_heights_skipped_roof` and the other Lens GameTests.

## 8. Risks

- The look is taste. The proof of concept is a first pass; stage 1's gate is his eye, and it may take two rounds.
- Texel-snapped shadows from a buffer a third of the screen's size keep a soft edge of a pixel or two up close; the Sun
  Pass's look-up can run at full size if that reads wrong.
- The cloud mesh's translucent layers cost by overdraw at 4.7 megapixels: measure before promising under 0.4 ms.
- The governor must not hunt; it has hysteresis and a visible state, and it is the first thing to switch off if he
  sees the picture change.
- ImmersiveVFX may fix its own pace (it ships daily; 1.9.2 "fixed shadows jumping"). Then the VFX Look is simply
  better than it was; this plan doesn't depend on it staying slow.

## 9. For Euan

See the page: go or no-go on stages 0-2; whether stage 3 starts now or after he's seen stage 1; Far Sight off by
default on Cinematic and Ultra; the clouds; the VFX Look's shadows; contacting ImmersiveVFX's author.
