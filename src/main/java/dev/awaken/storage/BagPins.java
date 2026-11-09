package dev.awaken.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Which of your bag's slots Tidy leaves alone (a bit per Inventory slot), and whether you've been told what pins are. */
public record BagPins(int schemaVersion, long mask, boolean hinted) {
    public static final BagPins EMPTY = new BagPins(1, 0L, false);
    public static final MapCodec<BagPins> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(BagPins::schemaVersion),
            Codec.LONG.optionalFieldOf("mask", 0L).forGetter(BagPins::mask),
            Codec.BOOL.optionalFieldOf("hinted", false).forGetter(BagPins::hinted)
    ).apply(i, BagPins::new));

    public boolean has(int slot) {
        return slot >= 0 && slot < 64 && (mask & 1L << slot) != 0;
    }

    BagPins toggled(int slot) {
        return new BagPins(schemaVersion, mask ^ 1L << slot, hinted);
    }

    BagPins seenHint() {
        return new BagPins(schemaVersion, mask, true);
    }
}
