package dev.awaken.gates.world;

import dev.awaken.gates.content.Theme;
import dev.awaken.gates.worlds.GateWorld;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * What a theme grows in the cave engine's caves (SPEC §38.10): its stone by face, what hangs from its roofs and stands on
 * its floors, its hero pieces, its touchstones, nooks and secret place, its screens and its mist. One per theme; the
 * helpers here are shared. Every placement is a pure function of the floor's seed, so a floor always dresses the same.
 */
abstract class Dress {
    final CaveEngine.Cave c;
    final WorldPlan p;
    final int W;
    final long seed;
    final WorldMats m;
    /** Whether a touchstone already keeps the theme's touch secret on this floor. */
    boolean touchKept;

    Dress(CaveEngine.Cave c) {
        this.c = c;
        this.p = c.p;
        this.W = c.W;
        this.seed = c.seed;
        this.m = c.m;
    }

    static Dress of(CaveEngine.Cave c) {
        if (c.borrowed != null) {
            Dress in = of(c, c.borrowed);
            String n = rareName(c.c.worldId());
            return n.equals("the_reflection") || n.equals("the_other_gate") ? new BorrowedDress(c, in, true) : new TwistDress(c, in);
        }
        if (c.world != null && c.world.tier() == GateWorld.Tier.RARE) return new RareDress(c);
        return of(c, c.c.theme());
    }

    static Dress of(CaveEngine.Cave c, Theme t) {
        return switch (t) {
            case EMBER_MINES -> new EmberDress(c);
            case HIVE -> new HiveDress(c);
            case OVERGROWN_TEMPLE -> new TempleDress(c);
            case DROWNED_ARCHIVE -> new ArchiveDress(c);
            case CLOCKWORK_FOUNDRY -> new FoundryDress(c);
            case GLASS_DESERT -> new DesertDress(c);
            case LANTERN_NIGHT_MARKET -> new MarketDress(c);
            case FROST_MONASTERY -> new FrostDress(c);
            case MIRROR_PALACE -> new PalaceDress(c);
            case PRECURSOR_LAB -> new LabDress(c);
            case NIGHTMARE -> new DreamDress(c);
            case SKY_ARCHIPELAGO -> new SkyDress(c);
            case ABYSSAL_SHRINE -> new AbyssDress(c);
            default -> new FungalDress(c);
        };
    }

    /** The cave themes a rare world can borrow a look from (38.13): every theme whose everyday worlds are caves. */
    static final List<Theme> BORROWS = List.of(Theme.FUNGAL_HOLLOW, Theme.EMBER_MINES, Theme.HIVE, Theme.OVERGROWN_TEMPLE,
            Theme.DROWNED_ARCHIVE, Theme.CLOCKWORK_FOUNDRY, Theme.MIRROR_PALACE, Theme.PRECURSOR_LAB, Theme.NIGHTMARE);

    /** The rare worlds that are a theme's world with something wrong in it (38.13), and the world each one wears. */
    static final java.util.Map<String, String> TWISTS = java.util.Map.of(
            "the_hollow_sky", "awaken:sky_archipelago/the_isles",
            "the_endless_stair", "awaken:abyssal_shrine/dark_steps",
            "the_gates_heart", "awaken:abyssal_shrine/starfall",
            "the_tide_room", "awaken:drowned_archive/atrium",
            "the_library_of_doors", "awaken:drowned_archive/stacks",
            "the_dollhouse", "awaken:nightmare/the_house",
            "the_glass_sea", "awaken:frost_monastery/still_snow");

    /** A world's name after its folder ("the_reflection"). */
    static String rareName(String worldId) {
        return worldId == null ? "" : worldId.substring(worldId.indexOf('/') + 1);
    }

    /**
     * The world whose look a rare world wears (38.13), or null when it has its own: the Reflection is the gate you came in
     * by, greyed (the Mirror Palace's when that gate isn't a cave theme); the Other Gate is another cave theme, its colours
     * turned, picked by the floor's seed; the twisted ones wear one world each.
     */
    static String look(String worldId, GateWorld w, Theme gate, long seed) {
        if (w == null || w.tier() != GateWorld.Tier.RARE || worldId == null) return null;
        String n = rareName(worldId);
        if (n.equals("the_reflection")) return source(BORROWS.contains(gate) ? gate : Theme.MIRROR_PALACE);
        if (n.equals("the_other_gate")) {
            List<Theme> l = new ArrayList<>(BORROWS);
            l.remove(gate);
            return source(l.get((int) (Hash.unit(seed, 13, 77) * l.size())));
        }
        return TWISTS.get(n);
    }

    /** The world a borrowed look reads its recipe from: the theme's first everyday cave world, or null. */
    static String source(Theme t) {
        List<String> ids = new ArrayList<>(dev.awaken.gates.worlds.GateWorlds.ofTheme(t));
        java.util.Collections.sort(ids);
        for (String id : ids) {
            GateWorld w = dev.awaken.gates.worlds.GateWorlds.get(id);
            if (w != null && w.tier() == GateWorld.Tier.EVERYDAY && w.kind().equals("caves")) return id;
        }
        return null;
    }

    // ---- hooks --------------------------------------------------------------------------------------------------------

    /** Before the chain is laid. */
    void programme() {}

    /** A shell block: floor (open above), ceiling (open below) or wall. */
    abstract BlockState skin(int x, int y, int z, boolean floor, boolean ceiling);

    /**
     * An open land's skyline over a point (38.11): the height of the land's top block above the floor (dunes, roofs, a
     * buried town's towers). The engine keeps it at least one under the lid.
     */
    int skyline(int x, int z) {
        return c.lid + 2 + (int) (Hash.noise(seed + 77, x, z, 18) * 8);
    }

    /** The rail along a bridge or a roof's edge over a street (38.11). */
    BlockState rail() {
        return GatePainter.state("minecraft:dark_oak_fence");
    }

    /** What every carved block passes through on its way out (38.13, a rare world's colours), or null. */
    Tint tint() {
        return null;
    }

    /** The glass floor over a mirrored underfloor (38.12), or null for none. */
    BlockState underGlass() {
        return null;
    }

    /** Whether a floor block of a place can be stood on as dressed (a maze's walls can't): spawn spots keep to these. */
    boolean walk(Room r, int x, int z) {
        return true;
    }

    /** After every place is carved, before each is dressed. */
    void world() {}

    /** Whether a version 7 floor shapes its places' ground (SPEC §38.14, {@link Relief}). */
    boolean depth() {
        return false;
    }

    /**
     * A piece of shaped ground at a block ({@link Relief#BANK} under a raised floor, {@link Relief#SLAB} or
     * {@link Relief#STAIRS} on a half step, a {@link Relief#DRIFT} at the foot of a rise, {@link Relief#BITS} lying on it),
     * or null: the bank skinned like the shell, no half step, nothing lying there.
     */
    BlockState ground(int part, int x, int y, int z) {
        return null;
    }

    /** How far round a place's middle its ground stays flat (something stands or lies there: a pool, a hero piece). */
    double pad(Room r, CaveEngine.Kind k) {
        return 0;
    }

    /** How thickly the bits lie on shaped ground. */
    double bits() {
        return 0;
    }

    /** The most room over a column of a place, blocks over the floor line, where the dress hangs something low (a ledge). */
    double headroom(Room r, CaveEngine.Kind k, int x, int z) {
        return 999;
    }

    void cavern(Room r, CaveEngine.Kind k) {}

    void tunnel(Room r, ChainLayout.Link l) {}

    /** The theme's secret world, a place of its own off a calm cavern. */
    void secret(Room r) {}

    abstract BlockState screen(int lat, int rise, int side);

    abstract List<FloorAir.Mist> mists();

    // ---- what the recipe says -----------------------------------------------------------------------------------------

    boolean hero(String id) {
        return c.hero(id);
    }

    /** The world the dress reads its look from: the floor's own, or the one a borrowed look comes from (38.13). */
    String worldId() {
        return c.lookId == null ? "" : c.lookId;
    }

    /** The world's name after its theme ("glowing_deep"), for a dress with a look per world. */
    String name() {
        String id = worldId();
        int i = id.indexOf('/');
        return i < 0 ? id : id.substring(i + 1);
    }

    /** Whether the world, or the secret world off it, keeps a secret by this id. */
    boolean has(String secret) {
        if (keeps(c.world, secret)) return true;
        String inner = c.secret >= 0 ? p.placeWorlds.get(c.secret) : null;
        return inner != null && keeps(dev.awaken.gates.worlds.GateWorlds.get(inner), secret);
    }

    static boolean keeps(GateWorld w, String secret) {
        if (w == null) return false;
        for (GateWorld.Secret s : w.dressing().secrets()) if (s.id().equals(secret)) return true;
        return false;
    }

    BlockState mat(String role, String fallback) {
        return m.get(role, fallback);
    }

    // ---- geometry -----------------------------------------------------------------------------------------------------

    static int[] box(Room r) {
        return new int[]{r.gx * 16, r.gz * 16, (r.gx + r.gw) * 16 - 1, (r.gz + r.gh) * 16 - 1};
    }

    double unit(int id, int salt) {
        return Hash.unit(seed, id, salt);
    }

    /** The highest open block of a cavern's column (its roof just above), as the carve leaves it at the field. */
    int top(int id, int x, int z) {
        return W + (int) Math.ceil(c.ceil(id, x + 0.5, z + 0.5) - 0.5) - 1;
    }

    /** A point inside a place between {@code q0} and {@code q1} of the way from its middle to its wall, clear of its doorways. */
    double[] spot(Room r, int salt, double q0, double q1, double clear) {
        return spot(r, salt, q0, q1, clear, clear + 3);
    }

    /** As {@link #spot(Room, int, double, double, double)}, keeping {@code doorClear} off the mouths. */
    double[] spot(Room r, int salt, double q0, double q1, double clear, double doorClear) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return null;
        for (int t = 0; t < 24; t++) {
            double a = Hash.unit(seed, r.id * 31 + salt, t) * Math.PI * 2, q = q0 + (q1 - q0) * Hash.unit(seed, r.id * 37 + salt, t + 50);
            double x = sh.cx() + Math.cos(a) * sh.a() * q, z = sh.cz() + Math.sin(a) * sh.b() * q;
            if (c.d(r.id, x, z) > -clear || c.inLake(r.id, x, z) || nearDoor(r, x, z, doorClear) || nearBlocked(x, z, clear)) continue;
            return new double[]{x, z};
        }
        return null;
    }

    /** Within {@code dist} of the way from a doorway to the place's middle (the mouth every cavern keeps open). */
    boolean nearDoor(Room r, double x, double z, double dist) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        for (Room.Door dr : r.doors) {
            double ax = dr.centerX() + 0.5, az = dr.centerZ() + 0.5;
            double bx = sh == null ? ax : ax + (sh.cx() - ax) * 0.7, bz = sh == null ? az : az + (sh.cz() - az) * 0.7;
            double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / l2));
            if (Math.hypot(x - (ax + vx * t), z - (az + vz * t)) < dist) return true;
        }
        return false;
    }

    /** In the middle of a doorway (the two blocks the way runs through), within {@code depth} blocks of its slot edge. */
    static boolean inDoorway(Room r, int x, int z, int depth) {
        for (Room.Door d : r.doors) {
            int[] b = WorldPlan.doorBox(d, depth);
            if (x < b[0] || x > b[1] || z < b[2] || z > b[3]) continue;
            int lat = d.side == 0 || d.side == 2 ? x - d.gx * 16 : z - d.gz * 16;
            if (lat > RoomGeometry.DOOR_LO && lat < RoomGeometry.DOOR_HI) return true;
        }
        return false;
    }

    boolean nearBlocked(double x, double z, double dist) {
        for (double[] b : c.blocked) if (Math.hypot(x - b[0], z - b[1]) < b[2] + dist) return true;
        return false;
    }

    void block(double x, double z, double r) {
        c.blocked.add(new double[]{x, z, r});
    }

    void light(double x, int y, double z, int rgb, float radius, float strength) {
        c.lights.add(new FloorAir.Light((int) Math.floor(x), y, (int) Math.floor(z), rgb, radius, strength));
    }

    // ---- growth --------------------------------------------------------------------------------------------------------

    /** Solid where a block would hang from or stand on (the shell, or a solid block set there). */
    static boolean solid(Sculpt.Buf b, int x, int y, int z) {
        short v = b.get(x, y, z);
        if (v == Sculpt.SOLID) return true;
        BlockState s = b.state(x, y, z);
        return s != null && s.isSolid();
    }

    interface Pick {
        BlockState at(int x, int y, int z, int len, int i);
    }

    /**
     * Things hanging from a place's roof over its box (roots, drips, vines): where the roof is, by {@code density}, each
     * up to {@code most} long, never lower than {@code low} above the floor.
     */
    void hang(Room r, double density, int most, int low, int salt, Pick pick) {
        int[] b = box(r);
        int hi = W + c.roofs.getOrDefault(r.id, c.roof) + 3;
        c.s.op(b[0], W + low, b[1], b[2], hi, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z)) return;
            long h = Hash.of(seed + salt, x, z);
            if (Hash.unit(h) >= density) return;
            int len = 1 + (int) ((Hash.unit(Hash.mix(h)) * most));
            for (int i = 0; i < len && y - i >= W + low; i++) {
                if (!buf.open(x, y - i, z)) break;
                buf.set(x, y - i, z, pick.at(x, y - i, z, len, i));
            }
        });
    }

    /** Things standing on a place's floor (plants, threads), by {@code density}, off the doorways' mouths. */
    void grow(Room r, double density, int salt, Pick pick) {
        int[] b = box(r);
        c.s.op(b[0], W, b[1], b[2], W, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y - 1, z) || !buf.open(x, y + 1, z)) return;
            long h = Hash.of(seed + salt, x, z);
            if (Hash.unit(h) >= density || nearDoor(r, x + 0.5, z + 0.5, 2.5) || p.nearAnchor(x + 0.5, z + 0.5, 3)) return;
            BlockState s = pick.at(x, y, z, 1, (int) (Hash.mix(h) & 7));
            if (s != null) buf.set(x, y, z, s);
        });
    }

    /**
     * A giant growth standing in a place: a stem {@code sr} across from the floor to {@code h} up, and a cap {@code cr}
     * wide domed over it whose underside is {@code under} (gills). The stem is kept clear of the floor's spawn spots.
     */
    void mushroom(double cx, double cz, int h, double sr, double cr, BlockState stem, BlockState cap, BlockState under, int lean) {
        double lx = (Hash.unit(seed, (int) cx, (int) cz) - 0.5) * lean, lz = (Hash.unit(seed, (int) cz, (int) cx) - 0.5) * lean;
        int r0 = (int) Math.ceil(Math.max(sr, cr)) + Math.abs(lean) + 2;
        int x0 = (int) cx - r0, x1 = (int) cx + r0, z0 = (int) cz - r0, z1 = (int) cz + r0;
        int capY = W + h;
        double ch = Math.max(2.5, cr * 0.42);
        c.s.op(x0, W - 1, z0, x1, (int) (capY + ch + 1), z1, (b, x, y, z) -> {
            double t = (y - W) / (double) h;
            double sx = cx + lx * t * t, sz = cz + lz * t * t;
            double px = x + 0.5, pz = z + 0.5;
            // the stem, flaring a little at its foot
            double rr = sr * (1 + Math.max(0, 0.6 - t) * 0.8);
            if (y < capY && Math.hypot(px - sx, pz - sz) < rr) {
                b.set(x, y, z, stem);
                return;
            }
            // the cap: a dome over (cx + lean, capY), its underside a layer of gills
            double ex = cx + lx, ez = cz + lz;
            double u = Math.hypot(px - ex, pz - ez) / cr, v = (y + 0.5 - capY) / ch;
            if (v < -0.15 || u * u + v * v > 1) return;
            if (v < 0.25 && u < 0.92) b.set(x, y, z, under);
            else b.set(x, y, z, cap);
        });
        block(cx, cz, sr + 1);
    }

    /** A pillar of {@code st} from the floor to the roof (or {@code h} up), {@code r} across. */
    void pillar(double cx, double cz, double r, int h, BlockState st) {
        int r0 = (int) Math.ceil(r) + 1;
        c.s.op((int) cx - r0, W, (int) cz - r0, (int) cx + r0, W + h, (int) cz + r0, (b, x, y, z) -> {
            if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) < r && (b.open(x, y, z) || b.get(x, y, z) == Sculpt.UNSET)) b.set(x, y, z, st);
        });
        block(cx, cz, r + 0.5);
    }

    /**
     * The touchstones of a place: {@code n} of the theme's rule block on its floor. The first that stands in a calm place
     * keeps {@code secret} for the floor (one per floor). A small cavern whose mouths cross it gets its stone nearer them.
     */
    void touchstones(Room r, BlockState st, int n, String secret, int salt) {
        // the gate within stands four from its room's middle, set after the carve: a touchstone there would be lost
        if (r.type == Room.Type.INNER_GATE) return;
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, salt + i * 7, 0.35, 0.75, 2.5, 5.5);
            if (at == null) at = spot(r, salt + i * 7 + 3, 0.2, 0.8, 1.5, 2.5);
            if (at == null) continue;
            boolean keeps = !touchKept && secret != null && !secret.isEmpty() && !p.sealable(r) && has(secret);
            touchKept |= keeps;
            p.touch(st, W, (int) Math.floor(at[0]), 0, (int) Math.floor(at[1]), keeps ? secret : "", null);
            light(at[0], 1, at[1], glow(), 6f, 0.5f);
        }
    }

    /** The colour the theme's light glows (its touchstones, its trail). */
    abstract int glow();

    /** A hidden nook: open ground (a box, y above the base) whose secret is found by standing in it. */
    void nook(String secret, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (!has(secret)) return;
        open(x0, W + y0, z0, x1, W + y1, z1);
        p.mark(FloorLayout.Mark.NOOK, secret, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

    /**
     * A nook dug into a place's wall where it bulges out most, a short crawl in from the floor: the way in is two high,
     * the nook three deep; returns its middle or null when the place has no wall to dig into.
     */
    int[] wallNook(Room r, String secret, int salt) {
        if (!has(secret)) return null;
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return null;
        for (int t = 0; t < 16; t++) {
            double a = Hash.unit(seed, r.id + salt, t) * Math.PI * 2;
            double dx = Math.cos(a), dz = Math.sin(a);
            // walk out from the middle to the wall
            double x = sh.cx(), z = sh.cz();
            int steps = 0;
            while (c.d(r.id, x, z) < -1 && steps++ < 80) {
                x += dx;
                z += dz;
            }
            if (nearDoor(r, x, z, 5) || !CaveEngine.Cave.insideSlots(r, (int) (x + dx * 4), (int) (z + dz * 4))) continue;
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            int ex = (int) Math.floor(x + dx * 4), ez = (int) Math.floor(z + dz * 4);
            // the crawl in, two high, then a room three across (carved, so it keeps a shell like the rest)
            double ax = x - dx, az = z - dz;
            c.s.carve((int) Math.min(ax, ex) - 2, W, (int) Math.min(az, ez) - 2, (int) Math.max(ax, ex) + 2, W + 2, (int) Math.max(az, ez) + 2,
                    (px, py, pz) -> Math.max(Sculpt.capsule(px, W + 1.0, pz, ax, W + 1.0, az, ex + 0.5, W + 1.0, ez + 0.5, 0.75), W - py));
            open(ex - 1, W, ez - 1, ex + 1, W + 2, ez + 1);
            p.mark(FloorLayout.Mark.NOOK, secret, ex - 1, 0, ez - 1, ex + 1, 2, ez + 1);
            return new int[]{ex, ez, ix, iz};
        }
        return null;
    }

    /** Carves a box of blocks open, a shell kept round it. */
    void open(int x0, int y0, int z0, int x1, int y1, int z1) {
        c.s.carve(x0, y0, z0, x1, y1, z1, (x, y, z) -> Sculpt.box(x, y, z, x0, y0, z0, x1 + 1, y1 + 1, z1 + 1));
    }

    /** The theme's mist, deeper in the world's lower air. */
    static List<FloorAir.Mist> mist(int rgb, float density, int rgb2, float density2) {
        List<FloorAir.Mist> l = new ArrayList<>();
        l.add(new FloorAir.Mist(0, 2, rgb, density));
        l.add(new FloorAir.Mist(7, 7, rgb2, density2));
        return l;
    }
}
