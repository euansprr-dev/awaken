package dev.awaken.living.wonder;

import dev.awaken.living.region.RegionMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The surface secrets (SPEC §21.11, decision 28): three in every region (a cairn over a brushable cache, a cold camp with a barrel, a chest under a
 * marked mound), each at a place a pure function of the seed and the region picks, with a cache of its land's rank (`awaken:chests/living/secret_<rank>`,
 * `awaken:archaeology/living/secret_<rank>`). Never listed: finding one is walking up to it, which counts a secret found (the "Secrets found" row) and says so once.
 * Underground secrets and the grotto's legendary fishing spot are the caves' (SPEC §28).
 */
public final class Secrets {
    private Secrets() {}

    public static final int REACH = 6;
    public static final int FIND = 6;

    public enum Kind {
        CAIRN("cairn", "A cairn", "Someone stacked these stones to mark a spot. The gravel at its foot has been disturbed."),
        OLD_CAMP("old_camp", "An old camp", "A cold fire, two logs to sit on, and a barrel somebody left."),
        BURIED_CHEST("buried_chest", "A marked mound", "A mound of bare earth with a bone on top. It is the only one for a long way.");

        public final String id, display, line;

        Kind(String id, String display, String line) {
            this.id = id;
            this.display = display;
            this.line = line;
        }
    }

    /** Where a cache stands in the plan (relative to the secret's heart), and what kind of container it is. */
    public record Cache(int dx, int dy, int dz, boolean brushable) {}

    public static final class Plan {
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final List<Cache> caches = new ArrayList<>(1);

        void set(int dx, int dy, int dz, BlockState s) {
            if (Math.abs(dx) <= REACH && Math.abs(dz) <= REACH && dy >= -4 && dy <= 10) blocks.put(BlockPos.asLong(dx, dy, dz), s);
        }

        public Long2ObjectOpenHashMap<BlockState> blocks() {
            return blocks;
        }

        public List<Cache> caches() {
            return caches;
        }
    }

    public record Site(Kind kind, long region, String regionName, int index, int rank, int x, int y, int z, Plan plan) {
        public String key() {
            return kind.id + "@" + x + "," + z;
        }
    }

    private static final Site NONE = new Site(Kind.CAIRN, -1, "", 0, 0, 0, 0, 0, new Plan());
    private static final ConcurrentHashMap<Long, Site[]> CACHE = new ConcurrentHashMap<>();

    public static void reset() {
        CACHE.clear();
    }

    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b + 0x632BE59BD9B4E019L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    /** A region's secrets (up to three; ground that is wet or steep gives one up). */
    public static List<Site> of(long seed, RegionMap map, RegionMap.Region r, Wonders.Terrain t) {
        if (r.commons()) return List.of();
        Site[] all = CACHE.computeIfAbsent(mix(seed, r.id()), k -> make(seed, map, r, t));
        List<Site> out = new ArrayList<>(3);
        for (Site s : all) if (s != NONE) out.add(s);
        return out;
    }

    private static Site[] make(long seed, RegionMap map, RegionMap.Region r, Wonders.Terrain t) {
        Site[] out = new Site[3];
        for (int i = 0; i < 3; i++) {
            out[i] = NONE;
            long h = mix(mix(seed, r.id()), i + 1);
            for (int attempt = 0; attempt < 5; attempt++) {
                double angle = ((h >>> 8) & 0xFFFF) / 65536.0 * Math.PI * 2 + attempt * 2.1;
                double dist = 150 + ((h >>> 28) & 0xFF) + attempt * 15;
                int x = r.heartX() + (int) Math.round(Math.cos(angle) * dist), z = r.heartZ() + (int) Math.round(Math.sin(angle) * dist);
                if (map.regionAt(x, z).id() != r.id()) continue;
                int[] hs = {t.ground(x, z), t.ground(x + 3, z), t.ground(x - 3, z), t.ground(x, z + 3), t.ground(x, z - 3)};
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int v : hs) {
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
                if (lo <= t.seaLevel() + 1 || hi - lo > 3) continue;
                Kind kind = Kind.values()[i];
                int rank = RegionMap.rankOf(map.levelAt(x, z));
                Plan plan = new Plan();
                build(kind, plan, RandomSource.create(mix(h, 7)));
                out[i] = new Site(kind, r.id(), r.name(), i, rank, x, t.ground(x, z), z, plan);
                break;
            }
        }
        return out;
    }

    /** Secrets within {@code radius} of the box (the regions at nine points across it). */
    public static List<Site> near(long seed, RegionMap map, Wonders.Terrain t, int x0, int z0, int x1, int z1, int radius) {
        Set<Long> seen = new HashSet<>();
        List<Site> out = new ArrayList<>(3);
        int ex0 = x0 - radius, ez0 = z0 - radius, ex1 = x1 + radius, ez1 = z1 + radius;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                RegionMap.Region r = map.regionAt(ex0 + (ex1 - ex0) * i / 2, ez0 + (ez1 - ez0) * j / 2);
                if (!seen.add(r.id())) continue;
                for (Site s : of(seed, map, r, t)) if (s.x() >= ex0 && s.x() <= ex1 && s.z() >= ez0 && s.z() <= ez1) out.add(s);
            }
        return out;
    }

    private static BlockState b(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    public static Plan planOf(Kind kind, long seed) {
        Plan p = new Plan();
        build(kind, p, RandomSource.create(seed));
        return p;
    }

    static void build(Kind kind, Plan p, RandomSource rnd) {
        switch (kind) {
            case CAIRN -> {
                for (int y = 1; y <= 3; y++) p.set(0, y, 0, b(y == 3 ? Blocks.COBBLESTONE_SLAB : Blocks.COBBLESTONE));
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) p.set(d[0], 1, d[1], b(Blocks.MOSSY_COBBLESTONE));
                p.set(1, 2, 0, b(Blocks.COBBLESTONE_SLAB));
                p.set(0, 0, 0, b(Blocks.STONE));
                for (int dx = 2; dx <= 3; dx++) for (int dz = -1; dz <= 1; dz++) p.set(dx, 0, dz, b(Blocks.GRAVEL));
                p.set(2, 0, 0, b(Blocks.SUSPICIOUS_GRAVEL));
                p.caches.add(new Cache(2, 0, 0, true));
            }
            case OLD_CAMP -> {
                p.set(0, 1, 0, b(Blocks.SOUL_CAMPFIRE).setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                p.set(2, 1, 0, b(Blocks.SPRUCE_LOG).setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
                p.set(-2, 1, 1, b(Blocks.SPRUCE_LOG).setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
                p.set(0, 0, 0, b(Blocks.COARSE_DIRT));
                p.set(-1, 1, -2, b(Blocks.BARREL).setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
                p.caches.add(new Cache(-1, 1, -2, false));
                p.set(1, 1, 2, b(Blocks.COBBLESTONE_SLAB));
            }
            case BURIED_CHEST -> {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (Math.abs(dx) + Math.abs(dz) < 2) p.set(dx, 1, dz, b(Blocks.COARSE_DIRT));
                p.set(0, 2, 0, b(Blocks.BONE_BLOCK));
                p.set(0, 3, 0, b(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, rnd.nextInt(16)));
                p.set(0, -1, 0, b(Blocks.DIRT));
                p.set(0, -2, 0, b(Blocks.CHEST));
                p.caches.add(new Cache(0, -2, 0, false));
            }
        }
    }

    /** The loot table of a cache at a rank (a chest or barrel's, or a brushed block's). */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table(int rank, boolean brushable) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                dev.awaken.Awaken.id((brushable ? "archaeology" : "chests") + "/living/secret_" + Math.max(0, Math.min(5, rank))));
    }

    public static @Nullable Kind byId(String id) {
        for (Kind k : Kind.values()) if (k.id.equals(id)) return k;
        return null;
    }
}
