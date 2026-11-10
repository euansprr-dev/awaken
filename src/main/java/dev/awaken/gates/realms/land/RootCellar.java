package dev.awaken.gates.realms.land;

import dev.awaken.gates.block.ThemeBlocks;
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
 * The Root Cellars (SPEC §50.9): the Fane skin on a run rolled from the seed. The undercroft of an overgrown temple: halls
 * of pale cut stone laid in courses, half built and half taken back (it wanders a little), with roots as thick as a man
 * running through the rock and out of the walls, moss along the foot of everything, vines down from the roofs and leaves
 * that have come through them and glow. Braziers still burn against the walls. The Vine Seat's own things live in it, and
 * Drip Lurkers in the roofs.
 */
public final class RootCellar extends GeneratedCrawl<VineSeat.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockState POOL = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13), GLEAM = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 10);
    private static final List<List<String>> HERDS = List.of(List.of("thorn_hound*3", "thorn_archer*2"), List.of("vine_creaker*2", "thorn_archer*2"),
            List.of("~drip_lurker*3", "thorn_hound*2"), List.of("thorn_archer*3", "incense_priest"));

    private final SimplexNoise root, patch;
    private final BlockState fire;
    private Arena lord;

    public RootCellar(Ctx ctx) {
        super(ctx, 0x524F4F54L);
        root = N.noise(seed + 951);
        patch = N.noise(seed + 952);
        fire = VineSeat.m().brazier.setValue(ThemeBlocks.Touchstone.LIT, true);
        generate();
        for (Part p : parts) if (p.name.equals("lord")) lord = (Arena) p;
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
        return List.of("thorn_hound*4", "vine_creaker");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("thorn_archer*2", "thorn_hound");
    }

    /** A carved face in the wall; a leaf lights behind it before it spits. */
    @Override
    protected BlockState spitOn(VineSeat.M m) {
        return m.leafLit;
    }

    @Override
    protected BlockState spitOff(VineSeat.M m) {
        return m.carving;
    }

    @Override
    protected BlockState tooth(VineSeat.M m) {
        return m.pale;
    }

    @Override
    protected BlockState thinFloor(VineSeat.M m) {
        return m.planks;
    }

    @Override
    protected double wild() {
        return 0.5;
    }

    /** How near a root runs to this cell: under 1 is inside one. Two families of roots, one creeping level and one going down. */
    private double rootAt(int x, int y, int z) {
        double a = Math.abs(root.get(x / 13.0 + y / 31.0, z / 13.0 - y / 27.0)) / 0.055, b = Math.abs(root.get(x / 9.0 + 70, y / 17.0 + z / 9.0)) / 0.045;
        return Math.min(a, b);
    }

    @Override
    protected BlockState rock(VineSeat.M m, int x, int y, int z) {
        // roots through the stone
        if (rootAt(x, y, z) < 1) return rnd(x * 5L + y, z, 953) < 0.3 ? m.barkMoss : m.bark;
        // cut stone in courses: one stone runs three cells, every course starts somewhere else, a band course every seventh
        if (Math.floorMod(y, 7) == 0 && rnd(Math.floorDiv(x, 4), Math.floorDiv(z, 4), 954) < 0.55) return m.band;
        int off = (int) (rnd(y, 0, 955) * 3);
        double n = rnd(Math.floorDiv(x + off, 3) * 31 + Math.floorDiv(z + off, 3), y, 956);
        return n < 0.58 ? m.stoneA : n < 0.8 ? m.worn : m.pale;
    }

    @Override
    protected BlockState deep(VineSeat.M m) {
        return m.deep;
    }

    @Override
    protected BlockState skinFloor(VineSeat.M m, Part part, int x, int y, int z) {
        double n = rnd(x, z, 957), green = patch.get(x / 7.0, z / 7.0);
        if (green > (part.name.equals("hidden") ? 0.0 : 0.38)) return m.moss;
        if (part.name.equals("hidden")) return n < 0.6 ? m.hexstone : m.pale;
        return n < 0.7 ? m.flag : n < 0.86 ? m.stoneA : n < 0.95 ? m.worn : m.moss;
    }

    @Override
    protected BlockState step(VineSeat.M m) {
        return m.flagSlab;
    }

    /** A wall of thorn grows across the way and dies back. */
    @Override
    protected BlockState doorBlock(VineSeat.M m) {
        return m.thorn;
    }

    private static boolean room(Part part) {
        String n = part.name;
        return n.startsWith("fight") || n.equals("ambush") || n.equals("warden") || n.equals("lord") || n.equals("root") || n.equals("side") || n.equals("hidden");
    }

    @Override
    protected void adorn(V v, VineSeat.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        // the thin ways stay bare: there is no room in them for anything
        if (part.name.equals("slip") || part.name.startsWith("side_")) return;
        boolean wall = wx != 0 || wz != 0, shaft = part instanceof Shaft;
        int up = y - Part.airFrom(part.feet2(x, z));
        if (wall) {
            int ax = x + wx, az = z + wz;
            BlockState at = v.get(ax, y, az);
            if (at == m.stoneA || at == m.worn || at == m.pale || at == m.band) {
                // a carved face or a relief at head height here and there; moss over the lowest courses in patches
                double c = rnd(ax * 7L + y, az, 958);
                if (up == 1 && c < 0.11) v.set(ax, y, az, c < 0.04 ? m.carving : m.relief);
                else if (up <= 1 && patch.get(ax / 6.0 + y / 5.0, az / 6.0 - y / 5.0) > 0.05) v.set(ax, y, az, m.moss);
            } else if ((at == m.bark || at == m.barkMoss) && up >= 3 && !shaft && !onFloor && rootAt(ax, y, az) < 0.55 && v.get(x, y, z) == null) {
                // where the middle of a root meets the room it stands proud of the wall, well over your head
                v.set(x, y, z, m.bark);
                return;
            }
        }
        if (underRoof && !onFloor) {
            // leaves have come through the roof in clumps, some of them lit: they are the light in the ways. Vines hang from the rest
            double clump = rnd(Math.floorDiv(x, 4), Math.floorDiv(z, 4), 959), own = rnd(x, z, 960);
            BlockState over = v.get(x, y + 1, z);
            boolean stone = over == m.stoneA || over == m.worn || over == m.pale || over == m.band;
            // (only where the roof is high enough that a clump of them is over you and not in your face)
            if (stone && up >= 3 && clump < 0.3 && own < 0.8) {
                v.set(x, y + 1, z, own < 0.3 ? m.leafLit : own < 0.6 ? m.leaf : m.leafDeep);
                if (own < 0.1 && v.get(x, y, z) == null) v.set(x, y, z, GLEAM);
            } else if (own > 0.86 && v.get(x, y, z) == null) {
                // one to three long, and never down to where a head is
                int len = Math.min(1 + (int) (rnd(x, z, 961) * 3), up - 2);
                for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, m.vines);
            }
        }
        if (!onFloor || shaft || up > 0) return;
        // a real wall stands at least two high beside you; the next step of a stair or a ledge is not one
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        if (v.get(x, y, z) != null) return;
        if (foot && room(part) && rnd(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 962) < 0.4 && rnd(x, z, 963) < 0.22) {
            // a brazier still burning against the wall, and a light over it so its pool reaches into the room
            v.set(x, y, z, fire);
            if (v.get(x, y + 2, z) == null) v.set(x, y + 2, z, POOL);
        } else if (rnd(x, z, 964) < (foot ? 0.3 : 0.03)) v.set(x, y, z, m.fern);
    }

    @Override
    protected void skinSpecial(V v, VineSeat.M m) {
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the hall stands on trunks, each with a band of the temple's stone left round it; a ring of moss in the floor
            if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) v.set(x, y, z, (y - lord.feet) % 6 == 5 ? m.band : rnd(x * 3L + y, z, 965) < 0.25 ? m.barkMoss : m.bark);
            else if (Math.abs(d - 9.4) < 0.8) v.set(x, lord.feet - 1, z, m.moss);
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, 150, 4, 3, 0xd8f0b0, 0.75F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x2f4a36, 0.5F).light(lord.cx, lord.feet + 5, lord.cz, 0xc8e070, 18, 0.6F);
        return air.build();
    }
}
