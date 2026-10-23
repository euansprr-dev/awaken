package dev.awaken.mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What every swimmer of the Tidewater shares (the water creatures and the Lantern Maw): vanilla's smooth swimming
 * controls (the dolphin's), water-bound or amphibious navigation, a swim that moves along the body's pitch, and the
 * fish's rule out of water (it flops and, if it can't walk on land, suffocates after 15 seconds).
 */
final class Swim {
    private Swim() {}

    /** Ticks a fish lasts out of water before it starts to drown (vanilla's 300 air). */
    static final int AIR = 300;

    /** The swimmer's controls (its constructor assigns them: the fields are the Mob's own). */
    static SmoothSwimmingMoveControl<Mob> moveControl(Mob mob, boolean amphibious) {
        return new SmoothSwimmingMoveControl<>(mob, 85, 10, 0.02F, amphibious ? 0.3F : 0.05F, true);
    }

    static SmoothSwimmingLookControl lookControl(Mob mob) {
        return new SmoothSwimmingLookControl(mob, 10);
    }

    static void malus(Mob mob, boolean amphibious) {
        mob.setPathfindingMalus(PathType.WATER, 0.0F);
        if (amphibious) mob.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    static PathNavigation navigation(Mob mob, Level level, boolean amphibious) {
        return amphibious ? new AmphibiousPathNavigation(mob, level) : new WaterBoundPathNavigation(mob, level);
    }

    /** The dolphin's and the nautilus' swim: accelerate by the move speed along the input, water drag 0.9. */
    static void travelInWater(Mob mob, Vec3 input) {
        mob.moveRelative(mob.getSpeed(), input);
        mob.move(MoverType.SELF, mob.getDeltaMovement());
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.9));
    }

    /** A fish on dry ground flops toward nowhere in particular (vanilla's AbstractFish). */
    static void flop(Mob mob) {
        if (!mob.isInWater() && mob.onGround() && mob.verticalCollision) {
            mob.setDeltaMovement(mob.getDeltaMovement().add((mob.getRandom().nextFloat() * 2 - 1) * 0.05, 0.4, (mob.getRandom().nextFloat() * 2 - 1) * 0.05));
            mob.setOnGround(false);
            mob.needsSync = true;
            mob.playSound(net.minecraft.sounds.SoundEvents.COD_FLOP, 0.4F, 0.9F + mob.getRandom().nextFloat() * 0.3F);
        }
    }

    /** A water creature out of water loses air and then drowns (2 damage a second), like vanilla's WaterAnimal. */
    static void air(Mob mob, ServerLevel level, int before) {
        if (mob.isAlive() && !mob.isInWater()) {
            mob.setAirSupply(before - 1);
            if (mob.getAirSupply() <= -20) {
                mob.setAirSupply(0);
                mob.hurtServer(level, mob.damageSources().drown(), 2.0F);
            }
        } else {
            mob.setAirSupply(AIR);
        }
    }

    /** A random spot in water within {@code r} blocks (and {@code dy} up or down), or null after a few tries. */
    static @Nullable Vec3 waterSpot(Mob mob, int r, int dy, double minDepth) {
        Level level = mob.level();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 10; i++) {
            int x = mob.getBlockX() + mob.getRandom().nextInt(r * 2 + 1) - r;
            int y = mob.getBlockY() + mob.getRandom().nextInt(dy * 2 + 1) - dy;
            int z = mob.getBlockZ() + mob.getRandom().nextInt(r * 2 + 1) - r;
            m.set(x, y, z);
            if (!level.hasChunkAt(m) || !level.getFluidState(m).is(FluidTags.WATER)) continue;
            if (minDepth > 0 && !level.getFluidState(m.above()).is(FluidTags.WATER)) continue;
            return Vec3.atCenterOf(m);
        }
        return null;
    }

    /** Height of the water surface above this point (0 when the block above is not water), at most {@code max}. */
    static int depthBelowSurface(Level level, BlockPos pos, int max) {
        BlockPos.MutableBlockPos m = pos.mutable();
        int n = 0;
        while (n < max && level.getFluidState(m.move(0, 1, 0)).is(FluidTags.WATER)) n++;
        return n;
    }

    /** The swimmer's body pitch for its swim (degrees, positive nose down), from its motion. */
    static float pitchOf(Vec3 v) {
        double h = v.horizontalDistance();
        if (h < 1.0E-4 && Math.abs(v.y) < 1.0E-4) return 0.0F;
        return Mth.clamp((float) (-Mth.atan2(v.y, h) * Mth.RAD_TO_DEG), -60.0F, 60.0F);
    }
}
