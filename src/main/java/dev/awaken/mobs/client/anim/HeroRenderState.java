package dev.awaken.mobs.client.anim;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * What a hero body needs this frame, copied from the entity by {@link HeroRenderer}: distance-locked gait, how much
 * it walks or runs, which cue clip plays and how far in, the melee swing, the flinch and the fall. Times in seconds;
 * clip indices are {@code HeroModel} clip slots (-1 for none).
 */
public final class HeroRenderState extends LivingEntityRenderState {
    /** Blocks travelled (unclamped), for walk and run cycles that never slide. */
    public float gait;
    /** 0..1 how much the walk cycle shows (the vanilla walk speed), and how much of that is the run. */
    public float walk, run;
    /** Ground speed in blocks per tick (fliers: air speed). */
    public float speed;
    public boolean airborne;
    /** 0..1: it has a target (a tense stance on top of the idle). */
    public float angry;
    public int cueClip = -1;
    public float cueTime, cueWeight;
    /** A wind-up that was cut short fades from its held pose. */
    public int prevClip = -1;
    public float prevTime, prevWeight;
    /** Seconds since a melee swing began, or -1. */
    public float melee = -1;
    /** Seconds since it was hit, or -1. */
    public float hurt = -1;
    /** Seconds since it started dying, or -1. */
    public float dying = -1;
    /** The overworld creature's pose bits (FaunaMob.P_*). */
    public int pose;
}
