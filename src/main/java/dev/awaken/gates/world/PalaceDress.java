package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Mirror Palace (SPEC §38.12): white marble and gilt, a checkered floor, mirrors between gilt pilasters with curtains
 * at their sides, chandeliers and candelabras. The Gallery is its long halls; the Mirror Maze has a maze of mirror walls
 * in its rooms; the Inverted Palace stands on a glass floor over itself upside down; the White is nothing but white, a
 * tree of glass, a door standing on its own, shadows with nothing to cast them and a whale swimming high over it all; the
 * Room Behind the Glass is everything the wrong way round.
 */
final class PalaceDress extends Dress {
    final BlockState marble, veined, checker, gilt, mirror, cracked, glass, white, shadow, curtain, chandelier, candelabra,
            crystal, stand, chain, whaleSkin, whaleBelly;
    /** Shaped ground (SPEC §38.14): checker slabs and stairs on the floor, marble at the walls, mirror shards */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean gallery, maze, inverted, theWhite, shattered;
    /** Each maze room's maze, by room. */
    private final Map<Integer, Maze> mazes = new HashMap<>();
    /** The secret room's blocks (x0, z0, x1, z1), once known: it keeps the palace's look on the White. */
    private int[] secretBox;

    /** A maze over a room's box: cells of four (three open and a wall line), and which walls are knocked through. */
    static final class Maze {
        final int x0, z0, nx, nz;
        final boolean[] in, east, south;

        Maze(int x0, int z0, int nx, int nz) {
            this.x0 = x0;
            this.z0 = z0;
            this.nx = nx;
            this.nz = nz;
            in = new boolean[nx * nz];
            east = new boolean[nx * nz];
            south = new boolean[nx * nz];
        }

        boolean in(int cx, int cz) {
            return cx >= 0 && cz >= 0 && cx < nx && cz < nz && in[cx + cz * nx];
        }

        /** Whether a block of the room is maze wall. */
        boolean wall(int x, int z) {
            int lx = x - x0, lz = z - z0;
            int cx = Math.floorDiv(lx, 4), cz = Math.floorDiv(lz, 4);
            boolean onX = Math.floorMod(lx, 4) == 0, onZ = Math.floorMod(lz, 4) == 0;
            if (!onX && !onZ) return false;
            if (onX && onZ) return in(cx, cz) || in(cx - 1, cz) || in(cx, cz - 1) || in(cx - 1, cz - 1);
            if (onX) {
                // the line between (cx - 1, cz) and (cx, cz)
                boolean a = in(cx - 1, cz), b = in(cx, cz);
                return (a || b) && !(a && b && east[(cx - 1) + cz * nx]);
            }
            boolean a = in(cx, cz - 1), b = in(cx, cz);
            return (a || b) && !(a && b && south[cx + (cz - 1) * nx]);
        }

        /** Whether a block stands in the open middle of a cell the maze reaches. */
        boolean walkable(int x, int z) {
            int lx = x - x0, lz = z - z0;
            if (Math.floorMod(lx, 4) == 0 || Math.floorMod(lz, 4) == 0) return false;
            return in(Math.floorDiv(lx, 4), Math.floorDiv(lz, 4));
        }
    }

    PalaceDress(CaveEngine.Cave c) {
        super(c);
        marble = mat("wall", "awaken:palace_marble");
        veined = mat("wall_glow", "awaken:palace_marble_veined");
        checker = mat("floor", "awaken:palace_checker");
        gilt = mat("trim", "awaken:palace_gilt");
        mirror = mat("mirror", "awaken:palace_mirror");
        cracked = mat("mirror_cracked", "awaken:palace_mirror_cracked");
        glass = mat("glass", "awaken:palace_glass_floor");
        white = mat("white", "awaken:palace_white");
        shadow = mat("shadow", "awaken:palace_shadow");
        curtain = mat("curtain", "awaken:palace_curtain");
        chandelier = mat("chandelier", "awaken:palace_chandelier");
        candelabra = mat("candelabra", "awaken:palace_candelabra");
        crystal = mat("crystal", "awaken:palace_crystal");
        stand = mat("touch", "awaken:mirror_stand");
        gFloorSlab = mat("ground_slab", "awaken:palace_checker_slab");
        gFloorStairs = mat("ground_stairs", "awaken:palace_checker_stairs");
        gWallSlab = mat("wall_slab", "awaken:palace_marble_slab");
        gWallStairs = mat("wall_stairs", "awaken:palace_marble_stairs");
        gDrift = null;
        gBits = mat("ground_bits", "awaken:palace_shards");
        chain = GatePainter.state("minecraft:iron_chain[axis=y]");
        // the whale is grey, so it shows against the white as a shape in the haze
        whaleSkin = GatePainter.state("minecraft:gray_concrete");
        whaleBelly = GatePainter.state("minecraft:light_gray_concrete");
        String n = name();
        gallery = n.equals("gallery");
        maze = n.equals("mirror_maze");
        inverted = n.equals("inverted_palace");
        theWhite = n.equals("the_white");
        shattered = c.mood.equals("shattered");
    }

    @Override
    int glow() {
        return 0xe8e8ff;
    }

    @Override
    BlockState rail() {
        return gilt;
    }

    @Override
    BlockState underGlass() {
        return glass;
    }

    @Override
    boolean walk(Room r, int x, int z) {
        Maze m = mazes.get(r.id);
        return m == null || m.walkable(x, z);
    }

    boolean inSecret(int x, int z) {
        if (secretBox == null) {
            if (c.secret < 0) secretBox = new int[]{1, 1, 0, 0};
            else {
                int[] b = box(p.world(c.secret));
                secretBox = new int[]{b[0] - 3, b[1] - 3, b[2] + 3, b[3] + 3};
            }
        }
        return x >= secretBox[0] && x <= secretBox[2] && z >= secretBox[1] && z <= secretBox[3];
    }

    // ---- the skin -----------------------------------------------------------------------------------------------------

    /** The White's floor: a block in every eight glows (a staggered grid, four apart, so its pattern repeats every 32). */
    static boolean lamp(int x, int z) {
        return (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) || (Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2);
    }

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        // under the Inverted Palace's glass, the palace again, its floors and ceilings swapped
        if (inverted && y < W - 1) return skin(x, 2 * (W - 1) - y, z, ceiling, fl);
        if (theWhite && !inSecret(x, z)) return fl && !lamp(x, z) ? marble : white;
        int rise = y - W;
        if (fl) return checker;
        if (ceiling) return Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0 ? gilt : veined;
        if (rise == 0 || rise == 4) return gilt;
        return rise > 4 ? veined : marble;
    }

    @Override
    boolean depth() {
        return !inverted && !theWhite;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, false, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        // glass lies thick where the mirrors broke: the palace grows nothing
        return 0.45;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return null;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // marble columns banded with gilt, marble veined, a toppled gilt frame, a heap of broken mirror
        return pieces(part, x, y, z, marble, gilt, marble, veined, gilt, cracked);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        if (theWhite) return white;
        if (lat % 5 == 0 || rise == 0 || rise == 4) return gilt;
        return rise > 4 ? veined : marble;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return theWhite ? mist(0xffffff, 0.3f, 0xf0f0ff, 0.2f) : mist(0x5a5060, 0.25f, 0x3a3444, 0.12f);
    }

    // ---- the floor ----------------------------------------------------------------------------------------------------

    @Override
    void world() {
        if (theWhite && hero("the_whale")) whale();
    }

    /**
     * The White's whale, high over it all: a long body tapering to a tail with flukes, fins, and an eye of mirror on each
     * side of its head, swimming across the middle of the plot (inside the repeat, so the turn-round never shows it twice).
     */
    void whale() {
        double cx = 96 + Hash.unit(seed, 5, 51) * 64, cz = 96 + Hash.unit(seed, 5, 53) * 64, cy = W + 26;
        boolean alongX = Hash.unit(seed, 5, 57) < 0.5;
        double L = 36, R = 5.5;
        int r0 = (int) (L / 2) + 4;
        c.s.op((int) cx - r0, (int) (cy - R) - 3, (int) cz - r0, (int) cx + r0, (int) (cy + R) + 2, (int) cz + r0, (b, x, y, z) -> {
            double u = ((alongX ? x : z) + 0.5 - (alongX ? cx : cz)) / (L / 2), w = (alongX ? z : x) + 0.5 - (alongX ? cz : cx);
            double v = y + 0.5 - cy;
            // u from -1 (tail) to 1 (head): fat toward the head, thin at the tail
            double rad = u < -1.15 || u > 1.05 ? 0 : R * Math.sqrt(Math.max(0, 1 - Math.pow(Math.max(u, -1), 2))) * (0.55 + 0.45 * (u + 1) / 2);
            double d = Math.hypot(w, v * 1.2);
            boolean body = rad > 0 && d < rad && d > rad - 1.6;
            boolean flukes = u < -0.9 && u > -1.3 && Math.abs(v) < 0.6 && Math.abs(w) < 2 + (-0.9 - u) * 16;
            boolean fins = u > 0.25 && u < 0.45 && v < -1 && v > -2.2 && Math.abs(w) < rad + 4 && Math.abs(w) > rad - 1;
            if (u > 0.62 && u < 0.7 && Math.abs(v - 0.8) < 0.7 && Math.abs(Math.abs(w) - rad + 0.6) < 0.7) {
                b.set(x, y, z, mirror);
                return;
            }
            if (body || flukes || fins) b.set(x, y, z, v < -rad * 0.35 ? whaleBelly : whaleSkin);
        });
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        if (theWhite) {
            whitePlace(r, k);
            return;
        }
        int[] b = box(r);
        int roof = c.roofs.getOrDefault(id, c.roof);
        boolean mazed = maze && k == CaveEngine.Kind.CAVERN && !p.sealable(r) && r.type != Room.Type.INNER_GATE && hero("the_maze");
        if (mazed) mazeIn(r);
        mirrorFaces(r, b[0], W, b[1], b[2], W + 4, b[3]);
        if (!mazed) chandeliers(r, roof);
        if (gallery || inverted) {
            candelabras(r);
            grow(r, 0.008, 19, (x, y, z, len, i) -> shadow);
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, stand, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_look", 60);
        if (k == CaveEngine.Kind.GREAT && sh != null) {
            if (gallery) {
                int[] at = wallNook(r, "the_portrait", 70);
                // the portrait under its cloth at the back of the nook
                if (at != null) c.s.putOpen(at[0], W + 1, at[1], curtain);
            }
            if (maze) wallNook(r, "the_true_mirror", 70);
            if (inverted) wallNook(r, "the_way_down", 70);
        }
        if (sh != null) light(sh.cx(), 4, sh.cz(), shattered ? 0xc8d0ff : 0xffe8c0, 10f + r.gw * 3, 0.55f);
    }

    /**
     * The walls a place shows: between gilt pilasters every six blocks, a curtain each side of a tall mirror (cracked
     * here and there when the palace is shattered).
     */
    void mirrorFaces(Room r, int x0, int y0, int z0, int x1, int y1, int z1) {
        c.s.op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> {
            if (b.get(x, y, z) != Sculpt.SOLID) return;
            boolean facesZ = b.open(x, y, z + 1) || b.open(x, y, z - 1);
            if (!facesZ && !(b.open(x + 1, y, z) || b.open(x - 1, y, z))) return;
            int rise = y - W;
            int lat = Math.floorMod(facesZ ? x : z, 6);
            if (lat == 0) b.set(x, y, z, gilt);
            else if (rise == 0 || rise == 4) return;
            else if (lat == 1 || lat == 5) b.set(x, y, z, curtain);
            else b.set(x, y, z, shattered && Hash.unit(seed + 31, x, y * 31 + z) < 0.35 ? cracked : mirror);
        });
    }

    /** Chandeliers on chains from the roof: one over a small place, four over a big one. */
    void chandeliers(Room r, int roof) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        boolean big = r.gw * r.gh >= 4;
        for (int i = 0; i < (big ? 4 : 1); i++) {
            double x = big ? sh.cx() + (i % 2 == 0 ? -1 : 1) * sh.a() * 0.4 : sh.cx();
            double z = big ? sh.cz() + (i < 2 ? -1 : 1) * sh.b() * 0.4 : sh.cz();
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            int top = top(id, ix, iz);
            int low = Math.max(W + 6, top - 4);
            c.s.op(ix, low, iz, ix, top, iz, (b, xx, y, zz) -> {
                if (b.open(xx, y, zz)) b.set(xx, y, zz, y == low ? chandelier : chain);
            });
            light(x, low - W, z, 0xffe0b0, 12f, 0.6f);
        }
    }

    /** Candelabras along a place's walls every twelve blocks, a step in from the wall. */
    void candelabras(Room r) {
        int id = r.id;
        int[] b = box(r);
        c.s.op(b[0], W, b[1], b[2], W, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || Math.floorMod(x + z, 12) != 3) return;
            double d = c.d(id, x + 0.5, z + 0.5);
            if (d > -1.2 || d < -2.2 || nearDoor(r, x + 0.5, z + 0.5, 3) || p.nearAnchor(x + 0.5, z + 0.5, 3)) return;
            buf.set(x, y, z, candelabra);
        });
    }

    /**
     * A maze of mirror walls three high under a gilt rail filling a place: cells four across, every one reached and one
     * way between any two (a depth-first walk from the middle), each doorway's mouth kept clear into it.
     */
    void mazeIn(Room r) {
        int id = r.id;
        int[] b = box(r);
        Maze m = new Maze(b[0], b[1], (b[2] - b[0] + 1) / 4, (b[3] - b[1] + 1) / 4);
        for (int cz = 0; cz < m.nz; cz++) {
            for (int cx = 0; cx < m.nx; cx++) {
                double px = b[0] + cx * 4 + 2.5, pz = b[1] + cz * 4 + 2.5;
                m.in[cx + cz * m.nx] = c.d(id, px, pz) < -1.5;
            }
        }
        // the walk: from the cell nearest the middle, knocking through to a random unvisited neighbour, back when stuck
        boolean[] seen = new boolean[m.nx * m.nz];
        int start = -1;
        double best = 1e9;
        PassageEngine.Shape sh = c.shapes.get(id);
        for (int i = 0; i < m.in.length; i++) {
            if (!m.in[i]) continue;
            double dd = sh == null ? 0 : Math.hypot(b[0] + (i % m.nx) * 4 + 2.5 - sh.cx(), b[1] + (i / m.nx) * 4 + 2.5 - sh.cz());
            if (dd < best) {
                best = dd;
                start = i;
            }
        }
        if (start < 0) return;
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        seen[start] = true;
        int step = 0;
        int[] nb = new int[4];
        while (!stack.isEmpty()) {
            int cur = stack.peek(), cx = cur % m.nx, cz = cur / m.nx, n = 0;
            for (int side = 0; side < 4; side++) {
                int ax = cx + GatePainter.DX[side], az = cz + GatePainter.DZ[side];
                if (m.in(ax, az) && !seen[ax + az * m.nx]) nb[n++] = side;
            }
            if (n == 0) {
                stack.pop();
                continue;
            }
            int side = nb[(int) (Hash.unit(seed, id * 131 + cur, step++) * n)];
            int ax = cx + GatePainter.DX[side], az = cz + GatePainter.DZ[side], next = ax + az * m.nx;
            switch (side) {
                case 1 -> m.east[cur] = true;
                case 3 -> m.east[next] = true;
                case 2 -> m.south[cur] = true;
                default -> m.south[next] = true;
            }
            seen[next] = true;
            stack.push(next);
        }
        mazes.put(id, m);
        c.s.op(b[0], W, b[1], b[2], W + 3, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !m.wall(x, z)) return;
            double px = x + 0.5, pz = z + 0.5;
            if (c.d(id, px, pz) > -0.5 || nearDoor(r, px, pz, 2.2) || p.nearAnchor(px, pz, 2.5)) return;
            boolean post = Math.floorMod(x - m.x0, 4) == 0 && Math.floorMod(z - m.z0, 4) == 0;
            buf.set(x, y, z, y == W + 3 || post ? gilt : shattered && Hash.unit(seed + 37, x, y * 17 + z) < 0.3 ? cracked : mirror);
        });
        // a chandelier over the middle, where the walk began
        int ix = b[0] + (start % m.nx) * 4 + 2, iz = b[1] + (start / m.nx) * 4 + 2;
        int top = top(id, ix, iz);
        c.s.op(ix, W + 6, iz, ix, top, iz, (buf, xx, y, zz) -> {
            if (buf.open(xx, y, zz)) buf.set(xx, y, zz, y == W + 6 ? chandelier : chain);
        });
    }

    /**
     * A place on the White: shadows lying on it with nothing to cast them; in the great place the glass tree, in the
     * first calm one the door standing on its own.
     */
    void whitePlace(Room r, CaveEngine.Kind k) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        grow(r, 0.012, 19, (x, y, z, len, i) -> shadow);
        if (sh == null) return;
        if (k == CaveEngine.Kind.GREAT && hero("glass_tree")) tree(sh.cx(), sh.cz());
        if (k == CaveEngine.Kind.CAVERN && !doorPlaced && hero("lone_door")) {
            double[] at = spot(r, 90, 0.1, 0.4, 3);
            if (at != null) {
                doorPlaced = true;
                loneDoor((int) Math.floor(at[0]), (int) Math.floor(at[1]), unit(r.id, 91) < 0.5);
                block(at[0], at[1], 2.5);
            }
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, stand, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_look", 60);
    }

    private boolean doorPlaced;

    /** A tree of mirror-glass: a trunk two across, branches climbing out from it, buds of crystal at their ends. */
    void tree(double cx, double cz) {
        int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
        int H = 9;
        c.s.op(ix, W, iz, ix + 1, W + H, iz + 1, (b, x, y, z) -> {
            if (b.open(x, y, z)) b.set(x, y, z, mirror);
        });
        for (int i = 0; i < 7; i++) {
            double a = i / 7.0 * Math.PI * 2 + unit(ix, 3);
            int y0 = 5 + i % 3 * 2, len = 4 + (int) (unit(ix + i, 5) * 3);
            int ex = 0, ez = 0, ey = 0;
            for (int t = 1; t <= len; t++) {
                ex = ix + (int) Math.round(0.5 + Math.cos(a) * t);
                ez = iz + (int) Math.round(0.5 + Math.sin(a) * t);
                ey = W + y0 + t / 2;
                c.s.putOpen(ex, ey, ez, mirror);
            }
            c.s.putOpen(ex, ey + 1, ez, crystal);
        }
        block(cx + 0.5, cz + 0.5, 2);
        light(cx, H, cz, 0xe8f0ff, 10f, 0.5f);
    }

    /** A door standing on its own: a gilt frame two wide and three high, open through, nothing either side of it. */
    void loneDoor(int x, int z, boolean alongX) {
        for (int t = -1; t <= 2; t++) {
            for (int y = 0; y <= 3; y++) {
                boolean edge = t == -1 || t == 2 || y == 3;
                if (!edge) continue;
                c.s.putOpen(alongX ? x + t : x, W + y, alongX ? z : z + t, gilt);
            }
        }
        if (alongX) nook("the_lone_door", x, 0, z, x + 1, 2, z);
        else nook("the_lone_door", x, 0, z, x, 2, z + 1);
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        if (theWhite) return;
        int[] b = box(r);
        mirrorFaces(r, b[0], W, b[1], b[2], W + 4, b[3]);
    }

    @Override
    void secret(Room r) {
        // the Room Behind the Glass: mirrors all round, a chandelier standing on the floor and candelabras hanging from the
        // roof, shadows on the floor, and a clock on the back wall whose hands go the wrong way
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int[] b = box(r);
        int roof = c.roofs.getOrDefault(id, c.roof);
        mirrorFaces(r, b[0], W, b[1], b[2], W + 4, b[3]);
        double cx = sh.cx(), cz = sh.cz();
        int ix = (int) Math.floor(cx) + 3, iz = (int) Math.floor(cz);
        c.s.putOpen(ix, W, iz, chandelier);
        c.s.putOpen(ix, W + 1, iz, chain);
        c.s.op(b[0], W + 4, b[1], b[2], W + roof + 2, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z) || Math.floorMod(x + z * 3, 9) != 0) return;
            buf.set(x, y, z, candelabra);
        });
        grow(r, 0.02, 23, (x, y, z, len, i) -> shadow);
        int back = CaveEngine.Cave.back(r);
        double fx = cx + GatePainter.DX[back] * sh.a() * 0.8, fz = cz + GatePainter.DZ[back] * sh.b() * 0.8;
        boolean faceX = GatePainter.DZ[back] != 0;
        double R = 3.5, cy = W + 5;
        c.s.op((int) fx - 5, W + 1, (int) fz - 5, (int) fx + 5, W + 10, (int) fz + 5, (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double u = (faceX ? x : z) + 0.5 - (faceX ? fx : fz), depth = (faceX ? z : x) + 0.5 - (faceX ? fz : fx);
            double v = y + 0.5 - cy, d = Math.hypot(u, v);
            if (Math.abs(depth) > 0.6) return;
            // the hands at a quarter past, read backwards: a quarter to
            boolean hand = (Math.abs(u) < 0.6 && v > 0 && v < R - 1) || (Math.abs(v) < 0.6 && u > 0 && u < R - 1.5);
            if ((d > R - 1 && d < R) || hand) buf.set(x, y, z, gilt);
        });
        int nx = (int) cx, nz = (int) cz;
        nook("the_backwards_clock", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        light(cx, 4, cz, 0xffe0c0, 14f, 0.8f);
    }
}
