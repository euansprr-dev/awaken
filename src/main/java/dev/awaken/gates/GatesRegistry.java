package dev.awaken.gates;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.entity.EchoGhost;
import dev.awaken.gates.entity.GateHoarder;
import dev.awaken.gates.entity.GateShade;
import dev.awaken.gates.entity.HollowFollower;
import dev.awaken.gates.run.GateMemory;
import dev.awaken.gates.run.GearStash;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.world.GateBiomeSource;
import dev.awaken.gates.world.GateChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Every registry object the gates module owns. */
public final class GatesRegistry {
    private GatesRegistry() {}

    public static final ResourceKey<Level> GATES = ResourceKey.create(Registries.DIMENSION, Awaken.id("gates"));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Awaken.MODID);
    public static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, Awaken.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);

    // ---- blocks -----------------------------------------------------------------------------------------------------

    private static UnaryOperator<BlockBehaviour.Properties> unbreakable(MapColor c, SoundType s, int light) {
        return p -> p.mapColor(c).strength(-1.0F, 3600000.0F).sound(s).lightLevel(st -> light).pushReaction(PushReaction.IMMOVEABLE)
                .isValidSpawn((st, l, pos, t) -> false);
    }

    public static final DeferredBlock<Block> FRAME = BLOCKS.registerSimpleBlock("gate_frame", unbreakable(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES, 0));
    public static final DeferredBlock<Block> FRAME_CRACKED = BLOCKS.registerSimpleBlock("gate_frame_cracked", unbreakable(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES, 2));
    public static final DeferredBlock<GateBlocks.Core> CORE = BLOCKS.registerBlock("gate_core", GateBlocks.Core::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F).noCollision().noOcclusion().noLootTable()
                    .lightLevel(s -> s.getValue(GateBlocks.Core.BROKEN) ? 9 : 15).sound(SoundType.AMETHYST).pushReaction(PushReaction.IMMOVEABLE)
                    .isValidSpawn((st, l, pos, t) -> false).isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false));
    public static final DeferredBlock<GateBlocks.Portal> EXIT = portal("gate_exit", GateBlocks.Portal.Kind.EXIT);
    public static final DeferredBlock<GateBlocks.Portal> RIFT = portal("gate_rift", GateBlocks.Portal.Kind.RIFT);
    public static final DeferredBlock<GateBlocks.Portal> INNER = portal("gate_inner", GateBlocks.Portal.Kind.INNER);
    public static final DeferredBlock<GateBlocks.Portal> RETURN = portal("gate_return", GateBlocks.Portal.Kind.RETURN);
    public static final DeferredBlock<GateBlocks.Shrine> SHRINE = BLOCKS.registerBlock("rest_shrine", GateBlocks.Shrine::new, unbreakable(MapColor.COLOR_CYAN, SoundType.AMETHYST, 13));
    public static final DeferredBlock<GateBlocks.Seal> SEAL = BLOCKS.registerBlock("gate_seal", GateBlocks.Seal::new, unbreakable(MapColor.COLOR_PURPLE, SoundType.DEEPSLATE_TILES, 7));
    public static final DeferredBlock<GateBlocks.Cache> CACHE = BLOCKS.registerBlock("gate_cache", GateBlocks.Cache::new, unbreakable(MapColor.GOLD, SoundType.COPPER, 4));
    public static final DeferredBlock<GateBlocks.Bars> BARS = BLOCKS.registerBlock("gate_bars", GateBlocks.Bars::new,
            p -> unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.CHAIN, 6).apply(p).noOcclusion());
    public static final DeferredBlock<GateBlocks.Rune> RUNE = BLOCKS.registerBlock("gate_rune", GateBlocks.Rune::new,
            p -> unbreakable(MapColor.COLOR_BLUE, SoundType.AMETHYST, 0).apply(p).lightLevel(s -> s.getValue(GateBlocks.Rune.LIT) ? 11 : 2));
    public static final DeferredBlock<GateBlocks.Entrance> ENTRANCE = BLOCKS.registerBlock("gate_entrance", GateBlocks.Entrance::new, unbreakable(MapColor.COLOR_CYAN, SoundType.DEEPSLATE_TILES, 8));
    public static final DeferredBlock<GateBlocks.Lantern> LANTERN = BLOCKS.registerBlock("lost_lantern", GateBlocks.Lantern::new,
            p -> unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.LANTERN, 12).apply(p).noOcclusion());
    public static final DeferredBlock<GateBlocks.Updraft> UPDRAFT = BLOCKS.registerBlock("gate_updraft", GateBlocks.Updraft::new, unbreakable(MapColor.SNOW, SoundType.WOOL, 6));

    private static DeferredBlock<GateBlocks.Portal> portal(String name, GateBlocks.Portal.Kind kind) {
        return BLOCKS.registerBlock(name, p -> new GateBlocks.Portal(p, kind),
                p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noCollision().noOcclusion().noLootTable()
                        .lightLevel(s -> 14).sound(SoundType.AMETHYST).pushReaction(PushReaction.IMMOVEABLE)
                        .isValidSpawn((st, l, pos, t) -> false).isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false));
    }

    public static final List<DeferredBlock<? extends Block>> ITEM_BLOCKS = List.of(FRAME, FRAME_CRACKED, SHRINE, SEAL, CACHE, BARS, RUNE, ENTRANCE, LANTERN, UPDRAFT);

    public static final DeferredItem<BlockItem> FRAME_ITEM = ITEMS.registerSimpleBlockItem(FRAME);
    public static final DeferredItem<BlockItem> FRAME_CRACKED_ITEM = ITEMS.registerSimpleBlockItem(FRAME_CRACKED);
    public static final DeferredItem<BlockItem> SHRINE_ITEM = ITEMS.registerSimpleBlockItem(SHRINE);
    public static final DeferredItem<BlockItem> SEAL_ITEM = ITEMS.registerSimpleBlockItem(SEAL);
    public static final DeferredItem<BlockItem> CACHE_ITEM = ITEMS.registerSimpleBlockItem(CACHE);
    public static final DeferredItem<BlockItem> BARS_ITEM = ITEMS.registerSimpleBlockItem(BARS);
    public static final DeferredItem<BlockItem> RUNE_ITEM = ITEMS.registerSimpleBlockItem(RUNE);
    public static final DeferredItem<BlockItem> ENTRANCE_ITEM = ITEMS.registerSimpleBlockItem(ENTRANCE);
    public static final DeferredItem<BlockItem> LANTERN_ITEM = ITEMS.registerSimpleBlockItem(LANTERN);
    public static final DeferredItem<BlockItem> UPDRAFT_ITEM = ITEMS.registerSimpleBlockItem(UPDRAFT);

    // ---- entities ---------------------------------------------------------------------------------------------------

    public static final DeferredHolder<EntityType<?>, EntityType<GateShade>> SHADE = ENTITIES.registerEntityType("gate_shade",
            GateShade::new, MobCategory.MONSTER, b -> b.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8).noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<EchoGhost>> GHOST = ENTITIES.registerEntityType("echo_ghost",
            EchoGhost::new, MobCategory.MISC, b -> b.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).updateInterval(1).noSave().noSummon());
    public static final DeferredHolder<EntityType<?>, EntityType<HollowFollower>> HOLLOW = ENTITIES.registerEntityType("hollow_follower",
            HollowFollower::new, MobCategory.MONSTER, b -> b.sized(0.6F, 1.9F).eyeHeight(1.7F).clientTrackingRange(8).updateInterval(1).noSave().noSummon());
    public static final DeferredHolder<EntityType<?>, EntityType<GateHoarder>> HOARDER = ENTITIES.registerEntityType("gate_hoarder",
            GateHoarder::new, MobCategory.MONSTER, b -> b.sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).noSave());

    // ---- sounds -----------------------------------------------------------------------------------------------------

    public static final DeferredHolder<SoundEvent, SoundEvent> SND_ENTER = sound("gate.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_EXIT = sound("gate.exit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SHRINE = sound("gate.shrine");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SEAL = sound("gate.seal");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CACHE = sound("gate.cache");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_BREAK = sound("gate.break");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_PRESSURE = sound("gate.pressure");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WARDEN = sound("gate.warden");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_BARS = sound("gate.bars");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RUNE = sound("gate.rune");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SOLVED = sound("gate.solved");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_ALARM = sound("gate.alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_COLLAPSE = sound("gate.collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CLEARED = sound("gate.cleared");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_HUM = sound("gate.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_BELL = sound("gate.ambient.bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_GEARS = sound("gate.ambient.gears");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_CHANT = sound("gate.ambient.chant");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_BUZZ = sound("gate.ambient.buzz");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_MARKET = sound("gate.ambient.market");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_LAB = sound("gate.ambient.lab");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_GLASS = sound("gate.ambient.glass");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_WIND = sound("gate.ambient.wind");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    // ---- worldgen ---------------------------------------------------------------------------------------------------

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<GateChunkGenerator>> GENERATOR =
            GENERATORS.register("gate_cells", () -> GateChunkGenerator.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<GateBiomeSource>> BIOME_SOURCE =
            BIOME_SOURCES.register("gate_cells", () -> GateBiomeSource.CODEC);
    /** Pre-generation of a gate's first floor and the surface structure. Removed as soon as the chunks are built. */
    public static final DeferredHolder<TicketType, TicketType> BUILD_TICKET = TICKETS.register("gate_build",
            () -> new TicketType(0L, TicketType.FLAG_LOADING));
    /** Keeps a broken gate's area simulated briefly while its spill wave spawns. */
    public static final DeferredHolder<TicketType, TicketType> BREAK_TICKET = TICKETS.register("gate_break",
            () -> new TicketType(600L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));

    // ---- attachments ------------------------------------------------------------------------------------------------

    public static final Supplier<AttachmentType<RunState>> RUN = ATTACHMENTS.register("gate_run",
            () -> AttachmentType.builder(RunState::none).serialize(RunState.MAP_CODEC, RunState::active).build());
    public static final Supplier<AttachmentType<GateMemory>> MEMORY = ATTACHMENTS.register("gate_memory",
            () -> AttachmentType.builder(GateMemory::new).serialize(GateMemory.MAP_CODEC).copyOnDeath().build());
    public static final Supplier<AttachmentType<GearStash>> STASH = ATTACHMENTS.register("gate_stash",
            () -> AttachmentType.builder(() -> GearStash.EMPTY).serialize(GearStash.MAP_CODEC, s -> !s.isEmpty()).copyOnDeath().build());

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        GENERATORS.register(modBus);
        BIOME_SOURCES.register(modBus);
        TICKETS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(GatesRegistry::attributes);
        modBus.addListener(GatesRegistry::creativeTab);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(SHADE.get(), GateShade.createAttributes().build());
        e.put(GHOST.get(), EchoGhost.createAttributes().build());
        e.put(HOLLOW.get(), HollowFollower.createAttributes().build());
        e.put(HOARDER.get(), GateHoarder.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (var item : List.of(FRAME_ITEM, FRAME_CRACKED_ITEM, SHRINE_ITEM, SEAL_ITEM, CACHE_ITEM, BARS_ITEM, RUNE_ITEM, ENTRANCE_ITEM,
                LANTERN_ITEM, UPDRAFT_ITEM)) e.accept(item.get());
    }
}
