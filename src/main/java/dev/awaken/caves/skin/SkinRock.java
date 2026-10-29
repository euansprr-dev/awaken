package dev.awaken.caves.skin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rocks the skin is made of (SPEC §42.4): each piece is cut from the rock it lies on or hangs from, so a heap reads as
 * fallen from the wall beside it. {@link #hosts} are the natural blocks a rock dresses (the stone family takes andesite,
 * diorite and granite too); the region rocks join in P2 and P3.
 */
public enum SkinRock {
    STONE("stone", MapColor.STONE, SoundType.STONE, 1.5F, List.of(Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE)),
    DEEPSLATE("deepslate", MapColor.DEEPSLATE, SoundType.DEEPSLATE, 3.0F, List.of(Blocks.DEEPSLATE)),
    TUFF("tuff", MapColor.TERRACOTTA_GRAY, SoundType.TUFF, 1.5F, List.of(Blocks.TUFF));

    public final String id;
    public final MapColor color;
    public final SoundType sound;
    public final float strength;
    final List<Block> hosts;

    SkinRock(String id, MapColor color, SoundType sound, float strength, List<Block> hosts) {
        this.id = id;
        this.color = color;
        this.sound = sound;
        this.strength = strength;
        this.hosts = hosts;
    }

    private static final Map<Block, SkinRock> BY_HOST = new IdentityHashMap<>();

    static {
        for (SkinRock r : values()) for (Block b : r.hosts) BY_HOST.put(b, r);
    }

    /** The rock a natural block dresses as, or null when it isn't natural rock the skin may touch. */
    public static @Nullable SkinRock of(Block block) {
        return BY_HOST.get(block);
    }
}
