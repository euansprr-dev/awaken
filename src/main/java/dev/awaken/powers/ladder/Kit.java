package dev.awaken.powers.ladder;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a power's ladder adds at one level, summed over its steps 2..level (each level keeps the steps below it). Built
 * once per power and level and shared by every cast ({@link PowerLadder#kit}); a cast keeps the kit of the level it started at.
 */
public final class Kit {
    public static final Kit NONE = new Kit(1, List.of());

    public final int level;
    /** Multipliers for the cast's radius, range and duration (1 = as level 1), and for the time to full charge. */
    public final float radius, range, duration, charge;
    /** The on-hit riders, in step order. */
    public final Rider[] onHit;
    /** The highest step's rider of each kind the power's own code reads, or null. */
    public final @Nullable Rider pierce, split, ramp, guard;

    Kit(int level, List<PowerLadder.Step> steps) {
        this.level = level;
        float r = 0, g = 0, d = 0, ch = 0;
        List<Rider> hits = new ArrayList<>();
        Rider pi = null, sp = null, ra = null, gu = null;
        for (PowerLadder.Step s : steps) {
            if (s.level() > level) continue;
            for (Rider x : s.riders()) {
                switch (x.kind()) {
                    case EXTEND -> {
                        r += x.radius();
                        g += x.range();
                        d += x.duration();
                        ch += x.charge();
                    }
                    case PIERCE -> pi = x;
                    case SPLIT -> sp = x;
                    case RAMP -> ra = x;
                    case GUARD -> gu = x;
                    default -> hits.add(x);
                }
            }
        }
        this.radius = 1 + r;
        this.range = 1 + g;
        this.duration = 1 + d;
        this.charge = Math.max(0.3f, 1 - ch);
        this.onHit = hits.toArray(new Rider[0]);
        this.pierce = pi;
        this.split = sp;
        this.ramp = ra;
        this.guard = gu;
    }

    public boolean has(Rider.Kind kind) {
        for (Rider x : onHit) if (x.kind() == kind) return true;
        return switch (kind) {
            case PIERCE -> pierce != null;
            case SPLIT -> split != null;
            case RAMP -> ramp != null;
            case GUARD -> guard != null;
            default -> false;
        };
    }
}
