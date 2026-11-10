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
    /**
     * POISED: it has stepped (a blink) and its cut is told but not yet thrown: the move's wind-up held at its last
     * frame, then its strike begun so the cut lands as that tell ends (SPEC §60.9).
     */
    int CUE_POISED = 7;

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

    /**
     * Client: how long the tell was (ticks) when the current cue's wind-up began, as the server synced it, or 0 if the
     * body doesn't know (then the renderer goes by the move's own wind-up). A blow told shorter or longer than its
     * move's usual (a blink's follow-up cut, a heavy) is gathered over the time it really has.
     */
    default int cueTell() {
        return 0;
    }

    /** Client: {@link #cueTell} of the cue before this one. */
    default int prevCueTell() {
        return 0;
    }

    /** Client: the tick its last melee swing began (the {@code bite} clip), or far in the past. */
    int meleeStart();

    /** Client: ticks since it started dying. */
    int dyingTicks();
}
