package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.MobScaling;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Model;
import dev.awaken.mobs.def.Tier;
import dev.awaken.mobs.def.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.awaken.core.api.Strikes;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Base of every AWAKEN creature. The def (from the entity type) supplies stats, look and abilities; this class adds
 * SPEC scaling, roles, the Five Forces tags (VOLATILE explodes on death, ECHO gets up once, HAUNTED is a phasing
 * spirit, FROSTED and CHARGED change hits), telegraphed abilities and the passive traits. Kept cheap: simple goals,
 * nothing heavier than one small entity query every few ticks.
 */
public class AwakenMob extends Monster {
    public static final int F_HAUNTED = 1, F_FROSTED = 1 << 1, F_CHARGED = 1 << 2, F_VOLATILE = 1 << 3, F_ECHO = 1 << 4,
            F_RISEN = 1 << 5, F_FALLEN = 1 << 6, F_PHASED = 1 << 7, F_GUARDING = 1 << 8, F_CHARGING = 1 << 9,
            F_DECOY = 1 << 10, F_FUSING = 1 << 11, F_DORMANT = 1 << 12, F_DUEL = 1 << 13, F_HEAVY = 1 << 14,
            F_SPLIT_CHILD = 1 << 15;
    /** Flags that are saved with the entity (the rest are recomputed). */
    private static final int SAVED_FLAGS = F_ECHO | F_RISEN | F_DECOY | F_DORMANT | F_SPLIT_CHILD | F_VOLATILE | F_HAUNTED;

    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(AwakenMob.class, EntityDataSerializers.INT);
    /** Remaining wind-up ticks of the current telegraph (0 = none). */
    private static final EntityDataAccessor<Integer> DATA_TELEGRAPH = SynchedEntityData.defineId(AwakenMob.class, EntityDataSerializers.INT);
    /** The class of the blow being told (core.api.Strikes.Kind ordinal; NONE between blows), for the tell's look. */
    private static final EntityDataAccessor<Byte> DATA_TELL = SynchedEntityData.defineId(AwakenMob.class, EntityDataSerializers.BYTE);
    /** Role ordinal (MobRole.Role) for visuals. */
    private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(AwakenMob.class, EntityDataSerializers.BYTE);
    /** Entity id of the player a MIMIC wears, -1 if none. */
    private static final EntityDataAccessor<Integer> DATA_MIMIC = SynchedEntityData.defineId(AwakenMob.class, EntityDataSerializers.INT);

    private static final Identifier FROST_SLOW = Awaken.id("mobs_frosted");
    private static final Identifier FRENZY_SPEED = Awaken.id("mobs_frenzy_speed");
    private static final Identifier FRENZY_POWER = Awaken.id("mobs_frenzy_power");

    private @Nullable MobDef def;
    protected @Nullable AbilityRunner abilities;
    protected @Nullable ServerBossEvent bar;
    /** Players tracking this mob. Its bar shows to the ones near its height: tracking ignores height, and a gate's
     *  floors are stacked 32 blocks apart, so a living Lord would otherwise show its bar on every floor above it. */
    private final Set<ServerPlayer> watchers = new HashSet<>();
    static final int BAR_STOREY = 24;

    private ItemStack stolen = ItemStack.EMPTY;
    int fleeTicks;
    @Nullable Player robbed;
    private int fallenTicks, fuseTicks, decoyTicks, hopCooldown, guardTicks, frenzyTicks, auraTicks, auraPulse, staggerTicks;
    private float auraRadius, auraDamage;
    private BoltKind auraBolt = BoltKind.PLAIN;
    private @Nullable MobEffectInstance auraEffect;
    private boolean watched, exploded;
    @Nullable Vec3 heardPos;
    int heardTicks;
    private int quietTicks;

    // client-side visuals
    private @Nullable AwLook look;
    /** Idle loop for models animated by keyframes (Breeze). */
    public final net.minecraft.world.entity.AnimationState idleAnim = new net.minecraft.world.entity.AnimationState();
    public float squish, oSquish;
    private boolean wasOnGround = true;

    public AwakenMob(EntityType<? extends AwakenMob> type, Level level) {
        super(type, level);
        MobDef d = def();
        this.xpReward = d.xp;
        if (d.flies()) {
            this.moveControl = new FlyerMoveControl(this);
            this.setNoGravity(true);
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) setDropChance(slot, 0.0F);
        if (d.tier.isBoss() || d.tier == Tier.WARDEN) setPersistenceRequired();
        if (!level.isClientSide()) {
            int flags = 0;
            if (d.has(Trait.HAUNTED)) flags |= F_HAUNTED;
            if (d.has(Trait.RISES)) flags |= F_ECHO;
            if (d.has(Trait.FUSE)) flags |= F_VOLATILE;
            if (d.has(Trait.DORMANT)) flags |= F_DORMANT;
            entityData.set(DATA_FLAGS, flags);
            entityData.set(DATA_ROLE, (byte) d.tier.role.ordinal());
        }
    }

    public final MobDef def() {
        if (def == null) def = MobTypes.def(getType());
        return def;
    }

    // ---- setup -------------------------------------------------------------------------------------------------

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (def().flies()) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            return nav;
        }
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        MobDef d = def();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MobGoals.Hold(this));
        if (d.has(Trait.STEALER)) goalSelector.addGoal(2, new MobGoals.Flee(this));
        if (d.has(Trait.SPIRIT_HUNTER)) goalSelector.addGoal(2, new MobGoals.SpiritHunt(this));
        if (d.flies()) {
            goalSelector.addGoal(3, new MobGoals.FlyHunt(this));
            goalSelector.addGoal(7, new MobGoals.FlyWander(this));
        } else {
            if (d.has(Trait.RANGED)) goalSelector.addGoal(3, new MobGoals.KeepRange(this, 9.0F));
            else goalSelector.addGoal(3, new MobGoals.ToldMelee(this));
            if (d.has(Trait.LISTENER)) goalSelector.addGoal(4, new MobGoals.SeekSound(this));
            if (d.tier.isBoss()) goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
            goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        }
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        if (d.has(Trait.FRIENDLY)) return;
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        if (d.has(Trait.NEUTRAL)) return;
        if (d.has(Trait.LISTENER)) targetSelector.addGoal(2, new MobGoals.HearTarget(this));
        else targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, !d.has(Trait.HUNTER), false, null));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_FLAGS, 0);
        b.define(DATA_TELEGRAPH, 0);
        b.define(DATA_TELL, (byte) Strikes.Kind.NONE.ordinal());
        b.define(DATA_ROLE, (byte) 0);
        b.define(DATA_MIMIC, -1);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        MobDef d = def();
        d.gear.forEach((slot, item) -> setItemSlot(slot, gearStack(slot, item)));
        if (getData(CoreApi.MOB_ROLE.get()).role() == MobRole.Role.NONE) {
            // Spawn eggs and summons without a caller: use the theme's home rank.
            configure(d.theme.home.contentLevel, new MobRole(d.tier.role, d.theme.home.contentLevel, d.theme.id,
                    d.tier.isBoss() ? java.util.Optional.of(d.bossId()) : java.util.Optional.empty(), ""), Set.of());
        }
        if (d.tier.isBoss() || d.tier == Tier.WARDEN) setHomeTo(blockPosition(), d.arena);
        return out;
    }

    /**
     * What a creature holds is its look, never its strength: a held weapon's attack damage would be added to the def's
     * and then multiplied by the level scaling (SPEC §2), making three Lords 2-3x their numbers. Armour stays real.
     */
    static ItemStack gearStack(EquipmentSlot slot, net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            stack.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        }
        return stack;
    }

    /** Applies scaling, the role and any Forces tags. Called by the spawner after finalizeSpawn. */
    public void configure(int lv, MobRole role, Set<Forces.Tag> tags) {
        MobScaling.apply(this, lv, role, def().tier);
        entityData.set(DATA_ROLE, (byte) role.role().ordinal());
        // what the band reads (SPEC §21.1): the living module adds titles
        setData(CoreApi.CREATURE_TAG.get(), new dev.awaken.core.api.CreatureTag(Math.max(1, lv), tagKind(role.role()),
                role.role() == MobRole.Role.ELITE, dev.awaken.core.api.CreatureTag.of(this).title()));
        for (Forces.Tag t : tags) Forces.tag(this, t);
        syncForceTags();
        if (role.role() == MobRole.Role.WARDEN || role.role() == MobRole.Role.BOSS || role.role() == MobRole.Role.NIGHTMARE) {
            setPersistenceRequired();
            ensureBar();
        }
    }

    /** What kind of creature the band says this is, for its role (fauna and beasts say their own). */
    protected dev.awaken.core.api.CreatureTag.Kind tagKind(MobRole.Role role) {
        return switch (role) {
            case WARDEN -> dev.awaken.core.api.CreatureTag.Kind.WARDEN;
            case BOSS, BREAK, NIGHTMARE -> dev.awaken.core.api.CreatureTag.Kind.BOSS;
            case NONE, SPIRIT, RITUAL, ECHO -> dev.awaken.core.api.CreatureTag.Kind.NONE;
            default -> dev.awaken.core.api.CreatureTag.Kind.GATE;
        };
    }

    public MobRole role() {
        return getData(CoreApi.MOB_ROLE.get());
    }

    public int contentLevel() {
        return role().contentLevel();
    }

    // ---- flags ---------------------------------------------------------------------------------------------------

    public boolean flag(int f) {
        return (entityData.get(DATA_FLAGS) & f) != 0;
    }

    public void setFlag(int f, boolean on) {
        int v = entityData.get(DATA_FLAGS);
        int n = on ? v | f : v & ~f;
        if (n != v) entityData.set(DATA_FLAGS, n);
    }

    public int telegraphTicks() {
        return entityData.get(DATA_TELEGRAPH);
    }

    public void setTelegraph(int ticks, boolean heavy) {
        entityData.set(DATA_TELEGRAPH, Math.max(0, ticks));
        setFlag(F_HEAVY, heavy && ticks > 0);
        if (ticks <= 0) entityData.set(DATA_TELL, (byte) Strikes.Kind.NONE.ordinal());
    }

    /** Tells a blow of this class landing in {@code ticks} (SPEC §24): the wind-up, its look and its sound by class. */
    public void setTell(int ticks, Strikes.Kind kind) {
        setTell(ticks, kind, kind == Strikes.Kind.HEAVY || kind == Strikes.Kind.PERILOUS);
    }

    /** heavyLook: the bigger telegraph look (an ability's own heavy flag). */
    public void setTell(int ticks, Strikes.Kind kind, boolean heavyLook) {
        setTelegraph(ticks, heavyLook);
        entityData.set(DATA_TELL, (byte) (ticks > 0 ? kind : Strikes.Kind.NONE).ordinal());
    }

    /**
     * The tell's sound: its class's one voice on every creature (Arms), or our own wind-up for an ability nobody can
     * answer (a summon, a heal); {@code pitch} colours only that wind-up.
     */
    public void playTell(Strikes.Kind kind, boolean heavy, float pitch) {
        SoundEvent voice = dev.awaken.arms.Arms.tellSound(kind);
        if (voice != null) playLevelSound(voice, 1.0F, 1.0F);
        else playLevelSound(heavy ? MobSounds.TELEGRAPH_HEAVY.get() : MobSounds.TELEGRAPH.get(), heavy ? 1.4F : 0.9F, pitch);
    }

    /** The class of the blow being told, NONE when no blow is (both sides). */
    public Strikes.Kind tellKind() {
        Strikes.Kind[] k = Strikes.Kind.values();
        return k[Math.max(0, Math.min(k.length - 1, entityData.get(DATA_TELL)))];
    }

    /**
     * Its stance broken (Arms' posture, SPEC §24): the wind-up in progress ends and it stands still for {@code ticks}.
     * Bosses stagger too; that is the point of breaking them.
     */
    public boolean stagger(int ticks) {
        if (!isAlive() || flag(F_DECOY)) return false;
        if (abilities != null) abilities.interrupt();
        setTelegraph(0, false);
        staggerTicks = Math.max(staggerTicks, ticks);
        getNavigation().stop();
        return true;
    }

    public boolean staggered() {
        return staggerTicks > 0;
    }

    public MobRole.Role syncedRole() {
        int i = entityData.get(DATA_ROLE);
        MobRole.Role[] r = MobRole.Role.values();
        return r[Math.max(0, Math.min(r.length - 1, i))];
    }

    public int mimicId() {
        return entityData.get(DATA_MIMIC);
    }

    /** Mirrors the core Forces bitmask into synced flags so the client can draw them. */
    public void syncForceTags() {
        boolean haunted = Forces.has(this, Forces.Tag.HAUNTED) || def().has(Trait.HAUNTED);
        setFlag(F_HAUNTED, haunted);
        setFlag(F_FROSTED, Forces.has(this, Forces.Tag.FROSTED));
        setFlag(F_CHARGED, Forces.has(this, Forces.Tag.CHARGED));
        setFlag(F_VOLATILE, Forces.has(this, Forces.Tag.VOLATILE) || def().has(Trait.FUSE));
        if (Forces.has(this, Forces.Tag.ECHO) || def().has(Trait.RISES)) setFlag(F_ECHO, true);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (flag(F_FROSTED)) speed.addOrUpdateTransientModifier(new AttributeModifier(FROST_SLOW, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else speed.removeModifier(FROST_SLOW);
        }
    }

    /** Short-lived illusion: one hit pops it, it deals no damage, no loot, gone after a while. */
    public void makeDecoy(int ticks) {
        setFlag(F_DECOY, true);
        decoyTicks = ticks;
        setData(CoreApi.MOB_ROLE.get(), MobRole.NONE);
    }

    public boolean isDecoy() {
        return flag(F_DECOY);
    }

    // ---- ticking -----------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (def().has(Trait.NOCLIP)) noPhysics = true;
        super.tick();
        if (def().has(Trait.NOCLIP)) noPhysics = false;
        if (level().isClientSide()) clientTick();
    }

    private void clientTick() {
        idleAnim.startIfStopped(tickCount);
        oSquish = squish;
        if (onGround() && !wasOnGround) squish = -0.5F;
        else if (!onGround() && wasOnGround) squish = 1.0F;
        squish *= 0.6F;
        wasOnGround = onGround();
        if (tickCount % 4 == 0) {
            MobDef d = def();
            if (flag(F_FROSTED)) level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            if (flag(F_CHARGED)) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.05, 0);
            if (flag(F_HAUNTED) && random.nextInt(3) == 0) level().addParticle(ParticleTypes.SOUL, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.02, 0);
            if (flag(F_RISEN) && random.nextInt(2) == 0) level().addParticle(ParticleTypes.SCULK_SOUL, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.03, 0);
            MobRole.Role r = syncedRole();
            if ((r == MobRole.Role.ELITE || r == MobRole.Role.WARDEN) && random.nextInt(3) == 0) {
                level().addParticle(new DustParticleOptions(d.glow, 1.0F), getRandomX(0.7), getRandomY(), getRandomZ(0.7), 0, 0.02, 0);
            }
            int tele = telegraphTicks();
            if (tele > 0) {
                level().addParticle(new DustParticleOptions(d.glow, flag(F_HEAVY) ? 1.6F : 1.0F), getRandomX(0.8), getY() + getBbHeight() * 0.8, getRandomZ(0.8), 0, 0.08, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        MobDef d = def();
        if (tickCount % 20 == 0) {
            syncForceTags();
            if (d.has(Trait.MIMIC)) updateMimic(level);
        }
        if (bar != null && (tickCount & 3) == 0) bar.setProgress(getHealth() / getMaxHealth());
        if (bar != null && tickCount % 10 == 0) refreshBarPlayers();
        if (flag(F_DECOY) && --decoyTicks <= 0) {
            poof(level);
            return;
        }
        if (flag(F_FALLEN)) {
            if (--fallenTicks <= 0) rise(level);
            return;
        }
        if (flag(F_DORMANT)) {
            if (tickCount % 10 == 0 && level.getNearestPlayer(this, 6.0) instanceof Player p && !p.isSpectator() && !p.isCreative()) wake(level);
            return;
        }
        if (d.has(Trait.WATCHED_FREEZE) && tickCount % 5 == 0) watched = isWatched(level);
        if (d.has(Trait.FUSE)) tickFuse(level);
        if (d.model.move == Model.Move.HOP) tickHop();
        if (flag(F_HAUNTED)) tickPhasing(level);
        if (guardTicks > 0 && --guardTicks == 0) setFlag(F_GUARDING, false);
        if (staggerTicks > 0) staggerTicks--;
        if (frenzyTicks > 0 && --frenzyTicks == 0) endFrenzy();
        if (auraTicks > 0) tickAura(level);
        if (fleeTicks > 0) fleeTicks--;
        if (heardTicks > 0 && --heardTicks == 0) heardPos = null;
        if (d.has(Trait.LISTENER) && getTarget() instanceof Player p) {
            // Blind hunters lose you when you go quiet.
            if (MobGoals.HearTarget.noisy(p)) quietTicks = 0;
            else if (++quietTicks > 60) { setTarget(null); quietTicks = 0; }
        }
        if (abilities == null) abilities = new AbilityRunner(this);
        abilities.tick(level);
    }

    private void updateMimic(ServerLevel level) {
        LivingEntity t = getTarget();
        Player p = t instanceof Player tp ? tp : level.getNearestPlayer(this, 32.0);
        entityData.set(DATA_MIMIC, p == null ? -1 : p.getId());
    }

    private boolean isWatched(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(this) > 32 * 32) continue;
            Vec3 view = p.getViewVector(1.0F);
            Vec3 to = getEyePosition().subtract(p.getEyePosition());
            double len = to.length();
            if (len < 1.0E-3) return true;
            if (view.dot(to) / len > 0.86 && p.hasLineOfSight(this)) return true;
        }
        return false;
    }

    public boolean isWatchedNow() {
        return watched;
    }

    private void tickFuse(ServerLevel level) {
        LivingEntity t = getTarget();
        if (fuseTicks > 0) {
            fuseTicks++;
            setTelegraph(30 - fuseTicks, true);
            if (fuseTicks >= 30) {
                fuseTicks = 0;
                setFlag(F_FUSING, false);
                explode(level, def().blast);
                if (def().has(Trait.DEATH_CLOUD)) Hazards.cloud(level, this, position(), 3.0F, 120, 0.3F, BoltKind.SPORE, new MobEffectInstance(MobEffects.POISON, 80, 0));
                discard();
            } else if (t == null || distanceToSqr(t) > 7 * 7) {
                fuseTicks = 0;
                setFlag(F_FUSING, false);
                setTelegraph(0, false);
            }
        } else if (t != null && distanceToSqr(t) < 2.6 * 2.6) {
            fuseTicks = 1;
            setFlag(F_FUSING, true);
            playSound(net.minecraft.sounds.SoundEvents.CREEPER_PRIMED, 1.0F, 0.6F);
        }
    }

    private void tickHop() {
        if (hopCooldown > 0) hopCooldown--;
        if (onGround() && hopCooldown <= 0 && !getNavigation().isDone()) {
            jumpFromGround();
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            setDeltaMovement(getDeltaMovement().add(look.x * 0.18, 0, look.z * 0.18));
            hopCooldown = 12 + random.nextInt(10);
        }
    }

    private void tickPhasing(ServerLevel level) {
        int cycle = (tickCount + getId() * 7) % 140;
        boolean phased = cycle >= 110;
        if (phased != flag(F_PHASED)) {
            setFlag(F_PHASED, phased);
            if (phased) level.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        }
    }

    void startGuard(int ticks) {
        guardTicks = ticks;
        setFlag(F_GUARDING, true);
    }

    void startFrenzy(int ticks) {
        frenzyTicks = ticks;
        AttributeInstance s = getAttribute(Attributes.MOVEMENT_SPEED);
        if (s != null) s.addOrUpdateTransientModifier(new AttributeModifier(FRENZY_SPEED, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        AttributeInstance a = getAttribute(Attributes.ATTACK_DAMAGE);
        if (a != null) a.addOrUpdateTransientModifier(new AttributeModifier(FRENZY_POWER, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void endFrenzy() {
        AttributeInstance s = getAttribute(Attributes.MOVEMENT_SPEED);
        if (s != null) s.removeModifier(FRENZY_SPEED);
        AttributeInstance a = getAttribute(Attributes.ATTACK_DAMAGE);
        if (a != null) a.removeModifier(FRENZY_POWER);
    }

    void startAura(int pulses, float radius, float damage, BoltKind bolt, @Nullable MobEffectInstance effect) {
        auraTicks = pulses * 20;
        auraPulse = 0;
        auraRadius = radius;
        auraDamage = damage;
        auraBolt = bolt;
        auraEffect = effect;
    }

    private void tickAura(ServerLevel level) {
        auraTicks--;
        if (tickCount % 4 == 0) Telegraph.ring(level, position().add(0, 0.2, 0), auraRadius, 14, def().glow, false);
        if (++auraPulse >= 20) {
            auraPulse = 0;
            for (LivingEntity e : Abilities.enemiesNear(level, this, position(), auraRadius)) {
                // an aura's pulse is the perilous blow it was told as
                dev.awaken.core.api.Strikes.deliver(this, dev.awaken.core.api.Strikes.Kind.PERILOUS, () -> {
                    Abilities.hurt(level, this, e, auraDamage, auraBolt, auraEffect);
                    return true;
                });
            }
        }
    }

    // ---- abilities a creature tells and lands in its own look (AbilityRunner keeps the choosing, timing and classes) ----

    /** Fills {@code marks} for an ability about to wind up (where it will land); false keeps the runner's own spots. */
    protected boolean abilityMarks(ServerLevel level, dev.awaken.mobs.def.Ability a, LivingEntity target, java.util.List<Vec3> marks) {
        return false;
    }

    /** Every tick of a wind-up ({@code left} of {@code total} ticks to go): true when the creature draws its own tell. */
    protected boolean abilityTell(ServerLevel level, dev.awaken.mobs.def.Ability a, int left, int total, Vec3 aim, java.util.List<Vec3> marks) {
        return false;
    }

    /** The blow itself (inside its Strikes class): true when the creature lands it its own way. */
    protected boolean abilityStrike(ServerLevel level, dev.awaken.mobs.def.Ability a, @Nullable LivingEntity target, Vec3 aim, java.util.List<Vec3> marks) {
        return false;
    }

    public boolean isHeld() {
        return flag(F_FALLEN) || flag(F_DORMANT) || flag(F_FUSING) || watched || staggerTicks > 0 || (abilities != null && abilities.holdsStill());
    }

    @Nullable AbilityRunner runner() {
        return abilities;
    }

    // ---- damage ------------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (flag(F_DECOY)) {
            poof(level);
            return true;
        }
        if (flag(F_FALLEN) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (flag(F_DORMANT)) wake(level);
        boolean spirit = source.is(Forces.SPIRIT);
        if (flag(F_PHASED) && !spirit && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 4, 0.3, 0.4, 0.3, 0.01);
            return false;
        }
        float amount = damage;
        if (flag(F_HAUNTED)) amount *= spirit ? 1.5F : 0.75F;
        Entity attacker = source.getEntity();
        if (flag(F_GUARDING) && attacker instanceof LivingEntity la && source.getDirectEntity() == attacker) {
            amount *= 0.2F;
            guardTicks = 0;
            setFlag(F_GUARDING, false);
            playSound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 12, 0.4, 0.4, 0.4, 0.2);
            // the counter off a raised guard: the guard was its tell, and it lands as a LIGHT blow (deflect it, block it)
            if (distanceToSqr(la) < 16) Strikes.deliver(this, Strikes.Kind.LIGHT, () -> doHurtTarget(level, la));
        }
        if (def().has(Trait.REFLECT) && source.getDirectEntity() instanceof Projectile p && p.getOwner() != this && random.nextFloat() < 0.6F) {
            p.setDeltaMovement(p.getDeltaMovement().scale(-1.0));
            p.setOwner(this);
            playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && bar != null) bar.setProgress(getHealth() / getMaxHealth());
        return hurt;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (flag(F_DECOY) || def().has(Trait.FUSE)) {
            swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            return false;
        }
        boolean hit = super.doHurtTarget(level, target);
        if (!hit || !(target instanceof LivingEntity lt)) return hit;
        MobDef d = def();
        if (d.has(Trait.IGNITE)) lt.igniteForSeconds(4.0F);
        if (d.has(Trait.POISON_TOUCH)) lt.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
        if (d.has(Trait.SLOW_TOUCH)) lt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), this);
        if (d.has(Trait.WITHER_TOUCH)) lt.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
        if (flag(F_FROSTED)) {
            lt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
            lt.setTicksFrozen(Math.min(lt.getTicksRequiredToFreeze() + 40, lt.getTicksFrozen() + 60));
        }
        if (flag(F_CHARGED)) {
            float extra = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.25F;
            lt.damageCooldownTime = 0;
            lt.hurtServer(level, damageSources().source(Forces.CHARGE, this), extra);
            lt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 4), this);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, lt.getX(), lt.getY() + lt.getBbHeight() * 0.5, lt.getZ(), 10, 0.3, 0.5, 0.3, 0.2);
        }
        if (d.has(Trait.STEALER) && lt instanceof ServerPlayer sp && stolen.isEmpty()) steal(sp);
        return hit;
    }

    private void steal(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(CoreApi.ESSENCE_SHARD.get())) {
                stolen = s.split(Math.min(s.getCount(), 1 + random.nextInt(3)));
                inv.setChanged();
                break;
            }
        }
        robbed = sp;
        fleeTicks = 200;
        setTarget(null);
        playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0F, 1.4F);
        sp.sendOverlayMessage(Component.translatable(stolen.isEmpty() ? "awaken.mobs.pickpocket.empty" : "awaken.mobs.pickpocket.stole"));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof AwakenMob) && dev.awaken.core.api.GateHooks.mayTarget(this, target) && super.canAttack(target);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        return other instanceof AwakenMob || super.considersEntityAsAlly(other);
    }

    // ---- death, rising, volatility -------------------------------------------------------------------------------

    private boolean canRise() {
        return flag(F_ECHO) && !flag(F_RISEN) && !flag(F_DECOY) && !Forces.hasRisen(this);
    }

    @Override
    public void die(DamageSource source) {
        if (!isRemoved() && !dead && level() instanceof ServerLevel sl && canRise()) {
            // Echoing: fall, then get up once at half health. No death event, no loot until the real death.
            setHealth(1.0F);
            setFlag(F_FALLEN, true);
            fallenTicks = 50;
            setTarget(null);
            getNavigation().stop();
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.5, getZ(), 12, 0.4, 0.3, 0.4, 0.02);
            playSound(MobSounds.RISE.get(), 1.0F, 0.7F);
            return;
        }
        super.die(source);
        if (dead && level() instanceof ServerLevel sl) {
            onDied(sl, source);
            if (bar != null) bar.removeAllPlayers();
        }
    }

    /** Server, once, after a real death. */
    protected void onDied(ServerLevel level, DamageSource source) {
        MobDef d = def();
        if (d.has(Trait.DEATH_CLOUD)) Hazards.cloud(level, this, position(), 2.5F, 100, 0.25F, BoltKind.SPORE, new MobEffectInstance(MobEffects.POISON, 60, 0));
        if (d.has(Trait.DEATH_SHARDS)) Abilities.nova(level, this, 8, d.theme.bolt, 0.6F);
        if (d.has(Trait.SPLITS) && !flag(F_SPLIT_CHILD)) split(level);
    }

    private void split(ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            AwakenMob child = (AwakenMob) getType().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (child == null) continue;
            child.snapTo(getX() + (i == 0 ? -0.6 : 0.6), getY() + 0.2, getZ(), getYRot(), 0);
            child.setFlag(F_SPLIT_CHILD, true);
            MobRole r = role();
            child.configure(r.contentLevel(), new MobRole(MobRole.Role.NORMAL, r.contentLevel(), r.theme(), java.util.Optional.empty(), ""), Set.of());
            AttributeInstance sc = child.getAttribute(Attributes.SCALE);
            if (sc != null) sc.addOrReplacePermanentModifier(new AttributeModifier(Awaken.id("mobs_split"), -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            AttributeInstance hp = child.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.addOrReplacePermanentModifier(new AttributeModifier(Awaken.id("mobs_split_health"), -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            child.setHealth(child.getMaxHealth());
            child.setPersistenceRequired();
            level.addFreshEntity(child);
        }
    }

    private void rise(ServerLevel level) {
        setFlag(F_FALLEN, false);
        setFlag(F_RISEN, true);
        Forces.markRisen(this);
        setHealth(getMaxHealth() * 0.5F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.0, getZ(), 16, 0.4, 0.6, 0.4, 0.05);
        playSound(MobSounds.RISE.get(), 1.2F, 1.1F);
    }

    /** A dormant creature wakes (a player near, or struck). Overridable: the seep heart wakes with a moment of its own. */
    protected void wake(ServerLevel level) {
        if (!flag(F_DORMANT)) return;
        setFlag(F_DORMANT, false);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.6, getZ(), 20, 0.5, 0.6, 0.5, 0.1);
        playSound(MobSounds.TELEGRAPH_HEAVY.get(), 1.0F, 1.4F);
    }

    void poof(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.5, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        playSound(net.minecraft.sounds.SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 1.4F);
        discard();
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (!exploded && deathTime == 10 && flag(F_VOLATILE) && level() instanceof ServerLevel sl) {
            exploded = true;
            float power = def().blast * (role().role() == MobRole.Role.ELITE ? 1.3F : 1.0F);
            explode(sl, power);
        }
    }

    protected void explode(ServerLevel level, float power) {
        level.explode(this, damageSources().explosion(this, this), null, getX(), getY() + getBbHeight() * 0.4, getZ(), power, false, Level.ExplosionInteraction.NONE);
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return !flag(F_DECOY) && super.shouldDropLoot(level);
    }

    @Override
    public boolean shouldDropExperience() {
        return !flag(F_DECOY) && super.shouldDropExperience();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (!stolen.isEmpty()) {
            spawnAtLocation(level, stolen);
            stolen = ItemStack.EMPTY;
        }
        if (killedByPlayer) Mobs.eliteCoreDrop(level, this, source);
    }

    // ---- bars ----------------------------------------------------------------------------------------------------

    private Component barName() {
        return syncedRole() == MobRole.Role.WARDEN ? getDisplayName().copy().append(" · Warden") : getDisplayName();
    }

    protected void ensureBar() {
        if (bar != null || level().isClientSide()) return;
        bar = new ServerBossEvent(Mth.createInsecureUUID(random), barName(), def().barColor,
                role().role() == MobRole.Role.BOSS ? def().tier.overlay() : BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(getHealth() / getMaxHealth());
        if (role().role() == MobRole.Role.BOSS) bar.setDarkenScreen(def().tier == Tier.FINAL || def().tier == Tier.SOVEREIGN);
        barVisible(true);
    }

    protected void barVisible(boolean visible) {
        if (bar != null) bar.setVisible(visible);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bar == null && (syncedRole() == MobRole.Role.WARDEN || syncedRole() == MobRole.Role.BOSS || syncedRole() == MobRole.Role.NIGHTMARE)) ensureBar();
        watchers.add(player);
        if (bar != null && sameStorey(player)) bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        watchers.remove(player);
        if (bar != null) bar.removePlayer(player);
    }

    private boolean sameStorey(ServerPlayer p) {
        return Math.abs(p.getY() - getY()) <= BAR_STOREY && dev.awaken.core.api.GateHooks.showsBossBar(this, p);
    }

    private void refreshBarPlayers() {
        for (ServerPlayer p : watchers) {
            boolean near = sameStorey(p), has = bar.getPlayers().contains(p);
            if (near && !has) bar.addPlayer(p);
            else if (!near && has) bar.removePlayer(p);
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (bar != null) bar.setName(barName());
    }

    public @Nullable ServerBossEvent bar() {
        return bar;
    }

    // ---- misc behaviour ----------------------------------------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !def().tier.isBoss() && def().tier != Tier.WARDEN && super.removeWhenFarAway(distSqr);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return !def().flies() && super.causeFallDamage(fallDistance, damageModifier, damageSource);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (!def().flies()) super.checkFallDamage(ya, onGround, onState, pos);
    }

    @Override
    public boolean fireImmune() {
        return def().has(Trait.FIRE_IMMUNE) || super.fireImmune();
    }

    @Override
    public boolean isPushable() {
        return !def().tier.isBoss() && super.isPushable();
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return !def().tier.isBoss() && super.canUsePortal(ignorePassenger);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return flag(F_DORMANT) || flag(F_FALLEN) ? null : def().model.voice.ambient;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return def().model.voice.hurt;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return def().model.voice.death;
    }

    @Override
    public float getVoicePitch() {
        float s = Math.max(0.35F, getScale());
        return Mth.clamp((float) (1.0 / Math.sqrt(s)), 0.5F, 1.6F) * (0.95F + random.nextFloat() * 0.1F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return def().tier.isBoss() ? 160 : 240;
    }

    public void playLevelSound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---- save ----------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("awaken_flags", entityData.get(DATA_FLAGS) & SAVED_FLAGS);
        out.putInt("awaken_decoy", decoyTicks);
        if (!stolen.isEmpty()) out.store("awaken_stolen", ItemStack.CODEC, stolen);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        int saved = in.getIntOr("awaken_flags", entityData.get(DATA_FLAGS) & SAVED_FLAGS);
        entityData.set(DATA_FLAGS, (entityData.get(DATA_FLAGS) & ~SAVED_FLAGS) | saved);
        decoyTicks = in.getIntOr("awaken_decoy", 0);
        stolen = in.read("awaken_stolen", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        entityData.set(DATA_ROLE, (byte) role().role().ordinal());
        if (bar != null) bar.setName(barName());
    }

    // ---- client look ---------------------------------------------------------------------------------------------

    /** Fills and returns this entity's reusable look (client render thread). */
    public AwLook look(float partialTicks) {
        AwLook l = look;
        if (l == null) look = l = new AwLook();
        MobDef d = def();
        int flags = entityData.get(DATA_FLAGS);
        int tint = d.tint;
        if ((flags & F_FROSTED) != 0) tint = mix(tint, 0xBFE8FF, 0.45F);
        if ((flags & F_CHARGED) != 0) tint = mix(tint, 0xE6F4FF, 0.3F);
        if ((flags & F_RISEN) != 0) tint = mix(tint, 0x9AA8D0, 0.35F);
        boolean haunted = (flags & F_HAUNTED) != 0 || (flags & F_DECOY) != 0;
        float alpha = 1.0F;
        if (haunted) {
            alpha = (flags & F_PHASED) != 0 ? 0.25F + 0.1F * Mth.sin((tickCount + partialTicks) * 0.8F) : 0.62F;
            tint = mix(tint, 0xB8C4FF, 0.35F);
        }
        l.translucent = haunted;
        l.tint = ((int) (alpha * 255) << 24) | (tint & 0xFFFFFF);
        int tele = entityData.get(DATA_TELEGRAPH);
        l.telegraph = tele > 0 ? Math.min(1.0F, tele / 20.0F) : 0.0F;
        float pulse = 0.5F + 0.5F * Mth.sin((tickCount + partialTicks) * (tele > 0 ? 0.9F : 0.12F));
        MobRole.Role r = syncedRole();
        float base = r == MobRole.Role.BOSS || r == MobRole.Role.WARDEN ? 0.8F : r == MobRole.Role.ELITE ? 0.7F : 0.45F;
        l.glow = tele > 0 ? 0.7F + 0.3F * pulse : (flags & F_DORMANT) != 0 ? 0.05F : base + 0.2F * pulse;
        l.flash = ((flags & F_VOLATILE) != 0 && deathTime > 0) || (flags & F_FUSING) != 0;
        l.swell = (flags & F_FUSING) != 0 ? 1.0F - Math.min(1.0F, tele / 30.0F) : 0.0F;
        l.mimic = entityData.get(DATA_MIMIC);
        l.squish = Mth.lerp(partialTicks, oSquish, squish);
        l.upsideDown = d.has(Trait.UPSIDE_DOWN);
        l.fallen = (flags & F_FALLEN) != 0;
        l.hurt = hurtTime > 0 && hurtDuration > 0 ? Math.max(0.0F, (hurtTime - partialTicks) / hurtDuration) : 0.0F;
        l.aggressive = isAggressive();
        return l;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }
}
