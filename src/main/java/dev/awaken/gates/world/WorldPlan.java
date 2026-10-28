package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.GatePlan;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A world floor in the making (SPEC §38.5). Its programme is today's rooms for that floor ({@link
 * FloorBuilder#programme}): room types, monsters, keys, puzzles, caches, the Rig and the Lord's arena. A world engine
 * hosts each programme room on a place of its own shape (a nave bay, a chapel, a ledge on a tower), laid on the plot's
 * slot grid and joined along the programme's tree; {@link #finish} carries everything over. Same fights and rewards,
 * a different world. The Lord's arena and Rig rooms are built rooms: painted as today, the world carved round them.
 */
final class WorldPlan {
    /** Walkable ground at a place's own height: where its monsters may stand. */
    interface Walk {
        boolean ok(Room r, int x, int z);
    }

    final WorldEngine.Ctx ctx;
    final FloorBuilder.Builder logic, b;
    /** programme room id → world room id (-1 until hosted) */
    final int[] host;
    /** the programme as a tree from its start (a loop's extra doors are dropped): parent of each room, -1 at the start */
    final int[] parent;
    final List<Integer> spine;
    private final Map<Integer, int[]> anchors = new HashMap<>();

    WorldPlan(WorldEngine.Ctx c) {
        ctx = c;
        logic = FloorBuilder.programme(c.plan(), c.theme(), c.area(), c.floor());
        b = new FloorBuilder.Builder(c.plan(), c.theme(), c.area(), c.floor(), c.seed());
        b.grid(Plots.SIDE / 16, Plots.SIDE / 16);
        b.bossFloor = logic.bossFloor;
        int n = logic.rooms.size();
        host = new int[n];
        Arrays.fill(host, -1);
        parent = new int[n];
        Arrays.fill(parent, -2);
        spine = logic.path();
        // the tree: the spine first, then breadth-first from it, door order (deterministic)
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < spine.size(); i++) {
            parent[spine.get(i)] = i == 0 ? -1 : spine.get(i - 1);
            q.add(spine.get(i));
        }
        while (!q.isEmpty()) {
            int c0 = q.poll();
            for (Room.Door d : logic.rooms.get(c0).doors) {
                if (parent[d.toRoom] != -2) continue;
                parent[d.toRoom] = c0;
                q.add(d.toRoom);
            }
        }
    }

    Room room(int programmeId) {
        return logic.rooms.get(programmeId);
    }

    int rooms() {
        return logic.rooms.size();
    }

    /** Programme rooms hanging off this one that aren't on the spine, in door order. */
    List<Integer> branches(int id) {
        List<Integer> out = new ArrayList<>();
        for (Room.Door d : logic.rooms.get(id).doors) if (parent[d.toRoom] == id && !spine.contains(d.toRoom)) out.add(d.toRoom);
        return out;
    }

    /** Every programme room under this one (itself included), depth first. */
    int subtree(int id) {
        int n = 1;
        for (int c : branches(id)) n += subtree(c);
        return n;
    }

    boolean free(int gx, int gz, int gw, int gh) {
        return b.free(gx, gz, gw, gh);
    }

    /** Hosts a programme room on a place of the world: slots and the height its floor stands at (above the base). */
    int host(int id, int gx, int gz, int gw, int gh, int dy) {
        Room L = logic.rooms.get(id);
        int w = b.add(L.type, Room.Shape.RECT, gx, gz, gw, gh, dy);
        host[id] = w;
        return w;
    }

    /** A programme room shares another's place (a long spine folded short): their monsters add up. */
    void merge(int id, int into) {
        host[id] = host[into];
    }

    /** A place of the world's own with no programme: a landing, a bridge (calm, nothing waits there). */
    int extra(int gx, int gz, int gw, int gh, int dy) {
        int w = b.add(Room.Type.NORMAL, Room.Shape.RECT, gx, gz, gw, gh, dy);
        return w;
    }

    /** Joins two world rooms by a door in the middle of their shared edge; false if they don't touch. */
    boolean join(int worldA, int worldB) {
        return worldA == worldB || b.connected(worldA, worldB) || b.connect(worldA, worldB);
    }

    boolean joinProgramme(int a, int bId) {
        return join(host[a], host[bId]);
    }

    /** Where a place's features gather (the shrine's apse, a chapel's altar): floor-local x, z. */
    void anchor(int worldRoom, int x, int z) {
        anchors.put(worldRoom, new int[]{x, z});
    }

    int[] anchorOf(Room w) {
        int[] a = anchors.get(w.id);
        return a != null ? a : new int[]{w.centerX(), w.centerZ()};
    }

    Room world(int worldRoom) {
        return b.rooms.get(worldRoom);
    }

    /** Built rooms are painted by today's painter inside the world: the Lord's arena and any Rig room. */
    static boolean built(Room w) {
        return w.type == Room.Type.BOSS || w.rig != null;
    }

    /** A place the bars close (an arena, the Lord's, a sealed end): the world screens every opening but the door. */
    boolean sealable(Room w) {
        return w.type == Room.Type.ARENA || w.type == Room.Type.BOSS || (w.id == b.end && endSealed());
    }

    boolean endSealed() {
        return logic.sealEnd || (ctx.plan().friendly() && !logic.bossFloor);
    }

    // ---- carving help ------------------------------------------------------------------------------------------------

    /**
     * Every doorway open and walkable: a floor under it, head room over it, the lintel and, when it's sealed from the
     * start, its bars. Run after the world's own spaces are carved, before its details.
     */
    void thresholds(Sculpt s, int baseY, BlockState floor, BlockState lintel) {
        BlockState bars = GatesRegistry.BARS.get().defaultBlockState();
        for (Room w : b.rooms) {
            if (built(w)) continue;
            for (Room.Door d : w.doors) {
                int y = baseY + d.y;
                int[] box = doorBox(d, Sculpt.SHELL);
                s.box(box[0], y - 1, box[2], box[1], y - 1, box[3], floor);
                s.openBox(box[0], y, box[2], box[1], y + RoomGeometry.DOOR_HEIGHT - 1, box[3]);
                // the lintel only where nothing else stands over the doorway
                s.op(box[0], y + RoomGeometry.DOOR_HEIGHT, box[2], box[1], y + RoomGeometry.DOOR_HEIGHT, box[3], (bb, x, yy, z) -> {
                    if (bb.get(x, yy, z) == Sculpt.SOLID) bb.set(x, yy, z, lintel);
                });
                if (d.sealed && d.ownsBars) {
                    for (int lat = RoomGeometry.DOOR_LO; lat <= RoomGeometry.DOOR_HI; lat++) {
                        int[] p = barsAt(d, lat);
                        for (int k = 0; k < RoomGeometry.DOOR_HEIGHT; k++) s.put(p[0], y + k, p[1], bars);
                    }
                }
            }
        }
    }

    /**
     * Screens round a place the bars close: every open block of its slot-edge rows that face another place, from its
     * floor to the top of the world, becomes {@code screen} (stone with grilles, a rood screen), except its doorways.
     */
    void screens(Sculpt s, int baseY, ScreenLook look) {
        for (Room w : b.rooms) {
            if (!sealable(w) || built(w)) continue;
            for (int gx = w.gx; gx < w.gx + w.gw; gx++) {
                for (int gz = w.gz; gz < w.gz + w.gh; gz++) {
                    for (int side = 0; side < 4; side++) {
                        int nx = gx + GatePainter.DX[side], nz = gz + GatePainter.DZ[side];
                        if (b.at(nx, nz) == w.id) continue;
                        Room.Door door = null;
                        for (Room.Door d : w.doors) if (d.gx == gx && d.gz == gz && d.side == side) door = d;
                        screen(s, baseY, w, gx, gz, side, door, look);
                    }
                }
            }
        }
    }

    /** What a screen is made of at a point on it: lateral offset in the slot and height above the place's floor. */
    interface ScreenLook {
        BlockState at(int lat, int rise, int side);
    }

    private void screen(Sculpt s, int baseY, Room w, int gx, int gz, int side, Room.Door door, ScreenLook look) {
        int floorY = baseY + w.dy;
        int x0, z0, x1, z1;
        switch (side) {
            case 0 -> { x0 = gx * 16; x1 = x0 + 15; z0 = z1 = gz * 16; }
            case 2 -> { x0 = gx * 16; x1 = x0 + 15; z0 = z1 = gz * 16 + 15; }
            case 1 -> { z0 = gz * 16; z1 = z0 + 15; x0 = x1 = gx * 16 + 15; }
            default -> { z0 = gz * 16; z1 = z0 + 15; x0 = x1 = gx * 16; }
        }
        int dy0 = door == null ? 0 : door.y - w.dy;
        s.op(x0, floorY - 4, z0, x1, s.yHi, z1, (bb, x, y, z) -> {
            // water is open too: nothing swims under a screen
            if (bb.get(x, y, z) != Sculpt.OPEN && !bb.water(x, y, z)) return;
            int lat = side == 0 || side == 2 ? x - gx * 16 : z - gz * 16;
            int rise = y - floorY;
            if (door != null && lat >= RoomGeometry.DOOR_LO && lat <= RoomGeometry.DOOR_HI && rise >= dy0 && rise < dy0 + RoomGeometry.DOOR_HEIGHT) return;
            bb.set(x, y, z, look.at(lat, rise, side));
        });
    }

    /** The doorway's box across its slot edge, reaching {@code depth} blocks into each side: x0, x1, z0, z1. */
    static int[] doorBox(Room.Door d, int depth) {
        int lo = RoomGeometry.DOOR_LO, hi = RoomGeometry.DOOR_HI;
        return switch (d.side) {
            case 0 -> new int[]{d.gx * 16 + lo, d.gx * 16 + hi, d.gz * 16 - depth, d.gz * 16 + depth - 1};
            case 2 -> new int[]{d.gx * 16 + lo, d.gx * 16 + hi, (d.gz + 1) * 16 - depth, (d.gz + 1) * 16 + depth - 1};
            case 1 -> new int[]{(d.gx + 1) * 16 - depth, (d.gx + 1) * 16 + depth - 1, d.gz * 16 + lo, d.gz * 16 + hi};
            default -> new int[]{d.gx * 16 - depth, d.gx * 16 + depth - 1, d.gz * 16 + lo, d.gz * 16 + hi};
        };
    }

    /** Where the bars stand at a lateral offset of a door: x, z (as Rooms.barsPositions). */
    static int[] barsAt(Room.Door d, int lat) {
        return switch (d.side) {
            case 0 -> new int[]{d.gx * 16 + lat, d.gz * 16};
            case 2 -> new int[]{d.gx * 16 + lat, d.gz * 16 + 15};
            case 1 -> new int[]{d.gx * 16 + 15, d.gz * 16 + lat};
            default -> new int[]{d.gx * 16, d.gz * 16 + lat};
        };
    }

    // ---- finishing ---------------------------------------------------------------------------------------------------

    /** What the world adds to the floor beyond its rooms. */
    record Look(int baseY, String biome, FloorAir air, WorldModel model, float yaw) {}

    /** Places that are a world of their own (a secret world reached from this one): world room id → world id. */
    final Map<Integer, String> placeWorlds = new HashMap<>(), placeBiomes = new HashMap<>();

    /**
     * Carries the programme over to the world's rooms and makes the floor: types, monsters, the path, features (the
     * Lord's and an inner gate's planned again for their place, the rest moved to the place's anchor as they were laid
     * round their room's centre), the Rig planned again on its place, spawn spots on the world's own ground, the end
     * sealed as the programme seals it. Null when the programme isn't fully hosted.
     */
    FloorLayout finish(Walk walk, Look look) {
        for (int h : host) if (h < 0) return WorldEngine.fail("a programme room has no place");
        Map<Integer, List<Room>> on = new HashMap<>();
        for (int i = 0; i < host.length; i++) on.computeIfAbsent(host[i], k -> new ArrayList<>()).add(logic.rooms.get(i));
        for (Room w : b.rooms) {
            List<Room> ls = on.get(w.id);
            if (ls == null) {
                // the world's own places are calm: nothing waits there, nothing is planned
                w.type = Room.Type.NORMAL;
                continue;
            }
            Room L = ls.get(0);
            w.type = L.type;
            w.dark = L.dark;
            if (ls.size() > 1) {
                boolean elite = false;
                for (Room o : ls) {
                    elite |= o.type == Room.Type.ELITE;
                    w.dark |= o.dark;
                }
                w.type = elite ? Room.Type.ELITE : Room.Type.NORMAL;
            }
            for (Room o : ls) {
                w.normals += o.normals;
                w.elites += o.elites;
                w.wave2Normals += o.wave2Normals;
                w.wave2Elites += o.wave2Elites;
            }
            if (w.type == Room.Type.BOSS) {
                if (w.gw != L.gw || w.gh != L.gh) return WorldEngine.fail("the Lord's arena changed size");
                w.piece = L.piece;
                w.shape = L.shape;
                w.height = L.height;
            }
        }
        b.start = host[logic.start];
        b.end = host[logic.end];
        b.sealEnd = logic.sealEnd;
        b.markPath();
        if (b.path().isEmpty()) return WorldEngine.fail("no way from the start to the end");
        for (Room w : b.rooms) {
            List<Room> ls = on.get(w.id);
            if (ls == null) continue;
            if (w.type == Room.Type.BOSS || w.type == Room.Type.INNER_GATE) {
                b.features(w);
                continue;
            }
            int[] a = anchorOf(w);
            java.util.Set<Long> taken = new java.util.HashSet<>();
            for (Room L : ls) {
                for (Room.Feature ft : L.features) {
                    int x = clamp(a[0] + ft.x() - L.centerX(), w.x0(), w.x1()), z = clamp(a[1] + ft.z() - L.centerZ(), w.z0(), w.z1());
                    int[] q = ground(w, walk, x, z, taken);
                    taken.add(((long) q[0] << 32) ^ (q[1] & 0xFFFFFFFFL));
                    w.features.add(new Room.Feature(ft.kind(), q[0], ft.y(), q[1], ft.index()));
                }
            }
        }
        for (int i = 0; i < host.length; i++) {
            Room L = logic.rooms.get(i);
            if (L.rig == null) continue;
            Room w = b.rooms.get(host[i]);
            if (w.gw == L.gw && w.gh == L.gh && w.onPath && w.pathIn != null && w.pathOut != null && w.type == L.type) {
                w.rig = RigRooms.rebuild(b, w, L.rig.kind);
                if (w.rig != null) w.height = L.height;
            }
            RigRooms.finish(w);
        }
        for (Room w : b.rooms) {
            if (built(w)) b.spots(w);
            else spots(w, walk);
        }
        if (endSealed()) {
            Room e = b.rooms.get(b.end);
            for (Room.Door d : e.doors) {
                d.sealed = true;
                d.ownsBars = true;
            }
        }
        Plots.Plot plot = ctx.plot();
        return new FloorLayout(ctx.area(), ctx.floor(), look.baseY(), plot.slotX(), plot.slotZ(), b.w, b.h, ctx.theme(), logic.bossFloor,
                b.rooms, b.start, b.end, new FloorLayout.Frame(plot, plot.minY(), plot.maxY(), null, ctx.worldId(), ctx.mood(), look.biome(), look.air(), look.model(), look.yaw(), placeWorlds, placeBiomes));
    }

    private void spots(Room r, Walk walk) {
        int[] a = anchorOf(r);
        List<int[]> cand = new ArrayList<>();
        for (int x = r.x0(); x <= r.x1(); x += 2) {
            for (int z = r.z0(); z <= r.z1(); z += 2) {
                if (!walk.ok(r, x, z)) continue;
                boolean nearFeature = false;
                for (Room.Feature f : r.features) if (Math.abs(f.x() - x) <= 1 && Math.abs(f.z() - z) <= 1) nearFeature = true;
                if (!nearFeature) cand.add(new int[]{x, z});
            }
        }
        cand.sort((p, q) -> Double.compare(Math.hypot(p[0] - a[0], p[1] - a[1]) + Hash.unit(r.seed, p[0], p[1]) * 4,
                Math.hypot(q[0] - a[0], q[1] - a[1]) + Hash.unit(r.seed, q[0], q[1]) * 4));
        for (int i = 0; i < cand.size() && r.spawnSpots.size() < 24; i++) r.spawnSpots.add(cand.get(i));
    }

    /** The nearest free walkable block to (x, z) in the place (a cache never floats over water), or (x, z) itself. */
    private static int[] ground(Room w, Walk walk, int x, int z, java.util.Set<Long> taken) {
        for (int r = 0; r <= 10; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int qx = x + dx, qz = z + dz;
                    if (qx < w.x0() || qx > w.x1() || qz < w.z0() || qz > w.z1()) continue;
                    if (walk.ok(w, qx, qz) && !taken.contains(((long) qx << 32) ^ (qz & 0xFFFFFFFFL))) return new int[]{qx, qz};
                }
            }
        }
        return new int[]{x, z};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- the model -----------------------------------------------------------------------------------------------------

    /** A world floor carved from one {@link Sculpt}, its built rooms painted as today and its places' features stamped. */
    record SculptModel(Sculpt sculpt) implements WorldModel {
        @Override
        public void carve(GatePlan plan, FloorLayout f, int slotX, int slotZ, GatePainter.Sink out) {
            int gx = slotX - f.originSlotX, gz = slotZ - f.originSlotZ;
            Room r = f.roomAtSlot(gx, gz);
            if (r != null && built(r)) {
                int floorY = f.baseY + r.dy;
                sculpt.carve(gx, gz, out, floorY - Cells.BAND_BELOW, floorY + Cells.BAND_ABOVE - 1);
                GatePainter.paintBuilt(plan, f, r, gx, gz, out);
                return;
            }
            sculpt.carve(gx, gz, out, 1, 0);
            if (r != null) GatePainter.paintFeatures(plan, f, r, gx, gz, out);
        }
    }
}
