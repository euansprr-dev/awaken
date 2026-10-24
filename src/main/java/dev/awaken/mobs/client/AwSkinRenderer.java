package dev.awaken.mobs.client;

import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Model;
import dev.awaken.mobs.def.Trait;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Player-shaped creatures: HUMAN wears its own skin, MIMIC the skin of the player it copies (wide Steve, the model's
 * vanilla texture, until it has one), at that skin's arm width. Same armour, theme skin and glow layers and the same
 * look (tint, see-through, hit flash) as {@link AwHumanoidRenderer}.
 */
final class AwSkinRenderer extends SkinnedMobRenderer<AwakenMob, SkinRenderState> {
    private final boolean mimic;
    private final Identifier own;

    AwSkinRenderer(EntityRendererProvider.Context ctx, MobDef def) {
        super(ctx, 0.5F);
        this.mimic = def.model == Model.MIMIC;
        this.own = AwRender.base(def);
        addArmorLayer(ctx);
        if (!def.model.ownTexture()) addLayer(new AwLayers.Skin<>(this, def));
        addLayer(new AwLayers.Glow<>(this, def));
    }

    @Override
    public SkinRenderState createRenderState() {
        return new SkinRenderState();
    }

    @Override
    public void extractRenderState(AwakenMob mob, SkinRenderState state, float partialTicks) {
        super.extractRenderState(mob, state, partialTicks);
        AwLook l = AwRender.extract(mob, state, partialTicks);
        var level = Minecraft.getInstance().level;
        Entity copied = mimic && l.mimic >= 0 && level != null ? level.getEntity(l.mimic) : null;
        if (copied instanceof AbstractClientPlayer p) state.wear(p.getSkin());
        else state.wear(own, false);
    }

    @Override
    protected int getModelTint(SkinRenderState state) {
        return AwRender.look(state).tint;
    }

    @Override
    protected @Nullable RenderType getRenderType(SkinRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        if (bodyVisible && AwRender.look(state).translucent) return RenderTypes.entityTranslucent(getTextureLocation(state));
        return super.getRenderType(state, bodyVisible, forceTransparent, glowing);
    }

    @Override
    protected float getWhiteOverlayProgress(SkinRenderState state) {
        return AwRender.flash(AwRender.look(state), state.ageInTicks);
    }

    @Override
    public boolean isEntityUpsideDown(AwakenMob mob) {
        return mob.def().has(Trait.UPSIDE_DOWN) || super.isEntityUpsideDown(mob);
    }
}
