package dev.awaken.lucidium.world;

import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.lucidium.trial.TrialCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turns a {@link DreamPlan} into a {@link DreamBlueprint}: every dream is a trial's course now (SPEC §9.3), laid by
 * {@link TrialCompiler}; the old islands of memory went with the dreams' remake (§9). An old plan with no trial compiles
 * as the first dream. Deterministic and free of world access, so it runs on a background thread.
 */
public final class BlueprintCompiler {
    private BlueprintCompiler() {}

    public static DreamBlueprint compile(DreamPlan plan, int cell, Function<String, Optional<Fragment>> memories) {
        return TrialCompiler.compile(plan, cell, memories);
    }

    /** A door frame: posts either side, a lintel, and an open door in the middle (opening at {@code o}). */
    public static void frame(Map<Long, BlockState> out, BlockPos o, Direction facing, BlockState frame, Block door) {
        Direction side = facing.getClockWise();
        for (int y = 0; y <= 3; y++) {
            out.put(o.relative(side).above(y).asLong(), frame);
            out.put(o.relative(side.getOpposite()).above(y).asLong(), frame);
        }
        out.put(o.above(3).asLong(), frame);
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        out.put(o.asLong(), lower);
        out.put(o.above().asLong(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
