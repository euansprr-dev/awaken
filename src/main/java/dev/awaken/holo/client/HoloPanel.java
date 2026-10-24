package dev.awaken.holo.client;

import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * One holo panel: a {@link HoloCanvas} of {@link #w}×{@link #h} units, placed in 3D every frame and drawn by
 * {@link HoloRender}.
 * <ul>
 *   <li>{@code VIEW}: in front of the eye, a little left, facing you with a slight turn toward the centre. It trails the
 *       camera through a {@link Spring}. Its size is set in framebuffer pixels per unit (a whole number, so text stays
 *       crisp) from the real projection each frame, so FOV changes never swell it; vanilla's bob is undone.</li>
 *   <li>{@code WRIST}: the same panel while it leaves the forearm band ({@link Wrist}) and unfolds, then a VIEW.</li>
 *   <li>{@code WORLD}: drawn by {@link HoloOthers} at a fixed spot in the world (the glow over another player's wrist).</li>
 * </ul>
 */
public final class HoloPanel {
    public enum Anchor { VIEW, WRIST, WORLD }

    public final HoloCanvas canvas = new HoloCanvas();
    public final HoloTimeline timeline = new HoloTimeline();
    final Spring spring = new Spring(0.20f, 4f);
    public Anchor anchor = Anchor.WRIST;
    public int w = 320, h = 200;
    public int accent = Glass.EDGE;
    /** Rest centre as a fraction of the half-screen (x right, y up) and distance in metres. */
    public float restX = -0.10f, restY = 0.05f, dist = 0.62f;
    /** Target width as a fraction of the screen. */
    public float screenFrac = 0.62f;
    /** Extra turn toward the centre and lean back, degrees. */
    public float turn = 2.5f, lean = 2f;
    /** Lower draw layers first (the demo under the K menu). */
    final int layer;

    /** Units → camera-relative world, as last drawn (picking uses it). */
    final Matrix4f pose = new Matrix4f();
    /** Framebuffer pixels per unit, as last drawn. */
    int ppu = 1;
    boolean drawn;
    // animation inputs for this frame (HoloRender reads them)
    float sx = 1, sy = 1, edgeAlpha = 1, glassAlpha = 1, scan = -1, typeMs = Float.MAX_VALUE, band = 1, fade = 1;
    /** The last click on the panel (units) and how long ago it was, ms (-1: none). */
    float flashX, flashY, flashMs = -1;

    private final Matrix4f m = new Matrix4f();
    private final Vector3f v = new Vector3f(), wrist = new Vector3f();

    public HoloPanel(int layer) {
        this.layer = layer;
    }

    /** Animation values for this frame, from the timeline (or all 1 for a plain VIEW panel). */
    void animate(boolean reduced) {
        HoloTimeline t = timeline;
        if (anchor == Anchor.VIEW && !t.active()) {
            sx = sy = edgeAlpha = glassAlpha = fade = 1;
            scan = -1;
            typeMs = Float.MAX_VALUE;
            return;
        }
        sy = Math.max(0.02f, t.unfoldY(reduced));
        sx = Math.max(0.035f, t.unfoldX(reduced));
        edgeAlpha = t.edges(reduced);
        glassAlpha = t.glass(reduced);
        scan = t.scan(reduced);
        typeMs = t.typing(reduced);
        band = t.band();
        fade = t.ms() < 150 ? 0 : 1;
    }

    /** Whole framebuffer pixels per unit so the panel is about {@link #screenFrac} of the width and fits the height. */
    private int choosePpu() {
        int byW = Math.round(screenFrac * ViewMath.fbW / w);
        int byH = (int) Math.floor(0.80f * ViewMath.fbH / h);
        return Math.max(1, Math.min(byW, byH));
    }

    /**
     * Places the panel for this frame in camera-relative world space ({@link #pose}). {@code leave} is 0 at the wrist
     * and 1 at rest; {@code wristView} is the band in view space (world projection).
     */
    void place(float leave, Vector3f wristView) {
        ppu = choosePpu();
        float unit = ppu * ViewMath.metresPerPixel(dist);
        // rest centre in view space and the turn that faces it to the eye
        float cx = restX * ViewMath.tanX * dist, cy = restY * ViewMath.tanY * dist;
        float face = (float) Math.atan2(-cx, dist), tilt = (float) Math.atan2(cy, dist);
        float l = anchor == Anchor.WRIST ? Math.max(0f, Math.min(1f, leave)) : 1f;
        // the centre trails the camera on the spring; while leaving the wrist it slides from the band to there,
        // starting laid back along the forearm and standing up as it goes
        v.set(cx, cy, -dist);
        rotateBySpring(v);
        if (l < 1f) v.set(wrist.set(wristView).lerp(v, l));
        m.identity().translate(v);
        m.rotateY((float) Math.toRadians(-spring.yaw()));
        m.rotateX((float) Math.toRadians(-spring.pitch()));
        m.rotateY((face + (float) Math.toRadians(turn)) * l);
        m.rotateX((tilt - (float) Math.toRadians(lean)) * l - (float) Math.toRadians(70) * (1 - l));
        m.scale(unit * sx, -unit * sy, unit);
        m.translate(-w / 2f, -h / 2f, 0);
        // view → world, with vanilla's bob undone
        pose.set(ViewMath.VIEW_TO_WORLD).mul(ViewMath.UNBOB).mul(m);
        drawn = true;
    }

    private void rotateBySpring(Vector3f p) {
        p.rotateX((float) Math.toRadians(-spring.pitch()));
        p.rotateY((float) Math.toRadians(-spring.yaw()));
    }

    /** Panel units → GUI coordinates on screen, through the same matrices {@link #pick} inverts. */
    boolean project(float px, float py, float[] out) {
        if (!drawn) return false;
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(inv);
        Vector3f wp = pose.transformPosition(px, py, 0, o);
        inv.transform(ray.set(wp.x, wp.y, wp.z, 1f));
        if (ray.w <= 1e-6f) return false;
        int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
        out[0] = (ray.x / ray.w + 1) * 0.5f * gw;
        out[1] = (1 - ray.y / ray.w) * 0.5f * gh;
        return true;
    }

    // ---- picking --------------------------------------------------------------------------------------------------

    private final Matrix4f inv = new Matrix4f();
    private final Vector4f ray = new Vector4f();
    private final Vector3f o = new Vector3f(), u = new Vector3f(), vv = new Vector3f(), n = new Vector3f(), hit = new Vector3f(), nrm = new Vector3f();

    /**
     * The panel point under a screen position (GUI coordinates), through the inverse of the camera's view-rotation-
     * projection matrix and the panel's plane as last drawn. Writes units into {@code out} (x, y); false if the ray
     * misses the plane.
     */
    boolean pick(double guiX, double guiY, float[] out) {
        if (!drawn) return false;
        Minecraft mc = Minecraft.getInstance();
        int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
        float nx = (float) (2 * guiX / gw - 1), ny = (float) (1 - 2 * guiY / gh);
        mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(inv).invert();
        inv.transform(ray.set(nx, ny, 0.5f, 1f));
        if (Math.abs(ray.w) < 1e-9f) return false;
        Vector3f dir = n.set(ray.x / ray.w, ray.y / ray.w, ray.z / ray.w).normalize();
        float dx = dir.x, dy = dir.y, dz = dir.z;
        pose.transformPosition(o.set(0, 0, 0));
        pose.transformPosition(u.set(1, 0, 0)).sub(o);
        pose.transformPosition(vv.set(0, 1, 0)).sub(o);
        Vector3f normal = nrm.set(u).cross(vv);
        float denom = normal.x * dx + normal.y * dy + normal.z * dz;
        if (Math.abs(denom) < 1e-9f) return false;
        float t = normal.dot(o) / denom;
        if (t <= 0) return false;
        hit.set(dx * t, dy * t, dz * t).sub(o);
        out[0] = hit.dot(u) / u.lengthSquared();
        out[1] = hit.dot(vv) / vv.lengthSquared();
        return true;
    }
}
