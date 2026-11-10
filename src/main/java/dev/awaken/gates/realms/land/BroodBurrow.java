package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Lower Combs (SPEC §50.9): the Brood skin on a run rolled from the seed. Every one that forms is a different nest:
 * the same grey paper, comb and sacs as The Broodwarren, the same things living in it, laid out afresh each time.
 */
public final class BroodBurrow extends GeneratedCrawl<Broodwarren.M> {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final List<List<String>> HERDS = List.of(List.of("hive_larva*4", "honey_slime*2"), List.of("drone_bee*3", "hive_guard"),
            List.of("~drip_lurker*3", "hive_larva*3"), List.of("honey_slime*3", "drone_bee*2"));
    /** Hanging comb in a shaft, from its middle: x, z, how far under the ledge's top its lowest cell is, how fat. */
    private static final int[][] COMBS = {{0, 0, 15, 3}, {4, -3, 6, 2}, {-4, 2, 10, 2}, {3, 4, 19, 2}, {-3, -4, 3, 2}};

    private final SimplexNoise grain, patch;
    private Cave ambush;
    private Arena lord;
    private final List<Shaft> shafts = new ArrayList<>();

    public BroodBurrow(Ctx ctx) {
        super(ctx, 0x4255524FL);
        grain = N.noise(seed + 921);
        patch = N.noise(seed + 922);
        generate();
        for (Part p : parts) {
            if (p.name.equals("ambush")) ambush = (Cave) p;
            if (p.name.equals("lord")) lord = (Arena) p;
            if (p instanceof Shaft s) shafts.add(s);
        }
    }

    @Override
    protected Broodwarren.M mats() {
        return Broodwarren.m();
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("hive_larva*5", "drone_bee*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("!thousandfoot", "hive_guard", "drone_bee*2");
    }

    @Override
    protected int wardenPillars() {
        return 0;
    }

    @Override
    protected BlockState spitOn(Broodwarren.M m) {
        return m.spitterLit;
    }

    @Override
    protected BlockState spitOff(Broodwarren.M m) {
        return m.spitter;
    }

    @Override
    protected BlockState tooth(Broodwarren.M m) {
        return m.resin;
    }

    @Override
    protected BlockState thinFloor(Broodwarren.M m) {
        return m.cell;
    }

    @Override
    protected double wild() {
        return 1;
    }

    @Override
    protected BlockState rock(Broodwarren.M m, int x, int y, int z) {
        double r = rnd(x * 73L + y, z, 811);
        double mouth = Math.sqrt(x * x + (y - mouthFeet() - 2) * (y - mouthFeet() - 2) + z * z);
        if (mouth < 20 && mouth < 15 + 4 * grain.get(x / 6.0 + 50, z / 6.0)) return r < 0.18 ? m.paperDark : m.earth;
        int band = Math.floorMod((int) Math.floor(y + 2.6 * grain.get(x / 11.0, z / 11.0)), 7);
        if (band == 0) return r < 0.85 ? m.paperPale : m.paper;
        if (band == 4) return r < 0.8 ? m.paperDark : m.paper;
        return r < 0.1 ? m.paperDark : r > 0.95 ? m.paperPale : m.paper;
    }

    @Override
    protected BlockState deep(Broodwarren.M m) {
        return m.paperDark;
    }

    @Override
    protected BlockState skinFloor(Broodwarren.M m, Part part, int x, int y, int z) {
        if (part.name.equals("root")) return m.earth;
        if (part.name.equals("hidden")) return rnd(x, z, 821) < 0.7 ? m.jelly : m.resin;
        return rnd(x, z, 822) < 0.1 ? m.paperDark : m.resin;
    }

    @Override
    protected BlockState step(Broodwarren.M m) {
        return m.resinSlab;
    }

    @Override
    protected BlockState doorBlock(Broodwarren.M m) {
        return m.skin;
    }

    private double sacs(Part part) {
        String n = part.name;
        return n.equals("ambush") ? 0.4 : n.equals("hidden") ? 0.2 : n.startsWith("fight") ? 0.12 : n.equals("shaft") || n.equals("lord") ? 0.1 : n.equals("warden") ? 0.07 : 0.05;
    }

    private double awake(Part part) {
        String n = part.name;
        // (a crossing over a pool is lit from its walls alone, and in the real game the first one was near black)
        if (part instanceof Chasm) return 0.45;
        return n.equals("way0") ? 0.34 : n.equals("shaft") || n.equals("lord") ? 0.24 : n.equals("ambush") ? 0.2 : n.equals("gallery") ? 0.1 : 0.15;
    }

    private int sacLen(Part part, int x, int z) {
        return rnd(x, z, 801) >= sacs(part) ? 0 : 1 + (int) (rnd(x, z, 802) * 3);
    }

    @Override
    protected void adorn(V v, Broodwarren.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        if (part.name.equals("root")) return;
        if (wx != 0 || wz != 0) {
            int ax = x + wx, az = z + wz;
            BlockState at = v.get(ax, y, az);
            if ((at == m.paper || at == m.paperDark || at == m.paperPale) && patch.get(ax / 8.0 + y / 9.0, az / 8.0 - y / 7.0) > (part.name.equals("way0") ? 0.1 : part instanceof Chasm ? 0.2 : 0.42)) {
                v.set(ax, y, az, rnd(ax * 31L + y, az, 77) < awake(part) ? m.cellLit : m.cell);
            }
        }
        if (underRoof && !onFloor) {
            int len = Math.min(sacLen(part, x, z), y - Part.airFrom(part.feet2(x, z)) - 1);
            for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, m.sac);
        }
    }

    @Override
    protected void skinSpecial(V v, Broodwarren.M m) {
        if (lord != null) {
            for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
                if (lord.out(x, z) >= 1) continue;
                if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) v.set(x, y, z, (y - lord.feet) % 5 == 4 ? m.paperPale : m.cell);
                else if ((x - lord.cx) * (x - lord.cx) + (z - lord.cz) * (z - lord.cz) < 11) v.set(x, lord.feet - 1, z, m.jelly);
            }
        }
        for (Shaft s : shafts) {
            for (int[] c : COMBS) {
                int cx = s.cx + c[0], cz = s.cz + c[1], lo = s.top - c[2], fat = c[3], hi = lo + fat * 2 + 2;
                if (cx + fat < v.x0 - 1 || cx - fat > v.x0 + 16 || cz + fat < v.z0 - 1 || cz - fat > v.z0 + 16) continue;
                for (int x = cx - fat; x <= cx + fat; x++) for (int z = cz - fat; z <= cz + fat; z++) for (int y = lo; y <= hi; y++) {
                    double dx = (x - cx) / (fat + 0.4), dz = (z - cz) / (fat + 0.4), dy = (y - (lo + hi) / 2.0) / ((hi - lo) / 2.0 + 0.4);
                    if (dx * dx + dz * dz + dy * dy < 1) v.set(x, y, z, rnd(x * 17L + y, z, 831) < 0.34 ? m.cellLit : m.cell);
                }
                for (int y = hi + 1; y < s.dome(cx, cz); y++) v.set(cx, y, cz, m.paperPale);
                v.set(cx, lo - 1, cz, m.sac);
            }
        }
    }

    @Override
    public List<Edit> change(String moment) {
        Broodwarren.M m = mats();
        List<Edit> out = new ArrayList<>();
        switch (moment) {
            case "ambush_ambush" -> {
                if (ambush == null) return out;
                for (int x = ambush.x0; x <= ambush.x1; x++) for (int z = ambush.z0; z <= ambush.z1; z++) {
                    if (ambush.feet2(x, z) == NONE || (x - ambush.cx) * (x - ambush.cx) + (z - ambush.cz) * (z - ambush.cz) > 81) continue;
                    int top = ambush.roof(x, z) - 1, len = Math.min(sacLen(ambush, x, z), top - ambush.feet - 1);
                    for (int k = 0; k < len; k++) out.add(new Edit(x, top - k, z, AIR));
                }
            }
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
        if (lord == null) return;
        for (int x = lord.x0; x <= lord.x1; x++) for (int z = lord.z0; z <= lord.z1; z++) {
            if (lord.out(x, z) >= 1 || !lord.pillar(x, z)) continue;
            for (int y = lord.feet; y < Math.min(lord.feet + high, lord.roof(x, z)); y++) if ((y - lord.feet) % 5 != 4) out.add(new Edit(x, y, z, s));
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xffe2b0, 0.8F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x7a2a12, 0.45F).light(lord.cx, lord.feet + 4, lord.cz, 0xffa040, 18, 0.8F);
        for (Shaft s : shafts) air.light(s.cx, s.top - 8, s.cz, 0xff6a2a, 14, 0.9F);
        if (ambush != null) air.light(ambush.cx, ambush.feet + 4, ambush.cz, 0xff5a20, 12, 0.7F);
        return airOf(air);
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): a nest: round cells, a larder you look down into, comb hung over the drops. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.OVAL, Cave.Form.PIT, Cave.Form.RING, Cave.Form.OVAL, Cave.Form.RIFT), List.of(Arena.Plan.RING),
                List.of(Arena.Plan.ROTUNDA, Arena.Plan.RING, Arena.Plan.TIERS), List.of(Chasm.Kind.PILLARS, Chasm.Kind.HANGERS, Chasm.Kind.LEDGES, Chasm.Kind.STEPS), false, 2.1, 4);
    }

    /** Its small pieces (SPEC §50.9 "Depth"): nothing in a nest is cut: its depth is its ground and its walls, and more sacs. */
    @Override
    protected Trim trim(Broodwarren.M m) {
        return new Trim(null, null, null, null,
                List.of(), namedAll("brood_sac_drip"), 0.7);
    }

    /** The colour of its light in the air (the same as its biome's). */
    @Override
    protected int airTint() {
        return 0xffa868;
    }
}
