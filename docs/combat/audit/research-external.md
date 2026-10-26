# AWAKEN combat research: external mods, games, game-feel literature

Researched 2026-10-25 for a combat-system plan for AWAKEN (NeoForge 26.3.0.95, Java 25, public GitHub repo).

**Method.** Licences come from the actual LICENSE files, read from raw.githubusercontent.com or a blobless `git clone` into the scratchpad. Release and version data comes from the Modrinth v2 API. Mechanics come from source code where it was available, then from wiki raw pages (minecraft.wiki `?action=raw`), then from other web pages.

**What I could not use.** reddit.com is blocked for this agent (HTTP 403 from the fetch tool and from search), so player sentiment comes from GitHub issue trackers, mod pages and press only. Fandom wikis returned HTTP 402/403. The GDC Vault was in maintenance.

**Source dates.** "fetched" means the URL was read on 2026-10-25. Other dates are publication or commit dates.

**Licence context for AWAKEN.** `gradle.properties` says `mod_license=All Rights Reserved`, and the repo has no LICENSE file. So:
- GPL or LGPL code cannot be copied in unless the owner relicenses AWAKEN.
- Only permissive licences (MIT, Apache-2.0, CC0, CC-BY) are usable, with attribution.

Each entry below follows the pattern **Claim → Source (date) → Applicability → Limitations → Architectural implication → Validate in-game**. Where a block of facts shares one source and one implication, I group them.

---

## A. Minecraft combat mods and vanilla combat data model

### A1. Better Combat (ZsoltMolnarrr)

**Status and licence**
- **Claim:** Better Combat 3.2.2 supports MC 26.3 on NeoForge. The build is `3.2.2+26.3-neoforge`, published 2026-09-17, with 17.5 M downloads. Modrinth labels it "LicenseRef-All-Rights-Reserved". Required dependencies are `cloth-config` and `player-animation-library`.
  - **Source:** Modrinth API `https://api.modrinth.com/v2/project/better-combat` and `/version` (fetched). The repo's `26.3` branch `gradle.properties` has `minecraft_version = 26.3` and `neoforge_version = 26.3.0.3-beta`.
  - **Applicability:** Players could run it next to AWAKEN.
  - **Limitation:** It is a hard competitor for the same hooks (see conflicts below).
- **Claim (licence history, from the git log of `LICENSE`):**
  - MIT: commit `be0639c`, 2022-05-08.
  - CC-BY-4.0: commit `f55f285`, 2022-06-10.
  - GPL-3.0: commit `372d778`, 2022-09-25.
  - **"All Rights Reserved"**: commit `cbc1459`, 2024-08-25, message "Update license, running as disassembked".
  - The `1.20.1` branch still carries GPL-3.0. The `1.21.1`, `master` and `26.3` branches carry ARR.
  - **Source:** blobless clone of `https://github.com/ZsoltMolnarrr/BetterCombat`, plus `https://raw.githubusercontent.com/ZsoltMolnarrr/BetterCombat/26.3/LICENSE` and `.../1.20.1/LICENSE` (fetched).
  - **Applicability:** Old GPL code can legally be forked under GPL. Rekindled Combat did exactly that (Modrinth `rekindled-combat`, GPL-3.0-only, 1.20.1).
  - **Limitation:** AWAKEN is ARR, so GPL code cannot be pulled in. Current code is ARR.
  - **Implication:** Copy no Better Combat code, animations or sounds. **Interoperate through data files only.** Better Combat reads `data/<ns>/weapon_attributes/<item>.json`, so AWAKEN may ship these files for its own items. Writing a JSON file in a documented format is not copying.
  - **Validate:** With Better Combat installed, AWAKEN weapons use our presets and don't double-animate.

**Weapon attribute format (`weapon_attributes` JSON)**
- **Claim:** The format has these parts.
  - **Top level:** `range_bonus` (the deprecated `attack_range` is absolute), `pose`, `off_hand_pose`, `two_handed`, `category`, `attacks[]`, `trail_appearance`.
  - **Each attack:** `conditions[]`, `hitbox` (`FORWARD_BOX` | `VERTICAL_PLANE` | `HORIZONTAL_PLANE`), `damage_multiplier`, `movement_speed_multiplier`, `range_multiplier`, `angle` (degrees, 0 means no angle check), `upswing` (fraction of weapon cooldown), `animation`, `swing_sound` {`id`, `pitch`, `randomness`}, `impact_sound` (documented as "Not working at the moment"), and `trail_particles`.
  - **Conditions:** include `NOT_DUAL_WIELDING`, `DUAL_WIELDING_ANY`, `DUAL_WIELDING_SAME` and `DUAL_WIELDING_SAME_CATEGORY`.
  - **Inheritance:** `parent` gives inheritance merged parent→child, and the attack list can be shortened or extended.
  - **Upswing timing:** the docs give the example "sword with 1.6 speed (12 ticks cooldown) … upswing 0.6 → 7 ticks". Cooldown formula: `(1/(4-ATTACK_SPEED))*20`.
  - **Source:** `WeaponAttributes.java` on master, `https://github.com/ZsoltMolnarrr/BetterCombat/blob/master/common/src/main/java/net/bettercombat/api/WeaponAttributes.java`, and the README (both fetched).
  - **Applicability:** This is a proven, data-driven shape for "hundreds of weapons": a per-weapon combo list, a hitbox shape per swing, and a windup fraction.
  - **Limitation:** It has no parry, block, stamina or i-frame fields. "Parry (under consideration)" is still in the README's future plans.
  - **Implication:** AWAKEN's own `WeaponProfile` codec can mirror these concepts. Write it from scratch, add a `schemaVersion`, and add fields Better Combat lacks: guard/parry window, poise damage, element and spell hooks, finisher.
  - **Validate:** Author roughly 10 profiles in JSON and hot-reload them via `/reload`.

**Concrete presets**
- `sword.json`: 3 hits.
  - Right horizontal slash, angle 120, upswing 0.5.
  - Left slash, angle 120.
  - Stab, `FORWARD_BOX`, pitch 1.2.
  - All three at ×1.0 damage.
- `claymore.json`: two-handed, `range_bonus` 0.25.
  - Horizontal slash ×0.75, angle 150.
  - Stab ×1.0.
  - Vertical slam ×1.25, angle 150.
  - Upswing 0.5 on each.
- **Source:** `https://raw.githubusercontent.com/ZsoltMolnarrr/BetterCombat/master/common/src/main/resources/data/bettercombat/weapon_attributes/{sword,claymore}.json` (fetched).
- **Implication:** Combo structure in Better Combat is "3 different moves, the finisher is heavier and wider". Minecraft Dungeons does the same (see B5).

**Server defaults (from `ServerConfig.java`)**
- `upswing_multiplier = 0.5`, clamped to 0.2–1. The comment says "upswing typically lasts 25% of the attack cooldown". For a 1.6-speed sword that is about 3 ticks (≈150 ms) of windup.
- `attack_interval_cap = 2` ticks.
- `allow_fast_attacks = true`. This means "Bypass damage receive throttling of LivingEntity from player attacks", i.e. it ignores vanilla i-frames.
- `movement_speed_while_attacking = 0.5`.
- `combo_reset_rate = 3`, so the combo resets after 3× the weapon cooldown idle (sword ≈ 37 ticks).
- `target_search_range_multiplier = 2`.
- `server_target_range_validation = false`. Targets are searched client-side and the server does not validate range by default.
- Dual wielding: ×1.2 attack speed.
- Reworked sweeping: up to 4 extra targets, at most −50% damage.
- Knockback is reduced for weapons faster than 12.5 ticks.
- `allow_attacking_thru_walls = false`.
- **Source:** `https://github.com/ZsoltMolnarrr/BetterCombat/blob/master/common/src/main/java/net/bettercombat/config/ServerConfig.java` (fetched).
- **Applicability:** These are tuned numbers from a 17 M-download mod. Use them as starting values.
- **Limitation:** Client-side target search trusts the client; that is an anti-cheat hole on public servers.
- **Implication:** AWAKEN should resolve hits on the server. Use the client's swing only as a request, then do a server-side oriented-box or sector test with a small tolerance.
- **Validate:** GameTest with a fake player that swings at mobs placed at the edge of the arc.

**Other features (README and CHANGELOG, fetched)**
- Feint: cancel during upswing.
- Hold attack to keep attacking.
- Hitbox calculation by oriented bounding box with separating-axis test (OBB+SAT).
- Two-handed weapons disable the offhand.
- Swing through grass.
- Trail particles (added in 2.3.0).
- Lunge support (3.0.2).
- Adjusted mace and axe windups (3.0.1).
- `CombatFlags` API and `/tag @p add bettercombat_disabled` (3.1.0).
- Changelog: `https://github.com/ZsoltMolnarrr/BetterCombat/blob/master/CHANGELOG.md`.
- **Implication:** If Better Combat is installed, AWAKEN can either disable it per player with the tag (soft interop, no code dependency) or yield to it. Decide this explicitly.

**Semantic conflicts declared by Better Combat**
- Dual wielding, attack range changes, attack timing or cooldown logic, attack/mining key handlers, player model modification.
- **Source:** README "Mod compatibility".
- **Implication:** Any AWAKEN combat layer conflicts with Better Combat by definition. Ship a compatibility mode: when Better Combat is loaded, AWAKEN supplies only `weapon_attributes`, abilities, parry, posture and VFX, and leaves swings to Better Combat.

**What players complain about**
- The issue tracker sorted by reactions is dominated by first-person rendering:
  - #523 "Player Model Visible in First-Person While Using Shaders with Iris and Sodium"
  - #559 "Odd view when swinging weapons"
  - #575 offhand visual bug
  - #9, #13, #15, #18 first-person body parts
- Mod compatibility: #500 (Tetra), #565 (Fresh Animations Player Extension).
- Version requests: #591, #533.
- Mechanics requests: #551 "Parry system from Witcher 3", #70 "Better Shield FP & Jump Attack", #228 "no jumping while attacking".
- Add-on mods exist to fix first-person problems: "First Person Model Compat", "Better Combat Punchy Fix".
- **Source:** `https://github.com/ZsoltMolnarrr/BetterCombat/issues?q=is%3Aissue+sort%3Areactions-%2B1-desc` and web search results for those add-ons (fetched).
- **What players love** (inferred from the README goals and the 17 M downloads, not from forum quotes because Reddit is blocked): the MCD-like combos and multi-hit swings.
- **Implication:** Keep the first-person camera and hand rendering separate from third-person body animation. Never move the camera with the body animation. Test with Iris shaders.

### A2. Player animation libraries

**KosmX playerAnimator**
- **Claim:** MIT (Copyright 2022 KosmX). The last release is `2.0.4+1.21.1`, from 2025-12-28. There is nothing for 26.x.
- **Source:** `https://raw.githubusercontent.com/KosmX/minecraftPlayerAnimator/dev/LICENSE` and the Modrinth API `playeranimator` (fetched).
- **Implication:** Dead end for 26.3.

**PlayerAnimationLibrary (PAL)**
- **Claim:** By ZigyTheBird, MIT (Copyright 2025). Its docs call it the "official successor to PlayerAnimator" and say PlayerAnimator "will no longer receive updates".
  - Version 1.2.8 targets MC 26.3, NeoForge `26.3.0.1-beta`, published 2026-10-06.
  - Maven artifact: `com.zigythebird.playeranim:PlayerAnimationLibNeo:1.2.8+mc.26.3`, repo `https://repo.redlance.org/public`.
  - Features: loads Blockbench and Blender JSON (GeckoLib and Bedrock formats, and PlayerAnimator's format), MoLang, smooth transitions, a priority system to resolve two mods animating the player, custom pivot and bones, effect/particle keyframes.
  - Transitive dependencies: MochaFloats (MIT, per its POM), `org.redlance.common-utils:services` (licence **not verified**), gson, fastutil, joml, netty.
  - The repo has moved to `github.com/PlayerAnimationLibrary/PlayerAnimationLibrary` (MIT).
- **Source:**
  - `https://raw.githubusercontent.com/ZigyTheBird/PlayerAnimationLibrary/main/LICENSE` and `.../gradle.properties` (`minecraft_version = 26.3`)
  - `https://docs.zigythebird.com/pal/intro`
  - `https://repo.redlance.org/public/com/zigythebird/playeranim/PlayerAnimationLibNeo/maven-metadata.xml`
  - `https://repo.redlance.org/public/org/redlance/mochafloats/runtime/6.1.1/runtime-6.1.1.pom`
  - Modrinth `player-animation-library` (all fetched)
- **Applicability:** This is the only maintained, permissively licensed full-body player animation library on 26.3 NeoForge. Better Combat and Combat Roll already depend on it, so it is the de-facto shared layer, and its priorities let AWAKEN coexist with them.
- **Limitations:**
  - It is a third-party runtime dependency, and the project rules say "no mixins unless unavoidable". PAL itself uses mixins internally (not verified file by file). Using it as a library is not our mixin.
  - Its first-person rendering behaviour is **not verified**.
- **Implication:** Two options:
  - (a) Depend on PAL (MIT, attribution in credits) and author our own animations in Blockbench. Never reuse Better Combat or Epic Fight animation files.
  - (b) Write a smaller in-house animator that drives `HumanoidModel` arm and body rotations via render-state hooks. That is a lot of client work.
- **Validate:**
  - The spike compiles against 26.3.0.95.
  - Our animation overrides vanilla swing.
  - With Better Combat installed, the priorities resolve.
  - Dedicated server boots without client classes.

### A3. Combat Roll (dodge)

- **Claim:** Licence and release.
  - GPL-3.0: the `LICENCE` file on branches `master` and `26.3`.
  - Version 3.0.1 on 26.3 NeoForge, 2026-09-17. Depends on PAL and cloth-config.
- **Claim:** Server defaults (from `ServerConfig.java`).
  - `invulnerable_ticks_upon_roll = 0`. **The default roll has no i-frames**; it is pure repositioning.
  - `roll_duration = 8` ticks. During it you cannot attack, use items or jump.
  - `roll_cooldown = 4` s.
  - Default distance 3 blocks.
  - 1 roll charge.
  - `exhaust_on_roll = 0.1`; `food_level_required = 6`.
  - Rolling while airborne and during weapon cooldown are both off by default.
- **Claim:** Attributes, enchantments and controls.
  - Attributes: `combat_roll:distance`, `combat_roll:recharge`, `combat_roll:count`.
  - Enchantments: Longfooted (+1 block per level), Acrobat (+10% recharge per level), Multi-Roll (+1 roll per level).
  - Default key R; a HUD arrow shows the cooldown.
- **Source:**
  - `https://github.com/ZsoltMolnarrr/CombatRoll/blob/master/common/src/main/java/net/combat_roll/config/ServerConfig.java`
  - `README.md`
  - `https://raw.githubusercontent.com/ZsoltMolnarrr/CombatRoll/26.3/LICENCE` (all fetched)
- **Applicability:** These are good baseline numbers for an MC-feel dodge.
- **Limitation:** GPL, so no copying.
- **Implication:** Use a dodge with a charges-plus-recharge model, attribute-driven, with no or very short i-frames. Reserve real i-frames as a reward for a *perfect* dodge (B7).
- **Validate:** 8-tick roll at 3 blocks feels OK at 20 TPS. Stepping off ledges mid-roll is safe.

### A4. Epic Fight (Yesman / Epic Fight Team)

**Licence — the critical part**
- **Claim (1.21.1 branch):**
  - Source code is GPL-3.0 (`LICENSE`).
  - Assets under `src/main/resources/assets` are "**All Rights Reserved**" (`LICENSE-ASSETS`): "No part of these assets may be copied, modified, distributed, or used in any manner without the prior written permission."
  - The README adds conditions for third-party redistribution: replace all assets, use a different name, credit, and **grant the Epic Fight project a licence to your modifications**. It also says "Fork and modify the code as long as modifications are not distributed publicly". That clashes with GPL-3.0 §7/§10, which forbid further restrictions, so the terms are legally ambiguous.
- **Claim (new 26.2 development branch):** the root `LICENSE` is now "Copyright (c) 2026 Antikythera Studios. **All Rights Reserved.** This software, including its source code … No permission is granted … to copy, modify … or create derivative works". Commit `dd05832`, 2026-10-12, "Add one-command publishing and the 26.x version scheme"; `gradle.properties` says `license=All Rights Reserved`, `release_type=alpha`.
- **Claim (release status):** Modrinth labels it GPL-3.0-or-later. The latest public build is `21.17.3.1-mc1.21.1-neoforge`, 2026-05-31. **There is no 26.x release.**
- **Source:**
  - `https://raw.githubusercontent.com/Yesssssman/epicfightmod/1.21.1/LICENSE`, `.../1.21.1/LICENSE-ASSETS`, `.../1.21.1/README.md`
  - `git fetch` of the `26.2` branch from `https://github.com/Yesssssman/epicfightmod` (LICENSE text read)
  - Modrinth `epic-fight` (all fetched)
- **Implication:** Do not copy anything from Epic Fight: no code (GPL and now ARR), no assets (ARR), no animations. Study it only for design.

**Mechanics (read from the 1.21.1 source)**
- **Stamina:**
  - Attribute `epicfight:stamina` defaults to **15**, max 1024.
  - `epicfight:stamina_regen` defaults to 1.0.
  - Other attributes: `impact` (default 0.5), `weight`, `armor_negation`, `max_strikes` (default 1), `stun_armor`, `execution_resistance`.
  - Source: `src/main/java/yesman/epicfight/registry/entries/EpicFightAttributes.java`.
- **Dodge cost:** `roll` costs 4.0 stamina; `step` costs 3.0. Source: `data/epicfight/skill_parameters/roll.json`, `step.json`.
- **Guard:**
  - Blocks only frontal hits (dot product of view and source > 0, i.e. 180°).
  - Stamina cost = (accumulated penalty + `penalizer` 0.6) × impact. Impact is 0.5 for non-Epic-Fight damage sources.
  - The penalty **grows with each consecutive block**.
  - It **resets to 0 when you deal damage**, or 40 ticks after you release guard.
  - If you can't afford the cost, you get a GUARD_BREAK animation and sound.
  - The tag `GUARD_PUNCTURE` bypasses guard.
  - Source: `skill/guard/GuardSkill.java`, `skill_parameters/guard.json` (`"penalizer": 0.6`).
- **Parry ("Parrying" skill):**
  - `parry_window` = **8 ticks (400 ms)**, counted from pressing guard.
  - The window can only be re-armed if ≥ 2× window (16 ticks) has passed since the last activation. This is the anti-spam rule.
  - On success: penalty drops to 0.1, knockback ×0.4, and the "Fix continuous parry" rule resets the timer.
  - Advanced guard can also parry projectiles.
  - Parry motions alternate through a per-weapon list.
  - Source: `skill/guard/ParryingSkill.java`, `skill_parameters/parrying.json` (`penalizer 0.6, parry_window 8`).
- **Impact guard:** `penalizer 0.6`, `superior_penalizer 0.2`, `damage_reducer 0.2`.
- **Dodge invulnerability:**
  - `DodgeAnimation` applies `ATTACK_RESULT = DODGEABLE_SOURCE_VALIDATOR` for the whole animation, so the entire roll or step dodges, except damage tagged `bypasses_invulnerability` or `BYPASS_DODGE`. It also ignores projectiles.
  - Steps lock turning and movement; for 0–0.2 s you cannot combo or use skills.
  - Source: `gameasset/Animations.java` lines ~755–790, `animation/types/DodgeAnimation.java`.
- **Weapon categories:** `AXE, FIST, GREATSWORD, HOE, PICKAXE, SHOVEL, SWORD, UCHIGATANA, SPEAR, TACHI, TRIDENT, LONGSWORD, …`. Source: `world/capabilities/item/CapabilityItem.java`.
- **Controls and armature:**
  - Basic attack, dash attack (attack while sprinting), dodge (Left Alt, needs a dodge skill), special attack, toggle Epic Fight mode (R).
  - Epic Fight replaces the player model with its own armature; the toggle "allows the player to disable the Epic Fight player model, animations, and combat mechanics".
  - Source: README "Controls".
- **Applicability:** These are tuned, shipped values. Stamina 15, roll 4, step 3 means about 3–4 dodges before you're empty.
- **Limitation:** Epic Fight's design is soulslike-heavy and replaces the whole player model, which is the root of its mod conflicts.
- **Implications for AWAKEN:**
  - (1) Copy the *idea* that guard cost escalates and attacking resets it. It rewards aggression the way Sekiro posture does.
  - (2) Copy the anti-spam re-arm rule (window W, lockout 2W) instead of Sekiro's shrinking window, which at 20 TPS would collapse to 0–1 tick.
  - (3) Do **not** replace the player model.
- **Validate:** A parry window of 8 ticks vs 6 vs 4 at simulated 100 ms RTT.

**What players complain about**
- Issue tracker by reactions:
  - #1914, a request for a 1.21.1 version.
  - #2134, a crash with AzureLib.
  - #1823, "Add an Option to Change Keybind for Guard/Parry Skill".
  - Many mod-compat and ticking-entity crashes: #188, #64, #1385.
  - #2370, "Possible enhancements for Animation System … 26.1".
  - Source: `https://github.com/Yesssssman/epicfightmod/issues?q=is%3Aissue+sort%3Areactions-%2B1-desc` (fetched).
- Third-party fixes that exist on CurseForge and Modrinth:
  - "Epic Fight Guard Fix": a shield in offhand with a one-handed weapon "silently breaks" guard/parry.
  - "Valour Guard": greatswords can't parry.
  - "Fix Guard": parry fails under high-frequency attacks.
  - "Epic Fight x Better Lock On": lock-on and camera fixes.
  - "Lock on Prio": several camera-controlling mods jitter.
  - Source: web search results (CurseForge and modpackindex pages, fetched via search).
- **Implication:** Pitfalls to design out:
  - Edge cases between guard and offhand items.
  - Two-handed weapons that can't parry.
  - Multi-hit attacks breaking parry.
  - Camera ownership fights.

### A5. Simply Swords (Sweenus / Timefall)

- **Claim (licence):** "Timefall Development License 1.2", the `LICENSE` on the default branch `Architectury-1.21`.
  - "This software may not be bundled with your code in any way… You may not publish copies… You may not distribute."
  - The CurseForge licence page adds that sound assets and `mob_effect` textures may not be used in other ways.
- **Claim (content):**
  - 14 weapon types that play differently with Better Combat: throwable chakrams, twinblades, greataxes and more.
  - "Unique weapons" found only in chests, which cannot be crafted and have abilities.
  - 200+ weapons via support for other mods.
- **Claim (versions):** Latest 1.70.2 for 1.21.1, 2026-08-27. A 1.21.11 branch is in development. No 26.x.
- **Source:**
  - `https://raw.githubusercontent.com/Sweenus/SimplySwords/Architectury-1.21/LICENSE`
  - `https://legacy.curseforge.com/project/659887/license`
  - Modrinth `simply-swords` (all fetched)
- **Implication:** Not reusable at all. Design lesson: crafted base types plus rare, uncraftable "uniques" with signature abilities. Diablo and MCD do the same.

### A6. Spell Engine / Wizards / Paladins & Priests (Zsolt, "RPG Series")

- **Licences:** Spell Engine is GPL-3.0 (`LICENCE` on master). Wizards and Paladins are "All Rights Reserved" (`LICENSE` on master).
- **Versions:** Modrinth lists 26.3 for all three. Wizards 3.1.4+26.3 (2026-10-04); Paladins 3.1.4.1+26.3 (2026-10-09).
- **Spell Engine features:**
  - Spell types: Active, Passive, Modifier.
  - Cast modes: Instant, Charged, Channeled.
  - Triggers: 14+, including melee hit, arrow, roll and evasion.
  - Targeting: Aim, Beam, Area, Caster.
  - Delivery: Projectile, Meteor, Cloud and others.
  - Costs: exhaust, runes, cooldown, durability.
  - Equipment set bonuses.
  - A Spell Binding Table that binds spells to weapons.
- **Weapon skills by weapon class:**
  - Whirlwind (channeled, double axes)
  - Cleave (instant spin, axes)
  - Ground Slam (charged leap, hammers)
  - Smash (charged, disables shield, maces)
  - Flurry (channeled 2-slash with momentum, claymores)
  - Swift Strikes (instant twin strike, swords)
  - Impale (charged throw, spears)
  - Fan of Knives (cone that bounces off terrain, daggers)
  - Thrust (charged lunge along a path, glaives)
  - Swipe (slide forward, sickles)
- **Source:** `https://github.com/ZsoltMolnarrr/SpellEngine/blob/master/README.md`, `.../LICENCE`, `https://raw.githubusercontent.com/ZsoltMolnarrr/Wizards/master/LICENSE`, Modrinth (all fetched).
- **Applicability:** The closest Minecraft analogue to "weapons merged with magic". One weapon skill per weapon *class*, plus spells bound to weapons, plus trigger-based passives that fire on melee hit or on roll.
- **Limitation:** No code or assets can be reused (GPL and ARR).
- **Implication:** AWAKEN's ability system should be data-driven. The shape is "trigger → targeting → delivery → impact", with abilities slotted into weapons, which is the Ashes of War analogue (B2). That fits the existing AWAKEN `powers`/`signal` modules (cross-module API per SPEC §12).
- **Validate:** One weapon can switch ability at a workbench, and the change persists through relog (codec plus `schemaVersion`).

### A7. Minecraft Dungeons ports

- **MC Dungeons Weapons (Timefall):**
  - Repo `LICENSE` says "Timefall Development License 1.1 … All Rights Reserved … may not be bundled … may not distribute … may not port". Modrinth says 1.2.
  - Fabric/Forge 1.20.1 only.
  - Source: `https://raw.githubusercontent.com/chronosacaria/MCDungeonsWeapons/master/LICENSE`, Modrinth `mcdw` (fetched).
  - **Not reusable.**
- **Dungeons Gear:**
  - CurseForge port of all MCD weapons, armour, artifacts and enchantments, 1.16.5–1.19.2, requires Dungeons Libraries.
  - Source: web search results pointing to `https://www.curseforge.com/minecraft/mc-mods/dungeons-gear`.
  - **Licence not verified.**
- **Dungeons Weaponry:** Modrinth says MIT, Forge up to 1.20.4, **no source link**. Source: Modrinth API `dungeons-weaponry` (fetched).
- **Pitfall for all of them:** these reproduce Minecraft Dungeons item designs and art, which are **Mojang/Microsoft IP**. A permissive mod licence cannot license Mojang's IP. **Never reuse MCD-derived art or names.**

### A8. Bosses of Mass Destruction

- **Claim:** LGPL-3.0 (`LICENSE` on master and 1.21). Fabric only. Last release 1.10.2 for 1.21.1, 2024-10-13. No 26.x.
- **Source:** `https://raw.githubusercontent.com/miyo6032/bosses-of-mass-destruction/master/LICENSE`, Modrinth (fetched).
- **Implication:** Design reference only. Copying LGPL source into an ARR mod would keep those files under LGPL with source and relinking obligations. Avoid.

### A9. Mowzie's Mobs (animation reference)

- **Claim (licence):** Custom licence: "All Rights Reserved unless otherwise explicitly stated".
  - Asset reuse is allowed only if "faithful to the original".
  - Forbids "competing projects that offer Mowzie's Mobs gameplay features".
  - Requires credit and a link.
- **Claim (versions):** NeoForge 1.21.1 build 1.8.2 (2026-03-15) **requires GeckoLib**.
- **Claim (source repo):** the GitHub repo named on Modrinth (`BobMowzie/MowziesMobs`) asked for credentials on anonymous clone on 2026-10-25, so it is private or moved. Not verified further.
- **Source:** `https://legacy.curseforge.com/project/250498/license`, Modrinth API (fetched).
- **Implication:** Study the boss telegraphing and animation only. Don't reuse; its terms specifically forbid competing gameplay.

### A10. GeckoLib and AzureLib (3D animated models)

- **GeckoLib:**
  - MIT (Copyright 2026 GeckoLib), repo `LICENSE` on main.
  - Version **5.5.7 for 26.3 NeoForge**, 2026-09-22; 77 M downloads.
  - GeckoLib 5 documents items, armour, blocks and entities.
  - Source: `https://raw.githubusercontent.com/bernie-g/geckolib/main/LICENSE`, Modrinth `geckolib`, `https://wiki.geckolib.com/docs/geckolib5/quick-reference` (fetched).
- **AzureLib:**
  - MIT (Copyright 2021 GeckoThePecko; a GeckoLib fork).
  - Version **4.1.1 for 26.3 NeoForge**, 2026-10-25.
  - Source: `https://raw.githubusercontent.com/AzureDoom/AzureLib/main/LICENSE`, Modrinth (fetched).
- **Applicability:** Gives "not flat 2D, 3D, animated weapons": bones, keyframes from Blockbench, glowing or spinning parts, transforms.
- **Limitations:**
  - Another runtime dependency.
  - Epic Fight issue #2134 shows a crash between AzureLib and Epic Fight, so library conflicts are real.
- **Implication:**
  - For **static** 3D weapons, vanilla item models are enough (Blockbench JSON elements plus item model definitions; A13).
  - Use GeckoLib only for weapons with moving parts (charging blades, opening tomes).
  - Pick **one** of GeckoLib or AzureLib; GeckoLib is the safer standard.
- **Validate:** A GeckoLib item renders in first person, third person and the GUI on 26.3 with Iris installed.

### A11. Parry, shield, posture and leveling mods

**Shield Expansion (Infernal Studios)**
- **Licence and version:** **Apache-2.0** (repo `LICENSE`; the README makes no separate asset statement). 1.86 M downloads. Latest 1.4.3 for 1.21.1, 2026-09-15.
- **Mechanics:**
  - Each shield has a JSON stats file with `cooldownTicks`, `speedFactor`, `parryDamage` (fraction reflected), `flatDamage`, `parryTicks`, `stamina` and `blastResistance`.
  - The parry window opens **when the shield is raised**. A lenient config doubles it.
  - A parry plays a sound, reflects `amount × parryDamage + flatDamage`, knocks the attacker back 0.55 + 0.5, and costs 1 durability.
  - A plain block costs "stamina" (2 if damage ≤ 5, 3 if > 5). When the shield's stamina is used up, the shield goes on cooldown and drops.
- **Per-shield values:**

  | Shield | parryTicks | parryDamage | flatDamage | stamina | cooldownTicks | speedFactor |
  |---|---|---|---|---|---|---|
  | Wood | 5 | — | — | 2 | 20 | 0.78 |
  | Vanilla | 5 | 0 | 1 | 2 | 30 | 0.60 |
  | Gold | 6 | 0 | — | 2 | 25 | 0.9 |
  | Diamond | 4 | 0.35 | 4 | 5 | 45 | 0.38 |
  | Netherite | 3 | 0.50 | 6 | 7 | 60 | 0.25 |

  Heavier shields have **narrower windows but stronger reflects**.
- **Source:** blobless clone of `https://github.com/infernalexp/Shield-Expansion` (branch 1.21.1): `events/ShieldEvents.java`, `init/ShieldDataLoader.java`, `data/*/shields/*.json`, `LICENSE` (read).
- **Applicability:** The data model maps cleanly onto vanilla `blocks_attacks`, plus an AWAKEN per-item parry profile.
- **Limitation:** Its textures are likely derived from vanilla shield art. **Not verified — avoid its textures.**
- **Implication:** Apache-2.0 code *could* be adapted, but only with the licence text, a NOTICE (none found) and marked changes. Reimplementing is cleaner.

**Fryc's Parry / Sword Parry (Xires87)**
- MIT (`LICENSE` on master). Fabric 1.21.1, 2026-05-11.
- Any tool can block. Using block "right before taking a hit" parries: it negates the damage, and against melee it knocks back and slows the attacker. Explosions can't be parried.
- Block delay is 0 by default. Shield and mace have a 5-tick explosion delay.
- Enchantments: Reflex (−1 block delay or +1 parry tick per level), Counterattack (20–50% damage back), Parry.
- Source: Modrinth `sword-parry`, `https://raw.githubusercontent.com/Xires87/SwordParry/master/LICENSE` (fetched).

**Parry (FoundationGames)**
- MIT, Fabric 1.18. Old-style sword blocking with a damage multiplier.
- Source: Modrinth `parry-foundationgames` (fetched).

**Parry! (MCTeamPotato)**
- LGPL-3.0, Forge 1.19–1.20. "Add a key to parry."
- Source: Modrinth `parry` (fetched).

**Shield Parry**
- MIT, NeoForge 1.21.1, 0.1.0 (2026-06-06), **no source link**.
- Perfect-parry window on raise, true damage reflected, **posture on mobs**, posture break leading to "true stagger", stagger feedback.
- Source: Modrinth `shield_parry` (fetched).
- Closest to Sekiro posture in Minecraft.

**Sekiro – Warden Curse**
- MIT, Forge 1.20.1, no source.
- Source: Modrinth `sekiro` (fetched).

**ReArm: Combat Overhaul (pajic)**
- MIT (Copyright 2026, repo `LICENSE` master). **26.3 Fabric** and 26.2 NeoForge.
- Bow "Perfect Shot": releasing in a short window after full draw gives a bonus crit, and vanilla bow crits are removed.
- Sword "Critical Counter": attacking shortly after a shield block crits, and vanilla sword crits are removed.
- Shield projectile parry on raise sends the projectile back at the shooter.
- Shield Bash enchant: action key, 8 s cooldown.
- Quickstep: dash after an arrow hit (Left Alt).
- Axe Crippling Throw and recall.
- A charge-progress bar on the hotbar.
- Source: Modrinth `rearm` body, `https://raw.githubusercontent.com/pajicadvance/rearm/master/LICENSE` (fetched).
- **Most relevant "vanilla-feel, few keys, timing skill" reference, and MIT.** Its textures (e.g. the netherite bow) may be derived from vanilla. Not verified; avoid.

**Weapon Master (not YDM's)**
- ARR. 1.20.1 Forge/NeoForge.
- Modular weapons with 3.5 billion part combinations, 8 items, level-based unlock of abilities and passives, "all-new hit-detection".
- **Conflicts with Better Combat (mixins) and is "completely incompatible" with Epic Fight.**
- Source: Modrinth `weapon-master` (fetched).

**Lukas' Weapon Leveling**
- ARR. 1.21.1. Gear levels up by fighting; broken gear can be repaired.
- Source: Modrinth `weapon-leveling` (fetched).

**"Improved Combat"**
- The name is ambiguous. Modrinth `improved-combat` is MPL-2.0 and only reworks enchantments; "Combat Improved" (ARR) ports the Combat Test features.
- **Not studied further.**

**Implication across these mods:**
- Shield parry, posture, counter-crit and perfect-release designs are everywhere, so AWAKEN gains no novelty by copying them.
- Differentiate through:
  - (a) any weapon can guard or parry with a per-weapon profile;
  - (b) the parry outcome is weapon-specific (riposte move, element burst);
  - (c) posture on mobs **and** bosses;
  - (d) magic fusion.

### A12. Jeb's Combat Test snapshots (2019–2020, on hiatus)

All items below come from `https://minecraft.wiki/w/Java_Edition_Combat_Tests?action=raw` (fetched). The Combat Test 8c page is dated 2020-08-26 (`https://minecraft.wiki/w/Java_Edition_Combat_Test_8c`).

**Attacking**
- Attacking is impossible until the cooldown reaches 100%.
- **An attack pressed at 80–100% charge is buffered to fire at 100%.**
- Cooldown charges up to 200%. At ≥195% you get **+1 block reach**.
- Sweeps need ≥185% (sword) or ≥190% (axe), and need the Sweeping Edge enchantment.
- Hold-to-attack fires 1 tick slower than optimal.
- **A missed attack has only a 4-tick cooldown** (5 ticks with hold-to-attack).
- Weapons with cooldown < 10 ticks give the target less i-frame time.
- Switching items no longer resets the cooldown.
- The swing animation was changed to "emphasize the new rhythm".

**Reach**
- Swords 3, tridents and hoes 3.5, axes, pickaxes and shovels 2.5, fist 2.5.
- The Creative reach bonus was removed.

**Retuned weapons (damage / speed / reach)**
- Swords: speed 3 (0.33 s), damage 4–7 by tier.
- Axes: speed 2, damage sword+1, reach 2.5, disable shields for **1.6 s (was 5 s)**.
- Trident: 7 damage, speed 2, reach 3.5.

**Shields**
- **Removed the 0.25 s (5-tick) raise delay.**
- Block only up to 5 damage (10 with a banner).
- Block 50% of knockback.
- Arc reduced to 148° (from 180°).
- Crouch-shielding allowed, and it allows attacking while shielding.
- Added a "Shield Indicator" HUD option.

**Other**
- Arrows and snowballs don't trigger i-frames.
- Eating is interrupted by hits.

**Applicability:** This is Mojang's own experiment on rhythm and timing. The 80% buffer, the 4-tick whiff recovery, the ≥195% reach bonus and the narrower shield arc are directly reusable *design ideas*; design ideas are not copyrightable.

**Limitation:** The tests were divisive and never shipped. Not verified here because Reddit is blocked.

**Implication:**
- Use an input buffer on attack (e.g. last 20% of recovery).
- Make whiffs cheap and hits committed.
- Charge-for-reach as an optional skill expression.

### A13. Vanilla combat data components (1.21.5 → 26.3) and NeoForge 26.3 hooks

**`minecraft:blocks_attacks`** (added in 1.21.5, released 2025-03-25)
- `block_delay_seconds` (float, default 0): how long Use must be held before blocks succeed.
- `disable_cooldown_scale` (default 1; 0 means the item can never be disabled): multiplier on the attacker's `weapon.disable_blocking_for_seconds`.
- `damage_reductions[]`, each with:
  - `type`: damage type, list or tag; default all.
  - `base`: flat amount, required.
  - `factor`: fraction, required.
  - `horizontal_blocking_angle`: default 90.
  - The default is `[{base:0,factor:1,horizontal_blocking_angle:90}]`.
- `item_damage`: `threshold` (default 0), `base` (default 0), `factor` (default 1.5).
- `block_sound` and `disabled_sound`: sound events.
- `bypassed_by`: damage type tag. **In 26.1 this also accepts an ID or a list of IDs.**
- **Source:** `https://minecraft.wiki/w/Data_component_format/blocks_attacks?action=raw`, `https://minecraft.wiki/w/Java_Edition_1.21.5` (line ~751), `https://minecraft.wiki/w/Java_Edition_26.1` (fetched).

**`minecraft:weapon`** (1.21.5)
- `item_damage_per_attack` (int, default 1).
- `disable_blocking_for_seconds` (float, default 0).
- **Source:** `https://minecraft.wiki/w/Data_component_format/weapon?action=raw` (fetched).

**Spear-era components** (1.21.11 "Mounts of Mayhem", released 2025-12-09)
- **`minecraft:attack_range`:**
  - `min_reach` (default 0) and `max_reach` (default 3); range 0–64.
  - `min_creative_reach` (default 0) and `max_creative_reach` (default 5).
  - `hitbox_margin` (default 0.3; range 0–1).
  - `mob_factor` (default 1; range 0–2).
  - It overrides the player's interaction range for attacks.
- **`minecraft:piercing_weapon`:**
  - `deals_knockback` (default true), `dismounts` (default false), `sound`, `hit_sound`.
  - Melee hits **multiple entities along a ray** and the item cannot mine.
- **`minecraft:kinetic_weapon`:**
  - `delay_ticks` (default 0).
  - `damage_conditions`, `knockback_conditions`, `dismount_conditions`, each as {`max_duration_ticks`, `min_speed`, `min_relative_speed`} in blocks/s.
  - `forward_movement` (default 0).
  - `damage_multiplier` (default 1). Damage = `floor(relative_speed × damage_multiplier)`.
  - `sound`, `hit_sound`.
  - `contact_cooldown_ticks` (default 10).
- **`minecraft:minimum_attack_charge`:** float 0–1, the attack-indicator charge required before the item can attack.
- **`minecraft:damage_type`:** the damage type the item deals, e.g. `minecraft:spear`.
- **`minecraft:use_effects`:** `can_sprint` (default false), `speed_multiplier` (0–1, default 0.2), `interact_vibrations` (default true).
- **`minecraft:swing_animation`:** `type` = none | whack | stab, `duration` ticks (default 6).
- **Source:** `https://minecraft.wiki/w/Java_Edition_1.21.11?action=raw` (lines ~789–860) and `https://minecraft.wiki/w/Data_component_format/{attack_range,piercing_weapon,kinetic_weapon,kinetic_weapon/conditions,minimum_attack_charge,damage_type,use_effects,swing_animation}?action=raw` (all fetched).

**26.3 change (critical for AWAKEN)**
- **`minecraft:swing_animation` was removed** and replaced by **`minecraft:attack_animation`** and **`minecraft:interact_animation`**.
- Both take `type` = whack | stab | none and `duration` in ticks (default 6).
- Existing items migrate to `whack`.
- `/swing` gained an animation-type argument and a duration argument, and no longer resets the player's attack strength.
- 26.3 also added the entity-data tag **`invulnerable_time`** ("makes a mob temporarily invulnerable for a specific number of ticks").
- Minecraft now uses **SDL3 instead of GLFW** for input. Mouse buttons 8–48 are bindable on Linux and 8–32 on macOS.
- 26.3 was released 2026-09-15.
- **Source:** `https://minecraft.wiki/w/Java_Edition_26.3?action=raw` (lines ~338–431, 471, 2501–2810, 2861; fetched).
- **Implication:** Cheat sheets or older code that mention `swing_animation` are wrong for 26.3. Grep `.ref` for `AttackAnimation` before use.

**Vanilla baselines**
- Shield block delay is 5 ticks (0.25 s); axes and wardens disable shields for 5 s (100 ticks). Source: `https://minecraft.wiki/w/Shield?action=raw`.
- After damage, the target is immune for **10 game ticks**. Higher damage during that window deals only the difference. Default attack speed 4 lets only 2 hits per second connect on one target. Source: `https://minecraft.wiki/w/Damage?action=raw` ("Invulnerability timer") and `https://minecraft.wiki/w/Melee_attack?action=raw`.
- Attack-cooldown damage scaling is `0.2 + 0.8p²`. Crits, sprint-knockback and sweeps need ≥ 84.8% charge. Crits add +50%; spears can't crit. Source: `https://minecraft.wiki/w/Melee_attack?action=raw`.
- **Spears:**
  - Reach 2–4.5.
  - Hitbox margin 0.125.
  - Jab requires full charge.
  - Charge attack has an activation delay of 0.75 s (wood) down to 0.4 s (netherite), and needs ≥ 4.6 b/s relative speed for damage and ≥ 5.1 b/s for knockback.
  - Alternating jab and charge raises DPS.
  - Source: `https://minecraft.wiki/w/Spear?action=raw`.
- **Lunge:**
  - Impulse 0.458 × level blocks/tick.
  - Costs 4 × level exhaustion and +1 durability.
  - Needs ≥ 7 hunger.
  - Source: `https://minecraft.wiki/w/Lunge?action=raw`.

**Item model definitions** (client assets `items/*.json`)
- Boolean `condition` properties include `using_item`, `keybind_down`, `selected`, `carried`, `broken`, `damaged`, `has_component`, `component`, `view_entity`, `extended_view`.
- `select` properties include `display_context`, `main_hand`, `charge_type`, `component`, `custom_model_data`.
- `range_dispatch` properties include `cooldown`, `use_duration`, `use_cycle`, `damage`, `time`.
- **Source:** `https://minecraft.wiki/w/Items_model_definition?action=raw` (fetched).
- **Implication:** Get "3D in hand, 2D icon in the GUI" with `display_context`, and blocking or charging model swaps with `using_item`, `use_duration` and `cooldown`, all **without a library**. Verify the exact names in `.ref/assets`.

**NeoForge 26.3 hooks** (branch `26.3.x`, all files fetched from `https://raw.githubusercontent.com/neoforged/NeoForge/26.3.x/...`)
- **`LivingShieldBlockEvent`:** "fires whether the player is actively using a shield or not". You can set blocked damage and shield durability damage; it is initialised from `BlocksAttacks#damageReductions()`. → **Parry for any item without a mixin.**
- **`DamageContainer.setPostAttackInvulnerabilityTicks(int)`** (field default 20): per-hit i-frame control for fast combos.
- Also present: `LivingIncomingDamageEvent`, `LivingDamageEvent`, `AttackEntityEvent`, `CriticalHitEvent`, `SweepAttackEvent`, `LivingKnockBackEvent`.
- **Client:**
  - `ViewportEvent.ComputeCameraAngles` and `ComputeFov`, for camera shake, roll and FOV punch.
  - `InputEvent.InteractionKeyMappingTriggered` (cancellable), to take over attack and use.
  - `RenderHandEvent`, for the first-person hand.
  - `IClientItemExtensions.applyForgeHandTransform(...)` and `getArmPose(...)`, for custom first-person swing and third-person arm pose per item.
- **Applicability:** Weapon variety and block/parry can mostly be built on vanilla components plus NeoForge events with **no mixins**.
- **Limitations:**
  - Full-body animation (legs, torso twist, rolls) still needs PAL or our own render-state work.
  - `blocks_attacks` is a hold-to-block model with no parry window concept, so the window is AWAKEN logic.
- **Validate:**
  - An item with `blocks_attacks` plus our parry handler gives a parry within N ticks of raising.
  - Holding it longer gives a normal block.
  - Axe-disable still works.
  - GameTest with a zombie attack timed by tick count.

---

## B. Games: design numbers

**Frame conversions used below:** 60 fps = 16.7 ms per frame, 30 fps = 33.3 ms, MC tick = 50 ms.

### B1. Sekiro

**Deflect**
- **Claim:** The deflect window is **12 frames by default (0.2 s before the hit)**.
  - Pressing early gives a normal Guard; pressing late means you get hit.
  - Each recent press shrinks the window, "to as little as 4 frames, even 0 frames if you spam it too fast".
  - The penalty clears after 30 frames, or immediately after a successful deflect.
  - Deflects build enemy Posture "by a significant amount".
  - Perilous attacks are marked with a red kanji.
- **Source:** `https://sekiroshadowsdietwice.wiki.fextralife.com/Deflection` (fetched; community-measured, **not datamined**). A Nexus mod that widens the window edits a SpEffectParam (`https://www.nexusmods.com/sekiro/mods/1374`, via search).
- **Conflict:** the search snippet claimed "30 frames". The fetched page says 12. **I use 12.**

**Posture**
- **Claim:** Enemy posture regenerates slower the lower their Vitality; one guide says slower per 25% missing.
- **Claim on perilous attacks:**
  - A perilous thrust is answered by a Mikiri Counter, which deals more posture damage than a deflect.
  - A perilous sweep is answered by jumping; jumping onto the enemy deals posture damage.
  - Grabs must be dodged.
- **Source:** Fextralife Combat, `https://sekiroshadowsdietwice.wiki.fextralife.com/Combat`, and PlayStation Blog (via search). **Approximate.**

**Applicability:** Three readable attack classes, each mapped to a different input (deflect, jump, dodge). That gives deep play with few keys.

**Limitation:** 12 frames is 200 ms, which is **4 MC ticks**, and Sekiro's spam shrink to 4 frames would be **about 1 tick**, unusable over a network.

**Implication:**
- Use a 4–6-tick base window with the Epic Fight-style lockout instead of shrinking.
- Perilous-style telegraphs (particle plus sound plus colour) for unparryable boss moves.

**Validate:** A boss "perilous" move readable at 20 TPS with ≥ 10 ticks of windup.

### B2. Elden Ring

**Parry frames** (30 fps, startup/active/recovery)
- Buckler Parry 3/6/12.
- Dagger parry 3/5/13.
- Small shield 4/6/11; medium shield 4/6/13.
- Curved sword 3/4/14.
- Golden Parry 4/3/16.
- Storm Wall vs projectiles 0/10/13.
- Being hit during startup or recovery costs stamina like a block.
- A parried enemy is open to a critical (riposte). Some bosses need 2–3 parries, and the count does not decay.
- **Source:** `https://eldenring.wiki.gg/wiki/Parrying` (fetched).
- **Applicability:** An active window of about 6 frames at 30 fps = **200 ms ≈ 4 ticks**, with heavy recovery for a whiff. The risk is in the recovery, not only the window.

**Roll i-frames** (30 fps)
- Light ≤ 29.9% equip load: **13 frames (433 ms)**, recovery 8 (233 ms).
- Medium 30–69.9%: also 13 frames (433 ms), recovery 8.
- Heavy 70–99.9%: 12 frames (400 ms), recovery 16 (533 ms).
- Overloaded: cannot roll.
- **Source:** `https://eldenring.wiki.fextralife.com/Equip+Load` (fetched; community data).
- Quickstep: 15 i-frames, 13 on follow-ups (`https://eldenring.wiki.gg/wiki/Skill:_Quickstep`, via search).
- **Implication:** A dodge with about 8 ticks of i-frames is "souls-generous". A weight or equip-load tie-in is a natural hook.

**Ashes of War** (the "fusion" analogue)
- An Ash replaces a weapon's **Skill** and sets its **Affinity**, which changes scaling and status.
- Compatibility is by weapon type (e.g. Piercing Fang needs a thrusting weapon).
- They drop from scarabs, bosses and merchants.
- Applied at a Site of Grace with a Whetblade. Removing an Ash refunds it, and Ashes can be moved between weapons.
- Somber-upgraded (unique) weapons cannot take them.
- **Source:** `https://eldenring.wiki.gg/wiki/Ashes_of_War` (fetched). The FP cost mechanics are **not on the page**.
- **Implication:** This is the template for "merge weapons with magic, discovered or intentional". Use slotted, removable, discoverable ability cores, constrained by weapon class, with uniques that keep a fixed signature skill.

**Guard counter and poise**
- **Claim:** A guard counter is a heavy attack right after blocking. It does high stance damage: about 18 for fists and daggers, about 39 for great hammers.
- **Source:** community posts and guides only (`https://www.gameskinny.com/tips/elden-ring-guard-counter-explained/`, Steam threads via search). **Not verified against data.**

### B3. Diablo III / IV (impact, loot)

**Death physics (D3)**
- **Claim:** D3 monster deaths are pre-animated but then physics-manipulable: flung by force, torn apart by crits, frozen bodies shatter. This "serves as a visual indicator of how much damage and force a character is able to output".
- **Source:** Diablo Wiki "Physics" (`https://diablowiki.net/Physics`) as quoted in search results. A direct fetch returned 403, so **this is a secondary source.**

**Sound (D3)**
- **Claim:** The D3 sound team built "a metric ton of gushy, gooey, squishy sounds" from food and liquids for impacts.
- The loot-flip whistle is "the same exact sound from Diablo II".
- "High-pitched metallic stuff will just totally break down under compression", so the gold sound was redesigned.
- **Source:** GameBanshee, 2012-07-11, quoting Joseph Lawrence via Kill Screen, `https://www.gamebanshee.com/kuucc` (fetched).

**Loot presentation (D3)**
- **Claim:** A legendary drop announces itself three ways: a loud clang, an orange beam of light (green for set items), and a minimap star.
- When Greater Rift keys used the same beam, it misled players, and Blizzard removed it from keys.
- **Source:** `https://di.diablowiki.net/Leg` and Softpedia (via search). **Secondary.**

**D4 rarity and aspects**
- **Claim:**
  - Tiers: Common, Magic, Rare, Legendary, Unique, plus Mythic Unique.
  - Legendaries are rares with a Legendary Aspect.
  - Aspects are extracted into the Codex of Power, and since Season 4 "Loot Reborn" (2024-05-14) every aspect goes there on salvage.
  - Masterworking raises chosen affixes.
  - Uniques have fixed powers that cannot be extracted.
- **Source:** mp1st, `https://mp1st.com/news/diablo-4-season-4-loot-reborn-out-may-14-brings-item-updates-and-here-are-the-changes`; GameSpot, `https://gamespot.com/articles/diablo-4-unique-sacred-ancestral-items-guide/1100-6514818/`; MeinMMO (via search).
- **Verification:** Official Blizzard patch notes were **not fetched**, and the current-season rules are **not verified**.

**Hit-stop in Diablo:** no source found. **Unverified.**

**Implication:**
- Impact is communicated by **the target's reaction** (knockback, death variant by damage type, gibs or shatter), layered wet and material sounds, and a loot reveal that uses sound, light column and map marker together.
- For AWAKEN, use a rarity ladder plus "aspect-like" ability cores that can be extracted, so uniques stay special.

### B4. Monster Hunter

- **Claim:** 14 weapon types, each its own playstyle: Great Sword, Long Sword, Sword & Shield, Dual Blades, Hammer, Hunting Horn, Lance, Gunlance, Switch Axe, Charge Blade, Insect Glaive, Light Bowgun, Heavy Bowgun, Bow.
  - Source: Gematsu, 2017-07-26, `https://www.gematsu.com/2017/07/monster-hunter-world-14-weapon-types-overview-trailers` (fetched).
- **Claim:** Every attack has a **motion value** (MV), a percentage of the weapon's attack. Slow, long motions get high MV; fast ones get low MV.
  - Great Sword level-3 charge = 100%.
  - Sword & Shield charged slash = 20% + 34%.
  - Source: GamerGuides, MH Generations, `https://www.gamerguides.com/monster-hunter-generations/weapon-breakdowns/weapons/motion-values` (fetched).
- **Hit-stop by weapon:** no source found. **Unverified.**
- **Implication:**
  - Per-move damage multipliers (Better Combat's `damage_multiplier`) are the MV equivalent.
  - "Hundreds of weapons" should be **~10–14 movesets × many item variants**, not hundreds of movesets.

### B5. Minecraft Dungeons

**Roll**
- **Claim:** The roll triggers a **3-second cooldown**. Brief invulnerability while rolling is an *armour property* (Shadow Walker, Mystery Armor, Glow Squid), not a baseline feature.
- Enchantments hang off the roll: Acrobat, Burst Bowstring, Dynamo (next hit stronger), Electrified, Fire Trail, Swiftfooted, Tumblebee, Multi-Roll.
- **Source:** `https://minecraft.wiki/w/Dungeons:Roll?action=raw` (fetched).

**Enchantments**
- Up to 3 enchantment slots, each with up to 3 random choices.
- Points per tier: common 1/2/3 (6 total), powerful 2/3/4 (9 total).
- Points come from levelling and are refunded by salvaging or the Enchantsmith.
- **Gilded** gear has an extra built-in enchant and higher costs.
- **Source:** `https://minecraft.wiki/w/MCD:Enchantment?action=raw` (fetched).

**Weapons and rarity**
- 83 melee weapons.
- Rarity: Common, Rare, Unique. Uniques have a built-in extra property, e.g. "defeated mobs explode".
- Some uniques change the combo itself (Fighter's Bindings, Mechanized Sawblade).
- Combo length differs per weapon, and the final hit triggers Shockwave and Swirling.
- **Source:** `https://minecraft.wiki/w/MCD:Weapon?action=raw`, `https://minecraft.wiki/w/MCD:Rarity?action=raw` (fetched).

**Artifacts**
- 3 artifact slots, each with an independent cooldown.
- Soul-based artifacts have shorter cooldowns.
- **Source:** `https://minecraft.wiki/w/Dungeons:Artifact?action=raw` (fetched).

**Implication:**
- One attack button, plus one roll on a cooldown, plus 3 cooldown abilities.
- Variety comes from the weapon's combo and its built-in unique property.
- Combo finishers are the hook for on-hit effects.
- This is the "intuitive, few keys" baseline the owner refers to.

### B6. Hades, Hyper Light Drifter, Hollow Knight (simple input, deep skill)

**Hades**
- **Claim:** Controls are an attack, a special, a cast, a dash "that provides momentary intangibility", and a summon.
- Boons from gods each offer "a choice of three persistent boosts" per run.
- **Source:** Wikipedia "Hades (video game)" (`https://en.wikipedia.org/w/index.php?title=Hades_(video_game)&action=raw`, fetched).
- **Unverified:** dash i-frame duration, and the rule that attacking mid-dash cancels immunity. The Hades fandom wiki returned 402 and community claims conflict.
- **Implication:** Boons modify the *same* few actions (attack, special, cast, dash). Magic fusion should **modify existing verbs**, not add keybinds.

**Hyper Light Drifter**
- **Claim:** Gun ammo recharges by **hitting things with the sword**.
- A patch added brief invincibility to the dash, which split the community.
- **Source:** Wikipedia "Hyper Light Drifter" (fetched).
- **Implication:** Melee hits feed the magic resource. That is a clean melee-magic loop.

**Hollow Knight**
- **Claim:** Striking enemies fills the Soul Vessel. Soul heals (Focus) or casts spells. Charms modify the nail, e.g. +range or more Soul.
- **Source:** Wikipedia "Hollow Knight" (fetched).
- **Unverified:** nail timings and post-hit i-frames; `https://hollowknight.wiki/w/Nail` did not list them.

**Celeste** (forgiveness constants from the published `Player.cs`)
- `JumpGraceTime = 0.1f` (coyote time).
- `DashTime = .15f`, `DashCooldown = .2f`.
- **Source:** `https://raw.githubusercontent.com/NoelFB/Celeste/master/Source/Player/Player.cs` (fetched).
- **Implication:** Forgiveness windows of 80–100 ms are invisible to players and make inputs feel "fair". In MC that is about 2 ticks.

### B7. Devil May Cry / Bayonetta (style and perfect dodge)

**DMC style rank**
- **Claim:** Ranks go D, C, B, A, S, SS, SSS. Points come from hits and kills, scaled by the attack's difficulty.
- Being hit drops 1–2 tiers.
- Variety is encouraged ("avoid spamming the same attack").
- Mission grade and Red Orbs depend on rank.
- **Source:** Shacknews, 2019-03-07, `https://shacknews.com/article/110322/stylish-points-and-style-ranks-in-devil-may-cry-5` (fetched).
- **Unverified:** the exact repetition penalty.

**Bayonetta Witch Time**
- **Claim:** A dodge at the last moment triggers slow-motion (Witch Time). On the hardest difficulty (Non-Stop ∞ Climax) the dodge trigger is disabled.
- **Source:** Bayonetta wiki via search snippets; a direct fetch returned 402.
- **Unverified:** duration in the main games. A Steam claim says ~2 s if idle. The Smash versions are 180 frames in Smash 4 and 90 frames in Ultimate, but those are not the main games (`https://ssbwiki.com/Witch_Time`).

**Implication:**
- A **perfect-dodge reward** (brief time-dilation on the attacker, or a counter window) turns the dodge from an escape into a skill-ceiling tool.
- A style or "flow" meter rewards variety without adding keys.
- In MC, slow-mo must be *per-entity* (the attacker's AI and animation speed on the server), never a global tick change.

### B8. Smash Bros. (hitlag) and input buffers

**Hitlag (Ultimate)**
- **Claim:** Hitlag in frames at 60 fps = `⌊⌊⌊(d×0.65+6)×h×e×s⌋×p⌋×c⌋`, with d = damage %, capped at 30 frames.
- Both attacker and victim freeze. The victim shakes, which allows smash directional influence (SDI).
- A 15% move gives 15 frames in Ultimate, 10 frames in Brawl and Smash 4, 8 frames in Melee.
- **Source:** `https://www.ssbwiki.com/Hitlag` (fetched).
- **Applicability:** A concrete hit-stop curve: about **100 ms minimum plus about 11 ms per damage point**, capped at 500 ms.

**Sakurai, "Eight Hit Stop Techniques"** (week of 2022-12-05)
1. Shake the hit character more.
2. Don't move the hitbox.
3. Shake horizontally on the ground and vertically in the air.
4. Gradually lessen the shake.
5. Control the amount of hit-stop.
6. Interpolate frames into the damage pose.
7. Keep the attacker moving slightly.
8. Scale the shake by camera distance.
- **Source:** NintendoWire recap, 2022-12-12, `https://nintendowire.com/news/2022/12/12/this-week-in-sakurai-12-5-12-11-fine-tuning-hit-stop-and-cheating-the-system/` (fetched). The video itself was not viewed. **The recap gives no frame counts.**

**Input buffer**
- **Claim:** Brawl has a 10-frame buffer at the end of moves; Ultimate has **9 frames** (≈150 ms). In Ultimate a held input buffers until the window opens.
- **Source:** `https://www.ssbwiki.com/Buffer` (fetched).
- **Implication:** A buffer of about 3 ticks (150 ms) at the end of each swing's recovery lets a held or early click chain the combo. The Combat Test's "80% buffer" is the MC precedent.

### B9. For Honor, Absolver, Chivalry/Mordhau (directional)

**For Honor**
- **Claim:** A three-direction guard dial (top, left, right) that both blocks and aims attacks, with parries, guard breaks, feints and unblockables layered on top.
- Reviewers call the learning curve steep, with "seemingly obtuse controls".
- **Source:** Tampere University PlayLab, `https://www.tuni.fi/playlab/it-takes-patience-to-enjoy-for-honors-brutal-melee`, and Inverse (via search).

**Absolver**
- **Claim:** Moves are cards in a **"combat deck"** built outside combat, across 4 stances with 2 attacks each. Critics said "simple controls quickly became complex".
- **Source:** Wikipedia "Absolver" (fetched).

**Mordhau**
- **Claim:** Parry, riposte (the counter queued after a parry, can't be interrupted), feints (cancel windup, cost stamina) and chambers (mirror the attack angle).
- **Source:** Steam threads and Twinfinite (via search).
- **Unverified:** exact parry window in ms.

**Why too complex for AWAKEN:**
- Directional guard needs constant mouse-direction reading.
- That conflicts with Minecraft's camera-as-aim and with block-mining on LMB.
- It is PvP-mind-game design that doesn't suit PvE.

**What to keep:** Absolver's "author the combo out of combat, execute with one button". That is "automatic combos with depth". Also Mordhau's riposte after a parry.

### B10. God of War (2018)

- **Claim:** GDC 2019 had two talks: "Evolving Combat in 'God of War' for a New Perspective" (Mihir Sheth, programming track) and "Taking an Axe to 'God of War' Gameplay" (Jason McDonald, design track).
- **Source:** GDC Vault, `https://gdcvault.com/play/1026085/Evolving-Combat-in-God-of` and `https://gdcvault.com/play/1026023/Taking-an-Axe-to-God` (vault in maintenance; existence confirmed via search). Also Game Informer, 2018-01-15, `https://www.gameinformer.com/b/features/archive/2018/01/15/how-kratos-axe-changes-god-of-wars-combat.aspx`.
- **Unverified:** talk contents (camera, hit-pause, haptics), axe recall and runic attack specifics.
- **Applicability:** Close over-the-shoulder camera, weapon throw and recall as a signature verb, runic (light/heavy) attacks as slottable magic. These are general knowledge, **not verified here**.
- **MC note:** Java has no native controller haptics; 26.3 moved input to SDL3, but controller support is **not verified**. Feel must come from audio, visuals and camera.

### B11. Game-feel literature

**Steve Swink, *Game Feel* (2008)**
- **Claim:** Defines game feel as "realtime control of virtual objects in a simulated space, with interactions emphasised by polish".
- Six components: input, response, context, polish, metaphor, rules.
- Response is described with an ADSR envelope.
- **Source:** Wikipedia "Game feel" (`https://en.wikipedia.org/w/index.php?title=Game_feel&action=raw`, fetched), citing the book and Swink, "Game Feel: The Secret Ingredient", Game Developer, 2007-11-23.
- **Not verified from the book text:** the ~100 ms "feels instant" and ~240 ms correction-cycle figures. University slides cite them (`https://medienwissenschaft.uni-bayreuth.de/wp-content/uploads/GxD-07-Interfaces.pdf`, via search).

**Jan Willem Nijman (Vlambeer), "The Art of Screenshake"** (INDIGO Classes 2013)
- Video: `https://www.youtube.com/watch?v=AJdEqssNZ-U`; title confirmed via noembed.
- Third-party lecture notes list 31 techniques in order. They include:
  - animation and sound
  - lower enemy HP
  - big and fast bullets
  - impact effects
  - hit animation
  - enemy knockback
  - permanence (bodies, shells, smoke)
  - camera lerp and look-ahead
  - screen shake
  - player knockback
  - **"sleep for 20 ms at impact"**
  - gun kick
  - more bass
  - camera kick
  - random explosions on death
  - slow death animation
- **Source:** `https://raw.githubusercontent.com/mingrui/ShotgunTom/master/README.md` (fetched; **secondary notes, not a transcript**). Another blog recounts a camera kick of 6 px and "+4 screenshake" per Nuclear Throne pistol shot (via search; **unverified**).

**Martin Jonasson & Petri Purho, "Juice it or lose it"** (talk video)
- `https://www.youtube.com/watch?v=Fy0aCDmgnxg`; title confirmed via noembed.

**Squirrel Eiserloh, "Math for Game Programmers: Juicing Your Cameras With Math"** (GDC 2016)
- Trauma-based shake: trauma 0–1 decays over time, shake grows with trauma raised to a power, and offsets come from Perlin noise so motion is smooth.
- **Source:** secondary implementations (Bevy example `https://bevy.org/examples/camera/2d-screen-shake/`, npm `screen-shake`) and Game Developer coverage (via search). **The exact exponent is not verified.**

**David Rosen, "Animation Bootcamp: An Indie Approach to Procedural Animation"** (GDC 2014)
- Overgrowth gets fluid, interactive motion from **very few keyframes** plus procedural interpolation.
- **Source:** Game Developer, `https://gamedeveloper.com/design/video-an-indie-approach-to-procedural-animation` (via search). The claim of "13 keyframes total" comes from a Unity forum and is **unverified**.
- **Implication:** Blocky MC rigs suit few-key poses plus procedural blending: an anticipation pose, a contact pose, a follow-through pose, with easing curves.

**Mark Brown, "Secrets of Game Feel and Juice"** (Game Maker's Toolkit, 2015-02-17)
- Cited by Wikipedia as above.

---

## C. Synthesis

### C1. Design principles most likely to give "satisfying, intuitive, deep, few keys"

**1. Few verbs, many modifiers.**
- Verbs: LMB attack (hold = auto-combo), RMB guard (a tap in the window = parry), one dodge key, and at most one "art" key.
- Everything else is context: sprint+attack = dash attack (Epic Fight), airborne attack = plunge, sneak+attack = heavy or charge, after-parry attack = riposte (Mordhau), after-block attack = counter-crit (ReArm).
- Magic **modifies these verbs**, as Hades boons do, rather than adding keys.
- Sources: A4, A11, B6, B9.

**2. A readable, committed attack structure.**
- Every swing has windup, active and recovery phases, with these rules:
  - Windup can be cancelled (feint; Better Combat).
  - Hits are committed.
  - Whiffs are cheap (Combat Test: 4-tick whiff cooldown).
  - The input buffer covers the last ~3 ticks (Smash 9–10 frames; Combat Test 80%).
- Per-move damage multipliers act as motion values (MH, Better Combat).
- Three-step combos with a heavier, wider finisher that carries on-hit effects (Better Combat presets; the MCD finisher triggers Shockwave).

**3. Defence as offence.**
- A parry window of about 4–8 ticks (Sekiro/Elden Ring ≈ 4 ticks; Shield Expansion 3–6; Epic Fight 8).
- Lockout instead of a shrinking window (Epic Fight's 2× rule).
- Block cost escalates and **resets when you land hits** (Epic Fight). That is posture-like aggression pressure.
- Parry outcomes are rewarding: riposte, posture damage, element burst. Posture on enemies, including bosses (Sekiro; the Shield Parry mod).
- Heavier gear gets narrower windows with bigger rewards (Shield Expansion table).

**4. The dodge is mobility; perfection is the skill ceiling.**
- Baseline: a short reposition with charges and recharge (Combat Roll 8 ticks / 3 blocks / 4 s / 0 i-frames; MCD 3 s cooldown).
- Reward a *perfect* dodge, timed to a telegraphed hit, with brief i-frames plus a counter or time-dilation of the attacker (Bayonetta).
- Elden Ring's 13 i-frames at 30 fps (≈ 9 ticks) is the upper bound for a "generous" setting.

**5. Melee feeds magic.**
- Hits fill a resource that powers spells, heals or arts (Hollow Knight Soul, HLD ammo).
- Abilities are slotted into weapons and constrained by weapon class (Ashes of War, Spell Engine weapon skills).
- "Discovered natively over time" = rare ability cores and uniques with fixed signature powers (Simply Swords uniques, MCD uniques, D4 aspects and uniques).

**6. Variety by moveset × variant.**
- About 10–14 movesets (MH's 14 types; Epic Fight's categories; Simply Swords' 14).
- Hundreds of items vary stats, element, unique property and ability core.
- Rarity ladder: Common, Rare, Unique, plus a mythic tier, with escalating loot reveal (sound + light column + map marker; D3).

**7. Impact is a multi-channel stack, scaled by damage.**
- **Target:** hit-stop on both attacker and target (Smash about 6 + 0.65×damage frames; Nijman's 20 ms sleep as the floor); the target shakes horizontally on the ground and vertically in the air; ease into the hit pose (Sakurai); knockback; a death variant by damage type (D3).
- **Camera:** trauma-based Perlin shake (Eiserloh), small camera kick or FOV punch.
- **Audio:** layered sounds (swing + impact material + bass "thump"); avoid high-pitched metallic sounds that break under compression (D3).
- **World:** permanence (Nijman): decals, particles that linger.
- Scale everything by damage, so light hits are crisp and heavy hits are huge.

**8. Variety pressure without a menu.**
- An optional style or flow meter rewards varied moves and perfect actions (DMC).
- Being hit drops it.

### C2. Minecraft-specific pitfalls

**1. 20 Hz server ticks (50 ms granularity).**
- Sekiro's 200 ms deflect = 4 ticks, and its spam shrink to 4 frames ≈ 1 tick; Elden Ring's 6 active frames at 30 fps = 4 ticks.
- So: use whole-tick windows (≥ 4), lockouts not shrinkage, and give boss windups ≥ 10 ticks.
- Keep visuals at render rate: client-side hit-stop, shake and pose interpolation using partial ticks. **Never freeze or slow the server tick for hit-stop.**

**2. Server authority vs latency for parry.**
- The server sees the guard press about one-way latency late. At 100 ms RTT a 4-tick window effectively loses about 1 tick.
- Options:
  - (a) resolve parry against the server tick at which the *client* pressed, with bounded backdating of ≤ 2–3 ticks based on measured ping;
  - (b) widen windows slightly in multiplayer;
  - (c) resolve mob hits at a server-side "impact tick" after a telegraphed windup, so players react to the windup, not the impact.
- Show the parry flash client-side immediately, but confirm damage server-side.
- Better Combat's default of client-side targeting without server range validation is the anti-pattern (A1).

**3. Attack cooldown.**
- Vanilla scales damage by `0.2 + 0.8p²` and needs ≥ 84.8% for crits and sweeps.
- A combo system needs its own timing model:
  - either tie each step's windup to the weapon cooldown (Better Combat's upswing fraction);
  - or use `minecraft:minimum_attack_charge` per weapon.
- The 26.3 `/swing` no longer resets attack strength.

**4. 10-tick i-frames.**
- Vanilla blocks most repeat hits for 10 ticks, and higher damage only adds the difference.
- Fast combos need per-hit control: NeoForge `DamageContainer.setPostAttackInvulnerabilityTicks`, as Better Combat does with `allow_fast_attacks`, and as the Combat Test did for weapons faster than 10 ticks.
- Mob attacks against the player keep vanilla i-frames, otherwise multi-hit bosses delete players.

**5. 26.3 renames.**
- `swing_animation` is gone and replaced by `attack_animation` and `interact_animation`.
- `blocks_attacks.bypassed_by` accepts IDs (26.1).
- Input is SDL3.
- Verify every API in `.ref` before use; the docs/api cheat sheets may predate 26.3.

**6. Mod conflicts.**
- Player-model and animation mods fight: Better Combat, Epic Fight, Fresh Animations/EMF, First Person Model, Weapon Master.
- Weapon Master breaks with Better Combat via mixins; Epic Fight crashed with AzureLib.
- Mitigation:
  - No player-model replacement.
  - Use PAL priorities if PAL is used.
  - Detect Better Combat and Epic Fight and fall back to "data-only" interop.
  - No mixins of our own on attack or render paths; NeoForge events cover hit, block, crit, sweep, knockback, camera and hand.
- Camera ownership: only one system should rotate the camera ("Lock on Prio" jitter).

**7. First-person rendering.**
- Most Better Combat bugs are first-person (shaders, body parts, odd view on swing).
- Animate the first-person hand separately (`RenderHandEvent` / `applyForgeHandTransform`) from the third-person body.
- Never move the camera with body animations.

**8. Motion sickness and accessibility.**
- Respect vanilla Damage Tilt, FOV Effects, Distortion Effects and View Bobbing (`https://minecraft.wiki/w/Options?action=raw`).
- Add an AWAKEN "screen shake" slider defaulting to moderate.
- Clamp camera roll and FOV punch.
- Avoid global slow-motion.

**9. Keybind budget.**
- Vanilla LMB is also mining. Weapons that disable mining (spear-like `piercing_weapon`, Better Combat's option) avoid accidental block breaking.
- Use the vanilla attack and use keys plus one new key for dodge.
- Don't require extra mouse buttons, even though 26.3 can now bind them.

**10. IP and assets.**
- No Mojang textures or sounds, and nothing derived from them; some shield and netherite textures in mods may be vanilla-derived.
- No Minecraft Dungeons designs or names, even from MIT mods.
- No Epic Fight, Better Combat, Simply Swords or Mowzie's animations, sounds or models.
- Author our own in Blockbench and with `tools/art`.

---

## Licence table

| Mod / library | Code licence | Asset licence | Evidence (fetched 2026-10-25) | Latest MC / loader | Reusable in AWAKEN's public ARR repo? |
|---|---|---|---|---|---|
| Better Combat | **All Rights Reserved** since 2024-08-25. Earlier: MIT, then CC-BY-4.0, then GPL-3.0; the `1.20.1` branch is GPL-3.0 | ARR (same repo file) | `raw.githubusercontent.com/ZsoltMolnarrr/BetterCombat/26.3/LICENSE`; git log of LICENSE | 26.3 NeoForge (3.2.2, 2026-09-17) | **No** copying. Data-file interop (`weapon_attributes`) and the `bettercombat_disabled` tag only. The old GPL code would need AWAKEN to become GPL-3.0. |
| PlayerAnimationLibrary (Zigy) | **MIT** (2025 ZigyTheBird) | MIT (repo-wide; no separate statement) | `.../ZigyTheBird/PlayerAnimationLibrary/main/LICENSE` | **26.3 NeoForge** (1.2.8, 2026-10-06) | **Yes.** Depend on it, or copy with the MIT notice. Credit "PlayerAnimationLibrary by ZigyTheBird, MIT". Transitive `common-utils` licence unverified. |
| MochaFloats (PAL dependency) | MIT (POM) | n/a | `repo.redlance.org/.../runtime-6.1.1.pom` | library | Yes, with MIT notice. |
| KosmX playerAnimator | MIT (2022 KosmX) | MIT | `.../KosmX/minecraftPlayerAnimator/dev/LICENSE` | 1.21.1 (2.0.4); deprecated | Yes (MIT notice), but obsolete. |
| Combat Roll | **GPL-3.0** | GPL-3.0 (repo-wide) | `.../ZsoltMolnarrr/CombatRoll/26.3/LICENCE` | 26.3 NeoForge (3.0.1) | **No** (copyleft vs ARR). Ideas only. |
| Epic Fight 1.21.1 | **GPL-3.0**, plus README terms that conflict with it | **All Rights Reserved** (`LICENSE-ASSETS`) | `.../Yesssssman/epicfightmod/1.21.1/LICENSE`, `/LICENSE-ASSETS`, `/README.md` | 1.21.1 NeoForge only (2026-05-31) | **No.** |
| Epic Fight 26.2 (dev) | **All Rights Reserved** (Antikythera Studios, 2026), source included | ARR | `git fetch` of branch `26.2`, commit dd05832 (2026-10-12) | unreleased alpha | **No.** |
| Simply Swords | Timefall Development License 1.2 (no bundling or distribution) | Same; sounds and `mob_effect` textures explicitly restricted | `.../Sweenus/SimplySwords/Architectury-1.21/LICENSE`; CurseForge licence page | 1.21.1 (1.70.2) | **No.** |
| Spell Engine | **GPL-3.0** | GPL-3.0 (repo-wide) | `.../ZsoltMolnarrr/SpellEngine/master/LICENCE` | 26.3 listed | **No.** |
| Wizards / Paladins & Priests | **All Rights Reserved** | ARR | `.../ZsoltMolnarrr/Wizards/master/LICENSE`, `.../Paladins/master/LICENSE` | 26.3 | **No.** |
| MC Dungeons Weapons | Timefall Dev. License 1.1 (repo; Modrinth says 1.2) | Same, plus Mojang MCD IP | `.../chronosacaria/MCDungeonsWeapons/master/LICENSE` | 1.20.1 | **No.** |
| Dungeons Gear | **Not verified** | Mojang MCD IP | CurseForge page (via search) | ≤1.19.2 | **No** (IP). |
| Dungeons Weaponry | MIT (Modrinth label; no source) | Mojang MCD IP | Modrinth API | ≤1.20.4 Forge | **No** (IP, no source to audit). |
| Bosses of Mass Destruction | **LGPL-3.0** | LGPL-3.0 (repo-wide) | `.../miyo6032/bosses-of-mass-destruction/master/LICENSE` | 1.21.1 Fabric | **No** copying into ARR code (LGPL obligations); ideas only. |
| Mowzie's Mobs | Custom: "ARR unless otherwise stated", no competing projects, attribution | Same | `legacy.curseforge.com/project/250498/license` | 1.21.1 NeoForge (needs GeckoLib) | **No.** |
| GeckoLib | **MIT** (2026 GeckoLib) | MIT | `.../bernie-g/geckolib/main/LICENSE` | **26.3 NeoForge** (5.5.7) | **Yes** (dependency). Credit "GeckoLib, MIT". |
| AzureLib | **MIT** (2021 GeckoThePecko) | MIT | `.../AzureDoom/AzureLib/main/LICENSE` | **26.3 NeoForge** (4.1.1) | **Yes** (pick one of GeckoLib or AzureLib). |
| Shield Expansion | **Apache-2.0** | Apache-2.0 (repo-wide; textures possibly vanilla-derived, unverified) | `infernalexp/Shield-Expansion` LICENSE (clone) | 1.21.1 | **Code: yes** with the Apache licence, NOTICE and change markings. **Textures: avoid.** |
| ReArm | **MIT** (2026 pajic) | MIT (some textures possibly vanilla-derived, unverified) | `.../pajicadvance/rearm/master/LICENSE` | 26.3 Fabric, 26.2 NeoForge | **Code: yes** with MIT notice. Textures: avoid unless original. |
| Fryc's Parry (Sword Parry) | **MIT** (2022 Xires87) | MIT | `.../Xires87/SwordParry/master/LICENSE` | 1.21.1 Fabric | Yes (MIT notice). |
| Parry (FoundationGames) | MIT (Modrinth) | MIT | Modrinth API | 1.18 Fabric | Yes in principle (MIT); old. |
| Parry! (MCTeamPotato) | LGPL-3.0 (Modrinth) | — | Modrinth API | 1.20.1 Forge | No (LGPL). |
| Shield Parry | MIT (Modrinth; **no source**) | — | Modrinth API | 1.21.1 NeoForge | Ideas only (no source to audit). |
| Sekiro – Warden Curse | MIT (Modrinth; **no source**) | — | Modrinth API | 1.20.1 Forge | Ideas only. |
| Weapon Master | ARR | ARR | Modrinth body | 1.20.1 | No. |
| Lukas' Weapon Leveling | ARR | ARR | Modrinth API | 1.21.1 | No. |
| Rekindled Combat | GPL-3.0 (fork of GPL-era Better Combat) | GPL-3.0 | Modrinth API | 1.20.1 | No (GPL). |
| Celeste `Player.cs` | Published for reference; **licence not checked** | n/a | `NoelFB/Celeste` | n/a | Numbers cited only; no copying. |
| Vanilla Minecraft data and assets | Mojang EULA | Mojang | — | — | **Never copy assets.** Using data component *formats* is fine. |

**Attribution format if MIT or Apache code is ever copied.** Keep the original licence header in the copied files and add an entry to a `THIRD_PARTY_NOTICES` file listing the project, URL, licence and copyright line. Apache-2.0 also requires keeping any NOTICE (none found for Shield Expansion) and stating the changes. Depending on a library, rather than copying it, needs only credits.

---

## Could not verify (explicitly)

- Reddit sentiment for any mod (domain blocked for this agent).
- The exact deflect frames in Sekiro, which are community-measured (12 frames per Fextralife; a search snippet said 30).
- Elden Ring roll i-frames and guard-counter stance numbers, which are community data.
- Hades dash i-frame duration.
- Bayonetta Witch Time duration and trigger window in the main games.
- Mordhau parry window.
- Hollow Knight nail timings.
- Monster Hunter hit-stop per weapon.
- Diablo hit-stop.
- God of War talk contents and haptics.
- The current Diablo IV season's rarity rules.
- The Vlambeer talk, verified only through secondary lecture notes.
- The exact trauma exponent in Eiserloh's talk.
- Rosen's keyframe count.
- PAL's first-person behaviour.
- PAL's internal mixin use.
- The licence of `org.redlance.common-utils`.
- Licences of mods without source (Dungeons Gear, Dungeons Weaponry, Shield Parry, Sekiro – Warden Curse).
- Whether the Shield Expansion and ReArm textures derive from vanilla.
- Mowzie's Mobs source (the repo was inaccessible).
- Every 26.3 API name above was read from NeoForge `26.3.x` sources on GitHub. They still need a grep in `/Volumes/MEDIA/Awaken/.ref` (not reachable from this environment) before coding.
