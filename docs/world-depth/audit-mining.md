# AWAKEN mining audit: ores, ore generation, mining XP, tools, the gear ladder, the economy

Read-only audit of `/Volumes/MEDIA/Awaken/.claude/worktrees/world-depth-e0a13d` (branch `claude/world-depth-e0a13d`,
HEAD c7846bc2). Paths below are relative to `src/main/java/dev/awaken/` unless they start with `src/`, `docs/` or
`/Volumes`. Vanilla numbers come from `/Volumes/MEDIA/Awaken/.ref/src` (shortened to `.ref/src/...`). The code is the
truth; where SPEC or a plan says otherwise, the code is quoted and the drift is noted.

The owner's question: "why would I go mining? What's the point of finding diamonds?" The short answer from the code:
AWAKEN asks for ores in a handful of one-off home upgrades and as cheaper alternatives in gear tags, pays the same flat
1% of a level for coal as for diamond, hands out the same ores from gates, chests, a Mine Echo and an essence market,
and gives mining no feel of its own. The Hunter gear plan removed iron as a wall on purpose
(`docs/modules/gear.md:3-5`: "the start is no longer 'go find iron'").

---

## (a) Every vanilla ore and mineral, and every place AWAKEN uses it

Legend: **needed** = a core AWAKEN step has no way round it; **nice** = one of several options, or an optional extra;
**unused** = nothing in AWAKEN asks for it (vanilla uses only). Counts are per craft or per upgrade at full price
(Hearth prices fall ×0.85 a rank above the upgrade's tier, `hearth/Costs.java:19, 28-36`).

| Ore / mineral | What AWAKEN uses it for (count) | Where | Verdict |
|---|---|---|---|
| **Coal** (ore, coal, coal block) | Forge Tier II: 4 coal blocks (36 coal) · Oren's First Steel quest: 8 coal · Anchor request (E 12, D 16; value 0.2) · Mine Echo yields it | `hearth/Upgrades.java:62`; `src/main/resources/data/awaken/awaken/quest/orens_first_steel.json:31`; `src/main/resources/data/awaken/awaken/request/coal.json`; `hearth/echo/Duty.java:197` | **nice** (one D upgrade, one quest; vanilla torches/smelting) |
| **Copper** (raw, ingot) | **Hearth Anchor: 4 ingots** (`hearth_anchor.json`, level-road step at 6) · Precursor Circuit: 1 per 2 circuits (with 2 redstone, 2 scrap) · Precursor Scanner: 2 · Anchor Tier IV: 32 · Signal Relay: 4 + a lightning rod · the **Charge** catalyst for Arms attunement: 1 · `#awaken:hunter_bind_d` option (Beastbound) · Oren's quest: 6 raw · request (E 12, D 16; 0.3) | `src/main/resources/data/awaken/recipe/hearth_anchor.json`, `precursor_circuit.json`, `precursor_scanner.json`; `hearth/Upgrades.java:54`; `above/MachineType.java:42-44`; `arms/ArmsForge.java:50-52`; `src/main/merge/gear/data/awaken/tags/item/hunter_bind_d.json` | **needed** (the Anchor, and every circuit) |
| **Iron** (raw, ingot, nugget, block) | Forge block: 2 ingots + a blast furnace (5 more) · Fabricator: 5 · Anchor Tier II: 16 · Forge Tier II: 4 iron blocks (36) · **every temper level: 3 × level** · each Blueprint: 2 · Field Forge mend: 1 a use · Magnet module: 4 · Deep Lift: 6 iron chain · role pads: an iron pickaxe (Mine) / iron sword (Guard) · bridle and whistle: 1 nugget · `#hunter_bind_d` option · quests: Moth Lights 3 raw, Oren 6 raw; bounty "Iron for the Hinges" 16 ingots · request (E 8, D 12, C 16; 0.5) | `recipe/forge.json`, `fabricator.json`, `role_pad_mine.json`, `role_pad_guard.json`; `hearth/Upgrades.java:52, 62, 69`; `arms/ArmsForge.java:54, 262-264`; `living/craft/CraftEvents.java:175-185`; `above/frame/FrameModule.java:25-27`; `above/MachineType.java:30-32`; `src/main/resources/data/awaken/awaken/bounty/iron_for_the_hinges.json` | **needed** (Forge, Fabricator, tempering) but never a wall, and the commonest gate loot |
| **Gold** (raw, ingot, nugget, block) | Anchor Tier III: 12 · Forge Tier III: 2 gold blocks (18) · **temper III-VI: 2 × (level − 2)** · Dream Bell: 5 · Precursor Trim: 1 (→3) · Trophy Mount: 1 (→2) · Night Vision module: a golden carrot · `#hunter_bind_d` and `#hunter_bind_c` option · gate market: 4 ingots → 1 essence shard · request (D 6, C 10; 0.9) | `hearth/Upgrades.java:53, 63, 70`; `recipe/dream_bell.json`, `precursor_trim.json`, `trophy_mount.json`; `src/main/merge/gear/data/awaken/tags/item/hunter_bind_c.json`; `gates/run/Rooms.java:557` | **needed** for tempering past II and Anchor III; otherwise nice |
| **Redstone** | Precursor Circuit: 2 per 2 circuits (the Above tree runs on circuits: 38 across the 12 machines, 9 for a Frame, 2-3 a module, 1 a cell) · Scanner: 1 · Start Gate: 1 · Magnet module: 2 · request (D 16, C 24; 0.15) · Mine Echo | `recipe/precursor_circuit.json`; `above/MachineType.java:18-53`; `above/frame/FrameForge.java:35-42`; `recipe/start_gate.json`; `hearth/echo/Duty.java:200` | **needed** (circuits), but broken machines also drop circuits (`src/main/resources/data/awaken/loot_table/blocks/broken_machine.json`) |
| **Lapis** | Anchor request only (D 12, C 16; 0.25) · Mine Echo yields it · gate loot (4-10 from D) | `src/main/resources/data/awaken/awaken/request/lapis.json`; `hearth/echo/Duty.java:201`; `gates/run/Loot.java:25` | **unused** by any AWAKEN recipe or upgrade (vanilla enchanting only) |
| **Emerald** | `#hunter_bind_c` option (Gatewrought) · gate market: 2 → 1 essence shard · request (C 4, B 6; 2.5) · paid out by the Caravan (4) and gate loot | `hunter_bind_c.json`; `gates/run/Rooms.java:551`; `living/pulse/People.java:73`; `gates/run/Loot.java:24` | **unused** in practice (an option, and a sink into essence); no villager or city economy (f) |
| **Diamond** | Anchor IV: 6 · Anchor V: 12 · Forge III: 3 · **temper IV-VI: level − 3** (6 per item from 0 to VI) · Recalibrator machine: 2 · `#hunter_bind_a` option (Lordscale; alternatives wind charge, Precursor circuit) · request (C 4, B 6, A 8, S 8; 3.0) | `hearth/Upgrades.java:54-55, 63, 71`; `above/MachineType.java:45-47`; `src/main/merge/gear/data/awaken/tags/item/hunter_bind_a.json` | **nice**: about 29 diamonds buy every one-off use (Anchor IV + V, Forge III, Recalibrator); tempering a five-piece kit to VI is the only big sink (30) |
| **Nether quartz** | `#hunter_core_e` option (Wildhide; bone and flint also work) · Stillpoint Shrine: 3 smooth quartz (12 quartz) · request (E 6, D 8; 0.7; unlocks on *seeing a Glass Scarab*) | `src/main/merge/gear/data/awaken/tags/item/hunter_core_e.json`; `recipe/stillpoint_shrine.json`; `request/quartz.json` | **nice**; the overworld drops it (Glass Scarab, Sand Stalker, Glass Husk and a dozen gate mobs: `src/main/resources/data/awaken/loot_table/entities/*.json`), so the ore is never needed |
| **Amethyst** (shard, block; geodes, not an ore tag) | **Reassessment Monolith: 1** (rank-ups; a Hearth Spire or a found monolith also work) · Anchor III: 12 · Anchor V: 8 blocks (32) · Stillpoint Shrine: 2 · Meditation Mat: 1 · Dream Journal: 1 · Dream Bell: 1 · Scanner: 1 · Gate Scanner: 2 · Dream Recorder: 4 · Shield Emitter: 2 · `#hunter_bind_c` option · request (D 8, C 12; 0.4) | `recipe/reassessment_monolith.json`, `stillpoint_shrine.json`, `meditation_mat.json`, `dream_journal.json`, `dream_bell.json`; `hearth/Upgrades.java:53, 55`; `above/MachineType.java:21-23, 39-41`; `above/frame/FrameModule.java:19-21` | **needed** (the most-used mineral in AWAKEN's recipes) |
| **Netherite** (ancient debris, scrap, ingot) | **`#hunter_hide_s` is only netherite ingot or scrap: a Sovereign Weave set takes 9** · `#hunter_hide_a` option (Lordscale; alternatives Precursor alloy, shulker shell) · request (B 2, A 4, S 4; 6.0) · gate market sells scrap for 6 essence · gate loot from A | `src/main/merge/gear/data/awaken/tags/item/hunter_hide_s.json`, `hunter_hide_a.json`; `gates/run/Rooms.java:561`; `gates/run/Loot.java:26` | **needed only at S** (level 97, ~82 h), and buyable there with essence |
| Raw-ore blocks, ore blocks, copper/lapis/redstone/emerald/diamond blocks | Iron, coal and gold blocks appear only as compact prices (Forge II/III); amethyst blocks in Anchor V; diamond, emerald, gold, iron and quartz blocks only in the anomaly-avoid tag | `hearth/Upgrades.java:55, 62-63`; `src/main/resources/data/awaken/tags/block/anomaly_avoid.json:67-71` | **unused** |
| Related minables: obsidian, glowstone, echo shard | Obsidian: Anchor V 32, request (C 8, B 12); glowstone: Checkpoint Ring, glow panel, warning light, Night Vision module, request; echo shard: `#hunter_bind_b/s`, request | `hearth/Upgrades.java:55`; `recipe/checkpoint_ring.json`, `precursor_glow_panel.json`; `hunter_bind_s.json` | obsidian nice; glowstone nice; echo shard **needed** at B/S as a bind option |

**Where ores arrive without a pickaxe** (why digging is optional):

| Source | What | Code |
|---|---|---|
| Gate loot (every cache and floor reward) | iron 2-6 (w12) and gold 1-4 (w9) from E; emerald 1-4 and lapis 4-10 from D; **diamond 1-2 from C, 2-4 from A**; netherite scrap from A; a theme item 1 roll in 6 (Ember Mines coal, Foundry copper, Glass Desert amethyst, Night Market emerald, Mirror Palace quartz, Precursor Lab redstone) | `gates/run/Loot.java:21-26, 28-44, 48-59` |
| Gate market (a friendly gate's floor-1 market room and its host; `gates/world/FloorBuilder.java:690-700`) | 2 emerald → 1 essence; 4 gold → 1 essence; **5 essence → 1 diamond**; 6 essence → 1 netherite scrap; 12 essence → a Lord's Heart | `gates/run/Rooms.java:542-563` |
| Mine Echo (a Hearth role pad, crafted with an iron pickaxe) | one roll every 120 s per pad: coal 3+tier, raw iron 2+tier/2, raw copper 3+tier, redstone 4+tier, lapis 3+tier, raw gold 1+tier/2, **a diamond (5 + Echo level + 3 × Anchor tier)/1000, up to 6%** | `hearth/echo/Duty.java:50, 173-203` |
| Chests | Precursor vault/spawn vault/monolith/ocean/sky, halo cache, landmark outer: diamond weight 2-4, copper and redstone 10, gold 10 (monolith), iron 20 (landmark) | `src/main/resources/data/awaken/loot_table/chests/*.json` |
| Anomalies | Fallen star: raw iron w20, raw gold 12, amethyst 12, raw copper 10, emerald 4, diamond 3 (of 90); spirit cache: amethyst, iron ingot, lapis, emerald | `src/main/resources/data/awaken/loot_table/blocks/anomaly_fallen_star.json`, `anomaly_spirit_cache.json` |
| Mobs | coal and raw iron (Ember mobs), copper and redstone (Foundry, Precursor), quartz and amethyst (Glass, Mirror, Dream), gold ingots (Sovereigns, Court) | `src/main/resources/data/awaken/loot_table/entities/*.json` |
| Foragers | a DIG fauna turns up emerald 3%, gold nuggets 10%, copper nuggets 17%, iron nuggets 25% | `mobs/entity/WildTalents.java:245-253` |

**The big ore sinks, totalled** (full price; a temper is paid per item, `hearth/Upgrades.java:67-74`):

| Sink | Iron | Gold | Diamond | Other | Rank |
|---|---|---|---|---|---|
| Anchor II → V | 16 | 12 | 18 | 32 copper, 12 shards + 8 blocks amethyst, 32 obsidian, 48 essence, a Lord's Heart | D, C, B, A |
| Forge II → III | 36 | 18 | 3 | 36 coal, 8 magma, 14 essence | D, B |
| Temper one item 0 → VI | 63 | 20 | 6 | 21 essence (none through Oren) | VI needs Forge III in an Armory (`hearth/Upgrades.java:235-237`) |
| Temper a 4-armour + weapon kit to VI | 315 | 100 | 30 | 105 essence | the largest metal sink in the game |

---

## (b) Ore generation

| Question | Answer from the code | Where |
|---|---|---|
| Do the Awakened Lands change vanilla ore placement? | **No.** All six biomes list the vanilla overworld set (26 ore/stone features incl. `amethyst_geode`, every diamond feature). Shattered Highlands adds `ore_emerald` and `ore_infested`, as vanilla's windswept hills/stony peaks have. **Frost Spires replaces grove but drops grove's `ore_emerald`** (vanilla `grove.json` has it), a small emerald loss | `src/main/resources/datapacks/awakened_lands/data/awaken/worldgen/biome/*.json` (e.g. `shattered_highlands.json:138-180`); `/Volumes/MEDIA/Awaken/.ref/assets/data/minecraft/worldgen/biome/grove.json`; replacements in `docs/SPEC.md:1220-1223` |
| Awakened Alpine / Tectonic | Surface material rules, eroded/ridge density wrappers, two spruce features; no ore touch. Tectonic changes heights; ore placed features are Y-based, so ores are vanilla | `src/main/resources/datapacks/awakened_alpine/data/awaken/lithostitched/worldgen_modifier/alpine/*.json`, `.../neoforge/biome_modifier/alpine/*.json` |
| Biome modifiers | 20 `add_spawns` (fauna) and 3 `add_features` (two spruces, the Carillon city); no ore | `src/main/resources/data/awaken/neoforge/biome_modifier/`, `src/main/resources/datapacks/*/data/awaken/neoforge/biome_modifier/` |
| Carillon | 20 solid blocks under the street, "caves and ores carry on below" | `docs/SPEC.md:1349` |
| Descent strata | **The only AWAKEN-placed ores.** About 1% of wall rock (`oreChance` 0.008-0.012) in 2×2×2 cells (hash on `x>>1, y>>1, z>>1`): 1 coal/iron/copper, 2 iron/gold/lapis, 3 gold/lapis/redstone, 4 redstone/iron/**diamond**, 5 diamond/emerald/gold, 6 diamond/iron/gold, 7 diamond/lapis/emerald, 8 diamond/amethyst block/emerald, 9 diamond/gilded blackstone/emerald, 10 diamond/**ancient debris**/emerald. All deepslate variants | `descent/world/Palette.java:45-84`; `descent/world/Terrain.java:374-377` |
| Gate and other AWAKEN dimensions | No ore features in any gate, stratum, Lucidium, Stillpoint, Halo or trials biome. The Ember Mines gate's **Ember Seam** (`gate_ember_ore`, light 7) is dressing and gate interiors are unbreakable | `src/main/resources/data/awaken/worldgen/biome/*.json` (features empty); `gates/GatesRegistry.java:111-113, 137`; `gates/world/PiecesSmall.java:145`; `docs/SPEC.md:2466` |
| Sky-island undersides | Coal, iron and copper ore in the earth under the sky ruins' floating islands | `above/ruin/RuinBuilder.java:864, 880-881` |
| AWAKEN ores or minable minerals of its own | **None.** The Lands' Sunshard, Frostspar, Fault Seam, the Descent's Choir Crystal and Seed Vein drop themselves and feed no recipe, request or upgrade. No block or item is named after Lucidium (a dream dimension) | `src/main/resources/data/awaken/loot_table/blocks/{sunshard,frostspar,fault_seam,choir_crystal,seed_vein}.json`; `terrain/glass/GlassShardsFeature.java:73-93`, `terrain/frost/FrostSpiresFeature.java:65-86` |
| Precursor alloy, scrap, circuits | Alloy is crafted: 8 scrap + a Lord's Heart → 8. Scrap comes from broken machines (strength 12, the only block that drops it) and ruin chests (w30 of ~110-137). Circuits: crafted or from broken machines | `src/main/resources/data/awaken/recipe/precursor_alloy.json`; `above/AboveBlocks.java:61`; `loot_table/blocks/broken_machine.json`; `loot_table/chests/precursor_*.json` |
| Essence shards | **Never mined.** From AWAKEN mobs (181 entity loot tables), gate runs and caches, Anchor requests, Descent satchels, fallen stars and rifts, Hunts, bounties, the Beloved town gift, the gate market and the level-road gift of 8 at level 8 | `gates/run/Loot.java:61-63`, `gates/run/Runs.java:741`; `hearth/request/Requests.java:376, 603`; `descent/DescentModule.java:252`; `world/anomaly/Stars.java:348`, `Rifts.java:210`; `living/pulse/Hunts.java:91`; `living/bounty/Bounties.java:160`; `src/main/resources/data/awaken/awaken/level_road/essence.json` |

---

## (c) Mining XP and crafts

### Hunter (home) XP from ores

| Rule | Value | Code |
|---|---|---|
| XP per ore | **0.01 of a level, the same for coal and diamond** | `progression/ProgressionModule.java:247, 286-289` |
| Which blocks | `#minecraft:ores` only: the eight overworld families + nether quartz. **Not ancient debris, not amethyst** | `ProgressionModule.java:288`; `/Volumes/MEDIA/Awaken/.ref/assets/data/minecraft/tags/block/ores.json` |
| Where | Overworld only (`isHome`): ores in the Nether and the Descent pay nothing | `ProgressionModule.java:249-251` |
| Budget | source `ore` 0.06 of a level per 10 min: ~6 ores pay in full, then each pays `budget / tally` | `progression/XpBudget.java:30-35, 52-67` |
| Taper | × 1 / (1 + (level − 1) / 10) (vanilla blocks are content level 0): ×0.34 at level 20, ×0.17 at 50 | `XpBudget.java:79-81`; `ProgressionModule.java:240-244` |
| Counters | `awaken:ores_mined` (awakened only) → Directive "Mine 12 ores." (0.1 level), drill "Mine 10 ores", title **Stonebreaker** at 1,000 ores (+5% loot) | `ProgressionModule.java:291`; `src/main/resources/data/awaken/awaken/directive/mine_deep.json`, `drill/mine_10.json`, `title/stonebreaker.json` |
| SPEC | §4.3 "Mining ores: 1% of a level per ore", replaced in §13 by the budgets above | `docs/SPEC.md:123-126, 509-510` |

### The Mining craft (Living Wilds, SPEC §21.9)

- Craft XP: **10 an ore, 15 a deepslate ore** (any `#minecraft:ores` or `#c:ores`, so ancient debris counts), any
  dimension (`living/craft/CraftEvents.java:135`; `living/craft/Herbs.java:61-68`). Smelting ores pays no craft
  (only food smelts pay Cooking, `CraftEvents.java:245-249`).
- Each ore also pays the Hunter 0.008 of a level through the `mine` budget, **0.012 per 10 min** (1.5 ores), and the craft
  XP is cut by the same share (`living/craft/Crafts.java:23, 40-54`; `living/LivingModule.java:56`). So in the long run
  the craft earns about **15 XP per 10 minutes (22 on deepslate) however many ores you break**.
- Curve 40·c^1.3 + 40 (`living/craft/Craft.java:72-84`). Cumulative XP: level 5 708, 10 3,438, 20 16,875, 30 42,928,
  40 83,305, 50 139,331. At the budget's pace that is roughly **level 5 after ~5-8 h of play with mining in it,
  level 10 after ~25-38 h, level 20 after 125+ h, level 50 never** (my arithmetic from the constants above; a player who
  mines a few ores in every 10 minutes of other play gets the most per ore).

| Perk (level) | Implemented | What it does exactly | Its look | Code |
|---|---|---|---|---|
| Clean Strike (5) | yes | an ore's drops: +1 copy of the first drop stack 15% of the time | none (the item just appears) | `living/craft/Craft.java:26`; `CraftEvents.java:139-153` |
| Ore Sense (10) | yes, client | every 4 s (`ORE_EVERY` 80 ticks) with an item in `#minecraft:pickaxes` in the main hand, every ore within 6 blocks (cube, max 96) is drawn as a glass box through stone for 3 s (`ORE_SHOW` 60): a 6-tick fade in, then out; stroke 2.2 px plus a faint fill, tinted per ore (gold FFE27A, iron EAD2BC, copper FFB07A, diamond 8FF6FF, emerald 7FFFA8, redstone FF7A7A, lapis 7FA0FF, coal B8C2CE) | gizmo cuboids, always on top | `living/client/CraftFx.java:36-37, 43, 94-129` |
| Vein Sight (20) | yes, but not as planned | Ore Sense's radius 6 → 10. The plan said "the whole vein outlined" | as Ore Sense | `Craft.java:28`; `CraftFx.java:100`; `docs/living-wilds/PLAN.md:438` |
| Deep Ear (30) | yes, but not as planned | stone and deepslate (`#base_stone_overworld`) break ×1.1 faster. The plan said "hear caves behind walls" | none | `Craft.java:29`; `CraftEvents.java:156-160` |
| Deep Vein (40) | yes (replaces the plan's **"alloy from deepslate", which is not built**) | deepslate ores +1 copy 25% (one extra at most, with Clean Strike) | none | `Craft.java:30`; `CraftEvents.java:144-146` |
| Heart of Stone (50) | yes | below y 0, all damage taken ×0.85 | none | `Craft.java:31`; `CraftEvents.java:106` |

**Ore Sense never fires with a Hunter mattock in hand**: the check is `ItemTags.PICKAXES` (`CraftFx.java:96`) and no
mattock is in that tag (the gear merge adds them only to `enchantable/mining`, `mining_loot`, `durability`:
`src/main/merge/gear/data/minecraft/tags/item/enchantable/*.json`).

### Other ore sight

| Sight | Who, when | Radius | Look | Code |
|---|---|---|---|---|
| Veil spirit sight | only while projected as a spirit (sneak + G 10 s); the server sends positions inside Awareness | Awareness 16 + 2·PER + SPI (+perk), 16-160 | stroke 1.2 px at alpha 110, fill 26, colour per ore (coal 6A7590, iron D8B7A0, copper E07A4A, gold F2C94C, redstone FF4A4F, lapis 3C6CF0, diamond 6FF0E8, emerald 4FE08A, quartz F2EEE6, other B48CFF); up to 384 ores | `veil/SpiritSight.java:60-97`; `veil/Cord.java:39-40`; `veil/client/VeilWorld.java:37-61` |
| Awake "secret sense" | every awakened player | 4 + PER/4 (cap 32) | **secrets only, never ores** (`scan(..., true)`) | `veil/SpiritSight.java:357-371`; `veil/Cord.java:43-45` |
| Shimmer grove "Ore sight" | standing in an awake ore-sight grove (radius 7, 150 s), mostly by day | box 12 to each side, 18 down, 5 up, nearest 64 | edge-only strokes 2 px, always on top; WAX_ON motes on the grove | `world/anomaly/GroveVariant.java:11`; `world/anomaly/Groves.java:47-49`; `world/anomaly/client/OreSight.java:29, 70-109`; `world/anomaly/ShimmerEntity.java:104` |
| Lumen Moths (DOWSE) | dusk to dawn, Greenwild ambient | probe 12 random columns within ±6, 2-27 blocks under the surface; orbit at radius 1.6, 1.4-2.8 above the ground over the hit; GLOW particles on the spot while within 6 | a moth circling and sparks on the ground | `mobs/entity/FaunaMob.java:501-509, 523-534`; `mobs/entity/FaunaGoals.java:144`; `world/fauna/Habitat.java:74` |
| Quest "Moth Lights" (Ilse) | follow 4 spawned moths, then deliver **3 raw iron**; 0.1 + 0.4 levels and the Lumen Tonic relic | — | — | `src/main/resources/data/awaken/awaken/quest/moth_lights.json` |
| Quest "Oren's First Steel" | after Carillon registration: **6 raw iron, 6 raw copper, 8 coal**, a temper; relic Oren's Mark (a fresh blade's first temper starts higher) | — | — | `quest/orens_first_steel.json`; `hearth/Upgrades.java:257-259` |

Two more mining interactions: in a Listener stratum every block broken adds 15 noise (half when sneaking), which draws
Listeners (`descent/DescentModule.java:316-318`; `descent/rules/Zones.java:357-362`); and a Hearth request appears only
once its `mined:` token holds, i.e. after you have mined that ore once (`hearth/request/Requests.java:130-133`).

---

## (d) Tools

| Tool | What it is | Code |
|---|---|---|
| Pickaxe, shovel, hoe, axe | **All vanilla.** AWAKEN registers no pickaxe, shovel, hoe or drill (`DataComponents.TOOL` is set only on the mattock and the Arms maul's mace tool) | `gear/GearItems.java:94`; `arms/ArmsItems.java:65` |
| **Hunter mattock** (gear, one per tier) | one head for stone, wood and earth: mines `mineable/pickaxe`, `axe` and `shovel` at the tier's speed, drops denied by the tier's vanilla "incorrect" tag; loses 2 durability a hit; Efficiency, Fortune, Silk Touch, Unbreaking allowed. Crafted from the tier's hunter tags + essence + sticks | `gear/GearItems.java:80-98`; `gear/HunterTier.java:31-44, 70-71`; `src/main/resources/data/awaken/recipe/*_mattock.json` |
| Arms axes | real vanilla-style axes (cut wood, strip logs) at the tier's speed | `arms/ArmsItems.java:56-62, 78-100` |
| Precursor tools, Frame mining, mining powers | **None.** The Precursor items are scrap, circuit, alloy, cell, scanner, frame modules (`above/AboveItems.java`); the six Frame modules are Jet Boots, Shield Emitter, Stealth Field, Magnet, Night Vision, Step Assist (`above/frame/FrameModule.java:15-33`). No power breaks a block for good: Terrain Rip and the Spirit door lift blocks into `TerrainLedger` and put them back (`powers/TerrainLedger.java`, `powers/catalog/KineticPowers.java:70-75, 230`); Architect builds hard-light constructs (`powers/catalog/ArchitectPowers.java:38`) | — |

**The mattock against vanilla pickaxes** (vanilla `.ref/src/net/minecraft/world/item/ToolMaterial.java:23-31`):

| Tier (level road) | Mattock speed | Mines like | Durability | Vanilla twin |
|---|---|---|---|---|
| E Wildhide (L3) | 4.5 | stone pick (iron, copper, lapis, coal) | 240 | stone 4.0 / 131, copper 5.0 / 190 |
| D Beastbound (L13) | 6.0 | iron pick (**diamonds**, gold, redstone, emerald) | 520 | iron 6.0 / 250 |
| C Gatewrought (L22) | 7.5 | diamond pick (obsidian, debris) | 1450 | diamond 8.0 / 1561 |
| B Veilwoven (L42) | 8.5 | netherite pick | 1950 | netherite 9.0 / 2031 |
| A Lordscale (L67) | 9.5 | netherite pick | 2350 | — |
| S Sovereign (L97) | 11.0 | netherite pick | 2850 | gold 12.0 (32 uses) |

`docs/modules/gear.md:32`: "a D mattock (beast trophies and copper, no iron needed) mines diamonds". So AWAKEN's own
pickaxe ladder needs no ore at all.

**`Arms.styleOf`** (`arms/Arms.java:24-50`): null for pickaxes, shovels, hoes, mattocks and foci, confirmed; but **not**
null for axes (`path.endsWith("_axe")` → the Axe style, `:47`; "pickaxe" has no underscore before "axe", so it stays
null), for any `_sword` and AWAKEN `_blade` (Longsword, `:43`), and for the mace (Maul, `:48`).

**Mining feel: nothing custom.** No sound, particle, hit-stop, shake or animation on breaking stone or ore. Holding
attack on a block stays vanilla mining (`arms/client/ArmsPredict.java:92-97` makes sure it is a hold, not a new
strike). The Hands system's only block touch is plucking a zero-hardness plant with an empty hand
(`anims/client/Touches.java:254-273`). Clean Strike and Deep Vein add the extra drop silently; Field Forge plays an anvil
(`CraftEvents.java:181`). Cave sound comes from the pack's Sound Physics Remastered (`pack/mods.json`).

---

## (e) Weapons and armour value ladder

Damage is the total with the player's 1 (AWAKEN rows are "over the player's 1": `arms/ArmsData.java:16-18`;
`gear/HunterTier.java:64-65`). Attacks a second = 4 + the speed modifier. Hours to the level-road level are interpolated
from the balanced player's medians (`docs/progression/audit-spine.md:195, 201-204`); recipes are taught by the level road
(`src/main/resources/data/awaken/awaken/level_road/hunter_gear_*.json`) but nothing enforces a recipe lock (no
limited-crafting code; SPEC: "the recipe unlocks when the blade is held", `docs/SPEC.md:4160-4161`).

### Weapons

| Weapon | Damage | Speed /s | Durability | Special | Made from | Reachable |
|---|---|---|---|---|---|---|
| Vanilla iron sword | 6 | 1.6 | 250 | fights as the Arms Longsword (`arms/Arms.java:43`); temperable | 2 iron | any time |
| Vanilla diamond sword | 7 | 1.6 | 1561 | same; temperable; **cannot be attuned** (`arms/ArmsForge.java:151, 186`) | 2 diamonds | once diamonds are found (iron pick, or a D mattock at L13) |
| Vanilla netherite sword | 8 | 1.6 | 2031 | same; fire-proof | diamond sword + an ingot (4 scrap, 4 gold) + template | Nether |
| Vanilla iron / diamond / netherite axe | 9 / 9 / 10 | 0.9 / 1.0 / 1.0 | 250 / 1561 / 2031 | fight as the Arms Axe | — | — |
| Gear blade E / D / C / B / A / S | 5 / 6 / 7 / 8 / 9 / 10 | 1.8 / 1.7 / 1.7 / 1.7 / 1.7 / 1.8 | 220 / 500 / 1400 / 1900 / 2300 / 2800 | Longsword style; the set bonuses live on armour | tier's `#hunter_core`, `#hunter_bind`, essence (+ a Lord's Heart at A, S) | L3 0.5 h / L13 ~6.4 h / L22 ~13.8 h / L42 ~41.7 h / L67 ~65.5 h / L97 ~81.8 h |
| Arms Longsword E…S | 5.5 … 10.5 (blade + 0.5) | 1.6 | as the blade | attunement at the Forge (6 essence, a Core Shard, a catalyst), Weaves, Marks on dropped ones, mastery | the tier's gear blade + 1 core + 1 bind | as the blade |
| Arms other styles (15) | e.g. Axe 7…12, Maul 8.5…13.5, Daggers 4…9 | 2.4 (gauntlets), 2.2 (daggers) … 0.7 (maul) | as the blade | as Longsword; each needs its **Blueprint** (4 paper, 2 iron, 1 essence) | blade + core + bind + Blueprint | as the blade |
| Lord-forged (15) | e.g. Bell Warden Maul 9.5, Hollow Herald Greatblade 12.5 | by style | by tier | a Lord's Mark | drop: 35% a Lord, certain on the 3rd without one | Lord kills |

Rows: `arms/ArmsData.java:23-140`; gear `gear/HunterTier.java:31-44`; vanilla `.ref/src/net/minecraft/world/item/Items.java:1678-1712`
and `ToolMaterial.java:23-31`; Lord-forged `arms/LordForged.java:135-136`.

### Armour (total of four pieces; chest durability = multiplier × 16)

| Set | Armour (B/L/C/H) | Toughness | Knockback res. | Chest durability | Set bonus | Made from (per set) | Reachable |
|---|---|---|---|---|---|---|---|
| Vanilla iron | 15 (2/5/6/2) | 0 | 0 | 240 | — | 24 iron | any time |
| Vanilla diamond | **20** (3/6/8/3) | **2.0** | 0 | 528 | — | 24 diamonds | with diamonds |
| Vanilla netherite | 20 (3/6/8/3) | 3.0 | 0.1 | 592 | — | diamond set + 4 ingots | Nether |
| E Wildhide | 10 | 0 | 0 | 208 | sneak 15% faster; Hunter's Sense | hides, `#hunter_bind_e` (string/feather/moss), essence | L3 |
| D Beastbound | 15 | 0.5 | 0 | 288 | +1/+2 hearts; Cornered | hides, beast trophies, **14 binds over the 7 pieces: copper, iron or gold ingot** | L13 |
| C Gatewrought | **19** (3/6/7/3) | **1.5** | 0 | 448 | Force ward −15/−25%; primed strikes | Lord relics, **9 binds: gold, emerald or amethyst**, 2 Lord's Hearts | L22 |
| B Veilwoven | 20 | 2.5 | 0.05 | 560 | Spirit ward; Veil Flicker | relics, echo shard / dream residue / ender pearl | L42 |
| A Lordscale | 21 | 3.0 | 0.1 | 640 | Lord ward; Overflow | **10 binds: diamond, wind charge or Precursor circuit**; 9 hides: alloy, netherite scrap or shulker shell; 3 Lord's Hearts | L67 |
| S Sovereign Weave | 24 | 3.5 | 0.15 | 768 | Unbowed; Sovereign's Measure | **9 hides: netherite ingot or scrap only**; 5 Lord's Hearts | L97 |
| Precursor Frame | 20 | 2.5 | 0.05 | 560 | six socketable modules | alloy, circuits, scrap, a cell | a scanned Frame Forge, after the Fabricator (level road 17) |

Sources: `gear/HunterTier.java:31-44` (order boots, legs, chest, helm); `gear/SetBonus.java:22-104`;
`.ref/src/net/minecraft/world/item/equipment/ArmorMaterials.java:18-32`; `above/AboveItems.java:47-49`; set counts by
summing `src/main/resources/data/awaken/recipe/<tier>_*.json`.

**Do Arms or gear recipes use iron, gold, diamond or netherite?** Only through the hook tags, and only as one option
among several, except S: `#hunter_bind_d` (copper/iron/gold), `#hunter_bind_c` (gold/emerald/amethyst), `#hunter_bind_a`
(diamond/wind charge/circuit), `#hunter_hide_a` (alloy/netherite scrap/shulker shell), `#hunter_hide_s` (netherite only).
The same tags repair the tier (`gear/HunterTier.java:122-126`; Arms rows' `repair`, `arms/ArmsData.java:16-18`).

**Is diamond gear ever worth crafting today?** On raw numbers, yes, and that is part of the problem: AWAKEN's tiers sit
on vanilla's (`docs/modules/gear.md:19-26`), so a diamond sword (7) equals the C blade a player gets at ~14 h and is
0.5 under the C Longsword; **diamond armour (20 armour, 2.0 toughness, 528 chest) beats Gatewrought (19, 1.5, 448)** and
netherite beats Veilwoven, and vanilla pieces mix with set halves (`gear/SetBonus.java:22-23`), can be tempered
(`hearth/Upgrades.java:212-228`) and fight with Arms' Longsword/Axe. What diamond gear can never have: a fighting style
beyond Longsword/Axe/Maul, attunement, Marks, a power focus, a full set bonus. Nothing in AWAKEN mentions, teaches or
rewards it, so it is invisible rather than useless; and "boring" is accurate for the sword, which plays as the plainest
of 16 styles.

---

## (f) The economy

| Piece | State in the code | Code |
|---|---|---|
| Coin (bell marks), shops, Hunter Exchange | **Not built.** `EconomyHooks` exists, nothing calls `install`, so `balance` is 0 and `pay` returns false; there is no `economy` package (SPEC §16.1 lists one) | `core/api/EconomyHooks.java`; `docs/SPEC.md:1781, 1955-1960` |
| Bounty rewards | levels + essence; "coin once Carillon's economy exists" | `living/bounty/BountyDef.java:12`; `living/bounty/Bounties.java:160` |
| Caravan (World Pulse) | a vanilla Wandering Trader with vanilla trades; driving off raiders pays **4 emeralds** | `living/pulse/People.java:30-74` |
| Caravan Writ relic | perk `awaken:shop_discount` 0.15, **read nowhere** | `living/quest/Relics.java:57` |
| Gate market (friendly gates) | the only AWAKEN trades: emerald and gold sell for essence; essence buys diamonds, netherite scrap, a Lord's Heart, food, pearls | `gates/run/Rooms.java:491-519, 542-563` |
| Hearth requests | the only buyer of mined ores: each delivery pays essence ≈ effort × (0.6 + 0.2 × rank), up to 0.4 of a level (×1.5 at a full streak), 10-15% Forge credit, a chance of a Core Shard (4% per effort, cap 60%) or an early recipe (25%), reputation. "value" = minutes of effort per item: diamond 3.0, emerald 2.5, gold 0.9, iron 0.5, copper 0.3, lapis 0.25, coal 0.2, redstone 0.15, netherite scrap 6.0 | `hearth/request/RequestRules.java:35-42, 155-165`; `hearth/request/RequestDef.java:12-21`; `src/main/resources/data/awaken/awaken/request/*.json` |
| Vanilla villagers | untouched (no trade event listener anywhere) | grep of `VillagerTradesEvent`/`WandererTradesEvent`: none |

---

## (g) Verdict

**Biggest gaps**

1. **Diamond has no AWAKEN use of its own.** About 29 diamonds cover every one-off (Anchor IV 6, Anchor V 12, Forge III 3,
   Recalibrator 2; `hearth/Upgrades.java:54-55, 63`, `above/MachineType.java:47`); its gear slot (`#hunter_bind_a`) has
   two non-ore alternatives; gates from C, chests, the Mine Echo and the essence market (5 shards each) hand it out.
2. **Lapis is unused** by AWAKEN; **emerald** is only an option and an essence sink; **nether quartz** ore is never
   needed (overworld mobs drop quartz); **ancient debris** matters only for Sovereign Weave at level 97, which the gate
   market also sells.
3. **Ore XP is flat and tiny**: 1% of a level for any ore, overworld only, 6 ores per 10 min, tapered by level
   (`ProgressionModule.java:247, 288-289`). A diamond is worth a lump of coal.
4. **The Mining craft cannot be levelled by mining**: the `mine` budget holds it to ~15 craft XP per 10 minutes, so Ore
   Sense (10) is ~25-38 h of play away and perks 20-50 are out of reach. Three planned perks became stat bumps (vein
   outline → radius 10; hear caves → ×1.1 speed; alloy from deepslate → +1 drop). **Ore Sense ignores the mattock**
   (`CraftFx.java:96`).
5. **AWAKEN removed the pickaxe ladder on purpose**: a D mattock mines diamonds with no iron (`docs/modules/gear.md:32`).
6. **No AWAKEN ore or mineral exists.** The Lands' crystals are decoration; the Ember Mines gate's "Ember Seam" can't be
   broken; the Descent's ores are vanilla.
7. **Mining has no feel**: no sound, particle, animation, hit-stop or reveal, in a mod whose quality bar is "every
   feature needs its own look".
8. **Automation and loot replace digging**: the Mine Echo rolls ore every 120 s per pad (`hearth/echo/Duty.java:50, 193-203`).
9. **Vanilla gear competes silently**: diamond armour out-stats Gatewrought; nothing tells the player either way.
10. **No economy**: no coin, no shop, no city buyer for ores (`core/api/EconomyHooks.java` uninstalled).

**Cheap hooks a design can plug into** (data first, then small code)

| Hook | Kind | Where |
|---|---|---|
| Hunter tags `#awaken:hunter_<hide\|core\|bind>_<e..s>`: make a mined mineral the tier's signature fitting, or add a new AWAKEN ore | data (merge folder per module) | `src/main/merge/gear/data/awaken/tags/item/hunter_*.json`; recipes `src/main/resources/data/awaken/recipe/<tier>_<piece>.json` |
| Anchor requests: new items, rank counts, `value`, tokens `mined:`, `depth:`, `got:` (a deepslate-only or depth-gated ore ask) | data | `src/main/resources/data/awaken/awaken/request/*.json`; tokens `hearth/request/Requests.java:123-142` |
| Arms Forge: ore catalysts (today only copper = Charge), attunement and Blueprint prices | one map and two lists | `arms/ArmsForge.java:50-54, 253-265` |
| Temper / Anchor / Forge prices (`Costs.Part` lists) | small code | `hearth/Upgrades.java:49-74` |
| Ore XP by ore (rarity, depth) | two lines | `progression/ProgressionModule.java:247, 288-289`; budget `progression/XpBudget.java:30-35` |
| Mining craft: XP per ore type, perk list, a vein outline (the renderer already tints by ore), the pickaxe check | small code | `living/craft/CraftEvents.java:111-160`; `living/craft/Craft.java:25-31`; `living/client/CraftFx.java:94-129` |
| Craft XP budget for mining | one constant | `living/LivingModule.java:56` |
| Descent strata ore mixes (a natural home for an AWAKEN ore) | data-like arrays | `descent/world/Palette.java:45-84` |
| Lands features: an ore or crystal per biome (feature pipeline and `CrystalClusterBlock` exist) | datapack + a block | `src/main/resources/datapacks/awakened_lands/data/awaken/worldgen/placed_feature/lands/`; `terrain/block/CrystalClusterBlock.java` |
| Lumen Moth dowsing, shimmer-grove ore sight, fallen-star raw-ore loot: three ore-finding beats that already exist | existing systems | `mobs/entity/FaunaMob.java:523-534`; `world/anomaly/client/OreSight.java`; `loot_table/blocks/anomaly_fallen_star.json` |
| Gate loot and market (remove or reduce diamonds to give mining its job back; or add ore-buying stalls) | one table | `gates/run/Loot.java:21-26`; `gates/run/Rooms.java:542-563` |
| Mine Echo yields | one table | `hearth/echo/Duty.java:193-203` |
| Bounties (`collect`) and quests (`deliver`) for ores | data | `src/main/resources/data/awaken/awaken/bounty/*.json`, `quest/*.json` |
| Machines' and Frame parts | enum lists | `above/MachineType.java:18-53`; `above/frame/FrameForge.java:35-42`; `above/frame/FrameModule.java:15-33` |
| Coin, when it exists | API ready | `core/api/EconomyHooks.java` |

Doc drift noticed on the way: SPEC §13 "A and S are designed only" (`docs/SPEC.md:646`) is stale (both are registered,
`gear/GearItems.java:39-47` loops every `HunterTier`, with recipes and level-road steps at 67 and 97); `GearItems` says
"The 28 Hunter gear items" but registers 42 (`gear/GearItems.java:30`).
