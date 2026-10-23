package dev.awaken.mobs.entity;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.mobs.def.FaunaTalent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A creature of the Hollows (P3). The Chime Cricket finds ore through the rock and sings beside it, faster the closer
 * you come; the Wickling ripens glow berries on the cave vines it walks under and follows glow berries in your hand;
 * the Drip Lurker hangs from the roof and drops on a player who walks beneath it in the dark; the Hushling hunts by
 * sound and loses you when you go quiet. Both hunters keep the light rule (a player in torchlight is never dropped on,
 * never hunted). Ore and vine searches are a dozen block probes every two seconds.
 */
public class CaveFauna extends FaunaMob implements WildPose {
    private static final EntityDataAccessor<Integer> DATA_CAVE = SynchedEntityData.defineId(CaveFauna.class, EntityDataSerializers.INT);
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ORES = net.neoforged.neoforge.common.Tags.BlockItems.ORES.block();

    // server (no field initialisers: registerGoals runs inside the super constructor)
    private boolean hanging, dropping;
    private int chirpIn, chirpPose, ripenPose, oreTries;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    public CaveFauna(EntityType<? extends CaveFauna> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_CAVE, 0);
    }

    @Override
    protected void registerGoals() {
        FaunaTalent t = def().talent;
        if (t == FaunaTalent.HUSH) {
            goal(0, new FloatGoal(this));
            goal(1, new MobGoals.Hold(this));
            goal(2, new FaunaGoals.Scatter(this));
            goal(3, new MeleeAttackGoal(this, 1.25, false));
            goal(4, new MobGoals.SeekSound(this));
            goal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
            goal(9, new RandomLookAroundGoal(this));
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
            targetSelector.addGoal(2, new Fauna3Goals.HearInDark(this));
            return;
        }
        super.registerGoals();
        switch (t) {
            case CHIME -> goal(4, new Fauna3Goals.Chime(this));
            case RIPEN -> {
                goal(3, new TemptGoal(this, 1.1, s -> s.is(Items.GLOW_BERRIES), false));
                goal(4, new Fauna3Goals.Ripen(this));
            }
            case CLING -> goal(1, new Fauna3Goals.Cling(this));
            default -> { }
        }
    }

    // ---- poses ---------------------------------------------------------------------------------------------------------

    private void pose(int bit, boolean on) {
        int v = entityData.get(DATA_CAVE);
        int n = on ? v | bit : v & ~bit;
        if (n != v) entityData.set(DATA_CAVE, n);
    }

    @Override
    public int wildState() {
        return entityData.get(DATA_CAVE);
    }

    // ---- ticking -------------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        int slot = tickCount + getId();
        switch (talent()) {
            case CHIME -> {
                if (slot % 40 == 7) seekOre(level);
                if (slot % 5 == 0) chime(level);
            }
            case RIPEN -> {
                if (slot % 40 == 7 && lure == null && talentCooldown <= 0) lure = findVine(level);
            }
            case CLING -> {
                if (dropping && onGround()) land(level);
            }
            case HUSH -> pose(S_LISTEN, heardTicks > 0);
            default -> { }
        }
        if (chirpPose > 0 && --chirpPose == 0) pose(S_CHIRP, false);
        if (ripenPose > 0 && --ripenPose == 0) pose(S_RIPEN, false);
    }

    @Override
    public void travel(Vec3 input) {
        if (hanging) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    // ---- the chime cricket ---------------------------------------------------------------------------------------------

    /**
     * Sweeps the rock within 8 blocks for ore, one 17 x 17 layer a call (11 calls a sweep, 5 below to 5 above); the
     * nearest ore of the layer is its spot (forgotten when mined). A sweep that finds nothing rests 15 calls. Random
     * probes missed a lone ore vein too often.
     */
    void seekOre(ServerLevel level) {
        if (lure != null && !level.getBlockState(lure).is(ORES)) lure = null;
        if (lure != null) return;
        if (oreTries < 0) {
            oreTries++;
            return;
        }
        int dy = oreTries - 5, bx = getBlockX(), by = getBlockY() + dy, bz = getBlockZ();
        int best = Integer.MAX_VALUE;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                probe.set(bx + x, by, bz + z);
                if (!level.hasChunkAt(probe) || !level.getBlockState(probe).is(ORES)) continue;
                int d = x * x + z * z;
                if (d < best) {
                    best = d;
                    lure = probe.immutable();
                }
            }
        }
        oreTries = lure != null ? 0 : oreTries >= 10 ? -15 : oreTries + 1;
    }

    /** Beside its ore it sings when a player is within 16 blocks, more often the closer they come. */
    private void chime(ServerLevel level) {
        if (lure == null || lure.distToCenterSqr(position()) > 3.5 * 3.5) return;
        Player p = level.getNearestPlayer(this, 16.0);
        if (p == null || p.isSpectator()) return;
        if (--chirpIn > 0) return;
        double d = Math.sqrt(p.distanceToSqr(this));
        chirpIn = 2 + (int) (d / 16.0 * 10); // every 10 to 60 ticks (in steps of 5)
        float pitch = 1.0F + (float) Mth.clamp(1.0 - d / 16.0, 0.0, 1.0) * 0.8F;
        SoundEvent s = dev.awaken.mobs.MobSounds.fauna(def().id, "ambient");
        level.playSound(null, getX(), getY(), getZ(), s != null ? s : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, pitch);
        level.sendParticles(ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.3, getZ(), 1, 0.1, 0.0, 0.1, (d / 16.0));
        level.sendParticles(ParticleTypes.GLOW, lure.getX() + 0.5, lure.getY() + 0.5, lure.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
        pose(S_CHIRP, true);
        chirpPose = 8;
    }

    /** Tests: the cricket's search and the lurker's roof, called directly. */
    public void seekOreForTest(ServerLevel level) {
        seekOre(level);
    }

    public int ceilingAboveForTest() {
        return ceilingAbove(3, 9);
    }

    /** The ore it sings for (tests, the Codex). */
    public @Nullable BlockPos singingFor() {
        return lure;
    }

    // ---- the wickling --------------------------------------------------------------------------------------------------

    /** A dozen probes within 6 blocks (mostly overhead) for a cave vine without berries; never inside a Hearth radius. */
    @Nullable BlockPos findVine(ServerLevel level) {
        if (HearthHooks.inside(level, blockPosition())) return null;
        for (int i = 0; i < 14; i++) {
            probe.set(getBlockX() + random.nextInt(13) - 6, getBlockY() + random.nextInt(8) - 1, getBlockZ() + random.nextInt(13) - 6);
            if (!level.hasChunkAt(probe)) continue;
            BlockState s = level.getBlockState(probe);
            if (s.getBlock() instanceof CaveVines && s.hasProperty(BlockStateProperties.BERRIES) && !s.getValue(BlockStateProperties.BERRIES)) return probe.immutable();
        }
        return null;
    }

    /** Under its vine: the vine (and up to two more of its column) bears glow berries. Returns how many ripened. */
    public int ripen(ServerLevel level, BlockPos vine) {
        lure = null;
        talentCooldown = 600 + random.nextInt(400);
        if (HearthHooks.inside(level, vine)) return 0;
        int n = 0;
        BlockPos.MutableBlockPos m = vine.mutable();
        for (int i = 0; i < 6 && n < 3; i++, m.move(Direction.UP)) {
            BlockState s = level.getBlockState(m);
            if (!(s.getBlock() instanceof CaveVines)) break;
            if (s.hasProperty(BlockStateProperties.BERRIES) && !s.getValue(BlockStateProperties.BERRIES) && (i == 0 || random.nextBoolean())) {
                level.setBlock(m, s.setValue(BlockStateProperties.BERRIES, true), 3);
                level.sendParticles(ParticleTypes.GLOW, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
                n++;
            }
        }
        if (n > 0) {
            level.playSound(null, vine, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.NEUTRAL, 0.6F, 1.5F);
            pose(S_RIPEN, true);
            ripenPose = 30;
        }
        return n;
    }

    // ---- the drip lurker -----------------------------------------------------------------------------------------------

    public boolean hanging() {
        return hanging;
    }

    /** The y of a sturdy roof 3 to {@code max} blocks over its head with open air under it, or MIN_VALUE. */
    int ceilingAbove(int min, int max) {
        Level level = level();
        int head = Mth.floor(getY() + getBbHeight());
        for (int dy = 0; dy <= max; dy++) {
            probe.set(getBlockX(), head + dy, getBlockZ());
            BlockState s = level.getBlockState(probe);
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            if (dy < min - 1 || !s.isFaceSturdy(level, probe, Direction.DOWN)) return Integer.MIN_VALUE;
            return probe.getY();
        }
        return Integer.MIN_VALUE;
    }

    /** Clings to the roof (its top against the block at {@code ceiling}). */
    public void hang(int ceiling) {
        hanging = true;
        dropping = false;
        setNoGravity(true);
        setPos(getX(), ceiling - getBbHeight(), getZ());
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        pose(S_HANG, true);
    }

    /** Lets go and falls on its prey. */
    public void drop(@Nullable LivingEntity prey) {
        if (!hanging) return;
        hanging = false;
        dropping = true;
        setNoGravity(false);
        pose(S_HANG, false);
        setDeltaMovement(0, -0.5, 0);
        needsSync = true;
        if (prey != null) setTarget(prey);
        playSound(SoundEvents.POINTED_DRIPSTONE_FALL, 1.0F, 0.8F);
    }

    private void land(ServerLevel level) {
        dropping = false;
        BlockState rock = Blocks.DRIPSTONE_BLOCK.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock), getX(), getY() + 0.2, getZ(), 18, 0.6, 0.2, 0.6, 0.1);
        playSound(SoundEvents.POINTED_DRIPSTONE_LAND, 1.2F, 0.7F);
        LivingEntity t = getTarget();
        if (t != null && distanceToSqr(t) < 2.4 * 2.4) doHurtTarget(level, t);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && hanging) drop(source.getEntity() instanceof LivingEntity l ? l : null);
        return hurt;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return talent() != FaunaTalent.CLING && super.causeFallDamage(fallDistance, damageModifier, source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (hanging) out.putBoolean("awaken_hanging", true);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        if (in.getBooleanOr("awaken_hanging", false)) {
            hanging = true;
            setNoGravity(true);
            pose(S_HANG, true);
        }
    }

}
