package dev.awaken.above.machine;

import dev.awaken.above.AboveSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Teleports for pylons, the Clone Vat, the Launch Ring and the Halo's return pad. */
public final class Travel {
    private Travel() {}

    /** "1,240 m NE". -Z is north. */
    public static String bearing(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        return String.format("%,d m %s", dist, compass(dx, dz));
    }

    public static String compass(int dx, int dz) {
        if (dx == 0 && dz == 0) return "here";
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.round(ang / 45.0) % 8];
    }

    private static boolean free(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && !level.getFluidState(feet).isSource();
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** A standing spot near {@code target}: on top, then around it, then the surface of the column. */
    public static BlockPos safeSpot(ServerLevel level, BlockPos target) {
        level.getChunk(target.getX() >> 4, target.getZ() >> 4);
        BlockPos above = target.above();
        if (free(level, above) && solid(level, target)) return above;
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos feet = target.offset(dx, dy, dz);
                        if (free(level, feet) && solid(level, feet.below())) return feet;
                    }
                }
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
        return new BlockPos(target.getX(), Math.max(y, level.getMinY() + 1), target.getZ());
    }

    public static void teleport(ServerPlayer player, ServerLevel level, BlockPos feet) {
        ServerLevel from = player.level();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        from.playSound(null, player.blockPosition(), AboveSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(feet), Vec3.ZERO, player.getYRot(), player.getXRot(),
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.END_ROD, feet.getX() + 0.5, feet.getY() + 1, feet.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, feet, AboveSounds.TELEPORT.get(), SoundSource.PLAYERS, 0.8f, 1.5f);
    }
}
