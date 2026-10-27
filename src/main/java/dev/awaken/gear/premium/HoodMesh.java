package dev.awaken.gear.premium;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Veilwoven's hero hood (branch pack/armour-hero-piece, never merged), written by tools/art/specs/gear_hero_hood.py: a real mesh
 * instead of boxes. Plain data so a GameTest can check it on the server. Quads, four vertices each, eight floats a vertex:
 * x y z (head-local model units, y down), the smooth normal, and u v in the atlas (albedo half, u below 0.5).
 */
public record HoodMesh(float[] data) {
    public static final String PATH = "gear_premium/veilwoven_hood.mesh";
    public static final String TEXTURE = "textures/entity/equipment/premium/veilwoven_hood.png";
    public static final int SCHEMA = 1;
    public static final int STRIDE = 8;
    public static final int ATLAS_W = 1024, ATLAS_H = 512;

    public static HoodMesh read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(in));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (!"AWHM".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("not a hood mesh");
        int schema = d.readInt();
        if (schema != SCHEMA) throw new IOException("hood mesh schema " + schema + ", want " + SCHEMA);
        int quads = d.readInt();
        if (quads <= 0 || quads > 100_000) throw new IOException("hood mesh with " + quads + " quads");
        float[] f = new float[quads * 4 * STRIDE];
        for (int i = 0; i < f.length; i++) f[i] = d.readFloat();
        return new HoodMesh(f);
    }

    public int quads() {
        return data.length / (4 * STRIDE);
    }

    /** What's wrong with it, or nothing: finite, unit normals, inside a head-sized box, uvs in the albedo half. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        for (int k = 0; k < data.length; k += STRIDE) {
            for (int c = 0; c < STRIDE; c++) {
                if (!Float.isFinite(data[k + c])) {
                    out.add("vertex " + k / STRIDE + " is not finite");
                    return out;
                }
            }
            if (Math.abs(data[k]) > 9 || data[k + 1] < -13 || data[k + 1] > 3 || Math.abs(data[k + 2]) > 10) {
                out.add("vertex " + k / STRIDE + " is outside the head's reach");
                return out;
            }
            float n = data[k + 3] * data[k + 3] + data[k + 4] * data[k + 4] + data[k + 5] * data[k + 5];
            if (Math.abs(n - 1) > 0.01F) {
                out.add("vertex " + k / STRIDE + " has a normal of length " + Math.sqrt(n));
                return out;
            }
            if (data[k + 6] < 0 || data[k + 6] > 0.5F || data[k + 7] < 0 || data[k + 7] > 1) {
                out.add("vertex " + k / STRIDE + " samples outside the albedo half");
                return out;
            }
        }
        return out;
    }
}
