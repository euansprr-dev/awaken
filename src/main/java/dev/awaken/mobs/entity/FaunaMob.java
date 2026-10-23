package dev.awaken.mobs.entity;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.FaunaTalent;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Model;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Base of the Awakened layer's overworld creatures (plan v2 section 4.2). Same stats, abilities, scaling, loot and
 * renderers as every AWAKEN creature, but a Minecraft animal's manners: ambient ones are never hostile and never keep
 * you from sleeping, neutral ones only fight back and calm down, night hunters keep vanilla's light rule (a player
 * standing in torchlight or daylight is safe from them) and melt away at dawn, and golems leave the peaceful ones
 * alone. Each creature's talent (moths dowse for ore, sprites tend crops...) is a goal that looks around a few times
 * a minute at most; nothing here scans entities every tick.
 */
public class FaunaMob extends AwakenMob {
    /** Pose bits synced to the client ({@link AwLook#fauna}). */
    public static final int P_AIR = 1, P_STALK = 1 << 1, P_GRAZE = 1 << 2, P_ANGRY = 1 << 3, P_BURROW = 1 << 4, P_REST = 1 << 5, P_GUARD = 1 << 6,
            P_ALERT = 1 << 7, P_SPRINT = 1 << 8, P_PUFF = 1 << 9;
    /** Night hunters leave alone a player standing in block light this bright, or under an open daytime sky. */
    public static final int SAFE_LIGHT = 8;
    /** Ticks a scarab takes to dig itself in. */
    static final int BURROW_TICKS = 30;

    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ORES = net.neoforged.neoforge.common.Tags.BlockItems.ORES.block();
    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(FaunaMob.class, EntityDataSerializers.INT);

    // Server state. No field initialisers: registerGoals runs inside the super constructor.
    int scareTicks;
    @Nullable Vec3 scareFrom;
    int graze;
    boolean stalking;
    int daylight;
    /** The talent's spot: the ore a moth circles, the crop a sprite tends, the campfire finches warm at. */
    @Nullable BlockPos lure;
    int talentCooldown, talentTries;
    int burrowTicks;
    /** P2 talents: a marmot sitting up to whistle, an owl's stare (alert); a puffcap's spore puff (ticks left). */
    int alertTicks, puffTicks;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    // client
    private float burrowAnim;
    /** Frog bodies are animated by keyframes: hops, croaks and the tongue (client only). */
    public final net.minecraft.world.entity.AnimationState jumpAnim = new net.minecraft.world.entity.AnimationState();
    public final net.minecraft.world.entity.AnimationState croakAnim = new net.minecraft.world.entity.AnimationState();
    public final net.minecraft.world.entity.AnimationState tongueAnim = new net.minecraft.world.entity.AnimationState();
    private int croakTicks, tongueTicks;

    public FaunaMob(EntityType<? extends FaunaMob> type, Level level) {
        super(type, level);
    }

    public FaunaRole faunaRole() {
        FaunaRole r = def().fauna;
        return r == null ? FaunaRole.NEUTRAL : r;
    }

    public FaunaTalent talent() {
        return def().talent;
    }

    // ---- goals ---------------------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        MobDef d = def();
        FaunaRole role = d.fauna == null ? FaunaRole.NEUTRAL : d.fauna;
        boolean flies = d.flies();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MobGoals.Hold(this));
        if (d.talent == FaunaTalent.BURROW || d.talent == FaunaTalent.DIG) goalSelector.addGoal(1, new FaunaGoals.Burrow(this));
        goalSelector.addGoal(2, new FaunaGoals.Scatter(this));
        switch (role) {
            case HUNTER -> {
                goalSelector.addGoal(2, new FaunaGoals.Stalk(this));
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, false));
                goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
                targetSelector.addGoal(1, new HurtByTargetGoal(this));
                targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (t, l) -> inDarkness(t)));
            }
            case NEUTRAL, BEAST -> {
                goalSelector.addGoal(3, flies ? new MobGoals.FlyHunt(this) : new MeleeAttackGoal(this, 1.15, true));
                targetSelector.addGoal(1, new HurtByTargetGoal(this));
            }
            case AMBIENT -> { }
        }
        switch (d.talent) {
            case DOWSE -> goalSelector.addGoal(4, new FaunaGoals.Dowse(this));
            case TEND -> goalSelector.addGoal(4, new FaunaGoals.Tend(this));
            case FLOCK -> goalSelector.addGoal(4, new FaunaGoals.Flock(this));
            case GLIDE -> goalSelector.addGoal(5, new FaunaGoals.Glide(this));
            case GRAZE, SKITTISH, SPRINT -> goalSelector.addGoal(5, new FaunaGoals.Graze(this));
            default -> WildGoals.add(this, d.talent);
        }
        if (flies) {
            if (d.talent != FaunaTalent.GLIDE && d.talent != FaunaTalent.DOWSE && !WildGoals.ownsFlight(d.talent)) {
                goalSelector.addGoal(7, new MobGoals.FlyWander(this));
            }
        } else {
            goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, role == FaunaRole.AMBIENT ? 0.9 : 0.7));
            goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        }
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** For {@link WildGoals} (the goal selector is protected). */
    void goal(int priority, net.minecraft.world.entity.ai.goal.Goal goal) {
        goalSelector.addGoal(priority, goal);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_POSE, 0);
    }

    public int pose() {
        return entityData.get(DATA_POSE);
    }

    /** Dart frogs climb trunks and walls like spiders. */
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        if (def().talent == FaunaTalent.VENOM) return new net.minecraft.world.entity.ai.navigation.WallClimberNavigation(this, level);
        return super.createNavigation(level);
    }

    @Override
    public boolean onClimbable() {
        return (talent() == FaunaTalent.VENOM && horizontalCollision) || super.onClimbable();
    }

    /** A startled basilisk runs straight across water on its hind legs. */
    @Override
    public boolean canStandOnFluid(net.minecraft.world.level.material.FluidState fluid) {
        return talent() == FaunaTalent.SPRINT && scareTicks > 0 && fluid.is(net.minecraft.tags.FluidTags.WATER);
    }

    // ---- spawning ------------------------------------------------------------------------------------------------

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        if (getData(CoreApi.MOB_ROLE.get()).role() == MobRole.Role.NONE) {
            // Natural spawns, eggs and commands: match the nearest player rather than a gate rank.
            MobDef d = def();
            int lv = Math.max(0, FaunaHooks.server().contentLevel(level.getLevel(), blockPosition(), faunaRole()));
            configure(lv, new MobRole(d.tier.role, lv, d.theme.id, Optional.empty(), ""), Set.of());
        }
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level() instanceof ServerLevel) FaunaHooks.server().added(this);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level() instanceof ServerLevel) FaunaHooks.server().removed(this);
    }

    // ---- ticking -------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            burrowAnim = (pose() & P_BURROW) != 0 ? Math.min(1.0F, burrowAnim + 1.0F / BURROW_TICKS) : 0.0F;
            if (def().model == Model.FROG) animateFrog();
            if (def().model == Model.SNOW_WOLF && tickCount % 6 == 0 && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.6), getY() + 0.3, getRandomZ(0.6), 0, 0.01, 0);
            }
        }
    }

    /** Keyframe states for frog bodies: a hop while airborne, a croak now and then, the tongue as an attack winds up. */
    private void animateFrog() {
        if (!onGround() && !isInWater()) jumpAnim.startIfStopped(tickCount);
        else jumpAnim.stop();
        if (croakTicks > 0) {
            if (--croakTicks == 0) croakAnim.stop();
        } else if (onGround() && walkAnimation.speed() < 0.05F && random.nextInt(240) == 0) {
            croakTicks = 60;
            croakAnim.start(tickCount);
        }
        if (tongueTicks > 0) {
            if (--tongueTicks == 0) tongueAnim.stop();
        } else if (telegraphTicks() > 0 || (pose() & P_ALERT) != 0) {
            tongueTicks = 10;
            tongueAnim.start(tickCount);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Finches flutter down instead of dropping (the chicken trick).
        if (talent() == FaunaTalent.FLOCK && !onGround()) {
            Vec3 v = getDeltaMovement();
            if (v.y < 0) setDeltaMovement(v.x, v.y * 0.6, v.z);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (scareTicks > 0 && --scareTicks == 0) scareFrom = null;
        if (graze > 0) graze--;
        if (burrowTicks > 0) tickBurrow(level);
        if (alertTicks > 0) alertTicks--;
        if (puffTicks > 0) puffTicks--;
        int phase = (tickCount + getId()) % 10;
        if (phase == 0) {
            switch (faunaRole()) {
                case HUNTER -> tickHunter(level);
                case NEUTRAL -> tickTemper();
                default -> { }
            }
            noticeThreats(level);
        }
        if (talentCooldown > 0) talentCooldown--;
        int slot = tickCount + getId();
        if (talent() == FaunaTalent.BURROW) {
            if (burrowTicks == 0 && slot % 5 == 0) checkBurrow(level);
        } else if (slot % 40 == 5) {
            tickTalent(level);
        }
        if (talent() == FaunaTalent.DIG && burrowTicks == 0 && slot % 5 == 0) WildTalents.checkAlarm(this, level);
        updatePose();
    }

    /** Ambient and skittish creatures keep their distance from players who run at them (sneak to get close). */
    private void noticeThreats(ServerLevel level) {
        FaunaTalent t = talent();
        if (scareTicks > 0 || getTarget() != null) return;
        if (t == FaunaTalent.SPRINT || t == FaunaTalent.SPORE) {
            WildTalents.noticeThreats(this, level);
            return;
        }
        if (t != FaunaTalent.FLOCK && t != FaunaTalent.SKITTISH) return;
        Player p = level.getNearestPlayer(this, t == FaunaTalent.SKITTISH ? 12.0 : 7.0);
        if (p == null || p.isSpectator()) return;
        double r = t == FaunaTalent.SKITTISH ? (p.isSprinting() ? 12 : p.isShiftKeyDown() ? 3 : 7) : (p.isSprinting() ? 7 : p.isShiftKeyDown() ? 2 : 4.5);
        if (distanceToSqr(p) < r * r) scare(p.position(), t == FaunaTalent.SKITTISH ? 80 : 50);
    }

    public void scare(Vec3 from, int ticks) {
        scareFrom = from;
        scareTicks = Math.max(scareTicks, ticks);
    }

    /** Night hunters: hunt only in the dark, back off from light, help their pack, fade at dawn. */
    private void tickHunter(ServerLevel level) {
        LivingEntity t = getTarget();
        if (t instanceof Player p && !inDarkness(p)) {
            boolean provoked = getLastHurtByMob() == p && tickCount - getLastHurtByMobTimestamp() < 100;
            if (!provoked) {
                setTarget(null);
                scare(p.position(), 40);
                t = null;
            }
        }
        if (t != null && (tickCount + getId()) % 40 == 0) {
            final LivingEntity prey = t;
            List<FaunaMob> pack = level.getEntitiesOfClass(FaunaMob.class, getBoundingBox().inflate(12),
                    m -> m != this && m.getType() == getType() && m.getTarget() == null && m.isAlive());
            for (FaunaMob m : pack) m.setTarget(prey);
        }
        if (t == null && !isPersistenceRequired() && level.isBrightOutside() && level.canSeeSky(blockPosition().above())) {
            daylight += 10;
            if (daylight >= 200) fadeAway(level);
        } else {
            daylight = 0;
        }
    }

    /** Neutral creatures forgive: ten seconds without being hit, or a target that left, and they go back to grazing. */
    private void tickTemper() {
        LivingEntity t = getTarget();
        if (t == null) return;
        boolean recent = getLastHurtByMob() == t && tickCount - getLastHurtByMobTimestamp() < 200;
        boolean gone = !t.isAlive() || t.distanceToSqr(this) > 24 * 24 || (t instanceof Player p && (p.isCreative() || p.isSpectator()));
        if (gone || !recent) {
            setTarget(null);
            if (talent() == FaunaTalent.SKITTISH) scare(t.position(), 80);
        }
    }

    /** A night hunter caught by the sun slinks into the shadows. */
    void fadeAway(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.01);
        discard();
    }

    private void tickTalent(ServerLevel level) {
        switch (talent()) {
            case DOWSE -> {
                if (lure == null && talentTries < 8) {
                    talentTries++;
                    lure = findOre(level);
                }
                if (lure != null && getTarget() == null && distanceToSqr(Vec3.atCenterOf(lure)) < 36) {
                    level.sendParticles(ParticleTypes.GLOW, lure.getX() + 0.5, lure.getY() + 0.2, lure.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.0);
                }
            }
            case TEND -> {
                if (lure == null && talentCooldown <= 0) lure = findCrop(level);
            }
            case FLOCK -> {
                if (lure == null || !isCampfire(level, lure)) lure = findCampfire(level);
            }
            default -> WildTalents.tick(this, level);
        }
    }

    // ---- talents ---------------------------------------------------------------------------------------------------

    /** Samples a dozen blocks in the column below; ore within 26 blocks down becomes the moth's spot (surface above it). */
    private @Nullable BlockPos findOre(ServerLevel level) {
        int bx = getBlockX(), bz = getBlockZ();
        for (int i = 0; i < 12; i++) {
            int x = bx + random.nextInt(13) - 6, z = bz + random.nextInt(13) - 6;
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = ground - 2 - random.nextInt(26);
            probe.set(x, y, z);
            if (!level.hasChunkAt(probe)) continue;
            if (level.getBlockState(probe).is(ORES)) return new BlockPos(x, ground, z);
        }
        return null;
    }

    @Nullable BlockPos findCrop(ServerLevel level) {
        int bx = getBlockX(), bz = getBlockZ();
        boolean mushrooms = talent() == FaunaTalent.MYCEL;
        for (int i = 0; i < 20; i++) {
            int x = bx + random.nextInt(25) - 12, z = bz + random.nextInt(25) - 12;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int dy = 1; dy >= -1; dy--) {
                probe.set(x, top + dy, z);
                if (!level.hasChunkAt(probe)) break;
                BlockState s = level.getBlockState(probe);
                boolean wanted = mushrooms ? s.getBlock() instanceof net.minecraft.world.level.block.MushroomBlock : s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS);
                if (wanted && s.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b
                        && b.isValidBonemealTarget(level, probe, s, net.minecraft.world.level.block.BonemealSource.MOB)) {
                    return probe.immutable();
                }
            }
        }
        return null;
    }

    /** A sprite hovering over its crop: one growth stage, then a long rest. */
    boolean tend(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        talentCooldown = 600 + random.nextInt(300);
        if (!(s.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b)
                || !b.isValidBonemealTarget(level, pos, s, net.minecraft.world.level.block.BonemealSource.MOB)) return false;
        b.performBonemeal(level, random, pos, s, net.minecraft.world.level.block.BonemealSource.MOB);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.35, 0.3, 0.35, 0.0);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5F, 1.6F);
        return true;
    }

    private @Nullable BlockPos findCampfire(ServerLevel level) {
        int bx = getBlockX(), by = getBlockY(), bz = getBlockZ();
        for (int i = 0; i < 16; i++) {
            probe.set(bx + random.nextInt(25) - 12, by + random.nextInt(7) - 3, bz + random.nextInt(25) - 12);
            if (level.hasChunkAt(probe) && isCampfire(level, probe)) return probe.immutable();
        }
        return null;
    }

    private static boolean isCampfire(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.is(BlockTags.CAMPFIRES) && s.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT)
                && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT);
    }

    /** Scarabs dive into sand, dirt or clay when a player stomps close; a sneaking player can catch one. */
    private void checkBurrow(ServerLevel level) {
        Player p = level.getNearestPlayer(this, 4.5);
        if (p == null || p.isSpectator() || p.isShiftKeyDown() || !onGround()) return;
        BlockState below = level.getBlockState(blockPosition().below());
        if (below.is(BlockTags.SAND) || below.is(BlockTags.TERRACOTTA) || below.is(BlockTags.BADLANDS_TERRACOTTA) || below.is(BlockTags.DIRT)) {
            burrowTicks = 1;
            getNavigation().stop();
            playSound(below.getSoundType().getHitSound(), 0.8F, 1.3F);
        }
    }

    private void tickBurrow(ServerLevel level) {
        burrowTicks++;
        if (burrowTicks % 3 == 0) {
            BlockState below = level.getBlockState(blockPosition().below());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.1, getZ(), 4, 0.2, 0.05, 0.2, 0.05);
        }
        if (burrowTicks >= BURROW_TICKS) discard();
    }

    public boolean burrowing() {
        return burrowTicks > 0;
    }

    private void updatePose() {
        int p = 0;
        if (alertTicks > 0) p |= P_ALERT;
        if (puffTicks > 0) p |= P_PUFF;
        if (scareTicks > 0 && talent() == FaunaTalent.SPRINT && getDeltaMovement().horizontalDistanceSqr() > 0.004) p |= P_SPRINT;
        if (!onGround()) p |= P_AIR;
        if (stalking) p |= P_STALK;
        if (graze > 0) p |= level().isDarkOutside() && talent() == FaunaTalent.GRAZE ? P_REST : P_GRAZE;
        if (getTarget() != null) p |= P_ANGRY;
        if (burrowTicks > 0) p |= P_BURROW;
        if (flag(F_GUARDING)) p |= P_GUARD;
        if (p != entityData.get(DATA_POSE)) entityData.set(DATA_POSE, p);
    }

    // ---- the light rule ----------------------------------------------------------------------------------------------

    /**
     * Vanilla's promise kept for night hunters: a player in block light {@value #SAFE_LIGHT}+ (about six blocks from a
     * torch) or under an open daytime sky is safe. Darkness, caves and night are not.
     */
    public static boolean inDarkness(LivingEntity t) {
        Level l = t.level();
        BlockPos p = BlockPos.containing(t.getX(), t.getEyeY() - 0.5, t.getZ());
        if (l.getBrightness(LightLayer.BLOCK, p) >= SAFE_LIGHT) return false;
        return l.getBrightness(LightLayer.SKY, p) - l.getSkyDarken() < SAFE_LIGHT;
    }

    // ---- manners -------------------------------------------------------------------------------------------------

    @Override
    public SoundSource getSoundSource() {
        return faunaRole() == FaunaRole.HUNTER ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
    }

    /** Only night hunters keep you awake (vanilla's monster rule); a moth at the window doesn't. */
    @Override
    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return faunaRole() == FaunaRole.HUNTER || (faunaRole() == FaunaRole.BEAST && getTarget() == player);
    }

    /** Golems guard villages from night hunters only. */
    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        if (other instanceof AbstractGolem && faunaRole() != FaunaRole.HUNTER) return true;
        return super.considersEntityAsAlly(other);
    }

    /** Monsters despawn faster in light; only the night hunters among us should. */
    @Override
    protected void updateNoActionTime() {
        if (faunaRole() == FaunaRole.HUNTER) super.updateNoActionTime();
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (faunaRole() == FaunaRole.HUNTER) return super.getWalkTargetValue(pos, level);
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.ANIMALS_SPAWNABLE_ON) || below.is(BlockTags.SAND) || below.is(BlockTags.SNOW) ? 10.0F : 0.0F;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && talent() == FaunaTalent.VENOM) WildTalents.venom(this, source);
        if (hurt && talent() == FaunaTalent.SPORE) WildTalents.puff(this, level);
        if (hurt && faunaRole() == FaunaRole.AMBIENT) {
            Entity a = source.getEntity();
            scare(a != null ? a.position() : position().add(random.nextGaussian(), 0, random.nextGaussian()), 100);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (faunaRole() == FaunaRole.AMBIENT) return false;
        return super.doHurtTarget(level, target);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        if (burrowTicks > 0) return null;
        return def().voice().ambient;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return def().voice().hurt;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return def().voice().death;
    }

    @Override
    public int getAmbientSoundInterval() {
        return switch (faunaRole()) {
            case AMBIENT -> 420;
            case HUNTER -> 300;
            default -> 360;
        };
    }

    // ---- client look ---------------------------------------------------------------------------------------------

    @Override
    public AwLook look(float partialTicks) {
        AwLook l = super.look(partialTicks);
        int pose = pose();
        l.fauna = pose;
        l.faunaAnim = burrowAnim;
        FaunaRole role = faunaRole();
        float pulse = 0.5F + 0.5F * Mth.sin((tickCount + partialTicks) * 0.09F + getId());
        if (l.telegraph <= 0) l.glow = role == FaunaRole.AMBIENT ? 0.65F + 0.3F * pulse : (pose & P_ANGRY) != 0 ? 0.85F : 0.45F + 0.25F * pulse;
        if ((pose & (P_ALERT | P_PUFF)) != 0) l.glow = Math.max(l.glow, 0.95F); // an owl's stare, a bogwick's cure, a puff
        if (def().model == Model.SNOW_WOLF) {
            // Wisp hounds are half spirit.
            l.translucent = true;
            l.tint = (0xC8 << 24) | (l.tint & 0xFFFFFF);
        }
        if (role == FaunaRole.AMBIENT && !FaunaHooks.viewerAwakened()) {
            // Before awakening, only a shimmer at the edge of sight.
            l.translucent = true;
            l.tint = (0x58 << 24) | (l.tint & 0xFFFFFF);
            l.glow *= 0.55F;
        }
        return l;
    }

    // ---- helpers for goals and tests -------------------------------------------------------------------------------

    /** The ground height (no leaves) under a column, for fliers that keep low. */
    static int groundAt(Level level, double x, double z) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** The nearest player within {@code range} holding a light (a torch, lantern, glowstone...), for moths. */
    static @Nullable Player lightBearer(ServerLevel level, Entity near, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level.players()) {
            if (p.isSpectator()) continue;
            double d = p.distanceToSqr(near);
            if (d < bestD && (emitsLight(p.getMainHandItem()) || emitsLight(p.getOffhandItem()))) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    static boolean emitsLight(net.minecraft.world.item.ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock().defaultBlockState().getLightEmission() > 0;
    }

    static AABB around(Entity e, double r) {
        return e.getBoundingBox().inflate(r);
    }
}
