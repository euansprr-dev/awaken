package dev.awaken.caves.skin;

import dev.awaken.terrain.block.BlockSet;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.EnumMap;
import java.util.Map;

/**
 * The skin's blocks (SPEC §42.4): per rock a rubble heap, loose stones and a formation; for every cave the band's lichens,
 * mineral crust, wet streaks, the puddle and the dusty web. They exist in every world (so a world opened without the caves
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

    public static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
