package dev.awaken.caves;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.caves.skin.CaveSkin;
import dev.awaken.caves.skin.CaveSkinFeature;
import dev.awaken.caves.skin.Drips;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Living Deep (docs/caves/PLAN.md, SPEC §42): the underground's own geography. Its worldgen lives in the data pack
 * {@code awakened_caves}, behind the feature flag of the same name, so only worlds created with it change: cave regions
 * as cave biomes Lithostitched injects under the land ({@link CaveRegion}), their shapes opened in the cave density
 * ({@link CaveShapeFunction}), their rock, growth and light; and the skin every cave gets ({@code skin}: its blocks exist
 * in every world, the pack's feature lays them). Without Lithostitched the pack's injectors and wrappers
 * do nothing and the caves stay as they were.
 */
public final class CavesModule {
    private CavesModule() {}

    public static final String PACK_ID = "mod/awaken:datapacks/awakened_caves";

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Awaken.MODID);

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    /** The underlife's voices (SPEC §42.5; tools/art/specs/audio_underlife.py): a cave cricket's chirps, a roost bursting off the roof. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CRICKET = sound("caves.cricket"), BATS = sound("caves.bats");
    /** The regions' sounds (SPEC §42.7): the Rootways' roots; the Ochre Deep's rock, chime crickets and hum. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROOT_CREAK = sound("caves.root_creak"), ROCK_TICK = sound("caves.rock_tick"),
            CHIME_CRICKET = sound("caves.chime_cricket"), WARM_HUM = sound("caves.warm_hum");
    /** The other six's (SPEC §42.8). */
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_DRIP = sound("caves.pool_drip"), WIND_MOAN = sound("caves.wind_moan"),
            PEBBLE_FALL = sound("caves.pebble_fall"), LAKE_LAP = sound("caves.lake_lap"), CRYSTAL_RING = sound("caves.crystal_ring"),
            VAULT_RUMBLE = sound("caves.vault_rumble"), VIOLET_HUM = sound("caves.violet_hum");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    static {
        DENSITY.register("cave_shape", () -> CaveShapeFunction.CODEC);
        FEATURES.register("cave_skin", () -> CaveSkinFeature.CODEC);
    }

    public static void init(IEventBus modBus) {
        DENSITY.register(modBus);
        FEATURES.register(modBus);
        SOUNDS.register(modBus);
        CaveSkin.init(modBus);
        Drips.init(modBus);
        modBus.addListener(AddPackFindersEvent.class, e -> e.addPackFinders(Awaken.id("datapacks/awakened_caves"), PackType.SERVER_DATA,
                Component.literal("Awakened Caves"), PackSource.BUILT_IN, false, Pack.Position.TOP));
        RegionCommands.register();
        CavesTests.register();
    }
}
