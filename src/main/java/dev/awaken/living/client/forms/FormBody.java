package dev.awaken.living.client.forms;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.living.combat.Forms.Species;
import dev.awaken.mobs.client.anim.HeroRenderState;
import dev.awaken.mobs.entity.AwLook;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.jspecify.annotations.Nullable;

/**
 * One formed creature's body on the client (SPEC §21.13): its own render state and look (so the life layer keeps its breath, blinks and
 * springs from frame to frame), the distance it has walked, its swing, the move it acts out, and what the gear layer and the eye halos
 * read at draw time. Made once per creature, reused every frame: nothing here allocates while it is drawn.
 */
final class FormBody implements SubmitNodeCollector.CustomGeometryRenderer {
    final Species species;
    final int rung, family;
    final FormRenderer renderer;
    final HeroRenderState hero;
    final AwLook look = new AwLook();

    /** This frame's vanilla state (its held items and worn armour), set before the body is drawn. */
    @Nullable LivingEntityRenderState vanilla;

    // the gait: blocks walked, measured from frame to frame so the feet never slide
    double lastX, lastZ;
    boolean placed;
    float gait;

    /** A blade skeleton's hunting stance, 0..1 (FormRenderer). */
    float stance;

    // the melee swing
    boolean wasSwinging;
    float meleeStart = -1000;

    // the move it acts out (FormCue)
    String cue = "";
    long cueStart;
    int cueWindup, cueHold;
    boolean cueDirty;
    int cueWindupClip = -1, cueStrikeClip = -1, cueOnceClip = -1;
    /** A wind-up cut short fades from where it was. */
    int fadeClip = -1;
    float fadeTime, fadeAt = -1;

    /** The six core parts' pose for the armour (x, y, z, xRot, yRot, zRot, xScale, yScale, zScale each), filled when the gear is drawn. */
    final float[] armour = new float[6 * 9];

    // the eye halo the custom geometry draws (two submits a frame, one per eye; the pose carries which)
    float haloSize;
    int haloColour;

    FormBody(Species species, int rung, int family, FormRenderer renderer) {
        this.species = species;
        this.rung = rung;
        this.family = family;
        this.renderer = renderer;
        this.hero = renderer.createRenderState();
    }

    void cue(String move, long start, int windup, int hold) {
        if (move.isEmpty() && !cue.isEmpty()) { // let a wind-up go from where it was, over a moment
            fadeClip = cueWindupClip;
            fadeAt = Float.NaN; // the renderer fills the time it was cut at on its next frame
        }
        cue = move;
        cueStart = start;
        cueWindup = windup;
        cueHold = hold;
        cueDirty = true;
    }

    /** A soft dot of the rank's light, square to the face, centred on the eye (the halo texture fades to its edge). */
    @Override
    public void render(PoseStack.Pose pose, VertexConsumer vc) {
        float r = haloSize / 16.0F;
        int c = haloColour;
        vc.addVertex(pose, -r, -r, -0.02F).setColor(c).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, -r, r, -0.02F).setColor(c).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, r, r, -0.02F).setColor(c).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, r, -r, -0.02F).setColor(c).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, -1);
    }
}
