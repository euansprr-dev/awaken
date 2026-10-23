package dev.awaken.powers;

import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.content.PowerSummon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import dev.awaken.core.api.MobHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Targeting, damage and impulses shared by every power. Server only. */
public final class Combat {
    private Combat() {}

    // ---- who is who -------------------------------------------------------------------------------------------------

    /** On the player's side: the player, other players, their summons, decoys, dominated mobs, pets and Echoes. */
    public static boolean isAlly(ServerPlayer player, Entity e) {
        if (e == player || e instanceof Player) return true;
        if (e instanceof PowerSummon s) return true;
        if (Allies.isAlly(e)) return true;
        if (e instanceof OwnableEntity oe && oe.getOwner() instanceof Player) return true;
        if (e instanceof ArmorStand || e instanceof Mannequin) return true;
        Optional<MobRole> role = e.getExistingData(CoreApi.MOB_ROLE.get());
        return role.isPresent() && role.get().role() == MobRole.Role.ECHO;
    }

    /**
     * Who area powers damage: hostile creatures, anything fighting you or your side, angry neutrals, and anything you've
     * struck in the last few seconds. Never allies, pets, villagers or town folk. Peaceful overworld creatures (ambient
     * and grazing fauna) count only once provoked. Everything else in a wave is a bystander: pushed, never hurt.
     */
    public static boolean isFoe(ServerPlayer player, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isRemoved()) return false;
        if (isAlly(player, e) || e instanceof Npc) return false;
        if (e instanceof Mob m && (m.getTarget() == player || isAllyTargetedBy(player, m))) return true;
        if (le.getLastHurtByPlayer() == player) return true; // the thing you're already fighting
        if (e instanceof NeutralMob n && player.level() instanceof ServerLevel sl && n.isAngryAt(player, sl)) return true;
        return e instanceof Enemy && !MobHooks.peaceful(e);
    }

    /** Something a power's wave moves but never hurts: not on your side, not a foe, not a villager. */
    public static boolean isBystander(ServerPlayer player, Entity e) {
        return e instanceof LivingEntity le && le.isAlive() && !isAlly(player, e) && !(e instanceof Npc) && !isFoe(player, e);
    }

    private static boolean isAllyTargetedBy(ServerPlayer player, Mob m) {
        LivingEntity t = m.getTarget();
        return t != null && t != m && isAlly(player, t) && !(t instanceof Player);
    }

    /** A valid target for an aimed power: any living thing that isn't on the player's side. */
    public static boolean isFoeOrTarget(ServerPlayer player, Entity e) {
        return e instanceof LivingEntity le && le.isAlive() && !isAlly(player, e);
    }

    public static boolean isBoss(Entity e) {
        Optional<MobRole> role = e.getExistingData(CoreApi.MOB_ROLE.get());
        return role.isPresent() && role.get().role() == MobRole.Role.BOSS;
    }

    /** Bosses and very large things can't be lifted, thrown or dominated. */
    public static boolean isHeavy(Entity e) {
        if (isBoss(e)) return true;
        Optional<MobRole> role = e.getExistingData(CoreApi.MOB_ROLE.get());
        if (role.isPresent() && role.get().role() == MobRole.Role.WARDEN) return true;
        return e.getBbWidth() > 2.0f || (e instanceof LivingEntity le && le.getMaxHealth() > 300);
    }

    // ---- damage -----------------------------------------------------------------------------------------------------

    public static DamageSource source(ServerLevel level, Forces.@Nullable Force force, @Nullable Entity direct, @Nullable Entity owner) {
        var key = force == null ? Forces.KINETIC : Forces.key(force);
        return level.damageSources().source(key, direct == null ? owner : direct, owner);
    }

    /**
     * Power damage from a player (directly or through a summon/bolt). Resets i-frames when asked (multi-hit powers),
     * caps single hits on bosses, and returns whether damage landed.
     */
    public static boolean hurtFrom(ServerPlayer owner, @Nullable Entity direct, LivingEntity target, float amount, Forces.@Nullable Force force, boolean resetIframes) {
        if (amount <= 0 || !target.isAlive()) return false;
        if (force == Forces.Force.CHARGE && ForceInteractions.isOwnEcho(owner, target)) {
            ForceInteractions.overcharge(owner, target); // Charge meets Echoes
            return false;
        }
        if (isAlly(owner, target)) return false;
        if (!(target.level() instanceof ServerLevel level)) return false;
        if (isBoss(target)) amount = PowerMath.capBossHit(amount, target.getMaxHealth());
        if (resetIframes) target.damageCooldownTime = 0;
        PowerState st = PowerState.of(owner);
        st.markCombat(owner);
        boolean landed = target.hurtServer(level, source(level, force, direct, owner), amount);
        if (landed) Mastery.landed(owner, direct, target, force, Casting.now(owner));
        return landed;
    }

    public static boolean hurt(Cast c, LivingEntity target, float amount, Forces.@Nullable Force force) {
        return hurtFrom(c.player, null, target, amount, force, true);
    }

    /** Damage to an ally-agnostic target from the environment of a force reaction (no player credit needed). */
    public static void hurtRaw(ServerLevel level, LivingEntity target, float amount, Forces.@Nullable Force force, @Nullable Entity owner) {
        if (amount <= 0 || !target.isAlive()) return;
        target.damageCooldownTime = 0;
        target.hurtServer(level, source(level, force, null, owner), amount);
    }

    public static List<LivingEntity> foesAround(ServerPlayer player, Vec3 center, double radius) {
        return around(player, center, radius, true);
    }

    /** Bystanders inside a wave (see {@link #isBystander}): for powers that push everything they sweep through. */
    public static List<LivingEntity> bystandersAround(ServerPlayer player, Vec3 center, double radius) {
        return around(player, center, radius, false);
    }

    /**
     * Inside the radius means any part of the body is: big and tall creatures at the edge of a blast are caught, not
     * judged by the point at their middle.
     */
    private static List<LivingEntity> around(ServerPlayer player, Vec3 center, double radius, boolean foes) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = radius * radius;
        double reach = radius + 4; // the widest bodies poke this far past the radius
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, reach * 2, reach * 2, reach * 2),
                x -> foes ? isFoe(player, x) : isBystander(player, x))) {
            if (distToBoxSqr(e.getBoundingBox(), center) <= r2) out.add(e);
        }
        return out;
    }

    /** Squared distance from a point to the nearest point of a box (0 inside). */
    public static double distToBoxSqr(AABB b, Vec3 p) {
        double dx = Math.max(0, Math.max(b.minX - p.x, p.x - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - p.y, p.y - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - p.z, p.z - b.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Foes inside a cone (half-angle in degrees) in front of the player. */
    public static List<LivingEntity> foesInCone(ServerPlayer player, Vec3 origin, Vec3 dir, double range, double halfAngleDeg) {
        return cone(foesAround(player, origin, range), origin, dir, halfAngleDeg);
    }

    /** Bystanders inside a cone (pushed by a wave, never hurt). */
    public static List<LivingEntity> bystandersInCone(ServerPlayer player, Vec3 origin, Vec3 dir, double range, double halfAngleDeg) {
        return cone(bystandersAround(player, origin, range), origin, dir, halfAngleDeg);
    }

    private static List<LivingEntity> cone(List<LivingEntity> in, Vec3 origin, Vec3 dir, double halfAngleDeg) {
        List<LivingEntity> out = new ArrayList<>();
        double cos = Math.cos(Math.toRadians(halfAngleDeg));
        Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
        for (LivingEntity e : in) {
            Vec3 to = e.position().subtract(origin);
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            // a wide body counts if its near side is inside the cone, not just its middle
            double slack = Math.min(0.5, e.getBbWidth() / Math.max(1.0, toFlat.length()));
            if (toFlat.lengthSqr() < 0.5 || toFlat.normalize().dot(flat) >= cos - slack) out.add(e);
        }
        return out;
    }

    // ---- aiming -----------------------------------------------------------------------------------------------------

    public static BlockHitResult clip(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    /** Where the player's aim hits a block, or the point at max range. */
    public static Vec3 aimPoint(Cast c, double range) {
        Vec3 from = c.eye();
        Vec3 to = from.add(c.look().scale(range));
        BlockHitResult hit = clip(c.player, from, to);
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    public static @Nullable BlockHitResult aimBlock(Cast c, double range) {
        Vec3 from = c.eye();
        BlockHitResult hit = clip(c.player, from, from.add(c.look().scale(range)));
        return hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    /**
     * The living thing the player is aiming at, with a little aim assist (a fat ray). Stops at walls.
     */
    public static @Nullable LivingEntity aimEntity(Cast c, double range, double assist) {
        return aimEntity(c.player, c.eye(), c.look(), range, assist, true);
    }

    public static @Nullable LivingEntity aimEntity(ServerPlayer player, Vec3 from, Vec3 dir, double range, double assist, boolean foesFirst) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult wall = clip(player, from, to);
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        AABB box = new AABB(from, to).inflate(assist + 1.0);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, x -> x != player && isFoeOrTarget(player, x))) {
            AABB eb = e.getBoundingBox().inflate(assist);
            Optional<Vec3> hit = eb.contains(from) ? Optional.of(from) : eb.clip(from, to);
            if (hit.isEmpty()) continue;
            double d = from.distanceToSqr(hit.get());
            if (foesFirst && !isFoe(player, e)) d += 4; // prefer hostiles when two are lined up
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Every target along a line (piercing powers). */
    public static List<LivingEntity> alongLine(ServerPlayer player, Vec3 from, Vec3 to, double width) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(from, to).inflate(width + 1.0);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, x -> x != player && isFoeOrTarget(player, x))) {
            AABB eb = e.getBoundingBox().inflate(width);
            if (eb.contains(from) || eb.clip(from, to).isPresent()) out.add(e);
        }
        out.sort((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)));
        return out;
    }

    // ---- impulses ---------------------------------------------------------------------------------------------------

    /** Pushes a target away from a point (horizontal power) and up. Players get their velocity synced. */
    public static void knockFrom(LivingEntity target, Vec3 from, double horizontal, double up) {
        if (isHeavy(target)) {
            horizontal *= 0.2;
            up *= 0.2;
        }
        double resist = target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        // a power pushes harder than a sword: resistance takes at most 70% of it (heavies were cut above)
        double m = Math.max(0.3, 1 - resist);
        Vec3 d = target.position().subtract(from);
        Vec3 flat = new Vec3(d.x, 0, d.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 0) : flat.normalize();
        Vec3 v = target.getDeltaMovement().add(flat.scale(horizontal * m)).add(0, up * m, 0);
        push(target, v);
    }

    public static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.needsSync = true;
        if (target instanceof ServerPlayer) target.syncVelocity = true;
    }

    public static void stun(LivingEntity target, int ticks) {
        if (ticks <= 0) return;
        if (isBoss(target)) ticks = ticks / 3;
        if (ticks > 0) target.addEffect(new MobEffectInstance(PowerContent.STUNNED, ticks, 0, false, true, true));
    }

    public static boolean airborne(Entity e) {
        return !e.onGround() && !e.isInWater() && !e.isPassenger() && !(e instanceof Player p && p.getAbilities().flying);
    }
}
