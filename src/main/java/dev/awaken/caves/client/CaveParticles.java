package dev.awaken.caves.client;

import dev.awaken.caves.skin.Drips;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The skin's drips (SPEC §42.4): a drop swells at a stalactite's tip, lets go, falls, and throws a small crown of beads
 * where it lands; and the regions' motes (§42.7). Sprites from tools/art/specs/deep_skin.py (drip: four frames gathering
 * to falling; splash: four of the crown rising and falling back; spores, pollen, warm dust).
 */
final class CaveParticles {
    private CaveParticles() {}

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Drips.DRIP.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Drop(l, x, y, z, s));
        e.registerSpriteSet(Drips.SPLASH.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Splash(l, x, y, z, s));
        e.registerSpriteSet(Drips.SPORE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s, 0.035F, 0.0035F, 3));
        e.registerSpriteSet(Drips.POLLEN.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s, 0.03F, 0.0015F, 2));
        e.registerSpriteSet(Drips.WARM_DUST.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s, 0.03F, 0.001F, 2));
    }

    /**
     * A mote of the regions' air (SPEC §42.7): spores sinking slowly, pollen and warm dust hanging, each drifting on the
     * cave's slow draught with a little sway; it fades in and out, and never goes quite black (the floor of its light), so
     * a torch shows the air full of it.
     */
    static final class Mote extends SingleQuadParticle {
        private final float sway;
        private final int dark;

        Mote(ClientLevel level, double x, double y, double z, SpriteSet sprites, float size, float sink, int dark) {
            super(level, x, y, z, 0, 0, 0, sprites.get(level.getRandom()));
            this.dark = dark;
            xd = (random.nextFloat() - 0.5F) * 0.006F;
            yd = -sink * (0.6F + random.nextFloat() * 0.8F);
            zd = (random.nextFloat() - 0.5F) * 0.006F;
            gravity = 0;
            friction = 1F;
            hasPhysics = false;
            lifetime = 100 + random.nextInt(80);
            quadSize = size * (0.7F + random.nextFloat() * 0.6F);
            sway = random.nextFloat() * 6.28F;
            alpha = 0F;
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float partial) {
            int c = super.getLightCoords(partial);
            return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), dark), LightCoordsUtil.sky(c));
        }

        @Override
        public void tick() {
            super.tick();
            float t = age + sway * 20F;
            xd += Math.sin(t * 0.05F) * 0.00015F;
            zd += Math.cos(t * 0.043F) * 0.00015F;
            alpha = Math.min(1F, Math.min(age / 25F, (lifetime - age) / 35F)) * 0.9F;
        }
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
