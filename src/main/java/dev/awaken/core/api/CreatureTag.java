package dev.awaken.core.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the band reads off a creature (SPEC §21.1): its level, what kind of creature it is, whether it's an elite, and a
 * title for the few that have one ("Terror of Ashfen"). Saved and synced to everyone tracking the creature (attachment
 * {@code awaken:creature_tag}); the mobs module writes it for its creatures, the living module for vanilla ones and
 * the titles. Absent ({@link #NONE}) means the band has nothing to say.
 */
public record CreatureTag(int level, Kind kind, boolean elite, String title) {
    /** PASSIVE: animals and the like; MONSTER: vanilla hostiles; GATE: a gate's normal creature. */
    public enum Kind {
        NONE, PASSIVE, AMBIENT, NEUTRAL, HUNTER, MONSTER, GATE, WARDEN, BOSS, BEAST, CAMP;
        public static final Codec<Kind> CODEC = dev.awaken.core.Codecs.lenientEnum(Kind.class, NONE);

        /** Whether a creature of this kind means harm by default (the plate's warm rim). */
        public boolean hostile() {
            return this == HUNTER || this == MONSTER || this == GATE || this == WARDEN || this == BOSS || this == CAMP;
        }
    }

    public static final CreatureTag NONE = new CreatureTag(0, Kind.NONE, false, "");

    public static final Codec<CreatureTag> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("level").forGetter(CreatureTag::level),
            Kind.CODEC.fieldOf("kind").forGetter(CreatureTag::kind),
            Codec.BOOL.optionalFieldOf("elite", false).forGetter(CreatureTag::elite),
            Codec.STRING.optionalFieldOf("title", "").forGetter(CreatureTag::title)
    ).apply(i, CreatureTag::new));

    public static final StreamCodec<ByteBuf, CreatureTag> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreatureTag::level,
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), CreatureTag::kind,
            ByteBufCodecs.BOOL, CreatureTag::elite,
            ByteBufCodecs.stringUtf8(64), CreatureTag::title,
            CreatureTag::new);

    public boolean present() {
        return kind != Kind.NONE;
    }

    public CreatureTag withTitle(String t) {
        return new CreatureTag(level, kind, elite, t);
    }

    /** The tag of an entity without creating an attachment on it. */
    public static CreatureTag of(net.minecraft.world.entity.Entity e) {
        return e.getExistingData(CoreApi.CREATURE_TAG).orElse(NONE);
    }
}
