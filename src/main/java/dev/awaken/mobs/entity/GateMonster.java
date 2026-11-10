package dev.awaken.mobs.entity;

import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A gate monster on a body of its own (SPEC §60): the same creature the bestiary always named, with the same numbers
 * and abilities, drawn on its rig and acting its moves out. The server tells the body what it is doing (a move winding
 * up, its strike, the opening after it, a miss) and judges the monster's rules, each of them data on its def:
 *
 * <ul>
 * <li><b>an opening</b> ({@code open}): after a move fires it stands open for that many ticks; blows bite half again as
 *     deep and it starts nothing;</li>
 * <li><b>stopped by a blow</b> ({@code stopped}): a direct hit while it winds a move up ends the move and staggers it;</li>
 * <li><b>a front that holds</b> ({@code front}): blows from in front do nothing unless it stands open or staggered;</li>
 * <li><b>planted to shoot</b> ({@code planted}): it can't turn while it winds up, and loses the move if its target
 *     gets beside it;</li>
 * <li><b>a miss costs it</b> ({@code miss}): a charge, a leap or a dive that hit nobody leaves it stuck for that long
 *     (a flier is brought down).</li>
 * </ul>
 *
 * Nothing here changes another creature's numbers.
 */
public class GateMonster extends AwakenMob implements RigBody {
    private static final EntityDataAccessor<Integer> DATA_CUE = SynchedEntityData.defineId(GateMonster.class, EntityDataSerializers.INT);
    /** Half the arc (degrees) a holding front covers, and the arc a planted shooter keeps its target in. */
    static final float FRONT_ARC = 70.0F, PLANT_ARC = 80.0F;
    /** A blow on an open monster bites this much deeper. */
    public static final float OPEN_BITE = 1.5F;
    /** Ticks a blow that stops a wind-up staggers it, and a planted shooter that lost its aim. */
    static final int STOPPED_TICKS = 26, LOST_AIM_TICKS = 18;

    // server (no initialisers: registerGoals runs inside the super constructor)
    private @Nullable Ability cueAbility;
    private int cueSerial;
    private boolean meleeTold;
    private int meleeIdle;
    private int openTicks;
    private boolean stuck;
    private boolean plantedNow;
    private float plantYaw;
    private boolean missWatch, missMoved;
    private int missSlot, missStamp, missWait;
    private int deathHold;
    // client
    private int cueStart, lastCue, prevCue, prevCueStart, prevCueEnd, dyingTicks;
    private float gait, gaitO, gaitSpeed;

    public GateMonster(EntityType<? extends GateMonster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_CUE, 0);
    }

    // ---- the cue ---------------------------------------------------------------------------------------------------

    /** The slot after the last ability: its plain blow, and the heavy one a big body throws every third time. */
    public static int meleeSlot(MobDef d, boolean heavy) {
        return FaunaMob.moveNames(d).size() + (heavy ? 1 : 0);
    }

    @Override
    public int cue() {
        return entityData.get(DATA_CUE);
    }

    private void cue(int stage, int slot) {
        cueSerial = (cueSerial + 1) & 0xFF;
        entityData.set(DATA_CUE, (slot + 1 & 0xFF) | (stage & 7) << 8 | cueSerial << 11);
    }

    private int slot(Ability a) {
        return FaunaMob.moveNames(def()).indexOf(a.name());
    }

    /** Whatever it was winding up will not land: the body lets the pose go. */
    private void forgetCue() {
        cueAbility = null;
        meleeTold = false;
        missWatch = false;
        plantedNow = false;
        if (FaunaMob.cueStage(cue()) != FaunaMob.CUE_NONE) cue(FaunaMob.CUE_NONE, -1);
    }

    /** Server, every tick: follow the ability runner (a wind-up begins, then it fires) and cue the body. */
    private void tickCue() {
        Ability a = abilities == null ? null : abilities.active();
        if (a != cueAbility) {
            if (a != null) {
                meleeTold = false;
                cue(FaunaMob.CUE_WINDUP, slot(a));
                plantedNow = def().planted;
                plantYaw = yBodyRot;
            } else if (cueAbility != null && isAlive()) {
                fired(cueAbility);
            }
            cueAbility = a;
        }
        // a blow that was told and never swung (its target gone): let the pose go
        if (meleeTold && tellKind() == Strikes.Kind.NONE && ++meleeIdle > 3) {
            meleeTold = false;
            cue(FaunaMob.CUE_NONE, -1);
        }
    }

    private void fired(Ability a) {
        MobDef d = def();
        plantedNow = false;
        int slot = slot(a);
        boolean lunges = a.kind() == Ability.Kind.CHARGE || a.kind() == Ability.Kind.LEAP;
        if (lunges && d.miss > 0) {
            // judged when the lunge ends (below): it either hit someone or it didn't
            cue(FaunaMob.CUE_STRIKE, slot);
            missWatch = true;
            missMoved = false;
            missWait = 0;
            missSlot = slot;
            missStamp = tickCount;
        } else if (d.open > 0 && opens(a)) {
            cue(CUE_OPEN, slot);
            open(d.open);
        } else {
            cue(FaunaMob.CUE_STRIKE, slot);
        }
    }

    /** Moves that leave it open: the ones it commits its body to, not a call or a guard. */
    static boolean opens(Ability a) {
        return switch (a.kind()) {
            case SUMMON, HEAL, GUARD, DECOY, FRENZY, BLINK -> false;
            default -> true;
        };
    }

    void open(int ticks) {
        openTicks = Math.max(openTicks, ticks);
        holdStill(ticks);
    }

    /** Whether it stands open (after a move, or stuck where a miss left it). */
    public boolean isOpen() {
        return openTicks > 0;
    }

    private void tickRules(ServerLevel level) {
        MobDef d = def();
        if (openTicks > 0 && --openTicks == 0) {
            stuck = false;
            if (FaunaMob.cueStage(cue()) == CUE_OPEN || FaunaMob.cueStage(cue()) == CUE_STUCK) cue(FaunaMob.CUE_NONE, -1);
        }
        if (missWatch && abilities != null) {
            if (abilities.holdsStill()) {
                missMoved = true;
            } else if (missMoved) {
                missWatch = false;
                if (!landed(level) && isAlive()) {
                    if (d.flies()) ground(d.miss); // (its stagger lets the old cue go, so the new one comes after)
                    stuck = true;
                    cue(CUE_STUCK, missSlot);
                    open(d.miss);
                    level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.2, getZ(), 6, 0.3, 0.1, 0.3, 0.05);
                }
            } else if (++missWait > 10) {
                missWatch = false; // it never left the spot (no room, a blind strike): nothing to judge
            }
        }
        // planted: it holds the way it faced when it began, and loses the move if its target gets beside it
        if (plantedNow && cueAbility != null) {
            LivingEntity t = getTarget();
            if (t != null && Math.abs(Mth.wrapDegrees(yawTo(t.position()) - plantYaw)) > PLANT_ARC) {
                stagger(LOST_AIM_TICKS);
            }
        }
    }

    /** Whether the lunge that just ended hurt anyone (whoever it was aimed at, or whoever stood in its way). */
    private boolean landed(ServerLevel level) {
        int since = tickCount - missStamp + 2;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8.0), e -> e != this)) {
            if (e.getLastHurtByMob() == this && e.tickCount - e.getLastHurtByMobTimestamp() <= since) return true;
        }
        return false;
    }

    private float yawTo(Vec3 p) {
        return (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if ((tickCount & 3) == 0) setAggressive(getTarget() != null); // synced: the body's fighting stance
        tickRules(level);
        tickCue();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientAnimTick();
        } else if (plantedNow && cueAbility != null && isAlive()) {
            // after the controls have turned it: a planted shooter keeps the facing it began with
            setYRot(plantYaw);
            yBodyRot = plantYaw;
            yBodyRotO = plantYaw;
            yHeadRot = plantYaw + Mth.clamp(Mth.wrapDegrees(yHeadRot - plantYaw), -50.0F, 50.0F);
        }
    }

    // ---- its plain blow, told and swung by the body ----------------------------------------------------------------

    @Override
    public void setTell(int ticks, Strikes.Kind kind, boolean heavyLook) {
        super.setTell(ticks, kind, heavyLook);
        if (ticks <= 0 || level().isClientSide() || meleeTold) return;
        if (abilities != null && abilities.active() != null) return; // an ability's own tell: tickCue has it
        meleeTold = true;
        meleeIdle = 0;
        cue(FaunaMob.CUE_WINDUP, meleeSlot(def(), kind == Strikes.Kind.HEAVY));
    }

    @Override
    public void swingForAttack(InteractionHand hand) {
        super.swingForAttack(hand);
        if (meleeTold && !level().isClientSide()) {
            meleeTold = false;
            cue(FaunaMob.CUE_STRIKE, FaunaMob.cueSlot(cue()));
        }
    }

    // ---- being hit -------------------------------------------------------------------------------------------------

    @Override
    public boolean stagger(int ticks) {
        boolean did = super.stagger(ticks);
        if (did) {
            forgetCue();
            stuck = false;
        }
        return did;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        MobDef d = def();
        Entity attacker = source.getEntity();
        boolean blow = attacker instanceof LivingEntity && attacker != this && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        boolean exposed = openTicks > 0 || staggered() || flag(F_FALLEN) || flag(F_DORMANT);
        if (d.front && blow && !exposed && fromFront(source, attacker)) {
            // the front holds: a ring off stone or shell, a few sparks, nothing more
            playSound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), 0.9F, 0.7F + random.nextFloat() * 0.1F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.3, 0.3, 0.3, 0.1);
            return false;
        }
        boolean winding = abilities != null && abilities.active() != null;
        boolean hurt = super.hurtServer(level, source, openTicks > 0 ? damage * OPEN_BITE : damage);
        if (hurt && d.stopped && blow && winding && source.getDirectEntity() == attacker && isAlive()) stagger(STOPPED_TICKS);
        return hurt;
    }

    private boolean fromFront(DamageSource source, Entity attacker) {
        Vec3 from = source.getSourcePosition();
        if (from == null) from = attacker.position();
        return Math.abs(Mth.wrapDegrees(yawTo(from) - yBodyRot)) < FRONT_ARC;
    }

    // ---- the fall --------------------------------------------------------------------------------------------------

    @Override
    protected void tickDeath() {
        // hold just before vanilla's removal tick for the rest of its fall, on both sides alike
        if (deathTime == 19 && deathHold < def().fall - 20) {
            deathHold++;
            return;
        }
        super.tickDeath();
    }

    // ---- client ----------------------------------------------------------------------------------------------------

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_CUE && level() != null && level().isClientSide()) {
            prevCue = lastCue;
            prevCueStart = cueStart;
            prevCueEnd = tickCount;
            cueStart = tickCount;
            lastCue = entityData.get(DATA_CUE);
        }
    }

    private void clientAnimTick() {
        gaitO = gait;
        double dx = getX() - xo, dy = def().flies() ? getY() - yo : 0, dz = getZ() - zo;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 4) d = 0; // a teleport, not a stride
        gait += d;
        gaitSpeed += (d - gaitSpeed) * 0.35F;
        dyingTicks = isDeadOrDying() ? dyingTicks + 1 : 0;
    }

    @Override
    public float gait(float partialTicks) {
        return Mth.lerp(partialTicks, gaitO, gait);
    }

    @Override
    public float gaitSpeed() {
        return gaitSpeed;
    }

    @Override
    public int cueStart() {
        return cueStart;
    }

    @Override
    public int prevCue() {
        return prevCue;
    }

    @Override
    public int prevCueStart() {
        return prevCueStart;
    }

    @Override
    public int prevCueEnd() {
        return prevCueEnd;
    }

    /** Its plain blow is a cue of its own (a wind-up and a strike), so no swing clip plays on top. */
    @Override
    public int meleeStart() {
        return Integer.MIN_VALUE / 2;
    }

    @Override
    public int dyingTicks() {
        return dyingTicks;
    }

    @Override
    public AwLook look(float partialTicks) {
        AwLook l = super.look(partialTicks);
        // the body is painted in its own colours: only frost, charge, a rising and a haunting tint it
        l.tint = l.kitTint;
        int pose = 0;
        if (!onGround() || def().flies()) pose |= FaunaMob.P_AIR;
        if (l.aggressive || getTarget() != null) pose |= FaunaMob.P_ANGRY;
        l.fauna = pose;
        return l;
    }

    // ---- its own voice ---------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        if (flag(F_DORMANT) || flag(F_FALLEN)) return null;
        SoundEvent s = MobSounds.monster(def().id, "ambient");
        return s != null ? s : super.getAmbientSound();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        SoundEvent s = MobSounds.monster(def().id, "hurt");
        return s != null ? s : super.getHurtSound(source);
    }

    @Override
    protected SoundEvent getDeathSound() {
        SoundEvent s = MobSounds.monster(def().id, "death");
        return s != null ? s : super.getDeathSound();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        SoundEvent s = MobSounds.monster(def().id, "step");
        if (s != null) playSound(s, 0.35F, 0.92F + random.nextFloat() * 0.16F);
        else super.playStepSound(pos, state);
    }
}
