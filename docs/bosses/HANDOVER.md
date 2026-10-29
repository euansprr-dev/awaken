# Bosses hand-over: how to finish the last eight at the same bar

Written 2026-10-29 by the boss-plan session (branch `claude/boss-plan-f38a45`) for the next session, on another
account, that finishes the roll-out. You start with none of that session's memory. This page is everything it knew
that isn't already in `docs/bosses/ROLLOUT.md` (the tracker and every pick's reasons) and SPEC §35.9–35.11 (the
decisions, and one bullet per finished boss). Read those two first, then this.

## What Euan asked for

- 2026-10-28: "every boss needs to be revamped to this level of standard ... all unique ... Truly let's push this to
  the max." The standard is the Ember Tyrant (the Living Forge, `kit/bosses/ember_tyrant.py`).
- 2026-10-29: "pick the most unique and interesting forms of each that avoid too much repetition and are CLEAR high
  quality ones ... keep going until fully finished and all bosses have: 1. Full bodies at the quality bar we set
  2. Animations on par with Fresh Animations".
- "Don't use multiple agents, do this all yourself." No sub-agents and no builder sessions: one session, one boss at a
  time.
- The fight (R3: phrases, tells, hit volumes) is not part of this ask. Bodies and motion only.
- His verdict on the first kit bosses explains the bar. They looked derpy, blocky and vanilla. The motion was rough and
  glitchy. The sounds were weak. He wants it to feel premium, like "this is insane". He doesn't want round shapes, and
  he wants Fresh Animations' smoothness and an Elden Ring feel.

## Where it stands

- **Done: 33 of 41.** These are the Forge, all 14 other Lords, the 10 Guardians and the 8 Sovereigns. Each row in
  ROLLOUT.md has its commit.
- **The Signal's Shadow is half done.** It is the Signal Cuttlefish: `kit/bosses/signals_shadow.py`, committed but
  not in `BOSSES` and not built.
  - Done: body (73 cubes, 49 bones), paint, every everyday clip, 14 moves and the intro, sounds, a 3-phase fight.
  - Meter: every move is held at most 2%, the worst kink is 3.7°, the floor sweep is clean, and every wind-up is
    at least 13.
  - **Left to do:**
    1. Fix the Lash → Grasp dip. It is 45% and the target is under 40%. It rose when the Lash's hit was lowered to
       keep the arm tips off the floor. Run `kit/checks/dipwhere.py signals_shadow lash-grasp` to see it. The fix is
       probably a bigger Lash hit (more spread or bend) or a Grasp gather that commits earlier.
    2. Add `"signals_shadow"` back to `BOSSES` in `kit_bodies.py`.
    3. Set `.size(1.15F, 1.13F)` on its `DeepBestiary` line. That is what `extents.py` gives at scale 2.0: true
       size 2.9 × 2.3 × 5.1 blocks.
    4. Write the ROLLOUT row and its pick note. The forms were: A, the Signal Cuttlefish (picked); B, the Antler
       Stag; C, a glass mantis, which repeats the Empress's upright insect with glass wings.
    5. Write the SPEC §35.11 bullet.
    6. Build, test, send Euan the sheet, then make the two commits (the steps below).
- **Not started: the seven Nightmares.** These are the Long Drop, the Pyre, the Undertow, the Hollow Crowd, the Thousand
  Legs, the Fuse and the Faceless. Their DeepBestiary lines (after `nightmares(`) hold their abilities and phase lines.
- **After the 41: the Gate Worlds' signature creatures.** That's 13 fights plus the `cloud_whale` (list below). They
  are queued; Euan decides when.

## The forms left, and what not to repeat

Each pick must be unlike everything already built. The roster already has these shapes: birds (the owl), knights and
fencers, shadow figures, objects on legs (the bell, the bed, the house, the engine, the clock tower), crowned humanoids,
robed figures, six arms, hounds and wolves, crabs and house-carriers, tree people, rays, pangolins, long-legged
crawlers (the harvestman), toads, dragons, fish (the angler), octopus and cuttlefish (so no more cephalopods), an
insect queen, a bat, a serpent and an hourglass.

These are my candidates, not decisions. Draw three per boss and pick by the rule above. Record A/B/C and why in
ROLLOUT's "The rounds so far", as every earlier pick does.

| Boss (kit id) | Family, old model | Its line | Candidates I had in mind |
|---|---|---|---|
| The Long Drop (`nightmare_falling`) | Flier, phantom ×2.4 | "Lifts you up and lets you fall" | a vast long-fingered hand coming down out of the dark on a long arm; a gibbet kite (a stretched-skin kite trailing a cage) |
| The Pyre | Flier, blaze ×2 | see DeepBestiary | a wicker effigy floating on its own heat (unlike the Ash dragon and the Forge); a burning censer swung on chains |
| The Undertow | Caster, drowned ×2 | see DeepBestiary | a huge snapping turtle with weed, chains and a whirlpool under its shell |
| The Hollow Crowd | Brute (a crowd), zombie ×1.7 | see DeepBestiary | a crowd stood so close it became one walking heap of hollow figures |
| The Thousand Legs | Crawler, spider ×2.6 | see DeepBestiary | a giant millipede that coils and rears (not a harvestman or a spider) |
| The Fuse | Construct, creeper ×2 | see DeepBestiary | a hunched thing bristling with lit fuses like quills that flare before it blows |
| The Faceless | Duelist, player | see DeepBestiary | a smooth blank figure that fights like you (not a knight, fencer, shadow or mirror) |

Get the kit id and the exact lines from `src/main/java/dev/awaken/mobs/def/DeepBestiary.java` (one `m(...)` per boss).
Every ability named there must be a move whose `ability=` is that exact name.

## The workflow for one boss (what every finished boss went through)

Work in `tools/art/specs`. A boss is one file, `kit/bosses/<id>.py`. Copy the structure of a recent boss of the same
kind:
- **Fliers or floaters:** `sovereign_sleep.py`, `guardian_seed.py`, or the Shadow's `signals_shadow.py`.
- **Four legs:** `sovereign_ash.py`, `sovereign_storms.py`.
- **Upright:** `sovereign_mirrors.py`, `guardian_court.py`.
- **Big brute:** `sovereign_silence.py`, `sovereign_machines.py`.

Each file has a docstring, then body, paint, motion, moves, clips, sounds, fight and `design()`.

1. **Pick the form.** Three silhouettes that don't look alike (`kit_bodies.py silhouettes DIR <id>`, options in
   `kit/sil/<id>.py`), or reasoned picks against the list above. Write the docstring first: what it is, its value
   story (which parts are brightest, which darkest), every move as the body does it, the intro, broken and death.
2. **Body** (`body()`). Use `fauna_rig.Rig`, bones through `_bone(parent, name, x, up, z, rot)` and cubes through `_box`.
   Model it at true size, where 16 px is 1 block. Aim for about 70–160 cubes, whole-pixel sizes only. Use flat cubes
   for fins and membranes, and stepped cubes for taper (no round shapes). Set `r.look`, `r.shadow`, `r.springs`
   (secondary parts that trail) and `r.guard`, the parts kept out of the floor. **`r.guard` takes bone names, never
   cube names.** `floorsweep.py` reports a bad one.
3. **Paint** (`paint(r, P_)`). Each boss has its own ramps (`Ramp(...)`) and one light story. Glow goes in `P_.glow`.
   Avoid flat fills: mottle, plates, cracks, edges. Every face gets painted, eyes especially.
4. **Motion.** One `_pose(...)` function returns a dict of turns from the bind (deltas) for every bone, with named
   parameters (lift, pitch, arms=dict(...), ...). Every clip and move is made of `_pose` calls and `_mix` blends.
   - **Everyday clips:** `idle` (8 s, many small things happening: breathing, looks, one gesture, a shift of weight);
     `walk`/`run` (with `r.walk_stride`/`r.run_stride`) or, for a floater, `fly`; `turn_l`/`turn_r`;
     `hurt`/`hurt_l`/`hurt_r`; `kneel` (broken); `rise`; `death` (about 5 s, a real performance).
   - **Moves:** `Move(id, cls, windup, active, recover, ...)` in ticks. `cls` is LIGHT, HEAVY, PERILOUS or NONE. Stages
     are `{"windup": [(f, pose)], "active": [...], "recover": [...]}`, f being 0–1 of the stage. Set `hit=[(bone,
     point, radius px)]`, `through=` (the follow-through pose), `kinds=`, `ability=` (the def's name), `origin=` (blocks
     x, up, forward, for casts), `advance=` (blocks, for a gap closer), `hold=` (ticks, HEAVY only) and `plant=()` when
     it has no planted feet. The intro move has `kinds=("intro",)`.
   - **The fight** (`FIGHT`): phases with pools. Each phase is `{"move", "weight", "when": near/far/behind, "then":
     [[...]]}`, `transition` names the move into that phase, plus `enrage`. `Kits.REQUIRED` needs every one of gap,
     combo, delay, area, ranged, behind and signature to be covered:
     - combo comes from `then` strings;
     - delay comes from any HEAVY move with `hold > 0` (only a HEAVY may hold);
     - signature means `signature=True`;
     - gap, area, ranged and behind come from `kinds`.
     `kit/checks/covcheck.py <id>`, after a single build, lists what is missing.
   - **Sounds** (`SOUNDS`, `MOVE_SOUNDS`): `(sound id, volume, pitch)` layers for tell_light, tell_heavy,
     tell_perilous, swing_light, swing_heavy, cast, impact, roar and step, plus any of the boss's own. **Check every id**
     by grepping the full name in quotes in `/Volumes/MEDIA/Awaken/.ref/src/net/minecraft/sounds/SoundEvents.java`.
     Make the impacts heavy; weak impact sound was one of Euan's complaints.
   - `design()` returns `{"rig", "plan": None, "moves", "fight", "paint", "scale", "half", "sounds", "move_sounds"}`.
     Add the id to `BOSSES` in `kit_bodies.py`.
5. **Check it.** All of these are pure Python and safe to run while Gradle runs:
   - `python3 kit_bodies.py motion DIR <id> --no-gif` is the meter. **Targets:**
     - kinks outside the strike: none over about 5° (4° is better);
     - held: under 15% on every move;
     - dip: under 40% on every string;
     - peak: roughly under 1500°/s.
     It prints every move and every `then` string.
   - `python3 kit/checks/windup.py <id>`: every LIGHT or HEAVY move with a hit needs a wind-up of at least 12 (the
     readability check).
   - `python3 kit/checks/floorsweep.py <id>`: no bad guard names and no clip more than 1.5 px into the floor. The build
     fails past 2 px.
   - `kit/checks/dipwhere.py <id> a-b`, `kinkwhere.py <id> seq` and `stillwhere.py <id> seq` show where a dip, a kink
     or a still spell is and which bones make it.
   - `kit/checks/extents.py <id>` gives the true-size extents and the `.size()` for DeepBestiary.
   - To look at it, use `BOSS=<id> YAWS=0,90,180,270 python3 kit/checks/rs_view.py out.png [clip t ...]` (four sides,
     a row per pose), or `pose_view.py` to look at `_pose(...)` arguments before they are in a clip.
     `kit_bodies.py lab DIR <id>` renders the body sheets.
6. **Size and SPEC.**
   - Set `.size(w, h)` on its DeepBestiary line from `extents.py`.
   - Write its ROLLOUT row: Today `kit`, Round `A, the <name>`, Body and Motion `✓ <hash>` once committed. Add the pick
     note under "The rounds so far".
   - Write its SPEC bullet at the end of §35.11, just before `## 39. Binoculars`, in the same shape as the Sovereign of
     Ash's: the body with cube and bone counts and the hitbox, then sub-bullets for its idle, walk and run, struck,
     broken and death, its moves, its fight and its meter.
   - Write player-facing text in plain natural speech.
7. **Build it.**
   - `python3 kit_bodies.py build <id>` builds one boss: rig, textures, moves and fight JSON. It doesn't touch index.json.
   - A full `python3 kit_bodies.py build` (run it in the background) rewrites index.json and **every** rig. Then run
     the rig-restore loop below, which puts back every rig whose lines only changed order.
   - Never run `kit_bodies.py build` while Gradle is running.
8. **Test it** with Gradle, always through the machine's slot so it doesn't fight other sessions. Run it in the
   background:
   ```
   SLOT_WHO=bosses /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build /Volumes/MEDIA/Awaken/.claude/worktrees/<yours>/tools/gradle.sh 1500 --no-daemon build runGameTestServer -Ptests=kit_,boss > <log> 2>&1
   ```
   The `kit_` and `boss` GameTests check every kit body (Kits.problems, the ground rule, coverage).
9. **Show Euan.** Render GIFs (`kit_bodies.py motion DIR <id>` without `--no-gif`, in the background), make a sheet
   (`cd DIR && python3 <repo>/tools/art/specs/kit/checks/gifsheet.py OUT.png 4 <gifs...>`), and send it with one GIF.
10. **Commit, two commits per boss.**
    - The first is "The <boss> to the Forge's bar: <the form>'s body and motion (SPEC §35.11)". It holds docs/SPEC.md,
      ROLLOUT.md (row at `kit | A, the X | — | — | —`), DeepBestiary.java, index.json, kit_bodies.py, `rigs/<id>.rig`,
      the textures `<id>.png` and `<id>_glow.png`, `kit/fights/<id>.json`, `kit/moves/<id>.json` and the boss's `.py`.
    - The second is "Boss tracker: the <boss>'s body and motion are in". It sets the row's Body and Motion to `✓ <first
      commit's hash>`.
    - End every commit message with `Co-Authored-By: ...` (see CLAUDE.md).
    - Push your own branch, never force. Dispatch CI with
      `gh workflow run gametests.yml --repo euansprr-dev/awaken --ref <branch>`.

The rig-restore loop. Run it from the repo root after a full build, adding each new boss to the list:
```
for f in ember_tyrant unit_zero drowned_archivist lantern_broker hive_regent abbot_of_frost mirror_knight the_sleeper sky_leviathan hollow_herald guardian_rim guardian_gardens guardian_choir guardian_orchard guardian_silent guardian_foundry guardian_inverted guardian_dreaming guardian_court guardian_seed mother_of_spores gearwright_colossus root_saint bell_warden glass_serpent sovereign_hunger sovereign_silence sovereign_sleep sovereign_machines sovereign_mirrors sovereign_storms sovereign_roots sovereign_ash; do p=src/main/resources/assets/awaken/kit/rigs/$f.rig; if git diff --quiet -- $p; then :; elif diff <(git show HEAD:$p | sort) <(sort $p) >/dev/null; then git checkout -- $p; else echo "$f REAL CHANGE"; fi; done
```

## What I learned the hard way (read before writing poses)

- **Coordinates.** fauna_rig model space is y-down. `_bone(parent, name, x, up, z)` places a bone at (x, -up, z). +x is
  the creature's left and -z is forward.
- **Turns.**
  - On a bone pointing forward (-z), +x turns it down and +y swings it toward -x (its right). On a bone pointing back
    (+z), +x turns it up.
  - On a left fin or wing (out along +x), +z turns its tip down, so a right one is mirrored.
  - A pose's `"pos"` y is up (+ raises).
  - Pose values are deltas from the bind, not absolute.
- **Clips add.** HeroModel plays each clip through vanilla `KeyframeAnimation.apply`, which adds. So the idle plays
  under everything, at weight 1 minus 0.55 × moving. A floater's `fly` plays always, at weight 1 and faster with speed.
  Keep `fly` a small zero-mean layer (a quicker ripple), never a pose. `walk` and `run` add on top when it moves on the
  ground.
- **The dip** is measured on the move layer's summed |angles| between one move's wind-up peak and the next one's. A
  swing between opposite extremes, like arms flung up and then whipped down, passes through zero mid-swing and reads as
  a dip. Fixes:
  - commit the wind-up early (a key at 0.2 already 0.55–0.7 of the peak);
  - put a mid-swing key where something else peaks, such as the crown fanning open as it comes down;
  - keep one big component the same sign through a whole string (curls stay curled, a fin stays flared).
- **The counter key.** `stage_clips` adds an anticipation key at 0.3 of the wind-up unless a wind-up key has f < 0.35,
  and it only touches bones named arm_, forearm_, hand_, chest, spine, head or neck. Always give an early wind-up key.
- **Lag.** Every joint below depth 2 trails by `core.BREAK` (0.018 s) a level. On long chains, like the Shadow's
  six-deep arms, that is 0.07 s at the tips. Active keys closer together than that bunch up and kink, so use one or two
  active keys, not three.
- **The floor pass makes kinks.** When a hit drives a limb into the floor, `core.ground_pass` turns it out tick by tick,
  and that shows as kinks in the recovery. Keep strikes a few px above the floor instead.
- **A living thing never holds still.** A `_drift` layer added under every move clip after `stage_clips` keeps held
  near 0% and reads as alive: arm tips stirring, a fin undulating, faded in and out at the clip's ends. It is in
  `guardian_seed.py` and `signals_shadow.py`. Re-key it every 2 ticks, not every tick, to keep the rig file small. Big
  rigs are up to about 1 MB.
- **An intro from the kneel.** With `"intro": {"pose": "kneel", ...}` the boss waits holding its kneel. Set
  `mv._first = <the kneel's last pose>` and stage it with the `_staged`/`_stage` wrapper in `guardian_seed.py` or
  `signals_shadow.py`, so the intro clip starts where it lies.
- **Stretches.** Scale tracks go on after `stage_clips`: `mv._scale` with `_move_scale`, or the Shadow's `mv._lance`
  for a tentacle that stretches while its club keeps its size (1/k on the child). Use "lin" interpolation when a
  scale passes near 0, or a curve overshoots and turns it inside out.
- **Spins.** For anything that turns more than half a turn, run the keys through `_unwrap` (see `sovereign_ash.py`).
- **Vanilla Catmull-Rom on uneven key times jumps in speed.** Bake clips to evenly spaced keys (the kit does) and keep
  key spacing even within a stage.
- **Hitbox.** Kit bodies render at true size. `.size()` × the def's scale is the hitbox in blocks, round the trunk, not
  the limbs.
- **The look after NeoForge clears it.** NeoForge clears a render state's data right after `extractRenderState`. So
  `MobsClient.looks()` puts each mob's look back with a render-state modifier per renderer class: KitRenderer,
  AwHumanoidRenderer and AwSkinRenderer as well as Hero, Wild and AwMob (commit bf0357bd). Without it, kit bosses lose
  their springs, breathing, blinks and glow in game. The mob-anims branch edits the same lines; on a conflict keep this
  branch's version whole.

## Rules of the house that apply here

- Never launch the game (`runClient`) and run no screenshot tours. Euan tests in game himself.
- Verify every Minecraft or NeoForge name in `/Volumes/MEDIA/Awaken/.ref/`. This is 26.3, not 1.21.
- Only touch the mobs module, the kit's files, docs/bosses and SPEC. New behaviour goes into SPEC in the same commit.
- Every new awaken item or code-placed block needs a recipe, a loot table entry, or a `Sources.declare(...)` line in
  `core/test/LedgerSources.java`. The `journey_ledger` GameTest checks it. Bosses so far added none.
- Merge `build/awaken-v1` into your branch before handing back. In an app-made worktree use the sync tool, not
  `git merge`. Run the full GameTest suite once at hand-back; CI runs it on every push.
- Use zsh with quoted heredocs, no `timeout`, and absolute paths. Make every cube size a whole pixel.

## Known gaps to mention at hand-back

- Small floor dips under the build's 2 px limit:
  - the Forge's head and walk toes;
  - the Abbot's spikes;
  - the Archivist's death;
  - the Mirror Knight;
  - Unit Zero;
  - the Orchard King;
  - the Vanity's lunge back foot (1.8 px).
- Nothing since the first three bosses has been seen in game. That includes the look fix, the sounds (chosen on paper)
  and every pose.

## After the 41: the Gate Worlds' creatures (queued, Euan decides the order)

These come from the gate-worlds session (their branch `claude/gate-worlds-plan-5546fb`, SPEC 38.13).
- **How they spawn:** `MobHooks.spawnCreature(level, "awaken:<id>", pos, contentLevel, gateId)`, with pos the spot it
  stands on after its entrance, which our entrance clip plays it in from.
- **How they're registered:** as floor monsters with room key -1. They set the attachment `awaken:gate_world_look` to
  `awaken:<id>` and give a keepsake on death.
- **Numbers:** their 2× health is only a fallback; our body keeps its own numbers.

In the arenas below, "r" is the clear floor all round its spot and "ceiling" is the headroom.

| Creature | World | Arena | Entrance |
|---|---|---|---|
| the_ringer | the Bell Crypt (four worlds) | must fit 6-high passages; the halls are r 6–7, ceiling 11–13, knee-deep water in half | enters on foot when the 8th bell is struck |
| slag_wyrm | the Cooling Sea | lava sea; crust islands, ceiling 32–38 | out of the lava |
| queens_choir | the Swarm | hive cavern r 6, ceiling 18 | on the floor |
| grove_walker | the Great Tree | r 6, ceiling about 20 | keepsake: the Grove Walker's Branch |
| bookworm | the Inkwell | a sea of ink with paper bridges, ceiling 29–33 | out of the ink |
| clock_keeper | the Stopped Hour | r 6, ceiling about 20 | man-sized is fine |
| sand_walker | the Mirage | r 12–19, an invisible lid 18 up | "The sand stands up in the shape of a man." |
| snow_monk | the Still Snow | r 12–20, lid 20 | "A monk made of snow stands up out of the drift." |
| the_reflection | the White | a plain 64+ open | "It's you, and it's smiling": ideally the nearest player's skin |
| the_glitch | the Simulation | r 6–11, ceiling 19 | flickers when looked at |
| sleepwalker | the Sleeper's Dream | furniture islands r 4–10 over a void | "much bigger than you" |
| storm_roc | Above the Clouds | islands r 4–6, a lid 20 up | circles and dives under the lid |
| the_watcher | the Last Room | small dark rooms r 5–12, ceiling 6–13 | "The door opens on its own and it stands in the doorway." |

`cloud_whale` is a spectacle, not a fight, and they spawn three of them. It follows a 16-point ring 84 blocks out and 36
up, given as persistent data:
- `awaken_path`, a long[] of `BlockPos.asLong`;
- `awaken_path_loop`;
- `awaken_keep_off`, 32 blocks.
