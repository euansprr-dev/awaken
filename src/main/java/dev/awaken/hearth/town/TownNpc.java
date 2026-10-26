package dev.awaken.hearth.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A townsperson. Wanders around the Hearth, opens doors, gathers when you rank up, and talks to you. Never needs
 * anything from you: no beds, no jobs, no assignments. Cannot be hurt.
 */
public class TownNpc extends PathfinderMob implements Npc {
    private static final EntityDataAccessor<String> DATA_KIND = SynchedEntityData.defineId(TownNpc.class, EntityDataSerializers.STRING);
    /** (W6) The skin it wears (a texture under textures/entity/, "" = its kind's townsfolk skin), and its look flags. */
    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(TownNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> DATA_LOOKS = SynchedEntityData.defineId(TownNpc.class, EntityDataSerializers.BYTE);
    /** Look flags: a bundle on the back (someone walking up the road to move in), slim arms. */
    public static final int L_BUNDLE = 1, L_SLIM = 2;

    private @Nullable UUID hearthOwner;
    private @Nullable Vec3 gatherAt;
    private int gatherTicks;
    /** (W6) Walking up the road to move in: where to, and whose arrival this is (never saved: an arrival is one walk). */
    private @Nullable BlockPos walkTo;
    private @Nullable String arrival;
    private int walkTicks, lastProgress;
    private double bestDist = Double.MAX_VALUE;

    public TownNpc(EntityType<? extends TownNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        getNavigation().setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIND, "");
        b.define(DATA_SKIN, "");
        b.define(DATA_LOOKS, (byte) 0);
    }

    public String skin() {
        return entityData.get(DATA_SKIN);
    }

    public int looks() {
        return entityData.get(DATA_LOOKS);
    }

    /** An arrival: walks to {@code to} wearing {@code skin} with a bundle on the back, then {@code Arrival.arrived}. */
    public void arrive(String id, String skin, boolean slim, BlockPos to) {
        this.arrival = id;
        this.walkTo = to.immutable();
        entityData.set(DATA_SKIN, skin);
        entityData.set(DATA_LOOKS, (byte) (L_BUNDLE | (slim ? L_SLIM : 0)));
        getNavigation().setRequiredPathLength(160);
        walkTicks = 0;
        lastProgress = 0;
        bestDist = Double.MAX_VALUE;
    }

    public @Nullable String arrival() {
        return arrival;
    }

    public @Nullable BlockPos walkTarget() {
        return walkTo;
    }

    public String kind() {
        return entityData.get(DATA_KIND);
    }

    public void setKind(String id) {
        entityData.set(DATA_KIND, id);
    }

    public @Nullable UUID hearthOwner() {
        return hearthOwner;
    }

    public void setHearthOwner(UUID owner) {
        this.hearthOwner = owner;
    }

    /** Walk over to a spot (a celebration) for a while, then go back to wandering. */
    public void gather(Vec3 at, int ticks) {
        this.gatherAt = at;
        this.gatherTicks = ticks;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WalkInGoal(this));
        goalSelector.addGoal(1, new GatherGoal(this));
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.45));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (gatherTicks > 0 && --gatherTicks == 0) gatherAt = null;
        if (walkTo != null && level() instanceof ServerLevel sl) {
            walkTicks++;
            double d = position().distanceToSqr(Vec3.atBottomCenterOf(walkTo));
            if (d < bestDist - 1) {
                bestDist = d;
                lastProgress = walkTicks;
            }
            // there (or as near as a door lets them): moved in. Stuck a long while, or far too long on the road: in anyway.
            if (d < 1.6 * 1.6 || walkTicks - lastProgress > 20 * 12 || walkTicks > 20 * 120) {
                BlockPos at = walkTo;
                walkTo = null;
                dev.awaken.hearth.settle.Arrival.arrived(sl, this, at);
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            getLookControl().setLookAt(sp);
            getNavigation().stop();
            Town.talk(sp, this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
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
        out.putString("kind", kind());
        if (hearthOwner != null) out.store("hearth_owner", UUIDUtil.CODEC, hearthOwner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setKind(in.getStringOr("kind", ""));
        hearthOwner = in.read("hearth_owner", UUIDUtil.CODEC).orElse(null);
    }

    /** Up the road and in through the door to the room they'll take, at a steady walk (repathed as they go). */
    static final class WalkInGoal extends Goal {
        private final TownNpc npc;
        private int repath;

        WalkInGoal(TownNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return npc.walkTo != null;
        }

        @Override
        public boolean canContinueToUse() {
            return npc.walkTo != null;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            BlockPos to = npc.walkTo;
            if (to == null) return;
            if (--repath <= 0 || npc.getNavigation().isDone()) {
                repath = 40;
                npc.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0, 0.42);
            }
            if (npc.getNavigation().isDone()) npc.getLookControl().setLookAt(to.getX() + 0.5, to.getY() + 1.4, to.getZ() + 0.5);
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
        }
    }

    static final class GatherGoal extends Goal {
        private final TownNpc npc;

        GatherGoal(TownNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return npc.gatherAt != null && npc.position().distanceToSqr(npc.gatherAt) > 9;
        }

        @Override
        public boolean canContinueToUse() {
            return npc.gatherAt != null && !npc.getNavigation().isDone() && npc.position().distanceToSqr(npc.gatherAt) > 6;
        }

        @Override
        public void start() {
            Vec3 at = npc.gatherAt;
            if (at != null) npc.getNavigation().moveTo(at.x, at.y, at.z, 0.7);
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
        }
    }

    public BlockPos homePos() {
        return getHomePosition();
    }
}
