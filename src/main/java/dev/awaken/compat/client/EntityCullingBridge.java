package dev.awaken.compat.client;

import dev.awaken.core.api.CameraHooks;
import dev.awaken.mobs.entity.AwakenMob;
import dev.tr7zw.entityculling.EntityCullingModBase;
import dev.tr7zw.entityculling.access.Cullable;

/**
 * EntityCulling hides a creature when its rays to it all hit rock. They are a block or so apart, which a sliver of a
 * creature in a cave mouth can slip between, and at the binoculars' zoom that sliver fills the view. While the glass is
 * up it leaves our creatures alone (SPEC §39.11); the view is so narrow then that this costs next to nothing.
 * Loaded only when the mod is present.
 */
final class EntityCullingBridge {
    private EntityCullingBridge() {}

    static void init() {
        EntityCullingModBase.instance.addDynamicEntityWhitelist(e -> {
            if (CameraHooks.zoom() <= 1.05 || !(e instanceof AwakenMob)) return false;
            ((Cullable) e).setCulled(false); // being on the list only stops new checks: this clears the last one
            return true;
        });
    }
}
