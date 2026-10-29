package dev.awaken.armour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Worn armour drawn as sculpted meshes (SPEC §44.1): each piece's mesh on each bone it rides, moved with the bone as the
 * model is posed, through the armour shader. On players (both arm widths), mannequins, armour stands and humanoid mobs.
 * The cape hangs from its yoke in three links that swing with the player's cape motion (vanilla's lean and flap, each
 * link a little further); the cloth over the legs is pushed by the stride (the front by the leg swung forward, the back
 * by the leg swung back). Nothing per frame but the vertices.
 */
final class ArmourLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final ArmorType[] SLOTS = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

    ArmourLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector out, int light, S state, float yRot, float xRot) {
        ItemStack[] worn = state.getRenderData(ArmourClient.WORN);
        if (state.isInvisible || worn == null) return;
        for (ArmorType slot : SLOTS) {
            ItemStack stack = worn[slot.ordinal()];
            if (stack == null) continue;
            ArmourLooks.Look look = ArmourLooks.of(stack);
            if (look == null || look.piece() != slot) continue;
            ArmourMesh[] bones = ArmourMeshes.piece(look.set(), slot);
            if (bones == null) continue;
            boolean foil = stack.hasFoil();
            for (Bone b : ArmourMeshes.BONES) {
                ArmourMesh mesh = bones[b.ordinal()];
                if (mesh == null) continue;
                ps.pushPose();
                if (place(ps, getParentModel(), b, state)) draw(out, ps, mesh, look.set(), light, foil);
                ps.popPose();
            }
        }
    }

    /** Moves the pose onto a bone as posed this frame; false where the bone is hidden (an armour stand's arms). */
    private static boolean place(PoseStack ps, HumanoidModel<?> m, Bone b, HumanoidRenderState state) {
        boolean stand = state instanceof ArmorStandRenderState;
        switch (b) {
            case HEAD -> {
                return bone(ps, m.head);
            }
            case BODY -> {
                return bone(ps, m.body);
            }
            case RIGHT_ARM -> {
                return bone(ps, m.rightArm);
            }
            case LEFT_ARM -> {
                return bone(ps, m.leftArm);
            }
            case RIGHT_LEG, LEFT_LEG -> {
                if (stand) ps.translate(0, -1 / 16F, 0); // a stand's own legs hang a texel lower than its armour's
                return bone(ps, b == Bone.RIGHT_LEG ? m.rightLeg : m.leftLeg);
            }
            case CAPE0, CAPE1, CAPE2 -> {
                if (!bone(ps, m.body)) return false;
                cape(ps, state, b.ordinal() - Bone.CAPE0.ordinal());
                return true;
            }
            case FRONT_CLOTH -> {
                if (!bone(ps, m.body)) return false;
                ps.translate(0, 13 / 16F, -5 / 16F);
                ps.rotate(Axis.XP, Math.min(0F, Math.min(m.rightLeg.xRot, m.leftLeg.xRot)) * 0.9F);
                return true;
            }
            case BACK_CLOTH -> {
                if (!bone(ps, m.body)) return false;
                ps.translate(0, 13 / 16F, 4 / 16F);
                ps.rotate(Axis.XP, Math.max(0F, Math.max(m.rightLeg.xRot, m.leftLeg.xRot)) * 0.9F);
                return true;
            }
        }
        return false;
    }

    private static boolean bone(PoseStack ps, ModelPart part) {
        if (!part.visible) return false;
        part.translateAndRotate(ps);
        return true;
    }

    /**
     * The cape from its yoke (a texel under the shoulders, behind the cuirass): turned back as vanilla's cape is (its
     * lean, flap and sideways lean), then each further link turned a little more, more as it streams out.
     */
    private static void cape(PoseStack ps, HumanoidRenderState state, int link) {
        float lean = 0, lean2 = 0, flap = 0;
        if (state instanceof AvatarRenderState a) {
            lean = a.capeLean;
            lean2 = a.capeLean2;
            flap = a.capeFlap;
        }
        ps.translate(0, 0.5F / 16, 4F / 16);
        ps.rotateDegrees(Axis.ZP, lean2 / 2);
        ps.rotateDegrees(Axis.XP, 6 + lean / 2 + flap);
        float curl = 4 + Math.min(lean, 60F) * 0.08F + Math.abs(flap) * 0.1F;
        for (int k = 0; k < link; k++) {
            ps.translate(0, 6F / 16, 0);
            ps.rotateDegrees(Axis.XP, curl);
        }
    }

    /** One mesh where the pose stands: the armour shader, or vanilla's entity cutout if that didn't build. */
    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourMesh mesh, ArmourSet set, int light, boolean foil) {
        boolean ours = ArmourPipelines.ok();
        var texture = ArmourMeshes.texture(set);
        int packed = ours ? (foil ? 256 : 0) << 16 : OverlayTexture.NO_OVERLAY;
        out.submitCustomGeometry(ps, ours ? ArmourPipelines.armour(texture) : ArmourPipelines.fallback(texture),
                (pose, buffer) -> emit(mesh, pose, buffer, light, packed, ours));
    }

    private static void emit(ArmourMesh mesh, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, boolean ours) {
        int accent = ours ? mesh.accent() : 0xFFFFFF;
        int r = accent >> 16 & 255, g = accent >> 8 & 255, b = accent & 255;
        float[] p = mesh.pos(), uv = mesh.uv();
        byte[] n = mesh.normal(), along = mesh.along();
        for (int i = 0, count = mesh.vertices(); i < count; i++) {
            corner(buffer, pose, p, uv, n, i, r, g, b, ours ? along[i] & 255 : 255, light, overlay);
            if (!ours && i % 3 == 2) corner(buffer, pose, p, uv, n, i, r, g, b, 255, light, overlay);
        }
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, float[] p, float[] uv, byte[] n, int i, int r, int g, int b, int a,
                               int light, int overlay) {
        buffer.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
                .setColor(r, g, b, a)
                .setUv(uv[i * 2], uv[i * 2 + 1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, n[i * 3] / 127F, n[i * 3 + 1] / 127F, n[i * 3 + 2] / 127F);
    }
}
