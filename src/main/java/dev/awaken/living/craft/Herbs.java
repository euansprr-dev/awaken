package dev.awaken.living.craft;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wild herbs (SPEC §21.9) are no new blocks: a natural plant (grass, ferns, flowers) is "ripe" by a pure function of its
 * column and the day, so the client can glint it (Herb Glint) and the server checks it on harvest, with nothing stored.
 * 3% of plants are ripe on a day (6% with Root Sense, three times that in a Greenhouse); which herb comes out is the land's.
 */
public final class Herbs {
    private Herbs() {}

    /** Natural plants that can be ripe. */
    public static boolean plant(BlockState s) {
        return s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN) || s.is(BlockTags.SMALL_FLOWERS);
    }

    /** The day as both sides know it (the clock, so sleeping turns the page). */
    public static long day(Level level) {
        return Math.floorDiv(level.getOverworldClockTime(), 24000L);
    }

    /** Whether the plant at this column is ripe today. */
    public static boolean ripe(BlockPos pos, long day, boolean rootSense) {
        return ripe(pos, day, rootSense, false);
    }

    /**
     * As above; in a Hearth's Greenhouse the threshold is three times as high (SPEC §28.1). The same hash decides, so a
     * plant ripe outside would be ripe inside too.
     */
    public static boolean ripe(BlockPos pos, long day, boolean rootSense, boolean greenhouse) {
        long h = pos.getX() * 0x9E3779B97F4A7C15L ^ pos.getZ() * 0xC2B2AE3D27D4EB4FL ^ day * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        long threshold = rootSense ? 60 : 30;
        return Math.floorMod(h, 1000L) < (greenhouse ? threshold * 3 : threshold);
    }

    private static final TagKey<Biome> FROST = TagKey.create(Registries.BIOME, Awaken.id("fauna/frostwild")),
            FROST_TAIGA = TagKey.create(Registries.BIOME, Awaken.id("fauna/frostwild_taiga")),
            SUN = TagKey.create(Registries.BIOME, Awaken.id("fauna/sunscorch")),
            MIRE = TagKey.create(Registries.BIOME, Awaken.id("fauna/mire")),
            DUSK = TagKey.create(Registries.BIOME, Awaken.id("fauna/duskwood")),
            CANOPY = TagKey.create(Registries.BIOME, Awaken.id("fauna/canopy"));

    /** The herb a ripe plant gives here: the land's own, now and then spiritgrass anywhere. */
    public static Item herbAt(Level level, BlockPos pos, RandomSource r) {
        if (r.nextFloat() < 0.1F) return CraftItems.SPIRITGRASS.get();
        var biome = level.getBiome(pos);
        if (biome.is(FROST) || biome.is(FROST_TAIGA)) return CraftItems.FROSTCAP.get();
        if (biome.is(SUN)) return CraftItems.EMBERROOT.get();
        if (biome.is(MIRE)) return CraftItems.MOONMOSS.get();
        if (biome.is(DUSK) || biome.is(CANOPY)) return CraftItems.DUSKBLOOM.get();
        return CraftItems.SUNLEAF.get();
    }

    /** Ores (vanilla's and the common tag). */
    public static boolean ore(BlockState s) {
        return s.is(BlockTags.ORES) || s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES.block());
    }

    /** A deepslate ore. */
    public static boolean deep(BlockState s) {
        return ore(s) && s.getBlock().getDescriptionId().contains("deepslate");
    }
}
