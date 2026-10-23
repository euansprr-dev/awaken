package dev.awaken.mobs.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.api.HearthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * An overworld creature that can become a mount (plan v2 section 4.4, the 11-20 band): the Frost Stag, the Dune Ray
 * and the Tidesteed (and, through {@link WaterFauna}, the base of every water creature). The trust ritual: offer its
 * favourite food while sneaking, a few times (it lets a sneaking player who holds that food come close); then it
 * trusts you and follows you; a Hunter's Bridle makes it rideable. A trusted mount never despawns, never turns on its
 * owner, can be told to rest (sneak and use it with an empty hand; at a Hearth it heals), and answers the owner's
 * whistle (the world module). Riding: a charged jump on land, a hovering glide for the ray, swimming where you look
 * for the Tidesteed (the rider breathes). The owner's UUID, the trust and the bond are saved, versioned.
 */
public class WildMount extends FaunaMob implements PlayerRideableJumping, WildPose {
    public static final int SCHEMA = 1;
    public static final int B_TRUSTED = 1, B_BRIDLED = 2, B_RESTING = 4;
    /** Ticks between two feedings that count (it needs a moment to eat). */
    public static final int FEED_GAP = 30;

    private static final EntityDataAccessor<Byte> DATA_BOND = SynchedEntityData.defineId(WildMount.class, EntityDataSerializers.BYTE);
    /** Held poses (WildPose.S_*) the server sets (a jelly's bell emptied, an otter's find in its mouth). */
    private static final EntityDataAccessor<Integer> DATA_WILD = SynchedEntityData.defineId(WildMount.class, EntityDataSerializers.INT);
    /** Autopilot rides: the game time until which the mount runs forward on its own, and when it jumps. */
    private static final EntityDataAccessor<Integer> DATA_DRIVE = SynchedEntityData.defineId(WildMount.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DRIVE_JUMP = SynchedEntityData.defineId(WildMount.class, EntityDataSerializers.INT);

    /** What is saved: the owner, the trust gained so far (and from whom), the bond bits, where it rests. */
    public record Bond(int schema, Optional<UUID> owner, Optional<UUID> trustBy, int trust, int bits, Optional<BlockPos> rest) {
        public static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Bond::schema),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Bond::owner),
                UUIDUtil.CODEC.optionalFieldOf("trust_by").forGetter(Bond::trustBy),
                Codec.INT.optionalFieldOf("trust", 0).forGetter(Bond::trust),
                Codec.INT.optionalFieldOf("bits", 0).forGetter(Bond::bits),
                BlockPos.CODEC.optionalFieldOf("rest").forGetter(Bond::rest)
        ).apply(i, Bond::new));
    }

    // server (no field initialisers: registerGoals runs inside the super constructor)
    @Nullable UUID owner, trustBy;
    int trust;
    long lastFeed;
    @Nullable BlockPos restAt;
    // the side that moves the mount (the rider's client, or the server)
    private float jumpPending;
    private int jumpCooldown, lastDriveJump;

    public WildMount(EntityType<? extends WildMount> type, Level level) {
        super(type, level);
    }

    /** Its mount table entry, or null for a creature that is never ridden (fish, jellies, otters). */
    public Fauna3Kinds.@Nullable Kind kind() {
        return Fauna3Kinds.mount(def().id);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_BOND, (byte) 0);
        b.define(DATA_WILD, 0);
        b.define(DATA_DRIVE, 0);
        b.define(DATA_DRIVE_JUMP, 0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        if (kind() != null) {
            goal(3, new Fauna3Goals.Rest(this));
            goal(4, new Fauna3Goals.FollowOwner(this));
        }
    }

    // ---- the bond ----------------------------------------------------------------------------------------------------

    private boolean bit(int b) {
        return (entityData.get(DATA_BOND) & b) != 0;
    }

    private void setBit(int b, boolean on) {
        int v = entityData.get(DATA_BOND);
        entityData.set(DATA_BOND, (byte) (on ? v | b : v & ~b));
    }

    public boolean trusted() {
        return bit(B_TRUSTED);
    }

    public boolean bridled() {
        return bit(B_BRIDLED);
    }

    public boolean resting() {
        return bit(B_RESTING);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public boolean ownedBy(Player p) {
        return owner != null && owner.equals(p.getUUID());
    }

    public int trust() {
        return trust;
    }

    public @Nullable BlockPos restAt() {
        return restAt;
    }

    protected void setWild(int bits, boolean on) {
        int v = entityData.get(DATA_WILD);
        int n = on ? v | bits : v & ~bits;
        if (n != v) entityData.set(DATA_WILD, n);
    }

    protected boolean wild(int bits) {
        return (entityData.get(DATA_WILD) & bits) != 0;
    }

    @Override
    public int wildState() {
        int s = entityData.get(DATA_WILD);
        if (resting()) s |= S_REST;
        if (bridled()) s |= S_BRIDLED;
        if (isVehicle()) s |= S_RIDDEN;
        if (isInWater()) s |= S_SWIM;
        return s;
    }

    /** Server: trust it to {@code player} at once (commands, tests, the autopilot). */
    public void tameTo(ServerPlayer player, boolean bridle) {
        owner = player.getUUID();
        trustBy = owner;
        Fauna3Kinds.Kind k = kind();
        trust = k == null ? 1 : k.trust();
        setBit(B_TRUSTED, true);
        setPersistenceRequired();
        setTarget(null);
        if (bridle) setBit(B_BRIDLED, true);
        MountHooks.server().tamed(this, player);
        if (bridle) MountHooks.server().bridled(this, player);
    }

    /** Server: rest here (or at {@code at}) until called: it stays close and heals at a Hearth. */
    public void rest(@Nullable BlockPos at) {
        restAt = at != null ? at : blockPosition();
        setBit(B_RESTING, true);
        setHomeTo(restAt, 6);
        getNavigation().stop();
    }

    public void wake() {
        setBit(B_RESTING, false);
        restAt = null;
        clearHome();
    }

    // ---- interaction ---------------------------------------------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        Fauna3Kinds.Kind k = kind();
        if (k == null || isVehicle()) return super.mobInteract(player, hand);
        ItemStack stack = player.getItemInHand(hand);
        boolean food = k.favourite(stack);
        boolean bridle = Fauna3Kinds.isBridle(stack);
        if (level().isClientSide()) {
            // predict what the server will do; it decides
            boolean act = food || bridle || (bridled() && !player.isShiftKeyDown()) || (trusted() && player.isShiftKeyDown() && stack.isEmpty());
            return act ? InteractionResult.SUCCESS : super.mobInteract(player, hand);
        }
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (food) return feed(sp, stack, k);
        boolean mine = ownedBy(sp);
        if (bridle) {
            if (!trusted()) {
                say(sp, "It won't take a bridle yet. Sneak and offer it " + foodName(k) + " first.");
                return InteractionResult.CONSUME;
            }
            if (!mine) {
                say(sp, "It trusts someone else.");
                return InteractionResult.CONSUME;
            }
            if (bridled()) {
                say(sp, "It already wears your bridle.");
                return InteractionResult.CONSUME;
            }
            stack.consume(1, sp);
            setBit(B_BRIDLED, true);
            playSound(SoundEvents.HORSE_SADDLE.value(), 1.0F, 1.0F);
            ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.7, getZ(), 8, 0.4, 0.3, 0.4, 0.0);
            MountHooks.server().bridled(this, sp);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (mine && sp.isShiftKeyDown() && stack.isEmpty()) {
            if (resting()) {
                wake();
                say(sp, def().name + " follows you again.");
            } else {
                rest(null);
                say(sp, def().name + " rests here" + (HearthHooks.inside((ServerLevel) level(), blockPosition()) ? " at your Hearth, and heals." : ". Your whistle calls it."));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (bridled() && !sp.isShiftKeyDown()) {
            if (!mine) {
                say(sp, "It wears someone else's bridle.");
                return InteractionResult.CONSUME;
            }
            if (resting()) wake();
            sp.setYRot(getYRot());
            sp.setXRot(getXRot());
            if (sp.startRiding(this)) MountHooks.server().mounted(this, sp);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (trusted() && !mine) {
            say(sp, "It trusts someone else.");
            return InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    /** One step of the trust ritual (or a treat for a mount that already trusts you). */
    private InteractionResult feed(ServerPlayer sp, ItemStack stack, Fauna3Kinds.Kind k) {
        ServerLevel level = (ServerLevel) level();
        if (trusted()) {
            if (ownedBy(sp) && getHealth() < getMaxHealth()) {
                stack.consume(1, sp);
                heal(4.0F);
                playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
                level.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.CONSUME;
        }
        if (!sp.isShiftKeyDown()) {
            say(sp, "Sneak, then offer it " + foodName(k) + ".");
            return InteractionResult.CONSUME;
        }
        if (getTarget() != null) {
            say(sp, "Not while it is angry.");
            return InteractionResult.CONSUME;
        }
        String refusal = MountHooks.server().tameRefusal(sp, this);
        if (refusal != null) {
            say(sp, refusal);
            return InteractionResult.CONSUME;
        }
        long now = level.getGameTime();
        if (now - lastFeed < FEED_GAP) return InteractionResult.CONSUME; // still eating
        lastFeed = now;
        if (trustBy == null || !trustBy.equals(sp.getUUID())) {
            trustBy = sp.getUUID();
            trust = 0; // trust is earned by one hand
        }
        stack.consume(1, sp);
        trust++;
        scareTicks = 0;
        scareFrom = null;
        getNavigation().stop();
        getLookControl().setLookAt(sp, 30.0F, 30.0F);
        playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 0.9F + random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 1 + trust, 0.3, 0.2, 0.3, 0.0);
        MountHooks.server().trust(this, sp, trust, k.trust());
        if (trust >= k.trust()) {
            owner = sp.getUUID();
            setBit(B_TRUSTED, true);
            setPersistenceRequired();
            playSound(SoundEvents.PLAYER_LEVELUP, 0.5F, 1.4F);
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 7, 0.5, 0.3, 0.5, 0.0);
            MountHooks.server().tamed(this, sp);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static String foodName(Fauna3Kinds.Kind k) {
        return new ItemStack(k.favouriteItem()).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
    }

    static void say(ServerPlayer sp, String line) {
        sp.sendOverlayMessage(Component.literal(line));
    }

    // ---- manners of a trusted mount -----------------------------------------------------------------------------------

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target instanceof Player p && ownedBy(p)) return; // it never turns on its owner
        super.setTarget(target);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return owner == null && super.removeWhenFarAway(distSqr);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return owner != null || super.requiresCustomPersistence();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (scareTicks > 0 && kind() != null && (trusted() || curious(level))) {
            // a trusted mount never bolts from you; a wild one lets a sneaking player with its food come close
            scareTicks = 0;
            scareFrom = null;
        }
    }

    private boolean curious(ServerLevel level) {
        Player p = level.getNearestPlayer(this, 8.0);
        Fauna3Kinds.Kind k = kind();
        return p != null && k != null && p.isShiftKeyDown() && (k.favourite(p.getMainHandItem()) || k.favourite(p.getOffhandItem()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        int slot = tickCount + getId();
        if (owner != null && slot % 40 == 0) MountHooks.server().seen(this);
        if (resting() && slot % 100 == 0 && getHealth() < getMaxHealth() && HearthHooks.inside(level, blockPosition())) {
            heal(2.0F);
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 1, 0.3, 0.2, 0.3, 0.0);
        }
        Fauna3Kinds.Kind k = kind();
        if (k != null && k.style() == Fauna3Kinds.Style.SWIM && getFirstPassenger() instanceof Player rider) {
            // the Tidesteed's rider breathes from its air (vanilla's nautilus effect)
            if (!rider.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS) || level.getGameTime() % 40L == 0L) {
                rider.addEffect(new MobEffectInstance(MobEffects.BREATH_OF_THE_NAUTILUS, 60, 0, true, true, true));
            }
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (owner != null && level() instanceof ServerLevel && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            MountHooks.server().gone(this, reason == RemovalReason.KILLED);
        }
    }

    // ---- riding ------------------------------------------------------------------------------------------------------

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return bridled() && kind() != null && getFirstPassenger() instanceof Player p ? p : super.getControllingPassenger();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !isVehicle() && bridled();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        Fauna3Kinds.Kind k = kind();
        if (k == null) return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
        float s = getScale();
        return new Vec3(0.0, k.seatY() * s, -k.seatZ() * s).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity l) l.yBodyRot = yBodyRot;
    }

    @Override
    protected void tickRidden(Player controller, Vec3 input) {
        super.tickRidden(controller, input);
        Fauna3Kinds.Kind k = kind();
        if (k == null) return;
        float yaw = getYRot() + Mth.wrapDegrees(controller.getYRot() - getYRot()) * (k.style() == Fauna3Kinds.Style.SWIM ? 0.5F : 1.0F);
        float pitch = k.style() == Fauna3Kinds.Style.SWIM && isInWater() ? controller.getXRot() * 0.5F : 0.0F;
        setRot(yaw, pitch);
        yRotO = yBodyRot = yHeadRot = getYRot();
        if (!isLocalInstanceAuthoritative()) return;
        if (jumpCooldown > 0) jumpCooldown--;
        int jumpAt = entityData.get(DATA_DRIVE_JUMP);
        long now = level().getGameTime();
        if (jumpAt != 0 && jumpAt != lastDriveJump && now >= jumpAt && now < jumpAt + 40 && readyToJump(k)) {
            lastDriveJump = jumpAt;
            jumpPending = 1.0F;
        }
        if (jumpPending > 0 && readyToJump(k)) {
            leap(k, jumpPending, controller);
            jumpPending = 0;
        }
    }

    private boolean readyToJump(Fauna3Kinds.Kind k) {
        if (jumpCooldown > 0) return false;
        return switch (k.style()) {
            case GROUND -> onGround();
            case GLIDE -> heightAboveGround(4) < MountPhysics.HOVER + 1.5;
            case SWIM -> true;
        };
    }

    private void leap(Fauna3Kinds.Kind k, float charge, Player controller) {
        Vec3 v = getDeltaMovement();
        float yawRad = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad);
        switch (k.style()) {
            case GROUND -> {
                setDeltaMovement(v.x + fx * 0.4 * charge, MountPhysics.jumpImpulse(k.style(), charge, k.jump()), v.z + fz * 0.4 * charge);
                net.neoforged.neoforge.common.CommonHooks.onLivingJump(this);
            }
            case GLIDE -> setDeltaMovement(v.x + fx * 0.3 * charge, MountPhysics.jumpImpulse(k.style(), charge, k.jump()), v.z + fz * 0.3 * charge);
            case SWIM -> addDeltaMovement(MountPhysics.dash(controller.getLookAngle(), charge, getAttributeValue(Attributes.MOVEMENT_SPEED),
                    isInWater(), isInWater() && !isUnderWater()));
        }
        jumpCooldown = MountPhysics.JUMP_COOLDOWN;
        needsSync = true;
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        Fauna3Kinds.Kind k = kind();
        if (k == null) return selfInput;
        boolean driving = level().getGameTime() < entityData.get(DATA_DRIVE);
        float strafe = driving ? 0.0F : controller.xxa, forward = driving ? 1.0F : controller.zza;
        if (k.style() == Fauna3Kinds.Style.SWIM && isInWater()) return MountPhysics.swimInput(strafe, forward, driving ? 0.0F : controller.getXRot());
        return MountPhysics.groundInput(strafe, forward);
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        Fauna3Kinds.Kind k = kind();
        if (k == null) return getSpeed();
        return MountPhysics.rideSpeed(k.style(), getAttributeValue(Attributes.MOVEMENT_SPEED), k.speed(), isInWater());
    }

    @Override
    public void travel(Vec3 input) {
        Fauna3Kinds.Kind k = kind();
        if (k != null && k.style() == Fauna3Kinds.Style.GLIDE && getControllingPassenger() instanceof Player && !isInWater()) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, MountPhysics.glideVertical(heightAboveGround(12), v.y), v.z);
            resetFallDistance();
        }
        super.travel(input);
    }

    /** Blocks between its feet and the first thing under it that stops a fall (at most {@code max}). */
    double heightAboveGround(int max) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(getBlockX(), getBlockY(), getBlockZ());
        for (int i = 0; i <= max; i++) {
            var state = level().getBlockState(m);
            if (!state.getCollisionShape(level(), m).isEmpty() || !state.getFluidState().isEmpty()) {
                double top = m.getY() + (state.getFluidState().isEmpty() ? state.getCollisionShape(level(), m).max(net.minecraft.core.Direction.Axis.Y) : 0.9);
                return Math.max(0.0, getY() - top);
            }
            m.move(net.minecraft.core.Direction.DOWN);
        }
        return max + 1;
    }

    @Override
    public boolean canJump() {
        return bridled() && kind() != null;
    }

    @Override
    public void onPlayerJump(int amount) {
        if (canJump() && jumpCooldown <= 0) jumpPending = getPlayerJumpPendingScale(Math.max(0, amount));
    }

    @Override
    public void handleStartJump(int scale) {
        Fauna3Kinds.Kind k = kind();
        if (k == null) return;
        switch (k.style()) {
            case GROUND -> playSound(SoundEvents.HORSE_JUMP, 0.5F, 1.1F);
            case GLIDE -> playSound(SoundEvents.PHANTOM_FLAP, 0.8F, 1.2F);
            case SWIM -> playSound(SoundEvents.DOLPHIN_JUMP, 0.9F, 0.9F);
        }
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public int getJumpCooldown() {
        return jumpCooldown;
    }

    /** Tests: what the rider's keys ask of the mount this tick, and how fast it goes for them. */
    public Vec3 riddenInputFor(Player rider) {
        return getRiddenInput(rider, Vec3.ZERO);
    }

    public float riddenSpeedFor(Player rider) {
        return getRiddenSpeed(rider);
    }

    /** Tests: the charge of a jump the rider asked for that has not happened yet. */
    public float jumpPending() {
        return jumpPending;
    }

    /** Autopilot and tests: run forward for {@code ticks} (and jump once after {@code jumpAfter} ticks, or never if < 0). */
    public void drive(int ticks, int jumpAfter) {
        long now = level().getGameTime();
        entityData.set(DATA_DRIVE, (int) (now + ticks));
        entityData.set(DATA_DRIVE_JUMP, jumpAfter < 0 ? 0 : (int) (now + jumpAfter));
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, net.minecraft.world.damagesource.DamageSource source) {
        Fauna3Kinds.Kind k = kind();
        // a mount lands its own jumps: only real falls hurt (and never a glider)
        if (k != null && (k.style() == Fauna3Kinds.Style.GLIDE || fallDistance < 6)) return false;
        return super.causeFallDamage(fallDistance, damageModifier, source);
    }

    // ---- save --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (kind() == null && owner == null) return;
        out.store("awaken_mount", Bond.CODEC, new Bond(SCHEMA, Optional.ofNullable(owner), Optional.ofNullable(trustBy), trust,
                entityData.get(DATA_BOND), Optional.ofNullable(restAt)));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("awaken_mount", Bond.CODEC).ifPresent(b -> {
            owner = b.owner().orElse(null);
            trustBy = b.trustBy().orElse(null);
            trust = b.trust();
            entityData.set(DATA_BOND, (byte) (b.bits() & (B_TRUSTED | B_BRIDLED | B_RESTING)));
            restAt = b.rest().orElse(null);
            if (resting() && restAt != null) setHomeTo(restAt, 6);
            if (owner != null) setPersistenceRequired();
        });
    }

    /** The saved bond as it stands (tests). */
    public Bond bond() {
        return new Bond(SCHEMA, Optional.ofNullable(owner), Optional.ofNullable(trustBy), trust, entityData.get(DATA_BOND), Optional.ofNullable(restAt));
    }
}
