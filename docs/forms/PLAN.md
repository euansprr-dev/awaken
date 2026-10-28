# Forms, rebuilt (plan, 28 Oct 2026)

Euan's planning process (`.claude/PLANNING-PROCESS.md`). Plan only: nothing in the mod has changed. Approval page: https://claude.ai/artifact/3uW5ZCauPxR4JB2sXggnKd (private until
shared). Builds on SPEC §21.11 (the forms), §23 (creature life), and lines up with the boss plan (`claude/boss-plan-f38a45`,
`docs/bosses/PLAN.md`, not approved yet).

## 1. What he needs

**Job.** Walk into deeper land and *see* that it is more dangerous before anything hits you. A levelled zombie, pig or
spider should look, move, sound and fight like something the land has changed, more so at every rank, D to S.

**Pain (his words).** "Very rushed", "not correctly aligned", "glowing eyes look super basic... derpy".

**Wanted.** "AAA", "the right animations and the right styles", "more and more epic as you go up", "magical, satisfying,
immersive", "possibly even the attacks and style of attacks".

**Strong result.** From 30 blocks you can tell D from B from S by silhouette and posture alone. Up close nothing floats,
stretches or crosses a face. Eyes sit in sockets and read as menace, day and night. Every move is acted out by the body
before it lands. Each species climbs its own ladder (a boar, an aurochs, a ram, a ghoul), sharing one style.
**Weak result.** Nicer textures and tidier boxes on the same vanilla bodies; moves still shown as dust rings.
**Unacceptable.** Anything floating or clipping at any pose; a frame-rate drop in a night of S land; armour, held bows,
saddles or wool lost; a desync between what you see and what hits you.

**Constraints.** No mixin (none needed). Multiplayer-safe. SPEC header budgets. No Mojang assets committed. Fresh
Animations optional. E land stays vanilla. Damage caps of §21.11 decision 19 (at most 1.3× a normal blow) stand.
**Non-goals.** New species (enderman, slime, witch, drowned: a later pass), bosses (boss plan), the rank mix and region
levels (unchanged), the plate.

## 2. Audit (verified)

| Piece | What it does | Evidence | Verdict |
|---|---|---|---|
| `FormLayers` | One render layer per species draws the vanilla body again with an overlay texture, a second vanilla-class model holding the kit's boxes, and the vanilla body a third time with a glow mask | `living/client/FormLayers.java` | replace (keep as LITE fallback, eyes only) |
| `FormKit` + `kit.json` | Kit boxes on vanilla parts, animated by the vanilla model class | `FormKit.java`; 511 cubes | retire |
| `forms.py` | Paints kit boxes face by face with random ±1 noise on a 4-step ramp; overlays scatter moss specks and slashes; the glow mask lights the vanilla eye pixels and 2-px-wide "cracks" | `tools/art/specs/forms.py` | replace by rig specs |
| `forms_geom.py` | Validator: attached, off the face, seen, in reach, at rest only | file | keep, extend to every key pose |
| Moves | One telegraphed move per rung; the creature stands still while dust appears | `Moves`, `MarkGoal`; SPEC §21.11 Build 2 | keep the rules, add the body |
| Voices | Pitch down, volume up by rung | `MarkEvents` | keep, add a voice layer |
| Hero rigs | Rig + clips + painter in Python; `HeroRig`/`HeroModel` bake to vanilla keyframes; `LifeRig` breath, blinks, springs | `fauna_rig.py`, `HeroModel.java` | reuse |

**Reproduced** (offline preview of the live textures, and the CI tour 40454849731's in-game shots):
- Eyes: every rung lights the vanilla 2×1 eye pixels flat, full-bright, in the rank's hue: a visor, no socket, no depth.
- 206 of 511 kit cubes have fractional sizes (helm 3.4 high, pauldrons 5.2 wide...) while their textures are painted at
  rounded sizes: stretched, off-grid pixels on a body whose pixels are whole. This is the "not aligned" look.
- Silhouette and posture never change: a rank only stacks boxes (a fence of planks on the pig, a bucket helm on the zombie).
  At 20 blocks the six pigs of the tour read the same.
- The chest "veins" light as 2-px-wide blobs that read as paint splats (zombie, skeleton, creeper at B to S).
- S "floating shards" read as debris. Moves have no body: the creature stands still for 0.7-1.2 s.

**Root causes.** (1) A layer can only add to a vanilla body, never change it: no posture, proportion, jaw, limb or real
animation. (2) Art made as boxes with noise, not designed bodies. (3) The eye is a texture colour, not a lit, recessed eye.
(4) Moves are server rules with no client presentation. Fixing (2)-(3) alone gives the "weak result" above.

**Checked, not a cause.** EMF/Fresh Animations: Euan's play copy has neither (mods folder, `resourcePacks:[]`), so the
kit is not drifting from a Fresh Animations body. If he adds them later, the new path is unaffected (our bodies).

## 3. Options

A. **Repair the kit.** Whole-pixel boxes, real shading, socket eyes, fewer bigger pieces. A day. Still vanilla posture,
   still a standing-still tell. Gives the weak result.
B. **Form bodies (recommended).** A formed creature wears a rigged body of ours, one rig per species family whose rungs
   reshape it (posture, proportions, growths), drawn instead of the vanilla body. Built on the hero rig pipeline, acted
   out with clips, with life (breath, blinks, springs). Same entity, AI and drops.
C. **New entities per rank.** Breaks "vanilla mobs themselves level up", spawns, farms, other mods' hooks. No.

## 4. Design

**Style rules (one look across species).**
1. *Posture and mass tell rank before colour.* D alert, C wary, B hunched and heavy, A massive, S tall and still.
2. *Growth in the creature's own material* (bone, horn, hide, carapace, matted wool), modelled at true size, never by
   scaling pixels. The land's crystal, in the rank's colour, only at A and S, as the signature.
3. *The land's light lives inside:* eyes deep in sockets, thin seams, a heart. Never painted on a surface.
4. Euan's placement rule, checked at every key pose: attached, off the face, seen, in reach.

**Eyes.** A dark socket under a brow (modelled as an inset, not painted), a one-pixel hot core set toward the nose, a rim
of the rank's colour. A small additive halo (two quads per eye, the Gate Rig's `rig_light` pipeline, within 48 blocks)
that is faint at noon and strong at night; Lens's bloom takes the core. The eyes narrow and flare in a wind-up (the
first tell), blink with the life layer, dim when hurt, go out over the fall. S only: a short light trail when the head
turns fast at night.

**Ladders.** Zombie → ghoul (hunch, open jaw, long clawed forearms, ribs, knuckle-walk at A, crystal crown and grave
mantle at S). Skeleton → bone knight (heart light in the ribs, fused plates, bone cloak, horned skull). Spider →
brood stalker (lit sac, plated back, bladed legs, eye crown). Creeper → bloom (frills that flare when it swells, light
running to its core). Pig → boar. Cow → aurochs. Sheep → ram. Chicken → fighting cock, then a cockatrice stance.
Husk, stray, cave spider and mooshroom are paint variants of their family's rig; biome family (green, sun, frost) stays.

**Fighting.** Every rung move gets `<move>_windup`, `_active`, `_recover` clips (the boss kit's names) and is acted out:
the body gathers, strikes, recovers (the opening). Tell marks on the ground drawn in our look, coloured by what you
should do (Arms' classes: LIGHT white, HEAVY amber, PERILOUS red, GRAB violet), not by rank. From B the normal attack
is a two-hit (each hit at most a normal blow, the second dodgeable); an alpha roars once when it first sees you. The
creeper gains its moves (C sidestep before the swell, B a delayed seed, A sets nearby fuses, S pulls you in while it
swells; explosion stays vanilla). Damage caps unchanged.

**Sound.** A voice layer per family (breath, growl) under the vanilla voice, deeper by rung; own tell and impact sounds
(`tools/art/specs/audio_living.py`).

**Presence (A, S, restrained).** A few motes drifting off the body in the land's style, heavy footfalls at S.

**How it is drawn.**
- `RenderLivingEvent.Pre` (cancellable, verified in 26.3) for a formed creature whose body is ready: cancel, then submit
  our per-species `FormRenderer` (a `LivingEntityRenderer` of that entity's own render state, built in
  `EntityRenderersEvent.AddLayers` from `getContext()`), so the vanilla extract fills the state and vanilla's layers
  are reused: held items and armour (humanoid rigs keep the six vanilla parts as their core), pig saddle, sheep wool
  (colour, sheared), charged-creeper shell. Name tag, fire, hurt flash, invisibility, outline, death fall as vanilla.
- The body: `HeroRig` data from the form specs, `HeroModel` unchanged (boss plan: do not fork its graph), `LifeRig`
  on top. Per-creature state kept in a client map by entity id, created once, dropped on leave.
- **Cue**: a payload to trackers when a move winds up (`entityId`, `moveId`, `startTick`, `holdTicks`: the boss kit's
  three fields, so one client type feeds both); late joiners compute the phase from `startTick`.
- **True size**: the rig's size by rung sets the hitbox through `EntityEvent.Size` (verified, already used by the
  gates); `refreshDimensions` when the form is set. Both sides compute it from the synced form.
- **Fallback**: no rig, a rig that failed to load, or setting LITE: the vanilla body with the new eyes only (no kit).
  OFF: vanilla. Game rule `awaken:wild_forms` unchanged.

**State.** The `wild_form` attachment is unchanged (rung, family, marks): no migration. Rigs and clips are generated
data (`HeroData`-style, schemaVersion). Nothing new is saved.

**Failure register (main).** Rig missing → fallback, logged once, test. A mod already cancelled the render → we leave it.
Riders (jockeys) → seats follow the new dimensions, test. Baby → no form (as now). Shaders (Iris) → halos may not show,
the eyes still do. Cue lost → the body plays no wind-up, the ground mark and sound still tell (server unchanged).

**Performance.** Rig budgets: at most 30 bones at D, 60 at S; no per-frame allocation (state per entity reused). Springs
and fidgets off past 32 blocks (life layer), growth details under 1 px hidden past 48, halos within 48. Server: one cue
packet per move, dimension refresh once per form. Baseline not measured yet. Gate: 60 S zombies and 30 B animals in view
on the Mac stay within 15% of the same scene with forms OFF (orchestrator's perf tour).

## 5. Demonstrations
Offline, scratch only: a zombie form body at D, B and S on the hero toolkit, idle, night and wind-up; an eye close-up,
now and proposed; the live forms sheet; the CI tour shots. The prototype's art is rough (an hour) and scales the S body;
the real bodies are modelled at true size and painted with the hero painter. It shows direction, not finish.

## 6. Delivery
- **F0 Foundation and the zombie (with husk):** renderer swap, cue, true size, eyes (both paths), class-coloured marks,
  validator at every key pose, zombie D-S body, clips, moves acted out, two-hit from B, voice layer. Hand back.
- **F1 Hostiles:** skeleton and stray (bow draw, held items), spider and cave spider (crawler, jockeys), creeper and its moves.
- **F2 Animals:** pig, cow and mooshroom, sheep (wool, shearing), chicken.
- **F3 Polish:** presence, LOD, trails, retire `FormKit`, `kit.json` and the 295 old textures; perf tour (orchestrator).
Each phase: CI green, previews (four angles and every key pose per rung) reviewed by me, then hand back; the orchestrator
may run `forms-parade` on CI; Euan tests in game. Rollback: setting LITE/OFF per player, per-species fallback, git revert.
Tests (one per mechanism): `forms_body_cue`, `forms_size`, `forms_fallback`, `living_form_moves` extended (two-hit caps).
Effort at AI pace: about a day each for F0-F2, half a day for F3 (the eleven hero bodies took about a day). The per-species
look may need a second paint pass; that is the main uncertainty.

## 7. Decisions for Euan
1. Approach: form bodies (recommended) or repair the kit.
2. Size: animals and spiders truly grow while humanoids stay under two blocks tall and gain bulk (recommended); or
   everything grows (S zombies can't follow you through a door); or nothing grows.
3. The land's crystal in the rank colour on A and S creatures (recommended), or bone, horn and hide only.
4. Fighting style: two-hit attacks from B and an alpha's roar (recommended), or the existing moves only, acted out.
5. Pace: build F0 to F3 straight through with a hand-back after each (recommended), or stop after the zombie until he has seen it.
