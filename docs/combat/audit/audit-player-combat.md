# AWAKEN: player-side combat audit (for the weapons and combat overhaul plan)

Repo `/home/user/awaken`, branch `claude/kind-darwin-18uqmv` @ `d12ea34` (same tree as `origin/build/awaken-v1`).
Read-only audit, 2026-10-25. Scope: the **player's** side of the fight loop (damage in and out, feel, HUD, input,
networking, multiplayer, performance). Enemies, bosses and loot are another agent's. So is the art and animation
pipeline, except where a combat animation system has to share hooks with it.

**Method and caveat.** I read every listener and helper named below. The decompiled sources (`/Volumes/MEDIA/Awaken/.ref`)
are **not on this machine** (Linux cloud box; no Gradle cache either), so I could not read vanilla or NeoForge code. Where
this report states vanilla behaviour, it comes from `docs/api/*.md` (cheat sheets that say they were verified against
`.ref`) or is marked **SUSPECTED**. Everything marked VERIFIED is AWAKEN code I read, cited `file:line`. All paths are
relative to `src/main/java/dev/awaken/` unless they start with `docs/`, `tools/` or `src/`.

---

## 0. Headline findings

1. **Melee is vanilla `Player.attack` with multiplicative listeners layered on top.** AWAKEN has no melee code of its
   own: no swing system, no combos, no reach or sweep changes, no custom melee damage types. Every source modifies
   `LivingIncomingDamageEvent`, `CriticalHitEvent` or attributes.
2. **No defensive mechanic exists for plain melee.** There is no parry, block, guard, posture, poise, stagger meter or
   dodge on the player's side. The defensive kit is all powers or gear procs: Brace (−60% for 3 s), Foresight (cancels
   one hit and blinks you aside), Veil Flicker (a chance to cancel a hit), Bulwark, Barrier and Nanite Skin. Vanilla
   shields still work, but nothing in AWAKEN touches them.
3. **The SPEC forbids i-frames, and Dash is the designated dodge.** SPEC §13 (Awakened Body): "No invincibility frames
   anywhere". `docs/proposals/awakened-body.md:37`: "Dash stays the dodge". The pack plan excludes Better Combat and
   Combat Roll (`docs/plan-pack.html:425`). Any dodge with i-frames, or any parry window, means amending the SPEC.
4. **Players with the Tether tier cannot cast Dash from its key.** Once a player holds Tether, Left Alt fires the rope
   and the movement slot's power steps aside (`powers/client/ClientCasting.java:51`, `body/client/BodyClient.java:86-88`,
   SPEC §17.1). Dash is also forward-along-look only (`powers/catalog/VelocityPowers.java:96-104`), not a directional dodge.
5. **Hit-stop exists only for powers.** It freezes the struck entity's render state for 20-80 ms, on the attacker's
   client only, and adds a camera shake (`powers/client/CombatFeel.java:45-76`, `ClientPowers.java:149-155`). Plain
   melee gets no hit-stop, no camera kick and no FOV beat. The only melee feel is the momentum hit's impact sound and
   dust, and the crit glyph's chime.
6. **No single class owns the camera in combat.** At least four AWAKEN listeners add to `ComputeCameraAngles`
   independently: CombatFeel, FxScreen, FeelDirector and BodyCamera, plus Camera Overhaul from the pack. They follow
   three different comfort switches. FeelDirector is the "one voice" for the Ascent only.
7. **Getting hit is vanilla only.** The hurt sound, red tint and damage tilt are vanilla's (Lens re-applies the tilt).
   There is no hurt vignette, no low-health feedback, no directional damage indicator and no block or parry sound
   design. Damage numbers are never shown for damage taken (SPEC §21.1).
8. **Inputs are crowded.** AWAKEN binds 15 keys. Sprint, sneak, jump, A/D and Left Alt all carry movement meanings.
   The rule "No new keys" (SPEC §16.3, §17.1) governs movement. Right-click (use) with a blade or an empty hand is not
   consumed by AWAKEN. That is the cleanest free input for block or parry. Mouse 4/5 are free from AWAKEN's side.
9. **The networking template for a predicted dodge or parry is the body's Start/End/Refuse/Seen pattern.** The Runner's
   12-tick position history adds lag compensation (`body/RunnerServer.java:74-80`). There is no shared clock and there
   are no timestamps: the server validates state at the moment the packet arrives.
10. **The Hands first-person plan (pack/fp-anims, plan only, no code) explicitly leaves attacking vanilla.** A combat
    animation system must either live inside Hands (its single HIGHEST `RenderHandEvent` owner, its FP clip format, its
    shared 3P/FP timing table, `core.api.FirstPerson`) or be planned jointly with it.

---

## 1. The damage pipeline, end to end

### 1.1 One player melee swing, in order (VERIFIED for AWAKEN steps; vanilla order from `docs/api/entities.md:338`)

```
client LMB ─ InputEvent.InteractionKeyMappingTriggered (isAttack)
             └ BodyClient:55-57 → ClientMoves.onAttackKey (225-228): ends a tactical sprint, never cancels the swing
             └ OpeningCamera.interact (opening/client/OpeningCamera.java:241-245): cancels the swing only during the opening's shots
server Player.attack(target)                                   [vanilla, SUSPECTED detail]
  ├ AttackEntityEvent
  │   ├ VeilEvents.onAttack (veil/VeilEvents.java:204-206)  cancels when the player is a spirit (registered before body)
  │   └ BodyServer.onAttack (body/BodyServer.java:364-372) ends the tactical sprint, remembers attacker, target and tick
  ├ attack strength (vanilla cooldown), attribute damage = base 1 + item + STR% + Cornered% + Measure% + temper
  ├ CriticalHitEvent
  │   ├ NORMAL ProgressionModule.onCrit (progression/ProgressionModule.java:343-352)
  │   ├ LOW    CraftEvents.onCrit (living/craft/CraftEvents.java:90-94)
  │   └ LOWEST Hits.onCrit (living/combat/Hits.java:30-32) records the crit for the damage glyph
  ├ (sweep: vanilla, no AWAKEN listener)
  └ target.hurtServer(level, playerAttack(p), amount)
      isInvulnerableTo → dead check → fire resistance
      → LivingIncomingDamageEvent  (table 1.2)       ← every AWAKEN multiplier lives here
      → vanilla blocking (shield) → i-frame check (damageCooldownTime > 10; only the excess over lastHurt)
      → actuallyHurt → armour/enchantments → LivingDamageEvent.Pre (clamps 1.3) → health
      → markHurt → dealDefaultKnockback → LivingKnockBackEvent (BodyServer.onKnockBack) → hurt/death sound
      → LivingDamageEvent.Post (table 1.3): HitFx glyph packet, Force reactions, combat music
```
AWAKEN mobs (`mobs/entity/AwakenMob.java:490-522`) modify the amount **inside their own `hurtServer` override, before
`super`**, so mob-side guard and haunt factors apply before every listener above.

### 1.2 `LivingIncomingDamageEvent` listeners, in firing order

NeoForge order: priority HIGH → NORMAL → LOW, then registration order inside a priority, which follows module init order
in `Awaken.java:34-58`. Listeners added with `addListener(...)` do not receive cancelled events by default.

| # | Prio | Listener (registration) | What it does (exact numbers) |
|---|---|---|---|
| 1 | HIGH | `BodyServer.onIncomingDamage` (`body/BodyModule.java:34`; `body/BodyServer.java:374-392`) | **Momentum hit.** Same attacker, target and tick as the AttackEntityEvent, `playerAttack` with the direct entity the attacker, attack strength ≥ 0.9: ×(1+m), m = clamp((v−0.2)/0.25, 0, 1)·0.3, where v is horizontal speed (`body/BodyRules.java:99-103,133-135`). Needs sprinting or sliding. Plays `body.impact` (vol 0.5+1.5m, pitch 1.1−m) and sends `body_seen` SEEN_MOMENTUM. Arms an extra knockback of m/0.3·0.6 for the hit's own `LivingKnockBackEvent` (`BodyServer.java:401-406`). |
| 2 | N | `ProgressionModule.onIncomingDamage` (`progression/ProgressionModule.java:82, 320-341`) | **Victim player:** VIT resistance = min(0.35, (VIT−5)·0.005), plus the explosion perk on explosions; amount ×max(0.2, 1−resist); counts DAMAGE_TAKEN. **Attacker player:** perk DAMAGE_ABOVE_HALF_HP ×(1+bonus) above 50% HP. |
| 3 | N | `WorldLatents.onDamage` (`progression/WorldLatents.java:50,136-146`) | Flag only (creeper at full HP). |
| 4 | N | `ForceInteractions.onIncoming` (`powers/PowerEvents.java:45`; `powers/ForceInteractions.java:111-119`) | A Charge hit on your own Echo is cancelled and overcharges it instead. |
| 5 | N | `Modifiers.onIncomingDamage` (`gates/run/RunEvents.java:29`; `gates/run/Modifiers.java:120-155`) | In a gate: GLASS ×2 both ways; counts run damage and noise; Twists: the Double Gate law **cancels** the hit, with SHIELD_BLOCK sound and ENCHANTED_HIT particles (`gates/run/Twists.java:476-490`). |
| 6 | N | `DescentModule.onIncomingDamage` (`descent/DescentModule.java:78,304-314`) | Cancels fall damage in the arrival grace; noise +8 hurt, +16 hitting. |
| 7 | N | `DreamEvents.onIncomingDamage` (`lucidium/dream/DreamEvents.java:61,145-156`) | A mentor's blows leave you at 3 HP or more. |
| 8 | N | `VeilEvents.onBodyIncoming` (`veil/VeilEvents.java:43,134-140`) | A projected Body takes no hits from its owner or the party; from others only with PvP on. |
| 9 | N | `Echoes.onIncomingDamage` (`hearth/echo/Echoes.java:92,503-515`) | Friendly fire between Echoes and their allies is cancelled. |
| 10 | N | `Prologue.onIncomingDamage` (`story/StoryModule.java:76`; `story/Prologue.java:183-200`) | An unawakened player can't die in a gate (cancel, set HP to 1). |
| 11 | N | `Ascension.onIncomingDamage` (`story/StoryModule.java:77`; `story/Ascension.java:25-39`) | Ascension tiers even out melee both ways: a creature's melee on the player ×(1+0.15·mine)/(1+0.15·its); the player's hits on it ×(1+0.3·its)/(1+0.3·mine) (`Ascension.java:42-49`). |
| 12 | N | `Sovereign.onDamage` (`body/ascent/AscentModule.java:37`; `body/ascent/Sovereign.java:135-137`) | In the Sovereign form every hit you land is ×1.5 (`AMPLIFY`, line 36). |
| 13 | N | `Creatures.onIncomingDamage` (`living/LivingModule.java:84`; `living/combat/Creatures.java:66-72`) | Projectiles and blasts from levelled vanilla monsters ×`Curve.mobDamage(lv)`. |
| 14 | N | `CraftEvents.onIncoming` (`living/LivingModule.java:100`; `living/craft/CraftEvents.java:97-108`) | Stalker ×1.5 (sneaking, the mob not targeting you), Apex ×1.1 (elites, beasts); Heart of Stone ×0.85 taken below y 0. |
| 15 | LOW | `PowerEvents.onIncomingDamage` (`powers/PowerEvents.java:44, 91-171`) | Cancels hits on your own summons and decoys. **Victim:** NO_FALL cancels falls; **Foresight cancels the hit and blinks you 2.5 blocks aside** (`MindPowers.java:248-255`); Bulwark ×(1−b); Brace ×(1−r) (r 0.6 / 0.75 Iron / 0.45 Thorns); Read Intent ×0.85 from mobs; melee within 5 blocks is reflected (Brace 0.5/1.0 of the original amount, Nanite as Charge). **Attacker:** Backstab ×buff, with a 50 ms hit-stop and 1.5° shake (`PowerEvents.java:155-160`); Cloak ×1.8 and breaks it; Read Intent ×1.15 on Enemies; Twin echo. |
| 16 | LOW | `GearCombat.onIncomingDamage` (`gear/GearModule.java:23`; `gear/GearCombat.java:49-251`) | **Victim:** Veil Flicker (4× Veilwoven, HP < 40%): chance min(0.35, 0.10 + 0.01·(SPI−5)), cooldown 160 ticks, **cancels the hit** (`GearCombat.java:231-243`, `SetBonus.java:57-58,122-124`); Force ward 15/25%, Lord ward 20%, Spirit ward 30%. **Attacker, power damage:** focus +6..+22% (+2% per temper level) × Measure, boss cap 20% of max HP. **Attacker, melee (`PLAYER_ATTACK`, direct):** a primed Force strike (4× Gatewrought, primed for 80 ticks by a power hit) ×1.35 (+0.15 for Charge) with the Force's rider (`GearCombat.java:84-120`). |

`LivingDamageEvent.Pre` (after armour), all LOWEST clamps that keep the player alive: `Ritual.onDamagePre`
(`story/Ritual.java:197-203`, at least half a heart), `OpeningModule.onDamage` (`opening/OpeningModule.java:144-149`, at
least one heart), `DreamEvents.onDamagePre` (`lucidium/dream/DreamEvents.java:117-124`, wakes the dreamer instead).

### 1.3 Post-damage, crits, knockback

| Event | Listener | Effect |
|---|---|---|
| `LivingDamageEvent.Post` | `ForceInteractions.onDamaged` (`powers/ForceInteractions.java:121-140`) | Five-Forces reactions on Force damage, at most one per target per 15 ticks. |
| | `Hits.onDamage` (`living/combat/Hits.java:34-52`) | Sends `living_hit_fx` (entity, inflicted amount, crit, force, incoming) to the attacker; the incoming copy to the victim only marks the fight. |
| | `VeilEvents.onDamage` (`veil/VeilEvents.java:111-132`) | The first time a player drops to ≤ 20% HP in a gate: a one-off out-of-body "glimpse" (50 ticks). |
| | `DreamEvents.onDamagePost`, `LayerWatch.onDamage` (LOWEST, `core/audio/AudioModule.java:33`, `LayerWatch.java:164-174`) | Dream bookkeeping; drives the **combat/lord music layers**. |
| `CriticalHitEvent` | `ProgressionModule.onCrit` (`progression/ProgressionModule.java:343-352`) | When vanilla didn't crit: chance min(0.35, (PER−5)·0.004 + perk); a hit it makes a crit gets **×1.6**. Vanilla's own crits keep vanilla's multiplier (SUSPECTED 1.5), so "crits ×1.6" (SPEC §3) holds only for PER crits. |
| | `CraftEvents.onCrit` LOW (`living/craft/CraftEvents.java:90-94`) | Weak Spot: crits ×1.15 on a kind you've killed 20 of. |
| `LivingKnockBackEvent` | `BodyServer.onKnockBack` (`body/BodyServer.java:401-406`) | The momentum extra, once per hit. No other knockback listener. |
| `SweepAttackEvent`, `LivingShieldBlockEvent` | **none** | (grep across `src/main/java`). |

Melee-relevant attributes, all VERIFIED:
- `progression/Progression.java:294-297`. STR: ATTACK_DAMAGE +2.5%/pt (multiply-base) minus Fracture 0.15 and Strain.
  AGI: ATTACK_SPEED +1%/pt (cap 60%) and MOVEMENT_SPEED +0.4%/pt (cap 40%). VIT: +0.6 HP/pt.
- `gear/SetBonus.java:191-225`. Cornered (4× Beastbound, < 50% HP): ATTACK_DAMAGE +min(0.30, 0.15 + 0.01·⌊(STR−5)/2⌋) and +0.3
  knockback resistance (`SetBonus.java:114-116`). Sovereign's Measure: extra STR, AGI or VIT points.
- `hearth/Upgrades.java:295`. Forge temper: +1.0 ATTACK_DAMAGE per level.
- Power buffs: Flow +40% attack speed (`powers/catalog/VelocityPowers.java:143-144`); Brace sets knockback resistance to
  1.0 (`KineticPowers.java:316`); Stunned −60% attack damage (`powers/content/PowerContent.java:79-81`).
- Weapons: one **Blade** per Hunter tier, 6 tiers E..S (`gear/HunterTier.java:31-44`). Damage 4/5/6/7/8/9; attack speed
  −2.2/−2.3/−2.3/−2.3/−2.3/−2.2, which is 1.8 or 1.7 swings a second on base 4 (SUSPECTED base). Durability 220-2800.
  Each is built with `Item.Properties.sword(...)` (`gear/GearItems.java:70-71`) and drawn as a flat 2D `item/handheld`
  sprite (`src/main/resources/assets/awaken/models/item/*_blade.json`). The Focus has −2.8 speed and `Weapon(1)`; the
  Mattock has −2.9 speed and `Weapon(2, 0f)` (`GearItems.java:72-96`). The `GearItems` header says "28 items, E to B",
  but the loop registers all 6 tiers × 7 pieces, so the comment is stale.

Scaling gap: power damage scales with player level, ×(1 + 0.075·level) (`powers/PowerMath.java:42-44`), and is capped at
20% of a boss's max HP per hit (`PowerMath.java:16,63`). **Melee scales only with STR, gear and temper, and has no boss
cap.** SPEC §2 relies on "3-6 hits per normal mob" from stats and gear.

### 1.4 Invulnerability frames

- 26.3 names: `LivingEntity.damageCooldownTime` holds the i-frames; `Entity.invulnerableTime` now means full
  invulnerability (`docs/api/entities.md:16,339`).
- AWAKEN only ever **removes** i-frames. Multi-hit powers do it in `Combat.hurtFrom` (`powers/Combat.java:113`) and
  `hurtRaw` (`:128`). A Charged mob's extra hit does it (`mobs/entity/AwakenMob.java:544`). TimeSlow counts cooldowns down
  in real time for slowed entities (`powers/TimeSlow.java:70`). Possession and Cord Eaters do the same for driven mobs
  (`veil/Possession.java:187-188`, `veil/CordEaters.java:123-124`).
- Nothing grants i-frames. Nothing calls `setInvulnerabilityTicks`. No damage type is tagged `bypasses_cooldown` or
  `no_knockback`: AWAKEN has 8 damage types (`src/main/resources/data/awaken/damage_type/`) and no damage-type tags.
  SPEC §13: "No invincibility frames anywhere."
- Pitfall: `LivingIncomingDamageEvent` fires **before** the i-frame check (`docs/api/entities.md:338`). Listeners
  therefore see hits that vanilla then drops. The gate run's damage counters (`Modifiers.java:128-131`) and DAMAGE_TAKEN
  count them. A parry or dodge built there must check `damageCooldownTime` itself.

### 1.5 Attack cooldown and attack speed

- Vanilla's attack strength ticker is untouched. Nothing resets it and nothing reads `attackStrengthTicker`. The only
  reader is momentum: `p.getAttackStrengthScale(0.5F) ≥ 0.9` (`body/BodyServer.java:381`, `BodyRules.java:103`).
- Attack speed is attribute-only: AGI, Flow and Measure (above).
- Nothing touches reach (`ENTITY_INTERACTION_RANGE`) or sweep.
- GameTests already call `ServerPlayer.attack(target)` on fake players (`body/BodyTests.java:646-667`), and
  `ATTACK_SPEED` base 1024 forces a full swing every time. That is a ready harness for melee tests.

### 1.6 Every defensive or stagger-like concept that exists anywhere

| Concept | What it is | Side | Evidence |
|---|---|---|---|
| Landing **roll** | Fall softening only: a fall of 3+ blocks while sprinting or sneaking adds +3 safe fall and ×0.6 beyond. Not a dodge. | player | `body/BodyServer.java:335-359`, `BodyRules.java:27-32` |
| **Slide** | Crouch while sprinting: 14 ticks, crawl-height box (dodges by geometry), trips small foes | player | `body/client/ClientMoves.java:232-269`, `BodyServer.java:152-216` |
| **Trip** | A slide staggers Enemies ≤ 1 block tall: Slowness VI for 16 ticks, a hop, navigation stopped | player→mob | `BodyServer.java:189-216`, `BodyRules.java:105-106` |
| **Dash** (power) | Movement slot, cost 10, cooldown 14 ticks, forward along the look (1.9 b/t ground, ×0.8 in air), contact damage 2 in a 1.6 radius for 7 ticks; Blink variant teleports 6+ blocks | player | `powers/catalog/VelocityPowers.java:45-51,96-135` |
| **Foresight** (power) | Rank C, hidden, 35 energy, 600-tick cooldown: the next hit from something is cancelled and you blink aside; slows the world to 10% | player | `PowerEvents.java:121-126`, `MindPowers.java:75-79,226-255` |
| **Brace** (power) | Rank D, 15 energy, 240-tick cooldown, 60 ticks: −60% damage, knockback resistance 1.0, reflects 50%; on expiry a burst of radius 3 | player | `KineticPowers.java:84-89,309-331` |
| **Bulwark / Barrier / Nanite Skin** | Damage-reduction buff; absorption shield; +8 armour and a shock on melee | player | `PowerEvents.java:128-145`, `EnergyPowers.java:201`, `TechPowers.java:266` |
| **Veil Flicker** (gear) | A chance to cancel a hit below 40% HP | player | `gear/GearCombat.java:231-243` |
| Air **barrel roll** | Skyborn flight only (SOAR state): double-tap A or D within 7 ticks, cooldown 20 ticks, costs 3% Aether, impulse 0.85, camera roll ≤ 4°. No i-frames. | player | `body/ascent/client/AscentMoves.java:280-290,959-979`, `AscentRules.java:330-331` |
| **Air dash** | Leaper tier: sprint pressed in the air, 0.95 b/t or ×1.33 of current speed, once per airtime | player | `AscentMoves.java:849-891`, `AscentRules.java:67` |
| Mob **GUARD ("Parry")** | Gate mobs, bosses and the Shell Up fauna: windup 8, cooldown 160, guard 60 ticks; the next direct hit is ×0.2, plays SHIELD_BLOCK and counter-attacks if within 4 blocks | mob | `mobs/entity/AbilityRunner.java:272-275`, `AwakenMob.java:505-512`, `mobs/def/Ability.java:105` |
| Mob client **stagger** | Visual only: the life layer's flinch springs on hurt | mob, client | `mobs/client/life/Life.java:341-358` |
| **TimeSlow** | Server slow motion: entities within 18-20 blocks tick every Nth tick (Flow 1/4, Foresight 1/10) | world | `powers/TimeSlow.java:1-81` |
| Vanilla shield | Untouched; AWAKEN has no shield item or shield listener. The mantle refuses while blocking. | player | `BodyServer.java:221`, `ClientMoves.java:344` |

Absent everywhere: **parry by the player, posture or poise, guard break, perfect-block or perfect-dodge windows, combos,
heavy or charged melee, lock-on.** The grep for parry, posture, poise, guard, dodge, combo, iframe and hitstop finds
only the items above, plus mob ability names.

---

## 2. Combat feel already built

### 2.1 Hit-stop (SPEC §6: "hit-stop 40-80 ms on heavy hits (client time scale on the hit entity's render + camera kick)")

**It exists, for powers only, and it is a render freeze rather than a time scale.** VERIFIED:
- Server: `Fx.feel(player, hit, ms, shake)` sends `power_feel {entityId, hitstopMs, shake}` **to the attacker only**
  (`powers/Fx.java:223-225`; payload `powers/net/PowerPayloads.java:45-50`). `Fx.quake` sends shake only, to players
  within a radius, scaled by distance (`Fx.java:228-233`).
- Callers: 12 power sites, 20-80 ms with shake 0.8-6°. Examples: Overload 80 ms and 6°
  (`powers/catalog/EnergyPowers.java:315`); Haymaker 40+40·charge ms (`KineticPowers.java:209`); Spirit 20-80 ms
  (`SpiritPowers.java:189,202,370,372`). Backstab is the one melee-adjacent caller (`PowerEvents.java:160`). **Plain
  melee never calls it.**
- Client: `ClientPowers.onFeel` (`powers/client/ClientPowers.java:149-155`) puts a `Freeze(untilNanos)` in a map.
  `CombatFeel.modify` is a render-state modifier on every entity (`powers/client/CombatFeel.java:45-76`). It captures
  x, y, z, `ageInTicks` and the walk animation once, then holds them, trembling ±0.04 blocks every 16 ms. Wall-clock
  nanos, not ticks. The caster's cast pose holds too (`FxClient.onFeel` → `CastAnims.hold`, which has tick granularity,
  `CastAnims.java:134-150`), and a Player Animation Library (PAL) clip pauses (`PalCasts.freeze`).
- Camera: `CombatFeel.kick(shake, 8 + 2·shake ticks)`. Strength is capped at 8°, falls off with t², and is a sin
  shake on yaw, pitch and roll (`CombatFeel.java:21-42`). Gated only by `AwakenConfig.Client.CAMERA_SHAKE`.
- What does **not** happen: no world or game time scale; no freeze of the attacker's view or first-person hands, except
  the cast pose hold; no hit-stop seen by other players; no audio duck.

### 2.2 Camera and FOV writers ("one voice"?)

| Listener | Event (prio) | Drives | Comfort switches it honours | Evidence |
|---|---|---|---|---|
| `CombatFeel.onCameraAngles` | ComputeCameraAngles (N) | power hit shake | `cameraShake` only | `powers/client/PowersClient.java:44` |
| `FxScreen.onAngles`, `onFov` | ComputeCameraAngles, ComputeFov (N) | power directional kick (decays ×0.62 a tick), FOV punch, flash, impact frames (post effects `power_fx_impact_0..3`), speed lines | `cameraShake`, `powerEffects`, `screenFlashes` | `powers/client/fx/FxScreen.java:48-160`, `FxClient.java:55-56` |
| `FeelDirector.onAngles`, `onFov`, `onHand` | ComputeCameraAngles, ComputeFovModifierEvent, RenderHandEvent (N) | **Ascent only**: a pitch spring (`pitchKickV += deg·0.55`, k 0.28, damping 0.62), roll ≤ `cameraRoll`, speed FOV, shake, streaks, flash, landing weight | `cameraRoll` 0-7.5 (default 5), `speedFov`, `movementShake`, `reducedMotion`, `cameraShake` | `body/client/feel/FeelDirector.java:18-310`, init at `body/ascent/client/AscentClient.java:45` |
| `BodyCamera.onAngles`, `onFov`, `onHand` | same (N) | first-person move beats: **roll dip +32° pitch and 7° roll sway**, mantle ±9°, slide roll 7.5° (+7% FOV), tactical sprint lean (+3.5% FOV) | only `wallRunRoll`. **The roll dip and slide roll ignore reducedMotion and shake** | `body/client/BodyCamera.java:41-89` |
| `ActionCamera` | CalculateDetachedCameraDistanceEvent, or Shoulder Surfing's offset | shoulder-view arm by pose: GROUND, SLIDE, WALL_RUN, HANG, ROPE, ZIP, CARRY, GLIDE, FLIGHT, AIM, SOVEREIGN. **No combat pose.** | `actionCamera`, `reducedMotion` (halves) | `body/ActionRig.java:14`, `body/client/ActionCamera.java` |
| `OpeningCamera` / `OpeningClient.shake`, `RitualCamera`, `VeilWorld.onCamera` | various | cutscenes, the Veil | n/a | `opening/client/OpeningClient.java:90-92`, `story/client/RitualClient.java:86-87`, `veil/client/VeilClient.java:49` |
| Camera Overhaul (pack mod) | its own | strafe and turn roll | paused only in Ascent states (`CameraOverhaulPause`) | `FeelDirector.java` tick |

FeelDirector's header calls it "the one client voice for how moving through the air feels". **No class owns the camera
in combat.** Power shakes (CombatFeel, FxScreen), body beats (BodyCamera), Ascent beats (FeelDirector) and Camera
Overhaul all add up independently. FeelDirector exposes moment methods (`kick` springs, `land`, `bonk`, `slam`, `dash`)
that a combat feel could reuse, but its `kick` is private and its public moments are Ascent-themed.

### 2.3 Body feel in a fight (VERIFIED)

- **Momentum hits** (table 1.2 #1). SPEC §13 bands: sprint ~+10%, tactical sprint ~+18%, slide +30%. The hit's target
  only; sweep victims get nothing (`swingTarget` and `swingTick` gate, `BodyServer.java:62-66,379`).
- **Trips**, checked every other slide tick (`BodyServer.java:316`).
- **Tactical sprint**: double-tap sprint (≤ 7 ticks apart), speed ×1.25 for 60 ticks, recharge 80 ticks (−10 a rank,
  minimum 30) (`BodyRules.java:36-60`). **A swing ends it** on both sides (`ClientMoves.java:225-228`,
  `BodyServer.java:368`).
- **Slide-casting**: a cast mid-slide keeps the slide. A power's motion ends it, snapped onto the slide's line; a
  Velocity dash out of it goes ×1.3 (`ClientMoves.java:452-463`, `core/api/MovementHooks.java:18`). Move twists:
  `powers/MoveTwists`.
- **Runner's Kit is on by default** (`body/BodyConfig.java:19-21`), so `Body.unlocked` is true for everyone
  (`body/Body.java:40-41`). Momentum and trips apply from the first second, not from awakening.

### 2.4 Comfort settings (four files, overlapping)

| File | Keys |
|---|---|
| `awaken-client.toml` (`core/AwakenConfig.java:58-62`) | `cameraShake` (everything shake-like). "Screen shake on heavy hits." |
| `awaken-feel-client.toml` (`body/client/feel/FeelConfig.java:18-45`) | `cameraRoll` 0-7.5 (5), `wallRunRoll` 0-7.5 (5), `speedFov` 0-1, `movementShake`, `reducedMotion`, `actionCamera`, `shoulderView`, `freeRun` |
| `awaken-power-fx-client.toml` (`powers/client/fx/PowerFxConfig.java:19-28`) | `powerEffects` FULL/REDUCED/MINIMAL, `castAnimations`, `screenFlashes` |
| Holo client config (`holo/client/HoloConfig.java:29,40`) | `hudMotion` OFF/SUBTLE/FULL, its **own** `reducedMotion` |
| Living client config (`living/client/LivingClientConfig.java:20-31`) | `plates`, `damageNumbers` FULL/CRITS/OFF, `xpStream`, `levelMoment` |

There are two separate `reducedMotion` switches (feel and holo). A combat feel layer should read one comfort source;
today there isn't one.

### 2.5 Sounds

| When | Sound | Ours or vanilla | Where |
|---|---|---|---|
| Melee swing, hit, crit, sweep, knockback, no damage | vanilla `entity.player.attack.*` | vanilla (SUSPECTED, Player.attack) | — |
| Momentum hit, trip, slide wall thud | `awaken:body.impact` ("a low punch and a short crack, under vanilla's own hit sound") | ours, synthesised | `tools/art/specs/audio_body.py:14,136-141,209`; `BodyServer.java:201,389` |
| A crit's damage glyph | `awaken:living.crit` (glass crack, F#6 A6 D7 rise), UI, local | ours | `tools/art/specs/audio_living.py:10,61-`; `living/client/LivingClient.java:143` |
| Primed Force strike | vanilla AMETHYST_BLOCK_CHIME 0.9 / 0.7 | vanilla | `GearCombat.java:119` |
| Veil Flicker | vanilla ILLUSIONER_MIRROR_MOVE | vanilla | `GearCombat.java:240` |
| Overflow burst | vanilla WIND_CHARGE_BURST | vanilla | `GearCombat.java:173` |
| Mob guard (parry) | vanilla SHIELD_BLOCK 1.0 / 0.8 | vanilla | `AwakenMob.java:510` |
| Double Gate law block | vanilla SHIELD_BLOCK 0.8 / 1.6 | vanilla | `Twists.java:486` |
| Power hits | `awaken:power.impact`, `.punch`, `.slam` … (50 events) | ours | `src/main/merge/powers/assets/awaken/sounds.json` |
| Cast refused | `power.fail`, caster only | ours | `powers/Casting.java:62-66` |
| **Player hurt, block, parry, guard break, low HP** | vanilla hurt only; **nothing of ours** | — | `sounds.json` in every merge folder lists none |
| Combat music | combat and lord layers, decided every 10 ticks | ours | `core/audio/LayerWatch.java:164-174`, SPEC §13 audio |

All of AWAKEN's sounds are synthesised in numpy (`tools/art/audiokit.py`, specs in `tools/art/specs/audio_*.py`,
rendered by `tools/art/audio_build.py`). A combat sound set would follow the same pipeline.

### 2.6 Particles and FX on hit

- Melee: vanilla crit and sweep particles (SUSPECTED). On a momentum hit or trip, our own ground-tinted dust burst at
  the struck body, client-built from `body_seen` (`body/client/BodyFx.java:94-105`, `BodyClient.java:119-120`). A primed
  strike sends 14 server particles of the Force's kind (`GearCombat.java:118`).
- Powers: the client-built FX system (`powers/fx`, `powers/client/fx`): pooled sprites, 192 shapes, debris and
  afterimages. Budgets per `powerEffects` level (3072/1536/512 live sprites, 220 per effect, culled past 72 blocks)
  (`docs/modules/power-fx.md` §4-5). **Melee doesn't use it**, but it is the obvious home for slash trails, impact
  sparks and parry flashes (an `FxKind` per event, ~50-byte payloads).

---

## 3. HUD and readouts

| Readout | What it shows | Data path | Evidence |
|---|---|---|---|
| **Damage glyphs** (SPEC §21.1) | Numbers drawn in strokes of light at the hit point; 14 ticks (700 ms); 24 pooled slots; hits on one target within 6 ticks merge (same crit-ness); coloured by Force; crit gold plus the chime; **damage taken is never shown** | Server `Hits.onDamage` (LivingDamageEvent.Post, `getInflictedDamage`, after armour) → `living_hit_fx {entity, amount, crit, force, incoming}` to the attacker (`living/combat/Hits.java:42-51`, `living/LivingNet.java:42-50`) → `LivingClient.onHit` (`living/client/LivingClient.java:132-144`) → `LivingFx.glyph` (`LivingFx.java:54-92`). Crit = a crit event in the last tick and the direct entity is the attacker. Off under the PURE_SURVIVAL gamerule (`LivingModule.java:163-169`) or `damageNumbers`. |
| **Creature plates** | Name, level chip, 10-segment bar with an amber drain, ≤ 4 at once, look-picked within 24/32/40 blocks by PER | client picking `LivingClient.PICKED`, drawn on Holo glass `living/client/LivingPlates.java` | SPEC §21.1 |
| **XP stream** | Motes into the band | `living_xp_fx` | `LivingClient.java:146-150` |
| **Power bar** | Energy bar over 6 slots (Z X C V · R · Alt), cooldown sweep, run glow, refusal flash | client loadout sync | `powers/client/PowerHud.java:1-30` |
| **Sprint line** | 2-px Holo-blue tactical sprint recharge line | client prediction | `body/client/SprintBar.java`; SPEC §17.15 |
| Calm HUD | The power bar and level line fade to 35% out of combat (combat = hurt within 100 ticks or an Enemy within 8 blocks, scanned every 10 ticks) | `SystemHud.inCombat` | `holo/client/CalmHud.java:47`, `signal/client/SystemHud.java:161-163,207-215` |
| Health | **Vanilla hearts** (not replaced); AppleSkin in the pack | — | — |
| Hurt feedback | Vanilla red tint and damage tilt. Lens re-applies the tilt in its own matrices (`lens/client/LensFrame.java:68,140-155`); Holo panels undo it (`holo/client/ViewMath.java:44-59`). Veil body-hurt flash while projecting (`veil/client/VeilHud.java:97`). | — | — |
| Low-health | **None**, except the one-off Veil glimpse at ≤ 20% in a gate | — | `veil/VeilEvents.java:115-124` |
| Target lock | **None.** Powers have a server aim assist (fat ray `Combat.aimEntity`, `powers/Combat.java:211-234`); the Danger Sense, Third Eye and Read Intent outlines tint render state (`ClientPowers.markColour`) | — | — |

"In combat" is tracked four separate ways: `Hits.inFight` (server, 100 ticks, `Hits.java:59-63`); `PowerState.inCombat`
(server, 100 ticks, `powers/PowerState.java:150-156`); `SystemHud.inCombat` (client); `LayerWatch` (music, its own
holds). A combat system needs one authoritative "engaged" state; it doesn't exist yet.

---

## 4. Input map

### 4.1 Every key AWAKEN registers (VERIFIED: `core/client/AwakenKeys.java:18-30,38-44`, `body/ascent/client/SovereignClient.java:25-26`, `compat/client/LookClient.java:23-24,31`; all `KeyConflictContext.IN_GAME`, category `awaken`)

| Key (default) | Mapping | Purpose | Consumers |
|---|---|---|---|
| Z | `key.awaken.power_1` | Active power slot 1 | `powers/client/ClientCasting.java:32` |
| X | `power_2` | Active slot 2 | `:33` |
| C | `power_3` | Active slot 3 | `:34` |
| V | `power_4` | Active slot 4 | `:35` |
| R | `ultimate` | Ultimate | `:36` |
| Left Alt | `movement` | Movement power slot. **Owned by the rope once the Tether tier is held** (the slot's power never casts from it) | `ClientCasting.java:37,51`; `body/ascent/client/AscentMoves.java:180`; `body/client/feel/ShoulderView.java:48` |
| K | `status` | K menu (Holo status) | `holo/client/HoloStatusScreen.java:80`, `progression/client/ProgressionClient.java:51` |
| G | `focus` | Meditate / project (Veil) | `veil/client/VeilInput.java:59-89` |
| H | `reality_check` | Lucidium reality check | `lucidium/client/LucidiumClient.java:104-110` |
| Y | `accept` | Signal: accept | `signal/client/SystemHud.java:219` and others |
| N | `dismiss` | Signal: dismiss | `SystemHud.java:220`, `veil/client/VeilInput.java:50` |
| B | `bind` | Bind Echo | `hearth/client/EchoKeys.java:19-24`, `veil/client/VeilInput.java:93` |
| J | `playtest_flag` | Playtest: flag an unfair death | `core/client/CoreClient.java:13` |
| U | `ascend` | Sovereign form | `SovereignClient.java:25` |
| O | `look` | Look switch (Swift / Cinematic / Ultra) | `LookClient.java:23` |

The lang file also has `key.awaken.satchel` ("Open Satchel", `src/main/merge/gates/.../en_us.json`) with no
`KeyMapping`. It is stale.

### 4.2 Vanilla keys AWAKEN gives extra meaning (VERIFIED)

| Vanilla key | Extra meaning | Evidence |
|---|---|---|
| Sprint (L-Ctrl) | Double-tap (≤ 7 ticks): tactical sprint. **Held with forward: the one-button free-run** (vault, mantle, jump at walls, leap edges, wall-run in the air, climb lines; off while using an item, sneaking or backing). In the air with Leaper: **air dash**. In flight: boost. | `body/client/ClientMoves.java:115-122`; `body/client/FreeRun.java:37-39`; `AscentMoves.java:852` |
| Sneak (L-Shift) | While sprinting on the ground: **slide**. Held on landing: roll. In the air, 4+ up, with Leaper: **dive slam** (3+3·speed damage, radius 3.5). Gliding: dive. Rope: reel in. Flight: sink. Stalker ×1.5 while sneaking. | `ClientMoves.java:130-133,409`; `AscentMoves.java:853`; `AscentServer.java:296-317`; `CraftEvents.java:101` |
| Jump (Space) | Mantle a ledge; wall-run, wall jump, ledge moves; slide-cancel; with Leaper a double jump; in the air with Wings, open the wings; Skyborn double-tap takes flight; on the rope, launch | `ClientMoves.java:123-129,291-296`; `AscentMoves.java:851,214-219` |
| A / D | In flight (SOAR): double-tap to barrel roll | `AscentMoves.java:290,959-979` |
| Attack (LMB) | Ends the tactical sprint (never cancelled). The Veil spirit consumes attack and use. The opening's shots cancel them. | `BodyClient.java:55-57`; `veil/client/VeilInput.java:83-84`; `OpeningCamera.java:241-245` |
| Use (RMB) | **Not consumed for a blade or an empty hand in the air.** Right-click-block handlers act only on specific blocks: berry bushes and a campfire with an iron ingot (`CraftEvents.java:163-180`), a city rescue (`citizens/CitizensModule.java:92-96`), a bed with the Dream Journal (`lucidium/dream/DreamEvents.java:247-256`), city protection and ladders. Item `use()` overrides exist only on utility items (Scanner, Whistle, Dream Journal, Power Core, Essence Cell, Core Shard, Relics). Vanilla swords have no use action in 1.21.5+ (SUSPECTED for 26.3). | grep `public InteractionResult use(` |
| Toggle perspective (F5) | `core.api.CameraHooks.toggleView` (with Shoulder Surfing: first person ↔ shoulder) | `core/api/CameraHooks.java`; `opening/client/OpeningClient.java:340` |
| Inventory (E) | Opens the Holo inventory in vanilla's place | `holo/client/HoloClient.java:100` |

### 4.3 What's free for parry, dodge or heavy attacks

- **Free from AWAKEN's side:** RMB with a melee weapon or an empty hand; Mouse 4/5; I; M; P; F6-F10; Caps Lock; Right
  Alt and Right Ctrl; backtick; a double-tap of a direction on the ground (the barrel roll uses it only in flight);
  LMB hold or release patterns (nothing reads them).
- **Likely taken by vanilla or the pack (SUSPECTED, from memory; check before choosing, `pack/mods.json` lists the
  mods).** Vanilla: C and X are "save/load hotbar" (creative only) and collide with power keys 2 and 3. 1.21.6+ "Quick
  Actions" may default to G (collides with Focus). Simple Voice Chat: V menu (Power 4), Caps Lock push-to-talk, G group,
  M mute, N disable, H hide icons. Iris: R reload shaders, K toggle shaders, O shader pack screen (Ultimate, Status,
  Look). Xaero's World Map: M. Shoulder Surfing: an O or arrow-key set. **Investigate: AWAKEN has no key-conflict audit
  anywhere** (`docs/` grep).
- **Design constraint:** the Runner and Ascent rule "No new keys: sprint, jump, sneak and movement keys only" (SPEC
  §16.3 line 1532, §17.1 line 1685) is about movement. A combat overhaul has to decide whether it may add keys (dodge,
  parry) or must overload existing ones. Overloading sneak or sprint collides with slide and free-run; overloading Left
  Alt collides with rope and Dash.

---

## 5. Networking pattern for client-predicted actions (the template for dodge and parry)

### 5.1 The Awakened Body pattern (best fit)

- **Client:** everything happens in `MovementInputUpdateEvent`, after the tick's keys are read and before vanilla moves
  the body (`body/client/BodyClient.java:46`, `ClientMoves.java:75-138`). A move sets this tick's velocity or edits the
  keys it lets through (a cached `Input[128]`, no allocation, `ClientMoves.java:165-172`). Then vanilla collision runs.
  The start is sent once, `BodyPayloads.Start {move, yaw, x, y, z, speed, flag}`, and an early end once,
  `End {move}` (`ClientMoves.java:174-180`; `body/net/BodyPayloads.java:13-33,64-69`).
- **Server:** the `registrar("1")` play-to-server handler (`body/BodyModule.java:50-66`) calls `BodyServer.onStart`
  (`BodyServer.java:84-94`). That checks the unlock and `off()` (riding, sleeping, spectator, dead, flying, gliding,
  spirit, meditating; `:77-80`), cooldowns on the `BodyMotion` attachment (unsaved plain fields, overworld game time,
  `body/BodyMotion.java:1-30`), and the geometry again from the server's own position (`BodyRules.findLedge`,
  `crawlFits`). It pays exhaustion. Then `did()`: count it, `playSound(p, …)` (everyone but the mover), and `show()`
  sends `body_seen {entityId, what, yaw}` to players within 96 blocks (`BodyServer.java:239-286`).
- **Refusal:** `Refuse {move}` plus a correction: a teleport back for a mantle, zero horizontal velocity for a slide
  (`BodyServer.java:107-117`; client `ClientMoves.java:423-445`). The server ends a running move with `tellClient=true`.
- **Per tick:** `BodyServer.tick` only for players with a move running (`:290-325`). The server lets a slide run
  `SLIDE_GRACE = 6` ticks past the client's (`BodyRules.java:85`).
- **Lag compensation (Runner variant):** a claimed start must be within `CLAIM_TOL = 0.75` of one of the server's last
  `HISTORY = 12` positions (a ring buffer, no allocation; `body/RunnerServer.java:74-80,328-334`, `RunnerRules.java:73-76`,
  `BodyMotion.java:38-67`). There is a debug latency knob (`RunnerServer.java:29-38`) and a refusal bench
  (`RunnerServer.java:544-641`). SPEC §17.1 G0: history checks gave "zero rubber-banding at 150 ms".
- **Clocks:** the server uses `overworld().getGameTime()` (`BodyServer.java:68-70`); the client counts its own
  `ClientMoves.ticks`. **No timestamps are exchanged.** Timing is judged when the packet arrives, so a parry window
  needs its own lag allowance: a server-side window or history, like the Runner's.

### 5.2 Powers pattern

- `power_cast {slot, phase PRESS|RELEASE, predicted, yaw, pitch}` (`ClientCasting.java:60-115`). A predictor applies
  its motion locally first (`t.predictor.motion`), with a predicted energy spend and cooldown (`ClientPowers.java:52-61`).
- The server checks owned, passive, alive, toggle or channel state, cooldown and energy, then runs the behaviour
  (`powers/Casting.java:37-127`). Refused: `power_feedback` with `undo`, which zeroes velocity (`Casting.java:53-67`).
  **The aim (yaw, pitch) comes from the client.** Predicted motion is not re-checked; only the cast itself is.
- Running effects tick every server tick through `Running`; every end path calls its end callback once.

### 5.3 Ascent pattern (weakest validation)

`AscentPayloads.Event {what, speed}`: the client reports a moment, the server gates it by tier and state and replays
`SeenEvent` (`body/ascent/AscentServer.java:252-291`). The dive slam's damage uses the **client's speed, clamped to
0..3** (`AscentServer.java:256,296-317`). Don't copy this for anything that deals damage or gives defence.

### 5.4 Shared plumbing

`core/AwakenNet.send` skips players without the channel (fake players, other mods) (`core/AwakenNet.java:16-21`).
Payloads are versioned by registrar. Records use `StreamCodec`. Server-only state lives in unsaved attachments.

---

## 6. Multiplayer: how others see it

- **Body moves:** `body_seen` within 96 blocks drives `BodyAnims.play` (vanilla poses) or PAL clips through
  `core.api.PlayerClips` (layer `player_clips`, priority 1600) (`BodyClient.java:111-146`,
  `powers/client/fx/PalCasts.java:43-67`).
- **Casts:** `power_fx` events reach players within 72 blocks of the caster, the effect or its end point. The event
  carries the cast anim, so others' `CastAnims.play` and PAL clips (layer `power_casts`, priority 1500) start when it
  arrives. Known gap: others see the wind-up 2-3 ticks late (`docs/modules/power-fx.md` §8). Arm swings:
  `Moves.swing(…, SwingAnimation.DEFAULT, true)` broadcasts (`powers/Moves.java:120-123`).
- **Ascent:** `AscentPayloads.Seen` and `SeenEvent` to everyone near but the mover (`AscentServer.java:323-327`).
- **Private to the attacker:** hit-stop and shake (`Fx.feel`), damage glyphs (`HitFx`), cast-fail sound.
  **Public:** momentum and trip dust and the impact sound, the power FX, `Fx.quake` shakes by distance.
- **PvP:** powers never hurt players (`Combat.isAlly`: any Player is an ally, `powers/Combat.java:41-49`). The slam
  and the Judgement Fall hit players only with the `pvp` rule (`AscentServer.java:308`, `Sovereign.java:148`). Melee
  follows vanilla PvP. Projected Bodies are guarded (Together, SPEC). There is no party friendly-fire rule for melee
  beyond vanilla teams (SUSPECTED).
- **Together** (SPEC Together section and `docs/multiplayer-audit.md`): parties come from `core.api.PartyHooks` (a gate
  run plus Hearth members). Shared rewards are split within 32 blocks. Ascension tiers even out every hit between a
  creature and a player. Nothing there covers combat feel or PvP balance.

---

## 7. Performance notes for combat paths

- Budgets (SPEC header): p50 ≥ 110 fps in a full gate fight on an M4; server tick p95 ≤ 25 ms; ≤ 60 active combat
  entities; ≤ 3 ms a tick for any background job.
- Server, per hit: every listener in 1.2 runs once per hit. Allocations per hit: `ForceInteractions.Subject.of`
  (an EnumSet), a `RECENT` HashMap capped at 512 (`ForceInteractions.java:128-131`), and `Hits` UUID maps. Fine per
  hit, not per tick. Multi-hit powers that reset i-frames multiply this, and each landed hit sends its own `HitFx`; there
  is no rate limit.
- Server, per tick: `BodyServer.onPlayerTick` only for active moves; `RunnerServer.track` writes the history ring
  without allocation (`RunnerServer.java:327-334`). `TimeSlow.onEntityTick` runs on every entity tick and exits early
  when no zone exists (`TimeSlow.java:51-52`). Power ticks are wrapped in `guard()` so one broken effect can't take down
  the tick (`PowerEvents.java:66-88`). `Combat.foesAround` allocates an ArrayList per call (each tick of a running area
  power).
- Client: `CombatFeel.modify` runs for every entity's render state every frame. It exits early when the marks and freeze
  maps are empty, but boxes `Integer` keys when they aren't (`ClientPowers.java:128-132`, `CombatFeel.java:45-50`). FX
  pools and LivingFx glyphs are fixed-size with no per-frame allocation. FeelDirector and BodyCamera use static scratch
  arrays. `SystemHud` scans for Enemies every 10 ticks (`SystemHud.java:212-214`).

---

## 8. The Hands first-person plan (origin/pack/fp-anims:docs/anims/PLAN.md; plan only, awaiting Euan; no code)

What a combat animation system must share or be planned with:
- **One owner of first-person drawing.** One `RenderHandEvent` listener at HIGHEST. When a layer is active it cancels
  vanilla and draws the arms itself (`AvatarRenderer.renderRightHand/renderLeftHand`) and items
  (`ItemModelResolver.updateForTopItem` + `ItemStackRenderState.submit`), as `CastAnims` already does. The five
  existing hand listeners become pose sources: BodyCamera, FeelDirector, CastAnims (LOW), OpeningCamera, Lucidium. Holo's
  `Wrist.onHand` (HIGH, `holo/client/HoloClient.java:85`) also replaces both hands while a panel is open. A combat swing
  layer would be another source, not a sixth listener.
- **Layer stack:** 1 vanilla (held item, swing, equip) → 2 stance (continuous state) → 3 action (one-shot clips) → 4
  procedural (look-lag spring, breath, grip tremble, impact springs, speed sway) → 5 camera beats stay with
  BodyCamera and FeelDirector ("Hands asks the Feel Director for its beats"). Melee swings, blocks and parries would sit
  in 2 and 3, and hit reactions in 4.
- **FP clip format and pipeline:** `assets/awaken/fp_clips/*.json` (schemaVersion 1), authored in
  `tools/art/specs/fp_moves.py` and `fp_interact.py` in `power_casts.py`'s figure terms, with the same easing names.
  **One timing table shared by 3P and FP clips** so both views stay in phase.
- **API:** a new package `dev.awaken.anims` (`anims.client.Hands`, `FpClips`, `BodyInView`) and `core.api.FirstPerson`
  (play, hold and release a clip; register a stance source), alongside `core.api.PlayerClips` for 3P
  (`core/api/PlayerClips.java`).
- **Body in view:** legs submitted through `ExtractLevelRenderStateEvent` and `SubmitCustomGeometryEvent`. The hand pass
  keeps a fixed FOV, so arms never stretch with speed FOV (combat FOV punches must respect this). PAL's first-person
  mode stays off.
- **Rules that bind combat:** "Input never waits for an animation: every interaction animates an action that has
  already happened", and any input cuts a touch. Settings: one switch per family. Reduced motion halves amplitudes.
- **Explicit conflict:** §5's table says "Mining, attacking, placing blocks: **Stay vanilla**: too frequent; any delay
  hurts". The research notes add "Better Combat (a wind-up reads as input lag; its 'feint' lets players cancel)". A
  melee animation system reverses this decision for attacking and must be agreed with Hands.
- 3P layers that exist now: PAL `power_casts` (1500) and `player_clips` (1600) (`PalCasts.java:43-46`). A combat layer
  needs a priority between or above these (weapon swings vs a slide clip). PAL and Bendable Cuboids are optional
  (`compileOnly`); without them the fallback is vanilla arm poses. There is a precedent for a custom `ArmPose` through
  the enum extension: `AWAKEN_HOLO_WRIST` with an `IArmPoseTransformer`
  (`src/main/resources/META-INF/enumextensions.json`, `holo/client/HoloArmPose.java:14-30`). That gives a no-PAL route
  for guard and block poses.

---

## 9. Gaps: what the brief needs that does not exist at all

| Brief item | State | Nearest thing to build on |
|---|---|---|
| Parry (player) | **None** | Mob GUARD logic (`AwakenMob.java:505-512`) as a mirror; the body packet pattern; TimeSlow for a slow-mo reward |
| Dodge (ground, directional) | **None**; Dash is forward-only, a power, and unreachable once Tether owns Left Alt; the SPEC forbids i-frames | Slide's prediction and validation; Foresight's blink-aside; barrel roll's double-tap detector (`AscentMoves.java:959-979`) |
| Blocking or guard with weapons | **None** (vanilla shield only, unlistened) | `LivingIncomingDamageEvent` HIGH; `LivingShieldBlockEvent` (unused); Brace numbers |
| Posture, poise, stagger meters | **None** (trip is a Slowness effect; mob stagger is visual) | `Combat.stun` (`powers/Combat.java:272-276`); the life-layer springs |
| Combos, heavy or charged melee, attack chains | **None** | `InputEvent.InteractionKeyMappingTriggered` (cancellable, `setSwingHand`) is already hooked (`BodyClient.java:55`, `OpeningCamera.java:241`) and is the no-mixin input point |
| Custom attack animations (FP and 3P) | **None for melee**; casts only | CastAnims, PalCasts and PlayerClips; the Hands plan |
| Melee hit-stop, hit feel | **None for melee** | Reuse `power_feel` and `CombatFeel` (render freeze + kick) |
| Getting-hit feel (vignette, directional indicator, low-HP heartbeat, hurt camera) | **None** (vanilla only) | FxScreen flash layer; Lens looks; HudMotion's STILL set |
| Blocking or parry feel and sounds | **None** | `tools/art/audiokit.py` synthesis; `body.impact` as a reference |
| Hundreds of 3D weapons | **6 flat 2D blades** (+6 foci, +6 mattocks) | Item components: `ToolMaterial`, `Weapon`; per-item data needs a weapon-archetype registry (none exists) |
| Weapon archetypes and data-driven moves | **None** | `PowerType` builder pattern; `FxKind` events |
| Melee damage types (slash, pierce, blunt) | **None** (vanilla `player_attack`) | `data/awaken/damage_type/*.json` (8 Force types) |
| Lock-on or target focus | **None** | Plate picking (`LivingClient.PICKED`); `Combat.aimEntity` |
| One combat camera voice | **None** | FeelDirector's spring and comfort model |
| One combat state | Four trackers | `Hits.inFight` is the most central (server, any hit either way) |
| Melee scaling vs powers | Melee scales only with STR and gear; no boss cap | `PowerMath.levelScale`, `capBossHit` |
| Key-conflict audit | **None** | `pack/mods.json` |

---

## 10. Master map: component → what it does → evidence → consequence → verdict

| Component | What it actually does | Evidence | Consequence for a combat overhaul | Verdict |
|---|---|---|---|---|
| Vanilla `Player.attack` as the melee core | Cooldown, crit, sweep and knockback are all vanilla; AWAKEN only multiplies | §1.1; no melee code anywhere | Combos, heavies and custom swings must intercept input (`InteractionKeyMappingTriggered`) and either drive `attack` themselves or replace it | **Replace or extend (decide)** |
| `LivingIncomingDamageEvent` stack (16 listeners) | Momentum, VIT, gates, story clamps, Sovereign, crafts, powers, gear | §1.2 | A parry or block layer slots in at HIGH or HIGHEST before momentum and wards; it must skip i-framed hits and respect the cancels (Foresight, Flicker, Double Gate) | **Keep; add a combat stage first** |
| Stats → attributes | STR +2.5%/pt, AGI +1% attack speed (cap 60%), VIT +0.6 HP and resistance ≤ 35% | `Progression.java:294-297`, `ProgressionModule.java:320-331` | New weapon stats should compose with these modifiers, not duplicate them | **Keep** |
| PER crits | Up to 35% chance, ×1.6; vanilla crits keep vanilla's multiplier | `ProgressionModule.java:343-352` | Crit sources disagree (1.5 vs 1.6); a weapon crit system should unify them | **Extend** |
| Momentum hit and knockback | ×(1+0.3·band) and +0.6 knockback from speed; the swing's own target | `BodyServer.java:364-406`, `BodyRules.java:97-106` | A good "moving strike" primitive; its tick and target gate shows how to pair an attack with its damage event | **Keep** |
| Trip | Slowness VI for 16 ticks on small Enemies | `BodyServer.java:189-216` | The stagger model is an effect, not poise; a poise system would supersede or reuse it | **Extend** |
| GearCombat | Wards, Flicker, primed Force strike on melee, focus, Overflow | `GearCombat.java:49-251` | Gear procs are melee hooks keyed on `PLAYER_ATTACK` + direct entity; new swing types must keep using that source or these break | **Keep (watch the damage source)** |
| Powers' defensive buffs | Brace −60%, Foresight cancel-and-blink, Bulwark, Read Intent | `PowerEvents.java:111-146` | Overlap with parry and block: decide whether parry is a body skill and Brace stays a power | **Keep; reconcile** |
| Dash as "the dodge" | Forward-only, a power, cooldown 14 ticks; lost from Left Alt with Tether | `VelocityPowers.java:45-135`; `ClientCasting.java:51` | Dash can't be the universal dodge as wired; it needs its own input or a body dodge | **Investigate / redesign** |
| "No i-frames" rule | SPEC §13 and the body proposal | `docs/SPEC.md` §13 body; `docs/proposals/awakened-body.md:37` | A dodge with i-frames or a parry window needs a SPEC amendment and Euan's approval | **Investigate (decision)** |
| Hit-stop | Power-only render freeze of 20-80 ms + shake, attacker only | `Fx.java:223-233`, `CombatFeel.java`, `ClientPowers.java:149-155` | Ready to reuse for melee: send `power_feel` from a melee hook, or generalise it into a combat feel payload; add attacker-side hand hold and a time-scale option | **Extend** |
| FxScreen | Power flash, impact frames, FOV punch, kick | `FxScreen.java:48-160` | Usable for parry flashes and impact frames; already gated by power-fx settings | **Extend** |
| FeelDirector | The Ascent's camera voice with comfort | `FeelDirector.java` | The model for a combat camera voice; today no combat path calls it | **Extend into one camera voice** |
| BodyCamera | First-person move beats; the roll dip ignores reduced motion | `BodyCamera.java:41-89` | Fold into the camera voice; fix the comfort gaps | **Extend** |
| ActionCamera | Shoulder-view poses, none for combat | `ActionRig.java:14` | Add a COMBAT or LOCK pose | **Extend** |
| Comfort settings | 4-5 files, 2 reducedMotion switches | §2.4 | Merge into one combat-feel switch set before adding more | **Investigate / consolidate** |
| Sounds | Vanilla melee; ours for momentum, crit glyph, powers | §2.5 | Swing, impact, block, parry and hurt need a synthesised set (`tools/art/specs/audio_*.py`) | **Extend** |
| Damage glyphs | Attacker-only, 700 ms, Force colours, crit gold | `Hits.java:34-52`, `LivingFx.java:54-92`, `LivingClient.java:132-144` | Parry, block, stagger and "guard broken" glyphs fit this path (add a kind field) | **Extend** |
| Plates | Look-picked creature plates on Holo glass | `living/client/LivingPlates.java` | A posture or poise bar would sit here; plates are the natural lock-on readout | **Extend** |
| Hurt and low-HP feedback | Vanilla only | §3 | Must be built from scratch (FxScreen-like layer, sounds) | **Gap** |
| Key map | 15 AWAKEN keys; sprint, sneak, jump and Alt overloaded | §4 | RMB with a weapon and Mouse 4/5 are the free inputs; "no new keys" applies to movement | **Investigate (decision + conflict audit)** |
| Body packet pattern | Start/End/Refuse/Seen + history lag compensation | §5.1 | The template for a predicted dodge or parry; add a server window history for timing | **Keep (template)** |
| Ascent Event pattern | Trusts the client's moment and speed | `AscentServer.java:252-317` | Don't use it for damage or defence | **Avoid** |
| Multiplayer visibility | Moves within 96 blocks, FX within 72; hit-stop and glyphs private | §6 | Parries and blocks need a public "seen" (others must see a parry), probably on `body_seen` or `power_fx` | **Extend** |
| PAL and PlayerClips layers | 1500 casts, 1600 moves; optional mod | `PalCasts.java:43-67`, `core/api/PlayerClips.java` | Weapon swings need a priority decision vs casts and moves; a no-PAL fallback through the ArmPose enum extension | **Extend** |
| Hands plan | One FP owner, layers, FP clips, a shared timing table; attacking left vanilla | `origin/pack/fp-anims:docs/anims/PLAN.md` §3, §5 | Combat FP animation must be planned inside Hands or before P1 lands | **Investigate (coordinate)** |
| TimeSlow | Server slow-mo zones | `powers/TimeSlow.java` | Usable for a "perfect parry" slow-mo, but it is world-visible and per-area | **Keep (reuse with care)** |
| Mob GUARD and REFLECT | Enemy parry ×0.2 + counter | `AwakenMob.java:505-518` | Player parry vs enemy parry interplay; owned by the enemies agent | **Coordinate** |
| GameTest harness | `ServerPlayer.attack` on fake players works | `BodyTests.java:646-667` | Melee rules can be tested headless | **Keep** |

---

## 11. SUSPECTED / to verify against `.ref` before planning code

1. Vanilla `Player.attack` in 26.3: crit rules (falling, not sprinting), crit multiplier 1.5, sprint knockback, sweep
   conditions for `Item.Properties.sword` items, attack sounds, and whether `CriticalHitEvent` and `SweepAttackEvent`
   still fire as in 1.21.x NeoForge.
2. Shield blocking position in `hurtServer` (docs/api says after `LivingIncomingDamageEvent`, before i-frames); the
   `BlocksAttacks` and `Weapon` component semantics in 26.3 (`Weapon(itemDamage, disableBlockingSeconds)`); whether
   there is a data-driven `SwingAnimation`, `AttackRange` or kinetic/piercing (spear) component. AWAKEN code calls
   `swing(hand, SwingAnimation.DEFAULT, …)`, so a `SwingAnimation` type exists. None of these are in `docs/api/*.md`;
   that is a cheat-sheet gap to fill first.
3. Right-click with a sword: confirm no vanilla use action in 26.3.
4. Default keys of vanilla 26.3 and the pack mods (Iris, Simple Voice Chat, Xaero's World Map, Shoulder Surfing, JEI,
   Jade, Distant Horizons) against AWAKEN's 15 (§4.3).
5. NeoForge `addListener` default `receiveCanceled=false` (assumed throughout §1.2).
6. Vanilla base attack speed 4.0, used for the swings-per-second figures.
