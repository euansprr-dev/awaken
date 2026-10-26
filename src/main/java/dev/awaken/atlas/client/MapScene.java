package dev.awaken.atlas.client;

import dev.awaken.Awaken;
import dev.awaken.atlas.Discovery;
import dev.awaken.atlas.MapShot;
import dev.awaken.holo.HoloSounds;
import dev.awaken.holo.client.HoloScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The map in the world (SPEC §30.5, Euan's idea): M (or the band's Map page) raises your wrist, the camera rises out of
 * your eyes to a high shot over your shoulder, and the band projects the Atlas in front of you, the same picture as the
 * table. The map is usable at 250 ms while the camera settles (420 ms; 220 ms within 12 s of the last). M, Esc, K or
 * Space reverses it in 250 ms. The flat table opens instead (instantly) on Shift+M or a double tap of M, in a fight,
 * with no room for the camera, with reduced motion or the setting "Map opens as: Table". Hit, or a hostile within 8
 * blocks, while it is open: it folds in 150 ms. The world never pauses.
 */
public final class MapScene {
    private MapScene() {}

    enum Phase { CLOSED, OPEN, CLOSING }

    private static Phase phase = Phase.CLOSED;
    private static long openedAt, closingAt, closedAt = Long.MIN_VALUE / 2;
    private static float delayMs, moveMs, closeMs;
    private static int side = 1;
    private static boolean high;
    private static float shotYaw;
    // your eyes when the scene opened (x, y, z, yaw, pitch), where the camera goes back to
    private static final double[] EYES = new double[5], SHOT = new double[5], TMP = new double[5];
    /** Ms since the scene opened, when input is live (SPEC §30.5). */
    static final float LIVE_MS = 250f;

    /** Whether the scene holds the view (open or folding away). */
    public static boolean active() {
        return phase != Phase.CLOSED;
    }

    static boolean open() {
        return phase == Phase.OPEN;
    }

    /** Which shoulder the camera took (+1 right, -1 left): the screen mirrors its layout to keep clear of you. */
    static int side() {
        return side;
    }

    /** Ms since the scene opened. */
    static float ageMs() {
        return (System.nanoTime() - openedAt) / 1e6f;
    }

    /** The map's opening yaw: up the map is the way the camera looks (the table's yaw 0 looks north; yours faces south). */
    static float mapYaw() {
        return shotYaw + 180f;
    }

    /** M, or the band's Map page: the scene where it can open, else the table, whose own rules apply (a closed map). */
    public static void openFromKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        String why = refuse(mc);
        if (why != null) {
            Awaken.LOG.info("[atlas] map scene: the table instead ({})", why);
            table();
            return;
        }
        if (!start(mc)) table();
    }

    /** The flat table, at once (Shift+M, a double tap, a fallback). */
    static void table() {
        AtlasScreen.open(20f, 52f, 1f);
        dev.awaken.core.api.TipHooks.client().done(Awaken.id("first/atlas"));
    }

    /** Why the scene can't open now, or null when it can. */
    private static String refuse(Minecraft mc) {
        if (!Discovery.mapped(AtlasState.get().dimension())) return "unmapped";
        if (AtlasConfig.opensAsTable()) return "setting";
        if (!HoloScene.allowed()) return "reduced motion";
        if (dev.awaken.signal.client.SystemHud.inCombat()) return "combat";
        if (!dev.awaken.core.api.HoloBand.worn(mc.player)) return "no band";
        if (dev.awaken.opening.client.OpeningCamera.active()) return "camera busy";
        if (mc.player.isSleeping() || mc.player.isSpectator()) return "pose";
        if (mc.gui.screen() != null) return "screen";
        if (!room(mc)) return "no room";
        return null;
    }

    /** Room for the camera: over the right shoulder, the left, then higher; false when none is clear. */
    private static boolean room(Minecraft mc) {
        var p = mc.player;
        shotYaw = p.getYRot();
        for (int i = 0; i < 3; i++) {
            side = i == 1 ? -1 : 1;
            high = i == 2;
            MapShot.pose(p.getX(), p.getY(), p.getZ(), shotYaw, side, high, SHOT);
            if (clear(mc, p.getEyePosition(), new Vec3(SHOT[0], SHOT[1], SHOT[2]))) return true;
        }
        return false;
    }

    /** The line from your eyes to a little past the camera's place is clear of blocks. */
    private static boolean clear(Minecraft mc, Vec3 from, Vec3 to) {
        Vec3 past = to.add(to.subtract(from).normalize().scale(0.4));
        var hit = mc.level.clip(new ClipContext(from, past, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static boolean start(Minecraft mc) {
        var p = mc.player;
        long now = System.nanoTime();
        boolean quick = (now - closedAt) / 1_000_000L < MapShot.QUICK_WITHIN_MS;
        delayMs = quick ? MapShot.QUICK_DELAY_MS : MapShot.DELAY_MS;
        moveMs = quick ? MapShot.QUICK_MOVE_MS : MapShot.MOVE_MS;
        Vec3 eye = p.getEyePosition();
        EYES[0] = eye.x;
        EYES[1] = eye.y;
        EYES[2] = eye.z;
        EYES[3] = p.getYRot();
        EYES[4] = p.getXRot();
        openedAt = now;
        phase = Phase.OPEN;
        if (!MapCamera.take()) {
            phase = Phase.CLOSED;
            return false;
        }
        AtlasScreen.openScene(mapYaw());
        mc.getSoundManager().play(SimpleSoundInstance.forUI((quick ? HoloSounds.OPEN_QUICK : HoloSounds.OPEN).get(), 1.0f, 0.8f));
        dev.awaken.core.api.TipHooks.client().done(Awaken.id("first/atlas"));
        Awaken.LOG.info("[atlas] map scene opened: side {} high {} quick {}", side, high, quick);
        return true;
    }

    /** Closes the scene: the camera goes back to your eyes (in 150 ms when it folds for a fight). */
    static void close(boolean fold) {
        if (phase != Phase.OPEN) return;
        phase = Phase.CLOSING;
        long now = System.nanoTime();
        // reverse from wherever the move had got to
        float done = (float) MapShot.ease((ageMs() - delayMs) / moveMs);
        closeMs = (fold ? MapShot.FOLD_MS : MapShot.CLOSE_MS) * Math.max(0.3f, done);
        closingAt = now;
        HoloScene.noBeams();
        if (fold) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.CLOSE.get(), 1.5f, 0.9f));
    }

    /** From the scene straight to the flat table (Shift+M, a double tap): no camera move back. */
    static void toTable() {
        end();
        table();
    }

    /** Straight back to your eyes, the screen left to the caller (a band page opening over it). */
    static void cut() {
        end();
    }

    /** Something went wrong: the view comes back at once. */
    static void abandon() {
        end();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AtlasScreen s && s.scene) mc.gui.setScreen(null);
    }

    private static void end() {
        phase = Phase.CLOSED;
        closedAt = System.nanoTime();
        MapCamera.finish();
        HoloScene.map(0f, false);
    }

    /** Once a tick: the fight rule, and the scene's end. */
    static void tick() {
        if (phase == Phase.CLOSED) return;
        Minecraft mc = Minecraft.getInstance();
        if (!MapCamera.held()) { // the camera was taken (a respawn, spectating, a cinematic) or the level went away
            end();
            if (mc.gui.screen() instanceof AtlasScreen s && s.scene) mc.gui.setScreen(null);
            return;
        }
        if (phase == Phase.OPEN) {
            boolean screen = mc.gui.screen() instanceof AtlasScreen s && s.scene;
            if (!screen) close(false); // the screen went (another opened over it, a death)
            else if (mc.player.hurtTime > 0 || dev.awaken.signal.client.SystemHud.inCombat()) {
                Awaken.LOG.info("[atlas] map scene folded: a fight");
                close(true); // first, so the screen's removal finds it folding
                mc.gui.setScreen(null);
            }
        }
    }

    /** Each frame, before the level is drawn: the wrist and what others see. */
    static void frame() {
        if (phase == Phase.CLOSED) return;
        long now = System.nanoTime();
        if (phase == Phase.OPEN) {
            HoloScene.map(Math.min(1f, ageMs() / MapShot.RISE_MS), true);
        } else {
            float t = (now - closingAt) / 1e6f / Math.max(1f, closeMs);
            if (t >= 1f) {
                end();
                return;
            }
            HoloScene.map(1f - t, false);
        }
    }

    /** The camera this frame: from your eyes to the shot and, closing, back. out: x, y, z, yaw, pitch. */
    static void pose(float pt, double[] out) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) {
            System.arraycopy(EYES, 0, out, 0, 5);
            return;
        }
        // the shot follows you if you're pushed; it never turns with you
        double x = net.minecraft.util.Mth.lerp(pt, p.xo, p.getX()), y = net.minecraft.util.Mth.lerp(pt, p.yo, p.getY()),
                z = net.minecraft.util.Mth.lerp(pt, p.zo, p.getZ());
        MapShot.pose(x, y, z, shotYaw, side, high, SHOT);
        TMP[0] = x + (EYES[0] - p.getX());
        TMP[1] = y + p.getEyeHeight();
        TMP[2] = z + (EYES[2] - p.getZ());
        // your own view (the same as when it opened, unless you turned while it folded away)
        TMP[3] = net.minecraft.util.Mth.rotLerp(pt, p.yRotO, p.getYRot());
        TMP[4] = net.minecraft.util.Mth.lerp(pt, p.xRotO, p.getXRot());
        double t;
        if (phase == Phase.CLOSING) {
            float c = (System.nanoTime() - closingAt) / 1e6f / Math.max(1f, closeMs);
            float reached = (float) MapShot.ease(((closingAt - openedAt) / 1e6f - delayMs) / moveMs);
            t = reached * (1 - MapShot.ease(c));
        } else {
            t = MapShot.ease((ageMs() - delayMs) / moveMs);
        }
        MapShot.blend(TMP, SHOT, t, out);
    }

    /** Logout. */
    static void reset() {
        phase = Phase.CLOSED;
        MapCamera.reset();
        HoloScene.map(0f, false);
    }
}
