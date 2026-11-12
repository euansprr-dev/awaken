package dev.awaken.frontier.client;

import dev.awaken.frontier.FrontierModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The air the skiff flies through (SPEC §67.5), all on the client and never sent: streaks of wind that hang in the air
 * so the skiff's own speed carries them past, cloud that thickens as it climbs into it and thins as it comes out, dust
 * blown off the pad when it wakes and when it sets down, and motes of the lift core's light. Its own sprites
 * ({@code tools/art/skiff_art.py}). And the two beds of sound under a flight: the core's hum and the wind.
 */
final class FlightAir {
    private FlightAir() {}

    static final int WISP = 0, STREAK = 1, MOTE = 2;
    /** A streak whose line passes nearer the eye than this isn't drawn; from here to {@link #CLEAR} it fades in. */
    static final float NEAR = 2F, CLEAR = 5F;

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(FrontierModule.P_WISP.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, WISP));
        e.registerSpriteSet(FrontierModule.P_STREAK.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, STREAK));
        e.registerSpriteSet(FrontierModule.P_MOTE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, MOTE));
    }

    private static @Nullable Air make(SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz, int tint, float size,
                                      float peak, int life) {
        Particle p = Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        if (!(p instanceof Air air)) return null;
        air.setColor(((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F);
        air.size(size);
        air.peak = peak;
        air.setLifetime(life);
        return air;
    }

    /**
     * The air ahead of a skiff moving at {@code speed} (blocks a tick) along a heading, seen from its helm: wind streaks
     * lying along the heading and, where {@code cloud} is above zero, cloud. They're put well ahead and off the line of
     * flight, and stay where they are: the skiff passes them.
     */
    static void stream(double ex, double ey, double ez, double hx, double hy, double hz, float speed, float cloud, RandomSource r) {
        if (speed < 0.25F) return;
        // two directions across the heading
        double rx = -hz, rz = hx, rl = Math.hypot(rx, rz);
        if (rl < 1.0E-4) return;
        rx /= rl;
        rz /= rl;
        double ux = -rz * hy, uy = rz * hx - rx * hz, uz = rx * hy;
        int streaks = Math.min(4, (int) (speed * 0.9F + r.nextFloat()));
        for (int i = 0; i < streaks; i++) {
            double ahead = 26 + r.nextDouble() * 40, ang = r.nextDouble() * Math.PI * 2, rad = 3.5 + r.nextDouble() * 15;
            double a = Math.cos(ang) * rad, b = Math.sin(ang) * rad * 0.7;
            Air s = make(FrontierModule.P_STREAK.get(), ex + hx * ahead + rx * a + ux * b, ey + hy * ahead + uy * b, ez + hz * ahead + rz * a + uz * b,
                    0, 0, 0, 0xF4FAFF, 2.2F + speed * 1.5F, 0.42F, 30);
            if (s != null) s.along((float) hx, (float) hy, (float) hz);
        }
        int wisps = cloud > 0.02F ? (int) (cloud * 3.2F + r.nextFloat()) : r.nextFloat() < 0.12F ? 1 : 0;
        for (int i = 0; i < wisps; i++) {
            double ahead = 34 + r.nextDouble() * 56, ang = r.nextDouble() * Math.PI * 2, rad = 6 + r.nextDouble() * 24;
            double a = Math.cos(ang) * rad, b = Math.sin(ang) * rad * 0.6;
            Air w = make(FrontierModule.P_WISP.get(), ex + hx * ahead + rx * a + ux * b, ey + hy * ahead + uy * b, ez + hz * ahead + rz * a + uz * b,
                    (r.nextFloat() - 0.5) * 0.02, 0, (r.nextFloat() - 0.5) * 0.02, 0xFFFFFF, 5F + r.nextFloat() * 6F + cloud * 5F,
                    0.16F + cloud * 0.36F, 56);
            if (w != null) w.grow = 0.02F;
        }
    }

    /** Dust blown out across the pad from under the keel, and a few motes of the core's light let go. */
    static void wash(double x, double y, double z, float strength, RandomSource r) {
        int n = 1 + (int) (strength * 3 + r.nextFloat());
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2, rad = 1.5 + r.nextDouble() * 2.5, out = 0.07 + r.nextDouble() * 0.09 * (0.5 + strength);
            Air d = make(FrontierModule.P_WISP.get(), x + Math.cos(ang) * rad, y + 0.15 + r.nextDouble() * 0.3, z + Math.sin(ang) * rad,
                    Math.cos(ang) * out, 0.004 + r.nextDouble() * 0.01, Math.sin(ang) * out, 0xDAD3C2, 1.3F + r.nextFloat() * 1.4F, 0.34F * strength, 34);
            if (d != null) {
                d.grow = 0.035F;
                d.drag = 0.95F;
            }
        }
        if (r.nextFloat() < 0.7F) {
            Air m = make(FrontierModule.P_MOTE.get(), x + (r.nextFloat() - 0.5) * 2.4, y + 0.6 + r.nextDouble() * 1.4, z + (r.nextFloat() - 0.5) * 2.4,
                    (r.nextFloat() - 0.5) * 0.02, 0.02 + r.nextDouble() * 0.03, (r.nextFloat() - 0.5) * 0.02, 0x9AD4FF, 0.5F + r.nextFloat() * 0.5F, 0.9F, 34);
            if (m != null) m.glow = true;
        }
    }

    /** One sprite of the air: a cloud wisp, a streak of wind lying along the flight, or a mote of light. */
    static final class Air extends SingleQuadParticle {
        final int kind;
        boolean glow;
        float peak = 0.5F, grow, drag = 1F;
        private float ax, ay, az;
        private boolean axial;

        Air(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, int kind) {
            super(level, x, y, z, sprites.get(level.getRandom()));
            this.kind = kind;
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.gravity = 0;
            this.friction = 1;
            this.hasPhysics = false;
            this.alpha = 0;
            this.quadSize = 0.1F;
            this.lifetime = 40;
            this.roll = kind == WISP ? random.nextFloat() * Mth.TWO_PI : 0;
            this.oRoll = roll;
        }

        void size(float blocks) {
            quadSize = blocks * 0.5F;
        }

        /** A streak lies along this direction, turned about it to face the eye. */
        void along(float x, float y, float z) {
            ax = x;
            ay = y;
            az = z;
            axial = true;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            float t = age / (float) lifetime;
            alpha = peak * Math.min(1, Math.min(t * 5, (1 - t) * 3));
            quadSize += grow;
            xd *= drag;
            zd *= drag;
            if (xd != 0 || yd != 0 || zd != 0) setPos(x + xd, y + yd, z + zd);
        }

        @Override
        public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
            if (!axial) {
                super.extract(state, camera, partialTick);
                return;
            }
            // the quad's long side along the flight, its face turned to the eye about that line
            Vec3 cam = camera.position();
            Vector3f along = new Vector3f(ax, ay, az).normalize();
            Vector3f toEye = new Vector3f((float) (cam.x - x), (float) (cam.y - y), (float) (cam.z - z));
            toEye.sub(new Vector3f(along).mul(toEye.dot(along)));
            // a streak whose line passes close by the eye would fill the view as a pale band: it fades out inside five blocks
            float off = toEye.length();
            if (off < NEAR) return;
            toEye.normalize();
            Vector3f up = new Vector3f(toEye).cross(along);
            float full = alpha;
            alpha = full * Mth.clamp((off - NEAR) / (CLEAR - NEAR), 0F, 1F);
            extractRotatedQuad(state, camera, new Quaternionf().setFromNormalized(new Matrix3f(along, up, toEye)), partialTick);
            alpha = full;
        }

        @Override
        protected int getLightCoords(float a) {
            return glow ? 0xF000F0 : super.getLightCoords(a);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }

    /** A bed of sound in the ear that follows the flight: its level and pitch are set a tick at a time and eased. */
    static final class Bed extends AbstractTickableSoundInstance {
        private float wantVolume, wantPitch;
        private boolean ending;

        Bed(SoundEvent e, float pitch) {
            super(e, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
            this.pitch = pitch;
            this.wantPitch = pitch;
        }

        void set(float volume, float pitch) {
            wantVolume = volume;
            wantPitch = pitch;
        }

        /** Fades out and stops. */
        void end() {
            ending = true;
            wantVolume = 0;
        }

        @Override
        public void tick() {
            volume = Math.max(0.001F, volume + (wantVolume - volume) * 0.12F);
            pitch += (wantPitch - pitch) * 0.1F;
            if (ending && volume <= 0.004F) stop();
        }
    }

    static Bed bed(SoundEvent e, float pitch) {
        Bed b = new Bed(e, pitch);
        Minecraft.getInstance().getSoundManager().play(b);
        return b;
    }
}
