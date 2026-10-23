package dev.awaken.mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

/**
 * A named beast of the deep (the Lantern Maw): everything a {@link NamedBeast} is (territory, warning, phases, its
 * lair, its drops) on a swimmer's body. It hunts through the water without a path, strikes a target at the surface
 * from just below it, and never leaves the water (beached, it flops back).
 */
public class DeepBeast extends NamedBeast implements WildPose {
    public DeepBeast(EntityType<? extends DeepBeast> type, Level level) {
        super(type, level);
        this.moveControl = Swim.moveControl(this, false);
        this.lookControl = Swim.lookControl(this);
        Swim.malus(this, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return Swim.navigation(this, level, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MobGoals.Hold(this));
        goalSelector.addGoal(3, new Fauna3Goals.SwimHunt(this));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.9));
        goalSelector.addGoal(7, new RandomSwimmingGoal(this, 0.8, 60));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.WATER) ? 10.0F : 0.0F;
    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        Swim.travelInWater(this, input);
    }

    @Override
    public void baseTick() {
        int air = getAirSupply();
        super.baseTick();
        if (level() instanceof ServerLevel level) Swim.air(this, level, air);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        Swim.flop(this);
        if (level().isClientSide() && isAlive() && tickCount % 5 == 0 && isInWater()) lureMote();
    }

    /**
     * Now and then a mote of light drifts off the lure's bulb (client only, no blocks touched: no real light), so the
     * lure reads from far off in dark water. The bulb sits about 27 model pixels ahead and 34 up at rest.
     */
    private void lureMote() {
        float sc = getScale(), yaw = yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
        double fwd = 27.0 / 16.0 * sc, up = 34.0 / 16.0 * sc;
        var r = getRandom();
        level().addParticle(net.minecraft.core.particles.ParticleTypes.GLOW,
                getX() - net.minecraft.util.Mth.sin(yaw) * fwd + (r.nextDouble() - 0.5) * 0.5, getY() + up + (r.nextDouble() - 0.5) * 0.5,
                getZ() + net.minecraft.util.Mth.cos(yaw) * fwd + (r.nextDouble() - 0.5) * 0.5,
                (r.nextDouble() - 0.5) * 0.02, 0.012, (r.nextDouble() - 0.5) * 0.02);
    }

    @Override
    public int wildState() {
        return isInWater() ? S_SWIM : S_BEACHED;
    }
}
