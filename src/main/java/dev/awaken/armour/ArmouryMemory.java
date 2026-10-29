package dev.awaken.armour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a smith knows (player attachment {@code awaken:armoury}, SPEC §44.7; schemaVersion 1, kept through death): armour
 * patterns found (the Hunter sets' come with rank and need none), sets forged whole on a Fitting Stand (the System names a
 * set the first time), and pieces made at Masterwork or better ("set:slot", which can then be forged from memory).
 */
public final class ArmouryMemory {
    public static final int SCHEMA = 1;

    final Set<String> patterns, forged, masterwork;
    /** The Now line, worked out at most once a second (not saved). */
    long nowAt = Long.MIN_VALUE;
    List<dev.awaken.core.api.NowHooks.Suggestion> now = List.of();

    ArmouryMemory(Set<String> patterns, Set<String> forged, Set<String> masterwork) {
        this.patterns = new HashSet<>(patterns);
        this.forged = new HashSet<>(forged);
        this.masterwork = new HashSet<>(masterwork);
    }

    static ArmouryMemory fresh() {
        return new ArmouryMemory(Set.of(), Set.of(), Set.of());
    }

    static final MapCodec<ArmouryMemory> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(m -> SCHEMA),
            Codec.STRING.listOf().optionalFieldOf("patterns", List.of()).forGetter(m -> List.copyOf(m.patterns)),
            Codec.STRING.listOf().optionalFieldOf("forged", List.of()).forGetter(m -> List.copyOf(m.forged)),
            Codec.STRING.listOf().optionalFieldOf("masterwork", List.of()).forGetter(m -> List.copyOf(m.masterwork))
    ).apply(i, (v, p, f, m) -> new ArmouryMemory(Set.copyOf(p), Set.copyOf(f), Set.copyOf(m))));

    public static ArmouryMemory of(ServerPlayer p) {
        return p.getData(ArmourContent.ARMOURY);
    }

    static String key(ArmourSet set, ArmorType piece) {
        return set.id + ":" + ForgeSong.slot(piece);
    }

    public boolean fromMemory(ArmourSet set, ArmorType piece) {
        return masterwork.contains(key(set, piece));
    }

    public boolean forgedWhole(ArmourSet set) {
        return forged.contains(set.id);
    }
}
