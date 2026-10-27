# Power animations: every cast as something your body does

Status: PLAN, awaiting Euan's approval (2026-10-27). Nothing in `src/` is changed by this branch. Branch `pack/power-anims-plan`.
Page: https://claude.ai/artifact/KBCT1me9LHaDXeUmGS5Xak (a copy is `docs/power-anims/plan.html`). Proof: `docs/power-anims/proof/` (pose sheets, numbers, the scripts).

Euan, playing, 2026-10-27: "the beam spell... it doesn't even come out of the core place it should... it's more so coming from the
wrist or something, and it's just like putting up your wrist slightly in a weird position"; "the stamping one, the one where you
stamp and it creates a wave... there's no real animation to it. Like it doesn't feel like you're actually stamping, with your foot...
if it had a small animation and a thump to it, that would be really cool"; "find how we can align everything better and make it feel
more like an actual thing that's happening... more cinematic. And immersive."

## 0. The short version

- **Both complaints are real and have exact causes in the code.** The beam's visible start is a hard-coded offset from the eye, not
  from the hand: in third person it sits about 0.5 block behind the drawn palm, and in first person (by the code's numbers) it starts
  about 0.7 of the half-height below the raised fist. Worse, in third person with the Player Animation Library the body holds the
  *charging* clip (hands cupped at the hip) for the whole beam, because the beam's events never carry a release pose. The stomp
  (Shockwave) has no stomp anywhere: the third-person clip is the Kinetic *punch*, the first-person arm dips 0.12 block, there is no
  foot, the wave leaves on the key-press tick, the sound is a wind burst and the camera only rumbles.
- **Same cause behind all 59 casts.** Every power plays one of nine *family* poses, the effect starts at the same tick the pose
  starts (the body's release comes 3 to 10 ticks later), effects start at a constant offset from the eye instead of a body point,
  first-person casts are not part of the Hands system at all, there are three unconnected camera-feel paths, and every sound is a
  vanilla remix played once at press time. Mean audit score 8.8 of 18; 45 of 59 powers score 1 or 0 on Body.
- **The plan is one system, not 59 one-offs:** (1) a `CastProfile` per power (data) that names its beats and body points;
  (2) named cast points (palm, both palms, core, foot, ground plant) resolved from the *drawn* body, the same in first and third person;
  (3) a pose and timing language per family with per-power accents, baked with the rig tools we already have; (4) first person moved
  into Hands (fists, legs, events); (5) one impact kit (thump, hit-stop, dust, layered sound) on one camera director; (6) Lens light
  for the big moments. Build order starts with the beam and the stomp as the proof.
- **Estimate** at the pace git history shows: about 20 hours of builder time in six phases; the beam and stomp proof is the first 4 to 5
  hours and ships on its own (section 7).

## 1. What I read, and what I did not do

Read: `powers/` (catalog, `Casting`, `Cast`, `Fx`, `fx/*`, `client/fx/*`, `PowerSounds`, `merge/powers/.../sounds.json`),
`tools/art/specs/power_casts.py` and its generated `power_casts.json`, `fp_moves.py`, `core.api.FirstPerson`, `CombatFeedback`,
`anims.client.WorldBody`, `arms.client.BladeTraceLayer` / `ArmsFeel` / `ArmsWrist`, `lens.client.LensClient`, SPEC §6, §17.13, §22,
§24, §25, and vanilla's `ItemInHandLayer` in `.ref/src`. Scores are a **code read, not a playtest**: I did not run the client or a tour.
The first-person screen numbers come from the constants in `CastAnims.offset` and `Anchors.hand`, so they say where the code puts things,
and the shots should confirm them. The default FOV of 70 is assumed (wider moves the start up the screen, see `numbers.json`).
Euan plays mostly in Shoulder Surfing's third-person view (SPEC §17.13 starts a session there), so third person matters as much as first.

## 2. Euan's two, traced

### 2.1 The beam (`beam`, Energy, hold; `prism_lance` is the same code on an ultimate)

| Where | What the code does | Where it should be |
|---|---|---|
| Server `EnergyPowers.beam` | sends a `CHARGE` at `hand(player)` = eye + look*0.4 + right*0.35 - 0.35 (a formula), then every fourth tick `beamRay` -> `Fx.beam(...)` with start = `from.getEyePosition()`; damage traces from the eye (correct) | damage stays on the eye ray; the *visual* starts at the palm |
| Client `FxRecipes.beam` | replaces the start with `attach(b, caster, Anchors.HAND)` | a named PALM point |
| `Anchors.hand`, third person | eye + look*0.35 + side*0.36 - 0.45 down (constants) | the drawn right palm |
| `Anchors.hand`, first person | eye + look*0.55 + side*0.32 - 0.3 down (constants, level look) | where the drawn fist is |
| `CastAnims.offset` AIM / AIM_HOLD | the vanilla arm moved (-0.2, +0.16, -0.24) and tipped 12 degrees: "the wrist put up" | a fist keyed on screen by Hands |
| Third-person pose | `AIM_HOLD` -> clip `hold_energy`: both hands **cupped at the hip, trembling**, for the whole channel. The beam's events come from the legacy `Fx.beam` helper and carry no pose, so `cast_energy_release` (the palm thrust) never plays | thrust arm, braced stance, held |
| Camera, light | none: no recoil, no tremble, no bloom, no light on the hand | recoil on start, tremble, light |

The numbers (`proof/numbers.json`; third person by forward kinematics of the shipped clip, first person from the constants):
- Third person: the drawn palm is **0.46 to 0.63 block** from the anchor (0.45 to 0.55 ahead of it, up to 0.42 above it).
- First person at FOV 70: fist at (0.32, -0.06) of the half-frame, beam start at (0.47, -0.78): the beam begins at the lower screen edge
  under the forearm and sweeps up past the *left* of the fist (at the fist's height it is at x = 0.04). At FOV 90 it starts at (0.33, -0.55).
  That is "it comes out of the wrist".

Pose sheets (frame by frame, the shipped Energy clip with the old anchor ringed): `proof/beam_before.png`; the proposed beam through the core:
`proof/beam_after.png` (cyan = core/sternum, gold = palm; the beam would start at the palm and the light would run from the core down the arm).

### 2.2 The stomp (`shockwave`, Kinetic; the same STOMP pose is used by `flow`, `spire`, `frost_nova`)

| Layer | What the code does |
|---|---|
| Server `KineticPowers.shockwave` | on the press tick: sound `power.shockwave` (vanilla `wind_charge.wind_burst`), `CONE` event at `c.pos()`, a 10-tick running front that damages and knocks back, `Fx.quake(...1.5)`; no `c.feel` (no hit-stop) |
| Third person with PAL | `CastClips.clip(KINETIC, STOMP)` falls to the default `cast_kinetic` = **the grounded punch** (coil, hips snap, straight fist). `proof/stomp_before.png` |
| Third person without PAL | `CastAnims.modify` STOMP = crouch for a few ticks plus a 16 degree lean. No leg |
| First person | `CastAnims.offset` STOMP: `dy = -0.12 * sin(pi * p)`: the hand dips a hand's width. No leg, no camera dip |
| Effect | the wave starts at the feet centre on tick 0, before any foot could have landed; `cone` has no kick, no FOV, no hit-stop |
| Camera | `Fx.quake` -> `CombatFeel` random sine rumble, not a thump |
| Sound | a wind burst, once, at press |

The proposal (`proof/stomp_after.png`, bent clip, no clipping): coil on the off leg (100 ms), knee to hip height (200 ms), hang one tick,
**plant at 300 ms** with a deep knee and a weight drop, rebound, settle. The planted foot is 0.19 block ahead of the body: that is where
the wave starts (`GROUND_PLANT`). Peak boot height 0.77, forward 0.46 (`numbers.json`).

## 3. The audit: every power, scored

59 castable powers (63 registered; `danger_sense`, `aura`, `wall_run`, `water_run` are passives with no cast). Each is scored 0 to 3 on six
columns from the code. **0** missing or wrong, **1** a placeholder or a partial fit, **2** fits the power, **3** designed for this power.
Nothing scores 3 in a column except Ground Slam's timing: nothing is designed per power yet.

| Column | The rule |
|---|---|
| **B**ody | the lower of the third-person clip (does it read as this power's gesture?) and the first-person arm offset (does it read at all?) |
| **O**rigin | where the effect starts: 3 a world point by design (ground, target, sky, around you); 2 a body point that fits (feet, head); 1 a hand-anchored effect on a constant offset; 0 visibly off |
| **T**iming | effect, sound and camera against the body's release frame: 3 on it; 2 held wind-up or predicted movement; 1 the effect leaves on tick 0 and the release lands 3 to 10 ticks later; 0 the pose contradicts the effect |
| **C**amera | 0 nothing; 1 only the rumble or a flash; 2 a directional kick, FOV punch or hit-stop; 3 designed (kick, FOV, hold, settle, light) |
| **S**ound | 0 wrong or none; 1 one vanilla remix at press; 2 staged (charge, release, impact, loop); 3 layered kit tied to the frames |
| **L**ook | 2 a dedicated recipe (own sprites, rings, debris); 1 generic (ring, smoke, aura); no power has a Lens look, so none can score 3 |

The two Euan named come first; the rest are worst first. Data: `proof/audit.py`.

| # | Power | Family | Cast | Pose and the clip it plays | B | O | T | C | S | L | Sum | What is wrong |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `beam` (Euan) | Energy | hold | AIM_HOLD (+AIM): hold_energy (hands cupped at the hip) the whole time | 0 | 0 | 0 | 0 | 1 | 2 | **3** | EUAN: starts 0.5 block behind the palm (3P) and 0.7 of the half-screen below the fist (1P); 3P holds the charge pose while firing; no camera |
| 2 | `shockwave` (Euan) | Kinetic | press | STOMP: cast_kinetic (a punch) | 0 | 1 | 1 | 1 | 0 | 2 | **5** | EUAN: no foot, no thump. 3P is a punch, 1P arm dips 0.12, wave starts at tick 0, sound is wind |
| 3 | `prism_lance` | Energy | hold | AIM_HOLD (+AIM): hold_energy the whole time | 0 | 0 | 0 | 1 | 1 | 2 | **4** | Same as the beam, on an ultimate |
| 4 | `cord_lash` | Spirit | press | THROW: cast_spirit | 1 | 1 | 1 | 0 | 1 | 1 | **5** | A lash plays a prayer; cord starts at the hand anchor |
| 5 | `drone` | Tech | press | RAISE: cast_tech | 1 | 2 | 1 | 0 | 1 | 1 | **6** | No device beat on the wrist |
| 6 | `turret` | Tech | press | CONJURE: cast_tech | 1 | 2 | 1 | 0 | 1 | 1 | **6** | Same |
| 7 | `cloak` | Shadow | press | CONJURE: cast_shadow | 1 | 3 | 1 | 0 | 1 | 1 | **7** | Pose fits; no body beat at the vanish |
| 8 | `cone` | Architect | press | RAISE: cast_architect | 1 | 2 | 1 | 0 | 1 | 2 | **7** | As ramp |
| 9 | `decoy` | Mind | press | THROW: cast_mind (temple, point) | 1 | 2 | 1 | 0 | 1 | 2 | **7** | A throw plays a temple-and-point |
| 10 | `floor` | Architect | press | CONJURE: cast_architect | 1 | 2 | 1 | 0 | 1 | 2 | **7** | As ramp |
| 11 | `flow` | Velocity | press | STOMP: cast_velocity (a sprint lunge) | 0 | 3 | 1 | 0 | 2 | 1 | **7** | Time slows to a quarter and the body lunges; no client cue at all for the slow (no look, no sound ramp) |
| 12 | `grapple` | Tech | move | AIM: cast_tech | 1 | 1 | 2 | 0 | 2 | 1 | **7** | Cable starts at the constant hand anchor, not the wrist device |
| 13 | `gravity_flip` | Flight | press | SKYWARD: cast_flight | 1 | 3 | 1 | 0 | 1 | 1 | **7** | No camera, no body beat for the flip |
| 14 | `nanite_skin` | Tech | press | BRACE: cast_tech | 1 | 3 | 1 | 0 | 1 | 1 | **7** | A tap-and-lock for a skin |
| 15 | `ramp` | Architect | press | CONJURE: cast_architect | 1 | 2 | 1 | 0 | 1 | 2 | **7** | As wall; 1P one hand low |
| 16 | `rampart` | Architect | press | SKYWARD: cast_architect | 1 | 2 | 1 | 0 | 1 | 2 | **7** | A fort rises with no ground beat |
| 17 | `third_eye` | Spirit | press | TEMPLE: cast_spirit | 1 | 3 | 1 | 0 | 1 | 1 | **7** | A prayer for a brow gesture |
| 18 | `twin_strike` | Spirit | press | CLAP: cast_spirit | 1 | 3 | 1 | 0 | 1 | 1 | **7** | A buff with a clap in 1P only |
| 19 | `waking_dream` | Spirit | press | CONJURE / PUSH2: cast_spirit | 1 | 2 | 1 | 0 | 2 | 1 | **7** | Prayer for shaping the world |
| 20 | `brace` | Kinetic | press | BRACE: cast_kinetic (a punch) | 0 | 3 | 1 | 1 | 2 | 1 | **8** | A guard plays a punch; the end burst is a good slam |
| 21 | `bulwark` | Spirit | press | SKYWARD: cast_spirit | 1 | 3 | 1 | 0 | 1 | 2 | **8** | Fits loosely; no camera |
| 22 | `drone_swarm` | Tech | press | SKYWARD: cast_tech | 1 | 2 | 1 | 2 | 1 | 1 | **8** | Fine beat, generic body |
| 23 | `frost_nova` | Spirit | press | STOMP: cast_spirit (prayer) | 0 | 3 | 1 | 1 | 1 | 2 | **8** | A frost burst from the ground, with a prayer and no foot |
| 24 | `spire` | Architect | press | STOMP: cast_architect (frame-draw) | 0 | 3 | 1 | 0 | 2 | 2 | **8** | A pillar throws you up with no stomp, no kick, no camera at all |
| 25 | `spirit_palm` | Spirit | press | PALM: cast_spirit (palms together, arms open) | 0 | 1 | 1 | 2 | 2 | 2 | **8** | A palm strike plays a prayer; the print starts at eye plus 0.6, not the palm |
| 26 | `terrain_rip` | Kinetic | press | THROW: cast_kinetic (a punch) | 1 | 2 | 1 | 1 | 1 | 2 | **8** | Rips the ground with a punch; no crouch-and-heave; blocks do not come from the hands |
| 27 | `wall` | Architect | press | PUSH2: cast_architect | 2 | 2 | 1 | 0 | 1 | 2 | **8** | Frame-and-set fits; blocks do not come from the hands |
| 28 | `afterimage` | Velocity | press | DASH: cast_velocity | 2 | 3 | 1 | 0 | 1 | 2 | **9** | No camera; clones appear with no body beat |
| 29 | `charged_shot` | Energy | hold | AIM_HOLD / AIM: hold_energy, cast_energy_release | 1 | 1 | 2 | 1 | 2 | 2 | **9** | Bolt leaves the server formula for a hand, which is not the palm |
| 30 | `collapse` | Architect | press | THROW: cast_architect | 1 | 2 | 1 | 1 | 2 | 2 | **9** | Pose is a frame, the power is a heave |
| 31 | `dominate` | Mind | press | TEMPLE: cast_mind | 2 | 3 | 1 | 0 | 1 | 2 | **9** | Ripples leave tick 0, the finger point lands at tick 9 |
| 32 | `flight` | Flight | toggle | SKYWARD: cast_flight | 1 | 3 | 2 | 1 | 1 | 1 | **9** | Launch sweep fits; 1P arm up only |
| 33 | `foresight` | Mind | press | TEMPLE: cast_mind | 2 | 3 | 1 | 0 | 2 | 1 | **9** | A big slow-sight moment with no look and no camera |
| 34 | `hover` | Flight | toggle | HOVER: hover (loop) | 1 | 3 | 2 | 0 | 2 | 1 | **9** | 3P clip is right; 1P is a 0.03 bob, no legs |
| 35 | `mend` | Spirit | press | CLAP: cast_spirit | 2 | 3 | 1 | 0 | 1 | 2 | **9** | Fits |
| 36 | `read_intent` | Mind | press | TEMPLE: cast_mind | 2 | 3 | 1 | 0 | 1 | 2 | **9** | Fits; no camera |
| 37 | `storm_call` | Spirit | press | RAISE: cast_spirit | 1 | 3 | 1 | 1 | 1 | 2 | **9** | Fits loosely |
| 38 | `telekinesis` | Flight | hold | AIM_HOLD / THROW: hold_flight, cast_flight_release | 1 | 1 | 2 | 1 | 2 | 2 | **9** | Tether starts at the constant hand anchor |
| 39 | `barrier` | Energy | press | PUSH2: cast_energy | 2 | 3 | 1 | 0 | 2 | 2 | **10** | Fits; no camera |
| 40 | `citadel` | Architect | press | SKYWARD: cast_architect | 1 | 2 | 1 | 2 | 2 | 2 | **10** | Ultimate beat is good; the body only sets a frame |
| 41 | `gravity_well` | Flight | press | AIM: cast_flight (a launch sweep) | 1 | 2 | 1 | 2 | 2 | 2 | **10** | Aims a well but plays a lift-off; the well appears at the target without leaving a hand |
| 42 | `legion` | Shadow | press | CONJURE: cast_shadow | 1 | 3 | 1 | 2 | 1 | 2 | **10** | One sound for an army |
| 43 | `railgun` | Tech | hold | AIM_HOLD / AIM: hold_tech, cast_tech_release | 1 | 1 | 2 | 2 | 2 | 2 | **10** | Slug starts at the constant hand anchor |
| 44 | `void_maw` | Spirit | press | AIM: cast_spirit | 1 | 2 | 1 | 2 | 2 | 2 | **10** | Prayer-and-rise for an aimed ultimate |
| 45 | `echo_swap` | Shadow | press | CLAP: cast_shadow | 1 | 3 | 1 | 2 | 2 | 2 | **11** | Clap in 1P only |
| 46 | `haymaker` | Kinetic | hold | PUNCH_WIND / PUNCH: hold_kinetic, cast_kinetic_release | 2 | 1 | 2 | 2 | 2 | 2 | **11** | Charge orb sits on the constant hand anchor, not the fist |
| 47 | `jet_burst` | Tech | move | DASH: cast_velocity | 1 | 3 | 2 | 2 | 1 | 2 | **11** | Boots fire; the body plays a sprint lunge |
| 48 | `leap` | Kinetic | move | DASH: cast_velocity | 1 | 3 | 2 | 2 | 1 | 2 | **11** | A jump plays the Velocity sprint lunge; landing slam is good |
| 49 | `nova` | Energy | press | SKYWARD / CHARGE2: hold_energy, cast_energy_release | 1 | 2 | 2 | 2 | 2 | 2 | **11** | Arms-up burst plays a forward palm thrust; orb on the hand anchor |
| 50 | `overload` | Energy | press | SKYWARD / CHARGE2: hold_energy, cast_energy_release | 1 | 2 | 2 | 2 | 2 | 2 | **11** | The ultimate of the core is gathered at a hand, released as a palm thrust |
| 51 | `thunderclap` | Kinetic | press | CLAP: cast_kinetic (a punch) | 1 | 3 | 1 | 2 | 2 | 2 | **11** | Clap in 1P, punch in 3P; the strike does not wait for the hands to meet |
| 52 | `dash` | Velocity | move | DASH: cast_velocity | 2 | 3 | 2 | 2 | 1 | 2 | **12** | Fits. Sound is one random whoosh |
| 53 | `ember_step` | Spirit | move | DASH: cast_velocity | 2 | 3 | 2 | 2 | 1 | 2 | **12** | Fits |
| 54 | `ghost_step` | Spirit | move | DASH: cast_velocity | 2 | 3 | 2 | 2 | 2 | 1 | **12** | Fits |
| 55 | `hundred_palms` | Spirit | press | FLURRY: flurry | 2 | 2 | 2 | 2 | 2 | 2 | **12** | Fits; the palms are drawn at the eye, not from the hands |
| 56 | `meteor_dive` | Flight | press | SKYWARD+SLAM_WIND / SLAM: cast_flight, slam | 1 | 3 | 2 | 2 | 2 | 2 | **12** | Strong landing; 1P has no dive pose beyond arms up |
| 57 | `shadow_step` | Shadow | move | DASH: cast_velocity (not Shadow's own) | 1 | 3 | 2 | 2 | 2 | 2 | **12** | Every dash plays Velocity's sprint; Shadow's crouch sweep is never used for it |
| 58 | `lightning_step` | Velocity | move | DASH: cast_velocity | 2 | 3 | 2 | 2 | 2 | 2 | **13** | Fits |
| 59 | `ground_slam` | Kinetic | press | SLAM / SLAM_WIND: slam_wind, slam | 2 | 3 | 3 | 2 | 2 | 2 | **14** | Best cast. 1P is arms lifted then dropped: no crouch, no legs, no hands on the ground |

| Family | Powers | Body | Origin | Timing | Camera | Sound | Look | Sum |
|---|---|---|---|---|---|---|---|---|
| Kinetic | 7 | 1.0 | 2.3 | 1.6 | 1.6 | 1.4 | 1.9 | 9.7 |
| Velocity | 4 | 1.5 | 3.0 | 1.5 | 1.0 | 1.5 | 1.8 | 10.2 |
| Flight | 6 | 1.0 | 2.5 | 1.7 | 1.0 | 1.7 | 1.5 | 9.3 |
| Energy | 6 | 0.8 | 1.3 | 1.2 | 1.0 | 1.7 | 2.0 | 8.0 |
| Shadow | 4 | 1.0 | 3.0 | 1.2 | 1.5 | 1.5 | 1.8 | 10.0 |
| Mind | 4 | 1.8 | 2.8 | 1.0 | 0.0 | 1.2 | 1.8 | 8.5 |
| Architect | 8 | 1.0 | 2.1 | 1.0 | 0.4 | 1.4 | 2.0 | 7.9 |
| Tech | 7 | 1.0 | 2.0 | 1.4 | 0.9 | 1.3 | 1.3 | 7.9 |
| Spirit | 13 | 1.2 | 2.5 | 1.2 | 0.9 | 1.4 | 1.6 | 8.8 |
| **All** | 59 | 1.1 | 2.3 | 1.3 | 0.9 | 1.4 | 1.7 | 8.8 |

Score counts (0 / 1 / 2 / 3): Body 8/37/14/0; Origin 2/8/18/31; Timing 2/38/18/1; Camera 27/10/22/0; Sound 1/31/27/0; Look 0/17/42/0. Mean total 8.8 of 18.

What the table says: Origin is the best column (most effects start at a world point) and Camera the worst (27 of 59 have none). Energy and Architect/Tech are the weakest families; Kinetic's Ground Slam, the dashes and Meteor Dive are the only casts that feel designed.
## 4. The shared causes

1. **One pose per family, not per power.** `CastAnim` has 21 poses; `CastClips.clip` maps a pose to `cast_<family>` (nine families, 42 clips
   in `power_casts.json`) except dash, slam, flurry and hover. A pose that no family clip says falls to that family's default: STOMP, THROW,
   CLAP, BRACE and PALM all become "the family's cast". So Shockwave, Brace and Thunderclap play Kinetic's punch, Spirit Palm plays a prayer,
   Flow plays a sprint lunge, every dash (Shadow Step, Jet Burst, Ember Step) plays Velocity's. 45 of 59 powers score 0 or 1 on Body.
2. **Effects start at constants, not at a body.** `Anchors.HAND` is `eye + look*0.55/0.35 + side*0.32/0.36 - 0.3/0.45`. The server has two more
   formulas (`EnergyPowers.hand()`, Spirit Palm's `eye + look*0.6 - 0.3`). Nothing reads where the drawn hand is. Beam, charge orbs, tethers,
   rail, bolts and palm prints all start in the air near the hand. (Arms already solved this for blades: `BladeTraceLayer` samples the posed arm.)
3. **First-person casts are outside Hands.** `CastAnims.onHand` moves vanilla's arm by offsets; `powers/` never calls `FirstPerson`.
   Hands has fists, grips, clips with events, legs in the world and glow, and SPEC §24 says folding casts in "is the powers module's step".
   Hence no hand placed on screen, no legs, no keyed events.
4. **Everything fires on the press tick; the body's release lands later.** `power_casts.py` says so itself: "the wind-up is two or three ticks
   because the effect leaves at the start of the clip". The release keys sit at ticks 3 (Velocity), 4 (Kinetic, Tech), 5 (Energy), 6 (Shadow, Flight), 7 (Spirit),
   9 (Mind), 10 (Architect). For the caster the gap is hidden or not depending on the connection; everyone else sees the effect at arrival, before the hands.
5. **Channels skip the release.** The beam and Prism Lance send their beam events through the legacy `Fx.beam` helper, which carries no pose, so the held
   wind-up pose is the whole cast and the release clip never plays.
6. **Three camera paths that do not know each other.** `FxScreen` (spring kick, FOV punch, flash, impact frame), `CombatFeel` (a random sine
   shake from `Fx.quake`) and Arms' `CombatFeedback` (directional kick, `hold`, hit-stop, FOV moments, a Lens breath). Powers use
   `CombatFeedback` only for the victim's hit-stop: the caster's own hands and view never hold, and no power uses the Feel Director or the action
   camera's arm drop (landings do). 27 of 59 powers have no camera at all (Beam, Barrier, all four Mind casts, six of the eight Architect powers).
7. **Sound is 50 vanilla remixes played once at press.** `sounds.json` points `power.*` at `mace.smash_ground`, `wind_charge.wind_burst`,
   `beacon.ambient` and so on. The server plays it on the press tick at the caster's feet for everyone. No foley, no layers, no tail, no
   position at the hand. Arms and the body have authored kits (`audio_arms.py`, `audio_body.py`, `audio_layers.py`, `audio_rig.py`); powers do not.
8. **No light.** No power touches Lens (`override`, `placeLook`, `ritual`, `motion` are all unused by `powers/`); Lens has no point light or pulse for a
   cast. Flow and Foresight, the time-slow powers, change the server's tick rate and tell the client nothing.
9. **No ground contact.** Heavy casts originate at the caster's position (`c.pos()`), not at a foot or a fist on the ground; no planted-foot puff.
10. **Multiplayer is half right.** The server's `FxEvent` already carries caster, pose, seed and style to everyone within 72 blocks and every client
    builds the same effect from the seed (good). But the pose is chosen by family, the origin by constant, and others see no anticipation.

## 5. The plan

### 5.1 A cast profile per power (the spine)

One data row per power, written by a script (`tools/art/specs/power_cast_table.py`, like `fp_moves.py`) into `data/awaken/powers/casts.json`, read by
server and client, held to the catalog by a GameTest (every power has a row; every clip and sound it names exists; every release tick is a key in
its clip). The `FxEvent`'s 16 high bits stop meaning "a `CastAnim` ordinal" and mean "profile id" (same size on the wire); `CastAnim` stays as the
generic fallback.

```
"shockwave": { "family": "kinetic", "windup": 4,                  // server delay in ticks (0 = the press tick)
  "beats": { "anticipation": [0, 4], "release": 4, "follow": [4, 9], "recovery": [9, 16] },
  "clip3p": { "begin": "stomp" }, "clip1p": "fp_stomp",
  "points": { "origin": "ground_plant", "dir": "aim_flat" },
  "feel": { "moment": "thump_heavy", "hitstopMs": 60 }, "sound": "kinetic_stomp", "lens": null }
"beam": { "family": "energy", "windup": 0, "beats": { "anticipation": [0, 5], "release": 5, "hold": "loop", "recovery": [0, 8] },
  "clip3p": { "begin": "beam_gather", "hold": "beam_hold", "end": "beam_end" }, "clip1p": "fp_beam",
  "points": { "gather": "core", "origin": "palm", "dir": "aim" },
  "feel": { "moment": "recoil_light", "tremble": 0.3 }, "sound": "energy_beam", "lens": "cast_beam" }
```

The 3P clips and the 1P clips are both written from this table, so the release tick is one number for both.

### 5.2 Named cast points: where an effect starts

`PALM_MAIN`, `PALM_OFF`, `PALMS` (the middle), `CORE` (sternum), `BROW`, `FOOT_MAIN`, `FOOT_OFF`, `GROUND_PLANT` (the ground under the planted foot),
`FEET` (the ground under you). A client class `Muzzles` answers `Muzzles.at(entity, POINT, partialTick, out[3])` and every recipe that today passes
`Anchors.HAND` passes a point instead. How each is known:

| Viewer | How the point is found | Why |
|---|---|---|
| You, first person | **authored**: the point is a key in the first-person clip (a fist or a leg in view space), put into the world with Hands' own lens factor (SPEC §24 "The lens") | exact by construction: the beam leaves the fist that is drawn |
| You in the shoulder view, and everyone else | **sampled**: a `RenderLayer` (like `BladeTraceLayer`) calls the model's `translateToHand` / torso / leg pose after the library has posed it and records the world position for entities with a live cast | proven in this repo for blades, with PAL's arms |
| An entity that was not drawn this frame (far, culled) | **baked**: `power_casts.py` writes each clip's point tracks (`proof/numbers.json` shows the format) and the client rotates them by body yaw | the effect still starts at the body |

The server never sends positions it cannot know: it sends the profile, the aim direction and the hit point; damage and aim traces stay on the eye
ray exactly as now. The visible beam goes from the palm to the hit point. Fixed arrays, no allocation (section 5.9).

### 5.3 A body language per family, and per-power accents

Every cast has four beats: **anticipation** (the body gathers, 1 to 5 ticks), **release** (the frame the effect leaves), **follow-through** (hold the
pose through the impact and the hit-stop) and **recovery** (ease home, 6 to 10 ticks). What changes by family is how they feel:

| Family | Body language | Anticipation | Release | Follow-through and recovery | Camera | Signature |
|---|---|---|---|---|---|---|
| Kinetic | heavy, low, grounded: weight shift, planted feet | 3-5 ticks, slow coil | a plant or a hit, hard | hold 2 ticks, settle slow (9) | thump: dip + FOV in + hold | ground contact: foot, fist, palm on ground |
| Velocity | sharp: lean, arms swept | 1 tick | explosive | no hold, fast out | FOV out, speed lines | lines and afterimage, no dust |
| Flight, Gravity | floaty: low to high, pause at the top | 4-6 ticks gather | rise on the breath | hang, slow float down | slow push, soft roll | weightless, a lift in the body |
| Energy | channelled from the **core**: hands to the sternum, arms run the light out | 3-5 ticks, tremble | palms open to the aim | recoil, brace, hold; arms drop slow | micro-recoil + bloom pulse | light travels sternum -> arm -> palm |
| Shadow | low, quiet, wide sweep | 3 ticks crouch | a cut, no flash | stay low, rise slow | vignette, no flash | ink, dry sound |
| Mind | still: fingers to the brow, a breath | 4-9 ticks stillness | a point | long hold, head tilt | slow narrow, soft vignette | only the face moves |
| Architect | precise: ruler-straight, a frame drawn in the air | 2-5 ticks | a firm set on a beat | hold the frame, drop | small click-kick | a tick per block |
| Tech | mechanical: wrist tap, servo lock | 2 ticks | lock and recoil | servo jitter | tiny kick, wrist glint | the device on the wrist |
| Spirit | open: palms meet at the chest, then open | 3-7 ticks, a breath | arms open, rise | float down | warm bloom | light lifts from the sternum |

**Per-power accents** (decision D9): each power's row overrides a few numbers of its family base: which hand, palm up/out/down, stance width, gaze, one
prop gesture. `power_casts.py` already builds a pose from figure terms (`pose(lean, twist, ra, la, rl, ll...)`), so an accent is a one-line `pose(base, ...)`.
Specific moves the families lack and the table needs: `stomp`, `heave` (a throw from the ground), `clap`, `palm_strike`, `core_beam`, `brow`, `device_tap`,
`conjure_low`, `set_down` (about eight new bases).

### 5.4 First person: your body through your eyes

Powers become a Hands source (SPEC §24). Nothing new in Hands is required except where marked.
- **Clips**: `tools/art/specs/fp_casts.py` (same schema as `fp_moves.py`) -> `assets/awaken/fp_clips/fp_casts.json`: `fists: true` clips key each hand's *place on
  screen* (so a palm can be at the sternum, then out along the aim) instead of offsetting vanilla's arm; `events` (`gather`, `release`, `plant`) call
  `FirstPerson.listen`, which spawns the effect, plays the sound and fires the camera moment **on the clip's own frame**.
- **Glow**: `View.glow` / `Rig.glow` (hands lit from within; already in the API) rises with the gather and holds through a channel.
- **Legs for the stomp** (new world source, priority above movement and below combat): a `FirstPerson.world` source fills `Rig` `RIGHT_LEG` with
  `reach(...)` to the ground plant; the boot rises into the lower frame edge (the frame shows y down to about 0.7 of the distance), hangs, and drives
  down. A real stomp is out of frame (the foot is 75 degrees below the eye), so this is a stylised beat, in the spirit of an FPS kick; off with reduced
  motion; decision D5. When you look down, the real leg shows through the same source.
- **The beam**: the main fist key sits at about view (0.30, -0.20, -0.95), the off hand rises under the wrist as a brace; the palm is the cast point and
  the beam starts there. The core's glow shows as light on the forearm and the lower frame (no sprite needed).
- **Camera in first person**: the Feel Director's pitch spring (landings use it) for the thump; `BodyCamera`-style dips are already comfort-scaled.
- Settings: `firstPersonMoves` and reduced motion already apply.

### 5.5 Third person, the shoulder view and other players

- **Per-power PAL clips** from `power_casts.py`: family base + the accent, with bent twins; `CastClips.clip(power, beat)` looks up the profile first and falls
  back to the family (so nothing breaks while the table fills). New bases per 5.3. The checker (arm through body) already runs on every clip.
- **Channels get a real hold and release**: `beam_gather` -> `beam_hold` (loop with a tremble tied to the drain) -> `beam_end`; the beam's events carry the
  profile so the release plays (fixing cause 5).
- **The cast point is sampled from the posed model** (5.2), so a beam, a bolt, a palm print or a tether starts at the palm that is drawn.
- **Without PAL**: improved vanilla poses (a stomp is a crouch + lean + camera + a sound, there is no leg pose in vanilla's arm poses).
- **Priority**: the body's moves layer (slide, roll, mantle; priority 1600) still wins over a cast (1500), as now.

### 5.6 The impact: the thump kit

One client class, `CastFeel`, replaces the three paths for powers: a *moment* (`thump_heavy`, `thump_light`, `recoil_light`, `release_big`, `ult`) is a
small record (pitch/yaw/roll kick, FOV push, hold ms, flash, bloom, dust) played through what already exists: `CombatFeedback.kick/hold/hitstop`,
the Feel Director's spring, `ArmsFeel`'s FOV moments, `FxScreen`'s flash. One comfort path (`camera shake`, reduced motion, Arms' `viewKick`).
- **The thump** (Kinetic's signature): a view dip of 2.4 degrees in 2 ticks and a rebound over 5, FOV -2 to -4 percent, the caster's hands and view held
  for the hit-stop (today only the victim freezes), a decal and dust ring **at the planted foot**, a small foot-shaped puff, debris, a low rumble tail.
- **Sound, layered to the frame** (`tools/art/specs/audio_powers.py`, using `audio_layers.py` and `audio_rig.py`): four layers per moment: *foley* on the
  anticipation (cloth, a boot lifting), *impact* on the release (a sub thud plus a crack for Kinetic, a rising chime for Spirit), *body* (the family's timbre),
  *tail* (air, rumble, a ring). Each family has a default kit; each power names one. Played **on each client at the muzzle on the release frame**, so the
  server stops sending the vanilla sound packet (fewer packets) and everyone hears the sound where the effect is, on the frame it lands.
- **In the shoulder view** the action camera's arm drops on a thump (as it does for a landing) so the thump reads in third person too.

### 5.7 Holding and channelling a beam

Stance: braced, lean 10 degrees, the off hand steadying the main wrist (`proof/beam_after.png`). The loop trembles; the tremble grows slowly with the drain and
eases at the last second. The light: the hands glow, the core glow is a soft sprite on the sternum in third person, a line of light runs sternum to palm
(the Energy signature), and a Lens bloom pulse plus a small `awaken:cast_beam` look (D8). Start: a micro-recoil (pitch -0.6) and a muzzle flare; end: the arms
drop, sparks, the light bleeds out of the palm.

### 5.8 How a cast reads in multiplayer

- The server's `FxEvent` keeps carrying caster, profile id, seed and aim; one event begins a cast that has a wind-up (about 14 powers, ~50 bytes) and the
  release recipes follow as today. Others see the anticipation (a stomp's knee comes up 4 ticks before the wave: a tell, good for PvP readability).
- Every client puts the effect at *its own* drawn muzzle, on *its own* clip's release tick, from the shared seed. Positions never go over the wire.
- Sound moves to the clients (positional, at the muzzle). Hit-stop on the victim and camera stay as now (server `Feel`). Camera shake from other players'
  casts keeps today's radius rule (`Fx.quake`).
- A culled or distant caster falls back to the baked point track: the effect still starts at their body.
- Range and budgets stay: `Fx.RANGE` 72, `FxBudget`, the particle setting.

### 5.9 Performance and the network

- Hot path: `Muzzles` is a fixed pool (48 slots like `CastAnims.SLOTS`, 9 points, 3 doubles each); the sampling layer runs only for entities with a live cast; the first-person
  key lookup is an array index. No per-tick allocation.
- Network: no new payloads. `FxEvent` keeps its size (profile id replaces the pose ordinal); one extra event for the wind-up powers; the vanilla sound packet is dropped.
  The wind-up uses the existing `Scheduler.after` on the server.
- Assets: `power_casts.json` is 208 KB today; per-power clips with bent twins are an estimated 500 to 700 KB (loaded once). Sounds are short OGGs (about 3 to 8 per family
  to start).
- Cost to check at build: the sampling layer's frame time with 20 casters (bench), and the one-frame order of the sample against `SubmitCustomGeometryEvent`
  (a one-frame lag is invisible; I have not verified the order).

### 5.10 Reuse: what each piece covers and where the gap is

| Piece | Covers | Gap for casts |
|---|---|---|
| Hands (§24): `FirstPerson` view stances, `fists`, `grips`, world `Rig` with legs, `glow`, clip `events`, the lens factor | everything first person needs | no power source; no foot-plant helper; nothing keys effects from clip events for powers yet |
| Arms (§25): `anim_lab.py` (onion-skin sheets, `third`, `audit`), `FpSwings`, `arms_rig4.py`, `CombatFeedback` (`kick`, `hold`, `hitstop`), `ArmsFeel` (FOV moments, Lens breath), `BladeTraceLayer` | the method for baked, audited motion; one feel; sampling the drawn arm | weapon-centred (hands and blades, no legs); `arms_rig4` is a weapon rig, so empty-hand and leg casts use `power_casts.py`'s figure poses instead |
| `power_casts.py` (figure-term poses, bent twins, forward kinematics, clipping check, previews) | all third-person authoring; the proof here | family-level only; no point-track export; no per-power rows |
| `FxRecipes`, `FxWorld`, seeds, budgets, decals, debris, dust | the look of the effects; multiplayer-identical builds from a seed | anchors are constants; no Lens link |
| §17.13 action camera + Feel Director | the shoulder view's arm drops; pitch spring; FOV | no cast poses; powers do not call it |
| Lens (§22): `override`, `placeLook`, `ritual`, `motion`, glow bloom | whole-screen looks and a bloom boost | no cast pulse or light; needs a small addition in `lens/` (D8) |
| Sound kit: `audio_layers.py`, `audio_rig.py`, `audio_body.py`, `audio_arms.py` | authored layered sound; a tooling path to OGG | no power script; `PowerSounds` are vanilla remixes |

## 6. Decisions (recommended answer first)

| # | Question | Recommended | Why |
|---|---|---|---|
| D1 | Do the whole system for all 59, or only the beam and the stomp? | **System, proof first**: beam and stomp end to end, then your look, then the families | the two you named share every cause with the other 57; fixing only them leaves the rest the same |
| D2 | Where does the beam come out? | **The palm of the aimed arm, fed from the chest core** (light runs sternum to palm); **Prism Lance** is the one that leaves the chest core with both arms wide | "the core place" is the sternum; a beam must still visibly leave a hand to read as thrown; two beams, two bodies |
| D3 | Real wind-up (a short cast time) for heavy powers? | **Yes, 3 to 6 ticks for 14 powers** (Shockwave, Spire, Frost Nova, Terrain Rip, Thunderclap, Brace, Gravity Flip, Collapse, Rampart, Legion, Citadel, Drone Swarm, Void Maw, Storm Call); none for movement, toggles or instants. Your key press starts the pose at once | the plant must come before the wave; cost and cooldown still start on the press; at most 250 ms; it is how Arms' heavy rows already work |
| D4 | For the other powers, should the effect wait for the body's release frame? | **Yes, visually only, on your client** (no gameplay delay, 1 to 4 ticks); the hit-stop and camera move with it | closes cause 4 without touching balance |
| D5 | A boot in first person for the stomp? | **Yes, stylised**: the boot rises into the lower frame edge and drives down; off with reduced motion | without it the stomp is only a camera dip and a hand drop; with it you see your foot do it |
| D6 | One camera director for powers? | **Yes**: `CastFeel` over `CombatFeedback`, the Feel Director and `FxScreen`; keep the existing settings | removes the three-path mismatch; comfort works everywhere |
| D7 | Authored layered sound instead of vanilla remixes? | **Yes, signature moments first** (stomp, slam, beam loop, nova, lightning, blink), then a kit per family; played on clients at the muzzle | sound is half of "a thump"; it is also where "vanilla add-on" shows most |
| D8 | Lens light for casts? | **Yes, small**: a bloom pulse for every cast and about 8 looks (ultimates, Beam hold, Flow, Foresight); Lens off falls back to today's flash | the light on the hands and the time-slow cue are what is missing; touches `lens/` so I will brief its owner |
| D9 | Per-power accents or one pose per family? | **Accents for all 59** (a hand, a stance, one beat), generated from the family base | "every power its own look"; about 3 more hours than family-only |
| D10 | Stay with PAL and Bendable Cuboids as the third-person path? | **Yes**; vanilla poses stay as the improved fallback | already in the pack; the bent twins fix elbows and knees |
| D11 | Left-handed players | **Mirror, as now** (everything is authored right-handed) | already how the pack works |
| D12 | PvP tells: anticipation visible to others | **Yes**, at least 3 ticks for heavy casts | readability; no gameplay effect in PvE |
| D13 | Comfort setting | **One "Cast animation: Full / Reduced / Off"** that halves or removes the dip, the boot, the tremble | fits the body's reduced motion; replaces the single `animations` flag |
| D14 | Build gate | **Euan's OK on the proof's shots before the families start** | the look is the risk, not the code |

## 7. Build order and estimate

At the pace git history shows: Hands P1-P6 (the whole first-person system) went in over about 2 hours of commits on 25 Oct plus a quality pass at 20:09; Arms
(three passes, 20 weapons) took about 35 hours of wall-clock with other work in parallel. Casts are closer to Hands in kind. About **20 hours of builder time**, less with
two builders in parallel (sound and families).

| Phase | What | Hours | Gate |
|---|---|---|---|
| **P0 proof** | `Muzzles` + `MuzzleLayer`; profile table skeleton + GameTest; the **beam** (palm + core, 1P fists, 3P gather/hold/end, tremble, light) and the **stomp** (3P stomp clip, 1P legs source + dip, 4-tick wind-up, plant at the foot, thump kit, layered sound, decal and dust); one CI tour each | 4-5 | Euan's look |
| P1 system | all profiles load; wind-up scheduler; release on the clip's frame (D4); `CastFeel` over the three paths; sound-at-muzzle; settings | 4 | tests |
| P2 Kinetic + Energy | slam, leap, haymaker, terrain rip, brace, thunderclap; nova, barrier, charged shot, overload, Prism Lance | 4 | tour |
| P3 Velocity, Flight, Shadow, Mind | dashes with each family's own body; hover/flight; gravity; shadow; mind | 3 | tour |
| P4 Architect, Tech, Spirit | 28 powers | 4 | tour |
| P5 sound (parallel from P0) | `audio_powers.py`: kits for all nine families and the signature moments | 3 | listening session |
| P6 Lens + multiplayer + perf | Lens looks and pulse; two-client check; sampling-layer bench; comfort setting | 2 | bench, CI |

Per-hand-back: one CI tour (`tools/tour-ci.sh`), the full GameTest suite once at the end, never the client locally.

## 8. Risks and what I did not verify

- Scores are from reading code, not from playing (no client was run). The first-person numbers assume FOV 70 and 16:9.
- Whether the sampling layer's pose is available before the effect shapes draw in the same frame: `BladeTraceLayer` shows the pattern works; the order against
  `SubmitCustomGeometryEvent` is unverified (worst case: a one-frame lag).
- `RenderLayer` sampling of an arm that PAL has posed is proven for blades in this repo; the torso and leg samples use the same call (`translateToHand` is vanilla's
  `ArmedModel`; the torso and legs are model parts) but are not yet exercised.
- The first-person boot's frame bounds come from SPEC §24 ("y down to about 0.7 of |z|"); the shots must confirm.
- D3 changes feel: a 4-tick wait on 14 powers. If Euan dislikes it the fallback is the visual-only release (D4) for them.
- Lens changes live in another module (`lens/`).

## 9. The proof in this branch

`docs/power-anims/proof/baked_poses.py` uses `tools/art/specs/power_casts.py` as a library (its `Clip`, `bake`, forward kinematics and clipping check) and writes
nothing the game reads. It shows that the rig can say a stomp (knee to hip height, hang, plant, rebound; legs and arms bent; no clipping at all) and a beam through the core
(gather at the sternum, arm along the aim, off hand at the wrist; worst clip 0.62 px, under the production limit of 1.25) with the cast points marked. Re-run:
`python3 docs/power-anims/proof/baked_poses.py OUT_DIR`. The audit data: `python3 docs/power-anims/proof/audit.py`. Files: `stomp_before.png`, `stomp_after.png`,
`beam_before.png`, `beam_after.png`, `numbers.json`.

## Appendix: APIs I checked in the real sources

- `ArmedModel.translateToHand(state, arm, poseStack)`, `ItemInHandLayer.submit` (`.ref/src/net/minecraft/client/renderer/entity/layers/ItemInHandLayer.java`);
  `RenderLayer` added with `AvatarRenderer.addLayer` (`arms.client.BladeTraceLayer`).
- `core.api.FirstPerson` (`view`, `world`, `Rig.limb/reach`, `View.fist*`, `glow`, `listen`, `play`), `core.api.CombatFeedback` (`kick`, `hold`, `hitstop`),
  `lens.client.LensClient` (`override`, `clearOverride`, `placeLook`, `ritual`, `motion`, `active`).
- `powers.fx.CastClips.clip` fall-through, `powers.client.fx.Anchors.hand`, `CastAnims.offset`/`modify`, `FxRecipes.beam`/`slam`/`cone`, `EnergyPowers.beam`/`hand`,
  `KineticPowers.shockwave`, `Fx.beam`/`quake`/`feel`, `PowerSounds` and `merge/powers/assets/awaken/sounds.json`.

## 10. Build log

**2026-10-27: Euan approved the plan in full ("Go with all recommendations"); P0 built on this branch (SPEC §31 has the as-built list).**
Changes from the plan as written, found while building:
- The stamp's plant is at tick 5 (250 ms), not 6 (300 ms), so the wind-up fits the 250 ms cap (D3); the beam's arm reaches the aim at tick 5 (the plan's sheet showed 7).
- The wind-up runs as a `Running` (key `shockwave_windup`) rather than `Scheduler.after`, so the existing per-tick GameTests drive it; `power_fx_reach_matches_hits` ticks 17 for the stamp (5 + 12).
- The first-person palm is only a profile cast's fist (a sprint's fist is not where a charge starts); everything else HAND-anchored still starts at the drawn palm in third person.
- Sounds ship with subtitles (the audio test requires them).
- First tour (run 40457072925) toured the opening's balcony camera, not the platform: the script lacked `opening skip`. Fixed and re-run.

**2026-10-27: Euan looked at the proof ("The proof looks right"); P1-P6 built on this branch (SPEC §31, "P1-P6 as built").**
- 54 profiles from one table (`tools/art/specs/power_cast_table.py` -> `data/awaken/powers/casts.json`); ground_slam, meteor_dive, hover, flight
  and leap stay on their family poses (movement already owns their bodies).
- The 14 heavy powers' wind-ups run generically in `Casting.windUp`; every client holds a caster's events back to the clips' release tick.
- 43 authored sound events (`audio_power_kits.py`, 115 files) replace the vanilla remixes under `power.*`.
- Families tour (run 40465447138, `tools/autopilot/power-casts-2.txt`, slow motion): gestures read and effects land after them (dominate's
  halo after the brow point, prism lance from the core, void maw's dark look, flow's vignette). Found: release_big's flash doubled the nova and
  prism recipes' own flash, so it went 0.10 -> 0.05; Wall's constructs block the front camera for the shots after it (tour order only).
- Not yet seen or heard: the sound kits (no audio on CI), most first-person gestures beyond the six toured, other players' view of a cast.
