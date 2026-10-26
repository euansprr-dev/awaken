package dev.awaken.city;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.core.api.CityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Carillon, the walled city at spawn (docs/SPEC.md §16; Milestone 1, the city in blockout): the authored plan, the
 * per-chunk painter and its grammar and landmarks, pregeneration at world creation, spawn on the Runners' Roost, the
 * street and roof graphs, civic protection and {@code CityHooks}.
 *
 * <p>New worlds get the built-in "Carillon" data pack, which asks for the feature flag {@code awaken:carillon} and
 * places the city feature; switching that pack off on the Data Packs screen is the Classic start. Worlds made before
 * it existed never get the flag, so they never change.
 */
public final class CityModule {
    private CityModule() {}

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
            DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);

    private static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, Awaken.MODID);

    /** Keeps a pregenerated city chunk loaded until the player's own tickets take over (CityPregen). */
    static final DeferredHolder<TicketType, TicketType> HOLD_TICKET =
            TICKET_TYPES.register("city_hold", () -> new TicketType(CityPregen.HOLD_TICKS, TicketType.FLAG_LOADING));

    static {
        FEATURE_TYPES.register("city", () -> CityFeature.CODEC);
    }

    /** The built-in pack's id in the pack repository. */
    static final String PACK_ID = "mod/awaken:datapacks/carillon";

    public static void init(IEventBus modBus) {
        FEATURE_TYPES.register(modBus);
        TICKET_TYPES.register(modBus);
        dev.awaken.city.kit.CityKit.register(modBus);
        modBus.addListener(CityModule::onAddPackFinders);
        CityProtection.registerConfig();
        City.init();
        NeoForge.EVENT_BUS.addListener(CitySpawn::onCreateSpawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CityMonsters::onPositionCheck); // few monsters near the city (the Commons step)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CityProtection::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CityProtection::onPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CityProtection::onUse);
        NeoForge.EVENT_BUS.addListener(CityProtection::onExplode);
        CityHooks.install(new Hooks());
        CityCommands.register();
        CityTests.register();
        CityWallTests.register();
        CityCommonsTests.register();
        // (M4 B3a) lit windows follow the time of day
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
            CityLights.tick(e.getServer());
            CityYard.tick(e.getServer()); // the High Gate's yard stays clear ground (decision 29)
        });
    }

    private static void onAddPackFinders(AddPackFindersEvent e) {
        e.addPackFinders(Awaken.id("datapacks/carillon"), PackType.SERVER_DATA, Component.translatable("awaken.city.pack"),
                PackSource.BUILT_IN, false, Pack.Position.TOP);
    }

    /** {@code CityHooks} (SPEC §16.9): what other modules may ask about the city. */
    private static final class Hooks implements CityHooks.Access {
        private static @Nullable CityLayout at(Level level) {
            return level.dimension() == Level.OVERWORLD ? City.layout() : null;
        }

        @Override
        public boolean inCity(Level level, BlockPos pos) {
            CityLayout l = at(level);
            return l != null && l.inCity(pos.getX(), pos.getZ());
        }

        @Override
        public @Nullable String district(Level level, BlockPos pos) {
            CityLayout l = at(level);
            if (l == null || !l.inCity(pos.getX(), pos.getZ())) return null;
            int d = l.district(pos.getX(), pos.getZ());
            return d <= 0 ? null : CityPlan.DISTRICTS[d];
        }

        /** Every block inside the wall is civic until players own plots (MMO-lite). */
        @Override
        public boolean isCivic(Level level, BlockPos pos) {
            return inCity(level, pos);
        }

        @Override
        public @Nullable BlockPos startPoint(ServerPlayer p) {
            CityLayout l = City.layout();
            return l == null ? null : City.start(l);
        }

        @Override
        public @Nullable BlockPos square(Level level) {
            CityLayout l = at(level);
            return l == null ? null : City.square(l);
        }

        @Override
        public @Nullable BlockPos spot(Level level, String id) {
            CityLayout l = at(level);
            return l == null ? null : City.spot(l, id);
        }
    }
}
