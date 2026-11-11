package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * The wings on show at a sky platform's core (SPEC §17.9, "The wings on show"): the real feathered pair (SPEC §62:
 * {@link WingForm} drawn by {@link WingFeathers} in {@link WingPipelines}' own types) hung over the core inside a case
 * of light glass that stands on the rail the rig lays round it. They breathe between nearly open and open and turn once
 * in 38 seconds, lit by themselves, in a column of light from the core to the roof, so they read through the cabin's
 * portals from the pier.
 * <p>
 * What a player sees is their own truth: without wings, the case shut and the pair inside; while the core gives them
 * (the gift's scene), the glass sinks into the rail and the pair comes across to their back, arriving as their own wings
 * open; with wings, the case open and empty over a low light.
 * <p>
 * One pair, drawn only within {@link #NEAR} blocks (the far forms past 48); nothing here allocates in a frame.
 */
public final class WingCaseRenderer implements BlockEntityRenderer<WingCoreBlockEntity, WingCaseRenderer.State> {
    /** The pair is drawn this near; the case's glass and light a little further. */
    static final double NEAR = 80;
    /**
     * The pair's size beside a player's (0.94): open, a wing reaches about 50 texels from the middle with its long
     * feathers, and the glass is two blocks out, so the pair turns inside it. Where it hangs over the core's floor, and
     * the glass's reach.
     */
    private static final float SIZE = 0.64F, HANG = 3.2F, HALF = 2F, RAIL = 1.5F, ROOF = 5F;
    /** The gift: the glass is down by this age of its scene; the pair leaves at FLY_FROM and arrives as the scene's own wings open (34). */
    private static final float OPEN_TICKS = 22, FLY_FROM = 6, FLY_TICKS = 28;

    public static final class State extends BlockEntityRenderState {
        float time, open, fly, tx, ty, tz, camX, camZ;
        boolean pair, near;
        int lod;
    }

    // this frame's case (the geometry runs after every submit; platforms stand hundreds of blocks apart, so one is in view)
    private static final float[] POSE = new float[WingForm.N], PAIR = {1F, 0F, 0F};
    private static final Quaternionf TURN = new Quaternionf();
    private static float alpha, glow, open, time, camX, camZ, column;
    private static int lod;
    private static final SubmitNodeCollector.CustomGeometryRenderer FEATHERS =
            (pose, vc) -> WingFeathers.pair(pose, vc, POSE, 0, PAIR, 0, WingFeathers.LIGHT, alpha, lod, false);
    private static final SubmitNodeCollector.CustomGeometryRenderer HALOS = (pose, vc) -> {
        WingFeathers.faceEye(pose, AscentRender.eye());
        WingFeathers.halos(pose, vc, POSE, 0, PAIR, 0, WingFeathers.LIGHT[WingFeathers.HALO], glow, alpha);
    };
    private static final SubmitNodeCollector.CustomGeometryRenderer GLASS = WingCaseRenderer::glass;

    public WingCaseRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WingCoreBlockEntity be, State s, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTicks, camera, crumbling);
        BlockPos p = be.getBlockPos();
        double dx = camera.x - (p.getX() + 0.5), dy = camera.y - (p.getY() + HANG), dz = camera.z - (p.getZ() + 0.5), d2 = dx * dx + dy * dy + dz * dz;
        s.time = AscentMoves.ticks + partialTicks;
        s.camX = (float) dx;
        s.camZ = (float) dz;
        s.near = d2 < NEAR * NEAR;
        s.lod = WingForm.lod(d2);
        LocalPlayer me = Minecraft.getInstance().player;
        int gift = me != null && me.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 16 * 16 ? AscentScene.wingsGift() : -2;
        if (gift >= -1) {
            float age = gift + partialTicks;
            s.open = gift < 0 ? 0 : WingForm.ease(age / OPEN_TICKS);
            s.fly = gift < 0 ? 0 : WingForm.ease((age - FLY_FROM) / FLY_TICKS);
            s.pair = s.fly < 1;
            // to between the shoulder blades of whoever it is given to
            float yaw = me.getYRot() * Mth.DEG_TO_RAD;
            s.tx = (float) (me.getX() - (p.getX() + 0.5)) + Mth.sin(yaw) * 0.3F;
            s.ty = (float) (me.getY() + 1.35 - (p.getY() + HANG));
            s.tz = (float) (me.getZ() - (p.getZ() + 0.5)) - Mth.cos(yaw) * 0.3F;
        } else {
            boolean has = AscentMoves.has(AscentPayloads.Tier.WINGS);
            s.open = has ? 1 : 0;
            s.fly = 0;
            s.pair = !has;
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (!s.near) return;
        time = s.time;
        open = s.open;
        lod = s.lod;
        camX = s.camX;
        camZ = s.camZ;
        // the column of light the pair hangs in: gone with the pair
        column = s.pair ? 1 - s.fly : 0;
        if (s.pair) {
            // breathing between nearly open and open; opened right out as it leaves the case
            float k = Mth.lerp(s.fly, 0.86F + 0.1F * Mth.sin(time / 46F), 1F);
            WingForm.mix(WingForm.FOLD, WingForm.OPEN, k, POSE);
            POSE[WingForm.CASC] = 1;
            POSE[WingForm.P0] += 3F * Mth.sin(time / 31F) * (1 - s.fly);
            // thinning out over the last of the way across: the scene's own wings open where it arrives
            alpha = 1 - WingForm.ease((s.fly - 0.8F) / 0.2F);
            glow = 0.75F + 0.25F * Mth.sin(time / 46F);
            float size = Mth.lerp(s.fly, SIZE, 0.94F), at = s.fly;
            pose.pushPose();
            pose.translate(0.5F + s.tx * at, HANG + s.ty * at + 0.06F * Mth.sin(time / 23F) * (1 - at), 0.5F + s.tz * at);
            pose.rotate(TURN.rotationY(time / 760F * Mth.TWO_PI));
            // the wings' own space is a body's back: x to its left, y down, z behind, from the neck (the roots 3 down, 4 behind)
            pose.scale(-size, -size, size);
            pose.translate(0, -WingFeathers.ROOT_Y / 16F, -WingFeathers.ROOT_Z / 16F);
            out.submitCustomGeometry(pose, WingPipelines.feather(0), FEATHERS);
            if (lod < 2) out.order(1).submitCustomGeometry(pose, WingPipelines.halo(), HALOS);
            pose.popPose();
        }
        out.order(1).submitCustomGeometry(pose, WingPipelines.halo(), GLASS);
    }

    // ---- the case: glass made of light ----------------------------------------------------------------------------------

    private static final int TINT = 0xA8DCFF, EDGE = 0xEAF7FF;

    private static int a(float alpha, int rgb) {
        return (int) (255 * Mth.clamp(alpha, 0, 1)) << 24 | rgb;
    }

    /** A quad through four corners, drawn for both sides, each corner with its own strength. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, int c0, int c1, int c2, int c3) {
        vc.addVertex(pose, x0, y0, z0).setColor(c0);
        vc.addVertex(pose, x1, y1, z1).setColor(c1);
        vc.addVertex(pose, x2, y2, z2).setColor(c2);
        vc.addVertex(pose, x3, y3, z3).setColor(c3);
        vc.addVertex(pose, x3, y3, z3).setColor(c3);
        vc.addVertex(pose, x2, y2, z2).setColor(c2);
        vc.addVertex(pose, x1, y1, z1).setColor(c1);
        vc.addVertex(pose, x0, y0, z0).setColor(c0);
    }

    /**
     * Four panes standing on the rail up to the roof (sunk into the rail by how open the case is), brighter at the
     * foot; a bright line up each corner and round the top; a band of sheen crossing each pane every ten seconds, one
     * pane after another; and the light the pair throws on the core's top (it stays, low, in an empty case).
     */
    private static void glass(PoseStack.Pose pose, VertexConsumer vc) {
        float top = RAIL + (ROOF - RAIL) * (1 - open), c = 0.5F, lo = c - HALF, hi = c + HALF;
        if (top > RAIL + 0.02F) {
            int foot = a(0.085F, TINT), head = a(0.03F, TINT), line = a(0.5F, EDGE), none = a(0, EDGE);
            for (int side = 0; side < 4; side++) {
                // a pane from (ax, az) to (bx, bz)
                float ax = side == 0 ? lo : side == 1 ? hi : side == 2 ? hi : lo, az = side == 0 ? lo : side == 1 ? lo : side == 2 ? hi : hi;
                float bx = side == 0 ? hi : side == 1 ? hi : side == 2 ? lo : lo, bz = side == 0 ? lo : side == 1 ? hi : side == 2 ? hi : lo;
                quad(pose, vc, ax, RAIL, az, bx, RAIL, bz, bx, top, bz, ax, top, az, foot, foot, head, head);
                // the corner's line (each pane draws the one it starts at) and the top's
                float ux = (bx - ax) / (2 * HALF), uz = (bz - az) / (2 * HALF), w = 0.05F;
                quad(pose, vc, ax, RAIL, az, ax + ux * w, RAIL, az + uz * w, ax + ux * w, top, az + uz * w, ax, top, az, line, none, none, line);
                quad(pose, vc, ax, top - w, az, bx, top - w, bz, bx, top, bz, ax, top, az, none, none, line, line);
                // the sheen: a soft band leaning across the pane, its turn a quarter of the cycle
                float cycle = (time / 200F + side * 0.25F) % 1F;
                if (cycle < 0.3F) {
                    float m = -0.2F + cycle / 0.3F * 1.4F, half = 0.22F, lean = 0.35F;
                    int mid = a(0.13F, EDGE);
                    for (int part = 0; part < 2; part++) {
                        float s0 = Mth.clamp(m + (part == 0 ? -half : 0), 0, 1), s1 = Mth.clamp(m + (part == 0 ? 0 : half), 0, 1);
                        float t0 = Mth.clamp(s0 + lean, 0, 1), t1 = Mth.clamp(s1 + lean, 0, 1);
                        quad(pose, vc, Mth.lerp(s0, ax, bx), RAIL, Mth.lerp(s0, az, bz), Mth.lerp(s1, ax, bx), RAIL, Mth.lerp(s1, az, bz),
                                Mth.lerp(t1, ax, bx), top, Mth.lerp(t1, az, bz), Mth.lerp(t0, ax, bx), top, Mth.lerp(t0, az, bz),
                                part == 0 ? none : mid, part == 0 ? mid : none, part == 0 ? mid : none, part == 0 ? none : mid);
                    }
                }
            }
        }
        // the column of light the pair hangs in, from the core's top to the roof: a sheet turned to face the eye, bright
        // down its middle and at its foot, breathing with the pair (it is what reads from the pier and the stair)
        float cl = (float) Math.sqrt(camX * camX + camZ * camZ);
        if (column > 0.01F && cl > 0.05F) {
            float wide = 1.25F, sx = -camZ / cl * wide, sz = camX / cl * wide, breath = 0.85F + 0.15F * Mth.sin(time / 46F);
            int low = a(0.2F * column * breath, TINT), high = a(0.07F * column * breath, TINT), off = a(0, TINT);
            quad(pose, vc, c - sx, 1.02F, c - sz, c, 1.02F, c, c, ROOF, c, c - sx, ROOF, c - sz, off, low, high, off);
            quad(pose, vc, c, 1.02F, c, c + sx, 1.02F, c + sz, c + sx, ROOF, c + sz, c, ROOF, c, low, off, off, high);
        }
        // the light on the core's top
        float y = 1.03F, r = 1.7F, strength = (0.16F + 0.05F * Mth.sin(time / 46F)) * (1 - 0.55F * open);
        int mid = a(strength, TINT), rim = a(0, TINT);
        for (int i = 0; i < 8; i++) {
            float a0 = i / 8F * Mth.TWO_PI, a1 = (i + 1) / 8F * Mth.TWO_PI;
            quad(pose, vc, c, y, c, c, y, c, c + Mth.cos(a1) * r, y, c + Mth.sin(a1) * r, c + Mth.cos(a0) * r, y, c + Mth.sin(a0) * r, mid, mid, rim, rim);
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(WingCoreBlockEntity be) {
        BlockPos p = be.getBlockPos();
        // the pair's way across to whoever it is given to is inside this too (the core answers within five blocks)
        return new AABB(p.getX() - 6, p.getY(), p.getZ() - 6, p.getX() + 7, p.getY() + 7, p.getZ() + 7);
    }
}
