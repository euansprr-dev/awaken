package dev.awaken.caves.skin;

import dev.awaken.caves.CaveRegion;
import dev.awaken.terrain.block.BlockSet;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The skin's blocks (SPEC §42.4): per rock a rubble heap, loose stones and a formation; for every cave the band's lichens,
 * mineral crust, wet streaks, the puddle and the dusty web; and the regions' own (§42.7): their rock, its ore twins, growth
 * and glow. They exist in every world (so a world opened without the caves
 * pack still reads its blocks); only the pack's feature lays them. Blockstates, models, items, loot and lang come from
 * tools/content/caves_content.py.
 */
public final class CaveSkin {
    private CaveSkin() {}

    private static final BlockSet SET = new BlockSet();

    public record Kit(DeferredBlock<SkinBlocks.Rubble> rubble, DeferredBlock<SkinBlocks.Pebbles> pebbles,
                      DeferredBlock<SkinBlocks.Formation> formation) {}

    public static final Map<SkinRock, Kit> KITS = new EnumMap<>(SkinRock.class);

    static {
        for (SkinRock r : SkinRock.values()) {
            KITS.put(r, new Kit(
                    SET.withItem(SET.blocks.registerBlock(r.id + "_rubble", SkinBlocks.Rubble::new,
                            p -> p.mapColor(r.color).sound(r.sound).strength(0.8F).noOcclusion().pushReaction(PushReaction.POPPED))),
                    SET.withItem(SET.blocks.registerBlock(r.id + "_pebbles", SkinBlocks.Pebbles::new,
                            p -> p.mapColor(r.color).sound(r.sound).noCollision().instabreak().replaceable()
                                    .pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ))),
                    SET.withItem(SET.blocks.registerBlock("pointed_" + r.id, SkinBlocks.Formation::new,
                            p -> p.mapColor(r.color).sound(r.sound).strength(r.strength * 0.5F).noOcclusion()
                                    .pushReaction(PushReaction.POPPED).dynamicShape()))));
        }
    }

    private static BlockBehaviour.Properties overlay(BlockBehaviour.Properties p, MapColor color, SoundType sound) {
        return p.mapColor(color).noCollision().strength(0.2F).sound(sound).replaceable().pushReaction(PushReaction.POPPED);
    }

    public static final DeferredBlock<MultifaceBlock> LICHEN_SHALLOWS = SET.withItem(SET.blocks.registerBlock("cave_lichen_shallows",
            MultifaceBlock::new, p -> overlay(p, MapColor.COLOR_LIGHT_GREEN, SoundType.GLOW_LICHEN)));
    public static final DeferredBlock<MultifaceBlock> LICHEN_MIDDLE = SET.withItem(SET.blocks.registerBlock("cave_lichen_middle",
            MultifaceBlock::new, p -> overlay(p, MapColor.GOLD, SoundType.GLOW_LICHEN)));
    public static final DeferredBlock<MultifaceBlock> LICHEN_DEEP = SET.withItem(SET.blocks.registerBlock("cave_lichen_deep",
            MultifaceBlock::new, p -> overlay(p, MapColor.COLOR_BLUE, SoundType.GLOW_LICHEN)));
    public static final DeferredBlock<MultifaceBlock> MINERAL_CRUST = SET.withItem(SET.blocks.registerBlock("mineral_crust",
            MultifaceBlock::new, p -> overlay(p, MapColor.QUARTZ, SoundType.CALCITE)));
    public static final DeferredBlock<MultifaceBlock> WET_STREAKS = SET.withItem(SET.blocks.registerBlock("wet_streaks",
            MultifaceBlock::new, p -> overlay(p, MapColor.WATER, SoundType.MUD)));
    public static final DeferredBlock<SkinBlocks.Puddle> PUDDLE = SET.withItem(SET.blocks.registerBlock("cave_puddle",
            SkinBlocks.Puddle::new, p -> p.mapColor(MapColor.WATER).noCollision().instabreak().replaceable().sound(SoundType.MUD)
                    .pushReaction(PushReaction.POPPED).noOcclusion()));
    public static final DeferredBlock<WebBlock> DUSTY_WEB = SET.withItem(SET.blocks.registerBlock("dusty_web", WebBlock::new,
            p -> p.mapColor(MapColor.WOOL).sound(SoundType.COBWEB).forceSolidOn().noCollision().requiresCorrectToolForDrops()
                    .strength(4.0F).pushReaction(PushReaction.POPPED)));

    // ---- the regions' own (SPEC §42.7): P2 the Rootways' and the Ochre Deep's --------------------------------------------

    private static BlockBehaviour.Properties rock(BlockBehaviour.Properties p, MapColor color, SoundType sound) {
        return p.mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(sound);
    }

    private static BlockBehaviour.Properties ore(BlockBehaviour.Properties p, MapColor color, SoundType sound) {
        return p.mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F).sound(sound);
    }

    /** The Rootways' rock: brown and earthy, threaded with old roots; mined like stone. */
    public static final DeferredBlock<Block> ROOTSTONE = SET.withItem(SET.blocks.registerBlock("rootstone", Block::new,
            p -> rock(p, MapColor.DIRT, SoundType.PACKED_MUD)));
    /** The Ochre Deep's rock: warm red-orange in bands; mined like stone. */
    public static final DeferredBlock<Block> OCHRE_STONE = SET.withItem(SET.blocks.registerBlock("ochre_stone", Block::new,
            p -> rock(p, MapColor.TERRACOTTA_ORANGE, SoundType.STONE)));
    // the ores that live in them, with their rock behind (drops, tags and tools are the stone ores')
    public static final DeferredBlock<DropExperienceBlock> ROOTSTONE_COAL_ORE = SET.withItem(SET.blocks.registerBlock("rootstone_coal_ore",
            p -> new DropExperienceBlock(UniformInt.of(0, 2), p), p -> ore(p, MapColor.DIRT, SoundType.PACKED_MUD)));
    public static final DeferredBlock<DropExperienceBlock> ROOTSTONE_COPPER_ORE = SET.withItem(SET.blocks.registerBlock("rootstone_copper_ore",
            p -> new DropExperienceBlock(ConstantInt.ZERO, p), p -> ore(p, MapColor.DIRT, SoundType.PACKED_MUD)));
    public static final DeferredBlock<DropExperienceBlock> OCHRE_IRON_ORE = SET.withItem(SET.blocks.registerBlock("ochre_iron_ore",
            p -> new DropExperienceBlock(ConstantInt.ZERO, p), p -> ore(p, MapColor.TERRACOTTA_ORANGE, SoundType.STONE)));

    /** The Rootways' growth: teal glowcaps on the floor (a faint light), root curtains hanging through the roof. */
    public static final DeferredBlock<SkinBlocks.Glowcap> GLOWCAP = SET.withItem(SET.blocks.registerBlock("glowcap", SkinBlocks.Glowcap::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).noCollision().instabreak().sound(SoundType.FUNGUS).lightLevel(st -> 5)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<SkinBlocks.RootCurtain> ROOT_CURTAIN = SET.withItem(SET.blocks.registerBlock("root_curtain",
            SkinBlocks.RootCurtain::new, p -> p.mapColor(MapColor.DIRT).noCollision().instabreak().replaceable().sound(SoundType.HANGING_ROOTS)
                    .noOcclusion().pushReaction(PushReaction.POPPED)));
    /** The Ochre Deep's walls: rust crust where it's dry, rust flowstone where it runs wet, ember lichen glowing faintly. */
    public static final DeferredBlock<MultifaceBlock> RUST_CRUST = SET.withItem(SET.blocks.registerBlock("rust_crust",
            MultifaceBlock::new, p -> overlay(p, MapColor.COLOR_ORANGE, SoundType.CALCITE)));
    public static final DeferredBlock<MultifaceBlock> RUST_FLOWSTONE = SET.withItem(SET.blocks.registerBlock("rust_flowstone",
            MultifaceBlock::new, p -> overlay(p, MapColor.TERRACOTTA_ORANGE, SoundType.CALCITE)));
    public static final DeferredBlock<MultifaceBlock> EMBER_LICHEN = SET.withItem(SET.blocks.registerBlock("ember_lichen",
            MultifaceBlock::new, p -> overlay(p, MapColor.COLOR_ORANGE, SoundType.GLOW_LICHEN).lightLevel(st -> 3)));

    // ---- P3 (SPEC §42.8): the other six regions' rock and twins -----------------------------------------------------

    /** The Pale Halls' rock: pale limestone in bedding planes. */
    public static final DeferredBlock<Block> LIMESTONE = SET.withItem(SET.blocks.registerBlock("limestone", Block::new,
            p -> rock(p, MapColor.TERRACOTTA_WHITE, SoundType.CALCITE)));
    /** The Windcleft's rock: dark slate in bands, flecked with mica. */
    public static final DeferredBlock<Block> BANDED_SLATE = SET.withItem(SET.blocks.registerBlock("banded_slate", Block::new,
            p -> rock(p, MapColor.COLOR_GRAY, SoundType.STONE)));
    /** The Drowned Galleries' rock: green-grey siltstone in fine damp layers. */
    public static final DeferredBlock<Block> SILTSTONE = SET.withItem(SET.blocks.registerBlock("siltstone", Block::new,
            p -> rock(p, MapColor.TERRACOTTA_GREEN, SoundType.MUD_BRICKS)));
    /** The Glimmer Hollows' host rock: sandy, with amber flecks. */
    public static final DeferredBlock<Block> GLIMMERSTONE = SET.withItem(SET.blocks.registerBlock("glimmerstone", Block::new,
            p -> rock(p, MapColor.SAND, SoundType.CALCITE)));
    /** The Black Vaults' rock: black basalt in columnar joints; mined like deepslate. */
    public static final DeferredBlock<Block> COLUMN_BASALT = SET.withItem(SET.blocks.registerBlock("column_basalt", Block::new,
            p -> rock(p, MapColor.COLOR_BLACK, SoundType.BASALT).strength(3.0F, 6.0F)));
    /** The Violet Deep's rock: deepslate stained violet, threaded with Deepglass; mined like deepslate. */
    public static final DeferredBlock<Block> SEEPSTONE = SET.withItem(SET.blocks.registerBlock("seepstone", Block::new,
            p -> rock(p, MapColor.COLOR_PURPLE, SoundType.DEEPSLATE).strength(3.0F, 6.0F)));
    public static final DeferredBlock<DropExperienceBlock> LIMESTONE_IRON_ORE = SET.withItem(SET.blocks.registerBlock("limestone_iron_ore",
            p -> new DropExperienceBlock(ConstantInt.ZERO, p), p -> ore(p, MapColor.TERRACOTTA_WHITE, SoundType.CALCITE)));
    public static final DeferredBlock<DropExperienceBlock> BANDED_SLATE_GOLD_ORE = SET.withItem(SET.blocks.registerBlock("banded_slate_gold_ore",
            p -> new DropExperienceBlock(ConstantInt.ZERO, p), p -> ore(p, MapColor.COLOR_GRAY, SoundType.STONE)));
    public static final DeferredBlock<DropExperienceBlock> SILTSTONE_LAPIS_ORE = SET.withItem(SET.blocks.registerBlock("siltstone_lapis_ore",
            p -> new DropExperienceBlock(UniformInt.of(2, 5), p), p -> ore(p, MapColor.TERRACOTTA_GREEN, SoundType.MUD_BRICKS)));
    public static final DeferredBlock<DropExperienceBlock> SILTSTONE_COPPER_ORE = SET.withItem(SET.blocks.registerBlock("siltstone_copper_ore",
            p -> new DropExperienceBlock(ConstantInt.ZERO, p), p -> ore(p, MapColor.TERRACOTTA_GREEN, SoundType.MUD_BRICKS)));
    public static final DeferredBlock<RedStoneOreBlock> GLIMMERSTONE_REDSTONE_ORE = SET.withItem(SET.blocks.registerBlock("glimmerstone_redstone_ore",
            RedStoneOreBlock::new, p -> ore(p, MapColor.SAND, SoundType.CALCITE).randomTicks()
                    .lightLevel(st -> st.getValue(RedStoneOreBlock.LIT) ? 9 : 0)));
    public static final DeferredBlock<DropExperienceBlock> COLUMN_BASALT_DIAMOND_ORE = SET.withItem(SET.blocks.registerBlock("column_basalt_diamond_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p), p -> ore(p, MapColor.COLOR_BLACK, SoundType.BASALT).strength(4.5F, 3.0F)));

    // ---- P3 (SPEC §42.8): the six regions' dressing ---------------------------------------------------------------------

    /** The Pale Halls: cave pearls in the pools, rimstone dams stepping down the floor. */
    public static final DeferredBlock<SkinBlocks.Pebbles> CAVE_PEARLS = SET.withItem(SET.blocks.registerBlock("cave_pearls", SkinBlocks.Pebbles::new,
            p -> p.mapColor(MapColor.QUARTZ).sound(SoundType.CALCITE).noCollision().instabreak().replaceable().pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<SkinBlocks.Drift> RIMSTONE = SET.withItem(SET.blocks.registerBlock("rimstone", p -> new SkinBlocks.Drift(p, 3),
            p -> p.mapColor(MapColor.TERRACOTTA_WHITE).sound(SoundType.CALCITE).strength(0.8F).noOcclusion().pushReaction(PushReaction.POPPED)));
    /** The Windcleft: rime on the high ledges where the draught runs cold, old Delvers' ropes down the cracks (climbable). */
    public static final DeferredBlock<MultifaceBlock> RIME = SET.withItem(SET.blocks.registerBlock("rime",
            MultifaceBlock::new, p -> overlay(p, MapColor.ICE, SoundType.POWDER_SNOW)));
    public static final DeferredBlock<SkinBlocks.RootCurtain> OLD_ROPE = SET.withItem(SET.blocks.registerBlock("old_rope",
            SkinBlocks.RootCurtain::new, p -> p.mapColor(MapColor.WOOL).noCollision().strength(0.3F).sound(SoundType.WOOL)
                    .noOcclusion().pushReaction(PushReaction.POPPED)));
    /** The Drowned Galleries: silt banks, bleached driftwood, pale weed under the water, glowing algae at its edge. */
    public static final DeferredBlock<SkinBlocks.Drift> SILT = SET.withItem(SET.blocks.registerBlock("silt", p -> new SkinBlocks.Drift(p, 4),
            p -> p.mapColor(MapColor.CLAY).sound(SoundType.MUD).strength(0.5F).pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<SkinBlocks.Driftwood> DRIFTWOOD = SET.withItem(SET.blocks.registerBlock("driftwood", SkinBlocks.Driftwood::new,
            p -> p.mapColor(MapColor.SAND).sound(SoundType.WOOD).strength(1.0F).noOcclusion().pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<SkinBlocks.CaveWeed> CAVE_WEED = SET.withItem(SET.blocks.registerBlock("cave_weed", SkinBlocks.CaveWeed::new,
            p -> p.mapColor(MapColor.WATER).noCollision().instabreak().sound(SoundType.WET_GRASS).replaceable().pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<MultifaceBlock> GLOW_ALGAE = SET.withItem(SET.blocks.registerBlock("glow_algae",
            MultifaceBlock::new, p -> overlay(p, MapColor.GLOW_LICHEN, SoundType.WET_GRASS).lightLevel(st -> 4)));
    /** The Glimmer Hollows' crystal: amber under the hot and dry lands, frost under the frozen ones. */
    public static final DeferredBlock<AmethystClusterBlock> AMBER_CRYSTAL = SET.withItem(SET.blocks.registerBlock("amber_crystal",
            p -> new AmethystClusterBlock(7F, 10F, p), p -> crystal(p, MapColor.COLOR_ORANGE)));
    public static final DeferredBlock<AmethystClusterBlock> GLACIAL_CRYSTAL = SET.withItem(SET.blocks.registerBlock("glacial_crystal",
            p -> new AmethystClusterBlock(7F, 10F, p), p -> crystal(p, MapColor.ICE)));
    /** The Black Vaults: ash drifted on the floors. */
    public static final DeferredBlock<SkinBlocks.Drift> ASH = SET.withItem(SET.blocks.registerBlock("ash", p -> new SkinBlocks.Drift(p, 2),
            p -> p.mapColor(MapColor.COLOR_GRAY).sound(SoundType.SAND).strength(0.3F).pushReaction(PushReaction.POPPED)));
    /** The Violet Deep: violet growths glowing faintly, and the husks the Hollows shed. */
    public static final DeferredBlock<SkinBlocks.Glowcap> VIOLET_GROWTH = SET.withItem(SET.blocks.registerBlock("violet_growth", SkinBlocks.Glowcap::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).noCollision().instabreak().sound(SoundType.NETHER_SPROUTS).lightLevel(st -> 4)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<SkinBlocks.Pebbles> SHED_CHITIN = SET.withItem(SET.blocks.registerBlock("shed_chitin", SkinBlocks.Pebbles::new,
            p -> p.mapColor(MapColor.TERRACOTTA_PURPLE).sound(SoundType.BONE_BLOCK).noCollision().instabreak().replaceable()
                    .pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ)));

    private static BlockBehaviour.Properties crystal(BlockBehaviour.Properties p, MapColor color) {
        return p.mapColor(color).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER).strength(1.5F).lightLevel(st -> 4)
                .pushReaction(PushReaction.POPPED);
    }

    /**
     * A region's rock, and its twins: the ores that live there, drawn with its rock behind them (the stone ore and its
     * deepslate twin both, so the band's floor at y 0 and the Deep agree). {@code deep}: it takes deepslate as well.
     */
    public record RegionKit(Supplier<? extends Block> rock, Map<Block, Supplier<? extends Block>> twins, boolean deep) {}

    private static final RegionKit ROOTWAYS_KIT = new RegionKit(ROOTSTONE, Map.of(Blocks.COAL_ORE, ROOTSTONE_COAL_ORE, Blocks.COPPER_ORE, ROOTSTONE_COPPER_ORE), false);
    private static final RegionKit OCHRE_KIT = new RegionKit(OCHRE_STONE, Map.of(Blocks.IRON_ORE, OCHRE_IRON_ORE), false);
    private static final RegionKit PALE_KIT = new RegionKit(LIMESTONE, Map.of(Blocks.IRON_ORE, LIMESTONE_IRON_ORE), false);
    private static final RegionKit WINDCLEFT_KIT = new RegionKit(BANDED_SLATE, Map.of(Blocks.GOLD_ORE, BANDED_SLATE_GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, BANDED_SLATE_GOLD_ORE), false);
    private static final RegionKit DROWNED_KIT = new RegionKit(SILTSTONE, Map.of(Blocks.LAPIS_ORE, SILTSTONE_LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE, SILTSTONE_LAPIS_ORE, Blocks.COPPER_ORE, SILTSTONE_COPPER_ORE), false);
    private static final RegionKit GLIMMER_KIT = new RegionKit(GLIMMERSTONE, Map.of(Blocks.REDSTONE_ORE, GLIMMERSTONE_REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE, GLIMMERSTONE_REDSTONE_ORE), false);
    private static final RegionKit VAULTS_KIT = new RegionKit(COLUMN_BASALT, Map.of(Blocks.DEEPSLATE_DIAMOND_ORE, COLUMN_BASALT_DIAMOND_ORE,
            Blocks.DIAMOND_ORE, COLUMN_BASALT_DIAMOND_ORE), true);
    private static final RegionKit VIOLET_KIT = new RegionKit(SEEPSTONE, Map.of(), true);

    /** A region's kit, or null for a region whose rock hasn't come yet. */
    public static @Nullable RegionKit kit(CaveRegion region) {
        return switch (region) {
            case ROOTWAYS -> ROOTWAYS_KIT;
            case OCHRE_DEEP -> OCHRE_KIT;
            case PALE_HALLS -> PALE_KIT;
            case WINDCLEFT -> WINDCLEFT_KIT;
            case DROWNED_GALLERIES -> DROWNED_KIT;
            case GLIMMER_HOLLOWS -> GLIMMER_KIT;
            case BLACK_VAULTS -> VAULTS_KIT;
            case VIOLET_DEEP -> VIOLET_KIT;
        };
    }

    public static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
