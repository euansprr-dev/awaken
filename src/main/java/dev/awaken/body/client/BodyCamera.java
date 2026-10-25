package dev.awaken.body.client;


import dev.awaken.body.BodyRules;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Each move's own camera, in first person: the roll dips the view hard and lets it swing back up, the mantle lifts
 * your eyes to the lip then tips you over it, the slide drops low and tilts, the tactical sprint pushes the view wide
 * and lowers your hands. Only the drawn camera moves; your aim is untouched. Respects the FOV-effects slider.
 */
final class BodyCamera {
    private BodyCamera() {}

    private static float tacWide, slideWide, stairTilt;

    private static float now(float pt) {
        return ClientMoves.ticks + pt;
    }

    /** 0 → 1 over {@code in} ticks after start, holding while running, → 0 over {@code out} ticks after end. */
    private static float hold(float t, int start, int end, boolean running, float in, float out) {
        if (start < -50) return 0;
        float up = Mth.clamp((t - start) / in, 0, 1);
        if (running) return up;
        float down = 1 - Mth.clamp((t - end) / out, 0, 1);
        return Math.min(up, down);
    }

    static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        float t = now((float) e.getPartialTick());
        float pitch = 0, roll = 0;
        // roll: a hard dip forward as the shoulder takes the ground, a little twist, then the view swings back up
        float r = t - ClientMoves.rollAt;
        if (r >= 0 && r < BodyRules.ROLL_TICKS + 6) {
            float attack = Mth.clamp(r / 2.2F, 0, 1);
            float release = Mth.clamp((r - 2.2F) / (BodyRules.ROLL_TICKS + 3.8F), 0, 1);
            float dip = attack * (1 - release * release * (3 - 2 * release));
            // reduced motion halves the dip and its twist like every other roll (SPEC §25 "Feel")
            float comfort = dev.awaken.body.client.feel.FeelConfig.reduced() ? 0.5F : 1F;
            pitch += 32F * dip * comfort;
            roll += 7F * Mth.sin(r * 0.55F) * dip * comfort;
        }
        // mantle: eyes up to the lip, then over it
        float m = t - ClientMoves.mantleStart;
        if (m >= 0 && m < BodyRules.MANTLE_TICKS + 5) {
            float u = m / BodyRules.MANTLE_TICKS;
            if (u < 0.5F) pitch -= 9F * Mth.sin(u / 0.5F * Mth.HALF_PI);
            else pitch += -9F * Mth.cos(Math.min(1, (u - 0.5F) / 0.35F) * Mth.HALF_PI) + 8F * Mth.sin(Math.min(1, (u - 0.5F) / 1.1F) * Mth.PI);
        }
        // slide: low, tilted, a faint judder from the ground
        float s = hold(t, ClientMoves.slideStart, ClientMoves.slideEnd, ClientMoves.sliding, 2.5F, 4F);
        if (s > 0) {
            roll += 7.5F * s;
            pitch -= 3F * s;
            pitch += 0.35F * s * Mth.sin(t * 2.9F) * (float) (ClientMoves.slideSpeed / BodyRules.SLIDE_V0);
        }
        // a stair slide (SPEC §17.22): the view tips down the run you're gliding (eased in and out)
        stairTilt += ((ClientMoves.stairSliding ? 1F : 0F) - stairTilt) * 0.08F;
        if (stairTilt > 0.01F) pitch += 9F * stairTilt;
        // the slide-cancel: a kick up as you come out
        float j = t - ClientMoves.slideJumpAt;
        if (j >= 0 && j < 7) pitch -= 5F * Mth.sin(j / 7F * Mth.PI);
        // the grip's slide (SPEC §17.23): a faint judder from the line running through the hands
        if (GripMoves.active() && GripMoves.mode == GripMoves.SLIDE) {
            pitch += 0.4F * Mth.sin(t * 3.3F) * (float) (-GripMoves.v / dev.awaken.body.GripRules.SLIDE_MAX);
        }
        // tactical sprint: a slight forward lean and a running sway
        float a = hold(t, ClientMoves.tacStart, ClientMoves.tacEnd, ClientMoves.tac, 4F, 5F);
        if (a > 0) {
            pitch += 2F * a;
            roll += 0.9F * a * Mth.sin(t * 0.8F);
        }
        // a wall-run rolls the view away from the wall (SPEC §16.3: wallRunRoll, 5° by default)
        float w = hold(t, RunnerMoves.runStart, RunnerMoves.runEnd, RunnerMoves.state == RunnerMoves.State.RUN, 4F, 6F);
        if (w > 0) {
            float yr = mc.player.getYRot() * Mth.DEG_TO_RAD;
            double rx = -Mth.cos(yr), rz = -Mth.sin(yr); // the view's right
            double wx = RunnerMoves.runAxis == 0 ? RunnerMoves.runSide : 0, wz = RunnerMoves.runAxis == 2 ? RunnerMoves.runSide : 0;
            float wr = dev.awaken.body.client.feel.FeelConfig.wallRunRoll();
            roll += (rx * wx + rz * wz > 0 ? -wr : wr) * w;
        }
        if (pitch != 0) e.setPitch(e.getPitch() + pitch);
        if (roll != 0) e.setRoll(e.getRoll() + roll);
    }

    static void onFov(ComputeFovModifierEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getPlayer() != mc.player) return;
        // eased toward the targets (the vanilla speed push from the sprint's attribute comes on top)
        tacWide += ((ClientMoves.tac ? 1F : 0F) - tacWide) * 0.25F;
        slideWide += ((ClientMoves.sliding ? 1F : 0F) - slideWide) * 0.3F;
        float extra = 0.035F * tacWide + 0.07F * slideWide;
        if (extra > 1e-4F) e.setNewFovModifier(e.getNewFovModifier() * (1 + extra * e.getFovScale()));
    }
}
