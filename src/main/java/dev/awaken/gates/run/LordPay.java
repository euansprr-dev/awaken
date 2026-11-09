package dev.awaken.gates.run;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Lord's payout (SPEC §50.4): what the Satchel held lands as one moment, level by level, a bell struck for each. The
 * XP waits in {@link GateMemory#lordXp}, saved with the player, so a crash mid-payout loses none. Logging out pays the rest
 * at once. Nobody waits on it: the run is already over, this is only how the news arrives.
 */
public final class LordPay {
    private LordPay() {}

    /** Ticks after the kill before the first level, then between levels; quicker when many are still to come. */
    static final int FIRST = 30, BEAT = 14, QUICK = 8, MANY = 8;
    /** A low pentatonic walk over vanilla's own bell recording: never pitched up into a bright ding. */
    private static final float[] WALK = {0.63F, 0.71F, 0.84F, 0.94F, 1.06F, 1.26F};

    public static final class Beats {
        long next;
        public int levels;
    }

    private static final Map<UUID, Beats> ACTIVE = new HashMap<>();

    /** Hands this XP to the moment. It starts a beat after the kill, so the kill lands first. */
    public static void start(ServerPlayer p, double xp) {
        if (xp <= 0) return;
        p.getData(GatesRegistry.MEMORY.get()).lordXp += xp;
        begin(p);
    }

    private static void begin(ServerPlayer p) {
        ACTIVE.computeIfAbsent(p.getUUID(), u -> {
            Beats b = new Beats();
            b.next = p.level().getServer().getTickCount() + FIRST;
            return b;
        });
    }

    /** After a crash: whatever hadn't landed plays on the way back in. */
    static void resume(ServerPlayer p) {
        if (p.getData(GatesRegistry.MEMORY.get()).lordXp > 0) begin(p);
    }

    /** Leaving mid-moment: the rest lands now. */
    static void leave(ServerPlayer p) {
        ACTIVE.remove(p.getUUID());
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        if (mem.lordXp <= 0) return;
        double rest = mem.lordXp;
        mem.lordXp = 0;
        Progression.grantXp(p, rest, Progression.XpSource.GATE_BOSS);
    }

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long t = server.getTickCount();
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (t >= e.getValue().next && step(p, e.getValue(), t)) it.remove();
        }
    }

    /** One beat: exactly enough XP to reach the next level, or all that's left. True when it's all landed. */
    public static boolean step(ServerPlayer p, Beats b, long t) {
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        double need = Progression.rawToNextLevel(p);
        // a hair over, so float error can't leave a level one point short and cost a beat
        double slice = Math.min(mem.lordXp, need * 1.000001 + 1e-6);
        mem.lordXp -= slice;
        if (mem.lordXp < 1e-6) mem.lordXp = 0;
        int gained = Progression.grantXpQuietly(p, slice, Progression.XpSource.GATE_BOSS);
        boolean done = mem.lordXp <= 0;
        if (gained > 0) {
            float pitch = WALK[b.levels % WALK.length];
            b.levels += gained;
            p.level().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.3F, pitch);
            dev.awaken.core.AwakenNet.send(p, new GatePayloads.LordLevel(Progression.profile(p).level(), b.levels,
                    b.levels * Curve.POINTS_PER_LEVEL, done));
        }
        if (done) {
            if (b.levels > 0) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.4F, 0.84F);
                dev.awaken.core.api.LivingHooks.levelMoment(p, Progression.profile(p).level(), b.levels);
            }
            return true;
        }
        b.next = t + (mem.lordXp > MANY * need ? QUICK : BEAT);
        return false;
    }
}
