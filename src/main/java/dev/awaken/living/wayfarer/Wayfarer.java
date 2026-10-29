package dev.awaken.living.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Wayfarer (SPEC §21, §43.1): Ilse at the Last Fire, Hettie in Carillon's market square, Hale at the High Gate. Drawn
 * with a named skin ({@code living.client.WayfarerRenderer}); talked to with use; hurt, never killed (Carillon's citizen
 * rule); stays near where it stands. Its name shows when looked at; it never wears a combat plate.
 */
public class Wayfarer extends PathfinderMob implements Npc {
    static final EntityDataAccessor<String> DATA_WHO = SynchedEntityData.defineId(Wayfarer.class, EntityDataSerializers.STRING);

    private long scene;

    public Wayfarer(EntityType<? extends Wayfarer> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_WHO, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Who this is ({@code wren}, {@code hettie}, ...), from {@link Wayfarers}. */
    public String who() {
        return entityData.get(DATA_WHO);
    }

    public long scene() {
        return scene;
    }

    /** Sets who it is, where it belongs (it strolls at most a few blocks from there) and which scene it belongs to. */
    public void become(String who, BlockPos home, int roam, long scene) {
        entityData.set(DATA_WHO, who);
        this.scene = scene;
        setHomeTo(home, roam);
        Wayfarers.Person p = Wayfarers.get(who);
        setCustomName(Component.literal(p == null ? who : p.name()));
        setCustomNameVisible(false);
    }

    /** Asleep (an old save's sleeper): lies still until morning or until spoken to. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isSleeping();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (isSleeping()) stopSleeping();
            getLookControl().setLookAt(sp);
            getNavigation().stop();
            Wayfarers.talk(sp, this);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A scene's Wayfarer moves on once its scene is over and nobody stands close (residents never do); so does an old
     * world's Hettie out at the camp, since she lives in the city now (SPEC §43.1).
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 200 != 0 || level.getNearestPlayer(this, 16) != null) return;
        if (scene != 0 && Scenes.scene(level.getServer(), scene) == null) discard();
        else if (scene == 0 && who().equals("hettie") && !dev.awaken.core.api.CityHooks.inCity(level, blockPosition())) discard();
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
        out.putString("awaken_who", who());
        out.putLong("awaken_scene", scene);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(DATA_WHO, in.getStringOr("awaken_who", ""));
        scene = in.getLongOr("awaken_scene", 0L);
    }
}
