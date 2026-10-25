package dev.awaken.living.pulse;

import dev.awaken.core.api.GateHooks;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/** The Deep Tide (SPEC §21.6), the new moon's night. */
final class Tides {
    private Tides() {}

    /** Levels the night's hunters and camps gain while the tide is up. */
    static final int EXTRA = 2;
    private static volatile long risingUntil = Long.MIN_VALUE;

    /** Whether the Deep Tide is up now (one at a time, for the whole overworld). */
    static boolean rising(ServerLevel level) {
        return level.getGameTime() < risingUntil;
    }

    /**
     * The Deep Tide: on the new moon's night grey fog creeps out of an old break site (or open ground), and the night's
     * hunters run two levels harder everywhere.
     */
    static final class DeepTide implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            if (rising(level)) return null;
            for (var g : GateHooks.near(level, p.blockPosition(), 300)) {
                if (g.state().equals("BROKEN") && Behaviours.allowed(level, g.pos())) return new Plan(g.pos(), g.pos());
            }
            BlockPos c = Behaviours.spot(level, p, def, 90, 200, r, at -> Behaviours.dry(level, at));
            return c == null ? null : new Plan(c, c);
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
            risingUntil = h.end;
            for (ServerPlayer pl : level.players()) {
                Signal.toast(pl, Component.literal("The Deep Tide · tonight the dark runs two levels harder"));
            }
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            risingUntil = h.end; // after a restart the record outlives the flag
        }

        @Override
        public void end(ServerLevel level, Happening h, HappeningDef def) {
            risingUntil = Long.MIN_VALUE;
            Pulse.dismiss(level, h);
        }
    }
}
