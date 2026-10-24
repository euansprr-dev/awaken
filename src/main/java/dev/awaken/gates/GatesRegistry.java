package dev.awaken.gates;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.block.RigBlocks;
import dev.awaken.gates.entity.EchoGhost;
import dev.awaken.gates.entity.GateHoarder;
import dev.awaken.gates.entity.GateShade;
import dev.awaken.gates.entity.HollowFollower;
import dev.awaken.gates.run.GateMemory;
import dev.awaken.gates.run.GearStash;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.world.GateBiomeSource;
import dev.awaken.gates.world.GateChunkGenerator;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
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
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);

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
    /** Ground cracks glowing in the rank colour round a surface gate (rank 7 = broken, red). */
    public static final DeferredBlock<dev.awaken.gates.block.GateFissureBlock> FISSURE = BLOCKS.registerBlock("gate_fissure",
            dev.awaken.gates.block.GateFissureBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5F, 6.0F).sound(SoundType.DEEPSLATE).noLootTable()
                    .lightLevel(s -> s.getValue(dev.awaken.gates.block.GateFissureBlock.RANK) == 7 ? 6 : 7).isValidSpawn((st, l, pos, t) -> false));
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

    // ---- interior materials: where vanilla runs out (docs/modules/gates.md "Interiors"; textures tools/art/specs/gate_interiors.py)

    private static UnaryOperator<BlockBehaviour.Properties> stoneLike(MapColor c, SoundType s, int light) {
        return p -> p.mapColor(c).strength(1.5F, 6.0F).sound(s).requiresCorrectToolForDrops().lightLevel(st -> light);
    }

    private static UnaryOperator<BlockBehaviour.Properties> soft(MapColor c, SoundType s, float strength, int light) {
        return p -> p.mapColor(c).strength(strength).sound(s).lightLevel(st -> light);
    }

    private static UnaryOperator<BlockBehaviour.Properties> glassy(MapColor c, SoundType s, int light) {
        return p -> p.mapColor(c).strength(0.4F).sound(s).noOcclusion().lightLevel(st -> light)
                .isValidSpawn((st, l, pos, t) -> false).isRedstoneConductor((st, l, pos) -> false)
                .isSuffocating((st, l, pos) -> false).isViewBlocking((st, l, pos, box) -> false);
    }

    /** Skulls and long bones stacked into a wall: the Bell Crypt's ossuaries. */
    public static final DeferredBlock<Block> OSSUARY = BLOCKS.registerSimpleBlock("gate_ossuary", stoneLike(MapColor.SAND, SoundType.BONE_BLOCK, 0));
    /** Dark bronze gone green at the edges: the great bells and their crowns. */
    public static final DeferredBlock<Block> BELL_BRONZE = BLOCKS.registerSimpleBlock("gate_bell_bronze", stoneLike(MapColor.TERRACOTTA_ORANGE, SoundType.COPPER, 0));
    /** A small bronze bell hanging from a chain (a plain model, no block entity, so worldgen can place it). */
    public static final DeferredBlock<Block> BELL = BLOCKS.registerSimpleBlock("gate_bell",
            p -> stoneLike(MapColor.TERRACOTTA_ORANGE, SoundType.COPPER, 0).apply(p).noOcclusion().isSuffocating((st, l, pos) -> false));
    /** A giant mushroom's cap, glowing violet with cyan spots. */
    public static final DeferredBlock<Block> GLOWCAP = BLOCKS.registerSimpleBlock("gate_glowcap", soft(MapColor.COLOR_PURPLE, SoundType.FUNGUS, 0.6F, 12));
    /** The gills under a giant cap, faintly luminous. */
    public static final DeferredBlock<Block> GLOWCAP_GILLS = BLOCKS.registerSimpleBlock("gate_glowcap_gills", soft(MapColor.COLOR_CYAN, SoundType.FUNGUS, 0.6F, 9));
    /** Blackstone split by veins of ore still glowing from below. */
    public static final DeferredBlock<Block> EMBER_ORE = BLOCKS.registerSimpleBlock("gate_ember_ore", stoneLike(MapColor.COLOR_BLACK, SoundType.GILDED_BLACKSTONE, 7));
    /** A bookshelf the sea got into: swollen spines, green with algae. */
    public static final DeferredBlock<Block> SODDEN_SHELF = BLOCKS.registerSimpleBlock("gate_sodden_shelf", soft(MapColor.COLOR_CYAN, SoundType.WOOD, 1.5F, 0));
    /** A copper gear face: hub, spokes and teeth. */
    public static final DeferredBlock<Block> GEAR = BLOCKS.registerSimpleBlock("gate_gear", stoneLike(MapColor.COLOR_ORANGE, SoundType.COPPER, 0));
    /** A conveyor belt: dark slats over rollers (axis = the way it runs). */
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> CONVEYOR = BLOCKS.registerBlock("gate_conveyor",
            net.minecraft.world.level.block.RotatedPillarBlock::new, stoneLike(MapColor.COLOR_GRAY, SoundType.COPPER, 0));
    /** Sand fused into amber glass by something very hot. */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> SANDGLASS = BLOCKS.registerBlock("gate_sandglass",
            net.minecraft.world.level.block.TransparentBlock::new, glassy(MapColor.COLOR_ORANGE, SoundType.GLASS, 0));
    /** A sandstone tile inlaid with a sun that still holds the heat. */
    public static final DeferredBlock<Block> SUN_TILE = BLOCKS.registerSimpleBlock("gate_sun_tile", stoneLike(MapColor.SAND, SoundType.STONE, 6));
    /** Wax comb, capped cells and a few glowing larvae. */
    public static final DeferredBlock<Block> BROOD_CELL = BLOCKS.registerSimpleBlock("gate_brood_cell", soft(MapColor.COLOR_ORANGE, SoundType.CORAL_BLOCK, 0.8F, 6));
    /** Royal jelly: a translucent glowing gold. */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> ROYAL_JELLY = BLOCKS.registerBlock("gate_royal_jelly",
            net.minecraft.world.level.block.TransparentBlock::new, glassy(MapColor.GOLD, SoundType.HONEY_BLOCK, 12));
    /** A carved face looking out of the temple wall (facing = the way it looks). */
    public static final DeferredBlock<net.minecraft.world.level.block.GlazedTerracottaBlock> TEMPLE_RELIEF = BLOCKS.registerBlock("gate_temple_relief",
            net.minecraft.world.level.block.GlazedTerracottaBlock::new, stoneLike(MapColor.STONE, SoundType.STONE, 0));
    /** Polished jade, veined green. */
    public static final DeferredBlock<Block> JADE = BLOCKS.registerSimpleBlock("gate_jade", stoneLike(MapColor.EMERALD, SoundType.STONE, 0));
    /** A red paper lantern. */
    public static final DeferredBlock<Block> PAPER_LANTERN = BLOCKS.registerSimpleBlock("gate_paper_lantern",
            p -> soft(MapColor.COLOR_RED, SoundType.WOOL, 0.3F, 15).apply(p).noOcclusion().isSuffocating((st, l, pos) -> false));
    /** Quartz brick under a crust of rime. */
    public static final DeferredBlock<Block> RIME_BRICK = BLOCKS.registerSimpleBlock("gate_rime_brick", stoneLike(MapColor.SNOW, SoundType.STONE, 0));
    /** A frosted window with cold light behind it. */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> FROST_WINDOW = BLOCKS.registerBlock("gate_frost_window",
            net.minecraft.world.level.block.TransparentBlock::new, glassy(MapColor.ICE, SoundType.GLASS, 9));
    /** Silvered glass: a mirror. */
    public static final DeferredBlock<Block> MIRROR = BLOCKS.registerSimpleBlock("gate_mirror", stoneLike(MapColor.QUARTZ, SoundType.GLASS, 3));
    /** A white Precursor lab panel with a cyan light strip. */
    public static final DeferredBlock<Block> LAB_PANEL = BLOCKS.registerSimpleBlock("gate_lab_panel", stoneLike(MapColor.QUARTZ, SoundType.METAL, 9));
    /** A rack of Precursor machines blinking in no language (facing = its front). */
    public static final DeferredBlock<net.minecraft.world.level.block.GlazedTerracottaBlock> LAB_RACK = BLOCKS.registerBlock("gate_lab_rack",
            net.minecraft.world.level.block.GlazedTerracottaBlock::new, stoneLike(MapColor.COLOR_GRAY, SoundType.METAL, 5));
    /** Wallpaper from a house you almost remember. The pattern is eyes. */
    public static final DeferredBlock<Block> DREAM_PAPER = BLOCKS.registerSimpleBlock("gate_dream_paper", soft(MapColor.COLOR_PURPLE, SoundType.WOOL, 0.8F, 0));
    /** A bank of cloud. */
    public static final DeferredBlock<Block> CLOUD = BLOCKS.registerSimpleBlock("gate_cloud", soft(MapColor.SNOW, SoundType.WOOL, 0.4F, 0));
    /** Black glass with stars in it, a long way down. */
    public static final DeferredBlock<Block> VOID_GLASS = BLOCKS.registerSimpleBlock("gate_void_glass", stoneLike(MapColor.COLOR_BLACK, SoundType.GLASS, 3));
    /** Open sky, painted on the Sky Archipelago's walls and vaults; it gives off a little daylight of its own. */
    public static final DeferredBlock<Block> SKY = BLOCKS.registerSimpleBlock("gate_sky", soft(MapColor.COLOR_LIGHT_BLUE, SoundType.WOOL, 0.4F, 6));
    /** The System's blue: the Rest Shrine's light. */
    public static final DeferredBlock<Block> SIGNAL_LAMP = BLOCKS.registerSimpleBlock("gate_signal_lamp", stoneLike(MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST, 14));
    /** A mirror crazed with fine cracks: the pavilion's front in a Mirror Hall (a spirit walks through it). */
    public static final DeferredBlock<Block> MIRROR_CRACKED = BLOCKS.registerSimpleBlock("gate_mirror_cracked", stoneLike(MapColor.QUARTZ, SoundType.GLASS, 3));
    /** The glowing rim of a gravity well, on the floor and on the ceiling above it. */
    public static final DeferredBlock<Block> WELL_RIM = BLOCKS.registerSimpleBlock("gate_well_rim", stoneLike(MapColor.COLOR_PURPLE, SoundType.AMETHYST, 10));

    public static final List<DeferredBlock<? extends Block>> INTERIOR_BLOCKS = List.of(OSSUARY, BELL_BRONZE, BELL, GLOWCAP, GLOWCAP_GILLS,
            EMBER_ORE, SODDEN_SHELF, GEAR, CONVEYOR, SANDGLASS, SUN_TILE, BROOD_CELL, ROYAL_JELLY, TEMPLE_RELIEF, JADE, PAPER_LANTERN,
            RIME_BRICK, FROST_WINDOW, MIRROR, LAB_PANEL, LAB_RACK, DREAM_PAPER, CLOUD, SKY, VOID_GLASS, SIGNAL_LAMP, MIRROR_CRACKED, WELL_RIM);
    public static final List<DeferredItem<BlockItem>> INTERIOR_ITEMS = INTERIOR_BLOCKS.stream().map(ITEMS::registerSimpleBlockItem).toList();

    // ---- the Gate Rig's Mirror Hall (SPEC §17): the turning mirror, the lens, the seal --------------------------------

    public static final DeferredBlock<RigBlocks.Mirror> RIG_MIRROR = BLOCKS.registerBlock("gate_rig_mirror",
            RigBlocks.Mirror::new, p -> unbreakable(MapColor.QUARTZ, SoundType.GLASS, 4).apply(p).noOcclusion());
    public static final DeferredBlock<RigBlocks.Lens> RIG_LENS = BLOCKS.registerBlock("gate_rig_lens",
            RigBlocks.Lens::new, unbreakable(MapColor.GOLD, SoundType.AMETHYST, 13));
    public static final DeferredBlock<RigBlocks.Seal> RIG_SEAL = BLOCKS.registerBlock("gate_rig_seal",
            RigBlocks.Seal::new, p -> unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST, 0).apply(p)
                    .lightLevel(s -> s.getValue(RigBlocks.Seal.LIT) ? 15 : 4));
    public static final List<DeferredItem<BlockItem>> RIG_ITEMS = List.of(ITEMS.registerSimpleBlockItem(RIG_MIRROR),
            ITEMS.registerSimpleBlockItem(RIG_LENS), ITEMS.registerSimpleBlockItem(RIG_SEAL));

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

    // ---- block entities ---------------------------------------------------------------------------------------------

    /** The anchor core's vortex (render data, synced). */
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<GateVortexBlockEntity>> VORTEX_BE =
            BLOCK_ENTITIES.register("gate_vortex", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(GateVortexBlockEntity::new, CORE.get()));

    /** The anchor of a doorway inside a gate (exit, way back, inner gate, rift): its renderer draws the whole opening. */
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<GatePortalBlockEntity>> PORTAL_BE =
            BLOCK_ENTITIES.register("gate_portal", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(GatePortalBlockEntity::new,
                    EXIT.get(), RIFT.get(), INNER.get(), RETURN.get()));

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
    /** The surface vortex's turning whoosh (looped with the hum near a gate). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_VORTEX = sound("gate.vortex");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_BELL = sound("gate.ambient.bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_GEARS = sound("gate.ambient.gears");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_CHANT = sound("gate.ambient.chant");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_BUZZ = sound("gate.ambient.buzz");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_MARKET = sound("gate.ambient.market");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_LAB = sound("gate.ambient.lab");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_GLASS = sound("gate.ambient.glass");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMB_WIND = sound("gate.ambient.wind");

    // the Gate Rig (tools/art/specs/audio_rig.py): the collapse, the mirrors and the light, the wells, the cave-in
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_CRACK = sound("gate.rig.crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_FALL = sound("gate.rig.fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_KNIT = sound("gate.rig.knit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_RUN = sound("gate.rig.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_PIT = sound("gate.rig.pit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_TURN = sound("gate.rig.turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_HUM = sound("gate.rig.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_BURN = sound("gate.rig.burn");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_IGNITE = sound("gate.rig.ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_SHATTER = sound("gate.rig.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_FLIP_UP = sound("gate.rig.flip_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_FLIP_DOWN = sound("gate.rig.flip_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_SURGE = sound("gate.rig.surge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_CAVE = sound("gate.rig.cave");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_RUBBLE = sound("gate.rig.rubble");

    // ---- particles (the Gate Rig's own; client providers in gates.client.RigParticles) -------------------------------

    /** Grit and dust shaken off a cracking tile or a ceiling about to fall (tinted by the client). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_GRIT =
            PARTICLES.register("gate_rig_grit", () -> new SimpleParticleType(false));
    /** A spark rising out of a crack, or off a monster the light burns. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_SPARK =
            PARTICLES.register("gate_rig_spark", () -> new SimpleParticleType(false));
    /** A mote of light drifting off the beam, a mirror or the seal. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_GLINT =
            PARTICLES.register("gate_rig_glint", () -> new SimpleParticleType(false));
    /** A shard of mirror glass, tumbling. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_SHARD =
            PARTICLES.register("gate_rig_shard", () -> new SimpleParticleType(false));
    /** A well's mote: it drifts toward the well's "down". */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_MOTE =
            PARTICLES.register("gate_rig_mote", () -> new SimpleParticleType(false));

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
        BLOCK_ENTITIES.register(modBus);
        PARTICLES.register(modBus);
        GateExterior.init(modBus);
        dev.awaken.gates.world.Interiors.init();
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
        for (var item : INTERIOR_ITEMS) e.accept(item.get());
        for (var item : RIG_ITEMS) e.accept(item.get());
    }
}
