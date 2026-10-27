package dev.awaken.living.combat;

import dev.awaken.core.api.Strikes;
import dev.awaken.living.combat.Forms.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;

/**
 * The moves (SPEC §21.11, decisions 19-20): one new telegraphed move per rung, from about a dozen shapes. Every move winds up first
 * (a ring or a line of the rank's colour on the ground where it will land, and a sound), then happens once, so a death is never a surprise.
 * Damage is the creature's own attack times {@code power} (at most 1.3 of a normal blow) and is delivered with
 * {@link Strikes#deliver} naming its class, so Arms' guard, deflect and dodge answer it. {@link MarkGoal} runs them.
 */
public final class Moves {
    private Moves() {}

    public enum Kind { LUNGE, CHARGE, STOMP, BASH, RALLY, ROAR, VOLLEY, SNIPE, BLINK, SPIT, WEB, SUMMON, RISE, PULL, RETREAT }

    /** What a roar does besides pushing. */
    public enum Fx { SLOW, BLIND, POISON }

    /** A move: its shape, how long it winds up and rests, the range it is used at, its power (times the creature's blow), its reach, a count. */
    public record Def(Kind kind, String name, int windup, int cooldown, double min, double max, float power, double radius, int count, Strikes.Kind strike, Fx fx) {}

    /** The rank colours of the glow (D gold, C orange, B red, A violet, S indigo), for the tells too. */
    public static final int[] COLOUR = {0xFFFFFF, 0xE4D063, 0xF29A48, 0xEC5656, 0xB07CFF, 0x8C7BFF};

    /** Tell lengths: 1.2 s in D down to 0.7 s in S (decision 19). */
    public static int windup(int rung) {
        return Math.round(24 - 2.5F * (Math.max(1, Math.min(5, rung)) - 1));
    }

    private static Def d(Kind k, String name, int rung, int cooldown, double min, double max, float power, double radius, int count, Strikes.Kind strike, Fx fx) {
        return new Def(k, name, windup(rung), cooldown, min, max, power, radius, count, strike, fx);
    }

    /** The move a species has at a rung (none: Retreat and Rise are not goals' work, see {@link #arm}), or null. */
    static Def moveOf(Species sp, int rung) {
        Strikes.Kind G = Strikes.Kind.GRAB, P = Strikes.Kind.PERILOUS, H = Strikes.Kind.HEAVY, L = Strikes.Kind.LIGHT, N = Strikes.Kind.NONE;
        switch (sp.layout) {
            case "pig": return switch (rung) {
                case 1 -> d(Kind.LUNGE, "Gore", 1, 90, 1.5, 6, 1.0F, 0, 0, G, null);
                case 2 -> d(Kind.STOMP, "Root-up", 2, 140, 0, 4, 1.0F, 4, 0, P, null);
                case 3 -> d(Kind.CHARGE, "Trample", 3, 160, 4, 14, 1.2F, 0, 0, G, null);
                case 4 -> d(Kind.STOMP, "Quake", 4, 140, 0, 6, 1.2F, 6, 0, P, null);
                default -> d(Kind.ROAR, "Roar", 5, 200, 0, 8, 0, 8, 0, N, Fx.SLOW);
            };
            case "cow": return switch (rung) {
                case 1 -> d(Kind.BASH, sp == Species.MOOSHROOM ? "Spore puff" : "Back-kick", 1, 80, 0, 3, 1.0F, 0, 0, H, null);
                case 2 -> d(Kind.CHARGE, "Charge", 2, 140, 4, 12, 1.1F, 0, 0, G, null);
                case 3 -> d(Kind.STOMP, "Stomp", 3, 140, 0, 5, 1.1F, 5, 0, P, null);
                case 4 -> d(Kind.CHARGE, "Storm charge", 4, 130, 4, 14, 1.2F, 0, 0, G, null);
                default -> d(Kind.ROAR, "Bellow", 5, 200, 0, 8, 0, 8, 0, N, Fx.SLOW);
            };
            case "sheep": return switch (rung) {
                case 1 -> d(Kind.BASH, "Headbutt", 1, 80, 0, 3, 1.0F, 0, 0, H, null);
                case 2, 4 -> d(Kind.CHARGE, "Charge", rung, 140, 4, 12, 1.1F, 0, 0, G, null);
                case 3 -> d(Kind.STOMP, "Spark", 3, 140, 0, 4, 1.0F, 4, 0, P, null);
                default -> d(Kind.ROAR, "Bleat", 5, 200, 0, 7, 0, 7, 0, N, Fx.SLOW);
            };
            case "chicken": return switch (rung) {
                case 1, 4 -> d(Kind.LUNGE, "Flap-jump", rung, 90, 1.5, 5, 0.9F, 0, 0, G, null);
                case 2 -> d(Kind.RALLY, "Crow", 2, 220, 2, 20, 0, 0, 0, N, null);
                case 3 -> d(Kind.STOMP, "Burst", 3, 140, 0, 3, 0.9F, 3, 0, P, null);
                default -> d(Kind.ROAR, "Screech", 5, 200, 0, 7, 0, 7, 0, N, Fx.BLIND);
            };
            case "zombie": return switch (rung) {
                case 1 -> d(Kind.RALLY, "Groan", 1, 220, 3, 20, 0, 0, 0, N, null);
                case 2 -> d(Kind.BASH, "Shield bash", 2, 100, 0, 3, 1.1F, 0, 0, H, null);
                case 3 -> d(Kind.LUNGE, "Pounce", 3, 120, 3, 7, 1.2F, 0, 0, G, null);
                case 4 -> null; // Rise is a death, not a goal: MarkEvents
                default -> { Def s = d(Kind.SUMMON, "Dread call", 5, 300, 0, 12, 0, 3, 4, N, null); yield new Def(s.kind(), s.name(), s.windup() + 12, s.cooldown(), s.min(), s.max(), s.power(), s.radius(), s.count(), s.strike(), s.fx()); }
            };
            case "skeleton": return switch (rung) {
                case 1 -> null; // Retreat: an avoid goal
                case 2 -> d(Kind.SNIPE, "Marksman", 2, 100, 6, 24, 1.3F, 0, 1, L, null);
                case 3, 5 -> d(Kind.VOLLEY, "Volley", rung, 120, 6, 22, 1.0F, 0, rung == 5 ? 5 : 3, L, null);
                default -> d(Kind.BLINK, "Wraith step", 4, 100, 0, 6, 0, 8, 0, N, null);
            };
            case "spider": return switch (rung) {
                case 1 -> d(Kind.WEB, "Web", 1, 160, 2, 10, 0, 0, 0, N, null);
                case 2 -> d(Kind.LUNGE, "Drop", 2, 100, 3, 9, 1.2F, 0, 0, G, null);
                case 3 -> d(Kind.SPIT, "Spit", 3, 110, 4, 14, 0.8F, 0, 0, L, Fx.POISON);
                case 4 -> d(Kind.SUMMON, "Brood", 4, 300, 0, 10, 0, 2.5, 3, N, null);
                default -> d(Kind.PULL, "Pull", 5, 160, 4, 14, 0, 0, 0, N, null);
            };
            default: return null; // the creeper's explosion is vanilla's; its forms are bodies only for now
        }
    }

    /** The moves a creature has: its rung's, and a crowned one the rung below's too (decision 19). */
    public static List<Def> of(Species sp, int rung, boolean crowned) {
        java.util.ArrayList<Def> out = new java.util.ArrayList<>(2);
        Def a = moveOf(sp, rung);
        if (a != null) out.add(a);
        if (crowned && rung >= 3) {
            Def b = moveOf(sp, rung - 1);
            if (b != null) out.add(b);
        }
        return out;
    }

    /** Puts a formed creature's moves on it (goals are not saved, so this runs at every join). */
    public static void arm(Mob mob) {
        Forms.Form f = Forms.of(mob);
        Species sp = Species.of(mob.getType());
        if (!f.worn() || sp == null || mob.isBaby()) return;
        for (var w : mob.goalSelector.getAvailableGoals()) if (w.getGoal() instanceof MarkGoal) return;
        List<Def> moves = of(sp, f.rung(), f.crowned());
        if (!moves.isEmpty()) mob.goalSelector.addGoal(-1, new MarkGoal(mob, moves, f.rung()));
        if (sp.layout.equals("skeleton") && f.rung() == 1 && mob instanceof net.minecraft.world.entity.PathfinderMob pm)
            mob.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(pm, Player.class, 6.0F, 1.0, 1.3));
    }

    // ---- the tell ------------------------------------------------------------------------------------------------

    /** The wind-up's first sound. */
    static void tellSound(ServerLevel level, Mob mob, Def d) {
        SoundEvent s = switch (d.kind()) {
            case ROAR, RALLY, SUMMON -> SoundEvents.RAVAGER_ROAR;
            case VOLLEY, SNIPE, SPIT -> SoundEvents.CROSSBOW_LOADING_END.value();
            case BLINK -> SoundEvents.ENDERMAN_TELEPORT;
            default -> SoundEvents.RAVAGER_ATTACK;
        };
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), s, SoundSource.HOSTILE, 1.0F, d.kind() == Kind.ROAR || d.kind() == Kind.RALLY ? 1.5F : 1.25F);
    }

    /** Dust in the rank's colour: a ring on the ground where a stomp will land, a line where a lunge or a shot will go, a glint on the head. */
    static void tellFx(ServerLevel level, Mob mob, Def d, int rung, Vec3 aim, float progress) {
        DustParticleOptions dust = new DustParticleOptions(COLOUR[Math.max(0, Math.min(5, rung))], 1.4F);
        switch (d.kind()) {
            case STOMP, ROAR, SUMMON, BASH, RALLY, BLINK -> {
                double r = d.radius() > 0 ? d.radius() : 2.5;
                int pts = (int) Math.max(10, r * 7);
                double now = r * (0.35 + 0.65 * progress);
                for (int i = 0; i < pts; i++) {
                    double a = i * Math.PI * 2 / pts;
                    level.sendParticles(dust, mob.getX() + Math.cos(a) * now, mob.getY() + 0.1, mob.getZ() + Math.sin(a) * now, 1, 0, 0, 0, 0);
                }
            }
            case LUNGE, CHARGE, PULL, SPIT -> {
                Vec3 dir = aim.subtract(mob.position()).multiply(1, 0, 1);
                double len = Math.min(d.kind() == Kind.CHARGE ? 12 : 7, dir.length());
                if (len > 0.1) {
                    dir = dir.normalize();
                    for (double t = 0.8; t < len; t += 0.7) level.sendParticles(dust, mob.getX() + dir.x * t, mob.getY() + 0.1, mob.getZ() + dir.z * t, 1, 0.05, 0, 0.05, 0);
                }
            }
            default -> level.sendParticles(dust, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 4, 0.25, 0.2, 0.25, 0);
        }
        if (d.kind() != Kind.ROAR) level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
    }

    // ---- the move itself ---------------------------------------------------------------------------------------

    static float damage(Mob mob, Def d) {
        return (float) (mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * d.power());
    }

    /** Hits one creature as this class of blow, and shoves it along (x, z) by {@code push}, up by {@code lift}. */
    public static boolean hit(ServerLevel level, Mob mob, LivingEntity target, Def d, double pushX, double pushZ, double push, double lift) {
        boolean landed = Strikes.deliver(mob, d.strike(), () -> target.hurtServer(level, mob.damageSources().mobAttack(mob), damage(mob, d)));
        if (landed) shove(target, pushX, pushZ, push, lift);
        return landed;
    }

    static void shove(LivingEntity target, double x, double z, double push, double lift) {
        double len = Math.max(1e-4, Math.hypot(x, z));
        target.setDeltaMovement(target.getDeltaMovement().add(x / len * push, lift, z / len * push));
        sync(target);
    }

    /** Tells the client its own body was pushed (a player's movement is theirs until the server says so). */
    static void sync(LivingEntity e) {
        if (e instanceof net.minecraft.server.level.ServerPlayer sp && sp.connection != null)
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp.getId(), sp.getDeltaMovement()));
        else e.needsSync = true;
    }

    /** Players the creature means: its target and every player near the place it strikes. */
    static List<Player> players(ServerLevel level, Vec3 at, double radius) {
        return level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(radius, 2.5, radius), p -> p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(at) <= radius * radius);
    }

    /** The instant moves (a stomp, a bash, a call, a shot...): what happens when the wind-up ends. The charges and lunges begin here and the goal carries them on. */
    static void strike(ServerLevel level, Mob mob, LivingEntity target, Def d, Vec3 aim, int rung) {
        Vec3 dir = aim.subtract(mob.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? mob.getLookAngle().multiply(1, 0, 1) : dir.normalize();
        switch (d.kind()) {
            case LUNGE -> {
                mob.setDeltaMovement(dir.x * 1.05, 0.40, dir.z * 1.05);
                sync(mob);
            }
            case CHARGE -> mob.setDeltaMovement(dir.x * 0.95, mob.getDeltaMovement().y, dir.z * 0.95);
            case STOMP -> {
                for (Player p : players(level, mob.position(), d.radius())) {
                    Vec3 away = p.position().subtract(mob.position());
                    hit(level, mob, p, d, away.x, away.z, 0.9, 0.45);
                }
                ring(level, mob, d, rung);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.5F);
            }
            case BASH -> {
                if (mob.distanceToSqr(target) <= 3.4 * 3.4) hit(level, mob, target, d, target.getX() - mob.getX(), target.getZ() - mob.getZ(), 0.9, 0.35);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
            }
            case RALLY -> {
                for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(24), m -> m != mob && m.isAlive() && m.getType() == mob.getType() && m.getTarget() == null)) m.setTarget(target);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.7F);
            }
            case ROAR -> {
                for (Player p : players(level, mob.position(), d.radius())) {
                    Vec3 away = p.position().subtract(mob.position());
                    shove(p, away.x, away.z, 0.7, 0.2);
                    if (d.fx() == Fx.BLIND) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                    else p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                }
                ring(level, mob, d, rung);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 0.9F);
            }
            case VOLLEY, SNIPE -> shoot(level, mob, target, d);
            case BLINK -> {
                for (int i = 0; i < 4; i++) {
                    double a = mob.getRandom().nextDouble() * Math.PI * 2, r = 6 + mob.getRandom().nextDouble() * 3;
                    if (mob.randomTeleport(target.getX() + Math.cos(a) * r, mob.getY(), target.getZ() + Math.sin(a) * r, true, state -> state.isSolid())) break;
                }
            }
            case SPIT -> {
                Vec3 from = mob.getEyePosition(), to = target.getEyePosition();
                LivingEntity hit = rayTarget(level, mob, from, to.subtract(from).normalize(), 16);
                if (hit != null) {
                    hit(level, mob, hit, d, 0, 0, 0, 0);
                    hit.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                }
                line(level, from, hit != null ? hit.getEyePosition() : from.add(to.subtract(from).normalize().scale(16)), new DustParticleOptions(0x6BE04A, 1.2F));
            }
            case WEB -> {
                BlockPos at = target.blockPosition();
                if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolid()) {
                    level.setBlockAndUpdate(at, Blocks.COBWEB.defaultBlockState());
                    MarkEvents.expire(level, at, level.getGameTime() + 160);
                }
            }
            case SUMMON -> summon(level, mob, d);
            case PULL -> {
                Vec3 to = mob.position().subtract(target.position());
                shove(target, to.x, to.z, 1.1, 0.25);
                line(level, mob.getEyePosition(), target.getEyePosition(), new DustParticleOptions(COLOUR[rung], 1.2F));
            }
            default -> {
            }
        }
    }

    /** The expanding ring a stomp or a roar leaves. */
    static void ring(ServerLevel level, Mob mob, Def d, int rung) {
        DustParticleOptions dust = new DustParticleOptions(COLOUR[Math.max(0, Math.min(5, rung))], 1.8F);
        for (double r = 0.8; r <= d.radius(); r += 0.8) {
            int pts = (int) Math.max(8, r * 6);
            for (int i = 0; i < pts; i++) {
                double a = i * Math.PI * 2 / pts;
                level.sendParticles(dust, mob.getX() + Math.cos(a) * r, mob.getY() + 0.15, mob.getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
            }
        }
    }

    static void line(ServerLevel level, Vec3 from, Vec3 to, DustParticleOptions dust) {
        Vec3 v = to.subtract(from);
        double len = v.length();
        if (len < 0.1) return;
        v = v.scale(1 / len);
        for (double t = 0.5; t < len; t += 0.6) level.sendParticles(dust, from.x + v.x * t, from.y + v.y * t, from.z + v.z * t, 1, 0, 0, 0, 0);
    }

    /** The first living thing (not its own kind) along a ray, within {@code range}. */
    static LivingEntity rayTarget(ServerLevel level, Mob mob, Vec3 from, Vec3 dir, double range) {
        AABB box = new AABB(from, from.add(dir.scale(range))).inflate(1);
        var hit = ProjectileUtil.getEntityHitResult(mob, from, from.add(dir.scale(range)), box, e -> e instanceof LivingEntity && e != mob && e.getType() != mob.getType(), range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    static void shoot(ServerLevel level, Mob mob, LivingEntity target, Def d) {
        ItemStack arrowItem = new ItemStack(Items.ARROW);
        int n = Math.max(1, d.count());
        float power = d.kind() == Kind.SNIPE ? 2.4F : 1.6F;
        for (int i = 0; i < n; i++) {
            AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, arrowItem, d.kind() == Kind.SNIPE ? 3.0F : 1.0F, null);
            double fan = n == 1 ? 0 : (i - (n - 1) / 2.0) * 0.16;
            double xd = target.getX() - mob.getX(), zd = target.getZ() - mob.getZ(), yd = target.getY(0.3333) - arrow.getY();
            double dist = Math.sqrt(xd * xd + zd * zd);
            double c = Math.cos(fan), s = Math.sin(fan);
            Projectile.spawnProjectileUsingShoot(arrow, level, arrowItem, xd * c - zd * s, yd + dist * 0.2, xd * s + zd * c, power, d.kind() == Kind.SNIPE ? 0.5F : 3.0F);
        }
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0F, d.kind() == Kind.SNIPE ? 0.7F : 1.0F);
    }

    static void summon(ServerLevel level, Mob mob, Def d) {
        EntityType<? extends Mob> type = mob.getType() == EntityTypes.SPIDER || mob.getType() == EntityTypes.CAVE_SPIDER ? EntityTypes.CAVE_SPIDER
                : (EntityType<? extends Mob>) mob.getType();
        // never a crowd: no more than eight of that kind within 24 blocks
        if (level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(24), m -> m.getType() == type).size() > 8) return;
        for (int i = 0; i < d.count(); i++) {
            double a = i * Math.PI * 2 / d.count();
            Mob child = type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (child == null) continue;
            child.snapTo(mob.getX() + Math.cos(a) * d.radius(), mob.getY(), mob.getZ() + Math.sin(a) * d.radius(), mob.getYRot(), 0);
            if (mob.getTarget() != null) child.setTarget(mob.getTarget());
            level.addFreshEntity(child);
            level.sendParticles(ParticleTypes.POOF, child.getX(), child.getY() + 0.3, child.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /** Cobwebs a spider laid, to take away again (a ring the server walks every second). */
    record Web(ServerLevel level, BlockPos pos, long until) {}

    static final ArrayDeque<Web> WEBS = new ArrayDeque<>();
}
