package dev.awaken.body.client;

import dev.awaken.body.BodyClips;
import dev.awaken.body.BodyContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Runner's grit and footfalls on this client: every step of a wall-run knocks dust off the wall (its own colour)
 * and sounds against it; other players' runs do the same from their clip. Fixed slots, nothing allocated per tick.
 */
final class RunnerFx {
    private RunnerFx() {}

    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    // other players running along walls: entity, wall on the left, age
    private static final int[] RUNNER = new int[16], AGE = new int[16];
    private static final boolean[] LEFT = new boolean[16];

    static {
        java.util.Arrays.fill(RUNNER, -1);
    }

    /** The colour of the wall block at a point, softened toward grey like the body's dust. */
    private static int wallTint(ClientLevel level, double x, double y, double z) {
        POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState s = level.getBlockState(POS);
        int col = s.isAir() ? 0x8A8378 : s.getMapColor(level, POS).col;
        if (col == 0) col = 0x8A8378;
        int r = ((col >> 16) & 255) * 7 / 10 + 0x8E * 3 / 10;
        int g = ((col >> 8) & 255) * 7 / 10 + 0x88 * 3 / 10;
        int b = (col & 255) * 7 / 10 + 0x7C * 3 / 10;
        return r << 16 | g << 8 | b;
    }

    /**
     * One footfall on a wall: a puff of the wall's grit off the face at the feet, thrown back along the run and out,
     * and the step's knock. (wx, wz): unit toward the wall; (tx, tz): the way along it.
     */
    static void step(Entity e, double wx, double wz, double tx, double tz, boolean sound) {
        if (!(e.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        double fx = e.getX() + wx * 0.35, fy = e.getY() + 0.35 + r.nextDouble() * 0.4, fz = e.getZ() + wz * 0.35;
        int tint = wallTint(level, e.getX() + wx * 0.7, fy, e.getZ() + wz * 0.7);
        for (int i = 0; i < 4; i++) {
            BodyFx.puff(level, tint, 0.7F, fx, fy, fz, -tx * (0.06 + r.nextDouble() * 0.05) - wx * 0.05 + (r.nextDouble() - 0.5) * 0.03,
                    0.02 + r.nextDouble() * 0.04, -tz * (0.06 + r.nextDouble() * 0.05) - wz * 0.05 + (r.nextDouble() - 0.5) * 0.03);
        }
        if (sound) BodyFx.sound(e, BodyContent.WALLSTEP.get(), 0.42F, 0.9F + r.nextFloat() * 0.2F);
    }

    /** Someone else's Runner clip started: remember their wall-runs to throw their grit. */
    static void seen(Entity e, String clip, float yaw) {
        boolean run = clip.equals(BodyClips.WALLRUN_L) || clip.equals(BodyClips.WALLRUN_R);
        int slot = -1;
        for (int i = 0; i < RUNNER.length; i++) if (RUNNER[i] == e.getId()) slot = i;
        if (!run) {
            if (slot >= 0) RUNNER[slot] = -1;
            return;
        }
        if (slot < 0) for (int i = 0; i < RUNNER.length; i++) if (RUNNER[i] < 0) {
            slot = i;
            break;
        }
        if (slot < 0) return;
        RUNNER[slot] = e.getId();
        AGE[slot] = 0;
        LEFT[slot] = clip.equals(BodyClips.WALLRUN_L);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (int i = 0; i < RUNNER.length; i++) {
            if (RUNNER[i] < 0) continue;
            Entity e = mc.level.getEntity(RUNNER[i]);
            if (e == null || ++AGE[i] > 50 || e.onGround()) {
                RUNNER[i] = -1;
                continue;
            }
            if (AGE[i] % 4 != 0) continue;
            float yaw = e.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            // your left, facing +z, is +x
            double lx = fz, lz = -fx;
            double wx = LEFT[i] ? lx : -lx, wz = LEFT[i] ? lz : -lz;
            step(e, wx, wz, fx, fz, false);
        }
    }

    static void reset() {
        java.util.Arrays.fill(RUNNER, -1);
    }
}
