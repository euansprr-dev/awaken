package dev.awaken.delve.client;

import dev.awaken.delve.DelveContent;
import dev.awaken.delve.HeldLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Motes in your light (SPEC §34.5): underground, while you carry a light (in either hand) or wear the miner's lamp,
 * dust hangs in the air the light reaches, drifting, at most {@value #MAX} at once. Lit by the world, so it shows only
 * where light falls and fades into the dark past it; tinted a little toward the light's colour. Mostly in front of you,
 * where you look. Half as many at Decreased particles, none at Minimal.
 */
final class Motes {
    private Motes() {}

    static final int MAX = 30;
    /** The mean mote's life (ticks): the spawn rate is what keeps {@value #MAX} alive. */
    private static final float LIFE = 90F;
    private static final int DUST = 0xC9C0B0;
    private static final RandomSource R = RandomSource.create();
    private static final BlockPos.MutableBlockPos AT = new BlockPos.MutableBlockPos();
    private static float owed;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !SenseState.active || SenseState.underwater || SenseState.under < 0.3F) return;
        ParticleStatus ps = mc.options.particles().get();
        if (ps == ParticleStatus.MINIMAL) return;
        HeldLight.Kind main = HeldLight.of(mc.player.getMainHandItem()), off = HeldLight.of(mc.player.getOffhandItem());
        HeldLight.Kind light = HeldLight.offhandWins(mc.player.getMainHandItem(), mc.player.getOffhandItem()) ? off : main;
        int emission = light.emission(), rgb = light.rgb();
        if (mc.player.getItemBySlot(EquipmentSlot.HEAD).is(DelveContent.MINERS_LAMP.get()) && emission < 12) {
            emission = 12;
            rgb = HeldLight.FLAME;
        }
        if (emission <= 0) return;
        float reach = Math.min(HeldLight.MAX_REACH, emission * HeldLight.REACH_PER_LEVEL) * 0.7F;
        owed += MAX / LIFE * SenseState.under * (ps == ParticleStatus.DECREASED ? 0.5F : 1F);
        int tint = mix(DUST, rgb, 0.35F);
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1F);
        for (int tries = 0; owed >= 1F && tries < 4; tries++) {
            owed -= 1F;
            // a point in a ball round the eyes, most of them in front
            double r = reach * Math.cbrt(R.nextDouble()) + 0.8;
            Vec3 d = new Vec3(R.nextGaussian(), R.nextGaussian() * 0.6, R.nextGaussian()).normalize();
            if (d.dot(look) < 0 && R.nextFloat() < 0.75F) d = d.scale(-1);
            double x = eye.x + d.x * r, y = eye.y + d.y * r - 0.3, z = eye.z + d.z * r;
            AT.set(x, y, z);
            if (!level.getBlockState(AT).isAir()) continue;
            DelveParticles.spawn(level, DelveContent.LIGHT_MOTE.get(), tint, 1F, x, y, z,
                    (R.nextDouble() - 0.5) * 0.004, (R.nextDouble() - 0.5) * 0.003, (R.nextDouble() - 0.5) * 0.004);
        }
        owed = Math.min(owed, 2F);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    static void reset() {
        owed = 0F;
    }
}
