package dev.awaken.storage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Storage (SPEC §56): the one list of sorts, Tidy, Undo and Pins. */
public final class StorageModule {
    private StorageModule() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    static final Supplier<AttachmentType<BagPins>> PINS = ATTACHMENTS.register("bag_pins",
            () -> AttachmentType.builder(() -> BagPins.EMPTY).serialize(BagPins.MAP_CODEC, p -> p.mask() != 0 || p.hinted()).copyOnDeath().build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(StorageNet::register);
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(Sorts::onTags);
        bus.addListener((PlayerContainerEvent.Close e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Tidying.closed(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) StorageNet.syncPins(p);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) StorageNet.syncPins(p);
        });
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("storage")
                .then(Commands.literal("pin").then(Commands.argument("slot", IntegerArgumentType.integer(9, 35)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Tidying.pinToggle(p, IntegerArgumentType.getInteger(c, "slot"));
                    StorageNet.syncPins(p);
                    return 1;
                }))))));
        StorageTests.register();
    }
}
