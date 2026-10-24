package dev.awaken.core.client.skin;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A non-player body wearing a player-format skin. Deliberately not an AvatarRenderState: the dispatcher would hand that
 * to the vanilla player renderer (see {@link SkinnedMobRenderer}).
 */
public class SkinRenderState extends HumanoidRenderState {
    private static final PlayerSkin DEFAULT = DefaultPlayerSkin.getDefaultSkin();

    /** The body texture (64x64 player layout). */
    public Identifier skin = DEFAULT.body().texturePath();
    /** Three-pixel arms (Alex-style skins). */
    public boolean slim = DEFAULT.model() == PlayerModelType.SLIM;
    public boolean showHat = true;
    public boolean showJacket = true;
    public boolean showSleeves = true;
    public boolean showPants = true;

    /** Wears a player's skin (texture and arm width). */
    public void wear(PlayerSkin s) {
        skin = s.body().texturePath();
        slim = s.model() == PlayerModelType.SLIM;
    }

    /** Wears one of our own skins. */
    public void wear(Identifier texture, boolean slimArms) {
        skin = texture;
        slim = slimArms;
    }
}
