package dev.awaken.citizens;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import static dev.awaken.citizens.CitizensData.INSIDE;
import static dev.awaken.citizens.CitizensData.STAND;
import static dev.awaken.citizens.CitizensData.WALK;

/**
 * A citizen's body near a player (SPEC §16.5, after spike S4). No goals and never saved: it walks its ledger record's
 * route edge by edge with the move control along keep-right lanes, always rejoins the street node before the next
 * edge, uses vanilla pathing only for the last few blocks (onto a spot that needs it) or to get unstuck, and thinks
 * less often the further it is from the nearest player. Hurt, never killed (SPEC §16.0.7): at the last heart it
 * stays up and limps to the infirmary.
 */
public class CitizenBody extends PathfinderMob implements Npc {
    static final EntityDataAccessor<Integer> DATA_REC = SynchedEntityData.defineId(CitizenBody.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(CitizenBody.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(CitizenBody.class, EntityDataSerializers.BYTE);
    /** Flags: fading out (leaving view or going in), carrying a crate on the back, hurt. */
    public static final int F_FADING = 1, F_CRATE = 2, F_HURT = 4, F_BAG = 8;
    static final double BASE_SPEED = 0.25;
    private static final double ARRIVE = 0.5 * 0.5, DOOR_ARRIVE = 1.05 * 1.05;
    static final int FADE_TICKS = 14;

    int rec = -1;
    private int thinkAt;
    private int fadeOut;
    // final approach and unstick with a vanilla path
    private boolean hasToken, straightFallback;
    private int tokenWait;
    private int pathPlace = -1;
    // progress
    private double refX, refZ;
    private int refTick;
    private boolean stuck;
    private int stuckSince;
    /** Set by the think: the nearest player, squared distance (for greetings and looks). */
    double nearestSq = Double.MAX_VALUE;
    int lastAct = -1;
    /** A brief gesture over whatever the body does (a wave, a nod), until tickCount reaches gestureUntil. */
    Act gesture = Act.NONE;
    int gestureUntil;
    /** Someone it's looking at for a while (a greeting). */
    net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player lookTarget;
    int lookUntil;
    /** A heading an event wants it to face while standing (NaN: the spot's). */
    float faceYaw = Float.NaN;
    // client: the clip playing since when, the one before it (crossfade), and when a fade-out began
    public int cAct, cStart, pAct, pStart, fadeStart = -1;
    public static final float FADE_TICKS_CLIENT = 8f;
    // client: what it's saying (wrapped once when it arrives) and until when
    public java.util.@org.jspecify.annotations.Nullable List<net.minecraft.util.FormattedCharSequence> speech;
    public int speechStart, speechUntil, speechKind;
    /** The hand lantern a bearer carries, on the client (citizens.client.LanternHold: its model state, its flame). */
    public Object clientLantern;
    /** What a citizen works with in hand on the client (citizens.client.HeldProp: a rope, a net, a parcel). */
    public Object clientHeld;
    public net.minecraft.util.@org.jspecify.annotations.Nullable FormattedCharSequence label;
    public float labelWidth;
    /** The client's life layer for this body (mobs.client.life.Life; an Object so common code never names a client class). */
    public @org.jspecify.annotations.Nullable Object clientLife;

    public CitizenBody(EntityType<? extends CitizenBody> type, Level level) {
        super(type, level);
        getNavigation().setCanFloat(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_REC, -1);
        b.define(DATA_ACT, 0);
        b.define(DATA_FLAGS, (byte) 0);
    }

    public int record() {
        return entityData.get(DATA_REC);
    }

    public Act act() {
        return Act.of(entityData.get(DATA_ACT));
    }

    public int flags() {
        return entityData.get(DATA_FLAGS);
    }

    public boolean has(int flag) {
        return (entityData.get(DATA_FLAGS) & flag) != 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide()) return;
        if (DATA_ACT.equals(key)) {
            int a = entityData.get(DATA_ACT);
            if (a != cAct) {
                pAct = cAct;
                pStart = cStart;
                cAct = a;
                cStart = tickCount;
            }
        } else if (DATA_FLAGS.equals(key)) {
            if (has(F_FADING) && fadeStart < 0) fadeStart = tickCount;
        }
    }

    void setFlag(int flag, boolean on) {
        int f = entityData.get(DATA_FLAGS);
        int nf = on ? f | flag : f & ~flag;
        if (nf != f) entityData.set(DATA_FLAGS, (byte) nf);
    }

    void setAct(Act a) {
        if (entityData.get(DATA_ACT) != a.ordinal()) entityData.set(DATA_ACT, a.ordinal());
    }

    void bind(int rec, float scale) {
        this.rec = rec;
        entityData.set(DATA_REC, rec);
        AttributeInstance s = getAttribute(Attributes.SCALE);
        if (s != null) s.setBaseValue(scale);
        thinkAt = 0;
        refTick = tickCount;
        refX = getX();
        refZ = getZ();
    }

    private int heldMain = -1, heldOff = -1;

    int heldOff() {
        return heldOff;
    }

    /** Holds a prop (Props code) in a hand; only a change touches the equipment. */
    void hold(EquipmentSlot slot, int prop) {
        if (slot == EquipmentSlot.MAINHAND) {
            if (prop == heldMain) return;
            heldMain = prop;
        } else {
            if (prop == heldOff) return;
            heldOff = prop;
        }
        setItemSlot(slot, Props.stack(prop));
    }

    void gesture(Act a, int ticks) {
        if (a == Act.NONE) return;
        gesture = a;
        gestureUntil = tickCount + ticks;
        setAct(a);
    }

    void lookAt(net.minecraft.world.entity.player.Player p, int ticks) {
        lookTarget = p;
        lookUntil = tickCount + ticks;
        getLookControl().setLookAt(p, 40f, 40f);
    }

    boolean fading() {
        return fadeOut > 0;
    }

    /** Detached from its record: fade out where it stands (or walking on), then go. */
    void fadeAway(int ticks) {
        releasePath();
        rec = -1;
        fadeOut = Math.max(1, ticks);
        setFlag(F_FADING, true);
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (level().isClientSide()) {
            super.tick();
            Act a = Act.of(cAct);
            if (a == Act.HAMMER) {
                // the hammer meets the anvil at the clip's tick 12 (citizen_clips.py): sparks off the work
                int t = (tickCount - cStart + (record() & 7) * 3) % 22;
                if (t == 12) {
                    double yaw = Math.toRadians(yBodyRot);
                    double fx = -Math.sin(yaw), fz = Math.cos(yaw);
                    double x = getX() + fx * 0.85, y = getY() + 0.95 * getScale(), z = getZ() + fz * 0.85;
                    for (int k = 0; k < 7; k++)
                        level().addParticle(CitizensContent.FORGE_SPARK.get(), x, y, z, fx * 0.02, 0, fz * 0.02);
                }
            }
            return;
        }
        long t0 = System.nanoTime();
        super.tick();
        Crowd.bodyNanos += System.nanoTime() - t0;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (fadeOut > 0) {
            if (--fadeOut == 0) discard();
            return;
        }
        Citizens c = Citizens.active;
        if (c == null || rec < 0 || c.ledger.body[rec] != this) {
            discard();
            return;
        }
        long now = level.getGameTime();
        steer(c, c.ledger, now);
        if (rec < 0 || isRemoved() || fadeOut > 0) return;
        if (tickCount >= thinkAt) think(c, c.ledger, level, now);
    }

    /** Every tick, cheap: aim the move control at the next lane point or the spot; step the record on arrival. */
    private void steer(Citizens c, Ledger l, long now) {
        StreetNet g = l.g;
        CitizensData d = l.d;
        byte st = d.state[rec];
        if (st == INSIDE || st == CitizensData.ABSENT) {
            c.crowd.entered(this);
            return;
        }
        if (st == STAND) {
            if (d.role[rec] == 0 && d.dwellUntil[rec] > 0 && now >= d.dwellUntil[rec]) {
                l.afterDwell(rec, now);
                return;
            }
            int s = d.spot[rec];
            double tx, tz;
            if (s >= 0) {
                l.spotWorld(s, d.place[rec], l.tmp);
                tx = l.tmp[0];
                tz = l.tmp[2];
            } else if (!Double.isNaN(l.rx[rec])) {
                tx = l.tmp[0] = l.rx[rec];
                l.tmp[1] = l.ry[rec];
                tz = l.tmp[2] = l.rz[rec];
            } else {
                if (!l.position(rec, l.tmp)) return;
                tx = l.tmp[0];
                tz = l.tmp[2];
            }
            double d2 = sq(getX() - tx) + sq(getZ() - tz);
            if (d2 < 0.25 * 0.25) {
                releasePath();
                getNavigation().stop();
                if (lookTarget == null || tickCount > lookUntil) face(Float.isNaN(faceYaw) ? l.spotYaw(rec) : faceYaw);
                return;
            }
            int p = d.place[rec];
            if (p >= 0 && l.r.pPath[p] && d2 > 1.0) approach(c, l, p, tx, l.tmp[1], tz);
            else moveToward(tx, l.tmp[1], tz, l.speed(rec), d2 < 1.0 ? 0.6 : 1.0);
            return;
        }
        // walking
        int at = d.at[rec], goal = d.goal[rec];
        if (d.next[rec] < 0) {
            if (at == goal) {
                double dd = sq(getX() - g.x[at]) + sq(getZ() - g.z[at]);
                boolean door = g.kind[at] != StreetNet.JUNCTION;
                if (dd < (door ? DOOR_ARRIVE : 1.2 * 1.2) || stuck && dd < 3 * 3) {
                    releasePath();
                    l.arrive(rec, now, true);
                    if (d.state[rec] == INSIDE) c.crowd.entered(this);
                    return;
                }
                moveToward(g.x[at], g.y[at] + 1, g.z[at], l.speed(rec), 1.0);
                return;
            }
            // leaving a stand: back onto the node first, so the next edge starts in the street
            double off2 = sq(getX() - g.x[at]) + sq(getZ() - g.z[at]);
            if (off2 > 1.5 * 1.5) {
                int p = d.place[rec];
                if (p >= 0 && l.r.pPath[p] && off2 > 2.5 * 2.5) approach(c, l, -1, g.x[at], g.y[at] + 1, g.z[at]);
                else moveToward(g.x[at], g.y[at] + 1, g.z[at], l.speed(rec), 1.0);
                return;
            }
            int h = g.nextHop(at, goal);
            if (h == -2) return; // the column bakes next tick
            if (h < 0) {
                l.noRoute++;
                d.at[rec] = goal;
                return;
            }
            d.next[rec] = h;
            d.prog[rec] = 0;
        }
        int nx = d.next[rec];
        double tx = g.x[nx], tz = g.z[nx];
        float lane = g.lane(at, nx);
        if (lane > 0) {
            double dx = g.x[nx] - g.x[at], dz = g.z[nx] - g.z[at], len = Math.max(1e-6, Math.hypot(dx, dz));
            tx += -dz / len * lane;
            tz += dx / len * lane;
        }
        double reach = g.kind[nx] != StreetNet.JUNCTION ? DOOR_ARRIVE : ARRIVE;
        if (sq(getX() - tx) + sq(getZ() - tz) < reach || g.kind[nx] == StreetNet.JUNCTION && passed(g, at, nx)) {
            if (hasToken && pathPlace == -2) releasePath();
            d.at[rec] = nx;
            d.next[rec] = -1;
            d.prog[rec] = 0;
            return;
        }
        if (hasToken && pathPlace == -2) {
            if (getNavigation().isDone()) releasePath();
            return;
        }
        moveToward(tx, g.y[nx] + 1, tz, l.speed(rec), 1.0);
    }

    /** Past the next node along the edge already (a wide turn, a push): count it as reached. */
    private boolean passed(StreetNet g, int a, int b) {
        double dx = g.x[b] - g.x[a], dz = g.z[b] - g.z[a], len2 = dx * dx + dz * dz;
        if (len2 < 1e-6) return true;
        double t = ((getX() - g.x[a]) * dx + (getZ() - g.z[a]) * dz) / len2;
        return t > 1.0 && sq(getX() - g.x[b]) + sq(getZ() - g.z[b]) < 2.5 * 2.5;
    }

    private void face(float yaw) {
        float body = Mth.approachDegrees(yBodyRot, yaw, 12f);
        setYRot(body);
        setYBodyRot(body);
    }

    private void moveToward(double x, double y, double z, float speed, double scale) {
        double mod = Math.sqrt(speed * scale / 2.2) / BASE_SPEED;
        getMoveControl().setWantedPosition(x, y, z, mod);
    }

    /** The last few blocks onto a spot, or back off one, with a vanilla path (≤ 4 at once; else wait, then go straight). */
    private boolean approach(Citizens c, Ledger l, int place, double tx, double ty, double tz) {
        double d2 = sq(getX() - tx) + sq(getZ() - tz);
        if (d2 < 0.5 * 0.5) {
            releasePath();
            return true;
        }
        if (straightFallback) {
            moveToward(tx, ty, tz, l.speed(rec), 1.0);
            return false;
        }
        if (!hasToken) {
            if (c.crowd.takePath()) {
                hasToken = true;
                tokenWait = 0;
                pathPlace = place;
                long t0 = System.nanoTime();
                boolean ok = getNavigation().moveTo(tx, ty, tz, 0, Math.sqrt(l.speed(rec) / 2.2) / BASE_SPEED);
                c.crowd.pathNanos += System.nanoTime() - t0;
                c.crowd.pathRequests++;
                if (!ok) {
                    c.crowd.pathFails++;
                    releasePath();
                    straightFallback = true;
                }
            } else if (++tokenWait > 40) {
                c.crowd.tokenTimeouts++;
                straightFallback = true;
            }
            return false;
        }
        if (getNavigation().isDone()) {
            releasePath();
            straightFallback = true;
        }
        return false;
    }

    private void releasePath() {
        if (hasToken) {
            Citizens c = Citizens.active;
            if (c != null) c.crowd.releasePath();
            hasToken = false;
            getNavigation().stop();
        }
        straightFallback = false;
        pathPlace = -1;
        tokenWait = 0;
    }

    /** Throttled: progress, unsticking, what the body shows, who it looks at. Every max(1, d²/256) ticks. */
    private void think(Citizens c, Ledger l, ServerLevel level, long now) {
        long t0 = System.nanoTime();
        double best = Double.MAX_VALUE;
        ServerPlayer near = null;
        for (ServerPlayer p : level.players()) {
            double dd = p.distanceToSqr(this);
            if (dd < best) {
                best = dd;
                near = p;
            }
        }
        nearestSq = best;
        thinkAt = tickCount + Math.max(1, Math.min(40, (int) (best / 256.0)));
        c.crowd.thinks++;
        c.work.show(this, rec, now);
        if (lookTarget != null && tickCount <= lookUntil && !lookTarget.isRemoved()) getLookControl().setLookAt(lookTarget, 40f, 40f);
        else if (near != null && best < 9 * 9) {
            lookTarget = null;
            if (!c.recognition.think(this, rec, near, best, now) && best < 6 * 6 && (rec * 7 + tickCount / 100) % 3 == 0)
                getLookControl().setLookAt(near, 20f, 20f);
        }
        CitizensData d = l.d;
        boolean wantsToMove = d.state[rec] == WALK || d.state[rec] == STAND && farFromSpot(l);
        if (!wantsToMove) {
            refX = getX();
            refZ = getZ();
            refTick = tickCount;
            stuck = false;
            c.crowd.thinkNanos += System.nanoTime() - t0;
            return;
        }
        double moved2 = sq(getX() - refX) + sq(getZ() - refZ);
        if (moved2 >= 1.0) {
            refX = getX();
            refZ = getZ();
            refTick = tickCount;
            stuck = false;
        } else if (!stuck && tickCount - refTick >= 60) {
            stuck = true;
            stuckSince = tickCount;
            c.crowd.stuck++;
        } else if (stuck && tickCount - stuckSince >= 200) {
            // ten seconds and nowhere: put them on their next node, but never in front of anyone
            if (c.crowd.unseen(level, this) || tickCount - stuckSince >= 300) {
                rescue(c, l);
                c.crowd.rescues++;
                stuck = false;
                refX = getX();
                refZ = getZ();
                refTick = tickCount;
            }
        } else if (stuck && !hasToken && !straightFallback && tickCount - stuckSince >= 20 && c.crowd.takePath()) {
            // a vanilla path round whatever is in the way
            hasToken = true;
            pathPlace = -2;
            int target = d.next[rec] >= 0 ? d.next[rec] : d.goal[rec];
            long p0 = System.nanoTime();
            boolean ok = getNavigation().moveTo(l.g.x[target], l.g.y[target] + 1, l.g.z[target], 0, Math.sqrt(l.speed(rec) / 2.2) / BASE_SPEED);
            c.crowd.pathNanos += System.nanoTime() - p0;
            c.crowd.pathRequests++;
            c.crowd.unstickPaths++;
            if (!ok) {
                c.crowd.pathFails++;
                releasePath();
            }
        }
        c.crowd.thinkNanos += System.nanoTime() - t0;
    }

    private boolean farFromSpot(Ledger l) {
        int s = l.d.spot[rec];
        if (s < 0) return false;
        l.spotWorld(s, l.d.place[rec], l.tmp);
        return sq(getX() - l.tmp[0]) + sq(getZ() - l.tmp[2]) > 0.6 * 0.6;
    }

    private void rescue(Citizens c, Ledger l) {
        releasePath();
        CitizensData d = l.d;
        if (d.state[rec] == STAND && d.spot[rec] >= 0) {
            l.spotWorld(d.spot[rec], d.place[rec], l.tmp);
            teleportTo(l.tmp[0], l.tmp[1], l.tmp[2]);
            return;
        }
        int target = d.next[rec] >= 0 ? d.next[rec] : d.goal[rec];
        teleportTo(l.g.x[target], l.g.y[target] + 1, l.g.z[target]);
        d.at[rec] = target;
        d.next[rec] = -1;
        d.prog[rec] = 0;
    }

    /** The clock jumped: the ledger re-seats everyone; this body goes and one comes back where the ledger says. */
    void discardForJump() {
        Citizens c = Citizens.active;
        if (c != null) c.crowd.despawn(this, false);
    }

    /** Ledger state from the body's real position (on despawn): progress along the edge it is walking. */
    void writeBack(Ledger l) {
        if (rec < 0) return;
        CitizensData d = l.d;
        int a = d.at[rec], b = d.next[rec];
        if (b >= 0) {
            StreetNet g = l.g;
            double dx = g.x[b] - g.x[a], dz = g.z[b] - g.z[a], len = Math.max(1e-6, Math.hypot(dx, dz));
            double t = ((getX() - g.x[a]) * dx + (getZ() - g.z[a]) * dz) / len;
            d.prog[rec] = (int) Math.round(Math.clamp(t, 0, len) * 100);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        releasePath();
        super.remove(reason);
        Citizens c = Citizens.active;
        if (c != null) c.crowd.forget(this);
    }

    private static double sq(double v) {
        return v * v;
    }

    // ---- hurt, never killed ---------------------------------------------------------------------------------------

    @Override
    public void setHealth(float health) {
        if (!level().isClientSide() && health < 1f && !isRemoved()) {
            super.setHealth(1f);
            Citizens c = Citizens.active;
            if (c != null && rec >= 0) c.knockedDown(this, rec);
            return;
        }
        super.setHealth(health);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hit = super.hurtServer(level, source, damage);
        Citizens c = Citizens.active;
        if (hit && c != null && rec >= 0) c.hurt(this, rec, source);
        return hit;
    }

    @Override
    public void kill(ServerLevel level) {
        // /kill and the like: the body goes, the person lives on in the ledger
        discard();
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
    public void checkDespawn() {
        // the crowd decides who has a body
    }

    @Override
    public boolean isPushable() {
        return !fading() && super.isPushable();
    }

    @Override
    public boolean canBeCollidedWith(@org.jspecify.annotations.Nullable Entity other) {
        return false;
    }

    BlockPos feet() {
        return blockPosition();
    }
}
