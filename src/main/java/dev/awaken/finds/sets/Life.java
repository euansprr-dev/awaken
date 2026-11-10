package dev.awaken.finds.sets;

import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/** The game's own creatures by id, for the finds that have one living at them; the fallback when one isn't there. */
final class Life {
    private Life() {}

    static EntityType<?> type(String id, EntityType<?> fallback) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Awaken.id(id)).<EntityType<?>>map(t -> t).orElse(fallback);
    }
}
