package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Silvered Halls (SPEC §50.9): the Mirror Palace's skin on a run rolled from the seed. The back halls of a palace where
 * every wall was once a mirror. It was built, and it is the most formal of the kinds: nothing wanders and nothing is
 * scattered. Dressed marble over a dark skirting, a gilt cornice under every roof; round the rooms, bays of tall mirror
 * three high between dark piers, a lamp let into every pier at head height; chequered floors, a runner down the middle
 * of the ways. Lamps in the piers, on a grid in the roofs and in the middle of every room's floor, each with an unseen
 * light a cell out, so a room is bright to its middle.
 */
public final class SilveredHalls extends GeneratedCrawl<SilveredHalls.M> {
    /** The theme has no Realm family of its own: the Mirror Palace's blocks, and the dark stone the old gate panelled with. */
    public static final class M extends Mats {
        final BlockState marble = b("palace_marble"), veined = b("palace_marble_veined"), checker = b("palace_checker"), gilt = b("palace_gilt"), mirror = b("palace_mirror"),
                cracked = b("palace_mirror_cracked"), pane = b("palace_glass_floor"), lamp = b("palace_white"), cloth = b("palace_curtain"), candelabra = b("palace_candelabra"),
                glass = b("mirror_stand"), silver = b("gate_mirror"), marbleSlab = b("palace_marble_slab"),
                panel = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true).setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true),
                pool = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);

        M() {
            // "amber" only so the kit's own blocks (the cache) exist; nothing of Amber's is drawn here
            super("amber", new Accents(b("palace_mirror"), b("palace_gilt"), b("palace_white"), Blocks.IRON_CHAIN.defaultBlockState(), b("palace_candelabra"), b("palace_shards"), b("mirror_stand"),
                    Blocks.IRON_BARS.defaultBlockState(), b("palace_curtain")));
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (SilveredHalls.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** A bay: a pier and three cells of mirror. */
    private static final int BAY = 4;
    private static final List<List<String>> HERDS = List.of(List.of("mirror_shade*3", "glint_mite*3"), List.of("silver_knight*2", "mirror_shade*2"),
            List.of("glint_mite*5", "glass_duelist"), List.of("silver_knight*3", "glint_mite*2"));

    private Arena lord;
    private final List<Cave> rooms = new ArrayList<>();

    public SilveredHalls(Ctx ctx) {
        super(ctx, 0x53494C56L);
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
        return List.of("mirror_shade*4", "glint_mite*3");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("silver_knight*2", "mirror_shade");
    }

    /** A small mirror in the wall; it flashes white before it fires. */
    @Override
    protected BlockState spitOn(M m) {
        return m.lamp;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.glass;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.panel;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.pane;
    }

    @Override
    protected double wild() {
        return 0;
    }

    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        // dressed marble in courses, one stone three cells long, a veined one among them now and then
        int off = (int) (rnd(y, 0, 1001) * 3);
        return rnd(Math.floorDiv(x + off, 3) * 31 + Math.floorDiv(z + off, 3), y, 1002) < 0.14 ? m.veined : m.marble;
    }

    @Override
    protected BlockState deep(M m) {
        return m.marble;
    }

    /** Whether a cell lies down the middle of its part: two cells of floor on every side of it. */
    private boolean middle(Part part, int x, int z) {
        return part.feet2(x + 2, z) != NONE && part.feet2(x - 2, z) != NONE && part.feet2(x, z + 2) != NONE && part.feet2(x, z - 2) != NONE;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        boolean room = part instanceof Cave;
        if (part.name.equals("hidden")) return Math.floorMod(x + z, 2) == 0 ? m.gilt : m.marble;
        // rooms are chequered in squares of two, marble and silvered glass (the Lord's dais in black and white); a way is marble with a
        // runner of cloth down its middle
        if (part == lordPart() && Math.hypot(x - lord.cx, z - lord.cz) < 8.8) return m.checker;
        if (room) return Math.floorMod(Math.floorDiv(x, 2) + Math.floorDiv(z, 2), 2) == 0 ? m.silver : m.marble;
        if (!(part instanceof Shaft) && !(part instanceof Chasm) && middle(part, x, z)) return m.cloth;
        return m.marble;
    }

    /** The Lord's hall, once the run is laid out (floors are asked for while it still is being). */
    private Part lordPart() {
        return lord;
    }

    @Override
    protected BlockState step(M m) {
        return m.marbleSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.bars;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, open = part instanceof Shaft || part instanceof Chasm;
        int up = y - Part.airFrom(part.feet2(x, z));
        boolean free = v.get(x, y, z) == null;
        if (wall) {
            // the wall beside you is the palace's own: everything here is set into it, nothing is hung on anything
            int ax = x + wx, az = z + wz, along = wx != 0 ? az : ax, bay = Math.floorMod(along, BAY);
            // one cornice of gilt, a line round the wall over the mirrors (a roof is vaulted in steps: a cornice that followed it would gild it all)
            if (!narrow && up == (room ? 4 : 3)) v.set(ax, y, az, m.gilt);
            else if (up == 0) v.set(ax, y, az, m.panel); // the skirting
            else if (room && up <= 3) {
                // bays of mirror three high between dark piers, and a lamp let into each pier at head height with its pool a cell out
                if (bay != 0) v.set(ax, y, az, rnd(ax * 7L + y, az, 1003) < 0.05 ? m.cracked : m.mirror);
                else if (up == 1) {
                    v.set(ax, y, az, m.lamp);
                    if (free) v.set(x, y, z, m.pool);
                } else v.set(ax, y, az, m.panel);
            } else if (!room && up == 2 && Math.floorMod(along, 5) == 0) {
                // along a way, a lamp in the wall every five cells
                v.set(ax, y, az, m.lamp);
                if (free) v.set(x, y, z, m.pool);
            }
        }
        if (narrow) return;
        if (underRoof && !onFloor && up >= 3 && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2) {
            // lamps let into the roof on a grid, each in its place
            v.set(x, y + 1, z, m.lamp);
            if (free) v.set(x, y, z, m.pool);
        }
        if (!onFloor || open || up > 0 || !free || !room) return;
        // a real wall stands at least two high beside you; the next step of a stair or a ledge is not one
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        // a candelabra at the foot of the wall, one to a stretch of it and never two together
        if (foot && Math.floorMod(x, 7) == 3 && Math.floorMod(z, 7) == 3) {
            v.set(x, y, z, m.candelabra);
            if (v.get(x, y + 2, z) == null) v.set(x, y + 2, z, m.pool);
        }
    }

    @Override
    protected void skinSpecial(V v, M m) {
        for (Cave r : rooms) {
            // a lamp in the middle of every room's floor, framed in gilt, and its light over it: bright to the middle
            if (r.cx + 1 < v.x0 - 1 || r.cx - 1 > v.x0 + 16 || r.cz + 1 < v.z0 - 1 || r.cz - 1 > v.z0 + 16) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (V.holds(v.get(r.cx + dx, r.feet - 1, r.cz + dz))) v.set(r.cx + dx, r.feet - 1, r.cz + dz, dx == 0 && dz == 0 ? m.lamp : m.gilt);
            }
            if (v.get(r.cx, r.feet + 2, r.cz) == null) v.set(r.cx, r.feet + 2, r.cz, m.pool);
        }
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the hall stands on mirrored pillars with a band of gilt every sixth course and at head and foot; two rings of pale
            // stone in the floor, a lamp let into each every fourth cell
            if (lord.pillar(x, z)) {
                for (int y = lord.feet, roof = lord.roof(x, z); y < roof; y++) v.set(x, y, z, y == lord.feet || y >= roof - 1 || (y - lord.feet) % 6 == 5 ? m.gilt : m.mirror);
            } else if (Math.abs(d - 9.4) < 0.6 || Math.abs(d - 17.0) < 0.6) {
                boolean lamp = Math.floorMod(x + z * 3, 4) == 0;
                v.set(x, lord.feet - 1, z, lamp ? m.lamp : m.marble);
                if (lamp && v.get(x, lord.feet + 2, z) == null) v.set(x, lord.feet + 2, z, m.pool);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xe8ecff, 0.8F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0xc8ccdc, 0.35F).light(lord.cx, lord.feet + 4, lord.cz, 0xfff0d0, 18, 0.8F);
        return air.build();
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): a palace: galleries, crossings and colonnades, wide high ways, a glass floor nobody should trust. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.HALL, Cave.Form.CROSS, Cave.Form.PIERS, Cave.Form.HALL), List.of(Arena.Plan.NAVE, Arena.Plan.SQUARE),
                List.of(Arena.Plan.NAVE, Arena.Plan.CROSS, Arena.Plan.SQUARE), List.of(Chasm.Kind.BRIDGE, Chasm.Kind.FALLING, Chasm.Kind.HANGERS, Chasm.Kind.BEAMS), true, 3.0, 6);
    }
}
