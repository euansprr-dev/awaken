package dev.awaken.holo.client;

import dev.awaken.holo.ui.HoloTimeline;
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
 *   <li>{@code WRIST}: the same panel while it grows out of the forearm band ({@link Wrist}) and unfolds, then a VIEW;
 *       or, {@link #landOn landed on} a flat screen's rectangle (the inventory), it grows out of the band into exactly
 *       that rectangle and hands over to the flat screen once it is there ({@link #handsOver}).</li>
 *   <li>{@code WORLD}: drawn by {@link HoloOthers} at a fixed spot in the world (the glow over another player's wrist).</li>
 * </ul>
 */
public final class HoloPanel {
    public enum Anchor { VIEW, WRIST, WORLD }

    public final HoloCanvas canvas = new HoloCanvas();
    public final HoloTimeline timeline;
    final Spring spring = new Spring(0.20f, 4f);
    public Anchor anchor = Anchor.WRIST;
    public int w = 320, h = 200;
    public int accent = Glass.EDGE;
    /** Rest centre as a fraction of the half-screen (x right, y up) and distance in metres. */
    public float restX = -0.455f, restY = 0.19f, dist = 0.62f;
    /** Target width as a fraction of the screen: left of the crosshair, its right edge just short of the centre, its
     *  bottom clear of the raised forearm. */
    public float screenFrac = 0.45f;
    /** Pixels a unit added to the size {@link #screenFrac} gives (the Band size setting: -1, 0, +1). */
    public int sizeStep;
    /** Yaw toward the wrist side (the left edge nearer) and lean back (the bottom nearer), degrees. */
    public float turn = 7f, lean = 3f;
    /** In third person the panel moves aside and shrinks so your raised arm stays in view. */
    boolean thirdPerson;
    /** Rests on a flat screen's rectangle instead of the view rest: its top-left corner in GUI pixels, 1 unit = 1 GUI
     *  pixel, parallel to the screen and never trailing (the flat screen doesn't). */
    boolean onScreen;
    float screenX, screenY;
    /** Hands over to a flat screen once landed (the inventory): its own light fades out across {@code landed()} while
     *  opening, and it shows fully again while it folds back after the flat screen has closed. */
    boolean handsOver;
    /** Lower draw layers first (the demo under the K menu). */
    final int layer;

    /** Units → camera-relative world, as last drawn (picking uses it). */
    final Matrix4f pose = new Matrix4f();
    /** The projection in view space: from the wrist emitter to the panel's lower corners; its strength and how wide. */
    final Vector3f beamFrom = new Vector3f(), cornerL = new Vector3f(), cornerR = new Vector3f();
    float beam, beamBoost;
    /** Framebuffer pixels per unit, as last drawn. */
    int ppu = 1;
    boolean drawn;
    // animation inputs for this frame (HoloRender reads them)
    float sx = 1, sy = 1, edgeAlpha = 1, glassAlpha = 1, scan = -1, typeMs = Float.MAX_VALUE, band = 1, fade = 1;
    /** The last click on the panel (units) and how long ago it was, ms (-1: none). */
    float flashX, flashY, flashMs = -1;
    /** A page change replays the scan line (SPEC §30.4): when it started (System.nanoTime, 0 for none). */
    long rescanAt;

    /** Replays the scan line down the panel: the band's page change (the new page prints in behind it). */
    void rescan() {
        rescanAt = System.nanoTime();
    }

    /** How far down the panel the new page has printed, in units ({@link #h} once it has). */
    float revealed() {
        if (rescanAt == 0) return h;
        float ms = (System.nanoTime() - rescanAt) / 1e6f;
        return Math.max(0, Math.min(1, (ms - 40) / 140f)) * h;
    }

    private final Matrix4f m = new Matrix4f();
    private final Vector3f v = new Vector3f(), wrist = new Vector3f();

    public HoloPanel(int layer) {
        this(layer, 1f);
    }

    /** {@code speed}: how fast it plays the K menu's choreography ({@link HoloTimeline#INVENTORY_SPEED}). */
    public HoloPanel(int layer, float speed) {
        this.layer = layer;
        this.timeline = new HoloTimeline(speed);
    }

    /** Rests on the flat screen rectangle whose top-left corner is (x, y) in GUI pixels (see {@link #onScreen}). */
    void landOn(float x, float y) {
        onScreen = true;
        screenX = x;
        screenY = y;
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
        sy = Math.max(0.18f, t.unfoldY(reduced));
        sx = Math.max(0.10f, t.unfoldX(reduced));
        edgeAlpha = t.edges(reduced);
        glassAlpha = t.glass(reduced);
        scan = t.scan(reduced);
        if (rescanAt != 0) {
            // a page change: 40 ms of nothing, then the line sweeps the panel in 140 ms and fades just past the bottom
            float ms = (System.nanoTime() - rescanAt) / 1e6f;
            if (reduced || ms > 200) rescanAt = 0;
            else if (scan < 0 || scan > 1.2f) scan = Math.max(0, (ms - 40) / 140f);
        }
        typeMs = t.typing(reduced);
        band = t.band();
        beam = anchor == Anchor.WRIST ? t.beam(reduced) : 0;
        beamBoost = anchor == Anchor.WRIST ? t.beamBoost(reduced) : 0;
        fade = t.ms() < 150 ? 0 : 1;
        if (handsOver) fade *= reduced ? 0 : t.closing() ? 1 : 1 - t.landed();
    }

    /** Whole framebuffer pixels per unit so the panel is about {@link #screenFrac} of the width and fits the height. */
    private int choosePpu() {
        int byW = Math.round(screenFrac * (thirdPerson ? 0.80f : 1f) * ViewMath.fbW / w);
        int byH = (int) Math.floor(0.80f * ViewMath.fbH / h);
        return Math.max(1, Math.min(byW + sizeStep, byH));
    }

    /**
     * Places the panel for this frame in camera-relative world space ({@link #pose}). At rest it is parallel to the
     * screen (a crisp rectangle), then turned {@link #turn} toward the wrist side and leaned back {@link #lean}, trailing
     * the camera on the spring. While a WRIST panel opens, {@code grow} (0..1) scales the whole panel up out of
     * {@code wristView} (the band's emitter, view space): it comes out of the wrist along the beams into its place.
     */
    void place(float grow, Vector3f wristView) {
        if (onScreen) {
            placeOnScreen(grow, wristView);
            return;
        }
        ppu = choosePpu();
        float unit = ppu * ViewMath.metresPerPixel(dist);
        float rx = thirdPerson ? Math.min(restX, -0.50f) : restX;
        float cx = rx * ViewMath.tanX * dist, cy = restY * ViewMath.tanY * dist;
        v.set(cx, cy, -dist);
        rotateBySpring(v);
        m.identity();
        float g = anchor == Anchor.WRIST ? Math.max(0.02f, Math.min(1f, grow)) : 1f;
        if (g < 1f) m.translate(wristView).scale(g).translate(-wristView.x, -wristView.y, -wristView.z);
        m.translate(v);
        m.rotateY((float) Math.toRadians(-spring.yaw()));
        m.rotateX((float) Math.toRadians(-spring.pitch()));
        m.rotateY((float) Math.toRadians(turn));
        m.rotateX((float) Math.toRadians(-lean));
        m.scale(unit * sx, -unit * sy, unit);
        m.translate(-w / 2f, -h / 2f, 0);
        if (anchor == Anchor.WRIST) {
            beamFrom.set(wristView);
            m.transformPosition(cornerL.set(0, h, 0));
            m.transformPosition(cornerR.set(w, h, 0));
        }
        // view → world, with vanilla's bob undone
        pose.set(ViewMath.VIEW_TO_WORLD).mul(ViewMath.UNBOB).mul(m);
        drawn = true;
    }

    /**
     * {@link #place} for a panel {@link #onScreen}: the plane at {@link #dist} on which a unit is exactly one GUI pixel,
     * so at rest the panel covers the flat screen's rectangle pixel for pixel (the GUI is an orthographic overlay of
     * the same framebuffer: GUI pixel × scale → framebuffer pixel → the level projection's NDC). It unfolds about its
     * centre and grows out of the wrist like any wrist panel.
     */
    private void placeOnScreen(float grow, Vector3f wristView) {
        int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        ppu = scale;
        float unit = scale * ViewMath.metresPerPixel(dist);
        float ndcX = 2f * (screenX + w / 2f) * scale / ViewMath.fbW - 1f;
        float ndcY = 1f - 2f * (screenY + h / 2f) * scale / ViewMath.fbH;
        v.set(ndcX * ViewMath.tanX * dist, ndcY * ViewMath.tanY * dist, -dist);
        m.identity();
        float g = Math.max(0.02f, Math.min(1f, grow));
        if (g < 1f) m.translate(wristView).scale(g).translate(-wristView.x, -wristView.y, -wristView.z);
        m.translate(v);
        m.scale(unit * sx, -unit * sy, unit);
        m.translate(-w / 2f, -h / 2f, 0);
        beamFrom.set(wristView);
        m.transformPosition(cornerL.set(0, h, 0));
        m.transformPosition(cornerR.set(w, h, 0));
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
