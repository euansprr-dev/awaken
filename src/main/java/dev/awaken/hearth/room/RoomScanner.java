package dev.awaken.hearth.room;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.block.HearthfireBlock;
import dev.awaken.hearth.block.TrophyMountBlockEntity;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Finds and reads rooms inside a Hearth's radius. Cheap and budgeted: a palette sweep skips every chunk section that
 * holds nothing a home is made of, then a flood fill from each piece of furniture (and each door, light, chest, sign,
 * and the household standing in the radius) finds the enclosed space around it, and one pass over that space's cells
 * and wall faces reads it ({@link Tally}, {@link Reading}). Every stage stops when the tick's budget is spent and goes
 * on next tick, so a big hall never costs more than the budget in one tick.
 *
 * <p>When a flood leaks, a second bounded fill of the covered space around the furniture says why ({@link Miss}): it
 * sees the sky, a hole in the roof, a gap in a wall, or it is too big. Furniture in the same leaky space shares that
 * answer without another fill.
 */
public final class RoomScanner {
    /** A room is at most this many open cells and this many blocks a side (a big hall fits); anything bigger is "outside". */
    public static final int MAX_CELLS = 20_000;
    public static final int MAX_EXTENT = 48;
    /** Explanations per scan, and per kind of furniture (each costs one more bounded fill; village beds can't use them up). */
    public static final int MAX_MISSES = 24, MAX_MISSES_PER_KIND = 4;
    /** How far above the heightmap a slab or trapdoor still counts as a roof. */
    private static final int ROOF_REACH = 24;
    /** How far outside a room the ground is looked for (a tower's foot). */
    private static final int GROUND_OUT = 4;

    private static final Predicate<BlockState> INTEREST = RoomScanner::interesting;
    private static final Direction[] DIRS = Direction.values();
    /** Seeds that aren't furniture (a door, a light, a person standing there). */
    private static final int NO_KIND = -1, LIGHT_KIND = -2;

    private enum Phase { SWEEP, FLOOD, DONE }

    private final ServerLevel level;
    private final HearthRecord hearth;
    private final Consumer<Scan> onDone;
    private Phase phase = Phase.SWEEP;
    private final int minCx, maxCx, minCz, maxCz, minSy, maxSy;
    private int cx, cz, sy;
    private final List<BlockPos> seeds = new ArrayList<>();
    private final IntArrayList seedKinds = new IntArrayList();
    private int seedIndex;
    private final LongSet claimed = new LongOpenHashSet();
    /** Every cell of every closed room this scan (what counts as indoors for the yard's lights). */
    private final LongSet indoors = new LongOpenHashSet();
    /** Cells of leaky spaces, mapped to the miss that explains them. */
    private final Long2IntOpenHashMap missOf = new Long2IntOpenHashMap();
    /** "miss index × furniture" pairs already reported, so ten beds in one leaky barn give one line per kind. */
    private final Set<Long> reported = new HashSet<>();
    private final List<Room> found = new ArrayList<>();
    private final List<Miss> misses = new ArrayList<>();
    private final LongArrayList lights = new LongArrayList();
    private final int[] furniture = new int[Furniture.COUNT];
    private final int[] diagnosed = new int[Furniture.COUNT];
    private int fills;
    private int farmland, fences, walls, composters, flowers;
    /** (W6) Where the land's work is: farmland and fence gates (filtered to those outside rooms when the scan ends). */
    private final LongArrayList fieldCells = new LongArrayList(), gateCells = new LongArrayList();
    /** The seed being worked on (null between seeds). */
    private Job job;

    public RoomScanner(ServerLevel level, HearthRecord hearth, Consumer<Scan> onDone) {
        this(level, hearth, List.of(), onDone);
    }

    /** {@code people}: where the household stands in the radius (a room with nothing in it yet is still read). */
    public RoomScanner(ServerLevel level, HearthRecord hearth, List<BlockPos> people, Consumer<Scan> onDone) {
        this.level = level;
        this.hearth = hearth;
        this.onDone = onDone;
        int r = hearth.radius();
        BlockPos c = hearth.pos;
        minCx = SectionPos.blockToSectionCoord(c.getX() - r);
        maxCx = SectionPos.blockToSectionCoord(c.getX() + r);
        minCz = SectionPos.blockToSectionCoord(c.getZ() - r);
        maxCz = SectionPos.blockToSectionCoord(c.getZ() + r);
        minSy = Math.max(level.getMinSectionY(), SectionPos.blockToSectionCoord(c.getY() - 32));
        maxSy = Math.min(level.getMaxSectionY(), SectionPos.blockToSectionCoord(c.getY() + 64));
        cx = minCx;
        cz = minCz;
        sy = minSy;
        missOf.defaultReturnValue(-1);
        for (BlockPos p : people) {
            if (!hearth.contains(p)) continue;
            seeds.add(p.immutable());
            seedKinds.add(NO_KIND);
        }
    }

    public HearthRecord hearth() {
        return hearth;
    }

    public boolean done() {
        return phase == Phase.DONE;
    }

    /** Runs the whole scan now (commands and tests). */
    public void runToEnd() {
        while (!done()) step(Integer.MAX_VALUE);
    }

    /** Does about {@code budget} block reads of work. */
    public void step(int budget) {
        while (budget > 0 && phase != Phase.DONE) {
            budget -= switch (phase) {
                case SWEEP -> sweepOne();
                case FLOOD -> floodStep(budget);
                default -> 0;
            };
        }
    }

    // ---- phase 1: find furniture with the section palettes -----------------------------------------------------------

    private int sweepOne() {
        if (cx > maxCx) {
            phase = Phase.FLOOD;
            return 1 + nearestFirst();
        }
        int cost = 1;
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk != null) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            if (!section.hasOnlyAir() && section.maybeHas(INTEREST)) {
                cost += 4096;
                int bx = SectionPos.sectionToBlockCoord(cx), by = SectionPos.sectionToBlockCoord(sy), bz = SectionPos.sectionToBlockCoord(cz);
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState s = section.getBlockState(x, y, z);
                            if (s.isAir()) continue;
                            sweepBlock(s, bx + x, by + y, bz + z);
                        }
                    }
                }
            }
        }
        if (++sy > maxSy) {
            sy = minSy;
            if (++cz > maxCz) {
                cz = minCz;
                cx++;
            }
        }
        return cost;
    }

    private void sweepBlock(BlockState s, int x, int y, int z) {
        Furniture f = Furniture.of(s);
        boolean seed = f != null || isSeed(s);
        boolean land = s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.getBlock() instanceof ComposterBlock || s.is(BlockTags.FLOWERS);
        if (!seed && !land) return;
        BlockPos p = new BlockPos(x, y, z);
        if (!hearth.contains(p)) return;
        if (s.is(BlockTags.FENCES)) fences++;
        else if (s.is(BlockTags.WALLS)) walls++;
        else if (s.getBlock() instanceof ComposterBlock) composters++;
        else if (s.is(BlockTags.FLOWERS) && !(s.getBlock() instanceof FlowerPotBlock)) flowers++;
        if (!seed) return;
        if (f == Furniture.GROWING && !(s.getBlock() instanceof FlowerPotBlock)) {
            farmland++;
            if (fieldCells.size() < 1024 && s.getBlock() instanceof net.minecraft.world.level.block.FarmlandBlock) fieldCells.add(p.asLong());
        }
        if (s.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock && gateCells.size() < 32) gateCells.add(p.asLong());
        boolean light = givesLight(s);
        if (light) lights.add(p.asLong());
        seeds.add(p);
        seedKinds.add(f != null ? f.ordinal() : light ? LIGHT_KIND : NO_KIND);
        if (f != null && (f != Furniture.TROPHY || mountedTrophy(p) != null)) furniture[f.ordinal()]++;
    }

    /** Floods (and explains) what is nearest the Anchor first: that is most likely the owner's own. */
    private int nearestFirst() {
        int n = seeds.size();
        if (n < 2) return 1;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        BlockPos c = hearth.pos;
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(i -> seeds.get(i).distSqr(c)));
        List<BlockPos> s = new ArrayList<>(n);
        IntArrayList k = new IntArrayList(n);
        for (int i : order) {
            s.add(seeds.get(i));
            k.add(seedKinds.getInt(i));
        }
        seeds.clear();
        seeds.addAll(s);
        seedKinds.clear();
        seedKinds.addAll(k);
        return n;
    }

    // ---- phase 2: flood fill the enclosed space around each seed, then read it --------------------------------------

    /** One seed's work, carried across ticks: the fill, then the reading pass (a closed room) or the explanation. */
    private final class Job {
        final BlockPos seed;
        final Furniture kind;
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final LongOpenHashSet visited = new LongOpenHashSet();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        /** Why the fill stopped: null while filling or closed. */
        Miss.Leak stopped;
        boolean filled, side;
        // the reading pass
        LongIterator cells;
        Tally tally;
        LongOpenHashSet wallSeen;
        Set<String> trophies;

        Job(BlockPos seed, Furniture kind) {
            this.seed = seed;
            this.kind = kind;
        }
    }

    private int floodStep(int budget) {
        if (job == null) {
            if (!sides.isEmpty()) {
                // the other open sides of a door or a full-block piece: each floods on its own (inside and out never mix)
                long side = sides.dequeueLong();
                int k = sideKinds.removeInt(0);
                BlockPos at = BlockPos.of(side);
                if (claimed.contains(side)) {
                    inherit(k >= 0 ? Furniture.values()[k] : null, sideSeed, side);
                    return 1;
                }
                begin(at, k >= 0 ? Furniture.values()[k] : null, sideSeed);
                job.side = true;
                return 2;
            }
            if (seedIndex >= seeds.size()) {
                finish();
                return 1;
            }
            int si = seedIndex++;
            int k = seedKinds.getInt(si);
            return startJob(seeds.get(si), k >= 0 ? Furniture.values()[k] : null, k);
        }
        if (!job.filled) return fill(budget);
        if (job.stopped == null) return read(budget);
        int cost = diagnose(job.kind, job.seed, job.visited, job.stopped);
        job = null;
        return cost;
    }

    /** Open sides of the last solid seed still to flood, with its kind. */
    private final LongArrayFIFOQueue sides = new LongArrayFIFOQueue();
    private final IntArrayList sideKinds = new IntArrayList();
    private BlockPos sideSeed = BlockPos.ZERO;
    /** One of the solid seed's sides closed into a room: its other sides' leaks need no explaining. */
    private boolean sideClosed;

    private int startJob(BlockPos seed, Furniture kind, int k) {
        if (passable(seed)) {
            if (claimed.contains(seed.asLong())) {
                inherit(kind, seed, seed.asLong());
                return 1;
            }
            return begin(seed, kind, seed);
        }
        // Full-block furniture (a Forge, a machine) and doors flood from the open cells around them, one side at a time.
        // Cells another flood already covered are that room (counted from its walls) or that leak (explained once).
        sideSeed = seed;
        sideClosed = false;
        for (Direction d : DIRS) {
            BlockPos n = seed.relative(d);
            if (!level.isLoaded(n) || !passable(n)) continue;
            long nl = n.asLong();
            if (claimed.contains(nl)) {
                inherit(kind, seed, nl);
                continue;
            }
            sides.enqueue(nl);
            sideKinds.add(k);
        }
        return 7;
    }

    /** Starts the fill from one open cell. {@code seed}: the furniture it explains (the cell itself or a solid piece beside it). */
    private int begin(BlockPos start, Furniture kind, BlockPos seed) {
        Job j = new Job(seed, kind);
        j.queue.enqueue(start.asLong());
        j.visited.add(start.asLong());
        job = j;
        return 2;
    }

    /** The fill, as far as the budget goes: open as soon as it sees the sky, meets unloaded land or outgrows a room. */
    private int fill(int budget) {
        Job j = job;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cost = 0;
        while (!j.queue.isEmpty() && cost < budget) {
            long l = j.queue.dequeueLong();
            m.set(l);
            cost += 7;
            if (seesSky(m)) {
                j.stopped = Miss.Leak.SKY;
                break;
            }
            int x = m.getX(), y = m.getY(), z = m.getZ();
            j.minX = Math.min(j.minX, x); j.maxX = Math.max(j.maxX, x);
            j.minY = Math.min(j.minY, y); j.maxY = Math.max(j.maxY, y);
            j.minZ = Math.min(j.minZ, z); j.maxZ = Math.max(j.maxZ, z);
            if (j.visited.size() > MAX_CELLS) {
                j.stopped = Miss.Leak.TOO_BIG;
                break;
            }
            if (j.maxX - j.minX >= MAX_EXTENT || j.maxZ - j.minZ >= MAX_EXTENT || j.maxY - j.minY >= MAX_EXTENT) {
                j.stopped = Miss.Leak.TOO_WIDE;
                break;
            }
            for (Direction d : DIRS) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                long nl = m.asLong();
                if (j.visited.contains(nl)) continue;
                if (!level.isLoaded(m)) {
                    j.stopped = Miss.Leak.OPENING;
                    break;
                }
                if (passable(m)) {
                    j.visited.add(nl);
                    j.queue.enqueue(nl);
                }
            }
            if (j.stopped != null) break;
        }
        cost += takeSkyReads();
        if (j.stopped == null && !j.queue.isEmpty()) return cost; // more next tick
        j.filled = true;
        claimed.addAll(j.visited);
        if (j.stopped != null) {
            // a leaky space: only the furniture the guide explains is worth a second fill; the rest just ends here. A solid
            // piece is explained once, from its last side, and only when none of its sides is a room.
            if (j.kind == null || !j.kind.explained() || j.side && (!sides.isEmpty() || sideClosed)) job = null;
            return cost;
        }
        j.cells = j.visited.iterator();
        j.tally = new Tally();
        j.tally.bounds(j.minX, j.minZ, j.maxX, j.maxZ);
        j.wallSeen = new LongOpenHashSet();
        j.trophies = new HashSet<>();
        return cost;
    }

    /** Furniture whose space another flood already found leaky gets that flood's answer. */
    private void inherit(Furniture kind, BlockPos seed, long cell) {
        if (kind == null || !kind.explained()) return;
        int mi = missOf.get(cell);
        if (mi < 0 || !reported.add((long) mi * Furniture.COUNT + kind.ordinal())) return;
        Miss base = misses.get(mi);
        misses.add(new Miss(kind, seed, base.leak(), base.at()));
    }

    // ---- why a leaky space is not a room -----------------------------------------------------------------------------

    /**
     * Fills the covered space around the furniture, treating open sky as a wall: a covered cell with sky right above it
     * means no roof; sky beside the space inside its footprint is a hole in the roof; sky beside it at the edge is a gap
     * in a wall. A space that outgrew a room needs no second fill: it is too big or too wide. Returns its cost.
     */
    private int diagnose(Furniture kind, BlockPos seed, LongOpenHashSet firstFill, Miss.Leak stopped) {
        if (fills >= MAX_MISSES || diagnosed[kind.ordinal()] >= MAX_MISSES_PER_KIND) return 1;
        fills++;
        diagnosed[kind.ordinal()]++;
        int cost = 0;
        Miss.Leak leak = null;
        BlockPos at = null;
        LongOpenHashSet seen = new LongOpenHashSet();
        if (stopped == Miss.Leak.TOO_BIG || stopped == Miss.Leak.TOO_WIDE) {
            leak = stopped;
            at = seed;
        }
        List<BlockPos> starts = new ArrayList<>();
        if (leak == null) {
            if (passable(seed)) starts.add(seed);
            else for (Direction d : DIRS) {
                BlockPos n = seed.relative(d);
                if (level.isLoaded(n) && passable(n)) starts.add(n);
            }
            for (BlockPos s : starts) {
                cost += 4;
                if (seesSky(s)) {
                    leak = Miss.Leak.SKY;
                    at = s.immutable();
                    break;
                }
            }
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        if (leak == null) {
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            for (BlockPos s : starts) if (seen.add(s.asLong())) queue.enqueue(s.asLong());
            long up = Long.MIN_VALUE;
            LongArrayList sideFrom = new LongArrayList(), sideTo = new LongArrayList();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            Miss.Leak big = null;
            // the explanation is a cheaper fill than a room's: a space this size with a gap is told by its first gap
            int cap = Math.min(MAX_CELLS, 6144);
            while (!queue.isEmpty()) {
                long l = queue.dequeueLong();
                m.set(l);
                cost += 7;
                int x = m.getX(), y = m.getY(), z = m.getZ();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                if (maxX - minX >= MAX_EXTENT || maxZ - minZ >= MAX_EXTENT || maxY - minY >= MAX_EXTENT) {
                    big = Miss.Leak.TOO_WIDE;
                    break;
                }
                if (seen.size() > cap) {
                    big = Miss.Leak.TOO_BIG;
                    break;
                }
                for (Direction d : DIRS) {
                    m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                    long nl = m.asLong();
                    if (seen.contains(nl)) continue;
                    if (!level.isLoaded(m)) {
                        if (sideFrom.size() < 32) { sideFrom.add(l); sideTo.add(nl); }
                        continue;
                    }
                    if (!passable(m)) continue;
                    cost += 2;
                    if (seesSky(m)) {
                        if (d == Direction.UP) {
                            if (up == Long.MIN_VALUE) up = l;
                        } else if (sideFrom.size() < 32) {
                            sideFrom.add(l);
                            sideTo.add(nl);
                        }
                        continue;
                    }
                    seen.add(nl);
                    queue.enqueue(nl);
                }
            }
            if (up != Long.MIN_VALUE) {
                leak = Miss.Leak.SKY;
                at = BlockPos.of(up).above();
            } else {
                // Open sky beside the space is a hole in the roof when it lies inside the space's footprint or a wall stands
                // right behind it; otherwise the space runs out through a gap in a wall.
                for (int i = 0; i < sideTo.size() && leak == null; i++) {
                    BlockPos from = BlockPos.of(sideFrom.getLong(i)), to = BlockPos.of(sideTo.getLong(i));
                    BlockPos beyond = to.offset(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
                    boolean within = to.getX() > minX && to.getX() < maxX && to.getZ() > minZ && to.getZ() < maxZ;
                    cost += 3;
                    boolean walled = level.isLoaded(beyond) && (!passable(beyond) || !seesSky(beyond));
                    if (within || walled) {
                        leak = Miss.Leak.ROOF_HOLE;
                        at = to;
                    }
                }
                if (leak == null && !sideFrom.isEmpty()) {
                    leak = Miss.Leak.OPENING;
                    at = BlockPos.of(sideFrom.getLong(0));
                }
                if (leak == null) {
                    leak = big != null ? big : Miss.Leak.OPENING;
                    at = seed;
                }
            }
        }
        int mi = misses.size();
        misses.add(new Miss(kind, seed, leak, at));
        reported.add((long) mi * Furniture.COUNT + kind.ordinal());
        var it = firstFill.iterator();
        while (it.hasNext()) missOf.put(it.nextLong(), mi);
        it = seen.iterator();
        while (it.hasNext()) missOf.put(it.nextLong(), mi);
        claimed.addAll(seen);
        return cost + takeSkyReads();
    }

    // ---- the reading pass ---------------------------------------------------------------------------------------------

    /**
     * Walks the room's cells and every wall face once, as far as the budget goes: furniture, the floor and its light,
     * what the walls, floor and roof are made of, windows, fire, detail, seats and tables. Then classifies and reads it.
     */
    private int read(int budget) {
        Job j = job;
        Tally t = j.tally;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cost = 0;
        while (j.cells.hasNext() && cost < budget) {
            long l = j.cells.nextLong();
            m.set(l);
            cost += 8;
            int x = m.getX(), y = m.getY(), z = m.getZ();
            BlockState s = level.getBlockState(m);
            cell(t, j, s, m);
            if (!j.visited.contains(BlockPos.asLong(x, y - 1, z))) {
                cost++;
                t.floorCell(x, z, level.getBrightness(LightLayer.BLOCK, m) >= 8);
                t.floorY = Math.min(t.floorY, y);
            }
            for (Direction d : DIRS) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                long nl = m.asLong();
                if (j.visited.contains(nl)) continue;
                boolean first = j.wallSeen.add(nl);
                if (d == Direction.DOWN) {
                    // (W6) a seat is a stair someone can sit on from above: counted from the cell over it, whichever side
                    // the reading met the stair first (a bench's stair is also beside the floor's other cells)
                    cost++;
                    BlockState under = level.getBlockState(m);
                    if (under.is(BlockTags.STAIRS) && under.hasProperty(StairBlock.HALF) && under.getValue(StairBlock.HALF) == Half.BOTTOM) t.seats++;
                }
                if (!first) continue;
                cost += 2;
                wall(t, j, level.getBlockState(m), m, d);
            }
        }
        if (j.cells.hasNext()) return cost;
        cost += finishRoom(j);
        job = null;
        return cost;
    }

    /** One open cell: furniture that isn't a full block, and the room's soft things, lights and fire. */
    private void cell(Tally t, Job j, BlockState s, BlockPos at) {
        t.cells++;
        if (s.isAir()) return;
        Block b = s.getBlock();
        Furniture f = Furniture.of(s);
        if (f == Furniture.TROPHY) {
            String boss = mountedTrophy(at);
            if (boss != null) j.trophies.add(boss);
        } else if (f != null) {
            t.furniture[f.ordinal()]++;
        }
        if (s.is(BlockTags.WOOL_CARPETS) || b == Blocks.MOSS_CARPET || b == Blocks.PALE_MOSS_CARPET) t.detail(Tally.CARPET);
        else if (s.is(BlockTags.BANNERS)) t.detail(Tally.BANNER);
        else if (b instanceof FlowerPotBlock) t.detail(Tally.POT);
        else if (s.is(BlockTags.CANDLES)) {
            t.detail(Tally.CANDLE);
            if (lit(s)) t.warm(Tally.WARM_EMBER);
        } else if (b instanceof LanternBlock) t.detail(Tally.LANTERN);
        else if (b instanceof ChainBlock) t.detail(Tally.CHAIN);
        else if (s.is(BlockTags.CAMPFIRES)) {
            if (lit(s)) t.warm(Tally.WARM_FIRE);
        } else if (s.is(BlockTags.FIRE) || b instanceof HearthfireBlock || s.getFluidState().is(FluidTags.LAVA)) {
            t.warm(Tally.WARM_FIRE);
        } else if (t.sign.isEmpty() && s.is(BlockTags.ALL_SIGNS)) {
            t.sign = signText(at);
        }
    }

    /** One wall, floor or roof block, seen from the cell on side {@code from} of it. */
    private void wall(Tally t, Job j, BlockState w, BlockPos at, Direction from) {
        Block b = w.getBlock();
        Furniture f = Furniture.of(w);
        if (f == Furniture.TROPHY) {
            String boss = mountedTrophy(at);
            if (boss != null) j.trophies.add(boss);
        } else if (f != null) {
            t.furniture[f.ordinal()]++;
        }
        boolean glass = w.is(Tags.BlockItems.GLASS_BLOCKS.block()) || w.is(Tags.BlockItems.GLASS_PANES.block());
        boolean seeThrough = w.is(BlockTags.LEAVES) || w.is(BlockTags.FENCES) || w.is(BlockTags.BARS);
        t.face(Materials.of(w), b, seeThrough);
        if (from.getAxis().isHorizontal()) {
            if (glass) {
                t.windows++;
                t.windowDirs |= 1 << from.get2DDataValue();
            }
        } else if (from == Direction.UP) {
            t.ceiling++;
            if (glass) t.glassCeiling++;
        } else if (b instanceof net.minecraft.world.level.block.TrapDoorBlock) {
            t.hatch(at.asLong()); // a trapdoor in the floor: a shaft's hatch, maybe (the Mine head, W6)
        }
        if (w.is(BlockTags.STAIRS)) {
            t.detail(Tally.STAIRS); // (a seat is counted in read(), from the cell over it)
        } else if (w.is(BlockTags.SLABS)) {
            t.detail(Tally.SLAB);
        } else if (w.is(BlockTags.TRAPDOORS)) {
            t.detail(Tally.TRAPDOOR);
        } else if (w.is(BlockTags.FENCES) || w.is(BlockTags.WALLS)) {
            t.detail(Tally.POST);
            // a table: a post with a carpet, a pressure plate or a slab on it
            BlockState top = level.getBlockState(at.above());
            if (top.is(BlockTags.WOOL_CARPETS) || top.getBlock() instanceof BasePressurePlateBlock
                    || top.getBlock() instanceof SlabBlock && top.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) t.tables++;
        } else if (f == Furniture.BOOKSHELF) {
            t.detail(Tally.BOOKS);
        }
        if (b == Blocks.FURNACE || b == Blocks.SMOKER || b == Blocks.BLAST_FURNACE) {
            t.warm(lit(w) ? Tally.WARM_STOVE : Tally.WARM_EMBER);
        } else if (f == Furniture.FORGE) {
            t.warm(Tally.WARM_STOVE);
        } else if (w.is(HearthContent.HEARTH_ANCHOR.get()) || w.getFluidState().is(FluidTags.LAVA) || b == Blocks.MAGMA_BLOCK) {
            t.warm(Tally.WARM_FIRE);
        } else if (t.sign.isEmpty() && w.is(BlockTags.ALL_SIGNS)) {
            t.sign = signText(at);
        }
    }

    /**
     * (W6, PLAN 7.7) Under each hatch in the room's floor: how far a shaft goes down (air and what you climb), and whether
     * you can climb it (a ladder, scaffolding, vines, a chain or a rope for at least half its depth). The deepest that can be
     * climbed is the room's shaft. At most four hatches and 96 blocks each.
     */
    private int shafts(Tally t) {
        int cost = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < t.hatchCount; k++) {
            m.set(t.hatches[k]);
            int x = m.getX(), top = m.getY(), z = m.getZ();
            int depth = 0, climbs = 0;
            for (int y = top - 1; y >= Math.max(level.getMinY(), top - 96); y--) {
                m.set(x, y, z);
                cost++;
                BlockState s = level.getBlockState(m);
                boolean climb = climbable(s);
                if (!climb && !s.getCollisionShape(level, m).isEmpty()) break;
                if (s.getFluidState().is(FluidTags.LAVA)) break;
                depth++;
                if (climb) climbs++;
            }
            if (depth > t.shaft && climbs * 2 >= depth) {
                t.shaft = depth;
                t.shaftAt = t.hatches[k];
            }
        }
        return cost;
    }

    /** What a shaft is climbed by: ladders, scaffolding, vines, chains and the ropes that hang down a wall. */
    public static boolean climbable(BlockState s) {
        Block b = s.getBlock();
        return b instanceof net.minecraft.world.level.block.LadderBlock || b instanceof net.minecraft.world.level.block.ScaffoldingBlock
                || b instanceof net.minecraft.world.level.block.VineBlock || b instanceof ChainBlock
                || s.is(dev.awaken.body.GripRules.CLIMBS) || s.is(BlockTags.CLIMBABLE);
    }

    private int finishRoom(Job j) {
        Tally t = j.tally;
        int cost = 0;
        t.height = j.maxY - j.minY + 1;
        t.width = j.maxX - j.minX + 1;
        t.depth = j.maxZ - j.minZ + 1;
        AABB box = new AABB(j.minX, j.minY, j.minZ, j.maxX + 1, j.maxY + 1, j.maxZ + 1);
        for (ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (j.visited.contains(a.blockPosition().asLong())) t.furniture[Furniture.STAND.ordinal()]++;
        }
        // paintings and item frames hang in the wall but face into the room
        if (!level.getEntitiesOfClass(HangingEntity.class, box.inflate(1)).isEmpty()) t.detail(Tally.PAINTING);
        cost += 16;
        // the ground a few blocks outside the room, its lowest side (a tower's foot)
        int cxm = (j.minX + j.maxX) / 2, czm = (j.minZ + j.maxZ) / 2;
        int[][] probes = {{j.minX - GROUND_OUT, czm}, {j.maxX + GROUND_OUT, czm}, {cxm, j.minZ - GROUND_OUT}, {cxm, j.maxZ + GROUND_OUT}};
        int ground = Integer.MAX_VALUE;
        for (int[] p : probes) {
            if (!level.isLoaded(new BlockPos(p[0], j.minY, p[1]))) continue;
            ground = Math.min(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]));
        }
        t.ground = ground == Integer.MAX_VALUE ? Integer.MIN_VALUE : ground;
        t.trophies.addAll(j.trophies);
        cost += shafts(t);
        int[] counts = t.furniture.clone();
        counts[Furniture.SEAT.ordinal()] = t.seats;
        counts[Furniture.TABLE.ordinal()] = t.tables;
        counts[Furniture.TROPHY.ordinal()] = j.trophies.size();
        int[] facts = t.facts();
        EnumSet<RoomType> types = Room.classify(counts, facts);
        Reading reading = Reading.of(t, types);
        found.add(new Room(types, new BlockPos(j.minX, j.minY, j.minZ), new BlockPos(j.maxX, j.maxY, j.maxZ), j.visited, counts, facts,
                Set.copyOf(j.trophies), reading));
        familyTotals(t);
        indoors.addAll(j.visited);
        if (j.side) sideClosed = true;
        return cost;
    }

    /** Faces per material family across every room this scan (the home's Style). */
    private final int[] families = new int[Materials.FAMILIES];

    private void familyTotals(Tally t) {
        int[] f = t.families();
        for (int i = 0; i < f.length; i++) families[i] += f[i];
    }

    // ---- the result -------------------------------------------------------------------------------------------------

    private void finish() {
        phase = Phase.DONE;
        int stands = 0, animals = 0;
        AABB box = hearth.box();
        for (ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, box)) if (hearth.contains(a.blockPosition())) stands++;
        List<BlockPos> beasts = new ArrayList<>();
        for (Animal a : level.getEntitiesOfClass(Animal.class, box)) {
            if (a instanceof TamableAnimal || !hearth.contains(a.blockPosition())) continue;
            animals++;
            if (beasts.size() < 32) beasts.add(a.blockPosition());
        }
        animals = Math.min(animals, 8);
        // (W6) the land's work for the people who live here: fields and gates outside the rooms, where the beasts are
        List<BlockPos> fields = new ArrayList<>(), gates = new ArrayList<>();
        for (int i = 0; i < fieldCells.size() && fields.size() < 512; i++) {
            long c = fieldCells.getLong(i);
            if (!indoors.contains(BlockPos.offset(c, Direction.UP))) fields.add(BlockPos.of(c));
        }
        for (int i = 0; i < gateCells.size() && gates.size() < 16; i++) {
            long c = gateCells.getLong(i);
            boolean in = false;
            for (Direction d : Direction.Plane.HORIZONTAL) in |= indoors.contains(BlockPos.offset(c, d));
            if (!in) gates.add(BlockPos.of(c));
        }
        furniture[Furniture.STAND.ordinal()] = stands;
        int outdoorLights = 0;
        for (int i = 0; i < lights.size(); i++) if (!indoors.contains(lights.getLong(i))) outdoorLights++;
        Scan.Land land = new Scan.Land(farmland, animals, fences, walls, composters, flowers, outdoorLights);
        onDone.accept(new Scan(List.copyOf(found), List.copyOf(misses), furniture.clone(), land, families.clone(),
                new Scan.Yard(List.copyOf(fields), List.copyOf(gates), List.copyOf(beasts))));
    }

    private String mountedTrophy(BlockPos p) {
        return level.getBlockEntity(p) instanceof TrophyMountBlockEntity be ? be.trophyBoss() : null;
    }

    private String signText(BlockPos p) {
        if (!(level.getBlockEntity(p) instanceof SignBlockEntity sign)) return "";
        StringBuilder b = new StringBuilder();
        for (SignTextSlot slot : new SignTextSlot[]{SignTextSlot.FRONT, SignTextSlot.BACK}) {
            for (Component line : sign.getText(slot).getMessages(false)) {
                String s = line.getString().strip();
                if (s.isEmpty()) continue;
                if (!b.isEmpty()) b.append(' ');
                b.append(s);
            }
            if (!b.isEmpty()) break;
        }
        String out = b.toString();
        return out.length() > 40 ? out.substring(0, 40).strip() : out;
    }

    // ---- rules ------------------------------------------------------------------------------------------------------

    /** Things that make a room something. The scan only floods from these (and from seeds). */
    public static boolean isFurniture(BlockState s) {
        return Furniture.of(s) != null;
    }

    /** What a home has in it that isn't furniture: doors, lights, chests, signs, carpets, banners, the Hearth's fire. */
    static boolean isSeed(BlockState s) {
        Block b = s.getBlock();
        return s.is(BlockTags.DOORS) || givesLight(s) || s.is(Tags.BlockItems.CHESTS.block()) || s.is(BlockTags.ALL_SIGNS)
                || s.is(BlockTags.FLOWER_POTS) || s.is(BlockTags.WOOL_CARPETS) || s.is(BlockTags.BANNERS)
                || b instanceof HearthfireBlock || s.is(HearthContent.HEARTH_ANCHOR.get());
    }

    /** Lights a player places (the yard's lights at night count these). */
    static boolean givesLight(BlockState s) {
        Block b = s.getBlock();
        return b instanceof BaseTorchBlock || b instanceof LanternBlock || s.is(BlockTags.CANDLES) || s.is(BlockTags.CAMPFIRES);
    }

    private static boolean interesting(BlockState s) {
        return Furniture.of(s) != null || isSeed(s) || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.getBlock() instanceof ComposterBlock;
    }

    private static boolean lit(BlockState s) {
        return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
    }

    /**
     * Open sky = nothing above this cell that closes it. The heightmap updates with every block change (sky light lags
     * behind) and counts a glass roof as a roof; slabs and trapdoors aren't in it, so a short look upward finds those.
     */
    private boolean seesSky(BlockPos p) {
        int x = p.getX(), y = p.getY(), z = p.getZ();
        if (y < level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)) return false;
        int limit = Math.min(level.getMaxY(), y + ROOF_REACH);
        for (int yy = y + 1; yy <= limit; yy++) {
            skyReads++;
            if (!passable(above.set(x, yy, z))) return false;
        }
        return true;
    }

    /** Block reads the upward looks did since the last call (added to the budget). */
    private int skyReads;

    private int takeSkyReads() {
        int r = skyReads;
        skyReads = 0;
        return r;
    }

    /** Reused by {@link #seesSky} so the upward look never allocates. */
    private final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();

    /**
     * Open space a room is made of: air, furniture and anything that isn't a full block, except things that close a
     * gap (doors, trapdoors, fence gates, fences, walls, bars, glass panes, stairs and slabs).
     */
    private boolean passable(BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return true;
        if (closesGap(s)) return false;
        return !s.isCollisionShapeFullBlock(level, p);
    }

    /** Thin or partial blocks that still close a room. Public so the Hearth guide states the same rule. */
    public static boolean closesGap(BlockState s) {
        return s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.FENCES)
                || s.is(BlockTags.WALLS) || s.is(BlockTags.BARS) || s.is(Tags.BlockItems.GLASS_PANES.block())
                || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS);
    }
}
