package dev.awaken.mobs.client.life;

import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.FaunaMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Our hand-built bodies (the serpent, the Listener, the bell, the lantern, the shade, the moth, stag, tortoise, ray,
 * scarab, lizard, snail, mushroom and marmot): each poses itself in {@link #pose}, then the life layer goes on top.
 * The renderer binds the creature's temperament once ({@link #bindLife}).
 */
public abstract class LifeModel extends EntityModel<LivingEntityRenderState> {
    private @Nullable LifeRig life;

    protected LifeModel(ModelPart root) {
        super(root);
    }

    protected LifeModel(ModelPart root, Function<Identifier, RenderType> renderType) {
        super(root, renderType);
    }

    public LifeRig bindLife(LifeProfile p) {
        return bindLife(p, null);
    }

    /** Binds the temperament and, when {@code id} has one, the creature's motion profile (its flight, SPEC §23). */
    public LifeRig bindLife(LifeProfile p, @Nullable String id) {
        if (life == null || life.p != p) life = LifeRig.of(root(), p, null, false, id);
        return life;
    }

    public @Nullable LifeRig life() {
        return life;
    }

    @Override
    public final void setupAnim(LivingEntityRenderState s) {
        super.setupAnim(s);
        pose(s);
        if (life != null) life.apply(Life.from(s), s);
    }

    /**
     * Whether a pose of the body's own holds its legs this frame (tucked, dug in, up on its haunches): the feet of its
     * motion profile let go to it (SPEC §23, "Motion"). Resting and burrowing, unless a body says otherwise.
     */
    public boolean legsHeld(AwLook l) {
        return (l.fauna & (FaunaMob.P_REST | FaunaMob.P_BURROW)) != 0;
    }

    /** The body's own pose and gait for this frame (after the reset to the rest pose). */
    protected abstract void pose(LivingEntityRenderState s);
}
