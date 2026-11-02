package dev.awaken.kin.client;

import dev.awaken.Awaken;
import dev.awaken.core.api.HudState;
import dev.awaken.kin.KinPlates;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The ride line (SPEC §43.17; Euan 2026-11-02): on the clear screen, the mount's health is one warm line just over your
 * own health line, its name at its left end, in place of vanilla's row of mount hearts (which the clear screen now
 * hides with the rest of vanilla's vitals). It shows for 3 s when you climb on, and again while the mount is hurt or
 * you're in a fight; low (a third or less), it breathes amber, never red. The jump bar is vanilla's, as before; Classic
 * keeps vanilla's hearts. Nothing allocated per frame.
 */
final class RideLine {
    private RideLine() {}

    static final Identifier LAYER = Awaken.id("ride_line");
    /** Half the line's width, and how far over the health row it sits (GUI px). */
    static final int HALF = 46, OVER = 6;
    private static final int CORE = 0xF2C46A, TOP = 0xFFF1C8, EMPTY = 0x2A2010, LOW = 0xF2A541, LOW_TOP = 0xFFCF7A;

    private static int mountId = -1;
    private static long mountedAt, ticks;
    private static float alpha;
    private static long lastNanos;

    static void tick() {
        ticks++;
        LocalPlayer p = Minecraft.getInstance().player;
        int id = p != null && p.getVehicle() instanceof LivingEntity m ? m.getId() : -1;
        if (id != mountId) {
            mountId = id;
            mountedAt = ticks;
        }
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = System.nanoTime();
        float frameMs = lastNanos == 0 ? 16F : Math.min(100F, (now - lastNanos) / 1e6F);
        lastNanos = now;
        boolean can = p != null && !mc.gui.hud.isHidden() && !p.isSpectator() && !HudState.classic() && p.getVehicle() instanceof LivingEntity;
        LivingEntity mount = can ? (LivingEntity) p.getVehicle() : null;
        float frac = mount == null ? 1F : Mth.clamp(mount.getHealth() / Math.max(1F, mount.getMaxHealth()), 0, 1);
        boolean want = mount != null && KinPlates.rideShows(ticks - mountedAt, frac, HudState.fighting());
        // it opens from its middle as it comes and closes to it as it goes, as the vitals do (about 200 ms)
        alpha = Mth.clamp(alpha + (want ? 1 : -1) * frameMs / 200F, 0, 1);
        if (mount == null || alpha < 0.01F) return;
        int cx = g.guiWidth() / 2, y = HudState.vitalsY(g.guiHeight(), 0) - OVER;
        float open = alpha * alpha * (3 - 2 * alpha);
        int lo = Math.round(cx - HALF * open), hi = Math.round(cx + HALF * open), x0 = cx - HALF, x1 = cx + HALF;
        boolean low = frac <= 1 / 3F;
        float breathe = low ? 0.5F + 0.5F * Mth.sin(now / 1e9F * Mth.TWO_PI * 1.2F) : 0F;
        int core = low ? LOW : CORE, top = low ? LOW_TOP : TOP;
        g.fill(lo - 1, y - 1, hi + 1, y + 3, Glass.a(0x000000, 0.42F * alpha));
        g.fill(lo, y, hi, y + 2, Glass.a(EMPTY, 0.7F * alpha));
        int lit = Math.min(hi, x0 + Math.round((x1 - x0) * frac));
        if (lit > lo) {
            HudLook.glow(g, lo - 3, y - 2, lit - lo + 6, 6, core, (0.4F + 0.35F * breathe) * alpha);
            g.fill(lo, y, lit, y + 1, Glass.a(top, alpha));
            g.fill(lo, y + 1, lit, y + 2, Glass.a(core, alpha));
        }
        // its name at the left end, small, in the line's warmth
        String name = KinPlate.name(mount);
        float s = 0.75F;
        int w = Math.round(mc.font.width(name) * s);
        g.pose().pushMatrix();
        g.pose().translate(lo - 5 - w, y - 2);
        g.pose().scale(s, s);
        HudLook.text(g, mc.font, name, 0, 0, Glass.a(top, 0.95F * alpha), false);
        g.pose().popMatrix();
    }
}
