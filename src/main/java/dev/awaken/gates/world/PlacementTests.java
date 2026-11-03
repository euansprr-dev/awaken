package dev.awaken.gates.world;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.progression.Rank;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Nothing hangs in the air (SPEC §38.16; Euan 2026-11-03: "a lot of floating things in gates instead of placement all
 * being thought out"). Every room kind and every gate world is carved whole into memory and each placed thing is held to
 * what holds it up: plants, debris and props stand on a solid face; chains, lanterns, vines and roots hang from something
 * solid (or, a strand, grow up from it); wall pieces are against a wall; falls of light run from something to something;
 * and no solid cluster floats unconnected to the ground or a wall. Failures are traced to the code that placed them.
 */
public final class PlacementTests {
    private PlacementTests() {}

    static final byte IGNORE = Holds.NONE, SOLID = Holds.SOLID, CHAIN = Holds.CHAIN, STRING = Holds.STRING;
    static final String[] RULE = Holds.NAMES;

    /** A cluster this big, or touching the edge of what was carved, is the ground (the rock, the shell, an island). */
    static final int GROUND = 2048;

    /** What failed on a floor: per rule, the cells (floor-local index) and the floating clusters' cells. */
    static final class Result {
        final String what;
        final int[] count = new int[RULE.length];
        final List<int[]> bad = new ArrayList<>();
        /** Floating clusters: their cells as {x, y, z}. */
        final List<List<int[]>> floating = new ArrayList<>();
        int checked;
        /** Lumps the generator floats on purpose (SPEC §38.16): blocks and lumps, counted apart, never a failure. */
        int designed, designedLumps;

        Result(String what) {
            this.what = what;
        }

        int total() {
            int n = 0;
            for (int c : count) n += c;
            return n;
        }
    }

    /**
     * The air someone on the floor can see: everything not solid that joins, face to face, a spot they can walk to (a
     * fragment of shell out in the dark behind a wall is never seen, so it's never counted).
     */
    static BitSet sight(WorldCheck w, Holds.Need[] needs) {
        FloorLayout f = w.f;
        int n = w.cellCount(), side = WorldCheck.SIDE, h = w.h;
        BitSet open = new BitSet(n);
        Room arrival = f.room(f.start);
        Room.Feature e = arrival == null ? null : arrival.feature(Room.FeatureKind.ENTRANCE);
        if (e == null) {
            open.set(0, n);
            return open;
        }
        GateWorldBellTests.walkAll(w, f, e.x(), f.baseY + arrival.dy + f.ground(e.x(), e.z()) + e.y(), e.z());
        int[] queue = new int[1 << 16];
        int tail = 0, heads = 0, dark = 0;
        for (int c = 0; c < n; c++) {
            int x = c / (side * h), z = (c / h) % side, y = c % h + w.yLo;
            if (w.reached(x, y, z)) {
                open.set(c);
                queue = push(queue, tail++, c);
                // over your head: the carve's air, or the dark outside it (a sky, a void the world is open to)
                int up = w.idx(x, y + 3, z);
                if (w.carved != null && up >= 0 && w.code(up) == 0) {
                    heads++;
                    if (!w.carved.get(up)) dark++;
                }
            }
        }
        // a world open to the sky or the void shows what's out there; a closed one shows only its carved air
        BitSet inside = w.carved != null && dark < heads / 5 ? w.carved : null;
        for (int head = 0; head < tail; head++) {
            int q = queue[head];
            int x = q / (side * h), z = (q / h) % side, yy = q % h;
            for (int d = 0; d < 6; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = yy + (d == 2 ? 1 : d == 3 ? -1 : 0), nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= side || nz >= side || ny < 0 || ny >= h) continue;
                int m = (nx * side + nz) * h + ny;
                if (open.get(m) || inside != null && !inside.get(m)) continue;
                byte k = needs[w.code(m)].rule();
                if (k == SOLID || k == CHAIN) continue;
                open.set(m);
                queue = push(queue, tail++, m);
            }
        }
        return open;
    }

    /** Holds every block of a carved floor that can be seen to its rule. */
    static Result check(WorldCheck w, String what) {
        Result r = new Result(what);
        List<BlockState> pal = w.palette();
        Holds.Need[] needs = new Holds.Need[pal.size()];
        for (int i = 0; i < needs.length; i++) needs[i] = Holds.need(pal.get(i));
        int side = WorldCheck.SIDE, h = w.h, n = w.cellCount();
        BitSet open = sight(w, needs);
        BitSet structural = new BitSet(n), strings = new BitSet(n);
        Holds.Grid grid = w::at;
        for (int c = 0; c < n; c++) {
            short code = w.code(c);
            if (code == 0) continue;
            Holds.Need nd = needs[code];
            if (nd.rule() == IGNORE) continue;
            // a lamp hung in a chain carries what hangs under it (a bell's clapper under its lamp)
            if (nd.rule() == SOLID || nd.rule() == CHAIN || nd.rule() == Holds.HANG && pal.get(code).getBlock() instanceof LanternBlock) structural.set(c);
            if (nd.rule() == SOLID || !open.get(c)) continue;
            int x = c / (side * h), z = (c / h) % side, y = c % h + w.yLo;
            r.checked++;
            if (nd.rule() == STRING) {
                strings.set(c);
                continue;
            }
            if (!Holds.held(grid, pal.get(code), x, y, z)) {
                if (w.afloat.get(c)) {
                    // floats on purpose (the Sky's updrafts): counted apart
                    r.designed++;
                    r.designedLumps++;
                    continue;
                }
                r.count[nd.rule()]++;
                r.bad.add(new int[]{x, y, z, nd.rule()});
            }
        }
        // a string (lights, flags) is held when both its ends are against something solid
        for (int c = strings.nextSetBit(0); c >= 0; c = strings.nextSetBit(c + 1)) {
            List<int[]> run = new ArrayList<>();
            java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
            q.add(c);
            strings.clear(c);
            while (!q.isEmpty()) {
                int k = q.poll();
                int x = k / (side * h), z = (k / h) % side, yy = k % h;
                run.add(new int[]{x, yy + w.yLo, z});
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    int nx = x + dx, ny = yy + dy, nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= side || nz >= side || ny < 0 || ny >= h) continue;
                    int m = (nx * side + nz) * h + ny;
                    if (strings.get(m)) {
                        strings.clear(m);
                        q.add(m);
                    }
                }
            }
            int[] lo = run.get(0), hi = run.get(0);
            boolean alongX = run.stream().mapToInt(v -> v[0]).max().getAsInt() - run.stream().mapToInt(v -> v[0]).min().getAsInt()
                    >= run.stream().mapToInt(v -> v[2]).max().getAsInt() - run.stream().mapToInt(v -> v[2]).min().getAsInt();
            int ax = alongX ? 0 : 2;
            for (int[] v : run) {
                if (v[ax] < lo[ax]) lo = v;
                if (v[ax] > hi[ax]) hi = v;
            }
            // an end is tied when a block of it (the last block along, or the elbow beside it on a slant) meets the rock
            boolean tiedLo = false, tiedHi = false;
            for (int[] v : run) {
                if (v[ax] <= lo[ax] + 1 && tied(w, needs, v)) tiedLo = true;
                if (v[ax] >= hi[ax] - 1 && tied(w, needs, v)) tiedHi = true;
            }
            if (!(tiedLo && tiedHi) && !(run.size() == 1 && tied(w, needs, lo))) {
                for (int[] v : run) {
                    r.count[STRING]++;
                    r.bad.add(new int[]{v[0], v[1], v[2], STRING});
                }
            }
        }
        // clusters: everything solid, joined face to face (chains carry what hangs on them)
        BitSet seen = new BitSet(n);
        int[] queue = new int[1 << 16];
        for (int c = structural.nextSetBit(0); c >= 0; c = structural.nextSetBit(c + 1)) {
            if (seen.get(c)) continue;
            int head = 0, tail = 0, size = 0;
            boolean edge = false, shown = false, designed = false;
            List<int[]> cells = new ArrayList<>();
            seen.set(c);
            queue = push(queue, tail++, c);
            while (head < tail) {
                int q = queue[head++];
                size++;
                int x = q / (side * h), z = (q / h) % side, yy = q % h;
                if (x == 0 || z == 0 || x == side - 1 || z == side - 1 || yy == 0 || yy == h - 1) edge = true;
                if (w.afloat.get(q)) designed = true;
                if (cells.size() < GROUND) cells.add(new int[]{x, yy + w.yLo, z});
                if (yy > 0 && w.at(x, yy + w.yLo - 1, z).getBlock() == Blocks.WATER) edge = true;
                // standing on something placed that's held itself and flat on top (a vault's lid on its cache)
                if (yy > 0 && !structural.get(q - 1)) {
                    BlockState below = w.at(x, yy + w.yLo - 1, z);
                    if (!below.isAir() && Holds.sturdy(below, Direction.UP) && Holds.held(grid, below, x, yy + w.yLo - 1, z)) edge = true;
                }
                for (int d = 0; d < 6; d++) {
                    int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = yy + (d == 2 ? 1 : d == 3 ? -1 : 0), nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
                    if (nx < 0 || nz < 0 || nx >= side || nz >= side || ny < 0 || ny >= h) continue;
                    int m = (nx * side + nz) * h + ny;
                    if (open.get(m)) shown = true;
                    // water beside it: it holds the water in
                    if (!w.at(nx, ny + w.yLo, nz).getFluidState().isEmpty()) edge = true;
                    if (!structural.get(m) || seen.get(m)) continue;
                    seen.set(m);
                    queue = push(queue, tail++, m);
                }
                // the queue is consumed from the front: compact it when it's mostly spent
                if (head > (1 << 20) && head > tail / 2) {
                    System.arraycopy(queue, head, queue, 0, tail - head);
                    tail -= head;
                    head = 0;
                }
            }
            if (!edge && size < GROUND && shown && designed) {
                r.designed += size;
                r.designedLumps++;
            } else if (!edge && size < GROUND && shown) {
                r.floating.add(cells);
                r.count[SOLID] += size;
            }
        }
        return r;
    }

    /** A string's end tied to something: a solid block beside, over or under it. */
    private static boolean tied(WorldCheck w, Holds.Need[] needs, int[] v) {
        for (Direction d : Direction.values()) if (Holds.rule(w.at(v[0] + d.getStepX(), v[1] + d.getStepY(), v[2] + d.getStepZ())) == SOLID) return true;
        return false;
    }

    private static int[] push(int[] q, int at, int v) {
        if (at >= q.length) q = java.util.Arrays.copyOf(q, q.length * 2);
        q[at] = v;
        return q;
    }

    // ---- who placed it ---------------------------------------------------------------------------------------------------

    private static final Set<String> PLUMBING = Set.of("Sculpt", "Sculpt$Buf", "Sculpt$Op", "WorldPlan$SculptModel", "PlacementTests",
            "WorldCheck", "GatePainter$Sink", "GatePainter$Band");

    /** The code that asked for an op or a block: the first frames outside the chisel and the tests, "Class.method<Class.method". */
    static String caller() {
        return StackWalker.getInstance().walk(st -> {
            StringBuilder out = new StringBuilder();
            String first = null;
            for (var it = st.iterator(); it.hasNext(); ) {
                var f = it.next();
                String cls = f.getClassName();
                cls = cls.substring(cls.lastIndexOf('.') + 1);
                int lam = cls.indexOf("$$Lambda");
                if (lam >= 0) cls = cls.substring(0, lam);
                if (PLUMBING.contains(cls)) continue;
                String m = f.getMethodName();
                if (m.startsWith("lambda$")) {
                    String[] parts = m.split("\\$");
                    m = parts.length > 1 ? parts[1] : m;
                }
                if (cls.equals("GatePainter") && (m.equals("paint") || m.equals("paintAny") || m.equals("paintFloor"))) continue;
                if (cls.equals("GatePainter$Ctx") && (m.equals("set") || m.equals("put"))) continue;
                String here = cls + "." + m;
                if (first == null) {
                    first = cls;
                    out.append(here);
                } else if (!cls.equals(first)) {
                    out.append('<').append(here);
                    break;
                }
            }
            return out.toString();
        });
    }

    /** Re-carves the chunks holding a floor's failures (with ops traced) and names the placer of each. */
    static Map<String, int[]> blame(GatePlan plan, FloorLayout traced, Result r, WorldCheck w, Map<String, List<String>> samples) {
        Map<Long, Integer> want = new HashMap<>();
        for (int[] b : r.bad) want.put(key(b[0], b[1], b[2]), (int) b[3]);
        for (List<int[]> cl : r.floating) for (int[] c : cl) want.put(key(c[0], c[1], c[2]), (int) SOLID);
        Map<Long, String> who = new HashMap<>();
        Set<Long> chunks = new java.util.HashSet<>();
        for (long k : want.keySet()) chunks.add(((long) (kx(k) >> 4) << 32) | ((kz(k) >> 4) & 0xFFFFFFFFL));
        for (long ch : chunks) {
            int gx = (int) (ch >> 32), gz = (int) ch;
            int bx = gx * 16, bz = gz * 16;
            GatePainter.Sink sink = (lx, y, lz, s) -> {
                long k = key(bx + lx, y, bz + lz);
                if (!want.containsKey(k)) return;
                String src = Sculpt.source();
                who.put(k, src != null && (onStack("Sculpt$Buf") || onStack("GatePainter$Band")) ? src : caller());
            };
            if (traced.model != null) traced.model.carve(plan, traced, traced.originSlotX + gx, traced.originSlotZ + gz, sink);
            else GatePainter.paintFloor(plan, traced, traced.originSlotX + gx, traced.originSlotZ + gz, sink);
        }
        Map<String, int[]> out = new TreeMap<>();
        for (int[] b : r.bad) {
            String k = who.getOrDefault(key(b[0], b[1], b[2]), "?");
            out.computeIfAbsent(k, x -> new int[RULE.length + 1])[b[3]]++;
            List<String> l = samples.computeIfAbsent(k, x -> new ArrayList<>());
            if (l.size() < 4) l.add(RULE[b[3]] + " " + r.what + " @" + b[0] + "," + b[1] + "," + b[2] + " " + w.at(b[0], b[1], b[2]) + " under "
                    + w.at(b[0], b[1] - 1, b[2]) + " over " + w.at(b[0], b[1] + 1, b[2]) + (l.size() < 2 ? "\n" + section(w, b[0], b[1], b[2]) : ""));
        }
        for (List<int[]> cl : r.floating) {
            int[] c = cl.get(0);
            String k = who.getOrDefault(key(c[0], c[1], c[2]), "?");
            int[] row = out.computeIfAbsent(k, x -> new int[RULE.length + 1]);
            row[SOLID] += cl.size();
            row[RULE.length]++;
            List<String> l = samples.computeIfAbsent(k, x -> new ArrayList<>());
            if (l.size() < 4) l.add("cluster of " + cl.size() + " " + r.what + " @" + c[0] + "," + c[1] + "," + c[2] + " " + w.at(c[0], c[1], c[2])
                    + (l.size() < 2 ? "\n" + section(w, c[0], c[1], c[2]) : ""));
        }
        return out;
    }

    /**
     * An x-y section through (x, y, z), 17 wide and 12 tall, for the census log: # solid, letters for things, . the carve's
     * air, , the dark outside it, ~ water.
     */
    static String section(WorldCheck w, int x, int y, int z) {
        StringBuilder b = new StringBuilder();
        Map<Block, Character> legend = new LinkedHashMap<>();
        for (int yy = y + 6; yy >= y - 5; yy--) {
            b.append("      ").append(String.format("%4d ", yy - w.f.baseY));
            for (int xx = x - 8; xx <= x + 8; xx++) {
                BlockState s = w.at(xx, yy, z);
                char ch;
                int ci = w.idx(xx, yy, z);
                if (s.isAir()) ch = w.carved != null && ci >= 0 && !w.carved.get(ci) ? ',' : '.';
                else if (s.getBlock() == Blocks.WATER) ch = '~';
                else if (Holds.rule(s) == SOLID && s.isSolidRender()) ch = '#';
                else ch = legend.computeIfAbsent(s.getBlock(), k -> (char) ('a' + legend.size() % 26));
                if (xx == x && yy == y) ch = Character.toUpperCase(ch == '.' ? '*' : ch);
                b.append(ch);
            }
            b.append('\n');
        }
        legend.forEach((k, v) -> b.append("      ").append(v).append(' ').append(BuiltInRegistries.BLOCK.getKey(k)).append('\n'));
        return b.toString();
    }

    private static boolean onStack(String cls) {
        return StackWalker.getInstance().walk(st -> st.anyMatch(f -> f.getClassName().endsWith("." + cls)));
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    private static int kx(long k) {
        return (int) (k >> 40) & 0xFFFFF;
    }

    private static int kz(long k) {
        return (int) k & 0xFFFFF;
    }

    // ---- what gets checked ----------------------------------------------------------------------------------------------

    /** The rooms floors that show every room kind and set piece of every theme, the ritual's hall and the trial's. */
    static List<GatePlan> roomPlans(int version) {
        List<GatePlan> out = new ArrayList<>();
        for (Theme t : Theme.values()) {
            Set<SetPiece> want = EnumSet.noneOf(SetPiece.class);
            for (SetPiece p : SetPiece.values()) if (p.theme == t) want.add(p);
            Set<Room.Type> types = EnumSet.noneOf(Room.Type.class);
            for (int i = 0; i < 40 && (!want.isEmpty() || out.stream().noneMatch(p -> p.theme() == t)); i++) {
                Layout lay = Layout.values()[i % Layout.values().length];
                Twist tw = i % 7 == 3 ? Twist.DOUBLE_GATE : Twist.NONE;
                GatePlan p = new GatePlan(0x91ACEL + t.ordinal() * 7919L + i * 104729L, version, Rank.of(i % 5), t, lay, List.of(), tw,
                        2 + i % 4, t.bossId(), Theme.values()[(t.ordinal() + 3) % Theme.values().length], 0);
                GateLayout l = FloorBuilder.build(p);
                boolean adds = false;
                for (FloorLayout f : floors(l)) {
                    for (Room r : f.rooms) {
                        if (r.piece != null && want.remove(r.piece)) adds = true;
                        if (types.add(r.type)) adds = true;
                    }
                }
                if (adds) out.add(p);
            }
        }
        out.add(new GatePlan(0x717A1L, version, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(), Twist.NONE, 1,
                GatePlan.RITUAL_BOSS, Theme.BELL_CRYPT, 0));
        out.add(new GatePlan(0x1EA9L, version, Rank.C, Theme.CLOCKWORK_FOUNDRY, Layout.LABYRINTH, List.of(), Twist.NONE, 1,
                GatePlan.LEAPER_TRIAL, Theme.CLOCKWORK_FOUNDRY, 0));
        return out;
    }

    /**
     * Tours (SPEC 38.16): where to stand on a world floor to see what hung in the air there at version 8, the same view at
     * any version, so before and after compare: the k-th biggest thing not held up (a floating lump, a string tied to
     * nothing, litter on half steps counting half), seen from 6 to 13 blocks off over ground you walk to, nothing in the
     * way. Floor-local x, absolute y, z, yaw and pitch like {@link Interiors#view}; null if nothing failed in sight.
     */
    static double[] floatView(GatePlan p, int area, int floor, int k) {
        GatePlan was = new GatePlan(p.seed(), 8, p.rank(), p.theme(), p.layout(), p.modifiers(), p.twist(), p.floors(), p.boss(),
                p.annexTheme(), p.annexRule(), p.worlds());
        FloorLayout f = FloorBuilder.build(was).floor(area, floor);
        if (f == null || !f.isWorld()) return null;
        WorldCheck w = new WorldCheck(was, f);
        Result r = check(w, "tour");
        // what failed, weighed by how much it shows: lumps by their blocks, strings twice, the rest in 4-block cells
        List<double[]> things = new ArrayList<>();
        for (List<int[]> cl : r.floating) {
            double x = 0, y = 0, z = 0;
            for (int[] c : cl) {
                x += c[0];
                y += c[1];
                z += c[2];
            }
            things.add(new double[]{x / cl.size(), y / cl.size(), z / cl.size(), cl.size()});
        }
        Map<Long, double[]> cells = new HashMap<>();
        for (int[] b : r.bad) {
            double[] c = cells.computeIfAbsent(((long) (b[0] >> 2) << 40) | ((long) ((b[1] >> 2) + 4096) << 20) | (b[2] >> 2), q -> new double[5]);
            c[0] += b[0];
            c[1] += b[1];
            c[2] += b[2];
            c[3] += b[3] == STRING || b[3] == Holds.STRAND ? 2 : b[3] == Holds.STAND ? 0.5 : 1;
            c[4]++;
        }
        for (double[] c : cells.values()) things.add(new double[]{c[0] / c[4], c[1] / c[4], c[2] / c[4], c[3]});
        things.sort((a, b) -> Double.compare(b[3], a[3]));
        List<double[]> seen = new ArrayList<>();
        for (int i = 0; i < things.size() && i < 80; i++) {
            double[] t = things.get(i);
            boolean near = false;
            for (double[] s : seen) near |= Math.hypot(s[0] - t[0], s[2] - t[2]) < 24;
            if (near) continue;
            double[] v = lookAt(w, t[0] + 0.5, t[1] + 0.5, t[2] + 0.5);
            if (v == null) continue;
            if (seen.size() == k) return v;
            seen.add(t);
        }
        return null;
    }

    /** A spot 6 to 13 blocks off (x, y, z) with room for a body, over ground you walk to and a clear line to it. */
    private static double[] lookAt(WorldCheck w, double x, double y, double z) {
        for (int d : new int[]{9, 7, 11, 13, 6})
            for (int dy : new int[]{-2, -4, 0, -6, 2})
                for (int a = 0; a < 16; a++) {
                    double ang = a * Math.PI / 8;
                    int sx = (int) Math.floor(x + d * Math.cos(ang)), sz = (int) Math.floor(z + d * Math.sin(ang)), sy = (int) Math.floor(y) + dy;
                    if (!w.pass(sx, sy, sz) || !w.pass(sx, sy + 1, sz)) continue;
                    boolean over = false;
                    for (int yy = sy; yy >= sy - 6 && !over; yy--) over = w.reached(sx, yy, sz);
                    if (!over) continue;
                    double ex = sx + 0.5, ey = sy + 1.62, ez = sz + 0.5, dx = x - ex, dyy = y - ey, dz = z - ez;
                    double len = Math.sqrt(dx * dx + dyy * dyy + dz * dz);
                    boolean clear = true;
                    for (double t = 0.5; t < len - 1.5 && clear; t += 0.25)
                        clear = w.pass((int) Math.floor(ex + dx * t / len), (int) Math.floor(ey + dyy * t / len), (int) Math.floor(ez + dz * t / len));
                    if (!clear) continue;
                    return new double[]{ex, sy, ez, Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dyy, Math.hypot(dx, dz)))};
                }
        return null;
    }

    static List<FloorLayout> floors(GateLayout l) {
        List<FloorLayout> out = new ArrayList<>(l.main);
        out.addAll(l.annex);
        return out;
    }

    /** Every floor of a plan held to the rules; with {@code blame}, failures traced to their placer into {@code by}. */
    static List<Result> checkPlan(GatePlan p, String label, boolean worldsOnly, Map<String, int[]> by, Map<String, List<String>> samples) {
        GateLayout l = FloorBuilder.build(p);
        List<Result> out = new ArrayList<>();
        GateLayout traced = null;
        List<FloorLayout> fs = floors(l);
        for (int i = 0; i < fs.size(); i++) {
            FloorLayout f = fs.get(i);
            if (worldsOnly && !f.isWorld()) continue;
            WorldCheck w = new WorldCheck(p, f);
            String col = System.getenv("AWAKEN_PLACEMENT_COLUMN");
            if (col != null) for (String one : col.split(";")) {
                // (debugging a placer: columns of every floor, top to bottom)
                String[] xz = one.split(",");
                StringBuilder sb = new StringBuilder("COLUMN " + label + " floor " + f.floor + " at " + one + ":");
                for (int y = w.yLo + w.h - 1; y >= w.yLo; y--) {
                    BlockState st = w.at(Integer.parseInt(xz[0]), y, Integer.parseInt(xz[1]));
                    if (!st.isAir()) sb.append(' ').append(y).append('=').append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
                }
                Awaken.LOG.info(sb.toString());
            }
            Result r = check(w, label + " floor " + f.floor + (f.area > 0 ? " annex" : "") + (f.isWorld() ? " " + f.world : " rooms " + f.theme));
            out.add(r);
            if (r.total() == 0 || by == null) continue;
            if (traced == null) {
                Sculpt.TRACE = true;
                try {
                    traced = FloorBuilder.build(p);
                } finally {
                    Sculpt.TRACE = false;
                }
            }
            Sculpt.TRACE = true;
            try {
                blame(p, floors(traced).get(i), r, w, samples).forEach((k, v) -> {
                    int[] row = by.computeIfAbsent(k, x -> new int[RULE.length + 1]);
                    for (int j = 0; j < v.length; j++) row[j] += v[j];
                });
            } finally {
                Sculpt.TRACE = false;
            }
        }
        return out;
    }

    static String row(int[] c) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < RULE.length; i++) if (c[i] > 0) b.append(' ').append(RULE[i]).append('=').append(c[i]);
        if (c.length > RULE.length && c[RULE.length] > 0) b.append(" clusters=").append(c[RULE.length]);
        return b.toString();
    }

    public static void register() {
        AwakenTests.register("gates_placement", 12000, h -> {
            long t0 = System.nanoTime();
            Sculpt.CENSUS = true;
            Map<String, int[]> by = new TreeMap<>();
            Map<String, List<String>> placerSamples = new TreeMap<>();
            Map<String, Integer> perFloor = new LinkedHashMap<>();
            StringBuilder samples = new StringBuilder();
            int floors = 0, checked = 0, bad = 0, designed = 0, lumps = 0;
            Map<String, Integer> designedBy = new TreeMap<>();
            List<Object[]> plans = new ArrayList<>();
            // (a census of an older version, for the before and after: AWAKEN_PLACEMENT_VERSION; one world: AWAKEN_PLACEMENT_ONLY)
            String only = System.getenv("AWAKEN_PLACEMENT_ONLY"), ver = System.getenv("AWAKEN_PLACEMENT_VERSION");
            int version = ver == null ? GatePlan.CONTENT_VERSION : Integer.parseInt(ver);
            for (String id : GateDepthTests.allWorlds()) {
                if (dev.awaken.gates.worlds.GateWorlds.get(id) == null || only != null && !id.contains(only)) continue;
                plans.add(new Object[]{GateDepthTests.measured(id, version), id, true});
            }
            if (only == null) for (GatePlan p : roomPlans(version)) plans.add(new Object[]{p, "rooms " + p.theme() + " " + Long.toHexString(p.seed()), false});
            for (Object[] o : plans) {
                for (Result r : checkPlan((GatePlan) o[0], (String) o[1], (Boolean) o[2], by, placerSamples)) {
                    floors++;
                    checked += r.checked;
                    designed += r.designed;
                    lumps += r.designedLumps;
                    if (r.designed > 0) designedBy.merge(r.what.split(" floor ")[0], r.designed, Integer::sum);
                    int n = r.total();
                    bad += n;
                    if (n == 0) continue;
                    int[] c = r.count.clone();
                    int[] withClusters = java.util.Arrays.copyOf(c, RULE.length + 1);
                    withClusters[RULE.length] = r.floating.size();
                    perFloor.put(r.what + row(withClusters), n);
                    for (int k = 0, shown = 0; k < r.bad.size() && shown < 3; k++, shown++) {
                        int[] b = r.bad.get(k);
                        samples.append("  ").append(r.what).append(' ').append(RULE[b[3]]).append(" at ").append(b[0]).append(' ').append(b[1])
                                .append(' ').append(b[2]).append('\n');
                    }
                    for (int k = 0; k < Math.min(2, r.floating.size()); k++) {
                        int[] b = r.floating.get(k).get(0);
                        samples.append("  ").append(r.what).append(" cluster of ").append(r.floating.get(k).size()).append(" at ").append(b[0])
                                .append(' ').append(b[1]).append(' ').append(b[2]).append('\n');
                    }
                }
            }
            StringBuilder log = new StringBuilder();
            log.append("PLACEMENT version ").append(version).append(": ").append(floors).append(" floors, ").append(checked).append(" placed things checked, ").append(bad)
                    .append(" not held up, in ").append((System.nanoTime() - t0) / 1_000_000).append(" ms\n");
            log.append("PLACEMENT designed floats (SPEC 38.16, not failures): ").append(designed).append(" blocks in ").append(lumps).append(" lumps\n");
            designedBy.forEach((k, v) -> log.append("DESIGNED ").append(k).append(' ').append(v).append('\n'));
            log.append("PLACEMENT by placer (rule=blocks; cluster blocks and clusters):\n");
            by.entrySet().stream().sorted((a, b) -> Integer.compare(sum(b.getValue()), sum(a.getValue())))
                    .forEach(e -> {
                        log.append("PLACER ").append(e.getKey()).append(row(e.getValue())).append('\n');
                        for (String sm : placerSamples.getOrDefault(e.getKey(), List.of())) log.append("    ").append(sm).append('\n');
                    });
            log.append("PLACEMENT by floor:\n");
            perFloor.forEach((k, v) -> log.append("FLOOR ").append(k).append('\n'));
            log.append("PLACEMENT samples:\n").append(samples);
            Awaken.LOG.info("[gates] placement census:\n{}", log);
            Sculpt.CENSUS = false;
            h.assertTrue(bad == 0, bad + " placed blocks not held up (see the placement census in the log)");
            h.succeed();
        });
    }

    private static int sum(int[] v) {
        int n = 0;
        for (int i = 0; i < RULE.length; i++) n += v[i];
        return n;
    }
}
