package dev.awaken.delve.client;

import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Ore;
import dev.awaken.delve.TuningForkItem;
import dev.awaken.delve.sense.SenseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Delver's Pick's hum (SPEC §34.4): while you hold it, the server names the nearest lode block within its reach;
 * the pick hums at that ore's note (the Tuning Fork's pitch, an octave up) as its head comes round to face it, from
 * nothing outside about 40° to clear when you look straight at it, louder the nearer it is. Round the listener (it's
 * in your hand). Silent when you put it away.
 */
final class PickSong {
    private PickSong() {}

    private static final float CONE = Mth.cos(40F * Mth.DEG_TO_RAD);
    private static int ore = -1;
    private static BlockPos target = BlockPos.ZERO;
    private static SenseLoop hum;

    static void told(DelveNet.PickHum m) {
        ore = m.ore() >= 0 && m.ore() < Ore.values().length ? m.ore() : -1;
        target = BlockPos.of(m.target());
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        float goal = 0F, pitch = 1F;
        if (mc.player != null && mc.level != null && !mc.isPaused() && ore >= 0 && mc.player.getMainHandItem().is(DelveContent.WAKING_PICK.get())) {
            Vec3 eye = mc.player.getEyePosition(), to = Vec3.atCenterOf(target).subtract(eye);
            double d = to.length();
            if (d > 1e-3) {
                float face = (float) (mc.player.getViewVector(1F).dot(to) / d);
                float align = Mth.clamp((face - CONE) / (1F - CONE), 0F, 1F);
                goal = 0.32F * align * align * (6F / (6F + (float) d)) * 2.2F;
                pitch = Math.min(2F, TuningForkItem.pitch(Ore.values()[ore]) * 2F);
            }
        }
        if (hum != null && (hum.isStopped() || hum.age > 40 && !mc.getSoundManager().isActive(hum))) hum = null;
        if (hum != null) {
            hum.goal = goal;
            if (goal > 0.01F) hum.pitchGoal = pitch;
        } else if (goal > 0.015F) {
            hum = new SenseLoop(SenseModule.DEEP_HUM.get(), true, 0, 0, 0, pitch, 0.12F);
            hum.goal = goal;
            mc.getSoundManager().play(hum);
        }
    }

    static void reset() {
        ore = -1;
        if (hum != null) hum.release();
        hum = null;
    }
}
