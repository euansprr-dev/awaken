package dev.awaken.core.audio;

import dev.awaken.Awaken;
import dev.awaken.gates.Gates;
import dev.awaken.gates.content.Theme;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The gates' layered music: in a run, the gate's theme ({@code awaken:gate/<theme>}). One gate, one sound: an annex
 * floor keeps its gate's music. The Abyssal Shrine stays silent ("no sound at all") and the Ritual of the Bell scores
 * itself, so neither has a context. Reports the floor's pressure, and an arrival whenever a run or a floor begins.
 */
final class GateMusic implements MusicLayers.Provider {
    static final GateMusic INSTANCE = new GateMusic();
    private static final Identifier[] CONTEXTS = new Identifier[Theme.values().length];

    static {
        for (Theme t : Theme.values()) CONTEXTS[t.ordinal()] = Awaken.id("gate/" + t.id);
    }

    /** Last (gate, area, floor) seen per player, packed: a change is an arrival. */
    private final Map<UUID, Long> where = new HashMap<>();

    private GateMusic() {}

    /** The context of a theme, or null for the themes that keep silence. */
    static @Nullable Identifier contextOf(Theme theme) {
        return theme == Theme.ABYSSAL_SHRINE ? null : CONTEXTS[theme.ordinal()];
    }

    @Override
    public @Nullable Identifier context(ServerPlayer p, Intensity.Signals s) {
        if (!Gates.isInGate(p)) {
            where.remove(p.getUUID());
            return null;
        }
        var run = Gates.activeRun(p);
        if (run.isEmpty()) return null;
        var r = run.get();
        Identifier ctx = contextOf(r.theme());
        if (ctx == null || Gates.isRitual(p.level().getServer(), r.gateId())) return null;
        long key = ((long) r.gateId() << 16) ^ ((long) r.area() << 8) ^ r.floor();
        Long last = where.put(p.getUUID(), key);
        if (last == null || last != key) s.arrival = true;
        s.pressure = Gates.pressure(p);
        return ctx;
    }

    void forget(UUID player) {
        where.remove(player);
    }

    void clear() {
        where.clear();
    }
}
