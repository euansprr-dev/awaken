package dev.awaken.core.api;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The Boss Plate's data (SPEC §35.1), server side. A module that shows a {@link ServerBossEvent} for one of its creatures
 * binds the bar to the entity for each player who may see it; the client then draws the plate (name, level in heat colour,
 * phase marks, stance) in place of vanilla's bar. Lines (the intro, a phase line, a signature move's name) show under the
 * plate instead of in chat. The living module installs the sink; until then every call is a no-op.
 */
public final class BossPlates {
    private BossPlates() {}

    /** A Lord's plate (bosses, Sovereigns, nightmares, a Break's Lord) or a Warden's smaller one. */
    public enum Kind { LORD, WARDEN }

    /** What a line is: the boss's first words, a phase's line, or a signature move's name (short, in caps). */
    public enum Line { INTRO, PHASE, SIGNATURE }

    public interface Sink {
        /** Ties {@code bar} to {@code entity} for this player: its kind, phase marks (health fractions) and glow (RGB). */
        default void bind(ServerPlayer player, UUID bar, int entity, Kind kind, float[] marks, int glow) {}

        /** A line under the plate of {@code bar} (a client without the plate prints INTRO and PHASE lines to chat). */
        default void line(ServerPlayer player, UUID bar, String text, Line kind, int colour) {}
    }

    private static Sink sink = new Sink() {};

    public static void install(Sink s) {
        sink = s;
    }

    public static void bind(ServerPlayer player, UUID bar, int entity, Kind kind, float[] marks, int glow) {
        sink.bind(player, bar, entity, kind, marks, glow);
    }

    public static void line(ServerPlayer player, UUID bar, String text, Line kind, int colour) {
        sink.line(player, bar, text, kind, colour);
    }

    /** A line to everyone who sees {@code bar}. */
    public static void line(ServerBossEvent bar, String text, Line kind, int colour) {
        for (ServerPlayer p : bar.getPlayers()) sink.line(p, bar.getId(), text, kind, colour);
    }
}
