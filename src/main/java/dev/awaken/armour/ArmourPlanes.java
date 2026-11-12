package dev.awaken.armour;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The check behind the armour's depth steps (SPEC §44.15). A set is drawn as many meshes, one per piece and bone, and
 * two faces that lie in one plane, face the same way and overlap fight for every pixel: they flicker. The art kit
 * builds every mesh a hair bigger than designed, each by its own step, so that none do. This walks the meshes as they
 * ship, stands them in the figure the way each drawer does, and lists every pair of triangles that still shares a
 * plane. Plain numbers, no client classes: a GameTest on a server runs it.
 */
final class ArmourPlanes {
    private ArmourPlanes() {}

    /** The smallest step between two meshes (texels): tools/art/specs/armour_sets.py's STEP. */
    static final double STEP = 1 / 128.0;
    /** Two faces nearer than this along their normal count as one plane; lying over each other deeper than EPS counts. */
    private static final double NEAR = 0.75 * STEP, EPS = 0.02;
    /** How far the stands hold the arms out (FittingStandRenderer.rest), radians. */
    private static final double ARM_REST = 0.1;

    /** A piece, then the bones it rides on (ArmourLayer; the kit's BONES). */
    private static final String[][] BONES = {
            {"helmet", "head"},
            {"chestplate", "body", "right_arm", "left_arm", "cape0", "cape1", "cape2"},
            {"leggings", "body", "right_leg", "left_leg", "front_cloth", "back_cloth"},
            {"boots", "right_leg", "left_leg"}};

    /** Worn by a player at rest (the skin under it); on an armour stand (its leg pieces a texel higher); on a Fitting Stand, in the Hall, in the hand (the arms held out). */
    enum Pose { WORN, STAND, FIT }

    /** A triangle in the figure (texels: feet at y 0, x to the wearer's left, z forward), its unit normal and its plane. */
    private record Tri(String mesh, double[] p, double[] n, double d) {}

    /** What a walk saw: how many triangles it stood in the figure over all poses, and the pairs that share a plane (one line each). */
    record Walk(int triangles, List<String> shared) {}

    /** Every pair of triangles of a set that shares a plane, in every pose; at most `most` are listed. */
    static Walk walk(String set, int most) throws IOException {
        List<String> out = new ArrayList<>();
        int seen = 0;
        for (Pose pose : Pose.values()) {
            List<Tri> tris = new ArrayList<>();
            for (String[] piece : BONES) {
                for (int b = 1; b < piece.length; b++) {
                    String base = "/assets/awaken/armour/" + set + "/" + piece[0] + "_" + piece[b];
                    if (exists(base + "_live0.mesh")) {
                        // a bone with living parts: its still mesh and each living one (the plain file is all of them together)
                        load(base + "_still.mesh", piece[b], pose, tris);
                        for (int k = 0; exists(base + "_live" + k + ".mesh"); k++) load(base + "_live" + k + ".mesh", piece[b], pose, tris);
                    } else {
                        load(base + ".mesh", piece[b], pose, tris);
                    }
                    load(base + "_awakened.mesh", piece[b], pose, tris);
                }
            }
            if (pose == Pose.WORN) skin(tris);
            seen += tris.size();
            find(tris, pose, out, most);
        }
        return new Walk(seen, out);
    }

    /**
     * The search does find: two triangles of two meshes in one plane, one over the other, are a pair; the same two a
     * step apart are not. (A walk that finds nothing only means something if the search can.)
     */
    static boolean finds() {
        for (int k = 0; k < 2; k++) {
            double z = k * STEP;
            List<Tri> tris = new ArrayList<>();
            add("one", new double[]{0, 0, 0}, new double[]{2, 0, 0}, new double[]{0, 2, 0}, tris);
            add("other", new double[]{0.5, 0.5, z}, new double[]{2.5, 0.5, z}, new double[]{0.5, 2.5, z}, tris);
            List<String> out = new ArrayList<>();
            find(tris, Pose.WORN, out, 4);
            if (out.size() != (k == 0 ? 1 : 0)) return false;
        }
        return true;
    }

    private static boolean exists(String path) throws IOException {
        try (InputStream in = ArmourPlanes.class.getResourceAsStream(path)) {
            return in != null;
        }
    }

    // ---- the meshes, stood in the figure -----------------------------------------------------------------------------

    /** Reads a mesh (tools/art/mesh.py: a 16-byte header, then 20 bytes a corner, its place first) if it ships. */
    private static void load(String path, String bone, Pose pose, List<Tri> out) throws IOException {
        byte[] data;
        try (InputStream in = ArmourPlanes.class.getResourceAsStream(path)) {
            if (in == null) return;
            data = in.readAllBytes();
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 16 || data[0] != 'A' || data[1] != 'W' || data[2] != 'M' || data[3] != 'S') throw new IOException(path + " is not a mesh");
        int n = buf.getInt(8);
        if (n < 0 || n % 3 != 0 || 16 + n * 20L > data.length) throw new IOException(path + ": " + n + " corners do not fit");
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".mesh".length());
        double[][] corner = new double[3][];
        for (int i = 0; i < n; i++) {
            int at = 16 + i * 20;
            // part space (blocks; y down, z back) to the bone's design space (texels; y up, z forward), then into the figure
            corner[i % 3] = place(bone, pose, buf.getFloat(at) * 16.0, -buf.getFloat(at + 4) * 16.0, -buf.getFloat(at + 8) * 16.0);
            if (i % 3 == 2) add(name, corner[0], corner[1], corner[2], out);
        }
    }

    private static double[] place(String bone, Pose pose, double x, double y, double z) {
        switch (bone) {
            case "head", "body" -> y += 24;
            case "right_arm", "left_arm" -> {
                if (pose == Pose.FIT) {
                    double a = bone.equals("right_arm") ? -ARM_REST : ARM_REST, c = Math.cos(a), s = Math.sin(a);
                    double rx = c * x - s * y, ry = s * x + c * y;
                    x = rx;
                    y = ry;
                }
                x += bone.equals("right_arm") ? -5 : 5;
                y += 22;
            }
            case "right_leg", "left_leg" -> {
                x += bone.equals("right_leg") ? -1.9 : 1.9;
                y += pose == Pose.STAND ? 13 : 12;
            }
            case "front_cloth" -> {
                y += 11;
                z += 5;
            }
            case "back_cloth" -> {
                y += 11;
                z -= 4;
            }
            default -> {
                // a cape's link: hung from the yoke 6 degrees back, each further link 4 more and a link's length (6) down the last
                int link = bone.charAt(4) - '0';
                double oy = 0, oz = 0;
                for (int j = 0; j < link; j++) {
                    double a = Math.toRadians(6 + 4 * j);
                    oy += 6 * -Math.cos(a);
                    oz += 6 * -Math.sin(a);
                }
                double a = Math.toRadians(6 + 4 * link), c = Math.cos(a), s = Math.sin(a);
                double ry = c * y - s * z, rz = s * y + c * z;
                y = 23.5 + oy + ry;
                z = -4 + oz + rz;
            }
        }
        return new double[]{x, y, z};
    }

    private static void add(String mesh, double[] a, double[] b, double[] c, List<Tri> out) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9) return; // no area
        double[] n = {nx / len, ny / len, nz / len};
        out.add(new Tri(mesh, new double[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]}, n, n[0] * a[0] + n[1] * a[1] + n[2] * a[2]));
    }

    /** The player's own skin (its inner layer; the outer one is hidden under armour), each bone's box in the figure. */
    private static void skin(List<Tri> out) {
        double[][] boxes = {{-4, 4, 24, 32, -4, 4}, {-4, 4, 12, 24, -2, 2}, {-8, -4, 12, 24, -2, 2}, {4, 8, 12, 24, -2, 2},
                {-3.9, 0.1, 0, 12, -2, 2}, {-0.1, 3.9, 0, 12, -2, 2}};
        String[] names = {"skin_head", "skin_body", "skin_right_arm", "skin_left_arm", "skin_right_leg", "skin_left_leg"};
        for (int k = 0; k < boxes.length; k++) {
            double[] b = boxes[k];
            for (int axis = 0; axis < 3; axis++) {
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                for (int side = 0; side < 2; side++) {
                    double[][] q = new double[4][3];
                    for (int i = 0; i < 4; i++) {
                        q[i][axis] = b[axis * 2 + side];
                        // counter-clockwise seen from outside: u then v for the far side, v then u for the near one
                        boolean hiU = side == 1 ? (i == 1 || i == 2) : (i == 2 || i == 3), hiV = side == 1 ? (i == 2 || i == 3) : (i == 1 || i == 2);
                        q[i][u] = b[u * 2 + (hiU ? 1 : 0)];
                        q[i][v] = b[v * 2 + (hiV ? 1 : 0)];
                    }
                    add(names[k], q[0], q[1], q[2], out);
                    add(names[k], q[0], q[2], q[3], out);
                }
            }
        }
    }

    // ---- the search ---------------------------------------------------------------------------------------------------

    private static void find(List<Tri> tris, Pose pose, List<String> out, int most) {
        // faces that look the same way together, then along their normal: only neighbours in that order can share a plane
        Map<Long, List<Tri>> facing = new HashMap<>();
        for (Tri t : tris) {
            long key = (Math.round(t.n[0] * 200) + 500) * 1_000_000L + (Math.round(t.n[1] * 200) + 500) * 1_000L + Math.round(t.n[2] * 200) + 500;
            facing.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        for (List<Tri> list : facing.values()) {
            list.sort(Comparator.comparingDouble(Tri::d));
            for (int a = 0; a < list.size(); a++) {
                Tri s = list.get(a);
                for (int b = a + 1; b < list.size() && list.get(b).d - s.d < NEAR; b++) {
                    Tri t = list.get(b);
                    if (s.mesh.startsWith("skin") && t.mesh.startsWith("skin")) continue; // the legs' skin down the middle: vanilla's own
                    if (s.n[0] * t.n[0] + s.n[1] * t.n[1] + s.n[2] * t.n[2] < 0.9999 || !boxesMeet(s.p, t.p)) continue;
                    if (overlap(s.p, t.p, s.n) > EPS) {
                        if (out.size() < most) out.add(String.format(java.util.Locale.ROOT, "%s and %s share a plane (%s, near %.2f %.2f %.2f)",
                                s.mesh, t.mesh, pose.name().toLowerCase(java.util.Locale.ROOT),
                                (s.p[0] + s.p[3] + s.p[6]) / 3, (s.p[1] + s.p[4] + s.p[7]) / 3, (s.p[2] + s.p[5] + s.p[8]) / 3));
                        else return;
                    }
                }
            }
        }
    }

    private static boolean boxesMeet(double[] a, double[] b) {
        for (int k = 0; k < 3; k++) {
            double loA = Math.min(a[k], Math.min(a[k + 3], a[k + 6])), hiA = Math.max(a[k], Math.max(a[k + 3], a[k + 6]));
            double loB = Math.min(b[k], Math.min(b[k + 3], b[k + 6])), hiB = Math.max(b[k], Math.max(b[k + 3], b[k + 6]));
            if (Math.min(hiA, hiB) - Math.max(loA, loB) < -1e-6) return false;
        }
        return true;
    }

    /** How deep two triangles in a plane (normal n) lie over each other: over EPS, or the first depth found under it. */
    private static double overlap(double[] a, double[] b, double[] n) {
        double best = 1e9;
        for (int which = 0; which < 2; which++) {
            double[] q = which == 0 ? a : b;
            for (int k = 0; k < 3; k++) {
                int i = k * 3, j = ((k + 1) % 3) * 3;
                double ex = q[j] - q[i], ey = q[j + 1] - q[i + 1], ez = q[j + 2] - q[i + 2];
                // across this edge, in the plane
                double ax = n[1] * ez - n[2] * ey, ay = n[2] * ex - n[0] * ez, az = n[0] * ey - n[1] * ex;
                double len = Math.sqrt(ax * ax + ay * ay + az * az);
                if (len < 1e-9) continue;
                double loA = 1e18, hiA = -1e18, loB = 1e18, hiB = -1e18;
                for (int c = 0; c < 9; c += 3) {
                    double pa = (a[c] * ax + a[c + 1] * ay + a[c + 2] * az) / len, pb = (b[c] * ax + b[c + 1] * ay + b[c + 2] * az) / len;
                    loA = Math.min(loA, pa);
                    hiA = Math.max(hiA, pa);
                    loB = Math.min(loB, pb);
                    hiB = Math.max(hiB, pb);
                }
                best = Math.min(best, Math.min(hiA, hiB) - Math.max(loA, loB));
                if (best <= EPS) return best;
            }
        }
        return best;
    }
}
