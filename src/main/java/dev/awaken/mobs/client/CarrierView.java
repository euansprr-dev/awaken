package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.client.kit.ServerPace;
import dev.awaken.mobs.entity.Carriers;
import dev.awaken.mobs.net.MobsNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The carriers as you see and hear them (SPEC §47.2): each one the server sent ({@code mobs_carrier}) drawn on the
 * server's clock ({@link ServerPace}), so a wave's crest is where the server judges it and a volley falls on the tick
 * it lands. All in the colour of how you answer it (core.api.Strikes): a wave's crest about as high as the jump that
 * clears it, a volley's pods rising then falling on rings that close as they come, a locked line or wedge brightening
 * to its fire, a leap's ring, a field's edge forming before it can hurt, a pull's streaks drawn in. Each sounds from
 * where it is; a lock clicks. A pull draws the local player in a little (its server can't move a player), never as fast
 * as a walk away from it. Nothing allocated per frame but a lambda per pass.
 */
public final class CarrierView {
    private CarrierView() {}

    /** Carriers drawn at once at most (the oldest go first). */
    static final int MAX = 96;
    /** Loops playing at once at most (the rest are seen, not heard). */
    static final int LOOPS = 5;
    /** A carrier lingers this long after its end (a fire's flash, a fade). */
    static final int FADE = 8;

    static final class View {
        final MobsNet.Carrier c;
        final Carriers.Kind kind;
        final int cls, theme;
        final float seed;
        /** The game time the server ended it early (a leap landed), or MAX. */
        long endedAt = Long.MAX_VALUE;
        @Nullable Loop loop;

        View(MobsNet.Carrier c) {
            this.c = c;
            this.kind = Carriers.Kind.byOrdinal(c.kind());
            this.cls = Strikes.Kind.values()[Mth.clamp(c.cls(), 0, Strikes.Kind.values().length - 1)].colour();
            this.theme = c.theme() & 0xFFFFFF;
            this.seed = (c.id() * 0.618034F) % 1F;
        }

        /** Its end (ticks from its start): its life, or when the server ended it. */
        float end() {
            return endedAt == Long.MAX_VALUE ? c.life() : Math.min(c.life(), endedAt - c.start());
        }
    }

    private static final List<View> VIEWS = new ArrayList<>();

    // ---- what arrives ------------------------------------------------------------------------------------------------

    public static void onCarrier(MobsNet.Carrier c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (VIEWS.size() >= MAX) VIEWS.removeFirst();
        View v = new View(c);
        VIEWS.add(v);
        Vec3 o = new Vec3(c.x(), c.y(), c.z());
        switch (v.kind) {
            case LINE, WEDGE -> oneShot(mc, MobSounds.LOCK.get(), o, 1.0F, 1.0F);
            case RING -> oneShot(mc, MobSounds.LOCK.get(), o, 0.9F, 0.85F);
            case VOLLEY -> {
                for (int i = 0; i < Math.min(3, c.points().size()); i++) oneShot(mc, MobSounds.VOLLEY_FALL.get(), c.points().get(i), 0.9F, 0.95F + 0.05F * i);
            }
            case WAVE -> loop(mc, v, MobSounds.WAVE_ROLL.get(), 1.0F);
            case GROUND -> loop(mc, v, MobSounds.GROUND_RUN.get(), 0.9F);
            case FIELD -> loop(mc, v, MobSounds.FIELD_HISS.get(), 0.6F);
            case PULL -> loop(mc, v, MobSounds.PULL_DRAW.get(), 1.0F);
        }
    }

    public static void onEnd(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (View v : VIEWS) if (v.c.id() == id) v.endedAt = (long) ServerPace.now(mc.level, 0F);
    }

    public static void reset() {
        for (View v : VIEWS) if (v.loop != null) v.loop.ending = true;
        VIEWS.clear();
    }

    /** Client tick: drop what's done, and a pull draws the local player in. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            if (!VIEWS.isEmpty()) reset();
            return;
        }
        float now = ServerPace.now(level, 0F);
        LocalPlayer p = mc.player;
        for (int i = VIEWS.size() - 1; i >= 0; i--) {
            View v = VIEWS.get(i);
            float t = now - v.c.start();
            if (t > v.end() + FADE || t < -200) {
                if (v.loop != null) v.loop.ending = true;
                VIEWS.remove(i);
                continue;
            }
            if (v.kind == Carriers.Kind.PULL && p != null && t >= 0 && t <= v.end()) pull(p, v);
        }
    }

    /** The pull on the local player: toward its heart, a little each tick (a walk away still gains; a dash outruns it). */
    private static void pull(LocalPlayer p, View v) {
        if (p.isSpectator() || p.isCreative()) return;
        double dx = v.c.x() - p.getX(), dz = v.c.z() - p.getZ(), n = Math.sqrt(dx * dx + dz * dz);
        if (n < 1.0 || n > v.c.to() || Math.abs(p.getY() - v.c.y()) > 3) return;
        double k = v.c.speed() * 0.5 / n;
        p.setDeltaMovement(p.getDeltaMovement().add(dx * k, 0, dz * k));
    }

    // ---- sound -------------------------------------------------------------------------------------------------------

    private static void oneShot(Minecraft mc, SoundEvent e, Vec3 at, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(e, SoundSource.HOSTILE, volume, pitch, SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
    }

    private static void loop(Minecraft mc, View v, SoundEvent e, float volume) {
        int playing = 0;
        for (View o : VIEWS) if (o.loop != null && !o.loop.isStopped()) playing++;
        if (playing >= LOOPS) return;
        v.loop = new Loop(v, e, volume);
        mc.getSoundManager().play(v.loop);
    }

    /** A carrier's own sound, from the point of it nearest you, easing in and out with it. */
    static final class Loop extends AbstractTickableSoundInstance {
        private final View v;
        private final float base;
        private float level;
        boolean ending;

        Loop(View v, SoundEvent e, float base) {
            super(e, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.v = v;
            this.base = base;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 0.95F + v.seed * 0.1F;
            this.x = v.c.x();
            this.y = v.c.y();
            this.z = v.c.z();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                stop();
                return;
            }
            float t = ServerPace.now(mc.level, 0F) - v.c.start();
            if (t > v.end()) ending = true;
            float target = ending || t < 0 ? 0F : base;
            level += (target - level) * (ending ? 0.35F : 0.5F);
            volume = Math.max(0.001F, level);
            if (ending && level < 0.01F) {
                stop();
                return;
            }
            Vec3 at = near(v, Math.max(0, t), mc.player.position());
            x = at.x;
            y = at.y;
            z = at.z;
        }
    }

    /** The point of a carrier nearest {@code to} now (where its sound comes from). */
    static Vec3 near(View v, float t, Vec3 to) {
        MobsNet.Carrier c = v.c;
        Vec3 o = new Vec3(c.x(), c.y(), c.z());
        switch (v.kind) {
            case WAVE, FIELD -> {
                float r = v.kind == Carriers.Kind.WAVE ? Math.min(c.to(), c.from() + c.speed() * t) : c.width();
                double dx = to.x - o.x, dz = to.z - o.z, n = Math.sqrt(dx * dx + dz * dz);
                return n < 1.0E-3 ? o : new Vec3(o.x + dx / n * r, o.y + 0.4, o.z + dz / n * r);
            }
            case GROUND -> {
                float d = Math.min(c.to(), c.speed() * t);
                return new Vec3(o.x + c.dx() * d, o.y + 0.2, o.z + c.dz() * d);
            }
            default -> {
                return o;
            }
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    private static double cx, cy, cz;
    private static float now;
    private static final Quaternionf CAM = new Quaternionf();
    static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || VIEWS.isEmpty()) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        now = ServerPace.now(level, state.worldPartialTicks);
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        RenderType shade = CarrierPipelines.shade();
        if (shade != null) out.submitCustomGeometry(ps, shade, (pose, vc) -> draw(vc, true));
        out.order(1).submitCustomGeometry(ps, CarrierPipelines.light(), (pose, vc) -> draw(vc, false));
    }

    private static void draw(VertexConsumer vc, boolean shade) {
        try {
            for (View v : VIEWS) {
                float t = now - v.c.start();
                if (t < 0) continue;
                switch (v.kind) {
                    case WAVE -> wave(vc, v, t, shade);
                    case VOLLEY -> volley(vc, v, t, shade);
                    case GROUND -> ground(vc, v, t, shade);
                    case LINE -> line(vc, v, t, shade);
                    case RING -> ring(vc, v, t, shade);
                    case WEDGE -> wedge(vc, v, t, shade);
                    case FIELD -> field(vc, v, t, shade);
                    case PULL -> pullDraw(vc, v, t, shade);
                }
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[mobs] drawing a carrier failed", ex);
        }
    }

    /** How a carrier fades in its last ticks past its end (1 while alive). */
    private static float fade(View v, float t) {
        float end = v.end();
        return t <= end ? 1F : Math.max(0F, 1F - (t - end) / FADE);
    }

    /** A wave: a crest leaning out as it rolls, about the height a jump clears, bright at its lip; its reach a faint ring. */
    private static void wave(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        float k = fade(v, t);
        if (k <= 0) return;
        float r = Math.min(c.to(), c.from() + c.speed() * t);
        float done = t > v.end() ? 1F - k : 0F;
        double y = c.y() + 0.03;
        float h = Carriers.CREST * (1F - done * 0.8F);
        int segs = Mth.clamp((int) (r * 7), 20, 96);
        if (shade) {
            band(vc, c.x(), y, c.z(), r - 0.8F, r, 0, h * 0.9F, segs, argb(dark(v.cls), 0F), argb(dark(v.cls), 0.35F * k));
            return;
        }
        // the crest: a sloped wall, the theme's colour at its foot, the class's at its lip
        band(vc, c.x(), y, c.z(), r - 0.9F, r, 0, h, segs, argb(v.theme, 0F), argb(v.cls, 0.75F * k));
        band(vc, c.x(), y, c.z(), r - 0.05F, r + 0.05F, h - 0.18F, h + 0.04F, segs, argb(hot(v.cls, 0.55F), 0.95F * k), argb(hot(v.cls, 0.55F), 0.95F * k));
        // its front along the ground, and how far it will roll
        band(vc, c.x(), y, c.z(), r, r + 0.35F, 0.02F, 0.02F, segs, argb(v.cls, 0.5F * k), argb(v.cls, 0F));
        if (t <= v.end()) band(vc, c.x(), y, c.z(), c.to() - 0.06F, c.to() + 0.06F, 0.02F, 0.02F, Mth.clamp((int) (c.to() * 5), 20, 80),
                argb(v.cls, 0.16F), argb(v.cls, 0.16F));
    }

    /** A volley: pods rising from the body and falling on rings that close as they come; a flash where each lands. */
    private static void volley(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        float life = Math.max(1, c.life());
        float f = Math.min(1F, t / life);
        float k = fade(v, t);
        if (k <= 0) return;
        for (Vec3 p : c.points()) {
            double py = p.y + 0.03;
            float w = c.width();
            if (t <= life) {
                if (shade) {
                    band(vc, p.x, py, p.z, w - 0.14F, w + 0.04F, 0.01F, 0.01F, 32, argb(dark(v.cls), 0.4F), argb(dark(v.cls), 0.4F));
                    continue;
                }
                // the ring where it falls, filling as it comes, and a ring closing on its edge: the time left
                band(vc, p.x, py, p.z, w - 0.12F, w + 0.02F, 0.01F, 0.01F, 36, argb(v.cls, 0.55F + 0.4F * f), argb(v.cls, 0.55F + 0.4F * f));
                band(vc, p.x, py, p.z, 0F, w - 0.12F, 0.01F, 0.01F, 36, argb(v.cls, 0.06F + 0.2F * f), argb(v.cls, 0.1F + 0.25F * f));
                float inner = w * (1F - f);
                band(vc, p.x, py, p.z, Math.max(0F, inner - 0.08F), inner + 0.04F, 0.01F, 0.01F, 36, argb(hot(v.cls, 0.4F), 0.6F), argb(hot(v.cls, 0.4F), 0.6F));
                // the pod: up out of the body, over and down onto its ring
                double arc = 4.0 + 0.25 * Math.sqrt(p.distanceToSqr(c.x(), c.y(), c.z()));
                Vec3 at = pod(c, p, f, arc), was = pod(c, p, Math.max(0F, f - 0.09F), arc);
                ribbon(vc, was, at, 0.28F, argb(v.theme, 0.5F), true);
                flare(vc, at, 0.55F, argb(hot(v.cls, 0.5F), 0.9F));
                flare(vc, at, 1.3F, argb(v.cls, 0.25F));
            } else if (!shade) {
                float g = 1F - k;
                band(vc, p.x, py, p.z, w * (0.6F + 0.8F * g), w * (0.75F + 0.8F * g), 0.02F, 0.3F * k, 36, argb(hot(v.cls, 0.5F), 0.8F * k), argb(v.cls, 0F));
                flare(vc, new Vec3(p.x, py + 0.6, p.z), 1.6F * k, argb(hot(v.cls, 0.6F), 0.7F * k));
            }
        }
    }

    private static Vec3 pod(MobsNet.Carrier c, Vec3 p, float f, double arc) {
        double x = Mth.lerp(f, c.x(), p.x), y = Mth.lerp(f, c.y(), p.y) + arc * 4 * f * (1 - f), z = Mth.lerp(f, c.z(), p.z);
        return new Vec3(x, y, z);
    }

    /** A ground line: the ground splitting from the body along its line, its front a bright spur. */
    private static void ground(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        float k = fade(v, t);
        if (k <= 0) return;
        float d = Math.min(c.to(), c.speed() * Math.min(t, v.end()));
        double y = c.y() + 0.04;
        double sx = -c.dz() * c.width(), sz = c.dx() * c.width();
        double fx = c.x() + c.dx() * d, fz = c.z() + c.dz() * d;
        if (shade) {
            flatQuad(vc, c.x() - sx, y, c.z() - sz, c.x() + sx, y, c.z() + sz, fx + sx, y, fz + sz, fx - sx, y, fz - sz, argb(dark(v.cls), 0.3F * k));
            return;
        }
        flatQuad(vc, c.x() - sx * 0.5, y, c.z() - sz * 0.5, c.x() + sx * 0.5, y, c.z() + sz * 0.5, fx + sx, y, fz + sz, fx - sx, y, fz - sz,
                argb(v.cls, 0.35F * k));
        // where it is still going (its whole line, faint)
        double ex = c.x() + c.dx() * c.to(), ez = c.z() + c.dz() * c.to();
        if (t <= v.end()) flatQuad(vc, fx - sx, y, fz - sz, fx + sx, y, fz + sz, ex + sx, y, ez + sz, ex - sx, y, ez - sz, argb(v.cls, 0.1F));
        Vec3 front = new Vec3(fx, y, fz);
        ribbon(vc, front, front.add(0, 0.9 * k, 0), 0.35F, argb(hot(v.cls, 0.5F), 0.85F * k), false);
        flare(vc, front.add(0, 0.3, 0), 0.9F, argb(v.cls, 0.55F * k));
    }

    /** A locked line: a thin line brightening and quickening to its fire, a flash at the lock and a burst as it fires. */
    private static void line(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        if (c.points().isEmpty()) return;
        float k = fade(v, t);
        if (k <= 0) return;
        Vec3 a = new Vec3(c.x(), c.y(), c.z()), b = c.points().getFirst();
        float life = Math.max(1, v.end());
        float f = Math.min(1F, t / life);
        float beat = 0.75F + 0.25F * Mth.sin(t * (0.6F + 2.2F * f));
        if (shade) {
            ribbon(vc, a, b, c.width() * 1.6F, argb(dark(v.cls), 0.3F * k), false);
            return;
        }
        if (t <= life) {
            ribbon(vc, a, b, c.width() * (0.8F + 0.6F * f), argb(v.cls, (0.25F + 0.5F * f) * beat), false);
            ribbon(vc, a, b, 0.07F, argb(hot(v.cls, 0.6F), 0.6F + 0.4F * f), false);
            flare(vc, b, 0.5F + 0.3F * f, argb(v.cls, 0.6F * beat));
            if (t < 5) {
                float l = 1F - t / 5F; // the lock's flash
                ribbon(vc, a, b, c.width() * 3F, argb(hot(v.cls, 0.7F), 0.7F * l), false);
                flare(vc, a, 1.4F * l, argb(hot(v.cls, 0.7F), 0.9F * l));
            }
        } else {
            ribbon(vc, a, b, c.width() * 2.6F * k, argb(hot(v.cls, 0.6F), 0.9F * k), false);
        }
    }

    /** A landing ring: where the leap comes down, pulsing, its inside filling as it falls. */
    private static void ring(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        float k = fade(v, t);
        if (k <= 0) return;
        float w = c.width();
        double y = c.y() + 0.03;
        float beat = 0.75F + 0.25F * Mth.sin(t * 1.3F);
        if (shade) {
            band(vc, c.x(), y, c.z(), w - 0.16F, w + 0.04F, 0.01F, 0.01F, 40, argb(dark(v.cls), 0.4F * k), argb(dark(v.cls), 0.4F * k));
            return;
        }
        band(vc, c.x(), y, c.z(), w - 0.14F, w + 0.02F, 0.01F, 0.01F, 40, argb(v.cls, 0.85F * beat * k), argb(v.cls, 0.85F * beat * k));
        band(vc, c.x(), y, c.z(), 0F, w - 0.14F, 0.01F, 0.01F, 40, argb(v.cls, 0.1F * k), argb(v.cls, 0.28F * k));
        if (t < 5) flare(vc, new Vec3(c.x(), y + 0.3, c.z()), 1.6F * (1F - t / 5F), argb(hot(v.cls, 0.6F), 0.7F));
    }

    /** A wedge: a cone's reach on the ground, filling to its fire. */
    private static void wedge(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        float k = fade(v, t);
        if (k <= 0) return;
        float life = Math.max(1, v.end());
        float f = Math.min(1F, t / life);
        double y = c.y() + 0.05;
        float half = (float) Math.acos(Mth.clamp(c.width(), -1F, 1F));
        float base = (float) Math.atan2(c.dz(), c.dx());
        int segs = 14;
        float r = c.to();
        int fill = shade ? argb(dark(v.cls), 0.3F * k) : argb(v.cls, (t <= life ? 0.1F + 0.3F * f : 0.6F) * k);
        for (int i = 0; i < segs; i++) {
            float a0 = base - half + 2 * half * i / segs, a1 = base - half + 2 * half * (i + 1) / segs;
            double x0 = c.x() + Mth.cos(a0) * r, z0 = c.z() + Mth.sin(a0) * r, x1 = c.x() + Mth.cos(a1) * r, z1 = c.z() + Mth.sin(a1) * r;
            quad(vc, c.x(), y, c.z(), c.x(), y, c.z(), x1, y, z1, x0, y, z0, shade ? fill : argb(v.cls, 0.02F), fill);
        }
        if (shade) return;
        Vec3 o = new Vec3(c.x(), y, c.z());
        ribbon(vc, o, new Vec3(c.x() + Mth.cos(base - half) * r, y, c.z() + Mth.sin(base - half) * r), 0.12F, argb(hot(v.cls, 0.4F), 0.8F * k), true);
        ribbon(vc, o, new Vec3(c.x() + Mth.cos(base + half) * r, y, c.z() + Mth.sin(base + half) * r), 0.12F, argb(hot(v.cls, 0.4F), 0.8F * k), true);
        if (t < 5) flare(vc, o.add(0, 0.6, 0), 1.5F * (1F - t / 5F), argb(hot(v.cls, 0.6F), 0.8F));
    }

    /** A field: its edge forming (dashed, faint) while it can't hurt yet, then solid with a haze inside; fading at its end. */
    private static void field(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        float k = fade(v, t);
        if (k <= 0) return;
        float w = c.width();
        double y = c.y() + 0.04;
        boolean forming = t < Carriers.FIELD_SAFE;
        float tail = Math.min(1F, (v.end() - t) / 10F);
        float a = (forming ? 0.25F + 0.5F * t / Carriers.FIELD_SAFE : 0.75F) * Math.max(0F, Math.min(tail, 1F)) * k;
        if (shade) {
            band(vc, c.x(), y, c.z(), 0F, w, 0.01F, 0.01F, 32, argb(dark(v.theme), 0.18F * a), argb(dark(v.theme), 0.3F * a));
            return;
        }
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            if (forming && (i & 1) == 1) continue;
            float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
            arc(vc, c.x(), y, c.z(), w - 0.1F, w + 0.03F, a0, a1, argb(v.cls, a), argb(v.cls, a));
        }
        if (!forming) band(vc, c.x(), y, c.z(), 0F, w - 0.1F, 0.01F, 0.25F, 32, argb(v.theme, 0.05F * a), argb(v.theme, 0.22F * a));
    }

    /** A pull: streaks of water or wind sliding in to its heart from its edge. */
    private static void pullDraw(VertexConsumer vc, View v, float t, boolean shade) {
        MobsNet.Carrier c = v.c;
        float k = fade(v, t);
        if (k <= 0 || shade) return;
        double y = c.y() + 0.35;
        float r0 = c.to();
        int spokes = 22;
        for (int i = 0; i < spokes; i++) {
            float ang = i * Mth.TWO_PI / spokes + v.seed * 3F;
            float ph = ((i * 0.37F + v.seed + t * 0.07F) % 1F);
            float r = r0 * (1F - ph);
            if (r < 0.8F) continue;
            float cs = Mth.cos(ang), sn = Mth.sin(ang);
            Vec3 a = new Vec3(c.x() + cs * r, y, c.z() + sn * r), b = new Vec3(c.x() + cs * Math.max(0.5F, r - 1.4F), y, c.z() + sn * Math.max(0.5F, r - 1.4F));
            ribbon(vc, a, b, 0.18F, argb(v.theme, 0.55F * (r / r0) * k), true);
        }
        band(vc, c.x(), c.y() + 0.03, c.z(), r0 - 0.06F, r0 + 0.06F, 0.01F, 0.01F, 48, argb(v.theme, 0.3F * k), argb(v.theme, 0.3F * k));
    }

    // ---- shapes (camera-relative quads) --------------------------------------------------------------------------------

    /** A ring band from radius {@code r0} at height {@code y0} to {@code r1} at {@code y1}: {@code inner} at r0, {@code outer} at r1. */
    private static void band(VertexConsumer vc, double x, double y, double z, float r0, float r1, float y0, float y1, int segs, int inner, int outer) {
        for (int i = 0; i < segs; i++) {
            float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad(vc, x + c1 * r1, y + y1, z + s1 * r1, x + c0 * r1, y + y1, z + s0 * r1,
                    x + c0 * r0, y + y0, z + s0 * r0, x + c1 * r0, y + y0, z + s1 * r0, outer, inner);
        }
    }

    private static void arc(VertexConsumer vc, double x, double y, double z, float r0, float r1, float a0, float a1, int inner, int outer) {
        float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
        quad(vc, x + c1 * r1, y, z + s1 * r1, x + c0 * r1, y, z + s0 * r1, x + c0 * r0, y, z + s0 * r0, x + c1 * r0, y, z + s1 * r0, outer, inner);
    }

    private static void flatQuad(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                                 double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        quad(vc, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, argb, argb);
    }

    /** A ribbon from a to b: facing the camera, or ({@code flat}) lying on the ground. */
    static void ribbon(VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb, boolean flat) {
        Vec3 d = b.subtract(a);
        Vec3 n;
        if (flat) n = new Vec3(-d.z, 0, d.x);
        else n = d.cross(a.add(d.scale(0.5)).subtract(cx, cy, cz));
        double len = n.length();
        if (len < 1e-6) return;
        n = n.scale(width * 0.5 / len);
        int rim = argb & 0x00FFFFFF;
        quad(vc, a.x, a.y, a.z, b.x, b.y, b.z, b.x + n.x, b.y + n.y, b.z + n.z, a.x + n.x, a.y + n.y, a.z + n.z, argb, rim);
        quad(vc, a.x, a.y, a.z, b.x, b.y, b.z, b.x - n.x, b.y - n.y, b.z - n.z, a.x - n.x, a.y - n.y, a.z - n.z, argb, rim);
    }

    /** A soft round glow facing the camera: bright at its heart, nothing at its rim. */
    static void flare(VertexConsumer vc, Vec3 p, float r, int argb) {
        if (r <= 0.001F || (argb >>> 24) == 0) return;
        int rim = argb & 0x00FFFFFF;
        int n = r < 1F ? 8 : 12;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            quad(vc, p.x, p.y, p.z, p.x, p.y, p.z,
                    p.x + RIGHT.x * c1 + UP.x * s1, p.y + RIGHT.y * c1 + UP.y * s1, p.z + RIGHT.z * c1 + UP.z * s1,
                    p.x + RIGHT.x * c0 + UP.x * s0, p.y + RIGHT.y * c0 + UP.y * s0, p.z + RIGHT.z * c0 + UP.z * s0, argb, rim);
        }
    }

    /** One quad, camera-relative: the first two corners take {@code inner}, the last two {@code outer}. */
    private static void quad(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, int inner, int outer) {
        vc.addVertex((float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz)).setColor(inner);
        vc.addVertex((float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).setColor(inner);
        vc.addVertex((float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).setColor(outer);
        vc.addVertex((float) (x3 - cx), (float) (y3 - cy), (float) (z3 - cz)).setColor(outer);
    }

    static void camera(double x, double y, double z) {
        cx = x;
        cy = y;
        cz = z;
    }

    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Toward white by {@code w}. */
    static int hot(int rgb, float w) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * w);
        g = (int) (g + (255 - g) * w);
        b = (int) (b + (255 - b) * w);
        return (r << 16) | (g << 8) | b;
    }

    /** A darker shade of the colour, for the alpha-blended underlay. */
    static int dark(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r * 2 / 5) << 16) | ((g * 2 / 5) << 8) | (b * 2 / 5);
    }
}
