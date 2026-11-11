package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.Land.SiteKind;
import dev.awaken.gates.realms.kit.N;
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
    /**
     * +1, or -1 where the seed turned east to west. A landform works everything out in a frame of its own (u, v) and the
     * land's x is {@code mir * u}; its z is v.
     */
    final int mir;

    Landform(GeneratedLand land, long salt) {
        this.land = land;
        seed = land.seed;
        r = new Random(seed * 0x9E3779B97F4A7C15L ^ salt);
        rank = Math.min(5, land.ctx.rank().ordinal());
        mir = r.nextBoolean() ? 1 : -1;
    }

    static Landform make(Kind kind, GeneratedLand land) {
        if (kind == Kind.ASCENT) return new Ascent(land);
        if (kind == Kind.VALLEY) return new Valley(land);
        if (kind == Kind.BASIN) return new Basin(land);
        if (kind == Kind.CAUSEWAY) return new Causeway(land);
        return new Isles(land);
    }

    /** The landform a blueprint asks for, or the one its seed rolls from those that are built. */
    public static Kind of(Land.Ctx ctx) {
        Double named = ctx.bp().land().params().get("landform");
        if (named != null && named >= 0 && named < Kind.values().length) return Kind.values()[named.intValue()];
        return Kind.ASCENT;
    }

    // ---- what every landform lays its way with -------------------------------------------------------------------------------

    /** The big shape alone, in the landform's own frame: what a way follows. */
    abstract double bs(double u, double v);

    /** Uphill on the big shape at a point: a unit vector, and how steep. */
    double[] up(double u, double v) {
        double gu = (bs(u + 1.5, v) - bs(u - 1.5, v)) / 3, gv = (bs(u, v + 1.5) - bs(u, v - 1.5)) / 3, s = Math.hypot(gu, gv);
        if (s < 1e-4) {
            double l = Math.max(1e-6, Math.hypot(u, v));
            return new double[]{-u / l, -v / l, 0};
        }
        return new double[]{gu / s, gv / s, s};
    }

    void add(Trail t, double u, double v) {
        t.add(u, v, bs(u, v));
    }

    /**
     * Walks the big shape from the trail's end with the hill on one hand, climbing at a grade, until it is as high as
     * asked or has crossed a line of u (NaN for no line).
     */
    void traverse(Trail t, int dir, double grade, double untilY, double untilU) {
        double u = t.x(t.last()), v = t.z(t.last());
        for (int n = 0; n < 400; n++) {
            if (bs(u, v) >= untilY) return;
            if (!Double.isNaN(untilU) && (dir > 0 ? u >= untilU : u <= untilU)) return;
            double[] g = up(u, v);
            double sin = Math.min(1, grade / Math.max(1e-3, g[2])), cos = Math.sqrt(1 - sin * sin), tu = -g[1], tv = g[0];
            if (tu * dir < 0) {
                tu = -tu;
                tv = -tv;
            }
            u += tu * cos + g[0] * sin;
            v += tv * cos + g[1] * sin;
            add(t, u, v);
        }
    }

    int lx(double u) {
        return (int) Math.round(mir * u);
    }

    static int li(double v) {
        return (int) Math.round(v);
    }

    int st(double u, double v, double feet, double flat, double blend, byte kind) {
        return land.station(mir * u, v, feet, flat, blend, kind, null);
    }

    /** A straight track between two points, its height going evenly from one to the other (straight, so it may climb a block in a block). */
    void spur(double u0, double v0, double f0, double u1, double v1, double f1, double flat) {
        double len = Math.hypot(u1 - u0, v1 - v0);
        int n = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            st(N.lerp(u0, u1, t), N.lerp(v0, v1, t), N.lerp(f0, f1, t), flat, 4, GeneratedLand.SPUR);
        }
        land.way(mir * u0, v0, mir * u1, v1, 2);
    }

    /** A den's site in the middle of its place, and whatever the skin stands round it. */
    void den(Trail t, int i, int radius, int index, int kind) {
        int x = lx(t.x(i)), z = li(t.z(i)), feet = (int) t.feet(i);
        land.putSite(SiteKind.DEN, x, feet, z, 10, "");
        int back = Math.max(0, i - 10);
        land.denPlace(x, feet, z, radius, kind, mir * t.x(back), t.z(back));
    }

    void find(double u, double v) {
        LandPieces.Cache c = new LandPieces.Cache(lx(u), li(v), seed + 400 + land.sites(SiteKind.FIND).size(), land);
        land.must(c);
        land.putSite(SiteKind.FIND, c.x, c.gy + 1, c.z, 2, "");
    }

    void stone(int x, int z, String name) {
        LandPieces.Marker m = new LandPieces.Marker(x, z, land);
        land.must(m);
        land.putSite(SiteKind.WAYSTONE, m.x, m.gy + 2, m.z, 4, name);
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

    /** Whether a column is part of something built that the ground itself carries (a temple's tiers). */
    boolean built(double x, double z) {
        return false;
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
