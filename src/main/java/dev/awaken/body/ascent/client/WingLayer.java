package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Wings of light on a player's back (the Ascent's Wings tier; Phase 0's placeholder, built to be the real thing's
 * skeleton). Each wing is a jointed leading edge (shoulder, elbow, wrist, tip) with a fan of feathers trailing from it:
 * they unfold from the back on deploying (a beat of anticipation, then the spread), flex with every beat, sweep back as
 * speed builds, and fold back down on landing. Drawn twice, a translucent feathered body and an additive glow, so
 * they read as light by day and by night. No vanilla elytra, no vanilla model: geometry from {@link #wing}.
 */
final class WingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    static final Identifier TEXTURE = Awaken.id("textures/ascent/wing.png");
    static final Identifier GLOW = Awaken.id("textures/ascent/wing_glow.png");

    /** How open each player's wings are, eased per tick: [entity, spread0, spread1, beat phase...]. */
    private static final class Wings {
        int entity = -1;
        float spread, spreadO, beat, beatO, sweep, sweepO;
        int lastFlap = -100;
        boolean open;
        // what this frame draws (the renderers run after every submit, so each slot keeps its own)
        float dSpread, dBeat, dSweep;
        /** Drawn for your own eyes (first person): only the outer feathers, faded in, never the root at the camera. */
        boolean dFirst;
        // light, not cloth: a see-through body tinted toward the Signal's cyan, and a soft halo of glow over it
        // (radiant gold while their bearer holds the Sovereign form: SPEC §17.19)
        final SubmitNodeCollector.CustomGeometryRenderer body = (pose, vc) -> wings(pose, vc, this,
                SovereignClient.radiant(entity) ? 0x9CFFE6A8 : 0x94A8DCFF, 1F);
        final SubmitNodeCollector.CustomGeometryRenderer glow = (pose, vc) -> wings(pose, vc, this,
                SovereignClient.radiant(entity) ? 0x48FFC05A : 0x3C7FC0FF, 1.07F);
    }

    private static final Wings[] WINGS = new Wings[32];

    static {
        for (int i = 0; i < WINGS.length; i++) WINGS[i] = new Wings();
    }

    private WingLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addTo(AvatarRenderer<?> r) {
        ((AvatarRenderer) r).addLayer(new WingLayer(r));
    }

    private static Wings find(int id) {
        for (Wings w : WINGS) if (w.entity == id) return w;
        return null;
    }

    /** Wings opened for a moment outside any state (a scene): entity ids and the client tick they fold at. */
    private static final int[] FLOURISH = {-1, -1, -1, -1}, FLOURISH_UNTIL = new int[4];

    /** Opens this entity's wings for {@code ticks}, with a beat in the middle, then folds them. */
    static void flourish(int entityId, int ticks) {
        int slot = 0;
        for (int i = 0; i < FLOURISH.length; i++) {
            if (FLOURISH[i] == entityId || FLOURISH[i] < 0) {
                slot = i;
                break;
            }
        }
        FLOURISH[slot] = entityId;
        FLOURISH_UNTIL[slot] = AscentMoves.ticks + ticks;
    }

    /** A flap for this entity (a beat is a quick down-stroke then a slower recovery). */
    static void flap(int entityId) {
        Wings w = find(entityId);
        if (w != null) w.lastFlap = AscentMoves.ticks;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // anyone with wings open gets a slot; slots ease shut and free themselves
        if (mc.player != null) touch(mc.player, AscentMoves.state(), AscentMoves.speed(), AscentMoves.flapAt);
        for (AscentRemote.Slot r : AscentRemote.SLOTS) {
            if (r.entity < 0) continue;
            Entity e = mc.level.getEntity(r.entity);
            if (e != null) {
                double vx = e.getX() - e.xo, vy = e.getY() - e.yo, vz = e.getZ() - e.zo;
                touch(e, r.state, Math.sqrt(vx * vx + vy * vy + vz * vz), -100);
            }
        }
        for (int i = 0; i < FLOURISH.length; i++) {
            if (FLOURISH[i] < 0) continue;
            int left = FLOURISH_UNTIL[i] - AscentMoves.ticks;
            Entity e = mc.level.getEntity(FLOURISH[i]);
            if (left <= 0 || e == null) {
                FLOURISH[i] = -1;
                continue;
            }
            // one slow beat in the middle, the wings showing their whole span
            touch(e, AscentState.GLIDE, 0, left == 22 ? AscentMoves.ticks : -100);
        }
        for (Wings w : WINGS) {
            if (w.entity < 0) continue;
            w.spreadO = w.spread;
            w.beatO = w.beat;
            w.sweepO = w.sweep;
            Entity e = mc.level.getEntity(w.entity);
            if (e == null) {
                w.entity = -1;
                continue;
            }
            float target = w.open ? 1F : 0F;
            // opening is quick after a short gather (anticipation); folding is a slower settle (follow-through)
            w.spread += (target - w.spread) * (w.open ? 0.32F : 0.16F);
            int since = AscentMoves.ticks - w.lastFlap;
            w.beat = since < 12 ? beatCurve(since) : 0.06F * Mth.sin(AscentMoves.ticks * 0.21F + w.entity);
            if (!w.open && w.spread < 0.01F) w.entity = -1;
            w.open = false; // refreshed by touch() every tick while the state lasts
        }
    }

    private static void touch(Entity e, AscentState st, double speed, int flapAt) {
        if (!st.wings) return;
        Wings w = find(e.getId());
        if (w == null) {
            for (Wings f : WINGS) if (f.entity < 0) {
                w = f;
                w.entity = e.getId();
                w.spread = w.spreadO = 0;
                w.beat = w.beatO = 0;
                w.sweep = w.sweepO = 0;
                w.lastFlap = AscentMoves.ticks; // opening is a beat
                break;
            }
            if (w == null) return;
        }
        w.open = true;
        if (flapAt > w.lastFlap) w.lastFlap = flapAt;
        float sweepTarget = (float) Mth.clamp((speed - 0.8) / 1.6, 0, 1);
        w.sweep += (sweepTarget - w.sweep) * 0.15F;
    }

    /** One beat: down hard (to -1) over 4 ticks, back up past level over 8. */
    private static float beatCurve(int t) {
        if (t < 4) return -Mth.sin(t / 4F * Mth.HALF_PI);
        float u = (t - 4) / 8F;
        return -Mth.cos(u * Mth.HALF_PI) + 0.35F * Mth.sin(u * Mth.PI);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector out, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        Wings w = find(state.id);
        if (w == null) return;
        float pt = state.partialTick;
        float spread = Mth.lerp(pt, w.spreadO, w.spread), beat = Mth.lerp(pt, w.beatO, w.beat), sweep = Mth.lerp(pt, w.sweepO, w.sweep);
        if (spread < 0.01F) return;
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        w.dFirst = false;
        draw(poseStack, out, w, spread, beat, sweep);
        poseStack.popPose();
    }

    /** First person: your own wings, drawn in the world from the body (only their tips reach into view). */
    static boolean drawFirstPerson(PoseStack ps, SubmitNodeCollector out, Entity e, float pt) {
        Wings w = find(e.getId());
        if (w == null) return false;
        float spread = Mth.lerp(pt, w.spreadO, w.spread), beat = Mth.lerp(pt, w.beatO, w.beat), sweep = Mth.lerp(pt, w.sweepO, w.sweep);
        if (spread < 0.01F) return false;
        w.dFirst = true;
        draw(ps, out, w, spread, beat, sweep);
        return true;
    }

    private static RenderType body, glow;

    private static void draw(PoseStack ps, SubmitNodeCollector out, Wings w, float spread, float beat, float sweep) {
        if (body == null) {
            body = RenderTypes.entityTranslucentEmissive(TEXTURE);
            glow = RenderTypes.energySwirl(GLOW, 0, 0);
        }
        w.dSpread = spread;
        w.dBeat = beat;
        w.dSweep = sweep;
        out.submitCustomGeometry(ps, body, w.body);
        out.order(1).submitCustomGeometry(ps, glow, w.glow);
    }

    // ---- geometry: model space (x to the player's left, y down, z behind) ---------------------------------------------

    private static final float[] LX = new float[4], LY = new float[4], LZ = new float[4];
    private static final float[] FEATHER = {0.55F, 1.05F, 1.2F, 0.55F};
    /** Bone directions folded (down the back) and open (out to the side, a little up); x outward, y down, z back. */
    private static final float[][] FOLD = {{0.35F, -0.45F, 0.75F}, {0.12F, 0.9F, 0.35F}, {0.06F, 1F, 0.25F}};
    private static final float[][] OPEN = {{0.92F, -0.38F, 0.12F}, {1F, -0.04F, 0.1F}, {0.9F, 0.22F, 0.3F}};
    /** How far back speed sweeps each bone, and the bones' lengths. */
    private static final float[] SWEEP = {0F, 0.3F, 0.55F}, BONE = {0.55F, 0.78F, 0.92F};
    private static final float[] U = {0F, 0.3F, 0.66F, 1F};

    private static void wings(PoseStack.Pose pose, VertexConsumer vc, Wings w, int argb, float scale) {
        wing(pose, vc, 1, w.dSpread, w.dBeat, w.dSweep, argb, scale, w.dFirst);
        wing(pose, vc, -1, w.dSpread, w.dBeat, w.dSweep, argb, scale, w.dFirst);
    }

    /** First person: no feathers inside this much of the span (they'd sit on the camera), faded in over the next 0.15. */
    private static final float FIRST_CUT = 0.5F;

    /**
     * One wing, side +1 (the player's left, model +x) or -1. The leading edge's three bones turn from folded (down the
     * back) to open (out to the side, a little up) with {@code spread}; a beat swings the whole wing about the shoulder,
     * the outer bones lagging; speed sweeps the outer wing back.
     */
    private static void wing(PoseStack.Pose pose, VertexConsumer vc, int side, float spread, float beat, float sweep, int argb, float scale,
                             boolean first) {
        float s = side;
        // bone directions: folded and open, mixed by spread (x outward, y down, z back)

        LX[0] = s * 0.1F;
        LY[0] = 0.1F;
        LZ[0] = 0.17F;
        for (int b = 0; b < 3; b++) {
            float dx = Mth.lerp(spread, FOLD[b][0], OPEN[b][0]), dy = Mth.lerp(spread, FOLD[b][1], OPEN[b][1]);
            float dz = Mth.lerp(spread, FOLD[b][2], OPEN[b][2] + SWEEP[b] * sweep);
            // a beat: rotate about the body's long axis at the shoulder (outer bones swing further)
            float a = beat * (0.55F + 0.25F * b) * spread;
            float c = Mth.cos(a), sn = Mth.sin(a);
            float rx = dx * c - dy * sn, ry = dx * sn + dy * c;
            dx = rx;
            dy = ry;
            float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            float bl = BONE[b] * scale;
            LX[b + 1] = LX[b] + s * dx / l * bl;
            LY[b + 1] = LY[b] + dy / l * bl;
            LZ[b + 1] = LZ[b] + dz / l * bl;
        }
        // the trailing edge: STRIPS points along the leading edge, each trailing a feather toward the feet and a
        // little behind (folded they lie along the back); the outer ones part into separate primaries
        int a = (int) (((argb >>> 24) & 255) * Math.min(1F, spread * 1.6F));
        int col = (a << 24) | (argb & 0xFFFFFF);
        float fy = Mth.lerp(spread, 0.3F, 0.95F), fz = Mth.lerp(spread, 1F, 0.32F);
        float fl = Mth.sqrt(fy * fy + fz * fz);
        fy /= fl;
        fz /= fl;
        float px = 0, py = 0, pz = 0, ptx = 0, pty = 0, ptz = 0, pu = 0;
        for (int i = 0; i <= STRIPS; i++) {
            float u = (float) i / STRIPS;
            // the point on the leading edge, by bone (each bone a third of the span)
            float bu = u * 3;
            int b = Math.min(2, (int) bu);
            float f = bu - b;
            float x = Mth.lerp(f, LX[b], LX[b + 1]), y = Mth.lerp(f, LY[b], LY[b + 1]), z = Mth.lerp(f, LZ[b], LZ[b + 1]);
            float len = feather(u) * scale * Mth.lerp(spread, 0.55F, 1F);
            // primaries part at the tip: every other one shorter, all a little spread toward the tip
            if (u > 0.68F && (i & 1) == 1) len *= 0.8F;
            float tx = x + s * 0.05F * u, ty = y + fy * len, tz = z + fz * len;
            if (i > 0 && (!first || pu >= FIRST_CUT)) {
                float u0 = side < 0 ? 1 - pu : pu, u1 = side < 0 ? 1 - u : u;
                int c = col;
                if (first) {
                    // fade the cut edge in: the body by its alpha, the additive glow (which ignores alpha) by its colour
                    // and lighter overall: this close, at full strength they read as solid blades, not light
                    float fade = Math.min(1F, (pu - FIRST_CUT) / 0.15F);
                    c = scale > 1F ? 0xFF000000 | scaled(col, fade * 0.45F) : ((int) (((col >>> 24) & 255) * fade * 0.6F) << 24) | (col & 0xFFFFFF);
                }
                quad(pose, vc, c, px, py, pz, u0, 0, x, y, z, u1, 0, tx, ty, tz, u1, 1, ptx, pty, ptz, u0, 1);
            }
            px = x;
            py = y;
            pz = z;
            ptx = tx;
            pty = ty;
            ptz = tz;
            pu = u;
        }
    }

    private static final int STRIPS = 14;

    private static int scaled(int rgb, float f) {
        return (int) (((rgb >> 16) & 255) * f) << 16 | (int) (((rgb >> 8) & 255) * f) << 8 | (int) ((rgb & 255) * f);
    }

    /** Feather length along the span: short at the shoulder, longest mid-wing, tapering to a rounded tip. */
    private static float feather(float u) {
        for (int i = 0; i < 3; i++) {
            if (u <= U[i + 1]) return Mth.lerp((u - U[i]) / (U[i + 1] - U[i]), FEATHER[i], FEATHER[i + 1]);
        }
        return FEATHER[3];
    }

    /** A quad seen from both sides. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int col,
                             float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3) {
        vert(pose, vc, col, x0, y0, z0, u0, v0);
        vert(pose, vc, col, x1, y1, z1, u1, v1);
        vert(pose, vc, col, x2, y2, z2, u2, v2);
        vert(pose, vc, col, x3, y3, z3, u3, v3);
        vert(pose, vc, col, x3, y3, z3, u3, v3);
        vert(pose, vc, col, x2, y2, z2, u2, v2);
        vert(pose, vc, col, x1, y1, z1, u1, v1);
        vert(pose, vc, col, x0, y0, z0, u0, v0);
    }

    private static void vert(PoseStack.Pose pose, VertexConsumer vc, int col, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, -1, 0);
    }
}
