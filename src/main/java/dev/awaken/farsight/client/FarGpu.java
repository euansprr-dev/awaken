package dev.awaken.farsight.client;

/**
 * Far Sight's GPU work besides drawing (SPEC §20), behind one interface so it can move without touching the rings:
 * {@link FragmentFarGpu} does it with a fragment pass today (the only kind of pass 26.3 gives mods); a Turbo compute
 * implementation (raw Vulkan, dev.awaken.turbo) and Mojang's own compute shaders, once they land, are the others.
 * Each is kept only if it measures better.
 */
interface FarGpu extends AutoCloseable {
    /**
     * Derives every corner's normal, valley shade and sunlight for the rings set in {@code ringMask}: heights in, shade
     * out. {@code sun} points toward the light; {@code sunUp} false when it is below the horizon (nothing to shadow).
     */
    void bake(FarField field, int ringMask, org.joml.Vector3fc sun, boolean sunUp);

    String name();

    @Override
    void close();
}
