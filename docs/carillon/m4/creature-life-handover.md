# Creature life: hand-over (creature-life session, 25 Oct 2026)

Branch `pack/creature-life` (worktree /Volumes/MEDIA/Awaken/.claude/worktrees/creature-life-5f1f3a), base build/awaken-v1
19afe05e, head b23ef00a. 7 commits. SPEC §23 (and a §12 entry for `CreatureLife`).

## What's in it
- **Pack** (72a8e280): EMF 3.3.12, ETF 7.2.6 and Fresh Animations 1.11.1 in `feel`. pack.py: resource packs lock
  (Modrinth project type, loader "minecraft"), install into resourcepacks/ + options.txt resourcePacks (append only if
  missing), remove only what it put there, export under resourcepacks/. Lock: only the three new entries; Simple Voice
  Chat's 2.6.25 -> 2.6.26 bump undone.
- **Life layer** (`mobs/client/life`): bone-name roles, temperaments in assets/awaken/life/creatures.json, per-creature
  `Life` (breath, blinks, slow head with eyes first, springs, step dip/sway, turn lean, idle fidgets, stagger, wind-up
  crouch + tremble, roar breath/shudder, fall thud, boss topple). On every body of ours: HeroModel, WildModel and the 14
  hand-built ones (new base `LifeModel`). Graph fixes: eased angry/held/swim/glide weights, loop clocks (fliers and
  swimmers used age x speed, which jumped phase on every speed change), one-shot clips let go of their last frame, fall
  crossfade. Bosses on vanilla bodies get only root-level stagger, provocation and topple.
- **Blink skins**: 14 creatures get `<id>_blink.png` + `<id>_glow_blink.png` from the art specs (`with P.eyes():`
  records eye pixels; fauna_rig.EyeRec). Every existing texture is byte-identical (only new files).
- **Test**: `creature_life_binds` (passes locally, -Ptests=creature_life).

## Tour (tools/autopilot/creature-life.txt, PACK=perf,feel, Vulkan)
Shots: run-autopilot/screenshots/life_*.png; clips with mp4 + contact sheets: run-autopilot/clips/life_*{.mp4,_sheet.png}
(paths under the worktree above).
- Idle: fidgets visible (stag and owl glances, the otter rising, the owl's own swivel), creatures out of step.
- The Briar Sow's fight reads (lowered-tusk charge, ram, pollen shake, red stagger on a blow); its fall crossfades onto
  its side.
- Gate Lords: the Bell Warden (ours) and the Sleeper (zombie body) recoil on a blow and topple flat, gone at 20 ticks.
- Fresh Animations is live on vanilla bodies (the burning vanilla zombie's FA panic; the Sleeper's FA hurt flail).
- Not captured: the walk/turn section (pilot framing: walkers far off behind the player); I stopped at two runs (the
  first run was unusable: Shoulder Surfing view + toasts, and the level-1 player died to the Sow).

## Compatibility (checked once in the tour)
- Player Animation Library + Bendable Cuboids: a third-person Haymaker cast renders its bent full-body clip with EMF,
  ETF and FA loaded; front view normal. Not Enough Animations, Shoulder Surfing, CameraOverhaul, 3D Skin Layers and
  Sodium all loaded; no errors in the log (only MoltenVK "primitive restart" warnings, as before).
- EMF leaves our own bodies alone (they bake `awaken:` layers; FA has no .jem for them). It DOES apply to our gate
  monsters on vanilla bodies (zombie, wolf, hoglin, iron golem, evoker...): wanted, FA keeps vanilla UVs so our skin
  layer fits. If a telegraph pose ever gets lost under FA, EMF's per-entity switch is
  `entityRenderModeOverrides` in config/entity_model_features.json (keyed by "entity.awaken.<id>").
- Shoulder Surfing: tours start in its over-the-shoulder view (FeelConfig.shoulderView) and the autopilot's
  `camera first` right after joining doesn't stick. Harmless, but tours wanting first person need a later `camera first`.

## Open, for whoever owns them
- The Briar Sow summoned and then `/kill`ed in the tour was removed ~15 ticks after the kill, not NamedBeast's 36
  (seen once, in the clip's frame times). HeroTests checks the hold for a normal death; worth a look if a player-kill
  fall looks short.
- Carillon wires `CreatureLife` into citizens in M4 B3 (creature-life-api.md); send them the API after the merge.
