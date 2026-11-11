package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.WingCoreBlockEntity;
import dev.awaken.body.ascent.WingForm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Wings' case on a sky platform's core (SPEC §17.9, 11 Nov 2026): the real wings (WingFeathers, the model Euan
 * approved) held open and raised in a tall glass case over the core, breathing slowly and turning once every 36 s, lit
 * from its lid so they read through the cabin's glass from the pier. Three blocks square and 3.4 high on the deck, its
 * posts and rails dark Precursor metal with a line of light up each inner edge.
 * <p>
 * The gift is the case opening: when the core gives the wings, the glass sinks into the base over 0.7 s, the wings wake
 * with one beat, fold, and fly in an arc to the receiver's back, growing to their own size, arriving as the scene's
 * wings unfold there (AscentScene's WINGS_AT). The server decides the gift; this only plays it. For each viewer the
 * case holds the wings until they have their own; after that it stands open and empty. Drawn within 96 blocks; the
 * wings' detail by distance (WingForm.lod). The case has no collision of its own: the rig stands barriers in its
 * footprint (SkyRig.caseCells).
 */
public final class WingCaseRenderer implements BlockEntityRenderer<WingCoreBlockEntity, WingCaseRenderer.State> {
    /** The case: half its width round the core's middle, its height over the deck, the base's and the rails' height. */
    static final float HALF = 1.5F, H = 3.4F, BASE = 0.25F, RAIL = 0.18F, POST = 0.16F;
    /** The wings in the case: their size, their root over the deck, their raise (degrees; negative lifts them). */
    static final float SIZE = 0.42F, ROOT_Y = 2.15F, RAISE = -34F;
    /** A turn of the case's wings, and their slow breath (ticks). */
    static final int TURN_TICKS = 720, BREATH_TICKS = 64;
    /** The gift: the glass sinks over this long; the wings wake, then fly from here to the scene's unfolding. */
    static final int SINK = 14, WAKE = 10;

    private static final int METAL = 0x2B3038, METAL_DARK = 0x1C2027, EDGE = 0x8FE6FF, GLASS = 0x2E5F78;
    /** The square's corners in turn round it. */
    private static final float[] SX = {-1, 1, 1, -1}, SZ = {-1, -1, 1, 1};

    public static final class State extends BlockEntityRenderState {
        final float[] pose = new float[WingForm.N];
        final float[] pair = {1, RAISE, 0};
        /** How far the glass has sunk (0 shut, 1 gone into the base). */
        float open;
        /** The wings: drawn at all, their root (blocks from the core's corner), their facing (degrees) and size. */
        boolean wings;
        double wx, wy, wz;
        float yaw, size, glow, alpha;
        int lod;
        /** The camera's turn (the halos face it). */
        final org.joml.Quaternionf eye = new org.joml.Quaternionf();
        final SubmitNodeCollector.CustomGeometryRenderer frame = this::frame, glass = this::glass, feathers = this::feathers, halos = this::halos,
                mount = this::mount;

        private void frame(PoseStack.Pose p, VertexConsumer vc) {
            int light = lightCoords;
            float lo = 0.5F - HALF, hi = 0.5F + HALF;
            // four posts at the corners
            for (int i = 0; i < 4; i++) {
                float x = (i & 1) == 0 ? lo : hi - POST, z = (i & 2) == 0 ? lo : hi - POST;
                cube(p, vc, x, 0, z, x + POST, H, z + POST, METAL, light);
            }
            // the base and the lid: a rail along each side, the lid's plate over all
            for (int s = 0; s < 4; s++) {
                boolean alongX = s < 2;
                float c = (s & 1) == 0 ? lo : hi - POST;
                if (alongX) {
                    cube(p, vc, lo + POST, 0, c, hi - POST, BASE, c + POST, METAL_DARK, light);
                    cube(p, vc, lo + POST, H - RAIL, c, hi - POST, H, c + POST, METAL, light);
                } else {
                    cube(p, vc, c, 0, lo + POST, c + POST, BASE, hi - POST, METAL_DARK, light);
                    cube(p, vc, c, H - RAIL, lo + POST, c + POST, H, hi - POST, METAL, light);
                }
            }
            cube(p, vc, lo - 0.06F, H, lo - 0.06F, hi + 0.06F, H + 0.1F, hi + 0.06F, METAL_DARK, light);
            // the lid's lamp: a square of light under its middle
            cube(p, vc, 0.5F - 0.45F, H - RAIL - 0.04F, 0.5F - 0.45F, 0.5F + 0.45F, H - RAIL, 0.5F + 0.45F, 0xD8F6FF, 0xF000F0);
        }

        private void glass(PoseStack.Pose p, VertexConsumer vc) {
            float lo = 0.5F - HALF + POST * 0.5F, hi = 0.5F + HALF - POST * 0.5F;
            // the panes sink into the base: their top comes down to it
            float top = H - RAIL - (H - RAIL - BASE) * open;
            if (top > BASE + 0.02F) {
                for (int s = 0; s < 4; s++) {
                    float x0, z0, x1, z1;
                    switch (s) {
                        case 0 -> { x0 = lo; z0 = lo; x1 = hi; z1 = lo; }
                        case 1 -> { x0 = hi; z0 = lo; x1 = hi; z1 = hi; }
                        case 2 -> { x0 = hi; z0 = hi; x1 = lo; z1 = hi; }
                        default -> { x0 = lo; z0 = hi; x1 = lo; z1 = lo; }
                    }
                    pane(p, vc, x0, z0, x1, z1, BASE, top, GLASS, 0.22F);
                    // a bright edge along the glass's top (it shows the glass going down) and two faint streaks
                    pane(p, vc, x0, z0, x1, z1, top - 0.035F, top, EDGE, 0.55F);
                    streak(p, vc, x0, z0, x1, z1, top, 0.18F, 0.30F);
                    streak(p, vc, x0, z0, x1, z1, top, 0.36F, 0.41F);
                }
            }
            // the lid's light falling on the wings: a soft square shaft, brighter at the top
            float a = 0.10F * (0.6F + 0.4F * glow);
            float t0 = H - RAIL - 0.05F, t1 = BASE + 0.4F, in0 = 0.45F, in1 = 0.95F;
            int hiCol = argb(0xCFF3FF, a), loCol = argb(0xCFF3FF, 0);
            for (int s = 0; s < 4; s++) {
                // the shaft's four sides, corner a to corner b, each widening as it falls
                float ax = SX[s], az = SZ[s], bx = SX[(s + 1) & 3], bz = SZ[(s + 1) & 3];
                vc.addVertex(p, 0.5F + ax * in0, t0, 0.5F + az * in0).setColor(hiCol);
                vc.addVertex(p, 0.5F + bx * in0, t0, 0.5F + bz * in0).setColor(hiCol);
                vc.addVertex(p, 0.5F + bx * in1, t1, 0.5F + bz * in1).setColor(loCol);
                vc.addVertex(p, 0.5F + ax * in1, t1, 0.5F + az * in1).setColor(loCol);
            }
        }

        private void feathers(PoseStack.Pose p, VertexConsumer vc) {
            WingFeathers.pair(p, vc, pose, 0, pair, 0, WingFeathers.LIGHT, alpha, lod, false);
        }

        private void mount(PoseStack.Pose p, VertexConsumer vc) {
            WingFeathers.mount(p, vc, 0xDFF8FF, alpha, 1F + 0.12F * glow);
        }

        private void halos(PoseStack.Pose p, VertexConsumer vc) {
            WingFeathers.faceEye(p, eye);
            WingFeathers.halos(p, vc, pose, 0, pair, 0, WingFeathers.LIGHT[WingFeathers.HALO], glow, alpha);
        }
    }

    public WingCaseRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WingCoreBlockEntity be, State state, float pt, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, pt, camera, crumbling);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        int t = AscentMoves.ticks;
        state.lod = WingForm.lod(camera.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())));
        // the case's own wings: raised, breathing, turning; the clock is whole ticks, eased by the partial one
        float turn = ((t % TURN_TICKS) + pt) / TURN_TICKS * 360F;
        float breath = ((t % BREATH_TICKS) + pt) / 20F;
        WingForm.beat(WingForm.HOVER, breath, BREATH_TICKS / 20F, 0.16F, 0.22F, state.pose);
        state.yaw = turn;
        state.size = 1F;
        state.wx = 0.5;
        state.wy = ROOT_Y;
        state.wz = 0.5;
        state.glow = 0.55F + 0.45F * (0.5F + 0.5F * Mth.sin(breath / (BREATH_TICKS / 20F) * Mth.TWO_PI));
        state.open = 0;
        state.wings = true;
        state.alpha = 1F;
        boolean mine = AscentMoves.has(AscentPayloads.Tier.WINGS);
        boolean waiting = AscentScene.pending != 0 && AscentScene.mode == AscentPayloads.GIFT_WINGS;
        boolean gift = AscentScene.age >= 0 && AscentScene.playingMode == AscentPayloads.GIFT_WINGS && me != null
                && me.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 10 * 10;
        if (gift) {
            float a = AscentScene.age + pt;
            state.open = WingForm.ease(a / SINK);
            if (a >= AscentScene.WINGS_AT) {
                state.wings = false;
            } else if (a > WAKE) {
                // fold and fly: an arc from the case to the back, growing to the wings' own size, turning to the body
                float u = WingForm.ease((a - WAKE) / (AscentScene.WINGS_AT - WAKE));
                float[] folded = FOLDED;
                WingForm.mix(state.pose, WingForm.FOLD, WingForm.ease(Math.min(1, u * 1.8F)), folded);
                System.arraycopy(folded, 0, state.pose, 0, WingForm.N);
                Vec3 at = me.getPosition(pt);
                float body = Mth.rotLerp(pt, me.yBodyRotO, me.yBodyRot);
                double bx = at.x - be.getBlockPos().getX() + Mth.sin(body * Mth.DEG_TO_RAD) * 0.25;
                double by = at.y - be.getBlockPos().getY() + 1.4;
                double bz = at.z - be.getBlockPos().getZ() - Mth.cos(body * Mth.DEG_TO_RAD) * 0.25;
                double mx = (0.5 + bx) / 2, my = Math.max(ROOT_Y, by) + 1.2, mz = (0.5 + bz) / 2;
                double v = 1 - u;
                state.wx = v * v * 0.5 + 2 * v * u * mx + u * u * bx;
                state.wy = v * v * ROOT_Y + 2 * v * u * my + u * u * by;
                state.wz = v * v * 0.5 + 2 * v * u * mz + u * u * bz;
                state.yaw = turn + Mth.wrapDegrees(body - turn) * u;
                state.size = Mth.lerp(u, 1F, 1F / SIZE);
                state.glow = 1F;
                state.pair[1] = Mth.lerp(u, RAISE, 0);
                // through your own eyes they fade as they reach you (they arrive at the back, where the camera is)
                double ex = state.wx + be.getBlockPos().getX() - camera.x, ey = state.wy + be.getBlockPos().getY() - camera.y,
                        ez = state.wz + be.getBlockPos().getZ() - camera.z;
                state.alpha = (float) Mth.clamp((Math.sqrt(ex * ex + ey * ey + ez * ez) - 0.9) / 1.2, 0, 1);
                if (state.alpha < 0.02F) state.wings = false;
            } else {
                // waking: one fuller beat, the light up
                WingForm.beat(WingForm.HOVER, a / 20F, WAKE / 20F, 0.6F * WingForm.ease(a / WAKE), 0.3F, state.pose);
                state.glow = 1F;
            }
        } else if (mine && !waiting) {
            // theirs already: the case stands open and empty for them
            state.open = 1;
            state.wings = false;
        }
    }

    /** The fold's pose, worked into while the wings fly (never allocated per frame). */
    private static final float[] FOLDED = new float[WingForm.N];

    @Override
    public void submit(State state, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
        state.eye.set(camera.orientation);
        out.submitCustomGeometry(ps, frameType(), state.frame);
        if (state.wings) {
            ps.pushPose();
            ps.translate(state.wx, state.wy, state.wz);
            ps.rotateDegrees(Axis.YP, 180F - state.yaw);
            float k = SIZE * state.size;
            ps.scale(-k, -k, k);
            // the wings' root, in their model space, to the middle
            ps.translate(0F, -WingFeathers.ROOT_Y / 16F, -WingFeathers.ROOT_Z / 16F);
            out.submitCustomGeometry(ps, WingPipelines.feather(0), state.feathers);
            out.submitCustomGeometry(ps, WingPipelines.feather(0), state.mount);
            if (state.lod < 2) out.order(1).submitCustomGeometry(ps, WingPipelines.halo(), state.halos);
            ps.popPose();
        }
        out.order(1).submitCustomGeometry(ps, WingPipelines.halo(), state.glass);
    }

    private static RenderType frame;

    private static RenderType frameType() {
        if (frame == null) frame = RenderTypes.entitySolid(WingPipelines.WHITE);
        return frame;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(WingCoreBlockEntity be) {
        // the case, and the gift's flight out to a receiver up to ten blocks off
        return new AABB(be.getBlockPos()).inflate(10, 0, 10).expandTowards(0, 6, 0);
    }

    // ---- geometry: blocks from the core's corner ----------------------------------------------------------------------

    /** A box lit by the world (its faces shaded as a block's are). */
    private static void cube(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        quad(p, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, shade(rgb, 1F), light, 0, 1, 0);
        quad(p, vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, shade(rgb, 0.5F), light, 0, -1, 0);
        quad(p, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, shade(rgb, 0.8F), light, 0, 0, -1);
        quad(p, vc, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, shade(rgb, 0.8F), light, 0, 0, 1);
        quad(p, vc, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, shade(rgb, 0.6F), light, -1, 0, 0);
        quad(p, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, shade(rgb, 0.6F), light, 1, 0, 0);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, int argb, int light, float nx, float ny, float nz) {
        vert(p, vc, ax, ay, az, 0, 0, argb, light, nx, ny, nz);
        vert(p, vc, bx, by, bz, 0, 1, argb, light, nx, ny, nz);
        vert(p, vc, cx, cy, cz, 1, 1, argb, light, nx, ny, nz);
        vert(p, vc, dx, dy, dz, 1, 0, argb, light, nx, ny, nz);
    }

    private static void vert(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    /** A pane of glass (additive: it brightens what is behind it), from (x0, z0) to (x1, z1), seen from both sides. */
    private static void pane(PoseStack.Pose p, VertexConsumer vc, float x0, float z0, float x1, float z1, float y0, float y1, int rgb, float a) {
        int c = argb(rgb, a);
        vc.addVertex(p, x0, y0, z0).setColor(c);
        vc.addVertex(p, x0, y1, z0).setColor(c);
        vc.addVertex(p, x1, y1, z1).setColor(c);
        vc.addVertex(p, x1, y0, z1).setColor(c);
    }

    /** A faint slanting streak across a pane, from {@code f0} to {@code f1} of its width at the top, under {@code top}. */
    private static void streak(PoseStack.Pose p, VertexConsumer vc, float x0, float z0, float x1, float z1, float top, float f0, float f1) {
        float h = top - BASE;
        if (h < 0.3F) return;
        int c = argb(0xBFEFFF, 0.12F);
        float lean = 0.35F;
        float ax = Mth.lerp(f0, x0, x1), az = Mth.lerp(f0, z0, z1), bx = Mth.lerp(f1, x0, x1), bz = Mth.lerp(f1, z0, z1);
        float cx = Mth.lerp(Math.min(1, f1 + lean), x0, x1), cz = Mth.lerp(Math.min(1, f1 + lean), z0, z1);
        float dx = Mth.lerp(Math.min(1, f0 + lean), x0, x1), dz = Mth.lerp(Math.min(1, f0 + lean), z0, z1);
        vc.addVertex(p, ax, top, az).setColor(c);
        vc.addVertex(p, bx, top, bz).setColor(c);
        vc.addVertex(p, cx, BASE + h * 0.15F, cz).setColor(argb(0xBFEFFF, 0));
        vc.addVertex(p, dx, BASE + h * 0.15F, dz).setColor(argb(0xBFEFFF, 0));
    }

    private static int shade(int rgb, float f) {
        return 0xFF000000 | (int) (((rgb >> 16) & 255) * f) << 16 | (int) (((rgb >> 8) & 255) * f) << 8 | (int) ((rgb & 255) * f);
    }

    private static int argb(int rgb, float a) {
        return (int) (255 * Mth.clamp(a, 0, 1)) << 24 | (rgb & 0xFFFFFF);
    }
}
