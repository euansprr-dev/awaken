package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.mobs.MobLoot;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A named beast of the overworld (one per region; the Mowzie's Mobs pattern): it keeps a territory, warns a player
 * who walks into it, and only fights someone who stays or strikes it. The fight has the boss phases of its def
 * (new abilities, a line, a bar that shows only while it fights), it heals and resets when left alone, and it drops
 * essence, its part and a good chance of a Power Core. Persistent: it is still there when you come back.
 */
public class NamedBeast extends FaunaMob {
    /** Power Core chance on a player kill (a Gate Lord's is 40%). */
    public static final float CORE_CHANCE = 0.45F;
    private static final Identifier PHASE_SPEED = Awaken.id("fauna_beast_phase_speed");
    private static final BossEvent.BossBarColor[] PHASE_COLOURS = {BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.RED};

    private int phase, warnCooldown, closeTicks, calmTicks;
    private boolean guaranteedCore;

    public NamedBeast(EntityType<? extends NamedBeast> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public int phase() {
        return phase;
    }

    /** Commands and tests: the next kill by a player drops a Power Core for certain. */
    public void setGuaranteedCore(boolean on) {
        this.guaranteedCore = on;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MobGoals.Hold(this));
        if (def().flies()) {
            goalSelector.addGoal(3, new MobGoals.FlyHunt(this));
            goalSelector.addGoal(7, new MobGoals.FlyWander(this));
        } else {
            goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
            goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.9));
            goalSelector.addGoal(6, new FaunaGoals.Graze(this));
            goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
        }
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 14.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** A named beast's bar shows only while it is fighting someone (a beast minding its territory is not a boss fight). */
    @Override
    protected void ensureBar() {
        super.ensureBar();
        barVisible(getTarget() != null);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (abilities != null && abilities.active() == null) checkPhase(level);
        if ((tickCount + getId()) % 10 == 0) territory(level);
        if ((tickCount + getId()) % 40 == 0) FaunaHooks.server().beastSeen(this);
        if (bar != null) {
            boolean show = getTarget() != null;
            if (bar.isVisible() != show) bar.setVisible(show);
        }
    }

    private void territory(ServerLevel level) {
        MobDef d = def();
        if (!hasHome()) setHomeTo(blockPosition(), d.arena);
        if (warnCooldown > 0) warnCooldown -= 10;
        LivingEntity t = getTarget();
        Vec3 home = Vec3.atBottomCenterOf(getHomePosition());
        if (t != null) {
            calmTicks = 0;
            double leash = d.arena + 16;
            boolean unfair = t instanceof Player p && (p.isCreative() || p.isSpectator());
            if (!t.isAlive() || unfair || t.position().distanceToSqr(home) > leash * leash) {
                setTarget(null);
                getNavigation().moveTo(home.x, home.y, home.z, 0.9);
            }
            return;
        }
        calmTicks += 10;
        if (calmTicks > 200 && getHealth() < getMaxHealth()) heal(getMaxHealth() * 0.02F);
        if (phase > 0 && getHealth() >= getMaxHealth()) {
            phase = 0;
            enterPhase(level, false);
        }
        Player p = level.getNearestPlayer(this, 14.0);
        if (p == null || p.isCreative() || p.isSpectator() || !hasLineOfSight(p)) {
            closeTicks = 0;
            return;
        }
        getLookControl().setLookAt(p, 30.0F, 30.0F);
        if (warnCooldown <= 0) {
            warnCooldown = 120;
            warn(level, p);
        }
        if (distanceToSqr(p) < 7 * 7) {
            closeTicks += 10;
            if (closeTicks >= 40 && p instanceof ServerPlayer sp) provoke(sp);
        } else {
            closeTicks = Math.max(0, closeTicks - 10);
        }
    }

    /** One snort, a flash and a line on the action bar: you are in its territory. */
    private void warn(ServerLevel level, Player p) {
        setTelegraph(16, false);
        cue(CUE_WARN, -1);
        net.minecraft.sounds.SoundEvent own = MobSounds.fauna(def().id, "ambient");
        playLevelSound(own != null ? own : def().voice().ambient != null ? def().voice().ambient : MobSounds.TELEGRAPH.get(), 1.6F, own != null ? 0.9F : 0.7F);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.4, 0.2, 0.4, 0.02);
        if (p instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.literal(def().name + " warns you off. Stay, or strike it, and it fights.").withColor(def().glow));
        }
    }

    private void provoke(ServerPlayer p) {
        setTarget(p);
        closeTicks = 0;
        playLevelSound(MobSounds.BOSS_INTRO.get(), 1.6F, 1.1F);
        roar();
    }

    /** The body rears and roars (its clip and its own cry): a fight begins, or a new phase. */
    private void roar() {
        cueRoar(CUE_ROAR);
        net.minecraft.sounds.SoundEvent cry = MobSounds.fauna(def().id, "roar");
        if (cry != null) playSound(cry, getSoundVolume() * 1.2F, 0.9F + random.nextFloat() * 0.1F);
    }

    /** Beasts take longer to fall than vanilla's second: the death clip runs this many ticks before removal. */
    public static final int DEATH_TICKS = 36;
    private int deathHold;

    @Override
    protected void tickDeath() {
        // Hold just before vanilla's removal tick (19) for the rest of the fall, on both sides alike.
        if (deathTime == 19 && deathHold < DEATH_TICKS - 20) {
            deathHold++;
            return;
        }
        super.tickDeath();
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity before = getTarget();
        super.setTarget(target);
        if (target instanceof ServerPlayer sp && before != target && level() instanceof ServerLevel) FaunaHooks.server().beastAngered(this, sp);
    }

    private void checkPhase(ServerLevel level) {
        MobDef d = def();
        float frac = getHealth() / getMaxHealth();
        int want = 0;
        for (int i = 1; i < d.phases.size(); i++) if (frac < d.phases.get(i).below()) want = i;
        if (want <= phase) return;
        phase = want;
        enterPhase(level, true);
    }

    private void enterPhase(ServerLevel level, boolean announce) {
        MobDef d = def();
        if (abilities != null) {
            abilities.interrupt();
            abilities.setPhase(phase);
        }
        forgetCue(); // the interrupted wind-up never strikes
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        float bonus = d.phases.get(Math.min(phase, d.phases.size() - 1)).speedBonus();
        if (speed != null) {
            if (bonus > 0) speed.addOrUpdateTransientModifier(new AttributeModifier(PHASE_SPEED, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else speed.removeModifier(PHASE_SPEED);
        }
        if (bar != null) bar.setColor(phase == 0 ? d.barColor : PHASE_COLOURS[(phase - 1) % PHASE_COLOURS.length]);
        if (!announce) return;
        setTelegraph(24, true);
        roar();
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() * 0.5, getZ(), 2, 0.4, 0.4, 0.4, 0);
        playLevelSound(MobSounds.BOSS_PHASE.get(), 1.6F, 0.9F);
        String line = d.phases.get(phase).line();
        if (line.isEmpty()) return;
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(this) < 40 * 40) sp.sendOverlayMessage(Component.literal(line).withColor(d.glow));
        }
    }

    // ---- death and drops -------------------------------------------------------------------------------------------

    @Override
    protected void onDied(ServerLevel level, DamageSource source) {
        super.onDied(level, source);
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp : getKillCredit() instanceof ServerPlayer sp2 ? sp2 : null;
        FaunaHooks.server().beastFelled(level, this, killer);
        FaunaHooks.server().beastGone(this);
        if (bar != null) bar.removeAllPlayers();
    }

    /** The loot table carries essence and the part; the Power Core is rolled here (no elite roll on top). */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        if (!killedByPlayer) return;
        if (guaranteedCore || random.nextFloat() < CORE_CHANCE) {
            ItemStack core = PowerCoreHooks.roll(MobLoot.rankFor(contentLevel()), random);
            if (!core.isEmpty()) spawnAtLocation(level, core);
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel) FaunaHooks.server().beastGone(this);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    public @Nullable BlockPos territoryCentre() {
        return hasHome() ? getHomePosition() : null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("awaken_beast_phase", phase);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        phase = in.getIntOr("awaken_beast_phase", 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount == 2 && phase > 0 && level() instanceof ServerLevel sl) {
            if (abilities == null) abilities = new AbilityRunner(this);
            enterPhase(sl, false);
        }
    }
}
