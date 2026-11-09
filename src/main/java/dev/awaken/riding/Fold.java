package dev.awaken.riding;

import dev.awaken.core.DirectorLog;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Running;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A killing blow on a rider near their partner (SPEC §55): they fold into the partner's shadow for four seconds, unseen,
 * untouchable and unable to act, then come back beside the partner at half health. Health is set back the moment the
 * blow lands, so a crash during the fold can't lose anyone. Kept in memory: a fold is four seconds long.
 */
final class Fold {
    private Fold() {}

    static final int TICKS = 80;
    /** After coming back, this long before anything can hurt them again. */
    static final int GRACE = 40;
    static final float HEALTH_BACK = 0.5F;
    private static final double[] RADII = {1.6, 2.4, 3.2};
    private static final int ANGLES = 8;

    private static final Map<UUID, Integer> FOLDED = new HashMap<>();
    private static final Map<UUID, Integer> GRACED = new HashMap<>();

    static boolean folded(UUID id) {
        return FOLDED.containsKey(id);
    }

    /** Ticks of the fold still to run (0 if not folded). */
    static int left(UUID id) {
        return FOLDED.getOrDefault(id, 0);
    }

    /** Nothing can hurt them now: folded, or just back. */
    static boolean shielded(UUID id) {
        return FOLDED.containsKey(id) || GRACED.containsKey(id);
    }

    static void begin(ServerPlayer p) {
        p.setHealth(Math.max(1F, p.getMaxHealth() * HEALTH_BACK));
        p.clearFire();
        for (MobEffectInstance i : List.copyOf(p.getActiveEffects())) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(i.getEffect());
        }
        p.stopUsingItem();
        Casting.endAll(p, Running.Why.CANCELLED);
        p.setDeltaMovement(Vec3.ZERO);
        p.needsSync = true;
        p.resetFallDistance();
        GRACED.remove(p.getUUID());
        FOLDED.put(p.getUUID(), TICKS);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), RidingContent.FOLD.get(), SoundSource.PLAYERS, 0.9F, 1F);
        RidingEvents.shakeOff(p);
        DirectorLog.log(p, "riding", "caught by the thread at " + p.blockPosition().toShortString());
        RidingNet.sync(p.level().getServer());
    }

    static void tick(MinecraftServer s) {
        if (!GRACED.isEmpty()) {
            GRACED.replaceAll((id, t) -> t - 1);
            GRACED.values().removeIf(t -> t <= 0);
        }
        if (FOLDED.isEmpty()) return;
        for (UUID id : List.copyOf(FOLDED.keySet())) {
            int left = FOLDED.get(id) - 1;
            if (left > 0) FOLDED.put(id, left);
            else springBack(s, id);
        }
    }

    /** The four seconds are up: beside the partner if they're still here, otherwise right where they fell. */
    private static void springBack(MinecraftServer s, UUID id) {
        FOLDED.remove(id);
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p == null) return;
        ServerPlayer anchor = Bonds.partner(p);
        if (anchor != null && anchor.isAlive() && anchor.level() == p.level()) {
            Vec3 at = landing(anchor);
            boolean clean = at != null;
            p.teleport(new TeleportTransition(anchor.level(), clean ? at : anchor.position(), Vec3.ZERO, anchor.getYRot(), 0F, TeleportTransition.DO_NOTHING));
            if (!clean) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
            p.setDeltaMovement(0, 0.25, 0);
            p.needsSync = true;
        }
        p.resetFallDistance();
        GRACED.put(id, GRACE);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), RidingContent.SPRING.get(), SoundSource.PLAYERS, 0.9F, 1F);
        RidingNet.sync(s);
    }

    /** Somewhere to stand beside the partner, behind them first, in a clear line from them; null if there's nowhere. */
    private static @Nullable Vec3 landing(ServerPlayer anchor) {
        ServerLevel level = anchor.level();
        Vec3 eye = anchor.getEyePosition();
        float yaw = anchor.getYRot() * Mth.DEG_TO_RAD;
        for (double r : RADII) {
            for (int i = 0; i < ANGLES; i++) {
                // 0, +45, -45, +90...: behind them first, and round to the front last
                float turn = (i + 1) / 2 * (i % 2 == 0 ? -1 : 1) * Mth.TWO_PI / ANGLES;
                double x = anchor.getX() + Mth.sin(yaw + turn) * r, z = anchor.getZ() - Mth.cos(yaw + turn) * r;
                for (int dy = 0; dy >= -1; dy = dy == 0 ? 1 : dy == 1 ? -1 : -2) {
                    BlockPos feet = BlockPos.containing(x, anchor.getY() + dy, z);
                    if (!standable(level, feet)) continue;
                    Vec3 chest = new Vec3(x, feet.getY() + 1.0, z);
                    if (level.clip(new ClipContext(eye, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, anchor)).getType() == HitResult.Type.MISS) {
                        return new Vec3(x, feet.getY(), z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && clear(level, feet) && clear(level, feet.above());
    }

    private static boolean clear(ServerLevel level, BlockPos at) {
        BlockState s = level.getBlockState(at);
        return s.getCollisionShape(level, at).isEmpty() && s.getFluidState().isEmpty();
    }

    /** The thread let go during the fold: they get up where they are, still shielded for a moment. */
    static void standUp(UUID id) {
        if (FOLDED.remove(id) != null) GRACED.put(id, GRACE);
    }

    static void clear() {
        FOLDED.clear();
        GRACED.clear();
    }

    /** Brings the end of the fold to this tick (tests). */
    static void elapseForTest(MinecraftServer s, UUID id) {
        if (FOLDED.containsKey(id)) springBack(s, id);
    }
}
