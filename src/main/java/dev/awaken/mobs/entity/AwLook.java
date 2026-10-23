package dev.awaken.mobs.entity;

/**
 * What the renderer needs to draw one AWAKEN creature this frame. One instance lives on each client entity and is
 * refilled in extractRenderState, so rendering allocates nothing per frame. Plain data, safe in common code.
 */
public final class AwLook {
    /** ARGB multiplied over the base texture. */
    public int tint = -1;
    /** Emissive layer alpha 0..1. */
    public float glow = 0.6F;
    public boolean translucent;
    public boolean flash;
    public float telegraph;
    /** Entity id of the player whose skin a mimic wears, or -1. */
    public int mimic = -1;
    public float squish;
    public boolean upsideDown;
    public boolean fallen;
    public float swell;
    /** Overworld creatures: pose bits ({@code FaunaMob.P_*}) for the model (airborne, stalking, grazing, angry...). */
    public int fauna;
    /** Overworld creatures: a 0..1 progress for the current pose (burrowing, grazing). */
    public float faunaAnim;
}
