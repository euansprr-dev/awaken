package dev.awaken.armour.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One piece's mesh on one bone (docs/armour/PLAN.md), as tools/art/specs/armour_sets.py writes it in the weapons'
 * format (tools/art/mesh.py): de-indexed triangles in the bone's part space (blocks; y down, z back), each corner with
 * its normal, its texel in the albedo half of the set's texture and how far up the figure it lies (the sheen and the
 * molten pulse climb it). The weapons' glow anchors and streams, if any, are skipped.
 */
record ArmourMesh(int accent, float[] pos, byte[] normal, float[] uv, byte[] along) {
    private static final int MAGIC = 'A' | 'W' << 8 | 'M' << 16 | 'S' << 24;

    int vertices() {
        return pos.length / 3;
    }

    static ArmourMesh read(InputStream in) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (b.getInt() != MAGIC) throw new IOException("not a mesh");
            int version = Short.toUnsignedInt(b.getShort());
            if (version != 1) throw new IOException("mesh version " + version + ", expected 1");
            b.getShort();
            int n = b.getInt();
            if (n <= 0 || n % 3 != 0 || n > 200_000) throw new IOException("mesh with " + n + " corners");
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
            return new ArmourMesh(accent, pos, normal, uv, along);
        } catch (BufferUnderflowException e) {
            throw new IOException("mesh cut short", e);
        }
    }
}
