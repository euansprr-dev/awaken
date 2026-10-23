# Mobs module (`dev.awaken.mobs`)

Every hostile creature and boss of AWAKEN: 154 creatures, each its own entity type (`awaken:<id>`), spawn egg
(`awaken:<id>_spawn_egg`), loot table (`awaken:entities/<id>`) and pair of generated textures, plus 50 boss part items.

| | count |
|---|---|
| Gate creatures (15 themes, 3 to 4 normal each) | 49 |
| Gate elites / Wardens / Gate Lords | 15 / 15 / 15 |
| Descent fauna (10 strata, 3 each) | 30 |
| Veil and Lucidium fauna | 4 (+2 shared with stratum 8) |
| Stratum guardians | 10 (strata 1 to 10) |
| Sovereigns of the Hollow Court | 8 |
| The Signal's Shadow | 1 |
| Nightmare templates | 7 |

## How to try it in game

- `/awaken admin mob <theme> <role> [level]`: spawns a creature from a roster, e.g.
  `/awaken admin mob awaken:bell_crypt warden 17`. Roles: `normal elite warden boss break nightmare` (and the other
  `MobRole` names). Without a level it uses your rank's content level.
- `/awaken admin boss <id> [level]`: e.g. `/awaken admin boss awaken:bell_warden 5`, `awaken:signals_shadow`,
  `awaken:nightmare/falling`. Tab completion lists every id.
- `/awaken admin bestiary`: counts by tier.
- Spawn eggs for every creature and the boss parts are in the AWAKEN creative tab (eggs use the theme's home rank).
- The Lantern Broker: right-click to trade; sneak and right-click to be offered a duel, accept with **Y** (Signal) or
  `/awaken mobs duel` within 30 s.

## Ids other modules use

Spawning goes through `core.api.MobHooks` (installed by `MobsModule.init`):

- `spawnThemed(level, theme, role, pos, lv, tags, gateId)` with theme ids:
  - gates: `awaken:bell_crypt fungal_hollow ember_mines drowned_archive clockwork_foundry glass_desert hive
    overgrown_temple lantern_market frost_monastery mirror_palace precursor_lab nightmare sky_archipelago abyssal_shrine`
  - Descent: `awaken:stratum_1` to `awaken:stratum_10`
  - `awaken:veil` (Cord-Eaters, Veil Wraiths), `awaken:dream` (Lucidium fauna)
  - moods: `awaken:silence` (Listeners, for the Silence modifier), `awaken:haunted` (Veil creatures, tagged HAUNTED, for
    the Haunted modifier), `awaken:mirror` (your shadow, for the Mirror modifier)
  - nightmares: `awaken:nightmare/<family>`, family in `falling burning drowning undead arthropod explosion generic`;
    always spawned with role NIGHTMARE whatever role is passed
  - anything else falls back to a gate theme picked from the id's hash (logged at debug)
  - roles: NORMAL picks a random normal; ELITE the theme's elite; WARDEN the theme's Warden; BOSS the theme's Lord (or the
    stratum guardian); BREAK a normal with role BREAK; a roster without a designed elite or Warden upgrades a normal
    (bigger, SPEC multipliers, a red bar for Wardens)
- `spawnBoss(level, bossId, pos, lv, gateId)`:
  - Gate Lords: `awaken:bell_warden mother_of_spores ember_tyrant drowned_archivist gearwright_colossus glass_serpent
    hive_regent root_saint lantern_broker abbot_of_frost mirror_knight unit_zero the_sleeper sky_leviathan hollow_herald`
  - stratum guardians: `awaken:guardian_rim gardens choir orchard silent foundry inverted dreaming court` (strata 1 to 9)
    and `awaken:guardian_seed` (stratum 10, the last door)
  - the final boss: `awaken:signals_shadow`
  - Sovereigns: `awaken:sovereign_hunger silence sleep machines mirrors storms roots ash`
  - nightmares: `awaken:nightmare/<family>` (or `awaken:nightmare_<family>`)
  - Wardens by id also work (`awaken:the_sexton` ...); any creature id works; unknown ids fall back to a Gate Lord
- `Mobs` (public API): `spawnScaled(level, type, pos, lv, modifiers)` (SPEC section 12, any entity type), `spawn(...)`,
  `def(id)`, `roster(theme)`, `themeIds()`, `bossIds()`, `installSpiritHooks(...)` for the Veil.

## What every creature does

- **Scaling**: SPEC section 2 numbers with the same role multipliers as `MobHooks.scale` (elite x2.5, Warden x5, boss
  x14 health and x1.6 damage; nightmares x6 / x1.25). Sovereigns get x1.5 and the final boss x2 more health. Applied as
  *permanent* attribute modifiers so a scaled mob keeps its health across reloads (`MobScaling`, tested equal to
  `MobHooks.scale`).
- **Roles** are stored in the core `MobRole` attachment and mirrored to the client for visuals (elites shimmer, Wardens
  and bosses glow brighter and carry a bar).
- **Telegraphs**: every ability winds up first: a sound (light or heavy), the creature's glow layer pulses, it holds
  still for rooted attacks, and the place it will land is drawn in the theme's colour (rings for slams, bursts and rain,
  lines for charges, beams and fangs, a cone for breath, portal sparks where a blink will land). Heavy signatures also
  print their name on the action bar. 25 ability kinds (slam, leap, charge, bolt fan, arrows, nova, burst with a safe
  centre, rain, beam, blink, summon, cloud, cone, pull, gust, heal, root, fang lines and rings, decoys, guard/riposte,
  lift, darkness, aura, frenzy) are combined per creature; damage is the creature's ATTACK_DAMAGE times the ability's
  power so scaling carries through.
- **Five Forces tags** (core `Forces.Tag`, read every second so other modules can tag at any time):
  VOLATILE explodes ten ticks into the death animation (the body flashes; no block damage), ECHO falls, lies still for
  2.5 s, then gets up once at half health (no death event and no loot until the real death), HAUNTED is translucent,
  takes 0.75x damage (1.5x from Spirit) and phases out for 1.5 s every 7 s (immune to non-Spirit damage),
  FROSTED is pale, 20% slower and its hits slow and freeze, CHARGED sparks and its hits add 25% Charge damage and a
  brief stun.
- **Traits**: ranged kiting, neutral (Lantern Market folk), fuse (walk up, swell, burst), Listeners (blind; they hunt
  the last place a sprinting, fighting, jumping, not-sneaking player was heard), Cord-Eaters (hunt spirits through
  walls), watched-freeze (Vine Creakers move only while unwatched), pickpockets (steal essence shards and run; the
  shards drop when caught), splitting gels, death clouds, death shards, dormant machines, reflecting Mirror creatures,
  mimics (wear the nearest player's skin), the Inverted Sea's upside-down drowned.
- **Bosses** (`AwakenBoss`): a `ServerBossEvent` bar (colour shifts each phase), 2 or 3 phases at 66% / 33% (or 50%
  for guardians and nightmares) with a 1.5 s invulnerable roar, a spoken line and a new ability list; arena awareness
  (home = spawn point, radius per boss; walks back, blinks back if kited more than 10 blocks out, ignores targets out
  of the arena, resets to full health after 30 s with nobody near); never despawn; knockback and explosion knockback
  resistance 1.0; one intro line when the fight starts. Monster Hunter style, each phase change knocks one of the
  boss's parts loose (dropped on the spot, once per boss, never again after a reset); the full set drops on the kill.
- **Drops**: loot tables carry themed vanilla drops, boss parts and essence shards through `awaken:mob_level_count`
  (count = floor(base + perLevel x content level + random)). Code adds the Lord Heart (Gate Lords), Power Cores via
  `PowerCoreHooks.roll` (Lord 40%, guardian 50%, Sovereign 75%, final 100%, nightmare 15%, elites 6%, Wardens 15%;
  elites and Wardens x1.5 after 20 minutes without a new thing) with the pity timer: guaranteed when the killer's last
  three bosses gave none (`BossRecord` attachment `awaken:mobs_boss_record`, kept through death).

  | tier | essence base | per level | at E (lv 5) | at S (lv 118) |
  |---|---|---|---|---|
  | normal | 0.35 | 0.035 | 0 to 1 | 4 to 5 |
  | elite | 1.5 | 0.08 | 1 to 2 | 10 to 11 |
  | Warden | 3 | 0.15 | 3 to 4 | 20 to 21 |
  | Gate Lord | 6 | 0.4 | 8 to 9 | 53 to 54 |
  | guardian / Sovereign / final | 7 / 10 / 20 | 0.45 / 0.5 / 0.6 | | |
- **Counters**: on a boss kill by a player, `awaken:bosses_killed` and (empty main hand) `awaken:bosses_killed_unarmed`;
  the first kill of each boss type calls `Progression.markNewThing("trophy:<id>")`; every boss kill calls
  `Progression.bestMoment` ("First kill: The Bell Warden", "Felled ... with bare hands"). A player killed by a boss gains
  Insight for it (`Progression.addInsight`). Decisions are logged to `/awaken why` (DirectorLog system `mobs`).
- **Patent rule**: nothing a player does changes another creature. Nightmares are authored templates chosen by id;
  heals and frenzies are the creatures' own telegraphed abilities.

## Performance

Goals are cheap: melee uses the vanilla goal; ranged creatures repath every 10 ticks; fliers steer straight at a point
(no 3D pathfinding); abilities are considered every 4 ticks with one small entity query when they fire. Listeners and
watched-freeze creatures check players every 5 ticks. Summons respect a budget (at most twice the ability's count alive
per summoner, and no summon if 40 creatures are already within 48 blocks). Ground hazards (spore clouds, ink, root
lances) use one shared list (max 256) pulsing every 10 ticks instead of vanilla area clouds. Rendering reuses one look
object per entity (no per-frame allocation of our own).

## Assets

- Textures are generated by `tools/art/specs/mobs.py` (`python3 tools/art/build_all.py mobs`): for vanilla bodies a
  skin layer (theme colours over the re-tinted vanilla texture, which is referenced, never copied) and an emissive glow
  layer (eyes, runes, cracks...); full textures for our own models (Serpent, Listener, Bell, Lantern, Shade and the
  player-shaped Unit Zero / Signal Echo / Faceless); spawn eggs (crowned for bosses) and part icons.
- JSON (loot tables, item models, lang, sounds.json, and the bestiary below) is generated by
  `python3 tools/content/mobs_content.py`. Both read the creature list straight from the Java bestiary
  (`tools/content/mobs_bestiary.py` parses `GateBestiary.java` / `DeepBestiary.java`).
- Sounds are re-pitched vanilla files (`mobs.telegraph`, `mobs.telegraph_heavy`, `mobs.boss_intro`, `mobs.boss_phase`,
  `mobs.boss_toll`, `mobs.boss_defeated`, `mobs.rise`, `mobs.cord_bite`, `mobs.listener_hear`).

## Decisions

- Wardens also get a (red) bar: "the gate has noticed you" should be visible.
- Summoned minions carry no gate id, so gates don't count them as floor spawns.
- Decoys (Mirage, Mirror Knight...) pop on the first hit without dying, so they never count as kills or drop loot.
- Nightmares do not count as bosses for `bosses_killed` or the pity timer (Lucidium owns `nightmares_beaten`).
- The Cord-Eater's default spirit test is "spectator with the scoreboard tag `awaken.spirit`"; the Veil module should
  call `Mobs.installSpiritHooks` with its real test and cord-bite handling (default: a Signal toast).
- Theme tints are multiplied over vanilla textures, so a vanilla body keeps a little of its own colour under the theme;
  the skin layer and glow layer carry the rest of the look.

## Known gaps

- I can't run the client here, so the renderers, our five models and the generated textures are verified only by
  compiling and by contact sheets (`tools/art/preview.py`), not in game. Breeze, Warden and Creaking bodies use their
  vanilla keyframe animations only for idle (no attack keyframes).
- Gates may also count `awaken:bosses_killed_unarmed` (COUNTERS.md lists gates as its owner); if both count it,
  one side should stop.

## Bestiary

<!-- bestiary:start -->

### Bell Crypt (`awaken:bell_crypt`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:toll_ghoul` | Toll Ghoul | normal | zombie | Rings a hand-bell: a toll that slows everyone around it | Bell Toll |
| `awaken:crypt_archer` | Crypt Archer | normal | skeleton | Mourning Volley: draws a long breath, then three arrows | Mourning Volley |
| `awaken:bell_wisp` | Bell Wisp | normal | vex | Grave Chime: chimes, then dives straight at you | Grave Chime |
| `awaken:crypt_keeper` | Crypt Keeper | elite | wither_skeleton | Bell Slam: lifts its blade over its head and brings the floor down | Bell Slam, Coffin Rush |
| `awaken:the_sexton` | The Sexton | Warden | warden | Hunts by the toll of your footsteps; its Knell tears at the spirit | Knell, Grave Stamp, Last Light |
| `awaken:bell_warden` | The Bell Warden | Gate Lord; parts: `awaken:bell_clapper`, `awaken:crypt_bronze` | bell | Toll Waves you dodge by stepping in; halfway through, the statues move | Toll Wave, Clapper Strike, Echo Bolts, The Statues Move, Bell Rush, Grand Peal, Shattered Bronze |

### Fungal Hollow (`awaken:fungal_hollow`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:sporeling` | Sporeling | normal | slime | Hops at you and pops into a cloud of spores when it dies | Pounce |
| `awaken:mycelid` | Mycelid Shambler | normal | zombie | Spore Cough: hunches, then a poison cone | Spore Cough |
| `awaken:capweaver` | Capweaver | normal | cave_spider | Web Spit: a marked circle that roots you if you stay in it | Web Spit |
| `awaken:puffcap` | Puffcap | normal | creeper | Swells and bursts into choking spores | (passive) |
| `awaken:hollow_tender` | Hollow Tender | elite | witch | Tends the colony: heals every fungus near it, lobs spores | Tend the Colony, Spore Lob, Rot Bloom |
| `awaken:the_rot` | The Rot | Warden | ravager | Rot Charge: lowers its head, then a charge that leaves spores behind | Rot Charge, Spore Burst, Choking Field |
| `awaken:mother_of_spores` | Mother of Spores | Gate Lord; parts: `awaken:spore_sac`, `awaken:mother_cap` | ghast | Spore Rain from above, then she births the colony | Spore Rain, Spore Lob, Birth, Spore Halo, Bloom |

### Ember Mines (`awaken:ember_mines`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:cinder_miner` | Cinder Miner | normal | husk | Ember Swing: an overhead pick strike that sets you alight | Ember Swing |
| `awaken:slag_cube` | Slag Cube | normal | magma | Molten Splash: lands in a ring of fire | Molten Splash |
| `awaken:ember_imp` | Ember Imp | normal | blaze | Cinder Burst: three fire bolts after a crackle | Cinder Burst |
| `awaken:scorch_crawler` | Scorch Crawler | normal | silverfish | Comes out of the coal seams biting, and the bites burn | Scorch Bite |
| `awaken:slag_boar` | Slag Boar | elite | hoglin | Slag Charge: paws the ground, then charges through fire | Slag Charge, Tusk Toss |
| `awaken:the_overseer` | The Overseer | Warden | wither_skeleton | Walks through fire to reach you, ringed in embers | Furnace Walk, Ember Ring, Heat Haze |
| `awaken:ember_tyrant` | The Ember Tyrant | Gate Lord; parts: `awaken:tyrant_crown`, `awaken:molten_core` | piglin_brute | Magma Fist and Eruption, then a Meltdown you have to step out of | Magma Fist, Tyrant's Advance, Eruption, Stoke the Imps, Meltdown |

### Drowned Archive (`awaken:drowned_archive`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:ink_drowned` | Ink Drowned | normal | drowned | Ink Spit: a black bolt that blinds | Ink Spit |
| `awaken:page_wraith` | Page Wraith | normal | vex | A swarm of wet pages that rustle, then cut | Papercut |
| `awaken:archive_eye` | Archive Eye | normal | guardian | Reading Beam: locks on, glows, then burns | Reading Beam |
| `awaken:salt_stray` | Salt Stray | normal | stray | Brine Arrows that weigh you down | Brine Arrows |
| `awaken:drowned_scribe` | Drowned Scribe | elite | evoker | Writes wraiths from torn pages; ink fangs rise along the floor | Torn Pages, Ink Fangs, Ink Spit |
| `awaken:the_curator` | The Curator | Warden | elder_guardian | Shushes you with Mining Fatigue, then its beam reads you to death | Silence in the Stacks, Shush, Ink Tide |
| `awaken:drowned_archivist` | The Drowned Archivist | Gate Lord; parts: `awaken:archivist_quill`, `awaken:waterlogged_codex` | drowned | Tidal Page washes you back; the shelves empty; then the Flood | Tidal Page, Ink Volley, Loose Pages, Ink Cloud, Flood, Undertow |

### Clockwork Foundry (`awaken:clockwork_foundry`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:cog_hound` | Cog Hound | normal | wolf | Ratchet Pounce: winds itself up with a clatter, then leaps | Ratchet Pounce |
| `awaken:rivet_gunner` | Rivet Gunner | normal | pillager | Rivet Burst: three hot rivets from a crossbow | Rivet Burst |
| `awaken:spark_drone` | Spark Drone | normal | bee | Spark Sting: buzzes louder, then a sting that stuns | Spark Sting |
| `awaken:foundry_marshal` | Foundry Marshal | elite | vindicator | Steam Charge, then a Piston Slam where it stops | Steam Charge, Piston Slam |
| `awaken:the_regulator` | The Regulator | Warden | iron_golem | Overclocks as it nears you; Governor Slam and a spark ring | Governor Slam, Spark Ring, Overclock |
| `awaken:gearwright_colossus` | The Gearwright Colossus | Gate Lord; parts: `awaken:colossus_gear`, `awaken:mainspring` | iron_golem | Piston Slams and spark halos; at the end an Overload arc | Piston Slam, Spark Halo, Release Drones, Colossal Stride, Overload Arc, Arc Strikes |

### Glass Desert (`awaken:glass_desert`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:glass_husk` | Glass Husk | normal | husk | Sandblast blinds; shatters into shards when slain | Sandblast |
| `awaken:sand_stalker` | Sand Stalker | normal | spider | Burrows and bursts up beside you | Burrow Strike |
| `awaken:heat_shimmer` | Heat Shimmer | normal | breeze | Mirage Gust: a wall of hot air, then glass on the wind | Mirage Gust, Glass Gust |
| `awaken:prism_sentinel` | Prism Sentinel | elite | enderman | Refracts: teleports beside you, then lances light | Refract, Prism Lance |
| `awaken:the_mirage` | The Mirage | Warden | illusioner | Splits into mirages; only one of them casts a shadow | Mirages, Sunshards, Heat Mirage |
| `awaken:glass_serpent` | The Glass Serpent | Gate Lord; parts: `awaken:serpent_scale`, `awaken:sand_glass_fang` | serpent | Lunges, dives into the sand and erupts beneath you, then the Glass Storm | Glass Lunge, Shard Spray, Burrow Strike, Erupting Glass, Glass Storm |

### Hive (`awaken:hive`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:drone_bee` | Hive Drone | normal | bee | Stinger Dive: hovers, buzzes, dives, poisons | Stinger Dive |
| `awaken:hive_larva` | Hive Larva | normal | silverfish | Wriggles out of the comb in numbers | Wriggle |
| `awaken:honey_slime` | Honey Slime | normal | slime | Sticky: every touch slows you | Sticky Landing |
| `awaken:hive_guard` | Hive Guard | elite | spider | Mandible Crush, and a wax seal that roots you | Mandible Crush, Wax Seal |
| `awaken:the_swarm_mind` | The Swarm Mind | Warden | phantom | Never alone: drones pour from it while honey falls | Call the Swarm, Swarm Dive, Honey Drop |
| `awaken:hive_regent` | The Hive Regent | Gate Lord; parts: `awaken:regent_stinger`, `awaken:royal_jelly` | bee | Royal Dives and Honey Rain; at the end she feeds on royal jelly and frenzies | Royal Dive, Brood, Honey Rain, Stinger Volley, Royal Jelly, Frenzy |

### Overgrown Temple (`awaken:overgrown_temple`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:vine_creaker` | Vine Creaker | normal | creaking | Only moves while you aren't looking at it | Root Grasp |
| `awaken:thorn_archer` | Thorn Archer | normal | bogged | Thorn Arrows that poison | Thorn Arrows |
| `awaken:thorn_hound` | Thorn Hound | normal | wolf | Bramble Pounce out of the undergrowth | Bramble Pounce |
| `awaken:incense_priest` | Incense Priest | elite | evoker | Root Fangs along the floor; incense that heals its flock | Root Fangs, Incense, Incense Smoke |
| `awaken:the_overgrowth` | The Overgrowth | Warden | iron_golem | A temple statue grown through with roots; rings of fangs | Temple Fist, Root Ring, Strangling Vines |
| `awaken:root_saint` | The Root Saint | Gate Lord; parts: `awaken:saint_heartwood`, `awaken:temple_resin` | creaking | Root Lances in lines, then the whole temple grabs for you | Root Lance, Saint's Palm, Grow Creakers, Root Grasp, Root Ring, Thorn Halo |

### Lantern Night Market (`awaken:lantern_market`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:lantern_moth` | Lantern Moth | normal | phantom | Drawn to light; harmless until something hits it | Drawn to Light |
| `awaken:pickpocket` | Market Pickpocket | normal | pillager | Snatches a shard and runs; catch it to get it back | (passive) |
| `awaken:wick_slime` | Wick Slime | normal | slime | A slime with a candle in it; burns if provoked | Wick Hop |
| `awaken:masked_duelist` | Masked Duelist | elite | piglin | Parries your swing, then a Fan Dance lunge | Parry, Fan Dance |
| `awaken:the_lamplighter` | The Lamplighter | Warden | enderman | Steps between lamps and snuffs them out | Between Lamps, Snuff the Lamps, Lamp Oil |
| `awaken:lantern_broker` | The Lantern Broker | Gate Lord; parts: `awaken:broker_lantern`, `awaken:gilded_ledger` | lantern | Trades with you; duels you only if you ask (sneak and use) | Lantern Bolts, Market Step, Release the Moths, Paper Lanterns, Lights Out |

### Frost Monastery (`awaken:frost_monastery`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:rime_acolyte` | Rime Acolyte | normal | pillager | Frost Palm: a cold shove that slows | Frost Palm |
| `awaken:hail_stray` | Hail Stray | normal | stray | Hail Volley: three chilling arrows | Hail Volley |
| `awaken:frost_wisp` | Frost Wisp | normal | breeze | Blizzard Bolts on the wind | Blizzard Bolt, Gale |
| `awaken:ice_hound` | Ice Hound | normal | snow_wolf | Ice Pounce; its bite slows | Ice Pounce |
| `awaken:chant_master` | Chant Master | elite | evoker | Chants up a wisp; ice spikes under your feet | Chant, Frost Nova, Ice Spikes |
| `awaken:the_yeti` | The Yeti | Warden | polar_bear | Avalanche Slam and freezing breath | Avalanche Slam, Snow Charge, Frost Breath |
| `awaken:abbot_of_frost` | The Abbot of Frost | Gate Lord; parts: `awaken:abbot_prayer_beads`, `awaken:glacier_heart` | evoker | Frost Hymns and Ice Spikes; the choir joins; then the Glacial Vow | Frost Hymn, Ice Spikes, The Choir, Blizzard, Glacial Vow, Frost Nova |

### Mirror Palace (`awaken:mirror_palace`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:mirror_shade` | Mirror Shade | normal | mimic | Wears your face and steps out behind you | Behind You |
| `awaken:silver_knight` | Silver Knight | normal | zombie | Parries: raises its blade, then punishes the swing | Parry |
| `awaken:glint_mite` | Glint Mite | normal | endermite | Jumps from reflection to reflection | Glint |
| `awaken:glass_duelist` | Glass Duelist | elite | skeleton | Ripostes your blows and sends your arrows back | Riposte, Mirror Lunge |
| `awaken:the_unblinking` | The Unblinking | Warden | enderman | Never blinks: teleports when you look away, gaze like a blade | Never Blinks, Silver Gaze, Reflections |
| `awaken:mirror_knight` | The Mirror Knight | Gate Lord; parts: `awaken:mirror_shard_crown`, `awaken:knight_reflection` | mimic | Wears your face, parries perfectly, splits into reflections | Perfect Parry, Mirror Lunge, Mirror Split, Through the Glass, Shatter |

### Precursor Lab (`awaken:precursor_lab`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:test_subject` | Test Subject | normal | zombie | Overcharged: bursts into sparks when killed | Lurch |
| `awaken:security_unit` | Security Unit | normal | iron_golem | Dormant until you come close; then a Scan Lance | Scan Lance |
| `awaken:coil_unit` | Coil Unit | normal | blaze | Coil Bolts, and a discharge if you crowd it | Coil Bolt, Discharge |
| `awaken:gel_sample` | Gel Sample | elite | slime | Containment Breach: splits in two when killed | Containment Breach |
| `awaken:the_sweeper` | The Sweeper | Warden | ravager | Purge Protocol: a charge, a sweep, a decontamination blast | Purge Protocol, Sweep, Decon Blast |
| `awaken:unit_zero` | Unit Zero | Gate Lord; parts: `awaken:zero_core`, `awaken:precursor_plating` | human | Counts down in no language: lances, orbital marks, Zero Point | Zero Lance, Phase Step, Deploy Coils, Orbital Mark, Zero Point |

### Nightmare (`awaken:nightmare`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:sleepwalker` | Sleepwalker | normal | zombie | Moves only when you blink: steps from nowhere | Sleepstep |
| `awaken:night_terror` | Night Terror | normal | phantom | Night Dive from somewhere above | Night Dive |
| `awaken:many_eyes` | Many Eyes | normal | spider | Staring: every eye opens and you slow | Staring |
| `awaken:the_tall_one` | The Tall One | elite | enderman | Doorways: it is always in the one behind you | Doorway, Lights Off |
| `awaken:the_waking` | The Waking | Warden | warden | A wake-up call that tears at the spirit | Wake-Up Call, Thrash, Bad Dreams |
| `awaken:the_sleeper` | The Sleeper | Gate Lord; parts: `awaken:sleeper_eyelid`, `awaken:sleepers_lullaby` | zombie | Sleepwalks between dream and waking; when it wakes, everything does | Restless Fist, Heavy Eyelids, Night Terrors, Dreamstep, Rude Awakening, Sleepwalkers |

### Sky Archipelago (`awaken:sky_archipelago`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:gale_harpy` | Gale Harpy | normal | phantom | Dive Bomb from the clouds | Dive Bomb |
| `awaken:cloud_breeze` | Cloud Breeze | normal | breeze | Updraft: blows you towards the edge | Updraft, Wind Bolt |
| `awaken:sky_raider` | Sky Raider | normal | pillager | Grav-Bolts that lift you off your feet | Grav-Bolt |
| `awaken:thunderhead` | Thunderhead | elite | ghast | Calls lightning onto marked circles | Lightning Call, Static |
| `awaken:the_gale` | The Gale | Warden | breeze | Gale Force and updraft traps over the void | Gale Force, Updraft Trap, Wind Spears |
| `awaken:sky_leviathan` | The Sky Leviathan | Gate Lord; parts: `awaken:leviathan_fin`, `awaken:sky_pearl` | serpent | Dives through the sky, tilts gravity, then the Storm Eye | Leviathan Dive, Wingbeat, Gravity Tide, Pearl Barrage, Storm Eye |

### Abyssal Shrine (`awaken:abyssal_shrine`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:abyss_watcher` | Abyss Watcher | normal | guardian | Hollow Gaze: a beam that drains the spirit | Hollow Gaze |
| `awaken:silent_stalker` | Silent Stalker | normal | enderman | Makes no sound at all; steps out beside you | Soundless Step |
| `awaken:void_spawn` | Void Spawn | normal | endermite | Pours out of the dark and bites | Void Bite |
| `awaken:hollow_priest` | Hollow Priest | elite | wither_skeleton | Draws you in, then Hollow Bolts that wither | Hollow Bolt, Draw In |
| `awaken:the_nothing` | The Nothing | Warden | wither | Three heads of nothing; an event horizon that pulls | Nothing Skulls, Event Horizon, Unlight |
| `awaken:hollow_herald` | The Hollow Herald | Gate Lord; parts: `awaken:herald_halo`, `awaken:abyssal_glass` | shade | Void bolts, an unlit shrine, then the Event Horizon | Void Bolts, Draw Near, Spawn of Nothing, Unlit, Event Horizon, Collapse, Void Halo |

### The Rim (`awaken:stratum_1`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:rim_crawler` | Rim Crawler | normal | cave_spider | Glows in the moss; pounces from the roots | Moss Pounce |
| `awaken:moss_shambler` | Moss Shambler | normal | zombie | Moss Grip: a slow grab that roots you | Moss Grip |
| `awaken:glowcap` | Glowcap | normal | slime | A lantern of moss that makes you glow for everything else | Glow Hop |
| `awaken:guardian_rim` | The Rootmother | stratum guardian; parts: `awaken:rootmother_silk` | spider | Moss webs and root lances; the Rim wakes with her | Moss Web, Rootfall, Brood, Root Lances |

### The Hanging Gardens (`awaken:stratum_2`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:vine_lurker` | Vine Lurker | normal | spider | Drops from the vines above you | Drop From Above |
| `awaken:pollen_drifter` | Pollen Drifter | normal | breeze | Pollen Gust: shoves you off the ledge | Pollen Gust, Thorn Seed |
| `awaken:bloom_creeper` | Bloom Creeper | normal | creeper | Blooms: swells with petals and bursts | (passive) |
| `awaken:guardian_gardens` | The Thornback | stratum guardian; parts: `awaken:thornback_horn` | ravager | Charges along the ledges and blows petals hard enough to knock you off | Thornback Charge, Gale of Petals, Thorn Spray, Lurkers |

### The Drowned Choir (`awaken:stratum_3`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:choir_drowned` | Choir Drowned | normal | drowned | Sings: every drowned near it hits harder and heals | Choir Song, Salt Note |
| `awaken:deep_eel` | Deep Eel | normal | serpent | Strikes out of the flooded dark | Eel Strike |
| `awaken:bell_jelly` | Bell Jelly | normal | slime | A translucent jelly whose sting numbs | Numbing Sting |
| `awaken:guardian_choir` | The Choirmaster | stratum guardian; parts: `awaken:choirmaster_pearl` | witch | Hymns of salt; when the water rises, the choir sings with her | Hymn of Salt, Refrain, Rising Tide, The Choir, Undertow |

### The Bone Orchard (`awaken:stratum_4`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:orchard_dead` | Orchard Dead | normal | skeleton | Gets up once after you kill it | Marrow Shot |
| `awaken:rib_hound` | Rib Hound | normal | wolf | A skeletal hound that gets up once | Rib Lunge |
| `awaken:marrow_fiend` | Marrow Fiend | normal | husk | Marrow Crush withers; gets up once | Marrow Crush |
| `awaken:guardian_orchard` | The Ossuary King | stratum guardian; parts: `awaken:ossuary_crown` | wither_skeleton | Rib Spikes and a court of the dead; he gets up once | Bone Crush, Rib Spikes, Rise, Royal March |

### The Silent Halls (`awaken:stratum_5`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:listener` | Listener | normal | listener | Blind; hunts the last sound you made. Sneak, and it loses you | Found You |
| `awaken:hush_moth` | Hush Moth | normal | phantom | Eats the light around you | Eats the Light |
| `awaken:still_one` | Still One | normal | creaking | Only moves while nobody is looking | Unseen Blow |
| `awaken:guardian_silent` | The Great Listener | stratum guardian; parts: `awaken:listener_ear` | listener | It hears your heartbeat; scream, and it screams back | Found You, Echo Scream, Absolute Silence, Ears |

### The Precursor Foundry (`awaken:stratum_6`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:foundry_sentinel` | Foundry Sentinel | normal | iron_golem | Dormant machine that wakes when you come close | Wake Slam, Weld Beam |
| `awaken:arc_drone` | Arc Drone | normal | bee | Arcs: a crackle, then a bolt | Arc |
| `awaken:servitor` | Servitor | normal | vindicator | Servo Rush with a precursor axe | Servo Rush |
| `awaken:guardian_foundry` | The Furnace Engine | stratum guardian; parts: `awaken:furnace_engine_core` | iron_golem | Arc welds and rivet rain; the machines wake around it | Engine Slam, Arc Weld, Wake the Drones, Rivet Rain |

### The Inverted Sea (`awaken:stratum_7`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:tide_eel` | Tide Eel | normal | serpent | Swims through the air when the tide flips | Tide Strike |
| `awaken:drift_jelly` | Drift Jelly | normal | ghast | Drifts above you, raining salt | Salt Rain |
| `awaken:upturned_drowned` | Upturned Drowned | normal | drowned | Walks the wrong way up; spits the undertow | Undertow Spit |
| `awaken:guardian_inverted` | The Drowned Moon | stratum guardian; parts: `awaken:moon_scale` | elder_guardian | Moonbeams and the tide-turn that lifts you off the floor | Moonbeam, Tide Turn, Undertow, Tide Halo |

### The Dreaming Stratum (`awaken:stratum_8`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:dream_wisp` | Dream Wisp | normal | vex | Flickers in and out, then dives | Flicker |
| `awaken:shifting_one` | Shifting One | normal | enderman | Rearranges itself around you | Rearrange |
| `awaken:lullaby_phantom` | Lullaby Phantom | normal | phantom | Its lullaby makes you heavy | Lullaby, Swoop |
| `awaken:guardian_dreaming` | The Architect of Rooms | stratum guardian; parts: `awaken:architect_key` | evoker | The floor shifts, doors open behind you, the ceiling falls | Shifting Floor, Door Step, Other Rooms, Falling Ceiling |

### The Hollow Court (`awaken:stratum_9`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:court_shadow` | Your Shadow | normal | mimic | Your own shadow: your face, your blade | Shadowstep, Your Charge |
| `awaken:hollow_courtier` | Hollow Courtier | normal | evoker | Court etiquette: fangs for anyone who approaches | Court Etiquette, Hollow Favour |
| `awaken:court_hound` | Court Hound | normal | zoglin | A gilded charge through the court | Gilded Charge |
| `awaken:guardian_court` | The Shadow Regent | stratum guardian; parts: `awaken:regent_shadow` | mimic | Your shadow on a throne: your parry, your charge, your moves | Your Parry, Your Charge, Your Reflections, Shadowstep, Hollow Halo |
| `awaken:sovereign_hunger` | Sovereign of Hunger | Sovereign; parts: `awaken:hunger_fang` | ravager | Hunger gone wrong: devours, gorges, and only gets hungrier | Devour, Gnawing Pull, Hounds of the Court, Gorge, Starving Frenzy, Famine |
| `awaken:sovereign_silence` | Sovereign of Silence | Sovereign; parts: `awaken:silence_ear` | listener | The Veil gone wrong: hunts every sound, swallows every light | Found You, No Sound At All, Inverted Scream, Ears, Deafening Silence, Hush |
| `awaken:sovereign_sleep` | Sovereign of Sleep | Sovereign; parts: `awaken:sleep_feather` | phantom | The Lucidium gone wrong: lullabies that make you heavy, dives from the dark | Night Dive, Lullaby, Lullabies, Dream Fall, Never Wake |
| `awaken:sovereign_machines` | Sovereign of Machines | Sovereign; parts: `awaken:machine_crown` | iron_golem | The Above gone wrong: every move calculated, every strike from orbit | Calculated Lance, Hydraulic Slam, Drones, Orbital Strike, Shutdown, Spark Lattice |
| `awaken:sovereign_mirrors` | Sovereign of Mirrors | Sovereign; parts: `awaken:mirror_heart` | mimic | The self gone wrong: your face, perfect parries, a hall of reflections | Flawless Parry, Mirrored Lunge, Hall of Mirrors, Through the Glass, Shatter |
| `awaken:sovereign_storms` | Sovereign of Storms | Sovereign; parts: `awaken:storm_core` | breeze | Speed and power gone wrong: lightning on every circle, thunder that throws you | Lightning, Thunderclap, Storm Spears, Updraft, Overdrive |
| `awaken:sovereign_roots` | Sovereign of Roots | Sovereign; parts: `awaken:root_heart` | creaking | Home gone wrong: it holds you fast and never lets you leave | Root Lance, Hold Fast, Rooted Ones, Hearth Ring, Thorn Halo |
| `awaken:sovereign_ash` | Sovereign of Ash | Sovereign; parts: `awaken:ash_heart` | blaze | Burnout gone wrong: cinders, ashfall, and a burnout you have to outrun | Cinders, Ash Cloud, Ashfall, Embers, Burnout, Last Embers |

### The Seed (`awaken:stratum_10`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:seed_mote` | Seed Mote | normal | vex | A spark of the Signal that flares when it dives | Mote Flare |
| `awaken:signal_echo` | Signal Echo | normal | human | An echo of an instruction: lances you, then is somewhere else | Echoed Instruction, Elsewhere |
| `awaken:seed_root` | Seed Root | normal | creaking | The Seed's roots: fangs in lines of light | Seed Roots |
| `awaken:guardian_seed` | The Seed Warden | stratum guardian; parts: `awaken:seed_shard` | shade | Lances of signal light around the Seed's last door | Signal Lance, Signal Marks, Motes, Door Light |
| `awaken:signals_shadow` | The Signal's Shadow | final boss; parts: `awaken:signal_fragment`, `awaken:shadow_of_you` | shade | It has your voice: every gate, every lance, then the Seed collapses | Instruction, Signal Step, Directive, Echoes, Signal Marks, Your Echoes, Seed Collapse, Come Down, Signal Halo, Last Instruction |

### The Veil (`awaken:veil`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:cord_eater` | Cord-Eater | normal | serpent | Hunts spirits through walls and bites the cord that ties you to your body | Cord Bite |
| `awaken:veil_wraith` | Veil Wraith | normal | phantom | A Veil creature: half there, diving through the fog | Wraith Dive |

### The Lucidium (`awaken:dream`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:dreamling` | Dreamling | normal | slime | Bounces like a thought you can't hold | Bounce |
| `awaken:memory_mimic` | Memory Mimic | normal | mimic | Remembers you: your face, a little wrong | Remembered You |

### Nightmares (`awaken:nightmare/<family>`)

| id | name | role | model | signature | abilities |
|---|---|---|---|---|---|
| `awaken:nightmare_falling` | The Long Drop | nightmare | phantom | Lifts you up and lets you fall | Lift, Fall With Me, No Bottom |
| `awaken:nightmare_burning` | The Pyre | nightmare | blaze | Fire that follows you | Cinders, Pyre, Firefall, Flare |
| `awaken:nightmare_drowning` | The Undertow | nightmare | drowned | Pulls you under and holds you there | Undertow, Rush of Water, Lungful, Black Water |
| `awaken:nightmare_undead` | The Hollow Crowd | nightmare | zombie | More of them every time you look; it gets up once | Crush, More of Them, Surge |
| `awaken:nightmare_arthropod` | The Thousand Legs | nightmare | spider | Webs, and the sound of a thousand legs | Web, Drop, Hatchlings |
| `awaken:nightmare_explosion` | The Fuse | nightmare | creeper | A hiss you can't find, and lines of fire to dodge | Fuse Lines, Blast Ring |
| `awaken:nightmare_generic` | The Faceless | nightmare | human | No face: always in the corner of your eye | Corner of Your Eye, Lights Off, Everywhere, Stare |

<!-- bestiary:end -->
