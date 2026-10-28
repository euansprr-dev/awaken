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
     * Where the table's result floats: over the top's far edge (out from the middle, up), so from where you stand it
     * never hides a square (over the middle it hid the far middle one).
     */
    public static final double RESULT_OUT = 0.5, RESULT_UP = 0.25;

    /**
     * Where a slot lies on the station. A table: 0 the result, floating over the far edge; 1..9 the grid, row 0 the far
     * one. A furnace: what cooks in the mouth, the fuel in the firebox below it, the finished result inside the mouth on the right.
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
            out[at] = cx + fx * RESULT_OUT;
            out[at + 1] = cy + RESULT_UP;
            out[at + 2] = cz + fz * RESULT_OUT;
            return;
        }
        int row = (slot - 1) / 3, col = (slot - 1) % 3;
        double u = (col - 1) * 0.3, v = (1 - row) * 0.3;
        out[at] = cx + rx * u + fx * v;
        out[at + 1] = cy + 0.01;
        out[at + 2] = cz + rz * u + fz * v;
    }

    /** How far a slot reaches from its spot (across, up, in): a grid square, a floating result, a furnace's opening. */
    private static double reach(Station s, int slot, int axis) {
        if (s.kind() == Kind.FURNACE) return axis == 2 ? 0.05 : 0.15;
        return slot != 0 && axis == 1 ? 0.03 : 0.15;
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
            double m = 0.15;
            x0 = Math.min(x0, p[0] - m);
            x1 = Math.max(x1, p[0] + m);
            y0 = Math.min(y0, p[1] - m);
            y1 = Math.max(y1, p[1] + m);
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
     * The slot under a point of the screen (gui pixels), from a ray through the view: a grid square on the top or the
     * floating result (a table), the nearest spot the ray passes close to (a furnace); -1 for none, and always -1
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
        // the result: near the ray, over the far edge
        spot(s, 0, p, 0);
        double wx = p[0] - ex, wy = p[1] - ey, wz = p[2] - ez, along = wx * d.x + wy * d.y + wz * d.z;
        double qx = wx - d.x * along, qy = wy - d.y * along, qz = wz - d.z * along;
        if (along > 0 && qx * qx + qy * qy + qz * qz < 0.15 * 0.15) return 0;
        // the top's plane
        BlockPos b = s.pos();
        if (Math.abs(d.y) < 1e-4) return -1;
        double t = (b.getY() + 1.0 - ey) / d.y;
        if (t <= 0) return -1;
        double hx = ex + d.x * t - (b.getX() + 0.5), hz = ez + d.z * t - (b.getZ() + 0.5);
        double u = hx * s.rx() + hz * s.rz(), v = hx * s.fx() + hz * s.fz();
        if (Math.abs(u) > 0.45 || Math.abs(v) > 0.45) return -1;
        int col = Mth.clamp((int) Math.floor((u + 0.45) / 0.3), 0, 2), row = Mth.clamp((int) Math.floor((0.45 - v) / 0.3), 0, 2);
        return 1 + row * 3 + col;
    }
}
