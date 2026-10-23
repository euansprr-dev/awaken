package dev.awaken.core.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * What a spawned AWAKEN mob is for. Saved on the entity (attachment {@code awaken:mob_role}). Gates use it to count
 * floor kills, Hearth uses it to decide what can be bound as an Echo, trophies come from BOSS.
 */
public record MobRole(Role role, int contentLevel, String theme, Optional<String> bossId, String gateId) {
    public enum Role { NONE, NORMAL, ELITE, WARDEN, BOSS, BREAK, ECHO, NIGHTMARE, SPIRIT;
        public static final Codec<Role> CODEC = dev.awaken.core.Codecs.lenientEnum(Role.class, NONE); }

    public static final MobRole NONE = new MobRole(Role.NONE, 0, "", Optional.empty(), "");

    public static final Codec<MobRole> CODEC = RecordCodecBuilder.create(i -> i.group(
            Role.CODEC.fieldOf("role").forGetter(MobRole::role),
            Codec.INT.fieldOf("level").forGetter(MobRole::contentLevel),
            Codec.STRING.fieldOf("theme").forGetter(MobRole::theme),
            Codec.STRING.optionalFieldOf("boss").forGetter(MobRole::bossId),
            Codec.STRING.optionalFieldOf("gate", "").forGetter(MobRole::gateId)
    ).apply(i, MobRole::new));

    /** The role of an entity without creating an attachment on it. */
    public static MobRole of(net.minecraft.world.entity.Entity e) {
        return e.getExistingData(CoreApi.MOB_ROLE).orElse(NONE);
    }

    public boolean isBindable() {
        return role == Role.ELITE || role == Role.WARDEN || role == Role.BOSS;
    }
}
