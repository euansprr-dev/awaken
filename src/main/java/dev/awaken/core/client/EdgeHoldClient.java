package dev.awaken.core.client;

import dev.awaken.Awaken;
import dev.awaken.core.EdgeHold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.LongPredicate;

/**
 * The edge hold on the client (SPEC §28.9): once a tick, last of every listener to the movement input (it fires in
 * {@code LocalPlayer.aiStep} just before {@code travel}), the local player's horizontal velocity is capped by
 * {@link EdgeHold} against the chunks this client has. Every fast mover moves the player by its velocity, so this one
 * hook holds them all: Skyborn flight and its boosts, glides, the Tether's launch and zip, dashes and the movement
 * powers (set on the client, or by the server's motion packets), the autopilot's glide, and vanilla's elytra, firework
 * boosts, riptide and creative or spectator flight. A mover that sets its velocity after this point in a tick is held
 * from the next one (a tick's travel, well inside the 1.25 s it looks ahead). Riding is the mount's movement, not held.
 * No allocation a tick; one chunk lookup a chunk the path crosses. Client only.
 */
public final class EdgeHoldClient {
    private EdgeHoldClient() {}

    /** The cap last tick, blocks a tick (infinite when nothing held). */
    private static double last = Double.POSITIVE_INFINITY;
    /** The hold under way: since when, and its slowest cap (one log line when a long one ends). */
    private static int heldTicks;
    private static double slowest;
    private static long toldAt;

    /** Whether the client has the chunk (one lookup in its chunk storage; null for one missing or out of its range). */
    private static final LongPredicate HAS = key -> {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null || level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false) != null;
    };

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MovementInputUpdateEvent.class, e -> {
            if (e.getEntity() instanceof LocalPlayer p) hold(p);
        });
    }

    private static void hold(LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        double cap = Double.POSITIVE_INFINITY;
        if (!p.isPassenger() && speed > EdgeHold.FLOOR) {
            // the client holds chunks out to its render distance only (the server's, if smaller): past that nothing's missing
            double reach = Math.min(speed * EdgeHold.LOOK_TICKS, (Minecraft.getInstance().options.getEffectiveRenderDistance() - 1) * 16.0);
            cap = EdgeHold.cap(speed, EdgeHold.edge(p.getX(), p.getZ(), v.x, v.z, reach, HAS), last);
        }
        last = cap;
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
}
