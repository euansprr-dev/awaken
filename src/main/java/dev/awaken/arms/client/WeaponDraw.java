package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.arms.WeaponMesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A weapon's mesh drawn by another part of Arms, not as an item: the War Anvil's work in the making and its hammer and
 * tongs (SPEC §46.3). Through the weapon shader, or vanilla's entity light if it didn't build (then without the heat).
 */
public final class WeaponDraw {
    private WeaponDraw() {}

    /** The weapon shader's heat mode on UV1.y: x carries the heat, y's low byte its front along the blade. */
    public static final int HEAT = 512, QUENCH = 1024;

    /** A weapon's mesh by its id, or a tool's ({@code anvil/hammer}, {@code anvil/tongs}); null before the resources load. */
    public static @Nullable WeaponMesh mesh(String id) {
        return WeaponMeshes.get(id);
    }

    /** Whether the weapon shader is drawing (its heat with it). */
    public static boolean shaded() {
        return WeaponPipelines.ok();
    }

    /**
     * Draws a mesh with the shader's state as UV1 ({@code x} and {@code y}: energy and flash, or with {@link #HEAT} heat
     * and its front), only from {@code from} along it on (0 all of it).
     */
    public static void draw(SubmitNodeCollector out, PoseStack ps, WeaponMesh mesh, Identifier texture, int light, int x, int y, float from) {
        boolean ours = WeaponPipelines.ok();
        int packed = ours ? x | y << 16 : OverlayTexture.NO_OVERLAY;
        int cut = Math.round(Math.max(0F, from) * 255);
        out.submitCustomGeometry(ps, ours ? WeaponPipelines.weapon(texture) : WeaponPipelines.fallback(texture),
                (pose, buffer) -> WeaponRenderer.emit(mesh, pose, buffer, light, packed, ours, cut));
    }

    /**
     * A mesh whose corners its caller writes itself (the anvil reshapes its work as it's made, SPEC §46.3): {@code overlay}
     * is UV1 as the shader takes it; {@code ours}, the weapon shader is drawing, else vanilla's quads (each triangle's last
     * corner twice) and white.
     */
    @FunctionalInterface
    public interface Corners {
        void emit(PoseStack.Pose pose, VertexConsumer buffer, int overlay, boolean ours);
    }

    /** Draws corners written by {@code corners} with the shader's state as UV1, as {@link #draw} does a mesh. */
    public static void draw(SubmitNodeCollector out, PoseStack ps, Identifier texture, int x, int y, Corners corners) {
        boolean ours = WeaponPipelines.ok();
        int packed = ours ? x | y << 16 : OverlayTexture.NO_OVERLAY;
        out.submitCustomGeometry(ps, ours ? WeaponPipelines.weapon(texture) : WeaponPipelines.fallback(texture),
                (pose, buffer) -> corners.emit(pose, buffer, packed, ours));
    }
}
