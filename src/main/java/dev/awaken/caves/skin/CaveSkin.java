package dev.awaken.caves.skin;

import dev.awaken.caves.CaveRegion;
import dev.awaken.terrain.block.BlockSet;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.MultifaceBlock;
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

    /** A region's rock, and its twins: the ores that live there, drawn with its rock behind them. */
    public record RegionKit(Supplier<? extends Block> rock, Map<Block, Supplier<? extends Block>> twins) {}

    private static final RegionKit ROOTWAYS_KIT = new RegionKit(ROOTSTONE, Map.of(Blocks.COAL_ORE, ROOTSTONE_COAL_ORE, Blocks.COPPER_ORE, ROOTSTONE_COPPER_ORE));
    private static final RegionKit OCHRE_KIT = new RegionKit(OCHRE_STONE, Map.of(Blocks.IRON_ORE, OCHRE_IRON_ORE));

    /** A region's kit, or null for a region whose rock hasn't come yet. */
    public static @Nullable RegionKit kit(CaveRegion region) {
        return switch (region) {
            case ROOTWAYS -> ROOTWAYS_KIT;
            case OCHRE_DEEP -> OCHRE_KIT;
            default -> null;
        };
    }

    public static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
