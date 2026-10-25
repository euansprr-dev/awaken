package dev.awaken.opening.client;

import dev.awaken.lens.client.LensClient;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.story.RitualSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import dev.awaken.Awaken;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The arrival (SPEC §16.4), twelve seconds for everyone in the square at once: control holds and the camera cranes up
 * from your eyes to frame the gate under the spire (the whole group in the shot); the great bell tolls three times, in
 * time now; the portal blooms; the light throws the citizens back and reaches for you: the camera swings in behind your
 * shoulder, the view stretches as it pulls you toward the light, the wind roars, and the frame tears to black. The
 * server takes you through the gate at the end; the black holds until the hall beyond is there.
 */
final class ArrivalShot implements OpeningCamera.Shot {
    static final int LENGTH = 240, TOLL_1 = 20, TOLL_2 = 60, TOLL_3 = 100, SWING = 110, PULL = 150, BLACK = 222;

    private final Vec3 gate, eye, group;
    private final Vec3 frame, frameLook, shoulder;
    private OpeningSounds.@Nullable Bed wind;
    private final float startYaw, startPitch;

    private ArrivalShot(OpeningPayloads.Scene s, LocalPlayer p) {
        gate = s.at();
        eye = p.getEyePosition();
        startYaw = p.getYRot();
        startPitch = p.getXRot();
        // the group: everyone within forty blocks, so the frame holds you all
        double gx = 0, gz = 0;
        int n = 0;
        for (Player o : p.level().players()) {
            if (o.distanceToSqr(p) > 40 * 40) continue;
            gx += o.getX();
            gz += o.getZ();
            n++;
        }
        group = n > 0 ? new Vec3(gx / n, eye.y, gz / n) : eye;
        Vec3 away = new Vec3(group.x - gate.x, 0, group.z - gate.z);
        double d = away.length();
        Vec3 dir = d < 1e-3 ? new Vec3(0, 0, 1) : away.scale(1 / d);
        // beyond the group, up high: the gate, the spire behind it, the people between
        frameLook = gate.add(0, 6, 0);
        frame = pickFrame(p, dir, d);
        Vec3 toGate = new Vec3(gate.x - eye.x, 0, gate.z - eye.z);
        double tg = Math.max(1e-3, toGate.length());
        Vec3 sh = eye.add(-toGate.x / tg * 2.6, 0.7, -toGate.z / tg * 2.6).add(-toGate.z / tg * 0.9, 0, toGate.x / tg * 0.9);
        // a wall or an awning behind you: the shoulder comes in short of it
        BlockHitResult h = p.level().clip(new ClipContext(eye, sh, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        shoulder = h.getType() == HitResult.Type.MISS ? sh : eye.add(h.getLocation().subtract(eye).scale(0.75));
    }

    /**
     * Where the crane goes: behind the group from the gate, up high, with nothing between it and your eyes (the crane's
     * own path) or between it and the gate. Arrivals land anywhere in the square (beside a stall, under the market's
     * eaves), so the first clear one of a fan of spots, turning away from straight behind as little as it can.
     */
    private Vec3 pickFrame(LocalPlayer p, Vec3 dir, double d) {
        Level level = p.level();
        double[] turns = {0, 20, -20, 40, -40, 65, -65, 90, -90, 135, -135, 180};
        double[][] spots = {{9, 9}, {9, 13}, {6, 13}, {6, 9}, {12, 6}, {4, 17}}; // {out beyond the group, up}
        for (double a : turns) {
            double c = Math.cos(Math.toRadians(a)), sn = Math.sin(Math.toRadians(a));
            double rx = dir.x * c - dir.z * sn, rz = dir.x * sn + dir.z * c;
            for (double[] sp : spots) {
                Vec3 cam = new Vec3(gate.x + rx * (d + sp[0]), gate.y + sp[1], gate.z + rz * (d + sp[0]));
                if (clear(level, p, cam, eye, 0.95) && clear(level, p, cam, frameLook, 0.8)) {
                    if (a != 0 || sp[0] != 9 || sp[1] != 9) Awaken.LOG.info("[opening] arrival frame turned {} degrees, out {}, up {}", a, sp[0], sp[1]);
                    return cam;
                }
            }
        }
        Awaken.LOG.info("[opening] arrival frame: no clear spot, straight behind");
        return new Vec3(gate.x + dir.x * (d + 9), gate.y + 9, gate.z + dir.z * (d + 9));
    }

    /** Nothing solid from a to b ({@code part} of the way: the target's own blocks don't count). */
    private static boolean clear(Level level, Player p, Vec3 a, Vec3 b, double part) {
        Vec3 to = a.add(b.subtract(a).scale(part));
        return level.clip(new ClipContext(a, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    static void start(OpeningPayloads.Scene s) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (OpeningCamera.shot() instanceof ArrivalShot) return;
        int from = Math.max(0, s.n());
        OpeningHud.barsTarget = 1;
        Awaken.LOG.info("[opening] arrival cinematic from {}", from);
        OpeningCamera.take(new ArrivalShot(s, p), from);
        if (from > LENGTH - 20) {
            OpeningHud.blackTarget = 1;
            OpeningHud.blackRate = 1;
        }
    }

    @Override
    public int length() {
        return LENGTH;
    }

    @Override
    public void tick(int age) {
        if (age == TOLL_1) {
            OpeningClient.toll(1);
            OpeningFx.bloomTarget = 0.35F;
            OpeningFx.ring(gate.add(0, 1, 0), 1F, 1.2F, 40F, 0xD0FFFFFF, 40);
        }
        if (age == TOLL_2) {
            OpeningClient.toll(2);
            OpeningFx.bloomTarget = 0.7F;
            // the light throws the people back
            OpeningFx.ring(gate.add(0, 0.4, 0), 2F, 2.2F, 60F, 0xE0CFE8FF, 34);
            OpeningSounds.ui(RitualSounds.IMPACT.get(), 0.7F, 0.8F);
            LensClient.ritual(0.2F, 0.5F);
        }
        if (age == TOLL_3) {
            OpeningClient.toll(3);
            OpeningFx.bloomTarget = 1F;
            wind = OpeningSounds.bed(dev.awaken.body.ascent.AscentContent.WIND.get(), 0.9F, 0.55F, 60, 10);
            LensClient.ritual(0.25F, 0.8F);
        }
        if (age == PULL) {
            OpeningSounds.ui(RitualSounds.DRAIN.get(), 1.1F, 0.9F);
        }
        if (wind != null && age > TOLL_3) wind.gain = Mth.clamp((age - TOLL_3) / 80F, 0.2F, 1F);
        if (age >= PULL) OpeningHud.flash(0xE4F2FF, Mth.clamp((age - PULL) / 90F, 0, 0.55F));
        if (age == BLACK) {
            OpeningSounds.ui(RitualSounds.CLOSE.get(), 1F, 1F);
            OpeningHud.blackTarget = 1;
            OpeningHud.blackRate = 0.5F;
            LensClient.ritual(0.6F, 1F);
        }
    }

    @Override
    public void pose(float t, double[] out) {
        double x, y, z;
        Vec3 look;
        double fov = 1, roll = 0;
        if (t < SWING) {
            // up from your eyes to the frame: the crane move
            double k = ease(Mth.clamp(t / 50.0, 0, 1));
            x = Mth.lerp(k, eye.x, frame.x);
            y = Mth.lerp(k, eye.y, frame.y);
            z = Mth.lerp(k, eye.z, frame.z);
            double yr = Math.toRadians(startYaw), pr = Math.toRadians(startPitch);
            Vec3 start = eye.add(-Math.sin(yr) * Math.cos(pr) * 20, -Math.sin(pr) * 20, Math.cos(yr) * Math.cos(pr) * 20);
            look = lerp(start, frameLook, k);
            // a slow push while the bell tolls
            double push = Math.max(0, t - 50) / 60.0;
            x += (frameLook.x - x) * push * 0.12;
            z += (frameLook.z - z) * push * 0.12;
        } else if (t < PULL) {
            // the light reaches for you: round to your shoulder, looking into it
            double k = ease(Mth.clamp((t - SWING) / (PULL - SWING), 0, 1));
            Vec3 from = framePushed();
            x = Mth.lerp(k, from.x, shoulder.x);
            y = Mth.lerp(k, from.y, shoulder.y);
            z = Mth.lerp(k, from.z, shoulder.z);
            look = lerp(frameLook, gate.add(0, 3, 0), k);
            fov = 1 + 0.15 * k;
        } else {
            // the pull: toward the light, the view stretching (and the world leaning into it)
            double k = Mth.clamp((t - PULL) / (LENGTH - PULL), 0, 1);
            double kk = k * k;
            Vec3 core = gate.add(0, 2.5, 0);
            x = Mth.lerp(kk * 0.92, shoulder.x, core.x);
            y = Mth.lerp(kk * 0.92, shoulder.y, core.y);
            z = Mth.lerp(kk * 0.92, shoulder.z, core.z);
            look = core.add(0, 0.5, 0);
            fov = 1.15 + 0.55 * k;
            roll = Math.sin(t * 0.21) * 2.5 * k;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        double dx = look.x - x, dy = look.y - y, dz = look.z - z;
        out[3] = Math.toDegrees(Math.atan2(-dx, dz));
        out[4] = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        out[5] = roll;
        out[6] = fov;
    }

    private Vec3 framePushed() {
        double push = (SWING - 50) / 60.0 * 0.12;
        return new Vec3(frame.x + (frameLook.x - frame.x) * push, frame.y, frame.z + (frameLook.z - frame.z) * push);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return new Vec3(Mth.lerp(k, a.x, b.x), Mth.lerp(k, a.y, b.y), Mth.lerp(k, a.z, b.z));
    }

    private static double ease(double k) {
        return k * k * (3 - 2 * k);
    }

    @Override
    public boolean hideBody(double camDistSq) {
        return camDistSq < 0.8;
    }

    @Override
    public void finished(String reason) {
        if (wind != null) wind.end();
        OpeningFx.bloomTarget = 0;
        OpeningHud.barsTarget = 0;
        LensClient.clearOverride(20); // the hall beyond has its own look
        // through the gate: the black holds until the hall is there, then opens
        OpeningHud.blackTarget = 1;
        OpeningHud.blackRate = 1;
        OpeningClient.later(reason.equals("taken") ? 30 : 60, () -> {
            OpeningHud.blackRate = 0.05F;
            OpeningHud.blackTarget = 0;
            LensClient.ritual(0F, 0F);
            OpeningFx.columnOn = false;
        });
    }
}
