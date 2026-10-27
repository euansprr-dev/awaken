# Armour and clothing: gear that looks and moves like the weapons do (plan for Euan's approval, 2026-10-27)

Status: **plan only, nothing built, nothing in production changed.** Revised the same day after Euan's reply ("I don't only want one set per rank, I want more variations, cool clothes, really get creative"): Part C adds Houses (several sets per rank) and the Wardrobe (clothing). Branch `pack/armour-plan`. Approval page (private until shared):
https://claude.ai/artifact/MPDTTbEFZSPhDr1m9gxfgX (source `docs/armour/page.src.html`, built to `page.html` by `build_page.py`). Written to Euan's planning method (`/Volumes/MEDIA/arms-ref/planning-method.md`).
Labels used below: **READ** (read in this repo at c9482190, or in the 26.3 / NeoForge sources under `.ref/`), **MEASURED**
(computed or rendered here), **PROPOSAL** (not built, nothing measured), **UNVERIFIED** (the first builder must check it).
The one isolated proof is in `docs/armour/proof/` (section 5.1): it renders two armour sets with the weapon style's rules beside
today's. It changes no production file.

## 0. What Euan said, and the one-line answer

> "start a sonnet agent that reviews the arm weapons and the level of effort put into them, then it should plan a whole armor set in
> the same language since we don't have much rn. Like really EPIC looking armour, clothing, etc. Not just boring flat stuff, but
> actually feels like different fabrics, materials, depth, etc and looks really awesome. With different effects, animations, and
> uses too." (2026-10-27)

**The answer.** Give armour the treatment the weapons got. Today a set is one flat 64x32 skin on vanilla's six boxes (the two late
sets add 17 to 20 extra boxes that never move). The plan: every set becomes a **model of real parts** (stacked plates, hanging
cloth, hoods, capes, gems), painted to the weapon style's rules, in **nine materials you can tell apart**; a second **glow pass**
that carries the runes, gems and threads; **cloth that moves** (client only, cheap, switched off with distance); **sound by
material**; a **set moment** when a set completes; the **first-person arm** wearing its sleeve; and a few **uses** that need no new
stats. Nothing in `SetBonus`, any recipe or any stat changes. It stays in vanilla's armour path (no mixins), so it works with
Sodium, other mods' poses and our own animations. About 14 to 18 builder hours at this project's pace (section 6.4).

**Revision, same day.** Euan: "I don't only want one set per rank, I want more variations, cool clothes, and really get creative so you have
options. This is a good start but let's expand on the plan more." Part C (sections 12 to 15) answers it: **Houses** (24 sets at launch, up to about
34 as data, each one a named hunt: the Part you spend picks the look), **a Wardrobe** (six slots of garments, three looks per piece including an
off-duty look, a Tailor, a stowed weapon) and **a second proof** (`houses_D.png`, `garments.png`). Sections 1 to 11 stand; where Part C changes
one, it says so (Decision 3 is replaced by 17, the build estimate by 15.3). **Part D** (sections 17 to 21, after his second reply: "a lot looks glitchy... I want it clean") adds the overlap checker, the lane rules and Decisions 28 and 29; the sets in the renders are the clean versions.

## Part A. The weapons review (what Arms did, and what the armour must speak)

### A.1 How much went into the weapons (MEASURED from git and the tree)

| Measure | Arms (weapons) | Gear (armour) today |
|---|---|---|
| Time from first commit to the last weapon batch | 2026-10-25 10:46 to 10-26 21:57, about 35 h, 61 commits that mention Arms | 2026-10-23 20:41 to 10-24 00:01, 3 commits, about 3.5 h. Since then only W2's recipes touched it (8072f40a) |
| Java | about 8,500 lines (`arms/**`) | about 2,300 lines (`gear/**`) |
| Art generator | `arms_weapons.py` 3,513 lines + `voxel.py` (box kit) + `mesh.py`/`mesh_render.py` + holds, moves and rig tools (about 5,000 lines more) | `gear.py` 2,157 lines (item icons, 64x32 layers, 37 silhouette parts, a preview) |
| Models | 111 meshes (96 forged + 15 Lord-forged), 244 to 510 triangles each, in a custom `.mesh` format | 6 sets of 6 vanilla boxes; Lordscale +20 and Sovereign +17 boxes |
| Shader | `arms_weapon` (painted light, studio metal, rim, emissive flow) + glow + shade pipelines, with a vanilla fallback | none: vanilla armour light only |
| Sound | 26 events, about 80 files, layered and checked for loudness, transient and harshness; a listening page | none: vanilla `ARMOR_EQUIP_*` per tier (`HunterTier.java:33-45`) |
| Motion | first-person swings baked per move, wrists, holds, third-person rig (one weapon part leads the blow) | none: parts are rigid |
| Systems on top | 48 Marks, attunement, mastery, Lord-forged reveal, Arts | set bonuses only |
| Review loop | a contact sheet beside Euan's reference image per batch, a lab (`anim_lab.py`), five audit and research notes, a listening page, 27 GameTests | one preview image |
| Rework | two passes thrown out by Euan before the third (plain cubes: "still very, very blocky"; lofted meshes: "too thin and noisy") | none; never reviewed against a reference |

Armour was built in one evening, two days before the weapon style existed (`docs/combat/weapon-style.md` first committed
2026-10-26 01:51). It was never brought up to it. That is the whole gap.

### A.2 The visual language (concrete rules the armour must follow)

From `docs/combat/weapon-style.md`, `tools/art/voxel.py`, `tools/art/specs/arms_weapons.py` and SPEC §25. The rules a builder can
test; the proof (section 5.1) follows 1 to 7.

1. **Density.** 1 texel = 1 skin pixel (1/16 block). Every box has whole-texel sizes (halves only for centring). Every face is its own
   chart at that density, so texels are square and match the player's arm.
2. **Construction from bold parts, not noise and not staircases.** A few big boxes per part; layers step out 1 to 2 texels and get
   different depths so no two faces share a plane (`check()` fails on a plane fight). A diagonal is a turned box, never a staircase.
3. **Silhouette.** The outline alone says what it is, and no two designs share one. Bolder with tier: bone and antler at the bottom,
   wings, crowns and framed stones at the top.
4. **Painted bevels.** A one-texel band on every outer edge: lit (+2 steps) toward the painted light (up and the upper left), +1 across
   it, dark (-1, -2) away; a contact shadow (base -3) where a face runs into another part. Top and bottom faces take at most ±1.
5. **Palette.** Three or four hand-picked, hue-shifted ramps per piece, one of them dark and one warm; metals keep their colour in the
   albedo (a light base step for gold and brass).
6. **Detail budget.** A few deliberate marks per part (a highlight streak, wrap bands, studs, scales in offset rows, a rune channel),
   never per-texel noise; at most one or two dither patterns, only where they are the material (fur).
7. **Glow is information and sits on specific texels.** Stones are concentric rings, albedo kept two steps under the top so glow adds
   colour instead of whitening; emissive 0.4-0.55 for stones, 0.6-1 for runes and edges; anything that pulses has a **flow** that runs
   along the piece (hilt to tip). Streams of embers, wisps and motes only on the top tiers (B, A, S): light is a reward.
8. **Check every look on a sheet before writing:** three-quarter, other side, back, close-up, at rest and in use, night, the icon at 4x
   and 1x, all beside the reference. Ask: chunky? bevels big and clean? material contrast? reads at a glance? more than plain cubes?
   free of noise?
9. **Tiers are a ladder of form, not of tint.** Each of the six tiers has its own outline and its own signature material (E bone and
   antler, D tusk and bronze, C slate and brass with signal runes, B Veil silver and violet, A black scale and gold, S void weave and crimson).

Mechanical language (how weapons *behave*, which armour should mirror):

10. **It reacts to the player's verbs.** The blade fills with light as a heavy charges, flares on the glint, hums when Resonance is full
    (SPEC §25 "It carries the fight").
11. **One effect engine, one budget, one setting.** Trails, sparks and motes go through the powers module's engine (`FxClient`, `FxPool`)
    with our own sprites (EMBER, WISP, MOTE), never vanilla particles.
12. **Server decides, every client draws it the same way.** `arms_seen` goes to players within 48 blocks; the rest is derived.
13. **Sound is layered and checked:** a transient, a body for the material, a tail, a sweetener, 2 to 5 seeded variants, loudness-gated.
14. **Optional pipelines with a plain fallback.** If the shader can't build, the same mesh draws with vanilla's light.
15. **Plain words everywhere** (`plain-dialogue`): the name, the type line and where to get more.

### A.3 Where the weapons themselves still fall short (READ)

- **Third person is the weak spot.** The blade itself clips less than before (17 of 89 rigged moves put it more than 1.5 px into the body,
  against 23 shipped), but counting hilts and hafts 39 moves have some part of the weapon in the body, against 24 shipped; the whole-game
  audit has 3 fails (two Veilwoven dagger moves, the Veilwoven Spear's second thrust). The CI runner draws
  about 10 frames a second, so the sheets show poses, not motion. (Arms note `arms-blade-first.md`.)
- **Lower tiers differ by model and trail colour only.** E, D and C shed no light (by design); SPEC §25 "Sounds" picks the swish by the
  move and the impact by what is struck, and nothing picks by the weapon's tier, so a Wildhide and a Sovereign Longsword sound alike.
- **No stowed weapon.** I found no code that draws a weapon on the body when it isn't held (searched: sheath, scabbard, holster).
- **Named and legendary weapons are unbuilt (S9)**; a Marked weapon looks like the plain forged one.
- **The route to a weapon was invisible** (arms-progression plan, now approved).
- **Armour doesn't match.** A Sovereign Longsword in a Sovereign Weave suit is a painted blade over a flat skin.

## Part B. The armour plan

## 1. Understanding and success contract

### 1.1 The brief, restated

- **The job.** Wear gear that makes a hunter look like what they are (their rank, their style, what they killed) to themselves in third
  person, to other players in a city, and in the Hearth's Hall, for 80+ hours of play.
- **The pain.** Armour reads as dyed leather in six colours. E, D, C and B are vanilla's six boxes with a texture (READ: `gear_silhouettes/`
  has only lordscale.json and sovereign.json). Nothing moves, nothing glows, nothing sounds like what it is made of, and a completed set
  fires vanilla's CRIT, FLAME and SNOWFLAKE particles and vanilla chimes (`GearCombat.java:96-119, 160-173, 239-240`).
- **The desired experience.** "Really EPIC" armour and clothing with real depth; fabrics and materials you can tell apart; effects,
  animations and uses; in the same language as the weapons.
- **Euan's proposed solutions.** None beyond "same language as the weapons". The mechanism is mine to choose.
- **Hard constraints.** No mixins unless unavoidable (Turbo contract); server authoritative; designs multiplayer-safe (guilds, cosmetics only
  for money, later); public repo, no Mojang assets; 60 players in a city; plain words; fit the arms-progression and regions plans; no stat changes.
- **Unknowns to verify (all answered in 2.4):** what the 26.3 equipment path can render; whether a custom layer can add glow and motion
  without a mixin; how first-person arms are drawn; what Sodium, Iris and Lens do to it.

**Interpretation, in one line:** make armour as good as the weapons by giving it the same *pipeline* (a kit, a painter, a review sheet,
checks) and then using it to author six sets, not by repainting six textures.

### 1.2 "Epic" as observable behaviour

| | Strong | Weak | Unacceptable |
|---|---|---|---|
| Silhouette | the six tiers are told apart from their black outline at 24 blocks; each set has parts that stand 1 to 3 texels off the body | tiers differ by colour only (today E to C) | two tiers share an outline |
| Materials | hide, studded leather, plate, mail, silk, scale, weave, alloy and Deepglass each read as themselves at 1x | metal and cloth painted the same way | noise instead of material |
| Motion | hoods, capes, sashes and tassets lag and settle with the body; plates stay rigid | everything rigid (today) | cloth through the legs or head, jitter, or stretch |
| Light | glow carries runes, gems and thread, brighter as more of the set is worn | glow always on | glow on armour that has none, or glare in a gate |
| Sound | a step in silk and a step in plate sound different at 8 blocks | vanilla equip sounds only (today) | a sound per piece per step (a wall of noise in a city) |
| Moments | a complete set announces itself once, then is quiet | nothing (today) | a flourish every time the set is touched |
| Cost | within the budget in 1.3 | | frame drops in a crowd |

### 1.3 Success contract

- **Usefulness.** You can read a stranger's rank and style from across the street; you can read your own set's state (is the full
  bonus on, is it primed) without looking at a HUD. Armour is a thing you want to find, wear and show.
- **Correctness.** Set numbers, recipes, stats, repairs and tags are unchanged (existing `gear_*` tests stay green). Every texture and
  part spec passes the kit's checks. A piece always draws: a model that fails falls back to vanilla's plain layer (logged once).
- **Effort.** No new menus to learn. Appearance options (hood, glow) live in one place, defaults are good.
- **Responsiveness.** Nothing in the frame waits on the server. Moments arrive within one tick of the event.
- **Control.** The player decides appearance only; the server decides every number. Effects have one setting (Off / Low / High) and obey
  reduced motion.
- **Reliability.** Every effect has a plain fallback (static pose, no glow, vanilla model).
- **Cost.** Per full set in view: at most 60 cubes (S), 720 triangles; at most 0.02 ms of main-thread time for cloth within 24 blocks;
  glow pass limited to the nearest wearers; no per-tick allocation; at most 4 tiny network events per player per second. Measured gate in 6.2.

**Non-goals.** New stats or set bonuses; a cloak slot (the player has none: `EquipmentSlot.BODY` and `SADDLE` are animal and saddle
slots, READ); mobs wearing sets (later); vertex physics; any pay-for-power; changing Arms.

## 2. Verified audit and diagnosis

### 2.1 Map: component → what it does → evidence → consequence → verdict

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| `assets/awaken/equipment/<set>.json` + `textures/entity/equipment/humanoid*/` | one albedo layer per body area, drawn by vanilla's `EquipmentLayerRenderer` with `armorCutoutNoCull` for every layer | `EquipmentLayerRenderer.java:88`; the 8 JSON files | no glow, no tint control, no depth: layers are all cutout, lit by the world | **Keep the path, replace the content** (JSON stays the entry point) |
| `gear.py` `equipment_layer` and the 16x16 icons | paints 64x32 skins from hand tables and ramps | `gear.py:759-940`, `gear_figures.png` | flat; E to B look alike at 10 blocks | **Replace the painter** with the weapon-style painter; keep names, tags, recipes, level road (the same file writes them) |
| `Silhouette` / `gear.client.Silhouettes` | A and S only: extra boxes on a 128x64 sheet, built into a `HumanoidModel` per tier and slot, cached, fallback to plain; head, chest and boots only | `Silhouette.java`, `Silhouettes.java:74-90`, `gear_silhouettes/*.json` (20 and 17 parts) | the right idea, applied to two tiers, static, leggings excluded | **Extend**: schema 2, every tier, all four slots, cloth chains, glow flags, far LOD |
| NeoForge `IClientItemExtensions` | `getHumanoidArmorModel`, `getArmorTexture`, `getArmorLayerTintColor`, called per layer type incl. leggings | `.ref/neo/.../IClientItemExtensions.java:99,229,196` | gives us model and texture control without a mixin | **Use** (already used) |
| `SetBonus`, `GearCombat` | counts worn pieces, applies attributes and combat parts; feedback is vanilla particles and sounds | `SetBonus.java`, `GearCombat.java:96-119` | the numbers are good, the feel is "an add-on" (visual-quality bar) | **Keep numbers; replace feedback** with a gear event and our own effects |
| `HunterGearItem` tooltip | tier line + "2 worn: …" lines; the blade has none; no type line or source line | `HunterGearItem.java:25-42`; arms-progression G2 | player can't tell what a piece is or where to get more | **Joins arms-progression Phase A** (words), adds appearance lines |
| `HunterSense` | Wildhide full set outlines creatures through walls (client) | `HunterSense.java` | a good tell with no visual on the wearer | **Keep; add a visible tell** on the wearer |
| Hands (first person) | draws your arm itself in the hand pass (skin + sleeves, tint, glow) | `WorldBody.drawFist/limb`, SPEC §24; `RenderArmEvent` exists in NeoForge | your own armour never shows on your arms | **Extend**: sleeve of the worn chest piece on that arm |
| `WingLayer` (Ascent) | a `RenderLayer<AvatarRenderState, PlayerModel>` added with `AddLayers`, per-entity slots (32), per-tick state, two passes (body + additive glow) | `WingLayer.java`, `AscentClient.java:82` | proves the exact mechanism needed here, in this repo, working | **Reuse the pattern** |
| `AwLayers.Glow` (mobs) | submits the parent model again with `entityTranslucentEmissive` at full bright, alpha by state | `AwLayers.java:46-80` | proves a glow pass on a model without a mixin | **Reuse the pattern** |
| Arms effects (`FxClient`, `WeaponMotes`, `arms_seen`) | one engine, one budget; observers get the event | SPEC §25 | the place armour effects go | **Reuse** |
| `ArmorStandRenderer` | draws armour with the same `HumanoidArmorLayer` | `ArmorStandRenderer.java:34` | stands (and the Hall) show our models for free | **Benefit** |
| Precursor Frame, miner's lamp | own equipment assets and 64x32 textures; Frame modules have effects (Jet Boots thrust, Shield Emitter…) | `AboveItems.java`, `docs/modules/above.md:81-93`, `DelveContent.java:106` | no worn visual for a module (UNVERIFIED: I did not read the Frame's client code) | **Extend** (Phase 4) |
| Sound | vanilla `ARMOR_EQUIP_LEATHER, TURTLE, IRON, DIAMOND, NETHERITE` on equip; nothing else | `HunterTier.java:33-45` | silent gear | **Replace** with a kit |

**What existing information doesn't reach the player:** the set count, the primed strike, Cornered, Flicker readiness and Overflow's counter
are all known to the server and none is visible on the wearer. (A stored value isn't a displayed one.)

### 2.2 The cases (reproduction; renders made here, `gear.py preview` and the proof)

| Case | What I looked at | What it shows |
|---|---|---|
| Disappointing | E Wildhide, D Beastbound, C Gatewrought, B Veilwoven side by side (`docs/armour/proof/renders/sheet_B.png` top row is today's B) | four recoloured vanilla boxes: same outline, a stripe or two, no part that stands off the body |
| Strong existing | A Lordscale and S Sovereign (`gear_late.png`) | horns, spines, talons, a crown, a collar, a cape: the silhouette approach works and reads at once |
| Ordinary | B Veilwoven | dark violet blocks with silver bands |
| Ambiguous | leggings and boots | the inner layer is a 64x32 skin on 0.5-grown boxes: nothing to see; A and S silhouettes exclude leggings entirely |
| Missing-data / failure | silhouette JSON missing or invalid | falls back to the plain layer and logs once (`Silhouettes.java:86-90`): a safe failure, kept |

### 2.3 Root causes (hypothesis → evidence → smallest test → impact)

| # | Symptom | Cause | Evidence for | Against | Smallest test | If fixed |
|---|---|---|---|---|---|---|
| H1 | armour looks flat and generic | armour never got the weapon pipeline (no kit, no style rules, no review against a reference) | built in 3.5 h, before the style doc; 2.3k vs 8.5k lines; never reviewed | none found | the proof: same renderer, weapon rules, B and C sets | the biggest gain; proven in 5.1 |
| H2 | no depth on E to B | no extra geometry: vanilla armour is one texture on six boxes | A and S have parts and read better | tier colour also differs | add parts to B and C only (the proof) | silhouette and depth |
| H3 | nothing glows or moves | equipment layers can only draw cutout, and parts are rigid | `EquipmentLayerRenderer.java:88`; static cape pose 0.18 in `gear.py:1567` | the pattern for a glow layer exists in `AwLayers`/`WingLayer` | a prototype layer on one stand | fixes glow and cloth |
| H4 | set bonuses feel invisible | feedback uses vanilla particles/sounds and shows to nobody | `GearCombat.java:96-119` | the numbers are strong | none needed | fixes "an add-on" |
| H5 | tiers don't feel like a ladder | identity is colour, not form, in E to C | sheets | S/A silhouettes help | silhouette-distinctness test (6.2) | readable rank |
| H6 | gear is silent | no sounds beyond vanilla equip | `HunterTier.java` | | listening page | feel |
| H7 | first-person has no armour | Hands draws your arm itself with no armour pass | `WorldBody.java` | `RenderArmEvent` exists | one stand-in sleeve on the arm | presence |

Source-data problem vs processing problem: it is **content and pipeline**, not engine. Nothing in the engine blocks this.

### 2.4 What 26.3 and NeoForge allow (READ before promising anything)

| Claim | Source (read) | What it means here | Validate in build |
|---|---|---|---|
| Equipment layers submit every layer with `RenderTypes.armorCutoutNoCull` (or its glint twin) and no other type | `EquipmentLayerRenderer.java:72-90` | JSON layers can't glow, blend or animate; glow and motion need our own layer | a stand with a test glow |
| `getHumanoidArmorModel(stack, layerType, original)` and `getArmorTexture(stack, type, layer, default)` replace model and texture per layer type; leggings are a layer type | `IClientItemExtensions.java:99, 229`; `EquipmentClientInfo.LayerType` (HUMANOID, HUMANOID_LEGGINGS, HUMANOID_BABY…) | all four slots can have their own model and texture; wear states by durability can pick the texture from the stack | leggings model test |
| `ModelFeatureRenderer` calls `model.setupAnim(state)` when the submitted model is drawn | `ModelFeatureRenderer.java:38` | a model subclass can pose swinging parts from the render state; the model is shared, so per-wearer state is keyed by entity id (as `WingLayer` does) | two stands, two poses, one model |
| `HumanoidRenderState` carries the four equipment stacks; `AvatarRenderState` has `id`, `ageInTicks`, `capeFlap`, `capeLean`, `isCrouching`, `isFallFlying`; `LivingEntityRenderState.isInWater` | `HumanoidRenderState.java`, `AvatarRenderState.java:15-37` | a layer can read what is worn and the pose without touching the entity | |
| `RenderLayer` + `EntityRenderersEvent.AddLayers`, one layer per player renderer, works today | `WingLayer.java`, `AscentClient.java:82`, `AwLayers.java` | no mixin; submit extra passes with `entityTranslucentEmissive` or `eyes` | |
| Custom render pipelines are registered with `RegisterRenderPipelinesEvent` and fall back cleanly | `WeaponPipelines.java`, SPEC §25 "Optional pipelines" | an armour shader (painted light, metal sheen) is possible later, with a fallback | |
| `RenderArmEvent` is "for rendering armor on the arm" in first person; our Hands also draws arms itself | `.ref/neo/.../event/RenderArmEvent.java` | two paths to cover (vanilla's arm and Hands') | tour in both states |
| `ArmorStandRenderer` uses `HumanoidArmorLayer`; `AddLayers` can add a layer to stands too | `ArmorStandRenderer.java:34` | the Hall and stands show everything | |
| Iris: absent (pinned to a Sodium that crashes); Sodium does not draw entities; Lens Swift/Cinematic draw after | SPEC §14 lines 1165, 1308 | vanilla render types are safe | one CI look tour |
| No mixin is needed for any of this | the rows above | the Turbo contract is not triggered | |

Not verified (UNVERIFIED, first builder's checks): whether Lens bloom picks up `entityTranslucentEmissive` pixels (weapon halos do, SPEC §25);
whether a 256x128 armour texture costs anything measurable; behaviour under a shader pack other than Lens (none shipped).

## 3. Alternatives and recommendation

| | A. Targeted repair | **B. Armour as parts (recommended)** | C. Armour as skinned meshes |
|---|---|---|---|
| What | repaint the 64x32 layers to the weapon style; add silhouettes to B and C; swap particles and sounds | a part model for every tier and slot (generalise `Silhouettes`), the weapon painter, a glow layer, cloth that moves, sounds, moments, first-person sleeve, uses | the weapon `.mesh` and `arms_weapon` shader on the body, skinned to the player's bones |
| Quality | better colour, still flat on E to C | deep, readable, animated; metals painted not reflective | the best light (studio metal, rim), arbitrary shapes |
| Friction | none | none for players | needs a new skinning layer for every pose source (vanilla, PAL clips, Hands) |
| Risk | low | low: every mechanism has an in-repo precedent (Silhouettes, WingLayer, AwLayers.Glow) | high: no precedent; mods that pose the body bypass it |
| Perf (60 players) | trivial | about 400 to 500 triangles a set (B proof: 35 cubes, the houses 41 to 43), LOD down to 9 cubes | 250 to 500 triangles a piece plus a custom shader per piece |
| Cost | 4 to 6 h | 14 to 18 h | 30+ h |
| Mixins | none | none | probably, for poses |

**Recommendation: B**, with one named option kept open: **B+**, an armour fragment shader (the weapons' painted light, metal sheen and
rim) on our own layer for the metal parts of the top tiers, off by default behind the same fallback the weapons use. It is a Phase 5 option
because it is the one part with a measurable cost and no read-through on a shader-pack-free pipeline yet.

Why A loses: it is the "bigger sprite" fix. The proof shows that depth, not paint, is what separates the weapons from today's armour.
Why C loses: a skinning layer means every animation source needs a second path, and the whole point of staying in vanilla's armour path is that
our own wrists, PAL clips and other mods pose it for free. **What would change my recommendation:** if Euan wants true metal reflections
on every set rather than painted bevels, C-for-S-only after B ships; if the 60-wearer bench exceeds the budget even with LOD, A for the lower tiers.

## 4. Behaviour and architecture (the contract)

### 4.1 The Armour Style Sheet (the rules, to be copied into `docs/armour/style.md` and the checker)

1. **Density, whole texels, each face its own chart** (weapon rules 1-2). The base layer keeps vanilla's boxes (outer 1.0 grow, inner 0.5)
   as the "shell" and every set adds parts on top.
2. **Three layers of depth per piece:** the shell (what you'd see today), a **mid layer** 1 to 2 texels proud (plates, folds, a capelet), and
   **accents** 2 to 4 texels proud (horns, spikes, gems, crests). Stacked lames step out and get different depths (1.5, 1.8, 2.1).
3. **Hanging parts hang from a pivot at their top edge** (a sash, a tasset, a tabard, a cape). They are separate boxes with a swing group.
   A long cloth is two or three boxes in a chain so it can bend.
4. **Front and back both designed.** A player is seen from behind more than any other angle: every set has a back (a cape, a plate, a sigil).
5. **Silhouette signatures** (section 4.3): each tier has one signature shape; a **silhouette check** (6.2) fails if two tiers' black
   outlines overlap more than 75% from the front, side or back.
6. **Cube budget** per set including the six shell boxes: E 28, D 46, C 44, B 46, A 52, S 60 (the clean proof sets are 33 to 43); per zone: head 9, shoulders and arms 7 a side, chest and back 8, hips and legs 5 a side, feet 2 a side. Garments add at most 12 cubes worn.
   Far model (beyond 40 blocks): the six shell boxes and at most three signature parts.
7. **Painter** (same rules as weapons 4-6): base step by face, one-texel bevel bands lit up-left, contact shadow at joins, a hem or rim in a
   second material, a few deliberate marks, no noise. One function per material (4.2).
8. **Glow channel:** a second texture of the same size. Emissive only on runes, gems, thread, eyes and molten edges. Albedo under a gem stays
   two steps under its ramp's top. Every glow part carries a **flow** (0..1 along the piece) so light travels.
9. **Each tier is checked on a sheet with the weapon beside it**: four angles, walking, night with glow, silhouette, icon at 4x and 1x.

### 4.2 Materials (nine, each with a painter, a sound family and a behaviour)

| Material | Painter rule (what makes it read) | Used by | Sound family | Behaviour |
|---|---|---|---|---|
| **Hide** (fur, pelt) | fur strokes (the one allowed dither), fringe at the hem, bone toggles | E hood, vest; D under-layer | soft creak, bone clatter | fringe and tails sway; hackles lift when Hunter's Sense is on |
| **Studded leather** | leather ramp with bronze studs in a 3-texel grid, stitched seams | D hauberk, greaves | leather, a light bronze clink | stiff: little sway |
| **Plate** | slate lames with a brass rim, a broken highlight streak, rivets, stepped lames | C (all), A pauldrons | a clank with a short ring | rigid; settles with a small clank on landing |
| **Mail** | 4-texel rings, each row offset half a ring, lit on the ring, dark in the hole | D hauberk skirt, C undershirt, optional | jingle | a fine ripple on landing and sprint |
| **Silk** | pleats (a dark line every 4th column and a ridge beside it), a silver hem, a thread line | B robe, hood, mantle, sash | a rustle, a faint chime from glass | the most motion: long sway, lifts on glide and meditation |
| **Scale** | scales in offset rows, lit edge on top, gold rim | A body | a dry rasp, a gold ring | rigid with a slight shimmer on movement |
| **Weave** (void weave) | a diagonal weave, gold filigree borders | S regalia, cape | low shimmer | cape trails, collar stands |
| **Precursor alloy** | panel seams with a circuit line in signal blue | Precursor Frame | servo, tick | modules extend and retract |
| **Deepglass** | crystal facets, a bright heart and a dark rim, rings | B gems (Deepglass is B's metal), Frame visor | a glass chime | pulses with the set |

The **proof** renders eight of these on one shoulder (`renders/materials.png`) so you can compare them at the same scale.

### 4.3 The six default sets, the Frame and the lamp (what each one is; Part C adds the houses)

The parts below are the design; the proof built B and C to the same rules so the claims are checked, not drawn from imagination.
Names stay as they are (Hood, Vest…).

| Tier | The idea | Silhouette signature | Materials | Glow | Motion | Sound | Set moment (existing bonus, now visible) |
|---|---|---|---|---|---|---|---|
| **E Wildhide** (Hood, Vest, Leggings, Boots) | a hunter's hide kit with a lantern | a deep fur-trimmed hood with a bent tip, bone toggles, fringe at sleeves and hem, a belt pouch and a small amber lantern | hide, bone, sinew, a little leather | **only the lantern** (a slow amber flicker); nothing else | fringe and hood tail sway | leather creak, bone clatter | 2 pieces: the lantern brightens. 4: the hackles rise and the hood eyes glint amber while **Hunter's Sense** is on |
| **D Beastbound** (Helm, Hauberk, Greaves, Boots) | the armour of someone who hunted a named beast: **it wears the trophy you used** | wide shoulders carrying the trophy (tusk, antler or scale), a heavy bronze collar, a beast-skull belt clasp | studded leather, bronze, bone/tusk, moss | a few moss-green spore dots on the trophy | a belt chain and a pelt cape tail | leather, bronze clink | **Cornered**: the trophy and the eyes warm to ember while below half health, and breath steams |
| **C Gatewrought** (Helm, Cuirass, Greaves, Sabatons) | gate-slate plate with brass and signal runes | square stacked pauldrons (three lames), a T-slot great helm with a brass crest, tassets, a surcoat in gate blue | plate, brass, a little cloth | signal-blue runes up the chest, vambraces and shins; they **pulse in order** up the body | rigid; the surcoat and tassets swing | clank, rune hum | a power hit **primes** the next melee hit: the runes flare in the Force's colour (Heat orange, Frost white, Charge yellow, Gravity violet, Spirit green) and run to the weapon hand |
| **B Veilwoven** (Cowl, Mantle, Leggings, Treads) | the Veil's weavers: layered silk over mirror silver | a deep hood with a curling peak, a two-layer capelet, a hanging sash, a back cape with a sigil, robe panels split at the front | silk, mirror silver, spirit glass, thread | a brow gem, a clasp gem, thread lines and the cape sigil | the most cloth: peak, cape (two links), sash tails, panels | silk rustle, glass chime | meditating: the thread lights and the cloth lifts. **Veil Flicker**: the wearer flickers (a one-frame translucent pass and an afterimage) as the hit passes through |
| **A Lordscale** (Greathelm, Warplate, Tassets, Talons) | the Lords' black scale with molten gold | horns, a crest, three layers of pauldron with spikes, a spined back, talons (already built; keep and deepen) | scale, gold, pearl | molten gold edges shed embers (the weapon motes, B/A/S only); a pearl in the brow | rigid with a tassets swing | dry rasp, gold ring | **Overflow**: on every fifth power hit the scales flare gold and a ring bursts at the target |
| **S Sovereign Weave** (Crown, Regalia, Legwraps, Striders) | the Hollow Court's regalia | a crown of spires, a standing collar, disc pauldrons, a long multi-link cape, ankle fins (already built; deepen) | void weave, gold filigree, crimson | crown spires and filigree; in the god form (Sovereign) the whole set turns radiant gold (the same cue the wings use) | the cape trails in two or three links; collar stands | low shimmer, a faint choral swell | **Sovereign's Measure**: the crown's centre gem takes the colour of your highest stat (STR red, AGI cyan, VIT green, INT blue, PER amber, SPI violet) |
| **Precursor Frame** (helm, chest, legs, boots) | hard-light alloy | alloy panels, a visor strip | alloy, Deepglass | the visor and a circuit line | **modules show**: Jet Boots vent, the Shield Emitter's disc deploys, Stealth Field shimmers | servo, tick | each module says when it fires |
| **Miner's lamp** | already lights the rock ahead | unchanged | | | | | unchanged |

(Colour mapping for Measure uses the six stats the game already has; the six colours are a PROPOSAL to be read on the sheet.)

### 4.4 Motion: cloth and hanging parts (client only, no network)

- **The rule:** cloth parts are boxes hung from a pivot at the top edge, posed in the model's `setupAnim` from two inputs: the wearer's smoothed
  velocity and turn rate (two floats per wearer, updated once per client tick like `WingLayer`), and the pose (crouching, gliding, wings,
  swimming, riding). A chain of two or three links gets a lagged angle per link (each link follows the one above with a delay), which is what
  makes a cape bend instead of swing as a board.
- **Limits:** each part has a min and max angle (so nothing passes through legs or head), a damping, and a "fold away" pose for wings open or
  glide. `reducedMotion` halves amplitude; the **Effects setting** Off pins every part to rest.
- **Leg-hung panels hang from the thigh**, not the body (robe and tabard panels follow the legs exactly; this is why the proof's B skirt can't
  clip a stride).
- **Cost:** two floats and a sin per cloth part within 24 blocks; the nearest 32 wearers animate (the same slot count as the wings); farther
  wearers draw the rest pose; beyond 40 blocks the far model. State resets on teleport, dimension change and respawn so nothing can explode.
- **Not simulated:** vertex cloth, collision, wind from the world. (A world-wind tilt from the existing grass-sway wind field is a cheap
  later option.)

### 4.5 Glow, light and effects

- **The glow layer** (`GearGlowLayer`, added to the player renderers and the armour stand, like `WingLayer` and `AwLayers.Glow`) submits only the
  **glow submodel** (the 8 to 10 cubes per set that carry glow texels) with `entityTranslucentEmissive(glowTexture)` at full bright, alpha by
  level. It reads what is worn from the render state, so every client draws every wearer the same way with no extra network.
- **Level rule:** 0 with no piece; 0.35 with 1; 0.55 with 2 (half bonus on); 1.0 with 4 (full), times a slow flow along each part. E: only the
  lantern. It never glows in a way that hides the world: capped at 0.8 beyond 32 blocks, off beyond 48.
- **Effects** go through the powers module's engine with our own sprites (EMBER, WISP, MOTE, FLARE, a new RUNE_PULSE), never vanilla particles:
  A's molten embers (B/A/S only, as for weapons), B's afterimage, C's rune run, D's breath, S's gem. Each set has **one** ambient effect and
  **one** moment effect. Nothing spawns beyond 24 blocks (16 for particles). The existing set-bonus particles in `GearCombat` are replaced by
  these through one `Gear.seen(...)` event.
- **Tells (uses with no stats):** a set can react to something near it, using data the client already has (Atlas, nearby alerts, Hunter's Sense):
  Gatewrought's runes brighten toward the nearest gate or rift you've found; Veilwoven's gems turn to the nearest spirit in Spirit sight;
  Lordscale hums near a Gate Lord's floor. These are hints, never new information beyond what the Atlas and alerts show (a pull, not a push).
  **Decision 9.**

### 4.6 Sound (an armour kit, built like the arms kit)

- Written by `tools/art/specs/audio_gear.py` from the audio kit (`audiokit.py`, Kenney CC0 for transients), loudness-checked like Arms (a new
  category `gear` near -20 dBFS, quieter than weapons: gear is ambience, not event), 2 to 4 seeded variants, a listening page for Euan.
- Events, **one per material family, not per piece**: `gear.equip.<material>` (fastening), `gear.move.<material>` (the footstep layer, quiet,
  only with two or more pieces of that family, at most one per entity per 8 ticks, silent beyond 16 blocks), `gear.land.<material>`,
  `gear.hit.<material>` (what you hear when a blow lands on **you**, in your own armour's voice), `gear.set.<tier>` (the set-complete chime,
  one per tier), `gear.module.*` for the Frame.
- Wildhide, Beastbound and the rest keep their current vanilla `equipSound` as a fallback so nothing is silent if the kit is off.

### 4.7 Set moments and uses

- **Equip.** A piece being equipped plays a 6 to 10 tick fastening beat: a sound by material and one small flare (plates settle, silk lifts).
  Derived from the equipment change on the client; no packet.
- **A set completing.** The first time a player completes a set they get the System line once ("VEILWOVEN · the set is complete. Below 40%
  health, some hits will pass straight through you."), a 1.2 s flourish on the set (crown flare, runes run, thread lights), then the set is
  quiet. Two pieces give a smaller "stirs" (a glint). Never again until a piece changes.
- **Bonus moments.** Cornered, Prime, Veil Flicker, Overflow, Measure's gem: `Gear.seen(player, kind, arg)` sends a 5-byte event to players
  within 48 blocks (at most 4 a second per player). Everyone sees the same moment; only the wearer gets the toast.
- **Uses without stats** (all optional, each a decision): (1) **tells** (4.5); (2) **Appearance** page in the band: hood up or down, cape on
  or off, glow Low/Normal, saved on the piece as `awaken:livery` (schemaVersion 1); (3) **wear states**: a worn piece draws one of three
  textures by durability (new, scuffed, battered) through `getArmorTexture`, so repairing shows; (4) **trophy trims and land colourways**: replaced by Houses (Part C), where the trophy or the land picks a whole set, not a trim; (5) **dyes**: a cloth-only layer, tinted by dye, never the metal; (6) **Hall and stands**
  display the full effect set; (7) the Frame's modules show.

### 4.8 First person, the Armoury and the Hall

- **First person.** The sleeve and vambrace parts of the worn chest piece are drawn on your arm in the hand pass (both paths: Hands'
  `drawFist`/`limb` and vanilla's arm through `RenderArmEvent`), with the same material and glow, reduced to the arm's own boxes. HUD hidden = hidden.
- **The Armoury** (arms-progression §3.2): a fifth tab, **ARMOUR**, listing the six tier cards with their four pieces, what each needs
  (Hide, Part, Binding, Metal, Shard: same four plain words), Track and source lines (their planned `arms_sources` test, Phase A, covers every gear
  recipe). The **locked tier card is a black silhouette of the real set** drawn from our model: this is the same "silhouette until you've
  seen it" idea the arms plan proposes, and the model makes it free.
- **The Hall and stands** show whatever you put on them, effects included.

### 4.9 Data, state, lifecycle, privacy, performance

| Thing | Where | Saved? | Notes |
|---|---|---|---|
| Set spec | `assets/awaken/gear/<set>.json`, schemaVersion 2 (schema 1 `gear_silhouettes` still reads, A and S migrate) | resource | written by `tools/art/specs/gear_parts.py` (new, uses the proof's code); a GameTest reads it on the server |
| Textures | `textures/entity/equipment/parts/<set>_<slot>.png` (+ `_glow`, `_worn1`, `_worn2`) | resource | generated, deterministic, seeded |
| Item icons | `textures/item/<set>_<piece>.png` 32x32, rendered from the parts, vanilla outline | resource | like the weapons' icons |
| Livery | item component `awaken:livery` {look, hood, cape, glow, garments[]} (and `awaken:house` for the house, Part C), schemaVersion 1, optional | saved with the stack (synced to all by vanilla) | cosmetic only; a stack without it renders defaults |
| Cloth and glow state | per wearer id, client only | no | cleared on level change |
| Gear event | `gear_seen` payload (entity id, kind, arg), to players within 48 blocks | no | rate-capped 4 a second per player; server sends only on a real bonus event |
| Settings | `awaken-client.toml`: `gearEffects` OFF / LOW / HIGH, `gearGlow`, `gearCloth` | client | reduced motion respected |

- **Multiplayer-safe.** Appearance is on the item; everything else is derived from what the server already syncs (equipment, health, pose).
  The server decides every number; the new event is cosmetic. Guild colourways later are data, not code. Cosmetic-only monetisation is
  possible without touching power (`multiplayer-ambition`), and this plan builds the hook (`awaken:livery`) and nothing more.
- **Privacy/ownership.** No new personal data; nothing is logged beyond a once-per-session warning on a load failure.
- **Performance and cost (numbers).** MEASURED here: the clean proof sets are 35 (B) and 33 (C) cubes = 420 and 396 triangles (the houses 492 to
  516) against vanilla's 6 cubes (72 triangles) and a weapon's 244 to 510. 60 players in a full B set is about 25,000 triangles. The
  glow pass draws 8 and 7 cubes. LOD: full cloth and glow to 24 blocks; rest pose and glow to 40; the six-cube far model beyond. Gate: **60
  armour stands in S sets, 8 to 30 blocks out, at most +1.0 ms of frame time against the same stands in vanilla netherite** on the M-series
  Mac (a perf tour under `slot.sh`; perf tours do not run on CI).
- **AI and retrieval:** not applicable. Everything is deterministic and seeded; no model, no network, no learning.

### 4.10 How it fits the two approved plans

**Arms progression** (`docs/arms-progression/PLAN.md`, approved 2026-10-27):
- **Words.** Hide, Part, Binding for armour as for weapons; Metal is already the fourth. Every armour ingredient gets a source line, enforced by their planned
  `arms_sources` test (it fails if any ingredient of any Awaken gear or weapon recipe has no line; not built yet, their Phase A). Tooltip copy follows §3.5.
- **The Armoury page** gets an ARMOUR tab (4.8) with Track; tier cards use the same foreshadow, open and job beats (§3.6); "Hale's job" names the
  trophy and the land.
- **Gear stays the floor, not the lever** (§3.8): nothing here changes a stat or a recipe.
- **Order.** This plan's tooltip and Armoury work (Phase 3) waits for their Phase A and C; the art phases (0 to 2) don't touch the same files.
- **First hour.** A decision asks the Arms planner to have Hale hand a plain Hunter's Coat (E look, 1 armour point) with the first weapon, so the
  first hour looks like a hunter's (Decision 12).

**Regions** (`docs/regions/PLAN.md`, approved 2026-10-27):
- **The land owns the hunt's part.** At E and D the land (and its named beast) picks a whole **house** (Part C, 12.3): Mossrunner, Dunewrap and Frostfur,
  then Briarguard, Wyrmscale and Antlerhold. "Terror of <region>" trophies join `#awaken:hunter_core_d` and can be given a house too. Nothing
  becomes "only here"; every tag slot still takes several items and the default recipe takes any.
- **Metal stays depth-native, light gate-native** (as the Arms planner agreed): unchanged. C and up open three levels before their land: the locked
  tier card (the silhouette) and "What waits" do the pulling, and no kit needs a land-native part before the land is reachable.
- **Land tells** (tint, sound) belong to the regions plan; armour doesn't repeat them. Armour tells (4.5) are about gates, rifts, spirits and Lords only.
- **Ranks E to S** are the same words everywhere (one band system).

## 5. Concrete demonstrations

### 5.1 The proof (what was built, what it shows, what it does not)

`docs/armour/proof/armour_proof.py` (disposable; reads gear.py's own software renderer, which uses the same cube, UV and pose maths as
`ModelPart`; writes only PNGs under `docs/armour/proof/renders/`). It builds **B Veilwoven (35 cubes)** and **C Gatewrought (33 cubes)** to the
rules in A.2, paints them with a painter per material, and renders them beside today's textures (same renderer, same scale, same light).

- **Shows:** the same six body boxes become a hooded robe with a layered capelet, a back cape with a sigil, a sash, split panels, silver cuffs and
  glass gems (B), or a stacked-plate suit with a crested great helm, three-lame pauldrons, tassets and runes (C). The tiers have different
  outlines and the materials are told apart (`renders/sheet_B.png`, `sheet_C.png`, `closeup_*.png`, `materials.png`). The glow texture is a
  separate small set of texels (`night_*.png`). Cube and triangle counts are as in 4.9.
- **Does not show:** that it runs in the game (no Java was run, the client was never launched); the real light (vanilla entity light differs from
  the proof's single directional light); real glow and bloom (the night frame is an approximation: dimmed albedo plus the glow texture, blurred);
  that cloth moves well (the walking frame poses the swinging parts by hand: stills, not motion); that 60 players are affordable (a count, not a
  measurement); and it is not final art: the B hood's peak and back are still clumsy, C's helm slot is only one cut.
- **What it settles:** that the weapon style's rules, applied through the existing box model, make armour read as deep and made of different things at the
  player's own pixel density. It does not settle the motion or the cost; section 6 gates those.

### 5.2 Before and after, the same inputs

| Case | Today | Proposed |
|---|---|---|
| **Ordinary: you put on a Veilwoven Cowl** | the hood is a recoloured helmet; a vanilla leather/diamond sound | the hood unfolds over the brow (a 8-tick beat and a silk sound); the brow gem lights; at two pieces, the thread lights |
| **The disappointing case: a stranger in a plaza** | six tiers look alike beyond 10 blocks | you read C from its square shoulders and crest, B from its peak and cape, S from the crown and long cape |
| **Strong: a complete Lordscale set, fifth power hit** | CRIT sparks | the scales flare gold, a ring bursts at the target, everyone near sees it; your own first-person arm shows scale and gold |
| **Ambiguous: the wearer hides the hood** | n/a | Appearance page: hood down; every client sees it down (item component) |
| **Missing data: the model JSON failed to load** | falls back to the plain layer (logged once) | the same; nothing else changes |

## 6. Evaluation and delivery

### 6.1 Baseline

Today's sets in the same renderer (`proof/renders/sheet_*.png`, `gear_today/` in the scratch, `gear_late.png`); the existing `gear_*` tests (13
registered in `GearTests.java`); the six figures at 8x. There is no frame-time baseline for worn armour: **the first task of Phase 2 is to measure
60 stands in vanilla netherite** so the +1.0 ms gate means something (PROPOSAL: the Mac's own perf tour, `slot.sh`, not CI).

### 6.2 Tests (one per mechanism; the full suite once at hand-off; CI does the rest)

`gear_parts_valid` (every set JSON reads, cube budget by tier, uv inside and apart, parents known, no coplanar faces, both layer types for all four
slots), `gear_glow_channel` (glow texels only on glow-flagged parts; level function 0/0.35/0.55/1.0), `gear_cloth_rules` (pure maths: angle limits,
determinism, reset on teleport, LOD), `gear_seen_event` (kinds, rate cap, 48-block radius, bonus numbers unchanged), `gear_livery` (codec round trip,
sync, default render), `gear_silhouettes_distinct` (a generated black silhouette from front, side and back for each tier; no pair above 75% overlap),
`gear_sounds` (events resolve; `audio_events_resolve` covers the files), plus `arms_sources` for armour ingredients and the existing 13 `gear_*`.
What CI cannot prove: how it looks and moves, and the frame cost. Those are gates.

### 6.3 Acceptance gates (set before rollout)

| Gate | Pass | Who/where |
|---|---|---|
| **Look 1** | one contact sheet of all six sets beside Euan's reference and the weapon sheets; every set answers the weapon checklist (A.2 rule 8) and no two share a silhouette | Euan, from the sheet (no code wired yet) |
| **Look 2** | in-game CI look tour: the sets worn on stands, walking, glow at night, first-person arm | CI (`tools/tour-ci.sh`), Euan reviews |
| **Cost** | 60-stand bench ≤ +1.0 ms; glow ≤ +0.3 ms of that; no allocation per frame in the layer | Mac, perf tour, under `slot.sh` |
| **Sound** | the listening page: keep, remake or cut per event | Euan's ear |
| **Words** | every line read aloud (`plain-dialogue`) | Euan / the builder |
| **No regression** | set bonuses, stats, recipes and repair unchanged (the existing tests) | CI |

### 6.4 Phases, effort, rollout, rollback

Effort is builder time at this project's pace (all of AWAKEN about 30 hours; Arms S7 with Marks, the Forge and mastery about 4.5 hours; a batch of 24 to
42 weapons every 20 to 40 minutes). The proof is a head start (the painter, the part model and the renderer exist).

| Phase | Scope | Effort | Gate |
|---|---|---|---|
| **0 · The kit** | `tools/art/specs/gear_parts.py` (part spec, nine material painters, atlas packer, glow texture, wear states, icon render, checks, the review sheet); `Silhouette` schema 2 and `ArmourModels` (generalised `Silhouettes`, all slots, far model); `gear_parts_valid` | 3-4 h | the sheet renders; the checks pass |
| **1 · The six sets** | E to S, one signature cut each, four pieces, textures, glow, icons, equipment JSON; the Frame's alloy set; wired with the fallback | 3-4 h | **Look 1** (Euan), then CI green |
| **2 · Light and motion** | `GearGlowLayer`, cloth state and LOD, settings, first-person sleeve (both paths), armour stand; the 60-stand baseline and bench | 3-4 h | **Cost**, **Look 2** |
| **3 · Sound, moments, words** | `audio_gear.py` kit and listening page, `Gear.seen`, the equip beat and set-complete moment, the effect sprites, tooltips and the ARMOUR tab (after arms-progression A and C) | 3-4 h | **Sound**, **Words** |
| **4 · Uses and cosmetics** | Appearance page and `awaken:livery`, wear states, a cloth-only dye layer (houses are Part C), tells, the Frame's module visuals | 2-3 h | tests; a short look tour |
| **5 · Options** (only if asked) | armour shader B+ (painted light, metal sheen), cuts (Heavy, Fleet, Adept) per tier, Marked armour | 4-8 h | its own plan |

**Total 14 to 19 hours for phases 0 to 4; about a day of wall clock with two builders** (art kit and sets, then layers) plus CI runs and Euan's
look gates. What doesn't compress: CI, Euan's three look gates and the listening page, the Mac's perf slot. **Discoveries that would change the
scope:** Lens not blooming the emissive pass (then glow costs a custom pipeline: +2 h); the 60-stand bench missing the gate even with LOD
(fewer parts on the lower tiers: +1 h); `RenderArmEvent` and Hands' arm drawing conflicting (+1 h).

**Rollout and rollback.** Phase 1 ships behind the existing fallback: every piece has the plain layer it has today, and the client setting
`gearModels` (default on) turns the models off. Resource-only changes (textures, JSON) can be reverted per set by deleting its JSON. The glow
layer and cloth are separate settings and separate commits so each can be switched off alone. In-flight items and saves are unaffected (ids,
recipes and components are unchanged; `awaken:livery` is optional). Rollback triggers: the cost gate failing, a crash from a layer (the layer turns
itself off on first exception and logs once, as `WorldBody` does for fists), or Euan rejecting Look 1.

### 6.5 Risks

- **The look is a taste call** (Euan rejected the weapons twice): Look 1 comes before any code is wired; the proof already shows the direction.
- **Clipping** (cloth through legs when running, capes through wings): leg-hung panels, angle limits and a fold-away pose; a tour checks sprint,
  slide, glide, wings and sneak.
- **Cost in a crowd:** budgets, LOD and a measured gate; the far model is six cubes.
- **A texture per layer type:** four slots times two layer types times glow and wear states is about 40 small textures per set, all generated.
- **Scope:** cuts and the shader are Phase 5 on purpose.

## 7. Failure register (priority: severity, likelihood, reversibility)

| # | Case | Detection | Containment | What the player sees | Recovery | Test |
|---|---|---|---|---|---|---|
| 1 | a set's JSON or texture is missing or invalid | `problems()` at first use | that tier falls back to the plain layer, logged once | today's flat set | fix the file | `gear_parts_valid`; fallback case |
| 2 | the glow layer throws | try/catch around the layer | the layer turns itself off for the session, logged once | no glow | restart | forced-failure test |
| 3 | cloth state goes wrong (NaN, a teleport, a huge frame gap) | clamp each tick; compare position jump | reset to rest | the cloth snaps to rest once | automatic | `gear_cloth_rules` |
| 4 | cloth clips legs, head or wings | angle limits; fold-away for wings, glide, swim, riding | | a small visible overlap at worst | tune | look tour |
| 5 | a crowd (60 players) | the cost gate | LOD tiers; cap on animated wearers; glow only near | lower detail beyond 24 blocks | automatic | the stand bench |
| 6 | glow too strong by day or in gates | look tour | level cap by distance; Effects setting | | player setting | look tour |
| 7 | reduced motion or Effects Off | settings read each frame | rest pose, no flicker | still armour | | `gear_cloth_rules` |
| 8 | another mod's armour layer or a resource pack replaces ours | the extension is not consulted | plain layer | vanilla look | | none (documented) |
| 9 | baby or other humanoid renderers | no `humanoid_baby` layer in our JSON | nothing drawn (as today) | no armour on babies | | existing |
| 10 | the first-person arm has two drawing paths (Hands and vanilla) | tour in both states | the sleeve draws on both | | | Look 2 tour |
| 11 | a spectator, invisible, sleeping, swimming, riding, projecting player | render state flags | no cloth, no glow when invisible | | | look tour |
| 12 | the event arrives after a piece is removed | the client checks the set before drawing | skip | | | `gear_seen_event` |
| 13 | a dedicated server loads client classes | client code stays in `...client` packages | | | | the existing dedicated-server GameTests |
| 14 | an old save | items and recipes are unchanged; livery is optional | | the same gear, better looking | | the existing tests |
| 15 | a text line is cryptic | read aloud | rewrite | | | words gate |

## 8. Challenging the plan (the method's last questions)

- **Real frustration or an internal metric?** Euan said the armour is flat and boring and wants it epic: the cases and the proof show the gap in the
  picture itself, not in a count.
- **Fewer decisions, or just moved?** No new menu to learn; one Appearance page with good defaults; sets are quiet unless something happens.
- **Could a simpler change deliver most of it?** Alternative A would deliver colour and a little depth on B and C only. The proof says depth comes
  from parts, so the part model is the minimum; the glow, cloth and sound are separable commits so Euan can stop after Phase 1 and still have the look.
- **What evidence would show the plan is wrong?** Look 1 rejected; the 60-stand cost; Lens not blooming the glow; cloth clipping that LOD and limits can't fix.
- **Uncertain, unavailable, stale?** Every effect falls back to a static, plain, quiet version.
- **Can the player inspect, correct, trust it?** The Appearance page and the Armoury tab; every number is the one that already existed.
- **Improvement without losing capability?** The existing tests stay the regression net; no stat or recipe moves.

## 9. Decisions for Euan (each with a recommended answer)

| # | Decision | Recommended | Why |
|---|---|---|---|
| 1 | Build armour as **parts, glow, cloth and sound in vanilla's armour path** (alternative B), for all six tiers and the Frame | **Yes** | proven in-repo mechanisms, no mixins, works with Sodium and our animations; the proof shows the look |
| 2 | **Look 1 first**: you approve a contact sheet of all six sets (beside the weapons) before anything is wired | **Yes** | the weapons were rejected twice on look; this protects your time |
| 3 | ~~One signature cut per tier first~~ **Replaced by Decision 17 (Houses)** after Euan asked for more than one set per rank | n/a | see 12 |
| 4 | **Glow carries information** (set count, the lantern at E, runes at C, thread at B, embers at A and S) and E has only its lantern | **Yes** | keeps light a reward (the weapon rule) and keeps crowds readable |
| 5 | **Cloth moves** (client only, nearest 32 wearers, off with distance, halved by reduced motion) | **Yes** | the biggest "alive" win for little cost |
| 6 | **A set completing is a moment** (System line once, 1.2 s flourish) and **bonus moments are visible to others** (a 5-byte event) | **Yes** | makes the set bonus felt; cheap on the network |
| 7 | **First-person armour on your arm** (sleeve and vambrace) | **Yes** | most of your screen time is your own arm |
| 8 | **An armour sound kit by material** (equip, a quiet step layer, landing, being hit, one chime per set) | **Yes** | gear is silent today; quiet by design |
| 9 | **Armour tells** (runes brighten toward a gate or rift you've found, gems toward a spirit, a hum near a Lord's floor) | **Yes, small** | a "use" with no stats; they only point at what the Atlas already shows |
| 10 | **Appearance page** (hood up or down, cape on or off, glow Low/Normal) saved on the piece | **Yes** | clothing you can wear your way; the multiplayer cosmetics hook |
| 11 | ~~Trophy trims and land colourways~~ **Replaced by Houses (17 and 18), which do the same job far better**; dyeable cloth stays, later | n/a | see 12 |
| 12 | Ask the Arms planner to have **Hale also hand a plain Hunter's Coat** with the first weapon | **Yes** | the first hour looks like a hunter's; it is their file to change |
| 13 | The **Frame's modules show** on the suit (vents, a shield disc, a shimmer) | **Yes** | feedback for a thing you pay essence for |
| 14 | Stands and the Hall show the full effects; mobs wearing sets wait | **Yes** | free from the render path |
| 15 | **Phase 5 options** (the armour shader, cuts, Marked armour) only after you've seen Phase 2 | **Yes** | cost and taste both want evidence first |
| 16 | Order: **Phases 0 and 1 now** (art only), 2 after Look 1, 3 after arms-progression A and C | **Yes** | no file conflict with the other plans |

---------------------------------------------------------------------------------------------------------------------

## Part C. Houses and the Wardrobe (added the same day, after Euan's reply)

> "Hmmm but I don't only want one set per rank, I want more variations, cool clothes, and really get creative so you have options.
> This is a good start but let's expand on the plan more." (2026-10-27)

Euan asks for two things the first version did not give: **several sets at every rank**, and **clothing** (cool clothes), with creative options
the player chooses between. This part adds both, keeps everything in Parts A and B that still holds, and says where it replaces one thing
(Decision 3 and the "land colourways" in Decision 11).

## 12. Houses: more than one set per rank

### 12.1 What I found that makes this cheap and makes it fit (READ)

- **The game already has the variety, in its loot.** Every tier's signature-Part tag lists the trophies and relics of real, distinct creatures
  (`gear.py` `TAGS`; `docs/modules/gear.md`): D 13 items from 3 named beasts and 5 E-rank Gate Lords; C 8 from 4 Lords; B 10 from 5; A 7 from
  5 Lords and guardians; S 13 from about 9 Court and guardian relics. That is about 31 creatures beyond E, each with its own look and story, and
  E has three lands (Greenwild, Sunscorch, Frostwild) with their own fauna and hides (`docs/modules/fauna.md`). Today they all feed the same
  recipe and the same look.
- **A recipe can stamp a component on its result with no new code.** `ShapedRecipe` takes an `ItemStackTemplate` (`item`, `count`,
  `components`), so a recipe JSON can say `"result": {"id": "awaken:briar_tusk_helm", "components": {"awaken:house": "briarguard"}}`
  (`ShapedRecipe.java:24`, `ItemStackTemplate.java:21-30`).
- **Set bonuses count by tier, not by look** (`SetBonus.count` checks `g.tier`, `SetBonus.java:101`). Any mix of houses within a tier counts, and
  no number needs to change.
- **The two approved plans already say who owns what.** Regions: the land owns the hunt's Part (hide and trophy) and "Terror of <region>" joins the
  Part tag. Arms progression: the gates give the thing, the world gives the materials; parts drop at a boss's phase changes; locked things show as a
  silhouette until seen.

### 12.2 The rule: the Part you spend picks the house

A **house** is a complete four-piece set with its own look, built for one hunt. The recipe shape, the cost class, the stats and the set bonus are
the tier's, unchanged. What changes is the **Part**:

- The tier's ordinary recipe keeps accepting **any** Part of the tier, and gives the **default house** (the six sets in section 4.3). Nobody is
  ever locked out, and no tier needs more than the one trophy it needs today (the arms plan's "no grind walls" rule holds).
- A **house recipe** takes **its own creature's Part** (for example `#awaken:house_briarguard`, which holds the Briar Tusk) and stamps
  `awaken:house` on the result. You choose the look by choosing which hunt to finish. "I want the Wyrmscale look" becomes "go and beat the Dune
  Wyrm", which is the loop the game already has.
- One piece, one house. You may mix houses piece by piece (a Briarguard helm over a Wyrmscale chest) and the tier's bonus still counts. All houses
  share the shell, so they mix cleanly.
- An item without the component renders the default house. No save is touched, no item id changes.

### 12.3 The roster: 24 sets at launch, about 34 as data

Six defaults (the sets of section 4.3) plus three houses a rank. The look column is a design brief for the builder, to be drawn to the style sheet and
reviewed on a sheet before it is wired. All names are proposals for Euan to rename.

| Rank | House (the Part that picks it) | The look | Reacts to | Glow and motion |
|---|---|---|---|---|
| **E** | *Wildhide* (any Part): the default | hooded hide kit with a lantern | Hunter's Sense | amber lantern |
| | **Mossrunner** (Greenwild: moss, Gloomstalker leather) | a mossy hooded cloak, herb pouches, a lantern of lumen moths | dodge | moths orbit the lantern; the cloak flares |
| | **Dunewrap** (Sunscorch: Dune Ray membrane, Glass Scarab quartz) | veils and wraps, a long sash, glass beads | dodge | beads glint; the sash flutters |
| | **Frostfur** (Frostwild: Frost Stag leather, feathers, Wisp Hound bone) | a fur mantle, a feather cord, bone toggles | guard | breath mist; fur sways |
| **D** | *Beastbound* (any Part): the default | studded leather and bronze, trophy trims | Cornered | ember eyes |
| | **Briarguard** (Briar Tusk) | a boar-skull helm with swept tusks, a rib cuirass, tusk pauldrons, a thorn spine | guard | pollen-green eyes; thorns bloom on a deflect |
| | **Wyrmscale** (Wyrm Scale) | overlapping sand scales, glass fins, a three-link tail | dodge | teal glass; the tail whips and the fins flare |
| | **Antlerhold** (Antler Tine) | a branching antler crown, a frost-fur mantle, a bell | Art | tine tips light; the bell rings on landing |
| **C** | *Gatewrought* (any Part): the default | gate-slate plate, brass, signal runes | primed strike | runes pulse up the body |
| | **Gearwright** (Colossus Gear, Mainspring) | brass clockwork plate, gear pauldrons, steam vents | guard | small gears turn; vents puff on a deflect |
| | **Inkbound** (Archivist's Quill, Waterlogged Codex) | a scholar's robe, a chained book, loose pages | powers | pages orbit when you cast |
| | **Gilded** (Broker's Lantern, Gilded Ledger) | a long coat, a hook lantern, coin chains | dodge | the lantern swings; coins chime |
| **B** | *Veilwoven* (any Part): the default | silk robes, mirror silver, spirit glass | Veil Flicker | thread and gems |
| | **Rimeprayer** (Abbot's Prayer Beads, Glacier Heart) | white and ice-blue monk's robes, prayer beads, an ice pauldron | guard | beads glow; a frost ring on a deflect |
| | **Mirrorguard** (Mirror Shard Crown, Knight's Reflection) | polished mirror plate and a tabard | guard | reflections flash across the plates on a deflect |
| | **Zeroframe** (Zero Core, Zero Plating) | white tech plating, a hard-light visor, a red countdown on the chest | powers | the readout ticks; the visor lights when you cast |
| **A** | *Lordscale* (any Part): the default | black scale and molten gold | Overflow | embers rise |
| | **Skyfin** (Leviathan Fin, Sky Pearl) | blue-pearl fin blades on the shoulders, wind ribbons | dodge | the ribbons trail; fins flare |
| | **Haloweave** (Herald's Halo) | gold-white robes with a halo ring turning behind the head | powers | the ring spins faster as you cast |
| | **Forgeheart** (Furnace Engine Core) | a molten core in the chest, vents, chimney spikes | guard | vents blast on a deflect |
| **S** | *Sovereign Weave* (any Part): the default | void weave, gold filigree, a crown | Sovereign's Measure | crown gem |
| | **Keywright** (Architect's Key, Machine Crown) | blueprint-line armour, small cubes that assemble around you | powers | the cubes fold in and out as you cast |
| | **Stormcrown** (Storm Core) | storm-grey plate with a crown of rods | Art | arcs jump between the rods on the Art |
| | **Nightshroud** (Regent's Shadow, Silence Ear, Sleep Feather) | a star-field cloak, ragged void edges | dodge | the cloak turns to shadow and leaves an afterimage |

**More, as data, any time** (a house is a spec file and its textures, like a weapon batch, which suits the live-updates dream in
`multiplayer-ambition`): D: Tollguard (Bell Warden), Sporeweave (Mother of Spores), Emberplate (Ember Tyrant), Hivecarapace (Hive Regent), Heartwood
(Root Saint); C: Serpentskin (Glass Serpent); B: Dreamshroud (the Sleeper); A: Moonscale (the Drowned Moon); S: Hungerfang, Rootcrown, Ashcrown,
Seedbearer, Mirrorheart. That is about 34 houses beyond the defaults without inventing a new creature.

**Not yet true (UNVERIFIED):** E has only three generic Parts (bone, flint, quartz), so the three E houses need land-native items. Greenwild (moss,
Gloomstalker leather), Sunscorch (Dune Ray membrane, Glass Scarab quartz) and Frostwild (Frost Stag leather, Frostfinch feathers, Wisp Hound bone) already
drop things, but several of them are vanilla items (leather, string) that any land can give. The first builder checks which are land-unique and adds up to
three small land drops (a Greenwild pelt, a Sunscorch membrane, a Frostwild fur) under the regions plan's "the land owns the hide" rule.

### 12.4 What a house is (so the cost is known)

- **A spec** like the proof's: parts (name, parent, box, pose, material, swing group, glow flag, which look shows it), written to
  `assets/awaken/gear/<house>.json` (schema 2 gets a `house` and `looks` field), and its textures (outer and inner layer, glow, two wear states), all
  generated by `gear_parts.py`.
- **Budget.** Cubes per set including the six shell boxes: E 28, D 46, C 44, B 46, A 52, S 60 (the clean proof's houses: 43, 42, 41; B and C: 35 and
  33). Briarguard is 516 triangles; a weapon is 244 to 510 and vanilla's armour is 72. Garments add at most 12 cubes worn (13.2).
- **Reaction profile.** Every house has one ambient effect, one reaction to a verb and one set moment (12.5); the engine and sprites are shared.
- **Recipes.** Four shaped recipes per house (generated), grouped under the tier's group, stamping the house; the existing default recipes unchanged.
  JEI and the recipe book show them as alternatives; each shows "Part: Briar Tusk (the Briar Sow)" in the same plain words as the arms plan.

### 12.5 Houses answer your three verbs (the "uses" Euan asked for, with no new numbers)

The weapons gave you three verbs: strike, guard, dodge, and a power or the Art on top. The armour reacts to them, **once**, and quietly:

| Reaction | When | What the armour does | Houses |
|---|---|---|---|
| **Hold** | a deflect, a block, a guard break | plates flash and ring; thorns bloom; vents blast; a frost ring spreads; mirror plates flare | Frostfur, Briarguard, Gearwright, Rimeprayer, Mirrorguard, Forgeheart |
| **Slip** | a dodge, a perfect dodge, a slide | the cloak or sash flares and leaves an afterimage; the tail whips; the fins flare; ribbons trail | Mossrunner, Dunewrap, Wyrmscale, Gilded, Skyfin, Nightshroud |
| **Cast** | a power, a heavy at full charge, the Art | rings, pages, cubes or arcs answer; antlers and crown light | Antlerhold, Inkbound, Zeroframe, Haloweave, Keywright, Stormcrown |

Six houses in each family, so every style of play has gear that answers it. It is cosmetic and derived: the client already predicts every Arms result
(`arms_result`: deflect, perfect dodge, break) and the server already sends `arms_seen` to players within 48 blocks, so the armour needs no new
message for these, only a listener. (A cast: the powers engine already sends one small event per effect so every client builds the same effect, `docs/modules/power-fx.md`; the armour listens to that.)

### 12.6 Do houses change numbers?

**Recommended: no, not at launch.** Every house of a tier has the tier's stats and bonus, so the sim, the arms plan's difficulty stance ("gear is the
floor, not the lever") and every test stay as they are, and you pick a house for how it looks and answers, not for power. If you later want houses to be
real choices, the clean way is **sidegrade bonuses of equal worth** (a Hold house gives the guard 10% more posture, a Slip house a dodge charge that comes
back a tick sooner, a Cast house 5% less power cost for the first cast after a deflect). That needs a sim row and Euan's say; it is Decision 19, and
the data (`awaken:house`) already carries what it would need.

### 12.7 How it fits arms progression and regions

- **Armoury.** The ARMOUR tab (4.8) shows each tier as a card and, under it, **house chips** for houses whose Part you have held or whose hunt you
  have met. An unseen house is not listed (the hard rule: undiscovered things are never listed), and a seen-but-unmade one shows as a black
  silhouette with "needs: Briar Tusk (the Briar Sow's lair, Greenwild)". Track works per house and fills the task card. The tier's own lock (a silhouette,
  "opens at level 13") is unchanged.
- **Where the Part comes from.** Exactly where the arms plan already puts it: a named beast, an E-rank Lord's part, a D-rank Lord's relic, and so on, "parts drop
  at a boss's phase changes". A Lord whose weapon you are chasing is also the Lord whose Part you want: one fight, two prizes (the Mirror Knight pays
  its Shieldblade and its Crown).
- **Regions.** The land owns the hunt: at E the house *is* the land (Greenwild, Sunscorch, Frostwild), at D it is the land's named beast (Briar Sow in
  Greenwild, Dune Wyrm in Sunscorch, Old Antler in Frostwild). Higher houses come from gates, which the regions plan keeps gate-native. Metal stays by depth. C and
  up still open three levels before their land, so the locked card does the pulling.
- **Raise.** The arms plan lets the Forge raise a found weapon to your tier. Armour gets the same door (Decision 25): raising a piece to the next tier
  **keeps its look family**, so a player who loves a house can stay in it as the tiers climb: the next tier's version is a house of that tier drawn as its
  descendant, where one exists, and the default look where none does yet.

### 12.8 The second proof (what it shows, what it doesn't)

`docs/armour/proof/houses_proof.py` builds **three D houses**, one for each named beast, to the same style sheet and draws them beside today's
Beastbound (`renders/houses_D.png`: front, three-quarter, back and night, with the glow pass). Briarguard (43 cubes, 516 triangles), Wyrmscale (42, 504)
and Antlerhold (41, 492) read as three different characters from three angles, and only the glow-bearing parts shine at night: the pollen eyes, the glass
fins and tail barb, the antler tips.

It shows that a house is not a recolour (this is the clean version, Part D; the first version of these sets had glitches): different silhouette, different material (bone and studded leather, sand scale and glass, frost fur and
antler), different glow. It does not show motion (the tail and cloak are posed by hand in the garments sheet only), real light, or the other 21 sets.
The painters for scale and fur were improved for it and are still rough (the Wyrmscale scales read as banded lames, the Antlerhold fur as speckle); a
real pass would iterate them on a sheet, as the weapons were.

## 13. The Wardrobe: clothing

Euan said "cool clothes". The game has no cloak or clothing slot (`EquipmentSlot.BODY` and `SADDLE` are animal slots, READ), and I do not want a new
equipment slot, so clothing is built from three things that fit what exists.

### 13.1 Three looks for every worn piece

Each worn piece carries a **look** (in `awaken:livery`), set from one Appearance page for all worn pieces at once:

| Look | What shows |
|---|---|
| **Armed** | the full armour: plates, horns, glow. The default. |
| **Travelling** | the soft parts only: cloaks, scarves, hoods, sashes; plates fold away. A calmer road look. |
| **Off duty** | the base layer becomes a coat, the plates hide, the glow is off: a civilian in the city, with the same stats underneath. |

This is cheap: every part in a spec says which looks show it, and the base layer has a second texture painted as clothes. The proof
(`renders/garments.png`, four looks across, front above and back below) shows Gatewrought plate four ways: Armed, Travelling (a cloak and a scarf),
Wayfarer (a hat, goggles, a cloak, a bandolier and a lantern, the helm hidden) and Off duty (a teal coat, a hat, a scarf, a lantern). The off-duty look is 25 cubes.

An optional client and server setting turns Off duty on automatically inside the Hearth's and the city's safe ground (default off). A town full of
off-duty hunters is the MMO-lite picture; a town full of armed ones is the other, and the player decides.

### 13.2 Garments: six slots of clothes that sit on any set

Garments are **small part groups that attach to the worn piece in their slot** and hide whatever they replace (a hat hides the helm's parts, a scarf sits
under a collar). They are drawn by the same model and cloth rules, so a cloak bends and a scarf trails. They add at most 12 cubes worn and hide beyond
24 blocks, except the cloak.

| Slot (attaches to) | Launch list (about 8 each, 48 in all, in two batches) |
|---|---|
| **Head** (the helmet) | hood up, hood down, traveller's hat, antler crown, circlet, half mask, beaked mask, goggles, headscarf, veil, halo ring |
| **Neck** (the chest piece) | long scarf, fur ruff, mantle, high collar, bell cord, pearl strand, coin chain |
| **Back** (the chest piece) | short cloak, long cloak, ragged cape, bedroll, quiver, scroll case, lantern pole, **stowed weapon** (13.4) |
| **Waist** (the leg piece) | sash with tails, pouch belt, tabard, hanging lantern, key ring, charm string, scabbard |
| **Hands** (the chest piece's arms) | gloves, fingerless gloves, wraps, bracers, rings |
| **Feet** (the boots) | spats, boot cuffs, anklet bells, tall boot covers |

The proof builds six of them (cloak, scarf, hat, goggles, bandolier, lantern) and mixes them on one set to prove the idea, not the art.

### 13.3 How you get them

Garments are **unlocks per player**, not items you carry: no clutter, a collection feel, and a clean multiplayer rule (a reward per claim id, once).

- **The Tailor.** A new Hearth townsperson, like Oren the blacksmith (`townsfolk/oren_blacksmith.json`), with a loom beside the Forge. She sells the common
  garments for essence and plain cloth (Hide, Binding). PROPOSAL: she arrives at D-rank with the other recognition arrivals (`RankHooks.onRankUp`).
- **A Lord's garment (15, one per Gate Lord, once).** The first time you down a Gate Lord you get one piece of clothing in its own style, the way you
  get a trophy: the Bell Warden's hood, the Mirror Knight's plume, the Sleeper's veil. Granted once per claim id, shown in the same System card as the
  Lord-forged reveal's smaller twin. It is the other half of "one fight, two prizes".
- **Found.** The arms plan's pattern stands, village smiths and the Wayfarer chains' rewards can each carry one garment (a stand at a lair, a smith's
  coat) with the same once-per-claim rule. Never in a chest.
- **Applied for free.** An unlocked garment can be put on any piece you wear from the Appearance page, and changed any time. Others see it because it
  is written on the piece (`awaken:livery.garments`), which vanilla already syncs; a friend you hand a piece to keeps seeing it.

### 13.4 A stowed weapon (the gap in the weapon review)

Part A.3 found no stowed weapon is drawn on the body. As a Back and Waist garment (**Stowed weapon**, **Scabbard**), the layer draws the weapon you last
held, on your back or at your hip, while it is not in your hand. The client remembers the last styled weapon it saw in each wearer's hand (no new
message, resets on level change). UNVERIFIED: the weapon is drawn by our `awaken:weapon` special item model; I did not verify that a render layer can
submit it (vanilla has `AvatarRenderState.heldOnHead`, an `ItemStackRenderState`, so a stored item render state is an existing idea). A one-hour spike
in the first Wardrobe phase answers it; if it fails, the garment draws a stand-in hilt from the weapon's own colours.

### 13.5 Data, state and multiplayer

| Thing | Where | Saved? | Notes |
|---|---|---|---|
| House | item component `awaken:house` (an id, schemaVersion 1) | with the stack | stamped by a recipe's `result.components`; absent means the default house |
| Look and garments | item component `awaken:livery` {look, hood, cape, glow, garments[]} (schemaVersion 1) | with the stack, synced by vanilla | cosmetic only |
| What you have unlocked | player attachment `awaken:wardrobe` {known garment ids, claimed ids} (schemaVersion 1) | yes, kept through death | the server checks an apply against it; a hand-me-down keeps what it shows |
| Tailor, unlocks | `townsfolk/*.json`, a payload to open the page and apply | | one server check per apply |
| Stowed weapon | client memory per wearer id | no | |

Multiplayer-safe: everything a stranger sees is on the item or derived from it; nothing is a per-world singleton; unlocks are per player. Guild colours and
emblems become one more field in `awaken:livery` later, as data. Real money, if it ever comes, can only touch this layer (looks and garments), never a stat
(`multiplayer-ambition`); this plan builds the hook and nothing more.

## 14. Failure register and tests for Part C

| # | Case | Detection | Containment | What the player sees | Test |
|---|---|---|---|---|---|
| 16 | an item has an unknown house id (a removed house, an old save) | the model lookup misses | the default house renders; logged once | the tier's default look | `gear_house_specs` |
| 17 | a house spec or texture is invalid | `problems()` at load | that house falls back to the default, others unaffected | the default look | `gear_house_specs` |
| 18 | mixed houses on one set | by design | each piece draws its own house; the bonus counts by tier | an eclectic set | `gear_house_mix` |
| 19 | a house recipe's Part tag is empty or names an item that does not exist | build-time check | the recipe is not generated; the default stays | nothing missing | `gear_house_recipes` |
| 20 | a hat and a helm both want the head | each part says which looks and which garments hide it | the garment wins; the helm parts hide | the hat, no helm clipping | `gear_wardrobe_rules` |
| 21 | a garment not unlocked is applied (a hacked client, a trade) | server check on apply | refuse; a handed-over piece keeps showing its garment | nothing | `gear_wardrobe_apply` |
| 22 | Off duty in a fight | the look is only appearance | stats and set bonuses unchanged; the Appearance page says so | a coat that still guards | `gear_looks_cosmetic` |
| 23 | the Tailor is missing or dies | towns respawn Oren-like NPCs when missing | the Appearance page still works for unlocked garments | | existing town respawn |
| 24 | too many cubes worn (a full house plus garments) | the kit's budget check | refuse a spec over budget; garments hide at distance | | `gear_parts_valid` |
| 25 | a stowed weapon clips the back, a cloak, or wings | angle limits, hide with wings or glide | fold away | a weapon tucked clear | look tour |
| 26 | the player never meets a creature but wants its house | the default recipe is always open | | the default look | `gear_house_recipes` |

New tests, one per mechanism (the full suite once at hand-off): `gear_house_specs` (every house has a valid spec, textures, a budget under its tier's, and a
reaction), `gear_house_recipes` (each house recipe stamps its house, its Part tag is not empty, the default recipes are unchanged), `gear_house_mix`,
`gear_wardrobe_rules` (visibility rules), `gear_wardrobe_apply` (unlock check, once per claim), `gear_looks_cosmetic` (no attribute differs between
looks), `arms_sources` for every house Part. What CI cannot prove is how a house looks and moves: Look 1 and Look 2.

## 15. Revised delivery

### 15.1 Phases (replaces 6.4)

| Phase | Scope | Builder time | Gate |
|---|---|---|---|
| **0 · The kit** | the part painter, packer, **overlap checker and lane helpers (Part D)**, and sheet from the proof; schema 2 with `house` and `looks`; `ArmourModels` for all four slots; the `awaken:house` component and the recipe generator | 5-6 h | the sheet renders, checks pass |
| **1a · The six defaults** | the six sets of 4.3 and the Frame | 3-4 h | |
| **1b · House wave 1** | one house a rank: Mossrunner, Briarguard, Gearwright, Rimeprayer, Skyfin, Keywright (six more sets) | 3-4 h | **Look 1: Euan approves a sheet of the twelve sets beside the weapons** |
| **2 · Light and motion** | glow layer, cloth and distance levels, first-person sleeve, the three verb reactions, the 60-stand test | 3-4 h | **Cost**, **Look 2** |
| **3 · Sound, moments, words** | the sound kit, set moments, tooltips and the ARMOUR tab with house chips (after arms-progression A and C) | 3-4 h | **Sound**, **Words** |
| **4 · Uses** | Appearance page, wear states, tells, the Frame's modules, dyeable cloth | 2-3 h | tests, a short tour |
| **5 · House wave 2** | the other twelve houses; runs beside 2 to 4 with a second builder once Look 1 is approved | 5-7 h | a sheet per batch |
| **6 · The Wardrobe** | the three looks, 48 garments in two batches, the Tailor and the unlock page, Lord and found garments, the stowed-weapon spike | 8-10 h | a sheet, a tour |
| **7 · Later, as data** | more houses (the 34), guild emblems, the armour shader, Raise | open | its own plan |

### 15.2 Effort and bottlenecks

At this project's pace (Arms S7 about 4.5 h; weapon batches of 24 to 42 every 20 to 40 minutes; the kit for the weapons was two rejected passes and a day
of iteration): a house takes a builder roughly 20 to 30 minutes once the kit exists (the proof's three houses and four looks took one sitting), 24 sets
about 8 to 10 h, 48 garments 4 to 6 h, the Tailor, page and unlocks 3 to 4 h.

### 15.3 New totals (replaces the earlier estimate)

- **Parts A to B alone** (the six defaults, glow, cloth, sound, moments, uses): 14 to 19 h, as before.
- **Plus Houses** (waves 1 and 2): +8 to 11 h.
- **Plus the Wardrobe:** +8 to 10 h.
- **All of it: about 31 to 41 builder hours; with three builders about two days of wall clock**, every wave shippable on its own, so Euan can stop after
  any of them and keep a working game. What doesn't compress: CI, Euan's look gates (one per wave), the listening page, and the Mac's perf slot.

### 15.4 Risks specific to Part C

- **Taste at scale.** 24 sets is a lot of look to approve: Look 1 is twelve sets, then one sheet per batch, so no wave is built on an unseen look.
- **E has no unique Parts** (12.3): three small drops may be needed under the regions plan.
- **Cost of garments on top of houses.** Budgeted (worst worn 72 cubes, 864 triangles; the heaviest look so far is 43) and tested with the stand bench; garments hide with distance.
- **The stowed weapon** may not draw through a layer (13.4), with a fallback.
- **Spoiler rule.** Houses and garments you have not met must not be listed; the Armoury and the Appearance page must filter by what the player knows (the same
  rule the arms plan already applies).

## 16. Decisions added by Part C (each with a recommended answer)

| # | Decision | Recommended | Why |
|---|---|---|---|
| 17 | **Houses:** the six defaults plus three houses a rank at launch (24 sets), and every other Lord, beast and Court Part becomes a house later as data (about 34) | **Yes** | answers "more than one set per rank" with content the game already has; each house is a named hunt |
| 18 | **The Part you spend picks the house** (a recipe stamps `awaken:house`); the default recipe keeps accepting any Part, so nobody is locked out and no tier needs a second trophy | **Yes** | choice without grind; no new crafting screen; fits the arms and regions plans |
| 19 | Houses are **look and reaction only at launch** (the tier's numbers; a mixed set still counts); sidegrade bonuses of equal worth only after a sim row | **Yes (no sidegrades now)** | keeps the sim, the difficulty stance and the tests intact |
| 20 | Houses **react to your verbs** (Hold, Slip, Cast: six houses each) | **Yes** | real "uses" with no new numbers; every playstyle has gear that answers it |
| 21 | **Three looks** for every worn piece: Armed, Travelling, Off duty, with an optional automatic Off duty in safe ground (default off) | **Yes** | clothes for the city, cheap to build, same stats |
| 22 | **Garments in six slots** (about 48 at launch, in two batches), unlocked per player and shown through the piece | **Yes** | the "cool clothes", with no new equipment slot |
| 23 | **A Tailor** at the Hearth, a **Lord's garment** for the first kill of each Gate Lord (15), and found garments (stands, smiths, chains); never in a chest | **Yes** | garments you earn from the hunt, a collection, once per claim |
| 24 | **A stowed weapon** on the back or hip (a spike first, with a fallback) | **Yes** | fixes a gap the weapon review found |
| 25 | **Raise carries a house forward** (a piece raised to the next tier keeps its look family) | **Yes, after the arms plan's Raise ships** | lets you stay your look from E to S |
| 26 | The names in 12.3 are proposals: you rename any house or garment freely | **Yes** | the lore names are mine |
| 27 | **Order:** wave 1 (defaults plus one house a rank) first, you approve the sheet, then waves 2 and the Wardrobe in parallel with light and motion | **Yes** | no wave built on a look you haven't seen |

---------------------------------------------------------------------------------------------------------------------

## Part D. Clean: no overlaps, one hero per zone (added after Euan's second reply)

> "Okay HUGE level up, but a lot of these feel very clunky like too much is going on in a single place so things overlap. I don't want it
> simplified, but I do want it clean looking and really AAA level. Right now a lot looks glitchy." (2026-10-27, with three crops of the
> houses' hips, legs and boots)

He is right, and it is measurable. The weapons had a checker that fails a model whose faces share a plane (`voxel.py` `check()`, A.2 rule 2), and my
proof sets never had one. Euan's crops were the symptom; this part is the diagnosis, the rules and a gate. It keeps the richness and removes the
clutter and the glitches. Everything in Parts A to C stands except the numbers it changes (the budgets and the triangle counts, patched above).

## 17. What was wrong (MEASURED)

I wrote the checker (`docs/armour/proof/audit.py`): it poses a set at rest and a stride each way (0.55 rad) and reports **zfight** (two faces on one plane
facing the same way: the flicker you saw as vertical stripes), **cross** (one box passing through another and poking out) and **buried** (a box wholly
inside another). A hinge on a cloth chain, a wrap around a limb's own shell, and a spike planted less than 30% into a plate are allowed. It was run on
the sets exactly as Euan saw them (commit e6d649f2):

| Set (first version) | Cubes | Findings | zfight | cross |
|---|---|---|---|---|
| B Veilwoven | 43 | 19 | 9 | 10 |
| C Gatewrought | 41 | 48 | 27 | 21 |
| Briarguard | 53 | 18 | 7 | 11 |
| Wyrmscale | 49 | 14 | 9 | 5 |
| Antlerhold | 48 | 22 | 15 | 7 |
| Look: Armed / Travelling / Wayfarer / Off duty | 41 / 49 / 54 / 25 | 48 / 65 / 61 / 4 | 27 / 34 / 27 / 2 | 21 / 31 / 34 / 2 |

The causes, in the order they matter:

1. **The two legs' layers shared planes.** Vanilla's leg boxes overlap in the middle (my shells did too), and the thigh panels were 5 wide on legs 3.8 apart, so
   left and right panels sat on the same plane for a 1.2 px strip: the vertical stripes in the Antlerhold and Wyrmscale crops. The boot cuffs (7 wide on legs 3.8
   apart) were worse: the two cuffs overlapped by 3 px.
2. **Too many things in one band.** A belt, a sash, its tails, a skirt front and back, a knee plate, a cuff and a toe cap were all hung in about 10 px of leg
   and hip, each its own box, so they passed through one another (the green and white boxes in the Briarguard crop were its knee thorn passing through its skirt).
3. **The arms hang beside the hips.** The belt, the sash and the belly lames were wider than the body, so they shared the hip band with the arms' cuffs: faces on
   one plane and boxes through each other. (Of all the causes this was the one I did not see until the checker named it.)
4. **Flush edges.** A pauldron lame started exactly on the shell's top plane, another had its inner edge 0.05 px from the shell's side, and a cuff's bottom matched the arm's.
5. **Layered cloth with the same swing.** A scarf and a cloak swung to the same angle on the same side, so they passed through each other; a spike was set to
   pass through a plate (not stand on it).

## 18. The clean rules (these join the style sheet, 4.1; the checker enforces the ones it can)

1. **One hero per zone.** The zones are head, shoulders, chest front, back, hips and legs, feet. Each has one shape that carries it (a hood, a pauldron stack, a
   rib cuirass, a cape, a split robe, a boot cuff); supporting parts stand on it and never share its volume; one accent (the glow) at most.
2. **Lanes.** Every limb's height is cut into bands, each owned by one part, with a gap of at least 0.3 px between bands. The proof's map is in the header of
   `proof_sets.py`: a leg is panel (0.2 to 6.4), knee (6.9 to 9.1), boot cuff (9.5 to 10.9), toe (11.3 to 12.9); an arm is pauldron (-4.8 to 0), elbow (3.8 to 6.2),
   cuff (6.8 to 10.2).
3. **Left and right never touch.** The leg shell is slimmer and outer-biased (flush with the centre line inside, 0.5 out on the outside, front and back), and every leg part
   stays on its own side of the centre line with a 0.2 gap, so the gap between the legs reads as a clean split.
4. **Arm-clear lane.** Body-parented bands below the shoulders stay within 3 px of the centre line, and arm-parented parts stay at least 3.2 px from it, so a
   belt can never meet a cuff. The arms hide the body's sides anyway.
5. **No flush edges.** Two same-facing faces closer than 0.08 px are a plane fight; keep 0.25 px between a part's edge and its neighbour's (a gem 0.3 off the
   brow's bottom, a lame never on the shell's own plane).
6. **Stacks step.** Stacked lames touch, and each is wider or deeper than the one above by 0.25 to 1.0 px, so a stack reads as a step, never an overlap.
7. **Hanging cloth is thin and hangs outside.** One pixel thick, hung from a pivot at its top, ending 0.4 px short of the next band; layered garments go behind
   one another in a fixed order (a scarf's tails, then the cloak behind them) and the outer layer swings more than the inner one.
8. **Plant, don't pierce.** A spike stands on a plate (under 30% of its volume inside it); it is never pushed through one.
9. **Paint small detail, don't model it.** A box under 1.5 px is an accent (a gem, a clasp) and there are few of them; ribs, studs and scales are painted.
10. **Garments hide what they replace** (a hat hides the helm, a cloak the back plate) and never share the volume.

**The gate.** `python3 audit.py` (the real kit: `gear_parts.py check`, and a GameTest `gear_parts_clean`): **zero zfight, cross and buried** at rest and a stride
each way for every set and every look. A full sprint swings the legs to 1.0 rad; the hip clips there are looked at by eye in the tour, not gated, because a
hanging panel at that angle is the one case where the geometry honestly cannot clear and a clamp (the panel stops swinging at 0.55) is the fix in the real cloth code.
Look 1 gets one more page: **the lower body, front and back, at rest and mid-stride, for every set** (the crops Euan took), beside the full figures.

## 19. The result (MEASURED)

I rebuilt all five sets and the garments to these rules (`proof_sets.py`), in lanes, and ran the checker until it reported nothing:

| Set | Cubes | Triangles | Findings, before then after |
|---|---|---|---|
| B Veilwoven | 35 (was 43) | 420 | 19 then **0** |
| C Gatewrought | 33 (was 41) | 396 | 48 then **0** |
| Briarguard | 43 (was 53) | 516 | 18 then **0** |
| Wyrmscale | 42 (was 49) | 504 | 14 then **0** |
| Antlerhold | 41 (was 48) | 492 | 22 then **0** |
| Looks: Armed, Travelling, Wayfarer, Off duty | 33, 38, 43, 26 | 396, 456, 516, 312 | 48, 65, 61, 4 then **0 in all four** |

It is not simpler: the sets keep their silhouettes (tusks, antlers, fins, a tail, a cape, stacked pauldrons), glow and materials. They are organised: fewer, larger,
better-placed boxes (7 to 10 fewer cubes a set), with the bands visible. `renders/before_after_lower.png` puts the first and the clean version of each house's
hips, legs and boots side by side, front and mid-stride (the shot of Euan's crops); `renders/lower_*.png` is that strip for every set; `renders/audit.txt` is the
checker's result.

What the checker cannot tell: whether it is **beautiful** (that is Look 1), whether the **textures** are clean (a fur painter that reads as speckle is not a
geometry fault; I calmed the scale and fur painters in this pass and they still need a sheet), and what a **full sprint** or **swimming** does to hanging parts (not posed here). It also
only knows boxes: a gem on a cuff is checked, a gradient on a texture is not.

**What this changes in the plan:**
- Phase 0 (the kit) gains the checker and the lane helpers, which are written: +1 h (now 5 to 6 h). Nothing else moves; every set is built to the rules, which cost
  less than rebuilding it later.
- The cube budgets drop (4.1, 12.4) and the triangle counts with them (4.9).
- Two decisions, 28 and 29 (section 21).

## 20. Failure register and tests added by Part D

| # | Case | Detection | Containment | What the player sees | Test |
|---|---|---|---|---|---|
| 27 | a new house or garment shares a plane or passes through another part | the checker at build time | the spec is refused | nothing (it never ships) | `gear_parts_clean` |
| 28 | a garment lands in a lane another garment owns | each garment names its lane and layer; the checker runs on every garment pair that can be worn together | refuse the spec | | `gear_wardrobe_rules` |
| 29 | a hanging panel clips at a full sprint | the sprint pose is looked at by eye; a clamp keeps swing at or under 0.55 rad | | a panel that stops swinging | the look tour |
| 30 | a texture reads as noise | Look 1 sheet | repaint | | Euan's eye |

Tests added (one per mechanism): `gear_parts_clean` (every set and look: no zfight, cross or buried at rest and a stride each way); the existing
`gear_parts_valid` budgets use the new cube numbers.

## 21. Decisions added by Part D

| # | Decision | Recommended | Why |
|---|---|---|---|
| 28 | Adopt **the lane rules and the overlap checker as a gate**: a set or garment that shares a plane or passes through another part cannot ship | **Yes** | the weapons have this gate; the armour needed it, and it is already written |
| 29 | **Look 1 shows the lower body at rest and mid-stride for every set**, beside the full figures | **Yes** | the glitches were at the hips, legs and boots; the sheet should look where the problems are |

## 22. Not verified, and left out on purpose (all parts)

- UNVERIFIED (Part C): which E items are land-unique (12.3); whether a render layer can draw the stowed weapon (13.4); that the Wyrmscale and Antlerhold painters (scale, fur) are still rough.
- UNVERIFIED: Lens bloom on the emissive pass; the cost of a 256x128 armour atlas; how the Frame's client code draws its modules today; how
  `RenderArmEvent` and Hands' arm drawing interact in the states they alternate; the exact player-facing words (a builder writes them and reads
  them aloud).
- Not built, not run: no Java, no client, no tour, no gate. The proof is a still.
- Left out: new stats or bonuses, mobs wearing sets, a cloak slot, vertex physics, weather-wet cloth, Marked armour, armour for animals.

## 23. Files

`docs/armour/PLAN.md` (this); `docs/armour/proof/` (`armour_proof.py` the engine, `proof_sets.py` the sets, `audit.py` the checker, `render_all.py` the driver, `renders/*.png`); `docs/armour/page.src.html`, `build_page.py` and `page.html` (the approval page).
