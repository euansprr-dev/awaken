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
     * A sealed rank door breathing its rank's colour (SPEC §17.29): a few motes lift off the core's face, so a door says
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

    private static boolean shuttered;

    /**
     * A sky platform's air, from its core's client tick (SPEC §17.29): wind going round the island's rim, leaves where its
     * trees stand, the Wings' case humming close to, all quieter at night; and the case's shutter as the gift's scene
     * begins beside it. Nothing plays from more than 48 out.
     */
    public static void skyAir(BlockPos core) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var p = mc.player;
        var level = mc.level;
        if (p == null || level == null) return;
        double cx = core.getX() + 0.5, cy = core.getY(), cz = core.getZ() + 0.5;
        double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz, h2 = dx * dx + dz * dz;
        var amb = net.minecraft.sounds.SoundSource.AMBIENT;
        if (AscentScene.age >= 1 && AscentScene.age <= 3 && AscentScene.playingMode == dev.awaken.body.ascent.AscentPayloads.GIFT_WINGS) {
            if (!shuttered && h2 < 100 && Math.abs(dy) < 6) {
                level.playLocalSound(cx, cy + 3.2, cz, dev.awaken.body.ascent.AscentContent.CASE_OPEN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1F, 1F, false);
            }
            shuttered = true;
        } else {
            shuttered = false;
        }
        if (h2 > 48 * 48 || dy < -40 || dy > 24) return;
        var r = level.getRandom();
        float hush = level.isDarkOutside() ? 0.6F : 1F;
        if (r.nextInt(90) == 0) {
            double a = r.nextDouble() * Math.PI * 2, rad = 14 + r.nextDouble() * 4;
            level.playLocalSound(cx + Math.cos(a) * rad, cy + (r.nextDouble() - 0.3) * 4, cz + Math.sin(a) * rad,
                    dev.awaken.body.ascent.AscentContent.SKY_WIND.get(), amb, (0.55F + r.nextFloat() * 0.25F) * hush, 0.85F + r.nextFloat() * 0.2F, false);
        }
        if (r.nextInt(120) == 0) {
            // where a tree stands at an eighth (a platform from before the island has none)
            double a = Math.toRadians(22.5 + 45 * r.nextInt(8)), lx = cx + Math.cos(a) * 12.2, lz = cz + Math.sin(a) * 12.2;
            if (level.getBlockState(BlockPos.containing(lx, cy + 4, lz)).is(net.minecraft.tags.BlockTags.LEAVES)) level.playLocalSound(lx, cy + 5, lz,
                    dev.awaken.body.ascent.AscentContent.SKY_LEAVES.get(), amb, 0.45F * hush, 0.9F + r.nextFloat() * 0.2F, false);
        }
        if (h2 < 100 && Math.abs(dy) < 6 && r.nextInt(100) == 0) {
            level.playLocalSound(cx, cy + 1.5, cz, dev.awaken.body.ascent.AscentContent.CASE_HUM.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5F, 0.8F + r.nextFloat() * 0.1F, false);
        }
    }

    /** A sky platform's core breathing a ring of light. */
    public static void coreRing(BlockPos pos) {
        AscentFx.ring(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, new Vec3(0, 1, 0), 0.45F);
    }
}
