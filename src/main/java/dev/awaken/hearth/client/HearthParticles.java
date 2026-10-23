package dev.awaken.hearth.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/** The module's two particles: dark Echo wisps and warm Hearth embers. */
public final class HearthParticles {
    private HearthParticles() {}

    /** A dark violet wisp that drifts up and thins out. */
    public static final class Wisp extends SingleQuadParticle {
        private final SpriteSet sprites;

        Wisp(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.xd = xa * 0.5 + (random.nextDouble() - 0.5) * 0.02;
            this.yd = Math.max(0.01, ya) + random.nextDouble() * 0.02;
            this.zd = za * 0.5 + (random.nextDouble() - 0.5) * 0.02;
            this.friction = 0.94f;
            this.gravity = -0.01f;
            this.hasPhysics = false;
            this.lifetime = 24 + random.nextInt(16);
            this.quadSize *= 1.2f + random.nextFloat() * 0.6f;
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
            alpha = 0.85f * (1f - (float) age / lifetime);
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Wisp(level, x, y, z, xa, ya, za, sprites);
            }
        }
    }

    /** A small amber ember that rises from the Anchor and glows. */
    public static final class Ember extends SingleQuadParticle {
        private final SpriteSet sprites;

        Ember(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.xd = xa + (random.nextDouble() - 0.5) * 0.01;
            this.yd = ya + 0.01;
            this.zd = za + (random.nextDouble() - 0.5) * 0.01;
            this.friction = 0.96f;
            this.gravity = -0.004f;
            this.hasPhysics = false;
            this.lifetime = 30 + random.nextInt(30);
            this.quadSize *= 0.6f + random.nextFloat() * 0.4f;
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
            alpha = 1f - (float) age / lifetime;
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Ember(level, x, y, z, xa, ya, za, sprites);
            }
        }
    }
}
