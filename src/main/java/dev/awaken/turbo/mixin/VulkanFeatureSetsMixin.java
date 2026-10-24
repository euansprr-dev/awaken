package dev.awaken.turbo.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turbo Metal objects (switch metalObjects, verified on 26.3): asks for VK_EXT_metal_objects as one more optional
 * FeatureSet, so the device has it when MoltenVK offers it (Pixels' MetalFX exports Metal handles through it). Optional
 * means Minecraft enables it only when the device supports it, exactly like its own optional sets.
 */
@Mixin(VulkanFeatureSets.class)
abstract class VulkanFeatureSetsMixin {
    private static final FeatureSet AWAKEN_METAL_OBJECTS = new FeatureSet("Awaken Turbo: Metal objects", Set.of("VK_EXT_metal_objects"), Set.of());

    @ModifyReturnValue(method = "optionalFeatureSets", at = @At("RETURN"))
    private static Set<FeatureSet> awaken$metalObjects(Set<FeatureSet> sets) {
        sets.add(AWAKEN_METAL_OBJECTS);
        return sets;
    }
}
