package dev.awaken.gates.realms.land;

import dev.awaken.gates.block.ThemeBlocks;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Stopped Works (SPEC §50.9): the Clockwork Foundry's skin on a run rolled from the seed. A foundry whose machines
 * stopped mid-stroke. It was built, so nothing wanders: riveted plate and dark brick in courses with a band of brass every
 * fourth, a pipe run round every wall just over your head, grating down the middle of the ways. Its light is its own:
 * furnace mouths low in the walls of the rooms, gauge-lights above the pipes and in the roofs, a lamp set in the middle
 * of every room's floor, each with an unseen light a cell out so a room is bright to its middle. The Lord's hall stands on
 * pistons stopped at the top of their stroke.
 */
public final class StoppedWorks extends GeneratedCrawl<StoppedWorks.M> {
    /** The theme has no Realm family of its own: the Clockwork Foundry's blocks and the copper sheet the old gate was plated with. */
    public static final class M extends Mats {
        final BlockState plate = b("foundry_plate"), iron = b("foundry_iron"), brick = b("foundry_brick"), brass = b("foundry_brass"), gear = b("foundry_gear"), cog = b("gate_gear"),
                pipe = b("foundry_pipe"), pipeX = b("foundry_pipe").setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), pipeZ = b("foundry_pipe").setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z),
                furnace = b("foundry_furnace"), gauge = b("foundry_lamp"), grate = b("foundry_grate"), grille = b("foundry_grille"), bulb = b("foundry_bulb"), shavings = b("foundry_shavings"),
                valve = b("foundry_valve"), valveLit = b("foundry_valve").setValue(ThemeBlocks.Touchstone.LIT, true), ironSlab = b("foundry_iron_slab"),
                sheet = vanilla("waxed_cut_copper"), sheetOld = vanilla("waxed_exposed_cut_copper"),
                bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true).setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true),
                pool = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);

        /** The game's copper comes in families by weathering now, with no field a block; it is looked up by its id, as the theme's are. */
        private static BlockState vanilla(String id) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id)).defaultBlockState();
        }

        M() {
            // "amber" only so the kit's own blocks (the cache) exist; nothing of Amber's is drawn here
            super("amber", new Accents(b("foundry_gear"), b("foundry_brass"), b("foundry_lamp"), Blocks.IRON_CHAIN.defaultBlockState(), b("foundry_bulb"), b("foundry_shavings"), b("foundry_valve"),
                    Blocks.IRON_BARS.defaultBlockState(), b("foundry_grille")));
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (StoppedWorks.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final List<List<String>> HERDS = List.of(List.of("cog_hound*3", "rivet_gunner*2"), List.of("spark_drone*4", "cog_hound"),
            List.of("rivet_gunner*3", "foundry_marshal"), List.of("cog_hound*4", "spark_drone*2"));

    private Arena lord;
    private final List<Cave> rooms = new ArrayList<>();

    public StoppedWorks(Ctx ctx) {
        super(ctx, 0x53544F50L);
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
        return List.of("spark_drone*5", "cog_hound*2");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("rivet_gunner*2", "cog_hound");
    }

    /** A valve in the wall; it glows before it vents. */
    @Override
    protected BlockState spitOn(M m) {
        return m.valveLit;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.valve;
    }

    /** Piston heads. */
    @Override
    protected BlockState tooth(M m) {
        return m.brass;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.grille;
    }

    @Override
    protected double wild() {
        return 0;
    }

    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        // courses: a band of brass every fourth, with a cog let into it now and then; between them plate in runs of three,
        // panels of copper sheet, and a run of dark brick where the plate was never hung
        if (Math.floorMod(y, 4) == 0) return rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 991) < 0.12 ? m.gear : m.brass;
        int off = (int) (rnd(y, 0, 992) * 3);
        double n = rnd(Math.floorDiv(x + off, 3) * 31 + Math.floorDiv(z + off, 3), y, 993);
        return n < 0.5 ? m.plate : n < 0.68 ? m.iron : n < 0.84 ? m.sheet : n < 0.95 ? m.sheetOld : m.brick;
    }

    @Override
    protected BlockState deep(M m) {
        return m.plate;
    }

    /** Whether a cell lies down the middle of its part: two cells of floor on every side of it. */
    private boolean middle(Part part, int x, int z) {
        return part.feet2(x + 2, z) != NONE && part.feet2(x - 2, z) != NONE && part.feet2(x, z + 2) != NONE && part.feet2(x, z - 2) != NONE;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        boolean room = part instanceof Cave;
        // grating down the middle of the ways; the rooms are laid in plate and old sheet, a strip of brass every fourth cell
        if (!room && !(part instanceof Shaft) && !(part instanceof Chasm) && middle(part, x, z)) return m.grate;
        double n = rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 994);
        if (part.name.equals("hidden")) return n < 0.5 ? m.brass : m.gear;
        if (room && Math.floorMod(x, 4) == 0) return m.brass;
        return n < 0.3 ? m.sheetOld : n < 0.84 ? m.plate : m.iron;
    }

    @Override
    protected BlockState step(M m) {
        return m.ironSlab;
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
            // the wall beside you is the works' own: what is set into it is set into its plate, never hung on anything
            int ax = x + wx, az = z + wz, along = wx != 0 ? az : ax;
            if (room && up == 0 && Math.floorMod(along, 6) <= 1) {
                // a furnace mouth low in a room's wall, two cells wide, and its light a cell out
                v.set(ax, y, az, m.furnace);
                if (free && Math.floorMod(along, 6) == 0) v.set(x, y, z, m.pool);
            } else if (up == 2 && !narrow) {
                // the pipe run, just over your head, round every wall
                v.set(ax, y, az, wx != 0 ? m.pipeZ : m.pipeX);
            } else if (up == (narrow ? 2 : 3) && Math.floorMod(along, 5) == 0) {
                // a gauge-light every five cells along every wall, and its pool a cell out
                v.set(ax, y, az, m.gauge);
                if (free) v.set(x, y, z, m.pool);
            } else if (up == 1 && !narrow && rnd(ax * 7L + y, az, 995) < 0.06) v.set(ax, y, az, rnd(ax, az, 996) < 0.5 ? m.gear : m.cog);
        }
        if (underRoof && !onFloor) {
            // a roof is plate on ribs of brass every third cell, whatever course it was cut at (a band of brass is a line round a wall,
            // not a whole ceiling), with lights let into it on a grid, as a works is lit
            boolean lamp = !narrow && up >= 3 && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2;
            if (lamp) v.set(x, y + 1, z, m.gauge);
            else v.set(x, y + 1, z, Math.floorMod(z, 3) == 0 ? m.brass : rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 999) < 0.7 ? m.plate : m.iron);
            if (lamp && free) v.set(x, y, z, m.pool);
        }
        if (narrow) return;
        if (!onFloor || open || up > 0 || !free) return;
        // a real wall stands at least two high beside you; the next step of a stair or a ledge is not one
        boolean foot = false;
        for (int[] d : SIDES) foot |= V.holds(v.get(x + d[0], y, z + d[1])) && V.holds(v.get(x + d[0], y + 1, z + d[1]));
        double own = rnd(x, z, 997);
        if (foot && room && rnd(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 998) < 0.4 && own < 0.2) {
            // a work lamp left standing at the foot of the wall
            v.set(x, y, z, m.bulb);
            if (v.get(x, y + 2, z) == null) v.set(x, y + 2, z, m.pool);
        } else if (own > 0.975) v.set(x, y, z, m.shavings);
    }

    @Override
    protected void skinSpecial(V v, M m) {
        for (Cave r : rooms) {
            // a lamp set in the middle of every room's floor in a square of brass, and its light over it: bright to the middle
            if (r.cx + 1 < v.x0 - 1 || r.cx - 1 > v.x0 + 16 || r.cz + 1 < v.z0 - 1 || r.cz - 1 > v.z0 + 16) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (V.holds(v.get(r.cx + dx, r.feet - 1, r.cz + dz))) v.set(r.cx + dx, r.feet - 1, r.cz + dz, dx == 0 && dz == 0 ? m.gauge : m.brass);
            }
            if (v.get(r.cx, r.feet + 2, r.cz) == null) v.set(r.cx, r.feet + 2, r.cz, m.pool);
        }
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the hall stands on great pistons stopped at the top of their stroke: an iron rod, a brass head under the roof, a brass
            // collar every sixth; and a ring of brass in the floor with a lamp let into it every fourth cell
            if (lord.pillar(x, z)) {
                for (int y = lord.feet, roof = lord.roof(x, z); y < roof; y++) v.set(x, y, z, y >= roof - 2 ? m.brass : (y - lord.feet) % 6 == 5 ? m.brass : (y - lord.feet) % 6 == 4 ? m.gear : m.iron);
            } else if (Math.abs(d - 9.4) < 0.6 || Math.abs(d - 17.0) < 0.6) {
                boolean lamp = Math.floorMod(x + z * 3, 4) == 0;
                v.set(x, lord.feet - 1, z, lamp ? m.gauge : m.brass);
                if (lamp && v.get(x, lord.feet + 2, z) == null) v.set(x, lord.feet + 2, z, m.pool);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xffd8a0, 0.8F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x6a4a2a, 0.4F).light(lord.cx, lord.feet + 4, lord.cz, 0xffb050, 18, 0.9F);
        return air.build();
    }
}
