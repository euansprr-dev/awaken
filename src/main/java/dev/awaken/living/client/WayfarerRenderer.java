package dev.awaken.living.client;

import dev.awaken.Awaken;
import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.living.wayfarer.Wayfarer;
import dev.awaken.living.wayfarer.Wayfarers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** A Wayfarer in their own skin ({@code textures/entity/wayfarer/<who>.png}), slim or wide as {@link Wayfarers} says. */
final class WayfarerRenderer extends SkinnedMobRenderer<Wayfarer, SkinRenderState> {
    WayfarerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0.5F);
    }

    @Override
    public SkinRenderState createRenderState() {
        return new SkinRenderState();
    }

    @Override
    public void extractRenderState(Wayfarer w, SkinRenderState state, float partialTicks) {
        super.extractRenderState(w, state, partialTicks);
        String who = w.who().isEmpty() ? "wren" : w.who();
        Wayfarers.Person p = Wayfarers.get(who);
        state.wear(Awaken.id("textures/entity/wayfarer/" + who + ".png"), p != null ? p.slim() : who.equals("risen"));
    }
}
