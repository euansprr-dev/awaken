package dev.awaken.body.ascent;

import dev.awaken.Awaken;
import dev.awaken.living.api.Quests;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Ascent's moments, told to the Living Wilds' quests (SPEC §17.20; `living.api.Quests`, a no-op unless a step waits
 * on the id): a zip that reached its anchor, a glide's length, a Tether Spire climbed, a hero tree's crown reached, a
 * windway ridden end to end, the Leaper's Gauntlet and the Sky Leviathan's trial done.
 */
final class AscentQuests {
    private AscentQuests() {}

    static final Identifier ZIP = Awaken.id("use/ascent_zip"), GLIDE = Awaken.id("use/ascent_glide"), SPIRE = Awaken.id("discover/spire"), HERO_CROWN = Awaken.id("discover/hero_crown"),
            WINDWAY = Awaken.id("use/windway"), GAUNTLET = Awaken.id("use/gauntlet"), SKY_TRIAL = Awaken.id("use/sky_trial");

    /** A zip counts when it ends at its anchor (a hang under it or the ledge vault), not let go of on the way. */
    static final double ZIP_ARRIVED = 3.5;
    /** A windway counts when one glide holds it from its first stretch to its last. */
    static final double WAY_START = 60, WAY_END = 30;
    /** A glide counts at its end with its length (blocks across the ground, from where the wings opened), from this long. */
    static final double GLIDE_FROM = 8;

    private static final Map<UUID, Windways.Way> RIDING = new HashMap<>();
    private static final Map<UUID, double[]> GLIDING = new HashMap<>();

    static void advance(ServerPlayer p, Identifier what) {
        Quests.advance(p, what);
    }

    /** A state change: a zip that ended at its anchor; a glide's start and, at its end, its length. */
    static void state(ServerPlayer p, AscentState was, AscentState now, double ax, double ay, double az) {
        if (was == AscentState.ZIP && (now == AscentState.TETHER || now == AscentState.CARRY)
                && p.distanceToSqr(ax, ay, az) < ZIP_ARRIVED * ZIP_ARRIVED) advance(p, ZIP);
        if (now == AscentState.GLIDE) {
            GLIDING.put(p.getUUID(), new double[]{p.getX(), p.getZ()});
        } else if (was == AscentState.GLIDE) {
            double[] from = GLIDING.remove(p.getUUID());
            double length = from == null ? 0 : Math.hypot(p.getX() - from[0], p.getZ() - from[1]);
            if (length >= GLIDE_FROM) Quests.advance(p, GLIDE, (int) length);
        }
    }

    /** Each tick on the wing: a windway taken near its start and held to its end. */
    static void ride(ServerPlayer p, AscentState s) {
        UUID id = p.getUUID();
        Windways.Way held = RIDING.get(id);
        if (s != AscentState.GLIDE && s != AscentState.SOAR) {
            if (held != null) RIDING.remove(id);
            return;
        }
        Windways.Way in = Windways.inside(p.level(), p.getX(), p.getY(), p.getZ());
        if (held == null) {
            if (in != null && in.along(p.getX(), p.getY(), p.getZ()) < WAY_START) RIDING.put(id, in);
            return;
        }
        if (in != held) {
            RIDING.remove(id);
        } else if (held.length() - held.along(p.getX(), p.getY(), p.getZ()) < WAY_END) {
            RIDING.remove(id);
            advance(p, WINDWAY);
        }
    }

    static void forget(UUID id) {
        RIDING.remove(id);
        GLIDING.remove(id);
    }
}
