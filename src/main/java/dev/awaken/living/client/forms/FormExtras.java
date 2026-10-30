package dev.awaken.living.client.forms;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.living.combat.FormShape;
import dev.awaken.living.combat.Forms.Species;
import dev.awaken.living.forms.FormFit;
import dev.awaken.living.forms.FormRigs;
import dev.awaken.mobs.client.anim.HeroRig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A form's own pieces on the vanilla body a model pack moves (SPEC §21.13 item 17): one layer on each species' vanilla renderer. From C
 * up, while Fresh Animations (or any pack EMF reads) has a model for the species, {@link FormBodies} leaves the vanilla body to it at the
 * rung's height and marks the state; this layer then draws the rig's crowns, plates, horns and crystals ({@link FormFit}) on the vanilla
 * parts they grow from, each carried by its part's motion away from rest, so they follow whatever animates it, in the form's paint and
 * glow. Read from the parts' own fields, not their {@code translateAndRotate} (EMF moves an arm's to the hand there). Nothing allocated
 * per frame.
 */
final class FormExtras<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    private static final double FAR_SQ = 96.0 * 96.0;
    /** Scratch for the parts' turns: render thread only, applied at once. */
    private static final Quaternionf TURN = new Quaternionf();

    /** The pieces on one vanilla part: that part's rest, and the pieces baked as one model in the vanilla model's frame at rest. */
    private static final class Anchor {
        final String part;
        final float px, py, pz;
        final Quaternionf restBack;
        final Model.Simple model;
        ModelPart vanilla;

        Anchor(String part, FormFit.Part rest, Model.Simple model) {
            this.part = part;
            this.px = rest.px();
            this.py = rest.py();
            this.pz = rest.pz();
            this.restBack = new Quaternionf().rotationX(-rest.rx());
            this.model = model;
        }
    }

    /** One rung's pieces and paints: the skin by family (the open-eyed one) and the glow. */
    private record Rung(Anchor[] anchors, RenderType[] skin, RenderType glow) {}

    private final Rung[] rungs = new Rung[FormRigs.LAST + 1];
    private ModelPart root;

    FormExtras(RenderLayerParent<S, M> parent, Species sp) {
        super(parent);
        for (int r = FormRigs.FIRST; r <= FormRigs.LAST; r++) {
            HeroRig rig = FormRigs.get(sp, r);
            if (rig == null) continue;
            Map<String, MeshDefinition> meshes = new LinkedHashMap<>();
            for (FormFit.Piece pc : FormFit.fit(rig, sp.layout, FormShape.heightFactor(sp.layout, r))) {
                CubeListBuilder cubes = CubeListBuilder.create();
                for (HeroRig.Cube c : pc.cubes()) {
                    cubes.texOffs(c.u(), c.v()).mirror(c.mirror())
                            .addBox(c.x(), c.y(), c.z(), c.w(), c.h(), c.d(), c.inflate() == 0 ? CubeDeformation.NONE : new CubeDeformation(c.inflate()));
                }
                meshes.computeIfAbsent(pc.anchor(), k -> new MeshDefinition()).getRoot().addOrReplaceChild(pc.bone(), cubes,
                        new PartPose(pc.x(), pc.y(), pc.z(), pc.xRot(), pc.yRot(), pc.zRot(), pc.scale(), pc.scale(), pc.scale()));
            }
            List<Anchor> anchors = new ArrayList<>();
            meshes.forEach((part, mesh) -> anchors.add(new Anchor(part, FormFit.part(sp.layout, part),
                    new Model.Simple(LayerDefinition.create(mesh, rig.texW, rig.texH).bakeRoot(), RenderTypes::entityCutout))));
            String base = "textures/entity/forms/body/" + rig.id + "_" + sp.id;
            RenderType[] skin = new RenderType[3];
            for (int f = 0; f < 3; f++) {
                Identifier t = FormRenderer.exists(Awaken.id(base + "_" + f + ".png"));
                skin[f] = t == null ? null : RenderTypes.entityCutout(t);
            }
            Identifier g = FormRenderer.exists(Awaken.id(base + "_glow.png"));
            if (skin[0] != null && !anchors.isEmpty())
                rungs[r] = new Rung(anchors.toArray(Anchor[]::new), skin, g == null ? null : RenderTypes.entityTranslucentEmissive(g));
        }
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S s, float yRot, float xRot) {
        Integer worn = s.getRenderData(FormBodies.EXTRAS);
        if (worn == null || s.isInvisible || s.distanceToCameraSq > FAR_SQ) return;
        Rung r = rungs[Mth.clamp(worn >> 2, 0, FormRigs.LAST)];
        if (r == null) return;
        ModelPart top = getParentModel().root();
        if (top != root) {
            root = top;
            for (Rung each : rungs) {
                if (each == null) continue;
                for (Anchor a : each.anchors) a.vanilla = top.hasChild(a.part) ? top.getChild(a.part) : null;
            }
        }
        RenderType skin = r.skin[worn & 3] != null ? r.skin[worn & 3] : r.skin[0];
        int overlay = LivingEntityRenderer.getOverlayCoords(s, 0.0F);
        float glow = Mth.clamp(0.7F + 0.075F * (worn >> 2), 0.0F, 1.0F) * (s.deathTime > 0 ? Math.max(0.0F, 1.0F - s.deathTime / 20.0F) : 1.0F);
        for (Anchor a : r.anchors) {
            ModelPart v = a.vanilla;
            if (v == null || !v.visible) continue;
            pose.pushPose();
            move(pose, top);
            move(pose, v);
            pose.rotate(a.restBack);
            pose.translate(-a.px / 16.0F, -a.py / 16.0F, -a.pz / 16.0F);
            collector.submitModel(a.model, Unit.INSTANCE, pose, skin, light, overlay, -1, null, s.outlineColor);
            if (r.glow != null && glow > 0.01F)
                collector.order(2).submitModel(a.model, Unit.INSTANCE, pose, r.glow, LightCoordsUtil.FULL_BRIGHT, overlay, ARGB.white(glow), null, s.outlineColor);
            pose.popPose();
        }
    }

    /** A part's pose as vanilla applies it. */
    private static void move(PoseStack pose, ModelPart p) {
        pose.translate(p.x / 16.0F, p.y / 16.0F, p.z / 16.0F);
        if (p.xRot != 0.0F || p.yRot != 0.0F || p.zRot != 0.0F) pose.rotate(TURN.rotationZYX(p.zRot, p.yRot, p.xRot));
        if (p.xScale != 1.0F || p.yScale != 1.0F || p.zScale != 1.0F) pose.scale(p.xScale, p.yScale, p.zScale);
    }
}
