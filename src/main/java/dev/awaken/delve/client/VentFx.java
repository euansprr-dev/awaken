package dev.awaken.delve.client;

import dev.awaken.body.ascent.UpdraftVentBlockEntity;
import dev.awaken.delve.DelveContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Hot air over a vent on magma (SPEC §28.5; the lava tunnels' and sulfur caves' vents): over the Ascent's own column of
 * pale streaks, embers ride the updraft off the grate (white-hot and gold most, orange and ember red among them,
 * flickering), rising eight or ten blocks and going out, and now and then a spark spat off the crust: the hot vent's own
 * look. The vents are found from the loaded chunks' block entities every second (a vent with magma under its grate is a
 * hot one); nothing per tick but the embers.
 */
final class VentFx {
    private VentFx() {}

    /** Hot vents within this of you are looked for, and breathe embers within {@link #NEAR}. */
    static final int FIND = 48, NEAR = 36;
    private static final int MAX = 8;
    private static final long[] HOT = new long[MAX];
    private static int hot, ticks;
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();
    /** Ember colours from the grate up: white-hot, gold, orange, ember red. */
    private static final int[] TINTS = {0xFFF2D0, 0xFFC456, 0xFF8A2E, 0xE0471C};

    static void reset() {
        hot = 0;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;
        if (++ticks % 20 == 0) find(level, p.getBlockX(), p.getBlockY(), p.getBlockZ());
        if (hot == 0 || mc.isPaused()) return;
        RandomSource r = level.getRandom();
        for (int i = 0; i < hot; i++) {
            long v = HOT[i];
            double x = BlockPos.getX(v) + 0.5, y = BlockPos.getY(v) + 1.05, z = BlockPos.getZ(v) + 0.5;
            if (p.distanceToSqr(x, y, z) > NEAR * NEAR) continue;
            // two or three embers a tick off the grate, fast at first, carried up the column and spreading
            int n = 2 + (r.nextFloat() < 0.4F ? 1 : 0);
            for (int k = 0; k < n; k++) {
                double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 1.6;
                double up = 0.16 + r.nextDouble() * 0.14;
                int tint = TINTS[Math.min(TINTS.length - 1, (int) (r.nextFloat() * r.nextFloat() * TINTS.length + (k == 0 ? 0 : 1)))];
                DelveParticles.spawn(level, DelveContent.MOTE.get(), tint, 0.7F + r.nextFloat() * 0.6F,
                        x + Math.cos(a) * rr, y + r.nextDouble() * 0.4, z + Math.sin(a) * rr,
                        Math.cos(a) * 0.012, up, Math.sin(a) * 0.012);
            }
            // now and then a spark spat sideways off the crust, falling back onto it
            if (r.nextFloat() < 0.08F) {
                double a = r.nextDouble() * Math.PI * 2;
                DelveParticles.spawn(level, DelveContent.SPARK.get(), 0xFF9A3C, 1.0F, x, y + 0.1, z,
                        Math.cos(a) * 0.12, 0.22 + r.nextDouble() * 0.12, Math.sin(a) * 0.12);
            }
        }
    }

    /** The hot vents near you: a vent's block entity with magma under its grate. */
    private static void find(ClientLevel level, int px, int py, int pz) {
        hot = 0;
        int c0x = (px - FIND) >> 4, c1x = (px + FIND) >> 4, c0z = (pz - FIND) >> 4, c1z = (pz + FIND) >> 4;
        for (int cx = c0x; cx <= c1x && hot < MAX; cx++) {
            for (int cz = c0z; cz <= c1z && hot < MAX; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof UpdraftVentBlockEntity) || hot >= MAX) continue;
                    BlockPos v = be.getBlockPos();
                    if (Math.abs(v.getY() - py) > FIND) continue;
                    if (level.getBlockState(M.set(v.getX(), v.getY() - 1, v.getZ())).is(Blocks.MAGMA_BLOCK)) HOT[hot++] = v.asLong();
                }
            }
        }
    }
}
