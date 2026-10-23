package dev.awaken.lucidium.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/** A raindrop falling up: the "rain falling up" dream sign. */
public final class DreamDropParticle extends SingleQuadParticle {
    DreamDropParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.gravity = -0.35F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.lifetime = 30 + this.random.nextInt(20);
        this.quadSize = 0.08F + this.random.nextFloat() * 0.05F;
        this.xd = xa;
        this.yd = ya;
        this.zd = za;
        this.setColor(0.85F, 0.8F, 1.0F);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float a) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) this.age / this.lifetime;
        this.alpha = life < 0.2F ? life * 5 : 1F - (life - 0.2F) / 0.8F;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            return new DreamDropParticle(level, x, y, z, xa, ya, za, sprites);
        }
    }
}
