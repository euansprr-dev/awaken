package dev.awaken.powers.ladder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * One shared mechanic of a ladder step (SPEC 34.3), as tools/art/specs/power_ladder_table.py wrote it. Fields are named for
 * what they mean in at least one kind; a kind reads only its own. Shares are of the hit that started them, ticks game ticks.
 */
public record Rider(Kind kind, int n, float share, float reach, int delay, float radius, int ticks, float dps, int amp, float force,
                    float lift, float energy, boolean kill, String sub, float range, float duration, float charge) {

    public enum Kind {
        /** Bigger, further, longer, sooner (radius, range, duration, charge: fractions added). */
        EXTEND,
        /** Each hit arcs on to n more foes within reach for a share. */
        CHAIN,
        /** The hit lands again on the same foe after delay ticks for a share. */
        ECHO,
        /** A pulse round the hit after delay ticks, radius, for a share. */
        AFTERSHOCK,
        /** A ground patch at the hit (sub: burn, slow, frost, void, hold, heal, shock) for ticks, radius, dps. */
        LINGER,
        /** A state on the foe hit (sub: burn, slow, weak, stun, mark, blind, root, lift) for ticks at amp. */
        STATUS,
        /** Extra knock (force, lift). */
        PUSH,
        /** Energy back on a hit, or only on a kill. */
        REFUND,
        /** The power's own code: through n more foes for a share each. */
        PIERCE,
        /** The power's own code: splits into n for a share each. */
        SPLIT,
        /** The power's own code: a held power's damage climbs by share over ticks. */
        RAMP,
        /** Damage taken is cut by share for ticks after the cast. */
        GUARD;

        /** On-hit kinds run from Riders.onHit; the rest are read where the power or the cast uses them. */
        public boolean onHit() {
            return switch (this) {
                case CHAIN, ECHO, AFTERSHOCK, LINGER, STATUS, PUSH, REFUND -> true;
                default -> false;
            };
        }
    }

    static Rider parse(JsonObject o) {
        Kind kind = Kind.valueOf(o.get("k").getAsString().toUpperCase(Locale.ROOT));
        String sub = o.has("kind") ? o.get("kind").getAsString() : o.has("effect") ? o.get("effect").getAsString() : "";
        return new Rider(kind, i(o, "n"), f(o, "share"), f(o, "reach"), i(o, "delay"), f(o, "radius"), i(o, "ticks"), f(o, "dps"), i(o, "amp"),
                f(o, "force"), f(o, "lift"), f(o, "energy"), o.has("kill") && o.get("kill").getAsBoolean(), sub, f(o, "range"), f(o, "duration"),
                f(o, "charge"));
    }

    private static float f(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0f : e.getAsFloat();
    }

    private static int i(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : Math.round(e.getAsFloat());
    }
}
