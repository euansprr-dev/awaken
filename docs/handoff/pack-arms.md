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
- **The sound kit**: 25 events, 74 files, hybrid (CC0 recordings + synthesis), every file inside the `arms` loudness
  window; the impact chosen by what the blade meets (`core.api.BodyMaterial`); one tell voice per class on every
  creature; a body thump when you are hit.

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

## For Euan, when he's back (about 10 minutes)
1. Open `docs/combat/listening-2026-10-25.html`, play the eight scenes, mark them, export.
2. Fight something with a sword (strike, hold for the glint, right mouse to deflect, sprint + back to dodge) and say
   whether it feels like steel and weight.

## Next (the plan's S2, then S3 onward)
Trails on every swing; third-person clips (PAL, with the ArmPose fallback); the Art at full Resonance; traversal
strikes (Undercut, Plunge, Wall Cut, Reel Strike, Swoop); posture bars and the focus bracket on Holo plates (needs
posture synced to clients); six 3D forged Longswords; then S3 (classes and tells on every boss, posture everywhere,
the combat simulator, item level) and the pipeline.

## Known
- `audio_layers_watch` can fail when run with only `-Ptests=arms_,mobs_,audio_`: a concurrent mobs test spawns a boss
  near it (the watch sees a Lord at tick 0). It passes in the full suite; it is the audio test's isolation, not Arms.
