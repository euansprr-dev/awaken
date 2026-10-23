package dev.awaken.lucidium.plan;

import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.data.Vision;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds tonight's {@link DreamPlan} from the Day Journal. Pure and deterministic: the same inputs always give the
 * same plan (tested), so plans can be stored, replayed and explained.
 *
 * <p>Picks 4–8 places (today's first, older ones to fill, authored "imagined" places if the journal is thin), lays
 * them out as floating islands, joins them with a spanning tree of bridges and spiral stairs plus one or two
 * impossible doors, distorts the palette, chooses two dream signs, maybe a nightmare, a mentor and a vision.
 */
public final class DreamPlanner {
    private DreamPlanner() {}

    public static final String FIND_THE_DOOR = "find_the_door", FACE_MY_FEAR = "face_my_fear", FLY = "fly";
    public static final List<String> INTENTIONS = List.of(FIND_THE_DOOR, FACE_MY_FEAR, FLY);

    public static final int BASE_Y = 100;
    public static final int MIN_NODES = 4, MAX_NODES = 8;

    public record Input(long worldSeed, UUID player, long day, int dreamsSoFar, List<Snapshot> journal,
                        Map<Nightmares.Family, Integer> fears, String intention, Optional<Vision> vision,
                        Optional<String> path) {}

    public static long seed(long worldSeed, UUID player, long day, int dreamsSoFar) {
        long h = worldSeed * 0x9E3779B97F4A7C15L;
        h ^= player.getMostSignificantBits() + 0x632BE59BD9B4E019L + (h << 6) + (h >>> 2);
        h ^= player.getLeastSignificantBits() + 0x85157AF5L + (h << 6) + (h >>> 2);
        h ^= day * 0xC2B2AE3D27D4EB4FL + dreamsSoFar * 0x165667B19E3779F9L;
        return h ^ (h >>> 29);
    }

    /** Tonight's plan, or empty when there is nothing real to dream of yet (then the player just sleeps). */
    public static Optional<DreamPlan> plan(Input in) {
        List<Snapshot> memories = new ArrayList<>();
        for (Snapshot s : in.journal()) if (s.kind() != Snapshot.Kind.VISION) memories.add(s);
        if (memories.isEmpty()) return Optional.empty();
        long seed = seed(in.worldSeed(), in.player(), in.day(), in.dreamsSoFar());
        RandomSource r = new XoroshiroRandomSource(seed);

        // ---- choose places: today's first (newest first), then older ones, homes and bosses favoured -------------
        List<Snapshot> today = new ArrayList<>();
        List<Snapshot> older = new ArrayList<>();
        for (int i = memories.size() - 1; i >= 0; i--) {
            Snapshot s = memories.get(i);
            (s.day() >= in.day() - 1 && s.day() <= in.day() ? today : older).add(s);
        }
        older.sort(Comparator.comparingLong(s -> olderRank(s, seed)));
        int target = Mth.clamp(MIN_NODES + today.size() / 3 + r.nextInt(3), MIN_NODES, MAX_NODES);
        List<Snapshot> picks = new ArrayList<>();
        for (Snapshot s : today) if (picks.size() < target) picks.add(s);
        for (Snapshot s : older) if (picks.size() < target) picks.add(s);
        // dreams start at home when home is part of them
        for (int i = 0; i < picks.size(); i++) {
            if (picks.get(i).kind() == Snapshot.Kind.HOME) {
                picks.addFirst(picks.remove(i));
                break;
            }
        }

        List<DreamPlan.Node> nodes = new ArrayList<>();
        List<int[]> pos = new ArrayList<>();
        for (Snapshot s : picks) {
            nodes.add(new DreamPlan.Node(DreamPlan.MEMORY, s.id(), s.label(), 0, 0, 0, r.nextInt(4), false));
        }
        List<String> imaginedPool = new ArrayList<>(Imagined.PLACES);
        while (nodes.size() < MIN_NODES) {
            String name = imaginedPool.remove(r.nextInt(imaginedPool.size()));
            nodes.add(new DreamPlan.Node(DreamPlan.IMAGINED, name, Imagined.label(name), 0, 0, 0, r.nextInt(4), false));
        }
        int mainCount = nodes.size();
        // one memory may hang upside down under its island (never the first)
        if (mainCount > 2 && r.nextInt(6) == 0) {
            int k = 1 + r.nextInt(mainCount - 1);
            DreamPlan.Node n = nodes.get(k);
            nodes.set(k, new DreamPlan.Node(n.kind(), n.ref(), n.label(), 0, 0, 0, n.rot(), true));
        }

        // ---- extras ------------------------------------------------------------------------------------------------
        Optional<Nightmares.Family> nightmare = Nightmares.choose(in.fears(), FACE_MY_FEAR.equals(in.intention()), r);
        nightmare.ifPresent(f -> nodes.add(new DreamPlan.Node(DreamPlan.NIGHTMARE, f.id, "a nightmare of " + f.fear, 0, 0, 0, 0, false)));
        if (in.path().isPresent() && r.nextInt(2) == 0) {
            nodes.add(new DreamPlan.Node(DreamPlan.MENTOR, in.path().get(), "the mentor's ring", 0, 0, 0, 0, false));
        }
        in.vision().ifPresent(v -> nodes.add(new DreamPlan.Node(DreamPlan.VISION, v.snapshotId(), v.label(), 0, 0, 0, 0, false)));
        if (FIND_THE_DOOR.equals(in.intention())) {
            nodes.add(new DreamPlan.Node(DreamPlan.DOOR, "door", "the door you were looking for", 0, 0, 0, r.nextInt(4), false));
        }

        // ---- layout: a loose golden-angle spiral of islands ------------------------------------------------------
        for (int i = 0; i < nodes.size(); i++) {
            DreamPlan.Node n = nodes.get(i);
            int x = 0, y = BASE_Y, z = 0;
            if (i > 0) {
                boolean extra = i >= mainCount;
                double radius = (extra ? 46 : 30) + 8 * i + r.nextInt(8);
                double angle = i * 2.39996 + (r.nextDouble() - 0.5) * 0.8;
                y = BASE_Y + r.nextInt(29) - 12;
                if (DreamPlan.NIGHTMARE.equals(n.kind())) y = BASE_Y - 8 - r.nextInt(6);
                if (DreamPlan.VISION.equals(n.kind())) y = BASE_Y + 18 + r.nextInt(6);
                for (int attempt = 0; attempt < 10; attempt++) {
                    x = (int) Math.round(Math.cos(angle) * radius);
                    z = (int) Math.round(Math.sin(angle) * radius);
                    if (clear(pos, x, z, 30)) break;
                    radius += 10;
                    angle += 0.35;
                }
                x = Mth.clamp(x, -215, 215);
                z = Mth.clamp(z, -215, 215);
            }
            pos.add(new int[]{x, y, z});
            nodes.set(i, new DreamPlan.Node(n.kind(), n.ref(), n.label(), x, y, z, n.rot(), n.flip()));
        }

        // ---- links: minimum spanning tree (bridges or spiral stairs) + impossible doors ---------------------------
        List<DreamPlan.Edge> edges = new ArrayList<>();
        boolean[] in_ = new boolean[nodes.size()];
        in_[0] = true;
        boolean[][] adjacent = new boolean[nodes.size()][nodes.size()];
        for (int added = 1; added < nodes.size(); added++) {
            int bestA = -1, bestB = -1;
            double best = Double.MAX_VALUE;
            for (int a = 0; a < nodes.size(); a++) {
                if (!in_[a]) continue;
                for (int b = 0; b < nodes.size(); b++) {
                    if (in_[b]) continue;
                    double d = dist(pos.get(a), pos.get(b));
                    if (d < best) {
                        best = d;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            in_[bestB] = true;
            adjacent[bestA][bestB] = adjacent[bestB][bestA] = true;
            int[] pa = pos.get(bestA), pb = pos.get(bestB);
            double span = Math.max(1, best - 18);
            String kind = Math.abs(pa[1] - pb[1]) > span * 0.5 ? DreamPlan.STAIR : DreamPlan.BRIDGE;
            edges.add(new DreamPlan.Edge(bestA, bestB, kind, r.nextInt(4)));
        }
        int doors = mainCount >= 7 ? 2 : (mainCount >= 4 ? 1 : 0);
        for (int k = 0, tries = 0; k < doors && tries < 40; tries++) {
            int a = r.nextInt(mainCount), b = r.nextInt(mainCount);
            if (a == b || adjacent[a][b]) continue;
            adjacent[a][b] = adjacent[b][a] = true;
            edges.add(new DreamPlan.Edge(a, b, DreamPlan.PORTAL, r.nextInt(4)));
            k++;
        }

        // ---- signs, palette, length, spares ------------------------------------------------------------------------
        DreamSign personal = DreamSign.personal(in.player());
        List<DreamSign> others = new ArrayList<>(List.of(DreamSign.values()));
        others.remove(personal);
        DreamSign second = others.get(r.nextInt(others.size()));
        List<String> signs = List.of(personal.name(), second.name());
        DreamPalette.Mood mood = DreamPalette.MOODS.get(r.nextInt(DreamPalette.MOODS.size()));
        int hue = 1 + r.nextInt(15);
        int duration = Mth.clamp(3600 + 600 * (mainCount - MIN_NODES) + r.nextInt(1200), 3600, 9600);
        List<String> spares = new ArrayList<>();
        for (Snapshot s : older) {
            if (spares.size() >= 2) break;
            if (!picks.contains(s)) spares.add(s.id());
        }
        while (spares.size() < 2 && !imaginedPool.isEmpty()) {
            spares.add("imagined:" + imaginedPool.remove(r.nextInt(imaginedPool.size())));
        }
        return Optional.of(new DreamPlan(seed, in.day(), mood.name(), hue, List.copyOf(nodes), List.copyOf(edges), signs,
                nightmare.map(f -> f.id), in.vision().map(Vision::id), in.intention(), duration, List.copyOf(spares)));
    }

    private static long olderRank(Snapshot s, long seed) {
        long bias = switch (s.kind()) {
            case HOME -> 0;
            case BOSS -> 1L << 40;
            default -> 2L << 40;
        };
        long h = (s.id().hashCode() * 0x9E3779B97F4A7C15L) ^ seed;
        return bias + ((h ^ (h >>> 31)) & 0xFFFFFFFFFFL);
    }

    private static boolean clear(List<int[]> placed, int x, int z, int min) {
        for (int[] p : placed) {
            int dx = p[0] - x, dz = p[2] - z;
            if (dx * dx + dz * dz < min * min) return false;
        }
        return true;
    }

    private static double dist(int[] a, int[] b) {
        double dx = a[0] - b[0], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dz * dz);
    }
}
