package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Cinder Workings (SPEC §50.9): the Ember Mines' skin on a run rolled from the seed. A mine cut into warm dark rock,
 * worked out and still burning. It was dug, so it wanders. The light is the rock's own: seams of ember ore and cracks that
 * glow run through the walls and roofs, magma shows low in the walls of every room and in a crack in the middle of its
 * floor, and each of those has an unseen light a cell out, so a room is lit to its middle. Timber props still stand along
 * the ways and the miners' lamps along the foot of the walls. What worked it never left.
 */
public final class CinderWorkings extends GeneratedCrawl<CinderWorkings.M> {
    /**
     * The theme has no Realm family of its own: the Ember Mines' blocks, looked up one by one. Its own basalt, planks and timber were painted
     * for the old gates and are too dark to light (a third as bright as a Realm family's stone: no lamp shows them), so the rock here is the
     * theme's slag and ash with its ore and cracks and a little of the game's basalt, dark stone is kept for what should read dark (the
     * teeth), and the timber is the paler kind the old palette used.
     */
    public static final class M extends Mats {
        final BlockState cracked = b("ember_basalt_cracked"), slag = b("ember_slag"), ash = b("ember_ash"), ore = b("gate_ember_ore"), magma = b("magma_crust"),
                lamp = b("mine_lamp"), crystal = b("ember_crystal"), cinders = b("ember_cinders"), slagSlab = b("ember_slag_slab"),
                basalt = Blocks.BASALT.defaultBlockState(), smooth = Blocks.SMOOTH_BASALT.defaultBlockState(), dressed = Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), boards = Blocks.SPRUCE_PLANKS.defaultBlockState(),
                grate = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true).setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true),
                pool = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);

        M() {
            // "amber" only so the kit's own blocks (the cache) exist; nothing of Amber's is drawn here
            super("amber", new Accents(b("ember_basalt_cracked"), b("ember_slag"), b("magma_crust"), Blocks.IRON_CHAIN.defaultBlockState(), b("mine_lamp"), b("ember_cinders"), b("ember_bellows"),
                    Blocks.IRON_BARS.defaultBlockState(), b("mine_planks")));
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (CinderWorkings.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final List<List<String>> HERDS = List.of(List.of("cinder_miner*3", "ember_imp*2"), List.of("slag_cube*3", "scorch_crawler*3"),
            List.of("ember_imp*3", "slag_boar"), List.of("scorch_crawler*5", "cinder_miner"));

    private final SimplexNoise seam, patch;
    private Arena lord;
    private final List<Cave> rooms = new ArrayList<>();

    public CinderWorkings(Ctx ctx) {
        super(ctx, 0x43494E44L);
        seam = N.noise(seed + 971);
        patch = N.noise(seed + 972);
        generate();
        for (Part p : parts) {
            if (p.name.equals("lord")) lord = (Arena) p;
            if (p instanceof Cave c && !p.name.equals("root")) rooms.add(c);
        }
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
        return List.of("scorch_crawler*5", "ember_imp*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("cinder_miner*2", "slag_cube");
    }

    /** A cracked stone in the wall; the crack fills with magma before it spits. */
    @Override
    protected BlockState spitOn(M m) {
        return m.magma;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.cracked;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.dressed;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.boards;
    }

    @Override
    protected double wild() {
        return 0.8;
    }

    /** How near a seam runs to this cell: under 1 is inside one. One family lies nearly level, as ore does; one runs down, as a crack does. */
    private double seamAt(int x, int y, int z) {
        double a = Math.abs(seam.get(x / 15.0 + 20, y / 5.0 + z / 37.0)) / 0.055, b = Math.abs(seam.get(x / 8.0 + y / 21.0, z / 8.0 - y / 25.0)) / 0.04;
        return Math.min(a, b);
    }

    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        double s = seamAt(x, y, z), n = rnd(x * 73L + y, z, 973);
        // a seam: ember ore, hotter at its heart
        if (s < 1) return s < 0.4 ? (n < 0.3 ? m.magma : m.cracked) : n < 0.75 ? m.ore : m.cracked;
        // slag in beds, with ash caught in it and a column of basalt here and there
        double bed = patch.get(x / 9.0 + 60, y / 4.0 + z / 9.0);
        return bed > 0.2 ? (n < 0.8 ? m.ash : m.slag) : n < 0.12 ? m.basalt : n < 0.3 ? m.ash : m.slag;
    }

    @Override
    protected BlockState deep(M m) {
        return m.slag;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        double n = rnd(x, z, 974), grey = patch.get(x / 6.0 + 30, z / 6.0);
        // ash lies in drifts over worn stone, with slag trodden into it and a crack still glowing here and there
        if (grey > 0.35) return n < 0.8 ? m.ash : m.slag;
        if (part.name.equals("hidden")) return n < 0.5 ? m.ore : m.dressed;
        return n < 0.4 ? m.slag : n < 0.76 ? m.ash : n < 0.88 ? m.smooth : n < 0.97 ? m.slag : m.cracked;
    }

    @Override
    protected BlockState step(M m) {
        return m.slagSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.grate;
    }

    private static boolean plain(M m, BlockState s) {
        return s == m.basalt || s == m.slag || s == m.ash;
    }

    private static boolean hot(M m, BlockState s) {
        return s == m.ore || s == m.cracked || s == m.magma;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        // the thin ways stay bare (there is no room in them for anything) but for the cracks that light them
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, shaft = part instanceof Shaft;
        int up = y - Part.airFrom(part.feet2(x, z));
        boolean free = v.get(x, y, z) == null;
        if (wall) {
            int ax = x + wx, az = z + wz;
            BlockState at = v.get(ax, y, az);
            double own = rnd(ax * 7L + y, az, 975);
            if (room && up <= 1 && plain(m, at) && patch.get(ax / 5.0 + y / 6.0, az / 5.0 - y / 6.0) > 0.12) {
                // magma showing low in a room's wall, and its pool a cell out so it reaches the floor
                v.set(ax, y, az, m.magma);
                if (up == 1 && free && own < 0.6) v.set(x, y, z, m.pool);
            } else if (!room && up == 2 && !hot(m, at) && V.holds(at) && Math.floorMod(ax * 3 + az * 2, 5) == 0) {
                // a crack glowing over your head every few steps along a way, so no stretch of it is left dark
                v.set(ax, y, az, rnd(ax, az, 976) < 0.3 ? m.magma : m.cracked);
                if (free) v.set(x, y, z, m.pool);
            } else if (hot(m, at) && up >= 1 && !onFloor && free && !narrow && own < (room ? 0.5 : 0.3)) v.set(x, y, z, m.pool);
        }
        if (narrow) return;
        if (underRoof && !onFloor && up >= 2) {
            // seams break through the roof in patches: a room is lit from over its middle as well as from its walls
            BlockState over = v.get(x, y + 1, z);
            double clump = rnd(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 977), own = rnd(x, z, 978);
            if (plain(m, over) && (room ? clump < 0.4 && own < 0.4 : own < 0.035)) {
                v.set(x, y + 1, z, own < 0.12 ? m.magma : own < 0.25 ? m.ore : m.cracked);
                if ((own < 0.25 || !room) && v.get(x, y, z) == null) v.set(x, y, z, m.pool);
            } else if (hot(m, over) && own < 0.5 && v.get(x, y, z) == null) v.set(x, y, z, m.pool);
        }
        if (!onFloor || shaft || up > 0 || v.get(x, y, z) != null) return;
        // a real wall stands at least two high beside you; the next step of a stair or a ledge is not one
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        double own = rnd(x, z, 979);
        if (foot && part.name.startsWith("way") && own < 0.05) {
            // a timber prop, floor to roof against the wall
            for (int k = y, roof = part.roof(x, z); k < roof && v.get(x, k, z) == null; k++) v.set(x, k, z, m.post);
        } else if (foot && room && rnd(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 980) < 0.45 && own < 0.2) {
            // a miner's lamp left burning at the foot of the wall
            v.set(x, y, z, m.lamp);
            if (v.get(x, y + 2, z) == null) v.set(x, y + 2, z, m.pool);
        } else if (own > (foot ? 0.86 : 0.985)) v.set(x, y, z, m.crystal);
        else if (own > 0.7 && own < 0.78) v.set(x, y, z, m.cinders);
    }

    @Override
    protected void skinSpecial(V v, M m) {
        for (Cave r : rooms) {
            // a crack of magma in the middle of every room's floor, and its light over it: lit to the middle, whatever the walls do
            if (r.cx + 2 < v.x0 - 1 || r.cx - 2 > v.x0 + 16 || r.cz + 2 < v.z0 - 1 || r.cz - 2 > v.z0 + 16) continue;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 4 || rnd(r.cx + dx, r.cz + dz, 981) < 0.3 && (dx != 0 || dz != 0)) continue;
                if (V.holds(v.get(r.cx + dx, r.feet - 1, r.cz + dz))) v.set(r.cx + dx, r.feet - 1, r.cz + dz, dx == 0 && dz == 0 ? m.magma : m.cracked);
            }
            if (v.get(r.cx, r.feet + 2, r.cz) == null) v.set(r.cx, r.feet + 2, r.cz, m.pool);
        }
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the hall stands on pillars of slag with a band of ore and magma round each; two rings of it burn in the floor
            if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) v.set(x, y, z, (y - lord.feet) % 6 == 3 ? m.magma : (y - lord.feet) % 6 == 2 ? m.ore : m.slag);
            else if (Math.abs(d - 9.4) < 0.6 || Math.abs(d - 17.0) < 0.6) {
                v.set(x, lord.feet - 1, z, rnd(x, z, 982) < 0.7 ? m.magma : m.cracked);
                if (Math.floorMod(x + z * 3, 4) == 0 && v.get(x, lord.feet + 2, z) == null) v.set(x, lord.feet + 2, z, m.pool);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xffc890, 0.8F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x7a2a12, 0.45F).light(lord.cx, lord.feet + 4, lord.cz, 0xff8030, 18, 0.9F);
        return air.build();
    }
}
