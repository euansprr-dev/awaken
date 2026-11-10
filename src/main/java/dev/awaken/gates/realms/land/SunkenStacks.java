package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Sunken Stacks (SPEC §50.9): the Drowned Archive's skin on a run rolled from the seed. A library the sea got into a long
 * time ago. Built, not dug: dressed blue-grey stone, and every wall a row of tall bays of drowned books between stone piers,
 * salt and weed along the foot of them. Wet tile underfoot. It is lit: a glowing shelf at head height in each bay with its
 * pool reaching the floor, lanterns on chains from every roof so a room is lit to its middle, standing lamps at the walls.
 */
public final class SunkenStacks extends GeneratedCrawl<SunkenStacks.M> {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockState POOL = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private static final List<List<String>> HERDS = List.of(List.of("ink_drowned*3", "salt_stray*2"), List.of("page_wraith*4", "ink_drowned"),
            List.of("salt_stray*3", "drowned_scribe"), List.of("archive_eye*2", "ink_drowned*3"));

    /** The theme has no Realm family of its own: these are the old gate's blocks and the vanilla ones its palette used. */
    public static final class M extends Mats {
        final BlockState stone = b("archive_stone"), carved = b("archive_stone_carved"), shelf = b("archive_shelf"), shelfLit = b("archive_shelf_lit"), tiles = b("archive_tiles"),
                tilesSlab = b("archive_tiles_slab"), boards = b("archive_planks"), lamp = b("archive_lamp"), kelp = b("archive_kelp"), scraps = b("archive_scraps"),
                brick = Blocks.PRISMARINE_BRICKS.defaultBlockState(), rough = Blocks.PRISMARINE.defaultBlockState(), dark = Blocks.DARK_PRISMARINE.defaultBlockState(),
                sea = Blocks.SEA_LANTERN.defaultBlockState(),
                bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true).setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);

        M() {
            // "amber" only so the kit's own blocks (the cache) are there; nothing of Amber's is drawn
            super("amber", new Accents(b("archive_shelf"), b("archive_shelf"), b("archive_shelf_lit"), b("archive_kelp"), b("archive_lamp"), b("archive_scraps"), b("archive_shelf"),
                    Blocks.IRON_BARS.defaultBlockState(), b("archive_planks")));
        }
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    private Arena lord;
    private final java.util.List<Arena> halls = new java.util.ArrayList<>();

    public SunkenStacks(Ctx ctx) {
        super(ctx, 0x535441434B53L);
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
        return List.of("page_wraith*5", "ink_drowned*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("salt_stray*2", "ink_drowned");
    }

    @Override
    protected BlockState spitOn(M m) {
        return m.shelfLit;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.shelf;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.brick;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.boards;
    }

    @Override
    protected double wild() {
        return 0.2;
    }

    /** Dressed stone in courses, with a carved course every sixth that still holds a little light. */
    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        if (Math.floorMod(y, 6) == 0) return rnd(x / 2, z / 2, 971) < 0.7 ? m.carved : m.brick;
        return rnd(x * 73L + y, z, 972) < 0.12 ? m.brick : m.stone;
    }

    @Override
    protected BlockState deep(M m) {
        return m.stone;
    }

    private static boolean dressed(M m, BlockState s) {
        return s == m.stone || s == m.carved || s == m.brick;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        double n = rnd(x, z, 973);
        if (part.name.equals("hidden")) return n < 0.3 ? m.sea : m.tiles;
        // wet tile, darker where the water lay longest
        return n < 0.74 ? m.tiles : n < 0.9 ? m.brick : m.dark;
    }

    @Override
    protected BlockState step(M m) {
        return m.tilesSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.bars;
    }

    private static boolean narrow(Part part) {
        return part.name.equals("slip") || part.name.startsWith("side_");
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        if (part instanceof Shaft) return;
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, narrow = narrow(part);
        int feet = Part.airFrom(part.feet2(x, z)), up = y - feet, roof = part.roof(x, z);
        if (wall && !narrow) {
            // the stacks: bays of books four wide between stone piers, a plinth under them, stone again over them
            int ax = x + wx, az = z + wz, along = Math.floorMod(wx != 0 ? az : ax, 5), high = room ? 4 : 2;
            if (dressed(m, v.get(ax, y, az))) {
                if (along == 0) v.set(ax, y, az, m.brick);
                else if (up == 0) v.set(ax, y, az, rnd(ax * 7L + y, az, 974) < 0.6 ? m.rough : m.dark);
                else if (up >= 1 && up <= high) {
                    // one shelf in each bay still glows, at head height, and its light reaches the floor in front of it
                    boolean lit = up == 2 && (along == 2 || along == 3) || room && up == 4 && along == 1;
                    v.set(ax, y, az, lit ? m.shelfLit : m.shelf);
                    if (lit && v.get(x, y, z) == null) v.set(x, y, z, POOL);
                }
            }
        }
        // lanterns on chains from the roof, on a grid, so a room is lit to its middle and a way is never dark
        if (underRoof && !onFloor && !narrow && Math.floorMod(x, 5) == 2 && Math.floorMod(z, 5) == 2 && roof - feet >= 4 && v.get(x, y, z) == null) {
            int hang = Math.max(feet + 3, roof - feet > 10 ? feet + 6 : roof - 2);
            for (int k = y; k > hang && v.get(x, k, z) == null; k--) v.set(x, k, z, m.chain);
            if (v.get(x, hang, z) == null || v.get(x, hang, z) == m.chain) v.set(x, hang, z, m.hung);
            // the bench draws a dark place brighter than the game does: a second light under each lantern, so the floor is plainly lit
            if (hang - 2 >= feet && v.get(x, hang - 2, z) == null) v.set(x, hang - 2, z, POOL);
            return;
        }
        if (!onFloor || narrow || up > 0 || v.get(x, y, z) != null) return;
        // along the foot of a real wall: weed and scraps of salt-white paper, and in rooms a standing lamp now and then
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        if (!foot) return;
        double own = rnd(x, z, 975);
        if (room && rnd(Math.floorDiv(x, 4), Math.floorDiv(z, 4), 976) < 0.3 && own < 0.3) v.set(x, y, z, m.lamp);
        else if (own < 0.45) v.set(x, y, z, m.kelp);
        else if (own < 0.7) v.set(x, y, z, m.scraps);
    }

    @Override
    protected void skinSpecial(V v, M m) {
        // every round hall (the Lord's, a Warden's): its piers are brick with books between, a band of sea light at head height and another high up
        for (Arena hall : halls) {
            boolean great = hall == lord;
            for (int x = Math.max(hall.x0, v.x0 - 1); x <= Math.min(hall.x1, v.x0 + 16); x++) for (int z = Math.max(hall.z0, v.z0 - 1); z <= Math.min(hall.z1, v.z0 + 16); z++) {
                if (hall.out(x, z) >= 1) continue;
                double d = Math.hypot(x - hall.cx, z - hall.cz);
                if (hall.pillar(x, z)) for (int y = hall.feet; y < hall.roof(x, z); y++) {
                    int up = y - hall.feet;
                    v.set(x, y, z, up == 2 || up == 9 ? m.sea : up % 6 == 5 ? m.carved : up == 0 ? m.rough : up < 5 ? m.shelf : m.brick);
                }
                else if (d < 2.2) v.set(x, hall.feet - 1, z, m.sea);
                else if (great && (Math.abs(d - 9.4) < 0.55 || Math.abs(d - 17.4) < 0.55)) v.set(x, hall.feet - 1, z, m.dark);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, 150, 4, 3, 0x9fd8e8, 0.7F);
        if (lord != null) air.mist(lord.feet + 1, 4, 0x2a5a6a, 0.4F).light(lord.cx, lord.feet + 4, lord.cz, 0x7fe0e8, 20, 0.7F);
        return air.build();
    }
}
