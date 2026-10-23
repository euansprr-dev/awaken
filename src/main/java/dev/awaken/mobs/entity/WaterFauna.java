package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.FaunaTalent;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A creature of the Tidewater (P3): it swims with vanilla's smooth swimming controls (the dolphin's), and fish and
 * jellies flop and suffocate out of water while the otter and the Tidesteed walk on land too. Each has its talent:
 * the Glimmerfin school (swim through it and your eyes open to the dark water), the Breathbell's air (a drowning
 * swimmer who touches one breathes again; its bell then refills for 20 seconds), the otter's fetch (it dives and
 * brings you what the river hides) and the Tidesteed's leaps from the waves (and it can be tamed: {@link WildMount}).
 */
public class WaterFauna extends WildMount {
    /** Ticks a Breathbell takes to refill its bell; air one touch gives (a breath is 15 ticks of air). */
    public static final int BELL_REFILL = 400, BELL_AIR = 150;
    /** Night vision a Glimmerfin school gives a swimmer who passes through it. */
    public static final int SHIMMER_TICKS = 300;

    // server (no field initialisers: registerGoals runs inside the super constructor)
    @Nullable WaterFauna leader;
    int bell;
    /** The otter's fetch: 0 idle (cooldown in fetchTicks), 1 diving, 2 rummaging, 3 bringing it, the find, for whom. */
    int fetchStage, fetchTicks, fetchCooldown;
    @Nullable BlockPos fetchAt;
    @Nullable Player fetchFor;
    ItemStack carried;

    public WaterFauna(EntityType<? extends WaterFauna> type, Level level) {
        super(type, level);
        boolean amphibious = amphibious();
        this.moveControl = Swim.moveControl(this, amphibious);
        this.lookControl = Swim.lookControl(this);
        Swim.malus(this, amphibious);
        this.carried = ItemStack.EMPTY;
    }

    public boolean amphibious() {
        return Fauna3Kinds.AMPHIBIOUS.contains(def().id);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return Swim.navigation(this, level, Fauna3Kinds.AMPHIBIOUS.contains(def().id));
    }

    @Override
    protected void registerGoals() {
        MobDef d = def();
        FaunaRole role = faunaRole();
        goal(1, new MobGoals.Hold(this));
        if (role == FaunaRole.AMBIENT) goal(2, new Fauna3Goals.SwimFlee(this));
        if (role == FaunaRole.NEUTRAL) {
            goal(2, new Fauna3Goals.SwimHunt(this));
            goal(3, new MeleeAttackGoal(this, 1.2, true));
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
        }
        if (Fauna3Kinds.mount(d.id) != null) {
            goal(3, new Fauna3Goals.Rest(this));
            goal(4, new Fauna3Goals.FollowOwner(this));
        }
        switch (d.talent) {
            case SHOAL -> goal(4, new Fauna3Goals.Shoal(this));
            case AIRBELL -> goal(5, new Fauna3Goals.Drift(this));
            case FETCH -> goal(4, new Fauna3Goals.Fetch(this));
            default -> { }
        }
        if (Fauna3Kinds.AMPHIBIOUS.contains(d.id)) goal(7, new RandomStrollGoal(this, 1.0, 80));
        else if (d.talent != FaunaTalent.AIRBELL) goal(7, new RandomSwimmingGoal(this, 1.0, d.talent == FaunaTalent.SHOAL ? 30 : 50));
        goal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goal(9, new RandomLookAroundGoal(this));
    }

    // ---- water manners -------------------------------------------------------------------------------------------------

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) return 10.0F;
        return amphibious() ? 2.0F : 0.0F;
    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        Swim.travelInWater(this, input);
    }

    @Override
    public void baseTick() {
        int air = getAirSupply();
        super.baseTick();
        if (!amphibious() && level() instanceof ServerLevel level) Swim.air(this, level, air);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!amphibious()) Swim.flop(this);
    }

    @Override
    public int getMaxAirSupply() {
        return Swim.AIR;
    }

    @Override
    public int wildState() {
        int s = super.wildState();
        if (!isInWater() && !amphibious()) s |= S_BEACHED;
        return s;
    }

    @Override
    protected SoundEvent getSwimSound() {
        SoundEvent s = dev.awaken.mobs.MobSounds.fauna(def().id, "step");
        return s != null ? s : super.getSwimSound();
    }

    // ---- talents -------------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (bell > 0 && --bell == 0) setWild(S_DEFLATE, false);
        if (fetchCooldown > 0) fetchCooldown--;
        int slot = tickCount + getId();
        switch (talent()) {
            case SHOAL -> {
                if (slot % 10 == 0) shimmer(level);
                if (slot % 10 == 5) noticeSwimmers(level);
            }
            case AIRBELL -> {
                if (slot % 5 == 0) airbell(level);
            }
            case FETCH -> {
                if (fetchStage == 0 && fetchCooldown <= 0 && slot % 40 == 0) startFetch(level);
            }
            case TIDE -> {
                if (slot % 40 == 0 && !isVehicle()) leapWaves();
            }
            default -> { }
        }
    }

    /** Followers of this fish (for the school's size). */
    int followers() {
        if (!(level() instanceof ServerLevel)) return 0;
        return level().getEntitiesOfClass(WaterFauna.class, getBoundingBox().inflate(10), o -> o.leader == this).size();
    }

    /** A school passing a swimmer: their eyes open to the dark water for a while (not stacked, not renewed early). */
    boolean shimmer(ServerLevel level) {
        Player p = level.getNearestPlayer(this, 2.5);
        if (p == null || p.isSpectator() || !p.isInWater() || p.hasEffect(MobEffects.NIGHT_VISION)) return false;
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SHIMMER_TICKS, 0, true, true, true));
        level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getEyeY(), p.getZ(), 10, 0.5, 0.4, 0.5, 0.02);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.8F);
        return true;
    }

    /** A swimmer sprinting through the water scatters the school (approach slowly to swim with it). */
    private void noticeSwimmers(ServerLevel level) {
        if (scareTicks > 0) return;
        Player p = level.getNearestPlayer(this, 4.0);
        if (p != null && !p.isSpectator() && (p.isSprinting() || p.isSwimming() && p.getDeltaMovement().lengthSqr() > 0.04)) scare(p.position(), 40);
    }

    /** A drowning swimmer touching the bell breathes again; the bell empties and refills in 20 seconds. */
    boolean airbell(ServerLevel level) {
        if (bell > 0) return false;
        List<Player> near = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(1.2), p -> !p.isSpectator() && p.isUnderWater());
        for (Player p : near) {
            if (p.getAirSupply() >= p.getMaxAirSupply() - 30) continue;
            p.setAirSupply(Math.min(p.getMaxAirSupply(), Math.max(0, p.getAirSupply()) + BELL_AIR));
            bell = BELL_REFILL;
            setWild(S_DEFLATE, true);
            level.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getEyeY(), p.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + getBbHeight() * 0.6, getZ(), 12, 0.3, 0.2, 0.3, 0.1);
            playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0F, 1.2F);
            return true;
        }
        return false;
    }

    /** The bell's state (tests). */
    public boolean bellEmpty() {
        return bell > 0;
    }

    /** At the surface and moving, a Tidesteed now and then leaps clear of the waves. */
    private void leapWaves() {
        if (!isInWater() || isUnderWater() || getDeltaMovement().horizontalDistanceSqr() < 0.004 || random.nextInt(4) != 0) return;
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * 1.8, 0.62, v.z * 1.8);
        needsSync = true;
        playSound(SoundEvents.DOLPHIN_JUMP, 0.8F, 0.85F);
    }

    // ---- the otter's fetch ---------------------------------------------------------------------------------------------

    /** Starts a dive when a player is near (within 16 blocks) and the otter is in water. Returns whether it did. */
    boolean startFetch(ServerLevel level) {
        if (!isInWater() || getTarget() != null) return false;
        Player p = level.getNearestPlayer(this, 16.0);
        if (p == null || p.isSpectator()) return false;
        BlockPos.MutableBlockPos m = blockPosition().mutable();
        int i = 0;
        while (i++ < 14 && level.getFluidState(m).is(FluidTags.WATER)) m.move(0, -1, 0);
        fetchAt = m.above().immutable();
        fetchFor = p;
        fetchStage = 1;
        fetchTicks = 0;
        return true;
    }

    /** One tick of the fetch (from its goal). */
    void fetchStep() {
        if (!(level() instanceof ServerLevel level)) return;
        fetchTicks++;
        switch (fetchStage) {
            case 1 -> { // dive to the bottom
                if (fetchAt == null || fetchTicks > 120 || fetchAt.distToCenterSqr(position()) < 2.0 * 2.0) {
                    fetchStage = 2;
                    fetchTicks = 0;
                    getNavigation().stop();
                } else if (fetchTicks % 10 == 1) {
                    getMoveControl().setWantedPosition(fetchAt.getX() + 0.5, fetchAt.getY() + 0.2, fetchAt.getZ() + 0.5, 1.3);
                }
            }
            case 2 -> { // rummage on the bottom
                if (fetchTicks % 6 == 0) {
                    BlockState under = level.getBlockState(blockPosition().below());
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX(), getY(), getZ(), 4, 0.3, 0.1, 0.3, 0.05);
                    level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 0.4, getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                }
                if (fetchTicks >= 50) {
                    carried = find();
                    setWild(S_CARRY, true);
                    fetchStage = 3;
                    fetchTicks = 0;
                }
            }
            case 3 -> { // bring it to the player
                Player p = fetchFor;
                if (p == null || !p.isAlive() || p.level() != level || fetchTicks > 400 || p.distanceToSqr(this) > 40 * 40) {
                    dropFind(level, null);
                    return;
                }
                if (p.distanceToSqr(this) < 2.6 * 2.6) {
                    dropFind(level, p);
                    return;
                }
                if (fetchTicks % 10 == 1) {
                    if (isInWater()) getMoveControl().setWantedPosition(p.getX(), Math.min(p.getY(), getY() + 2.0), p.getZ(), 1.3);
                    getNavigation().moveTo(p, 1.2);
                }
            }
            default -> fetchStage = 0;
        }
    }

    private void dropFind(ServerLevel level, @Nullable Player to) {
        if (!carried.isEmpty()) {
            ItemEntity e = new ItemEntity(level, getX(), getY() + getBbHeight() * 0.6, getZ(), carried);
            if (to != null) {
                Vec3 d = to.position().subtract(position()).normalize().scale(0.25);
                e.setDeltaMovement(d.x, 0.25, d.z);
            }
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
            playSound(SoundEvents.ITEM_PICKUP, 0.6F, 0.7F);
        }
        carried = ItemStack.EMPTY;
        setWild(S_CARRY, false);
        fetchStage = 0;
        fetchFor = null;
        fetchCooldown = 900 + random.nextInt(900);
    }

    /** The fetch was cut short (it got angry): what it carried drops where it is. */
    void fetchAbort() {
        if (level() instanceof ServerLevel level) dropFind(level, null);
    }

    /** What the river hides: mostly clay and fish, sometimes a nugget, rarely a nautilus shell or a Tide Scale. */
    ItemStack find() {
        int r = random.nextInt(100);
        Item item;
        int count = 1;
        if (r < 24) {
            item = Items.CLAY_BALL;
            count = 1 + random.nextInt(3);
        } else if (r < 38) item = Items.COD;
        else if (r < 50) item = Items.SALMON;
        else if (r < 58) item = Items.LILY_PAD;
        else if (r < 66) item = Items.INK_SAC;
        else if (r < 76) {
            item = Items.IRON_NUGGET;
            count = 1 + random.nextInt(3);
        } else if (r < 85) {
            item = Items.GOLD_NUGGET;
            count = 1 + random.nextInt(2);
        } else if (r < 90) item = Items.GLOW_INK_SAC;
        else if (r < 97) item = BuiltInRegistries.ITEM.getValue(Awaken.id("tide_scale"));
        else item = Items.NAUTILUS_SHELL;
        if (item == Items.AIR) item = Items.CLAY_BALL;
        return new ItemStack(item, count);
    }

    /** Tests: the talents, called directly. */
    public boolean shimmerForTest(ServerLevel level) {
        return shimmer(level);
    }

    public boolean airbellForTest(ServerLevel level) {
        return airbell(level);
    }

    public boolean startFetchForTest(ServerLevel level) {
        return startFetch(level);
    }

    public void fetchStepForTest() {
        fetchStep();
    }

    /** Tests and commands: the otter's state. */
    public int fetchStage() {
        return fetchStage;
    }

    public ItemStack carried() {
        return carried;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!carried.isEmpty() && level() instanceof ServerLevel level) dropFind(level, null);
        super.die(source);
    }

    /** Whistle and tests: a spot of water near {@code at} for a swimmer to come to. */
    public static @Nullable BlockPos waterNear(Level level, BlockPos at, int r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                    for (int dy = -2; dy <= 1; dy++) {
                        m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                        if (level.getFluidState(m).is(FluidTags.WATER) && level.getFluidState(m.above()).is(FluidTags.WATER)) return m.immutable();
                    }
                }
            }
        }
        return null;
    }
}
