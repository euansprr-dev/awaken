package dev.awaken.ground;

import dev.awaken.Awaken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The Soft Land (docs/terrain-immersion/PLAN.md, SPEC §31): the land's seams, steps and detail. This is P0, seamless
 * ground: grass that wraps down a one-block step, grass creeping onto paths and dirt, and a lawn whose colour drifts in
 * patches. All of it is the client's drawing ({@code ground.client}); the rules about which sides and edges it touches
 * live here so tests can hold them.
 */
public final class GroundModule {
    private GroundModule() {}

    /** The data pack every worldgen part of the Soft Land lives in (P1-P5), behind the feature flag of the same name: new worlds only. */
    /** Rock a boulder is made of besides the overworld's base stones: wild life perches on it ({@code wildlife.client.Sites}). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> PERCH_ROCK =
            net.minecraft.tags.TagKey.create(Registries.BLOCK, Awaken.id("ground/perch_rock"));

    public static final String PACK_ID = "mod/awaken:datapacks/awakened_ground";

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);
    static {
        FEATURES.register("soft_steps", () -> SoftStepsFeature.CODEC);
        FEATURES.register("boulder", () -> BoulderFeature.CODEC);
    }

    public static void init(IEventBus modBus) {
        SoftSlabs.init(modBus);
        GroundCover.init(modBus);
        FEATURES.register(modBus);
        GroundFresh.init(modBus);
        dev.awaken.ground.wild.WildBlocks.init(modBus);
        dev.awaken.ground.wild.gen.WildGen.init(modBus);
        modBus.addListener(AddPackFindersEvent.class, e -> e.addPackFinders(Awaken.id("datapacks/awakened_ground"), PackType.SERVER_DATA,
                Component.literal("Awakened Ground"), PackSource.BUILT_IN, false, Pack.Position.TOP));
        GroundTests.register();
        dev.awaken.ground.wild.WildTests.register();
        dev.awaken.village.road.RoadBandsTests.register();
    }
}
