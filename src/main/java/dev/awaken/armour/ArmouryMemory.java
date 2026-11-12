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
 * What a smith knows (player attachment {@code awaken:armoury}, SPEC §44.7; schemaVersion 3, kept through death): armour
 * patterns found (the Hunter sets' come with rank and need none), sets forged whole on a Fitting Stand (the System names a
 * set the first time), pieces made at Masterwork or better ("set:slot", which can then be forged from memory), (schema
 * 3) the sets worn whole, so the System says its one line the first time only (§44.11), and (schema 4) the best rank
 * you have worn head to foot, so the Tasks page's armour step stays done when the suit comes off (§27.14).
 */
public final class ArmouryMemory {
    public static final int SCHEMA = 4;

    final Set<String> patterns, forged, masterwork;
    /** Sets worn whole at least once (set ids). Schema 3; an older save reads it empty. */
    final Set<String> worn = new HashSet<>();
    /** The best rank (ordinal) of a suit worn head to foot, every piece that rank or better, mixed sets too; -1: none. Schema 4. */
    int suited = -1;
    /** The suit the Tasks page is following (a set id; empty: none). Schema 2. */
    String tracked = "";
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
            Codec.STRING.listOf().optionalFieldOf("masterwork", List.of()).forGetter(m -> List.copyOf(m.masterwork)),
            Codec.STRING.optionalFieldOf("tracked", "").forGetter(m -> m.tracked),
            Codec.STRING.listOf().optionalFieldOf("worn", List.of()).forGetter(m -> List.copyOf(m.worn)),
            Codec.INT.optionalFieldOf("suited", -1).forGetter(m -> m.suited)
    ).apply(i, (v, p, f, m, t, w, su) -> {
        ArmouryMemory out = new ArmouryMemory(Set.copyOf(p), Set.copyOf(f), Set.copyOf(m));
        out.tracked = t;
        out.worn.addAll(w);
        out.suited = su;
        return out;
    }));

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

    public boolean wornWhole(ArmourSet set) {
        return worn.contains(set.id);
    }
}
