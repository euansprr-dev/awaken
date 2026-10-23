package dev.awaken.gates.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Treasure Gate: a small thing wearing a chest, fleeing with the gate's treasure. Every hit shakes loot loose; kill it
 * for the hoard. Ninety seconds after it first sees you, it gets away.
 */
public class GateHoarder extends Zombie {
    public int gateId = -1;
    public int area, floor;
    public long spottedAt = -1;

    public GateHoarder(EntityType<? extends GateHoarder> type, Level level) {
        super(type, level);
        setBaby(true);
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHEST));
        setDropChance(EquipmentSlot.HEAD, 0);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 14F, 1.25, 1.55));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void addBehaviourGoals() {
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (spottedAt < 0 && tickCount % 10 == 0 && level.getNearestPlayer(this, 12) != null) {
            spottedAt = level.getGameTime();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_LOCKED, SoundSource.HOSTILE, 1F, 1.6F);
        }
        if (tickCount % 8 == 0) level.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 0.8, getZ(), 1, 0.2, 0.2, 0.2, 0);
        if (spottedAt >= 0 && level.getGameTime() - spottedAt > 20 * 90) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 20, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_CLOSE, SoundSource.HOSTILE, 1F, 1.8F);
            discard();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && spottedAt < 0) spottedAt = level.getGameTime();
        return hurt;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }
}
