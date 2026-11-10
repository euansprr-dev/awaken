package dev.awaken.armour.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * A set's living parts (SPEC §44.10): one or two things that should not stand still, a halo, a marker, a plume, wings.
 * Each is some of a bone's own boxes written as a mesh of their own (tools/art/specs/armour_finish.py, LIVE) with what
 * moves it, read from {@code assets/awaken/armour/<set>/live.json}. Its motion is worked out from the frame's render
 * state alone (the wearer's age, stride and the wind of a fall): nothing is kept per wearer, sent or saved, and everyone
 * sees the same.
 */
public final class ArmourLive {
    private ArmourLive() {}

    /** How a part moves: round its axis, leaning round and round, swinging, wandering, or swelling on a double beat. */
    public enum Kind { TURN, TILT, SWAY, DRIFT, PULSE }

    /**
     * One living part, in its bone's part space (blocks; y down, z back). speed: degrees a tick (turn), radians a tick
     * (tilt, sway, drift), beats a tick (pulse). amount: degrees (tilt, sway), a share (pulse). reach: blocks (drift).
     * u, v: the two directions a tilt leans in. idle, stride, wind: what shares of a sway come from standing, from the
     * stride and from the wind.
     */
    public record Part(Bone bone, ArmourMesh mesh, Kind kind, float px, float py, float pz, float ax, float ay, float az,
                       float ux, float uy, float uz, float vx, float vy, float vz, float speed, float amount, float reach, float bob,
                       float phase, float idle, float stride, float wind) {}

    static boolean on(Part[] parts, Bone bone) {
        for (Part p : parts) if (p.bone == bone) return true;
        return false;
    }

    private static final Quaternionf Q = new Quaternionf(); // the render thread's own scratch

    /**
     * Moves the pose (already on the part's bone) to where the part is now. age in ticks; stride 0..1 and walk the
     * wearer's stride and its phase; wind 0..1 how hard the air moves past them (a fall, a glide, a sprint).
     */
    public static void pose(PoseStack ps, Part p, float age, float stride, float walk, float wind) {
        float bob = p.bob == 0 ? 0 : p.bob * Mth.sin(0.05F * age + p.phase);
        if (p.kind == Kind.DRIFT) {
            float t = p.speed * age;
            ps.translate(p.reach * Mth.sin(t + p.phase), -p.reach * Mth.sin(0.7F * t + 2 * p.phase) - bob, -p.reach * Mth.sin(1.3F * t + 3 * p.phase));
            return;
        }
        ps.translate(p.px, p.py - bob, p.pz);
        switch (p.kind) {
            case TURN -> ps.rotate(Q.rotationAxis(p.speed * age * Mth.DEG_TO_RAD, p.ax, p.ay, p.az));
            case TILT -> {
                float t = p.speed * age + p.phase, a = p.amount * Mth.DEG_TO_RAD;
                ps.rotate(Q.rotationAxis(a * Mth.sin(t), p.ux, p.uy, p.uz));
                ps.rotate(Q.rotationAxis(a * Mth.cos(t), p.vx, p.vy, p.vz));
            }
            case SWAY -> {
                float a = p.amount * (p.idle * Mth.sin(p.speed * age + p.phase) + p.stride * stride * Mth.sin(walk * 0.6662F + p.phase) + p.wind * wind);
                ps.rotate(Q.rotationAxis(a * Mth.DEG_TO_RAD, p.ax, p.ay, p.az));
            }
            case PULSE -> {
                float f = Mth.frac(p.speed * age + p.phase), a = (f - 0.1F) * 9F, b = (f - 0.32F) * 9F;
                float s = 1F + p.amount * ((float) Math.exp(-a * a) + 0.6F * (float) Math.exp(-b * b));
                ps.scale(s, s, s);
            }
            default -> { }
        }
        ps.translate(-p.px, -p.py, -p.pz);
    }

    /**
     * The wind past a wearer, 0..1: an elytra's glide is all of it; else the cape's rise in a fall, or its lean in a
     * run (a walk barely counts: wings stay folded until you move fast).
     */
    static float wind(net.minecraft.client.renderer.entity.state.HumanoidRenderState state) {
        if (state.isFallFlying) return 1F;
        if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState a)
            return Mth.clamp(Math.max(a.capeFlap / 32F, (a.capeLean - 50F) / 100F), 0F, 1F);
        return 0F;
    }

    // ---- mastery IV's parts grow in (SPEC §44.10) ------------------------------------------------------------------------

    /** When each slot's awakened parts first showed on one wearer (0: not showing), so they grow in and never pop. */
    static final class Wake {
        final long[] since = new long[4];

        /** 0..1, eased out: how much of the slot's awakened parts has grown. */
        float grown(int slot) {
            long age = net.minecraft.util.Util.getMillis() - since[slot];
            if (age >= GROW_MS) return 1F;
            float k = 1F - age / (float) GROW_MS;
            return Math.max(0.02F, 1F - k * k * k); // never nothing: a pose scaled to zero has no normals
        }
    }

    private static final long GROW_MS = 500;
    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Wake> WAKES = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

    /** The wearer's record, made on its first awakened piece. Kept per entity id until you leave the world. */
    static Wake wake(int entity) {
        Wake w = WAKES.get(entity);
        if (w == null) {
            if (WAKES.size() > 512) WAKES.clear(); // a long session past many wearers: start again (parts regrow once)
            WAKES.put(entity, w = new Wake());
        }
        return w;
    }

    static @org.jspecify.annotations.Nullable Wake woken(int entity) {
        return WAKES.get(entity);
    }

    static void clear() {
        WAKES.clear();
    }

    /** Scales the pose about the middle of a mesh (its bounds), so its parts grow where they sit. Only while growing. */
    static void grow(PoseStack ps, ArmourMesh mesh, float k) {
        float[] p = mesh.pos();
        float x0 = Float.MAX_VALUE, y0 = x0, z0 = x0, x1 = -x0, y1 = -x0, z1 = -x0;
        for (int i = 0; i + 2 < p.length; i += 3) {
            x0 = Math.min(x0, p[i]);
            x1 = Math.max(x1, p[i]);
            y0 = Math.min(y0, p[i + 1]);
            y1 = Math.max(y1, p[i + 1]);
            z0 = Math.min(z0, p[i + 2]);
            z1 = Math.max(z1, p[i + 2]);
        }
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        ps.translate(cx, cy, cz);
        ps.scale(k, k, k);
        ps.translate(-cx, -cy, -cz);
    }

    /** One part from live.json (the kit writes it; a field missing or a name unknown throws, and the set stands still). */
    static Part read(JsonObject o, Bone bone, ArmourMesh mesh) {
        JsonArray pv = o.getAsJsonArray("pivot"), ax = o.getAsJsonArray("axis"), u = o.getAsJsonArray("u"), v = o.getAsJsonArray("v");
        return new Part(bone, mesh, Kind.valueOf(o.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)),
                pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat(),
                ax.get(0).getAsFloat(), ax.get(1).getAsFloat(), ax.get(2).getAsFloat(),
                u.get(0).getAsFloat(), u.get(1).getAsFloat(), u.get(2).getAsFloat(),
                v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat(),
                o.get("speed").getAsFloat(), o.get("amount").getAsFloat(), o.get("reach").getAsFloat(), o.get("bob").getAsFloat(),
                o.get("phase").getAsFloat(), o.get("idle").getAsFloat(), o.get("stride").getAsFloat(), o.get("wind").getAsFloat());
    }
}
