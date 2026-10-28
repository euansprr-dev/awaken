package dev.awaken.living.client.forms;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.mobs.client.anim.HeroRenderState;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.model.HeroModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What a walker's form body carries and wears (SPEC §21.13): the vanilla creature's held items at the body's hands, and its armour on
 * vanilla's armour meshes posed to the body. The rig names its core ({@code head}, {@code torso}, {@code arm_r}, {@code arm_l},
 * {@code leg_r}, {@code leg_l}, each with its main cube first) and its hands ({@code hand_r}, {@code hand_l}); a hunched torso pivots at
 * the hips, so each armour piece stands where vanilla's would (the neck, the shoulder, the hip) with the composed turn of every bone above
 * it, and grows with the bone's bulk.
 */
final class FormGear extends RenderLayer<HeroRenderState, HeroModel> {
    static boolean humanoid(String body) {
        return body.equals("zombie") || body.equals("skeleton");
    }

    private static final String[] CORE = {"head", "torso", "arm_r", "arm_l", "leg_r", "leg_l"};
    private static final String[] VANILLA = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    /** The armour mesh's box of each core part (width, height, depth) and where its pivot sits in it (fractions of the box). */
    private static final float[][] BOX = {{8, 8, 8}, {8, 12, 4}, {4, 12, 4}, {4, 12, 4}, {4, 12, 4}, {4, 12, 4}};
    /** The vanilla body's own box of each part: a skeleton's limbs are two pixels thick under the same armour. */
    private static final float[][] SKELETON_BOX = {{8, 8, 8}, {8, 12, 4}, {2, 12, 2}, {2, 12, 2}, {2, 12, 2}, {2, 12, 2}};
    private static final float[][] PIVOT_IN_BOX = {{0.5F, 1.0F, 0.5F}, {0.5F, 0.0F, 0.5F}, {0.75F, 2 / 12.0F, 0.5F}, {0.25F, 2 / 12.0F, 0.5F},
            {0.5F, 0.0F, 0.5F}, {0.5F, 0.0F, 0.5F}};

    private final ModelPart[][] core = new ModelPart[6][];
    /** Per core part: the pivot vanilla's armour wants, in the bone's own space, and the bulk over vanilla's box. */
    private final float[][] anchor = new float[6][3], bulk = new float[6][3];
    private final ModelPart[] handR, handL;
    private final ArmorModelSet<Piece> armour;
    private final EquipmentLayerRenderer equipment;
    private final Quaternionf q = new Quaternionf(), r = new Quaternionf();
    private final Vector3f v = new Vector3f(), p = new Vector3f();

    FormGear(FormRenderer parent, EntityRendererProvider.Context ctx) {
        super(parent);
        HeroModel m = parent.getModel();
        HeroRig rig = parent.rig;
        for (int k = 0; k < 6; k++) {
            core[k] = FormRenderer.chain(m, rig, CORE[k]);
            int i = rig.bone(CORE[k]);
            // the part's own box: every cube of its bone (a skeleton's chest is its yoke, ribs and spine). The rigs hang what grows on a
            // part on a child "gear" bone, so the core bone holds only what armour should fit
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            if (i >= 0) {
                for (HeroRig.Cube c : rig.bones.get(i).cubes()) {
                    lo[0] = Math.min(lo[0], c.x());
                    lo[1] = Math.min(lo[1], c.y());
                    lo[2] = Math.min(lo[2], c.z());
                    hi[0] = Math.max(hi[0], c.x() + c.w());
                    hi[1] = Math.max(hi[1], c.y() + c.h());
                    hi[2] = Math.max(hi[2], c.z() + c.d());
                }
            }
            if (lo[0] > hi[0]) {
                bulk[k][0] = bulk[k][1] = bulk[k][2] = 1;
                continue;
            }
            float[] own = (parent.body.equals("skeleton") ? SKELETON_BOX : BOX)[k];
            for (int a = 0; a < 3; a++) {
                float size = hi[a] - lo[a];
                // only the torso stretches tall: a short skull still wears a whole helmet from its crown, a long arm a whole sleeve
                // from its shoulder, so a piece hangs from the top of its box
                bulk[k][a] = a == 1 && k != 1 ? 1.0F : size / own[a];
                // the armour box, grown by the bulk, centred on the part's box across and hung from its top
                anchor[k][a] = a == 1 ? lo[a] + BOX[k][a] * bulk[k][a] * PIVOT_IN_BOX[k][a]
                        : (lo[a] + hi[a]) / 2 + (PIVOT_IN_BOX[k][a] - 0.5F) * BOX[k][a] * bulk[k][a];
            }
        }
        handR = FormRenderer.chain(m, rig, "hand_r");
        handL = FormRenderer.chain(m, rig, "hand_l");
        armour = (parent.body.equals("skeleton") ? ModelLayers.SKELETON_ARMOR : ModelLayers.ZOMBIE_ARMOR).map(id -> new Piece(ctx.bakeLayer(id)));
        equipment = ctx.getEquipmentRenderer();
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HeroRenderState s, float yRot, float xRot) {
        FormBody b = s.getRenderData(FormBodies.BODY);
        if (b == null || s.isInvisible || b.vanilla == null) return;
        if (b.vanilla instanceof ArmedEntityRenderState armed) {
            hand(armed.rightHandItemState, handR, false, pose, collector, light, s);
            hand(armed.leftHandItemState, handL, true, pose, collector, light, s);
        }
        if (b.vanilla instanceof HumanoidRenderState h) {
            boolean any = !h.headEquipment.isEmpty() || !h.chestEquipment.isEmpty() || !h.legsEquipment.isEmpty() || !h.feetEquipment.isEmpty();
            if (!any) return;
            snapshot(b);
            piece(h.chestEquipment, EquipmentSlot.CHEST, pose, collector, light, s);
            piece(h.legsEquipment, EquipmentSlot.LEGS, pose, collector, light, s);
            piece(h.feetEquipment, EquipmentSlot.FEET, pose, collector, light, s);
            piece(h.headEquipment, EquipmentSlot.HEAD, pose, collector, light, s);
        }
    }

    /** As vanilla's item-in-hand layer, from the body's hand bone (which sits where vanilla's hand would be, ten pixels down the arm). */
    private static void hand(ItemStackRenderState item, ModelPart[] chain, boolean left, PoseStack pose, SubmitNodeCollector collector, int light, HeroRenderState s) {
        if (item.isEmpty() || chain.length == 0) return;
        pose.pushPose();
        FormRenderer.standAt(chain, pose);
        pose.rotateDegrees(Axis.XP, -90.0F);
        pose.rotateDegrees(Axis.YP, 180.0F);
        pose.translate((left ? -1 : 1) / 16.0F, 2 / 16.0F, 0.0F);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, s.outlineColor);
        pose.popPose();
    }

    private void piece(ItemStack stack, EquipmentSlot slot, PoseStack pose, SubmitNodeCollector collector, int light, HeroRenderState s) {
        Equippable e = stack.get(DataComponents.EQUIPPABLE);
        if (e == null || !HumanoidArmorLayer.shouldRender(stack, slot) || e.assetId().isEmpty()) return;
        EquipmentClientInfo.LayerType type = slot == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        equipment.renderLayers(type, e.assetId().get(), armour.get(slot), s, stack, pose, collector, light, s.outlineColor);
    }

    /** Each core part's pose relative to the model root (the model is posed for this state now), where vanilla's mesh wants its pivot. */
    private void snapshot(FormBody b) {
        for (int k = 0; k < 6; k++) {
            ModelPart[] chain = core[k];
            int o = k * 9;
            if (chain.length == 0) {
                java.util.Arrays.fill(b.armour, o, o + 9, 0.0F); // no such bone: scale 0 hides that piece
                continue;
            }
            p.set(0, 0, 0);
            q.identity();
            for (ModelPart part : chain) {
                p.add(q.transform(v.set(part.x, part.y, part.z)));
                q.mul(r.rotationZYX(part.zRot, part.yRot, part.xRot));
            }
            p.add(q.transform(v.set(anchor[k][0], anchor[k][1], anchor[k][2])));
            q.getEulerAnglesZYX(v);
            b.armour[o] = p.x;
            b.armour[o + 1] = p.y;
            b.armour[o + 2] = p.z;
            b.armour[o + 3] = v.x;
            b.armour[o + 4] = v.y;
            b.armour[o + 5] = v.z;
            b.armour[o + 6] = bulk[k][0];
            b.armour[o + 7] = bulk[k][1];
            b.armour[o + 8] = bulk[k][2];
        }
    }

    /** One of vanilla's armour meshes, posed from the body's snapshot when it is drawn. */
    private static final class Piece extends Model<HeroRenderState> {
        private final ModelPart[] parts = new ModelPart[6];

        Piece(ModelPart root) {
            super(root, RenderTypes::armorCutoutNoCull);
            for (int k = 0; k < 6; k++) parts[k] = root.hasChild(VANILLA[k]) ? root.getChild(VANILLA[k]) : null;
        }

        @Override
        public void setupAnim(HeroRenderState s) {
            super.setupAnim(s);
            FormBody b = s.getRenderData(FormBodies.BODY);
            if (b == null) return;
            for (int k = 0; k < 6; k++) {
                ModelPart part = parts[k];
                if (part == null) continue;
                int o = k * 9;
                part.x = b.armour[o];
                part.y = b.armour[o + 1];
                part.z = b.armour[o + 2];
                part.xRot = b.armour[o + 3];
                part.yRot = b.armour[o + 4];
                part.zRot = b.armour[o + 5];
                part.xScale = b.armour[o + 6];
                part.yScale = b.armour[o + 7];
                part.zScale = b.armour[o + 8];
            }
        }
    }
}
