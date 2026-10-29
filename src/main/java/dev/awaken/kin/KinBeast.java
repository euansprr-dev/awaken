package dev.awaken.kin;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One of the kin (SPEC §43.5): a beast of the land with a want and a day. Wild, it keeps near its own spot (a Lampwing's
 * edge of the trees, a Pebblepup's pile) and shows its want there; it runs from you as far as its kind's Kinship with you
 * says. Helped three times by one player it trusts them, and follows them home if their Hearth has a nook it likes
 * ({@link KinHome}); then it lives there and works. A beast that lives with someone never dies: badly hurt, it hides
 * and comes back. What it is doing is one synced act, which the client turns into its clips.
 */
public class KinBeast extends PathfinderMob {
    public static final int IDLE = 0, WANT = 1, FLEE = 2, TELL = 3, NUDGE = 4, WORK = 5, SLEEP = 6, FOLLOW = 7, HIDE = 8, SNIFF = 9, CARRY = 10;
    private static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(KinBeast.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACT_AT = SynchedEntityData.defineId(KinBeast.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HOMED = SynchedEntityData.defineId(KinBeast.class, EntityDataSerializers.BOOLEAN);

    private @Nullable KinKind kind;
    /** Its own spot in the wild (a pile, a burrow field, the edge of the trees). */
    public BlockPos origin = BlockPos.ZERO;
    /** Where it lives: the Hearth's Anchor and its nook; null while wild. */
    public @Nullable BlockPos home, nook;
    public final Map<UUID, Integer> helps = new HashMap<>();
    public final Set<UUID> trusted = new HashSet<>();
    public @Nullable UUID follow;
    /** Who it lives with (the one who brought it home). */
    public @Nullable UUID keeper;
    public final List<ItemStack> carry = new ArrayList<>();
    public int made;
    public long madeDay = -1, hideUntil, lastHelp, nextWant;
    /** The tower's song (SPEC §43.7): how it takes it, and where the tower is, until {@code songUntil}. Not saved. */
    public long songUntil;
    public int songMode;
    public @Nullable BlockPos songAt;
    public static final int SONG_HUNKER = 1, SONG_LISTEN = 2, SONG_GATHER = 3, SONG_SING = 4;
    /** Ticks a player's want-watching has gone on (one watcher at a time). */
    public int watchTicks;
    public @Nullable UUID watcher;

    public KinBeast(EntityType<? extends KinBeast> type, Level level) {
        super(type, level);
        if (kind() == KinKind.LAMPWING) {
            moveControl = new FlyingMoveControl<>(this, 20, true);
            setNoGravity(true);
        }
    }

    public KinKind kind() {
        if (kind == null) {
            KinKind k = KinContent.kindOf(getType());
            kind = k == null ? KinKind.LAMPWING : k;
        }
        return kind;
    }

    public static AttributeSupplier.Builder attributes(KinKind k) {
        AttributeSupplier.Builder b = PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, k == KinKind.MOSSBACK ? 16 : 10)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.MOVEMENT_SPEED, switch (k) {
                    case MOSSBACK -> 0.12;
                    case BURROWBUN -> 0.3;
                    case PEBBLEPUP -> 0.28;
                    default -> 0.25;
                });
        if (k == KinKind.LAMPWING) b.add(Attributes.FLYING_SPEED, 0.45);
        return b;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (KinContent.kindOf(getType()) == KinKind.LAMPWING) {
            FlyingPathNavigation n = new FlyingPathNavigation(this, level);
            n.setCanOpenDoors(false);
            n.setCanFloat(true);
            return n;
        }
        return super.createNavigation(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ACT, IDLE);
        b.define(DATA_ACT_AT, 0);
        b.define(DATA_HOMED, false);
    }

    // ---- what it's doing --------------------------------------------------------------------------------------------

    public int act() {
        return entityData.get(DATA_ACT);
    }

    public int actAt() {
        return entityData.get(DATA_ACT_AT);
    }

    public boolean homed() {
        return entityData.get(DATA_HOMED);
    }

    /** Starts an act (restarts it when {@code again}: a second tell still plays its clip). */
    public void act(int a, boolean again) {
        if (act() == a && !again) return;
        entityData.set(DATA_ACT, a);
        entityData.set(DATA_ACT_AT, tickCount);
    }

    public void act(int a) {
        act(a, false);
    }

    /** Lives with someone, or was trusted: befriended (they never die). */
    public boolean befriended() {
        return home != null || !trusted.isEmpty();
    }

    public void settle(BlockPos anchor, BlockPos nook, UUID keeper) {
        this.home = anchor.immutable();
        this.nook = nook.immutable();
        this.keeper = keeper;
        this.follow = null;
        entityData.set(DATA_HOMED, true);
        setPersistenceRequired();
        setHomeTo(nook, 24);
    }

    public void leaveHome() {
        home = nook = null;
        keeper = null;
        entityData.set(DATA_HOMED, false);
        setHomeTo(origin, 16);
    }

    // ---- goals ------------------------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        KinKind k = KinContent.kindOf(getType());
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HideGoal());
        goalSelector.addGoal(1, new SongGoal());
        goalSelector.addGoal(2, new PanicGoal(this, 1.5));
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, p -> p instanceof ServerPlayer sp && KinWatch.scares(this, sp), 10F, 1.1, 1.5,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR::test));
        goalSelector.addGoal(4, new FollowGoal());
        goalSelector.addGoal(5, new HomeGoal());
        goalSelector.addGoal(6, new WantGoal());
        goalSelector.addGoal(7, k == KinKind.LAMPWING ? new WaterAvoidingRandomFlyingGoal(this, 0.8) : new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** Badly hurt while it lives with someone: it runs off to its nook and stays hidden until it's well. */
    final class HideGoal extends Goal {
        HideGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return hideUntil > level().getGameTime();
        }

        @Override
        public void start() {
            act(HIDE);
        }

        @Override
        public void tick() {
            BlockPos to = nook != null ? nook : origin;
            if (distanceToSqr(to.getX() + 0.5, to.getY(), to.getZ() + 0.5) > 4) getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.4);
            if (tickCount % 40 == 0) heal(1F);
        }

        @Override
        public void stop() {
            act(IDLE);
        }
    }

    /**
     * The tower rings (SPEC §43.7): with a song still full of gaps it hunkers where it is, ears flat; with more bells it
     * stops and listens, then gathers under the tower, and at a hundred it sings.
     */
    final class SongGoal extends Goal {
        SongGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return songUntil > level().getGameTime() && songAt != null && hideUntil <= level().getGameTime();
        }

        @Override
        public void start() {
            getNavigation().stop();
            act(songMode == SONG_HUNKER ? HIDE : songMode == SONG_SING ? TELL : IDLE);
        }

        @Override
        public void tick() {
            BlockPos at = songAt;
            if (at == null) return;
            if (songMode != SONG_HUNKER) getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 2.5, at.getZ() + 0.5);
            if ((songMode == SONG_GATHER || songMode == SONG_SING) && distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > 16)
                getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.9);
            else getNavigation().stop();
            if (songMode == SONG_SING && tickCount % 40 == 0) act(TELL, true);
        }

        @Override
        public void stop() {
            act(IDLE);
        }
    }

    /** Trusting someone whose home has a nook for it: it comes along, a few steps behind. */
    final class FollowGoal extends Goal {
        FollowGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private @Nullable ServerPlayer who() {
            if (follow == null || !(level() instanceof ServerLevel sl)) return null;
            return sl.getServer().getPlayerList().getPlayer(follow) instanceof ServerPlayer p && p.level() == level() ? p : null;
        }

        @Override
        public boolean canUse() {
            return home == null && who() != null;
        }

        @Override
        public void start() {
            act(FOLLOW);
        }

        @Override
        public void tick() {
            ServerPlayer p = who();
            if (p == null) return;
            getLookControl().setLookAt(p, 30F, 30F);
            double d = distanceToSqr(p);
            if (d > 48 * 48) {
                follow = null;
                return;
            }
            if (d > 9) getNavigation().moveTo(p, d > 100 ? 1.3 : 1.0);
            else getNavigation().stop();
        }

        @Override
        public void stop() {
            act(IDLE);
        }
    }

    /** At home: its work, by its kind ({@link KinHome#work}). */
    final class HomeGoal extends Goal {
        HomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return home != null && level() instanceof ServerLevel;
        }

        @Override
        public void tick() {
            if (level() instanceof ServerLevel sl) KinHome.work(sl, KinBeast.this);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return false;
        }

        @Override
        public void stop() {
            act(IDLE);
        }
    }

    /** Wild: now and then it goes back to its spot and shows its want there (what a watcher learns from). */
    final class WantGoal extends Goal {
        private int left;

        WantGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (home != null || follow != null) return false;
            long now = level().getGameTime();
            if (now < nextWant || !KinWatch.wantsNow(KinBeast.this)) return false;
            return true;
        }

        @Override
        public void start() {
            left = 20 * (4 + random.nextInt(4));
            act(WANT, true);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && home == null && follow == null;
        }

        @Override
        public void tick() {
            left--;
            double d = distanceToSqr(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            if (d > 9) getNavigation().moveTo(origin.getX() + 0.5, origin.getY() + (kind() == KinKind.LAMPWING ? 2 : 0), origin.getZ() + 0.5, 0.9);
            else {
                getNavigation().stop();
                if (act() != WANT) act(WANT);
            }
        }

        @Override
        public void stop() {
            nextWant = level().getGameTime() + 20 * (10 + random.nextInt(20));
            if (act() == WANT) act(IDLE);
        }
    }

    // ---- life -------------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 10 == 0) KinHelp.beastTick(level, this);
        // a one-shot act ends by itself
        int a = act();
        if ((a == TELL || a == NUDGE || a == SNIFF) && tickCount - actAt() > 50) act(IDLE);
        if (a == IDLE && getDeltaMovement().horizontalDistanceSqr() < 1.0E-4 && KinWatch.asleepNow(this)) act(SLEEP);
        else if (a == SLEEP && !KinWatch.asleepNow(this)) act(IDLE);
        // a Lampwing settles to the ground to sleep, and is up in the air again when it wakes
        if (kind() == KinKind.LAMPWING) {
            boolean down = act() == SLEEP;
            if (isNoGravity() == down) setNoGravity(!down);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof ServerPlayer p && !p.isCreative()) KinWatch.hurtBy(this, p);
        if (befriended()) {
            // never dies: at most it's left at one heart, and then it hides
            float clamped = Math.min(damage, getHealth() - 1F);
            if (clamped <= 0F) {
                hideUntil = level.getGameTime() + 20 * 30;
                return false;
            }
            boolean hit = super.hurtServer(level, source, clamped);
            if (getHealth() <= 2F) hideUntil = level.getGameTime() + 20 * 30;
            return hit;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide() && source.getEntity() instanceof ServerPlayer p) KinWatch.killedBy(this, p);
        if (home != null && level() instanceof ServerLevel sl) KinWorld.get(sl.getServer()).leave(home, getUUID());
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        var s = KinContent.CALL.get(kind());
        return s == null || act() == SLEEP ? null : s.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        var s = KinContent.WORRY.get(kind());
        return s == null ? null : s.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState onState, BlockPos pos) {
        if (kind() != KinKind.LAMPWING) super.checkFallDamage(ya, onGround, onState, pos);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return kind() != KinKind.LAMPWING && super.causeFallDamage(distance, multiplier, source);
    }

    // ---- saving (schema 1) -----------------------------------------------------------------------------------------

    private static final Codec<Map<UUID, Integer>> HELPS = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("kin_schema", 1);
        out.store("origin", BlockPos.CODEC, origin);
        out.storeNullable("home", BlockPos.CODEC, home);
        out.storeNullable("nook", BlockPos.CODEC, nook);
        out.store("helps", HELPS, new HashMap<>(helps));
        out.store("trusted", UUIDUtil.STRING_CODEC.listOf(), List.copyOf(trusted));
        out.storeNullable("follow", UUIDUtil.STRING_CODEC, follow);
        out.storeNullable("keeper", UUIDUtil.STRING_CODEC, keeper);
        out.store("carry", ItemStack.CODEC.listOf(), carry.stream().filter(s -> !s.isEmpty()).toList());
        out.putInt("made", made);
        out.putLong("made_day", madeDay);
        out.putLong("hide_until", hideUntil);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        origin = in.read("origin", BlockPos.CODEC).orElse(blockPosition());
        home = in.read("home", BlockPos.CODEC).orElse(null);
        nook = in.read("nook", BlockPos.CODEC).orElse(null);
        helps.clear();
        helps.putAll(in.read("helps", HELPS).orElse(Map.of()));
        trusted.clear();
        trusted.addAll(in.read("trusted", UUIDUtil.STRING_CODEC.listOf()).orElse(List.of()));
        follow = in.read("follow", UUIDUtil.STRING_CODEC).orElse(null);
        keeper = in.read("keeper", UUIDUtil.STRING_CODEC).orElse(null);
        carry.clear();
        carry.addAll(in.read("carry", ItemStack.CODEC.listOf()).orElse(List.of()));
        made = in.getIntOr("made", 0);
        madeDay = in.getLongOr("made_day", -1);
        hideUntil = in.getLongOr("hide_until", 0);
        entityData.set(DATA_HOMED, home != null);
        if (home != null && nook != null) setHomeTo(nook, 24);
        else setHomeTo(origin, 16);
    }

    /** Placed in the wild at its spot (the spawner, a command). */
    public void wildAt(BlockPos spot) {
        origin = spot.immutable();
        setHomeTo(origin, 16);
        setPersistenceRequired();
    }
}
