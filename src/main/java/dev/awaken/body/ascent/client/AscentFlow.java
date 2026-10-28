package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentState;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Flow (SPEC §17.6): how well the moves are chaining, 0..1. Each move adds to it (a clean release more than a sloppy
 * one, the boom most), a wall taken at speed takes a third of it, and standing still drains it in about three seconds;
 * in the air it holds. It pays a little back: a stronger pump and a carry that keeps more of its speed. Shown as one
 * thin line of light over the hotbar that grows from the middle and flares at each link.
 */
public final class AscentFlow {
    private AscentFlow() {}

    /** Links further apart than this start a new chain (the server's rule, SPEC §17.6). */
    static final int CHAIN_GAP = 50;

    static float flow, shown, flare;
    static int lastGain = -1000, chain;

    public static float flow() {
        return flow;
    }

    /** A move went off: Flow rises (less as it fills), and the chain counts it. */
    public static void gain(float amount) {
        int now = AscentMoves.ticks;
        chain = now - lastGain <= CHAIN_GAP ? chain + 1 : 1;
        // chained moves are worth more than the same move alone
        float k = chain >= 3 ? 1.25F : 1F;
        flow = Math.min(1F, flow + amount * k * (1F - flow * 0.5F));
        lastGain = now;
        flare = 1F;
    }

    /** A wall taken hard. */
    static void bonk() {
        flow = Math.max(0F, flow - 0.35F);
        chain = 0;
    }

    static void tick(LocalPlayer p) {
        int idle = AscentMoves.ticks - lastGain;
        boolean still = AscentMoves.state == AscentState.NONE && p.onGround() && !dev.awaken.body.client.RunnerMoves.busy();
        if (still && idle > 25) flow = Math.max(0F, flow - 0.015F);
        else if (!still && idle > 80) flow = Math.max(0F, flow - 0.002F);
        if (idle > CHAIN_GAP) chain = 0;
        shown += (flow - shown) * 0.2F;
        flare *= 0.85F;
    }

    /** The pump's factor at this Flow. */
    static double pump() {
        return 1 + dev.awaken.body.ascent.AscentRules.FLOW_PUMP * flow;
    }

    static void reset() {
        flow = shown = flare = 0;
        chain = 0;
        lastGain = -1000;
    }

    // ---- the line -----------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator() || shown < 0.01F) return;
        if (!Progression.profile(mc.player).awakened()) return;
        int cx = g.guiWidth() / 2, half = Math.round(91 * Math.min(1F, shown));
        if (half < 1) return;
        // between the XP bar and the hotbar; whiter as it fills, brighter for a moment at each link
        int y = g.guiHeight() - 23;
        int col = mix(Glass.EDGE, 0xEAF7FF, shown * shown);
        HudLook.glow(g, cx - half - 3, y - 2, half * 2 + 6, 5, col, 0.35F + 0.5F * shown + 0.4F * flare);
        g.fill(cx - half, y, cx + half, y + 1, Glass.a(col, Math.min(1F, 0.55F + 0.35F * shown + 0.3F * flare)));
        if (chain >= 3) {
            // the ends burn brighter while a chain runs
            g.fill(cx - half, y, cx - half + 2, y + 1, Glass.a(0xFFFFFF, 0.9F));
            g.fill(cx + half - 2, y, cx + half, y + 1, Glass.a(0xFFFFFF, 0.9F));
        }
    }

    /**
     * Skyborn's Aether over the top edge of the XP bar, in pale gold: shown while flying or not full, fading out once full
     * on the ground; it flickers when nearly gone and flashes dim when a flight is refused for want of it.
     */
    static void renderAether(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        if (!AscentMoves.has(dev.awaken.body.ascent.AscentPayloads.Tier.SKYBORN)) return;
        boolean flying = AscentMoves.state() == AscentState.SOAR;
        // the clear screen (SPEC §40.6): under the health line, also through a fight
        boolean clear = !dev.awaken.core.api.HudState.classic();
        boolean wanted = flying || AscentMoves.aether < 0.995 || clear && dev.awaken.core.api.HudState.fighting();
        aetherShown += ((wanted ? 1F : 0F) - aetherShown) * 0.08F;
        if (aetherShown < 0.02F) return;
        // Classic: in the free gap between the hearts and the XP bar, two pixels tall
        int full = clear ? dev.awaken.core.api.HudState.VITALS_HALF : 91;
        int cx = g.guiWidth() / 2, half = Math.round(full * (float) AscentMoves.aether);
        int y = clear ? dev.awaken.core.api.HudState.vitalsY(g.guiHeight(), 1) : g.guiHeight() - 32;
        float a = aetherShown;
        if (AscentMoves.aether < 0.2 && flying) a *= 0.6F + 0.4F * (float) Math.abs(Math.sin(AscentMoves.ticks * 0.5));
        if (AscentMoves.ticks - AscentMoves.aetherOutAt < 12) a *= 0.5F;
        if (clear) g.fill(cx - full - 1, y - 1, cx + full + 1, y + 3, Glass.a(0x000000, 0.4F * a));
        g.fill(cx - full, y, cx + full, y + 2, Glass.a(0x2A2410, 0.6F * a));
        if (half > 0) {
            HudLook.glow(g, cx - half - 3, y - 2, half * 2 + 6, 6, 0xFFD966, 0.65F * a);
            g.fill(cx - half, y, cx + half, y + 2, Glass.a(0xFFE08A, a));
        }
    }

    private static float aetherShown;

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gr = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | gr << 8 | bl;
    }
}
