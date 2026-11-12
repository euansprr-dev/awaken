package dev.awaken.frontier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.frontier.FrontierNet;
import dev.awaken.frontier.Skiff;
import dev.awaken.opening.client.OpeningCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The skiff in the air (SPEC §67), drawn block by block with its own models: the one you're flying in, and one you
 * watch leave from its mooring (it lifts away with its party, and drifts back empty to its berth a while later). It's
 * alive as it flies: the wings rise about their roots and beat slowly, and the wind-wheel turns about its hub.
 */
final class SkiffFx {
    private SkiffFx() {}

    /** A watched skiff is gone this long before it comes back to its berth. */
    static final int AWAY = 20 * 15;

    /** A skiff someone else flew off in, as this client sees it from the mooring. */
    static final class Watch {
        final BlockPos berth;
        final Direction facing;
        final List<UUID> party;
        final List<FlightFilm.Snap> snap = new ArrayList<>();
        final float[] px, py, pz, yaw, nose, roll;
        final long start;
        final FlightFilm.Flight back;

        Watch(FrontierNet.Seen m, long start) {
            this.berth = m.from();
            this.facing = Direction.from2DDataValue(m.facing());
            this.party = m.party();
            this.start = start;
            int n = FlightFilm.LIFT + 1;
            px = new float[n];
            py = new float[n];
            pz = new float[n];
            yaw = new float[n];
            nose = new float[n];
            roll = new float[n];
            FlightFilm.liftPath(facing.toYRot(), m.bearing(), px, py, pz, yaw, nose, roll);
            // its return: the descent onto its own berth
            this.back = new FlightFilm.Flight(new FrontierNet.Fly(FrontierNet.Fly.START, berth, m.facing(), berth, m.facing(), false, "", "", List.of(), false, 0), 1.62F);
        }

        long age(long now) {
            return now - start;
        }

        long end() {
            return FlightFilm.LIFT + AWAY + FlightFilm.DESCENT;
        }
    }

    static final List<Watch> WATCHES = new ArrayList<>();

    private static final Quaternionf Q = new Quaternionf(), REST = new Quaternionf(), LIGHT = new Quaternionf(),
            WING_A = new Quaternionf(), WING_B = new Quaternionf(), WHEEL = new Quaternionf();
    private static final Vector3f V = new Vector3f();
    private static final float[] RIG = new float[2];

    static void onSeen(FrontierNet.Seen m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || FlightFilm.flying()) return;
        Watch w = new Watch(m, FrontierClient.ticks);
        FlightFilm.snap(mc.level, w.berth, w.facing, w.snap);
        if (w.snap.isEmpty()) return;
        FlightFilm.hide(mc.level, w.berth, w.snap);
        WATCHES.add(w);
        Awaken.LOG.info("[frontier] a skiff lifts from {} with {} aboard", w.berth.toShortString(), w.party.size());
    }

    /** Client tick: a watched skiff that's back at its berth is the real one again. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        for (Iterator<Watch> it = WATCHES.iterator(); it.hasNext(); ) {
            Watch w = it.next();
            long age = w.age(FrontierClient.ticks);
            if (age >= w.end() - 6 && mc.level != null) FlightFilm.restore(mc.level, w.berth, w.snap);
            if (age >= w.end() || mc.level == null) it.remove();
        }
    }

    static void clear() {
        WATCHES.clear();
    }

    /** Someone aboard a skiff this client watched leave, while it's still lifting: their body is on the skiff, unseen. */
    static boolean aboard(UUID who) {
        for (Watch w : WATCHES) {
            long age = w.age(FrontierClient.ticks);
            if (age >= FlightFilm.WAKE && age < FlightFilm.LIFT + 40 && w.party.contains(who)) return true;
        }
        return false;
    }

    static void submit(SubmitCustomGeometryEvent e) {
        FlightFilm.Flight f = FlightFilm.cur;
        if (f == null && WATCHES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        float pt = state.worldPartialTicks;
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        try {
            if (f != null && OpeningCamera.shot() instanceof FlightFilm.Film) {
                float t = OpeningCamera.age() + pt;
                if (f.phase == FlightFilm.Phase.LIFT) {
                    float[] s = sample(f.px, f.py, f.pz, f.yaw, f.nose, f.roll, Math.min(t, f.lift()));
                    FlightFilm.liftRig(t, f.quick, RIG);
                    draw(ps, out, level, cam, f.from, f.fromFacing, f.fromSnap, s, RIG[0], RIG[1]);
                } else if (f.phase == FlightFilm.Phase.DESCEND) {
                    FlightFilm.descentRig(t - f.descendAt, f.descent(), RIG);
                    draw(ps, out, level, cam, f.to, f.toFacing, f.toSnap, FlightFilm.descent(f, t - f.descendAt), RIG[0], RIG[1]);
                }
            }
            for (Watch w : WATCHES) {
                float age = w.age(FrontierClient.ticks) + pt;
                if (age < FlightFilm.LIFT) {
                    FlightFilm.liftRig(age, false, RIG);
                    draw(ps, out, level, cam, w.berth, w.facing, w.snap, sample(w.px, w.py, w.pz, w.yaw, w.nose, w.roll, age), RIG[0], RIG[1]);
                } else if (age >= FlightFilm.LIFT + AWAY) {
                    float back = Math.min(age - FlightFilm.LIFT - AWAY, FlightFilm.DESCENT);
                    FlightFilm.descentRig(back, FlightFilm.DESCENT, RIG);
                    draw(ps, out, level, cam, w.berth, w.facing, w.snap, FlightFilm.descent(w.back, back), RIG[0], RIG[1]);
                }
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[frontier] drawing the skiff failed", ex);
            WATCHES.clear();
        }
    }

    /** The lift's path at t: offset x, y, z, yaw, nose-up, roll. */
    static float[] sample(float[] px, float[] py, float[] pz, float[] yaw, float[] nose, float[] roll, float t) {
        int n = px.length - 1;
        int i = Math.max(0, Math.min(n, (int) t)), j = Math.min(n, i + 1);
        float k = Math.max(0, Math.min(1, t - i));
        return new float[] {px[i] + (px[j] - px[i]) * k, py[i] + (py[j] - py[i]) * k, pz[i] + (pz[j] - pz[i]) * k,
                yaw[i] + (yaw[j] - yaw[i]) * k, nose[i] + (nose[j] - nose[i]) * k, roll[i] + (roll[j] - roll[i]) * k};
    }

    /**
     * Every block of a skiff, turned as one body about its berth's middle and moved by the path's offset; the wings turned
     * {@code wing} degrees up about their roots and the wind-wheel {@code wheel} degrees about its hub.
     */
    private static void draw(PoseStack ps, SubmitNodeCollector out, ClientLevel level, Vec3 cam, BlockPos berth, Direction facing,
                             List<FlightFilm.Snap> snap, float[] s, float wing, float wheel) {
        if (snap.isEmpty()) return;
        // as it stands -> south-facing -> (its moving parts) -> its attitude in the air
        FlightFilm.turn(Q, s[3], s[4], s[5]);
        REST.rotationY((float) Math.toRadians(facing.toYRot()));
        LIGHT.set(Q).mul(REST);
        WING_A.rotationZ((float) Math.toRadians(wing));
        WING_B.rotationZ((float) Math.toRadians(-wing));
        WHEEL.rotationZ((float) Math.toRadians(wheel));
        float wingY = Skiff.WING_Y + 0.5F, hubX = Skiff.HUB.getX(), hubY = Skiff.HUB.getY() + 0.5F, hubZ = Skiff.HUB.getZ();
        double bx = berth.getX() + 0.5 + s[0], by = berth.getY() + s[1], bz = berth.getZ() + 0.5 + s[2];
        Holder<Biome> biome = level.getBiome(berth);
        // At its berth it's lit by where each block is. In the air it's lit by the open sky over its berth: where it's
        // drawn the land may not have come yet, and a place with no land has no light (the hull went black on the way down).
        boolean aloft = s[0] * s[0] + s[1] * s[1] + s[2] * s[2] > 9F;
        BlockPos sky = new BlockPos(berth.getX(), level.getMaxY() - 1, berth.getZ());
        for (FlightFilm.Snap b : snap) {
            LIGHT.transform(V.set(b.dx(), b.dy() + 0.5F, b.dz()));
            MovingBlockRenderState mb = new MovingBlockRenderState();
            mb.blockState = b.state();
            mb.blockPos = aloft ? sky : BlockPos.containing(bx + V.x, by + V.y, bz + V.z);
            mb.randomSeedPos = berth.offset(b.dx(), b.dy(), b.dz());
            mb.lightEngine = level.getLightEngine();
            mb.cardinalLighting = level.cardinalLighting();
            mb.biome = biome;
            ps.pushPose();
            ps.translate(bx - cam.x, by - cam.y, bz - cam.z);
            ps.rotate(Q);
            switch (b.rig()) {
                case Skiff.WING_A -> ps.rotateAround(WING_A, Skiff.WING_ROOT, wingY, 0F);
                case Skiff.WING_B -> ps.rotateAround(WING_B, -Skiff.WING_ROOT, wingY, 0F);
                case Skiff.WHEEL -> ps.rotateAround(WHEEL, hubX, hubY, hubZ);
                default -> {}
            }
            ps.rotate(REST);
            ps.translate(b.dx() - 0.5F, (float) b.dy(), b.dz() - 0.5F);
            out.submitMovingBlock(ps, mb, 0);
            ps.popPose();
        }
    }
}
