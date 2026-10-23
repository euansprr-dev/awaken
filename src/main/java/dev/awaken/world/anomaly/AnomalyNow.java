package dev.awaken.world.anomaly;

import dev.awaken.core.api.NowHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * What the anomalies suggest in the Now tracker: an open rift ("Rift tear · 40 m NE", 80; "Break the rift's core · 3
 * hits", 85 when you stand at it), a procession ("Follow the lights · 30 m", 55; "Spirit cache · 12 m", 60), a fallen
 * star ("Fallen star · 60 m NE", 75; "Defeat the star's guardian", 85; "Open the fallen star", 82) and a shimmer grove
 * ("Shimmer grove · 40 m SW", 55; "Light steps · 1:52 left", 50 while you stand in it). Wandering gates are suggested by
 * the gates module, so they are left out here.
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
        long now = p.level().getGameTime();
        for (Anomaly a : data.active()) {
            if (a.kind == AnomalyKind.WANDERING_GATE) continue;
            if (a.kind == AnomalyKind.FALLEN_STAR && !a.landed) continue; // still falling, two seconds
            BlockPos at = a.kind == AnomalyKind.SPIRIT_PROCESSION ? BlockPos.containing(Processions.lead(p.level(), a)) : a.focus();
            double d = Spots.horizontal(me, at);
            if (d > RANGE || (!a.owner.equals(p.getUUID()) && d > Spots.MAX)) continue;
            String id = "anomaly:" + a.kind.id + ":" + a.id;
            switch (a.kind) {
                case RIFT_TEAR -> {
                    if (d <= 8) out.add(NowHooks.Suggestion.at(id, 85, "Break the rift's core · " + a.hitsLeft + (a.hitsLeft == 1 ? " hit" : " hits"), at));
                    else out.add(NowHooks.Suggestion.at(id, 80, "Rift tear · " + Spots.where(me, at), at));
                }
                case SPIRIT_PROCESSION -> {
                    if (a.stage == Anomaly.CACHE) out.add(NowHooks.Suggestion.at(id, 60, "Spirit cache · " + Spots.where(me, at), at));
                    else out.add(NowHooks.Suggestion.at(id, 55, "Follow the lights · " + Spots.where(me, at), at));
                }
                case FALLEN_STAR -> {
                    if (a.guardState == Anomaly.GUARD_UP && d <= 32) {
                        Entity g = a.guard == null ? null : p.level().getEntity(a.guard);
                        out.add(NowHooks.Suggestion.at(id, 85, "Defeat the star's guardian", g != null ? g.blockPosition() : at));
                    } else if (a.guardState != Anomaly.GUARD_NONE && a.guardState != Anomaly.GUARD_UP && d <= 12) {
                        out.add(NowHooks.Suggestion.at(id, 82, "Open the fallen star", at));
                    } else {
                        out.add(NowHooks.Suggestion.at(id, 75, "Fallen star · " + Spots.where(me, at), at));
                    }
                }
                case SHIMMER_GROVE -> {
                    if (a.stage == Anomaly.ACTIVE && Groves.inside(p, a, Groves.RADIUS)) {
                        GroveVariant v = Groves.variant(a);
                        String what = v == GroveVariant.WHISPER ? "The spirit has spoken" : v.label;
                        out.add(NowHooks.Suggestion.of(id, 50, what + " · " + left(a.endsAt - now)));
                    } else if (a.stage == Anomaly.ACTIVE) {
                        out.add(NowHooks.Suggestion.at(id, 55, "Shimmer grove · " + Spots.where(me, at) + " · " + left(a.endsAt - now), at));
                    } else {
                        out.add(NowHooks.Suggestion.at(id, 55, "Shimmer grove · " + Spots.where(me, at), at));
                    }
                }
                default -> { }
            }
        }
        return out;
    }

    /** Whole minutes, so the tracker line changes once a minute rather than every update ("2 min left", "ending"). */
    static String left(long ticks) {
        long min = (ticks + Pacing.MINUTE - 1) / Pacing.MINUTE;
        return ticks < Pacing.MINUTE ? "ending" : min + " min left";
    }
}
