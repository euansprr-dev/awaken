package dev.awaken.living.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.core.api.MapMarks;
import dev.awaken.living.LivingContent;
import dev.awaken.living.LivingNet;
import dev.awaken.living.craft.Craft;
import dev.awaken.living.craft.Crafts;
import dev.awaken.living.hunt.HuntState;
import dev.awaken.living.population.Herds;
import dev.awaken.living.population.Spoor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The signs on the ground and the band's scout motes (SPEC §41.3). Prints are pressed into the earth (dark, fading as they
 * age, a patch of many feet for a herd); with the Tracks perk they glow amber instead and last longer. Droppings lie on a
 * flattened oval where a herd bedded; a kill is bones on a dark stain. On a trail the next sign pulses amber and a thread of
 * motes drifts from you toward it, or, once the band knows, toward the quarry itself.
 */
final class SpoorFx {
    private SpoorFx() {}

    /** A sign as drawn: the message, when it arrived (client game time), and its pieces laid on the ground once. */
    private static final class Sign {
        final LivingNet.SpoorMark m;
        final long got;
        final List<float[]> pieces = new ArrayList<>();

        Sign(LivingNet.SpoorMark m, long got) {
            this.m = m;
            this.got = got;
        }

        long age(long now) {
            return m.age() + Math.max(0, now - got);
        }
    }

    private static final Map<Integer, Sign> SIGNS = new HashMap<>();
    private static LivingNet.Trail trail = new LivingNet.Trail(0, -1, 0, 0, 0, "");

    static void init() {
        MapMarks.register(SpoorFx::marks);
    }

    static void onSpoor(LivingNet.Spoor msg) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Map<Integer, Sign> keep = new HashMap<>();
        for (LivingNet.SpoorMark m : msg.marks()) {
            Sign old = SIGNS.get(m.id());
            if (old != null && old.m.read() == m.read()) {
                keep.put(m.id(), old);
                continue;
            }
            Sign s = new Sign(m, now);
            if (old != null) s.pieces.addAll(old.pieces);
            else lay(level, s);
            keep.put(m.id(), s);
        }
        SIGNS.clear();
        SIGNS.putAll(keep);
    }

    static void onTrail(LivingNet.Trail t) {
        trail = t;
    }

    static void reset() {
        SIGNS.clear();
        trail = new LivingNet.Trail(0, -1, 0, 0, 0, "");
    }

    // ---- laying the pieces ----------------------------------------------------------------------------------------------

    /**
     * Each piece is {x, y, z, yaw (radians), shape, size}: shapes 0 a hoof, 1 a paw, 2 a dropping, 3 a bone, 4 a skull,
     * 5 the bed's flattened oval, 6 the kill's stain. Every piece sits on the ground under it; one with none in reach is left out.
     */
    private static void lay(ClientLevel level, Sign s) {
        LivingNet.SpoorMark m = s.m;
        RandomSource r = RandomSource.create(m.id() * 7919L);
        double yaw = Math.toRadians(m.yaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
        switch (m.kind()) {
            case 0 -> {
                int bodies = Math.max(1, m.count()), lanes = Math.min(bodies, 8);
                double width = 0.4 + Math.sqrt(bodies) * 0.6;
                int per = Math.max(3, Math.min(8, (4 + bodies * 2) / lanes));
                for (int lane = 0; lane < lanes; lane++) {
                    double off = lanes == 1 ? 0 : (lane / (lanes - 1.0) - 0.5) * width + (r.nextDouble() - 0.5) * 0.3;
                    double start = (r.nextDouble() - 0.5) * 1.6 - per * 0.4;
                    double drift = (r.nextDouble() - 0.5) * 0.25;
                    for (int i = 0; i < per; i++) {
                        double along = start + i * 0.8, side = off + drift * i + (i % 2 == 0 ? 0.16 : -0.16);
                        piece(level, s, m.x() + fx * along + sx * side, m.y(), m.z() + fz * along + sz * side,
                                yaw + (r.nextDouble() - 0.5) * 0.4, m.hoofed() ? 0 : 1, 0.85 + r.nextDouble() * 0.3);
                    }
                }
            }
            case 1 -> {
                double a = r.nextDouble() * Math.PI;
                piece(level, s, m.x(), m.y(), m.z(), a, 5, 1.2 + Math.sqrt(Math.max(1, m.count())) * 0.35);
                int lumps = 4 + r.nextInt(4);
                for (int i = 0; i < lumps; i++) {
                    double d = r.nextDouble() * 1.4, t = r.nextDouble() * Math.PI * 2;
                    piece(level, s, m.x() + Math.cos(t) * d, m.y(), m.z() + Math.sin(t) * d, r.nextDouble() * Math.PI, 2, 0.8 + r.nextDouble() * 0.5);
                }
            }
            default -> {
                piece(level, s, m.x(), m.y(), m.z(), r.nextDouble() * Math.PI, 6, 1.1);
                piece(level, s, m.x() + (r.nextDouble() - 0.5) * 0.6, m.y(), m.z() + (r.nextDouble() - 0.5) * 0.6, r.nextDouble() * Math.PI * 2, 4, 1);
                for (int i = 0; i < 7; i++) {
                    double d = 0.2 + r.nextDouble() * 1.1, t = r.nextDouble() * Math.PI * 2;
                    piece(level, s, m.x() + Math.cos(t) * d, m.y(), m.z() + Math.sin(t) * d, r.nextDouble() * Math.PI, 3, 0.5 + r.nextDouble() * 0.7);
                }
            }
        }
    }

    private static void piece(ClientLevel level, Sign s, double x, double y0, double z, double yaw, int shape, double size) {
        double y = ground(level, x, y0, z);
        if (Double.isNaN(y)) return;
        s.pieces.add(new float[]{(float) x, (float) y, (float) z, (float) yaw, shape, (float) size});
    }

    /** The top of the solid ground at (x, z) near y0 (plants and snow layers count as air), or NaN. */
    private static double ground(ClientLevel level, double x, double y0, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), top = Mth.floor(y0) + 1;
        for (int y = top; y >= top - 4; y--) {
            p.set(bx, y, bz);
            BlockState st = level.getBlockState(p);
            VoxelShape shape = st.getCollisionShape(level, p);
            if (shape.isEmpty() || !st.getFluidState().isEmpty()) continue;
            double h = y + shape.max(Direction.Axis.Y);
            return h > y0 + 1.2 ? Double.NaN : h + 0.012;
        }
        return Double.NaN;
    }

    // ---- the Atlas ------------------------------------------------------------------------------------------------------

    private static List<MapMarks.Mark> marks(Player p) {
        HuntState s = p.getData(LivingContent.HUNT);
        if (s.found().isEmpty()) return List.of();
        List<MapMarks.Mark> out = new ArrayList<>(s.found().size());
        for (HuntState.Found f : s.found()) {
            String one = Herds.name(f.species(), false), many = Herds.name(f.species(), true);
            String label = switch (f.kind()) {
                case HuntState.Found.BED -> cap(one) + " bed";
                case HuntState.Found.DEN -> cap(one) + " den";
                default -> "Kill site (" + many + ")";
            };
            int colour = f.kind() == HuntState.Found.BED ? 0xC8A060 : f.kind() == HuntState.Found.DEN ? 0x9A6A44 : 0xB04A3A;
            out.add(new MapMarks.Mark(new BlockPos(f.x(), 64, f.z()), colour, label, true));
        }
        return out;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------

    private static RenderType earth, light;
    private static double cx, cy, cz;
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    private static final float[] CORNERS = new float[8];

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null || SIGNS.isEmpty() && trail.stage() < 2) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        if (earth == null) earth = RenderTypes.debugQuads();
        if (light == null) light = RenderTypes.lightning();
        long now = level.getGameTime();
        float pt = state.worldPartialTicks, t = LivingClient.ticks + pt;
        boolean tracks = Crafts.has(p, Craft.HUNTING, "tracks"), band = LivingClient.shown;
        Vec3 from = p.getPosition(pt).add(0, 1.1, 0);
        if (!SIGNS.isEmpty()) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), earth, (pose, vc) -> guard(() -> ground(vc, now, tracks)));
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> glow(vc, now, t, tracks, band, from)));
    }

    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[living] spoor fx failed", ex);
        }
    }

    /** How much of a sign is left to see: 1 fresh, 0 gone (prints over their life; droppings and bones hold, then fade). */
    private static float fade(Sign s, long now, boolean tracks) {
        int kind = s.m.kind();
        float life = Spoor.life(Spoor.Kind.values()[Math.min(kind, 2)], tracks);
        float a = 1 - s.age(now) / life;
        return kind == 0 ? Mth.clamp(a, 0, 1) : Mth.clamp(a * 3, 0, 1);
    }

    /** The earthy pass: prints pressed in the ground, droppings, bones (without Tracks; with it the prints glow instead). */
    private static void ground(VertexConsumer vc, long now, boolean tracks) {
        for (Sign s : SIGNS.values()) {
            float f = fade(s, now, tracks);
            if (f <= 0) continue;
            for (float[] pc : s.pieces) {
                int shape = (int) pc[4];
                double x = pc[0], y = pc[1], z = pc[2], yaw = pc[3], k = pc[5];
                double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
                switch (shape) {
                    case 0, 1 -> {
                        if (tracks) continue;
                        // a fresh print is a dark wet press with a crumbled rim; an old one a faint dent
                        oval(vc, x, y - 0.004, z, fx, fz, sx, sz, 0.24 * k, 0.2 * k, argb(0.28F * f, 0x4A3A28));
                        if (shape == 0) hoof(vc, x, y, z, fx, fz, sx, sz, k, argb(0.62F * f, 0x24190F));
                        else paw(vc, x, y, z, fx, fz, sx, sz, k, argb(0.62F * f, 0x281E14));
                    }
                    case 2 -> box(vc, x, y, z, fx, fz, sx, sz, 0.07 * k, 0.06 * k, 0.07 * k, argb(0.95F * f, 0x4A3A22), argb(0.95F * f, 0x352818));
                    case 3 -> box(vc, x, y, z, fx, fz, sx, sz, 0.32 * k, 0.035, 0.05, argb(f, 0xE2D9C2), argb(f, 0xB9AE95));
                    case 4 -> box(vc, x, y, z, fx, fz, sx, sz, 0.12, 0.1, 0.16, argb(f, 0xE6DDC6), argb(f, 0xBCB199));
                    case 5 -> oval(vc, x, y - 0.006, z, fx, fz, sx, sz, 1.0 * k, 0.65 * k, argb(0.34F * f, 0xA49C5A));
                    default -> oval(vc, x, y - 0.006, z, fx, fz, sx, sz, 0.95 * k, 0.7 * k, argb(0.55F * f, 0x3C1610));
                }
            }
        }
    }

    /** The glowing pass: Tracks' amber prints, the next sign's pulse and the motes. */
    private static void glow(VertexConsumer vc, long now, float t, boolean tracks, boolean band, Vec3 from) {
        if (tracks) {
            for (Sign s : SIGNS.values()) {
                if (s.m.kind() != 0) continue;
                float f = fade(s, now, true), a = f * f;
                if (a <= 0.01F) continue;
                for (float[] pc : s.pieces) {
                    double yaw = pc[3], k = pc[5], fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
                    oval(vc, pc[0], pc[1] + 0.01, pc[2], fx, fz, sx, sz, 0.3 * k, 0.26 * k, argb(0.3F * a, 0xFFB050));
                    if (pc[4] == 0) hoof(vc, pc[0], pc[1] + 0.012, pc[2], fx, fz, sx, sz, k, argb(a, 0xFFB050));
                    else paw(vc, pc[0], pc[1] + 0.012, pc[2], fx, fz, sx, sz, k, argb(a, 0xFFB050));
                }
            }
        }
        if (!band || trail.stage() < 2) return;
        Sign next = trail.sign() >= 0 ? SIGNS.get(trail.sign()) : null;
        if (next != null) {
            float pulse = 0.55F + 0.45F * Mth.sin(t * 0.15F);
            LivingNet.SpoorMark m = next.m;
            double y = next.pieces.isEmpty() ? m.y() : next.pieces.getFirst()[1];
            for (int ring = 0; ring < 2; ring++) {
                float grow = ((t * 0.02F + ring * 0.5F) % 1F);
                ring(vc, m.x(), y + 0.03, m.z(), 0.6 + grow * 1.4, argb(0.5F * (1 - grow) * pulse, 0xFFB050));
            }
            streak(vc, m.x(), y, m.z(), 1.6, 0.12F, 0xFFC070, 0.35F * pulse);
        }
        if (trail.stage() == 2 && next == null) return;
        double tx = trail.x(), ty = trail.y() + (trail.stage() == 2 ? 0.4 : 0), tz = trail.z();
        double dx = tx - from.x, dy = ty - from.y, dz = tz - from.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 2.5) return;
        dx /= dist;
        dy /= dist;
        dz /= dist;
        double len = Math.min(dist - 1.5, trail.stage() == 3 ? 16 : 12);
        int n = trail.stage() == 3 ? 14 : 9;
        int rgb = trail.stage() == 3 ? 0xFF9A48 : 0xFFC070;
        double px = -dz, pz = dx; // sideways, level
        for (int i = 0; i < n; i++) {
            float ph = ((t * 0.011F + i / (float) n) % 1F);
            float life = Mth.sin(ph * Mth.PI);
            double along = 1.0 + ph * len;
            double wob = Mth.sin(t * 0.07F + i * 2.1F) * 0.4 * life, bob = Mth.sin(t * 0.09F + i * 1.3F) * 0.15 + life * 0.25;
            double x = from.x + dx * along + px * wob, y = from.y + dy * along + bob, z = from.z + dz * along + pz * wob;
            mote(vc, x, y, z, 0.2, argb(0.32F * life, rgb));
            mote(vc, x, y, z, 0.05, argb(0.95F * life, 0xFFE8C0));
        }
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | rgb;
    }

    private static void hoof(VertexConsumer vc, double x, double y, double z, double fx, double fz, double sx, double sz, double k, int c) {
        for (int half = -1; half <= 1; half += 2) { // two halves, a gap between, each pointed at the toe
            double hx = x + sx * half * 0.045 * k, hz = z + sz * half * 0.045 * k;
            flat(vc, hx, y, hz, fx, fz, sx, sz, 0.1 * k, 0.032 * k, c);
            flat(vc, hx + fx * 0.09 * k, y, hz + fz * 0.09 * k, fx, fz, sx, sz, 0.03 * k, 0.022 * k, c);
        }
    }

    private static void paw(VertexConsumer vc, double x, double y, double z, double fx, double fz, double sx, double sz, double k, int c) {
        flat(vc, x, y, z, fx, fz, sx, sz, 0.1 * k, 0.11 * k, c); // the pad
        for (int toe = 0; toe < 4; toe++) {                       // four toes fanned ahead
            double off = (toe - 1.5) * 0.085 * k, ahead = (0.15 + (toe == 0 || toe == 3 ? 0 : 0.035)) * k;
            flat(vc, x + fx * ahead + sx * off, y, z + fz * ahead + sz * off, fx, fz, sx, sz, 0.035 * k, 0.03 * k, c);
        }
    }

    /** A soft flat oval: its colour in the middle, clear at the rim. */
    private static void oval(VertexConsumer vc, double x, double y, double z, double fx, double fz, double sx, double sz, double rl, double rw, int c) {
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz);
        int clear = c & 0xFFFFFF;
        for (int i = 0; i < 12; i++) {
            double a0 = i * Math.PI / 6, a1 = (i + 1) * Math.PI / 6;
            float ex0 = (float) (fx * Math.cos(a0) * rl + sx * Math.sin(a0) * rw), ez0 = (float) (fz * Math.cos(a0) * rl + sz * Math.sin(a0) * rw);
            float ex1 = (float) (fx * Math.cos(a1) * rl + sx * Math.sin(a1) * rw), ez1 = (float) (fz * Math.cos(a1) * rl + sz * Math.sin(a1) * rw);
            quad(vc, x0, y0, z0, x0, y0, z0, x0 + ex1, y0, z0 + ez1, x0 + ex0, y0, z0 + ez0, c, clear);
        }
    }

    /** A thin ring on the ground (the next sign's pulse). */
    private static void ring(VertexConsumer vc, double x, double y, double z, double r, int c) {
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz), w = 0.09F;
        int clear = c & 0xFFFFFF;
        for (int i = 0; i < 24; i++) {
            double a0 = i * Math.PI / 12, a1 = (i + 1) * Math.PI / 12;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ri = (float) r, ro = (float) r + w;
            quad(vc, x0 + c0 * ri, y0, z0 + s0 * ri, x0 + c1 * ri, y0, z0 + s1 * ri, x0 + c1 * ro, y0, z0 + s1 * ro, x0 + c0 * ro, y0, z0 + s0 * ro, c, clear);
        }
    }

    private static void flat(VertexConsumer vc, double x, double y, double z, double fx, double fz, double sx, double sz, double hl, double hw, int c) {
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz);
        quad(vc, x0 - (float) (fx * hl + sx * hw), y0, z0 - (float) (fz * hl + sz * hw), x0 + (float) (fx * hl - sx * hw), y0, z0 + (float) (fz * hl - sz * hw),
                x0 + (float) (fx * hl + sx * hw), y0, z0 + (float) (fz * hl + sz * hw), x0 - (float) (fx * hl - sx * hw), y0, z0 - (float) (fz * hl - sz * hw), c, c);
    }

    /** A small block lying on the ground along (fx, fz): half length, half width, height; a lighter top. */
    private static void box(VertexConsumer vc, double x, double y, double z, double fx, double fz, double sx, double sz, double hl, double hw, double h,
                            int top, int side) {
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz), y1 = y0 + (float) h;
        float[] c = CORNERS;
        for (int i = 0; i < 4; i++) {
            double a = (i == 1 || i == 2 ? 1 : -1) * hl, b = (i >= 2 ? 1 : -1) * hw;
            c[i * 2] = x0 + (float) (fx * a + sx * b);
            c[i * 2 + 1] = z0 + (float) (fz * a + sz * b);
        }
        quad(vc, c[0], y1, c[1], c[2], y1, c[3], c[4], y1, c[5], c[6], y1, c[7], top, top);
        for (int i = 0; i < 4; i++) {
            int p = i * 2, q = (i + 1) % 4 * 2;
            quad(vc, c[p], y0, c[p + 1], c[q], y0, c[q + 1], c[q], y1, c[q + 1], c[p], y1, c[p + 1], side, side);
        }
    }

    /** A soft dot of light facing the camera, bright in the middle. */
    private static void mote(VertexConsumer vc, double x, double y, double z, double r, int c) {
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz);
        int clear = c & 0xFFFFFF;
        for (int i = 0; i < 8; i++) {
            double a0 = i * Math.PI / 4, a1 = (i + 1) * Math.PI / 4;
            float u0 = (float) (Math.cos(a0) * r), v0 = (float) (Math.sin(a0) * r), u1 = (float) (Math.cos(a1) * r), v1 = (float) (Math.sin(a1) * r);
            quad(vc, x0, y0, z0, x0, y0, z0,
                    x0 + RIGHT.x * u1 + UP.x * v1, y0 + RIGHT.y * u1 + UP.y * v1, z0 + RIGHT.z * u1 + UP.z * v1,
                    x0 + RIGHT.x * u0 + UP.x * v0, y0 + RIGHT.y * u0 + UP.y * v0, z0 + RIGHT.z * u0 + UP.z * v0, c, clear);
        }
    }

    private static void streak(VertexConsumer vc, double x, double y, double z, double h, float w, int rgb, float a) {
        int c = argb(a, rgb), clear = rgb;
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz), hw = w / 2;
        quad(vc, x0 - RIGHT.x * hw, y0, z0 - RIGHT.z * hw, x0 + RIGHT.x * hw, y0, z0 + RIGHT.z * hw,
                x0 + RIGHT.x * hw, y0 + (float) h, z0 + RIGHT.z * hw, x0 - RIGHT.x * hw, y0 + (float) h, z0 - RIGHT.z * hw, c, clear);
    }

    /** Both faces, so a print reads from any side (the debug quads and lightning don't cull the same way). */
    private static void quad(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, int bottom, int top) {
        vc.addVertex(x0, y0, z0).setColor(bottom);
        vc.addVertex(x1, y1, z1).setColor(bottom);
        vc.addVertex(x2, y2, z2).setColor(top);
        vc.addVertex(x3, y3, z3).setColor(top);
        vc.addVertex(x3, y3, z3).setColor(top);
        vc.addVertex(x2, y2, z2).setColor(top);
        vc.addVertex(x1, y1, z1).setColor(bottom);
        vc.addVertex(x0, y0, z0).setColor(bottom);
    }
}
