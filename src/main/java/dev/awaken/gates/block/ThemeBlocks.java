package dev.awaken.gates.block;

import dev.awaken.gates.worlds.WorldRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The world blocks of every theme after the Bell Crypt (SPEC §38.10 on): one table, a shape each. Cubes and columns of the
 * theme's stone, glowing ones, glass, small plants, things that hang, thin layers on the floor, falls of light you walk
 * through, the theme's touchstone (its rule block) and props with a shape of their own. Textures, models and the rest
 * from tools/art/specs/gate_worlds_&lt;theme&gt;.py, which keeps the same table.
 */
public final class ThemeBlocks {
    private ThemeBlocks() {}

    public enum Shape { CUBE, COLUMN, GLASS, PLANT, HANG, CARPET, FALL, TOUCH, PROP, FOLD, SKETCH, CLOUD }

    /** One block: its id, shape, map colour, sound, light and (props, touchstones) its outline in pixels. */
    public record Def(String name, Shape shape, MapColor color, SoundType sound, int light, double[] box, boolean soft) {
        static Def of(String name, Shape shape, MapColor color, SoundType sound, int light) {
            return new Def(name, shape, color, sound, light, null, false);
        }

        static Def soft(String name, Shape shape, MapColor color, SoundType sound, int light) {
            return new Def(name, shape, color, sound, light, null, true);
        }

        static Def box(String name, Shape shape, MapColor color, SoundType sound, int light, double... box) {
            return new Def(name, shape, color, sound, light, box, false);
        }
    }

    private static final Shape C = Shape.CUBE, COL = Shape.COLUMN, G = Shape.GLASS, PL = Shape.PLANT, H = Shape.HANG, CA = Shape.CARPET,
            F = Shape.FALL, T = Shape.TOUCH, PR = Shape.PROP, FO = Shape.FOLD, SK = Shape.SKETCH, CL = Shape.CLOUD;

    /** Every theme's blocks, in the order of the creative tab. */
    public static final List<Def> DEFS = List.of(
            // Fungal Hollow (38.10)
            Def.of("fungal_rock", C, MapColor.TERRACOTTA_PURPLE, SoundType.TUFF, 0),
            Def.of("fungal_rock_veined", C, MapColor.TERRACOTTA_PURPLE, SoundType.TUFF, 6),
            Def.soft("fungal_loam", C, MapColor.TERRACOTTA_BLACK, SoundType.ROOTED_DIRT, 0),
            Def.soft("fungal_moss", C, MapColor.COLOR_CYAN, SoundType.MOSS, 3),
            Def.soft("glowcap_stem", COL, MapColor.TERRACOTTA_WHITE, SoundType.STEM, 0),
            Def.soft("glowcap_cap", C, MapColor.COLOR_PURPLE, SoundType.WART_BLOCK, 0),
            Def.soft("glowcap_gills", C, MapColor.COLOR_LIGHT_BLUE, SoundType.SHROOMLIGHT, 12),
            Def.soft("lumen_roots", H, MapColor.COLOR_LIGHT_BLUE, SoundType.HANGING_ROOTS, 8),
            Def.soft("glowcap_sprouts", PL, MapColor.COLOR_CYAN, SoundType.FUNGUS, 6),
            Def.soft("mycelium_threads", CA, MapColor.COLOR_LIGHT_BLUE, SoundType.SCULK_VEIN, 7),
            Def.soft("fungal_glowfall", F, MapColor.COLOR_CYAN, SoundType.EMPTY, 10),
            Def.box("spore_puffball", T, MapColor.TERRACOTTA_WHITE, SoundType.FUNGUS, 5, 2, 0, 2, 14, 11, 14),
            // Ember Mines
            Def.of("ember_basalt", C, MapColor.COLOR_BLACK, SoundType.BASALT, 0),
            Def.of("ember_basalt_cracked", C, MapColor.COLOR_BLACK, SoundType.BASALT, 7),
            Def.of("ember_slag", C, MapColor.TERRACOTTA_GRAY, SoundType.NETHER_ORE, 0),
            Def.soft("ember_ash", C, MapColor.COLOR_GRAY, SoundType.SAND, 0),
            Def.soft("mine_timber", COL, MapColor.WOOD, SoundType.WOOD, 0),
            Def.soft("mine_planks", C, MapColor.WOOD, SoundType.WOOD, 0),
            Def.box("mine_lamp", PR, MapColor.COLOR_ORANGE, SoundType.LANTERN, 14, 5, 0, 5, 11, 11, 11),
            Def.of("magma_crust", C, MapColor.NETHER, SoundType.NETHER_ORE, 5),
            Def.soft("ember_crystal", PL, MapColor.COLOR_ORANGE, SoundType.AMETHYST_CLUSTER, 11),
            Def.soft("ember_moltenfall", F, MapColor.COLOR_ORANGE, SoundType.EMPTY, 15),
            Def.box("ember_bellows", T, MapColor.WOOD, SoundType.WOOD, 4, 1, 0, 3, 15, 10, 13),
            // Hive
            Def.soft("hive_wax", C, MapColor.COLOR_YELLOW, SoundType.CANDLE, 0),
            Def.soft("hive_wax_dark", C, MapColor.COLOR_BROWN, SoundType.CANDLE, 0),
            Def.soft("hive_comb", C, MapColor.COLOR_ORANGE, SoundType.CORAL_BLOCK, 7),
            Def.soft("hive_comb_capped", C, MapColor.COLOR_YELLOW, SoundType.CORAL_BLOCK, 0),
            Def.soft("hive_honey_glow", G, MapColor.COLOR_ORANGE, SoundType.HONEY_BLOCK, 12),
            Def.soft("hive_floor", C, MapColor.COLOR_BROWN, SoundType.PACKED_MUD, 0),
            Def.soft("hive_pollen", C, MapColor.COLOR_YELLOW, SoundType.SAND, 0),
            Def.soft("hive_drip", H, MapColor.COLOR_ORANGE, SoundType.HONEY_BLOCK, 4),
            Def.box("hive_drum", T, MapColor.COLOR_BROWN, SoundType.CANDLE, 3, 2, 0, 2, 14, 12, 14),
            Def.soft("hive_royal", C, MapColor.COLOR_PINK, SoundType.CORAL_BLOCK, 10),
            Def.soft("hive_petals", CA, MapColor.COLOR_PINK, SoundType.PINK_PETALS, 0),
            Def.soft("hive_candle", PL, MapColor.COLOR_YELLOW, SoundType.CANDLE, 12),
            // Overgrown Temple
            Def.of("temple_stone", C, MapColor.TERRACOTTA_GREEN, SoundType.STONE, 0),
            Def.of("temple_stone_mossy", C, MapColor.TERRACOTTA_GREEN, SoundType.STONE, 0),
            Def.of("temple_carving", C, MapColor.TERRACOTTA_GREEN, SoundType.STONE, 5),
            Def.of("temple_tiles", C, MapColor.TERRACOTTA_LIGHT_GREEN, SoundType.DEEPSLATE_TILES, 0),
            Def.soft("temple_root", COL, MapColor.TERRACOTTA_BROWN, SoundType.MANGROVE_ROOTS, 0),
            Def.soft("temple_vines", H, MapColor.PLANT, SoundType.CAVE_VINES, 6),
            Def.soft("temple_moss", CA, MapColor.PLANT, SoundType.MOSS_CARPET, 0),
            Def.box("temple_brazier", T, MapColor.TERRACOTTA_GREEN, SoundType.STONE, 12, 2, 0, 2, 14, 12, 14),
            Def.box("temple_idol", PR, MapColor.TERRACOTTA_GREEN, SoundType.STONE, 4, 3, 0, 3, 13, 15, 13),
            Def.soft("temple_fern", PL, MapColor.PLANT, SoundType.GRASS, 0),
            Def.soft("temple_lantern_bulb", H, MapColor.COLOR_YELLOW, SoundType.SPORE_BLOSSOM, 12),
            Def.soft("temple_canopy", C, MapColor.PLANT, SoundType.AZALEA_LEAVES, 0),
            Def.soft("temple_waterfall", F, MapColor.WATER, SoundType.EMPTY, 3),
            // Drowned Archive (38.11)
            Def.of("archive_stone", C, MapColor.TERRACOTTA_CYAN, SoundType.STONE, 0),
            Def.of("archive_stone_carved", C, MapColor.TERRACOTTA_CYAN, SoundType.STONE, 5),
            Def.soft("archive_shelf", COL, MapColor.WOOD, SoundType.CHISELED_BOOKSHELF, 0),
            Def.soft("archive_shelf_lit", COL, MapColor.WOOD, SoundType.CHISELED_BOOKSHELF, 7),
            Def.of("archive_tiles", C, MapColor.TERRACOTTA_WHITE, SoundType.DEEPSLATE_TILES, 0),
            Def.soft("archive_planks", C, MapColor.COLOR_BROWN, SoundType.WOOD, 0),
            Def.box("archive_lamp", PR, MapColor.COLOR_GREEN, SoundType.LANTERN, 14, 4, 0, 4, 12, 13, 12),
            Def.soft("archive_paper", CA, MapColor.TERRACOTTA_WHITE, SoundType.WOOL, 0),
            Def.soft("archive_kelp", H, MapColor.COLOR_GREEN, SoundType.WET_GRASS, 6),
            Def.soft("archive_sea_glass", G, MapColor.WATER, SoundType.GLASS, 4),
            Def.soft("archive_inkfall", F, MapColor.COLOR_BLACK, SoundType.EMPTY, 4),
            Def.box("archive_lectern", T, MapColor.WOOD, SoundType.WOOD, 10, 2, 0, 2, 14, 14, 14),
            Def.box("archive_paper_bridge", FO, MapColor.TERRACOTTA_WHITE, SoundType.WOOL, 3, 0, 13, 0, 16, 16, 16),
            // Clockwork Foundry
            Def.of("foundry_iron", C, MapColor.METAL, SoundType.METAL, 0),
            Def.of("foundry_brick", C, MapColor.TERRACOTTA_RED, SoundType.NETHER_BRICKS, 0),
            Def.of("foundry_brass", C, MapColor.GOLD, SoundType.METAL, 0),
            Def.of("foundry_gear", C, MapColor.GOLD, SoundType.METAL, 0),
            Def.of("foundry_grille", G, MapColor.METAL, SoundType.CHAIN, 0),
            Def.of("foundry_pipe", COL, MapColor.COLOR_ORANGE, SoundType.COPPER, 0),
            Def.of("foundry_furnace", C, MapColor.TERRACOTTA_RED, SoundType.NETHER_BRICKS, 13),
            Def.box("foundry_bulb", PR, MapColor.COLOR_YELLOW, SoundType.LANTERN, 14, 5, 0, 5, 11, 12, 11),
            Def.soft("foundry_steam", F, MapColor.SNOW, SoundType.EMPTY, 0),
            Def.soft("foundry_sparks", PL, MapColor.COLOR_ORANGE, SoundType.CHAIN, 10),
            Def.soft("foundry_cables", H, MapColor.COLOR_BLACK, SoundType.CHAIN, 3),
            Def.box("foundry_valve", T, MapColor.GOLD, SoundType.METAL, 6, 2, 0, 2, 14, 14, 14),
            // Glass Desert
            Def.soft("desert_sand", C, MapColor.SAND, SoundType.SAND, 0),
            Def.soft("desert_sand_rippled", C, MapColor.SAND, SoundType.SAND, 0),
            Def.of("desert_sandstone", C, MapColor.SAND, SoundType.STONE, 0),
            Def.of("desert_sandstone_carved", C, MapColor.SAND, SoundType.STONE, 6),
            Def.of("desert_brick", C, MapColor.TERRACOTTA_ORANGE, SoundType.MUD_BRICKS, 0),
            Def.of("desert_tile", C, MapColor.COLOR_LIGHT_BLUE, SoundType.DEEPSLATE_TILES, 0),
            Def.of("desert_column", COL, MapColor.SAND, SoundType.STONE, 0),
            Def.soft("desert_glass", G, MapColor.COLOR_ORANGE, SoundType.GLASS, 0),
            Def.soft("desert_glass_glowing", G, MapColor.COLOR_YELLOW, SoundType.GLASS, 12),
            Def.soft("desert_crystal", PL, MapColor.COLOR_YELLOW, SoundType.AMETHYST_CLUSTER, 9),
            Def.soft("desert_grass", PL, MapColor.SAND, SoundType.GRASS, 0),
            Def.soft("desert_sandfall", F, MapColor.SAND, SoundType.EMPTY, 0),
            Def.box("desert_mirror", T, MapColor.GOLD, SoundType.METAL, 8, 2, 0, 2, 14, 15, 14),
            // Lantern Night Market
            Def.soft("market_boards", C, MapColor.WOOD, SoundType.WOOD, 0),
            Def.of("market_cobbles", C, MapColor.STONE, SoundType.STONE, 0),
            Def.of("market_plaster", C, MapColor.TERRACOTTA_WHITE, SoundType.PACKED_MUD, 0),
            Def.soft("market_timber", COL, MapColor.COLOR_RED, SoundType.WOOD, 0),
            Def.of("market_roof", C, MapColor.TERRACOTTA_GRAY, SoundType.DEEPSLATE_TILES, 0),
            Def.soft("market_paper_wall", G, MapColor.TERRACOTTA_WHITE, SoundType.BAMBOO_WOOD, 10),
            Def.soft("market_lantern", H, MapColor.COLOR_RED, SoundType.LANTERN, 15),
            Def.soft("market_lights", H, MapColor.COLOR_YELLOW, SoundType.CHAIN, 11),
            Def.soft("market_cloth", C, MapColor.COLOR_RED, SoundType.WOOL, 0),
            Def.box("market_goods", PR, MapColor.COLOR_ORANGE, SoundType.WOOD, 4, 1, 0, 1, 15, 12, 15),
            Def.soft("market_boat_lamp", CA, MapColor.COLOR_YELLOW, SoundType.WOOL, 10),
            Def.box("market_wish_lantern", T, MapColor.COLOR_RED, SoundType.BAMBOO_WOOD, 12, 4, 0, 4, 12, 15, 12),
            // Frost Monastery (38.12)
            Def.of("frost_stone", C, MapColor.TERRACOTTA_WHITE, SoundType.STONE, 0),
            Def.of("frost_stone_carved", C, MapColor.TERRACOTTA_WHITE, SoundType.STONE, 6),
            Def.of("frost_tiles", C, MapColor.COLOR_BLUE, SoundType.DEEPSLATE_TILES, 0),
            Def.soft("frost_timber", COL, MapColor.COLOR_RED, SoundType.WOOD, 0),
            Def.of("frost_plaster", C, MapColor.SNOW, SoundType.PACKED_MUD, 0),
            Def.soft("frost_snow", C, MapColor.SNOW, SoundType.SNOW, 0),
            Def.soft("frost_ice", G, MapColor.ICE, SoundType.GLASS, 0),
            Def.soft("frost_ice_glowing", G, MapColor.ICE, SoundType.GLASS, 10),
            Def.soft("frost_icicles", H, MapColor.ICE, SoundType.GLASS, 4),
            Def.soft("frost_flags", H, MapColor.COLOR_RED, SoundType.WOOL, 0),
            Def.box("frost_butter_lamp", PR, MapColor.GOLD, SoundType.LANTERN, 13, 5, 0, 5, 11, 7, 11),
            Def.soft("frost_crystal", PL, MapColor.ICE, SoundType.AMETHYST_CLUSTER, 9),
            Def.soft("frost_cloud", CL, MapColor.SNOW, SoundType.WOOL, 0),
            Def.box("frost_prayer_wheel", T, MapColor.GOLD, SoundType.METAL, 6, 3, 0, 3, 13, 15, 13),
            // Mirror Palace
            Def.of("palace_marble", C, MapColor.QUARTZ, SoundType.CALCITE, 0),
            Def.of("palace_marble_veined", C, MapColor.QUARTZ, SoundType.CALCITE, 4),
            Def.of("palace_checker", C, MapColor.QUARTZ, SoundType.DEEPSLATE_TILES, 0),
            Def.of("palace_gilt", C, MapColor.GOLD, SoundType.METAL, 0),
            Def.soft("palace_mirror", G, MapColor.METAL, SoundType.GLASS, 0),
            Def.soft("palace_mirror_cracked", G, MapColor.METAL, SoundType.GLASS, 0),
            Def.soft("palace_glass_floor", G, MapColor.NONE, SoundType.GLASS, 0),
            Def.of("palace_white", C, MapColor.SNOW, SoundType.CALCITE, 11),
            Def.soft("palace_shadow", CA, MapColor.COLOR_LIGHT_GRAY, SoundType.WOOL, 0),
            Def.soft("palace_curtain", C, MapColor.COLOR_PURPLE, SoundType.WOOL, 0),
            Def.soft("palace_chandelier", H, MapColor.GOLD, SoundType.CHAIN, 14),
            Def.box("palace_candelabra", PR, MapColor.GOLD, SoundType.METAL, 13, 4, 0, 4, 12, 14, 12),
            Def.soft("palace_crystal", PL, MapColor.QUARTZ, SoundType.AMETHYST_CLUSTER, 8),
            Def.box("mirror_stand", T, MapColor.METAL, SoundType.GLASS, 6, 3, 0, 3, 13, 16, 13),
            // Precursor Lab
            Def.of("lab_panel", C, MapColor.TERRACOTTA_WHITE, SoundType.METAL, 0),
            Def.of("lab_panel_lit", C, MapColor.TERRACOTTA_WHITE, SoundType.METAL, 10),
            Def.of("lab_floor", C, MapColor.COLOR_GRAY, SoundType.METAL, 0),
            Def.of("lab_frame", COL, MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 0),
            Def.soft("lab_glass", G, MapColor.COLOR_CYAN, SoundType.GLASS, 0),
            Def.of("lab_rack", C, MapColor.COLOR_BLACK, SoundType.METAL, 6),
            Def.soft("lab_tank", G, MapColor.COLOR_GREEN, SoundType.GLASS, 12),
            Def.soft("lab_cables", H, MapColor.COLOR_BLACK, SoundType.CHAIN, 3),
            Def.soft("lab_light", H, MapColor.TERRACOTTA_WHITE, SoundType.LANTERN, 14),
            Def.soft("lab_hologram", PL, MapColor.COLOR_CYAN, SoundType.AMETHYST_CLUSTER, 9),
            Def.soft("lab_moss", CA, MapColor.PLANT, SoundType.MOSS_CARPET, 0),
            Def.of("lab_sketch", SK, MapColor.COLOR_CYAN, SoundType.METAL, 0),
            Def.box("lab_console", T, MapColor.COLOR_GRAY, SoundType.METAL, 8, 2, 0, 2, 14, 14, 14),
            // Nightmare
            Def.soft("dream_wallpaper", C, MapColor.COLOR_PURPLE, SoundType.WOOD, 0),
            Def.soft("dream_wallpaper_torn", C, MapColor.COLOR_PURPLE, SoundType.WOOD, 0),
            Def.soft("dream_boards", C, MapColor.WOOD, SoundType.WOOD, 0),
            Def.soft("dream_wood", COL, MapColor.COLOR_BROWN, SoundType.WOOD, 0),
            Def.soft("dream_plush", C, MapColor.COLOR_MAGENTA, SoundType.WOOL, 0),
            Def.soft("dream_sheet", C, MapColor.TERRACOTTA_WHITE, SoundType.WOOL, 0),
            Def.of("dream_dark", C, MapColor.COLOR_BLACK, SoundType.SCULK, 3),
            Def.box("dream_lamp", PR, MapColor.COLOR_YELLOW, SoundType.LANTERN, 14, 4, 0, 4, 12, 16, 12),
            Def.soft("dream_candle", PL, MapColor.COLOR_YELLOW, SoundType.CANDLE, 11),
            Def.soft("dream_motes", H, MapColor.COLOR_PURPLE, SoundType.EMPTY, 8),
            Def.soft("dream_toy", C, MapColor.COLOR_RED, SoundType.WOOD, 0),
            Def.soft("dream_window", G, MapColor.COLOR_LIGHT_BLUE, SoundType.GLASS, 9),
            Def.box("dream_music_box", T, MapColor.COLOR_PINK, SoundType.WOOD, 6, 3, 0, 4, 13, 10, 12)
    );

    /** The touchstones that stay lit once touched (a brazier fed); the rest fade back after a few seconds. */
    static final java.util.Set<String> STAYS_LIT = java.util.Set.of("temple_brazier");

    /** Registers every theme block; the map is by id in table order. */
    public static Map<String, DeferredBlock<? extends Block>> register(DeferredRegister.Blocks blocks) {
        Map<String, DeferredBlock<? extends Block>> out = new LinkedHashMap<>();
        for (Def d : DEFS) out.put(d.name(), blocks.registerBlock(d.name(), factory(d), p -> props(d, p)));
        return out;
    }

    private static Function<BlockBehaviour.Properties, ? extends Block> factory(Def d) {
        return switch (d.shape()) {
            case CUBE -> Block::new;
            case COLUMN -> RotatedPillarBlock::new;
            case GLASS -> TransparentBlock::new;
            case PLANT -> p -> new Shaped(p, Block.box(2, 0, 2, 14, 13, 14), false);
            case HANG -> p -> new Shaped(p, Block.box(2, 0, 2, 14, 16, 14), false);
            case CARPET -> p -> new Shaped(p, Block.box(0, 0, 0, 16, 1, 16), true);
            case FALL -> p -> new Shaped(p, Shapes.block(), false);
            case TOUCH -> p -> new Touchstone(p, boxOf(d), STAYS_LIT.contains(d.name()));
            case PROP -> p -> new Shaped(p, boxOf(d), true);
            case FOLD -> p -> new Folding(p, boxOf(d));
            case SKETCH -> p -> new Folding(p, Shapes.block());
            case CLOUD -> p -> new Shaped(p, Shapes.block(), false);
        };
    }

    private static VoxelShape boxOf(Def d) {
        double[] b = d.box();
        return Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    private static BlockBehaviour.Properties props(Def d, BlockBehaviour.Properties p) {
        int light = d.light();
        p = p.mapColor(d.color()).sound(d.sound());
        if (d.shape() == Shape.TOUCH) p = p.lightLevel(s -> s.getValue(Touchstone.LIT) ? light : light / 3);
        // a sketch glows faintly until it's drawn
        else if (d.shape() == Shape.SKETCH) p = p.lightLevel(s -> s.getValue(Folding.OPEN) ? 0 : light);
        else p = p.lightLevel(s -> light);
        p = d.soft() ? p.strength(0.6F) : p.strength(1.5F, 6.0F).requiresCorrectToolForDrops();
        switch (d.shape()) {
            case GLASS -> p = p.noOcclusion().isValidSpawn((s, l, pos, t) -> false).isRedstoneConductor((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false).strength(0.4F);
            case PLANT, HANG, FALL -> p = p.noOcclusion().noCollision().instabreak().isValidSpawn((s, l, pos, t) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false);
            // a cloud is a whole block you fall through (it hides its neighbours' faces, so a sea of it is cheap to draw)
            case CLOUD -> p = p.noCollision().isValidSpawn((s, l, pos, t) -> false).isSuffocating((s, l, pos) -> false);
            case CARPET, PROP, TOUCH, FOLD -> p = p.noOcclusion().isValidSpawn((s, l, pos, t) -> false);
            default -> {}
        }
        return p;
    }

    /** A block with its own outline (a plant, hanging roots, a thin layer, a prop); it collides only where it says. */
    public static class Shaped extends Block {
        private final VoxelShape shape;
        private final boolean collide;

        public Shaped(BlockBehaviour.Properties p, VoxelShape shape, boolean collide) {
            super(p);
            this.shape = shape;
            this.collide = collide;
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return shape;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return collide ? shape : Shapes.empty();
        }

        @Override
        protected boolean isPathfindable(BlockState s, PathComputationType t) {
            return !collide || shape.max(net.minecraft.core.Direction.Axis.Y) < 0.2;
        }
    }

    /**
     * A world's rule block (SPEC §38.10, "Touchstones"): a puffball, a bellows, a wax drum, a brazier. Hit it or use it and
     * the world's rule answers; it glows brighter for a few seconds (a brazier stays lit).
     */
    public static final class Touchstone extends Shaped {
        public static final BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
        private final boolean stays;

        public Touchstone(BlockBehaviour.Properties p, VoxelShape shape, boolean stays) {
            super(p, shape, true);
            this.stays = stays;
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LIT);
        }

        private boolean touched(BlockState state, Level level, BlockPos pos, Player player) {
            if (!(player instanceof ServerPlayer sp) || !WorldRules.touch(sp, pos)) return false;
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
            if (!stays) level.scheduleTick(pos, this, 100);
            return true;
        }

        @Override
        protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
            touched(state, level, pos, player);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            return touched(state, level, pos, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The Inkwell's paper bridge (SPEC §38.11): a sheet you can always stand on, shown folded flat and faint until someone
     * comes near, when it opens out into a bridge (the live layer opens it and it folds itself again a while later).
     */
    public static final class Folding extends Shaped {
        public static final BooleanProperty OPEN = net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;

        public Folding(BlockBehaviour.Properties p, VoxelShape shape) {
            super(p, shape, true);
            registerDefaultState(stateDefinition.any().setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(OPEN);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (!state.getValue(OPEN)) return;
            // fold again once nobody is near
            if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 6, false) != null) level.scheduleTick(pos, this, 60);
            else level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_CLIENTS);
        }

        /** Opens the sheet if it's folded, and folds it again some seconds after nobody is near. */
        public static void open(ServerLevel level, BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof Folding f) || state.getValue(OPEN)) return;
            level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, f, 80);
        }
    }

    // ---- the themes' set pieces (38.8 "Sets") ------------------------------------------------------------------------

    /** The Fungal Hollow's set piece: a jar of living spores that lights a room; shake it and they swirl up. */
    public static final class SporeJar extends Shaped {
        public SporeJar(BlockBehaviour.Properties p) {
            super(p, Block.box(3, 0, 3, 13, 14, 13), true);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.6, 0.8, 0.6, 0.02);
                sl.playSound(null, pos, SoundEvents.SPORE_BLOSSOM_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
            if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.3 + r.nextDouble() * 0.4, pos.getY() + 0.9,
                    pos.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    /** The Ember Mines' set piece, the Ever-Ember: a little forge that never goes out. Use raw food on it and it cooks. */
    public static final class EverEmber extends Shaped {
        public EverEmber(BlockBehaviour.Properties p) {
            super(p, Block.box(1, 0, 1, 15, 13, 15), true);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
            if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
            SingleRecipeInput in = new SingleRecipeInput(stack);
            var recipe = sl.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, in, sl);
            if (recipe.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
            ItemStack cooked = recipe.get().value().assemble(in);
            stack.shrink(1);
            if (!player.getInventory().add(cooked)) player.drop(cooked, false, net.minecraft.util.Prediction.SERVER_ONLY);
            sl.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
            sl.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
            return InteractionResult.SUCCESS_SERVER;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
            if (r.nextInt(2) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + r.nextDouble() * 0.4, pos.getY() + 0.7,
                    pos.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.01, 0);
            if (r.nextInt(6) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0.03, 0);
        }
    }

    /** The Hive's set piece, the Queen's Comb: it fills with honey once a day; take it with a glass bottle. */
    public static final class QueensComb extends Shaped {
        public static final BooleanProperty FULL = BooleanProperty.create("full");

        public QueensComb(BlockBehaviour.Properties p) {
            super(p, Block.box(1, 0, 1, 15, 16, 15), true);
            registerDefaultState(stateDefinition.any().setValue(FULL, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FULL);
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return !state.getValue(FULL);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
            // about once a Minecraft day at the default random tick speed
            if (r.nextInt(6) == 0) level.setBlock(pos, state.setValue(FULL, true), Block.UPDATE_ALL);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
            if (!stack.is(Items.GLASS_BOTTLE) || !state.getValue(FULL)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            stack.shrink(1);
            ItemStack honey = new ItemStack(Items.HONEY_BOTTLE);
            if (stack.isEmpty()) player.setItemInHand(hand, honey);
            else if (!player.getInventory().add(honey)) player.drop(honey, false, net.minecraft.util.Prediction.SERVER_ONLY);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1F, 1F);
            level.setBlock(pos, state.setValue(FULL, false), Block.UPDATE_ALL);
            return InteractionResult.SUCCESS_SERVER;
        }
    }

    /** The Overgrown Temple's set piece, the Little Idol: the crops round it (four blocks) grow as if tended. */
    public static final class LittleIdol extends Shaped {
        public LittleIdol(BlockBehaviour.Properties p) {
            super(p, Block.box(3, 0, 3, 13, 15, 13), true);
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return true;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
            BlockPos at = pos.offset(r.nextInt(9) - 4, r.nextInt(3) - 1, r.nextInt(9) - 4);
            if (!level.isLoaded(at)) return;
            BlockState s = level.getBlockState(at);
            if (s.getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(level, at, s, BonemealSource.INTERACTION)
                    && !(s.getBlock() instanceof net.minecraft.world.level.block.GrassBlock)) {
                b.performBonemeal(level, r, at, s, BonemealSource.INTERACTION);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    /** The Drowned Archive's set piece, the Diving Lamp: use it and you can breathe underwater for a minute. */
    public static final class DivingLamp extends Shaped {
        public DivingLamp(BlockBehaviour.Properties p) {
            super(p, Block.box(3, 0, 3, 13, 15, 13), true);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel sl) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 20 * 60, 0));
                sl.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.02);
                sl.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 0.8F, 1.1F);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
            if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.3 + r.nextDouble() * 0.4, pos.getY() + 0.9,
                    pos.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    /** The Clockwork Foundry's set piece, the Pocket Engine: use it to start it, and while it runs it gives a redstone signal. */
    public static final class PocketEngine extends Shaped {
        public static final BooleanProperty POWERED = net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;

        public PocketEngine(BlockBehaviour.Properties p) {
            super(p, Block.box(2, 0, 2, 14, 12, 14), true);
            registerDefaultState(stateDefinition.any().setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(POWERED);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            BlockState next = state.cycle(POWERED);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
            level.playSound(null, pos, next.getValue(POWERED) ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, 1.6F);
            return InteractionResult.SUCCESS_SERVER;
        }

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction dir) {
            return state.getValue(POWERED) ? 15 : 0;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
            if (state.getValue(POWERED) && r.nextInt(3) == 0)
                level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5, 0, 0.03, 0);
        }
    }

    /** The Glass Desert's set piece, the Hourglass: turn it and it tells you how long it is until the sun sets or rises. */
    public static final class Hourglass extends Shaped {
        public Hourglass(BlockBehaviour.Properties p) {
            super(p, Block.box(4, 0, 4, 12, 15, 12), true);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
            long t = Math.floorMod(sl.getOverworldClockTime(), 24000L);
            boolean day = t < 12000;
            long left = (day ? 12000 - t : 24000 - t) / 20;
            String when = left < 60 ? "less than a minute" : left < 120 ? "about a minute" : "about " + (left / 60) + " minutes";
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The sand says " + when + " until " + (day ? "sunset." : "sunrise.")));
            sl.playSound(null, pos, SoundEvents.SAND_FALL, SoundSource.BLOCKS, 0.8F, 1.3F);
            return InteractionResult.SUCCESS_SERVER;
        }
    }

    /** The Lantern Night Market's set piece, the Lucky Cat: once a day it has a small gift for you (an emerald). */
    public static final class LuckyCat extends Shaped {
        public static final BooleanProperty FULL = BooleanProperty.create("full");

        public LuckyCat(BlockBehaviour.Properties p) {
            super(p, Block.box(4, 0, 4, 12, 13, 12), true);
            registerDefaultState(stateDefinition.any().setValue(FULL, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FULL);
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return !state.getValue(FULL);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
            if (r.nextInt(6) == 0) level.setBlock(pos, state.setValue(FULL, true), Block.UPDATE_ALL);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!state.getValue(FULL)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            ItemStack gift = new ItemStack(Items.EMERALD);
            if (!player.getInventory().add(gift)) player.drop(gift, false, net.minecraft.util.Prediction.SERVER_ONLY);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1F, 1.4F);
            level.setBlock(pos, state.setValue(FULL, false), Block.UPDATE_ALL);
            return InteractionResult.SUCCESS_SERVER;
        }
    }

    /**
     * The Frost Monastery's set piece, the Singing Bowl: strike it and it hums, and anyone near it shakes off the cold
     * and whatever slows them (slowness, mining fatigue, weakness). Then it rests a while before it'll sing again.
     */
    public static final class SingingBowl extends Shaped {
        public static final BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

        public SingingBowl(BlockBehaviour.Properties p) {
            super(p, Block.box(3, 0, 3, 13, 6, 13), true);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LIT);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (state.getValue(LIT)) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The bowl is still humming. Give it a moment."));
                return InteractionResult.SUCCESS_SERVER;
            }
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, 20 * 60);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4F, 0.6F);
            for (Player q : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pos).inflate(8))) {
                q.setTicksFrozen(0);
                q.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
                q.removeEffect(net.minecraft.world.effect.MobEffects.MINING_FATIGUE);
                q.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        }
    }

    /** The Mirror Palace's set piece, the Hand Mirror: look into it and it shows you the way to where you last slept. */
    public static final class HandMirror extends Shaped {
        public HandMirror(BlockBehaviour.Properties p) {
            super(p, Block.box(4, 0, 6, 12, 15, 10), true);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            ServerPlayer.RespawnConfig rc = sp.getRespawnConfig();
            net.minecraft.core.GlobalPos home = rc == null ? null : rc.respawnData().globalPos();
            if (home == null || !home.dimension().equals(sl.dimension())) {
                sp.sendOverlayMessage(net.minecraft.network.chat.Component.literal(home == null
                        ? "The mirror shows you yourself, and nothing behind you. You haven't slept anywhere yet."
                        : "The mirror shows a bed a whole world away from here."));
            } else {
                net.minecraft.world.phys.Vec3 a = sp.getEyePosition(), b = net.minecraft.world.phys.Vec3.atCenterOf(home.pos());
                net.minecraft.world.phys.Vec3 dir = b.subtract(a).normalize();
                for (int i = 1; i <= 12; i++) {
                    net.minecraft.world.phys.Vec3 v = a.add(dir.scale(i * 1.2)).add(0, -0.4, 0);
                    sl.sendParticles(sp, ParticleTypes.END_ROD, true, false, v.x, v.y, v.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
                sp.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Your bed is about " + Math.round(a.distanceTo(b)) + " blocks that way."));
            }
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.6F);
            return InteractionResult.SUCCESS_SERVER;
        }
    }

    /** The Precursor Lab's set piece, the Humming Cube: it gives a redstone signal while anyone stands within four blocks. */
    public static final class HummingCube extends Shaped {
        public static final BooleanProperty POWERED = net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;

        public HummingCube(BlockBehaviour.Properties p) {
            super(p, Block.box(4, 0, 4, 12, 8, 12), true);
            registerDefaultState(stateDefinition.any().setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(POWERED);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (!level.isClientSide()) level.scheduleTick(pos, this, 10);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            boolean near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, false) != null;
            if (near != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, near), Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, this);
                level.playSound(null, pos, near ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.4F, 1.8F);
            }
            level.scheduleTick(pos, this, 10);
        }

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction dir) {
            return state.getValue(POWERED) ? 15 : 0;
        }
    }

    /** The Nightmare's set piece, the Night Light: switch it on and every monster within 24 blocks shows through walls a while. */
    public static final class NightLight extends Shaped {
        public NightLight(BlockBehaviour.Properties p) {
            super(p, Block.box(5, 0, 5, 11, 9, 11), true);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
            int n = 0;
            for (net.minecraft.world.entity.monster.Monster m : sl.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(24), e -> e.isAlive())) {
                m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 200, 0));
                n++;
            }
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(n == 0 ? "Nothing under the bed. Nothing anywhere near."
                    : n == 1 ? "There's one out there. Now you can see it." : "There are " + n + " of them out there. Now you can see them."));
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.8F);
            return InteractionResult.SUCCESS_SERVER;
        }
    }

    /** The set pieces' ids and makers, registered with their items in the creative tab's set row. */
    public static Map<String, DeferredBlock<? extends Block>> registerSets(DeferredRegister.Blocks blocks) {
        Map<String, DeferredBlock<? extends Block>> out = new LinkedHashMap<>();
        out.put("spore_jar", blocks.registerBlock("spore_jar", SporeJar::new,
                p -> p.mapColor(MapColor.COLOR_CYAN).sound(SoundType.GLASS).strength(0.6F).noOcclusion().lightLevel(s -> 15)));
        out.put("ember_forge", blocks.registerBlock("ember_forge", EverEmber::new,
                p -> p.mapColor(MapColor.COLOR_BLACK).sound(SoundType.BASALT).strength(1.5F, 6.0F).noOcclusion().lightLevel(s -> 13)));
        out.put("queens_comb", blocks.registerBlock("queens_comb", QueensComb::new,
                p -> p.mapColor(MapColor.COLOR_YELLOW).sound(SoundType.CORAL_BLOCK).strength(0.6F).noOcclusion().randomTicks()
                        .lightLevel(s -> s.getValue(QueensComb.FULL) ? 10 : 4)));
        out.put("little_idol", blocks.registerBlock("little_idol", LittleIdol::new,
                p -> p.mapColor(MapColor.TERRACOTTA_GREEN).sound(SoundType.STONE).strength(1.5F, 6.0F).noOcclusion().randomTicks().lightLevel(s -> 4)));
        out.put("diving_lamp", blocks.registerBlock("diving_lamp", DivingLamp::new,
                p -> p.mapColor(MapColor.COLOR_GREEN).sound(SoundType.LANTERN).strength(0.6F).noOcclusion().lightLevel(s -> 13)));
        out.put("pocket_engine", blocks.registerBlock("pocket_engine", PocketEngine::new,
                p -> p.mapColor(MapColor.GOLD).sound(SoundType.METAL).strength(1.5F, 6.0F).noOcclusion()
                        .lightLevel(s -> s.getValue(PocketEngine.POWERED) ? 9 : 2)));
        out.put("sun_hourglass", blocks.registerBlock("sun_hourglass", Hourglass::new,
                p -> p.mapColor(MapColor.SAND).sound(SoundType.GLASS).strength(0.6F).noOcclusion().lightLevel(s -> 5)));
        out.put("lucky_cat", blocks.registerBlock("lucky_cat", LuckyCat::new,
                p -> p.mapColor(MapColor.GOLD).sound(SoundType.DECORATED_POT).strength(0.6F).noOcclusion().randomTicks()
                        .lightLevel(s -> s.getValue(LuckyCat.FULL) ? 8 : 3)));
        out.put("singing_bowl", blocks.registerBlock("singing_bowl", SingingBowl::new,
                p -> p.mapColor(MapColor.GOLD).sound(SoundType.METAL).strength(1.5F, 6.0F).noOcclusion()
                        .lightLevel(s -> s.getValue(SingingBowl.LIT) ? 9 : 2)));
        out.put("hand_mirror", blocks.registerBlock("hand_mirror", HandMirror::new,
                p -> p.mapColor(MapColor.METAL).sound(SoundType.GLASS).strength(0.6F).noOcclusion().lightLevel(s -> 5)));
        out.put("humming_cube", blocks.registerBlock("humming_cube", HummingCube::new,
                p -> p.mapColor(MapColor.COLOR_CYAN).sound(SoundType.METAL).strength(1.5F, 6.0F).noOcclusion()
                        .lightLevel(s -> s.getValue(HummingCube.POWERED) ? 12 : 5)));
        out.put("night_light", blocks.registerBlock("night_light", NightLight::new,
                p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).sound(SoundType.GLASS).strength(0.6F).noOcclusion().lightLevel(s -> 11)));
        return out;
    }
}
