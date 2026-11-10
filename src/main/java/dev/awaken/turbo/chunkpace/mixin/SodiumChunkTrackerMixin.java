package dev.awaken.turbo.chunkpace.mixin;

import dev.awaken.turbo.chunkpace.ChunkPace;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium 0.9.3's {@code ChunkTracker.forEachEvent(load, unload)} hands every queued unload to the renderer and clears
 * the queue. Before it runs, all but this frame's share are lifted out of {@code unloadQueue}; after it, they go back
 * in, so between frames the queue is Sodium's own and its bookkeeping (a chunk that returns is taken off the queue)
 * sees every waiting chunk. Pseudo (Sodium may be absent) and not required: if it doesn't apply, the probe never
 * fires and the queue drains as Sodium's own.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker")
abstract class SodiumChunkTrackerMixin {
    @Shadow(remap = false)
    @Final
    private LongSet unloadQueue;

    @Unique
    private final LongArrayList awaken$held = new LongArrayList();

    @Inject(method = "forEachEvent", at = @At("HEAD"), require = 0, remap = false)
    private void awaken$hold(CallbackInfo ci) {
        ChunkPace.unloadsApplied();
        try {
            int queued = this.unloadQueue.size(), take = ChunkPace.unloadBudget(queued);
            if (take >= queued) return;
            LongIterator it = this.unloadQueue.iterator();
            for (int i = 0; it.hasNext(); i++) {
                long key = it.nextLong();
                if (i < take) continue;
                this.awaken$held.add(key);
                it.remove();
            }
        } catch (RuntimeException e) {
            this.unloadQueue.addAll(this.awaken$held);
            this.awaken$held.clear();
            ChunkPace.fail(e);
        }
    }

    @Inject(method = "forEachEvent", at = @At("RETURN"), require = 0, remap = false)
    private void awaken$giveBack(CallbackInfo ci) {
        if (this.awaken$held.isEmpty()) return;
        this.unloadQueue.addAll(this.awaken$held);
        this.awaken$held.clear();
    }
}
