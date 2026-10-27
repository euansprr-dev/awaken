package dev.awaken.progression;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The current task, top right (SPEC §27.10; Euan, 2026-10-26: "every time you have a goal and things to do, you
 * actually have a small checklist top right"): what you're doing right now, as a short checklist that ticks as you do
 * it. The Now line, top left, keeps the where; this keeps the what.
 * <ul>
 * <li>First, <b>Your body remembers</b>: the four moves every hunter has, each ticked the first time you do it (the body
 * module counts them), with its key drawn as a key and, for the ledge, the nearest one to try it on. It replaces the
 * toast that listed them, which ended up in the Journal unread.</li>
 * <li>Then whatever a module offers through {@link #register}: the tracked chain's steps (the living module), with a
 * count where a step has one ("0/10").</li>
 * </ul>
 * Sent only when it changes. Nothing while the opening runs.
 */
public final class Checklist {
    private Checklist() {}

    /** One step: its words ("{key}" where the key cap goes), the key's mapping name, done, a count, a place to do it. */
    public record Row(String text, String key, boolean done, int have, int need, @Nullable BlockPos at) {
        public static Row of(String text, boolean done) {
            return new Row(text, "", done, 0, 0, null);
        }
    }

    public record Task(String id, String title, List<Row> rows) {}

    /** A module's task for this player now, or null. */
    public interface Source {
        @Nullable Task task(ServerPlayer p);
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    public static void register(Source s) {
        SOURCES.add(s);
    }

    /** When the first four moves were all done (game time; 0: not yet). The finished list stays a moment, then goes. */
    public static final String MOVES_DONE = "awaken:first/moves_done";
    public static final int DONE_HOLD = 20 * 4;
    /** The first moves: the counter the body module keeps, the words, the key. */
    static final String[][] MOVES = {
            {"awaken:body_roll", "Hold {key} as you land to roll", "key.sneak"},
            {"awaken:body_sprint", "Double-tap {key} to sprint harder", "key.sprint"},
            {"awaken:body_slide", "Hold {key} while sprinting to slide", "key.sneak"},
            {"awaken:body_mantle", "Press {key} at a ledge to climb it", "key.jump"},
    };
    static final String MOVES_TITLE = "Your body remembers";

    private static final Map<UUID, Task> SENT = new HashMap<>();
    private static final Map<UUID, BlockPos> LEDGE = new HashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            SENT.remove(e.getEntity().getUUID());
            LEDGE.remove(e.getEntity().getUUID());
        });
    }

    /** What the panel shows for this player now, or null. */
    public static @Nullable Task now(ServerPlayer p) {
        Profile prof = Progression.profile(p);
        if (!prof.awakened() || OpeningHooks.inOpening(p)) return null;
        Task moves = moves(p, prof);
        if (moves != null) return moves;
        for (Source s : SOURCES) {
            Task t = s.task(p);
            if (t != null) return t;
        }
        return null;
    }

    /** Your body remembers: until all four are done, and for a moment after. */
    static @Nullable Task moves(ServerPlayer p, Profile prof) {
        long done = prof.counter(MOVES_DONE), now = p.level().getGameTime();
        if (done > 0 && now - done > DONE_HOLD) return null;
        List<Row> rows = new ArrayList<>(MOVES.length);
        boolean all = true, current = false;
        for (String[] m : MOVES) {
            boolean did = prof.counter(m[0]) > 0;
            all &= did;
            BlockPos at = null;
            // the first not done is the one being shown how; the ledge row points at a ledge near you
            if (!did && !current) {
                current = true;
                if (m[0].equals("awaken:body_mantle")) at = LEDGE.get(p.getUUID());
            }
            rows.add(new Row(m[1], m[2], did, 0, 0, at));
        }
        if (all && done == 0) Progression.setCounter(p, MOVES_DONE, Math.max(1, now));
        return new Task("moves", MOVES_TITLE, rows);
    }

    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (t % 40 == 0) findLedge(p);
            Task task = now(p);
            Task was = SENT.get(p.getUUID());
            if (task == null ? was == null : task.equals(was)) continue;
            if (task == null) SENT.remove(p.getUUID());
            else SENT.put(p.getUUID(), task);
            AwakenNet.send(p, payload(task));
        }
    }

    static SignalPayloads.Task payload(@Nullable Task t) {
        if (t == null) return new SignalPayloads.Task("", "", List.of());
        List<SignalPayloads.TaskRow> rows = new ArrayList<>(t.rows().size());
        for (Row r : t.rows()) {
            rows.add(new SignalPayloads.TaskRow(r.text(), r.key(), r.done(), r.have(), r.need(), r.at() != null, r.at() == null ? BlockPos.ZERO : r.at()));
        }
        return new SignalPayloads.Task(t.id(), t.title(), List.copyOf(rows.subList(0, Math.min(16, rows.size()))));
    }

    /**
     * Every 2 s while the ledge move isn't done yet: the nearest chest-high ledge within 10 blocks (a two-block step up
     * from standing ground, with room to stand on top), so the panel can point at one.
     */
    static void findLedge(ServerPlayer p) {
        if (Progression.profile(p).counter("awaken:body_mantle") > 0 || !(p.level() instanceof ServerLevel level)) {
            LEDGE.remove(p.getUUID());
            return;
        }
        BlockPos feet = p.blockPosition(), best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) for (int dy = -2; dy <= 1; dy++) {
            q.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
            if (!loaded(level, q) || !standable(level, q)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos w = q.relative(d);
                if (solid(level, w) && solid(level, w.above()) && !solid(level, w.above(2)) && !solid(level, w.above(3))) {
                    double dist = w.distSqr(feet);
                    if (dist < bestD && dist > 2) {
                        bestD = dist;
                        best = w.above(2).immutable();
                    }
                }
            }
        }
        if (best == null) LEDGE.remove(p.getUUID());
        else LEDGE.put(p.getUUID(), best);
    }

    private static boolean standable(ServerLevel level, BlockPos at) {
        return solid(level, at.below()) && !solid(level, at) && !solid(level, at.above());
    }

    private static boolean solid(ServerLevel level, BlockPos at) {
        return loaded(level, at) && level.getBlockState(at).isCollisionShapeFullBlock(level, at);
    }

    /**
     * Only a chunk that is already whole: hasChunkAt is true for one still generating, and reading it then waits for it
     * on the server thread (a chunk waiting on its roads held the server for seconds, roads-perf 2026-10-27).
     */
    private static boolean loaded(ServerLevel level, BlockPos at) {
        return level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) != null;
    }
}
