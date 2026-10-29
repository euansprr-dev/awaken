package dev.awaken.hearth.room;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * One finished room scan: the closed rooms, the furniture that isn't in one (and why), how many of each piece of
 * furniture the radius holds ({@code furniture[Furniture.ordinal()]}; trophies count only when mounted), what the land
 * round the rooms has (for Comfort), and the faces of each material family over every room (the home's Style).
 */
public record Scan(List<Room> rooms, List<Miss> misses, int[] furniture, Land land, int[] families, Yard yard) {
    public static final Scan EMPTY = new Scan(List.of(), List.of(), new int[Furniture.COUNT], Land.NONE, new int[Materials.FAMILIES], Yard.NONE);

    public Scan(List<Room> rooms, List<Miss> misses, int[] furniture, Land land, int[] families) {
        this(rooms, misses, furniture, land, families, Yard.NONE);
    }

    /**
     * (W6) Where the land's work is, for the people who live here: the farmland outside rooms (Hettie's fields, at most
     * 512), the fence gates outside rooms (a pen's gate, at most 16) and the farm animals (at most 32).
     */
    public record Yard(List<BlockPos> fields, List<BlockPos> gates, List<BlockPos> beasts) {
        public static final Yard NONE = new Yard(List.of(), List.of(), List.of());
    }

    public int count(Furniture f) {
        return furniture.length > f.ordinal() ? furniture[f.ordinal()] : 0;
    }

    /**
     * The land's features outside the rooms (PLAN 4.5): a field (farmland), a pen (farm animals by fences), a garden (a
     * composter and flowers), a lit yard (lights outdoors at night) and a wall round the place.
     */
    public record Land(int farmland, int animals, int fences, int walls, int composters, int flowers, int outdoorLights) {
        public static final Land NONE = new Land(0, 0, 0, 0, 0, 0, 0);

        public boolean field() {
            return farmland >= 16;
        }

        public boolean pen() {
            return animals >= 2 && fences >= 8;
        }

        public boolean garden() {
            return composters >= 1 && flowers >= 6;
        }

        public boolean litYard() {
            return outdoorLights >= 6;
        }

        public boolean wall() {
            return walls + fences >= 40;
        }
    }
}
