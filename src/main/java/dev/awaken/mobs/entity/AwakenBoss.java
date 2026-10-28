package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.BossPlates;
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
import org.jspecify.annotations.Nullable;

/**
 * Gate Lords, stratum guardians, Sovereigns, the Signal's Shadow and nightmares. Adds phases (a short invulnerable
 * roar, a new ability list, the bar changes colour, the boss says its line), arena awareness (it stays near its
 * spawn, walks or blinks back, resets if everyone leaves), and boss drops and counters on death.
 */
public class AwakenBoss extends AwakenMob {
    private static final Identifier PHASE_SPEED = Awaken.id("mobs_phase_speed");
    private static final BossEvent.BossBarColor[] PHASE_COLOURS = {BossEvent.BossBarColor.RED, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarColor.WHITE};

    /**
     * A kit boss's move, synced (SPEC §34.3): bits 0-9 the move index + 1, 10-15 its hold in ticks, 16-19 the pose
     * ({@link #KIT_POSE_INTRO}, {@link #KIT_POSE_BROKEN}), 20-27 a serial (the same move twice restarts its clips); and
     * the game time the move began, so a late joiner sees it where it is.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_KIT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(AwakenBoss.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_KIT_AT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(AwakenBoss.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    public static final int KIT_POSE_INTRO = 1, KIT_POSE_BROKEN = 2;

    private int phase, shieldTicks, lonelyTicks;
    private @Nullable FightRunner fight;
    private boolean fightChecked;
    private int kitSerial;
    /** Client: blocks walked (for gaits locked to distance), this tick and the last. */
    private float kitGait, kitGaitO;
    /** Client: the sync as last seen, the move before the current one (to fade it out) and when the pose last changed. */
    private int kitSeen, kitSeenAt, kitPrevMove = -1, kitPrevStart, kitPrevHold, kitPrevEnd, kitPoseFrom, kitPoseAt = Integer.MIN_VALUE / 2;
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
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIT, 0);
        b.define(DATA_KIT_AT, 0);
    }

    /** The kit body this boss wears, if its kit loaded (both sides). */
    public dev.awaken.mobs.boss.Kits.@Nullable Kit kit() {
        return dev.awaken.mobs.boss.Kits.get(def().id);
    }

    @Override
    protected boolean scriptedFight() {
        var k = dev.awaken.mobs.boss.Kits.get(def().id);
        return k != null && k.fight() != null;
    }

    /** The fight script's runner (server), or null when it fights the old way. */
    /** The Creature Lab's studio (/awaken bosses studio): every move in turn, then the fight. False without a kit fight. */
    public boolean studio() {
        FightRunner f = fight();
        if (f == null) return false;
        f.studio();
        return true;
    }

    /** Plays one of its kit moves now (admin, tests). */
    public boolean playKitMove(String id) {
        FightRunner f = fight();
        return f != null && level() instanceof ServerLevel sl && f.play(sl, id);
    }

    /** The kit move playing now (server side), or null. */
    public @Nullable String kitPlaying() {
        FightRunner f = fight;
        return f != null ? f.playing() : null;
    }

    @Nullable FightRunner fight() {
        if (!fightChecked && !level().isClientSide()) {
            fightChecked = true;
            var k = kit();
            if (k != null && k.fight() != null) fight = new FightRunner(this, k);
        }
        return fight;
    }

    /** Server: a move starts ({@code move} >= 0, with its hold), or none plays and the body holds {@code pose}. */
    void syncKit(int move, int hold, int pose) {
        if (move >= 0) {
            kitSerial = (kitSerial + 1) & 0xFF;
            entityData.set(DATA_KIT_AT, (int) level().getGameTime());
            entityData.set(DATA_KIT, (move + 1 & 0x3FF) | (Math.min(63, hold) & 0x3F) << 10 | (pose & 0xF) << 16 | kitSerial << 20);
            return;
        }
        int v = (pose & 0xF) << 16 | kitSerial << 20;
        if (entityData.get(DATA_KIT) != v) entityData.set(DATA_KIT, v);
    }

    /** The synced move (an index into the kit's move table), or -1. */
    public int kitMove() {
        return (entityData.get(DATA_KIT) & 0x3FF) - 1;
    }

    public int kitHold() {
        return (entityData.get(DATA_KIT) >> 10) & 0x3F;
    }

    public int kitPose() {
        return (entityData.get(DATA_KIT) >> 16) & 0xF;
    }

    public int kitSerial() {
        return (entityData.get(DATA_KIT) >> 20) & 0xFF;
    }

    /** The game time the synced move began. */
    public int kitStart() {
        return entityData.get(DATA_KIT_AT);
    }

    /** Client: the move that played before the current one, its start, hold and the game time it ended; -1 none. */
    public int kitPrevMove() {
        return kitPrevMove;
    }

    public int kitPrevStart() {
        return kitPrevStart;
    }

    public int kitPrevHold() {
        return kitPrevHold;
    }

    public int kitPrevEnd() {
        return kitPrevEnd;
    }

    /** Client: the pose before the current one and the game time it changed. */
    public int kitPoseFrom() {
        return kitPoseFrom;
    }

    public int kitPoseAt() {
        return kitPoseAt;
    }

    /** Client: blocks walked, smoothed. */
    public float kitGait(float partialTicks) {
        return kitGaitO + (kitGait - kitGaitO) * partialTicks;
    }

    /** How fast its moves play: its fight's enrage speed under the enrage line, else 1 (both sides; health is synced). */
    public float kitSpeed() {
        var k = kit();
        if (k == null || k.fight() == null) return 1;
        var en = k.fight().enrage();
        return getHealth() / Math.max(1, getMaxHealth()) < en.below() ? en.speed() : 1;
    }

    /** Invulnerable for a transition's performance. */
    void shield(int ticks) {
        shieldTicks = Math.max(shieldTicks, ticks);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (shieldTicks > 0) {
            shieldTicks--;
            if (shieldTicks % 4 == 0) Telegraph.ring(level, position(), 1.5F + getBbWidth(), 18, def().glow, true);
        }
        FightRunner f = fight();
        abilityRunner().scripted(f != null);
        super.customServerAiStep(level);
        if (f != null) {
            if (isAlive()) f.tick(level);
            if (!f.busy()) checkPhase(level);
        } else if (abilities != null && abilities.active() == null) checkPhase(level);
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
        FightRunner f = fight();
        if (f != null) {
            // its transition move is the performance (it shields itself for as long as that plays)
            f.enterPhase(phase);
        } else {
            shieldTicks = 30;
            setTelegraph(30, true);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
        }
        playLevelSound(MobSounds.BOSS_PHASE.get(), 2.0F, 0.8F);
        if ((partsBroken & (1 << phase)) == 0) {
            partsBroken |= 1 << phase;
            MobLoot.breakPart(level, this, phase);
        }
        String line = d.phases.get(phase).line();
        if (!line.isEmpty()) tell(level, line, Component.literal(line).withColor(d.glow), BossPlates.Line.PHASE);
    }

    /**
     * The boss speaks to everyone near its arena: under the Boss Plate for those who see its bar (SPEC §34.1), in chat
     * ({@code chat}) for the rest.
     */
    void tell(ServerLevel level, String plate, Component chat, BossPlates.Line kind) {
        double r = def().arena + 16;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) >= r * r) continue;
            if (bar != null && bar.getPlayers().contains(p)) BossPlates.line(p, bar.getId(), plate, kind, def().glow);
            else p.sendSystemMessage(chat);
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
                if (d.intro != null) tell(level, d.intro, Component.literal(d.name + ": ").withColor(d.glow).append(Component.literal(d.intro).withColor(0xE6EAF2)), BossPlates.Line.INTRO);
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
        if (fight != null) fight.reset();
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

    /** A kit body falls by its own death clip: the body stays this many ticks before it goes (both sides alike). */
    private int deathHold;

    @Override
    protected void tickDeath() {
        var k = kit();
        if (k != null && deathTime == 19) {
            var clip = k.rig().clip("death");
            int len = clip == null ? 20 : Math.min(80, Math.round(clip.length() * 20) + 6);
            if (deathHold < len - 20) {
                deathHold++;
                return;
            }
        }
        super.tickDeath();
    }

    @Override
    public void tick() {
        if (level().isClientSide()) {
            kitGaitO = kitGait;
            double dx = getX() - xo, dz = getZ() - zo;
            kitGait += (float) Math.sqrt(dx * dx + dz * dz);
            int v = entityData.get(DATA_KIT);
            if (v != kitSeen) {
                int was = (kitSeen & 0x3FF) - 1;
                if (was >= 0) {
                    kitPrevMove = was;
                    kitPrevStart = kitSeenAt;
                    kitPrevHold = (kitSeen >> 10) & 0x3F;
                    kitPrevEnd = (int) level().getGameTime();
                }
                int poseWas = (kitSeen >> 16) & 0xF, pose = (v >> 16) & 0xF;
                if (pose != poseWas) {
                    kitPoseFrom = poseWas;
                    kitPoseAt = (int) level().getGameTime();
                }
                kitSeen = v;
                kitSeenAt = entityData.get(DATA_KIT_AT);
            }
        }
        super.tick();
        if (!level().isClientSide() && tickCount == 2 && level() instanceof ServerLevel sl && phase > 0) {
            if (abilities == null) abilities = new AbilityRunner(this);
            enterPhase(sl, false);
        }
    }
}
