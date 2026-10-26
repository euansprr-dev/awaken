package dev.awaken.terrain;

import java.nio.ByteBuffer;

/**
 * The waving shader's inputs and its sway, in Java (SPEC 15). The client writes one {@link Frame} a frame into the
 * block atlas's corner ({@code terrain.client.WindTexels}, through {@link #encode}); Awaken's terrain vertex shader
 * ({@code resourcepacks/awaken_waving_leaves/.../block_layer_opaque.vsh}) decodes it exactly as {@link #decode} does and
 * computes {@link #leafOffset} and {@link #plantOffset}. These are a line-by-line mirror of the shader, kept for the
 * GameTests (continuity across every wrap) and for the client's probe, which logs what the GPU computes each frame.
 * Change one, change the other.
 *
 * <p>Continuity by construction: every sine takes a phase in turns reduced with {@code fract} first (a GPU's sine is
 * imprecise far from zero); time phases are whole turns in 2048 s, the period of the clock; spatial phases are whole
 * turns in 4096 blocks, the period of the world positions the shader reconstructs (the camera is written modulo 4096,
 * so it wraps at x = 0 and z = 0, right by spawn); wind direction, steady strength and gusts are 16-bit; the gust
 * profile is sampled on a grid fixed in the world, so walking never shifts it.
 */
public final class SwayMath {
    private SwayMath() {}

    public static final int TEXELS = 45;
    public static final int PROFILE = 64;
    public static final float PROFILE_STEP = 4F;
    public static final int SIGNATURE = 0x444E5741; // "AWND" little-endian
    // texel layout
    public static final int T_TIME = 1, T_CAM = 2, T_DIR = 5, T_STEADY = 6, T_PROFILE_OFF = 7, T_GUST = 8, T_PLANT = 40,
            T_FEET = 41, T_PUSH = 44;
    public static final float TAU = (float) (2 * Math.PI);
    /** Plants sway out to FAR blocks from the camera, fading from FADE. */
    public static final float PLANT_FADE = 72F, PLANT_FAR = 112F;
    static final float REGION_XZ = 128F, REGION_Y = 64F;

    /** One frame of inputs. {@code t}: sway clock in seconds modulo 2048. Camera and feet x, z modulo 4096. */
    public static final class Frame {
        public boolean signature = true;
        public float t;
        public float camX, camY, camZ;
        public float dirX = 1F, dirZ, steady, leafAmp;
        /** Where the gust grid sits along the wind: sample i lies (i - 31.5) x 4 + profileOff blocks downwind of the camera. */
        public float profileOff;
        public final float[] gust = new float[PROFILE];
        public float air, water, sheen, weather = 1F;
        public float feetX, feetY = -1.0E6F, feetZ, push;
    }

    // ---- the corner texels -----------------------------------------------------------------------------------------

    /** Writes a frame as the shader reads it: TEXELS x 4 bytes, little-endian. */
    public static void encode(Frame f, ByteBuffer b) {
        b.putInt(f.signature ? SIGNATURE : 0);
        b.putFloat(f.t);
        b.putFloat(f.camX).putFloat(f.camY).putFloat(f.camZ);
        b.putShort(u16(f.dirX * 0.5F + 0.5F)).putShort(u16(f.dirZ * 0.5F + 0.5F));
        b.putShort(u16(f.steady)).put(u8(f.leafAmp / 0.25F)).put((byte) 0);
        b.putFloat(f.profileOff);
        for (int i = 0; i < PROFILE; i++) b.putShort(u16(f.gust[i]));
        b.put(u8(f.air / 0.5F)).put(u8(f.water / 0.5F)).put(u8(f.sheen / 0.5F)).put(u8(f.weather));
        b.putFloat(f.feetX).putFloat(f.feetY).putFloat(f.feetZ);
        b.put(u8(f.push)).put((byte) 0).put((byte) 0).put((byte) 0);
    }

    /** Reads a frame back from TEXELS x 4 bytes at the buffer's start, quantised exactly as the shader sees it. */
    public static Frame decode(ByteBuffer b, Frame f) {
        f.signature = b.getInt(0) == SIGNATURE;
        f.t = b.getFloat(T_TIME * 4);
        f.camX = b.getFloat(T_CAM * 4);
        f.camY = b.getFloat(T_CAM * 4 + 4);
        f.camZ = b.getFloat(T_CAM * 4 + 8);
        f.dirX = un16(b, T_DIR * 4) * 2F - 1F;
        f.dirZ = un16(b, T_DIR * 4 + 2) * 2F - 1F;
        f.steady = un16(b, T_STEADY * 4);
        f.leafAmp = un8(b, T_STEADY * 4 + 2) * 0.25F;
        f.profileOff = b.getFloat(T_PROFILE_OFF * 4);
        for (int i = 0; i < PROFILE; i++) f.gust[i] = un16(b, T_GUST * 4 + i * 2);
        f.air = un8(b, T_PLANT * 4) * 0.5F;
        f.water = un8(b, T_PLANT * 4 + 1) * 0.5F;
        f.sheen = un8(b, T_PLANT * 4 + 2) * 0.5F;
        f.weather = un8(b, T_PLANT * 4 + 3);
        f.feetX = b.getFloat(T_FEET * 4);
        f.feetY = b.getFloat(T_FEET * 4 + 4);
        f.feetZ = b.getFloat(T_FEET * 4 + 8);
        f.push = un8(b, T_PUSH * 4);
        return f;
    }

    /** The grid offset that keeps gust samples at world-fixed places along the wind (multiples of 4 blocks). */
    public static float profileOffset(double camX, double camZ, float dirX, float dirZ) {
        double along = camX * dirX + camZ * dirZ;
        return (float) -floorMod(along - 31.5 * PROFILE_STEP, PROFILE_STEP);
    }

    public static float sampleAlong(int i, float profileOff) {
        return (i - (PROFILE - 1) * 0.5F) * PROFILE_STEP + profileOff;
    }

    public static double wrap4096(double v) {
        return v - Math.floor(v / 4096.0) * 4096.0;
    }

    // ---- the sway (mirrors the shader) -----------------------------------------------------------------------------

    /** sin of a phase in turns, reduced first. */
    static float sinTurns(float turns) {
        return (float) Math.sin(TAU * fract(turns));
    }

    /** A time phase: k whole turns in 2048 s. */
    static float timeTurns(float t, float k) {
        return t * (k / 2048F);
    }

    /** A phase fixed in the world: (nx, nz) whole turns in 4096 blocks. */
    static float worldTurns(float x, float z, float nx, float nz) {
        return (x * nx + z * nz) * (1F / 4096F);
    }

    /** The gust the shader reads at a place (x, z modulo 4096), from the frame's profile. */
    public static float gustAt(Frame f, float x, float z) {
        float gi = Math.clamp(((x - f.camX) * f.dirX + (z - f.camZ) * f.dirZ - f.profileOff) * 0.25F + 31.5F, 0F, 63F);
        int i0 = (int) gi;
        float a = f.gust[i0], b = f.gust[Math.min(i0 + 1, 63)];
        return a + (b - a) * (gi - i0);
    }

    static float[] dir(Frame f) {
        float len = (float) Math.sqrt(Math.max(f.dirX * f.dirX + f.dirZ * f.dirZ, 1e-4F));
        return new float[] {f.dirX / len, f.dirZ / len};
    }

    /** A leaf vertex at world position (x, z modulo 4096) moves by out[0..2]. */
    public static void leafOffset(Frame f, float x, float y, float z, float[] out) {
        out[0] = out[1] = out[2] = 0F;
        if (!f.signature) return;
        float[] d = dir(f);
        float gust = gustAt(f, x, z);
        float push = Math.min(1F, f.steady * (0.6F + 0.8F * gust) + 0.25F * gust);
        push *= 0.4F + 0.6F * gust;
        float sway = sinTurns(timeTurns(f.t, 551F) + worldTurns(x, z, 150F, 202F) + y * (0.21F / TAU));
        float cross = sinTurns(timeTurns(f.t, 945F) + worldTurns(x, z, 372F, -274F) + y * (0.37F / TAU));
        int h = hash(Math.floorMod((int) Math.floor(x * 16F + 0.5F), 65536), (int) Math.floor(y * 16F + 0.5F) + 1048576,
                Math.floorMod((int) Math.floor(z * 16F + 0.5F), 65536));
        float flutter = sinTurns(timeTurns(f.t, 2376F) + (h & 1023) / 1024F);
        float sx = -d[1], sz = d[0];
        float k = f.leafAmp * push;
        out[0] = (d[0] * (0.5F + 0.5F * sway + 0.2F * flutter) + sx * (0.35F * cross)) * k;
        out[2] = (d[1] * (0.5F + 0.5F * sway + 0.2F * flutter) + sz * (0.35F * cross)) * k;
        out[1] = (0.3F * cross * sway + 0.25F * flutter) * k;
    }

    /**
     * A plant vertex with marker code {@code code} (PlantSway: mode x 8 + level - 1, or 40 for a pad) at world position
     * (x, z modulo 4096), {@code dist} blocks from the camera, moves by out[0..2]; out[3] is its tint (above 1 in a gust).
     */
    public static void plantOffset(Frame f, int code, float x, float y, float z, float dist, float[] out) {
        out[0] = out[1] = out[2] = 0F;
        out[3] = 1F;
        if (dist > PLANT_FAR || !f.signature) return;
        float near = 1F - smoothstep(PLANT_FADE, PLANT_FAR, dist);
        if (f.air == 0F && f.water == 0F) return;
        if (code == PlantSway.FLOAT_CODE) {
            float bob = sinTurns(timeTurns(f.t, 717F) + worldTurns(x, z, 847F, 587F)) + 0.4F * sinTurns(timeTurns(f.t, 1311F) + worldTurns(x, z, -456F, 717F));
            out[1] = bob * (0.006F + 0.01F * f.steady) * near;
            return;
        }
        int mode = code >> 3;
        float weight = ((code & 7) + 1) * 0.25F;
        if (mode == PlantSway.MODE_WATER) {
            float a = 1.1F * sinTurns(worldTurns(x, z, 14F, 11F)) + 0.7F * sinTurns(worldTurns(x, z, -8F, 19F));
            float ax = (float) Math.cos(a), az = (float) Math.sin(a);
            float ph = worldTurns(x, z, 202F, 150F) - y * (0.45F / TAU);
            float ph2 = worldTurns(x, z, 323F, 240F) - y * (0.72F / TAU) + 1.3F / TAU;
            float surge = sinTurns(timeTurns(f.t, 410F) + ph);
            float roll = sinTurns(timeTurns(f.t, 717F) + ph2);
            float k = f.water * weight;
            out[0] = (ax * (0.8F * surge + 0.2F * roll) - az * (0.3F * roll)) * k;
            out[2] = (az * (0.8F * surge + 0.2F * roll) + ax * (0.3F * roll)) * k;
            out[1] = -0.5F * (out[0] * out[0] + out[2] * out[2]) / Math.max(weight, 0.5F);
            for (int i = 0; i < 3; i++) out[i] *= near;
            return;
        }
        float[] d = dir(f);
        float expo = smoothstep(30F, 50F, y) * f.weather;
        float gust = gustAt(f, x, z) * expo;
        float s = lerp(0.12F, Math.min(1F, f.steady * (0.6F + 0.8F * gust) + 0.25F * gust), expo);
        float warp = (1.7F / TAU) * sinTurns(worldTurns(x, z, -189F, 267F));
        boolean hanging = mode != PlantSway.MODE_ROOTED;
        float yt = hanging ? y * (0.7F / TAU) : 0F;
        float ph1 = worldTurns(x, z, 541F, 306F) + warp + yt;
        float ph2 = worldTurns(x, z, 741F, 420F) + 1.37F * (warp + yt) + 2.1F / TAU;
        float phc = worldTurns(x, z, -128F, 655F) + 0.5F * (warp + yt);
        float sway = 0.72F * sinTurns(timeTurns(f.t, hanging ? 922F : 1536F) + ph1) + 0.28F * sinTurns(timeTurns(f.t, hanging ? 1597F : 2662F) + ph2);
        float cross = sinTurns(timeTurns(f.t, hanging ? 700F : 1167F) + phc);
        float lean = s * (0.35F + 0.65F * s);
        float swing = (0.3F + 0.5F * s) * (0.4F + 0.6F * expo);
        float k = f.air * weight;
        out[0] = (d[0] * (lean + swing * sway) - d[1] * (0.4F * swing * cross)) * k;
        out[2] = (d[1] * (lean + swing * sway) + d[0] * (0.4F * swing * cross)) * k;
        if (mode == PlantSway.MODE_ROOTED) {
            if (dist < 10F) {
                float dx = x - f.feetX, dy = y - f.feetY, dz = z - f.feetZ;
                dx -= Math.round(dx / 4096F) * 4096F;
                dz -= Math.round(dz / 4096F) * 4096F;
                float r = (float) Math.sqrt(dx * dx + dz * dz);
                if (r < 1.3F && dy > -0.5F && dy < 2.6F) {
                    float kk = 1F - r / 1.3F;
                    kk *= kk;
                    float w = Math.min(weight, 1F);
                    out[0] += dx / Math.max(r, 0.05F) * (kk * f.push * w);
                    out[2] += dz / Math.max(r, 0.05F) * (kk * f.push * w);
                    out[1] -= kk * f.push * 0.35F * w;
                }
            }
            out[3] = 1F + f.sheen * 0.5F * gust * Math.min(weight, 1F) * near;
        }
        out[1] += (hanging ? 0.5F : -0.5F) * (out[0] * out[0] + out[2] * out[2]) / Math.max(weight, 0.5F);
        if (mode == PlantSway.MODE_WALL_X) out[0] = 0F;
        if (mode == PlantSway.MODE_WALL_Z) out[2] = 0F;
        for (int i = 0; i < 3; i++) out[i] *= near;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    static int hash(int x, int y, int z) {
        // the shader's uvec3 hash, in 32-bit unsigned arithmetic
        int vx = x * 1664525 + 1013904223, vy = y * 1664525 + 1013904223, vz = z * 1664525 + 1013904223;
        vx += vy * vz; vy += vz * vx; vz += vx * vy;
        vx ^= vx >>> 16; vy ^= vy >>> 16; vz ^= vz >>> 16;
        vx += vy * vz; vy += vz * vx; vz += vx * vy;
        return vx ^ vy ^ vz;
    }

    static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    static double floorMod(double v, double m) {
        return v - Math.floor(v / m) * m;
    }

    static float smoothstep(float a, float b, float v) {
        float t = Math.clamp((v - a) / (b - a), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static short u16(float v) {
        return (short) Math.round(Math.clamp(v, 0F, 1F) * 65535F);
    }

    static byte u8(float v) {
        return (byte) Math.round(Math.clamp(v, 0F, 1F) * 255F);
    }

    static float un16(ByteBuffer b, int at) {
        return (b.getShort(at) & 0xFFFF) / 65535F;
    }

    static float un8(ByteBuffer b, int at) {
        return (b.get(at) & 0xFF) / 255F;
    }
}
