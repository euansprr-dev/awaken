package dev.awaken.turbo.wateredge.mixin;

import net.minecraft.core.BlockPos;

/** Added to RenderSectionRegion by {@link RenderSectionRegionMixin}: whether a position's chunk was unloaded when copied. */
public interface SectionRegionProbe {
    boolean awaken$unloaded(BlockPos pos);
}
