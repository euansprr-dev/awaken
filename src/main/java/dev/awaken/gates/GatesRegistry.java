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
    /** The gates under an open sky (SPEC §38.11): the same cells and plots, only the open lands' floors built in it. */
    public static final ResourceKey<Level> GATES_SKY = ResourceKey.create(Registries.DIMENSION, Awaken.id("gates_sky"));

    /** Whether a dimension is one gates are built in. */
    public static boolean isGateDim(ResourceKey<Level> dim) {
        return dim == GATES || dim == GATES_SKY;
    }

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

    // ---- gate worlds' blocks (SPEC §38.6; textures tools/art/specs/gate_worlds_*.py) ----------------------------------

    /** Bell Crypt: limestone in three states of wear, carved tracery, worn and wet flagstones. */
    public static final DeferredBlock<Block> CRYPT_LIMESTONE = BLOCKS.registerSimpleBlock("crypt_limestone", stoneLike(MapColor.STONE, SoundType.STONE, 0));
    public static final DeferredBlock<Block> CRYPT_LIMESTONE_WORN = BLOCKS.registerSimpleBlock("crypt_limestone_worn", stoneLike(MapColor.STONE, SoundType.STONE, 0));
    public static final DeferredBlock<Block> CRYPT_LIMESTONE_CRUMBLING = BLOCKS.registerSimpleBlock("crypt_limestone_crumbling", stoneLike(MapColor.STONE, SoundType.STONE, 0));
    public static final DeferredBlock<Block> CRYPT_TRACERY = BLOCKS.registerSimpleBlock("crypt_tracery", stoneLike(MapColor.STONE, SoundType.STONE, 0));
    public static final DeferredBlock<Block> CRYPT_FLAGSTONE = BLOCKS.registerSimpleBlock("crypt_flagstone", stoneLike(MapColor.COLOR_GRAY, SoundType.STONE, 0));
    public static final DeferredBlock<Block> CRYPT_FLAGSTONE_WET = BLOCKS.registerSimpleBlock("crypt_flagstone_wet", stoneLike(MapColor.COLOR_GRAY, SoundType.MUD_BRICKS, 0));
    /** Patina bronze: the great bells. Strike it and the Belfry's rule answers. */
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Bronze> CRYPT_PATINA = BLOCKS.registerBlock("crypt_patina",
            dev.awaken.gates.block.WorldBlocks.Bronze::new, stoneLike(MapColor.TERRACOTTA_ORANGE, SoundType.COPPER, 0));
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Candles> CRYPT_CANDLES = BLOCKS.registerBlock("crypt_candles",
            dev.awaken.gates.block.WorldBlocks.Candles::new,
            p -> soft(MapColor.SAND, SoundType.CANDLE, 0.1F, 0).apply(p).noOcclusion().lightLevel(dev.awaken.gates.block.WorldBlocks.Candles::light));
    /** Moonlight that isn't there: the lancets' glass. */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> CRYPT_MOON_GLASS = BLOCKS.registerBlock("crypt_moon_glass",
            net.minecraft.world.level.block.TransparentBlock::new, glassy(MapColor.COLOR_LIGHT_BLUE, SoundType.GLASS, 10));
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.RoseGlass> CRYPT_ROSE_GLASS = BLOCKS.registerBlock("crypt_rose_glass",
            dev.awaken.gates.block.WorldBlocks.RoseGlass::new, glassy(MapColor.COLOR_PURPLE, SoundType.GLASS, 6));
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Facing> CRYPT_NICHE = BLOCKS.registerBlock("crypt_niche",
            p -> new dev.awaken.gates.block.WorldBlocks.Facing(p, false), stoneLike(MapColor.SAND, SoundType.BONE_BLOCK, 5));
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Facing> CRYPT_VESTMENT = BLOCKS.registerBlock("crypt_vestment",
            p -> new dev.awaken.gates.block.WorldBlocks.Facing(p, true), p -> soft(MapColor.COLOR_CYAN, SoundType.WOOL, 0.2F, 0).apply(p).noOcclusion().noCollision());
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Thin> CRYPT_BELL_ROPE = BLOCKS.registerBlock("crypt_bell_rope",
            p -> new dev.awaken.gates.block.WorldBlocks.Thin(p, Block.box(6, 0, 6, 10, 16, 10)),
            p -> soft(MapColor.COLOR_BROWN, SoundType.WOOL, 0.3F, 0).apply(p).noOcclusion());
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Thin> CRYPT_LILY_CANDLE = BLOCKS.registerBlock("crypt_lily_candle",
            p -> new dev.awaken.gates.block.WorldBlocks.Thin(p, Block.box(1, 0, 1, 15, 1.5, 15)),
            p -> soft(MapColor.PLANT, SoundType.LILY_PAD, 0.1F, 6).apply(p).noOcclusion());
    /** A handbell in a stone yoke on the floor: the bell anyone can reach and strike (SPEC §38.8). */
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Handbell> CRYPT_HANDBELL = BLOCKS.registerBlock("crypt_handbell",
            dev.awaken.gates.block.WorldBlocks.Handbell::new, p -> stoneLike(MapColor.TERRACOTTA_ORANGE, SoundType.COPPER, 0).apply(p).noOcclusion());
    /** A swinging bell's body: never placed, only shown by a block display (SPEC §38.6). */
    public static final DeferredBlock<Block> BELL_PROP = BLOCKS.registerBlock("bell_prop", Block::new,
            p -> stoneLike(MapColor.TERRACOTTA_ORANGE, SoundType.COPPER, 0).apply(p).noOcclusion().noLootTable());
    /** A bay bell fallen from the vault (SPEC §38.14, stage 3): the swinging bell's look on its side, its mouth to its facing. */
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.Facing> CRYPT_BELL_TOPPLED = BLOCKS.registerBlock("crypt_bell_toppled",
            p -> new dev.awaken.gates.block.WorldBlocks.Facing(p, false),
            p -> stoneLike(MapColor.TERRACOTTA_ORANGE, SoundType.COPPER, 0).apply(p).noOcclusion().noLootTable());
    public static final List<DeferredBlock<? extends Block>> WORLD_BLOCKS = List.of(CRYPT_LIMESTONE, CRYPT_LIMESTONE_WORN, CRYPT_LIMESTONE_CRUMBLING,
            CRYPT_TRACERY, CRYPT_FLAGSTONE, CRYPT_FLAGSTONE_WET, CRYPT_PATINA, CRYPT_CANDLES, CRYPT_MOON_GLASS, CRYPT_ROSE_GLASS, CRYPT_NICHE,
            CRYPT_VESTMENT, CRYPT_BELL_ROPE, CRYPT_LILY_CANDLE, CRYPT_HANDBELL);
    public static final List<DeferredItem<BlockItem>> WORLD_ITEMS = WORLD_BLOCKS.stream().map(ITEMS::registerSimpleBlockItem).toList();
    /** The Bell Crypt's set piece, yours when all five of its keepsakes are (SPEC §38.8, "Sets"). */
    public static final DeferredBlock<dev.awaken.gates.block.WorldBlocks.BellTower> BELL_TOWER = BLOCKS.registerBlock("bell_tower",
            dev.awaken.gates.block.WorldBlocks.BellTower::new, p -> stoneLike(MapColor.STONE, SoundType.STONE, 6).apply(p).noOcclusion());
    /** Every theme after the Bell Crypt's world blocks (SPEC §38.10 on), by id, from one table. */
    public static final java.util.Map<String, DeferredBlock<? extends Block>> THEME_BLOCKS = dev.awaken.gates.block.ThemeBlocks.register(BLOCKS);
    public static final List<DeferredItem<BlockItem>> THEME_ITEMS = THEME_BLOCKS.values().stream().map(ITEMS::registerSimpleBlockItem).toList();
    /** The themes' set pieces after the Bell Tower (38.8 "Sets"). */
    public static final java.util.Map<String, DeferredBlock<? extends Block>> SETS = dev.awaken.gates.block.ThemeBlocks.registerSets(BLOCKS);
    public static final List<DeferredItem<BlockItem>> SET_ITEMS = java.util.stream.Stream.<DeferredBlock<? extends Block>>concat(java.util.stream.Stream.of(BELL_TOWER),
            SETS.values().stream()).map(ITEMS::registerSimpleBlockItem).toList();
    /** Slabs, stairs, drifts and bits cut from the themes' blocks for the places' ground (SPEC §38.14), after them. */
    public static final java.util.Map<String, DeferredBlock<? extends Block>> DEPTH_BLOCKS = dev.awaken.gates.block.DepthBlocks.register(BLOCKS,
            id -> THEME_BLOCKS.containsKey(id) ? THEME_BLOCKS.get(id) : WORLD_BLOCKS.stream().filter(b -> b.getId().getPath().equals(id)).findFirst().orElse(null));
    public static final List<DeferredItem<BlockItem>> DEPTH_ITEMS = DEPTH_BLOCKS.values().stream().map(ITEMS::registerSimpleBlockItem).toList();

    /** A theme block by id (SPEC §38.10), for code that names one. */
    public static Block theme(String id) {
        DeferredBlock<? extends Block> b = THEME_BLOCKS.get(id);
        if (b == null) b = SETS.get(id);
        if (b == null) b = DEPTH_BLOCKS.get(id);
        return b.get();
    }

    // ---- the Gate Rig's Mirror Hall (SPEC §17): the turning mirror, the lens, the seal --------------------------------

    public static final DeferredBlock<RigBlocks.Mirror> RIG_MIRROR = BLOCKS.registerBlock("gate_rig_mirror",
            RigBlocks.Mirror::new, p -> unbreakable(MapColor.QUARTZ, SoundType.GLASS, 4).apply(p).noOcclusion());
    public static final DeferredBlock<RigBlocks.Lens> RIG_LENS = BLOCKS.registerBlock("gate_rig_lens",
            RigBlocks.Lens::new, unbreakable(MapColor.GOLD, SoundType.AMETHYST, 13));
    public static final DeferredBlock<RigBlocks.Seal> RIG_SEAL = BLOCKS.registerBlock("gate_rig_seal",
            RigBlocks.Seal::new, p -> unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST, 0).apply(p)
                    .lightLevel(s -> s.getValue(RigBlocks.Seal.LIT) ? 15 : 4));
    // ---- the Gate Rig's Gauntlet (SPEC §17.5): the presses and the wall you run along ----------------------------------

    /** The Foundry's press: riveted iron, its face banded in warning amber. */
    public static final DeferredBlock<Block> PRESS = BLOCKS.registerSimpleBlock("gate_press", unbreakable(MapColor.COLOR_GRAY, SoundType.METAL, 0));
    /** The Monastery's ram: a block of rime-hard blue ice in a frame of iron. */
    public static final DeferredBlock<Block> FROST_RAM = BLOCKS.registerSimpleBlock("gate_frost_ram", unbreakable(MapColor.ICE, SoundType.GLASS, 3));
    /** The wall a Gauntlet's course runs along over the drop: dark panels with a lit groove at running height. */
    public static final DeferredBlock<Block> RUN_WALL = BLOCKS.registerSimpleBlock("gate_run_wall", unbreakable(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_TILES, 7));

    public static final List<DeferredItem<BlockItem>> RIG_ITEMS = List.of(ITEMS.registerSimpleBlockItem(RIG_MIRROR),
            ITEMS.registerSimpleBlockItem(RIG_LENS), ITEMS.registerSimpleBlockItem(RIG_SEAL), ITEMS.registerSimpleBlockItem(PRESS),
            ITEMS.registerSimpleBlockItem(FROST_RAM), ITEMS.registerSimpleBlockItem(RUN_WALL));

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
    // gate worlds' live layer (SPEC §38.8): bells struck and tolling, the Ringer, the Flood, the bone fall, the swing,
    // the fall caught, a secret found, a world's title card
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_STRIKE = sound("gate.world.strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_HANDBELL = sound("gate.world.handbell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_TOLL = sound("gate.world.toll");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_RINGER = sound("gate.world.ringer");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_FLOOD = sound("gate.world.flood");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_BONES = sound("gate.world.bone_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_SWING = sound("gate.world.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_CATCH = sound("gate.world.catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_FOUND = sound("gate.world.found");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_ARRIVE = sound("gate.world.arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_GREAT = sound("gate.world.great_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_HUM = sound("gate.world.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_DRIP = sound("gate.world.drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WORLD_STORM = sound("gate.world.storm");
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
    // gate worlds (SPEC §38.6, §38.9): each world's sound bed (the bells and the rest are above)
    public static final List<String> WORLD_SOUNDS = List.of("belfry", "ossuary", "shaft", "field", "rest");
    static {
        for (String w : WORLD_SOUNDS) sound("gate.world." + w);
    }
    /**
     * Every theme after the Bell Crypt's sounds (SPEC §38.10 on), registered as gate.world.&lt;name&gt;: three beds a theme, its
     * touchstone, its events and moments, and its arrival sting (arrive_&lt;theme&gt;). From tools/art/specs/
     * audio_gate_worlds_caves.py (38.10), audio_gate_worlds_d.py (38.11), audio_gate_worlds_c.py (38.12) and
     * audio_gate_worlds_b.py (38.13).
     */
    public static final List<String> THEME_SOUNDS = List.of(
            "hollow_glow", "hollow_roots", "hollow_bloom", "mine_works", "mine_chasm", "mine_sea",
            "hive_comb", "hive_brood", "hive_lake", "temple_courts", "temple_halls", "temple_falls",
            "puff", "bellows", "drum", "brazier", "tremor", "swarm", "rains", "wakening", "bloom", "surface", "choir", "saint",
            "arrive_fungal_hollow", "arrive_ember_mines", "arrive_hive", "arrive_overgrown_temple",
            // the D themes (38.11)
            "archive_stacks", "archive_atrium", "archive_ink", "foundry_works", "foundry_pipes", "foundry_engine",
            "desert_wind", "desert_city", "desert_glass", "market_street", "market_water", "market_festival",
            "page", "pages", "tide", "bookworm", "valve", "overdrive", "powercut", "clockwork", "chime", "sandstorm", "shimmer",
            "walker", "wish", "fireworks", "release", "festival", "spirits",
            "arrive_drowned_archive", "arrive_clockwork_foundry", "arrive_glass_desert", "arrive_lantern_night_market",
            // the C themes (38.12)
            "frost_chant", "frost_ice", "frost_wind", "palace_halls", "palace_under", "palace_white",
            "lab_hum", "lab_servers", "lab_sim", "dream_house", "dream_hall", "dream_deep",
            "wheel", "bells", "blizzard", "icecrack", "still", "monk", "mirror", "shatter", "doubled", "whale", "reflection",
            "console", "lockdown", "breach", "glitch", "draw", "musicbox", "lullaby", "alarm", "stirs", "sleepwalker", "fold",
            "arrive_frost_monastery", "arrive_mirror_palace", "arrive_precursor_lab", "arrive_nightmare",
            // the B themes and the rare worlds (38.13)
            "sky_wind", "sky_ruins", "sky_high", "abyss_steps", "abyss_lake", "abyss_void",
            "rare_quiet", "rare_rain", "rare_aurora", "rare_campfire", "rare_heart",
            "skychime", "gale", "sunburst", "great_wind", "roc", "offering", "hush", "hunger", "starfall", "knock", "watcher",
            "arrive_rare", "echoes", "heartbeat", "doorway", "arrive_sky_archipelago", "arrive_abyssal_shrine");
    private static final java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> THEME_SOUND = new java.util.HashMap<>();
    static {
        for (String n : THEME_SOUNDS) THEME_SOUND.put(n, sound("gate.world." + n));
    }

    /** A theme's sound by its name (gate.world.&lt;name&gt;), or null when this build has none by that name. */
    public static SoundEvent worldSound(String name) {
        DeferredHolder<SoundEvent, SoundEvent> h = THEME_SOUND.get(name);
        return h == null ? null : h.get();
    }

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
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_WINDUP = sound("gate.rig.windup");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_PRESS = sound("gate.rig.press");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_LIFT = sound("gate.rig.lift");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_RING = sound("gate.rig.ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RIG_MEDAL = sound("gate.rig.medal");

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
    // a world floor's small life (SPEC §38.9; sprites from tools/art/specs/gate_worlds_bell.py), made only on the client
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_MOTH =
            PARTICLES.register("gate_world_moth", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_BAT =
            PARTICLES.register("gate_world_bat", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_FISH =
            PARTICLES.register("gate_world_fish", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_RAT =
            PARTICLES.register("gate_world_rat", () -> new SimpleParticleType(false));
    // a world's air (SPEC §38.9 "Air"; sprites from tools/art/specs/gate_worlds_air.py), tinted on the client
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_WISP =
            PARTICLES.register("gate_air_wisp", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_STREAK =
            PARTICLES.register("gate_air_streak", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_FLAKE =
            PARTICLES.register("gate_air_flake", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_ORB =
            PARTICLES.register("gate_air_orb", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> P_FLASH =
            PARTICLES.register("gate_air_flash", () -> new SimpleParticleType(false));
    /** A glowing mote the server sends in a colour (a world's rule showing the way: spores, cinders, pollen, fireflies). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<net.minecraft.core.particles.ColorParticleOption>> P_TRAIL =
            PARTICLES.register("gate_world_trail", () -> coloured(false));
    /** Something light falling or blown in a colour (spore dust, ash, petals, leaves), sent by the server for a world's event. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<net.minecraft.core.particles.ColorParticleOption>> P_DRIFT =
            PARTICLES.register("gate_world_drift", () -> coloured(false));

    private static ParticleType<net.minecraft.core.particles.ColorParticleOption> coloured(boolean always) {
        return new ParticleType<>(always) {
            @Override
            public com.mojang.serialization.MapCodec<net.minecraft.core.particles.ColorParticleOption> codec() {
                return net.minecraft.core.particles.ColorParticleOption.codec(this);
            }

            @Override
            public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.core.particles.ColorParticleOption> streamCodec() {
                return net.minecraft.core.particles.ColorParticleOption.streamCodec(this);
            }
        };
    }

    /** A {@link #P_TRAIL} mote of a colour. */
    public static net.minecraft.core.particles.ColorParticleOption trail(int rgb) {
        return net.minecraft.core.particles.ColorParticleOption.create(P_TRAIL.get(), 0xFF000000 | rgb);
    }

    /** A {@link #P_DRIFT} flake of a colour. */
    public static net.minecraft.core.particles.ColorParticleOption drift(int rgb) {
        return net.minecraft.core.particles.ColorParticleOption.create(P_DRIFT.get(), 0xFF000000 | rgb);
    }

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
    /** The Atlas of Gates and the world director's memory (SPEC §38). */
    public static final Supplier<AttachmentType<dev.awaken.gates.worlds.GateAtlas>> ATLAS = ATTACHMENTS.register("gate_atlas",
            () -> AttachmentType.builder(dev.awaken.gates.worlds.GateAtlas::new)
                    .serialize(dev.awaken.gates.worlds.GateAtlas.MAP_CODEC, a -> !a.isEmpty()).copyOnDeath().build());
    /** A gate monster's world and look, synced to whoever sees it (SPEC §38.8). */
    public static final Supplier<AttachmentType<dev.awaken.gates.worlds.WorldLook>> WORLD_LOOK = ATTACHMENTS.register("gate_world_look",
            () -> AttachmentType.builder(() -> dev.awaken.gates.worlds.WorldLook.NONE)
                    .serialize(dev.awaken.gates.worlds.WorldLook.MAP_CODEC, dev.awaken.gates.worlds.WorldLook::isSet)
                    .sync(dev.awaken.gates.worlds.WorldLook.STREAM_CODEC).build());
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
        for (var item : WORLD_ITEMS) e.accept(item.get());
        for (var item : THEME_ITEMS) e.accept(item.get());
        for (var item : SET_ITEMS) e.accept(item.get());
        for (var item : DEPTH_ITEMS) e.accept(item.get());
        for (var item : RIG_ITEMS) e.accept(item.get());
    }
}
