package dev.awaken.citizens.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * The city's own particles: chimney smoke (soft, slow, leaning with the wind, swelling as it rises), forge sparks
 * (hot streaks that arc and die), the flare of a lamp being lit, and the embers of a house fire.
 */
public final class CitizenParticles {
    private CitizenParticles() {}

    /** Wind over Carillon: a gentle, slowly turning push (the same for every chimney, so the smoke leans together). */
    static double windX(ClientLevel level) {
        return 0.006 + 0.004 * Math.sin(level.getGameTime() / 2400.0);
    }

    static double windZ(ClientLevel level) {
        return 0.003 * Math.cos(level.getGameTime() / 3100.0);
    }

    public static final class Smoke extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final float startSize;
        private final float shade;

        Smoke(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.xd = xa + (random.nextDouble() - 0.5) * 0.006;
            this.yd = 0.035 + ya + random.nextDouble() * 0.015;
            this.zd = za + (random.nextDouble() - 0.5) * 0.006;
            this.friction = 0.985f;
            this.gravity = -0.0006f;
            this.hasPhysics = false;
            this.lifetime = 90 + random.nextInt(70);
            this.startSize = quadSize * (2.4f + random.nextFloat() * 1.2f);
            this.quadSize = startSize;
            // ya > 0.02 asks for darker smoke (forges, fires); otherwise a warm grey
            this.shade = ya > 0.02 ? 0.42f + random.nextFloat() * 0.08f : 0.78f + random.nextFloat() * 0.1f;
            setColor(shade, shade * 0.97f, shade * 0.93f);
            setSpriteFromAge(sprites);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        public void tick() {
            xd += windX(level) * 0.04;
            zd += windZ(level) * 0.04;
            super.tick();
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            quadSize = startSize * (1f + t * 2.2f);
            alpha = 0.55f * Math.min(1f, t * 6f) * (1f - t) * (1f - t * 0.3f);
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Smoke(level, x, y, z, xa, ya, za, sprites);
            }
        }
    }

    public static final class Spark extends SingleQuadParticle {
        private final SpriteSet sprites;

        Spark(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            double a = random.nextDouble() * Math.PI * 2, s = 0.06 + random.nextDouble() * 0.12;
            this.xd = Math.cos(a) * s + xa;
            this.yd = 0.08 + random.nextDouble() * 0.16 + ya;
            this.zd = Math.sin(a) * s + za;
            this.friction = 0.96f;
            this.gravity = 0.9f;
            this.hasPhysics = true;
            this.lifetime = 7 + random.nextInt(9);
            this.quadSize *= 0.35f + random.nextFloat() * 0.25f;
            setColor(1f, 0.72f + random.nextFloat() * 0.2f, 0.32f);
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
            setColor(1f, 0.85f - t * 0.5f, 0.4f - t * 0.3f);
            alpha = 1f - t * t;
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Spark(level, x, y, z, xa, ya, za, sprites);
            }
        }
    }

    public static final class Flare extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final float base;

        Flare(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.xd = (random.nextDouble() - 0.5) * 0.01;
            this.yd = 0.01 + random.nextDouble() * 0.01;
            this.zd = (random.nextDouble() - 0.5) * 0.01;
            this.friction = 0.9f;
            this.gravity = 0;
            this.hasPhysics = false;
            this.lifetime = 10 + random.nextInt(6);
            this.base = quadSize * (1.4f + random.nextFloat() * 0.6f);
            setColor(1f, 0.86f, 0.55f);
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
            quadSize = base * (float) Math.sin(Math.min(1f, t * 1.4f) * Math.PI);
            alpha = 1f - t;
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Flare(level, x, y, z, sprites);
            }
        }
    }

    public static final class Ember extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final float phase;

        Ember(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.xd = xa + (random.nextDouble() - 0.5) * 0.04;
            this.yd = 0.04 + random.nextDouble() * 0.05 + ya;
            this.zd = za + (random.nextDouble() - 0.5) * 0.04;
            this.friction = 0.95f;
            this.gravity = -0.003f;
            this.hasPhysics = false;
            this.lifetime = 24 + random.nextInt(24);
            this.quadSize *= 0.4f + random.nextFloat() * 0.3f;
            this.phase = random.nextFloat() * 6.28f;
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
            xd += windX(level) * 0.2 + Math.sin(age * 0.4 + phase) * 0.004;
            zd += windZ(level) * 0.2;
            super.tick();
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            float flick = 0.75f + 0.25f * (float) Math.sin(age * 1.7 + phase);
            setColor(1f, (0.7f - t * 0.4f) * flick + 0.1f, 0.25f * (1f - t));
            alpha = (1f - t) * flick;
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Ember(level, x, y, z, xa, ya, za, sprites);
            }
        }
    }
}
