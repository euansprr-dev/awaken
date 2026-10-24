package dev.awaken.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import dev.awaken.Awaken;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Distant Horizons draws far terrain from cached LODs. Where {@link FarView} says no (Awaken's instanced dimensions)
 * DH renders nothing and hands back empty columns instead of running Awaken's chunk generators: a cached LOD there
 * would show phantom rooms, and generating one would only burn CPU. Everywhere else it works as normal.
 */
final class DistantHorizonsCompat {
    private DistantHorizonsCompat() {}

    // the render hook runs every frame: decide once per level, not per frame
    private static IDhApiLevelWrapper lastLevel;
    private static boolean lastAllowed = true;

    static void init() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new HideInInstances());
        DhApi.events.bind(DhApiLevelLoadEvent.class, new EmptyGenerationInInstances());
        Awaken.LOG.info("[compat] Distant Horizons {}: far terrain off in instanced Awaken dimensions", DhApi.getModVersion());
    }

    private static final class HideInInstances extends DhApiBeforeRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            IDhApiLevelWrapper level = event.value.clientLevelWrapper;
            if (level == null) return;
            if (level != lastLevel) {
                lastLevel = level;
                lastAllowed = FarView.allowed(level.getDimensionName());
            }
            if (!lastAllowed) event.cancelEvent();
        }
    }

    private static final class EmptyGenerationInInstances extends DhApiLevelLoadEvent {
        @Override
        public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> event) {
            IDhApiLevelWrapper level = event.value.levelWrapper;
            String name = level.getDimensionName();
            boolean allowed = FarView.allowed(name);
            Awaken.LOG.info("[compat] Distant Horizons level {} ({}): far terrain {}", name, level.getLevelType(), allowed ? "on" : "off");
            if (allowed || level.getLevelType() != EDhApiLevelType.SERVER_LEVEL) return;
            var result = DhApi.worldGenOverrides.registerWorldGeneratorOverride(level, new EmptyGenerator(level.getMinHeight(), level.getMaxHeight()));
            if (!result.success) Awaken.LOG.warn("[compat] Distant Horizons kept its generator for {}: {}", name, result.message);
        }
    }

    /** Hands DH empty columns, so it never asks Awaken's chunk generators for far terrain nobody will see. */
    private record EmptyGenerator(int minY, int maxY) implements IDhApiWorldGenerator {
        @Override
        public EDhApiWorldGeneratorReturnType getReturnType() {
            return EDhApiWorldGeneratorReturnType.API_CHUNKS;
        }

        @Override
        public CompletableFuture<Void> generateApiChunks(int chunkPosMinX, int chunkPosMinZ, int generationRequestChunkWidthCount,
                byte targetDataDetail, EDhApiDistantGeneratorMode generatorMode, ExecutorService worldGeneratorThreadPool,
                Consumer<DhApiChunk> resultConsumer) {
            for (int x = 0; x < generationRequestChunkWidthCount; x++) {
                for (int z = 0; z < generationRequestChunkWidthCount; z++) {
                    resultConsumer.accept(DhApiChunk.create(chunkPosMinX + x, chunkPosMinZ + z, minY, maxY));
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void preGeneratorTaskStart() {}

        @Override
        public void close() {}
    }
}
