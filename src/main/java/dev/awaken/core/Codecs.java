package dev.awaken.core;

import com.mojang.serialization.Codec;

/** Shared codecs. */
public final class Codecs {
    private Codecs() {}

    /**
     * An enum codec that never fails: unknown names (a renamed or removed constant) decode to {@code fallback}, so one
     * bad field can't make NeoForge drop a whole saved attachment.
     */
    public static <E extends Enum<E>> Codec<E> lenientEnum(Class<E> type, E fallback) {
        return Codec.STRING.xmap(s -> {
            try {
                return Enum.valueOf(type, s);
            } catch (IllegalArgumentException unknown) {
                return fallback;
            }
        }, Enum::name);
    }
}
