package dev.awaken.tasks;

import dev.awaken.Awaken;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** The Tasks page's registrations: what a player pinned and has seen. */
final class TasksContent {
    private TasksContent() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    static final Supplier<AttachmentType<TaskState>> STATE = ATTACHMENTS.register("tasks",
            () -> AttachmentType.builder(TaskState::fresh).serialize(TaskState.CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
