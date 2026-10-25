package dev.awaken.arms;

import dev.awaken.Awaken;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Arms' registrations: the live fight on a player and the stance of anything struck (neither is saved). */
final class ArmsContent {
    private ArmsContent() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    static final Supplier<AttachmentType<ArmsState>> STATE = ATTACHMENTS.register("arms_state", () -> AttachmentType.builder(ArmsState::new).build());
    static final Supplier<AttachmentType<Posture>> POSTURE = ATTACHMENTS.register("arms_posture", () -> AttachmentType.builder(Posture::new).build());
    /** What a fighter has woven (saved, schemaVersion'd, kept through death). */
    static final Supplier<AttachmentType<ArmsWeaves.ArmsMemory>> MEMORY = ATTACHMENTS.register("arms_memory",
            () -> AttachmentType.builder(ArmsWeaves.ArmsMemory::fresh).serialize(ArmsWeaves.ArmsMemory.CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
