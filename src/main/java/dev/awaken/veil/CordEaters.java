package dev.awaken.veil;

import dev.awaken.core.Forces;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cord-Eaters hunt spirits that stay out too long anywhere but the Stillpoint. They drift through walls straight at the
 * spirit; if one reaches it, the cord is cut: the player slams back into their body with Spirit Shock. The creature comes
 * from the mobs module's "awaken:veil" theme (vanilla fallback when it is absent); its tick is replaced by the hunt.
 */
public final class CordEaters {
    private CordEaters() {}

    public static final String THEME = "awaken:veil";
    private static final String TAG = "awaken_cord_eater";
    static final int FIRST_AFTER = 20 * 25;
    static final int EVERY = 20 * 15;
    static final int MAX = 3;
    static final double REACH = 1.1;

    /** Eater UUID -> hunted player UUID. */
    static final Map<UUID, UUID> EATERS = new HashMap<>();

    public static boolean isEater(Entity e) {
        return EATERS.containsKey(e.getUUID());
    }

    /** A Cord-Eater saved to disk without its hunt (a crash): it has no business existing. */
    static boolean isStrayEater(Entity e) {
        return e.getPersistentData().getBooleanOr(TAG, false) && !EATERS.containsKey(e.getUUID());
    }

    static void tick(ServerPlayer sp, Projection.Spirit s) {
        s.eaters.removeIf(e -> {
            if (e.isAlive() && !e.isRemoved()) return false;
            EATERS.remove(e.getUUID());
            return true;
        });
        if (Veil.inStillpoint(sp) || s.pocket != null) {
            s.outsideTicks = 0;
            return;
        }
        s.outsideTicks++;
        if (s.nextEater < 0) s.nextEater = FIRST_AFTER + sp.getRandom().nextInt(200);
        if (s.outsideTicks >= s.nextEater && s.eaters.size() < MAX) {
            spawn(sp, s, sp.level());
            s.nextEater = s.outsideTicks + EVERY + sp.getRandom().nextInt(200);
        }
        Vec3 target = sp.position().add(0, 0.9, 0);
        for (LivingEntity e : s.eaters) {
            if (e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(target) < REACH) {
                Projection.end(sp, Projection.EndReason.CORD_CUT);
                return;
            }
        }
    }

    private static void spawn(ServerPlayer sp, Projection.Spirit s, ServerLevel level) {
        double a = sp.getRandom().nextDouble() * Math.PI * 2;
        double d = 14 + sp.getRandom().nextDouble() * 6;
        Vec3 p = sp.position().add(Math.cos(a) * d, sp.getRandom().nextInt(7) - 3, Math.sin(a) * d);
        BlockPos pos = BlockPos.containing(p);
        if (!level.isLoaded(pos)) return;
        int lv = Progression.contentLevel(Progression.rank(sp));
        LivingEntity e;
        try {
            e = MobHooks.spawnThemed(level, THEME, MobRole.Role.NORMAL, pos, lv, Set.of(Forces.Tag.HAUNTED), "");
        } catch (RuntimeException ex) {
            dev.awaken.Awaken.LOG.warn("Cord-Eater spawn failed", ex);
            return;
        }
        if (e == null) return;
        EATERS.put(e.getUUID(), sp.getUUID());
        s.eaters.add(e);
        e.getPersistentData().putBoolean(TAG, true);
        e.setNoGravity(true);
        e.noPhysics = true;
        e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        if (e instanceof Mob m) m.setTarget(null);
        if ("minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) {
            e.setCustomName(Component.literal("Cord-Eater").withStyle(ChatFormatting.DARK_RED));
        }
        level.playSound(null, e.getX(), e.getY(), e.getZ(), VeilContent.WHISPER.get(), SoundSource.HOSTILE, 1.0F, 0.5F);
        if (s.eaters.size() == 1) Signal.toast(sp, Component.literal("Something is following the cord.").withStyle(ChatFormatting.RED));
    }

    /** Replaces an eater's tick with the hunt. Returns true when the vanilla tick should be skipped. */
    static boolean drive(LivingEntity e) {
        UUID owner = EATERS.get(e.getUUID());
        if (owner == null || !(e.level() instanceof ServerLevel level)) return false;
        if (e.isDeadOrDying()) return false;
        ServerPlayer sp = level.getServer().getPlayerList().getPlayer(owner);
        Projection.Spirit s = sp == null ? null : Projection.live(sp);
        if (s == null || sp.level() != level) {
            EATERS.remove(e.getUUID());
            e.discard();
            return true;
        }
        e.commonTick();
        if (e.hurtTime > 0) e.hurtTime--;
        if (e.damageCooldownTime > 0) e.damageCooldownTime--;
        Vec3 target = sp.position().add(0, 0.9, 0);
        Vec3 from = e.position().add(0, e.getBbHeight() * 0.5, 0);
        Vec3 to = target.subtract(from);
        double dist = to.length();
        double speed = Math.min(0.42, 0.2 + e.tickCount * 0.0006);
        Vec3 step = dist < 1e-4 ? Vec3.ZERO : to.scale(Math.min(speed, dist) / dist);
        e.move(MoverType.SELF, step);
        e.setDeltaMovement(step);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        e.setYRot(yaw);
        e.setXRot(pitch);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        if (e.tickCount % 4 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.2, 0.3, 0.2, 0.01);
        return true;
    }

    static void clear(Projection.Spirit s) {
        for (LivingEntity e : s.eaters) {
            EATERS.remove(e.getUUID());
            if (!e.isRemoved()) {
                if (e.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                }
                e.discard();
            }
        }
        s.eaters.clear();
    }
}
