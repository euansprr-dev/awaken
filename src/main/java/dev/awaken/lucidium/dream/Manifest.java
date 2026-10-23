package dev.awaken.lucidium.dream;

import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shaping the dream by intent (lucidity 50+). Hold H on a wall and a door opens in it; on a floor and a stair climbs
 * away from you; into the open air and a bridge reaches out. Built from the places you remembered today. At 80+,
 * sneaking, you call a creature from memory to fight beside you.
 */
final class Manifest {
    private Manifest() {}

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static boolean shape(ServerPlayer sp, ActiveDream ad) {
        ServerLevel level = ad.level;
        Vec3 eye = sp.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(sp.getLookAngle().scale(7)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sp));
        List<BlockState> mats = ad.bp.manifestPalette;
        BlockState main = mats.getFirst();
        BlockState accent = mats.getLast();
        List<BlockPos> placed = new ArrayList<>();
        boolean ok;
        if (hit.getType() == HitResult.Type.MISS) ok = bridge(level, sp, main, accent, placed);
        else if (hit.getDirection().getAxis().isHorizontal()) ok = door(level, hit.getBlockPos(), hit.getDirection(), accent, placed);
        else ok = stair(level, sp, hit.getBlockPos(), main, placed);
        if (!ok) {
            sp.sendOverlayMessage(Component.literal("The dream doesn't give here.").withStyle(net.minecraft.ChatFormatting.GRAY));
            return false;
        }
        for (int i = 0; i < placed.size(); i += 2) {
            BlockPos p = placed.get(i);
            level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
        }
        return true;
    }

    /** A doorway through a blank wall (at least 6 of the 9 blocks around the spot must be solid). */
    private static boolean door(ServerLevel level, BlockPos wall, Direction face, BlockState frame, List<BlockPos> placed) {
        Direction side = face.getClockWise();
        int solid = 0;
        for (int du = -1; du <= 1; du++)
            for (int dv = -1; dv <= 1; dv++) if (level.getBlockState(wall.relative(side, du).above(dv)).isSolidRender()) solid++;
        if (solid < 6) return false;
        BlockPos base = wall;
        if (level.getBlockState(base.relative(face).below()).isAir() && level.getBlockState(base.below()).isSolidRender()) base = base.below();
        Direction into = face.getOpposite();
        BlockState lower = Blocks.CHERRY_DOOR.defaultBlockState().setValue(DoorBlock.FACING, into).setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        for (int depth = 0; depth < 4; depth++) {
            BlockPos lo = base.relative(into, depth), hi = lo.above();
            boolean solidHere = !level.getBlockState(lo).isAir() || !level.getBlockState(hi).isAir();
            if (!solidHere && depth > 0) break;
            if (depth == 0) {
                level.setBlock(lo, lower, FLAGS);
                level.setBlock(hi, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
                for (int dv = 0; dv <= 2; dv++) {
                    set(level, lo.relative(side).above(dv), frame, placed, true);
                    set(level, lo.relative(side.getOpposite()).above(dv), frame, placed, true);
                }
                set(level, lo.above(2), frame, placed, true);
            } else {
                level.setBlock(lo, Blocks.AIR.defaultBlockState(), FLAGS);
                level.setBlock(hi, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            placed.add(lo);
            placed.add(hi);
        }
        return true;
    }

    /** Six steps climbing away from you, starting on the floor you look at. */
    private static boolean stair(ServerLevel level, ServerPlayer sp, BlockPos floor, BlockState main, List<BlockPos> placed) {
        Direction dir = sp.getDirection();
        Direction side = dir.getClockWise();
        BlockPos start = floor.above();
        for (int k = 0; k < 7; k++) {
            BlockPos step = start.relative(dir, k).above(k);
            set(level, step, main, placed, false);
            set(level, step.relative(side), main, placed, false);
        }
        return !placed.isEmpty();
    }

    /** A bridge reaching out over the void from your feet, rising if you look up. */
    private static boolean bridge(ServerLevel level, ServerPlayer sp, BlockState main, BlockState light, List<BlockPos> placed) {
        Vec3 look = sp.getLookAngle();
        double hx = look.x, hz = look.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 0.2) return false;
        hx /= len;
        hz /= len;
        double rise = sp.getXRot() < -20 ? 0.35 : (sp.getXRot() > 25 ? -0.35 : 0);
        double px = -hz, pz = hx;
        int baseY = Mth.floor(sp.getY()) - 1;
        for (int k = 1; k <= 14; k++) {
            double cx = sp.getX() + hx * k, cz = sp.getZ() + hz * k;
            int y = baseY + (int) Math.floor(rise * k);
            for (int w = -1; w <= 1; w++) {
                BlockPos p = BlockPos.containing(cx + px * w, y, cz + pz * w);
                set(level, p, (k % 4 == 0 && w != 0) ? light : main, placed, false);
            }
        }
        return !placed.isEmpty();
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s, List<BlockPos> placed, boolean replace) {
        BlockState there = level.getBlockState(p);
        if (!replace && !there.canBeReplaced()) return;
        if (replace && there.isAir()) return;
        level.setBlock(p, s, FLAGS);
        placed.add(p);
    }

    /** A creature from memory: the wolf you tamed, or a guardian made of the places you remember. */
    static boolean summon(ServerPlayer sp, ActiveDream ad) {
        ServerLevel level = ad.level;
        boolean wolf = Progression.profile(sp).counter("awaken:wolves_tamed") > 0;
        Vec3 at = sp.position().add(sp.getLookAngle().multiply(2, 0, 2));
        Mob mob;
        if (wolf) {
            Wolf w = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (w == null) return false;
            w.tame(sp);
            w.setCustomName(Component.literal("Memory of a friend"));
            mob = w;
        } else {
            IronGolem g = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (g == null) return false;
            g.setPlayerCreated(true);
            g.setCustomName(Component.literal("Memory of a guardian"));
            mob = g;
        }
        mob.snapTo(at.x, sp.getY(), at.z, sp.getYRot() + 180, 0);
        mob.setPersistenceRequired();
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
        level.addFreshEntity(mob);
        ad.summons.add(mob.getUUID());
        level.sendParticles(ParticleTypes.END_ROD, at.x, sp.getY() + 1, at.z, 40, 0.5, 1, 0.5, 0.04);
        Signal.toast(sp, Component.literal("A memory answers."));
        return true;
    }
}
