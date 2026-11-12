package dev.awaken.gates.client;

import dev.awaken.gates.net.GatePayloads;

import java.util.List;

/** What the client knows about gates: the beacons it was told about, the run HUD, the last spoils view. */
public final class ClientGates {
    private ClientGates() {}

    static volatile List<GatePayloads.Beacon> beacons = List.of();
    static volatile GatePayloads.RunHud hud = GatePayloads.RunHud.NONE;
    static volatile long hudAt;
    /** The Satchel and what is held for you, as the open inventory screen shows them (SPEC §13, "Gate spoils"). */
    static volatile GatePayloads.SpoilsView spoils = GatePayloads.SpoilsView.EMPTY;
    static volatile long spoilsNanos;

    public static List<GatePayloads.Beacon> beacons() {
        return beacons;
    }

    // ---- an open land's lights (SPEC §50.9 "Lights and seals") ----------------------------------------------------------

    /** The gate whose lights these are, what is lit now, and what went out (each with the client's time it did). */
    static volatile int lightsGate = -1;
    static volatile List<GatePayloads.RealmLight> realmLights = List.of();
    static volatile List<Fading> fading = List.of();

    /** A light that went out since the last list: it fades out over {@link #FADE} ms rather than vanish. */
    record Fading(GatePayloads.RealmLight light, long at) {}

    static final long FADE = 2500;

    static void realmLights(GatePayloads.RealmLights msg) {
        List<GatePayloads.RealmLight> now = List.copyOf(msg.lights());
        long t = System.currentTimeMillis();
        List<Fading> out = new java.util.ArrayList<>();
        for (Fading f : fading) if (t - f.at() < FADE && !now.contains(f.light())) out.add(f);
        if (msg.gateId() == lightsGate) {
            for (GatePayloads.RealmLight was : realmLights) if (!now.contains(was)) out.add(new Fading(was, t));
        } else out.clear(); // another gate's land: nothing of the last one carries over
        lightsGate = msg.gateId();
        realmLights = now;
        fading = List.copyOf(out);
    }

    public static GatePayloads.RunHud hud() {
        return hud;
    }

    public static boolean inRun() {
        return hud.gateId() >= 0;
    }

    static void clear() {
        beacons = List.of();
        lightsGate = -1;
        realmLights = List.of();
        fading = List.of();
        hud = GatePayloads.RunHud.NONE;
        spoils = GatePayloads.SpoilsView.EMPTY;
    }

    static final int[] RANK_COLOURS = {0xFFF4F6FB, 0xFF5FD08F, 0xFF6FB3FF, 0xFFB48CFF, 0xFFF2A541, 0xFFFF5A6A, 0xFFD4AF37};
    static final String[] RANK_LABELS = {"E", "D", "C", "B", "A", "S", "Sov"};

    static int rankColour(int rank) {
        return RANK_COLOURS[Math.max(0, Math.min(6, rank))];
    }

    static String rankLabel(int rank) {
        return RANK_LABELS[Math.max(0, Math.min(6, rank))];
    }
}
