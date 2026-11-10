package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rime Hollows (SPEC §50.9): the Frost Monastery's second kind, and nothing like its cells. The natural ice caves under
 * the mountain, where the monks never built. Every wall is one thing that changes as it rises: dark mountain stone at the
 * foot, packed snow over it, clear blue ice above, in bands that follow the lie of the rock. Whole sheets of ice in the walls,
 * floors and roofs are lit from inside, and that ice is the light. Icicles hang in clusters, longest in the middle of a room.
 * Once to a room the monastery reaches in: a lamp on a post at the foot of a wall.
 */
public final class RimeHollow extends GeneratedCrawl<RimeHollow.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockState POOL = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    // the beasts and the cold things; the monks' own kind is in The Frozen Cells
    private static final List<List<String>> HERDS = List.of(List.of("ice_hound*4", "frost_wisp"), List.of("frost_wisp*4", "hail_stray*2"),
            List.of("ice_hound*2", "hail_stray*3"), List.of("frost_wisp*3", "ice_hound*2", "rime_acolyte"));

    /** The Frost Monastery's blocks and the vanilla ones its palette used; the same family as The Frozen Cells, used differently. */
    public static final class M extends Mats {
        final BlockState stone = b("frost_stone"), stoneSlab = b("frost_stone_slab"), snow = b("frost_snow"), ice = b("frost_ice"), iceLit = b("frost_ice_glowing"),
                lamp = b("frost_butter_lamp"), crystal = b("frost_crystal"),
                pale = Blocks.POLISHED_DIORITE.defaultBlockState(), packed = Blocks.PACKED_ICE.defaultBlockState(), blue = Blocks.BLUE_ICE.defaultBlockState(),
                sea = Blocks.SEA_LANTERN.defaultBlockState(),
                // an icicle's point: a white rod (pointed dripstone was tried first and read as brown rock under the ice)
                tip = Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN);

        M() {
            // "amber" only so the kit's own blocks (the cache) are there; nothing of Amber's is drawn
            super("amber", new Accents(b("frost_ice"), b("frost_ice"), b("frost_ice_glowing"), b("frost_ice"), b("frost_butter_lamp"), b("frost_chips"), b("frost_snow"),
                    Blocks.IRON_BARS.defaultBlockState(), b("frost_snow")));
        }
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    private final SimplexNoise lie, sheet, bed, cluster;
    private Arena lord;
    private final List<Arena> halls = new ArrayList<>();
    private final List<Cave> rooms = new ArrayList<>();

    public RimeHollow(Ctx ctx) {
        super(ctx, 0x52494D45L);
        lie = N.noise(seed + 1001);
        sheet = N.noise(seed + 1002);
        bed = N.noise(seed + 1003);
        cluster = N.noise(seed + 1004);
        generate();
        for (Part p : parts) {
            if (p.name.equals("lord")) lord = (Arena) p;
            if (p instanceof Arena a) halls.add(a);
            if (p instanceof Cave c && !p.name.equals("root") && !p.name.equals("side") && !p.name.equals("hidden")) rooms.add(c);
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
        return List.of("ice_hound*5", "frost_wisp*3");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("ice_hound*2", "frost_wisp*2");
    }

    @Override
    protected BlockState spitOn(M m) {
        return m.iceLit;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.ice;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.blue;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.ice;
    }

    @Override
    protected double wild() {
        return 1;
    }

    /** What the mountain is where nothing has dressed it (roofs, the backs of walls): ice, with great lenses of packed snow in it. */
    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        return lie.get(x / 23.0 + y / 31.0, z / 23.0 - y / 29.0) > 0.35 ? m.snow : m.packed;
    }

    @Override
    protected BlockState deep(M m) {
        return m.stone;
    }

    private static boolean mountain(M m, BlockState s) {
        return s == m.packed || s == m.snow;
    }

    /** Whether lit ice lies here, in a wall, a floor or a roof: whole sheets a few cells across, by one field of noise. */
    private boolean lit(int x, int y, int z) {
        return sheet.get(x / 6.0 + y / 9.0, z / 6.0 - y / 7.0) > 0.3;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        // a ledge over a drop is bare stone, so thin ice can be told from it
        if (part instanceof Shaft || part instanceof Chasm) return m.stone;
        if (part.name.equals("hidden")) return lit(x, y, z) ? m.iceLit : m.blue;
        // snow, with beds of bare dark stone and a few sheets of ice; in a room the ice is lit
        if (bed.get(x / 11.0, z / 11.0) > 0.4) return m.stone;
        if (lit(x, y - 1, z)) return part instanceof Cave ? m.iceLit : m.blue;
        return m.snow;
    }

    @Override
    protected BlockState step(M m) {
        return m.stoneSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.packed;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean climb = part instanceof Shaft || part instanceof Chasm, room = part instanceof Cave, narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int feet = Part.airFrom(part.feet2(x, z)), up = y - feet, roof = part.roof(x, z);
        boolean free = v.get(x, y, z) == null;
        if (wx != 0 || wz != 0) {
            // a wall is one thing that changes as it rises: stone at the foot, packed snow over it, blue ice above, the bands following the rock
            int ax = x + wx, az = z + wz;
            if (mountain(m, v.get(ax, y, az))) {
                double wave = lie.get(ax / 9.0 + 40, az / 9.0), wave2 = lie.get(ax / 13.0, az / 13.0 + 70);
                int stoneTop = (int) Math.round(0.6 + wave * 1.6), snowTop = stoneTop + 1 + (int) Math.round(1.2 + wave2 * 1.4);
                boolean glow = lit(ax, y, az) && (climb || up >= 1);
                v.set(ax, y, az, glow ? m.iceLit : climb ? m.blue : up <= stoneTop ? m.stone : up <= snowTop ? m.snow : m.blue);
                // the ice itself is the light: a light a cell out in front of each sheet, every third cell of it at head height
                if (glow && !climb && free && up == 2 && Math.floorMod(wx != 0 ? az : ax, 3) == 1) {
                    v.set(x, y, z, POOL);
                    free = false;
                }
            }
        }
        if (climb) return;
        if (underRoof && !onFloor && free) {
            if (lit(x, y + 1, z) && mountain(m, v.get(x, y + 1, z))) {
                // a sheet of lit ice in the roof, and its light let down into the room
                v.set(x, y + 1, z, m.iceLit);
                if (Math.floorMod(x + z, 2) == 0 && v.get(x, Math.max(feet + 2, y - 3), z) == null) v.set(x, Math.max(feet + 2, y - 3), z, POOL);
            } else if (!narrow && cluster.get(x / 3.5, z / 3.5) > 0.38) {
                // icicles in clusters, from rock only: an ice stalk and a point, longest in the middle of a room, never within three clear cells of the floor
                double mid = room ? 1 - Math.min(1, Math.hypot(x - ((Cave) part).cx, z - ((Cave) part).cz) / Math.max(1, Math.min(((Cave) part).rx, ((Cave) part).rz))) : 0;
                int len = Math.min(1 + (int) Math.round(mid * 4 + cluster.get(x / 1.3 + 9, z / 1.3) * 0.8), y - (feet + 3) + 1);
                for (int k = 0; k < len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, k == len - 1 ? m.tip : m.ice);
            }
            return;
        }
        // a sheet of lit ice underfoot throws its light up into the room as well
        if (onFloor && up == 0 && free && Math.floorMod(x, 3) == 1 && Math.floorMod(z, 3) == 1 && v.get(x, y - 1, z) == m.iceLit && v.get(x, y + 2, z) == null && roof - feet >= 4) v.set(x, y + 2, z, POOL);
        // on a floor, only at the foot of a real wall, and nothing with a body: ice crystals, in whole patches
        if (!onFloor || narrow || up > 0 || !free) return;
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        if (foot && bed.get(x / 5.0 + 30, z / 5.0) > 0.35) v.set(x, y, z, m.crystal);
    }

    @Override
    protected void skinSpecial(V v, M m) {
        // once to a room the monastery reaches in: a butter lamp on a stone post at the foot of the wall, on the side a roll of the room's place picks
        for (Cave room : rooms) {
            if (room.x1 < v.x0 - 1 || room.x0 > v.x0 + 16 || room.z1 < v.z0 - 1 || room.z0 > v.z0 + 16) continue;
            double a = N.rnd(room.cx, room.cz, 1005) * Math.PI * 2;
            int px = room.cx, pz = room.cz;
            for (double d = 1; d < 40; d += 0.5) {
                int nx = (int) Math.round(room.cx + Math.cos(a) * d), nz = (int) Math.round(room.cz + Math.sin(a) * d);
                if (room.feet2(nx, nz) == NONE) break;
                px = nx;
                pz = nz;
            }
            if (px == room.cx && pz == room.cz || room.roof(px, pz) - room.feet < 4) continue;
            v.set(px, room.feet, pz, m.stone);
            v.set(px, room.feet + 1, pz, m.lamp);
            v.set(px, room.feet + 3, pz, POOL);
        }
        // every round hall (the Lord's, a Warden's): pillars of clear ice with a core of light; a ring of pale stone in the Lord's floor
        for (Arena hall : halls) {
            boolean great = hall == lord;
            for (int x = Math.max(hall.x0, v.x0 - 1); x <= Math.min(hall.x1, v.x0 + 16); x++) for (int z = Math.max(hall.z0, v.z0 - 1); z <= Math.min(hall.z1, v.z0 + 16); z++) {
                if (hall.out(x, z) >= 1) continue;
                double d = Math.hypot(x - hall.cx, z - hall.cz);
                if (hall.pillar(x, z)) {
                    int beside = 0;
                    for (int[] s : SIDES) if (hall.pillar(x + s[0], z + s[1])) beside++;
                    for (int y = hall.feet; y < hall.roof(x, z); y++) v.set(x, y, z, beside == 4 ? m.sea : m.ice);
                } else if (great && Math.abs(d - 9.4) < 0.55) v.set(x, hall.feet - 1, z, m.pale);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xcfe8ff, 0.8F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0xb8d8f0, 0.5F).light(lord.cx, lord.feet + 5, lord.cz, 0xa8e0ff, 22, 0.8F);
        return air.build();
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): ice caves: round hollows, crevasses, a pit of blue ice, and thin ice over the drops. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.OVAL, Cave.Form.RIFT, Cave.Form.PIT, Cave.Form.RING), List.of(Arena.Plan.RING, Arena.Plan.TIERS),
                List.of(Arena.Plan.ROTUNDA, Arena.Plan.TIERS), List.of(Chasm.Kind.PILLARS, Chasm.Kind.FALLING, Chasm.Kind.FORD, Chasm.Kind.STEPS, Chasm.Kind.LEDGES), false, 2.4, 5);
    }
}
