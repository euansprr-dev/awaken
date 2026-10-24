package dev.awaken.hearth.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/** The module's particles: dark Echo wisps, warm Hearth embers and the Hearth guide's marks. */
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

    /**
     * The Hearth guide's marks, built on the ember sprite: {@code ring} is a faint amber mote that hangs at the edge of
     * the radius and drifts up a little; otherwise a signal-blue spark that pulses on the spot where a room leaks.
     */
    public static final class Mark extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final boolean ring;
        private final float baseSize;

        Mark(ClientLevel level, double x, double y, double z, double ya, SpriteSet sprites, boolean ring) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.ring = ring;
            this.xd = 0;
            this.yd = ring ? ya + random.nextDouble() * 0.008 : 0;
            this.zd = 0;
            this.friction = 0.98f;
            this.gravity = 0;
            this.hasPhysics = false;
            this.lifetime = ring ? 28 + random.nextInt(24) : 18;
            this.baseSize = quadSize * (ring ? 0.7f + random.nextFloat() * 0.3f : 1.6f);
            this.quadSize = baseSize;
            if (ring) setColor(1.0f, 0.78f, 0.42f);
            else setColor(0.55f, 0.82f, 1.0f);
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
            float t = (float) age / lifetime;
            // fade in, hold, fade out; the leak mark also swells once
            alpha = (ring ? 0.55f : 0.9f) * Math.min(1f, Math.min(t * 4f, (1f - t) * 2.5f));
            if (!ring) quadSize = baseSize * (0.7f + 0.5f * (float) Math.sin(t * Math.PI));
        }

        public record RingProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Mark(level, x, y, z, ya, sprites, true);
            }
        }

        public record LeakProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Mark(level, x, y, z, 0, sprites, false);
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
