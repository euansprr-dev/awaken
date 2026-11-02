package dev.awaken.arms.anvil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.arms.ArmsArmoury;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A smith's anvil and what they've made on it (player attachment {@code awaken:anvil_smith}, SPEC §46.4, §46.7;
 * schemaVersion 2, kept through death): the last War Anvil they set down or began a forging at, for the Weapons tab's
 * steps; the weapons they've forged Masterwork or better ("daggers:D"), which they can make from memory; the styles
 * they've forged; and the highest tier they've forged (-1 none). A v1 save reads with all three empty. How many weapons
 * they've forged is the progression counter {@code awaken:weapon_forged}.
 */
public final class SmithMemory {
    public static final int SCHEMA = 2;

    @Nullable GlobalPos anvil;
    final Set<String> masterwork, forged;
    int best;
    /** Whether the Hearth's Forge is in reach of that anvil, where, and its Temper limit for them, read at most every two seconds (not saved). */
    long forgeAt = Long.MIN_VALUE;
    boolean forgeNear;
    @Nullable BlockPos forgePos;
    int forgeCap = 1;
    /** Read with the Forge: the anvil's build (-1 unknown, its chunk not loaded) and whether a grindstone is in reach (§46.6). */
    int build = -1;
    boolean grindNear;

    SmithMemory(@Nullable GlobalPos anvil, Set<String> masterwork, Set<String> forged, int best) {
        this.anvil = anvil;
        this.masterwork = new HashSet<>(masterwork);
        this.forged = new HashSet<>(forged);
        this.best = best;
    }

    static SmithMemory fresh() {
        return new SmithMemory(null, Set.of(), Set.of(), -1);
    }

    static final MapCodec<SmithMemory> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(m -> SCHEMA),
            GlobalPos.CODEC.optionalFieldOf("anvil").forGetter(m -> Optional.ofNullable(m.anvil)),
            Codec.STRING.listOf().optionalFieldOf("masterwork", List.of()).forGetter(m -> List.copyOf(m.masterwork)),
            Codec.STRING.listOf().optionalFieldOf("forged", List.of()).forGetter(m -> List.copyOf(m.forged)),
            Codec.INT.optionalFieldOf("best", -1).forGetter(m -> m.best)
    ).apply(i, (v, a, mw, f, b) -> new SmithMemory(a.orElse(null), Set.copyOf(mw), Set.copyOf(f), b)));

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

    /** Whether this weapon is in their memory: forged Masterwork or better before. */
    public boolean remembers(String style, int tier) {
        return tier >= 0 && tier < ArmsArmoury.TIERS.length && masterwork.contains(style + ":" + ArmsArmoury.TIERS[tier]);
    }

    public int best() {
        return best;
    }

    public boolean hasForged(String style) {
        return forged.contains(style);
    }

    /** A weapon forged here (not a blade alone): its style, its tier, and into memory if Masterwork or better. */
    void forged(String style, int tier, AnvilSong.Quality q) {
        forged.add(style);
        best = Math.max(best, tier);
        if (q.ordinal() >= AnvilSong.Quality.MASTERWORK.ordinal()) masterwork.add(style + ":" + ArmsArmoury.TIERS[tier]);
    }
}
