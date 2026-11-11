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
 * The hands at a station (SPEC §65.2). While a stack is on your pointer and the pointer is over the station, your main
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
    /** Blows still to come on a slot (a pass each, by the hand that isn't carrying), and whether this one has struck. */
    private static int blowsLeft, blowSlot;
    private static boolean struck = true;
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
    private static double sleeveLen, sleeveHalf;
    private static boolean sleeveOn;

    static void reset() {
        last = 0;
        carry = 0;
        carrying = hasHit = freeSet = sleeveOn = false;
        passSlot = -1;
        blowsLeft = 0;
        struck = true;
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
        if (s == null) return;
        passSlot = slot;
        passAt = System.nanoTime();
        StationView.spot(s, slot, P, 0);
        double u = (P[0] - s.pos().getX() - 0.5) * s.rx() + (P[2] - s.pos().getZ() - 0.5) * s.rz();
        // a table's left column the left hand, its right the right, the middle the main; a furnace's fuel the off hand
        passHand = s.kind() == Kind.FURNACE ? (slot == net.minecraft.world.inventory.AbstractFurnaceMenu.FUEL_SLOT ? -mainSide() : mainSide())
                : Math.abs(u) < 0.05 ? mainSide() : u > 0 ? 1 : -1;
    }

    /** {@code count} blows on a slot's work by the hand that isn't carrying (an anvil's three, a smithing table's one). */
    static void blows(int slot, int count) {
        blowSlot = slot;
        blowsLeft = count;
    }

    /** The carrying arm lies over this slot's middle this frame (what lies there is shown through the sleeve). */
    static boolean over(int slot, int guiW, int guiH) {
        Station s = StationClient.station;
        if (!sleeveOn || s == null) return false;
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.mainCamera();
        Vec3 o = cam.position();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return StationView.sleeveOver(s, slot, SLEEVE, sleeveLen, sleeveHalf, o.x, o.y, o.z, cam.rotation(), cam.getFov(), aspect,
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
     * The world layer's source: your arms at the station, as world arms. {@code w}: how far the scene is in (0..1);
     * {@code screen}: the open scene screen, or null while the view goes home.
     */
    /** A stack carried before a brewing stand is held this far out from its middle: in front of every bottle and the rod. */
    private static final double STAND_HOLD = 0.34;

    static boolean pose(float w, FirstPerson.Rig rig, @org.jspecify.annotations.Nullable StationScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        Station s = StationClient.station;
        if (s == null || mc.player == null) return false;
        // a furnace is worked through its face and a brewing stand stands upright: both on an upright plane, with hands that
        // show only while they work (resting before either they would be two arms across the frame)
        boolean furnace = s.kind() == Kind.FURNACE, upright = s.kind().upright;
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        var cam = mc.gameRenderer.mainCamera();
        Vec3 eye = cam.position();
        var win = mc.getWindow();
        float aspect = (float) win.getWidth() / Math.max(1, win.getHeight());
        int gw = win.getGuiScaledWidth(), gh = win.getGuiScaledHeight();
        double cx = s.pos().getX() + 0.5, top = s.top(), base = s.pos().getY(), cz = s.pos().getZ() + 0.5;
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

        // where the stack is held: the pointer's own line onto the air over a table's top, or onto the upright plane just
        // out from a furnace's face; a dip (down, or in to the mouth) as it is set down, a rise as it is picked up
        ItemStack carried = screen == null ? ItemStack.EMPTY : screen.getMenu().getCarried();
        double dipU = since(dipAt, now, DIP_NS), liftU = since(liftAt, now, LIFT_NS);
        double dip = dipU < 1 ? Math.sin(dipU * Math.PI) * DIP : 0, lift = liftU < 1 ? LIFT * (1 - smooth(liftU)) : 0;
        boolean above = screen != null && screen.layout != null && py < screen.layout.py0() && w > 0.6F;
        double hu = 0, hv = 0;
        if (upright) {
            hasHit = above && StationView.onUpright(s, furnace ? 0.5 + StationView.FURNACE_HOLD : STAND_HOLD, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect,
                    px, py, gw, gh, HIT);
            if (hasHit) {
                hu = (HIT[0] - cx) * s.rx() + (HIT[2] - cz) * s.rz();
                if (Math.abs(hu) > 0.5 + REACH_OUT || HIT[1] < base - 0.2 || HIT[1] > base + 1.3) hasHit = false;
                if (furnace) {
                    // set down, the hand goes in toward the mouth; picked up, the stack comes out to it
                    HIT[0] += s.fx() * (dip * 1.6 - lift);
                    HIT[2] += s.fz() * (dip * 1.6 - lift);
                } else {
                    // on a stand it is set down onto its place and lifted off it
                    HIT[1] += lift - dip;
                }
            }
        } else {
            hasHit = above && StationView.onPlane(eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, px, py, gw, gh, top + StationView.HOVER - dip - lift, HIT);
            if (hasHit) {
                hu = (HIT[0] - cx) * s.rx() + (HIT[2] - cz) * s.rz();
                hv = (HIT[0] - cx) * s.fx() + (HIT[2] - cz) * s.fz();
                // past the table's sides or far side the hand does not follow (the stack is the pointer's icon there)
                if (Math.abs(hu) > 0.5 + REACH_OUT || hv > 0.5 + REACH_OUT) hasHit = false;
            }
        }
        carrying = hasHit && !carried.isEmpty();
        if (dt > 0) carry += ((carrying ? 1 : 0) - carry) * (1 - Math.exp(-dt / CARRY_TAU));
        if (carry < 0.002) carry = 0;
        if (carry > 0.998) carry = 1;

        // each hand's free place: off a table's near edge (the main one under the pointer's column), low and out of the
        // frame before a furnace; or out on a pass to a slot
        double passU = since(passAt, now, PASS_NS);
        for (int h = 0; h < 2; h++) {
            int side = h == 0 ? 1 : -1;
            boolean isMain = side == main;
            if (upright) {
                T[0] = eye.x + s.rx() * side * 0.34 + s.fx() * 0.45;
                T[1] = eye.y - 0.42;
                T[2] = eye.z + s.rz() * side * 0.34 + s.fz() * 0.45;
            } else {
                // a hand with nothing to do rests off the near corner on its own side, clear of the strip's glass; the main
                // one comes in under the pointer's column only once it carries (waiting there empty it lay under the glass)
                boolean under = isMain && hasHit && !carried.isEmpty();
                double u = under ? Mth.clamp(hu, -0.3, 0.3) : side * 0.36, v = under ? -0.56 : -0.6;
                T[0] = cx + s.rx() * u + s.fx() * v;
                T[1] = top + (under ? 0.05 : 0.03);
                T[2] = cz + s.rz() * u + s.fz() * v;
            }
            if (passSlot >= 0 && passU < 1 && passHand == side) {
                StationView.spot(s, passSlot, P, 0);
                double k = Math.sin(Math.min(1, passU) * Math.PI);
                double ox = furnace ? -s.fx() * (StationView.FURNACE_DEPTH + StationView.FURNACE_HOLD) : 0, oz = furnace ? -s.fz() * (StationView.FURNACE_DEPTH + StationView.FURNACE_HOLD) : 0;
                T[0] += (P[0] + ox - T[0]) * k;
                T[1] += (P[1] + (upright ? 0 : StationView.HOVER) - T[1]) * k + (upright ? 0 : 0.03 * k);
                T[2] += (P[2] + oz - T[2]) * k;
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
        // a blow lands at the middle of its pass: sparks off the work
        if (passSlot >= 0 && !struck && passU >= 0.5 && passU < 1) {
            struck = true;
            StationView.spot(s, passSlot, P, 0);
            StationFire.sparks(P[0], P[1] + 0.03, P[2], 7);
        }
        boolean passing = passSlot >= 0 && passU < 1;
        if (passU >= 1) passSlot = -1;
        if (passSlot < 0 && blowsLeft > 0) {
            blowsLeft--;
            passSlot = blowSlot;
            passAt = now;
            passHand = -main;
            struck = false;
        }

        // the arms: world arms at the station's own scale, each lying back toward you from its fist
        float hand = StationClient.ownsCamera() ? mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.hudFov : 70F;
        double lens = cam.getFov() <= 1F ? 1 : Math.tan(Math.toRadians(hand) / 2) / Math.tan(Math.toRadians(cam.getFov()) / 2);
        double scale = upright ? StationView.FIST / StationView.FURNACE_FIST : ARM_SCALE;
        rig.fx = s.fx();
        rig.fz = s.fz();
        rig.worldArms = true;
        sleeveOn = false;
        double most = upright || screen == null || screen.layout == null ? 0.3
                : StationView.acrossMost(s, screen.layout, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, gw, gh, P);
        double c = smooth(carry);
        boolean posed = false;
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
            // how much of this hand shows: at a table always (it rests at the near edge); before a furnace only while it
            // works (resting there it would be a corner of arm in the frame, SPEC §24)
            float show = w;
            if (upright) show *= (float) Math.max(isMain ? c : 0, passing && passHand == side ? Math.sin(Math.min(1, passU) * Math.PI) : 0);
            if (show <= 0.01F) continue;
            // in and out of the scene: up from under the near edge
            if (!upright) y -= (1 - w) * 0.5;
            double lean = isMain ? Mth.clamp(speedX * 0.12 * c, -0.08, 0.08) : 0;
            if (upright) {
                // back toward you and down: to a point under the inventory strip's corner on the hand's own side
                double dw0 = Math.sqrt((x - eye.x) * (x - eye.x) + (y - eye.y) * (y - eye.y) + (z - eye.z) * (z - eye.z));
                if (screen != null && screen.layout != null) {
                    StationView.along(eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, side > 0 ? screen.layout.px1() - 16 : screen.layout.px0() + 16,
                            screen.layout.py0() + 18, gw, gh, dw0 * 0.6, P);
                } else {
                    P[0] = eye.x + s.rx() * side * 0.3;
                    P[1] = eye.y - 0.5;
                    P[2] = eye.z + s.rz() * side * 0.3;
                }
                double ax = P[0] - s.rx() * lean - x, ay = P[1] - y, az = P[2] - s.rz() * lean - z, al = Math.sqrt(ax * ax + ay * ay + az * az);
                if (al < 1e-5) continue;
                ARM[3] = ax / al;
                ARM[4] = ay / al;
                ARM[5] = az / al;
            } else {
                // the arm leans against the pointer's speed (the far end trails; the fist does not)
                StationView.carry(s, x - s.rx() * lean, y, z - s.rz() * lean, side, most, ARM);
            }
            // the fist's middle sits just short of where the stack is held
            double back = upright ? 0.13 : StationView.HAND_BACK;
            double fxw = x + ARM[3] * back, fyw = y + ARM[4] * back, fzw = z + ARM[5] * back;
            ARM[0] = fxw;
            ARM[1] = fyw;
            ARM[2] = fzw;
            double dw = Math.sqrt((fxw - eye.x) * (fxw - eye.x) + (fyw - eye.y) * (fyw - eye.y) + (fzw - eye.z) * (fzw - eye.z));
            if (isMain || rig.handDistance == 0) rig.handDistance = StationView.FIST * dw / (StationView.FIST / scale * Math.max(0.05, lens));
            float stretch = screen == null || screen.layout == null ? 1F
                    : StationView.stretch(ARM, scale, screen.layout, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, gw, gh, Q);
            rig.armStretch[h] = stretch;
            rig.limb(h == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, fxw + ARM[3], fyw + ARM[4], fzw + ARM[5], fxw, fyw, fzw, 0, 1, 0, show);
            posed = true;
            if (isMain && c > 0.5) {
                System.arraycopy(ARM, 0, SLEEVE, 0, 6);
                sleeveLen = StationView.ARM_BACK * stretch / scale;
                sleeveHalf = StationView.FIST / 2 / scale;
                sleeveOn = true;
            }
        }
        // a lit fire lights the skin of an arm that reaches toward it (SPEC §65.6)
        if (furnace && screen != null && screen.getMenu() instanceof net.minecraft.world.inventory.AbstractFurnaceMenu f) {
            rig.fireLight = Math.min(1F, (f.isLit() ? 0.45F : 0F) + StationFire.level());
        }
        // before a furnace the hands are the scene's even while neither shows: the game's own hand and what it holds
        // would sit in the corner of the lowered view
        if (upright && w > 0.01F) {
            rig.noHands = true;
            return true;
        }
        return posed;
    }
}
