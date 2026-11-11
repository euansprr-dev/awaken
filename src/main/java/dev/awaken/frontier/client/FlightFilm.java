package dev.awaken.frontier.client;

import dev.awaken.Awaken;
import dev.awaken.frontier.FrontierModule;
import dev.awaken.frontier.FrontierNet;
import dev.awaken.frontier.Skiff;
import dev.awaken.opening.client.Cinema;
import dev.awaken.opening.client.OpeningCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The skiff's flight on screen (SPEC §67), filmed with the opening's camera. First you watch it wake from off its bow:
 * the lift core hums up, the wings rise on their booms, the wind-wheel starts to turn, the bell rings. Then you're at the
 * helm as it comes off its cradle, gathers speed along its bow, leans into its turn toward the far end and climbs, wind
 * streaking past, into the cloud; the cloud covers the screen ({@link CloudCrossing}) while the server moves you and your
 * client loads the land; then it breaks out high over the far mooring, comes in along its berth with its wings flared
 * and its wheel slowing, and settles. The skiff in the air is drawn with its own block models ({@link SkiffFx}); the
 * resting skiff it stands in for is swapped for invisible barriers on this client while it flies, so you still stand on
 * something, and put back when it lands. The server decides when you're moved and when it's over; the film only follows.
 */
public final class FlightFilm {
    private FlightFilm() {}

    /** The lift (the server's is the same), the quick one, the descent and its quick one, in ticks. */
    static final int LIFT = 120, QUICK_LIFT = 14, DESCENT = 110, QUICK_DESCENT = 50;
    /** The cloud starts covering this long before the lift ends; it holds at least this long once covering; it parts over this long. */
    static final int COVER_IN = 22, HOLD_MIN = 60, PART = 34;
    /** Give the land this long to arrive and draw before parting anyway. */
    static final int WAIT_LAND = 20 * 20;
    /** The skiff wakes for this long before it lifts, watched from outside (a quick flight has none). */
    static final int WAKE = 40;
    /** A skiff that came for you: the view goes from your own eyes up to its helm over this long. */
    static final int BOARD = 26;
    /** Where the wake is watched from and to: off the open bow quarter, low, drifting in (south-facing, from the berth's middle). */
    static final double[] WAKE_FROM = {12.5, 0.4, 14.0}, WAKE_TO = {10.5, 2.0, 12.0};

    enum Phase { LIFT, CROSS, DESCEND }

    /** One block of a skiff as it stood: its offset from the berth's middle (world-turned), what it was, and what it moves with. */
    record Snap(int dx, int dy, int dz, BlockState state, int rig) {}

    static final class Flight {
        final BlockPos from, to;
        final Direction fromFacing;
        Direction toFacing;
        final boolean quick, home, drawn, wake;
        /** A skiff that came for you: your own eyes when it began (x, y, z, yaw, pitch). */
        double @Nullable [] startEye;
        /** How far the wake's camera is lifted to stand clear of the land. */
        float camUp;
        FlightAir.@Nullable Bed hum, wind;
        final String title, line;
        final List<UUID> party;
        final List<Snap> fromSnap = new ArrayList<>();
        List<Snap> toSnap = new ArrayList<>();
        Phase phase = Phase.LIFT;
        int arrivedAt = -1, coveredAt = -1, descendAt = -1;
        boolean toHidden;
        /** The lift's path, a sample a tick: offset from the berth's middle, yaw, nose-up and roll (degrees). */
        final float[] px, py, pz, yaw, nose, roll;
        final float eye;

        Flight(FrontierNet.Fly m, float eye) {
            this.from = m.from();
            this.to = m.to();
            this.fromFacing = Direction.from2DDataValue(m.fromFacing());
            this.toFacing = Direction.from2DDataValue(m.toFacing());
            this.quick = m.quick();
            this.drawn = m.drawn();
            this.wake = !m.quick() && !m.drawn();
            this.home = m.title().equals("Carillon");
            this.title = m.title();
            this.line = m.line();
            this.party = m.party();
            this.eye = eye;
            int n = lift() + 1;
            px = new float[n];
            py = new float[n];
            pz = new float[n];
            yaw = new float[n];
            nose = new float[n];
            roll = new float[n];
            liftPath(fromFacing.toYRot(), bearing(from, to), px, py, pz, yaw, nose, roll);
        }

        int lift() {
            return quick ? QUICK_LIFT : LIFT;
        }

        int descent() {
            return quick ? QUICK_DESCENT : DESCENT;
        }
    }

    static @Nullable Flight cur;
    /** Where the server put this player at the far helm (feet), and how many ticks more they're held there. */
    private static double @Nullable [] pin;
    private static int pinLeft;

    public static boolean flying() {
        return cur != null;
    }

    // ---- the server's word ----------------------------------------------------------------------------------------------

    static void onFly(FrontierNet.Fly m) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        switch (m.kind()) {
            case FrontierNet.Fly.START -> start(mc, p, m);
            case FrontierNet.Fly.ARRIVED -> arrived(m);
            case FrontierNet.Fly.END -> {
                if (cur != null) {
                    Awaken.LOG.info("[frontier] the server ended the flight; the film lets go");
                    if (OpeningCamera.shot() instanceof Film) Cinema.cut("done");
                    else clear(true);
                }
            }
            default -> {}
        }
    }

    private static void start(Minecraft mc, LocalPlayer p, FrontierNet.Fly m) {
        if (cur != null) clear(true);
        Flight f = new Flight(m, p.getEyeHeight());
        cur = f;
        if (f.drawn) {
            // the skiff that came for you: drawn as built beside you, nothing real there to hide; you're taken up to its helm
            template(f.fromFacing, f.fromSnap);
            f.startEye = new double[] {p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot()};
        } else {
            snap(mc.level, f.from, f.fromFacing, f.fromSnap);
            hide(mc.level, f.from, f.fromSnap);
        }
        if (f.wake) f.camUp = clearAbove(mc.level, f);
        CloudCrossing.begin(f.title, f.line, f.home);
        Cinema.bars(true);
        Cinema.hud(false);
        Cinema.take(new Film(f));
        Awaken.LOG.info("[frontier] the flight lifts from {} for {}{}", f.from.toShortString(), f.to.toShortString(), f.quick ? " (quick)" : "");
    }

    private static void arrived(FrontierNet.Fly m) {
        Flight f = cur;
        if (f == null) return;
        f.toFacing = Direction.from2DDataValue(m.toFacing());
        f.arrivedAt = OpeningCamera.age();
        BlockPos stand = Skiff.at(m.to(), f.toFacing, Skiff.STAND);
        pin = new double[] {stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5};
        pinLeft = 20 * 60;
        if (f.phase == Phase.LIFT) {
            // a client a little behind the server: cover now
            f.phase = Phase.CROSS;
            f.coveredAt = f.arrivedAt;
            CloudCrossing.coverNow();
        }
        Awaken.LOG.info("[frontier] moved to the far deck at {}; the land is loading under the cloud", m.to().toShortString());
    }

    // ---- the shot ---------------------------------------------------------------------------------------------------

    /** The whole flight as one camera shot: lift, the wait in the cloud, the descent. */
    static final class Film implements OpeningCamera.Shot {
        final Flight f;
        private final double[] last = new double[7];
        private final Vector3f v = new Vector3f();
        private final Quaternionf q = new Quaternionf();

        Film(Flight f) {
            this.f = f;
        }

        @Override
        public int length() {
            return f.descendAt < 0 ? -1 : f.descendAt + f.descent();
        }

        @Override
        public boolean hideBody(double camDistSq) {
            // while it wakes you're seen at the helm, where you really stand; once it lifts the skiff is drawn elsewhere
            return !waking(f);
        }

        @Override
        public @Nullable CameraType landIn() {
            return CameraType.FIRST_PERSON;
        }

        @Override
        public void tick(int age) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;
            RandomSource r = level.getRandom();
            switch (f.phase) {
                case LIFT -> {
                    liftSounds(age);
                    liftAir(age, r);
                    if (age >= f.lift() - (f.quick ? f.lift() : COVER_IN)) CloudCrossing.cover();
                    if (age >= f.lift()) {
                        f.phase = Phase.CROSS;
                        f.coveredAt = age;
                    }
                }
                case CROSS -> {
                    // inside the cloud everything is close and muffled
                    beds(0.14F, 1.1F, 0.2F, 0.85F);
                    if (f.arrivedAt < 0) return;
                    boolean held = age - f.coveredAt >= HOLD_MIN && age - f.arrivedAt >= 20;
                    if (!held) return;
                    if (!landHere(mc) && age - f.arrivedAt < WAIT_LAND) return;
                    // the far skiff as it stands (or as it's built, if this client hasn't got it), swapped out for the descent
                    f.toSnap = new ArrayList<>();
                    snap(level, f.to, f.toFacing, f.toSnap);
                    if (f.toSnap.size() < Skiff.skiff().size() / 2) template(f.toFacing, f.toSnap);
                    else {
                        hide(level, f.to, f.toSnap);
                        f.toHidden = true;
                    }
                    f.phase = Phase.DESCEND;
                    f.descendAt = age;
                    CloudCrossing.part(PART);
                    play(FrontierModule.SAIL.get(), 0.8F, 0.5F);
                }
                case DESCEND -> {
                    int t = age - f.descendAt, left = f.descent() - t;
                    if (left == 6 && f.toHidden) {
                        // the real skiff back a few ticks early, under the drawn one, so it's meshed when the drawn one goes
                        restore(level, f.to, f.toSnap);
                        f.toHidden = false;
                    }
                    float[] a = descent(f, t), b = descent(f, t + 1);
                    double hx = b[0] - a[0], hy = b[1] - a[1], hz = b[2] - a[2];
                    float speed = (float) Math.sqrt(hx * hx + hy * hy + hz * hz), u = t / (float) f.descent();
                    if (left > 20) beds(0.26F, 0.9F + Math.min(0.4F, speed * 0.12F), 0.12F + Math.min(0.5F, speed * 0.16F), 0.7F + Math.min(0.5F, speed * 0.14F));
                    else endBeds(f);
                    if (speed > 0.2F) FlightAir.stream(last[0], last[1], last[2], hx / speed, hy / speed, hz / speed, speed, Mth.clamp(1 - u / 0.28F, 0, 1) * 0.85F, r);
                    // two bells for an arrival, the wash of the core over the pad, the hull onto its cradle
                    if (left == 34 || left == 25) play(FrontierModule.BELL.get(), 1.0F, 0.45F);
                    if (left <= 16 && left >= 3 && (t & 1) == 0)
                        FlightAir.wash(f.to.getX() + 0.5, f.to.getY() + Skiff.KEEL, f.to.getZ() + 0.5, 0.9F, r);
                    if (left == 9) {
                        play(FrontierModule.SET_DOWN.get(), 0.7F, 0.8F);
                        play(FrontierModule.CREAK.get(), 0.6F, 0.5F);
                    }
                    if (left == 4) play(FrontierModule.CORE_REST.get(), 0.9F, 0.4F);
                }
            }
        }

        /** The lift's sounds: the mooring let go, the core coming up, the wings filling, the bell, then the wind. */
        private void liftSounds(int age) {
            if (f.quick) {
                if (age == 1) {
                    play(FrontierModule.CAST_OFF.get(), 0.8F, 0.5F);
                    play(FrontierModule.SAIL.get(), 1.0F, 0.6F);
                }
                beds(0.26F, 1.1F, 0.4F, 1.0F);
                return;
            }
            if (age == 1) play(FrontierModule.CAST_OFF.get(), 0.8F, 0.6F);
            if (age == 5) play(FrontierModule.CORE_WAKE.get(), 0.9F, 0.5F);
            if (age == 12) play(FrontierModule.SAIL.get(), 1.1F, 0.5F);
            if (age == 24) play(FrontierModule.CREAK.get(), 0.6F, 0.5F);
            if (age == WAKE - 8) play(FrontierModule.BELL.get(), 1.0F, 0.5F);
            if (age == WAKE) play(FrontierModule.SAIL.get(), 0.9F, 0.7F);
            if (age < 6) return;
            if (age < WAKE) {
                float up = (age - 6F) / (WAKE - 6F);
                beds(0.1F + 0.18F * up, 0.7F + 0.3F * up, 0.06F * up, 0.6F);
            } else {
                float speed = speedAt(age);
                beds(0.3F, 1.0F + Math.min(0.35F, speed * 0.11F), 0.14F + Math.min(0.5F, speed * 0.17F), 0.7F + Math.min(0.5F, speed * 0.15F));
            }
        }

        /** The lift's air: dust blown off the pad as it wakes and lifts, then the wind and the cloud it climbs into. */
        private void liftAir(int age, RandomSource r) {
            if (f.quick) return;
            if (age >= 8 && age <= WAKE + 12 && (age & 1) == 0)
                FlightAir.wash(f.from.getX() + 0.5, f.from.getY() + Skiff.KEEL, f.from.getZ() + 0.5, Math.min(1F, age / 30F), r);
            if (age <= WAKE || age >= f.lift()) return;
            double hx = f.px[age] - f.px[age - 1], hy = f.py[age] - f.py[age - 1], hz = f.pz[age] - f.pz[age - 1];
            float speed = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
            if (speed < 0.2F) return;
            float k = (age - WAKE) / (float) (f.lift() - WAKE);
            FlightAir.stream(last[0], last[1], last[2], hx / speed, hy / speed, hz / speed, speed, Mth.clamp((k - 0.5F) / 0.4F, 0, 1), r);
        }

        private float speedAt(int age) {
            int i = Mth.clamp(age, 1, f.lift());
            double hx = f.px[i] - f.px[i - 1], hy = f.py[i] - f.py[i - 1], hz = f.pz[i] - f.pz[i - 1];
            return (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        }

        /** The two beds under the flight (the core's hum, the wind), started when first wanted and eased to these levels. */
        private void beds(float humVolume, float humPitch, float windVolume, float windPitch) {
            if (f.hum == null) f.hum = FlightAir.bed(FrontierModule.CORE_HUM.get(), humPitch);
            if (f.wind == null) f.wind = FlightAir.bed(FrontierModule.WIND.get(), windPitch);
            f.hum.set(humVolume, humPitch);
            f.wind.set(windVolume, windPitch);
        }

        @Override
        public void pose(float t, double[] out) {
            switch (f.phase) {
                case LIFT -> {
                    if (f.wake && t < WAKE) wakePose(t, out);
                    else {
                        liftPose(Math.min(t, f.lift()), out);
                        if (f.startEye != null && t < BOARD) board(t, out);
                    }
                }
                case CROSS -> {
                    if (f.arrivedAt >= 0) descentPose(0, out);
                    else liftPose(f.lift(), out);
                }
                case DESCEND -> descentPose(Mth.clamp(t - f.descendAt, 0, f.descent()), out);
            }
            System.arraycopy(out, 0, last, 0, 7);
        }

        /** The wake, from outside: low off the open bow quarter, drifting in and up, on the whole skiff. */
        private void wakePose(float t, double[] out) {
            float u = Mth.clamp(t / WAKE, 0, 1), e = u * u * (3 - 2 * u);
            double[] at = Skiff.turn(f.fromFacing, Mth.lerp(e, WAKE_FROM[0], WAKE_TO[0]), Mth.lerp(e, WAKE_FROM[2], WAKE_TO[2]));
            double[] on = Skiff.turn(f.fromFacing, 0.0, -0.5);
            double x = f.from.getX() + 0.5 + at[0], y = f.from.getY() + Mth.lerp(e, WAKE_FROM[1], WAKE_TO[1]) + f.camUp, z = f.from.getZ() + 0.5 + at[1];
            double dx = f.from.getX() + 0.5 + on[0] - x, dy = f.from.getY() + 2.6 - y, dz = f.from.getZ() + 0.5 + on[1] - z;
            out[0] = x;
            out[1] = y;
            out[2] = z;
            out[3] = Math.toDegrees(Math.atan2(-dx, dz));
            out[4] = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            out[5] = 0;
            out[6] = 0.84;
        }

        /** A skiff that came for you: from your own eyes up to its helm. */
        private void board(float t, double[] out) {
            double[] from = f.startEye;
            if (from == null) return;
            float u = Mth.clamp(t / BOARD, 0, 1), e = u * u * (3 - 2 * u);
            // up first, then across, so the view clears the rail
            float rise = Math.min(1F, e * 1.5F);
            out[0] = Mth.lerp(e, from[0], out[0]);
            out[1] = Mth.lerp(rise, from[1], out[1]);
            out[2] = Mth.lerp(e, from[2], out[2]);
            out[3] = from[3] + Mth.wrapDegrees(out[3] - from[3]) * e;
            out[4] = Mth.lerp(e, from[4], out[4]);
        }

        private void liftPose(float t, double[] out) {
            int i = Mth.clamp((int) t, 0, f.lift()), j = Math.min(f.lift(), i + 1);
            float k = t - i;
            float ox = Mth.lerp(k, f.px[i], f.px[j]), oy = Mth.lerp(k, f.py[i], f.py[j]), oz = Mth.lerp(k, f.pz[i], f.pz[j]);
            float yaw = Mth.lerp(k, f.yaw[i], f.yaw[j]), nose = Mth.lerp(k, f.nose[i], f.nose[j]), roll = Mth.lerp(k, f.roll[i], f.roll[j]);
            float u = t / f.lift();
            eye(f.from, ox, oy, oz, yaw, nose, roll, Mth.lerp(u, 7F, 1F), t, out);
            // the view opens a little as it gathers speed
            float speed = (float) Math.sqrt(Mth.square(f.px[j] - f.px[i]) + Mth.square(f.py[j] - f.py[i]) + Mth.square(f.pz[j] - f.pz[i]));
            out[6] = 1.0 + Math.min(0.08, speed * 0.028);
        }

        private void descentPose(float t, double[] out) {
            float[] s = descent(f, t);
            float u = t / f.descent();
            float pitch = u < 0.8F ? Mth.lerp(u / 0.8F, 18F, 4F) : Mth.lerp((u - 0.8F) / 0.2F, 4F, 0F);
            eye(f.to, s[0], s[1], s[2], s[3], s[4], s[5], pitch, t * (1 - u), out);
            out[6] = 1.0 + 0.08 * Math.pow(1 - u, 2);
        }

        /** The eyes at the helm of a skiff at this place and attitude, looking along its bow, swaying a little with it. */
        private void eye(BlockPos berth, float ox, float oy, float oz, float yaw, float nose, float roll, float pitch, float sway, double[] out) {
            turn(q, yaw, nose, roll);
            q.transform(v.set(Skiff.STAND.getX(), Skiff.STAND.getY() + f.eye, Skiff.STAND.getZ()));
            float live = Math.min(1F, Math.abs(oy) / 3F);
            out[0] = berth.getX() + 0.5 + ox + v.x;
            out[1] = berth.getY() + oy + v.y + 0.035F * live * Mth.sin(sway * 0.19F);
            out[2] = berth.getZ() + 0.5 + oz + v.z;
            out[3] = yaw;
            out[4] = pitch - nose * 0.6F;
            out[5] = roll * 0.55F + 0.7F * live * Mth.sin(sway * 0.047F);
            out[6] = 1.0;
        }

        @Override
        public void finished(String reason) {
            boolean done = reason.equals("done");
            if (done) ClientPacketDistributor.sendToServer(new FrontierNet.Landed(0));
            clear(!done || f.toHidden);
            Awaken.LOG.info("[frontier] the flight's film ended ({})", reason);
        }
    }

    // ---- paths ------------------------------------------------------------------------------------------------------

    /** Minecraft's yaw from a to b. */
    static float bearing(BlockPos a, BlockPos b) {
        return (float) Math.toDegrees(Math.atan2(-(b.getX() - a.getX()), b.getZ() - a.getZ()));
    }

    /**
     * The lift, integrated a tick at a time: while it wakes it only stirs on its cradle; then it comes off with a surge,
     * gathers speed along its bow, turns toward the far end as it climbs, leaning into the turn with its nose up, and is
     * 140 blocks up when the cloud closes.
     */
    static void liftPath(float restYaw, float toYaw, float[] px, float[] py, float[] pz, float[] yaw, float[] nose, float[] roll) {
        int n = px.length - 1;
        float delta = Mth.wrapDegrees(toYaw - restYaw);
        double x = 0, z = 0;
        float lastYaw = restYaw;
        for (int t = 0; t <= n; t++) {
            if (n < WAKE + 20) {
                // a quick flight: it barely stirs before the cloud
                py[t] = 0.15F * Mth.sin(t * 0.4F);
                yaw[t] = restYaw;
                continue;
            }
            float k = Mth.clamp((t - (float) WAKE) / (n - WAKE), 0, 1);
            float s = k * k * (3 - 2 * k);
            float turn = Mth.clamp((k - 0.12F) / 0.6F, 0, 1);
            float y = restYaw + delta * turn * turn * (3 - 2 * turn);
            double v = 3.0 * s;
            x += -Math.sin(Math.toRadians(y)) * v;
            z += Math.cos(Math.toRadians(y)) * v;
            px[t] = (float) x;
            pz[t] = (float) z;
            float stir = t < WAKE ? 0.05F * Mth.sin(t * 0.33F) * Math.min(1F, t / 14F) : 0;
            float surge = t < WAKE ? 0 : 2.2F * (1 - (float) Math.exp(-(t - WAKE) / 6.0));
            py[t] = (float) (140 * Math.pow(s, 1.2)) + surge + stir;
            yaw[t] = y;
            nose[t] = 9F * Mth.sin((float) Math.PI * Math.min(1, k * 1.4F));
            roll[t] = Mth.clamp(-(y - lastYaw) * 5F, -14F, 14F);
            lastYaw = y;
        }
    }

    /** The living parts while it lifts, at tick t: {how far the wings are raised, how far the wind-wheel has turned} in degrees. */
    static void liftRig(float t, boolean quick, float[] out) {
        if (quick) {
            float u = Mth.clamp(t / QUICK_LIFT, 0, 1);
            out[0] = 14F * u * u * (3 - 2 * u);
            out[1] = 15F * t * t / QUICK_LIFT;
            return;
        }
        float u = Mth.clamp((t - 6F) / 28F, 0, 1);
        float beat = t > WAKE ? 3.5F * Mth.sin((t - WAKE) * 0.13F) * Math.min(1F, (t - WAKE) / 20F) : 0;
        out[0] = 15F * u * u * (3 - 2 * u) + beat;
        // spinning up to thirty degrees a tick by the time it lifts, then steady
        out[1] = t < WAKE ? t * t * t / 160F : 400F + 30F * (t - WAKE);
    }

    /** The same on the way down: the wings flare as it brakes and fold flat as it settles; the wheel runs down to rest as it stood. */
    static void descentRig(float t, int descent, float[] out) {
        float u = Mth.clamp(t / descent, 0, 1);
        float flare = Mth.clamp((u - 0.5F) / 0.3F, 0, 1), fold = Mth.clamp((u - 0.88F) / 0.12F, 0, 1), left = 1 - u;
        float up = 15F + 9F * flare * flare * (3 - 2 * flare) + 2.5F * Mth.sin(t * 0.13F) * left;
        out[0] = up * (1 - fold * fold * (3 - 2 * fold));
        out[1] = 1080F * (1 - left * left * left);
    }

    /** Whether this flight is still waking at its mooring (watched from outside, everyone aboard where they really stand). */
    static boolean waking(Flight f) {
        return f.wake && f.phase == Phase.LIFT && OpeningCamera.age() < WAKE;
    }

    /** How far to lift the wake's camera so neither end of its drift is in the land. */
    private static float clearAbove(ClientLevel level, Flight f) {
        for (int up = 0; up <= 8; up++) {
            boolean free = true;
            for (double[] local : new double[][] {WAKE_FROM, WAKE_TO}) {
                double[] w = Skiff.turn(f.fromFacing, local[0], local[2]);
                BlockPos p = BlockPos.containing(f.from.getX() + 0.5 + w[0], f.from.getY() + local[1] + up, f.from.getZ() + 0.5 + w[1]);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty())
                    free = false;
            }
            if (free) return up;
        }
        return 8;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Cinema.sound(sound, pitch, volume);
    }

    private static void endBeds(Flight f) {
        if (f.hum != null) f.hum.end();
        if (f.wind != null) f.wind.end();
        f.hum = null;
        f.wind = null;
    }

    /** The descent at t ticks: in along the berth from behind and above, slowing, a little dip as it settles. */
    static float[] descent(Flight f, float t) {
        float u = Mth.clamp(t / f.descent(), 0, 1);
        float e = 1 - (float) Math.pow(1 - u, 2.4);
        float back = f.quick ? 60 : 190, up = f.quick ? 30 : 120;
        float yaw = f.toFacing.toYRot();
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        float rest = 1 - e;
        float dip = u > 0.86F ? -0.22F * Mth.sin((float) Math.PI * (u - 0.86F) / 0.14F) : 0;
        return new float[] {(float) (-fx * back * rest), (float) (up * Math.pow(rest, 1.3)) + dip, (float) (-fz * back * rest), yaw,
                -4F * rest, 2.5F * Mth.sin(u * (float) Math.PI * 2) * rest};
    }

    /** The turn from a south-facing skiff to one at this yaw, nose-up and roll. */
    static Quaternionf turn(Quaternionf q, float yaw, float nose, float roll) {
        return q.rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(-nose)).rotateZ((float) Math.toRadians(roll));
    }

    // ---- the resting skiffs -----------------------------------------------------------------------------------------

    /** Reads a skiff as this client has it (blocks that aren't there are left out). */
    static void snap(ClientLevel level, BlockPos berth, Direction facing, List<Snap> out) {
        out.clear();
        for (Skiff.Part part : Skiff.skiff()) {
            BlockPos at = Skiff.at(berth, facing, part.x(), part.y(), part.z());
            if (!level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) continue;
            BlockState s = level.getBlockState(at);
            if (s.isAir() || s.is(Blocks.BARRIER)) continue;
            out.add(new Snap(at.getX() - berth.getX(), at.getY() - berth.getY(), at.getZ() - berth.getZ(), s, part.rig()));
        }
    }

    /** The skiff as it's built, for a far end this client hasn't got yet. */
    static void template(Direction facing, List<Snap> out) {
        out.clear();
        var rot = Skiff.rotation(facing);
        for (Skiff.Part part : Skiff.skiff()) {
            BlockPos at = Skiff.at(BlockPos.ZERO, facing, part.x(), part.y(), part.z());
            out.add(new Snap(at.getX(), at.getY(), at.getZ(), part.state().rotate(rot), part.rig()));
        }
    }

    /** Swaps a resting skiff for barriers on this client: unseen, but still something to stand on. */
    static void hide(ClientLevel level, BlockPos berth, List<Snap> snap) {
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (Snap s : snap) level.setBlock(berth.offset(s.dx, s.dy, s.dz), barrier, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** Puts it back where this client still has the barriers. */
    static void restore(ClientLevel level, BlockPos berth, List<Snap> snap) {
        for (Snap s : snap) {
            BlockPos at = berth.offset(s.dx, s.dy, s.dz);
            if (level.hasChunk(at.getX() >> 4, at.getZ() >> 4) && level.getBlockState(at).is(Blocks.BARRIER))
                level.setBlock(at, s.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /**
     * The far end's land is here and drawn: either nothing is left to draw anywhere, or the land round the mooring is
     * (the far land can go on arriving while the skiff comes down; waiting for all of it can hold the cloud for long).
     */
    static boolean landHere(Minecraft mc) {
        Flight f = cur;
        LocalPlayer p = mc.player;
        if (f == null || p == null || mc.level == null) return false;
        if (p.distanceToSqr(f.to.getX() + 0.5, f.to.getY(), f.to.getZ() + 0.5) > 48 * 48) return false;
        int cx = f.to.getX() >> 4, cz = f.to.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (!mc.level.hasChunk(cx + dx, cz + dz)) return false;
        if (mc.levelRenderer.hasRenderedAllSections()) return true;
        int drawn = 0;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean here = mc.levelRenderer.isSectionCompiledAndVisible(f.to.offset(dx * 16, 0, dz * 16), 0L);
                if (here) drawn++;
                else if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) return false;
            }
        return drawn >= 38;
    }

    /**
     * Once a client tick. Moved to the far deck, the land there is still coming, and the game lets a player sink (a tenth
     * of a block a tick) where it has no chunk yet: they'd come to rest inside whatever arrives, or fall through it into
     * a cave, and see the descent from the dark. So they're held at the helm, where the server put them, until the film
     * has set them down and there's land under them (a minute at most).
     */
    static void holdAtHelm(Minecraft mc) {
        double[] at = pin;
        if (at == null) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || --pinLeft <= 0) {
            pin = null;
            return;
        }
        if (cur == null && mc.level.hasChunk(Mth.floor(at[0]) >> 4, Mth.floor(at[2]) >> 4)) {
            pin = null;
            return;
        }
        // only where the server put them: never dragged there from somewhere else
        if (Math.abs(p.getX() - at[0]) > 3 || Math.abs(p.getZ() - at[2]) > 3) return;
        p.setPos(at[0], at[1], at[2]);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
    }

    /** The flight is over on this client: both skiffs back, the bars up, the cloud gone. */
    static void clear(boolean restoreAll) {
        Flight f = cur;
        cur = null;
        Minecraft mc = Minecraft.getInstance();
        if (f != null) endBeds(f);
        if (f != null && mc.level != null) {
            if (!f.drawn) restore(mc.level, f.from, f.fromSnap);
            if (restoreAll || f.toHidden) restore(mc.level, f.to, f.toSnap);
        }
        CloudCrossing.end();
        Cinema.bars(false);
        Cinema.hud(true);
    }

    /** Disconnecting: the level is going away. */
    static void reset() {
        if (cur != null) endBeds(cur);
        cur = null;
        pin = null;
        CloudCrossing.end();
    }

    /** The party aboard, while this client's skiff is lifting (their bodies are hidden: they're on the skiff). */
    static boolean aboard(UUID who) {
        Flight f = cur;
        return f != null && f.phase != Phase.DESCEND && !waking(f) && f.party.contains(who);
    }
}
