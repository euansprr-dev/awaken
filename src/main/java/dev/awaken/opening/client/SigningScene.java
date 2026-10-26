package dev.awaken.opening.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.anims.Ease;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Signing the register (SPEC §16.4, "The Registry ceremony"): a first-person station scene at the Registry desk. The
 * view leans over the paper laid on the counter (the Association's form: its blue header, the ruled lines, the
 * signature line, the ring where the seal goes); your hand goes to the ink pot, takes the quill, dips it, and writes
 * your name across the line in ink, stroke by stroke with the quill's scratch, the ink glossy while it's wet; a flourish
 * under it and a blot where the nib rests; the quill goes back in the pot. Bram stamps it (the Association's blue seal
 * thumps into the ring); the paper is folded and handed to you (the licence goes into your inventory). About eight seconds for a short name. Drawn for you only (others see you at the
 * desk, Bram stamping). The player's own look never moves; the view turns for the scene and back.
 */
final class SigningScene {
    private SigningScene() {}

    private static final Identifier PAPER_TEX = Awaken.id("textures/opening/registry_paper.png");
    private static final RenderType PAPER = RenderTypes.entityCutout(PAPER_TEX);
    /** The paper's size on the desk (blocks) and its texture (texels): 64 × 48. */
    private static final double PW = 0.62, PH = 0.465;
    private static final int TW = 64, TH = 48;

    // the timeline (ticks): lean in, reach the pot, dip, to the line, the name (per letter), the flourish, the blot,
    // the quill back, Bram's stamp, the seal shown, folded and handed over, lean out
    static final int LEAN = 12, REACH = 10, DIP = 7, TO_LINE = 8, PER_CHAR = 4, FLOURISH = 9, BLOT = 9, BACK = 10, REST = 6,
            STAMP = 16, HOLD = 36, FOLD = 14, OUT = 10;

    private static boolean active;
    private static int age, inkAt, endAt, total;
    private static String name = "";
    private static @Nullable Vec3 paper;
    /** The way across the desk (toward Bram) and your right, horizontal unit vectors. */
    private static double fx, fz, rx, rz;
    private static boolean inkSent, signedSent;
    private static float lastFov = 70F;
    private static final RandomSource RANDOM = RandomSource.create();
    private static @Nullable ItemStack quill, inkPot;
    private static final ItemStackRenderState QUILL_STATE = new ItemStackRenderState(), POT_STATE = new ItemStackRenderState();

    private static ItemStack quill() {
        if (quill == null) quill = new ItemStack(Items.FEATHER);
        return quill;
    }

    private static ItemStack inkPot() {
        ItemStack s = new ItemStack(Items.POTION);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(0x1C1A2A), List.of(), Optional.empty()));
        return s;
    }

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, ViewportEvent.ComputeCameraAngles.class, SigningScene::onAngles);
        bus.addListener(ComputeFovModifierEvent.class, SigningScene::onFov);
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeFov.class, e -> lastFov = (float) e.getFOV());
        bus.addListener(SubmitCustomGeometryEvent.class, SigningScene::onSubmit);
        FirstPerson.world("opening_sign", 250, SigningScene::pose);
    }

    static boolean active() {
        return active;
    }

    /** The paper's place on the desk and which way the desk runs (from you toward Bram). */
    static void begin(Vec3 paperCentre, double towardX, double towardZ) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        paper = paperCentre;
        double len = Math.max(1e-6, Math.hypot(towardX, towardZ));
        fx = towardX / len;
        fz = towardZ / len;
        rx = -fz;
        rz = fx;
        name = p.getPlainTextName();
        if (name.length() > 16) name = name.substring(0, 16);
        age = 0;
        inkAt = LEAN + REACH + DIP + TO_LINE + name.length() * PER_CHAR + FLOURISH + BLOT + BACK + REST;
        endAt = inkAt + STAMP + HOLD + FOLD;
        total = endAt + OUT;
        inkSent = signedSent = false;
        active = true;
    }

    static void reset() {
        active = false;
        paper = null;
    }

    static void tick() {
        if (!active) return;
        age++;
        int write = LEAN + REACH + DIP + TO_LINE;
        if (age == LEAN + REACH + 2) OpeningSounds.ui(SoundEvents.BOTTLE_FILL, 1.7F, 0.25F); // the dip
        if (age >= write && age < write + name.length() * PER_CHAR && (age - write) % PER_CHAR == 0 && name.charAt((age - write) / PER_CHAR) != ' ') {
            // the quill's scratch on every other letter, a little different each time
            if (((age - write) / PER_CHAR) % 2 == 0) OpeningSounds.ui(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.3F + RANDOM.nextFloat() * 0.35F, 0.45F);
        }
        int flourish = write + name.length() * PER_CHAR;
        if (age == flourish + 1) OpeningSounds.ui(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.05F, 0.4F);
        if (age == inkAt + 1 - REST - BACK + 4) OpeningSounds.ui(SoundEvents.BOTTLE_EMPTY, 1.9F, 0.15F); // the quill back in the pot
        if (age == inkAt && !inkSent) {
            inkSent = true;
            RegistryScene.inkDone();
        }
        if (age == inkAt + STAMP) {
            // Bram's stamp lands
            OpeningSounds.ui(SoundEvents.BOOK_PUT, 0.6F, 1F);
            OpeningSounds.ui(SoundEvents.ANVIL_LAND, 1.9F, 0.12F);
            OpeningClient.shake(0.12F, 4);
        }
        if (age == endAt - FOLD + 2) OpeningSounds.ui(SoundEvents.BOOK_PAGE_TURN, 1.1F, 0.8F);
        if (age == endAt && !signedSent) {
            signedSent = true;
            RegistryScene.signed();
        }
        if (age >= total) reset();
    }

    /** How far in the scene is (0 out, 1 in). */
    private static float weight(float pt) {
        float t = age + pt;
        if (t < LEAN) return Ease.at(Ease.OUT_CUBIC, t / LEAN);
        if (t < endAt) return 1;
        return 1 - Ease.at(Ease.IN_OUT_SINE, (t - endAt) / OUT);
    }

    // ---- where things are on the paper -------------------------------------------------------------------------------

    /** A point on the paper (texels: u across from its left edge, v down from its far edge), lifted a little. */
    private static void at(double u, double v, double lift, double[] out, int o) {
        Vec3 c = paper;
        if (c == null) return;
        double a = (u / TW - 0.5) * PW, b = (0.5 - v / TH) * PH;
        out[o] = c.x + rx * a + fx * b;
        out[o + 1] = c.y + 0.003 + lift;
        out[o + 2] = c.z + rz * a + fz * b;
    }

    /** The ink pot stands on the desk off the paper's far right corner. */
    private static void pot(double[] out, int o) {
        at(TW + 9, 7, 0, out, o);
    }

    /** The signature: where it starts, how big the hand writes (blocks per font pixel). */
    private static final float SIG_U = 8, SIG_V = 37.2F;

    private static float sigScale() {
        Font f = Minecraft.getInstance().font;
        double w = Math.max(1, f.width(name));
        // the line runs 39 texels; the name fills most of it, never bigger than a bold hand
        return (float) Math.min(0.011, (34.0 / TW * PW) / w);
    }

    /** The nib's place in the writing (texel coordinates on the paper) at {@code t}. */
    private static void nib(float t, double[] uv) {
        Font f = Minecraft.getInstance().font;
        float s = sigScale();
        double perPx = s / (PW / TW); // texels per font pixel
        int write = LEAN + REACH + DIP + TO_LINE;
        float k = Mth.clamp((t - write) / PER_CHAR, 0, name.length());
        int i = (int) k;
        float frac = k - i;
        double x = f.width(name.substring(0, Math.min(i, name.length())));
        if (i < name.length()) x += f.width(String.valueOf(name.charAt(i))) * frac;
        // up and down through each letter: the strokes
        double y = -3.5 - 2.6 * Math.sin(frac * Math.PI * 2 + i);
        uv[0] = SIG_U + x * perPx + (-y) * perPx * 0.25; // the hand slants
        uv[1] = SIG_V + y * perPx;
        float fl = t - write - name.length() * PER_CHAR;
        if (fl >= 0) {
            // the flourish: back under the name, then the blot at its end
            double end = SIG_U + f.width(name) * perPx;
            float q = Mth.clamp(fl / FLOURISH, 0, 1);
            uv[0] = Mth.lerp(Ease.at(Ease.IN_OUT_SINE, q), end + 2, SIG_U + 1);
            uv[1] = SIG_V + 1.2 + Math.sin(q * Math.PI) * 0.8;
        }
    }

    // ---- the view -----------------------------------------------------------------------------------------------------

    private static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!active || paper == null) return;
        float w = weight((float) e.getPartialTick());
        if (w <= 0) return;
        Vec3 o = e.getCamera().position();
        // look at the paper's near half, where the writing is
        double[] t = new double[3];
        at(TW * 0.5, TH * 0.66, 0, t, 0);
        double ax = t[0] - o.x, ay = t[1] - o.y, az = t[2] - o.z;
        float yaw = (float) (Mth.atan2(-ax, az) * Mth.RAD_TO_DEG), pitch = (float) (-Mth.atan2(ay, Math.sqrt(ax * ax + az * az)) * Mth.RAD_TO_DEG);
        e.setYaw(e.getYaw() + Mth.wrapDegrees(yaw - e.getYaw()) * w);
        e.setPitch(Mth.lerp(w, e.getPitch(), pitch));
        e.setPitch(e.getPitch() + 0.2F * w * Mth.sin((age + (float) e.getPartialTick()) * 0.11F));
    }

    private static void onFov(ComputeFovModifierEvent e) {
        if (!active) return;
        float w = weight(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (w > 0) e.setNewFovModifier(e.getNewFovModifier() * (1 - 0.42F * w));
    }

    // ---- the hands ----------------------------------------------------------------------------------------------------

    private static final double[] A = new double[3], B = new double[3], UV = new double[2];

    private static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || paper == null || mc.player == null) return false;
        float w = weight(pt);
        if (w <= 0.01F) return false;
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        float t = age + pt;
        rig.fx = fx;
        rig.fz = fz;
        rig.handDistance = 0.8;
        boolean leftMain = mc.player.getMainArm() == HumanoidArm.LEFT;
        double side = leftMain ? -1 : 1;
        // the writing hand: rest, the pot, the line, the name, the pot, rest
        double restX = eye.x + fx * 0.45 + rx * side * 0.32, restY = paper.y - 0.12, restZ = eye.z + fz * 0.45 + rz * side * 0.32;
        pot(A, 0);
        double potX = A[0], potY = A[1] + 0.2, potZ = A[2];
        int reach = LEAN, dip = reach + REACH, line = dip + DIP, write = line + TO_LINE;
        int back = write + name.length() * PER_CHAR + FLOURISH + BLOT, rest = back + BACK;
        double x, y, z;
        boolean holding = t >= dip + 2 && t < back + BACK - 2;
        if (t < reach) {
            x = restX;
            y = restY;
            z = restZ;
        } else if (t < dip) {
            float k = Ease.at(Ease.IN_OUT_SINE, (t - reach) / REACH);
            x = Mth.lerp(k, restX, potX);
            y = Mth.lerp(k, restY, potY) + 0.08 * Mth.sin(k * Mth.PI);
            z = Mth.lerp(k, restZ, potZ);
        } else if (t < line) {
            // the dip: down into the pot and out
            float k = (t - dip) / DIP;
            x = potX;
            y = potY - 0.07 * Mth.sin(k * Mth.PI);
            z = potZ;
        } else if (t < back) {
            nib(t, UV);
            at(UV[0], UV[1], 0, B, 0);
            // the fist holds the quill well above the nib and to its right, so the writing stays in view
            double nx = B[0] - fx * 0.05 + rx * side * 0.11, ny = B[1] + 0.2, nz = B[2] - fz * 0.05 + rz * side * 0.11;
            if (t < write) {
                float k = Ease.at(Ease.IN_OUT_SINE, (t - line) / TO_LINE);
                x = Mth.lerp(k, potX, nx);
                y = Mth.lerp(k, potY, ny) + 0.06 * Mth.sin(k * Mth.PI);
                z = Mth.lerp(k, potZ, nz);
            } else {
                x = nx;
                y = ny;
                z = nz;
                // lifted off the paper between words, a breath at the blot
                if (t > write + name.length() * PER_CHAR + FLOURISH) y += 0.01;
            }
        } else if (t < rest) {
            // the quill back in the pot, then the hand away
            float k = Ease.at(Ease.IN_OUT_SINE, (t - back) / BACK);
            nib(write + name.length() * PER_CHAR + FLOURISH, UV);
            at(UV[0], UV[1], 0, B, 0);
            double sx0 = B[0] - fx * 0.05 + rx * side * 0.11, sy0 = B[1] + 0.2, sz0 = B[2] - fz * 0.05 + rz * side * 0.11;
            x = Mth.lerp(k, sx0, potX);
            y = Mth.lerp(k, sy0, potY) + 0.07 * Mth.sin(k * Mth.PI);
            z = Mth.lerp(k, sz0, potZ);
        } else {
            float k = Ease.at(Ease.IN_OUT_SINE, Mth.clamp((t - rest) / REST, 0, 1));
            x = Mth.lerp(k, potX, restX);
            y = Mth.lerp(k, potY, restY);
            z = Mth.lerp(k, potZ, restZ);
        }
        y -= (1 - w) * 0.6;
        double sx = eye.x + rx * side * 0.33 - fx * 0.1, sy = eye.y - 0.4, sz = eye.z + rz * side * 0.33 - fz * 0.1;
        FirstPerson.Limb main = leftMain ? FirstPerson.Limb.LEFT_ARM : FirstPerson.Limb.RIGHT_ARM;
        rig.reach(main, sx, sy, sz, x, y, z, FirstPerson.Rig.ARM_REACH, 0, 1, 0, w);
        rig.held[leftMain ? 1 : 0] = holding ? quill() : ItemStack.EMPTY;
        // the off hand stays down out of the frame (on the paper it hid the signature: tour 1)
        return true;
    }

    // ---- the desk: the paper, the writing, the pot and the quill, the seal --------------------------------------------

    private static void onSubmit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || paper == null || mc.level == null) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        BlockPos above = BlockPos.containing(paper.x, paper.y + 0.5, paper.z);
        int light = LightCoordsUtil.pack(Math.max(9, mc.level.getBrightness(LightLayer.BLOCK, above)), mc.level.getBrightness(LightLayer.SKY, above));
        float t = age + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float fold = Mth.clamp((t - (endAt - FOLD)) / FOLD, 0, 1);
        if (fold >= 0.999F) {
            pot(ps, out, cam, light, t);
            return;
        }
        ps.pushPose();
        // the paper's far-left corner, its face up; the fold lifts it toward you and folds it in half (far over near)
        double[] c = new double[3];
        at(0, 0, 0, c, 0);
        ps.translate(c[0] - cam.x, c[1] - cam.y, c[2] - cam.z);
        if (fold > 0) {
            float k = Ease.at(Ease.IN_OUT_SINE, fold);
            ps.translate(-fx * 0.25 * k, 0.32 * k, -fz * 0.25 * k);
        }
        // basis: x along your right (the paper's across), y down the paper toward you (the font's down), z up
        float s = (float) (PW / TW);
        Matrix4f m = new Matrix4f(
                (float) rx * s, 0, (float) rz * s, 0,
                (float) -fx * s, 0, (float) -fz * s, 0,
                0, s, 0, 0,
                0, 0, 0, 1);
        if (fold > 0) {
            // the far half folds over: shown as the paper shortening toward you
            float k = Ease.at(Ease.IN_OUT_SINE, fold);
            m.translate(0, TH * 0.5F * k, 0).scale(1, 1 - 0.5F * k, 1);
        }
        ps.mulPose(m);
        PoseStack.Pose pose = ps.last();
        out.submitCustomGeometry(ps, PAPER, (pp, vc) -> paperQuad(pp, vc, light));
        Font font = mc.font;
        // the form: the Association's name in the header, the printed lines
        text(ps, out, font, "HUNTER ASSOCIATION OF CARILLON", 32, 4.4F, 0.30F, 0xFFE6EEF8, light, true);
        text(ps, out, font, "THE REGISTER OF HUNTERS", 32, 12.0F, 0.22F, 0xFF6B5E44, light, true);
        text(ps, out, font, "Assessed by the Lens: rank E", 6, 16.4F, 0.2F, 0xFF4E4636, light, false);
        text(ps, out, font, "Day of registration: the bell's morning", 6, 20.4F, 0.2F, 0xFF4E4636, light, false);
        text(ps, out, font, "Witness: Bram, officer of the Association", 6, 24.4F, 0.2F, 0xFF4E4636, light, false);
        text(ps, out, font, "Sign here", 9, 39.6F, 0.17F, 0xFF6B5E44, light, false);
        signature(ps, out, font, t, light);
        seal(ps, out, font, t, light);
        ps.popPose();
        pot(ps, out, cam, light, t);
    }

    private static void paperQuad(PoseStack.Pose pp, VertexConsumer vc, int light) {
        // texels → the basis already scales to blocks; z up is the face's normal
        vertex(vc, pp, 0, 0, 0, 0, light);
        vertex(vc, pp, 0, TH, 0, 1, light);
        vertex(vc, pp, TW, TH, 1, 1, light);
        vertex(vc, pp, TW, 0, 1, 0, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pp, float x, float y, float u, float v, int light) {
        vc.addVertex(pp, x, y, 0).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pp, 0, 0, 1);
    }

    /** Printed text on the form: at texel (u, v), {@code size} texels per font pixel, centred or from the left. */
    private static void text(PoseStack ps, SubmitNodeCollector out, Font font, String s, float u, float v, float size, int argb, int light, boolean centred) {
        ps.pushPose();
        ps.translate(u, v, 0.02F);
        ps.scale(size, size, 1);
        float x = centred ? -font.width(s) / 2F : 0;
        out.submitText(ps, x, -font.lineHeight / 2F, Component.literal(s).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, light, argb, 0, 0);
        ps.popPose();
    }

    /** Your name in ink, letter by letter behind the nib, glossy while wet; the flourish under it; the blot. */
    private static void signature(PoseStack ps, SubmitNodeCollector out, Font font, float t, int light) {
        int write = LEAN + REACH + DIP + TO_LINE;
        if (t < write) return;
        float s = sigScale(), perPx = (float) (s / (PW / TW));
        ps.pushPose();
        ps.translate(SIG_U, SIG_V, 0.025F);
        // a hand that slants: lean the letters forward
        ps.mulPose(new Matrix4f().m10(-0.28F));
        ps.scale(perPx, perPx, 1);
        float x = 0;
        for (int i = 0; i < name.length(); i++) {
            String ch = String.valueOf(name.charAt(i));
            float at = write + i * PER_CHAR;
            if (t < at) break;
            float k = Mth.clamp((t - at) / PER_CHAR, 0, 1);
            float wet = Mth.clamp(1 - (t - at - PER_CHAR) / 40F, 0, 1);
            int rgb = lerpColour(0x1C1A2A, 0x4A4478, wet);
            int a = (int) (255 * Math.min(1, k * 1.6F));
            out.submitText(ps, x, -font.lineHeight + 1, Component.literal(ch).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, light,
                    (a << 24) | rgb, 0, 0);
            x += font.width(ch);
        }
        ps.popPose();
        float fl = t - write - name.length() * PER_CHAR;
        if (fl > 0) {
            // the flourish: a stroke back under the name (texel space, flat on the paper)
            double end = SIG_U + font.width(name) * perPx + 2;
            float q = Mth.clamp(fl / FLOURISH, 0, 1);
            float from = (float) end, to = (float) Mth.lerp(Ease.at(Ease.IN_OUT_SINE, q), end, SIG_U + 1);
            rect(ps, out, Math.min(from, to), SIG_V + 0.9F, Math.max(from, to), SIG_V + 1.5F, 0xF01C1A2A, light);
            // the blot where the nib rests, spreading a little
            float b = Mth.clamp((fl - FLOURISH) / BLOT, 0, 1);
            if (b > 0) {
                float r = 0.5F + 0.6F * b;
                float bx = (float) SIG_U + 1;
                rect(ps, out, bx - r, SIG_V + 1.2F - r * 0.8F, bx + r, SIG_V + 1.2F + r * 0.8F, 0xFF16141F, light);
                rect(ps, out, bx - r * 0.7F, SIG_V + 1.2F - r, bx + r * 0.7F, SIG_V + 1.2F + r, 0xFF16141F, light);
            }
        }
    }

    /** Bram's stamp: the Association's blue seal in the ring, and his name under it. */
    private static void seal(PoseStack ps, SubmitNodeCollector out, Font font, float t, int light) {
        float k = t - (inkAt + STAMP);
        if (k < 0) return;
        float in = Mth.clamp(k / 3F, 0, 1), cx = 53, cy = 35, r = 5.4F * (1.15F - 0.15F * in);
        int ink = ((int) (235 * in) << 24) | 0x2F62A8;
        rect(ps, out, cx - r, cy - r * 0.42F, cx + r, cy + r * 0.42F, ink, light);
        rect(ps, out, cx - r * 0.42F, cy - r, cx + r * 0.42F, cy + r, ink, light);
        rect(ps, out, cx - r * 0.75F, cy - r * 0.75F, cx + r * 0.75F, cy + r * 0.75F, ink, light);
        text(ps, out, font, "E", cx, cy + 0.4F, 0.62F, ((int) (255 * in) << 24) | 0xE6EEF8, light, true);
        text(ps, out, font, "Bram", cx, cy + 8.2F, 0.2F, ((int) (220 * in) << 24) | 0x1C1A2A, light, true);
    }

    private static void rect(PoseStack ps, SubmitNodeCollector out, float x0, float y0, float x1, float y1, int argb, int light) {
        ps.pushPose();
        ps.translate(0, 0, 0.022F);
        out.submitTextBackground(ps, x0, y0, x1, y1, argb, Font.DisplayMode.POLYGON_OFFSET, light);
        ps.popPose();
    }

    /** The ink pot on the desk, and the quill standing in it while the hand doesn't hold it. */
    private static void pot(PoseStack ps, SubmitNodeCollector out, Vec3 cam, int light, float t) {
        Minecraft mc = Minecraft.getInstance();
        double[] p = new double[3];
        pot(p, 0);
        float yaw = (float) (Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
        if (inkPot == null) inkPot = inkPot();
        mc.getItemModelResolver().updateForTopItem(POT_STATE, inkPot, ItemDisplayContext.FIXED, mc.level, null, 0);
        ps.pushPose();
        ps.translate(p[0] - cam.x, p[1] - cam.y + 0.11, p[2] - cam.z);
        ps.rotateDegrees(Axis.YP, 180F - yaw);
        ps.scale(0.26F, 0.26F, 0.26F);
        POT_STATE.submit(ps, out, light, OverlayTexture.NO_OVERLAY, 0);
        ps.popPose();
        int dip = LEAN + REACH;
        int back = dip + DIP + TO_LINE + name.length() * PER_CHAR + FLOURISH + BLOT + BACK;
        boolean inPot = t < dip + 2 || t >= back - 2;
        if (!inPot) return;
        mc.getItemModelResolver().updateForTopItem(QUILL_STATE, quill(), ItemDisplayContext.FIXED, mc.level, null, 1);
        ps.pushPose();
        ps.translate(p[0] - cam.x - rx * 0.02, p[1] - cam.y + 0.25, p[2] - cam.z - rz * 0.02);
        ps.rotateDegrees(Axis.YP, 180F - yaw + 30F);
        ps.rotateDegrees(Axis.ZP, -12F);
        ps.scale(0.32F, 0.32F, 0.32F);
        QUILL_STATE.submit(ps, out, light, OverlayTexture.NO_OVERLAY, 0);
        ps.popPose();
    }

    private static int lerpColour(int a, int b, float k) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) Mth.lerp(k, ar, br) << 16 | (int) Mth.lerp(k, ag, bg) << 8 | (int) Mth.lerp(k, ab, bb);
    }
}
