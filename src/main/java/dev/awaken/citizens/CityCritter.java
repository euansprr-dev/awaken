package dev.awaken.citizens;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Consumer;

/**
 * One of Carillon's animals (M4 B3b): a pigeon, a gull, a cat, a dog, a carthorse with its cart. Made and moved only on
 * the client ({@code citizens.client.CityCritters}), never on the server, never saved, never pushed or picked: the
 * server pays nothing for the city's animals. This class is plain data plus the hook the client's brain fills.
 */
public final class CityCritter extends Entity {
    public static final int PIGEON = 0, GULL = 1, CAT = 2, DOG = 3, HORSE = 4;

    /** The client's brain (CityCritters.tick): one call a tick. A critter on the server discards itself. */
    public static Consumer<CityCritter> brain = Entity::discard;

    public int kind, variant, seed;
    /** The brain's state machine: state, when it began, a timer, a target, a home (index into the brain's table). */
    public int state, home = -1, flock = -1, owner = -1;
    public long since;
    public int timer;
    public double tx, ty, tz, vx, vy, vz;
    /** The clip playing (the model's index), when it began (age in ticks), and the one it crossfades from. */
    public int clip = -1, prevClip = -1;
    public float clipStart, prevStart, prevEnd;
    /** Ground speed this tick (blocks/tick) for the gait's pace; a cart's wheels' turn (radians). */
    public float speed, wheel, wheelO;
    /** A cart's axle relative to the horse (blocks) and its yaw, now and last tick. */
    public double cartDx, cartDz, cartDxO, cartDzO;
    public float cartYaw, cartYawO, cartY, cartYO;
    /** Where it looks (world), and how much it wants to (0..1); NaN: its own idle glances. */
    public double lookX = Double.NaN, lookY, lookZ;
    /** The life layer's state (client), held here so it survives between frames. */
    public Object clientLife;

    public CityCritter(EntityType<? extends CityCritter> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    public void tick() {
        brain.accept(this);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
