package dev.awaken.village;

import dev.awaken.Awaken;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.core.api.EconomyHooks;
import dev.awaken.core.audio.Intensity;
import dev.awaken.core.audio.MusicLayers;
import dev.awaken.village.api.Villages;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.VillageWorldgen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Living villages (Between the Gates W3; SPEC §28.6, docs/modules/village.md): AWAKEN's own hamlets and villages in
 * new worlds (vanilla's switched off by the built-in Awakened Villages pack), planned in code on the settlement grid,
 * painted by the city's grammar with interiors, and lived in by the people engine's settlements: households and village
 * jobs, speech, a soundscape, trade in emeralds, requests on the notice board, standing, a bed at the inn and a scribe.
 * Saved state and the {@code Villages} API (§12) are what W4's troubles build on.
 */
public final class VillageModule {
    private VillageModule() {}

    public static void init(IEventBus modBus) {
        VillageWorldgen.init(modBus);
        // (SPEC §21.10) settled land and the roads between are calmer: creatures there are held down or a few levels lower
        dev.awaken.living.api.Regions.addLevelExtra(SettledCalm::roadExtra);
        dev.awaken.living.api.Regions.addLevelCap(SettledCalm::cap);
        // (SPEC §41.1) and tamed: the city, settlements, towns and roads take the wild out of the land round them
        WildTamers.register();
        dev.awaken.village.road.RoadAtlas.init(modBus);
        // (SPEC §28.9, Turbo) a lattice chunk waiting for its roads frees the overworld's world generation meanwhile
        dev.awaken.turbo.roadstarts.RoadStarts.init(modBus);
        dev.awaken.turbo.packetcap.PacketCap.init(modBus); // Turbo: the server's packets get a tick's time (SPEC §28.9)
        VillageContent.register(modBus);
        modBus.addListener(VillageModule::onAddPackFinders);
        modBus.addListener(VillageNet::register);
        // after terrain has chosen the city's site (default priority): plans cleared, the first hamlet's spot found
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, (ServerAboutToStartEvent e) -> {
            Plans.clear();
            dev.awaken.village.road.RoadNet.clear();
            dev.awaken.village.road.RoadWorld.close();
            if (VillageService.enabled(e.getServer())) {
                dev.awaken.village.world.FirstHamlet.find(e.getServer());
                // (SPEC §28.9) the roads read the same land, the region map's hearts and the first town from here on
                dev.awaken.village.road.RoadWorld.open(e.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            dev.awaken.village.road.RoadNet.stopAhead();
            Plans.clear();
            dev.awaken.village.road.RoadNet.clear();
            dev.awaken.village.road.RoadWorld.close();
            VillageService.serverStopped();
            Holdfast.serverStopped();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> VillageService.tick(e.getServer()));
        // (SPEC §43.2) the holdfasts' bells, and the Ember refused where one is heard
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> Holdfast.tick(e.getServer()));
        dev.awaken.core.api.TheLull.installHoldfasts((level, at) -> level.dimension() == Level.OVERWORLD
                && !dev.awaken.village.road.Holdfasts.near(at.getX(), at.getZ(), dev.awaken.core.api.TheLull.HOLDFAST_BELL, true).isEmpty());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> dev.awaken.village.road.RoadAtlas.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> dev.awaken.village.road.RoadNet.prefetch(e.getServer()));
        // (SPEC §28.9, Cost) a chunk the server thread had to wait for far from every player: who asked
        NeoForge.EVENT_BUS.addListener(dev.awaken.village.road.RoadAccess::chunkLoaded);
        // a player changing level stands on nothing the old one held: its first tick reads only where it is
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, dev.awaken.village.road.Arrivals::changed);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ServerTickEvent.Pre e) -> dev.awaken.village.road.RoadAccess.serverTick(true));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerTickEvent.Post e) -> dev.awaken.village.road.RoadAccess.serverTick(false));
        NeoForge.EVENT_BUS.addListener(Inn::onRespawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, VillageModule::onBroken);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, VillageModule::onExploded);
        NeoForge.EVENT_BUS.addListener(VillageModule::onRightClickBlock);
        // (SPEC §28.7) a village's trouble answers first: the parent in the square, the elder, the smith casting a bell
        dev.awaken.village.trouble.TroubleDirector.init(modBus);
        Settlements.onTalk(dev.awaken.village.trouble.TroubleDirector::talk);
        Settlements.onTalk(VillageService::talk);
        Settlements.installVoice(new VillageService.Voice());
        EconomyHooks.install(new Emeralds());
        Villages.install(new VillagesAccess());
        // (SPEC §28.9) the roads for the World Pulse's caravans
        dev.awaken.core.api.RoadHooks.install(new dev.awaken.village.road.RoadAccess());
        dev.awaken.village.road.RoadAccess.installHearths();
        MusicLayers.register(MusicLayers.Slot.AMBIENCE, 90, "village", VillageModule::soundscape);
        VillageCommands.register();
        VillageTests.register();
    }

    private static void onAddPackFinders(AddPackFindersEvent e) {
        e.addPackFinders(Awaken.id("datapacks/awakened_villages"), PackType.SERVER_DATA, Component.literal("Awakened Villages"), PackSource.BUILT_IN,
                false, Pack.Position.TOP);
    }

    /** Part of a village broken: the block that stood there is kept (W4's Rebuild), and it was seen. */
    private static void onBroken(BreakBlockEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        VillageService.broke(level, e.getPos().immutable(), e.getState(), e.getPlayer() instanceof ServerPlayer sp ? sp : null);
    }

    private static void onExploded(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || VillageService.LIVE.isEmpty()) return;
        for (var pos : e.getAffectedBlocks()) {
            var s = level.getBlockState(pos);
            if (!s.isAir()) VillageService.broke(level, pos.immutable(), s, null);
        }
    }

    /**
     * The notice board: its notices. (SPEC §43.1) An empty settlement's are years old; a holdfast's are its own people's,
     * never tasks (every goal comes from the System).
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        var state = level.getBlockState(e.getPos());
        if (!state.is(dev.awaken.city.kit.StreetKit.NOTICE_BOARD.get())) return;
        VillageService.Live l = VillageService.at(level, e.getPos());
        String line = l == null ? (Villages.reserved(level, e.getPos(), 0) ? EMPTY_BOARD : null)
                : dev.awaken.village.road.Holdfasts.isKey(l.key()) ? HOLDFAST_BOARD : null;
        if (line != null) {
            dev.awaken.signal.Signal.send(p, dev.awaken.signal.SignalWindow.builder(dev.awaken.signal.SignalWindow.Kind.NOTICE, Component.literal(line))
                    .ttl(20 * 8).build());
            e.setCanceled(true);
            return;
        }
        if (l == null) return;
        VillageNet.openBoard(p, l);
        e.setCanceled(true);
    }

    static final String EMPTY_BOARD = "[The notices are years old. The last one says the chime has stopped and they're all going to the city.]";
    static final String HOLDFAST_BOARD = "[The board has the bell's shift list and a few notes they've left each other. Nothing on it is for you.]";

    /**
     * The ambience slot in a village (below Carillon's): the day's bed (birds over a murmur that grows with the people
     * out of doors) or the night's (crickets, and the inn's voices till late where there is one).
     */
    private static @Nullable Identifier soundscape(ServerPlayer p, Intensity.Signals s) {
        if (p.level().dimension() != Level.OVERWORLD) return null;
        VillageService.Live l = VillageService.at(p.level(), p.blockPosition());
        if (l == null) return null;
        int tod = (int) Math.floorMod(p.level().getOverworldClockTime(), 24000L);
        boolean night = tod >= 12500 && tod < 23300;
        boolean nature = dev.awaken.citizens.Soundscape.nature(p);
        if (night) {
            boolean inn = l.plan().buildings.stream().anyMatch(b -> b.use().equals("inn"));
            s.forced = !inn ? Intensity.CALM : tod < 14500 ? Intensity.COMBAT : tod < 16500 ? Intensity.TENSION : Intensity.CALM;
            return Awaken.id(nature ? "village/as/night" : "village/night");
        }
        int out = l.settlement().bodies();
        s.forced = out < 3 ? Intensity.CALM : out < 8 ? Intensity.TENSION : out < 16 ? Intensity.COMBAT : Intensity.LORD;
        if (p.level().isRainingAt(p.blockPosition().above())) s.forced = Intensity.CALM;
        return Awaken.id(nature ? "village/as/day" : "village/day");
    }
}
