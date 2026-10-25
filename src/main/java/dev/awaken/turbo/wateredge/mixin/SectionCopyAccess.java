package dev.awaken.turbo.wateredge.mixin;

import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunk a section copy was taken from (package-private in 26.3): an EmptyLevelChunk where none had arrived. */
@Mixin(SectionCopy.class)
public interface SectionCopyAccess {
    @Accessor("wrapped")
    LevelChunk awaken$wrapped();
}
