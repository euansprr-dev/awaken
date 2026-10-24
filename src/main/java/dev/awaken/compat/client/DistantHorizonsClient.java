package dev.awaken.compat.client;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterColorDepthTextureCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiTextureCreatedParam;
import dev.awaken.Awaken;
import dev.awaken.compat.FarView;

/**
 * Distant Horizons on the client. In Awaken's instanced dimensions ({@link FarView}) its render pass is cancelled, so
 * cached far terrain never shows phantom rooms. Everywhere else it reports its clip planes, depth convention and depth
 * texture to {@link FarTerrain} for Lens. Loaded only when Distant Horizons is present.
 */
final class DistantHorizonsClient {
    private DistantHorizonsClient() {}

    // decided once per level, not every frame
    private static IDhApiLevelWrapper lastLevel;
    private static boolean lastAllowed = true;
    // DH recreates its depth texture on resize: fetch it again only then
    private static volatile boolean depthStale = true;

    static void init() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new BeforeRender());
        DhApi.events.bind(DhApiAfterColorDepthTextureCreatedEvent.class, new TexturesCreated());
        Awaken.LOG.info("[compat] Distant Horizons client hooks ready (instanced dimensions hidden, depth shared with Lens)");
    }

    private static final class BeforeRender extends DhApiBeforeRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            IDhApiLevelWrapper level = event.value.clientLevelWrapper;
            if (level == null) return;
            if (level != lastLevel) {
                lastLevel = level;
                lastAllowed = FarView.allowed(level.getDimensionName());
            }
            if (!lastAllowed) {
                event.cancelEvent();
                return;
            }
            FarTerrain.near = event.value.nearClipPlane;
            FarTerrain.far = event.value.farClipPlane;
            if (depthStale) refreshDepth();
            FarTerrain.lastFrameNanos = System.nanoTime();
        }
    }

    private static final class TexturesCreated extends DhApiAfterColorDepthTextureCreatedEvent {
        @Override
        public void onResize(DhApiEventParam<DhApiTextureCreatedParam> event) {
            depthStale = true;
        }
    }

    private static void refreshDepth() {
        var proxy = DhApi.Delayed.renderProxy;
        if (proxy == null) return;
        try {
            FarTerrain.reverseZ = proxy.getDepthDirection() == EDhApiDepthDirection.REVERSE_Z;
            FarTerrain.zeroToOne = proxy.getDepthRange() == EDhApiDepthRange.ZERO_TO_POS_ONE;
            var wrapper = proxy.getDhDepthTextureBlazeWrapper();
            if (wrapper.success && wrapper.payload != null) {
                FarTerrain.pointDepthAt(wrapper.payload.getWrappedMcObject());
                depthStale = false;
                Awaken.LOG.info("[compat] Distant Horizons depth for Lens: {} ({}x{}, {}, {})", wrapper.payload.getName(),
                        wrapper.payload.getWidth(), wrapper.payload.getHeight(), FarTerrain.reverseZ ? "reverse-Z" : "forward-Z",
                        FarTerrain.zeroToOne ? "0..1" : "-1..1");
            }
        } catch (IllegalStateException e) {
            // DH's renderer isn't up yet: try again next frame
        }
    }
}
