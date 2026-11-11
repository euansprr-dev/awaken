package dev.awaken.together;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.DreamHooks;
import dev.awaken.core.api.GateHooks;
import dev.awaken.hearth.trial.Trials;
import dev.awaken.powers.Powers;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Answering Peal (SPEC §70.6): a partner rings their handbell and you hear it from where they are; ring yours back
 * within a minute, stand still five seconds, and you're beside them. It is Homeward's rule pointed at a person. Both in
 * the overworld, neither in a fight, a gate, a trial or a dream; every ten minutes for the one who travels. Not saved:
 * a restart cancels a call.
 */
final class Answering {
    private Answering() {}

    static final int CALL_TICKS = 20 * 60, WAIT = 100, AGAIN = 20 * 60 * 10;
    /** A partner nearer than this needs no peal. */
    static final double NEAR = 64;
    /** A call is heard from this far off along its bearing. */
    static final double HEARD_AT = 24;

    private record Call(UUID by, long until) {}

    private record Pending(Vec3 from, long started, long at, UUID to) {}

    private static final Map<UUID, Call> CALLS = new HashMap<>();
    private static final Map<UUID, Pending> TRAVEL = new HashMap<>();
    private static final Map<UUID, Long> AGAIN_AT = new HashMap<>();

    static void forget() {
        CALLS.clear();
        TRAVEL.clear();
        AGAIN_AT.clear();
    }

    /** What stops {@code p} going to {@code to} now, in words for them, or null. */
    static @Nullable String whyNot(ServerPlayer p, @Nullable ServerPlayer to) {
        if (to == null || !to.isAlive()) return "They've gone.";
        if (p.level().dimension() != Level.OVERWORLD || to.level().dimension() != Level.OVERWORLD) return "The Answering Peal only carries you across this world, and you both have to be out in it.";
        if (Powers.inCombat(p) || Powers.inCombat(to)) return "Not while either of you is in a fight.";
        if (GateHooks.inGate(p) || GateHooks.inGate(to) || Trials.inTrial(p) || Trials.inTrial(to)) return "Not while either of you is in a gate or a trial.";
        if (DreamHooks.isDreaming(p) || DreamHooks.isDreaming(to)) return "Not while either of you is dreaming.";
        if (Fights.kneeling(p.getUUID()) || Fights.kneeling(to.getUUID())) return "Not while either of you is on a knee.";
        long now = p.level().getGameTime();
        long again = AGAIN_AT.getOrDefault(p.getUUID(), 0L);
        if (again > now) {
            long s = (again - now + 19) / 20;
            return "The Answering Peal can carry you again in " + (s >= 120 ? (s + 59) / 60 + " minutes" : s >= 60 ? "a minute" : s + " seconds") + ".";
        }
        return null;
    }

    /**
     * A handbell rang. If a partner rang for this player within the last minute, the ring answers them (and isn't
     * their own peal this time). Otherwise it calls to every partner far enough off to need it, and rings as it always did.
     */
    static boolean rung(ServerLevel level, ServerPlayer p) {
        if (!TogetherConfig.joins()) return false;
        long now = level.getGameTime();
        Call call = CALLS.get(p.getUUID());
        if (call != null && call.until() >= now) {
            CALLS.remove(p.getUUID());
            ServerPlayer to = level.getServer().getPlayerList().getPlayer(call.by());
            String no = whyNot(p, to);
            if (no != null) {
                p.sendOverlayMessage(Component.literal(no));
                return true;
            }
            TRAVEL.put(p.getUUID(), new Pending(p.position(), now, now + WAIT, call.by()));
            p.sendOverlayMessage(Component.literal("Answering " + to.getGameProfile().name() + ". Stand still."));
            return true;
        }
        if (level.dimension() != Level.OVERWORLD) return false;
        for (ServerPlayer m : PartyService.online(p)) {
            if (m == p || m.level() != level || m.distanceToSqr(p) < NEAR * NEAR) continue;
            CALLS.put(m.getUUID(), new Call(p.getUUID(), now + CALL_TICKS));
            // heard from its bearing, fainter the further it is
            Vec3 toward = p.position().subtract(m.position());
            double far = toward.length();
            Vec3 at = m.position().add(toward.scale(HEARD_AT / far));
            float volume = (float) Math.max(0.5, 1.6 - far / 2000.0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, volume * 2F, 1F);
            Signal.toast(m, Component.literal(p.getGameProfile().name() + " is ringing for you. Ring your handbell within a minute and you'll be brought to them."));
        }
        return false;
    }

    static void tick(MinecraftServer server) {
        if (TRAVEL.isEmpty()) return;
        TRAVEL.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) return true;
            ServerLevel level = p.level();
            Pending w = e.getValue();
            long now = level.getGameTime();
            if (p.position().distanceToSqr(w.from()) > 0.6 * 0.6 || p.hurtTime > 0) {
                p.sendOverlayMessage(Component.literal("The Answering Peal broke off. Stand still and ring it again."));
                return true;
            }
            // a ring of light closes round them as it gathers, as Homeward's does
            if ((now - w.started()) % 4 == 0) {
                double f = 1.0 - (double) (now - w.started()) / Math.max(1, w.at() - w.started());
                double r = 0.4 + 1.2 * f;
                for (int k = 0; k < 10; k++) {
                    double a = k * Math.PI / 5 + now * 0.15;
                    level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * r, p.getY() + 0.1 + (1 - f) * 1.6, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (now < w.at()) return false;
            ServerPlayer to = server.getPlayerList().getPlayer(w.to());
            String no = whyNot(p, to);
            if (no != null) {
                p.sendOverlayMessage(Component.literal(no));
                return true;
            }
            BlockPos beside = standing(to.level(), to.blockPosition());
            level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1F, 1.4F);
            p.teleportTo(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5);
            p.resetFallDistance();
            level.playSound(null, beside, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5F, 1.1F);
            level.sendParticles(ParticleTypes.END_ROD, beside.getX() + 0.5, beside.getY() + 1, beside.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.02);
            AGAIN_AT.put(p.getUUID(), now + AGAIN);
            Signal.toast(to, Component.literal(p.getGameProfile().name() + " heard you and is here."));
            DirectorLog.log(p, "together", "the Answering Peal to " + to.getGameProfile().name());
            return true;
        });
    }

    /** A place to stand beside them: a neighbouring block with ground under it and room for a body, else their own. */
    private static BlockPos standing(ServerLevel level, BlockPos at) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos feet = at.relative(d, 1).above(dy);
                BlockPos below = feet.below();
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getFluidState(feet).isEmpty()) return feet;
            }
        }
        return at;
    }
}
