package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.Mth;

/** Shared render glue: the per-frame look travels on the render state; textures are resolved once per def. */
final class AwRender {
    private AwRender() {}

    static final ContextKey<AwLook> LOOK = dev.awaken.mobs.client.model.AwModels.LOOK;
    private static final AwLook DEFAULT = new AwLook();

    /** Model-specific state filling (arms up, wings flapping, swelling...). */
    interface Filler<S> {
        void fill(AwakenMob mob, S state, float partialTicks, AwLook look);
    }

    /** Extra transforms on top of the entity scale (slime squish, creeper swell). */
    interface Scaler<S> {
        void scale(S state, PoseStack pose, AwLook look);
    }

    static AwLook look(LivingEntityRenderState s) {
        AwLook l = s.getRenderData(LOOK);
        return l != null ? l : DEFAULT;
    }

    static AwLook extract(AwakenMob mob, LivingEntityRenderState s, float partialTicks) {
        AwLook l = mob.look(partialTicks);
        s.setRenderData(LOOK, l);
        if (l.fallen) {
            s.deathTime = 18.0F;
            s.hasRedOverlay = false;
        }
        return l;
    }

    static Identifier vanilla(MobDef d) {
        return Identifier.withDefaultNamespace("textures/entity/" + d.model.vanillaTexture + ".png");
    }

    static Identifier skin(MobDef d) {
        return Awaken.id("textures/entity/mobs/" + d.id + ".png");
    }

    static Identifier glow(MobDef d) {
        return Awaken.id("textures/entity/mobs/" + d.id + "_glow.png");
    }

    /** The texture the model is drawn with: ours for our own models, else the vanilla creature's. */
    static Identifier base(MobDef d) {
        return d.model.ownTexture() ? skin(d) : vanilla(d);
    }

    static float flash(AwLook l, float ageInTicks) {
        if (!l.flash) return 0.0F;
        return ((int) (ageInTicks * 0.8F)) % 2 == 0 ? 0.0F : Mth.clamp(0.4F + l.swell * 0.6F, 0.4F, 1.0F);
    }
}
