package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.ArmsRules;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Arms' only HUD (SPEC §24), on surfaces the player already reads: a thin Resonance arc under the crosshair (filling,
 * gold and glowing when the Art is ready), the held heavy's charge on the same arc with the glint's flash, and the
 * dodge's charges as two pips beside the sprint line. Nothing is drawn at rest.
 */
final class ArmsHud {
    private ArmsHud() {}

    private static final int HOLO = 0x7FC3FF, GOLD = 0xE2B450, DIM = 0x0E1E2E;
    private static final int ARC_DOTS = 15;
    private static float shown;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        boolean fights = ArmsPredict.fights(mc.player);
        float charge = ArmsPredict.charge();
        float res = ArmsPredict.resonance / (float) ArmsRules.RESONANCE_MAX;
        boolean busy = fights && (charge >= 0 || res > 0 || ArmsPredict.ticks - ArmsPredict.fightAt < 100);
        shown += ((busy ? 1F : 0F) - shown) * 0.15F;
        if (shown < 0.02F) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        // the arc: 15 dots across the lower half-circle under the crosshair
        boolean full = res >= 1;
        float fill = charge >= 0 ? charge : res;
        int colour = charge >= 0 ? (glinting() ? GOLD : HOLO) : full ? GOLD : HOLO;
        for (int i = 0; i < ARC_DOTS; i++) {
            float f = i / (float) (ARC_DOTS - 1);
            float ang = Mth.PI * (0.15F + 0.7F * f);
            int x = cx + Math.round(Mth.cos(ang) * 11), y = cy + Math.round(Mth.sin(ang) * 11);
            boolean lit = f <= fill;
            g.fill(x, y, x + 1, y + 1, Glass.a(lit ? colour : DIM, (lit ? 0.95F : 0.35F) * shown));
        }
        if (full || glinting()) HudLook.glow(g, cx - 12, cy + 7, 24, 6, GOLD, 0.45F * shown);
        // the dodge's charges, beside the sprint line above the hotbar
        int charges = ArmsPredict.dodgeCharges;
        var style = ArmsPredict.style;
        if (style != null && charges >= 0) {
            Archetype.Dodge d = style.def().dodge();
            int y = g.guiHeight() - 24;
            for (int i = 0; i < d.charges(); i++) {
                int x = cx + 95 + i * 5;
                boolean lit = i < charges;
                g.fill(x, y - 1, x + 3, y + 2, Glass.a(lit ? HOLO : DIM, (lit ? 0.9F : 0.5F) * shown));
            }
        }
    }

    private static boolean glinting() {
        var style = ArmsPredict.style;
        if (!ArmsPredict.charging || style == null) return false;
        Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
        return heavy != null && heavy.charge().isPresent()
                && heavy.charge().get().perfect(heavy.charge().get().min() + (ArmsPredict.ticks - ArmsPredict.chargeStart));
    }
}
