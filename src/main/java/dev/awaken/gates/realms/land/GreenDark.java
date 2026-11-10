package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Green Dark (SPEC §50.9): the Overgrown Temple's second kind, and nothing like its cellars. Wet jungle caves far
 * below the temple, all plant and no mason: dark wet stone that moss is taking over from the floor up, a whole wall at a
 * time; vines and glow berries hanging in curtains that wander across the roofs; moss underfoot with beds of dark wet
 * earth; ferns and great leaves along the foot of the walls, and here and there one carved stone that fell this far.
 */
public final class GreenDark extends GeneratedCrawl<VineSeat.M> {
    private static final List<List<String>> HERDS = List.of(List.of("thorn_hound*4", "vine_creaker"), List.of("thorn_archer*2", "vine_creaker*2"),
            List.of("~drip_lurker*4", "thorn_archer"), List.of("thorn_hound*2", "thorn_archer*2", "incense_priest"));
    /** How far apart the lone berry strands hang between the curtains: one to every square of this many cells, so no floor is far from a pool. */
    private static final int GRID = 4;

    private final SimplexNoise field, line;
    /**
     * Every stone the temple's family has is cut and coursed, and nothing here was built: the rock is the world's own dark
     * stone, the moss its own moss, the wet beds its mud and rooted earth (the berries are the world's too). The roots and
     * the leaf litter are the temple's.
     */
    private final BlockState stone = Blocks.DEEPSLATE.defaultBlockState(), seam = Blocks.TUFF.defaultBlockState(), moss = Blocks.MOSS_BLOCK.defaultBlockState(),
            mud = Blocks.MUD.defaultBlockState(), earth = Blocks.ROOTED_DIRT.defaultBlockState(), slab = Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState();
    private final BlockState root = block("temple_root"), litter = block("temple_leaves");
    private final BlockState berryStem = Blocks.CAVE_VINES_PLANT.defaultBlockState(), berryTip = Blocks.CAVE_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 25).setValue(CaveVines.BERRIES, true);
    private Arena lord;

    public GreenDark(Ctx ctx) {
        super(ctx, 0x4752454EL);
        field = N.noise(seed + 1001);
        line = N.noise(seed + 1002);
        generate();
        for (Part p : parts) if (p.name.equals("lord") && p instanceof Arena a) lord = a;
    }

    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    @Override
    protected VineSeat.M mats() {
        return VineSeat.m();
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("vine_creaker*3", "thorn_hound*3");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("vine_creaker", "thorn_hound*2");
    }

    /** A bud of dark leaves in the wall; it lights before it fires. */
    @Override
    protected BlockState spitOn(VineSeat.M m) {
        return m.leafLit;
    }

    @Override
    protected BlockState spitOff(VineSeat.M m) {
        return m.leafDeep;
    }

    @Override
    protected BlockState tooth(VineSeat.M m) {
        return root;
    }

    @Override
    protected BlockState thinFloor(VineSeat.M m) {
        return m.leaf;
    }

    @Override
    protected double wild() {
        return 1;
    }

    /** The rock is one thing: dark wet stone, darker still in broad seams. What grows on it is put there by adorn, a field at a time. */
    @Override
    protected BlockState rock(VineSeat.M m, int x, int y, int z) {
        return field.get(x / 26.0 + y / 40.0, z / 26.0 - y / 33.0) > 0.45 ? seam : stone;
    }

    @Override
    protected BlockState deep(VineSeat.M m) {
        return stone;
    }

    @Override
    protected BlockState skinFloor(VineSeat.M m, Part part, int x, int y, int z) {
        // a ledge over a drop is root, so the mat of leaves that gives way can be told from it
        if (part instanceof Shaft || part instanceof Chasm) return m.bark;
        // moss, with beds of dark wet earth lying in it
        double bed = field.get(x / 9.0 + 400, z / 9.0);
        return bed > 0.5 ? mud : bed > 0.38 ? earth : moss;
    }

    @Override
    protected BlockState step(VineSeat.M m) {
        return slab;
    }

    /** A wall of roots across the way. */
    @Override
    protected BlockState doorBlock(VineSeat.M m) {
        return root;
    }

    private boolean bare(VineSeat.M m, BlockState s) {
        return s == stone || s == seam;
    }

    /** How high the moss has climbed a wall column, from the floor: nothing on a bare wall, the whole height of a taken one. */
    private double mossTo(int ax, int az) {
        double f = field.get(ax / 19.0 + 100, az / 19.0);
        return f < 0.02 ? 0 : 2 + (f - 0.02) * 34 + 1.6 * field.get(ax / 3.0 + 150, az / 3.0);
    }

    /** Hangs a strand from under the roof at (x, y, z): plain vine, or berries with their light under them. Three cells stay clear over the floor. */
    private void hang(V v, VineSeat.M m, int x, int y, int z, int up, int len, boolean berries, boolean lit) {
        len = Math.min(len, up - 2);
        int low = Integer.MIN_VALUE;
        for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) {
            v.set(x, y - k, z, berries ? berryStem.setValue(CaveVines.BERRIES, rnd(x * 7L + k, z, 1011) < 0.6) : m.vines);
            low = y - k;
        }
        if (low == Integer.MIN_VALUE) return;
        if (berries) v.set(x, low, z, berryTip);
        if (!lit) return;
        if (v.get(x, low - 1, z) == null) v.set(x, low - 1, z, m.light);
        int over = y - up + 3; // and one just over head height, however high the roof is
        if (over < low - 1 && v.get(x, over, z) == null) v.set(x, over, z, m.light);
    }

    @Override
    protected void adorn(V v, VineSeat.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, drop = part instanceof Shaft || part instanceof Chasm;
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int up = y - Part.airFrom(part.feet2(x, z));
        if (wall) {
            int ax = x + wx, az = z + wz;
            if (bare(m, v.get(ax, y, az))) {
                // down a shaft the moss comes in long falls, top to bottom; in a room it climbs from the floor, a whole wall at a time
                if (drop ? field.get(ax / 6.0 + 200, az / 6.0) > 0.12 : up < mossTo(ax, az)) v.set(ax, y, az, moss);
            }
        }
        if (narrow || drop) return;
        if (onFloor && up <= 0) {
            // along the foot of a real wall (rock two cells high beside you): ferns and great leaves in clumps, and once or
            // twice in a room a carved stone that fell this far, the only built thing here
            boolean foot = wall && v.get(x + wx, y + 1, z + wz) != null;
            double clump = rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 1012), own = rnd(x, z, 1013);
            if (foot && room && own < 0.025) v.set(x, y, z, m.carving);
            else if (foot && clump < 0.5) {
                if (own < 0.65) v.set(x, y, z, m.fern);
                else if (room && own < 0.8) v.set(x, y, z, m.leaf);
            } else if (own > 0.94) v.set(x, y, z, own > 0.97 ? m.fern : litter);
        }
        if (!underRoof || onFloor || up < 3) return;
        // the roof: curtains of vine and of glow berries along lines that wander across it, open roof between them; a lone
        // strand of berries here and there so no floor is far from a pool; and leaves come through in broad patches
        double vineLine = Math.abs(line.get(x / 15.0, z / 15.0)), berryLine = Math.abs(line.get(x / 17.0 + 500, z / 17.0));
        int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID);
        boolean lone = Math.floorMod(x, GRID) == (int) (rnd(gx, gz, 1014) * GRID) && Math.floorMod(z, GRID) == (int) (rnd(gx, gz, 1015) * GRID);
        int len = 2 + (int) (rnd(x, z, 1016) * 4);
        if (berryLine < 0.05) hang(v, m, x, y, z, up, len, true, true);
        else if (lone) hang(v, m, x, y, z, up, Math.max(len, up > 9 ? 5 : 2), true, true);
        else if (vineLine < 0.06) hang(v, m, x, y, z, up, len, false, false);
        else if (bare(m, v.get(x, y + 1, z)) && field.get(x / 12.0 + 300, z / 12.0) > 0.3) v.set(x, y + 1, z, m.leaf);
    }

    @Override
    protected void skinSpecial(V v, VineSeat.M m) {
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the hall stands on great trunks, mossed at the foot, each with a band of lit leaves round it; a ring of lit leaves in the floor
            if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) {
                int k = y - lord.feet;
                v.set(x, y, z, k == 6 || k == 7 ? m.leafLit : k < 4 ? m.barkMoss : m.bark);
            } else if (d < 1.6 || Math.abs(d - 9.4) < 0.55) v.set(x, lord.feet - 1, z, m.leafLit);
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xc8f0a0, 0.7F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x3a6a3a, 0.5F).light(lord.cx, lord.feet + 5, lord.cz, 0xd8e070, 18, 0.8F);
        return air.build();
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): jungle caves: hollows with a bank along one side, a great trunk to walk round, fallen trunks and wet stones to cross on. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.OVAL, Cave.Form.TERRACE, Cave.Form.RING, Cave.Form.RIFT), List.of(Arena.Plan.RING, Arena.Plan.TIERS),
                List.of(Arena.Plan.RING, Arena.Plan.TIERS, Arena.Plan.ROTUNDA), List.of(Chasm.Kind.BEAMS, Chasm.Kind.FORD, Chasm.Kind.PILLARS, Chasm.Kind.LEDGES, Chasm.Kind.HANGERS), false, 2.5, 5);
    }
}
