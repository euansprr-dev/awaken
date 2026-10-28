package dev.awaken.holo.client;

import dev.awaken.core.api.HudState;
import dev.awaken.holo.ui.GlanceRules;
import dev.awaken.holo.ui.GlanceRules.Row;
import dev.awaken.holo.ui.HoloTimeline;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import dev.awaken.signal.client.SignalGlance;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * The Tab glance (SPEC §40.7; Metro's watch): hold the player-list key and the off-hand forearm comes up with the band, and
 * the band projects one flat, readable panel just above it: level and rank with the XP line, health, Aether, food,
 * armour, the task with its current and next step, and a needle to where you're heading. You keep walking and looking
 * while you hold it; in multiplayer vanilla's player list shows with it (the same key). A quick tap leaves the panel up
 * for 4 s without the arm. The panel unfolds from the wrist along a beam of light, in the band's own glass. Off in
 * Classic. Its texts are built once a tick while it shows; a frame only draws.
 */
final class Glance {
    private Glance() {}

    static final net.minecraft.resources.Identifier LAYER = dev.awaken.Awaken.id("glance");
    /** The panel's width (GUI px), its row height, and the gap it keeps above the wrist. */
    private static final int W = 184, ROW = 11, PAD = 6;
    private static final float ARM_IN_MS = 260F, ARM_OUT_MS = 200F, PANEL_IN_MS = 220F, PANEL_OUT_MS = 170F;

    private static final GlanceRules.Key KEY = new GlanceRules.Key();
    private static float arm, panel, flare;
    private static boolean armWanted, panelWanted, wasArm, wasPanel;

    // ---- the texts, built once a tick while the panel shows -----------------------------------------------------------
    private static final Row[] ROWS = new Row[Row.values().length];
    private static int rowN;
    private static String level = "", rank = "", health = "", aetherText = "", food = "", armour = "", taskTitle = "", count = "",
            step = "", next = "", heading = "", distance = "";
    private static int rankColour = Glass.EDGE;
    private static float xpFrac, healthFrac, aetherFrac, foodFrac, armourFrac, bearing;
    private static boolean destination;

    static boolean glancing() {
        return panelWanted || panel > 0.01F;
    }

    /** The arm is drawn by {@link Wrist} while raised or lowering. */
    static boolean armShown() {
        return arm > 0.001F;
    }

    /** The raise, 0 down .. 1 up, eased. */
    static float arm() {
        return armWanted ? HoloTimeline.easeOut(arm) : HoloTimeline.easeInOut(arm);
    }

    /** Lowering: vanilla's hands come back. */
    static boolean lowering() {
        return !armWanted;
    }

    /** The band's flare as the panel leaves it. */
    static float flare() {
        return flare;
    }

    // ---- once a tick --------------------------------------------------------------------------------------------------

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean on = p != null && !HudState.classic() && mc.gui.screen() == null && !mc.gui.hud.isHidden() && !p.isSpectator();
        KEY.tick(on && mc.options.keyPlayerList.isDown());
        armWanted = on && KEY.arm() && armAllowed(mc);
        panelWanted = on && KEY.panel();
        if (wasArm && !armWanted) Wrist.handsBack(); // vanilla raises the hands from below as the arm goes
        if (!wasPanel && panelWanted) {
            flare = 1F;
            HoloAudio.open(false);
        } else if (wasPanel && !panelWanted && p != null) HoloAudio.close(false);
        wasArm = armWanted;
        wasPanel = panelWanted;
        if (p != null && (panelWanted || panel > 0.01F)) build(mc, p);
    }

    /** The arm comes up only in first person, with the band on, no holo menu on the wrist and the holo pipelines working. */
    private static boolean armAllowed(Minecraft mc) {
        if (!mc.options.getCameraType().isFirstPerson() || !HoloPipelines.ok() || HoloBoot.active()) return false;
        if (!dev.awaken.core.api.HoloBand.worn(mc.player)) return false;
        HoloPanel w = HoloClient.wristPanel();
        return w == null || !w.timeline.active();
    }

    private static void build(Minecraft mc, LocalPlayer p) {
        Profile prof = Progression.profile(p);
        level = "LEVEL " + prof.level();
        rank = prof.rank().label + "-RANK";
        rankColour = prof.rank().colour;
        xpFrac = (float) Mth.clamp(prof.xp() / Math.max(1, prof.xpToNext()), 0, 1);
        float max = Math.max(1F, p.getMaxHealth());
        healthFrac = Mth.clamp(p.getHealth() / max, 0, 1);
        health = Math.round(p.getHealth()) + "/" + Math.round(max);
        double ae = HudState.aether();
        aetherFrac = (float) Math.max(0, ae);
        aetherText = Math.round(aetherFrac * 100) + "%";
        int f = p.getFoodData().getFoodLevel();
        foodFrac = f / 20F;
        food = f + "/20";
        int ar = p.getArmorValue();
        armourFrac = Math.min(1F, ar / 20F);
        armour = String.valueOf(ar);
        Font font = mc.font;
        taskTitle = SignalGlance.taskTitle().toUpperCase(Locale.ROOT);
        count = taskTitle.isEmpty() ? "" : SignalGlance.done() + "/" + SignalGlance.steps();
        String s0 = SignalGlance.step(0), s1 = s0 == null ? null : SignalGlance.step(1);
        step = s0 == null ? "" : fit(font, "▸ " + s0, W - 2 * PAD);
        next = s1 == null ? "" : fit(font, "· " + s1, W - 2 * PAD);
        if (!taskTitle.isEmpty()) taskTitle = fit(font, taskTitle, W - 2 * PAD - font.width(count) - 6);
        var h = SignalGlance.heading();
        heading = distance = "";
        if (h != null) {
            destination = h.destination();
            boolean here = h.dimension().equals(p.level().dimension().identifier());
            double dx = h.at().getX() + 0.5 - p.getX(), dz = h.at().getZ() + 0.5 - p.getZ();
            distance = here ? Math.round(Math.sqrt(dx * dx + dz * dz)) + " m" : "elsewhere";
            bearing = here ? Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot()) : Float.NaN;
            heading = fit(font, (destination ? "◆ " : "◇ ") + h.name(), W - 2 * PAD - 22 - font.width(distance));
        }
        rowN = GlanceRules.rows(ae >= 0, !taskTitle.isEmpty(), !step.isEmpty(), !next.isEmpty(), h != null, ROWS);
    }

    private static String fit(Font font, String s, int room) {
        if (font.width(s) <= room) return s;
        return font.plainSubstrByWidth(s, Math.max(10, room - font.width("…"))) + "…";
    }

    // ---- once a frame -------------------------------------------------------------------------------------------------

    static void frame(float dtMs) {
        arm = armWanted ? Math.min(1F, arm + dtMs / ARM_IN_MS) : Math.max(0F, arm - dtMs / ARM_OUT_MS);
        panel = panelWanted ? Math.min(1F, panel + dtMs / PANEL_IN_MS) : Math.max(0F, panel - dtMs / PANEL_OUT_MS);
        flare = Math.max(0F, flare - dtMs / 450F);
    }

    // ---- the panel ----------------------------------------------------------------------------------------------------

    private static final Vector3f WRIST = new Vector3f();

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (panel <= 0.01F || mc.player == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        // where the wrist is on screen (the hand pass's own field of view), the panel just above it
        int side = mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : -1;
        Wrist.band(1F, side, WRIST);
        float tanY = Math.max(0.1F, ViewMath.tanYHand), aspect = sw / (float) sh;
        float wx = (0.5F + 0.5F * WRIST.x / -WRIST.z / (tanY * aspect)) * sw, wy = (0.5F - 0.5F * WRIST.y / -WRIST.z / tanY) * sh;
        int h = 2 * PAD + ROW * rowN + 4 + (hasTask() ? 3 : 0) + (hasHeading() ? 3 : 0);
        int x = Mth.clamp(Math.round(side < 0 ? wx - 24 : wx - W + 24), 8, sw - W - 8);
        int y = Mth.clamp(Math.round(wy - 30 - h), 8, sh - h - 8);
        float k = HoloTimeline.easeOut(panel);
        // the beam: the band's light runs up to the panel's near corner as it unfolds
        float beam = Mth.clamp(4F * panel * (1F - panel), 0, 1) * 0.8F + 0.12F * k;
        int cornerX = side < 0 ? x : x + W, cornerY = y + h;
        if (armShown() && beam > 0.02F) beamLine(g, wx, wy, cornerX, cornerY, beam);
        // unfold from the corner nearest the wrist: wide first, then tall
        g.pose().pushMatrix();
        g.pose().translate(cornerX, cornerY);
        g.pose().scale(0.82F + 0.18F * k, Math.max(0.02F, k * k));
        g.pose().translate(-cornerX, -cornerY);
        HudLook.card(g, x, y, W, h, Glass.EDGE, k, k, 0F, HudLook.FRAME);
        HudLook.glow(g, x - 2, y - 2, W + 4, h + 4, Glass.EDGE, 0.18F * k + 0.5F * flare);
        float a = Mth.clamp((panel - 0.35F) / 0.65F, 0, 1); // the words come in once the glass is up
        int ry = y + PAD;
        for (int i = 0; i < rowN; i++) {
            Row r = ROWS[i];
            if ((r == Row.TASK || r == Row.HEADING) && i > 0) {
                g.fill(x + PAD, ry, x + W - PAD, ry + 1, Glass.a(Glass.EDGE, 0.25F * a));
                ry += 3;
            }
            row(g, font, r, x, ry, a);
            ry += r == Row.LEVEL ? ROW + 4 : ROW;
        }
        g.pose().popMatrix();
    }

    private static boolean hasTask() {
        for (int i = 0; i < rowN; i++) if (ROWS[i] == Row.TASK) return true;
        return false;
    }

    private static boolean hasHeading() {
        for (int i = 0; i < rowN; i++) if (ROWS[i] == Row.HEADING) return true;
        return false;
    }

    private static void row(GuiGraphicsExtractor g, Font font, Row r, int x, int y, float a) {
        int lx = x + PAD, rx = x + W - PAD;
        switch (r) {
            case LEVEL -> {
                diamond(g, lx + 2, y + 4, Glass.a(Glass.EDGE, a));
                HudLook.text(g, font, level, lx + 8, y, Glass.a(Glass.TITLE, a), true);
                HudLook.text(g, font, rank, rx - font.width(rank), y, Glass.a(rankColour, a), true);
                // the XP line under it
                int bx0 = lx, bx1 = rx, by = y + 11;
                g.fill(bx0, by, bx1, by + 1, Glass.a(0x000000, 0.45F * a));
                int lit = bx0 + Math.round((bx1 - bx0) * xpFrac);
                if (lit > bx0) {
                    HudLook.glow(g, bx0 - 2, by - 2, lit - bx0 + 4, 5, Glass.GOOD, 0.4F * a);
                    g.fill(bx0, by, lit, by + 1, Glass.a(Glass.GOOD, a));
                }
            }
            case HEALTH -> bar(g, font, "Health", health, healthFrac, 0xFF6F61, x, y, a, 10);
            case AETHER -> bar(g, font, "Aether", aetherText, aetherFrac, 0xFFE08A, x, y, a, 0);
            case FOOD -> bar(g, font, "Food", food, foodFrac, 0xE8C07A, x, y, a, 10);
            case ARMOUR -> bar(g, font, "Armour", armour, armourFrac, 0xBCCDE3, x, y, a, 10);
            case TASK -> {
                HudLook.text(g, font, taskTitle, lx, y, Glass.a(Glass.EDGE, a), true);
                HudLook.text(g, font, count, rx - font.width(count), y, Glass.a(Glass.LABEL, a), true);
            }
            case STEP -> HudLook.text(g, font, step, lx, y, Glass.a(Glass.TEXT, a), true);
            case NEXT -> HudLook.text(g, font, next, lx, y, Glass.a(Glass.LABEL, 0.8F * a), true);
            case HEADING -> {
                HudLook.text(g, font, heading, lx, y, Glass.a(destination ? Glass.TITLE : Glass.TEXT, a), true);
                HudLook.text(g, font, distance, rx - font.width(distance), y, Glass.a(Glass.LABEL, a), true);
                if (!Float.isNaN(bearing)) needle(g, rx - font.width(distance) - 10, y + 4, bearing, a);
            }
        }
    }

    /** A labelled line: the label, a thin segmented bar and the value at the right. */
    private static void bar(GuiGraphicsExtractor g, Font font, String label, String value, float frac, int colour, int x, int y, float a, int segments) {
        int lx = x + PAD, rx = x + W - PAD;
        HudLook.text(g, font, label, lx, y, Glass.a(Glass.LABEL, a), true);
        HudLook.text(g, font, value, rx - font.width(value), y, Glass.a(Glass.TEXT, a), true);
        int bx0 = lx + 44, bx1 = rx - 36, by = y + 3;
        g.fill(bx0 - 1, by - 1, bx1 + 1, by + 3, Glass.a(0x000000, 0.4F * a));
        int lit = bx0 + Math.round((bx1 - bx0) * frac);
        if (lit > bx0) HudLook.glow(g, bx0 - 2, by - 2, lit - bx0 + 4, 6, colour, 0.35F * a);
        int n = Math.max(1, segments);
        for (int k = 0; k < n; k++) {
            int s0 = bx0 + Math.round((bx1 - bx0 + 1) * k / (float) n), s1 = bx0 + Math.round((bx1 - bx0 + 1) * (k + 1) / (float) n) - (segments > 0 ? 1 : 0);
            g.fill(s0, by, s1, by + 2, Glass.a(0x1A222E, 0.8F * a));
            int l = Math.min(s1, lit);
            if (l > s0) {
                g.fill(s0, by, l, by + 1, Glass.a(mix(colour, 0xFFFFFF, 0.45F), a));
                g.fill(s0, by + 1, l, by + 2, Glass.a(colour, a));
            }
        }
    }

    /** A small compass: a ring of four ticks and a needle toward the place (up is ahead). */
    private static void needle(GuiGraphicsExtractor g, int cx, int cy, float deg, float a) {
        int ring = Glass.a(Glass.EDGE, 0.45F * a);
        g.fill(cx - 5, cy, cx - 3, cy + 1, ring);
        g.fill(cx + 4, cy, cx + 6, cy + 1, ring);
        g.fill(cx, cy - 5, cx + 1, cy - 3, ring);
        g.fill(cx, cy + 4, cx + 1, cy + 6, ring);
        g.pose().pushMatrix();
        g.pose().translate(cx + 0.5F, cy + 0.5F);
        g.pose().rotate(deg * Mth.DEG_TO_RAD);
        g.pose().scale(0.5F, 0.5F);
        g.fill(-1, -9, 1, 0, Glass.a(Glass.AMBER, a));
        g.fill(-1, 0, 1, 5, Glass.a(Glass.LABEL, 0.6F * a));
        g.pose().popMatrix();
    }

    /** The light from the band to the panel: a thin line, brighter at the band. */
    private static void beamLine(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float strength) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 2) return;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(dy, dx));
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
            float s = strength * (1F - 0.7F * t0);
            g.fill(Math.round(len * t0), 0, Math.round(len * t1), 1, Glass.a(Glass.EDGE, s));
        }
        g.pose().popMatrix();
        HudLook.glow(g, Math.round(x0) - 4, Math.round(y0) - 4, 8, 8, Glass.EDGE, strength);
    }

    /** The band's diamond, 5 px tall, centred on (x, y). */
    private static void diamond(GuiGraphicsExtractor g, int x, int y, int argb) {
        g.fill(x, y - 2, x + 1, y + 3, argb);
        g.fill(x - 1, y - 1, x, y + 2, argb);
        g.fill(x + 1, y - 1, x + 2, y + 2, argb);
        g.fill(x - 2, y, x - 1, y + 1, argb);
        g.fill(x + 2, y, x + 3, y + 1, argb);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gr = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | gr << 8 | bl;
    }

    static void reset() {
        KEY.reset();
        arm = panel = flare = 0;
        armWanted = panelWanted = wasArm = wasPanel = false;
        rowN = 0;
    }
}
