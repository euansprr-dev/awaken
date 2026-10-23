package dev.awaken.gates.client;

import dev.awaken.Awaken;
import dev.awaken.gates.entity.SkinOwner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * Draws a gate entity with a player's skin (its owner's, or the default): the Mirror shade (dark), the Echo ghost
 * (translucent blue) and the Hollow follower (a black silhouette).
 */
public final class ShadeRenderer<T extends Mob & SkinOwner> extends HumanoidMobRenderer<T, AvatarRenderState, PlayerModel> {
    private static final ContextKey<Integer> TINT = new ContextKey<>(Awaken.id("gate_shade_tint"));
    private static final ContextKey<Boolean> TRANSLUCENT = new ContextKey<>(Awaken.id("gate_shade_translucent"));

    public ShadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(T entity, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.skin = DefaultPlayerSkin.getDefaultSkin();
        String owner = entity.skinOwner();
        if (!owner.isEmpty()) {
            try {
                UUID id = UUID.fromString(owner);
                var conn = Minecraft.getInstance().getConnection();
                PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
                state.skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
            } catch (IllegalArgumentException ignored) {
            }
        }
        state.setRenderData(TINT, entity.skinTint());
        state.setRenderData(TRANSLUCENT, entity.translucent());
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }

    @Override
    protected int getModelTint(AvatarRenderState state) {
        Integer t = state.getRenderData(TINT);
        return t == null ? -1 : t;
    }

    @Override
    protected RenderType getRenderType(AvatarRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        Boolean tr = state.getRenderData(TRANSLUCENT);
        if (tr != null && tr) return RenderTypes.entityTranslucent(getTextureLocation(state));
        return super.getRenderType(state, bodyVisible, forceTransparent, glowing);
    }
}
