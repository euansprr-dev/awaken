package dev.awaken.arms.anvil.client;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsHilts;
import dev.awaken.arms.WeaponMesh;
import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.client.WeaponDraw;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Where a weapon's parts lie along it at the War Anvil (SPEC §46.3), read from its own mesh, so a longer or shorter design
 * needs nothing here. Heights are the design's (texels up the weapon, 0 at its grip point); a fraction along it (0 the
 * pommel's end, 1 the point) is the mesh's own "along". Where the blade, the guard and the grip begin along it is the
 * sword's own {@link ArmsHilts#CUTS}, from its design's parts; a weapon without takes {@link AnvilSong#HILT_CUT}.
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
    /** The blank's foot, where it's cut clean from the hilt, and where the tongs close on it (design heights). */
    final float foot, hold;
    /** Which of the mesh's triangles the blank is cut from (null: the hand-set layout, cut along the mesh as the hilt is). */
    final boolean @Nullable [] blank;
    /** The blank's cut end: triangles of {@link AnvilBlank}'s corners (position, normal, uv, along), one texel's colour each. */
    final float[] cap;
    /** The texel the bar's core is coloured from, and how far along the blade its foot is (0..255). */
    final float coreU, coreV, footAlong;
    /** The blank's section at each of the {@link #PROFILE} heights: its middle across and half its width, its middle through and half its thickness. */
    private final float[] px, hx, pz, hz;
    /** The bar it starts as: its middle (across, through) and half its side. */
    final float barX, barZ, side;
    /** The blade itself: its middle across and half its width, its middle through and half its thickness, and its thickest. */
    final float midX, halfW, midZ, face, back;
    /** Item space to this shape's design texels: {@link AnvilScene#ITEM}, or for a pick's head at the anvil that turned so its long axis runs up the work. */
    final Matrix4f item;
    /** A forged pick (SPEC §49): {@code head} is the head alone lying along the work, otherwise it's the whole tool. */
    final boolean pick, head;
    /** The lowest of it (design height). */
    final float lo;

    private AnvilShape(float[] cut, float root, float tip, float lo, float[] taps, float[] hands, Blank b) {
        this(cut, root, tip, lo, taps, hands, b, AnvilScene.ITEM, false, false);
    }

    private AnvilShape(float[] cut, float root, float tip, float lo, float[] taps, float[] hands, Blank b, Matrix4f item, boolean pick, boolean head) {
        this.item = item;
        this.pick = pick;
        this.head = head;
        this.lo = lo;
        this.cut = cut;
        this.root = root;
        this.tip = tip;
        this.mid = (root + tip) / 2F;
        this.wholeMid = (lo + tip) / 2F;
        this.taps = taps;
        this.hands = hands;
        this.texel = Math.min(AnvilScene.TEXEL, LONGEST / (tip - lo));
        this.foot = b.foot;
        this.hold = b.foot + 0.5F;
        this.blank = b.blank;
        this.cap = b.cap;
        this.coreU = b.coreU;
        this.coreV = b.coreV;
        this.footAlong = b.footAlong;
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

    private record Blank(float foot, boolean @Nullable [] blank, float[] cap, float coreU, float coreV, float footAlong, float[] px, float[] hx,
                         float[] pz, float[] hz, float barX, float barZ, float side, float midX, float halfW, float midZ, float face, float back) {
        /** A blank with the hand-set blade's section all along it. */
        static Blank plain(float foot, float cut) {
            float[] px = new float[PROFILE], hx = new float[PROFILE], pz = new float[PROFILE], hz = new float[PROFILE];
            Arrays.fill(px, AnvilScene.BLADE_X);
            Arrays.fill(hx, AnvilScene.HALF_W);
            Arrays.fill(hz, AnvilScene.BACK_Z);
            return new Blank(foot, null, new float[0], 0.5F, 0.5F, cut * 255F, px, hx, pz, hz, AnvilScene.BLADE_X, 0F, 2F, AnvilScene.BLADE_X,
                    AnvilScene.HALF_W, 0F, AnvilScene.FACE_Z, AnvilScene.BACK_Z);
        }
    }

    /** The Beastbound Longsword as first set by hand: what a weapon without a mesh is laid out by. */
    static final AnvilShape FALLBACK = new AnvilShape(AnvilSong.HILT_CUT, 5F, 21F, -6F, new float[]{4F, 0.5F, -3.5F}, new float[]{2F, -0.5F},
            Blank.plain(5F, AnvilSong.HILT_CUT[0]));

    private static final Map<WeaponMesh, AnvilShape> READ = new IdentityHashMap<>(), HEADS = new IdentityHashMap<>();
    /** Height of a pick's eye in its design (arms_picks.HEAD) and the mesh's "along" where its head begins. */
    private static final float EYE = 23.5F;
    private static final int HEAD_ALONG = 191;
    private static final Matrix4f PICK_HEAD = new Matrix4f().rotateZ(-Mth.HALF_PI).translate(0F, -EYE, 0F).mul(AnvilScene.ITEM);

    static boolean isPick(@Nullable String id) {
        return id != null && id.contains("_pick_");
    }

    /** A pick's head alone, lying along the work (SPEC §49.4): what's forged on the face before it's hung. */
    static AnvilShape head(@Nullable String id) {
        WeaponMesh mesh = id == null ? null : WeaponDraw.mesh(id);
        if (mesh == null) return FALLBACK;
        AnvilShape s = HEADS.get(mesh);
        if (s == null) {
            if (HEADS.size() > 32) HEADS.clear();
            s = readHead(mesh);
            HEADS.put(mesh, s);
        }
        return s;
    }

    private static AnvilShape readHead(WeaponMesh mesh) {
        float[] pos = mesh.pos();
        byte[] along = mesh.along();
        float[] d = new float[along.length * 3];
        Vector3f v = new Vector3f();
        int tris = along.length / 3;
        boolean[] use = new boolean[tris];
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int t = 0; t < tris; t++) {
            boolean head = true;
            for (int i = t * 3; i < t * 3 + 3; i++) head &= (along[i] & 255) >= HEAD_ALONG;
            use[t] = head;
            for (int i = t * 3; i < t * 3 + 3; i++) {
                PICK_HEAD.transformPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], v);
                d[i * 3] = v.x;
                d[i * 3 + 1] = v.y;
                d[i * 3 + 2] = v.z;
                if (head) {
                    lo = Math.min(lo, v.y);
                    hi = Math.max(hi, v.y);
                }
            }
        }
        if (!(hi - lo > 4F)) return FALLBACK;
        float[] cut = {0.75F};
        Blank b = blank(d, mesh.uv(), along, cut[0], lo, hi, false, use);
        float mid = (lo + hi) / 2F;
        return new AnvilShape(cut, lo, hi, lo, new float[]{mid, mid, mid}, new float[]{mid + 2F, mid - 2F}, b, PICK_HEAD, true, true);
    }

    /** A whole pick, haft and head: its cuts bring the haft on from the head down as it's bound. */
    private static AnvilShape readWhole(WeaponMesh mesh) {
        Vector3f v = new Vector3f();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        float[] pos = mesh.pos();
        for (int i = 0; i < pos.length; i += 3) {
            AnvilScene.ITEM.transformPosition(pos[i], pos[i + 1], pos[i + 2], v);
            lo = Math.min(lo, v.y);
            hi = Math.max(hi, v.y);
        }
        float[] cut = {0.75F, 0.5F, 0.25F, 0F};
        float root = EYE - 4F;
        return new AnvilShape(cut, root, hi, lo, new float[]{EYE + 3F, EYE, EYE - 5F}, new float[]{8F, -1F}, Blank.plain(root, cut[0]), AnvilScene.ITEM, true, false);
    }

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
            String id = meshId(weapon);
            s = isPick(id) ? readWhole(mesh) : read(mesh, ArmsHilts.CUTS.getOrDefault(id, AnvilSong.HILT_CUT), !id.endsWith("gauntlets"));
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
        return new AnvilShape(cut, root, hi, lo, taps, new float[]{top - q, low + q}, blank(d, mesh.uv(), along, cut[0], root, hi, guarded, null));
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
     * The blank from the mesh (design positions {@code d}): the blade cut clean across at its root, or at its guard's top,
     * so the guard and the grip's top stay with the hilt. The guard is what's wide about the root, read through the cut on
     * the whole mesh (wider than 1.25 the blade's middle and a texel, starting in its lowest 0.15 and ending below 0.45); a
     * spear's wings are halfway up it and an axe's head is its widest, so neither has one. Pieces reaching less than 0.75
     * over the cut that stand out past the blade above them by more than 0.6 (a guard's tips, a collar) are the hilt's too.
     */
    private static Blank blank(float[] d, float[] uv, byte[] along, float cut, float root, float tip, boolean guarded, boolean @Nullable [] given) {
        int tris = along.length / 3;
        boolean[] all = new boolean[tris], use = new boolean[tris];
        float[] high = new float[tris];
        for (int t = 0; t < tris; t++) {
            all[t] = true;
            high[t] = Math.max(d[t * 9 + 1], Math.max(d[t * 9 + 4], d[t * 9 + 7]));
            use[t] = given != null ? given[t] : high[t] > root + 1e-3F;
        }
        float len = tip - root, step = len / PROFILE;
        float[][] sec = new float[PROFILE][];
        for (int k = 0; k < PROFILE; k++) sec[k] = section(d, use, root + step * (k + 0.5F));
        float wide = 1.25F * median(sec, 0, 0.35F, 0.8F) + 1F, from = root;
        if (guarded) {
            float first = Float.NaN, last = Float.NaN;
            for (int k = -PROFILE / 5; (k + 0.5F) / PROFILE <= 0.45F; k++) {
                float y = root + step * (k + 0.5F);
                if (!wider(d, all, y, wide)) continue;
                if (Float.isNaN(first)) first = y;
                last = y;
            }
            if (first <= root + 0.15F * len) {
                // where it ends, to a hair
                float a = last, b = last + step;
                for (int i = 0; i < 10; i++) {
                    float m = (a + b) / 2F;
                    if (wider(d, all, m, wide)) a = m;
                    else b = m;
                }
                from = Math.max(root, a);
            }
        }
        for (int t = 0; t < tris && given == null; t++) use[t] = high[t] > from + 1e-3F;
        if (guarded) {
            // the blade's footprint just above, and what stands out past it just over the cut
            float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
            for (int k = 0; k < PROFILE / 4; k++) {
                float[] s = section(d, use, from + 0.75F + step * (k + 0.5F));
                if (s == null) continue;
                x0 = Math.min(x0, s[0]);
                x1 = Math.max(x1, s[1]);
                z0 = Math.min(z0, s[2]);
                z1 = Math.max(z1, s[3]);
            }
            for (int t = 0; t < tris && x0 <= x1; t++) {
                if (!use[t] || high[t] > from + 0.75F) continue;
                float nx0 = Float.MAX_VALUE, nx1 = -Float.MAX_VALUE, nz0 = Float.MAX_VALUE, nz1 = -Float.MAX_VALUE;
                for (int c = t * 9; c < t * 9 + 9; c += 3) {
                    nx0 = Math.min(nx0, d[c]);
                    nx1 = Math.max(nx1, d[c]);
                    nz0 = Math.min(nz0, d[c + 2]);
                    nz1 = Math.max(nz1, d[c + 2]);
                }
                if (nx0 < x0 - 0.6F || nx1 > x1 + 0.6F || nz0 < z0 - 0.6F || nz1 > z1 + 0.6F) use[t] = false;
            }
        }
        // its foot and section, from what's left
        float foot = Float.MAX_VALUE;
        for (int t = 0; t < tris; t++) {
            if (use[t]) for (int c = t * 9; c < t * 9 + 9; c += 3) foot = Math.min(foot, d[c + 1]);
        }
        foot = Math.max(from, foot);
        if (foot >= tip - 1F) return Blank.plain(root, cut);
        float blen = tip - foot;
        float[][] at = new float[PROFILE][];
        int good = -1;
        for (int k = 0; k < PROFILE; k++) {
            at[k] = section(d, use, foot + blen * (k + 0.5F) / PROFILE);
            if (at[k] != null) good = k;
        }
        if (good < 0) return Blank.plain(root, cut);
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
        float midX = median(px, 0.35F, 0.8F), midZ = median(pz, 0.35F, 0.8F), midY = (foot + tip) / 2F;
        float back = 0F, near = Float.MAX_VALUE, coreU = 0.5F, coreV = 0.5F;
        for (int t = 0; t < tris; t++) {
            if (!use[t]) continue;
            for (int i = t * 3; i < t * 3 + 3; i++) {
                back = Math.max(back, Math.abs(d[i * 3 + 2] - midZ));
                // the core takes the colour of the blade's middle
                float dx = d[i * 3] - midX, dy = d[i * 3 + 1] - midY, n = dx * dx + dy * dy;
                if (n < near) {
                    near = n;
                    coreU = uv[i * 2];
                    coreV = uv[i * 2 + 1];
                }
            }
        }
        float[] cap = cap(d, uv, along, use, foot);
        float footAlong = cut * 255F;
        if (cap.length > 0) {
            footAlong = 0F;
            for (int o = 8; o < cap.length; o += AnvilBlank.V) footAlong += cap[o];
            footAlong /= cap.length / AnvilBlank.V;
        }
        return new Blank(foot, use, cap, coreU, coreV, footAlong, px, hx, pz, hz, median(px, 0F, 1F), median(pz, 0F, 1F), side, midX,
                median(hx, 0.35F, 0.8F), midZ, median(hz, 0.35F, 0.8F), back);
    }

    private static boolean wider(float[] d, boolean[] use, float y, float than) {
        float[] s = section(d, use, y);
        return s != null && s[1] - s[0] > than;
    }

    /**
     * The blank's cut end: where each of its triangles crosses the foot a segment (cut from the edge's lower end, as
     * {@link AnvilBlank} cuts it, so the two meet), fanned from all their middle and facing down the blade; each fan
     * triangle one texel's colour, its segment's first end's.
     */
    private static float[] cap(float[] d, float[] uv, byte[] along, boolean[] use, float foot) {
        float[] seg = new float[64 * 10];
        float[] e = new float[10];
        int n = 0;
        float cx = 0F, cz = 0F, ca = 0F;
        for (int t = 0; t < use.length; t++) {
            if (!use[t]) continue;
            int found = 0;
            for (int j = 0; j < 3 && found < 2; j++) {
                int a = t * 3 + j, b = t * 3 + (j + 1) % 3;
                float ya = d[a * 3 + 1], yb = d[b * 3 + 1];
                if (!(ya < foot && yb > foot || ya > foot && yb < foot)) continue;
                int lo = ya < yb ? a : b, hi = lo == a ? b : a;
                float k = (foot - d[lo * 3 + 1]) / (d[hi * 3 + 1] - d[lo * 3 + 1]);
                int o = found * 5;
                e[o] = Mth.lerp(k, d[lo * 3], d[hi * 3]);
                e[o + 1] = Mth.lerp(k, d[lo * 3 + 2], d[hi * 3 + 2]);
                e[o + 2] = Mth.lerp(k, uv[lo * 2], uv[hi * 2]);
                e[o + 3] = Mth.lerp(k, uv[lo * 2 + 1], uv[hi * 2 + 1]);
                e[o + 4] = Mth.lerp(k, along[lo] & 255, along[hi] & 255);
                found++;
            }
            if (found < 2) continue;
            if (n * 10 == seg.length) seg = Arrays.copyOf(seg, seg.length * 2);
            System.arraycopy(e, 0, seg, n * 10, 10);
            cx += e[0] + e[5];
            cz += e[1] + e[6];
            ca += e[4] + e[9];
            n++;
        }
        if (n == 0) return new float[0];
        cx /= 2 * n;
        cz /= 2 * n;
        ca /= 2 * n;
        int V = AnvilBlank.V;
        float[] out = new float[n * 3 * V];
        for (int s = 0; s < n; s++) {
            int o = s * 10, p = s * 3 * V;
            // facing down the blade: (a - c) x (b - c) points down
            boolean flip = (seg[o + 1] - cz) * (seg[o + 5] - cx) - (seg[o] - cx) * (seg[o + 6] - cz) > 0F;
            int a = flip ? o + 5 : o, b = flip ? o : o + 5;
            corner(out, p, cx, foot, cz, seg[o + 2], seg[o + 3], ca);
            corner(out, p + V, seg[a], foot, seg[a + 1], seg[o + 2], seg[o + 3], seg[a + 4]);
            corner(out, p + 2 * V, seg[b], foot, seg[b + 1], seg[o + 2], seg[o + 3], seg[b + 4]);
        }
        return out;
    }

    private static void corner(float[] out, int o, float x, float y, float z, float u, float v, float along) {
        out[o] = x;
        out[o + 1] = y;
        out[o + 2] = z;
        out[o + 3] = 0F;
        out[o + 4] = -1F;
        out[o + 5] = 0F;
        out[o + 6] = u;
        out[o + 7] = v;
        out[o + 8] = along;
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
