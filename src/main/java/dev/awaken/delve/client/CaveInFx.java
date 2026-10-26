package dev.awaken.delve.client;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A cave-in's warning (PLAN 7.4): for the three seconds before the roof comes down, threads of dust trickle from the
 * stones that will fall, grit patters off them, thicker as the moment nears, and the ground trembles under you if
 * you're close. The fall itself is the server's falling blocks.
 */
final class CaveInFx {
    private CaveInFx() {}

    private record Warn(long[] roof, long until, int ticks, BlockPos centre) {}

    private static final List<Warn> WARNS = new ArrayList<>();

    static void warn(DelveNet.CaveWarn m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WARNS.add(new Warn(m.roof(), mc.level.getGameTime() + m.ticks(), m.ticks(), BlockPos.of(m.centre())));
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || WARNS.isEmpty()) return;
        long now = level.getGameTime();
        WARNS.removeIf(w -> now > w.until);
        RandomSource r = level.getRandom();
        int density = StrikeFeel.density(mc);
        for (Warn w : WARNS) {
            float t = 1F - (w.until - now) / (float) w.ticks;   // 0 at the warning, 1 as it falls
            if (density > 0) {
                int threads = (int) (2 + t * 6) * density / 2;
                for (int i = 0; i < threads; i++) {
                    BlockPos b = BlockPos.of(w.roof[r.nextInt(w.roof.length)]);
                    BlockState s = level.getBlockState(b);
                    if (s.isAir()) continue;
                    int col = s.getMapColor(level, b).col;
                    double x = b.getX() + 0.15 + r.nextDouble() * 0.7, z = b.getZ() + 0.15 + r.nextDouble() * 0.7;
                    DelveParticles.spawn(level, DelveContent.DUST.get(), col, 1F, x, b.getY() - 0.02, z, 0, -0.03 - r.nextDouble() * 0.02, 0);
                    if (r.nextFloat() < 0.25F + t * 0.4F) {
                        DelveParticles.spawn(level, DelveContent.GRIT.get(), col, 0.9F, x, b.getY() - 0.05, z, (r.nextDouble() - 0.5) * 0.02, -0.05, (r.nextDouble() - 0.5) * 0.02);
                    }
                }
            }
            if (mc.player != null && mc.player.blockPosition().distSqr(w.centre) < 18 * 18 && now % 8 == 0) {
                float k = 0.25F + t * 0.6F;
                CombatFeedback.kick((r.nextFloat() - 0.5F) * k, (r.nextFloat() - 0.5F) * k, (r.nextFloat() - 0.5F) * k * 1.5F, 6);
            }
        }
    }

    static void reset() {
        WARNS.clear();
    }
}
