package dev.awaken.gates.client;

import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.gates.entity.SkinOwner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Draws a gate entity with a player's skin (its owner's, or the default): the Mirror shade (dark), the Echo ghost
 * (translucent blue), the Hollow follower (a black silhouette) and a Gauntlet's ghost (translucent pale gold, lit from
 * within).
 */
public final class ShadeRenderer<T extends Mob & SkinOwner> extends SkinnedMobRenderer<T, ShadeRenderer.State> {
    /** Owner strings parsed once (a frame must not parse a UUID per entity); NO_OWNER marks a bad string. */
    private static final Map<String, UUID> OWNERS = new HashMap<>();
    private static final UUID NO_OWNER = new UUID(0L, 0L);

    public static final class State extends SkinRenderState {
        int tint = -1;
        boolean translucent;
    }

    public ShadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0.4F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.wear(skinOf(entity.skinOwner()));
        state.tint = entity.skinTint();
        state.translucent = entity.translucent();
    }

    private static PlayerSkin skinOf(String owner) {
        if (owner.isEmpty()) return DefaultPlayerSkin.getDefaultSkin();
        if (OWNERS.size() > 256) OWNERS.clear();
        UUID id = OWNERS.computeIfAbsent(owner, ShadeRenderer::parse);
        if (id == NO_OWNER) return DefaultPlayerSkin.getDefaultSkin();
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }

    private static UUID parse(String owner) {
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return NO_OWNER;
        }
    }

    @Override
    protected int getModelTint(State state) {
        return state.tint;
    }

    /** A Gauntlet's ghost is a figure of light: lit by nothing around it. */
    @Override
    protected int getBlockLightLevel(T entity, net.minecraft.core.BlockPos pos) {
        return entity instanceof dev.awaken.gates.entity.GauntletGhost ? 15 : super.getBlockLightLevel(entity, pos);
    }

    @Override
    protected RenderType getRenderType(State state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        if (state.translucent) return RenderTypes.entityTranslucent(getTextureLocation(state));
        return super.getRenderType(state, bodyVisible, forceTransparent, glowing);
    }
}
