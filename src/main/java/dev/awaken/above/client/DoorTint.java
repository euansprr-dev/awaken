package dev.awaken.above.client;

import dev.awaken.above.block.DoorCoreBlock;
import dev.awaken.above.block.PrecursorDoorBlock;
import dev.awaken.progression.Rank;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Set;

/**
 * A sealed door's light (SPEC §17.27): the seams of its panels and the rune on its core are drawn full bright in the
 * colour of the rank the door waits for, so a hunter reads it from across the forecourt before touching it. A panel
 * that doesn't know its rank yet (an older door, until its core paints it) keeps the old brass amber. One source per
 * block: each names only its own block's rank property (the two blocks' "rank" are different properties).
 */
final class DoorTint {
    private DoorTint() {}

    /** The amber every door wore before ranks had colours. */
    static final int UNKNOWN = 0xFFE0A040;

    static final BlockTintSource CORE = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return state.hasProperty(DoorCoreBlock.RANK) ? 0xFF000000 | Rank.of(state.getValue(DoorCoreBlock.RANK)).colour : UNKNOWN;
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return Set.of(DoorCoreBlock.RANK);
        }
    };

    static final BlockTintSource PANEL = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            if (!state.hasProperty(PrecursorDoorBlock.RANK)) return UNKNOWN;
            int r = state.getValue(PrecursorDoorBlock.RANK);
            return r == PrecursorDoorBlock.UNKNOWN ? UNKNOWN : 0xFF000000 | Rank.of(r).colour;
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return Set.of(PrecursorDoorBlock.RANK);
        }
    };
}
