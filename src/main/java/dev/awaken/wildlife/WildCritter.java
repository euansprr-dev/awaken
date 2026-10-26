package dev.awaken.wildlife;

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
 * One of the wild's small animals (SPEC "Wild life"): a songbird, a woodpecker, a crow, an owl, a buzzard or kestrel, a
 * gull, a duck, a heron, a squirrel, a rabbit, a fox or a frog. Made and moved only on the client
 * ({@code wildlife.client.Wildlife}), never on the server, never saved, never pushed, picked or hunted: the server pays
 * nothing for the wild's ambient life. Plain data plus the hook the client's brain fills (as Carillon's CityCritter).
 */
public final class WildCritter extends Entity {
    /** The client's brain: one call a tick. A critter on the server discards itself. */
    public static Consumer<WildCritter> brain = Entity::discard;

    public int kind, variant, seed;
    /** The brain's state machine: state, a timer, a target, the tick it began. */
    public int state, timer, next;
    public long since;
    public double tx, ty, tz, vx, vy, vz;
    /** Where it lives: its chunk (ChunkPos key) and its place in that chunk's spots; -1 when it has none. */
    public long home;
    public int spot = -1;
    /** A trunk or a perch it holds to: the side it faces (yaw, degrees) and the trunk's centre. */
    public float face;
    public double ax, az, hi, lo;
    /** The clip playing (the model's index), when it began (age in ticks), and the one it crossfades from. */
    public int clip = -1, prevClip = -1;
    public float clipStart, prevStart, prevEnd;
    /** Ground or air speed this tick (blocks/tick), and the body's bank (roll) and pitch in flight, now and last tick. */
    public float speed, roll, rollO, pitch, pitchO;
    /** Where it looks (world), NaN: its own idle glances. */
    public double lookX = Double.NaN, lookY, lookZ;
    /** Leaving for good (flown off, run to cover): the director drops it once it's out of sight. */
    public boolean leaving;
    /** How far it sits in the water (blocks; a duck on a pond), and its next call's tick. */
    public float sink;
    public long nextCall;
    /** The life layer's state (client), held here so it survives between frames. */
    public Object clientLife;

    public WildCritter(EntityType<? extends WildCritter> type, Level level) {
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
