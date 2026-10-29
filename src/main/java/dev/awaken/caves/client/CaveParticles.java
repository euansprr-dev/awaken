package dev.awaken.caves.client;

import dev.awaken.caves.skin.Drips;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The skin's drips (SPEC §42.4): a drop swells at a stalactite's tip, lets go, falls, and throws a small crown of beads
 * where it lands. Sprites from tools/art/specs/deep_skin.py (drip: four frames gathering to falling; splash: four of the
 * crown rising and falling back).
 */
final class CaveParticles {
    private CaveParticles() {}

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Drips.DRIP.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Drop(l, x, y, z, s));
        e.registerSpriteSet(Drips.SPLASH.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Splash(l, x, y, z, s));
    }

    /** The drop: hangs while it swells, then falls; on landing it splashes. */
    static final class Drop extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final int hang;

        Drop(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.hang = 14 + random.nextInt(16);
            xd = yd = zd = 0;
            gravity = 0;
            friction = 1F;
            hasPhysics = true;
            lifetime = hang + 60;
            quadSize = 0.045F;
            setSprite(sprites.get(0, 3));
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float partial) {
            // water catches what light there is: a drop never goes quite black
            int c = super.getLightCoords(partial);
            return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), 3), LightCoordsUtil.sky(c));
        }

        @Override
        public void tick() {
            if (age < hang) {
                age++;
                setSprite(sprites.get(Math.min(2, age * 3 / hang), 3));   // swelling
                return;
            }
            if (age == hang) {
                gravity = 0.9F;
                friction = 0.98F;
                setSprite(sprites.get(3, 3));                          // long as it falls
            }
            super.tick();
            if (onGround || removed) {
                level.addParticle(Drips.SPLASH.get(), x, y + 0.02, z, 0, 0, 0);
                remove();
            }
        }
    }

    static final class Splash extends SingleQuadParticle {
        private final SpriteSet sprites;

        Splash(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            xd = yd = zd = 0;
            gravity = 0;
            hasPhysics = false;
            lifetime = 7;
            quadSize = 0.09F;
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
            alpha = 1F - (float) age / lifetime * 0.6F;
        }
    }
}
