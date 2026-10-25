package dev.awaken.mobs.entity;

import dev.awaken.core.Forces;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** What each ability kind does when its wind-up ends. Damage is the caster's ATTACK_DAMAGE times the ability power. */
final class Abilities {
    private Abilities() {}

    // ---- targeting and damage ----------------------------------------------------------------------------------

    static boolean isEnemy(@Nullable AwakenMob mob, LivingEntity e) {
        if (!e.isAlive() || e == mob || e instanceof AwakenMob) return false;
        if (e instanceof Player p) return !p.isSpectator() && !p.isCreative();
        if (e instanceof OwnableEntity oe && oe.getOwner() instanceof Player) return true;
        if (e instanceof AbstractGolem) return true;
        return mob != null && mob.getTarget() == e;
    }

    static List<LivingEntity> enemiesNear(ServerLevel level, @Nullable AwakenMob mob, Vec3 pos, float radius) {
        double r2 = radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(pos, radius * 2, radius * 2 + 4, radius * 2),
                e -> isEnemy(mob, e) && e.distanceToSqr(pos.x, e.getY(), pos.z) <= r2 && Math.abs(e.getY() - pos.y) < 4.0);
    }

    static DamageSource source(ServerLevel level, @Nullable AwakenMob mob, @Nullable Entity direct, BoltKind bolt) {
        if (bolt.force != null) return level.damageSources().source(Forces.key(bolt.force), direct != null ? direct : mob, mob);
        if (mob == null) return level.damageSources().magic();
        if (direct instanceof Projectile p) return level.damageSources().mobProjectile(p, mob);
        return level.damageSources().mobAttack(mob);
    }

    static void hurt(ServerLevel level, @Nullable AwakenMob mob, LivingEntity e, float amount, BoltKind bolt, @Nullable MobEffectInstance effect) {
        hurtBy(level, mob, null, e, amount, bolt, effect);
    }

    static void hurtBy(ServerLevel level, @Nullable AwakenMob mob, @Nullable Entity direct, LivingEntity e, float amount, BoltKind bolt, @Nullable MobEffectInstance effect) {
        // a blow that was deflected, evaded or blocked whole (Arms) brings none of its burn or effects with it
        if (amount > 0 && !e.hurtServer(level, source(level, mob, direct, bolt), amount)) return;
        if (bolt == BoltKind.FIRE) e.igniteForSeconds(3.0F);
        if (effect != null) e.addEffect(new MobEffectInstance(effect), mob);
        else if (bolt.effect != null && bolt.effectTicks > 0) e.addEffect(new MobEffectInstance(bolt.effect, bolt.effectTicks, 0), mob);
    }

    static void push(LivingEntity e, Vec3 dir, double strength, double up) {
        Vec3 d = new Vec3(dir.x, 0, dir.z);
        if (d.lengthSqr() < 1.0E-4) d = new Vec3(1, 0, 0);
        d = d.normalize().scale(strength * (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        e.setDeltaMovement(e.getDeltaMovement().add(d.x, up, d.z));
        e.needsSync = true;
        if (e instanceof ServerPlayer sp) sp.syncVelocity = true;
    }

    static float damage(AwakenMob mob, Ability a) {
        return (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * a.power();
    }

    static BoltKind bolt(AwakenMob mob, Ability a) {
        return a.bolt() != null ? a.bolt() : mob.def().theme.bolt;
    }

    static @Nullable MobEffectInstance effect(Ability a) {
        return a.effect() == null ? null : new MobEffectInstance(a.effect(), a.effectTicks(), a.effectAmp());
    }

    static Vec3 chest(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    // ---- executors -----------------------------------------------------------------------------------------------

    static void area(ServerLevel level, AwakenMob mob, Ability a, Vec3 centre, float radius, float inner, double knock) {
        BoltKind b = bolt(mob, a);
        float dmg = damage(mob, a);
        double in2 = inner * inner;
        for (LivingEntity e : enemiesNear(level, mob, centre, radius)) {
            if (inner > 0 && e.distanceToSqr(centre.x, e.getY(), centre.z) < in2) continue;
            hurt(level, mob, e, dmg, b, effect(a));
            if (knock > 0) push(e, e.position().subtract(centre), knock, 0.35);
        }
    }

    static void slamFx(ServerLevel level, AwakenMob mob, Vec3 c, float radius) {
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, radius * 0.3, 0.1, radius * 0.3, 0);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 12, radius * 0.5, 0.1, radius * 0.5, 0.05);
        Telegraph.ring(level, c, radius, 24, mob.def().glow, false);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, mob.getSoundSource(), 1.2F, 0.8F);
    }

    static void bolts(ServerLevel level, AwakenMob mob, Ability a, Vec3 aim) {
        Vec3 from = mob.getEyePosition().subtract(0, mob.getBbHeight() * 0.1, 0);
        Vec3 dir = aim.subtract(from);
        int n = Math.max(1, a.count());
        float spread = n == 1 ? 0 : 0.14F;
        for (int i = 0; i < n; i++) {
            float ang = (i - (n - 1) / 2.0F) * spread;
            Vec3 d = dir.yRot(ang);
            AwBolt.shoot(level, mob, from, d, bolt(mob, a), damage(mob, a), effect(a), 0.9);
        }
        mob.playLevelSound(SoundEvents.BLAZE_SHOOT, 0.8F, 1.2F);
    }

    static void nova(ServerLevel level, AwakenMob mob, int count, BoltKind kind, float power) {
        Vec3 from = chest(mob);
        float dmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * power;
        int n = Math.max(4, count);
        float off = mob.getRandom().nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < n; i++) {
            float ang = off + i * Mth.TWO_PI / n;
            AwBolt.shoot(level, mob, from, new Vec3(Mth.cos(ang), 0.02, Mth.sin(ang)), kind, dmg, null, 0.6);
        }
        mob.playLevelSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.2F);
    }

    static void arrows(ServerLevel level, AwakenMob mob, Ability a, LivingEntity target) {
        ItemStack bow = mob.getMainHandItem().is(Items.BOW) || mob.getMainHandItem().is(Items.CROSSBOW) ? mob.getMainHandItem() : new ItemStack(Items.BOW);
        int n = Math.max(1, a.count());
        for (int i = 0; i < n; i++) {
            AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, new ItemStack(Items.ARROW), 1.0F, bow);
            arrow.setBaseDamage(Math.max(1.0, damage(mob, a) * 0.42));
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            if (a.effect() != null && arrow instanceof Arrow tipped) tipped.addEffect(new MobEffectInstance(a.effect(), a.effectTicks(), a.effectAmp()));
            double xd = target.getX() - mob.getX();
            double yd = target.getY(0.3333) - arrow.getY();
            double zd = target.getZ() - mob.getZ();
            double dist = Math.sqrt(xd * xd + zd * zd);
            Vec3 d = new Vec3(xd, 0, zd).yRot((i - (n - 1) / 2.0F) * 0.12F);
            Projectile.spawnProjectileUsingShoot(arrow, level, new ItemStack(Items.ARROW), d.x, yd + dist * 0.2, d.z, 1.6F, 4.0F);
        }
        mob.playLevelSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
    }

    static void beam(ServerLevel level, AwakenMob mob, Ability a, Vec3 aim) {
        Vec3 from = mob.getEyePosition();
        Vec3 dir = aim.subtract(from).normalize();
        Vec3 to = from.add(dir.scale(a.range() + 2));
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        AABB box = mob.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mob, from, to, box, e -> e instanceof LivingEntity le && isEnemy(mob, le), from.distanceToSqr(to));
        if (hit != null && hit.getEntity() instanceof LivingEntity victim) {
            to = hit.getLocation();
            hurt(level, mob, victim, damage(mob, a), bolt(mob, a), effect(a));
            push(victim, dir, 0.6, 0.2);
        }
        Telegraph.line(level, from, to, mob.def().glow, true);
        BoltKind b = bolt(mob, a);
        Vec3 d = to.subtract(from);
        for (int i = 0; i <= 10; i++) {
            Vec3 p = from.add(d.scale(i / 10.0));
            level.sendParticles(b.trail, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        mob.playLevelSound(a.heavy() ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.GUARDIAN_ATTACK, a.heavy() ? 1.5F : 1.0F, 1.2F);
    }

    static void rainStrike(ServerLevel level, AwakenMob mob, Ability a, Vec3 at) {
        BoltKind b = bolt(mob, a);
        if (b == BoltKind.CHARGE || b == BoltKind.SIGNAL) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(at.x, at.y, at.z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        } else {
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(b.trail, at.x, at.y + 0.5, at.z, 16, a.radius() * 0.4, 0.5, a.radius() * 0.4, 0.05);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), mob.getSoundSource(), 0.6F, 1.4F);
        }
        for (LivingEntity e : enemiesNear(level, mob, at, a.radius())) hurt(level, mob, e, damage(mob, a), b, effect(a));
    }

    static void fangs(ServerLevel level, AwakenMob mob, Ability a, Vec3 aim) {
        Vec3 from = mob.position();
        Vec3 d = aim.subtract(from);
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 1.0E-4) d = mob.getLookAngle();
        d = d.normalize();
        float yaw = (float) Mth.atan2(d.z, d.x);
        double minY = Math.min(mob.getY(), aim.y) - 2, maxY = Math.max(mob.getY(), aim.y) + 1;
        for (int i = 0; i < a.count(); i++) {
            double dist = 1.25 * (i + 1);
            spike(level, mob, a, from.x + d.x * dist, from.z + d.z * dist, minY, maxY, yaw, i);
        }
    }

    static void fangRing(ServerLevel level, AwakenMob mob, Ability a) {
        double minY = mob.getY() - 2, maxY = mob.getY() + 1;
        int n = Math.max(6, a.count());
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n;
            spike(level, mob, a, mob.getX() + Mth.cos(ang) * 1.6, mob.getZ() + Mth.sin(ang) * 1.6, minY, maxY, ang, 0);
            spike(level, mob, a, mob.getX() + Mth.cos(ang + 0.4F) * 2.8, mob.getZ() + Mth.sin(ang + 0.4F) * 2.8, minY, maxY, ang, 3);
        }
    }

    private static void spike(ServerLevel level, AwakenMob mob, Ability a, double x, double z, double minY, double maxY, float rot, int delay) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        double y = -1;
        do {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                y = pos.getY();
                break;
            }
            pos = below;
        } while (pos.getY() >= Mth.floor(minY) - 1);
        if (y < 0 && pos.getY() < level.getMinY()) return;
        if (y < 0) y = minY + 2;
        level.addFreshEntity(new EvokerFangs(level, x, y, z, rot, delay, mob));
        // Vanilla fangs bite for a flat 6; top up so the attack scales with the caster's level.
        float extra = damage(mob, a) - 6.0F;
        if (extra > 0) Hazards.strike(level, mob, new Vec3(x, y, z), 0.9F, delay + 2, extra, bolt(mob, a), effect(a));
    }

    static void blink(ServerLevel level, AwakenMob mob, Ability a, @Nullable LivingEntity target, Vec3 dest) {
        Vec3 old = mob.position();
        AABB moved = mob.getBoundingBox().move(dest.subtract(old));
        if (!level.noCollision(mob, moved)) {
            dest = target != null ? target.position() : old;
            moved = mob.getBoundingBox().move(dest.subtract(old));
            if (!level.noCollision(mob, moved)) return;
        }
        level.sendParticles(ParticleTypes.PORTAL, old.x, old.y + mob.getBbHeight() * 0.5, old.z, 24, 0.4, 0.8, 0.4, 0.3);
        mob.teleportTo(dest.x, dest.y, dest.z);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + mob.getBbHeight() * 0.5, dest.z, 24, 0.4, 0.8, 0.4, 0.1);
        mob.playLevelSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.1F);
        if (target != null) {
            mob.getLookControl().setLookAt(target);
            double reach = mob.getBbWidth() + target.getBbWidth() + 1.5;
            if (mob.distanceToSqr(target) < reach * reach) {
                hurt(level, mob, target, damage(mob, a), bolt(mob, a), effect(a));
                mob.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
    }

    static Vec3 blinkSpot(AwakenMob mob, LivingEntity target) {
        Vec3 behind = target.getLookAngle();
        behind = new Vec3(behind.x, 0, behind.z);
        if (behind.lengthSqr() < 1.0E-4) behind = new Vec3(1, 0, 0);
        behind = behind.normalize().scale(-(1.6 + mob.getBbWidth()));
        return target.position().add(behind);
    }

    static void summon(ServerLevel level, AwakenMob mob, Ability a, List<Vec3> spots) {
        MobDef d = a.summon() == null ? null : dev.awaken.mobs.def.Bestiary.get(a.summon());
        if (d == null) return;
        for (Vec3 p : spots) {
            AwakenMob m = Mobs.spawnMinion(level, d, p, mob);
            if (m != null) level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.5, p.z, 10, 0.3, 0.4, 0.3, 0.02);
        }
        mob.playLevelSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 1.0F);
    }

    static List<Vec3> summonSpots(ServerLevel level, AwakenMob mob, int count) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float ang = mob.getRandom().nextFloat() * Mth.TWO_PI;
            double r = 2.0 + mob.getBbWidth() + mob.getRandom().nextFloat() * 2.0;
            double x = mob.getX() + Mth.cos(ang) * r, z = mob.getZ() + Mth.sin(ang) * r;
            out.add(new Vec3(x, mob.getY(), z));
        }
        return out;
    }

    static void decoys(ServerLevel level, AwakenMob mob, int count) {
        for (int i = 0; i < count; i++) {
            AwakenMob copy = (AwakenMob) mob.getType().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (copy == null) continue;
            float ang = mob.getRandom().nextFloat() * Mth.TWO_PI;
            double r = 2.0 + mob.getBbWidth();
            Vec3 p = mob.position().add(Mth.cos(ang) * r, 0, Mth.sin(ang) * r);
            copy.snapTo(p.x, p.y, p.z, mob.getYRot(), 0);
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.VALUES) {
                copy.setItemSlot(slot, mob.getItemBySlot(slot).copy());
            }
            copy.makeDecoy(200);
            copy.setTarget(mob.getTarget());
            level.addFreshEntity(copy);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 8, 0.3, 0.6, 0.3, 0.02);
        }
        mob.playLevelSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.0F);
    }

    static void heal(ServerLevel level, AwakenMob mob, Ability a) {
        float r = a.radius();
        for (AwakenMob ally : level.getEntitiesOfClass(AwakenMob.class, mob.getBoundingBox().inflate(r), m -> m.isAlive() && !m.isDecoy())) {
            ally.heal(ally.getMaxHealth() * a.power());
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + ally.getBbHeight(), ally.getZ(), 4, 0.3, 0.3, 0.3, 0);
        }
        mob.playLevelSound(SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
    }

    static boolean alliesHurt(ServerLevel level, AwakenMob mob, float r) {
        if (mob.getHealth() < mob.getMaxHealth() * 0.7F) return true;
        for (AwakenMob ally : level.getEntitiesOfClass(AwakenMob.class, mob.getBoundingBox().inflate(r), m -> m.isAlive() && m.getHealth() < m.getMaxHealth() * 0.7F)) {
            return true;
        }
        return false;
    }

    static void pull(ServerLevel level, AwakenMob mob, Ability a) {
        for (LivingEntity e : enemiesNear(level, mob, mob.position(), a.radius())) {
            push(e, mob.position().subtract(e.position()), 0.6 + a.power(), 0.25);
            hurt(level, mob, e, damage(mob, a) * 0.4F, bolt(mob, a), effect(a));
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 40, a.radius() * 0.4, 1.0, a.radius() * 0.4, 0.3);
        mob.playLevelSound(MobSounds.TELEGRAPH_HEAVY.get(), 1.0F, 0.5F);
    }

    static void gust(ServerLevel level, AwakenMob mob, Ability a) {
        for (LivingEntity e : enemiesNear(level, mob, mob.position(), a.radius())) {
            push(e, e.position().subtract(mob.position()), 1.0 + a.power() * 1.5, 0.45);
            hurt(level, mob, e, damage(mob, a), bolt(mob, a), effect(a));
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
        mob.playLevelSound(SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.9F);
    }

    static void cone(ServerLevel level, AwakenMob mob, Ability a, Vec3 aim) {
        Vec3 dir = aim.subtract(mob.position());
        dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 1.0E-4) dir = mob.getLookAngle();
        dir = dir.normalize();
        BoltKind b = bolt(mob, a);
        for (LivingEntity e : enemiesNear(level, mob, mob.position(), a.radius())) {
            Vec3 to = e.position().subtract(mob.position());
            to = new Vec3(to.x, 0, to.z);
            if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(dir) > 0.7) {
                hurt(level, mob, e, damage(mob, a), b, effect(a));
                if (b == BoltKind.WATER) push(e, dir, 1.0, 0.2);
            }
        }
        for (int i = 1; i <= 5; i++) {
            Vec3 p = mob.position().add(dir.scale(a.radius() * i / 5.0)).add(0, 1.0, 0);
            level.sendParticles(b.trail, p.x, p.y, p.z, 4, i * 0.15, 0.3, i * 0.15, 0.02);
        }
        mob.playLevelSound(SoundEvents.ENDER_DRAGON_SHOOT, 0.7F, 1.4F);
    }

    static void root(ServerLevel level, AwakenMob mob, Ability a, Vec3 at) {
        for (LivingEntity e : enemiesNear(level, mob, at, a.radius())) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 5), mob);
            hurt(level, mob, e, damage(mob, a) * 0.5F, bolt(mob, a), effect(a));
        }
        level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y + 0.5, at.z, 20, a.radius() * 0.4, 0.4, a.radius() * 0.4, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CREAKING_FREEZE, mob.getSoundSource(), 1.0F, 1.0F);
    }

    static void lift(ServerLevel level, AwakenMob mob, Ability a, Vec3 at) {
        for (LivingEntity e : enemiesNear(level, mob, at, a.radius())) {
            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 2), mob);
            hurt(level, mob, e, damage(mob, a) * 0.3F, bolt(mob, a), null);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 30, a.radius() * 0.4, 0.8, a.radius() * 0.4, 0.2);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHULKER_SHOOT, mob.getSoundSource(), 1.0F, 0.7F);
    }

    static void darkness(ServerLevel level, AwakenMob mob, Ability a) {
        for (LivingEntity e : enemiesNear(level, mob, mob.position(), a.radius())) {
            if (a.effect() != null) e.addEffect(new MobEffectInstance(a.effect(), a.effectTicks(), a.effectAmp()), mob);
            else e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0), mob);
            if (a.heavy()) e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), mob);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 1.5, 1.0, 1.5, 0.05);
        mob.playLevelSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8F, 0.6F);
    }
}
