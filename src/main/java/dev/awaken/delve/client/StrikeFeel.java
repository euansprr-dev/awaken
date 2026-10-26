package dev.awaken.delve.client;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.HeavyStrikes;
import dev.awaken.delve.Ore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The strike's feel (PLAN 7.4): every bite of a pick or mattock into rock throws sparks (white-hot, cooling to the ore's
 * colour), soft ground throws grit; every ore sounds like itself as it's struck (iron rings, copper clanks, gold is soft,
 * diamond cracks like glass, Deepglass hums); the last blow on an ore lets go with its own crack, a short hold of the
 * hands and view (Arms' hit-stop, {@code core.api.CombatFeedback}) and a small kick. Bites follow vanilla's mining
 * rhythm (one every four ticks held). Client only, from the blocks the client already has.
 */
final class StrikeFeel {
    private StrikeFeel() {}

    static final int BITE_EVERY = 4;
    private static final long NONE = Long.MIN_VALUE;
    private static long target = NONE;
    private static float progress;
    private static int held;
    // a tour's demonstration: blows on one block, then the last
    private static long demoPos = NONE;
    private static int demoTick = -1, demoFace;

    static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!e.getLevel().isClientSide()) return;
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || mc.level == null) return;
        BlockPos pos = e.getPos();
        BlockState s = mc.level.getBlockState(pos);
        if (s.isAir()) return;
        Direction face = e.getFace() == null ? Direction.UP : e.getFace();
        switch (e.getAction()) {
            case START -> {
                target = pos.asLong();
                progress = 0;
                held = 0;
                bite(mc.level, mc.player, pos, face, s, false);
            }
            case CLIENT_HOLD -> {
                if (pos.asLong() != target) {
                    target = pos.asLong();
                    progress = 0;
                    held = 0;
                }
                progress += s.getDestroyProgress(mc.player, mc.level, pos);
                held++;
                if (progress >= 1F) {
                    bite(mc.level, mc.player, pos, face, s, true);
                    target = NONE;
                } else if (held % BITE_EVERY == 0) {
                    bite(mc.level, mc.player, pos, face, s, false);
                }
            }
            default -> {
            }
        }
    }

    /** One bite into the block at {@code pos} on its {@code face}; {@code last}: the blow that breaks it. */
    static void bite(ClientLevel level, LocalPlayer p, BlockPos pos, Direction face, BlockState s, boolean last) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource r = level.getRandom();
        Vec3 at = mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos)
                ? bh.getLocation() : Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        Ore ore = Ore.of(s);
        boolean tool = HeavyStrikes.isMiningTool(p.getMainHandItem());
        if (ore != null) {
            float pitch = 0.94F + r.nextFloat() * 0.12F;
            level.playLocalSound(at.x, at.y, at.z, DelveContent.STRIKE.get(ore).get(), SoundSource.BLOCKS, last ? 0.85F : 0.55F, pitch, false);
            if (last) level.playLocalSound(at.x, at.y, at.z, DelveContent.STRIKE_LAST.get(), SoundSource.BLOCKS, 0.7F, 0.8F + ore.ordinal() * 0.05F, false);
        }
        int density = density(mc);
        if (density > 0) {
            boolean hard = s.is(BlockTags.MINEABLE_WITH_PICKAXE) && s.getDestroySpeed(level, pos) >= 1F;
            boolean soft = s.is(BlockTags.MINEABLE_WITH_SHOVEL);
            if (hard && tool) {
                int n = (last ? 16 : 6) * density / 2;
                sparks(level, at, face, n, ore == null ? 0xFFC98A : ore.tint, last ? 1.4F : 1.15F, r);
            } else if (soft || hard) {
                int n = (last ? 9 : 4) * density / 2;
                grit(level, at, face, n, s.getMapColor(level, pos).col, r);
            }
        }
        if (last && ore != null) {
            CombatFeedback.hold(ore == Ore.DIAMOND || ore == Ore.DEEPGLASS ? 70 : 50);
            CombatFeedback.kick((r.nextFloat() - 0.5F) * 0.8F, 0.9F, (r.nextFloat() - 0.5F) * 0.6F, 5);
        }
    }

    /** Vanilla's particle setting: 2 all, 1 decreased, 0 minimal (none of ours). */
    static int density(Minecraft mc) {
        ParticleStatus st = mc.options.particles().get();
        return st == ParticleStatus.ALL ? 2 : st == ParticleStatus.DECREASED ? 1 : 0;
    }

    /** Sparks off the face: out along its normal, spread wide, falling in arcs. */
    static void sparks(ClientLevel level, Vec3 at, Direction face, int n, int tint, float size, RandomSource r) {
        for (int i = 0; i < n; i++) {
            double sp = 0.10 + r.nextDouble() * 0.16;
            double vx = face.getStepX() * sp + (r.nextDouble() - 0.5) * 0.22;
            double vy = face.getStepY() * sp + (r.nextDouble() - 0.2) * 0.2;
            double vz = face.getStepZ() * sp + (r.nextDouble() - 0.5) * 0.22;
            DelveParticles.spawn(level, DelveContent.SPARK.get(), tint, size, at.x + face.getStepX() * 0.03, at.y + face.getStepY() * 0.03,
                    at.z + face.getStepZ() * 0.03, vx, vy, vz);
        }
    }

    static void grit(ClientLevel level, Vec3 at, Direction face, int n, int tint, RandomSource r) {
        for (int i = 0; i < n; i++) {
            double vx = face.getStepX() * 0.06 + (r.nextDouble() - 0.5) * 0.1;
            double vy = face.getStepY() * 0.06 + r.nextDouble() * 0.06;
            double vz = face.getStepZ() * 0.06 + (r.nextDouble() - 0.5) * 0.1;
            DelveParticles.spawn(level, DelveContent.GRIT.get(), tint, 1F, at.x + (r.nextDouble() - 0.5) * 0.3, at.y + (r.nextDouble() - 0.5) * 0.3,
                    at.z + (r.nextDouble() - 0.5) * 0.3, vx, vy, vz);
        }
    }

    // ---- the tour's demonstration (/awaken delve demo strike) -----------------------------------------------------------

    static void demo(DelveNet.Demo m) {
        if (m.kind() == DelveNet.Demo.CHARGE) {
            HeavyCharge.demo();
            return;
        }
        demoPos = m.pos();
        demoFace = m.face();
        demoTick = 0;
    }

    static void tick() {
        if (demoTick < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            demoTick = -1;
            return;
        }
        BlockPos pos = BlockPos.of(demoPos);
        BlockState s = mc.level.getBlockState(pos);
        if (demoTick % BITE_EVERY == 0 && demoTick <= 20 && !s.isAir()) {
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
            bite(mc.level, mc.player, pos, Direction.from3DDataValue(demoFace), s, demoTick == 20);
        }
        if (++demoTick > 20) demoTick = -1;
    }
}
