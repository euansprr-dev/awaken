package dev.awaken.gates.world;

/**
 * A planned world floor's shape (SPEC §38, "Engines"): what its engine worked out at layout build (places, paths, hero
 * piece sites, light), able to carve any chunk of its plot on demand. Immutable and shared by worldgen threads, like the
 * rest of the layout; carving reads only this and writes only to the sink.
 */
public interface WorldModel {
    /** Paints the chunk at cell slot (slotX, slotZ); sink x/z are 0..15 inside it, y is absolute. */
    void carve(dev.awaken.gates.content.GatePlan plan, FloorLayout f, int slotX, int slotZ, GatePainter.Sink out);
}
