# AWAKEN worldgen + dimensions cheat sheet: Minecraft 26.3 / NeoForge 26.3.0.95

Every name, signature and JSON field below was checked against the real sources. Path prefixes used in "Src:" lines:

- `[mc]` = `/Volumes/MEDIA/Awaken/.ref/src/net/minecraft/` (Minecraft + NeoForge patches)
- `[neo]` = `/Volumes/MEDIA/Awaken/.ref/neo/net/neoforged/neoforge/`
- `[data]` = `/Volumes/MEDIA/Awaken/.ref/assets/data/minecraft/`

## 0. Things that no longer exist (vs 1.21.x) and their replacements

| 1.21.x | 26.3 |
|---|---|
| `ChunkGenerator#fillFromNoise`, `#buildSurface`, `#applyCarvers` | one method: `buildTerrain(...)` (§3). Statuses `NOISE`/`SURFACE`/`CARVERS` → `ChunkStatus.TERRAIN` |
| `BiomeSource#getNoiseBiome(x,y,z,Climate.Sampler)` | `createResolver(Climate.Sampler)` returns a `BiomeResolver` (§4) |
| dimension_type `effects`, `fixed_time` (long), `bed_works`, `respawn_anchor_works`, `piglin_safe`, `has_raids`, `ultrawarm`, `natural`, `cloud_height` | `skybox`, `cardinal_light`, `has_fixed_time` (bool), `default_clock`, `timelines`, and environment `attributes` (`gameplay/bed_rule`, `gameplay/respawn_anchor_works`, `gameplay/piglins_zombify`, `gameplay/can_start_raid`, `gameplay/water_evaporates`, `gameplay/fast_lava`, `gameplay/nether_portal_spawns_piglin`, `visual/cloud_height`) |
| `DimensionSpecialEffects`, `RegisterDimensionSpecialEffectsEvent` | removed. `skybox` enum + attributes; custom rendering via NeoForge attributes `neoforge:custom_skybox` / `neoforge:custom_clouds` / `neoforge:custom_weather_effects` + `RegisterCustomEnvironmentEffectRendererEvent` |
| biome `effects.fog_color/sky_color/water_fog_color/particle/ambient_sound/mood_sound/additions_sound/music`, top-level `spawners`/`spawn_costs`/`creature_spawn_probability` | biome `attributes` (§2). `effects` keeps only `water_color`, `foliage_color`, `dry_foliage_color`, `grass_color`, `grass_color_modifier` |
| `ConfiguredFeature` / `worldgen/configured_feature` | `Feature` is now the configured object: data registry `worldgen/feature` (`Registries.FEATURE`), codecs in `Registries.FEATURE_TYPE` |
| `SurfaceRules` / `SurfaceSystem` | `MaterialRule` / `MaterialSystem` (`worldgen/material_rule`, `worldgen/material_condition`) |
| `ImprovedNoise`, `PerlinSimplexNoise`, octave `PerlinNoise` | single-octave `PerlinNoise` / `SimplexNoise` (both `GradientNoise`) + `NoiseStack`; `NormalNoise` = parameters + `create(RandomSource)` (§10) |
| `TicketType<T>` (generic, comparator) | `record TicketType(long timeout, int flags, boolean forceNaturalSpawning)`, registry `Registries.TICKET_TYPE` (§7) |
| `ChunkPos.x` field, `new ChunkPos(BlockPos)` | `record ChunkPos(int x, int z)`: `pos.x()`, `ChunkPos.containing(BlockPos)` |
| BlockState JSON `{"Name":..,"Properties":..}` | `"minecraft:stone"` or `{"id": "minecraft:oak_log", "properties": {"axis": "y"}}` |
| overworld chunks in `<world>/region` | `<world>/dimensions/minecraft/overworld/region` (all dimensions, §11) |
| `StructurePlacement` abstract class | interface; spread logic in `AbstractSpreadingStructurePlacement`; new `minecraft:dimension_origin` |

## 1. Custom dimension via datapack JSON

Files (mod jar resources count as a datapack; levels are created from the `LEVEL_STEM` registry at every server start,
so new and existing worlds both get them. Src: `[mc]server/MinecraftServer.java` line ~422):

- `data/awaken/dimension_type/<name>.json` → `Registries.DIMENSION_TYPE` (`"dimension_type"`)
- `data/awaken/dimension/<name>.json` → `Registries.LEVEL_STEM` (`"dimension"`)
- optional `data/awaken/timeline/<name>.json` (`Registries.TIMELINE`), `data/awaken/world_clock/<name>.json` (`Registries.WORLD_CLOCK`, content `{}`)

Java keys:
```java
ResourceKey<Level> GATES = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("awaken", "gates"));
ResourceKey<DimensionType> GATES_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath("awaken", "gates"));
ServerLevel gates = server.getLevel(GATES);   // @Nullable
```

### Vanilla `[data]dimension_type/overworld.json` (verbatim)
```json
{
  "ambient_light": 0.0,
  "attributes": {
    "minecraft:audio/ambient_sounds": {
      "mood": { "block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000 }
    },
    "minecraft:audio/background_music": {
      "creative": { "max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.creative" },
      "default": { "max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.game" }
    },
    "minecraft:gameplay/bed_rule": {
      "can_set_spawn": "always", "can_sleep": "when_dark",
      "error_message": { "translate": "block.minecraft.bed.no_sleep" }
    },
    "minecraft:gameplay/nether_portal_spawns_piglin": true,
    "minecraft:gameplay/respawn_anchor_works": false,
    "minecraft:gameplay/straw_bed_rule": {
      "can_set_spawn": "never", "can_sleep": "when_dark", "destroy_on_leave": true,
      "error_message": { "translate": "block.minecraft.bed.no_sleep" }
    },
    "minecraft:visual/ambient_light_color": "#0a0a0a",
    "minecraft:visual/cloud_color": "#ccffffff",
    "minecraft:visual/cloud_height": 192.33,
    "minecraft:visual/fog_color": "#c0d8ff",
    "minecraft:visual/sky_color": "#78a7ff"
  },
  "coordinate_scale": 1.0,
  "default_clock": "minecraft:overworld",
  "has_ceiling": false,
  "has_ender_dragon_fight": false,
  "has_skylight": true,
  "height": 384,
  "infiniburn": "#minecraft:infiniburn_overworld",
  "logical_height": 384,
  "min_y": -64,
  "monster_spawn_block_light_limit": 0,
  "monster_spawn_light_level": { "type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0 },
  "timelines": "#minecraft:in_overworld"
}
```
(Only whitespace compacted.) `#minecraft:in_overworld` = `#minecraft:universal` (= `minecraft:villager_schedule`) + `minecraft:day` + `minecraft:moon` + `minecraft:early_game`. Vanilla also ships `overworld_caves.json` (same, `has_ceiling: true`).

### Vanilla `[data]dimension_type/the_end.json` (verbatim)
```json
{
  "ambient_light": 0.25,
  "attributes": {
    "minecraft:audio/ambient_sounds": {
      "mood": { "block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000 }
    },
    "minecraft:audio/background_music": {
      "default": { "max_delay": 24000, "min_delay": 6000, "replace_current_music": true, "sound": "minecraft:music.end" }
    },
    "minecraft:gameplay/bed_rule": { "can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": true },
    "minecraft:gameplay/respawn_anchor_works": false,
    "minecraft:gameplay/straw_bed_rule": { "can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": true },
    "minecraft:visual/ambient_light_color": "#3f473f",
    "minecraft:visual/fog_color": "#181318",
    "minecraft:visual/sky_color": "#000000",
    "minecraft:visual/sky_light_color": "#ac60cd",
    "minecraft:visual/sky_light_factor": 0.0
  },
  "coordinate_scale": 1.0,
  "default_clock": "minecraft:the_end",
  "has_ceiling": false,
  "has_ender_dragon_fight": true,
  "has_fixed_time": true,
  "has_skylight": true,
  "height": 256,
  "infiniburn": "#minecraft:infiniburn_end",
  "logical_height": 256,
  "min_y": 0,
  "monster_spawn_block_light_limit": 0,
  "monster_spawn_light_level": 15,
  "skybox": "end",
  "timelines": "#minecraft:in_end"
}
```
The nether adds `"cardinal_light": "nether"`, `"skybox": "none"`, `"has_skylight": false`, `"has_ceiling": true`, `"logical_height": 128`, and
attributes `gameplay/sky_light_level: 4.0`, `gameplay/fast_lava`, `gameplay/water_evaporates`, `visual/fog_start_distance: 10.0`, `visual/fog_end_distance: 96.0`.

### Complete `dimension_type` field list
`record DimensionType(...)`, codec `createDirectCodec`. Src: `[mc]world/level/dimension/DimensionType.java`

| field | type | required | notes |
|---|---|---|---|
| `has_fixed_time` | bool | no (false) | true → `Level.isBrightOutside()`/`isDarkOutside()` always false (beds with `when_dark` never work) |
| `has_skylight` | bool | yes | |
| `has_ceiling` | bool | yes | true → no weather, nether-style maps, spawn uses `ChunkGenerator#getSpawnHeight`, mob spawns search down from top |
| `has_ender_dragon_fight` | bool | yes | |
| `coordinate_scale` | double [1e-5, 3e7] | yes | |
| `min_y` | int [-2032, 2031], %16 | yes | §9 |
| `height` | int [16, 4064], %16 | yes | `min_y + height <= 2032` |
| `logical_height` | int [0, 4064] ≤ height | yes | |
| `infiniburn` | block holder set (`"#tag"`) | yes | |
| `ambient_light` | float | yes | light floor: 0.0 overworld, 0.1 nether, 0.25 end |
| `monster_spawn_light_level` | IntProvider 0..15 or int | yes | |
| `monster_spawn_block_light_limit` | int 0..15 | yes | |
| `skybox` | `"none"` / `"overworld"` / `"end"` | no (`overworld`) | `end` also enables end flashes |
| `cardinal_light` | `"default"` / `"nether"` | no (`default`) | face shading (`CardinalLighting`) |
| `attributes` | environment attribute map | no (`{}`) | any attribute, positional or not (§2) |
| `timelines` | timeline id / list / `"#tag"` | no (empty) | time-based attribute tracks |
| `default_clock` | world_clock id | no | used by `/time`, sleeping, `Level#getDefaultClockTime()` |

### `dimension` (LevelStem) JSON
`record LevelStem(Holder<DimensionType> type, ChunkGenerator generator, OptionalLong seedOverride)`. Src: `[mc]world/level/dimension/LevelStem.java`
```json
{
  "type": "awaken:gates",
  "generator": {
    "type": "minecraft:flat",
    "settings": { "biome": "awaken:gate_void", "features": false, "lakes": false,
                  "layers": [ { "block": "minecraft:bedrock", "height": 1 } ], "structure_overrides": [] }
  },
  "neoforge:seed_override": 1234567
}
```
`type` can also be an inline dimension_type object. Generator types: `minecraft:noise` (`biome_source`, `settings`), `minecraft:flat` (`settings`), `minecraft:debug`, or your own (§3). `neoforge:seed_override` (optional long) changes `ServerLevel#getSeed()` and the `RandomState` of that dimension only.

### Recipes
- **Fixed time / no day cycle**: `"has_fixed_time": true`, omit `default_clock`, omit `timelines` (or `"#minecraft:universal"` to keep villager schedules). With no timeline the attributes stay at their dimension/biome values: `gameplay/sky_light_level` 15, `visual/sun_angle` 0 (= noon: `day.json` keyframe 6000 → 0/360), `visual/star_brightness` 0. Want another fixed look? set those attributes on the dimension (e.g. `"minecraft:visual/sun_angle": 180.0` = midnight position, `"minecraft:gameplay/sky_light_level": 4.0`).
- **Own day length**: make `world_clock/<x>.json` `{}` + a timeline using `"clock": "awaken:<x>"` (copy `[data]timeline/day.json`), set `default_clock`. At runtime `ServerClockManager#setRate(Holder<WorldClock>, float)` / `setPaused(holder, bool)` / `setTotalTicks(holder, long)` (`server.clockManager()`). Src: `[mc]world/clock/ServerClockManager.java`
- **No weather**: weather is global (`server.getWeatherData()`) and applies to every level where `Level#canHaveWeather()` = `hasSkyLight() && !hasCeiling() && dimension != END`. The only data switch is `"has_ceiling": true` (sky still renders; side effects listed in the table). Biome `"has_precipitation": false` only removes rain/snow particles; the weather layer still greys sky/fog. Src: `[mc]world/level/Level.java:926`, `[mc]world/attribute/WeatherAttributes.java`

**Dark cave dimension** (`data/awaken/dimension_type/stratum_cave.json`):
```json
{
  "has_fixed_time": true, "has_skylight": false, "has_ceiling": true, "has_ender_dragon_fight": false,
  "coordinate_scale": 1.0, "min_y": -64, "height": 384, "logical_height": 384,
  "infiniburn": "#minecraft:infiniburn_overworld", "ambient_light": 0.0,
  "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
  "skybox": "none",
  "attributes": {
    "minecraft:visual/fog_color": "#0b0a10",
    "minecraft:visual/fog_start_distance": 6.0,
    "minecraft:visual/fog_end_distance": 64.0,
    "minecraft:visual/ambient_light_color": "#000000",
    "minecraft:audio/ambient_sounds": { "mood": { "sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0 } },
    "minecraft:gameplay/bed_rule": { "can_sleep": "never", "can_set_spawn": "never" },
    "minecraft:gameplay/can_start_raid": false
  }
}
```
**Bright open-sky dimension, no weather, no day cycle** (`stillpoint.json`):
```json
{
  "has_fixed_time": true, "has_skylight": true, "has_ceiling": true, "has_ender_dragon_fight": false,
  "coordinate_scale": 1.0, "min_y": 0, "height": 256, "logical_height": 256,
  "infiniburn": "#minecraft:infiniburn_overworld", "ambient_light": 0.0,
  "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
  "skybox": "overworld",
  "attributes": {
    "minecraft:visual/sky_color": "#9cc4ff",
    "minecraft:visual/fog_color": "#d8e6ff",
    "minecraft:visual/cloud_color": "#ccffffff",
    "minecraft:visual/cloud_height": 96.0,
    "minecraft:gameplay/bed_rule": { "can_sleep": "never", "can_set_spawn": "always" }
  }
}
```
Gotchas: attribute defaults are black/transparent (`fog_color`, `sky_color` = `#000000`, `cloud_color` = `#00000000` → no clouds). Set them in the dimension or every biome. Biome values override dimension values (§2).

## 2. Biome JSON (`data/awaken/worldgen/biome/<name>.json`)

Vanilla biomes are shipped as JSON in `[data]worldgen/biome/` (and generated by `[mc]data/worldgen/biome/OverworldBiomes.java` etc.).
Codec (NeoForge-patched): `Biome.DIRECT_CODEC` = `ClimateSettings` + `attributes` (`EnvironmentAttributeMap.CODEC_ONLY_POSITIONAL`) + `effects` + `BiomeGenerationSettings`. Src: `[mc]world/level/biome/Biome.java`, `BiomeSpecialEffects.java`, `BiomeGenerationSettings.java`

```json
{
  "has_precipitation": false,
  "temperature": 0.5,
  "temperature_modifier": "none",
  "downfall": 0.0,
  "effects": {
    "water_color": "#3f76e4",
    "foliage_color": "#59ae30", "dry_foliage_color": "#7b5334", "grass_color": "#7fb238",
    "grass_color_modifier": "none"
  },
  "attributes": {
    "minecraft:visual/sky_color": "#9fb8ff",
    "minecraft:visual/fog_color": "#a0a8c0",
    "minecraft:visual/water_fog_color": "#050533",
    "minecraft:visual/water_fog_end_distance": { "modifier": "multiply", "argument": 0.85 },
    "minecraft:visual/ambient_particles": {
      "modifier": "append",
      "argument": [ { "particle": { "type": "minecraft:white_ash" }, "probability": 0.01 } ]
    },
    "minecraft:audio/background_music": {
      "default": { "sound": "minecraft:music.overworld.deep_dark", "min_delay": 12000, "max_delay": 24000 }
    },
    "minecraft:audio/ambient_sounds": {
      "loop": "minecraft:ambient.basalt_deltas.loop",
      "mood": { "sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0 },
      "additions": { "sound": "minecraft:ambient.basalt_deltas.additions", "tick_chance": 0.0111 }
    },
    "minecraft:gameplay/natural_mob_spawns": {
      "modifier": "overlay",
      "argument": { "spawn_costs": {}, "spawns_by_category": { "monster": [ { "type": "minecraft:zombie", "count": 4, "weight": 100 } ] } }
    }
  },
  "carvers": [],
  "features": []
}
```
Required: `has_precipitation`, `temperature`, `downfall`, `effects.water_color`, `carvers`, `features` (list of 11 step lists; shorter or `[]` is fine).
`background_music` keys: `default`, `creative`, `underwater`; each `{sound, min_delay, max_delay, replace_current_music?}`.
`ambient_sounds`: `loop?`, `mood?`, `additions?` (one object or a list). Colours: `"#rrggbb"`, ARGB attributes `"#aarrggbb"` (ints also accepted).

**Entry format**: a bare value = `override`; otherwise `{"modifier": <id>, "argument": <value>}`. Modifier ids
(`[mc]world/attribute/modifier/AttributeModifier.java`): `override`, `alpha_blend`, `add`, `subtract`, `multiply`, `blend_to_gray`,
`minimum`, `maximum`, `and`, `nand`, `or`, `nor`, `xor`, `xnor`, `append` (lists), `overlay` (mob spawns). Each attribute type allows a subset.

**Biomes may only contain positional attributes**: `gameplay/sky_light_level` and `gameplay/fast_lava` are `notPositional()` → dimension only (codec error otherwise).

### Attribute keys (`net.minecraft.world.attribute.EnvironmentAttributes`, all `minecraft:` namespace)
Src: `[mc]world/attribute/EnvironmentAttributes.java`
- visual (RGB unless noted): `visual/fog_color` (def #000000), `visual/fog_start_distance` (float 0), `visual/fog_end_distance` (1024),
  `visual/sky_fog_end_distance` (512), `visual/cloud_fog_end_distance` (2048), `visual/water_fog_color`, `visual/water_fog_start_distance` (-8),
  `visual/water_fog_end_distance` (96), `visual/sky_color` (#000000), `visual/sunrise_sunset_color` (ARGB), `visual/cloud_color` (ARGB, transparent),
  `visual/cloud_height` (192.33), `visual/sun_angle` / `visual/moon_angle` / `visual/star_angle` (degrees), `visual/moon_phase` (enum, e.g. `"full_moon"`),
  `visual/star_brightness` (0..1), `visual/block_light_tint`, `visual/sky_light_color` (#ffffff), `visual/sky_light_factor` (0..1, def 1),
  `visual/night_vision_color`, `visual/ambient_light_color` (#000000), `visual/default_dripstone_particle` (particle), `visual/ambient_particles` (list)
- audio: `audio/background_music`, `audio/music_volume` (0..1), `audio/ambient_sounds`, `audio/firefly_bush_sounds` (bool)
- gameplay: `gameplay/sky_light_level` (0..15, def 15, dimension-only), `gameplay/can_start_raid`, `gameplay/water_evaporates`, `gameplay/bed_rule`,
  `gameplay/straw_bed_rule`, `gameplay/respawn_anchor_works`, `gameplay/nether_portal_spawns_piglin`, `gameplay/fast_lava` (dimension-only),
  `gameplay/increased_fire_burnout`, `gameplay/eyeblossom_open` (tri-state), `gameplay/turtle_egg_hatch_chance`, `gameplay/piglins_zombify`,
  `gameplay/snow_golem_melts`, `gameplay/creaking_active`, `gameplay/surface_slime_spawn_chance`, `gameplay/cat_waking_up_gift_chance`,
  `gameplay/bees_stay_in_hive`, `gameplay/monsters_burn`, `gameplay/can_pillager_patrol_spawn`, `gameplay/natural_mob_spawns`,
  `gameplay/creature_world_gen_spawn_probability` (def 0.1), `gameplay/villager_activity`, `gameplay/baby_villager_activity`
- NeoForge (`net.neoforged.neoforge.common.world.NeoForgeEnvironmentAttributes`, Identifier-valued, client renderers registered in
  `RegisterCustomEnvironmentEffectRendererEvent#registerSkyboxRenderer/registerCloudRenderer/registerWeatherEffectRenderer`):
  `neoforge:custom_skybox`, `neoforge:custom_clouds`, `neoforge:custom_weather_effects` (default `minecraft:default`). Src: `[neo]common/world/NeoForgeEnvironmentAttributes.java`, `[neo]client/event/RegisterCustomEnvironmentEffectRendererEvent.java`

### Layer order (who wins)
`EnvironmentAttributeSystem.addDefaultLayers(level)` stacks, in this order, each layer receiving the previous result:
1. **dimension** `attributes` (constant base on top of the attribute default)
2. **biome** `attributes` (positional; spatially interpolated between biomes for colours/distances)
3. **timelines** of the dimension (time-based, in tag order; e.g. `day.json` `multiply`s sky_color toward black at night)
4. **weather** (rain/thunder lerp; only if `canHaveWeather()`). Client adds lightning flash after that.

So: weather > timeline > biome > dimension. An `override` in a biome replaces the dimension value; a timeline `multiply` then scales it.
Read values: `level.environmentAttributes().getValue(EnvironmentAttributes.FOG_COLOR, pos)` (Vec3 or BlockPos) /
`getDimensionValue(attr)` for non-positional. Src: `[mc]world/attribute/EnvironmentAttributeSystem.java`, `EnvironmentAttributeReader.java`

### Java builder (datagen / bootstrap)
```java
Biome biome = new Biome.BiomeBuilder()
    .hasPrecipitation(false).temperature(0.5F).downfall(0.0F)
    .setAttribute(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(0x9FB8FF))
    .setAttribute(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(0xA0A8C0))
    .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_DEEP_DARK))
    .modifyAttribute(EnvironmentAttributes.AMBIENT_PARTICLES, ListModifier.append(), AmbientParticle.of(ParticleTypes.WHITE_ASH, 0.01F))
    .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
    .mobSpawnSettings(MobSpawnSettings.EMPTY)                 // = modifyAttribute(NATURAL_MOB_SPAWNS, overlay(), ...)
    .generationSettings(BiomeGenerationSettings.EMPTY)
    .build();                                                 // throws if temperature/downfall/effects/generation missing
```
Pattern copied from `[mc]data/worldgen/biome/NetherBiomes.java` (`basaltDeltas`). A custom NeoForge `BiomeModifier` can edit attributes through
`builder.getAttributes()` (`EnvironmentAttributeMapBuilder`); NeoForge ships no stock attribute modifier. Src: `[neo]common/world/ModifiableBiomeInfo.java`

## 3. A fully custom ChunkGenerator

`public abstract class net.minecraft.world.level.chunk.ChunkGenerator`. Constructors: `ChunkGenerator(BiomeSource)`,
`ChunkGenerator(BiomeSource, Function<Holder<Biome>, BiomeGenerationSettings>)`. Field `protected final BiomeSource biomeSource`.
Src: `[mc]world/level/chunk/ChunkGenerator.java`, templates `[mc]world/level/levelgen/FlatLevelSource.java`, `DebugLevelSource.java`

Abstract methods (exact):
```java
protected abstract MapCodec<? extends ChunkGenerator> codec();
public abstract CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
        StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes);
public abstract void spawnOriginalMobs(WorldGenRegion worldGenRegion);
public abstract int getGenDepth();
public abstract int getSeaLevel();
public abstract int getMinY();
public abstract int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState);
public abstract NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState);
public abstract void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext);
```
Useful overridables: `getSpawnHeight(LevelHeightAccessor)` (def 64), `getOrigin(RandomState)` (def `ChunkPos.ZERO`), `createState(HolderLookup<StructureSet>, RandomState, long)`,
`createBiomes(...)`, `applyBiomeDecoration(WorldGenLevel, ChunkAccess, StructureManager)` (features + structure pieces, FEATURES step), `getMobsAt(...)`.
Types: `Blender` = `net.minecraft.world.level.levelgen.blending.Blender`, `WorldGenRegion` = `net.minecraft.server.level.WorldGenRegion`,
`SamplerContext` = `net.minecraft.world.level.levelgen.densityfunction.SamplerContext`, `NoiseColumn` = `net.minecraft.world.level.NoiseColumn`,
`StructureManager` = `net.minecraft.world.level.StructureManager`, `RandomState` = `net.minecraft.world.level.levelgen.RandomState`.
Pipeline (`[mc]world/level/chunk/status/ChunkPyramid.java`): `EMPTY → STRUCTURE_STARTS → STRUCTURE_REFERENCES → BIOMES → TERRAIN (buildTerrain, write radius 0)
→ FEATURES (applyBiomeDecoration, write radius 1) → INITIALIZE_LIGHT → LIGHT → SPAWN (spawnOriginalMobs) → FULL`. After `buildTerrain`
the task primes `MOTION_BLOCKING`, `MOTION_BLOCKING_NO_LEAVES`, `OCEAN_FLOOR`, `WORLD_SURFACE` itself (`ChunkStatusTasks.buildTerrain`); you only keep the `_WG` ones updated.

Threading: generation tasks run on the worldgen dispatcher (worker threads, many chunks in parallel). `buildTerrain` may work inline and
return `CompletableFuture.completedFuture(chunk)` (Flat) or offload with `CompletableFuture.supplyAsync(..., Util.backgroundExecutor().forName("..."))`
(NoiseBased). Never touch `ServerLevel`, SavedData, or non-thread-safe shared state; `getBaseHeight`/`getBaseColumn` are also called from
structure-placement threads and `/locate`. Keep generator fields immutable; per-chunk randoms must be created per call (§10).

```java
package dev.awaken.gates.world;

public final class CellChunkGenerator extends ChunkGenerator {
    public static final MapCodec<CellChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            BlockState.CODEC.fieldOf("floor").forGetter(g -> g.floor),
            Codec.INT.fieldOf("floor_y").forGetter(g -> g.floorY)
        ).apply(i, i.stable(CellChunkGenerator::new)));

    private final BlockState floor;
    private final int floorY;

    public CellChunkGenerator(BiomeSource biomeSource, BlockState floor, int floorY) {
        super(biomeSource);
        this.floor = floor;
        this.floorY = floorY;
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
            StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion,
            Set<Holder<Biome>> possibleBiomes) {
        if (chunk.isOutsideBuildHeight(this.floorY)) return CompletableFuture.completedFuture(chunk);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(this.floorY));
        int localY = SectionPos.sectionRelative(this.floorY);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                section.setBlockState(x, localY, z, this.floor, false);   // false = skip ThreadingDetector
                oceanFloor.update(x, this.floorY, z, this.floor);
                worldSurface.update(x, this.floorY, z, this.floor);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public void spawnOriginalMobs(WorldGenRegion region) { }
    @Override public int getGenDepth() { return 384; }   // = dimension_type height
    @Override public int getMinY() { return -64; }       // = dimension_type min_y
    @Override public int getSeaLevel() { return -64; }
    @Override public int getSpawnHeight(LevelHeightAccessor level) { return this.floorY + 1; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return type.isOpaque().test(this.floor) ? this.floorY + 1 : level.getMinY();   // first free Y, like FlatLevelSource
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        column[this.floorY - level.getMinY()] = this.floor;
        return new NoiseColumn(level.getMinY(), column);
    }

    @Override public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext ctx) { }

    // Optional: no structure sets at all in this dimension (default = every set whose biomes the source can produce).
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.getOrigin(randomState), this.biomeSource, Stream.empty());
    }
}
```
Setting blocks in `buildTerrain`: the chunk is a `ProtoChunk`. Fastest is `LevelChunkSection#setBlockState(int lx, int ly, int lz, BlockState, boolean checkThreading)`
(local 0..15 coords; keeps block counts). `ChunkAccess#setBlockState(BlockPos, BlockState, @Block.UpdateFlags int)` also works (flags ignored for proto
chunks). Heightmaps: `chunk.getOrCreateHeightmapUnprimed(type).update(lx, y, lz, state)`. Blocks with block entities / loot: place them in an
`applyBiomeDecoration` override via `WorldGenLevel#setBlock(pos, state, 2)` (WorldGenRegion creates the block entity), within the 3×3-chunk window.
`getMinY()`/`getGenDepth()` feed `WorldGenerationContext` (VerticalAnchor `above_bottom`/`below_top`) as `max(level min, gen min)` / `min(level height, gen depth)`.

Register the codec and reference it:
```java
public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, "awaken");
public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<CellChunkGenerator>> CELLS =
        CHUNK_GENERATORS.register("cells", () -> CellChunkGenerator.CODEC);
// in module init: CHUNK_GENERATORS.register(modBus);
```
`data/awaken/dimension/gates.json`:
```json
{
  "type": "awaken:gates",
  "generator": {
    "type": "awaken:cells",
    "biome_source": { "type": "minecraft:fixed", "biome": "awaken:gate_void" },
    "floor": "minecraft:bedrock",
    "floor_y": 0
  }
}
```
(`BlockState.CODEC` accepts `"minecraft:bedrock"` or `{"id": "...", "properties": {...}}`. Src: `[mc]world/level/block/state/BlockState.java`, `StateHolder.java`)

## 4. Custom BiomeSource

`public abstract class net.minecraft.world.level.biome.BiomeSource`. Src: `[mc]world/level/biome/BiomeSource.java`, `FixedBiomeSource.java`, `CheckerboardColumnBiomeSource.java`
```java
protected abstract MapCodec<? extends BiomeSource> codec();
protected abstract Stream<Holder<Biome>> collectPossibleBiomes();     // memoized into possibleBiomes()
public abstract BiomeResolver createResolver(Climate.Sampler sampler);
// optional: createResolverForChunk(Climate.Sampler, minQX, minQY, minQZ, sizeQX, sizeQY, sizeQZ), addDebugInfo(List<String>, BlockPos, Climate.Sampler)
@FunctionalInterface interface BiomeResolver { Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ); }   // quart = block >> 2
```
Built-in types (`BiomeSources`): `minecraft:fixed` `{"biome": id}`, `minecraft:multi_noise` (`preset` or `biomes`), `minecraft:checkerboard`
`{"biomes": [...], "scale": 2}`, `minecraft:the_end`. Single fixed biome:
```json
"biome_source": { "type": "minecraft:fixed", "biome": "awaken:gate_void" }
```
Code-driven per-position source (e.g. one theme biome per 512-block cell):
```java
public final class CellBiomeSource extends BiomeSource {
    public static final MapCodec<CellBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes),      // every biome the map may return
            Biome.CODEC.fieldOf("fallback").forGetter(s -> s.fallback)
        ).apply(i, CellBiomeSource::new));
    /** Written on the server thread before the cell generates, read by worldgen threads. */
    public static final ConcurrentHashMap<Integer, Holder<Biome>> CELL_BIOME = new ConcurrentHashMap<>();

    private final HolderSet<Biome> biomes;
    private final Holder<Biome> fallback;
    public CellBiomeSource(HolderSet<Biome> biomes, Holder<Biome> fallback) { this.biomes = biomes; this.fallback = fallback; }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return Stream.concat(this.biomes.stream(), Stream.of(this.fallback)); }
    @Override public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (qx, qy, qz) -> {
            int cell = Math.floorDiv(QuartPos.toBlock(qx), 1024) + 64 * Math.floorDiv(QuartPos.toBlock(qz), 1024);
            return CELL_BIOME.getOrDefault(cell, this.fallback);
        };
    }
}
```
Register with `DeferredRegister.create(Registries.BIOME_SOURCE, "awaken")` (`MapCodec<? extends BiomeSource>`), JSON `"type": "awaken:cells"`.
Rules: the resolver is called concurrently from worldgen threads (and `/locate`, structure checks) → only immutable or concurrent data;
every returned holder must be in `possibleBiomes()` (features, structure sets and the feature-order sorter are built from it once, at construction);
biomes are baked into each chunk at the `BIOMES` step and saved, so changing the map later only affects chunks not yet generated.

## 5. Structure templates at runtime

`StructureTemplateManager` (`server.getStructureTemplateManager()` or `serverLevel.getStructureTemplateManager()`). Src:
`[mc]world/level/levelgen/structure/templatesystem/StructureTemplateManager.java`, `StructureTemplate.java`, `StructurePlaceSettings.java`
```java
StructureTemplate getOrCreate(Identifier id);        // cached or new empty template registered under id
Optional<StructureTemplate> get(Identifier id);      // sources: <world>/generated/<ns>/structure/<path>.nbt, then datapacks data/<ns>/structure/<path>.nbt
boolean save(Identifier id);                         // writes <world>/generated/<ns>/structure/<path>.nbt (gzipped NBT)
static boolean save(Path file, StructureTemplate t, boolean asText) throws IOException;
void remove(Identifier id);                          // drop from cache
Stream<Identifier> listTemplates();
```
`StructureTemplate`:
```java
public void fillFromWorld(Level level, BlockPos position, Vec3i size, boolean inludeEntities, List<Block> ignoreBlocks);
public boolean placeInWorld(ServerLevelAccessor level, BlockPos position, BlockPos referencePos, StructurePlaceSettings settings,
                            RandomSource random, @Block.UpdateFlags int updateMode);
public CompoundTag save(CompoundTag tag);            // adds DataVersion
public void load(HolderGetter<Block> blockLookup, CompoundTag tag);
public Vec3i getSize();  public Vec3i getSize(Rotation);  public BoundingBox getBoundingBox(StructurePlaceSettings, BlockPos);
```
`fillFromWorld` captures air too (the 3rd arg means *include* entities, players excluded). Pass `List.of(Blocks.STRUCTURE_VOID)` like the
structure block, or add `Blocks.AIR` to skip air. Runs on the server thread; reads load chunks synchronously.

`StructurePlaceSettings` (fluent): `setMirror(Mirror)`, `setRotation(Rotation)`, `setRotationPivot(BlockPos)`, `setIgnoreEntities(boolean)`,
`setBoundingBox(BoundingBox)`, `setRandom(RandomSource)`, `setLiquidSettings(LiquidSettings.APPLY_WATERLOGGING|IGNORE_WATERLOGGING)`,
`setKnownShape(boolean)`, `addProcessor(StructureProcessor)` (e.g. `BlockIgnoreProcessor.AIR`, `.STRUCTURE_BLOCK`, `.STRUCTURE_AND_AIR`),
`setFinalizeEntities(boolean)`. Defaults: no rotation/mirror, entities placed, apply waterlogging.

Capture → own file → rebuild elsewhere (Lucidium snapshots):
```java
StructureTemplate t = new StructureTemplate();
t.fillFromWorld(level, corner, new Vec3i(16, 12, 16), false, List.of(Blocks.STRUCTURE_VOID));
NbtIo.writeCompressed(t.save(new CompoundTag()), file);                         // java.nio.file.Path

CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), tag, NbtUtils.getDataVersion(tag, 500)); // only needed across MC versions
StructureTemplate copy = new StructureTemplate();
copy.load(level.holderLookup(Registries.BLOCK), tag);
copy.placeInWorld(target, pos, pos, new StructurePlaceSettings().setIgnoreEntities(true), target.getRandom(),
        Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
```
Bulk block writes into a live `ServerLevel` (`Level#setBlock(BlockPos, BlockState, int flags)`; constants in `[mc]world/level/block/Block.java`):
`UPDATE_NEIGHBORS 1`, `UPDATE_CLIENTS 2`, `UPDATE_INVISIBLE 4`, `UPDATE_IMMEDIATE 8`, `UPDATE_KNOWN_SHAPE 16`, `UPDATE_SUPPRESS_DROPS 32`,
`UPDATE_MOVE_BY_PISTON 64`, `UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE 128`, `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS 256`, `UPDATE_SKIP_ON_PLACE 512`;
`UPDATE_ALL 3`, `UPDATE_NONE 260`, `UPDATE_SKIP_ALL_SIDEEFFECTS 816` (16|32|256|512). Use `Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS`
(= 818, the structure block's "strict" mode) for rebuilds. No flag skips lighting: `LevelChunk#setBlockState` always queues
`ThreadedLevelLightEngine#checkBlock` (processed off-thread); main-thread cost comes from neighbour/shape updates and `onPlace`, which 818 skips.
Spread very large rebuilds over several ticks, and prefer generating whole cells in the ChunkGenerator before players arrive.

## 6. Mod structures in the overworld

### (a) Rare surface ruin: jigsaw structure + structure_set + template_pool
Template: `data/awaken/structure/ruin/ruin_1.nbt`.
`data/awaken/worldgen/template_pool/ruin/start.json` (`StructureTemplatePool`: `elements`, `fallback` required):
```json
{
  "fallback": "minecraft:empty",
  "elements": [
    { "weight": 1, "element": { "element_type": "minecraft:single_pool_element",
      "location": "awaken:ruin/ruin_1", "processors": { "processors": [] }, "projection": "rigid" } }
  ]
}
```
`data/awaken/worldgen/structure/ruin.json` (fields from `[data]worldgen/structure/trail_ruins.json`, codecs `JigsawStructure`, `Structure.StructureSettings`):
```json
{
  "type": "minecraft:jigsaw",
  "biomes": "#minecraft:is_overworld",
  "step": "surface_structures",
  "spawn_overrides": {},
  "terrain_adaptation": "beard_thin",
  "start_pool": "awaken:ruin/start",
  "size": 1,
  "start_height": { "absolute": 0 },
  "project_start_to_heightmap": "WORLD_SURFACE_WG",
  "max_distance_from_center": 80,
  "use_expansion_hack": false
}
```
`data/awaken/worldgen/structure_set/ruins.json`:
```json
{
  "structures": [ { "structure": "awaken:ruin", "weight": 1 } ],
  "placement": { "type": "minecraft:random_spread", "spacing": 48, "separation": 16, "salt": 1650423781 }
}
```
Field notes: `size` 0..20 (jigsaw depth), `max_distance_from_center` 1..128 (or `{"horizontal", "vertical"}`), optional `start_jigsaw_name`,
`pool_aliases`, `dimension_padding`, `liquid_settings`; `terrain_adaptation`: `none|bury|beard_thin|beard_box|encapsulate`; `projection`: `rigid|terrain_matching`;
element types `single_pool_element`, `legacy_single_pool_element`, `list_pool_element`, `feature_pool_element`, `empty_pool_element`.
`random_spread`: `spacing`/`separation` in chunks (spacing > separation), `salt` required, optional `spread_type` (`linear`/`triangular`),
`frequency` (0..1), `frequency_reduction_method` (`default`, `legacy_type_1..3`), `locate_offset`, `exclusion_zone {other_set, chunk_count}`.
Src: `[mc]world/level/levelgen/structure/structures/JigsawStructure.java`, `pools/StructureTemplatePool.java`, `pools/SinglePoolElement.java`,
`placement/AbstractSpreadingStructurePlacement.java`, `placement/RandomSpreadStructurePlacement.java`

### (b) Big landmark near spawn
Placement types (`[mc]world/level/levelgen/structure/placement/StructurePlacements.java`): `random_spread`, `concentric_rings`, `dimension_origin`.
- `{"type": "minecraft:dimension_origin"}` (unit codec, no fields): exactly one start, in `ChunkGenerator#getOrigin(randomState)`, the same chunk
  `MinecraftServer.setInitialSpawn` starts its spawn search from (spawn then lands within ±5 chunks). Unused by vanilla data.
- `concentric_rings` (`distance`, `spread`, `count`, `preferred_biomes`, + `salt` etc.) rings are centred on chunk 0,0, not on spawn
  (overworld spawn can be up to ~2048 blocks from 0,0: `NoiseSpawnFinder.MAX_RADIUS`).
- "400–700 blocks from spawn" (the Well): register a custom placement whose single chunk is derived from the dimension origin:
```java
public record SpawnRingPlacement(int minDistance, int maxDistance, int salt) implements StructurePlacement {
    public static final MapCodec<SpawnRingPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("min_distance").forGetter(SpawnRingPlacement::minDistance),
            Codec.INT.fieldOf("max_distance").forGetter(SpawnRingPlacement::maxDistance),
            Codec.INT.fieldOf("salt").forGetter(SpawnRingPlacement::salt)
        ).apply(i, SpawnRingPlacement::new));

    public ChunkPos target(ChunkGeneratorStructureState state) {
        RandomSource r = new XoroshiroRandomSource(state.getLevelSeed() ^ this.salt);
        double angle = r.nextDouble() * Math.PI * 2.0;
        double dist = this.minDistance + r.nextDouble() * (this.maxDistance - this.minDistance);
        ChunkPos o = state.getDimensionOrigin();
        return new ChunkPos(o.x() + (int) Math.round(Math.cos(angle) * dist / 16.0), o.z() + (int) Math.round(Math.sin(angle) * dist / 16.0));
    }
    @Override public boolean isStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        ChunkPos t = this.target(state);
        return t.x() == chunkX && t.z() == chunkZ;
    }
    @Override public MapCodec<? extends StructurePlacement> codec() { return CODEC; }
}
// DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, "awaken").register("spawn_ring", () -> SpawnRingPlacement.CODEC)
```
`"placement": {"type": "awaken:spawn_ring", "min_distance": 400, "max_distance": 700, "salt": 77031}`. Gotchas: `/locate` and maps only
search `concentric_rings` and `random_spread` (`ChunkGenerator#findNearestMapStructure`), so store the Well's position yourself; the start is
silently skipped if the start biome is not in the structure's `biomes`, so use a broad tag or a structure type that tolerates water.
Fallback that always works: place the template at runtime once (SavedData flag) with `StructureTemplate#placeInWorld` after loading the chunks (§7).

### (c) Small decoration via a Feature + NeoForge biome modifier
NeoForge biome modifier: `data/awaken/neoforge/biome_modifier/<name>.json` (registry `neoforge:biome_modifier`; non-vanilla registry
dirs are prefixed with their namespace, `CommonHooks.prefixNamespace`). Stock types (`[neo]common/NeoForgeMod.java`): `neoforge:add_features`,
`remove_features`, `add_spawns`, `remove_spawns`, `add_carvers`, `remove_carvers`, `add_spawn_costs`, `remove_spawn_costs`.
```json
{
  "type": "neoforge:add_features",
  "biomes": "#minecraft:is_overworld",
  "features": "awaken:small_ruin",
  "step": "surface_structures"
}
```
(`biomes`/`features` accept id, list or `#tag`; `step` = `GenerationStep.Decoration`: `raw_generation`, `lakes`, `local_modifications`,
`underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`,
`vegetal_decoration`, `top_layer_modification`. `remove_features` takes optional `steps`.) Src: `[neo]common/world/BiomeModifiers.java`

`data/awaken/worldgen/placed_feature/small_ruin.json` (shape from `[data]worldgen/placed_feature/desert_well.json`):
```json
{
  "feature": "awaken:small_ruin",
  "placement": [
    { "type": "minecraft:rarity_filter", "chance": 600 },
    { "type": "minecraft:in_square" },
    { "type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG" },
    { "type": "minecraft:biome" }
  ]
}
```
`data/awaken/worldgen/feature/small_ruin.json` (vanilla `minecraft:template` feature, `[mc]world/level/levelgen/feature/TemplateFeature.java`):
```json
{
  "type": "minecraft:template",
  "templates": [ { "data": { "id": "awaken:ruin/small_1", "rotations": ["none", "clockwise_90", "180", "counterclockwise_90"] }, "weight": 1 } ]
}
```
`TemplateFeature` centres the template on the origin in X/Z and places with flags 3; optional `processors`. Features may only write inside the
3×3 chunks around the decorated chunk, so keep feature templates ≤ 32 blocks wide; anything bigger must be a structure (pieces are clipped per chunk).
Custom feature code: `interface Feature { MapCodec<? extends Feature> codec(); boolean place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos); }`,
codec registered in `Registries.FEATURE_TYPE`. Src: `[mc]world/level/levelgen/feature/Feature.java`

## 7. Chunk loading and tickets

`record TicketType(long timeout, int flags, boolean forceNaturalSpawning)` (+ ctor `(timeout, flags)`), registry `Registries.TICKET_TYPE`.
Flags: `FLAG_PERSIST 1` (saved in `chunk_tickets.dat`), `FLAG_LOADING 2`, `FLAG_SIMULATION 4`, `FLAG_KEEP_DIMENSION_ACTIVE 8`, `FLAG_CAN_EXPIRE_IF_UNLOADED 16`.
Vanilla: `PLAYER_SPAWN(20, 2)`, `SPAWN_SEARCH(1, 2)`, `DRAGON(0, 6)`, `PLAYER_LOADING(0, 2)`, `PLAYER_SIMULATION(0, 12)`, `FORCED(0, 15)`,
`PORTAL(300, 15)`, `ENDER_PEARL(40, 14)`, `UNKNOWN(1, 18)`. Timeouts count down only once the chunk is loaded (unless flag 16).
Src: `[mc]server/level/TicketType.java`, `Ticket.java`, `[mc]world/level/TicketStorage.java`, `[mc]server/level/ChunkLevel.java`

`ServerChunkCache` (`level.getChunkSource()`), Src `[mc]server/level/ServerChunkCache.java`:
```java
void addTicketWithRadius(TicketType type, ChunkPos pos, int radius);       // level = 33 - radius
void removeTicketWithRadius(TicketType type, ChunkPos pos, int radius);    // must match type AND radius
CompletableFuture<?> addTicketAndLoadWithRadius(TicketType type, ChunkPos pos, int radius); // type must load, must not have flag 16
void addTicket(Ticket ticket, ChunkPos pos);                               // new Ticket(TicketType, int level)
boolean updateChunkForced(ChunkPos pos, boolean forced);                   // FORCED ticket at ENTITY_TICKING (31)
```
`ServerLevel#setChunkForced(int chunkX, int chunkZ, boolean forced)` = `/forceload` (shared with the command; anyone can remove it).
Levels: 33 = FULL (loaded, not ticking), 32 = block ticking, 31 = entity ticking. Radius 2 → centre entity-ticking, ring 1 block-ticking, ring 2 loaded.
Re-adding an identical ticket resets its timeout (`TicketStorage#addTicket` → `resetTicksLeft`).
A dimension with no players stops ticking entities after 300 ticks unless some ticket has `FLAG_KEEP_DIMENSION_ACTIVE` or NeoForge forced chunks exist
(`ServerLevel` tick, `emptyTime`).

Keep chunks alive around a non-player entity (ender-pearl pattern, `ServerPlayer.placeEnderPearlTicket`):
```java
public static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, "awaken");
public static final DeferredHolder<TicketType, TicketType> BODY = TICKETS.register("veil_body",
        () -> new TicketType(60L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
// Body entity, server side, every 20 ticks (old chunks expire 60 ticks after the entity leaves them):
if (this.level() instanceof ServerLevel sl && this.tickCount % 20 == 0) {
    sl.getChunkSource().addTicketWithRadius(BODY.get(), this.chunkPosition(), 2);
}
```
Persistent, owner-tracked alternative (survives restarts, validated on load): NeoForge `TicketController`.
```java
public static final TicketController BODY_TICKETS = new TicketController(Identifier.fromNamespaceAndPath("awaken", "veil_body"),
        (level, helper) -> { /* helper.getEntityTickets(), helper.removeAllTickets(UUID) for stale owners */ });
modBus.addListener((RegisterTicketControllersEvent e) -> e.register(BODY_TICKETS));
BODY_TICKETS.forceChunk(serverLevel, bodyEntity, chunkX, chunkZ, true /*add*/, true /*forceNaturalSpawning/ticking*/);
```
Signatures: `forceChunk(ServerLevel, BlockPos owner, int, int, boolean add, boolean forceNaturalSpawning)`, `(ServerLevel, Entity owner, ...)`,
`(ServerLevel, UUID owner, ...)`. Unregistered controllers throw. Src: `[neo]common/world/chunk/TicketController.java`, `RegisterTicketControllersEvent.java`

## 8. Teleporting between dimensions

```java
public record TeleportTransition(ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot,
        boolean missingRespawnBlock, boolean asPassenger, Set<Relative> relatives, TeleportTransition.PostTeleportTransition postTeleportTransition)
// ctors: (ServerLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, PostTeleportTransition)
//        (ServerLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, Set<Relative>, PostTeleportTransition)
// PostTeleportTransition constants: DO_NOTHING, PLAY_PORTAL_SOUND, PLACE_PORTAL_TICKET; combine with .then(...)
// helpers: withRotation(y, x), withPosition(Vec3), transitionAsPassenger(), static createDefault(ServerPlayer, post), missingRespawnBlock(ServerPlayer, post)
```
`ServerPlayer#teleport(TeleportTransition)` → `@Nullable ServerPlayer`; `Entity#teleport(TeleportTransition)` → `@Nullable Entity`
(cross-dimension returns a NEW entity instance; keep the returned reference). Both return null when cancelled by NeoForge
`EntityTravelToDimensionEvent` (`CommonHooks.onTravelToDimension`). Non-passenger teleports dismount. Alternative:
`teleportTo(ServerLevel, double x, double y, double z, Set<Relative>, float yRot, float xRot, boolean resetCamera)`.
`Relative` = `net.minecraft.world.entity.Relative`. Src: `[mc]world/level/portal/TeleportTransition.java`, `[mc]server/level/ServerPlayer.java:1176`, `[mc]world/entity/Entity.java:3177`

Safe Y:
```java
ServerLevel dest = server.getLevel(GATES);
dest.getChunk(x >> 4, z >> 4);   // REQUIRED: Level#getHeight returns getMinY() for unloaded chunks; this loads/generates to FULL synchronously
int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);   // first free Y above the top block (+1 already applied)
if (y <= dest.getMinY()) { /* empty column: build a platform */ }
player.teleport(new TeleportTransition(dest, new Vec3(x + 0.5, y, z + 0.5), Vec3.ZERO, player.getYRot(), player.getXRot(),
        TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
```
Without stalling the tick: `dest.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, ChunkPos.containing(target), 1)
.thenRunAsync(() -> { ...height + teleport... }, server);` (MinecraftServer is an Executor). Without loading at all (estimate from the generator):
`dest.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, dest, dest.getChunkSource().randomState())`.
Heightmap types: `WORLD_SURFACE_WG`, `WORLD_SURFACE`, `OCEAN_FLOOR_WG`, `OCEAN_FLOOR`, `MOTION_BLOCKING` (block tag `blocks_motion_in_heightmap` or fluid),
`MOTION_BLOCKING_NO_LEAVES`. Other helpers: `getHeightmapPos(type, BlockPos)`, `level.getRespawnData().pos()` (world spawn). Src: `[mc]world/level/Level.java:349`, `[mc]world/level/levelgen/Heightmap.java`

## 9. World height limits

`DimensionType` constants: `BITS_FOR_Y = BlockPos.PACKED_Y_LENGTH` = 12, `Y_SIZE` = 4096 − 32 = **4064**, `MAX_Y` = **2031**, `MIN_Y` = **−2032**, `MIN_HEIGHT` = 16.
Constructor checks (throw `IllegalStateException`): `height >= 16`; `min_y + height <= 2032`; `logical_height <= height`; `height % 16 == 0`;
`min_y % 16 == 0`. Codec ranges: `min_y` [−2032, 2031], `height` [16, 4064], `logical_height` [0, 4064]. Same limits as 1.21.x.
Sections: `LevelHeightAccessor#getSectionsCount()`, `getMinSectionY()`, `getSectionIndex(blockY)`, `getMaxY()` = `minY + height − 1`.
Src: `[mc]world/level/dimension/DimensionType.java`, `[mc]core/BlockPos.java:47`, `[mc]world/level/LevelHeightAccessor.java`

## 10. Noise and randoms for a custom generator

Package `net.minecraft.world.level.levelgen.synth`. Src: files of the same names.
- `interface Noise { Interval range(); float get(double x, double y); float get(double x, double y, double z); }` (returns **float** now)
- `SimplexNoise extends GradientNoise`: `new SimplexNoise(RandomSource)`, `new SimplexNoise(RandomSource, boolean discardNoiseOffset)`; 2D ≈ [−1, 1].
- `PerlinNoise extends GradientNoise`: `new PerlinNoise(RandomSource)` (single octave, old `ImprovedNoise`); `noiseWithDerivative(x, y, z, float[] out)`.
- `NoiseStack` (fractal sum, replaces `PerlinSimplexNoise`/octave Perlin): `NoiseStack.builder().add(Noise, double frequency, float amplitude)....build()`.
- `NormalNoise` (private ctor): `NormalNoise.builder().setBaseOctave(int /*freq = 2^n*/).setOctaveCount(int).setBaseAmplitude(double)
  .setAmplitudeModifier(int index, double).setNormalize(boolean).build()` or `NormalNoise.createParity(int firstOctave, double... amplitudes)`,
  then `Noise n = normalNoise.create(RandomSource)`. Registered vanilla noises: `randomState.getOrCreateNoise(ResourceKey<NormalNoise>)` (keys in `Noises`).

Example (as vanilla `Biome.FROZEN_TEMPERATURE_NOISE`):
```java
WorldgenRandom r = new WorldgenRandom(new XoroshiroRandomSource(seed));
Noise terrain = NoiseStack.builder()
        .add(new SimplexNoise(r), 1.0 / 256, 1.0F)
        .add(new SimplexNoise(r), 1.0 / 128, 0.5F)
        .add(new SimplexNoise(r), 1.0 / 64, 0.25F)
        .build();
float h = terrain.get(blockX, blockZ);
```
Randoms (`net.minecraft.world.level.levelgen`): `new XoroshiroRandomSource(long)` / `(long lo, long hi)`, `new LegacyRandomSource(long)`,
`new WorldgenRandom(RandomSource)` with `setDecorationSeed(long seed, int minBlockX, int minBlockZ)` (returns long), `setFeatureSeed(long, int index, int step)`,
`setLargeFeatureSeed(long seed, int chunkX, int chunkZ)`; `RandomSource.create(long)` = `LegacyRandomSource`. `forkPositional()` →
`PositionalRandomFactory` with `at(x, y, z)`, `at(BlockPos)`, `fromHashOf(String|Identifier)`. RandomSource instances are single-thread
(`LegacyRandomSource` throws on concurrent use): create them per chunk/call. Built noise objects are immutable after construction → safe to share.

World seed inside a generator:
- `buildTerrain` / `getBaseHeight` / `getBaseColumn` receive `RandomState`: `randomState.seed()` (public, `@Deprecated`),
  `randomState.getOrCreateRandomFactory(Identifier)` (cached `PositionalRandomFactory` derived from the seed).
- `applyBiomeDecoration(WorldGenLevel level, ...)`: `level.getSeed()`; `spawnOriginalMobs(WorldGenRegion r)`: `r.getSeed()`; `createState(..., long levelSeed)`.
- For non-noise generators `ChunkMap` builds `RandomState.create(noises, level.getSeed(), false, STONE, 63, NoiseRouterData.none())`; the seed honours
  `neoforge:seed_override`. Src: `[mc]server/level/ChunkMap.java:178`, `[mc]world/level/levelgen/RandomState.java`

The generator is decoded before the seed is known, so build seed-dependent noise lazily and cache it immutably:
```java
private record Noises(long seed, Noise terrain) { }
private volatile Noises noises;   // benign race: worst case built twice
private Noises noises(RandomState rs) {
    Noises n = this.noises;
    if (n == null || n.seed() != rs.seed()) {
        RandomSource r = rs.getOrCreateRandomFactory(Identifier.fromNamespaceAndPath("awaken", "cells")).fromHashOf("terrain");
        this.noises = n = new Noises(rs.seed(), new SimplexNoise(r));
    }
    return n;
}
```

## 11. Region files and per-dimension storage

- One region file = 32×32 chunks = 512×512 blocks: `ChunkPos.REGION_SIZE = 32`, `getRegionX() = x >> 5`, slot index `localX + localZ * 32`,
  name `"r." + regionX + "." + regionZ + ".mca"` (`RegionFileStorage.ANVIL_EXTENSION`), 4096-byte sectors.
  Src: `[mc]world/level/ChunkPos.java:37`, `[mc]world/level/chunk/storage/RegionFileStorage.java:36`, `RegionFile.java`
- Folder: `DimensionType.getStorageFolder(ResourceKey<Level>, Path worldRoot)` = `<world>/dimensions/<namespace>/<path>`; ChunkMap uses
  `.../region`, `.../poi` and `.../entities` (each with its own `r.X.Z.mca`), plus `.../data` for per-dimension SavedData.
  `awaken:gates` → `<world>/dimensions/awaken/gates/{region,entities,poi}/r.X.Z.mca`. The overworld is now `<world>/dimensions/minecraft/overworld`
  (old `region/`, `DIM-1/`, `DIM1/` are migrated by `util/filefix/fixes/DimensionStorageFileFix.java`).
  Src: `[mc]world/level/dimension/DimensionType.java#getStorageFolder`, `[mc]server/level/ChunkMap.java:170`, `[mc]server/level/ServerLevel.java:258`
- World root: `server.getWorldPath(LevelResource.ROOT)`.

Gates cells (origin `((i mod 64)·1024, (i div 64)·1024)`, 512×512) map to exactly one region: `rx = 2·(i mod 64)`, `rz = 2·(i div 64)`.
```java
Path dim = DimensionType.getStorageFolder(GATES, server.getWorldPath(LevelResource.ROOT));
for (String sub : List.of("region", "entities", "poi")) {
    Files.deleteIfExists(dim.resolve(sub).resolve("r." + rx + "." + rz + ".mca"));
}
```
When it is safe: only while the level is closed. `RegionFileStorage` keeps up to 256 open `RegionFile` handles (and caches "missing"),
`ChunkMap` keeps loaded/pending chunks in memory and the IOWorker queues writes, so deleting under a running level resurrects or corrupts data.
Hooks: `LevelEvent.Unload` (posted right after `level.close()` during `MinecraftServer.stopServer`, check `((ServerLevel) e.getLevel()).dimension()`),
`ServerStoppedEvent` (after all levels closed), or `ServerAboutToStartEvent` (before `loadLevel()`, good for crash cleanup).
(Single-chunk wipe exists only internally: `RegionFileStorage#write(pos, null)` → `RegionFile#clear`.) Src: `[mc]server/MinecraftServer.java:633-690`, `[mc]server/dedicated/DedicatedServer.java:246`
