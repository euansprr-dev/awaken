package dev.awaken.village.road;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The stall watch's view of chunk generation (SPEC §28.9, Cost): at a long tick, what the chunk under each player waits on.
 * The roads-perf tour of run 40709834352 waited 6.6 minutes for the chunk under a gliding player with every worldgen
 * thread idle and no roads being laid. So this follows that chunk's next step to the neighbour it waits on, and that
 * one's, down to a step that started and never finished or one that never started, beside the state of each level's
 * worldgen and light dispatchers. Read from the watch's own thread while the server thread is held: it only reads
 * (statuses, sizes, references), and a read that fails says so.
 */
final class ChunkWaits {
    private ChunkWaits() {}

    /** How many neighbours a chain follows. */
    private static final int HOPS = 8;
    private static final List<ChunkStatus> STATUSES = ChunkStatus.getStatusList();

    /** One line a level with players (the dispatchers), then one a player (the chain under them). */
    static String describe(MinecraftServer s) {
        StringBuilder b = new StringBuilder();
        try {
            for (ServerLevel level : s.getAllLevels()) {
                if (level.players().isEmpty()) continue;
                b.append("\n  ").append(level(level));
                for (ServerPlayer p : level.players()) {
                    b.append("\n  under ").append(p.getGameProfile().name()).append(": ").append(chain(level.getChunkSource().chunkMap, p.chunkPosition()));
                }
            }
        } catch (RuntimeException e) {
            b.append("\n  (chunk generation not read: ").append(e).append(')');
        }
        return b.toString();
    }

    /** A level's chunk generation: its worldgen and light dispatchers, its light tasks, the generation and chunk tasks waiting. */
    static String level(ServerLevel level) {
        ChunkMap map = level.getChunkSource().chunkMap;
        var light = level.getChunkSource().getLightEngine();
        return level.dimension().identifier() + ": worldgen " + dispatcher(map.worldgenTaskDispatcher) + "; light "
                + dispatcher(map.lightTaskDispatcher) + ", " + light.lightTasks.size() + " light tasks"
                + (light.scheduled.get() ? " (an update scheduled)" : "") + "; " + map.pendingGenerationTasks.size()
                + " generation tasks not yet run; " + level.getChunkSource().getPendingTasksCount() + " chunk tasks for the server thread";
    }

    /** A dispatcher: its queue (the head's level, or empty), asleep or not, and its two executors ("name status queue-empty"). */
    private static String dispatcher(ChunkTaskDispatcher d) {
        return (d.queue.hasWork() ? d.queue.toString() : "queue empty") + (d.sleeping ? ", asleep" : ", awake")
                + ", " + d.dispatcher + ", " + d.executor;
    }

    /**
     * From {@code at}: each chunk's status, the step it started, its levels and its generation task; then the neighbour
     * its next step waits on (the one furthest behind), until a chunk whose step started and didn't finish, or whose
     * neighbours are ready and the step never started.
     */
    static String chain(ChunkMap map, ChunkPos at) {
        StringBuilder b = new StringBuilder();
        for (int hop = 0; hop < HOPS; hop++) {
            if (hop > 0) b.append("\n    waits for ");
            b.append(at.x()).append(',').append(at.z());
            ChunkHolder h = map.getVisibleChunkIfPresent(at.pack());
            if (h == null) return b.append(" (no holder)").toString();
            ChunkStatus latest = h.getLatestStatus(), started = h.startedWork.get();
            b.append(' ').append(name(latest));
            if (started != latest) b.append(" (").append(name(started)).append(" started)");
            b.append(", ticket level ").append(h.getTicketLevel()).append(", queue level ").append(h.getQueueLevel());
            ChunkGenerationTask task = h.task.get();
            if (task != null) {
                int n = 0, done = 0;
                try {
                    var layer = task.scheduledLayer;
                    n = layer.size();
                    for (int i = 0; i < n; i++) if (layer.get(i).isDone()) done++;
                } catch (RuntimeException e) {
                    // the layer changed while read
                }
                b.append(", a task to ").append(name(task.targetStatus)).append(" scheduling ").append(name(task.scheduledStatus))
                        .append(" (").append(done).append(" of ").append(n).append(" done)").append(task.markedForCancellation ? ", cancelled" : "");
            }
            if (latest == ChunkStatus.FULL) return b.append(": done").toString();
            if (started != latest) return b.append(": its ").append(name(started)).append(" step started and hasn't finished").toString();
            ChunkStatus next = latest == null ? ChunkStatus.EMPTY : STATUSES.get(latest.getIndex() + 1);
            ChunkDependencies deps = ChunkPyramid.GENERATION_PYRAMID.getStepTo(next).directDependencies();
            ChunkPos behind = null;
            @Nullable ChunkStatus behindHas = null;
            ChunkStatus behindNeeds = null;
            for (int r = 1; r < deps.size(); r++) {
                ChunkStatus needs = deps.get(r);
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    ChunkHolder o = map.getVisibleChunkIfPresent(ChunkPos.pack(at.x() + dx, at.z() + dz));
                    ChunkStatus has = o == null ? null : o.getLatestStatus();
                    if (has != null && has.isOrAfter(needs)) continue;
                    if (behind == null || (behindHas != null && (has == null || behindHas.isAfter(has)))) {
                        behind = new ChunkPos(at.x() + dx, at.z() + dz);
                        behindHas = has;
                        behindNeeds = needs;
                    }
                }
            }
            if (behind == null) return b.append(": its neighbours are ready for ").append(name(next)).append(" and that step hasn't started").toString();
            b.append(", its ").append(name(next)).append(" step needs ").append(name(behindNeeds)).append(" around it");
            at = behind;
        }
        return b.append(" ...").toString();
    }

    private static String name(@Nullable ChunkStatus s) {
        return s == null ? "nothing" : s.toString().replace("minecraft:", "");
    }
}
