package dev.awaken.wilds.arrival;

import com.mojang.serialization.MapCodec;
import dev.awaken.core.api.ArrivalHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Set;

/**
 * Loot condition {@code awaken:arrived_by_movement}: true when the player opening the chest got to it by movement
 * ({@link ArrivalHooks#byMovement}). A site's best roll is a pool behind it, so a tower still reaches the chest, just not
 * the prize (SPEC §17). No player (a hopper, an explosion): false.
 */
public final class ArrivedByMovement implements LootItemCondition {
    private static final ArrivedByMovement INSTANCE = new ArrivedByMovement();
    public static final MapCodec<ArrivedByMovement> MAP_CODEC = MapCodec.unit(INSTANCE);

    private ArrivedByMovement() {}

    @Override
    public MapCodec<ArrivedByMovement> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        return context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer p && ArrivalHooks.byMovement(p);
    }
}
