package dev.awaken.atlas.client;

import dev.awaken.Awaken;
import dev.awaken.atlas.AtlasNet;
import dev.awaken.atlas.Discovery;
import dev.awaken.atlas.Explored;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The client's Atlas: sampled columns and their disk cache, the mirror of what the server says this player has
 * explored, the mesh (kept warm while the table is shut, so opening never waits), the clouds, and the opening run's
 * thread. One per client; a new world or dimension starts it over.
 */
final class AtlasState {
    private static AtlasState instance;

    static AtlasState get() {
        if (instance == null) instance = new AtlasState();
        return instance;
    }

    static final Stats stats = new Stats();
    /** Ticks between disk saves of changed tiles. */
    static final int SAVE_TICKS = 600;
    /** A bell's ripple: seconds per 100 blocks from the tower. */
    static final float RIPPLE_PER_BLOCK_NANOS = 1.0e9f / 160f;

    final ColumnStore store = new ColumnStore();
    final TileCache cache = new TileCache();
    final AtlasMesh mesh = new AtlasMesh(store);
    final AtlasClouds clouds = new AtlasClouds();
    /** What the known land belongs to: districts in Carillon, Living Wilds regions outside. */
    final ZoneMap zones = new ZoneMap();
    /** When each recently explored cell cleared (nanoTime; later for a ripple), so its clouds part over a second. */
    final Long2LongOpenHashMap revealedAt = new Long2LongOpenHashMap();
    /** Cells a bell's ripple will reach later (nanoTime), kept out of the record until then. */
    private final Long2LongOpenHashMap pending = new Long2LongOpenHashMap();
    int[] trail = new int[0];
    long trailStart;
    /** The opening ended: the next time the table opens it plays the Registry reveal. */
    boolean revealPending;
    private ClientLevel level;
    private String dim = "";
    private boolean roofed;
    private final it.unimi.dsi.fastutil.longs.LongArrayList roofQueue = new it.unimi.dsi.fastutil.longs.LongArrayList();
    /** The Living Wilds regions you've been in, and your party's pins here (from the server). */
    java.util.List<dev.awaken.atlas.AtlasData.Region> regions = java.util.List.of();
    java.util.List<AtlasNet.PinView> pins = java.util.List.of();
    /** Your Hearths and your household's (SPEC §16.8), as the server last sent them. */
    java.util.List<AtlasNet.HomeView> homes = java.util.List.of();
    private int ticks;

    // ---- the server's word ----------------------------------------------------------------------------------------------

    void onSync(AtlasNet.Sync m) {
        Explored mine = Discovery.CLIENT;
        long now = System.nanoTime();
        if (!m.dim().equals(Discovery.clientDim) || m.reset()) {
            Discovery.clientDim = m.dim();
            mine.clear();
            revealedAt.clear();
            pending.clear();
            zones.clear();
            mesh.clear(); // slices built from the old record would float as stray land under the clouds
        }
        boolean animate = !m.reset();
        for (AtlasNet.WireTile t : m.tiles()) {
            zones.put(t.tx(), t.tz(), t.zones());
            zones.putLevels(t.tx(), t.tz(), t.levels());
            long[] old = mine.tile(t.tx(), t.tz()), bits = t.bits().clone();
            if (animate) {
                for (int w = 0; w < Explored.WORDS; w++) {
                    long added = bits[w] & ~(old == null ? 0L : old[w]);
                    while (added != 0) {
                        int bit = Long.numberOfTrailingZeros(added);
                        added &= added - 1;
                        int i = w * 64 + bit;
                        int cx = (t.tx() << Explored.TILE_SHIFT) + (i & (Explored.TILE - 1)), cz = (t.tz() << Explored.TILE_SHIFT) + (i >> Explored.TILE_SHIFT);
                        long at = now;
                        if (m.ripple()) {
                            double dx = (cx << 4) + 8 - m.ox(), dz = (cz << 4) + 8 - m.oz();
                            at += (long) (Math.sqrt(dx * dx + dz * dz) * RIPPLE_PER_BLOCK_NANOS);
                        }
                        if (at > now + 40_000_000L) {
                            // the ripple hasn't reached it: no land there yet, or tall roofs would poke through the clouds
                            bits[w] &= ~(1L << bit);
                            pending.put(Explored.key(cx, cz), at);
                            continue;
                        }
                        revealedAt.put(Explored.key(cx, cz), at);
                        mesh.changed(cx << 4, cz << 4, 16);
                    }
                }
            } else {
                mesh.changed(t.tx() << 9, t.tz() << 9, 512);
            }
            if (old != null) for (int w = 0; w < Explored.WORDS; w++) bits[w] |= old[w];
            mine.putTile(t.tx(), t.tz(), bits);
        }
    }

    /** Cells the ripple reaches now join the record: their land is built and their clouds start to part. */
    private void ripple(long now) {
        if (pending.isEmpty()) return;
        var it = pending.long2LongEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getLongValue() > now) continue;
            int cx = Explored.keyX(e.getLongKey()), cz = Explored.keyZ(e.getLongKey());
            Discovery.CLIENT.mark(cx, cz);
            revealedAt.put(e.getLongKey(), now);
            mesh.changed(cx << 4, cz << 4, 16);
            it.remove();
        }
    }

    void onCity(AtlasNet.City m) {
        zones.city(m.present(), m.x(), m.z());
        mesh.changedAll();
    }

    void onRegions(List<dev.awaken.atlas.AtlasData.Region> list) {
        regions = List.copyOf(list);
        zones.regionsChanged();
    }

    /** A zone you know, for the table's labels and legend: its name, a line under it, colour, and where its known land is. */
    record ZoneLabel(int zone, String name, String sub, int colour, double x, double z, int cells) {}

    private List<ZoneLabel> labels = List.of();
    private int labelsExplored = -1, labelsZones = -1;
    private long labelsAt;

    /**
     * Every zone with known land, and the middle of its known land (where its name goes): districts and regions you've
     * walked, largest first. Worked out from the explored cells when they change, at most twice a second.
     */
    List<ZoneLabel> zoneLabels() {
        int ev = Discovery.CLIENT.version, zv = zones.version();
        long now = System.nanoTime();
        if ((ev == labelsExplored && zv == labelsZones) || now - labelsAt < 500_000_000L) return labels;
        labelsExplored = ev;
        labelsZones = zv;
        labelsAt = now;
        ZoneMap.View view = zoneView();
        it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<long[]> sums = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
        for (Explored.TileBits t : Discovery.CLIENT.tiles()) {
            long[] bits = t.bits();
            for (int i = 0; i < Explored.TILE * Explored.TILE; i++) {
                if ((bits[i >> 6] & (1L << (i & 63))) == 0) continue;
                int bx = (((t.tx() << Explored.TILE_SHIFT) + (i & (Explored.TILE - 1))) << 4) + 8;
                int bz = (((t.tz() << Explored.TILE_SHIFT) + (i >> Explored.TILE_SHIFT)) << 4) + 8;
                int zone = view.zone(bx, bz);
                if (zone == 0) continue;
                long[] acc = sums.computeIfAbsent(zone, k -> new long[3]);
                acc[0] += bx;
                acc[1] += bz;
                acc[2]++;
            }
        }
        // a zone's name sits on its own known land: the known cell nearest the middle of it (a ring's middle, the
        // Wallring's, is the city, not the ring)
        it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<double[]> anchor = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
        for (Explored.TileBits t : Discovery.CLIENT.tiles()) {
            long[] bits = t.bits();
            for (int i = 0; i < Explored.TILE * Explored.TILE; i++) {
                if ((bits[i >> 6] & (1L << (i & 63))) == 0) continue;
                int bx = (((t.tx() << Explored.TILE_SHIFT) + (i & (Explored.TILE - 1))) << 4) + 8;
                int bz = (((t.tz() << Explored.TILE_SHIFT) + (i >> Explored.TILE_SHIFT)) << 4) + 8;
                int zone = view.zone(bx, bz);
                long[] acc = zone == 0 ? null : sums.get(zone);
                if (acc == null) continue;
                double mx = acc[0] / (double) acc[2], mz = acc[1] / (double) acc[2], d = (bx - mx) * (bx - mx) + (bz - mz) * (bz - mz);
                double[] best = anchor.get(zone);
                if (best == null) anchor.put(zone, new double[]{bx, bz, d});
                else if (d < best[2]) {
                    best[0] = bx;
                    best[1] = bz;
                    best[2] = d;
                }
            }
        }
        List<ZoneLabel> out = new java.util.ArrayList<>();
        for (var e : sums.int2ObjectEntrySet()) {
            int zone = e.getIntKey();
            long[] a = e.getValue();
            double[] at = anchor.get(zone);
            String name, sub;
            if (zone >= ZoneMap.DISTRICT) {
                int d = Math.min(ZoneMap.DISTRICT_NAME.length - 1, zone - ZoneMap.DISTRICT);
                name = ZoneMap.DISTRICT_NAME[d];
                sub = "Carillon";
            } else {
                if (zone - 1 >= regions.size()) continue;
                var r = regions.get(zone - 1);
                name = r.name();
                sub = r.low() == r.high() ? "Lv " + r.low() : "Lv " + r.low() + "–" + r.high();
            }
            out.add(new ZoneLabel(zone, name, sub, view.colour(zone), at[0], at[1], (int) a[2]));
        }
        out.sort((p, q) -> Integer.compare(q.cells(), p.cells()));
        labels = List.copyOf(out);
        return labels;
    }

    /** The zones as the mesh worker should see them now. */
    ZoneMap.View zoneView() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) zones.setMyLevel(dev.awaken.progression.Progression.profile(mc.player).level());
        return zones.view(regions);
    }

    /** H: colour the land by its rank, or by how a creature there measures against you. */
    boolean toggleDanger() {
        boolean me = zones.toggleForMe();
        mesh.changedAll();
        return me;
    }

    void onTrail(AtlasNet.Trail m) {
        trail = m.points().stream().mapToInt(Integer::intValue).toArray();
        if (m.play()) revealPending = true;
    }

    /** 0..1 while the thread draws itself (2.4 s from the reveal), 1 after. */
    float trailProgress() {
        if (trailStart == 0) return 1f;
        return Math.min(1f, (System.nanoTime() - trailStart) / 2.4e9f);
    }

    /** The thread glows while it draws and for a while after, then settles to a faint line. */
    float trailGlow() {
        if (trailStart == 0) return 0.35f;
        float t = (System.nanoTime() - trailStart) / 1e9f;
        return t < 6f ? 1f : Math.max(0.35f, 1f - (t - 6f) / 4f * 0.65f);
    }

    // ---- the world ------------------------------------------------------------------------------------------------------

    void onChunkLoad(LevelChunk chunk) {
        sameLevel();
        if (!Discovery.mapped(dim)) return;
        if (roofed) { // reading under a roof costs more: a few chunks a tick (roofedTick)
            roofQueue.add(chunk.getPos().pack());
            return;
        }
        store.sample(chunk, false);
        mesh.changed(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(), 16);
    }

    /** Samples queued roofed chunks that are still loaded, up to about a millisecond a tick. */
    private void roofedTick(ClientLevel l) {
        long t0 = System.nanoTime();
        while (!roofQueue.isEmpty() && System.nanoTime() - t0 < 1_000_000L) {
            long k = roofQueue.removeLong(0);
            int cx = net.minecraft.world.level.ChunkPos.getX(k), cz = net.minecraft.world.level.ChunkPos.getZ(k);
            if (l.getChunkSource().getChunk(cx, cz, false) instanceof LevelChunk c) {
                store.sample(c, true);
                mesh.changed(cx << 4, cz << 4, 16);
            }
        }
    }

    /** The chunks round the player (this many each way) are read again, a few a tick. */
    static final int REREAD_R = 4;
    private int reread;

    /**
     * (SPEC §16.8) A chunk is sampled when it arrives; what changes in it later (what you build at your Hearth, a tree
     * you fell) would never reach the map. So the chunks round the player are read again in turn, two a tick (a full
     * round every two seconds; one every other tick under a roof, where a read costs more), and only a chunk whose
     * columns really changed rebuilds its slices.
     */
    private void reread(ClientLevel l, net.minecraft.world.entity.player.Player p) {
        int side = REREAD_R * 2 + 1, n = side * side, per = roofed ? (ticks & 1) : 2;
        for (int k = 0; k < per; k++) {
            reread = (reread + 1) % n;
            int cx = (p.getBlockX() >> 4) + reread % side - REREAD_R, cz = (p.getBlockZ() >> 4) + reread / side - REREAD_R;
            if (l.getChunkSource().getChunk(cx, cz, false) instanceof LevelChunk c && store.sample(c, roofed))
                mesh.changed(cx << 4, cz << 4, 16);
        }
    }

    void tick(Minecraft mc, boolean open) {
        if (mc.level == null || mc.player == null) return;
        sameLevel();
        ripple(System.nanoTime());
        if (roofed && !roofQueue.isEmpty()) roofedTick(mc.level);
        if (Discovery.mapped(dim)) reread(mc.level, mc.player);
        ColumnStore.Tile t;
        int adopted = 0;
        while ((t = cache.loaded.poll()) != null && adopted++ < 4) {
            store.adopt(t);
            mesh.changed(t.tx << ColumnStore.TILE_SHIFT, t.tz << ColumnStore.TILE_SHIFT, ColumnStore.TILE);
        }
        if (++ticks % SAVE_TICKS == 0) cache.save(store.tiles.values());
        if (ticks % 200 == 0 && !revealedAt.isEmpty()) {
            long now = System.nanoTime();
            revealedAt.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > 3_000_000_000L);
        }
        // keep the land round the player built while the table is shut, so opening it is the warm 4–10 ms
        mesh.zones = zoneView();
        if (!open) mesh.update(mc.player.getX(), mc.player.getZ(), 0, Discovery.CLIENT, System.nanoTime());
    }

    /** A new world or dimension starts a new Atlas, from that world's own cache. */
    private void sameLevel() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == level) return;
        if (level != null) cache.save(store.tiles.values());
        level = l;
        store.clear();
        mesh.clear();
        revealedAt.clear();
        if (l == null) return;
        dim = l.dimension().identifier().toString();
        roofed = l.dimensionType().hasCeiling();
        roofQueue.clear();
        AtlasMesh.BASE_Y = roofed ? 24 : 44;
        cache.open(dim);
        if (mc.player != null) {
            int tx = mc.player.getBlockX() >> ColumnStore.TILE_SHIFT, tz = mc.player.getBlockZ() >> ColumnStore.TILE_SHIFT;
            cache.loadAround(tx, tz, 3, java.util.Set.of());
        }
    }

    /** More of the disk cache round a point (the table panned far). */
    void want(double x, double z) {
        int tx = (int) Math.floor(x) >> ColumnStore.TILE_SHIFT, tz = (int) Math.floor(z) >> ColumnStore.TILE_SHIFT;
        LongOpenHashSet have = new LongOpenHashSet(store.tiles.keySet());
        cache.loadAround(tx, tz, 2, have);
    }

    void leave() {
        pending.clear();
        cache.save(store.tiles.values());
        TileCache.drain();
        store.clear();
        mesh.clear();
        revealedAt.clear();
        Discovery.CLIENT.clear();
        Discovery.clientDim = "";
        trail = new int[0];
        trailStart = 0;
        regions = java.util.List.of();
        pins = java.util.List.of();
        homes = java.util.List.of();
        zones.clear();
        revealPending = false;
        level = null;
    }

    /** "Clear Atlas cache": the disk cache and the sampled columns go; what you explored stays (it's the server's). */
    void clearCache() {
        TileCache.clearAll();
        store.clear();
        mesh.clear();
    }

    String dimension() {
        return dim;
    }

    static float night(ClientLevel level) {
        if (level.dimensionType().hasCeiling()) return 0f; // no sky under a roof
        long t = Math.floorMod(level.getDefaultClockTime(), 24000L);
        if (t < 12000) return 0f;
        if (t < 13800) return (t - 12000) / 1800f;
        if (t < 22200) return 1f;
        return 1f - (t - 22200) / 1800f;
    }

    String memory() {
        return String.format(Locale.ROOT, "columns=%d tiles (%.1f MB) explored=%d cells mesh=%d/%d/%d/%d/%d slices built, %d quads, %.1f MB GPU",
                store.tiles.size(), store.bytes() / 1048576.0, Discovery.CLIENT.count(), mesh.built(0), mesh.built(1), mesh.built(2), mesh.built(3), mesh.built(4),
                mesh.totalQuads, mesh.totalBytes / 1048576.0);
    }

    static double pct(long[] ring, int n, double q) {
        if (n == 0) return 0;
        long[] c = Arrays.copyOf(ring, n);
        Arrays.sort(c);
        return c[Math.min(n - 1, (int) (n * q))];
    }

    String costs() {
        int n = (int) Math.min(store.samples, store.sampleNanos.length), b = (int) Math.min(mesh.builds, mesh.buildNanos.length);
        return String.format(Locale.ROOT, "sampler p50=%.1fus p99=%.1fus (%d chunks) | slice builds (worker) p50=%.2fms p99=%.2fms (%d)",
                pct(store.sampleNanos, n, 0.5) / 1e3, pct(store.sampleNanos, n, 0.99) / 1e3, store.samples,
                pct(mesh.buildNanos, b, 0.5) / 1e6, pct(mesh.buildNanos, b, 0.99) / 1e6, mesh.builds);
    }

    /** Per-open numbers: prepare() CPU time and the open time. */
    static final class Stats {
        final long[] cpu = new long[8192];
        int frames, redraws, slicesDrawn, puffs;
        long openStart;
        double lastOpenMs;

        final long[] extract = new long[8192];
        int extracts;

        void reset() {
            frames = redraws = extracts = 0;
        }

        /** The screen's own CPU time a frame (layout, labels, legend), outside the picture's pass. */
        void extract(long nanos) {
            extract[extracts & 8191] = nanos;
            extracts++;
        }

        void frame(long nanos, boolean redraw) {
            cpu[frames & 8191] = nanos;
            frames++;
            if (redraw) redraws++;
            if (openStart != 0) {
                lastOpenMs = (System.nanoTime() - openStart) / 1e6;
                Awaken.LOG.info(String.format(Locale.ROOT, "[atlas] open: first frame %.1f ms after the key (%s)", lastOpenMs, AtlasState.get().memory()));
                openStart = 0;
            }
        }

        String summary() {
            int n = Math.min(frames, cpu.length);
            int x = Math.min(extracts, extract.length);
            return String.format(Locale.ROOT, "frames=%d redraws=%d prepare_cpu p50=%.3fms p99=%.3fms | screen_cpu p50=%.3fms p99=%.3fms | slices drawn=%d puffs=%d | open %.1f ms",
                    frames, redraws, pct(cpu, n, 0.5) / 1e6, pct(cpu, n, 0.99) / 1e6, pct(extract, x, 0.5) / 1e6, pct(extract, x, 0.99) / 1e6,
                    slicesDrawn, puffs, lastOpenMs);
        }
    }

    static List<String> describe() {
        AtlasState a = get();
        return List.of(a.memory(), a.costs(), stats.summary());
    }
}
