package dev.awaken.wilds.ward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A climb cache's inner seal (SPEC §17.27), kept on the chest's block entity: the best roll's table, and everyone it has
 * opened to. Each climber by the line gets the best roll once; nobody else's open spends it. Schema 1.
 */
public record InnerSeal(int schemaVersion, String best, List<UUID> opened) {
    static final int SCHEMA = 1;
    /** Not a cache (yet): a chest the seal has never stamped. */
    public static final InnerSeal NONE = new InnerSeal(SCHEMA, "", List.of());

    public static final MapCodec<InnerSeal> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(InnerSeal::schemaVersion),
            Codec.STRING.fieldOf("best").forGetter(InnerSeal::best),
            UUIDUtil.CODEC.listOf().optionalFieldOf("opened", List.of()).forGetter(InnerSeal::opened)
    ).apply(i, InnerSeal::new));

    boolean cache() {
        return !best.isEmpty();
    }

    boolean openedTo(UUID who) {
        return opened.contains(who);
    }

    InnerSeal openTo(UUID who) {
        List<UUID> next = new ArrayList<>(opened);
        next.add(who);
        return new InnerSeal(SCHEMA, best, List.copyOf(next));
    }
}
