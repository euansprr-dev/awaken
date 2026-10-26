package dev.awaken.holo.band;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's wrist (SPEC §16.4, "The Registry ceremony"). {@code fitted}: this player has the wrist slot (they were
 * handed a band, or the opening took the Holo away to hand it over); without it the Holo is always on, as it was before
 * the ceremony (classic worlds, old saves). {@code wrist}: the band worn there. {@code kept}: bands that were in the
 * inventory at a death, handed back on respawn (the band never drops). Saved (schemaVersion 1), kept through death,
 * synced to whoever can see the player (the wrist is visible).
 */
public final class BandState {
    public static final int SCHEMA = 1;

    boolean fitted;
    ItemStack wrist = ItemStack.EMPTY;
    final List<ItemStack> kept = new ArrayList<>();

    public boolean fitted() {
        return fitted;
    }

    public ItemStack wrist() {
        return wrist;
    }

    /** The Holo works: a band on the wrist, or no wrist slot at all. */
    public boolean worn() {
        return !fitted || !wrist.isEmpty();
    }

    public static final Codec<BandState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.BOOL.optionalFieldOf("fitted", false).forGetter(s -> s.fitted),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("wrist", ItemStack.EMPTY).forGetter(s -> s.wrist),
            ItemStack.CODEC.listOf().optionalFieldOf("kept", List.of()).forGetter(s -> s.kept)
    ).apply(i, (schema, fitted, wrist, kept) -> {
        BandState s = new BandState();
        s.fitted = fitted;
        s.wrist = wrist;
        s.kept.addAll(kept);
        return s;
    }));

    /** What the client needs: the slot and what's in it (never the kept bands). */
    public static final StreamCodec<RegistryFriendlyByteBuf, BandState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, s -> s.fitted,
            ItemStack.OPTIONAL_STREAM_CODEC, s -> s.wrist,
            (fitted, wrist) -> {
                BandState s = new BandState();
                s.fitted = fitted;
                s.wrist = wrist;
                return s;
            });
}
