package dev.awaken.mobs.client;

import dev.awaken.mobs.MobParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Quaternionf;

/**
 * The seep heart's particles (sprites from tools/art/specs/fauna3.py), all the Deep's violet and full-bright: motes that
 * drift, motes drawn in along their speed, falling slivers of Deepglass, and two flat on the floor (drawn face up and
 * face down, so they read from anywhere): cracks of light where roots will burst, and rings that rush out or hold as a
 * tell. A flat one carries its numbers in its speed (MobParticles).
 */
public final class SeepParticles {
    private SeepParticles() {}

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(MobParticles.SEEP_MOTE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.MOTE));
        e.registerSpriteSet(MobParticles.SEEP_DRAIN.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.DRAIN));
        e.registerSpriteSet(MobParticles.SEEP_SHARD.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.SHARD));
        e.registerSpriteSet(MobParticles.SEEP_CRACK.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.CRACK));
        e.registerSpriteSet(MobParticles.SEEP_WAVE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.WAVE));
    }

    static final class Bit extends SingleQuadParticle {
        static final int MOTE = 0, DRAIN = 1, SHARD = 2, CRACK = 3, WAVE = 4;
        /** Face up and face down, for the flat ones (made once per particle, reused every frame). */
        private static final Quaternionf UP = new Quaternionf().rotationX(-Mth.HALF_PI), DOWN = new Quaternionf().rotationX(Mth.HALF_PI);
        private final SpriteSet sprites;
        private final int kind;
        private final float base, spin;
        private final boolean hold;
        private final Quaternionf up = new Quaternionf(), down = new Quaternionf();

        Bit(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, int kind) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.kind = kind;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            float shade = 0.9F + random.nextFloat() * 0.2F;
            boolean holds = false;
            switch (kind) {
                case DRAIN -> {
                    // flies straight in and is gone as it arrives
                    gravity = 0F;
                    friction = 1.0F;
                    hasPhysics = false;
                    lifetime = 16;
                    quadSize = 0.06F + random.nextFloat() * 0.05F;
                    setColor(0.78F * shade, 0.62F * shade, 1.0F);
                }
                case SHARD -> {
                    gravity = 0.7F;
                    friction = 0.94F;
                    hasPhysics = true;
                    lifetime = 16 + random.nextInt(14);
                    quadSize = 0.05F + random.nextFloat() * 0.05F;
                    setColor(0.86F * shade, 0.76F * shade, 1.0F);
                    roll = random.nextFloat() * Mth.TWO_PI;
                }
                case CRACK, WAVE -> {
                    // flat on the floor: x speed the radius, y speed its life, z speed 1 for a ring that holds (a tell)
                    float radius = (float) Math.max(0.3, xa);
                    lifetime = Math.max(4, (int) ya);
                    holds = kind == WAVE && za > 0.5;
                    xd = yd = zd = 0;
                    gravity = 0F;
                    friction = 1.0F;
                    hasPhysics = false;
                    quadSize = radius;
                    roll = random.nextFloat() * Mth.TWO_PI;
                    setColor(0.74F, 0.56F, 1.0F);
                }
                default -> {
                    gravity = -0.003F;
                    friction = 0.96F;
                    hasPhysics = false;
                    lifetime = 24 + random.nextInt(24);
                    quadSize = 0.045F + random.nextFloat() * 0.05F;
                    setColor(0.72F * shade, 0.55F * shade, 1.0F);
                }
            }
            this.hold = holds;
            this.base = quadSize;
            this.spin = (random.nextFloat() - 0.5F) * 0.3F;
            if (kind == WAVE) setSprite(sprites.get(holds ? 1 : 0, 1)); // a held tell is the runed ring, a rushing one the bright one
            else setSpriteFromAge(sprites);
            if (kind == CRACK || kind == WAVE) {
                up.set(UP).rotateZ(roll);
                down.set(DOWN).rotateZ(roll);
            }
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float a) {
            return LightCoordsUtil.FULL_BRIGHT;
        }

        @Override
        public void extract(QuadParticleRenderState state, Camera camera, float partialTicks) {
            if (kind != CRACK && kind != WAVE) {
                super.extract(state, camera, partialTicks);
                return;
            }
            extractRotatedQuad(state, camera, up, partialTicks);
            extractRotatedQuad(state, camera, down, partialTicks);
        }

        @Override
        public void tick() {
            super.tick();
            if (kind != WAVE) setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            switch (kind) {
                case DRAIN -> {
                    alpha = Math.min(1F, t * 5F) * (1F - t * t);
                    quadSize = base * (1.15F - 0.7F * t);
                }
                case SHARD -> {
                    oRoll = roll;
                    roll += spin;
                    alpha = 1F - t * t;
                }
                case CRACK -> {
                    // opens fast, glows, flickers brighter as the roots come
                    alpha = Math.min(1F, t * 6F) * (0.7F + 0.3F * (float) Math.sin(age * (0.6F + t)));
                    if (t > 0.9F) alpha *= (1F - t) * 10F;
                    quadSize = base * (0.6F + 0.4F * Math.min(1F, t * 4F));
                }
                case WAVE -> {
                    if (hold) {
                        // a tell: it fades in and pulses while the blow winds up
                        alpha = Math.min(1F, t * 5F) * (0.55F + 0.3F * (float) Math.sin(age * 0.5F));
                        if (t > 0.85F) alpha *= (1F - t) / 0.15F;
                        quadSize = base;
                    } else {
                        // rushes out from the middle and thins away
                        float e = 1F - (1F - t) * (1F - t);
                        quadSize = base * (0.1F + 0.9F * e);
                        alpha = (1F - t) * 0.95F;
                    }
                }
                default -> {
                    alpha = Math.min(1F, Math.min(t * 4F, (1F - t) * 2F)) * 0.9F;
                    quadSize = base * (0.85F + 0.25F * (float) Math.sin(t * Math.PI * 3));
                }
            }
        }
    }
}
