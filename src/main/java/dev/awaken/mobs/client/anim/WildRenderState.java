package dev.awaken.mobs.client.anim;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * A P3 body this frame (the hero state plus what swimmers, hangers and mounts need): distance-locked gait, the cue
 * clip, the melee swing, the flinch, the fall, the held poses ({@code WildPose.S_*}) and the swim pitch. Times in
 * seconds; clip indices are {@code WildModel} clip slots (-1 for none).
 */
public final class WildRenderState extends LivingEntityRenderState {
    public float gait;
    public float walk, run;
    public float speed;
    public float angry;
    public int cueClip = -1;
    public float cueTime, cueWeight;
    public int prevClip = -1;
    public float prevTime, prevWeight;
    public float melee = -1;
    public float hurt = -1;
    public float dying = -1;
    /** Held poses (WildPose.S_*). */
    public int wild;
    /** Body pitch while swimming (degrees, positive nose down). */
    public float pitch;
    /** Seconds since its last heartbeat (WildPose#beatTime), -1 for none. */
    public float beat = -1;
}
