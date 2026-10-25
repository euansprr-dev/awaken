package dev.awaken.turbo.wateredge.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets a section's region say whether a neighbouring position lies in a chunk the client hadn't received. */
@Mixin(RenderSectionRegion.class)
abstract class RenderSectionRegionMixin implements SectionRegionProbe {
    @Shadow
    private SectionCopy getSection(int sectionX, int sectionY, int sectionZ) {
        throw new AssertionError();
    }

    @Override
    public boolean awaken$unloaded(BlockPos pos) {
        SectionCopy copy = getSection(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        return ((SectionCopyAccess) copy).awaken$wrapped() instanceof EmptyLevelChunk;
    }
}
