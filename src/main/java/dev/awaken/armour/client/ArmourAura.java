package dev.awaken.armour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourAbilities;
import dev.awaken.armour.ArmourSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Mastery V's aura (SPEC §44.5, "Mastery"): small glowing motes moving about the wearer in the set's own way, drawn in the
 * model's space (y down from the neck, the front toward -z) where the armour is drawn, so stands and others show it too.
 * Worked out from the time alone: nothing is kept per wearer and nothing is spawned.
 */
final class ArmourAura {
    private ArmourAura() {}

    /** How the motes move: rising off the shoulders, falling from the face, circling the head, hanging about the body, rings
     * from the chest with the heartbeat, glints that wink on the plate, streaming back as if in wind. */
    enum Kind { RISE, FALL, CROWN, DRIFT, PULSE, GLINT, STREAM }

    record Style(Kind kind, int count, float size, float period) {}

    private static final Identifier MOTE = Awaken.id("textures/entity/armour/mote.png");

    static Style style(ArmourSet set) {
        return switch (set) {
            case WILDHIDE -> new Style(Kind.DRIFT, 10, 0.022F, 90);
            case BEASTBOUND -> new Style(Kind.RISE, 12, 0.026F, 70);
            case GATEWROUGHT -> new Style(Kind.CROWN, 5, 0.032F, 120);
            case VEILWOVEN -> new Style(Kind.DRIFT, 14, 0.03F, 140);
            case LORDSCALE -> new Style(Kind.RISE, 16, 0.028F, 60);
            case SOVEREIGN -> new Style(Kind.CROWN, 7, 0.026F, 160);
            case BELLGUARD -> new Style(Kind.PULSE, 12, 0.022F, 50);
            case ROOTWARDEN -> new Style(Kind.FALL, 10, 0.026F, 110);
            case AMBERWING -> new Style(Kind.GLINT, 10, 0.03F, 40);
            case CLEFTRUNNER -> new Style(Kind.STREAM, 12, 0.022F, 36);
            case LANTERNLIGHT -> new Style(Kind.RISE, 12, 0.022F, 80);
            case BLADEWARD -> new Style(Kind.GLINT, 8, 0.028F, 34);
            case HORNLORD -> new Style(Kind.RISE, 14, 0.03F, 56);
            case VAULTBREAKER -> new Style(Kind.GLINT, 12, 0.03F, 44);
            case FROSTWALKER -> new Style(Kind.FALL, 14, 0.024F, 70);
            case SUNWARD -> new Style(Kind.CROWN, 9, 0.028F, 100);
            case SHARDCROWN -> new Style(Kind.CROWN, 6, 0.036F, 130);
            case DEEPCARAPACE -> new Style(Kind.DRIFT, 14, 0.024F, 120);
            case CLOUDRUNNER -> new Style(Kind.STREAM, 14, 0.026F, 30);
            case TIDEBREAKER -> new Style(Kind.STREAM, 12, 0.024F, 44);
            case HOLLOWHEART -> new Style(Kind.PULSE, 14, 0.024F, 34);
            default -> new Style(Kind.DRIFT, 10, 0.024F, 100);
        };
    }

    /** Gatewrought's motes take the Five Forces' colours in turn. */
    private static final int[] FORCES = {0xFFFF9A3C, 0xFF9AD8FF, 0xFF9A6BFF, 0xFF86E0A0, 0xFFE6F6FF};

    /** The motes about one wearer, the pose on the model's root. */
    static void draw(SubmitNodeCollector out, PoseStack ps, ArmourSet set, float age) {
        Style st = style(set);
        int colour = ArmourAbilities.glow(set);
        out.submitCustomGeometry(ps, RenderTypes.eyes(MOTE), (pose, buffer) -> {
            for (int i = 0; i < st.count; i++) mote(buffer, pose, st, set == ArmourSet.GATEWROUGHT ? FORCES[i % 5] : colour, i, age);
        });
    }

    /** A steady hash of a mote's number and its turn, 0..1. */
    private static float hash(int i, int k) {
        int h = i * 73856093 ^ k * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    private static void mote(VertexConsumer b, PoseStack.Pose pose, Style st, int colour, int i, float age) {
        float t = age / st.period + i / (float) st.count;
        int turn = Mth.floor(t);
        float u = t - turn;
        float a1 = hash(i, turn), a2 = hash(i + 101, turn), a3 = hash(i + 211, turn);
        float x, y, z, size = st.size, alpha;
        switch (st.kind) {
            case RISE -> { // off the shoulders and back, up past the crown, shrinking
                float ang = a1 * Mth.TWO_PI;
                float r = 0.18F + 0.22F * a2 + 0.15F * u;
                x = Mth.cos(ang) * r + Mth.sin(age * 0.07F + i) * 0.04F;
                z = Mth.sin(ang) * r * 0.7F + 0.08F;
                y = 0.15F - u * 1.15F;
                size *= 1.2F - u;
                alpha = fade(u);
            }
            case FALL -> { // from the face and shoulders, drifting down and out
                float ang = a1 * Mth.TWO_PI;
                x = Mth.cos(ang) * (0.12F + 0.3F * u) * (0.6F + a2);
                z = -0.22F + Mth.sin(ang) * 0.1F - 0.25F * u * a3;
                y = -0.2F + u * 1.4F;
                size *= 1.1F - 0.5F * u;
                alpha = fade(u) * 0.9F;
            }
            case CROWN -> { // circling the head, bobbing
                float ang = age * Mth.TWO_PI / st.period + i * Mth.TWO_PI / st.count;
                x = Mth.cos(ang) * 0.42F;
                z = Mth.sin(ang) * 0.42F;
                y = -0.62F + Mth.sin(age * 0.09F + i * 1.7F) * 0.05F;
                alpha = 0.85F;
            }
            case DRIFT -> { // hanging about the body in a slow column
                float ang = a1 * Mth.TWO_PI + age * 0.01F;
                float r = 0.42F + 0.25F * a2;
                x = Mth.cos(ang) * r;
                z = Mth.sin(ang) * r;
                y = -0.5F + 1.9F * a3 - 0.25F * u;
                alpha = fade(u) * 0.8F;
            }
            case PULSE -> { // rings out from the chest, in pairs like a heartbeat
                float beat = (age % st.period) / st.period;
                float ring = beat < 0.5F ? beat * 2 : (beat - 0.25F) * 2;
                float ang = i * Mth.TWO_PI / st.count;
                float r = 0.2F + 0.9F * ring;
                x = Mth.cos(ang) * r;
                z = Mth.sin(ang) * r;
                y = 0.3F;
                alpha = 0.9F * (1 - Math.min(1F, ring));
            }
            case GLINT -> { // a wink of light at a point on the plate
                float ang = a1 * Mth.TWO_PI;
                x = Mth.cos(ang) * 0.3F;
                z = Mth.sin(ang) * 0.2F;
                y = -0.4F + 1.6F * a2;
                float pop = u < 0.15F ? u / 0.15F : u < 0.35F ? 1 - (u - 0.15F) / 0.2F : 0;
                size *= 0.4F + 1.2F * pop;
                alpha = pop;
            }
            default -> { // STREAM: back off the shoulders as if in wind
                float side = (a1 - 0.5F) * 0.7F;
                x = side + Mth.sin(age * 0.2F + i) * 0.03F;
                z = 0.15F + u * 1.1F;
                y = -0.1F + 0.9F * a2 + 0.2F * u;
                size *= 1.1F - 0.6F * u;
                alpha = fade(u) * 0.85F;
            }
        }
        if (alpha <= 0.02F || size <= 0) return;
        cube(b, pose, x, y, z, size, colour, alpha);
    }

    /** In over the first fifth, out over the last third. */
    private static float fade(float u) {
        return Math.min(1F, Math.min(u * 5F, (1F - u) * 3F));
    }

    private static void cube(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float s, int colour, float alpha) {
        int r = colour >> 16 & 255, g = colour >> 8 & 255, bl = colour & 255, a = (int) (alpha * 255);
        float x0 = x - s, x1 = x + s, y0 = y - s, y1 = y + s, z0 = z - s, z1 = z + s;
        quad(b, pose, r, g, bl, a, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(b, pose, r, g, bl, a, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(b, pose, r, g, bl, a, -1, 0, 0, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(b, pose, r, g, bl, a, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quad(b, pose, r, g, bl, a, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(b, pose, r, g, bl, a, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose pose, int r, int g, int bl, int a, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        corner(b, pose, r, g, bl, a, nx, ny, nz, ax, ay, az, 0, 0);
        corner(b, pose, r, g, bl, a, nx, ny, nz, bx, by, bz, 0, 1);
        corner(b, pose, r, g, bl, a, nx, ny, nz, cx, cy, cz, 1, 1);
        corner(b, pose, r, g, bl, a, nx, ny, nz, dx, dy, dz, 1, 0);
    }

    private static void corner(VertexConsumer b, PoseStack.Pose pose, int r, int g, int bl, int a, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        b.addVertex(pose, x, y, z).setColor(r, g, bl, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }
}
