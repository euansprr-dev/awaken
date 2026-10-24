package dev.awaken.signal.client;

import dev.awaken.core.api.HorizonHooks;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** The client's copy of the Horizon line (SPEC §17) and when it last changed (a new line fades in). */
final class HorizonClient {
    private HorizonClient() {}

    private static SignalPayloads.Horizon current = new SignalPayloads.Horizon(Identifier.withDefaultNamespace("overworld"), Optional.empty());
    private static String id = "";
    private static int changedAt, ticks, version;

    static void accept(SignalPayloads.Horizon h) {
        version++;
        String next = h.line().map(l -> l.id() + (l.tease() ? "#tease" : "")).orElse("");
        if (!next.equals(id)) {
            id = next;
            changedAt = ticks;
        }
        current = h;
    }

    static Optional<HorizonHooks.Line> line() {
        return current.line();
    }

    /** " ↗ 420 m" toward the line's site, in the dimension it was picked in. */
    static Optional<String> compass(HorizonHooks.Line l) {
        return NowClient.compass(l.where(), current.dimension());
    }

    static int version() {
        return version;
    }

    static void tick() {
        ticks++;
    }

    static int sinceChange() {
        return ticks - changedAt;
    }

    static void clear() {
        version++;
        current = new SignalPayloads.Horizon(current.dimension(), Optional.empty());
        id = "";
    }
}
