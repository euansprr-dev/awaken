# Creatures hand-over: every remaining custom mob to the Fresh Animations bar

Written 2026-10-29 by the mob-anims session (branch `claude/mob-anims-6d4ff0`, merged into build/awaken-v1 as `a093757a`) for the next
session, on another account, that finishes the job. You start with none of that session's memory. Everything it knew
that isn't already in the code is on this page. SPEC §23 (the life layer, near line 5608) and §23.1 (Motion: the mechanism, then
one bullet per finished creature, from near line 5705) are the record of what's built. Read this page first, then §23.1 end to end, then the
files named below.

## What Euan asked for

- **The brief (29 Oct):** bring the animations of all custom mobs, but not bosses and not people, to the level of
  Fresh Animations (FA). Build on the life layer, the hero rigs and the Creature Kit. Prove each batch with CI tour clips
  side by side with FA, and record every creature's motion in SPEC §23.
- **His verdict on the first mp4s:** "this is still not on the level of fresh animations… the cow's legs… aren't even
  on leg! And the animations themselves are FAR from on par with fresh animations."
  - The lesson: the session had reviewed small, distant shots and judged them generously. Judge close up, at leg
    height, frame by frame. Assume he will see every flaw you let through.
- **His verdict on the 2fa13502 mp4s:** the Drip Lurker "looks incredible" and the wickling is "pretty good". He also
  listed six problems, all fixed in `fca4a964`/`5355d925` and seen fixed in CI tours:
  - the Briar Sow's head snapped round almost 180°, and "make sure that doesn't happen for any mob";
  - the ram's bottom half came off its body;
  - the weaver queen turned on the spot without moving its legs, and glided for most of its walk;
  - the thousandfoot glided, barely moving its legs;
  - the hushling's front legs (its hands) went under the floor and didn't move;
  - the pig's legs came apart. That pig is FA's reference pig (vanilla under the pack), not ours.
- **His standing rule for look work:** anything that looks vanilla reads as an add-on. Every creature should move like
  itself, not on one shared cycle.

## The bar (what FA does, learned from its files, nothing copied)

FA 1.11.1 is at `/Volumes/MEDIA/Awaken-play/run-pack/resourcepacks/FreshAnimations_v1.11.1_26.1+.zip`.

**It is read-only and not ours.** Learn the principles from it: unzip it into your scratchpad and read the `.jem`
animation expressions. Never copy its files or expressions into our repo, because the repo is public. Never write
anything under `/Volumes/MEDIA/Awaken-play`.

What the bar means, and what we built to meet it:

- **Stride and feet:** stride comes from ground speed, and feet stay where they land.
- **The body rides its feet:**
  - it bobs as the feet lift;
  - it rolls off a lifted foot;
  - it pitches with the ground.
- **The head:**
  - it is held level against the gait;
  - it leads the body into a turn;
  - the body follows the look a little.
- **Turning:** a turn on the spot steps the feet. It must not swivel the body on frozen legs.
- **Attacks and hits:** attacks have anticipation and follow-through, and a hit jerks the body over planted feet.
- **Landings, idles and deaths:**
  - a landing squashes by the height of the fall;
  - idles have variety;
  - deaths curl and twitch.

Where we are still honestly behind FA:

- **Trots:** FA's trots swing big, about one cycle a second, and its feet slide. Our feet stay planted, so
  short-legged bodies patter. A longer pig run stride fails the vault check.
- **Body sway:** FA's cow sways its body more continuously.
- **Idles:** ours (graze, look, sniff, scratch) are busier than FA's.

## Where it stands (merged: build/awaken-v1 `a093757a` has everything below)

`ec1d3b97` has green GameTests (run 40861874180). Its tours, all reviewed: crawlers 40861893343, walkers 40861937903,
beasts close-up 40861988876. The orchestrator merged it into build/awaken-v1 as `a093757a` on 29 Oct, after reviewing
the walkers and beasts shots. This page and `tools/creatures/` came in a later docs-only commit. The old
session's running notes, batch by batch, with every tour run id: `/Users/euanspencer/Library/Application Support/Claude/side-session-notes/local_460b18dc-28ec-4e59-8e59-ed73535cf750/mob-anims.md`
(local to Euan's Mac; read it if you can).

| Done | Creatures | Commits |
|---|---|---|
| B0 | Drip Lurker | 78911c01, 6eabe46d |
| B1 crawlers | form spiders 2–5; thousandfoot, wickling, hushling, weaver queen | 6eabe46d, f244fbb9 |
| B2 four legs | form pig/cow/sheep/chicken 2–5 | 79b388a9, 20bae3ae |
| B2 four legs | Briar Sow, Old Antler, Stonehorn | b0547a88 |
| B2 four legs | rigid legs; FA's walk read close up | 79b60193, 1cc729fd |
| B2 four legs | starts without splits; turns that step | c2189ebe, 657e5a6f |
| B3 fliers/swimmers | bank, pitch along the path, ride the stroke | d66cebbf |
| B3 fliers/swimmers | the Hollow Owl's wingbeat (yoke bone) | 6dfbc1f2 |
| The look reaches the model | NeoForge cleared it; see Gotchas | 2fa13502 |
| B4 hand-built walkers | Frost Stag, Mossback, Crested Basilisk, Glass Scarab, Shroomlet, Listener | 78abc204 |
| Euan's fixes | neck reach, hanging plates, wardens' feet, hushling walks on its arms | fca4a964 |
| Euan's fixes | hushling elbow, scarab splay | 5355d925 |

## What's left, in the order I'd do it

Each row is a batch, or part of one. For every creature: profile it, test it, film it close up beside its nearest FA
animal, review it honestly, make the mp4s, write its SPEC §23.1 bullet. The workflow is below.

| # | Creature(s) | Body (where it's defined) | What it needs |
|---|---|---|---|
| 1 | **Mire Mother**, **Mycelord** (named beasts, Tier.WARDEN, in scope like the sow) | hero rigs: `tools/art/specs/fauna_heroes.py` → `HeroData.java`, drawn by `HeroRenderer` | No motion profile yet. The Mire Mother is a toad as big as a hut. Her forelegs are ARM-role bones, so use the gait's `"arms": true` (built for the hushling). It needs a heavy, squat, frog-like stance and walk, with weight that lags. The Mycelord is a tree-tall mushroom that walks: a heavy biped. Both are big and slow, so the vault and slow swings matter. Compare with FA's frog and the vanilla warden or iron golem under FA. |
| 2 | **Mudskipper** | hand-built `WildModels.Lizard` at 0.75 scale (same model as the basilisk), `AwMobRenderer` | Hand-built bodies are profiled by **creature id**, so add a `mudskipper` entry in `motion.json` and a `HAND_FEET` entry in `LifeTests`. At 0.75 scale a planted trot steps 8–9 times a second, which is too fast. Mudskippers crutch on their fins in skips, so it probably needs a skip or hop pattern (see 4). |
| 3 | **Brook Otter**, **Tidesteed** | fauna3: `tools/art/specs/fauna3.py`, `WildRenderer` | The otter has two-bone hind legs and one-bone front legs. `LegRig.of` takes one-bone legs only on a `knees: rigid` gait, so mixed legs need per-leg rigidity (design below). Its land gait is a bound: hind pair together, front pair together. The Tidesteed (a mount) has one-bone legs, so a rigid gait like the stag. Riders drive it, so check that the gait follows the ridden speed. Both already have swim (`fly`) entries; re-check those close up. |
| 4 | **Chime Cricket**, **Scree Marmot** | the cricket is fauna3 (hind legs only); the marmot is hand-built `WildModels.Marmot` with 2 px legs | Planted legs would step 8–9 times a second, so both are still on their own clip swing. They need a new **hop or bound pattern in `Legs`**: all feet planted, the body launches on an arc and lands, and the feet re-plant on landing. FA's rabbit is the reference. For the cricket, also the chirp's leg rasp. The marmot's legs must at least not skate: match the swing frequency to ground speed. |
| 5 | **Gloam Snail**, **Sporeback Snail**, **Breathbell** | the snails are hand-built `WildModels.Snail` with no legs; the Breathbell is a fauna3 jellyfish | No legs, so no gait. The snails need a sole wave whose speed follows ground speed, body stretch and contract with travel, shell sway, and eye stalks with lag (their blink is the stalk squash). The Breathbell needs a jellyfish pulse: the bell contracts, thrusts, then glides, with tentacles trailing on springs. It has no `motion.json` entry yet. |
| 6 | **Fliers and swimmers, a close re-check** | bogwick, Empress Moth, Plumed Coil, Dune Wyrm, glimmerfin, Lantern Maw, lumen moth, petalwing, dune ray, deep eel, tide eel, cord eater, and the otter's and Tidesteed's swims | B3 gave them bank, pitch and the stroke. Until 2fa13502 the layer never actually drew on them (see Gotchas), and since then only the owl has been checked close up. Others were only seen at a distance (flyclose 40846570969: "sane"). Film each close beside FA's parrot, bee or cod (`tools/creatures/tours/flyclose.py`) and fix what the clips show. |
| 7 | **Humanoid forms**: zombie, skeleton, creeper, levels 2–5 | `living` module: `living/client/forms/FormBodies`, `FormRenderer`; `living/forms/FormData` | One-bone legs; FA's humanoids bend at the knee. First read `docs/forms/PLAN.md`: a forms rebuild was planned (session local_79e529c7), so these bodies may be replaced. Ask Euan or whoever merges before animating bodies that may be thrown away. |
| 8 | **Other modules' animals** | `wildlife` (fox, rabbit, squirrel, frog, songbird, crow, owl, raptor, gull, duck, heron, woodpecker): `wildlife/client/WildModel`, `WildRenderer`; `citizens` (city animals): `citizens/client/CritterRenderer`, `CityFaunaData` | They use `CreatureLife` (SPEC §12: breath, look, springs), but the motion layer needs a motion profile id and the inputs `grounded`/`legsFree`/`gaitRun`/`stalking`, which `CreatureLife.rig(root, temperament)` can't take. That means an additive overload on a §12 API. It's another module's code, so coordinate before touching it. |
| — | **Vanilla-bodied fauna**: gloomstalker (fox), crag ram (goat), canopy prowler (ocelot), frostfinch (parrot), bog toad and dart frog (frog), the drones (bee), bloom sprite (allay) | `AwMobRenderer` on vanilla model layers | Presumably FA animates these through its model layers. **Unverified:** film one beside its vanilla twin under FA. If FA doesn't reach our entity types, raise it. |
| — | **Seep heart** | fauna3, rooted | No gait by design. Leave it. |

Out of scope: bosses (the kit bosses are the boss plan's, `docs/bosses/HANDOVER.md`) and people.

## How the motion layer works (read the code; these are the pointers)

- **Data:**
  - `src/main/resources/assets/awaken/life/motion.json`: by rig id, or by **creature id** for a hand-built body.
    - `gait`: pattern, duty, stride, lift, reach, settle, crouch, regrip, swing, bob, roll, lag, lead, rock, knees
      (auto, forward, back or rigid), shoulder, rearPairs, arms.
    - `body`: headLevel, twist, slope, land, shift, nod, still, creep.
    - `fly`: flight and swim.
    - The file's `about` text and SPEC §23.1 "Data" define every field.
  - `creatures.json` gives creature → temperament, and each temperament's springs, fidgets and `neck` (the head's most
    turn from the body, in radians).
- **Code:** `src/main/java/dev/awaken/mobs/client/life/`
  - `Life`: the per-creature state. It ticks in extractRenderState: the head look with the neck clamp, fidgets,
    springs, and the foot planner.
  - `LifeRig`: applies the state in setupAnim. It binds bones by role, through `LifeBones` by bone name. `rootCarries`
    lets the root part carry a hand-built body whose legs don't hang off its body bone. `hinge` handles hanging plates.
  - `MotionProfile`: parses `motion.json`.
  - `LegRig`: plain data, server-testable. It finds the legs (one-, two- or three-bone), works out their rest, homes,
    ranks and phase, and solves them: two-bone IK through `solve`, a rigid leg through `solveRigid`. `sink` gives how
    far the hips drop for a far foot (the vault, at most a quarter of a leg).
  - `LegParts`: poses the ModelParts last, blended by the feet's weight.
  - `Legs`: the foot planner. It plants feet in the world, runs the gait clock by distance and turn arc, takes steps,
    settles, re-grips, slides a planted foot past SLIP, and carries the body.
  - `LegGround`: the ground probe.
  - `Flight`: flight and swim.
  - `LifeModel`: the hand-built models' base. `legsHeld` says when a pose (rest, burrow, sprint) keeps its own legs.
  - `CreatureLife`: the API for other modules.
- **Who feeds the inputs:**
  - `WildRenderer` (mobs.client.anim) and `HeroRenderer` feed `grounded`, `legsFree`, `stalking`, `gaitRun`.
  - `AwMobRenderer` (hand-built bodies) feeds `grounded`, `legsFree`, `gaitRun`. `gaitRun` comes from ground pace, about
    1.6·(speed attr·modifier)² blocks a tick. Fauna wander at modifier 0.7 (neutral) or 0.9 (ambient).
  - `living`'s `FormRenderer` feeds the forms.
- **Bodies:**
  - Hero and fauna3 rigs are generated. To change a body, edit its spec, then run
    `python3 tools/art/specs/heroes.py build` (heroes) or `python3 tools/art/build_all.py fauna3` (textures plus
    `Fauna3Data.java`).
  - Hand-built bodies are Java: `FaunaModels`, `WildModels`, `AwModels`.
  - If you change a hand-built model, change its `HAND_FEET` spec in `LifeTests` too; it mirrors the model.

**Mixed legs, the design I sketched (needed for the otter):**

1. In `LegRig`, keep a `boolean[] stiff`, where `stiff[i] = rigid || knee[i] < 0 || hides[i] < 20°`.
2. Make `of()` accept a one-bone leg on any gait.
3. Use `stiff[i]` where `rigid` is used today:
   - `LegParts.apply`: hold the knee at rest and call `solveRigid`;
   - `Legs`: the SLIP slide clamp and the overshoot of a rigid swing;
   - `LegRig.sink`;
   - the test's rigid branches, through `legs.rigid(i)`.
4. I built and reverted this on 29 Oct because nothing needed it then. It is about 20 lines.

## The test

`creature_motion_feet` in `src/main/java/dev/awaken/mobs/entity/LifeTests.java` checks every profiled leg:

- it reaches its stance;
- IK accuracy, and the Euler round trip;
- knee direction;
- a knee that can't hide a bend must be rigid;
- the planted sweep of the walk and run strides stays within the vault;
- a rigid leg's top stays inside the body;
- a walk trial without splits: stand, sudden walk, sudden run, stop, turn.

Its asserts count legs (`checked`, `rigid`, `handLegs`), so raise them when you add bodies. The other tests are
`creature_motion_flight` (fliers) and `creature_life_binds`.

To pick a stride, use `tools/creatures/stride_est.py`, which gets you near the limit. Then let the test decide, and
choose about 0.9 of the most it allows.

## The workflow, per batch

1. **Branch.** Make a fresh branch from the latest build/awaken-v1. Push only your own branch, and never force-push.
2. **Read the body.** Read its model or spec and its current clips, and look at the FA animal you'll compare it with.
3. **Make the change.** Profile, or code what the body needs. Client code only. No allocation per frame, so nothing
   new in `tick` or `apply`. Verify every Minecraft or NeoForge API against `/Volumes/MEDIA/Awaken/.ref/src`,
   `.ref/neo` and `.ref/assets` before using it: 26.3 is not 1.21.
4. **Test locally, through the machine slot.**
   `SLOT_WHO=<you> /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 900 runGameTestServer -Ptests=creature_,life_`
   takes about 30 s. Use the same wrapper for `compileJava` and `build`. Never run `runClient` without asking Euan.
   Never build inside `/Volumes/MEDIA/Awaken-play`.
5. **Commit and push.** Commits are small and end with the `Co-Authored-By` line from CLAUDE.md. Push.
6. **Dispatch GameTests.** Pushes don't trigger them:
   `gh workflow run gametests.yml --repo euansprr-dev/awaken --ref <branch>`. A newer dispatch on the same branch
   cancels the older run.
7. **Write and run a close tour.**
   - Write a tour generator in `tools/creatures/tours/`. Copy `b4walk.py` (ground walkers) or `flyclose.py` (fliers).
     Each body is summoned NoAI and walked by `/awaken admin stroll <targets> <blocks/tick> <ticks> [deg/tick]` (each
     call queues a leg). A camera at leg height rides beside it with `glide`, and `cmd awaken ascent pilot rec <name>
     <ticks> <every>` records clips. Put the vanilla FA animal first on the same course; `# ci knobs: PACK=perf,feel`
     loads FA on the runner.
   - Generate it: `python3 -I tools/creatures/tours/<x>.py tools/autopilot/mob-anims-<x>.txt`, then commit and push.
   - Run `tools/tour-ci.sh tools/autopilot/mob-anims-<x>.txt` in the background. It refuses unless your pushed branch
     is at HEAD, and takes about 10–13 minutes. Tours run on GitHub's macOS runners, never on the Mac.
   - Shots land in `run-autopilot/screenshots-ci/<x>-<run>/`. Fetch clips with the `clips:` line it prints
     (`gh run download <run> -R euansprr-dev/awaken -n tour-mob-anims-<x>-clips -D run-autopilot/clips-ci/mob-anims-<x>-<run>`).
     If a download fails with "file exists", delete the folder and run it again.
8. **Review frame by frame.** See the next section.
9. **Make mp4s for Euan.** Run `python3 -I tools/creatures/mp4s.py run-autopilot/mp4/<sha> pair <before clip dir>
   "Before: …" <after clip dir> "Now: …" <name>`. It plays at game speed, side by side, labelled. Send them with
   SendUserFile and say in plain words what changed and what didn't.
10. **Record it.** Write a SPEC §23.1 bullet per creature, with numbers and why, in the same commit as the change.
11. **Hand back.** Report the tip SHA, a green GameTests run on that exact SHA, the tour run ids and what you reviewed.
    Report to whoever merges (on 29 Oct that was the orchestrator session; on another account, Euan will say).
    - Before handing back, merge the base branch if it moved. In this app use `mcp__ccd_host__sync_with_base_branch`,
      not git merge.
    - If a shared file conflicts (e.g. `MobsClient.looks()` with the boss work), keep the other side's whole list and
      re-apply yours.

## Reviewing honestly (where the first round went wrong)

- **Look close, at leg height.** Use the clips, not the stills.
  - `tools/creatures/track.py` makes a contact sheet that follows the creature. Use `MODE=dark` or `MODE=white` for
    black, purple or white bodies.
  - `tools/creatures/sheet.py` uses fixed crops.
  - Look at about 8 frames a row across the walk, the run, the stop, the turn and the idle.
- **Check each of these, every time:**
  - feet stay on their legs (nothing splits at a joint);
  - legs move with ground speed (no gliding);
  - no spinning on the spot without stepping;
  - nothing goes through the floor, and no hands or feet sink;
  - no plate or skirt lifts off the body;
  - the head never snaps. `tools/creatures/snap.py` measures how fast a region changes: the sow's old snap was a spike
    of 23 against a median under 1, and the fix brought the maximum down to 8 with no single-frame jump;
  - starts, stops and pace changes have no splits;
  - idles vary;
  - deaths curl.
- **Compare with FA in the same clip** (the vanilla twin walks first), and write down where we're still behind.
- **Don't call it fixed from a distant shot.** Euan watches the mp4s full screen.

## Gotchas that cost time

- **The look and the render state.** NeoForge clears a render state's data right after `extractRenderState`, so a
  look set there never reaches the model. `MobsClient.looks()` puts it back from a render state modifier on every
  renderer class: Hero, Wild, AwMob, AwHumanoid, AwSkin, Kit. If you add a renderer class that doesn't extend one of
  those, register it there too. Until 2fa13502 no Hero, Wild or hand-built body drew its life layer, so B0–B3's
  earlier "it works" reads were clips only.
- **Wardens are named beasts.** They are Tier.WARDEN, which `AwMobRenderer.boss` counts as a boss, but they are in
  scope (sow, antler, ram, queen, thousandfoot, the Mire Mother and the Mycelord).
- **Hand-built bodies are root-motion bodies.** Their legs plant only through `LifeRig.rootCarries`: the root part
  carries dip, pitch, roll and gait, rotated about the body pivot.
- **Hanging plates.** A DANGLE spring whose cubes form a plane with height swings only about the edge it hangs from. If
  a body's own clip pitches a skirt about a corner, fix the clip. The Stonehorn's skirts were fixed in the spec, and
  you can tell when it's wrong because a gap shows at the back.
- **Straight joints.** A straight limb whose upper block ends exactly on the joint's pivot opens a wedge at any bend.
  Either make it rigid, or run the upper block about a pixel past the joint over a thinner lower one. The hushling's
  arms got the second fix.
- **Splayed legs and stride.** A rigid leg splayed fore or aft can barely reach further along its own direction. The
  scarab's front legs at 0.5 rad failed its stride, and 0.25 rad passes.
- **NoAI creatures in tours** stand still. Use `/awaken admin stroll`; a tp is one step per command round trip. A NoAI
  flier perched mid-air until a FaunaMob fix.
- **CI runners.** They sometimes lose the GPU (VK_TIMEOUT, Lost VkDevice) partway through; rerun. They also start from
  a fresh game folder, with Far Sight off.
- **mp4 framing.** The CI frames are 854×480. A small creature filmed from 4 blocks away is tiny, so frame the camera
  by body height (`b4walk.py` does: distance = max(1.6, 2.4·height + 0.8)).

## Rules (from the brief and CLAUDE.md)

- **Your files:** only the mobs module's package and resources, `docs/SPEC.md` §23, the art specs, `tools/creatures/`
  and `tools/autopilot/mob-anims-*`.
- **Shared code:** for anything shared with the boss work (`mobs/client/kit`, Life/LifeRig as the kit uses them) or
  another module, tell whoever merges before you change it.
- **One test per mechanism**, run locally on your prefixes. CI runs the full suite.
- **No sub-agents.** Euan wants each builder to do the work itself.
- **Plain words for Euan.** Read what you write aloud before you send it.
