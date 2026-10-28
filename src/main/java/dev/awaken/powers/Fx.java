package dev.awaken.powers;

import dev.awaken.core.AwakenNet;
import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.CastProfiles;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.powers.fx.PowerFxDefs;
import dev.awaken.powers.net.FxPayloads;
import dev.awaken.powers.net.PowerPayloads;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server side of power visuals. Behaviours describe an effect in one line ({@code Fx.at(c, FxKind.SLAM, at).scale(r)
 * .send()}); the event goes to every player who can see it and their client builds it (docs/modules/power-fx.md).
 * Sending one ~50-byte event replaces the dozens of particle packets the first build sent.
 * <p>
 * Conventions: an event from a cast carries the caster, the power's style, variant and level and a random seed. The
 * power's release recipe (from {@link PowerFxDefs}) also starts the caster's cast animation, its wind-up recipe the
 * held wind-up pose, and an ultimate's release also sends the screen beat.
 */
public final class Fx {
    private Fx() {}

    /** Players this far from an effect (or from the caster, for attached effects) receive it. */
    public static final double RANGE = 72;

    // ---- the event API ----------------------------------------------------------------------------------------------

    /** An effect from a cast, styled and animated by the power's definition. */
    public static Emit at(Cast c, FxKind kind, Vec3 at) {
        PowerFxDefs.Def def = PowerFxDefs.get(c.type.path);
        FxStyle style = def != null ? def.style() : FxStyle.of(c.type.family);
        Emit e = new Emit(c.level, FxEvent.of(kind, style, at), at);
        e.profiled = CastProfiles.byPower(c.type.path) != null;
        e.b.caster(c.player).seed(c.player.getRandom().nextInt()).variant(c.variant).power(PowerRegistry.index(c.type) + 1);
        e.power = c.powerLevel;
        e.caster = c.player;
        CastProfiles.Profile prof = CastProfiles.byPower(c.type.path);
        if (def != null) {
            // a power with a cast profile (docs/power-anims/PLAN.md) carries its beat instead of a family pose
            if (prof != null && kind == def.windup()) {
                e.b.profile(prof.id(), CastProfiles.BEGIN);
                c.poseSent = true;
            } else if (prof != null && kind == def.release()) {
                e.b.profile(prof.id(), CastProfiles.RELEASE);
                c.poseSent = true;
            }
            else if (kind == def.release() && def.anim() != CastAnim.NONE) e.b.anim(def.anim());
            else if (kind == def.windup() && def.windAnim() != null) e.b.anim(def.windAnim());
            if (def.ultimate()) {
                e.b.flag(FxEvent.ULT);
                e.ultBeat = kind == def.release();
            }
        }
        return e;
    }

    /** An effect not tied to a cast (passives, force reactions, summons). */
    public static Emit at(ServerLevel level, FxKind kind, FxStyle style, Vec3 at) {
        Emit e = new Emit(level, FxEvent.of(kind, style, at), at);
        e.b.seed(level.getRandom().nextInt());
        return e;
    }

    /** A power's side effect outside a cast (a damage hook): its style and the player's level of it, {@code plain()}. */
    public static Emit of(ServerPlayer player, String power, FxKind kind, Vec3 at) {
        return looks(player, power, kind, at).plain();
    }

    /** A passive's own effect outside a cast (Wall Stride's run): its style, the player's level and that level's looks. */
    public static Emit looks(ServerPlayer player, String power, FxKind kind, Vec3 at) {
        PowerType type = PowerRegistry.get(power);
        if (type == null) throw new IllegalArgumentException("no power " + power);
        PowerFxDefs.Def def = PowerFxDefs.get(type.path);
        FxStyle style = def != null ? def.style() : FxStyle.of(type.family);
        Loadout.Owned o = Powers.loadout(player).get(type.id.toString());
        return at((ServerLevel) player.level(), kind, style, at).caster(player).powerOf(type).level(o != null ? o.level() : 1);
    }

    /** A pending effect: chain setters, then {@link #send()}. */
    public static final class Emit {
        private final ServerLevel level;
        private final FxEvent.Builder b;
        private final Vec3 origin;
        private @Nullable Vec3 end;
        private @Nullable Entity caster;
        private int power = 1;
        private boolean ultBeat;
        private boolean profiled;
        private double range = RANGE;

        Emit(ServerLevel level, FxEvent.Builder b, Vec3 origin) {
            this.level = level;
            this.b = b;
            this.origin = origin;
        }

        public Emit caster(@Nullable Entity e) {
            this.caster = e;
            b.caster(e);
            return this;
        }

        /** Overrides the power's style (a heat variant, a soul echo). */
        public Emit style(FxStyle s) {
            b.style(s);
            return this;
        }

        public Emit target(@Nullable Entity e) {
            b.target(e);
            return this;
        }

        public Emit dir(Vec3 d) {
            b.dir(d);
            return this;
        }

        public Emit to(Vec3 to) {
            this.end = to;
            b.to(to);
            return this;
        }

        public Emit colour(int argb) {
            b.colour(argb);
            return this;
        }

        public Emit scale(double s) {
            b.scale(s);
            return this;
        }

        public Emit aux(double a) {
            b.aux(a);
            return this;
        }

        public Emit ticks(int t) {
            b.ticks(t);
            return this;
        }

        public Emit flag(int f) {
            b.flag(f);
            return this;
        }

        public Emit flag(int f, boolean on) {
            b.flag(f, on);
            return this;
        }

        public Emit anim(CastAnim a) {
            if (!profiled) b.anim(a); // a power with a cast profile has its own poses: a family pose here would replace them
            return this;
        }

        public Emit noAnim() {
            if (!profiled) b.anim(CastAnim.NONE); // a profile's beat marks stay: a second effect of the release kind marking it again is harmless
            return this;
        }

        /** Marks the event as a beat of a cast profile. */
        public Emit profile(int id, int beat) {
            b.profile(id, beat);
            return this;
        }

        public Emit seed(int s) {
            b.seed(s);
            return this;
        }

        /**
         * A ladder rider's or step's own effect (SPEC 36.3): the power's style and level, but no pose, no profile beat, no
         * ultimate screen beat and no look layers of its own, so a chain arc or an echo never restarts a cast or cascades.
         */
        public Emit plain() {
            b.clearPose().unflag(FxEvent.ULT).flag(FxEvent.RIDER);
            ultBeat = false;
            return this;
        }

        public Emit range(double r) {
            this.range = r;
            return this;
        }

        /** The power level the event draws at (an effect not sent from a cast: the evolution moment, a summon). */
        public Emit level(int level) {
            this.power = Math.max(1, level);
            return this;
        }

        /** The power whose ladder looks the event draws. */
        public Emit powerOf(PowerType type) {
            b.power(PowerRegistry.index(type) + 1);
            return this;
        }

        /** Builds the event (with the power level packed in) without sending it: tests. */
        public FxEvent build() {
            b.flag(Math.max(0, Math.min(7, power - 1)) << LEVEL_SHIFT);
            return b.build();
        }

        public void send() {
            FxEvent e = build();
            Fx.send(level, e, origin, end, caster, range);
            if (ultBeat) {
                FxEvent beat = FxEvent.of(FxKind.ULT, e.style(), origin).caster(caster).scale(28).seed(e.seed()).flag(FxEvent.ULT).build();
                Fx.send(level, beat, origin, null, caster, range);
            }
        }
    }

    /** Power level (1-5) rides in flags bits 8-10; recipes grow with it. */
    public static final int LEVEL_SHIFT = 8;

    public static int levelOf(FxEvent e) {
        return ((e.flags() >> LEVEL_SHIFT) & 7) + 1;
    }

    /** Tests: runs the per-tick power logic for a player (mock players are not ticked by a connection). */
    public static void testTick(ServerPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) Casting.tickPlayer(player);
    }

    /** Tests capture every event sent while this is set. */
    public static java.util.function.@Nullable Consumer<FxEvent> testSink;

    /** Sends to players near the origin, the end point or the caster. */
    public static void send(ServerLevel level, FxEvent e, Vec3 origin, @Nullable Vec3 end, @Nullable Entity caster, double range) {
        if (testSink != null) testSink.accept(e);
        FxPayloads.Fx msg = new FxPayloads.Fx(e);
        double r2 = range * range;
        for (ServerPlayer p : level.players()) {
            boolean near = p.position().distanceToSqr(origin) < r2
                    || (end != null && p.position().distanceToSqr(end) < r2)
                    || (caster != null && p.distanceToSqr(caster) < r2);
            if (near) AwakenNet.send(p, msg);
        }
    }

    public static void send(ServerLevel level, FxEvent e) {
        send(level, e, e.pos(), null, null, RANGE);
    }

    /**
     * Ends effects attached to an entity: {@code what} 0 = a charge (and the held wind-up pose), an aura's ordinal + 1 =
     * that aura, -2 = domes and shells.
     */
    public static void end(Cast c, Entity on, int what) {
        Emit e = at(c, FxKind.CHARGE_END, on.position()).caster(on).aux(what).noAnim();
        CastProfiles.Profile prof = CastProfiles.byPower(c.type.path);
        if (what == 0 && prof != null) e.profile(prof.id(), CastProfiles.END);
        e.send();
    }

    /**
     * A power's sound as an FX event: every client in range plays it at {@code at} on the cast's release frame (the same frame as
     * the effect and the camera moment), not on the press tick, so sound, hands and light land together.
     */
    public static void sound(Cast c, Vec3 at, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float volume, float pitch) {
        int id = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getId(sound.value());
        if (id < 0) return;
        at(c, FxKind.SOUND, at).noAnim().colour(id).scale(volume).aux(pitch).send();
    }

    /**
     * Begins a cast profile's pose for everyone who can see the caster (the key press already started it on their own
     * client): the clips run, nothing is drawn. For a power with a wind-up, the effect follows {@code windup} ticks later.
     */
    public static void pose(Cast c) {
        CastProfiles.Profile prof = CastProfiles.byPower(c.type.path);
        if (prof == null) return;
        at(c, FxKind.POSE, c.pos()).noAnim().profile(prof.id(), CastProfiles.BEGIN).send();
    }

    /** A glowing trail on a thrown block (Terrain Rip, Collapse, wall rams). */
    public static void trailOn(Cast c, FlyingBlocks.@Nullable Flyer f, int ticks) {
        if (f == null || f.display == null) return;
        at(c, FxKind.TRAIL, f.display.position()).caster(f.display).aux(2).ticks(ticks).noAnim().send();
    }

    // ---- combat feel ------------------------------------------------------------------------------------------------

    /** Hit-stop on the struck entity and a camera kick for the player who struck it. */
    public static void feel(ServerPlayer player, @Nullable LivingEntity hit, int hitstopMs, float shake) {
        AwakenNet.send(player, new PowerPayloads.Feel(hit == null ? -1 : hit.getId(), hitstopMs, shake));
    }

    /** Camera kick only, for every player near a big impact. */
    public static void quake(ServerLevel level, Vec3 at, double radius, float shake) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d < radius) AwakenNet.send(p, new PowerPayloads.Feel(-1, 0, (float) (shake * (1 - d / radius))));
        }
    }

    // ---- shared shapes still used outside the catalog -----------------------------------------------------------------

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(rgb, scale);
    }

    /** Beam visuals to the caster and everyone near: a layered beam (white core, coloured body, wide halo). */
    public static void beam(ServerPlayer caster, Entity from, Vec3 end, int argb, int ticks, float width, float range, boolean follow) {
        beam(null, caster, from, end, argb, ticks, width, range, follow);
    }

    /** A cast's beam: it also carries the power and its level, so every viewer draws the ladder's looks on it (SPEC 36.4). */
    public static void beam(@Nullable Cast c, ServerPlayer caster, Entity from, Vec3 end, int argb, int ticks, float width, float range, boolean follow) {
        beam(c, caster, from, end, argb, ticks, width, range, follow, true);
    }

    /** A cast's beam; {@code layers} false draws it in the ladder's palette without its look layers (a split or side beam). */
    public static void beam(@Nullable Cast c, ServerPlayer caster, Entity from, Vec3 end, int argb, int ticks, float width, float range, boolean follow,
                            boolean layers) {
        FxStyle style = styleFor(argb);
        Vec3 start = from.getEyePosition();
        FxEvent.Builder b = FxEvent.of(FxKind.BEAM, style, start).caster(from).to(end).colour(argb).aux(width).scale(range)
                .ticks(ticks).flag(FxEvent.FOLLOW, follow).seed(caster.getRandom().nextInt());
        if (c != null) b.power(PowerRegistry.index(c.type) + 1).variant(c.variant).flag(Math.max(0, Math.min(7, c.powerLevel - 1)) << LEVEL_SHIFT);
        if (!layers) b.flag(FxEvent.RIDER);
        send(caster.level(), b.build(), start, end, caster, 96);
    }

    /** One step of a bolt in flight (Bolts): a glowing streak. */
    public static void streak(ServerLevel level, Vec3 a, Vec3 b, int argb, int count, int duration) {
        streak(level, a, b, argb, count, duration, 0, 1);
    }

    /** A cast's bolt step: it carries the power and level (power = registry index + 1) for the ladder's looks. */
    public static void streak(ServerLevel level, Vec3 a, Vec3 b, int argb, int count, int duration, int power, int powerLevel) {
        FxEvent e = FxEvent.of(FxKind.BOLT, styleFor(argb), a).to(b).colour(argb).ticks(duration).scale(0.5 + 0.25 * count)
                .seed(level.getRandom().nextInt()).power(power).flag(Math.max(0, Math.min(7, powerLevel - 1)) << LEVEL_SHIFT).build();
        send(level, e, a, b, null, RANGE);
    }

    /** A jagged lightning arc from a to b. */
    public static void lightning(ServerLevel level, Vec3 a, Vec3 b, int segments) {
        FxEvent e = FxEvent.of(FxKind.LIGHTNING, FxStyle.CHARGE, a).to(b).ticks(6).scale(Math.max(0.6, segments / 6.0))
                .seed(level.getRandom().nextInt()).build();
        send(level, e, a, b, null, RANGE);
    }

    /** A hit spark in a colour (legacy callers; the colour picks the closest style). */
    public static void impact(ServerLevel level, Vec3 at, int argb) {
        FxEvent e = FxEvent.of(FxKind.IMPACT, styleFor(argb), at).scale(1.0).seed(level.getRandom().nextInt()).build();
        send(level, e);
    }

    /** Particles along a line, one every {@code step} blocks, at most 32 (vanilla particles; force reactions). */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 u = d.scale(1 / len);
        int n = Math.min(32, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(u.scale(i * step));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** A flat ring of vanilla particles (kept for callers outside the catalog). */
    public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0.02, 0, speed);
        }
    }

    /** A ring of vanilla particles flying outward (kept for callers outside the catalog). */
    public static void burstRing(ServerLevel level, Vec3 c, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.05, Math.sin(a), speed);
        }
    }

    /** A sphere shell of vanilla particles (kept for callers outside the catalog). */
    public static void sphere(ServerLevel level, Vec3 c, double radius, ParticleOptions p, int points) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double t = golden * i;
            level.sendParticles(p, c.x + Math.cos(t) * r * radius, c.y + y * radius, c.z + Math.sin(t) * r * radius, 1, 0, 0, 0, 0);
        }
    }

    /** The style whose mid colour is closest to an RGB colour (legacy colour-only calls). */
    public static FxStyle styleFor(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        FxStyle best = FxStyle.NEUTRAL;
        int bestD = Integer.MAX_VALUE;
        for (FxStyle s : FxStyle.values()) {
            for (int c : new int[]{s.mid, s.core}) {
                int dr = ((c >> 16) & 255) - r, dg = ((c >> 8) & 255) - g, db = (c & 255) - b;
                int d = dr * dr + dg * dg + db * db;
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
        }
        return best;
    }
}
