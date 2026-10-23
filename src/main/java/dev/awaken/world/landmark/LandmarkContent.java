package dev.awaken.world.landmark;

import dev.awaken.Awaken;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** What the landmarks register: the player's memory of them. */
public final class LandmarkContent {
    private LandmarkContent() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Supplier<AttachmentType<LandmarkMemory>> MEMORY = ATTACHMENTS.register("landmark_memory", () -> AttachmentType
            .builder(LandmarkMemory::new).serialize(LandmarkMemory.MAP_CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
