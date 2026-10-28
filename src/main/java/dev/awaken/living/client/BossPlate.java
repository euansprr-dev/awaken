package dev.awaken.living.client;

import dev.awaken.core.api.BossPlates;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.api.Stances;
import dev.awaken.living.LivingNet;
import dev.awaken.living.combat.PlateRules;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Boss Plate (SPEC §34.1): our bosses' and Wardens' bars drawn as Holo glass at the top of the screen in place of
 * vanilla's. The name in capitals, the level chip and bar in the heat colour against your level (grey, blue, orange,
 * red), diamonds where the phases begin, Arms' stance strip, the boss's own glow on the rim only, and its lines under the
 * plate instead of chat. It lifts off the boss's head when its bar first shows (fades in with reduced motion) and
 * shatters on a kill. Bars the server didn't bind (the Wither, the dragon, other mods') stay vanilla.
 */
public final class BossPlate {
    private BossPlate() {}

    /** One bound bar. */
    static final class Bound {
        final UUID bar;
        int entity, kind, glow;
        float[] marks = new float[0];
        /** When it first drew (ms; 0 not yet), and where on screen it started from (the boss's head; NaN: off screen). */
        long born;
        float fromX = Float.NaN, fromY;
        /** The last frame it drew: where, how big, and what it showed (the shatter and the fly-back replay these). */
        long seen;
        int x, y, w, h;
        float frac = 1, drain = 1;
        long drainAt;
        String name = "";
        final long[] markFlash = new long[8];
        /** The kill's start (ms), or 0. */
        long shattered;

        Bound(UUID bar) {
            this.bar = bar;
        }
    }

    record Said(String text, int kind, int colour, long at) {}

    static final Map<UUID, Bound> BOUND = new HashMap<>();
    private static final Map<UUID, Said> LINES = new HashMap<>(), SIGNATURES = new HashMap<>();
    /** Bars whose plate ended (a kill: the shards; left behind: it fades), drawn by {@link #renderEnds}. */
    private static final List<Bound> ENDING = new ArrayList<>();

    static final int LORD_BAR = 182, WARDEN_BAR = 140, PAD = 8, GAP = 4, MAX_SHOWN = 2;
    static final long FLY_MS = 450, OPEN_MS = 200, LINE_MS = 3000, SIGNATURE_MS = 1200, SHATTER_MS = 600, NAME_HOLD_MS = 1000, FADE_MS = 300;
    private static final int BROKEN = 0xFFD060, STANCE = 0xF2A541, STANCE_HOT = 0xFFE6B0;

    private static int shownThisFrame, boundSeenLastFrame, boundSeenThisFrame;

    /** Whether our plates replace the bound bars (the setting, and a world to read). */
    static boolean on() {
        return LivingClientConfig.bossPlate;
    }

    /** Whether this entity's plate is on screen now (its world plate then steps aside). */
    static boolean onScreen(int entityId) {
        if (!on()) return false;
        long now = Util.getMillis();
        for (Bound b : BOUND.values()) if (b.entity == entityId && b.born != 0 && now - b.seen < 250) return true;
        return false;
    }

    // ---- data -------------------------------------------------------------------------------------------------------

    static void onBind(LivingNet.BossBind m) {
        Bound b = BOUND.computeIfAbsent(m.bar(), Bound::new);
        b.entity = m.entity();
        b.kind = m.kind();
        b.glow = m.glow();
        b.marks = new float[m.marks().size()];
        for (int i = 0; i < b.marks.length; i++) b.marks[i] = m.marks().get(i);
    }

    static void onLine(LivingNet.BossLine m) {
        boolean signature = m.kind() == BossPlates.Line.SIGNATURE.ordinal();
        if (!on() || !BOUND.containsKey(m.bar())) {
            // no plate to put it under: the intro and phase lines go to chat as they used to; a move's name needs no echo
            if (!signature) Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(m.text()).withColor(m.colour()));
            return;
        }
        (signature ? SIGNATURES : LINES).put(m.bar(), new Said(m.text(), m.kind(), m.colour(), Util.getMillis()));
    }

    static void reset() {
        BOUND.clear();
        LINES.clear();
        SIGNATURES.clear();
        ENDING.clear();
    }

    // ---- the camera, for the lift-off -------------------------------------------------------------------------------

    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean camReady;

    static void extract(ExtractLevelRenderStateEvent e) {
        CameraRenderState cam = e.getRenderState().cameraRenderState;
        VIEW_PROJ.set(cam.projectionMatrix).mul(cam.viewRotationMatrix);
        camPos = cam.pos;
        camReady = true;
    }

    /** The GUI position of a world point, into {@code out} (x, y); false when it is behind the camera. */
    private static boolean project(Vec3 p, int guiW, int guiH, float[] out) {
        if (!camReady) return false;
        TMP.set((float) (p.x - camPos.x), (float) (p.y - camPos.y), (float) (p.z - camPos.z), 1F);
        VIEW_PROJ.transform(TMP);
        if (TMP.w <= 0.05F) return false;
        float nx = TMP.x / TMP.w, ny = TMP.y / TMP.w;
        if (Math.abs(nx) > 1.2F || Math.abs(ny) > 1.2F) return false;
        out[0] = (nx + 1) * 0.5F * guiW;
        out[1] = (1 - ny) * 0.5F * guiH;
        return true;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** Before the GUI's layers each frame. */
    static void frameStart() {
        boundSeenLastFrame = boundSeenThisFrame;
        boundSeenThisFrame = 0;
        shownThisFrame = 0;
    }

    /** Vanilla is about to draw a bar: ours draw the plate in its place, and every other bar stays as it is. */
    static void onBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        if (!on()) return;
        Bound b = BOUND.get(e.getBossEvent().getId());
        if (b == null) return;
        e.setCanceled(true);
        boundSeenThisFrame++;
        long now = Util.getMillis();
        if (shownThisFrame >= MAX_SHOWN) {
            // a third plate waits; the second counts it
            e.setIncrement(0);
            b.seen = now;
            return;
        }
        shownThisFrame++;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphicsExtractor g = e.getGuiGraphics();
        Entity ent = mc.level == null ? null : mc.level.getEntity(b.entity);
        LivingEntity le = ent instanceof LivingEntity l ? l : null;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int barW = b.kind == BossPlates.Kind.WARDEN.ordinal() ? WARDEN_BAR : LORD_BAR;
        b.w = barW + PAD * 2;
        boolean stance = le != null && (Stances.broken(le) || Stances.share(le) > 0.01F);
        b.h = 36 + (stance ? 4 : 0);
        b.x = sw / 2 - b.w / 2;
        b.y = Math.max(2, e.getY() - 9);
        if (b.born == 0 || now - b.seen > 2000) {
            // first sight (or back after a while): lift off the boss's head
            b.born = now;
            float[] at = new float[2];
            if (ent != null && project(ent.getEyePosition().add(0, ent.getBbHeight() * 0.35, 0), sw, sh, at)) {
                b.fromX = at[0];
                b.fromY = at[1];
            } else {
                b.fromX = Float.NaN;
            }
        }
        b.seen = now;
        float frac = le != null ? Mth.clamp(le.getHealth() / Math.max(1F, le.getMaxHealth()), 0, 1) : Mth.clamp(e.getBossEvent().getProgress(), 0, 1);
        track(b, frac, now);
        b.name = le != null ? cleanName(le.getDisplayName().getString()) : cleanName(e.getBossEvent().getName().getString());
        int extra = shownThisFrame == MAX_SHOWN ? Math.max(0, boundSeenLastFrame - MAX_SHOWN) : 0;

        // the flight: from the head to the slot, small to full, then the sheet opens downward
        long age = now - b.born;
        boolean reduced = FirstPerson.reducedMotion.getAsBoolean();
        float alpha = 1, scale = 1, cx = b.x + b.w / 2F, cy = b.y + b.h / 2F, open = 1;
        if (reduced || Float.isNaN(b.fromX)) {
            alpha = Mth.clamp(age / (float) FADE_MS, 0, 1);
        } else if (age < FLY_MS + OPEN_MS) {
            float t = Mth.clamp(age / (float) FLY_MS, 0, 1), k = 1 - (1 - t) * (1 - t) * (1 - t);
            cx = Mth.lerp(k, b.fromX, cx);
            cy = Mth.lerp(k, b.fromY, cy);
            scale = Mth.lerp(k, 0.35F, 1F);
            alpha = Mth.clamp(t * 3, 0, 1);
            open = Mth.clamp((age - FLY_MS) / (float) OPEN_MS, 0.18F, 1);
        }
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        pose.translate(-b.w / 2F, -b.h / 2F);
        draw(g, mc.font, b, le, frac, stance, alpha, open, now, extra);
        pose.popMatrix();

        // its lines under it (not while it flies)
        int below = b.h + GAP;
        if (age >= FLY_MS || reduced) below += lines(g, mc.font, b, now, b.y + b.h + 3);
        e.setIncrement(below);
    }

    /** The amber drain of what was just lost, and the phase marks' flash as health crosses one. */
    private static void track(Bound b, float frac, long now) {
        if (frac < b.frac) {
            for (int i = 0; i < b.marks.length && i < b.markFlash.length; i++) {
                if (b.frac >= b.marks[i] && frac < b.marks[i]) b.markFlash[i] = now;
            }
            if (b.drain < b.frac) b.drain = b.frac;
            b.drainAt = now;
        } else if (frac > b.frac) {
            b.drain = frac; // healed (or a reset)
        }
        b.frac = frac;
        if (now - b.drainAt > 500) b.drain = Math.max(frac, b.drain - (now - b.drainAt - 500) / 1500F);
    }

    private static String cleanName(String s) {
        int dot = s.indexOf(" · ");
        return (dot > 0 ? s.substring(0, dot) : s).toUpperCase(Locale.ROOT);
    }

    /** The plate itself at (0, 0) in the current pose. */
    private static void draw(GuiGraphicsExtractor g, Font font, Bound b, @Nullable LivingEntity le, float frac, boolean stance, float alpha,
                             float open, long now, int extra) {
        UiCanvas c = GuiCanvas.of(g);
        int w = b.w, h = Math.max(6, Math.round(b.h * open));
        Glass.panel(c, 0, 0, w, h, b.glow, alpha);
        if (open < 0.99F) return;
        Minecraft mc = Minecraft.getInstance();
        int mine = mc.player == null ? 1 : Progression.profile(mc.player).level();
        CreatureTag tag = le == null ? CreatureTag.NONE : CreatureTag.of(le);
        int lv = tag.level();
        int heat = lv > 0 ? LivingFx.heatColour(lv, mine) : Glass.EDGE;
        boolean hard = lv > 0 && PlateRules.heat(lv, mine) == PlateRules.Heat.HARD;
        float pulse = hard ? 0.75F + 0.25F * Mth.sin(now / 160F) : 1;
        // the level chip (right) and the name (left)
        int chipR = w - PAD + 1, chipL = chipR;
        if (lv > 0) {
            String lvText = "Lv " + lv;
            chipL = chipR - font.width(lvText) - 6;
            c.gradient(chipL, 3, chipR, 14, Glass.a(heat, 0.30F * pulse * alpha), Glass.a(heat, 0.16F * pulse * alpha));
            c.fill(chipL, 14, chipR, 15, Glass.a(heat, 0.85F * pulse * alpha));
            c.text(font, lvText, chipL + 3, 5, Glass.a(heat, alpha), false);
        }
        String name = b.name;
        int room = chipL - PAD - 6;
        if (font.width(name) > room) name = font.plainSubstrByWidth(name, Math.max(10, room - font.width("…"))) + "…";
        c.text(font, name, PAD, 5, Glass.a(Glass.TITLE, alpha), true);
        String title = b.kind == BossPlates.Kind.WARDEN.ordinal() ? "Warden" : tag.title();
        if (!title.isEmpty()) {
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(PAD, 15.5F);
            pose.scale(0.75F, 0.75F);
            c.text(font, title, 0, 0, Glass.a(Glass.LABEL, alpha * 0.95F), false);
            pose.popMatrix();
        }
        // the bar: ten segments in the heat colour, amber for what was just lost
        int bx0 = PAD, bx1 = w - PAD, by0 = 24, by1 = 29;
        segments(c, bx0, bx1, by0, by1, frac, b.drain, heat, alpha, frac < 0.25F && lv > 0);
        // the phase marks: a diamond over the bar where each phase begins; white for a second as it is crossed
        for (int i = 0; i < b.marks.length; i++) {
            float m = b.marks[i];
            if (m <= 0 || m >= 1) continue;
            int mx = Math.round(bx0 + (bx1 - bx0) * m);
            long since = i < b.markFlash.length ? now - b.markFlash[i] : Long.MAX_VALUE;
            boolean flash = since < 1000;
            int col = flash ? 0xFFFFFF : frac < m ? Glass.LABEL : b.glow;
            float a = alpha * (flash ? 1 : frac < m ? 0.55F : 0.95F);
            c.fill(mx, by0 - 1, mx + 1, by1 + 1, Glass.a(col, a));
            c.fill(mx - 1, by0 - 3, mx + 2, by0 - 2, Glass.a(col, a));
            c.fill(mx, by0 - 4, mx + 1, by0 - 1, Glass.a(col, a));
            if (flash) c.fill(mx - 2, by0 - 2, mx + 3, by1 + 2, Glass.a(0xFFFFFF, 0.25F * (1 - since / 1000F) * alpha));
        }
        if (stance && le != null) stanceStrip(c, font, le, bx0, bx1, by1 + 2, alpha, now);
        if (extra > 0) c.text(font, "+" + extra, w + 4, h / 2 - 4, Glass.a(Glass.LABEL, alpha), true);
    }

    private static void segments(UiCanvas c, int bx0, int bx1, int by0, int by1, float frac, float drain, int heat, float alpha, boolean low) {
        float seg = (bx1 - bx0) / 10F;
        int top = low ? 0xFF9A8A : heat, bottom = Glass.darken(low ? 0xFF7A6A : heat);
        for (int k = 0; k < 10; k++) {
            float s0 = k / 10F, s1 = (k + 1) / 10F;
            int x0 = Math.round(bx0 + k * seg) + (k == 0 ? 0 : 1), x1 = Math.round(bx0 + (k + 1) * seg);
            c.fill(x0, by0, x1, by1, Glass.a(0x05080E, 0.70F * alpha));
            float lit = Mth.clamp((frac - s0) / (s1 - s0), 0, 1);
            int xl = x0 + Math.round((x1 - x0) * lit);
            if (lit > 0) c.gradient(x0, by0, xl, by1, Glass.a(top, alpha), Glass.a(bottom, alpha));
            float lost = Mth.clamp((drain - s0) / (s1 - s0), 0, 1);
            int xd = x0 + Math.round((x1 - x0) * lost);
            if (xd > xl) c.gradient(xl, by0, xd, by1, Glass.a(LivingFx.AMBER, alpha), Glass.a(0xF2A541, alpha));
        }
    }

    /** Arms' stance: one strip filling from both ends to the middle, gold and BROKEN while broken. */
    private static void stanceStrip(UiCanvas c, Font font, LivingEntity e, int x0, int x1, int y0, float alpha, long now) {
        int y1 = y0 + 2;
        c.fill(x0, y0, x1, y1, Glass.a(0x05080E, 0.7F * alpha));
        if (Stances.broken(e)) {
            float pulse = 0.7F + 0.3F * Mth.sin(now / 110F);
            c.fill(x0, y0, x1, y1, Glass.a(BROKEN, pulse * alpha));
            String s = "BROKEN";
            c.text(font, s, (x0 + x1) / 2 - font.width(s) / 2, y1 + 2, Glass.a(BROKEN, pulse * alpha), true);
            return;
        }
        float share = Mth.clamp(Stances.share(e), 0, 1);
        int half = Math.round((x1 - x0) / 2F * share), col = share > 0.75F ? STANCE_HOT : STANCE;
        c.fill(x0, y0, x0 + half, y1, Glass.a(col, alpha));
        c.fill(x1 - half, y0, x1, y1, Glass.a(col, alpha));
    }

    /** The plate's line (3 s) and a signature move's name (1.2 s, small caps) under it; returns the height used. */
    private static int lines(GuiGraphicsExtractor g, Font font, Bound b, long now, int y) {
        UiCanvas c = GuiCanvas.of(g);
        int used = 0, cx = b.x + b.w / 2;
        Said sig = SIGNATURES.get(b.bar);
        if (sig != null && now - sig.at() < SIGNATURE_MS) {
            float t = (now - sig.at()) / (float) SIGNATURE_MS, a = Math.min(1, Math.min(t * 8, (1 - t) * 5));
            String s = sig.text().toUpperCase(Locale.ROOT);
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(cx, y);
            pose.scale(0.75F, 0.75F);
            c.text(font, s, -font.width(s) / 2, 0, Glass.a(sig.colour(), a), true);
            pose.popMatrix();
            used += 8;
        }
        Said line = LINES.get(b.bar);
        if (line != null && now - line.at() < LINE_MS) {
            float t = (now - line.at()) / (float) LINE_MS, a = Math.min(1, Math.min(t * 10, (1 - t) * 6));
            String s = "“" + line.text() + "”";
            int maxW = Math.max(b.w + 60, 240);
            if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, maxW - font.width("…")) + "…";
            c.text(font, s, cx - font.width(s) / 2, y + used, Glass.a(line.colour(), a), true);
            used += 11;
        }
        return used;
    }

    // ---- the ends: a kill's shards, or a plate left behind fading out ----------------------------------------------

    /** A layer over the boss bars: plates whose bars went away this second. */
    static void renderEnds(GuiGraphicsExtractor g, DeltaTracker dt) {
        long now = Util.getMillis();
        Minecraft mc = Minecraft.getInstance();
        // a bar that stopped drawing ends here: shattered if its creature died, faded if we walked away
        for (Iterator<Bound> it = BOUND.values().iterator(); it.hasNext(); ) {
            Bound b = it.next();
            if (b.born == 0 || now - b.seen < 120) continue;
            Entity ent = mc.level == null ? null : mc.level.getEntity(b.entity);
            boolean dead = ent instanceof LivingEntity le ? le.isDeadOrDying() : b.frac <= 0.02F;
            if (now - b.seen < 400 && on()) {
                b.shattered = dead ? now : -now; // negative: the fade
                ENDING.add(b);
            }
            if (dead || now - b.seen > 60_000) it.remove(); else b.born = 0;
        }
        if (ENDING.isEmpty()) return;
        UiCanvas c = GuiCanvas.of(g);
        for (Iterator<Bound> it = ENDING.iterator(); it.hasNext(); ) {
            Bound b = it.next();
            boolean kill = b.shattered > 0;
            long age = now - Math.abs(b.shattered);
            long life = kill ? NAME_HOLD_MS + FADE_MS : FADE_MS;
            if (age > life) {
                it.remove();
                continue;
            }
            if (!kill) {
                float a = 1 - age / (float) FADE_MS;
                Glass.panel(c, b.x, b.y, b.w, b.h, b.glow, a * 0.8F);
                continue;
            }
            // the name holds; the segments fall away as shards, turning to the boss's glow
            float nameA = age < NAME_HOLD_MS ? 1 : 1 - (age - NAME_HOLD_MS) / (float) FADE_MS;
            Glass.panel(c, b.x, b.y, b.w, 18, b.glow, nameA * 0.85F);
            c.text(mc.font, b.name, b.x + b.w / 2 - mc.font.width(b.name) / 2, b.y + 5, Glass.a(Glass.TITLE, nameA), true);
            if (age < SHATTER_MS) {
                float t = age / (float) SHATTER_MS;
                int bx0 = b.x + PAD, seg = (b.w - PAD * 2) / 10;
                for (int k = 0; k < 10; k++) {
                    float drift = (k - 4.5F) * 2.2F * t, fall = 26 * t * t + (k % 3) * 3 * t;
                    int x0 = Math.round(bx0 + k * seg + drift), y0 = Math.round(b.y + 22 + fall);
                    int sz = Math.max(1, Math.round((seg - 2) * (1 - t * 0.6F)));
                    c.fill(x0, y0, x0 + sz, y0 + Math.max(1, Math.round(4 * (1 - t))), Glass.a(k % 2 == 0 ? b.glow : 0xFFFFFF, 1 - t));
                }
            }
        }
    }
}
