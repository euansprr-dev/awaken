package dev.awaken.powers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A power that keeps going after its key press: a channel, a toggle or a timed effect (Flow, Barrier, a gravity well).
 * Ticked by {@link Casting} every server tick until it stops, runs out, or the player dies, leaves or changes world;
 * {@link End} always runs exactly once so attribute modifiers and post effects are always cleaned up.
 */
public final class Running {
    public enum Why { RELEASED, EXPIRED, NO_ENERGY, CANCELLED, TOGGLED_OFF, DONE }

    public interface Tick {
        void tick(Running r);
    }

    public interface End {
        void end(Running r, Why why);
    }

    public final Cast c;
    /** Unique per player: re-casting the same key replaces or refreshes the effect. */
    public final String key;
    public int age;
    /** Ticks to run; -1 = until stopped. */
    public int duration;
    /** Channel/toggle: drains energy each tick and starts the cooldown when it ends. */
    public boolean channel;
    /** A ladder step's cheaper channel (Flight's Contrails and Long Haul): the drain is scaled by this. */
    public float drainScale = 1f;
    public @Nullable Tick onTick;
    public @Nullable End onEnd;

    // scratch state for behaviours
    public Vec3 pos = Vec3.ZERO;
    public Vec3 dir = Vec3.ZERO;
    public double value;
    public int count;
    public @Nullable LivingEntity target;
    public @Nullable Object data;
    public final Set<Integer> hit = new HashSet<>();

    Why why;
    boolean ended;

    Running(Cast c, String key, int duration) {
        this.c = c;
        this.key = key;
        this.duration = duration;
    }

    public Running tick(Tick t) {
        this.onTick = t;
        return this;
    }

    public Running end(End e) {
        this.onEnd = e;
        return this;
    }

    public Running at(Vec3 p) {
        this.pos = p;
        return this;
    }

    /** Ask the effect to stop at the end of this tick. */
    public void stop(Why why) {
        if (this.why == null) this.why = why;
    }

    public void stop() {
        stop(Why.DONE);
    }

    public boolean stopping() {
        return why != null;
    }

    public int left() {
        return duration < 0 ? Integer.MAX_VALUE : duration - age;
    }

    /** 0..1 progress through a timed effect, or charge progress for a channel with a max length. */
    public double progress() {
        return duration <= 0 ? 0 : Math.min(1.0, (double) age / duration);
    }
}
