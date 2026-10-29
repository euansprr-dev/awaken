package dev.awaken.kin.client;

import dev.awaken.mobs.client.life.Life;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** One of the kin this frame: its clips and crossfade, its coat, its glow (a Lampwing's lamp, a Pebblepup's crystals). */
final class KinRenderState extends LivingEntityRenderState {
    int clip = -1, prevClip = -1;
    float clipTime, prevTime, prevWeight;
    @Nullable Identifier texture, blink, glow;
    float glowAlpha;
    @Nullable Life life;
    float modelScale = 1F;
    boolean airborne;
    /** What it's carrying on its back (a Totter's armful, a Thatchback's plank), and how high its back is. */
    final net.minecraft.client.renderer.item.ItemStackRenderState held = new net.minecraft.client.renderer.item.ItemStackRenderState();
    float backY;
}
