package dev.awaken.holo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.holo.HoloKind;
import dev.awaken.holo.HoloNet;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What other players see of your holo panels, and you of theirs: the wrist pose ({@link HoloArmPose}), a band of
 * light on the wrist, and a small content-free glow card over it (a WORLD-anchored panel: depth-tested, so walls
 * hide it, and never what is on the real panel). States arrive through {@code awaken:holo_state_relay}; your own
 * comes from your panel (third person). Open and close blend over 300 ms.
 */
public final class HoloOthers {
    private HoloOthers() {}

    /** One player's raise, carried on their render state. */
    static final class Raise {
        int entity = -1;
        HoloKind kind = HoloKind.NONE;
        float progress;
    }

    private static final Raise[] SLOTS = new Raise[64];
    private static final Raise SELF = new Raise();
    static final ContextKey<Raise> KEY = new ContextKey<>(Awaken.id("holo_raise"));

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Raise();
    }

    static void onRelay(HoloNet.Relay msg) {
        HoloKind kind = HoloKind.byId(msg.kind());
        if (kind == null) return;
        Raise free = null;
        for (Raise r : SLOTS) {
            if (r.entity == msg.entityId()) {
                r.kind = kind;
                return;
            }
            if (free == null && r.entity < 0) free = r;
        }
        if (free != null && kind != HoloKind.NONE) {
            free.entity = msg.entityId();
            free.kind = kind;
            free.progress = 0;
        }
    }

    static void clear() {
        for (Raise r : SLOTS) {
            r.entity = -1;
            r.kind = HoloKind.NONE;
            r.progress = 0;
        }
    }

    /** Once a frame: ease every raise toward its state; free slots that have fully lowered. */
    static void advance(float dtMs) {
        float step = dtMs / 300f;
        for (Raise r : SLOTS) {
            if (r.entity < 0) continue;
            r.progress = r.kind != HoloKind.NONE ? Math.min(1, r.progress + step) : Math.max(0, r.progress - step);
            if (r.kind == HoloKind.NONE && r.progress <= 0) r.entity = -1;
        }
    }

    private static Raise find(int entity) {
        for (Raise r : SLOTS) if (r.entity == entity) return r;
        return null;
    }

    /** 0..1, eased, from the render state the pose and the layer see. */
    static float progress(EntityRenderState state) {
        Raise r = state.getRenderData(KEY);
        if (r == null) return 0;
        float p = r.progress;
        return p * p * (3 - 2 * p);
    }

    /** Render state modifier: puts the raise on the state and the wrist pose on the wrist arm. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(state instanceof HumanoidRenderState s) || !(entity instanceof AbstractClientPlayer player)) return;
        Minecraft mc = Minecraft.getInstance();
        Raise r;
        if (player == mc.player) {
            HoloPanel p = HoloClient.wristPanel();
            if (p == null || !p.timeline.active() || mc.options.getCameraType().isFirstPerson()) {
                s.setRenderData(KEY, null);
                return;
            }
            if (Wrist.forcedSkin != null && s instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState av) av.skin = Wrist.forcedSkin;
            SELF.entity = player.getId();
            SELF.progress = Math.min(1f, p.timeline.arm());
            r = SELF;
        } else {
            r = find(player.getId());
        }
        s.setRenderData(KEY, r);
        if (r == null || r.progress <= 0) return;
        if (s.mainArm == HumanoidArm.RIGHT) s.leftArmPose = HoloArmPose.pose();
        else s.rightArmPose = HoloArmPose.pose();
    }

    // ---- the glow on the wrist ------------------------------------------------------------------------------------

    /** A layer on the player renderers: the band and, for other players, the glow card. */
    static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final boolean slim;

        Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, PlayerModelType type) {
            super(parent);
            this.slim = type == PlayerModelType.SLIM;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
            float p = progress(state);
            if (p <= 0.01f || state.isInvisible) return;
            Raise r = state.getRenderData(KEY);
            HumanoidArm arm = state.mainArm.getOpposite();
            int side = arm == HumanoidArm.LEFT ? 1 : -1;
            poseStack.pushPose();
            getParentModel().translateToHand(state, arm, poseStack);
            float x0 = side > 0 ? -1f : (slim ? -2f : -3f), x1 = side > 0 ? (slim ? 2f : 3f) : 1f;
            band = p;
            bandX0 = x0 / 16f;
            bandX1 = x1 / 16f;
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), BAND);
            if (r != SELF) {
                // the card floats beyond the back of the wrist; billboarded so anyone around sees a soft panel of light
                poseStack.translate((side > 0 ? x1 + 3.5f : x0 - 3.5f) / 16f, 8f / 16f, 0);
                Matrix4f m = poseStack.last().pose();
                m.getTranslation(T);
                m.getScale(S);
                m.translation(T).rotate(Minecraft.getInstance().gameRenderer.mainCamera().rotation()).scale(S.x);
                card = p;
                collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), CARD);
            }
            poseStack.popPose();
        }
    }

    private static final Vector3f T = new Vector3f(), S = new Vector3f();
    private static final float[] XS = new float[4], YS = new float[4];
    private static float band, bandX0, bandX1, card;
    private static final SubmitNodeCollector.CustomGeometryRenderer BAND = HoloOthers::band;
    private static final SubmitNodeCollector.CustomGeometryRenderer CARD = HoloOthers::card;

    /** A soft ring of light around the wrist (lightning blend: additive, depth-tested, fogged). */
    private static void band(PoseStack.Pose pose, VertexConsumer vc) {
        float a = band;
        float pad = 0.45f / 16f;
        float x0 = bandX0 - pad, x1 = bandX1 + pad, z0 = -2.45f / 16f, z1 = 2.45f / 16f;
        float y0 = 7.2f / 16f, y1 = 8.4f / 16f, ym = (y0 + y1) / 2;
        int core = Glass.a(Glass.EDGE, 0.55f * a), clear = Glass.a(Glass.EDGE, 0);
        Matrix4f m = pose.pose();
        // each face: two strips fading from a bright centre line to nothing at the band's edges
        ring(vc, m, x1, z0, x1, z1, y0, ym, y1, core, clear);
        ring(vc, m, x0, z1, x0, z0, y0, ym, y1, core, clear);
        ring(vc, m, x0, z0, x1, z0, y0, ym, y1, core, clear);
        ring(vc, m, x1, z1, x0, z1, y0, ym, y1, core, clear);
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float ax, float az, float bx, float bz, float y0, float ym, float y1, int core, int clear) {
        vc.addVertex(m, ax, y0, az).setColor(clear);
        vc.addVertex(m, bx, y0, bz).setColor(clear);
        vc.addVertex(m, bx, ym, bz).setColor(core);
        vc.addVertex(m, ax, ym, az).setColor(core);
        vc.addVertex(m, ax, ym, az).setColor(core);
        vc.addVertex(m, bx, ym, bz).setColor(core);
        vc.addVertex(m, bx, y1, bz).setColor(clear);
        vc.addVertex(m, ax, y1, az).setColor(clear);
    }

    /** A content-free glow card: a 3×3 grid whose outer ring fades out, with a brighter rim line on top. */
    private static void card(PoseStack.Pose pose, VertexConsumer vc) {
        float a = card;
        float hw = 0.24f * a, hh = 0.15f * (0.3f + 0.7f * a), soft = 0.07f;
        int inner = Glass.a(Glass.EDGE, 0.20f * a), rim = Glass.a(0xCFEAFF, 0.45f * a), clear = Glass.a(Glass.EDGE, 0);
        Matrix4f m = pose.pose();
        float[] xs = XS, ys = YS;
        xs[0] = -hw - soft;
        xs[1] = -hw;
        xs[2] = hw;
        xs[3] = hw + soft;
        ys[0] = -hh - soft;
        ys[1] = -hh;
        ys[2] = hh;
        ys[3] = hh + soft;
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                vc.addVertex(m, xs[i], ys[j], 0).setColor(gridColour(i, j, inner, clear));
                vc.addVertex(m, xs[i + 1], ys[j], 0).setColor(gridColour(i + 1, j, inner, clear));
                vc.addVertex(m, xs[i + 1], ys[j + 1], 0).setColor(gridColour(i + 1, j + 1, inner, clear));
                vc.addVertex(m, xs[i], ys[j + 1], 0).setColor(gridColour(i, j + 1, inner, clear));
            }
        }
        float t = 0.006f;
        rimLine(vc, m, -hw, hh - t, hw, hh + t, rim);
        rimLine(vc, m, -hw, -hh - t, hw, -hh + t, rim);
    }

    /** Grid vertex (0..3 each way): the inner four corners are lit, the outer ring is clear. */
    private static int gridColour(int i, int j, int inner, int clear) {
        return (i == 1 || i == 2) && (j == 1 || j == 2) ? inner : clear;
    }

    private static void rimLine(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int colour) {
        vc.addVertex(m, x0, y0, 0).setColor(colour);
        vc.addVertex(m, x1, y0, 0).setColor(colour);
        vc.addVertex(m, x1, y1, 0).setColor(colour);
        vc.addVertex(m, x0, y1, 0).setColor(colour);
    }
}
