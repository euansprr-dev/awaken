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

    /**
     * After the Wake (SPEC §43.14): where a risen Stilled is walking (toward the bells), or the fire Ilse is walking to
     * on her one visit. {@code visiting} marks that visit; {@code visitLine} is how far through what she came to say.
     */
    @org.jspecify.annotations.Nullable BlockPos walkTo;
    boolean visiting;
    int visitLine;

    /** One of the Stilled, stood up: walks toward {@code bells} until nobody's near enough to see. */
    public void walkHome(BlockPos bells) {
        walkTo = bells.immutable();
        clearHome();
    }

    /** Ilse's one visit: she walks to {@code fire}, says her piece, and goes home at dusk. */
    public void visit(BlockPos fire) {
        walkTo = fire.immutable();
        visiting = true;
        setHomeTo(walkTo, 4);
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
        if (who().equals("risen")) {
            risen(level);
            return;
        }
        if (visiting) {
            visiting(level);
            return;
        }
        if (tickCount % 200 != 0 || level.getNearestPlayer(this, 16) != null) return;
        if (scene != 0 && Scenes.scene(level.getServer(), scene) == null) discard();
        else if (scene == 0 && who().equals("hettie") && !dev.awaken.core.api.CityHooks.inCity(level, blockPosition())) discard();
    }

    /** A risen Stilled: a step at a time toward the bells, gone once nobody is within 48 or it's nearly there. */
    private void risen(ServerLevel level) {
        if (walkTo == null || (tickCount > 200 && tickCount % 100 == 0 && !watched(level, 48))) {
            discard();
            return;
        }
        if (tickCount % 40 != 0) return;
        net.minecraft.world.phys.Vec3 d = net.minecraft.world.phys.Vec3.atCenterOf(walkTo).subtract(position()).multiply(1, 0, 1);
        double len = d.length();
        if (len < 8) {
            discard();
            return;
        }
        net.minecraft.world.phys.Vec3 step = position().add(d.scale(Math.min(1, 20 / len)));
        getNavigation().moveTo(step.x, getY(), step.z, 0.6);
    }

    /** Anyone within {@code reach}, a spectator too (one watching shouldn't see them vanish). */
    private boolean watched(ServerLevel level, double reach) {
        for (net.minecraft.server.level.ServerPlayer p : level.players()) if (p.distanceToSqr(this) < reach * reach) return true;
        return false;
    }

    /** Ilse visiting: walks to the fire and stays by it; at dusk (or after a long while with nobody about) she goes home. */
    private void visiting(ServerLevel level) {
        if (walkTo == null) {
            discard();
            return;
        }
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        boolean alone = !watched(level, 24);
        if (tickCount % 100 == 0 && alone && (tod >= 12500 || tickCount > 20 * 60 * 20)) {
            discard();
            return;
        }
        if (tickCount % 40 == 0 && distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(walkTo)) > 9)
            getNavigation().moveTo(walkTo.getX() + 0.5, walkTo.getY(), walkTo.getZ() + 1.5, 0.55);
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
        if (walkTo != null) out.putLong("awaken_walk_to", walkTo.asLong());
        out.putBoolean("awaken_visiting", visiting);
        out.putInt("awaken_visit_line", visitLine);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(DATA_WHO, in.getStringOr("awaken_who", ""));
        scene = in.getLongOr("awaken_scene", 0L);
        long w = in.getLongOr("awaken_walk_to", Long.MIN_VALUE);
        walkTo = w == Long.MIN_VALUE ? null : BlockPos.of(w);
        visiting = in.getBooleanOr("awaken_visiting", false);
        visitLine = in.getIntOr("awaken_visit_line", 0);
    }
}
