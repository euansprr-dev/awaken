package dev.awaken.terrain.landmark;

import net.minecraft.util.RandomSource;

/**
 * A Shard Drift (docs/modules/lands.md, "Shattered Highlands"): where a mountain split along a line of light, pieces
 * of it hang in the air above the crack. A cluster of floating rock shards strung along the fault: one great one
 * over the line itself, smaller ones before and after it, each an upturned point of riven shale with faultstone in its
 * lower half and the seam's light at its tip, some still grassed on top with roots hanging from the rim, and rubble
 * drifting between them. Everything here floats on purpose.
 * <p>
 * The layout (where each shard is along the fault) comes from the seed and the fault's direction; the structure start
 * samples the ground under each shard once, so every chunk builds the same drift at the same heights.
 */
final class ShardDrift {
    private ShardDrift() {}

    // palette codes (Landmark.SHARD_DRIFT.state)
    static final byte SHALE = 1, FAULT = 2, SEAM = 3, GRASS = 4, DIRT = 5, ROOTS = 6, COARSE = 7;

    /** One shard: where it hangs (x, z from the centre), its top's radius, height, and how far above the ground its tip is. */
    record Shard(double x, double z, double radius, int height, int lift, double tiltX, double tiltZ, boolean grassy, double phase) {}

    /** The shards' places and sizes from the seed and the fault's direction (radians); the first is the great one. */
    static Shard[] layout(long seed, double angle) {
        RandomSource r = RandomSource.create(seed ^ 0x5A17L);
        int n = 7 + r.nextInt(6);
        Shard[] out = new Shard[n];
        double ca = Math.cos(angle), sa = Math.sin(angle);
        out[0] = new Shard(0, 0, 5.0 + r.nextDouble() * 1.5, 11 + r.nextInt(5), 20 + r.nextInt(8), tilt(r), tilt(r), true, r.nextDouble() * 6.3);
        int placed = 1, guard = 0;
        while (placed < n && guard++ < 200) {
            double along = (r.nextDouble() * 2 - 1) * 32, across = (r.nextDouble() * 2 - 1) * 7;
            double x = ca * along - sa * across, z = sa * along + ca * across;
            double rad = 1.6 + r.nextDouble() * r.nextDouble() * 3.4;
            boolean clear = true;
            for (int i = 0; i < placed; i++) {
                if (Math.hypot(out[i].x() - x, out[i].z() - z) < out[i].radius() + rad + 3) clear = false;
            }
            if (!clear) continue;
            int h = (int) Math.round(rad * (1.6 + r.nextDouble() * 0.9)) + 2;
            // higher the further from the line's middle: the drift rises like a wave toward its ends
            int lift = 11 + r.nextInt(14) + (int) (Math.abs(along) * 0.35);
            out[placed++] = new Shard(x, z, rad, h, lift, tilt(r), tilt(r), r.nextInt(3) == 0, r.nextDouble() * 6.3);
        }
        return java.util.Arrays.copyOf(out, placed);
    }

    private static double tilt(RandomSource r) {
        return (r.nextDouble() - 0.5) * 0.5;
    }

    /**
     * The whole drift as voxels: {@code ground[i]} is the first air above the ground under shard i, relative to the
     * centre's (voxel y 0).
     */
    static Voxels build(long seed, double angle, int[] ground) {
        Shard[] shards = layout(seed, angle);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, extent = 8;
        for (int i = 0; i < shards.length; i++) {
            Shard s = shards[i];
            int g = i < ground.length ? ground[i] : 0;
            lo = Math.min(lo, g + s.lift() - 3);
            hi = Math.max(hi, g + s.lift() + s.height() + 3);
            extent = Math.max(extent, (int) Math.ceil(Math.max(Math.abs(s.x()), Math.abs(s.z())) + s.radius() * 1.4 + s.height() * 0.3 + 6));
        }
        Voxels v = new Voxels(extent, lo, hi);
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < shards.length; i++) {
            Shard s = shards[i];
            shard(v, s, (i < ground.length ? ground[i] : 0) + s.lift(), seed + i * 7919L);
        }
        // rubble drifting among them
        int bits = 14 + r.nextInt(14);
        for (int k = 0; k < bits; k++) {
            Shard near = shards[r.nextInt(shards.length)];
            double a = r.nextDouble() * Math.PI * 2, d = near.radius() + 2 + r.nextDouble() * 6;
            int x = (int) Math.round(near.x() + Math.cos(a) * d), z = (int) Math.round(near.z() + Math.sin(a) * d);
            int gi = indexOf(shards, near);
            int y = (gi < ground.length ? ground[gi] : 0) + near.lift() + r.nextInt(Math.max(1, near.height() + 6)) - 3;
            byte code = r.nextInt(3) == 0 ? FAULT : SHALE;
            v.fill(x, y, z, code);
            if (r.nextBoolean()) v.fill(x + (r.nextBoolean() ? 1 : 0), y + (r.nextBoolean() ? 1 : 0), z, code);
        }
        v.seal();
        return v;
    }

    private static int indexOf(Shard[] shards, Shard s) {
        for (int i = 0; i < shards.length; i++) if (shards[i] == s) return i;
        return 0;
    }

    /**
     * One shard, its tip at voxel y {@code tip}: layers from the top down, each a lobed outline narrowing to the point,
     * the axis leaning by the shard's tilt. Grassed shards have a turf top and roots hanging from the overhanging rim.
     */
    static void shard(Voxels v, Shard s, int tip, long seed) {
        int top = tip + s.height() - 1;
        for (int k = 0; k < s.height(); k++) {
            int y = top - k;
            double t = k / (double) s.height(); // 0 at the top, toward 1 at the tip
            double rad = s.radius() * Math.pow(1 - t, 0.85) * (k == 0 ? 0.92 : 1.0);
            double cx = s.x() + s.tiltX() * k, cz = s.z() + s.tiltZ() * k;
            int span = (int) Math.ceil(rad * 1.4) + 1;
            boolean any = false;
            for (int x = (int) Math.floor(cx) - span; x <= (int) Math.ceil(cx) + span; x++) {
                for (int z = (int) Math.floor(cz) - span; z <= (int) Math.ceil(cz) + span; z++) {
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                    double a = Math.atan2(dz, dx);
                    double lobe = 1 + 0.2 * Math.sin(3 * a + s.phase()) + 0.1 * Math.sin(5 * a + s.phase() * 2.3);
                    double edge = rad * lobe + (Voxels.hash(seed, x, y, z) - 0.5) * 0.6;
                    if (dx * dx + dz * dz > edge * edge) continue;
                    any = true;
                    byte code;
                    if (k == s.height() - 1) code = SEAM; // the tip: the seam's light
                    else if (s.grassy() && k == 0) code = GRASS;
                    else if (s.grassy() && k <= 2) code = k == 2 && Voxels.hash(seed + 3, x, y, z) < 0.3 ? COARSE : DIRT;
                    else if (k == s.height() - 1 || t > 0.86) code = SEAM;
                    else if (t > 0.5 || Voxels.hash(seed + 1, x, y, z) < 0.1 + 0.25 * t) code = FAULT;
                    else code = SHALE;
                    v.set(x, y, z, code);
                }
            }
            if (!any) v.set((int) Math.floor(cx), y, (int) Math.floor(cz), k == s.height() - 1 || t > 0.8 ? SEAM : FAULT); // the point
        }
        if (!s.grassy()) return;
        // roots hang from the turf's rim where the shard narrows away beneath it
        int span = (int) Math.ceil(s.radius() * 1.4) + 2;
        for (int x = (int) Math.floor(s.x()) - span; x <= (int) Math.ceil(s.x()) + span; x++) {
            for (int z = (int) Math.floor(s.z()) - span; z <= (int) Math.ceil(s.z()) + span; z++) {
                for (int y = top - 2; y <= top; y++) {
                    byte c = v.get(x, y, z);
                    if ((c != DIRT && c != COARSE) || v.get(x, y - 1, z) != Voxels.EMPTY) continue;
                    if (Voxels.hash(seed + 5, x, y, z) > 0.45) continue;
                    v.set(x, y - 1, z, ROOTS); // one block: hanging roots only hold to soil
                }
            }
        }
    }
}
