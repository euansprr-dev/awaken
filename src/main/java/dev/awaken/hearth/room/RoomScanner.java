package dev.awaken.hearth.room;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.block.ForgeBlock;
import dev.awaken.hearth.block.TrophyMountBlock;
import dev.awaken.hearth.block.TrophyMountBlockEntity;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Finds rooms inside a Hearth's radius. Cheap and budgeted: a palette sweep skips every chunk section that cannot hold
 * room furniture, then a bounded flood fill from each piece of furniture finds the enclosed space around it. Work is
 * spread across ticks ({@link #step}) so a scan never costs more than a fraction of a millisecond per tick.
 *
 * <p>When a flood leaks, a second bounded fill of the covered space around the furniture says why ({@link Miss}): it
 * sees the sky, a hole in the roof, a gap in a wall, or it is too big. Furniture in the same leaky space shares that
 * answer without another fill.
 */
public final class RoomScanner {
    /** A room is at most this many open cells and this wide; anything bigger is "outside". */
    public static final int MAX_CELLS = 4096;
    public static final int MAX_EXTENT = 32;
    /** Explanations per scan, and per kind of furniture (each costs one more bounded fill; village beds can't use them up). */
    public static final int MAX_MISSES = 24, MAX_MISSES_PER_KIND = 4;
    /** How far above the heightmap a slab or trapdoor still counts as a roof. */
    private static final int ROOF_REACH = 24;

    private static final Predicate<BlockState> FURNITURE = RoomScanner::isFurniture;
    private static final Direction[] DIRS = Direction.values();

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
    /** Cells of leaky spaces, mapped to the miss that explains them. */
    private final Long2IntOpenHashMap missOf = new Long2IntOpenHashMap();
    /** "miss index × furniture" pairs already reported, so ten beds in one leaky barn give one line per kind. */
    private final Set<Long> reported = new HashSet<>();
    private final List<Room> found = new ArrayList<>();
    private final List<Miss> misses = new ArrayList<>();
    private final int[] furniture = new int[Furniture.COUNT];
    private final int[] diagnosed = new int[Furniture.COUNT];
    private int fills;

    public RoomScanner(ServerLevel level, HearthRecord hearth, Consumer<Scan> onDone) {
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
                case FLOOD -> floodOne();
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
            if (!section.hasOnlyAir() && section.maybeHas(FURNITURE)) {
                cost += 4096;
                int bx = SectionPos.sectionToBlockCoord(cx), by = SectionPos.sectionToBlockCoord(sy), bz = SectionPos.sectionToBlockCoord(cz);
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState s = section.getBlockState(x, y, z);
                            Furniture f = Furniture.of(s);
                            if (f == null) continue;
                            BlockPos p = new BlockPos(bx + x, by + y, bz + z);
                            if (!hearth.contains(p)) continue;
                            seeds.add(p);
                            seedKinds.add(f.ordinal());
                            if (f != Furniture.TROPHY || mountedTrophy(p) != null) furniture[f.ordinal()]++;
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

    /** Floods (and explains) the furniture nearest the Anchor first: that is most likely the owner's own. */
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

    // ---- phase 2: flood fill the enclosed space around each piece of furniture ----------------------------------------

    private int floodOne() {
        if (seedIndex >= seeds.size()) {
            finish();
            return 1;
        }
        int si = seedIndex++;
        BlockPos seed = seeds.get(si);
        Furniture kind = Furniture.values()[seedKinds.getInt(si)];
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet visited = new LongOpenHashSet();
        if (passable(seed)) {
            if (claimed.contains(seed.asLong())) {
                inherit(kind, seed, seed.asLong());
                return 1;
            }
            queue.enqueue(seed.asLong());
            visited.add(seed.asLong());
        } else {
            // Full-block furniture (a Forge, a machine) floods from the open cells around it. Cells another flood already
            // covered are that room (counted from its walls) or that leak (explained once): skip them.
            for (Direction d : DIRS) {
                BlockPos n = seed.relative(d);
                if (!level.isLoaded(n) || !passable(n)) continue;
                long nl = n.asLong();
                if (claimed.contains(nl)) {
                    inherit(kind, seed, nl);
                    continue;
                }
                if (visited.add(nl)) queue.enqueue(nl);
            }
        }
        if (queue.isEmpty()) return 6;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cost = 0;
        boolean open = false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        while (!queue.isEmpty()) {
            long l = queue.dequeueLong();
            m.set(l);
            cost += 7;
            if (seesSky(m)) {
                open = true;
                break;
            }
            minX = Math.min(minX, m.getX()); maxX = Math.max(maxX, m.getX());
            minY = Math.min(minY, m.getY()); maxY = Math.max(maxY, m.getY());
            minZ = Math.min(minZ, m.getZ()); maxZ = Math.max(maxZ, m.getZ());
            if (visited.size() > MAX_CELLS || maxX - minX > MAX_EXTENT || maxZ - minZ > MAX_EXTENT || maxY - minY > MAX_EXTENT) {
                open = true;
                break;
            }
            int x = m.getX(), y = m.getY(), z = m.getZ();
            for (Direction d : DIRS) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                long nl = m.asLong();
                if (visited.contains(nl)) continue;
                if (!level.isLoaded(m)) {
                    open = true;
                    break;
                }
                if (passable(m)) {
                    visited.add(nl);
                    queue.enqueue(nl);
                }
            }
            if (open) break;
        }
        claimed.addAll(visited);
        if (open) return cost + diagnose(kind, seed, visited) + takeSkyReads();
        found.add(build(visited, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
        return cost + visited.size() + takeSkyReads();
    }

    /** Furniture whose space another flood already found leaky gets that flood's answer. */
    private void inherit(Furniture kind, BlockPos seed, long cell) {
        int mi = missOf.get(cell);
        if (mi < 0 || !reported.add((long) mi * Furniture.COUNT + kind.ordinal())) return;
        Miss base = misses.get(mi);
        misses.add(new Miss(kind, seed, base.leak(), base.at()));
    }

    // ---- why a leaky space is not a room -----------------------------------------------------------------------------

    /**
     * Fills the covered space around the furniture, treating open sky as a wall: a covered cell with sky right above it
     * means no roof; sky beside the space inside its footprint is a hole in the roof; sky beside it at the edge is a gap
     * in a wall. Bounded like the room fill. Returns its cost.
     */
    private int diagnose(Furniture kind, BlockPos seed, LongOpenHashSet firstFill) {
        if (fills >= MAX_MISSES || diagnosed[kind.ordinal()] >= MAX_MISSES_PER_KIND) return 1;
        fills++;
        diagnosed[kind.ordinal()]++;
        List<BlockPos> starts = new ArrayList<>();
        if (passable(seed)) starts.add(seed);
        else for (Direction d : DIRS) {
            BlockPos n = seed.relative(d);
            if (level.isLoaded(n) && passable(n)) starts.add(n);
        }
        int cost = 0;
        Miss.Leak leak = null;
        BlockPos at = null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (BlockPos s : starts) {
            cost += 4;
            if (seesSky(s)) {
                leak = Miss.Leak.SKY;
                at = s.immutable();
                break;
            }
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        if (leak == null) {
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            for (BlockPos s : starts) if (seen.add(s.asLong())) queue.enqueue(s.asLong());
            long up = Long.MIN_VALUE;
            LongArrayList sideFrom = new LongArrayList(), sideTo = new LongArrayList();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            boolean big = false;
            while (!queue.isEmpty()) {
                long l = queue.dequeueLong();
                m.set(l);
                cost += 7;
                int x = m.getX(), y = m.getY(), z = m.getZ();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                if (seen.size() > MAX_CELLS || maxX - minX > MAX_EXTENT || maxZ - minZ > MAX_EXTENT || maxY - minY > MAX_EXTENT) {
                    big = true;
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
            if (big) {
                leak = Miss.Leak.TOO_BIG;
                at = seed;
            } else if (up != Long.MIN_VALUE) {
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
                if (leak == null) {
                    leak = Miss.Leak.OPENING;
                    at = sideFrom.isEmpty() ? seed : BlockPos.of(sideFrom.getLong(0));
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
        return cost;
    }

    // ---- the result -------------------------------------------------------------------------------------------------

    private void finish() {
        phase = Phase.DONE;
        int stands = 0;
        for (ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, hearth.box())) if (hearth.contains(a.blockPosition())) stands++;
        furniture[Furniture.STAND.ordinal()] = stands;
        onDone.accept(new Scan(List.copyOf(found), List.copyOf(misses), furniture.clone()));
    }

    private Room build(LongOpenHashSet cells, BlockPos min, BlockPos max) {
        int beds = 0, mats = 0, anvils = 0, forges = 0, pads = 0, machines = 0;
        Set<String> trophies = new HashSet<>();
        LongOpenHashSet walls = new LongOpenHashSet();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        var it = cells.iterator();
        while (it.hasNext()) {
            long l = it.nextLong();
            m.set(l);
            BlockState s = level.getBlockState(m);
            Furniture f = Furniture.of(s);
            if (f == Furniture.BED) beds++;
            else if (f == Furniture.MAT) mats++;
            else if (f == Furniture.ANVIL) anvils++;
            else if (f == Furniture.PAD) pads++;
            else if (f == Furniture.TROPHY) {
                String boss = mountedTrophy(m);
                if (boss != null) trophies.add(boss);
            }
            if (s.is(HearthContent.MACHINES)) machines++;
            int x = m.getX(), y = m.getY(), z = m.getZ();
            // Full-block furniture (the Forge, most machines) forms the room's walls: look at the faces around each cell.
            for (Direction d : DIRS) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                long nl = m.asLong();
                if (cells.contains(nl) || !walls.add(nl)) continue;
                BlockState w = level.getBlockState(m);
                if (w.getBlock() instanceof ForgeBlock) forges++;
                if (w.is(HearthContent.MACHINES)) machines++;
                if (w.getBlock() instanceof TrophyMountBlock) {
                    String boss = mountedTrophy(m);
                    if (boss != null) trophies.add(boss);
                }
            }
        }
        int stands = 0;
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        for (ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (cells.contains(a.blockPosition().asLong())) stands++;
        }
        var types = Room.classify(beds, mats, anvils, forges, stands, trophies.size(), machines, pads);
        return new Room(types, min, max, cells, beds, mats, anvils, forges, stands, pads, machines, Set.copyOf(trophies));
    }

    private String mountedTrophy(BlockPos p) {
        return level.getBlockEntity(p) instanceof TrophyMountBlockEntity be ? be.trophyBoss() : null;
    }

    // ---- rules ------------------------------------------------------------------------------------------------------

    /** Things that make a room something. The scan only floods from these. */
    public static boolean isFurniture(BlockState s) {
        return Furniture.of(s) != null;
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
