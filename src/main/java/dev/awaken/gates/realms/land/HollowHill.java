package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Hollow Hills (SPEC §50.9): the Barrow skin on a run rolled from the seed. The passages under a burial mound, dug by hand a
 * very long time ago: low ways through packed earth, shored with a course of kerb stones at the foot of each wall and a course
 * of stone at head height, a timber frame where a way runs straight, roots through the roofs of the rooms. An urn stands in a
 * niche here and there, a shield hangs on a room's wall. It is dark: the only light is a little grave light low on the walls of
 * the rooms, in pools, because what lives here hunts by sound and keeps out of the light.
 */
public final class HollowHill extends GeneratedCrawl<QuietBarrow.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockState POOL = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private static final List<List<String>> HERDS = List.of(List.of("~hushling*3", "toll_ghoul"), List.of("toll_ghoul*3", "crypt_archer*2"),
            List.of("~hushling*2", "crypt_archer*2"), List.of("bell_wisp*3", "crypt_keeper"));

    private final SimplexNoise grain;
    private Arena lord;

    public HollowHill(Ctx ctx) {
        super(ctx, 0x484F4C4C4FL);
        grain = N.noise(seed + 951);
        generate();
        for (Part p : parts) if (p.name.equals("lord")) lord = (Arena) p;
    }

    @Override
    protected QuietBarrow.M mats() {
        return QuietBarrow.m();
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("toll_ghoul*4", "bell_wisp*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("toll_ghoul*2", "crypt_archer");
    }

    @Override
    protected BlockState spitOn(QuietBarrow.M m) {
        return m.glow;
    }

    @Override
    protected BlockState spitOff(QuietBarrow.M m) {
        return m.bronze;
    }

    @Override
    protected BlockState tooth(QuietBarrow.M m) {
        return m.kerb;
    }

    @Override
    protected BlockState thinFloor(QuietBarrow.M m) {
        return m.planks;
    }

    @Override
    protected double wild() {
        return 0.8;
    }

    /** Packed earth, with the stone it was dug through showing in seams. */
    @Override
    protected BlockState rock(QuietBarrow.M m, int x, int y, int z) {
        double seam = grain.get(x / 9.0 + y / 13.0, z / 9.0 - y / 11.0), r = rnd(x * 73L + y, z, 952);
        if (seam > 0.5) return r < 0.2 ? m.stoneA : m.worn;
        return r < 0.07 ? m.worn : m.bed;
    }

    @Override
    protected BlockState deep(QuietBarrow.M m) {
        return m.deep;
    }

    private static boolean earth(QuietBarrow.M m, BlockState s) {
        return s == m.bed || s == m.worn || s == m.stoneA;
    }

    /** Whether a column is two or more in from every side of its part: the middle of a way, where feet have worn it. */
    private static boolean middle(Part part, int x, int z) {
        return part.feet2(x + 2, z) != NONE && part.feet2(x - 2, z) != NONE && part.feet2(x, z + 2) != NONE && part.feet2(x, z - 2) != NONE;
    }

    @Override
    protected BlockState skinFloor(QuietBarrow.M m, Part part, int x, int y, int z) {
        double n = rnd(x, z, 953);
        if (part.name.equals("hidden")) return n < 0.5 ? m.hexstone : m.pale;
        if (part instanceof Cave) return n < 0.07 ? m.flag : n < 0.16 ? m.worn : m.bed;
        // a way: bare earth at the sides, flagstones down the middle where everyone has walked
        return middle(part, x, z) ? (n < 0.72 ? m.flag : n < 0.9 ? m.worn : m.bed) : n < 0.08 ? m.worn : m.bed;
    }

    @Override
    protected BlockState step(QuietBarrow.M m) {
        return m.stoneSlab;
    }

    @Override
    protected BlockState doorBlock(QuietBarrow.M m) {
        return m.timber;
    }

    /** Which way a way runs here, from its own shape: 1 along z, 2 along x, 0 when it runs at a slant (then it gets no frames). */
    private static int runs(Part part, int x, int z) {
        int wide = 1, deep = 1;
        for (int k = 1; k <= 5 && part.feet2(x + k, z) != NONE; k++) wide++;
        for (int k = 1; k <= 5 && part.feet2(x - k, z) != NONE; k++) wide++;
        for (int k = 1; k <= 5 && part.feet2(x, z + k) != NONE; k++) deep++;
        for (int k = 1; k <= 5 && part.feet2(x, z - k) != NONE; k++) deep++;
        return wide <= 6 && deep >= 11 ? 1 : deep <= 6 && wide >= 11 ? 2 : 0;
    }

    @Override
    protected void adorn(V v, QuietBarrow.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        if (part instanceof Shaft) return;
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int up = y - Part.airFrom(part.feet2(x, z));
        if (wall) {
            // the shoring: kerb stones at the foot of the wall, a course of stone at head height, earth between
            int ax = x + wx, az = z + wz;
            if (earth(m, v.get(ax, y, az))) {
                if (up == 0) {
                    // a little grave light low on a room's wall, a few to a room, each with its pool
                    // (in the real game the first pass was too dark to read: more of them in a room, a few along the ways, and each pool brighter)
                    boolean lit = !narrow && rnd(Math.floorDiv(ax, 4), Math.floorDiv(az, 4), 954) < (room ? 0.75 : 0.4) && rnd(ax, az, 955) < (room ? 0.5 : 0.3);
                    v.set(ax, y, az, lit ? m.glow : rnd(ax * 7L + y, az, 956) < 0.8 ? m.pale : m.stoneA);
                    if (lit && v.get(x, y + 1, z) == null) v.set(x, y + 1, z, POOL);
                    if (lit && room && v.get(x, y + 3, z) == null) v.set(x, y + 3, z, POOL);
                } else if (up == 2) v.set(ax, y, az, rnd(ax * 7L + y, az, 957) < 0.75 ? m.stoneA : m.worn);
                else if (up == 1 && !narrow && rnd(ax * 11L + y, az, 958) < 0.045) v.set(ax, y, az, m.urn); // an urn in a niche at knee height
            }
            // a shield hung at head height on a room's wall (it hangs flat against a wall to the north or south of it)
            if (room && !narrow && up == 2 && wz != 0 && v.get(x, y, z) == null && rnd(x, z, 959) < 0.07) v.set(x, y, z, wz > 0 ? m.shield : m.shieldSouth);
        }
        // roots through the very top of a room
        if (underRoof && room && y + 1 >= ((Cave) part).feet + ((Cave) part).height - 2 && earth(m, v.get(x, y + 1, z)) && rnd(x, z, 960) < 0.7) v.set(x, y + 1, z, m.turf);
        // a timber frame every eight along a way that runs straight: a post each side and a beam across
        if (part.name.startsWith("way") && v.get(x, y, z) == null) {
            int along = (Math.floorMod(z, 8) == 3 ? 1 : 0) | (Math.floorMod(x, 8) == 3 ? 2 : 0);
            if (along != 0 && (runs(part, x, z) & along) != 0) {
                boolean alongZ = (runs(part, x, z) & 1) != 0;
                boolean edge = alongZ ? part.feet2(x - 1, z) == NONE || part.feet2(x + 1, z) == NONE : part.feet2(x, z - 1) == NONE || part.feet2(x, z + 1) == NONE;
                if (edge) v.set(x, y, z, m.timber);
                else if (underRoof && !onFloor) v.set(x, y, z, alongZ ? m.timberX : m.timberZ);
                return;
            }
        }
        // a few urns stood at the foot of a room's wall; nothing with a body anywhere else on a floor
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        if (onFloor && foot && up <= 0 && room && !narrow && v.get(x, y, z) == null && rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 961) < 0.12 && rnd(x, z, 962) < 0.4) v.set(x, y, z, m.urn);
    }

    @Override
    protected void skinSpecial(V v, QuietBarrow.M m) {
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            if (lord.pillar(x, z)) {
                // the hall's pillars are standing stones: tallest in the middle, a cap on each column, clear of the roof where the roof is high enough
                int roof = lord.roof(x, z), beside = 0;
                for (int[] s : SIDES) if (lord.pillar(x + s[0], z + s[1])) beside++;
                int tall = 2 + beside * 2;
                boolean free = roof - lord.feet >= tall + 3;
                for (int y = lord.feet; y < roof; y++) v.set(x, y, z, !free || y < lord.feet + tall ? m.stoneA : y == lord.feet + tall ? m.cap : null);
            } else if (Math.abs(d - 9.4) < 0.6 || Math.abs(d - 17.4) < 0.6) v.set(x, lord.feet - 1, z, rnd(x, z, 963) < 0.85 ? m.pale : m.stoneA); // kerb rings in the floor
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xa8b8d8, 0.6F);
        if (lord != null) air.mist(lord.feet + 1, 4, 0x5a6a60, 0.45F);
        return air.build();
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): a barrow: round chambers, a ring round a standing core, a ledge of earth along one side. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.OVAL, Cave.Form.RING, Cave.Form.TERRACE, Cave.Form.OVAL), List.of(Arena.Plan.RING, Arena.Plan.TIERS),
                List.of(Arena.Plan.RING, Arena.Plan.TIERS), List.of(Chasm.Kind.PILLARS, Chasm.Kind.FORD, Chasm.Kind.STEPS, Chasm.Kind.LEDGES), false, 2.0, 4);
    }
}
