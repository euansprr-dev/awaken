package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.Land.Family;
import dev.awaken.gates.realms.kit.crawl.BeatSkin;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.gates.realms.kit.crawl.Composer;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Sealed Wing (SPEC §50.9): the Precursor Lab's skin on a run rolled from the seed. A wing of a laboratory far older than
 * anything else in the world, sealed and still running. Built and exact, nothing wanders: smooth pale panels in courses with a
 * dark seam between, a strip of light along every wall at knee height and another under the roof, lights set flush in every
 * ceiling, a lit line down the middle of each way that leads you on. Racks, tanks and sealed hatches stand in the walls. It is
 * the most evenly lit of the dungeons.
 */
public final class SealedWing extends GeneratedCrawl<SealedWing.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockState POOL = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private static final List<List<String>> HERDS = List.of(List.of("test_subject*4", "security_unit"), List.of("security_unit*2", "coil_unit*2"),
            List.of("coil_unit*3", "gel_sample"), List.of("test_subject*3", "coil_unit*2"));

    /** The theme has no Realm family of its own: these are the old gate's blocks and the vanilla ones its palette used. */
    public static final class M extends Mats {
        final BlockState panel = b("lab_panel"), strip = b("lab_panel_lit"), plate = b("lab_floor"), plateSlab = b("lab_floor_slab"), frame = b("lab_frame"), glass = b("lab_glass"),
                rack = b("lab_rack"), tank = b("lab_tank"), hologram = b("lab_hologram"), screws = b("lab_screws"),
                seam = vanilla("gray_concrete"), line = vanilla("light_gray_concrete"), iron = Blocks.IRON_BLOCK.defaultBlockState(), sea = Blocks.SEA_LANTERN.defaultBlockState(),
                bulb = vanilla("waxed_copper_bulb").setValue(CopperBulbBlock.LIT, false), bulbLit = bulb.setValue(CopperBulbBlock.LIT, true);

        /** A vanilla block by its id: 26.3 keeps the dyed and the copper blocks in collections, not as fields of Blocks. */
        private static BlockState vanilla(String id) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id)).defaultBlockState();
        }

        M() {
            // "amber" only so the kit's own blocks (the cache) are there; nothing of Amber's is drawn
            super("amber", new Accents(b("lab_panel_lit"), b("lab_panel_lit"), b("lab_tank"), b("lab_rack"), b("lab_hologram"), b("lab_screws"), b("lab_rack"),
                    Blocks.IRON_BARS.defaultBlockState(), b("lab_panel")));
        }
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    private Arena lord;
    private final List<Arena> halls = new ArrayList<>();

    public SealedWing(Ctx ctx) {
        super(ctx, 0x5345414C4544L);
        generate();
        for (Part p : parts) {
            if (p.name.equals("lord")) lord = (Arena) p;
            if (p instanceof Arena a) halls.add(a);
        }
    }

    @Override
    protected M mats() {
        return m();
    }

    /** Its beats (SPEC §72.3): panel walls to run, bulkheads that close; the slide under the closing bulkheads. */
    @Override
    protected Composer.Accents accents() {
        return Composer.Accents.of(Beat.TEETH, Family.WALL, 3, Family.LOW, 2);
    }

    /** Its beats in its own blocks (SPEC §72.2); the rest is its rock, floor and half step. */
    @Override
    protected BeatSkin beatSkin(SealedWing.M m) {
        return super.beatSkin(m).scuff(m.panel).lip(m.frame).beam(m.frame).rail(m.iron).deck(m.plate).plinth(m.seam).dust(m.line).glow(m.strip).line(BeatSkin.named("hanging_ladder"));
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("test_subject*5", "security_unit");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("security_unit*2", "coil_unit");
    }

    @Override
    protected BlockState spitOn(M m) {
        return m.bulbLit;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.bulb;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.frame;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.glass;
    }

    @Override
    protected double wild() {
        return 0;
    }

    /** Smooth pale panels in courses four high, a dark seam between. */
    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        return Math.floorMod(y, 4) == 0 ? m.seam : m.panel;
    }

    @Override
    protected BlockState deep(M m) {
        return m.seam;
    }

    private static boolean built(M m, BlockState s) {
        return s == m.panel || s == m.seam;
    }

    /** Whether a column is two or more in from every side of its part: the middle of a way. */
    private static boolean middle(Part part, int x, int z) {
        return part.feet2(x + 2, z) != NONE && part.feet2(x - 2, z) != NONE && part.feet2(x, z + 2) != NONE && part.feet2(x, z - 2) != NONE;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        if (part.name.equals("hidden")) return (x + z & 1) == 0 ? m.strip : m.panel;
        boolean gx = Math.floorMod(x, 4) == 0, gz = Math.floorMod(z, 4) == 0;
        // rooms: plate laid on a grid of pale joints, a light at every crossing
        if (part instanceof Cave) return gx && gz ? m.strip : gx || gz ? m.panel : m.plate;
        // ways: plate with a lit line down the middle that leads you on
        return middle(part, x, z) ? m.strip : m.plate;
    }

    @Override
    protected BlockState step(M m) {
        return m.plateSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.frame;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        if (part instanceof Shaft || part instanceof Chasm) {
            // down a shaft and along a crossing the wing has one thing in its walls: a lit band right round, every eighth course
            if ((wx != 0 || wz != 0) && Math.floorMod(y, 8) == 0 && built(m, v.get(x + wx, y, z + wz))) v.set(x + wx, y, z + wz, m.strip);
            return;
        }
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int feet = Part.airFrom(part.feet2(x, z)), up = y - feet, roof = part.roof(x, z), down = roof - 1 - y;
        boolean free = v.get(x, y, z) == null;
        if (wall) {
            int ax = x + wx, az = z + wz, run = wx != 0 ? az : ax, bay = Math.floorMod(run, 8);
            if (built(m, v.get(ax, y, az))) {
                if (up == 0 || down == 0 && up >= 2) {
                    // the strips: one at knee height, one under the roof, all the way along; a light a cell out from every third cell of them
                    v.set(ax, y, az, m.strip);
                    if (free && Math.floorMod(run, 3) == (up == 0 ? 0 : 1)) {
                        v.set(x, y, z, POOL);
                        free = false;
                    }
                } else if (!narrow && up >= 1 && up <= (room ? 3 : 2) && down >= 1) {
                    // what stands in the walls between the strips: a rack of instruments, a tank still lit, a hatch that was sealed for good
                    if (bay == 4) v.set(ax, y, az, m.rack);
                    else if (room && bay == 1) v.set(ax, y, az, m.tank);
                    else if (bay == 7 && up <= 2 && rnd(Math.floorDiv(run, 8), wx != 0 ? ax : az, 991) < 0.5) v.set(ax, y, az, up == 1 ? m.frame : m.iron);
                }
            }
        }
        // lights set flush in the ceiling on a grid of four, and under each a second light part way down, so every floor is evenly lit
        if (underRoof && v.get(x, y + 1, z) == m.seam) v.set(x, y + 1, z, m.panel); // a ceiling is all panel: the seams are for walls
        if (underRoof && Math.floorMod(x, 4) == 1 && Math.floorMod(z, 4) == 1 && built(m, v.get(x, y + 1, z))) {
            v.set(x, y + 1, z, m.sea);
            int low = Math.max(feet + 2, y - 4);
            if (low < y && v.get(x, low, z) == null) v.set(x, low, z, POOL);
        }
        if (!onFloor || narrow || !room || up > 0 || !free) return;
        // on the floor of a room, only at the foot of a real wall, and nothing with a body: a projection still running, a few dropped screws
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        if (!foot) return;
        double own = rnd(x, z, 992);
        if (own < 0.07) v.set(x, y, z, m.hologram);
        else if (own < 0.13) v.set(x, y, z, m.screws);
    }

    @Override
    protected void skinSpecial(V v, M m) {
        // every round hall (the Lord's, a Warden's): its pillars are tanks, a glowing core banded with panel; a lit ring in the Lord's floor
        for (Arena hall : halls) {
            boolean great = hall == lord;
            for (int x = Math.max(hall.x0, v.x0 - 1); x <= Math.min(hall.x1, v.x0 + 16); x++) for (int z = Math.max(hall.z0, v.z0 - 1); z <= Math.min(hall.z1, v.z0 + 16); z++) {
                if (hall.out(x, z) >= 1) continue;
                double d = Math.hypot(x - hall.cx, z - hall.cz);
                if (hall.pillar(x, z)) {
                    int beside = 0;
                    for (int[] s : SIDES) if (hall.pillar(x + s[0], z + s[1])) beside++;
                    for (int y = hall.feet; y < hall.roof(x, z); y++) {
                        int up = y - hall.feet;
                        v.set(x, y, z, up == 0 || up % 4 == 3 ? m.panel : beside == 4 ? m.sea : m.tank);
                    }
                } else if (d < 2.2) v.set(x, hall.feet - 1, z, m.sea);
                else if (great && (Math.abs(d - 9.4) < 0.55 || Math.abs(d - 17.4) < 0.55)) v.set(x, hall.feet - 1, z, m.strip);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xd8f4ff, 0.6F);
        if (lord != null) air.light(lord.cx, lord.feet + 4, lord.cz, 0xbff0ff, 20, 0.7F);
        return airOf(air);
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): a laboratory: bays, a walk round a core, a pit with something in it, conduits to cross on. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.HALL, Cave.Form.PIERS, Cave.Form.PIT, Cave.Form.RING), List.of(Arena.Plan.SQUARE, Arena.Plan.RING),
                List.of(Arena.Plan.SQUARE, Arena.Plan.ROTUNDA), List.of(Chasm.Kind.BEAMS, Chasm.Kind.HANGERS, Chasm.Kind.FALLING, Chasm.Kind.BRIDGE), true, 2.6, 5);
    }

    /** Its small pieces (SPEC §50.9 "Depth"): panel coved at floor and roof, moss that got in from somewhere, screws, cables and lights let down. */
    @Override
    protected Trim trim(SealedWing.M m) {
        return new Trim(named("lab_panel_stairs"), named("lab_panel_stairs"), named("lab_moss_drift"), named("lab_screws"),
                namedAll("lab_moss"), namedAll("lab_cables", "lab_light"), 0.6);
    }

    /** The colour of its light in the air (the same as its biome's). */
    @Override
    protected int airTint() {
        return 0xe0f4ff;
    }
}
