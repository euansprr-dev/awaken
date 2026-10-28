package dev.awaken.body;

import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Public API of the Awakened Body (stable). Works on both sides: the server answers from the live motion, the client
 * for its own player from its prediction. Other modules should prefer {@code core.api.MovementHooks}, which this backs.
 */
public final class Body {
    private Body() {}

    /** The client's answers for its own player (installed by the client init; a dedicated server never needs it). */
    public interface ClientQuery {
        boolean sliding();
        boolean tacticalSprinting();
        boolean mantling();
        @Nullable Vec3 slideDirection();
        default boolean wallRunning() { return false; }
        default boolean hanging() { return false; }
        default boolean stairSliding() { return false; }
    }

    private static @Nullable ClientQuery client;

    /** The client init installs its answers and its half of the one fall path (its own landing roll). */
    public static void installClient(ClientQuery q, java.util.function.ObjDoubleConsumer<Player> fall) {
        client = q;
        BodyServer.clientFall = fall::accept;
    }

    /**
     * Whether the player has this move. With the Runner's Kit (the default, SPEC §16.3): everyone, from the first
     * second; ranks improve the moves ({@link #runnerRank}). Without it, the old rule: awakened and at or above the
     * move's rank, and no Runner moves at all.
     */
    public static boolean unlocked(Player p, Move m) {
        if (BodyConfig.runnersKit()) return true;
        if (m.runner()) return false;
        Profile pr = Progression.profile(p);
        return pr.awakened() && pr.rank().ordinal() >= m.rank.ordinal();
    }

    /** Whether the player holds Wall Stride (the passive power; the client asks its own loadout). */
    public static boolean wallStride(Player p) {
        if (p.level().isClientSide()) {
            var l = dev.awaken.powers.Powers.clientLoadoutOrNull();
            return l != null && l.owns(RunnerRules.STRIDE_POWER);
        }
        return dev.awaken.powers.Powers.owns(p, RunnerRules.STRIDE_POWER);
    }

    /** Wall Stride's level (0 without it): its ladder (SPEC 36) lengthens, steadies and chains the run. */
    public static int wallStrideLevel(Player p) {
        return dev.awaken.powers.ladder.StepHooks.strideLevel(p);
    }

    /** The rank the Runner's moves improve by (E before the awakening). */
    public static dev.awaken.progression.Rank runnerRank(Player p) {
        Profile pr = Progression.profile(p);
        return pr.awakened() ? pr.rank() : dev.awaken.progression.Rank.E;
    }

    public static boolean isSliding(Player p) {
        if (p.level().isClientSide()) return client != null && client.sliding();
        BodyMotion m = motion(p);
        return m != null && m.slideUntil != 0;
    }

    public static boolean isTacticalSprinting(Player p) {
        if (p.level().isClientSide()) return client != null && client.tacticalSprinting();
        BodyMotion m = motion(p);
        return m != null && m.tacUntil != 0;
    }

    public static boolean isMantling(Player p) {
        if (p.level().isClientSide()) return client != null && client.mantling();
        BodyMotion m = motion(p);
        return m != null && (m.mantleUntil != 0 || m.vaultUntil != 0); // a crossing is a mantle to everyone else
    }

    /** Sliding down a run of stairs or slabs (SPEC §17.22). */
    public static boolean isStairSliding(Player p) {
        if (p.level().isClientSide()) return client != null && client.stairSliding();
        BodyMotion m = motion(p);
        return m != null && m.slideUntil != 0 && m.stairSliding;
    }

    /** Running along a wall (the Runner's wall-run). */
    public static boolean isWallRunning(Player p) {
        if (p.level().isClientSide()) return client != null && client.wallRunning();
        BodyMotion m = motion(p);
        return m != null && m.runUntil != 0;
    }

    /** Hanging from a ledge (or moving between holds). */
    public static boolean isHanging(Player p) {
        if (p.level().isClientSide()) return client != null && client.hanging();
        BodyMotion m = motion(p);
        return m != null && m.hanging;
    }

    /** Overworld game time of the last tick a move ran, or {@link Long#MIN_VALUE}. Server side. */
    public static long lastMoveTick(Player p) {
        BodyMotion m = p.level().isClientSide() ? null : motion(p);
        return m == null ? Long.MIN_VALUE : m.lastMove;
    }

    public static @Nullable Vec3 slideDirection(Player p) {
        if (p.level().isClientSide()) return client == null ? null : client.slideDirection();
        BodyMotion m = motion(p);
        return m == null || m.slideUntil == 0 ? null : new Vec3(m.slideDirX, 0, m.slideDirZ);
    }

    /** The unlocks the System has announced to this player. */
    public static BodyMemory memory(Player p) {
        return p.getData(BodyContent.MEMORY);
    }

    private static @Nullable BodyMotion motion(Player p) {
        return p instanceof ServerPlayer ? p.getExistingDataOrNull(BodyContent.MOTION) : null;
    }
}
