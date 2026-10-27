package dev.awaken.living.client;

import dev.awaken.living.region.RegionMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The air of a rank (SPEC §21.11, decision 25): a few specks drifting through the land you stand in, so a place feels like its number before
 * anything in it moves. D dust, C ash, B embers (snow-white flakes in the cold), A violet motes, S almost nothing, as the birds are. Client only,
 * a handful of particles a second, off with the {@code dread} setting (it is atmosphere), never in a gate or another dimension (the server
 * sends no land there).
 */
final class AmbientFx {
    private AmbientFx() {}

    private static final RandomSource RND = RandomSource.create();

    /** Specks every four ticks by rank E to S. */
    static final float[] RATE = {0F, 1F, 1.6F, 2.2F, 2.4F, 0.4F};

    static void tick(Minecraft mc, Player p) {
        if (!LivingClientConfig.dread || mc.level == null || mc.isPaused() || (p.tickCount & 3) != 0) return;
        int land = DreadFx.land();
        if (land < 1) return;
        int rank = RegionMap.rankOf(land);
        float rate = RATE[rank];
        if (rate <= 0F) return;
        boolean cold = mc.level.getBiome(p.blockPosition()).value().getBaseTemperature() <= 0.2F;
        int n = (int) rate + (RND.nextFloat() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double a = RND.nextDouble() * Math.PI * 2, r = 3 + RND.nextDouble() * 12;
            double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r, y = p.getY() - 1 + RND.nextDouble() * 7;
            ParticleOptions o = speck(rank, cold);
            // all of them drift one way, as wind would
            double vx = 0.02 + RND.nextDouble() * 0.02, vz = 0.01 + RND.nextDouble() * 0.02, vy = rank == 3 ? 0.025 : rank == 4 ? 0.012 : -0.01;
            mc.level.addParticle(o, x, y, z, vx, vy, vz);
        }
    }

    private static ParticleOptions speck(int rank, boolean cold) {
        return switch (rank) {
            case 1 -> cold ? ParticleTypes.SNOWFLAKE : ParticleTypes.WHITE_ASH;
            case 2 -> cold ? ParticleTypes.SNOWFLAKE : ParticleTypes.ASH;
            case 3 -> cold ? ParticleTypes.SNOWFLAKE : ParticleTypes.SMALL_FLAME;
            case 4 -> new DustParticleOptions(0xB07CFF, 0.8F);
            default -> new DustParticleOptions(0x8C7BFF, 0.6F);
        };
    }
}
