package dev.awaken.living.pulse;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/** What a kind of happening does (SPEC §21.6): where it can start, what it brings, how it moves, how it leaves. */
public interface Behaviour {
    /** Its line: where it starts and where it heads (the same spot for one that stays put). */
    record Plan(BlockPos from, BlockPos to) {}

    /** A place near the player, in loaded land that suits it, or null (the Pulse tries another kind). */
    @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r);

    /** It starts: its creatures arrive, what it leaves is placed. */
    default void start(ServerLevel level, Happening h, HappeningDef def) {}

    /** Once a second while it runs: it moves ({@link Happening#centre}), its creatures act. */
    default void tick(ServerLevel level, Happening h, HappeningDef def, long now) {}

    /** It ends: its creatures leave with it. */
    default void end(ServerLevel level, Happening h, HappeningDef def) {
        Pulse.dismiss(level, h);
    }

    /** A player witnessed it (after the Almanac's discovery): anything the kind gives for seeing it. */
    default void witnessed(ServerPlayer p, Happening h, HappeningDef def) {}
}
