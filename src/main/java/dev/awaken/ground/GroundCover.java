package dev.awaken.ground;

import dev.awaken.ground.cover.PebblesBlock;
import dev.awaken.ground.cover.TwigsBlock;
import dev.awaken.terrain.block.BlockSet;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * What lies on the ground (SPEC §31, P2): pebbles (one to three small stones, four stone looks) and twigs (fallen sticks, three
 * barks). Blocks exist in every world; the worldgen that scatters them is in the {@code awaken:awakened_ground} pack. Their
 * models use the game's own stone and bark textures; blockstates, models, loot and lang come from tools/content/ground_content.py.
 */
public final class GroundCover {
    private GroundCover() {}

    private static final BlockSet SET = new BlockSet();

    private static BlockBehaviour.Properties small(BlockBehaviour.Properties p, MapColor color, SoundType sound) {
        return p.mapColor(color).noCollision().instabreak().replaceable().sound(sound).pushReaction(PushReaction.POPPED)
                .offsetType(BlockBehaviour.OffsetType.XZ);
    }

    public static final DeferredBlock<PebblesBlock> PEBBLES = SET.withItem(SET.blocks.registerBlock("pebbles", PebblesBlock::new,
            p -> small(p, MapColor.STONE, SoundType.STONE)));
    public static final DeferredBlock<TwigsBlock> TWIGS = SET.withItem(SET.blocks.registerBlock("twigs", TwigsBlock::new,
            p -> small(p, MapColor.WOOD, SoundType.WOOD)));

    static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
