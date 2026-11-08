package dev.awaken.mobs.client;

import dev.awaken.mobs.MobParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * What a creature's shot leaves behind and breaks into (SPEC §47.15; sprites from tools/art/specs/shot_particles.py): seven
 * shapes, tinted by the kind of shot, each moving as what it is. Full-bright, so a shot reads in a dark room.
 */
public final class ShotParticles {
    private ShotParticles() {}

    static void register(RegisterParticleProvidersEvent e) {
        for (MobParticles.Shot shape : MobParticles.Shot.values()) {
            e.registerSpriteSet(shape.type.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, shape, o));
        }
    }

    static final class Bit extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final MobParticles.Shot shape;
        private final float base, spin;

        Bit(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, MobParticles.Shot shape, ColorParticleOption tint) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.shape = shape;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            float shade = 0.9F + random.nextFloat() * 0.2F;
            setColor(Math.min(1F, tint.getRed() * shade), Math.min(1F, tint.getGreen() * shade), Math.min(1F, tint.getBlue() * shade));
            hasPhysics = false;
            switch (shape) {
                case SPARK -> set(0.2F, 0.88F, 6, 7, 0.09F, 0.06F);
                case EMBER -> set(-0.01F, 0.94F, 10, 9, 0.07F, 0.05F);
                case FLAKE -> set(0.05F, 0.96F, 14, 11, 0.09F, 0.04F);
                case WISP -> set(-0.008F, 0.96F, 14, 11, 0.14F, 0.07F);
                case PUFF -> set(-0.004F, 0.93F, 12, 11, 0.16F, 0.1F);
                case DROP -> {
                    set(0.6F, 0.98F, 14, 11, 0.08F, 0.04F);
                    hasPhysics = true;
                }
                case SLIVER -> set(0.25F, 0.95F, 8, 7, 0.1F, 0.07F);
            }
            this.base = quadSize;
            boolean turns = shape == MobParticles.Shot.FLAKE || shape == MobParticles.Shot.SLIVER || shape == MobParticles.Shot.WISP;
            this.spin = turns ? (random.nextFloat() - 0.5F) * 0.3F : 0F;
            if (turns) roll = random.nextFloat() * Mth.TWO_PI;
            setSpriteFromAge(sprites);
        }

        private void set(float gravity, float friction, int life, int more, float size, float bigger) {
            this.gravity = gravity;
            this.friction = friction;
            this.lifetime = life + random.nextInt(more);
            this.quadSize = size + random.nextFloat() * bigger;
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
            oRoll = roll;
            roll += spin;
            float t = (float) age / lifetime;
            switch (shape) {
                case WISP, PUFF -> {
                    quadSize = base * (0.8F + 0.7F * t);
                    alpha = Math.min(1F, t * 6F) * (1F - t) * 0.85F;
                }
                case DROP, SLIVER -> alpha = 1F - t * t;
                default -> alpha = 1F - t * t * t;
            }
        }
    }
}
