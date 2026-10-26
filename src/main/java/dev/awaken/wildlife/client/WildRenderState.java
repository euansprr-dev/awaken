package dev.awaken.wildlife.client;

import dev.awaken.mobs.client.life.Life;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** One of the wild's animals this frame: which body and coat, its clips and crossfade, its bank and pitch in flight. */
final class WildRenderState extends LivingEntityRenderState {
    int kind, variant;
    int clip = -1, prevClip = -1;
    float clipTime, prevTime, prevWeight;
    float roll, pitch, sink;
    boolean airborne;
    @Nullable Identifier texture, blink, glow;
    float glowAlpha;
    @Nullable Life life;
    float modelScale = 1F;
}
