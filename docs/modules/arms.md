# Arms (`dev.awaken.arms`)

Weapons and combat: three verbs on every melee weapon (strike, guard, dodge), enemy blows that are told and answered,
stances that break, and the feel and sound of a blow. The plan is `docs/combat/PLAN.md` (approval page
`docs/combat/plan.html`); the numbers and every decision as built are SPEC §24. Approved by Euan on 2026-10-25.

| Verb | Input | What happens |
|---|---|---|
| Strike | attack key, tap | the style's chain; a press in the rhythm window earns a step (+8%, up to 3) |
| Heavy | attack key, held | charges from 6 ticks; release on the glint (16) for a perfect heavy |
| Guard | use key | 4 ticks of deflect, then a block; a HEAVY breaks it, PERILOUS and GRAB pass |
| Dodge | sprint + back, left, right or none | 6 ticks, 2.6 blocks, ticks 0–4 evade, 0–2 a perfect dodge |
| (context) | | Riposte after a deflect, Slipcut after a dodge, Execution on a broken stance |

## Where things are

- `Archetype`, `Archetypes`: the timing table, `data/awaken/arms/archetypes.json`, read from the classpath on both sides
  (the server judges and the client predicts by the same rows). Add a style there; give it moves by the `Move` keys.
- `ArmsRules`: every rule as a pure function (the chain and its rhythm, the guard's answer, the latency grace, the
  dodge's ticks, posture). Both sides call it; the `arms_timeline` and `arms_defence_rules` tests pin it.
- `ArmsServer`: presses judged on the server's clock, the move's active ticks swept (`sweep` → `hit`, through vanilla's
  own `Player.attack` at full strength), the damage stage (`onIncomingDamage` at HIGHEST: the move's multiplier, the
  Execution cap, posture; the dodge's evade; the latency hold), the guard (`onShieldBlock`), dodges, stances, Resonance.
- `Arms`: the public face (§12): `styleOf`, `guards`, `fights`, `posture`, `broken`, `tellSound`.
- `ArmsSounds`: the sound events and the one place that picks a blow's sound (both sides).
- `client/ArmsPredict`: the verbs' input and prediction (the move starts on the press frame), the server's answers.
- `client/ArmsHands`: the weapon in first person (vanilla's arm placement, then the row's swing, charge and guard).
- `client/ArmsFeel`: `core.api.CombatFeedback`'s implementation (freeze, hold, kick). Powers' hits use it too.
- `client/ArmsHud`, `client/ArmsAudio`: the arc, the pips, the hurt shard; what the fighter hears of their own fight.
- `ArmsItems`, `ArmsData` (generated): our weapons, one item per catalog row. `WeaponMesh`: a sculpted weapon's mesh as
  plain data (read on either side). `client/WeaponRenderer` (the `awaken:weapon` special item model), `WeaponPipelines`
  (the weapon shader `shaders/core/arms_weapon.*` and the stones' halo light, both optional), `WeaponMeshes` (loaded
  with the resources).
- Across modules: `core.api.Strikes` (blow classes, tells, stagger: the mobs module installs them), `core.api.BodyMaterial`
  (what a blade meets: `MobDef.material()` for ours), `core.api.CombatFeedback`.

## Rules worth knowing

- **The server decides, the client never waits.** Every move is predicted from the same rows; the server's `MOVE`
  result corrects the move (a different one) or `REFUSE` fades it out. Never start a move only on the server's word.
- **One Arms hit at a time** on the server thread: `pendingBy`/`pendingTarget` mark it so the damage stage, the sound
  listener (vanilla's attack sounds are silenced) and `AttackEntityEvent` (not cancelled for it) know.
- **Tests run side by side.** Nothing a test reads may be shared static state: the latency hold and the test grace
  are per player (`ArmsServer.clear(p)`, `holds(p)`, `ArmsState.testGrace`).
- **Mock players never tick**, so a held sword's attribute modifiers never apply in a GameTest: `ArmsTests.fighter`
  sets the attack damage base to the sword's 7 itself.
- **Sounds**: re-render with `python3 tools/art/specs/audio_arms.py` (all), `--json` (only sounds.json and the
  subtitles), `--check` (the checks on what is on disk) or name prefixes (`hit_ swish`). Every event needs a holder in
  `ArmsSounds` (`audio_events_resolve` fails otherwise).

## Progression: where weapons come from

Plan `docs/arms-progression/PLAN.md`, SPEC §25 "Arms progression". Patterns come from gates (a bearer on the first floor), villages (the
smith's rack), structures (Pattern Stands) and people; Hale's rack sells every pattern at its rank for essence. The code, by file:

- `ArmsHomes` (`data/awaken/arms/style_homes.json`: homes, prices, level bands, boss and Marked rates), `ArmsSources`
  (`sources.json`, written by `tools/arms/sources.py --check`), `ArmsLang` (our English, readable on a server).
- `ArmsProgress` (`awaken:arms_progress`): styles known, issue taken, pity counters, claimed gates, people and stands.
- `ArmsIssue` (implements `core.api.ArmsApi.Hooks`): Hale's issue, `learn`, the shard pity, the hooks the other modules call.
- `ArmsArmoury`, `ArmsTrack`, `client/ArmouryScreen`: the Armoury's three doors, Track, the Now line.
- `ArmsPatterns`: the gate bearer, the Lord's hoard fallback, camp hearts, the six teachers. `ArmsSpoils`, `LordForged`, `MarkedDrops`: boss
  weapons and the Marked drops. `ArmsRaise`: raising a weapon a tier.
- `ArmsTiers`: how a tier is announced (the lock line, the open toast, "What waits"); the part jobs are `bounty/part_job_*.json`.
- `ArmsStands`, `PatternStandBlock`, `PatternStandBlockEntity`, `StandKind`, `client/PatternStandRenderer`: the Pattern Stand. Art:
  `python3 tools/art/build_all.py pattern_stand` (textures) and `python3 tools/art/specs/pattern_stand.py content` (models, blockstate, name).
- Simulation: `python3 tools/sim/gear_sim.py` sets the boss-weapon and Marked rates (Lords per weapon, hours to the first of each).
- Tests: `tools/gradle.sh 900 runGameTestServer -Ptests=arms_` (the `arms_` prefix covers all of the above).

## Weapons

A weapon is a design function in `tools/art/specs/arms_weapons.py` (its catalog row there too), built from the toolkit
in `tools/art/mesh.py` (`blade`, `block`, `ribbon`, `orb`, `gem`, `ring`, `torus`, `spike`, `MS.sweep/lathe/tube`) and
painted by the painters there (a material is a function of each texel's point, normal, tag and place). Run
`python3 tools/art/specs/arms_weapons.py build`; check the look with `preview DIR` before the game. Keep
`tools/art/mesh_render.py`'s `shade()` and `arms_weapon.fsh` in step: the preview is only worth trusting while they
match.

## Tests and tours

- `tools/gradle.sh 900 runGameTestServer -Ptests=arms_` (and `mobs_told,mobs_ability,mobs_body,mobs_held` for S0).
- Autopilot: `arms <strike|heavy <ticks>|guard on|off|dodge left|right|back|pose <move> <0..1>|hud>` lines drive the
  fighter for screenshots (`tools/autopilot/arms-*.txt`).
