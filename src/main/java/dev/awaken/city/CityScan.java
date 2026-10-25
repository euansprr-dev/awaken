package dev.awaken.city;

import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * {@code /awaken city scan}: finds tree wood that hangs in the air in and round the city (dev). Every natural log, bark
 * block and leaf (what {@link CityWriter#vegetation} calls the trees', never the city's own timber) in the scanned
 * chunks is grouped with the ones it touches ({@link CityTrees}); a group none of whose blocks
 * rests on something solid that is not a tree is floating. A group cut by the edge of the scan is reported apart, since
 * it may be held up by chunks that were not read.
 */
final class CityScan {
    private CityScan() {}

    /** One floating group: where its wood nearest the city centre is, how big it is, and which ring of the city that is. */
    record Floater(int x, int y, int z, int blocks, int wood, int dist, String zone, int height, boolean clipped, String woods) {}

    record Result(int chunks, int missing, int treeBlocks, int groups, int cleared, int inside, List<Floater> floaters, int removed, long millis) {
        int count(boolean clipped) {
            int n = 0;
            for (Floater f : floaters) if (f.clipped == clipped) n++;
            return n;
        }

        String summary() {
            TreeMap<String, int[]> byZone = new TreeMap<>();
            for (Floater f : floaters) {
                if (f.clipped) continue;
                int[] a = byZone.computeIfAbsent(f.zone, k -> new int[2]);
                a[0]++;
                a[1] += f.wood;
            }
            StringBuilder sb = new StringBuilder();
            byZone.forEach((z, a) -> sb.append(' ').append(z).append(' ').append(a[0]).append(" groups/").append(a[1]).append(" wood;"));
            return String.format(Locale.ROOT, "%d chunks (%d missing), %d tree blocks in %d groups (%d in the columns the city clears, %d of them within the apron); %d floating with wood, %d more cut by the scan's edge;%s%s (%d ms)",
                    chunks, missing, treeBlocks, groups, cleared, inside, count(false), count(true), sb.length() == 0 ? " none" : sb.toString(),
                    removed > 0 ? " " + removed + " blocks removed;" : "", millis);
        }
    }

    /** The last scan's floaters, biggest first (for {@code /awaken city floater <n>}). */
    static volatile List<Floater> last = List.of();

    /**
     * Reads the chunks within {@code radius} of the city's centre (loading them from disk) and finds the floating groups
     * of tree wood. With {@code tidy}, removes those too (a world made before the sweep took the lands' bark has them).
     */
    static Result scan(ServerLevel level, CityLayout l, int radius, boolean tidy) {
        long t0 = System.nanoTime();
        Long2ByteOpenHashMap blocks = new Long2ByteOpenHashMap(1 << 20);
        Long2ObjectOpenHashMap<Block> woods = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet scanned = new LongOpenHashSet();
        int missing = 0;
        int c0x = (l.cx - radius) >> 4, c1x = (l.cx + radius) >> 4, c0z = (l.cz - radius) >> 4, c1z = (l.cz + radius) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                long dx = (cx << 4) + 8 - l.cx, dz = (cz << 4) + 8 - l.cz;
                if (dx * dx + dz * dz > (long) radius * radius) continue;
                ChunkAccess chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
                if (chunk == null) {
                    missing++;
                    continue;
                }
                scanned.add(ChunkPos.pack(cx, cz));
                CityTrees.read(chunk, CityWriter::vegetation, blocks, woods);
            }
        }
        int total = blocks.size(), cleared = 0, inside = 0;
        for (long p : blocks.keySet()) {
            int x = BlockPos.getX(p), z = BlockPos.getZ(p);
            if (!CityWriter.owned(l, x, z)) continue;
            cleared++;
            long dx = x - l.cx, dz = z - l.cz;
            if (dx * dx + dz * dz < (long) l.plan.fieldsFrom * l.plan.fieldsFrom) {
                // the city clears these columns: say what is still there (a few, with where), so a leftover can be found
                if (inside++ < 6) {
                    var st = level.getBlockState(new BlockPos(x, BlockPos.getY(p), z));
                    Awaken.LOG.info("[city-scan] left in the city's columns: {} at {} {} {}", BuiltInRegistries.BLOCK.getKey(st.getBlock()), x, BlockPos.getY(p), z);
                }
            }
        }
        List<Floater> out = new ArrayList<>();
        LongArrayList doomed = new LongArrayList();
        int groups = CityTrees.groups(blocks, scanned::contains, (cells, grounded, clipped) -> {
            if (grounded) return;
            int wood = 0, nearD2 = Integer.MAX_VALUE, nx = 0, ny = 0, nz = 0;
            HashMap<Block, Integer> kinds = new HashMap<>();
            for (int i = 0; i < cells.size(); i++) {
                long p = cells.getLong(i);
                Block b = woods.get(p);
                if (b == null) continue;
                wood++;
                kinds.merge(b, 1, Integer::sum);
                int x = BlockPos.getX(p), z = BlockPos.getZ(p), ddx = x - l.cx, ddz = z - l.cz, d2 = ddx * ddx + ddz * ddz;
                if (d2 < nearD2) {
                    nearD2 = d2;
                    nx = x;
                    ny = BlockPos.getY(p);
                    nz = z;
                }
            }
            if (wood == 0) return;
            if (tidy && !clipped) doomed.addAll(cells);
            StringBuilder names = new StringBuilder();
            kinds.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3).forEach(e -> names.append(names.length() == 0 ? "" : ", ")
                    .append(BuiltInRegistries.BLOCK.getKey(e.getKey())).append(" x").append(e.getValue()));
            out.add(new Floater(nx, ny, nz, cells.size(), wood, (int) Math.round(Math.sqrt(nearD2)), zone(l, nx, nz), ny - l.streetY, clipped, names.toString()));
        });
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < doomed.size(); i++) {
            long p = doomed.getLong(i);
            level.setBlock(pos.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        out.sort(Comparator.comparingInt((Floater f) -> f.clipped ? 1 : 0).thenComparingInt(f -> -f.wood));
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        Result r = new Result(scanned.size(), missing, total, groups, cleared, inside, List.copyOf(out), doomed.size(), ms);
        last = r.floaters;
        return r;
    }

    /** Which ring of the city a column lies in. */
    static String zone(CityLayout l, int wx, int wz) {
        int px = wx - l.cx, pz = wz - l.cz;
        double r = Math.sqrt((double) px * px + (double) pz * pz);
        CityPlan p = l.plan;
        if (p.kind(px, pz) != CityPlan.VOID) return "inside-wall";
        if (r < p.fieldsFrom) return "apron";
        if (r <= p.fieldsTo) return "fields";
        if (r <= l.reach) return "edge";
        if (r <= l.reach + CityWriter.RING) return "ring";
        if (r <= l.site.coreRadius() + dev.awaken.terrain.api.CityBasin.MARGIN) return "reserve";
        return "outside";
    }

    /** The scan on the console and in chat: the summary and the biggest floaters. */
    static List<String> report(Result r, int max) {
        List<String> lines = new ArrayList<>();
        lines.add(r.summary());
        int n = 0;
        for (Floater f : r.floaters) {
            if (n++ >= max) break;
            lines.add(String.format(Locale.ROOT, "#%d %d %d %d: %d wood in %d blocks (%s), %d out (%s), %d above the street%s", n, f.x, f.y, f.z, f.wood,
                    f.blocks, f.woods, f.dist, f.zone, f.height, f.clipped ? ", cut by the scan's edge" : ""));
        }
        for (String s : lines) Awaken.LOG.info("[city-scan] {}", s);
        return lines;
    }
}
