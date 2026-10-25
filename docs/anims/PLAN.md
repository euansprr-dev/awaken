# Hands: first-person animation for AWAKEN (approved 2026-10-25; building phase by phase)

Status: approved by Euan 2026-10-25 with every recommendation in §7; P1-P6 built (SPEC §17.23, §24); the quality pass P1.1/P2.1/P4.1 (hands that meet the world) after the orchestrator's review. Branch `pack/fp-anims`. Approval page: https://claude.ai/artifact/5dQodY76zUGpn536Z34Lpy (`docs/plan-hands.html`).
Euan's ask: a real pipe climb and slide; first-person animation for every move of The Ascent; "Cinematic
Interactions"-level hand moments across the mod; all in the design language we already have.

## 1. What exists (audit)

| Piece | Where | How it's made | Its language |
|---|---|---|---|
| Body moves, third person | `body/client/BodyAnims`, `assets/awaken/player_animations/body_moves.json` | Keyframed Player Animation Library (PAL) clips written in Python (`tools/art/specs/body_moves.py`, in `power_casts.py`'s figure terms: lean, twist, rise, ra/la, re/le elbows); vanilla arm-pose fallback without PAL | Held loops with a tiny jitter (period 4 ticks), `_end` settles over 5 ticks, fade-in 1-3 ticks, `_bent` twin for Bendable Cuboids, `_l/_r` variants by wall side |
| Ascent bodies | `body/ascent/client/AscentAnims`, `ascent_moves.json` | Whole-figure transforms on the pose stack (swing pivots at the shoulder, belly down in flight, banked at 1.5× yaw rate, one flip off a rope) plus PAL clips | Physics-led: the pose follows the real velocity; no canned flight loop |
| Cast animations | `powers/client/fx/CastAnims`, `PalCasts`, `power_casts.json` | 3P: vanilla arm poses + lean, or PAL clips. **FP: `RenderHandEvent`, the arm redrawn with `AvatarRenderer.renderRightHand` and moved in screen space** | Envelope in over 15 %, out over 35 %; anticipation (a 12 % wind-back before a punch, 35 % before a throw); `sin` quarter-wave ease-outs; a held wind-up trembles (`sin(t·2.7)`, ~1.6°) |
| First person today | `BodyCamera.onHand`, `FeelDirector.onHand`, `CastAnims.onHand`, `OpeningCamera.hand`, `LucidiumClient` | Five separate `RenderHandEvent` listeners, each shifting the whole hand stack | Sprint lowers the hands, slide braces them, hang and mantle lift both hands, the rope raises the main arm 58°. No owner, so they can stack. |
| Camera beats | `BodyCamera`, `FeelDirector` | `ComputeCameraAngles`, `ComputeFovModifierEvent`, ticked values drawn with partial ticks | Roll dip 32° on a roll, mantle eyes ±9°, slide roll 7.5°, wall-run roll 5° (0-7.5 setting); a damped pitch spring for moments; FOV follows vanilla's slider; reduced motion halves |
| Wings in first person | `WingLayer` | Outer half of the span only, faded, at the frame's edges on beats and banks | Light at the edges, never the root at the camera |
| Action camera | `ActionCamera`, `ActionRig` | Critically damped spring (0.22 out, 0.32 back) per body pose, through Shoulder Surfing | Trails out, never overshoots, home in under 1.5 s |
| Creature life | `mobs/client/life` (SPEC §23) | Procedural springs by bone name and temperament | Breath, follow-through, weight; no per-creature code |
| Climb lines (pipes) | `#minecraft:climbable` + `#awaken:runner_climbs` (drainpipe, trellis, rope, hanging ladder, ladder); `FreeRun.climb` | Vanilla ladder physics; the free-run pushes up at 0.3 b/t with a grip sound | **No animation at all**, 3P or FP: the body floats. Vanilla rules: no keys = drift down 0.15 b/t, Shift = hold |

The free-run's feel is approved as it is (Euan, 25 Oct): this plan adds animation to it, never changes its motion.

Shared rules every new animation keeps: ticked state drawn with partial ticks; fixed slots, nothing allocated per
frame; 2-4 tick blend in, 4-6 tick blend out (`BodyCamera.hold`); smoothstep for turns, sine quarter-waves for
thrusts, a small anticipation before any big action; own sounds and own grit (the surface's colour), never vanilla
particles; Signal cyan for magic, Holo blue `#7FC3FF` for UI; comfort settings (roll cap, FOV slider, reduced motion)
respected everywhere.

## 2. Pipes: the grip

A new Runner state, **GRIP**, on every `#awaken:runner_climbs` block. Walking or jumping into a climb line, or falling
past one with forward held, takes it: the body snaps to the line (centred on the pipe, facing the wall), both hands
round it, knees and insteps clamped.

| Input | What the body does |
|---|---|
| Nothing | **Holds still** (vanilla drifts down at 0.15). Breathes, sways a touch; the head looks round freely, the body stays. |
| Forward (or the free-run) | Climbs hand over hand at 0.2 b/t (0.3 with the free-run). The cycle is locked to distance (one pair of pulls per 1.1 blocks), so hands never slide on the pipe. |
| **Shift, held** | **Slides**: loosens the grip and drops, accelerating 0.06 a tick to 0.65 b/t; legs wrapped, palms hissing on the pipe, a thin line of grit or sparks. |
| Shift released | Brakes over 4 ticks: a squeeze, a small dip of the view, a squeal, then holds. |
| Reaching the ground | Feet hit the shoe and the body stands out of it in a crouch; no fall damage at any slide speed. |
| **Jump + back** | **Kicks off** the wall (0.5 out, 0.42 up, turning to face out over 5 ticks; the ledge back-jump's numbers). |
| Jump + left / right | Swings out to the side: catches a pipe or ledge within 3 blocks, or drops clear. |
| Jump alone | Lunges up the pipe about 1.2 blocks (once each 10 ticks). |
| The top | The eave's lip is caught as a hang, as the free-run does now; then the climb out. |
| Rope key | Fires the rope from the pipe (the rope always cuts into a Runner move). |

**Dropping off: three options.**
- **A (recommended): "Shift takes you down, Jump takes you off."** Hold Shift to slide; Jump with back to kick off;
  ride the slide to the ground to step off. It matches the ledge hang, where Jump + back already kicks off the wall.
  It needs no timing, and you can't drop by accident while you slide and brake in short bursts.
- **B: double-tap Shift to let go.** Euan's idea. Hold Shift to slide, tap it twice within 6 ticks to open the hands
  and drop. Easy to learn, but feathering the slide (slide, brake, slide) reads as a double-tap and drops you.
- **C: hold Back for 4 ticks to lean out and let go.** No conflict with the slide, but it is a new gesture, and Back
  means something else on a ledge.

Engineering notes: vanilla clamps descent on a ladder to 0.15 b/t inside `LivingEntity.handleOnClimbable`. Our own
climb blocks override NeoForge's `IBlockExtension.isLadder(state, level, pos, entity)` and return false while that
body is in GRIP, so the state owns the motion on both sides (no mixin). Vanilla's `minecraft:ladder` can't be
overridden that way: it gets the grip, the holds and every animation, but its slide stays at 0.15 b/t. The server checks
GRIP like a hang: a climb line within 0.6 of where it saw the body, fall distance reset, motion zeroed after its tick.

Looks: 3P clips `body_pipe` (hand over hand, its speed tied to the climb by PAL's `SpeedModifier`, stopped = the held
pose), `body_pipe_hold` (loop), `body_pipe_slide` (loop) and `_end`, `body_pipe_kick`, `body_pipe_side_l/_r`,
`body_pipe_hop`, `body_pipe_land`, each with a `_bent` twin. FP: both hands on the pipe in view, alternately reaching
up past the eye line; looking down shows your knees and feet clamped round it (the body layer, §3). Sounds: grip
(exists), `body.pipe_slide` (a palm hiss over metal, wood or rope by block), `body.pipe_brake`. Grit: the pipe's own
colour off the hands while sliding; copper and iron pipes throw a few sparks at full speed.

## 3. The first-person system ("Hands")

One owner for everything drawn in first person, in the same pattern as the Feel Director (one voice for the camera).

**Layers, composed every frame:**
1. Vanilla: the held item, swing and equip motion. Untouched when nothing else plays.
2. Stance: the body's continuous state (grip, hang, wall-run, rope, wings, flight). Follows the state, blends 2-4 in,
   4-6 out.
3. Action: one-shot clips (mantle, ledge-up, vault, landing, kick-off, every interaction, every cast).
4. Procedural: look-lag (the hands trail fast view turns on a critically damped spring, like the action camera),
   breath (the life layer's rate), grip tremble, impact springs (landings push the hands down and back), speed sway.
5. Camera beats stay with `BodyCamera` and `FeelDirector`; Hands asks the Feel Director for its beats.

**Hooks, all verified in 26.3 (`.ref/neo`, `.ref/src`), no mixins:**
- `RenderHandEvent` (NeoForge, cancellable, per hand; pose stack, `SubmitNodeCollector`, `PlayerRenderState`, light,
  partial tick, equip and swing progress, item). One listener at HIGHEST priority. When a layer is active it cancels
  vanilla and draws the arms itself with `AvatarRenderer.renderRightHand/renderLeftHand` (skin, sleeves), as
  `CastAnims` already does, and held items or props with `ItemModelResolver.updateForTopItem` and
  `ItemStackRenderState.submit`. The five existing listeners become pose sources to it, so nothing stacks.
- The body in first person (knees and feet on a pipe, legs out in a slide, feet on the wall in a hang, legs trailing in
  flight): `ExtractLevelRenderStateEvent` takes a snapshot of the pose, `SubmitCustomGeometryEvent` submits the player
  model's leg parts with the skin (`submitModelPart`) in the world, with true depth. Only during moves where looking
  down should show them, never while walking; nothing above the hips; it steps aside if a full-body first-person mod
  is present.
- Camera: `ViewportEvent.ComputeCameraAngles` and `ComputeFovModifierEvent` (as today).
- The hand pass keeps its own fixed FOV, so arms never stretch with the speed FOV.
- PAL's own first-person mode (`FirstPersonMode.THIRD_PERSON_MODEL`) stays off: PAL is optional, its first-person mode
  runs on its own mixins, and a third-person clip rarely puts the arms in view.

**Authoring, same pipeline as the 3P clips:** `tools/art/specs/fp_moves.py` and `fp_interact.py`, in
`power_casts.py`'s figure terms, with the same easing names (linear, easeoutcubic, easeinoutsine) and the same lengths
as the matching 3P clip (one timing table shared by both, so the views stay in step). Each key gives the arms in eye
space (shoulder pivot at its real place: 0.31 to the side, 0.22 under the eye) plus a framing offset that keeps the
hands in view. Export: `assets/awaken/fp_clips/*.json` (schemaVersion 1). `preview` renders a frame strip from the eye
at the hand pass's FOV, like `body_moves.py preview`. Edit the poses in Python, never the JSON.

**Smooth in both views:** the 3P clip and the FP clip start from the same event on the same clock, so switching
between first person and the shoulder view mid-move lands in phase. **Input never waits for an animation:** every
interaction animates an action that has already happened (the server stays authoritative). A touch lasts at most
0.3 s, any input cuts it (move, attack, scroll), and a screen opens as soon as the server answers; the reach fills
that wait. Settings (feel settings): one switch each for moves, touches and stations, and one per station. Reduced
motion halves amplitudes and drops the tremble.

Code: a new module package `dev.awaken.anims` (client: `anims.client.Hands`, `FpClips`, `BodyInView`). Other
modules call it through a new `core.api.FirstPerson` (play, hold and release a clip; register a stance source), as
they reach the 3P clips through `core.api.PlayerClips`. Added to SPEC §12 when built.

## 4. Every move in first person

| Group | Move | First person |
|---|---|---|
| Ground | Tactical sprint | Hands pump low at the frame's foot, locked to the stride |
| | Slide | Lead hand braced forward, off hand trails on the ground throwing grit; your legs out ahead |
| | Stair slide (SPEC §17.22, built on pack/ascent; `MovementHooks` `stairSliding`) | The slide's hands with the view tipped down the slope; the off hand skims the step edges, a small bump and a puff of the stair's grit at each step; your legs out ahead along the slope. 3P: the slide pose (`body_slide`) with the whole figure tipped to the stairs' angle about the across-body axis (`BodyAnims.tip`, eased in over 3 ticks), heels chattering once a step |
| | Slide-cancel jump | Both hands thrown up, then open for the landing |
| | Roll | Forearms cross the frame as the view dips (the dip exists) |
| | Free-run vault | A palm plants on the top with the slap and puff that exist now; the body swings over it |
| | Mantle | Both palms slap the lip, press, elbows lock as the view rises over |
| | Leap from an edge | Arms swing forward and up |
| | Landing, soft / hard | Hands drop and spring back / one hand touches the ground (a three-point landing) |
| Walls | Wall-run | Wall-side fingertips brush the wall, grit every 4 ticks; open hand out for balance |
| | Wall jump | Wall hand shoves off, palm in view |
| | Wall climb (Wall Stride) | Hands slap up the face alternately |
| | Ledge grab / hang | Both hands slam the lip with a catch shake; fingers over the edge; look down to see your feet on the wall |
| | Shimmy | Hand over hand sideways, the lead hand reaching |
| | Ledge up / side / back / lower / corner | Pull with an elbow over; reach and catch; push off and turn; hands slide to the lip; one hand swings round the corner |
| Climb lines | Grip, climb, slide, brake, kick, side swing, hop, top-out | As in §2 |
| Rope | Fire | The arm throws forward with a wrist flick; the rope leaves the palm |
| | Swing / pump / reel | Rope hand high; the other hand joins the rope to pump; hand over hand to reel |
| | Zip | Both hands on the rope overhead, the body tilted forward |
| | Release / flip / carry | Hand opens and the rope falls away; arms tuck through the flip; arms out for balance |
| Leaper | Double jump / air dash / dive slam | Arms thrown down; lead fist punches through the dash; both fists down, knuckles to the ground on impact |
| Wings | Open / glide / beat / dive / roll / boom / fold | Hands spread to the frame's edges as the wings unfold; arms flat out, fingers trembling with speed; arms sweep with each beat; swept back out of view in a dive; tucked through a roll; raised against the boom; brought in as the wings fold |
| Flight | Cruise / boost / hover / climb | One fist forward on boost; palms down and breathing in a hover; arms sweep up to climb |
| Sovereign | Ascend / form / Judgement Fall | Palms rise with gold light gathering; faintly gold hands throughout; both fists overhead, then down to the impact |
| Elsewhere | Tether Vow, updraft vents, windways, casts | The rope binds round the wrist; arms lift in the draft; glide variant; the casts move into Hands with their look unchanged |

## 5. Cinematic interactions

What "Cinematic Interactions" is (research, §8): a Fabric mod (MIT, NeoForge only for 1.21.1) for **workstations**.
You right-click a station, the body steps to it, the camera leans into a framed view, arms in your own skin rise into
the scene, and the crafting table becomes a real 3×3 surface where your hands lay the ingredients. Three hammer
strikes at the anvil, page turns at the lectern. It does nothing for doors, chests or eating, and it runs on about 26
mixins that collide with Shoulder Surfing and First-person Model. So we build our own, in our language, in two
families.

**A. Station scenes** (Euan's "the level I want"). The view leans in to the station on the opening's shot system (a
camera placed every frame from `ComputeCameraAngles`, as `OpeningCamera` does; no mixin). Our own screen replaces the
vanilla one through `ScreenEvent.Opening.setNewScreen`. It is a container screen on the same vanilla menu, so every item
moves by normal menu clicks and the server stays in charge. The screen draws no slot grid: you point at the 3D surface
(a ray from the cursor) and click. Arms are drawn in the world with true depth (`SubmitCustomGeometryEvent`, the
player's arm parts and skin), and your body is hidden while you're at the station. Lean-in 0.6 s (0.3 s when you come
back within 12 s), out 0.5 s, each tap 0.2 s; any movement key, jump, E or Esc leaves. Sneak + right-click always opens
the plain vanilla screen, and each station has its own on/off switch.

**B. Touches in the flow.** Quick hand moments on actions that already happened. They last at most 0.3 s, never wait
for anything, and any input cuts them.

Ranked by what they add for what they cost:

| # | Moment | Family | What the hands do | Adds | Cost |
|---|---|---|---|---|---|
| 1 | Doors, trapdoors, gates (vanilla, Stillpoint, Precursor, Ghost) | B | Palm on the edge pushes it through its swing | Very high (every minute) | Low |
| 2 | Crafting table | A | The 3×3 is the tabletop: tap an ingredient and the hand lays it on its square, where everyone can see it; "lay out" places a whole recipe; the result rises and the hand takes it | Very high (Euan's example) | High (the first scene builds the shared scene kit) |
| 3 | Levers, buttons, the Ghost Lever | B | Grip and throw; a finger press | High | Low |
| 4 | Picking herbs and forage | B | Pinch, pluck with a shake, carried down to the hip | High (the crafts loop) | Low |
| 5 | The Forge (temper) and anvils | A | The blade on the anvil, hammer strikes on each click, sparks, the glow cooling | High | Medium, on the kit |
| 6 | Eating and drinking (the nine meals) | B | Bowl or cup up in both hands, a sip | High | Low-medium |
| 7 | Chests, barrels, the Satchel, caches | B | Hands lift the lid during the server's answer; the screen opens at once; the lid drops on close. Never a scene: storage trips must stay instant | High | Low-medium |
| 8 | Cooking: campfire and furnaces | A | Skewers turned over the fire; food slid into the furnace mouth | Medium-high | Medium, on the kit |
| 9 | Journal, Dream Journal, notices, the Spire Board | A (lectern-like) / B | The book held up in view, pages turned by hand; a notice torn off the board | Medium-high | Medium |
| 10 | Picking up items | B | A quick grab as the item flies in | Medium | Low |
| 11 | Petting and mounting creatures | B | A hand strokes the creature and the life layer answers | Medium | Low-medium |
| 12 | Waystones, conduits, gates, the Hearth anchor | B | Palm presses the stone; light spreads from the hand | Medium | Medium |
| 13 | The Ritual of the Bell | B (long) | Both hands on the bell rope; the pull. Rare, so it may take 1-2 s | High, once | Medium |
| 14 | Other vanilla stations (stonecutter, grindstone, smithing, loom, brewing, enchanting) | A | Each its own gesture on the kit | Medium | Medium each |
| — | Mining, attacking, placing blocks | — | Stay vanilla: too frequent; any delay hurts | — | — |

## 6. Phases and effort (at our pace)

Scaled from git history: the Ascent's Tether Vow to Wings and flight took about 2 hours of building; the Runner
tier about 3. One builder per phase (Opus, effort xhigh, since this is look work); each phase ends with an autopilot
clip tour in first person and in the shoulder view, run alone, Gradle wrapped in `slot.sh`. CI runs the GameTests.

| Phase | Scope | Builder time |
|---|---|---|
| P1 Pipes and the rig | Hands (one owner, the five listeners folded in), the FP clip format, spec tool and preview, the body-in-view legs; GRIP (hold, climb, slide, brake, kick, side swing, hop, land, top-out), its server check, 3P and FP clips, sounds, grit. Tests: `runner_pipe_grip` (slide, brake, kick-off, no fall damage), `anims_clip_names` | 3-4 h |
| P2 The Runner's Kit in FP | Every ground and wall move of §4, the stair slide included (3P tilt and FP) | 2-3 h |
| P3 The Ascent in FP | Rope, Leaper, wings, flight, Sovereign, landings | 2-3 h |
| P4 Touches | #1, 3, 4, 6, 7, 10, 11 of §5 | 2-3 h |
| P5 Station scenes | The scene kit (lean-in shot, input, cursor ray, arms in the world, vanilla escape hatch) and the crafting table; then the Forge and anvils, cooking, the Journal. Built: the crafting table and furnace scenes, the Forge/anvil and campfire touches; the Journal moves to P6. No `anims_station_menu` test: the scene sends the vanilla menu's own clicks, so the server sees nothing new (tours check the scene) | 4-6 h |
| P6 The rest | #12-14; books and the Spire board's notices (#9). Built: the palm (with its glow), the notice torn off, the stations' touches (grindstone, lectern, enchanting, smithing, stonecutter, loom, cartography, brewing), a page turned as a book opens, the Ritual's hands | 2-3 h |

Total about 15-22 hours of builder time. P4 and P5 can swap if Euan wants the crafting table sooner.

## 7. Euan's decisions (with recommendations)
1. Dropping off a pipe: **A** (Shift slides, Jump + back kicks off), B (double-tap Shift) or C (hold Back).
2. No keys on a pipe: **hold still** (recommended) or vanilla's slow drift.
3. Vanilla ladders get the grip too: **yes** (their slide stays at vanilla's 0.15 b/t).
4. See your own legs in first person during moves: **yes**, moves only.
5. Station scenes in full (the crafting table as a real 3×3 surface): **yes, in P5**, with sneak + right-click for the
   plain screen. Chests stay touches, never scenes.
6. Settings: **one switch per family** (moves, touches, stations) and per station, all on by default. A single
   all-or-nothing toggle frustrates players (ParCool's issue tracker).
7. Order: **P1 first**: it fixes the pipe and builds the rig everything else stands on.

## 8. Research notes (Cinematic Interactions and its neighbours)
- Cinematic Interactions (southware; MIT code, CC BY 4.0 hammer model; CurseForge, ~111K downloads, created mid-October
  2026): Fabric 1.21-1.21.11 and 26.1.1-26.3 (beta); NeoForge 1.21.1 only. Workstations only. Timing in its source:
  walk 0.25 s plus distance at 3.6 b/s, lean-in 1.2 s (0.4 s on a quick return), exit 1.05 s, hop to a neighbouring
  station 0.45 s, a tap 0.22 s, speed 50-200 %. Motion is code (smootherstep, cubic, overshoot, damped springs); arms
  are its own skinned meshes drawn in the world; its own camera rig (breathing, up to 1° drift toward the cursor, up
  to 1.6° roll only on approach, FOV narrows into the scene). Movement, jump, E or Esc exits; sneak + right-click opens
  the vanilla screen. https://www.curseforge.com/minecraft/mc-mods/cinematic-interactions ,
  https://github.com/dcvke/CinematicInteractions
- What we take: the lean-in and the surface you work on, the escape hatch, short taps, per-station switches, motion
  in code with springs (our language already). What we leave: the 1.2 s lean-in (too slow for our pace; 0.6 s), the
  auto-walk, mixins.
- Neighbours: First-person Model (full body in first person; conflicts with CI, so our body layer steps aside when it
  is installed); Better Combat (a wind-up reads as input lag; its "feint" lets players cancel); ParCool (players ask for
  separate hand and camera toggles); Real Camera (never let body animation drive the camera); ImmersiveMC (keep the menu
  as a way out); Eating Animation (cheap, readable, no input cost).
- Complaints to design around: conflicts with other mods' camera and hand mixins; slow storage trips (no scenes on
  chests); motion sickness from camera motion you didn't cause, roll and FOV (respect the comfort settings, roll only
  on approach); wind-ups that feel like lag (the action happens on the click and the animation catches up).

## 9. The API, for every module (combat included)

Everything first person goes through `dev.awaken.core.api.FirstPerson` (common: holds no client classes, a no-op on a
server). The `anims` client installs the player and composes three layers each frame. A module never adds its own
`RenderHandEvent` listener.

**World layer: the body in the world, anchored to what it touches.**
```java
FirstPerson.world("body_grip", 100, (partialTick, rig) -> {
    // fill the limbs you pose this frame (world coordinates); return true to own the body this frame
    rig.fx = facingX; rig.fz = facingZ;                       // where the body faces
    rig.reach(Limb.RIGHT_ARM, shoulderX, shoulderY, shoulderZ, // near the joint
              fistX, fistY, fistZ, Rig.ARM_REACH,              // where the fist must be
              0, 1, 0, weight);                                // the limb's front; 0..1 how much shows
    rig.limb(Limb.BODY, neck..., hips..., front..., weight);   // or a limb from root toward tip
    return true;
});
```
Priorities: movement 100, interactions 200, combat 300 (higher is asked first; the first that returns true wins). Legs
are drawn in the world with real depth; arms are drawn as viewmodels in the hand pass (each fist where the world puts
it on screen, pushed out to 0.85 from the eye, the arm rising from under the frame's corner), because true-scale arms a
third of a block from the eye fill half the frame (P1 tour). While the rig shows arms, vanilla's hands (and held items)
are hidden; draw a held weapon yourself if the world arms hold one (P5's station kit adds item drawing at a limb's end).

**View layer: moves on top of vanilla's hands and whatever they hold.**
```java
FirstPerson.view((partialTick, v) -> {
    // view space: x right, y up, z toward you (blocks); then pitch, yaw, roll (degrees, applied in that order)
    FirstPerson.View.add(v.both, k, x, y, z, pitch, yaw, roll);   // both hands
    FirstPerson.View.add(v.main, k, ...);                          // the main hand only
    v.drawOff = true;                                              // draw the off arm even when it's empty
    v.hideMain = true;                                             // hide a hand
});
```
Stances add every frame; keep them cheap and allocation-free.

**Fists, for any empty hand that must go somewhere (P2).** Moving vanilla's arm up by offsets floats its forearm into
view. Instead key where the fist is on screen (view space) and the arm is drawn rising to it from under the frame:
```java
FirstPerson.View.fist(v.fistMain, k, x, y, z);   // a stance's fist (weighted; a clip's fist replaces a stance's)
```
Clips: `clip(..., fists=True)` with keys `fist(x, y, z)`; vanilla's resting fist is `REST_FIST` (0.744, -0.455,
-1.072); an off hand comes in from and goes back to `OFF_OUT` under the frame; `to_stance=True` for clips that end where
a stance holds the hands. A hand holding an item ignores fists (combat: use offsets on the held weapon).
Clean fists (P1.1, Euan 2026-10-25): vanilla's blocky fist is the hand, never modelled fingers. A world source sets
`rig.handDistance` (nearer reads bigger), `rig.held[0|1]` (an ItemStack at the fist) and `rig.glow`; its arms rise from
under their fists. In the hand pass the frame shows y down to about 0.7·|z|: keep keyed fists inside it.
`v.tint` colours the fists' arms (RGB, 0 none: the Sovereign's gold); `v.glow` (0..1, sources take the most) lights them
from within (a palm on a waystone; combat: a charging cast).

**The procedural layer.** Look-lag (the hands trail fast turns) runs for everyone; `FirstPerson.reducedMotion` halves it.

**Clips: one-shots and loops written in Python, with events.**
```java
FirstPerson.play("fp_pipe_kick");           // from its start; FirstPerson.play(name, speed)
FirstPerson.stop("fp_guard_loop");          // fades out over 3 ticks
FirstPerson.listen((clip, event) -> { if (event.equals("impact")) hitStop(); });
```
Author clips in `tools/art/specs/fp_*.py` (add the file to `anims.FpClipSet.FILES`): keys `(tick, hand(x, y, z, pitch,
yaw, roll), "curve")` per hand (`main`, `off`), `both=True` to draw the empty off arm, `events=[(tick, "name")]`,
`loop=True` for held stances. Curves are the body clips' names (`dev.awaken.anims.Ease`: linear, easeoutcubic,
easeinoutsine, easeinquad, easeoutquad, easeoutsine, easeinsine, easeincubic, easeoutback, smooth, step). A one-shot
ends at rest (all zeros) so it hands back to vanilla cleanly (`anims_fp_clips` checks). Authored right-handed; the game
mirrors x, yaw and roll for a left main arm. `python3 tools/art/specs/fp_moves.py preview DIR` draws a strip from the eye.

**Timing language** (keep it): blend in 2-4 ticks, out 4-6; one-shots ease out of their start fast (easeoutcubic) and
settle (easeinoutsine); a small anticipation before a big action; a held pose trembles slightly; hands trail fast turns
(look-lag, P2); comfort settings respected (reduced motion halves amplitudes).

