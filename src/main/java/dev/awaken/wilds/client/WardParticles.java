package dev.awaken.wilds.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * The ward's two particles (sprites {@code ward_grain_0..3} and {@code ward_spark_0..3}, drawn by tools/art/specs/wilds.py).
 * A grain is a chip of the block coming apart: hard-edged, tinted like its block, it falls, bounces once and thins. A
 * spark is a thread of the Signal's light lifting off: lit from within, it rises, slows and goes out.
 */
final class WardParticles {
    private WardParticles() {}

    /** Tint for the next particle made (client thread only), set by {@link WardFxClient} just before it spawns one. */
    static int nextTint = 0xFFFFFF;

    static final class Grain extends SingleQuadParticle {
        private final SpriteSet sprites;

        Grain(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.friction = 0.9F;
            this.gravity = 0.9F;
            this.hasPhysics = true;
            this.lifetime = 18 + random.nextInt(14);
            this.quadSize = 0.045F + random.nextFloat() * 0.05F;
            int c = nextTint;
            float shade = 0.82F + random.nextFloat() * 0.3F;
            setColor(Math.min(1F, ((c >> 16) & 255) / 255F * shade), Math.min(1F, ((c >> 8) & 255) / 255F * shade), Math.min(1F, (c & 255) / 255F * shade));
            setSpriteFromAge(sprites);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            float life = (float) age / lifetime;
            alpha = life < 0.6F ? 1F : 1F - (life - 0.6F) / 0.4F;
        }
    }

    static final class Spark extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final float size0;

        Spark(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.friction = 0.86F;
            this.gravity = -0.02F;
            this.hasPhysics = false;
            this.lifetime = 16 + random.nextInt(12);
            this.size0 = 0.06F + random.nextFloat() * 0.05F;
            this.quadSize = size0;
            int c = nextTint;
            setColor(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F);
            setSpriteFromAge(sprites);
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
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            float life = (float) age / lifetime;
            alpha = 0.9F * (1F - life * life);
            quadSize = size0 * (1F - 0.5F * life);
        }
    }

    record GrainProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
            return new Grain(level, x, y, z, xa, ya, za, sprites);
        }
    }

    record SparkProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
            return new Spark(level, x, y, z, xa, ya, za, sprites);
        }
    }
}
