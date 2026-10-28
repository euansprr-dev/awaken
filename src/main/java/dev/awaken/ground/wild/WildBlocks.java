package dev.awaken.ground.wild;

import dev.awaken.Awaken;
import dev.awaken.terrain.block.BlockSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

/**
 * The wild's own plants and ground (SPEC §41.2, the Wild Lands stage 1a): about two dozen blocks in Minecraft's own manner.
 * They exist in every world; the worldgen that grows them (stage 1b) is in the {@code awaken:awakened_ground} pack. Art from
 * tools/art/specs/wild_plants.py; blockstates, models, items, loot, tags and lang from tools/content/wild_plants_content.py.
 */
public final class WildBlocks {
    private WildBlocks() {}

    private static final BlockSet SET = new BlockSet();
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);

    /** A plant you walk through: no collision, broken by a touch, burns, pops off when pushed. */
    private static BlockBehaviour.Properties plant(BlockBehaviour.Properties p, SoundType sound, BlockBehaviour.OffsetType offset) {
        return p.mapColor(MapColor.PLANT).noCollision().instabreak().sound(sound).offsetType(offset).ignitedByLava()
                .pushReaction(PushReaction.POPPED);
    }

    private static <B extends Block> DeferredBlock<B> grass(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.OffsetType offset) {
        return SET.withItem(SET.blocks.registerBlock(name, f, p -> plant(p, SoundType.GRASS, offset).replaceable()));
    }

    private static DeferredBlock<WildDoubleBlock> tall(String name, WildKind kind, boolean replaceable) {
        return SET.withItem(SET.blocks.registerBlock(name, p -> new WildDoubleBlock(kind, p), p -> {
            BlockBehaviour.Properties q = plant(p, SoundType.GRASS, BlockBehaviour.OffsetType.XZ);
            return replaceable ? q.replaceable() : q;
        }), DoubleHighBlockItem::new);
    }

    private static DeferredBlock<WildBushBlock> bush(String name, WildKind kind, int height, boolean replaceable) {
        return SET.withItem(SET.blocks.registerBlock(name, p -> new WildBushBlock(kind, height, p), p -> {
            BlockBehaviour.Properties q = plant(p, SoundType.GRASS, BlockBehaviour.OffsetType.XZ);
            return replaceable ? q.replaceable() : q;
        }));
    }

    // grasses
    public static final DeferredBlock<MeadowGrassBlock> MEADOW_GRASS = grass("meadow_grass", MeadowGrassBlock::new, BlockBehaviour.OffsetType.XYZ);
    public static final DeferredBlock<WildBushBlock> FROST_GRASS = bush("frost_grass", WildKind.FROST, 12, true);
    public static final DeferredBlock<WildDoubleBlock> LONG_GRASS = tall("long_grass", WildKind.LONG, true);
    /** Wild rye's foot; its item is the rye ("wild_rye"), which places all three blocks. */
    public static final DeferredBlock<RyeStemsBlock> RYE_STEMS = SET.withItem(SET.blocks.registerBlock("wild_rye", RyeStemsBlock::new,
            p -> plant(p, SoundType.GRASS, BlockBehaviour.OffsetType.XZ).replaceable()));
    public static final DeferredBlock<WildRyeBlock> WILD_RYE = SET.blocks.registerBlock("wild_rye_top", WildRyeBlock::new,
            p -> plant(p, SoundType.GRASS, BlockBehaviour.OffsetType.XZ).replaceable());
    public static final DeferredBlock<TrampledGrassBlock> TRAMPLED_GRASS = SET.blocks.registerBlock("trampled_grass", TrampledGrassBlock::new,
            p -> plant(p, SoundType.GRASS, BlockBehaviour.OffsetType.NONE).replaceable().randomTicks());
    public static final DeferredBlock<CloverBlock> CLOVER = SET.withItem(SET.blocks.registerBlock("clover", CloverBlock::new,
            p -> p.mapColor(MapColor.PLANT).noCollision().sound(SoundType.PINK_PETALS).pushReaction(PushReaction.POPPED)));
    // flowers and herbs
    public static final DeferredBlock<WildDoubleBlock> COW_PARSLEY = tall("cow_parsley", WildKind.PARSLEY, false);
    public static final DeferredBlock<WildDoubleBlock> FOXGLOVE = tall("foxglove", WildKind.FOXGLOVE, false);
    public static final DeferredBlock<WildBushBlock> THISTLE = bush("thistle", WildKind.THISTLE, 14, false);
    public static final DeferredBlock<WildBushBlock> HEATHER = bush("heather", WildKind.HEATHER, 10, false);
    public static final DeferredBlock<WildBushBlock> GORSE = bush("gorse", WildKind.GORSE, 15, false);
    public static final DeferredBlock<WildBushBlock> DRY_TUSSOCK = bush("dry_tussock", WildKind.TUSSOCK, 10, true);
    // shrubs, ferns, the water's edge
    public static final DeferredBlock<BrambleBlock> BRAMBLE = SET.withItem(SET.blocks.registerBlock("bramble", BrambleBlock::new,
            p -> plant(p, SoundType.SWEET_BERRY_BUSH, BlockBehaviour.OffsetType.NONE).randomTicks()));
    public static final DeferredBlock<WildDoubleBlock> BRACKEN = tall("bracken", WildKind.BRACKEN, true);
    public static final DeferredBlock<WildDoubleBlock> REEDS = tall("reeds", WildKind.REEDS, false);
    public static final DeferredBlock<LeavesBlock> HAWTHORN_LEAVES = SET.withItem(SET.blocks.registerBlock("hawthorn_leaves",
            p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
            p -> p.mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.AZALEA_LEAVES).noOcclusion()
                    .isValidSpawn((s, l, pos, type) -> false).isSuffocating((s, l, pos) -> false)
                    .ignitedByLava().pushReaction(PushReaction.POPPED).isRedstoneConductor((s, l, pos) -> false)));
    // still things and ground
    public static final DeferredBlock<BracketFungusBlock> BRACKET_FUNGUS = SET.withItem(SET.blocks.registerBlock("bracket_fungus",
            BracketFungusBlock::new, p -> p.mapColor(MapColor.TERRACOTTA_WHITE).noCollision().instabreak().sound(SoundType.FUNGUS)
                    .pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<AntlerShedBlock> ANTLER_SHED = SET.withItem(SET.blocks.registerBlock("antler_shed",
            AntlerShedBlock::new, p -> p.mapColor(MapColor.SAND).noCollision().strength(0.3F).sound(SoundType.BONE_BLOCK)
                    .pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<WallowBlock> WALLOW = SET.withItem(SET.blocks.registerBlock("wallow", WallowBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_CYAN).strength(0.5F).sound(SoundType.MUD).speedFactor(0.8F)));
    public static final DeferredBlock<Block> SALT_LICK = SET.withItem(SET.blocks.registerSimpleBlock("salt_lick",
            p -> p.mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.0F, 3.0F)
                    .sound(SoundType.CALCITE)));
    public static final DeferredBlock<Block> TRODDEN_EARTH = SET.withItem(SET.blocks.registerSimpleBlock("trodden_earth",
            p -> p.mapColor(MapColor.DIRT).strength(0.6F).sound(SoundType.ROOTED_DIRT)));
    public static final DeferredBlock<WildSwardBlock> WILD_SWARD = SET.withItem(SET.blocks.registerBlock("wild_sward", WildSwardBlock::new,
            p -> p.mapColor(MapColor.GRASS).randomTicks().strength(0.6F).sound(SoundType.GRASS)));

    public static final FoodProperties BLACKBERRY_FOOD = new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build();
    public static final DeferredItem<Item> BLACKBERRIES = ITEMS.registerItem("blackberries", Item::new, p -> p.food(BLACKBERRY_FOOD));

    /** Pushing through long grass, rye or bracken; the meadow's hiss under wind (a loop the client fades with the wind). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SWISH = SOUNDS.register("wild_plants.swish",
            () -> SoundEvent.createVariableRangeEvent(Awaken.id("wild_plants.swish")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HISS = SOUNDS.register("wild_plants.hiss",
            () -> SoundEvent.createVariableRangeEvent(Awaken.id("wild_plants.hiss")));
    /** Seed fluff lifted off seeding grass and rye by a gust. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEED = PARTICLES.register("wild_seed", () -> new SimpleParticleType(false));

    /** Every block registered here (for the tests). */
    public static java.util.Collection<DeferredHolder<Block, ? extends Block>> blocks() {
        return SET.blocks.getEntries();
    }

    public static void init(IEventBus modBus) {
        SET.init(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
    }
}
