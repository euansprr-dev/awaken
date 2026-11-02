package dev.awaken.hearth.trial;

import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A Proving's Warden (SPEC §46.4, §46.6 to §46.9): who waits at a rank's Reassessment, its hall, how you arrive, what changes in
 * the hall as the fight turns, and what it leaves you. {@link Provings} runs the rest the same for every one of them:
 * the bell, the party, the falls and the hints, the reset, the gathering of what it left, the way home.
 */
abstract class Warden {
    final String mob;
    final Rank rank;
    final Hall hall;

    Warden(String mob, Rank rank, Hall hall) {
        this.mob = mob;
        this.rank = rank;
        this.hall = hall;
    }

    /** Every Warden. In a holder: a Warden's first touch initialises this class before its own WARDEN is set. */
    static List<Warden> all() {
        return All.LIST;
    }

    private static final class All {
        static final List<Warden> LIST = List.of(RingerWarden.WARDEN, ExaminerWarden.WARDEN, UnmadeWarden.WARDEN, SkyWarden.WARDEN,
                KeeperWarden.WARDEN, LoopWarden.WARDEN);
    }

    /** The Warden of a Reassessment to {@code target}, or null where a box trial still stands. */
    static @Nullable Warden of(Rank target) {
        for (Warden w : all()) if (w.rank == target) return w;
        return null;
    }

    /** What the clerk says at the Reading Bell (the same plain measuring; from the second on, a remembered line). */
    abstract String asked();

    /** The bell won't stop: the Fault that opens it; how many ticks it plays. */
    abstract int ring(ServerPlayer m, boolean practice);

    /** Before anyone goes (the Examiner reads the room you're standing in). */
    void before(Provings.Run run, ServerPlayer leader) {}

    /** Where you wake in a hall at {@code centre} (the session's own spot). */
    abstract BlockPos wakeAt(BlockPos centre);

    /** Waking there: the first time ({@code first}), after a fall, after a reset. */
    abstract void wake(ServerPlayer m, Provings.Run run, boolean first);

    /** Once the hall is ready and you're in it, before it comes (sounds, a room turning). */
    void waiting(Provings.Run run, List<ServerPlayer> here, long now) {}

    /** Whether it comes now. */
    abstract boolean comes(Provings.Run run, List<ServerPlayer> here, long now);

    /** Where it comes in, and which way it faces. */
    abstract Vec3 arrive(Provings.Run run);

    abstract float arriveYaw(Provings.Run run);

    /** How far from the hall's middle it goes after you (the whole hall: nowhere in it is a hiding place). */
    int home() {
        return Math.max(hall.wall(), Math.max(-hall.north(), hall.back()));
    }

    /** It's come, standing and after you. */
    void came(Provings.Run run, LivingEntity warden, List<ServerPlayer> here) {}

    /** As the fight goes (the hall turning with its health). */
    void fight(Provings.Run run, LivingEntity warden, long now) {}

    /** Every tick of a run once it's begun (a change still spreading through the hall). */
    void step(Provings.Run run, long now) {}

    /** Everyone fell: the hall goes back to how it was before it came. */
    void reset(Provings.Run run) {}

    /** It died: what the hall does at once. */
    void died(Provings.Run run, long now) {}

    /** After its death, {@code t} ticks on (its gift, its last sounds); what it left is gathered at 30 by the run. */
    abstract void outro(Provings.Run run, List<ServerPlayer> here, long t);

    /** Whether a member has left by the way out. */
    abstract boolean out(Provings.Run run, ServerPlayer m);

    /** Said once, the first time you fall. */
    abstract String firstFall();

    /** Its name, in the hint about what keeps catching you. */
    abstract String name();
}
