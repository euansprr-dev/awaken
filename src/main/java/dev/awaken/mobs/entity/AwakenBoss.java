package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.mobs.MobLoot;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Gate Lords, stratum guardians, Sovereigns, the Signal's Shadow and nightmares. Adds phases (a short invulnerable
 * roar, a new ability list, the bar changes colour, the boss says its line), arena awareness (it stays near its
 * spawn, walks or blinks back, resets if everyone leaves), and boss drops and counters on death.
 */
public class AwakenBoss extends AwakenMob {
    private static final Identifier PHASE_SPEED = Awaken.id("mobs_phase_speed");
    private static final BossEvent.BossBarColor[] PHASE_COLOURS = {BossEvent.BossBarColor.RED, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarColor.WHITE};

    private int phase, shieldTicks, lonelyTicks;
    /** Bitmask of phases whose part already broke off (kept through resets, so a part can't be farmed). */
    private int partsBroken;
    private boolean introduced;

    public AwakenBoss(EntityType<? extends AwakenBoss> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public int phase() {
        return phase;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (shieldTicks > 0) {
            shieldTicks--;
            if (shieldTicks % 4 == 0) Telegraph.ring(level, position(), 1.5F + getBbWidth(), 18, def().glow, true);
        }
        super.customServerAiStep(level);
        if (abilities != null && abilities.active() == null) checkPhase(level);
        if (tickCount % 20 == 0) arena(level);
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
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        float bonus = d.phases.get(Math.min(phase, d.phases.size() - 1)).speedBonus();
        if (speed != null) {
            if (bonus > 0) speed.addOrUpdateTransientModifier(new AttributeModifier(PHASE_SPEED, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else speed.removeModifier(PHASE_SPEED);
        }
        if (bar != null) bar.setColor(phase == 0 ? d.barColor : PHASE_COLOURS[(phase - 1) % PHASE_COLOURS.length]);
        if (!announce) return;
        shieldTicks = 30;
        setTelegraph(30, true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
        playLevelSound(MobSounds.BOSS_PHASE.get(), 2.0F, 0.8F);
        if ((partsBroken & (1 << phase)) == 0) {
            partsBroken |= 1 << phase;
            MobLoot.breakPart(level, this, phase);
        }
        String line = d.phases.get(phase).line();
        if (!line.isEmpty()) tell(level, Component.literal(line).withColor(d.glow));
    }

    private void tell(ServerLevel level, Component text) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < (def().arena + 16) * (def().arena + 16)) p.sendSystemMessage(text);
        }
    }

    void arena(ServerLevel level) {
        MobDef d = def();
        if (!hasHome()) setHomeTo(blockPosition(), d.arena);
        Vec3 home = Vec3.atBottomCenterOf(getHomePosition());
        int radius = getHomeRadius(); // Break Lords have a caller-supplied home, larger than their indoor arena.
        double limit = radius + 10;
        if (position().distanceToSqr(home) > limit * limit) {
            // Kited out of its arena: step back in.
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 20, 0.5, 1, 0.5, 0.2);
            teleportTo(home.x, home.y, home.z);
            getNavigation().stop();
            getMoveControl().setWait();
            setDeltaMovement(Vec3.ZERO);
        }
        LivingEntity t = getTargetUnchecked();
        if (t != null && !insideHome(t)) setTarget(null);
        Player near = level.getNearestPlayer(home.x, home.y, home.z, radius + 16, p -> !p.isSpectator() && !((Player) p).isCreative());
        if (near == null) {
            lonelyTicks += 20;
            if (lonelyTicks >= 600 && (getHealth() < getMaxHealth() || phase > 0)) reset(level);
        } else {
            lonelyTicks = 0;
            if (!introduced && getTarget() != null) {
                introduced = true;
                if (d.intro != null) tell(level, Component.literal(d.name + ": ").withColor(d.glow).append(Component.literal(d.intro).withColor(0xE6EAF2)));
                playLevelSound(MobSounds.BOSS_INTRO.get(), 2.0F, 0.9F);
            }
        }
    }

    /** Acquisition and continued pursuit use the same saved home as the flight controller and the return check. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && insideHome(target);
    }

    private boolean insideHome(LivingEntity target) {
        if (!hasHome()) return true;
        var home = getHomePosition();
        double radius = getHomeRadius();
        return target.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) <= radius * radius;
    }

    /** Everyone left the arena: heal up and start over (no drops, nothing is remembered). */
    protected void reset(ServerLevel level) {
        lonelyTicks = 0;
        introduced = false;
        setHealth(getMaxHealth());
        setTarget(null);
        phase = 0;
        enterPhase(level, false);
        if (bar != null) bar.setProgress(1.0F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (shieldTicks > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.5, 0.5, 0.5, 0.1);
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    protected void onDied(ServerLevel level, DamageSource source) {
        super.onDied(level, source);
        MobLoot.onBossKilled(level, this, source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        MobLoot.bossDrops(level, this, source);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("awaken_phase", phase);
        out.putInt("awaken_parts_broken", partsBroken);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        phase = in.getIntOr("awaken_phase", 0);
        partsBroken = in.getIntOr("awaken_parts_broken", 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount == 2 && level() instanceof ServerLevel sl && phase > 0) {
            if (abilities == null) abilities = new AbilityRunner(this);
            enterPhase(sl, false);
        }
    }
}
