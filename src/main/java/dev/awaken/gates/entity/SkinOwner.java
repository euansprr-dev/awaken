package dev.awaken.gates.entity;

/** Entities drawn with a player's skin (their owner's): the Mirror shade and the Echo ghost. */
public interface SkinOwner {
    /** Owner UUID as a string, or "" for the default dark silhouette. */
    String skinOwner();

    /** ARGB tint for the model. */
    int skinTint();

    /** Drawn translucent. */
    default boolean translucent() {
        return false;
    }
}
