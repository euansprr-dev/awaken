# Arms: the weapons and combat plan for AWAKEN (plan, awaiting Euan's approval)

Status: plan only, 2026-10-25. No game code until Euan approves. Branch `pack/combat-plan`. Approval page:
`docs/combat/plan.html` (self-contained; the orchestrator publishes it). Handoff: `docs/handoff/pack-combat-plan.md`.
Euan's brief and his process instructions are quoted word for word in the handoff.

How to read this: §1 is what Euan needs and how we'll know we got there. §2 is what the code does today, with
evidence. §3 compares approaches and recommends one. §4 is the design as a contract. §5 shows it with before/after
walkthroughs. §6 is evaluation and delivery. §7 is the decisions only Euan can make. Labels: **VERIFIED** (read in this
repo's code, or in the 26.3 jars), **RESEARCH** (an outside source, cited in §8), **HYPOTHESIS** (to be tested),
**PROPOSAL** (not built, nothing measured).

---------------------------------------------------------------------------------------------------------------------

## 1. Understanding and success contract

### 1.1 The brief, restated
- **The job.** Make fighting in AWAKEN feel like its movement already does: a different game, "magical", the best
  melee in Minecraft. Every fight, not only bosses: hitting, being hit, blocking, the sounds.
- **The pain (verified in §2).** Melee is vanilla's click-and-wait swing. There is no parry, no block of our own and no
  dodge, so a boss fight is a damage race the player loses: with a diamond sword the player dies in 1–6 s standing in
  a Lord's reach, after landing 3–11% of its health. Weapons are 18 flat 2D sticks with no abilities.
- **The desired experience.** Hundreds of truly 3D weapons, each a way of fighting with its own ability; custom swing
  animations; parry, block and dodge on every weapon; hits with weight (hit-stop, sound, camera, reaction); combos
  that flow without effort but reward rhythm and timing; weapons that merge with the powers; rare drops you remember;
  found naturally over time or hunted on purpose.
- **His proposed solutions (to test, not to assume):** automatic combos; parry on all weapons; a dodge; custom
  animations; borrowing from other mods' code.
- **Hard constraints.** Few keys ("not 50 keybinds"). A skill ceiling. Server authoritative; multiplayer; SPEC perf
  budgets (p50 ≥ 110 fps in a full gate fight on an M4, server tick p95 ≤ 25 ms, ≤ 60 combat entities). Public repo:
  only licence-compatible code or assets, with attribution; nothing of Mojang's. The patent rule (SPEC header). The
  approved feel of the Ascent stays as it is. Combat animations share the first-person system planned in Hands
  (`origin/pack/fp-anims:docs/anims/PLAN.md`).
- **Negotiable.** The SPEC's "no invincibility frames anywhere" (§13, Awakened Body), "Dash stays the dodge"
  (`docs/proposals/awakened-body.md:37`), Hands' "attacking stays vanilla" (its §5), whether PAL stays optional.

### 1.2 "Magical", as observable behaviour
| Word | What a player can see or measure |
|---|---|
| Satisfying hit | Every connecting blow has, on the same frame: a short freeze of both bodies (45–220 ms by weight), a sound made of 3 layers (swish, material impact, sweetener), a camera kick along the swing, sparks of the right material, and the target's body recoiling *in the blow's direction* by the blow's strength. A dagger and a maul feel different with your eyes closed. |
| Satisfying defence | A deflect rings, throws sparks off both blades, freezes 100 ms, and visibly staggers the attacker. A block shudders the weapon in your hands. A perfect dodge slows the attacker for a beat. |
| Intuitive | Three verbs on three inputs: attack (left mouse), guard and deflect (right mouse), dodge (the sprint key with any direction but forward). Everything else is context: how long you hold, when you press, where your body is (sliding, falling, on a wall, on a rope). |
| Skill ceiling | Rhythm (Cadence), deflect timing, perfect dodges, charge releases on the glint, cancels, traversal strikes, Breaks and Executions, Weaves with powers. A beginner who clicks and holds right mouse survives a D Lord; an expert ends it in a third of the time with most of their health. |
| "I didn't know this was possible" | A Lord's swing deflected and the world dropping into a slow beat; sliding under a charge and cutting its legs; firing the rope at a Lord, reeling in and landing an Execution; a parry followed by a Heat cast that sets the riposte ablaze (a Weave you found by accident, now in your Codex); a 3D weapon that reforms between swings. |

**Strong result:** Euan plays a D Lord with the slice weapon and a diamond sword and says the fight feels like a
different game, at no point fighting the controls; the sim shows skill, not gear, decides survival.
**Weak result:** parry and dodge exist but feel like vanilla with extra buttons (no weight, no readable tells, timing
blurred by latency); weapons are recolours.
**Unacceptable:** input lag on attacks; new keys beyond one optional dodge key; any regression of the Ascent's feel;
other systems broken (momentum hits, gear procs, powers, glyphs); a boss that can still kill you with unreadable hits;
a per-tick allocation in a hot path; copying code or art whose licence forbids it.

### 1.3 Outcome contract
| Dimension | Contract |
|---|---|
| Usefulness | Boss and everyday fights are won by reading and answering, not by stats. Any melee weapon, vanilla included, gets guard, deflect and dodge. |
| Correctness | One timing table per move feeds the server's hit, the first-person clip, the third-person clip, the trail and the sound offsets: what you see is what hits. Every number lives in SPEC. Patent rule holds. |
| Effort | No new key required. No stamina bar. No menu to learn combos: they happen by pressing; mastery adds to the same presses. |
| Responsiveness | The swing starts on the frame of the click (no wind-up wait); a light attack's hit lands 2–3 ticks after the click; hit-stop and impact sound play on the client at predicted contact, the server confirms. |
| Control | Automatic: combo continuation, soft focus (≤ 15° aim help), traversal strikes from context. Player-chosen: deflect vs block, dodge, charge, Art. Every feel layer has a comfort switch and respects reduced motion. Server config `arms.enabled=false` restores vanilla melee. |
| Reliability | With 150 ms latency a deflect timed to what the player sees still lands (deflect grace). Without PAL, Hands or the pack, everything plays with fallbacks. With another combat mod installed, Arms steps aside for items it claims. |
| Cost | Builder time estimated in §6.5; no paid services; no new runtime dependency (PAL stays optional unless Euan decides otherwise). |

**Non-goals.** Directional combat (For Honor style: too many inputs). A stamina economy. Gore. Changing the Ascent's
motion. PvP balance beyond vanilla's rules (deflect and dodge work in PvP, but PvP isn't tuned in this plan). Ranged
weapon archetypes (bows, crossbows) beyond keeping them working: later.

**Must not regress.** Momentum hits and trips (`body/BodyServer.java:364-406`), every `LivingIncomingDamageEvent`
listener (§2.2), gear procs keyed on `PLAYER_ATTACK` (`gear/GearCombat.java:84-120`), power hit-stop, damage glyphs
(`living/combat/Hits.java:34-52`), the free-run and tactical sprint inputs, the Ritual of the Bell's scripted fight,
the gate run's rules (Satchel, death), the 461+ GameTests.

---------------------------------------------------------------------------------------------------------------------

## 2. Verified audit and diagnosis

Four audits read the code and a fifth researched outside sources; their full reports, read-only snapshots at
`d12ea34`, are in `docs/combat/audit/` (player combat, enemies and loot with every boss's attacks and the repro
arithmetic, the art pipeline, the 26.3 API check, external research and licences). The key evidence is cited here. Paths are under
`src/main/java/dev/awaken/` unless they start with `docs/`, `tools/` or `src/`. The decompiled `.ref/` sources are on
Euan's Mac only; the 26.3 client jar (unobfuscated), NeoForge 26.3.0.95 sources/userdev and PAL 1.2.8 were downloaded
to a scratch folder and read with `javap`, so vanilla claims below are **VERIFIED-jar** unless marked.

### 2.1 Map: component → what it does → evidence → consequence → verdict
| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Player melee | Vanilla `Player.attack`: cooldown, crit, sweep, knockback. AWAKEN has no swing, combo, reach or sweep code; it only multiplies. | audit §1.1; `Player.attack` order: AttackEntityEvent → strength → CriticalHitEvent → SweepAttackEvent → hurtOrSimulate → knockback → sweep → onAttack (jar) | Combos, heavies and custom swings must take the input and drive hits themselves | **Replace for Arms weapons; keep `attack()` as the damage call** |
| Damage stack | 16 `LivingIncomingDamageEvent` listeners: momentum (HIGH), VIT resistance, gate modifiers, story clamps, Ascension, Sovereign ×1.5, crafts, powers' buffs, gear (LOW) | `BodyServer.java:374-392`, `ProgressionModule.java:320-341`, `PowerEvents.java:91-171`, `GearCombat.java:49-251` | A combat stage must run first (HIGHEST) and keep every other listener | **Keep; add Arms stage** |
| Stats | STR +2.5%/pt attack damage, AGI +1%/pt attack speed (cap 60%), VIT +0.6 HP and ≤ 35% resistance, PER crit ≤ 35% ×1.6 | `Progression.java:294-297`, `ProgressionModule.java:320-352` | Weapons compose with these; AGI scales the move clock | **Keep** |
| Melee scaling | Melee scales only with STR, gear, temper; powers scale ×(1+0.075·lv) and cap at 20% of boss HP per hit | `powers/PowerMath.java:6-16,42-63` | On-level melee can't keep up with Lord health ×(1+0.075·lv) | **Extend: item level** |
| Defence | No player parry, block, guard, posture, dodge. Brace (−60% 3 s), Foresight (cancel + blink), Veil Flicker (chance). Vanilla shield unlistened. | `KineticPowers.java:309-331`, `MindPowers.java:226-255`, `GearCombat.java:231-243`; no `LivingShieldBlockEvent` listener | The core gap | **Build** |
| Dash as the dodge | A power, forward-only, unreachable from Left Alt once Tether owns it | `VelocityPowers.java:96-104`, `ClientCasting.java:51`, `BodyClient.java:86-88` | Can't be the universal dodge | **Replace by a body dodge; Dash stays a power** |
| I-frames | Vanilla: a hit sets `damageCooldownTime` 20; above 10, smaller hits drop, bigger ones deal the excess (≈10 ticks immune). AWAKEN only removes them; SPEC: "No invincibility frames anywhere" | jar; `powers/Combat.java:113,128`; `docs/SPEC.md:623` | Fast combos must manage i-frames; an evasive dodge needs a SPEC change | **Decision (§7)** |
| Hit-stop | Powers only: freezes the struck entity's render state 20–80 ms with a ±0.04 tremble, attacker's client only, plus a shake ≤ 8° | `powers/Fx.java:223-233`, `powers/client/CombatFeel.java:21-76`, `ClientPowers.java:149-155` | Melee has none; the mechanism is right, its owner is wrong | **Extend into `core.api.CombatFeedback`** |
| Camera | Four independent writers (CombatFeel, FxScreen, FeelDirector, BodyCamera) plus Camera Overhaul; FeelDirector is "one voice" for the Ascent only; BodyCamera's 32° roll dip ignores reduced motion | `FeelDirector.java:18-310`, `BodyCamera.java:41-89` | Combat beats would stack unpredictably | **Extend FeelDirector into the one camera voice** |
| Comfort | 4–5 config files, two `reducedMotion` switches (feel, holo) | `FeelConfig.java:18-45`, `HoloConfig.java:29,40` | Combat must read one source | **Consolidate (read-only `Comfort`)** |
| Getting hit | Vanilla hurt sound, red tint, tilt; no vignette, no low-HP cue, no direction; damage taken never printed | audit §3 | Half of "the feeling of getting hit" missing | **Build** |
| Sounds | Melee and hurt are vanilla; ours: `body.impact`, `living.crit`; power punch/impact/whoosh point at vanilla events | `merge/powers/.../sounds.json`, `audio_body.py:136` | No combat palette; audiokit can make one | **Build with audiokit** |
| Damage glyphs | Attacker-only, 700 ms, Force colour, crit gold, merge within 6 ticks | `living/combat/Hits.java:34-52`, `LivingFx.java:54-92` | Free for weapons; add kinds (DEFLECT, BREAK) | **Extend** |
| Plates | 10-segment health on Holo glass, ≤ 4 at once | `living/client/LivingPlates.java`, SPEC §21.1 | The natural home for posture and the focus bracket | **Extend** |
| "In combat" | Four trackers that disagree (`Hits.inFight`, `PowerState.inCombat`, `SystemHud.inCombat`, `LayerWatch`) | audit §3 | Arms needs one "engaged" state | **Unify (server) behind `core.api.Engagement`** |
| Keys | 15 AWAKEN keys; sprint, sneak, jump, A/D, Left Alt carry movement; right mouse with a blade or empty hand is unused; sprint with back/side/no direction on the ground does nothing in vanilla | `AwakenKeys.java:18-30`, `ClientMoves.java:115-138`, `FreeRun.java:37-39` | Room for guard (RMB) and dodge (sprint + non-forward) with no new key | **Use** |
| Predicted-move template | Body Start/End/Refuse/Seen; server re-checks; 12-tick position history; latency knob and a 300 ms bench | `body/net/BodyPayloads.java`, `BodyServer.java:84-117`, `RunnerServer.java:29-80,544-641` | The template for dodge, guard and swings | **Reuse** |
| Ascent event pattern | Trusts client speed for the dive slam's damage | `AscentServer.java:256,296-317` | Must not be copied for damage or defence | **Avoid** |
| Boss melee | Ground Lords use vanilla `MeleeAttackGoal`: a swing every 20 ticks in reach with **no wind-up**; fliers dive-swing every 30–49 ticks | `mobs/entity/AwakenMob.java:175`, `MobGoals.java:113-123` | ~74% of the damage that kills you can't be read, so it can't be parried or dodged fairly | **Replace with told attacks** |
| Ability telegraphs | Wind-ups 14–58 ticks (+8 heavy), marked ground, aim locks 6 ticks before, heavy names on the action bar, 14-tick gap | `mobs/entity/AbilityRunner.java:121-194` | Excellent; the language every attack should speak | **Keep; extend to all attacks** |
| Life layer | `windup` set only from abilities; hurt recoil away from the camera at a fixed strength | `AwMobRenderer.java:90`, `mobs/client/life/Life.java:206-242,341-368` | Hits can't feel different by weapon | **Extend: hit vector + strength** |
| Boss control | Knockback resistance 1, unpushable, ⅓ stuns, nothing interrupts a wind-up | `MobDef.java:71`, `Combat.java:85-91,272-276`, `AbilityRunner.java:58-64` | Nothing for skill to act on | **Add posture and Break** |
| Lords with held weapons | Ember Tyrant (netherite axe), Drowned Archivist (trident), Mirror Knight (diamond sword): held weapon's damage probably added then scaled: Ember Tyrant at D 13.4 → 37.6 | `GateBestiary.java:96,117`, audit §4 (SUSPECTED, high confidence) | One-shots at D; must be settled before tuning | **Investigate (GameTest), mobs module** |
| Weapons | 6 Hunter blades, 6 foci, 6 mattocks: vanilla-twin stats, `item/handheld` 2D sprites, no abilities, crafted only; mobs never drop held weapons | `gear/HunterTier.java:31-44`, `GearItems.java:70-96`, `AwakenMob.java:132` | Nothing to discover | **Keep as the E–S forged line's ancestors; supersede** |
| 3D items | None of 393 item models has `elements`; `fauna_rig.py` is a full cuboid-rig generator (bones, packed UVs, painter, clips, checks, preview); `ladder3d.py` writes element models with a UV atlas | audit-pipeline §1 | The pipeline core exists | **Extend into `arms_models.py`** |
| Item model tech | Definitions: `model`, `select`, `condition`, `range_dispatch`, `special`, `composite`; display contexts incl. `gui` and first/third person; element rotations accept any angle; `RegisterSpecialModelRendererEvent`, `RegisterItemModelsEvent` exist | jar; `CorePowerSelect.java` | 2D icon in the GUI, 3D in the hand, from one item; any-angle blades | **Use (validate on a client)** |
| Vanilla weapon components | `blocks_attacks` (block delay, angle, reductions, sounds), `weapon`, `attack_range` (min/max reach, hitbox margin), `kinetic_weapon`, `piercing_weapon`, `attack_animation` (none/whack/stab + ticks), `minimum_attack_charge`, `use_effects`, `damage_type`; the spear exists | jar | Reach, block cosmetics and a vanilla-compatible fallback come free | **Reuse where they fit** |
| Animation | PAL 1.2.8 (MIT) optional, `compileOnly`, only `PalCasts` imports it; layers 1500 (casts), 1600 (moves); freeze for hit-stop. No-PAL route: `ArmPose` enum extension with `IArmPoseTransformer` (`HoloArmPose`) | `build.gradle:153`, `PalCasts.java:43-130`, `HoloArmPose.java:14-30` | 3P swings for pack players; a fallback for the rest | **Extend** |
| First person | `CastAnims` redraws the empty hand; with an item it only shifts the stack, so vanilla's swing still plays. Hands (plan only) gives one HIGHEST owner, stance/action/procedural layers, `fp_clips` JSON, one 3P/FP timing table, but keeps attacking vanilla | `CastAnims.java:350-504`; Hands PLAN §3, §5 | Weapons must live in Hands; its scope must grow | **Coordinate (§7)** |
| Trails | `Shape.TRAIL` follows one point, once a tick, camera-facing | `Shape.java:69,117`, `FxRender.java:681` | Wrong for blades | **New SWEEP shape** |
| Audio tooling | audiokit: synthesis, loudness, true peak, limiter; Ritual swing/impact templates; listening page; `audio_events_resolve` GameTest | `audiokit.py`, `ritual_sounds.py:682-717` | Hundreds of sounds are feasible | **Extend (`arms_sounds.py`, an `arms` loudness category)** |
| Drops | Light pillars for rare/epic ground drops; in a gate, loot goes silently into the view-only Satchel; the Lord's hoard has no reveal | `LootPillars.java:24-40`, `gates/run/Runs.java:665-711` | Rare weapons would arrive with no moment | **Extend: the reveal** |
| Discovery | `Progression.discover`, Codex "N / ??" sections, `markNewThing`, Truth/Title/Latent conditions, item components (`temper`, `power_core`) | `Progression.java:198-205,545-556`, `AlmanacSection.java` | Weapons, Weaves and mastery plug in | **Reuse** |
| Pacing sim | Models no fight: a Lord = 6% death risk and 4 minutes | `tools/sim/pacing_sim.py:193-197,238` | Can't tune combat | **Add `combat_sim.py`** |

### 2.2 Reproduction: a diamond sword against a Gate Lord (numbers from the code)
Inputs: diamond sword 7 (`gear/GearTests.java:293-296`), 1.6 swings/s base; level-17 and level-57 builds at 3 points
a level (≈42% STR, 30% VIT, 18% AGI, 10% PER); full unenchanted diamond armour; Lord health ×(1+0.075·lv)×14, damage
×(1+0.04·lv)×1.6, armour lv/12 (`mobs/MobScaling.java:30-84`, tested `mobs/MobsTests.java:46-66`). Armour formula,
mob swing interval and shield rules: vanilla 1.21 (confirmed in the 26.3 jar for i-frames; armour formula ASSUMED).

| Case | Lord HP | Your hits to kill / time | Its basic melee to you | You die after | Share of its HP you take first |
|---|---|---|---|---|---|
| D Gearwright Colossus (lv 17) | 1,083 | 103 / 61.8 s | 4.06 (no tell, every 20 t) | 7 swings, ~6 s | 11% |
| D Ember Tyrant (axe counted, SUSPECTED) | 956 | 93 / 55.8 s | 19.25 | 2 swings, ~1 s | 3.6% |
| D Drowned Archivist (trident counted) | 956 | 91 / 54.6 s | 16.01 | 2 swings, ~1 s | 3.7% |
| B Gearwright Colossus (lv 57) | 2,511 | 130 / 65 s | 7.81 | 7 swings, ~6 s | 11% |
| B Mirror Knight (sword + armour counted) | 2,216 | 177 / 88.5 s | 27.40 | 2 swings, ~1 s | 2.3% |
| B Colossus, Sharpness V + temper II + Trophy Hall | 2,511 | 74 / 37 s | 7.81 | ~6 s | 19% |

The scaling keeps the ratio fixed: at every rank the player takes about 11% of a Lord per life of standing in reach
and must avoid about 89% of the basic melee alone, before any ability. Gear upgrades move it to 14–32%.

**Where the experience first disappears.** Not in the damage numbers: in the *first unreadable swing*. The basic
melee has no wind-up (`AwakenMob.java:175`), the life layer never shows one (`AwMobRenderer.java:90`), and the player
has no verb to answer it. The told abilities (14–58 tick wind-ups) are the readable minority of the threat. A
parry added to today's Lords would have nothing fair to read.

### 2.3 Root causes, ranked
| Symptom | Cause | Evidence for | Evidence against | Smallest test | Impact if fixed |
|---|---|---|---|---|---|
| Can't survive a Lord | No defensive verb + untold melee | §2.2; no parry/block/dodge anywhere | Powers (Brace, Foresight) help some builds | combat sim: survival by profile, before/after | Very high |
| Fights are a stat race | Melee doesn't scale like Lord HP; bosses immune to control | `PowerMath`; `MobDef.java:71` | Temper and Sharpness help (+19%) | sim TTK on-level, with/without item level | High |
| Hits feel flat | No melee hit-stop, sound, camera, directional reaction | §2.1 rows Hit-stop, Sounds, Life | Momentum hit has a sound and dust | slice A/B: Euan blind-tests hits on/off | High |
| Swings look vanilla | No FP/3P weapon animation; Hands keeps attacking vanilla | `CastAnims.java:350-504`; Hands §5 | PAL casts look good | slice clips in both views | High |
| Weapons are boring | 18 stat sticks, 2D, no abilities, never dropped | `HunterTier.java`, item models | — | — | High (scale phase) |
| Three Lords one-shot | Held weapon damage scaled (SUSPECTED) | attribute maths | maybe intended | 10-line GameTest | Medium, urgent |
| Rare drops forgettable | Satchel swallows gate loot silently | `Runs.java:665-711` | Pillars exist outside gates | — | Medium |

Remaining hypotheses: (H1) a 4-tick deflect window plus a ≤ 3-tick server grace feels fair at 0–150 ms; (H2) client
predicted hit-stop rarely disagrees with the server (< 2% of hits at 150 ms); (H3) sprint + non-forward never fires a
dodge by accident during the free-run or a wall-run; (H4) JSON element models at any angle render cleanly in hand;
(H5) the no-PAL ArmPose fallback reads as a swing, not a twitch. Each has a test in §6.

---------------------------------------------------------------------------------------------------------------------

## 3. Alternatives and recommendation

| | A. Targeted repair | B. Structural: **Arms** (recommended) | C. Adopt a combat framework |
|---|---|---|---|
| What | Tell every boss melee, add a vanilla-shield-style block and a parry window to swords via `blocks_attacks`, melee hit-stop from `power_feel`, a sound pass | A combat module: data-driven archetypes and move timelines, server-run swings, guard/deflect/dodge on every melee weapon, posture and Break on enemies, one feel layer (hit-stop, camera, sound, reaction), weapons as generated 3D data, Weaves with powers; built in Hands and PAL | Depend on Epic Fight or Better Combat and theme around it |
| Quality | Survivable bosses; still vanilla swings, no combos, no 3D, no variety | All of the brief | Proven animation depth, but its feel, not ours |
| Fit | Fits | Reuses body networking, AbilityRunner, FX, glyphs, plates, audiokit, fauna_rig, PAL, Hands, discovery | Epic Fight is GPL-3 with its own model/animation system and stamina; Better Combat is All Rights Reserved (code can't be copied) and the pack plan excluded it for fighting our feel (`docs/plan-pack.html:425`) |
| Risk | Low | Medium: scope; latency on deflect; mod conflicts | High: version lag (26.3), mixins clash with Shoulder Surfing/Hands, licence |
| Cost | ~4–6 h | ~45–65 h builder time (§6.5) | Unknown; ongoing upkeep against another mod |

**Recommendation: B, staged so that A's fixes ship inside its first slice.** A alone answers "survive a boss" but not
"a different game", "hundreds of 3D weapons" or "custom animations". C trades our design language (the Holo, the
Ascent's springs, Hands) for someone else's and puts a licence and a version dependency on the core loop. B's slice
reaches the full feel on one weapon family before any scaling, so the expensive part (hundreds of weapons) only starts
once the feel is approved in Euan's hands. What would change the recommendation: if the slice's deflect can't be made
fair at 150 ms (fall back to block-only parry on the server's clock, A's version); if Euan rejects any new verb on right
mouse (then guard moves to sneak-while-attacking, a worse fit).

Overlooked opportunities this plan takes: the Ascent's movement becomes the combat's best skill expression
(traversal strikes); the Holo plates become the posture readout (no new HUD); the powers become weapon partners
(Weaves) instead of a separate bar; the Forge and the Codex already exist for discovery; the vanilla `attack_range`
and `blocks_attacks` components give reach and block cosmetics without custom code.

---------------------------------------------------------------------------------------------------------------------

## 4. Behaviour and architecture (the contract)

### 4.1 The language: three verbs, everything else is context
| Input | Verb | Detail |
|---|---|---|
| Left mouse, tap | **Strike** | The archetype's light chain (3–4 strikes, a 5th with mastery). Tapping again continues it; stopping resets after the recovery. |
| Left mouse, hold | **Heavy** | Charges from 6 ticks; a glint at the archetype's charge mark (e.g. 16 ticks); releasing within ±2 ticks of the glint is a perfect heavy. Held past full charge with full Resonance: the weapon's **Art**. |
| Right mouse, press | **Guard / Deflect** | The first 4 ticks after the press are the deflect window; then it's a block while held. |
| Sprint key + back, left, right or no direction (on the ground) | **Dodge** | A 6-tick step-roll, 2.6 blocks. Sprint + forward keeps meaning run and free-run. Optional rebindable Dodge key (unbound by default) and a "double-tap direction" option in settings. |
| Context | Riposte, Slipcut, Execution, traversal strikes, Weaves | No input of their own (table 4.3). |

Holding a vanilla sword, axe, trident or mace gives the same verbs with a plain moveset (§4.6). Bare hands and tools
stay vanilla. Shields keep vanilla blocking; a timed shield raise also deflects.

### 4.2 Timing (20 ticks a second; a tick is 50 ms; all values scale with attack speed)
The slice's archetype, the **Longsword**, as the template (`data/awaken/arms/archetype/longsword.json`):

| Move | Wind-up | Active | Recovery | Cadence window | Motion value | Posture damage | Notes |
|---|---|---|---|---|---|---|---|
| Strike 1 (right to left) | 2 | 2 | 6 | ticks 6–9 | 0.90 | 4 | swing starts on the click frame |
| Strike 2 (left to right) | 2 | 2 | 6 | 6–9 | 0.95 | 4 | |
| Strike 3 (overhead) | 3 | 2 | 8 | — | 1.40 | 8 | finisher |
| Strike 4 (thrust; mastery 2) | 3 | 2 | 10 | — | 1.70 | 10 | |
| Heavy (rising cut) | hold 6–24 | 3 | 10 | — | 1.8 (2.4 on the glint) | 14 (20) | glint at 16 |
| Riposte (strike ≤ 12 ticks after a deflect) | 1 | 2 | 8 | — | 2.5 | 20 | |
| Slipcut (strike ≤ 6 ticks after a dodge) | 1 | 2 | 7 | — | 1.2 | 6 | |
| Execution (heavy on a Broken foe) | 2 | 3 | 14 | — | 4.0 | — | Lords: capped at 8% of max HP |
| Guard raise | — | deflect ticks 0–3 | — | — | — | — | block after; 70% frontal reduction (120°) |
| Dodge | — | evade ticks 0–4 | 5–9 (strike-cancel from 6) | — | — | — | 2 charges, 24 ticks each |

- **Cadence.** Pressing inside a strike's cadence window (not mashing early, not late) earns a Cadence step: +8%
  damage and +1 posture per step, up to 3, and the next strike's wind-up shortens by a tick. Mashing still chains
  (inputs buffer for 5 ticks) but never earns Cadence. A Cadence-3 finisher fills Resonance faster. This is the
  rhythm skill: audible (the swish rises a semitone each step) and visible (the trail brightens).
- **Cancels.** A dodge can cancel any recovery (it drops Cadence to 0). Guard can cancel a wind-up (a feint).
  Strikes can't cancel strikes.
- **Input never waits for an animation** (Hands' rule): the clip begins on the click frame; the wind-up ticks are
  where the blade is already moving.

### 4.3 Defence and reward
- **Deflect.** An enemy attack of class LIGHT or HEAVY that lands during ticks 0–3 of a guard raise is deflected: no
  damage, the attacker takes posture (LIGHT 18, HEAVY 26), both bodies freeze 100 ms, sparks and a ring, Resonance +20,
  the riposte opens for 12 ticks. **Spam guard:** a raise within 8 ticks of a failed deflect can block but not deflect
  (a lockout of twice the window, Epic Fight's rule, rather than Sekiro's shrinking window, which at 20 ticks a second
  would shrink to one tick).
- **Block.** Guard held past the window: LIGHT hits take 70% less (archetype value), with a weapon shudder and a clang;
  HEAVY hits break guard (15-tick stagger, 50% less). The weapon's durability pays as `blocks_attacks.item_damage`.
- **Attack classes (enemies).** LIGHT (parry or block), HEAVY (amber flare: deflect or dodge; breaks guard), PERILOUS
  (red flash and a distinct sound: dodge only; most of today's HEAVY abilities), GRAB (dodge only). Every class has its
  own tell sound, so a fight can be read by ear.
- **Dodge.** Ticks 0–4 evade attacks (the SPEC amendment in §7). A hit that would have landed in ticks 0–2 is a
  **perfect dodge**: the attacker alone slows to 0.45 of time for 8 ticks (`powers/TimeSlow` scoped to one entity; the
  same beat as SPEC §17.15), the camera exhales, Resonance +15, Slipcut opens.
- **Posture (enemies only; no player stamina bar).** Normal mobs 20, elites 40, Lords 100 per phase. Regenerates 6/s
  (Lords 8/s) after 2 s without posture damage. Full: **Broken** for 50 ticks (Lords 40), the AbilityRunner's wind-up is
  interrupted (a new hook on the existing `interrupt()`), the plate's posture bar flashes, and a heavy becomes an
  Execution. Posture is on the struck creature itself, so the patent rule holds.
- **Resonance.** One meter per player, 0–100, from Cadence finishers, deflects, perfect dodges, Breaks; drains 2/s out
  of combat. Full: the blade glows and the next held heavy past full charge is the weapon's **Art**. Shown as a thin
  arc under the crosshair (and on the weapon's glow), nothing else.
- **Traversal strikes (the Ascent becomes combat).** Strike while sliding: **Undercut** (low sweep, posture ×2, trips
  as the slide does); falling 2+ blocks: **Plunge** (the Leaper's dive slam numbers, now from the server's own fall
  speed); wall-running: **Wall Cut**; with the rope latched on a creature, heavy: **Reel Strike** (pulled in, lands as
  a riposte); gliding: **Swoop**. Each uses the body's existing state; none adds a key.
- **Soft focus.** The creature you strike becomes your focus (a bracket on its plate). Strikes turn toward it by at
  most 15° and close at most 0.6 blocks. Looking 60° away for 1 s drops it. An optional lock key exists, unbound.

### 4.4 Magic: Weaves, attunement and Arts
- **Weaves.** For 12 ticks after a deflect, a perfect dodge, a Break or a Cadence-3 finisher, casting a power fuses
  with the weapon: 4 openings × 9 families = 36 Weaves, flavoured by the archetype's class (light, heavy, reach). Two
  examples: deflect + an Energy power (Heat) = Ember Riposte (the riposte ignites and spreads a ring of fire); perfect dodge + Shadow =
  Afterimage (a shadow clone finishes the Slipcut). The power costs 30% less energy inside a Weave. The first time a
  Weave happens the System names it ("WEAVE · Ember Riposte") and it joins the Codex's Weaves section (N / ??, never
  listing undiscovered ones). Data: `data/awaken/arms/weave/*.json`.
- **Attunement** (the Forge, Hearth's existing station): bind a weapon to a Force (HEAT, CHARGE, GRAVITY, SPIRIT, FROST)
  with essence and a Core Shard. Its Marks take that element; its hits prime your next power like the Gatewrought set
  already primes melee (`GearCombat.java:84-120`), both ways.
- **Arts.** Each archetype has a default Art; Lord-forged and legendary weapons have their own (a Lord's signature
  move, yours now: the Gearwright Colossus's maul does the Piston Slam).

### 4.5 Weapons: archetypes, rarity, count
Sixteen archetypes, each a play style (moveset, reach, timing, guard type, sound kit):
Longsword (balanced, the deflect weapon) · Twinblades (fast, Cadence, dodge-cancels) · Greatblade (slow, hyper-armour
on heavies, posture) · Spear (reach, thrust, sprint lunge) · Glaive (sweeping arcs, crowds) · Rapier (precision,
riposte crits) · Katana (sheathe stance: guard is the sheath; the draw cut) · Axe (cleave, guard break) · Maul
(blunt, armour break, ground slam) · Scythe (wide reap, Spirit, cuts Veil spirits) · Gauntlets (fist strings, counters
that throw) · Chain-blade (mid-range, pulls; pairs with the rope) · Daggers (backstab, the shortest dodge) ·
Shieldblade (blade and board: block-first, bash) · War-staff (hits charge energy; Weaves cheaper) · Precursor Arm
(Tech: an energy blade that changes length by combo step).

| Line | How it's found | Count | Look |
|---|---|---|---|
| Forged | blueprints at the Forge, E–S Hunter tiers (supersedes today's 6 blades) | 16 × 6 = 96 | the archetype's grammar, growing with tier |
| Named (rare, epic) | elites, gate caches, strata, camps, bounties; themed by the 15 gate themes and the regions | ~120 | theme materials, 1–2 Marks |
| Lord-forged | one per boss, with pity like Power Cores | 41 | the Lord's material and its move as the Art |
| Legendary | authored, tied to the world: Descent strata, Lucidium dreams (a blade that reforms each strike), the Veil, Above, the story's Sovereigns and the Rival | ~30 | unique model and moveset twist |
| **Total** | | **~290 distinct 3D weapons** | each passes the distinctness check (§4.9) |

Rarity colours follow the loot pillars: rare blue, epic violet, Lord-forged ember-gold, legendary white-gold.
**Marks** (~48, data-driven): trigger (hit, Cadence finisher, deflect, perfect dodge, Break, kill, guarding, perfect
heavy) × effect (a Force rider, posture, heal, energy, a short buff).
**Mastery** per archetype (5 steps, from use): adds to the same inputs (a 4th and 5th strike, a charged deflect, a
longer Slipcut), never a key.

### 4.6 Every melee weapon gets the verbs
Tags map vanilla and other mods' items to plain archetypes: `#minecraft:swords` → Longsword (plain), axes → Axe,
trident → Spear, mace → Maul. They get guard, deflect, dodge, Cadence and the feel layer, keep their own damage and
look (2D unless Euan chooses our own generated 3D models for vanilla swords, §7). A diamond sword is an E/D-tier
weapon: enough to survive with skill, slower than a found weapon (§6.1 targets).

### 4.7 The flow of one strike (server authoritative)
```
client  click ─ InteractionKeyMappingTriggered (cancel vanilla attack+swing for Arms weapons)
        ├ Arms predicts: move = chain(step, cadence), starts FP clip (Hands action layer) + 3P clip (PlayerClips/ArmPose)
        ├ sends arms_swing {seq, move, yaw, pitch, chargeTicks}
        └ at predicted contact (client positions): CombatFeedback.hitstop/kick/sound/sparks (cosmetic only)
server  ArmsServer.onSwing: weapon is Arms? not off()? move legal for state, cadence and clock? (else Refuse{seq})
        ├ ArmsMotion (unsaved attachment) runs the timeline; on active ticks: arc sweep vs entities in reach
        │    (shape from the archetype; attack_range hitbox margin; ≤ one hit per target per move)
        ├ per target: pending motion value (player, target, tick) → player.attack(target) with full strength
        │    → HIGHEST LivingIncomingDamageEvent: × motion value × cadence × item level; posture += move's value
        │    → every existing listener runs (momentum, VIT, gear, powers, crafts...) → glyphs, Force reactions, music
        ├ vanilla sweep cancelled for Arms weapons (SweepAttackEvent); i-frames set per hit with NeoForge's
        │    DamageContainer.setPostAttackInvulnerabilityTicks (a move's hits land; the same move can't hit twice)
        └ arms_seen to players within 48 (others' clips, trails, sparks); arms_result {seq, hits} to the attacker
```
Enemy attacks on a player run the reverse: the attacker's tell (synced like `DATA_TELEGRAPH`) → contact on the server
→ `Arms.resolveIncoming` at HIGHEST: deflect, perfect dodge, evade, block, or pass. **Deflect grace:** when a LIGHT or
HEAVY attack connects with an engaged player holding a guardable weapon, its damage is held for up to G ticks
(G = min(3, ⌈one-way latency / 50 ms⌉ + 1)); a guard raise arriving in that time is judged against the hit's tick.
Players see enemy swings late by the same latency, so the hold is invisible; it makes a deflect timed to the screen
land. Tested on the Runner's latency bench (§6.3).

### 4.8 Feel: one layer, shared
- **`core.api.CombatFeedback`** (client; moved from powers' package-private `CombatFeel`): `hitstop(entity, ms)`,
  `holdHands(ms)`, `kick(yawDeg, pitchDeg, rollDeg)`, `impact(pos, weaponMaterial, targetMaterial, weight)`. Powers call
  the same API (no behaviour change for them).
- **Hit-stop by weight** (HYPOTHESIS, tuned in the slice): light 45 ms, finisher 70, heavy 90, perfect heavy 110,
  deflect 100 (both), Break 160, Execution 220 with a slow beat. Victim: render freeze + tremble (exists); attacker:
  hands and camera hold (new); never server time.
- **One camera voice.** FeelDirector gains combat moments (`strike`, `deflect`, `blocked`, `hurt(dir)`, `break`); the
  others (CombatFeel, FxScreen, BodyCamera) route through it; one comfort source; reduced motion halves; the 32° roll
  dip respects it (a fix). ActionCamera gets a COMBAT pose: a little closer and lower, leaning toward the focus.
- **Reaction.** `CreatureLife` takes a hit vector and strength (blow direction from the swing plane, strength from
  motion value × weapon mass), so a dagger flicks and a maul folds the body. Vanilla bodies: knockback per archetype.
- **Getting hit.** A Holo shard at the screen edge toward the attacker (300 ms); the camera jolts away from the blow;
  our own hurt sounds by damage kind; under 30% health a slow heartbeat and a Lens look (`arms:low_hp`, edges cooled
  and desaturated), under reduced motion only the sound and a static edge.
- **Sound kit per archetype** (`tools/art/specs/arms_sounds.py`, audiokit): swish by length, speed and Cadence step;
  impact by weapon material (edge, blunt, point) × target material (flesh, armour, stone, wood, glass, ghost, slime);
  sweeteners (crit glass, Force element, Break crack, deflect ring, guard clang, perfect-dodge rush, Execution boom);
  enemy tells by class; 3–4 seeded variants each; an `arms` loudness category calibrated against vanilla's
  `player.attack.*` (Mac `--ref`).
- **FX.** A new FxRender **SWEEP** shape: two edges (hilt, tip) sampled every frame from the posed weapon (3P through
  `translateToHand` in a render layer, FP inside the hand pass), a ribbon lying in the blade's plane, tinted by
  element and Cadence. Sparks of the struck material. The SLASH sprite as an impact flash. Glyph kinds DEFLECT, BREAK,
  PERFECT beside the damage numbers.

### 4.9 Animation: inside Hands and PAL
- **First person: Hands' layers.** Stance = the archetype's idle and guard; Action = strikes, heavies, deflects,
  Arts; Procedural = recoil springs, the hit-stop hold, block shudder. Hands' clip format gains an **item channel**
  (the weapon's offset and rotation in the hand, per key), **markers** (active window, cadence window, cancel window,
  contact) and **trail anchors**. Authoring in `fp_moves.py` with the shared timing table: the Arms archetype JSON *is*
  that table for weapons.
- **Third person.** PAL clips in `player_animations/arms_*.json` (+ `_bent`) through an extended `PlayerClips`
  (`speed`, `pause`); without PAL an `AWAKEN_ARMS_*` ArmPose enum extension keyframes the arms and torso twist (no
  knees). A layer priority above casts (1500) and moves (1600): 1700, so a slide-strike shows the strike over the
  slide's legs.
- **Others see it** through `arms_seen` (move id + seq), started on their clock (the body's pattern).

### 4.10 Content pipeline (tools/art)
```
arms_catalog.py   one line per weapon: archetype, tier, materials, parts, motif, Marks, Art, element, seed
     │
     ├ arms_models.py  part grammar → boxes → greedy merge → element JSON with a packed atlas (+ _glow), any angle
     ├ arms_icons.py   the same grammar along the diagonal at 16×16 (GUI, ground)
     ├ items/<id>.json select on display_context: gui/ground/fixed → icon, hands → 3D
     ├ arms_moves.py   per archetype: 3P clips (+_bent), FP clips (Hands format + item channel), the timing table
     ├ arms_sounds.py  per archetype kit + per-weapon sweetener
     ├ ArmsData.java   generated plain data (ids, stats, Marks, Art) registered at init
     └ arms_preview.py contact sheet per weapon: icon 1×/2×/4×, a turntable, in hand 3P key frames, FP strip,
                       the trail over the hit arc, spectrogram tiles (gear.py's stand-in skin, not .ref)
checks (per weapon, builders run; structural parts mirrored as classpath GameTests for CI):
  model: bounds, faces with real UVs, uniform texel density, no coplanar overlaps, ≤ 400 quads, grip at origin
  texture: palette, contrast, distinct from siblings (hash + silhouette overlap at 16 px and at 10 blocks)
  animation: blade-as-segment vs body penetration, hit frame inside the active window, trail arc covers the server arc
  audio: arms loudness and true peak, impact onset on the contact frame, variant spread, mono, subtitles
  ids: every item, model, lang, sound, clip resolves (extends tools/qa/check_assets.py)
review: one autopilot tour per batch on the Mac (FP and shoulder view, PACK=feel and none) → clip.py contact sheet;
        one listening page per batch (audio_listen.py keep / remake / cut)
```
Items: one registered item per weapon definition (stable ids, JEI, per-id lang and model), generated from
`ArmsData`; instance state in an `awaken:arms` component `{schemaVersion, mastery kills, attunement, temper}`.

### 4.11 Data, state and lifecycle
| Data | Where | Saved? | Version | Notes |
|---|---|---|---|---|
| Archetypes, moves, timing | `data/awaken/arms/archetype/*.json` | data | `schemaVersion` 1 | the one timing table; reloadable; checked by a GameTest |
| Weapon definitions | generated `ArmsData.java` + `data/awaken/arms/weapon/*.json` (stats only) | code + data | 1 | items registered at init (registries freeze) |
| Marks, Arts, Weaves | `data/awaken/arms/{mark,art,weave}/*.json` | data | 1 | |
| Weapon instance | item component `awaken:arms` | with the item | `schemaVersion` | moves with the item: loot moves, never copied |
| Player memory | attachment `awaken:arms_memory` (mastery per archetype, Weaves found, weapons seen) | yes, copyOnDeath | 1 | Codex reads it; migrations by schema |
| Live state | attachment `ArmsMotion` (move, seq, clock, guard raise tick, dodge charges, focus, Resonance) | no | — | reset on login, death, dimension change |
| Enemy posture | runtime attachment on the creature | no | — | reset on reload; decays |
| Engagement | `core.api.Engagement` (server; any hit either way or a told attack at you in the last 100 ticks) | no | — | replaces the four trackers' reads over time |

Idempotency: every swing has a `seq`; the server ignores repeats and stale seqs (older than the running move).
A refusal (`Refuse{seq}`) makes the client fade its clip in 3 ticks, no snap. A result arriving after the player
switched weapons is dropped (seq mismatch). Old saves: vanilla swords need no migration (tags); Hunter blades stay
items with their stats and gain the Longsword's plain moveset, and the Forge offers their Arms successor.

### 4.12 Failure register
| Case | Detection | Containment and user-visible behaviour | Test |
|---|---|---|---|
| 150 ms+ latency | smoothed keep-alive, our own seq clock | deflect grace (≤ 3 ticks); dodge judged on the server's arrival with the Runner's history; cosmetic hit-stop may show on a miss (H2) | `arms_deflect_latency` on the bench (0/150/300 ms) |
| Server refuses a swing | `Refuse{seq}` | clip fades out in 3 ticks; no damage number | `arms_refuse` |
| PAL missing | `ModList` | ArmPose fallback; FP unaffected | `arms_clip_fallback` |
| Hands not built | `core.api.FirstPerson` absent | weapon FP uses CastAnims-style redraw; flagged in the slice's gate | slice gate |
| Other combat mod (Better Combat, Epic Fight) | `ModList`, its item claims (`weapon_attributes`) | Arms steps aside for the items it claims (never both on one swing); toast once | manual |
| Shoulder Surfing decoupled aim | ShoulderView API | strikes use the camera's look like the rope (§17.13) | tour |
| Crowd (≤ 60 combat entities) | arc sweep counts | sweep only on active ticks, an AABB query of reach + 2 | `arms_crowd_budget` |
| Multi-hit and i-frames | per-move hit set; per-hit `setPostAttackInvulnerabilityTicks` | one hit per target per move; no infinite stun-locks: posture can't Break twice within 6 s | `arms_iframes` |
| Deflect spam | raise history | no deflect for 8 ticks after a failed one (block still works) | `arms_deflect_spam` |
| Old Hunter blades | item id | plain moveset; still craftable until the Forge line lands | `arms_legacy_blade` |
| `arms.enabled=false` | config | vanilla melee everywhere; items act as swords | `arms_kill_switch` |
| Ritual and scripted fights | `Ritual`, story clamps | Arms verbs work; clamps keep running after the Arms stage | existing story tests |

Privacy and AI: not applicable (no language model, no network calls, no personal data). Action boundaries: the server
decides every hit and defence; clients only predict cosmetics.

### 4.13 Performance and cost
Server: work only for players mid-move and creatures under a told attack; no per-tick allocation (fixed slots, a
ring buffer for raises); arc sweeps on 2–3 active ticks per move. Client: one SWEEP ribbon per visible wielder
(32 samples × 2 edges, pooled), hit-stop reuses the render-state modifier, clips are data. Budget check: a perf tour
of a full gate fight (8 creatures, 2 players) must hold SPEC's p50 ≥ 110 fps; `/awaken arms bench` prints per-tick
server cost. Cost: builder time only (§6.5).

---------------------------------------------------------------------------------------------------------------------

## 5. Concrete demonstrations (proposed behaviour, nothing measured)

**A · The disappointing case: a diamond sword against the D Gearwright Colossus.**
- *Today:* you walk in, it swings every second with no warning, you trade hits, die in ~6 s at 11% of its health. Brace
  and Foresight help if you have them.
- *Proposed:* its arm draws back for 10 ticks with a rising servo whine and a glint on the fist (LIGHT). You press
  right mouse as the fist falls: a ring, sparks off your sword, both of you freeze for 100 ms, its posture bar on the
  plate jumps. You strike back (Riposte). The Piston Slam lights amber: you deflect it too, or sidestep (sprint +
  left). Overload Arc flashes red: you dodge through the beam's sweep, and the world around the Colossus slows for a
  beat (perfect dodge). Five deflects in, the plate's posture bar fills and flashes: BREAK. The Colossus sags; you hold
  left mouse: Execution. *Target:* ~110 s with a diamond sword at D for a medium player, ~60% health left.
- *Why less work:* you never read stats; every hit it can deal has told you how to answer it.

**B · An ordinary Wilds fight: three skeletons and a zombie, Hunter blade.**
- *Today:* click, wait for the cooldown, click; vanilla sound; numbers float up.
- *Proposed:* tap-tap-tap in rhythm: the three-strike chain, the swish rising with each Cadence step, each hit a short
  freeze and a crack of bone; an arrow is deflected off the blade (projectiles are LIGHT); the zombie lunges and you
  sprint-left, Slipcut. You never press more than three inputs.

**C · The ambiguous case: a crowded gate floor in a pressure wave.**
- *Proposed:* soft focus stays on the creature you struck; the arc hits everything in it (one hit each); two attacks
  land in the same deflect window: both are deflected (the window covers the guard, not one attacker); a PERILOUS
  grab from behind can't be deflected: the red flash and its sound say dodge. Hit-stop is capped at one freeze a 4-tick
  window so a sweep through five bodies doesn't stall.

**D · Missing dependency and failure: a 150 ms server, no PAL, Hands not yet built.**
- *Proposed:* your deflect timed to what you see lands (grace); others see your swings as ArmPose keyframes; your first
  person uses the slice's interim redraw. A swing the server refuses fades in 3 ticks instead of snapping.

**E · Discovery: a Weave you didn't know existed.**
- *Proposed:* you deflect an imp and, panicking, cast Heat: the riposte comes out on fire and rings the ground. "WEAVE ·
  Ember Riposte" types in; the Codex's Weaves page goes from 3 / ?? to 4 / ??. A Wayfarer later hints at "what frost does
  after a perfect step".

---------------------------------------------------------------------------------------------------------------------

## 6. Evaluation and delivery

### 6.1 Baseline and targets
Baseline: §2.2 (numbers from code). New `tools/sim/combat_sim.py` (seeded; boss ability tables generated from
`GateBestiary`): four player profiles (Tank: no defence; Dodger: 70% of told attacks dodged; Parrier: 60% of LIGHT/HEAVY
deflected; Expert: 85% deflected, 90% dodged, Cadence 2 average) against every Lord at its home rank.

| Target (on-level Arms weapon, PROPOSAL; tuned by the sim, then Euan) | Value |
|---|---|
| Tank survives an on-level Lord | ≤ 10% (the race should be lost) |
| Dodger / Parrier wins | ≥ 60% / ≥ 80%, TTK 80–120 s |
| Expert wins | ≥ 98%, TTK ≤ 75 s, ≥ 50% health left |
| Diamond sword, Parrier, at D | wins ≥ 70%, TTK ≤ 140 s |
| Normal mobs | 3–6 hits on level (SPEC §2 holds) |
| Click to first visible motion | same frame |
| Click to light hit (server) | 2–3 ticks |

### 6.2 Tests (CI GameTests; one per mechanism, prefix `arms_`)
`arms_timeline` (move clock, cadence windows, buffer, cancels), `arms_hit_arc` (arc and reach hit exactly the line-up),
`arms_damage_stack` (momentum, VIT, gear, crit, glyph listeners still apply to an Arms hit), `arms_deflect` (window,
classes, posture, riposte), `arms_deflect_latency` (0/150/300 ms through the latency knob), `arms_deflect_spam`,
`arms_block_guard_break`, `arms_dodge` (evade ticks, perfect dodge slows only the attacker), `arms_posture_break`
(Break interrupts a wind-up; no double Break within 6 s), `arms_iframes`, `arms_vanilla_sword` (tags give the verbs),
`arms_kill_switch`, `arms_data` (every archetype, weapon, Mark, Weave parses; timing rows complete), `arms_assets`
(every item resolves a model, icon, lang, sound, clip), `arms_audio` (via `audio_events_resolve`), `mobs_tells`
(every Lord attack has a class and a tell ≥ 8 ticks), `mobs_held_weapon_damage` (settles the SUSPECTED bug).
A filter run while iterating: `tools/gradle.sh 900 runGameTestServer -Ptests=arms_`.

### 6.3 What CI can't prove (Euan, or a tour on his Mac)
Feel (hit weight, sound, camera), clip quality in both views, the no-PAL fallback's look, 3D models in hand,
latency feel on a real server, motion comfort. Each phase ends with: an autopilot tour (FP and shoulder view, run
alone, Gradle wrapped in `slot.sh`); a listening page for its sounds; for the slice and the enemy pass, a feel session
with Euan playing (like `docs/ascent/FEEL-SESSION-1.md`). Ask Euan before any client launch.

### 6.4 Phases and gates
| Phase | Scope | Gate: CI | Gate: Euan / Mac |
|---|---|---|---|
| **S0 Fixes** (in mobs, before tuning) | `mobs_held_weapon_damage`; tells for the basic melee of the slice's Lord | tests green | — |
| **S1 Feel spike** | Arms core: input, timeline, server strike, guard/deflect/block, dodge, Cadence, CombatFeedback, deflect grace; the Longsword with placeholder clips and sounds; the D Gearwright Colossus retold and given posture; a training dummy at the Training Grounds | `arms_timeline`, `arms_hit_arc`, `arms_damage_stack`, `arms_deflect(_latency, _spam)`, `arms_dodge`, `arms_posture_break` | **G1 feel session**: 20 minutes, dummy then Colossus, with a diamond sword and the spike weapon; go/no-go on the verbs and windows |
| **S2 The slice** ("The Blade", end to end) | Hands rig (if not yet built), FP and 3P Longsword clips with the item channel, SWEEP trails, the Longsword sound kit, impact FX, the camera voice, getting-hit feel, the HUD (posture on plates, focus bracket, Resonance arc), Riposte, Slipcut, Execution, the Longsword Art, two traversal strikes (Undercut, Plunge), the 6 forged Longswords as generated 3D models (the pipeline's first archetype) | all slice tests + `arms_assets` | **G2**: tour (FP/shoulder, PACK=feel and none), listening page, feel session 2; Euan's yes unlocks scaling |
| **S3 Enemies speak** | Classes and tells for every Lord's and gate creature's attacks; posture everywhere; the Life hit vector; `combat_sim.py`; item-level damage; rebalance | `mobs_tells`, sim targets in CI (pure Java mirror) | Euan plays three Lords |
| **S4 Pipeline at scale** | arms_catalog/models/icons/moves/sounds/preview, every check, GameTest mirrors, (optional) a Python CI step | asset and data tests | contact sheets reviewed |
| **S5 Archetypes 2–9** | movesets, clips, kits, trails | per archetype `arms_hit_arc` rows | tour per 4 archetypes |
| **S6 Archetypes 10–16** + the other traversal strikes | | | tour |
| **S7 Loot and discovery** | Marks, named weapons, Lord-forged with pity, the reveal (pillar, gate-cleared window, FP hold-up), Armoury and Weaves Codex pages, Forge blueprints and attunement, mastery | `arms_loot`, `arms_memory_codec` | Euan's look at the reveal |
| **S8 Weaves, Arts, Resonance** | the 36 Weaves, Arts per archetype and per Lord | `arms_weave` | feel session 3 |
| **S9 Content batches** | ~290 weapons in batches of ~40 | per batch | contact sheet + listening page per batch |
| **S10 Together and polish** | multiplayer seen, PvP sanity, key-conflict audit, comfort, perf tour | `arms_crowd_budget` | perf tour |

Rollout: everything behind `arms.enabled` (server, default on once S2 passes) and the comfort switches; the Hunter
blades keep working until S7. Rollback triggers: a feel-session no-go (revise windows before more work), a perf tour
under budget, CI regressions in other modules' tests.

### 6.5 Effort (AI pace; all of AWAKEN took ~30 builder hours; Hands is planned at 15–22)
S0 0.5 h · S1 4–6 h · S2 6–9 h · S3 4–6 h · S4 5–7 h · S5 6–9 h · S6 5–8 h · S7 4–6 h · S8 3–5 h · S9 6–10 h · S10 2–4 h.
**Total ≈ 45–70 builder hours**; with two or three builders in parallel after S2 (S3 ‖ S4, then S5 ‖ S6 ‖ S7, and S9's
batches split), about 35–45 hours of wall clock at the middle estimates, plus Euan's sessions. The slice (S0–S2) is
11–16 hours on its own and is the only part committed before his go/no-go. What could change it: Hands not built before S2 (+3 h); the no-PAL
fallback needing more than arm poses; 3D JSON models misbehaving in hand (special renderers instead, +3–5 h).

### 6.6 Risks
Latency fairness (H1, H2) → the bench and the grace; accidental dodges (H3) → the free-run's own conditions and a test;
scope creep in content → batches with a stop after each review; animation cost × 16 archetypes → archetype clips
shared by every weapon of the archetype; mod conflicts → step-aside rules; feel taste → Euan's sessions before scale.

### 6.7 Challenging the plan
- *Real frustration or a metric?* The slice is judged by Euan playing the exact case he described (a diamond sword
  against a Lord), not by the simulator; the simulator only keeps the numbers honest.
- *Less effort, or relocated?* Three inputs and no stamina bar; combos continue by pressing; Weaves and mastery add
  depth to the same presses. The risk is reading load: every class has a sound so it can be read by ear.
- *Could something simpler do most of it?* Yes for survival: S0–S1 (tells, deflect, dodge, hit-stop) are the targeted
  repair, and they ship first. They do not give "a different game", 3D weapons or custom swings, which is why B follows.
- *What would prove the diagnosis wrong?* If Euan's G1 session with tells and deflects still feels unfair, the cause
  is elsewhere (damage tuning, arena size, camera); the session notes decide before any scaling.
- *Uncertain or unavailable?* Latency → deflect grace and the bench; no PAL → ArmPose; no Hands → interim redraw;
  another combat mod → Arms steps aside; `arms.enabled=false` → vanilla melee.
- *Can the player trust and correct it?* The server decides every outcome; the glyphs say what happened (DEFLECT,
  BLOCKED, BREAK); comfort switches per layer; soft focus never takes the camera.
- *Proof without losing what works?* `arms_damage_stack` keeps every listener's effect on an Arms hit; the full
  GameTest suite runs on every push; the Ascent's inputs keep their meaning (H3's test).

---------------------------------------------------------------------------------------------------------------------

## 7. Decisions for Euan (with recommendations)
1. **I-frames on the dodge:** amend SPEC's "no invincibility frames anywhere" for the dodge's 5 evade ticks and the
   perfect dodge. **Recommended: yes**, scoped to the dodge only. Alternative: geometry-only dodge (weaker, less fair).
2. **The dodge input:** **sprint + back/left/right/none on the ground** (recommended: no new key, nothing in vanilla
   or the Runner uses it), or a dedicated key (Mouse 4?), or double-tap a direction.
3. **Guard and deflect on right mouse** with any melee weapon. **Recommended: yes.**
4. **Hands' scope:** extend it to weapon attacks (reverses its "attacking stays vanilla" for Arms weapons only; vanilla
   items keep vanilla's swing in first person unless 5 says otherwise). **Recommended: yes**, and build Hands' P1 rig
   before S2.
5. **Vanilla swords:** the verbs only (recommended), or also our own generated 3D models for vanilla swords and axes.
6. **PAL:** stay optional with the ArmPose fallback (recommended), or require it.
7. **Melee scaling:** weapons carry an item level that scales like powers (recommended), so found weapons keep pace;
   vanilla weapons stay E/D.
8. **Found weapons in a gate:** a Lord-forged weapon goes straight to your hands on the kill (recommended), the rest
   stay Satchel loot.
9. **Order:** S0 and S1 first (recommended), with the feel session as the go/no-go before anything scales.

---------------------------------------------------------------------------------------------------------------------

## 8. Research notes

Each row: claim → source (fetched 2026-10-25 unless noted) → what it means here → what we still validate.

### 8.1 Numbers from games and mods (design references, not code)
| Claim | Source | Use here | Validate |
|---|---|---|---|
| Sekiro's deflect window ≈ 12 frames at 60 fps (200 ms); spamming shrinks it | fextralife Sekiro wiki "Deflection" (community-measured, not datamined) | 4 ticks is the genre's norm; a shrink collapses to 1 tick at 20 TPS, so we use a lockout | `arms_deflect_latency`, G1 |
| Elden Ring buckler parry: 6 active frames at 30 fps (200 ms); roll ≈ 13 i-frames at 30 fps (433 ms) | eldenring.wiki.gg "Parrying"; fextralife "Equip Load" (community data) | 4-tick deflect; 5 evade ticks (250 ms) sits under the "generous" bound of ~9 ticks | G1 |
| Epic Fight: parry 8 ticks from guard, re-armed only after 16; block cost escalates and resets on a hit; dodge fully invulnerable | `Yesssssman/epicfightmod` source, branch 1.21.1 | the lockout rule; "attacking resets" is kept for a later guard-strain option | G1 |
| Shield Expansion: parry 3–6 ticks from the raise, heavier = narrower | `infernalexp/Shield-Expansion` source (Apache-2.0) | archetype-specific windows (Daggers 5, Greatblade 3) | S5 |
| Combat Roll: 8 ticks, 3 blocks, 4 s cooldown, one charge, 0 i-frames by default | `ZsoltMolnarrr/CombatRoll` ServerConfig (GPL-3.0: ideas only) | our dodge is shorter (6 ticks, 2.6 blocks), two charges, evades | G1 |
| Better Combat: per-weapon attack lists (hitbox shape, angle, damage multiplier, windup ≈ 25% of the cooldown); its defaults ignore i-frames and don't check the client's target range | `ZsoltMolnarrr/BetterCombat` `WeaponAttributes.java`, ServerConfig (ARR: ideas only) | data-driven archetypes; hits resolved on the server, never on a client claim | `arms_hit_arc` |
| Players' top complaints about Better Combat and Epic Fight are first-person rendering and camera motion on swings, and guard edge cases | their GitHub issue trackers, sorted by reactions | Hands owns FP; camera beats go through one voice with comfort switches | tours |
| Smash Ultimate hit-stop ≈ damage × 0.65 + 6 frames (60 fps), capped at 30; both fighters freeze; buffer 9–10 frames | ssbwiki "Hitlag" | hit-stop scales with weight on both bodies; a 5-tick input buffer | G1/G2 |
| "Sleep ~20 ms at impact" (Vlambeer, *The Art of Screenshake*) | talk notes, secondary | the floor for the smallest hit | G2 |
| Celeste: coyote time 0.1 s, dash 0.15 s | `NoelFB/Celeste` `Player.cs` (published source; numbers only) | forgiveness constants on the order of 2–3 ticks | G1 |
| Minecraft Combat Tests: buffered attack at 80% charge, whiff costs 4 ticks, shield delay removed, 148° block arc | minecraft.wiki "Java Edition Combat Tests" | buffer, cheap whiffs, a wide frontal guard (we use 120°) | G1 |
| Minecraft Dungeons: roll on a 3 s cooldown, no base i-frames; rarity Common, Rare, Unique; weapons with a built-in ability | minecraft.wiki "Dungeons:Roll", "MCD:Rarity", "MCD:Weapon" | every weapon has its own ability (Marks and Arts) | S7 |
| Monster Hunter: 14 weapon types, per-move motion values | community references (MH wikis) | 16 archetypes with motion values per move | S5 |
| Elden Ring Ashes of War: a swappable skill plus affinity, limited by weapon class | eldenring.wiki.gg "Ashes of War" | Arts and attunement: magic bound to a weapon by class | S7/S8 |

### 8.2 Vanilla and NeoForge 26.3 (read in the jars and sources)
| Claim | Source | Implication |
|---|---|---|
| `blocks_attacks`: block delay, angle-scoped damage reductions, item damage, `bypassed_by`, block/disabled sounds; shield = 0.25 s delay, 90° full block | 26.3 client jar; minecraft.wiki "Data component format/blocks_attacks" | block cosmetics and durability for any item; the deflect window is our own logic (the component has none) |
| `attack_range` (min/max reach, hitbox margin, mob factor), `kinetic_weapon`, `piercing_weapon`, `minimum_attack_charge`, `use_effects`, `damage_type` exist; the spear exists in 7 tiers | jar; minecraft.wiki "Java Edition 1.21.11", "Spear" | reach per archetype without custom code; Arms weapons don't set `minimum_attack_charge` (it suppresses the click hook) |
| 26.3 replaced `swing_animation` with `attack_animation` and `interact_animation` (none/whack/stab + ticks; default whack, 6) | jar; minecraft.wiki "Java Edition 26.3" | the vanilla-fallback swing type for archetypes; a stab for spears and rapiers |
| `InputEvent.InteractionKeyMappingTriggered` can cancel attack and use and choose the arm swing; it doesn't fire while `missTime > 0` | NeoForge 26.3.0.95 sources | the input point; Arms keeps vanilla's miss timer out of its own clock |
| `LivingShieldBlockEvent` fires for any `blocks_attacks` item, not cancellable (`setBlocked`, `setBlockedDamage`, `setShieldDamage`) | NeoForge sources | vanilla shields can deflect too |
| `DamageContainer.setPostAttackInvulnerabilityTicks(int)` (default 20): per-hit i-frames | NeoForge sources | fast combos without clearing i-frames by hand |
| Vanilla i-frames: `damageCooldownTime` 20, effectively 10 ticks immune; attack-cooldown damage `0.2 + 0.8p²`; crits need ≥ 84.8% charge and falling | jar; minecraft.wiki "Damage", "Melee attack" | Arms forces full strength on its own hits and owns its clock |
| Item model definitions: `model`, `select` (incl. display context), `condition`, `range_dispatch`, `special`, `composite`; element rotations accept any angle | jar; minecraft.wiki "Items model definition" | a 2D icon in the GUI, a 3D model in hand; any-angle blades (validate in a client) |
| 20 TPS by default (`/tick rate` can change it); game-time sync every 20 ticks; no per-packet server tick | jar | windows counted in ticks; our own seq clock in payloads |

### 8.3 Licences (AWAKEN is All Rights Reserved: `gradle.properties` `mod_license`; the repo is public)
| Project | Code | Assets | 26.3? | Reuse here |
|---|---|---|---|---|
| Player Animation Library (ZigyTheBird) | MIT | MIT | NeoForge 1.2.8 | yes; already our optional dependency; credit it |
| GeckoLib / AzureLib | MIT | MIT | NeoForge 5.5.7 / 4.1.1 | yes, but not needed (our cuboid pipeline and PAL cover it) |
| ReArm, Fryc's Parry | MIT | MIT (some textures may be vanilla-derived) | ReArm: 26.3 Fabric | code ideas could be copied with the MIT notice; no textures. Not planned: our design differs |
| Shield Expansion | Apache-2.0 | Apache-2.0 (textures possibly vanilla-derived) | 1.21.1 | code only, with licence, NOTICE and change notes; not planned |
| Better Combat | **All Rights Reserved** since 2024-08-25 (older branches GPL-3.0) | ARR | NeoForge 3.2.2 | **no copying**; interop only (step aside for its items) |
| Combat Roll, Spell Engine | GPL-3.0 | GPL-3.0 | yes | **no** (copyleft vs ARR); ideas only |
| Epic Fight | GPL-3.0 with conflicting README terms (1.21.1); ARR on its 26.2 dev branch | ARR | no | **no** |
| Simply Swords, MC Dungeons Weapons | Timefall Development License | same (+ Mojang's Minecraft Dungeons IP) | no | **no** |
| Bosses of Mass Destruction | LGPL-3.0 | LGPL-3.0 | no | **no** (ideas only) |
| Mowzie's Mobs, Wizards, Paladins, Weapon Master, Weapon Leveling | ARR or custom | ARR | some | **no** |
| Minecraft (Mojang) | EULA | EULA | — | never copy assets; data formats only |

**Conclusion:** the plan copies no outside code or art. Everything is written and generated here; outside projects are
design references, cited above. If an MIT or Apache snippet is ever copied, it keeps its header and gets a line in a
new `THIRD_PARTY_NOTICES` file (project, URL, licence, copyright). Not verified: Reddit sentiment (blocked), Hades and
Bayonetta timings, Mordhau's parry window, Monster Hunter and Diablo hit-stop, PAL's own first-person behaviour.
