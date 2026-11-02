package dev.awaken.arms.anvil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Where a smith's War Anvil is (player attachment {@code awaken:anvil_smith}, SPEC §46.4; schemaVersion 1, kept through death):
 * the last one they set down or began a forging at, for the Weapons tab's steps. How many weapons they've forged is the
 * progression counter {@code awaken:weapon_forged}.
 */
public final class SmithMemory {
    public static final int SCHEMA = 1;

    @Nullable GlobalPos anvil;
    /** Whether the Hearth's Forge is in reach of that anvil, read at most every two seconds (not saved). */
    long forgeAt = Long.MIN_VALUE;
    boolean forgeNear;

    SmithMemory(@Nullable GlobalPos anvil) {
        this.anvil = anvil;
    }

    static SmithMemory fresh() {
        return new SmithMemory(null);
    }

    static final MapCodec<SmithMemory> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(m -> SCHEMA),
            GlobalPos.CODEC.optionalFieldOf("anvil").forGetter(m -> Optional.ofNullable(m.anvil))
    ).apply(i, (v, a) -> new SmithMemory(a.orElse(null))));

    public static SmithMemory of(ServerPlayer p) {
        return p.getData(AnvilContent.SMITH);
    }

    /** This smith's anvil is here now. */
    void at(ResourceKey<Level> dimension, BlockPos pos) {
        GlobalPos g = GlobalPos.of(dimension, pos.immutable());
        if (g.equals(anvil)) return;
        anvil = g;
        forgeAt = Long.MIN_VALUE;
    }
}
