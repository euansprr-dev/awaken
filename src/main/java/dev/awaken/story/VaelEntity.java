package dev.awaken.story;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Vael, the Rival. Appears for authored beats tied to the player's rank only, then leaves. Can't be hurt. A beat is one
 * player's scene: only that player sees this Vael (SPEC "Together").
 */
public class VaelEntity extends PathfinderMob {
    int lifeTicks = 20 * 40;
    /** The player whose scene this is (null: everyone sees it, as before). */
    java.util.UUID viewer;

    public VaelEntity(EntityType<? extends VaelEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 16f, 1f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer player) {
        return (viewer == null || viewer.equals(player.getUUID())) && super.broadcastToPlayer(player);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("awaken_viewer", net.minecraft.core.UUIDUtil.CODEC, viewer);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        viewer = in.read("awaken_viewer", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (--lifeTicks <= 0) discard();
        // Vael speaks to one player: look at them, not at whoever stands nearest
        var p = viewer == null ? null : level.getServer().getPlayerList().getPlayer(viewer);
        if (p != null && p.level() == level && p.distanceToSqr(this) < 24 * 24) getLookControl().setLookAt(p);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }
}
