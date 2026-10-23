package dev.awaken.core.audio;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Public API of the audio module. Server side; every call is cheap and safe from any server-thread code path
 * (fake players are skipped by {@link AwakenNet}).
 *
 * <ul>
 *   <li>{@link #play(ServerPlayer, Cue)}: a music cue for one player, if its cooldown and the gap between cues
 *   allow (per player, in memory). The client still plays it only when the music slot is free (see {@link Cue}).</li>
 *   <li>{@link #playNear(ServerLevel, BlockPos, double, Cue)}: the same for every awakened player within a radius.</li>
 *   <li>{@link #ui(ServerPlayer, UiSound)}: a System UI sound for one player (not positional, the UI slider).</li>
 * </ul>
 * The awakening and rank-up cues are wired here from progression's hooks; beasts and lairs from discoveries
 * ({@link AudioWatch}); gates call {@link #play}/{@link #playNear} where they form, break and reach a Rest Shrine.
 */
public final class AudioCues {
    private AudioCues() {}

    private static final Map<UUID, CueClock> CLOCKS = new HashMap<>();

    /** Plays a cue for one player if allowed; returns whether it was sent. */
    public static boolean play(ServerPlayer player, Cue cue) {
        if (player == null || player.isRemoved()) return false;
        if (cue != Cue.AWAKENING && !Progression.profile(player).awakened()) return false;
        long now = player.level().getServer().getTickCount();
        CueClock clock = clock(player);
        if (!clock.tryFire(cue, now)) {
            DirectorLog.log(player, "audio", "cue " + cue.id + " held back: " + clock.why(cue, now));
            return false;
        }
        AwakenNet.send(player, new AudioNet.PlayCue(cue.ordinal()));
        DirectorLog.log(player, "audio", "cue " + cue.id);
        return true;
    }

    /** Plays a cue for every awakened player in {@code level} within {@code radius} blocks of {@code pos}. */
    public static int playNear(ServerLevel level, BlockPos pos, double radius, Cue cue) {
        int n = 0;
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2 && play(p, cue)) n++;
        }
        return n;
    }

    /** A System UI sound for this player only. */
    public static void ui(ServerPlayer player, UiSound sound) {
        if (player != null) AwakenNet.send(player, new AudioNet.PlayUi(sound.ordinal()));
    }

    /** Sends a cue regardless of cooldowns (game-master command, autopilot). */
    static void force(ServerPlayer player, Cue cue) {
        AwakenNet.send(player, new AudioNet.PlayCue(cue.ordinal()));
        DirectorLog.log(player, "audio", "cue " + cue.id + " (forced)");
    }

    public static CueClock clock(ServerPlayer player) {
        return CLOCKS.computeIfAbsent(player.getUUID(), u -> new CueClock());
    }

    static void forget(UUID player) {
        CLOCKS.remove(player);
    }

    static void clear() {
        CLOCKS.clear();
    }
}
