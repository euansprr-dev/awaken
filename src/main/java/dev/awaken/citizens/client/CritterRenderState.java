package dev.awaken.citizens.client;

import dev.awaken.mobs.client.life.Life;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** One of the city's animals this frame: which body and coat, its clips and crossfade, a cart's wheels and load. */
final class CritterRenderState extends LivingEntityRenderState {
    int kind, variant;
    int clip = -1, prevClip = -1;
    float clipTime, prevTime, prevWeight;
    float wheel;
    int load = -1;
    @Nullable Identifier texture, blink, glow;
    float glowAlpha;
    /** A horse's cart: its axle relative to the horse (blocks), yaw and height; its own clip state. */
    double cartDx, cartDz;
    float cartYaw, cartDy;
    @Nullable Life life;
    float modelScale = 1F;
    @Nullable CritterRenderState cart;
}
