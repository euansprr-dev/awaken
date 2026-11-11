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

    /** A Gauntlet stand near enough to tick says so (SPEC §71.3): the Lesson lights it and pins its prompt if it is still to take. */
    public static void stand(BlockPos pos, int leap) {
        WayClient.standSeen(pos, leap);
    }

    /** A sky platform's core breathing a ring of light. */
    public static void coreRing(BlockPos pos) {
        AscentFx.ring(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, new Vec3(0, 1, 0), 0.45F);
    }

    /**
     * The air round the wings' case (SPEC §17.9, "The wings on show"), each client tick of a core within 48 blocks:
     * the core's ring of light every three seconds; light shed off the feathers, rising; the wind across the deck
     * carrying motes of it out over the rim; the pair stirring (the wings' own fold, low) and a gust now and then; and
     * as the gift's scene begins, the case opening (the vault's shutter, Minecraft's own) under the wings' opening.
     */
    public static void caseTick(net.minecraft.world.level.Level level, BlockPos pos) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level != level) return;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5, d2 = mc.player.distanceToSqr(cx, cy, cz);
        if (d2 > 48 * 48) return;
        long t = level.getGameTime() + pos.asLong();
        var r = level.getRandom();
        int gift = AscentScene.wingsGift();
        boolean pair = gift >= -1 && gift < 34 || gift < -1 && !AscentMoves.has(dev.awaken.body.ascent.AscentPayloads.Tier.WINGS);
        if (t % 60 == 0) coreRing(pos);
        if (pair && t % 4 == 0) {
            double a = r.nextDouble() * Math.PI * 2, d = 0.3 + r.nextDouble() * 1.4;
            MoteParticle.next(r.nextFloat() < 0.35F ? 0xEAF7FF : 0x9FD4FF, 0.6F + r.nextFloat() * 0.5F, 50 + r.nextInt(30), -0.012F, false);
            level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), cx + Math.cos(a) * d, cy + 2.2 + r.nextDouble() * 1.8, cz + Math.sin(a) * d,
                    0, 0.008 + r.nextDouble() * 0.01, 0);
        }
        if (t % 3 == 0) {
            // the wind: from the stair's side across the deck and off the pier's
            double side = (r.nextDouble() - 0.5) * 26, back = -13 + r.nextDouble() * 6, inv = 0.7071;
            MoteParticle.next(r.nextFloat() < 0.2F ? 0xEAF7FF : 0xBFE3FF, 0.5F + r.nextFloat() * 0.4F, 70 + r.nextInt(40), 0F, false);
            level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), cx + (back - side) * inv, cy + 0.4 + r.nextDouble() * 5, cz + (back + side) * inv,
                    0.09 + r.nextDouble() * 0.05, (r.nextDouble() - 0.4) * 0.012, 0.09 + r.nextDouble() * 0.05);
        }
        if (pair && d2 < 14 * 14 && t % 92 == 0) {
            level.playLocalSound(cx, cy + 3, cz, dev.awaken.body.ascent.AscentContent.WINGS_FOLD.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.2F,
                    0.7F + r.nextFloat() * 0.08F, false);
        }
        if (t % 190 == 0) {
            level.playLocalSound(cx + 8, cy + 2, cz + 8, dev.awaken.body.ascent.AscentContent.WIND.get(), net.minecraft.sounds.SoundSource.AMBIENT, 0.28F,
                    0.8F + r.nextFloat() * 0.15F, false);
        }
        if (gift == 1 && d2 < 16 * 16) {
            level.playLocalSound(cx, cy + 2, cz, net.minecraft.sounds.SoundEvents.VAULT_OPEN_SHUTTER, net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 0.8F, false);
            level.playLocalSound(cx, cy + 3, cz, dev.awaken.body.ascent.AscentContent.WINGS_OPEN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.92F, false);
        }
    }
}
