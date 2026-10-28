package dev.awaken.delve.client;

import dev.awaken.core.api.MapMarks;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Lamps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * A Delver lamp lighting (SPEC §34.6), seen: the crystal flares, and the wave goes out through the hall at the server's
 * pace (16 blocks a second), each lattice light blooming in a burst of warm motes as the front reaches it while the
 * game's own light floods in behind. The lit lamps are pinned on the Atlas by their hall's name.
 */
final class LampFx {
    private LampFx() {}

    private static final int WARM = 0xFFE2B0;
    private static final RandomSource R = RandomSource.create();

    private record Wave(BlockPos lamp, long[] lights, float[] dist, long start, boolean[] done) {}

    private static final List<Wave> WAVES = new ArrayList<>();
    private static List<MapMarks.Mark> known = List.of();

    static void register() {
        MapMarks.register(p -> known);
    }

    static void lit(DelveNet.LampLit m) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos lamp = BlockPos.of(m.lamp());
        float[] d = new float[m.reached().length];
        for (int i = 0; i < d.length; i++) d[i] = (float) Math.sqrt(BlockPos.of(m.reached()[i]).distSqr(lamp));
        WAVES.add(new Wave(lamp, m.reached(), d, level.getGameTime(), new boolean[d.length]));
        burst(level, lamp.getX() + 0.5, lamp.getY() + 0.5, lamp.getZ() + 0.5, 30, 0.12);
    }

    static void known(DelveNet.LampsKnown m) {
        List<MapMarks.Mark> out = new ArrayList<>();
        for (int i = 0; i < m.at().length && i < m.names().size(); i++) out.add(new MapMarks.Mark(BlockPos.of(m.at()[i]), WARM, m.names().get(i), true));
        known = List.copyOf(out);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || WAVES.isEmpty()) return;
        for (var it = WAVES.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            float front = (level.getGameTime() - w.start() + 1) * Lamps.WAVE;
            boolean all = true;
            for (int i = 0; i < w.lights().length; i++) {
                if (w.done()[i]) continue;
                if (w.dist()[i] > front) {
                    all = false;
                    continue;
                }
                w.done()[i] = true;
                BlockPos b = BlockPos.of(w.lights()[i]);
                burst(level, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 8, 0.05);
            }
            if (all) it.remove();
        }
    }

    private static void burst(ClientLevel level, double x, double y, double z, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double dx = R.nextGaussian(), dy = R.nextGaussian() * 0.6, dz = R.nextGaussian();
            DelveParticles.spawn(level, DelveContent.LIGHT_MOTE.get(), WARM, 1.6F, x, y, z, dx * speed, dy * speed, dz * speed);
        }
    }

    static void reset() {
        WAVES.clear();
        known = List.of();
    }
}
