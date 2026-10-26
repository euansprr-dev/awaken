package dev.awaken.delve;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The minerals a lode can be made of (SPEC §28.3, PLAN 7.3): vanilla's eight overworld ores and Deepglass. Each has its
 * own depth (the band its lodes' hearts sit in), how many lodes a 256-block region holds, how big one runs (in blocks),
 * the colour the Ore Sense glass and the vein's light take, and the job it has (the line the System says when you first
 * find it). Numbers here are the source for SPEC §28.3; change them together.
 */
public enum Ore {
    //       name        lodes chance  y band      size      tint
    COAL("Coal", 2, 1.0, 6, 40, 50, 110, 0xB8C2CE, () -> Blocks.COAL_ORE, () -> Blocks.DEEPSLATE_COAL_ORE),
    COPPER("Copper", 2, 1.0, 0, 40, 50, 110, 0xFFB07A, () -> Blocks.COPPER_ORE, () -> Blocks.DEEPSLATE_COPPER_ORE),
    IRON("Iron", 3, 1.0, -20, 36, 40, 100, 0xEAD2BC, () -> Blocks.IRON_ORE, () -> Blocks.DEEPSLATE_IRON_ORE),
    GOLD("Gold", 1, 1.0, -54, 16, 30, 70, 0xFFE27A, () -> Blocks.GOLD_ORE, () -> Blocks.DEEPSLATE_GOLD_ORE),
    REDSTONE("Redstone", 1, 1.0, -58, -4, 40, 90, 0xFF7A7A, () -> Blocks.REDSTONE_ORE, () -> Blocks.DEEPSLATE_REDSTONE_ORE),
    LAPIS("Lapis", 1, 1.0, -48, 8, 30, 60, 0x7FA0FF, () -> Blocks.LAPIS_ORE, () -> Blocks.DEEPSLATE_LAPIS_ORE),
    EMERALD("Emerald", 1, 0.35, -8, 30, 20, 32, 0x7FFFA8, () -> Blocks.EMERALD_ORE, () -> Blocks.DEEPSLATE_EMERALD_ORE),
    DIAMOND("Diamond", 1, 0.8, -58, -18, 20, 40, 0x8FF6FF, () -> Blocks.DIAMOND_ORE, () -> Blocks.DEEPSLATE_DIAMOND_ORE),
    DEEPGLASS("Deepglass", 1, 1.0, -58, -12, 25, 50, 0xB48CFF, () -> DelveContent.DEEPGLASS.get(), () -> DelveContent.DEEPGLASS.get());

    public final String title;
    /** Lodes per 256 x 256 region (the last one only {@link #lastChance} of the time). */
    public final int lodes;
    public final double lastChance;
    /** The band the lode's heart sits in (y, inclusive). */
    public final int minY, maxY;
    /** How many blocks of ore one lode holds before caves and air cut it (20-120, PLAN 7.3). */
    public final int minSize, maxSize;
    public final int tint;
    private final Supplier<Block> stone, deep;

    Ore(String title, int lodes, double lastChance, int minY, int maxY, int minSize, int maxSize, int tint, Supplier<Block> stone, Supplier<Block> deep) {
        this.title = title;
        this.lodes = lodes;
        this.lastChance = lastChance;
        this.minY = minY;
        this.maxY = maxY;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.tint = tint;
        this.stone = stone;
        this.deep = deep;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The ore block in stone (or the deep one in deepslate, which is also what Deepglass always is). */
    public Block block(boolean inDeepslate) {
        return inDeepslate ? deep.get() : stone.get();
    }

    /** This ore's lode heart (one block per ore, so each takes its own tool tier). */
    public Block heart() {
        return DelveContent.HEARTS.get(this).get();
    }

    /** Lowercase for the System's lines ("LODE · iron · 64"). */
    public String word() {
        return title.toLowerCase(Locale.ROOT);
    }

    /** A block of this ore: either variant or its heart. */
    public boolean matches(BlockState s) {
        Block b = s.getBlock();
        return b == stone.get() || b == deep.get() || b == heart();
    }

    /** Which lode ore a block is (its ore blocks and its heart), or null. */
    public static @Nullable Ore of(BlockState s) {
        for (Ore o : values()) if (o.matches(s)) return o;
        return null;
    }

    public static @Nullable Ore byId(String id) {
        for (Ore o : values()) if (o.id().equals(id)) return o;
        return null;
    }
}
