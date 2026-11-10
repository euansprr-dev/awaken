package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.CrawlLand;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Broodwarren (SPEC §50.9, gate 14): a nest dug down under a dead tree, fifty blocks from the split root you come in
 * by to the chamber the brood is laid in. A short crawl, and the first gate built from the crawl kit: every room and way
 * is a part placed by name, and what is the Broodwarren's own is here: grey nest paper in layers, comb in the walls with
 * something lit inside it, sacs hung from every roof, a red skin that grows over a doorway while a fight is on.
 *
 * <p>The run: the antechamber (a pack waits), the spitting gallery (cells in the walls light and spit across it, one
 * after another), the nursery (a shaft full of hanging comb, a ledge winding down it broken in three places, with floor
 * that gives way), the larder (the sacs burst when you're in the middle of them), the comb ring (the Warden), the brood
 * chamber (the Lord). Clearing the larder opens the workers' crawl back up to the antechamber, and a slip behind the
 * larder's sacs leads to a cell of royal jelly most people will walk past.
 */
public final class Broodwarren extends CrawlLand<Broodwarren.M> {
    public static final class M extends Mats {
        final BlockState paper = b("brood_paper"), paperDark = b("brood_paper_dark"), paperPale = b("brood_paper_pale"), earth = b("brood_earth"), resin = b("brood_resin"),
                resinSlab = b("brood_resin_slab"), cell = b("brood_cell"), cellLit = b("brood_cell_lit"), sac = b("brood_sac_drip"), skin = b("brood_membrane"), jelly = b("brood_jelly"),
                spitter = b("brood_spitter"), spitterLit = b("brood_spitter_lit");

        M() {
            super("amber", new Accents(b("brood_cell"), b("brood_cell_lit"), b("brood_jelly"), b("brood_sac_drip"), b("hive_candle"), b("hive_wax_crumbs"), b("hive_drum"),
                    Blocks.DARK_OAK_FENCE.defaultBlockState(), b("amber_leaf_gold")));
        }
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    @Override
    protected M mats() {
        return m();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** The skin over the way down to the brood chamber, the skin over the top of the workers' crawl, and the curtain of sacs in front of the slip. Each is a box; only its air is filled. */
    private static final int[] HEART_DOOR = {-22, 62, 92, -20, 74, 102}, CRAWL_SKIN = {13, 103, -26, 20, 110, -24}, CURTAIN = {43, 73, 62, 46, 78, 67};
    /** Where the nursery's ledge is thin comb that gives way: the south side of its first turn and the north side of its second. */
    private static final int[][] THIN = {{-3, 87, 40, 3, 91, 43}, {-3, 79, 21, 3, 83, 24}};
    /** The spitting gallery's cells: how far along it each is, and which wall it is in. */
    private static final int[][] SPITTERS = {{-13, -1}, {-8, 1}, {-3, -1}, {2, 1}, {7, -1}};
    /** Hanging comb in the nursery, from the middle of the shaft: x, z, the height of its lowest cell, how fat it is. */
    private static final int[][] COMBS = {{0, 0, 83, 3}, {4, -3, 92, 2}, {-4, 2, 88, 2}, {3, 4, 79, 2}, {-3, -4, 95, 2}};
    /** What's left lying about: x, the cell it sits in, z. */
    private static final int[][] FINDS = {{57, 73, 75}, {60, 73, 78}, {-9, 106, -36}, {40, 74, 55}, {0, 70, 30}};
    static final int LORD_X = -52, LORD_Z = 100, LORD_Y = 62, CRACK_Z = -66;

    private final Cave root, ante, larder, jellyCell;
    private final Arena ring, brood;
    private final Tunnel throat, gallery;
    private final Shaft nursery;
    private final SimplexNoise grain, patch;

    public Broodwarren(Ctx ctx) {
        super(ctx, 0x42524F4F44L);
        grain = N.noise(seed + 911);
        patch = N.noise(seed + 912);
        // every part meets the next at the same height: a tunnel's end is the floor of the room it comes into
        root = part(new Cave("root", 0, 112, -70, 7, 8, 6, seed + 1));
        throat = part(new Tunnel("throat", 0, 112, -63, 0, 106, -44, 2.3, 4, seed + 2));
        ante = part(new Cave("ante", 0, 106, -30, 13, 13, 9, seed + 3));
        gallery = part(new Tunnel("gallery", 0, 106, -18, 0, 98, 14, 2.6, 4, seed + 4));
        part(new Tunnel("link", 0, 98, 14, 0, 98, 23, 2.4, 4, seed + 5));
        nursery = part(new Shaft("nursery", 0, 98, 32, 11, 3.2, 16, 26, 70, 3, seed + 6, 0.25, 0.75, 1.25));
        part(new Tunnel("drain", 0, 74, 43, 17, 74, 56, 2.3, 4, seed + 7));
        larder = part(new Cave("larder", 30, 74, 60, 14, 11, 7, seed + 8));
        part(new Tunnel("slip", 43, 74, 64, 54, 73, 72, 1.4, 3, seed + 9));
        jellyCell = part(new Cave("jelly", 58, 73, 76, 5.5, 5.5, 5, seed + 10));
        part(new Tunnel("gullet", 30, 74, 70, 12, 70, 87, 2.4, 4, seed + 11));
        ring = part(new Arena("ring", 0, 70, 92, 13, 9, 5, seed + 12));
        part(new Tunnel("descent", -12, 70, 94, -30, 62, 100, 2.6, 5, seed + 13));
        brood = part(new Arena("brood", LORD_X, LORD_Y, LORD_Z, 22, 16, 8, seed + 14));
        part(new Tunnel("crawl1", 32, 74, 50, 36, 90, 10, 1.7, 3, seed + 15));
        part(new Tunnel("crawl2", 36, 90, 10, 14, 106, -28, 1.7, 3, seed + 16));
        done();

        // the doors of each fight: a skin across every way out of it, well clear of the room itself
        door("ante", -4, 104, -47, 4, 112, -45);
        door("ante", -4, 102, -15, 4, 110, -13);
        door("ante", 15, 100, -20, 24, 107, -17);
        door("larder", 11, 73, 48, 14, 79, 57);
        door("larder", 24, 71, 72, 27, 78, 79);
        door("larder", 29, 74, 43, 36, 81, 45);
        door("ring", 14, 69, 80, 17, 76, 87);

        site(SiteKind.MOUTH, 0, 112, -70, 6, "");
        site(SiteKind.PORTAL, 0, 112, -75, 2, "");
        site(SiteKind.LORD, LORD_X, LORD_Y, LORD_Z, 22, ctx.bp().heart().lord());
        site(SiteKind.WARDEN, 0, 70, 92, 13, "comb ring");
        site(SiteKind.DEN, 0, 106, -30, 10, "");
        site(SiteKind.DEN, 30, 74, 60, 8, "");
        for (int[] f : FINDS) site(SiteKind.FIND, f[0], f[1], f[2], 2, "");
        clearSites();

        M m = m();
        List<String> none = List.of();
        leg("root", LegKind.ARRIVE, "The split root", 0, 112, -70, 6, -1, none, "", "ante");
        leg("ante", LegKind.FIGHT, "The antechamber", 0, 106, -30, 13, 0, List.of("hive_larva*4", "honey_slime*2"), "", "gallery", "crawl");
        leg("gallery", LegKind.TRAP, "The spitting gallery", 0, 102, -2, 16, -1, none, "", "nursery");
        leg("nursery", LegKind.MOVEMENT, "The nursery", 0, 98, 23, 12, -1, none, "", "larder");
        leg("larder", LegKind.AMBUSH, "The larder", 30, 74, 60, 7, 1, List.of("hive_larva*6", "drone_bee*2"), "", "ring", "jelly", "crawl");
        leg("jelly", LegKind.HIDDEN, "The jelly cell", 58, 73, 76, 5, -1, none, "", "larder");
        leg("crawl", LegKind.SHORTCUT, "The workers' crawl", 16, 105, -25, 3, -1, none, "den1", "ante");
        leg("ring", LegKind.WARDEN, "The comb ring", 0, 70, 92, 14, 0, List.of("hive_guard", "drone_bee*2"), "", "brood");
        leg("brood", LegKind.LORD, "The brood chamber", LORD_X, LORD_Y, LORD_Z, 22, -1, none, "");
        // five cells light and spit one after another down the gallery, a second apart, three seconds round
        for (int i = 0; i < SPITTERS.length; i++) {
            int z = SPITTERS[i][0], side = SPITTERS[i][1], y = spitY(z);
            trap(new Trap("gallery", TrapKind.SPIT, side * 3, y, z, -side * 3, y, z, 60, i * 20, m.spitterLit, m.spitter));
        }
        for (int[] t : THIN) trap(new Trap("nursery", TrapKind.CRUMBLE, t[0], t[1], t[2], t[3], t[4], t[5], 0, 0, m.cell, AIR));
        List<int[]> ledges = new ArrayList<>();
        for (double turns : new double[]{0.02, 0.31, 0.81, 1.31}) {
            int[] at = nursery.stand(turns);
            ledges.add(new int[]{at[0], at[1] + 1, at[2]});
        }
        course(new Course("nursery", -13, 70, 19, 13, 112, 45, ledges));
    }

    /** A spitter sits at chest height where the gallery's floor has got to. */
    private int spitY(int z) {
        return Part.airFrom(gallery.feet2(0, z)) + 1;
    }

    private static boolean in(int[] box, int x, int y, int z) {
        return x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
    }

    // ---- what the nest is made of ----------------------------------------------------------------------------------------

    /** Nest paper, laid down in layers that sag and rise: a pale dry layer and a dark damp one in every seven. Round the split root it is still earth. */
    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        double r = rnd(x * 73L + y, z, 811);
        double mouth = Math.sqrt(x * x + (y - 114) * (y - 114) + (z + 70) * (z + 70));
        if (mouth < 20 && mouth < 15 + 4 * grain.get(x / 6.0 + 50, z / 6.0)) return r < 0.18 ? m.paperDark : m.earth;
        int band = Math.floorMod((int) Math.floor(y + 2.6 * grain.get(x / 11.0, z / 11.0)), 7);
        if (band == 0) return r < 0.85 ? m.paperPale : m.paper;
        if (band == 4) return r < 0.8 ? m.paperDark : m.paper;
        return r < 0.1 ? m.paperDark : r > 0.95 ? m.paperPale : m.paper;
    }

    @Override
    protected BlockState deep(M m) {
        return m.paperDark;
    }

    @Override
    protected BlockState floor(M m, Part part, int x, int y, int z) {
        if (part == root) return m.earth;
        if (part == jellyCell) return rnd(x, z, 821) < 0.7 ? m.jelly : m.resin;
        if (part == nursery && thin(part, x, y, z)) return m.cell;
        return rnd(x, z, 822) < 0.1 ? m.paperDark : m.resin;
    }

    @Override
    protected boolean thin(Part part, int x, int y, int z) {
        if (part != nursery) return false;
        for (int[] t : THIN) if (in(t, x, y, z)) return true;
        return false;
    }

    @Override
    protected BlockState step(M m) {
        return m.resinSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.skin;
    }

    /** How much of a part's roof has a sac hung from it, and how much of its comb has something awake in it. */
    private double sacs(Part part) {
        return part == larder ? 0.4 : part == jellyCell ? 0.2 : part == ante ? 0.12 : part == nursery || part == brood ? 0.1 : part == ring ? 0.07 : 0.05;
    }

    private double awake(Part part) {
        // the throat is ringed with it, so the first thing you see from the root is where to go
        return part == throat ? 0.34 : part == nursery || part == brood ? 0.24 : part == larder ? 0.2 : part == gallery ? 0.1 : 0.15;
    }

    /** How many sacs hang in a chain from the roof over this column: none for most, one to three for the rest. The same answer wherever it is asked. */
    private int sacLen(Part part, int x, int z) {
        return rnd(x, z, 801) >= sacs(part) ? 0 : 1 + (int) (rnd(x, z, 802) * 3);
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        if (part == root) return; // the root is bare earth: the nest starts in the throat
        if (wx != 0 || wz != 0) {
            // comb comes through the paper in patches, and here and there a cell has something awake in it
            int ax = x + wx, az = z + wz;
            BlockState at = v.get(ax, y, az);
            if ((at == m.paper || at == m.paperDark || at == m.paperPale) && patch.get(ax / 8.0 + y / 9.0, az / 8.0 - y / 7.0) > (part == throat ? 0.1 : 0.42)) {
                v.set(ax, y, az, rnd(ax * 31L + y, az, 77) < awake(part) ? m.cellLit : m.cell);
            }
        }
        if (underRoof && !onFloor) {
            // never lower than head height over the floor, so a room of them is something you push through, not a wall
            int len = Math.min(sacLen(part, x, z), y - Part.airFrom(part.feet2(x, z)) - 1);
            for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, m.sac);
        }
    }

    @Override
    protected void special(V v, M m) {
        // the two fighting rooms' pillars are comb all the way up, and the brood chamber's middle is jelly
        for (Arena a : new Arena[]{ring, brood}) {
            for (int x = Math.max(a.x0, v.x0 - 1); x <= Math.min(a.x1, v.x0 + 16); x++) for (int z = Math.max(a.z0, v.z0 - 1); z <= Math.min(a.z1, v.z0 + 16); z++) {
                if (a.out(x, z) >= 1) continue;
                if (a.pillar(x, z)) for (int y = a.feet; y < a.roof(x, z); y++) v.set(x, y, z, (y - a.feet) % 5 == 4 ? m.paperPale : m.cell);
                else if (a == brood && (x - LORD_X) * (x - LORD_X) + (z - LORD_Z) * (z - LORD_Z) < 11) v.set(x, a.feet - 1, z, m.jelly);
            }
        }
        // skins that are shut in the land as drawn: the way down to the Lord, and the top of the workers' crawl
        for (int[] box : new int[][]{HEART_DOOR, CRAWL_SKIN}) fill(v, box, m.skin);
        // the slip to the jelly cell is behind a curtain of sacs, floor to roof
        fill(v, CURTAIN, m.sac);
        for (int[] s : SPITTERS) v.set(s[1] * 3, spitY(s[0]), s[0], m.spitter);
        for (int[] f : FINDS) v.set(f[0], f[1], f[2], m.cache);
        // a crack in the roof over the root lets the day down onto the lip of the throat, just in front of where you stand
        for (int x = -3; x <= 3; x++) for (int y = root.roof(x, CRACK_Z); y <= rockTop(); y++) v.set(x, y, CRACK_Z, null);
        combs(v, m);
    }

    private void fill(V v, int[] box, BlockState s) {
        for (int x = Math.max(box[0], v.x0 - 1); x <= Math.min(box[3], v.x0 + 16); x++) for (int z = Math.max(box[2], v.z0 - 1); z <= Math.min(box[5], v.z0 + 16); z++) {
            for (int y = box[1]; y <= box[4]; y++) if (open(x, y, z) && v.get(x, y, z) == null) v.set(x, y, z, s);
        }
    }

    /** Comb hung in the nursery on stalks from the dome: each a fat drop of cells, a third of them lit, with one sac under it. */
    private void combs(V v, M m) {
        for (int[] c : COMBS) {
            int cx = nursery.cx + c[0], cz = nursery.cz + c[1], lo = c[2], fat = c[3], hi = lo + fat * 2 + 2;
            if (cx + fat < v.x0 - 1 || cx - fat > v.x0 + 16 || cz + fat < v.z0 - 1 || cz - fat > v.z0 + 16) continue;
            for (int x = cx - fat; x <= cx + fat; x++) for (int z = cz - fat; z <= cz + fat; z++) for (int y = lo; y <= hi; y++) {
                double dx = (x - cx) / (fat + 0.4), dz = (z - cz) / (fat + 0.4), dy = (y - (lo + hi) / 2.0) / ((hi - lo) / 2.0 + 0.4);
                if (dx * dx + dz * dz + dy * dy < 1) v.set(x, y, z, rnd(x * 17L + y, z, 831) < 0.34 ? m.cellLit : m.cell);
            }
            for (int y = hi + 1; y < nursery.dome(cx, cz); y++) v.set(cx, y, cz, m.paperPale);
            v.set(cx, lo - 1, cz, m.sac);
        }
    }

    // ---- what changes --------------------------------------------------------------------------------------------------

    private void box(List<Edit> out, int[] box, BlockState s) {
        for (int x = box[0]; x <= box[3]; x++) for (int y = box[1]; y <= box[4]; y++) for (int z = box[2]; z <= box[5]; z++) if (open(x, y, z)) out.add(new Edit(x, y, z, s));
    }

    @Override
    public List<Edit> change(String moment) {
        M m = m();
        List<Edit> out = new ArrayList<>();
        switch (moment) {
            case "heart_open" -> box(out, HEART_DOOR, AIR);
            case "heart_shut" -> box(out, HEART_DOOR, m.skin);
            case "shortcut_crawl", "walk_open" -> box(out, CRAWL_SKIN, AIR);
            case "ambush_larder" -> {
                // the sacs near the middle burst: what was in them is what comes at you
                for (int x = larder.x0; x <= larder.x1; x++) for (int z = larder.z0; z <= larder.z1; z++) {
                    if (larder.feet2(x, z) == NONE || (x - larder.cx) * (x - larder.cx) + (z - larder.cz) * (z - larder.cz) > 81) continue;
                    int top = larder.roof(x, z) - 1, len = Math.min(sacLen(larder, x, z), top - larder.feet - 1);
                    for (int k = 0; k < len; k++) out.add(new Edit(x, top - k, z, AIR));
                }
            }
            // the brood chamber's pillars wake from the floor up as the Lord is worn down, and sleep again when it's over
            case "heart_weeps" -> pillars(out, 4, m.cellLit);
            case "heart_winds" -> pillars(out, 9, m.cellLit);
            case "heart_still" -> pillars(out, 9, m.cell);
            default -> {
                return super.change(moment);
            }
        }
        return out;
    }

    private void pillars(List<Edit> out, int high, BlockState s) {
        M m = m();
        for (int x = brood.x0; x <= brood.x1; x++) for (int z = brood.z0; z <= brood.z1; z++) {
            if (brood.out(x, z) >= 1 || !brood.pillar(x, z)) continue;
            for (int y = brood.feet; y < Math.min(brood.feet + high, brood.roof(x, z)); y++) if ((y - brood.feet) % 5 != 4) out.add(new Edit(x, y, z, s));
        }
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return (x - LORD_X) * (x - LORD_X) + (z - LORD_Z) * (z - LORD_Z) < 21 * 21 && y < LORD_Y + 18;
    }

    @Override
    public String heartClosedLine() {
        return "The way down from here has grown shut. Whatever is keeping it shut is in this room with you.";
    }

    @Override
    public String shortcutLine(String legId) {
        return "Something tore open, a long way above you. There's a way back up to the first chamber now.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        return new AirPlan().mist(LORD_Y + 1, 5, 0x7a2a12, 0.45F).mist(72, 4, 0x8a5a14, 0.3F)
                .shaft(0, rockTop(), CRACK_Z, 0, 112, CRACK_Z, 3, 0xffe2b0, 0.8F)
                .light(0, 90, 32, 0xff6a2a, 14, 0.9F).light(LORD_X, LORD_Y + 4, LORD_Z, 0xffa040, 18, 0.8F).light(58, 75, 76, 0xd8e860, 7, 0.9F)
                .light(30, 78, 60, 0xff5a20, 12, 0.7F).light(0, 110, -30, 0xff7a30, 10, 0.5F).build();
    }

    @Override
    public Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    @Override
    public int biome(int x, int y, int z) {
        return 0;
    }

    @Override
    public float mouthYaw() {
        return 0;
    }
}
