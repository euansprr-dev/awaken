package dev.awaken.powers;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Building blocks shared by many powers: blinks, area hits, decoys, flight, modifiers, post effects. */
public final class Moves {
    private Moves() {}

    // ---- area hits --------------------------------------------------------------------------------------------------

    /** Damages and knocks every foe within radius; returns how many were hit. */
    public static int blast(Cast c, Vec3 centre, double radius, float damage, Forces.@Nullable Force force, double knock, double up) {
        int n = 0;
        for (LivingEntity e : Combat.foesAround(c.player, centre, radius)) {
            if (c.hurt(e, damage, force)) n++;
            if (knock != 0 || up != 0) Combat.knockFrom(e, centre, knock, up);
        }
        // the wave moves everything it passes through; only foes are hurt
        if (knock != 0 || up != 0) for (LivingEntity e : Combat.bystandersAround(c.player, centre, radius)) Combat.knockFrom(e, centre, knock * 0.6, up * 0.6);
        return n;
    }

    // ---- movement ---------------------------------------------------------------------------------------------------

    public static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /**
     * The way a dash goes: {@code look} turned by the movement keys held (+1 strafe is left, +1 forward is forward). No keys,
     * or forward alone, is the look itself. Sideways and back dashes run flat; a forward lean keeps the look's pitch in
     * proportion. Always a unit vector.
     */
    public static Vec3 steer(Vec3 look, float strafe, float forward) {
        if (strafe == 0 && forward == 0) return look;
        Vec3 f = flat(look);
        Vec3 left = new Vec3(f.z, 0, -f.x);
        Vec3 h = f.scale(forward).add(left.scale(strafe));
        if (h.lengthSqr() < 1e-6) return look;
        h = h.normalize();
        double y = look.y * Math.max(0, h.dot(f));
        double hl = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(h.x * hl, y, h.z * hl);
    }

    /** The furthest safe spot along a direction (the player's box fits there), stopping at walls. */
    public static Vec3 blinkTarget(ServerPlayer p, Vec3 dir, double max) {
        Vec3 start = p.position();
        Vec3 best = start;
        Vec3 u = dir.normalize();
        AABB box = p.getBoundingBox();
        for (double d = 0.5; d <= max; d += 0.5) {
            Vec3 at = start.add(u.scale(d));
            AABB moved = box.move(at.subtract(start));
            if (!p.level().noCollision(p, moved)) break;
            best = at;
        }
        return best;
    }

    /** Like {@link #blinkTarget} but may pass through solid walls up to {@code wallLimit} blocks thick. */
    public static Vec3 phaseTarget(ServerPlayer p, Vec3 dir, double max, int wallLimit) {
        Vec3 start = p.position();
        Vec3 best = start;
        Vec3 u = dir.normalize();
        AABB box = p.getBoundingBox();
        double inWall = 0;
        for (double d = 0.5; d <= max + wallLimit; d += 0.5) {
            Vec3 at = start.add(u.scale(d));
            AABB moved = box.move(at.subtract(start));
            if (p.level().noCollision(p, moved)) {
                best = at;
                inWall = 0;
                if (d >= max) break;
            } else {
                inWall += 0.5;
                if (inWall > wallLimit) break;
            }
        }
        return best;
    }

    public static void teleport(ServerPlayer p, Vec3 to) {
        p.teleportTo(p.level(), to.x, to.y, to.z, Set.of(), p.getYRot(), p.getXRot(), false);
        p.resetFallDistance();
    }

    public static void teleportFacing(ServerPlayer p, Vec3 to, Vec3 lookAt) {
        Vec3 d = lookAt.subtract(to);
        float yRot = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float xRot = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        p.teleportTo(p.level(), to.x, to.y, to.z, Set.of(), yRot, xRot, false);
        p.resetFallDistance();
    }

    /** Sends a velocity to the player's client (used when the client didn't already predict it). */
    public static void launch(ServerPlayer p, Vec3 v) {
        Combat.push(p, v);
    }

    /** Swing an arm for everyone watching, the player included. */
    public static void swing(LivingEntity e, net.minecraft.world.InteractionHand hand) {
        e.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    public static void noFall(Cast c, int ticks) {
        PowerState.of(c.player).buff(PowerState.Buffs.NO_FALL, c.now() + ticks, 1);
        c.player.resetFallDistance();
    }

    // ---- attributes and effects -------------------------------------------------------------------------------------

    public static Identifier mod(String name) {
        return Awaken.id("power." + name);
    }

    public static void addMod(LivingEntity e, Holder<Attribute> attr, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(mod(name), amount, op));
    }

    public static void removeMod(LivingEntity e, Holder<Attribute> attr, String name) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.removeModifier(mod(name));
    }

    public static void effect(LivingEntity e, Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp, false, true, true));
    }

    // ---- flight (Hover / Flight) ------------------------------------------------------------------------------------

    public static void startFlight(ServerPlayer p, String name, float speed) {
        if (p.isCreative() || p.isSpectator()) return;
        addMod(p, NeoForgeMod.CREATIVE_FLIGHT, name, 1.0, AttributeModifier.Operation.ADD_VALUE);
        p.getAbilities().flying = true;
        p.getAbilities().setFlyingSpeed(speed);
        p.onUpdateAbilities();
    }

    public static void setFlightSpeed(ServerPlayer p, float speed) {
        if (p.isCreative() || p.isSpectator()) return;
        p.getAbilities().setFlyingSpeed(speed);
        p.onUpdateAbilities();
    }

    public static void endFlight(ServerPlayer p, String name) {
        removeMod(p, NeoForgeMod.CREATIVE_FLIGHT, name);
        if (p.isCreative() || p.isSpectator()) return;
        if (!p.mayFly()) p.getAbilities().flying = false;
        p.getAbilities().setFlyingSpeed(0.05f);
        p.onUpdateAbilities();
    }

    // ---- post effects -----------------------------------------------------------------------------------------------

    public static final Identifier FX_FLOW = Awaken.id("flow");
    public static final Identifier FX_FORESIGHT = Awaken.id("foresight");
    public static final Identifier FX_OVERLOAD = Awaken.id("overload");
    public static final Identifier FX_THIRD_EYE = Awaken.id("third_eye");
    public static final Identifier FX_CLOAK = Awaken.id("cloak");
    public static final Identifier FX_VOID = Awaken.id("void");
    public static final List<Identifier> ALL_FX = List.of(FX_FLOW, FX_FORESIGHT, FX_OVERLOAD, FX_THIRD_EYE, FX_CLOAK, FX_VOID);

    public static void postOn(ServerPlayer p, Identifier id) {
        p.addPostEffect(id);
    }

    public static void postOff(ServerPlayer p, Identifier id) {
        p.removePostEffect(id);
    }

    /** Post effects are saved in player NBT: strip ours whenever no power is running them (login, respawn). */
    public static void clearPowerPostEffects(ServerPlayer p) {
        for (Identifier id : ALL_FX) p.removePostEffect(id);
    }

    // ---- decoys -----------------------------------------------------------------------------------------------------

    /**
     * A decoy that wears the caster's skin (a vanilla mannequin). Hostiles nearby switch to it; when it ends it pops
     * for {@code popDamage} in a small radius.
     */
    public static @Nullable Mannequin decoy(Cast c, Vec3 at, int ticks, float popDamage, double popRadius, double tauntRadius, String key) {
        return decoy(c, at, ticks, popDamage, popRadius, tauntRadius, key, null);
    }

    /** As above; {@code onPop} runs for each foe the burst hits (a ladder step's extra, SPEC 36). */
    public static @Nullable Mannequin decoy(Cast c, Vec3 at, int ticks, float popDamage, double popRadius, double tauntRadius, String key,
                                            java.util.function.@Nullable Consumer<LivingEntity> onPop) {
        ServerLevel level = c.level;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("immovable", true);
        tag.putBoolean("hide_description", true);
        Entity e = EntityType.loadEntityRecursive(EntityTypes.MANNEQUIN, tag, level, EntitySpawnReason.TRIGGERED, ent -> {
            ent.snapTo(at.x, at.y, at.z, c.player.getYRot(), 0f);
            return ent;
        });
        if (!(e instanceof Mannequin body)) return null;
        body.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(c.player.getGameProfile()));
        body.setYBodyRot(c.player.getYRot());
        body.setYHeadRot(c.player.getYRot());
        body.addTag(FlyingBlocks.TEMP_TAG);
        AttributeInstance hp = body.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(20 * PowerMath.levelScale(dev.awaken.progression.Progression.profile(c.player).level()));
        body.setHealth(body.getMaxHealth());
        level.addFreshEntity(body);
        Allies.add(body, c.player.getUUID(), Allies.Kind.DECOY, c.now() + ticks + 20);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 10, 0.3, 0.6, 0.3, 0.02);
        Running r = Casting.start(c, key + ":" + body.getUUID(), ticks);
        r.target = body;
        r.tick(rr -> {
            if (!body.isAlive()) {
                rr.stop();
                return;
            }
            if (rr.age % 10 == 0) taunt(c.player, body, tauntRadius);
            if (rr.age % 6 == 0) level.sendParticles(Fx.dust(c.type.family.colour, 0.8f), body.getX(), body.getY() + 1.0, body.getZ(), 2, 0.3, 0.6, 0.3, 0);
        }).end((rr, why) -> {
            Vec3 p = body.position().add(0, 1, 0);
            Allies.remove(body);
            if (popDamage > 0 && why != Running.Why.CANCELLED) {
                for (LivingEntity f : Combat.foesAround(c.player, p, popRadius)) {
                    c.hurt(f, popDamage, Forces.Force.SPIRIT);
                    effect(f, MobEffects.BLINDNESS, 40, 0);
                    if (onPop != null) onPop.accept(f);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 12, 0.3, 0.6, 0.3, 0.03);
            level.playSound(null, p.x, p.y, p.z, PowerSounds.POP.get(), SoundSource.PLAYERS, 0.9f, 1.1f);
            body.discard();
            for (Mob m : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(tauntRadius), m -> m.getTarget() == body)) m.setTarget(null);
        });
        taunt(c.player, body, tauntRadius);
        return body;
    }

    static void taunt(ServerPlayer owner, LivingEntity decoy, double radius) {
        for (Mob m : decoy.level().getEntitiesOfClass(Mob.class, decoy.getBoundingBox().inflate(radius), x -> Combat.isFoe(owner, x))) {
            if (m.getTarget() != decoy) m.setTarget(decoy);
        }
    }

    // ---- summons ----------------------------------------------------------------------------------------------------

    /** Most summons one player may have at once (SPEC: at most 60 combat entities in a fight). */
    public static final int SUMMON_CAP = 12;

    /** Retires a player's oldest summons so {@code incoming} new ones fit under the cap. */
    public static void makeRoom(ServerPlayer p, int incoming) {
        List<dev.awaken.powers.content.PowerSummon> mine = new java.util.ArrayList<>(p.level().getEntitiesOfClass(
                dev.awaken.powers.content.PowerSummon.class, p.getBoundingBox().inflate(96), s -> s.getOwner() == p && s.isAlive()));
        mine.sort(java.util.Comparator.comparingInt(dev.awaken.powers.content.PowerSummon::lifeLeft));
        int excess = mine.size() + incoming - SUMMON_CAP;
        for (int i = 0; i < excess && i < mine.size(); i++) mine.get(i).discard();
    }

    // ---- visuals ----------------------------------------------------------------------------------------------------

    /** A harmless lightning bolt for the look and sound of a strike. */
    public static void visualLightning(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.setVisualOnly(true);
        bolt.snapTo(at.x, at.y, at.z);
        level.addFreshEntity(bolt);
    }

    public static <T extends ParticleOptions> void trail(ServerLevel level, Vec3 a, Vec3 b, T p, double step) {
        Fx.line(level, a, b, p, step);
    }

    public static BlockPos below(Entity e) {
        return BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
    }

    public static boolean isPlayerNear(ServerLevel level, Vec3 at, double r) {
        for (Player p : level.players()) if (p.position().distanceToSqr(at) < r * r) return true;
        return false;
    }
}
