package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.Blueprint;
import dev.awaken.gates.realms.Blueprints;
import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.LandPrograms;
import dev.awaken.gates.realms.RealmPlan;
import dev.awaken.gates.realms.check.MoveProof;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The beat hall (SPEC §72.8, for tours and whoever builds a kind): every beat of the kit in one row, in one kind's own
 * blocks at one rank, written into the level where it is asked for, and each of its moves proved again on the blocks as
 * they stand there. Nothing in a run calls this; a hall is a block of the kind's rock with the beats carved through it,
 * lit as a run is, so a camera inside sees each beat as a body coming to it would.
 */
public final class BeatHall {
    private BeatHall() {}

    /** A laid hall: each beat it has, where each starts (x, y, z, the yaw along it) and what didn't hold. */
    public record Laid(String kind, Rank rank, List<String> beats, List<int[]> starts, List<String> skipped, List<String> failures, int blocks) {}

    /** The last hall laid in each level, for {@link #start}. */
    private static final Map<String, Laid> LAST = new ConcurrentHashMap<>();

    /** The height a hall's floor starts at: clear of any land under it, with room for its climbs and drops. */
    public static final int FEET = 236;

    /**
     * Lays the hall for a rolled kind ({@code id}, a blueprint) at a rank, its first cell at {@code at} running east. Empty
     * when the kind isn't a rolled run under a roof.
     */
    public static Optional<Laid> lay(ServerLevel level, BlockPos at, String id, Rank rank) {
        Optional<Blueprint> bp = Blueprints.get(id);
        if (bp.isEmpty()) return Optional.empty();
        Land land = LandPrograms.make(new Land.Ctx(bp.get(), id, rank, 1, RealmPlan.CURRENT_VERSION));
        if (!(land instanceof CrawlLand<?> crawl)) return Optional.empty();

        BeatLine L = new BeatLine("hall", at.getX(), at.getY(), at.getZ(), 1, 0);
        Beats.pad(L, 4);
        Random r = new Random(72L * 31 + rank.ordinal());
        double t = Math.min(5, rank.ordinal()) / 5.0;
        List<String> beats = new ArrayList<>(), skipped = new ArrayList<>();
        List<int[]> starts = new ArrayList<>();
        int side = 1;
        for (Beat b : Beat.values()) {
            if (b.rank.ordinal() > rank.ordinal()) {
                skipped.add(b.title + " (from " + b.rank + ")");
                continue;
            }
            int[] start = {L.x(L.a, 0), L.feet, L.z(L.a, 0), -90};
            if (!Beats.build(b, L, t, side, rank, r)) {
                skipped.add(b.title + " (found no shape that holds)");
                continue;
            }
            beats.add(b.title);
            starts.add(start);
            side = -side;
            Beats.pad(L, 3);
            // keep the hall about level, so it fits over the land and under the sky: a flight of stairs back toward the start
            int off = L.feet - at.getY();
            if (Math.abs(off) > 10) {
                Beats.stairs(L, -off);
                Beats.pad(L, 2);
            }
        }
        Beats.pad(L, 4);
        L.s.done();
        BlockGetter view = L.s.view(crawl.beatPaint(L.s));

        int y0 = Math.max(level.getMinY(), L.yLo - 10), y1 = Math.min(level.getMaxY() - 1, L.yHi + 2), n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = L.s.x0; x <= L.s.x1; x++) for (int z = L.s.z0; z <= L.s.z1; z++) for (int y = y0; y <= y1; y++) {
            pos.set(x, y, z);
            BlockState s = view.getBlockState(pos);
            // no shape updates: a ladder or a pipe on its wall stays where the hall put it
            if (level.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) n++;
        }
        List<String> failures = MoveProof.failures(level, L.moves, rank);
        Laid laid = new Laid(id, rank, List.copyOf(beats), List.copyOf(starts), List.copyOf(skipped), failures, n);
        LAST.put(level.dimension().identifier().toString(), laid);
        return Optional.of(laid);
    }

    /** Where the n-th beat of the last hall in a level starts, or null. */
    public static int[] start(ServerLevel level, int index) {
        Laid h = LAST.get(level.dimension().identifier().toString());
        return h == null || index < 0 || index >= h.starts().size() ? null : h.starts().get(index);
    }

    /** The n-th beat's name in the last hall in a level, or null. */
    public static String name(ServerLevel level, int index) {
        Laid h = LAST.get(level.dimension().identifier().toString());
        return h == null || index < 0 || index >= h.beats().size() ? null : h.beats().get(index);
    }
}
