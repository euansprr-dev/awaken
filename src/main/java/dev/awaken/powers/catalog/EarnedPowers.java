package dev.awaken.powers.catalog;

import dev.awaken.core.Forces;
import dev.awaken.core.api.FightMoments;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Constructs;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.powers.fx.Voice.Beat;
import dev.awaken.powers.fx.Ways;
import dev.awaken.powers.ladder.Riders;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The earned powers (SPEC §50, "Earned powers"; Euan, 10 Nov 2026: "surprise me, as long as it still feels like MC"). Five
 * powers that no Power Core rolls: each is given for something you did ({@code powers.Earned}), and each fills a hole the
 * catalogue had. Heat and Frost had one power apiece while every weapon attunes to both, nothing answered the forge, nothing
 * tied Flight to climbing, and nothing in the catalogue used the land you stand on.
 * <p>
 * Brand and Hoarfrost Lances are made to be finished by the weapon in your hand: the cast sets something up and your own
 * blows spend it. Nothing is timed. Updraft is a place you make, for everyone. Backdraft rewards fire that is already burning,
 * Thornwake the plants that are really there.
 */
public final class EarnedPowers {
    private EarnedPowers() {}

    static void register() {
        PowerType.builder("brand", "Brand", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.HEAT).cost(18).cooldown(160).damage(9).unlock(Rank.E).hidden().icon("brand")
                .desc("Press your palm to an enemy and leave a burning mark on it for 8 s. Your next weapon blow on it sets the mark off, "
                        + "and fire bursts out over everything close. Aim at the ground and the mark waits there as a trap.")
                .variant(Variant.of("Slow Burn", "The mark lasts twice as long and bursts a little weaker").dur(2f).dmg(0.8f))
                .variant(Variant.of("Flash Brand", "A wider, lighter burst").radius(1.4f).dmg(0.85f))
                .cast(EarnedPowers::brand).register();

        PowerType.builder("hoarfrost_lances", "Hoarfrost Lances", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.FROST).cost(24).cooldown(360).damage(5).unlock(Rank.D).hidden().icon("lances")
                .desc("Three lances of ice hang over your shoulders for 12 s. Every blow you land with a weapon looses one at what you hit, "
                        + "and a heavy blow looses all of them. Press again to throw what's left where you aim.")
                .variant(Variant.of("Long Winter", "They wait twice as long and hit a little lighter").dur(2f).dmg(0.85f))
                .variant(Variant.of("Heavy Lances", "Two lances that hit much harder").dmg(1.7f))
                .cast(EarnedPowers::hoarfrostLances).register();

        PowerType.builder("updraft", "Updraft", Family.FLIGHT, Slot.ACTIVE)
                .cost(20).cooldown(240).damage(3).unlock(Rank.E).hidden().icon("updraft")
                .desc("A column of rising air where you stand for 6 s. Step in and it carries you 14 blocks up, friends too. "
                        + "Enemies caught in it are thrown into the air and dropped, and arrows are swept upward.")
                .variant(Variant.of("Far Draft", "The column rises where you aim, up to 14 blocks away"))
                .variant(Variant.of("Tall Draft", "Half again as tall, for less time").dur(0.7f))
                .cast(EarnedPowers::updraft).register();

        PowerType.builder("backdraft", "Backdraft", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.HEAT).cost(30).cooldown(400).damage(6).unlock(Rank.D).hidden().icon("backdraft")
                .desc("Breathe in every fire within 8 blocks: flames go out, burning things stop burning, your Brands are swallowed. "
                        + "Then breathe it all back out as one cone of flame, bigger for every fire you took.")
                .variant(Variant.of("Deep Breath", "Draws fire in from further away").radius(1.5f).dmg(0.9f))
                .cast(EarnedPowers::backdraft).register();

        PowerType.builder("thornwake", "Thornwake", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(26).cooldown(320).damage(5).unlock(Rank.D).hidden().icon("thornwake")
                .desc("Wake the plants around you. Roots burst out of grass, flowers, leaves and crops within 7 blocks and seize the enemies "
                        + "nearest them. The greener the ground, the more roots. On bare stone you get one thin root.")
                .variant(Variant.of("Briar Ring", "Roots only near you, but they hold longer").radius(0.6f).dur(1.6f))
                .cast(EarnedPowers::thornwake).register();

        FightMoments.listen(EarnedPowers::moment);
    }

    // ---- what a cast leaves for the blade ---------------------------------------------------------------------------

    /** A mark Brand left: on a body ({@code target} its id) or on the ground (-1). */
    private static final class Mark {
        final Cast c;
        int target;
        Vec3 at;
        long until, armedAt;
        final float damage;
        final double radius;
        int jumps;
        /** Set when a blow has lit it: how hard (1 a blow, 1.5 a heavy one). It goes off a moment later. */
        float firing;

        Mark(Cast c, int target, Vec3 at, long until, float damage, double radius, int jumps) {
            this.c = c;
            this.target = target;
            this.at = at;
            this.until = until;
            this.armedAt = c.now() + 15;
            this.damage = damage;
            this.radius = radius;
            this.jumps = jumps;
        }
    }

    /** The lances a cast hung over its caster. */
    private static final class Lances {
        final Cast c;
        int left;
        final float damage;
        /** When one last flew (half the range, so "how long since" never overflows). */
        long last = Long.MIN_VALUE / 2;
        boolean crowned;

        Lances(Cast c, int n, float damage) {
            this.c = c;
            this.left = n;
            this.damage = damage;
        }
    }

    private static final class State {
        final List<Mark> marks = new ArrayList<>(MAX_MARKS);
        /** A watcher is ticking the marks (one a player, however many casts). */
        boolean watching;
        @Nullable Lances lances;
    }

    private static final Map<ServerPlayer, State> STATE = new WeakHashMap<>();
    private static final int MAX_MARKS = 3;

    private static State state(ServerPlayer p) {
        return STATE.computeIfAbsent(p, k -> new State());
    }

    /** A melee blow of any kind landed (PowerEvents, on the damage itself): fists and plain swords spend these too. */
    public static void meleeHit(ServerPlayer p, LivingEntity victim) {
        blow(p, victim, false);
    }

    private static void moment(ServerPlayer p, FightMoments.Moment m, @Nullable LivingEntity target) {
        if (target == null) return;
        if (m == FightMoments.Moment.HIT) blow(p, target, false);
        else if (m == FightMoments.Moment.FINISHER || m == FightMoments.Moment.PERFECT_HEAVY) blow(p, target, true);
    }

    private static void blow(ServerPlayer p, LivingEntity victim, boolean heavy) {
        State s = STATE.get(p);
        if (s == null) return;
        if (!s.marks.isEmpty()) {
            for (Mark m : s.marks) {
                boolean on = m.target >= 0 ? m.target == victim.getId() : victim.position().distanceToSqr(m.at) <= 2.5 * 2.5;
                if (on) light(p, s, m, heavy ? 1.5f : 1f);
            }
        }
        Lances l = s.lances;
        if (l != null && l.left > 0) {
            long now = l.c.now();
            if (heavy) {
                // a heavy blow in the same swing as a light one still throws the rest
                loose(p, l, victim, l.left);
            } else if (now - l.last >= 6) {
                loose(p, l, victim, 1);
            }
        }
    }

    // ---- Brand ------------------------------------------------------------------------------------------------------

    static Result brand(Cast c) {
        LivingEntity t = Combat.aimEntity(c, 4.5, 0.6);
        Vec3 at;
        if (t != null) {
            at = t.getBoundingBox().getCenter();
        } else {
            BlockHitResult ground = Combat.aimBlock(c, 5.5);
            if (ground == null) return Result.fail("no_target");
            at = ground.getLocation();
        }
        State s = state(c.player);
        while (s.marks.size() >= MAX_MARKS) fade(c.player, s.marks.remove(0));
        // on the same body twice: the new mark replaces the old one
        if (t != null) s.marks.removeIf(old -> old.target == t.getId());
        int ticks = c.ticks(160);
        // Second Brand (4): it jumps once more when what it's on dies
        Mark m = new Mark(c, t == null ? -1 : t.getId(), at, c.now() + ticks, c.dmg(), c.radius(3.5), 1 + (int) c.num(4, "jumps", 0f));
        s.marks.add(m);
        Vec3 from = c.eye().add(c.look().scale(0.6)).add(0, -0.3, 0);
        Fx.at(c, FxKind.PALM, from).dir(at.subtract(from)).scale(0.9).send();
        if (t != null) {
            c.hurt(t, c.dmg() * 0.25f, Forces.Force.HEAT);
            // the mark shows as the palm lands (the clip's release tick), not on the press
            var prof = dev.awaken.powers.fx.CastProfiles.byPower(c.type.path);
            Scheduler.after(prof == null ? 1 : Math.max(1, prof.release()), () -> {
                if (t.isAlive() && s.marks.contains(m)) Fx.at(c, FxKind.AURA, t.position()).caster(t).aux(FxKind.Aura.BRAND.ordinal()).ticks(ticks).noAnim().send();
            });
        } else {
            Fx.at(c, FxKind.GLYPH, at).scale(1.1).ticks(ticks).noAnim().send();
        }
        c.voice(at, Beat.IMPACT, PowerSounds.EMBER, 1f, 0.8f);
        c.feel(t, 30, 1.2f);
        watchMarks(c);
        return Result.OK;
    }

    /** One watcher a player ticks every mark they have out: marks follow their bodies, traps wait, time runs out. */
    private static void watchMarks(Cast c) {
        ServerPlayer p = c.player;
        State s = state(p);
        if (s.watching) return;
        s.watching = true;
        Running r = Casting.start(c, "awaken:brand:marks", -1);
        r.tick(rr -> {
            if (s.marks.isEmpty()) {
                rr.stop();
                return;
            }
            long now = c.now();
            for (int i = s.marks.size() - 1; i >= 0; i--) {
                Mark m = s.marks.get(i);
                if (m.firing > 0) continue;
                if (m.target >= 0) {
                    Entity e = p.level().getEntity(m.target);
                    if (!(e instanceof LivingEntity body) || !body.isAlive()) {
                        // it died with the brand still on: the fire finds the next body
                        s.marks.remove(i);
                        jump(p, s, m);
                        continue;
                    }
                    m.at = body.getBoundingBox().getCenter();
                } else if (now >= m.armedAt && rr.age % 3 == 0 && !Combat.foesAround(p, m.at, 1.5).isEmpty()) {
                    light(p, s, m, 1f);
                    continue;
                }
                if (now >= m.until) {
                    s.marks.remove(i);
                    fade(p, m);
                }
            }
        }).end((rr, why) -> {
            // however it ended (the last mark went, or you died or left): nothing is left burning
            s.watching = false;
            for (Mark m : s.marks) fade(p, m);
            s.marks.clear();
        });
    }

    private static void fade(ServerPlayer p, Mark m) {
        if (m.target >= 0 && p.level().getEntity(m.target) instanceof LivingEntity body) Fx.end(m.c, body, FxKind.Aura.BRAND.ordinal() + 1);
    }

    /** A blow lit the mark. It goes off two ticks later, so the blow lands first and the fire answers it. */
    private static void light(ServerPlayer p, State s, Mark m, float power) {
        boolean first = m.firing == 0;
        m.firing = Math.max(m.firing, power);
        if (!first) return;
        Scheduler.after(2, () -> {
            if (!s.marks.remove(m) || p.isRemoved()) return;
            setOff(p, m);
        });
    }

    private static void setOff(ServerPlayer p, Mark m) {
        Cast c = m.c;
        boolean heavy = m.firing > 1f;
        double radius = m.radius * (heavy ? 1.3 : 1);
        float dmg = m.damage * Math.max(1f, m.firing);
        LivingEntity body = m.target >= 0 && p.level().getEntity(m.target) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (body != null) m.at = body.getBoundingBox().getCenter();
        Vec3 at = m.at;
        if (body != null) {
            Combat.hurtFrom(p, null, body, dmg, Forces.Force.HEAT, true);
            if (c.kit.onHit.length > 0) Riders.onHit(c, body, dmg, Forces.Force.HEAT);
            body.igniteForSeconds(4);
            Fx.end(c, body, FxKind.Aura.BRAND.ordinal() + 1);
        }
        // Wildfire (3): the burst carries further into the crowd
        float share = 0.6f + c.num(3, "share", 0f);
        for (LivingEntity e : Combat.foesAround(p, at, radius)) {
            if (e == body) continue;
            Combat.hurtFrom(p, null, e, dmg * share, Forces.Force.HEAT, true);
            e.igniteForSeconds(3);
            Combat.knockFrom(e, at, 0.5, 0.25);
        }
        Fx.at(c, FxKind.NOVA, at).target(body).scale(radius).flag(FxEvent.REACH).flag(FxEvent.FULL, heavy).noAnim().send();
        c.voice(at, Beat.RELEASE, heavy ? 1.2f : 1f, PowerSounds.EMBER, 1.2f, 0.7f);
        Fx.feel(p, body, heavy ? 110 : 70, heavy ? 4f : 3f);
        Fx.quake(c.level, at, 10, heavy ? 2f : 1.2f);
        Progression.count(p, "awaken:brands_set_off");
        // Forge Mark (5): the ground keeps the brand and burns what stands on it
        if (c.step(5)) forgeMark(c, at, radius);
    }

    /** Forge Mark (Brand 5): the mark stays seared into the ground for a few seconds and burns whatever stands on it. */
    private static void forgeMark(Cast c, Vec3 at, double radius) {
        int ticks = (int) c.num(5, "ground", 80);
        float dps = c.dmg() * c.num(5, "dps", 0.25f);
        double r = radius * 0.7;
        Fx.at(c, FxKind.GLYPH, at).scale(r).ticks(ticks).flag(FxEvent.FULL).noAnim().send();
        Running g = Casting.start(c, "awaken:brand:ground", ticks).at(at);
        g.tick(rr -> {
            if (rr.age % 10 != 5) return;
            for (LivingEntity e : Combat.foesAround(c.player, at, r)) {
                Combat.hurtFrom(c.player, null, e, dps * 0.5f, Forces.Force.HEAT, false);
                e.igniteForSeconds(2);
            }
        });
    }

    /** The branded thing died of something else: the mark leaps to the nearest enemy, with a little time on it. */
    private static void jump(ServerPlayer p, State s, Mark m) {
        if (m.jumps <= 0) return;
        LivingEntity next = null;
        double best = 6 * 6;
        for (LivingEntity e : Combat.foesAround(p, m.at, 6)) {
            if (e.getId() == m.target) continue;
            boolean marked = false;
            for (Mark o : s.marks) if (o.target == e.getId()) marked = true;
            double d = e.position().distanceToSqr(m.at);
            if (!marked && d < best) {
                best = d;
                next = e;
            }
        }
        if (next == null) return;
        Vec3 to = next.getBoundingBox().getCenter();
        Mark n = new Mark(m.c, next.getId(), to, m.c.now() + 100, m.damage, m.radius, m.jumps - 1);
        s.marks.add(n);
        Fx.at(m.c, FxKind.AURA, to).caster(next).dir(m.at.subtract(to)).aux(FxKind.Aura.BRAND.ordinal()).ticks(100).noAnim().send();
        m.c.voice(to, Beat.ECHO, PowerSounds.EMBER, 0.8f, 1.3f);
    }

    /** Every Brand a player has out right now, taken (Backdraft swallows them): how many there were. */
    private static int swallowBrands(ServerPlayer p, Vec3 from, double radius, List<Vec3> where) {
        State s = STATE.get(p);
        if (s == null) return 0;
        int n = 0;
        for (int i = s.marks.size() - 1; i >= 0; i--) {
            Mark m = s.marks.get(i);
            if (m.firing > 0 || m.at.distanceToSqr(from) > radius * radius) continue;
            s.marks.remove(i);
            fade(p, m);
            where.add(m.at);
            n++;
        }
        return n;
    }

    // ---- Hoarfrost Lances -------------------------------------------------------------------------------------------

    static Result hoarfrostLances(Cast c) {
        // Fourth Lance (4), Glacier Crown (5): more of them
        int n = (c.is(2) ? 2 : 3) + (int) c.num(4, "more", 0f) + (int) c.num(5, "crown", 0f);
        int ticks = c.ticks(240);
        State s = state(c.player);
        Lances l = new Lances(c, n, c.dmg());
        s.lances = l;
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (l.left <= 0 && c.now() - l.last > 10) rr.stop();
        }).end((rr, why) -> {
            if (s.lances != l) return; // a newer cast's lances have taken their place
            s.lances = null;
            Fx.end(c, c.player, FxKind.Aura.LANCES.ordinal() + 1);
            Casting.endFollowUp(c);
        });
        // the count rides in the scale: every viewer hangs the same lances over this caster
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.LANCES.ordinal()).scale(n).ticks(ticks).send();
        c.voice(Beat.RELEASE, PowerSounds.FROST, 0.9f, 1.4f);
        c.followUp(ticks, () -> {
            // the second press: everything left goes where you're looking now
            if (s.lances != l || l.left <= 0) return;
            float yaw = c.player.getYRot(), pitch = c.player.getXRot();
            Vec3 look = Vec3.directionFromRotation(pitch, yaw);
            LivingEntity t = Combat.aimEntity(c.player, c.player.getEyePosition(), look, 22, 1.0, true);
            if (t != null) loose(c.player, l, t, l.left);
            else volley(c, l, c.player.getEyePosition().add(look.scale(20)));
        });
        return Result.OK;
    }

    /** Where lance {@code i} of a caster's hangs: over the shoulders, a fan behind the head (the client draws them at the same places). */
    private static Vec3 lancePos(ServerPlayer p, int i) {
        float yaw = (float) Math.toRadians(p.getYRot());
        double side = ((i & 1) == 0 ? 1 : -1) * (0.55 + 0.3 * (i >> 1));
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        return p.position().add(rx * side, p.getBbHeight() + 0.35 + 0.12 * (i >> 1), rz * side);
    }

    private static void loose(ServerPlayer p, Lances l, LivingEntity target, int count) {
        Cast c = l.c;
        count = Math.min(count, l.left);
        if (count <= 0) return;
        l.last = c.now();
        boolean all = count > 1;
        for (int k = 0; k < count; k++) {
            int index = --l.left;
            int delay = 2 + k * 2;
            Scheduler.after(delay, () -> {
                if (p.isRemoved()) return;
                Vec3 from = lancePos(p, index);
                if (!target.isAlive()) {
                    // what it was thrown at is already dead: it flies on and breaks on the ground behind
                    Vec3 at = target.position();
                    Fx.at(c, FxKind.BOLT, from).to(at).scale(1.3).ticks(index).noAnim().send();
                    return;
                }
                Vec3 at = target.getBoundingBox().getCenter();
                float dmg = l.damage * (all ? 0.6f : 0.7f);
                // Rime (3): a lance into something already frosted bursts
                boolean rimed = c.step(3) && Forces.has(target, Forces.Tag.FROSTED);
                Combat.hurtFrom(p, null, target, dmg, Forces.Force.FROST, true);
                if (c.kit.onHit.length > 0) Riders.onHit(c, target, dmg, Forces.Force.FROST);
                chill(c, target, 60);
                Fx.at(c, FxKind.BOLT, from).to(at).scale(1.3).ticks(index).noAnim().send();
                Fx.at(c, FxKind.IMPACT, at).target(target).dir(at.subtract(from)).scale(0.9).noAnim().send();
                c.voice(at, Beat.IMPACT, PowerSounds.FROST, 0.8f, 1.5f);
                if (rimed) {
                    float burst = dmg * c.num(3, "burst", 0.5f);
                    for (LivingEntity e : Combat.foesAround(p, at, 2.5)) {
                        if (e != target) Combat.hurtFrom(p, null, e, burst, Forces.Force.FROST, true);
                        chill(c, e, 40);
                    }
                    Fx.at(c, FxKind.FROST, target.position()).scale(2.5).plain().noAnim().send();
                }
            });
        }
        Progression.count(p, "awaken:lances_loosed", count);
        // Glacier Crown (5): when the last one flies, the cold left round you bursts
        if (l.left <= 0 && c.step(5) && !l.crowned) {
            l.crowned = true;
            Scheduler.after(2 + count * 2, () -> {
                if (p.isRemoved()) return;
                double radius = c.num(5, "ring", 4f);
                float dmg = l.damage * c.num(5, "share", 0.8f);
                for (LivingEntity e : Combat.foesAround(p, p.position().add(0, 1, 0), radius)) {
                    Combat.hurtFrom(p, null, e, dmg, Forces.Force.FROST, true);
                    chill(c, e, 80);
                }
                Fx.at(c, FxKind.FROST, p.position()).scale(radius).flag(FxEvent.REACH).noAnim().send();
                c.voice(Beat.EXTRA_A, PowerSounds.FROST, 1.1f, 0.9f);
            });
        }
    }

    /** The second press with nothing in the aim: the lances fly out along the look and hit what they meet. */
    private static void volley(Cast c, Lances l, Vec3 to) {
        ServerPlayer p = c.player;
        int count = l.left;
        l.left = 0;
        l.last = c.now();
        for (int k = 0; k < count; k++) {
            int index = k;
            Scheduler.after(1 + k * 2, () -> {
                if (p.isRemoved()) return;
                Vec3 from = lancePos(p, index);
                BlockHitResult wall = Combat.clip(p, from, to);
                Vec3 end = wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : wall.getLocation();
                for (LivingEntity e : Combat.alongLine(p, from, end, 0.9)) {
                    Combat.hurtFrom(p, null, e, l.damage * 0.6f, Forces.Force.FROST, true);
                    chill(c, e, 60);
                    Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).target(e).dir(end.subtract(from)).scale(0.8).noAnim().send();
                }
                Fx.at(c, FxKind.BOLT, from).to(end).scale(1.3).ticks(index).noAnim().send();
                c.voice(end, Beat.IMPACT, PowerSounds.FROST, 0.7f, 1.6f);
            });
        }
    }

    /** Cold that the next blow or the next Frost power can use: slowed, frosted for a while. */
    private static void chill(Cast c, LivingEntity e, int ticks) {
        Moves.effect(e, MobEffects.SLOWNESS, ticks, 1);
        e.setTicksFrozen(Math.max(e.getTicksFrozen(), ticks + 40));
        Forces.tag(e, Forces.Tag.FROSTED);
        long until = c.now() + ticks;
        Scheduler.after(ticks, () -> {
            if (e.isAlive() && c.now() >= until - 1 && e.getTicksFrozen() <= 60) Forces.untag(e, Forces.Tag.FROSTED);
        });
    }

    // ---- Updraft ----------------------------------------------------------------------------------------------------

    static Result updraft(Cast c) {
        ServerLevel level = c.level;
        Vec3 foot;
        double extra = 0;
        if (c.is(1)) {
            Vec3 aim = Combat.aimPoint(c, 14);
            foot = groundUnder(level, aim.add(0, 0.5, 0), 12);
        } else {
            foot = groundUnder(level, c.pos().add(0, 0.2, 0), 24);
            // your own column never starts above your feet (standing in a doorway's block, on a slab, half in a wall)
            if (foot.y > c.pos().y) foot = new Vec3(foot.x, c.pos().y, foot.z);
            // cast in the air, it comes up from the ground beneath you and still carries you its full height
            extra = Math.max(0, c.pos().y - foot.y);
        }
        double radius = c.radius(2.2) * (1 + c.num(4, "wide", 0f));
        // Thermal (2), Skyward Gate (5): taller; the Tall Draft variant half again
        double height = c.range(14) * (c.is(2) ? 1.5 : 1) + c.num(5, "taller", 0f) + extra;
        int ticks = c.ticks(120) + (int) c.num(4, "longer", 0f);
        float dmg = c.dmg();
        boolean inAir = c.way == Ways.AIR;
        Fx.at(c, FxKind.RING, foot).scale(radius + 0.6).ticks(10).flag(FxEvent.REACH).send();
        Fx.at(c, FxKind.GLYPH, foot).scale(radius).aux(height).ticks(ticks).noAnim().send();
        c.voice(foot, Beat.RELEASE, PowerSounds.HOVER, 1.1f, 0.8f);
        Moves.noFall(c, ticks + 100);
        Vec3 f = foot;
        Map<Integer, Integer> held = new java.util.HashMap<>();
        AABB box = new AABB(f.x - radius, f.y - 0.5, f.z - radius, f.x + radius, f.y + height, f.z + radius);
        Running r = Casting.start(c, ticks).at(foot);
        r.tick(rr -> {
            // you, and whoever is with you
            carry(c, rr, c.player, f, radius, height);
            for (ServerPlayer p : level.players()) if (p != c.player && Combat.isAlly(c.player, p)) carry(c, rr, p, f, radius, height);
            if (rr.age % 2 == 0) {
                for (LivingEntity e : Combat.foesAround(c.player, f.add(0, height * 0.5, 0), Math.max(radius, height * 0.5) + 1)) {
                    if (!inColumn(e, f, radius, height)) continue;
                    boolean first = rr.hit.add(e.getId());
                    if (first) {
                        c.hurt(e, dmg, null);
                        Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).target(e).dir(new Vec3(0, 1, 0)).scale(0.7).noAnim().send();
                    }
                    if (Combat.isBoss(e) || Combat.isHeavy(e)) continue;
                    // thrown up for a second and a half, then the air lets go of it; Gust (3) throws it higher
                    if (held.merge(e.getId(), 1, Integer::sum) < 15 + (int) c.num(3, "hold", 0f)) {
                        e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 5, 6 + (int) c.num(3, "lift", 0f), true, false, false));
                    }
                }
                for (Projectile shot : level.getEntitiesOfClass(Projectile.class, box)) {
                    if (shot.getOwner() == c.player || !inColumn(shot, f, radius, height)) continue;
                    Vec3 v = shot.getDeltaMovement();
                    Combat.push(shot, new Vec3(v.x * 0.7, Math.min(1.2, v.y + 0.45), v.z * 0.7));
                }
            }
        });
        // in the air it's a catch: the column takes the fall out of you at once
        if (inAir) c.player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false, false));
        return Result.OK;
    }

    /** The column carries a player inside it: the game's own Levitation, so the client moves itself and nothing rubber-bands. */
    private static void carry(Cast c, Running rr, ServerPlayer p, Vec3 foot, double radius, double height) {
        if (!p.isAlive() || p.isSpectator() || !inColumn(p, foot, radius, height)) return;
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 8, true, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, true, false, false));
        p.resetFallDistance();
        if (rr.hit.add(p.getId())) {
            Progression.count(p, "awaken:updraft_rides");
            // Skyward Gate (5): the top of the column throws you on along your look
            if (c.step(5)) gate(c, p, foot.y + height);
        }
    }

    /** Skyward Gate: once a rider nears the top, a push along their look and a ring to go through. */
    private static void gate(Cast c, ServerPlayer rider, double top) {
        Running g = Casting.start(c, c.type.id + ":gate:" + rider.getId(), 160);
        g.tick(rr -> {
            if (rider.isRemoved() || !rider.isAlive()) {
                rr.stop();
                return;
            }
            if (rider.getY() < top - 2.5) return;
            Vec3 look = rider.getLookAngle();
            Vec3 flat = Moves.flat(look);
            float push = c.num(5, "throw", 1.4f);
            Moves.launch(rider, new Vec3(flat.x * push, 0.45, flat.z * push));
            rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, true, false, false));
            Fx.at(c, FxKind.RING, rider.position().add(0, 1, 0)).dir(new Vec3(flat.x, 0.2, flat.z)).scale(2.4).ticks(10).plain().noAnim().send();
            c.voice(rider.position(), Beat.EXTRA_A, PowerSounds.WHOOSH, 1f, 1.1f);
            rr.stop();
        });
    }

    private static boolean inColumn(Entity e, Vec3 foot, double radius, double height) {
        double dx = e.getX() - foot.x, dz = e.getZ() - foot.z;
        return dx * dx + dz * dz <= radius * radius && e.getY() >= foot.y - 0.6 && e.getY() <= foot.y + height;
    }

    /** The top of the first solid ground under a point, looking at most {@code max} blocks down (the point itself when there is none). */
    private static Vec3 groundUnder(ServerLevel level, Vec3 from, int max) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x = (int) Math.floor(from.x), z = (int) Math.floor(from.z), y0 = (int) Math.floor(from.y);
        for (int dy = 0; dy <= max; dy++) {
            p.set(x, y0 - dy, z);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty()) {
                return new Vec3(from.x, p.getY() + s.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y), from.z);
            }
        }
        return from;
    }

    // ---- Backdraft --------------------------------------------------------------------------------------------------

    static Result backdraft(Cast c) {
        ServerPlayer p = c.player;
        ServerLevel level = c.level;
        Vec3 mouth = c.eye().add(c.look().scale(0.4));
        double draw = c.radius(8) * (1 + c.num(2, "draw", 0f));
        // Kindle (4): it can hold more
        int cap = 8 + (int) c.num(4, "cap", 0f);
        List<Vec3> taken = new ArrayList<>();
        int fires = 0;
        // burning bodies: enemies, friends and you
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(mouth, mouth).inflate(draw))) {
            if (!e.isOnFire() || e.position().distanceToSqr(c.pos()) > draw * draw) continue;
            e.clearFire();
            taken.add(e.getBoundingBox().getCenter());
            fires++;
        }
        fires += swallowBrands(p, c.pos(), draw, taken);
        // fire on the ground goes out; a campfire or lava feeds the breath and keeps burning
        BlockPos centre = BlockPos.containing(c.pos());
        int r = (int) Math.ceil(Math.min(draw, 10));
        int hearths = 0;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r && fires < cap + 4; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > draw * draw) continue;
                for (int dy = -3; dy <= 4; dy++) {
                    q.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState s = level.getBlockState(q);
                    if (s.is(BlockTags.FIRE)) {
                        if (Constructs.mayChange(p, q)) level.removeBlock(q, false);
                        taken.add(Vec3.atCenterOf(q));
                        fires++;
                    } else if (hearths < 2 && (s.is(Blocks.LAVA) || s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT))) {
                        taken.add(Vec3.atCenterOf(q));
                        hearths++;
                        fires++;
                    }
                }
            }
        }
        fires = Math.min(cap, fires);
        int n = fires;
        // every fire taken is drawn as a line of flame into the mouth
        for (int i = 0; i < Math.min(taken.size(), 12); i++) Fx.at(c, FxKind.TETHER, taken.get(i)).caster(null).to(mouth).ticks(5).noAnim().send();
        c.voice(c.pos(), Beat.PEAK, 0.6f + 0.1f * n, PowerSounds.EMBER, 0.8f, 0.6f);
        if (n > 0) Progression.count(p, "awaken:fires_breathed", n);
        float dmg = c.dmg() * (1 + 0.25f * n);
        double range = c.range(7) + 0.5 * n;
        float half = 26 + 1.5f * n;
        // Dragon's Breath (5): the breath is a long jet, and for a few seconds fire can't hurt you
        boolean dragon = c.step(5);
        if (dragon) Moves.effect(p, MobEffects.FIRE_RESISTANCE, (int) c.num(5, "proof", 100), 0);
        Scheduler.after(3, () -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3 look = p.getLookAngle();
            Vec3 from = p.getEyePosition().add(0, -0.15, 0);
            double reach = dragon ? range * c.num(5, "jet", 1.6f) : range;
            float cone = dragon ? half * 0.6f : half;
            for (LivingEntity e : Combat.foesInCone(p, p.position(), look, reach, cone)) {
                c.hurt(e, dmg, Forces.Force.HEAT);
                e.igniteForSeconds(3 + n * 0.5f);
                Combat.knockFrom(e, p.position(), 0.5 + 0.08 * n, 0.2);
            }
            Fx.at(c, FxKind.BREATH, from).dir(look).scale(reach).aux(cone).ticks(8 + n).flag(FxEvent.REACH).flag(FxEvent.FULL, n >= 4).send();
            c.voice(p.position(), Beat.RELEASE, 0.7f + 0.1f * n, PowerSounds.EMBER, 1.3f, 0.6f);
            Fx.feel(p, null, 0, 1.5f + 0.4f * n);
            // Cinder (3): the ground the breath crossed keeps burning
            if (c.step(3)) {
                int ticks = (int) c.num(3, "cinder", 60);
                float dps = c.dmg() * c.num(3, "dps", 0.2f);
                Vec3 flat = Moves.flat(look);
                Vec3 a = p.position().add(flat.scale(1.5)), b = p.position().add(flat.scale(reach * 0.85));
                Fx.at(c, FxKind.FIRE_TRAIL, a).to(b).ticks(ticks).noAnim().send();
                Running g = Casting.start(c, c.type.id + ":cinder", ticks);
                g.tick(rr -> {
                    if (rr.age % 10 != 5) return;
                    for (LivingEntity e : Combat.alongLine(p, a.add(0, 0.5, 0), b.add(0, 0.5, 0), 1.6)) {
                        Combat.hurtFrom(p, null, e, dps * 0.5f, Forces.Force.HEAT, false);
                        e.igniteForSeconds(2);
                    }
                });
            }
        });
        return Result.OK;
    }

    // ---- Thornwake --------------------------------------------------------------------------------------------------

    static Result thornwake(Cast c) {
        ServerPlayer p = c.player;
        ServerLevel level = c.level;
        double radius = c.radius(7);
        List<LivingEntity> foes = Combat.foesAround(p, c.pos().add(0, 1, 0), radius + 2);
        if (foes.isEmpty()) return Result.fail("no_target");
        foes.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        // how green is it here: every living plant close by is a root; turf alone gives a few
        List<Vec3> plants = new ArrayList<>();
        int turf = 0;
        BlockPos centre = BlockPos.containing(c.pos());
        int r = (int) Math.ceil(radius);
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -2; dy <= 3; dy++) {
                    q.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState s = level.getBlockState(q);
                    if (isPlant(s)) {
                        if (plants.size() < 48) plants.add(Vec3.atBottomCenterOf(q));
                    } else if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
                        turf++;
                    }
                }
            }
        }
        // Overgrowth (4): two more roots
        int max = 2 + Math.min(4, plants.size() / 2 + turf / 24) + (int) c.num(4, "more", 0f);
        boolean bare = plants.isEmpty() && turf < 6;
        if (bare) max = 1;
        int hold = c.ticks(bare ? 20 : 50) + (int) c.num(2, "hold", 0f);
        float dmg = c.dmg() * (bare ? 0.5f : 1f);
        // Briar (3): the thorns keep biting while they hold
        float briar = c.num(3, "briar", 0f);
        int roots = 0;
        for (LivingEntity e : foes) {
            if (roots >= max) break;
            Vec3 at = e.position();
            // the root comes from the plant nearest this enemy, or straight up out of the ground under it
            Vec3 from = at;
            double best = 5 * 5;
            for (Vec3 pl : plants) {
                double d = pl.distanceToSqr(at);
                if (d < best) {
                    best = d;
                    from = pl;
                }
            }
            int delay = roots * 2;
            Vec3 src = from;
            Scheduler.after(delay, () -> {
                if (!e.isAlive() || p.isRemoved()) return;
                c.hurt(e, dmg, Forces.Force.SPIRIT);
                if (!Combat.isBoss(e)) {
                    Moves.effect(e, MobEffects.SLOWNESS, hold, 6);
                    Combat.push(e, new Vec3(0, Math.min(0, e.getDeltaMovement().y), 0));
                } else {
                    Moves.effect(e, MobEffects.SLOWNESS, hold, 1);
                }
                Vec3 mid = e.getBoundingBox().getCenter();
                Fx.at(c, FxKind.TETHER, src).caster(null).to(mid).target(e).ticks(hold).noAnim().send();
                Fx.at(c, FxKind.IMPACT, mid).target(e).dir(new Vec3(0, 1, 0)).scale(0.8).noAnim().send();
                c.voice(e.position(), Beat.IMPACT, PowerSounds.SPIRIT, 0.8f, 0.7f);
            });
            if (briar > 0) {
                Running b = Casting.start(c, c.type.id + ":briar:" + e.getId(), hold);
                b.tick(rr -> {
                    if (rr.age % 10 == 9 && e.isAlive()) Combat.hurtFrom(p, null, e, dmg * briar, Forces.Force.SPIRIT, false);
                });
            }
            roots++;
        }
        Fx.at(c, FxKind.RING, c.pos()).scale(radius).ticks(12).flag(FxEvent.REACH).flag(FxEvent.FULL, !bare).send();
        c.voice(c.pos(), Beat.RELEASE, bare ? 0.6f : 1f, PowerSounds.SPIRIT, 1f, 0.6f);
        Fx.quake(level, c.pos(), 10, bare ? 0.6f : 1.4f);
        Progression.count(p, "awaken:roots_woken", roots);
        // Elder Grove (5): one great root comes up under the strongest thing here and holds it off the ground
        if (c.step(5) && !bare) {
            LivingEntity big = foes.get(0);
            for (LivingEntity e : foes) if (e.getMaxHealth() > big.getMaxHealth()) big = e;
            LivingEntity elder = big;
            int lift = (int) c.num(5, "lift", 40);
            Scheduler.after(6, () -> {
                if (!elder.isAlive() || p.isRemoved()) return;
                Combat.hurtFrom(p, null, elder, c.dmg() * c.num(5, "share", 1.2f), Forces.Force.SPIRIT, true);
                if (!Combat.isBoss(elder) && !Combat.isHeavy(elder)) {
                    elder.addEffect(new MobEffectInstance(MobEffects.LEVITATION, lift, 0, true, false, false));
                }
                Combat.stun(elder, lift);
                Fx.at(c, FxKind.PILLAR, elder.position()).target(elder).scale(3.5).ticks(lift).noAnim().send();
                c.voice(elder.position(), Beat.EXTRA_A, PowerSounds.SPIRIT, 1.2f, 0.5f);
                Fx.quake(level, elder.position(), 12, 2.2f);
            });
        }
        return Result.OK;
    }

    private static boolean isPlant(BlockState s) {
        return s.is(BlockTags.FLOWERS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS)
                || s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.liquid() || s.is(Blocks.VINE) || s.is(Blocks.MOSS_CARPET)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BAMBOO) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS);
    }
}
