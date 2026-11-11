package dev.awaken.mobs.entity;

import dev.awaken.core.api.Strikes;
import dev.awaken.Awaken;
import dev.awaken.core.api.BossPlates;
import dev.awaken.mobs.MobLoot;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
     * A kit boss's move, synced (SPEC §35.3): bits 0-9 the move index + 1, 10-15 its hold in ticks, 16-19 the pose
     * ({@link #KIT_POSE_INTRO}, {@link #KIT_POSE_BROKEN}), 20-27 a serial (the same move twice restarts its clips); and
     * the game time the move began, so a late joiner sees it where it is.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_KIT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(AwakenBoss.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** The degrees a kit body leans into the blow it's making (SPEC §47.15). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_KIT_LEAN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(AwakenBoss.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /** Where a kit body leans from, as a share of its height. */
    public static final float KIT_LEAN_HIP = 0.45F;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_KIT_AT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(AwakenBoss.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    public static final int KIT_POSE_INTRO = 1, KIT_POSE_BROKEN = 2;
    /** The duel's reactions (SPEC §47.3): a deflected blow's limb thrown back, and the reel after a phrase's last one. */
    public static final int KIT_POSE_RECOIL = 3, KIT_POSE_REEL = 4;
    /** It has seen what you started (SPEC §48.2): its weight drops before it answers. */
    public static final int KIT_POSE_NOTICE = 5;

    private int phase, shieldTicks, lonelyTicks;
    /** Its stance broken (Arms), or reeling from a deflect, for this many ticks more; open until this game time. */
    private int breakTicks, reelTicks;
    private long openUntil;
    private @Nullable FightRunner fight;
    private boolean fightChecked;
    private int kitSerial;
    /** Client: blocks walked (for gaits locked to distance), this tick and the last. */
    private float kitGait, kitGaitO;
    /** Client: how fast the body turns, degrees a tick, eased (this tick and the last). */
    private float kitTurn, kitTurnO;
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
        b.define(DATA_KIT_LEAN, (byte) 0);
    }

    /** An intro or transition with no roar of its own: the boss's voice, loud and low. */
    void roar() {
        var v = def().voice().ambient;
        if (v != null) playSound(v, 3.0F, 0.75F);
    }

    /** A kit body's footfall (SPEC §35.8): its palette's step under the block's own, so a giant shakes the floor. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super.playStepSound(pos, state);
        var k = kit();
        if (k != null && !level().isClientSide()) dev.awaken.mobs.boss.KitSounds.play(this, k.moves(), "step", 1F);
    }

    /** A big body's stride: a footfall about every width it walks (moveDist runs at 0.6 a block), not every block. */
    @Override
    protected float nextStep() {
        return kit() != null ? moveDist + Math.max(1F, getBbWidth() * 0.6F) : super.nextStep();
    }

    /**
     * A kit body turns as one heavy thing (SPEC §35.4). Vanilla's BodyRotationControl lets the body wait until the head is
     * 75° off and then lurch after it, ignoring the facing the fight script sets. Instead: on the server the body is the
     * facing (the capsules and the behind check use it); on the client it eases after the synced facing every tick, so it
     * never steps or snaps. The head turns within its range of the body.
     */
    @Override
    protected void tickHeadTurn(float yBodyRotT) {
        var k = kit();
        if (k == null || k.fight() == null) {
            super.tickHeadTurn(yBodyRotT);
            return;
        }
        float want = getYRot();
        if (!level().isClientSide()) yBodyRot = want;
        else {
            float d = Mth.clamp(Mth.wrapDegrees(want - yBodyRot) * 0.3F, -18, 18);
            yBodyRot += d;
            kitTurnO = kitTurn;
            kitTurn += (d - kitTurn) * 0.35F; // the turn steps fade in and out with it, never popping off at a threshold
        }
        yHeadRot = Mth.rotateIfNecessary(yHeadRot, yBodyRot, getMaxHeadYRot());
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

    @Override
    public boolean stagger(int ticks) {
        if (!super.stagger(ticks)) return false;
        breakTicks = Math.max(breakTicks, ticks);
        if (fight != null) fight.interrupt();
        return true;
    }

    /** Its stance broken (server): it kneels, and a heavy on it is the finisher. */
    public boolean broken() {
        return breakTicks > 0;
    }

    /** Reels from a phrase's last blow turned aside (SPEC §47.3): it stands open, not kneeling. */
    void reel(int ticks) {
        if (super.stagger(ticks)) reelTicks = Math.max(reelTicks, ticks);
    }

    boolean reeling() {
        return reelTicks > 0;
    }

    /** It stands open for {@code ticks} (after a phrase, a reel): it starts nothing, and Arms breaks its stance faster. */
    void openFor(int ticks) {
        openUntil = Math.max(openUntil, level().getGameTime() + ticks);
    }

    /** Whether it stands in an opening now (server). */
    public boolean inOpening() {
        return level().getGameTime() < openUntil || reelTicks > 0;
    }

    /** Glides three steps to each place it's sent, which it's sent every tick: a late packet can't stop the body (SPEC §48.8). */
    @Override
    protected net.minecraft.world.entity.InterpolationHandler createInterpolationHandler() {
        return net.minecraft.world.entity.LinearInterpolationHandler.create(this);
    }

    /** Where clients are told it is: while a move carries it, ahead by what their glide lags, so the run keeps the clip's clock (SPEC §48.8). */
    @Override
    public Vec3 trackingPosition() {
        FightRunner f = fight;
        Vec3 by = f != null ? f.lead() : null;
        if (by == null && abilities != null) by = abilities.lead();
        // a wall in the way: it stops there, so it isn't drawn inside it first
        return by == null || !level().noCollision(this, getBoundingBox().move(by)) ? position() : position().add(by);
    }

    /** One of its blows was deflected (Arms): its fight answers (a recoil, a reel). */
    public void deflected(Strikes.Kind kind) {
        if (fight != null && level() instanceof ServerLevel sl) fight.deflected(sl, kind);
    }

    @Override
    boolean fightBusy() {
        return fight != null && fight.busy();
    }

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

    /** Plays a string of kit moves now, the rest linking on as the fight script's would (tests). */
    public boolean playKitString(String first, java.util.List<String> then) {
        FightRunner f = fight();
        return f != null && level() instanceof ServerLevel sl && f.play(sl, first, then);
    }

    /** The kit move playing now (server side), or null. */
    public @Nullable String kitPlaying() {
        FightRunner f = fight;
        return f != null ? f.playing() : null;
    }

    /** The last attack its fight script began (title and class), or null (SPEC §47.4: the Signal names what felled you). */
    public dev.awaken.core.api.Strikes.@Nullable Blow lastBlow() {
        FightRunner f = fight;
        var m = f != null ? f.lastAttack() : null;
        if (m == null) return null;
        String title = !m.title().isEmpty() ? m.title() : m.ability().orElse(m.id());
        return new dev.awaken.core.api.Strikes.Blow(title, m.kind());
    }

    /** It fights as a duel (SPEC §47.3): phrases, openings, recoils; at most {@link #DUEL_ADDS} of its adds alive. */
    public boolean duel() {
        FightRunner f = fight();
        return f != null && f.duel();
    }

    /** A duel is tuned on its whole scaled health, past vanilla's 1024 cap (SPEC §47.3); other Lords stop at the cap. */
    @Override
    public float bulk() {
        return duel() ? dev.awaken.mobs.MobScaling.bulk(this) : 1F;
    }

    /** For a fight that reads you (SPEC §47.6): the habit it has read of you. */
    public dev.awaken.core.api.Strikes.Habit habit() {
        FightRunner f = fight();
        return f != null ? f.habit() : dev.awaken.core.api.Strikes.Habit.NONE;
    }

    /** A duel's stance size (Arms' posture), or 0 for a Lord's own. */
    public int duelStance() {
        FightRunner f = fight();
        return f != null ? f.stance() : 0;
    }

    /** A duel's adds alive at once (I6). */
    public static final int DUEL_ADDS = 2;
    /** What a duel's add hits for at most, as a share of its caller's attack (Mobs.spawnMinion). */
    public static final double ADD_DAMAGE = 0.6;

    /** The fight script's runner (server), or null when it fights the old way. */
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

    void syncKitLean(int degrees) {
        byte b = (byte) Mth.clamp(degrees, -60, 60);
        if (entityData.get(DATA_KIT_LEAN) != b) entityData.set(DATA_KIT_LEAN, b);
    }

    /** The degrees it leans into its blow (0 none). */
    public int kitLean() {
        return entityData.get(DATA_KIT_LEAN);
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

    /** Client: how fast the body turns (degrees a tick, + to its left), eased. */
    public float kitTurn(float partialTicks) {
        return kitTurnO + (kitTurn - kitTurnO) * partialTicks;
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

    boolean shielded() {
        return shieldTicks > 0;
    }

    /** Whether Arms holds its stance now (SPEC §47.3 "Held while it fights"), as it was last told. */
    private boolean stanceHeld;

    /** A duel body tells Arms when it begins and stops fighting: while it fights, its stance doesn't drain. */
    private void holdStance(boolean fighting) {
        if (fighting == stanceHeld) return;
        stanceHeld = fighting;
        dev.awaken.core.api.Strikes.holdStance(this, fighting);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (breakTicks > 0) breakTicks--;
        if (reelTicks > 0) reelTicks--;
        if (shieldTicks > 0) {
            shieldTicks--;
            if (shieldTicks % 4 == 0) Telegraph.ring(level, position(), 1.5F + getBbWidth(), 18, def().glow, true);
        }
        FightRunner f = fight();
        abilityRunner().scripted(f != null);
        super.customServerAiStep(level);
        if (f != null) {
            if (isAlive()) {
                long t0 = System.nanoTime();
                f.tick(level);
                if (f.duel()) holdStance(f.fighting());
                FightWatch.kit(System.nanoTime() - t0);
                // its clock goes to whoever is near, fighting or not: a pace that stopped (a reel against a beast, the
                // player it chased fallen) held its body still on their screen until the clock went stale
                dev.awaken.mobs.net.MobsNet.fighting(this);
            }
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
     * The boss speaks to everyone near its arena: under the Boss Plate for those who see its bar (SPEC §35.1), in chat
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

    /** Walking in never kills: everyone in its arena gets the Lord's moment as it first sees them (SPEC §47.14). */
    void moment(ServerLevel level) {
        double r = (hasHome() ? getHomeRadius() : def().arena) + 4.0;
        Vec3 home = hasHome() ? Vec3.atBottomCenterOf(getHomePosition()) : position();
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.distanceToSqr(home) <= r * r) FairGuard.arrive(p, this);
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
                moment(level);
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
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && fight != null) fight.hurtBy(source);
        return hurt;
    }

    /** Getting up is part of its fight: a stray tap can't end it, a real flurry can (SPEC §47.16). */
    @Override
    protected float fallenHealth() {
        return getMaxHealth() * 0.1F;
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
