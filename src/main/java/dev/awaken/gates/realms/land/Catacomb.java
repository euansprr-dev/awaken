package dev.awaken.gates.realms.land;

import dev.awaken.gates.block.WorldBlocks;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.Land.Family;
import dev.awaken.gates.realms.kit.crawl.BeatSkin;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.gates.realms.kit.crawl.Composer;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Bone Roads (SPEC §50.9): the Knell skin on a run rolled from the seed. Roads cut for the dead under a crypt: round
 * vaults and straight ways in coursed stone (nothing here wanders: it was built), skulls set in the walls, bone packed
 * into them, candles gathered along the foot of every wall, a small bell hung here and there from the roof. The Hour
 * Crypt's own things live in it, and Hushlings in the rooms the candles don't reach.
 */
public final class Catacomb extends GeneratedCrawl<HourCrypt.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockState POOL = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private static final List<List<String>> HERDS = List.of(List.of("toll_ghoul*3", "crypt_archer*2"), List.of("crypt_archer*3", "bell_wisp*2"),
            List.of("~hushling*3", "toll_ghoul"), List.of("bell_wisp*4", "crypt_keeper"));

    private final SimplexNoise patch;
    private Arena lord;
    private final List<Shaft> shafts = new ArrayList<>();

    public Catacomb(Ctx ctx) {
        super(ctx, 0x424F4E45L);
        patch = N.noise(seed + 931);
        generate();
        for (Part p : parts) {
            if (p.name.equals("lord")) lord = (Arena) p;
            if (p instanceof Shaft s) shafts.add(s);
        }
    }

    @Override
    protected HourCrypt.M mats() {
        return HourCrypt.m();
    }

    /** Its beats (SPEC §72.3): niches to mantle, low vaults to slide under, bell ropes; the descent on the bell ropes. */
    @Override
    protected Composer.Accents accents() {
        return Composer.Accents.of(Beat.CHAINFALL, Family.LEDGE, 2, Family.LOW, 2, Family.GRIP, 2);
    }

    /** Its beats in its own blocks (SPEC §72.2); the rest is its rock, floor and half step. */
    @Override
    protected BeatSkin beatSkin(HourCrypt.M m) {
        return super.beatSkin(m).lip(m.pale).scuff(m.worn).beam(m.timber).rail(m.bronze).plinth(m.deep).dust(m.hexstone).deck(m.planks);
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("bell_wisp*4", "toll_ghoul*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("crypt_archer*2", "toll_ghoul");
    }

    @Override
    protected BlockState spitOn(HourCrypt.M m) {
        return m.gold;
    }

    @Override
    protected BlockState spitOff(HourCrypt.M m) {
        return m.bronzeDark;
    }

    @Override
    protected BlockState tooth(HourCrypt.M m) {
        return m.pale;
    }

    @Override
    protected BlockState thinFloor(HourCrypt.M m) {
        return m.planks;
    }

    @Override
    protected double wild() {
        return 0;
    }

    @Override
    protected BlockState rock(HourCrypt.M m, int x, int y, int z) {
        return y % 6 == 0 && rnd(x / 3, z / 3, 941) < 0.5 ? m.band : HourCrypt.course(m, x, y, z);
    }

    @Override
    protected BlockState deep(HourCrypt.M m) {
        return m.deep;
    }

    @Override
    protected BlockState skinFloor(HourCrypt.M m, Part part, int x, int y, int z) {
        double n = rnd(x, z, 942);
        if (part.name.equals("hidden")) return n < 0.5 ? m.hexstone : m.pale;
        return n < 0.58 ? m.flag : n < 0.82 ? m.stoneA : n < 0.94 ? m.worn : m.deep;
    }

    @Override
    protected BlockState step(HourCrypt.M m) {
        return m.flagSlab;
    }

    @Override
    protected BlockState doorBlock(HourCrypt.M m) {
        return m.barsAlong(true, true);
    }

    @Override
    protected void adorn(V v, HourCrypt.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean wall = wx != 0 || wz != 0;
        if (wall) {
            // the wall beside you: bone packed into it in patches, and at head height a skull set in a niche now and then
            int ax = x + wx, az = z + wz, up = y - Part.airFrom(part.feet2(x, z));
            BlockState at = v.get(ax, y, az);
            if (at == m.stoneA || at == m.worn || at == m.deep || at == m.pale || at == m.band) {
                if (up == 1 && rnd(ax * 7L + y, az, 943) < 0.14) v.set(ax, y, az, m.niche.setValue(WorldBlocks.Facing.FACING, wx > 0 ? Direction.WEST : wx < 0 ? Direction.EAST : wz > 0 ? Direction.NORTH : Direction.SOUTH));
                else if (up <= 3 && patch.get(ax / 7.0 + y / 9.0, az / 7.0 - y / 8.0) > 0.45) v.set(ax, y, az, m.ossuary);
            }
        }
        // a real wall stands at least two high beside you; the next step of a stair or a ledge is not one
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_"), low = part instanceof Shaft ? false : y - Part.airFrom(part.feet2(x, z)) <= 0;
        if (onFloor && foot && low && !narrow) {
            // candles gather along the foot of the wall in clusters, with a light over each so its pool reaches into the room; bones lie between
            double c = rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 944), own = rnd(x, z, 945);
            // (in the real game the first pass was too dark to read: half the wall's foot has candles now, and half of those a light)
            if (c < 0.5 && own < 0.75) {
                v.set(x, y, z, m.candles(2 + (int) (own * 4)));
                if (v.get(x, y + 2, z) == null && own < 0.4) v.set(x, y + 2, z, POOL);
            } else if (own < 0.07) v.set(x, y, z, m.bones);
        } else if (onFloor && low && !narrow && !wall && rnd(x, z, 946) < 0.02) v.set(x, y, z, m.bones);
        if (underRoof && !onFloor && !wall && rnd(x, z, 947) < 0.025) {
            // a small bell on its rope
            int len = Math.min(1 + (int) (rnd(x, z, 948) * 3), y - Part.airFrom(part.feet2(x, z)) - 2);
            for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, k == len - 1 ? m.smallBell : m.rope);
        }
    }

    @Override
    protected void skinSpecial(V v, HourCrypt.M m) {
        if (lord != null) {
            for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
                if (lord.out(x, z) >= 1) continue;
                double d = Math.hypot(x - lord.cx, z - lord.cz);
                if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) v.set(x, y, z, (y - lord.feet) % 6 == 5 ? m.band : m.pale);
                // rings of gilt in the floor, as under the great bell
                else if (d < 2.2 || Math.abs(d - 9.4) < 0.55 || Math.abs(d - 17.4) < 0.55) v.set(x, lord.feet - 1, z, m.gold);
            }
        }
        // bell ropes down the middle of every shaft, each with its bell
        for (Shaft s : shafts) {
            for (int[] c : new int[][]{{0, 0, 14}, {3, -2, 8}, {-3, 3, 18}, {2, 4, 5}, {-4, -2, 11}}) {
                int x = s.cx + c[0], z = s.cz + c[1], lo = s.top - c[2];
                if (x < v.x0 - 1 || x > v.x0 + 16 || z < v.z0 - 1 || z > v.z0 + 16) continue;
                for (int y = s.dome(x, z) - 1; y > lo; y--) v.set(x, y, z, m.rope);
                v.set(x, lo, z, m.smallBell);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0x9fb8e8, 0.75F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x3a4a6a, 0.5F);
        return airOf(air);
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): roads cut for the dead: long vaults, crossings, piers, and bridges the masons left. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.HALL, Cave.Form.CROSS, Cave.Form.PIERS, Cave.Form.HALL), List.of(Arena.Plan.NAVE, Arena.Plan.SQUARE),
                List.of(Arena.Plan.NAVE, Arena.Plan.CROSS), List.of(Chasm.Kind.BRIDGE, Chasm.Kind.FALLING, Chasm.Kind.LEDGES, Chasm.Kind.STEPS), true, 2.2, 4);
    }

    /** Its small pieces (SPEC §50.9 "Depth"): worn stone at the foot of the walls, dust in the corners, bones underfoot, webs under the vaults. */
    @Override
    protected Trim trim(HourCrypt.M m) {
        return new Trim(named("knell_stone_stairs"), named("knell_stone_stairs"), named("crypt_dust"), named("crypt_bones"),
                List.of(), namedAll("minecraft:cobweb"), 0.6);
    }

    /** The colour of its light in the air (the same as its biome's). */
    @Override
    protected int airTint() {
        return 0xffd9a0;
    }
}
