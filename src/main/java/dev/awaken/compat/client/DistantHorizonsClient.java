package dev.awaken.compat.client;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiBlazeTextureWrapper;
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
 * <p>
 * Lens samples Distant Horizons' own depth texture, no copy: its Blaze renderer (the default on 26.3) clears that
 * texture only at the start of its own pass (right after {@code DhApiBeforeTextureClearEvent}), so after it draws, the
 * texture holds this frame's far terrain until the next frame's pass, well past the post effects.
 */
final class DistantHorizonsClient {
    private DistantHorizonsClient() {}

    // decided once per level, not every frame
    private static IDhApiLevelWrapper lastLevel;
    private static boolean lastAllowed = true;
    // DH's depth texture wrapper: one object for the renderer's life; its texture is recreated on a resize
    private static IDhApiBlazeTextureWrapper depthWrapper;
    private static boolean reported;
    // until DH has a depth texture to share, ask again at most twice a second (each ask allocates a result)
    private static long nextAsk;

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
            if (depthWrapper == null) findDepth(false);
            // the matrix's planes, not event.value.nearClipPlane (DH clamps the matrix's near plane to 7.5 blocks)
            var m = event.value.dhProjectionMatrix;
            FarTerrain.planesFrom(m.m22, m.m23, FarTerrain.reverseZ, FarTerrain.zeroToOne);
            // the reused array DH hands back: no allocation; re-points only when DH made a new texture
            if (depthWrapper != null) FarTerrain.pointDepthAt(depthWrapper.getWrappedMcObject());
            FarTerrain.lastFrameNanos = System.nanoTime();
        }
    }

    /** DH recreates its textures on a resize, inside its pass setup: point Lens at the new one before anything samples it. */
    private static final class TexturesCreated extends DhApiAfterColorDepthTextureCreatedEvent {
        @Override
        public void onResize(DhApiEventParam<DhApiTextureCreatedParam> event) {
            if (depthWrapper == null) findDepth(true);
            if (depthWrapper != null) FarTerrain.pointDepthAt(depthWrapper.getWrappedMcObject());
        }
    }

    private static void findDepth(boolean now) {
        var proxy = DhApi.Delayed.renderProxy;
        long t = System.nanoTime();
        if (proxy == null || !now && t - nextAsk < 0) return;
        nextAsk = t + 500_000_000L;
        try {
            FarTerrain.reverseZ = proxy.getDepthDirection() == EDhApiDepthDirection.REVERSE_Z;
            FarTerrain.zeroToOne = proxy.getDepthRange() == EDhApiDepthRange.ZERO_TO_POS_ONE;
            var wrapper = proxy.getDhDepthTextureBlazeWrapper();
            if (!wrapper.success || wrapper.payload == null) {
                if (!reported) {
                    reported = true;
                    // the OpenGL engine (renderingEngine = OPEN_GL) has no texture to share: Lens keeps distance haze off
                    Awaken.LOG.info("[compat] Distant Horizons has no depth texture for Lens yet ({}, engine {})", wrapper.message,
                            proxy.getRenderingEngine());
                }
                return;
            }
            depthWrapper = wrapper.payload;
            boolean real = FarTerrain.pointDepthAt(depthWrapper.getWrappedMcObject());
            Awaken.LOG.info("[compat] Distant Horizons depth for Lens: {} ({}x{}, {}, {}, readable {})", depthWrapper.getName(),
                    depthWrapper.getWidth(), depthWrapper.getHeight(), FarTerrain.reverseZ ? "reverse-Z" : "forward-Z",
                    FarTerrain.zeroToOne ? "0..1" : "-1..1", real);
        } catch (IllegalStateException e) {
            // DH's renderer isn't up yet: try again next frame
        }
    }
}
