package dev.awaken.hearth.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An enclosed space inside a Hearth's radius: what it was recognised as, what it holds, and how it reads. {@code cells}
 * are the air/furniture cells the flood fill visited, so "is this position in the Sanctuary" is exact. A room loaded
 * from the save has no cells until the next scan reads it again: until then its box answers.
 */
public record Room(EnumSet<RoomType> types, BlockPos min, BlockPos max, LongSet cells, int[] counts, int[] facts, Set<String> trophies,
                   Reading reading) {

    public boolean contains(BlockPos p) {
        if (!cells.isEmpty()) return cells.contains(p.asLong());
        return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    public BlockPos centre() {
        return new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2);
    }

    public int volume() {
        return cells.size();
    }

    /** How many of this furniture the room holds (trophies: different Lords on mounts). */
    public int count(Furniture f) {
        return f == Furniture.TROPHY ? trophies.size() : f.ordinal() < counts.length ? counts[f.ordinal()] : 0;
    }

    public int fact(int i) {
        return i < facts.length ? facts[i] : 0;
    }

    /** A key that stays the same while the room does (its box): which rooms a player has been told about. */
    public long key() {
        return min.asLong() * 31 + max.asLong();
    }

    /** Classifies a room from its counts and facts with {@link RoomType#metBy} (pure, used by the scanner and the tests). */
    public static EnumSet<RoomType> classify(int[] counts, int[] facts) {
        EnumSet<RoomType> t = EnumSet.noneOf(RoomType.class);
        for (RoomType type : RoomType.values()) if (type.metBy(counts, facts)) t.add(type);
        return t;
    }

    /** As before the reading: a room holding just these (the tests' shorthand). */
    public static EnumSet<RoomType> classify(int beds, int mats, int anvils, int forges, int stands, int trophies, int machines) {
        int[] c = new int[Furniture.COUNT];
        c[Furniture.BED.ordinal()] = beds;
        c[Furniture.MAT.ordinal()] = mats;
        c[Furniture.ANVIL.ordinal()] = anvils;
        c[Furniture.FORGE.ordinal()] = forges;
        c[Furniture.STAND.ordinal()] = stands;
        c[Furniture.TROPHY.ordinal()] = trophies;
        c[Furniture.MACHINE.ordinal()] = machines;
        return classify(c, new int[Tally.F_COUNT]);
    }

    // ---- saved (HearthRecord schema 3): everything but the cells ------------------------------------------------------

    private record Saved(List<String> types, BlockPos min, BlockPos max, List<Integer> counts, List<Integer> facts, List<String> trophies,
                         Reading reading) {}

    public static final Codec<Room> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
            Codec.STRING.listOf().fieldOf("types").forGetter(Saved::types),
            BlockPos.CODEC.fieldOf("min").forGetter(Saved::min),
            BlockPos.CODEC.fieldOf("max").forGetter(Saved::max),
            Codec.INT.listOf().optionalFieldOf("counts", List.of()).forGetter(Saved::counts),
            Codec.INT.listOf().optionalFieldOf("facts", List.of()).forGetter(Saved::facts),
            Codec.STRING.listOf().optionalFieldOf("trophies", List.of()).forGetter(Saved::trophies),
            Reading.CODEC.fieldOf("reading").forGetter(Saved::reading)
    ).apply(i, Saved::new)).xmap(Room::fromSaved, Room::toSaved);

    private static Room fromSaved(Saved s) {
        EnumSet<RoomType> types = EnumSet.noneOf(RoomType.class);
        for (String t : s.types) {
            try {
                types.add(RoomType.valueOf(t));
            } catch (IllegalArgumentException ignored) {
                // a kind this build doesn't know
            }
        }
        int[] counts = new int[Furniture.COUNT];
        for (int k = 0; k < counts.length && k < s.counts.size(); k++) counts[k] = s.counts.get(k);
        int[] facts = new int[Tally.F_COUNT];
        for (int k = 0; k < facts.length && k < s.facts.size(); k++) facts[k] = s.facts.get(k);
        return new Room(types, s.min, s.max, new LongOpenHashSet(), counts, facts, Set.copyOf(s.trophies), s.reading);
    }

    private Saved toSaved() {
        List<String> t = new ArrayList<>();
        for (RoomType type : types) t.add(type.name());
        List<Integer> c = new ArrayList<>(counts.length), f = new ArrayList<>(facts.length);
        for (int v : counts) c.add(v);
        for (int v : facts) f.add(v);
        return new Saved(t, min, max, c, f, List.copyOf(trophies), reading);
    }
}
