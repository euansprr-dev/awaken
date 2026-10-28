package dev.awaken.powers.ladder;

import dev.awaken.core.Forces;
import dev.awaken.powers.Allies;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.Running;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.Bolts;
import dev.awaken.powers.Cast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * The ladder steps that answer the damage hooks (SPEC 36.3) rather than a cast: Brace's plates and glances, Thunderclap's chained
 * blows, Flow's tally, Afterimage's copies. {@code PowerEvents} reads the buffs a cast set and calls these for the effect.
 */
public final class StepHooks {
    private StepHooks() {}

    /** Mountain Stance, Halo Ward: a plate caught a hit ({@code left} plates remain). */
    public static void plateCatch(ServerPlayer sp, DamageSource src, int left, String power) {
        Vec3 at = hitPoint(sp, src);
        Fx.of(sp, power, FxKind.IMPACT, at).scale(1.4).send();
        Fx.of(sp, power, power.equals("brace") ? FxKind.SLAM : FxKind.NOVA, at).scale(0.7).flag(FxEvent.QUIET).send();
        sp.level().playSound(null, at.x, at.y, at.z, PowerSounds.IMPACT.get(), SoundSource.PLAYERS, 1.0f, left > 0 ? 1.3f : 0.9f);
    }

    /** Unmoved, Calm Air: a projectile sparks off the power that softened it. */
    public static void glance(ServerPlayer sp, DamageSource src, String power) {
        Fx.of(sp, power, FxKind.IMPACT, hitPoint(sp, src)).scale(0.7).send();
    }

    /** Where a hit meets the body: the side it came from, at chest height. */
    private static Vec3 hitPoint(ServerPlayer sp, DamageSource src) {
        Vec3 centre = sp.getBoundingBox().getCenter();
        Vec3 from = src.getDirectEntity() != null ? src.getDirectEntity().position() : src.getSourcePosition();
        if (from == null) return centre.add(sp.getLookAngle().scale(0.6));
        Vec3 flat = from.subtract(centre).multiply(1, 0, 1);
        return flat.lengthSqr() < 1e-4 ? centre : centre.add(flat.normalize().scale(0.7));
    }

    /** Heaven's Clap: a blow arcs to the closest other foe for {@code dmg}. */
    public static void chainBlow(ServerPlayer sp, LivingEntity victim, float dmg) {
        chainBlow(sp, victim, dmg, "thunderclap");
    }

    /** A hit's shock arcs on to the closest other foe for {@code dmg}, in {@code power}'s light (Heaven's Clap, Arc Shock). */
    public static void chainBlow(ServerPlayer sp, LivingEntity victim, float dmg, String power) {
        Vec3 from = victim.getBoundingBox().getCenter();
        LivingEntity next = nearestFoe(sp, from, 6, victim);
        if (next == null) return;
        LivingEntity to = next;
        // a tick after this hit lands, so the arc's own damage never folds into the same event
        Scheduler.after(1, () -> {
            if (!to.isAlive() || sp.isRemoved()) return;
            Combat.hurtFrom(sp, null, to, dmg, Forces.Force.CHARGE, true);
            Fx.of(sp, power, FxKind.LIGHTNING, from).to(to.getBoundingBox().getCenter()).ticks(6).send();
        });
    }

    /** Decoy's Echo Cast: a power hit while your decoy stands is echoed from it at a share, once a cast. */
    public static void decoyEcho(dev.awaken.powers.Cast c, LivingEntity target, float amount) {
        if (c.type.id.getPath().equals("decoy")) return;
        PowerState st = PowerState.peek(c.player);
        if (st == null) return;
        for (Running r : st.runningList()) {
            if (!r.key.startsWith("decoy:") || r.stopping() || r.c.powerLevel < 3 || !(r.target instanceof Mannequin body) || !body.isAlive()) continue;
            if (ECHOED.put(c, Boolean.TRUE) != null) return;
            float share = amount * r.c.num(3, "echo", 0.3f);
            Scheduler.after(4, () -> {
                if (!body.isAlive() || !target.isAlive()) return;
                Combat.hurtFrom(c.player, body, target, share, null, true);
                Fx.of(c.player, "decoy", FxKind.PSI, body.getEyePosition()).to(target.getEyePosition()).ticks(8).send();
            });
            return;
        }
    }

    private static final Map<dev.awaken.powers.Cast, Boolean> ECHOED = new java.util.WeakHashMap<>();

    /** Overload's Live Wire: an energy hit during the afterglow jumps to the foe nearest it. */
    public static void liveWire(dev.awaken.powers.Cast c, LivingEntity target, float amount) {
        if (c.type.family != dev.awaken.powers.PowerType.Family.ENERGY) return;
        PowerState st = PowerState.peek(c.player);
        double share = st == null ? 0 : st.buff(PowerState.Buffs.LIVE_WIRE, c.now());
        if (share <= 0) return;
        LivingEntity next = nearestFoe(c.player, target.getBoundingBox().getCenter(), 6, target);
        if (next == null) return;
        Vec3 from = target.getBoundingBox().getCenter();
        Scheduler.after(1, () -> {
            if (!next.isAlive() || c.player.isRemoved()) return;
            Combat.hurtFrom(c.player, null, next, amount * (float) share, Forces.Force.CHARGE, true);
            Fx.of(c.player, "overload", FxKind.LIGHTNING, from).to(next.getBoundingBox().getCenter()).ticks(6).send();
        });
    }

    /** Wall Stride's looks (2+), from the server so every viewer sees them: a wake down the wall while the run lasts. */
    public static void strideTick(ServerPlayer sp, long now) {
        if (now % 3 != 0) return;
        var o = dev.awaken.powers.Powers.loadout(sp).get(STRIDE);
        if (o == null) return;
        // a second on the wall in a run pays Wall Stride's mastery (SPEC 36.8)
        if (now % 21 == 0) dev.awaken.powers.Mastery.use(sp, STRIDE, dev.awaken.powers.Mastery.WALL_SECOND);
        if (o.level() < 2) return;
        Fx.looks(sp, "wall_run", FxKind.TRAIL, sp.position()).ticks(12).dir(sp.getDeltaMovement()).send();
    }

    /** Wall Stride's level for the body's wall-run (0 without it): server from the save, client from its own loadout. */
    public static int strideLevel(net.minecraft.world.entity.player.Player p) {
        var l = p.level().isClientSide() ? dev.awaken.powers.Powers.clientLoadoutOrNull() : dev.awaken.powers.Powers.loadout(p);
        var o = l == null ? null : l.get(STRIDE);
        return o == null ? 0 : o.level();
    }

    private static final String STRIDE = "awaken:wall_run";

    // ---- Architect: constructs that keep working after the cast (BuildZones) -----------------------------------------

    // ---- Spirit -------------------------------------------------------------------------------------------------------

    /** Soul Guard (Aura 5): your wisps strike back at whoever hits you up close. */
    public static void soulGuard(ServerPlayer sp, LivingEntity attacker, float dmg) {
        if (dmg <= 0 || !attacker.isAlive()) return;
        Combat.hurtFrom(sp, null, attacker, dmg, Forces.Force.SPIRIT, true);
        Fx.of(sp, "aura", FxKind.IMPACT, attacker.getBoundingBox().getCenter()).scale(0.8).send();
    }

    /** Shatter (Frost Nova 3): a hit on a foe your nova froze bursts the frost round it; Winter Bloom (5) bursts it again. */
    public static void frostHit(ServerPlayer sp, LivingEntity victim) {
        PowerState st = PowerState.peek(sp);
        if (st == null || !Forces.has(victim, Forces.Tag.FROSTED)) return;
        long now = sp.level().getServer().overworld().getGameTime();
        float dmg = (float) st.buff(PowerState.Buffs.FROST_SHATTER, now);
        if (dmg <= 0) return;
        Forces.untag(victim, Forces.Tag.FROSTED);
        Vec3 at = victim.getBoundingBox().getCenter();
        float bloom = (float) st.buff(PowerState.Buffs.FROST_BLOOM, now);
        // after this hit lands, so the burst's own damage never folds into it
        Scheduler.after(1, () -> {
            frostBurst(sp, at, dmg, 2.5f);
            if (bloom > 0) Scheduler.after(8, () -> frostBurst(sp, at, dmg * bloom, 3.5f));
        });
    }

    private static void frostBurst(ServerPlayer sp, Vec3 at, float dmg, float radius) {
        if (sp.isRemoved()) return;
        for (LivingEntity e : Combat.foesAround(sp, at, radius)) {
            Combat.hurtFrom(sp, null, e, dmg, Forces.Force.FROST, true);
            Moves.effect(e, MobEffects.SLOWNESS, 40, 1);
        }
        Fx.looks(sp, "frost_nova", FxKind.FROST, at.subtract(0, 0.8, 0)).scale(radius).send();
        sp.level().playSound(null, at.x, at.y, at.z, PowerSounds.SHATTER.get(), SoundSource.PLAYERS, 0.9f, 1.2f);
    }

    /** Twin Souls (Twin Strike 5): while the twin walks with you, it repeats your power hits at a share, up to six foes a cast. */
    public static void twinCast(dev.awaken.powers.Cast c, LivingEntity target, float amount) {
        if (c.type.path.equals("twin_strike")) return;
        PowerState st = PowerState.peek(c.player);
        double share = st == null ? 0 : st.buff(PowerState.Buffs.TWIN_CASTS, c.now());
        if (share <= 0) return;
        java.util.Set<Integer> done = TWINNED.computeIfAbsent(c, k -> new java.util.HashSet<>());
        if (done.size() >= 6 || !done.add(target.getId())) return;
        dev.awaken.powers.catalog.SpiritPowers.twinEcho(c.player, target, amount, share);
    }

    private static final Map<dev.awaken.powers.Cast, java.util.Set<Integer>> TWINNED = new java.util.WeakHashMap<>();

    /** Turret's Shield (4): it shrugs off a share of every hit, in a flash of hex light. */
    public static float turretShield(dev.awaken.powers.content.PowerDrone d, float amount) {
        if (d.shield <= 0) return amount;
        ServerPlayer o = d.ownerPlayer();
        if (o != null) Fx.of(o, "turret", FxKind.IMPACT, d.getBoundingBox().getCenter()).scale(0.9).send();
        return amount * (1 - d.shield);
    }

    /** Hard light's own gold, for the shots a construct sends back. */
    private static final int LIGHT = 0xFFFFD28A;

    /**
     * Ricochet (Wall 3), Mirror Wall (5): a foe's shot strikes your wall. An arrow turns round and flies back at whoever
     * loosed it, now yours; at Mirror anything shot comes back as a lance of light. True when the hit was taken over.
     */
    public static boolean wallStruck(Projectile shot, BlockPos pos, Vec3 at, Direction face) {
        if (!(shot.level() instanceof ServerLevel level)) return false;
        BuildZones.Zone z = BuildZones.at(level, pos);
        if (z == null || z.level < 3 || !z.power.equals("wall")) return false;
        Cast c = z.cast;
        Entity shooter = shot.getOwner();
        if (shooter == null || !shooter.isAlive() || Combat.isAlly(c.player, shooter)) return false;
        Vec3 out = at.add(face.getUnitVec3().scale(0.3));
        Vec3 aim = shooter.getBoundingBox().getCenter();
        Vec3 dir = aim.subtract(out);
        if (dir.lengthSqr() < 1e-4) return false;
        if (z.level >= 5) {
            shot.discard();
            var b = Bolts.fire(level, c.player, null, out, dir, 3.2f, c.dmg(c.num(5, "mirror", 5f)), c.force(), LIGHT, 20, 0f);
            b.cast = c;
            Fx.of(c.player, "wall", FxKind.IMPACT, out).dir(dir).scale(1.1).send();
            level.playSound(null, out.x, out.y, out.z, PowerSounds.SHOT.get(), SoundSource.PLAYERS, 0.9f, 1.6f);
            return true;
        }
        if (!(shot instanceof AbstractArrow arrow)) return false;
        double speed = Math.max(1.2, shot.getDeltaMovement().length());
        // a little lift so the arrow's drop over the distance still meets the shooter
        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        arrow.setOwner(c.player);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setPos(out);
        arrow.shoot(dir.x, dir.y + flat * 0.06, dir.z, (float) speed, 0f);
        Fx.of(c.player, "wall", FxKind.IMPACT, out).dir(dir).scale(0.6).send();
        level.playSound(null, out.x, out.y, out.z, PowerSounds.IMPACT.get(), SoundSource.PLAYERS, 0.6f, 1.7f);
        return true;
    }

    /** Crystal (Wall 4): a blast can't break the wall. */
    public static boolean blastProof(ServerLevel level, BlockPos pos) {
        BuildZones.Zone z = BuildZones.at(level, pos);
        return z != null && z.level >= 4 && z.power.equals("wall");
    }

    /** Spring (Floor 3): a landing on the floor (sneak to stop) bounces you up. True when it took the landing. */
    public static boolean spring(ServerPlayer sp, double distance) {
        if (distance < 1.0 || sp.isShiftKeyDown()) return false;
        ServerLevel level = sp.level();
        BlockPos under = BlockPos.containing(sp.getX(), sp.getY() - 0.2, sp.getZ());
        if (!dev.awaken.powers.Constructs.isConstruct(level, under)) return false;
        BuildZones.Zone z = BuildZones.at(level, under);
        if (z == null || z.level < 3 || !z.power.equals("floor")) return false;
        double up = z.cast.num(3, "spring", 0.9f) + Math.min(0.35, distance * 0.025);
        Vec3 v = sp.getDeltaMovement();
        Combat.push(sp, new Vec3(v.x, up, v.z));
        sp.resetFallDistance();
        Vec3 at = new Vec3(sp.getX(), under.getY() + 1.02, sp.getZ());
        Fx.of(z.cast.player, "floor", FxKind.RING, at).scale(1.6).ticks(8).send();
        level.playSound(null, at.x, at.y, at.z, PowerSounds.BUILD.get(), SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    /** Shatterproof (Cone 4): a shot from outside the roof can't hurt whoever stands under it. */
    public static boolean sheltered(ServerPlayer sp, DamageSource src) {
        BuildZones.Zone z = BuildZones.sheltering(sp.level(), sp.position(), "cone", 4);
        if (z == null) return false;
        Entity shooter = src.getEntity();
        if (shooter != null && z.shelters(shooter.position())) return false;
        Fx.of(z.cast.player, "cone", FxKind.IMPACT, hitPoint(sp, src).add(0, 0.8, 0)).scale(0.8).send();
        return true;
    }

    /** Flow's Red Hour: adds what you dealt this foe while Flow runs. */
    @SuppressWarnings("unchecked")
    public static void dealt(ServerPlayer sp, LivingEntity victim, float amount) {
        PowerState st = PowerState.peek(sp);
        Running r = st == null ? null : st.running("awaken:flow");
        if (r == null || !(r.data instanceof Map<?, ?>)) return;
        ((Map<Integer, Float>) r.data).merge(victim.getId(), amount, Float::sum);
    }

    /** The copies Afterimage left standing (Mimic and Snare), or null. */
    private static @org.jspecify.annotations.Nullable List<?> copies(ServerPlayer sp, int level) {
        PowerState st = PowerState.peek(sp);
        Running r = st == null ? null : st.running("awaken:afterimage");
        if (r == null || r.stopping() || r.c.powerLevel < level || !(r.data instanceof List<?> list)) return null;
        return list;
    }

    /** Afterimage's Mimic: each copy still standing strikes the foe nearest it for a share of your blow. */
    public static void mimic(ServerPlayer sp, LivingEntity victim, float amount) {
        List<?> copies = copies(sp, 3);
        if (copies == null) return;
        PowerState st = PowerState.peek(sp);
        Running r = st.running("awaken:afterimage");
        float share = r.c.num(3, "mimic", 0.5f) * amount;
        for (Object o : copies) {
            if (!(o instanceof Mannequin m) || !m.isAlive()) continue;
            LivingEntity t = victim.distanceToSqr(m) < 16 ? victim : nearestFoe(sp, m.position(), 4, null);
            if (t == null) continue;
            Mannequin copy = m;
            // a beat behind your own swing, as an echo would be
            Scheduler.after(3, () -> {
                if (!copy.isAlive() || !t.isAlive()) return;
                copy.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
                Moves.swing(copy, InteractionHand.MAIN_HAND);
                Combat.hurtFrom(sp, copy, t, share, null, true);
                Fx.of(sp, "afterimage", FxKind.IMPACT, t.getBoundingBox().getCenter()).dir(t.position().subtract(copy.position())).scale(0.8).send();
            });
        }
    }

    /** Afterimage's Snare: a mob that strikes one of your copies is slowed. */
    public static void copyStruck(LivingEntity victim, LivingEntity striker) {
        Allies.Entry ally = Allies.get(victim);
        if (ally == null || ally.kind() != Allies.Kind.DECOY || !(victim.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ally.owner());
        if (owner == null) return;
        List<?> copies = copies(owner, 4);
        if (copies == null || !copies.contains(victim)) return;
        Running r = PowerState.peek(owner).running("awaken:afterimage");
        Moves.effect(striker, MobEffects.SLOWNESS, (int) r.c.num(4, "snare", 40), 2);
        Fx.of(owner, "afterimage", FxKind.LIGHTNING, victim.getBoundingBox().getCenter()).to(striker.getBoundingBox().getCenter()).ticks(6).send();
    }

    public static @org.jspecify.annotations.Nullable LivingEntity nearestFoe(ServerPlayer sp, Vec3 at, double radius,
                                                                             @org.jspecify.annotations.Nullable LivingEntity skip) {
        LivingEntity best = null;
        double d = radius * radius;
        for (LivingEntity e : Combat.foesAround(sp, at, radius)) {
            if (e == skip) continue;
            double q = e.distanceToSqr(at);
            if (q < d) {
                d = q;
                best = e;
            }
        }
        return best;
    }
}
