package dev.awaken.hearth.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * The module's particles: dark Echo wisps, warm Hearth embers, the Hearth guide's marks, and the carried flame's own:
 * the warmth that runs out over the land when a coal takes, the Hearthfire's tongues of flame and fireflies at night.
 */
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

    /**
     * Warmth running out over the land when a coal takes: a broad, soft, warm glow that sits on the ground, lifts a
     * hand's width and fades. Spawned in rings travelling outward, on the ground's own height, so the land seems to
     * brighten as the wave passes ({@code ya}: how bright, 0-1).
     */
    public static final class Warmth extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final float peak, baseSize;

        Warmth(ClientLevel level, double x, double y, double z, double ya, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.xd = 0;
            this.yd = 0.006 + random.nextDouble() * 0.006;
            this.zd = 0;
            this.friction = 0.97f;
            this.gravity = 0;
            this.hasPhysics = false;
            this.lifetime = 26 + random.nextInt(14);
            this.peak = (float) Math.max(0.15, Math.min(1.0, ya));
            this.baseSize = quadSize * (3.4f + random.nextFloat() * 1.8f);
            this.quadSize = baseSize;
            // bell-gold into ember: the wave's front is brighter and paler
            float w = random.nextFloat();
            setColor(1.0f, 0.70f + 0.16f * w, 0.32f + 0.18f * w);
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
            alpha = peak * 0.7f * Math.min(1f, Math.min(t * 6f, (1f - t) * 1.6f));
            quadSize = baseSize * (0.85f + 0.3f * t);
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Warmth(level, x, y, z, ya, sprites);
            }
        }
    }

    /** A tongue of the Hearthfire's flame: it licks up off the coal, leans, thins and is gone in under a second. */
    public static final class Flame extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final float baseSize;

        Flame(ClientLevel level, double x, double y, double z, double ya, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.xd = (random.nextDouble() - 0.5) * 0.006;
            this.yd = ya;
            this.zd = (random.nextDouble() - 0.5) * 0.006;
            this.friction = 0.9f;
            this.gravity = -0.002f;
            this.hasPhysics = false;
            this.lifetime = 10 + random.nextInt(8);
            this.baseSize = quadSize * (0.9f + random.nextFloat() * 0.5f);
            this.quadSize = baseSize;
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
            alpha = Math.min(1f, (1f - t) * 1.8f);
            quadSize = baseSize * (1f - 0.55f * t);
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Flame(level, x, y, z, ya, sprites);
            }
        }
    }

    /** A firefly over a Hearth's land at night: it drifts on its own slow course and blinks, green-gold. */
    public static final class Firefly extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final float phase, rate;

        Firefly(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.friction = 0.92f;
            this.gravity = 0;
            this.hasPhysics = false;
            this.lifetime = 120 + random.nextInt(120);
            this.quadSize *= 0.55f + random.nextFloat() * 0.25f;
            this.phase = random.nextFloat() * 6.283f;
            this.rate = 0.08f + random.nextFloat() * 0.06f;
            setColor(0.86f, 1.0f, 0.46f);
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
            // a slow wander: small nudges that the friction smooths into curves
            xd += (random.nextDouble() - 0.5) * 0.006;
            yd += (random.nextDouble() - 0.5) * 0.004;
            zd += (random.nextDouble() - 0.5) * 0.006;
            float t = (float) age / lifetime;
            float blink = Math.max(0f, (float) Math.sin(age * rate + phase));
            alpha = Math.min(1f, Math.min(t * 8f, (1f - t) * 4f)) * (0.15f + 0.85f * blink * blink);
        }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
                return new Firefly(level, x, y, z, sprites);
            }
        }
    }
}
