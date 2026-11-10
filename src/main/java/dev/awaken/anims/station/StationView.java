package dev.awaken.anims.station;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * The station scenes' geometry (SPEC §24, "Station scenes"), kept free of client classes so the GameTests can check it
 * headless: where each slot lies on the station, the screen's layout (the inventory strip, the hint, the room the
 * station is framed in), the view that frames every slot inside that room, and the slot under a point of the screen.
 */
public final class StationView {
    private StationView() {}

    /** A kind of station: its slots (the menu's first), its result slot and its hint. */
    public enum Kind {
        TABLE(10, 0, "screen.awaken.table.hint"),
        FURNACE(3, AbstractFurnaceMenu.RESULT_SLOT, "screen.awaken.furnace.hint");

        public final int slots, result;
        public final String hint;

        Kind(int slots, int result, String hint) {
            this.slots = slots;
            this.result = result;
            this.hint = hint;
        }
    }

    /** A station being worked: where, its kind, the way in (horizontal, along an axis); across is to your right. */
    public record Station(BlockPos pos, Kind kind, double fx, double fz) {
        public double rx() {
            return -fz;
        }

        public double rz() {
            return fx;
        }
    }

    /**
     * The crafting table top's 3×3 squares, as its texture draws them: 2-pixel squares between 1-pixel lines, a square
     * every 3 pixels of 16, centred on pixels 5, 8 and 11 (the grid is 9 of the face's 16 pixels, framed inside its
     * border; a third of the face each put the corner items out at the table's edges).
     */
    public static final double CELL = 3 / 16.0, GRID_HALF = 1.5 * CELL;
    /**
     * Where the table's result lies: flat on the top's right border (vanilla's own side of the grid), out from the middle
     * to your right, inside the table's own block so it can never go into a wall. A hand reaching over the grid lies
     * back toward you, so it never crosses the right border (on the near border it did; SPEC §63.2).
     */
    public static final double RESULT_SIDE = 0.39, RESULT_HALF = 0.085;

    /**
     * Where a slot lies on the station. A table: 0 the result, on the right border; 1..9 the grid, row 0 the far
     * one. A furnace: what cooks on the left of its mouth, the finished result on the right, the fuel on the coals below.
     */
    public static void spot(Station s, int slot, double[] out, int at) {
        BlockPos b = s.pos();
        double fx = s.fx(), fz = s.fz(), rx = s.rx(), rz = s.rz();
        if (s.kind() == Kind.FURNACE) {
            // out of the front face (n = -f), along it (r)
            double fx0 = b.getX() + 0.5 - fx * 0.5, fz0 = b.getZ() + 0.5 - fz * 0.5;
            double depth = FURNACE_DEPTH, side = slot == AbstractFurnaceMenu.RESULT_SLOT ? 0.15 : slot == AbstractFurnaceMenu.INGREDIENT_SLOT ? -0.15 : 0;
            out[at] = fx0 + fx * depth + rx * side;
            out[at + 1] = b.getY() + (slot == AbstractFurnaceMenu.FUEL_SLOT ? 0.26 : 0.57);
            out[at + 2] = fz0 + fz * depth + rz * side;
            return;
        }
        double cx = b.getX() + 0.5, cy = b.getY() + 1.0, cz = b.getZ() + 0.5;
        if (slot == 0) {
            out[at] = cx + rx * RESULT_SIDE;
            out[at + 1] = cy + 0.012;
            out[at + 2] = cz + rz * RESULT_SIDE;
            return;
        }
        int row = (slot - 1) / 3, col = (slot - 1) % 3;
        double u = (col - 1) * CELL, v = (1 - row) * CELL;
        out[at] = cx + rx * u + fx * v;
        out[at + 1] = cy + 0.01;
        out[at + 2] = cz + rz * u + fz * v;
    }

    /** How far a slot reaches from its spot (across, up, in): a grid square, the result, a furnace's opening. */
    public static double reach(Station s, int slot, int axis) {
        if (s.kind() == Kind.FURNACE) return axis == 2 ? 0.05 : 0.15;
        if (axis == 1) return 0.03;
        return slot == 0 ? RESULT_HALF : CELL / 2;
    }

    public static final double FURNACE_DEPTH = 3.0 / 16;

    /** A furnace lowers only the view, keeping the player's horizontal position. Side/rear access uses the ordinary menu. */
    public static boolean furnaceEye(Station s, double x, double y, double z, double[] out) {
        double dx = x - s.pos().getX() - .5, dz = z - s.pos().getZ() - .5;
        double front = -dx * s.fx() - dz * s.fz(), side = dx * s.rx() + dz * s.rz();
        if (front < .85 || front > 4.5) return false;
        // The far item's outer edge must still be visible through the opening from an oblique approach.
        double outside = front - .5;
        double edgeAtMouth = (.27 * outside + Math.abs(side) * FURNACE_DEPTH) / (outside + FURNACE_DEPTH);
        if (edgeAtMouth > .30) return false;
        out[0] = x; out[1] = s.pos().getY() + .57; out[2] = z;
        return true;
    }

    /** The near plane follows a clear vertical sweep; it cannot cross a floor, wall or low ceiling. */
    public static boolean clearEyePath(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity player,
                                       net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? -.12 : .12, y = (i & 2) == 0 ? -.12 : .12, z = (i & 4) == 0 ? -.12 : .12;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(from.add(x, y, z), to.add(x, y, z),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
        }
        return true;
    }

    // ---- the bench view (SPEC §63.1) --------------------------------------------------------------------------------

    /**
     * A table's own views, best first: how far back from its middle along the way in, how far over its top. The first
     * looks down on the grid at 54 degrees; the others are lower and nearer, for a low roof or a wall behind you.
     */
    private static final double[][] BENCH = {{0.81, 1.12}, {0.66, 0.90}, {0.55, 0.72}};
    public static final int BENCHES = BENCH.length;

    /** A table's bench view {@code i}: where the eye is. The same from wherever you clicked the table. */
    public static void benchEye(Station s, int i, double[] out) {
        BlockPos b = s.pos();
        out[0] = b.getX() + 0.5 - s.fx() * BENCH[i][0];
        out[1] = b.getY() + 1.0 + BENCH[i][1];
        out[2] = b.getZ() + 0.5 - s.fz() * BENCH[i][0];
    }

    /** How long the view takes to reach a station's own place from {@code d} blocks away (ticks): 5 beside it to 9. */
    public static int glideTicks(double d) {
        return (int) Math.round(Mth.clamp(4.2 + d * 1.1, 5, 9));
    }

    // ---- a furnace's own view (SPEC §63.6) --------------------------------------------------------------------------

    /** A furnace's mouth views, best first: how far out from the middle of its face, at the height of what cooks. */
    private static final double[] MOUTH = {1.05, 0.80, 0.60};
    public static final int MOUTHS = MOUTH.length;

    /** A furnace's mouth view {@code i}: where the eye is, the same from wherever you stand before it. */
    public static void mouthEye(Station s, int i, double[] out) {
        BlockPos b = s.pos();
        out[0] = b.getX() + 0.5 - s.fx() * (0.5 + MOUTH[i]);
        out[1] = b.getY() + 0.57;
        out[2] = b.getZ() + 0.5 - s.fz() * (0.5 + MOUTH[i]);
    }

    /** Standing before a furnace's face (its scene may open): out from the face, and no further to the side than out. */
    public static boolean beforeFace(Station s, double x, double z) {
        double dx = x - s.pos().getX() - .5, dz = z - s.pos().getZ() - .5;
        double front = -dx * s.fx() - dz * s.fz(), side = dx * s.rx() + dz * s.rz();
        return front >= 0.6 && front <= 5.0 && Math.abs(side) <= front + 0.3;
    }

    /** A stack carried at a furnace is held this far out from its face (inside the stone it could not be seen). */
    public static final double FURNACE_HOLD = 0.08;
    /** A fist at a furnace reads this wide against the world (blocks): a little over half a slot. */
    public static final double FURNACE_FIST = 0.165;

    /**
     * Where the line of sight through a point of the screen meets the upright plane a stack is held in before a furnace
     * (into {@code out}); false when it never does.
     */
    public static boolean onFace(Station s, double ex, double ey, double ez, Quaternionfc rot, float fov, float aspect, double mx, double my,
                                 int guiW, int guiH, double[] out) {
        float tan = (float) Math.tan(Math.toRadians(fov) / 2);
        Vector3f d = new Vector3f((float) (mx / guiW * 2 - 1) * tan * aspect, (float) (1 - my / guiH * 2) * tan, -1).normalize().rotate(rot);
        double along = d.x * s.fx() + d.z * s.fz();
        if (along < 1e-4) return false;
        double px = s.pos().getX() + 0.5 - s.fx() * (0.5 + FURNACE_HOLD), pz = s.pos().getZ() + 0.5 - s.fz() * (0.5 + FURNACE_HOLD);
        double t = ((px - ex) * s.fx() + (pz - ez) * s.fz()) / along;
        if (t <= 0) return false;
        out[0] = ex + d.x * t;
        out[1] = ey + d.y * t;
        out[2] = ez + d.z * t;
        return true;
    }

    /** The point {@code dist} along the line of sight through a point of the screen (into {@code out}). */
    public static void along(double ex, double ey, double ez, Quaternionfc rot, float fov, float aspect, double mx, double my, int guiW, int guiH,
                             double dist, double[] out) {
        float tan = (float) Math.tan(Math.toRadians(fov) / 2);
        Vector3f d = new Vector3f((float) (mx / guiW * 2 - 1) * tan * aspect, (float) (1 - my / guiH * 2) * tan, -1).normalize().rotate(rot);
        out[0] = ex + d.x * dist;
        out[1] = ey + d.y * dist;
        out[2] = ez + d.z * dist;
    }

    // ---- the carrying hand (SPEC §63.2) -----------------------------------------------------------------------------

    /** How far over the surface a carried stack is held, and how far short of it the fist's middle sits along the arm. */
    public static final double HOVER = 0.07, HAND_BACK = 0.11;
    /** The arm lies back toward a point this far past the table's near edge, this far over its top, leaning to its side. */
    public static final double ARM_BEYOND = 0.45, ARM_RISE = 0.30, ARM_LEAN = 0.10;
    /** A fist is this much of a grid square across on screen, and an arm may be drawn this much longer than its model. */
    public static final double FIST_SQUARES = 0.8, STRETCH_MOST = 1.8;
    /** The arm's model: a fist 4 texels across; from the fist's middle back to the arm's far end, in blocks. */
    public static final double FIST = 0.25, ARM_BACK = 0.685;

    /**
     * How far from the eye a station's arms are drawn so a fist reads {@link #FIST_SQUARES} of a square across, for a
     * hand {@code dw} blocks from the eye in the world ({@code lens}: the hand pass's field of view against the
     * world's, 1 when they are the same).
     */
    public static double handDistance(double dw, double lens) {
        return FIST * dw / (FIST_SQUARES * CELL * Math.max(0.05, lens));
    }

    /**
     * The carrying arm for a stack held at {@code (px, py, pz)} over a table: the fist's middle into {@code out[0..2]},
     * a unit vector along the arm from the fist back toward you into {@code out[3..5]}. {@code side}: 1 a right hand,
     * -1 a left. {@code most}: how far to either side of the table's middle the arm's far end may lie and still be under
     * the inventory strip ({@link #acrossMost}).
     */
    public static void carry(Station s, double px, double py, double pz, int side, double most, double[] out) {
        BlockPos b = s.pos();
        double cx = b.getX() + 0.5, cz = b.getZ() + 0.5;
        double u = (px - cx) * s.rx() + (pz - cz) * s.rz();
        double across = Mth.clamp(u + side * ARM_LEAN, -most, most), back = 0.5 + ARM_BEYOND;
        double ax = cx + s.rx() * across - s.fx() * back - px, ay = b.getY() + 1.0 + ARM_RISE - py, az = cz + s.rz() * across - s.fz() * back - pz;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1e-6) {
            ax = -s.fx();
            ay = 0;
            az = -s.fz();
            al = 1;
        }
        out[3] = ax / al;
        out[4] = ay / al;
        out[5] = az / al;
        out[0] = px + out[3] * HAND_BACK;
        out[1] = py + out[4] * HAND_BACK;
        out[2] = pz + out[5] * HAND_BACK;
    }

    /**
     * How far to either side of a table's middle an arm's far end may lie and still pass under the inventory strip: the
     * strip's top corners, seen through the view, on the level the arm's far end is at, less the arm's own half width.
     */
    public static double acrossMost(Station s, Layout l, double ex, double ey, double ez, Quaternionfc rot, float fov, float aspect, int guiW, int guiH,
                                    double[] p) {
        BlockPos b = s.pos();
        double cx = b.getX() + 0.5, cz = b.getZ() + 0.5, y = b.getY() + 1.0 + ARM_RISE * 0.75, most = 0.36;
        for (int k = 0; k < 2; k++) {
            if (!onPlane(ex, ey, ez, rot, fov, aspect, k == 0 ? l.px0() + 3 : l.px1() - 3, l.py0() + 3, guiW, guiH, y, p)) continue;
            most = Math.min(most, Math.abs((p[0] - cx) * s.rx() + (p[2] - cz) * s.rz()) - 0.1);
        }
        return Math.max(0.04, most);
    }

    /**
     * How much longer than its model the arm must be drawn for its far end to pass under the inventory strip (or off
     * the frame), so no sleeve end is ever in view: {@code 1} to {@link #STRETCH_MOST}. {@code arm}: {@link #carry}'s six
     * numbers; {@code scale}: world blocks to the hand's own (hand distance over the fist's distance from the eye).
     */
    public static float stretch(double[] arm, double scale, Layout l, double ex, double ey, double ez, Quaternionfc rot, float fov, float aspect,
                                int guiW, int guiH, double[] q) {
        double model = ARM_BACK / scale;
        for (double k = 1; k <= STRETCH_MOST + 1e-6; k += 0.05) {
            double len = model * k;
            if (!project(arm[0] + arm[3] * len, arm[1] + arm[4] * len, arm[2] + arm[5] * len, ex, ey, ez, rot, fov, aspect, guiW, guiH, q)) return (float) k;
            if (hidden(l, q[0], q[1], guiW, guiH)) return (float) k;
        }
        return (float) STRETCH_MOST;
    }

    /** A point of the screen the arm's far end may be at: under the strip, or off the frame. */
    public static boolean hidden(Layout l, double x, double y, int guiW, int guiH) {
        return y >= guiH + 2 || x < -2 || x > guiW + 2 || (y >= l.py0() + 2 && x >= l.px0() + 2 && x <= l.px1() - 2);
    }

    /**
     * Whether the arm lies over a slot's middle on screen (what lies there is then shown through the sleeve): the
     * slot's middle within the arm's half width of the line from the fist to the arm's far end. {@code len}: the arm's
     * drawn length back from the fist's middle, in world blocks; {@code half}: half its width, in world blocks.
     */
    public static boolean sleeveOver(Station s, int slot, double[] arm, double len, double half, double ex, double ey, double ez, Quaternionfc rot,
                                     float fov, float aspect, int guiW, int guiH, double[] p, double[] q) {
        spot(s, slot, p, 0);
        if (!project(p[0], p[1], p[2], ex, ey, ez, rot, fov, aspect, guiW, guiH, q)) return false;
        double sx = q[0], sy = q[1];
        // the fist's forward end (it covers a little ahead of its middle), and the far end
        double fwd = half;
        if (!project(arm[0] - arm[3] * fwd, arm[1] - arm[4] * fwd, arm[2] - arm[5] * fwd, ex, ey, ez, rot, fov, aspect, guiW, guiH, q)) return false;
        double ax = q[0], ay = q[1];
        if (!project(arm[0] + arm[3] * len, arm[1] + arm[4] * len, arm[2] + arm[5] * len, ex, ey, ez, rot, fov, aspect, guiW, guiH, q)) return false;
        double bx = q[0], by = q[1];
        // the arm's half width on screen, at the fist
        if (!project(arm[0] + s.rx() * half, arm[1], arm[2] + s.rz() * half, ex, ey, ez, rot, fov, aspect, guiW, guiH, q)) return false;
        double wx = q[0], wy = q[1];
        if (!project(arm[0], arm[1], arm[2], ex, ey, ez, rot, fov, aspect, guiW, guiH, q)) return false;
        double hw = Math.hypot(wx - q[0], wy - q[1]);
        double dx = bx - ax, dy = by - ay, ll = dx * dx + dy * dy;
        if (ll < 1e-6) return false;
        double t = Mth.clamp(((sx - ax) * dx + (sy - ay) * dy) / ll, 0, 1);
        return Math.hypot(sx - (ax + dx * t), sy - (ay + dy * t)) < hw;
    }

    /**
     * Where the line of sight through a point of the screen (gui pixels) meets the level plane at height {@code y}
     * (into {@code out}); false when it never does.
     */
    public static boolean onPlane(double ex, double ey, double ez, Quaternionfc rot, float fov, float aspect, double mx, double my, int guiW, int guiH,
                                  double y, double[] out) {
        float tan = (float) Math.tan(Math.toRadians(fov) / 2);
        Vector3f d = new Vector3f((float) (mx / guiW * 2 - 1) * tan * aspect, (float) (1 - my / guiH * 2) * tan, -1).normalize().rotate(rot);
        if (Math.abs(d.y) < 1e-5) return false;
        double t = (y - ey) / d.y;
        if (t <= 0) return false;
        out[0] = ex + d.x * t;
        out[1] = y;
        out[2] = ez + d.z * t;
        return true;
    }

    // ---- the screen -------------------------------------------------------------------------------------------------

    /** The inventory's image: vanilla's container size, its rows at 84..160 of it. */
    public static final int IMAGE_W = 176, IMAGE_H = 166;
    /** A hint line's height (the font's 9 and a gap). */
    public static final int LINE = 10;

    /**
     * The scene screen's layout (gui pixels): the inventory image's corner ({@code left}, {@code top}), the glass strip
     * behind its rows (the panel), where the hint's first line is, and the room the station is framed in, above the
     * hint. With side panels (JEI's list and bookmarks) the room is the middle half of the screen and the screen reports
     * room and strip as its area, so they sit beside it; without them it is the screen's width.
     */
    public record Layout(int left, int top, int px0, int py0, int px1, int py1, int hintY, int rx0, int ry0, int rx1, int ry1) {
        public boolean inPanel(double x, double y) {
            return x >= px0 && x < px1 && y >= py0 && y < py1;
        }

        public boolean inRoom(double x, double y) {
            return x >= rx0 && x < rx1 && y >= ry0 && y < ry1;
        }
    }

    public static Layout layout(int guiW, int guiH, int hintLines, boolean sidePanels) {
        int left = (guiW - IMAGE_W) / 2, top = guiH - 165;
        int px0 = left - 4, py0 = top + 80, px1 = left + IMAGE_W + 4, py1 = top + 162;
        // the furnace's two progress lines sit in the 7 px over the strip
        int hintY = py0 - 7 - LINE * hintLines;
        int half = sidePanels ? Math.max(IMAGE_W / 2 + 4, guiW / 4) : guiW / 2 - 8;
        return new Layout(left, top, px0, py0, px1, py1, hintY, guiW / 2 - half, 6, guiW / 2 + half, hintY - 4);
    }

    // ---- the view ---------------------------------------------------------------------------------------------------

    /** The widest the view opens to frame a station (vertical degrees), whatever the player's own. */
    public static final float MAX_FOV = 100F;
    /** Room left round the slots' bounds when framed. */
    private static final double MARGIN = 1.06;

    /** The camera's rotation for a look (degrees), exactly as the game's camera builds it. */
    public static Quaternionf rotation(float yaw, float pitch, Quaternionf q) {
        return q.rotationYXZ((float) Math.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0);
    }

    /**
     * The view from the eye that frames every slot of the station inside the layout's room: {@code out} gets yaw,
     * pitch and the vertical field of view (degrees, at most {@code maxFov}). {@code aspect} is the window's.
     */
    public static void frame(Station s, double ex, double ey, double ez, Layout l, int guiW, int guiH, float aspect, float maxFov, float[] out) {
        // first aim at the middle of the slots' box
        double[] p = new double[3], b = new double[4];
        double x0 = 1e9, y0 = 1e9, z0 = 1e9, x1 = -1e9, y1 = -1e9, z1 = -1e9;
        for (int i = 0; i < s.kind().slots; i++) {
            spot(s, i, p, 0);
            double m = Math.max(reach(s, i, 0), reach(s, i, 2)), mu = reach(s, i, 1);
            x0 = Math.min(x0, p[0] - m);
            x1 = Math.max(x1, p[0] + m);
            y0 = Math.min(y0, p[1] - mu);
            y1 = Math.max(y1, p[1] + mu);
            z0 = Math.min(z0, p[2] - m);
            z1 = Math.max(z1, p[2] + m);
        }
        double ax = (x0 + x1) / 2 - ex, ay = (y0 + y1) / 2 - ey, az = (z0 + z1) / 2 - ez;
        float yaw = (float) (Mth.atan2(-ax, az) * Mth.RAD_TO_DEG), pitch = (float) (-Mth.atan2(ay, Math.sqrt(ax * ax + az * az)) * Mth.RAD_TO_DEG);
        // the room in the screen's -1..1 (y up)
        double nx0 = l.rx0() * 2.0 / guiW - 1, nx1 = l.rx1() * 2.0 / guiW - 1, ny0 = 1 - l.ry0() * 2.0 / guiH, ny1 = 1 - l.ry1() * 2.0 / guiH;
        double hx = (nx1 - nx0) / 2, hy = (ny0 - ny1) / 2, ncx = (nx0 + nx1) / 2, ncy = (ny0 + ny1) / 2;
        double tanMax = Math.tan(Math.toRadians(maxFov) / 2), tan = tanMax;
        Quaternionf q = new Quaternionf();
        Vector3f f = new Vector3f(), u = new Vector3f(), r = new Vector3f();
        // then open the view to fit the slots in the room and turn it so they sit in the room's middle (a few rounds:
        // each turn changes the bounds a little)
        for (int it = 0; it < 4; it++) {
            rotation(yaw, pitch, q);
            f.set(0, 0, -1).rotate(q);
            u.set(0, 1, 0).rotate(q);
            r.set(1, 0, 0).rotate(q);
            bounds(s, ex, ey, ez, f, u, r, p, b);
            tan = Mth.clamp(Math.max((b[1] - b[0]) / (2 * aspect * hx), (b[3] - b[2]) / (2 * hy)) * MARGIN, 0.05, tanMax);
            yaw += (float) Math.toDegrees(Math.atan((b[0] + b[1]) / 2) - Math.atan(ncx * tan * aspect));
            pitch += (float) Math.toDegrees(Math.atan(ncy * tan) - Math.atan((b[2] + b[3]) / 2));
        }
        out[0] = yaw;
        out[1] = pitch;
        out[2] = (float) Math.toDegrees(2 * Math.atan(tan));
    }

    /** The slots' bounds in the view's tangent plane (x right, y up): min x, max x, min y, max y. */
    private static void bounds(Station s, double ex, double ey, double ez, Vector3f f, Vector3f u, Vector3f r, double[] p, double[] b) {
        b[0] = b[2] = 1e9;
        b[1] = b[3] = -1e9;
        double fx = s.fx(), fz = s.fz(), rx = s.rx(), rz = s.rz();
        for (int i = 0; i < s.kind().slots; i++) {
            spot(s, i, p, 0);
            double ma = reach(s, i, 0), mu = reach(s, i, 1), mi = reach(s, i, 2);
            for (int c = 0; c < 8; c++) {
                double a = (c & 1) == 0 ? -ma : ma, up = (c & 2) == 0 ? -mu : mu, in = (c & 4) == 0 ? -mi : mi;
                double wx = p[0] + rx * a + fx * in - ex, wy = p[1] + up - ey, wz = p[2] + rz * a + fz * in - ez;
                double z = Math.max(0.05, wx * f.x + wy * f.y + wz * f.z);
                double tx = (wx * r.x + wy * r.y + wz * r.z) / z, ty = (wx * u.x + wy * u.y + wz * u.z) / z;
                b[0] = Math.min(b[0], tx);
                b[1] = Math.max(b[1], tx);
                b[2] = Math.min(b[2], ty);
                b[3] = Math.max(b[3], ty);
            }
        }
    }

    /** Where a world point lands on the screen (gui pixels into {@code out}); false when it is behind the eye. */
    public static boolean project(double px, double py, double pz, double ex, double ey, double ez, Quaternionfc rot, float fov, float aspect,
                                  int guiW, int guiH, double[] out) {
        Vector3f w = new Vector3f((float) (px - ex), (float) (py - ey), (float) (pz - ez)).rotate(new Quaternionf(rot).conjugate());
        if (w.z >= -1e-4F) return false;
        double tan = Math.tan(Math.toRadians(fov) / 2);
        double nx = w.x / -w.z / (tan * aspect), ny = w.y / -w.z / tan;
        out[0] = (nx + 1) / 2 * guiW;
        out[1] = (1 - ny) / 2 * guiH;
        return true;
    }

    /**
     * The slot under a point of the screen (gui pixels), from a ray through the view: a grid square or the result on
     * the top (a table), the nearest spot the ray passes close to (a furnace); -1 for none, and always -1
     * outside the layout's room (the strip, the hint, a side panel: a click there never reaches the station).
     */
    public static int pick(Station s, Layout l, double ex, double ey, double ez, Quaternionfc rot, float fov, float aspect,
                           double mx, double my, int guiW, int guiH) {
        if (!l.inRoom(mx, my)) return -1;
        float tan = (float) Math.tan(Math.toRadians(fov) / 2);
        float nx = (float) (mx / guiW * 2 - 1), ny = (float) (1 - my / guiH * 2);
        Vector3f d = new Vector3f(nx * tan * aspect, ny * tan, -1).normalize().rotate(rot);
        double[] p = new double[3];
        if (s.kind() == Kind.FURNACE) {
            int best = -1;
            double bestAlong = Double.MAX_VALUE;
            for (int i = 0; i < s.kind().slots; i++) {
                spot(s, i, p, 0);
                double wx = p[0] - ex, wy = p[1] - ey, wz = p[2] - ez, along = wx * d.x + wy * d.y + wz * d.z;
                double qx = wx - d.x * along, qy = wy - d.y * along, qz = wz - d.z * along;
                if (along > 0 && along < bestAlong && qx * qx + qy * qy + qz * qz < 0.16 * 0.16) {
                    best = i;
                    bestAlong = along;
                }
            }
            return best;
        }
        // the top's plane
        BlockPos b = s.pos();
        if (Math.abs(d.y) < 1e-4) return -1;
        double t = (b.getY() + 1.0 - ey) / d.y;
        if (t <= 0) return -1;
        double hx = ex + d.x * t - (b.getX() + 0.5), hz = ez + d.z * t - (b.getZ() + 0.5);
        double u = hx * s.rx() + hz * s.rz(), v = hx * s.fx() + hz * s.fz();
        // the result on the right border
        if (Math.abs(u - RESULT_SIDE) <= RESULT_HALF + 0.02 && Math.abs(v) <= RESULT_HALF + 0.02) return 0;
        if (Math.abs(u) > GRID_HALF || Math.abs(v) > GRID_HALF) return -1;
        int col = Mth.clamp((int) Math.floor((u + GRID_HALF) / CELL), 0, 2), row = Mth.clamp((int) Math.floor((GRID_HALF - v) / CELL), 0, 2);
        return 1 + row * 3 + col;
    }
}
