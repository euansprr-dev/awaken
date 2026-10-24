package dev.awaken.hearth.client;

import dev.awaken.Awaken;
import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.town.Townsfolk;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Townsfolk use the player model with their own generated skins (textures/entity/townsfolk/&lt;name&gt;.png). */
public final class TownNpcRenderer extends SkinnedMobRenderer<TownNpc, SkinRenderState> {
    private static final Map<String, Identifier> SKINS = new HashMap<>();
    private static final Identifier FALLBACK = Awaken.id("textures/entity/townsfolk/oren.png");

    public TownNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0.5f);
    }

    @Override
    public SkinRenderState createRenderState() {
        return new SkinRenderState();
    }

    @Override
    public void extractRenderState(TownNpc npc, SkinRenderState state, float partialTicks) {
        super.extractRenderState(npc, state, partialTicks);
        state.wear(skin(npc.kind()), false);
    }

    /** "oren_blacksmith" → textures/entity/townsfolk/oren.png, resolved once per kind. */
    private static Identifier skin(String kind) {
        if (kind.isEmpty()) return FALLBACK;
        return SKINS.computeIfAbsent(kind, k -> Awaken.id("textures/entity/townsfolk/" + Townsfolk.skin(k) + ".png"));
    }
}
