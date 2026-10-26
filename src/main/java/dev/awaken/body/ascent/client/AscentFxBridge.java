package dev.awaken.body.ascent.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Client effects other Ascent code asks for (block entities' client ticks). */
public final class AscentFxBridge {
    private AscentFxBridge() {}

    /** One mote of the Signal's thread on the ground (SPEC §27.7): it lifts a little and thins out. */
    public static void threadMote(double x, double y, double z, double dx, double dz) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        var r = mc.level.getRandom();
        MoteParticle.next(r.nextFloat() < 0.3F ? 0xEAF7FF : 0x9FD4FF, 0.9F + r.nextFloat() * 0.5F, 26 + r.nextInt(14), -0.01F, false);
        mc.level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), x, y, z, dx * 0.04, 0.012 + r.nextDouble() * 0.01, dz * 0.04);
    }

    /**
     * A sealed rank door breathing its rank's colour (SPEC §17.25): a few motes lift off the core's face, so a door says
     * which rank it waits for before anyone touches it.
     */
    public static void doorBreath(BlockPos pos, int colour) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        var r = mc.level.getRandom();
        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 2.6, y = pos.getY() - 0.6 + r.nextDouble() * 2.4, z = pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 2.6;
            MoteParticle.next(r.nextFloat() < 0.25F ? 0xEAF7FF : colour, 0.8F + r.nextFloat() * 0.5F, 40 + r.nextInt(20), -0.015F, false);
            mc.level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), x, y, z, 0, 0.01 + r.nextDouble() * 0.01, 0);
        }
    }

    /** A sky platform's core breathing a ring of light. */
    public static void coreRing(BlockPos pos) {
        AscentFx.ring(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, new Vec3(0, 1, 0), 0.45F);
    }
}
