package dev.awaken.gear.premium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.gear.premium.HoodMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Veilwoven's hero hood (pack/armour-hero-piece, never merged): the mesh from tools/art/specs/gear_hero_hood.py, drawn on the
 * head as our own geometry (smooth normals, so it is shaded like cloth, not boxes). Loaded once per resource reload; if it fails,
 * the helmet draws as it ships.
 */
final class HeroHood {
    private HeroHood() {}

    static final Identifier MESH = Awaken.id(HoodMesh.PATH);
    static final Identifier TEXTURE = Awaken.id(HoodMesh.TEXTURE);

    private static float @Nullable [] data;
    private static boolean failed;
    // the geometry callback runs on the render thread only
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NRM = new Vector3f();

    static void reset() {
        data = null;
        failed = false;
    }

    static float @Nullable [] data() {
        if (data == null && !failed) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(MESH);
            try {
                if (resource.isEmpty()) throw new IOException("missing " + MESH);
                HoodMesh mesh;
                try (InputStream in = resource.get().open()) {
                    mesh = HoodMesh.read(in);
                }
                List<String> bad = mesh.problems();
                if (!bad.isEmpty()) throw new IOException(String.join("; ", bad));
                data = mesh.data();
            } catch (IOException | RuntimeException e) {
                failed = true;
                Awaken.LOG.warn("[gear] the hero hood is unavailable, drawing the helmet as it ships: {}", e.toString());
            }
        }
        return data;
    }

    /** The mesh in the head's space: positions in model units (16 to a block), normals through the pose. */
    static void emit(PoseStack.Pose pose, VertexConsumer out, float[] d, int light) {
        Matrix4f m = pose.pose();
        for (int k = 0; k < d.length; k += HoodMesh.STRIDE) {
            m.transformPosition(d[k] / 16.0F, d[k + 1] / 16.0F, d[k + 2] / 16.0F, POS);
            pose.transformNormal(d[k + 3], d[k + 4], d[k + 5], NRM);
            out.addVertex(POS.x, POS.y, POS.z, -1, d[k + 6], d[k + 7], OverlayTexture.NO_OVERLAY, light, NRM.x, NRM.y, NRM.z);
        }
    }
}
