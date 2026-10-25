package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.client.feel.FeelDirector;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.lens.client.LensClient;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Tether Vow, as it's seen and heard (SPEC §17.7). The call: the vault's light gathers and rises toward its mast,
 * the colours go deep and cold, a low note swells. The line: the rope leaps from your hand to the pylon's top and
 * pulls you up onto it, "The line is yours.", then how to throw it, then the mast to reach, pulsing over the roof.
 * Kept: the swell of a first time and the colours come back. Lapsed: the mast simply waits.
 */
final class VowScene {
    private VowScene() {}

    static int stage = -1, age;
    static double mx, my, mz, ax, ay, az;
    /** The lines on screen: a title (large) and a line under it, each with when it started and how long it stays. */
    private static Component title, line;
    private static int titleAt = -1000, titleFor, lineAt = -1000, lineFor;
    private static int clock;

    static void on(AscentPayloads.Vow v) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        stage = v.stage();
        age = 0;
        switch (v.stage()) {
            case 0 -> { // the call
                mx = v.mx();
                my = v.my();
                mz = v.mz();
                LensClient.override("awaken:vow", 40);
                AscentSounds.duck(80);
                AscentSounds.local(p, AscentContent.VOW_CALL.get(), 1F, 1F);
                say(Component.translatable("awaken.ascent.vow.call"), 70, false);
            }
            case 1 -> { // the line
                ax = v.ax();
                ay = v.ay();
                az = v.az();
                AscentSounds.local(p, AscentContent.VOW_LINE.get(), 1F, 1F);
                FeelDirector.launch(1F);
                if (!Double.isNaN(ax)) AscentMoves.zipTo(p, ax, ay, az);
                say(Component.translatable("awaken.ascent.vow.line"), 90, true);
            }
            case 2 -> { // kept
                LensClient.clearOverride(40);
                AscentSounds.local(p, AscentContent.REMEMBER.get(), 1F, 1.1F);
                AscentFlow.gain(1F);
                FeelDirector.remember();
                say(Component.translatable("awaken.ascent.vow.kept"), 110, true);
                line = null;
                stage = -1;
            }
            default -> { // lapsed
                LensClient.clearOverride(40);
                say(Component.translatable("awaken.ascent.vow.lapsed"), 90, false);
                stage = -1;
            }
        }
    }

    private static void say(Component c, int life, boolean big) {
        if (big) {
            title = c;
            titleAt = clock;
            titleFor = life;
        } else {
            line = c;
            lineAt = clock;
            lineFor = life;
        }
    }

    static void tick(LocalPlayer p) {
        clock++;
        if (stage < 0) return;
        age++;
        RandomSource r = p.getRandom();
        if (stage == 0) {
            // the vault's light gathers off the paving round you and drifts up toward the mast
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = 0.8 + r.nextDouble() * 2.5;
                double x = p.getX() + Math.cos(a) * d, y = p.getY() + 0.1, z = p.getZ() + Math.sin(a) * d;
                Vec3 to = new Vec3(mx - x, my - y, mz - z).normalize().scale(0.12 + r.nextDouble() * 0.08);
                MoteParticle.next(r.nextFloat() < 0.3F ? 0xEAF7FF : 0x9FD4FF, 0.8F + r.nextFloat() * 0.5F, 50 + r.nextInt(20), -0.02F, false);
                p.level().addParticle(AscentContent.MOTE.get(), x, y, z, to.x, to.y, to.z);
            }
        } else if (stage == 1) {
            if (age == 50) {
                say(Component.translatable("awaken.ascent.vow.how", AwakenKeys.MOVEMENT.getTranslatedKeyMessage()), 160, false);
            }
            if (age == 100) LensClient.clearOverride(60);
            if (age == 220) say(Component.translatable("awaken.ascent.vow.mast"), 100000, false);
            // the mast calls: a slow ring of light round its tip
            if (age % 30 == 0) AscentFx.ring(mx, my, mz, new Vec3(0, 1, 0), 0.6F);
        }
    }

    static void reset() {
        stage = -1;
        title = line = null;
    }

    // ---- the words ----------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int cx = g.guiWidth() / 2, y = (int) (g.guiHeight() * 0.28F);
        if (title != null) {
            float a = env(clock + pt - titleAt, titleFor);
            if (a <= 0.01F && clock - titleAt > titleFor) title = null;
            else if (a > 0.01F) {
                int w = mc.font.width(title);
                HudLook.glow(g, cx - w - 8, y - 6, w * 2 + 16, 22, Glass.EDGE, 0.5F * a);
                g.pose().pushMatrix();
                g.pose().translate(cx, y);
                g.pose().scale(2F, 2F);
                HudLook.centered(g, mc.font, title.getString(), 0, 0, Glass.a(0xEAF7FF, a), true);
                g.pose().popMatrix();
            }
        }
        if (line != null) {
            float a = env(clock + pt - lineAt, lineFor);
            if (a <= 0.01F && clock - lineAt > lineFor) line = null;
            else if (a > 0.01F) HudLook.centered(g, mc.font, line.getString(), cx, y + 26, Glass.a(Glass.EDGE, a), true);
        }
    }

    /** Fades in over 12 ticks, holds, fades out over the last 20. */
    private static float env(float t, int life) {
        if (t < 0 || t > life) return 0;
        return Math.min(1, Math.min(t / 12F, (life - t) / 20F));
    }
}
