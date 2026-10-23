package dev.awaken.descent.rules;

import dev.awaken.descent.DescentContent;
import dev.awaken.descent.Strata;
import dev.awaken.descent.block.ConduitBlock;
import dev.awaken.descent.world.Layout;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rule 6, machines wake: five conduits ring the Foundry's pit, which is sealed. Touching a conduit flips it and its two
 * neighbours (a ring "lights out"; every pattern is solvable on five). When all five burn, the seal lifts for good.
 * Each touch can wake a Foundry sentry.
 */
public final class Foundry {
    private Foundry() {}

    /** Pure puzzle step: flips conduit i and its ring neighbours. */
    public static boolean[] press(boolean[] lit, int i) {
        boolean[] out = lit.clone();
        int n = out.length;
        for (int k = -1; k <= 1; k++) {
            int j = Math.floorMod(i + k, n);
            out[j] = !out[j];
        }
        return out;
    }

    public static boolean solved(boolean[] lit) {
        for (boolean b : lit) if (!b) return false;
        return true;
    }

    private static int indexOf(int depth, BlockPos pos) {
        int form = Strata.formOf(depth);
        for (int i = 0; i < Layout.CONDUITS; i++) {
            if (Layout.world(depth, Layout.conduit(form, i)).equals(pos)) return i;
        }
        return -1;
    }

    public static boolean sealed(ServerLevel level, int depth) {
        BlockPos pit = Layout.world(depth, Layout.pit(Strata.formOf(depth)));
        return level.getBlockState(pit.above()).is(DescentContent.FOUNDRY_SEAL.get());
    }

    public static void touch(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int depth = Strata.depthAt(level.dimension(), pos);
        int i = depth > 0 ? indexOf(depth, pos) : -1;
        if (i < 0) {
            player.sendOverlayMessage(Component.literal("This conduit is cold. It belongs to a Foundry."));
            return;
        }
        if (!sealed(level, depth)) {
            player.sendOverlayMessage(Component.literal("The Foundry is awake. The way down is open."));
            return;
        }
        int form = Strata.formOf(depth);
        boolean[] lit = new boolean[Layout.CONDUITS];
        BlockPos[] at = new BlockPos[Layout.CONDUITS];
        for (int k = 0; k < Layout.CONDUITS; k++) {
            at[k] = Layout.world(depth, Layout.conduit(form, k));
            BlockState s = level.getBlockState(at[k]);
            lit[k] = s.is(DescentContent.CONDUIT.get()) && s.getValue(ConduitBlock.LIT);
        }
        boolean[] next = press(lit, i);
        for (int k = 0; k < Layout.CONDUITS; k++) {
            if (next[k] != lit[k]) {
                level.setBlock(at[k], DescentContent.CONDUIT.get().defaultBlockState().setValue(ConduitBlock.LIT, next[k]), Block.UPDATE_ALL);
                level.sendParticles(next[k] ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE,
                        at[k].getX() + 0.5, at[k].getY() + 1.0, at[k].getZ() + 0.5, 8, 0.2, 0.3, 0.2, 0.02);
            }
        }
        level.playSound(null, pos, next[i] ? DescentContent.SND_CONDUIT_ON.get() : DescentContent.SND_CONDUIT_OFF.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        Zones.machineWoke(level, player, depth, pos);
        if (solved(next)) open(level, depth);
    }

    public static void open(ServerLevel level, int depth) {
        int form = Strata.formOf(depth);
        BlockPos pit = Layout.world(depth, Layout.pit(form));
        for (int dx = -Layout.PIT_HALF; dx <= Layout.PIT_HALF; dx++) {
            for (int dz = -Layout.PIT_HALF; dz <= Layout.PIT_HALF; dz++) {
                BlockPos p = pit.offset(dx, 1, dz);
                if (level.getBlockState(p).is(DescentContent.FOUNDRY_SEAL.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        level.playSound(null, pit, DescentContent.SND_FOUNDRY_OPEN.get(), SoundSource.BLOCKS, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, pit.getX() + 0.5, pit.getY() + 1.5, pit.getZ() + 0.5, 60, 2.0, 1.0, 2.0, 0.05);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(pit) < 160 * 160) Signal.toast(p, Component.literal("The Foundry wakes. The way down is open."));
        }
    }
}
