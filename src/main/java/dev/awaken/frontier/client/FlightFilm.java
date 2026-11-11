package dev.awaken.frontier.client;

import dev.awaken.Awaken;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The skiff's flight on screen (SPEC §67), filmed with the opening's camera: you stand at the helm and the skiff lifts,
 * leans into its turn toward the far end and climbs into the cloud; the cloud covers the screen ({@link CloudCrossing})
 * while the server moves you and your client loads the land; then the cloud parts high over the far mooring and the skiff
 * comes in along its berth and settles. The skiff in the air is drawn with the game's own block models
 * ({@link SkiffFx}); the resting skiff it stands in for is swapped for invisible barriers on this client while it flies,
 * so you still stand on something, and put back when it lands. The server decides when you're moved and when it's over;
 * the film only follows.
 */
public final class FlightFilm {
    private FlightFilm() {}

    /** The lift (the server's is the same), the quick one, the descent and its quick one, in ticks. */
    static final int LIFT = 120, QUICK_LIFT = 14, DESCENT = 110, QUICK_DESCENT = 50;
    /** The cloud starts covering this long before the lift ends; it holds at least this long once covering; it parts over this long. */
    static final int COVER_IN = 22, HOLD_MIN = 60, PART = 34;
    /** Give the land this long to arrive and draw before parting anyway. */
    static final int WAIT_LAND = 20 * 20;

    enum Phase { LIFT, CROSS, DESCEND }

    /** One block of a skiff as it stood: its offset from the berth's middle (world-turned) and what it was. */
    record Snap(int dx, int dy, int dz, BlockState state) {}

    static final class Flight {
        final BlockPos from, to;
        final Direction fromFacing;
        Direction toFacing;
        final boolean quick, home, drawn;
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
            // the skiff that came for you: drawn as built where you stand, nothing real there to hide
            template(f.fromFacing, f.fromSnap);
        } else {
            snap(mc.level, f.from, f.fromFacing, f.fromSnap);
            hide(mc.level, f.from, f.fromSnap);
        }
        CloudCrossing.begin(f.title, f.line, f.home);
        Cinema.bars(true);
        Cinema.hud(false);
        Cinema.take(new Film(f));
        Cinema.sound(SoundEvents.CHAIN_PLACE, 0.6F, 0.5F);
        if (!f.quick) Cinema.sound(SoundEvents.ELYTRA_FLYING, 0.8F, 0.25F);
        Awaken.LOG.info("[frontier] the flight lifts from {} for {}{}", f.from.toShortString(), f.to.toShortString(), f.quick ? " (quick)" : "");
    }

    private static void arrived(FrontierNet.Fly m) {
        Flight f = cur;
        if (f == null) return;
        f.toFacing = Direction.from2DDataValue(m.toFacing());
        f.arrivedAt = OpeningCamera.age();
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
            return true;
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
            switch (f.phase) {
                case LIFT -> {
                    if (age >= f.lift() - (f.quick ? f.lift() : COVER_IN)) CloudCrossing.cover();
                    if (age >= f.lift()) {
                        f.phase = Phase.CROSS;
                        f.coveredAt = age;
                    }
                }
                case CROSS -> {
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
                    Cinema.sound(SoundEvents.ELYTRA_FLYING, 0.7F, f.quick ? 0.12F : 0.2F);
                }
                case DESCEND -> {
                    int t = age - f.descendAt;
                    if (t == f.descent() - 6 && f.toHidden) {
                        // the real skiff back a few ticks early, under the drawn one, so it's meshed when the drawn one goes
                        restore(level, f.to, f.toSnap);
                        f.toHidden = false;
                    }
                    if (t == f.descent() - 10) Cinema.sound(SoundEvents.WOOD_PLACE, 0.7F, 0.6F);
                }
            }
        }

        @Override
        public void pose(float t, double[] out) {
            switch (f.phase) {
                case LIFT -> liftPose(Math.min(t, f.lift()), out);
                case CROSS -> {
                    if (f.arrivedAt >= 0) descentPose(0, out);
                    else liftPose(f.lift(), out);
                }
                case DESCEND -> descentPose(Mth.clamp(t - f.descendAt, 0, f.descent()), out);
            }
            System.arraycopy(out, 0, last, 0, 7);
        }

        private void liftPose(float t, double[] out) {
            int i = Mth.clamp((int) t, 0, f.lift()), j = Math.min(f.lift(), i + 1);
            float k = t - i;
            float ox = Mth.lerp(k, f.px[i], f.px[j]), oy = Mth.lerp(k, f.py[i], f.py[j]), oz = Mth.lerp(k, f.pz[i], f.pz[j]);
            float yaw = Mth.lerp(k, f.yaw[i], f.yaw[j]), nose = Mth.lerp(k, f.nose[i], f.nose[j]), roll = Mth.lerp(k, f.roll[i], f.roll[j]);
            float u = t / f.lift();
            eye(f.from, ox, oy, oz, yaw, nose, roll, Mth.lerp(u, 6F, 1F), out);
        }

        private void descentPose(float t, double[] out) {
            float[] s = descent(f, t);
            float u = t / f.descent();
            float pitch = u < 0.8F ? Mth.lerp(u / 0.8F, 18F, 4F) : Mth.lerp((u - 0.8F) / 0.2F, 4F, 0F);
            eye(f.to, s[0], s[1], s[2], s[3], s[4], s[5], pitch, out);
        }

        /** The eyes at the helm of a skiff at this place and attitude, looking along its bow. */
        private void eye(BlockPos berth, float ox, float oy, float oz, float yaw, float nose, float roll, float pitch, double[] out) {
            turn(q, yaw, nose, roll);
            q.transform(v.set(Skiff.STAND.getX(), Skiff.STAND.getY() + f.eye, Skiff.STAND.getZ()));
            out[0] = berth.getX() + 0.5 + ox + v.x;
            out[1] = berth.getY() + oy + v.y;
            out[2] = berth.getZ() + 0.5 + oz + v.z;
            out[3] = yaw;
            out[4] = pitch - nose * 0.6F;
            out[5] = roll * 0.55F;
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
     * The lift, integrated a tick at a time: a second's bob at rest, then the skiff gathers speed along its bow, turns
     * toward the far end as it climbs, leaning into the turn with its nose up, and is 150 blocks up when the cloud closes.
     */
    static void liftPath(float restYaw, float toYaw, float[] px, float[] py, float[] pz, float[] yaw, float[] nose, float[] roll) {
        int n = px.length - 1;
        float delta = Mth.wrapDegrees(toYaw - restYaw);
        double x = 0, z = 0;
        float lastYaw = restYaw;
        for (int t = 0; t <= n; t++) {
            if (n < 30) {
                // a quick flight: it barely stirs before the cloud
                py[t] = 0.15F * Mth.sin(t * 0.4F);
                yaw[t] = restYaw;
                continue;
            }
            float k = Mth.clamp((t - 20F) / (n - 20F), 0, 1);
            float s = k * k * (3 - 2 * k);
            float turn = Mth.clamp((k - 0.1F) / 0.6F, 0, 1);
            float y = restYaw + delta * turn * turn * (3 - 2 * turn);
            double v = 2.4 * s;
            x += -Math.sin(Math.toRadians(y)) * v;
            z += Math.cos(Math.toRadians(y)) * v;
            px[t] = (float) x;
            pz[t] = (float) z;
            py[t] = (float) (150 * Math.pow(s, 1.15)) + (t < 24 ? 0.12F * Mth.sin(t * 0.35F) : 0);
            yaw[t] = y;
            nose[t] = 8F * Mth.sin((float) Math.PI * Math.min(1, k * 1.4F));
            roll[t] = Mth.clamp(-(y - lastYaw) * 5F, -14F, 14F);
            lastYaw = y;
        }
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
            out.add(new Snap(at.getX() - berth.getX(), at.getY() - berth.getY(), at.getZ() - berth.getZ(), s));
        }
    }

    /** The skiff as it's built, for a far end this client hasn't got yet. */
    static void template(Direction facing, List<Snap> out) {
        out.clear();
        var rot = Skiff.rotation(facing);
        for (Skiff.Part part : Skiff.skiff()) {
            BlockPos at = Skiff.at(BlockPos.ZERO, facing, part.x(), part.y(), part.z());
            out.add(new Snap(at.getX(), at.getY(), at.getZ(), part.state().rotate(rot)));
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

    /** The far end's land is here and drawn. */
    static boolean landHere(Minecraft mc) {
        Flight f = cur;
        LocalPlayer p = mc.player;
        if (f == null || p == null || mc.level == null) return false;
        if (p.distanceToSqr(f.to.getX() + 0.5, f.to.getY(), f.to.getZ() + 0.5) > 48 * 48) return false;
        int cx = f.to.getX() >> 4, cz = f.to.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (!mc.level.hasChunk(cx + dx, cz + dz)) return false;
        return mc.levelRenderer.hasRenderedAllSections();
    }

    /** The flight is over on this client: both skiffs back, the bars up, the cloud gone. */
    static void clear(boolean restoreAll) {
        Flight f = cur;
        cur = null;
        Minecraft mc = Minecraft.getInstance();
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
        cur = null;
        CloudCrossing.end();
    }

    /** The party aboard, while this client's skiff is lifting (their bodies are hidden: they're on the skiff). */
    static boolean aboard(UUID who) {
        Flight f = cur;
        return f != null && f.phase != Phase.DESCEND && f.party.contains(who);
    }
}
