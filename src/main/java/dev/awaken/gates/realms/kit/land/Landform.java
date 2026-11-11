package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.realms.Land;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The big shape of a rolled land and the way through it (SPEC §50.9, "Rolled lands"): a height for every column, and a
 * run laid as stations on the {@link GeneratedLand} that owns it. A landform knows nothing of blocks; a skin knows
 * nothing of where the mountain is.
 */
public abstract class Landform {
    /** The five journeys. A blueprint names one in {@code land.params.landform} (its place in this list). */
    public enum Kind { ASCENT, VALLEY, BASIN, CAUSEWAY, ISLES }

    final GeneratedLand land;
    final long seed;
    final Random r;
    /** 0 at E rank, 5 at S. */
    final int rank;

    Landform(GeneratedLand land, long salt) {
        this.land = land;
        seed = land.seed;
        r = new Random(seed * 0x9E3779B97F4A7C15L ^ salt);
        rank = Math.min(5, land.ctx.rank().ordinal());
    }

    static Landform make(Kind kind, GeneratedLand land) {
        if (kind == Kind.ASCENT) return new Ascent(land);
        throw new IllegalArgumentException("the " + kind + " landform is not built yet");
    }

    /** The landform a blueprint asks for, or the one its seed rolls from those that are built. */
    public static Kind of(Land.Ctx ctx) {
        Double named = ctx.bp().land().params().get("landform");
        if (named != null && named >= 0 && named < Kind.values().length) return Kind.values()[named.intValue()];
        return Kind.ASCENT;
    }

    /** Stations, sites, legs, the fall catch and every piece the journey itself needs; ends with the ways frozen. */
    abstract void lay();

    /** The ground with everything on it but the ways. {@code calm} is 0 beside a way and 1 well clear of one. */
    abstract double natural(double x, double z, double calm);

    /** What is cut out of the ground after the ways are laid on it (a rift a bridge crosses). */
    double cut(double x, double z, double s) {
        return s;
    }

    /** 0 to 1: how much the rock here stands in columns. */
    double columns(double x, double z) {
        return 0;
    }

    double columnSize() {
        return 3.4;
    }

    double columnJitter() {
        return 3;
    }

    /** Whether what burns lies on the ground here. */
    boolean crust(double x, double z) {
        return false;
    }

    double heat(double x, double z) {
        return 0;
    }

    abstract double edge(int x, int z);

    abstract int half();

    abstract float mouthYaw();

    Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    /**
     * A way being laid: its points in order, each with the height the big shape has there. {@link #settle} turns those
     * into heights a way can have: never steeper than asked, and dead level (on a whole block) wherever a place stands.
     */
    static final class Trail {
        /** x, z, the height wanted, 1 where it must be level, and (once settled) the feet. */
        final List<double[]> p = new ArrayList<>();

        void add(double x, double z, double want) {
            p.add(new double[]{x, z, want, 0, 0});
        }

        int last() {
            return p.size() - 1;
        }

        double x(int i) {
            return p.get(i)[0];
        }

        double z(int i) {
            return p.get(i)[1];
        }

        double feet(int i) {
            return p.get(i)[4];
        }

        void level(int from, int to) {
            for (int i = Math.max(0, from); i <= Math.min(last(), to); i++) p.get(i)[3] = 1;
        }

        void settle(double start, double grade) {
            double y = start;
            for (int i = 0; i < p.size(); i++) {
                double[] q = p.get(i);
                if (q[3] != 0) {
                    if (i > 0 && p.get(i - 1)[3] == 0) y = Math.rint(y);
                } else y = Math.max(y - grade, Math.min(y + grade, q[2]));
                q[4] = y;
            }
        }

        /** The way's own direction at a point, as a unit vector. */
        double[] heading(int i) {
            int a = Math.max(0, i - 2), b = Math.min(last(), i + 2);
            double dx = x(b) - x(a), dz = z(b) - z(a), l = Math.max(1e-6, Math.hypot(dx, dz));
            return new double[]{dx / l, dz / l};
        }
    }
}
