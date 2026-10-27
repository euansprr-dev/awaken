package dev.awaken.living.wonder;

import dev.awaken.living.combat.Forms;
import dev.awaken.living.region.RegionMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The wonders (SPEC §21.11, decision 27, Living Wilds Phase F): one place in each region worth walking into dangerous land to see. A region's wonder is a
 * pure function of the world's seed and the region (its kind by the rank of the land, its site on level, dry ground a way from the region's heart, its plan
 * of blocks from a seed), so every chunk can write its own share when it is made, with nothing saved and nothing asked of its neighbours. The
 * wonder is warded (no hostile spawns within {@link #WARD} blocks) and finding it is a discovery.
 */
public final class Wonders {
    private Wonders() {}

    /** No hostile creature spawns this close to a wonder's heart: it can be looked at in peace; getting there is the walk. */
    public static final int WARD = 44;
    /** No wonder's blocks go further than this from its heart. */
    public static final int REACH = 38;

    public enum Kind {
        BELL_TREE("bell_tree", "The Bell Tree", "A tree grown round a bell, older than the roads. It still rings.", 0xD9C27A, 0, 1, 10),
        BOAR_THRONE("boar_throne", "The Boar Throne", "A seat of bone and tusks, and nothing sitting in it.", 0xE4D063, 1, 2, 9),
        STATUE_FIELD("statue_field", "The Statue Field", "Stone people, all looking the same way.", 0xA9B4C0, 1, 3, 31),
        WEB_CATHEDRAL("web_cathedral", "The Web Cathedral", "Silk strung between pillars, high enough to walk under.", 0xF29A48, 2, 3, 12),
        GRAVEFIELD("gravefield", "The Gravefield of the Revenants", "Rows of headstones round a black house. Some of the graves are open.", 0xB07CFF, 4, 4, 16),
        WEEPING_STONES("weeping_stones", "The Weeping Stones", "A ring of standing stones, each one crying.", 0x8C7BFF, 5, 5, 14);

        public final String id, display, line;
        public final int colour, rankFrom, rankTo, clear;

        Kind(String id, String display, String line, int colour, int from, int to, int clear) {
            this.clear = clear;
            this.id = id;
            this.display = display;
            this.line = line;
            this.colour = colour;
            this.rankFrom = from;
            this.rankTo = to;
        }

        public static @Nullable Kind byId(String id) {
            for (Kind k : values()) if (k.id.equals(id)) return k;
            return null;
        }
    }

    /** Where the ground is, for any column (the chunk generator's own noise: the same answer in every chunk and from the server later). */
    public interface Terrain {
        /** The y of the ground block at (x, z). */
        int ground(int x, int z);

        int seaLevel();
    }

    /** A wonder: its kind and the region it belongs to, its heart and the ground level there, and its plan (blocks relative to the heart, at the ground block's level). */
    public record Site(Kind kind, long region, String regionName, int x, int y, int z, Plan plan) {
        public BlockPos heart() {
            return new BlockPos(x, y, z);
        }

        public String key() {
            return kind.id + "@" + x + "," + z;
        }
    }

    /** Blocks relative to a wonder's heart: (dx, dy, dz) with dy 0 the ground block's level. */
    public static final class Plan {
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();

        void set(int dx, int dy, int dz, BlockState s) {
            if (Math.abs(dx) <= REACH && Math.abs(dz) <= REACH && dy >= 0 && dy < 64) blocks.put(BlockPos.asLong(dx, dy, dz), s);
        }

        public Long2ObjectOpenHashMap<BlockState> blocks() {
            return blocks;
        }
    }

    private static final Site NONE = new Site(Kind.BELL_TREE, -1, "", 0, 0, 0, new Plan());
    private static final ConcurrentHashMap<Long, Site> CACHE = new ConcurrentHashMap<>();

    public static void reset() {
        CACHE.clear();
    }

    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    /** This region's wonder, or null (the Commons has none; steep, wet or crowded ground finds no place). */
    public static @Nullable Site siteOf(long seed, RegionMap map, RegionMap.Region r, Terrain t) {
        if (r.commons()) return null;
        Site s = CACHE.computeIfAbsent(mix(seed, r.id()), k -> make(seed, map, r, t));
        return s == NONE ? null : s;
    }

    private static Site make(long seed, RegionMap map, RegionMap.Region r, Terrain t) {
        int rank = RegionMap.rankOf(map.levelAt(r.heartX(), r.heartZ()));
        List<Kind> fits = new ArrayList<>();
        for (Kind k : Kind.values()) if (rank >= k.rankFrom && rank <= k.rankTo) fits.add(k);
        if (fits.isEmpty()) return NONE;
        long h = mix(seed, r.id());
        Kind kind = fits.get((int) Math.floorMod(h >>> 20, (long) fits.size()));
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = ((h >>> 8) & 0xFFFF) / 65536.0 * Math.PI * 2 + attempt * 1.9;
            double dist = 70 + ((h >>> 28) & 0x7F) + attempt * 10;
            int x = r.heartX() + (int) Math.round(Math.cos(angle) * dist), z = r.heartZ() + (int) Math.round(Math.sin(angle) * dist);
            if (map.regionAt(x, z).id() != r.id()) continue;
            int[] hs = {t.ground(x, z), t.ground(x + 12, z), t.ground(x - 12, z), t.ground(x, z + 12), t.ground(x, z - 12)};
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int v : hs) {
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
            if (lo <= t.seaLevel() + 1 || hi - lo > 8) continue;
            int[] sorted = hs.clone();
            java.util.Arrays.sort(sorted);
            int y = sorted[2];
            Plan plan = new Plan();
            RandomSource rnd = RandomSource.create(mix(h, kind.ordinal()));
            build(kind, plan, rnd, family(map.landOf(r).group()));
            return new Site(kind, r.id(), r.name(), x, y, z, plan);
        }
        return NONE;
    }

    /** The materials of the land round (the region's group: cold, hot or green). */
    private static int family(RegionMap.Group g) {
        return g == RegionMap.Group.SNOW || g == RegionMap.Group.MOUNTAIN ? Forms.FROST : g == RegionMap.Group.DESERT || g == RegionMap.Group.BADLANDS ? Forms.SUN : Forms.GREEN;
    }

    /** Wonders within {@code radius} of the box (x0, z0)-(x1, z1): the regions at nine points across it and their sites. */
    public static List<Site> near(long seed, RegionMap map, Terrain t, int x0, int z0, int x1, int z1, int radius) {
        Set<Long> seen = new HashSet<>();
        List<Site> out = new ArrayList<>(2);
        int ex0 = x0 - radius, ez0 = z0 - radius, ex1 = x1 + radius, ez1 = z1 + radius;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                RegionMap.Region r = map.regionAt(ex0 + (ex1 - ex0) * i / 2, ez0 + (ez1 - ez0) * j / 2);
                if (!seen.add(r.id())) continue;
                Site s = siteOf(seed, map, r, t);
                if (s != null && s.x() >= ex0 && s.x() <= ex1 && s.z() >= ez0 && s.z() <= ez1) out.add(s);
            }
        return out;
    }

    // ---- the wonders ---------------------------------------------------------------------------------------------------------------------

    private static BlockState s(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState();
    }

    private static BlockState axis(net.minecraft.world.level.block.Block b, Direction.Axis a) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, a);
    }

    /** A wonder's plan on its own (for tests and previews). */
    public static Plan planOf(Kind kind, long seed, int fam) {
        Plan p = new Plan();
        build(kind, p, RandomSource.create(seed), fam);
        return p;
    }

    static void build(Kind kind, Plan p, RandomSource rnd, int fam) {
        BlockState plinth = s(Blocks.CHISELED_STONE_BRICKS);
        p.set(0, 0, 0, plinth);
        switch (kind) {
            case BELL_TREE -> {
                BlockState log = s(fam == Forms.SUN ? Blocks.ACACIA_LOG : fam == Forms.FROST ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG);
                BlockState leaf = (fam == Forms.SUN ? Blocks.ACACIA_LEAVES : fam == Forms.FROST ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
                for (int y = 1; y <= 15; y++) {
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (y <= 6 && !(dx != 0 && dz != 0 && y > 3)) p.set(dx, y, dz, log);
                    if (y > 6) p.set(0, y, 0, log);
                }
                for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) { p.set(d[0], 1, d[1], log); p.set(d[0] * 2, 1, d[1] * 2, axis(log.getBlock(), d[0] != 0 ? Direction.Axis.X : Direction.Axis.Z)); }
                for (int d = 1; d <= 6; d++) { // four limbs
                    p.set(d, 11, 0, axis(log.getBlock(), Direction.Axis.X));
                    p.set(-d, 12, 0, axis(log.getBlock(), Direction.Axis.X));
                    p.set(0, 12, d, axis(log.getBlock(), Direction.Axis.Z));
                    p.set(0, 11, -d, axis(log.getBlock(), Direction.Axis.Z));
                }
                for (int dx = -8; dx <= 8; dx++) for (int dy = 8; dy <= 20; dy++) for (int dz = -8; dz <= 8; dz++) {
                    double d = Math.sqrt(dx * dx + (dy - 14) * (dy - 14) * 1.6 + dz * dz);
                    if (d < 7.2 - rnd.nextFloat() * 1.6 && p.blocks.get(BlockPos.asLong(dx, dy, dz)) == null) p.set(dx, dy, dz, leaf);
                }
                // the bell hangs from the east limb, a lantern from the west
                p.set(5, 11, 0, axis(log.getBlock(), Direction.Axis.X));
                p.set(5, 10, 0, s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, Direction.NORTH));
                p.set(-5, 11, 0, s(Blocks.IRON_CHAIN));
                p.set(-5, 10, 0, s(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) + Math.abs(dz) == 4) p.set(dx, 0, dz, s(Blocks.MOSSY_COBBLESTONE));
            }
            case BOAR_THRONE -> {
                BlockState bone = s(Blocks.BONE_BLOCK);
                for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= 6.5) p.set(dx, 0, dz, s(d < 3 ? Blocks.COARSE_DIRT : Blocks.PODZOL));
                    if (d <= 3.5) p.set(dx, 1, dz, s(Blocks.COARSE_DIRT));
                }
                for (int dx = -2; dx <= 2; dx++) for (int dy = 1; dy <= 7; dy++) p.set(dx, dy, 3, bone);
                for (int dy = 1; dy <= 4; dy++) { p.set(-3, dy, 2, bone); p.set(3, dy, 2, bone); p.set(-3, dy, 1, bone); p.set(3, dy, 1, bone); }
                p.set(0, 2, 2, s(Blocks.STONE_BRICK_STAIRS).setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
                for (int sx : new int[]{-1, 1}) { p.set(sx, 2, 2, s(Blocks.STONE_BRICK_SLAB)); }
                // four tusks curving in over the seat
                for (int[] c : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
                    int sx = Integer.signum(-c[0]), sz = Integer.signum(-c[1]);
                    for (int y = 1; y <= 9; y++) {
                        int in = y > 5 ? y - 5 : 0;
                        p.set(c[0] + sx * in, y, c[1] + sz * in, bone);
                    }
                }
                for (int[] c : new int[][]{{-4, -3}, {4, -3}}) { p.set(c[0], 1, c[1], s(Blocks.OAK_FENCE)); p.set(c[0], 2, c[1], s(Blocks.OAK_FENCE)); p.set(c[0], 3, c[1], s(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, rnd.nextInt(16))); }
            }
            case STATUE_FIELD -> {
                BlockState base = s(fam == Forms.SUN ? Blocks.SANDSTONE : Blocks.STONE_BRICKS), mossy = s(fam == Forms.GREEN ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS);
                List<int[]> at = new ArrayList<>();
                for (int tries = 0; tries < 200 && at.size() < 22; tries++) {
                    int x = rnd.nextInt(61) - 30, z = rnd.nextInt(61) - 30;
                    if (x * x + z * z > 30 * 30 || x * x + z * z < 9) continue;
                    boolean clear = true;
                    for (int[] o : at) if ((o[0] - x) * (o[0] - x) + (o[1] - z) * (o[1] - z) < 30) clear = false;
                    if (clear) at.add(new int[]{x, z});
                }
                for (int[] o : at) {
                    boolean broken = rnd.nextInt(4) == 0;
                    p.set(o[0], 1, o[1], rnd.nextBoolean() ? base : mossy);
                    p.set(o[0], 2, o[1], s(Blocks.STONE_BRICK_WALL));
                    p.set(o[0], 3, o[1], s(Blocks.STONE_BRICK_WALL));
                    p.set(o[0] + 1, 3, o[1], s(Blocks.STONE_BRICK_SLAB));
                    p.set(o[0] - 1, 3, o[1], s(Blocks.STONE_BRICK_SLAB));
                    if (!broken) p.set(o[0], 4, o[1], s(Blocks.CHISELED_STONE_BRICKS));
                }
                p.set(0, 1, 0, s(Blocks.CHISELED_STONE_BRICKS));
            }
            case WEB_CATHEDRAL -> {
                BlockState log = s(fam == Forms.FROST ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG);
                int n = 8;
                int[][] pillar = new int[n][2];
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    pillar[i] = new int[]{(int) Math.round(Math.cos(a) * 9), (int) Math.round(Math.sin(a) * 9)};
                    for (int y = 1; y <= 13; y++) p.set(pillar[i][0], y, pillar[i][1], log);
                }
                for (int i = 0; i < n; i++) { // silk strung between neighbours, in an arch
                    int[] a = pillar[i], b = pillar[(i + 1) % n];
                    for (int k = 0; k <= 12; k++) {
                        double t = k / 12.0;
                        int x = (int) Math.round(a[0] + (b[0] - a[0]) * t), z = (int) Math.round(a[1] + (b[1] - a[1]) * t);
                        int y = 13 - (int) Math.round(Math.sin(t * Math.PI) * -3.0) + (int) Math.round(-Math.sin(t * Math.PI) * 3.0) - 0;
                        p.set(x, 13 - (int) Math.round(Math.sin(t * Math.PI) * 3), z, s(Blocks.COBWEB));
                        if (k % 4 == 2) { p.set(x, 12 - (int) Math.round(Math.sin(t * Math.PI) * 3), z, s(Blocks.COBWEB)); }
                    }
                }
                for (int i = 0; i < n; i += 2) { // cocoons
                    int[] a = pillar[i];
                    for (int y = 8; y <= 10; y++) p.set(a[0] + Integer.signum(-a[0]), y, a[1] + Integer.signum(-a[1]), s(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)));
                    p.set(a[0] + Integer.signum(-a[0]), 11, a[1] + Integer.signum(-a[1]), s(Blocks.COBWEB));
                }
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) if (dx * dx + dz * dz <= 25) p.set(dx, 0, dz, s(Blocks.COARSE_DIRT));
            }
            case GRAVEFIELD -> {
                BlockState brick = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                    boolean wall = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                    for (int y = 1; y <= 5; y++) if (wall && !(dz == 4 && dx >= -1 && dx <= 1 && y <= 3)) p.set(dx, y, dz, brick);
                    p.set(dx, 6, dz, s(Blocks.BLACKSTONE_SLAB));
                    p.set(dx, 0, dz, s(Blocks.BLACKSTONE));
                }
                p.set(0, 5, 0, s(Blocks.SOUL_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                p.set(0, 6, 0, s(Blocks.IRON_CHAIN));
                for (int row = 0; row < 3; row++) for (int col = 0; col < 7; col++) {
                    int x = -12 + col * 4, z = -9 - row * 4;
                    if (rnd.nextInt(6) == 0) { p.set(x, 0, z, s(Blocks.COARSE_DIRT)); continue; } // an open grave
                    p.set(x, 1, z, s(Blocks.MOSSY_STONE_BRICKS));
                    p.set(x, 2, z, s(Blocks.STONE_BRICK_WALL));
                    p.set(x, 3, z, s(Blocks.STONE_BRICK_SLAB));
                    p.set(x, 1, z + 1, s(Blocks.COARSE_DIRT));
                }
            }
            case WEEPING_STONES -> {
                for (int i = 0; i < 9; i++) {
                    double a = i * Math.PI * 2 / 9;
                    int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12), h = 6 + rnd.nextInt(5);
                    p.set(x, 1, z, s(Blocks.OBSIDIAN));
                    for (int y = 2; y <= h; y++) p.set(x, y, z, s(Blocks.CRYING_OBSIDIAN));
                    p.set(x, h + 1, z, s(Blocks.OBSIDIAN));
                }
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + 0.5;
                    int x = (int) Math.round(Math.cos(a) * 5), z = (int) Math.round(Math.sin(a) * 5);
                    p.set(x, 1, z, s(Blocks.CRYING_OBSIDIAN));
                    if (i % 2 == 0) p.set(x, 2, z, s(Blocks.CRYING_OBSIDIAN));
                }
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) p.set(dx, 0, dz, s(Blocks.OBSIDIAN));
                p.set(0, 1, 0, s(Blocks.CRYING_OBSIDIAN));
            }
        }
    }
}
