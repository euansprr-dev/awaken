# AWAKEN weapons pipeline audit: what exists, what it would take

Read-only audit of `/home/user/awaken` at HEAD `d12ea34` (branch `claude/kind-darwin-18uqmv`), 2026-10-25.
Goal: a pipeline that turns one weapon spec into a 3D held model, textures, first- and third-person attack clips,
sounds, a preview sheet and automatic quality checks, for hundreds of weapons.

## 0. How each claim was checked

| Tag | Meaning | How it was established |
|---|---|---|
| **V-CI** | Verified: Java at HEAD uses this API and it compiles on MC 26.3 / NeoForge 26.3.0.95 | CI run 40181959033 on `d12ea34` (`gh run view`): **Build: success**. The GameTests step failed on one unrelated test (`awaken:living_camp ... the camp is built into the world on tick 0`). |
| **V-DOC** | Verified by the cheat sheets | `docs/api/client.md:3`: "Every signature below was read from the real sources on 2026-10-23". |
| **V-RUN** | Shipped JSON or assets that the game loads on the client | **CI cannot check these.** The GameTest run is a dedicated server, so item models, item definitions and textures are never baked there. Only the owner's client runs (and autopilot tours) prove them. |
| **V-HERE** | I ran it in this container, writing only to the scratchpad | Python 3.13, PIL 12.3, numpy 2.5.3 and ffmpeg are installed. `git status` stayed clean. |
| **V-WEB** | Modrinth API, 2026-10-25 | Licences are not recorded anywhere in the repo. |
| **S** | Suspected | Present in vanilla 1.21.4 to 1.21.11 as far as I know, but absent from the repo and from `docs/api`. Must be grepped in `.ref` before anyone builds on it (see §6). |

Environment limits: `.ref/` (the Minecraft and NeoForge sources and vanilla assets) is not in this container, only JDK 21
is installed (the project needs 25), and I did not run gradle. I did not run `build_all.py`, `audio_build.py` or
`audio_mix.py`: the first two write into `src/main/resources`, and the third writes `build/audio-loudness.txt`.

---------------------------------------------------------------------------------------------------------------------

## 1. Headline findings

1. **There are no 3D items.** The repo has 642 item definitions, all `minecraft:model` except one `minecraft:select`.
   Of 393 item models, 432 parent references are `item/generated` and 23 are `item/handheld` (some models chain parents),
   and **none has `elements`**. Today's "weapons" are gear.py's 16×16 diagonal sprites with an emissive layer.
2. **The repo already has a complete generator for 3D cuboid models: `fauna_rig.py`** (used by heroes.py and fauna3.py).
   It builds a bone and cube tree, packs UVs, paints face by face, writes keyframe clips, runs checks, renders a
   software preview and writes plain-data Java parsed at runtime. It is the right core for a weapon generator.
   `ladder3d.py` plus the block-model specs show the JSON-elements variant (a per-face UV atlas, emissive overlay
   elements).
3. **Third-person attack animation is solved only for players who have the pack.** The Player Animation Library
   (PAL) is `compileOnly` and optional (SPEC §14: "never bundled or required"). Without it, players see vanilla arm
   poses. A no-PAL route without mixins does exist: a custom `ArmPose` with an `IArmPoseTransformer`, which the Holo
   wrist already uses.
4. **First person: nothing exists for weapons.** The "Hands" system on `origin/pack/fp-anims` is a plan only (3 commits,
   2 doc files, no code, awaiting approval), and it explicitly keeps **attacking vanilla** (`PLAN.md:186`).
5. **Hit feel is built for powers, not melee.** Hit-stop and camera shake live inside the powers module and are
   package-private. The life layer's stagger is pushed away from the *camera*, at a fixed strength. Damage numbers
   already work for every hit.
6. **No trail renderer suits blades.** `Shape.TRAIL` is a single-point, camera-facing ribbon sampled once per tick.
   A blade needs a two-edge sweep sampled per frame.
7. **Audio tooling is AAA-grade, but there is no combat sound palette.** Powers' punch, impact and whoosh sounds are
   vanilla events. Close synthesized templates exist (the Ritual's swing and impact). There is no loudness category
   for combat sounds.
8. **QA gaps.** Previews depend on `.ref` for the Steve skin (`power_casts.py preview` fails here). Python checks never
   run in CI. The CI run cannot catch a broken item model.

---------------------------------------------------------------------------------------------------------------------

## 2. The map (component → what it does → evidence → consequence → verdict)

### 2.1 tools/art generators

| Component | What it actually does | Evidence | Consequence for weapons | Verdict |
|---|---|---|---|---|
| `build_all.py` | Imports every `specs/*.py` and calls `build(out)` with `pixelart.Out` rooted at `src/main/resources`. It has no output-directory option. | build_all.py:12-27 | One entry point for all specs, but it always writes into the repo, so it can't be used for dry runs or CI. | keep; add `--out` |
| `pixelart.py` | 24 shared palette ramps (`RAMPS`), a Canvas, tileable value noise, block materials (stone, bricks, plates, circuits, glyph, crystal), 16×16 item shapes (orb, polygon, `blade()`, book, pouch, shard, device, trophy) with a 1 px outline and top-left light. `Out` writes `textures/{block,item,entity,particle,gui}` and animated strips with `.mcmeta`. Deterministic (sha256 seeds). | pixelart.py:28, 375, 479-514 | The palette keeps the whole mod reading as one game, and weapons must stay inside it. It handles 2D only and writes no models. | keep palette; extend |
| `preview.py` | 8× contact sheet of every texture. | preview.py | Generic texture QA. | keep |
| `specs/gear.py` | **The only weapons today**: six tiers × blade, focus and mattock, as 16×16 sprites drawn along a diagonal (`diag_band` from the hand end OX,OY). It adds `_glow` emissive layers for the C, B, A and S blades and foci. `content()` writes models (`item/handheld`, `layer1` with `neoforge_data.light_emission: 15`), `items/*.json`, recipes, tags (`minecraft:swords` and others), level-road steps, and lang through `merge/gear`. `preview()` draws icon sheets plus a **software cube renderer** (`Scene`, `_Pose`, `cube_polys`) that reproduces ModelPart.Cube's UV and pose maths and puts armour, including the A/S silhouette parts from JSON, on a figure with a built-in stand-in skin (`_skin()`, no `.ref`). | gear.py:300-410, 1517-1640, 1758-1945, 1888, 2004-2058 (glow layer 2017), 2096 | This is the template for "spec → icons + model JSON + item definitions + tags + lang + preview". The renderer runs headless. `Silhouettes.java` proves cuboid geometry can be data-driven at runtime (JSON → ModelPart, with a vanilla fallback). | extend (generalise to weapons). V-HERE: preview ran in 6.8 s. V-CI: Silhouettes. |
| `specs/ladder3d.py` | The only generated model with real depth for a vanilla object: two rails and four rungs as JSON `elements` with explicit per-face UVs into a hand-laid 16×16 atlas, overriding `minecraft:block/ladder`. | ladder3d.py:58-94 | Proves the pattern of a generated atlas plus generated elements with per-face UVs. It is a block model, not an item. | extend pattern. V-RUN |
| `city_kit.py`, `gate_rig.py`, `structures.py`, `gate_interiors.py`, `ascent_blocks.py` | JSON element block models. Emissive overlay elements use `light_emission` 15 with `shade_direction_override: "up"`. A mirror uses element `rotation` (y, −45) with `light_emission` 6. `city_kit.check()` validates models offline: parents resolve, texture keys resolve, UVs fall in 0..16, sampled regions are opaque unless the model is cutout. The check reads vanilla parents from `.ref`. | gate_rig.py:388-421; city_kit.py:2882-3000; structures.py:673 | The emissive-element pattern is ready for blade runes and edges. The model validator should be adapted into a weapon-model gate without the `.ref` dependency. | extend. V-RUN; the check needs `.ref` |
| `items_living.py`, `items_crafts.py` | Hand-drawn 16×16 icons. `check()` requires: 16×16 RGBA, no partial alpha, 50-215 solid pixels, at least 9 colours, distinct from each other, deterministic. items_crafts adds a colour distance of at least 14 and a silhouette overlap under 220 px. Previews on grey and night backgrounds, plus a ×2 hotbar strip. | items_living.py:539-558; items_crafts.py:680-712 | A ready-made **GUI-icon quality gate**. | keep, reuse |
| `powers.py` + `powericons.py` + `powerglyphs.py` + `power_sheet.py` | 63 32×32 GUI icons (one frame per family plus glyph rules). **Power Core "crystals" are 2D item sprites + `_glow`**, chosen by `minecraft:select` on the custom property `awaken:core_power`. | powers.py:1-6, 121-135; items/power_core.json; CorePowerSelect.java; PowersClient.java:33-34 | Not 3D. It does prove one item can take many looks through a custom select property, which is an alternative architecture (few items, many variants as components). | keep as option. V-CI (property) |
| `fauna_rig.py` (with heroes.py, fauna3.py, fauna_heroes.py, fauna_anim.py) | **The generated-entity-model pipeline.** A `Rig` is a bone tree with ModelPart semantics (pivots in model px, y down, rotations Z, Y, X) holding `Cube`s. A skyline packer lays out UVs, and mirrored or shared cubes reuse a rect. A `Painter` paints faces with ramps, AO, rim light and patterns (fur, scales, plates, stone, strokes). `Clip` keyframes follow vanilla AnimationDefinition semantics (catmull or linear). `check()` catches missing bones, keys out of range, missing idle/hurt/death clips, and moves without `_windup`/`_strike`. `java_lines()` writes text data that `HeroRig.java` parses at runtime (no MC classes, so it loads on the server for GameTests) and `HeroModel` bakes. `render()` is a software renderer with a night glow halo. `EyeRec` produces blink textures. | fauna_rig.py:96-275, 707-790; heroes.py:25, 75-126; HeroRig.java:1-60 | **The core to reuse.** A weapon is a small rig (blade, guard, grip, pommel, head) that can be emitted as a JSON item model or as a ModelPart model, painted by the same Painter and previewed by the same renderer. The catch: `steve()` reads `/Volumes/MEDIA/.ref/...` (fauna_rig.py:807), so previews that include a player figure fail outside the owner's Mac. | extend (core). V-CI (HeroRig, HeroModel) |
| `fauna.py`, `fauna2.py` | Older approach: Java ModelPart bodies written by hand (`FaunaModels.java`) and mirrored "box for box" in Python. | fauna.py:1-12; FaunaModels.java:17-22 | Shows the drift risk of mirroring two sources. | avoid this pattern |
| `power_fx.py` | Particle atlas of band-quantised pixel sprites, including an additive 3-frame `slash` used by FxRecipes' SLASH sprite. | power_fx.py:432, 830; FxRecipes.java:381, 825 | A cheap impact or slash flash already exists. | reuse |
| `ascent_fx.py`, `lens_fx.py` | Smooth (non-pixel) light textures: the rope (u along it, v across), glow, ring, star, wing; ambient particles. | ascent_fx.py:1-20; lens_fx.py:1-15 | The right kind of texture for a swing ribbon (u along time, v across the blade). | extend (`arms_fx.py`) |
| `power_casts.py`, `body_moves.py`, `ascent_moves.py`, `citizen_clips.py` | Clip authoring (see §2.3). | | | |
| `compat_ui.py` | Jade nine-slice theme. | | Not relevant. | |

### 2.2 Item models and held-item rendering in 26.3

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Item definitions | `assets/awaken/items/*.json`: 642 `minecraft:model` and 1 `minecraft:select` (power_core). | Tally of assets | Everything uses the simple path. | V-RUN |
| Item models | 432 `item/generated` and 23 `item/handheld` parent references; **0 models with `elements`**. 3 models set a custom `display` (citizens' watch_halberd, lamplighter_pole and mason_trowel scale ×1.7 in third person). | models/item/watch_halberd.json | No 3D item precedent. `display` transforms work in game. | V-RUN |
| Emissive | NeoForge per-layer `{"sprite":…, "neoforge_data":{"light_emission":15}}` on generated items; element-level `light_emission` in block models (vanilla `cross_emissive`). | client.md:673-704; gear.py:2017; gate_rig.py:400 | For 3D weapons, use overlay elements with `light_emission: 15`. **Whether the item renderer honours element `light_emission`** is S (it works for block models). | investigate. V-DOC / S |
| Custom select property | `RegisterSelectItemModelPropertyEvent.register(id, SelectItemModelProperty.Type.create(MapCodec, Codec))`. | PowersClient.java:33-34; CorePowerSelect.java | A weapon's look can switch by state (charged, awakened, ability ready, element). | keep. V-CI |
| Drawing an item anywhere | `ItemModelResolver.updateForTopItem(state, stack, ctx, level, owner, seed)` and `updateForLiving(state, stack, ctx, living)`; `ItemStackRenderState.submit(pose, collector, light, overlay, outline)`. In first person, `PlayerRenderState.firstPersonHandsAndItems.mainHandItem` and `.mainHandRenderState` come already resolved. | TrophyMountRenderer.java:49-62; CitizenRenderer.java:91, 192; HoloRender.java:435-444; **Wrist.java:198-211** | A first-person swing can cancel vanilla's hand and re-pose and submit the resolved weapon itself. The same calls cover a sheathed weapon on the back or a display stand. | keep. V-CI |
| Swing types | `net.minecraft.world.item.component.SwingAnimation` (`.DEFAULT`); `LivingEntity.swing(hand, SwingAnimation, boolean)`; `HumanoidModel.ArmPose.SPEAR`; `HumanoidMobRenderer.usesSpearPose`. | Moves.java:121-123; SkinnedMobRenderer.java:64-66; ClientCasting.java:104 | 26.3 has the spear-era swing machinery. The matching data components (`swing_animation`, `attack_range`, `kinetic_weapon`, `piercing_weapon`, `minimum_attack_charge`) are **S**. They could give stab vs whack swings and per-item reach without new code. | investigate. V-CI (class) / S (components) |
| Weapon stats | `Item.Properties.sword(ToolMaterial, …)`, `.attributes(ItemAttributeModifiers)`, `DataComponents.WEAPON` → `new Weapon(n[, disableBlockingSeconds])`. | GearItems.java:70-96 | Stats are solved. | keep. V-CI |
| `IClientItemExtensions` | Registered through `RegisterClientExtensionsEvent.registerItem(ext, items…)` (armour models). | Silhouettes.java:52-58 | Whether 26.3 still has its first-person hand-transform and arm-pose hooks is S. | investigate. V-CI (registration only) |
| Not in repo or docs (**S**) | `minecraft:special` item models with `SpecialModelRenderer` / NeoForge `RegisterSpecialModelRendererEvent`; `RegisterItemModelsEvent` (custom `ItemModel` types); `minecraft:condition`, `range_dispatch`, `composite`; the **`display_context` select** that vanilla's trident and spyglass use (2D icon in the GUI, on the ground and in frames; 3D in hand); element bounds (−16..32); **element rotation rules**. | grep found nothing | These decide whether curved or angled sub-parts need 22.5° segment stacks or a special renderer. Only 0, ±22.5 and ±45 appear in shipped models (−45 ×6, −22.5 ×2, 45 ×1, axes y and x). `docs/api` doesn't cover element rotation. | **investigate first** (§6) |
| Limits of JSON elements (general) | Cuboids only; one rotation per element; per-face UV; baked once (cheap at runtime); no animation (state swaps only, through select/condition). `display` transforms are free floats per context, so the overall angle of the weapon in hand is unrestricted; only sub-part angles are limited. Long weapons hit the coordinate bound and must scale down in `display`. | | Model a weapon straight along its axis and orient it with `display`. Make curves from stepped segments, or from a special renderer if 26.3 allows that. | design rule |

### 2.3 Animation

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| `PalCasts` (powers) | **The only class that imports PAL.** It registers two client-side layers on every Avatar through `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory`: `power_casts` at priority 1500 and `player_clips` at 1600. A `MirrorModifier` handles left-handed players. Clips start with `replaceAnimationWithFade(standardFadeIn(n, EASE_IN_OUT_SINE))`. When `bendable_cuboids` is loaded it plays the `_bent` twin (if `PlayerAnimResources.hasAnimation`). `freeze()` pauses a clip for hit-stop. Any exception flips a `broken` flag and vanilla poses take over. | PalCasts.java:43-48, 62-77, 96-115, 121-130 | Weapon 3P clips plug in as another layer, or reuse `player_clips` through PlayerClips. Hit-stop pause already exists. | extend. V-CI (compiled against PAL 1.2.8 for 26.3) |
| `core.api.PlayerClips` | `play(entityId, clip, fadeIn)`, `stop`, `available`. Installed by PalCasts; a no-op without PAL. | core/api/PlayerClips.java | The door an `arms` module would use. It lacks pause (hit-stop) and speed (attack-speed scaling). | extend. V-CI |
| PAL dependency status | `compileOnly "maven.modrinth:player-animation-library:xJ30QmpE"` (1.2.8+mc.26.3). Not in `neoforge.mods.toml`, not bundled, in the pack's `feel` profile only (client required, server optional). SPEC §14: Awaken "plays the same with no companion mod installed… never bundled or required." | build.gradle:153; src/main/templates/META-INF/neoforge.mods.toml; pack/mods.json; pack/lock.json; SPEC.md:854-857 | **Custom 3P attack animations would reach only pack players** unless a fallback is built or the rule changes. | DECISION |
| Licences | PAL **MIT** (ZigyTheBird/PlayerAnimationLibrary). Bendable Cuboids **MIT**. Shoulder Surfing **MIT**. GeckoLib **MIT** (has 26.3 builds; not used). Better Combat **All Rights Reserved**. playerAnimator (KosmX) MIT but stops at 1.21.7. First-person Model MIT. Camera Overhaul GPL-3.0+. EMF and ETF LGPL-3.0. Fresh Animations: custom terms. Not Enough Animations and 3D Skin Layers: tr7zw protective licence. | Modrinth API | Bundling PAL by jar-in-jar is licence-compatible but breaks SPEC §14. **Nothing from Better Combat may be copied** (design ideas only). The repo doesn't track licences in `pack/lock.json`, though SPEC.md:1074 shows licence terms have mattered before. | V-WEB |
| No-PAL 3P arms | A custom `HumanoidModel.ArmPose` added as an enum extension (`AWAKEN_HOLO_WRIST`), whose `IArmPoseTransformer(model, state, arm)` sets the arm ModelPart's xRot, yRot and zRot. Render-state modifiers twist the body; a `RenderPlayerEvent` leans the figure (CastAnims). power-fx.md already names this route for "fully keyframed arms". | META-INF/enumextensions.json; HoloArmPose.java; HoloOthers.java:130-131; power-fx.md:225-226 | A keyframed-arms fallback for weapon swings without PAL and without mixins (arms and body yaw only; no knees). Untested: how it stacks with vanilla's attack swing. Probably the swing has to be zeroed in the render state (S). | extend. V-CI (mechanism) / S (order) |
| `power_casts.py` (clip language) | Poses in figure terms: `lean`, `tilt`, `rise`, `shift`, `side`, `twist`, `ra`/`la` (an `_aim` variant follows the look), `rl`/`ll`, `head_z`, `head_x`, elbow and knee flex `re`/`le`/`rk`/`lk`, `limbs_body`, `ground`. `bake()` turns them into PAL bones (body, torso, head, arms, legs, plus `bend`). `Clip` holds keys with named easings, loops or an `endTick`; JSON `format_version` 1.8.0. Every clip gets a `_bent` twin. `check()` measures **penetration** (arm sample points inside the head, torso or legs; limit 1.25 px; look pitch 0 and 30). `preview` draws frame strips and sheets. | power_casts.py:1-27, 82-92, 129-175, 267-340, 549-623, 804-836 | The 3P weapon move language exists. **Missing:** the weapon itself (no item or blade bone, no wrist; blade roll comes only from the arm's yRot), blade-vs-body clipping, trail anchors, hit-frame markers, attack-speed scaling. | extend. V-HERE: `check` ran 84 clips in 11.9 s, ok; `preview` fails: needs `.ref` Steve |
| `body_moves.py`, `ascent_moves.py`, `citizen_clips.py` | The same language. `citizen_clips` plays on Awaken's own `CitizenModel` (24 channels, no PAL, straight limbs). | file headers | Clips can drive non-player humanoids (NPCs or monsters with weapons) without PAL. | reuse |
| Existing strike-like clips | `cast_kinetic`, 13 ticks: load at 2, strike at 4 (`easeoutexpo`), follow at 8, settle at 10. `cast_shadow`, 13 ticks: a waist-high sweep from the off shoulder (draw at 3, sweep at 6, low at 9, settle at 10). | power_casts.py:415-471 | Reference timing and poses for light and sweeping slashes. | seed |
| Timing today | First-person lengths live in the Java `CastAnim` enum (PUNCH 8, PALM 9…). Third-person clip lengths live in Python (13). They differ. | CastAnim.java; power_casts.py | The "one timing table" from the Hands plan doesn't exist yet. Weapons need it from day one. | build |
| First person today | `CastAnims.onHand`: with an **empty hand** it cancels vanilla and redraws the arm through `AvatarRenderer.renderRightHand` with vanilla's arm transform plus offset curves. **When holding an item** it only shifts the whole hand stack, so vanilla still draws the item **and vanilla's swing**. | CastAnims.java:350-385, 387-406, 408-504 | A weapon swing must cancel vanilla for that hand and draw the arm plus `mainHandRenderState` itself, as Wrist does. | extend. V-CI |
| Five FP hand listeners | BodyCamera, FeelDirector, CastAnims, OpeningCamera and LucidiumClient, plus the Holo Wrist at HIGH, each move the hand stack. | PLAN.md §1 | A weapon system would be a sixth listener. It needs the Hands owner. | wait for / extend Hands |
| **Hands plan** (`origin/pack/fp-anims`) | **Docs only**: commits 5ce4a99, 301d674, dcef945 add `docs/anims/PLAN.md` (+234) and `docs/plan-hands.html` (+318), no code, "awaiting Euan's approval". Layers: vanilla → Stance → Action (one-shots) → Procedural (look-lag, breath, tremble, impact springs) → camera through FeelDirector. One `RenderHandEvent` owner at HIGHEST that cancels vanilla and draws arms (renderRightHand/LeftHand) and items (`updateForTopItem` + `ItemStackRenderState.submit`). Authoring in `fp_moves.py` with figure terms, arms in eye space (shoulder 0.31 to the side, 0.22 under the eye) plus a framing offset. Export `assets/awaken/fp_clips/*.json` (schemaVersion 1). A preview from the eye at the hand-pass FOV. **One timing table shared with 3P.** New `core.api.FirstPerson` (play, hold, release; register a stance source). The hand pass keeps a fixed FOV. | `git show origin/pack/fp-anims:docs/anims/PLAN.md` §3 (lines 70-110), §5 (line 186) | A weapon swing system is: **Stance** = the weapon's idle or guard; **Action** = light, heavy and ability swings; **Procedural** = hit recoil springs and hit-stop freeze; the same FpClips format and timing table. **But the plan excludes attacking** ("Stay vanilla: too frequent; any delay hurts"), so its scope must be amended. | plan amendment |
| Gaps for weapons in Hands | (1) No **item channel** in FpClips (the grip's offset and rotation relative to the hand, per key). (2) The **held-item transform during a swing**: vanilla's swing transform must be bypassed, and the model's `firstperson_*` display transform is only the rest pose. (3) No **hit-frame, active-window and cancel-window markers** in the clip format. (4) No **trail anchors** (hilt and tip in item space) and no hand-pass trail renderer. The hand pass "gives us no depth" (Wrist.java:219), so custom geometry there needs its own face ordering. (5) No **attack-speed scaling** (PAL's SpeedModifier is mentioned in PLAN §2; Hands' clock). (6) No **combo state machine** or input buffer (client-predicted, server-authoritative). (7) Both views in phase: the plan has the rule; it must also cover swings. (8) Whether the held item follows a bent forearm under Bendable Cuboids is unknown. | | | add to plan |

### 2.4 Creature life and the feeling of a hit

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Life layer (SPEC §23, `mobs/client/life`) | `hurt` (0..1, from vanilla `hurtTime`) and `windup` come from the renderer. A blow kicks a damped spring pushed **away from the camera**, sized by temperament `stagger` / √mass, **not by damage, weapon or blow direction**. The head pitches and yaws. Exertion rises. Wind-ups gather the body low and tremble. Bosses topple. Only Awaken's own bodies get it: hero rigs, P3 rigs, 14 hand-built bodies, and citizens through `CreatureLife`. Vanilla bodies get Fresh Animations (pack only) or vanilla's red flash. | Life.java:25-27, 188-208, 341-368; AwMobRenderer.java:89-90; SPEC.md:3096-3163 | Weapons want the blow's direction (swing plane or attacker yaw) and strength (damage, weapon mass, crit) to drive the reaction, so a dagger and a hammer feel different. That needs a cross-module API, because mobs owns Life. | extend through an API. V-CI |
| `CombatFeel` (powers, package-private) | Camera shake (respects the `cameraShake` setting). **Hit-stop**: freezes the struck entity's render state (position, age, walk) for 40-80 ms with a tremble. The caster's clip pauses too (`PalCasts.freeze`, `CastAnims.hold`). Triggered **only** by the powers' `Feel` payload. | CombatFeel.java:14-77; ClientPowers.java:149-155; Fx.java:224 | Melee weapons get none of it today. Needs a public API (for example `core.api.CombatFeedback.hitstop/shake`) and a melee trigger. | extend. V-CI |
| Damage glyphs (living) | `LivingDamageEvent.Post` for every damage: numbers in strokes of light at the hit point, coloured by Force; crits in gold with a flare. | living/combat/Hits.java:34; SPEC.md:2614 | Weapons get damage numbers for free. A weapon's element should map to a Force colour. | keep. V-CI |
| Vanilla damage | `knockback(power, xd, zd, src, dmg)`; i-frames; `damageCooldownTime = 0` allows multi-hits. | entities.md §5 | Combos and sweeping multi-hits need i-frame handling. | V-DOC |

### 2.5 VFX: trails, arcs, slashes

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| `FxRender` | One `SubmitCustomGeometryEvent` submit in four batches: additive meshes, additive sprites, lit sprites, full-bright sprites, plus a daylight underlay. Vertices are written camera-relative, with no allocation per frame. Any failure switches FX off for the session. | FxRender.java:22-120, 92 | The home for a third-person swing ribbon in world space. | extend. V-CI |
| `Shape.TRAIL` | A ribbon through **one** anchor's recent positions: a 20-entry ring buffer pushed **once per tick**, camera-facing width, tapering. | Shape.java:13, 69, 117-123; FxRender.java:681-697 | Wrong for blades. It tracks one point, not hilt and tip; at 20 Hz a 4-8 tick swing gives 4-8 points, so the arc is polygonal; it faces the camera instead of lying in the blade's plane. Needs a new **SWEEP** shape: a two-edge strip sampled per frame, or analytically from the clip. | add a new shape |
| `Anchors.HAND` | Estimates the hand position from look vectors (first person: 0.32 to the side, 0.55 forward, 0.3 down). | Anchors.java:64-87 | Not the animated hand or the blade tip. | replace for weapons |
| SLASH sprite | An animated additive slash flash. | FxRecipes.java:381, 825 | A cheap impact flash. | reuse |
| Geometry attached to a body | `WingLayer` is a `RenderLayer` on `AvatarRenderer` that calls `getParentModel().body.translateAndRotate`, then `submitCustomGeometry` with `entityTranslucentEmissive` plus `energySwirl` at `order(1)`. `HoloOthers` calls `getParentModel().translateToHand(state, arm, pose)`. `AscentRender` draws the rope as a ribbon with a u-along texture. | WingLayer.java:165-200; HoloOthers.java:146-153; AscentRender.java:103-123 | A 3P blade trail can be captured in the arm's frame, which follows PAL's posed arm. | reuse. V-CI |
| Custom render types | `RegisterRenderPipelinesEvent` + `RenderType.create(name, RenderSetup.builder(pipeline)…)`. | HoloPipelines.java:79-92; client.md §6 | Available for a heat-haze or distortion trail. | keep. V-CI |
| Geometry in the hand pass | Custom geometry can be submitted inside `RenderHandEvent` (Wrist), but the pass gives no depth (Wrist culls faces itself), and Lens can't read first-person depth. | Wrist.java:156-157, 219; SPEC §14 (Lens, the first-person arm) | A first-person trail must be drawn in the hand pass with manual ordering. Whether a 3D JSON weapon occludes itself correctly in first person needs checking (S: vanilla 3D block items do). | investigate |

### 2.6 Audio

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| `audiokit.py` | numpy-only synthesis: perc and ADSR envelopes, glides, modal, glass, bell, bar, piano, pluck, FM, flute, choir, strings, pad, drone, sub boom; coloured noise; FFT and STFT filters; band noise; crackle; chorus, echo, convolution reverb on synthesised IRs. Also K-weighted loudness, gated RMS, true peak (×4), a look-ahead limiter, `master()`, and bit-exact Ogg through ffmpeg. Seeded. | audiokit.py:1-906 | Everything a swish, impact, clang or ring needs. | keep |
| Combat templates that exist | The Ritual's `swing` (pink and white noise band sweeps under a pass-by envelope, a sine push, air) and `impact` (boom + slab modes + thud + crack + debris + puff). `audio_rig` has thump, sweep, grit, grind and burst. Also `body_impact` and fauna thuds. | ritual_sounds.py:682-717; audio_rig.py:60-95; audio_body.py:136 | The basis for parametric swishes (blade length, speed, mass) and impacts per material. | extend into `arms_sounds.py` |
| Current combat sounds | `power.punch`, `power.impact`, `power.slam` and `power.whoosh` point at **vanilla events** (`player.attack.strong`, `mace.smash_ground`, `breeze.wind_burst`). Gear blades use vanilla attack sounds. Synthesized: `story.ritual.swing`/`impact`, `body.impact`, `living.crit`, `gate.rig.windup`, 188 fauna hurt/death/roar events. | merge/powers sounds.json; merge/{story,body,living,gates,fauna} | **There is no own melee palette.** The creature side of a hit is covered for Awaken's creatures. | build |
| `audio_build.py` | Renders the listed specs, masters each to its category, encodes into `sounds/`, writes `merge/audio` sounds.json and lang. | audio_build.py:1-40 | A new module adds its own spec and writes `merge/arms`. | extend |
| `audio_mix.py` | Gated RMS, LUFS and true peak per file. Categories come from the file-name prefix: music −19±2 / −1 dBTP, ui −24±3 / −3, amb −28±3 / −6, **sfx −20±4 / −1**, plus the layered mixes. `--fix`; `--ref` for vanilla calibration (needs the Mac launcher assets). | audio_mix.py:30-45, 57-66; audio.md:188-201 | The doc calls sfx "a generic window, not a rule", but the script counts misses toward its exit code. **No combat category**: swings, impacts and clangs need their own targets, calibrated against vanilla `player.attack.*`. | extend |
| `audio_sheet.py`, `audio_listen.py` | Spectrogram contact sheets (RMS, LUFS, TP, clipping ticks). An HTML listening page with keep / remake / cut, notes and JSON export. | headers | Per-weapon sound QA, and the owner's review loop for hundreds of sounds. | extend |
| `audio_score.py` | A music arranger, **not** a quality score. | header | n/a | |
| GameTest `audio_events_resolve` | Every event is in sounds.json and registered, has a subtitle and lang line, every file is Ogg at 44.1 kHz, and positional files are mono. | audio.md "Tests" | New weapon events are covered automatically. | keep. V-CI |

### 2.7 Build, modules and merge

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| `mergeResources` | Reads `src/main/merge/<module>/…`. Objects merge by key, and a **duplicate key fails the build** naming both modules. `{"values":[…]}` tag lists are unioned. Anything else fails with "Cannot merge". | build.gradle:205-245 | An `arms` module adds `merge/arms/assets/awaken/{lang/en_us.json, sounds.json}`, `data/minecraft/tags/item/{swords, enchantable/…}.json` and `data/awaken/tags/...` without touching anyone's files. | keep. V-CI |
| Per-file assets | `items/<id>.json`, `models/item/<sub>/<id>.json`, `textures/item/<sub>/<id>.png`, `player_animations/<file>.json`. Three clip files coexist and clip ids are global (`awaken:<clip>`). | assets folders; PalCasts.java:107-110 | Use an `arms/` subfolder and an `arms_` clip prefix. Clip names must be unique across files. | rule |
| Module rules | Own DeferredRegisters, payloads, GameTests, `/awaken arms …`; a SPEC §1 row and §12 public APIs. | CLAUDE.md; SPEC.md:23-55 | New APIs needed: `FirstPerson` (Hands), `CombatFeedback` (moved out of powers), a hit-direction input for Life (mobs), and PlayerClips pause and speed. | SPEC edits |
| `tools/qa/check_assets.py` | After a GameTest run, checks that every registered item has `items/<id>.json` and a lang key, and every block has a blockstate. | check_assets.py | Checks presence only. | extend (models and textures exist, parents resolve) |
| CI | Only `./gradlew build` and `runGameTestServer` on Ubuntu with Java 25. Branches `build/**`, `pack/**`, `lands/**`, `carillon/*`. **Python generators and checks never run.** | .github/workflows/gametests.yml | Weapon checks must be (a) run by builders, (b) mirrored as GameTests that read generated JSON from the classpath (like `power_fx_cast_clips`, PowerFxTests.java:135-175), or (c) a new CI step (Python, PIL, numpy and ffmpeg install easily on Ubuntu). | decide |
| Autopilot | Owner's Mac only (`sed -i ''`, SDL hints). Scripted tours take screenshots, and `/awaken ascent pilot rec` records frames that `tools/ascent/clip.py` turns into an mp4 and a contact sheet. | tools/autopilot.sh; tools/ascent/clip.py; autopilot/creature-life.txt | The only way to see real in-game renders (FP and shoulder view, PACK=feel or none). It runs a client, so ask Euan first. | reuse for per-batch review |

### 2.8 Dependencies and the pack

- `build.gradle:147-163`, all `compileOnly`: Distant Horizons, Iris, **PAL (`xJ30QmpE` = 1.2.8)**, Camera Overhaul,
  Shoulder Surfing, JEI, Jade. **No GeckoLib, no Better Combat, no Bendable Cuboids compile dependency** (Bendable
  Cuboids needs no code).
- Pack `feel` profile (`pack/mods.json`, `pack/lock.json`): PAL 1.2.8 (client required, server optional), Bendable
  Cuboids 2.1.0, Camera Overhaul 2.1.2, Shoulder Surfing 26.3-5.2.0, Not Enough Animations 1.12.6, 3D Skin Layers
  1.11.4, EMF 3.3.12, ETF 7.2.6, Fresh Animations 1.11.1.
- `pack/lock.json` records versions and SHA-512 but **no licence field**. Licences are listed in §2.3 (V-WEB).

---------------------------------------------------------------------------------------------------------------------

## 3. What doesn't exist (the gap list)

1. A 3D item model generator; any item with `elements`; an item-model validator (city_kit's needs `.ref`).
2. Weapon archetypes, movesets and an attack timing table. Today weapons hit with vanilla's swing, vanilla's sounds
   and vanilla's instant hit.
3. A first-person system (Hands is a plan, and it excludes attacking). Weapon swings in first person are vanilla.
4. Third-person custom swings without PAL. The no-PAL arm route is proven for one static pose only.
5. A blade trail (TRAIL is single-point, per tick and camera-facing).
6. Melee hit feel: hit-stop and shake belong to powers; Life's stagger is camera-relative with a fixed strength;
   vanilla mobs get nothing.
7. A combat sound palette and a loudness category for it.
8. Portable previews (`fauna_rig.STEVE` and city_kit and structures' `VANILLA` paths point at `.ref`) and Python
   checks in CI.
9. Licence tracking in `pack/lock.json`.

---------------------------------------------------------------------------------------------------------------------

## 4. Sketch: weapon spec → model + textures + FP/3P clips + sounds + preview + checks

### 4.1 Data

```python
# tools/art/specs/arms_catalog.py (a sketch)
weapon("ember_fang", archetype="sabre", tier="C",
       materials=("ember_steel", "brass", "obsidian"),           # pixelart ramps (+ local ramps)
       parts=dict(blade="curved_fuller", guard="swept", grip="wrapped", pommel="gem"),
       motif=dict(runes="ember", glow=("fuller", 0.7)),          # emissive regions
       ability="flame_arc", element="heat", seed="ember_fang")
```

An **archetype** is shared by many weapons. Roughly 12-16 would cover hundreds: dagger, sword, longsword or
greatsword, sabre or katana, rapier, axe, greataxe, hammer or mace, spear, glaive or halberd, scythe,
gauntlets, whip or chain blade, staff, twin blades, shield. Each one holds:
- a **part grammar** with parameter ranges (blade length and width profile, fuller, edge bevel, guard shape, grip
  length, pommel);
- the **grip point** and `display` transforms per context;
- a **moveset**: light 1-3, heavy, ability, maybe guard or parry;
- a **timing table** per attack (windup, active ticks, recovery, combo window, cancel window), scaled by attack speed;
- a **hit shape** (arc in degrees, reach, height band);
- **trail anchors** (hilt and tip in item space) and a trail style;
- a **sound kit** (swish by length and mass, impact by material).

Clips and sounds belong to the archetype, not the weapon. The clip check cost about 0.14 s per clip here (84 clips in
11.9 s), so hundreds of weapons stay cheap.

### 4.2 Outputs, and what each reuses

| Output | Where | Generator | Reuses |
|---|---|---|---|
| GUI icon (2D 16×16) | `textures/item/arms/<id>.png` | `arms_icons.py`, the same part grammar drawn along the diagonal | pixelart RAMPS, gear.py `diag_band`/`grip`/`pommel`, items_crafts `check()` |
| 3D held model | `models/item/arms/<id>.json` + `textures/item/arms/<id>_3d.png` (+ `_glow`) | `arms_models.py`: grammar → box set (cross-sections stacked so the edge is thinner than the spine, guard proud, fuller inset) → greedy merge → elements with per-face UVs in a packed atlas; Painter paints faces; emissive overlay elements | fauna_rig `Rig`/`Cube`/`pack()`/`Painter`; ladder3d's per-face UV atlas; gate_rig emissive overlays |
| Item definition | `items/<id>.json`: select on `display_context` (gui, ground, fixed → 2D icon; hands → 3D, if 26.3 confirms it), plus a custom select for states | arms content | `CorePowerSelect` pattern |
| 3P clips | `player_animations/arms_moves.json` (+ `_bent`); a no-PAL keyframe table for the custom ArmPose | `arms_moves.py` | power_casts figure terms, `Clip`, `bake()`, `check()`; citizen_clips for NPCs |
| FP clips | `fp_clips/arms_*.json` (the Hands format plus an **item channel**) | `fp_moves.py` (extended) | Hands plan §3 |
| Timing table | `data/awaken/arms/archetypes/<a>.json` (schemaVersion) or generated Java data | the one source for server hit timing, FP, 3P, trails and sound offsets | the HeroData/HeroRig pattern |
| Trail textures | `textures/arms/trail_<style>.png` | `arms_fx.py` | ascent_fx smooth light textures |
| Sounds | `sounds/arms/<kit>_<event>_<n>.ogg`; `merge/arms` sounds.json + subtitles | `arms_sounds.py` (3-4 seeded variants per event) | audiokit; Ritual swing/impact; audio_mix, audio_sheet, audio_listen |
| Lang, tags, recipes, loot | `merge/arms/...`, `data/awaken/recipe/...` | arms content | gear.py `content()` |
| Java data | `dev/awaken/arms/ArmsData.java` (generated, plain data, loads on the server) | arms content | HeroData → HeroRig |
| Preview sheet per weapon | outside the repo | `arms_preview.py`: icon at 1×/2×/4× on grey and night; 3D turntable; in hand on a figure in 3P (key frames of each attack); FP strip from the eye; the trail overlaid; spectrogram tiles | `fauna_rig.render` or gear `Scene`; **gear `_skin()` stand-in instead of the `.ref` Steve**; power_casts strips; audio_sheet |

Runtime pieces (new Java), with the existing hooks each would use:
- **Third person**: a PAL layer through an extended `PlayerClips` (pause, speed). Without PAL, a weapon ArmPose
  enum extension plus a body-twist modifier and a lean (the CastAnims pattern).
- **First person**: Hands' Action and Stance layers. Cancel `RenderHandEvent` for the weapon hand, draw the arm with
  `renderRightHand`, and submit `firstPersonHandsAndItems.mainHandRenderState` posed by the FP clip's item channel.
- **Trails**: 3P from a `RenderLayer` (`translateToHand`) that records hilt and tip in world space every frame into a
  ring buffer, drawn by a new FxRender SWEEP shape. FP drawn inside the hand pass (submitCustomGeometry with manual
  face order).
- **Hit feel**: a public `CombatFeedback` (hit-stop and shake, moved out of powers), and a hit direction plus strength
  given to `Life` (mobs API).

### 4.3 Automatic checks per weapon

Builders run a Python gate. The structural parts are mirrored as classpath GameTests so CI catches them.

- **Model**: elements inside bounds; only legal rotation angles; every face has a non-degenerate UV inside the
  texture; **uniform texel density** (UV size matches face size, which keeps the Minecraft look); no coplanar
  overlapping faces (z-fighting); a quad budget (for example ≤ 400); emissive faces only where glow texels are; the grip
  at the display origin; the silhouette fills an agreed share of the GUI, FP and 3P frames.
- **Texture**: palette membership; no partial alpha except glow; at least 9 colours; value contrast per material;
  distinct from siblings (hash, plus silhouette overlap at 16×16 and at 10 blocks using `power_casts.silhouette`);
  deterministic.
- **Animation**: the penetration check extended with the **blade as a segment** against the head, torso and legs;
  FP and 3P use the same timing row; the hit frame falls inside the active window; the **trail arc covers the
  server's hit arc** (in the spirit of the `power_fx_reach_matches_hits` test); `_bent` twins present; clip bones
  allowed (as `power_fx_cast_clips` checks).
- **Audio**: a new `arms` category target and true-peak ceiling; length limits; the impact's onset lands on the hit
  frame; variants differ (spectral distance); positional files mono; subtitles; covered by `audio_events_resolve`.
- **Java and GameTests**: every id resolves (item, model, lang, sound event, clip); `check_assets.py` extended to models
  and textures; a server test swings each archetype at a line-up and checks hits match the arc and reach.
- **In game**: one autopilot tour per batch recording FP and shoulder-view clips, with and without PACK=feel, made
  into contact sheets by `clip.py`. Owner's Mac only; ask Euan before running a client.

---------------------------------------------------------------------------------------------------------------------

## 5. Decisions for Euan

1. **PAL**: keep it optional and build the no-PAL ArmPose fallback for weapon arms, or make it required (jar-in-jar;
   the MIT licence allows it) and amend SPEC §14.
2. **Hit timing**: a vanilla instant hit with a 1-tick anticipation (the Hands rule: "input never waits"), or delayed
   active frames (Better Combat style, which reads as lag). Suggested: light attacks hit on the click; only heavy
   attacks and abilities wind up, since holding the button shows intent.
3. **Items**: one registered item per weapon (gear's way, which JEI, recipes, tags and `check_assets` already
   assume), or a few archetype items with data components and a select (the Power Core way).
4. **3D technique**: JSON elements (baked, cheap, GUI and ground views come free) for nearly everything; a special
   renderer only for a few legendary or animated weapons, once `RegisterSpecialModelRendererEvent` is verified.
5. **Hands scope**: amend it to own weapon swings (it currently excludes attacking).
6. **CI**: whether Python checks join CI or are mirrored as GameTests.

---------------------------------------------------------------------------------------------------------------------

## 6. Check these in `.ref` before planning code

- `src/net/minecraft/client/renderer/block/model/BlockElement*.java` (and its rotation codec): legal angles, multiple
  axes, coordinate bounds.
- `assets/items/trident.json`, `spyglass.json`: the `display_context` select and `minecraft:special`.
- `neo/…/client/event/`: `RegisterSpecialModelRendererEvent`, `RegisterItemModelsEvent`,
  `RegisterConditionalItemModelPropertyEvent`, `RegisterRangeSelectItemModelPropertyEvent`.
- `DataComponents`: `SWING_ANIMATION`, `ATTACK_RANGE`, `KINETIC_WEAPON`, `PIERCING_WEAPON`, `MINIMUM_ATTACK_CHARGE`;
  `SwingAnimation`'s type and duration.
- The item renderer: whether it applies element `light_emission` (BakedQuad light emission) to items.
- `FirstPersonHandsAndItemsRenderer`: when `RenderHandEvent` fires relative to the attack-swing transform, and whether a
  swing-progress getter exists.
- `HumanoidModel.setupAnim`: whether the ArmPose transformer runs before `setupAttackAnimation` (then zero
  `attackTime` in a render-state modifier).
- `IClientItemExtensions` in 26.3 (hand transform, arm pose).
- PAL 1.2.8: item bones, the first-person mode, the speed modifier API. Bendable Cuboids: does the held item follow a
  bent forearm?
