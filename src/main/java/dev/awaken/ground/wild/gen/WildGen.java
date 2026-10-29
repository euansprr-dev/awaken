package dev.awaken.ground.wild.gen;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The wild's worldgen types (SPEC §41.3); the features themselves are in the {@code awaken:awakened_ground} pack (new worlds). */
public final class WildGen {
    private WildGen() {}

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENT =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Awaken.MODID);

    static {
        PLACEMENT.register("wildness", () -> WildnessPlacement.CODEC);
        FEATURES.register("wild_ground", () -> WildGroundFeature.CODEC);
        FEATURES.register("wild_bush", () -> WildGrowth.Bush.CODEC);
        FEATURES.register("hedgerow", () -> WildGrowth.Hedgerow.CODEC);
        FEATURES.register("thicket", () -> WildGrowth.Thicket.CODEC);
        FEATURES.register("old_giant", () -> WildGrowth.OldGiant.CODEC);
        FEATURES.register("snag", () -> WildGrowth.Snag.CODEC);
        FEATURES.register("deadfall", () -> WildGrowth.Deadfall.CODEC);
    }

    public static void init(IEventBus modBus) {
        FEATURES.register(modBus);
        PLACEMENT.register(modBus);
    }
}
