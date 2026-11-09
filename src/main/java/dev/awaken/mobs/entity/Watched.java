package dev.awaken.mobs.entity;

import dev.awaken.core.api.Spotting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A creature someone is looking at doesn't go (SPEC §39.11). Vanilla rolls 1 in 800 a tick for a mob over 32 blocks from
 * every player, so one you study from afar was gone within a minute more often than not. Asked only when that roll has
 * come up, so a full look costs nothing.
 */
final class Watched {
    private Watched() {}

    /** Roughly in front of them, whatever they're looking through: 60 degrees either side of the look. */
    private static final double IN_VIEW = 0.5;

    /** Whether a player has this creature in view, in clear sight, and near enough that their client knows of it. */
    static boolean now(AwakenMob m) {
        if (!(m.level() instanceof ServerLevel level)) return false;
        Vec3 middle = m.getBoundingBox().getCenter();
        double reach = m.getType().clientTrackingRange() * 16.0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            Vec3 eye = p.getEyePosition(), to = middle.subtract(eye);
            double len = to.length();
            if (len > reach) continue;
            if (len > 0.1 && to.dot(p.getViewVector(1.0F)) / len < IN_VIEW) continue;
            if (Spotting.clearLine(level, eye, middle) || Spotting.clearLine(level, eye, m.getEyePosition())) return true;
        }
        return false;
    }
}
