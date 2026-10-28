package dev.awaken.gates.world;

import dev.awaken.gates.content.GatePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A world floor carved whole into memory, for its GameTests (SPEC §38.6, "How we'll know"): every chunk carved as the
 * generator would (timed), then walked on foot from the arrival (steps, jumps of one block, drops, ropes and water),
 * lit (block light spreading as the game spreads it, near enough), and its water checked to stay where it was put.
 * With {@code AWAKEN_WORLD_DUMP} set it also draws the floor: slices, sections and a view from the arrival.
 */
final class WorldCheck {
    final GatePlan plan;
    final FloorLayout f;
    final int x0, z0, yLo, h;
    /** Palette index per block (0 air), x-major: [(x * SIDE + z) * h + (y - yLo)]. */
    private final short[] cells;
    private final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Short> index = new IdentityHashMap<>();
    private byte[] light, reach;
    long carveNanosTotal, carveNanosWorst;
    int chunks;

    static final int SIDE = Plots.SIDE;

    WorldCheck(GatePlan plan, FloorLayout f) {
        this.plan = plan;
        this.f = f;
        this.x0 = f.originSlotX * 16;
        this.z0 = f.originSlotZ * 16;
        this.yLo = f.yLo;
        this.h = f.yHi - f.yLo + 1;
        this.cells = new short[SIDE * SIDE * h];
        palette.add(Blocks.AIR.defaultBlockState());
        index.put(Blocks.AIR.defaultBlockState(), (short) 0);
        for (int gx = 0; gx < SIDE / 16; gx++) {
            for (int gz = 0; gz < SIDE / 16; gz++) {
                final int bx = gx * 16, bz = gz * 16;
                long t = System.nanoTime();
                f.model.carve(plan, f, f.originSlotX + gx, f.originSlotZ + gz, (lx, y, lz, s) -> set(bx + lx, y, bz + lz, s));
                long dt = System.nanoTime() - t;
                carveNanosTotal += dt;
                carveNanosWorst = Math.max(carveNanosWorst, dt);
                chunks++;
            }
        }
    }

    private int i(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= SIDE || z >= SIDE || y < yLo || y >= yLo + h) return -1;
        return (x * SIDE + z) * h + (y - yLo);
    }

    void set(int x, int y, int z, BlockState s) {
        int i = i(x, y, z);
        if (i < 0) return;
        Short p = index.get(s);
        if (p == null) {
            p = (short) palette.size();
            palette.add(s);
            index.put(s, p);
        }
        cells[i] = p;
    }

    BlockState at(int x, int y, int z) {
        int i = i(x, y, z);
        return i < 0 ? palette.get(0) : palette.get(cells[i]);
    }

    // ---- what a block is to someone on foot -------------------------------------------------------------------------

    private final Map<BlockState, Byte> kinds = new IdentityHashMap<>();
    static final byte PASS = 1, STAND = 2, CLIMB = 4, WATER = 8;

    byte kind(BlockState s) {
        Byte k = kinds.get(s);
        if (k != null) return k;
        byte v = 0;
        boolean empty = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        if (empty) v |= PASS;
        else v |= STAND;
        if (s.is(BlockTags.CLIMBABLE)) v |= CLIMB;
        if (s.getBlock() == Blocks.WATER) v |= WATER;
        kinds.put(s, v);
        return v;
    }

    boolean pass(int x, int y, int z) {
        return (kind(at(x, y, z)) & PASS) != 0;
    }

    boolean stand(int x, int y, int z) {
        return (kind(at(x, y, z)) & STAND) != 0;
    }

    /** Feet at (x, y, z) can stay there: room for a body, and ground, a rope or water holding it. */
    boolean node(int x, int y, int z) {
        if (i(x, y, z) < 0 || !pass(x, y, z) || !pass(x, y + 1, z)) return false;
        byte k = kind(at(x, y, z));
        return stand(x, y - 1, z) || (k & (CLIMB | WATER)) != 0;
    }

    /** Where you land falling from (x, y, z): the first node below, or -1 after 48 blocks of air. */
    int land(int x, int y, int z) {
        for (int d = 0; d < 48 && y - d > yLo; d++) {
            int yy = y - d;
            if (!pass(x, yy, z) || !pass(x, yy + 1, z)) return -1;
            if (node(x, yy, z)) return yy;
        }
        return -1;
    }

    int[] parent;
    private int startIdx = -1;

    /** Everything you can stand on from the arrival: walks, one-block jumps, drops, climbing ropes and swimming. */
    void walk(int sx, int sy, int sz) {
        reach = new byte[cells.length];
        parent = new int[cells.length];
        more(sx, sy, sz, -1);
    }

    /** Goes on walking from another spot (beyond a Rig room, which its own mechanism gets you through), reached from {@code from}. */
    void more(int sx, int sy, int sz, int from) {
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int y0 = land(sx, sy, sz);
        if (y0 < 0) return;
        startIdx = i(sx, y0, sz);
        if (reach[startIdx] != 0) return;
        reach[startIdx] = 1;
        parent[startIdx] = from;
        q.add(startIdx);
        int[] dx = {1, -1, 0, 0}, dz = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int c = q.poll();
            int x = c / (SIDE * h), z = (c / h) % SIDE, y = c % h + yLo;
            byte here = kind(at(x, y, z));
            boolean holds = (here & (CLIMB | WATER)) != 0;
            for (int k = 0; k < 4; k++) {
                int nx = x + dx[k], nz = z + dz[k];
                if (pass(nx, y, nz) && pass(nx, y + 1, nz)) {
                    int ly = land(nx, y, nz);
                    if (ly >= 0) visit(q, c, nx, ly, nz);
                }
                // a jump up one block onto the next
                if (pass(x, y + 2, z) && node(nx, y + 1, nz)) visit(q, c, nx, y + 1, nz);
            }
            // up and down a rope, up out of water
            if (holds && node(x, y + 1, z)) visit(q, c, x, y + 1, z);
            if (holds && pass(x, y - 1, z) && node(x, y - 1, z)) visit(q, c, x, y - 1, z);
            if (holds && pass(x, y + 2, z)) {
                for (int k = 0; k < 4; k++) if (node(x + dx[k], y + 1, z + dz[k])) visit(q, c, x + dx[k], y + 1, z + dz[k]);
            }
        }
    }

    private void visit(ArrayDeque<Integer> q, int from, int x, int y, int z) {
        int i = i(x, y, z);
        if (i < 0 || reach[i] != 0) return;
        reach[i] = 1;
        parent[i] = from;
        q.add(i);
    }

    int idx(int x, int y, int z) {
        return i(x, y, z);
    }

    boolean reached(int x, int y, int z) {
        int i = i(x, y, z);
        return i >= 0 && reach != null && reach[i] != 0;
    }

    /** A reachable foot spot within {@code r} blocks (any height within 2) of (x, y, z), or null. */
    int[] near(int x, int y, int z, int r) {
        for (int d = 0; d <= r; d++) {
            for (int ax = -d; ax <= d; ax++) {
                for (int az = -d; az <= d; az++) {
                    if (Math.max(Math.abs(ax), Math.abs(az)) != d) continue;
                    for (int ay = -2; ay <= 2; ay++) if (reached(x + ax, y + ay, z + az)) return new int[]{x + ax, y + ay, z + az};
                }
            }
        }
        return null;
    }

    /** The walked route from the arrival to (x, y, z), arrival first. */
    List<int[]> route(int x, int y, int z) {
        List<int[]> out = new ArrayList<>();
        int c = i(x, y, z);
        if (c < 0 || reach[c] == 0) return out;
        while (c >= 0) {
            out.add(0, new int[]{c / (SIDE * h), c % h + yLo, (c / h) % SIDE});
            c = parent[c];
        }
        return out;
    }

    // ---- light --------------------------------------------------------------------------------------------------------

    /** Block light from every emitter, spreading one level a block through anything that isn't a full solid. */
    void light() {
        light = new byte[cells.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int c = 0; c < cells.length; c++) {
            if (cells[c] == 0) continue;
            int e = palette.get(cells[c]).getLightEmission();
            if (e > 0) {
                light[c] = (byte) e;
                q.add(c);
            }
        }
        int[] dx = {1, -1, 0, 0, 0, 0}, dy = {0, 0, 1, -1, 0, 0}, dz = {0, 0, 0, 0, 1, -1};
        while (!q.isEmpty()) {
            int c = q.poll();
            int l = light[c];
            if (l <= 1) continue;
            int x = c / (SIDE * h), z = (c / h) % SIDE, y = c % h + yLo;
            for (int k = 0; k < 6; k++) {
                int n = i(x + dx[k], y + dy[k], z + dz[k]);
                if (n < 0 || light[n] >= l - 1) continue;
                BlockState s = palette.get(cells[n]);
                if (cells[n] != 0 && s.canOcclude() && s.getLightEmission() == 0) continue;
                light[n] = (byte) (l - 1);
                q.add(n);
            }
        }
    }

    int lightAt(int x, int y, int z) {
        int i = i(x, y, z);
        return i < 0 || light == null ? 0 : light[i];
    }

    // ---- water ----------------------------------------------------------------------------------------------------------

    /** Water blocks with air (or anything that isn't water or solid) beside or below them: they'd flow on the first update. */
    List<int[]> leaks() {
        List<int[]> out = new ArrayList<>();
        int[] dx = {1, -1, 0, 0, 0}, dy = {0, 0, 0, 0, -1}, dz = {0, 0, 1, -1, 0};
        for (int c = 0; c < cells.length && out.size() < 16; c++) {
            if (cells[c] == 0 || palette.get(cells[c]).getBlock() != Blocks.WATER) continue;
            int x = c / (SIDE * h), z = (c / h) % SIDE, y = c % h + yLo;
            for (int k = 0; k < 5; k++) {
                BlockState n = at(x + dx[k], y + dy[k], z + dz[k]);
                if (n.getBlock() == Blocks.WATER) continue;
                if ((kind(n) & PASS) != 0) {
                    out.add(new int[]{x, y, z});
                    break;
                }
            }
        }
        return out;
    }

    // ---- pictures -------------------------------------------------------------------------------------------------------

    static int colour(BlockState s) {
        if (s.isAir()) return 0x10131c;
        if (s.getBlock() == Blocks.WATER) return 0x1d3550;
        return s.getBlock().defaultMapColor().col;
    }

    /** Draws slices, sections and a view from (ex, ey, ez) facing +z into {@code dir} as name-*.png. */
    void dump(java.nio.file.Path dir, String name, int ex, int ey, int ez) throws java.io.IOException {
        java.nio.file.Files.createDirectories(dir);
        int base = f.baseY;
        for (int dy : new int[]{1, 20}) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(SIDE, SIDE, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < SIDE; x++) {
                for (int z = 0; z < SIDE; z++) {
                    int c = shade(colour(at(x, base + dy, z)), lightAt(x, base + dy, z), true);
                    boolean walked = false;
                    for (int k = -1; k <= 1 && !walked; k++) walked = reached(x, base + dy - 1 + k, z);
                    if (walked) c = (c & 0xfefefe) / 2 + 0x206020;
                    img.setRGB(x, z, c);
                }
            }
            javax.imageio.ImageIO.write(img, "png", dir.resolve(name + "-plan" + dy + ".png").toFile());
        }
        java.awt.image.BufferedImage lon = new java.awt.image.BufferedImage(SIDE, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < SIDE; z++) for (int y = 0; y < h; y++) lon.setRGB(z, h - 1 - y, shade(colour(at(ex, yLo + y, z)), lightAt(ex, yLo + y, z), true));
        javax.imageio.ImageIO.write(lon, "png", dir.resolve(name + "-long.png").toFile());
        java.awt.image.BufferedImage cross = new java.awt.image.BufferedImage(SIDE, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int cz = ez + 24;
        for (int x = 0; x < SIDE; x++) for (int y = 0; y < h; y++) cross.setRGB(x, h - 1 - y, shade(colour(at(x, yLo + y, cz)), lightAt(x, yLo + y, cz), true));
        javax.imageio.ImageIO.write(cross, "png", dir.resolve(name + "-cross.png").toFile());
        javax.imageio.ImageIO.write(view(ex + 0.5, ey + 1.62, ez + 0.5, 0, -6, 480, 270), "png", dir.resolve(name + "-view.png").toFile());
        javax.imageio.ImageIO.write(view(ex + 0.5, ey + 1.62, ez + 0.5, 0, -32, 480, 270), "png", dir.resolve(name + "-up.png").toFile());
    }

    static int shade(int rgb, int l, boolean flat) {
        double k = flat ? 0.7 + 0.3 * l / 15.0 : 0.04 + 0.96 * Math.pow(l / 15.0, 1.6);
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** A rough view by rays: each pixel the first block it meets, lit by the light beside its face, fogged by distance. */
    java.awt.image.BufferedImage view(double ex, double ey, double ez, double yawDeg, double pitchDeg, int w, int hh) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, hh, java.awt.image.BufferedImage.TYPE_INT_RGB);
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg), fov = Math.toRadians(70);
        // facing +z at yaw 0, as the game does
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        // right = forward x up, up = right x forward
        double rx = -fz, rz = fx, rn = Math.hypot(rx, rz);
        rx /= rn;
        rz /= rn;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double t = Math.tan(fov / 2);
        for (int py = 0; py < hh; py++) {
            for (int px = 0; px < w; px++) {
                double sx = (2.0 * px / w - 1) * t * w / hh, sy = (1 - 2.0 * py / hh) * t;
                double dx = fx + rx * sx + ux * sy, dy = fy + uy * sy, dz = fz + rz * sx + uz * sy;
                double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
                img.setRGB(px, py, ray(ex, ey, ez, dx / n, dy / n, dz / n));
            }
        }
        return img;
    }

    private int ray(double ox, double oy, double oz, double dx, double dy, double dz) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stx = dx > 0 ? 1 : -1, sty = dy > 0 ? 1 : -1, stz = dz > 0 ? 1 : -1;
        double tdx = Math.abs(1 / dx), tdy = Math.abs(1 / dy), tdz = Math.abs(1 / dz);
        double tx = (dx > 0 ? x + 1 - ox : ox - x) * tdx, ty = (dy > 0 ? y + 1 - oy : oy - y) * tdy, tz = (dz > 0 ? z + 1 - oz : oz - z) * tdz;
        int px = x, py = y, pz = z;
        double dist = 0;
        for (int step = 0; step < 600 && dist < 200; step++) {
            BlockState s = at(x, y, z);
            if (!s.isAir() && (kind(s) & PASS) == 0 || s.getBlock() == Blocks.WATER || s.getLightEmission() > 0 && !s.is(Blocks.LIGHT)) {
                int l = Math.max(lightAt(px, py, pz), s.getLightEmission());
                int c = shade(colour(s), l, false);
                return fog(c, dist);
            }
            px = x;
            py = y;
            pz = z;
            if (tx < ty && tx < tz) {
                x += stx;
                dist = tx;
                tx += tdx;
            } else if (ty < tz) {
                y += sty;
                dist = ty;
                ty += tdy;
            } else {
                z += stz;
                dist = tz;
                tz += tdz;
            }
        }
        return 0x05060a;
    }

    private static int fog(int c, double dist) {
        double k = Math.min(1, Math.max(0, (dist - 12) / 140));
        int fr = 0x2a, fg = 0x31, fb = 0x40;
        int r = (int) (((c >> 16) & 255) * (1 - k) + fr * k * 0.5), g = (int) (((c >> 8) & 255) * (1 - k) + fg * k * 0.5),
                b = (int) ((c & 255) * (1 - k) + fb * k * 0.5);
        return (r << 16) | (g << 8) | b;
    }
}
