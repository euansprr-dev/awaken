package dev.awaken.hearth.room;

import java.util.List;

/**
 * One finished room scan: the closed rooms, the furniture that isn't in one (and why), and how many of each piece of
 * furniture the radius holds ({@code furniture[Furniture.ordinal()]}; trophies count only when mounted).
 */
public record Scan(List<Room> rooms, List<Miss> misses, int[] furniture) {
    public static final Scan EMPTY = new Scan(List.of(), List.of(), new int[Furniture.COUNT]);

    public int count(Furniture f) {
        return furniture.length > f.ordinal() ? furniture[f.ordinal()] : 0;
    }
}
