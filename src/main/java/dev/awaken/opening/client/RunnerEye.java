package dev.awaken.opening.client;

import dev.awaken.opening.OpeningRoutes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The Runner's eye (SPEC §16.4, plan "How the run teaches without prompts"): stand still at an edge for six seconds and
 * the next hold on the run's routes pulses once in bronze. It waits longer each time it has had to show you (shown less
 * the better you get), and it can be turned off (client setting {@code runnersEye}). A second hesitation before the same
 * kind of move also shows its key, once per move.
 */
final class RunnerEye {
    private RunnerEye() {}

    /** Six seconds at an edge, half as long again each time it has shown (at most 4x). */
    static final int WAIT = 120;
    private static int still;
    private static double lastX, lastZ;
    /** Hesitations per move kind this session (the glyph shows on the second). */
    private static final Map<OpeningRoutes.Move, Integer> HESITATED = new HashMap<>();

    static void reset() {
        still = 0;
        HESITATED.clear();
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !OpeningConfig.runnersEye() || OpeningCamera.active() || mc.gui.screen() != null) {
            still = 0;
            return;
        }
        double dx = p.getX() - lastX, dz = p.getZ() - lastZ;
        lastX = p.getX();
        lastZ = p.getZ();
        if (!p.onGround() || dx * dx + dz * dz > 0.0009) {
            still = 0;
            return;
        }
        still++;
        int wait = (int) (WAIT * Math.min(4.0, 1 + 0.5 * OpeningConfig.hints()));
        if (still < wait) return;
        still = 0;
        if (!atEdge(p)) return;
        Vec3 o = OpeningClient.origin();
        double px = p.getX() - o.x, py = p.getY() - o.y, pz = p.getZ() - o.z;
        // the nearest point on any route, then the next hold after it on that route
        OpeningRoutes.Route best = null;
        int bi = -1;
        double bd = 14 * 14;
        for (OpeningRoutes.Route r : OpeningRoutes.get().routes) {
            for (int i = 0; i < r.points().size(); i++) {
                OpeningRoutes.Point q = r.points().get(i);
                double d = (q.x() - px) * (q.x() - px) + (q.ry() - py) * (q.ry() - py) * 0.5 + (q.z() - pz) * (q.z() - pz);
                if (d < bd) {
                    bd = d;
                    best = r;
                    bi = i;
                }
            }
        }
        if (best == null) return;
        OpeningRoutes.Point hold = null;
        for (int i = bi + 1; i < best.points().size(); i++) {
            OpeningRoutes.Point q = best.points().get(i);
            if (q.move() != OpeningRoutes.Move.RUN && q.move() != OpeningRoutes.Move.WALK) {
                hold = q;
                break;
            }
        }
        if (hold == null) return;
        int n = HESITATED.merge(hold.move(), 1, Integer::sum);
        String key = null;
        String name = hold.move().name();
        if (n >= 2 && !OpeningConfig.glyphShown(name)) {
            key = keyName(mc, hold.move());
            OpeningConfig.glyph(name);
        }
        OpeningFx.eye(new Vec3(o.x + hold.x(), o.y + hold.ry() + 0.6, o.z + hold.z()), key);
        OpeningSounds.ui(dev.awaken.body.BodyContent.GRAB.get(), 1.6F, 0.25F);
        OpeningConfig.hint();
    }

    /** A drop of two blocks or more within a step, in any direction. */
    private static boolean atEdge(LocalPlayer p) {
        var level = p.level();
        BlockPos feet = p.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos side = feet.offset(dx, 0, dz);
                if (!level.getBlockState(side).getCollisionShape(level, side).isEmpty()) continue;
                BlockPos d1 = side.below(), d2 = side.below(2);
                if (level.getBlockState(d1).getCollisionShape(level, d1).isEmpty()
                        && level.getBlockState(d2).getCollisionShape(level, d2).isEmpty()) return true;
            }
        }
        return false;
    }

    private static String keyName(Minecraft mc, OpeningRoutes.Move m) {
        String jump = mc.options.keyJump.getTranslatedKeyMessage().getString();
        String sprint = mc.options.keySprint.getTranslatedKeyMessage().getString();
        String sneak = mc.options.keyShift.getTranslatedKeyMessage().getString();
        return switch (m) {
            case WALLRUN_L, WALLRUN_R -> sprint + " + " + jump;
            case HANG, DROP, SLIDE -> sneak;
            case SHIMMY -> mc.options.keyLeft.getTranslatedKeyMessage().getString() + " / " + mc.options.keyRight.getTranslatedKeyMessage().getString();
            default -> jump;
        };
    }
}
