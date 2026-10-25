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
 */
public final class AtlasScreen extends Screen {
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
    private int wantX = Integer.MIN_VALUE, wantZ;
    private int shareX0, shareX1, clearX0, clearX1, buttonsY;

    AtlasScreen(float yaw, float tilt, float zoomScale) {
        super(Component.translatable("screen.awaken.atlas"));
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

    private int px0() { return MARGIN; }
    private int py0() { return MARGIN + 14; }
    private int px1() { return width - MARGIN; }
    private int py1() { return height - MARGIN - 14; }

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
        float age = age();
        UiHooks.setFocus(this, Math.min(1f, age / 0.25f));
        int lod = AtlasMesh.lodFor(zoom);
        int sx = (int) Math.floor(cx) >> 8, sz = (int) Math.floor(cz) >> 8;
        if (sx != wantX || sz != wantZ) { // panned somewhere new: read more of the disk cache
            wantX = sx;
            wantZ = sz;
            atlas.want(cx, cz);
        }
        float night = mc.level == null ? 0f : AtlasState.night(mc.level);
        int x0 = px0(), y0 = py0(), x1 = px1(), y1 = py1();
        float reveal = Math.min(1f, age / 0.35f);
        // the K panel's projected glass: frame, corner brackets, a slow interference band; scans in as it opens
        HudLook.card(g, x0, y0, x1 - x0, y1 - y0, Glass.EDGE, 0.92f, reveal, 0f, HudLook.FRAME | HudLook.DRIFT);
        float time = frozen ? 0f : (now - opened) / 1e9f;
        boolean moving = !frozen || spin != 0f || age < 1.5f || atlas.trailProgress() < 1f || atlas.trailGlow() < 1f && atlas.trailGlow() > 0.35f;
        g.submitPictureInPictureRenderState(new AtlasRenderState(x0, y0, x1, y1, yaw, tilt, zoom, cx, cy, cz, time, night,
                moving, lod, age, 0, g.peekScissorStack()));
        if (age > 0.45f) markers(g, mc, atlas, x0, y0, x1, y1, lod);
        chrome(g, mc, x0, y0, x1, y1, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, pt);
    }

    private void chrome(GuiGraphicsExtractor g, Minecraft mc, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        String where = place(mc);
        String count = String.format(Locale.ROOT, "%,d places known", Discovery.CLIENT.count());
        int room = x1 - x0 - font.width(count) - 20;
        String title = "ATLAS" + (where == null ? "" : "  ·  " + where.toUpperCase(Locale.ROOT));
        if (font.width(title) > room) title = font.plainSubstrByWidth(title, room - font.width("…")) + "…";
        HudLook.text(g, font, title, x0 + 2, MARGIN, INK, false);
        HudLook.text(g, font, count, x1 - font.width(count) - 2, MARGIN, Glass.LABEL | 0xFF000000, false);
        // compass: north turns with the table
        int ccx = x1 - 18, ccy = y0 + 18;
        double r = Math.toRadians(yaw);
        int nx = ccx + (int) Math.round(-Math.sin(r) * 9), ny = ccy - (int) Math.round(Math.cos(r) * 9 * Math.sin(Math.toRadians(tilt)));
        g.fill(ccx - 1, ccy - 1, ccx + 1, ccy + 1, 0x667FC3FF);
        HudLook.text(g, font, "N", nx - 2, ny - 4, 0xFF7FC3FF, false);
        // hints and the two buttons
        buttonsY = height - MARGIN - 8;
        String share = "Share with party", clear = "Clear Atlas cache";
        clearX1 = x1 - 2;
        clearX0 = clearX1 - font.width(clear);
        shareX1 = clearX0 - 14;
        shareX0 = shareX1 - font.width(share);
        for (String hint : new String[]{"drag orbit  ·  right-drag pan  ·  scroll zoom  ·  C you", "drag · right-drag · scroll · C"}) {
            if (x0 + 2 + font.width(hint) < shareX0 - 12) {
                HudLook.text(g, font, hint, x0 + 2, buttonsY, Glass.LABEL | 0xFF000000, false);
                break;
            }
        }
        boolean overShare = mouseY >= buttonsY - 2 && mouseY < buttonsY + 10 && mouseX >= shareX0 && mouseX < shareX1;
        boolean overClear = mouseY >= buttonsY - 2 && mouseY < buttonsY + 10 && mouseX >= clearX0 && mouseX < clearX1;
        HudLook.text(g, font, share, shareX0, buttonsY, overShare ? 0xFFFFFFFF : 0xFF7FC3FF, false);
        HudLook.text(g, font, clear, clearX0, buttonsY, overClear ? 0xFFFFFFFF : 0xFF93A7C2, false);
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
        // Carillon's districts, lettered once their ground is known (close in only)
        BlockPos sq = mc.level == null ? null : CityHooks.square(mc.level);
        if (sq != null && lod == 0) {
            for (Bells.District d : Bells.districts()) {
                int wx = sq.getX() + d.x(), wz = sq.getZ() + d.z();
                if (!Discovery.CLIENT.explored(wx >> 4, wz >> 4)) continue;
                if (!project(wx, atlas.store.groundY(wx, wz, sq.getY()) + 8, wz, x0, y0)) continue;
                String name = d.key().equals("market") ? "THE CHOIR" : d.name().toUpperCase(Locale.ROOT);
                label(g, name, Math.round(v.x), Math.round(v.y) - 6, d.key().equals("market") ? BRONZE : 0xFFD9E4F2);
            }
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

    /** An amber "?" riding the clouds over a place you've only heard of, with its name. */
    private void rumour(GuiGraphicsExtractor g, String name) {
        int sx = Math.round(v.x), sy = Math.round(v.y);
        float pulse = 0.75f + 0.25f * (float) Math.sin(age() * 2.2);
        int amber = Glass.a(Glass.AMBER, pulse);
        g.pose().pushMatrix();
        g.pose().translate(sx, sy);
        g.pose().scale(2f, 2f);
        HudLook.text(g, font, "?", -font.width("?") / 2, -8, amber, false);
        g.pose().popMatrix();
        if (zoom > 0.3f) label(g, name.toUpperCase(Locale.ROOT), sx, sy + 6, Glass.a(Glass.AMBER, 0.9f));
    }

    private boolean project(double x, double y, double z, int x0, int y0) {
        gui.transformPosition((float) (x - cx), (float) (y - cy), (float) (z - cz), v);
        v.x += x0;
        v.y += y0;
        return v.x > x0 - 20 && v.x < px1() + 20 && v.y > y0 - 20 && v.y < py1() + 20;
    }

    private void label(GuiGraphicsExtractor g, String text, int cx, int y, int color) {
        int tw = font.width(text);
        g.fill(cx - tw / 2 - 3, y - 2, cx + tw / 2 + 3, y + 9, 0x9A0B1626);
        HudLook.text(g, font, text, cx - tw / 2, y, color, false);
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
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && e.y() >= buttonsY - 2 && e.y() < buttonsY + 10) {
            if (e.x() >= shareX0 && e.x() < shareX1) {
                ClientPacketDistributor.sendToServer(new AtlasNet.Share());
                return true;
            }
            if (e.x() >= clearX0 && e.x() < clearX1) {
                AtlasState.get().clearCache();
                Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("atlas.awaken.cleared"));
                return true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
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
        zoom = Math.clamp(zoom * (float) Math.pow(1.15, scrollY), 0.04f, 16f);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (AtlasClient.KEY.matches(e)) {
            onClose();
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        UiHooks.clearFocus(this);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.CLOSE.get(), 1.0f, 0.7f));
        dev.awaken.Awaken.LOG.info("[atlas] closed: {} | {} | {}", AtlasState.stats.summary(), AtlasState.get().memory(), AtlasState.get().costs());
        super.removed();
    }
}
