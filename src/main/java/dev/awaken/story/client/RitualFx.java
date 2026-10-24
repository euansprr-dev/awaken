package dev.awaken.story.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.mobs.client.RitualAnchors;
import dev.awaken.mobs.entity.RitualStatue;
import dev.awaken.mobs.entity.SeatedOne;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The ritual's light, drawn camera-relative as additive meshes (vanilla's lightning render type, so Lens blooms them):
 * the Seated One's eyes, the gaze's line and beam, the statues' swings and glares, the rune ring's pits, the plates, the
 * words carved in the air, dust falling when it wakes, the light streaming into its palm, and the motes of the void.
 * Everything lives in fixed pools: nothing is allocated per frame.
 */
final class RitualFx {
    private RitualFx() {}

    private static @Nullable RenderType add;
    private static double cx, cy, cz;
    private static float pt;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    // ---- motes: dust, the drain, the void --------------------------------------------------------------------------

    private static final int MOTES = 480;
    private static final float[] mx = new float[MOTES], my = new float[MOTES], mz = new float[MOTES];
    private static final float[] px = new float[MOTES], py = new float[MOTES], pz = new float[MOTES];
    private static final float[] vx = new float[MOTES], vy = new float[MOTES], vz = new float[MOTES];
    private static final int[] life = new int[MOTES], maxLife = new int[MOTES], kind = new int[MOTES];
    private static final int DUST = 1, DRAIN = 2, VOID = 3, EMBER = 4;
    private static final java.util.Random R = new java.util.Random(77);

    static void reset() {
        for (int i = 0; i < MOTES; i++) life[i] = 0;
    }

    private static void spawn(int k, double x, double y, double z, double vx0, double vy0, double vz0, int ticks) {
        for (int i = 0; i < MOTES; i++) {
            if (life[i] > 0) continue;
            mx[i] = px[i] = (float) (x - cx0());
            my[i] = py[i] = (float) y;
            mz[i] = pz[i] = (float) (z - cz0());
            vx[i] = (float) vx0;
            vy[i] = (float) vy0;
            vz[i] = (float) vz0;
            life[i] = maxLife[i] = ticks;
            kind[i] = k;
            return;
        }
    }

    // mote positions are kept relative to a fixed origin near the hall so floats stay precise at gate-cell coordinates
    private static double originX, originZ;

    private static double cx0() {
        return originX;
    }

    private static double cz0() {
        return originZ;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (cursorLife > 0) cursorLife--;
        if (!RitualClient.throne.equals(Vec3.ZERO)) {
            originX = Math.floor(RitualClient.throne.x);
            originZ = Math.floor(RitualClient.throne.z);
        } else {
            originX = Math.floor(mc.player.getX());
            originZ = Math.floor(mc.player.getZ());
        }
        Vec3 palm = palm(0);
        for (int i = 0; i < MOTES; i++) {
            if (life[i] <= 0) continue;
            px[i] = mx[i];
            py[i] = my[i];
            pz[i] = mz[i];
            if (kind[i] == DRAIN) {
                // drawn into the palm, faster as it gets close
                float tx = (float) (palm.x - originX), ty = (float) palm.y, tz = (float) (palm.z - originZ);
                float dx = tx - mx[i], dy = ty - my[i], dz = tz - mz[i];
                float d = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 0.001F;
                float acc = 0.05F + 0.6F / (d + 1);
                vx[i] = vx[i] * 0.92F + dx / d * acc;
                vy[i] = vy[i] * 0.92F + dy / d * acc;
                vz[i] = vz[i] * 0.92F + dz / d * acc;
                if (d < 0.8F) life[i] = 1;
            } else if (kind[i] == DUST) {
                vy[i] = Math.max(-0.18F, vy[i] - 0.01F);
            } else if (kind[i] == VOID) {
                vx[i] += (R.nextFloat() - 0.5F) * 0.004F;
                vy[i] += (R.nextFloat() - 0.5F) * 0.004F;
                vz[i] += (R.nextFloat() - 0.5F) * 0.004F;
            } else if (kind[i] == EMBER) {
                vy[i] += 0.002F;
            }
            mx[i] += vx[i];
            my[i] += vy[i];
            mz[i] += vz[i];
            life[i]--;
        }
        int beat = RitualClient.beat;
        float age = RitualClient.beatAge(0);
        // dust shaken loose from the vault while it wakes
        if (beat == RitualClient.WAKING && age > 10 && age < 110) {
            for (int n = 0; n < 6; n++) {
                double x = RitualClient.door.x + (R.nextFloat() - 0.5) * 40, z = RitualClient.door.z + 4 + R.nextFloat() * 40;
                spawn(DUST, x, RitualClient.throne.y + 20 + R.nextFloat() * 3, z, 0, -0.05, 0, 60 + R.nextInt(40));
            }
        }
        // the hall's light drained into the palm, from everywhere, faster toward the end
        if (beat == RitualClient.END && age > 40 && age < 222) {
            int n = 2 + (int) (age / 30);
            for (int k = 0; k < n; k++) {
                double x = RitualClient.door.x + (R.nextFloat() - 0.5) * 42, z = RitualClient.door.z + 2 + R.nextFloat() * 38;
                double y = RitualClient.altar.y + 0.5 + R.nextFloat() * (R.nextFloat() < 0.7 ? 2.5 : 14);
                spawn(DRAIN, x, y, z, 0, 0.05, 0, 120);
            }
        }
        // a few motes of blue in the dark of the question
        if (beat == StoryPayloads.RitualScene.VOID && RitualClient.ticks % 4 == 0) {
            Vec3 p = mc.player.getEyePosition();
            double a = R.nextFloat() * Math.PI * 2, r = 1.5 + R.nextFloat() * 4;
            spawn(VOID, p.x + Math.cos(a) * r, p.y - 1.5 + R.nextFloat() * 3.5, p.z + Math.sin(a) * r,
                    (R.nextFloat() - 0.5) * 0.01, 0.004 + R.nextFloat() * 0.01, (R.nextFloat() - 0.5) * 0.01, 120 + R.nextInt(80));
        }
        // embers off a lit plate
        for (RitualClient.Cue c : RitualClient.CUES) {
            if (!c.live) continue;
            if (c.kind == RitualClient.PLATE && c.age(0) < c.ticks && RitualClient.ticks % 2 == 0) {
                double a = R.nextFloat() * Math.PI * 2, r = R.nextFloat() * 1.6;
                spawn(EMBER, c.a.x + Math.cos(a) * r, c.a.y + 0.1, c.a.z + Math.sin(a) * r, 0, 0.04 + R.nextFloat() * 0.03, 0, 40);
            }
        }
    }

    // ---- anchors ---------------------------------------------------------------------------------------------------

    static @Nullable SeatedOne seatedEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RitualClient.seated < 0) return null;
        Entity e = mc.level.getEntity(RitualClient.seated);
        return e instanceof SeatedOne s ? s : null;
    }

    /** The point between the Seated One's eyes (from its posed head when it's drawn, else from the throne). */
    static Vec3 eyes(float partial) {
        SeatedOne s = seatedEntity();
        if (s != null) {
            Vec3 a = RitualAnchors.eyes(s);
            if (a != null) return a;
            float yaw = Mth.lerp(partial, s.yHeadRotO, s.yHeadRot) * Mth.DEG_TO_RAD;
            return s.position().add(-Mth.sin(yaw) * 2.0, 19.5, Mth.cos(yaw) * 2.0);
        }
        return RitualClient.throne.add(0, 19.5, -2);
    }

    static Vec3 eye(float partial, int side) {
        SeatedOne s = seatedEntity();
        if (s != null) {
            Vec3 a = RitualAnchors.eye(s, side);
            if (a != null) return a;
        }
        Vec3 c = eyes(partial);
        float yaw = s == null ? 0 : Mth.lerp(partial, s.yHeadRotO, s.yHeadRot) * Mth.DEG_TO_RAD;
        return c.add(Mth.cos(yaw) * 0.75 * side, 0, Mth.sin(yaw) * 0.75 * side);
    }

    /** The raised palm. */
    static Vec3 palm(float partial) {
        SeatedOne s = seatedEntity();
        if (s != null) {
            Vec3 a = RitualAnchors.palm(s);
            if (a != null) return a;
        }
        return RitualClient.throne.add(3, 17, -8);
    }

    /** How bright the eyes are (0 closed, 1 open, more while it gazes or the moment they open). */
    static float eyeLight(float partial) {
        SeatedOne s = seatedEntity();
        return s == null ? 0 : RitualAnchors.eyeLight(s, partial);
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    static void submit(SubmitCustomGeometryEvent e) {
        int beat = RitualClient.beat;
        if (beat == StoryPayloads.RitualScene.NONE || beat == StoryPayloads.RitualScene.TEAR) {
            if (!anyMote()) return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        if (add == null) add = RenderTypes.lightning();
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        out.submitCustomGeometry(ps, add, (pose, vc) -> {
            try {
                draw(vc);
            } catch (RuntimeException ex) {
                dev.awaken.Awaken.LOG.error("[story] ritual fx failed", ex);
            }
        });
        words(ps, out, mc);
    }

    /** A point moved a little toward the camera (a light in front of the stone that holds it). */
    private static Vec3 towardCamera(Vec3 p, double by) {
        Vec3 d = new Vec3(cx - p.x, cy - p.y, cz - p.z);
        double len = d.length();
        return len < by * 2 ? p : p.add(d.scale(by / len));
    }

    private static boolean anyMote() {
        for (int i = 0; i < MOTES; i++) if (life[i] > 0) return true;
        return false;
    }

    private static void draw(VertexConsumer vc) {
        // the eyes
        float light = eyeLight(pt);
        if (light > 0.01F) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 p = towardCamera(eye(pt, side), 0.35);
                flare(vc, p, 0.32F, argb(1F, 1F, 0.97F, 0.88F * Math.min(1, light)));
                flare(vc, p, 1.4F * light, argb(1F, 0.86F, 0.5F, 0.45F * Math.min(1.5F, light)));
                flare(vc, p, 4.5F * Math.min(1.6F, light), argb(1F, 0.7F, 0.32F, 0.12F * Math.min(1.5F, light)));
            }
            Vec3 c = eyes(pt);
            streak(vc, c, 7.5F * Math.min(1.8F, light), 0.22F, argb(1F, 0.85F, 0.55F, 0.3F * Math.min(1.5F, light)));
        }
        for (RitualClient.Cue c : RitualClient.CUES) {
            if (!c.live) continue;
            float age = c.age(pt);
            switch (c.kind) {
                case RitualClient.GAZE_CHARGE -> gazeLine(vc, c, age);
                case RitualClient.GAZE_FIRE -> gazeBeam(vc, c, age);
                case RitualClient.SWING -> swing(vc, c, age);
                case RitualClient.PIT -> pit(vc, c, age);
                case RitualClient.PLATE -> plate(vc, c, age);
                case RitualClient.RULE_STRIKE -> strike(vc, c, age);
                case RitualClient.GLARE -> glare(vc, c, age);
                case RitualClient.IMPACT -> impact(vc, c, age);
                default -> {}
            }
        }
        if (RitualClient.beat == RitualClient.END) palmLight(vc);
        if (cursorLife > 0) {
            flare(vc, cursor, 1.1F, argb(1F, 0.97F, 0.85F, 0.8F));
            flare(vc, cursor, 3.2F, argb(1F, 0.8F, 0.5F, 0.25F));
        }
        motes(vc);
    }

    /** The gaze's line on the floor: dark red, filling from the throne end to the far end, brightening as it fills. */
    private static void gazeLine(VertexConsumer vc, RitualClient.Cue c, float age) {
        if (age > c.ticks + 2) return;
        float fill = Mth.clamp(age / c.ticks, 0, 1);
        float half = c.f;
        Vec3 a = c.a, b = c.b;
        float pulse = 0.75F + 0.25F * Mth.sin(age * 0.9F);
        groundStrip(vc, a, b, half, 0, 1, argb(1F, 0.18F, 0.06F, 0.55F));
        groundStrip(vc, a, b, half * 0.9F, 0, fill, argb(1F, 0.3F, 0.1F, (0.6F + 0.4F * fill) * pulse));
        groundStrip(vc, a, b, half * 0.25F, Math.max(0, fill - 0.08F), fill, argb(1F, 0.95F, 0.8F, 1F));
        // the edges, sharp
        Vec3 n = side(a, b);
        groundStrip(vc, a.add(n.scale(half)), b.add(n.scale(half)), 0.08F, 0, 1, argb(1F, 0.35F, 0.15F, 1F));
        groundStrip(vc, a.subtract(n.scale(half)), b.subtract(n.scale(half)), 0.08F, 0, 1, argb(1F, 0.35F, 0.15F, 1F));
    }

    /** The gaze fires: a white-hot beam from the eyes sweeping down the line, and the floor burning behind it. */
    private static void gazeBeam(VertexConsumer vc, RitualClient.Cue c, float age) {
        if (age > c.ticks + 20) return;
        Vec3 eyes = eyes(pt);
        Vec3 from = RitualClient.throne.add(0, 0.05, -4.5);
        float sweep = Mth.clamp(age / Math.max(1, c.ticks), 0, 1);
        Vec3 hit = from.add(c.b.subtract(from).scale(sweep));
        float fade = age < c.ticks ? 1 : Math.max(0, 1 - (age - c.ticks) / 20F);
        beam(vc, eyes, hit, 0.55F * fade, argb(1F, 1F, 0.95F, 0.95F * fade));
        beam(vc, eyes, hit, 1.6F * fade, argb(1F, 0.45F, 0.2F, 0.45F * fade));
        beam(vc, eyes, hit, 3.4F * fade, argb(1F, 0.3F, 0.1F, 0.15F * fade));
        flare(vc, hit.add(0, 0.4, 0), 2.6F * fade, argb(1F, 0.85F, 0.6F, 0.7F * fade));
        groundStrip(vc, from, hit, c.f, 0, 1, argb(1F, 0.5F, 0.15F, 0.55F * fade));
    }

    /** A statue winds up: the arc it will sweep fills with red; at the end it flashes. */
    private static void swing(VertexConsumer vc, RitualClient.Cue c, float age) {
        if (age > c.ticks + 6) return;
        float fill = Mth.clamp(age / c.ticks, 0, 1);
        Vec3 o = c.a;
        Entity e = Minecraft.getInstance().level.getEntity(c.id);
        if (e != null) o = e.getPosition(pt);
        double yaw = Math.atan2(c.b.z - o.z, c.b.x - o.x);
        float flash = age > c.ticks ? 1 - (age - c.ticks) / 6F : 0;
        sector(vc, o, yaw, Math.toRadians(55), c.f, 1.0F, argb(1F, 0.2F, 0.06F, 0.5F + 0.5F * flash));
        sector(vc, o, yaw, Math.toRadians(55), c.f * fill, 1.0F, argb(1F, 0.4F, 0.12F, 0.6F + 0.4F * fill));
        sectorEdge(vc, o, yaw, Math.toRadians(55), c.f, argb(1F, 0.5F, 0.2F, 1F));
    }

    /** A segment of the rune ring: warning (pulsing red), open (a glowing pit), closing. */
    private static void pit(VertexConsumer vc, RitualClient.Cue c, float age) {
        double a0 = c.id * Math.PI / 4 - Math.PI / 8, a1 = a0 + Math.PI / 4;
        Vec3 o = RitualClient.altar;
        if (c.f == 0) {
            if (age > c.ticks) return;
            float pulse = 0.5F + 0.5F * Mth.sin(age * 0.8F);
            annulus(vc, o, 7.0, 9.0, a0, a1, 0.03, argb(1F, 0.3F, 0.08F, 0.45F + 0.55F * pulse * (age / c.ticks)));
        } else if (c.f == 1) {
            if (age > c.ticks) return;
            annulus(vc, o, 7.0, 9.0, a0, a1, -1.85, argb(1F, 0.45F, 0.12F, 0.9F));
            annulus(vc, o, 7.0, 9.0, a0, a1, -0.6, argb(1F, 0.3F, 0.06F, 0.35F));
        }
    }

    /** A lit plate: a gold ring round it, a column of light over it, a countdown arc. */
    private static void plate(VertexConsumer vc, RitualClient.Cue c, float age) {
        if (age > c.ticks + 8) return;
        float left = 1 - Mth.clamp(age / c.ticks, 0, 1);
        float on = age > c.ticks ? 1 - (age - c.ticks) / 8F : Math.min(1, age / 4F);
        annulus(vc, c.a, 1.45, 1.75, 0, Math.PI * 2, 0.04, argb(1F, 0.85F, 0.45F, 0.85F * on));
        annulus(vc, c.a, 0.0, 1.45, 0, Math.PI * 2, 0.03, argb(1F, 0.75F, 0.35F, 0.25F * on));
        annulus(vc, c.a, 1.9, 2.1, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * left, 0.05, argb(1F, 0.95F, 0.75F, 0.8F * on));
        beam(vc, c.a, c.a.add(0, 9, 0), 2.6F * on, argb(1F, 0.8F, 0.4F, 0.14F * on));
        beam(vc, c.a, c.a.add(0, 6, 0), 0.9F * on, argb(1F, 0.95F, 0.7F, 0.3F * on));
    }

    /** The rule strike: a crack of red light from the statue to the one who stood. */
    private static void strike(VertexConsumer vc, RitualClient.Cue c, float age) {
        if (age > c.ticks + 8) return;
        float f = age < c.ticks ? 1 : 1 - (age - c.ticks) / 8F;
        beam(vc, c.a, c.b, 0.5F * f, argb(1F, 0.9F, 0.75F, 0.9F * f));
        beam(vc, c.a, c.b, 1.5F * f, argb(1F, 0.3F, 0.1F, 0.4F * f));
        flare(vc, c.b, 2.2F * f, argb(1F, 0.5F, 0.25F, 0.6F * f));
    }

    /** A statue's eyes flash red toward someone. */
    private static void glare(VertexConsumer vc, RitualClient.Cue c, float age) {
        if (age > c.ticks) return;
        Entity e = Minecraft.getInstance().level.getEntity(c.id);
        if (!(e instanceof RitualStatue s)) return;
        float f = Math.min(1, age / 3F) * (1 - Math.max(0, (age - c.ticks + 10) / 10F)) * c.f;
        Vec3 head = RitualAnchors.statueEyes(s);
        if (head == null) {
            float yaw = Mth.lerp(pt, s.yHeadRotO, s.yHeadRot) * Mth.DEG_TO_RAD;
            head = s.getPosition(pt).add(-Mth.sin(yaw) * 0.35, s.stage() == RitualStatue.KNEEL ? 2.3 : 3.3, Mth.cos(yaw) * 0.35);
        }
        flare(vc, head, 0.35F, argb(1F, 1F, 0.55F, 0.95F * f));
        flare(vc, head, 1.3F, argb(1F, 0.3F, 0.1F, 0.45F * f));
    }

    private static void impact(VertexConsumer vc, RitualClient.Cue c, float age) {
        if (age > 10) return;
        float f = 1 - age / 10F;
        annulus(vc, c.a, Math.max(0, age * 0.35 - 0.3), age * 0.35 + 0.15, 0, Math.PI * 2, 0.05, argb(1F, 0.6F, 0.3F, 0.6F * f));
        flare(vc, c.a.add(0, 0.3, 0), 2.0F * f, argb(1F, 0.6F, 0.3F, 0.5F * f));
    }

    /** The end: the palm gathers the hall's light until everything is white. */
    private static void palmLight(VertexConsumer vc) {
        float age = RitualClient.beatAge(pt);
        if (age < 30) return;
        float grow = Mth.clamp((age - 30) / 190F, 0, 1);
        Vec3 p = towardCamera(palm(pt), 1.4);
        flare(vc, p, 0.6F + grow * 2.2F, argb(1F, 1F, 0.96F, 0.95F));
        flare(vc, p, 2F + grow * 9F, argb(1F, 0.9F, 0.6F, 0.35F + 0.3F * grow));
        flare(vc, p, 6F + grow * 22F, argb(1F, 0.8F, 0.5F, 0.08F + 0.14F * grow));
        // rays
        float t = (RitualClient.ticks + pt) * 0.02F;
        for (int i = 0; i < 9; i++) {
            float a = t + i * Mth.TWO_PI / 9;
            Vec3 dir = new Vec3(Mth.cos(a), Mth.sin(a * 1.3F) * 0.6F, Mth.sin(a));
            beam(vc, p, p.add(dir.scale(4 + grow * 16)), 0.3F + grow * 0.8F, argb(1F, 0.95F, 0.78F, 0.35F + 0.4F * grow));
        }
    }

    private static void motes(VertexConsumer vc) {
        for (int i = 0; i < MOTES; i++) {
            if (life[i] <= 0) continue;
            float x = (float) (Mth.lerp(pt, px[i], mx[i]) + originX), y = Mth.lerp(pt, py[i], my[i]), z = (float) (Mth.lerp(pt, pz[i], mz[i]) + originZ);
            float t = life[i] / (float) maxLife[i];
            float fade = Math.min(1, t * 4) * Math.min(1, (1 - t) * 6 + 0.2F);
            Vec3 p = new Vec3(x, y, z);
            switch (kind[i]) {
                case DUST -> flare(vc, p, 0.09F, argb(1F, 0.85F, 0.7F, 0.35F * fade));
                case DRAIN -> {
                    flare(vc, p, 0.16F, argb(1F, 0.97F, 0.82F, 0.9F * fade));
                    flare(vc, p, 0.55F, argb(1F, 0.8F, 0.45F, 0.25F * fade));
                }
                case VOID -> {
                    flare(vc, p, 0.07F, argb(0.65F, 0.85F, 1F, 0.9F * fade));
                    flare(vc, p, 0.3F, argb(0.3F, 0.5F, 1F, 0.22F * fade));
                }
                case EMBER -> flare(vc, p, 0.08F, argb(1F, 0.8F, 0.4F, 0.8F * fade));
                default -> {}
            }
        }
    }

    // ---- primitives (camera-relative; additive, so alpha is how much light a quad adds) ------------------------------

    private static int argb(float r, float g, float b, float a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (Mth.clamp(r, 0, 1) * 255) << 16) | ((int) (Mth.clamp(g, 0, 1) * 255) << 8)
                | (int) (Mth.clamp(b, 0, 1) * 255);
    }

    private static int alpha0(int argb) {
        return argb & 0x00FFFFFF;
    }

    // every quad is sent with both windings: floor decals, ribbons and fans read from above, below and behind
    private static final float[] QX = new float[4], QY = new float[4], QZ = new float[4];
    private static final int[] QC = new int[4];
    private static int qn;

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

    /** A soft round glow facing the camera: bright in the middle, nothing at the rim (a fan of quads). */
    private static void flare(VertexConsumer vc, Vec3 p, float r, int argb) {
        if (r <= 0.001F || (argb >>> 24) == 0) return;
        int rim = alpha0(argb);
        int n = r < 0.4F ? 6 : 12; // motes are small: six sides read as round
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            v(vc, p.x, p.y, p.z, argb);
            v(vc, p.x, p.y, p.z, argb);
            v(vc, p.x + RIGHT.x * c1 + UP.x * s1, p.y + RIGHT.y * c1 + UP.y * s1, p.z + RIGHT.z * c1 + UP.z * s1, rim);
            v(vc, p.x + RIGHT.x * c0 + UP.x * s0, p.y + RIGHT.y * c0 + UP.y * s0, p.z + RIGHT.z * c0 + UP.z * s0, rim);
        }
    }

    /** A thin horizontal streak through a point, facing the camera (the eyes' anamorphic glint). */
    private static void streak(VertexConsumer vc, Vec3 p, float len, float h, int argb) {
        int rim = alpha0(argb);
        for (int s = -1; s <= 1; s += 2) {
            double ex = RIGHT.x * len * s, ey = RIGHT.y * len * s, ez = RIGHT.z * len * s;
            v(vc, p.x + UP.x * h, p.y + UP.y * h, p.z + UP.z * h, argb);
            v(vc, p.x - UP.x * h, p.y - UP.y * h, p.z - UP.z * h, argb);
            v(vc, p.x + ex, p.y + ey, p.z + ez, rim);
            v(vc, p.x + ex, p.y + ey, p.z + ez, rim);
        }
    }

    /** A beam between two points, a ribbon turned to the camera, soft at its edges. */
    private static void beam(VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb) {
        if (width <= 0.001F || (argb >>> 24) == 0) return;
        Vec3 d = b.subtract(a);
        Vec3 view = a.add(d.scale(0.5)).subtract(cx, cy, cz);
        Vec3 n = d.cross(view);
        double len = n.length();
        if (len < 1e-6) return;
        n = n.scale(width * 0.5 / len);
        int rim = alpha0(argb);
        v(vc, a.x, a.y, a.z, argb);
        v(vc, b.x, b.y, b.z, argb);
        v(vc, b.x + n.x, b.y + n.y, b.z + n.z, rim);
        v(vc, a.x + n.x, a.y + n.y, a.z + n.z, rim);
        v(vc, a.x - n.x, a.y - n.y, a.z - n.z, rim);
        v(vc, b.x - n.x, b.y - n.y, b.z - n.z, rim);
        v(vc, b.x, b.y, b.z, argb);
        v(vc, a.x, a.y, a.z, argb);
    }

    private static Vec3 side(Vec3 a, Vec3 b) {
        Vec3 d = new Vec3(b.x - a.x, 0, b.z - a.z);
        double len = d.length();
        return len < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(-d.z / len, 0, d.x / len);
    }

    /** A strip on the floor from a (t0) to b (t1) along the line, half-width h, soft at the sides. */
    private static void groundStrip(VertexConsumer vc, Vec3 a, Vec3 b, float h, float t0, float t1, int argb) {
        if (t1 <= t0) return;
        Vec3 n = side(a, b).scale(h);
        Vec3 p0 = a.add(b.subtract(a).scale(t0)).add(0, 0.03, 0), p1 = a.add(b.subtract(a).scale(t1)).add(0, 0.03, 0);
        int rim = alpha0(argb);
        v(vc, p0.x, p0.y, p0.z, argb);
        v(vc, p1.x, p1.y, p1.z, argb);
        v(vc, p1.x + n.x, p1.y, p1.z + n.z, rim);
        v(vc, p0.x + n.x, p0.y, p0.z + n.z, rim);
        v(vc, p0.x - n.x, p0.y, p0.z - n.z, rim);
        v(vc, p1.x - n.x, p1.y, p1.z - n.z, rim);
        v(vc, p1.x, p1.y, p1.z, argb);
        v(vc, p0.x, p0.y, p0.z, argb);
    }

    /** A filled sector on the floor (the statue's swing), brighter at the rim. */
    private static void sector(VertexConsumer vc, Vec3 o, double yaw, double half, double r, float rimBoost, int argb) {
        int n = 14;
        int centre = alpha0(argb) | (((argb >>> 24) / 3) << 24);
        for (int i = 0; i < n; i++) {
            double a0 = yaw - half + 2 * half * i / n, a1 = yaw - half + 2 * half * (i + 1) / n;
            v(vc, o.x, o.y + 0.04, o.z, centre);
            v(vc, o.x, o.y + 0.04, o.z, centre);
            v(vc, o.x + Math.cos(a1) * r, o.y + 0.04, o.z + Math.sin(a1) * r, argb);
            v(vc, o.x + Math.cos(a0) * r, o.y + 0.04, o.z + Math.sin(a0) * r, argb);
        }
    }

    private static void sectorEdge(VertexConsumer vc, Vec3 o, double yaw, double half, double r, int argb) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = yaw - half + 2 * half * i / n, a1 = yaw - half + 2 * half * (i + 1) / n;
            v(vc, o.x + Math.cos(a0) * (r - 0.08), o.y + 0.05, o.z + Math.sin(a0) * (r - 0.08), argb);
            v(vc, o.x + Math.cos(a1) * (r - 0.08), o.y + 0.05, o.z + Math.sin(a1) * (r - 0.08), argb);
            v(vc, o.x + Math.cos(a1) * (r + 0.08), o.y + 0.05, o.z + Math.sin(a1) * (r + 0.08), argb);
            v(vc, o.x + Math.cos(a0) * (r + 0.08), o.y + 0.05, o.z + Math.sin(a0) * (r + 0.08), argb);
        }
    }

    /**
     * A ring (or part of one) on the floor round o, at height dy above it. Angles in the rune ring's convention: 0
     * points at the doors (-z), increasing clockwise seen from above.
     */
    private static void annulus(VertexConsumer vc, Vec3 o, double r0, double r1, double a0, double a1, double dy, int argb) {
        int n = Math.max(4, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 24)));
        for (int i = 0; i < n; i++) {
            double t0 = a0 + (a1 - a0) * i / n, t1 = a0 + (a1 - a0) * (i + 1) / n;
            double s0 = Math.sin(t0), c0 = -Math.cos(t0), s1 = Math.sin(t1), c1 = -Math.cos(t1);
            v(vc, o.x + s0 * r0, o.y + dy, o.z + c0 * r0, argb);
            v(vc, o.x + s1 * r0, o.y + dy, o.z + c1 * r0, argb);
            v(vc, o.x + s1 * r1, o.y + dy, o.z + c1 * r1, argb);
            v(vc, o.x + s0 * r1, o.y + dy, o.z + c0 * r1, argb);
        }
    }

    // ---- the words --------------------------------------------------------------------------------------------------

    static final String[] WORDS = {"BOW.", "RING.", "BE JUDGED."};

    /** The rule carved in light over the dais, a letter at a time, its newest letter blazing; then it burns away. */
    private static void words(PoseStack ps, SubmitNodeCollector out, Minecraft mc) {
        RitualClient.Cue word = null;
        for (RitualClient.Cue c : RitualClient.CUES) if (c.live && c.kind == RitualClient.WORD && (word == null || c.start > word.start)) word = c;
        if (word == null || word.id < 0 || word.id >= WORDS.length) return;
        float age = word.age(pt);
        if (age > word.ticks + 20) return;
        String text = WORDS[word.id];
        int shown = Math.min(text.length(), (int) (age / 3.5F) + 1);
        float alpha = age < word.ticks ? 1 : 1 - (age - word.ticks) / 20F;
        Font font = mc.font;
        float scale = word.id == 2 ? 0.42F : 0.36F;
        int full = font.width(text);
        String part = text.substring(0, shown);
        ps.pushPose();
        Vec3 w = RitualClient.words;
        ps.translate(w.x - cx, w.y - cy, w.z - cz);
        // the text faces the doors (north) and stands upright
        ps.rotate(Axis.YP.rotationDegrees(180));
        ps.scale(scale, -scale, scale);
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        int gold = (a << 24) | 0xFFE7A8, glow = ((a / 4) << 24) | 0xFFB050;
        float x0 = -full / 2F;
        out.submitText(ps, x0, -4, Component.literal(part).getVisualOrderText(), false, Font.DisplayMode.NORMAL,
                LightCoordsUtil.FULL_BRIGHT, gold, 0, glow);
        ps.popPose();
        // the newest letter's blaze, and a glow under the whole word
        if (shown <= text.length() && age < shown * 3.5F + 4) {
            float lx = x0 + font.width(part) - 3;
            Vec3 at = w.add(-lx * scale, 0.2, -0.2);
            // drawn in the additive pass next frame order is fine: a flare at the cursor
            cursor = at;
            cursorLife = 4;
        }
    }

    private static Vec3 cursor = Vec3.ZERO;
    private static int cursorLife;
}
