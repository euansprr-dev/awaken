package dev.awaken.core.client;

import dev.awaken.Awaken;
import dev.awaken.core.EdgeHold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.function.LongPredicate;

/**
 * The edge hold on the client (SPEC §28.9): once a tick, last of every listener to the movement input (it fires in
 * {@code LocalPlayer.aiStep} just before {@code travel}), the local player's horizontal velocity is capped by
 * {@link EdgeHold} against the chunks this client has. Every fast mover moves the player by its velocity, so this one
 * hook holds them all: Skyborn flight and its boosts, glides, the Tether's launch and zip, dashes and the movement
 * powers (set on the client, or by the server's motion packets), the autopilot's glide, and vanilla's elytra, firework
 * boosts, riptide and creative or spectator flight. A mover that sets its velocity after this point in a tick is held
 * from the next one (a tick's travel, well inside the 1.25 s it looks ahead). Riding is the mount's movement, not held.
 * Travel adds the mover's own acceleration after the cap, so a guard at the end of the player's tick (before the
 * position is sent) puts back any step that brought the player nearer than {@link EdgeHold#BODY} to a chunk this
 * client doesn't have, on the axis that crossed: at the edge the player stops, never enters.
 * No allocation a tick; one chunk lookup a chunk the path crosses. Client only. While it holds, {@link EdgeHoldCue}
 * plays a headwind and closes a pale mist in toward the edge, from {@link #shown()} and {@link #edgeShown()}.
 */
public final class EdgeHoldClient {
    private EdgeHoldClient() {}

    /** The cap last tick, blocks a tick (infinite when nothing held). */
    private static double last = Double.POSITIVE_INFINITY;
    /** The hold under way: since when, and its slowest cap (one log line when a long one ends). */
    private static int heldTicks;
    private static double slowest;
    private static long toldAt;
    /** The cue: its strength 0..1 and the edge it closes in on (blocks ahead on the path), both eased. */
    private static float shown, edgeShown = 64;
    private static double heldEdge = Double.POSITIVE_INFINITY;
    private static int sinceHeld = 1000;
    /** The cue holds this many ticks past the last tick held (a crawl dipping under the cap stays one hold). */
    private static final int CUE_LINGER = 10;
    /** An edge this far ahead or more gives the cue its least, {@link #CUE_LEAST}; at the edge, all of it. */
    private static final double CUE_FAR = 48;
    private static final float CUE_LEAST = 0.3F;

    /** Whether the client has the chunk (one lookup in its chunk storage; null for one missing or out of its range). */
    private static final LongPredicate HAS = key -> {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null || level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false) != null;
    };

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MovementInputUpdateEvent.class, e -> {
            if (e.getEntity() instanceof LocalPlayer p) hold(p);
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof LocalPlayer p) guard(p);
        });
    }

    private static void hold(LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        double cap = Double.POSITIVE_INFINITY, edge = Double.POSITIVE_INFINITY;
        if (!p.isPassenger() && speed > 1.0E-4) {
            // the client holds chunks out to its render distance only (the server's, if smaller): past that nothing's missing;
            // a slow mover still reads the last block ahead, where it eases to a stop
            double reach = Math.min(Math.max(speed * EdgeHold.LOOK_TICKS, EdgeHold.BODY + EdgeHold.LAST),
                    (Minecraft.getInstance().options.getEffectiveRenderDistance() - 1) * 16.0);
            edge = EdgeHold.edge(p.getX(), p.getZ(), v.x, v.z, reach, HAS);
            cap = EdgeHold.cap(speed, edge, last);
        }
        last = cap;
        cue(cap < speed, edge);
        if (cap < speed) {
            double k = cap / speed;
            p.setDeltaMovement(v.x * k, v.y, v.z * k);
            if (heldTicks++ == 0 || cap < slowest) slowest = cap;
        } else if (heldTicks > 0) {
            long now = System.currentTimeMillis();
            // a long hold says so in the log (at most every 10 s): the tours read it, a player's log shows the land lagging
            if (heldTicks >= 20 && now - toldAt > 10_000) {
                toldAt = now;
                Awaken.LOG.info("[edgehold] held {} ticks at the edge of the land this client has, slowest {} blocks a second", heldTicks,
                        Math.round(slowest * 20 * 10) / 10.0);
            }
            heldTicks = 0;
        }
    }

    /**
     * After the move, before it's sent: a step that took the player from {@link EdgeHold#BODY} clear of every missing
     * chunk to nearer is put back on the axis that crossed, and that axis's speed zeroed. A player already nearer (just
     * arrived, the land still coming) isn't held there.
     */
    private static void guard(LocalPlayer p) {
        double x = p.getX(), z = p.getZ(), xo = p.xo, zo = p.zo;
        if (p.isPassenger() || (x == xo && z == zo) || clear(x, z) || !clear(xo, zo)) return;
        Vec3 v = p.getDeltaMovement();
        if (clear(x, zo)) {
            p.setPos(x, p.getY(), zo);
            p.setDeltaMovement(v.x, v.y, 0);
        } else if (clear(xo, z)) {
            p.setPos(xo, p.getY(), z);
            p.setDeltaMovement(0, v.y, v.z);
        } else {
            p.setPos(xo, p.getY(), zo);
            p.setDeltaMovement(0, v.y, 0);
        }
    }

    /** Whether every chunk within {@link EdgeHold#BODY} of (x, z) across is here. */
    private static boolean clear(double x, double z) {
        int x0 = Mth.floor(x - EdgeHold.BODY) >> 4, x1 = Mth.floor(x + EdgeHold.BODY) >> 4;
        int z0 = Mth.floor(z - EdgeHold.BODY) >> 4, z1 = Mth.floor(z + EdgeHold.BODY) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (!HAS.test(ChunkPos.pack(cx, cz))) return false;
            }
        }
        return true;
    }

    /** Eases the cue toward this tick's hold: in over about half a second, out over about a second. */
    private static void cue(boolean held, double edge) {
        if (held) {
            sinceHeld = 0;
            heldEdge = edge;
        } else if (sinceHeld < 1000) {
            sinceHeld++;
        }
        float target = sinceHeld <= CUE_LINGER ? (float) Mth.clamp(1 - heldEdge / CUE_FAR, CUE_LEAST, 1) : 0F;
        shown += (target - shown) * (target > shown ? 0.12F : 0.06F);
        if (shown < 0.002F) shown = 0F;
        edgeShown += ((float) Math.min(heldEdge, 96) - edgeShown) * 0.2F;
    }

    /** The cue's strength now, 0 (no hold) to 1 (held right at the edge). */
    public static float shown() {
        return shown;
    }

    /** Blocks ahead on the path to the edge the cue closes in on. */
    public static float edgeShown() {
        return edgeShown;
    }
}
