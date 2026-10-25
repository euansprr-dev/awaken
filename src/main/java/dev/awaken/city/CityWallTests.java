package dev.awaken.city;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;

import static dev.awaken.city.CityPlan.*;

/**
 * The Walls step's GameTests, one per mechanism (the city's silent breakages): the climbing grid on the wall's faces,
 * the wall walk all the way round with a stair up in every tower, and a clear way through every gate.
 */
final class CityWallTests {
    private CityWallTests() {}

    static final long SEED = 20261024L;
    static final int RMIN = 218, RMAX = 258, Y0 = 0, YN = 52;

    /** The ring band of the painted city, rows 0..51: every column within RMIN..RMAX of the centre. */
    static final class Band {
        final BlockState[] cells;
        final int size = RMAX * 2 + 2;

        Band(CityLayout l) {
            cells = new BlockState[size * size * YN];
            CityBuffer buf = new CityBuffer(16, 16);
            int ccx = l.cx >> 4, ccz = l.cz >> 4;
            for (int cx = (l.cx - RMAX) >> 4; cx <= (l.cx + RMAX) >> 4; cx++)
                for (int cz = (l.cz - RMAX) >> 4; cz <= (l.cz + RMAX) >> 4; cz++) {
                    int px = (cx << 4) - l.cx, pz = (cz << 4) - l.cz;
                    if (!touches(px, pz)) continue;
                    CityPainter.paintChunk(l, cx, cz, SEED, buf);
                    for (int lx = 0; lx < 16; lx++)
                        for (int lz = 0; lz < 16; lz++) {
                            int x = px + lx, z = pz + lz;
                            if (!in(x, z)) continue;
                            for (int y = Y0; y < YN; y++) cells[index(x, y, z)] = buf.get(x, y, z);
                        }
                }
        }

        static boolean touches(int px, int pz) {
            double nx = Math.max(Math.max(px - 0, 0 - (px + 15)), 0), nz = Math.max(Math.max(pz, -(pz + 15)), 0);
            double fx = Math.max(Math.abs(px), Math.abs(px + 15)), fz = Math.max(Math.abs(pz), Math.abs(pz + 15));
            return Math.hypot(nx, nz) <= RMAX && Math.hypot(fx, fz) >= RMIN;
        }

        boolean in(int x, int z) {
            double r = Math.hypot(x + 0.5, z + 0.5);
            return r >= RMIN && r <= RMAX;
        }

        int index(int x, int y, int z) {
            return ((x + RMAX) * size + (z + RMAX)) * YN + y;
        }

        BlockState get(int x, int y, int z) {
            if (y < Y0 || y >= YN || Math.abs(x) > RMAX || Math.abs(z) > RMAX || !in(x, z)) return null;
            return cells[index(x, y, z)];
        }

        /** A body fits here: nothing with collision in this cell. */
        boolean passable(int x, int y, int z) {
            BlockState s = get(x, y, z);
            if (s == null) return true; // left alone by the city: open air over the ground
            if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) return true; // doors open
            return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        }

        /** Something to stand on: collision reaching the cell's top half. */
        boolean floor(int x, int y, int z) {
            BlockState s = get(x, y, z);
            if (s == null) return false;
            var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) > 0.49;
        }

        /** A stair or slab to walk up (no jump, so no headroom over the start). */
        boolean stepped(int x, int y, int z) {
            BlockState s = get(x, y, z);
            return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                    || s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock);
        }

        /** Standing at row y (feet in y): a floor below, room for a body. */
        boolean stand(int x, int y, int z) {
            return floor(x, y - 1, z) && passable(x, y, z) && passable(x, y + 1, z);
        }
    }

    static void register() {
        // the grid on the wall: every hold on its faces below the walk sits in an odd row (as the town's), the courses
        // run along every clear stretch (outer rows 15 and 27, inner row 9), and every side has a climb line to the walk
        AwakenTests.register("city_wall_grid", 600, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            CityPlan p = l.plan;
            Band band = new Band(l);
            int top = CityWall.top(p);
            int holds = 0, outerCourse = 0, outerMissing = 0, innerCourse = 0, innerMissing = 0;
            String firstBad = "", firstMissing = "";
            int[] lines = new int[CityWall.SIDES];
            for (int x = -RMAX; x <= RMAX; x++)
                for (int z = -RMAX; z <= RMAX; z++) {
                    if (!band.in(x, z)) continue;
                    byte k = p.kind(x, z);
                    if (k != WALL && k != VOID && k != RING) continue;
                    CityWall.Geo g = CityWall.geo(p, x, z);
                    if (g.d() < -9 || g.d() >= 8) continue;
                    boolean flight = CityWall.stairSide(g.k()) && g.wo() >= 7 && k == WALL; // the grand stair's treads are a floor
                    for (int y = 1; y < top && !flight; y++) {
                        BlockState s = band.get(x, y, z);
                        if (s == null || s.isAir() || !CityTests.lip(s) || !CityTests.clearOver(band.get(x, y + 1, z))) continue;
                        if (k == WALL && !exposed(band, x, y, z)) continue;
                        holds++;
                        if ((y & 1) == 0 && firstBad.isEmpty()) firstBad = "row " + y + " at " + x + "," + z + " (side " + g.k() + "): " + s;
                    }
                    boolean clear = CityWall.clearSpan(p, g.k(), g.a() - 1, g.a() + 1, -5.5);
                    if (k == VOID && g.lout() == 1 && clear && !CityWall.buttress(p, g) && !CityWall.stairSide(g.k())) {
                        for (int r : CityWall.COURSE_ROWS) {
                            outerCourse++;
                            BlockState s = band.get(x, r, z);
                            if (s == null || !CityTests.lip(s)) {
                                outerMissing++;
                                if (firstMissing.isEmpty()) firstMissing = "outer row " + r + " at " + x + "," + z + ": " + s;
                            }
                        }
                    }
                    if (k == RING && g.lin() == 1 && CityWall.bayClear(p, g) && !CityWall.pipe(p, g)) {
                        innerCourse++;
                        BlockState s = band.get(x, 9, z);
                        if (s == null || !CityTests.lip(s)) {
                            innerMissing++;
                            if (firstMissing.isEmpty()) firstMissing = "inner row 9 at " + x + "," + z + ": " + s;
                        }
                    }
                    // a climb line: climbable from the street to the walk's floor, unbroken
                    if (k == RING && g.lin() == 1) {
                        boolean line = true;
                        for (int y = 1; y <= top && line; y++) {
                            BlockState s = band.get(x, y, z);
                            line = s != null && s.is(net.minecraft.tags.BlockTags.CLIMBABLE);
                        }
                        if (line) lines[g.k()]++;
                    }
                }
            h.assertTrue(firstBad.isEmpty(), "a hold in an even row on the wall: " + firstBad);
            h.assertTrue(holds > 3000, "holds checked on the wall's faces: " + holds);
            h.assertTrue(outerMissing * 50 <= outerCourse, outerMissing + " of " + outerCourse + " outer course cells without a ledge; first " + firstMissing);
            h.assertTrue(innerMissing * 50 <= innerCourse, innerMissing + " of " + innerCourse + " inner course cells without a ledge; first " + firstMissing);
            int sidesWithLine = 0;
            StringBuilder without = new StringBuilder();
            for (int k = 0; k < CityWall.SIDES; k++) {
                if (lines[k] > 0) sidesWithLine++;
                else without.append(k).append(' ');
            }
            h.assertTrue(sidesWithLine >= 20, "sides with a climb line from the street to the walk: " + sidesWithLine + " of 24 (not " + without + ")");
            dev.awaken.Awaken.LOG.info("[city] wall grid: {} holds, outer courses {}/{} missing, inner {}/{} missing, climb lines on {} sides",
                    holds, outerMissing, outerCourse, innerMissing, innerCourse, sidesWithLine);
            h.succeed();
        });

        // the wall walk is one place: from one cell of it you can walk to every one of the 24 sides at the walk's level
        // (through the towers, the gatehouses and over the water gates), and a stair from the ring reaches it in every tower
        AwakenTests.register("city_wall_walk", 600, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            CityPlan p = l.plan;
            Band band = new Band(l);
            int walk = CityWall.top(p) + 1;
            // start on the walk in the middle of side 0
            double ap = CityWall.apothem(p);
            int sx = (int) Math.floor(ap * CityWall.COS[0]), sz = (int) Math.floor(ap * CityWall.SIN[0]);
            h.assertTrue(band.stand(sx, walk, sz), "the walk is standable on side 0 at " + sx + "," + sz);
            boolean[] seen = new boolean[band.size * band.size * YN];
            boolean[] sides = new boolean[CityWall.SIDES];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{sx, walk, sz});
            seen[band.index(sx, walk, sz)] = true;
            int reached = 0;
            while (!q.isEmpty()) {
                int[] c = q.poll();
                reached++;
                if (c[1] >= walk - 1 && c[1] <= walk + 1) {
                    CityWall.Geo g = CityWall.geo(p, c[0], c[2]);
                    if (Math.abs(g.d()) < 5) sides[g.k()] = true;
                }
                for (int[] d : CityPainter.NB)
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = c[0] + d[0], ny = c[1] + dy, nz = c[2] + d[1];
                        if (ny < walk - 3 || ny > walk + 4 || !band.in(nx, nz)) continue;
                        int i = band.index(nx, ny, nz);
                        if (seen[i] || !band.stand(nx, ny, nz)) continue;
                        if (dy > 0 && !band.stepped(nx, ny - 1, nz) && !band.passable(c[0], c[1] + 2, c[2])) continue; // headroom for the step up
                        seen[i] = true;
                        q.add(new int[]{nx, ny, nz});
                    }
            }
            int n = 0;
            StringBuilder missing = new StringBuilder();
            for (int k = 0; k < CityWall.SIDES; k++) {
                if (sides[k]) n++;
                else missing.append(k).append(' ');
            }
            h.assertTrue(n == CityWall.SIDES, "the walk reaches " + n + " of 24 sides (missing " + missing + ") from side 0; " + reached + " cells");
            // every tower's stair: from the ground outside its door to its hall on the walk
            int towers = 0, climbed = 0;
            StringBuilder failed = new StringBuilder();
            for (Part part : p.parts) {
                if (!part.kind().equals("tower") && !part.kind().equals("gate_tower") || "3".equals(part.style())) continue; // (water gates: no stair)
                towers++;
                if (climbs(band, p, part, walk)) climbed++;
                else failed.append(part.landmark()).append('@').append(part.x0()).append(',').append(part.z0()).append(' ');
            }
            h.assertTrue(climbed == towers, "stairs from the ring to the walk in " + climbed + " of " + towers + " towers; not: " + failed);
            // the Watch's way onto the walk (the citizens' graph, tools/city/citizens.py "wallWalk"): every tower door
            // node stands before the door this painter made, and every stretch of the round is open walk at the walk's row
            dev.awaken.citizens.Roster r = dev.awaken.citizens.Roster.get();
            h.assertTrue(r != null && r.wallX.length > 100, "the roster carries the wall walk");
            int base = p.streetGraph.nodes(), doors = 0, posts = 0;
            StringBuilder bad = new StringBuilder();
            for (int i = 0; i < r.wallX.length; i++) {
                int x = (int) Math.floor(r.wallX[i]), z = (int) Math.floor(r.wallZ[i]);
                if (r.wallRy[i] < walk - 1) {
                    doors++;
                    boolean door = false;
                    for (int[] d : CityPainter.NB)
                        for (int k = 0; k <= 3 && !door; k++) {
                            BlockState s = band.get(x + d[0] * k, 1, z + d[1] * k);
                            door = s != null && s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
                        }
                    if (!door || !band.stand(x, 1, z)) bad.append("door node ").append(base + i).append('@').append(x).append(',').append(z).append(' ');
                } else if (!band.stand(x, walk, z)) bad.append("walk node ").append(base + i).append('@').append(x).append(',').append(z).append(' ');
                else posts++;
            }
            for (int e = 0; e < r.wallA.length; e++) {
                int a = r.wallA[e] - base, b = r.wallB[e] - base;
                if (r.wallMove[e] != 0 || a < 0 || b < 0 || r.wallRy[a] < walk - 1 || r.wallRy[b] < walk - 1) continue;
                double len = Math.hypot(r.wallX[b] - r.wallX[a], r.wallZ[b] - r.wallZ[a]);
                for (double t = 0; t <= len; t += 0.5) {
                    int x = (int) Math.floor(r.wallX[a] + (r.wallX[b] - r.wallX[a]) * t / Math.max(len, 1e-6));
                    int z = (int) Math.floor(r.wallZ[a] + (r.wallZ[b] - r.wallZ[a]) * t / Math.max(len, 1e-6));
                    if (!band.stand(x, walk, z)) {
                        bad.append("round blocked at ").append(x).append(',').append(z).append(' ');
                        break;
                    }
                }
            }
            h.assertTrue(bad.isEmpty(), "the Watch's wall walk: " + bad);
            h.assertTrue(doors == towers, "a door node for every tower: " + doors + " of " + towers);
            dev.awaken.Awaken.LOG.info("[city] wall walk: {} cells, 24 sides, stairs in {} towers", reached, climbed);
            h.succeed();
        });

        // every gate's passage is clear for carts and crowds: six wide and five high under the portcullis, end to end
        AwakenTests.register("city_wall_gates", 200, h -> {
            CityLayout l = CityTests.layoutAt(0, 0);
            CityPlan p = l.plan;
            Band band = new Band(l);
            int gates = 0;
            for (Part part : p.parts) {
                if (!part.kind().equals("gate_passage")) continue;
                gates++;
                boolean alongZ = part.axis().equals("z");
                int width = alongZ ? part.x1() - part.x0() + 1 : part.z1() - part.z0() + 1;
                int length = alongZ ? part.z1() - part.z0() + 1 : part.x1() - part.x0() + 1;
                for (int across = 2; across < width - 2; across++)
                    for (int along = 0; along < length; along++) {
                        int x = alongZ ? part.x0() + across : part.x0() + along, z = alongZ ? part.z0() + along : part.z0() + across;
                        h.assertTrue(band.floor(x, 0, z), "the road under the " + part.landmark() + " at " + x + "," + z);
                        for (int y = 1; y <= 5; y++)
                            h.assertTrue(band.passable(x, y, z), "the " + part.landmark() + " passage is blocked at " + x + "," + y + "," + z + ": " + band.get(x, y, z));
                    }
            }
            h.assertTrue(gates == 3, "three gate passages: " + gates);
            h.succeed();
        });
    }

    /** A wall cell that shows to the open (a hold on its own face). */
    static boolean exposed(Band b, int x, int y, int z) {
        for (int[] d : CityPainter.NB) if (b.passable(x + d[0], y, z + d[1])) return true;
        return false;
    }

    /** From the ground outside a tower (any open cell beside its box on the town's side) up to the walk inside it. */
    static boolean climbs(Band band, CityPlan p, Part part, int walk) {
        int x0 = part.x0() - 2, x1 = part.x1() + 2, z0 = part.z0() - 2, z1 = part.z1() + 2;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean inBox = x >= part.x0() && x <= part.x1() && z >= part.z0() && z <= part.z1();
                byte k = p.kind(x, z);
                if (inBox || k == WALL || k == VOID || water(k)) continue;
                int g = p.lift(x, z) + 1;
                if (band.stand(x, g, z)) {
                    q.add(new int[]{x, g, z});
                    seen.add(key(x, g, z));
                }
            }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            boolean inBox = c[0] >= part.x0() && c[0] <= part.x1() && c[2] >= part.z0() && c[2] <= part.z1();
            if (inBox && c[1] >= walk) return true;
            for (int[] d : CityPainter.NB)
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = c[0] + d[0], ny = c[1] + dy, nz = c[2] + d[1];
                    if (nx < x0 || nx > x1 || nz < z0 || nz > z1 || ny < 1 || ny >= YN - 2) continue;
                    // outside the box only at the ground (no climbing the walls round it)
                    boolean nIn = nx >= part.x0() && nx <= part.x1() && nz >= part.z0() && nz <= part.z1();
                    if (!nIn && ny > p.lift(nx, nz) + 1) continue;
                    long kk = key(nx, ny, nz);
                    if (seen.contains(kk) || !band.stand(nx, ny, nz)) continue;
                    if (dy > 0 && !band.stepped(nx, ny - 1, nz) && !band.passable(c[0], c[1] + 2, c[2])) continue;
                    seen.add(kk);
                    q.add(new int[]{nx, ny, nz});
                }
        }
        return false;
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 32) | ((long) (z + 4096) << 8) | y;
    }
}
