package dev.awaken.hearth.client;

import dev.awaken.Awaken;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.town.Townsfolk;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Townsfolk use the player model with their own generated skins (textures/entity/townsfolk/&lt;name&gt;.png). */
public final class TownNpcRenderer extends HumanoidMobRenderer<TownNpc, AvatarRenderState, PlayerModel> {
    private static final Map<String, Identifier> SKINS = new HashMap<>();
    private static final Identifier FALLBACK = Awaken.id("textures/entity/townsfolk/oren.png");
    private static final String KIND = "awaken_kind";

    public TownNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(TownNpc npc, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(npc, state, partialTicks);
        state.showCape = false;
        state.id = npc.getId();
        state.setRenderData(KIND_KEY, npc.kind());
    }

    private static final net.minecraft.util.context.ContextKey<String> KIND_KEY = new net.minecraft.util.context.ContextKey<>(Awaken.id(KIND));

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        String kind = state.getRenderData(KIND_KEY);
        if (kind == null || kind.isEmpty()) return FALLBACK;
        return SKINS.computeIfAbsent(Townsfolk.skin(kind), s -> Awaken.id("textures/entity/townsfolk/" + s + ".png"));
    }
}
