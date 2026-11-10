package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Sporewells (SPEC §50.9): the Fungal Hollow's skin on a run rolled from the seed. Wells and burrows grown through by
 * one enormous fungus: dark damp earth and stone with its pale threads running through them in veins, shelves of it
 * stepping out of the walls, soft moss underfoot, and glowcaps hanging from every roof. The one dungeon lit cold and
 * soft, teal and pale violet, and lit to the middle of every room.
 */
public final class Sporewell extends GeneratedCrawl<Sporewell.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final List<List<String>> HERDS = List.of(List.of("sporeling*4", "mycelid*2"), List.of("capweaver*3", "puffcap*2"),
            List.of("mycelid*3", "hollow_tender"), List.of("~drip_lurker*3", "sporeling*3"));
    /**
     * How far apart the roof's glowcap clusters are: one in every square of this many cells, so no floor is far from a pool.
     * (At four the roof was a ceiling of lit gills from wall to wall; at six each cluster hangs in dark of its own.)
     */
    private static final int GRID = 6;

    /** The Fungal Hollow has no Realm family of its own: these are its gate's blocks, by id. Amber's names only give the kit its cache. */
    public static final class M extends Mats {
        final BlockState stone = b("fungal_rock"), veined = b("fungal_rock_veined"), loam = b("fungal_loam"), moss = b("fungal_moss"), stem = b("glowcap_stem"),
                cap = b("glowcap_cap"), gills = b("glowcap_gills"), sprouts = b("glowcap_sprouts"), spores = b("fungal_spores"), loamSlab = b("fungal_loam_slab");

        M() {
            super("amber", new Accents(null, null, null, null, null, null, null, null, null));
        }
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    private final SimplexNoise vein, patch;
    private Arena lord;

    public Sporewell(Ctx ctx) {
        super(ctx, 0x53504F52L);
        vein = N.noise(seed + 971);
        patch = N.noise(seed + 972);
        generate();
        for (Part p : parts) if (p.name.equals("lord") && p instanceof Arena a) lord = a;
    }

    @Override
    protected M mats() {
        return m();
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("sporeling*6", "puffcap*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("mycelid*2", "capweaver");
    }

    /** A dark cap in the wall; its gills light before it spits. */
    @Override
    protected BlockState spitOn(M m) {
        return m.gills;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.cap;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.stem;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.moss;
    }

    @Override
    protected double wild() {
        return 1;
    }

    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        // the fungus's threads: thin pale veins that wander through everything, wherever two folds of the noise meet
        // (the pale stalk itself at the heart of each vein, lit rock either side of it: the gate's own stone is near black, and
        // without these a wall reads as nothing at all)
        double thread = Math.abs(vein.get(x / 11.0 + y / 13.0, z / 11.0 - y / 9.0));
        if (thread < 0.05) return m.stem;
        if (thread < 0.17) return m.veined;
        // damp earth in beds between the stone
        return Math.floorMod((int) Math.floor(y + 3 * patch.get(x / 16.0, z / 16.0)), 6) == 0 ? m.loam : m.stone;
    }

    @Override
    protected BlockState deep(M m) {
        return m.stone;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        // a ledge over a drop is bare earth, so the mossy stretch that gives way can be told from it
        if (part instanceof Shaft) return m.loam;
        // moss in beds on dark earth (all moss, the floor was one bright speckle from wall to wall)
        return patch.get(x / 9.0 + 40, z / 9.0) > -0.05 ? m.moss : m.loam;
    }

    @Override
    protected BlockState step(M m) {
        return m.loamSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.stem;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, shaft = part instanceof Shaft;
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int up = y - Part.airFrom(part.feet2(x, z));
        if (wall) {
            // the wall beside you: brackets of the fungus have come through it, each a dark cap with its gills glowing along
            // the underside, and a light in the air beside some of those, so the shafts and the narrow ways are lit from their
            // own walls. (The first pass set gills, stalk and cap cell by cell at random, and in the real game every wall read
            // as a chequer of five blocks; a wall is rock now, with whole brackets in it and moss creeping up from its foot.)
            int ax = x + wx, az = z + wz;
            BlockState at = v.get(ax, y, az);
            double edge = shaft || narrow ? 0.3 : 0.42;
            if (at == m.stone || at == m.loam || at == m.veined) {
                if (bracket(ax, y, az) > edge) {
                    boolean under = up <= 0 || bracket(ax, y - 1, az) <= edge;
                    v.set(ax, y, az, under ? m.gills : m.cap);
                    if (up >= 1 && rnd(ax * 17L + y, az, 975) < (under ? 0.6 : shaft || narrow ? 0.2 : 0)) {
                        v.set(x, y, z, m.light);
                        return;
                    }
                } else if (at != m.veined && up <= (patch.get(ax / 6.0 + 90, az / 6.0) > 0.1 ? 1 : -1)) v.set(ax, y, az, m.moss);
            }
        }
        if (narrow || shaft) return;
        // shelves of the fungus step out of a room's walls above head height
        if (wall && room && !onFloor && !underRoof && up >= 3 && up <= 6 && v.get(x + wx, y, z + wz) == m.cap && rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2) * 7L + up / 2, 976) < 0.3) {
            v.set(x, y, z, m.cap);
            return;
        }
        // at the foot of a real wall (two cells high beside you): a standing glowcap now and then, sprouts and spores round it
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        if (onFloor && up <= 0) {
            double cluster = rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 977), own = rnd(x, z, 978);
            if (foot && room && cluster < 0.4 && own < 0.4 && v.get(x, y + 1, z) == null && v.get(x, y + 2, z) == null) {
                v.set(x, y, z, m.stem);
                v.set(x, y + 1, z, m.gills);
                return;
            }
            if (foot && own < 0.4) v.set(x, y, z, m.sprouts);
            else if (own > 0.93) v.set(x, y, z, m.spores);
        }
        if (underRoof && !onFloor && !wall && up >= 3) {
            // glowcaps hang from the roof, one cluster to every few cells: a stalk, a dark cap, the lit gills lowest, and a
            // light under each so its pool reaches the floor. Nothing comes lower than three clear cells over the floor.
            int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID);
            boolean heart = Math.floorMod(x, GRID) == (int) (rnd(gx, gz, 979) * GRID) && Math.floorMod(z, GRID) == (int) (rnd(gx, gz, 980) * GRID);
            int room2 = up - 2; // cells from here down to the lowest it may reach
            if (heart) {
                int len = Math.min(room2, room ? 2 + (int) (rnd(x, z, 981) * (up > 9 ? 6 : 3)) : 1), low = y;
                for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) {
                    v.set(x, y - k, z, k == len - 1 ? m.gills : k == len - 2 ? m.cap : m.stem);
                    low = y - k;
                }
                if (v.get(x, low - 1, z) == null) v.set(x, low - 1, z, m.light);
                int over = y - up + 3; // and one just over head height, however high the roof is
                if (over < low - 1 && v.get(x, over, z) == null) v.set(x, over, z, m.light);
            } else if (rnd(x, z, 982) < (room ? 0.03 : 0.05)) {
                // small ones between the clusters
                int len = Math.min(room2, 1 + (int) (rnd(x, z, 983) * 2));
                for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, k == len - 1 ? m.gills : m.stem);
            }
        }
    }

    /** Where a bracket of the fungus has come through the rock: whole patches, a few cells across. */
    private double bracket(int x, int y, int z) {
        return patch.get(x / 5.0 + y / 6.0, z / 5.0 - y / 5.0);
    }

    @Override
    protected void skinSpecial(V v, M m) {
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the Lord's hall stands on stalks as thick as trees, banded with lit gills; a ring of them glows in the floor
            if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) v.set(x, y, z, (y - lord.feet) % 5 == 3 ? m.gills : m.stem);
            else if (d < 1.6 || Math.abs(d - 9.4) < 0.55 || Math.abs(d - 16.4) < 0.55) v.set(x, lord.feet - 1, z, m.gills);
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xbfe8e0, 0.7F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x4a8a90, 0.5F).light(lord.cx, lord.feet + 4, lord.cz, 0x80e0d8, 18, 0.8F);
        return airOf(air);
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): wells grown through: round hollows, a well you walk round, caps to cross on. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.OVAL, Cave.Form.RING, Cave.Form.PIT, Cave.Form.RIFT), List.of(Arena.Plan.RING, Arena.Plan.TIERS),
                List.of(Arena.Plan.ROTUNDA, Arena.Plan.RING), List.of(Chasm.Kind.PILLARS, Chasm.Kind.HANGERS, Chasm.Kind.FORD, Chasm.Kind.STEPS), false, 2.3, 4);
    }

    /** Its small pieces (SPEC §50.9 "Depth"): sprouts everywhere off the way, threads of it over the earth, moss drifted against the rock, roots that glow. */
    @Override
    protected Trim trim(Sporewell.M m) {
        return new Trim(named("fungal_rock_stairs"), named("fungal_rock_stairs"), named("fungal_moss_drift"), named("fungal_spores"),
                namedAll("glowcap_sprouts", "glowcap_sprouts", "mycelium_threads"), namedAll("lumen_roots"), 1.3);
    }

    /** The colour of its light in the air (the same as its biome's). */
    @Override
    protected int airTint() {
        return 0xa8f0e0;
    }
}
