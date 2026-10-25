package dev.awaken.farsight.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.awaken.farsight.Rings;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.Vector3fc;

import java.util.Optional;

/**
 * {@link FarGpu} as a fragment pass: one triangle over the shade atlas, each fragment one ring corner. It reads the
 * corners around it in its ring's window (generated where seen, else predicted) and writes the normal, how far the
 * corner sits below its neighbours (valleys darken) and whether the sun reaches it past the ridges toward it. Rings not
 * asked for are discarded early.
 */
final class FragmentFarGpu implements FarGpu {
    private final MappableRingBuffer ubo = new MappableRingBuffer(() -> "awaken far sight bake", 130, 16 * (2 + Rings.RINGS));

    @Override
    public void bake(FarField field, int ringMask, Vector3fc sun, boolean sunUp) {
        if (ringMask == 0) return;
        var buffer = ubo.currentBuffer();
        try (var view = buffer.map(false, true)) {
            Std140Builder u = Std140Builder.intoBuffer(view.data()).putIVec4(ringMask, FarField.WIDTH, FarField.HEIGHT, 0)
                    .putVec4(sun.x(), sun.y(), sun.z(), sunUp ? 1f : 0f);
            for (int r = 0; r < Rings.RINGS; r++) {
                u.putIVec4((field.winX[r] * Rings.PATCH) & Rings.MASK, (field.winZ[r] * Rings.PATCH) & Rings.MASK, 0, 0);
            }
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "awaken far sight bake", field.shadeView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.BAKE));
            pass.setUniform("FarBake", buffer);
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("HeightSampler", field.heightView, nearest);
            pass.setUniform("GenHeightSampler", field.genHeightView, nearest);
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
