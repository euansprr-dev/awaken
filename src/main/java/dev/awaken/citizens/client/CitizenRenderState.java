package dev.awaken.citizens.client;

import dev.awaken.core.client.skin.SkinRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** A citizen's body this frame: its skin (from the roster), fade, the clip pose blended over vanilla's, the crate on its back. */
public final class CitizenRenderState extends SkinRenderState {
    public int rec = -1;
    public float alpha = 1f;
    /** The clip's channels now and the previous clip's (crossfading over a few ticks); NaN keeps vanilla. */
    public final float[] pose = new float[CitizenClips.CH], prevPose = new float[CitizenClips.CH];
    /** 0..1: how far from the previous pose (or vanilla) to this clip's. */
    public float blend = 1f;
    public boolean hasPose, hasPrev;
    public boolean crate, hurt;
    public final ItemStackRenderState backItem = new ItemStackRenderState();
}
