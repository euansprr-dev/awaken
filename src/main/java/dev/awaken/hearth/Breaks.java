package dev.awaken.hearth;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.hearth.echo.Duty;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gate breaks near home. Guards go to defend; when a break we saw goes from BROKEN to CLEARED we count
 * {@code awaken:breaks_held_at_hearth}, unless the gates module already counted it (its counter moved since we saw the
 * break). One flag per gate id in the Hearth's saved data, so nothing is ever counted twice.
 */
public final class Breaks {
    private Breaks() {}

    public static final int RADIUS = 256;
    private static final Set<UUID> ACTIVE = new HashSet<>();

    /** Server stop: the next world starts with no breaks remembered (singleplayer reuses the JVM). */
    static void clear() {
        ACTIVE.clear();
    }

    /** Whether a break is happening near this Hearth right now (from the last check). */
    public static boolean active(HearthRecord h) {
        return ACTIVE.contains(h.owner);
    }

    /**
     * Once every five seconds for a Hearth with someone of its household online ({@code home}: the owner and members
     * online). Everyone in the household hears of a break and of the Hearth holding; the counter is the owner's (a
     * member who fights the Lord is counted by the gates module, which counts everyone near it).
     */
    static void check(List<ServerPlayer> home, ServerLevel level, HearthRecord h) {
        boolean changed = false;
        BlockPos defend = null;
        ServerPlayer owner = null;
        for (ServerPlayer p : home) if (p.getUUID().equals(h.owner)) owner = p;
        ServerPlayer logTo = owner != null ? owner : home.get(0);
        List<GateHooks.GateView> near = GateHooks.near(level, h.pos, RADIUS);
        for (GateHooks.GateView g : near) {
            if (!"BROKEN".equals(g.state())) continue;
            if (defend == null) defend = g.pos();
            if (!h.breaks.containsKey(g.id())) {
                long counter = owner == null ? -1 : Progression.profile(owner).counter(Counters.BREAKS_HELD_AT_HEARTH);
                h.breaks.put(g.id(), new HearthRecord.BreakState(g.id(), counter, false, g.pos()));
                changed = true;
                DirectorLog.log(logTo, "hearth", "break near home: " + g.id());
                for (ServerPlayer p : home) {
                    Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("A gate broke near your Hearth"))
                            .row(Component.literal("Gate"), Component.literal(rankLabel(g.rank()) + "-rank · " + g.themeName()))
                            .row(Component.literal("Distance"), Component.literal((int) Math.sqrt(g.pos().distSqr(h.pos)) + " blocks"))
                            .body(Component.literal(Duty.guardCount(h) > 0 ? "The Guards are on their way." : "Hold the line. Guard pads would help."))
                            .priority(6).build());
                }
            }
        }
        for (Iterator<Map.Entry<String, HearthRecord.BreakState>> it = h.breaks.entrySet().iterator(); it.hasNext(); ) {
            HearthRecord.BreakState b = it.next().getValue();
            Optional<GateHooks.GateView> g = GateHooks.get(level, b.gateId());
            if (g.isEmpty() || "RELEASED".equals(g.get().state())) {
                it.remove();
                changed = true;
                continue;
            }
            if (!b.counted() && "CLEARED".equals(g.get().state())) {
                long now = owner == null ? -2 : Progression.profile(owner).counter(Counters.BREAKS_HELD_AT_HEARTH);
                h.breaks.put(b.gateId(), new HearthRecord.BreakState(b.gateId(), b.counterAtBreak(), true, b.gatePos()));
                changed = true;
                boolean count = owner != null && now == b.counterAtBreak();
                if (count) {
                    Progression.count(owner, Counters.BREAKS_HELD_AT_HEARTH);
                    for (ServerPlayer p : home) {
                        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Hearth held"))
                                .body(Component.literal("The break is closed and home is still standing.")).priority(5).build());
                    }
                }
                DirectorLog.log(logTo, "hearth", "break cleared " + b.gateId() + (count ? " (counted)" : " (already counted)"));
            }
        }
        if (defend != null) ACTIVE.add(h.owner);
        else ACTIVE.remove(h.owner);
        Duty.defend(level, h, defend);
        if (changed) HearthService.dirty(level.getServer());
    }

    /** Pure rule used by {@link #check} and the tests: count only a BROKEN→CLEARED transition nobody else counted. */
    public static boolean shouldCount(boolean alreadyCounted, String state, long counterAtBreak, long counterNow) {
        return !alreadyCounted && "CLEARED".equals(state) && counterNow == counterAtBreak;
    }

    static String rankLabel(int ordinal) {
        return dev.awaken.progression.Rank.of(ordinal).label;
    }
}
