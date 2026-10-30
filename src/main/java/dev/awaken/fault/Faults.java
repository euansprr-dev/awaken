package dev.awaken.fault;

import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Plays Faults (SPEC §43.8): the public way in for every big moment. A Fault plays for every player in the event's reach;
 * a Great Fault plays once per player (counted as {@code awaken:fault_<key>}) and as a plain Fault after. {@link #mend}
 * ends the band's flicker with a clean sweep and a clear chord; a woken bell ends on one.
 */
public final class Faults {
    private Faults() {}

    /** Plays it for one player. Returns the size it played at. */
    public static Fault.Size play(ServerPlayer p, Fault f) {
        Fault sent = f;
        if (f.size() == Fault.Size.GREAT) {
            String counter = "awaken:fault_" + f.key();
            if (f.key().isEmpty() || Progression.profile(p).counter(counter) > 0) sent = f.as(Fault.Size.FAULT);
            else Progression.count(p, counter);
        }
        dev.awaken.core.AwakenNet.send(p, new FaultNet.Play(sent));
        return sent.size();
    }

    /** Plays it for every player within {@code reach} blocks of {@code at} in that level. */
    public static List<ServerPlayer> playNear(ServerLevel level, BlockPos at, int reach, Fault f) {
        List<ServerPlayer> near = near(level, at, reach);
        for (ServerPlayer p : near) play(p, f);
        return near;
    }

    /** The mend: a clean sweep and a clear chord; the band's edge stops flickering. */
    public static void mend(ServerPlayer p) {
        dev.awaken.core.AwakenNet.send(p, new FaultNet.Mend());
    }

    public static void mendNear(ServerLevel level, BlockPos at, int reach) {
        for (ServerPlayer p : near(level, at, reach)) mend(p);
    }

    /** Whether this player has had this Great Fault. */
    public static boolean seen(ServerPlayer p, String key) {
        return Progression.profile(p).counter("awaken:fault_" + key) > 0;
    }

    private static List<ServerPlayer> near(ServerLevel level, BlockPos at, int reach) {
        double r2 = (double) reach * reach;
        return level.players().stream().filter(p -> !p.isSpectator() && p.blockPosition().distSqr(at) <= r2).toList();
    }
}
