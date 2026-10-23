package dev.awaken.hearth.room;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.block.ForgeBlock;
import dev.awaken.hearth.block.MeditationMatBlock;
import dev.awaken.hearth.block.RolePadBlock;
import dev.awaken.hearth.block.TrophyMountBlock;
import dev.awaken.hearth.block.TrophyMountBlockEntity;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * Finds rooms inside a Hearth's radius. Cheap and budgeted: a palette sweep skips every chunk section that cannot hold
 * room furniture, then a bounded flood fill from each piece of furniture finds the enclosed space around it. Work is
 * spread across ticks ({@link #step}) so a scan never costs more than a fraction of a millisecond per tick.
 */
public final class RoomScanner {
    /** A room is at most this many open cells and this wide; anything bigger is "outside". */
    public static final int MAX_CELLS = 4096;
    public static final int MAX_EXTENT = 32;

    private static final Predicate<BlockState> FURNITURE = RoomScanner::isFurniture;

    private enum Phase { SWEEP, FLOOD, DONE }

    private final ServerLevel level;
    private final HearthRecord hearth;
    private final Consumer<List<Room>> onDone;
    private Phase phase = Phase.SWEEP;
    private final int minCx, maxCx, minCz, maxCz, minSy, maxSy;
    private int cx, cz, sy;
    private final List<BlockPos> seeds = new ArrayList<>();
    private int seedIndex;
    private final LongSet claimed = new LongOpenHashSet();
    private final List<Room> found = new ArrayList<>();

    public RoomScanner(ServerLevel level, HearthRecord hearth, Consumer<List<Room>> onDone) {
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
            return 1;
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
                            if (isFurniture(s)) {
                                BlockPos p = new BlockPos(bx + x, by + y, bz + z);
                                if (hearth.contains(p)) seeds.add(p);
                            }
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

    // ---- phase 2: flood fill the enclosed space around each piece of furniture ----------------------------------------

    private int floodOne() {
        if (seedIndex >= seeds.size()) {
            phase = Phase.DONE;
            onDone.accept(List.copyOf(found));
            return 1;
        }
        BlockPos seed = seeds.get(seedIndex++);
        if (claimed.contains(seed.asLong())) return 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet visited = new LongOpenHashSet();
        if (passable(seed)) {
            queue.enqueue(seed.asLong());
            visited.add(seed.asLong());
        } else {
            for (Direction d : Direction.values()) {
                BlockPos n = seed.relative(d);
                if (level.isLoaded(n) && passable(n) && visited.add(n.asLong())) queue.enqueue(n.asLong());
            }
        }
        if (queue.isEmpty()) return 6;
        int cost = 0;
        boolean open = false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        while (!queue.isEmpty()) {
            long l = queue.dequeueLong();
            m.set(l);
            cost += 7;
            // Open sky = nothing solid anywhere above this cell. The heightmap updates with every block change (sky
            // light lags behind) and counts a glass roof as a roof.
            if (m.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, m.getX(), m.getZ())) {
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
            for (Direction d : Direction.values()) {
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
        if (open) return cost;
        found.add(build(visited, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
        return cost + visited.size();
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
            if (s.is(BlockTags.BEDS) && s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) beds++;
            else if (s.getBlock() instanceof MeditationMatBlock) mats++;
            else if (s.is(BlockTags.ANVIL)) anvils++;
            else if (s.getBlock() instanceof RolePadBlock) pads++;
            else if (s.getBlock() instanceof TrophyMountBlock && level.getBlockEntity(m) instanceof TrophyMountBlockEntity be && be.trophyBoss() != null) {
                trophies.add(be.trophyBoss());
            }
            if (s.is(HearthContent.MACHINES)) machines++;
            int x = m.getX(), y = m.getY(), z = m.getZ();
            // Full-block furniture (the Forge, most machines) forms the room's walls: look at the faces around each cell.
            for (Direction d : Direction.values()) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                long nl = m.asLong();
                if (cells.contains(nl) || !walls.add(nl)) continue;
                BlockState w = level.getBlockState(m);
                if (w.getBlock() instanceof ForgeBlock) forges++;
                if (w.is(HearthContent.MACHINES)) machines++;
                if (w.getBlock() instanceof TrophyMountBlock && level.getBlockEntity(m) instanceof TrophyMountBlockEntity be && be.trophyBoss() != null) {
                    trophies.add(be.trophyBoss());
                }
            }
        }
        int stands = 0;
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        for (ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (cells.contains(a.blockPosition().asLong())) stands++;
        }
        EnumSet<RoomType> types = Room.classify(beds, mats, anvils, forges, stands, trophies.size(), machines, pads);
        return new Room(types, min, max, cells, beds, mats, anvils, forges, stands, pads, machines, Set.copyOf(trophies));
    }

    // ---- rules ------------------------------------------------------------------------------------------------------

    /** Things that make a room something. The scan only floods from these. */
    public static boolean isFurniture(BlockState s) {
        if (s.isAir()) return false;
        var b = s.getBlock();
        return b instanceof MeditationMatBlock || b instanceof ForgeBlock || b instanceof RolePadBlock || b instanceof TrophyMountBlock
                || s.is(BlockTags.ANVIL) || (s.is(BlockTags.BEDS) && s.hasProperty(BlockStateProperties.BED_PART)
                && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) || s.is(HearthContent.MACHINES);
    }

    /** Open space a room is made of: air, furniture and anything that isn't a full block, except things that close a gap. */
    private boolean passable(BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return true;
        if (s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.FENCES)
                || s.is(BlockTags.WALLS) || s.is(BlockTags.BARS) || s.is(Tags.BlockItems.GLASS_PANES.block())) return false;
        return !s.isCollisionShapeFullBlock(level, p);
    }
}
