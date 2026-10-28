package dev.awaken.aim.client;

import dev.awaken.Awaken;
import dev.awaken.aim.AimRules;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.api.HudState;
import dev.awaken.core.api.Strikes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The lock's look (SPEC §40.4), in the band's language: thin cyan lines with light round a hot core, the art from
 * {@code tools/art/specs/aim_reticle.py}. The mark: four corner brackets that spring in round the creature. The lock: a
 * broken ring turning slowly, four ticks that fly in, the System's diamond over it; it springs closed with a small
 * overshoot when it takes, slides to the next creature on a switch, kicks and flashes on every blow that lands, and
 * splinters when the creature falls. A creature's tell closes in on the ring as a second ring in the tell's colour (white,
 * amber, red, violet), meeting it as the blow lands; a wind-up out of view shows as an arc at that edge. The dot in the
 * middle shows only when there is something to mine, place or use.
 */
final class Reticle {
    private Reticle() {}

    static final Identifier LAYER = Awaken.id("aim_reticle");

    private static final int HOLO = 0x7FE3FF, LIGHT = 0xFFFFFF, HEAVY = 0xFFB340, PERIL = 0xFF4A3D, GRAB = 0xB77BFF;

    private static final Identifier RING = tex("ring"), RING_G = tex("ring_glow"), TICKS = tex("ticks"), TICKS_G = tex("ticks_glow"),
            BRACKET = tex("bracket"), BRACKET_G = tex("bracket_glow"), DIAMOND = tex("diamond"), DIAMOND_G = tex("diamond_glow"),
            TELL = tex("tell"), TELL_G = tex("tell_glow"), EDGE = tex("edge"), EDGE_G = tex("edge_glow"), DOT = tex("dot"),
            DOT_G = tex("dot_glow"), SHARD = tex("shard"), SHARD_G = tex("shard_glow");

    private static Identifier tex(String name) {
        return Awaken.id("textures/gui/aim/" + name + ".png");
    }

    /** The ring's radius last drawn (GUI px): the release's fading ring and the splinters start from it. */
    static float lastR = 16F;
    private static float lastX, lastY, slideFromX, slideFromY;
    private static int lastId = -1;
    private static long lastNanos;
    private static float dotAlpha;

    // the tell being shown on the ring: whose, its first count (the ring closes over it) and its last
    private static int tellOf = -1, tellTotal = 1, tellLast;
    private static final float[] P = new float[2], ANCHOR = new float[3];
    private static boolean anchored;

    // ---- the camera, for placing it on the creature (as the Boss Plate's lift-off does) ---------------------------------

    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean camReady;

    static void extract(ExtractLevelRenderStateEvent e) {
        CameraRenderState cam = e.getRenderState().cameraRenderState;
        VIEW_PROJ.set(cam.projectionMatrix).mul(cam.viewRotationMatrix);
        camPos = cam.pos;
        camReady = true;
    }

    /** The GUI position of a world point into P; false behind the camera or well off the screen. */
    private static boolean project(double x, double y, double z, int w, int h, float margin) {
        if (!camReady) return false;
        TMP.set((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1F);
        VIEW_PROJ.transform(TMP);
        if (TMP.w <= 0.05F) return false;
        float nx = TMP.x / TMP.w, ny = TMP.y / TMP.w;
        if (Math.abs(nx) > margin || Math.abs(ny) > margin) return false;
        P[0] = (nx + 1) * 0.5F * w;
        P[1] = (1 - ny) * 0.5F * h;
        return true;
    }

    /** Where the ring is this frame (Arms' Resonance arc rides under it). */
    static boolean anchor(float[] out) {
        if (!anchored) return false;
        out[0] = ANCHOR[0];
        out[1] = ANCHOR[1];
        out[2] = ANCHOR[2];
        return true;
    }

    // ---- the layer --------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        anchored = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.hud.isHidden() || !AimConfig.enabled() || p.isSpectator()) return;
        float pt = dt.getGameTimeDeltaPartialTick(true);
        long now = System.nanoTime();
        float frame = lastNanos == 0 ? 0F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean reduced = FirstPerson.reducedMotion.getAsBoolean();

        int id = Lock.locked >= 0 ? Lock.locked : markShown(p) ? Lock.marked : -1;
        boolean drew = false;
        if (id >= 0 && mc.level.getEntity(id) instanceof LivingEntity le && le.isAlive()) drew = target(g, p, le, Lock.locked >= 0, pt, now, frame, w, h, reduced);
        if (!drew) lastId = -1;
        gone(g, now, w, h, reduced);
        if (AimConfig.threats()) threats(g, p, pt, now, w, h);
        dot(g, mc, p, frame, w, h);
    }

    /** The mark shows while a weapon is in hand or a fight is on (the lock always shows). */
    private static boolean markShown(LocalPlayer p) {
        return AimConfig.mark() && Lock.marked >= 0 && (HudState.fighting() || dev.awaken.arms.Arms.styleOf(p.getMainHandItem()) != null);
    }

    private static boolean target(GuiGraphicsExtractor g, LocalPlayer p, LivingEntity le, boolean locked, float pt, long now, float frame,
                                  int w, int h, boolean reduced) {
        double x = Mth.lerp(pt, le.xo, le.getX()), z = Mth.lerp(pt, le.zo, le.getZ());
        double y = Mth.lerp(pt, le.yo, le.getY()) + le.getBbHeight() * 0.5;
        if (!project(x, y, z, w, h, 1.05F)) {
            if (locked) offScreen(g, p, le, pt, now, w, h);
            return false;
        }
        float sx = P[0], sy = P[1];
        // the ring's size: the body's half-size on the screen, kept between 11 and 44 GUI px
        float half = Math.max(le.getBbHeight(), le.getBbWidth()) * 0.55F;
        float r = project(x, y + half, z, w, h, 3F) ? Math.abs(sy - P[1]) * 1.08F + 5F : lastR;
        r = Mth.clamp(r, 11F, 44F);
        // a new target: brackets spring in where it is; a switch slides the ring from the last one
        if (le.getId() != lastId) {
            slideFromX = lastId >= 0 ? lastX : sx;
            slideFromY = lastId >= 0 ? lastY : sy;
            if (lastId < 0) lastR = r;
        }
        lastId = le.getId();
        lastR += (r - lastR) * Math.min(1F, frame * 14F);
        r = lastR;
        float slide = locked ? ease(ms(now, Lock.switchedAt) / 130F) : 1F;
        if (slide < 1F && !reduced) {
            sx = Mth.lerp(slide, slideFromX, sx);
            sy = Mth.lerp(slide, slideFromY, sy);
        }
        lastX = sx;
        lastY = sy;

        // the tell on this creature: its class, and how far the closing ring has come
        Strikes.Tell tell = Strikes.tell(le);
        int tellColour = -1;
        float tellLeft = 0;
        if (tell != null && tell.kind() != Strikes.Kind.NONE && tell.ticks() > 0) {
            if (tellOf != le.getId() || tell.ticks() > tellLast) tellTotal = Math.max(1, tell.ticks());
            tellOf = le.getId();
            tellLast = tell.ticks();
            tellColour = colour(tell.kind());
            tellLeft = Mth.clamp((tell.ticks() - pt) / tellTotal, 0F, 1F);
        } else if (tellOf == le.getId()) {
            tellOf = -1;
        }

        int base = tellColour >= 0 ? mix(HOLO, tellColour, 0.65F) : HOLO;
        if (locked) lockRing(g, sx, sy, r, base, now, reduced);
        else brackets(g, sx, sy, r, base, now, reduced);
        if (tellColour >= 0) {
            // the closing ring: from twice the ring's size down onto it as the blow lands
            float tr = r * (1F + 1.15F * tellLeft);
            float pulse = 0.75F + 0.25F * Mth.sin(now / 1.0E9F * 18F);
            glowed(g, TELL, TELL_G, 128, 128, sx, sy, tr * 2.3F, tr * 2.3F, 0F, tellColour, (0.45F + 0.55F * (1F - tellLeft)) * pulse);
        }
        ANCHOR[0] = sx;
        ANCHOR[1] = sy;
        ANCHOR[2] = r;
        anchored = true;
        return true;
    }

    /** The lock: the ring, the ticks and the diamond, springing closed when it takes. */
    private static void lockRing(GuiGraphicsExtractor g, float sx, float sy, float r, int colour, long now, boolean reduced) {
        float t = ms(now, Lock.lockedAt);
        float k = reduced ? 1F : spring(t);
        float scale = Mth.lerp(k, 1.9F, 1F);
        float alpha = Mth.clamp(t / 90F, 0F, 1F);
        // a blow that lands: the ring kicks out and flashes white for a moment
        float hit = 1F - Mth.clamp(ms(now, Lock.hitAt) / 170F, 0F, 1F);
        if (!reduced) scale *= 1F + 0.09F * hit * hit;
        int ringColour = mix(colour, LIGHT, 0.55F * hit);
        float spin = now / 1.0E9F * 16F + (reduced ? 0F : 70F * (1F - k));
        float size = r * scale * 2.45F;
        glowed(g, RING, RING_G, 128, 128, sx, sy, size, size, spin, ringColour, alpha);
        // the ticks come in from further out, a beat behind the ring
        float kt = reduced ? 1F : spring(t - 45F);
        float ts = r * Mth.lerp(kt, 2.6F, 1F) * 2.45F;
        glowed(g, TICKS, TICKS_G, 128, 128, sx, sy, ts, ts, 0F, ringColour, Mth.clamp((t - 45F) / 90F, 0F, 1F));
        // the diamond over it, dropping in, breathing
        float kd = reduced ? 1F : spring(t - 80F);
        float bob = reduced ? 0F : Mth.sin(now / 1.0E9F * 2.4F) * 0.7F;
        float dy = sy - r * scale - 8F - 12F * (1F - kd) + bob;
        glowed(g, DIAMOND, DIAMOND_G, 32, 32, sx, dy, 9F, 9F, 0F, ringColour, Mth.clamp((t - 80F) / 90F, 0F, 1F));
    }

    /** The mark: four corner brackets, springing in from wider. */
    private static void brackets(GuiGraphicsExtractor g, float sx, float sy, float r, int colour, long now, boolean reduced) {
        float t = ms(now, Lock.markedAt);
        float k = reduced ? 1F : spring(t * 1.3F);
        float half = r * Mth.lerp(k, 1.55F, 1.02F) + (reduced ? 0F : Mth.sin(now / 1.0E9F * 2.0F) * 0.5F);
        float alpha = 0.78F * Mth.clamp(t / 110F, 0F, 1F);
        float size = 13F;
        for (int i = 0; i < 4; i++) {
            float cx = sx + (i == 1 || i == 2 ? half : -half), cy = sy + (i >= 2 ? half : -half);
            // the bracket's corner sits at the square's corner: its 64 px art has the corner at (7, 7)
            float off = size * (32F - 7F) / 64F;
            float ox = (i == 1 || i == 2 ? -off : off), oy = (i >= 2 ? -off : off);
            glowed(g, BRACKET, BRACKET_G, 64, 64, cx + ox, cy + oy, size, size, i * 90F, colour, alpha);
        }
    }

    /** A lock on something out of the frame: the diamond at that edge, pointing the way. */
    private static void offScreen(GuiGraphicsExtractor g, LocalPlayer p, LivingEntity le, float pt, long now, int w, int h) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 c = le.getBoundingBox().getCenter();
        float relDeg = Mth.wrapDegrees(Lock.yawTo(eye, c) - p.getYRot(pt)), relP = Lock.pitchTo(eye, c) - p.getXRot(pt);
        float rel = relDeg * Mth.DEG_TO_RAD, ax = Mth.sin(rel), ay;
        // ahead but over or under the frame (a flier overhead): up or down; else round the way it lies
        if (Math.abs(relDeg) < 60F) ay = Math.abs(relP) < 1F ? Math.signum(relP + 0.01F) : Mth.clamp(relP / 30F, -1F, 1F);
        else ay = -Mth.cos(rel);
        float len = (float) Math.sqrt(ax * ax + ay * ay);
        if (len < 1.0E-3F) return;
        float ex = w / 2F + ax / len * (w / 2F - 18F), ey = h / 2F + ay / len * (h / 2F - 18F);
        float pulse = 0.7F + 0.3F * Mth.sin(now / 1.0E9F * 6F);
        glowed(g, EDGE, EDGE_G, 128, 32, ex, ey, 44F, 11F, (float) Math.toDegrees(Math.atan2(ax, -ay)), HOLO, 0.8F * pulse);
        glowed(g, DIAMOND, DIAMOND_G, 32, 32, ex - ax / len * 9F, ey - ay / len * 9F, 8F, 8F, 0F, HOLO, 0.9F);
    }

    /** The release's ring opening out and fading, and a fallen creature's splinters flying off the ring. */
    private static void gone(GuiGraphicsExtractor g, long now, int w, int h, boolean reduced) {
        float tr = ms(now, Lock.releasedAt), tb = ms(now, Lock.brokeAt);
        if (tr >= 220F && tb >= 520F) return;
        if (!project(Lock.goneX, Lock.goneY, Lock.goneZ, w, h, 1.1F)) return;
        float sx = P[0], sy = P[1], r = Lock.goneR;
        if (tr < 220F) {
            float f = tr / 220F;
            float size = r * (1F + 0.5F * f) * 2.45F;
            glowed(g, RING, RING_G, 128, 128, sx, sy, size, size, now / 1.0E9F * 16F, HOLO, (1F - f) * 0.8F);
        }
        if (tb < 520F) {
            float f = tb / 520F;
            float flash = 1F - Mth.clamp(tb / 120F, 0F, 1F);
            if (flash > 0) glowed(g, RING, RING_G, 128, 128, sx, sy, r * 2.45F * (1F + 0.25F * (1F - flash)), r * 2.45F, 0F, LIGHT, flash);
            if (reduced) return;
            float out = r * (1F + 1.6F * (1F - (1F - f) * (1F - f)));
            for (int i = 0; i < 10; i++) {
                float a = i * 36F + 11F * (i & 3);
                float rad = a * Mth.DEG_TO_RAD;
                float d = out * (0.85F + 0.3F * ((i * 7) % 5) / 5F);
                glowed(g, SHARD, SHARD_G, 16, 16, sx + Mth.sin(rad) * d, sy - Mth.cos(rad) * d, 4.5F, 9F, a + f * 90F * ((i & 1) == 0 ? 1 : -1), i % 3 == 0 ? LIGHT : HOLO,
                        (1F - f) * (1F - f));
            }
        }
    }

    /** A blow wound up out of view, within 12 blocks: an arc at that edge in the tell's colour, brighter as it nears. */
    private static void threats(GuiGraphicsExtractor g, LocalPlayer p, float pt, long now, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = p.getEyePosition(pt);
        float viewYaw = p.getYRot(pt);
        float rad = Math.min(w, h) * 0.43F;
        int shown = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (shown >= 4) break;
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive()) continue;
            if (le.distanceToSqr(p) > AimRules.THREAT_RANGE * AimRules.THREAT_RANGE) continue;
            Strikes.Tell tell = Strikes.tell(le);
            if (tell == null || tell.kind() == Strikes.Kind.NONE || tell.ticks() <= 0) continue;
            AABB b = le.getBoundingBox();
            Vec3 c = b.getCenter();
            if (project(c.x, c.y, c.z, w, h, 0.92F)) continue; // in view: its own tell (and the ring) say it
            float rel = Mth.wrapDegrees(Lock.yawTo(eye, c) - viewYaw);
            float a = rel * Mth.DEG_TO_RAD;
            float near = 1F - Mth.clamp(tell.ticks() / 12F, 0F, 1F);
            float pulse = 0.8F + 0.2F * Mth.sin(now / 1.0E9F * 22F);
            float ex = w / 2F + Mth.sin(a) * rad * (w / (float) Math.min(w, h)) * 0.98F, ey = h / 2F - Mth.cos(a) * rad;
            int col = colour(tell.kind());
            glowed(g, EDGE, EDGE_G, 128, 32, ex, ey, 64F + 18F * near, 16F + 4F * near, rel, col, (0.55F + 0.45F * near) * pulse);
            shown++;
        }
    }

    /** The dot: only when you can mine, place or use something (and no crosshair is drawn). */
    private static void dot(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer p, float frame, int w, int h) {
        boolean want = !HudState.crosshair() && Dot.wanted(mc, p);
        dotAlpha += ((want ? 1F : 0F) - dotAlpha) * Math.min(1F, frame * (want ? 18F : 8F));
        if (dotAlpha < 0.02F) return;
        glowed(g, DOT, DOT_G, 16, 16, w / 2F, h / 2F, 6F, 6F, 0F, 0xEAF8FF, dotAlpha * 0.95F);
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** A shape with its light: the glow dark underneath (it reads on a bright sky), then tinted, then the hot core. */
    private static void glowed(GuiGraphicsExtractor g, Identifier core, Identifier glow, int tw, int th, float cx, float cy, float w, float h, float deg,
                               int colour, float alpha) {
        if (alpha <= 0.01F) return;
        sprite(g, glow, tw, th, cx, cy, w * 1.04F, h * 1.04F, deg, ARGB.color(Math.round(alpha * 0.34F * 255), 0x000000));
        sprite(g, glow, tw, th, cx, cy, w, h, deg, ARGB.color(Math.round(alpha * 0.62F * 255), colour));
        sprite(g, core, tw, th, cx, cy, w, h, deg, ARGB.color(Math.round(alpha * 255), mix(colour, LIGHT, 0.45F)));
    }

    private static void sprite(GuiGraphicsExtractor g, Identifier tex, int tw, int th, float cx, float cy, float w, float h, float deg, int argb) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (deg != 0F) pose.rotate(deg * Mth.DEG_TO_RAD);
        pose.scale(w / tw, h / th);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -tw / 2, -th / 2, 0F, 0F, tw, th, tw, th, argb);
        pose.popMatrix();
    }

    /** A damped spring from 0 to 1 over about 200 ms, overshooting a little (the ring snapping shut). */
    static float spring(float ms) {
        if (ms <= 0F) return 0F;
        return 1F - (float) (Math.exp(-ms / 70.0) * Math.cos(ms / 70.0 * 1.35));
    }

    private static float ease(float f) {
        f = Mth.clamp(f, 0F, 1F);
        return 1F - (1F - f) * (1F - f) * (1F - f);
    }

    private static float ms(long now, long at) {
        return at == 0 ? Float.MAX_VALUE : (now - at) / 1.0E6F;
    }

    static int colour(Strikes.Kind kind) {
        return switch (kind) {
            case HEAVY -> HEAVY;
            case PERILOUS -> PERIL;
            case GRAB -> GRAB;
            default -> LIGHT;
        };
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255)), gr = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255)),
                bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return (r << 16) | (gr << 8) | bl;
    }
}
