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
    /** At work (a job clip, not standing about): looks up from it only when spoken to or crowded. */
    public boolean working;
    public final ItemStackRenderState backItem = new ItemStackRenderState();
    /** What the citizen is saying (wrapped lines), how it fades, and its kind (0 to you, 1 aloud, 2 a shout). */
    public java.util.@org.jspecify.annotations.Nullable List<net.minecraft.util.FormattedCharSequence> speech;
    public float speechAlpha;
    public int speechKind;
    /** The name shown when you look at them up close. */
    public net.minecraft.util.@org.jspecify.annotations.Nullable FormattedCharSequence label;
    public float labelWidth;
    public float height = 1.95f;
    /** The life layer (breath, blinks, the slow head, idle glances) and which model's rig it was made for. */
    public dev.awaken.mobs.client.life.@org.jspecify.annotations.Nullable Life life;
    public boolean lifeSlim;
    /** The lantern in the off hand (drawn by the lantern layer), or null. */
    public @org.jspecify.annotations.Nullable LanternHold lantern;
}
