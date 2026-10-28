package dev.awaken.delve.client;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.HeavyStrikes;
import dev.awaken.delve.Ore;
import dev.awaken.delve.StrikeBlocks;
import dev.awaken.delve.TrueStrikes;
import dev.awaken.delve.TuningForkItem;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The strike's feel (PLAN 7.4, SPEC §34.3): every bite of a pick or mattock into hard rock throws spark streaks
 * (white-gold, cooling to the ore's colour; the chips come with the hit, {@link StrikeFx}); a bite on an ore's seam is a
 * true strike (its own note, rising when they come in a row, and the block gives faster); every ore sounds like itself as it's struck (iron rings, copper clanks, gold is soft,
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
                lastHold = clock;
                target = pos.asLong();
                progress = 0;
                held = 0;
                bite(mc.level, mc.player, pos, face, s, false);
            }
            case CLIENT_HOLD -> {
                lastHold = clock;
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
        lastBite = clock;
        RandomSource r = level.getRandom();
        boolean aimed = mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos);
        Vec3 at = aimed ? mc.hitResult.getLocation() : Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        Ore ore = Ore.of(s);
        boolean tool = HeavyStrikes.isMiningTool(p.getMainHandItem());
        boolean landed = aimed && ore != null && tool && DelveClientConfig.trueStrikes() && trueStrike(level, p, pos, face, at, ore);
        if (!landed) trueRun = 0;
        if (ore != null) {
            float pitch = 0.94F + r.nextFloat() * 0.12F;
            level.playLocalSound(at.x, at.y, at.z, DelveContent.STRIKE.get(ore).get(), SoundSource.BLOCKS, last ? 0.85F : 0.55F, pitch, false);
            if (last) level.playLocalSound(at.x, at.y, at.z, DelveContent.STRIKE_LAST.get(), SoundSource.BLOCKS, 0.7F, 0.8F + ore.ordinal() * 0.05F, false);
        }
        int density = density(mc);
        boolean hard = s.is(BlockTags.MINEABLE_WITH_PICKAXE) && s.getDestroySpeed(level, pos) >= 1F;
        if (density > 0 && hard && tool) {
            // sparks only off metal and hard rock under a pick; chips come with the hit itself (StrikeFx)
            int n = StrikeBlocks.streaks(last, landed, density, r);
            StrikeFx.sparks(level, at, face, n, ore == null ? 0xFFC98A : ore.tint, landed, r);
        } else if (density > 0 && !DelveClientConfig.strikeParticles() && (hard || s.is(BlockTags.MINEABLE_WITH_SHOVEL))) {
            grit(level, at, face, (last ? 9 : 4) * density / 2, s.getMapColor(level, pos).col, r);
        }
        // a small nudge of the view on contact (the camera-kick comfort setting scales it)
        if (!last && hard && tool) CombatFeedback.kick(0F, landed ? 0.45F : 0.22F, 0F, 3);
        if (last && ore != null) {
            CombatFeedback.hold(ore == Ore.DIAMOND || ore == Ore.DEEPGLASS ? 70 : 50);
            CombatFeedback.kick((r.nextFloat() - 0.5F) * 0.8F, 0.9F, (r.nextFloat() - 0.5F) * 0.6F, 5);
        }
    }

    /** True strikes in a row: each rises a step in pitch, up to three. */
    private static int trueRun;

    /**
     * Whether this bite lands on the seam (SPEC §34.3): if so the client counts it (its mining speeds up at once), tells
     * the server (which checks with its own ray and corrects the count if it disagrees) and hears the ore's note.
     */
    private static boolean trueStrike(ClientLevel level, LocalPlayer p, BlockPos pos, Direction face, Vec3 at, Ore ore) {
        int n = TrueStrikes.trues(p, pos);
        if (!TrueStrikes.near(pos, face, n, at, TrueStrikes.radius(p.getMainHandItem()))) return false;
        TrueStrikes.predicted(p, pos);
        ClientPacketDistributor.sendToServer(new DelveNet.TrueStrike(pos.asLong(), face.get3DDataValue(), n));
        trueRun = Math.min(trueRun + 1, 4);
        float step = (float) Math.pow(2, (trueRun - 1) * 2 / 12.0);
        level.playLocalSound(at.x, at.y, at.z, DelveContent.TRUE_STRIKE.get(), SoundSource.BLOCKS, 0.5F, TuningForkItem.pitch(ore) * step, false);
        return true;
    }

    /** Vanilla's particle setting: 2 all, 1 decreased, 0 minimal (none of ours). */
    static int density(Minecraft mc) {
        ParticleStatus st = mc.options.particles().get();
        return st == ParticleStatus.ALL ? 2 : st == ParticleStatus.DECREASED ? 1 : 0;
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

    // the dig motion's clock (DigMotion): client ticks, the last bite and the last tick the attack was held on a block
    static int clock, lastBite = -100, lastHold = -100;

    static void tick() {
        clock++;
        if (demoTick < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            demoTick = -1;
            return;
        }
        BlockPos pos = BlockPos.of(demoPos);
        BlockState s = mc.level.getBlockState(pos);
        lastHold = clock;
        if (demoTick % BITE_EVERY == 0 && demoTick <= 20 && !s.isAir()) {
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
            bite(mc.level, mc.player, pos, Direction.from3DDataValue(demoFace), s, demoTick == 20);
        }
        if (++demoTick > 20) demoTick = -1;
    }
}
