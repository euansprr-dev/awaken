package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;

/** A clean Haymaker's target folds round the fist for a beat before it flies (SPEC §50.3): any body, drawn bent and squashed along the punch. */
final class FxFold {
    private FxFold() {}

    private static final int SLOTS = 8, LIFE = 9;
    private static final int[] ID = new int[SLOTS], START = new int[SLOTS];
    private static final float[] DX = new float[SLOTS], DZ = new float[SLOTS];
    private static int active;
    private static final ContextKey<float[]> FOLD = new ContextKey<>(Awaken.id("power_fold"));
    private static final Quaternionf Q = new Quaternionf();
    private static boolean pushed;

    static {
        java.util.Arrays.fill(ID, -1);
    }

    static void start(int entity, float dx, float dz) {
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (entity < 0 || len < 1e-3f || !PowerFxConfig.animations() || FxWorld.level == dev.awaken.powers.fx.FxBudget.Level.MINIMAL) return;
        int slot = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (ID[i] == entity || ID[i] < 0) {
                slot = i;
                break;
            }
            if (START[i] < START[slot]) slot = i;
        }
        if (ID[slot] < 0) active++;
        ID[slot] = entity;
        START[slot] = FxWorld.ticks;
        DX[slot] = dx / len;
        DZ[slot] = dz / len;
    }

    static void tick() {
        if (active == 0) return;
        for (int i = 0; i < SLOTS; i++) {
            if (ID[i] >= 0 && FxWorld.ticks - START[i] > LIFE) {
                ID[i] = -1;
                active--;
            }
        }
    }

    static void reset() {
        java.util.Arrays.fill(ID, -1);
        active = 0;
    }

    /** Render-state modifier: remembers the fold for the body being extracted (render states carry no entity id). */
    static void modify(Entity e, EntityRenderState s) {
        if (active == 0 || !(e instanceof LivingEntity)) return;
        for (int i = 0; i < SLOTS; i++) {
            if (ID[i] == e.getId()) {
                float t = (FxWorld.ticks - START[i] + s.partialTick) / LIFE;
                // snaps shut in the first tick or two, then eases back open as it flies
                float amount = t < 0.12f ? t / 0.12f : Math.max(0, 1 - (t - 0.12f) / 0.88f);
                s.setRenderData(FOLD, new float[]{amount * amount * (3 - 2 * amount), DX[i], DZ[i]});
                return;
            }
        }
    }

    static void onPre(RenderLivingEvent.Pre<?, ?, ?> e) {
        pushed = false;
        if (active == 0 || e.isCanceled()) return;
        LivingEntityRenderState s = e.getRenderState();
        float[] f = s.getRenderData(FOLD);
        if (f == null || f[0] <= 0.01f) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        pushed = true;
        float mid = s.boundingBoxHeight * 0.5f;
        ps.translate(0, mid, 0);
        // bent over the fist: the top and bottom thrown toward you, the middle driven away along the punch
        ps.translate(f[1] * 0.18f * f[0], 0, f[2] * 0.18f * f[0]);
        ps.rotate(Q.rotationAxis(0.65f * f[0], -f[2], 0, f[1]));
        ps.scale(1 + 0.12f * f[0], 1 - 0.2f * f[0], 1 + 0.12f * f[0]);
        ps.translate(0, -mid, 0);
    }

    static void onPost(RenderLivingEvent.Post<?, ?, ?> e) {
        if (pushed) {
            e.getPoseStack().popPose();
            pushed = false;
        }
    }
}
