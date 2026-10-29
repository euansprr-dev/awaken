package dev.awaken.binoculars.client;

import dev.awaken.binoculars.BinocularsNet;
import dev.awaken.binoculars.BinocularsSounds;
import dev.awaken.binoculars.Spotter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;

/**
 * The glass's sign that a find is coming (SPEC §39.10): a ring of {@value #TICKS} ticks round the centre of the view
 * that light one by one, clockwise from the top, while the server holds the look on something you haven't found, with
 * four corner brackets closing in as it fills. When the find lands the ring flashes and the brackets turn into the find
 * cue's diamond and fade; slipping off fades it where it stood. Drawn only from what the server says
 * ({@code awaken:binoculars_spot}): nothing ever shows over something already found.
 */
final class GlassSpot {
    private GlassSpot() {}

    static final int TICKS = 24;
    /** The ring's radius and the brackets' travel (GUI pixels). */
    private static final float RING = 11F, BRACKET_FAR = 19F, BRACKET_NEAR = 13F;
    private static final float APPEAR_MS = 140F, LAND_MS = 650F, FADE_MS = 180F;
    /** The last tick waits for the server's word. */
    private static final float WAIT_AT = 0.97F;
    private static final int LIT = 0xFFC86A, DIM = 0xF1E6CF, FLASH = 0xFFF6E0;

    private enum Phase { NONE, HOLD, FOUND, LOST }

    private static Phase phase = Phase.NONE;
    private static long since;
    private static float holdMs = Spotter.HOLD * 50F, fillAtEnd;

    static void onSpot(BinocularsNet.Spot msg) {
        long now = System.nanoTime();
        if (!Glass.seeing()) {
            phase = Phase.NONE;
            return;
        }
        Spotter.Phase p = msg.phase() >= 0 && msg.phase() < Spotter.Phase.values().length ? Spotter.Phase.values()[msg.phase()] : Spotter.Phase.LOST;
        switch (p) {
            case HOLD -> {
                phase = Phase.HOLD;
                holdMs = Math.max(1, msg.hold()) * 50F;
                since = now;
            }
            case FOUND -> {
                phase = Phase.FOUND;
                since = now;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BinocularsSounds.STEP.get(), 1.7F, 0.4F));
            }
            case LOST -> {
                if (phase == Phase.HOLD) {
                    fillAtEnd = fill(now);
                    phase = Phase.LOST;
                    since = now;
                }
            }
        }
    }

    private static float fill(long now) {
        return Math.min(WAIT_AT, (now - since) / 1_000_000F / holdMs);
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    static void reset() {
        phase = Phase.NONE;
    }

    /** Over the lens edge, at the centre; {@code a} is the edge's opacity. */
    static void render(GuiGraphicsExtractor g, float a, float cx, float cy) {
        if (phase == Phase.NONE) return;
        if (!Glass.seeing()) {
            phase = Phase.NONE;
            return;
        }
        long now = System.nanoTime();
        float ms = (now - since) / 1_000_000F;
        float fill, alpha, spread = 0, turn = 0, flash = 0;
        float near = BRACKET_NEAR;
        switch (phase) {
            case HOLD -> {
                fill = fill(now);
                alpha = ease(ms / APPEAR_MS);
            }
            case FOUND -> {
                float t = ms / LAND_MS;
                if (t >= 1) {
                    phase = Phase.NONE;
                    return;
                }
                fill = 1;
                flash = 1 - ease(t / 0.35F);
                turn = Glass.reducedMotion() ? 0 : ease(t / 0.3F);
                near = BRACKET_NEAR - 5F * turn; // the brackets close to the cue's diamond
                spread = Glass.reducedMotion() ? 0 : 6F * ease(t);
                alpha = 1 - ease((t - 0.45F) / 0.55F);
            }
            case LOST -> {
                fill = fillAtEnd;
                alpha = 1 - ms / FADE_MS;
                if (alpha <= 0) {
                    phase = Phase.NONE;
                    return;
                }
            }
            default -> {
                return;
            }
        }
        alpha *= a;
        if (alpha <= 0.01F) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(0.5F, 0.5F); // half-pixel steps: the ticks are one pixel wide at any GUI scale
        // the ring
        int lit = (int) Math.floor(fill * TICKS + 1e-4);
        float r2 = (RING + spread) * 2;
        for (int i = 0; i < TICKS; i++) {
            boolean on = i < lit;
            boolean lead = on && i == lit - 1 && phase == Phase.HOLD;
            int col = flash > 0 ? lerpColour(on ? LIT : DIM, FLASH, flash) : lead ? FLASH : on ? LIT : DIM;
            float ta = alpha * (on ? (lead ? 1F : 0.9F) : 0.3F);
            pose.pushMatrix();
            pose.rotate((float) (Math.PI * 2 * i / TICKS));
            int len = on ? 3 : 2;
            g.fill(-1, Math.round(-r2 - len), 1, Math.round(-r2 + len), ARGB.color(Math.round(ta * 255), col));
            pose.popMatrix();
        }
        // the brackets, closing in as it fills, turned to a diamond when it lands
        float br = (BRACKET_FAR + (near - BRACKET_FAR) * ease(phase == Phase.FOUND ? 1 : fill)) * 2;
        int bc = ARGB.color(Math.round(alpha * (0.55F + 0.45F * Math.max(fill, flash)) * 255), flash > 0 ? lerpColour(LIT, FLASH, flash) : LIT);
        pose.pushMatrix();
        pose.rotate((float) (Math.PI / 4 * turn));
        int s = Math.round(br * 0.72F), arm = 7;
        for (int q = 0; q < 4; q++) {
            pose.pushMatrix();
            pose.rotate((float) (Math.PI / 2 * q));
            g.fill(s - arm, -s - 1, s + 1, -s + 1, bc);
            g.fill(s - 1, -s - 1, s + 1, -s + arm, bc);
            pose.popMatrix();
        }
        pose.popMatrix();
        pose.popMatrix();
    }

    private static int lerpColour(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (gr << 8) | b;
    }
}
