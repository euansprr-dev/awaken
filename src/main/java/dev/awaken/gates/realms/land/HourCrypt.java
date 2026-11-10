package dev.awaken.gates.realms.land;

import dev.awaken.gates.block.ThemeBlocks;
import dev.awaken.gates.block.WorldBlocks;
import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.KitLand;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.Build.stair;
import static dev.awaken.gates.realms.kit.Build.top;
import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Hour Crypt (SPEC 50.8): a crypt dug round one enormous bell. You arrive at the head of a stair with the nave falling away in front of you and
 * the bell hanging past the end of it, and the floor you'll fight the Lord on is the round pit under it, shut behind iron until the three Wardens are
 * down. Every wall is tiers of niches, and every minute the bell tolls and the doors in the crypt drop for as long as it rings.
 */
public final class HourCrypt extends KitLand<HourCrypt.M> {
    /** The nave's floor, and the Lord's floor eight blocks under it. */
    static final int F = 72, PIT = F - 8;
    static final int ROCK_BOTTOM = 0, ROCK_TOP = F + 124, HALF = 224;
    static final int BAY = 6, TIER = 6, TIERS = 11, DEPTH = 3;
    /** The walls: the nave's half width and the round drum's radius at the floor; both step back a block a tier as they rise. */
    static final int NAVE = 30, DRUM = 46, DRUM_TOP = DRUM + TIERS, HEAD_Z = -112;
    static final double PIT_R = 29.2, RAIL_R = 29.8;
    /**
     * The great bell's tongue isn't painted: it swings, so the Realm hangs it as block displays ({@code RealmClapper}). The middle of its ball in the
     * land's own blocks, how far above that its pivot is, and how far above that the yoke.
     */
    public static final int CLAPPER_X = 0, CLAPPER_Y = F + 16, CLAPPER_Z = 0, CLAPPER_DROP = 44, CLAPPER_YOKE = 56;
    private static final BlockState PIT_GLOW = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 13);
    private static final BlockState POOL_GLOW = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15);

    public static final class M extends Mats {
        final BlockState bronze = b("knell_bronze"), bronzeDark = b("knell_bronze_dark"), patina = b("knell_patina"), key = b("knell_bronze_key"), iron = b("knell_iron"),
                ossuary = b("gate_ossuary"), bones = b("crypt_bones"), niche = b("crypt_niche"), lily = b("crypt_lily_candle"), dust = b("crypt_dust"), rope = b("crypt_bell_rope"),
                smallBell = b("gate_bell"), bars = Blocks.IRON_BARS.defaultBlockState(), link = b("knell_link"), linkX = link.setValue(ThemeBlocks.CarvedAxis.AXIS, Direction.Axis.X),
                arch = b("knell_arch"), flute = b("knell_flute"), rib = b("knell_rib"), ribX = rib.setValue(ThemeBlocks.CarvedAxis.AXIS, Direction.Axis.X), corbel = b("knell_corbel"), timberX = timber.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        private final BlockState[] candles = new BlockState[6];

        M() {
            super("knell", new Accents(b("gate_ossuary"), b("crypt_tracery"), b("knell_window"), b("crypt_bell_rope"), b("crypt_candles").setValue(WorldBlocks.Candles.CANDLES, 3), b("crypt_bones"),
                    b("crypt_handbell"), Blocks.IRON_BARS.defaultBlockState(), b("knell_timber")));
            for (int i = 1; i <= 5; i++) candles[i] = b("crypt_candles").setValue(WorldBlocks.Candles.CANDLES, i);
        }

        BlockState candles(int n) {
            return candles[Math.max(1, Math.min(5, n))];
        }

        /** One link of a chain; every other one is turned a quarter, as links are. */
        BlockState link(int n) {
            return (n & 1) == 0 ? link : linkX;
        }

        /** A carved stone turned to face a way: an arch stone faces its opening, a flute and a corbel face the room. */
        BlockState facing(BlockState carved, Direction to) {
            return carved.setValue(ThemeBlocks.CarvedFacing.FACING, to);
        }

        /** A vault rib running along an axis. */
        BlockState rib(Direction.Axis along) {
            return along == Direction.Axis.X ? ribX : rib;
        }

        BlockState barsAlong(boolean ew, boolean ns) {
            return bars.setValue(IronBarsBlock.EAST, ew).setValue(IronBarsBlock.WEST, ew).setValue(IronBarsBlock.NORTH, ns).setValue(IronBarsBlock.SOUTH, ns);
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (HourCrypt.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    @Override
    protected M mats() {
        return m();
    }

    /** A room knows the shape of its floor, so the game can stand things on it. */
    abstract static class Room extends Piece<M> {
        abstract boolean has(int x, int z);

        abstract int floor(int x, int z);
    }

    final SimplexNoise mottle;
    private final List<Room> rooms = new ArrayList<>();
    private final List<Edit> grille = new ArrayList<>();
    static final int BALC = F + 64;

    public HourCrypt(Ctx ctx) {
        super(ctx, 0x4B4E454C4CL);
        mottle = new SimplexNoise(new LegacyRandomSource(seed ^ 0x51L));
        room(new Drum());
        room(new Nave());
        // the Ossuary, east: a low gallery of bones, a side aisle crossing it, and the round room at its end
        room(new Hall(true, 42, 56, 0, 5, F, 1, 41, 0, false));
        room(new Hall(true, 56, 128, 0, 10, F, 3, 43, 1, false));
        room(new Hall(false, -30, 38, 92, 7, F, 2, 45, 1, false));
        // the reliquary, at the south end of the Ossuary's cross aisle, behind the one door that stands open only while the bell rings
        room(new Round(92, 48, 9, F, 2, 67, 1, false));
        room(new Hall(true, 128, 136, 0, 5, F, 1, 47, 1, false));
        room(new Round(156, 0, 21, F, 4, 49, 1, false));
        // the Sunk Aisle, west: a stair down, a flooded hall with the moon coming through the roof, and a drowned round room
        room(new Hall(true, -56, -42, 0, 5, F, 1, 51, 0, false));
        room(new Steps(-90, -56, 0, 8, F - 14, F));
        room(new Hall(true, -150, -90, 0, 13, F - 14, 2, 53, 2, true));
        room(new Hall(true, -158, -150, 0, 5, F - 14, 1, 55, 2, true));
        room(new Round(-178, 0, 21, F - 14, 3, 57, 2, true));
        // the way up, south: a throat through the wall and the tall square tower of the ringers
        room(new Hall(false, 42, 62, 0, 5, F, 1, 61, 0, false));
        room(new Tower());
        // the Candle-Keepers' cloister, off the nave
        room(new Hall(true, -46, -28, -66, 4, F, 1, 63, 0, false));
        room(new Hall(true, -96, -46, -66, 18, F, 2, 65, 3, false));
        // where a wing's wall meets the room at its end, a way through
        pieces.add(new Cut(128, 140, -4, 4, F + 1, F + 5, -1));
        pieces.add(new Cut(-162, -150, -4, 4, F - 13, F - 9, -1));
        pieces.add(new Cut(-4, 4, 50, 62, F + 1, F + 5, -1));
        pieces.add(new Cut(-3, 3, 50, 62, BALC + 1, BALC + 5, BALC));
        pieces.add(new Cut(89, 95, 36, 42, F + 1, F + 5, -1));
        for (int[] st : new int[][]{{-58, -76}, {-58, -56}, {-82, -76}, {-82, -56}}) pieces.add(new dev.awaken.gates.realms.kit.piece.Stall(st[0], st[1], seed + st[0], this));
        pieces.add(new Balcony());
        pieces.add(new GreatBell());
        door(new TollDoor(true, 50, 0, 4, F));
        door(new TollDoor(true, 132, 0, 4, F));
        door(new TollDoor(true, -50, 0, 4, F));
        door(new TollDoor(true, -154, 0, 4, F - 14));
        door(new TollDoor(false, 0, 52, 4, F));
        door(new TollDoor(false, 0, 62, 3, BALC));
        for (Piece<? super M> p : List.copyOf(pieces)) if (p instanceof TollDoor) pieces.remove(p);
        pieces.addAll(doors);
        pieces.add(relic);
        relic.edits(relicShut, false);
        grilles();

        site(SiteKind.MOUTH, 0, F + 11, -106, 12, "");
        site(SiteKind.PORTAL, 0, F + 11, -111, 2, "");
        site(SiteKind.LORD, 0, PIT + 2, 0, 28, ctx.bp().heart().lord());
        site(SiteKind.WARDEN, 156, F + 2, 0, 16, "bones");
        site(SiteKind.WARDEN, -178, F - 13, 0, 16, "water");
        site(SiteKind.WARDEN, 0, BALC + 1, 92, 14, "ropes");
        site(SiteKind.VILLAGE, -70, F + 1, -66, 26, "");
        for (int[] d : DENS) site(SiteKind.DEN, d[0], ground(d[0], d[1]) + 1, d[1], 10, "");
        waystone(16, -66, "the foot of the stair");
        waystone(92, 0, "the crossing in the Ossuary");
        waystone(-120, -9, "the flooded aisle");
        finds();
        clearSites();
        run();
    }

    /** Where packs are: the nave, the Ossuary, its north chapel (an ambush), the Sunk Aisle, and its far bays (an ambush). */
    static final int[][] DENS = {{0, -66}, {112, 0}, {92, -22}, {-112, 0}, {-138, 0}};
    /** The stone teeth across the Ossuary's gallery: where each row stands along the hall. */
    static final int[] TEETH = {65, 69, 73};
    /** Where the ringers' rope comes down from the bridge to the bell, once the Warden of the ropes has fallen. */
    static final int ROPE_X = 2, ROPE_Z = 40;

    /**
     * The run (SPEC §50.9). The nave, then the walk round the pit, and three wings off it in any order: east through the
     * teeth to the Ossuary and its Warden, west down into the water and its Warden, south up the broken stair of the
     * tower to the Warden among the ropes, whose fall lets a rope down from the bridge so nobody walks the tower twice.
     */
    private void run() {
        M m = m();
        List<String> none = List.of();
        leg("stair", LegKind.ARRIVE, "The stair head", 0, F + 11, -106, 12, -1, none, "", "nave");
        leg("nave", LegKind.FIGHT, "The nave", 0, F + 1, -66, 24, 0, List.of("toll_ghoul*3", "crypt_archer*2"), "", "drum");
        leg("drum", LegKind.BREATHER, "The walk round the pit", 0, F + 1, -38, 12, -1, none, "", "gallery", "sunk", "tower", "lord");
        leg("gallery", LegKind.TRAP, "The Ossuary gallery", 69, F + 1, 0, 10, -1, none, "", "ossuary");
        leg("ossuary", LegKind.FIGHT, "The Ossuary", 112, F + 1, 0, 12, 1, List.of("crypt_archer*3", "bell_wisp*2", "crypt_keeper"), "", "bones", "chapel");
        leg("chapel", LegKind.AMBUSH, "The north chapel", 92, F + 1, -22, 6, 2, List.of("bell_wisp*4", "toll_ghoul"), "", "reliquary");
        leg("reliquary", LegKind.HIDDEN, "The reliquary", 92, F + 2, 48, 7, -1, none, "", "ossuary");
        leg("bones", LegKind.WARDEN, "The round room of bones", 156, F + 2, 0, 18, 0, List.of("crypt_archer*3"), "", "drum");
        leg("sunk", LegKind.FIGHT, "The Sunk Aisle", -112, F - 14, 0, 13, 3, List.of("toll_ghoul*3", "bell_wisp*2"), "", "bays");
        leg("bays", LegKind.AMBUSH, "The drowned bays", -138, F - 14, 0, 9, 4, List.of("bell_wisp*3", "crypt_archer*2"), "", "water");
        leg("water", LegKind.WARDEN, "The drowned round room", -178, F - 13, 0, 18, 1, List.of("toll_ghoul*3"), "", "drum");
        leg("tower", LegKind.MOVEMENT, "The ringers' tower", 0, F + 1, Tower.CZ, 22, -1, none, "", "ropes");
        leg("ropes", LegKind.WARDEN, "The ringing chamber", 0, BALC + 1, 92, 16, 2, List.of("bell_wisp*4"), "", "balcony");
        leg("balcony", LegKind.SHORTCUT, "The ringers' rope", 0, BALC + 1, ROPE_Z, 4, -1, none, "warden2", "drum");
        leg("lord", LegKind.LORD, "Under the bell", 0, PIT + 2, 0, 28, -1, none, "");
        // three rows of teeth come up out of the gallery floor one after another, east to west and round again: there is always a gap to stand in
        BlockState air = Blocks.AIR.defaultBlockState();
        int[] phase = {0, 70, 40};
        for (int i = 0; i < TEETH.length; i++) trap(new Trap("gallery", TrapKind.PRESS, TEETH[i], F + 1, -9, TEETH[i], F + 3, 9, 100, phase[i], m.pale, air));
        // the tower's stair is broken in three places; these are its ledges, lowest first: the door, then each corner and the far side of each break
        int cz = Tower.CZ;
        course(new Course("tower", -Tower.SIZE, F, cz - Tower.SIZE, Tower.SIZE, BALC - 1, cz + Tower.SIZE, List.of(
                new int[]{0, F + 1, cz - 18}, new int[]{19, F + 17, cz - 20}, new int[]{19, F + 17, cz - 7}, new int[]{19, F + 33, cz + 20},
                new int[]{7, F + 33, cz + 19}, new int[]{-19, F + 49, cz + 20}, new int[]{-19, F + 49, cz + 7})));
    }

    @Override
    public String shortcutLine(String legId) {
        return "Something has given way on the bridge out to the bell. There's a rope down from it now, all the way to the floor.";
    }

    private final List<TollDoor> doors = new ArrayList<>();
    /** The reliquary's door works the other way round: shut all the time, open only while the bell rings (and for good once someone has been in). */
    private final TollDoor relic = new TollDoor(false, 92, 34, 6, F);
    private final List<Edit> relicShut = new ArrayList<>();

    private void door(TollDoor d) {
        doors.add(d);
    }

    /** What's worth finding: a few in plain sight, more for those who climb or wade to look. */
    private void finds() {
        int[][] at = {{-24, -46}, {24, -78}, {92, -28}, {92, 28}, {74, 8}, {118, -8}, {-128, 11}, {-110, -11}, {-146, 10}, {-96, -80}, {-54, -50}, {168, 8}, {-190, -8}, {88, 51}, {96, 51}};
        int i = 0;
        for (int[] f : at) {
            int gy = ground(f[0], f[1]);
            pieces.add(new dev.awaken.gates.realms.kit.piece.Find(f[0], gy, f[1], seed + i++));
            site(SiteKind.FIND, f[0], gy + 1, f[1], 2, "");
        }
        // up where only the curious go: the tower's landings, the balcony, and the bell's own shoulder
        int[][] high = {{19, F + 16, 64}, {19, F + 32, 104}, {-19, F + 48, 104}, {36, BALC, 36}, {-36, BALC, 36}, {7, BALC, 0}, {0, BALC, 100}};
        for (int[] f : high) {
            pieces.add(new dev.awaken.gates.realms.kit.piece.Find(f[0], f[1], f[2], seed + i++));
            site(SiteKind.FIND, f[0], f[1] + 1, f[2], 2, "high");
        }
    }

    /** Every piece of iron that keeps you off the Lord's floor. */
    private void grilles() {
        M m = m();
        for (int x = -38; x <= 38; x++) for (int z = -38; z <= 38; z++) {
            double r = Math.sqrt(x * x + z * z);
            int a = Math.abs(x), c = Math.abs(z);
            boolean inSlotBand = a <= 5 && c >= 26 && c <= 36 || c <= 5 && a >= 26 && a <= 36;
            if (!inSlotBand && Math.abs(r - RAIL_R) < 0.75) {
                boolean e = onRail(x + 1, z), w = onRail(x - 1, z), n = onRail(x, z - 1), s = onRail(x, z + 1);
                BlockState b = m.bars.setValue(IronBarsBlock.EAST, e).setValue(IronBarsBlock.WEST, w).setValue(IronBarsBlock.NORTH, n).setValue(IronBarsBlock.SOUTH, s);
                for (int y = F + 2; y <= F + 5; y++) grille.add(new Edit(x, y, z, b));
            } else if (inSlotBand) {
                boolean ns = a <= 5;
                int t = ns ? a : c, along = ns ? c : a;
                if (t == 5 && along >= 27 && along <= 36) for (int y = F + 2; y <= F + 5; y++) grille.add(new Edit(x, y, z, m.barsAlong(!ns, ns)));
                if (along == 36 && t < 5) for (int y = F + 1; y <= F + 5; y++) grille.add(new Edit(x, y, z, y == F + 5 ? m.iron : m.barsAlong(ns, !ns)));
            }
        }
    }

    private static boolean onRail(int x, int z) {
        int a = Math.abs(x), c = Math.abs(z);
        if (a <= 5 && c >= 26 && c <= 36 || c <= 5 && a >= 26 && a <= 36) return false;
        return Math.abs(Math.sqrt(x * x + z * z) - RAIL_R) < 0.75;
    }

    @Override
    public List<Edit> change(String moment) {
        BlockState air = Blocks.AIR.defaultBlockState();
        List<Edit> out = new ArrayList<>();
        switch (moment) {
            case "heart_open" -> {
                for (Edit e : grille) out.add(new Edit(e.x(), e.y(), e.z(), air));
            }
            case "heart_shut" -> out.addAll(grille);
            case "heart_weeps" -> floorBreak(out, 1);
            case "heart_winds" -> floorBreak(out, 2);
            case "heart_still" -> floorBreak(out, 0);
            case "toll_shut" -> {
                for (TollDoor d : doors) d.edits(out, false);
            }
            case "toll_open" -> {
                for (TollDoor d : doors) d.edits(out, true);
            }
            case "relic_open", "found_reliquary" -> relic.edits(out, true);
            case "relic_shut" -> out.addAll(relicShut);
            // a Warden's fight shuts the door of its wing; the tower's has the stair behind it, so nothing there is shut
            case "seal_bones" -> doors.get(1).edits(out, false);
            case "unseal_bones" -> doors.get(1).edits(out, true);
            case "seal_water" -> doors.get(3).edits(out, false);
            case "unseal_water" -> doors.get(3).edits(out, true);
            case "shortcut_balcony" -> rope(out);
            case "walk_open" -> {
                relic.edits(out, true);
                rope(out);
            }
            default -> {}
        }
        return out;
    }

    /** A gap in the bridge's rail and a bell rope from it down to the walk round the pit. */
    private void rope(List<Edit> out) {
        M m = m();
        out.add(new Edit(1, BALC + 1, ROPE_Z, Blocks.AIR.defaultBlockState()));
        for (int y = F + 1; y <= BALC + 1; y++) out.add(new Edit(ROPE_X, y, ROPE_Z, m.rope));
    }

    /** How the Lord's floor gives way: the outer ring first, in alternate wedges, then the middle ring. Stage 0 puts it all back. */
    private void floorBreak(List<Edit> out, int stage) {
        M m = m();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -26; x <= 26; x++) for (int z = -26; z <= 26; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r < 10 || r >= 25.6 || slot(x, z) >= 0) continue;
            int wedge = (int) Math.floor((Math.atan2(z, x) + Math.PI) / (Math.PI / 6));
            boolean outer = r >= 18 && wedge % 2 == 0, middle = r < 17 && wedge % 2 == 1;
            boolean broken = stage >= 1 && outer || stage >= 2 && middle;
            if (broken) {
                out.add(new Edit(x, PIT, z, air));
                out.add(new Edit(x, PIT - 1, z, air));
                out.add(new Edit(x, PIT - 2, z, rnd(x, z, 545) < 0.5 ? m.worn : m.deep));
            } else if (outer || middle) {
                Drum d = (Drum) rooms.get(0);
                out.add(new Edit(x, PIT, z, d.pitTop(m, x, z, r, Math.atan2(z, x))));
                out.add(new Edit(x, PIT - 1, z, m.stoneA));
                out.add(new Edit(x, PIT - 2, z, m.stoneA));
            }
        }
    }

    private void room(Room r) {
        r.mayOverlap = true;
        rooms.add(r);
        pieces.add(r);
    }

    @Override
    public int ground(int x, int z) {
        for (int i = rooms.size() - 1; i >= 0; i--) if (rooms.get(i).has(x, z)) return rooms.get(i).floor(x, z);
        return 0;
    }

    @Override
    public boolean indoors() {
        return true;
    }

    @Override
    public Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    /** No wind at the edge of a crypt: the rock is the edge. */
    @Override
    public double edge(int x, int z) {
        return -1;
    }

    @Override
    public int halfSize() {
        return HALF;
    }

    @Override
    public int biome(int x, int y, int z) {
        return 0;
    }

    @Override
    public float mouthYaw() {
        return 0;
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return x * x + z * z < 29 * 29 && y < F;
    }

    @Override
    public String heartClosedLine() {
        return "The iron round this floor won't lift for you yet. Whatever holds it down is somewhere else in the crypt.";
    }

    @Override
    public void paint(V v) {
        M m = mats();
        if (v.x0 + 17 < -HALF || v.x0 - 1 > HALF || v.z0 + 17 < -HALF || v.z0 - 1 > HALF) return;
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            if (Math.abs(x) > HALF || Math.abs(z) > HALF) continue;
            v.set(x, ROCK_BOTTOM, z, m.bedrock);
            for (int y = ROCK_BOTTOM + 1; y <= ROCK_TOP; y++) v.set(x, y, z, m.deep);
        }
        drawPieces(v, m);
        for (Edit e : grille) if (v.in(e.x(), e.y(), e.z())) v.set(e.x(), e.y(), e.z(), e.state());
        for (Edit e : relicShut) if (v.in(e.x(), e.y(), e.z())) v.set(e.x(), e.y(), e.z(), e.state());
        shafts(v);
        settle(v, m);
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        var air = new dev.awaken.gates.realms.kit.AirPlan().mist(F + 2, 6, 0x3a4a6a, 0.5F);
        for (int[] c : SHAFTS) air.shaft(c[0], ROCK_TOP, c[1], c[0], c[2], c[1], c[3] + 1, 0x9fb8e8, 0.75F);
        return air.build();
    }

    /** Round walls step in and out, so now and then a candle ends up one cell past its shelf: it goes rather than hang there. */
    private void settle(V v, M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) for (int y = F - 14; y <= Tower.ROOF; y++) {
            BlockState s = v.get(x, y, z);
            if (s == null || !(s.getBlock() == m.candles(1).getBlock() || s.getBlock() == m.bones.getBlock() || s.getBlock() == m.niche.getBlock())) continue;
            BlockState u = v.get(x, y - 1, z);
            if (u == null || u.is(Blocks.WATER) || u.getBlock() == m.lily.getBlock()) v.set(x, y, z, s.getBlock() == m.niche.getBlock() ? m.stoneA : null);
        }
    }

    /** Cracks in the roof, each with the moon coming down it. */
    private void shafts(V v) {
        for (int[] s : SHAFTS) {
            int r = s[3];
            if (s[0] + r < v.x0 - 1 || s[0] - r > v.x0 + 16 || s[1] + r < v.z0 - 1 || s[1] - r > v.z0 + 16) continue;
            for (int x = s[0] - r; x <= s[0] + r; x++) for (int z = s[1] - r; z <= s[1] + r; z++) {
                if (r > 2 && (x - s[0]) * (x - s[0]) + (z - s[1]) * (z - s[1]) > r * r) continue;
                for (int y = s[2]; y <= ROCK_TOP + 1; y++) v.set(x, y, z, null);
            }
        }
    }

    /**
     * x, z, the height the crack starts at, its half width. The great round one is the oculus over the bell; the rest are three to five cells across,
     * wide enough that the sky's own light reaches the floor in a patch. Two fall on the nave's road and one on the head of the stair.
     */
    private static final int[][] SHAFTS = {{0, 0, F + 104, 24}, {16, -74, F + 76, 2}, {-20, -98, F + 76, 2}, {-14, -60, F + 76, 2}, {22, -54, F + 76, 2},
            {-3, -80, F + 76, 2}, {4, -50, F + 76, 2}, {3, -103, F + 76, 2},
            {-104, -4, F + 6, 2}, {-122, 6, F + 6, 2}, {-140, -5, F + 6, 2}, {-178, 0, F + 8, 3}, {156, 6, F + 26, 2}, {100, 2, F + 26, 2}, {0, 84, F + 60, 2}};

    // ---- what every wall is made of ----------------------------------------------------------------------------------------

    /**
     * A wall's stone is laid in courses, not scattered: one stone runs three cells along its course (two to four where a wall turns or curves), and
     * every course starts its runs somewhere else, so the joints never line up. Mostly the plain stone, with worn, deep and dressed stones among it.
     */
    static BlockState course(M m, int x, int y, int z) {
        int off = (int) (rnd(y, 0, 78) * 3);
        double n = rnd(Math.floorDiv(x + off, 3) * 31 + Math.floorDiv(z + off, 3), y, 77);
        return n < 0.62 ? m.stoneA : n < 0.82 ? m.worn : n < 0.94 ? m.deep : m.pale;
    }

    /**
     * One cell of a wall of niches, whether the wall runs straight or round: u is along it, h up it, d into it from the face (-1 is the air just in front).
     * Cut-stone frames, a recess in each bay with brick at the back, and in the recess whatever was left there: candles, a skull, bones, a stone coffin, iron.
     * out is the way the wall faces, along the way u grows: the carved stones need both. The wall is tiers high; stepped says each tier stands a
     * block back from the one under it, leaving a ledge (the nave, the drum and the round rooms; a hall's wall goes straight up).
     *
     * The light is gathered into pools: every third bay is lit, its candles in clusters of four and five on the ledge in front and in the recess, and
     * the two bays between are dark but for one candle in about one bay in five.
     */
    static void niche(V v, M m, int wall, int x, int y, int z, int u, int h, int d, Direction out, Direction along, int tiers, boolean stepped) {
        int tier = Math.floorDiv(h, TIER), hh = Math.floorMod(h, TIER), uu = Math.floorMod(u, BAY), bay = Math.floorDiv(u, BAY);
        if (d > DEPTH + 1) return;
        boolean lit = Math.floorMod(bay, 3) == 0;
        if (d < 0) {
            // what stands proud of the face, and the candles left along the ledge where the tier below steps out
            boolean pierSide = uu == 0 || uu == BAY - 1;
            // a bracket under the ledge where the tier above steps back, at both sides of every bay
            if (stepped && tier < tiers - 1 && pierSide && hh == TIER - 1) v.set(x, y, z, m.facing(m.corbel, out));
            else if (pierSide && rnd(bay + uu * 7, h, wall + 3) < 0.16) v.set(x, y, z, m.worn);
            else if (lit && hh == 0 && !pierSide && rnd(x, y * 3 + z, wall + 4) < 0.45) v.set(x, y, z, m.candles(rnd(x, z, wall + 5) < 0.5 ? 4 : 5));
            // candles light a few blocks: two unseen lights a cell out from a lit bay carry its pool into the room, one at the foot of the wall
            // and one at its middle tier. (The nave's floor climbs its wall, so it sets its own foot light: see Nave.column.)
            else if (lit && uu == 2 && (tier == 0 && hh == 2 || tier == tiers / 2 && hh == 3)) v.set(x, y, z, POOL_GLOW);
            return;
        }
        int kind = (int) (rnd(bay, tier, wall) * 16);
        boolean frame = hh == 0 || hh == TIER - 1 || uu == 0 || uu == BAY - 1;
        if (frame) {
            if (d > 1) return;
            if (hh == TIER - 1 && d == 0 && tier % 4 == 2) v.set(x, y, z, m.band);
            else if (hh == TIER - 1 && d == 0 && rnd(bay, tier, wall + 1) < 0.6) v.set(x, y, z, m.pale);
            else v.set(x, y, z, course(m, x, y, z));
            return;
        }
        if (kind == 13) {
            // a walled-up tomb, its stone cut to fit
            if (d == 0) v.set(x, y, z, hh == 2 || hh == 3 ? m.pale : course(m, x, y, z));
            return;
        }
        if (d == DEPTH) {
            boolean skull = (kind == 7 || kind == 8) && hh >= 2 && hh <= 3 && uu >= 2 && uu <= 3;
            v.set(x, y, z, skull ? m.niche.setValue(WorldBlocks.Facing.FACING, out) : kind == 9 || kind == 10 ? m.ossuary : m.brick);
            return;
        }
        if (d > DEPTH) return;
        // an arch stone in each top corner of the opening, the two facing each other: a small arch, not a square hole
        if (d == 0 && hh == TIER - 2 && (uu == 1 || uu == BAY - 2)) {
            v.set(x, y, z, m.facing(m.arch, uu == 1 ? along : along.getOpposite()));
            return;
        }
        v.set(x, y, z, null);
        double c = rnd(x * 7 + z, y, wall + 2);
        // a dark bay's one candle, in about one bay in five
        boolean lone = !lit && hh == 1 && rnd(bay, tier, wall + 6) < 0.2 && uu == 1 + (int) (rnd(bay, tier, wall + 7) * 4);
        switch (kind) {
            case 9, 10 -> {
                if (d == 2 && hh <= 2) v.set(x, y, z, m.ossuary);
                else if (lone && d == 0) v.set(x, y, z, m.candles(1));
                else if (hh == 1 && c < 0.5) v.set(x, y, z, m.bones);
            }
            case 11, 12 -> {
                if (hh == 1) v.set(x, y, z, m.pale);
                else if (hh == 2) v.set(x, y, z, uu == 1 && d == 1 ? m.pale : m.paleSlab);
                else if (lit && hh == 3 && uu == 1 && d == 1) v.set(x, y, z, m.candles(4));
            }
            case 14 -> {
                if (d == 0) v.set(x, y, z, m.barsAlong(out.getAxis() == Direction.Axis.Z, out.getAxis() == Direction.Axis.X));
                else if (lit && hh == 1 && c < 0.4) v.set(x, y, z, m.candles(c < 0.2 ? 4 : 5));
                else if (lone && d == 1) v.set(x, y, z, m.candles(1));
            }
            case 15 -> {
                if (d == 1 && uu == 2 && hh >= 2) v.set(x, y, z, m.link(y));
            }
            default -> {
                if (lit && hh == 1 && c < (d == 0 ? 0.85 : 0.5)) v.set(x, y, z, m.candles(rnd(x, z, y) < 0.5 ? 4 : 5));
                else if (lone && d == 0) v.set(x, y, z, m.candles(1));
            }
        }
    }

    /** One fat candle on the floor, as tall as a child: the wax built up from years of lighting the same spot. */
    static void tallCandle(V v, M m, int x, int fl, int z, int h) {
        for (int k = 1; k <= h; k++) v.set(x, fl + k, z, m.plaster);
        v.set(x, fl + h + 1, z, m.candles(5));
    }

    // ---- the nave ------------------------------------------------------------------------------------------------------------

    /** The long hall from the head of the stair to the drum: a wet road down the middle, tombs stepping up on both sides, walls of niches, a vault lost in the dark. */
    final class Nave extends Room {
        static final int PIERS = 26;

        Nave() {
            x0 = -(NAVE + TIERS + 8);
            x1 = -x0;
            z0 = HEAD_Z - TIERS - 8;
            z1 = -30;
            label = "nave";
        }

        @Override
        boolean has(int x, int z) {
            return Math.abs(x) < NAVE && z > HEAD_Z && z < -30 && x * x + z * z >= DRUM * DRUM;
        }

        /** Where the stair comes down: flights of five with a landing between, the head of it level with the arrival. */
        private static int stairY(int z) {
            if (z <= -102) return F + 10;
            if (z <= -97) return F + 10 + (-101 - z);
            if (z <= -94) return F + 5;
            if (z <= -89) return F + 5 + (-93 - z);
            return F;
        }

        @Override
        int floor(int x, int z) {
            int a = Math.abs(x);
            if (a <= 11) return stairY(z);
            int rise = z >= -88 ? 0 : Math.min(10, (int) Math.round((-88 - z) * 10 / 14.0));
            int base = a <= 15 ? 2 : a <= 22 ? 3 : 5;
            return F + Math.max(rise, base + (int) (rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 501) * 3));
        }

        private int terrace(int x, int z) {
            int a = Math.abs(x);
            if (a <= 5) return F;
            if (a <= 6) return F + 1;
            if (a <= 9) return F + 1 + (int) (rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 500) * 2);
            return floor(x, z);
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) column(v, m, x, z);
        }

        private void column(V v, M m, int x, int z) {
            int a = Math.abs(x);
            if (x * x + z * z < DRUM * DRUM && z > -60) return;
            boolean stairs = a <= 11 && z > HEAD_Z && z < -88;
            int fl = stairs ? stairY(z) : terrace(x, z);
            boolean pier = Math.floorMod(z + 4, PIERS) <= 6;
            int band = Math.floorMod(z + 4, PIERS);
            for (int y = fl + 1; y <= F + 92; y++) {
                int h = y - F, tier = Math.min(TIERS - 1, Math.floorDiv(Math.max(0, h), TIER));
                int side = a - (NAVE + tier - (pier ? 4 : 0)), head = HEAD_Z - tier - z, d = Math.max(side, head);
                if (h >= TIER * TIERS) {
                    vault(v, m, x, y, z, a, pier, band);
                    continue;
                }
                if (d < -1) v.set(x, y, z, null);
                else if (pier && side >= head) {
                    // the great piers run straight up the wall, dressed at the corners
                    if (d == -1) v.set(x, y, z, null);
                    else if (d <= 2) v.set(x, y, z, band == 0 || band == 6 ? m.pale : y % 12 == 0 ? m.band : d == 0 ? m.facing(m.flute, x > 0 ? Direction.WEST : Direction.EAST) : course(m, x, y, z));
                } else {
                    if (d == -1) v.set(x, y, z, null);
                    int u = side >= head ? z : x;
                    niche(v, m, 11, x, y, z, u, h, d, side >= head ? (x > 0 ? Direction.WEST : Direction.EAST) : Direction.SOUTH, side >= head ? Direction.SOUTH : Direction.EAST, TIERS, true);
                    // the foot of this wall is wherever the terraces have climbed to: a lit bay's low light stands two above them
                    if (d == -1 && y == fl + 2 && Math.floorMod(Math.floorDiv(u, BAY), 3) == 0 && Math.floorMod(u, BAY) == 2 && v.get(x, y, z) == null) v.set(x, y, z, POOL_GLOW);
                }
            }
            if (a < NAVE + 1 && z > HEAD_Z) dress(v, m, x, z, fl, stairs);
        }

        private void vault(V v, M m, int x, int y, int z, int a, boolean pier, int band) {
            int top = NAVE + TIERS;
            if (a >= top) return;
            int yv = F + TIER * TIERS + (int) (18 * Math.sqrt(Math.max(0, 1 - (double) (a * a) / (top * top))));
            int under = yv - (pier ? 2 : 0);
            // a rib hangs under each edge of a pier's arch, across the nave, and one runs along the crown
            if (y == under && pier && (band == 0 || band == 6)) v.set(x, y, z, m.rib(Direction.Axis.X));
            else if (y == under && a == 0) v.set(x, y, z, m.rib(Direction.Axis.Z));
            else if (y <= under) v.set(x, y, z, null);
            else if (y <= yv + 2) v.set(x, y, z, pier && (band == 0 || band == 6) ? m.pale : course(m, x, y, z));
        }

        private void dress(V v, M m, int x, int z, int fl, boolean stairs) {
            int a = Math.abs(x);
            if (stairs) {
                boolean riser = z > -102 && z <= -97 || z > -94 && z <= -89;
                if (a <= 11 && riser) v.set(x, fl, z, stair(m.flagStairs, Direction.NORTH));
                else v.set(x, fl, z, a == 11 ? m.pale : a % 5 == 0 ? m.pale : tile(m, x, z));
                if (a == 11 && z % 3 == 0) tallCandle(v, m, x, fl, z, 1 + Math.floorMod(z * 5, 3));
                return;
            }
            int cell = Math.floorDiv(x, 3) * 31 + Math.floorDiv(z, 3);
            if (a <= 5) {
                v.set(x, fl, z, a == 0 && Math.floorMod(z, 5) == 0 ? m.pale : tile(m, x, z));
                if (a >= 3 && rnd(x, z, 511) < 0.07) v.set(x, fl + 1, z, m.bones);
                return;
            }
            double n = rnd(cell, a, 512);
            v.set(x, fl, z, a <= 6 ? m.pale : n < 0.45 ? m.stoneA : n < 0.7 ? m.worn : n < 0.86 ? m.deep : m.flag);
            for (int y = fl - 1; y >= F - 1; y--) v.set(x, y, z, a <= 6 ? m.worn : course(m, x, y, z));
            double c = rnd(x, z, 513);
            if (a >= 7 && a <= 9 && Math.floorMod(z, 13) == x % 3 + 2) tallCandle(v, m, x, fl, z, 2 + Math.floorMod(z * 3, 3));
            else if (c < 0.11) v.set(x, fl + 1, z, m.candles(1 + (int) (rnd(x, z, 514) * 5)));
            else if (c < 0.15) v.set(x, fl + 1, z, m.bones);
        }

        private BlockState tile(M m, int x, int z) {
            double n = rnd(x, z, 510);
            return n < 0.62 ? m.flag : n < 0.84 ? m.stoneA : n < 0.95 ? m.deep : m.worn;
        }
    }

    // ---- the drum ------------------------------------------------------------------------------------------------------------

    /** How far out along one of the four stairs a column is, or -1: they come up from the Lord's floor at the four quarters. */
    static int slot(int x, int z) {
        int a = Math.abs(x), c = Math.abs(z);
        if (a <= 4 && c >= 27 && c <= 35) return c;
        if (c <= 4 && a >= 27 && a <= 35) return a;
        return -1;
    }

    static Direction slotDir(int x, int z) {
        return Math.abs(z) >= Math.abs(x) ? (z < 0 ? Direction.NORTH : Direction.SOUTH) : (x < 0 ? Direction.WEST : Direction.EAST);
    }

    /** The round room the bell hangs in: the Lord's floor sunk in the middle, a walk round it, niches rising tier on tier to a dome. */
    final class Drum extends Room {
        Drum() {
            box(0, 0, DRUM_TOP + 10, DRUM_TOP + 10);
            label = "drum";
        }

        @Override
        boolean has(int x, int z) {
            return x * x + z * z < DRUM * DRUM;
        }

        @Override
        int floor(int x, int z) {
            int s = slot(x, z);
            if (s >= 0) return s >= 35 ? F : PIT + s - 26;
            double r = Math.sqrt(x * x + z * z);
            return r < 9 ? PIT + 1 : r < 26 ? PIT : r < 28 ? PIT + 1 : r < PIT_R ? PIT + 2 : F;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) column(v, m, x, z);
        }

        private void column(V v, M m, int x, int z) {
            double r = Math.sqrt(x * x + z * z);
            if (r > DRUM_TOP + 8) return;
            double theta = Math.atan2(z, x);
            int fl = floor(x, z), s = slot(x, z);
            int domeTop = F + TIER * TIERS + (int) (44 * Math.sqrt(Math.max(0, 1 - r * r / (DRUM_TOP * DRUM_TOP))));
            if (r < PIT_R || s >= 0) for (int y = fl + 1; y <= F; y++) v.set(x, y, z, null);
            else if (r < PIT_R + 5) pitWall(v, m, x, z, r, theta);
            // the walk, the walls and the dome
            int u = (int) Math.round(theta * DRUM);
            boolean pier = Math.floorMod(u, 40) <= 6;
            Direction out = inward(x, z);
            for (int y = F + 1; y <= F + 118; y++) {
                int h = y - F - 1, tier = Math.min(TIERS - 1, Math.floorDiv(Math.max(0, h), TIER));
                if (h >= TIER * TIERS) {
                    if (r < DRUM_TOP) {
                        if (y <= domeTop) v.set(x, y, z, null);
                        else if (y <= domeTop + 2) v.set(x, y, z, Math.floorMod(u, 40) <= 1 ? m.pale : course(m, x, y, z));
                    }
                    continue;
                }
                int d = (int) Math.floor(r - (DRUM + tier - (pier ? 4 : 0)));
                if (z < 0 && Math.abs(x) < NAVE + tier && r < DRUM + tier + 1) { // the nave comes in here
                    v.set(x, y, z, null);
                    continue;
                }
                if (d < -1) v.set(x, y, z, null);
                else if (pier) {
                    if (d == -1) v.set(x, y, z, null);
                    else if (d <= 2) v.set(x, y, z, Math.floorMod(u, 40) == 0 || Math.floorMod(u, 40) == 6 ? m.pale : y % 12 == 0 ? m.band : d == 0 ? m.facing(m.flute, out) : course(m, x, y, z));
                } else {
                    if (d == -1) v.set(x, y, z, null);
                    niche(v, m, 23, x, y, z, u, h, d, out, out.getCounterClockWise(), TIERS, true);
                }
            }
            floors(v, m, x, z, r, theta, fl, s);
            rail(v, m, x, z, r, s);
        }

        private void pitWall(V v, M m, int x, int z, double r, double theta) {
            int u = (int) Math.round(theta * PIT_R), d = (int) Math.floor(r - PIT_R);
            for (int y = PIT + 1; y <= F; y++) {
                int h = y - (PIT + 1);
                if (h < TIER) niche(v, m, 37, x, y, z, u, h, d, inward(x, z), inward(x, z).getCounterClockWise(), 1, false);
                else if (d <= 1) v.set(x, y, z, h == TIER ? m.band : course(m, x, y, z));
            }
        }

        private Direction inward(int x, int z) {
            return Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
        }

        private void floors(V v, M m, int x, int z, double r, double theta, int fl, int s) {
            if (s >= 0) {
                v.set(x, fl, z, s <= 34 ? stair(m.flagStairs, slotDir(x, z)) : m.pale);
                for (int y = fl - 1; y >= PIT - 2; y--) v.set(x, y, z, m.stoneA);
                return;
            }
            if (r < PIT_R) {
                v.set(x, fl, z, pitTop(m, x, z, r, theta));
                for (int y = fl - 1; y >= PIT - 2; y--) v.set(x, y, z, m.stoneA);
                if (r > 26 && rnd(x, z, 531) < 0.2) v.set(x, fl + 1, z, m.candles(3));
                // the ring of candles would leave the middle black, and a fight has to be read: a low even glow, as in the painting
                if (r < 25 && Math.floorMod(x, 7) == 3 && Math.floorMod(z, 7) == 3) v.set(x, PIT + 4, z, PIT_GLOW);
                return;
            }
            if (r < DRUM + 1) {
                int ring = (int) Math.floor(r / 2);
                int n = Math.max(12, ring * 5), sec = (int) Math.floor((theta / (2 * Math.PI) + 0.5 + ring * 0.137) * n);
                double t = rnd(ring, sec, 533);
                v.set(x, F, z, r < 31.5 ? m.pale : t < 0.55 ? m.flag : t < 0.8 ? m.hexstone : t < 0.93 ? m.stoneA : m.worn);
                double c = rnd(x, z, 534);
                if (r > 42 && c < 0.08) v.set(x, F + 1, z, m.candles(1 + (int) (rnd(x, z, 535) * 5)));
                else if (r > 38 && c < 0.1) v.set(x, F + 1, z, m.bones);
            }
        }

        BlockState pitTop(M m, int x, int z, double r, double theta) {
            if (r < 2.2) return m.gold;
            if (Math.abs(r - 9.4) < 0.55 || Math.abs(r - 17.4) < 0.55 || Math.abs(r - 25.1) < 0.55 || Math.abs(r - 3.6) < 0.5) {
                return rnd(x, z, 540) < 0.18 ? m.worn : m.gold;
            }
            int ring = (int) Math.floor(r / 2);
            int n = Math.max(8, ring * 6), sec = (int) Math.floor((theta / (2 * Math.PI) + 0.5 + ring * 0.211) * n);
            double t = rnd(ring, sec, 541);
            if (mottle.get(x / 7.0, z / 7.0) > 0.55) return m.deep;
            return t < 0.5 ? m.flag : t < 0.75 ? m.hexstone : t < 0.9 ? m.pale : m.worn;
        }

        /** The low wall round the pit with the iron standing on it until the Wardens are down. */
        private void rail(V v, M m, int x, int z, double r, int s) {
            int a = Math.abs(x), c = Math.abs(z);
            boolean ns = a <= 5 && c >= 26 && c <= 36, ew = c <= 5 && a >= 26 && a <= 36;
            if (ns || ew) {
                int t = ns ? a : c, along = ns ? c : a;
                if (t == 5 && along <= 36) {
                    for (int y = PIT; y <= F + 1; y++) v.set(x, y, z, y == F + 1 ? m.paleSlab : course(m, x, y, z));
                    return;
                }
                return;
            }
            if (Math.abs(r - RAIL_R) < 0.75) {
                v.set(x, F, z, m.stoneA);
                v.set(x, F + 1, z, m.pale);
            }
        }
    }

    // ---- the bell ------------------------------------------------------------------------------------------------------------

    /** The bell itself and what it hangs from. It's the one thing in the crypt that is meant to be in the air. */
    final class GreatBell extends Piece<M> {
        static final int LIP = CLAPPER_Y;
        static final double[][] PROFILE = {{0, 19.2}, {1.5, 19.4}, {3, 18.4}, {5, 17.2}, {9, 16}, {14, 15.2}, {22, 14.6}, {32, 14.2}, {38, 13.4}, {42, 11.8}, {45, 9.4}, {47, 6.2}, {48, 2.6}};
        static final int YOKE = LIP + 56;

        GreatBell() {
            box(0, 0, DRUM_TOP + 10, DRUM_TOP + 10);
            airborne = true;
            mayOverlap = true;
            label = "great bell";
        }

        private double radius(double hh) {
            for (int i = 0; i < PROFILE.length - 1; i++) {
                if (hh <= PROFILE[i + 1][0]) {
                    double t = (hh - PROFILE[i][0]) / (PROFILE[i + 1][0] - PROFILE[i][0]);
                    return PROFILE[i][1] + (PROFILE[i + 1][1] - PROFILE[i][1]) * t;
                }
            }
            return 0;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 22) body(v, m, x, z, r);
                yoke(v, m, x, z);
            }
            for (int i = 0; i < 4; i++) {
                int sx = i < 2 ? 1 : -1, sz = i % 2 == 0 ? 1 : -1;
                chain(v, m, sx * 48, YOKE + 24, sz * 32, sx * 16, YOKE + 8, sz * 4, 12);
            }
        }

        private void body(V v, M m, int x, int z, double r) {
            double theta = Math.atan2(z, x);
            for (int hh = -6; hh <= 56; hh++) {
                int y = LIP + hh;
                if (hh >= 0 && hh <= 48) {
                    double out = radius(hh + 0.5) + (hh == 5 || hh == 8 || hh == 39 || hh == 42 ? 0.8 : 0);
                    if (r <= out && r > out - 2.3) v.set(x, y, z, r > out - 1 ? skin(m, x, z, hh, theta, out) : hh % 9 == 3 ? m.gold : m.bronze);
                    else if (hh >= 44 && r <= out) v.set(x, y, z, m.bronzeDark);
                }
                // no clapper here: the Realm hangs it, so it can swing (CLAPPER_Y). Above, the head the bell hangs by
                // what the candles on the floor can't reach: the inside of the bell shows gold from the floor
                if (hh >= 3 && hh <= 38 && hh % 5 == 2 && r >= 9 && r < 10) v.set(x, y, z, m.light);
                if (hh > 48) {
                    double w = hh < 52 ? 5.4 : 3.2;
                    if (r <= w) v.set(x, y, z, hh == 49 || hh == 53 ? m.key : hh < 52 ? m.bronze : m.iron);
                }
            }
            // small bells hung round the lip on their own chains
            if (r > 17.4 && r < 18.8) {
                int sec = (int) Math.floor((theta / (2 * Math.PI) + 0.5) * 112);
                if (sec % 8 == 0 && Math.abs((theta / (2 * Math.PI) + 0.5) * 112 - sec - 0.5) < 0.09) {
                    int len = 3 + (sec / 8) % 5;
                    for (int k = 1; k <= len; k++) v.set(x, LIP - k, z, k == len ? m.smallBell : m.link(k));
                }
            }
        }

        private BlockState skin(M m, int x, int z, int hh, double theta, double out) {
            if (hh == 6 || hh == 7 || hh == 40 || hh == 41) return m.key;
            if (hh <= 1 || hh == 5 || hh == 8 || hh == 39 || hh == 42) return m.bronze;
            double n = mottle.get(theta * out / 17.0, hh / 12.0) + mottle.get(theta * out / 5.5, hh / 4.0) * 0.22;
            return n > 0.5 ? m.bronzeDark : n < -0.72 ? m.bronze : m.patina;
        }

        private void yoke(V v, M m, int x, int z) {
            int a = Math.abs(x), c = Math.abs(z);
            if (a > DRUM_TOP + 6 || c > 6) return;
            if (c <= 5) for (int y = YOKE; y <= YOKE + 7; y++) {
                boolean strap = a % 12 <= 1;
                boolean edge = (y == YOKE || y == YOKE + 7) && c == 5;
                v.set(x, y, z, strap ? m.iron : edge ? m.iron : m.timberX);
            }
            // the headstock, where the bell's weight comes onto the beam
            if (a <= 8 && c <= 6) for (int y = YOKE - 4; y < YOKE; y++) v.set(x, y, z, a <= 6 ? m.iron : m.bronzeDark);
            // the corbels the beam rests on, stepping out of the wall on both sides
            if (a >= DRUM_TOP - 16 && c <= 6) {
                int under = YOKE - 1 - (DRUM_TOP + 2 - a) * 2;
                for (int y = YOKE - 1; y >= under && y > F + 48; y--) v.set(x, y, z, c == 6 ? m.pale : course(m, x, y, z));
            }
        }

        /** A chain sagging between two points, its links big enough to read from the floor: each is a cross of rings, every ring a quarter turn from the next. */
        private void chain(V v, M m, int ax, int ay, int az, int bx, int by, int bz, double sag) {
            int n = (int) (Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz)) * 1.2);
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                int x = (int) Math.round(ax + (bx - ax) * t), z = (int) Math.round(az + (bz - az) * t), y = (int) Math.round(ay + (by - ay) * t - sag * 4 * t * (1 - t));
                if (i % 4 == 3) continue;
                int turn = x + y + z;
                v.set(x, y, z, m.link(turn));
                if (i % 4 == 0) {
                    v.set(x, y + 1, z, m.link(turn + 1));
                    v.set(x, y - 1, z, m.link(turn + 1));
                } else if (i % 4 == 2) {
                    v.set(x + 1, y, z, m.link(turn + 1));
                    v.set(x - 1, y, z, m.link(turn + 1));
                }
            }
        }
    }

    // ---- halls, round rooms, the tower ---------------------------------------------------------------------------------------

    /** How a room's floor is laid: 0 plain, 1 the Ossuary's dust, 2 under water, 3 the keepers' swept boards. */
    private void lay(V v, M m, int x, int y, int z, int mix) {
        double n = rnd(x, z, 600);
        switch (mix) {
            case 1 -> {
                v.set(x, y, z, n < 0.42 ? m.bed : n < 0.8 ? m.flag : m.worn);
                if (rnd(x, z, 601) < 0.14) v.set(x, y + 1, z, m.bones);
            }
            case 2 -> v.set(x, y, z, n < 0.5 ? m.flag : n < 0.82 ? m.moss : m.deep);
            case 3 -> v.set(x, y, z, Math.floorMod(x + z, 7) == 0 ? m.timber : m.planks);
            default -> v.set(x, y, z, n < 0.6 ? m.flag : n < 0.85 ? m.stoneA : m.worn);
        }
    }

    /** A straight hall, along X or Z, walled in niches, its roof a low barrel. Its ends are open unless something else closes them. */
    final class Hall extends Room {
        final boolean alongX, wet;
        final int a0, a1, cross, half, fy, tiers, wall, mix;

        Hall(boolean alongX, int a0, int a1, int cross, int half, int fy, int tiers, int wall, int mix, boolean wet) {
            this.alongX = alongX;
            this.a0 = a0;
            this.a1 = a1;
            this.cross = cross;
            this.half = half;
            this.fy = fy;
            this.tiers = tiers;
            this.wall = wall;
            this.mix = mix;
            this.wet = wet;
            int pad = half + 6;
            if (alongX) {
                x0 = a0;
                x1 = a1;
                z0 = cross - pad;
                z1 = cross + pad;
            } else {
                z0 = a0;
                z1 = a1;
                x0 = cross - pad;
                x1 = cross + pad;
            }
            label = "hall " + wall;
        }

        @Override
        boolean has(int x, int z) {
            int a = alongX ? x : z, c = (alongX ? z : x) - cross;
            return a >= a0 && a <= a1 && Math.abs(c) < half;
        }

        @Override
        int floor(int x, int z) {
            return wet ? fy - 1 : fy;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) column(v, m, x, z);
        }

        private void column(V v, M m, int x, int z) {
            int a = alongX ? x : z, c = (alongX ? z : x) - cross, ac = Math.abs(c), top = TIER * tiers;
            if (ac >= half - 1 && inAnother(x, z, this)) return; // where two halls cross, neither walls off the other
            Direction out = alongX ? (c > 0 ? Direction.NORTH : Direction.SOUTH) : (c > 0 ? Direction.WEST : Direction.EAST);
            int roof = half * 3 / 4;
            for (int y = fy + 1; y <= fy + 1 + top + roof + 2; y++) {
                int h = y - fy - 1, d = ac - half;
                if (h >= top) {
                    if (ac > half) continue;
                    int yv = top + (int) (roof * Math.sqrt(Math.max(0, 1 - (double) (c * c) / (half * half))));
                    boolean rib = Math.floorMod(a, 12) <= 1;
                    int under = yv - (rib ? 1 : 0);
                    if (h == under && rib) v.set(x, y, z, m.rib(alongX ? Direction.Axis.Z : Direction.Axis.X));
                    else if (h == under && c == 0) v.set(x, y, z, m.rib(alongX ? Direction.Axis.X : Direction.Axis.Z));
                    else if (h <= under) v.set(x, y, z, null);
                    else if (h <= yv + 1) v.set(x, y, z, rib ? m.pale : course(m, x, y, z));
                    continue;
                }
                if (d < -1) v.set(x, y, z, null);
                else if (d == -1) {
                    v.set(x, y, z, null);
                    niche(v, m, wall, x, y, z, a, h, d, out, alongX ? Direction.EAST : Direction.SOUTH, tiers, false);
                } else niche(v, m, wall, x, y, z, a, h, d, out, alongX ? Direction.EAST : Direction.SOUTH, tiers, false);
            }
            if (ac < half) {
                int fl = wet ? fy - 1 : fy;
                lay(v, m, x, fl, z, mix);
                if (wet) {
                    v.set(x, fy, z, Blocks.WATER.defaultBlockState());
                    v.set(x, fl - 1, z, m.deep);
                    if (rnd(x, z, 604) < 0.012) v.set(x, fy + 1, z, m.lily);
                }
                dress(v, m, x, z, a, c, fl);
            }
        }

        /** What stands down the hall: a row of stone coffins in the Ossuary, columns rising out of the water in the Sunk Aisle, the keepers' racks. */
        private void dress(V v, M m, int x, int z, int a, int c, int fl) {
            if (mix == 1 && half >= 10) {
                int k = Math.floorMod(a, 18);
                if (Math.abs(c) <= 1 && k >= 3 && k <= 9) {
                    v.set(x, fl + 1, z, m.pale);
                    v.set(x, fl + 2, z, k == 3 || k == 9 ? m.stoneSlab : m.paleSlab);
                    if (c == 0 && (k == 4 || k == 8)) v.set(x, fl + 3, z, m.candles(5));
                }
            } else if (mix == 2 && half >= 10) {
                int k = Math.floorMod(a, 14), s = Math.abs(c) - 7;
                if (k <= 2 && s >= -1 && s <= 1) for (int y = fl + 1; y <= fy + 1 + TIER * tiers + 3; y++) v.set(x, y, z, k == 0 || k == 2 || s != 0 ? course(m, x, y, z) : m.pale);
            } else if (mix == 3) {
                int k = Math.floorMod(a, 9), j = Math.floorMod(c + 2, 11);
                if (k <= 3 && j <= 1) {
                    v.set(x, fl + 1, z, m.planks);
                    v.set(x, fl + 2, z, k % 2 == 0 ? m.candles(5) : m.candles(3));
                }
            }
        }
    }

    private boolean inAnother(int x, int z, Room self) {
        for (Piece<?> p : pieces) if (p instanceof Room r && r != self && r.has(x, z)) return true;
        return false;
    }

    /** A wide flight of stairs down a hall, with landings. */
    final class Steps extends Room {
        final int a0, a1, cross, half, low, high;

        Steps(int a0, int a1, int cross, int half, int low, int high) {
            this.a0 = a0;
            this.a1 = a1;
            this.cross = cross;
            this.half = half;
            this.low = low;
            this.high = high;
            x0 = a0;
            x1 = a1;
            z0 = cross - half - 6;
            z1 = cross + half + 6;
            label = "steps";
        }

        @Override
        boolean has(int x, int z) {
            return x >= a0 && x <= a1 && Math.abs(z - cross) < half;
        }

        /** Falls towards a0: a flight of seven, a landing of six, a flight of seven. */
        @Override
        int floor(int x, int z) {
            int run = a1 - x, n = high - low;
            if (run < 4) return high;
            if (run < 4 + n / 2) return high - (run - 3);
            if (run < 10 + n / 2) return high - n / 2;
            if (run < 10 + n) return high - n / 2 - (run - 9 - n / 2);
            return low;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int c = z - cross, ac = Math.abs(c), fl = floor(x, z);
                Direction out = c > 0 ? Direction.NORTH : Direction.SOUTH;
                for (int y = low + 1; y <= high + 18; y++) {
                    int h = y - low - 1, d = ac - half, rel = y - fl - 1;
                    if (y > fl + 13) {
                        if (ac <= half && y <= fl + 15) v.set(x, y, z, course(m, x, y, z));
                        continue;
                    }
                    if (d < -1) {
                        if (rel >= 0) v.set(x, y, z, null);
                    } else {
                        if (d == -1) v.set(x, y, z, null);
                        niche(v, m, 71, x, y, z, x, h, d, out, Direction.EAST, 3, false);
                    }
                }
                if (ac < half) {
                            v.set(x, fl, z, fl != floor(x - 1, z) ? stair(m.flagStairs, Direction.EAST) : ac == half - 1 ? m.pale : m.flag);
                    for (int y = fl - 1; y >= low - 1; y--) v.set(x, y, z, m.stoneA);
                }
            }
        }
    }

    /** A round room at the end of a wing, for its Warden: a step up to a raised floor, niches rising to a dome. */
    final class Round extends Room {
        final int cx, cz, radius, fy, tiers, wall, mix;
        final boolean wet;

        Round(int cx, int cz, int radius, int fy, int tiers, int wall, int mix, boolean wet) {
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.fy = fy;
            this.tiers = tiers;
            this.wall = wall;
            this.mix = mix;
            this.wet = wet;
            box(cx, cz, radius + tiers + 8, radius + tiers + 8);
            label = "round " + wall;
        }

        @Override
        boolean has(int x, int z) {
            return (x - cx) * (x - cx) + (z - cz) * (z - cz) < radius * radius;
        }

        @Override
        int floor(int x, int z) {
            double r = Math.hypot(x - cx, z - cz);
            return wet ? (r < 11 ? fy : fy - 1) : r < 11 ? fy + 1 : fy;
        }

        @Override
        public void draw(V v, M m) {
            int top = tiers * TIER, domeR = radius + tiers;
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x - cx, dz = z - cz;
                double r = Math.sqrt(dx * dx + dz * dz), theta = Math.atan2(dz, dx);
                if (r > domeR + 6) continue;
                int u = (int) Math.round(theta * radius);
                Direction out = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                int dome = top + (int) (domeR * 0.7 * Math.sqrt(Math.max(0, 1 - r * r / (domeR * domeR))));
                for (int y = fy + 1; y <= fy + 1 + top + domeR; y++) {
                    int h = y - fy - 1, tier = Math.min(tiers - 1, h / TIER);
                    if (h >= top) {
                        if (r >= domeR) continue;
                        if (h <= dome) v.set(x, y, z, null);
                        else if (h <= dome + 1) v.set(x, y, z, Math.floorMod(u, 24) <= 1 ? m.pale : course(m, x, y, z));
                        continue;
                    }
                    int d = (int) Math.floor(r - (radius + tier));
                    if (d < -1) v.set(x, y, z, null);
                    else {
                        if (d == -1) v.set(x, y, z, null);
                        niche(v, m, wall, x, y, z, u, h, d, out, out.getCounterClockWise(), tiers, true);
                    }
                }
                if (r < radius + 1) floor(v, m, x, z, r, theta);
            }
        }

        private void floor(V v, M m, int x, int z, double r, double theta) {
            int fl = wet ? fy - 1 : fy;
            if (wet) {
                v.set(x, fl, z, rnd(x, z, 610) < 0.5 ? m.moss : m.flag);
                v.set(x, fy, z, Blocks.WATER.defaultBlockState());
                if (r < 11) {
                    v.set(x, fy, z, r < 2.5 ? m.gold : Math.abs(r - 9.5) < 0.6 ? m.gold : m.pale);
                    if (r > 10 && rnd(x, z, 612) < 0.3) v.set(x, fy + 1, z, m.candles(4));
                } else if (rnd(x, z, 611) < 0.02) v.set(x, fy + 1, z, m.lily);
                return;
            }
            lay(v, m, x, fy, z, mix);
            if (r < 11) {
                v.set(x, fy + 1, z, r < 2.5 ? m.gold : Math.abs(r - 9.5) < 0.6 ? m.gold : Math.floorMod((int) (theta * 6), 2) == 0 ? m.ossuary : m.pale);
                if (rnd(x, z, 613) < 0.05 && r > 3 && r < 9) v.set(x, fy + 2, z, m.bones);
                if (r > 10) v.set(x, fy + 2, z, rnd(x, z, 614) < 0.35 ? m.candles(5) : null);
            }
        }
    }

    /** The ringers' tower: one stair round four walls with a rope-hung shaft in the middle and the ringing chamber at the top. */
    final class Tower extends Room {
        static final int CZ = 84, SIZE = 22, STRIP = 5, CAP = BALC, ROOF = BALC + 18;

        Tower() {
            box(0, CZ, SIZE + 8, SIZE + 8);
            label = "tower";
        }

        @Override
        boolean has(int x, int z) {
            return Math.abs(x) < SIZE && Math.abs(z - CZ) < SIZE;
        }

        @Override
        int floor(int x, int z) {
            return F + (gap(x, z - CZ) ? 0 : rise(x, z - CZ));
        }

        /**
         * Where the stair is broken: three cells gone out of the level run of the east, south and west walls, each a slot
         * open all the way down to the shaft's floor. A sprint clears one; a fall is caught (the tower is a course).
         */
        static boolean gap(int dx, int dz) {
            if (SIZE - 1 - Math.max(Math.abs(dx), Math.abs(dz)) >= STRIP) return false;
            if (Math.abs(dz) > Math.abs(dx)) return dz > 0 && dx >= 10 && dx <= 12;
            return dx > 0 ? dz >= -12 && dz <= -10 : dz >= 10 && dz <= 12;
        }

        /** How high the stair has got at this column; zero for the shaft's own floor. */
        private int rise(int dx, int dz) {
            int o = SIZE - 1 - Math.max(Math.abs(dx), Math.abs(dz));
            if (o >= STRIP) return 0;
            if (dx < -SIZE + STRIP && dz < -SIZE + STRIP) return 64;
            int q, base;
            if (Math.abs(dz) > Math.abs(dx) && dz < 0) {
                q = dx + SIZE;
                base = 0;
            } else if (Math.abs(dx) >= Math.abs(dz) && dx > 0) {
                q = dz + SIZE;
                base = 16;
            } else if (Math.abs(dz) > Math.abs(dx)) {
                q = SIZE - dx;
                base = 32;
            } else {
                q = SIZE - dz;
                base = 48;
            }
            return base + Math.max(0, Math.min(16, q - 23));
        }

        private Direction up(int dx, int dz) {
            if (Math.abs(dz) > Math.abs(dx) && dz < 0) return Direction.EAST;
            if (Math.abs(dx) >= Math.abs(dz) && dx > 0) return Direction.SOUTH;
            return Math.abs(dz) > Math.abs(dx) ? Direction.WEST : Direction.NORTH;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x, dz = z - CZ, far = Math.max(Math.abs(dx), Math.abs(dz)), o = SIZE - 1 - far;
                int u = Math.abs(dz) > Math.abs(dx) ? x : z;
                Direction out = Math.abs(dz) > Math.abs(dx) ? (dz > 0 ? Direction.NORTH : Direction.SOUTH) : (dx > 0 ? Direction.WEST : Direction.EAST);
                for (int y = F + 1; y <= ROOF + 4; y++) {
                    int h = y - F - 1, d = far - SIZE;
                    if (y > ROOF) {
                        if (far < SIZE + 2) v.set(x, y, z, course(m, x, y, z));
                        continue;
                    }
                    if (d >= 0) niche(v, m, 81, x, y, z, u, h, d, out, Math.abs(dz) > Math.abs(dx) ? Direction.EAST : Direction.SOUTH, TIERS, false);
                    else v.set(x, y, z, null);
                }
                if (far >= SIZE) continue;
                int e = rise(dx, dz), fl = F + e;
                if (o < STRIP && !gap(dx, dz)) {
                    for (int y = F; y < fl; y++) v.set(x, y, z, course(m, x, y, z));
                    Direction d = up(dx, dz);
                    boolean flight = e % 16 != 0;
                    v.set(x, fl, z, flight ? stair(m.flagStairs, d) : tile(m, x, z));
                    if (o == 0 && e % 16 == 0 && rnd(x, z, 623) < 0.5) tallCandle(v, m, x, fl, z, 1);
                    if (o == STRIP - 1 && fl > F && !(dx < -SIZE + STRIP && dz < -SIZE + STRIP)) v.set(x, fl + 1, z, m.barsAlong(Math.abs(dz) > Math.abs(dx), Math.abs(dz) <= Math.abs(dx)));
                } else {
                    lay(v, m, x, F, z, 1);
                    if (rnd(x, z, 624) < 0.09) v.set(x, F + 1, z, m.candles(4));
                }
                chamber(v, m, x, z, dx, dz, o, e);
            }
        }

        private BlockState tile(M m, int x, int z) {
            double n = rnd(x, z, 620);
            return n < 0.62 ? m.flag : n < 0.84 ? m.stoneA : n < 0.95 ? m.deep : m.worn;
        }

        /** The ringing chamber's floor over the shaft, with the ropes going down through it. */
        private void chamber(V v, M m, int x, int z, int dx, int dz, int o, int e) {
            boolean hatch = dx < -SIZE + STRIP && dz < -10;
            int well = Math.max(Math.abs(dx), Math.abs(dz));
            if (!hatch) {
                v.set(x, CAP, z, Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0 ? m.timber : m.planks);
                v.set(x, CAP - 1, z, Math.floorMod(x, 8) == 0 ? m.timberX : m.planks);
                if (o <= 1 && rnd(x, z, 622) < 0.1) v.set(x, CAP + 1, z, m.candles(5));
            }
            // the moon comes down the middle of the shaft through a hole in these boards, so the hole has a rail and no ropes
            if (well == 3) v.set(x, CAP + 1, z, m.barsAlong(Math.abs(dz) == 3, Math.abs(dx) == 3));
            if (o >= STRIP - 1 && o % 2 == 1 && well > 3 && Math.floorMod(x + z, 6) == 0) {
                int end = F + 26 + Math.floorMod(x * 3 + z, 9) * 3;
                for (int y = CAP - 2; y >= end; y--) v.set(x, y, z, m.rope);
                v.set(x, end - 1, z, Math.floorMod(x, 2) == 0 ? m.smallBell : m.hung);
                for (int y = CAP + 1; y <= ROOF; y++) v.set(x, y, z, m.rope);
            }
        }
    }

    /** The narrow walk high round the drum, and the bridge out from it to the bell's shoulder. */
    final class Balcony extends Piece<M> {
        Balcony() {
            box(0, 0, DRUM_TOP + 4, DRUM_TOP + 4);
            mayOverlap = true;
            label = "balcony";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < 50 && r > 8 && Math.abs(x) <= 1 && z > 0) {
                    v.set(x, BALC, z, m.planks);
                    v.set(x, BALC - 1, z, m.timber);
                    if (Math.abs(x) == 1) v.set(x, BALC + 1, z, m.barsAlong(false, true));
                }
                if (r >= 5.6 && r < 9.2) {
                    v.set(x, BALC, z, m.planks);
                    if (r > 8.2 && !(Math.abs(x) <= 1 && z > 0)) v.set(x, BALC + 1, z, m.barsAlong(true, true));
                }
                if (z < 0 && Math.abs(x) < NAVE + TIERS) continue;
                if (r < 49 || r >= DRUM_TOP) continue;
                v.set(x, BALC, z, ((int) r) % 3 == 0 ? m.pale : m.flag);
                v.set(x, BALC - 1, z, m.stoneA);
                for (int y = BALC - 2; y >= BALC - 8 && r < 49 + (BALC - y) * 1.2; y--) v.set(x, y, z, course(m, x, y, z));
                if (r < 50.2) v.set(x, BALC + 1, z, m.barsAlong(true, true));
                for (int y = BALC + 1; y < BALC + 6 && r >= 50.2; y++) v.set(x, y, z, null);
            }
        }
    }

    /** A way cut through whatever stood in it, drawn after the rooms so nothing can close it. */
    final class Cut extends Piece<M> {
        final int y0, y1, floorY;

        Cut(int ax, int bx, int az, int bz, int y0, int y1, int floorY) {
            x0 = ax;
            x1 = bx;
            z0 = az;
            z1 = bz;
            this.y0 = y0;
            this.y1 = y1;
            this.floorY = floorY;
            mayOverlap = true;
            label = "way through";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                for (int y = y0; y <= y1; y++) v.set(x, y, z, null);
                if (floorY >= 0) v.set(x, floorY, z, m.planks);
            }
        }
    }

    /** A door the bell shuts: iron drops into a stone frame on the stroke and lifts when the ringing dies away. */
    final class TollDoor extends Piece<M> {
        final boolean alongX;
        final int x, z, half, fy;

        /** @param alongX whether you walk through it along X */
        TollDoor(boolean alongX, int x, int z, int half, int fy) {
            this.alongX = alongX;
            this.x = x;
            this.z = z;
            this.half = half;
            this.fy = fy;
            box(x, z, half + 3, half + 3);
            mayOverlap = true;
            label = "toll door";
        }

        void edits(List<Edit> out, boolean open) {
            M m = m();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int t = -half; t <= half; t++) for (int y = fy + 1; y <= fy + 5; y++) {
                int ex = alongX ? x : x + t, ez = alongX ? z + t : z;
                out.add(new Edit(ex, y, ez, open ? air : y == fy + 5 ? m.iron : m.barsAlong(!alongX, alongX)));
            }
        }

        @Override
        public void draw(V v, M m) {
            for (int t = -half - 2; t <= half + 2; t++) for (int w = -1; w <= 1; w++) {
                int ax = alongX ? x + w : x + t, az = alongX ? z + t : z + w, at = Math.abs(t);
                for (int y = fy + 1; y <= fy + 8; y++) {
                    if (at <= half && y <= fy + 5) continue;
                    if (at > half || y > fy + 5) v.set(ax, y, az, y == fy + 7 ? m.band : at == half + 1 || y == fy + 6 ? m.pale : course(m, ax, y, az));
                }
            }
        }
    }
}
