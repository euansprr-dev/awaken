package dev.awaken.arms.client;

import dev.awaken.Awaken;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.ArmsConfig;
import dev.awaken.arms.ArmsRules;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Arms' only HUD (SPEC §25), on surfaces the player already reads: a thin Resonance arc under the crosshair (filling,
 * gold and glowing when the Art is ready), the held heavy's charge on the same arc with the glint's flash, the
 * dodge's charges as two pips beside the sprint line, and a short red shard on a ring round the crosshair pointing
 * to where the last blow on you came from (whatever is in your hand); under 30% health the frame's edges cool and
 * darken, tightening on each heartbeat (still under reduced motion). Nothing is drawn at rest.
 */
final class ArmsHud {
    private ArmsHud() {}

    private static final int HOLO = 0x7FC3FF, GOLD = 0xE2B450, DIM = 0x0E1E2E, HURT = 0xFF6A50;
    private static final int HURT_TICKS = 24;
    private static final int ARC_DOTS = 15;
    private static float shown;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        lowHealth(g, dt.getGameTimeDeltaPartialTick(true));
        hurtShard(g, mc, dt.getGameTimeDeltaPartialTick(true));
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
        int colour = charge >= 0 ? (ArmsPredict.glinting() ? GOLD : HOLO) : full ? GOLD : HOLO;
        for (int i = 0; i < ARC_DOTS; i++) {
            float f = i / (float) (ARC_DOTS - 1);
            float ang = Mth.PI * (0.85F - 0.7F * f); // fills left to right, like any meter
            int x = cx + Math.round(Mth.cos(ang) * 11), y = cy + Math.round(Mth.sin(ang) * 11);
            boolean lit = f <= fill;
            dot(g, x, y, 1, lit ? colour : DIM, (lit ? 0.95F : 0.35F) * shown);
        }
        if (full || ArmsPredict.glinting()) HudLook.glow(g, cx - 12, cy + 7, 24, 6, GOLD, 0.45F * shown);
        // the dodge's charges, beside the sprint line above the hotbar
        int charges = ArmsPredict.dodgeCharges;
        var style = ArmsPredict.style;
        if (style != null && charges >= 0) {
            Archetype.Dodge d = style.def().dodge();
            int y = g.guiHeight() - 24;
            for (int i = 0; i < d.charges(); i++) {
                int x = cx + 95 + i * 5;
                boolean lit = i < charges;
                g.fill(x - 1, y - 2, x + 4, y + 3, Glass.a(0x000000, 0.2F * shown));
                g.fill(x, y - 1, x + 3, y + 2, Glass.a(lit ? HOLO : DIM, (lit ? 0.9F : 0.5F) * shown));
            }
        }
    }

    private static final Identifier EDGE = Awaken.id("textures/gui/arms/low_health.png");
    private static final int COLD = 0x0B1826;

    /** The cold edge of low health: a dark blue falloff from the corners, a touch deeper on each beat. */
    private static void lowHealth(GuiGraphicsExtractor g, float partial) {
        float e = ArmsFeel.edgeWas + (ArmsFeel.edge - ArmsFeel.edgeWas) * partial;
        if (e < 0.01F) return;
        float a = Mth.clamp(e * (0.62F + 0.22F * ArmsFeel.pulse), 0, 0.9F);
        int w = g.guiWidth(), h = g.guiHeight();
        g.blit(RenderPipelines.GUI_TEXTURED, EDGE, 0, 0, 0F, 0F, w, h, 256, 256, 256, 256, ARGB.color(Math.round(a * 255), COLD));
    }

    /** Seven dots across about 30° of a ring, toward the blow (up = ahead), brightest at the middle, fading out. */
    private static void hurtShard(GuiGraphicsExtractor g, Minecraft mc, float partial) {
        float age = ArmsPredict.ticks - ArmsPredict.hurtAt + partial;
        if (age >= HURT_TICKS || age < 0 || !ArmsConfig.enabled()) return;
        float fade = 1F - age / HURT_TICKS;
        float rel = Mth.wrapDegrees(ArmsPredict.hurtYaw - mc.player.getViewYRot(partial)) * Mth.DEG_TO_RAD;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        for (int i = -3; i <= 3; i++) {
            float a = rel + i * 0.087F;
            int x = cx + Math.round(Mth.sin(a) * 22), y = cy - Math.round(Mth.cos(a) * 22);
            float edge = 1F - Math.abs(i) / 4F;
            dot(g, x, y, Math.abs(i) <= 1 ? 2 : 1, HURT, fade * fade * (0.45F + 0.55F * edge));
        }
    }

    /** A dot with a soft dark halo under it, so it reads on a bright sky as well as in a cave. */
    private static void dot(GuiGraphicsExtractor g, int x, int y, int size, int colour, float alpha) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Glass.a(0x000000, 0.22F * alpha));
        g.fill(x, y, x + size, y + size, Glass.a(colour, alpha));
    }
}
