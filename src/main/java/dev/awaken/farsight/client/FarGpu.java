package dev.awaken.farsight.client;

/**
 * Far Sight's GPU work besides drawing (SPEC §20), behind one interface so it can move without touching the rings:
 * {@link FragmentFarGpu} does it with a fragment pass today (the only kind of pass 26.3 gives mods); a Turbo compute
 * implementation (raw Vulkan, dev.awaken.turbo) and Mojang's own compute shaders, once they land, are the others.
 * Each is kept only if it measures better.
 */
interface FarGpu extends AutoCloseable {
    /** Derives every cell's normal and valley shade for the rings set in {@code ringMask}: heights in, shade out. */
    void bake(FarField field, int ringMask);

    String name();

    @Override
    void close();
}
