package dev.awaken.finds;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a kind builds at one site (SPEC §58.3), relative to the site's heart: blocks, named parts, what lives there and
 * where its tell shows. A kind's {@link Kind.Maker} writes in its own unturned coordinates ({@code lx} east, {@code lz}
 * south, the kind facing south); the scene turns them by the site's quarter turn and knows the ground under every
 * column from the generator's own noise, so a build follows the land and every position is the same in any chunk and
 * later on the server. Heights: {@code dy} 0 is the heart's ground block; {@link #on} stands a block on a column's own
 * ground.
 */
public final class Scene {
    /** What a scene asks to live at it: spawned once, the first time the site wakes. */
    public record Spawn(EntityType<?> type, BlockPos rel, int flags, String role) {
        public static final int STAY = 1, BABY = 2, LIE = 4, NO_AI = 8, QUIET = 16;
    }

    public final Site site;
    public final RandomSource rnd;
    private final Terrain terrain;
    final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
    final Map<String, List<BlockPos>> parts = new LinkedHashMap<>();
    final List<Spawn> spawns = new ArrayList<>();
    private final Long2IntOpenHashMap ground = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap natural = new Long2IntOpenHashMap();
    /** Water the scene wants running (scene offsets): the feature gives each a fluid tick once it is written. */
    public final List<BlockPos> flows = new ArrayList<>();
    int clear;
    @Nullable BlockPos tell;
    /** Columns whose ground the scene set itself (turned offsets): the feature fills or cuts the land to them even where nothing is built. */
    public final it.unimi.dsi.fastutil.longs.LongOpenHashSet shaped = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    /** Where steps leave a terrace, in the kind's own offsets: {x, z, step x, step z}. The first is the way in (SPEC §64.3). */
    public final java.util.List<int[]> ways = new java.util.ArrayList<>();
    /** Where a camera stands to look at the place, for the tours: {x, y, z (turned offsets), yaw, pitch}. */
    final List<int[]> cams = new ArrayList<>();
    private final Rotation turn;

    Scene(Site site, Terrain terrain) {
        this.site = site;
        this.terrain = terrain;
        this.rnd = RandomSource.create(site.seed() ^ 0x5DEECE66DL);
        this.turn = Rotation.values()[site.rot() & 3];
        ground.defaultReturnValue(Integer.MIN_VALUE);
        natural.defaultReturnValue(Integer.MIN_VALUE);
    }

    // ---- coordinates ------------------------------------------------------------------------------------------------

    private int wx(int lx, int lz) {
        return switch (site.rot() & 3) { case 1 -> -lz; case 2 -> -lx; case 3 -> lz; default -> lx; };
    }

    private int wz(int lx, int lz) {
        return switch (site.rot() & 3) { case 1 -> lx; case 2 -> -lz; case 3 -> -lx; default -> lz; };
    }

    /** A kind's own direction, turned with the site. */
    public Direction dir(Direction local) {
        return turn.rotate(local);
    }

    /** The scene's own offset (already turned) for a kind's position. */
    public BlockPos rel(int lx, int dy, int lz) {
        return new BlockPos(wx(lx, lz), dy, wz(lx, lz));
    }

    /** The ground block's height at a column, relative to the heart's (0 at the heart). Solid ground: under water it's the bed. */
    public int g(int lx, int lz) {
        int dx = wx(lx, lz), dz = wz(lx, lz);
        return ask(ground, BlockPos.asLong(dx, 0, dz), dx, dz);
    }

    /** The land's own ground at a column, whatever the scene has shaped there since (relative to the heart's). */
    public int nat(int lx, int lz) {
        int dx = wx(lx, lz), dz = wz(lx, lz);
        return ask(natural, BlockPos.asLong(dx, 0, dz), dx, dz);
    }

    /**
     * A column's height from one of the two maps, which fill as they are asked. A scene is read by every worldgen
     * thread that writes a chunk of it and by the server, so the maps are only touched under their lock: read bare,
     * a column's ground came back wrong now and then while another thread grew the map, and the feature dug or left
     * a one-block pit in a lawn (the first worlds made without a client, SPEC §64.9). The land is asked outside it.
     */
    private int ask(Long2IntOpenHashMap map, long k, int dx, int dz) {
        int v;
        synchronized (map) {
            v = map.get(k);
        }
        if (v != Integer.MIN_VALUE) return v;
        int land = terrain.floor(site.x() + dx, site.z() + dz) - site.y();
        synchronized (map) {
            v = map.get(k);
            if (v == Integer.MIN_VALUE) map.put(k, v = land);
        }
        return v;
    }

    /** Water at this block is to run once the world has it (a channel that fills, a fall). */
    public Scene flow(int lx, int dy, int lz) {
        flows.add(rel(lx, dy, lz));
        return this;
    }

    /** The land the site stands in: what wears a monument (sand, snow, vines) goes by this. */
    public dev.awaken.living.region.RegionMap.Group land() {
        return site.group();
    }

    /** The ground at an already turned offset, for the feature. */
    public int groundAt(int dx, int dz) {
        return ask(ground, BlockPos.asLong(dx, 0, dz), dx, dz);
    }

    /** Sets a column's ground: the land there is filled or cut to this height (relative to the heart's ground). */
    public Scene ground(int lx, int lz, int dy) {
        long k = BlockPos.asLong(wx(lx, lz), 0, wz(lx, lz));
        synchronized (ground) {
            ground.put(k, dy);
        }
        shaped.add(k);
        return this;
    }

    /**
     * Levels a disc to the heart's ground and blends its edge back into the land over {@code blend} blocks (SPEC §59.3):
     * what a place stands on. Call it first; after it {@code g} is 0 across the disc.
     */
    public Scene level(int radius, int blend) {
        return level(0, 0, radius, blend, 0);
    }

    /** The same round another point and to another height. */
    public Scene level(int cx, int cz, int radius, int blend, int dy) {
        int r = radius + blend;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r) continue;
                if (d <= radius) ground(cx + x, cz + z, dy);
                else if (!shaped.contains(BlockPos.asLong(wx(cx + x, cz + z), 0, wz(cx + x, cz + z)))) { // a blend never undoes ground levelled before it
                    int nat = g(cx + x, cz + z);
                    double t = (d - radius) / blend;
                    t = t * t * (3 - 2 * t);
                    ground(cx + x, cz + z, (int) Math.round(dy + (nat - dy) * t));
                }
            }
        return this;
    }

    /** A place for a tour's camera, in the kind's own coordinates: yaw 0 looks south (+z), 90 west, 180 north, -90 east. */
    public Scene cam(int lx, int dy, int lz, int yaw, int pitch) {
        cams.add(new int[] {wx(lx, lz), dy, wz(lx, lz), yaw + 90 * (site.rot() & 3), pitch});
        return this;
    }

    public List<int[]> cams() {
        return cams;
    }

    /** Whether a column is under water, and how deep. 0 is dry. */
    public int depth(int lx, int lz) {
        return Math.max(0, terrain.seaLevel() - 1 - (site.y() + g(lx, lz)));
    }

    /** The water's surface relative to the heart's ground (the block of water itself). */
    public int sea() {
        return terrain.seaLevel() - 1 - site.y();
    }

    // ---- blocks -----------------------------------------------------------------------------------------------------

    /** A block at a height relative to the heart's ground. */
    public Scene put(int lx, int dy, int lz, BlockState s) {
        if (lx == 0 && lz == 0 && dy == -2) return this; // the heart's own cell: whatever a big scene wanted there, the heart stands
        if (site.kind().size() == Kind.Size.PLACE && (Math.abs(lx) > site.kind().reach() || Math.abs(lz) > site.kind().reach())) return this; // a place's dressing never strays past what its chunks write
        blocks.put(BlockPos.asLong(wx(lx, lz), dy, wz(lx, lz)), s.rotate(turn));
        return this;
    }

    /** A block standing on a column's own ground. */
    public Scene on(int lx, int lz, BlockState s) {
        return put(lx, g(lx, lz) + 1, lz, s);
    }

    /** A block {@code k} above what stands on a column's ground (0 is {@link #on}). */
    public Scene up(int lx, int lz, int k, BlockState s) {
        return put(lx, g(lx, lz) + 1 + k, lz, s);
    }

    /** Replaces a column's ground block itself. */
    public Scene floor(int lx, int lz, BlockState s) {
        return put(lx, g(lx, lz), lz, s);
    }

    /** A stack on a column's ground, {@code k0} to {@code k1} above it. */
    public Scene column(int lx, int lz, int k0, int k1, BlockState s) {
        for (int k = k0; k <= k1; k++) up(lx, lz, k, s);
        return this;
    }

    /** Cuts a block out (the feature writes air there). */
    public Scene air(int lx, int dy, int lz) {
        return put(lx, dy, lz, Blocks.AIR.defaultBlockState());
    }

    public boolean taken(int lx, int dy, int lz) {
        return blocks.containsKey(BlockPos.asLong(wx(lx, lz), dy, wz(lx, lz)));
    }

    public @Nullable BlockState get(int lx, int dy, int lz) {
        return blocks.get(BlockPos.asLong(wx(lx, lz), dy, wz(lx, lz)));
    }

    // ---- parts, life, tell ------------------------------------------------------------------------------------------

    /** Names a position a script works with; returns its index among parts of that name. */
    public int part(String name, int lx, int dy, int lz) {
        List<BlockPos> l = parts.computeIfAbsent(name, n -> new ArrayList<>());
        l.add(rel(lx, dy, lz));
        return l.size() - 1;
    }

    /** A part {@code k} above a column's ground. */
    public int partOn(String name, int lx, int lz, int k) {
        return part(name, lx, g(lx, lz) + 1 + k, lz);
    }

    /** A block that is also a part. */
    public int partOn(String name, int lx, int lz, int k, BlockState s) {
        up(lx, lz, k, s);
        return partOn(name, lx, lz, k);
    }

    public Scene spawn(EntityType<?> type, int lx, int lz, int flags, String role) {
        spawns.add(new Spawn(type, rel(lx, g(lx, lz) + 1, lz), flags, role));
        return this;
    }

    /** Life standing {@code k} above a column's ground (on a branch, a ledge). */
    public Scene spawnUp(EntityType<?> type, int lx, int lz, int k, int flags, String role) {
        spawns.add(new Spawn(type, rel(lx, g(lx, lz) + 1 + k, lz), flags, role));
        return this;
    }

    /** Where the tell shows, above a column's ground. */
    public Scene tell(int lx, int lz, int k) {
        tell = rel(lx, g(lx, lz) + 1 + k, lz);
        return this;
    }

    /** Trees, leaves and plants above the ground go within this many blocks of the heart. */
    public Scene clear(int radius) {
        clear = Math.max(clear, radius);
        return this;
    }

    // ---- read by the engine -----------------------------------------------------------------------------------------

    public List<BlockPos> part(String name) {
        return parts.getOrDefault(name, List.of());
    }

    public Map<String, List<BlockPos>> parts() {
        return parts;
    }

    public Long2ObjectOpenHashMap<BlockState> blocks() {
        return blocks;
    }

    public List<Spawn> spawns() {
        return spawns;
    }

    /** The name and index of the part at a scene offset, or null. */
    public @Nullable PartRef partAt(BlockPos rel) {
        for (var e : parts.entrySet()) {
            int i = e.getValue().indexOf(rel);
            if (i >= 0) return new PartRef(e.getKey(), i);
        }
        return null;
    }

    /**
     * The scene's trees become the land's (SPEC §64.3): every leaf within six of one of the scene's own logs, counted
     * through leaves the way the game counts, is a living leaf with its distance set, not a kept one. A road or a
     * village that crosses a monument's ground fells the land's trees in its way and sweeps what that leaves hanging,
     * but it spares kept leaves as somebody's hedge: laid over one of our planted trees it took the trunk and left
     * the whole crown in the air (the Mooring Mast and the Sealed Shaft, in the first worlds checked block by block).
     * Leaves no log reaches stay kept (a hedge, a bush), since living ones would wither.
     */
    void liveLeaves() {
        Long2IntOpenHashMap dist = new Long2IntOpenHashMap();
        dist.defaultReturnValue(99);
        it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue q = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
        for (var e : blocks.long2ObjectEntrySet())
            if (e.getValue().is(net.minecraft.tags.BlockTags.LOGS)) {
                dist.put(e.getLongKey(), 0);
                q.enqueue(e.getLongKey());
            }
        while (!q.isEmpty()) {
            long k = q.dequeueLong();
            int d = dist.get(k);
            if (d >= 6) continue;
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            for (Direction dir : Direction.values()) {
                long n = BlockPos.asLong(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                BlockState b = blocks.get(n);
                if (b == null || !(b.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) || dist.get(n) <= d + 1) continue;
                dist.put(n, d + 1);
                q.enqueue(n);
            }
        }
        for (var e : dist.long2IntEntrySet()) {
            int d = e.getIntValue();
            if (d == 0) continue;
            BlockState b = blocks.get(e.getLongKey());
            if (b.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT) && b.hasProperty(net.minecraft.world.level.block.LeavesBlock.DISTANCE))
                blocks.put(e.getLongKey(), b.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false).setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, d));
        }
    }

    public record PartRef(String name, int index) {}

    private volatile int[] box;

    /** The box round everything the scene builds: {minX, minY, minZ, maxX, maxY, maxZ}, in scene offsets. */
    public int[] box() {
        if (box == null) {
            int[] b = {0, -2, 0, 0, 2, 0};
            for (long k : blocks.keySet()) grow(b, BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
            for (List<BlockPos> l : parts.values()) for (BlockPos p : l) grow(b, p.getX(), p.getY(), p.getZ());
            box = b;
        }
        return box;
    }

    private static void grow(int[] b, int x, int y, int z) {
        b[0] = Math.min(b[0], x);
        b[1] = Math.min(b[1], y);
        b[2] = Math.min(b[2], z);
        b[3] = Math.max(b[3], x);
        b[4] = Math.max(b[4], y);
        b[5] = Math.max(b[5], z);
    }

    /** Whether a scene offset is inside the box, with a margin. */
    public boolean inBox(BlockPos rel, int margin) {
        int[] b = box();
        return rel.getX() >= b[0] - margin && rel.getX() <= b[3] + margin && rel.getY() >= b[1] - margin && rel.getY() <= b[4] + margin
                && rel.getZ() >= b[2] - margin && rel.getZ() <= b[5] + margin;
    }

    /** Whether the scene itself put something (not air) at an offset. */
    public boolean built(BlockPos rel) {
        BlockState s = blocks.get(BlockPos.asLong(rel.getX(), rel.getY(), rel.getZ()));
        return s != null && !s.isAir();
    }
}
