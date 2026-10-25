package dev.awaken.holo.client;

import dev.awaken.holo.ui.PlateScale;
import dev.awaken.signal.client.Glass;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;

/**
 * Holo glass in the world (SPEC §12, client only): a small plate at a world position, facing the camera, drawn by
 * {@link HoloRender} exactly as it draws a K-menu panel. It's the same {@link HoloPanel} and {@link HoloCanvas}: the
 * glass shader's sheet, inner glow, hairline rim, corner brackets and scan lines, the edge glow and halo, the scan line,
 * and text as see-through light with its glow. Nothing is forked, so a plate always matches the panels.
 * <p>
 * Use: fill a reused {@link Spec} and call {@link #plate} during {@code ExtractLevelRenderStateEvent}. The plate is drawn
 * in that frame's level pass, then forgotten. A plate keeps its world size up close; further off it grows so that a
 * unit never falls below {@link PlateScale#MIN_PX} framebuffer pixels at 1080 lines, so text (9 units tall) stays readable at any
 * distance. It writes depth like the panels (Lens keeps fog and AO off it), draws over walls like them, and never
 * counts toward {@code UiHooks.focus}. Plates are off when the holo shaders didn't load or reduced motion is on.
 * <p>
 * Frame cost: per plate, three custom-geometry submits (halo, glass, edges) and five text submits per line (the line and
 * four glow taps). Nothing is allocated once a slot has drawn its lines, and a line's text record is rebuilt only when its
 * position, colour or scale changes. At most {@link #MAX} plates a frame.
 */
public final class HoloWorld {
    private HoloWorld() {}

    public static final int MAX = 6;

    /** What a plate shows: write its fills and text into the canvas, in units (0..w, 0..h, y down). */
    @FunctionalInterface
    public interface Content {
        void write(HoloCanvas canvas);
    }

    /** A plate's placement and look. Keep one and refill it each call: {@link #plate} copies what it needs. */
    public static final class Spec {
        /** The world point of the plate's bottom centre. */
        public double x, y, z;
        /** Size in units. */
        public int w = 120, h = 24;
        /** 0..1: the whole plate fades with it. */
        public float alpha = 1;
        /** The rim, brackets and glow colour (rgb). */
        public int accent = Glass.EDGE;
        /** The panels' scan line, 0..1.15 top to bottom then fading; below 0 none. */
        public float scan = -1;
    }

    private static final HoloRender.Draw[] SLOTS = new HoloRender.Draw[MAX];
    private static final double[] AT = new double[MAX * 3];
    private static int count;

    static {
        for (int i = 0; i < MAX; i++) {
            HoloPanel p = new HoloPanel(3 + i); // above the demo, Status and inventory layers
            p.anchor = HoloPanel.Anchor.WORLD;
            SLOTS[i] = new HoloRender.Draw(p);
        }
    }

    /** Whether plates draw at all right now. */
    public static boolean on() {
        return allowed() && HoloPipelines.ok();
    }

    /** Whether world plates are wanted at all: reduced motion turns them off (a fallback should stay off too). */
    public static boolean allowed() {
        return !HoloConfig.reducedMotion;
    }

    /** Queues a plate for this frame. False when plates are off, the frame is full, or it's faded out. */
    public static boolean plate(Spec s, Content content) {
        if (count >= MAX || s.alpha <= 0.02f || !on()) return false;
        HoloPanel p = SLOTS[count].p;
        p.w = Math.max(8, s.w);
        p.h = Math.max(8, s.h);
        p.accent = s.accent & 0xFFFFFF;
        p.glassAlpha = s.alpha;
        p.edgeAlpha = s.alpha;
        p.fade = 1;
        p.sx = p.sy = 1;
        p.band = 1;
        p.beam = 0;
        p.scan = s.scan;
        p.typeMs = Float.MAX_VALUE;
        p.flashMs = -1;
        p.canvas.begin(p.w, p.h);
        content.write(p.canvas);
        AT[count * 3] = s.x;
        AT[count * 3 + 1] = s.y;
        AT[count * 3 + 2] = s.z;
        count++;
        return true;
    }

    /** The level pass: place each queued plate facing the camera and draw it as a panel; then the queue empties. */
    static void submit(SubmitCustomGeometryEvent e) {
        int n = count;
        count = 0;
        if (n == 0 || !HoloPipelines.ok()) return;
        var cam = e.getLevelRenderState().cameraRenderState;
        Vec3 c = cam.pos;
        Quaternionf q = cam.orientation;
        for (int i = 0; i < n; i++) {
            HoloRender.Draw d = SLOTS[i];
            HoloPanel p = d.p;
            float dx = (float) (AT[i * 3] - c.x), dy = (float) (AT[i * 3 + 1] - c.y), dz = (float) (AT[i * 3 + 2] - c.z);
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float unit = PlateScale.unit(dist, ViewMath.tanY, ViewMath.fbH);
            // as vanilla's name tags: the camera's rotation, y flipped (units run down), the bottom centre on the point
            p.pose.identity().translate(dx, dy, dz).rotate(q).scale(unit, -unit, unit).translate(-p.w / 2f, -p.h, 0);
            p.drawn = true;
            HoloRender.submitPlate(e.getSubmitNodeCollector(), d);
        }
    }

    /** Plates queued so far this frame (tests). */
    static int queued() {
        return count;
    }

    /** Drops the queue (tests, logout). */
    static void clear() {
        count = 0;
    }
}
