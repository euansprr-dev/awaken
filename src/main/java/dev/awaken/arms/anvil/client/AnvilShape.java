package dev.awaken.arms.anvil.client;

import dev.awaken.Awaken;
import dev.awaken.arms.WeaponMesh;
import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.client.WeaponDraw;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Where a weapon's parts lie along it at the War Anvil (SPEC §46.3), read from its own mesh, so a longer or shorter design
 * needs nothing here. Heights are the design's (texels up the weapon, 0 at its grip point); a fraction along it (0 the
 * pommel's end, 1 the point) is the mesh's own "along". Where the blade, the guard and the grip begin along it is
 * {@link AnvilSong#HILT_CUT} (the Beastbound Longsword's) until the weapon generator gives each weapon its own.
 * <p>
 * Also the blank, what lies on the anvil until the hilt goes on: the blade with its guard's pieces left off, and its
 * section all along it, so any design starts as a square bar and is drawn out into its own shape ({@link AnvilBlank}).
 */
final class AnvilShape {
    /** The longest a weapon lies at the anvil (blocks): a longer design is laid a little smaller, so it fits the trough. */
    private static final float LONGEST = 1.2F;
    /** The bar's length before it's drawn out, of the blank's. */
    static final float BAR_LONG = 0.62F;
    /** The blank's section is read at this many heights from its foot to its point. */
    static final int PROFILE = 48;

    /** Where the blade, the guard, the grip and the pommel begin along it: the hilt goes on part by part. */
    final float[] cut;
    /** The blade's root and point, the blade's middle and the whole weapon's (design heights). */
    final float root, tip, mid, wholeMid;
    /** Where the hilt's three taps land: the middles of the guard, the grip and the pommel. */
    final float[] taps;
    /** Your hands on the hilt as you draw it: the grip's upper quarter and its lower. */
    final float[] hands;
    /** Blocks a design texel of it. */
    final float texel;
    /** The blank's lowest point, and where the tongs close on it (design heights). */
    final float foot, hold;
    /** Which of the mesh's triangles are the guard's, left off the blank until the hilt's first tap (null: none). */
    final boolean @Nullable [] guard;
    /** The blank's section at each of the {@link #PROFILE} heights: its middle across and half its width, its middle through and half its thickness. */
    private final float[] px, hx, pz, hz;
    /** The bar it starts as: its middle (across, through) and half its side. */
    final float barX, barZ, side;
    /** The blade itself: its middle across and half its width, its middle through and half its thickness, and its thickest. */
    final float midX, halfW, midZ, face, back;

    private AnvilShape(float[] cut, float root, float tip, float lo, float[] taps, float[] hands, Blank b) {
        this.cut = cut;
        this.root = root;
        this.tip = tip;
        this.mid = (root + tip) / 2F;
        this.wholeMid = (lo + tip) / 2F;
        this.taps = taps;
        this.hands = hands;
        this.texel = Math.min(AnvilScene.TEXEL, LONGEST / (tip - lo));
        this.foot = b.foot;
        this.hold = Math.max(root, b.foot) - 0.5F;
        this.guard = b.guard;
        this.px = b.px;
        this.hx = b.hx;
        this.pz = b.pz;
        this.hz = b.hz;
        this.barX = b.barX;
        this.barZ = b.barZ;
        this.side = b.side;
        this.midX = b.midX;
        this.halfW = b.halfW;
        this.midZ = b.midZ;
        this.face = b.face;
        this.back = b.back;
    }

    private record Blank(float foot, boolean @Nullable [] guard, float[] px, float[] hx, float[] pz, float[] hz, float barX, float barZ, float side,
                         float midX, float halfW, float midZ, float face, float back) {
        /** A blank with the hand-set blade's section all along it. */
        static Blank plain(float foot) {
            float[] px = new float[PROFILE], hx = new float[PROFILE], pz = new float[PROFILE], hz = new float[PROFILE];
            Arrays.fill(px, AnvilScene.BLADE_X);
            Arrays.fill(hx, AnvilScene.HALF_W);
            Arrays.fill(hz, AnvilScene.BACK_Z);
            return new Blank(foot, null, px, hx, pz, hz, AnvilScene.BLADE_X, 0F, 2F, AnvilScene.BLADE_X, AnvilScene.HALF_W, 0F, AnvilScene.FACE_Z,
                    AnvilScene.BACK_Z);
        }
    }

    /** The Beastbound Longsword as first set by hand: what a weapon without a mesh is laid out by. */
    static final AnvilShape FALLBACK = new AnvilShape(AnvilSong.HILT_CUT, 5F, 21F, -6F, new float[]{4F, 0.5F, -3.5F}, new float[]{2F, -0.5F},
            Blank.plain(5F));

    private static final Map<WeaponMesh, AnvilShape> READ = new IdentityHashMap<>();

    /** The mesh a thing at the anvil is drawn with: its own, or for a Hunter blade forged alone its tier's Longsword's blade. */
    static String meshId(String id) {
        return id.endsWith("_" + AnvilWork.BLADE) && AnvilWork.tierOf(id) >= 0 ? id.substring(0, id.indexOf('_')) + "_longsword" : id;
    }

    static boolean blade(String id) {
        return !meshId(id).equals(id);
    }

    static AnvilShape of(@Nullable String weapon) {
        WeaponMesh mesh = weapon == null ? null : WeaponDraw.mesh(meshId(weapon));
        if (mesh == null) return FALLBACK;
        AnvilShape s = READ.get(mesh);
        if (s == null) {
            if (READ.size() > 32) READ.clear(); // meshes are new on each resource reload
            // gauntlets have no guard: what's wide at their root is the cuff
            s = read(mesh, AnvilSong.HILT_CUT, !meshId(weapon).endsWith("gauntlets"));
            READ.put(mesh, s);
        }
        return s;
    }

    /** A weapon standing in the slot. */
    static AnvilShape of(ItemStack st) {
        var key = st.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(st.getItem());
        return of(key != null && key.getNamespace().equals(Awaken.MODID) ? key.getPath() : null);
    }

    private static final int BINS = 64;

    /** Its heights from the mesh: each fraction along it at the mean height of the points there, gaps bridged. */
    private static AnvilShape read(WeaponMesh mesh, float[] cut, boolean guarded) {
        float[] pos = mesh.pos();
        byte[] along = mesh.along();
        float[] d = new float[along.length * 3];
        float[] sum = new float[BINS];
        int[] count = new int[BINS];
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        Vector3f v = new Vector3f();
        for (int i = 0; i < along.length; i++) {
            AnvilScene.ITEM.transformPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], v);
            d[i * 3] = v.x;
            d[i * 3 + 1] = v.y;
            d[i * 3 + 2] = v.z;
            lo = Math.min(lo, v.y);
            hi = Math.max(hi, v.y);
            int b = (along[i] & 255) * BINS / 256;
            sum[b] += v.y;
            count[b]++;
        }
        float[] at = new float[BINS];
        int prev = -1;
        for (int b = 0; b < BINS; b++) {
            if (count[b] == 0) continue;
            at[b] = sum[b] / count[b];
            for (int g = prev + 1; g < b; g++) at[g] = prev < 0 ? at[b] : at[prev] + (at[b] - at[prev]) * (g - prev) / (b - prev);
            prev = b;
        }
        if (prev < 0) return FALLBACK;
        for (int g = prev + 1; g < BINS; g++) at[g] = at[prev];
        float root = height(at, cut[0]);
        // a mesh whose heights don't climb along it (or whose blade is a sliver) keeps the hand-set layout
        if (!(hi - lo > 4F) || !(hi - root > 2F) || !(root - lo > 1F)) return FALLBACK;
        float[] taps = {height(at, (cut[0] + cut[1]) / 2F), height(at, (cut[1] + cut[2]) / 2F), height(at, cut[2] / 2F)};
        float top = height(at, cut[1]), low = height(at, cut[2]), q = (top - low) / 4F;
        return new AnvilShape(cut, root, hi, lo, taps, new float[]{top - q, low + q}, blank(d, along, cut[0], root, hi, guarded));
    }

    /** The height at a fraction along it, between the bins' middles. */
    private static float height(float[] at, float a) {
        float x = a * BINS - 0.5F;
        if (x <= 0F) return at[0];
        if (x >= BINS - 1) return at[BINS - 1];
        int i = (int) x;
        return at[i] + (at[i + 1] - at[i]) * (x - i);
    }

    // ---- the blank ---------------------------------------------------------------------------------------------------

    /**
     * The blank from the mesh (design positions {@code d}): the triangles from {@code cut} along it on, less the guard's,
     * which is what's wide at the blade's root under a narrower blade (wider than 1.25 the blade's middle and a texel,
     * starting in the lowest 0.15 of it and ending below 0.45): the pieces up to a little over its top that stand out past
     * the blade just above it by more than 0.6. A spear's wings are halfway up it and an axe's head is its widest, so
     * neither has one.
     */
    private static Blank blank(float[] d, byte[] along, float cut, float root, float tip, boolean guarded) {
        int tris = along.length / 3, need = Math.round(cut * 255) * 3;
        boolean[] use = new boolean[tris];
        for (int t = 0; t < tris; t++) use[t] = (along[t * 3] & 255) + (along[t * 3 + 1] & 255) + (along[t * 3 + 2] & 255) >= need;
        float len = tip - root;
        float[][] sec = new float[PROFILE][];
        for (int k = 0; k < PROFILE; k++) sec[k] = section(d, use, root + len * (k + 0.5F) / PROFILE);
        float wb = median(sec, 0, 0.35F, 0.8F);
        boolean[] guard = null;
        int first = -1, last = -1;
        for (int k = 0; k < PROFILE && (k + 0.5F) / PROFILE <= 0.45F; k++) {
            if (sec[k] == null || sec[k][1] - sec[k][0] <= 1.25F * wb + 1F) continue;
            if (first < 0) first = k;
            last = k;
        }
        if (guarded && first >= 0 && (first + 0.5F) / PROFILE <= 0.15F) {
            float top = root + len * (last + 0.5F) / PROFILE;
            float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
            for (int k = last + 1; k < PROFILE && root + len * (k + 0.5F) / PROFILE <= top + 0.25F * len; k++) {
                if (sec[k] == null) continue;
                x0 = Math.min(x0, sec[k][0]);
                x1 = Math.max(x1, sec[k][1]);
                z0 = Math.min(z0, sec[k][2]);
                z1 = Math.max(z1, sec[k][3]);
            }
            if (x0 <= x1) {
                guard = new boolean[tris];
                for (int t = 0; t < tris; t++) {
                    if (!use[t]) continue;
                    float ny = -Float.MAX_VALUE, nx0 = Float.MAX_VALUE, nx1 = -Float.MAX_VALUE, nz0 = Float.MAX_VALUE, nz1 = -Float.MAX_VALUE;
                    for (int c = t * 9; c < t * 9 + 9; c += 3) {
                        nx0 = Math.min(nx0, d[c]);
                        nx1 = Math.max(nx1, d[c]);
                        ny = Math.max(ny, d[c + 1]);
                        nz0 = Math.min(nz0, d[c + 2]);
                        nz1 = Math.max(nz1, d[c + 2]);
                    }
                    if (ny <= top + 0.75F && (nx0 < x0 - 0.6F || nx1 > x1 + 0.6F || nz0 < z0 - 0.6F || nz1 > z1 + 0.6F)) {
                        guard[t] = true;
                        use[t] = false;
                    }
                }
            }
        }
        // its foot and section, from what's left
        float foot = Float.MAX_VALUE;
        for (int t = 0; t < tris; t++) {
            if (use[t]) for (int c = t * 9; c < t * 9 + 9; c += 3) foot = Math.min(foot, d[c + 1]);
        }
        if (foot >= tip - 1F) return Blank.plain(root);
        float blen = tip - foot;
        float[][] at = new float[PROFILE][];
        int good = -1;
        for (int k = 0; k < PROFILE; k++) {
            at[k] = section(d, use, foot + blen * (k + 0.5F) / PROFILE);
            if (at[k] != null) good = k;
        }
        if (good < 0) return Blank.plain(root);
        float[] px = new float[PROFILE], hx = new float[PROFILE], pz = new float[PROFILE], hz = new float[PROFILE];
        int prev = -1;
        for (int k = 0; k < PROFILE; k++) {
            if (at[k] == null) continue;
            fill(at[k], k, px, hx, pz, hz);
            // a gap (a long face's middle with no corner there is still crossed, so only past both ends) takes its neighbours'
            for (int g = prev + 1; g < k; g++) {
                if (prev < 0) fill(at[k], g, px, hx, pz, hz);
                else {
                    float f = (g - prev) / (float) (k - prev);
                    px[g] = Mth.lerp(f, px[prev], px[k]);
                    hx[g] = Mth.lerp(f, hx[prev], hx[k]);
                    pz[g] = Mth.lerp(f, pz[prev], pz[k]);
                    hz[g] = Mth.lerp(f, hz[prev], hz[k]);
                }
            }
            prev = k;
        }
        for (int g = prev + 1; g < PROFILE; g++) fill(at[prev], g, px, hx, pz, hz);
        float side = 0F;
        for (int k = 0; k < PROFILE; k++) side += (float) Math.sqrt(hx[k] * hz[k]);
        side = Mth.clamp(side / PROFILE, 1F, 3F);
        float midX = median(px, 0.35F, 0.8F), midZ = median(pz, 0.35F, 0.8F);
        float back = 0F;
        for (int t = 0; t < tris; t++) {
            if (use[t]) for (int c = t * 9; c < t * 9 + 9; c += 3) back = Math.max(back, Math.abs(d[c + 2] - midZ));
        }
        return new Blank(foot, guard, px, hx, pz, hz, median(px, 0F, 1F), median(pz, 0F, 1F), side, midX, median(hx, 0.35F, 0.8F), midZ,
                median(hz, 0.35F, 0.8F), back);
    }

    private static void fill(float[] s, int k, float[] px, float[] hx, float[] pz, float[] hz) {
        px[k] = (s[0] + s[1]) / 2F;
        hx[k] = Math.max(0.25F, (s[1] - s[0]) / 2F);
        pz[k] = (s[2] + s[3]) / 2F;
        hz[k] = Math.max(0.25F, (s[3] - s[2]) / 2F);
    }

    /** Where the triangles in use cross the height y: least and most across, least and most through; null for nowhere. */
    private static float @Nullable [] section(float[] d, boolean[] use, float y) {
        float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (int t = 0; t < use.length; t++) {
            if (!use[t]) continue;
            for (int e = 0; e < 3; e++) {
                int a = (t * 3 + e) * 3, b = (t * 3 + (e + 1) % 3) * 3;
                float ya = d[a + 1], yb = d[b + 1];
                if (ya == yb || (ya - y) * (yb - y) > 0F) continue;
                float k = (y - ya) / (yb - ya), x = d[a] + (d[b] - d[a]) * k, z = d[a + 2] + (d[b + 2] - d[a + 2]) * k;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                z0 = Math.min(z0, z);
                z1 = Math.max(z1, z);
            }
        }
        return x0 <= x1 ? new float[]{x0, x1, z0, z1} : null;
    }

    /** The middle value of the sections' widths ({@code c} 0) between two fractions of the way up. */
    private static float median(float[][] sec, int c, float from, float to) {
        float[] w = new float[sec.length];
        int n = 0;
        for (int k = 0; k < sec.length; k++) {
            float f = (k + 0.5F) / sec.length;
            if (sec[k] != null && f >= from && f <= to) w[n++] = sec[k][c + 1] - sec[k][c];
        }
        if (n == 0) return 0F;
        Arrays.sort(w, 0, n);
        return w[n / 2];
    }

    private static float median(float[] a, float from, float to) {
        int i0 = Math.round(from * (a.length - 1)), i1 = Math.round(to * (a.length - 1));
        float[] w = Arrays.copyOfRange(a, i0, i1 + 1);
        Arrays.sort(w);
        return w[w.length / 2];
    }

    // ---- drawn out and twisted ---------------------------------------------------------------------------------------

    /**
     * A corner of the blank's mesh as it is drawn out {@code d} (0 the bar, 1 its own shape), design texels in and out:
     * its section at its height squared onto the bar's, every outer corner pushed out to the bar's sides and anything
     * further in kept just inside them, then drawn back to its own over d; its height drawn out about {@code mid}.
     */
    void corner(float x, float y, float z, float mid, float d, Vector3f out) {
        float f = Mth.clamp((y - foot) / (tip - foot) * PROFILE - 0.5F, 0F, PROFILE - 1.001F);
        int i = (int) f;
        float k = f - i;
        float cx = Mth.lerp(k, px[i], px[i + 1]), w = Mth.lerp(k, hx[i], hx[i + 1]), cz = Mth.lerp(k, pz[i], pz[i + 1]), h = Mth.lerp(k, hz[i], hz[i + 1]);
        float bx = barX + side * square((x - cx) / w), bz = barZ + side * square((z - cz) / h);
        out.set(Mth.lerp(d, bx, x), mid + (y - mid) * Mth.lerp(d, BAR_LONG, 1F), Mth.lerp(d, bz, z));
    }

    /** Out to the side from a fifth of the way there, a corner further in a little inside one further out (no two faces meet). */
    private static float square(float u) {
        return Mth.clamp(u / 0.3F, -1F, 1F) * (0.9F + 0.1F * Math.min(1F, Math.abs(u)));
    }

    /** A point on or about the work (a mark, the tongs' lips, a hand) as it's drawn out: scaled from the blade's middle onto the bar's. */
    void point(float x, float y, float z, float mid, float d, Vector3f out) {
        float bx = barX + (x - midX) * side / halfW, bz = barZ + (z - midZ) * side / face;
        out.set(Mth.lerp(d, bx, x), mid + (y - mid) * Mth.lerp(d, BAR_LONG, 1F), Mth.lerp(d, bz, z));
    }

    /** The twist at design height y (radians), of {@code twist} at the far end: none where the tongs hold it, the turns between. */
    float turnAt(float y, float twist) {
        if (twist == 0F) return 0F;
        float v = Mth.clamp(((y - foot) / (tip - foot) - 0.12F) / 0.76F, 0F, 1F);
        return twist * v * v * (3F - 2F * v);
    }

    /** Turns a drawn-out point about the work's length by {@code a} (the bar's middle, moving to the blade's as it's drawn out). */
    void turn(Vector3f p, float a, float d) {
        if (a == 0F) return;
        float ax = Mth.lerp(d, barX, midX), az = Mth.lerp(d, barZ, midZ), c = Mth.cos(a), s = Mth.sin(a), x = p.x - ax, z = p.z - az;
        p.x = ax + x * c - z * s;
        p.z = az + x * s + z * c;
    }
}
