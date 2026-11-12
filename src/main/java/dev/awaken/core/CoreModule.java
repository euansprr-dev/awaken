package dev.awaken.core;

import dev.awaken.Awaken;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.server.level.TicketType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared registrations (the creative tabs, core sounds) and global services. */
public final class CoreModule {
    private CoreModule() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Awaken.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    public static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, Awaken.MODID);

    /**
     * The land round a teleport's arrival while it's made ({@link dev.awaken.core.api.Passage}, SPEC §28.9): loads, keeps
     * its dimension awake, and lets go 15 s after the land is made (vanilla's portal ticket holds as long).
     */
    public static final DeferredHolder<TicketType, TicketType> ARRIVAL_TICKET = TICKETS.register("arrival",
            () -> new TicketType(300L, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    static {
        // the creative menu's ten tabs (SPEC §1, "Creative tabs"): modules fill their shelves through core.api.Tabs
        dev.awaken.core.api.Tabs.register(TABS);
    }

    /**
     * The one tab Awaken had before the menu was split (11 Nov 2026). No tab has this key any more, so a listener that
     * still checks for it adds nothing: its items land on Materials' Unsorted shelf and the tabs check names them. Fill a
     * shelf with {@link dev.awaken.core.api.Tabs#fill} instead.
     */
    @Deprecated
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Awaken.id("awaken"));

    public static final DeferredHolder<SoundEvent, SoundEvent> SIGNAL_CHIME = sound("signal.chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGNAL_OPEN = sound("signal.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGNAL_WARNING = sound("signal.warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = sound("level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANK_UP = sound("rank_up");
    /** The edge hold's headwind (SPEC §28.9; the elytra's own wind by name: tools/art/specs/audit_world.py, SPEC §66.5). */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGE_HEADWIND = sound("edge.headwind");

    public static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    public static void init(IEventBus modBus) {
        TABS.register(modBus);
        SOUNDS.register(modBus);
        TICKETS.register(modBus);
        Forces.init(modBus);
        MotionTags.init(modBus);
        dev.awaken.core.test.AwakenTests.init(modBus);
        dev.awaken.core.api.CoreApi.init(modBus);
        NeoForge.EVENT_BUS.addListener(PlayDays::onServerTick);
        NeoForge.EVENT_BUS.addListener(AwakenCommands::onRegister);
        // "/awaken core tabs": the creative tabs' check (SPEC §1, "Creative tabs"), to chat and the log
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("core").requires(AwakenCommands.admin())
                .then(Commands.literal("tabs").executes(c -> {
                    var level = c.getSource().getLevel();
                    var report = dev.awaken.core.api.Tabs.audit(
                            new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
                    Awaken.LOG.info("[core] creative tabs: {}", report.summary());
                    c.getSource().sendSuccess(() -> Component.literal("Creative tabs: " + report.summary()), false);
                    return report.clean() ? 1 : 0;
                }))
                // "/awaken core land": the chunks in view not made yet, and whether land is coming in (SPEC §12, land first)
                .then(Commands.literal("land").executes(c -> {
                    String line = dev.awaken.core.api.LandFirst.missing() + " chunks in view not made yet"
                            + (dev.awaken.core.api.LandFirst.streaming() ? ", land coming in" : "");
                    Awaken.LOG.info("[core] land: {}", line);
                    c.getSource().sendSuccess(() -> Component.literal(line), false);
                    return 1;
                }))));
        modBus.addListener(CorePayloads::register);
        Playtest.init();
        NeoForge.EVENT_BUS.addListener(Later::tick);
        NeoForge.EVENT_BUS.addListener(Later::stopping);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> { DirectorLog.clear(); Playtest.clear(); Throttle.clear(); RegionRecycler.clear(); dev.awaken.core.api.Passage.clear(); dev.awaken.core.api.LandFirst.clear(); });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> { RegionRecycler.tick(e.getServer()); dev.awaken.core.api.Passage.tick(e.getServer()); dev.awaken.core.api.LandFirst.tick(e.getServer()); });
        // land comes first (SPEC §12): a chunk made or read means the generator is at work
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load e) -> { if (!e.getLevel().isClientSide()) dev.awaken.core.api.LandFirst.chunkLoaded(); });
        NeoForge.EVENT_BUS.addListener(dev.awaken.core.api.Passage::hurt);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand.class,
                dev.awaken.core.api.Passage::commanded);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, net.neoforged.neoforge.event.entity.EntityTeleportEvent.SpreadPlayersCommand.class,
                dev.awaken.core.api.Passage::commanded);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> Throttle.forget(e.getEntity().getUUID()));
    }
}
