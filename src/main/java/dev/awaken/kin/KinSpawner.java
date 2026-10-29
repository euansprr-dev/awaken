package dev.awaken.kin;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.TheLull;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.village.api.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the kin live in the wild (SPEC §43.5): the land past the ridge is cut into 128-block cells, and about half of
 * them hold one site, its spot and its kind pure from the seed and the land there (a Lampwing in woods, Burrowbuns in
 * meadow and plains, a Mossback by rivers and swamps, Pebblepups on rocky slopes and stony shores). Never where the
 * bells carry, in the city's basin, on a settlement's ground or inside a Hearth. A site fills when a player comes within
 * 96 blocks (never closer than 24, so nothing pops in under their nose), and fills again three days after its beasts
 * are gone. Rumours (the Hearth page's needs) are picked from these same sites, so a rumour always points at a real one.
 */
public final class KinSpawner {
    private KinSpawner() {}

    static final int CELL_SHIFT = 7, NEAR = 96, AWAY = 24, REFILL_DAYS = 3, RUMOUR_REACH = 600, RUMOUR_MIN = 100;

    record Site(long cell, BlockPos at, KinKind kind) {}

    static long cell(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The cell's one site, if it has one. No chunk loads (biomes come from the generator for unloaded land). */
    static @Nullable Site site(ServerLevel level, int cx, int cz) {
        long key = cell(cx, cz);
        KinKind promised = KinWorld.get(level.getServer()).promised.get(key);
        long h = mix(level.getSeed() ^ key * 0x9E3779B97F4A7C15L ^ 0x6B696E5L);
        if (promised == null && (h & 1) == 0) return null;
        int bx = (cx << CELL_SHIFT) + 16 + (int) ((h >>> 8) & 95), bz = (cz << CELL_SHIFT) + 16 + (int) ((h >>> 20) & 95);
        // the spot, or one of four tries round it (rivers are narrow)
        int[][] tries = {{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}};
        for (int[] t : tries) {
            // kept inside the cell, so a site is always its own cell's
            int x = Math.clamp(bx + t[0], (cx << CELL_SHIFT) + 4, (cx << CELL_SHIFT) + 123), z = Math.clamp(bz + t[1], (cz << CELL_SHIFT) + 4, (cz << CELL_SHIFT) + 123);
            BlockPos at = new BlockPos(x, 64, z);
            if (!wild(level, at)) continue;
            if (promised != null) {
                if (fits(level, at, promised)) return new Site(key, at, promised);
                continue;
            }
            List<KinKind> kinds = new ArrayList<>();
            for (KinKind k : KinKind.livingNow()) if (fits(level, at, k)) kinds.add(k);
            if (!kinds.isEmpty()) return new Site(key, at, kinds.get((int) ((h >>> 36) & 0xFFFF) % kinds.size()));
        }
        return null;
    }

    /** Whether a kind's land is here. */
    static boolean fits(ServerLevel level, BlockPos at, KinKind k) {
        Holder<Biome> b = level.getBiome(at);
        return switch (k) {
            case LAMPWING -> b.is(BiomeTags.IS_FOREST);
            case BURROWBUN -> b.is(Tags.Biomes.IS_PLAINS) || b.is(Biomes.MEADOW);
            case MOSSBACK -> b.is(Tags.Biomes.IS_SWAMP) || b.is(BiomeTags.IS_RIVER);
            case PEBBLEPUP -> b.is(BiomeTags.IS_MOUNTAIN) || b.is(Tags.Biomes.IS_STONY_SHORES) || b.is(Tags.Biomes.IS_WINDSWEPT) || b.is(Tags.Biomes.IS_MOUNTAIN_PEAK);
            default -> false;
        };
    }

    /** The wild: past the ridge, off the city's basin, off settlements' ground and outside every Hearth. */
    static boolean wild(ServerLevel level, BlockPos at) {
        return level.dimension() == Level.OVERWORLD && !TheLull.bellsCarry(level, at) && !CityBasin.reservedWithin(at.getX(), at.getZ(), 64)
                && !Villages.reserved(level, at, 48) && !HearthHooks.inside(level, at);
    }

    // ---- filling ------------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 37) return;
        ServerLevel level = server.overworld();
        KinWorld w = KinWorld.get(server);
        long day = level.getOverworldClockTime() / 24000L;
        Set<Long> seen = new HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            int pcx = p.getBlockX() >> CELL_SHIFT, pcz = p.getBlockZ() >> CELL_SHIFT;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    if (!seen.add(cell(cx, cz))) continue;
                    Site s = site(level, cx, cz);
                    if (s == null) continue;
                    double ddx = s.at().getX() + 0.5 - p.getX(), ddz = s.at().getZ() + 0.5 - p.getZ();
                    double d2 = ddx * ddx + ddz * ddz;
                    if (d2 > NEAR * NEAR || d2 < AWAY * AWAY || tooNear(level, s)) continue;
                    if (!level.isPositionEntityTicking(new BlockPos(s.at().getX(), p.getBlockY(), s.at().getZ()))) continue;
                    Long when = w.filled.get(s.cell());
                    if (when != null && day - when < REFILL_DAYS) continue;
                    if (when == null || !present(level, s)) fill(level, s);
                    w.filled.put(s.cell(), day);
                    w.setDirty();
                }
        }
    }

    /** Someone stands right by the spot (any player): it fills later. */
    private static boolean tooNear(ServerLevel level, Site s) {
        for (ServerPlayer p : level.players()) {
            double dx = s.at().getX() + 0.5 - p.getX(), dz = s.at().getZ() + 0.5 - p.getZ();
            if (dx * dx + dz * dz < AWAY * AWAY) return true;
        }
        return false;
    }

    private static boolean present(ServerLevel level, Site s) {
        return !level.getEntitiesOfClass(KinBeast.class, new AABB(s.at()).inflate(64, 128, 64),
                b -> b.isAlive() && b.home == null && b.kind() == s.kind()).isEmpty();
    }

    static int group(KinKind k) {
        return switch (k) {
            case BURROWBUN, PEBBLEPUP -> 2;
            default -> 1;
        };
    }

    /** The site's beasts, at their spot (a Pebblepup's pile set down by it). */
    static List<KinBeast> fill(ServerLevel level, Site s) {
        List<KinBeast> out = new ArrayList<>();
        BlockPos spot = stand(level, s.at().getX(), s.at().getZ(), s.kind());
        if (spot == null) return out;
        if (s.kind() == KinKind.PEBBLEPUP) pile(level, spot);
        for (int i = 0; i < group(s.kind()); i++) {
            BlockPos at = i == 0 ? spot : spot.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2);
            BlockPos g = KinHelp.ground(level, at.getX(), at.getZ());
            KinBeast b = KinHome.spawn(level, s.kind(), s.kind() == KinKind.LAMPWING ? g.above(2) : g, EntitySpawnReason.NATURAL);
            if (b == null) continue;
            b.wildAt(spot);
            out.add(b);
        }
        return out;
    }

    /** Dry ground near a column to stand on (a Mossback at a river stands on its bank), or null. */
    static @Nullable BlockPos stand(ServerLevel level, int x, int z, KinKind k) {
        for (int r = 0; r <= 12; r += 2)
            for (int dx = -r; dx <= r; dx += 2)
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos g = KinHelp.ground(level, x + dx, z + dz);
                    BlockState under = level.getBlockState(g.below());
                    if (under.getFluidState().is(FluidTags.WATER) || under.getFluidState().is(FluidTags.LAVA) || !under.isSolid()) continue;
                    if (!level.getBlockState(g).canBeReplaced()) continue;
                    return g;
                }
        return null;
    }

    /** A Pebblepup's pile: a few stones heaped where it lives, one of them shiny (its want is right there). */
    private static void pile(ServerLevel level, BlockPos at) {
        BlockPos[] spots = {at.offset(1, 0, 0), at.offset(1, 0, 1), at.offset(2, 0, 0)};
        BlockState[] stones = {Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.CALCITE.defaultBlockState()};
        for (int i = 0; i < spots.length; i++) {
            BlockPos g = KinHelp.ground(level, spots[i].getX(), spots[i].getZ());
            if (Math.abs(g.getY() - at.getY()) > 1 || !level.getBlockState(g).canBeReplaced() || !level.getBlockState(g.below()).isSolid()) continue;
            level.setBlock(g, stones[i], 3);
        }
    }

    // ---- rumours ----------------------------------------------------------------------------------------------------

    /** A real site of a kind within 600 blocks of {@code from} (nearest rings first, never right at home); promised. */
    static @Nullable BlockPos rumour(ServerLevel level, BlockPos from, KinKind k) {
        int fcx = from.getX() >> CELL_SHIFT, fcz = from.getZ() >> CELL_SHIFT;
        int rings = (RUMOUR_REACH >> CELL_SHIFT) + 1;
        KinWorld w = KinWorld.get(level.getServer());
        for (int r = 1; r <= rings; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Site s = site(level, fcx + dx, fcz + dz);
                    if (s == null) continue;
                    double d = Math.hypot(s.at().getX() - from.getX(), s.at().getZ() - from.getZ());
                    if (d < RUMOUR_MIN || d > RUMOUR_REACH) continue;
                    if (s.kind() != k) {
                        if (!fits(level, s.at(), k) || w.promised.containsKey(s.cell())) continue;
                        w.promised.put(s.cell(), k);
                        w.filled.remove(s.cell());
                        w.setDirty();
                    }
                    return s.at();
                }
        return null;
    }

    /** How plain a player's rumour of a kind is now: 0 as first given, 1 after a play-day unfollowed, 2 after two. */
    static int plainness(ServerPlayer p, Kinship.Kind s) {
        if (!s.rumour || s.met) return 0;
        long played = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        long since = played - s.rumourAt;
        return since >= 48000L ? 2 : since >= 24000L ? 1 : 0;
    }

    /** The player's rumour of a kind, found and kept the first time it's asked for; null if none lives near enough. */
    static @Nullable BlockPos rumourFor(ServerPlayer p, KinKind k, BlockPos from) {
        Kinship ks = Kinship.of(p);
        Kinship.Kind s = ks.kind(k);
        if (s.rumour) return new BlockPos(s.rumourX, 64, s.rumourZ);
        BlockPos at = rumour(p.level(), from, k);
        if (at == null) return null;
        s.rumour = true;
        s.heard = true;
        s.rumourX = at.getX();
        s.rumourZ = at.getZ();
        s.rumourAt = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        Kinship.save(p, ks);
        return at;
    }

    /** What the System says of where a kind lives: the sort of animal, its sort of place, a real region from home. */
    static String rumourLine(ServerPlayer p, KinKind k, BlockPos at, BlockPos from, boolean home) {
        Kinship.Kind s = Kinship.of(p).kind(k);
        int plain = plainness(p, s);
        double dx = at.getX() - from.getX(), dz = at.getZ() - from.getZ();
        int step = plain >= 1 ? 50 : 100;
        long dist = Math.max(step, Math.round(Math.hypot(dx, dz) / step) * step);
        String region = "about " + dist + " blocks " + compass(dx, dz) + " of " + (home ? "your home" : "here");
        String place = switch (k) {
            case LAMPWING -> "comes out at the edge of the woods after dark";
            case BURROWBUN -> "lives in soft meadow ground";
            case MOSSBACK -> "sits by slow water, in the sun";
            case PEBBLEPUP -> "lives on the rocky slopes";
            default -> "lives out there";
        };
        String sort = k.sort.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + k.sort.substring(1);
        String near = "";
        if (plain >= 1) {
            String biome = level(p).getBiome(at).unwrapKey().map(r -> r.identifier().getPath().replace('_', ' ')).orElse("");
            if (!biome.isEmpty()) near = " The land there is " + biome + ".";
        }
        return sort + " " + place + ", " + region + "." + near;
    }

    private static ServerLevel level(ServerPlayer p) {
        return p.level();
    }

    /** Eight-point compass words for a heading on the map (north is -z). */
    static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        if (a < 0) a += 360;
        String[] w = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return w[(int) Math.round(a / 45) % 8];
    }

    /** Track's rough bearing: the spot moved by up to 48 blocks (24, then 8, as the rumour grows plainer). */
    static BlockPos rough(ServerPlayer p, KinKind k, BlockPos at) {
        int plain = plainness(p, Kinship.of(p).kind(k));
        int off = plain >= 2 ? 8 : plain == 1 ? 24 : 48;
        long h = mix(at.asLong() ^ p.getUUID().getLeastSignificantBits());
        int ox = (int) Math.floorMod(h, 2 * off + 1) - off, oz = (int) Math.floorMod(h >>> 20, 2 * off + 1) - off;
        return new BlockPos(at.getX() + ox, 64, at.getZ() + oz);
    }
}
