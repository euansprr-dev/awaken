package dev.awaken.caves.client;

import dev.awaken.caves.skin.CaveSkin;
import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.util.List;

/** The Living Deep on the client (SPEC §42): the puddle takes the cave's water colour; the drips' particles. */
public final class CavesClient {
    private CavesClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class,
                e -> e.register(List.of(BlockTintSources.water()), CaveSkin.PUDDLE.get()));
        modBus.addListener(RegisterParticleProvidersEvent.class, CaveParticles::register);
    }
}
