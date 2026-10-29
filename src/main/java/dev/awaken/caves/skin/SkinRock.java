package dev.awaken.caves.skin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The rocks the skin is made of (SPEC §42.4): each piece is cut from the rock it lies on or hangs from, so a heap reads as
 * fallen from the wall beside it. {@link #hosts} are the natural blocks a rock dresses (the stone family takes andesite,
 * diorite and granite too); the regions' rocks are their own (P2 the Rootways' and the Ochre Deep's, P3 the other six).
 */
public enum SkinRock {
    STONE("stone", MapColor.STONE, SoundType.STONE, 1.5F, List.of(() -> Blocks.STONE, () -> Blocks.ANDESITE, () -> Blocks.DIORITE, () -> Blocks.GRANITE)),
    DEEPSLATE("deepslate", MapColor.DEEPSLATE, SoundType.DEEPSLATE, 3.0F, List.of(() -> Blocks.DEEPSLATE)),
    TUFF("tuff", MapColor.TERRACOTTA_GRAY, SoundType.TUFF, 1.5F, List.of(() -> Blocks.TUFF)),
    ROOTSTONE("rootstone", MapColor.DIRT, SoundType.PACKED_MUD, 1.5F, List.of(() -> CaveSkin.ROOTSTONE.get())),
    OCHRE("ochre", MapColor.TERRACOTTA_ORANGE, SoundType.STONE, 1.5F, List.of(() -> CaveSkin.OCHRE_STONE.get())),
    LIMESTONE("limestone", MapColor.TERRACOTTA_WHITE, SoundType.CALCITE, 1.5F, List.of(() -> CaveSkin.LIMESTONE.get())),
    SLATE("slate", MapColor.COLOR_GRAY, SoundType.STONE, 1.5F, List.of(() -> CaveSkin.BANDED_SLATE.get())),
    SILTSTONE("siltstone", MapColor.TERRACOTTA_GREEN, SoundType.MUD_BRICKS, 1.5F, List.of(() -> CaveSkin.SILTSTONE.get())),
    GLIMMER("glimmer", MapColor.SAND, SoundType.CALCITE, 1.5F, List.of(() -> CaveSkin.GLIMMERSTONE.get())),
    BASALT("basalt", MapColor.COLOR_BLACK, SoundType.BASALT, 3.0F, List.of(() -> CaveSkin.COLUMN_BASALT.get())),
    SEEPSTONE("seepstone", MapColor.COLOR_PURPLE, SoundType.DEEPSLATE, 3.0F, List.of(() -> CaveSkin.SEEPSTONE.get()));

    public final String id;
    public final MapColor color;
    public final SoundType sound;
    public final float strength;
    final List<Supplier<Block>> hosts;

    SkinRock(String id, MapColor color, SoundType sound, float strength, List<Supplier<Block>> hosts) {
        this.id = id;
        this.color = color;
        this.sound = sound;
        this.strength = strength;
        this.hosts = hosts;
    }

    /** Built at first use, after the blocks are registered (the region rocks are ours). */
    private static final class ByHost {
        static final Map<Block, SkinRock> MAP = new IdentityHashMap<>();

        static {
            for (SkinRock r : values()) for (Supplier<Block> b : r.hosts) MAP.put(b.get(), r);
        }
    }

    /** The rock a natural block dresses as, or null when it isn't natural rock the skin may touch. */
    public static @Nullable SkinRock of(Block block) {
        return ByHost.MAP.get(block);
    }
}
