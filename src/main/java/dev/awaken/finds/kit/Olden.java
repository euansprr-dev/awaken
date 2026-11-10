package dev.awaken.finds.kit;

import dev.awaken.terrain.block.BlockSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Olden stone (SPEC §61.2): the masonry of the old world's monuments, one family of thirty. Textures, models and the rest
 * from {@code tools/art/specs/finds_olden.py}, which keeps the same table. Nothing here does anything but stand, except
 * the lamp, which can be lit, and the three that glow.
 */
public final class Olden {
    private Olden() {}

    private static final BlockSet SET = new BlockSet();
    private static final java.util.List<String> IDS = new java.util.ArrayList<>();

    private static String id(String id) {
        IDS.add(id);
        return id;
    }

    /** A lamp of the old world: dark until a flame is brought to it, then it burns for good. */
    public static final class Lamp extends Block {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public Lamp(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LIT);
        }
    }

    private static BlockBehaviour.Properties stone(BlockBehaviour.Properties p, MapColor c, SoundType sound) {
        return p.mapColor(c).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(sound);
    }

    private static DeferredBlock<Block> cube(String id, MapColor c, SoundType sound, int light) {
        return SET.withItem(SET.blocks.registerSimpleBlock(id(id), p -> stone(p, c, sound).lightLevel(s -> light)));
    }

    private static DeferredBlock<SlabBlock> slab(DeferredBlock<Block> of, MapColor c) {
        return SET.withItem(SET.blocks.registerBlock(id(of.getId().getPath() + "_slab"), SlabBlock::new, p -> stone(p, c, SoundType.DEEPSLATE_BRICKS)));
    }

    private static DeferredBlock<StairBlock> stairs(DeferredBlock<Block> of, MapColor c) {
        return SET.withItem(SET.blocks.registerBlock(id(of.getId().getPath() + "_stairs"), p -> new StairBlock(of.get().defaultBlockState(), p),
                p -> stone(p, c, SoundType.DEEPSLATE_BRICKS)));
    }

    public static final DeferredBlock<Block> STONE = cube("olden_stone", MapColor.STONE, SoundType.DEEPSLATE_BRICKS, 0),
            WORN = cube("olden_stone_worn", MapColor.STONE, SoundType.DEEPSLATE_BRICKS, 0),
            MOSSY = cube("olden_stone_mossy", MapColor.COLOR_GREEN, SoundType.DEEPSLATE_BRICKS, 0),
            DARK = cube("olden_stone_dark", MapColor.DEEPSLATE, SoundType.DEEPSLATE_BRICKS, 0),
            BLOCK = cube("olden_block", MapColor.STONE, SoundType.DEEPSLATE_TILES, 0),
            BLOCK_DARK = cube("olden_block_dark", MapColor.DEEPSLATE, SoundType.DEEPSLATE_TILES, 0),
            BAND = cube("olden_band", MapColor.STONE, SoundType.DEEPSLATE_TILES, 0),
            PANEL = cube("olden_panel", MapColor.STONE, SoundType.DEEPSLATE_TILES, 0),
            GLYPH = cube("olden_glyph", MapColor.STONE, SoundType.DEEPSLATE_TILES, 0),
            FLAG = cube("olden_flag", MapColor.STONE, SoundType.DEEPSLATE_TILES, 0),
            MOSAIC = cube("olden_mosaic", MapColor.TERRACOTTA_CYAN, SoundType.DEEPSLATE_TILES, 0),
            RUBBLE = cube("olden_rubble", MapColor.STONE, SoundType.TUFF, 0),
            CAPITAL = cube("olden_capital", MapColor.STONE, SoundType.DEEPSLATE_TILES, 0),
            BRONZE = cube("olden_bronze", MapColor.WARPED_NYLIUM, SoundType.COPPER, 0),
            BRONZE_BAND = cube("olden_bronze_band", MapColor.STONE, SoundType.DEEPSLATE_BRICKS, 0),
            DOOR = cube("olden_door", MapColor.WARPED_NYLIUM, SoundType.COPPER, 0),
            SEAL = cube("olden_seal", MapColor.WARPED_WART_BLOCK, SoundType.COPPER, 9),
            LICHEN = cube("olden_lichen", MapColor.DEEPSLATE, SoundType.DEEPSLATE_BRICKS, 6);
    public static final DeferredBlock<RotatedPillarBlock> PILLAR = SET.withItem(SET.blocks.registerBlock(id("olden_pillar"), RotatedPillarBlock::new,
            p -> stone(p, MapColor.STONE, SoundType.DEEPSLATE_TILES)));
    public static final DeferredBlock<Lamp> LAMP = SET.withItem(SET.blocks.registerBlock(id("olden_lamp"), Lamp::new,
            p -> stone(p, MapColor.STONE, SoundType.DEEPSLATE_TILES).lightLevel(s -> s.getValue(Lamp.LIT) ? 14 : 0)));
    public static final DeferredBlock<SlabBlock> STONE_SLAB = slab(STONE, MapColor.STONE), WORN_SLAB = slab(WORN, MapColor.STONE), DARK_SLAB = slab(DARK, MapColor.DEEPSLATE),
            BLOCK_SLAB = slab(BLOCK, MapColor.STONE), FLAG_SLAB = slab(FLAG, MapColor.STONE);
    public static final DeferredBlock<StairBlock> STONE_STAIRS = stairs(STONE, MapColor.STONE), WORN_STAIRS = stairs(WORN, MapColor.STONE),
            DARK_STAIRS = stairs(DARK, MapColor.DEEPSLATE), BLOCK_STAIRS = stairs(BLOCK, MapColor.STONE);
    public static final DeferredBlock<WallBlock> WALL = SET.withItem(SET.blocks.registerBlock(id("olden_stone_wall"), WallBlock::new,
            p -> stone(p, MapColor.STONE, SoundType.DEEPSLATE_BRICKS).forceSolidOn()));

    /** Whether a block is of the family: what the ageing pass works on. */
    public static boolean is(BlockState s) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return id.getNamespace().equals(dev.awaken.Awaken.MODID) && id.getPath().startsWith("olden_");
    }

    public static String[] ids() {
        return IDS.toArray(String[]::new);
    }

    public static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
