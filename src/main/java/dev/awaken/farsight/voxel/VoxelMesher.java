package dev.awaken.farsight.voxel;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Turns a {@link VoxelSnapshot} into the quads Far Sight draws for it (SPEC §20, City Sight): level 0 block for block,
 * levels 1-3 in cells of 2, 4 and 8 blocks. One instance per worker thread (it reuses its arrays).
 *
 * <p>Level 0: every face of a cube (FULL or SOFT) that no opaque cube hides, merged into the largest rectangles of the
 * same look, tint and light (greedy meshing), so a wall of one brick is a few quads; slabs, beds and the like as their
 * box, carpets and snow layers as their top, water as its surface (at 14/16) and the sides it shows. Each face carries
 * the light of the air in front of it, as the game lights it. Coarser levels: a cell is solid where at least half its
 * blocks are (so thin things thin out with distance, never swell), wearing the look most of its blocks have, and on
 * top the look most of its columns' highest blocks have (roofs from above).
 *
 * <p>A quad is four ints: {@code x16 | z16 << 9 | dir << 18 | eu << 21 | lod << 30}, {@code y16 | ev << 14},
 * {@code look | slot << 16}, {@code tint | sky << 24 | block << 28}: its least corner in sixteenths of a block (x, z in
 * the chunk, y from the level's floor), its face (down, up, north, south, west, east), its extent along the face's
 * u and v axes (x and z for up and down, x and y for north and south, z and y for west and east) in sixteenths, its
 * look and chunk slot, its tint (0xRRGGBB) and light.
 */
public final class VoxelMesher {
    /** How a block's faces look: the client's models and colours (a test's stand-in). */
    public interface Looks {
        /**
         * The face of block state {@code id} toward {@code dir} (down, up, north, south, west, east): its look (0..65535)
         * and, from bit 16, its tint index + 1 (0 untinted); -1 for none.
         */
        int face(int id, int dir);

        /** The tint (0xRRGGBB) of a face of state {@code id} with tint index {@code tintIndex} in this biome. */
        int tint(int id, int tintIndex, @Nullable Holder<Biome> biome);
    }

    public static final int LEVELS = 4, INTS = 4;
    private static final int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};
    private static final long PRESENT = 1L << 63;

    private final Looks looks;
    private final int waterId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.WATER.defaultBlockState());
    private int[] ids = new int[0];
    private byte[] kinds = new byte[0];
    private long[] mask = new long[0];
    private final int[][] out = new int[LEVELS][];
    private final int[] size = new int[LEVELS];
    private VoxelSnapshot snap;
    private int height, yBase, slot, lod;
    private int minY16, maxY16;
    // the coarse grids: solid look per cell (0 empty, -1 water, else state id + 1) and the up faces' look
    private int[] coarse = new int[0], coarseTop = new int[0];

    public VoxelMesher(Looks looks) {
        this.looks = looks;
        for (int l = 0; l < LEVELS; l++) out[l] = new int[4096];
    }

    /** The quads of one chunk, every level. {@code slot} is written into each quad. */
    public VoxelMesh mesh(VoxelSnapshot s, int slot) {
        snap = s;
        this.slot = slot;
        height = s.height();
        yBase = (s.minY() - s.levelMinY()) * 16;
        int n = 256 * height;
        if (ids.length < n) {
            ids = new int[n];
            kinds = new byte[n];
        }
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int i = index(x, y, z), id = s.state(x, y, z);
                    ids[i] = id;
                    kinds[i] = id == 0 ? VoxelKinds.AIR : VoxelKinds.kind(id);
                }
            }
        }
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int[][] quads = new int[LEVELS][];
        for (int l = 0; l < LEVELS; l++) {
            lod = l;
            size[l] = 0;
            minY16 = Integer.MAX_VALUE;
            maxY16 = Integer.MIN_VALUE;
            if (l == 0) level0();
            else coarse(1 << l);
            quads[l] = Arrays.copyOf(out[l], size[l]);
            if (size[l] > 0) {
                lo = Math.min(lo, minY16);
                hi = Math.max(hi, maxY16);
            }
        }
        snap = null;
        int levelMin = s.levelMinY();
        return new VoxelMesh(s.chunkX(), s.chunkZ(), slot, lo == Integer.MAX_VALUE ? 0 : levelMin + Math.floorDiv(lo, 16),
                hi == Integer.MIN_VALUE ? 0 : levelMin + Math.floorDiv(hi + 15, 16), quads);
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private byte kindAt(int x, int y, int z) {
        if (y < 0) return snap.fromSection() * 16 <= snap.levelMinY() ? VoxelKinds.FULL : VoxelKinds.AIR;
        if (y >= height || x < 0 || x > 15 || z < 0 || z > 15) return VoxelKinds.AIR;
        return kinds[index(x, y, z)];
    }

    // ---- level 0 ----------------------------------------------------------------------------------------------------

    private void level0() {
        // the cubes' faces, merged; then boxes, sheets and water's sides one by one
        for (int dir = 0; dir < 6; dir++) greedy(dir);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    byte k = kinds[index(x, y, z)];
                    if (k == VoxelKinds.BOX || k == VoxelKinds.FLAT) box(x, y, z, k);
                    else if (k == VoxelKinds.WATER) waterSides(x, y, z);
                }
            }
        }
    }

    /** The key of the cube or water face at (x, y, z) toward dir, or 0 if none shows. */
    private long faceKey(int x, int y, int z, int dir) {
        int i = index(x, y, z);
        byte k = kinds[i];
        int nx = x + DX[dir], ny = y + DY[dir], nz = z + DZ[dir];
        byte nk = kindAt(nx, ny, nz);
        int look, offset;
        int id = ids[i];
        if (k == VoxelKinds.FULL || k == VoxelKinds.SOFT) {
            if (nk == VoxelKinds.FULL) return 0;
            if (k == VoxelKinds.SOFT && nk == VoxelKinds.SOFT && ids[index(nx, ny, nz)] == id) return 0;
            look = looks.face(id, dir);
            offset = (dir & 1) == 1 ? 16 : 0;
        } else if (k == VoxelKinds.WATER) {
            // the surface only: under another water block, or a block above, nothing shows from afar
            if (dir != 1 || nk == VoxelKinds.WATER || nk == VoxelKinds.FULL || nk == VoxelKinds.SOFT) return 0;
            id = waterId;
            look = looks.face(waterId, 1);
            offset = 14;
        } else {
            return 0;
        }
        if (look < 0) return 0;
        int tint = 0xFFFFFF;
        if ((look >>> 16) != 0) tint = looks.tint(id, (look >>> 16) - 1, snap.biome(x, y, z)) & 0xFFFFFF;
        int sky = snap.skyLight(nx, ny, nz), block = snap.blockLight(nx, ny, nz);
        return PRESENT | (long) offset << 52 | (long) block << 48 | (long) sky << 44 | (long) tint << 16 | (look & 0xFFFF);
    }

    private void greedy(int dir) {
        boolean y = dir < 2;
        int slices = y ? height : 16, uN = 16, vN = y ? 16 : height;
        if (mask.length < uN * vN) mask = new long[uN * vN];
        for (int s = 0; s < slices; s++) {
            boolean any = false;
            for (int v = 0; v < vN; v++) {
                for (int u = 0; u < uN; u++) {
                    long key;
                    if (dir < 2) key = faceKey(u, s, v, dir);
                    else if (dir < 4) key = faceKey(u, v, s, dir);
                    else key = faceKey(s, v, u, dir);
                    mask[v * uN + u] = key;
                    any |= key != 0;
                }
            }
            if (!any) continue;
            for (int v = 0; v < vN; v++) {
                for (int u = 0; u < uN; ) {
                    long key = mask[v * uN + u];
                    if (key == 0) {
                        u++;
                        continue;
                    }
                    int w = 1;
                    while (u + w < uN && mask[v * uN + u + w] == key) w++;
                    int h = 1;
                    grow:
                    while (v + h < vN) {
                        for (int k = 0; k < w; k++) if (mask[(v + h) * uN + u + k] != key) break grow;
                        h++;
                    }
                    for (int dv = 0; dv < h; dv++) Arrays.fill(mask, (v + dv) * uN + u, (v + dv) * uN + u + w, 0L);
                    int offset = (int) (key >>> 52 & 31);
                    int plane = s * 16 + offset;
                    int look = (int) (key & 0xFFFF), tint = (int) (key >>> 16 & 0xFFFFFF), sky = (int) (key >>> 44 & 15), block = (int) (key >>> 48 & 15);
                    if (dir < 2) quad(u * 16, plane, v * 16, dir, w * 16, h * 16, look, tint, sky, block);
                    else if (dir < 4) quad(u * 16, v * 16, plane, dir, w * 16, h * 16, look, tint, sky, block);
                    else quad(plane, v * 16, u * 16, dir, w * 16, h * 16, look, tint, sky, block);
                    u += w;
                }
            }
        }
    }

    /** A box's (slab, bed) or sheet's (carpet) faces, each its own quad, lit by the air in front where it can be. */
    private void box(int x, int y, int z, byte k) {
        int id = ids[index(x, y, z)], b = VoxelKinds.box(id);
        int x0 = VoxelKinds.x0(b), y0 = VoxelKinds.y0(b), z0 = VoxelKinds.z0(b), x1 = VoxelKinds.x1(b), y1 = VoxelKinds.y1(b), z1 = VoxelKinds.z1(b);
        int bx = x * 16, by = y * 16, bz = z * 16;
        for (int dir = 0; dir < 6; dir++) {
            if (k == VoxelKinds.FLAT && dir != 1) continue;
            // a face on the block's edge hides behind an opaque neighbour
            boolean edge = switch (dir) {
                case 0 -> y0 == 0;
                case 1 -> y1 == 16;
                case 2 -> z0 == 0;
                case 3 -> z1 == 16;
                case 4 -> x0 == 0;
                default -> x1 == 16;
            };
            int nx = x + DX[dir], ny = y + DY[dir], nz = z + DZ[dir];
            if (edge && kindAt(nx, ny, nz) == VoxelKinds.FULL) continue;
            int look = looks.face(id, dir);
            if (look < 0) continue;
            int tint = (look >>> 16) != 0 ? looks.tint(id, (look >>> 16) - 1, snap.biome(x, y, z)) & 0xFFFFFF : 0xFFFFFF;
            int lx = edge ? nx : x, ly = edge ? ny : y, lz = edge ? nz : z;
            int sky = Math.max(snap.skyLight(lx, ly, lz), edge ? 0 : snap.skyLight(x, y + 1, z)), block = snap.blockLight(lx, ly, lz);
            switch (dir) {
                case 0 -> quad(bx + x0, by + y0, bz + z0, 0, x1 - x0, z1 - z0, look, tint, sky, block);
                case 1 -> quad(bx + x0, by + y1, bz + z0, 1, x1 - x0, z1 - z0, look, tint, sky, block);
                case 2 -> quad(bx + x0, by + y0, bz + z0, 2, x1 - x0, y1 - y0, look, tint, sky, block);
                case 3 -> quad(bx + x0, by + y0, bz + z1, 3, x1 - x0, y1 - y0, look, tint, sky, block);
                case 4 -> quad(bx + x0, by + y0, bz + z0, 4, z1 - z0, y1 - y0, look, tint, sky, block);
                default -> quad(bx + x1, by + y0, bz + z0, 5, z1 - z0, y1 - y0, look, tint, sky, block);
            }
        }
    }

    /** Water's sides where it meets open air (a fall, a fountain's rim seen from outside), up to its surface. */
    private void waterSides(int x, int y, int z) {
        int look = looks.face(waterId, 2);
        if (look < 0) return;
        boolean under = kindAt(x, y + 1, z) == VoxelKinds.WATER;
        int top = under ? 16 : 14;
        int tint = (look >>> 16) != 0 ? looks.tint(waterId, (look >>> 16) - 1, snap.biome(x, y, z)) & 0xFFFFFF : 0xFFFFFF;
        for (int dir = 2; dir < 6; dir++) {
            int nx = x + DX[dir], nz = z + DZ[dir];
            byte nk = kindAt(nx, y, nz);
            if (nk != VoxelKinds.AIR && nk != VoxelKinds.SKIP && nk != VoxelKinds.FLAT) continue;
            // the chunk's edge: its neighbour draws its own side if it has one
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            int sky = snap.skyLight(nx, y, nz), block = snap.blockLight(nx, y, nz);
            int bx = x * 16, by = y * 16, bz = z * 16;
            switch (dir) {
                case 2 -> quad(bx, by, bz, 2, 16, top, look & 0xFFFF, tint, sky, block);
                case 3 -> quad(bx, by, bz + 16, 3, 16, top, look & 0xFFFF, tint, sky, block);
                case 4 -> quad(bx, by, bz, 4, 16, top, look & 0xFFFF, tint, sky, block);
                default -> quad(bx + 16, by, bz, 5, 16, top, look & 0xFFFF, tint, sky, block);
            }
        }
    }

    private void quad(int x16, int y16, int z16, int dir, int eu, int ev, int look, int tint, int sky, int block) {
        int l = lod;
        if (size[l] + INTS > out[l].length) out[l] = Arrays.copyOf(out[l], out[l].length * 2);
        int[] o = out[l];
        int at = size[l];
        int y = yBase + y16;
        o[at] = x16 | z16 << 9 | dir << 18 | eu << 21 | l << 30;
        o[at + 1] = y | ev << 14;
        o[at + 2] = (look & 0xFFFF) | slot << 16;
        o[at + 3] = (tint & 0xFFFFFF) | sky << 24 | block << 28;
        size[l] = at + INTS;
        minY16 = Math.min(minY16, y);
        int top = (dir < 2 ? y : y + ev);
        maxY16 = Math.max(maxY16, top);
    }

    // ---- levels 1-3 -------------------------------------------------------------------------------------------------

    private int cellIndex(int cx, int cy, int cz, int n) {
        return (cy * n + cz) * n + cx;
    }

    /** Coarse cells of c blocks: solid by majority, then meshed as cubes. */
    private void coarse(int c) {
        int n = 16 / c, ny = height / c, cells = n * n * ny;
        if (coarse.length < cells) {
            coarse = new int[cells];
            coarseTop = new int[cells];
        }
        int half = c * c * c / 2;
        int[] seenId = new int[16], seenN = new int[16];
        for (int cy = 0; cy < ny; cy++) {
            for (int cz = 0; cz < n; cz++) {
                for (int cx = 0; cx < n; cx++) {
                    int solid = 0, water = 0, distinct = 0;
                    for (int y = cy * c; y < cy * c + c; y++) {
                        for (int z = cz * c; z < cz * c + c; z++) {
                            for (int x = cx * c; x < cx * c + c; x++) {
                                int i = index(x, y, z);
                                byte k = kinds[i];
                                if (k == VoxelKinds.WATER) water++;
                                else if (k == VoxelKinds.FULL || k == VoxelKinds.SOFT || k == VoxelKinds.BOX) {
                                    solid++;
                                    distinct = count(seenId, seenN, distinct, ids[i]);
                                }
                            }
                        }
                    }
                    int ci = cellIndex(cx, cy, cz, n);
                    if (solid >= half && solid > 0) {
                        coarse[ci] = top(seenId, seenN, distinct) + 1;
                        // the top: the look most of its columns' highest solid blocks have
                        int td = 0;
                        for (int z = cz * c; z < cz * c + c; z++) {
                            for (int x = cx * c; x < cx * c + c; x++) {
                                for (int y = cy * c + c - 1; y >= cy * c; y--) {
                                    byte k = kinds[index(x, y, z)];
                                    if (k == VoxelKinds.FULL || k == VoxelKinds.SOFT || k == VoxelKinds.BOX) {
                                        td = count(seenId, seenN, td, ids[index(x, y, z)]);
                                        break;
                                    }
                                }
                            }
                        }
                        coarseTop[ci] = td == 0 ? coarse[ci] : top(seenId, seenN, td) + 1;
                    } else if (solid + water >= half && water > 0) {
                        coarse[ci] = -1;
                        coarseTop[ci] = -1;
                    } else {
                        coarse[ci] = 0;
                        coarseTop[ci] = 0;
                    }
                }
            }
        }
        for (int dir = 0; dir < 6; dir++) coarseGreedy(dir, c, n, ny);
    }

    private static int count(int[] ids, int[] counts, int distinct, int id) {
        for (int k = 0; k < distinct; k++) {
            if (ids[k] == id) {
                counts[k]++;
                return distinct;
            }
        }
        if (distinct < ids.length) {
            ids[distinct] = id;
            counts[distinct] = 1;
            return distinct + 1;
        }
        return distinct;
    }

    private static int top(int[] ids, int[] counts, int distinct) {
        int best = 0;
        for (int k = 1; k < distinct; k++) if (counts[k] > counts[best]) best = k;
        return ids[best];
    }

    private int coarseAt(int cx, int cy, int cz, int n, int ny) {
        if (cy < 0) return snap.fromSection() * 16 <= snap.levelMinY() ? 1 : 0;
        if (cy >= ny || cx < 0 || cx >= n || cz < 0 || cz >= n) return 0;
        return coarse[cellIndex(cx, cy, cz, n)];
    }

    private long coarseKey(int cx, int cy, int cz, int dir, int c, int n, int ny) {
        int ci = cellIndex(cx, cy, cz, n), cell = coarse[ci];
        if (cell == 0) return 0;
        int ncx = cx + DX[dir], ncy = cy + DY[dir], ncz = cz + DZ[dir];
        int nb = coarseAt(ncx, ncy, ncz, n, ny);
        int id, look, offset;
        if (cell > 0) {
            if (nb > 0) return 0;
            id = (dir == 1 ? coarseTop[ci] : cell) - 1;
            look = looks.face(id, dir);
            offset = (dir & 1) == 1 ? c * 16 : 0;
        } else {
            if (dir != 1 || nb != 0) return 0;
            id = waterId;
            look = looks.face(waterId, 1);
            offset = c * 16 - 2;
        }
        if (look < 0) return 0;
        // the light of the air at the middle of the face, a block out
        int mx = cx * c + c / 2, my = cy * c + c / 2, mz = cz * c + c / 2;
        int lx = mx + DX[dir] * (c / 2 + 1), ly = dir == 1 ? cy * c + c : dir == 0 ? cy * c - 1 : my, lz = mz + DZ[dir] * (c / 2 + 1);
        if (dir >= 2) {
            lx = dir == 4 ? cx * c - 1 : dir == 5 ? cx * c + c : mx;
            lz = dir == 2 ? cz * c - 1 : dir == 3 ? cz * c + c : mz;
        }
        int sky = snap.skyLight(lx, ly, lz), block = snap.blockLight(lx, ly, lz);
        int tint = (look >>> 16) != 0 ? looks.tint(id, (look >>> 16) - 1, snap.biome(mx, my, mz)) & 0xFFFFFF : 0xFFFFFF;
        return PRESENT | (long) (offset & 255) << 52 | (long) block << 48 | (long) sky << 44 | (long) tint << 16 | (look & 0xFFFF);
    }

    private void coarseGreedy(int dir, int c, int n, int ny) {
        boolean y = dir < 2;
        int slices = y ? ny : n, uN = n, vN = y ? n : ny;
        if (mask.length < uN * vN) mask = new long[uN * vN];
        for (int s = 0; s < slices; s++) {
            boolean any = false;
            for (int v = 0; v < vN; v++) {
                for (int u = 0; u < uN; u++) {
                    long key;
                    if (dir < 2) key = coarseKey(u, s, v, dir, c, n, ny);
                    else if (dir < 4) key = coarseKey(u, v, s, dir, c, n, ny);
                    else key = coarseKey(s, v, u, dir, c, n, ny);
                    mask[v * uN + u] = key;
                    any |= key != 0;
                }
            }
            if (!any) continue;
            int cs = c * 16;
            for (int v = 0; v < vN; v++) {
                for (int u = 0; u < uN; ) {
                    long key = mask[v * uN + u];
                    if (key == 0) {
                        u++;
                        continue;
                    }
                    int w = 1;
                    while (u + w < uN && mask[v * uN + u + w] == key) w++;
                    int h = 1;
                    grow:
                    while (v + h < vN) {
                        for (int k = 0; k < w; k++) if (mask[(v + h) * uN + u + k] != key) break grow;
                        h++;
                    }
                    for (int dv = 0; dv < h; dv++) Arrays.fill(mask, (v + dv) * uN + u, (v + dv) * uN + u + w, 0L);
                    int offset = (int) (key >>> 52 & 255);
                    int plane = s * cs + offset;
                    int look = (int) (key & 0xFFFF), tint = (int) (key >>> 16 & 0xFFFFFF), sky = (int) (key >>> 44 & 15), block = (int) (key >>> 48 & 15);
                    if (dir < 2) quad(u * cs, plane, v * cs, dir, w * cs, h * cs, look, tint, sky, block);
                    else if (dir < 4) quad(u * cs, v * cs, plane, dir, w * cs, h * cs, look, tint, sky, block);
                    else quad(plane, v * cs, u * cs, dir, w * cs, h * cs, look, tint, sky, block);
                    u += w;
                }
            }
        }
    }
}
