package dev.awaken.turbo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import dev.awaken.turbo.PipelineCacheStore;
import java.nio.LongBuffer;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turbo pipeline cache (switch pipelineCache, verified on 26.3): 26.3 builds every graphics pipeline with no
 * VkPipelineCache (four variants each, every launch). This hands all four calls the cache kept in the game folder;
 * when the cache couldn't be made it hands back 0, which is exactly the original call.
 */
@Mixin(VulkanRenderPipeline.class)
abstract class VulkanRenderPipelineMixin {
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK12;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private static int awaken$pipelineCache(VkDevice device, long pipelineCache, VkGraphicsPipelineCreateInfo.Buffer createInfos,
                                            VkAllocationCallbacks allocator, LongBuffer pipelines, Operation<Integer> original) {
        long cache = pipelineCache != 0L ? pipelineCache : PipelineCacheStore.handle(device);
        if (cache == 0L) return original.call(device, pipelineCache, createInfos, allocator, pipelines);
        PipelineCacheStore.beginCompile();
        try {
            return original.call(device, cache, createInfos, allocator, pipelines);
        } finally {
            PipelineCacheStore.endCompile();
        }
    }
}
