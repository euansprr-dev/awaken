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

    /** Within {@code r} of any place's anchor (where its features stand: nothing grows over them). */
    /** Where the handbells and touchstones asked for so far want to stand (x, z), a copy: they're set last of the carve. */
    List<int[]> handSpots() {
        List<int[]> l = new ArrayList<>();
        for (Hand hd : hands) l.add(new int[]{hd.x(), hd.z()});
        return l;
    }

    boolean nearAnchor(double x, double z, double r) {
        for (int[] a : anchors.values()) if (Math.hypot(x - a[0] - 0.5, z - a[1] - 0.5) < r) return true;
        return false;
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

    /** A hidden light at head height just inside every doorway of the world's own places, so no way goes dark at a door. */
    void doorLights(Sculpt s, int baseY, BlockState light) {
        for (Room w : b.rooms) {
            if (built(w)) continue;
            for (Room.Door d : w.doors) {
                int x = switch (d.side) { case 1 -> d.gx * 16 + 15; case 3 -> d.gx * 16; default -> d.gx * 16 + 7; };
                int z = switch (d.side) { case 0 -> d.gz * 16; case 2 -> d.gz * 16 + 15; default -> d.gz * 16 + 7; };
                s.putOpen(x, baseY + d.y + 1, z, light);
            }
        }
    }

    /**
     * Screens round a place the bars close: every open block of its slot-edge rows that face another place, from its
     * floor to the top of the world, becomes {@code screen} (stone with grilles, a rood screen), except its doorways.
     */
    void screens(Sculpt s, int baseY, ScreenLook look) {
        screens(s, baseY, look, s.yHi);
    }

    /** As {@link #screens(Sculpt, int, ScreenLook)}, up to {@code topY} (an open land's: under its lid). */
    void screens(Sculpt s, int baseY, ScreenLook look, int topY) {
        for (Room w : b.rooms) {
            if (!sealable(w) || built(w)) continue;
            for (int gx = w.gx; gx < w.gx + w.gw; gx++) {
                for (int gz = w.gz; gz < w.gz + w.gh; gz++) {
                    for (int side = 0; side < 4; side++) {
                        int nx = gx + GatePainter.DX[side], nz = gz + GatePainter.DZ[side];
                        if (b.at(nx, nz) == w.id) continue;
                        Room.Door door = null;
                        for (Room.Door d : w.doors) if (d.gx == gx && d.gz == gz && d.side == side) door = d;
                        screen(s, baseY, w, gx, gz, side, door, look, topY);
                    }
                }
            }
        }
    }

    /** What a screen is made of at a point on it: lateral offset in the slot and height above the place's floor. */
    interface ScreenLook {
        BlockState at(int lat, int rise, int side);
    }

    private void screen(Sculpt s, int baseY, Room w, int gx, int gz, int side, Room.Door door, ScreenLook look, int topY) {
        int floorY = baseY + w.dy;
        int x0, z0, x1, z1;
        switch (side) {
            case 0 -> { x0 = gx * 16; x1 = x0 + 15; z0 = z1 = gz * 16; }
            case 2 -> { x0 = gx * 16; x1 = x0 + 15; z0 = z1 = gz * 16 + 15; }
            case 1 -> { z0 = gz * 16; z1 = z0 + 15; x0 = x1 = gx * 16 + 15; }
            default -> { z0 = gz * 16; z1 = z0 + 15; x0 = x1 = gx * 16; }
        }
        int dy0 = door == null ? 0 : door.y - w.dy;
        s.op(x0, floorY - 4, z0, x1, topY, z1, (bb, x, y, z) -> {
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
    record Look(int baseY, String biome, FloorAir air, WorldModel model, float yaw, boolean sky) {
        Look(int baseY, String biome, FloorAir air, WorldModel model, float yaw) {
            this(baseY, biome, air, model, yaw, false);
        }
    }

    /**
     * Where you stand over each column, blocks above the base (SPEC §38.14), set by an engine that gives its places
     * ground before {@link #finish}; null for a flat floor. Spawns and features read it through the floor.
     */
    byte[] ground;
    /** The columns of the high ledges and their stairs (SPEC §38.14, stage 4), null if none: nothing is placed there. */
    java.util.BitSet high;

    /** Places that are a world of their own (a secret world reached from this one): world room id → world id. */
    final Map<Integer, String> placeWorlds = new HashMap<>(), placeBiomes = new HashMap<>();
    /** What the live layer needs to find again (SPEC §38.8): bells, secrets, a basin the Flood fills. */
    final List<FloorLayout.Mark> marks = new ArrayList<>();

    /** A mark over a box, floor-local x/z and y above the base, like Gate Air. */
    void mark(String kind, String id, int x0, int y0, int z0, int x1, int y1, int z1) {
        marks.add(new FloorLayout.Mark(kind, id, x0, y0, z0, x1, y1, z1));
    }

    /** A bell's mark: its lip's centre, its radius and height (so the Tolling rings it and a strike knows it). */
    void bell(double cx, int lip, double cz, double r, int h) {
        bell("", cx, lip, cz, r, h);
    }

    /** A bell an event looks for by name ("still", "lowest"). */
    void bell(String id, double cx, int lip, double cz, double r, int h) {
        mark(FloorLayout.Mark.BELL, id, (int) Math.floor(cx - r), lip, (int) Math.floor(cz - r), (int) Math.ceil(cx + r) - 1, lip + h - 1,
                (int) Math.ceil(cz + r) - 1);
    }

    /** Swinging bells a floor holds at most (display entities, SPEC §38.6), and the biggest that swings. */
    static final int PROPS = 32;
    static final double PROP_R = 3.2;
    private int props;

    /**
     * A hung bell (lip at absolute {@code lipY}, the floor's base {@code base}): while the floor has room for one and it's
     * small enough it's a prop the live layer swings (a {@code prop} mark, "bell:" + ten times its radius; only its
     * canon, chains and a light are carved), else it's carved whole. Either way it's a bell mark.
     */
    void hungBell(Sculpt s, String id, double cx, int lipY, int base, double cz, double R, int H, int chainTo,
                  BlockState bronze, BlockState lamp, BlockState chain) {
        bell(id, cx, lipY - base, cz, R, H);
        if (props >= PROPS || R > PROP_R) {
            BellPieces.bell(s, cx, lipY, cz, R, H, chainTo, bronze, lamp, chain, false);
            return;
        }
        props++;
        mark(FloorLayout.Mark.PROP, "bell:" + Math.round(R * 10), (int) Math.floor(cx - R), lipY - base, (int) Math.floor(cz - R),
                (int) Math.ceil(cx + R) - 1, lipY - base + H - 1, (int) Math.ceil(cz + R) - 1);
        BellPieces.hanger(s, cx, lipY, cz, H, chainTo, bronze, WorldMats.light(10), chain);
    }

    /**
     * A prop the live layer moves (a gear turning, a planet on its orbit: SPEC §38.11), sharing the floor's budget with the
     * swinging bells; false when the floor has no room left for one.
     */
    boolean prop(String id, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (props >= PROPS) return false;
        props++;
        mark(FloorLayout.Mark.PROP, id, x0, y0, z0, x1, y1, z1);
        return true;
    }

    /** A handbell waiting for {@link #handbells}: where it's wanted, its turned state, the secret it keeps and that box. */
    private record Hand(int base, int x, int h, int z, BlockState state, String secret, int[] with, boolean touch) {}

    private final List<Hand> hands = new ArrayList<>();

    /** A handbell in its yoke on the floor at (x, h above the base, z), set by {@link #handbells}; {@code face} turns it. */
    void handbell(BlockState hb, int base, int x, int h, int z, net.minecraft.core.Direction face) {
        handbell(hb, base, x, h, z, face, "", null);
    }

    /**
     * One that keeps a secret: the secret's mark spans where the bell ends up and the box {@code with} (x0 y0 z0 x1 y1 z1,
     * y above the base: a niche it opens), or the bell alone when that's null.
     */
    void handbell(BlockState hb, int base, int x, int h, int z, net.minecraft.core.Direction face, String secret, int[] with) {
        hands.add(new Hand(base, x, h, z, WorldMats.facing(hb, face), secret, with, false));
    }

    /**
     * A world's rule block (a touchstone: SPEC §38.10) set like a handbell, marked {@code touch}; one that keeps a secret
     * marks it as a handbell does.
     */
    void touch(BlockState st, int base, int x, int h, int z, String secret, int[] with) {
        hands.add(new Hand(base, x, h, z, st, secret, with, true));
    }

    /**
     * Sets the floor's handbells, last of its carve (call it just before sealing) so nothing after cuts one away: each where
     * it was wanted, else the nearest spot within two blocks on its level, that's open with head room over solid floor
     * and clear of every doorway (the thresholds open those boxes, bars close them). One with nowhere to stand isn't set
     * or marked. Features keep a block clear of them ({@link #finish}).
     */
    void handbells(Sculpt s) {
        for (Hand hd : hands) {
            int[] at = handSpot(s, hd);
            if (at == null) continue;
            if (hd.touch()) s.putOverGrowth(at[0], hd.base() + hd.h(), at[1], hd.state());
            else s.putOpen(at[0], hd.base() + hd.h(), at[1], hd.state());
            if (hd.touch()) mark(FloorLayout.Mark.TOUCH, "", at[0], hd.h(), at[1], at[0], hd.h(), at[1]);
            else mark(FloorLayout.Mark.BELL, FloorLayout.Mark.HANDBELL, at[0], hd.h(), at[1], at[0], hd.h(), at[1]);
            if (hd.secret().isEmpty()) continue;
            int[] w = hd.with() != null ? hd.with() : new int[]{at[0], hd.h(), at[1], at[0], hd.h(), at[1]};
            mark(FloorLayout.Mark.SECRET, hd.secret(), Math.min(at[0], Math.min(w[0], w[3])), Math.min(hd.h(), Math.min(w[1], w[4])),
                    Math.min(at[1], Math.min(w[2], w[5])), Math.max(at[0], Math.max(w[0], w[3])), Math.max(hd.h(), Math.max(w[1], w[4])),
                    Math.max(at[1], Math.max(w[2], w[5])));
        }
        hands.clear();
    }

    private int[] handSpot(Sculpt s, Hand hd) {
        int y = hd.base() + hd.h();
        for (int r = 0; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = hd.x() + dx, z = hd.z() + dz;
                    if (inDoorway(x, hd.h(), z)) continue;
                    if (s.probe(x, y, z).get(x, y, z) != Sculpt.OPEN || s.probe(x, y + 1, z).get(x, y + 1, z) != Sculpt.OPEN) continue;
                    Sculpt.Buf under = s.probe(x, y - 1, z);
                    BlockState st = under.state(x, y - 1, z);
                    if (under.get(x, y - 1, z) == Sculpt.SOLID || (st != null && st.isSolid() && !under.water(x, y - 1, z))) return new int[]{x, z};
                }
            }
        }
        return null;
    }

    /** Inside a doorway's box (as {@link #thresholds} opens it), at its height (h above the base). */
    private boolean inDoorway(int x, int h, int z) {
        for (Room w : b.rooms) {
            for (Room.Door d : w.doors) {
                if (h < d.y - 1 || h > d.y + RoomGeometry.DOOR_HEIGHT) continue;
                int[] box = doorBox(d, Sculpt.SHELL);
                if (x >= box[0] && x <= box[1] && z >= box[2] && z <= box[3]) return true;
            }
        }
        return false;
    }

    /** A secret's mark: the box where it's found (a handbell to strike, a rope to climb), {@code id} the recipe's secret. */
    void secret(String id, int x0, int y0, int z0, int x1, int y1, int z1) {
        mark(FloorLayout.Mark.SECRET, id, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

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
                w.space = L.space;
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
            // a feature stands a block clear of every handbell (a vault's bars would close round one)
            for (FloorLayout.Mark m : marks) {
                if (!m.id().equals(FloorLayout.Mark.HANDBELL) && !m.kind().equals(FloorLayout.Mark.TOUCH)) continue;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) taken.add(((long) (m.x0() + dx) << 32) ^ ((m.z0() + dz) & 0xFFFFFFFFL));
            }
            for (Room L : ls) {
                for (Room.Feature ft : L.features) {
                    int x = clamp(a[0] + ft.x() - L.centerX(), w.x0(), w.x1()), z = clamp(a[1] + ft.z() - L.centerZ(), w.z0(), w.z1());
                    int[] q = ground(w, walk, x, z, taken, ctx.plan().contentVersion() >= 7, high);
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
                // the Gravity Wells measure the room's height (38.12), so they're planned again at the programme room's
                int h0 = w.height;
                if (L.rig.kind == RigRoom.Kind.GRAVITY_WELLS) w.height = L.height;
                w.rig = RigRooms.rebuild(b, w, L.rig.kind);
                w.height = w.rig != null ? L.height : h0;
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
        FloorLayout f = new FloorLayout(ctx.area(), ctx.floor(), look.baseY(), plot.slotX(), plot.slotZ(), b.w, b.h, ctx.theme(), logic.bossFloor,
                b.rooms, b.start, b.end, new FloorLayout.Frame(plot, plot.minY(), plot.maxY(), null, ctx.worldId(), ctx.mood(), look.biome(), look.air(), look.model(), look.yaw(), placeWorlds, placeBiomes, marks, look.sky()));
        f.ground = ground;
        f.high = high;
        return f;
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
    private static int[] ground(Room w, Walk walk, int x, int z, java.util.Set<Long> taken, boolean clear, java.util.BitSet high) {
        // shaped ground (content 7) can leave no level spot near: then the nearest free one (never up on a high ledge),
        // so a vault's bars never close round a touchstone or a handbell
        for (int pass = 0; pass < (clear ? 2 : 1); pass++) {
            for (int r = 0; r <= 10; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        int qx = x + dx, qz = z + dz;
                        if (qx < w.x0() || qx > w.x1() || qz < w.z0() || qz > w.z1()) continue;
                        if ((pass == 1 ? high == null || qx < 0 || qz < 0 || qx >= Plots.SIDE || qz >= Plots.SIDE || !high.get(qx + qz * Plots.SIDE) : walk.ok(w, qx, qz))
                                && !taken.contains(((long) qx << 32) ^ (qz & 0xFFFFFFFFL))) return new int[]{qx, qz};
                    }
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
    /** A sculpted floor, its carve passed through a rare world's {@link Tint} when it has one (38.13). */
    record SculptModel(Sculpt sculpt, Tint tint) implements WorldModel {
        SculptModel(Sculpt sculpt) {
            this(sculpt, null);
        }

        @Override
        public void carve(GatePlan plan, FloorLayout f, int slotX, int slotZ, GatePainter.Sink out) {
            int gx = slotX - f.originSlotX, gz = slotZ - f.originSlotZ;
            Room r = f.roomAtSlot(gx, gz);
            GatePainter.Sink to = tint == null ? out : (x, y, z, s) -> out.set(x, y, z, tint.at(gx * 16 + x, y, gz * 16 + z, s));
            if (r != null && built(r)) {
                int floorY = f.baseY + r.dy;
                sculpt.carve(gx, gz, to, floorY - Cells.BAND_BELOW, floorY + Cells.BAND_ABOVE - 1);
                GatePainter.paintBuilt(plan, f, r, gx, gz, out);
                return;
            }
            sculpt.carve(gx, gz, to, 1, 0);
            if (r != null) GatePainter.paintFeatures(plan, f, r, gx, gz, out);
        }
    }
}
