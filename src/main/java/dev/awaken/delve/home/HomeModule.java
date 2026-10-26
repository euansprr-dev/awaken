package dev.awaken.delve.home;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.core.api.NowHooks;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * A way home underground (SPEC §28.5; PLAN 8.4), the vents in lava tunnels and sulfur caves, and the Descent's tunnels'
 * Deepglass and small grottos (PLAN 8.5; written by {@code descent.world.Terrain}).
 *
 * <p>The way home: the server keeps each player's trail ({@link HomeTrail}, sampled by {@link HomeServer}), the Now
 * line shows "Surface ↑ 34 · NE 60" when you're lost underground, and the Signal's thread runs back along the path you
 * came by ({@code delve.client.HomeClient}). The vents: {@link VentFeature} ({@code awaken:delve/vents}, biome modifier
 * {@code delve_vents} at {@code underground_decoration}).
 */
public final class HomeModule {
    private HomeModule() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);

    /** The way you came, saved small (it's empty, and not saved, until you've stood under the sky). */
    public static final Supplier<AttachmentType<HomeTrail>> TRAIL = ATTACHMENTS.register("delve_home",
            () -> AttachmentType.builder(HomeTrail::new).serialize(HomeTrail.MAP_CODEC, t -> !t.isEmpty()).build());

    static {
        FEATURES.register("delve_vents", () -> VentFeature.CODEC);
    }

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(HomeNet::register);
        NowHooks.register(HomeServer::suggest);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) HomeServer.tick(p);
        });
        HomeCommands.register();
        HomeTests.register();
    }
}
