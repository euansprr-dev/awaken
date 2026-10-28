package dev.awaken.core;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared registrations (creative tab, core sounds) and global services. */
public final class CoreModule {
    private CoreModule() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Awaken.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** Icon for the shared creative tab; modules may replace it at init with a better stack supplier. */
    public static java.util.function.Supplier<ItemStack> tabIcon = () -> new ItemStack(Items.AMETHYST_SHARD);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("awaken", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.awaken"))
            .icon(() -> tabIcon.get())
            .build());

    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Awaken.id("awaken"));

    public static final DeferredHolder<SoundEvent, SoundEvent> SIGNAL_CHIME = sound("signal.chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGNAL_OPEN = sound("signal.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGNAL_WARNING = sound("signal.warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = sound("level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANK_UP = sound("rank_up");
    /** The edge hold's headwind (SPEC §28.9; its file from tools/art/specs/audio_edge_hold.py, merge/core_edge). */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGE_HEADWIND = sound("edge.headwind");

    public static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    public static void init(IEventBus modBus) {
        TABS.register(modBus);
        SOUNDS.register(modBus);
        Forces.init(modBus);
        dev.awaken.core.test.AwakenTests.init(modBus);
        dev.awaken.core.api.CoreApi.init(modBus);
        NeoForge.EVENT_BUS.addListener(PlayDays::onServerTick);
        NeoForge.EVENT_BUS.addListener(AwakenCommands::onRegister);
        modBus.addListener(CorePayloads::register);
        Playtest.init();
        NeoForge.EVENT_BUS.addListener(Later::tick);
        NeoForge.EVENT_BUS.addListener(Later::stopping);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> { DirectorLog.clear(); Playtest.clear(); Throttle.clear(); RegionRecycler.clear(); });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> RegionRecycler.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> Throttle.forget(e.getEntity().getUUID()));
    }
}
