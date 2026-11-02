package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.mobs.client.anim.HeroRenderer;
import dev.awaken.mobs.client.anim.WildRenderer;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.Topple;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.function.BiConsumer;

/**
 * A toppled big beast drawn (SPEC §47.5): our rigs go over onto their side about the edge they fall toward, so nothing
 * sinks into the ground; a vanilla body (a quarry under Fresh Animations) by vanilla's own death flip, as a carcass
 * lies, without the red.
 */
public final class ToppleView {
    private ToppleView() {}

    /** How far over the body being drawn lies, 0..1, signed by the side it goes over on. */
    public static final ContextKey<Float> OVER = new ContextKey<>(Awaken.id("mobs_over"));

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void register(RegisterRenderStateModifiersEvent e) {
        BiConsumer<Entity, EntityRenderState> ours = (entity, state) -> {
            if (!(entity instanceof LivingEntity le)) return;
            float a = Topple.over(le, state.partialTick);
            if (a > 0.0F) state.setRenderData(OVER, a * Topple.side(le));
        };
        e.registerEntityModifier((Class) HeroRenderer.class, (BiConsumer) ours);
        e.registerEntityModifier((Class) WildRenderer.class, (BiConsumer) ours);
        BiConsumer<Entity, EntityRenderState> vanilla = (entity, state) -> {
            if (entity instanceof AwakenMob || !(entity instanceof LivingEntity le) || !(state instanceof LivingEntityRenderState s) || s.deathTime > 0.0F) return;
            float a = Topple.over(le, state.partialTick);
            if (a <= 0.0F) return;
            s.deathTime = 1.0F + 12.5F * a * a; // vanilla turns sqrt((t - 1) / 20 * 1.6) of its 90 degrees
            s.hasRedOverlay = false;
        };
        e.registerEntityModifier((Class) LivingEntityRenderer.class, (BiConsumer) vanilla);
    }

    /** After the yaw in setupRotations (the pose already scaled by the entity's): over onto its side. */
    public static void roll(EntityRenderState s, PoseStack pose, float entityScale) {
        Float o = s.getRenderData(OVER);
        if (o == null || o == 0.0F) return;
        // a turn about z tips the top toward -x: it pivots on its bottom edge on that side
        float edge = -Math.signum(o) * s.boundingBoxWidth * 0.5F / entityScale;
        pose.translate(edge, 0.0F, 0.0F);
        pose.rotateDegrees(Axis.ZP, 90.0F * o);
        pose.translate(-edge, 0.0F, 0.0F);
    }
}
