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
import org.joml.Quaternionf;
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
        /** The last kind it showed, so a closing map fades as a map. */
        HoloKind shown = HoloKind.NONE;
        float progress;
        /** Its map disc (SPEC §30.5), drawn after the frame's submits, so it keeps its own copy of what to draw. */
        final Disc disc = new Disc();
    }

    private static final Raise[] SLOTS = new Raise[64];
    private static final Raise SELF = new Raise();
    static final ContextKey<Raise> KEY = new ContextKey<>(Awaken.id("holo_raise"));
    /** Autopilot ({@code holo peek on}): show your own player in third person as other players see it. */
    static boolean peekSelf;
    /** Your own wrist this frame (camera-relative world), found by the layer; for the beams in third person. */
    private static final Vector3f SELF_WRIST = new Vector3f();
    private static long selfWristFrame = -1;

    static boolean selfWrist(Vector3f out) {
        if (selfWristFrame < HoloClient.frame() - 1) return false;
        out.set(SELF_WRIST);
        return true;
    }

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
                if (kind != HoloKind.NONE) r.shown = kind;
                return;
            }
            if (free == null && r.entity < 0) free = r;
        }
        if (free != null && kind != HoloKind.NONE) {
            free.entity = msg.entityId();
            free.kind = kind;
            free.shown = kind;
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
        if (player == mc.player && HoloScene.raise() > 0f) {
            // the map scene holds the camera (first person on its own camera entity): the wrist stays up for it
            SELF.entity = player.getId();
            SELF.kind = SELF.shown = HoloKind.MAP;
            SELF.progress = HoloScene.raise();
            r = SELF;
        } else if (player == mc.player) {
            SELF.kind = SELF.shown = HoloKind.NONE;
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
            // a glow at the wrist and, for anyone else looking, a small content-free hologram above it, billboarded
            poseStack.translate((x0 + x1) / 32f, 8f / 16f, 0);
            Matrix4f m = poseStack.last().pose();
            m.getTranslation(T);
            m.getScale(S);
            if (r == SELF) {
                SELF_WRIST.set(T);
                selfWristFrame = HoloClient.frame();
            }
            // the hologram floats beside the body on the wrist's side, a little ahead and above it: it never covers
            // the face from the front and still shows past the shoulder from behind
            float yaw = (float) Math.toRadians(state.bodyRot);
            float fx = -(float) Math.sin(yaw), fz = (float) Math.cos(yaw);   // forward
            float lx = (float) Math.cos(yaw), lz = (float) Math.sin(yaw);    // the body's left
            float sideSign = arm == HumanoidArm.LEFT ? 1f : -1f;
            CARD_OFFSET.set(fx * 0.16f + lx * sideSign * 0.40f, 0, fz * 0.16f + lz * sideSign * 0.40f);
            var cam = Minecraft.getInstance().gameRenderer.mainCamera().rotation();
            cam.transformInverse(CARD_OFFSET); // into the billboard's own axes
            m.translation(T).rotate(cam).scale(S.x);
            card = p;
            boolean map = r != null && r.shown == HoloKind.MAP;
            showCard = !map && (r != SELF || peekSelf);
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), CARD);
            poseStack.popPose();
            if (map && r == SELF && HoloScene.hasBeams() && !peekSelf) {
                BEAMS.set(T, p);
                collector.submitCustomGeometry(ROOT, RenderTypes.lightning(), BEAMS);
            } else if (map && (r != SELF || peekSelf)) {
                // a disc of light before the body at the waist, the cone from the wrist: never the map itself
                float dx = fx * 0.95f, dz = fz * 0.95f;
                r.disc.set(T, dx, -0.32f, dz, cam, p, HoloClient.frame());
                collector.submitCustomGeometry(ROOT, RenderTypes.lightning(), r.disc);
            }
        }
    }

    private static final Vector3f T = new Vector3f(), S = new Vector3f(), CARD_OFFSET = new Vector3f();
    /** Camera-relative world space: the beams and discs are placed in it directly. */
    private static final PoseStack ROOT = new PoseStack();
    private static final Beams BEAMS = new Beams();
    private static final Matrix4f M2 = new Matrix4f(), M3 = new Matrix4f();
    private static final float[] XS = new float[4], YS = new float[4];
    private static float band, bandX0, bandX1, card;
    private static boolean showCard;
    private static final SubmitNodeCollector.CustomGeometryRenderer BAND = HoloOthers::band;
    private static final SubmitNodeCollector.CustomGeometryRenderer CARD = HoloOthers::card;

    /** A soft ring of light around the wrist (lightning blend: additive, depth-tested, fogged). */
    private static void band(PoseStack.Pose pose, VertexConsumer vc) {
        float a = band;
        float pad = 0.45f / 16f;
        float x0 = bandX0 - pad, x1 = bandX1 + pad, z0 = -2.45f / 16f, z1 = 2.45f / 16f;
        float y0 = 7.2f / 16f, y1 = 8.4f / 16f, ym = (y0 + y1) / 2;
        int core = Glass.a(Glass.EDGE, 0.95f * a), clear = Glass.a(Glass.EDGE, 0);
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

    /**
     * At the wrist, in camera-facing space: a soft glow, and (for other players) a small hologram above it with a faint
     * cone down to the wrist: a framed sheet of light with three bars where text would be, never the panel's content.
     */
    private static void card(PoseStack.Pose pose, VertexConsumer vc) {
        float a = card;
        Matrix4f m = pose.pose();
        int clear = Glass.a(Glass.EDGE, 0);
        // the wrist glow sits just off the arm toward the viewer, so the arm itself never hides it
        Matrix4f g = M3.set(m).translate(0, 0, 0.16f);
        softRect(vc, g, 0, 0, 0.05f * a, 0.05f * a, 0.10f, Glass.a(Glass.EDGE, 0.95f * a), clear);
        if (!showCard) return;
        float hw = 0.30f * a, hh = 0.17f * (0.3f + 0.7f * a), cy = 0.14f * a + CARD_OFFSET.y, cx = CARD_OFFSET.x * a, cz = CARD_OFFSET.z * a;
        // the cone from the wrist to the sheet's bottom edge
        int coneNear = Glass.a(Glass.EDGE, 0.40f * a);
        vc.addVertex(m, 0, 0.03f, 0).setColor(coneNear);
        vc.addVertex(m, cx - hw, cy - hh, cz).setColor(clear);
        vc.addVertex(m, cx + hw, cy - hh, cz).setColor(clear);
        vc.addVertex(m, 0, 0.03f, 0).setColor(coneNear);
        // the sheet sits in its own plane, offset ahead of the body
        m = M2.set(m).translate(cx, 0, cz);
        // the sheet, its frame and three bars
        softRect(vc, m, 0, cy, hw, hh, 0.06f, Glass.a(Glass.EDGE, 0.42f * a), clear);
        int rim = Glass.a(0xE2F2FF, Math.min(1f, 1.0f * a)), bar = Glass.a(0xCFEAFF, 0.6f * a);
        float t = 0.009f;
        rimLine(vc, m, -hw, cy + hh - t, hw, cy + hh + t, rim);
        rimLine(vc, m, -hw, cy - hh - t, hw, cy - hh + t, rim);
        rimLine(vc, m, -hw - t, cy - hh, -hw + t, cy + hh, rim);
        rimLine(vc, m, hw - t, cy - hh, hw + t, cy + hh, rim);
        for (int i = 0; i < 3; i++) {
            float y = cy + hh * 0.45f - i * hh * 0.45f, len = hw * (i == 1 ? 1.2f : i == 2 ? 0.8f : 1.5f);
            rimLine(vc, m, -hw * 0.7f, y - t * 0.8f, -hw * 0.7f + len, y + t * 0.8f, bar);
        }
    }

    /** A rectangle of light whose outer ring of width {@code soft} fades to nothing (a 3×3 grid). */
    private static void softRect(VertexConsumer vc, Matrix4f m, float cx, float cy, float hw, float hh, float soft, int inner, int clear) {
        float[] xs = XS, ys = YS;
        xs[0] = cx - hw - soft;
        xs[1] = cx - hw;
        xs[2] = cx + hw;
        xs[3] = cx + hw + soft;
        ys[0] = cy - hh - soft;
        ys[1] = cy - hh;
        ys[2] = cy + hh;
        ys[3] = cy + hh + soft;
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                vc.addVertex(m, xs[i], ys[j], 0).setColor(gridColour(i, j, inner, clear));
                vc.addVertex(m, xs[i + 1], ys[j], 0).setColor(gridColour(i + 1, j, inner, clear));
                vc.addVertex(m, xs[i + 1], ys[j + 1], 0).setColor(gridColour(i + 1, j + 1, inner, clear));
                vc.addVertex(m, xs[i], ys[j + 1], 0).setColor(gridColour(i, j + 1, inner, clear));
            }
        }
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

    // ---- the map scene (SPEC §30.5) -------------------------------------------------------------------------------

    /** Your own beams in the map scene: two fans of light from the band to the map's near edge. */
    static final class Beams implements SubmitNodeCollector.CustomGeometryRenderer {
        private final Vector3f wrist = new Vector3f(), a = new Vector3f(), b = new Vector3f(), w = new Vector3f();
        private float alpha;

        void set(Vector3f wrist, float alpha) {
            this.wrist.set(wrist);
            this.alpha = alpha;
            HoloScene.beamEnds(a, b);
        }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            Matrix4f m = pose.pose();
            int near = Glass.a(Glass.EDGE, 0.55f * alpha), far = Glass.a(Glass.EDGE, 0.10f * alpha), clear = Glass.a(Glass.EDGE, 0);
            // the sheet of light between the two beams, then each beam as a bright thread
            vc.addVertex(m, wrist.x, wrist.y, wrist.z).setColor(Glass.a(Glass.EDGE, 0.22f * alpha));
            vc.addVertex(m, a.x, a.y, a.z).setColor(clear);
            vc.addVertex(m, b.x, b.y, b.z).setColor(clear);
            vc.addVertex(m, wrist.x, wrist.y, wrist.z).setColor(Glass.a(Glass.EDGE, 0.22f * alpha));
            thread(vc, m, wrist, a, near, far);
            thread(vc, m, wrist, b, near, far);
        }

        /** A thin quad from p to q, turned to face the eye (the camera sits at the origin of this space). */
        private void thread(VertexConsumer vc, Matrix4f m, Vector3f p, Vector3f q, int c0, int c1) {
            q.sub(p, w).cross(p.x + q.x, p.y + q.y, p.z + q.z);
            float len = w.length();
            if (len < 1e-5f) return;
            w.mul(0.012f / len);
            vc.addVertex(m, p.x - w.x, p.y - w.y, p.z - w.z).setColor(c0);
            vc.addVertex(m, q.x - w.x * 3, q.y - w.y * 3, q.z - w.z * 3).setColor(c1);
            vc.addVertex(m, q.x + w.x * 3, q.y + w.y * 3, q.z + w.z * 3).setColor(c1);
            vc.addVertex(m, p.x + w.x, p.y + w.y, p.z + w.z).setColor(c0);
        }
    }

    /**
     * What anyone else sees of a player's map: a round sheet of light before them at the waist, a ring with compass
     * ticks, a few points of light drifting over it, and a cone down from the wrist. Content-free (SPEC §30.5).
     */
    static final class Disc implements SubmitNodeCollector.CustomGeometryRenderer {
        private final Vector3f wrist = new Vector3f(), centre = new Vector3f(), lo = new Vector3f(), hi = new Vector3f();
        private final Quaternionf rot = new Quaternionf();
        private final Matrix4f local = new Matrix4f();
        private float alpha;
        private long frame;

        void set(Vector3f wrist, float dx, float dy, float dz, Quaternionf cam, float alpha, long frame) {
            this.wrist.set(wrist);
            centre.set(wrist.x + dx, wrist.y + dy, wrist.z + dz);
            rot.set(cam);
            this.alpha = alpha;
            this.frame = frame;
        }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            float a = alpha, r = 0.48f * (0.4f + 0.6f * a);
            int clear = Glass.a(Glass.EDGE, 0);
            Matrix4f m = pose.pose();
            // the cone from the wrist to the disc's lower rim (world space)
            local.set(m).translate(centre.x, centre.y, centre.z).rotate(rot);
            local.transformPosition(lo.set(-r * 0.8f, -r * 0.35f, 0));
            local.transformPosition(hi.set(r * 0.8f, -r * 0.35f, 0));
            int coneNear = Glass.a(Glass.EDGE, 0.40f * a);
            vc.addVertex(m, wrist.x, wrist.y, wrist.z).setColor(coneNear);
            vc.addVertex(m, lo.x, lo.y, lo.z).setColor(clear);
            vc.addVertex(m, hi.x, hi.y, hi.z).setColor(clear);
            vc.addVertex(m, wrist.x, wrist.y, wrist.z).setColor(coneNear);
            // the disc, flattened as a table seen from a little above, in the billboard's plane
            Matrix4f d = local;
            float squash = 0.42f;
            int fill = Glass.a(Glass.EDGE, 0.20f * a), rim = Glass.a(0xE2F2FF, 0.95f * a);
            int seg = 40;
            for (int i = 0; i < seg; i++) {
                double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
                float x0 = (float) Math.cos(t0) * r, y0 = (float) Math.sin(t0) * r * squash;
                float x1 = (float) Math.cos(t1) * r, y1 = (float) Math.sin(t1) * r * squash;
                vc.addVertex(d, 0, 0, 0).setColor(fill);
                vc.addVertex(d, x0, y0, 0).setColor(fill);
                vc.addVertex(d, x1, y1, 0).setColor(fill);
                vc.addVertex(d, 0, 0, 0).setColor(fill);
                // the rim: a band of light round the edge, soft outside
                float o = 1.07f;
                vc.addVertex(d, x0, y0, 0).setColor(rim);
                vc.addVertex(d, x1, y1, 0).setColor(rim);
                vc.addVertex(d, x1 * o, y1 * o, 0).setColor(clear);
                vc.addVertex(d, x0 * o, y0 * o, 0).setColor(clear);
                if (i % 5 == 0) { // compass ticks
                    vc.addVertex(d, x0 * 0.90f, y0 * 0.90f, 0).setColor(rim);
                    vc.addVertex(d, x0 * 0.90f + 0.008f, y0 * 0.90f, 0).setColor(rim);
                    vc.addVertex(d, x0 * 0.99f + 0.008f, y0 * 0.99f, 0).setColor(rim);
                    vc.addVertex(d, x0 * 0.99f, y0 * 0.99f, 0).setColor(rim);
                }
            }
            // a few points of light drifting over it (cloud wisps and places, never real ones)
            float time = frame / 60f;
            for (int k = 0; k < 5; k++) {
                float ang = k * 1.7f + time * (0.15f + k * 0.03f), rr = r * (0.25f + 0.13f * k);
                float px = (float) Math.cos(ang) * rr, py = (float) Math.sin(ang) * rr * squash + 0.03f;
                softRect(vc, d, px, py, 0.012f, 0.012f, 0.03f, Glass.a(k == 2 ? Glass.AMBER : 0xE2F2FF, 0.85f * a), clear);
            }
        }
    }
}
