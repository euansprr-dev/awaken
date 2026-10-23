package dev.awaken.hearth.request;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registrations of the request part of the hearth module: the player's state, the Power Core Shard, two sounds. */
public final class RequestContent {
    private RequestContent() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** Requests offered and taken, the streak, reputation and Forge credit. Saved, kept on death, synced to its owner. */
    public static final Supplier<AttachmentType<RequestState>> STATE = ATTACHMENTS.register("hearth_requests", () -> AttachmentType
            .builder(RequestState::new).serialize(RequestState.MAP_CODEC).copyOnDeath()
            .sync((holder, to) -> holder == to, RequestState.STREAM_CODEC).build());

    /** A quarter of a Power Core: four fuse into one (use them). */
    public static final DeferredItem<CoreShardItem> CORE_SHARD = ITEMS.registerItem("core_shard", CoreShardItem::new,
            p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<SoundEvent, SoundEvent> SND_DELIVER = sound("hearth.request_deliver");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_COMPLETE = sound("hearth.request_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_FUSE = sound("hearth.core_fuse");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(RequestContent::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(CoreModule.TAB_KEY)) e.accept(CORE_SHARD.get());
    }
}
