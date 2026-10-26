package dev.awaken.farsight.voxel;

import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Real land ahead (SPEC §20, Far Sight 3): the server loads or generates, a few at a time and only while it keeps up,
 * the chunks in a band just past the render distance and in a cone ahead of where the player is moving, so City Sight
 * captures the real blocks (trees, structures, all of it) before they reach the render distance. The far view's guess
 * then gives way to the real land out there, in the haze, dissolving over a second, instead of at the edge where the
 * player looks (the game interleaves a tree's growth with where the next tree goes, so where trees will stand can't be
 * replayed without growing them: generating the real chunks is the exact way). A chunk is held by Awaken's own ticket,
 * loading only (never ticking), which starts its short timeout once the chunk is ready to save.
 */
public final class VoxelAhead {
    private VoxelAhead() {}

    /** Chunks past the render distance taken all round, and how much farther ahead in the direction of travel. */
    static final int BAND = 3, AHEAD = 12;
    /** Two asks a round (every ROUND ticks), at most IN_FLIGHT waiting at once. */
    static final int ROUND = 10, IN_FLIGHT = 6;
    /** Only while the server's tick takes less than this (ms): generation never costs the game its pace. */
    static final float HEALTHY_MS = 30f;

    private static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, Awaken.MODID);
    /** Loads (never ticks); times out 40 ticks after the chunk is ready to save. */
    static final DeferredHolder<TicketType, TicketType> TICKET = TICKETS.register("farsight_ahead",
            () -> new TicketType(40L, TicketType.FLAG_LOADING));

    private static final Long2IntOpenHashMap ASKED = new Long2IntOpenHashMap();
    /** Chunks asked for and seen loaded (or known some other way): never asked again this session. */
    private static final LongOpenHashSet DONE = new LongOpenHashSet();
    private static double lastX, lastZ, velX, velZ;
    private static int asked;

    public static void init(IEventBus modBus) {
        TICKETS.register(modBus);
    }

    /** Chunks asked for this session (status). */
    public static int asked() {
        return asked;
    }

    /** A chunk loaded (server thread): asked or not, it needs no asking now. */
    static synchronized void loaded(long key) {
        ASKED.remove(key);
        DONE.add(key);
    }

    static synchronized void reset() {
        ASKED.clear();
        DONE.clear();
        asked = 0;
    }

    /** One round (server thread, while City Sight draws): a couple more chunks ahead, if the server keeps up. */
    static synchronized void tick(ServerLevel level, int tick) {
        if (tick % ROUND != 0 || level.players().isEmpty()) return;
        ServerPlayer p = level.players().getFirst();
        double x = p.getX(), z = p.getZ();
        // the player's heading, smoothed over the rounds
        double vx = (x - lastX) / ROUND, vz = (z - lastZ) / ROUND;
        if (Math.abs(vx) > 20 || Math.abs(vz) > 20) vx = vz = 0; // a jump
        velX += (vx - velX) * 0.3;
        velZ += (vz - velZ) * 0.3;
        lastX = x;
        lastZ = z;
        // let waiting asks lapse after 30 s (a chunk the server never got to)
        ASKED.long2IntEntrySet().removeIf(e -> tick - e.getIntValue() > 600);
        if (ASKED.size() >= IN_FLIGHT || level.getServer().getCurrentSmoothedTickTime() > HEALTHY_MS) return;
        int rd = level.getServer().getPlayerList().getViewDistance();
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        double speed = Math.hypot(velX, velZ), hx = speed > 0.05 ? velX / speed : 0, hz = speed > 0.05 ? velZ / speed : 0;
        int reach = rd + BAND + (speed > 0.05 ? AHEAD : 0);
        long best1 = Long.MIN_VALUE, best2 = Long.MIN_VALUE;
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r <= rd + 1 || r > reach) continue;
                // all round within the band; past it, only in a 35° cone ahead
                if (r > rd + BAND && (dx * hx + dz * hz) < r * 0.82) continue;
                long key = ChunkPos.pack(cx + dx, cz + dz);
                if (DONE.contains(key) || ASKED.containsKey(key)) continue;
                if (level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    DONE.add(key);
                    continue;
                }
                // nearest first, and ahead before behind
                double score = r - 4 * (dx * hx + dz * hz) / Math.max(1, r);
                if (score < d1) {
                    d2 = d1;
                    best2 = best1;
                    d1 = score;
                    best1 = key;
                } else if (score < d2) {
                    d2 = score;
                    best2 = key;
                }
            }
        }
        for (long key : new long[]{best1, best2}) {
            if (key == Long.MIN_VALUE || ASKED.size() >= IN_FLIGHT) continue;
            ASKED.put(key, tick);
            asked++;
            level.getChunkSource().addTicketWithRadius(TICKET.get(), new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)), 0);
        }
    }
}
