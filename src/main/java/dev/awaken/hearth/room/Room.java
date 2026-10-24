package dev.awaken.hearth.room;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.Set;

/**
 * An enclosed space inside a Hearth's radius and what it was recognised as. {@code cells} are the air/furniture
 * cells the flood fill visited, so "is this position in the Sanctuary" is exact.
 */
public record Room(EnumSet<RoomType> types, BlockPos min, BlockPos max, LongSet cells, int beds, int mats, int anvils,
                   int forges, int stands, int pads, int machines, Set<String> trophies) {

    public boolean contains(BlockPos p) {
        return cells.contains(p.asLong());
    }

    public BlockPos centre() {
        return new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2);
    }

    public int volume() {
        return cells.size();
    }

    /** How many of this furniture the room holds (trophies: different Lords on mounts). */
    public int count(Furniture f) {
        return count(f, beds, mats, anvils, forges, stands, trophies.size(), machines, pads);
    }

    private static int count(Furniture f, int beds, int mats, int anvils, int forges, int stands, int trophies, int machines, int pads) {
        return switch (f) {
            case BED -> beds;
            case MAT -> mats;
            case ANVIL -> anvils;
            case FORGE -> forges;
            case STAND -> stands;
            case TROPHY -> trophies;
            case MACHINE -> machines;
            case PAD -> pads;
        };
    }

    /** Classifies a room from its contents with {@link RoomType#furniture} (pure, used by the scanner and the tests). */
    public static EnumSet<RoomType> classify(int beds, int mats, int anvils, int forges, int stands, int trophies, int machines, int pads) {
        EnumSet<RoomType> t = EnumSet.noneOf(RoomType.class);
        for (RoomType type : RoomType.values()) {
            if (type.metBy(f -> count(f, beds, mats, anvils, forges, stands, trophies, machines, pads))) t.add(type);
        }
        return t;
    }
}
