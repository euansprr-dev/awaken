package dev.awaken.gates.realms;

import dev.awaken.core.AwakenNet;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.net.WorldPayloads;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What makes a Realm's air move (SPEC 50.8): the same air and small life a world floor has (SPEC 38.9), told to a client when
 * it arrives and again every half minute in case it missed that. No card: the crossing has already said where you are.
 */
final class RealmAir {
    private static final Map<ServerPlayer, Integer> TOLD = new WeakHashMap<>();
    private static final int AGAIN = 600;

    private RealmAir() {}

    /** Called each half second for a player in a Realm. */
    static void tick(ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, int t) {
        boolean told = Integer.valueOf(g.id).equals(TOLD.get(p));
        if (told && (t + p.getId()) % AGAIN >= 10) return;
        TOLD.put(p, g.id);
        List<String> air = plan.bp().sky().air();
        AwakenNet.send(p, new WorldPayloads.Arrival(g.id, 0, 1, "realm", plan.bp().name(), "", "", "", "", "", "", "",
                air.size() > 16 ? air.subList(0, 16) : air, false, false, Realms.world(ref.cell(), 0, 0, 0), plan.land().air()));
    }
}
