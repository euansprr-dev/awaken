package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Sleepless Ward (SPEC §50.9): the Nightmare's skin on a run rolled from the seed. The corridors of a house or a ward
 * as a dream remembers them, slightly wrong: papered walls over a board wainscot whose rail slips out of line, doors and
 * windows in the walls that lead nowhere, sheets hung where a bed might have been, a pale ceiling, a floor whose pattern
 * drifts, and lamps on short cords, each in its own pool of sickly yellow.
 */
public final class SleeplessWard extends GeneratedCrawl<SleeplessWard.M> {
    private static final List<List<String>> HERDS = List.of(List.of("sleepwalker*4", "many_eyes"), List.of("night_terror*2", "sleepwalker*2"),
            List.of("many_eyes*3", "the_tall_one"), List.of("sleepwalker*3", "night_terror*2"));
    /** How far apart the lamps hang: one to every square of this many cells. */
    private static final int GRID = 5;
    private static final int NONE_BAY = 0, DOOR = 1, WINDOW = 2, SHEET = 3;

    /** The Nightmare has no Realm family of its own: these are its gate's blocks, by id. Amber's names only give the kit its cache. */
    public static final class M extends Mats {
        final BlockState paper = b("dream_wallpaper"), torn = b("dream_wallpaper_torn"), boards = b("dream_boards"), wood = b("dream_wood"), plush = b("dream_plush"),
                sheet = b("dream_sheet"), dark = b("dream_dark"), lamp = b("dream_lamp"), candle = b("dream_candle"), toy = b("dream_toy"), window = b("dream_window"),
                feathers = b("dream_feathers"), slab = b("dream_boards_slab");
        final BlockState cord = Blocks.IRON_CHAIN.defaultBlockState(), door = Blocks.DARK_OAK_DOOR.defaultBlockState();

        M() {
            super("amber", new Accents(null, null, null, null, null, null, null, null, null));
        }

        /** Whether a block is the ward's own wall as the rock drew it. */
        boolean wall(BlockState s) {
            return s == paper || s == torn;
        }
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    private final SimplexNoise patch;
    private Arena lord;

    public SleeplessWard(Ctx ctx) {
        super(ctx, 0x534C4550L);
        patch = N.noise(seed + 991);
        generate();
        for (Part p : parts) if (p.name.equals("lord") && p instanceof Arena a) lord = a;
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
        return List.of("night_terror*3", "sleepwalker*3");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("sleepwalker*2", "many_eyes");
    }

    /** A dark panel in the wall; the eyes in it open before it fires. */
    @Override
    protected BlockState spitOn(M m) {
        return m.dark;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.wood;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.wood;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.boards;
    }

    @Override
    protected double wild() {
        return 0.4;
    }

    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        return patch.get(x / 6.0 + y / 5.0, z / 6.0 - y / 7.0) > 0.35 ? m.torn : m.paper;
    }

    @Override
    protected BlockState deep(M m) {
        return m.paper;
    }

    /** Whether a floor cell lies against a wall: nothing is open beside it at any height a step could reach. */
    private boolean edge(int x, int y, int z) {
        return shut(x + 1, y, z) || shut(x - 1, y, z) || shut(x, y, z + 1) || shut(x, y, z - 1);
    }

    private boolean shut(int x, int y, int z) {
        return !open(x, y, z) && !open(x, y + 1, z) && !open(x, y + 2, z);
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        // a ledge over a drop is dark planking, so the old boards that give way can be told from it
        if (part instanceof Shaft || part instanceof Chasm) return m.wood;
        // the pattern drifts: its squares slide a cell out of step as you cross the room
        int sx = x + (int) Math.floor(2.5 * patch.get(z / 9.0 + 30, x / 31.0)), sz = z + (int) Math.floor(2.5 * patch.get(x / 9.0 + 60, z / 31.0));
        if (part instanceof Cave) return ((Math.floorDiv(sx, 2) + Math.floorDiv(sz, 2)) & 1) == 0 ? m.sheet : m.boards;
        // a way is boards with a runner down it
        return edge(x, y, z) || ((sx + sz) & 7) == 0 ? m.boards : m.plush;
    }

    @Override
    protected BlockState step(M m) {
        return m.slab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.wood;
    }

    /** What is set into a wall column at floor level: a door, a window or a hung sheet, each leading nowhere. */
    private int bay(int ax, int az, int wx) {
        int along = Math.floorMod(wx != 0 ? az : ax, 5);
        double h = rnd(Math.floorDiv(ax, 5), Math.floorDiv(az, 5), 993);
        if (h < 0.24) return along == 2 ? DOOR : NONE_BAY;
        if (h < 0.52) return along == 2 || along == 3 ? WINDOW : NONE_BAY;
        if (h < 0.72) return along == 1 || along == 2 ? SHEET : NONE_BAY;
        return NONE_BAY;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean wall = wx != 0 || wz != 0, room = part instanceof Cave, drop = part instanceof Shaft || part instanceof Chasm;
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        int up = y - Part.airFrom(part.feet2(x, z));
        if (underRoof) {
            // the ceiling over you is pale, where the paper hasn't come away
            BlockState over = v.get(x, y + 1, z);
            if (over == m.paper) v.set(x, y + 1, z, m.sheet);
        }
        if (wall) {
            int ax = x + wx, az = z + wz, kind = narrow || drop ? NONE_BAY : bay(ax, az, wx);
            BlockState at = v.get(ax, y, az);
            if (kind != NONE_BAY) {
                // a face in the wall, set from the floor up: all of its cells must be the wall itself
                if (up == 0 && onFloor && m.wall(at) && m.wall(v.get(ax, y + 1, az)) && m.wall(v.get(ax, y + 2, az))) {
                    if (kind == DOOR) {
                        Direction facing = wx > 0 ? Direction.EAST : wx < 0 ? Direction.WEST : wz > 0 ? Direction.SOUTH : Direction.NORTH;
                        v.set(ax, y, az, m.door.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                        v.set(ax, y + 1, az, m.door.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                        v.set(ax, y + 2, az, m.wood);
                    } else if (kind == WINDOW) {
                        v.set(ax, y, az, m.wood);
                        v.set(ax, y + 1, az, m.window);
                        v.set(ax, y + 2, az, m.window);
                        // whatever is behind it lights the floor in front of it
                        if (v.get(x, y + 1, z) == null) v.set(x, y + 1, z, m.light);
                    } else for (int k = 0; k <= 2; k++) v.set(ax, y + k, az, m.sheet);
                }
            } else if (m.wall(at)) {
                if (drop) {
                    // down a shaft the house goes on: windows one above another, lit, with nothing behind them
                    if (patch.get(ax / 4.0 + 80, az / 4.0 + y / 3.0) > 0.3) {
                        v.set(ax, y, az, m.window);
                        if (rnd(ax * 17L + y, az, 994) < 0.5) {
                            v.set(x, y, z, m.light);
                            return;
                        }
                    }
                } else {
                    // a board wainscot under a dark rail, and a picture rail in a room; both slip out of line in patches
                    int slip = (int) Math.round(1.4 * patch.get(ax / 7.0 + 110, az / 7.0)), rail = 2 + slip;
                    if (up == rail || room && up == rail + 4) v.set(ax, y, az, m.wood);
                    else if (up < rail) v.set(ax, y, az, m.boards);
                }
            }
        }
        if (narrow || drop) return;
        if (onFloor && up <= 0) {
            // candles someone left along the foot of a real wall (wall two cells high beside you), feathers from a pillow
            boolean foot = wall && v.get(x + wx, y + 1, z + wz) != null;
            double own = rnd(x, z, 995);
            if (foot && rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 996) < 0.4 && own < 0.5) v.set(x, y, z, m.candle);
            else if (own > 0.95) v.set(x, y, z, m.feathers);
        }
        if (underRoof && !onFloor && up >= 3) {
            // lamps on short cords, one to every few cells, a light under each: pools of yellow with the dark between them.
            // Three cells stay clear over the floor. (A roof that steps down has rock beside its highest cells as well as
            // over them, so being beside a wall is no reason not to hang here: most of a wandering roof is like that.)
            int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID);
            if (Math.floorMod(x, GRID) != (int) (rnd(gx, gz, 997) * GRID) || Math.floorMod(z, GRID) != (int) (rnd(gx, gz, 998) * GRID)) return;
            int cordLen = Math.min(up - 3, up > 9 ? 2 + (int) (rnd(x, z, 999) * 5) : (int) (rnd(x, z, 999) * 3)), low = y;
            for (int k = 0; k <= cordLen && v.get(x, y - k, z) == null; k++) {
                v.set(x, y - k, z, k == cordLen ? m.lamp : m.cord);
                low = y - k;
            }
            if (v.get(x, low, z) == m.cord) v.set(x, low, z, m.lamp);
            if (v.get(x, low - 1, z) == null) v.set(x, low - 1, z, m.light);
            int over = y - up + 3; // and one just over head height, however high the room is
            if (over < low - 1 && v.get(x, over, z) == null) v.set(x, over, z, m.light);
        }
    }

    @Override
    protected void skinSpecial(V v, M m) {
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the Sleeper's hall is held up by everything a bedroom had in it, stacked: bedposts, bolsters, sheets, toys
            if (lord.pillar(x, z)) for (int y = lord.feet; y < lord.roof(x, z); y++) {
                int k = (y - lord.feet) % 6;
                v.set(x, y, z, k == 1 || k == 4 ? m.sheet : k == 2 ? m.plush : k == 5 ? m.toy : m.wood);
            } else if (d < 1.6 || Math.abs(d - 9.4) < 0.55) v.set(x, lord.feet - 1, z, m.sheet); // a pale ring in the floor
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xd8d0ff, 0.6F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x5a4a80, 0.5F).light(lord.cx, lord.feet + 4, lord.cz, 0xffe090, 18, 0.8F);
        return airOf(air);
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): a house a dream remembers: corridors that are rooms, a landing along one side, boards that give, a stairwell. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.HALL, Cave.Form.CROSS, Cave.Form.TERRACE, Cave.Form.HALL), List.of(Arena.Plan.SQUARE, Arena.Plan.NAVE),
                List.of(Arena.Plan.NAVE, Arena.Plan.SQUARE), List.of(Chasm.Kind.FALLING, Chasm.Kind.BEAMS, Chasm.Kind.HANGERS, Chasm.Kind.BRIDGE), true, 2.2, 4);
    }

    /** Its small pieces (SPEC §50.9 "Depth"): a skirting and a cove of dark wood, plush drifted in corners, feathers, candles someone left, motes. */
    @Override
    protected Trim trim(SleeplessWard.M m) {
        return new Trim(named("dream_wood_stairs"), named("dream_wood_stairs"), named("dream_plush_drift"), named("dream_feathers"),
                namedAll("dream_candle"), namedAll("dream_motes"), 0.7);
    }

    /** The colour of its light in the air (the same as its biome's). */
    @Override
    protected int airTint() {
        return 0xffe9a0;
    }
}
