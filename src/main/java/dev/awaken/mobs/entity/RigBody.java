package dev.awaken.mobs.entity;

/**
 * A creature drawn on a rig of our own ({@code mobs.client.anim.WildRenderer}): what its body needs from the entity each
 * frame. Overworld creatures ({@link FaunaMob}) and the gates' monsters ({@link GateMonster}) both carry it, so one
 * renderer draws both (SPEC §23, §60).
 */
public interface RigBody {
    /**
     * Cue stages beyond {@link FaunaMob}'s wind-up, strike, roar and warning. OPEN: the move has fired and left the
     * creature open; its strike clip plays, then its {@code <move>_open} clip, held until the cue ends. STUCK: a dive
     * or a charge that missed; the {@code <move>_open} clip from its start, held the same way.
     */
    int CUE_OPEN = 5, CUE_STUCK = 6;

    /** Client: blocks travelled (unclamped), so gaits lock to the ground at any speed. */
    float gait(float partialTicks);

    /** Client: smoothed ground speed in blocks per tick. */
    float gaitSpeed();

    /** The cue as synced ({@link FaunaMob#cueStage}, {@link FaunaMob#cueSlot}). */
    int cue();

    int cueStart();

    int prevCue();

    int prevCueStart();

    int prevCueEnd();

    /** Client: the tick its last melee swing began (the {@code bite} clip), or far in the past. */
    int meleeStart();

    /** Client: ticks since it started dying. */
    int dyingTicks();
}
