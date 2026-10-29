package dev.awaken.delve;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * The blocks whose hit and break particles the strike draws (SPEC §34.3): the rock, ore, dirt, gravel, sand and
 * deepslate families of the overworld, the deep's own blocks and the cave regions' rock and ore twins, by name, so it
 * can be decided before tags load (the client claims them while mods register their client extensions, and only those no
 * mod has claimed).
 */
public final class StrikeBlocks {
    private StrikeBlocks() {}

    /** The kind of bite a block takes: hard rock sparks under a pick, loose ground doesn't. */
    public enum Family { ROCK, ORE, LOOSE }

    private static final Set<String> ROCK = Set.of(
            "stone", "cobblestone", "mossy_cobblestone", "granite", "diorite", "andesite", "deepslate", "cobbled_deepslate",
            "tuff", "calcite", "dripstone_block", "smooth_basalt", "basalt", "blackstone", "netherrack", "end_stone",
            "sandstone", "red_sandstone", "terracotta", "white_terracotta", "orange_terracotta", "yellow_terracotta",
            "brown_terracotta", "red_terracotta", "light_gray_terracotta", "obsidian", "amethyst_block", "budding_amethyst",
            "raw_iron_block", "raw_copper_block", "raw_gold_block");
    private static final Set<String> LOOSE = Set.of(
            "dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium", "gravel", "sand", "red_sand", "clay",
            "mud", "packed_mud", "soul_sand", "soul_soil", "moss_block");

    /** The cave regions' rock and ore twins (SPEC §42.7). */
    private static final Set<String> REGION_ROCK = Set.of("rootstone", "ochre_stone"),
            REGION_ORES = Set.of("rootstone_coal_ore", "rootstone_copper_ore", "ochre_iron_ore");

    /** The family of a block (null: not ours to draw). */
    public static Family family(Block b) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);
        String path = id.getPath();
        if (id.getNamespace().equals("minecraft")) {
            if (path.endsWith("_ore")) return Family.ORE;
            if (ROCK.contains(path)) return Family.ROCK;
            if (LOOSE.contains(path)) return Family.LOOSE;
            return null;
        }
        if (id.getNamespace().equals(dev.awaken.Awaken.MODID)) {
            if (path.equals("deepglass") || path.endsWith("_lode_heart") || REGION_ORES.contains(path)) return Family.ORE;
            if (REGION_ROCK.contains(path)) return Family.ROCK;
        }
        return null;
    }

    // ---- how much a bite throws (the client draws them; numbers here so the tests can read them) -----------------------

    /** At most this many chips and streaks from mining are alive at once. */
    public static final int MAX_CHIPS = 48, MAX_STREAKS = 24;

    /** Chips a bite throws at a particle setting (2 all, 1 decreased, 0 minimal): 2-4 at all. */
    public static int biteChips(int density, RandomSource r) {
        return density <= 0 ? 0 : density == 1 ? 1 + r.nextInt(2) : 2 + r.nextInt(3);
    }

    /** Chips a break throws: 8-14 at all. */
    public static int breakChips(int density, RandomSource r) {
        return density <= 0 ? 0 : density == 1 ? 4 + r.nextInt(4) : 8 + r.nextInt(7);
    }

    /** Spark streaks off metal or hard rock under a pick: 2-6 a bite, 6-10 on the last blow, 4 more on a true strike. */
    public static int streaks(boolean last, boolean trueStrike, int density, RandomSource r) {
        if (density <= 0) return 0;
        int n = last ? 6 + r.nextInt(5) : 2 + r.nextInt(5);
        if (trueStrike) n += 4;
        return density == 1 ? (n + 1) / 2 : n;
    }

    /** Whether the strike draws this block's particles (given no other mod has claimed it). */
    public static boolean owns(Block b) {
        return family(b) != null;
    }
}
