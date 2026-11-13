package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.check.MoveProof;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A shaft at plan 3 (SPEC §72.3): flights of beats round a core, turning on a landing at each corner, about twenty-four
 * down (or up, on a climb). Six forms: a spiral with landings (holds on one to three of them), a broken spiral (its breaks
 * crossed by a jump, a run along the wall or a drop to a hang), a chimney (wall jumps up, chains down), a chain fall, ledges
 * (hang and drop down, ledge to ledge up), a square well (a wall run each side and down by the ramp). Shafts that go up
 * get the climbing forms. No flight runs into another (their boxes are kept apart); a well that can't be had is the shaft
 * of before.
 */
public final class BeatWell {
    private BeatWell() {}

    public enum Form {
        SPIRAL("A spiral stair"), BROKEN("A broken spiral"), CHIMNEY("A chimney"), CHAIN("A chain fall"), LEDGES("Ledges round a well"), SQUARE("A square well");

        public final String title;

        Form(String title) {
            this.title = title;
        }
    }

    /** The forms a shaft can be: going up, the climbing ones. */
    public static List<Form> forms(boolean up, Rank rank) {
        List<Form> out = new ArrayList<>(List.of(Form.SPIRAL, Form.BROKEN, Form.LEDGES));
        if (rank.ordinal() >= Rank.D.ordinal()) out.add(up ? Form.CHIMNEY : Form.CHAIN);
        if (rank.ordinal() >= Rank.D.ordinal()) out.add(Form.SQUARE);
        if (!up && rank.ordinal() >= Rank.C.ordinal()) out.add(Form.CHIMNEY);
        return out;
    }

    /** The beats a flight of a form may have (none: stairs), going down or up. */
    static List<Beat> beats(Form f, boolean up, Rank rank) {
        List<Beat> all = switch (f) {
            case SPIRAL -> List.of();
            case BROKEN -> up ? List.of(Beat.JUMPHANG, Beat.MANTLE, Beat.GULF) : List.of(Beat.DROPROLL, Beat.DROPHANG, Beat.GULF, Beat.CRUMBLE);
            case CHIMNEY -> up ? List.of(Beat.CHIMNEY, Beat.LEDGES) : List.of(Beat.CHAINFALL);
            case CHAIN -> up ? List.of(Beat.PIPE) : List.of(Beat.CHAINFALL);
            case LEDGES -> up ? List.of(Beat.LEDGES, Beat.JUMPHANG, Beat.MANTLE) : List.of(Beat.DROPHANG);
            case SQUARE -> List.of(Beat.GULF);
        };
        List<Beat> out = new ArrayList<>();
        for (Beat b : all) if (b.rank.ordinal() <= rank.ordinal()) out.add(b);
        return out;
    }

    /** A well as rolled: its form, which way it turns, its flights' beats (null: stairs only), sizes, sides and seeds. */
    public record Plan(Form form, int turn, List<Composer.Step> flights, List<Boolean> holds) {}

    /** How deep a well goes, and in how many flights. */
    static final int DEPTH = 24, FLIGHTS = 6;

    /** A plan for a well, or null when nothing of its forms lays and holds here. */
    public static @Nullable Plan roll(boolean up, Rank rank, Random r, Composer.Paints paint, Composer.Run run) {
        List<Form> forms = forms(up, rank);
        for (int tries = 0; tries < 6; tries++) {
            Form f = forms.get(r.nextInt(forms.size()));
            List<Beat> kit = beats(f, up, rank);
            List<Composer.Step> flights = new ArrayList<>();
            List<Boolean> holds = new ArrayList<>();
            // at S every landing but the last has its hold (SPEC §72.5)
            int holding = rank.ordinal() >= Rank.S.ordinal() ? FLIGHTS - 1 : f == Form.SPIRAL ? 1 + r.nextInt(3) : 1;
            Beat last = null;
            for (int k = 0; k < FLIGHTS; k++) {
                // a flight in two has a beat (a spiral's never), never the same twice running
                Beat b = null;
                if (!kit.isEmpty() && (k % 2 == 0 || f == Form.SQUARE || f == Form.CHAIN)) {
                    List<Beat> pool = new ArrayList<>(kit);
                    if (pool.size() > 1) pool.remove(last);
                    b = pool.get(r.nextInt(pool.size()));
                }
                last = b;
                flights.add(b == null ? null : new Composer.Step(b, Composer.sizeAt(rank, r), r.nextBoolean() ? 1 : -1, r.nextLong()));
                holds.add(false);
            }
            // the landings with a hold: on a spiral one to three, else one; never the last (the way out)
            for (int i = 0; i < holding; i++) holds.set(holding == FLIGHTS - 1 ? i : r.nextInt(FLIGHTS - 1), true);
            Plan p = new Plan(f, r.nextBoolean() ? 1 : -1, flights, holds);
            String why = check(p, up, rank, paint);
            if (why == null) return p;
            run.note("well " + f + ": " + why);
        }
        return null;
    }

    /**
     * Lays a well into {@code L} from its cursor. {@code turn} is which way it goes round. Each flight: its beat if it has
     * one, the stairs for its share of the depth, and a landing (a hold on it if the plan says).
     */
    public static boolean lay(Plan p, BeatLine L, boolean up, Rank rank, @Nullable List<int[]> spans) {
        int start = L.feet, want = up ? DEPTH : -DEPTH;
        Beats.pad(L, 2);
        if (spans != null) spans.add(L.span());
        for (int k = 0; k < FLIGHTS; k++) {
            Composer.Step st = p.flights().get(k);
            if (st != null && !Beats.build(st.beat(), L, st.t(), st.side(), rank, new Random(st.seed()))) return false;
            int done = L.feet - start, left = FLIGHTS - k, share = (want - done) / left;
            Beats.stairs(L, Math.clamp(share, -6, 6));
            Beats.pad(L, 1);
            if (k < FLIGHTS - 1) {
                L.landing(p.turn(), L.feet + BeatLine.HEAD);
                L.catchAt(0, 0, L.feet);
                if (p.holds().get(k)) L.hold(Land.HoldKind.HOLD, 0, 0, L.feet, 2, rank.ordinal() >= Rank.D.ordinal() ? 3 : 2);
            }
            if (spans != null) spans.add(L.span());
        }
        Beats.pad(L, 2);
        if (spans != null) spans.add(L.span());
        return true;
    }

    /** Why a plan can't be had (a flight that won't lay or hold, two flights that run into each other), or null. */
    static @Nullable String check(Plan p, boolean up, Rank rank, Composer.Paints paint) {
        BeatLine L = new BeatLine("well", 0, 160, 0, 0, 1);
        List<int[]> spans = new ArrayList<>();
        if (!lay(p, L, up, rank, spans)) return "a flight didn't lay";
        // a flight is the cells between two landings: each touches the next, but nothing further on. Side by side they keep a
        // cell apart; one over another may share the rock between them (a span's top is its roof and its bottom the stone
        // under its floor, both solid), so they only meet where they overlap
        for (int i = 0; i < spans.size(); i++) for (int j = i + 2; j < spans.size(); j++) {
            int[] a = spans.get(i), b = spans.get(j);
            if (a[0] > a[3] || b[0] > b[3]) continue;
            if (a[0] <= b[3] + 1 && a[3] >= b[0] - 1 && a[1] <= b[4] && a[4] >= b[1] && a[2] <= b[5] + 1 && a[5] >= b[2] - 1) {
                return "flights " + i + " and " + j + " meet";
            }
        }
        L.s.done();
        List<String> f = MoveProof.failures(L.s.view(paint.paint()), L.moves, rank);
        return f.isEmpty() ? null : f.getFirst();
    }

    /** A plan's box in its own frame (start (0, 0) going +z, feet 0), its end and the way on: as {@link Composer.Recipe#at}. */
    public static int[] measure(Plan p, boolean up, Rank rank) {
        BeatLine L = new BeatLine("well", 0, 0, 0, 0, 1);
        lay(p, L, up, rank, null);
        L.s.done();
        return new int[]{L.s.x0, L.yLo - 10, L.s.z0, L.s.x1, L.yHi + 2, L.s.z1, L.x(L.a, 0), L.feet, L.z(L.a, 0), L.dx(), L.dz()};
    }

    /** A measured box in the world, from the start cell going (dx, dz): as {@link Composer.Recipe#at}. */
    public static int[] at(int[] m, int x, int feet, int z, int dx, int dz) {
        int[] a = Composer.rot(m[0], m[2], dx, dz), b = Composer.rot(m[3], m[5], dx, dz), e = Composer.rot(m[6], m[8], dx, dz), d = Composer.rot(m[9], m[10], dx, dz);
        return new int[]{x + Math.min(a[0], b[0]), feet + m[1], z + Math.min(a[1], b[1]), x + Math.max(a[0], b[0]), feet + m[4], z + Math.max(a[1], b[1]),
                x + e[0], feet + m[7], z + e[1], d[0], d[1]};
    }
}
