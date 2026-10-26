package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.client.feel.FeelDirector;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * First times are scenes, not toasts. "Your body remembers" (SPEC §17.6): an old world's tiers given back on its first
 * load. Once the player is free (no screen open, on the ground, not moving by rope or wings), the air goes still, light
 * rises around them, the swell lands as the wings unfold once and settle (others see it too), and two lines of light
 * name what came back. About six seconds; nothing to press. The same scene gives wings at a sky platform's core and the
 * leap at the Leaper's Gauntlet's far landing; at that course's chasm a short one (three seconds, at once, no wings)
 * tells the legs what to do.
 */
final class AscentScene {
    private AscentScene() {}

    private static final int LENGTH = 130, WINGS_AT = 34, EDGE_LENGTH = 64;
    static int pending, playing, age = -1, waited, mode, playingMode;

    static void remember(int bits, int sceneMode) {
        pending = bits;
        mode = sceneMode;
        waited = 0;
    }

    /** The short moments that play at once (a trial's edge), not waiting for a free moment on the ground. */
    private static boolean edge(int m) {
        return m == AscentPayloads.LEAP_EDGE || m == AscentPayloads.SKY_EDGE;
    }

    static boolean running() {
        return age >= 0;
    }

    static void tick(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        // a gift's scene holds every tip back (its own tip comes after)
        dev.awaken.core.api.TipHooks.client().hold(AscentScene.class, age >= 0);
        dev.awaken.core.api.Notices.client().hold(AscentScene.class, age >= 0); // and the System's messages (SPEC §26)
        if (pending != 0 && age < 0) {
            boolean free = mc.gui.screen() == null && p.onGround() && !AscentMoves.active() && !dev.awaken.core.api.Notices.client().quiet();
            // never lost: after 30 s it plays wherever you are; the chasm's moment plays at once
            if (free || edge(mode) || ++waited > 600) {
                dev.awaken.core.api.Notices.client().claim(dev.awaken.core.api.Notices.Tier.CRITICAL, "ascent");
                playing = pending;
                playingMode = mode;
                pending = 0;
                age = 0;
                boolean edge = edge(playingMode);
                AscentSounds.local(p, AscentContent.REMEMBER.get(), edge ? 0.7F : 1F, edge ? 1.26F : 1F);
                AscentSounds.duck(edge ? EDGE_LENGTH : LENGTH);
                ClientPacketDistributor.sendToServer(new AscentPayloads.Event(AscentPayloads.EV_REMEMBER, 0));
            }
        }
        if (age < 0) return;
        age++;
        // square the body to the view (standing still it lags up to 50 degrees behind the head): the wings frame you
        p.setYBodyRot(p.getYHeadRot());
        risingLight(p, p, age);
        boolean edge = edge(playingMode);
        if (age == (edge ? 10 : WINGS_AT)) {
            FeelDirector.remember();
            if (!edge && playingMode != AscentPayloads.GIFT_LEAP && (AscentPayloads.Tier.WINGS.in(playing) || AscentPayloads.Tier.SKYBORN.in(playing))) {
                WingLayer.flourish(p.getId(), 46);
            }
        }
        if (age > (edge ? EDGE_LENGTH : LENGTH)) age = -1;
    }

    /** Light rising around a body: thick at first, thinning as the swell settles (others' scenes call it too). */
    static void risingLight(LocalPlayer viewer, net.minecraft.world.entity.Entity who, int t) {
        if (t > 80) return;
        RandomSource r = viewer.getRandom();
        int n = t < 34 ? 2 : t % 2;
        for (int i = 0; i < n; i++) {
            // rising past the eyes (about 3 blocks): first person sees it go up through the view
            double a = r.nextDouble() * Math.PI * 2, d = 0.5 + r.nextDouble() * 1.1;
            MoteParticle.next(r.nextFloat() < 0.3F ? 0xEAF7FF : 0x9FD4FF, 0.7F + r.nextFloat() * 0.5F, 36 + r.nextInt(16), -0.25F, false);
            viewer.level().addParticle(AscentContent.MOTE.get(), who.getX() + Math.cos(a) * d, who.getY() + r.nextDouble() * 0.4,
                    who.getZ() + Math.sin(a) * d, -Math.cos(a) * 0.01, 0.03 + r.nextDouble() * 0.03, -Math.sin(a) * 0.01);
        }
    }

    static void reset() {
        pending = playing = 0;
        age = -1;
    }

    // ---- the words ----------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (age < 0 || mc.player == null || mc.gui.hud.isHidden()) return;
        float t = age + dt.getGameTimeDeltaPartialTick(false);
        int cx = g.guiWidth() / 2, y = (int) (g.guiHeight() * 0.30F);
        boolean edge = edge(playingMode);
        float a1 = edge ? env(t, 2, 10, 46, 62) : env(t, 8, 22, 100, 122), a2 = edge ? env(t, 12, 20, 50, 64)
                : env(t, WINGS_AT + 6, WINGS_AT + 20, 104, 126);
        if (a1 > 0.01F) {
            Component title = Component.translatable(switch (playingMode) {
                case AscentPayloads.GIFT_WINGS -> "awaken.ascent.gift.wings";
                case AscentPayloads.GIFT_LEAP -> "awaken.ascent.gift.leap";
                case AscentPayloads.LEAP_EDGE -> "awaken.ascent.edge.leap";
                case AscentPayloads.GIFT_SKY -> "awaken.ascent.gift.sky";
                case AscentPayloads.SKY_EDGE -> "awaken.ascent.edge.sky";
                case AscentPayloads.GIFT_SOVEREIGN -> "awaken.ascent.gift.sovereign";
                default -> "awaken.ascent.remember.title";
            });
            HudLook.glow(g, cx - mc.font.width(title) - 8, y - 6, mc.font.width(title) * 2 + 16, 22, Glass.EDGE, 0.5F * a1);
            g.pose().pushMatrix();
            g.pose().translate(cx, y);
            g.pose().scale(2F, 2F);
            HudLook.centered(g, mc.font, title.getString(), 0, 0, Glass.a(0xEAF7FF, a1), true);
            g.pose().popMatrix();
        }
        if (a2 > 0.01F) {
            String line = switch (playingMode) {
                case AscentPayloads.GIFT_WINGS -> Component.translatable("awaken.ascent.gift.wings.how").getString();
                case AscentPayloads.GIFT_LEAP -> Component.translatable("awaken.ascent.gift.leap.how").getString();
                case AscentPayloads.LEAP_EDGE -> Component.translatable("awaken.ascent.edge.leap.how").getString();
                case AscentPayloads.GIFT_SKY -> Component.translatable("awaken.ascent.gift.sky.how").getString();
                case AscentPayloads.SKY_EDGE -> Component.translatable("awaken.ascent.edge.sky.how").getString();
                case AscentPayloads.GIFT_SOVEREIGN -> Component.translatable("awaken.ascent.gift.sovereign.how",
                        SovereignClient.ASCEND.getTranslatedKeyMessage()).getString();
                default -> given(playing);
            };
            HudLook.centered(g, mc.font, line, cx, y + 26, Glass.a(Glass.EDGE, a2), true);
        }
    }

    /** What came back, in the ladder's order: "The line · The leap · Wings". */
    private static String given(int bits) {
        StringBuilder b = new StringBuilder();
        for (AscentPayloads.Tier tier : AscentPayloads.Tier.values()) {
            if (!tier.in(bits)) continue;
            if (!b.isEmpty()) b.append("  ·  ");
            b.append(Component.translatable("awaken.ascent.tier." + tier.name().toLowerCase(java.util.Locale.ROOT)).getString());
        }
        return b.toString();
    }

    private static float env(float t, float in0, float in1, float out0, float out1) {
        if (t < in0 || t > out1) return 0;
        if (t < in1) return (t - in0) / (in1 - in0);
        if (t > out0) return 1 - (t - out0) / (out1 - out0);
        return 1;
    }
}
