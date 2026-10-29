package dev.awaken.mobs.entity;

/**
 * What the renderer needs to draw one AWAKEN creature this frame. One instance lives on each client entity and is
 * refilled in extractRenderState, so rendering allocates nothing per frame. Plain data, safe in common code.
 */
public final class AwLook {
    /** ARGB multiplied over the base texture. */
    public int tint = -1;
    /** The same without the creature's own colour (a kit body is painted in its own colours): only frost, charge,
     *  the risen pall and a haunting tint it, and the haunting's fade. */
    public int kitTint = -1;
    /** Emissive layer alpha 0..1. */
    public float glow = 0.6F;
    /** The creature sets its emissive strength itself, the fall included (no floor, no fade as it dies): the seep heart's beat. */
    public boolean ownGlow;
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
    /** 1 the moment it is hit, falling to 0 (the model's flinch, the life layer's stagger). */
    public float hurt;
    /** It has turned on someone (vanilla's aggressive flag): a Gate Lord's provocation. */
    public boolean aggressive;
    /** Client only: the creature's life-layer state ({@code mobs.client.life.Life}), made on its first draw. */
    public Object life;
}
