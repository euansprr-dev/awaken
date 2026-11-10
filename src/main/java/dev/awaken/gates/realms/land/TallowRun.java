package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Tallow Runs (SPEC §50.9): the Wax skin on a run rolled from the seed. The runs under a great candle works, where a
 * thousand years of wax has gone down through the rock: soot-black stone that pale wax has run down and set over, drips
 * from every roof, candle stubs banked along the foot of the walls. The one dungeon that glows.
 */
public final class TallowRun extends GeneratedCrawl<WaxGalleries.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final List<List<String>> HERDS = List.of(List.of("honey_slime*3", "hive_larva*3"), List.of("drone_bee*3", "hive_guard"),
            List.of("~drip_lurker*3", "honey_slime*2"), List.of("hive_larva*6", "drone_bee"));

    private final SimplexNoise patch;
    /** The rock the wax ran down: the Wax family's soot stone and its banded course, which its galleries never needed. */
    private final BlockState soot = block("wax_soot"), band = block("wax_soot_band");
    private Cave ambush;
    private Arena lord;

    public TallowRun(Ctx ctx) {
        super(ctx, 0x54414C4CL);
        patch = N.noise(seed + 951);
        generate();
        for (Part p : parts) {
            if (p.name.equals("ambush")) ambush = (Cave) p;
            if (p.name.equals("lord")) lord = (Arena) p;
        }
    }

    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    @Override
    protected WaxGalleries.M mats() {
        return WaxGalleries.m();
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("drone_bee*4", "hive_larva*3");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("hive_guard", "honey_slime*2");
    }

    @Override
    protected BlockState spitOn(WaxGalleries.M m) {
        return m.cellGlow;
    }

    @Override
    protected BlockState spitOff(WaxGalleries.M m) {
        return m.cell;
    }

    @Override
    protected BlockState tooth(WaxGalleries.M m) {
        return m.cold;
    }

    @Override
    protected BlockState thinFloor(WaxGalleries.M m) {
        return m.run;
    }

    @Override
    protected double wild() {
        return 0.7;
    }

    @Override
    protected BlockState rock(WaxGalleries.M m, int x, int y, int z) {
        return Math.floorMod(y + (int) Math.floor(1.5 * patch.get(x / 14.0, z / 14.0)), 5) == 0 ? band : soot;
    }

    @Override
    protected BlockState deep(WaxGalleries.M m) {
        return soot;
    }

    /** Whether a floor cell lies against the rock: nothing is open beside it at any height a step could reach. */
    private boolean edge(int x, int y, int z) {
        for (int[] d : SIDES) {
            int ax = x + d[0], az = z + d[1];
            if (!open(ax, y, az) && !open(ax, y + 1, az) && !open(ax, y + 2, az)) return true;
        }
        return false;
    }

    @Override
    protected BlockState skinFloor(WaxGalleries.M m, Part part, int x, int y, int z) {
        if (part.name.equals("hidden")) return rnd(x, z, 952) < 0.5 ? m.old : m.combWall;
        // wax tile where people walked, cold wax pooled along the foot of the walls
        return edge(x, y, z) || rnd(x, z, 953) < 0.08 ? m.cold : m.deck;
    }

    @Override
    protected BlockState step(WaxGalleries.M m) {
        return m.coldSlab;
    }

    @Override
    protected BlockState doorBlock(WaxGalleries.M m) {
        return m.old;
    }

    /** Hangs wax from under a roof: strands over a tip, or a string of beads. It stops at whatever is in its way. */
    private static void hang(V v, WaxGalleries.M m, int x, int y, int z, int len, boolean beads, int tip) {
        int last = Integer.MIN_VALUE;
        for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) {
            v.set(x, y - k, z, beads ? m.bead : m.drip(0));
            last = y - k;
        }
        if (last != Integer.MIN_VALUE && !beads) v.set(x, last, z, m.drip(tip));
    }

    @Override
    protected void adorn(V v, WaxGalleries.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, shaft = part instanceof Shaft;
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int up = y - Part.airFrom(part.feet2(x, z)), down = part.roof(x, z) - 1 - y;
        if (wall) {
            // the wall beside you: wax has run down it from the roof and set, run wax at the top, cold or yellowed under it
            // in ragged curtains, each wall column its own length; the soot shows at the foot
            int ax = x + wx, az = z + wz;
            BlockState at = v.get(ax, y, az);
            if (at == soot || at == band) {
                double c = rnd(ax, az, 961);
                boolean lit = part == ambush || part == lord;
                if (lit && up >= 1 && up <= (part == lord ? 11 : 5) && patch.get(ax / 6.0 + y / 9.0, az / 6.0 - y / 7.0) > 0.1) v.set(ax, y, az, rnd(ax * 31L + y, az, 962) < 0.6 ? m.cellLit : m.combWall);
                else if (shaft) {
                    // down a shaft it never stopped: whole runs from top to bottom, with rock between them
                    if (c < 0.45) v.set(ax, y, az, rnd(ax * 31L + y, az, 963) < 0.8 ? m.run : m.cold);
                } else {
                    int top = 1 + (int) (rnd(ax, az, 964) * 2), len = room ? 3 + (int) (c * 6) : 1 + (int) (c * 3), foot = room ? 1 + (int) (rnd(ax, az, 965) * 2) : 1;
                    if (down < top) v.set(ax, y, az, m.run);
                    else if (down < len && up >= foot) v.set(ax, y, az, rnd(ax, az, 966) < 0.3 ? m.old : m.cold);
                }
            }
        }
        if (narrow) return;
        // candle stubs banked along the foot of a real wall (two cells high beside you: the next step of a stair is not one),
        // in clusters, with a light over some so the pool reaches into the room
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        if (onFloor && foot && !shaft && up <= 0) {
            double cluster = rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 967), own = rnd(x, z, 968);
            if (cluster < (room ? 0.5 : 0.3) && own < 0.7) {
                v.set(x, y, z, m.bank);
                if (own < 0.45 && v.get(x, y + 2, z) == null) v.set(x, y + 2, z, m.tipLight);
            }
        }
        if (underRoof && !onFloor && !wall) {
            // drips from every roof, and now and then a string of beads; two cells are kept clear over the floor
            double h = rnd(x, z, 969);
            int clear = up - 2;
            if (h < 0.025) hang(v, m, x, y, z, Math.min(2 + (int) (rnd(x, z, 970) * 4), clear), true, 0);
            else if (h < (room ? 0.3 : 0.2)) hang(v, m, x, y, z, Math.min(1 + (int) (rnd(x, z, 970) * (room ? 4 : 2)), clear), false, 1 + (int) (rnd(x, z, 971) * 3));
        }
    }

    @Override
    protected void skinSpecial(V v, WaxGalleries.M m) {
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // pillars of old wax with a band of comb, and a ring of lit cells in the floor round its middle
            if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) v.set(x, y, z, (y - lord.feet) % 6 == 4 ? m.combWall : m.old);
            else if (d < 1.6 || Math.abs(d - 9.4) < 0.55) v.set(x, lord.feet - 1, z, m.cellLit);
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xffd890, 0.8F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0xd8a040, 0.5F).light(lord.cx, lord.feet + 4, lord.cz, 0xffc050, 18, 0.9F);
        if (ambush != null) air.light(ambush.cx, ambush.feet + 3, ambush.cz, 0xffc050, 12, 0.7F);
        return air.build();
    }
}
