package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.Land.Family;
import dev.awaken.gates.realms.kit.crawl.BeatSkin;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.gates.realms.kit.crawl.Composer;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Glass Warrens (SPEC §50.9): the Glass Desert's skin on a run rolled from the seed. Warrens under a desert where
 * lightning has fused the sand: banded sandstone, dug and blasted, with runs and sheets of glass through it and the stone
 * burnt dark beside them; packed sand underfoot with shards along the walls; glass that dripped from the roofs and set;
 * and glass that still holds the heat, glowing amber and white in the walls. Bright and warm to the middle of every room.
 */
public final class GlassWarren extends GeneratedCrawl<GlassWarren.M> {
    private static final List<List<String>> HERDS = List.of(List.of("glass_husk*3", "sand_stalker*2"), List.of("heat_shimmer*3", "glass_husk*2"),
            List.of("sand_stalker*3", "prism_sentinel"), List.of("glass_husk*4", "heat_shimmer"));
    /** How far apart the roof's hanging glass is: one piece with heat in it to every square of this many cells. */
    private static final int GRID = 4;

    /** The Glass Desert has no Realm family of its own: these are its gate's blocks, by id. Amber's names only give the kit its cache. */
    public static final class M extends Mats {
        final BlockState sand = b("desert_sand"), rippled = b("desert_sand_rippled"), sandstone = b("desert_sandstone"), burnt = b("desert_brick"), tile = b("desert_tile"),
                glass = b("desert_glass"), hot = b("desert_glass_glowing"), crystal = b("desert_crystal"), shards = b("desert_shards"), slab = b("desert_sandstone_slab");

        M() {
            super("amber", new Accents(null, null, null, null, null, null, null, null, null));
        }

        /** Whether a block is the warren's own wall: stone, burnt stone, or glass set in it. */
        boolean rock(BlockState s) {
            return s == sandstone || s == burnt || s == glass || s == hot;
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

    public GlassWarren(Ctx ctx) {
        super(ctx, 0x474C4153L);
        vein = N.noise(seed + 981);
        patch = N.noise(seed + 982);
        generate();
        for (Part p : parts) if (p.name.equals("lord") && p instanceof Arena a) lord = a;
    }

    @Override
    protected M mats() {
        return m();
    }

    /** Its beats (SPEC §72.3): glass sheets to run along, glass floors that shatter, sand chutes; the shattering floor. */
    @Override
    protected Composer.Accents accents() {
        return Composer.Accents.of(Beat.CRUMBLE, Family.FLOW, 3, Family.WALL, 2, Family.LOW, 2);
    }

    /** Its beats in its own blocks (SPEC §72.2); the rest is its rock, floor and half step. */
    @Override
    protected BeatSkin beatSkin(GlassWarren.M m) {
        return super.beatSkin(m).scuff(m.glass).lip(m.sandstone).beam(m.timber).rail(m.burnt).plinth(m.burnt).dust(m.rippled).deck(m.sandstone).glow(m.hot);
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("sand_stalker*4", "heat_shimmer*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("glass_husk*2", "sand_stalker");
    }

    /** A dark glass eye in the wall; it glows before it flares. */
    @Override
    protected BlockState spitOn(M m) {
        return m.hot;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.glass;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.glass;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.glass;
    }

    @Override
    protected double wild() {
        return 0.9;
    }

    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        // where the lightning went: a run of glass at the heart of each stroke, the stone burnt dark either side of it
        double stroke = Math.abs(vein.get(x / 12.0 + y / 10.0, z / 12.0 - y / 14.0));
        if (stroke < 0.045) return m.glass;
        if (stroke < 0.12) return m.burnt;
        // and sheets of it lying along the beds, where sand lay between the courses
        int bed = Math.floorMod((int) Math.floor(y + 2.5 * patch.get(x / 18.0, z / 18.0)), 8);
        if (bed == 3 && patch.get(x / 9.0 + 70, z / 9.0) > 0.25) return m.glass;
        return bed == 0 ? m.burnt : m.sandstone;
    }

    @Override
    protected BlockState deep(M m) {
        return m.sandstone;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        if (part.name.equals("hidden")) return ((x + z) & 1) == 0 ? m.tile : m.sand;
        return patch.get(x / 6.0 + 20, z / 6.0) > 0.2 ? m.rippled : m.sand;
    }

    @Override
    protected BlockState step(M m) {
        return m.slab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.glass;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, drop = part instanceof Shaft || part instanceof Chasm;
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int up = y - Part.airFrom(part.feet2(x, z));
        if (wall) {
            // the wall beside you: glass that still holds the heat shows through it in patches, amber and white, with a light
            // a cell out from some of each patch; plain glass and burnt stone round them
            int ax = x + wx, az = z + wz;
            BlockState at = v.get(ax, y, az);
            double heat = patch.get(ax / 5.0 + y / 6.0, az / 5.0 - y / 5.0);
            if (m.rock(at) && heat > 0.46) { // (at 0.38 half of every wall glowed in the real game)
                v.set(ax, y, az, m.hot);
                if (up >= 1 && rnd(ax * 17L + y, az, 985) < 0.5) {
                    v.set(x, y, z, m.light);
                    return;
                }
            } else if (at == m.sandstone && heat > 0.2) v.set(ax, y, az, heat > 0.32 ? m.glass : m.burnt);
        }
        if (underRoof) {
            // the roof over you, the same, a little less of it
            BlockState over = v.get(x, y + 1, z);
            double heat = patch.get(x / 5.0 + 90, z / 5.0);
            if (m.rock(over) && heat > 0.34) v.set(x, y + 1, z, heat > 0.52 ? m.hot : m.glass);
        }
        if (narrow || drop) return;
        // along the foot of a real wall (rock two cells high beside you): shards, and crystals that have grown out of the glass and glow
        if (onFloor && up <= 0) {
            boolean foot = wall && m.rock(v.get(x + wx, y + 1, z + wz));
            double own = rnd(x, z, 986);
            if (foot && rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 987) < 0.4 && own < 0.6) v.set(x, y, z, m.crystal);
            else if (foot && own < 0.8 || own > 0.95) v.set(x, y, z, m.shards);
        }
        if (underRoof && !onFloor && !wall && up >= 3) {
            // glass that dripped from the roof and set: one long piece with the heat still in its tip to every few cells, and
            // a light under it so its pool reaches the floor; shorter cold ones between. Three cells stay clear over the floor.
            int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID), reach = up - 2;
            boolean heart = Math.floorMod(x, GRID) == (int) (rnd(gx, gz, 988) * GRID) && Math.floorMod(z, GRID) == (int) (rnd(gx, gz, 989) * GRID);
            if (heart) {
                int len = Math.min(reach, room ? 1 + (int) (rnd(x, z, 990) * (up > 9 ? 5 : 3)) : 1), low = y;
                for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) {
                    v.set(x, y - k, z, k == len - 1 ? m.hot : m.glass);
                    low = y - k;
                }
                if (v.get(x, low - 1, z) == null) v.set(x, low - 1, z, m.light);
                int over = y - up + 3; // and one just over head height, however high the roof is
                if (over < low - 1 && v.get(x, over, z) == null) v.set(x, over, z, m.light);
            } else if (rnd(x, z, 991) < (room ? 0.14 : 0.06)) {
                int len = Math.min(reach, 1 + (int) (rnd(x, z, 992) * 2));
                for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, m.glass);
            }
        }
    }

    @Override
    protected void skinSpecial(V v, M m) {
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // pillars of fused glass, the heat still in them in bands; a ring of glowing glass in the floor round the middle
            if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) v.set(x, y, z, (y - lord.feet) % 4 == 2 ? m.hot : m.glass);
            else if (d < 1.6 || Math.abs(d - 9.4) < 0.55 || Math.abs(d - 16.4) < 0.55) v.set(x, lord.feet - 1, z, m.hot);
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xffe6b0, 0.85F);
        if (lord != null) air.mist(lord.feet + 1, 4, 0xe0a850, 0.4F).light(lord.cx, lord.feet + 4, lord.cz, 0xffc870, 18, 0.9F);
        return airOf(air);
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): burrows: low round rooms, long cracks, a shelf of glass along one side, and glass that will not take your weight. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.OVAL, Cave.Form.RIFT, Cave.Form.TERRACE, Cave.Form.RING), List.of(Arena.Plan.RING, Arena.Plan.TIERS),
                List.of(Arena.Plan.RING, Arena.Plan.CROSS, Arena.Plan.TIERS), List.of(Chasm.Kind.PILLARS, Chasm.Kind.LEDGES, Chasm.Kind.STEPS, Chasm.Kind.FALLING), false, 2.2, 4);
    }

    /** Its small pieces (SPEC §50.9 "Depth"): sand drifted against the rock, shards, dry grass, crystals grown out of the glass. */
    @Override
    protected Trim trim(GlassWarren.M m) {
        return new Trim(named("desert_sandstone_stairs"), named("desert_sandstone_stairs"), named("desert_sand_drift"), named("desert_shards"),
                namedAll("desert_grass", "desert_grass", "desert_crystal"), List.of(), 0.9);
    }

    /** The colour of its light in the air (the same as its biome's). */
    @Override
    protected int airTint() {
        return 0xffc878;
    }
}
