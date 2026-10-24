# Power FX (`dev.awaken.powers.fx`, `dev.awaken.powers.client.fx`)

The owner's verdict on build 1: the powers "look very vanilla and almost like add-ons". This module replaces the
server-side vanilla particle spam with a client VFX system: the server sends one small event per effect, every client
builds the same effect from it (same seed, same shape), with our own sprites, meshes, trails, debris, afterimages,
screen feel and cast animations.

## 1. Visual language

Every effect is built in five beats: **wind-up** (anticipation, energy gathers, the body loads), **release** (a flash,
the body snaps, a shape leaves the hand), **travel** (trail, beam, wave), **impact** (flare, debris, hit-stop, kick)
and **linger** (decal, smoke, sparks settling). A power's level makes each beat bigger; ultimates get a screen beat
(impact frame and FOV punch) and twice the linger.

Pixel rules: sprites are 8, 16 or 32 px, hard-edged, banded falloff (3-4 bands, no smooth gradients), frame-animated
at 2-3 ticks a frame, drawn white/grey so one sheet serves every family through the vertex tint. Glow is additive and
full-bright, so effects read at night and stay legible by day thanks to a dark or saturated **edge** colour in every
palette (pure additive white disappears against a bright sky).

| Family | Shape language | Palette (core / mid / edge / accent) | Signature layers |
|---|---|---|---|
| Kinetic | low, wide, heavy, horizontal | `#FFF1D6` / `#F2A541` / `#B26A12` / dust `#9A8A72` | ground shockwave ring mesh, crack decal, block-textured debris chunks, dust billows, amber flare, long hit-stop, heavy camera kick |
| Velocity | long thin streaks, motion-aligned | `#FFFFFF` / `#FF4A3D` / `#A8262F` / `#FF8A7A` | speed lines, red-tinted afterimages of the player, wind ribbon trail, sonic ring at dash start, FOV punch |
| Flight | soft curves, rising | `#E6F6FF` / `#A7CDE3` / `#6B98B8` / cloud `#DCDFE8` | white wind ribbons from hands/feet, curved wind sprites, cloud puffs |
| Gravity | contracting, orbiting, inward | `#E0D0FF` / `#9A6BFF` / `#4A2A9A` / void `#0D0A17` | contracting violet rings, dark lensing core with bright rim, orbiting debris, upside-down dust |
| Energy | round orbs, straight lines, bloom | `#FFFFFF` / `#7FC3FF` / `#3C86CC` / heat `#FF9A3C` | charge orb at the hand with inward sparks, 3-layer beams (white core, blue body, wide faint halo), crackle bolts, expanding glow sphere, shield bubble with rim light |
| Shadow | jagged, angular, sharp edges | ink `#0D0A17` / `#8F6BFF` / `#231A3B` / `#C3B0FF` | inky alpha smoke (dark, not additive), violet rim sparks, tendrils from the ground, crescent slashes, shadow pool decal |
| Mind | concentric, rippling, symmetric | `#FFE6F6` / `#D088C2` / `#73367C` / gold `#FFD27A` | psionic ripple rings from the head, floating runes, third-eye glyph, shimmering distortion ring |
| Architect | grids and right angles | seam `#FFD28A` / `#D9BA80` / `#937646` / blueprint `#7FC3FF` | blocks assembling with glowing seams (wireframe edges flash, then solidify), blueprint grid lines on the ground, construction sparks running along edges |
| Tech | precise, thin, digital | `#E6FFFA` / `#47C6B0` / `#17564E` / `#7FC3FF` | holographic hex grids, scanline reticles, square pixel motes, thin laser lines, jet exhaust with blue core |
| Spirit | soft, translucent, echoing | `#F0FFF5` / `#86E0A0` / `#3F8F6E` / soul `#9FB0FF` | translucent wisps, palm print shockwave, delayed echo of every impact, lotus heal ring, soul flames |

Forces override the accent: heat `#FF9A3C`/`#C44A12` embers, charge `#F0FAFF`/`#9AD8FF` crackle, frost
`#E6F6FF`/`#A7CDE3` shards, gravity violet, spirit soul blue.

## 2. Sprites (particle atlas, `textures/particle/power_fx_*.png`)

Additive sprites are drawn on black: brightness is intensity, alpha is 255 wherever a pixel is lit (the additive render
type cuts out alpha < 0.1). Alpha sprites (smoke, dust, decals) carry coverage in alpha and a light grey value ramp.

| Sprite | Size × frames | Kind | Use |
|---|---|---|---|
| `glow` | 16 × 1 | add | soft orb, bloom halos, charge cores |
| `spark` | 16 × 2 | add | vertical streak, stretched along velocity |
| `flare` | 16 × 4 | add | 4-point impact star, shrinking |
| `bolt` | 16 × 4 | add | electric crackle |
| `wisp` | 16 × 4 | add | spirit wisp curl |
| `ember` | 8 × 4 | add | heat embers |
| `ring` | 16 × 1 | add | thin ripple ring |
| `rune` | 16 × 4 | add | angular sigils (architect, mind, tech) |
| `hex` | 16 × 1 | add | hex cell (tech) |
| `mote` | 8 × 2 | add | square pixel mote (tech, heal) |
| `shard` | 8 × 4 | add/alpha | triangular shards (frost, glass, shadow) |
| `slash` | 32 × 3 | add | crescent slash arc |
| `palm` | 32 × 1 | add | open palm print (Spirit Palm) |
| `wind` | 16 × 3 | add | curved wind streak (flight) |
| `glyph_circle` | 32 × 1 | add | magic circle decal |
| `halo` | 64 × 1 | add | smooth radial gradient for big halos (not pixel art: it is scaled across the view) |
| `smoke` | 16 × 6 | alpha | puff breaking up (shadow ink, generic) |
| `dust` | 16 × 6 | alpha | ground dust billow |
| `decal_crack` | 32 × 1 | alpha | radial ground cracks |
| `decal_scorch` | 32 × 1 | alpha | burn mark |
| `decal_pool` | 32 × 1 | alpha | inky shadow pool |

Generated by `tools/art/specs/power_fx.py` (`python3 tools/art/build_all.py power_fx`); its `preview()`
(`python3 tools/art/specs/power_fx.py preview build/fx-preview`) writes contact sheets of every family's tinted sprites
and key frames of each family's signature effect on a night and a daylight background (`sprites_<family>.png`,
`effects_<family>.png`, `effects_ultimate.png`).

## 3. Every power, beat by beat

`dev.awaken.powers.fx.PowerFxDefs` is the table (a GameTest checks it covers all 63 powers): the colour style, the body
animation (and the held wind-up pose of channels), and the recipe of each beat. Behaviours in `catalog/*Powers` send
the recipes; the client uses the table to pose and wind up the instant a key goes down.

| Power | Style | Animation | Wind-up | Release | Travel | Impact | Linger |
|---|---|---|---|---|---|---|---|
| `ground_slam` | kinetic | slam / hold slam_wind | trail | slam | - | slam | smoke |
| `leap` | kinetic | dash | - | dash | trail | slam | - |
| `haymaker` | kinetic | punch / hold punch_wind | charge | impact | - | impact | smoke |
| `terrain_rip` | kinetic | throw | - | slam | trail | impact | smoke |
| `shockwave` | kinetic | stomp | - | cone | cone | impact | smoke |
| `brace` | kinetic | brace | - | ring | - | ring | aura |
| `thunderclap` | charge | clap | - | strike | lightning | impact | ring |
| `dash` | velocity | dash | - | dash | trail | impact | afterimage |
| `flow` | velocity | stomp | - | ring | - | - | aura |
| `afterimage` | velocity | dash | - | afterimage | trail | smoke | aura |
| `wall_run` | velocity | none | - | trail | - | - | - |
| `water_run` | velocity | none | - | trail | - | - | - |
| `lightning_step` | charge | dash | - | blink | lightning | impact | afterimage |
| `hover` | flight | hover | - | ring | trail | - | aura |
| `flight` | flight | skyward | - | ring | trail | - | aura |
| `gravity_well` | gravity | aim | - | vortex | - | nova | vortex |
| `telekinesis` | gravity | throw / hold aim_hold | lift | tether | trail | impact | - |
| `meteor_dive` | gravity | slam / hold skyward | trail | ring | meteor | slam | smoke |
| `gravity_flip` | gravity | skyward | - | ring | lift | impact | - |
| `beam` | heat | aim / hold aim_hold | charge | beam | beam | impact | smoke |
| `nova` | heat | skyward / hold charge2 | charge | nova | - | impact | ring |
| `barrier` | energy | push2 | charge | dome | - | nova | dome |
| `charged_shot` | charge | aim / hold aim_hold | charge | bolt | bolt | impact | - |
| `overload` (ult) | energy | skyward / hold charge2 | charge | nova | lightning | impact | aura |
| `prism_lance` (ult) | prism | aim / hold aim_hold | charge | beam | beam | impact | - |
| `shadow_step` | shadow | dash | - | blink | - | smoke | aura |
| `cloak` | shadow | conjure | - | smoke | - | - | aura |
| `echo_swap` | shadow | clap | - | blink | - | smoke | - |
| `legion` (ult) | shadow | conjure | glyph | summon | - | - | glyph |
| `danger_sense` | mind | none | - | psi | - | - | - |
| `dominate` | mind | temple | - | psi | psi | aura | - |
| `read_intent` | mind | temple | - | psi | - | - | aura |
| `decoy` | mind | throw | - | psi | trail | afterimage | - |
| `foresight` | mind | temple | - | ring | - | - | aura |
| `wall` | architect | push2 | - | construct | - | - | glyph |
| `ramp` | architect | conjure | - | construct | - | - | glyph |
| `floor` | architect | conjure | - | construct | - | - | glyph |
| `cone` | architect | raise | - | construct | - | - | glyph |
| `rampart` | architect | skyward | - | construct | - | - | glyph |
| `spire` | architect | stomp | - | pillar | construct | impact | - |
| `collapse` | architect | throw | - | construct | trail | impact | - |
| `citadel` (ult) | architect | skyward | glyph | dome | construct | - | glyph |
| `grapple` | tech | aim | - | tether | - | impact | - |
| `jet_burst` | tech | dash | - | exhaust | trail | - | smoke |
| `drone` | tech | raise | - | holo | - | - | - |
| `turret` | tech | conjure | - | holo | - | - | - |
| `railgun` | tech | aim / hold aim_hold | charge | rail | - | impact | smoke |
| `nanite_skin` | tech | brace | - | aura | - | - | aura |
| `drone_swarm` (ult) | tech | skyward | holo | holo | - | - | - |
| `aura` | spirit | none | - | aura | - | impact | - |
| `spirit_palm` | spirit | palm | - | palm | palm | impact | echo |
| `mend` | spirit | clap | - | heal | - | - | heal |
| `third_eye` | soul | temple | - | psi | - | - | aura |
| `cord_lash` | soul | throw | - | tether | - | impact | - |
| `bulwark` | spirit | skyward | - | dome | - | - | dome |
| `hundred_palms` (ult) | spirit | flurry | - | flurry | - | impact | echo |
| `void_maw` (ult) | gravity | aim | - | vortex | - | impact | vortex |
| `waking_dream` | mind | conjure | - | construct | - | - | glyph |
| `storm_call` | charge | raise | - | strike | - | impact | - |
| `ember_step` | heat | dash | - | dash | fire_trail | - | fire_trail |
| `frost_nova` | frost | stomp | - | frost | - | impact | - |
| `ghost_step` | soul | dash | - | blink | - | impact | - |
| `twin_strike` | soul | clap | - | aura | - | echo | aura |

Notes on the starters and Dash, which every player sees first:
- **Haymaker**: the fist cocks back over the shoulder (crouched, body twisted away, trembling) while an amber orb gathers
  at the knuckles with dust and sparks pulled in; a ring snaps round it at full charge. Release: body twists into a
  straight punch, the orb pops, a flare and a ring of force punch out of the target, sparks fly along the hit, hit-stop
  and a camera kick scaled to the charge. A whiff still throws the ring into the air.
- **Shockwave**: a stamp (lean and drop), a curved upright sheet of amber force rolls out exactly as far and as wide as
  the hit cone, tearing dust and the ground's own block chunks up along its front; a crack decal where you stamped.
- **Charged Shot**: arm out along the aim, a crackling blue orb at the palm (bolt sprites pulled in), released into a
  white-cored streak drawn every tick of the bolt's flight with a glowing head; a crackle burst where it lands.
- **Beam** (heat): palm out, an orb at the hand, then a three-layer beam (wide faint halo, orange body, white core)
  that breathes, with glows at both ends, embers and scorch marks where it touches and live aim on the caster's client.
- **Spirit Palm**: a palm thrust; a translucent jade palm print flies from the hand with a ring travelling with it,
  rings punch through the target, and a second later a blue echo ring and palm print hit again.
- **Shadow Step**: lean, vanish in ink smoke (dark puffs with violet rims and glints), tendrils and a shadow pool where
  you stood, reappear behind the target with a crescent slash and a rune over its head while the backstab is ready.
- **Dash**: lean hard into the motion; a sonic ring at the start, speed lines streaming back, red ribbons from body
  and feet, red-tinted afterimages of your pose every couple of ticks, an FOV punch and screen speed lines for you.

### Round 2 (after the first in-game tour)

The tour (third person, camera behind the caster, target straight ahead) showed what reads and what doesn't:
- Anything that travels along the aim (beams, bolts, the palm print) is hidden behind the caster's own body from that
  camera, and short effects (5-10 ticks) were gone before the first shot. Fixes: effects that matter now outlast a
  second and are bigger than the caster's silhouette: Spirit Palm's print flies three blocks past the target, with a
  2.5-block spirit ring and a ground ring on impact and a blooming echo a second later; beams carry rings riding down
  their length (a tunnel of light around the line of fire when seen from behind) plus shock rings at both ends;
  Charged Shot gets a muzzle burst, a tracer that piles up and a landing burst where the aim meets something.
- Shockwave: the wall of force is 2.3 blocks tall in the family colour, and a crescent of dust is thrown up along the
  whole front and left standing for two seconds, with chunks and cracks along the path.
- Nova: the shell holds for a second with its equator drawn as a ring. Storm Call: a dark storm knot overhead, a
  longer re-flickering bolt, crackle crawling over the ground, and the vanilla sky flash and thunder.
- Daylight: the under-copy is stronger (58% opacity at 62% brightness), and bodies (rings, sparks) are drawn in the
  family colour rather than white, because white additive light disappears against a pale sky.
- Beams never depended on the key being held: they are built from the server's events, so other players and
  `/awaken admin power cast` show them too (the caster's own client follows its live aim).

### Round 3 (first-person polish)

- Nothing glows within 1.5 blocks of the camera and everything is at full strength from 4 (`FxRender.near`); a
  camera-facing sprite never grows past 0.45 x its distance (about 25 degrees of view). Mesh vertices fade the same
  way, except a beam's thin body and core, which leave your hand visibly. The caster's own charge orb in first person
  stays a small light in the hand (capped at 0.2 x distance). Your own afterimages fade when they stand on the camera.
- Big halos use a smooth 64 px radial gradient (`power_fx_halo`); pixel-art bands are for small sprites. Soft glows
  get no daylight under-copy (that copy was what drew the blocky translucent squares).
- Nova: the shell grows in eight ticks and holds a second, its silhouette drawn as a bold band facing the camera;
  from inside (the caster) the shell reads as a wall of light round the horizon, with two more rings of latitude and a
  flash across the ground.

## 4. How it works

- **Server** (`powers/Fx.java`): `Fx.at(cast, FxKind, pos).scale(..).send()` builds one `FxEvent` (kind, style, caster,
  target, position, direction or end point, colour, scale, aux, seed, ticks, flags with variant, power level and the
  caster's animation) and sends it as `awaken:power_fx` (`powers/net/FxPayloads`, ~50 bytes) to players within 72 blocks
  of the effect, its end point or its caster. The power's release recipe also starts the cast animation, its wind-up
  recipe the held wind-up pose, and an ultimate's release also sends the screen beat. `Fx.end(cast, entity, what)` ends
  a charge (and the held pose), an aura or a dome. Older helpers (`Fx.beam`, `Fx.streak` used by `Bolts`,
  `Fx.lightning`, `Fx.impact`) now send events too.
- **Client** (`powers/client/fx`): `FxClient` wires events; `FxRecipes` builds each kind from the seed
  (deterministic, so every viewer sees the same shape); `FxWorld` owns the sprite pool (`powers/fx/FxPool`, parallel
  arrays, no objects per sprite) and 192 reusable `Shape`s (rings, spheres, beams, trails, bolts, waves, columns, cords,
  seams, glyphs, discs, hex shells, emitters); `FxDebris` throws cubes wearing the hit block's texture; `FxAfterimages`
  freezes a player's render state and redraws it tinted and glowing; `FxRender` draws in ordered batches: a daylight
  underlay (a darker translucent copy of every glow, so colour reads against a bright sky), lit smoke/dust/decals,
  additive light (vanilla `lightning` render type for meshes, `energy_swirl` on the particle atlas for sprites), then
  dark ink on top. `FxScreen` adds a coloured flash, impact frames (`post_effect/power_fx_impact_0..3`, swapped tick by
  tick because post uniforms are fixed per effect), FOV punches, directional camera kicks and screen speed lines.
- **Cast animations** (`CastAnims`, no mixins): a render-state modifier swaps vanilla arm poses into a cast (the horn
  pose is a straight punch, the bow pose two palms out, the trident pose an arm cocked overhead, the shield pose a
  guard, the spyglass pose fingers to the temple), twists the body (body yaw with the head kept on target) and a player
  render event leans the whole figure; the animation rides on the render state (NeoForge render data) so the lean
  finds it. First person: `RenderHandEvent` moves the drawn arm, and for two-handed casts draws the off arm too with the
  vanilla arm transform. Held wind-ups tremble until released; hit-stop pauses the caster's pose with the target.
  A custom `ArmPose` (NeoForge enum extension with an `IArmPoseTransformer`) would allow fully keyframed arms, but needs
  an entry in the shared `META-INF/enumextensions.json`; the vanilla poses were enough for now.
- **Full-body casts (optional: Player Animation Library 1.2.8, mod id `player_animation_library`, in the pack's `feel`
  profile)**. When the library is loaded, third person plays a keyframed clip per power family instead of the vanilla
  arm poses and lean; without it everything above runs unchanged. Only `PalCasts` imports the library, and only
  `CastAnims` calls it, behind `ModList.isLoaded` (the library is `compileOnly`, never bundled or required). Each
  client-side player gets one Awaken layer (priority 1500, a mirror for a left main arm); `CastAnims.play` starts the
  clip `CastClips` names from the same `awaken:power_fx` events and prediction that start the vanilla pose (no new
  packets, same timing): the family comes from the event's style and pose (`CastClips.family`). First person is not
  touched (the library's first-person mode stays off; `CastAnims` still draws the arms). Hit-stop pauses the clip; a
  held wind-up lets go two ticks after release unless the release cast takes over with its clip's release half.
  Clips (`assets/awaken/player_animations/power_casts.json`) are generated by `tools/art/specs/power_casts.py` from
  poses written in figure terms (lean, twist, rise, aimed arms that follow the look pitch, legs that stay planted,
  feet put on the ground); its `preview` draws frame strips and a nine-family sheet, its `check` fails on an arm
  inside the head, torso or legs.

  | family | cast (wind-up, release, follow-through) | ticks | held wind-up |
  |---|---|---|---|
  | Kinetic | wide stance, chest coils away, hips and chest snap round, straight fist at the target, off fist chambered | 10 | punch loaded, trembling |
  | Velocity | crouch, then a hard forward lean with both arms swept back and a long stride (every dash plays this) | 10 | crouched to go |
  | Flight & Gravity | hands gather low at the off hip, sweep diagonally up past the main shoulder, the body lifts and arches | 15 | a hand up holding something aloft |
  | Energy | hands gather the charge cupped at the main hip, chest coiled, then both palms thrust out along the aim from a braced lunge | 12 | power between the palms at the hip |
  | Shadow | drop into a low wide crouch, hand drawn to the off shoulder, then a wide sweep at waist height | 13 | coiled low |
  | Mind | two fingers to the temple, head tipped to the hand, then a straight point at the target | 15 | concentrating |
  | Architect | hands draw a frame in the air (together, apart, down the sides), then set it with both palms | 14 | the frame held open |
  | Tech | off hand taps the wrist device, side-on lock-on, recoil kick, settle | 12 | locked on, servo jitter |
  | Spirit | palms together at the chest, then an open-armed rise | 16 | palms together, breathing |

  Poses the power does to the body look the same in every family: a dash (the Velocity burst), hover (a slow bob),
  slam (both arms up, held; then fists into the ground) and the flurry (palms in turn, 40 ticks).
- **Reach**: events flagged `FxEvent.REACH` draw the power's real hit volume (`scale` = the radius or cone range the
  damage code used, `aux` = a cone's half angle or a line's width). The catalog passes the same locals to both;
  `power_fx_reach_matches_hits` checks rings and cones against where mobs actually get hit.

## 5. Settings and budgets

Client config `config/awaken-power-fx-client.toml` (also in the mod's config screen): `powerEffects` FULL / REDUCED /
MINIMAL, `castAnimations`, `screenFlashes`. Camera kicks and FOV punches follow the core `cameraShake` setting.

| | FULL | REDUCED | MINIMAL |
|---|---|---|---|
| sprite density | 1 | 0.45 | 0.15 |
| live sprites (all effects) | 3072 | 1536 | 512 |
| live meshes | 192 | 128 | 64 |
| debris chunks | 160 | 64 | 0 |
| afterimages | 12 | 4 | 0 |
| screen feel (flash, impact frames) | full | half | off |
| daylight underlay | on | on | off |

The vanilla particle setting multiplies density again (decreased x0.6, minimal x0.3); effects beyond 24 blocks thin
out with distance and beyond 72 blocks are not built at all; one effect never spawns more than 220 sprites. A full
pool reuses the most-spent slot, so a fresh impact always shows. Nothing is allocated per sprite or per frame in the
draw path (sprites and shapes are preallocated, vertices are written straight to the buffer).

## 6. Try it

`/awaken admin power cast <id>` (learns it first), `/awaken admin power reset`. Autopilot (integrator only):
`tools/autopilot/powers-fx.txt` casts every power at a zombie on a sky platform and screenshots each 3-4 times, then
repeats the starters, Dash and ultimates at night. Cast poses are best judged with a hand-pressed F5 run.
`tools/autopilot/powers-fx-body.txt` shoots the full-body clips (one power or more per family, behind and in front):
run it with `PACK=feel` for the clips and plain for the vanilla poses. Offline:
`python3 tools/art/specs/power_casts.py preview DIR` (frame strips, nine-family sheet with ten-block silhouettes).

## 7. Tests (`tools/gradle.sh 1200 runGameTestServer`)

`power_fx_every_power_has_fx` (a definition, style, animation per power; ultimates get the ultimate treatment;
channels a held wind-up), `power_fx_payload_round_trip` (every kind survives the wire, events stay under 64 bytes),
`power_fx_pool_caps` (the pool stops at its cap, a full pool still takes a fresh sprite, lowering the setting trims it,
every sprite dies on time, the per-effect cap), `power_fx_settings_cut_counts` (reduced and minimal cut counts, the
vanilla setting and distance thin them, minimal has no screen feel or afterimages, same seed same effect),
`power_fx_assets` (every sprite's JSON and frames, the impact frames), `power_fx_every_cast_emits` (isolated: every
active power sends at least one designed effect when cast), `power_fx_reach_matches_hits` (isolated: rings and cones
reach exactly as far as they hurt) and `power_fx_cast_clips` (every clip `CastClips` can name is in the generated file
with player bones only, one-shots ease out and wind-ups loop, and every power's events read back as its own family).

## 8. Known gaps

- Nothing here was seen in a running client (the owner's rule). The render code uses only vanilla render types and
  NeoForge events; preview sheets stand in for screenshots until the autopilot runs.
- Without the Player Animation Library, cast poses use vanilla arm poses, so a few casts are approximations (two arms
  raised uses the bow pose with the head tilted up).
- With it, every power in a family shares the family's clip (a Spirit palm strike plays the open-armed rise); legs take
  the clip's stance, so a player running while casting slides for the clip's half second. Other players' clips start
  when their effect arrives, so the wind-up (two or three ticks) is seen just after the effect leaves.
- Danger Sense, Wall Run and Water Run have small effects only (a ripple, a wake); the autopilot skips them.
- Flying blocks get a trail attached to their display entity; Collapse's many blocks get trails on the first ten.

## 9. SPEC-worthy decisions

- Power visuals are client-built from compact events (`awaken:power_fx`); the server sends no particle spam for powers.
- Client settings: `powerEffects` (FULL/REDUCED/MINIMAL), `castAnimations`, `screenFlashes` in their own client config.
- Budgets as in section 5; effects beyond 72 blocks are not built.
- An effect flagged REACH shows the true hit volume; tests hold visuals to the damage code.
- Optional client dependency: Player Animation Library (full-body cast clips per family); without it, the vanilla
  poses. Never required, never bundled; no packets of its own.
