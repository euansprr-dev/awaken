package dev.awaken.trails;

import dev.awaken.Awaken;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Trails' registrations: the saved state of what a player has opened and followed. */
final class TrailsContent {
    private TrailsContent() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    static final Supplier<AttachmentType<TrailState>> STATE = ATTACHMENTS.register("trails",
            () -> AttachmentType.builder(TrailState::fresh).serialize(TrailState.CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
