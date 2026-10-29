package dev.awaken.gates.worlds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The world a gate monster was met in and the look it wears there (attachment {@code awaken:gate_world_look}, SPEC §38.8).
 * Synced to everyone who sees the monster, so a renderer can paint the residents' look (the mobs module's job); the
 * worlds never change a fight. Unset outside a world.
 */
public record WorldLook(String world, String look) {
    public static final int SCHEMA = 1;
    public static final WorldLook NONE = new WorldLook("", "");

    public static final MapCodec<WorldLook> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(l -> SCHEMA),
            Codec.STRING.fieldOf("world").forGetter(WorldLook::world),
            Codec.STRING.optionalFieldOf("look", "").forGetter(WorldLook::look)
    ).apply(i, (schema, world, look) -> new WorldLook(world, look)));

    public static final StreamCodec<ByteBuf, WorldLook> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WorldLook::world, ByteBufCodecs.STRING_UTF8, WorldLook::look, WorldLook::new);

    public boolean isSet() {
        return !world.isEmpty();
    }

    /** A recipe's bare look id ("drowned_vestments") is ours: {@code awaken:drowned_vestments}. */
    static String id(String raw) {
        return raw.isEmpty() || raw.indexOf(':') >= 0 ? raw : "awaken:" + raw;
    }
}
