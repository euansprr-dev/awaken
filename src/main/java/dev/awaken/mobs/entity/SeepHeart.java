package dev.awaken.mobs.entity;

import dev.awaken.mobs.MobParticles;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The seep heart (W5, Between the Gates; its def is in mobs.def.CaveBestiary): a knot of Deepglass where the Deep has
 * grown up through the rock at a seep's innermost chamber. Rooted at its socket, it never walks: its crown turns to face
 * you, its body rises a little, its arms and roots lash. It sleeps (plates shut round a dim core that beats slowly)
 * until a player comes within {@value #WAKE_RANGE} blocks or strikes it, then wakes with a moment of its own: the plates
 * burst open, the core flares, the room lights violet, a cry. Then three phases of told blows: Deep Shards (a fan you
 * can deflect), Rootfall (cracks of light open under you, then roots burst from the floor), Root Lash (round it, when
 * you hug it); at 65% Deep Pulse (it closes, swells and throws everything back, the light dims and darkness takes your
 * eyes) and the Call of the Deep (Hushlings claw out of the walls, called at once as the phase opens); at 30% Shard
 * Storm and Heartburst (a ring of light you dodge by stepping in to it). Left alone half a minute it closes again,
 * heals and sleeps. Its death is its own moment: the violet drains out of the room into the core, the plates shut and
 * go dark, it exhales.
 *
 * <p>The world places it ({@link dev.awaken.mobs.SeepHearts#spawn}) and listens for its death. Its level comes the
 * normal way (FaunaMob: a BEAST's, from the region). Its light is a real light block at its core, placed only into air
 * and taken away when it dies. It stays out of the beast registry and the fauna census ({@link #registered},
 * {@link #censused}): the seep is the world's to track.
 */
public class SeepHeart extends NamedBeast implements WildPose {
    /** A player this close (and within 7 blocks up or down) wakes it. */
    public static final double WAKE_RANGE = 12;
    /** Awake, it turns on players this close; it lets go of one past {@link #LEASH}. */
    static final double FIGHT_RANGE = 20, LEASH = 26;
    /** The wake's moment, in ticks; with nobody to fight this long it closes and sleeps. */
    static final int WAKE_TICKS = 40, SLEEP_AFTER = 600;
    /** Its fall: the drain, the plates closing, the dark (the rig's death clip is 3.2 s). */
    static final int DEATH_HOLD = 66;
    public static final int ASLEEP = 0, WAKING = 1, AWAKE = 2;
    static final String SHARDS = "Deep Shards", ROOTFALL = "Rootfall", LASH = "Root Lash", PULSE = "Deep Pulse", CALL = "Call of the Deep",
            STORM = "Shard Storm", BURST = "Heartburst";
    /** The core: this high over its feet (the rig's core sits at 26 model pixels). */
    static final double CORE = 1.6;

    /** Bits 0-1 the state, 2-3 the phase, 4: the light dimmed by a pulse. */
    private static final EntityDataAccessor<Byte> DATA_SEEP = SynchedEntityData.defineId(SeepHeart.class, EntityDataSerializers.BYTE);

    // server (no field initialisers: registerGoals runs inside the super constructor)
    private @Nullable BlockPos socket;
    private float rootYaw;
    private int wakeTicks, calm, callIn, dimTicks, flareTicks, deathAge, lightHave;
    private @Nullable UUID waker;
    private @Nullable BlockPos lightPos;
    private @Nullable List<BlockPos> callWalls;
    // client
    private int beatAt, nextBeat, seenState, wakeStart;

    public SeepHeart(EntityType<? extends SeepHeart> type, Level level) {
        super(type, level);
        setNoGravity(true);
        lightHave = -1;
        seenState = -1;
        beatAt = -1000;
    }

    // ---- setup ---------------------------------------------------------------------------------------------------------

    /** Roots it at {@code at} facing {@code yaw} (before it is added to the level). */
    public void root(BlockPos at, float yaw) {
        socket = at.immutable();
        rootYaw = yaw;
        snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    /** The block it is rooted in (its feet). */
    public BlockPos socket() {
        return socket != null ? socket : blockPosition();
    }

    @Override
    protected void registerGoals() {
        // it never moves: no walking goals; it turns on whoever strikes it, and picks its fights itself (fight())
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_SEEP, (byte) 0);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        if (socket == null) root(blockPosition(), getYRot());
        setFlag(F_DORMANT, true);
        sync(ASLEEP, 0, false);
        return out;
    }

    /** The body never turns: the crown does (its look bone). */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {}
        };
    }

    @Override
    public int getMaxHeadYRot() {
        return 175;
    }

    @Override
    public int getHeadRotSpeed() {
        return 6;
    }

    /** Solid as the rock it grew from: you can't walk through it. */
    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return isAlive();
    }

    @Override
    public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
        // rooted
    }

    @Override
    protected boolean registered() {
        return false;
    }

    @Override
    protected int deathTicks() {
        return DEATH_HOLD;
    }

    // ---- state ---------------------------------------------------------------------------------------------------------

    public int state() {
        return entityData.get(DATA_SEEP) & 3;
    }

    int syncedPhase() {
        return (entityData.get(DATA_SEEP) >> 2) & 3;
    }

    boolean dimmed() {
        return (entityData.get(DATA_SEEP) & 16) != 0;
    }

    private void sync(int state, int phase, boolean dim) {
        byte v = (byte) (state | Math.min(3, phase) << 2 | (dim ? 16 : 0));
        if (entityData.get(DATA_SEEP) != v) entityData.set(DATA_SEEP, v);
    }

    private void sync() {
        sync(state(), phase(), dimTicks > 0);
    }

    Vec3 core() {
        return position().add(0, CORE, 0);
    }

    // ---- ticking -------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        float yaw = level().isClientSide() ? getYRot() : rootYaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        if (level().isClientSide()) {
            seepClient();
            return;
        }
        setYRot(rootYaw);
        if (socket != null && isAlive()) {
            // rooted: whatever moved it (a blast, a pull), it is back in its socket
            double x = socket.getX() + 0.5, y = socket.getY(), z = socket.getZ() + 0.5;
            if (Math.abs(getX() - x) + Math.abs(getY() - y) + Math.abs(getZ() - z) > 0.02) {
                setPos(x, y, z);
                setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (state() == ASLEEP && (tickCount + getId()) % 5 == 0) {
            Player p = nearest(level, WAKE_RANGE, 7);
            if (p != null) beginWaking(level, p);
        }
        super.customServerAiStep(level);
        switch (state()) {
            case WAKING -> {
                if (--wakeTicks <= 0) finishWaking(level);
            }
            case AWAKE -> {
                if ((tickCount + getId()) % 10 == 0) fight(level);
                LivingEntity t = getTarget();
                if (t != null && (abilities == null || abilities.active() == null)) getLookControl().setLookAt(t, 6.0F, 30.0F);
                if (callIn > 0 && --callIn == 0 && abilities != null && Mobs.minionRoom(level, this, 2)) abilities.start(level, CALL);
            }
            default -> {
                if ((tickCount + getId()) % 20 == 0) rest(level);
            }
        }
        if (dimTicks > 0 && --dimTicks == 0) sync();
        if (flareTicks > 0) flareTicks--;
        if (phase() != syncedPhase()) sync();
        light(level);
    }

    /** It keeps no territory to warn about: it sleeps, wakes and fights (customServerAiStep). */
    @Override
    protected void territory(ServerLevel level) {}

    /** The nearest player it can fight within {@code range} (and {@code dy} up or down), or null. */
    private @Nullable Player nearest(ServerLevel level, double range, double dy) {
        Player best = null;
        double bestD = range * range;
        for (ServerPlayer p : level.players()) {
            if (!fair(p) || Math.abs(p.getY() - getY()) > dy) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean fair(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    private void beginWaking(ServerLevel level, @Nullable Player p) {
        if (state() != ASLEEP || !isAlive()) return;
        wakeTicks = WAKE_TICKS;
        waker = p == null ? null : p.getUUID();
        sync(WAKING, phase(), false);
        cueRoar(CUE_ROAR);
        sound("roar", 2.4F, 1.0F);
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(this) < 32 * 32) sp.sendOverlayMessage(Component.literal(def().name + " wakes.").withColor(def().glow));
        }
    }

    private void finishWaking(ServerLevel level) {
        sync(AWAKE, phase(), false);
        setFlag(F_DORMANT, false);
        calm = 0;
        Player p = waker == null ? null : level.getPlayerByUUID(waker);
        if (p == null || !fair(p) || p.distanceToSqr(this) > LEASH * LEASH) p = nearest(level, FIGHT_RANGE, 8);
        if (p != null) setTarget(p);
        waker = null;
    }

    /** Twice a second while awake: let go of a target out of reach, take the nearest, sleep when there is nobody. */
    private void fight(ServerLevel level) {
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || (t instanceof Player p && !fair(p)) || t.distanceToSqr(this) > LEASH * LEASH || Math.abs(t.getY() - getY()) > 10)) {
            setTarget(null);
            t = null;
        }
        if (t == null) {
            Player p = nearest(level, FIGHT_RANGE, 8);
            if (p != null) setTarget(p);
        }
        if (getTarget() != null) calm = 0;
        else if ((calm += 10) >= SLEEP_AFTER) fallAsleep(level);
    }

    private void fallAsleep(ServerLevel level) {
        if (abilities != null) abilities.interrupt();
        forgetCue();
        setTarget(null);
        setFlag(F_DORMANT, true);
        callIn = 0;
        sync(ASLEEP, phase(), false);
        sound("ambient", 1.2F, 0.8F);
    }

    /** Asleep: it heals (2% a second) and, whole again, forgets the fight's phases. */
    private void rest(ServerLevel level) {
        if (getHealth() < getMaxHealth()) heal(getMaxHealth() * 0.02F);
        else resetPhase(level);
    }

    /** A sleeping heart woken by AwakenMob's dormancy (a player very near, or a blow): the moment, never a plain wake. */
    @Override
    protected void wake(ServerLevel level) {
        if (state() == ASLEEP) beginWaking(level, nearest(level, WAKE_RANGE, 7));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (state() == ASLEEP && isAlive() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            beginWaking(level, source.getEntity() instanceof Player p && fair(p) ? p : nearest(level, WAKE_RANGE, 7));
        }
        return super.hurtServer(level, source, damage);
    }

    // ---- phases ----------------------------------------------------------------------------------------------------------

    /** A new phase opens with the call: the Hollows claw out of the walls a moment after its cry. */
    @Override
    protected void onPhase(ServerLevel level, int phase) {
        if (phase >= 1) callIn = 30;
        sync();
    }

    @Override
    protected void phaseFx(ServerLevel level) {
        Vec3 c = core();
        send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.05, getZ(), 7.0, 16, 0);
        level.sendParticles(MobParticles.SEEP_MOTE.get(), c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.25);
        level.sendParticles(MobParticles.SEEP_SHARD.get(), c.x, c.y, c.z, 10, 0.4, 0.3, 0.4, 0.3);
        flareTicks = 24;
    }

    // ---- its light: a light block at the core, only ever into air ----------------------------------------------------------

    private int wantLight() {
        if (isDeadOrDying()) return deathAge < 24 ? 13 : deathAge < 40 ? 8 : deathAge < 52 ? 4 : 0;
        return switch (state()) {
            case ASLEEP -> 5;
            case WAKING -> 13;
            default -> dimTicks > 0 ? 2 : flareTicks > 0 ? 14 : 10 + Math.min(2, phase());
        };
    }

    private void light(ServerLevel level) {
        int want = wantLight();
        if (want == lightHave) return;
        lightHave = want;
        BlockPos at = socket().above();
        BlockState s = level.getBlockState(at);
        if (!s.isAir() && !s.is(Blocks.LIGHT)) return;
        level.setBlock(at, want <= 0 ? Blocks.AIR.defaultBlockState() : Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, want), Block.UPDATE_ALL);
        lightPos = want <= 0 ? null : at;
    }

    private void clearLight(ServerLevel level) {
        if (lightPos != null && level.getBlockState(lightPos).is(Blocks.LIGHT)) level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        lightPos = null;
        lightHave = 0;
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level() instanceof ServerLevel sl) {
            deathAge++;
            light(sl);
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (reason.shouldDestroy() && level() instanceof ServerLevel sl) clearLight(sl);
    }

    // ---- its blows: told and landed in its own look (the runner chooses, times and classes them) -------------------------

    @Override
    protected boolean abilityMarks(ServerLevel level, Ability a, LivingEntity target, List<Vec3> marks) {
        switch (a.kind()) {
            case RAIN -> rootMarks(level, a, target, marks);
            case SUMMON -> wallSpots(level, Math.max(1, a.count()), marks);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Under each player in reach first (where you stand), then the target, then the chamber's floor round it. */
    private void rootMarks(ServerLevel level, Ability a, LivingEntity target, List<Vec3> marks) {
        int n = Math.max(1, a.count());
        double r2 = a.range() * a.range();
        for (ServerPlayer p : level.players()) {
            if (marks.size() >= n) break;
            if (!Abilities.isEnemy(this, p) || p.distanceToSqr(this) > r2) continue;
            Vec3 f = floorUnder(level, p.position());
            if (f != null) marks.add(f);
        }
        if (marks.size() < n && !(target instanceof Player)) {
            Vec3 f = floorUnder(level, target.position());
            if (f != null) marks.add(f);
        }
        Vec3 c = position();
        for (int guard = 0; marks.size() < n && guard < n * 8; guard++) {
            float ang = random.nextFloat() * Mth.TWO_PI, r = 2.6F + random.nextFloat() * 3.0F;
            Vec3 f = floorUnder(level, new Vec3(c.x + Mth.cos(ang) * r, c.y + 1, c.z + Mth.sin(ang) * r));
            if (f != null && apart(f, marks, 2.2)) marks.add(f);
        }
    }

    /** Spots at the foot of the chamber's walls (a Hollow claws out of the rock there); the open floor if none. */
    private void wallSpots(ServerLevel level, int count, List<Vec3> marks) {
        List<BlockPos> walls = new ArrayList<>(count);
        Vec3 c = position();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int tries = 0; marks.size() < count && tries < 16; tries++) {
            float ang = random.nextFloat() * Mth.TWO_PI;
            double dx = Mth.cos(ang), dz = Mth.sin(ang);
            Vec3 last = null;
            BlockPos wall = null;
            for (double r = 2.5; r <= 9.0; r += 0.5) {
                m.set(c.x + dx * r, c.y + 1.2, c.z + dz * r);
                if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                    wall = m.immutable();
                    break;
                }
                last = new Vec3(c.x + dx * r, c.y + 1, c.z + dz * r);
            }
            if (wall == null || last == null || last.distanceToSqr(c.x, last.y, c.z) < 3.0 * 3.0) continue;
            Vec3 f = floorUnder(level, last);
            if (f == null || !apart(f, marks, 2.0)) continue;
            marks.add(f);
            walls.add(wall);
        }
        for (int guard = 0; marks.size() < count && guard < 12; guard++) {
            float ang = random.nextFloat() * Mth.TWO_PI;
            Vec3 f = floorUnder(level, new Vec3(c.x + Mth.cos(ang) * 4, c.y + 1, c.z + Mth.sin(ang) * 4));
            if (f != null) {
                marks.add(f);
                walls.add(BlockPos.containing(f).below());
            }
        }
        callWalls = walls;
    }

    private static boolean apart(Vec3 p, List<Vec3> others, double d) {
        for (Vec3 o : others) if (o.distanceToSqr(p) < d * d) return false;
        return true;
    }

    /** The top of the floor under a point (within 4 blocks down), with room above it; null over a drop. */
    private static @Nullable Vec3 floorUnder(ServerLevel level, Vec3 p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(p.x), Mth.floor(p.y + 0.5), Mth.floor(p.z));
        for (int k = 0; k < 5; k++, m.move(Direction.DOWN)) {
            BlockState s = level.getBlockState(m);
            if (!s.isFaceSturdy(level, m, Direction.UP)) continue;
            BlockPos up = m.above();
            if (!level.getBlockState(up).getCollisionShape(level, up).isEmpty()) return null;
            return new Vec3(p.x, m.getY() + 1.0, p.z);
        }
        return null;
    }

    @Override
    protected boolean abilityTell(ServerLevel level, Ability a, int left, int total, Vec3 aim, List<Vec3> marks) {
        boolean first = left == total;
        Vec3 c = core();
        switch (a.name()) {
            case SHARDS, STORM -> {
                // the light gathers into the core
                if ((left & 1) == 0) drawIn(level, c, a.name().equals(STORM) ? 3 : 2, 1.8);
            }
            case ROOTFALL -> {
                // cracks of light open under you; motes and grit rise from them; the rock groans
                if (first) {
                    for (Vec3 m : marks) send(level, MobParticles.SEEP_CRACK.get(), m.x, m.y + 0.02, m.z, a.radius() + 0.3, total + 4, 0);
                    for (Vec3 m : marks) soundAt(level, m, "rumble", 1.1F, 0.95F + random.nextFloat() * 0.1F);
                }
                if (left % 3 == 0) {
                    for (Vec3 m : marks) {
                        level.sendParticles(MobParticles.SEEP_MOTE.get(), m.x, m.y + 0.1, m.z, 2, a.radius() * 0.45, 0.05, a.radius() * 0.45, 0.02);
                        grit(level, m, 2, a.radius() * 0.4F);
                    }
                }
            }
            case LASH -> {
                // a ring of light round it marks the reach of the arms; motes rise along it
                if (first) send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.04, getZ(), a.radius(), total + 2, 1);
                if (left % 3 == 0) ring(level, a.radius() * 0.85F, 3);
            }
            case PULSE -> {
                // the reach of the pulse is drawn on the floor while the room's light is drawn into the shut bud
                if (first) send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.04, getZ(), a.radius(), total + 2, 1);
                if ((left & 1) == 0) drawIn(level, c, 3, 5.0);
            }
            case BURST -> {
                // two rings: the burst's reach, and the calm inside it you dodge into; the ground between them glows
                if (first) {
                    send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.04, getZ(), a.radius(), total + 2, 1);
                    send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.05, getZ(), a.inner(), total + 2, 1);
                }
                if ((left & 1) == 0) {
                    drawIn(level, c, 2, 1.4);
                    for (int i = 0; i < 3; i++) {
                        float ang = random.nextFloat() * Mth.TWO_PI, r = a.inner() + random.nextFloat() * (a.radius() - a.inner());
                        level.sendParticles(MobParticles.SEEP_MOTE.get(), getX() + Mth.cos(ang) * r, getY() + 0.15, getZ() + Mth.sin(ang) * r, 1, 0.1, 0.05, 0.1, 0.01);
                    }
                }
            }
            case CALL -> {
                // its cry to the walls; the rock where they will come out cracks and leaks light
                if (first) sound("call", 2.0F, 1.0F);
                if (left % 3 == 0 && callWalls != null) {
                    for (int i = 0; i < callWalls.size() && i < marks.size(); i++) {
                        BlockPos w = callWalls.get(i);
                        Vec3 face = facing(w, marks.get(i));
                        BlockState s = level.getBlockState(w);
                        if (!s.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), face.x, face.y, face.z, 3, 0.25, 0.4, 0.25, 0.05);
                        level.sendParticles(MobParticles.SEEP_MOTE.get(), face.x, face.y, face.z, 1, 0.2, 0.4, 0.2, 0.02);
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean abilityStrike(ServerLevel level, Ability a, @Nullable LivingEntity target, Vec3 aim, List<Vec3> marks) {
        Vec3 c = core();
        switch (a.name()) {
            case SHARDS -> {
                Vec3 dir = aim.subtract(c);
                if (dir.lengthSqr() < 1.0E-4) dir = getLookAngle();
                dir = dir.normalize();
                int n = Math.max(1, a.count());
                for (int i = 0; i < n; i++) {
                    float ang = (i - (n - 1) / 2.0F) * 0.13F;
                    Vec3 d = dir.yRot(ang).add(0, (random.nextFloat() - 0.5F) * 0.08F, 0);
                    AwBolt.shoot(level, this, c.add(d.scale(0.9)), d, BoltKind.DEEP, Abilities.damage(this, a), Abilities.effect(a), 0.95);
                }
                level.sendParticles(MobParticles.SEEP_SHARD.get(), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.25);
                sound("shards", 1.4F, 1.0F);
            }
            case STORM -> {
                int n = Math.max(6, a.count());
                float off = random.nextFloat() * Mth.TWO_PI;
                for (int i = 0; i < n; i++) {
                    float ang = off + i * Mth.TWO_PI / n;
                    Vec3 d = new Vec3(Mth.cos(ang), 0.03 + (i % 2) * 0.07, Mth.sin(ang));
                    AwBolt.shoot(level, this, c.add(d.scale(0.9)), d, BoltKind.DEEP, Abilities.damage(this, a), null, 0.7);
                }
                level.sendParticles(MobParticles.SEEP_SHARD.get(), c.x, c.y, c.z, 18, 0.4, 0.3, 0.4, 0.35);
                send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.05, getZ(), 4.5, 10, 0);
                sound("shards", 1.8F, 0.8F);
            }
            case ROOTFALL -> {
                for (Vec3 m : marks) rootBurst(level, a, m);
            }
            case LASH -> {
                Abilities.area(level, this, a, position(), a.radius(), 0, 0.9);
                float off = random.nextFloat() * Mth.TWO_PI;
                for (int i = 0; i < 7; i++) {
                    float ang = off + i * Mth.TWO_PI / 7;
                    Vec3 f = floorUnder(level, position().add(Mth.cos(ang) * 2.3, 1, Mth.sin(ang) * 2.3));
                    if (f != null) SeepRoot.spawn(level, f, ang * Mth.RAD_TO_DEG + 90.0F, 0.85F);
                }
                send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.05, getZ(), a.radius() + 0.4, 10, 0);
                sound("roots", 1.5F, 0.85F);
            }
            case PULSE -> {
                for (LivingEntity e : Abilities.enemiesNear(level, this, position(), a.radius())) {
                    Abilities.push(e, e.position().subtract(position()), 1.0 + a.power() * 1.5, 0.45);
                    Abilities.hurt(level, this, e, Abilities.damage(this, a), BoltKind.DEEP, Abilities.effect(a));
                }
                send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.05, getZ(), a.radius(), 12, 0);
                level.sendParticles(MobParticles.SEEP_MOTE.get(), c.x, c.y, c.z, 30, 0.4, 0.4, 0.4, 0.45);
                dimTicks = 70; // the light goes down with the pulse, and comes back
                sync();
                sound("pulse", 2.0F, 1.0F);
            }
            case BURST -> {
                Abilities.area(level, this, a, position(), a.radius(), a.inner(), 0.6);
                for (float r = a.inner(); r <= a.radius() + 0.01F; r += (a.radius() - a.inner()) / 2) {
                    send(level, MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.05, getZ(), r, 9, 0);
                }
                level.sendParticles(MobParticles.SEEP_SHARD.get(), c.x, c.y, c.z, 24, 0.5, 0.3, 0.5, 0.45);
                level.sendParticles(MobParticles.SEEP_MOTE.get(), c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.5);
                flareTicks = 16;
                sound("pulse", 2.2F, 0.75F);
                sound("shards", 1.6F, 0.7F);
            }
            case CALL -> {
                MobDef d = a.summon() == null ? null : Bestiary.get(a.summon());
                for (int i = 0; i < marks.size(); i++) {
                    Vec3 p = marks.get(i);
                    if (d != null) Mobs.spawnMinion(level, d, p, this);
                    BlockPos w = callWalls != null && i < callWalls.size() ? callWalls.get(i) : BlockPos.containing(p).below();
                    Vec3 face = facing(w, p);
                    BlockState s = level.getBlockState(w);
                    if (!s.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), face.x, face.y, face.z, 26, 0.3, 0.5, 0.3, 0.15);
                    level.sendParticles(MobParticles.SEEP_SHARD.get(), face.x, face.y, face.z, 8, 0.3, 0.4, 0.3, 0.2);
                    level.sendParticles(MobParticles.SEEP_MOTE.get(), face.x, face.y, face.z, 10, 0.3, 0.5, 0.3, 0.1);
                    soundAt(level, p, "roots", 1.2F, 1.25F);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Roots burst from the floor at a mark: they strike whoever still stands there and throw them up. */
    private void rootBurst(ServerLevel level, Ability a, Vec3 m) {
        SeepRoot.spawn(level, m, random.nextFloat() * 360.0F, 1.0F);
        for (LivingEntity e : Abilities.enemiesNear(level, this, m, a.radius())) {
            Abilities.hurt(level, this, e, Abilities.damage(this, a), BoltKind.DEEP, Abilities.effect(a));
            Abilities.push(e, e.position().subtract(m), 0.25, 0.55);
        }
        grit(level, m, 16, a.radius() * 0.4F);
        level.sendParticles(MobParticles.SEEP_SHARD.get(), m.x, m.y + 0.3, m.z, 8, 0.3, 0.2, 0.3, 0.25);
        soundAt(level, m, "roots", 1.3F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** The middle of the wall block's face toward a spot (where a Hollow comes out). */
    private static Vec3 facing(BlockPos w, Vec3 spot) {
        Vec3 c = Vec3.atCenterOf(w);
        Vec3 d = new Vec3(spot.x - c.x, 0, spot.z - c.z);
        if (d.lengthSqr() < 1.0E-4) return c.add(0, 0.5, 0);
        d = d.normalize().scale(0.55);
        return c.add(d.x, 0, d.z);
    }

    /** Motes drawn from round a point into it (they arrive as they fade). */
    private void drawIn(ServerLevel level, Vec3 to, int n, double r) {
        for (int i = 0; i < n; i++) {
            double yaw = random.nextDouble() * Math.PI * 2, pitch = (random.nextDouble() - 0.35) * 1.4;
            double rr = r * (0.75 + random.nextDouble() * 0.5);
            double x = to.x + Math.cos(yaw) * Math.cos(pitch) * rr, y = to.y + Math.sin(pitch) * rr, z = to.z + Math.sin(yaw) * Math.cos(pitch) * rr;
            send(level, MobParticles.SEEP_DRAIN.get(), x, y, z, (to.x - x) / 16.0, (to.y - y) / 16.0, (to.z - z) / 16.0);
        }
    }

    private void ring(ServerLevel level, float r, int n) {
        for (int i = 0; i < n; i++) {
            float ang = random.nextFloat() * Mth.TWO_PI;
            level.sendParticles(MobParticles.SEEP_MOTE.get(), getX() + Mth.cos(ang) * r, getY() + 0.15, getZ() + Mth.sin(ang) * r, 1, 0.1, 0.05, 0.1, 0.01);
        }
    }

    /** Grit of the floor under a point, thrown up. */
    private void grit(ServerLevel level, Vec3 m, int n, float spread) {
        BlockPos below = BlockPos.containing(m.x, m.y - 0.5, m.z);
        BlockState s = level.getBlockState(below);
        if (s.isAir()) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), m.x, m.y + 0.1, m.z, n, spread, 0.1, spread, 0.15);
    }

    /** One particle whose speed carries its numbers (MobParticles). */
    private static void send(ServerLevel level, ParticleOptions p, double x, double y, double z, double xa, double ya, double za) {
        level.sendParticles(p, x, y, z, 0, xa, ya, za, 1.0);
    }

    private void sound(String kind, float volume, float pitch) {
        SoundEvent s = MobSounds.fauna(def().id, kind);
        if (s != null) playLevelSound(s, volume, pitch);
    }

    private void soundAt(ServerLevel level, Vec3 at, String kind, float volume, float pitch) {
        SoundEvent s = MobSounds.fauna(def().id, kind);
        if (s != null) level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, volume, pitch);
    }

    // ---- the client: its beat, its wake, its death ------------------------------------------------------------------------

    private void seepClient() {
        int st = state();
        if (st != seenState) {
            if (seenState == ASLEEP && st == WAKING) {
                wakeStart = tickCount;
                wakeBurst();
            }
            seenState = st;
            nextBeat = Math.min(nextBeat, tickCount + 3);
        }
        if (isDeadOrDying()) {
            deathFx();
            return;
        }
        if (tickCount >= nextBeat) beat(st);
        if (st != ASLEEP && tickCount % 5 == 0) {
            // light rising off the open plates
            Vec3 c = core();
            float ang = random.nextFloat() * Mth.TWO_PI;
            double r = 0.3 + random.nextDouble() * 0.5;
            level().addParticle(MobParticles.SEEP_MOTE.get(), c.x + Mth.cos(ang) * r, c.y + 0.3, c.z + Mth.sin(ang) * r, 0, 0.02, 0);
        }
    }

    /** One heartbeat: the light swells (look()), the core thumps (the rig's beat clip), a sound, a few motes. */
    private void beat(int st) {
        beatAt = tickCount;
        nextBeat = tickCount + switch (st) {
            case ASLEEP -> 70;
            case WAKING -> 9;
            default -> 30 - 6 * syncedPhase();
        };
        SoundEvent s = MobSounds.fauna(def().id, "beat");
        if (s != null && !isSilent()) {
            level().playLocalSound(getX(), getY() + CORE, getZ(), s, getSoundSource(), st == ASLEEP ? 0.45F : 0.9F,
                    (st == ASLEEP ? 0.88F : 1.0F) + 0.05F * syncedPhase(), false);
        }
        Vec3 c = core();
        int n = st == ASLEEP ? 1 : 3;
        for (int i = 0; i < n; i++) {
            float ang = random.nextFloat() * Mth.TWO_PI;
            level().addParticle(MobParticles.SEEP_MOTE.get(), c.x, c.y, c.z, Mth.cos(ang) * 0.06, 0.02 + random.nextFloat() * 0.04, Mth.sin(ang) * 0.06);
        }
    }

    /** The wake: light bursts out of the opening bud and a ring runs out across the floor. */
    private void wakeBurst() {
        Vec3 c = core();
        for (int i = 0; i < 36; i++) {
            double yaw = random.nextDouble() * Math.PI * 2, pitch = (random.nextDouble() - 0.3) * 1.2, v = 0.18 + random.nextDouble() * 0.2;
            level().addParticle(MobParticles.SEEP_MOTE.get(), c.x, c.y, c.z, Math.cos(yaw) * Math.cos(pitch) * v, Math.sin(pitch) * v, Math.sin(yaw) * Math.cos(pitch) * v);
        }
        for (int i = 0; i < 12; i++) {
            level().addParticle(MobParticles.SEEP_SHARD.get(), c.x, c.y + 0.3, c.z, (random.nextDouble() - 0.5) * 0.3, 0.2 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
        }
        level().addParticle(MobParticles.SEEP_WAVE.get(), getX(), getY() + 0.05, getZ(), 6.0, 18, 0);
    }

    /**
     * Its death: for a second and a half the violet drains out of the room into the core (motes stream in from the walls),
     * two last slow beats, then as the plates shut a breath of dark light leaks out of the closed bud.
     */
    private void deathFx() {
        int age = dyingTicks();
        Vec3 c = core();
        if (age < 30) {
            for (int i = 0; i < 4; i++) {
                double yaw = random.nextDouble() * Math.PI * 2, pitch = (random.nextDouble() - 0.4) * 1.1, r = 4.5 + random.nextDouble() * 1.8;
                double x = c.x + Math.cos(yaw) * Math.cos(pitch) * r, y = c.y + Math.sin(pitch) * r, z = c.z + Math.sin(yaw) * Math.cos(pitch) * r;
                level().addParticle(MobParticles.SEEP_DRAIN.get(), x, y, z, (c.x - x) / 16.0, (c.y - y) / 16.0, (c.z - z) / 16.0);
            }
        }
        if (age == 4 || age == 18) {
            beatAt = tickCount;
            SoundEvent s = MobSounds.fauna(def().id, "beat");
            if (s != null) level().playLocalSound(getX(), getY() + CORE, getZ(), s, getSoundSource(), age == 4 ? 0.8F : 0.5F, age == 4 ? 0.85F : 0.7F, false);
        }
        if (age >= 40 && age < 58 && (age & 1) == 0) {
            level().addParticle(MobParticles.SEEP_MOTE.get(), c.x + (random.nextDouble() - 0.5) * 0.4, c.y + 0.4, c.z + (random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.02, 0.025 + random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.02);
        }
    }

    /** No vanilla poof when it goes: the last of its light lifts off the dark bud. */
    @Override
    public void makePoofParticles() {
        Vec3 c = core();
        for (int i = 0; i < 10; i++) {
            level().addParticle(MobParticles.SEEP_MOTE.get(), c.x + (random.nextDouble() - 0.5) * 0.8, c.y + random.nextDouble() * 0.6, c.z + (random.nextDouble() - 0.5) * 0.8,
                    0, 0.015 + random.nextDouble() * 0.02, 0);
        }
    }

    @Override
    public float beatTime(float partialTicks) {
        float t = (tickCount - beatAt + partialTicks) / 20.0F;
        return t >= 0 && t < 0.6F ? t : -1.0F;
    }

    /** A heartbeat's light: lub at once, dub a quarter second later. */
    static float beatGlow(float ticks) {
        if (ticks < 0) return 0.0F;
        float lub = (float) Math.exp(-ticks / 2.5F);
        float d = ticks - 4.5F;
        float dub = d < 0 ? 0.0F : 0.6F * (float) Math.exp(-d / 3.0F);
        return Math.min(1.0F, lub + dub);
    }

    @Override
    public int wildState() {
        return !isDeadOrDying() && state() == ASLEEP ? S_SLEEP : 0;
    }

    @Override
    public AwLook look(float partialTicks) {
        AwLook l = super.look(partialTicks);
        l.ownGlow = true;
        float beat = beatGlow(tickCount - beatAt + partialTicks);
        int st = state();
        float g;
        if (isDeadOrDying()) {
            // brighter as the room's violet drains into it, then out as the plates shut
            float t = (dyingTicks() + partialTicks) / 20.0F;
            g = t < 1.3F ? 0.75F + 0.25F * t / 1.3F + 0.15F * beat : Math.max(0.0F, 1.0F - (t - 1.3F) / 1.6F);
        } else if (st == ASLEEP) {
            g = 0.16F + 0.24F * beat;
        } else if (st == WAKING) {
            g = 0.25F + 0.75F * Mth.clamp((tickCount - wakeStart + partialTicks) / WAKE_TICKS, 0.0F, 1.0F) + 0.15F * beat;
        } else {
            g = 0.58F + 0.06F * syncedPhase() + 0.34F * beat;
            if (l.telegraph > 0) g = Math.max(g, 0.7F + 0.3F * (0.5F + 0.5F * Mth.sin((tickCount + partialTicks) * 1.3F)));
        }
        if (dimmed() && !isDeadOrDying()) g *= 0.35F;
        l.glow = Mth.clamp(g, 0.0F, 1.0F);
        return l;
    }

    // ---- save ----------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("awaken_seep_schema", 1);
        if (socket != null) out.store("awaken_seep_socket", BlockPos.CODEC, socket);
        out.putFloat("awaken_seep_yaw", rootYaw);
        out.putInt("awaken_seep_state", state() == ASLEEP ? ASLEEP : AWAKE); // a wake in progress loads awake
        if (lightPos != null) out.store("awaken_seep_light", BlockPos.CODEC, lightPos);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        socket = in.read("awaken_seep_socket", BlockPos.CODEC).orElse(null);
        rootYaw = in.getFloatOr("awaken_seep_yaw", getYRot());
        lightPos = in.read("awaken_seep_light", BlockPos.CODEC).orElse(null);
        int st = in.getIntOr("awaken_seep_state", ASLEEP) == AWAKE ? AWAKE : ASLEEP;
        setFlag(F_DORMANT, st == ASLEEP);
        sync(st, phase(), false);
        setNoGravity(true);
    }
}
