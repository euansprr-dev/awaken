package dev.awaken.core.api;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Shared attachments and materials every module may use. */
public final class CoreApi {
    private CoreApi() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    public static final Supplier<AttachmentType<MobRole>> MOB_ROLE = ATTACHMENTS.register("mob_role",
            () -> AttachmentType.builder(() -> MobRole.NONE).serialize(MobRole.CODEC.fieldOf("role"), r -> r.role() != MobRole.Role.NONE).build());

    /** Gate currency: dropped in gates and breaks, spent by the Hearth and the Above. */
    public static final DeferredItem<Item> ESSENCE_SHARD = ITEMS.registerSimpleItem("essence_shard");
    /** Dropped by every Gate Lord. The first one crafts the Hearth Anchor. */
    public static final DeferredItem<Item> LORD_HEART = ITEMS.registerSimpleItem("lord_heart", p -> p.rarity(Rarity.RARE));

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CoreApi::creativeTab);
        CoreModule.tabIcon = () -> LORD_HEART.get().getDefaultInstance();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CoreModule.TAB_KEY)) {
            event.accept(ESSENCE_SHARD.get());
            event.accept(LORD_HEART.get());
        }
    }
}
