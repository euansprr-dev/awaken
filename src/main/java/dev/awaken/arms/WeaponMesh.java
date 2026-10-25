package dev.awaken.arms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * A weapon's sculpted mesh (SPEC §24 "Weapons"), as tools/art/mesh.py writes it to
 * {@code assets/awaken/arms/weapons/<id>.mesh}: de-indexed triangles in item space (the 0..1 box of a JSON item model,
 * so vanilla's hand transforms hold it), each corner with its normal, its texel in the albedo half of the weapon's
 * texture and how far along the weapon it lies (the shader's sheen runs on it); then the points that glow.
 * Plain data, readable on either side: the client draws it, the GameTests check it.
 */
public record WeaponMesh(int accent, float[] pos, byte[] normal, float[] uv, byte[] along, List<Anchor> anchors) {
    public static final int VERSION = 1;
    private static final int MAGIC = 'A' | 'W' << 8 | 'M' << 16 | 'S' << 24;

    /** A point that glows (a gem, a rune's heart): where, how big, its colour (rgb) and its kind. */
    public record Anchor(float x, float y, float z, float radius, int rgb, int kind) {}

    public int vertices() {
        return pos.length / 3;
    }

    /** The mesh's bounds: min x, y, z, max x, y, z. */
    public float[] bounds() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < pos.length; i++) {
            b[i % 3] = Math.min(b[i % 3], pos[i]);
            b[3 + i % 3] = Math.max(b[3 + i % 3], pos[i]);
        }
        return b;
    }

    public static WeaponMesh read(InputStream in) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (b.getInt() != MAGIC) throw new IOException("not a weapon mesh");
            int version = Short.toUnsignedInt(b.getShort());
            if (version != VERSION) throw new IOException("weapon mesh version " + version + ", expected " + VERSION);
            b.getShort(); // flags, none yet
            int n = b.getInt();
            if (n <= 0 || n % 3 != 0 || n > 200_000) throw new IOException("weapon mesh with " + n + " corners");
            int accent = (b.get() & 0xFF) << 16 | (b.get() & 0xFF) << 8 | b.get() & 0xFF;
            b.get();
            float[] pos = new float[n * 3], uv = new float[n * 2];
            byte[] normal = new byte[n * 3], along = new byte[n];
            for (int i = 0; i < n; i++) {
                pos[i * 3] = b.getFloat();
                pos[i * 3 + 1] = b.getFloat();
                pos[i * 3 + 2] = b.getFloat();
                normal[i * 3] = b.get();
                normal[i * 3 + 1] = b.get();
                normal[i * 3 + 2] = b.get();
                along[i] = b.get();
                uv[i * 2] = Short.toUnsignedInt(b.getShort()) / 65535F;
                uv[i * 2 + 1] = Short.toUnsignedInt(b.getShort()) / 65535F;
            }
            int k = Short.toUnsignedInt(b.getShort());
            List<Anchor> anchors = new ArrayList<>(k);
            for (int i = 0; i < k; i++) {
                float x = b.getFloat(), y = b.getFloat(), z = b.getFloat(), r = b.getFloat();
                int rgb = (b.get() & 0xFF) << 16 | (b.get() & 0xFF) << 8 | b.get() & 0xFF;
                anchors.add(new Anchor(x, y, z, r, rgb, b.get() & 0xFF));
            }
            if (b.hasRemaining()) throw new IOException("weapon mesh with " + b.remaining() + " bytes left over");
            return new WeaponMesh(accent, pos, normal, uv, along, List.copyOf(anchors));
        } catch (BufferUnderflowException e) {
            throw new IOException("weapon mesh cut short", e);
        }
    }
}
