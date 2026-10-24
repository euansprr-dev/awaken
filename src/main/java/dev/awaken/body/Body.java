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
    }

    private static @Nullable ClientQuery client;

    /** The client init installs its answers and its half of the one fall path (its own landing roll). */
    public static void installClient(ClientQuery q, java.util.function.ObjDoubleConsumer<Player> fall) {
        client = q;
        BodyServer.clientFall = fall::accept;
    }

    /** Whether the player has this move: awakened, and at or above its rank. Players who never awakened move like vanilla. */
    public static boolean unlocked(Player p, Move m) {
        Profile pr = Progression.profile(p);
        return pr.awakened() && pr.rank().ordinal() >= m.rank.ordinal();
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
        return m != null && m.mantleUntil != 0;
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
