package dev.awaken.lucidium.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * One Day Journal entry: a memorable place, saved as a structure template file
 * ({@code <world>/awaken/lucidium/<player>/<id>.nbt}, at most 16x12x16). {@code pos} is where the player's feet were;
 * the template's min corner is {@code pos - (8, 4, 8)}, so the remembered floor sits at template y = 3.
 */
public record Snapshot(String id, Kind kind, long day, String dim, BlockPos pos, String label, String detail, long tick) {

    public enum Kind {
        NEAR_DEATH, BOSS, HOME, VISTA, KILLS, BIOME, STRUCTURE, GATE, VISION, MANUAL;
        public static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);

        /** How many of this kind may be captured per play-day (the journal also caps the day at 10). */
        public int dailyMax() {
            return switch (this) {
                case HOME, KILLS -> 1;
                case NEAR_DEATH, VISTA, GATE -> 2;
                case BOSS, BIOME, STRUCTURE -> 3;
                case VISION, MANUAL -> 99;
            };
        }

        /** Visions and admin captures never use the daily allowance. */
        public boolean countsTowardDay() {
            return this != VISION && this != MANUAL;
        }
    }

    public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Snapshot::id),
            Kind.CODEC.fieldOf("kind").forGetter(Snapshot::kind),
            Codec.LONG.fieldOf("day").forGetter(Snapshot::day),
            Codec.STRING.fieldOf("dim").forGetter(Snapshot::dim),
            BlockPos.CODEC.fieldOf("pos").forGetter(Snapshot::pos),
            Codec.STRING.fieldOf("label").forGetter(Snapshot::label),
            Codec.STRING.optionalFieldOf("detail", "").forGetter(Snapshot::detail),
            Codec.LONG.optionalFieldOf("tick", 0L).forGetter(Snapshot::tick)
    ).apply(i, Snapshot::new));

    public static final int SX = 16, SY = 12, SZ = 16;
    /** Feet level inside the template. */
    public static final int FLOOR = 4;

    public BlockPos corner() {
        return pos.offset(-SX / 2, -FLOOR, -SZ / 2);
    }
}
