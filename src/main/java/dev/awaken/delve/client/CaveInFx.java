package dev.awaken.delve.client;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A cave-in on the client (PLAN 7.4). The three seconds before: cracks spread over the stones that will fall (the ones
 * that go first crack first), dust pours from the seams between them in streams that thicken as the moment nears, grit
 * patters down, the roof creaks a second time and the ground trembles under you. Then each stone lets go when the server
 * drops it (nearest the heart first, over {@code spread} ticks): a burst of dust and a crack where it tore free, and when
 * it lands a cloud rolling out along the floor and the thud of it, while the view shakes with every stone that hits.
 */
final class CaveInFx {
    private CaveInFx() {}

    /** Seams pouring dust at most; the view shakes within SHAKE blocks of the heart. */
    static final int SEAMS = 14, SHAKE = 20;
    /** Breaker ids for the roof's cracks (negative: never an entity's). */
    private static final int CRACK_ID = -0x5A7E0000;

    private static final class Cave {
        final long[] roof;
        final long start, fall;
        final int ticks, spread, id;
        final BlockPos centre;
        final double[] seamX, seamY, seamZ;
        final int[] seamOf, stage;
        final boolean[] dropped;
        final BlockState[] was;   // the roof as the warning found it (the server may have dropped a stone before we look)
        final List<long[]> landings = new ArrayList<>();   // {x, y, z (packed pos), tick, state id}
        boolean creaked;
        long lastHit = Long.MIN_VALUE;

        Cave(ClientLevel level, DelveNet.CaveWarn m, long now, int id, RandomSource r) {
            roof = m.roof();
            was = new BlockState[roof.length];
            for (int i = 0; i < roof.length; i++) {
                BlockState s = level.getBlockState(BlockPos.of(roof[i]));
                was[i] = s.isAir() ? Blocks.STONE.defaultBlockState() : s;
            }
            ticks = Math.max(1, m.ticks());
            spread = m.spread();
            start = now;
            fall = now + ticks;
            centre = BlockPos.of(m.centre());
            this.id = id;
            stage = new int[roof.length];
            java.util.Arrays.fill(stage, -1);
            dropped = new boolean[roof.length];
            // the seams: points on the edges between roof stones (where a crack opens), spread over the roof
            int n = Math.min(SEAMS, roof.length);
            seamX = new double[n];
            seamY = new double[n];
            seamZ = new double[n];
            seamOf = new int[n];
            for (int i = 0; i < n; i++) {
                int k = (int) ((long) i * roof.length / n);
                BlockPos b = BlockPos.of(roof[k]);
                boolean alongX = r.nextBoolean();
                double e = r.nextBoolean() ? 0.02 : 0.98, along = 0.2 + r.nextDouble() * 0.6;
                seamX[i] = b.getX() + (alongX ? along : e);
                seamZ[i] = b.getZ() + (alongX ? e : along);
                seamY[i] = b.getY() - 0.03;
                seamOf[i] = k;
            }
        }

        long due(int i) {
            return fall + (long) i * spread / roof.length;
        }

        boolean done(long now) {
            return now > fall + spread + 4 && landings.isEmpty();
        }
    }

    private static final List<Cave> CAVES = new ArrayList<>();
    private static int next;

    static void warn(DelveNet.CaveWarn m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || m.roof().length == 0) return;
        CAVES.add(new Cave(mc.level, m, mc.level.getGameTime(), CRACK_ID - (next++ & 63) * 64, mc.level.getRandom()));
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || CAVES.isEmpty()) return;
        long now = level.getGameTime();
        RandomSource r = level.getRandom();
        int density = StrikeFeel.density(mc);
        for (int c = CAVES.size() - 1; c >= 0; c--) {
            Cave cave = CAVES.get(c);
            if (now < cave.fall) warning(level, cave, now, r, density);
            falling(level, cave, now, r, density);
            shake(mc, cave, now, r);
            if (cave.done(now)) {
                for (int i = 0; i < cave.roof.length; i++) if (cave.stage[i] >= 0) level.destroyBlockProgress(cave.id - i, BlockPos.of(cave.roof[i]), -1);
                CAVES.remove(c);
            }
        }
    }

    /** Before the fall: cracks spreading, dust pouring from the seams, grit, the second creak. */
    private static void warning(ClientLevel level, Cave cave, long now, RandomSource r, int density) {
        float t = (now - cave.start) / (float) cave.ticks;   // 0 at the warning, 1 as it falls
        // the cracks: the stones that go first crack first, all of them broken through by the fall
        for (int i = 0; i < cave.roof.length && i < 64; i++) {
            float lead = 0.35F * i / cave.roof.length;
            int stage = Mth.clamp((int) ((t * 1.25F - lead) * 10F), -1, 9);
            if (stage != cave.stage[i] && stage >= 0) {
                cave.stage[i] = stage;
                level.destroyBlockProgress(cave.id - i, BlockPos.of(cave.roof[i]), stage);
            }
        }
        if (!cave.creaked && t >= 0.55F) {
            cave.creaked = true;
            level.playLocalSound(cave.centre.getX() + 0.5, cave.centre.getY() + 3.5, cave.centre.getZ() + 0.5, DelveContent.CAVE_CREAK.get(),
                    SoundSource.BLOCKS, 1.2F, 1.15F, false);
        }
        if (density == 0) return;
        // the seams pour: more of them open, and each runs thicker, as the moment nears
        int open = Math.min(cave.seamX.length, 3 + (int) (t * cave.seamX.length));
        for (int s = 0; s < open; s++) {
            if (r.nextFloat() > (0.45F + 0.55F * t) * density / 2F) continue;
            BlockPos b = BlockPos.of(cave.roof[cave.seamOf[s]]);
            BlockState st = cave.was[cave.seamOf[s]];
            int col = dust(st.getMapColor(level, b).col);
            double x = cave.seamX[s] + (r.nextDouble() - 0.5) * 0.12, z = cave.seamZ[s] + (r.nextDouble() - 0.5) * 0.12;
            DelveParticles.spawn(level, DelveContent.DUST.get(), col, 2.2F + t * 1.8F + r.nextFloat(), x, cave.seamY[s], z,
                    (r.nextDouble() - 0.5) * 0.01, -0.06 - t * 0.06 - r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
            if (r.nextFloat() < 0.18F + t * 0.4F) {
                DelveParticles.spawn(level, DelveContent.GRIT.get(), st.getMapColor(level, b).col, 1F, x, cave.seamY[s] - 0.05, z,
                        (r.nextDouble() - 0.5) * 0.02, -0.08, (r.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    /** Stones letting go as the server drops them, and where they land. */
    private static void falling(ClientLevel level, Cave cave, long now, RandomSource r, int density) {
        for (int i = 0; i < cave.roof.length; i++) {
            if (cave.dropped[i] || now < cave.due(i)) continue;
            cave.dropped[i] = true;
            BlockPos b = BlockPos.of(cave.roof[i]);
            if (i < 64 && cave.stage[i] >= 0) level.destroyBlockProgress(cave.id - i, b, -1);
            BlockState st = cave.was[i];
            SoundType sound = st.getSoundType();
            level.playLocalSound(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, sound.getBreakSound(), SoundSource.BLOCKS, 1.1F, 0.55F + r.nextFloat() * 0.15F, false);
            if (density > 0) {
                int col = dust(st.getMapColor(level, b).col);
                for (int k = 0; k < 3 * density; k++) {
                    DelveParticles.spawn(level, DelveContent.DUST.get(), col, 3.5F + r.nextFloat() * 1.5F, b.getX() + r.nextDouble(), b.getY() - 0.05, b.getZ() + r.nextDouble(),
                            (r.nextDouble() - 0.5) * 0.06, -0.04 - r.nextDouble() * 0.04, (r.nextDouble() - 0.5) * 0.06);
                    DelveParticles.spawn(level, DelveContent.GRIT.get(), st.getMapColor(level, b).col, 1.2F, b.getX() + r.nextDouble(), b.getY() - 0.1, b.getZ() + r.nextDouble(),
                            (r.nextDouble() - 0.5) * 0.08, -0.1, (r.nextDouble() - 0.5) * 0.08);
                }
            }
            // where it will land: the first floor below, after a fall's time (the falling block's 0.04 a tick, a little drag)
            int floor = b.getY() - 1, lowest = b.getY() - 24;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(b.getX(), floor, b.getZ());
            while (floor > lowest && level.getBlockState(m.setY(floor)).isAir()) floor--;
            int h = Math.max(1, b.getY() - (floor + 1));
            long land = now + Math.round(Math.sqrt(2.0 * h / 0.04) * 1.08);
            cave.landings.add(new long[]{BlockPos.asLong(b.getX(), floor + 1, b.getZ()), land, Block.getId(st)});
        }
        for (int k = cave.landings.size() - 1; k >= 0; k--) {
            long[] l = cave.landings.get(k);
            if (now < l[1]) continue;
            cave.landings.remove(k);
            cave.lastHit = now;
            BlockPos at = BlockPos.of(l[0]);
            BlockState st = Block.stateById((int) l[2]);
            SoundType sound = st.getSoundType();
            level.playLocalSound(at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, sound.getPlaceSound(), SoundSource.BLOCKS, 1.4F, 0.5F + r.nextFloat() * 0.15F, false);
            level.playLocalSound(at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, sound.getBreakSound(), SoundSource.BLOCKS, 0.9F, 0.45F + r.nextFloat() * 0.1F, false);
            if (density == 0) continue;
            // a cloud rolling out along the floor from where it hit
            int col = dust(st.getMapColor(level, at).col);
            for (int q = 0; q < 5 * density; q++) {
                double a = r.nextDouble() * Math.PI * 2, v = 0.07 + r.nextDouble() * 0.09;
                DelveParticles.spawn(level, DelveContent.DUST.get(), col, 3.5F + r.nextFloat() * 2F, at.getX() + 0.5 + Math.cos(a) * 0.4, at.getY() + 0.15 + r.nextDouble() * 0.3,
                        at.getZ() + 0.5 + Math.sin(a) * 0.4, Math.cos(a) * v, 0.01 + r.nextDouble() * 0.02, Math.sin(a) * v);
            }
            for (int q = 0; q < 3 * density; q++) {
                DelveParticles.spawn(level, DelveContent.GRIT.get(), st.getMapColor(level, at).col, 1.3F, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5,
                        (r.nextDouble() - 0.5) * 0.25, 0.12 + r.nextDouble() * 0.12, (r.nextDouble() - 0.5) * 0.25);
            }
        }
    }

    /** The ground trembling: growing through the warning, hard through the fall, a jolt with each stone that lands near. */
    private static void shake(Minecraft mc, Cave cave, long now, RandomSource r) {
        if (mc.player == null) return;
        double d = Math.sqrt(mc.player.blockPosition().distSqr(cave.centre));
        if (d > SHAKE) return;
        float near = (float) (1 - d / SHAKE);
        float k;
        int every;
        if (now < cave.fall) {
            k = 0.25F + 0.6F * (now - cave.start) / (float) cave.ticks;
            every = 6;
        } else if (now <= cave.fall + cave.spread + 20) {
            k = now - cave.lastHit < 3 ? 2.6F : 1.5F;
            every = 2;
        } else {
            return;
        }
        if (now % every != 0) return;
        k *= 0.4F + 0.6F * near;
        CombatFeedback.kick((r.nextFloat() - 0.5F) * k * 1.4F, (r.nextFloat() - 0.5F) * k, (r.nextFloat() - 0.5F) * k * 1.6F, every + 2);
    }

    /** Dust reads paler than the rock it comes off. */
    private static int dust(int rock) {
        int r = rock >> 16 & 255, g = rock >> 8 & 255, b = rock & 255;
        return (r + (0xD6 - r) * 45 / 100) << 16 | (g + (0xCE - g) * 45 / 100) << 8 | (b + (0xC2 - b) * 45 / 100);
    }

    static void reset() {
        CAVES.clear();
    }
}
