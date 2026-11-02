package dev.awaken.gates.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Held for you (attachment {@code awaken:gate_held}, kept through death; SPEC §13, "Gate spoils"): what a gate gave you
 * that didn't fit your pockets. No limit and no expiry; only the gate puts things in, and you take them out from the
 * inventory screen. Mutated only on the server thread.
 */
public final class Held {
    public static final int SCHEMA = 1;

    public final List<ItemStack> items = new ArrayList<>();

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static final MapCodec<Held> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(h -> SCHEMA),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(h -> h.items.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList())
    ).apply(i, (schema, items) -> {
        Held h = new Held();
        for (ItemStack s : items) h.items.add(s.copy());
        return h;
    }));
}
