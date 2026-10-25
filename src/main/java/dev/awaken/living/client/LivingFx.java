package dev.awaken.living.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.living.LivingNet;
import dev.awaken.living.combat.PlateRules;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The band's light in the world (SPEC §21.1), drawn camera-relative like the opening's: additive meshes (vanilla's
 * lightning type, so Lens blooms them) for everything that is light, translucent quads for the plates' glass, and the
 * plates' words as text. Plates over creatures; damage written in strokes of light; XP streaming from what earned it to
 * the band; the level moment's rings and column; pillars over rare drops. Fixed pools; nothing here allocates per frame
 * beyond a few small vectors while something is drawn.
 */
final class LivingFx {
    private LivingFx() {}

    private static @Nullable RenderType add, glass;
    private static double cx, cy, cz;
    private static float pt, now;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f(), LOOK = new Vector3f();

    static final int SIGNAL = 0x7FC3FF, LIGHT = 0xDCEFFF, AMBER = 0xFFCF7A, EMBER = 0xFF7A52, WARM = 0xFFA27A, GREY = 0x8A97A8;
    static final int[] BAND = {0xF4F6FB, 0x5FD08F, 0x6FB3FF, 0xB48CFF, 0xF2A541, 0xFF5A6A};
    /** Damage colours by Force (HEAT, CHARGE, GRAVITY, SPIRIT, FROST), and kinetic, and a crit's gold. */
    static final int[] FORCE = {0xFF9A5A, 0xFFE36A, 0xC59CFF, 0x7FF0D0, 0xA8E6FF};
    static final int KINETIC = 0xE8F4FF, CRIT = 0xFFD36A;
    static final int[] XP_KIND = {0x9ED2FF, 0x9FE6D6, 0xFFE6A0, 0xFFCF7A, 0xBFE3FF};

    // ---- damage glyphs ----------------------------------------------------------------------------------------------

    static final int GLYPHS = 24;
    private static final double[] gx = new double[GLYPHS], gy = new double[GLYPHS], gz = new double[GLYPHS];
    private static final float[] gValue = new float[GLYPHS], gBorn = new float[GLYPHS], gSide = new float[GLYPHS];
    private static final int[] gEntity = new int[GLYPHS], gColour = new int[GLYPHS];
    private static final boolean[] gCrit = new boolean[GLYPHS];
    static final float GLYPH_LIFE = 14; // ticks (700 ms)

    /** A hit's number at the creature (merged into a fresh one on the same target within 6 ticks). */
    static void glyph(Entity target, float amount, boolean crit, int force) {
        float t = LivingClient.ticks;
        for (int i = 0; i < GLYPHS; i++) {
            if (gEntity[i] == target.getId() && t - gBorn[i] < 6 && gCrit[i] == crit) {
                gValue[i] += amount;
                gBorn[i] = t - 2; // keep rising, a little refreshed
                return;
            }
        }
        int slot = 0;
        for (int i = 0; i < GLYPHS; i++) {
            if (t - gBorn[i] > GLYPH_LIFE) {
                slot = i;
                break;
            }
            if (gBorn[i] < gBorn[slot]) slot = i;
        }
        var r = target.getRandom();
        gx[slot] = target.getX() + (r.nextDouble() - 0.5) * target.getBbWidth() * 0.8;
        gy[slot] = target.getY() + target.getBbHeight() * (0.55 + r.nextDouble() * 0.3);
        gz[slot] = target.getZ() + (r.nextDouble() - 0.5) * target.getBbWidth() * 0.8;
        gValue[slot] = amount;
        gBorn[slot] = t;
        gEntity[slot] = target.getId();
        gCrit[slot] = crit;
        gSide[slot] = (r.nextFloat() - 0.5F) * 0.6F;
        gColour[slot] = crit ? CRIT : force >= 0 && force < FORCE.length ? FORCE[force] : KINETIC;
    }

    // ---- XP streams -------------------------------------------------------------------------------------------------

    static final int STREAMS = 8, MOTES = 11;
    static final float STREAM_TICKS = 17; // the lead mote's flight
    private static final double[] sx = new double[STREAMS], sy = new double[STREAMS], sz = new double[STREAMS];
    private static final float[] sBorn = new float[STREAMS];
    private static final int[] sXp = new int[STREAMS], sKind = new int[STREAMS];
    private static final boolean[] sLive = new boolean[STREAMS], sArrived = new boolean[STREAMS];
    private static final String[] sText = new String[STREAMS];

    static void stream(double x, double y, double z, int xp, int kind) {
        int slot = 0;
        for (int i = 0; i < STREAMS; i++) {
            if (!sLive[i]) {
                slot = i;
                break;
            }
            if (sBorn[i] < sBorn[slot]) slot = i;
        }
        sx[slot] = x;
        sy[slot] = y;
        sz[slot] = z;
        sBorn[slot] = LivingClient.ticks;
        sXp[slot] = xp;
        sKind[slot] = Math.max(0, Math.min(XP_KIND.length - 1, kind));
        sLive[slot] = true;
        sArrived[slot] = false;
        sText[slot] = "+" + xp + " XP";
    }

    // ---- level moments ----------------------------------------------------------------------------------------------

    static final int MOMENTS = 4;
    private static final int[] mEntity = new int[MOMENTS];
    private static final float[] mBorn = new float[MOMENTS];
    private static final boolean[] mSelf = new boolean[MOMENTS];
    static final float MOMENT_TICKS = 48;

    static void moment(Entity who, boolean self) {
        int slot = 0;
        for (int i = 0; i < MOMENTS; i++) if (mBorn[i] < mBorn[slot]) slot = i;
        mEntity[slot] = who.getId();
        mBorn[slot] = LivingClient.ticks;
        mSelf[slot] = self;
    }

    static void reset() {
        for (int i = 0; i < GLYPHS; i++) gBorn[i] = -1000;
        for (int i = 0; i < STREAMS; i++) sLive[i] = false;
        for (int i = 0; i < MOMENTS; i++) mBorn[i] = -1000;
    }

    static {
        reset();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        now = LivingClient.ticks + pt;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        LOOK.set(0, 0, -1).rotate(CAM);
        if (add == null) add = RenderTypes.lightning();
        if (glass == null) glass = RenderTypes.debugQuads();
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        boolean plates = LivingClient.shown && LivingClientConfig.plates && !LivingClient.PICKED.isEmpty();
        if (plates) out.submitCustomGeometry(ps, glass, (pose, vc) -> guard(() -> platesGlass(vc, mc)));
        out.submitCustomGeometry(ps, add, (pose, vc) -> guard(() -> light(vc, mc, plates)));
        if (plates) platesText(ps, out, mc);
    }

    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[living] fx failed", ex);
        }
    }

    private static void light(VertexConsumer vc, Minecraft mc, boolean plates) {
        if (plates) platesLight(vc, mc);
        for (int i = 0; i < GLYPHS; i++) drawGlyph(vc, i);
        for (int i = 0; i < STREAMS; i++) drawStream(vc, mc, i);
        for (int i = 0; i < MOMENTS; i++) drawMoment(vc, mc, i);
        for (ItemEntity item : LivingClient.PILLARS) drawPillar(vc, item);
    }

    // ---- plates -----------------------------------------------------------------------------------------------------

    /** A plate's frame: centre, half width, half height (blocks), visibility, the entity. */
    private static final double[] PC = new double[3];

    private static boolean plateFrame(LivingEntity e, PlateRules.Candidate c, double[] centre, float[] size) {
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
        CreatureTag tag = CreatureTag.of(e);
        float halfW = (float) Mth.clamp(e.getBbWidth() * 0.65, 0.5, 1.2);
        float halfH = tag.title().isEmpty() ? 0.17F : 0.23F;
        centre[0] = x;
        centre[1] = y + e.getBbHeight() + 0.45 + halfH;
        centre[2] = z;
        size[0] = halfW;
        size[1] = halfH;
        return true;
    }

    private static final float[] SIZE = new float[2];

    private static void platesGlass(VertexConsumer vc, Minecraft mc) {
        for (PlateRules.Candidate c : LivingClient.PICKED) {
            if (!(mc.level.getEntity(c.id()) instanceof LivingEntity e)) continue;
            float vis = PlateRules.visibility(c, LivingClient.ticks, LivingClient.lookRange) * LivingClient.fadeIn(c.id());
            if (vis <= 0.01F) continue;
            plateFrame(e, c, PC, SIZE);
            float hw = SIZE[0], hh = SIZE[1];
            // the glass, a hair behind the words, denser toward the bottom
            double bx = PC[0] + LOOK.x * 0.02, by = PC[1] + LOOK.y * 0.02, bz = PC[2] + LOOK.z * 0.02;
            int top = argb(0.38F * vis, 0x16233A), bottom = argb(0.58F * vis, 0x0F1724);
            planeQuad(vc, bx, by, bz, -hw, -hh, hw, hh, top, bottom);
            // the bar's empty wells
            float bx0 = -hw + 0.06F, bx1 = hw - 0.06F, by0 = -hh + 0.05F, by1 = by0 + 0.065F;
            float seg = (bx1 - bx0) / 10F, gap = 0.018F;
            int well = argb(0.5F * vis, 0x05080E);
            for (int k = 0; k < 10; k++) planeQuad(vc, bx, by, bz, bx0 + k * seg + gap / 2, by0, bx0 + (k + 1) * seg - gap / 2, by1, well, well);
        }
    }

    private static void platesLight(VertexConsumer vc, Minecraft mc) {
        int mine = Progression.profile(mc.player).level();
        for (PlateRules.Candidate c : LivingClient.PICKED) {
            if (!(mc.level.getEntity(c.id()) instanceof LivingEntity e)) continue;
            float vis = PlateRules.visibility(c, LivingClient.ticks, LivingClient.lookRange) * LivingClient.fadeIn(c.id());
            if (vis <= 0.01F) continue;
            plateFrame(e, c, PC, SIZE);
            float hw = SIZE[0], hh = SIZE[1];
            CreatureTag tag = CreatureTag.of(e);
            int rim = tag.kind().hostile() ? WARM : tag.kind() == CreatureTag.Kind.PASSIVE || tag.kind() == CreatureTag.Kind.AMBIENT ? 0 : SIGNAL;
            if (tag.elite()) rim = AMBER;
            int rimA = argb(0.55F * vis, rim == 0 ? 0x5A7088 : rim);
            // hairline rim and brighter corner brackets
            float b = Math.min(0.12F, hw * 0.3F);
            planeLine(vc, -hw, hh, hw, hh, 0.012F, rimA);
            planeLine(vc, -hw, -hh, hw, -hh, 0.012F, rimA);
            planeLine(vc, -hw, -hh, -hw, hh, 0.012F, rimA);
            planeLine(vc, hw, -hh, hw, hh, 0.012F, rimA);
            int brk = argb(0.95F * vis, rim == 0 ? SIGNAL : rim);
            planeLine(vc, -hw, hh, -hw + b, hh, 0.03F, brk);
            planeLine(vc, -hw, hh, -hw, hh - b, 0.03F, brk);
            planeLine(vc, hw, -hh, hw - b, -hh, 0.03F, brk);
            planeLine(vc, hw, -hh, hw, -hh + b, 0.03F, brk);
            // the health segments: light for what's left, amber draining for what was just lost
            float frac = Mth.clamp(e.getHealth() / Math.max(1F, e.getMaxHealth()), 0, 1);
            float drain = Math.max(frac, LivingClient.drained(e.getId(), frac));
            float bx0 = -hw + 0.06F, bx1 = hw - 0.06F, by0 = -hh + 0.05F, by1 = by0 + 0.065F;
            float seg = (bx1 - bx0) / 10F, gap = 0.018F;
            float flash = 0.65F + 0.35F * Mth.sin(now * 1.3F);
            for (int k = 0; k < 10; k++) {
                float s0 = k / 10F, s1 = (k + 1) / 10F;
                float x0 = bx0 + k * seg + gap / 2, x1 = bx0 + (k + 1) * seg - gap / 2;
                float lit = Mth.clamp((frac - s0) / (s1 - s0), 0, 1);
                if (lit > 0) planeQuadLight(vc, x0, by0, x0 + (x1 - x0) * lit, by1, argb(0.9F * vis, frac < 0.25F ? 0xFF9A8A : 0xBFE7FF));
                float lost = Mth.clamp((drain - s0) / (s1 - s0), 0, 1);
                if (lost > lit) planeQuadLight(vc, x0 + (x1 - x0) * lit, by0, x0 + (x1 - x0) * lost, by1, argb(0.85F * vis * flash, AMBER));
            }
            // a scan band sweeping down over the creature when the band first reads it
            float scan = LivingClient.scanAge(e.getId()) + pt;
            if (scan >= 0 && scan < 5) {
                float k = scan / 5F;
                double sy = e.getY() + e.getBbHeight() * (1 - k);
                double ex = Mth.lerp(pt, e.xo, e.getX()), ez = Mth.lerp(pt, e.zo, e.getZ());
                flatRing(vc, ex, sy, ez, e.getBbWidth() * 0.75F, 0.05F, argb(0.8F * (1 - k), SIGNAL));
                planeLine(vc, -hw, hh - 2 * hh * k, hw, hh - 2 * hh * k, 0.02F, argb(0.9F * (1 - k), LIGHT));
            }
            // a deadly creature: a small warning chevron beside the level
            int lv = tag.level();
            if (lv >= mine + 5) {
                float wx = hw - 0.03F, wy = hh - 0.08F;
                planeLine(vc, wx - 0.05F, wy - 0.04F, wx - 0.025F, wy + 0.03F, 0.016F, argb(vis, EMBER));
                planeLine(vc, wx - 0.025F, wy + 0.03F, wx, wy - 0.04F, 0.016F, argb(vis, EMBER));
            }
        }
    }

    private static void platesText(PoseStack ps, SubmitNodeCollector out, Minecraft mc) {
        Font font = mc.font;
        Profile prof = Progression.profile(mc.player);
        int mine = prof.level();
        for (PlateRules.Candidate c : LivingClient.PICKED) {
            if (!(mc.level.getEntity(c.id()) instanceof LivingEntity e)) continue;
            float vis = PlateRules.visibility(c, LivingClient.ticks, LivingClient.lookRange) * LivingClient.fadeIn(c.id());
            if (vis <= 0.04F) continue;
            plateFrame(e, c, PC, SIZE);
            float hw = SIZE[0], hh = SIZE[1];
            CreatureTag tag = CreatureTag.of(e);
            float s = 0.0105F;
            ps.pushPose();
            ps.translate(PC[0] - cx, PC[1] - cy, PC[2] - cz);
            ps.rotate(CAM);
            ps.scale(s, -s, s);
            float left = (-hw + 0.06F) / s, top = (-hh + 0.03F) / s, right = (hw - 0.06F) / s;
            // the level, in its band's colour (grey far below you, ember far above)
            int lv = tag.level();
            int lvCol = lv <= mine - 10 ? GREY : lv >= mine + 5 ? EMBER : BAND[dev.awaken.living.api.Regions.bandOf(lv)];
            String lvText = "Lv " + lv;
            float lvX = right - font.width(lvText) - (lv >= mine + 5 ? 0.07F / s : 0);
            lightText(out, ps, font, lvText, lvX, top, lvCol, vis);
            // the name (unknown until the Codex has it); an elite says so first
            String name = name(e, tag, prof);
            float nx = left;
            if (tag.elite()) {
                lightText(out, ps, font, "ELITE", nx, top, AMBER, vis);
                nx += font.width("ELITE ") + 2;
            }
            int room = (int) (lvX - nx - 4);
            if (font.width(name) > room) name = font.plainSubstrByWidth(name, Math.max(10, room - font.width("…"))) + "…";
            lightText(out, ps, font, name, nx, top, LIGHT, vis);
            if (!tag.title().isEmpty()) {
                ps.pushPose();
                ps.translate(left, top + 11, 0);
                ps.scale(0.78F, 0.78F, 1);
                lightText(out, ps, font, tag.title(), 0, 0, AMBER, vis * 0.95F);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    /** Text as light: four faint cyan offsets for a halo, then the words. */
    private static void lightText(SubmitNodeCollector out, PoseStack ps, Font font, String text, float x, float y, int rgb, float alpha) {
        var seq = Component.literal(text).getVisualOrderText();
        int halo = argb(0.22F * alpha, SIGNAL);
        for (int i = 0; i < 4; i++) {
            float ox = i == 0 ? 0.6F : i == 1 ? -0.6F : 0, oy = i == 2 ? 0.6F : i == 3 ? -0.6F : 0;
            out.submitText(ps, x + ox, y + oy, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, halo, 0, 0);
        }
        out.submitText(ps, x, y, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, argb(alpha, rgb), 0, 0);
    }

    static String name(LivingEntity e, CreatureTag tag, Profile prof) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        boolean fauna = Awaken.MODID.equals(key.getNamespace()) && (tag.kind() == CreatureTag.Kind.AMBIENT || tag.kind() == CreatureTag.Kind.NEUTRAL
                || tag.kind() == CreatureTag.Kind.HUNTER || tag.kind() == CreatureTag.Kind.BEAST);
        if (fauna && prof.counter("awaken:discovered/creature/" + key.getPath()) <= 0) return "Unknown";
        return e.getName().getString();
    }

    // ---- glyphs -----------------------------------------------------------------------------------------------------

    private static void drawGlyph(VertexConsumer vc, int i) {
        float age = now - gBorn[i];
        if (age < 0 || age > GLYPH_LIFE) return;
        float k = age / GLYPH_LIFE;
        float rise = 1 - (1 - k) * (1 - k);
        float pop = age < 1.6F ? 1.25F - 0.25F * (age / 1.6F) : 1;
        float alpha = k < 0.65F ? 1 : 1 - (k - 0.65F) / 0.35F;
        boolean crit = gCrit[i];
        float unit = (crit ? 0.06F : 0.038F) * pop;
        double x = gx[i] + RIGHT.x * gSide[i] * rise, y = gy[i] + 0.9 * rise, z = gz[i] + RIGHT.z * gSide[i] * rise;
        String text = gValue[i] >= 10 || gValue[i] == Math.floor(gValue[i]) ? Integer.toString(Math.max(1, Math.round(gValue[i])))
                : String.format(java.util.Locale.ROOT, "%.1f", gValue[i]);
        int col = gColour[i];
        strokes(vc, text, x, y, z, unit, crit ? 0.16F : 0.11F, argb(alpha, col), argb(0.22F * alpha, col));
        if (crit && age < 5) {
            // the fracture: short rays out of the number, gone in a quarter of a second
            float f = age / 5F;
            float w = StrokeFont.width(text) * unit;
            double mx = x + RIGHT.x * w * 0.5, my = y + StrokeFont.HEIGHT * unit * 0.5, mz = z + RIGHT.z * w * 0.5;
            for (int r = 0; r < 7; r++) {
                double a = r * Math.PI * 2 / 7 + gSide[i] * 3;
                float r0 = w * (0.55F + f * 0.4F), r1 = w * (0.75F + f * 0.9F);
                double ax = Math.cos(a), ay = Math.sin(a);
                Vec3 p0 = new Vec3(mx + (RIGHT.x * ax + UP.x * ay) * r0, my + (RIGHT.y * ax + UP.y * ay) * r0, mz + (RIGHT.z * ax + UP.z * ay) * r0);
                Vec3 p1 = new Vec3(mx + (RIGHT.x * ax + UP.x * ay) * r1, my + (RIGHT.y * ax + UP.y * ay) * r1, mz + (RIGHT.z * ax + UP.z * ay) * r1);
                beam(vc, p0, p1, 0.03F, argb(0.9F * (1 - f), CRIT));
            }
            flare(vc, mx, my, mz, w * (0.6F + f), argb(0.35F * (1 - f), CRIT));
        }
    }

    /** A string in strokes of light from (x, y, z), left to right on the camera's plane; unit = blocks per grid step. */
    private static void strokes(VertexConsumer vc, String s, double x, double y, double z, float unit, float width, int core, int glow) {
        float pen = 0;
        for (int c = 0; c < s.length(); c++) {
            char ch = s.charAt(c);
            if (ch == ' ') {
                pen += StrokeFont.ADVANCE * 0.6F;
                continue;
            }
            for (float[] seg : StrokeFont.glyph(ch)) {
                float x0 = pen + seg[0] + seg[1] * StrokeFont.SLANT, x1 = pen + seg[2] + seg[3] * StrokeFont.SLANT;
                stroke(vc, x, y, z, x0 * unit, seg[1] * unit, x1 * unit, seg[3] * unit, width * unit * 4, core);
                stroke(vc, x, y, z, x0 * unit, seg[1] * unit, x1 * unit, seg[3] * unit, width * unit * 12, glow);
            }
            pen += StrokeFont.ADVANCE;
        }
    }

    /** One stroke on the camera's plane, from (u0, v0) to (u1, v1) about (x, y, z): bright on its line, soft to its edges. */
    private static void stroke(VertexConsumer vc, double x, double y, double z, float u0, float v0, float u1, float v1, float width, int argb) {
        float du = u1 - u0, dv = v1 - v0;
        float len = Mth.sqrt(du * du + dv * dv);
        if (len < 1e-5F) {
            du = 0.001F;
            len = 0.001F;
        }
        float nu = -dv / len * width * 0.5F, nv = du / len * width * 0.5F;
        // extend the ends a touch so joints close
        float eu = du / len * width * 0.3F, ev = dv / len * width * 0.3F;
        u0 -= eu;
        v0 -= ev;
        u1 += eu;
        v1 += ev;
        int rim = argb & 0x00FFFFFF;
        p(vc, x, y, z, u0, v0, argb);
        p(vc, x, y, z, u1, v1, argb);
        p(vc, x, y, z, u1 + nu, v1 + nv, rim);
        p(vc, x, y, z, u0 + nu, v0 + nv, rim);
        p(vc, x, y, z, u0 - nu, v0 - nv, rim);
        p(vc, x, y, z, u1 - nu, v1 - nv, rim);
        p(vc, x, y, z, u1, v1, argb);
        p(vc, x, y, z, u0, v0, argb);
    }

    private static void p(VertexConsumer vc, double x, double y, double z, float u, float v, int argb) {
        v(vc, x + RIGHT.x * u + UP.x * v, y + RIGHT.y * u + UP.y * v, z + RIGHT.z * u + UP.z * v, argb);
    }

    // ---- the XP stream ----------------------------------------------------------------------------------------------

    private static void drawStream(VertexConsumer vc, Minecraft mc, int i) {
        if (!sLive[i]) return;
        float age = now - sBorn[i];
        float total = STREAM_TICKS + MOTES * 0.6F + 2;
        if (age > total) {
            sLive[i] = false;
            return;
        }
        Vec3 target = bandTarget(mc);
        double ox = sx[i], oy = sy[i], oz = sz[i];
        double dx = target.x - ox, dy = target.y - oy, dz = target.z - oz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double mx = ox + dx * 0.5, my = oy + dy * 0.5 + 1.0 + dist * 0.12, mz = oz + dz * 0.5;
        int col = XP_KIND[sKind[i]];
        double leadX = 0, leadY = 0, leadZ = 0;
        float leadU = 0;
        for (int m = 0; m < MOTES; m++) {
            float u = Mth.clamp((age - m * 0.6F) / STREAM_TICKS, 0, 1);
            if (u <= 0 || u >= 1) continue;
            float e = u < 0.5F ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
            float eb = Math.max(0, e - 0.05F);
            double bx = bez(ox, mx, target.x, e), by = bez(oy, my, target.y, e), bz = bez(oz, mz, target.z, e);
            double tx = bez(ox, mx, target.x, eb), ty = bez(oy, my, target.y, eb), tz = bez(oz, mz, target.z, eb);
            float a = (m == 0 ? 1F : 0.75F) * Math.min(1, u * 6);
            // nearer the camera a mote shrinks, so the stream reads the same as it lands in the band
            double cdx = bx - cx, cdy = by - cy, cdz = bz - cz;
            float near = (float) Mth.clamp(Math.sqrt(cdx * cdx + cdy * cdy + cdz * cdz) / 3.0, 0.15, 1);
            beam(vc, new Vec3(tx, ty, tz), new Vec3(bx, by, bz), 0.035F * near, argb(0.55F * a, col));
            flare(vc, bx, by, bz, (m == 0 ? 0.09F : 0.06F) * near, argb(0.95F * a, col));
            if (m == 0) {
                leadX = bx;
                leadY = by;
                leadZ = bz;
                leadU = u;
            }
        }
        if (leadU > 0.08F && leadU < 0.82F && sXp[i] > 0) {
            double cdx = leadX - cx, cdy = leadY - cy, cdz = leadZ - cz;
            float near = (float) Mth.clamp(Math.sqrt(cdx * cdx + cdy * cdy + cdz * cdz) / 4.0, 0.12, 1);
            float a = Math.min(1, (leadU - 0.08F) * 8) * Math.min(1, (0.82F - leadU) * 8);
            strokes(vc, sText[i], leadX + UP.x * 0.12 * near, leadY + 0.12 * near, leadZ + UP.z * 0.12 * near, 0.018F * near, 0.11F,
                    argb(0.95F * a, col), argb(0.2F * a, col));
        }
        if (!sArrived[i] && age >= STREAM_TICKS) {
            sArrived[i] = true;
            LivingClient.arrived(sXp[i], sKind[i]);
        }
    }

    private static double bez(double a, double b, double c, float t) {
        double u = 1 - t;
        return u * u * a + 2 * u * t * b + t * t * c;
    }

    /** Where XP lands: in first person the HUD's level line (just in front of the eye); in third the left wrist. */
    static Vec3 bandTarget(Minecraft mc) {
        var p = mc.player;
        if (mc.options.getCameraType().isFirstPerson()) {
            var win = mc.getWindow();
            int gw = win.getGuiScaledWidth(), gh = win.getGuiScaledHeight();
            double lx = gw / 2.0 - 91 - 6 - 26, ly = gh - 12;
            double ndcX = lx / gw * 2 - 1, ndcY = 1 - ly / gh * 2;
            double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
            double aspect = (double) win.getWidth() / Math.max(1, win.getHeight());
            double d = 1.2;
            double rx = ndcX * tan * aspect * d, uy = ndcY * tan * d;
            return new Vec3(cx + LOOK.x * d + RIGHT.x * rx + UP.x * uy, cy + LOOK.y * d + RIGHT.y * rx + UP.y * uy,
                    cz + LOOK.z * d + RIGHT.z * rx + UP.z * uy);
        }
        double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
        float yaw = (float) Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
        return new Vec3(x + lx * 0.38 + fx * 0.18, y + 0.95, z + lz * 0.38 + fz * 0.18);
    }

    // ---- the level moment -------------------------------------------------------------------------------------------

    private static void drawMoment(VertexConsumer vc, Minecraft mc, int i) {
        float age = now - mBorn[i];
        if (age < 0 || age > MOMENT_TICKS) return;
        if (!(mc.level.getEntity(mEntity[i]) instanceof LivingEntity who)) return;
        double x = Mth.lerp(pt, who.xo, who.getX()), y = Mth.lerp(pt, who.yo, who.getY()) + 0.06, z = Mth.lerp(pt, who.zo, who.getZ());
        float k = age / MOMENT_TICKS;
        // two rings widening across the ground, the second a beat later
        for (int r = 0; r < 2; r++) {
            float a2 = age - r * 5;
            if (a2 < 0 || a2 > 26) continue;
            float kk = a2 / 26F;
            float rad = 0.6F + 6.5F * (1 - (1 - kk) * (1 - kk));
            flatRing(vc, x, y, z, rad, 0.09F + 0.05F * (1 - kk), argb(0.85F * (1 - kk), r == 0 ? SIGNAL : 0xBFE3FF));
        }
        // square motes lifting off the first ring
        for (int s = 0; s < 22; s++) {
            float born = (s % 7) * 0.9F;
            float a3 = age - born;
            if (a3 < 0 || a3 > 22) continue;
            float kk = a3 / 22F;
            double ang = s * 2.399963 + mEntity[i];
            float rad = 0.8F + 5.2F * Math.min(1, (born + 4) / 26F) * (0.6F + 0.4F * ((s * 37) % 10) / 10F);
            double px = x + Math.cos(ang) * rad, pz = z + Math.sin(ang) * rad, py = y + 2.2 * kk;
            flare(vc, px, py, pz, 0.07F, argb(0.9F * (1 - kk), 0xBFE3FF));
        }
        // a column of light rising out of the band, brightest at first
        float colA = (k < 0.15F ? k / 0.15F : 1 - (k - 0.15F) / 0.85F) * (mSelf[i] ? 0.55F : 0.75F);
        if (colA > 0) {
            beam(vc, new Vec3(x, y, z), new Vec3(x, y + 14, z), 0.9F, argb(colA * 0.4F, SIGNAL));
            beam(vc, new Vec3(x, y, z), new Vec3(x, y + 9, z), 0.18F, argb(colA, LIGHT));
        }
        if (age < 6) flare(vc, x, y + 1.1, z, 1.6F * (1 - age / 6F) + 0.3F, argb(0.6F * (1 - age / 6F), LIGHT));
    }

    // ---- loot pillars -----------------------------------------------------------------------------------------------

    private static void drawPillar(VertexConsumer vc, ItemEntity item) {
        int tier = LivingClient.pillarTier(item);
        if (tier <= 0) return;
        int col = tier == 3 ? 0xFFF0C0 : tier == 2 ? 0xB48CFF : 0x6FB3FF;
        double x = Mth.lerp(pt, item.xo, item.getX()), y = Mth.lerp(pt, item.yo, item.getY()) + 0.1, z = Mth.lerp(pt, item.zo, item.getZ());
        float pulse = 0.85F + 0.15F * Mth.sin(now * 0.15F + item.getId());
        beam(vc, new Vec3(x, y, z), new Vec3(x, y + 2.5, z), 0.11F, argb(0.95F * pulse, col));
        beam(vc, new Vec3(x, y + 2.5, z), new Vec3(x, y + 5, z), 0.09F, argb(0.55F * pulse, col));
        beam(vc, new Vec3(x, y + 5, z), new Vec3(x, y + 8, z), 0.07F, argb(0.22F * pulse, col));
        beam(vc, new Vec3(x, y, z), new Vec3(x, y + 4, z), 0.5F, argb(0.16F * pulse, col));
        float ring = (now + item.getId() * 7) % 32 / 32F;
        flatRing(vc, x, y - 0.05, z, 0.3F + ring * 1.3F, 0.05F, argb(0.7F * (1 - ring), col));
    }

    // ---- primitives -------------------------------------------------------------------------------------------------

    static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** A quad on the camera's plane about the plate centre {@link #PC} offset to (bx, by, bz): two colours top to bottom. */
    private static void planeQuad(VertexConsumer vc, double bx, double by, double bz, float u0, float v0, float u1, float v1, int top, int bottom) {
        p(vc, bx, by, bz, u0, v0, bottom);
        p(vc, bx, by, bz, u1, v0, bottom);
        p(vc, bx, by, bz, u1, v1, top);
        p(vc, bx, by, bz, u0, v1, top);
    }

    private static void planeQuadLight(VertexConsumer vc, float u0, float v0, float u1, float v1, int argb) {
        planeQuad(vc, PC[0], PC[1], PC[2], u0, v0, u1, v1, argb, argb);
    }

    private static void planeLine(VertexConsumer vc, float u0, float v0, float u1, float v1, float width, int argb) {
        stroke(vc, PC[0], PC[1], PC[2], u0, v0, u1, v1, width, argb);
    }

    private static final float[] QX = new float[4], QY = new float[4], QZ = new float[4];
    private static final int[] QC = new int[4];
    private static int qn;

    /** Quads in both windings, so they read from either side. */
    private static void v(VertexConsumer vc, double x, double y, double z, int argb) {
        QX[qn] = (float) (x - cx);
        QY[qn] = (float) (y - cy);
        QZ[qn] = (float) (z - cz);
        QC[qn] = argb;
        if (++qn < 4) return;
        qn = 0;
        for (int i = 0; i < 4; i++) vc.addVertex(QX[i], QY[i], QZ[i]).setColor(QC[i]);
        for (int i = 3; i >= 0; i--) vc.addVertex(QX[i], QY[i], QZ[i]).setColor(QC[i]);
    }

    /** A ribbon between two points turned to the camera, bright on its line, nothing at its edges. */
    private static void beam(VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb) {
        if (width <= 0.001F || (argb >>> 24) == 0) return;
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double len2 = dx * dx + dy * dy + dz * dz;
        double k = len2 < 1e-9 ? 0 : Mth.clamp(((cx - a.x) * dx + (cy - a.y) * dy + (cz - a.z) * dz) / len2, 0, 1);
        double vx = a.x + dx * k - cx, vy = a.y + dy * k - cy, vz = a.z + dz * k - cz;
        double nx = dy * vz - dz * vy, ny = dz * vx - dx * vz, nz = dx * vy - dy * vx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-9) return;
        double s = width * 0.5 / nl;
        nx *= s;
        ny *= s;
        nz *= s;
        int r = argb & 0x00FFFFFF;
        v(vc, a.x, a.y, a.z, argb);
        v(vc, b.x, b.y, b.z, argb);
        v(vc, b.x + nx, b.y + ny, b.z + nz, r);
        v(vc, a.x + nx, a.y + ny, a.z + nz, r);
        v(vc, a.x - nx, a.y - ny, a.z - nz, r);
        v(vc, b.x - nx, b.y - ny, b.z - nz, r);
        v(vc, b.x, b.y, b.z, argb);
        v(vc, a.x, a.y, a.z, argb);
    }

    /** A soft round glow facing the camera. */
    private static void flare(VertexConsumer vc, double px, double py, double pz, float r, int argb) {
        if (r <= 0.001F || (argb >>> 24) == 0) return;
        int rr = argb & 0x00FFFFFF;
        int n = r < 0.3F ? 6 : r < 2F ? 10 : 14;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            v(vc, px, py, pz, argb);
            v(vc, px, py, pz, argb);
            v(vc, px + RIGHT.x * c1 + UP.x * s1, py + RIGHT.y * c1 + UP.y * s1, pz + RIGHT.z * c1 + UP.z * s1, rr);
            v(vc, px + RIGHT.x * c0 + UP.x * s0, py + RIGHT.y * c0 + UP.y * s0, pz + RIGHT.z * c0 + UP.z * s0, rr);
        }
    }

    /** A flat ring on the ground: bright on its circle, soft either side. */
    private static void flatRing(VertexConsumer vc, double px, double py, double pz, float r, float half, int argb) {
        if (r <= 0.01F || (argb >>> 24) == 0) return;
        int rr = argb & 0x00FFFFFF;
        int n = Math.max(20, Math.min(96, (int) (r * 10)));
        float ri = Math.max(0, r - half), ro = r + half;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            v(vc, px + c0 * ri, py, pz + s0 * ri, rr);
            v(vc, px + c1 * ri, py, pz + s1 * ri, rr);
            v(vc, px + c1 * r, py, pz + s1 * r, argb);
            v(vc, px + c0 * r, py, pz + s0 * r, argb);
            v(vc, px + c0 * r, py, pz + s0 * r, argb);
            v(vc, px + c1 * r, py, pz + s1 * r, argb);
            v(vc, px + c1 * ro, py, pz + s1 * ro, rr);
            v(vc, px + c0 * ro, py, pz + s0 * ro, rr);
        }
    }
}
