package dev.awaken.body.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Grit the body throws up: a soft clod that billows out, drifts down and thins, coloured like the ground it came off
 * ({@link BodyFx} sets the tint just before spawning it). Lit by the world, so dust in a dark cave stays dark.
 */
final class BodyDustParticle extends SingleQuadParticle {
    /** Tint for the next particle made (client thread only), and its size. */
    static int nextTint = 0x8A8378;
    static float nextSize = 1F;

    private final SpriteSet sprites;
    private final float grow;

    BodyDustParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.sprites = sprites;
        this.xd = xa;
        this.yd = ya;
        this.zd = za;
        this.friction = 0.84F;
        this.gravity = 0.05F;
        this.hasPhysics = true;
        this.lifetime = 14 + this.random.nextInt(12);
        this.quadSize = (0.09F + this.random.nextFloat() * 0.07F) * nextSize;
        this.grow = 1.0F + 0.9F * this.random.nextFloat();
        int c = nextTint;
        float shade = 0.9F + this.random.nextFloat() * 0.2F;
        this.setColor(Math.min(1F, ((c >> 16) & 255) / 255F * shade), Math.min(1F, ((c >> 8) & 255) / 255F * shade), Math.min(1F, (c & 255) / 255F * shade));
        this.alpha = 0.85F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float life = (float) this.age / this.lifetime;
        this.alpha = 0.85F * (1F - life * life);
        if (this.age < 8) this.quadSize *= 1F + 0.06F * this.grow;
    }

    record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            return new BodyDustParticle(level, x, y, z, xa, ya, za, sprites);
        }
    }
}
