package dev.awaken.arms.anvil.client;

import dev.awaken.Awaken;
import dev.awaken.arms.WeaponMesh;
import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.arms.client.WeaponDraw;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Where a weapon's parts lie along it at the War Anvil (SPEC §46.3), read from its own mesh, so a longer or shorter design
 * needs nothing here. Heights are the design's (texels up the weapon, 0 at its grip point); a fraction along it (0 the
 * pommel's end, 1 the point) is the mesh's own "along". Where the blade, the guard and the grip begin along it is
 * {@link AnvilSong#HILT_CUT} (the Beastbound Longsword's) until the weapon generator gives each weapon its own.
 */
final class AnvilShape {
    /** The longest a weapon lies at the anvil (blocks): a longer design is laid a little smaller, so it fits the trough. */
    private static final float LONGEST = 1.2F;

    /** Where the blade, the guard, the grip and the pommel begin along it: the hilt goes on part by part. */
    final float[] cut;
    /** The blade's root and point, the blade's middle and the whole weapon's (design heights). */
    final float root, tip, mid, wholeMid;
    /** Where the hilt's three taps land: the middles of the guard, the grip and the pommel. */
    final float[] taps;
    /** Blocks a design texel of it. */
    final float texel;

    private AnvilShape(float[] cut, float root, float tip, float lo, float[] taps) {
        this.cut = cut;
        this.root = root;
        this.tip = tip;
        this.mid = (root + tip) / 2F;
        this.wholeMid = (lo + tip) / 2F;
        this.taps = taps;
        this.texel = Math.min(AnvilScene.TEXEL, LONGEST / (tip - lo));
    }

    /** The Beastbound Longsword as first set by hand: what a weapon without a mesh is laid out by. */
    static final AnvilShape FALLBACK = new AnvilShape(AnvilSong.HILT_CUT, 5F, 21F, -6F, new float[]{4F, 0.5F, -3.5F});

    private static final Map<WeaponMesh, AnvilShape> READ = new IdentityHashMap<>();

    static AnvilShape of(@Nullable String weapon) {
        WeaponMesh mesh = weapon == null ? null : WeaponDraw.mesh(weapon);
        if (mesh == null) return FALLBACK;
        AnvilShape s = READ.get(mesh);
        if (s == null) {
            if (READ.size() > 32) READ.clear(); // meshes are new on each resource reload
            s = read(mesh, AnvilSong.HILT_CUT);
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
    private static AnvilShape read(WeaponMesh mesh, float[] cut) {
        float[] pos = mesh.pos();
        byte[] along = mesh.along();
        float[] sum = new float[BINS];
        int[] count = new int[BINS];
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        Vector3f v = new Vector3f();
        for (int i = 0; i < along.length; i++) {
            AnvilScene.ITEM.transformPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], v);
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
        return new AnvilShape(cut, root, hi, lo, taps);
    }

    /** The height at a fraction along it, between the bins' middles. */
    private static float height(float[] at, float a) {
        float x = a * BINS - 0.5F;
        if (x <= 0F) return at[0];
        if (x >= BINS - 1) return at[BINS - 1];
        int i = (int) x;
        return at[i] + (at[i + 1] - at[i]) * (x - i);
    }
}
