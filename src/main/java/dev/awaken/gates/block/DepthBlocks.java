package dev.awaken.gates.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The depth blocks (SPEC §38.14): slabs and stairs cut from a theme's ground and wall blocks (wearing their texture), a
 * drift that piles up in layers like snow, and a scatter of small bits underfoot. Outside a gate they're blocks like the
 * theme's others; inside one nothing breaks. Textures, models and the rest from tools/art/specs/gate_depth.py, which
 * keeps the same table.
 */
public final class DepthBlocks {
    private DepthBlocks() {}

    public enum Kind { SLAB, STAIRS, DRIFT, BITS }

    /** One block: its id, kind, the block it's cut from (slabs and stairs: their look and strength), map colour, sound. */
    public record Def(String name, Kind kind, String base, MapColor color, SoundType sound) {}

    private static Def slab(String base, MapColor c, SoundType s) {
        return new Def(base + "_slab", Kind.SLAB, base, c, s);
    }

    private static Def stairs(String base, MapColor c, SoundType s) {
        return new Def(base + "_stairs", Kind.STAIRS, base, c, s);
    }

    public static final List<Def> DEFS = List.of(
            // Hive
            slab("hive_wax", MapColor.COLOR_YELLOW, SoundType.CANDLE),
            stairs("hive_wax", MapColor.COLOR_YELLOW, SoundType.CANDLE),
            slab("hive_wax_dark", MapColor.COLOR_BROWN, SoundType.CANDLE),
            slab("hive_floor", MapColor.COLOR_BROWN, SoundType.PACKED_MUD),
            stairs("hive_floor", MapColor.COLOR_BROWN, SoundType.PACKED_MUD),
            slab("hive_comb_capped", MapColor.COLOR_YELLOW, SoundType.CORAL_BLOCK),
            new Def("hive_pollen_drift", Kind.DRIFT, "hive_pollen", MapColor.COLOR_YELLOW, SoundType.SAND),
            new Def("hive_wax_crumbs", Kind.BITS, "", MapColor.COLOR_YELLOW, SoundType.CANDLE),
            // Ember Mines
            slab("ember_basalt", MapColor.COLOR_BLACK, SoundType.BASALT),
            stairs("ember_basalt", MapColor.COLOR_BLACK, SoundType.BASALT),
            slab("ember_slag", MapColor.COLOR_GRAY, SoundType.NETHERRACK),
            stairs("ember_slag", MapColor.COLOR_GRAY, SoundType.NETHERRACK),
            new Def("ember_ash_drift", Kind.DRIFT, "ember_ash", MapColor.COLOR_LIGHT_GRAY, SoundType.SAND),
            new Def("ember_cinders", Kind.BITS, "", MapColor.COLOR_BLACK, SoundType.NETHERRACK),
            // Overgrown Temple
            slab("temple_stone", MapColor.STONE, SoundType.STONE),
            stairs("temple_stone", MapColor.STONE, SoundType.STONE),
            slab("temple_tiles", MapColor.STONE, SoundType.STONE),
            stairs("temple_tiles", MapColor.STONE, SoundType.STONE),
            new Def("temple_moss_drift", Kind.DRIFT, "temple_moss", MapColor.COLOR_GREEN, SoundType.MOSS),
            new Def("temple_leaves", Kind.BITS, "", MapColor.COLOR_GREEN, SoundType.GRASS),
            // Drowned Archive
            slab("archive_stone", MapColor.COLOR_CYAN, SoundType.STONE),
            stairs("archive_stone", MapColor.COLOR_CYAN, SoundType.STONE),
            slab("archive_tiles", MapColor.COLOR_CYAN, SoundType.STONE),
            stairs("archive_tiles", MapColor.COLOR_CYAN, SoundType.STONE),
            new Def("archive_paper_drift", Kind.DRIFT, "archive_paper", MapColor.SNOW, SoundType.WOOL),
            new Def("archive_scraps", Kind.BITS, "", MapColor.SNOW, SoundType.WOOL),
            // Clockwork Foundry
            slab("foundry_iron", MapColor.METAL, SoundType.METAL),
            stairs("foundry_iron", MapColor.METAL, SoundType.METAL),
            slab("foundry_brick", MapColor.COLOR_RED, SoundType.STONE),
            stairs("foundry_brick", MapColor.COLOR_RED, SoundType.STONE),
            new Def("foundry_shavings", Kind.BITS, "", MapColor.GOLD, SoundType.METAL),
            // Glass Desert
            slab("desert_sandstone", MapColor.SAND, SoundType.STONE),
            stairs("desert_sandstone", MapColor.SAND, SoundType.STONE),
            slab("desert_tile", MapColor.SAND, SoundType.STONE),
            stairs("desert_tile", MapColor.SAND, SoundType.STONE),
            new Def("desert_sand_drift", Kind.DRIFT, "desert_sand", MapColor.SAND, SoundType.SAND),
            new Def("desert_shards", Kind.BITS, "", MapColor.COLOR_LIGHT_BLUE, SoundType.GLASS),
            // Lantern Night Market
            slab("market_cobbles", MapColor.STONE, SoundType.STONE),
            stairs("market_cobbles", MapColor.STONE, SoundType.STONE),
            slab("market_boards", MapColor.WOOD, SoundType.WOOD),
            stairs("market_boards", MapColor.WOOD, SoundType.WOOD),
            new Def("market_confetti", Kind.BITS, "", MapColor.COLOR_RED, SoundType.WOOL),
            // Frost Monastery
            slab("frost_stone", MapColor.STONE, SoundType.STONE),
            stairs("frost_stone", MapColor.STONE, SoundType.STONE),
            slab("frost_tiles", MapColor.STONE, SoundType.STONE),
            stairs("frost_tiles", MapColor.STONE, SoundType.STONE),
            new Def("frost_snow_drift", Kind.DRIFT, "frost_snow", MapColor.SNOW, SoundType.SNOW),
            new Def("frost_chips", Kind.BITS, "", MapColor.ICE, SoundType.GLASS),
            // Mirror Palace
            slab("palace_marble", MapColor.QUARTZ, SoundType.STONE),
            stairs("palace_marble", MapColor.QUARTZ, SoundType.STONE),
            slab("palace_checker", MapColor.QUARTZ, SoundType.STONE),
            stairs("palace_checker", MapColor.QUARTZ, SoundType.STONE),
            new Def("palace_shards", Kind.BITS, "", MapColor.QUARTZ, SoundType.GLASS),
            // Precursor Lab
            slab("lab_panel", MapColor.COLOR_LIGHT_GRAY, SoundType.METAL),
            stairs("lab_panel", MapColor.COLOR_LIGHT_GRAY, SoundType.METAL),
            slab("lab_floor", MapColor.COLOR_GRAY, SoundType.METAL),
            stairs("lab_floor", MapColor.COLOR_GRAY, SoundType.METAL),
            new Def("lab_moss_drift", Kind.DRIFT, "lab_moss", MapColor.COLOR_GREEN, SoundType.MOSS),
            new Def("lab_screws", Kind.BITS, "", MapColor.METAL, SoundType.METAL),
            // Nightmare
            slab("dream_wood", MapColor.COLOR_PURPLE, SoundType.WOOD),
            stairs("dream_wood", MapColor.COLOR_PURPLE, SoundType.WOOD),
            slab("dream_boards", MapColor.COLOR_BROWN, SoundType.WOOD),
            stairs("dream_boards", MapColor.COLOR_BROWN, SoundType.WOOD),
            new Def("dream_plush_drift", Kind.DRIFT, "dream_plush", MapColor.COLOR_PINK, SoundType.WOOL),
            new Def("dream_feathers", Kind.BITS, "", MapColor.SNOW, SoundType.WOOL),
            // Sky Archipelago
            slab("sky_rock", MapColor.STONE, SoundType.STONE),
            stairs("sky_rock", MapColor.STONE, SoundType.STONE),
            slab("sky_marble", MapColor.QUARTZ, SoundType.STONE),
            stairs("sky_marble", MapColor.QUARTZ, SoundType.STONE),
            new Def("sky_turf_drift", Kind.DRIFT, "sky_turf", MapColor.GRASS, SoundType.GRASS),
            new Def("sky_pebbles", Kind.BITS, "", MapColor.STONE, SoundType.STONE),
            // Abyssal Shrine
            slab("shrine_stone", MapColor.COLOR_BLACK, SoundType.DEEPSLATE),
            stairs("shrine_stone", MapColor.COLOR_BLACK, SoundType.DEEPSLATE),
            slab("abyss_tiles", MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES),
            stairs("abyss_tiles", MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES),
            new Def("abyss_wax_stubs", Kind.BITS, "", MapColor.COLOR_LIGHT_GRAY, SoundType.CANDLE),
            // Fungal Hollow
            slab("fungal_rock", MapColor.COLOR_PURPLE, SoundType.STONE),
            stairs("fungal_rock", MapColor.COLOR_PURPLE, SoundType.STONE),
            slab("fungal_loam", MapColor.DIRT, SoundType.ROOTED_DIRT),
            stairs("fungal_loam", MapColor.DIRT, SoundType.ROOTED_DIRT),
            new Def("fungal_moss_drift", Kind.DRIFT, "fungal_moss", MapColor.COLOR_CYAN, SoundType.MOSS),
            new Def("fungal_spores", Kind.BITS, "", MapColor.COLOR_MAGENTA, SoundType.FUNGUS),
            // Bell Crypt (its own engines, stage 3)
            slab("crypt_limestone", MapColor.STONE, SoundType.STONE),
            stairs("crypt_limestone", MapColor.STONE, SoundType.STONE),
            slab("crypt_flagstone", MapColor.COLOR_GRAY, SoundType.STONE),
            stairs("crypt_flagstone", MapColor.COLOR_GRAY, SoundType.STONE),
            new Def("crypt_dust", Kind.DRIFT, "crypt_limestone_worn", MapColor.STONE, SoundType.SAND),
            new Def("crypt_bones", Kind.BITS, "", MapColor.TERRACOTTA_WHITE, SoundType.BONE_BLOCK)
    );

    /** Registers every depth block after the blocks they're cut from ({@code base} finds those by id); by id, table order. */
    public static Map<String, DeferredBlock<? extends Block>> register(DeferredRegister.Blocks blocks, Function<String, Supplier<? extends Block>> base) {
        Map<String, DeferredBlock<? extends Block>> out = new LinkedHashMap<>();
        for (Def d : DEFS) {
            Supplier<? extends Block> from = d.base().isEmpty() ? null : base.apply(d.base());
            Function<BlockBehaviour.Properties, ? extends Block> f = switch (d.kind()) {
                case SLAB -> SlabBlock::new;
                case STAIRS -> p -> new StairBlock(from.get().defaultBlockState(), p);
                case DRIFT -> Drift::new;
                case BITS -> p -> new ThemeBlocks.Shaped(p, Block.box(0, 0, 0, 16, 1, 16), false);
            };
            out.put(d.name(), blocks.registerBlock(d.name(), f, p -> props(d, from, p)));
        }
        return out;
    }

    private static BlockBehaviour.Properties props(Def d, Supplier<? extends Block> from, BlockBehaviour.Properties p) {
        p = p.mapColor(d.color()).sound(d.sound());
        // cut from a soft block (wax, pollen) it's soft; from stone it wants its tool, as the stone does
        boolean soft = from == null || from.get().defaultDestroyTime() < 1F;
        p = soft ? p.strength(0.6F) : p.strength(1.5F, 6.0F).requiresCorrectToolForDrops();
        return switch (d.kind()) {
            case BITS -> p.noOcclusion().noCollision().instabreak().isValidSpawn((s, l, pos, t) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false);
            case DRIFT -> p.noOcclusion().isValidSpawn((s, l, pos, t) -> false);
            default -> p;
        };
    }

    /** A drift: one to eight layers deep, standing a layer under its top like snow, so you wade through the first. */
    public static class Drift extends Block {
        public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
        private static final VoxelShape[] SHAPES = Block.boxes(8, h -> Block.column(16.0, 0.0, h * 2));

        public Drift(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LAYERS);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return SHAPES[s.getValue(LAYERS)];
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return SHAPES[s.getValue(LAYERS) - 1];
        }

        @Override
        protected VoxelShape getBlockSupportShape(BlockState s, BlockGetter l, BlockPos p) {
            return SHAPES[s.getValue(LAYERS)];
        }

        @Override
        protected VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return SHAPES[s.getValue(LAYERS)];
        }

        @Override
        protected boolean useShapeForLightOcclusion(BlockState s) {
            return true;
        }

        @Override
        protected boolean isPathfindable(BlockState s, PathComputationType t) {
            return t == PathComputationType.LAND && s.getValue(LAYERS) < 5;
        }

        @Override
        protected VoxelShape getOcclusionShape(BlockState s) {
            return s.getValue(LAYERS) == 8 ? Shapes.block() : Shapes.empty();
        }
    }
}
