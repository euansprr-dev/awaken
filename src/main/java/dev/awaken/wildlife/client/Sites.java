package dev.awaken.wildlife.client;

import dev.awaken.core.audio.world.Families;
import dev.awaken.wildlife.Density;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;

/**
 * What each chunk near the player offers the wild (SPEC "Wild life"), read once from the client's own chunks and kept:
 * where leaves top the canopy and fences stand (perches), trunks (woodpeckers, squirrels), open ground, flowers, still
 * water and its shore, and lights (moths). One chunk is read a tick, all 256 columns from the three heightmaps the
 * client keeps, so a forest is known within a few seconds of arriving; a chunk is read again after two minutes (trees
 * fall, ponds are dug). The spots kept per kind are a fixed sample (the chunk's own draw), so animals return to the same
 * branches.
 */
final class Sites {
    private Sites() {}

    static final int PERCHES = 12, TRUNKS = 6, GROUND = 8, FLOWERS = 12, WATER = 12, SHORE = 8, LIGHTS = 4;
    /** Perch kinds: a leaf top on the canopy, its edge, a fence or wall post, a bare rock. */
    static final byte LEAF = 0, EDGE = 1, FENCE = 2, ROCK = 3;
    /** Chunks read within this many chunks of the player, dropped past {@link #FORGET}; re-read after {@link #STALE} ticks. */
    static final int RADIUS = 8, FORGET = 11;
    static final long STALE = 2400;

    /** One chunk's offer. Positions are BlockPos longs: a perch is where a bird stands, a trunk the log above the ground. */
    static final class Site {
        final long key;
        final int cx, cz;
        final Density.Habitat h = new Density.Habitat();
        final long[] perch = new long[PERCHES], trunk = new long[TRUNKS], ground = new long[GROUND], flower = new long[FLOWERS],
                water = new long[WATER], shore = new long[SHORE], light = new long[LIGHTS];
        final byte[] perchKind = new byte[PERCHES], shoreDir = new byte[SHORE];
        /** How high a perch stands above the ground under it (blocks; 99 when the column is a trunk's). */
        final byte[] perchLift = new byte[PERCHES];
        /** A trunk's height of clear bark above its foot (blocks). */
        final int[] trunkHeight = new int[TRUNKS];
        int nPerch, nTrunk, nGround, nFlower, nWater, nShore, nLight;
        float groundY;
        long read;

        Site(long key) {
            this.key = key;
            this.cx = ChunkPos.getX(key);
            this.cz = ChunkPos.getZ(key);
        }

        double centreX() {
            return cx * 16 + 8;
        }

        double centreZ() {
            return cz * 16 + 8;
        }
    }

    private static final Long2ObjectOpenHashMap<Site> SITES = new Long2ObjectOpenHashMap<>();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();
    private static final byte[] KIND = new byte[256];
    private static final int[] TOP = new int[256];
    private static final int OPEN = 0, LAND = 1, WATERC = 2;
    private static int ring;

    static @Nullable Site get(long key) {
        return SITES.get(key);
    }

    static Iterable<Site> all() {
        return SITES.values();
    }

    static int size() {
        return SITES.size();
    }

    static void clear() {
        SITES.clear();
    }

    /**
     * Once a tick: read the nearest chunk not yet read (or gone stale), and forget far ones. A spiral out from the player
     * so what's near is known first.
     */
    static void tick(ClientLevel level, double px, double pz, long now) {
        int pcx = Mth_floor(px) >> 4, pcz = Mth_floor(pz) >> 4;
        if (now % 40 == 0) {
            for (Iterator<Site> it = SITES.values().iterator(); it.hasNext(); ) {
                Site s = it.next();
                if (Math.abs(s.cx - pcx) > FORGET || Math.abs(s.cz - pcz) > FORGET) it.remove();
            }
        }
        // nearest unread first: rings out to RADIUS
        for (int r = 0; r <= RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    long key = ChunkPos.pack(pcx + dx, pcz + dz);
                    Site s = SITES.get(key);
                    if (s != null && now - s.read < STALE) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                    if (chunk == null || chunk.isEmpty()) continue;
                    if (s == null) {
                        s = new Site(key);
                        SITES.put(key, s);
                    }
                    read(level, chunk, s, now);
                    return;
                }
            }
        }
        ring++;
    }

    private static int Mth_floor(double v) {
        return (int) Math.floor(v);
    }

    /** Reads one chunk: every column's tops, then the shore (land beside water), then the habitat counts. */
    static void read(ClientLevel level, LevelChunk chunk, Site s, long now) {
        s.read = now;
        s.nPerch = s.nTrunk = s.nGround = s.nFlower = s.nWater = s.nShore = s.nLight = 0;
        int x0 = s.cx * 16, z0 = s.cz * 16;
        int perches = 0, trunks = 0, ground = 0, flowers = 0, water = 0, shore = 0, lights = 0, canopy = 0, land = 0;
        long seed = s.key * 0x9E3779B97F4A7C15L;
        double gy = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz * 16 + lx, x = x0 + lx, z = z0 + lz;
                int mb = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
                int ws = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                int nl = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                BlockState top = chunk.getBlockState(M.set(x, mb, z));
                TOP[i] = mb;
                FluidState fs = top.getFluidState();
                if (!fs.isEmpty()) {
                    KIND[i] = fs.is(FluidTags.WATER) ? (byte) WATERC : (byte) OPEN;
                    if (KIND[i] == WATERC && fs.isSource()) {
                        water++;
                        keep(s.water, water, M.set(x, mb, z).asLong(), seed + 1, null);
                    }
                    continue;
                }
                KIND[i] = LAND;
                land++;
                gy += mb + 1;
                boolean leaves = top.is(BlockTags.LEAVES);
                if (leaves) {
                    canopy++;
                    if (ws == mb) {
                        perches++;
                        // the canopy's edge (a neighbour well below) is where small birds sit and sing
                        boolean edge = lx > 0 && lx < 15 && lz > 0 && lz < 15
                                && (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx + 1, lz) < mb - 2
                                || chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx - 1, lz) < mb - 2
                                || chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz + 1) < mb - 2
                                || chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz - 1) < mb - 2);
                        int slot = keep(s.perch, perches + (edge ? 2 : 0), M.set(x, mb + 1, z).asLong(), seed + 2, null);
                        if (slot >= 0) {
                            s.perchKind[slot] = edge ? EDGE : LEAF;
                            boolean trunk = nl < mb && chunk.getBlockState(M.set(x, nl, z)).is(BlockTags.LOGS);
                            s.perchLift[slot] = (byte) (trunk ? 99 : Math.min(99, mb - nl));
                        }
                    }
                    // a trunk: the highest non-leaf block under the canopy is a log
                    if (nl < mb && nl > level.getMinY()) {
                        BlockState t = chunk.getBlockState(M.set(x, nl, z));
                        if (t.is(BlockTags.LOGS)) {
                            trunks++;
                            int foot = nl;
                            while (foot > nl - 30 && chunk.getBlockState(M.set(x, foot - 1, z)).is(BlockTags.LOGS)) foot--;
                            if (nl - foot >= 2) {
                                int slot = keep(s.trunk, trunks, BlockPos.asLong(x, foot, z), seed + 3, null);
                                if (slot >= 0) s.trunkHeight[slot] = nl - foot;
                            }
                        }
                    }
                    continue;
                }
                if (top.is(BlockTags.FENCES) || top.is(BlockTags.WALLS)) {
                    perches++;
                    int slot = keep(s.perch, perches, M.set(x, mb + 1, z).asLong(), seed + 2, null);
                    if (slot >= 0) {
                        s.perchKind[slot] = FENCE;
                        s.perchLift[slot] = 1;
                    }
                    continue;
                }
                if (top.is(BlockTags.DIRT) || top.is(BlockTags.SAND) || top.is(BlockTags.SNOW)) {
                    ground++;
                    keep(s.ground, ground, M.set(x, mb + 1, z).asLong(), seed + 4, null);
                } else if (top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(dev.awaken.ground.GroundModule.PERCH_ROCK)) { // boulders count (SPEC §31)
                    perches++;
                    int slot = keep(s.perch, perches, M.set(x, mb + 1, z).asLong(), seed + 2, null);
                    if (slot >= 0) {
                        s.perchKind[slot] = ROCK;
                        s.perchLift[slot] = 0;
                    }
                }
                if (ws > mb) {
                    BlockState plant = chunk.getBlockState(M.set(x, ws, z));
                    if (plant.is(BlockTags.FLOWERS)) {
                        flowers++;
                        keep(s.flower, flowers, M.asLong(), seed + 5, null);
                    }
                    if (plant.getLightEmission() >= 10) {
                        lights++;
                        keep(s.light, lights, M.asLong(), seed + 6, null);
                    }
                }
                if (top.getLightEmission() >= 10) {
                    lights++;
                    keep(s.light, lights, M.set(x, mb, z).asLong(), seed + 6, null);
                }
            }
        }
        // the shore: land beside water, facing it (a heron's stand, a frog's)
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz * 16 + lx;
                if (KIND[i] != LAND) continue;
                int dir = -1;
                if (lx < 15 && KIND[i + 1] == WATERC) dir = 0;
                else if (lz < 15 && KIND[i + 16] == WATERC) dir = 1;
                else if (lx > 0 && KIND[i - 1] == WATERC) dir = 2;
                else if (lz > 0 && KIND[i - 16] == WATERC) dir = 3;
                if (dir < 0) continue;
                shore++;
                int slot = keep(s.shore, shore, BlockPos.asLong(x0 + lx, TOP[i] + 1, z0 + lz), seed + 7, null);
                if (slot >= 0) s.shoreDir[slot] = (byte) dir;
            }
        }
        s.nPerch = Math.min(perches, PERCHES);
        s.nTrunk = Math.min(countKept(s.trunk, trunks), TRUNKS);
        s.nGround = Math.min(ground, GROUND);
        s.nFlower = Math.min(flowers, FLOWERS);
        s.nWater = Math.min(water, WATER);
        s.nShore = Math.min(shore, SHORE);
        s.nLight = Math.min(lights, LIGHTS);
        s.groundY = land > 0 ? (float) (gy / land) : level.getSeaLevel();
        Holder<Biome> biome = level.getBiome(M.set(x0 + 8, (int) s.groundY, z0 + 8));
        s.h.set(Families.of(biome), canopy / 256F, biome.value().getBaseTemperature(), perches, s.nTrunk, ground, flowers,
                water, shore, lights);
        String path = biome.unwrapKey().map(k -> k.identifier().getNamespace().equals("awaken") ? k.identifier().getPath() : "").orElse("");
        for (int v = 1; v < Density.VARIANTS.length; v++) if (Density.VARIANTS[v].equals(path)) s.h.variant = v;
    }

    /** Trunk columns kept (a column whose clear bark was too short was counted but not kept). */
    private static int countKept(long[] arr, int found) {
        int n = 0;
        for (int i = 0; i < Math.min(found, arr.length); i++) if (arr[i] != 0L) n++;
        return n;
    }

    interface Kept {
        void kept(int k, long v);
    }

    /**
     * A fixed sample: the n-th find goes into a free slot while there is one, else replaces a slot with chance
     * capacity / n (the chunk's own draw, so the same spots are kept every read). Returns the slot written, or -1.
     */
    private static int keep(long[] arr, int n, long value, long seed, @Nullable Kept k) {
        int slot;
        if (n <= arr.length) {
            slot = n - 1;
        } else {
            long h = (seed + n) * 0xD6E8FEB86659FD93L;
            h ^= h >>> 32;
            long r = Math.floorMod(h, (long) n);
            if (r >= arr.length) return -1;
            slot = (int) r;
        }
        arr[slot] = value;
        return slot;
    }
}
