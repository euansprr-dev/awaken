package dev.awaken.anims.client.station;

import dev.awaken.anims.client.HandsLog;
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
 * The hands at a station (SPEC §65.2). A hand is in the view only while it works: your main hand while a stack is on
 * your pointer and the pointer is over the station (the stack is drawn exactly where the pointer's own line meets the
 * air a little over the surface, the fist just short of it), and either hand for the one short pass a change without
 * carrying gets (a shift-click, a number key, a blow as work is taken). It comes in along its own line from under the
 * inventory strip and goes back the same way; resting, there is no hand in the view at all (a resting arm seen from a
 * station's own view is its cut end and its back).
 * <p>
 * <b>It is your own arm.</b> A vanilla arm at its own size and its own proportions, never drawn longer than it is.
 * <b>Held still, it is still.</b> Its pose is a pure function of where the stack is held, the view and the layout
 * ({@link StationView#armTo}): no speed of the pointer, no remembered place, no length that changes. The only
 * things that move a hand over time are three short eases kept in whole nanoseconds (in under a stack, a dip as it is
 * set down, a pass). The pointer and your clicks decide everything; nothing the server sends moves a hand.
 */
final class StationHands {
    private StationHands() {}

    /** How long a hand takes to come in under a stack or go back out (seconds, to about two thirds of the way). */
    private static final double CARRY_TAU = 0.045;

    /** Where the carried stack is drawn (world): on the pointer's own line, a little over the surface. */
    static boolean hit(double[] out) {
        if (!carrying) return false;
        out[0] = HIT[0];
        out[1] = HIT[1];
        out[2] = HIT[2];
        return true;
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

    /** A stack set down: the hand dips this far and comes back over this long; picked up, it rises from this far under. */
    private static final double DIP = 0.045, LIFT = 0.03;
    private static final long DIP_NS = 150_000_000L, LIFT_NS = 140_000_000L, PASS_NS = 190_000_000L;
    /** How far past the table's own top the hand follows the pointer (blocks, to the sides and the far side). */
    private static final double REACH_OUT = 0.35;
    /** A stack carried before a brewing stand is held this far out from its middle: in front of every bottle and the rod. */
    private static final double STAND_HOLD = 0.34;

    private static long last, dipAt, liftAt, passAt;
    private static int passSlot = -1, passHand;
    /** Blows still to come on a slot (a pass each, by the hand that isn't carrying), and whether this one has struck. */
    private static int blowsLeft, blowSlot;
    private static boolean struck = true;
    /** The pointer in gui pixels (as fine as the mouse reports). */
    static double px, py;
    /** How far the main hand is in under the stack (0 out of the view, 1 the stack is in it), and whether it holds one now. */
    private static double carry;
    private static boolean carrying;
    /** How much larger than its own size this scene's arm would have to be to end under the strip ({@link StationView#armGrow}). */
    private static double grow = 1;
    private static StationView.@org.jspecify.annotations.Nullable Layout layout;
    /** Where the stack is held (world); an arm ({@link StationView#armTo}'s eight numbers); scratch. */
    private static final double[] HIT = new double[3], ARM = new double[8], Q = new double[2], T = new double[3], P = new double[3];
    /** This frame's arm (the carrying hand's): its fist, its line, how long it is drawn, for the squares it lies over. */
    private static final double[] SLEEVE = new double[6];
    private static double sleeveLen, sleeveHalf;
    private static boolean sleeveOn;

    static void reset() {
        last = 0;
        carry = 0;
        carrying = sleeveOn = false;
        passSlot = -1;
        blowsLeft = 0;
        struck = true;
        dipAt = liftAt = passAt = 0;
        grow = 1;
        layout = null;
    }

    private static int mainSide() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
    }

    /**
     * The main hand holds the stack on the pointer this frame: the world draws the stack (and the screen no icon there).
     * One answer for both, so the stack is never drawn twice or not at all.
     */
    static boolean carrying() {
        return carrying;
    }

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
    static boolean pose(float w, FirstPerson.Rig rig, @org.jspecify.annotations.Nullable StationScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        Station s = StationClient.station;
        if (s == null || mc.player == null) return false;
        // a furnace is worked through its face and a brewing stand stands upright: both on an upright plane
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
        if (screen != null && screen.layout != null) layout = screen.layout;
        StationView.Layout lay = layout;

        // the pointer, as fine as the mouse reports it
        px = mc.mouseHandler.xpos() * gw / Math.max(1, win.getScreenWidth());
        py = mc.mouseHandler.ypos() * gh / Math.max(1, win.getScreenHeight());

        // where the stack is held: the pointer's own line onto the air over a table's top, or onto the upright plane
        // before a furnace's face or a brewing stand; a dip as it is set down, a rise as it is picked up
        ItemStack carried = screen == null ? ItemStack.EMPTY : screen.getMenu().getCarried();
        double dipU = since(dipAt, now, DIP_NS), liftU = since(liftAt, now, LIFT_NS);
        double dip = dipU < 1 ? Math.sin(dipU * Math.PI) * DIP : 0, lift = liftU < 1 ? LIFT * (1 - smooth(liftU)) : 0;
        boolean above = screen != null && lay != null && py < lay.py0() && w > 0.6F;
        boolean hasHit;
        if (upright) {
            hasHit = above && StationView.onUpright(s, furnace ? 0.5 + StationView.FURNACE_HOLD : STAND_HOLD, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect,
                    px, py, gw, gh, HIT);
            if (hasHit) {
                double hu = (HIT[0] - cx) * s.rx() + (HIT[2] - cz) * s.rz();
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
                double hu = (HIT[0] - cx) * s.rx() + (HIT[2] - cz) * s.rz(), hv = (HIT[0] - cx) * s.fx() + (HIT[2] - cz) * s.fz();
                // past the table's sides or far side the hand does not follow (the stack is the pointer's icon there)
                if (Math.abs(hu) > 0.5 + REACH_OUT || hv > 0.5 + REACH_OUT) hasHit = false;
            }
        }
        carrying = hasHit && !carried.isEmpty();
        if (dt > 0) carry += ((carrying ? 1 : 0) - carry) * (1 - Math.exp(-dt / CARRY_TAU));
        if (carry < 0.002) carry = 0;
        if (carry > 0.998) carry = 1;

        // a pass: out to a slot and back; a blow lands at its middle, sparks off the work
        double passU = since(passAt, now, PASS_NS);
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

        // the scene has the hands for as long as it is in the view: the game's own hand and what it holds step aside
        rig.fx = s.fx();
        rig.fz = s.fz();
        rig.worldArms = true;
        rig.noHands = true;
        sleeveOn = false;
        HandsLog.scene(s.kind().name());
        if (lay == null || screen == null) return w > 0.01F;

        // the arms: your own arm at its own size, never drawn longer than it is; larger, whole, only in a window too
        // small for it to end under the strip (one number for the scene, found once the view is home), and no arm at all
        // where even that is too short
        float hand = StationClient.ownsCamera() ? mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.hudFov : 70F;
        double lens = cam.getFov() <= 1F ? 1 : Math.tan(Math.toRadians(hand) / 2) / Math.tan(Math.toRadians(cam.getFov()) / 2);
        if (w >= 0.999F) {
            grow = StationView.armGrow(s, StationView.ARM_BACK, StationView.HAND_BACK, lay, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, gw, gh, P, T);
        }
        if (grow > StationView.GROW_MOST) return w > 0.01F;
        double size = Math.max(1, grow), model = StationView.ARM_BACK * size, fist = StationView.FIST * size, reach = StationView.HAND_BACK * size;
        double c = smooth(carry);
        for (int h = 0; h < 2; h++) {
            int side = h == 0 ? 1 : -1;
            // what asks for this hand, and how far out it is: under the stack, or on a pass to a slot
            double out = 0, tx = 0, ty = 0, tz = 0;
            // (on its way back out the stack has left it: it goes from where it last held it)
            if (side == main && c > 0) {
                out = c;
                tx = HIT[0];
                ty = HIT[1];
                tz = HIT[2];
            }
            if (passing && passHand == side) {
                double k = Math.sin(Math.min(1, passU) * Math.PI);
                if (k > out) {
                    StationView.spot(s, passSlot, P, 0);
                    out = k;
                    tx = P[0] - (furnace ? s.fx() * (StationView.FURNACE_DEPTH + StationView.FURNACE_HOLD) : 0);
                    ty = P[1] + (upright ? 0 : StationView.HOVER);
                    tz = P[2] - (furnace ? s.fz() * (StationView.FURNACE_DEPTH + StationView.FURNACE_HOLD) : 0);
                }
            }
            if (out <= 0.004) continue;
            StationView.armTo(tx, ty, tz, side, model, reach, lay, eye.x, eye.y, eye.z, cam.rotation(), cam.getFov(), aspect, gw, gh, ARM, T);
            // in and out along its own line: from under the strip to where it works
            double back = (1 - out) * (ARM[7] + 0.06);
            double fxw = ARM[0] + ARM[3] * back, fyw = ARM[1] + ARM[4] * back, fzw = ARM[2] + ARM[5] * back;
            double dw = Math.sqrt((fxw - eye.x) * (fxw - eye.x) + (fyw - eye.y) * (fyw - eye.y) + (fzw - eye.z) * (fzw - eye.z));
            rig.armDistance[h] = (float) (StationView.FIST * dw / (fist * Math.max(0.05, lens)));
            if (rig.handDistance == 0) rig.handDistance = rig.armDistance[h];
            rig.armStretch[h] = 1F;
            rig.limb(h == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, fxw + ARM[3], fyw + ARM[4], fzw + ARM[5], fxw, fyw, fzw, 0, 1, 0, 1F);
            HandsLog.arm(h, fxw, fyw, fzw, ARM[3], ARM[4], ARM[5], ARM[6], out);
            if (side == main && carrying && c > 0.5) {
                SLEEVE[0] = fxw;
                SLEEVE[1] = fyw;
                SLEEVE[2] = fzw;
                SLEEVE[3] = ARM[3];
                SLEEVE[4] = ARM[4];
                SLEEVE[5] = ARM[5];
                sleeveLen = ARM[7];
                sleeveHalf = fist / 2;
                sleeveOn = true;
            }
        }
        // a lit fire lights the skin of an arm that reaches toward it (SPEC §65.6)
        if (furnace && screen.getMenu() instanceof net.minecraft.world.inventory.AbstractFurnaceMenu f) {
            rig.fireLight = Math.min(1F, (f.isLit() ? 0.45F : 0F) + StationFire.level());
        }
        return w > 0.01F;
    }

    /** This scene's arm size against its own (for the pose log). */
    static double grow() {
        return grow;
    }
}
