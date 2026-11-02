package dev.awaken.arms.anvil.client;

import dev.awaken.arms.anvil.WarAnvilBlockEntity;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Your hands at the War Anvil (SPEC §46.3; a SPEC §24 world source, {@code arms_anvil}, priority 200): the main hand on
 * the hammer's grip wherever the swing has it, the off hand on the tongs' handles while they're within reach, both on
 * the hilt as you draw the weapon out of the stone. Where the scene puts them ({@link AnvilScene}).
 */
final class AnvilHands {
    private AnvilHands() {}

    private static final AnvilScene.Placed AT = new AnvilScene.Placed();

    static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        WarAnvilBlockEntity be = AnvilClient.sceneAnvil();
        if (be == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) return false;
        AnvilScene.place(be, AnvilClient.now(pt), true, AT);
        if (AT.mainW <= 0F && AT.offW <= 0F) return false;
        var cam = mc.gameRenderer.mainCamera();
        Vec3 eye = cam.position();
        Vector3fc left = cam.leftVector();
        double fx = -left.z(), fz = left.x(); // the view's forward, level (left × up)
        double fl = Math.sqrt(fx * fx + fz * fz);
        rig.fx = fl < 1e-4 ? 0 : fx / fl;
        rig.fz = fl < 1e-4 ? 1 : fz / fl;
        BlockPos pos = be.getBlockPos();
        boolean leftMain = mc.player.getMainArm() == HumanoidArm.LEFT;
        double dist = 0;
        int shown = 0;
        for (int h = 0; h < 2; h++) {
            boolean main = h == 0;
            float w = main ? AT.mainW : AT.offW;
            if (w <= 0F) continue;
            Vector3f fist = main ? AT.mainFist : AT.offFist;
            boolean right = main != leftMain;
            double side = right ? -1 : 1; // along the camera's left
            double sx = eye.x + left.x() * side * 0.33 - rig.fx * 0.1, sy = eye.y - 0.4, sz = eye.z + left.z() * side * 0.33 - rig.fz * 0.1;
            double hx = pos.getX() + fist.x, hy = pos.getY() + fist.y, hz = pos.getZ() + fist.z;
            rig.reach(right ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, sx, sy, sz, hx, hy, hz, FirstPerson.Rig.ARM_REACH, 0, 1, 0, w);
            dist += Math.sqrt((hx - eye.x) * (hx - eye.x) + (hy - eye.y) * (hy - eye.y) + (hz - eye.z) * (hz - eye.z));
            shown++;
        }
        // drawn about as far off as the tools are, so a fist reads the size of the haft it closes on
        rig.handDistance = Mth.clamp(dist / shown, 0.75, 1.2);
        return true;
    }
}
