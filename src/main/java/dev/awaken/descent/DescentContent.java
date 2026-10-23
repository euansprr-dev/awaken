package dev.awaken.descent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.descent.block.ConduitBlock;
import dev.awaken.descent.block.DescentPitBlock;
import dev.awaken.descent.block.UseBlock;
import dev.awaken.descent.block.WaystoneBlock;
import dev.awaken.descent.entity.GraveEcho;
import dev.awaken.descent.entity.ShadowSelf;
import dev.awaken.descent.player.DeepPack;
import dev.awaken.descent.player.DescentData;
import dev.awaken.descent.world.EndlessBiomeSource;
import dev.awaken.descent.world.StratumGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Every registry entry of the descent module. Block names must match tools/content/descent_assets.py. */
public final class DescentContent {
    private DescentContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Awaken.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    private static final List<DeferredItem<BlockItem>> TAB = new ArrayList<>();

    // ---- worldgen codecs ----------------------------------------------------------------------------------------------

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<StratumGenerator>> STRATUM_GENERATOR =
            GENERATORS.register("stratum", () -> StratumGenerator.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<EndlessBiomeSource>> ENDLESS_BIOMES =
            BIOME_SOURCES.register("endless", () -> EndlessBiomeSource.CODEC);

    // ---- blocks -------------------------------------------------------------------------------------------------------

    private static BlockBehaviour.Properties unbreakable(BlockBehaviour.Properties p) {
        return p.strength(-1.0F, 3600000.0F).pushReaction(PushReaction.IMMOVEABLE);
    }

    private static DeferredBlock<Block> simple(String name, UnaryOperator<BlockBehaviour.Properties> props) {
        DeferredBlock<Block> b = BLOCKS.registerSimpleBlock(name, props);
        TAB.add(ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static <B extends Block> DeferredBlock<B> block(String name, java.util.function.Function<BlockBehaviour.Properties, B> f,
                                                          UnaryOperator<BlockBehaviour.Properties> props) {
        DeferredBlock<B> b = BLOCKS.registerBlock(name, f, props);
        TAB.add(ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static UnaryOperator<BlockBehaviour.Properties> rock(float hardness, SoundType sound, MapColor colour) {
        return p -> p.strength(hardness, hardness * 3).sound(sound).mapColor(colour).requiresCorrectToolForDrops();
    }

    private static UnaryOperator<BlockBehaviour.Properties> glow(float hardness, SoundType sound, int light, MapColor colour) {
        return p -> p.strength(hardness, hardness * 2).sound(sound).mapColor(colour).lightLevel(s -> light);
    }

    // Shared
    public static final DeferredBlock<Block> ABYSS_STONE = simple("abyss_stone", rock(3.0F, SoundType.DEEPSLATE, MapColor.COLOR_BLACK));
    public static final DeferredBlock<Block> WELL_STONE = simple("well_stone", rock(4.0F, SoundType.POLISHED_DEEPSLATE, MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> WELL_GLYPH = simple("well_glyph", p -> rock(4.0F, SoundType.POLISHED_DEEPSLATE, MapColor.COLOR_LIGHT_BLUE).apply(p).lightLevel(s -> 10));
    public static final DeferredBlock<Block> WELL_LIGHT = simple("well_light", glow(2.0F, SoundType.GLASS, 15, MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<UseBlock> WELL_LIFT = block("well_lift", p -> new UseBlock(p, DescentTravel::useWellLift),
            p -> unbreakable(p).sound(SoundType.COPPER).lightLevel(s -> 9).mapColor(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<UseBlock> ASCENT_STONE = block("ascent_stone", p -> new UseBlock(p, DescentTravel::useAscent),
            p -> unbreakable(p).sound(SoundType.POLISHED_DEEPSLATE).lightLevel(s -> 9).mapColor(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<WaystoneBlock> WAYSTONE = block("waystone", WaystoneBlock::new,
            p -> unbreakable(p).sound(SoundType.LODESTONE).noOcclusion().mapColor(MapColor.COLOR_ORANGE)
                    .lightLevel(s -> s.getValue(WaystoneBlock.LIT) ? 14 : 5));
    public static final DeferredBlock<DescentPitBlock> DESCENT_PIT = block("descent_pit", DescentPitBlock::new,
            p -> unbreakable(p).noCollision().noOcclusion().sound(SoundType.SCULK).lightLevel(s -> 7).mapColor(MapColor.COLOR_BLACK));
    public static final DeferredBlock<ConduitBlock> CONDUIT = block("conduit", ConduitBlock::new,
            p -> unbreakable(p).sound(SoundType.COPPER).noOcclusion().mapColor(MapColor.COLOR_CYAN)
                    .lightLevel(s -> s.getValue(ConduitBlock.LIT) ? 13 : 3));
    public static final DeferredBlock<Block> FOUNDRY_SEAL = simple("foundry_seal", p -> unbreakable(p).sound(SoundType.NETHERITE_BLOCK).mapColor(MapColor.COLOR_GRAY));

    // 1 The Rim
    public static final DeferredBlock<Block> RIM_STONE = simple("rim_stone", rock(2.5F, SoundType.TUFF, MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<Block> RIM_SOIL = simple("rim_soil", p -> p.strength(0.6F).sound(SoundType.ROOTED_DIRT).mapColor(MapColor.DIRT));
    public static final DeferredBlock<Block> RIM_MOSS = simple("rim_moss", glow(0.6F, SoundType.MOSS, 9, MapColor.COLOR_GREEN));
    // 2 The Hanging Gardens
    public static final DeferredBlock<Block> GARDEN_STONE = simple("garden_stone", rock(2.5F, SoundType.CALCITE, MapColor.TERRACOTTA_WHITE));
    public static final DeferredBlock<Block> GARDEN_BLOOM = simple("garden_bloom", glow(0.4F, SoundType.AZALEA, 12, MapColor.COLOR_PINK));
    // 3 The Drowned Choir
    public static final DeferredBlock<Block> CHOIR_STONE = simple("choir_stone", rock(3.0F, SoundType.STONE, MapColor.COLOR_CYAN));
    public static final DeferredBlock<Block> CHOIR_BRICKS = simple("choir_bricks", rock(3.0F, SoundType.STONE, MapColor.COLOR_CYAN));
    public static final DeferredBlock<Block> CHOIR_CRYSTAL = simple("choir_crystal", glow(1.5F, SoundType.AMETHYST, 12, MapColor.COLOR_LIGHT_BLUE));
    // 4 The Bone Orchard
    public static final DeferredBlock<Block> ORCHARD_ASH = simple("orchard_ash", p -> p.strength(0.6F).sound(SoundType.SOUL_SOIL).mapColor(MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<Block> MARROW_STONE = simple("marrow_stone", rock(3.0F, SoundType.BONE_BLOCK, MapColor.SAND));
    public static final DeferredBlock<Block> MARROW_LANTERN = simple("marrow_lantern", glow(1.0F, SoundType.BONE_BLOCK, 14, MapColor.COLOR_YELLOW));
    // 5 The Silent Halls
    public static final DeferredBlock<Block> HUSH_STONE = simple("hush_stone", rock(3.0F, SoundType.DEEPSLATE, MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> HUSH_TILES = simple("hush_tiles", rock(3.0F, SoundType.DEEPSLATE_TILES, MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> HUSH_LAMP = simple("hush_lamp", glow(1.5F, SoundType.GLASS, 8, MapColor.COLOR_LIGHT_GRAY));
    // 6 The Precursor Foundry
    public static final DeferredBlock<Block> FOUNDRY_PLATE = simple("foundry_plate", rock(5.0F, SoundType.NETHERITE_BLOCK, MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> FOUNDRY_GRATE = simple("foundry_grate", rock(5.0F, SoundType.COPPER, MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> FOUNDRY_LAMP = simple("foundry_lamp", glow(3.0F, SoundType.COPPER, 15, MapColor.COLOR_ORANGE));
    // 7 The Inverted Sea
    public static final DeferredBlock<Block> BRINE_STONE = simple("brine_stone", rock(3.0F, SoundType.STONE, MapColor.WARPED_NYLIUM));
    public static final DeferredBlock<Block> TIDE_CORAL = simple("tide_coral", glow(1.0F, SoundType.CORAL_BLOCK, 11, MapColor.COLOR_CYAN));
    // 8 The Dreaming Stratum
    public static final DeferredBlock<Block> DREAM_CLOUD = simple("dream_cloud", p -> p.strength(0.8F).sound(SoundType.WOOL).mapColor(MapColor.COLOR_PINK));
    public static final DeferredBlock<Block> DREAM_TILE = simple("dream_tile", rock(2.0F, SoundType.CALCITE, MapColor.COLOR_MAGENTA));
    public static final DeferredBlock<Block> DREAM_GLOW = simple("dream_glow", glow(1.0F, SoundType.AMETHYST, 13, MapColor.COLOR_PINK));
    // 9 The Hollow Court
    public static final DeferredBlock<Block> COURT_STONE = simple("court_stone", rock(4.0F, SoundType.DEEPSLATE_TILES, MapColor.COLOR_BLACK));
    public static final DeferredBlock<Block> COURT_TILE = simple("court_tile", rock(4.0F, SoundType.POLISHED_DEEPSLATE, MapColor.COLOR_PURPLE));
    public static final DeferredBlock<Block> COURT_BRAZIER = simple("court_brazier", glow(3.0F, SoundType.LANTERN, 15, MapColor.GOLD));
    // 10 The Seed
    public static final DeferredBlock<Block> SEED_SHELL = simple("seed_shell", rock(5.0F, SoundType.AMETHYST, MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<Block> SEED_VEIN = simple("seed_vein", glow(3.0F, SoundType.AMETHYST, 10, MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<Block> SEED_CORE = simple("seed_core", p -> unbreakable(p).sound(SoundType.AMETHYST).lightLevel(s -> 15).mapColor(MapColor.DIAMOND));

    // ---- entities -----------------------------------------------------------------------------------------------------

    public static final DeferredHolder<EntityType<?>, EntityType<GraveEcho>> GRAVE_ECHO = ENTITIES.registerEntityType("grave_echo",
            GraveEcho::new, MobCategory.MISC, b -> b.sized(0.8F, 1.8F).clientTrackingRange(10).updateInterval(20).noSummon().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowSelf>> SHADOW_SELF = ENTITIES.registerEntityType("shadow_self",
            ShadowSelf::new, MobCategory.MONSTER, b -> b.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8).noLootTable().noSave().fireImmune());

    // ---- attachments --------------------------------------------------------------------------------------------------

    /** The player's Descent record (lit Waystones, graves, visits...). Survives death. */
    public static final Supplier<AttachmentType<DescentData>> DATA = ATTACHMENTS.register("descent", () -> AttachmentType
            .builder(DescentData::new).serialize(DescentData.MAP_CODEC).copyOnDeath().build());
    /** Loot carried inside the Descent. Not copied on death: the Grave Echo takes it. Synced to the owner for the Status screen. */
    public static final Supplier<AttachmentType<DeepPack>> DEEP_PACK = ATTACHMENTS.register("deep_pack", () -> AttachmentType
            .builder(DeepPack::new).serialize(DeepPack.MAP_CODEC).sync((holder, to) -> holder == to, DeepPack.STREAM_CODEC).build());
    /** Flags on mobs the Descent spawned ({@link dev.awaken.descent.spawn.DescentSpawner}). */
    public static final Supplier<AttachmentType<Integer>> MOB_FLAGS = ATTACHMENTS.register("descent_mob", () -> AttachmentType
            .builder(() -> 0).serialize(Codec.INT.fieldOf("flags")).build());

    // ---- sounds -------------------------------------------------------------------------------------------------------

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WELL = sound("descent.well");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_DROP = sound("descent.drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_ARRIVE = sound("descent.arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SEALED = sound("descent.sealed");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WAYSTONE_LIGHT = sound("descent.waystone_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WAYSTONE_REST = sound("descent.waystone_rest");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_WIND = sound("descent.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CHOIR = sound("descent.choir");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_FLOOD = sound("descent.flood");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RISE = sound("descent.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_LISTENER = sound("descent.listener");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CONDUIT_ON = sound("descent.conduit_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CONDUIT_OFF = sound("descent.conduit_off");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_FOUNDRY_OPEN = sound("descent.foundry_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_TIDE = sound("descent.tide");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_ROOM_SHIFT = sound("descent.room_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SHADOW = sound("descent.shadow");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_GRAVE = sound("descent.grave");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_GRAVE_RECOVER = sound("descent.grave_recover");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_STRAIN = sound("descent.strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SEED = sound("descent.seed");

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        GENERATORS.register(modBus);
        BIOME_SOURCES.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(DescentContent::creativeTab);
        modBus.addListener(DescentContent::attributes);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CoreModule.TAB_KEY)) {
            for (var item : TAB) event.accept(item.get());
        }
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(SHADOW_SELF.get(), ShadowSelf.createAttributes().build());
    }
}
