package dev.awaken.powers.catalog;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.powers.Allies;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
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
        for (Mob m : c.level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(14), m -> m.getTarget() == p && m.isAlive())) {
            double d2 = m.distanceToSqr(p);
            double reach = m.getBbWidth() * 2 + 2.6;
            boolean threat = d2 < reach * reach
                    || (m.isUsingItem() && m.hasLineOfSight(p))
                    || (m instanceof Creeper cr && cr.getSwellDir() > 0)
                    || (m.isAggressive() && d2 < 36);
            if (threat) ids.add(m.getId());
        }
        for (Projectile pr : c.level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(12), x -> x.getOwner() != p && !x.onGround())) {
            Vec3 v = pr.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;
            Vec3 to = p.getBoundingBox().getCenter().subtract(pr.position());
            if (v.normalize().dot(to.normalize()) > 0.93) ids.add(pr.getId());
        }
        if (!ids.isEmpty()) AwakenNet.send(p, new PowerPayloads.Marks(PowerPayloads.Marks.DANGER, ids, 8));
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
        long now = c.now();
        List<Integer> ids = new ArrayList<>();
        for (Mob m : minds) {
            int ticks = isElite(m) ? base / 2 : base;
            Allies.add(m, c.player.getUUID(), Allies.Kind.DOMINATED, now + ticks);
            m.setTarget(null);
            ids.add(m.getId());
            c.level.sendParticles(Fx.dust(PINK, 1.4f), m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 16, 0.4, 0.4, 0.4, 0);
            if (c.is(2)) Moves.addMod(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, "frenzy", 0.5,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        AwakenNet.send(c.player, new PowerPayloads.Marks(PowerPayloads.Marks.ALLY, ids, base));
        c.sound(target.position(), PowerSounds.MIND, 1.1f, 0.8f);
        Running r = Casting.start(c, base);
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
                c.level.sendParticles(Fx.dust(PINK, 0.9f), m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 2, 0.2, 0.1, 0.2, 0);
            }
            if (!any) rr.stop();
        }).end((rr, why) -> {
            for (Mob m : minds) {
                Allies.remove(m);
                Moves.removeMod(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, "frenzy");
                if (m.isAlive()) {
                    m.setTarget(null);
                    c.level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 2, 0.2, 0.2, 0.2, 0);
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
        double range = c.is(2) ? 48 : 24;
        PowerState.of(c.player).buff(PowerState.Buffs.READ_INTENT, c.now() + ticks, 0.15);
        c.sound(PowerSounds.MIND, 1f, 1.2f);
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age % 10 != 0) return;
            List<Integer> hunting = new ArrayList<>();
            List<Integer> other = new ArrayList<>();
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(c.pos(), range * 2, range, range * 2),
                    x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive() && !Combat.isAlly(c.player, x))) {
                if (e instanceof Mob m && m.getTarget() == c.player) hunting.add(e.getId());
                else other.add(e.getId());
            }
            if (!hunting.isEmpty()) AwakenNet.send(c.player, new PowerPayloads.Marks(PowerPayloads.Marks.INTENT_HUNTING, hunting, 14));
            if (!other.isEmpty()) AwakenNet.send(c.player, new PowerPayloads.Marks(PowerPayloads.Marks.INTENT_OTHER, other, 14));
        }).end((rr, why) -> PowerState.of(c.player).clearBuff(PowerState.Buffs.READ_INTENT));
        return Result.OK;
    }

    static Result decoy(Cast c) {
        Vec3 at = Combat.aimPoint(c, 10);
        at = at.subtract(c.look().scale(0.6));
        // settle onto the ground below the aim point
        var pos = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i < 6 && c.level.getBlockState(pos.below()).getCollisionShape(c.level, pos.below()).isEmpty(); i++) pos = pos.below();
        Vec3 spot = new Vec3(at.x, pos.getY(), at.z);
        var body = Moves.decoy(c, spot, c.ticks(120), c.dmg(), c.is(2) ? 4.5 : 3.0, c.is(1) ? 24 : 16, "decoy");
        if (body == null) return Result.fail("blocked");
        c.sound(spot, PowerSounds.MIND, 0.9f, 1.4f);
        return Result.OK;
    }

    static Result foresight(Cast c) {
        int ticks = c.ticks(60);
        PowerState st = PowerState.of(c.player);
        st.buff(PowerState.Buffs.FORESIGHT, c.now() + ticks, 1);
        TimeSlow.Zone zone = TimeSlow.add(c.player, 20, 10);
        Moves.postOn(c.player, Moves.FX_FORESIGHT);
        c.sound(PowerSounds.FLOW_START, 1f, 1.5f);
        c.sound(PowerSounds.MIND, 1f, 0.6f);
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age % 3 == 0) c.level.sendParticles(Fx.dust(0xFFD27A, 0.8f), c.player.getX(), c.player.getEyeY(), c.player.getZ(), 2, 0.4, 0.3, 0.4, 0);
        }).end((rr, why) -> {
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
        player.level().sendParticles(Fx.dust(0xFFD27A, 1.2f), player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.6, 0.3, 0);
        Moves.teleport(player, to);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), PowerSounds.WHOOSH.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.8f);
    }
}
