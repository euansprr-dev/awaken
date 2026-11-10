package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.core.client.FirstPersonDress;
import dev.awaken.powers.fx.FxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A power's light on an arm that wears armour (SPEC §32.N, "The light on what you wear"; Euan, 10 Nov 2026, on a light
 * that showed through the plate: "custom styling for each armor piece for the different powers. They would actually map
 * to the shape of the armor itself and the arm, instead of sitting below it and looking glitchy").
 * <p>
 * The arm's piece is taken as it is drawn ({@link FirstPersonDress.Surface}) and cut into cells, one a texel of every
 * face. Each cell has a part to play, read from the set's own texture: cloth, plate, a <i>line</i> (a trim: the rim of a
 * bright plate, a cuff ring, a band) or something that glows of itself (a stone, a crystal, a seam). The light is then
 * drawn cell by cell on the faces themselves, a hair off them: the type's colour runs down the set's own lines, fills its
 * stones, and lays the type's motif over cloth and plate, one face at a time, as a tint that follows their own painting
 * (never a coat of paint over it). So every set wears each type its own way
 * with no art of its own, and a set's record ({@link #SETS}) says how its materials take it: how far the lines run ahead
 * of the rest (metal), how much the cloth holds a glow (hide and cloth), how what glows shimmers (crystal).
 * <p>
 * {@code tools/art/arm_light_preview.py} draws the same thing off line; {@link #light} and its {@code light} are kept
 * line for line.
 */
final class ArmourLight {
    private ArmourLight() {}

    /** The parts a cell can play. */
    static final int SOFT = 0, PLATE = 1, TRIM = 2, EMIT = 3;
    private static final int FRONT = 0, BACK = 1, OUTER = 2, INNER = 3, CAP = 4;
    /** Rows of the arm, shoulder end to fingertips, in texels (the glove ends half a texel past the skin's twelve). */
    static final float ROWS = 12.5f;
    /** How far off the face a cell is drawn, and its glow. */
    private static final float LIFT = 0.003f, GLOW_LIFT = 0.007f;

    /**
     * A set's own way with the light: how much its cloth, plate, lines and glowing parts each take; how many rows its
     * lines run ahead of the rest; how much its cloth holds a glow where the light has been; how much what glows of itself
     * shimmers.
     */
    record SetStyle(float soft, float plate, float trim, float emit, float lead, float soak, float shimmer) {
        float gain(int role) {
            return switch (role) {
                case SOFT -> soft;
                case PLATE -> plate;
                case TRIM -> trim;
                default -> emit;
            };
        }
    }

    private static final SetStyle DEFAULT = new SetStyle(0.8f, 0.85f, 1.0f, 1.15f, 1.0f, 0.0f, 0.0f);
    /** The sets whose arms are finished (the armour's C rank, 10 Nov 2026); the rest take {@link #DEFAULT} until theirs are. */
    private static final Map<String, SetStyle> SETS = Map.of(
            "gatewrought", new SetStyle(0.85f, 0.9f, 1.0f, 1.15f, 1.5f, 0.12f, 0.0f),  // a leather sleeve under a plated glove
            "vaultbreaker", new SetStyle(0.7f, 0.85f, 1.05f, 1.2f, 2.5f, 0.0f, 0.0f),  // steel: the cuff rings run well ahead
            "frostwalker", new SetStyle(0.9f, 0.85f, 1.0f, 1.2f, 0.5f, 0.2f, 0.0f),    // hide: it soaks the light up
            "sunward", new SetStyle(0.85f, 0.9f, 1.1f, 1.2f, 1.5f, 0.14f, 0.5f),       // cloth under gold bands and a stone
            "shardcrown", new SetStyle(0.7f, 0.8f, 1.0f, 1.3f, 1.0f, 0.0f, 1.0f));     // crystal fills and shimmers

    /** One arm piece, cut up. Cells of a face lie together: {@code faceStart[f]} to {@code faceStart[f + 1]}. */
    static final class Cells {
        int n, faces;
        float[] pos;        // twelve a cell: its four corners
        float[] v;          // how far down the arm, in texels from the shoulder end
        short[] face;       // which flat face
        byte[] ul, cols, role, side;
        byte[] lum;         // how bright its own painting is there, 0 to 255
        float[] normal;     // three a face
        float[] centre;     // three a face
        int[] faceStart;
        SetStyle style = DEFAULT;
    }

    private static final IdentityHashMap<Object, Cells> BUILT = new IdentityHashMap<>();
    private static boolean warned;

    /** The cells of what this limb wears, worked out once a shape; null if it could not be read. */
    static @Nullable Cells of(FirstPersonDress.Surface surface) {
        Object key = surface.key();
        if (BUILT.containsKey(key)) return BUILT.get(key);
        if (BUILT.size() > 64) BUILT.clear(); // shapes are made again when resources reload: let the old ones go
        Cells cells = null;
        try {
            cells = build(surface);
        } catch (Exception e) {
            if (!warned) Awaken.LOG.error("[powers] the light could not read what an arm wears ({}); it is drawn the old way", surface.style(), e);
            warned = true;
        }
        BUILT.put(key, cells);
        return cells;
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static boolean same(float[] p, int a, int b) {
        return p[a * 3] == p[b * 3] && p[a * 3 + 1] == p[b * 3 + 1] && p[a * 3 + 2] == p[b * 3 + 2];
    }

    private static Cells build(FirstPersonDress.Surface surface) throws java.io.IOException {
        float[] p = surface.positions(), uv = surface.texels();
        byte[] nr = surface.normals();
        int faces = p.length / 18;
        Cells out = new Cells();
        out.style = SETS.getOrDefault(surface.style(), DEFAULT);
        var resource = Minecraft.getInstance().getResourceManager().getResource(surface.texture());
        if (resource.isEmpty()) throw new java.io.IOException("no texture " + surface.texture());
        try (InputStream in = resource.get().open(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            // first, how many cells there could be
            int[] c0 = new int[faces], c1 = new int[faces], c3 = new int[faces], n1 = new int[faces], n2 = new int[faces];
            int most = 0, widest = 0;
            for (int f = 0; f < faces; f++) {
                int a = f * 6;
                // the corner of the second triangle that the first hasn't got, and the first's corner the second hasn't
                int d = -1, lone = 0;
                for (int k = 3; k < 6; k++) if (!same(p, a + k, a) && !same(p, a + k, a + 1) && !same(p, a + k, a + 2)) d = a + k;
                for (int k = 0; k < 3; k++) if (!same(p, a + k, a + 3) && !same(p, a + k, a + 4) && !same(p, a + k, a + 5)) {
                    lone = k;
                    break;
                }
                if (d < 0) {
                    n1[f] = 0;
                    continue;
                }
                c0[f] = a + lone;
                c1[f] = a + (lone + 1) % 3;
                c3[f] = a + (lone + 2) % 3;
                n1[f] = Math.max(1, Math.round(16f * dist(p, c0[f], c1[f])));
                n2[f] = Math.max(1, Math.round(16f * dist(p, c0[f], c3[f])));
                most += n1[f] * n2[f];
                widest = Math.max(widest, n1[f] * n2[f]);
            }
            out.pos = new float[most * 12];
            out.v = new float[most];
            out.face = new short[most];
            out.ul = new byte[most];
            out.cols = new byte[most];
            out.role = new byte[most];
            out.side = new byte[most];
            out.lum = new byte[most];
            out.normal = new float[faces * 3];
            out.centre = new float[faces * 3];
            out.faceStart = new int[faces + 1];
            out.faces = faces;
            int[] grid = new int[widest];
            int n = 0;
            for (int f = 0; f < faces; f++) {
                out.faceStart[f] = n;
                if (n1[f] == 0) continue;
                int a = c0[f], b = c1[f], c = c3[f];
                float ax = p[a * 3], ay = p[a * 3 + 1], az = p[a * 3 + 2];
                float e1x = p[b * 3] - ax, e1y = p[b * 3 + 1] - ay, e1z = p[b * 3 + 2] - az;
                float e2x = p[c * 3] - ax, e2y = p[c * 3 + 1] - ay, e2z = p[c * 3 + 2] - az;
                float nx = nr[a * 3] / 127f, ny = nr[a * 3 + 1] / 127f, nz = nr[a * 3 + 2] / 127f;
                out.normal[f * 3] = nx;
                out.normal[f * 3 + 1] = ny;
                out.normal[f * 3 + 2] = nz;
                out.centre[f * 3] = ax + (e1x + e2x) * 0.5f;
                out.centre[f * 3 + 1] = ay + (e1y + e2y) * 0.5f;
                out.centre[f * 3 + 2] = az + (e1z + e2z) * 0.5f;
                // which edge runs along the arm (the bone's y), and so which counts across it
                boolean firstAlong = Math.abs(e1y) >= Math.abs(e2y);
                float anx = Math.abs(nx), any = Math.abs(ny), anz = Math.abs(nz);
                int side = anx >= any && anx >= anz ? (nx < 0 ? OUTER : INNER) : any >= anz ? (ny > 0 ? CAP : FRONT) : (nz < 0 ? FRONT : BACK);
                int w1 = n1[f], w2 = n2[f];
                for (int i = 0; i < w1; i++) {
                    for (int j = 0; j < w2; j++) {
                        grid[i * w2 + j] = -1;
                        float s0 = i / (float) w1, s1 = (i + 1) / (float) w1, t0 = j / (float) w2, t1 = (j + 1) / (float) w2;
                        float sm = (s0 + s1) * 0.5f, tm = (t0 + t1) * 0.5f;
                        float u = uv[a * 2] + (uv[b * 2] - uv[a * 2]) * sm + (uv[c * 2] - uv[a * 2]) * tm;
                        float vv = uv[a * 2 + 1] + (uv[b * 2 + 1] - uv[a * 2 + 1]) * sm + (uv[c * 2 + 1] - uv[a * 2 + 1]) * tm;
                        int tx = Math.min(w / 2 - 1, Math.max(0, (int) (u * w))), ty = Math.min(h - 1, Math.max(0, (int) (vv * h)));
                        int alb = img.getPixel(tx, ty), mat = img.getPixel(tx + w / 2, ty);
                        if ((alb >>> 24) < 26) continue;
                        float metal = (mat >> 16 & 255) / 255f, gloss = (mat >> 8 & 255) / 255f, emit = (mat & 255) / 255f;
                        float bright = Math.max(alb >> 16 & 255, Math.max(alb >> 8 & 255, alb & 255)) / 255f;
                        int role = emit > 0.25f ? EMIT : metal * gloss * smooth(0.35f, 0.7f, bright) > 0.3f ? TRIM : metal > 0.3f ? PLATE : SOFT;
                        int at = n * 12;
                        corner(out.pos, at, ax, ay, az, e1x, e1y, e1z, e2x, e2y, e2z, s0, t0);
                        corner(out.pos, at + 3, ax, ay, az, e1x, e1y, e1z, e2x, e2y, e2z, s1, t0);
                        corner(out.pos, at + 6, ax, ay, az, e1x, e1y, e1z, e2x, e2y, e2z, s1, t1);
                        corner(out.pos, at + 9, ax, ay, az, e1x, e1y, e1z, e2x, e2y, e2z, s0, t1);
                        out.v[n] = (ay + e1y * sm + e2y * tm) * 16f + 2f;
                        out.face[n] = (short) f;
                        out.ul[n] = (byte) Math.min(127, firstAlong ? j : i);
                        out.cols[n] = (byte) Math.min(127, firstAlong ? w2 : w1);
                        out.role[n] = (byte) role;
                        out.side[n] = (byte) side;
                        out.lum[n] = (byte) Math.round(bright * 255f);
                        grid[i * w2 + j] = n++;
                    }
                }
                // a trim is a line: the rim of a bright plate, or a strip a texel or two wide. The middle of a wide bright
                // plate is plate. (Worked out before any is changed, so a plate's rim stays a rim.)
                for (int i = 1; i < w1 - 1; i++) {
                    for (int j = 1; j < w2 - 1; j++) {
                        int k = grid[i * w2 + j];
                        if (k < 0 || (out.role[k] & 3) != TRIM) continue;
                        if (trim(out, grid[(i - 1) * w2 + j]) && trim(out, grid[(i + 1) * w2 + j]) && trim(out, grid[i * w2 + j - 1])
                                && trim(out, grid[i * w2 + j + 1])) out.role[k] |= 8;
                    }
                }
                for (int k = out.faceStart[f]; k < n; k++) if ((out.role[k] & 8) != 0) out.role[k] = PLATE;
            }
            out.faceStart[faces] = n;
            out.n = n;
        }
        return out;
    }

    private static boolean trim(Cells c, int k) {
        return k >= 0 && (c.role[k] & 3) == TRIM;
    }

    private static float dist(float[] p, int a, int b) {
        float x = p[a * 3] - p[b * 3], y = p[a * 3 + 1] - p[b * 3 + 1], z = p[a * 3 + 2] - p[b * 3 + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static void corner(float[] out, int at, float ax, float ay, float az, float e1x, float e1y, float e1z, float e2x, float e2y, float e2z,
                               float s, float t) {
        out[at] = ax + e1x * s + e2x * t;
        out[at + 1] = ay + e1y * s + e2y * t;
        out[at + 2] = az + e1z * s + e2z * t;
    }

    // ---- the light ----------------------------------------------------------------------------------------------------

    private static float hash(int a, int b, int c) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA77 + c * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65536f;
    }

    private static float fmod(float a, float b) {
        float r = a % b;
        return r < 0 ? r + b : r;
    }

    /**
     * How lit one cell is: 0 dark, to 1 and a touch over; negative for ink. The same rules as
     * {@code tools/art/arm_light_preview.py}'s {@code light}: the set's lines carry the type, the type's motif lies on the
     * rest one face at a time, and each type keeps the way it moves on bare skin (to the fist, or back from the hand).
     */
    static float light(FxStyle style, Cells c, int k, float t, int seed, float charge, boolean awake) {
        SetStyle set = c.style;
        float v = c.v[k];
        int q = c.face[k], ul = c.ul[k], cols = Math.max(1, c.cols[k]), role = c.role[k], f = c.side[k];
        int vi = Mth.floor(v);
        float reach = charge * ROWS;
        boolean line = role >= TRIM; // the set's own lines: its trims and what glows of itself
        boolean here = v < reach + (line ? set.lead : 0f); // the lines run ahead of the rest
        boolean region = here; // where the light has been: there a set's cloth may hold a glow
        float i = 0f;
        switch (style) {
            case KINETIC -> {
                // cracks: the set's lines pulse as if about to split, one seam wanders down every face
                if (here) {
                    if (role == EMIT) {
                        i = 1.05f;
                    } else if (role == TRIM) {
                        i = 0.62f + 0.38f * Mth.sin(t * 0.55f + v * 0.9f);
                    } else {
                        int seam = (int) (hash(seed, q, Math.floorDiv(vi, 2)) * cols);
                        boolean on = ul == seam || (Math.floorMod(vi, 4) == 1 && Math.abs(ul - seam) == 1 && hash(seed, q + 7, vi) > 0.45f);
                        if (awake && !on) on = Math.floorMod(vi, 6) == 3 && hash(seed, q + 3, ul + vi) > 0.5f;
                        i = on ? 0.6f + 0.3f * Mth.sin(t * 0.55f + v * 0.9f) : 0f;
                    }
                }
            }
            case HEAT -> {
                // the hand's end burns; the lines glow hot behind it and embers sit in the rest
                float lick = 3.5f + 2.5f * Mth.sin(t * 0.9f + ul * 2.1f + q) + (awake ? 2f : 0f);
                if (v >= ROWS - lick * charge) {
                    i = 0.75f + 0.35f * hash(seed, ul + q * 5, (int) (t * 0.8f) + vi);
                    region = true;
                } else if (here) {
                    if (line) i = 0.55f + 0.35f * Mth.sin(t * 0.7f + ul + v);
                    else i = hash(seed, q * 4 + ul, vi) > 0.78f ? 0.45f + 0.4f * Mth.sin(t * 0.7f + ul + v) : 0f;
                }
            }
            case VELOCITY -> {
                // streaks run to the fist: down every line, and one lane of each face
                if (here) {
                    int lane = (int) (hash(seed, q, 1) * cols);
                    if (line || ul == lane || (awake && ul == (lane + 2) % cols)) {
                        float run = fmod(v - t * 1.6f + hash(seed, q, ul) * 9f, 6f);
                        i = run < 3.6f ? 0.55f + 0.14f * run : (line ? 0.3f : 0f);
                    }
                }
            }
            case FLIGHT -> {
                // motes that lift and drift; the lines hold a pale light
                if (here) {
                    int drift = (int) (t * 0.22f);
                    float hh = hash(seed + drift / 3, q * 5 + ul, vi + drift);
                    if (hh > (awake ? 0.8f : 0.86f)) i = 0.6f + 0.4f * Mth.sin(t * 0.4f + hh * 30f);
                    else if (line) i = 0.42f;
                }
            }
            case GRAVITY -> {
                // bands falling to the wrist, ink pooled there with the lines lit over it
                if (here) {
                    float band = fmod(v + t * 0.5f, 4f);
                    if (v >= 9.5f) i = line ? 0.9f : -(0.6f + 0.4f * Mth.sin(t * 0.3f + ul));
                    else if (band < 1f) i = line ? 0.85f : 0.7f;
                    else if (awake && band < 2f && Math.floorMod(ul + vi, 2) == 0) i = 0.45f;
                }
            }
            case ENERGY, PRISM -> {
                // the lines carry it, a pulse running down them; a vein forks across each face
                if (here) {
                    float pulse = fmod(v - t * 0.9f, 5f);
                    if (line) {
                        i = pulse < 1.2f ? 1.15f : 0.62f;
                    } else {
                        int vein = Math.min(cols - 1, Math.floorMod(Math.floorDiv(vi, 2) + (int) (hash(seed, q, 2) * 2), 2) + (cols > 2 ? 1 : 0));
                        boolean on = ul == vein || (Math.floorMod(vi, 4) == 1 && ul == Math.min(cols - 1, vein + 1))
                                || (awake && Math.floorMod(vi, 4) == 3 && ul == Math.max(0, vein - 1));
                        if (on) i = pulse < 1.2f ? 1.1f : 0.58f;
                    }
                }
            }
            case CHARGE -> {
                // lightning crawling the lines, jumping every few ticks; a jagged line down each face
                if (here) {
                    int jump = (int) (t * 0.5f);
                    boolean on;
                    if (line) {
                        on = hash(seed + jump, q, Math.floorDiv(vi, 2)) > 0.4f;
                    } else {
                        int at = (int) (hash(seed + jump, q, Math.floorDiv(vi, 2)) * cols);
                        on = ul == at || (awake && ul == (at + 1) % cols && Math.floorMod(vi, 3) == 0);
                    }
                    if (on) i = hash(seed + (int) t, q + ul, vi) > 0.25f ? 1.1f : 0.5f;
                }
            }
            case SHADOW -> {
                // ink creeping back from the hand over cloth and plate; the lines stay lit in it
                float edge = ROWS - charge * (7f + (awake ? 2f : 0f)) + hash(seed, q, ul) * 2.5f + 0.6f * Mth.sin(t * 0.25f + ul + q);
                region = false;
                if (v >= edge + 1f) i = line ? 0.85f : -1f;
                else if (v >= edge) i = 0.8f;
            }
            case MIND, SOUL -> {
                // rings closing round the wrist, one after another, across everything they cross
                int rings = 1 + (int) (charge * (awake ? 4.99f : 3.99f));
                for (int r = 0; r < rings; r++) {
                    float at = 10.5f - r * 2;
                    if (Math.abs(v - at) <= 0.5f) {
                        i = fmod(t * 0.25f - r * 0.7f, 3f) < 1f ? 1.1f : 0.6f;
                        break;
                    }
                }
                region = v >= 10.5f - rings * 2;
                if (i == 0f && line && region) i = 0.4f;
            }
            case ARCHITECT -> {
                // a drawing: the set's lines inked in as the light arrives, a rule across every third row
                if (here) {
                    if (line) i = v > reach - 1.5f ? 1.1f : 0.62f;
                    else if (Math.floorMod(vi, 3) == 0 || ul == 0) i = v > reach - 1.5f ? 1.0f : 0.5f;
                    else if (awake && Math.floorMod(ul + vi, 4) == 0) i = 0.35f;
                }
            }
            case TECH -> {
                // a scan: the lines lit, a bright row sweeping the arm, a pixel blinking on each face
                if (here) {
                    float scan = fmod(t * 0.45f, ROWS + 3f);
                    if (Math.abs(v - scan) <= 0.5f) i = 1.15f;
                    else if (line) i = 0.6f;
                    else if (hash(seed, q, ul * 31 + vi) > 0.93f) i = Math.floorMod((int) (t * 0.3f) + vi, 3) == 0 ? 1.1f : 0.45f;
                    else if (awake && Math.floorMod(vi, 4) == 3) i = 0.4f;
                }
            }
            case SPIRIT -> {
                // tongues of flame from the hand, taller on the lines
                float tongue = (2.5f + 3.5f * hash(seed, q, ul) + 1.4f * Mth.sin(t * 0.6f + ul * 1.7f + q * 2.3f)) * charge + (awake ? 1.5f * charge : 0f);
                if (line) tongue += (1f + set.lead) * charge;
                region = v >= ROWS - 4f * charge;
                if (v >= ROWS - tongue) i = v >= ROWS - 2 ? 1.1f : 0.6f + 0.25f * Mth.sin(t * 0.8f + v);
            }
            case FROST -> {
                // rime: it takes the lines first, then crystals grow across the rest in diagonals
                float grown = charge * (9f + (awake ? 3f : 0f));
                region = v >= ROWS - grown;
                if (v >= ROWS - grown - (line ? 1f + set.lead : 0f)) {
                    if (line) i = 0.75f + 0.4f * hash(seed, ul + q * 3, vi + (int) (t * 0.15f));
                    else if (Math.floorMod(ul + vi + f, 3) == 0 || Math.floorMod(ul - vi - f, 4) == 0) i = 0.7f + 0.4f * hash(seed, ul + q * 3, vi + (int) (t * 0.15f));
                    else if (v >= ROWS - 1.5f) i = 0.5f;
                }
            }
            default -> {
                if (here && (line || Math.floorMod(ul + vi, 3) == 0)) i = 0.6f;
            }
        }
        if (i == 0f && role == SOFT && set.soak > 0f && region) return set.soak * (0.8f + 0.2f * Mth.sin(t * 0.3f + q));
        if (role == EMIT && i > 0f) i *= 1f - 0.3f * set.shimmer * hash(seed, q * 7 + ul, vi + (int) (t * 0.6f));
        // cloth and plate take the light by their own painting: a bright texel more, a dark one less, so the piece still reads
        if (!line && i > 0f) i *= 0.5f + 0.65f * ((c.lum[k] & 255) / 255f);
        return i > 0f ? i * set.gain(role) : i;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** One arm's light, kept from the submit to the draw: a small ring, nothing made a frame. */
    private static final class Draw implements SubmitNodeCollector.CustomGeometryRenderer {
        Cells cells;
        FxStyle style;
        boolean awake, glow;
        int seed;
        float charge, heat, time;

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            Cells c = cells;
            if (c == null) return;
            FxStyle plain = style.plain();
            float fade = Mth.clamp(heat, 0f, 1f), flash = Mth.clamp(heat - 1f, 0f, 1f);
            if (glow && plain == FxStyle.SHADOW && heat <= 1f) return; // ink doesn't glow
            float lift = glow ? GLOW_LIFT : LIFT;
            for (int f = 0; f < c.faces; f++) {
                int from = c.faceStart[f], to = c.faceStart[f + 1];
                if (from == to) continue;
                // a face turned away from your eye (the hand's space has the eye at its origin) is left out
                float nx = c.normal[f * 3], ny = c.normal[f * 3 + 1], nz = c.normal[f * 3 + 2];
                pose.pose().transformPosition(c.centre[f * 3], c.centre[f * 3 + 1], c.centre[f * 3 + 2], V);
                pose.transformNormal(nx, ny, nz, W);
                if (V.x * W.x + V.y * W.y + V.z * W.z >= 0f) continue;
                float lx = nx * lift, ly = ny * lift, lz = nz * lift;
                for (int k = from; k < to; k++) {
                    float i = light(plain, c, k, time, seed, charge, awake);
                    if (i == 0f) continue;
                    int at = k * 12;
                    float[] p = c.pos;
                    if (glow) {
                        // the brightest cells spill a little over their neighbours, along the face
                        if (i < 0.7f) continue;
                        int argb = scale(style.mid, (0.1f + 0.12f * flash) * fade * Math.min(i, 1.2f));
                        float cx = (p[at] + p[at + 6]) * 0.5f, cy = (p[at + 1] + p[at + 7]) * 0.5f, cz = (p[at + 2] + p[at + 8]) * 0.5f;
                        for (int v = 0; v < 12; v += 3) {
                            vc.addVertex(pose, cx + (p[at + v] - cx) * SPILL + lx, cy + (p[at + v + 1] - cy) * SPILL + ly, cz + (p[at + v + 2] - cz) * SPILL + lz)
                                    .setColor(argb);
                        }
                        continue;
                    }
                    int colour;
                    float alpha;
                    if (i < 0f) { // ink: a dark cell over what is there, the piece still showing through it
                        colour = style.accent;
                        alpha = 0.78f * fade * Math.min(1f, -i);
                    } else {
                        // the lines are crisp; cloth and plate are tinted, never painted out
                        colour = mix(style.mid, style.core, Mth.clamp((i - 0.55f) * 2.2f + flash, 0f, 1f));
                        alpha = fade * Mth.clamp(c.role[k] >= TRIM ? 0.25f + 0.75f * i : 0.1f + 0.55f * i, 0f, 1f);
                    }
                    int argb = (colour & 0xFFFFFF) | (Math.round(alpha * 255) << 24);
                    for (int v = 0; v < 12; v += 3) vc.addVertex(pose, p[at + v] + lx, p[at + v + 1] + ly, p[at + v + 2] + lz).setColor(argb);
                }
            }
        }
    }

    /** How far a bright cell's glow reaches past its own texel (1 = not at all). */
    private static final float SPILL = 2.2f;
    private static final Vector3f V = new Vector3f(), W = new Vector3f();
    private static final Draw[] RING = new Draw[8];
    private static int next;

    static {
        for (int i = 0; i < RING.length; i++) RING[i] = new Draw();
    }

    /** Lays the light on what the arm wears; the pose is on the arm's bone, where the piece itself was drawn. */
    static void draw(PoseStack ps, SubmitNodeCollector out, Cells cells, FxStyle style, boolean awake, int seed, float charge, float heat, float now) {
        submit(ps, out, cells, style, awake, seed, charge, heat, now, false);
        submit(ps, out, cells, style, awake, seed, charge, heat, now, true);
    }

    private static void submit(PoseStack ps, SubmitNodeCollector out, Cells cells, FxStyle style, boolean awake, int seed, float charge, float heat,
                               float now, boolean glow) {
        Draw d = RING[next];
        next = (next + 1) % RING.length;
        d.cells = cells;
        d.style = style;
        d.awake = awake;
        d.seed = seed;
        d.charge = charge;
        d.heat = heat;
        d.time = now;
        d.glow = glow;
        out.submitCustomGeometry(ps, glow ? RenderTypes.lightning() : RenderTypes.debugQuads(), d);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255),
                bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}
