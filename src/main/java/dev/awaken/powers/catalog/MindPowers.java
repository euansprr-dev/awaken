package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.powers.Allies;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.ladder.Steps;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.powers.TimeSlow;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Mind: knowing what's coming. Scales with PER. */
public final class MindPowers {
    private MindPowers() {}

    static final int PINK = 0xD088C2;

    static void register() {
        PowerType.builder("danger_sense", "Danger Sense", Family.MIND, Slot.PASSIVE)
                .unlock(Rank.D).icon("danger")
                .desc("Anything about to hit you glows red a moment before it does: a raised claw, a drawn bow, a swelling creeper, an arrow in flight.")
                .passive(MindPowers::dangerSense).register();

        PowerType.builder("dominate", "Dominate", Family.MIND, Slot.ACTIVE)
                .cost(35).cooldown(600).unlock(Rank.C).icon("dominate")
                .desc("Bend a mob's will for 8 s (more with PER): it turns on its own kind. Elites resist for half as long; bosses can't be bent.")
                .variant(Variant.of("Mass", "Up to three weaker minds at once, for less time").dur(0.6f))
                .variant(Variant.of("Frenzy", "Your puppet hits half again as hard"))
                .cast(MindPowers::dominate).register();

        PowerType.builder("read_intent", "Read Intent", Family.MIND, Slot.ACTIVE)
                .cost(20).cooldown(400).unlock(Rank.D).icon("intent")
                .desc("For 10 s you see who is hunting you (red) and who isn't (amber). Read enemies take 15% more from you and deal 15% less.")
                .variant(Variant.of("Long", "Lasts 16 s").dur(1.6f))
                .variant(Variant.of("Wide", "Reads twice as far"))
                .cast(MindPowers::readIntent).register();

        PowerType.builder("decoy", "Decoy", Family.MIND, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(18).cooldown(240).damage(4).unlock(Rank.E).icon("decoy")
                .desc("Throw an image of yourself where you aim. Enemies attack it for 6 s, then it bursts and blinds them.")
                .variant(Variant.of("Lure", "Lasts twice as long, draws from further away").dur(2f))
                .variant(Variant.of("Trap", "A much bigger burst").dmg(2.5f))
                .cast(MindPowers::decoy).register();

        PowerType.builder("foresight", "Foresight", Family.MIND, Slot.ACTIVE)
                .cost(35).cooldown(600).unlock(Rank.C).hidden().icon("foresight")
                .desc("Seer's signature. For 3 s you see a heartbeat ahead: the world crawls at a tenth of its speed and the first blow that reaches you misses.")
                .variant(Variant.of("Long", "Lasts 5 s").dur(1.67f))
                .cast(MindPowers::foresight).register();
    }

    // ---- danger sense -----------------------------------------------------------------------------------------------

    static void dangerSense(Cast c) {
        ServerPlayer p = c.player;
        List<Integer> ids = new ArrayList<>();
        PowerState st = PowerState.of(p);
        long now = c.now();
        // Sooner (3): a threat is read a little further out, so the warning comes earlier
        double lead = c.num(3, "lead", 0) * 0.25;
        for (Mob m : c.level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(14), m -> m.getTarget() == p && m.isAlive())) {
            double d2 = m.distanceToSqr(p);
            double reach = m.getBbWidth() * 2 + 2.6 + lead;
            boolean aiming = m.isUsingItem() && m.hasLineOfSight(p);
            boolean threat = d2 < reach * reach
                    || aiming
                    || (m instanceof Creeper cr && cr.getSwellDir() > 0)
                    || (m.isAggressive() && d2 < (6 + lead) * (6 + lead));
            if (threat) ids.add(m.getId());
            // Read the Shot (4): a drawn bow's line, gold, before it looses
            if (aiming && c.step(4)) {
                Vec3 from = m.getEyePosition(), to = p.getBoundingBox().getCenter();
                Fx.of(p, "danger_sense", FxKind.BEAM, from).to(to).colour(0xFFFFD27A).aux(0.025).scale(from.distanceTo(to)).ticks(6).send();
            }
            // note who was in reach, to see the blow dodged: it pays mastery (SPEC 34.8), and Sixth Sense (5) slows the one who missed
            if (d2 < reach * reach) st.warned.put(m.getId(), now);
        }
        if (!st.warned.isEmpty()) sixthSense(c, st, now);
        for (Projectile pr : c.level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(12), x -> x.getOwner() != p && !x.onGround())) {
            Vec3 v = pr.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;
            Vec3 to = p.getBoundingBox().getCenter().subtract(pr.position());
            if (v.normalize().dot(to.normalize()) > 0.93) ids.add(pr.getId());
        }
        if (!ids.isEmpty()) {
            AwakenNet.send(p, new PowerPayloads.Marks(PowerPayloads.Marks.DANGER, ids, 8));
            // a faint ripple from the head, at most once a second
            if (p.tickCount % 20 < PowerType.PASSIVE_PERIOD) Fx.at(c, FxKind.PSI, p.getEyePosition()).ticks(6).flag(FxEvent.QUIET).send();
        }
    }

    // ---- active -----------------------------------------------------------------------------------------------------

    static Result dominate(Cast c) {
        LivingEntity aimed = Combat.aimEntity(c, 16, 0.8);
        if (!(aimed instanceof Mob target)) return Result.fail("no_target");
        if (Combat.isBoss(target)) return Result.fail("too_strong");
        List<Mob> minds = new ArrayList<>();
        minds.add(target);
        if (c.is(1)) {
            for (LivingEntity e : Combat.foesAround(c.player, target.position(), 6)) {
                if (minds.size() >= 3) break;
                if (e instanceof Mob m && m != target && !Combat.isBoss(m) && !isElite(m)) minds.add(m);
            }
        }
        int per = Progression.stat(c.player, Stat.PER);
        int base = c.ticks(160) + per * 2;
        // rings of will travel from your head into theirs
        Fx.at(c, FxKind.PSI, c.player.getEyePosition()).to(target.getEyePosition()).ticks(12).send();
        long now = c.now();
        List<Integer> ids = new ArrayList<>();
        for (Mob m : minds) {
            // Puppet King (5): an elite bends for the full time
            int ticks = isElite(m) && !c.step(5) ? base / 2 : base;
            Allies.add(m, c.player.getUUID(), Allies.Kind.DOMINATED, now + ticks);
            m.setTarget(null);
            ids.add(m.getId());
            Fx.at(c, FxKind.AURA, m.position()).caster(m).aux(FxKind.Aura.MARK.ordinal()).ticks(ticks).noAnim().send();
            Fx.at(c, FxKind.IMPACT, m.getEyePosition()).scale(0.8).noAnim().send();
            if (c.is(2)) Moves.addMod(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, "frenzy", 0.5,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            if (c.step(5)) Moves.addMod(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, "puppet_king", c.num(5, "hard", 1.5f) - 1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        AwakenNet.send(c.player, new PowerPayloads.Marks(PowerPayloads.Marks.ALLY, ids, base));
        c.sound(target.position(), PowerSounds.MIND, 1.1f, 0.8f);
        Running r = Casting.start(c, base);
        r.data = minds;
        r.tick(rr -> {
            if (rr.age % 10 != 0) return;
            boolean any = false;
            for (Mob m : minds) {
                if (!m.isAlive() || !Allies.isDominated(m)) continue;
                any = true;
                LivingEntity t = m.getTarget();
                if (t == null || !t.isAlive() || Combat.isAlly(c.player, t)) {
                    LivingEntity next = nearestFoeOf(c, m);
                    m.setTarget(next);
                }
            }
            if (!any) rr.stop();
        }).end((rr, why) -> {
            for (Mob m : minds) {
                Allies.remove(m);
                Moves.removeMod(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, "frenzy");
                Moves.removeMod(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, "puppet_king");
                // Last Order (4): the will leaves it in a psychic burst
                if (m.isAlive() && c.step(4) && why != Running.Why.CANCELLED) {
                    Vec3 at = m.getBoundingBox().getCenter();
                    for (LivingEntity e : Combat.foesAround(c.player, at, c.num(4, "radius", 3.5f))) {
                        Combat.hurtFrom(c.player, null, e, c.dmg() * c.num(4, "burst", 0.5f), null, true);
                    }
                    Fx.at(c, FxKind.PSI, at).ticks(10).scale(1.4).plain().send();
                }
                if (m.isAlive()) {
                    m.setTarget(null);
                    c.level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 2, 0.2, 0.2, 0.2, 0);
                    Fx.end(c, m, FxKind.Aura.MARK.ordinal() + 1);
                }
            }
        });
        return Result.OK;
    }

    static boolean isElite(LivingEntity e) {
        var role = e.getExistingData(CoreApi.MOB_ROLE.get());
        return role.isPresent() && (role.get().role() == MobRole.Role.ELITE || role.get().role() == MobRole.Role.WARDEN);
    }

    static LivingEntity nearestFoeOf(Cast c, Mob puppet) {
        LivingEntity best = null;
        double bestD = 16 * 16;
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, puppet.getBoundingBox().inflate(16), x -> x != puppet && Combat.isFoe(c.player, x))) {
            double d = e.distanceToSqr(puppet);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    static Result readIntent(Cast c) {
        int ticks = c.ticks(200);
        // Rings (2): it reads further
        double range = (c.is(2) ? 48 : 24) + c.num(2, "range", 0);
        boolean walls = c.step(4);
        PowerState.of(c.player).buff(PowerState.Buffs.READ_INTENT, c.now() + ticks, 0.15);
        if (c.step(5)) PowerState.of(c.player).buff(PowerState.Buffs.READ_CRIT, c.now() + ticks, 1.5);
        c.sound(PowerSounds.MIND, 1f, 1.2f);
        Fx.at(c, FxKind.PSI, c.player.getEyePosition()).ticks(16).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.READ.ordinal()).ticks(ticks).noAnim().send();
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age % 10 != 0) return;
            List<Integer> hunting = new ArrayList<>();
            List<Integer> other = new ArrayList<>();
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(c.pos(), range * 2, range, range * 2),
                    x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive() && !Combat.isAlly(c.player, x))) {
                // Through Walls (4): before it, only the foes you can see are read
                if (!walls && !c.player.hasLineOfSight(e)) continue;
                if (e instanceof Mob m && m.getTarget() == c.player) {
                    hunting.add(e.getId());
                    // Tells (3): a red flash on a read foe about to strike
                    if (c.step(3) && m.isAggressive() && m.distanceToSqr(c.player) < 16) {
                        Fx.at(c, FxKind.IMPACT, m.getEyePosition()).colour(0xFFFF3B3B).scale(0.5).plain().flag(FxEvent.QUIET).send();
                    }
                } else other.add(e.getId());
            }
            if (!hunting.isEmpty()) AwakenNet.send(c.player, new PowerPayloads.Marks(PowerPayloads.Marks.INTENT_HUNTING, hunting, 14));
            if (!other.isEmpty()) AwakenNet.send(c.player, new PowerPayloads.Marks(PowerPayloads.Marks.INTENT_OTHER, other, 14));
        }).end((rr, why) -> {
            PowerState.of(c.player).clearBuff(PowerState.Buffs.READ_INTENT);
            PowerState.of(c.player).clearBuff(PowerState.Buffs.READ_CRIT);
        });
        return Result.OK;
    }

    static Result decoy(Cast c) {
        Vec3 at = Combat.aimPoint(c, 10);
        at = at.subtract(c.look().scale(0.6));
        // settle onto the ground below the aim point
        var pos = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i < 6 && c.level.getBlockState(pos.below()).getCollisionShape(c.level, pos.below()).isEmpty(); i++) pos = pos.below();
        Vec3 spot = new Vec3(at.x, pos.getY(), at.z);
        // Confusion (4): what the burst hits turns on its own kind for a moment
        java.util.function.Consumer<LivingEntity> confuse = c.step(4) ? f -> confuse(c, f) : null;
        var body = Moves.decoy(c, spot, c.ticks(120), c.dmg(), c.is(2) ? 4.5 : 3.0, c.is(1) ? 24 : 16, "decoy", confuse);
        if (body == null) return Result.fail("blocked");
        // Hall of Mirrors (5): two more in a triangle round the first, all bursting together
        if (c.step(5)) {
            Vec3 side = c.flatLook().yRot((float) (Math.PI / 2));
            Vec3 back = c.flatLook().scale(1.3);
            for (int k = -1; k <= 1; k += 2) {
                Vec3 at2 = spot.add(back).add(side.scale(k * 1.5));
                Moves.decoy(c, at2, c.ticks(120), c.dmg(), c.is(2) ? 4.5 : 3.0, c.is(1) ? 24 : 16, "decoy", confuse);
                Fx.at(c, FxKind.BLINK, spot).to(at2).noAnim().send();
            }
        }
        // the image is thrown: a ripple leaves the hand and the copy shimmers into being
        Fx.at(c, FxKind.PSI, c.player.getEyePosition()).to(spot.add(0, 1.4, 0)).ticks(8).send();
        Fx.at(c, FxKind.BLINK, c.pos()).to(spot).noAnim().send();
        c.sound(spot, PowerSounds.MIND, 0.9f, 1.4f);
        return Result.OK;
    }

    static Result foresight(Cast c) {
        int ticks = c.ticks(60);
        PowerState st = PowerState.of(c.player);
        st.buff(PowerState.Buffs.FORESIGHT, c.now() + ticks, c.step(5) ? c.num(5, "misses", 2) : 1);
        TimeSlow.Zone zone = TimeSlow.add(c.player, 20, 10);
        Moves.postOn(c.player, Moves.FX_FORESIGHT);
        c.sound(PowerSounds.FLOW_START, 1f, 1.5f);
        c.sound(PowerSounds.MIND, 1f, 0.6f);
        Fx.at(c, FxKind.RING, c.pos()).scale(6).ticks(16).colour(0xFFFFD27A).send();
        Fx.at(c, FxKind.AURA, c.pos()).aux(FxKind.Aura.FORESIGHT.ordinal()).ticks(ticks).noAnim().send();
        Running r = Casting.start(c, ticks);
        // Ghost Lines (2): faint gold lines show where the foes near you are headed
        if (c.step(2)) r.tick(rr -> {
            if (rr.age % 10 != 0) return;
            int n = 0;
            for (LivingEntity e : Steps.nearest(c, c.pos(), 14, 6, null)) {
                Vec3 v = e.getDeltaMovement();
                if (v.horizontalDistanceSqr() < 0.0004) continue;
                Vec3 from = e.position().add(0, 0.1, 0);
                Vec3 to = from.add(Moves.flat(v).normalize().scale(Math.min(4, 1 + v.horizontalDistance() * 20)));
                Fx.at(c, FxKind.BEAM, from).to(to).colour(0xFFFFD27A).aux(0.02).scale(from.distanceTo(to)).ticks(10).plain().flag(FxEvent.QUIET).send();
                if (++n >= 6) break;
            }
        });
        r.end((rr, why) -> {
            Fx.end(c, c.player, FxKind.Aura.FORESIGHT.ordinal() + 1);
            TimeSlow.remove(zone);
            st.clearBuff(PowerState.Buffs.FORESIGHT);
            Moves.postOff(c.player, Moves.FX_FORESIGHT);
            c.sound(PowerSounds.FLOW_END, 0.8f, 1.5f);
        });
        return Result.OK;
    }

    /** Foresight's dodge: called by the damage hook. Steps the player aside from the attacker. */
    public static void dodge(ServerPlayer player, LivingEntity attackerOrNull) {
        Vec3 away = attackerOrNull == null ? player.getLookAngle().scale(-1) : player.position().subtract(attackerOrNull.position());
        Vec3 side = Moves.flat(away).yRot((float) (Math.PI / 2 * (player.getRandom().nextBoolean() ? 1 : -1)));
        Vec3 to = Moves.blinkTarget(player, side.add(Moves.flat(away)).normalize(), 2.5);
        Fx.at(player.level(), FxKind.BLINK, FxStyle.MIND, player.position()).caster(player).to(to).colour(0xFFFFD27A).send();
        Moves.teleport(player, to);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), PowerSounds.WHOOSH.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.8f);
        // Counter (4): the blow that missed is met with a gold flash, and its owner staggers
        var foresight = dev.awaken.powers.PowerRegistry.get("foresight");
        if (attackerOrNull != null && foresight != null) {
            Cast c = Cast.of(player, foresight);
            if (c.step(4)) {
                Combat.stun(attackerOrNull, (int) c.num(4, "stagger", 20));
                Fx.at(c, FxKind.IMPACT, attackerOrNull.getBoundingBox().getCenter()).colour(0xFFFFD27A).scale(1.2).plain().send();
            }
        }
    }

    /** Sixth Sense (5): a warned foe that lost its reach without landing a blow was dodged, so it's slowed. */
    /** A warned foe that leaves reach without landing its blow: a dodge (mastery; Sixth Sense at 5 slows it). */
    private static void sixthSense(Cast c, PowerState st, long now) {
        var it = st.warned.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            if (now - en.getValue() > 40) {
                it.remove();
                continue;
            }
            if (!(c.level.getEntity(en.getKey()) instanceof Mob m) || !m.isAlive()) {
                it.remove();
                continue;
            }
            double reach = m.getBbWidth() * 2 + 3.2;
            boolean hit = m.getLastHurtMob() == c.player && m.getLastHurtMobTimestamp() >= m.tickCount - (now - en.getValue());
            if (hit) {
                it.remove();
            } else if (m.distanceToSqr(c.player) > reach * reach) {
                it.remove();
                dev.awaken.powers.Mastery.use(c.player, "awaken:danger_sense", dev.awaken.powers.Mastery.DODGED);
                if (!c.step(5)) continue;
                Moves.effect(m, MobEffects.SLOWNESS, (int) c.num(5, "slow", 20), 2);
                Fx.at(c, FxKind.PSI, c.pos()).ticks(8).plain().send();
                Fx.at(c, FxKind.PSI, c.pos()).ticks(8).send();
            }
        }
    }

    /** Decoy's Confusion (4): a burst foe turns on the nearest of its own side for a couple of seconds. */
    private static void confuse(Cast c, LivingEntity f) {
        if (!(f instanceof Mob m) || Combat.isBoss(m)) return;
        LivingEntity other = null;
        double best = 8 * 8;
        for (LivingEntity o : Combat.foesAround(c.player, m.position(), 8)) {
            double d = o.distanceToSqr(m);
            if (o != m && d < best) {
                best = d;
                other = o;
            }
        }
        if (other == null) return;
        m.setTarget(other);
        m.setLastHurtByMob(other);
        Fx.at(c, FxKind.PSI, m.getEyePosition().add(0, 0.4, 0)).ticks((int) c.num(4, "confuse", 40)).send();
        LivingEntity was = other;
        dev.awaken.powers.Scheduler.after((int) c.num(4, "confuse", 40), () -> {
            if (m.isAlive() && m.getTarget() == was) m.setTarget(null);
        });
    }

    /** Dominate's Marked Prey (3): what your puppet strikes is marked for you. */
    public static void puppetStruck(LivingEntity striker, LivingEntity victim) {
        if (!(striker instanceof Mob m)) return;
        Allies.Entry e = Allies.get(m);
        if (e == null || e.kind() != Allies.Kind.DOMINATED || !(m.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(e.owner());
        if (owner == null) return;
        PowerState st = PowerState.peek(owner);
        Running r = st == null ? null : st.running("awaken:dominate");
        if (r == null || r.c.powerLevel < 3) return;
        dev.awaken.powers.ladder.Riders.mark(owner, victim, r.c.now() + (long) r.c.num(3, "mark", 60));
        Fx.at(r.c, FxKind.IMPACT, victim.getEyePosition()).scale(0.6).noAnim().flag(FxEvent.QUIET).send();
    }
}
