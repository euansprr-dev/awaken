package dev.awaken.caves;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Living Deep (docs/caves/PLAN.md, SPEC §42): the underground's own geography. Its worldgen lives in the data pack
 * {@code awakened_caves}, behind the feature flag of the same name, so only worlds created with it change: cave regions
 * as cave biomes Lithostitched injects under the land ({@link CaveRegion}), their shapes opened in the cave density
 * ({@link CaveShapeFunction}), their rock, growth and light. Without Lithostitched the pack's injectors and wrappers
 * do nothing and the caves stay as they were.
 */
public final class CavesModule {
    private CavesModule() {}

    public static final String PACK_ID = "mod/awaken:datapacks/awakened_caves";

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Awaken.MODID);

    static {
        DENSITY.register("cave_shape", () -> CaveShapeFunction.CODEC);
    }

    public static void init(IEventBus modBus) {
        DENSITY.register(modBus);
        modBus.addListener(AddPackFindersEvent.class, e -> e.addPackFinders(Awaken.id("datapacks/awakened_caves"), PackType.SERVER_DATA,
                Component.literal("Awakened Caves"), PackSource.BUILT_IN, false, Pack.Position.TOP));
        CavesTests.register();
    }
}
