# AWAKEN core API cheat sheet: Minecraft 26.3 / NeoForge 26.3.0.95

Every signature below was checked against the real sources. Path prefixes used in "Src:" lines:

- `[mc]` = `/Volumes/MEDIA/Awaken/.ref/src/net/minecraft/`
- `[neo]` = `/Volumes/MEDIA/Awaken/.ref/neo/net/neoforged/neoforge/`
- `[data]` = `/Volumes/MEDIA/Awaken/.ref/assets/data/minecraft/`, `[assets]` = `/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/`
- `[fml]` = `loader-12.0.12.jar`, `[bus]` = `bus-8.0.5.jar` (both under `/Volumes/MEDIA/.gradle-home/caches/modules-2/files-2.1/`, read with `javap`; neither has sources)

## Renames you will hit first (vs 1.21.1)

| 1.21.x | 26.3 |
|---|---|
| `ResourceLocation` | `net.minecraft.resources.Identifier` |
| `ResourceKey#location()` | `ResourceKey#identifier()` |
| `ResourceLocationArgument` | `net.minecraft.commands.arguments.IdentifierArgument` (`id()`, `getId(ctx,name)`) |
| `level.isClientSide` (field) | `level.isClientSide()` (the field is now private) |
| `entity.getServer()` | removed. Use `serverPlayer.level().getServer()` (`ServerPlayer#level()` returns `ServerLevel`) or `ServerLifecycleHooks.getCurrentServer()` |
| `Entity#hurt(src, amt)` | `hurtServer(ServerLevel, DamageSource, float)` / `hurtOrSimulate(DamageSource, float)` |
| `Player#displayClientMessage(c, true)` | `sendOverlayMessage(Component)` or `sendSystemMessage(Component, boolean overlay)` |
| `BlockEvent.BreakEvent` | `net.neoforged.neoforge.event.level.block.BreakBlockEvent` |
| `AddReloadListenerEvent` | `net.neoforged.neoforge.event.AddServerReloadListenersEvent` |
| `DataPackRegistryEvent.NewRegistry` | `net.neoforged.neoforge.registries.NewDatapackRegistryEvent` |
| `ModConfig.Type.COMMON / SERVER` | `ModConfig.Type.LOCAL / SYNCED` (also `CLIENT`, `STARTUP`) |
| `BlockEntityType.Builder.of(...)` | gone: `new BlockEntityType<>(factory, blocks...)` |
| `Block#codec()` | gone: no `codec()` on `Block` / `BaseEntityBlock` anymore |
| NBT `save/load(CompoundTag, Provider)` | `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` |
| `SavedData.Factory` + `computeIfAbsent(factory, name)` | `SavedDataType<T>` + `computeIfAbsent(TYPE)` |
| `Commands#hasPermission(int)` / `source.hasPermission(2)` | `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| `@GameTestHolder` / `@GameTest` / `RegisterGameTestsEvent#register(Class)` | data-driven `test_function` + `test_instance` registries |
| Loot JSON `"conditions": [...]`, `"functions": [...]` | `"condition"` and `"modifier"` (see section 15) |

---

## 1. Mod entry and buses

- `net.neoforged.fml.common.Mod`: `String value()`, `Dist[] dist() default {CLIENT, DEDICATED_SERVER}`, `String[] depends() default {}`. Src: `[fml] net/neoforged/fml/common/Mod.class`
- Constructor params are injected by type. Allowed: `IEventBus` (the mod bus), `ModContainer`, `FMLModContainer`, `Dist`, in any order. Src: `[fml] FMLModContainer.class` (matches on `getParameterTypes`).
- `net.neoforged.fml.common.EventBusSubscriber`: only `Dist[] value()` and `String modid()`. **No `bus` parameter anymore.** Each `@SubscribeEvent` method is routed on its own: events implementing `net.neoforged.fml.event.IModBusEvent` go to the mod bus, everything else to `NeoForge.EVENT_BUS`. Methods **must be static** and take exactly one Event parameter. Src: `[fml] AutomaticEventSubscriber.class` (strings "Subscribing method {} to the event bus of mod {}", "...is not static").
- Game bus: `net.neoforged.neoforge.common.NeoForge.EVENT_BUS` (`IEventBus`). Src: `[neo]common/NeoForge.java:17`
- `net.neoforged.bus.api.IEventBus`: `register(Object)`, `<T extends Event> addListener(Consumer<T>)`, `addListener(Class<T>, Consumer<T>)`, `addListener(EventPriority, Consumer<T>)`, `addListener(EventPriority, boolean receiveCanceled, Consumer<T>)`, `post(T)`. Src: `[bus] IEventBus.class`
- Lifecycle (mod bus): `net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent`, `FMLClientSetupEvent`, `FMLDedicatedServerSetupEvent`, `FMLLoadCompleteEvent`. Src: `[fml]`

```java
@Mod(Awaken.MODID)
public final class Awaken {
    public static final String MODID = "awaken";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MODID, path); }

    public Awaken(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);              // every DeferredRegister: .register(modBus)
        ModItems.ITEMS.register(modBus);
        modBus.addListener(AwakenNet::register);         // RegisterPayloadHandlersEvent is a mod-bus event
        NeoForge.EVENT_BUS.addListener(AwakenCommands::register); // RegisterCommandsEvent is a game event
        container.registerConfig(ModConfig.Type.SYNCED, AwakenConfig.SPEC);
    }
}

@Mod(value = Awaken.MODID, dist = Dist.CLIENT)            // net.neoforged.api.distmarker.Dist
public final class AwakenClient {
    public AwakenClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}

@EventBusSubscriber(modid = Awaken.MODID)                  // add value = Dist.CLIENT for client-only classes
public final class GameEvents {
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) { /* game bus */ }
    @SubscribeEvent
    public static void onAttrs(EntityAttributeModificationEvent e) { /* mod bus, routed automatically */ }
}
```
`IConfigScreenFactory#createScreen(ModContainer, Screen)`, `ConfigurationScreen(ModContainer, Screen)`. Src: `[neo]client/gui/IConfigScreenFactory.java:26`, `[neo]client/gui/ConfigurationScreen.java:260`

Gotcha: if you wire listeners by hand with `addListener`, you must pick the right bus yourself. Only `@EventBusSubscriber` routes for you.

## 2. Identifier, ResourceKey, Registries

`net.minecraft.resources.Identifier` (Src: `[mc]resources/Identifier.java`):
- `static Identifier fromNamespaceAndPath(String ns, String path)`, `parse(String)`, `withDefaultNamespace(String)`, `@Nullable tryParse(String)`, `@Nullable tryBuild(ns, path)`
- `getNamespace()`, `getPath()`, `withPath(..)`, `withPrefix(..)`, `withSuffix(..)`, `toLanguageKey(String prefix)`, `Identifier.CODEC`, `Identifier.STREAM_CODEC`

`net.minecraft.resources.ResourceKey` (Src: `[mc]resources/ResourceKey.java`): `static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> reg, Identifier id)`, `createRegistryKey(Identifier)`, `codec(regKey)`, `streamCodec(regKey)`, `identifier()`, `registry()`.

`net.minecraft.core.registries.Registries` (Src: `[mc]core/registries/Registries.java`): `BLOCK`, `ITEM`, `BLOCK_ENTITY_TYPE`, `ENTITY_TYPE`, `SOUND_EVENT`, `PARTICLE_TYPE`, `MENU`, `CREATIVE_MODE_TAB`, `DATA_COMPONENT_TYPE`, `ATTRIBUTE`, `MOB_EFFECT`, `POTION`, `RECIPE_SERIALIZER`, `CHUNK_GENERATOR` (`Registry<MapCodec<? extends ChunkGenerator>>`), `BIOME_SOURCE` (`Registry<MapCodec<? extends BiomeSource>>`), `DAMAGE_TYPE`, `DIMENSION` (`Registry<Level>`), `LEVEL_STEM`, `DIMENSION_TYPE`, `BIOME`, `STRUCTURE`, `LOOT_TABLE`, `ENCHANTMENT`, `GAME_RULE`, `TEST_FUNCTION` (`Registry<Consumer<GameTestHelper>>`), `TEST_INSTANCE`, `TEST_ENVIRONMENT`, `TEST_INSTANCE_TYPE`.

```java
ResourceKey<Level> SANCTUM = ResourceKey.create(Registries.DIMENSION, Awaken.id("sanctum"));
```

## 3. DeferredRegister

`net.neoforged.neoforge.registries.DeferredRegister<T>` (Src: `[neo]registries/DeferredRegister.java`):
- `create(ResourceKey<? extends Registry<T>> key, String ns)`, `create(Registry<T>, String)`, `createBlocks(String)`, `createItems(String)`, `createEntities(String)`, `createDataComponents(ResourceKey<Registry<DataComponentType<?>>>, String)`
- `register(String name, Supplier<? extends I>)`, `register(String name, Function<Identifier, ? extends I>)` returning `DeferredHolder<T, I>`; `register(IEventBus)`; `getEntries()`; `createTagKey(String)`; `makeRegistry(Consumer<RegistryBuilder<T>>)`
- `DeferredHolder<R,T> extends Holder.Reference<R> implements Supplier<T>`: `get()`, `value()`, `getId()`, `key()` (use this: `Holder#getKey()` is `@Deprecated(forRemoval, since 26.3)`, see `[neo]common/extensions/IHolderExtension.java:42-44`), `is(TagKey)`. It is a `Holder<R>`, so pass it straight to APIs taking `Holder<Attribute>` / `Holder<MobEffect>`. Src: `[neo]registries/DeferredHolder.java:32`
- `DeferredBlock`/`DeferredItem`: `get()`, `asItem()`, `toStack()`, `toStack(int)`.
- Registration order is patched so `ATTRIBUTE` and `DATA_COMPONENT_TYPE` register before items and effects, so `Item.Properties.component(MY_COMPONENT.get(), v)` inside an item factory is safe. Src: `[neo]registries/GameData.java:129-130`

```java
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    // registerBlock calls props.setId(ResourceKey.create(BLOCK, id)) for you
    public static final DeferredBlock<Block> GATE_STONE = BLOCKS.registerSimpleBlock("gate_stone",
            p -> p.strength(3f, 9f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<AltarBlock> ALTAR = BLOCKS.registerBlock("altar", AltarBlock::new,
            p -> p.strength(4f).noOcclusion().lightLevel(s -> 10));
}
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    public static final DeferredItem<Item> MANA_SHARD = ITEMS.registerSimpleItem("mana_shard", p -> p.stacksTo(16));
    public static final DeferredItem<SigilItem> SIGIL = ITEMS.registerItem("sigil", SigilItem::new, p -> p.stacksTo(1));
    // sets useBlockDescriptionPrefix() for you
    public static final DeferredItem<BlockItem> ALTAR_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.ALTAR);
}
```
Overloads: `registerSimpleBlock(String)`, `(String, Supplier<Properties>)`, `(String, UnaryOperator<Properties>)`; `registerBlock(String, Function<Properties, B>)`, `(.., Supplier<Properties>)`, `(.., UnaryOperator<Properties>)`; the same three shapes for `registerSimpleItem` / `registerItem`; `registerSimpleBlockItem(Holder<Block>)`, `(Holder<Block>, UnaryOperator<Item.Properties>)`, `(String, Supplier<? extends Block>)`, `(String, Supplier<? extends Block>, UnaryOperator<Item.Properties>)`.

**Block entities.** There is no Builder in 26.3. Constructors are `BlockEntityType(BlockEntitySupplier<? extends T>, Block... blocks)`, `(factory, Set<Block>)` and `(factory, boolean onlyOpCanSetNbt, Block...)`. `BlockEntitySupplier#create(BlockPos, BlockState)`. Src: `[mc]world/level/block/entity/BlockEntityType.java:20-36,74`
```java
public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
public static final Supplier<BlockEntityType<AltarBlockEntity>> ALTAR_BE = BLOCK_ENTITIES.register("altar",
        () -> new BlockEntityType<>(AltarBlockEntity::new, ModBlocks.ALTAR.get()));
```

**Entity types.** Use `EntityType.Builder.of(EntityFactory<T>, MobCategory)` and finish with `.build(ResourceKey<EntityType<?>>)`; the key is required. `EntityFactory#create(EntityType<T>, Level)`. Builder methods: `sized(w,h)`, `eyeHeight`, `clientTrackingRange(int)`, `updateInterval(int)`, `fireImmune()`, `noSummon()`, `noSave()`, `noLootTable()`, `notInPeaceful()`. Src: `[mc]world/entity/EntityType.java:543-715`
```java
public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
public static final DeferredHolder<EntityType<?>, EntityType<ShadeEntity>> SHADE = ENTITIES.registerEntityType(
        "shade", ShadeEntity::new, MobCategory.MONSTER, b -> b.sized(0.6f, 1.95f).clientTrackingRange(8));
// Attributes (mod bus): EntityAttributeCreationEvent#put(EntityType<? extends LivingEntity>, AttributeSupplier)
@SubscribeEvent public static void attrs(EntityAttributeCreationEvent e) { e.put(SHADE.get(), ShadeEntity.createAttributes().build()); }
```
Src: `[neo]registries/DeferredRegister.java:813-845`, `[neo]event/entity/EntityAttributeCreationEvent.java:30`

**Sounds.** `record SoundEvent(Identifier location, Optional<Float> fixedRange)`, `static createVariableRangeEvent(Identifier)`, `createFixedRangeEvent(Identifier, float)`. Src: `[mc]sounds/SoundEvent.java:15-39`
```java
public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
public static final DeferredHolder<SoundEvent, SoundEvent> GATE_OPEN = SOUNDS.register("gate_open", SoundEvent::createVariableRangeEvent); // Function<Identifier,..>
```

**Particles.** `new SimpleParticleType(boolean overrideLimiter)` is public. Src: `[mc]core/particles/SimpleParticleType.java:11`. Client side: `RegisterParticleProvidersEvent#registerSpriteSet(ParticleType<T>, ParticleResources.SpriteParticleRegistration<T>)` / `registerSpecial(...)`. Src: `[neo]client/event/RegisterParticleProvidersEvent.java:49,65`
```java
public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MANA_SPARK = PARTICLES.register("mana_spark", () -> new SimpleParticleType(false));
```

**Menus.** `MenuType(MenuSupplier<T>, FeatureFlagSet)` with `MenuSupplier#create(int, Inventory)`. With extra data, use `IMenuTypeExtension.create(IContainerFactory<T>)`, where `IContainerFactory#create(int windowId, Inventory, RegistryFriendlyByteBuf)`. Open it with `player.openMenu(MenuProvider, BlockPos)` or `openMenu(provider, Consumer<RegistryFriendlyByteBuf>)`. Client: `RegisterMenuScreensEvent`. Src: `[mc]world/inventory/MenuType.java:50,72`, `[neo]common/extensions/IMenuTypeExtension.java:26`, `[neo]network/IContainerFactory.java:34`, `[neo]common/extensions/IPlayerExtension.java:59,76`
```java
public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Awaken.MODID);
public static final Supplier<MenuType<AltarMenu>> ALTAR_MENU = MENUS.register("altar", () -> IMenuTypeExtension.create(AltarMenu::new));
```

**Creative tabs.** `CreativeModeTab.builder()` then `.title(Component)`, `.icon(Supplier<ItemStack>)`, `.displayItems((ItemDisplayParameters p, Output out) -> out.accept(ItemLike))`, `.withTabsBefore(Identifier...)`, `.build()`. To add to vanilla tabs, use `BuildCreativeModeTabContentsEvent` (mod bus; implements `CreativeModeTab.Output`; `getTabKey()` returns `ResourceKey<CreativeModeTab>`, compare with `CreativeModeTabs.COMBAT` etc.). Src: `[mc]world/item/CreativeModeTab.java:69,193-317,386-396`, `[neo]event/BuildCreativeModeTabContentsEvent.java:25,47`
```java
public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Awaken.MODID);
public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.awaken.main"))
        .icon(() -> ModItems.SIGIL.toStack())
        .displayItems((params, out) -> { out.accept(ModItems.SIGIL); out.accept(ModItems.ALTAR_ITEM); })
        .build());
```

**Data components.** Use `DataComponentType.<T>builder()`, then `.persistent(Codec<T>)`, `.networkSynchronized(StreamCodec<? super RegistryFriendlyByteBuf, T>)`, `.cacheEncoding()`, `.build()`. Read and write with `stack.get(type)`, `stack.getOrDefault(type, d)`, `stack.set(type, v)`. These take `DataComponentType`, not a Supplier, so call `.get()`. Src: `[mc]core/component/DataComponentType.java:26-78`, `[mc]world/item/ItemStack.java:834`, `[mc]core/component/DataComponentHolder.java:12,25`
```java
public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE = COMPONENTS.registerComponentType(
        "charge", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
```

**Attributes.** `new RangedAttribute(String descriptionId, double def, double min, double max)`; `Attribute#setSyncable(boolean)` returns `Attribute`. To attach one to the player (mod bus), use `EntityAttributeModificationEvent#add(EntityType<? extends LivingEntity>, Holder<Attribute>)` or `add(type, holder, double)`. Src: `[mc]world/entity/ai/attributes/RangedAttribute.java:9`, `Attribute.java:35`, `[neo]event/entity/EntityAttributeModificationEvent.java:43-49`
```java
public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Awaken.MODID);
public static final DeferredHolder<Attribute, Attribute> MAX_MANA = ATTRIBUTES.register("max_mana",
        () -> new RangedAttribute("attribute.awaken.max_mana", 100, 0, 100000).setSyncable(true));
@SubscribeEvent public static void addAttrs(EntityAttributeModificationEvent e) { e.add(EntityType.PLAYER, MAX_MANA); }
```

**Mob effects.** All `MobEffect` constructors are **protected** (`(MobEffectCategory, int color)`, `(.., ParticleOptions)`), so subclass it. Override `boolean applyEffectTick(ServerLevel, LivingEntity, int amp)` and `boolean shouldApplyEffectTickThisTick(int tickCount, int amp)`. `addAttributeModifier(Holder<Attribute>, Identifier id, double amount, AttributeModifier.Operation)`. Apply with `living.addEffect(new MobEffectInstance(Holder<MobEffect>, int duration, int amp))`. Src: `[mc]world/effect/MobEffect.java:56-159`, `MobEffectInstance.java:49-57`
```java
public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Awaken.MODID);
public static final DeferredHolder<MobEffect, MobEffect> FOCUS = EFFECTS.register("focus", () -> new FocusEffect()
        .addAttributeModifier(Attributes.ATTACK_DAMAGE, Awaken.id("effect.focus"), 2.0, AttributeModifier.Operation.ADD_VALUE));
```

**Chunk generators and biome sources.** The registry value is the `MapCodec`, not the generator. `ChunkGenerator#codec()` is `protected abstract MapCodec<? extends ChunkGenerator>`; same for `BiomeSource#codec()`. Abstract methods in 26.3: `buildTerrain(ChunkAccess, Blender, RandomState, StructureManager, BiomeManager, @Nullable WorldGenRegion, Set<Holder<Biome>>)` returning `CompletableFuture<ChunkAccess>`, `spawnOriginalMobs(WorldGenRegion)`, `getGenDepth()`, `getSeaLevel()`, `getMinY()`, `getBaseHeight(int,int,Heightmap.Types,LevelHeightAccessor,RandomState)`, `getBaseColumn(int,int,LevelHeightAccessor,RandomState)`, `addDebugScreenInfo(List<String>, RandomState, BlockPos, SamplerContext)`. Constructor: `ChunkGenerator(BiomeSource)`. Src: `[mc]world/level/chunk/ChunkGenerator.java:97-699`, `[mc]world/level/biome/BiomeSource.java:37-156`, vanilla registration in `[mc]world/level/chunk/ChunkGenerators.java:10-13`
```java
public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);
public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<SanctumGenerator>> SANCTUM_GEN = CHUNK_GENS.register("sanctum", () -> SanctumGenerator.CODEC);
public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Awaken.MODID);
```
JSON `"type": "awaken:sanctum"` in `data/awaken/dimension/*.json` then resolves to it.

## 4. Blocks, items, block entities

- `BlockBehaviour.Properties.of()`, `ofFullCopy(BlockBehaviour)`, `strength(f)`, `strength(f,f)`, `sound(SoundType)`, `lightLevel(ToIntFunction<BlockState>)`, `noOcclusion()`, `noCollision()`, `requiresCorrectToolForDrops()`, `randomTicks()`, `noLootTable()`, `mapColor(..)`, **`setId(ResourceKey<Block>)` is required.** Without it you crash with "Block id not set". `DeferredRegister.Blocks.registerBlock/registerSimpleBlock` call it for you. If you build `Properties` yourself (e.g. `new Block(...)` inside `register(name, key -> ...)`), call `setId` yourself. Src: `[mc]world/level/block/state/BlockBehaviour.java:1045-1313,1190`
- `Item.Properties`: `stacksTo`, `durability`, `rarity`, `fireResistant`, `food(FoodProperties)`, `component(DataComponentType<T>, T)`, `equippable(EquipmentSlot)`, `attributes(ItemAttributeModifiers)`, `useBlockDescriptionPrefix()`, **`setId(ResourceKey<Item>)` is required** ("Item id not set"). `Items.registerItem/registerSimpleItem` handle it. Src: `[mc]world/item/Item.java:439-753`
- Item hooks (Src: `[mc]world/item/Item.java:197-361`):
  - `InteractionResult use(Level, Player, InteractionHand)` (no more `InteractionResultHolder<ItemStack>`)
  - `InteractionResult useOn(UseOnContext)`
  - `ItemStack finishUsingItem(ItemStack, Level, LivingEntity)`
  - `void inventoryTick(ItemStack, ServerLevel, Entity, @Nullable EquipmentSlot)` (server only now)
  - `int getUseDuration(ItemStack, LivingEntity)`, `ItemUseAnimation getUseAnimation(ItemStack)`
  - `boolean releaseUsing(ItemStack, Level, LivingEntity, int)` (now returns boolean)
  - `void appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`
- `InteractionResult` is a sealed interface: `SUCCESS` (client predicted swing), `SUCCESS_SERVER`, `CONSUME` (no swing), `FAIL`, `PASS`, `TRY_WITH_EMPTY_HAND` (only from `useItemOn`, falls through to `useWithoutItem`). `Success#heldItemTransformedTo(ItemStack)` replaces the held item. Src: `[mc]world/InteractionResult.java:11-66`
- Block hooks (all `protected` in `BlockBehaviour`; Src: `[mc]world/level/block/state/BlockBehaviour.java:156-326`, `Block.java:463-542`):
  - `InteractionResult useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)`
  - `InteractionResult useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` (default `TRY_WITH_EMPTY_HAND`)
  - `VoxelShape getShape(BlockState, BlockGetter, BlockPos, CollisionContext)`, `void tick(BlockState, ServerLevel, BlockPos, RandomSource)`, `randomTick(...)`, `onPlace(BlockState, Level, BlockPos, BlockState old, boolean)`, `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)` (replaces `onRemove`)
  - `public BlockState playerWillDestroy(Level, BlockPos, BlockState, Player)`, `public @Nullable BlockState getStateForPlacement(BlockPlaceContext)`, `protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState>)`
- `EntityBlock`: `@Nullable BlockEntity newBlockEntity(BlockPos, BlockState)`, `default <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level, BlockState, BlockEntityType<T>)`. `BaseEntityBlock` gives `protected static createTickerHelper(BlockEntityType<A> actual, BlockEntityType<E> expected, BlockEntityTicker<? super E>)`. It does **not** override `getRenderShape`, so the model renders by default. Src: `[mc]world/level/block/EntityBlock.java`, `BaseEntityBlock.java:30`
- `BlockEntityTicker<T>#tick(Level, BlockPos, BlockState, T)`. Src: `[mc]world/level/block/entity/BlockEntityTicker.java:9`
- `BlockEntity(BlockEntityType<?>, BlockPos, BlockState)`; `protected void saveAdditional(ValueOutput)`, `protected void loadAdditional(ValueInput)`, `CompoundTag getUpdateTag(HolderLookup.Provider)`, `final CompoundTag saveWithoutMetadata(HolderLookup.Provider)`, `@Nullable Packet<ClientGamePacketListener> getUpdatePacket()`, `setChanged()`, `preRemoveSideEffects(BlockPos, BlockState)`. NeoForge adds `onDataPacket(Connection, ValueInput)`, `handleUpdateTag(ValueInput)`, `onLoad()`. Src: `[mc]world/level/block/entity/BlockEntity.java:62-255`, `[neo]common/extensions/IBlockEntityExtension.java:35-65`
- `net.minecraft.world.level.storage.ValueOutput`: `putInt/putLong/putBoolean/putString/putDouble/putFloat/putIntArray`, `store(String, Codec<T>, T)`, `storeNullable(..)`, `store(MapCodec<T>, T)`, `child(String)`, `list(String, Codec<T>)`, `childrenList(String)`. `ValueInput`: `getIntOr(name, def)`, `getInt(name)` (returns `Optional<Integer>`), `getLongOr`, `getBooleanOr`, `getStringOr`, `getDoubleOr`, `read(String, Codec<T>)` (returns `Optional<T>`), `read(MapCodec<T>)`, `child(..)`, `childOrEmpty(..)`, `listOrEmpty(String, Codec<T>)`, `lookup()`. Src: `[mc]world/level/storage/ValueInput.java`, `ValueOutput.java`
- Entities use the same pattern: `protected abstract void addAdditionalSaveData(ValueOutput)`, `readAdditionalSaveData(ValueInput)`, `defineSynchedData(SynchedEntityData.Builder)`. Src: `[mc]world/entity/Entity.java:428,2288-2290`

```java
public class AltarBlock extends BaseEntityBlock {
    public AltarBlock(BlockBehaviour.Properties p) { super(p); }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AltarBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ALTAR_BE.get(), AltarBlockEntity::serverTick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof AltarBlockEntity be) be.activate((ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }
}
public class AltarBlockEntity extends BlockEntity {
    private int charge;
    public AltarBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ALTAR_BE.get(), pos, state); }
    public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity be) { if (++be.charge % 20 == 0) be.setChanged(); }
    @Override protected void saveAdditional(ValueOutput out) { super.saveAdditional(out); out.putInt("charge", charge); }
    @Override protected void loadAdditional(ValueInput in) { super.loadAdditional(in); charge = in.getIntOr("charge", 0); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider regs) { return saveWithoutMetadata(regs); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    void activate(ServerPlayer p) { /* ... */ setChanged(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }
}
public class SigilItem extends Item {
    public SigilItem(Item.Properties p) { super(p); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel sl) { /* server work */ }
        return InteractionResult.SUCCESS;
    }
}
```
`Block.UPDATE_CLIENTS = 2`, `UPDATE_ALL = 3`, `Level#sendBlockUpdated(BlockPos, BlockState, BlockState, int)`. Src: `[mc]world/level/block/Block.java:92-103`, `[mc]world/level/Level.java:323`

## 5. Data attachments

`net.neoforged.neoforge.attachment.AttachmentType<T>` (Src: `[neo]attachment/AttachmentType.java:109-307`):
- `builder(Supplier<T> default)`, `builder(Function<IAttachmentHolder, T>)`, `serializable(Supplier<T extends ValueIOSerializable>)`
- `Builder#serialize(MapCodec<T>)` (**a MapCodec, not a Codec**, so wrap with `Codec.INT.fieldOf("v")` or use `RecordCodecBuilder.mapCodec`), `serialize(MapCodec<T>, Predicate<? super T> shouldSerialize)`, `serialize(IAttachmentSerializer<T>)`
- `copyOnDeath()`: **must come after `serialize(..)`**, otherwise `IllegalStateException("copyOnDeath requires a serializer")`. Without it, entity attachments are lost on death (but kept when returning from the End).
- `copyHandler(IAttachmentCopyHandler<T>)`, `sync(StreamCodec<? super RegistryFriendlyByteBuf, T>)` (to every client tracking the holder; for a player that also includes the player), `sync(BiPredicate<IAttachmentHolder, ServerPlayer> sendToPlayer, StreamCodec<..>)`, `sync(AttachmentSyncHandler<T>)`, `build()`
- Registry: `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`. Src: `[neo]registries/NeoForgeRegistries.java:58`
- `IAttachmentHolder` (Entity, BlockEntity, ChunkAccess, Level): `getData(AttachmentType<T>)` / `getData(Supplier<AttachmentType<T>>)` (creates and stores the default if absent), `getExistingData(..)` (returns `Optional<T>`), `hasData(..)`, `setData(.., T)`, `removeData(..)`, `syncData(..)`. Src: `[neo]attachment/IAttachmentHolder.java:24-134`
- **Sync rule:** `setData`, `removeData` and the first `getData` call `syncData` automatically. **Mutating the object returned by `getData` does not sync**: use immutable records with `setData`, or call `player.syncData(TYPE)` after mutating. Src: `[neo]attachment/AttachmentHolder.java:73-115`, `[neo]attachment/AttachmentSync.java:138-151`
- Block entities: call `setChanged()` after modifying; chunks: `markUnsaved()`. Entities save automatically.

```java
public record HunterData(int level, long xp) {
    public static final HunterData DEFAULT = new HunterData(1, 0L);
    public static final MapCodec<HunterData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("level").forGetter(HunterData::level),
            Codec.LONG.fieldOf("xp").forGetter(HunterData::xp)).apply(i, HunterData::new));
    public static final StreamCodec<ByteBuf, HunterData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HunterData::level, ByteBufCodecs.VAR_LONG, HunterData::xp, HunterData::new);
}
public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
public static final Supplier<AttachmentType<HunterData>> HUNTER = ATTACHMENTS.register("hunter", () -> AttachmentType
        .builder(() -> HunterData.DEFAULT)
        .serialize(HunterData.MAP_CODEC)
        .copyOnDeath()
        .sync((holder, to) -> holder == to, HunterData.STREAM_CODEC)   // only the owning player
        .build());

HunterData d = player.getData(ModAttachments.HUNTER);
player.setData(ModAttachments.HUNTER, new HunterData(d.level() + 1, 0));   // persists and syncs
```

## 6. Networking

- `net.minecraft.network.protocol.common.custom.CustomPacketPayload`: `Type<? extends CustomPacketPayload> type()`; `record Type<T>(Identifier id)`. Gotcha: `CustomPacketPayload.createType(String)` uses the **minecraft** namespace, so build `new Type<>(Awaken.id(..))` yourself. Src: `[mc]network/protocol/common/custom/CustomPacketPayload.java:14-79`
- `StreamCodec.composite(codec1, getter1, ..., constructor)` goes up to **14 fields** (the 14-field overload is the last). For more, nest records or write `StreamCodec.of(encoder, decoder)`. Src: `[mc]network/codec/StreamCodec.java:118-673`
- `net.minecraft.network.codec.ByteBufCodecs` (an interface): `BOOL, BYTE, SHORT, INT, VAR_INT, LONG, VAR_LONG, FLOAT, DOUBLE, STRING_UTF8, BYTE_ARRAY, COMPOUND_TAG, TAG, OPTIONAL_COMPOUND_TAG, GAME_PROFILE, RGB_COLOR, INSTANT`, plus `optional(c)`, `collection(IntFunction, c)`, `list()` (use with `.apply(ByteBufCodecs.list())`), `map(..)`, `fromCodec(Codec)`, `fromCodecWithRegistries(Codec)`, `registry(key)`, `holderRegistry(key)`, `idMapper(..)`. Others: `BlockPos.STREAM_CODEC`, `UUIDUtil.STREAM_CODEC`, `ComponentSerialization.STREAM_CODEC`, `ItemStack.OPTIONAL_STREAM_CODEC`, `Identifier.STREAM_CODEC`, NeoForge `NeoForgeStreamCodecs.enumCodec(Class)`, `CHUNK_POS`. Src: `[mc]network/codec/ByteBufCodecs.java`, `[neo]network/codec/NeoForgeStreamCodecs.java`
- `RegisterPayloadHandlersEvent` (mod bus) has `registrar(String version)`, which returns `PayloadRegistrar`: `playToClient(type, codec, handler)`, `playToClient(type, codec)` (handler then registered on the client via `RegisterClientPayloadHandlersEvent#register(type, handler)`), `playToServer(type, codec, handler)`, `playBidirectional(type, codec, serverHandler, clientHandler)`, `configurationToClient/ToServer(..)`, `executesOn(HandlerThread)`, `versioned(String)`, `optional()`. Src: `[neo]network/registration/PayloadRegistrar.java:31-209`, `[neo]client/network/event/RegisterClientPayloadHandlersEvent.java:25-37`
- **Threading:** the default `HandlerThread.MAIN` wraps handlers in `MainThreadPayloadHandler`, so **handlers already run on the game thread** and `enqueueWork` is not needed. Use `executesOn(HandlerThread.NETWORK)` to opt out, then call `IPayloadContext#enqueueWork(Runnable)` (returns `CompletableFuture<Void>`). Src: `[neo]network/registration/PayloadRegistrar.java:170-184`, `HandlerThread.java:21-26`
- `IPayloadContext`: `player()`, `reply(payload)`, `disconnect(Component)`, `enqueueWork(..)`, `flow()`, `connection()`. Src: `[neo]network/handling/IPayloadContext.java`
- Sending, with `net.neoforged.neoforge.network.PacketDistributor` (server): `sendToPlayer(ServerPlayer, payload, ...)`, `sendToPlayersInDimension(ServerLevel, ..)`, `sendToPlayersNear(ServerLevel, @Nullable ServerPlayer excluded, x, y, z, radius, ..)`, `sendToAllPlayers(..)`, `sendToPlayersTrackingEntity(Entity, ..)`, `sendToPlayersTrackingEntityAndSelf(Entity, ..)`, `sendToPlayersTrackingChunk(ServerLevel, ChunkPos, ..)`. Client to server: **`net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload, ...)`** (not in `PacketDistributor`). Src: `[neo]network/PacketDistributor.java:36-99`, `[neo]client/network/ClientPacketDistributor.java:22`

```java
public record CastSkillPayload(int slot) implements CustomPacketPayload {
    public static final Type<CastSkillPayload> TYPE = new Type<>(Awaken.id("cast_skill"));
    public static final StreamCodec<ByteBuf, CastSkillPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CastSkillPayload::slot, CastSkillPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
public static void register(RegisterPayloadHandlersEvent e) {          // modBus.addListener(AwakenNet::register)
    PayloadRegistrar r = e.registrar("1");
    r.playToServer(CastSkillPayload.TYPE, CastSkillPayload.STREAM_CODEC, (p, ctx) -> Skills.cast((ServerPlayer) ctx.player(), p.slot()));
    r.playToClient(StatusPayload.TYPE, StatusPayload.STREAM_CODEC);   // client handler registered below
}
@EventBusSubscriber(modid = Awaken.MODID, value = Dist.CLIENT)
public final class ClientNet {
    @SubscribeEvent public static void onRegister(RegisterClientPayloadHandlersEvent e) {
        e.register(StatusPayload.TYPE, (p, ctx) -> StatusHud.update(p));
    }
}
PacketDistributor.sendToPlayer(serverPlayer, new StatusPayload(...));
ClientPacketDistributor.sendToServer(new CastSkillPayload(0));
```

## 7. SavedData

- `abstract class SavedData { setDirty(); setDirty(boolean); isDirty(); }`. No `save(CompoundTag, Provider)` anymore: it is codec-driven. Src: `[mc]world/level/saveddata/SavedData.java`
- `record SavedDataType<T extends SavedData>(Identifier id, Factory<T> factory, Factory<Codec<T>> codecFactory, @Nullable DataFixTypes)`. Constructors: `(Identifier, Supplier<T>, Codec<T>)`, `(Identifier, Supplier<T>, Codec<T>, DataFixTypes)`, and a NeoForge `(Identifier, Factory<T>, Factory<Codec<T>>)` where `Factory#create(@Nullable ServerLevel)`. Pass `null`/omit the `DataFixTypes` for mod data. Src: `[mc]world/level/saveddata/SavedDataType.java:10-53`
- `net.minecraft.world.level.storage.SavedDataStorage`: `computeIfAbsent(SavedDataType<T>)`, `@Nullable get(type)`, `set(type, data)`. Src: `[mc]world/level/storage/SavedDataStorage.java:74-116`
- **Two stores:** `MinecraftServer#getDataStorage()` is world-global (`<world>/data/`, new in 26.x; vanilla weather lives here), and `ServerLevel#getDataStorage()` is per dimension (`<dim>/data/`). The file is `<data>/<namespace>/<path>.dat` (via `Identifier#resolveAgainst`). Src: `[mc]server/MinecraftServer.java:330,2167`, `[mc]server/level/ServerLevel.java:1611`, `[mc]server/level/ServerChunkCache.java:94-102`; vanilla model: `[mc]world/level/saveddata/WeatherData.java`

```java
public final class GateLedger extends SavedData {
    public static final Codec<GateLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("next_id").forGetter(g -> g.nextId)).apply(i, GateLedger::new));
    public static final SavedDataType<GateLedger> TYPE = new SavedDataType<>(Awaken.id("gates"), GateLedger::new, CODEC);
    private int nextId;
    public GateLedger() {}
    private GateLedger(int nextId) { this.nextId = nextId; }
    public int allocate() { setDirty(); return nextId++; }
    public static GateLedger get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
}
```

## 8. Commands

- `RegisterCommandsEvent` (game bus): `getDispatcher()` (returns `CommandDispatcher<CommandSourceStack>`), `getBuildContext()`, `getCommandSelection()`. Src: `[neo]event/RegisterCommandsEvent.java:36-50`
- `Commands.literal(String)`, `Commands.argument(String, ArgumentType<T>)`. Src: `[mc]commands/Commands.java:456-460`
- **Permissions (new):** `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`; constants are `LEVEL_ALL`, `LEVEL_MODERATORS`, `LEVEL_GAMEMASTERS`, `LEVEL_ADMINS`, `LEVEL_OWNERS` (type `net.minecraft.server.permissions.PermissionCheck`). In code: `source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` (`net.minecraft.server.permissions.Permissions`). `hasPermission(int)` is gone. Src: `[mc]commands/Commands.java:165-169,558`, `[mc]server/permissions/Permissions.java:6-9`, `PermissionSet.java:7`, usage `[mc]server/commands/TimeCommand.java:62`
- `CommandSourceStack`: `sendSuccess(Supplier<Component>, boolean broadcastToOps)`, `sendFailure(Component)`, `sendSystemMessage(Component)`, `getPlayerOrException()`, `@Nullable getPlayer()`, `getLevel()`, `getServer()`, `permissions()`. Src: `[mc]commands/CommandSourceStack.java:376-494`
- Args: `EntityArgument.player()` / `getPlayer(ctx, n)`, `DimensionArgument.dimension()` / `getDimension(ctx, n)` (returns `ServerLevel`), `IdentifierArgument.id()` / `getId(ctx, n)`, brigadier `IntegerArgumentType.integer(min,max)` / `getInteger(ctx, n)`. Src: `[mc]commands/arguments/EntityArgument.java:77-85`, `DimensionArgument.java:47-51`, `IdentifierArgument.java:15-19`

```java
public static void register(RegisterCommandsEvent e) {
    e.getDispatcher().register(Commands.literal("awaken")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(Commands.literal("setlevel")
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 999))
                    .executes(ctx -> {
                        ServerPlayer t = EntityArgument.getPlayer(ctx, "target");
                        int lvl = IntegerArgumentType.getInteger(ctx, "level");
                        t.setData(ModAttachments.HUNTER, new HunterData(lvl, 0));
                        ctx.getSource().sendSuccess(() -> Component.translatable("commands.awaken.setlevel", t.getDisplayName(), lvl), true);
                        return 1;
                    })))));
}
```

## 9. Config

- `net.neoforged.neoforge.common.ModConfigSpec`: `new ModConfigSpec.Builder()`, then `push(String)` / `pop()`, `comment(String...)`, `translation(String)`, `define(String, boolean)` (returns `BooleanValue`), `defineInRange(String, int, int, int)` (returns `IntValue`; double and long overloads exist), `defineEnum(String, E)`, `defineList(String, List, Supplier<T> newElement, Predicate<Object>)`, `build()`, `configure(Function<Builder,T>)` (returns `Pair<T, ModConfigSpec>`). Values: `get()`, `getAsInt()`, `getAsBoolean()`, `getAsDouble()`. Src: `[neo]common/ModConfigSpec.java:309-886,1195-1356`
- `net.neoforged.fml.config.ModConfig.Type` is **`LOCAL`, `CLIENT`, `SYNCED`, `STARTUP`. `COMMON` and `SERVER` no longer exist.**
  - `SYNCED` is the old SERVER: per-world, loaded at server start from `<world>/syncedconfig/`, synced to clients. NeoForge itself registers `SYNCED` + `LOCAL`.
  - `LOCAL` is the old COMMON: loaded at mod load from `config/`, not synced.
  - `STARTUP` is loaded very early, not synced.
  - The file name suffix is `type.name().toLowerCase()` (e.g. `awaken-synced.toml`). Treat that as an inference from `extension()`.
  - Src: `[fml] ModConfig$Type.class`, `[neo]common/NeoForgeMod.java:586-587`, `[neo]server/ServerLifecycleHooks.java:66,99`, `[neo]internal/CommonModLoader.java:62-64`
- `ModContainer#registerConfig(ModConfig.Type, IConfigSpec)` and `(.., String fileName)`. Reload events (mod bus): `net.neoforged.fml.event.config.ModConfigEvent.Loading` / `.Reloading` / `.Unloading`. Src: `[fml] ModContainer.class`, `[fml] ModConfigEvent*.class`
- `ModConfigSpec.RestartType.GAME` cannot be used on `SYNCED` configs. Src: `[neo]common/ModConfigSpec.java:1395-1402`

```java
public final class AwakenConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue GATE_INTERVAL;
    public static final ModConfigSpec.BooleanValue HARDCORE_GATES;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("gates");
        GATE_INTERVAL = b.comment("Ticks between gate spawn attempts").defineInRange("spawnInterval", 6000, 20, 1_000_000);
        HARDCORE_GATES = b.define("hardcore", false);
        b.pop();
        SPEC = b.build();
    }
}
// in @Mod ctor: container.registerConfig(ModConfig.Type.SYNCED, AwakenConfig.SPEC);
```

## 10. Common game events (all game bus unless noted)

| Need | Class (package `net.neoforged.neoforge.event.` ...) | Key methods |
|---|---|---|
| Player tick | `tick.PlayerTickEvent.Pre` / `.Post` | `getEntity()` (returns `Player`). Fires on **both sides**: check `player.level().isClientSide()` |
| Server tick | `tick.ServerTickEvent.Pre` / `.Post` | `getServer()`, `hasTime()` |
| Level tick | `tick.LevelTickEvent.Pre` / `.Post` | `getLevel()` (returns `Level`; check `instanceof ServerLevel`) |
| Entity tick | `tick.EntityTickEvent.Pre` / `.Post` | |
| Login / logout | `entity.player.PlayerEvent.PlayerLoggedInEvent` / `PlayerLoggedOutEvent` | `getEntity()` |
| Respawn | `PlayerEvent.PlayerRespawnEvent` | `isEndConquered()` |
| Clone | `PlayerEvent.Clone` | `getOriginal()`, `isWasDeath()`, `getEntity()` (the new player) |
| Dimension change | `PlayerEvent.PlayerChangedDimensionEvent` | `getFrom()`, `getTo()` (both `ResourceKey<Level>`) |
| Game mode change | `PlayerEvent.PlayerChangeGameModeEvent` (cancellable) | `getNewGameMode()`, `setNewGameMode(GameType)` |
| Death | `entity.living.LivingDeathEvent` (cancellable) | `getEntity()`, `getSource()` |
| Damage, before armor/i-frames | `entity.living.LivingIncomingDamageEvent` (cancellable) | `getSource()`, `getAmount()`, `setAmount(float)`, `getContainer()`, `addReductionModifier(..)`, `setInvulnerabilityTicks(int)` |
| Damage, after reductions | `entity.living.LivingDamageEvent.Pre` / `.Post` | Pre: `getNewDamage()`, `setNewDamage(float)`, `getSource()`; Post (read-only): `getOriginalDamage()`, `getInflictedDamage()`, `getHealthDamage()`, `getBlockedDamage()`, `getSource()` |
| Entity join | `entity.EntityJoinLevelEvent` (cancellable) | `getEntity()`, `getLevel()`, `loadedFromDisk()` |
| Block break | **`level.block.BreakBlockEvent`** (cancellable) | `getPlayer()`, `getLevel()`, `getPos()`, `getState()` |
| Right-click block / item | `entity.player.PlayerInteractEvent.RightClickBlock` / `RightClickItem` | `getPos()`, `getHand()`, `getItemStack()`, `getHitVec()`, `setUseBlock(TriState)`, `setCancellationResult(InteractionResult)` |
| Can sleep | `entity.player.CanPlayerSleepEvent` | `setProblem(@Nullable BedSleepingProblem)` |
| Keep sleeping | `entity.player.CanContinueSleepingEvent` | `mayContinueSleeping()`, `setContinueSleeping(boolean)` |
| Night skipped | `level.SleepFinishedTimeEvent` (cancellable) | **now `getAdjustment()` / `setAdjustment(ClockAdjustment)`** with `net.neoforged.neoforge.common.util.ClockAdjustment.Absolute(long)` / `.Relative(long)` / `.Marker(ResourceKey<ClockTimeMarker>)` (26.x world clocks; no more `setTimeAddition`) |
| Wake up | `entity.player.PlayerWakeUpEvent` | `wakeImmediately()`, `updateLevel()` |
| Server started | `server.ServerStartedEvent` (also `ServerStartingEvent`, `ServerStoppingEvent`) | `getServer()` |
| Reload listeners | **`AddServerReloadListenersEvent`** | `addListener(Identifier key, PreparableReloadListener)`, `addDependency(Identifier, Identifier)`; client: `client.event.AddClientReloadListenersEvent` |
| Datapack registry (mod bus) | **`net.neoforged.neoforge.registries.NewDatapackRegistryEvent`** | `worldRegistry(key, codec)` (unsynced), `worldRegistry(key, codec, networkCodec)` (synced), `reloadableRegistry(key, codec)` (reloads with /reload) |
| Commands | `RegisterCommandsEvent` | section 8 |

Src: `[neo]event/tick/*.java`, `[neo]event/entity/player/PlayerEvent.java:246-575`, `[neo]event/entity/living/LivingIncomingDamageEvent.java:28-84`, `LivingDamageEvent.java:48-167`, `LivingDeathEvent.java:32-40`, `[neo]event/entity/EntityJoinLevelEvent.java:30-55`, `[neo]event/level/block/BreakBlockEvent.java:31-43`, `[neo]event/entity/player/PlayerInteractEvent.java:115-189`, `[neo]event/level/SleepFinishedTimeEvent.java`, `[neo]common/util/ClockAdjustment.java`, `[neo]event/AddServerReloadListenersEvent.java`, `[neo]event/SortedReloadListenerEvent.java:62-85`, `[neo]registries/NewDatapackRegistryEvent.java:22-115`

Datapack registry JSON lives at `data/<pack_ns>/<registry_ns>/<registry_path>/`: a registry `awaken:gate_type` loads from `data/*/awaken/gate_type/*.json`.

Reload listener base: `SimpleJsonResourceReloadListener<T>(Codec<T>, FileToIdConverter.json("awaken_dir"))` with `apply(Map<Identifier,T>, ResourceManager, ProfilerFiller)`. Src: `[mc]server/packs/resources/SimpleJsonResourceReloadListener.java:26`, `SimplePreparableReloadListener.java:22-24`, `[mc]resources/FileToIdConverter.java:11`

```java
@SubscribeEvent public static void onSleep(SleepFinishedTimeEvent e) { e.setAdjustment(new ClockAdjustment.Relative(12000)); }
@SubscribeEvent public static void onHurt(LivingIncomingDamageEvent e) { if (e.getSource().is(DamageTypes.FALL)) e.setAmount(e.getAmount() * 0.5f); }
@SubscribeEvent public static void onRegs(NewDatapackRegistryEvent e) { e.worldRegistry(GATE_TYPE_KEY, GateType.CODEC, GateType.CODEC); }
```

## 11. GameTests (data-driven in 26.3)

Facts from source:
- `@GameTest`, `@GameTestHolder`, `@PrefixGameTestTemplate` and `RegisterGameTestsEvent#register(Class)` **no longer exist** (no hits in `[neo]` or `[mc]`).
- A test has two parts:
  1. A **test function**: a `Consumer<GameTestHelper>` in the static built-in registry `Registries.TEST_FUNCTION`, registered with a normal `DeferredRegister`.
  2. A **test instance** (`GameTestInstance`, datapack registry `Registries.TEST_INSTANCE`) that points at the function, an environment and a structure. Write it as JSON or register it in code with NeoForge's `RegisterGameTestsEvent` (mod bus).
- `FunctionGameTestInstance(ResourceKey<Consumer<GameTestHelper>> function, TestData<Holder<TestEnvironmentDefinition<?>>> info)`. JSON `"type": "minecraft:function"`. Src: `[mc]gametest/framework/FunctionGameTestInstance.java`
- `record TestData<E>(E environment, ResourceKey<Level> dimension, Identifier structure, int maxTicks, int setupTicks, boolean required, Rotation rotation, boolean manualOnly, int maxAttempts, int requiredSuccesses, boolean skyAccess, int padding)`. Short ctor: `TestData(env, Identifier structure, int maxTicks, int setupTicks, boolean required)`. JSON keys: `environment`, `structure`, `max_ticks` (required); `dimension`, `setup_ticks`, `required` (default true), `rotation`, `manual_only`, `max_attempts`, `required_successes`, `sky_access`, `padding`. Src: `[mc]gametest/framework/TestData.java`
- `RegisterGameTestsEvent`: `Holder<TestEnvironmentDefinition<?>> registerEnvironment(Identifier, TestEnvironmentDefinition<?>...)`, `registerTest(Identifier, GameTestInstance)`, `registerTest(Identifier, Function<TestData<Holder<..>>, GameTestInstance>, TestData<..>)`. It fires from `RegistryDataLoader` only when `GameTestHooks.isGametestEnabled()`, which is `!production && (IS_RUNNING_IN_IDE || isGameTestServer() || -Dneoforge.enableGameTest=true)`. `IS_RUNNING_IN_IDE = !FMLEnvironment.isProduction()`, so it is always on in dev runs (the `/test` command works in `runClient`). Src: `[neo]event/RegisterGameTestsEvent.java`, `[neo]gametest/GameTestHooks.java`, `[mc]resources/RegistryDataLoader.java:267-273`, `[mc]SharedConstants.java:129`
- Built-ins you can reference: environment `minecraft:default` (`{"type":"minecraft:all_of","definitions":[]}`), structure `minecraft:empty` (1x1x1, `[data]structure/empty.nbt`). Vanilla's sample test is `[data]test_instance/always_pass.json`.
- `GameTestServer` runs **every** non-`manual_only` instance in the registry when `--tests` is absent (that includes vanilla's `minecraft:always_pass`, which has `required:false`). Selector: `--tests awaken:*` (wildcards `*`/`?`). Exit code = number of failed required tests. Src: `[mc]gametest/framework/GameTestServer.java:193-216,323`, `GameTestMainUtil.java:38-41`
- **Gotcha for this repo's `build.gradle`:** the system property `neoforge.enabledGameTestNamespaces` is referenced nowhere in the 26.3 sources or the NeoForge jars, so it does nothing. To limit the run, add `programArguments.addAll('--tests', 'awaken:*')` to the `gameTestServer` run. MDG passing program args to this run type is an assumption I did not check in the MDG sources.
- `GameTestHelper`: `getLevel()` (returns `ServerLevel`), `succeed()`, `succeedWhen(Runnable)`, `succeedIf(Runnable)`, `fail(String)`, `fail(Component)`, `assertTrue(boolean, String|Component)`, `assertFalse(..)`, `setBlock(BlockPos, Block|BlockState)`, `getBlockState(BlockPos)`, `assertBlockPresent(Block, BlockPos)`, `spawn(EntityType<E>, BlockPos)`, `assertEntityPresent(EntityType<?>, BlockPos)`, `makeMockPlayer(GameType)`, `makeMockServerPlayerInLevel()`, `absolutePos(BlockPos)`, `runAfterDelay(long, Runnable)`, `runAtTickTime(long, Runnable)`, `startSequence()`. Coordinates are relative to the test structure. A test that never calls `succeed()` times out at `max_ticks`. Src: `[mc]gametest/framework/GameTestHelper.java:107-1121`

Complete minimal example (`runGameTestServer` picks it up):
```java
package dev.awaken.test;

public final class AwakenGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Awaken.MODID);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> HUNTER_DEFAULTS =
            TEST_FUNCTIONS.register("hunter_defaults", () -> AwakenGameTests::hunterDefaults);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ALTAR_PLACES =
            TEST_FUNCTIONS.register("altar_places", () -> AwakenGameTests::altarPlaces);

    static void hunterDefaults(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(p.getData(ModAttachments.HUNTER).level() == 1, "fresh hunter should be level 1");
        h.succeed();
    }
    static void altarPlaces(GameTestHelper h) {
        BlockPos pos = new BlockPos(0, 0, 0);               // inside minecraft:empty (1x1x1)
        h.setBlock(pos, ModBlocks.ALTAR.get());
        h.succeedWhen(() -> h.assertBlockPresent(ModBlocks.ALTAR.get(), pos));
    }

    // Route B: register the instance in code (mod bus). Skip this if you use JSON (route A).
    public static void onRegisterTests(RegisterGameTestsEvent e) {
        Holder<TestEnvironmentDefinition<?>> env = e.registerEnvironment(Awaken.id("default"));   // empty all_of
        e.registerTest(Awaken.id("altar_places"), new FunctionGameTestInstance(ALTAR_PLACES.key(),
                new TestData<>(env, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
    }
}
// In the @Mod ctor:
//   AwakenGameTests.TEST_FUNCTIONS.register(modBus);
//   modBus.addListener(AwakenGameTests::onRegisterTests);
```
`key()` comes from `Holder.Reference` (which `DeferredHolder` extends) and returns `ResourceKey<Consumer<GameTestHelper>>`. Src: `[mc]core/Holder.java:160`

Route A: `src/main/resources/data/awaken/test_instance/hunter_defaults.json` (vanilla registry, so no extra mod folder):
```json
{
  "type": "minecraft:function",
  "function": "awaken:hunter_defaults",
  "environment": "minecraft:default",
  "structure": "minecraft:empty",
  "max_ticks": 100,
  "required": true
}
```
Custom structures go in `data/awaken/structure/<name>.nbt`, referenced as `"structure": "awaken:<name>"`. Run with `./gradlew runGameTestServer`; in-game use `/test run awaken:*`.

## 12. Players: dimensions, teleport, game mode, items, messages, sounds, attributes

- `MinecraftServer#getLevel(ResourceKey<Level>)` (returns `@Nullable ServerLevel`), `overworld()`, `getAllLevels()`, `getPlayerList()`. `Level.OVERWORLD/NETHER/END`. Src: `[mc]server/MinecraftServer.java:1200-1212,1398`, `[mc]world/level/Level.java:96-98`
- `record TeleportTransition(ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, boolean missingRespawnBlock, boolean asPassenger, Set<Relative> relatives, PostTeleportTransition post)`. Convenience ctors: `(ServerLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, PostTeleportTransition)` and the same plus `Set<Relative>`. Post actions: `TeleportTransition.DO_NOTHING`, `PLAY_PORTAL_SOUND`, `PLACE_PORTAL_TICKET`. Use `ServerPlayer#teleport(TeleportTransition)`, which returns `@Nullable ServerPlayer` and handles cross-dimension moves. Same-level shortcut: `teleportTo(ServerLevel, x, y, z, Set<Relative>, yRot, xRot, boolean resetCamera)`. Src: `[mc]world/level/portal/TeleportTransition.java:13-56`, `[mc]server/level/ServerPlayer.java:1176,1847`
- Game mode: `ServerPlayer#setGameMode(GameType)` returns boolean (fires `PlayerChangeGameModeEvent`), `gameMode()`. `net.minecraft.world.level.GameType`. Src: `[mc]server/level/ServerPlayer.java:1893-1920`
- Items: `player.addItem(ItemStack)` returns false if the item did not fit, `player.getInventory().add(stack)`, `player.drop(ItemStack, boolean thrownFromHand, Prediction)`. Src: `[mc]world/entity/player/Player.java:1714,2154`, `Inventory.java:251`
- Messages: `sendSystemMessage(Component)`, actionbar `sendOverlayMessage(Component)` or `sendSystemMessage(Component, true)`. Broadcast: `server.getPlayerList().broadcastSystemMessage(Component, boolean overlay)`. Src: `[mc]server/level/ServerPlayer.java:1933-1950`, `[mc]server/players/PlayerList.java:735`
- Sounds:
  - `Player#playSound(SoundEvent, vol, pitch)` plays for everyone **except** that player (`level().playSound(this, ...)`).
  - Everyone, including the player: `level.playSound(null, x, y, z, SoundEvent|Holder<SoundEvent>, SoundSource, vol, pitch)` or `level.playSound(null, BlockPos, ..)`.
  - Only that player: `sp.connection.send(new ClientboundSoundPacket(Holder<SoundEvent>, SoundSource, x, y, z, vol, pitch, long seed))`.
  - Src: `[mc]world/entity/player/Player.java:414`, `[mc]world/level/Level.java:398-446`, `[mc]network/protocol/game/ClientboundSoundPacket.java:25`
- Attributes:
  - `living.getAttribute(Holder<Attribute>)` returns `@Nullable AttributeInstance`.
  - `AttributeInstance`: `addOrUpdateTransientModifier(AttributeModifier)` (not saved), `addPermanentModifier(..)` (saved with the entity), `removeModifier(Identifier)`, `hasModifier(Identifier)`, `getModifier(Identifier)`, `setBaseValue(double)`, `getValue()`.
  - `record AttributeModifier(Identifier id, double amount, Operation op)` with `Operation.ADD_VALUE`, `ADD_MULTIPLIED_BASE`, `ADD_MULTIPLIED_TOTAL`.
  - Vanilla holders: `Attributes.MAX_HEALTH`, `MOVEMENT_SPEED`, `ATTACK_DAMAGE` (type `Holder<Attribute>`).
  - Src: `[mc]world/entity/ai/attributes/AttributeInstance.java:45-139`, `AttributeModifier.java:14-41`, `Attributes.java`, `[mc]world/entity/LivingEntity.java:2287`

```java
ServerLevel target = sp.level().getServer().getLevel(Awaken.SANCTUM);
if (target != null) sp.teleport(new TeleportTransition(target, new Vec3(0.5, 65, 0.5), Vec3.ZERO, sp.getYRot(), sp.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND));
sp.setGameMode(GameType.ADVENTURE);
ItemStack gift = ModItems.SIGIL.toStack();
if (!sp.addItem(gift)) sp.drop(gift, false, Prediction.SERVER_ONLY);   // vanilla EmptyMapItem.java:30 does exactly this
sp.sendOverlayMessage(Component.translatable("awaken.gate.entered").withStyle(ChatFormatting.AQUA));
sp.connection.send(new ClientboundSoundPacket(ModSounds.GATE_OPEN, SoundSource.PLAYERS, sp.getX(), sp.getY(), sp.getZ(), 1f, 1f, sp.getRandom().nextLong()));
AttributeInstance hp = sp.getAttribute(Attributes.MAX_HEALTH);
if (hp != null) hp.addOrUpdateTransientModifier(new AttributeModifier(Awaken.id("hunter_level_hp"), 2.0 * lvl, AttributeModifier.Operation.ADD_VALUE));
```
`drop` has no 2-arg overload anymore. The third arg is the enum `net.minecraft.util.Prediction { PREDICTED, SERVER_ONLY }`. `addItem` shrinks the stack it inserts, so pass the same `gift` instance to `drop` to drop only the leftover. Src: `[mc]util/Prediction.java`, `[mc]world/entity/LivingEntity.java:803`

## 13. Text

- `net.minecraft.network.chat.Component`: `literal(String)`, `translatable(String)`, `translatable(String, Object...)`, `translatableWithFallback(String, String, Object...)`, `empty()`, `keybind(String)`. Src: `[mc]network/chat/Component.java:135-170`
- `MutableComponent`: `append(String|Component)`, `withStyle(ChatFormatting...)`, `withStyle(UnaryOperator<Style>)`, `withStyle(Style)`, `withColor(int rgb)`, `withColor(TextColor)`. Src: `[mc]network/chat/MutableComponent.java:48-82`
- `Style`: `withColor(TextColor|ChatFormatting|int)`, `withBold(Boolean)`, `withItalic(Boolean)`, `withClickEvent(ClickEvent)`, `withHoverEvent(HoverEvent)`, `withFont(FontDescription)`. Click and hover are records: `new ClickEvent.RunCommand(String)`, `ClickEvent.SuggestCommand(String)`, `ClickEvent.OpenUrl(URI)`, `new HoverEvent.ShowText(Component)`. Src: `[mc]network/chat/Style.java:137-369`, `ClickEvent.java:126-159`, `HoverEvent.java:135`

```java
Component msg = Component.literal("[SYSTEM] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        .append(Component.translatable("awaken.quest.daily", 4).withColor(0xE0E0FF))
        .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal("Daily quest"))));
```

## 14. Damage

- `DamageSources` via `level.damageSources()` or `entity.damageSources()`: `source(ResourceKey<DamageType>)`, `source(key, @Nullable Entity cause)`, `source(key, direct, causing)`, plus the vanilla helpers. Src: `[mc]world/damagesource/DamageSources.java:75-83`, `[mc]world/level/Level.java:1096`
- Direct: `new DamageSource(Holder<DamageType>, @Nullable Entity direct, @Nullable Entity causing)`; holder lookup `level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key)`. Src: `[mc]world/damagesource/DamageSource.java:34-53`, `[mc]core/RegistryAccess.java:21`, `[mc]core/HolderGetter.java:11`
- Hurting: `abstract boolean hurtServer(ServerLevel, DamageSource, float)` (server); `final boolean hurtOrSimulate(DamageSource, float)` (either side). **`hurt(DamageSource, float)` is gone.** Src: `[mc]world/entity/Entity.java:1976-1980`, `[mc]world/entity/LivingEntity.java:1223`
- Vanilla keys: `net.minecraft.world.damagesource.DamageTypes.MAGIC`, `FALL`, etc. Custom types are data: `data/awaken/damage_type/<name>.json` (vanilla `[data]damage_type/magic.json`: `{"exhaustion":0.0,"message_id":"magic","scaling":"when_caused_by_living_non_player"}`); add them to damage-type tags under `data/minecraft/tags/damage_type/` for bypass behaviour. Death message key: `death.attack.<message_id>`.

```java
public static final ResourceKey<DamageType> MANA_BURN = ResourceKey.create(Registries.DAMAGE_TYPE, Awaken.id("mana_burn"));
if (target.level() instanceof ServerLevel sl) target.hurtServer(sl, sl.damageSources().source(MANA_BURN, attacker), 6.0f);
```

## 15. Data and asset JSON (real vanilla files)

Folder names are singular: `recipe/`, `loot_table/`, `tags/block/`, `tags/item/`, `structure/`, `damage_type/`, `test_instance/`.

**Shaped recipe**, `[data]recipe/stick.json` (tag ingredient = `"#ns:tag"`, item = plain id, alternatives = array as in `torch.json`):
```json
{ "type": "minecraft:crafting_shaped", "group": "sticks",
  "key": { "#": "#minecraft:planks" },
  "pattern": [ "#", "#" ],
  "result": { "count": 4, "id": "minecraft:stick" } }
```
`category` is optional (`[data]recipe/stone_bricks.json` uses `"category": "building"`). Shapeless (`[data]recipe/flint_and_steel.json`): `"type": "minecraft:crafting_shapeless"`, `"ingredients": ["minecraft:iron_ingot", "minecraft:flint"]`, `"result": {"id": "minecraft:flint_and_steel"}`.

**Block loot table**, `[data]loot_table/blocks/stone_bricks.json`. **26.x uses a singular `"condition"` and `"modifier"`**: `LootPool` codec fields are `entries`, `condition`, `modifier`, `rolls`, `bonus_rolls`, `name`. A condition can be an inline object or a predicate id string such as `"minecraft:tool/can_silk_touch"` (`[data]predicate/tool/can_silk_touch.json`). Src: `[mc]world/level/storage/loot/LootPool.java:28-37`, `entries/LootPoolEntryContainer.java:34`. No file under `[data]loot_table/blocks/` uses `"conditions"`.
```json
{ "type": "minecraft:block",
  "pools": [ { "condition": { "type": "minecraft:survives_explosion" },
               "entries": [ { "type": "minecraft:item", "name": "minecraft:stone_bricks" } ],
               "rolls": 1 } ],
  "random_sequence": "minecraft:blocks/stone_bricks" }
```
Item modifiers, from `[data]loot_table/blocks/diamond_ore.json`: an entry has `"modifier": [ {"type":"minecraft:apply_bonus","enchantment":"minecraft:fortune","formula":"minecraft:ore_drops"}, {"type":"minecraft:explosion_decay"} ]`.

**Blockstate**, `[assets]blockstates/stone_bricks.json` / `hay_block.json`:
```json
{ "variants": { "": { "model": "minecraft:block/stone_bricks" } } }
{ "variants": { "axis=x": { "model": "minecraft:block/hay_block_horizontal", "x": 90, "y": 90 },
                "axis=y": { "model": "minecraft:block/hay_block" },
                "axis=z": { "model": "minecraft:block/hay_block_horizontal", "x": 90 } } }
```
**Block model**, `[assets]models/block/stone_bricks.json`: `{ "parent": "minecraft:block/cube_all", "textures": { "all": "minecraft:block/stone_bricks" } }`

**Item model**, `[assets]models/item/stick.json`: `{ "parent": "minecraft:item/handheld", "textures": { "layer0": "minecraft:item/stick" } }` (use `minecraft:item/generated` for non-tools).

**Client item definition (required for every item to render)**: `assets/<ns>/items/<item>.json`. `[assets]items/stick.json`:
```json
{ "model": { "type": "minecraft:model", "model": "minecraft:item/stick" } }
```
A block item points straight at the block model: `[assets]items/stone_bricks.json` is `{ "model": { "type": "minecraft:model", "model": "minecraft:block/stone_bricks" } }`.

For AWAKEN, swap `minecraft:` for `awaken:` in ids and put files under `src/main/resources/assets/awaken/...` and `.../data/awaken/...`.
