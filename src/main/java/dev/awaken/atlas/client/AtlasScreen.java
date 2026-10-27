package dev.awaken.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.atlas.AtlasNet;
import dev.awaken.atlas.Bells;
import dev.awaken.atlas.Discovery;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.MapMarks;
import dev.awaken.core.api.UiHooks;
import dev.awaken.holo.HoloSounds;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * The Atlas table view (SPEC §16.8): the world as a model on the Holo's glass. The land prints in from under your feet
 * when it opens, under clouds wherever you haven't been. Drag to orbit and tilt, right-drag to pan, scroll to zoom,
 * C to come back to yourself, M or Esc to close. Markers are only what you know: you, Carillon's districts once seen,
 * and every place other modules mark ({@code MapMarks}) in a cell you've explored. The first open after the opening
 * plays the Registry reveal: the run you made draws itself across the city.
 * <p>
 * In the map scene ({@link MapScene}, SPEC §30.5) the same table is the band's projection: a card in front of you while
 * the camera stands over your shoulder, the legend on its own glass beside it, the world dimmed behind. Keys move the
 * map while your body stays still (WASD, Q/E turn, R/F tilt, +/- zoom); Tab steps through the gates you know and T
 * points the Now line at the one picked.
 */
public final class AtlasScreen extends Screen implements dev.awaken.signal.client.QuietScreen {
    private static final int MARGIN = 12;
    private static final int INK = 0xFFE9F1FF, BRONZE = 0xFFE0A86A;
    /** Blocks across the table when it opens. */
    static final float OPEN_SPAN = 420f;

    float yaw, tilt, zoom;
    double cx, cy, cz;
    float spin;
    boolean frozen;
    private float zoomScale;
    private long lastNanos = System.nanoTime();
    private final long opened = System.nanoTime();
    private final Matrix4f gui = new Matrix4f();
    private final Vector3f v = new Vector3f();
    private boolean reveal;
    private int drawLod = -1;
    private long lodSince;
    // the legend: where it sits (names keep clear of it), its rows (for clicks), and a flight to the row picked
    private int legendX0, legendY0, legendX1, legendY1;
    private final java.util.List<AtlasState.ZoneLabel> legendRows = new java.util.ArrayList<>();
    private int legendRowY0;
    private double flyX = Double.NaN, flyZ;
    static final int LEGEND_W = 146, ROW = 12, LEGEND_ROWS = 6;
    /** L shows or hides the legend; kept for the session. */
    private static boolean showLegend = true;
    /** Profiling (dev: {@code /atlas prof <bits>}): parts of the table switched off to measure what each costs. */
    static int prof;
    static final int PROF_NO_CARD = 1, PROF_PLAIN_TEXT = 2, PROF_NO_OVERLAY = 4, PROF_NO_FOCUS = 8, PROF_NO_CLOUDS = 16,
            PROF_NO_LAND = 32, PROF_NO_PIP = 64;
    // the zone chips drawn this frame and their centres, so the hover doesn't repeat one that's right there
    private final int[] placedZones = new int[64], placedAt = new int[128];
    private int placedCount;
    private long lastInput;
    private static final int BOXES = 96;
    private final int[] regionBoxes = new int[BOXES * 4];

    /** Keeps a box (x0, y0, x1, y1) that names must stay clear of; returns the new count. */
    private int box(int n, int x0, int y0, int x1, int y1) {
        if (n >= BOXES) return n;
        regionBoxes[n * 4] = x0;
        regionBoxes[n * 4 + 1] = y0;
        regionBoxes[n * 4 + 2] = x1;
        regionBoxes[n * 4 + 3] = y1;
        return n + 1;
    }

    private boolean free(int n, int x0, int y0, int x1, int y1) {
        for (int k = 0; k < n; k++)
            if (x1 >= regionBoxes[k * 4] && x0 <= regionBoxes[k * 4 + 2] && y1 >= regionBoxes[k * 4 + 1] && y0 <= regionBoxes[k * 4 + 3]) return false;
        return true;
    }
    private int wantX = Integer.MIN_VALUE, wantZ;
    private int shareX0 = -1, shareX1 = -1, buttonsY;
    /** The band's projection in the map scene (SPEC §30.5), not the full-screen table. */
    final boolean scene;
    // the gates in the legend (nearest first), the one picked (Tab, a click), and its "Point me there" row
    private final java.util.List<MapMarks.Gate> gateRows = new java.util.ArrayList<>();
    private int gateRowY0, pointY = -1000;
    private @org.jspecify.annotations.Nullable BlockPos chosen;
    static final int GATE_ROWS = 4;
    // keys held down (the map moves while they are)
    private int held;
    private static final int K_FWD = 1, K_BACK = 2, K_LEFT = 4, K_RIGHT = 8, K_TURN_L = 16, K_TURN_R = 32, K_TILT_UP = 64,
            K_TILT_DOWN = 128, K_ZOOM_IN = 256, K_ZOOM_OUT = 512;

    AtlasScreen(float yaw, float tilt, float zoomScale) {
        this(yaw, tilt, zoomScale, false);
    }

    AtlasScreen(float yaw, float tilt, float zoomScale, boolean scene) {
        super(Component.translatable("screen.awaken.atlas"));
        this.scene = scene;
        Minecraft mc = Minecraft.getInstance();
        this.yaw = yaw;
        this.tilt = tilt;
        this.zoomScale = zoomScale;
        var p = mc.player;
        cx = p.getX();
        cz = p.getZ();
        cy = AtlasState.get().store.groundY(cx, cz, p.getY());
    }

    /** Opens the table from where the player stands (the M key, the glance, a dev command). */
    static void open(float yaw, float tilt, float zoomScale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        AtlasState.stats.reset();
        AtlasState.stats.openStart = System.nanoTime();
        if (!Discovery.mapped(AtlasState.get().dimension())) {
            mc.player.sendOverlayMessage(Component.translatable("atlas.awaken.closed"));
            return;
        }
        AtlasScreen s = new AtlasScreen(yaw, tilt, zoomScale);
        AtlasState a = AtlasState.get();
        if (a.revealPending && a.trail.length >= 6) s.startReveal(a);
        a.revealPending = false;
        mc.gui.setScreen(s);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.OPEN.get(), 1.0f, 0.8f));
    }

    /** The map scene's card (its camera and sound are {@link MapScene}'s): up the map is the way the camera looks. */
    static void openScene(float yaw) {
        Minecraft mc = Minecraft.getInstance();
        AtlasState.stats.reset();
        AtlasState.stats.openStart = System.nanoTime();
        AtlasState.get().revealPending = false;
        mc.gui.setScreen(new AtlasScreen(yaw, 54f, 1.3f, true)); // the card is narrower than the table: a little closer
    }

    /** The Registry reveal: high over the city, the run's thread drawing itself from start to finish. */
    private void startReveal(AtlasState a) {
        int[] t = a.trail;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i + 2 < t.length; i += 3) {
            x0 = Math.min(x0, t[i]);
            x1 = Math.max(x1, t[i]);
            z0 = Math.min(z0, t[i + 2]);
            z1 = Math.max(z1, t[i + 2]);
        }
        cx = (x0 + x1) / 2;
        cz = (z0 + z1) / 2;
        cy = a.store.groundY(cx, cz, cy);
        double span = Math.max(160, Math.max(x1 - x0, z1 - z0) * 1.6);
        zoomScale = (float) (OPEN_SPAN / span);
        tilt = 60f;
        yaw = 20f;
        a.trailStart = System.nanoTime() + 900_000_000L; // after the land has printed in
        reveal = true;
    }

    // the table fills the screen; the scene's card stands beside you, on the far side of the frame from your shoulder
    // (the card ends above and right of your raised wrist, so the beams show between them)
    private int px0() { return scene ? (MapScene.side() > 0 ? Math.round(width * 0.335f) : Math.round(width * 0.025f)) : MARGIN; }
    private int py0() { return scene ? Math.round(height * 0.075f) : MARGIN + 14; }
    private int px1() { return scene ? (MapScene.side() > 0 ? Math.round(width * 0.975f) : Math.round(width * 0.665f)) : width - MARGIN; }
    private int py1() { return scene ? Math.round(height * 0.68f) : height - MARGIN - 14; }
    /** The scene's legend glass: over your head, on the near side. */
    private int sideX0() { return MapScene.side() > 0 ? Math.round(width * 0.02f) : Math.round(width * 0.685f); }
    private int sideX1() { return MapScene.side() > 0 ? Math.round(width * 0.315f) : Math.round(width * 0.98f); }

    @Override
    protected void init() {
        if (zoom == 0) zoom = (px1() - px0()) / OPEN_SPAN * zoomScale;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // no blur: Lens dims the world behind the table through UiHooks.focus
    }

    private float age() {
        return (System.nanoTime() - opened) / 1e9f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        AtlasState atlas = AtlasState.get();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        yaw += spin * dt;
        if (!Double.isNaN(flyX)) { // gliding to a zone picked in the legend
            float k = Math.min(1f, dt * 6f);
            cx += (flyX - cx) * k;
            cz += (flyZ - cz) * k;
            cy = atlas.store.groundY(cx, cz, cy);
            if (Math.abs(flyX - cx) + Math.abs(flyZ - cz) < 0.5) flyX = Double.NaN;
        }
        float age = age();
        if ((prof & PROF_NO_FOCUS) == 0) UiHooks.setFocus(this, Math.min(1f, age / 0.25f));
        else UiHooks.clearFocus(this);
        if (held != 0) keysHeld(dt);
        // zooming across a level: keep drawing the level we have until the new one is built round the view (it builds
        // on the worker meanwhile), or the land blinks out for a moment
        int want = AtlasMesh.lodFor(zoom);
        if (drawLod < 0) drawLod = want;
        if (want != drawLod) {
            if (lodSince == 0) lodSince = now;
            int waiting = atlas.mesh.update(cx, cz, want, Discovery.CLIENT, now);
            if (waiting == 0 || now - lodSince > 900_000_000L) {
                drawLod = want;
                lodSince = 0;
            }
        } else lodSince = 0;
        int lod = drawLod;
        int sx = (int) Math.floor(cx) >> 8, sz = (int) Math.floor(cz) >> 8;
        if (sx != wantX || sz != wantZ) { // panned somewhere new: read more of the disk cache
            wantX = sx;
            wantZ = sz;
            atlas.want(cx, cz);
        }
        float night = mc.level == null ? 0f : AtlasState.night(mc.level);
        int x0 = px0(), y0 = py0(), x1 = px1(), y1 = py1();
        // the scene: the band's beams reach the card at 120 ms and it scans in by 420 (SPEC §30.5)
        float reveal = scene ? Math.clamp((age - 0.12f) / 0.30f, 0f, 1f) : Math.min(1f, age / 0.35f);
        if (scene) veil(g, age);
        // the K panel's projected glass: frame, corner brackets, a slow interference band; scans in as it opens
        if ((prof & PROF_NO_CARD) == 0) HudLook.card(g, x0, y0, x1 - x0, y1 - y0, Glass.EDGE, scene ? 0.80f : 0.92f, reveal, 0f, HudLook.FRAME | HudLook.DRIFT);
        else g.fill(x0, y0, x1, y1, 0xE00B1626);
        float time = frozen ? 0f : (now - opened) / 1e9f;
        boolean moving = !frozen || spin != 0f || age < 1.5f || atlas.trailProgress() < 1f || atlas.trailGlow() < 1f && atlas.trailGlow() > 0.35f;
        // the scene's land prints in quicker, once the card is there
        float printAge = scene ? Math.max(0f, (age - 0.12f) * 1.6f) : age;
        if ((prof & PROF_NO_PIP) == 0 && (!scene || age > 0.12f))
            g.submitPictureInPictureRenderState(new AtlasRenderState(x0, y0, x1, y1, yaw, tilt, zoom, cx, cy, cz, time, night,
                    moving, lod, printAge, 0, g.peekScissorStack()));
        if (scene) {
            edges(g, x0, y0, x1, y1, reveal);
            beams(x0, y0, x1, y1);
        }
        if ((prof & PROF_NO_OVERLAY) == 0) {
            legendLayout(mc, atlas, x0, y0, lod);
            float marks = scene ? 0.30f : 0.45f;
            if (age > marks) markers(g, mc, atlas, x0, y0, x1, y1, lod);
            if (age > marks) {
                legend(g, mc, atlas, mouseX, mouseY);
                hover(g, mc, atlas, mouseX, mouseY, x0, y0, x1, y1);
            }
        }
        chrome(g, mc, x0, y0, x1, y1, mouseX, mouseY);
        AtlasState.stats.extract(System.nanoTime() - now);
        super.extractRenderState(g, mouseX, mouseY, pt);
    }

    private void chrome(GuiGraphicsExtractor g, Minecraft mc, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        String where = place(mc);
        String count = String.format(Locale.ROOT, "%,d places known", Discovery.CLIENT.count());
        int room = x1 - x0 - font.width(count) - 20;
        String title = "ATLAS" + (where == null ? "" : "  ·  " + where.toUpperCase(Locale.ROOT));
        if (font.width(title) > room) title = font.plainSubstrByWidth(title, room - font.width("…")) + "…";
        int ty = scene ? y0 - 11 : MARGIN;
        text(g, title, x0 + 2, ty, INK, false);
        text(g, count, x1 - font.width(count) - 2, ty, Glass.LABEL | 0xFF000000, false);
        // compass: north turns with the table
        int ccx = x1 - 18, ccy = y0 + 18;
        double r = Math.toRadians(yaw);
        int nx = ccx + (int) Math.round(-Math.sin(r) * 9), ny = ccy - (int) Math.round(Math.cos(r) * 9 * Math.sin(Math.toRadians(tilt)));
        g.fill(ccx - 1, ccy - 1, ccx + 1, ccy + 1, 0x667FC3FF);
        text(g, "N", nx - 2, ny - 4, 0xFF7FC3FF, false);
        // one line of keys along the bottom, and "Share with party" when there's anyone to share with (the Atlas
        // cache's clearing lives in the settings now: SPEC §30)
        buttonsY = height - MARGIN - 8;
        String share = "Share with party";
        boolean others = mc.getConnection() != null && mc.getConnection().getOnlinePlayers().size() > 1;
        shareX0 = shareX1 = -1;
        int right = x1 - 2;
        if (others) {
            shareX1 = right;
            shareX0 = shareX1 - font.width(share);
            right = shareX0 - 14;
        }
        String[] hints = scene
                ? new String[]{"drag turn  ·  right-drag or WASD move  ·  scroll zoom  ·  Tab gates  ·  T point me there  ·  C you  ·  Shift+M table  ·  M close",
                        "drag turn  ·  WASD move  ·  scroll zoom  ·  Tab gates  ·  T point me there  ·  C you  ·  M close",
                        "drag · WASD · scroll · Tab · T · C · M close"}
                : new String[]{"drag orbit  ·  right-drag pan  ·  scroll zoom  ·  double-click pin  ·  Tab gates  ·  C you  ·  L legend",
                        "drag orbit  ·  right-drag pan  ·  scroll zoom  ·  double-click pin  ·  C you",
                        "drag orbit  ·  right-drag pan  ·  scroll zoom  ·  C you", "drag · right-drag · scroll · C"};
        int left = scene ? Math.round(width * 0.025f) : x0 + 2;
        for (String hint : hints) {
            if (left + font.width(hint) < right - 12) {
                // the scene centres its line under the whole frame
                int hx = scene && !others ? (width - font.width(hint)) / 2 : left;
                text(g, hint, hx, buttonsY, Glass.LABEL | 0xFF000000, false);
                break;
            }
        }
        if (others) {
            boolean overShare = mouseY >= buttonsY - 2 && mouseY < buttonsY + 10 && mouseX >= shareX0 && mouseX < shareX1;
            text(g, share, shareX0, buttonsY, overShare ? 0xFFFFFFFF : 0xFF7FC3FF, false);
        }
    }

    // ---- the scene's dressing (SPEC §30.5) -----------------------------------------------------------------------------

    /** The world dims behind the projection (Lens also dims it through the focus): darker at the top, where sky may be. */
    private void veil(GuiGraphicsExtractor g, float age) {
        float a = Math.min(1f, age / 0.30f);
        g.fillGradient(0, 0, width, height, Glass.a(0x050C18, 0.42f * a), Glass.a(0x050C18, 0.18f * a));
    }

    /** The card's cut edge fades into its glass, so the land reads as a projection, not a rectangle (smooth: steps showed over cloud). */
    private void edges(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float reveal) {
        int band = 14, c = Glass.a(0x0B1626, 0.62f * reveal), clear = Glass.a(0x0B1626, 0f);
        g.fillGradient(x0, y0, x1, y0 + band, c, clear);
        g.fillGradient(x0, y1 - band, x1, y1, clear, c);
        // the sides: the same gradient turned a quarter (local x runs along the edge, local y inward)
        g.pose().pushMatrix();
        g.pose().translate(x0, y1);
        g.pose().rotate((float) (-Math.PI / 2));
        g.fillGradient(0, 0, y1 - y0, band, c, clear);
        g.pose().popMatrix();
        g.pose().pushMatrix();
        g.pose().translate(x1, y0);
        g.pose().rotate((float) (Math.PI / 2));
        g.fillGradient(0, 0, y1 - y0, band, c, clear);
        g.pose().popMatrix();
    }

    /** Where the band's beams land: two points along the card's near bottom edge, so the light fans up into the map. */
    private void beams(int x0, int y0, int x1, int y1) {
        boolean right = MapScene.side() > 0;
        float w = x1 - x0, ax = right ? x0 + w * 0.06f : x1 - w * 0.06f, bx = right ? x0 + w * 0.30f : x1 - w * 0.30f;
        float ny = 1f - (y1 - 2) / (float) height * 2f;
        dev.awaken.holo.client.HoloScene.beams(ax / width * 2f - 1f, ny, bx / width * 2f - 1f, ny, 4.5f);
    }

    /** The district or "Carillon" under the table's centre, if known. */
    private String place(Minecraft mc) {
        if (mc.level == null) return null;
        BlockPos at = BlockPos.containing(cx, cy, cz);
        BlockPos sq = CityHooks.square(mc.level);
        if (sq == null || !Discovery.CLIENT.explored(at.getX() >> 4, at.getZ() >> 4)) return null;
        double dx = cx - sq.getX(), dz = cz - sq.getZ();
        if (dx * dx + dz * dz > 260 * 260) return null;
        Bells.District best = null;
        double bestD = Double.MAX_VALUE;
        for (Bells.District d : Bells.districts()) {
            double ddx = d.x() - dx, ddz = d.z() - dz, dd = ddx * ddx + ddz * ddz;
            if (dd < bestD) {
                bestD = dd;
                best = d;
            }
        }
        return best == null ? "Carillon" : "Carillon  ·  " + best.name();
    }

    private void markers(GuiGraphicsExtractor g, Minecraft mc, AtlasState atlas, int x0, int y0, int x1, int y1, int lod) {
        float w = x1 - x0, h = y1 - y0;
        AtlasRenderState.view(gui, w, h, zoom, yaw, tilt);
        g.enableScissor(x0, y0, x1, y1);
        // (SPEC §28.9) the roads you've walked, drawn on the land you know, under everything else
        if (mc.player != null) roads(g, atlas, MapMarks.lines(mc.player), x0, y0);
        // first what stands on the table (you, pins, marks): names keep clear of them
        int placed = 0;
        placedCount = 0;
        if (mc.player != null && project(mc.player.getX(), atlas.store.groundY(mc.player.getX(), mc.player.getZ(), mc.player.getY()), mc.player.getZ(), x0, y0))
            placed = box(placed, Math.round(v.x) - 12, Math.round(v.y) - 30, Math.round(v.x) + 12, Math.round(v.y) + 12);
        for (dev.awaken.atlas.AtlasNet.PinView pin : atlas.pins)
            if (project(pin.x() + 0.5, pin.y() + 1, pin.z() + 0.5, x0, y0))
                placed = box(placed, Math.round(v.x) - 7, Math.round(v.y) - 26, Math.round(v.x) + 7, Math.round(v.y) + 4);
        if (mc.player != null) for (MapMarks.Mark m : MapMarks.collect(mc.player)) {
            BlockPos p = m.pos();
            if (!m.label().startsWith("? ") && !Discovery.CLIENT.explored(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (project(p.getX() + 0.5, atlas.store.groundY(p.getX(), p.getZ(), p.getY()), p.getZ() + 0.5, x0, y0))
                placed = box(placed, Math.round(v.x) - 8, Math.round(v.y) - 8, Math.round(v.x) + 8, Math.round(v.y) + 8);
        }
        if (mc.player != null) for (MapMarks.Gate gate : MapMarks.gates(mc.player))
            if (project(gate.pos().getX() + 0.5, gateY(atlas, gate), gate.pos().getZ() + 0.5, x0, y0))
                placed = box(placed, Math.round(v.x) - 40, Math.round(v.y) - PILLAR - 26, Math.round(v.x) + 40, Math.round(v.y) + 2);
        placed = box(placed, legendX0, legendY0, legendX1, legendY1); // the legend's corner of the table
        // every zone you know, named on its own known land (never out in the clouds): a chip in its colour with its
        // levels or its city under the name. Districts close in; regions at every zoom. Largest first, so when two
        // would overlap the bigger one keeps its place and the other moves or waits for a closer zoom
        for (AtlasState.ZoneLabel z : atlas.zoneLabels()) {
            boolean district = z.zone() >= ZoneMap.DISTRICT;
            if (district && lod != 0) continue;
            if (!project(z.x(), atlas.store.groundY(z.x(), z.z(), cy) + 4, z.z(), x0, y0)) continue;
            String name = z.name().toUpperCase(Locale.ROOT);
            int cw = Math.max(font.width(name), font.width(z.sub())) + 14, ch = 22, sy = Integer.MIN_VALUE;
            int sx = Math.clamp(Math.round(v.x) - cw / 2, x0 + 4, Math.max(x0 + 4, x1 - 4 - cw));
            for (int dy : new int[]{0, -26, 26, -52, 52}) {
                int y = Math.clamp(Math.round(v.y) - ch / 2 + dy, y0 + 4, Math.max(y0 + 4, y1 - 4 - ch));
                if (free(placed, sx, y, sx + cw, y + ch)) {
                    sy = y;
                    break;
                }
            }
            if (sy == Integer.MIN_VALUE || placed >= BOXES) continue;
            placed = box(placed, sx - 2, sy - 2, sx + cw + 2, sy + ch + 2);
            chip(g, name, z.sub(), z.colour(), sx, sy, cw, ch, 0.92f);
            placedZones[placedCount] = z.zone();
            placedAt[placedCount * 2] = sx + cw / 2;
            placedAt[placedCount * 2 + 1] = sy + ch / 2;
            if (placedCount < placedZones.length - 1) placedCount++;
        }
        if (mc.player != null) {
            for (MapMarks.Mark m : MapMarks.collect(mc.player)) {
                BlockPos p = m.pos();
                if (m.label().startsWith("? ")) { // a rumour: you've heard of it, not seen it, so it hangs in the clouds
                    if (project(p.getX() + 0.5, atlas.store.groundY(p.getX(), p.getZ(), 64) + 34, p.getZ() + 0.5, x0, y0)) rumour(g, m.label().substring(2));
                    continue;
                }
                if (!Discovery.CLIENT.explored(p.getX() >> 4, p.getZ() >> 4)) continue; // only what you know
                if (!project(p.getX() + 0.5, atlas.store.groundY(p.getX(), p.getZ(), p.getY()), p.getZ() + 0.5, x0, y0)) continue;
                int sx = Math.round(v.x), sy = Math.round(v.y), c = 0xFF000000 | m.colour();
                diamond(g, sx, sy, 4, c, m.visited());
                if (zoom > 0.6f) label(g, m.label(), sx, sy - 14, c);
            }
            // the gates you know: a pillar of each one's light, over the land or riding the clouds (SPEC §30)
            for (MapMarks.Gate gate : MapMarks.gates(mc.player))
                if (project(gate.pos().getX() + 0.5, gateY(atlas, gate), gate.pos().getZ() + 0.5, x0, y0))
                    pillar(g, Math.round(v.x), Math.round(v.y), gate);
            // your party's pins (and yours, ringed), standing on the table
            for (dev.awaken.atlas.AtlasNet.PinView pin : atlas.pins) {
                if (!project(pin.x() + 0.5, pin.y() + 1, pin.z() + 0.5, x0, y0)) continue;
                int sx = Math.round(v.x), sy = Math.round(v.y), c = pin.colour();
                g.fill(sx, sy - 16, sx + 1, sy, 0xFF0B1626);
                g.fill(sx - 1, sy - 16, sx, sy, c);
                g.fill(sx - 3, sy - 21, sx + 3, sy - 15, 0xFF0B1626);
                g.fill(sx - 2, sy - 20, sx + 2, sy - 16, c);
                if (pin.mine()) ring(g, sx, sy - 18, 5, 0xE0E9F1FF);
                if (zoom > 0.5f && !pin.mine()) label(g, pin.owner(), sx, sy - 32, c);
            }
            double px = mc.player.getX(), pz = mc.player.getZ();
            if (project(px, atlas.store.groundY(px, pz, mc.player.getY()), pz, x0, y0)) {
                int sx = Math.round(v.x), sy = Math.round(v.y);
                float pulse = 0.5f + 0.5f * (float) Math.sin(age() * 3.0);
                // a pin of light standing on the roofs, so you're found at a glance on the busiest street
                g.fillGradient(sx, sy - 22, sx + 1, sy, 0x00E9F1FF, 0xF0E9F1FF);
                g.fill(sx - 2, sy - 25, sx + 3, sy - 21, 0xFF0B1626);
                g.fill(sx - 1, sy - 24, sx + 2, sy - 22, INK);
                ring(g, sx, sy, 6 + Math.round(pulse * 2), Glass.a(0xE9F1FF, 0.5f + 0.5f * (1 - pulse)));
                ring(g, sx, sy, 5, 0xB00B1626);
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, INK);
                // facing: a short tick on the table toward where you look
                double f = Math.toRadians(mc.player.getYRot());
                gui.transformDirection((float) -Math.sin(f), 0f, (float) Math.cos(f), v);
                float len = (float) Math.sqrt(v.x * v.x + v.y * v.y);
                if (len > 1e-4f) for (int i = 4; i <= 9; i++)
                    g.fill(sx + Math.round(v.x / len * i), sy + Math.round(v.y / len * i), sx + Math.round(v.x / len * i) + 1, sy + Math.round(v.y / len * i) + 1, INK);
            }
        }
        g.disableScissor();
    }

    /**
     * The ground under a point of the panel (GUI coordinates): the view is affine, so the line of sight through it is
     * solved for the table's height there, a few times over as the height under it settles. False off the panel.
     */
    private boolean ground(double mx, double my, double[] out) {
        int x0 = px0(), y0 = py0();
        if (mx < x0 || mx > px1() || my < y0 || my > py1()) return false;
        Matrix4f inv = AtlasRenderState.view(new Matrix4f(), px1() - x0, py1() - y0, zoom, yaw, tilt).invert();
        Vector3f o = inv.transformPosition((float) (mx - x0), (float) (my - y0), 0f, new Vector3f());
        Vector3f d = inv.transformDirection(0f, 0f, 1f, new Vector3f());
        if (Math.abs(d.y) < 1e-6f) return false;
        double h = cy;
        double wx = cx, wz = cz;
        for (int i = 0; i < 4; i++) {
            float t = (float) ((h - cy - o.y) / d.y);
            wx = cx + o.x + d.x * t;
            wz = cz + o.z + d.z * t;
            h = AtlasState.get().store.groundY(wx, wz, h);
        }
        out[0] = wx;
        out[1] = h;
        out[2] = wz;
        return true;
    }

    /** Text as light (the Holo's look), or plain with the profiling switch. */
    private void text(GuiGraphicsExtractor g, String str, int x, int y, int argb, boolean shadow) {
        if ((prof & PROF_PLAIN_TEXT) != 0) g.text(font, str, x, y, argb, shadow);
        else HudLook.text(g, font, str, x, y, argb, shadow);
    }

    /** Text on a dark plate (chips, the legend): plain, it reads as well there and costs a fraction of the glow. */
    private void plain(GuiGraphicsExtractor g, String str, int x, int y, int argb) {
        g.text(font, str, x, y, argb, false);
    }

    /** A name chip on the table: dark glass, a bar in the zone's colour, the name, and a quieter line under it. */
    private void chip(GuiGraphicsExtractor g, String name, String sub, int colour, int x, int y, int w, int h, float alpha) {
        g.fill(x, y, x + w, y + h, Glass.a(0x0B1626, 0.86f * alpha));
        g.fill(x, y, x + 3, y + h, colour);
        g.fill(x + 3, y, x + w, y + 1, Glass.a(colour & 0xFFFFFF, 0.55f * alpha));
        plain(g, name, x + 8, y + 3, INK);
        plain(g, sub, x + 8, y + 12, Glass.a(colour & 0xFFFFFF, 0.95f * alpha));
    }

    /** The gates you know (nearest first), the zone you're in, then the nearest others: the legend's rows. */
    private void legendLayout(Minecraft mc, AtlasState atlas, int x0, int y0, int lod) {
        legendRows.clear();
        gateRows.clear();
        legendX0 = legendY0 = legendX1 = legendY1 = -1000;
        pointY = -1000;
        if (!showLegend || mc.player == null || !scene && px1() - px0() < 300) return;
        double px = mc.player.getX(), pz = mc.player.getZ();
        java.util.List<MapMarks.Gate> gates = new java.util.ArrayList<>(MapMarks.gates(mc.player));
        gates.sort(java.util.Comparator.comparingDouble((MapMarks.Gate gt) -> gt.pos().distToCenterSqr(px, gt.pos().getY(), pz)));
        for (int i = 0; i < Math.min(GATE_ROWS, gates.size()); i++) gateRows.add(gates.get(i));
        moreGates = gates.size() - gateRows.size();
        if (chosen != null && gateRows.stream().noneMatch(gt -> gt.pos().equals(chosen))) chosen = null;
        java.util.List<AtlasState.ZoneLabel> all = atlas.zoneLabels();
        int here = atlas.zoneView().zone(mc.player.getBlockX(), mc.player.getBlockZ());
        java.util.List<AtlasState.ZoneLabel> sorted = new java.util.ArrayList<>(all);
        sorted.sort(java.util.Comparator.comparingDouble((AtlasState.ZoneLabel z) -> z.zone() == here ? -1 : (z.x() - px) * (z.x() - px) + (z.z() - pz) * (z.z() - pz)));
        // in the scene the legend stays over your head; on the table, inside the table's corner
        int maxY = scene ? Math.round(height * 0.56f) : py1() - 6;
        // the scene always shows its GATES heading: with none seen yet, it says how they're found
        noGates = scene && gateRows.isEmpty() ? font.split(Component.literal(NO_GATES), Math.max(40, (scene ? sideX1() - sideX0() : LEGEND_W) - 12)) : java.util.List.of();
        int gateBlock = !gateRows.isEmpty() ? 12 + gateRows.size() * ROW + (moreGates > 0 ? ROW : 0) + (chosen != null ? ROW + 2 : 0) + 6
                : noGates.isEmpty() ? 0 : 12 + noGates.size() * 10 + 6;
        int fixed = 4 + gateBlock + 12 + 22;
        int lands = Math.max(0, (maxY - (scene ? py0() : y0 + 6) - fixed) / ROW);
        // close in, the city's districts and the land round you; zoomed out, the regions (their danger matters there)
        for (AtlasState.ZoneLabel z : sorted)
            if (legendRows.size() < Math.min(LEGEND_ROWS, lands) && (lod == 0 || z.zone() < ZoneMap.DISTRICT)) legendRows.add(z);
        if (legendRows.isEmpty() && gateRows.isEmpty() && noGates.isEmpty()) return;
        legendX0 = scene ? sideX0() : x0 + 6;
        legendY0 = scene ? py0() : y0 + 6;
        legendX1 = scene ? sideX1() : legendX0 + LEGEND_W;
        int y = legendY0 + 4;
        if (!noGates.isEmpty()) {
            gateRowY0 = y + 12;
            y = gateRowY0 + noGates.size() * 10 + 6;
        }
        if (!gateRows.isEmpty()) {
            gateRowY0 = y + 12;
            y = gateRowY0 + gateRows.size() * ROW + (moreGates > 0 ? ROW : 0);
            if (chosen != null) {
                pointY = y + 1;
                y += ROW + 2;
            }
            y += 6;
        }
        legendRowY0 = legendRows.isEmpty() ? -1000 : y + 12;
        if (!legendRows.isEmpty()) y = legendRowY0 + legendRows.size() * ROW;
        dangerY = y + 6;
        // the danger key on one line when it fits, else its colours on a line of their own
        int keyW = font.width("DANGER") + 6;
        for (String lv : DANGER_LEVELS) keyW += 8 + font.width(lv) + 6;
        dangerWrap = keyW > legendX1 - legendX0 - 10;
        legendY1 = dangerY + (dangerWrap ? 25 : 14);
    }

    private int dangerY, moreGates;
    private boolean dangerWrap;
    private static final String[] DANGER_LEVELS = {"1", "10", "25", "45", "70", "100"};
    private java.util.List<net.minecraft.util.FormattedCharSequence> noGates = java.util.List.of();
    private static final String NO_GATES = "You haven't seen a gate yet. Their light shows from far off: look for a pillar in the sky.";

    /** The legend: the gates you know (pick one, then "Point me there"), the lands and what their colours mean. */
    private void legend(GuiGraphicsExtractor g, Minecraft mc, AtlasState atlas, int mouseX, int mouseY) {
        if (legendRows.isEmpty() && gateRows.isEmpty() && noGates.isEmpty()) return;
        int x = legendX0, y = legendY0, w = legendX1 - legendX0;
        if (scene) HudLook.card(g, x, y, w, legendY1 - y, Glass.EDGE, 0.86f, Math.clamp((age() - 0.18f) / 0.25f, 0f, 1f), 0f, HudLook.FRAME);
        else {
            g.fill(x, y, x + w, legendY1, 0xC80B1626);
            g.fill(x, y, x + w, y + 1, 0x667FC3FF);
        }
        if (!noGates.isEmpty()) {
            plain(g, "GATES", x + 6, gateRowY0 - 12, Glass.LABEL | 0xFF000000);
            for (int i = 0; i < noGates.size(); i++) g.text(font, noGates.get(i), x + 6, gateRowY0 + i * 10, Glass.a(Glass.LABEL, 1f), false);
        }
        if (!gateRows.isEmpty()) {
            plain(g, "GATES", x + 6, gateRowY0 - 12, Glass.LABEL | 0xFF000000);
            if (moreGates > 0) plain(g, "+ " + moreGates + " further out", x + 17, gateRowY0 + gateRows.size() * ROW, Glass.a(Glass.LABEL, 0.8f));
            String tab = "Tab";
            plain(g, tab, x + w - 6 - font.width(tab), gateRowY0 - 12, Glass.a(Glass.LABEL, 0.7f));
            for (int i = 0; i < gateRows.size(); i++) {
                MapMarks.Gate gt = gateRows.get(i);
                int ry = gateRowY0 + i * ROW;
                boolean picked = gt.pos().equals(chosen);
                boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry - 1 && mouseY < ry + ROW - 1;
                if (picked) g.fill(x + 1, ry - 1, x + w - 1, ry + ROW - 1, Glass.a(gt.colour(), 0.22f));
                else if (over) g.fill(x + 1, ry - 1, x + w - 1, ry + ROW - 1, 0x307FC3FF);
                g.fill(x + 8, ry, x + 10, ry + 9, Glass.a(gt.colour(), gt.read() ? 0.6f : 1f));
                String dist = gateDistance(mc, gt);
                int dw = font.width(dist);
                String name = font.plainSubstrByWidth((gt.broken() ? "! " : "") + gt.name(), w - 30 - dw);
                plain(g, name, x + 17, ry, over || picked ? 0xFFFFFFFF : INK);
                plain(g, dist, x + w - 6 - dw, ry, Glass.a(gt.broken() ? Glass.WARN : Glass.LABEL, 0.95f));
            }
            if (pointY > -1000) {
                boolean pointing = pinned().map(at -> at.equals(chosen)).orElse(false);
                String label = pointing ? "Pointing you there" : "Point me there";
                String key = pointing ? "" : "T";
                boolean over = mouseX >= x && mouseX < x + w && mouseY >= pointY - 1 && mouseY < pointY + ROW - 1;
                int bx0 = x + 15, bx1 = x + w - 6;
                g.fill(bx0, pointY - 1, bx1, pointY + ROW - 1, Glass.a(0x7FC3FF, pointing ? 0.12f : over ? 0.34f : 0.22f));
                g.fill(bx0, pointY - 1, bx0 + 1, pointY + ROW - 1, 0xFF7FC3FF);
                plain(g, (pointing ? "✓ " : "› ") + label, bx0 + 5, pointY + 1, pointing ? Glass.a(Glass.LABEL, 1f) : 0xFFFFFFFF);
                if (!key.isEmpty()) plain(g, key, bx1 - 4 - font.width(key), pointY + 1, Glass.a(Glass.LABEL, 0.8f));
            }
        }
        if (!legendRows.isEmpty()) {
            plain(g, "KNOWN LANDS", x + 6, legendRowY0 - 12, Glass.LABEL | 0xFF000000);
            int here = mc.player == null ? 0 : atlas.zoneView().zone(mc.player.getBlockX(), mc.player.getBlockZ());
            for (int i = 0; i < legendRows.size(); i++) {
                AtlasState.ZoneLabel z = legendRows.get(i);
                int ry = legendRowY0 + i * ROW;
                boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry - 1 && mouseY < ry + ROW - 1;
                if (over) g.fill(x + 1, ry - 1, x + w - 1, ry + ROW - 1, 0x307FC3FF);
                g.fill(x + 6, ry + 1, x + 13, ry + 8, z.colour());
                String sub = z.zone() == here ? "here" : z.sub();
                int subW = font.width(sub);
                String name = font.plainSubstrByWidth(z.name(), w - 30 - subW);
                plain(g, name, x + 17, ry, over ? 0xFFFFFFFF : INK);
                plain(g, sub, x + w - 6 - subW, ry, z.zone() == here ? 0xFFE9F1FF : Glass.a(z.colour() & 0xFFFFFF, 0.9f));
            }
        }
        // the danger key: what a region's colour means
        int ky = dangerY;
        plain(g, "DANGER", x + 6, ky + 1, Glass.LABEL | 0xFF000000);
        int kx = x + 6 + font.width("DANGER") + 6;
        if (dangerWrap) {
            kx = x + 6;
            ky += 11;
        }
        String[] lv = DANGER_LEVELS;
        for (int b = 0; b < ZoneMap.BAND_COLOUR.length; b++) {
            g.fill(kx, ky + 2, kx + 6, ky + 8, ZoneMap.BAND_COLOUR[b]);
            plain(g, lv[b], kx + 8, ky + 1, Glass.LABEL | 0xFF000000);
            kx += 8 + font.width(lv[b]) + 6;
        }
    }

    private static java.util.Optional<BlockPos> pinned() {
        return dev.awaken.signal.client.NowClient.pinned();
    }

    /** "120 m" along the ground from you to a gate. */
    private static String gateDistance(Minecraft mc, MapMarks.Gate gt) {
        if (mc.player == null) return "";
        double dx = gt.pos().getX() + 0.5 - mc.player.getX(), dz = gt.pos().getZ() + 0.5 - mc.player.getZ();
        long m = Math.round(Math.sqrt(dx * dx + dz * dz));
        return m >= 1000 ? String.format(Locale.ROOT, "%.1f km", m / 1000.0) : m + " m";
    }

    /** Picks a gate: the map glides to it and the legend offers "Point me there". */
    private void choose(MapMarks.Gate gt) {
        chosen = gt.pos();
        dev.awaken.progression.client.StatusSections.narrate(gt.name() + ", " + gateDistance(Minecraft.getInstance(), gt)
                + (gt.when().isEmpty() ? "" : ", " + gt.when()) + ". Press T to be pointed there.");
        flyX = gt.pos().getX() + 0.5;
        flyZ = gt.pos().getZ() + 0.5;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), 1.1f, 0.7f));
    }

    /** "Point me there": the Now line, its compass and the Signal's thread lead to the gate picked, until you reach it. */
    private void pointAtChosen() {
        if (chosen == null) return;
        for (MapMarks.Gate gt : gateRows) {
            if (!gt.pos().equals(chosen)) continue;
            dev.awaken.signal.client.NowClient.pin("Go to the " + (gt.broken() ? "broken " : "") + gt.name(), gt.pos());
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.CONFIRM.get(), 1.0f, 0.7f));
            if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("atlas.awaken.point"));
            return;
        }
    }

    /** Under the cursor: which district or region that land is, and its levels. */
    private void hover(GuiGraphicsExtractor g, Minecraft mc, AtlasState atlas, int mouseX, int mouseY, int x0, int y0, int x1, int y1) {
        if (spin != 0f || System.nanoTime() - lastInput < 150_000_000L) return; // not while the table is moving under the cursor
        if (mouseX >= legendX0 && mouseX < legendX1 && mouseY >= legendY0 && mouseY < legendY1) return;
        double[] at = new double[3];
        if (!ground(mouseX, mouseY, at)) return;
        int bx = (int) Math.floor(at[0]), bz = (int) Math.floor(at[2]);
        if (!Discovery.CLIENT.explored(bx >> 4, bz >> 4)) return;
        int zone = atlas.zoneView().zone(bx, bz);
        if (zone == 0) return;
        for (int i = 0; i < placedCount; i++) {
            int dx = placedAt[i * 2] - mouseX, dy = placedAt[i * 2 + 1] - mouseY;
            if (placedZones[i] == zone && dx * dx + dy * dy < 110 * 110) return; // its name is right there already
        }
        for (AtlasState.ZoneLabel z : atlas.zoneLabels()) {
            if (z.zone() != zone) continue;
            String name = z.name().toUpperCase(Locale.ROOT);
            int w = Math.max(font.width(name), font.width(z.sub())) + 14;
            int sx = Math.min(mouseX + 12, x1 - 4 - w), sy = Math.min(mouseY + 12, y1 - 26);
            chip(g, name, z.sub(), z.colour(), sx, sy, w, 22, 1f);
            return;
        }
    }

    static final int PILLAR = 30;

    /** Where a gate's pillar stands: on its ground where you've been, else on the clouds (you saw its light from afar). */
    private static double gateY(AtlasState atlas, MapMarks.Gate gate) {
        BlockPos p = gate.pos();
        return Discovery.CLIENT.explored(p.getX() >> 4, p.getZ() >> 4) ? atlas.store.groundY(p.getX(), p.getZ(), p.getY())
                : atlas.store.groundY(p.getX(), p.getZ(), 64) + 34;
    }

    /** A gate's pillar of light in its rank's colour, its rank and how long it holds over it (a scanner's reading paler). */
    private void pillar(GuiGraphicsExtractor g, int sx, int sy, MapMarks.Gate gate) {
        float a = gate.read() ? 0.6f : 1f;
        int c = gate.colour();
        g.fill(sx - 2, sy - PILLAR, sx + 2, sy, Glass.a(0x0B1626, 0.55f * a)); // a dark core: the light reads over white cloud
        g.fillGradient(sx - 1, sy - PILLAR, sx + 1, sy, Glass.a(c, 0.15f * a), Glass.a(c, a));
        g.fill(sx - 3, sy - 1, sx + 4, sy + 1, Glass.a(c, a));
        if (zoom > 0.3f) {
            label(g, (gate.broken() ? "! " : "") + gate.name(), sx, sy - PILLAR - 24, Glass.a(c, a));
            if (!gate.when().isEmpty()) label(g, gate.when(), sx, sy - PILLAR - 12, Glass.a(gate.broken() ? Glass.WARN : Glass.LABEL, a));
        }
    }

    /** An amber "?" riding the clouds over a place you've only heard of, with its name. */
    private void rumour(GuiGraphicsExtractor g, String name) {
        int sx = Math.round(v.x), sy = Math.round(v.y);
        float pulse = 0.75f + 0.25f * (float) Math.sin(age() * 2.2);
        int amber = Glass.a(Glass.AMBER, pulse);
        g.pose().pushMatrix();
        g.pose().translate(sx, sy);
        g.pose().scale(2f, 2f);
        text(g, "?", -font.width("?") / 2, -8, amber, false);
        g.pose().popMatrix();
        if (zoom > 0.3f) label(g, name.toUpperCase(Locale.ROOT), sx, sy + 6, Glass.a(Glass.AMBER, 0.9f));
    }

    private boolean project(double x, double y, double z, int x0, int y0) {
        gui.transformPosition((float) (x - cx), (float) (y - cy), (float) (z - cz), v);
        v.x += x0;
        v.y += y0;
        return v.x > x0 - 20 && v.x < px1() + 20 && v.y > y0 - 20 && v.y < py1() + 20;
    }

    /** Each walked road's line over known land: a dark casing under its colour, stepped a pixel at a time between points. */
    private void roads(GuiGraphicsExtractor g, AtlasState atlas, java.util.List<MapMarks.Line> lines, int x0, int y0) {
        for (int pass = 0; pass < 2; pass++) {
            for (MapMarks.Line l : lines) {
                int[] xz = l.xz();
                float px = Float.NaN, py = Float.NaN;
                for (int i = 0; i + 1 < xz.length; i += 2) {
                    int x = xz[i], z = xz[i + 1];
                    if (!Discovery.CLIENT.explored(x >> 4, z >> 4) || !project(x + 0.5, atlas.store.groundY(x, z, cy) + 0.6, z + 0.5, x0, y0)) {
                        px = Float.NaN;
                        continue;
                    }
                    float sx = v.x, sy = v.y;
                    if (!Float.isNaN(px)) {
                        int w = pass == 0 ? l.width() + 2 : l.width();
                        int c = pass == 0 ? 0xA00B1626 : 0xFF000000 | l.colour();
                        float dx = sx - px, dy = sy - py;
                        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
                        if (steps > 400) {
                            px = sx;
                            py = sy;
                            continue;
                        }
                        for (int k = 0; k <= steps; k++) {
                            int qx = Math.round(px + dx * k / steps), qy = Math.round(py + dy * k / steps);
                            g.fill(qx - w / 2, qy - w / 2, qx - w / 2 + w, qy - w / 2 + w, c);
                        }
                    }
                    px = sx;
                    py = sy;
                }
            }
        }
    }

    private void label(GuiGraphicsExtractor g, String text, int cx, int y, int color) {
        int tw = font.width(text);
        g.fill(cx - tw / 2 - 3, y - 2, cx + tw / 2 + 3, y + 9, 0x9A0B1626);
        text(g, text, cx - tw / 2, y, color, false);
    }

    private static void diamond(GuiGraphicsExtractor g, int x, int y, int r, int color, boolean filled) {
        for (int dy = -r - 1; dy <= r + 1; dy++) {
            int span = r + 1 - Math.abs(dy);
            g.fill(x - span, y + dy, x + span + 1, y + dy + 1, 0xFF0B1626);
        }
        for (int dy = -r; dy <= r; dy++) {
            int span = r - Math.abs(dy);
            if (filled || Math.abs(dy) == r) g.fill(x - span, y + dy, x + span + 1, y + dy + 1, color);
            else {
                g.fill(x - span, y + dy, x - span + 1, y + dy + 1, color);
                g.fill(x + span, y + dy, x + span + 1, y + dy + 1, color);
            }
        }
    }

    private static void ring(GuiGraphicsExtractor g, int x, int y, int r, int color) {
        for (int k = 0; k < 48; k++) {
            double t = k * Math.PI * 2 / 48;
            int px = x + (int) Math.round(Math.cos(t) * r), py = y + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    // ---- input ----------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        boolean inLegend = e.x() >= legendX0 && e.x() < legendX1 && e.y() >= legendY0 && e.y() < legendY1;
        if (inLegend && e.button() == InputConstants.MOUSE_BUTTON_LEFT && !gateRows.isEmpty()) {
            if (e.y() >= pointY - 1 && e.y() < pointY + ROW - 1) {
                pointAtChosen();
                return true;
            }
            int i = (int) Math.floor((e.y() - gateRowY0 + 1) / ROW);
            if (e.y() >= gateRowY0 - 1 && i >= 0 && i < gateRows.size()) {
                choose(gateRows.get(i));
                return true;
            }
        }
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && inLegend && legendRowY0 > -1000 && e.y() >= legendRowY0 - 1) {
            int i = (int) ((e.y() - legendRowY0 + 1) / ROW);
            if (i >= 0 && i < legendRows.size()) { // fly there, close enough to read it
                AtlasState.ZoneLabel z = legendRows.get(i);
                flyX = z.x();
                flyZ = z.z();
                if (z.zone() >= ZoneMap.DISTRICT) zoom = Math.max(zoom, (px1() - px0()) / AtlasScreen.OPEN_SPAN * 1.3f);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), 1.1f, 0.7f));
                return true;
            }
        }
        if (e.x() >= legendX0 && e.x() < legendX1 && e.y() >= legendY0 && e.y() < legendY1) return true;
        // double-click: drop a pin on the ground there (your party sees it), or take back your own pin under the cursor
        double[] at = new double[3];
        if (doubleClick && e.button() == InputConstants.MOUSE_BUTTON_LEFT && ground(e.x(), e.y(), at)) {
            boolean remove = false;
            AtlasRenderState.view(gui, px1() - px0(), py1() - py0(), zoom, yaw, tilt);
            for (dev.awaken.atlas.AtlasNet.PinView pin : AtlasState.get().pins) {
                if (!pin.mine() || !project(pin.x() + 0.5, pin.y() + 1, pin.z() + 0.5, px0(), py0())) continue;
                if (Math.abs(v.x - e.x()) <= 6 && v.y - e.y() >= -4 && v.y - e.y() <= 24) {
                    remove = true;
                    at[0] = pin.x();
                    at[2] = pin.z();
                }
            }
            ClientPacketDistributor.sendToServer(new AtlasNet.Pin((int) Math.floor(at[0]), (int) Math.floor(at[1]), (int) Math.floor(at[2]), remove));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), remove ? 0.8f : 1.3f, 0.8f));
            return true;
        }
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && e.y() >= buttonsY - 2 && e.y() < buttonsY + 10
                && shareX0 >= 0 && e.x() >= shareX0 && e.x() < shareX1) {
            ClientPacketDistributor.sendToServer(new AtlasNet.Share());
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        lastInput = System.nanoTime();
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            // pan in the ground plane, along the view's own right and forward
            double r = Math.toRadians(yaw), k = 1.0 / zoom, fwd = dy / Math.max(0.3, Math.sin(Math.toRadians(tilt)));
            cx -= (Math.cos(r) * dx - Math.sin(r) * fwd) * k;
            cz -= (Math.sin(r) * dx + Math.cos(r) * fwd) * k;
            cy = AtlasState.get().store.groundY(cx, cz, cy);
            return true;
        }
        yaw += (float) dx * 0.6f;
        tilt = Math.clamp(tilt - (float) dy * 0.4f, 20f, 89f);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        lastInput = System.nanoTime();
        zoom = Math.clamp(zoom * (float) Math.pow(1.15, scrollY), 0.04f, 16f);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (AtlasClient.KEY.matches(e)) {
            // in the scene: Shift+M or a quick second M is the flat table (SPEC §30.5); else M closes
            if (scene && (e.hasShiftDown() || age() < 0.30f)) MapScene.toTable();
            else onClose();
            return true;
        }
        if (scene && (dev.awaken.core.client.AwakenKeys.STATUS.matches(e) || e.key() == InputConstants.KEY_SPACE)) {
            onClose();
            return true;
        }
        if (scene && e.key() >= InputConstants.KEY_1 && e.key() <= InputConstants.KEY_5) { // a band page
            var page = dev.awaken.progression.client.StatusSections.Tab.values()[e.key() - InputConstants.KEY_1];
            MapScene.cut(); // the band opens on your own eyes, at once
            dev.awaken.progression.client.ProgressionClient.openBand(page);
            return true;
        }
        if (e.key() == InputConstants.KEY_TAB && !gateRows.isEmpty()) { // the next gate, nearest first
            int at = -1;
            for (int i = 0; i < gateRows.size(); i++) if (gateRows.get(i).pos().equals(chosen)) at = i;
            choose(gateRows.get((at + (e.hasShiftDown() ? gateRows.size() - 1 : 1)) % gateRows.size()));
            return true;
        }
        if (e.key() == InputConstants.KEY_T && chosen != null) {
            pointAtChosen();
            return true;
        }
        int k = heldKey(mc, e);
        if (k != 0) {
            held |= k;
            lastInput = System.nanoTime();
            return true;
        }
        if (e.key() == InputConstants.KEY_L) {
            showLegend = !showLegend;
            return true;
        }
        if (e.key() == InputConstants.KEY_C && Minecraft.getInstance().player != null) {
            var p = Minecraft.getInstance().player;
            cx = p.getX();
            cz = p.getZ();
            cy = AtlasState.get().store.groundY(cx, cz, p.getY());
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        int k = heldKey(Minecraft.getInstance(), e);
        if (k != 0) {
            held &= ~k;
            return true;
        }
        return super.keyReleased(e);
    }

    /** The keys that move the map while held: your movement keys pan it, Q/E turn, R/F tilt, +/- zoom. */
    private static int heldKey(Minecraft mc, KeyEvent e) {
        if (mc.options.keyUp.matches(e)) return K_FWD;
        if (mc.options.keyDown.matches(e)) return K_BACK;
        if (mc.options.keyLeft.matches(e)) return K_LEFT;
        if (mc.options.keyRight.matches(e)) return K_RIGHT;
        return switch (e.key()) {
            case InputConstants.KEY_Q -> K_TURN_L;
            case InputConstants.KEY_E -> K_TURN_R;
            case InputConstants.KEY_R -> K_TILT_UP;
            case InputConstants.KEY_F -> K_TILT_DOWN;
            case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> K_ZOOM_IN;
            case InputConstants.KEY_MINUS, 86 -> K_ZOOM_OUT; // 86: the keypad's minus (no constant for it)
            default -> 0;
        };
    }

    /** Moves the map by the keys held, this frame. */
    private void keysHeld(float dt) {
        lastInput = System.nanoTime();
        float fwd = ((held & K_FWD) != 0 ? 1 : 0) - ((held & K_BACK) != 0 ? 1 : 0);
        float right = ((held & K_RIGHT) != 0 ? 1 : 0) - ((held & K_LEFT) != 0 ? 1 : 0);
        if (fwd != 0 || right != 0) {
            // as a right-drag: a third of the card a second, along the view's own right and forward
            double px = (px1() - px0()) * 0.35 * dt;
            double r = Math.toRadians(yaw), k = 1.0 / zoom;
            double dx = -right * px, dy = fwd * px / Math.max(0.3, Math.sin(Math.toRadians(tilt)));
            cx -= (Math.cos(r) * dx - Math.sin(r) * dy) * k;
            cz -= (Math.sin(r) * dx + Math.cos(r) * dy) * k;
            cy = AtlasState.get().store.groundY(cx, cz, cy);
            flyX = Double.NaN;
        }
        if ((held & K_TURN_L) != 0) yaw -= 90f * dt;
        if ((held & K_TURN_R) != 0) yaw += 90f * dt;
        if ((held & K_TILT_UP) != 0) tilt = Math.clamp(tilt + 40f * dt, 20f, 89f);
        if ((held & K_TILT_DOWN) != 0) tilt = Math.clamp(tilt - 40f * dt, 20f, 89f);
        if ((held & K_ZOOM_IN) != 0) zoom = Math.clamp(zoom * (float) Math.pow(2.2, dt), 0.04f, 16f);
        if ((held & K_ZOOM_OUT) != 0) zoom = Math.clamp(zoom / (float) Math.pow(2.2, dt), 0.04f, 16f);
    }

    @Override
    public void onClose() {
        if (scene) MapScene.close(false);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (scene) MapScene.close(false); // replaced by another screen: the camera goes back too
        dev.awaken.holo.client.HoloScene.noBeams();
        UiHooks.clearFocus(this);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.CLOSE.get(), 1.0f, 0.7f));
        dev.awaken.Awaken.LOG.info("[atlas] closed: {} | {} | {}", AtlasState.stats.summary(), AtlasState.get().memory(), AtlasState.get().costs());
        super.removed();
    }
}
