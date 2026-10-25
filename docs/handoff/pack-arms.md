# pack/arms: Arms, the weapons and combat build, hand-off (cloud session, 2026-10-25)

Euan approved the plan (`docs/combat/PLAN.md`, `docs/combat/plan.html`): "Go ahead with the full plan", every §7
decision as recommended. Then: "Do anything you can for the highest quality bar without me since I won't be checking
here." So this session decides and writes every decision down: SPEC §24 holds them as built, the list below is the
short version. Branch `pack/arms` (CI runs the full suite on every push); the session's own branch
`claude/kind-darwin-18uqmv` carries the same commits.

## Built
- **S0, every blow is told and has a class** (`core.api.Strikes`): our creatures' melee winds up 8–10 ticks before it
  swings (big bodies make every third swing a HEAVY), abilities have a class, a mob's held weapon is only a look, a
  broken stance staggers.
- **S1, the three verbs** on every sword: strike (the chain with its rhythm, the held heavy and its glint), guard
  (deflect, block, a HEAVY breaks it, the latency grace), dodge (sprint + back/left/right/none: 5 evade ticks, the
  perfect dodge), the riposte, the Slipcut, the Execution, posture and breaks, Resonance, soft focus. Client-predicted,
  server-judged, one timing table (`data/awaken/arms/archetypes.json`).
- **The feel**: one hit-stop for every blow (`core.api.CombatFeedback`; Powers' hits now use it too), the view's kick,
  the striker's hold; the first-person swings; the HUD's arc, dodge pips and the hurt shard.
- **The sound kit**: 26 events, 76 files, hybrid (CC0 recordings + synthesis), every file inside the `arms` loudness
  window; the impact chosen by what the blade meets (`core.api.BodyMaterial`); one tell voice per class on every
  creature; a body thump when you are hit.
- **Trails and sparks** on the powers module's effects engine: every blow's arc as a crescent facing whoever looks,
  tinted by the weapon, brighter each rhythm step, gold on a perfect heavy; sparks by what the blade met.
- **Stances on the plates** (`core.api.Stances`): a strained stance's amber strip under the health bar, gold while
  broken; the fighter's focus wears the plate's bright rim. Snapshots on change, drained by one rule on both sides.
- **Third person** (Player Animation Library, optional): a full-body clip per move keyed on the timing table, the
  guard and the heavy's draw held, the dodge by its way; checked for an arm through the body.
- **The Art** (a full charge with Resonance full: a great sweeping wave), the **Undercut** (a strike out of a slide)
  and the **Plunge** (a strike in a fall, landing all round).
- **The six forged Longswords, sculpted**: one per Hunter tier, each a hand-designed mesh (lofted blades with real
  edges, bevels and fullers, swept guards, wound grips, cut stones), painted texel by texel as pixel art and drawn by
  our own shader (reflecting metal, a sheen that runs the blade, runes and circuits that light in turn, halos round
  the stones). The fighter's own blade fills with light as a heavy charges and hums when the Art is ready. Their own
  first-person hold, icons drawn from the same meshes. Review sheet: `docs/combat/weapons-2026-10-25.png`.

## Decided without Euan (each in SPEC §24)
1. **Sounds are hybrid**: Kenney's CC0 Impact Sounds and RPG Audio for the physical transients, the kit's synthesis
   for the steel, the air, the weight and the tells. Unused recordings are pruned (renders stay byte-identical).
2. **An `arms` loudness category** (−14 dBFS ±3, true peak −1), calibrated against vanilla's own combat sounds: at
   AWAKEN's general −20 a blade sounded weaker than a wooden sword.
3. **Each strike class has one voice everywhere** (the mobs module plays `Arms.tellSound(kind)` with every told blow):
   the ear learns light / heavy / perilous / grab before the eye does. Abilities that are no blow keep their old wind-up.
4. **Vanilla's attack sounds are silenced during an Arms hit**; the fighter hears their own client's copy, everyone
   else the server's.
5. **`core.api.BodyMaterial`** (flesh, armour, bone, wood, stone, spirit, slime) is a core API, not an Arms detail:
   sparks and splinters (S2) will read it too. `MobDef.material()` answers for our bodies.
6. **One hit-stop**: the powers module's freeze was a copy of Arms'; it now calls `CombatFeedback.hitstop`.
7. **Arms off means vanilla**: with `awaken-arms.toml` `enabled = false` the guard Arms gave a sword never rises.
8. **First-person swings move the hand across the view and keep the blade flat to the eye.** The first tour showed a
   flat item sprite turned about the vertical goes edge-on and vanishes mid-cut, and the blow happened off-screen. A
   pose is now the hand's place on screen, the blade's roll and an optional tilt; the blow runs on an arc about the
   shoulder, the wind-up and the way home on straight lines. Reviewed frame by frame (below).
9. **The perfect dodge's slow is exactly half speed** (every other tick for 8 ticks), the plan's 0.45 rounded to the
   tick clock.
10. **The timing table is read from the classpath, not a reloadable datapack**: the server judges and the client
    predicts by the same rows, so a reload that only one side saw would desync them.
11. **Soft focus turns, it doesn't pull**: the plan's 0.6-block close waits for S2's lunge movement (the constant
    was removed rather than left unused).
12. **A hurt shard** (a short red arc round the crosshair toward the blow, 24 ticks) for any held item, with a body
    thump and a small jolt; vanilla's own tilt stays.
13. **Trails face the viewer**: an arc drawn in the plane of the look is seen edge-on (a line), found in the tour; the
    crescent follows the hand's arc pushed out ahead of the eye instead.
14. **Weapons are sculpted meshes with our own renderer and shader, not JSON cubes.** Euan's verdict on the cube
    version and on a chunkier cube prototype: "still very, very blocky... push it to the maximum". NeoForge's OBJ
    loader would give shapes but vanilla's flat light; a special item renderer drawing our own mesh through our own
    pipeline gives reflections, sheen, flowing light, halos and a blade that reacts to the fight. The pipelines are
    optional: a shader pack or driver that can't build them gets the same mesh in vanilla's entity light.
15. **Pixel art on smooth forms**: light is painted continuously, then set onto hand-picked hue-shifted ramps with an
    ordered dither, at 4 texels a unit (vanilla blocks are 1); the shader's light is partly banded. That keeps
    sculpted weapons reading as Minecraft rather than as a different game's assets.
16. **Each part unfolds about its own line** (a blade's two faces separately, each about its fuller), so runes,
    circuits and scales are painted on straight columns of texels and stay square.
17. **A first-person hold of their own**: vanilla's sword sits so close that a longsword shows a slab of blade at rest;
    ours stand the whole blade at the right with its point in view. Chosen with a preview that reproduces the game's
    hand projection exactly (checked by overlaying it on a game frame). The swings are unchanged.
18. **32×32 icons rendered from the mesh**, each pixel the surface colour at its centre (averaging, then snapping to
    the ramps, both turned pixel art to mud); vanilla's outline. Ground, frames and shelves use the icon too.
19. **Recipes until the Forge's blueprints (S7)**: the tier's Hunter blade between its core and bind.
20. **One tier, one concept**: flint and antler (E), tusk and bronze (D), gate-steel and a lit circuit (C), Veil-steel
    with a spirit edge (B), black scale and molten gold (A), ivory, gold and the Court's runes (S). Every design is a
    function in `tools/art/specs/arms_weapons.py` with its story in its docstring.

## How it was verified
- **GameTests**: the full suite, 475, all green locally (JDK 25 in the container). New: `arms_sounds`,
  `mobs_body_materials`; `arms_kill_switch` now also proves the guard never rises with Arms off.
- **Audio**: `tools/art/specs/audio_arms.py` checks every render (loudness, true peak after encoding, the blow's onset
  in its first 6 ms, clicks, harshness, variants that differ): 0 problems. `audio_mix.py`'s report: arms 74 files, RMS
  −16.6..−11.9 against −14±3, peak −1.1.
- **The game, headless**: the dev client runs in this container on Vulkan through Mesa's lavapipe (Xvfb,
  `SDL_VIDEO_DRIVER=x11`, `VK_ICD_FILENAMES` lavapipe). `tools/autopilot/arms-poses.txt` holds each move still at six
  points (the autopilot's new `arms pose <move> <0..1>`), `tools/autopilot/arms-feel.txt` drives the real verbs (`arms
  strike|heavy|guard|dodge`): guard, the charge, a live strike, the hurt shard front and side, third person.
- **The listening sheet**: `docs/combat/listening-2026-10-25.html` (eight fight scenes at the game's own timing, then
  every sound alone, keep / remake / cut, export). **This is Euan's to do**: the checks catch a broken sound, not a
  dull one.

- **The weapons**: `python3 tools/art/specs/arms_weapons.py build` (designs, checks, every file);
  `tools/art/mesh_render.py` renders them with the shader's own terms; `tools/autopilot/arms-weapons.txt` shows each
  in first person at rest and mid-blow, a charged heavy, on armor stands, in the hotbar, on the ground and at night.
  `arms_assets` reads every mesh with the game's reader.

## For Euan, when he's back (about 15 minutes)
1. Open `docs/combat/listening-2026-10-25.html`, play the eight scenes, mark them, export.
2. Fight something with a sword (strike, hold for the glint, right mouse to deflect, sprint + back to dodge) and say
   whether it feels like steel and weight.
3. Take the six Longswords from the creative tab (AWAKEN). Hold each in first person, swing, charge a heavy (watch the
   blade fill with light), look at them at night. Say which designs land and which don't (the designs are cheap to
   change: each is one function).

## Next (the rest of S2, then S3 onward)
S2: the camera voice and comfort switches; the weapons' own particles (embers off Lordscale, wisps off the Veil edge,
motes round the Sovereign's stones), and a third-person look at PAL in game (verified so far on preview sheets only).
Then S3 (classes and tells on every boss, posture everywhere, the combat simulator, item level) and the pipeline's
other fifteen archetypes, which now have a toolkit: a new weapon is a design function and a painter or two.

## Known
- The headless tours render on lavapipe (software Vulkan): lighting and colour match, but Lens's bloom and the halos'
  glow read stronger on a real GPU; judge the glow on the Mac.
- Trails are subtle against a bright sky (additive light can only brighten); strong at dusk, at night and indoors.
- `audio_layers_watch` can fail when run with only `-Ptests=arms_,mobs_,audio_`: a concurrent mobs test spawns a boss
  near it (the watch sees a Lord at tick 0). It passes in the full suite; it is the audio test's isolation, not Arms.
