package dev.awaken.veil;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.signal.Signal;
import dev.awaken.veil.entity.VeilSpirit;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wearing a creature. C-rank: animals. A-rank: any mob that is not a boss. The camera moves into the creature, the
 * client sends movement intent every tick, and the creature's own tick is replaced by ours while it is worn (so its AI
 * cannot fight the spirit). Shift lets go (vanilla's "stop spectating"). Possessing a creeper and pressing attack lights
 * it, which is allowed and funny.
 */
public final class Possession {
    private Possession() {}

    /** Worn creature UUID -> wearer UUID. */
    static final Map<UUID, UUID> WORN = new HashMap<>();
    static final int RANGE = 8;

    public static boolean eligible(Mob m, int possessLevel) {
        if (possessLevel <= 0 || !m.isAlive() || m instanceof VeilSpirit || CordEaters.isEater(m)) return false;
        if (m.typeHolder().is(VeilContent.UNPOSSESSABLE) || m instanceof Npc) return false;
        MobRole role = m.getExistingData(CoreApi.MOB_ROLE).orElse(MobRole.NONE);
        if (role.role() == MobRole.Role.BOSS || role.role() == MobRole.Role.WARDEN || role.role() == MobRole.Role.ECHO) return false;
        if (m.isVehicle() || m.isPassenger() || WORN.containsKey(m.getUUID())) return false;
        boolean creature = !(m instanceof Enemy);
        return possessLevel >= 2 || creature;
    }

    /** Someone else's tamed pet or bonded mount: only its owner's spirit may wear it (SPEC "Together"). */
    public static boolean ownedByAnother(Mob m, ServerPlayer sp) {
        return (m instanceof TamableAnimal t && t.isTame() && !t.isOwnedBy(sp))
                || dev.awaken.core.api.MobHooks.bondedTo(m).map(o -> !o.equals(sp.getUUID())).orElse(false);
    }

    static void request(ServerPlayer sp, Projection.Spirit s, int entityId) {
        if (s.possessed != null) {
            release(sp, s, null);
            return;
        }
        Entity e = sp.level().getEntity(entityId);
        if (!(e instanceof Mob m) || m.distanceTo(sp) > RANGE) return;
        int level = Projection.possessLevel(sp);
        if (level == 0) {
            Signal.toast(sp, Component.literal("The living won't let you in yet. (C-rank)"));
            dev.awaken.core.api.Promises.seen(sp, "possession", null, dev.awaken.core.api.Promises.Need.rank("C"), "Wearing a creature, in the Veil");
            return;
        }
        if (ownedByAnother(m, sp)) {
            Signal.toast(sp, Component.literal("That one belongs to someone. It won't let you in."));
            return;
        }
        if (!eligible(m, level)) {
            MobRole role = m.getExistingData(CoreApi.MOB_ROLE).orElse(MobRole.NONE);
            boolean boss = role.role() == MobRole.Role.BOSS || role.role() == MobRole.Role.WARDEN || m.typeHolder().is(VeilContent.UNPOSSESSABLE);
            Signal.toast(sp, Component.literal(boss ? "Too much will in that one. It throws you out." :
                    m instanceof Enemy && level < 2 ? "Hostile minds won't let you in yet. (A-rank)" : "That one won't let you in."));
            return;
        }
        s.possessed = m;
        s.possessedHadNoGravity = m.isNoGravity();
        s.ignited = false;
        s.input = new VeilPayloads.PossessInput(0, 0, false, false, m.getYRot(), m.getXRot());
        WORN.put(m.getUUID(), sp.getUUID());
        m.getNavigation().stop();
        m.setTarget(null);
        if (flies(m)) m.setNoGravity(true);
        sp.setCamera(m);
        s.dirty = true;
        sp.level().playSound(null, m.getX(), m.getY(), m.getZ(), VeilContent.RISE.get(), SoundSource.NEUTRAL, 0.6F, 1.5F);
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        if (mem.tipsShown < 10) {
            mem.tipsShown = Math.max(mem.tipsShown, 3) + 1;
            Signal.toast(sp, Component.literal("[Shift] let go · [Attack] act"));
        }
    }

    static boolean flies(Mob m) {
        return m.getNavigation() instanceof FlyingPathNavigation || m.isNoGravity();
    }

    private static boolean swims(Mob m) {
        return m.getNavigation() instanceof WaterBoundPathNavigation && m.isInWater();
    }

    /** Lets go. The spirit stays where the creature was. */
    static void release(ServerPlayer sp, Projection.Spirit s, @Nullable String why) {
        Mob m = s.possessed;
        if (m == null) return;
        s.possessed = null;
        WORN.remove(m.getUUID());
        if (m.isAlive()) {
            m.setNoGravity(s.possessedHadNoGravity);
            m.setDeltaMovement(Vec3.ZERO);
            m.getNavigation().stop();
        }
        if (sp.getCamera() == m) sp.setCamera(sp);
        s.lastGood = sp.position();
        s.dirty = true;
        if (why != null) Signal.toast(sp, Component.literal(why));
    }

    /** Called from the spirit's tick: is the creature still worn? */
    static void check(ServerPlayer sp, Projection.Spirit s) {
        Mob m = s.possessed;
        if (m == null) return;
        if (m.isRemoved() || !m.isAlive() || m.level() != sp.level()) {
            release(sp, s, s.ignited ? "Worth it." : "It's gone. You're loose again.");
            return;
        }
        if (sp.getCamera() != m) {
            release(sp, s, null);
            return;
        }
        if (s.actCooldown > 0) s.actCooldown--;
        s.lastGood = m.position();
    }

    static void input(ServerPlayer sp, VeilPayloads.PossessInput in) {
        Projection.Spirit s = Projection.live(sp);
        if (s == null || s.possessed == null) return;
        s.input = new VeilPayloads.PossessInput(Mth.clamp(in.forward(), -1, 1), Mth.clamp(in.strafe(), -1, 1), in.jump(), in.sprint(),
                Mth.wrapDegrees(in.yaw()), Mth.clamp(in.pitch(), -90, 90));
    }

    /** Attack while wearing something: it bites, kicks, or (creepers) lights up. */
    static void act(ServerPlayer sp, Projection.Spirit s, int targetId) {
        Mob m = s.possessed;
        if (m == null || s.actCooldown > 0) return;
        s.actCooldown = 12;
        if (m instanceof Creeper creeper) {
            if (!s.ignited) {
                creeper.ignite();
                s.ignited = true;
            }
            return;
        }
        m.swingForAttack(InteractionHand.MAIN_HAND);
        Entity t = targetId >= 0 ? m.level().getEntity(targetId) : null;
        if (t instanceof LivingEntity living && !(t instanceof Player) && t != m && t != s.body && living.isAlive()
                && m.distanceTo(t) < 3.0 + m.getBbWidth() && m.level() instanceof ServerLevel sl
                && m.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            m.doHurtTarget(sl, t);
        }
    }

    /**
     * Replaces a worn creature's tick. Returns true when the vanilla tick should be skipped. Death and a lit creeper are
     * left to vanilla.
     */
    static boolean drive(Mob m) {
        UUID owner = WORN.get(m.getUUID());
        if (owner == null || !(m.level() instanceof ServerLevel level)) return false;
        ServerPlayer sp = level.getServer().getPlayerList().getPlayer(owner);
        Projection.Spirit s = sp == null ? null : Projection.live(sp);
        if (s == null || s.possessed != m) {
            WORN.remove(m.getUUID());
            return false;
        }
        if (m.isDeadOrDying() || s.ignited) return false;
        m.commonTick();
        if (m.hurtTime > 0) m.hurtTime--;
        if (m.damageCooldownTime > 0) m.damageCooldownTime--;

        VeilPayloads.PossessInput in = s.input;
        float yaw = in.yaw(), pitch = in.pitch();
        m.setYRot(yaw);
        m.setXRot(pitch);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);

        double speed = m.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double flySpeed = m.getAttribute(Attributes.FLYING_SPEED) != null ? m.getAttributeValue(Attributes.FLYING_SPEED) : 0;
        double v = Mth.clamp(Math.max(speed, flySpeed) * 0.95 * (in.sprint() ? 1.35 : 1.0), 0.08, 0.5);
        float fwd = in.forward(), str = in.strafe();
        double len = Math.sqrt(fwd * fwd + str * str);
        if (len > 1) {
            fwd /= (float) len;
            str /= (float) len;
        }
        float yr = yaw * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yr), cos = Mth.cos(yr);
        Vec3 motion = m.getDeltaMovement();
        double hx, hz, vy;
        if (flies(m) || swims(m)) {
            Vec3 look = Vec3.directionFromRotation(pitch, yaw);
            hx = (look.x * fwd + cos * str) * v;
            hz = (look.z * fwd + sin * str) * v;
            vy = look.y * fwd * v + (in.jump() ? v * 0.8 : 0);
            if (swims(m) && !flies(m) && fwd == 0 && !in.jump()) vy = -0.01;
        } else {
            hx = (-sin * fwd + cos * str) * v;
            hz = (cos * fwd + sin * str) * v;
            vy = motion.y;
            if (m.onGround()) {
                vy = in.jump() || motion.y > 0.05 ? Math.max(motion.y, m.getAttributeValue(Attributes.JUMP_STRENGTH)) : 0;
            } else if (m.isInWater()) {
                vy = in.jump() ? 0.06 : Math.max(vy - 0.02, -0.1);
            } else {
                vy = (vy - m.getGravity()) * 0.98;
            }
        }
        m.move(MoverType.SELF, new Vec3(hx, vy, hz));
        double nextY = m.onGround() && vy < 0 ? 0 : vy;
        if (m.horizontalCollision && m.onGround() && (fwd != 0 || str != 0) && !flies(m)) nextY = m.getAttributeValue(Attributes.JUMP_STRENGTH);
        m.setDeltaMovement(hx, nextY, hz);
        return true;
    }
}
