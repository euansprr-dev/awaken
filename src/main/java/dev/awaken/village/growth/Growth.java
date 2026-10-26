package dev.awaken.village.growth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A village grows (SPEC §28.7, PLAN 6.4): when its prosperity (trade, notices filled, troubles solved) runs high, a
 * plot past the end of one of its lanes is fenced and stacked with timber and stone; the next morning masons raise
 * scaffolding and build a house there over two and a half play-days, course by course from the floor up (one of the
 * village's own houses, turned to face the lane); then a new family comes up the lane and moves in. From the next time
 * the village wakes they live there on the people engine like everyone else. Growth goes on with calm villages on.
 */
public final class Growth {
    private Growth() {}

    public static final int NONE = 0, FENCED = 1, BUILDING = 2, ARRIVING = 3;
    /** Prosperity that raises a house (it costs some), houses a hamlet and a village may add, the masons' time. */
    static final int RICH = 72, COST = 14;
    static final long WORK = 60000;

    /** A house raised: its door and the cell before it, its box (two corners), its family and how many they are. */
    public record Grown(long door, long out, long box0, long box1, String family, int people) {
        static final Codec<Grown> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("door").forGetter(Grown::door),
                Codec.LONG.fieldOf("out").forGetter(Grown::out),
                Codec.LONG.fieldOf("box0").forGetter(Grown::box0),
                Codec.LONG.fieldOf("box1").forGetter(Grown::box1),
                Codec.STRING.fieldOf("family").forGetter(Grown::family),
                Codec.INT.optionalFieldOf("people", 3).forGetter(Grown::people)
        ).apply(i, Grown::new));
    }

    /** A village's growth: the plot under way (its stage, where, turned how, from which house, its cells) and the houses raised. */
    public static final class State {
        int stage, rot, template, placed;
        long origin, start, lastGrew, street;
        String family;
        final List<Long> cells, laid;
        final List<Grown> houses;

        State(int stage, int rot, int template, int placed, long origin, long start, long lastGrew, long street, String family, List<Long> cells,
              List<Long> laid, List<Grown> houses) {
            this.stage = stage;
            this.rot = rot;
            this.template = template;
            this.placed = placed;
            this.origin = origin;
            this.start = start;
            this.lastGrew = lastGrew;
            this.street = street;
            this.family = family;
            this.cells = new ArrayList<>(cells);
            this.laid = new ArrayList<>(laid);
            this.houses = new ArrayList<>(houses);
        }

        public static State fresh() {
            return new State(NONE, 0, -1, 0, 0L, 0L, -100L, 0L, "", List.of(), List.of(), List.of());
        }

        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("stage", 0).forGetter(s -> s.stage),
                Codec.INT.optionalFieldOf("rot", 0).forGetter(s -> s.rot),
                Codec.INT.optionalFieldOf("template", -1).forGetter(s -> s.template),
                Codec.INT.optionalFieldOf("placed", 0).forGetter(s -> s.placed),
                Codec.LONG.optionalFieldOf("origin", 0L).forGetter(s -> s.origin),
                Codec.LONG.optionalFieldOf("start", 0L).forGetter(s -> s.start),
                Codec.LONG.optionalFieldOf("last_grew", -100L).forGetter(s -> s.lastGrew),
                Codec.LONG.optionalFieldOf("street", 0L).forGetter(s -> s.street),
                Codec.STRING.optionalFieldOf("family", "").forGetter(s -> s.family),
                Codec.LONG.listOf().optionalFieldOf("cells", List.of()).forGetter(s -> s.cells),
                Codec.LONG.listOf().optionalFieldOf("laid", List.of()).forGetter(s -> s.laid),
                Grown.CODEC.listOf().optionalFieldOf("houses", List.of()).forGetter(s -> s.houses)
        ).apply(i, State::new));

        public List<Grown> houses() {
            return List.copyOf(houses);
        }

        public int stage() {
            return stage;
        }
    }

    // ---- the tick (each second for an awake village) -----------------------------------------------------------------------

    /** {@code dirty} is called when anything saved changed. */
    public static void tick(ServerLevel level, VillageService.Live l, State g, long day, int tod, Runnable dirty, boolean busy) {
        switch (g.stage) {
            case NONE -> {
                int cap = l.plan().kind == VillagePlan.Kind.TOWN ? 4 : l.plan().big() ? 3 : 2;
                if (busy || g.houses.size() >= cap || day - g.lastGrew < 6) return;
                if (Villages.prosperity(level.getServer(), l.key()) < RICH) return;
                if (begin(level, l, g, day)) dirty.run();
            }
            case FENCED -> {
                // the masons start in the morning after the plot was fenced
                if (day > g.lastGrew && tod >= 1000 && tod < 9000) {
                    g.stage = BUILDING;
                    g.start = level.getGameTime();
                    scaffold(level, l, g);
                    dirty.run();
                }
            }
            case BUILDING -> {
                build(level, l, g, tod);
                dirty.run();
            }
            case ARRIVING -> {
                arrive(level, l, g, tod);
                dirty.run();
            }
            default -> {}
        }
    }

    // ---- the plot ------------------------------------------------------------------------------------------------------------

    /** The template's box: its plot and a ring round it, from the floor to above the ridge. */
    static int[] box(VillagePlan p, CityPlan.Plot q) {
        return new int[]{q.x0() - 1, q.z0() - 1, q.x1() + 1, q.z1() + 1, q.lift(), q.lift() + q.top() + 4};
    }

    /** Picks a house to copy and a lane's end to build past; reads the house's blocks; fences the plot. */
    public static boolean begin(ServerLevel level, VillageService.Live l, State g, long day) {
        VillagePlan p = l.plan();
        VillagePlan.Building tb = null;
        int best = Integer.MAX_VALUE;
        for (VillagePlan.Building b : p.buildings) {
            if (!b.use().equals("house")) continue;
            CityPlan.Plot q = p.plots.get(b.plot());
            int area = (q.x1() - q.x0() + 1) * (q.z1() - q.z0() + 1);
            if (area < best) {
                best = area;
                tb = b;
            }
        }
        if (tb == null) return false;
        CityPlan.Plot q = p.plots.get(tb.plot());
        int[] bx = box(p, q);
        int w = bx[2] - bx[0] + 1, d = bx[3] - bx[1] + 1, h = bx[5] - bx[4] + 1;
        BlockPos lo = new BlockPos(p.ox + bx[0], p.baseY + bx[4], p.oz + bx[1]);
        if (!level.isLoaded(lo) || !level.isLoaded(lo.offset(w, 0, d))) return false;
        // the template's front: the way out of its door
        Direction front = Direction.getApproximateNearest(tb.outX() - tb.doorX(), 0, tb.outZ() - tb.doorZ());
        // a lane's end with room past it
        for (VillagePlan.Street s : p.streets) {
            int n = s.points();
            if (n < 2) continue;
            for (int end : new int[]{n - 1, 0}) {
                int prev = end == 0 ? 1 : n - 2;
                double dx = s.x()[end] - s.x()[prev], dz = s.z()[end] - s.z()[prev];
                Direction out = Direction.getApproximateNearest(dx, 0, dz);
                int rot = turns(front, out.getOpposite());
                int[] door = rotated(tb.doorX() - bx[0], tb.doorZ() - bx[1], w, d, rot);
                int rw = rot % 2 == 0 ? w : d, rd = rot % 2 == 0 ? d : w;
                int ex = Math.round(s.x()[end]), ez = Math.round(s.z()[end]);
                int doorX = p.ox + ex + out.getStepX() * 4, doorZ = p.oz + ez + out.getStepZ() * 4;
                int ox = doorX - door[0], oz = doorZ - door[1];
                int ground = p.baseY + s.lift()[end];
                if (!fits(level, p, ox, oz, rw, rd, ground)) continue;
                // read the house
                g.cells.clear();
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                for (int y = 0; y < h; y++)
                    for (int z = 0; z < d; z++)
                        for (int x = 0; x < w; x++) {
                            BlockState st = level.getBlockState(m.set(lo.getX() + x, lo.getY() + y, lo.getZ() + z));
                            if (st.isAir() || st.is(Blocks.SCAFFOLDING) || !st.getFluidState().isEmpty()) continue;
                            if (y == 0 && (x == 0 || z == 0 || x == w - 1 || z == d - 1)) continue; // the yard's ground stays the land's
                            g.cells.add((long) x | (long) y << 8 | (long) z << 16 | (long) Block.getId(st) << 24);
                        }
                if (g.cells.size() < 40) return false;
                g.cells.sort((a, b) -> Long.compare(a >>> 8 & 0xFF, b >>> 8 & 0xFF));
                g.rot = rot;
                g.template = tb.plot();
                g.origin = BlockPos.asLong(ox, ground, oz);
                g.street = BlockPos.asLong(p.ox + ex, ground, p.oz + ez);
                g.placed = 0;
                g.stage = FENCED;
                g.lastGrew = day;
                g.family = family(p, g.houses.size());
                fence(level, l, g, rw, rd, out);
                Villages.addProsperity(level.getServer(), l.key(), -COST);
                Villages.history(level.getServer(), l.key(), "A new plot was fenced past the end of the lane, timber and stone stacked on it.");
                Awaken.LOG.info("[village] {} grows: a plot at {} (turned {}, {} cells from plot {})", p.name, BlockPos.of(g.origin).toShortString(), rot,
                        g.cells.size(), tb.plot());
                return true;
            }
        }
        return false;
    }

    /** Quarter turns clockwise that bring {@code from} round to {@code to}. */
    static int turns(Direction from, Direction to) {
        int n = 0;
        Direction d = from;
        while (d != to && n < 4) {
            d = d.getClockWise();
            n++;
        }
        return n % 4;
    }

    /** A cell of a w x d box turned {@code rot} quarter turns clockwise (as {@link Rotation} turns a block). */
    static int[] rotated(int x, int z, int w, int d, int rot) {
        return switch (rot & 3) {
            case 1 -> new int[]{d - 1 - z, x};
            case 2 -> new int[]{w - 1 - x, d - 1 - z};
            case 3 -> new int[]{z, w - 1 - x};
            default -> new int[]{x, z};
        };
    }

    static Rotation rotation(int rot) {
        return switch (rot & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Free ground: nobody's plot, no water, no more than two blocks off the lane's height, within reach of the village. */
    static boolean fits(ServerLevel level, VillagePlan p, int ox, int oz, int w, int d, int ground) {
        if (Math.hypot(ox + w / 2.0 - p.ox, oz + d / 2.0 - p.oz) > p.radius() + 22) return false;
        for (int x = -1; x <= w; x++)
            for (int z = -1; z <= d; z++) {
                int wx = ox + x, wz = oz + z;
                if (!level.isLoaded(new BlockPos(wx, 0, wz))) return false;
                if (p.owns(wx, wz) || p.plotAt(wx - p.ox, wz - p.oz) >= 0) return false;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                if (Math.abs(top - 1 - ground) > 2) return false;
                if (!level.getFluidState(new BlockPos(wx, top - 1, wz)).isEmpty() || !level.getFluidState(new BlockPos(wx, top, wz)).isEmpty()) return false;
            }
        return true;
    }

    static String family(VillagePlan p, int n) {
        String[] names = {"Ashdown", "Merrow", "Calloway", "Fairweather", "Holloway", "Penrose", "Quill", "Rushby", "Starling", "Thornley"};
        return names[(int) Math.floorMod(p.seed + n * 7919L, (long) names.length)];
    }

    private static void lay(ServerLevel level, State g, BlockPos at, BlockState s) {
        BlockState was = level.getBlockState(at);
        if (!was.isAir() && !was.canBeReplaced()) return;
        level.setBlock(at, s, Block.UPDATE_ALL);
        g.laid.add(at.asLong());
    }

    /** A fence round the plot (a gate toward the lane), the land inside cleared, timber and stone stacked in it. */
    private static void fence(ServerLevel level, VillageService.Live l, State g, int w, int d, Direction toLane) {
        BlockPos o = BlockPos.of(g.origin);
        int ground = o.getY();
        // clear the plot: plants and trees over it, the ground made level at the lane's height
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= w; x++)
            for (int z = -1; z <= d; z++) {
                for (int y = ground + 1; y <= ground + 14; y++) {
                    BlockState s = level.getBlockState(m.set(o.getX() + x, y, o.getZ() + z));
                    if (!s.isAir() && (s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES)
                            || y > ground + 1 && s.is(net.minecraft.tags.BlockTags.DIRT)))
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                BlockState top = level.getBlockState(m.set(o.getX() + x, ground, o.getZ() + z));
                if (top.isAir()) level.setBlock(m, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        // the fence and its gate
        int gx = toLane.getStepX() != 0 ? (toLane.getStepX() > 0 ? w : -1) : w / 2, gz = toLane.getStepZ() != 0 ? (toLane.getStepZ() > 0 ? d : -1) : d / 2;
        for (int x = -1; x <= w; x++)
            for (int z = -1; z <= d; z++) {
                if (x != -1 && x != w && z != -1 && z != d) continue;
                BlockPos at = new BlockPos(o.getX() + x, ground + 1, o.getZ() + z);
                if (x == gx && z == gz) lay(level, g, at, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, toLane));
                else lay(level, g, at, Blocks.OAK_FENCE.defaultBlockState());
            }
        // the timber and the stone, stacked at the back
        BlockPos back = o.offset(w / 2, 1, d / 2).relative(toLane.getOpposite(), Math.max(1, Math.min(w, d) / 2 - 1));
        for (int k = 0; k < 3; k++) {
            lay(level, g, back.offset(k - 1, 0, 0), Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
            lay(level, g, back.offset(k - 1, 0, 1), Blocks.COBBLESTONE.defaultBlockState());
        }
        lay(level, g, back.offset(0, 1, 0), Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
    }

    /** Scaffolding at the plot's corners, up to the eaves; the fence comes down (the piles stay till the end). */
    private static void scaffold(ServerLevel level, VillageService.Live l, State g) {
        int[] size = size(l, g);
        BlockPos o = BlockPos.of(g.origin);
        for (long p : List.copyOf(g.laid)) {
            BlockPos at = BlockPos.of(p);
            BlockState s = level.getBlockState(at);
            if (s.is(Blocks.OAK_FENCE) || s.is(Blocks.OAK_FENCE_GATE)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                g.laid.remove(p);
            }
        }
        int[][] corners = {{-1, -1}, {size[0], -1}, {-1, size[1]}, {size[0], size[1]}};
        for (int[] c : corners)
            for (int y = 1; y <= 6; y++) lay(level, g, o.offset(c[0], y, c[1]), Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0));
    }

    /** The template's box size turned: {w, d}. */
    static int[] size(VillageService.Live l, State g) {
        CityPlan.Plot q = l.plan().plots.get(g.template);
        int[] bx = box(l.plan(), q);
        int w = bx[2] - bx[0] + 1, d = bx[3] - bx[1] + 1;
        return g.rot % 2 == 0 ? new int[]{w, d} : new int[]{d, w};
    }

    /** Course by course from the floor up, at the masons' pace (caught up when the village wakes). */
    private static void build(ServerLevel level, VillageService.Live l, State g, int tod) {
        CityPlan.Plot q = l.plan().plots.get(g.template);
        int[] bx = box(l.plan(), q);
        int w = bx[2] - bx[0] + 1, d = bx[3] - bx[1] + 1;
        BlockPos o = BlockPos.of(g.origin);
        int target = (int) Math.min(g.cells.size(), Math.ceil(g.cells.size() * Math.min(1.0, (level.getGameTime() - g.start) / (double) WORK)));
        int budget = Math.min(40, target - g.placed);
        Rotation r = rotation(g.rot);
        BlockPos last = null;
        for (int k = 0; k < budget && g.placed < g.cells.size(); k++) {
            long c = g.cells.get(g.placed++);
            int x = (int) (c & 0xFF), y = (int) (c >>> 8 & 0xFF), z = (int) (c >>> 16 & 0xFF);
            BlockState s = Block.stateById((int) (c >>> 24)).rotate(r);
            int[] rc = rotated(x, z, w, d, g.rot);
            BlockPos at = new BlockPos(o.getX() + rc[0], o.getY() + y, o.getZ() + rc[1]);
            if (!level.isLoaded(at)) {
                g.placed--;
                return;
            }
            BlockState was = level.getBlockState(at);
            if (was.is(Blocks.SCAFFOLDING)) continue;
            level.setBlock(at, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            last = at;
        }
        if (last != null) {
            level.playSound(null, last, level.getBlockState(last).getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 0.9f);
            level.sendParticles(ParticleTypes.POOF, last.getX() + 0.5, last.getY() + 0.5, last.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
        }
        masons(level, l, g, tod);
        if (g.placed >= g.cells.size()) finish(level, l, g);
    }

    /** Two of the village at the plot by day, hammering and laying. */
    private static void masons(ServerLevel level, VillageService.Live l, State g, int tod) {
        int[] size = size(l, g);
        BlockPos o = BlockPos.of(g.origin);
        Vec3 mid = new Vec3(o.getX() + size[0] / 2.0, o.getY() + 1, o.getZ() + size[1] / 2.0);
        int n = 0;
        for (int i = 0; i < l.settlement().people() && n < 2; i++) {
            String job = l.settlement().job(i);
            if (!job.equals("smith") && !job.equals("woodcutter") && !job.equals("carter") && !job.equals("miner")) continue;
            if (l.settlement().isAway(i)) continue;
            boolean work = tod >= 1000 && tod < 11000;
            if (!work) {
                l.settlement().stage(i, null, Float.NaN, "", "");
                n++;
                continue;
            }
            Vec3 at = new Vec3(n == 0 ? o.getX() - 1.5 : o.getX() + size[0] + 0.5, o.getY() + 1, o.getZ() + size[1] / 2.0);
            l.settlement().stage(i, at, (float) Math.toDegrees(Math.atan2(-(mid.x - at.x), mid.z - at.z)), n == 0 ? "hammer" : "trowel", n == 0 ? "hammer" : "trowel");
            n++;
        }
    }

    /** Built: the scaffolding and the piles go, a path to the door, and the family comes up the lane. */
    private static void finish(ServerLevel level, VillageService.Live l, State g) {
        for (long p : g.laid) {
            BlockPos at = BlockPos.of(p);
            BlockState s = level.getBlockState(at);
            if (s.is(Blocks.SCAFFOLDING) || s.is(Blocks.OAK_LOG) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.OAK_FENCE) || s.is(Blocks.OAK_FENCE_GATE))
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        g.laid.clear();
        // the path from the lane's end to the door
        BlockPos street = BlockPos.of(g.street);
        BlockPos door = doorOf(l, g);
        for (int k = 0; k <= 6; k++) {
            double t = k / 6.0;
            int x = (int) Math.round(street.getX() + (door.getX() - street.getX()) * t), z = (int) Math.round(street.getZ() + (door.getZ() - street.getZ()) * t);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos at = new BlockPos(x, y, z);
            if (level.getBlockState(at).is(Blocks.GRASS_BLOCK) || level.getBlockState(at).is(Blocks.DIRT)) level.setBlock(at, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
        }
        g.stage = ARRIVING;
        g.start = level.getGameTime();
        Villages.history(level.getServer(), l.key(), "The masons finished the new house at the end of the lane.");
    }

    /** Where the new house's door is (its plan door, turned). */
    static BlockPos doorOf(VillageService.Live l, State g) {
        VillagePlan p = l.plan();
        CityPlan.Plot q = p.plots.get(g.template);
        int[] bx = box(p, q);
        int w = bx[2] - bx[0] + 1, d = bx[3] - bx[1] + 1;
        VillagePlan.Building tb = null;
        for (VillagePlan.Building b : p.buildings) if (b.plot() == g.template) tb = b;
        int[] rc = tb == null ? new int[]{w / 2, 0} : rotated(tb.doorX() - bx[0], tb.doorZ() - bx[1], w, d, g.rot);
        BlockPos o = BlockPos.of(g.origin);
        return new BlockPos(o.getX() + rc[0], o.getY() + 1, o.getZ() + rc[1]);
    }

    /** The family comes up the lane (a carter leading their cart with their things), and the house is theirs. */
    private static void arrive(ServerLevel level, VillageService.Live l, State g, int tod) {
        BlockPos door = doorOf(l, g);
        BlockPos street = BlockPos.of(g.street);
        Direction toDoor = Direction.getApproximateNearest(door.getX() - street.getX(), 0, door.getZ() - street.getZ());
        BlockPos outside = door.relative(toDoor.getOpposite(), 2);
        long since = level.getGameTime() - g.start;
        int carter = -1;
        for (int i = 0; i < l.settlement().people(); i++) if (l.settlement().job(i).equals("carter") && !l.settlement().isAway(i)) carter = i;
        if (since < 25) {
            // the cart at the house's side, shafts toward the lane, loaded with their crates
            BlockPos body = outside.relative(toDoor.getClockWise(), 2), shafts = body.relative(toDoor.getOpposite());
            cart(level, g, body, toDoor.getOpposite(), "body");
            cart(level, g, shafts, toDoor.getOpposite(), "shafts");
            // and the family walking up behind it from the lane's end
            java.util.Random r = new java.util.Random(l.plan().seed ^ g.houses.size() * 31L);
            String[] given = {"Wenna", "Abel", "Marit", "Coll", "Isolde", "Pell", "Rue", "Tam"};
            for (int k = 0; k < 3; k++) {
                int look = r.nextInt(Math.max(1, l.settlement().people()));
                var lk = l.settlement().look(look);
                Vec3 from = new Vec3(street.getX() + 0.5 - toDoor.getStepX() * (2 + k * 1.4), street.getY() + 1, street.getZ() + 0.5 - toDoor.getStepZ() * (2 + k * 1.4));
                dev.awaken.village.trouble.Rescuee.arrival(level, from, new Vec3(outside.getX() + 0.5 + k * 0.6, outside.getY(), outside.getZ() + 0.5),
                        given[r.nextInt(given.length)] + " " + g.family, lk.texture(), lk.slim(), k == 2);
            }
        }
        if (carter >= 0 && since < 20 * 40) {
            Vec3 at = new Vec3(outside.getX() + 0.5 + toDoor.getClockWise().getStepX() * 2.2, outside.getY(), outside.getZ() + 0.5 + toDoor.getClockWise().getStepZ() * 2.2);
            l.settlement().stage(carter, at, (float) Math.toDegrees(Math.atan2(-(door.getX() - at.x), door.getZ() - at.z)), "set_down", "");
        }
        if (since < 20 * 40) return;
        if (carter >= 0) l.settlement().stage(carter, null, Float.NaN, "", "");
        for (long p : g.laid) if (level.isLoaded(BlockPos.of(p))) level.setBlock(BlockPos.of(p), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        g.laid.clear();
        // theirs: from the village's next waking they live there
        CityPlan.Plot q = l.plan().plots.get(g.template);
        int[] size = size(l, g);
        BlockPos o = BlockPos.of(g.origin);
        g.houses.add(new Grown(door.asLong(), outside.asLong(), o.asLong(), BlockPos.asLong(o.getX() + size[0] - 1, o.getY() + q.top() + 4, o.getZ() + size[1] - 1),
                g.family, 3));
        Villages.history(level.getServer(), l.key(), "The " + g.family + "s came up the lane with their cart and moved into the new house.");
        Villages.addMood(level.getServer(), l.key(), 8);
        Awaken.LOG.info("[village] {} grew: the {}s moved in at {}", l.plan().name, g.family, door.toShortString());
        g.stage = NONE;
        g.cells.clear();
        g.template = -1;
        pendingRestart.add(l.key());
    }

    /** One cell of a cart (the city kit's), facing a way. */
    private static void cart(ServerLevel level, State g, BlockPos at, Direction facing, String part) {
        Block cart = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Awaken.id("cart"));
        if (cart == Blocks.AIR) return;
        BlockState s = cart.defaultBlockState();
        for (var prop : s.getProperties()) {
            String want = switch (prop.getName()) {
                case "facing" -> facing.getSerializedName();
                case "part" -> part;
                case "load" -> "crates";
                default -> null;
            };
            if (want != null) s = with(s, prop, want);
        }
        lay(level, g, at, s);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, net.minecraft.world.level.block.state.properties.Property<T> prop, String value) {
        return prop.getValue(value).map(v -> s.setValue(prop, v)).orElse(s);
    }

    /** Tours and tests: the stage the plot is at moved on (the masons start now; or, {@code all}, the house finished). */
    public static String hurry(ServerLevel level, VillageService.Live l, State g, long day, boolean all) {
        if (g.stage == FENCED) {
            g.lastGrew = day - 1;
            g.stage = BUILDING;
            g.start = level.getGameTime() - (all ? WORK : WORK * 2 / 5);
            scaffold(level, l, g);
            return all ? "the masons at it, the house going up fast" : "the masons begin";
        }
        if (g.stage == BUILDING) {
            g.start = level.getGameTime() - (all ? WORK : Math.min(WORK, level.getGameTime() - g.start + WORK / 3));
            return "the masons further on (" + g.placed + " of " + g.cells.size() + ")";
        }
        if (g.stage == ARRIVING) {
            g.start = level.getGameTime() - 20 * 40;
            return "the family moves in";
        }
        return "nothing under way";
    }

    /** Villages to restart (their roster now holds a new family) once no player is near. */
    public static final java.util.Set<String> pendingRestart = new java.util.HashSet<>();

    /** Restarts a grown village when nobody is within reach of seeing it happen (the service starts it again by itself). */
    public static void restartIfAlone(ServerLevel level, VillageService.Live l) {
        if (!pendingRestart.contains(l.key())) return;
        for (ServerPlayer p : level.players()) if (Math.hypot(p.getX() - l.plan().ox, p.getZ() - l.plan().oz) < l.plan().radius() + 80) return;
        pendingRestart.remove(l.key());
        VillageService.stop(l);
        Awaken.LOG.info("[village] {} restarts with its new family", l.plan().name);
    }
}
