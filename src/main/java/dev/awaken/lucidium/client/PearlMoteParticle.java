package dev.awaken.lucidium.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/** A pearl mote: a soft speck of the dream's light that drifts and fades (a course laying itself, the path's glint, a bed a dream waits in). */
public final class PearlMoteParticle extends SingleQuadParticle {
    PearlMoteParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.gravity = -0.01F;
        this.friction = 0.94F;
        this.hasPhysics = false;
        this.lifetime = 24 + this.random.nextInt(24);
        this.quadSize = 0.06F + this.random.nextFloat() * 0.06F;
        this.xd = xa + (this.random.nextFloat() - 0.5F) * 0.01F;
        this.yd = ya;
        this.zd = za + (this.random.nextFloat() - 0.5F) * 0.01F;
        float warm = this.random.nextFloat() * 0.12F;
        this.setColor(0.93F + warm * 0.5F, 0.9F + warm * 0.3F, 1.0F - warm);
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
        this.alpha = life < 0.25F ? life * 4 : 1F - (life - 0.25F) / 0.75F;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            return new PearlMoteParticle(level, x, y, z, xa, ya, za, sprites);
        }
    }
}
