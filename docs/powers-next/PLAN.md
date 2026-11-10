# Powers, the Next Level

Plan only. Nothing here is built. Approval page: `plan.html` (built by `build_page.py`). Branch `claude/powers-next-plan-557510`.
Euan, 10 Nov 2026: "look at all the power animations and how we can improve those even more ... especially when it comes to the
variety, when it comes to the animations, the sounds. The sounds are a big one ... maybe there's a ton more powers that could be
interesting, a ton more interesting combos or different things." And: "I don't want to completely scrap everything."

This plan sits on top of Powers in Full Voice (PIFV, `docs/powers-aaa/PLAN.md`, SPEC §50, all 43 answers yes on 9 Nov). PIFV says
what each power does. This plan says how every power is made from now on: what the hands do, what you hear, how two casts differ,
and how powers meet weapons and movement. It replaces one thing in PIFV: the way sounds are built (its answer 11 keeps its goal).

## 1. Brief and success contract

- **Job.** Press a power key in a fight and see, hear and feel something that is clearly that power, different from the other 62 and
  from the last time you pressed it, and that belongs in Minecraft.
- **Pain, as measured (section 2).** The hands barely act and often act off screen; sounds are synthesis; powers share gestures,
  effects and sounds; nothing varies between two casts; a cast and a weapon can't share the screen.
- **Strong result.** With the HUD off he can name a power from one second of the hands alone, and from the sound alone. Three casts
  in a row neither look nor sound the same. A sword in hand is never hidden or wagged by a one-handed cast. No wind-up happens out
  of view. No sound reads as synthetic to him.
- **Weak result.** Nicer clips on the same single arm-block, with a ring on the ground.
- **Unacceptable.** A synthetic ring at a release. A hand covering the target. Hands and server disagreeing. A lower frame rate.
- **Constraints.** Polish, not scrap. No timing layer (only PIFV's six). No dings. Sounds start from Minecraft's own recordings, by
  name; no Mojang audio in the repo. Blocky fists, no fingers (SPEC §24). Nothing fights for you. Server decides damage. Multiplayer
  safe. New progression steps go on the Tasks page (`core.api.Goals`).
- **Not known.** Whether Euan has played PIFV's Stage 0 and what he thought. Whether an elbow reads well on a blocky arm. Whether
  layered game recordings sound right to him. All three are answered by the proof, not by more planning.
- **Must not regress.** PIFV Stage 0's mechanics (stance, ground wave, wall slam, Well, bore). Held-hands ownership
  (`power_hold_pose_has_an_owner`). Release on the body's frame. The ladder. Charged Shot's ball. 1167 GameTests.

## 2. Audit (verified on build 43dceee2a, 10 Nov)

| Part | What it really does | Evidence | Keep / change |
|---|---|---|---|
| First-person arm | One straight box from a fixed point under the frame's corner to a keyed point. No elbow, no wrist turn. | `anims/client/WorldBody.java` `viewArm`; `fp_moves.fist(x,y,z)` | Extend (shared with weapons) |
| Cast clips | 159 clips; median 5 keys a hand counting start and end; 30 are identical to another power's; one "dash" gesture serves six powers. SPEC §32: "authored from the numbers, not yet seen". | `fp_casts.json`; `tools/art/specs/fp_gestures.py` | Re-author against film |
| Where the hands are | Wind-ups under the frame or behind the hotbar (Haymaker's charge, Nova, Frost Nova, Mend). Dominate's arm covers the target. | CI film, run 42788742556 | Fix, then guard with a check |
| Weapon in hand | Fist clips hide it at once (a pop). Offset clips move the weapon hand, so the weapon wags (Spirit Palm, Hundred Palms). | SPEC §32 "a pop, not a lowering"; film | Replace: off-hand casts |
| Screen moments | 8 shared moments for 54 profiles. | `CastFeel.MOMENTS` | Per power |
| Effects | 30 effect kinds tinted by family. On film: a thin ring, a flash and a number (Thunderclap, Frost Nova, Telekinesis, Gravity Well). Barrier and Mend show nothing in first person. Dash throws plain squares. Burning is the game's own fire. | `FxKind`; film | A signature per power |
| Sounds | 224 files, 83 ring. Kit recipes: 72 of 76 pure synthesis. The Stage 0 "recordings" packs layer a few recordings under synthesis. Source library: 7 field recordings and two Kenney packs. 46 powers play only shared events. | `ding_check.py` run today; `audio_power_kits.py`, `audio_power_voice.py`; `audiokit.py` header "No samples" | Replace the method |
| Variety in one power | One clip, two sound takes. | clip and sounds.json files | Add takes and ways |
| Combos | 7 Force reactions; 3 slide twists; 36 Weaves that are 4 shapes x 9 colours. 24 Duets approved, unbuilt. | `ForceInteractions`, `MoveTwists`, SPEC §25 Weaves | Give them bodies |
| PIFV Stage 0 | In build since 9 Nov, never filmed until today. | merge d0e14dfa1 | Keep, bring to the new bar |

Already good, kept as they are: Storm Call's look, the Beam, Charged Shot's ball, Haymaker's hit, the ground wave, the ladder,
release-frame sync, server authority, third-person clips (208, through the animation library).

Seen once, to check in play: after Shadow Step the view was full of sky and the target's name plate (film still `nb_shadow_step_b`).

## 3. Root causes

1. **The arm can't act.** A rigid box whose only freedom is where its end is. Verified in code and on film.
2. **Casts were authored blind.** Poses came from numbers, never from film, so wind-ups landed off screen.
3. **A cast and a weapon can't share the screen.** Either the weapon pops away or it wags.
4. **Sound is synthesis first, with almost nothing recorded to draw on.** 9 Nov removed the ring from four powers and kept the method.
   His own verdict on that method for the pick: "super synthetic", then on the game's recordings: "WAYYYY better".
5. **Identity is a colour.** 30 effect kinds, 40 shared sound events, 8 screen moments across 63 powers.
6. **Nothing varies** between two casts of one power.
7. **Combos are tables**, not moments: 4 x 9 Weaves, none specific to a weapon or a power.

Not proven: that an elbow looks right (causes 1); that game recordings sound right to him (cause 4). Both go to the proof.

## 4. Alternatives

- **A. Repair.** Re-author the clips in today's rig, remake the 83 ringing sounds, fix off-screen and wagging. About 15 hours. The arm
  stays a stick and powers stay alike.
- **B. Structure (recommended).** Elbow, wrist and light on the arm; off-hand casting; sound scores from the game's recordings;
  takes and ways; a signature per power; combos with bodies. Done power by power in the same pass as PIFV's reworks.
- **C. New modelled hands with fingers and a bought sound library.** Against his "clean fists" ruling and the Minecraft feel. Loses.

What would change the recommendation: if the proof's elbow reads as loose bricks, fall back to A's rig and keep B's sound, takes
and combos. If he prefers other recordings on the listening page, the score system stays and its sources change.

## 5. Design

### 5.1 Hands (module `anims`, used by `powers` and `arms`)

- **Clip schema 2** (`anims.FpClipSet`): a fist key may add an elbow point and a wrist turn in degrees. Missing fields mean today's
  values (the fixed anchor, front up), so every existing clip plays unchanged. `WorldBody.viewArm` takes the elbow as the arm's
  root and the turn as its front. An upper arm is drawn from under the frame to the elbow.
- **Light on the arm** (`powers.client.fx`): a second pass of the same arm part with a texture of ours on the skin's own pixel grid
  (emissive, like `RenderTypes.eyes`), one pattern per family, driven by a `charge` value that runs elbow to fist through the
  wind-up, leaves through the hand on the release and fades over the recovery. Level 5 adds the family's awakened pattern. Works on
  any skin. Self-buffs (Barrier, Mend, Nanite Skin, Brace) show here, so you can see them in first person.
- **Off-hand casting.** One-handed casts key only the off hand; the main hand keeps the weapon and lowers it into a guard by an
  offset. Two-handed casts put the weapon away through the existing ease (`Hands.STOW`, 2.5 ticks down, 3 back), never a pop. Body
  casts (a dash, a stomp) leave both hands on the weapon. If the off hand holds something (a twin, a shield) it sinks out for the cast.
  Left-handed play mirrors as today. Arms' grips need one new call for two-handed weapons: "the off hand leaves the grip for N ticks".
  The weapon session's draw (Weight in the Hand, stage 4) plays only on a real change of the held item, never when a two-handed cast
  gives the weapon back (its note, 10 Nov).
- **Authoring rules** (each one a check on the clip data, `power_cast_frames`):
  every pose held 2 ticks or more is inside the safe frame (above the hotbar line); no hand sits in the middle fifth of the view for
  more than 2 ticks; a strike of 4 ticks or more has 2 ticks of anticipation; the two hands never start on the same tick; a strike
  overshoots and settles; no two powers share a clip; the recovery can be cut by the next action from the release on.
  From `animation-glitch-rules`: the hands decide what plays and the server plays that or refuses; a reply never changes what is on
  screen; clocks are doubles; nothing is called done before its film has been looked at.
- **Camera per power**: kick, dip, field of view and roll keyed with the clip, replacing the 8 shared moments. Reduced motion halves
  it, camera shake off removes it.
- **Third person** stays in step: each first-person clip's third-person twin is updated in the same commit.

### 5.2 Voice

- **A score per power**, data in `assets/awaken/power_voices/<power>.json` (schemaVersion 1): beats `gather`, `peak`, `release`,
  `travel`, `impact`, `tail`, `level5`; each beat two to five layers; a layer is a sound event, volume (at most 1), pitch (0.5 to 2),
  start tick, jitter. Layer events are ours and point at the game's recordings by name in `sounds.json`
  (`"name": "minecraft:mob/warden/heartbeat_1"`), every take listed. Where the game has nothing, a CC0 recording of the real thing.
  No synthesis.
- **One message per beat.** The server sends `FxKind.VOICE` (power, beat, intensity 0 to 1, position); each client schedules the
  layers with `SoundManager.playDelayed`. The server decides when; clients decide how it sounds.
- **The world joins in.** The impact beat adds the struck block's own sound (`SoundType`) and the struck creature's own hurt sound,
  pitched down.
- **Never the same twice**: takes, jitter, and two layer sets per beat chosen in turn.
- **Rules**: no tonal sources (amethyst, note blocks, bells, beacon, level-up, orb); the ding check runs on an offline mix of every
  score as a tripwire, and he is the judge; one subtitle per beat; at most 12 voices a cast; beyond 24 blocks only the first two
  layers; the bed ducks 150 ms before the biggest impacts (`Soundscape.muffle`).
- **Build check**: every named recording exists in this Minecraft version's sound list.
- **Hearing it**: a client setting, Power sounds: Classic or New, switchable in play, for as long as both exist. And the local
  listening page (`docs/powers-next/voice/`, output under `run-autopilot/`, never hosted: it plays Mojang's audio).

### 5.3 Variety

- **Takes**: two or three versions of each gesture, chosen from the cast's own counter (sent with the cast, so everyone sees the same
  one), never the same twice running.
- **Ways**: about twenty physical powers read what the body is doing at the press (`MovementHooks`): standing, at a sprint or slide,
  in the air. Each way has its own gesture and changes the shape of the power, never its damage (all under `PowerMath.capBonus`).
  No timing. First set: Shockwave, Ground Slam, Haymaker, Spirit Palm, Nova, Frost Nova, Thunderclap, Terrain Rip, Beam, Charged Shot.

### 5.4 A signature per power

No power's main effect may be only a ring, a flash and a number. Each gets one effect in its own material, anchored to the hands
or the world; PIFV answer 10's showpieces supply many. No plain squares, no vanilla fire (our own burning look). Checked by a list
in the tour: one still per power, looked at.

### 5.5 Combos and new powers

- **Weaves performed by the weapon**: the 36 stay; when one fires the weapon does it (4 openings x 6 weapon classes of swing carrying
  the family's element). With the weapon-feel session.
- **Arms Duets**: twelve named pairs of one power and one weapon style, found by trying, counted with PIFV's Duets (N / ??):
  Telekinesis + any weapon (Recall), Charged Shot + Spear (Thunder Javelin), Flow + Katana (Still Cut), Barrier + Shieldblade
  (Mirror Guard), Haymaker + Gauntlets (Both Barrels), Shadow Step + Daggers (Chain Step), Frost Nova + Maul (Icebreaker), Gravity
  Well + Scythe (Reap), Beam + Greatblade (Edge of Light), Terrain Rip + Maul or War-staff (Batter Up), Lightning Step + Twin
  Blades (Crossing), Spirit Palm + War-staff (Long Reach).
- **Break finishers**: a cast into a broken boss (the existing Break Weave) plays a short first-person finisher, one per family.
- **PIFV's 24 Duets** each get their own gesture, effect and score.
- **New powers**, each filling a measured hole (Heat has one power and Frost one, while weapons attune to both and PIFV's Burning
  and Chilled States have almost no source; nothing answers the forge or ties Flight to the Ascent):
  Brand (Heat, from the War Anvil), Hoarfrost Lances (Frost, loosed by your swings), Updraft (Flight, a column of air for the
  Ascent), Backdraft (Heat, breathe in every fire and give it back), Thornwake (Spirit, roots from the real plants near you).
  Each registers its first step on the Tasks page and its Reading in the Journal.
- **Later experiment**: a beam meeting a boss's beam.

## 6. Delivery

One builder, no sub-agents. Merge `build/awaken-v1` first (the One Stage UI is landing). Each stage ends with the same CI film
(`tools/autopilot/powers-next-before.txt`, so before and after are the same shots), a listening page, and his play.

| Stage | What | About | Stop |
|---|---|---|---|
| 0 | The proof: arm schema 2 and light (behind a setting), off-hand casting, voice scores with the Classic/New switch, takes. Four powers to the bar: Spirit Palm, Nova, Shockwave (three ways), Frost Nova. Haymaker's charge brought into view with its new score. | 14 h | Euan plays and listens |
| 1 | The six starters and what a new player meets first | 12 h | film and listening page |
| 2 to 10 | One family each: PIFV's rework, hands, voice, signature, takes, ways. Each power opened once. | 10 to 14 h each | film and listening page; he plays when he likes |
| 11 | Weaves by the weapon, Arms Duets, Break finishers (with weapon-feel) | 20 h | he plays |
| 12 | New powers he picked, two at a time | 4 h each | he plays each pair |

PIFV's systems (States, Duets, the R gauge, the bar) keep their own stages and estimate (130 hours). This plan adds about 95 hours
to that (70 to 130): engine 14, re-authoring and scores about 55 across the families, combos 20, plus 4 a new power.

**Go or stop after Stage 0**: he says the four are clearly better in the hand and the ear; `FxStats` shows no extra frame time; the
play log has no line where the server ran one thing and the hands showed another.

**Roll back**: the arm's new fields default to today's; the light and the scores sit behind client settings; a stage is one merge.

## 7. Checks

- `power_cast_frames`: the authoring rules of 5.1 on every clip.
- `power_voice_scores`: every score parses, names only recordings that exist, no tonal source, at most 12 voices, a subtitle per beat.
- `power_takes`: every profile has two takes or more and never repeats one.
- `power_offhand`: a one-handed cast never sets `stow`; a two-handed one always eases.
- Existing: `power_hold_pose_has_an_owner`, `power_held_casts_say_they_ended`, `power_cast_profiles`, `power_cast_beats`, the 1167.
- Film: the same 17 casts, before and after, every frame looked at.
- His: the HUD-off naming test; pass or redo on each listening row; the Classic/New switch in a real fight.

## 8. Risks

| Risk | Answer |
|---|---|
| The elbow reads as loose bricks | Proof first; fall back to today's rig with everything else |
| A future Minecraft renames a recording | Build check lists it; scores are data |
| A player's resource pack changes the game's sounds | The powers change with it; accepted |
| Two-handed weapons and an off-hand cast | The off hand leaves the grip and returns; one Arms call; agree with weapon-feel before Stage 0 |
| Ways unbalance a power | Shape only, under the same caps; simulator row per way |
| Two sessions change the hands at once | `anims` owns schema 2; note left for weapon-feel and armour-polish in the shared notes folder |
| Flash and motion comfort | Existing settings apply to every new camera key and bloom |

## 9. Decisions for Euan

Eleven, on the page. Each has a recommendation and what happens if he says no.
