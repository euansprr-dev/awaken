package dev.awaken.mobs.entity;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.net.MobsNet;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A beast's bite, told (SPEC §47.2): the Gnawed and the wild animals that fight back bit with vanilla melee, unseen. In
 * reach, a beast now plants itself, rears back with a growl and a white-gold glint for {@link #TELL} ticks (a light
 * blow: deflect, block or dodge it), lunges a step {@link #LUNGE} ticks before the bite and bites only if you are still
 * in its reach. At most {@link #AT_ONCE} beasts wind up on one body at a time; the rest wait their turn. Installed as
 * {@link Strikes#bite} for the kin and living modules; the tell reaches Arms through {@link #tellOf}.
 */
public final class ToldBite extends MeleeAttackGoal {
    /** The wind-up: half a second (the glint shows at 5, the deflect window is 4). */
    public static final int TELL = 10;
    /** The lunge leaves this long before the bite. */
    public static final int LUNGE = 3;
    /** Beasts winding up on one body at once. */
    public static final int AT_ONCE = 2;
    /** What the lunge adds to a beast's reach (blocks): a dash of 2.6 is well out of it. */
    public static final double LUNGE_REACH = 0.8;
    /** Players this near a beast see its tell. */
    static final double TELL_RANGE = 48;

    // the bites winding up now (a handful), for the pack's turns
    private static final List<ToldBite> WINDING = new ArrayList<>();

    private final BooleanSupplier when;
    private int left;
    private @Nullable LivingEntity on;

    public ToldBite(PathfinderMob mob, double speed, BooleanSupplier when) {
        super(mob, speed, true);
        this.when = when;
    }

    @Override
    public boolean canUse() {
        return when.getAsBoolean() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return when.getAsBoolean() && super.canContinueToUse();
    }

    @Override
    public void stop() {
        release();
        super.stop();
    }

    @Override
    public void tick() {
        if (left <= 0) {
            super.tick();
            return;
        }
        LivingEntity t = on;
        if (t == null || !t.isAlive() || t != mob.getTarget()) {
            release();
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(t, 30F, 30F);
        left--;
        if (left == LUNGE) lunge(t);
        if (left == 0) bite(t);
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        if (!canPerformAttack(target)) return;
        if (winding(target) >= AT_ONCE) {
            // the pack takes turns: wait a beat and look again
            resetAttackCooldown();
            return;
        }
        on = target;
        left = TELL;
        WINDING.add(this);
        mob.getNavigation().stop();
        // rears back a little: the gather you read before the lunge
        push(mob, mob.position().subtract(target.position()), 0.12, 0);
        tell(mob, TELL);
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), MobSounds.GROWL.get(), mob.getSoundSource(), 1.0F, pitch(mob));
    }

    private void lunge(LivingEntity t) {
        double gap = Math.max(0, Math.sqrt(mob.distanceToSqr(t)) - (mob.getBbWidth() + t.getBbWidth()) * 0.5);
        push(mob, t.position().subtract(mob.position()), Mth.clamp(gap * 0.35, 0.15, 0.45), mob.onGround() ? 0.22 : 0);
    }

    private void bite(LivingEntity t) {
        release();
        resetAttackCooldown();
        if (!(mob.level() instanceof ServerLevel level) || !inReach(mob, t) || !mob.getSensing().hasLineOfSight(t)) return; // it bit air
        mob.swingForAttack(InteractionHand.MAIN_HAND);
        Strikes.deliver(mob, Strikes.Kind.LIGHT, () -> mob.doHurtTarget(level, t));
    }

    /** Out of the pack's turns; a wind-up cut short takes its tell back. */
    private void release() {
        WINDING.remove(this);
        if (left > 0) tell(mob, 0);
        left = 0;
        on = null;
    }

    static boolean inReach(PathfinderMob mob, LivingEntity t) {
        return mob.isWithinMeleeAttackRange(t) || mob.getBoundingBox().inflate(LUNGE_REACH, 0.3, LUNGE_REACH).intersects(t.getBoundingBox());
    }

    /** Beasts winding up on {@code target} now (dropping any whose body is gone). */
    static int winding(LivingEntity target) {
        int n = 0;
        for (int i = WINDING.size() - 1; i >= 0; i--) {
            ToldBite b = WINDING.get(i);
            if (b.mob.isRemoved()) WINDING.remove(i);
            else if (b.on == target) n++;
        }
        return n;
    }

    private static void push(Entity e, Vec3 dir, double strength, double up) {
        double n = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (n < 1.0E-4) return;
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x + dir.x / n * strength, Math.max(v.y, up), v.z + dir.z / n * strength);
        e.needsSync = true;
    }

    /** A small beast growls high, a big one low. */
    private static float pitch(PathfinderMob mob) {
        return Mth.clamp(1.35F - mob.getBbWidth() * 0.4F, 0.6F, 1.3F);
    }

    // ---- the tell, on both sides ----------------------------------------------------------------------------------------

    // body id -> (game tick its bite lands << 4 | class); the server's own, and what the client heard
    private static final Int2LongOpenHashMap SERVER = new Int2LongOpenHashMap(), CLIENT = new Int2LongOpenHashMap();

    static {
        SERVER.defaultReturnValue(Long.MIN_VALUE);
        CLIENT.defaultReturnValue(Long.MIN_VALUE);
    }

    /** Shows a beast's bite coming {@code ticks} from now (0: no bite coming) to the players near it. */
    private static void tell(PathfinderMob mob, int ticks) {
        put(SERVER, mob.getId(), ticks, mob.level().getGameTime());
        if (!(mob.level() instanceof ServerLevel level)) return;
        MobsNet.Tell msg = new MobsNet.Tell(mob.getId(), Strikes.Kind.LIGHT.ordinal(), ticks);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) <= TELL_RANGE * TELL_RANGE) AwakenNet.send(p, msg);
        }
    }

    /** The client heard a beast's tell ({@code mobs_tell}). */
    public static void heard(int id, int ticks, long gameTime) {
        put(CLIENT, id, ticks, gameTime);
    }

    private static void put(Int2LongOpenHashMap map, int id, int ticks, long now) {
        if (ticks <= 0) {
            map.remove(id);
            return;
        }
        if (map.size() > 64) map.values().removeIf((java.util.function.LongPredicate) v -> (v >> 4) <= now); // bodies gone before their bite was read
        map.put(id, (now + ticks) << 4 | Strikes.Kind.LIGHT.ordinal());
    }

    /** The bite this beast is winding up, or null (the server's word on the server, what it said on the client). */
    public static Strikes.@Nullable Tell tellOf(Entity e) {
        Int2LongOpenHashMap map = e.level().isClientSide() ? CLIENT : SERVER;
        long v = map.get(e.getId());
        if (v == Long.MIN_VALUE) return null;
        int ticks = (int) ((v >> 4) - e.level().getGameTime());
        if (ticks <= 0) {
            map.remove(e.getId());
            return null;
        }
        return new Strikes.Tell(ticks, Strikes.Kind.values()[(int) (v & 15)]);
    }

    /** Leaving a world (client) or the server stopping: nothing is winding up. */
    public static void reset(boolean client) {
        if (client) CLIENT.clear();
        else {
            SERVER.clear();
            WINDING.clear();
        }
    }
}
