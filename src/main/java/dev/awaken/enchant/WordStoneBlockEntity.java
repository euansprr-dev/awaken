package dev.awaken.enchant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A Word Stone's client side (SPEC §45.14); nothing is saved, the carving is the block state. While you're near and
 * haven't learned it, its letters lift off the carved face in the enchantment's colour and gather above the stone with
 * a faint hum; the moment you learn it they all stream into you. Once you know it the stone goes quiet.
 */
public class WordStoneBlockEntity extends BlockEntity {
    private static final double NEAR = 10;
    private @Nullable ColorParticleOption letter;
    private int time;
    private boolean knew = true;

    public WordStoneBlockEntity(BlockPos pos, BlockState state) {
        super(EnchantContent.WORD_STONE_BE.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, WordStoneBlockEntity be) {
        be.time++;
        Inscriptions.Entry e = WordStones.entry(state);
        if (e == null) return;
        Player p = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, NEAR, false);
        if (p == null || !p.isLocalPlayer()) {
            be.knew = true;
            return;
        }
        boolean knows = Known.of(p).knows(e.id());
        if (be.letter == null) be.letter = ColorParticleOption.create(EnchantContent.LETTER.get(), 0xFF000000 | e.colour());
        RandomSource r = level.getRandom();
        Direction f = state.getValue(WordStoneBlock.FACING);
        double side = f.getAxis() == Direction.Axis.X ? 0 : 1, depth = 1 - side;
        if (!be.knew && knows && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 36) {
            // learned: every letter on the face streams into you
            double tx = p.getX(), ty = p.getY() + 1.1, tz = p.getZ();
            for (int i = 0; i < 28; i++) {
                double[] s = face(pos, f, side, depth, r);
                level.addParticle(be.letter, tx, ty, tz, s[0] - tx, s[1] - ty, s[2] - tz);
            }
        } else if (!knows && be.time % 3 == 0) {
            double[] s = face(pos, f, side, depth, r);
            double gx = pos.getX() + 0.5 + f.getStepX() * 0.2 + (r.nextDouble() - 0.5) * 0.5, gy = pos.getY() + 1.45 + r.nextDouble() * 0.3;
            double gz = pos.getZ() + 0.5 + f.getStepZ() * 0.2 + (r.nextDouble() - 0.5) * 0.5;
            level.addParticle(be.letter, gx, gy, gz, s[0] - gx, s[1] - gy, s[2] - gz);
            if (be.time % 84 == 0)
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, EnchantContent.DRIFT.get(), SoundSource.BLOCKS, 0.14F, 0.75F, false);
        }
        be.knew = knows;
    }

    /** A point on the carved face (a thumb's width out from it), somewhere down the script. */
    private static double[] face(BlockPos pos, Direction f, double side, double depth, RandomSource r) {
        double lateral = (r.nextDouble() - 0.5) * 0.5;
        return new double[]{pos.getX() + 0.5 + f.getStepX() * 0.36 + lateral * side, pos.getY() + 0.3 + r.nextDouble() * 0.62,
                pos.getZ() + 0.5 + f.getStepZ() * 0.36 + lateral * depth};
    }
}
