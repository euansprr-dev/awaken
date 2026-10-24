package dev.awaken.powers;

import dev.awaken.core.api.MovementHooks;
import dev.awaken.powers.fx.FxKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Powers meeting the Awakened Body's moves (SPEC §13, docs/modules/power-fx.md "Move twists"). Body state comes only
 * through {@link MovementHooks}; damage and pushes go through {@link Combat}; visuals are ordinary power FX events.
 * <ul>
 *   <li>Chain: a cast within {@link #CHAIN_TICKS} of a move gives back a little energy, at most once a second.</li>
 *   <li>Kinetic, cast mid-slide: the slide's momentum leaves as a forward shockwave.</li>
 *   <li>Velocity Dash out of a slide: goes further (the client stretches its predicted motion, see the body module)
 *       and leaves a longer streak.</li>
 *   <li>Shadow, cast mid-slide: the slide trails smoke for 3 s that loses whatever was chasing you.</li>
 * </ul>
 */
final class MoveTwists {
    private MoveTwists() {}

    /** A cast this soon after a move (ticks) is a chain. */
    static final int CHAIN_TICKS = 10;
    /** Chain refund: this share of the cast's cost, between the two bounds; at most once per {@link #REFUND_GAP} ticks. */
    static final float REFUND_SHARE = 0.1F, REFUND_MIN = 0.5F, REFUND_MAX = 2.0F;
    static final int REFUND_GAP = 20;
    /** Slide-punch: reach, half angle, base damage (scaled like the power's own) and pushes. */
    static final double PUNCH_RANGE = 4.5, PUNCH_HALF_ANGLE = 40, PUNCH_KNOCK = 0.9, PUNCH_UP = 0.3;
    static final float PUNCH_DAMAGE = 3F;
    /** Smoke trail: how long it lingers, how wide each puff is, how often it blinds pursuers. */
    static final int SMOKE_TICKS = 60, SMOKE_EVERY = 5;
    static final double SMOKE_RADIUS = 1.8;

    private static final Map<ServerPlayer, long[]> LAST_REFUND = new WeakHashMap<>();

    static void afterCast(Cast c, float cost, long now) {
        ServerPlayer p = c.player;
        long last = MovementHooks.lastMoveTick(p);
        if (cost > 0 && last != Long.MIN_VALUE && now - last <= CHAIN_TICKS) refund(p, cost, now);
        if (!MovementHooks.sliding(p)) return;
        Vec3 dir = MovementHooks.slideDirection(p);
        if (dir == null) return;
        switch (c.type.family) {
            case KINETIC -> slidePunch(c, dir);
            case VELOCITY -> {
                if (c.type.dashLike) dashOut(c, dir);
            }
            case SHADOW -> smokeTrail(c);
            default -> {
            }
        }
    }

    /** Returns the energy given back (0 when gated). Restored energy is never "spent", so mastery ignores it. */
    static float refund(ServerPlayer p, float cost, long now) {
        long[] at = LAST_REFUND.computeIfAbsent(p, k -> new long[]{Long.MIN_VALUE / 2});
        if (now - at[0] < REFUND_GAP) return 0;
        at[0] = now;
        float amount = Math.max(REFUND_MIN, Math.min(REFUND_MAX, cost * REFUND_SHARE));
        return Powers.restoreEnergy(p, amount);
    }

    /** The slide's speed leaves the body as a wave along the slide: foes ahead are hit and thrown, others pushed. */
    static int slidePunch(Cast c, Vec3 dir) {
        Vec3 origin = c.pos().add(0, 0.6, 0);
        float dmg = c.dmg(PUNCH_DAMAGE);
        int hit = 0;
        for (LivingEntity e : Combat.foesInCone(c.player, origin, dir, PUNCH_RANGE, PUNCH_HALF_ANGLE)) {
            if (c.hurt(e, dmg, null)) hit++;
            Combat.knockFrom(e, origin, PUNCH_KNOCK, PUNCH_UP);
        }
        for (LivingEntity e : Combat.bystandersInCone(c.player, origin, dir, PUNCH_RANGE, PUNCH_HALF_ANGLE)) {
            Combat.knockFrom(e, origin, PUNCH_KNOCK * 0.55, PUNCH_UP * 0.5);
        }
        Fx.at(c, FxKind.CONE, origin).dir(dir).scale(PUNCH_RANGE).aux(PUNCH_HALF_ANGLE).ticks(14).noAnim().send();
        Fx.at(c, FxKind.SLAM, c.pos()).scale(1.6).noAnim().send();
        c.sound(PowerSounds.SHOCKWAVE, 0.9F, 1.25F);
        return hit;
    }

    /** The Dash out of a slide: a longer streak along the slide's way, and the landing kept safe a little longer. */
    static void dashOut(Cast c, Vec3 dir) {
        Moves.noFall(c, 40);
        Fx.at(c, FxKind.DASH, c.pos()).dir(dir.scale(1.9 * MovementHooks.DASH_OUT_STRETCH)).scale(1.35).ticks(12).noAnim().send();
        Fx.at(c, FxKind.TRAIL, c.pos()).aux(0).ticks(14).noAnim().send();
        c.sound(PowerSounds.WHOOSH, 0.7F, 0.85F);
    }

    /** Smoke puffs along the rest of the slide; pursuers inside them lose you (their target drops) while it lingers. */
    static void smokeTrail(Cast c) {
        double[] pts = new double[3 * 12];
        Running r = Casting.start(c, "body_smoke", SMOKE_TICKS);
        r.data = pts;
        // the first puff where the cast went off, then one every other tick while the slide lasts
        pts[0] = c.player.getX();
        pts[1] = c.player.getY();
        pts[2] = c.player.getZ();
        r.count = 1;
        Fx.at(c, FxKind.SMOKE, c.pos()).scale(1.3).noAnim().send();
        r.tick(rr -> {
            ServerPlayer p = c.player;
            if (MovementHooks.sliding(p) && rr.age % 2 == 0 && rr.count < 12) {
                int i = rr.count++ * 3;
                pts[i] = p.getX();
                pts[i + 1] = p.getY();
                pts[i + 2] = p.getZ();
                Fx.at(c, FxKind.SMOKE, p.position()).scale(1.1).noAnim().send();
            }
            if (rr.age % SMOKE_EVERY == 0) lose(c, pts, rr.count);
        });
    }

    /** Foes standing in the smoke that were chasing the caster forget them and stumble. Returns how many. */
    static int lose(Cast c, double[] pts, int count) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            Vec3 at = new Vec3(pts[i * 3], pts[i * 3 + 1] + 0.8, pts[i * 3 + 2]);
            for (LivingEntity e : Combat.foesAround(c.player, at, SMOKE_RADIUS)) {
                if (!(e instanceof Mob m) || m.getTarget() != c.player) continue;
                m.setTarget(null);
                m.getNavigation().stop();
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, false, false, true));
                n++;
            }
        }
        return n;
    }
}
