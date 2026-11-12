package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.Awaken;
import dev.awaken.gates.realms.Land.Family;
import dev.awaken.gates.realms.check.MoveProof;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The composer (SPEC §72.3): picks the beats of every line of a run, for the leg's height change and room, the rank and the
 * kind's accents. Its rules: never the same beat twice in a row; every line asks for a move that isn't walking or a sprint
 * jump, and two families from C; across a run no family is more than 40% of the beats; a kind leans to its families and
 * its signature beat comes once a run, at its largest. Every beat is proved on the kind's own blocks with the body's rules
 * before it is kept; one that fails is rolled again (up to six times, a little smaller each time), then swapped for a
 * simpler beat of its family, then left out ({@code beats.fallback}); a line with nothing left is today's way.
 */
public final class Composer {
    private Composer() {}

    /** A kind's accents (SPEC §72.3): how much it leans to each family (1 is plain) and its signature beat. */
    public record Accents(Map<Family, Integer> lean, @Nullable Beat signature) {
        public static final Accents NONE = new Accents(Map.of(), null);

        /** Accents from pairs: a family, its weight, a family, its weight... */
        public static Accents of(@Nullable Beat signature, Object... pairs) {
            Map<Family, Integer> m = new EnumMap<>(Family.class);
            for (int i = 0; i + 1 < pairs.length; i += 2) m.put((Family) pairs[i], (Integer) pairs[i + 1]);
            return new Accents(Map.copyOf(m), signature);
        }

        int weight(Family f) {
            return lean.getOrDefault(f, 1);
        }
    }

    /** One beat of a line: which, its size (0 an E gate's, 1 an S gate's), the side it's on, the seed of its small rolls. */
    public record Step(Beat beat, double t, int side, long seed) {}

    /** How a run's lines are going so far: the families met, the last beat, the signature, the fallbacks. */
    public static final class Run {
        final EnumMap<Family, Integer> count = new EnumMap<>(Family.class);
        int total;
        boolean signed;
        /** Whether the run has had its chase (from B) and its first perch (from C) yet (SPEC §72.5). */
        boolean chased, perched;
        @Nullable Beat last;
        int fallbacks;
        final List<String> log = new ArrayList<>();

        public int fallbacks() {
            return fallbacks;
        }

        public List<String> log() {
            return log;
        }

        void note(String what) {
            fallbacks++;
            log.add(what);
            Awaken.LOG.debug("[realms] beats.fallback {}", what);
        }
    }

    /**
     * A line as composed: its steps, the stairs after each, and where it goes in its own frame (it starts at (0, 0) going
     * +z, feet 0): the box it takes, where it ends (the cell past its last) and which way it is going there.
     */
    /**
     * The fights a line has (SPEC §72.5): a landing with a hold after step {@code courts} lists (from A: partway along; at S
     * after every second beat), a marksman on the far side of step {@code perch} (a gulf crossed on a wall, a rope or
     * ledges; from C), and a chase from its start (from B, once a run, on a line with no bay off it). -1 or false: none.
     */
    public record Fights(List<Integer> courts, int perch, boolean chase) {
        public static final Fights NONE = new Fights(List.of(), -1, false);
    }

    /** The beats a marksman can be perched across: the gulfs. */
    private static final Set<Beat> ACROSS = EnumSet.of(Beat.GULF, Beat.CORNER, Beat.RUNHANG, Beat.SWING, Beat.LEDGES, Beat.BROKEN);

    /** The fights for a line of these steps at this rank, where this run has room for them. */
    static Fights fights(List<Step> steps, Rank rank, Run run, Random r) {
        int ord = rank.ordinal();
        List<Integer> courts = new ArrayList<>();
        if (ord >= Rank.S.ordinal()) for (int i = 1; i < steps.size() - 1; i += 2) courts.add(i);
        else if (ord >= Rank.A.ordinal() && steps.size() >= 3) courts.add(steps.size() / 2 - 1);
        int perch = -1;
        if (ord >= Rank.C.ordinal() && (!run.perched || ord >= Rank.B.ordinal() && r.nextBoolean())) {
            for (int i = 0; i < steps.size() && perch < 0; i++) if (ACROSS.contains(steps.get(i).beat())) perch = i;
        }
        boolean bays = false;
        for (Step st : steps) bays |= !st.beat().onTheWay();
        boolean chase = ord >= Rank.B.ordinal() && !run.chased && !bays && r.nextInt(3) == 0;
        return new Fights(List.copyOf(courts), perch, chase);
    }

    public static final class Recipe {
        public final List<Step> steps;
        final int[] stairs;
        public final Rank rank;
        public Fights fights = Fights.NONE;
        /** The box (x0, y0, z0, x1, y1, z1) in the line's own frame, feet 0 at the start; walls and pits included. */
        public int[] box;
        public int endX, endZ, endDx, endDz, dy, length;

        Recipe(List<Step> steps, int[] stairs, Rank rank) {
            this.steps = steps;
            this.stairs = stairs;
            this.rank = rank;
        }

        /** Lays the line into {@code L} from its cursor: a pad, each beat with its stairs and a pad after it, and its fights. */
        public boolean lay(BeatLine L) {
            Beats.pad(L, 2);
            int ord = rank.ordinal();
            if (fights.chase()) L.hold(dev.awaken.gates.realms.Land.HoldKind.CHASE, 1, 0, L.feet, 1, 3 + (ord >= Rank.S.ordinal() ? 1 : 0));
            for (int i = 0; i < steps.size(); i++) {
                Step st = steps.get(i);
                if (!Beats.build(st.beat(), L, st.t(), st.side(), rank, new Random(st.seed()))) return false;
                if (i == fights.perch()) L.hold(dev.awaken.gates.realms.Land.HoldKind.PERCH, L.a - 2, 0, L.feet, 1, ord >= Rank.A.ordinal() ? 2 : 1);
                Beats.stairs(L, stairs[i]);
                Beats.pad(L, 2);
                if (fights.courts().contains(i)) {
                    Beats.court(L, ord >= Rank.S.ordinal() ? 4 : 3);
                    Beats.pad(L, 1);
                }
            }
            return true;
        }

        /** What it is called on the sheet: its beats in plain words. */
        public String name() {
            List<String> t = new ArrayList<>();
            for (Step s : steps) if (s.beat().onTheWay()) t.add(s.beat().title);
            if (t.isEmpty()) return "A way";
            StringBuilder b = new StringBuilder(t.getFirst());
            for (int i = 1; i < t.size(); i++) {
                String w = t.get(i);
                b.append(i == t.size() - 1 ? " and " : ", ").append(Character.toLowerCase(w.charAt(0))).append(w.substring(1));
            }
            return b.toString();
        }

        /** In the world, from the start cell going (dx, dz): the box, the end cell and the way on. */
        public int[] at(int x, int feet, int z, int dx, int dz) {
            int[] a = rot(box[0], box[2], dx, dz), b = rot(box[3], box[5], dx, dz), e = rot(endX, endZ, dx, dz), d = rot(endDx, endDz, dx, dz);
            return new int[]{x + Math.min(a[0], b[0]), feet + box[1], z + Math.min(a[1], b[1]), x + Math.max(a[0], b[0]), feet + box[4], z + Math.max(a[1], b[1]),
                    x + e[0], feet + dy, z + e[1], d[0], d[1]};
        }

        /** The way this line's steps go up or down without its stairs. */
        int beatDy() {
            int s = 0;
            for (int st : stairs) s += st;
            return dy - s;
        }
    }

    /** A point of a line's own frame (start (0, 0), going +z) in a frame going (dx, dz). */
    static int[] rot(int x, int z, int dx, int dz) {
        return new int[]{x * dz + z * dx, -x * dx + z * dz};
    }

    // ---- the rank table (SPEC §72.3) ------------------------------------------------------------------------------------

    /** How many beats a line has at a rank: E 2-3, D 3-4, C 4-5, B 4-6, A 5-7, S 6-8. */
    static int beatsAt(Rank rank, Random r) {
        return switch (rank) {
            case E -> 2 + r.nextInt(2);
            case D -> 3 + r.nextInt(2);
            case C -> 4 + r.nextInt(2);
            case B -> 4 + r.nextInt(3);
            case A -> 5 + r.nextInt(3);
            default -> 6 + r.nextInt(3);
        };
    }

    /** A beat's size at a rank: 0 at E to 1 at S, and a little either way. */
    static double sizeAt(Rank rank, Random r) {
        double base = Math.min(5, rank.ordinal()) / 5.0;
        return Math.clamp(base + (r.nextDouble() - 0.6) * 0.25, 0, 1);
    }

    /** The longest a line may be at a rank (cells along): long lines across whole rooms only at the top. */
    public static int longestAt(Rank rank) {
        return 40 + 6 * Math.min(5, rank.ordinal());
    }

    // ---- composing ------------------------------------------------------------------------------------------------------

    /**
     * A line for a leg: {@code wantDy} up (+) or down (-) in all, no longer than {@code maxLen}, of beats in {@code allowed}
     * families. Null when nothing holds (today's way then).
     */
    public static @Nullable Recipe compose(Run run, Rank rank, Accents acc, int wantDy, int maxLen, Set<Family> allowed, boolean sideRoutes, Random r, Paints paint) {
        int n = beatsAt(rank, r);
        List<Step> steps = new ArrayList<>();
        EnumSet<Family> fams = EnumSet.noneOf(Family.class);
        int dySoFar = 0;
        @Nullable Beat last = run.last;
        boolean sign = acc.signature() != null && !run.signed && acc.signature().rank.ordinal() <= rank.ordinal()
                && allowed.contains(acc.signature().family) && r.nextInt(3) == 0;
        int signAt = sign ? r.nextInt(n) : -1;
        for (int i = 0; i < n; i++) {
            Beat b = i == signAt && acc.signature() != last ? acc.signature() : pick(run, rank, acc, allowed, last, wantDy - dySoFar, fams, steps.size(), r);
            if (b == null) break;
            double t = b == acc.signature() && i == signAt ? Math.min(1, sizeAt(rank, r) + 0.35) : sizeAt(rank, r);
            steps.add(new Step(b, t, r.nextBoolean() ? 1 : -1, r.nextLong()));
            if (b.asks()) fams.add(b.family);
            dySoFar += b.dy;
            last = b;
        }
        // two families from C (one family a line below it): swap a beat for one of another family
        if (rank.ordinal() >= Rank.C.ordinal() && fams.size() < 2 && steps.size() >= 2) {
            int k = steps.size() - 1;
            if (steps.get(k).beat() == acc.signature()) k--;
            Beat prev = k > 0 ? steps.get(k - 1).beat() : run.last;
            EnumSet<Family> others = EnumSet.copyOf(allowed);
            others.removeAll(fams);
            Beat b = others.isEmpty() ? null : pick(run, rank, acc, others, prev, wantDy, EnumSet.noneOf(Family.class), k, r);
            if (b != null && (k + 1 >= steps.size() || steps.get(k + 1).beat() != b)) steps.set(k, new Step(b, sizeAt(rank, r), r.nextBoolean() ? 1 : -1, r.nextLong()));
        }
        // a side route now and then, never on the way (from D: more often)
        if (sideRoutes && r.nextDouble() < (rank.ordinal() >= Rank.D.ordinal() ? 0.35 : 0.2)) {
            List<Beat> sides = new ArrayList<>();
            for (Beat b : Beat.values()) if (!b.onTheWay() && b.rank.ordinal() <= rank.ordinal()) sides.add(b);
            if (!sides.isEmpty()) steps.add(r.nextInt(steps.size() + 1), new Step(sides.get(r.nextInt(sides.size())), 0.5, r.nextBoolean() ? 1 : -1, r.nextLong()));
        }

        // every step proved on its own on this kind's blocks: rolled again, then something simpler, then left out
        List<Step> kept = new ArrayList<>();
        for (Step st : steps) {
            Step ok = prove(run, st, rank, paint, r, kept.isEmpty() ? run.last : kept.getLast().beat());
            if (ok != null) kept.add(ok);
        }
        boolean asks = false;
        for (Step st : kept) asks |= st.beat().asks();
        if (!asks) {
            run.note("no beat asks anything: today's way");
            return null;
        }

        // the height the beats don't make, in stairs after them; then the length, and the whole line proved once more
        long fightSeed = r.nextLong();
        while (!kept.isEmpty()) {
            Recipe rc = measure(kept, wantDy, rank, fights(kept, rank, run, new Random(fightSeed)));
            if (rc == null) {
                run.note("a line didn't lay: " + kept);
                kept.removeLast();
                continue;
            }
            if (rc.length > maxLen && kept.size() > 1) {
                // the longest that isn't the only beat that asks something goes
                int drop = -1;
                for (int i = kept.size() - 1; i >= 0 && drop < 0; i--) {
                    Step st = kept.get(i);
                    int asking = 0;
                    for (Step o : kept) if (o.beat().asks()) asking++;
                    if (!st.beat().asks() || asking > 1) drop = i;
                }
                kept.remove(drop < 0 ? kept.size() - 1 : drop);
                continue;
            }
            String why = proveWhole(rc, paint);
            if (why != null) {
                run.note("the whole line failed (" + why + "): " + kept);
                kept.removeLast();
                continue;
            }
            if (rc.fights.chase()) run.chased = true;
            if (rc.fights.perch() >= 0) run.perched = true;
            for (Step st : kept) {
                if (st.beat().onTheWay()) run.count.merge(st.beat().family, 1, Integer::sum);
                if (st.beat().onTheWay()) run.total++;
                if (st.beat() == acc.signature()) run.signed = true;
            }
            for (int i = kept.size() - 1; i >= 0; i--) if (kept.get(i).beat().onTheWay()) {
                run.last = kept.get(i).beat();
                break;
            }
            return rc;
        }
        run.note("nothing held: today's way");
        return null;
    }

    /** The next beat: the kind's lean, the height still to make, no family past 40% of the run, never the last again. */
    static @Nullable Beat pick(Run run, Rank rank, Accents acc, Set<Family> allowed, @Nullable Beat last, int remaining, Set<Family> inLine, int index, Random r) {
        List<Beat> pool = new ArrayList<>();
        List<Double> w = new ArrayList<>();
        double sum = 0;
        int cap = Math.max(2, (int) Math.floor(0.4 * (run.total + index + 1)));
        for (Beat b : Beat.values()) {
            if (!b.onTheWay() || b.rank.ordinal() > rank.ordinal() || !allowed.contains(b.family) || b == last) continue;
            if (b == acc.signature() && run.signed) continue;
            double x = acc.weight(b.family);
            if (b.dy != 0 && Integer.signum(remaining) == b.dy) x *= 2;
            else if (b.dy != 0 && Integer.signum(remaining) == -b.dy && Math.abs(remaining) >= 4) x *= 0.35;
            if (run.count.getOrDefault(b.family, 0) + 1 > cap) x *= 0.05;
            // a line's own families: from C a second one is wanted
            if (rank.ordinal() >= Rank.C.ordinal() && inLine.size() == 1 && inLine.contains(b.family)) x *= 0.5;
            // the newest beats of a rank come up more at it (what this rank is the first to have)
            if (b.rank == rank) x *= 1.4;
            if (!b.asks()) x *= 0.6;
            pool.add(b);
            w.add(x);
            sum += x;
        }
        if (pool.isEmpty()) return null;
        double roll = r.nextDouble() * sum;
        for (int i = 0; i < pool.size(); i++) {
            roll -= w.get(i);
            if (roll <= 0) return pool.get(i);
        }
        return pool.getLast();
    }

    /** What a beat that won't hold becomes: one of its family a rank lower, else the plainest beat of the family. */
    static @Nullable Beat simpler(Beat b, Rank rank, @Nullable Beat last) {
        Beat best = null;
        for (Beat o : Beat.values()) {
            if (o == b || o == last || o.family != b.family || !o.onTheWay() || o.rank.ordinal() > rank.ordinal() || o.rank.ordinal() > b.rank.ordinal()) continue;
            if (best == null || o.rank.ordinal() < best.rank.ordinal()) best = o;
        }
        return best;
    }

    /** One step that holds, or null: this one, rolled again a little smaller up to six times, then something simpler. */
    static @Nullable Step prove(Run run, Step st, Rank rank, Paints paint, Random r, @Nullable Beat last) {
        String why = proveAlone(st, rank, paint);
        if (why == null) return st;
        Step at = st;
        for (int k = 1; k <= 6; k++) {
            at = new Step(st.beat(), Math.max(0, st.t() - 0.12 * k), r.nextBoolean() ? 1 : -1, r.nextLong());
            if (proveAlone(at, rank, paint) == null) {
                run.note(st.beat() + " rolled again " + k + "x (" + why + ")");
                return at;
            }
        }
        Beat easy = simpler(st.beat(), rank, last);
        while (easy != null) {
            Step e = new Step(easy, Math.max(0, st.t() - 0.2), st.side(), r.nextLong());
            if (proveAlone(e, rank, paint) == null) {
                run.note(st.beat() + " became " + easy + " (" + why + ")");
                return e;
            }
            easy = simpler(easy, rank, last);
        }
        if (!st.beat().onTheWay()) return null;
        run.note(st.beat() + " left out (" + why + ")");
        return null;
    }

    /** Proofs already made: a beat, its size, side and seed, on a kind's paint (a paint is one kind's for a whole run). */
    private static final Map<String, String> PROVED = new ConcurrentHashMap<>();

    /** Why one step fails on its own between two pads, in this paint, or null when it holds. */
    static @Nullable String proveAlone(Step st, Rank rank, Paints paint) {
        String key = st.beat() + "/" + Math.round(st.t() * 20) + "/" + st.side() + "/" + st.seed() + "/" + Math.min(rank.ordinal(), Rank.D.ordinal()) + "/" + paint.kind();
        String done = PROVED.get(key);
        if (done != null) return done.isEmpty() ? null : done;
        BeatLine L = new BeatLine("proof", 0, 96, 0, 0, 1);
        Beats.pad(L, 3);
        String why;
        if (!Beats.build(st.beat(), L, st.t(), st.side(), rank, new Random(st.seed()))) why = "no size of it holds";
        else {
            Beats.pad(L, 3);
            L.s.done();
            List<String> f = MoveProof.failures(L.s.view(paint.paint()), L.moves, rank);
            why = f.isEmpty() ? null : f.getFirst();
        }
        if (PROVED.size() > 20_000) PROVED.clear();
        PROVED.put(key, why == null ? "" : why);
        return why;
    }

    /** Lays the steps in their own frame with the stairs that make up the height, and measures it. Null if it doesn't lay. */
    static @Nullable Recipe measure(List<Step> steps, int wantDy, Rank rank) {
        return measure(steps, wantDy, rank, Fights.NONE);
    }

    static @Nullable Recipe measure(List<Step> steps, int wantDy, Rank rank, Fights fights) {
        // what the beats really do to the floor is only known once laid: lay without stairs first
        Recipe dry = new Recipe(List.copyOf(steps), new int[steps.size()], rank);
        dry.fights = fights;
        BeatLine D = new BeatLine("measure", 0, 0, 0, 0, 1);
        if (!dry.lay(D)) return null;
        int beatDy = D.feet;
        int rest = Math.clamp(wantDy - beatDy, -10, 10);
        int[] stairs = new int[steps.size()];
        // the stairs go after the beats, spread over the gaps (no more than five a gap)
        for (int i = 0; i < steps.size() && rest != 0; i++) {
            int left = steps.size() - i, part = rest / left + (rest % left != 0 ? Integer.signum(rest) : 0);
            part = Math.clamp(part, -5, 5);
            stairs[i] = part;
            rest -= part;
        }
        Recipe rc = new Recipe(List.copyOf(steps), stairs, rank);
        rc.fights = fights;
        BeatLine L = new BeatLine("measure", 0, 0, 0, 0, 1);
        if (!rc.lay(L)) return null;
        L.s.done();
        rc.box = new int[]{L.s.x0, L.yLo - 10, L.s.z0, L.s.x1, L.yHi + 2, L.s.z1};
        rc.endX = L.x(L.a, 0);
        rc.endZ = L.z(L.a, 0);
        rc.endDx = L.dx();
        rc.endDz = L.dz();
        rc.dy = L.feet;
        rc.length = Math.max(Math.abs(rc.box[5] - rc.box[2]), Math.abs(rc.box[3] - rc.box[0])) + 1;
        return rc;
    }

    /** Why the whole line fails in its own frame, or null. */
    static @Nullable String proveWhole(Recipe rc, Paints paint) {
        BeatLine L = new BeatLine("whole", 0, 96, 0, 0, 1);
        if (!rc.lay(L)) return "didn't lay";
        L.s.done();
        List<String> f = MoveProof.failures(L.s.view(paint.paint()), L.moves, rc.rank);
        return f.isEmpty() ? null : f.getFirst();
    }

    /** A kind's blocks for the proof, and its name (what a proof made on them is kept under). */
    public record Paints(String kind, Sculpt.Paint paint) {
        public static final Paints PLAIN = new Paints("plain", Sculpt.PLAIN);
    }

    /** The families a way may have: everything on the way. */
    public static final Set<Family> WAY = EnumSet.of(Family.LOW, Family.WALL, Family.LEDGE, Family.GRIP, Family.FLOW);
    /** A crossing of a gulf: what gets you over or down into it. */
    public static final Set<Family> CROSSING = EnumSet.of(Family.WALL, Family.LEDGE, Family.GRIP, Family.FLOW);
}
