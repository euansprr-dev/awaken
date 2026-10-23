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

    /** Classifies a room from its contents (pure, used by the scanner and the tests). */
    public static EnumSet<RoomType> classify(int beds, int mats, int anvils, int forges, int stands, int trophies, int machines, int pads) {
        EnumSet<RoomType> t = EnumSet.noneOf(RoomType.class);
        if (beds > 0 && mats > 0) t.add(RoomType.SANCTUARY);
        if (anvils > 0 && forges > 0 && stands > 0) t.add(RoomType.ARMORY);
        if (trophies > 0) t.add(RoomType.TROPHY_HALL);
        if (machines > 0) t.add(RoomType.WORKSHOP);
        if (pads > 0) t.add(RoomType.BARRACKS);
        return t;
    }
}
