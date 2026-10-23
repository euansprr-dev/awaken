package dev.awaken.world.anomaly;

import dev.awaken.core.api.NowHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * What the anomalies suggest in the Now tracker: an open rift ("Rift tear · 40 m NE", 80; "Break the rift's core · 3
 * hits", 85 when you stand at it) and a procession ("Follow the lights · 30 m", 55; "Spirit cache · 12 m", 60).
 * Wandering gates are suggested by the gates module, so they are left out here.
 */
final class AnomalyNow {
    private AnomalyNow() {}

    static final int RANGE = 160;

    static void register() {
        NowHooks.register(AnomalyNow::suggest);
    }

    static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return List.of();
        AnomalyData data = AnomalyData.get(p.level().getServer());
        if (data.active().isEmpty()) return List.of();
        List<NowHooks.Suggestion> out = new ArrayList<>(2);
        BlockPos me = p.blockPosition();
        for (Anomaly a : data.active()) {
            if (a.kind == AnomalyKind.WANDERING_GATE) continue;
            BlockPos at = a.kind == AnomalyKind.SPIRIT_PROCESSION ? BlockPos.containing(Processions.lead(p.level(), a)) : a.focus();
            double d = Spots.horizontal(me, at);
            if (d > RANGE || (!a.owner.equals(p.getUUID()) && d > Spots.MAX)) continue;
            String id = "anomaly:" + a.kind.id + ":" + a.id;
            if (a.kind == AnomalyKind.RIFT_TEAR) {
                if (d <= 8) out.add(NowHooks.Suggestion.at(id, 85, "Break the rift's core · " + a.hitsLeft + (a.hitsLeft == 1 ? " hit" : " hits"), at));
                else out.add(NowHooks.Suggestion.at(id, 80, "Rift tear · " + Spots.where(me, at), at));
            } else if (a.stage == Anomaly.CACHE) {
                out.add(NowHooks.Suggestion.at(id, 60, "Spirit cache · " + Spots.where(me, at), at));
            } else {
                out.add(NowHooks.Suggestion.at(id, 55, "Follow the lights · " + Spots.where(me, at), at));
            }
        }
        return out;
    }
}
