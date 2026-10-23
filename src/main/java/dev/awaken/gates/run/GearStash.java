package dev.awaken.gates.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Gear held safe across a death inside a gate (attachment {@code awaken:gate_stash}, copied on death). Slots keep their
 * inventory index so the hotbar comes back as it was. Restored on respawn.
 */
public record GearStash(List<Slot> slots, int xpLevel, float xpProgress, int xpTotal) {
    public static final GearStash EMPTY = new GearStash(List.of(), 0, 0, 0);

    public record Slot(int index, ItemStack stack) {
        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("i").forGetter(Slot::index),
                ItemStack.CODEC.fieldOf("stack").forGetter(Slot::stack)
        ).apply(i, Slot::new));
    }

    public static final MapCodec<GearStash> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Slot.CODEC.listOf().fieldOf("slots").forGetter(GearStash::slots),
            Codec.INT.fieldOf("xp_level").forGetter(GearStash::xpLevel),
            Codec.FLOAT.fieldOf("xp_progress").forGetter(GearStash::xpProgress),
            Codec.INT.fieldOf("xp_total").forGetter(GearStash::xpTotal)
    ).apply(i, GearStash::new));

    public boolean isEmpty() {
        return slots.isEmpty() && xpLevel == 0 && xpProgress == 0;
    }

    public GearStash {
        slots = List.copyOf(slots);
    }
}
