package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Someone a village's trouble is about, out of their village (SPEC §28.7): bound in a beast's lair waiting to be cut
 * free, on your back with their arms round your neck, walking behind you (and up a rope you hang for them), waiting
 * where you set them down, or lost on the land. They wear their own village skin (their settlement's look for their
 * record) and are never hurt. Back in the square they hand back to their village's people (the record comes home) and
 * this body goes. Saved with its chunk; its trouble finds it again by id.
 */
public class Rescuee extends PathfinderMob implements Npc {
    /** What they're doing (synced: the client poses them by it). */
    public static final int BOUND = 0, CARRIED = 1, FOLLOW = 2, WAIT = 3, HOME = 4, EMBRACE = 5, LOST = 6;

    static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(Rescuee.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(Rescuee.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Boolean> DATA_SLIM = SynchedEntityData.defineId(Rescuee.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<String> DATA_SAY = SynchedEntityData.defineId(Rescuee.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Float> DATA_CUT = SynchedEntityData.defineId(Rescuee.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> DATA_CHILD = SynchedEntityData.defineId(Rescuee.class, EntityDataSerializers.BOOLEAN);

    /** The carrier is slowed a little and can't sprint (Body's moves stop for anyone carrying). */
    static final Identifier CARRY_SLOW = Awaken.id("village_carry");

    int trouble = -1;
    String village = "";
    int rec = -1;
    @Nullable UUID follow;
    /** Where a HOME walk ends (the parent's place in the square). */
    @Nullable Vec3 home;
    int stuck, said;
    /** Client: when the current line started (tickCount). */
    public int sayStart;

    public Rescuee(EntityType<? extends Rescuee> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_MODE, BOUND);
        b.define(DATA_SKIN, "");
        b.define(DATA_SLIM, false);
        b.define(DATA_SAY, "");
        b.define(DATA_CUT, 0f);
        b.define(DATA_CHILD, false);
        b.define(DATA_CARRIER, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Not a trouble's: a new family's member walking up the lane to their new house (growth); gone indoors on arrival. */
    public static final int ARRIVAL = -2;

    /** A new family's member (growth, SPEC §28.7): walks from {@code from} to the door and goes in. */
    public static @Nullable Rescuee arrival(ServerLevel level, Vec3 from, Vec3 door, String name, Identifier skin, boolean slim, boolean child) {
        Rescuee r = TroubleContent.RESCUEE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (r == null) return null;
        r.become(ARRIVAL, "", -1, name, skin, slim, child);
        r.snapTo(from.x, from.y, from.z, 0, 0);
        r.home = door;
        r.mode(HOME);
        level.addFreshEntity(r);
        return r;
    }

    // ---- who they are ---------------------------------------------------------------------------------------------------

    /** Who this is: their trouble, village and record, their name, their look, a child or not. */
    void become(int trouble, String village, int rec, String name, Identifier skin, boolean slim, boolean child) {
        this.trouble = trouble;
        this.village = village;
        this.rec = rec;
        entityData.set(DATA_SKIN, skin.toString());
        entityData.set(DATA_SLIM, slim);
        entityData.set(DATA_CHILD, child);
        setCustomName(Component.literal(name));
        setCustomNameVisible(false);
        refreshDimensions();
    }

    public int mode() {
        return entityData.get(DATA_MODE);
    }

    void mode(int m) {
        entityData.set(DATA_MODE, m);
        if (m != FOLLOW) getNavigation().stop();
    }

    public String skin() {
        return entityData.get(DATA_SKIN);
    }

    public boolean slim() {
        return entityData.get(DATA_SLIM);
    }

    public boolean child() {
        return entityData.get(DATA_CHILD);
    }

    public float cut() {
        return entityData.get(DATA_CUT);
    }

    void cut(float f) {
        entityData.set(DATA_CUT, f);
    }

    public String line() {
        return entityData.get(DATA_SAY);
    }

    /** Says a line over their head (shown to everyone near; it fades on its own). */
    void say(String line) {
        if (line.isEmpty()) return;
        entityData.set(DATA_SAY, line);
        said = tickCount;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SAY.equals(accessor)) sayStart = tickCount;
        if (DATA_CHILD.equals(accessor)) refreshDimensions();
    }

    @Override
    public float getAgeScale() {
        return child() ? 0.68f : 1f;
    }

    // ---- the carry --------------------------------------------------------------------------------------------------------
    //
    // A player can't be ridden in 26.3 (its entity type is never saved, so the server refuses it as a vehicle), so on a
    // back they're held in place each tick behind the carrier's shoulders, and each client draws them from the carrier's
    // own position that frame (no lag behind your own moves).

    /** The carrier's entity id (synced), or -1. */
    static final EntityDataAccessor<Integer> DATA_CARRIER = SynchedEntityData.defineId(Rescuee.class, EntityDataSerializers.INT);

    public int carrier() {
        return entityData.get(DATA_CARRIER);
    }

    public boolean carried() {
        return mode() == CARRIED && carrier() >= 0;
    }

    /** Onto a player's back (from bound, waiting or following). */
    boolean climbOn(ServerPlayer p) {
        if (carried() || p.isSpectator() || carrying(p)) return false;
        follow = p.getUUID();
        entityData.set(DATA_CARRIER, p.getId());
        mode(CARRIED);
        noPhysics = true;
        setNoGravity(true);
        slow(p, true);
        hold(p);
        return true;
    }

    /** Set down behind the carrier: they walk after them, or wait there. */
    void setDown(boolean followCarrier) {
        if (level() instanceof ServerLevel sl && follow != null && sl.getPlayerByUUID(follow) instanceof ServerPlayer p && carried()) {
            slow(p, false);
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            teleportTo(p.getX() + Mth.sin(yaw) * 0.9, p.getY(), p.getZ() - Mth.cos(yaw) * 0.9);
        }
        entityData.set(DATA_CARRIER, -1);
        noPhysics = false;
        setNoGravity(false);
        mode(followCarrier ? FOLLOW : WAIT);
        stuck = 0;
    }

    static void slow(ServerPlayer p, boolean on) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on && !speed.hasModifier(CARRY_SLOW)) speed.addTransientModifier(new AttributeModifier(CARRY_SLOW, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!on) speed.removeModifier(CARRY_SLOW);
        if (on) p.setSprinting(false);
    }

    /** Whether a player carries someone (both sides: the speed modifier is synced to its owner). */
    public static boolean carrying(Player p) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(CARRY_SLOW);
    }

    /** Where someone on a carrier's back is: behind their shoulders, holding on. */
    public static Vec3 onBack(double x, double y, double z, float bodyYaw, boolean child) {
        float yaw = bodyYaw * Mth.DEG_TO_RAD;
        double back = child ? 0.30 : 0.34, up = child ? 0.62 : 0.30;
        // the forward of a body facing yaw is (-sin, cos): its back the other way
        return new Vec3(x + Mth.sin(yaw) * back, y + up, z - Mth.cos(yaw) * back);
    }

    private void hold(Player p) {
        Vec3 at = onBack(p.getX(), p.getY(), p.getZ(), p.yBodyRot, child());
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        setYRot(p.yBodyRot);
        yBodyRot = p.yBodyRot;
        yHeadRot = p.yBodyRot + Mth.sin(tickCount * 0.03f) * 18f;
        fallDistance = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && mode() == CARRIED && level() instanceof ServerLevel sl) {
            Player p = follow == null ? null : sl.getPlayerByUUID(follow);
            if (p != null && p.isAlive() && !p.isSpectator() && p.level() == level()) hold(p);
        }
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !carried() && super.canBeCollidedWith(other);
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------------

    @Override
    protected boolean isImmobile() {
        int m = mode();
        return super.isImmobile() || m == BOUND || m == EMBRACE || m == LOST || m == WAIT || m == CARRIED;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        int m = mode();
        // their trouble ended while this body slept in an unloaded chunk: they've long since gone home
        if (trouble == ARRIVAL) {
            // a new family's member: at their door, they go in (their village holds them from its next waking)
            if (m == EMBRACE || tickCount > 20 * 90) discard();
            else if (m == HOME && home != null && tickCount % 10 == 0) getNavigation().moveTo(home.x, home.y, home.z, 1.0);
            if (m == HOME && home != null && distanceToSqr(home) < 1.6 * 1.6) mode(EMBRACE);
            return;
        }
        if (tickCount % 200 == 17 && TroubleState.get(level.getServer()).byId(trouble) == null) {
            if (carried()) setDown(false);
            discard();
            return;
        }
        if (m == CARRIED) {
            Player held = follow == null ? null : level.getPlayerByUUID(follow);
            if (!(held instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()) {
                setDown(false);
                return;
            }
            p.setSprinting(false);
            // sneaking still for a second sets them down to walk
            if (p.isShiftKeyDown() && p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() < 0.002) {
                if (++stuck >= 20) {
                    setDown(true);
                    say(TroubleText.line(level, "village.trouble.rescue.walk", rec));
                }
            } else stuck = 0;
            return;
        }
        if (m == FOLLOW) follow(level);
        if (m == HOME && home != null) {
            if (distanceToSqr(home) > 1.6 * 1.6) {
                if (tickCount % 10 == 0) getNavigation().moveTo(home.x, home.y, home.z, 1.0);
            } else {
                getNavigation().stop();
                mode(EMBRACE);
            }
        }
        // a line now and then while someone is near to hear it
        if (tickCount - said > 240 && (m == BOUND || m == LOST || m == WAIT) && level.getNearestPlayer(this, 20) != null) {
            String key = m == BOUND ? "village.trouble.rescue.bound" : m == LOST ? "village.trouble.missing.found_call" : "village.trouble.rescue.wait";
            say(TroubleText.line(level, key, rec + tickCount / 240));
        }
        if (m == BOUND || m == LOST || m == WAIT) {
            Player near = level.getNearestPlayer(this, 12);
            if (near != null) getLookControl().setLookAt(near, 30f, 30f);
        }
    }

    /** Walks after the one they follow; climbs a rope (anything climbable) when they've gone up it. */
    private void follow(ServerLevel level) {
        Player p = follow == null ? null : level.getPlayerByUUID(follow);
        if (p == null || p.isSpectator() || distanceToSqr(p) > 64 * 64 || p.level() != level()) {
            mode(WAIT);
            return;
        }
        getLookControl().setLookAt(p, 30f, 30f);
        double dy = p.getY() - getY();
        double flat = Math.hypot(p.getX() - getX(), p.getZ() - getZ());
        if (onRope(level) && dy > 0.3) {
            // up the rope (or the ladder) after them; at its top, a step off toward them
            boolean top = !rope(level.getBlockState(blockPosition().above()));
            double ux = flat > 0.01 ? (p.getX() - getX()) / flat : 0, uz = flat > 0.01 ? (p.getZ() - getZ()) / flat : 0;
            setDeltaMovement(top ? ux * 0.18 : 0, top ? 0.32 : 0.2, top ? uz * 0.18 : 0);
            fallDistance = 0;
            stuck = 0;
            return;
        }
        if (flat < 2.6 && Math.abs(dy) < 2.5) {
            getNavigation().stop();
            stuck = 0;
            return;
        }
        if (dy > 2.5 && flat < 10) {
            // they went up: is there a line or a ladder near to climb?
            BlockPos rope = climbable(level, 6);
            if (rope != null) {
                if (Math.hypot(rope.getX() + 0.5 - getX(), rope.getZ() + 0.5 - getZ()) > 0.8) {
                    if (tickCount % 10 == 0) getNavigation().moveTo(rope.getX() + 0.5, rope.getY(), rope.getZ() + 0.5, 1.0);
                } else {
                    setPos(rope.getX() + 0.5, getY(), rope.getZ() + 0.5);
                    setDeltaMovement(0, 0.2, 0);
                }
                return;
            }
            if (++stuck == 200) say(TroubleText.line(level, "village.trouble.rescue.cant_climb", rec));
            return;
        }
        if (tickCount % 10 == 0) {
            boolean ok = getNavigation().moveTo(p, flat > 8 ? 1.25 : 1.0);
            stuck = ok ? 0 : stuck + 10;
            if (stuck == 200) say(TroubleText.line(level, "village.trouble.rescue.cant_climb", rec));
        }
    }

    static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> RUNNER_CLIMBS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, Awaken.id("runner_climbs"));

    /** A Runner's rope (the pipe grip's lines) or anything vanilla climbs. */
    static boolean rope(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(BlockTags.CLIMBABLE) || s.is(RUNNER_CLIMBS);
    }

    private boolean onRope(ServerLevel level) {
        return rope(level.getBlockState(blockPosition())) || rope(level.getBlockState(blockPosition().above()));
    }

    /** The nearest climbable block (a rope, a ladder, vines) within r, at or a little above their feet. */
    private @Nullable BlockPos climbable(ServerLevel level, int r) {
        BlockPos at = blockPosition();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= 2; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (!rope(level.getBlockState(m))) continue;
                    double d = dx * dx + dz * dz + dy;
                    if (d < bd) {
                        bd = d;
                        best = m.immutable();
                    }
                }
        return best;
    }

    // ---- players --------------------------------------------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp && level() instanceof ServerLevel level) TroubleDirector.touched(level, sp, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        int m = mode();
        return m == FOLLOW || m == HOME;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("awaken_trouble", trouble);
        out.putString("awaken_village", village);
        out.putInt("awaken_rec", rec);
        out.putInt("awaken_mode", mode() == CARRIED ? WAIT : mode());
        out.putString("awaken_skin", skin());
        out.putBoolean("awaken_slim", slim());
        out.putBoolean("awaken_child", child());
        if (follow != null) out.putString("awaken_follow", follow.toString());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        trouble = in.getIntOr("awaken_trouble", -1);
        village = in.getStringOr("awaken_village", "");
        rec = in.getIntOr("awaken_rec", -1);
        entityData.set(DATA_MODE, in.getIntOr("awaken_mode", WAIT));
        entityData.set(DATA_SKIN, in.getStringOr("awaken_skin", ""));
        entityData.set(DATA_SLIM, in.getBooleanOr("awaken_slim", false));
        entityData.set(DATA_CHILD, in.getBooleanOr("awaken_child", false));
        Optional<String> f = in.getString("awaken_follow");
        follow = null;
        if (f.isPresent()) {
            try {
                follow = UUID.fromString(f.get());
            } catch (IllegalArgumentException ignored) {
                // a bad id: they wait for whoever comes
            }
        }
    }
}
