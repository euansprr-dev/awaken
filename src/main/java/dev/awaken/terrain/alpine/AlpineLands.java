package dev.awaken.terrain.alpine;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Awakened Alpine (SPEC §15, Far Sight 2): real mountain form for new worlds. Its data pack asks for the feature flag
 * {@code awaken:awakened_alpine}, so only worlds created with it change; a world made before keeps its land. The pack
 * wraps the terrain's offset in {@link ErodedFunction} (vanilla's, and Tectonic's when it is loaded; there it also lifts
 * Tectonic's offset clamp and holds its ridges under the ceiling, {@link RidgeRoomFunction}), prepends
 * {@link AlpineSurface}'s snow, scree and rock to the mountain biomes' surfaces, and plants mountain forests (spruce
 * below the treeline, {@link TreelineFilter}).
 */
public final class AlpineLands {
    private AlpineLands() {}

    public static final String PACK_ID = "mod/awaken:datapacks/awakened_alpine";

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends MaterialCondition>> CONDITIONS =
            DeferredRegister.create(Registries.MATERIAL_CONDITION_TYPE, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends net.minecraft.world.level.levelgen.feature.Feature>> FEATURES =
            DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENT =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Awaken.MODID);

    static {
        DENSITY.register("eroded", () -> ErodedFunction.CODEC);
        DENSITY.register("ridge_room", () -> RidgeRoomFunction.CODEC);
        CONDITIONS.register("alpine", () -> AlpineCondition.CODEC);
        CONDITIONS.register("strata", () -> StrataCondition.CODEC);
        PLACEMENT.register("treeline", () -> TreelineFilter.CODEC);
        FEATURES.register("snow_drifts", () -> SnowDriftsFeature.CODEC);
    }

    public static void init(IEventBus modBus) {
        DENSITY.register(modBus);
        CONDITIONS.register(modBus);
        PLACEMENT.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(AddPackFindersEvent.class, e -> e.addPackFinders(Awaken.id("datapacks/awakened_alpine"), PackType.SERVER_DATA,
                Component.literal("Awakened Alpine"), PackSource.BUILT_IN, false, Pack.Position.TOP));
        AlpineTests.register();
    }
}
