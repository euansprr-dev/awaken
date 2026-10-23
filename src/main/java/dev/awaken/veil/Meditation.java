package dev.awaken.veil;

import dev.awaken.core.AwakenNet;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Meditation: hold Focus while sneaking and still, out of combat. The server decides; the client only says "held".
 * <ul>
 *   <li>10 s (then release): step out of your body.</li>
 *   <li>20 s (keep holding): go to the Stillpoint. In the Stillpoint, 10 s and release takes you back.</li>
 *   <li>N while meditating: just sit. No transition, as long as you like.</li>
 * </ul>
 * While meditating: energy regen x2 ({@link Veil#energyRegenMultiplier}), Fracture heals three times as fast, a little
 * health comes back, and the meditation counters tick.
 */
public final class Meditation {
    private Meditation() {}

    public static final int SETTLE = 20;
    public static final int STEP_OUT = 200;
    public static final int STILLPOINT = 400;
    public static final String HIGH_TICKS = "awaken:meditate_high_ticks";
    public static final String DAWN_LATENT = "awaken:latent/dawn_meditation_above_clouds";

    static final class State {
        boolean holding;
        boolean justSit;
        int ticks;
        Vec3 last = Vec3.ZERO;
        boolean effects;
        int pending;
        int pendingHigh;
        boolean combat;
        int combatCheck;
        int lastWarn = -1000;
        int age;
    }

    static final Map<UUID, State> STATES = new HashMap<>();
    /** Last time each player took damage (game time), for the "not in combat" rule. */
    static final Map<UUID, Long> LAST_HURT = new HashMap<>();

    public static boolean isMeditating(UUID player) {
        State s = STATES.get(player);
        return s != null && s.holding && s.ticks >= SETTLE;
    }

    static int ticks(UUID player) {
        State s = STATES.get(player);
        return s == null ? 0 : s.ticks;
    }

    /** The client pressed or released Focus (with sneak), or pressed N to just sit. */
    static void input(ServerPlayer sp, boolean holding, boolean justSit) {
        if (holding) {
            if (Veil.isSpirit(sp)) return;
            State s = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
            if (!s.holding) s.last = sp.position();
            s.holding = true;
            if (justSit && !s.justSit) {
                s.justSit = true;
                send(sp, s);
            }
            return;
        }
        State s = STATES.get(sp.getUUID());
        if (s == null) return;
        boolean complete = s.holding && s.ticks >= STEP_OUT && !s.justSit && canTravel(sp);
        stop(sp);
        if (!complete) return;
        if (Veil.inStillpoint(sp)) Stillpoint.leave(sp);
        else Projection.start(sp, false, null);
    }

    static boolean canTravel(ServerPlayer sp) {
        return Veil.enabled() && Progression.profile(sp).awakened();
    }

    /** Ends meditation without any transition. */
    public static void stop(ServerPlayer sp) {
        State s = STATES.remove(sp.getUUID());
        if (s == null) return;
        flush(sp, s);
        if (s.effects) sp.removePostEffect(Projection.RIPPLE_EFFECT);
        AwakenNet.send(sp, new VeilPayloads.Meditation(0, false, Veil.inStillpoint(sp), canTravel(sp)));
    }

    private static void flush(ServerPlayer sp, State s) {
        if (s.pending > 0) Progression.count(sp, Counters.MEDITATE_TICKS, s.pending);
        if (s.pendingHigh > 0) Progression.count(sp, HIGH_TICKS, s.pendingHigh);
        s.pending = 0;
        s.pendingHigh = 0;
    }

    static void tick(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        if (s == null || !s.holding) return;
        s.age++;
        if (!Veil.enabled() || Veil.isSpirit(sp) || !sp.isAlive()) {
            stop(sp);
            return;
        }
        ServerLevel level = sp.level();
        Vec3 pos = sp.position();
        boolean still = pos.distanceToSqr(s.last) < 0.0004;
        s.last = pos;
        if (--s.combatCheck <= 0) {
            s.combatCheck = 10;
            s.combat = inCombat(sp);
        }
        boolean valid = sp.isShiftKeyDown() && sp.onGround() && still && !s.combat && !sp.isSleeping() && !sp.isPassenger();
        if (!valid) {
            if (s.ticks > 0) {
                if (s.combat && s.ticks > SETTLE && s.age - s.lastWarn > 100) {
                    Signal.toast(sp, Component.literal("Not now. Something is hunting you."));
                    s.lastWarn = s.age;
                }
                flush(sp, s);
                s.ticks = 0;
                if (s.effects) {
                    sp.removePostEffect(Projection.RIPPLE_EFFECT);
                    s.effects = false;
                }
                send(sp, s);
            }
            return;
        }
        s.ticks++;
        if (s.ticks == SETTLE) {
            sp.addPostEffect(Projection.RIPPLE_EFFECT);
            s.effects = true;
        }
        boolean stillpoint = Veil.inStillpoint(sp);
        if (s.ticks >= SETTLE) {
            s.pending++;
            if (sp.getY() > 150 && !stillpoint) s.pendingHigh++;
        }
        if (s.ticks == 40) firstTime(sp);
        if (s.ticks % 20 == 0) {
            flush(sp, s);
            heal(sp, level, s.ticks);
            if (!stillpoint) checkDawn(sp, level);
        }
        if (s.ticks == STEP_OUT && !s.justSit && canTravel(sp)) {
            sp.connection.send(new ClientboundSoundPacket(VeilContent.THRESHOLD, SoundSource.PLAYERS, pos.x, pos.y, pos.z, 0.8F, 1.0F,
                    sp.getRandom().nextLong()));
        }
        if (s.ticks >= STILLPOINT && !s.justSit && !stillpoint && canTravel(sp)) {
            stop(sp);
            Stillpoint.enter(sp);
            return;
        }
        if (s.ticks % 4 == 0 || s.ticks == 1) send(sp, s);
    }

    private static void send(ServerPlayer sp, State s) {
        AwakenNet.send(sp, new VeilPayloads.Meditation(s.ticks, s.justSit, Veil.inStillpoint(sp), canTravel(sp)));
    }

    static boolean inCombat(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        Long hurt = LAST_HURT.get(sp.getUUID());
        if (hurt != null && now - hurt < 100) return true;
        return !sp.level().getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(10, 5, 10),
                m -> m instanceof Enemy && m.isAlive() && m.getTarget() == sp).isEmpty();
    }

    private static void firstTime(ServerPlayer sp) {
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        if (mem.firstMeditation) return;
        mem.firstMeditation = true;
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(SignalLines.get("first_meditation", "Quiet. Good. Stay.")))
                .ttl(20 * 6).priority(2).build());
    }

    /** Fracture heals three times as fast; a little health comes back. */
    private static void heal(ServerPlayer sp, ServerLevel level, int ticks) {
        long now = level.getGameTime();
        long until = Progression.profile(sp).fractureUntil();
        if (until > now) {
            long left = until - now - 40;
            if (left <= 0) {
                Progression.clearFracture(sp);
                Signal.toast(sp, Component.literal("Fracture healed"));
            } else {
                Progression.fracture(sp, (int) left);
            }
        }
        if (ticks % 60 == 0 && sp.getHealth() < sp.getMaxHealth() && sp.getFoodData().getFoodLevel() > 0) sp.heal(1F);
    }

    /** Meditating at dawn, above the clouds, under open sky: a latent wakes (Clarity). */
    private static void checkDawn(ServerPlayer sp, ServerLevel level) {
        if (Progression.profile(sp).counter(DAWN_LATENT) > 0) return;
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()) return;
        long t = Math.floorMod(level.getDefaultClockTime(), 24000L);
        boolean dawn = t >= 22800 || t <= 1200;
        if (!dawn) return;
        float cloud = level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, sp.position());
        if (sp.getY() <= cloud) return;
        if (!level.canSeeSky(sp.blockPosition().above())) return;
        Progression.setCounter(sp, DAWN_LATENT, 1);
    }
}
