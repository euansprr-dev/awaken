package dev.awaken.binoculars;

import dev.awaken.Awaken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Binoculars (SPEC §39), common half: the {@code awaken:binoculars} relay that lets others see yours up, the sounds, and
 * the display-only {@code awaken:binoculars} item the hands and other players' models draw (in no loot, recipe or tab:
 * you always have them, on a key). Everything you see through them is client side ({@code binoculars.client}); the
 * side-agnostic rules it follows ({@link GlassRules}, {@link GlassKey}, {@link GlassState}, {@link GlassSpring},
 * {@link GlassFeel}) are checked by GameTests.
 */
public final class BinocularsModule {
    private BinocularsModule() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    /** Drawn in the hands and at other players' eyes; never given to anyone. */
    public static final DeferredItem<Item> BINOCULARS = ITEMS.registerSimpleItem("binoculars", p -> p.stacksTo(1));

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        BinocularsSounds.register(modBus);
        modBus.addListener(BinocularsNet::register);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> BinocularsRelay.flush(e.getServer(), BinocularsRelay.SEND));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, e -> {
            if (e.getEntity() instanceof ServerPlayer tracker) BinocularsRelay.onStartTracking(tracker, e.getTarget());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) BinocularsRelay.forget(p);
        });
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) BinocularsRelay.reset(p, BinocularsRelay.SEND);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) BinocularsRelay.reset(p, BinocularsRelay.SEND);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) BinocularsRelay.reset(p, BinocularsRelay.SEND);
        });
        BinocularsTests.register();
    }
}
