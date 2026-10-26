package dev.awaken.above.client;

import dev.awaken.above.block.DoorCoreBlock;
import dev.awaken.above.block.PrecursorDoorBlock;
import dev.awaken.progression.Rank;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Set;

/**
 * A sealed door's light (SPEC §17.25): the seams of its panels and the rune on its core are drawn full bright in the
 * colour of the rank the door waits for, so a hunter reads it from across the forecourt before touching it. A panel
 * that doesn't know its rank yet (an older door, until its core paints it) keeps the old brass amber.
 */
final class DoorTint implements BlockTintSource {
    static final DoorTint INSTANCE = new DoorTint();
    /** The amber every door wore before ranks had colours. */
    static final int UNKNOWN = 0xFFE0A040;

    private DoorTint() {}

    @Override
    public int color(BlockState state) {
        if (state.hasProperty(DoorCoreBlock.RANK)) return 0xFF000000 | Rank.of(state.getValue(DoorCoreBlock.RANK)).colour;
        if (state.hasProperty(PrecursorDoorBlock.RANK)) {
            int r = state.getValue(PrecursorDoorBlock.RANK);
            return r == PrecursorDoorBlock.UNKNOWN ? UNKNOWN : 0xFF000000 | Rank.of(r).colour;
        }
        return -1;
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return Set.of(DoorCoreBlock.RANK, PrecursorDoorBlock.RANK);
    }
}
