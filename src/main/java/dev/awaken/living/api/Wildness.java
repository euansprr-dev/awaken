package dev.awaken.living.api;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.living.region.RegionService;
import dev.awaken.living.wild.WildField;
import dev.awaken.living.wild.WildRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * How wild a place is (SPEC §41, the Wild Lands): one value from 0 (tamed by people) to 1 (deep wild), separate from the
 * land's danger rank. People tame the land round them (the city, settlements, Hearths) and roads are ribbons of tamed land;
 * far from all of them the land goes wild, a little more in harder land, in patches. Vegetation, herds, packs and hunts all
 * read this one value. The land's part is worked out from the seed's places and the laid roads (never the live villages)
 * and kept per chunk ({@link WildRecord}), so it never shifts once worked out; Hearths tame live on top. Server only: {@link #land} on any thread,
 * the rest on the server thread.
 */
public final class Wildness {
    private Wildness() {}

    /** The bands' upper edges: Tamed under 0.2, Pasture under 0.45, Wild under 0.7, then Deep wild. */
    public static final double TAMED = 0.2, PASTURE = 0.45, WILD = 0.7;

    public enum Band {
        TAMED("Tamed"), PASTURE("Pasture"), WILD("Wild"), DEEP("Deep wild");
        public final String title;

        Band(String title) {
            this.title = title;
        }
    }

    public static Band band(double w) {
        return w < TAMED ? Band.TAMED : w < PASTURE ? Band.PASTURE : w < WILD ? Band.WILD : Band.DEEP;
    }

    /**
     * Something that tames the land round it, as the share of the land it leaves untouched at (x, z): 1 for none, 0 for
     * fully tamed. Registered by the module that owns it (the village module: the city, settlements and roads). Must be
     * pure for the land's part (the same answer on any thread, whatever has generated), and cheap: it runs 16 times a chunk.
     */
    @FunctionalInterface
    public interface Tamer {
        double keep(ServerLevel level, int x, int z);
    }

    private static final List<Tamer> TAMERS = new CopyOnWriteArrayList<>();

    public static void addTamer(Tamer t) {
        TAMERS.add(t);
    }

    /** Whether wildness applies here (the overworld; elsewhere every place reads as deep wild and nothing tames it). */
    public static boolean on(Level level) {
        return level instanceof ServerLevel && level.dimension() == Level.OVERWORLD;
    }

    /** The land's own wildness at (x, z), worked out afresh (no record, no Hearths): what generation and the record use. */
    public static double land(ServerLevel level, int x, int z) {
        if (!on(level)) return 1.0;
        double keep = 1.0;
        for (Tamer t : TAMERS) {
            keep *= t.keep(level, x, z);
            if (keep <= 0) break;
        }
        int lv = RegionService.map(level.getServer()).levelAt(x, z);
        double w = WildField.combine(keep, Regions.bandOf(lv), level.getSeed(), x, z);
        if (keep <= 0 || w >= 1.0) return w;
        Climate.TargetPoint c = climate(level).sample(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z));
        float weird = Climate.unquantizeCoord(c.weirdness());
        return Math.min(1.0, w + WildField.lean(keep, Climate.unquantizeCoord(c.erosion()), Climate.unquantizeCoord(c.humidity()),
                NoiseRouterData.peaksAndValleys(weird)));
    }

    /** The overworld's climate, sampled without a chunk's caches (pure; one sampler a world, shared by every thread). */
    private record Sampled(RandomState state, Climate.Sampler sampler) {}

    private static volatile Sampled sampled;

    private static Climate.Sampler climate(ServerLevel level) {
        RandomState rs = level.getChunkSource().randomState();
        Sampled s = sampled;
        if (s == null || s.state != rs) sampled = s = new Sampled(rs, rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        return s.sampler;
    }

    /**
     * How wild (x, z) is now: the chunk's record where the chunk is loaded (worked out once, then kept), else the land
     * afresh; then any Hearth near tames it further.
     */
    public static double at(ServerLevel level, int x, int z) {
        if (!on(level)) return 1.0;
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        double w = chunk != null ? WildRecord.of(level, chunk).at(x, z) : land(level, x, z);
        return w * hearthKeep(level, x, z);
    }

    public static Band bandAt(ServerLevel level, BlockPos pos) {
        return band(at(level, pos.getX(), pos.getZ()));
    }

    /**
     * How hidden someone at a place is, 0..1 (SPEC §41 decision 10: long grass is cover, both ways): registered by the module
     * that grows the cover (the wild's plants). Creatures that hunt by sight, and herds, see a person in cover from closer.
     */
    @FunctionalInterface
    public interface Cover {
        double at(Level level, BlockPos pos);
    }

    private static volatile Cover cover = (level, pos) -> 0;

    public static void setCover(Cover c) {
        cover = c;
    }

    public static double coverAt(Level level, BlockPos pos) {
        return Math.clamp(cover.at(level, pos), 0.0, 1.0);
    }

    /**
     * The wild's grass, as herds wear it (SPEC §41 decision 9): a grazing animal crops the plant at its feet, a moving herd
     * presses its way flat (strength {@link #LIGHT} one body, {@link #HERD} a herd on the move, {@link #STAMPEDE}). Registered by
     * the module that grows the grass (the ground module's {@code WildPlants}); until then nothing changes. Server thread.
     */
    public interface Ground {
        default boolean crop(ServerLevel level, BlockPos pos) {
            return false;
        }

        default boolean trample(ServerLevel level, BlockPos pos, int strength) {
            return false;
        }
    }

    public static final int LIGHT = 1, HERD = 2, STAMPEDE = 3;
    private static volatile Ground ground = new Ground() {};

    public static void setGround(Ground g) {
        ground = g;
    }

    public static Ground ground() {
        return ground;
    }

    /** A Hearth tames its land and 140 blocks round it, softly (SPEC §41.1): a home is never in the deep wild. */
    public static double hearthKeep(ServerLevel level, int x, int z) {
        double keep = 1.0;
        BlockPos pos = new BlockPos(x, 64, z);
        for (BlockPos anchor : HearthHooks.within(level, pos, WildField.HEARTH_RADIUS + WildField.HEARTH_REACH * 3)) {
            double d = Math.hypot(anchor.getX() - x, anchor.getZ() - z);
            keep *= WildField.touch(d, WildField.HEARTH_RADIUS, WildField.HEARTH_REACH, WildField.HEARTH_STRENGTH);
        }
        return keep;
    }
}
