package dev.awaken.world.anomaly;

import dev.awaken.core.api.GateHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Wandering gates: a 1–2 floor gate at the player's rank forms on a validated spot nearby and fades after 5–8 minutes
 * unless entered (the gates module builds it, never breaks it, and puts it in the Now tracker). The anomaly only
 * tracks it so the director keeps one event at a time and pays the first-sighting discovery.
 */
final class WanderingGates {
    private WanderingGates() {}

    static final int LIFE_MIN = 5 * Pacing.MINUTE, LIFE_MAX = 8 * Pacing.MINUTE;
    static final double TWO_FLOORS = 0.35;

    static @Nullable Anomaly open(ServerLevel level, ServerPlayer owner, net.minecraft.core.BlockPos ground, AnomalyData data) {
        RandomSource r = owner.getRandom();
        int floors = r.nextDouble() < TWO_FLOORS ? 2 : 1;
        int life = LIFE_MIN + r.nextInt(LIFE_MAX - LIFE_MIN + 1);
        Optional<String> id = GateHooks.openWandering(owner, ground, floors, life);
        if (id.isEmpty()) return null;
        long now = level.getGameTime();
        // a little longer than the gate itself, so the record never outlives a gate that is still standing by much
        Anomaly a = new Anomaly(data.allocateId(), AnomalyKind.WANDERING_GATE, owner.getUUID(), ground, now, now + life + 30 * 20);
        a.gateId = id.get();
        a.contentLevel = floors;
        data.add(a);
        return a;
    }

    static int minutesLeft(Anomaly a, long now) {
        return (int) Math.max(1, Math.round((a.endsAt - 30 * 20 - now) / (double) Pacing.MINUTE));
    }

    /** Returns why it ended, or null while the gate stands. */
    static @Nullable String update(ServerLevel level, AnomalyData data, Anomaly a, long now) {
        Optional<GateHooks.GateView> v = GateHooks.get(level, a.gateId);
        if (v.isEmpty()) return "the gate is gone";
        String state = v.get().state();
        if (state.equals("CLEARED") || state.equals("BROKEN") || state.equals("RELEASED")) return "the gate is " + state.toLowerCase();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
        if (owner != null && !a.discovered && owner.level() == level && Spots.horizontal(owner.blockPosition(), v.get().mouth()) <= Rifts.SEEN) {
            a.discovered = true;
            data.setDirty();
            AnomalyDirector.discovered(owner, a.kind);
        }
        if (now >= a.endsAt && !state.equals("ACTIVE")) return "its time ran out";
        return null;
    }
}
