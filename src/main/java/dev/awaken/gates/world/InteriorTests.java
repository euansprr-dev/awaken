package dev.awaken.gates.world;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GameTests for the interiors (docs/modules/gates.md "Interiors"): every theme's floors paint inside their worldgen
 * budget, set pieces never block a doorway, every piece and arena gets built, the Lords fit their arenas. Also writes
 * one floor and the Lord's floor of each theme to run/interiors/ for tools/art/specs/gate_interiors.py.
 */
public final class InteriorTests {
    private InteriorTests() {}

    static void register() {
        AwakenTests.register("gates_interior_dumps", 400, h -> {
            for (Theme t : Theme.values()) {
                GatePlan plan = showcasePlan(t);
                GateLayout l = GateTestAccess.build(plan);
                dump(l, l.main.get(0), t.id + "_floor1");
                dump(l, l.main.get(l.main.size() - 1), t.id + "_lord");
            }
            h.succeed();
        });

        AwakenTests.register("gates_interior_pieces_keep_doorways", 600, h -> {
            Set<SetPiece> seen = EnumSet.noneOf(SetPiece.class);
            int checked = 0;
            for (Theme t : Theme.values()) {
                for (int i = 0; i < 6; i++) {
                    GatePlan plan = plan(0xD00Bl + t.ordinal() * 131L + i * 7L, Rank.values()[1 + i % 3], t,
                            i % 2 == 0 ? Layout.LABYRINTH : Layout.ARENA_CHAIN, Twist.NONE);
                    GateLayout l = GateTestAccess.build(plan);
                    for (FloorLayout f : List.of(l.main.get(0), l.main.get(l.main.size() - 1))) {
                        for (Room r : f.rooms) {
                            if (r.piece == null) continue;
                            seen.add(r.piece);
                            Map<Long, BlockState> blocks = paintRoom(l, f, r, 9);
                            String why = reachable(f, r, blocks);
                            h.assertTrue(why == null, t.id + " " + r.piece + " in a " + r.gw + "x" + r.gh + " " + r.type + ": " + why);
                            checked++;
                        }
                    }
                }
            }
            for (SetPiece p : SetPiece.values()) h.assertTrue(seen.contains(p), "set piece " + p + " is built somewhere (" + checked + " rooms checked)");
            h.succeed();
        });

        AwakenTests.register("gates_interior_build_budget", 600, h -> {
            StringBuilder report = new StringBuilder();
            for (int pass = 0; pass < 2; pass++) {
                for (Theme t : Theme.values()) {
                    GatePlan plan = plan(0xB0D6E7L + t.ordinal(), Rank.B, t, Layout.values()[t.ordinal() % Layout.values().length], Twist.NONE);
                    long b0 = System.nanoTime();
                    GateLayout l = GateTestAccess.build(plan);
                    long build = System.nanoTime() - b0;
                    long worst = 0, total = 0;
                    int chunks = 0, maxBlocks = 0;
                    for (FloorLayout f : l.main) {
                        for (int gx = -1; gx <= f.w; gx++) {
                            for (int gz = -1; gz <= f.h; gz++) {
                                int[] count = new int[1];
                                int cx = (Cells.originX(11) >> 4) + f.originSlotX + gx, cz = (Cells.originZ(11) >> 4) + f.originSlotZ + gz;
                                long c0 = System.nanoTime();
                                GatePainter.paint(l, 11, cx, cz, (x, y, z, s) -> count[0]++);
                                long dt = System.nanoTime() - c0;
                                worst = Math.max(worst, dt);
                                total += dt;
                                chunks++;
                                maxBlocks = Math.max(maxBlocks, count[0]);
                            }
                        }
                    }
                    if (pass == 0) continue; // the first pass warms the JIT and the palette caches
                    double mean = total / 1e6 / chunks;
                    report.append(String.format(Locale.ROOT, "%s: build %.1f ms, %d chunks, mean %.2f ms, worst %.2f ms, max %d blocks%n",
                            t.id, build / 1e6, chunks, mean, worst / 1e6, maxBlocks));
                    // a worldgen thread paints one chunk per task: keep it well under a vanilla noise chunk (~5-15 ms)
                    h.assertTrue(mean < 4.0, t.id + " paints a chunk in " + mean + " ms on average (budget 4)");
                    h.assertTrue(worst < 40.0 * 1e6, t.id + " worst chunk " + worst / 1e6 + " ms (budget 40)");
                    h.assertTrue(build < 50 * 1e6, t.id + " layout built in " + build / 1e6 + " ms (budget 50, once per gate, cached)");
                    h.assertTrue(maxBlocks <= 16 * 16 * Cells.FLOOR_PITCH * l.main.size(), t.id + " writes " + maxBlocks + " blocks in one chunk");
                }
            }
            Awaken.LOG.info("[gates] interior build budget\n{}", report);
            h.succeed();
        });
    }

    // ---- plans ------------------------------------------------------------------------------------------------------

    /** A content-version-2 plan (new interiors) with no modifiers. */
    static GatePlan plan(long seed, Rank rank, Theme theme, Layout layout, Twist twist) {
        return new GatePlan(seed, 2, rank, theme, layout, List.<Modifier>of(), twist, Curve.floorsFor(rank, false),
                theme.bossId(), Theme.values()[(theme.ordinal() + 3) % Theme.values().length], 0);
    }

    /** The plan whose first floor shows a theme off in the previews: a labyrinth with a big hall and its pieces. */
    static GatePlan showcasePlan(Theme t) {
        GatePlan best = null;
        int bestScore = -1;
        for (int i = 0; i < 24; i++) {
            GatePlan p = plan(0x5EED00L + t.ordinal() * 1000L + i, Rank.C, t, Layout.LABYRINTH, Twist.NONE);
            GateLayout l = GateTestAccess.build(p);
            Set<SetPiece> pieces = EnumSet.noneOf(SetPiece.class);
            for (Room r : l.main.get(0).rooms) if (r.piece != null) pieces.add(r.piece);
            int score = pieces.size() * 10 + (int) l.main.get(0).rooms.stream().filter(r -> r.piece != null && r.area() > 1).count();
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    // ---- painting into maps -----------------------------------------------------------------------------------------

    static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    /** Paints the chunks a room covers (and one ring of neighbours); keys are floor-local x, absolute y, floor-local z. */
    static Map<Long, BlockState> paintRoom(GateLayout l, FloorLayout f, Room r, int cell) {
        Map<Long, BlockState> out = new HashMap<>();
        for (int sx = r.gx - 1; sx <= r.gx + r.gw; sx++) for (int sz = r.gz - 1; sz <= r.gz + r.gh; sz++) paintSlot(l, f, sx, sz, cell, out);
        return out;
    }

    static Map<Long, BlockState> paintFloor(GateLayout l, FloorLayout f, int cell) {
        Map<Long, BlockState> out = new HashMap<>();
        for (int sx = -1; sx <= f.w; sx++) for (int sz = -1; sz <= f.h; sz++) paintSlot(l, f, sx, sz, cell, out);
        return out;
    }

    static void paintSlot(GateLayout l, FloorLayout f, int sx, int sz, int cell, Map<Long, BlockState> out) {
        int chunkX = (Cells.originX(cell) >> 4) + f.originSlotX + sx;
        int chunkZ = (Cells.originZ(cell) >> 4) + f.originSlotZ + sz;
        int bx0 = sx * 16, bz0 = sz * 16;
        GatePainter.paint(l, cell, chunkX, chunkZ, (x, y, z, s) -> {
            if (Cells.floorAtY(y) == f.floor) out.put(key(bx0 + x, y, bz0 + z), s);
        });
    }

    // ---- reachability -----------------------------------------------------------------------------------------------

    static boolean passable(BlockState s) {
        if (s == null || s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return true;
        var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.2;
    }

    static boolean standable(BlockState s) {
        return s != null && (!passable(s) || !s.getFluidState().isEmpty());
    }

    /**
     * Walks the painted room: from the first doorway, every other doorway and (in arenas) the Lord's spot and the exit
     * portal's row must be reachable on foot (step up one, drop up to three, two blocks of head room). Null when fine.
     */
    static String reachable(FloorLayout f, Room r, Map<Long, BlockState> b) {
        if (r.doors.isEmpty()) return null;
        int y0 = f.baseY - Cells.BAND_BELOW, y1 = f.baseY + Cells.BAND_ABOVE - 1;
        int minX = r.gx * 16 - 1, minZ = r.gz * 16 - 1, maxX = (r.gx + r.gw) * 16, maxZ = (r.gz + r.gh) * 16;
        int W = maxX - minX + 1, D = maxZ - minZ + 1, H = y1 - y0 + 1;
        boolean[] seen = new boolean[W * D * H];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int[] start = doorStand(f, r, r.doors.get(0), b);
        if (start == null) return "no place to stand in the first doorway";
        q.add(start);
        seen[(start[0] - minX) + (start[2] - minZ) * W + (start[1] - y0) * W * D] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + GatePainter.DX[d], nz = c[2] + GatePainter.DZ[d];
                if (nx < minX || nz < minZ || nx > maxX || nz > maxZ) continue;
                for (int ny = c[1] + 1; ny >= c[1] - 3; ny--) {
                    if (ny <= y0 || ny + 1 > y1) continue;
                    if (!standable(b.get(key(nx, ny - 1, nz))) || !passable(b.get(key(nx, ny, nz))) || !passable(b.get(key(nx, ny + 1, nz)))) continue;
                    if (ny > c[1] && !passable(b.get(key(c[0], c[1] + 2, c[2])))) continue; // jumping needs head room
                    int i = (nx - minX) + (nz - minZ) * W + (ny - y0) * W * D;
                    if (!seen[i]) {
                        seen[i] = true;
                        q.add(new int[]{nx, ny, nz});
                    }
                    break;
                }
            }
        }
        for (Room.Door d : r.doors) {
            int[] s = doorStand(f, r, d, b);
            if (s == null) return "no place to stand in the doorway on side " + d.side;
            boolean ok = false;
            for (int dy = -1; dy <= 1 && !ok; dy++) {
                int y = s[1] + dy;
                if (y > y0 && y <= y1) ok = seen[(s[0] - minX) + (s[2] - minZ) * W + (y - y0) * W * D];
            }
            if (!ok) return "doorway on side " + d.side + " can't be reached from side " + r.doors.get(0).side;
        }
        if (r.piece != null && r.piece.arena) {
            int y = f.baseY + r.dy;
            int[][] spots = {{r.centerX(), r.centerZ()}, {r.centerX(), r.centerZ() + 3}};
            for (int[] sp : spots) {
                boolean ok = false;
                for (int dy = -1; dy <= 1 && !ok; dy++) ok = seen[(sp[0] - minX) + (sp[1] - minZ) * W + (y + dy - y0) * W * D];
                if (!ok) return "the Lord's spot / exit portal at " + sp[0] + "," + sp[1] + " can't be reached";
            }
        }
        return null;
    }

    /** Standing spot (feet) in the middle of a doorway, on this room's side of the wall. */
    static int[] doorStand(FloorLayout f, Room r, Room.Door d, Map<Long, BlockState> b) {
        int x, z;
        switch (d.side) {
            case 0 -> { x = d.gx * 16 + 8; z = d.gz * 16 + 1; }
            case 2 -> { x = d.gx * 16 + 8; z = d.gz * 16 + 14; }
            case 1 -> { x = d.gx * 16 + 14; z = d.gz * 16 + 8; }
            default -> { x = d.gx * 16 + 1; z = d.gz * 16 + 8; }
        }
        int y = f.baseY + d.y;
        for (int dy = 0; dy >= -2; dy--) {
            if (standable(b.get(key(x, y + dy - 1, z))) && passable(b.get(key(x, y + dy, z)))) return new int[]{x, y + dy, z};
        }
        return null;
    }

    // ---- dumps for the offline renderer -----------------------------------------------------------------------------

    static void dump(GateLayout l, FloorLayout f, String name) {
        Map<Long, BlockState> b = paintFloor(l, f, 13);
        StringBuilder sb = new StringBuilder(1 << 20);
        sb.append("{\"name\":\"").append(name).append("\",\"theme\":\"").append(f.theme.id).append("\",\"floor\":").append(f.floor)
                .append(",\"boss\":").append(f.bossFloor).append(",\"baseY\":").append(f.baseY).append(",\"rooms\":[");
        for (int i = 0; i < f.rooms.size(); i++) {
            Room r = f.rooms.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(r.id).append(",\"type\":\"").append(r.type).append("\",\"shape\":\"").append(r.shape)
                    .append("\",\"piece\":\"").append(r.piece == null ? "" : r.piece.name()).append("\",\"x0\":").append(r.x0())
                    .append(",\"z0\":").append(r.z0()).append(",\"x1\":").append(r.x1()).append(",\"z1\":").append(r.z1())
                    .append(",\"y\":").append(f.baseY + r.dy).append(",\"h\":").append(r.height).append(",\"onPath\":").append(r.onPath)
                    .append(",\"doors\":[");
            for (int j = 0; j < r.doors.size(); j++) {
                Room.Door d = r.doors.get(j);
                if (j > 0) sb.append(',');
                sb.append("{\"x\":").append(d.centerX()).append(",\"z\":").append(d.centerZ()).append(",\"side\":").append(d.side)
                        .append(",\"y\":").append(f.baseY + d.y).append(",\"to\":").append(d.toRoom).append(",\"onward\":").append(d.onward)
                        .append(",\"toEnd\":").append(d.toEnd).append('}');
            }
            sb.append("]}");
        }
        sb.append("],\"start\":").append(f.start).append(",\"end\":").append(f.end).append(",\"cams\":[");
        boolean fc = true;
        for (String spot : f.bossFloor ? List.of("start", "ante", "lord") : List.of("start", "piece", "shrine")) {
            double[] v = Interiors.view(f, spot);
            if (v == null) continue;
            if (!fc) sb.append(',');
            fc = false;
            sb.append(String.format(Locale.ROOT, "{\"name\":\"%s\",\"pos\":[%.2f,%.2f,%.2f],\"yaw\":%.1f,\"pitch\":%.1f}", spot, v[0], v[1] + 1.62, v[2], v[3], v[4]));
        }
        sb.append("]");
        // the whole band as a grid: a palette and one uint16 index per block (x fastest, then z, then y), base64
        int minX = -16, minZ = -16, maxX = f.w * 16 + 15, maxZ = f.h * 16 + 15;
        int y0 = f.baseY - Cells.BAND_BELOW, y1 = f.baseY + Cells.BAND_ABOVE - 1;
        int nx = maxX - minX + 1, nz = maxZ - minZ + 1, ny = y1 - y0 + 1;
        Map<BlockState, Integer> pal = new java.util.LinkedHashMap<>();
        pal.put(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(nx * ny * nz * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int y = y0; y <= y1; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockState st = b.get(key(x, y, z));
                    int id = st == null ? 0 : pal.computeIfAbsent(st, k -> pal.size());
                    buf.putShort((short) id);
                }
            }
        }
        sb.append(",\"origin\":[").append(minX).append(',').append(y0).append(',').append(minZ).append("],\"dims\":[")
                .append(nx).append(',').append(ny).append(',').append(nz).append("],\"palette\":[");
        boolean fp0 = true;
        for (BlockState st : pal.keySet()) {
            if (!fp0) sb.append(',');
            fp0 = false;
            var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            sb.append("[\"").append(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()).append("\",\"");
            boolean fp = true;
            for (Property<?> p : st.getProperties()) {
                if (!fp) sb.append(',');
                fp = false;
                sb.append(p.getName()).append('=').append(propValue(st, p));
            }
            sb.append("\"]");
        }
        sb.append("],\"grid\":\"").append(java.util.Base64.getEncoder().encodeToString(buf.array())).append("\"}");
        try {
            Path dir = Path.of("interiors");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".json"), sb.toString());
        } catch (IOException ex) {
            Awaken.LOG.warn("[gates] could not write the interior dump: {}", ex.toString());
        }
    }

    private static <T extends Comparable<T>> String propValue(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    static List<Room> pieceRooms(FloorLayout f) {
        List<Room> out = new ArrayList<>();
        for (Room r : f.rooms) if (r.piece != null) out.add(r);
        return out;
    }
}
