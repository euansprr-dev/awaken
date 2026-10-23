package dev.awaken.powers.content;

import dev.awaken.core.Forces;
import dev.awaken.powers.Combat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** Legion's shadow soldiers: melee summons that follow their owner and fight what the owner fights. */
public final class ShadowSoldier extends PowerSummon {
    public ShadowSoldier(EntityType<? extends ShadowSoldier> type, Level level) {
        super(type, level);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2, 8f, 3f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerTargetGoal(this, true));
        targetSelector.addGoal(2, new OwnerTargetGoal(this, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                (t, lvl) -> ownerPlayer() != null && Combat.isFoe(ownerPlayer(), t)));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity le)) return false;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        boolean hit = strike(le, power(), Forces.Force.SPIRIT);
        if (hit) level.sendParticles(ParticleTypes.SCULK_SOUL, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl && tickCount % 5 == 0) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 1, 0.25, 0.1, 0.25, 0.0);
        }
    }

    /** Targets whoever hurt the owner (defend) or whatever the owner last hit. */
    static final class OwnerTargetGoal extends TargetGoal {
        private final ShadowSoldier soldier;
        private final boolean defend;
        private @Nullable LivingEntity pick;
        private int stamp;

        OwnerTargetGoal(ShadowSoldier soldier, boolean defend) {
            super(soldier, false);
            this.soldier = soldier;
            this.defend = defend;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = soldier.getOwner();
            if (o == null) return false;
            pick = defend ? o.getLastHurtByMob() : o.getLastHurtMob();
            int s = defend ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            ServerPlayer sp = soldier.ownerPlayer();
            return s != stamp && pick != null && sp != null && Combat.isFoeOrTarget(sp, pick) && canAttack(pick, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(pick);
            LivingEntity o = soldier.getOwner();
            if (o != null) stamp = defend ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            super.start();
        }
    }

    static final class FollowOwnerGoal extends Goal {
        private final PowerSummon s;
        private final double speed;
        private final float start;
        private final float stop;

        FollowOwnerGoal(PowerSummon s, double speed, float start, float stop) {
            this.s = s;
            this.speed = speed;
            this.start = start;
            this.stop = stop;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = s.getOwner();
            return o != null && !o.isSpectator() && s.getTarget() == null && s.distanceToSqr(o) > start * start;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = s.getOwner();
            return o != null && s.getTarget() == null && !s.getNavigation().isDone() && s.distanceToSqr(o) > stop * stop;
        }

        @Override
        public void tick() {
            LivingEntity o = s.getOwner();
            if (o == null) return;
            s.getLookControl().setLookAt(o, 10f, s.getMaxHeadXRot());
            if (s.distanceToSqr(o) > 576) s.snapTo(o.getX(), o.getY(), o.getZ(), s.getYRot(), s.getXRot());
            else s.getNavigation().moveTo(o, speed);
        }

        @Override
        public void stop() {
            s.getNavigation().stop();
        }
    }
}
