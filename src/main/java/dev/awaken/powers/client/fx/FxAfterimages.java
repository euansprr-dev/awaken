package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.powers.fx.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;

/**
 * Afterimages: a frozen, tinted, glowing copy of a player's pose left behind by dashes and Afterimage. The pose is
 * captured once (the player's own render state at that moment, cast animation included) and drawn with the player
 * model until it fades. The most expensive layer, so it has the tightest cap.
 */
final class FxAfterimages {
    private FxAfterimages() {}

    private static final int CAP = FxBudget.afterimageCap(FxBudget.Level.FULL);

    private static final class Ghost {
        boolean live;
        int age, life;
        double x, y, z;
        float bodyRot, drift;
        int colour;
        AvatarRenderState state;
        AvatarRenderer<?> renderer;
        Identifier skin;
    }

    private static final Ghost[] G = new Ghost[CAP];
    private static int live;
    private static float pt;

    static {
        for (int i = 0; i < CAP; i++) G[i] = new Ghost();
    }

    static boolean any() {
        return live > 0;
    }

    static void clear() {
        for (Ghost g : G) {
            g.live = false;
            g.state = null;
        }
        live = 0;
    }

    /** Leaves a ghost of a player where they stand now. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void capture(Entity e, int life, int colour, float drift) {
        if (!(e instanceof AbstractClientPlayer player)) return;
        int cap = FxBudget.afterimageCap(FxWorld.level);
        if (cap <= 0) return;
        Ghost slot = null;
        int alive = 0;
        Ghost oldest = null;
        for (Ghost g : G) {
            if (g.live) {
                alive++;
                if (oldest == null || g.age > oldest.age) oldest = g;
            } else if (slot == null) {
                slot = g;
            }
        }
        if (alive >= cap || slot == null) slot = oldest;
        if (slot == null) return;
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof AvatarRenderer<?> avatar)) return;
        AvatarRenderState state;
        try {
            state = (AvatarRenderState) renderer.createRenderState(player, 1f);
        } catch (RuntimeException ex) {
            return;
        }
        if (state.skin == null) return;
        slot.live = true;
        slot.age = 0;
        slot.life = Math.max(2, life);
        slot.x = player.getX();
        slot.y = player.getY();
        slot.z = player.getZ();
        slot.bodyRot = state.bodyRot;
        slot.drift = drift;
        slot.colour = colour;
        slot.state = state;
        slot.renderer = avatar;
        slot.skin = state.skin.body().texturePath();
    }

    static void tick() {
        int n = 0;
        for (Ghost g : G) {
            if (!g.live) continue;
            if (++g.age >= g.life) {
                g.live = false;
                g.state = null;
                continue;
            }
            n++;
        }
        live = n;
    }

    static void extract(float partial) {
        pt = partial;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void submit(PoseStack ps, SubmitNodeCollector out, double cx, double cy, double cz) {
        if (live == 0) return;
        for (Ghost g : G) {
            if (!g.live || g.state == null) continue;
            float t = (g.age + pt) / g.life;
            double gx = g.x - cx, gy = g.y + 1 - cy, gz = g.z - cz;
            // a ghost of yourself left where you stand would fill a first-person view: fade ghosts near the camera
            float a = (1 - t) * (1 - t) * 0.55f * FxRender.near((float) Math.sqrt(gx * gx + gy * gy + gz * gz));
            if (a <= 0.02f) continue;
            int tint = ((int) (a * 255) << 24) | (g.colour & 0xFFFFFF);
            ps.pushPose();
            ps.translate(g.x - cx, g.y - cy + g.drift * t, g.z - cz);
            ps.rotateDegrees(Axis.YP, 180f - g.bodyRot);
            ps.scale(-1f, -1f, 1f);
            ps.scale(0.9375f, 0.9375f, 0.9375f);
            ps.translate(0f, -1.501f, 0f);
            Model model = g.renderer.getModel();
            out.submitModel(model, g.state, ps, RenderTypes.entityTranslucentEmissive(g.skin), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, tint, null, 0);
            ps.popPose();
        }
    }
}
