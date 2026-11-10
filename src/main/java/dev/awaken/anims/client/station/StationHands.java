package dev.awaken.anims.client.station;

import dev.awaken.anims.station.StationView;
import dev.awaken.anims.station.StationView.Kind;
import dev.awaken.anims.station.StationView.Station;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The hands at a station (SPEC §63.2). While a stack is on your pointer and the pointer is over the station, your main
 * hand holds that stack: the stack is drawn exactly where the pointer's own line meets the air a little over the
 * surface (no lag: what you click is what it is over), the fist sits just short of it and one straight arm lies back
 * across the table toward you, its far end under the inventory strip. Empty, the hand waits at the near edge under the
 * pointer's column, off the grid; the other hand rests off its own corner. A change you made without carrying (a
 * shift-click, a number key) sends the nearer hand on one short pass to that slot.
 * <p>
 * The pointer and your clicks decide everything here. Nothing the server sends moves a hand: a reply only changes what
 * lies on a square, as on any container screen. A new target bends a hand from where it is; it never goes back to rest
 * first. Time is kept in whole nanoseconds and differences of them (never a float of a clock).
 */
final class StationHands {
    private StationHands() {}

    /** How long a hand takes to come in under a stack or go back out (seconds, to about two thirds of the way). */
    private static final double CARRY_TAU = 0.045, FREE_TAU = 0.05, SPEED_TAU = 0.06;
    /** A stack set down: the hand dips this far and comes back over this long; picked up, it rises from this far under. */
    private static final double DIP = 0.045, LIFT = 0.03;
    private static final long DIP_NS = 150_000_000L, LIFT_NS = 140_000_000L, PASS_NS = 190_000_000L;
    /** How far past the table's own top the hand follows the pointer (blocks, to the sides and the far side). */
    private static final double REACH_OUT = 0.35;

    private static long last, dipAt, liftAt, passAt;
    private static int passSlot = -1, passHand;
    /** The pointer in gui pixels (as fine as the mouse reports) and its speed across the screen, in screen widths a second. */
    static double px, py;
    private static double lastPx = Double.NaN, lastPy, speedX, speedY;
    /** How far the main hand is in under the stack (0 resting, 1 the stack is in it), and whether it holds one now. */
    private static double carry;
    private static boolean carrying, hasHit;
    /** Where the stack is held (world), the free places of the right and left hands (world), whether they are set. */
    private static final double[] HIT = new double[3], FREE = new double[6], ARM = new double[6], Q = new double[2], T = new double[3], P = new double[3];
    private static boolean freeSet;
    /** This frame's arm (the carrying hand's): its fist, its line, how long it is drawn, for the squares it lies over. */
    private static final double[] SLEEVE = new double[6];
    private static double sleeveLen;
    private static boolean sleeveOn;

    static void reset() {
        last = 0;
        carry = 0;
        carrying = hasHit = freeSet = sleeveOn = false;
        passSlot = -1;
        lastPx = Double.NaN;
        speedX = speedY = 0;
        dipAt = liftAt = passAt = 0;
    }

    private static int mainSide() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
    }

    /** The main hand holds the stack on the pointer this frame (the screen then draws no icon there). */
    static boolean carrying() {
        return carrying && carry > 0.5;
    }

    /** Where the carried stack is drawn (world): on the pointer's own line, a little over the surface. */
    static boolean hit(double[] out) {
        if (!carrying || !hasHit) return false;
        out[0] = HIT[0];
        out[1] = HIT[1];
        out[2] = HIT[2];
        return true;
    }

    /** The pointer's speed across the screen (screen widths a second, to your right): the stack tips with it. */
    static double speedX() {
        return speedX;
    }

    /** Your click set a stack down on this slot: the hand dips with it. */
    static void laid(int slot) {
        dipAt = System.nanoTime();
    }

    /** Your click picked a stack up: it rises into the hand. */
    static void lifted() {
        liftAt = System.nanoTime();
    }

    /** A slot you changed without carrying (a shift-click, a number key): the nearer hand makes one short pass to it. */
    static void pass(int slot) {
        Station s = StationClient.station;
        if (s == null || s.kind() != Kind.TABLE) return;
        passSlot = slot;
        passAt = System.nanoTime();
        StationView.spot(s, slot, P, 0);
        double u = (P[0] - s.pos().getX() - 0.5) * s.rx() + (P[2] - s.pos().getZ() - 0.5) * s.rz();
        passHand = Math.abs(u) < 0.05 ? mainSide() : u > 0 ? 1 : -1;
    }

    /** The carrying arm lies over this slot's middle this frame (what lies there is shown through the sleeve). */
    static boolean over(int slot, int guiW, int guiH) {
        Station s = StationClient.station;
        if (!sleeveOn || s == null) return false;
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.mainCamera();
        Vec3 o = cam.position();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return StationView.sleeveOver(s, slot, SLEEVE, sleeveLen, StationView.FIST / 2 / ARM_SCALE, o.x, o.y, o.z, cam.rotation(), cam.getFov(), aspect,
                guiW, guiH, P, Q);
    }

    /** World blocks to the arm's own, so a fist reads FIST_SQUARES of a square across. */
    static final double ARM_SCALE = StationView.FIST / (StationView.FIST_SQUARES * StationView.CELL);

    private static double smooth(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static double since(long at, long now, long length) {
        return at == 0 ? 2 : (now - at) / (double) length;
    }

    /**
     * The world layer's source for a table: both arms as world arms. {@code w}: how far the scene is in (0..1);
     * {@code screen}: the open scene screen, or null while the view goes home.
     */
    static boolean pose(float w, FirstPerson.Rig rig, @org.jspecify.annotations.Nullable StationScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        Station s = StationClient.station;
        if (s == null || mc.player == null) return false;
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        var cam = mc.gameRenderer.mainCamera();
        Vec3 eye = cam.position();
        var win = mc.getWindow();
        float aspect = (float) win.getWidth() / Math.max(1, win.getHeight());
        int gw = win.getGuiScaledWidth(), gh = win.getGuiScaledHeight();
        double cx = s.pos().getX() + 0.5, top = s.pos().getY() + 1.0, cz = s.pos().getZ() + 0.5;
        int main = mainSide();

        // the pointer, as fine as the mouse reports it, and its speed
        px = mc.mouseHandler.xpos() * gw / Math.max(1, win.getScreenWidth());
        py = mc.mouseHandler.ypos() * gh / Math.max(1, win.getScreenHeight());
        if (!Double.isNaN(lastPx) && dt > 0) {
            double k = 1 - Math.exp(-dt / SPEED_TAU);
            speedX += ((px - lastPx) / gw / dt - speedX) * k;
            speedY += ((py - lastPy) / gh / dt - speedY) * k;
        }
        lastPx = px;
        lastPy = py;

        // where the stack is held: the pointer's line onto the air over the top, a dip as it is set down, a rise as it is picked up
        ItemStack carried = screen == null ? ItemStack.EMPTY : screen.getMenu().getCarried();
        double dipU = since(dipAt, now, DIP_NS), liftU = since(liftAt, now, LIFT_NS);
        double dip = dipU < 1 ? Math.sin(dipU * Math.PI) * DIP : 0, lift = liftU < 1 ? LIFT * (1 - smooth(liftU)) : 0;
        boolean above = screen != null && screen.layout != null && py < screen.layout.py0() && w > 0.6F;
        hasHit = above && StationView.onPlane(eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, px, py, gw, gh, top + StationView.HOVER - dip - lift, HIT);
        double hu = 0, hv = 0;
        if (hasHit) {
            hu = (HIT[0] - cx) * s.rx() + (HIT[2] - cz) * s.rz();
            hv = (HIT[0] - cx) * s.fx() + (HIT[2] - cz) * s.fz();
            // past the table's sides or far side the hand does not follow (the stack is the pointer's icon there)
            if (Math.abs(hu) > 0.5 + REACH_OUT || hv > 0.5 + REACH_OUT) hasHit = false;
        }
        carrying = hasHit && !carried.isEmpty();
        if (dt > 0) carry += ((carrying ? 1 : 0) - carry) * (1 - Math.exp(-dt / CARRY_TAU));
        if (carry < 0.002) carry = 0;
        if (carry > 0.998) carry = 1;

        // each hand's free place: off the near edge (the main one under the pointer's column), or out on a pass to a slot
        double passU = since(passAt, now, PASS_NS);
        for (int h = 0; h < 2; h++) {
            int side = h == 0 ? 1 : -1;
            boolean isMain = side == main;
            double u = isMain ? (hasHit ? Mth.clamp(hu, -0.3, 0.3) : side * 0.2) : side * 0.34, v = isMain ? -0.56 : -0.6, y = top + (isMain ? 0.05 : 0.03);
            T[0] = cx + s.rx() * u + s.fx() * v;
            T[1] = y;
            T[2] = cz + s.rz() * u + s.fz() * v;
            if (passSlot >= 0 && passU < 1 && passHand == side) {
                StationView.spot(s, passSlot, P, 0);
                double k = Math.sin(Math.min(1, passU) * Math.PI);
                T[0] += (P[0] - T[0]) * k;
                T[1] += (P[1] + StationView.HOVER - T[1]) * k + 0.03 * k;
                T[2] += (P[2] - T[2]) * k;
            }
            int j = h * 3;
            if (!freeSet || dt <= 0) {
                FREE[j] = T[0];
                FREE[j + 1] = T[1];
                FREE[j + 2] = T[2];
            } else {
                double k = 1 - Math.exp(-dt / FREE_TAU);
                FREE[j] += (T[0] - FREE[j]) * k;
                FREE[j + 1] += (T[1] - FREE[j + 1]) * k;
                FREE[j + 2] += (T[2] - FREE[j + 2]) * k;
            }
        }
        freeSet = true;
        if (passU >= 1) passSlot = -1;

        // the arms: world arms at a table's own scale, each lying back toward you from its fist
        float hand = StationClient.ownsCamera() ? mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.hudFov : 70F;
        double lens = cam.getFov() <= 1F ? 1 : Math.tan(Math.toRadians(hand) / 2) / Math.tan(Math.toRadians(cam.getFov()) / 2);
        rig.fx = s.fx();
        rig.fz = s.fz();
        rig.worldArms = true;
        sleeveOn = false;
        double most = screen == null || screen.layout == null ? 0.3
                : StationView.acrossMost(s, screen.layout, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, gw, gh, P);
        double c = smooth(carry);
        for (int h = 0; h < 2; h++) {
            int side = h == 0 ? 1 : -1, j = h * 3;
            boolean isMain = side == main;
            double x = FREE[j], y = FREE[j + 1], z = FREE[j + 2];
            if (isMain && c > 0 && hasHit) {
                // under the stack: exactly at the pointer once it is in (nothing trails the pointer)
                x += (HIT[0] - x) * c;
                y += (HIT[1] - y) * c;
                z += (HIT[2] - z) * c;
            }
            // in and out of the scene: up from under the near edge
            y -= (1 - w) * 0.5;
            // the arm leans against the pointer's speed (the far end trails; the fist does not)
            StationView.carry(s, x - (isMain ? s.rx() * Mth.clamp(speedX * 0.12 * c, -0.08, 0.08) : 0), y, z - (isMain ? s.rz() * Mth.clamp(speedX * 0.12 * c, -0.08, 0.08) : 0), side, most, ARM);
            // the fist's middle sits just short of where the stack is held
            double fxw = x + ARM[3] * StationView.HAND_BACK, fyw = y + ARM[4] * StationView.HAND_BACK, fzw = z + ARM[5] * StationView.HAND_BACK;
            ARM[0] = fxw;
            ARM[1] = fyw;
            ARM[2] = fzw;
            double dw = Math.sqrt((fxw - eye.x) * (fxw - eye.x) + (fyw - eye.y) * (fyw - eye.y) + (fzw - eye.z) * (fzw - eye.z));
            if (isMain || rig.handDistance == 0) rig.handDistance = StationView.handDistance(dw, lens);
            float stretch = screen == null || screen.layout == null ? 1F
                    : StationView.stretch(ARM, ARM_SCALE, screen.layout, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, gw, gh, Q);
            rig.armStretch[h] = stretch;
            rig.limb(h == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, fxw + ARM[3], fyw + ARM[4], fzw + ARM[5], fxw, fyw, fzw, 0, 1, 0, w);
            if (isMain && c > 0.5) {
                System.arraycopy(ARM, 0, SLEEVE, 0, 6);
                sleeveLen = StationView.ARM_BACK * stretch / ARM_SCALE;
                sleeveOn = true;
            }
        }
        return true;
    }
}
