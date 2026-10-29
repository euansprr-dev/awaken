package dev.awaken.turbo.roadstarts.mixin;

import dev.awaken.turbo.roadstarts.RoadStarts;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * 26.3's structure starts step ({@code ChunkStatusTasks.generateStructureStarts}, read against 26.3 / NeoForge
 * 26.3.0.95): a lattice chunk that would wait for its roads hands back RoadStarts' future instead. Not required: if it
 * doesn't apply, the probe never fires and structure starts run as vanilla's.
 */
@Mixin(ChunkStatusTasks.class)
abstract class ChunkStatusTasksMixin {
    @Inject(method = "generateStructureStarts", at = @At("HEAD"), cancellable = true, require = 0)
    private static void awaken$roadStarts(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk,
                                          CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        CompletableFuture<ChunkAccess> later = RoadStarts.later(context, step, chunks, chunk);
        if (later != null) cir.setReturnValue(later);
    }
}
