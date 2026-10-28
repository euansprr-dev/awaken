package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.AscentState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Ascent's light in the world: ropes (a bright woven core and a soft glow, flying out to bite, sagging while
 * slack, falling away when let go), contrails, shock rings, the flash where a rope bites, the motes of air streaming
 * past at speed, and your own wings in first person. Vertices are written camera-relative into fixed buffers; nothing
 * is allocated per frame. Ribbons face the camera.
 */
final class AscentRender {
    private AscentRender() {}

    static final Identifier ROPE = Awaken.id("textures/ascent/rope.png");
    static final Identifier GLOW = Awaken.id("textures/ascent/glow.png");
    static final Identifier RING = Awaken.id("textures/ascent/ring.png");
    static final Identifier STAR = Awaken.id("textures/ascent/star.png");
    /** The fitted lines (tools/art/specs/ascent_rappel.py): grey-steel strands; black glass with violet light in it. */
    static final Identifier ROPE_IRON = Awaken.id("textures/ascent/rope_iron.png"), ROPE_DEEP = Awaken.id("textures/ascent/rope_deepglass.png");
    /** The gold reel, face on: a spool in a gold rim, spokes, a hub (spun at the hand while it winds). */
    static final Identifier REEL = Awaken.id("textures/ascent/reel.png");
    private static final boolean FIRST_PERSON_WINGS = true;

    private static RenderType ropeType, glowType, ringType, starType, ropeIronType, ropeDeepType, reelType;
    private static float pt;
    private static double cx, cy, cz;
    // camera right / up / back (toward the viewer)
    private static float rx, ry, rz, ux, uy, uz, bx, by, bz;
    private static final Vector3f V = new Vector3f();
    private static final org.joml.Matrix3f MA = new org.joml.Matrix3f(), MB = new org.joml.Matrix3f();
    private static final Quaternionf QW = new Quaternionf();
    private static final Quaternionf Q = new Quaternionf();
    private static boolean broken;
    /** Set while drawing ropes: ribbons thin and fade near the camera. */
    private static boolean nearFade;

    // ropes this frame: hand, anchor, sag, reach (0..1 drawn), alpha
    private static final int MAX_ROPES = 48;
    private static final double[] RH = new double[MAX_ROPES * 3], RA = new double[MAX_ROPES * 3];
    private static final float[] RSAG = new float[MAX_ROPES], RREACH = new float[MAX_ROPES], RALPHA = new float[MAX_ROPES];
    /** Each rope's line (RopeKit.LINE_*), the scroll of its strands (a rappel's rope runs through the hand), and whether
     * a gold reel winds at its hand. */
    private static final int[] RLINE = new int[MAX_ROPES];
    private static final float[] RFLOW = new float[MAX_ROPES];
    private static final boolean[] RREEL = new boolean[MAX_ROPES];
    /** A taut swing's hum (0..1: the line trembling under the weight) and a let-go line's whip (0..1, fading). */
    private static final float[] RHUM = new float[MAX_ROPES], RWHIP = new float[MAX_ROPES];
    private static float nextHum, nextWhip;
    /** The marker as drawn (eased toward where a press would throw, SPEC §17.31) and how much of it shows. */
    private static double markX, markY, markZ;
    private static float markA;
    private static boolean markZip;
    private static int ropes;
    /** The rope's line for the next rope() call, its flow and its reel. */
    private static int nextLine;
    private static float nextFlow;
    private static boolean nextReel;
    private static final double[] PTS = new double[3 * 24];
    private static final double[] F = new double[9];
    private static final double[] E = new double[6];

    private static final SubmitNodeCollector.CustomGeometryRenderer ROPES = (pose, vc) -> safe(() -> drawRopes(vc, pose, false, 0));
    private static final SubmitNodeCollector.CustomGeometryRenderer ROPES_IRON = (pose, vc) -> safe(() -> drawRopes(vc, pose, false, 1));
    private static final SubmitNodeCollector.CustomGeometryRenderer ROPES_DEEP = (pose, vc) -> safe(() -> drawRopes(vc, pose, false, 2));
    private static final SubmitNodeCollector.CustomGeometryRenderer ROPE_GLOWS = (pose, vc) -> safe(() -> drawRopes(vc, pose, true, -1));
    private static final SubmitNodeCollector.CustomGeometryRenderer REELS = (pose, vc) -> safe(() -> drawReels(vc, pose));
    private static final SubmitNodeCollector.CustomGeometryRenderer GLOWS = (pose, vc) -> safe(() -> drawGlows(vc, pose));
    private static final SubmitNodeCollector.CustomGeometryRenderer RINGS = (pose, vc) -> safe(() -> drawRings(vc, pose));
    private static final SubmitNodeCollector.CustomGeometryRenderer STARS = (pose, vc) -> safe(() -> drawStars(vc, pose));

    private static void safe(Runnable r) {
        if (broken) return;
        try {
            r.run();
        } catch (RuntimeException ex) {
            broken = true;
            Awaken.LOG.error("Ascent rendering failed and is off for this session", ex);
        }
    }

    static void onExtract(ExtractLevelRenderStateEvent e) {
        pt = e.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        Q.set(state.cameraRenderState.orientation);
        Q.transform(1, 0, 0, V);
        rx = V.x;
        ry = V.y;
        rz = V.z;
        Q.transform(0, 1, 0, V);
        ux = V.x;
        uy = V.y;
        uz = V.z;
        Q.transform(0, 0, 1, V);
        bx = V.x;
        by = V.y;
        bz = V.z;
        if (ropeType == null) {
            ropeType = RenderTypes.entityTranslucentEmissive(ROPE);
            ropeIronType = RenderTypes.entityTranslucentEmissive(ROPE_IRON);
            ropeDeepType = RenderTypes.entityTranslucentEmissive(ROPE_DEEP);
            reelType = RenderTypes.entityTranslucentEmissive(REEL);
            glowType = RenderTypes.energySwirl(GLOW, 0, 0);
            ringType = RenderTypes.energySwirl(RING, 0, 0);
            starType = RenderTypes.energySwirl(STAR, 0, 0);
        }
        try {
            gatherRopes(mc);
        } catch (RuntimeException ex) {
            broken = true;
            Awaken.LOG.error("Ascent rendering failed and is off for this session", ex);
            return;
        }
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        if (ropes > 0) {
            // one draw per line's look (each its own texture), only for the looks out this frame
            boolean plain = false, iron = false, deep = false, reel = false;
            for (int i = 0; i < ropes; i++) {
                plain |= RLINE[i] == 0;
                iron |= RLINE[i] == 1;
                deep |= RLINE[i] == 2;
                reel |= RREEL[i];
            }
            if (plain) out.order(2).submitCustomGeometry(ps, ropeType, ROPES);
            if (iron) out.order(2).submitCustomGeometry(ps, ropeIronType, ROPES_IRON);
            if (deep) out.order(2).submitCustomGeometry(ps, ropeDeepType, ROPES_DEEP);
            if (reel) out.order(2).submitCustomGeometry(ps, reelType, REELS);
            out.order(3).submitCustomGeometry(ps, glowType, ROPE_GLOWS);
        }
        out.order(3).submitCustomGeometry(ps, glowType, GLOWS);
        out.order(3).submitCustomGeometry(ps, ringType, RINGS);
        out.order(3).submitCustomGeometry(ps, starType, STARS);
        // your own wings in first person: off until they have a look of their own (drawn whole they fill the frame's
        // lower corners; Phase 1 gives them light at the edges instead)
        if (FIRST_PERSON_WINGS && mc.options.getCameraType().isFirstPerson() && AscentMoves.state().wings) firstPersonWings(ps, out, mc);
    }

    // ---- gathering ------------------------------------------------------------------------------------------------------

    private static void gatherRopes(Minecraft mc) {
        ropes = 0;
        marker();
        Entity me = mc.player;
        boolean first = mc.options.getCameraType().isFirstPerson();
        AscentState st = AscentMoves.state();
        if (st.rope) {
            if (first && st == AscentState.RAPPEL) cameraPoint(AscentFp.guideNow[0], AscentFp.guideNow[1], AscentFp.guideNow[2], RH);
            else if (first) cameraHand(RH, 0);
            else bodyHand(me, RH, 0);
            double hx = RH[0], hy = RH[1], hz = RH[2];
            double d = Math.sqrt(sq(AscentMoves.ax - hx) + sq(AscentMoves.ay - hy) + sq(AscentMoves.az - hz));
            float reach = AscentMoves.ropeTravel > 0 ? Mth.clamp(1F - (AscentMoves.ropeTravel - pt) / 3F, 0.15F, 1F) : 1F;
            float sag = AscentMoves.ropeTaut ? 0 : (float) Math.min(2.5, Math.max(0, AscentMoves.len - d) * 0.5 + 0.15);
            if (AscentMoves.ropeTravel > 0) sag = 0.2F;
            // the line's look; a rappel's rope runs through the hands as it pays out; the gold reel winds at the hand
            nextLine = AscentMoves.kitLine;
            nextFlow = (float) (AscentMoves.paid * 0.9);
            nextReel = AscentMoves.reeling && AscentMoves.kitReel == dev.awaken.body.ascent.RopeKit.REEL_GOLD;
            nextHum = AscentMoves.swinging() && AscentMoves.ropeTaut ? (float) Mth.clamp((AscentMoves.tension - 0.2) / 0.6, 0, 1) : 0;
            rope(hx, hy, hz, AscentMoves.ax, AscentMoves.ay, AscentMoves.az, sag, reach, 1F);
            if (st == AscentState.RAPPEL) {
                if (first) cameraPoint(AscentFp.brakeNow[0], AscentFp.brakeNow[1], AscentFp.brakeNow[2], TMP);
                else brakeHand(me, TMP, 0);
                tail(hx, hy, hz, AscentMoves.kitLine, (float) (AscentMoves.paid * 0.9));
            }
        }
        for (AscentRemote.Slot r : AscentRemote.SLOTS) {
            if (r.entity < 0 || !r.state.rope || ropes >= MAX_ROPES) continue;
            Entity e = mc.level.getEntity(r.entity);
            if (e == null) continue;
            bodyHand(e, RH, ropes * 3);
            double hx = RH[ropes * 3], hy = RH[ropes * 3 + 1], hz = RH[ropes * 3 + 2];
            nextLine = r.kit & 3;
            nextReel = (r.kit >> 2 & 1) == 1 && AscentRemote.winding(r);
            rope(hx, hy, hz, r.ax, r.ay, r.az, 0.1F, Mth.clamp((AscentMoves.ticks - r.since + pt) / 2F, 0.2F, 1F), 1F);
            if (r.state == AscentState.RAPPEL) {
                brakeHand(e, TMP, 0);
                tail(hx, hy, hz, r.kit & 3, (float) (-Mth.lerp(pt, e.yo, e.getY()) * 0.9));
            }
        }
        for (AscentFx.Shot s : AscentFx.SHOTS) {
            if (ropes >= MAX_ROPES) break;
            if (s.kind == AscentFx.LOOSE) {
                float u = Mth.clamp((s.age + pt) / s.life, 0, 1);
                // the free end falls and swings in toward the anchor
                double fx = Mth.lerp(u * 0.6, s.nx, s.x), fy = s.ny - 4.0 * u * u, fz = Mth.lerp(u * 0.6, s.nz, s.z);
                nextLine = s.line;
                nextWhip = 1 - u;
                rope(fx, fy, fz, s.x, s.y, s.z, 1.2F * u, 1F, (1 - u) * (1 - u));
            } else if (s.kind == AscentFx.MISS) {
                Entity e = mc.level.getEntity(s.entity);
                if (e == null) continue;
                if (e == me && first) cameraHand(RH, ropes * 3);
                else bodyHand(e, RH, ropes * 3);
                float u = Mth.clamp((s.age + pt) / s.life, 0, 1);
                float reach = Math.min(1F, u * 4F);
                double hx = RH[ropes * 3], hy = RH[ropes * 3 + 1], hz = RH[ropes * 3 + 2];
                // the far end droops back once it found nothing
                rope(hx, hy, hz, s.x, s.y - 6 * Math.max(0, u - 0.25) * Math.max(0, u - 0.25), s.z, 1.5F * u, reach, 1 - u * u);
            }
        }
    }

    private static final double[] TMP = new double[3];

    /**
     * A rappel's rope below the guiding hand at (hx, hy, hz): down to the brake hand (in TMP), then its tail hanging
     * out of that hand, swaying; the same line, running through both hands as it pays out ({@code flow}).
     */
    private static void tail(double hx, double hy, double hz, int line, float flow) {
        double tx = TMP[0], ty = TMP[1], tz = TMP[2];
        float t = AscentMoves.ticks + pt;
        nextLine = line;
        nextFlow = flow;
        rope(hx, hy, hz, tx, ty, tz, 0.05F, 1F, 1F);
        nextLine = line;
        nextFlow = flow;
        rope(tx, ty, tz, tx + Mth.sin(t * 0.13F) * 0.14, ty - 2.1, tz + Mth.cos(t * 0.11F) * 0.14, 0.12F, 1F, 0.95F);
    }

    /** A point in view space (x right, y up, z ahead negative) in the world, into out[0..2]. */
    private static void cameraPoint(double x, double y, double z, double[] out) {
        out[0] = cx + rx * x + ux * y + bx * z;
        out[1] = cy + ry * x + uy * y + by * z;
        out[2] = cz + rz * x + uz * y + bz * z;
    }

    /** Eases the drawn marker toward the anchor a press would take; fades it in and out (a far jump is a cut). */
    private static void marker() {
        boolean on = AscentMoves.markerOn && !AscentMoves.state().rope;
        var m = AscentMoves.MARKER;
        if (on) {
            double d2 = sq(m.x - markX) + sq(m.y - markY) + sq(m.z - markZ);
            if (markA < 0.05F || d2 > 36) {
                markX = m.x;
                markY = m.y;
                markZ = m.z;
            } else {
                markX += (m.x - markX) * 0.35;
                markY += (m.y - markY) * 0.35;
                markZ += (m.z - markZ) * 0.35;
            }
            markZip = m.zip;
        }
        markA += ((on ? 1F : 0F) - markA) * 0.25F;
    }

    private static void rope(double hx, double hy, double hz, double ax, double ay, double az, float sag, float reach, float alpha) {
        if (ropes >= MAX_ROPES) {
            nextLine = 0;
            nextFlow = 0;
            nextReel = false;
            nextHum = nextWhip = 0;
            return;
        }
        int i = ropes++;
        RLINE[i] = nextLine;
        RFLOW[i] = nextFlow;
        RREEL[i] = nextReel;
        RHUM[i] = nextHum;
        RWHIP[i] = nextWhip;
        nextLine = 0;
        nextFlow = 0;
        nextReel = false;
        nextHum = nextWhip = 0;
        RH[i * 3] = hx;
        RH[i * 3 + 1] = hy;
        RH[i * 3 + 2] = hz;
        RA[i * 3] = ax;
        RA[i * 3 + 1] = ay;
        RA[i * 3 + 2] = az;
        RSAG[i] = sag;
        RREACH[i] = reach;
        RALPHA[i] = alpha;
    }

    /** Your raised hand in first person: low right of the view, a little ahead. */
    /** Where the rope leaves your hand in first person: the main fist's place on a rope (AscentFp.ROPE_*, view space). */
    private static void cameraHand(double[] out, int at) {
        float side = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT
                ? -1 : 1;
        double x = side * AscentFp.ROPE_X, y = AscentFp.ROPE_Y, z = AscentFp.ROPE_Z;
        out[at] = cx + rx * x + ux * y + bx * z;
        out[at + 1] = cy + ry * x + uy * y + by * z;
        out[at + 2] = cz + rz * x + uz * y + bz * z;
    }

    /** A body's rope hand as the figure is drawn: the right arm raised along the body toward the anchor. */
    private static void bodyHand(Entity e, double[] out, int at) {
        AscentAnims.frame(e, pt, F);
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()) + 1.4, z = Mth.lerp(pt, e.zo, e.getZ());
        // a rappelling figure is drawn off the rock (AscentAnims.rappelOffset)
        AscentState st = e == Minecraft.getInstance().player ? AscentMoves.state() : AscentRemote.stateOf(e);
        if (AscentAnims.rappelOffset(e, st, OFF)) {
            x += OFF[0];
            z += OFF[1];
        }
        out[at] = x + F[6] * 0.34 + F[3] * 0.72;
        out[at + 1] = y + F[7] * 0.34 + F[4] * 0.72;
        out[at + 2] = z + F[8] * 0.34 + F[5] * 0.72;
    }

    /** A rappelling body's brake hand (the left, low at the hip, where the rope's tail runs out of it). */
    private static void brakeHand(Entity e, double[] out, int at) {
        AscentAnims.frame(e, pt, F);
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()) + 1.4, z = Mth.lerp(pt, e.zo, e.getZ());
        if (AscentAnims.rappelOffset(e, AscentState.RAPPEL, OFF)) {
            x += OFF[0];
            z += OFF[1];
        }
        // the left hand: off the body's left side, down by the hip, a little in front
        out[at] = x - F[6] * 0.32 - F[3] * 0.5 + F[0] * 0.18;
        out[at + 1] = y - F[7] * 0.32 - F[4] * 0.5 + F[1] * 0.18;
        out[at + 2] = z - F[8] * 0.32 - F[5] * 0.5 + F[2] * 0.18;
    }

    private static final double[] OFF = new double[2];

    private static double sq(double v) {
        return v * v;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------------

    /**
     * The ropes of one line's look ({@code look}: RopeKit.LINE_*), or every rope's glow ({@code glow}): the light
     * rope's cyan halo, a faint cool sheen on steel, the Deepglass line's violet.
     */
    private static void drawRopes(VertexConsumer vc, PoseStack.Pose pose, boolean glow, int look) {
        float time = AscentMoves.ticks + pt;
        for (int i = 0; i < ropes; i++) {
            if (!glow && RLINE[i] != look) continue;
            double hx = RH[i * 3], hy = RH[i * 3 + 1], hz = RH[i * 3 + 2];
            double ax = RA[i * 3], ay = RA[i * 3 + 1], az = RA[i * 3 + 2];
            float hum = RHUM[i], whip = RWHIP[i];
            int n = RSAG[i] > 0.05F || RREACH[i] < 1 || hum > 0.01F || whip > 0.01F ? 16 : 2;
            float reach = RREACH[i];
            for (int k = 0; k <= n; k++) {
                double t = (double) k / n * reach;
                double x = hx + (ax - hx) * t, y = hy + (ay - hy) * t, z = hz + (az - hz) * t;
                // slack hangs in a curve; a rope still flying out ripples
                y -= RSAG[i] * 4 * t * (1 - t) * reach;
                if (reach < 1) {
                    double w = Math.sin(t * 9 - time * 1.7) * 0.18 * (1 - reach) * t;
                    x += rx * w;
                    y += ry * w;
                    z += rz * w;
                }
                if (hum > 0.01F) {
                    // taut under the weight: the line trembles (its first two modes), still at both ends
                    double w = hum * (Math.sin(t * Math.PI) * Math.sin(time * 4.3) * 0.045 + Math.sin(t * Math.PI * 3) * Math.sin(time * 11.7) * 0.02);
                    x += rx * w;
                    y += ry * w;
                    z += rz * w;
                }
                if (whip > 0.01F) {
                    // let go: a wave runs up the line from the free end, dying as it falls
                    double w = Math.sin(t * 11 - time * 2.4) * 0.4 * whip * whip * (1 - t);
                    x += rx * w + ux * w * 0.5;
                    y += ry * w + uy * w * 0.5;
                    z += rz * w + uz * w * 0.5;
                }
                PTS[k * 3] = x;
                PTS[k * 3 + 1] = y;
                PTS[k * 3 + 2] = z;
            }
            float a = RALPHA[i];
            // close to the eye a rope thins and its glow fades, or it would fill the frame from your own hand
            nearFade = true;
            int line = RLINE[i];
            if (glow) {
                ribbon(vc, pose, PTS, n + 1, line == 1 ? 0.11F : 0.16F, line == 1 ? 0.11F : 0.16F, (int) (a * (line == 1 ? 34 : line == 2 ? 92 : 100)),
                        line == 1 ? 0xA9BCCC : line == 2 ? 0xA77CFF : 0xA8DCFF, 0F, 1F);
            } else {
                // the light rope's strands run on their own; steel only moves as the rope runs through the hand; the
                // Deepglass line's light drifts slowly along it
                float run = line == 1 ? 0F : line == 2 ? time * 0.06F : time * 0.35F;
                // a fitted line's strands keep their lay at any length (a repeat every 1.2 blocks); the light rope's
                // stretch with it as they always have
                float span = line == 0 ? (float) (reach * 3)
                        : (float) (Math.sqrt(sq(ax - hx) + sq(ay - hy) + sq(az - hz)) * reach / 1.2);
                ribbon(vc, pose, PTS, n + 1, line == 1 ? 0.045F : 0.05F, line == 1 ? 0.045F : 0.05F, (int) (a * 255), 0xFFFFFF,
                        (run + RFLOW[i]) % 1F, span);
            }
            nearFade = false;
        }
    }

    private static void drawGlows(VertexConsumer vc, PoseStack.Pose pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // contrails
        for (AscentFx.Trail t : AscentFx.TRAILS) {
            if (t.entity < 0 || t.count < 2 || t.alpha < 0.02F) continue;
            Entity e = mc.level.getEntity(t.entity);
            if (e == null) continue;
            // your own trails start at your eyes in first person: they would cut across the whole frame
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            AscentState st = e == mc.player ? AscentMoves.state() : AscentRemote.stateOf(e);
            boolean live = st.airborne();
            if (live) AscentFx.emitters(e, st, pt, E);
            for (int side = 0; side < 2; side++) {
                double[] xs = side == 0 ? t.lx : t.rx, ys = side == 0 ? t.ly : t.ry, zs = side == 0 ? t.lz : t.rz;
                int n = 0;
                if (live) {
                    PTS[0] = E[side * 3];
                    PTS[1] = E[side * 3 + 1];
                    PTS[2] = E[side * 3 + 2];
                    n = 1;
                }
                int keep = t.wings ? 13 : 7;
                for (int k = 0; k < t.count && n < keep; k++, n++) {
                    PTS[n * 3] = xs[k];
                    PTS[n * 3 + 1] = ys[k];
                    PTS[n * 3 + 2] = zs[k];
                }
                if (n < 2) continue;
                int col = t.wings ? 0xD8ECFF : 0xBFE0FF;
                ribbon(vc, pose, PTS, n, t.width, 0F, (int) (t.alpha * (t.wings ? 95 : 55)), col, 0F, 1F);
            }
        }
        // air streaming past your eyes
        double s = AscentFx.moteSpeed;
        if (s > 0.6 && !dev.awaken.body.client.feel.FeelConfig.reduced()) {
            double len = Math.min(5, s * 1.8);
            for (int i = 0; i < AscentFx.MOTES; i++) {
                int age = AscentFx.MAGE[i];
                if (age < 0) continue;
                double x = AscentFx.MX[i], y = AscentFx.MY[i], z = AscentFx.MZ[i];
                PTS[0] = x;
                PTS[1] = y;
                PTS[2] = z;
                PTS[3] = x - AscentFx.moteVx * len;
                PTS[4] = y - AscentFx.moteVy * len;
                PTS[5] = z - AscentFx.moteVz * len;
                float fade = Math.min(1F, (age + pt) / 5F) * (float) Mth.clamp((s - 0.6) / 1.2, 0, 1);
                ribbon(vc, pose, PTS, 2, 0.03F, 0.01F, (int) (fade * 120), 0xE6F1FF, 0F, 1F);
            }
        }
    }

    private static void drawRings(VertexConsumer vc, PoseStack.Pose pose) {
        for (AscentFx.Shot s : AscentFx.SHOTS) {
            if (s.kind != AscentFx.RING) continue;
            float u = Mth.clamp((s.age + pt) / s.life, 0, 1);
            float radius = (float) (0.8 + 11 * Math.pow(u, 0.5)) * s.size;
            float width = Mth.lerp(u, 2.2F, 0.5F) * s.size;
            int a = (int) (240 * (1 - u) * (1 - u * 0.5F));
            // two axes across the flight
            double nx = s.nx, ny = s.ny, nz = s.nz;
            double ax = -nz, ay = 0, az = nx, al = Math.sqrt(ax * ax + az * az);
            if (al < 1e-3) {
                ax = 1;
                az = 0;
                al = 1;
            }
            ax /= al;
            az /= al;
            double bx2 = ny * az - nz * ay, by2 = nz * ax - nx * az, bz2 = nx * ay - ny * ax;
            int seg = 28;
            for (int k = 0; k < seg; k++) {
                double a0 = k * Mth.TWO_PI / seg, a1 = (k + 1) * Mth.TWO_PI / seg;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                float r0 = radius - width * 0.5F, r1 = radius + width * 0.5F;
                double ox0 = ax * c0 + bx2 * s0, oy0 = ay * c0 + by2 * s0, oz0 = az * c0 + bz2 * s0;
                double ox1 = ax * c1 + bx2 * s1, oy1 = ay * c1 + by2 * s1, oz1 = az * c1 + bz2 * s1;
                quad2(vc, pose, a, 0xEEF6FF,
                        s.x + ox0 * r0, s.y + oy0 * r0, s.z + oz0 * r0, 0, 0,
                        s.x + ox1 * r0, s.y + oy1 * r0, s.z + oz1 * r0, 1, 0,
                        s.x + ox1 * r1, s.y + oy1 * r1, s.z + oz1 * r1, 1, 1,
                        s.x + ox0 * r1, s.y + oy0 * r1, s.z + oz0 * r1, 0, 1);
            }
        }
    }

    /**
     * The marker (SPEC §17.31): where a press would throw, a small bright point with four short ticks turning slowly
     * round it, sized to stay readable far off; cool white for a swing, warm gold for a zip up to a lip.
     */
    private static void drawMarker(VertexConsumer vc, PoseStack.Pose pose) {
        if (markA < 0.03F) return;
        float time = AscentMoves.ticks + pt;
        double d = Math.sqrt(sq(markX - cx) + sq(markY - cy) + sq(markZ - cz));
        if (d < 1) return;
        float scale = (float) (0.16 + d * 0.012);
        // drawn a little toward the eye, so the face it sits on doesn't cut it
        double pull = Math.min(0.6, scale * 1.8) / d;
        double markX = AscentRender.markX + (cx - AscentRender.markX) * pull, markY = AscentRender.markY + (cy - AscentRender.markY) * pull,
                markZ = AscentRender.markZ + (cz - AscentRender.markZ) * pull;
        int rgb = markZip ? 0xFFD58A : 0xD6F0FF;
        float pulse = 1F + 0.12F * Mth.sin(time * 0.35F);
        float c = scale * 0.55F * pulse;
        int a = (int) (230 * markA);
        quad2(vc, pose, a, rgb,
                markX - rx * c - ux * c, markY - ry * c - uy * c, markZ - rz * c - uz * c, 0, 1,
                markX + rx * c - ux * c, markY + ry * c - uy * c, markZ + rz * c - uz * c, 1, 1,
                markX + rx * c + ux * c, markY + ry * c + uy * c, markZ + rz * c + uz * c, 1, 0,
                markX - rx * c + ux * c, markY - ry * c + uy * c, markZ - rz * c + uz * c, 0, 0);
        // the four ticks: small points on a turning square round it
        float spin = time * 0.05F;
        for (int k = 0; k < 4; k++) {
            float ang = spin + k * Mth.HALF_PI;
            double ox = Mth.cos(ang) * scale * 1.6, oy = Mth.sin(ang) * scale * 1.6;
            double px = markX + rx * ox + ux * oy, py = markY + ry * ox + uy * oy, pz = markZ + rz * ox + uz * oy;
            float t = scale * 0.28F;
            quad2(vc, pose, (int) (a * 0.8F), rgb,
                    px - rx * t - ux * t, py - ry * t - uy * t, pz - rz * t - uz * t, 0, 1,
                    px + rx * t - ux * t, py + ry * t - uy * t, pz + rz * t - uz * t, 1, 1,
                    px + rx * t + ux * t, py + ry * t + uy * t, pz + rz * t + uz * t, 1, 0,
                    px - rx * t + ux * t, py - ry * t + uy * t, pz - rz * t + uz * t, 0, 0);
        }
    }

    private static void drawStars(VertexConsumer vc, PoseStack.Pose pose) {
        drawMarker(vc, pose);
        for (AscentFx.Shot s : AscentFx.SHOTS) {
            if (s.kind != AscentFx.BITE) continue;
            float u = Mth.clamp((s.age + pt) / s.life, 0, 1);
            float size = s.size * (0.6F + 0.8F * u) * (1 - u * u);
            int a = (int) (255 * (1 - u));
            double x = s.x, y = s.y, z = s.z;
            quad2(vc, pose, a, 0xFFFFFF,
                    x - rx * size - ux * size, y - ry * size - uy * size, z - rz * size - uz * size, 0, 1,
                    x + rx * size - ux * size, y + ry * size - uy * size, z + rz * size - uz * size, 1, 1,
                    x + rx * size + ux * size, y + ry * size + uy * size, z + rz * size + uz * size, 1, 0,
                    x - rx * size + ux * size, y - ry * size + uy * size, z - rz * size + uz * size, 0, 0);
        }
        // the Deepglass line: violet glints of the light inside it, travelling down toward the hand (faster as it pays out)
        float time = AscentMoves.ticks + pt;
        for (int i = 0; i < ropes; i++) {
            if (RLINE[i] != 2 || RREACH[i] < 1 || RALPHA[i] < 0.5F) continue;
            double hx = RH[i * 3], hy = RH[i * 3 + 1], hz = RH[i * 3 + 2], ax = RA[i * 3], ay = RA[i * 3 + 1], az = RA[i * 3 + 2];
            double len = Math.sqrt(sq(ax - hx) + sq(ay - hy) + sq(az - hz));
            int glints = Mth.clamp((int) (len / 6), 1, 6);
            for (int k = 0; k < glints; k++) {
                float t = 1F - ((time * 0.012F + RFLOW[i] * 0.04F + k / (float) glints + i * 0.37F) % 1F);
                double x = ax + (hx - ax) * (1 - t), y = ay + (hy - ay) * (1 - t), z = az + (hz - az) * (1 - t);
                float size = 0.07F + 0.05F * Mth.sin((time * 0.4F + k * 2.1F));
                int a = (int) (230 * Mth.sin(t * Mth.PI) * RALPHA[i]);
                quad2(vc, pose, a, k % 3 == 0 ? 0xF4ECFF : 0xB48CFF,
                        x - rx * size - ux * size, y - ry * size - uy * size, z - rz * size - uz * size, 0, 1,
                        x + rx * size - ux * size, y + ry * size - uy * size, z + rz * size - uz * size, 1, 1,
                        x + rx * size + ux * size, y + ry * size + uy * size, z + rz * size + uz * size, 1, 0,
                        x - rx * size + ux * size, y - ry * size + uy * size, z - rz * size + uz * size, 0, 0);
            }
        }
    }

    /**
     * The gold reel at a winding hand: a disc face on to the camera, spinning, a little under the fist (smaller in your
     * own first-person hand, which is close to the eye).
     */
    private static void drawReels(VertexConsumer vc, PoseStack.Pose pose) {
        float time = AscentMoves.ticks + pt;
        boolean first = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        for (int i = 0; i < ropes; i++) {
            if (!RREEL[i]) continue;
            boolean mine = i == 0 && first && AscentMoves.state().rope;
            float size = mine ? 0.055F : 0.13F;
            double x = RH[i * 3] - ux * size * 0.6, y = RH[i * 3 + 1] - uy * size * 0.6, z = RH[i * 3 + 2] - uz * size * 0.6;
            float ang = time * 0.9F;
            float c = Mth.cos(ang) * size, s = Mth.sin(ang) * size;
            // the quad's corners turned in the view plane: (±1, ±1) rotated by the spin
            double ax = rx * c + ux * s, ay = ry * c + uy * s, az = rz * c + uz * s;
            double bx2 = -rx * s + ux * c, by2 = -ry * s + uy * c, bz2 = -rz * s + uz * c;
            quad2(vc, pose, 255, 0xFFFFFF,
                    x - ax - bx2, y - ay - by2, z - az - bz2, 0, 1,
                    x + ax - bx2, y + ay - by2, z + az - bz2, 1, 1,
                    x + ax + bx2, y + ay + by2, z + az + bz2, 1, 0,
                    x - ax + bx2, y - ay + by2, z - az + bz2, 0, 0);
        }
    }

    /**
     * A camera-facing strip through world points (3 doubles each), {@code w0} wide at the first point narrowing to
     * {@code w1} at the last, alpha fading from {@code a0} toward the end of a trail (constant for a rope: a1 = 1 means
     * the far end keeps its alpha). u runs along the strip from {@code uOff} over {@code uSpan}.
     */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, double[] p, int n, float w0, float w1, int a0, int rgb,
                               float uOff, float uSpan) {
        if (a0 <= 2) return;
        boolean trail = w1 < w0 * 0.99F || uSpan == 1F && w1 == 0F;
        double px = 0, py = 0, pz = 0, pwx = 0, pwy = 0, pwz = 0;
        int pa = 0;
        float pu = 0;
        for (int k = 0; k < n; k++) {
            double x = p[k * 3], y = p[k * 3 + 1], z = p[k * 3 + 2];
            // the strip's width runs across both the segment and the line of sight
            int j = Math.min(k + 1, n - 1), i = Math.max(0, j - 1);
            double sx = p[j * 3] - p[i * 3], sy = p[j * 3 + 1] - p[i * 3 + 1], sz = p[j * 3 + 2] - p[i * 3 + 2];
            double vx = x - cx, vy = y - cy, vz = z - cz;
            double wx = sy * vz - sz * vy, wy = sz * vx - sx * vz, wz = sx * vy - sy * vx;
            double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
            float f = n == 1 ? 0 : (float) k / (n - 1);
            float w = Mth.lerp(f, w0, w1) * 0.5F;
            float near = 1F;
            if (nearFade) {
                double d = Math.sqrt(vx * vx + vy * vy + vz * vz);
                near = (float) Mth.clamp((d - 0.5) / 3.0, 0, 1);
                w *= 0.35F + 0.65F * near;
            }
            if (wl > 1e-6) {
                wx = wx / wl * w;
                wy = wy / wl * w;
                wz = wz / wl * w;
            }
            int a = Mth.clamp((int) ((trail ? a0 * (1 - f) * (1 - f * 0.3F) : a0) * (nearFade ? 0.25F + 0.75F * near : 1F)), 0, 255);
            float u = uOff + f * uSpan;
            if (k > 0) {
                vert(vc, pose, px - pwx, py - pwy, pz - pwz, pu, 0, (pa << 24) | rgb);
                vert(vc, pose, x - wx, y - wy, z - wz, u, 0, (a << 24) | rgb);
                vert(vc, pose, x + wx, y + wy, z + wz, u, 1, (a << 24) | rgb);
                vert(vc, pose, px + pwx, py + pwy, pz + pwz, pu, 1, (pa << 24) | rgb);
            }
            px = x;
            py = y;
            pz = z;
            pwx = wx;
            pwy = wy;
            pwz = wz;
            pa = a;
            pu = u;
        }
    }

    private static void quad2(VertexConsumer vc, PoseStack.Pose pose, int a, int rgb,
                              double x0, double y0, double z0, float u0, float v0, double x1, double y1, double z1, float u1, float v1,
                              double x2, double y2, double z2, float u2, float v2, double x3, double y3, double z3, float u3, float v3) {
        int c = (Mth.clamp(a, 0, 255) << 24) | rgb;
        vert(vc, pose, x0, y0, z0, u0, v0, c);
        vert(vc, pose, x1, y1, z1, u1, v1, c);
        vert(vc, pose, x2, y2, z2, u2, v2, c);
        vert(vc, pose, x3, y3, z3, u3, v3, c);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v, int argb) {
        vc.addVertex(pose, (float) (x - cx), (float) (y - cy), (float) (z - cz)).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    // ---- first-person wings ---------------------------------------------------------------------------------------------

    private static void firstPersonWings(PoseStack ps, SubmitNodeCollector out, Minecraft mc) {
        var p = mc.player;
        AscentAnims.frame(p, pt, F);
        float bodyRot = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float yaw = bodyRot * Mth.DEG_TO_RAD;
        double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
        ps.pushPose();
        ps.translate((float) (x - cx), (float) (y - cy), (float) (z - cz));
        // the same turn the figure gets (AscentAnims.onPre), about the body's centre
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        MA.set((float) -fz, 0, (float) fx, 0, 1, 0, (float) fx, 0, (float) fz);
        MB.set((float) F[6], (float) F[7], (float) F[8], (float) F[3], (float) F[4], (float) F[5], (float) F[0], (float) F[1], (float) F[2]);
        MB.mul(MA.transpose());
        ps.translate(0, 0.9F, 0);
        ps.rotate(QW.setFromNormalized(MB));
        ps.translate(0, -0.9F, 0);
        // then the renderer's own: turned to the body's yaw, flipped into model space
        ps.rotateDegrees(com.mojang.math.Axis.YP, 180F - bodyRot);
        ps.scale(-1, -1, 1);
        ps.translate(0, -1.501F, 0);
        WingLayer.drawFirstPerson(ps, out, p, pt);
        ps.popPose();
    }
}
