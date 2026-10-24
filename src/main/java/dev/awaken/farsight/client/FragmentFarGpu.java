package dev.awaken.farsight.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.MappableRingBuffer;

import java.util.Optional;

/**
 * {@link FarGpu} as a fragment pass: one triangle over the shade atlas, each fragment one cell. It reads the 5×5 cells
 * around it from the height atlas (wrapping inside its ring's block) and writes the smoothed normal and how far the
 * cell sits below its neighbours (valleys darken). Rings that didn't change are discarded early.
 */
final class FragmentFarGpu implements FarGpu {
    private final MappableRingBuffer ubo = new MappableRingBuffer(() -> "awaken far sight bake", 130, 16);

    @Override
    public void bake(FarField field, int ringMask) {
        if (ringMask == 0) return;
        var buffer = ubo.currentBuffer();
        try (var view = buffer.map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putIVec4(ringMask, FarField.WIDTH, FarField.HEIGHT, 0);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "awaken far sight bake", field.shadeView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.BAKE));
            pass.setUniform("FarBake", buffer);
            pass.setUniform("HeightSampler", field.heightView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
        ubo.rotate();
    }

    @Override
    public String name() {
        return "fragment pass";
    }

    @Override
    public void close() {
        ubo.close();
    }
}
