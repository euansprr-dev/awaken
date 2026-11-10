package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Frozen Cells (SPEC §50.9): the Frost Monastery's skin on a run rolled from the seed. The cells and prayer halls of a
 * monastery cut into a frozen mountain, long since iced over. Mostly built: pale cut stone in courses, ice grown down every
 * wall in sheets from the roof, packed snow in the corners, frost on worn flags. Cold and clear, not dim: blue-white light
 * through the ice at head height in every bay, butter lamps along the foot of the walls, lanterns and rods of ice from the roofs.
 */
public final class FrozenCells extends GeneratedCrawl<FrozenCells.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockState POOL = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private static final List<List<String>> HERDS = List.of(List.of("rime_acolyte*3", "hail_stray*2"), List.of("ice_hound*3", "frost_wisp*2"),
            List.of("hail_stray*3", "chant_master"), List.of("frost_wisp*4", "rime_acolyte"));

    /** The theme has no Realm family of its own: these are the old gate's blocks and the vanilla ones its palette used. */
    public static final class M extends Mats {
        final BlockState stone = b("frost_stone"), carved = b("frost_stone_carved"), tiles = b("frost_tiles"), stoneSlab = b("frost_stone_slab"), snow = b("frost_snow"),
                drift = b("frost_snow_drift"), ice = b("frost_ice"), iceLit = b("frost_ice_glowing"), lamp = b("frost_butter_lamp"), crystal = b("frost_crystal"), chips = b("frost_chips"),
                pale = Blocks.POLISHED_DIORITE.defaultBlockState(), quartz = Blocks.QUARTZ_BRICKS.defaultBlockState(), packed = Blocks.PACKED_ICE.defaultBlockState(),
                blue = Blocks.BLUE_ICE.defaultBlockState(), sea = Blocks.SEA_LANTERN.defaultBlockState(), rod = Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN),
                bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true).setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);

        M() {
            // "amber" only so the kit's own blocks (the cache) are there; nothing of Amber's is drawn
            super("amber", new Accents(b("frost_stone_carved"), b("frost_stone_carved"), b("frost_ice_glowing"), b("frost_ice"), b("frost_butter_lamp"), b("frost_chips"), b("frost_snow"),
                    Blocks.IRON_BARS.defaultBlockState(), b("frost_snow")));
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

    public FrozenCells(Ctx ctx) {
        super(ctx, 0x46524F5354L);
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

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("ice_hound*4", "frost_wisp*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("rime_acolyte*2", "ice_hound");
    }

    @Override
    protected BlockState spitOn(M m) {
        return m.iceLit;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.carved;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.packed;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.ice;
    }

    @Override
    protected double wild() {
        return 0.3;
    }

    /** Pale cut stone in courses, a band of quartz every sixth, a carved course between that still holds a little light. */
    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        int course = Math.floorMod(y, 6);
        if (course == 0) return m.quartz;
        if (course == 3 && rnd(x / 2, z / 2, 981) < 0.45) return m.carved;
        return rnd(x * 73L + y, z, 982) < 0.14 ? m.stone : m.pale;
    }

    @Override
    protected BlockState deep(M m) {
        return m.stone;
    }

    private static boolean cut(M m, BlockState s) {
        return s == m.pale || s == m.stone || s == m.quartz || s == m.carved;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        double n = rnd(x, z, 983);
        if (part.name.equals("hidden")) return n < 0.3 ? m.sea : m.blue;
        // worn flags with frost on them: mostly grey stone, a dark tile here and there, snow trodden into patches
        if (rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 984) < 0.2) return n < 0.75 ? m.snow : m.stone;
        return n < 0.86 ? m.stone : n < 0.93 ? m.pale : m.tiles;
    }

    @Override
    protected BlockState step(M m) {
        return m.stoneSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.bars;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        if (part instanceof Shaft || part instanceof Chasm) return;
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int feet = Part.airFrom(part.feet2(x, z)), up = y - feet, roof = part.roof(x, z), down = roof - 1 - y;
        if (wall && !narrow) {
            int ax = x + wx, az = z + wz, along = Math.floorMod(wx != 0 ? az : ax, 5);
            if (cut(m, v.get(ax, y, az))) {
                // ice grown down the wall in sheets from the roof: longer in some bays than others, never to the floor
                double grown = rnd(Math.floorDiv(wx != 0 ? az : ax, 4), wx != 0 ? ax : az, 985);
                int sheet = grown < 0.45 ? 0 : 1 + (int) ((grown - 0.45) / 0.55 * (room ? 4 : 2));
                // blue-white light through the ice at head height, one in each bay, its pool reaching the floor in front of it
                boolean lit = up == 2 && (along == 2 || room && along == 4);
                if (lit) {
                    v.set(ax, y, az, m.iceLit);
                    if (v.get(x, y, z) == null) v.set(x, y, z, POOL);
                } else if (down < sheet && up >= 1) v.set(ax, y, az, rnd(ax * 7L + y, az, 986) < 0.75 ? m.packed : m.blue);
            }
        }
        if (underRoof && !onFloor && !narrow && roof - feet >= 4 && v.get(x, y, z) == null) {
            // lanterns on chains from the roof, on a grid, so a room is lit to its middle and a way is never dark
            if (Math.floorMod(x, 5) == 2 && Math.floorMod(z, 5) == 2) {
                int hang = Math.max(feet + 3, roof - feet > 10 ? feet + 6 : roof - 2);
                for (int k = y; k > hang && v.get(x, k, z) == null; k--) v.set(x, k, z, m.chain);
                if (v.get(x, hang, z) == null || v.get(x, hang, z) == m.chain) v.set(x, hang, z, m.hung);
                if (hang - 2 >= feet && v.get(x, hang - 2, z) == null) v.set(x, hang - 2, z, POOL);
                return;
            }
            // icicles, from the rock of the roof only: rods of white ice that hold a cold light
            if (rnd(x, z, 987) < (room ? 0.09 : 0.05)) {
                v.set(x, y, z, m.rod);
                return;
            }
        }
        if (!onFloor || narrow || up > 0 || v.get(x, y, z) != null) return;
        // along the foot of a real wall: packed snow in the corners, drifts beside it, butter lamps, ice crystals
        int beside = 0;
        for (int[] d : SIDES) if (V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]))) beside++;
        if (beside == 0) return;
        double own = rnd(x, z, 988);
        if (rnd(Math.floorDiv(x, 4), Math.floorDiv(z, 4), 989) < (room ? 0.4 : 0.25) && own < 0.3) {
            v.set(x, y, z, m.lamp);
            if (v.get(x, y + 2, z) == null) v.set(x, y + 2, z, POOL);
        } else if (beside >= 2 && own < 0.8) v.set(x, y, z, m.snow);
        else if (own < 0.5) v.set(x, y, z, m.drift);
        else if (own < 0.64) v.set(x, y, z, m.crystal);
        else if (own < 0.74) v.set(x, y, z, m.chips);
    }

    @Override
    protected void skinSpecial(V v, M m) {
        // every round hall (the Lord's, a Warden's): pillars of blue ice banded with stone, a light in each band at head height
        for (Arena hall : halls) {
            boolean great = hall == lord;
            for (int x = Math.max(hall.x0, v.x0 - 1); x <= Math.min(hall.x1, v.x0 + 16); x++) for (int z = Math.max(hall.z0, v.z0 - 1); z <= Math.min(hall.z1, v.z0 + 16); z++) {
                if (hall.out(x, z) >= 1) continue;
                double d = Math.hypot(x - hall.cx, z - hall.cz);
                if (hall.pillar(x, z)) for (int y = hall.feet; y < hall.roof(x, z); y++) {
                    int up = y - hall.feet;
                    v.set(x, y, z, up == 0 ? m.quartz : up == 2 || up == 9 ? m.iceLit : up % 4 == 3 ? m.pale : m.blue);
                }
                // a ring of lamps in the Lord's floor, and one light under the middle of every hall
                else if (d < 2.2) v.set(x, hall.feet - 1, z, m.sea);
                else if (great && Math.abs(d - 9.4) < 0.55) v.set(x, hall.feet - 1, z, Math.floorMod((int) Math.round(Math.atan2(z - hall.cz, x - hall.cx) * 9.4), 4) == 0 ? m.sea : m.quartz);
                else if (great && Math.abs(d - 17.4) < 0.55) v.set(x, hall.feet - 1, z, m.quartz);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xd8ecff, 0.8F);
        if (lord != null) air.mist(lord.feet + 1, 4, 0xa8c8e8, 0.4F).light(lord.cx, lord.feet + 4, lord.cz, 0xbfe4ff, 20, 0.7F);
        return air.build();
    }
}
