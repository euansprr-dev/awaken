package dev.awaken.veil;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/** Saved on a mannequin that is someone's Body. An orphaned Body (its owner is not out of it) is removed on load. */
public record BodyTag(UUID owner) {
    public static final UUID NOBODY = new UUID(0L, 0L);
    public static final BodyTag NONE = new BodyTag(NOBODY);

    public static final MapCodec<BodyTag> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(BodyTag::owner)
    ).apply(i, BodyTag::new));

    public boolean isBody() {
        return !NOBODY.equals(owner);
    }
}
