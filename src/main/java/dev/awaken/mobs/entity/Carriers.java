package dev.awaken.mobs.entity;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.net.MobsNet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The carriers (SPEC §46.2): everything a creature does to you at range travels or is drawn first, and lands only where
 * it reaches. A wave rolls out from a point along the ground (jump it, dash through it, or stand inside where it
 * starts); a volley falls on rings put where you stood when it was thrown; a ground line races out along a locked line
 * (step off it); a field lies where a lob burst, harmless for its first half second. Locked lines, landing rings and a
 * pull's draw are shown here too. Each is sent once to the players near it ({@code mobs_carrier}) and run on the
 * server's clock on both sides, so the client draws exactly where the server judges. Shots are entities ({@link AwBolt}).
 * Allocation per carrier, never per tick; at most {@link #PER_OWNER} alive for one creature.
 */
public final class Carriers {
    private Carriers() {}

    public enum Kind {
        /** A ring rolling out from {@code origin}, radius {@code from} to {@code to} at {@code speed} blocks a tick. */
        WAVE,
        /** Pods thrown up from {@code origin} falling on {@code points} (rings of radius {@code width}) at {@code life}. */
        VOLLEY,
        /** A front racing from {@code origin} along {@code dir} at {@code speed} for {@code to} blocks, {@code width} each side. */
        GROUND,
        /** A locked line from {@code origin} to {@code points[0]}: its flash at the start, its fire at {@code life}. Shown only. */
        LINE,
        /** A landing ring of radius {@code width} at {@code origin} (a leap locked on it). Shown only. */
        RING,
        /** A wedge from {@code origin} along {@code dir}, {@code to} blocks long, its half angle's cosine {@code width}: a cone's lock. Shown only. */
        WEDGE,
        /** A field of radius {@code width} at {@code origin} for {@code life}, harmless for {@link #FIELD_SAFE}. Shown only (Hazards hurts). */
        FIELD,
        /** Water or wind drawn into {@code origin} from {@code to} blocks for {@code life} (a wave follows it). */
        PULL;

        public static Kind byOrdinal(int i) {
            Kind[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** A wave's crest: feet higher than this over its ground clear it (a jump's arc is over it for 7 ticks). */
    public static final float CREST = 0.75F;
    /** A wave rolls this fast (blocks a tick): from 8 blocks it shows coming for 0.6-0.9 s. */
    public static final float WAVE_SPEED = 0.55F;
    /** A volley's ring is never wider than this (a dash is 2.6), and falls this long after it shows. */
    public static final float VOLLEY_RING = 2.2F;
    public static final int VOLLEY_FALL = 16;
    /** An aimed line or leap stops following this long before it can land (I3). */
    public static final int LOCK = 10;
    /** A shot or ground line stops following this long before it leaves: its flight adds the rest. */
    public static final int LOCK_SHOT = 8;
    /** A ground line's front (blocks a tick) and half width. */
    public static final float GROUND_SPEED = 0.7F, GROUND_WIDTH = 0.7F;
    /** A field: at most this wide, harmless for its first half second. */
    public static final float FIELD_MAX = 2.0F;
    public static final int FIELD_SAFE = 10;
    /** A pull draws this fast (blocks a tick; a walk is 0.22, a dash 0.43) for this long, and hurts nothing by itself. */
    public static final float PULL_SPEED = 0.12F;
    public static final int PULL_TICKS = 20;
    /** Carriers (and shots) alive for one creature at most. */
    public static final int PER_OWNER = 24;
    /** Players this near a carrier are sent it. */
    static final double SEND_RANGE = 72;

    static final class Carrier {
        final int id;
        final Kind kind;
        final ServerLevel level;
        final @Nullable AwakenMob owner;
        final Strikes.Kind cls;
        final int theme;
        final Vec3 origin;
        final double dx, dz;
        final float from, to, speed, width;
        final long start;
        final int life;
        final List<Vec3> points;
        // what a hit does
        float damage;
        BoltKind bolt = BoltKind.PLAIN;
        @Nullable MobEffectInstance effect;
        /** A second effect the same hit carries (a root's theme on top of its hold, a heavy darkness's blindness). */
        @Nullable MobEffectInstance second;
        double knock;
        /** A volley pod that bursts into a field where it lands (a lobbed cloud): its radius, life and pulse. */
        float fieldRadius, fieldDamage;
        int fieldLife;
        final IntOpenHashSet resolved = new IntOpenHashSet(4);
        int ends;

        Carrier(Kind kind, ServerLevel level, @Nullable AwakenMob owner, Strikes.Kind cls, Vec3 origin, double dx, double dz, float from, float to,
                float speed, float width, long start, int life, List<Vec3> points) {
            this.id = ++next;
            this.kind = kind;
            this.level = level;
            this.owner = owner;
            this.cls = cls;
            this.theme = owner != null ? owner.def().glow : 0xFFFFFF;
            this.origin = origin;
            this.dx = dx;
            this.dz = dz;
            this.from = from;
            this.to = to;
            this.speed = speed;
            this.width = width;
            this.start = start;
            this.life = life;
            this.points = points;
        }

        Carrier hits(float damage, BoltKind bolt, @Nullable MobEffectInstance effect, double knock) {
            this.damage = damage;
            this.bolt = bolt;
            this.effect = effect;
            this.knock = knock;
            return this;
        }

        MobsNet.Carrier payload() {
            return new MobsNet.Carrier(id, kind.ordinal(), cls.ordinal(), theme, origin.x, origin.y, origin.z, (float) dx, (float) dz, from, to,
                    speed, width, start, life, points);
        }
    }

    private static int next;
    private static final List<Carrier> ACTIVE = new ArrayList<>();
    private static final List<Carrier> PENDING = new ArrayList<>();
    private static boolean ticking;

    // ---- making them -------------------------------------------------------------------------------------------------

    /** A wave from {@code origin}, its radius {@code from} to {@code to}: a hit on whoever's feet are under its crest as it passes. */
    static @Nullable Carrier wave(ServerLevel level, @Nullable AwakenMob owner, Vec3 origin, float from, float to, float damage, BoltKind bolt,
                                  @Nullable MobEffectInstance effect, double knock) {
        int life = Math.max(1, (int) Math.ceil((to - from) / WAVE_SPEED));
        Carrier c = new Carrier(Kind.WAVE, level, owner, Strikes.Kind.PERILOUS, origin, 0, 0, from, to, WAVE_SPEED, CREST, level.getGameTime(), life, List.of());
        return add(c.hits(damage, bolt, effect, knock));
    }

    /** A lobbed globe from {@code origin} to {@code at} (a ring of 1 block where it falls) that leaves a field there. */
    static @Nullable Carrier lob(ServerLevel level, @Nullable AwakenMob owner, Vec3 origin, Vec3 at, float damage, BoltKind bolt,
                                 @Nullable MobEffectInstance effect, float fieldRadius, int fieldLife, float fieldDamage) {
        Carrier c = volley(level, owner, origin, List.of(at), 1.0F, damage, bolt, effect);
        if (c == null) return null;
        c.fieldRadius = Math.min(FIELD_MAX, fieldRadius);
        c.fieldLife = fieldLife;
        c.fieldDamage = fieldDamage;
        return c;
    }

    /** A volley from {@code origin} falling on {@code points} ({@link #VOLLEY_FALL} ticks from now), each a ring of {@code radius}. */
    static @Nullable Carrier volley(ServerLevel level, @Nullable AwakenMob owner, Vec3 origin, List<Vec3> points, float radius, float damage,
                                    BoltKind bolt, @Nullable MobEffectInstance effect) {
        if (points.isEmpty()) return null;
        List<Vec3> pts = List.copyOf(points.subList(0, Math.min(8, points.size())));
        Carrier c = new Carrier(Kind.VOLLEY, level, owner, Strikes.Kind.PERILOUS, origin, 0, 0, 0, 0, 0, Math.min(VOLLEY_RING, radius),
                level.getGameTime(), VOLLEY_FALL, pts);
        return add(c.hits(damage, bolt, effect, 0.3));
    }

    /** A ground line from {@code origin} toward {@code aim} (flat), {@code length} blocks, as {@code cls}. */
    static @Nullable Carrier ground(ServerLevel level, @Nullable AwakenMob owner, Strikes.Kind cls, Vec3 origin, Vec3 aim, float length, float damage,
                                    BoltKind bolt, @Nullable MobEffectInstance effect) {
        double ax = aim.x - origin.x, az = aim.z - origin.z, n = Math.sqrt(ax * ax + az * az);
        if (n < 1.0E-4 && owner != null) {
            Vec3 l = owner.getLookAngle();
            ax = l.x;
            az = l.z;
            n = Math.max(1.0E-4, Math.sqrt(ax * ax + az * az));
        }
        int life = Math.max(1, (int) Math.ceil(length / GROUND_SPEED));
        Carrier c = new Carrier(Kind.GROUND, level, owner, cls, origin, ax / n, az / n, 0, length, GROUND_SPEED, GROUND_WIDTH, level.getGameTime(), life, List.of());
        return add(c.hits(damage, bolt, effect, 0.2));
    }

    /** A locked line shown from {@code from} to {@code to}, firing {@code fireIn} ticks from now (the lock's flash and sound now). */
    static @Nullable Carrier line(ServerLevel level, @Nullable AwakenMob owner, Strikes.Kind cls, Vec3 from, Vec3 to, int fireIn, float width) {
        return add(new Carrier(Kind.LINE, level, owner, cls, from, 0, 0, 0, 0, 0, width, level.getGameTime(), Math.max(1, fireIn), List.of(to)));
    }

    /** A cone's wedge from {@code at} along {@code dir}, {@code radius} long, firing {@code fireIn} ticks from now. */
    static @Nullable Carrier wedge(ServerLevel level, @Nullable AwakenMob owner, Strikes.Kind cls, Vec3 at, Vec3 dir, float radius, int fireIn) {
        return add(new Carrier(Kind.WEDGE, level, owner, cls, at, dir.x, dir.z, 0, radius, 0, (float) Abilities.coneDot(radius), level.getGameTime(),
                Math.max(1, fireIn), List.of()));
    }

    /**
     * How long before it lands an ability's aim stops following you (SPEC §46.2, I3): a line, a wedge or a leap
     * {@link #LOCK} ticks; a shot or a ground line {@link #LOCK_SHOT} (its flight adds the rest); the rest don't aim.
     */
    public static int lead(dev.awaken.mobs.def.Ability a) {
        return switch (a.kind()) {
            case BEAM, CHARGE, CONE, LEAP -> LOCK;
            case BOLT, ARROWS, FANGS, ROOT, CLOUD -> LOCK_SHOT;
            default -> 0;
        };
    }

    /** A landing ring at {@code at}: a leap locked there, shown until it lands (or {@code life}). */
    static @Nullable Carrier ring(ServerLevel level, @Nullable AwakenMob owner, Strikes.Kind cls, Vec3 at, float radius, int life) {
        return add(new Carrier(Kind.RING, level, owner, cls, at, 0, 0, 0, 0, 0, radius, level.getGameTime(), Math.max(1, life), List.of()));
    }

    /** A field's edge for {@code life} (its pulses are a {@link Hazards} cloud that waits {@link #FIELD_SAFE} first). */
    static @Nullable Carrier field(ServerLevel level, @Nullable AwakenMob owner, Vec3 at, float radius, int life) {
        return add(new Carrier(Kind.FIELD, level, owner, Strikes.Kind.PERILOUS, at, 0, 0, 0, 0, 0, Math.min(FIELD_MAX, radius), level.getGameTime(),
                Math.max(1, life), List.of()));
    }

    /**
     * A pull into {@code origin} from {@code radius} for {@link #PULL_TICKS} (it hurts nothing), and as it ends a wave
     * out from it to {@code radius}: both sent now, the wave's start {@link #PULL_TICKS} on.
     */
    static @Nullable Carrier pull(ServerLevel level, @Nullable AwakenMob owner, Vec3 origin, float radius, float damage, BoltKind bolt,
                                  @Nullable MobEffectInstance effect, double knock) {
        long now = level.getGameTime();
        Carrier c = add(new Carrier(Kind.PULL, level, owner, Strikes.Kind.PERILOUS, origin, 0, 0, 0, radius, PULL_SPEED, 0, now, PULL_TICKS, List.of()));
        if (c == null) return null;
        float from = 1.0F;
        int life = Math.max(1, (int) Math.ceil((radius - from) / WAVE_SPEED));
        add(new Carrier(Kind.WAVE, level, owner, Strikes.Kind.PERILOUS, origin, 0, 0, from, radius, WAVE_SPEED, CREST, now + PULL_TICKS, life, List.of())
                .hits(damage, bolt, effect, knock));
        return c;
    }

    /** Ends a shown carrier now (a leap landed): the clients drop it. */
    static void end(@Nullable Carrier c) {
        if (c == null) return;
        c.ends = 1;
    }

    /** Carriers alive now for this creature (its shots counted by the caller). */
    static int alive(@Nullable AwakenMob owner) {
        if (owner == null) return 0;
        int n = 0;
        for (Carrier c : ACTIVE) if (c.owner == owner) n++;
        for (Carrier c : PENDING) if (c.owner == owner) n++;
        return n;
    }

    private static @Nullable Carrier add(Carrier c) {
        if (alive(c.owner) >= PER_OWNER) return null;
        if (ticking) PENDING.add(c);
        else ACTIVE.add(c);
        send(c);
        return c;
    }

    private static void send(Carrier c) {
        MobsNet.Carrier msg = c.payload();
        for (ServerPlayer p : c.level.players()) {
            if (p.distanceToSqr(c.origin) > SEND_RANGE * SEND_RANGE) continue;
            AwakenNet.send(p, msg);
            told++;
        }
    }

    /** Carriers sent to a player near them this session, one per player (tests: a fake player's channel takes none). */
    static int told;

    // ---- running them ------------------------------------------------------------------------------------------------

    public static void tick(Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!PENDING.isEmpty() && !ticking) {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
        }
        if (ACTIVE.isEmpty()) return;
        ticking = true;
        try {
            long now = sl.getGameTime();
            for (Iterator<Carrier> it = ACTIVE.iterator(); it.hasNext(); ) {
                Carrier c = it.next();
                if (c.level != sl) continue;
                long t = now - c.start;
                if (t < 0) continue;
                if (c.ends > 0) {
                    for (ServerPlayer p : sl.players()) {
                        if (p.distanceToSqr(c.origin) <= SEND_RANGE * SEND_RANGE) AwakenNet.send(p, new MobsNet.CarrierEnd(c.id));
                    }
                    it.remove();
                    continue;
                }
                switch (c.kind) {
                    case WAVE -> wave(sl, c, t);
                    case VOLLEY -> {
                        if (t == c.life) volleyLands(sl, c);
                    }
                    case GROUND -> ground(sl, c, t);
                    case PULL -> pull(sl, c);
                    default -> { }
                }
                if (t >= c.life) it.remove();
            }
        } finally {
            ticking = false;
        }
    }

    private static void wave(ServerLevel level, Carrier c, long t) {
        float r = Math.min(c.to, c.from + c.speed * t);
        for (LivingEntity e : Abilities.enemiesNear(level, c.owner, c.origin, c.to + 1)) {
            if (c.resolved.contains(e.getId())) continue;
            double d = Math.sqrt(e.distanceToSqr(c.origin.x, e.getY(), c.origin.z)) - e.getBbWidth() * 0.5;
            if (t == 0 && d < c.from) {
                c.resolved.add(e.getId()); // inside where it starts: the safe ring
                continue;
            }
            if (d > r) continue;
            c.resolved.add(e.getId());
            // over its crest (a jump, a block's step up) or behind a wall: it rolls under or stops
            if (e.getY() - c.origin.y > CREST || e.getY() < c.origin.y - 1.5 || blocked(level, c.origin, e)) continue;
            land(level, c, e, e.position().subtract(c.origin));
        }
    }

    private static void volleyLands(ServerLevel level, Carrier c) {
        AwakenMob owner = c.owner != null && c.owner.isAlive() ? c.owner : null;
        for (Vec3 p : c.points) {
            Abilities.rainFx(level, owner, c.bolt, p, c.width);
            for (LivingEntity e : Abilities.enemiesNear(level, c.owner, p, c.width + 0.3F)) {
                if (!c.resolved.add(e.getId())) continue;
                land(level, c, e, e.position().subtract(p));
            }
            if (c.fieldLife > 0) {
                // the field it leaves: its edge drawn at once, its pulses only after it has shown a while (never on you)
                Hazards.cloud(level, owner, p, c.fieldRadius, c.fieldLife, c.fieldDamage, c.bolt, null, FIELD_SAFE);
                field(level, owner, p, c.fieldRadius, c.fieldLife + FIELD_SAFE);
            }
        }
    }

    private static void ground(ServerLevel level, Carrier c, long t) {
        float d = Math.min(c.to, c.speed * (t + 1)), was = Math.max(0, c.speed * t - 0.6F);
        for (LivingEntity e : Abilities.enemiesNear(level, c.owner, c.origin, c.to + 1)) {
            if (c.resolved.contains(e.getId())) continue;
            double ex = e.getX() - c.origin.x, ez = e.getZ() - c.origin.z;
            double along = ex * c.dx + ez * c.dz, side = Math.abs(ex * c.dz - ez * c.dx);
            if (along < was - 0.5 || along > d || side > c.width + e.getBbWidth() * 0.5 || Math.abs(e.getY() - c.origin.y) > 2) continue;
            c.resolved.add(e.getId());
            land(level, c, e, new Vec3(c.dx, 0, c.dz));
        }
    }

    private static void pull(ServerLevel level, Carrier c) {
        // creatures are drawn here; a player's own client draws them (mobs_carrier PULL), as its movement is its own
        for (LivingEntity e : Abilities.enemiesNear(level, c.owner, c.origin, c.to)) {
            if (e instanceof Player) continue;
            Vec3 in = c.origin.subtract(e.position());
            double n = Math.sqrt(in.x * in.x + in.z * in.z);
            if (n < 1) continue;
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x + in.x / n * c.speed * 0.5, v.y, v.z + in.z / n * c.speed * 0.5);
            e.needsSync = true;
        }
    }

    /** A carrier reaches {@code e}: its blow as its class, its push and effects only with the hit (I5). */
    private static void land(ServerLevel level, Carrier c, LivingEntity e, Vec3 away) {
        AwakenMob owner = c.owner != null && c.owner.isAlive() ? c.owner : null;
        // a carrier always strikes for something: an effect-only one stings for a quarter heart, so a dodge can stop it
        float dmg = Math.max(0.5F, c.damage);
        boolean landed = owner != null
                ? Strikes.deliver(owner, c.cls, () -> Abilities.hurt(level, owner, e, dmg, c.bolt, c.effect == null ? null : new MobEffectInstance(c.effect)))
                : Abilities.hurt(level, null, e, dmg, c.bolt, c.effect == null ? null : new MobEffectInstance(c.effect));
        if (!landed) return;
        if (c.second != null) e.addEffect(new MobEffectInstance(c.second), owner);
        if (c.knock > 0) Abilities.push(e, away, c.knock, 0.3);
        hits++;
    }

    /** Carriers' hits that landed this session (tests). */
    static int hits;

    /** A wall between a wave's origin and the body stops it. */
    private static boolean blocked(ServerLevel level, Vec3 origin, LivingEntity e) {
        Vec3 a = origin.add(0, 0.4, 0), b = e.position().add(0, 0.4, 0);
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.BLOCK;
    }

    /** Drops everything for a level (server stopping, or a level unloading). */
    public static void clear(@Nullable Level level) {
        if (level == null) {
            ACTIVE.clear();
            PENDING.clear();
        } else {
            ACTIVE.removeIf(c -> c.level == level);
            PENDING.removeIf(c -> c.level == level);
        }
    }

    /** What's alive now (tests). */
    static List<Carrier> active() {
        return ACTIVE;
    }
}
