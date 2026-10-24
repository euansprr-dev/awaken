package dev.awaken.powers.client.fx;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.enums.State;
import dev.awaken.Awaken;
import dev.awaken.core.api.PlayerClips;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * Full-body cast clips through the Player Animation Library. The ONLY class that touches the library: everything else
 * reaches it through {@link CastAnims}, and only after {@code ModList} said the library is loaded, so a client without
 * it never loads these imports. Each player (client side) gets one Awaken layer; a cast starts a clip on it (named by
 * {@code CastClips}) and the vanilla arm poses step aside for that player. First person is untouched: the library's
 * first-person mode stays off and {@link CastAnims} keeps drawing the arms. Any failure switches the library path off
 * for the session and the vanilla poses carry on.
 * <p>
 * Bendable Cuboids (optional, needs no code from us) draws the library's elbow and knee bends; while it is loaded each
 * clip plays its {@code _bent} twin, whose limbs bend and whose upper arms and thighs turn to keep the hands and feet
 * where the straight clip puts them.
 * <p>
 * A second layer above the casts plays other modules' clips (the Awakened Body's moves) through
 * {@link PlayerClips}, which this class installs; a move's pose then wins over a cast's for as long as it runs.
 */
final class PalCasts {
    private PalCasts() {}

    private static final Identifier LAYER = Awaken.id("power_casts");
    /** Above the library's own default layer (1000) so a cast wins over idle emotes. */
    private static final int PRIORITY = 1500;
    /** Other modules' clips (body moves): above the casts, so a slide or a roll keeps its whole body. */
    private static final Identifier MOVES = Awaken.id("player_clips");
    private static final int MOVES_PRIORITY = 1600;
    /** Bendable Cuboids draws bends: play each clip's bent twin. */
    private static final boolean BENT = ModList.get().isLoaded("bendable_cuboids");
    private static boolean broken;

    /** Our layer: a controller with nothing to play by default and a mirror for left-handed players. */
    private static final class Layer extends PlayerAnimationController {
        final MirrorModifier mirror = new MirrorModifier();

        Layer(Avatar avatar) {
            super(avatar, (controller, state, setter) -> PlayState.STOP);
            mirror.enabled = false;
            addModifierBefore(mirror);
        }
    }

    static void init() {
        // every Avatar (players, mannequins) gets our layers on the client; server-side avatars get none
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY,
                avatar -> avatar.level().isClientSide() ? new Layer(avatar) : null);
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(MOVES, MOVES_PRIORITY,
                avatar -> avatar.level().isClientSide() ? new Layer(avatar) : null);
        PlayerClips.install(new PlayerClips.Player() {
            @Override public boolean play(int entityId, String clip, int fadeIn) {
                return PalCasts.play(MOVES, entityId, clip, fadeIn);
            }

            @Override public void stop(int entityId) {
                PalCasts.stop(MOVES, entityId);
            }
        });
    }

    private static @Nullable Layer layer(Identifier id, int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (broken || mc.level == null) return null;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof Avatar avatar)) return null;
        IAnimation a = PlayerAnimationAccess.getPlayerAnimationLayer(avatar, id);
        return a instanceof Layer l ? l : null;
    }

    private static @Nullable Layer layer(int entityId) {
        return layer(LAYER, entityId);
    }

    /**
     * Starts a cast clip on a player; false when the library can't pose this entity (not an avatar, no such clip), so
     * the caller keeps the vanilla pose. {@code fadeIn}: ticks to blend from the current pose (vanilla or another clip).
     */
    static boolean play(int entityId, String clip, int fadeIn) {
        return play(LAYER, entityId, clip, fadeIn);
    }

    private static boolean play(Identifier layer, int entityId, String clip, int fadeIn) {
        try {
            Layer l = layer(layer, entityId);
            if (l == null) return false;
            l.mirror.enabled = l.getAvatar().getMainArm() == HumanoidArm.LEFT;
            boolean active = l.isActive();
            int fade = Math.max(1, active ? Math.max(2, fadeIn) : fadeIn);
            l.unpause();
            Identifier id = Awaken.id(clip);
            if (BENT) {
                Identifier bent = Awaken.id(clip + "_bent");
                if (PlayerAnimResources.hasAnimation(bent)) id = bent;
            }
            return l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, EasingType.EASE_IN_OUT_SINE), id, true);
        } catch (RuntimeException | LinkageError ex) {
            fail(ex);
            return false;
        }
    }

    /** Hit-stop: the clip holds still while {@code frozen}. */
    static void freeze(int entityId, boolean frozen) {
        try {
            Layer l = layer(entityId);
            if (l == null) return;
            if (frozen && l.getAnimationState() == State.RUNNING) l.pause();
            else if (!frozen && l.getAnimationState() == State.PAUSED) l.unpause();
        } catch (RuntimeException | LinkageError ex) {
            fail(ex);
        }
    }

    /** Stops whatever cast clip a player is in, at once (logout, respawn, settings off). */
    static void stop(int entityId) {
        stop(LAYER, entityId);
    }

    private static void stop(Identifier layer, int entityId) {
        try {
            Layer l = layer(layer, entityId);
            if (l != null && l.isActive()) l.stop();
        } catch (RuntimeException | LinkageError ex) {
            fail(ex);
        }
    }

    private static void fail(Throwable ex) {
        if (broken) return;
        broken = true;
        Awaken.LOG.warn("Player Animation Library clips failed; using the vanilla poses from now on", ex);
    }
}
