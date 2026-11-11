package dev.awaken.enchant.client;

import dev.awaken.enchant.EnchantContent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * A letter of our script drifting off a bookshelf into the table's book (SPEC §45.13): it lifts off the shelf, sways,
 * arcs over and speeds into the book, brightening toward white as it arrives and gone as it touches the page. Spawned at
 * the book with its speed the way back to the shelf (vanilla's convention for its own letters).
 */
final class LetterParticle extends SingleQuadParticle {
    private final double tx, ty, tz, ox, oy, oz, sideX, sideZ;
    private final float r0, g0, b0, phase, spin;

    /** The sprite set's order (the art spec writes it): the script's twelve letters, then the thirty-six signs by number. */
    private static final int LETTERS = 12, SIGNS = 36;
    private static final int[] KNOWN = new int[SIGNS];

    /** A letter of the script, or, one time in three, the sign of a word you know (SPEC §45.17). */
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite pick(ClientLevel level, SpriteSet sprites) {
        var random = level.getRandom();
        var me = net.minecraft.client.Minecraft.getInstance().player;
        if (me != null && random.nextInt(3) == 0) {
            dev.awaken.enchant.Known k = dev.awaken.enchant.Known.of(me);
            int n = 0;
            for (dev.awaken.enchant.Inscriptions.Entry e : dev.awaken.enchant.Inscriptions.ALL) if (e.no() <= SIGNS && k.knows(e.id())) KNOWN[n++] = e.no();
            if (n > 0) return sprites.get(LETTERS + KNOWN[random.nextInt(n)] - 1, LETTERS + SIGNS - 1);
        }
        return sprites.get(random.nextInt(LETTERS), LETTERS + SIGNS - 1);
    }

    private LetterParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, int argb, SpriteSet sprites) {
        super(level, x + xa, y + ya, z + za, pick(level, sprites));
        tx = x;
        ty = y;
        tz = z;
        ox = xa;
        oy = ya;
        oz = za;
        double l = Math.sqrt(xa * xa + za * za);
        sideX = l < 1e-4 ? 1 : -za / l;
        sideZ = l < 1e-4 ? 0 : xa / l;
        xd = yd = zd = 0;
        hasPhysics = false;
        gravity = 0F;
        lifetime = 36 + random.nextInt(20);
        quadSize = 0.05F + random.nextFloat() * 0.03F;
        r0 = (argb >> 16 & 255) / 255F;
        g0 = (argb >> 8 & 255) / 255F;
        b0 = (argb & 255) / 255F;
        setColor(r0, g0, b0);
        alpha = 0F;
        roll = oRoll = (random.nextFloat() - 0.5F) * 0.8F;
        spin = (random.nextFloat() - 0.5F) * 0.07F;
        phase = random.nextFloat() * Mth.TWO_PI;
    }

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(EnchantContent.LETTER.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new LetterParticle(l, x, y, z, xa, ya, za,
                ((int) (o.getRed() * 255) << 16) | ((int) (o.getGreen() * 255) << 8) | (int) (o.getBlue() * 255), s));
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
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = age / (float) lifetime;
        // slow off the shelf, quick into the book
        float e = (float) Math.pow(t, 2.2);
        double lift = Mth.sin(t * Mth.PI) * 0.35, sway = Mth.sin(t * 7F + phase) * 0.07 * (1 - t);
        x = tx + ox * (1 - e) + sideX * sway;
        y = ty + oy * (1 - e) + lift;
        z = tz + oz * (1 - e) + sideZ * sway;
        roll += spin;
        alpha = Math.min(1F, Math.min(t / 0.15F, (1F - t) / 0.1F)) * 0.95F;
        float w = Mth.clamp((t - 0.65F) / 0.35F, 0F, 1F);
        setColor(Mth.lerp(w, r0, 1F), Mth.lerp(w, g0, 1F), Mth.lerp(w, b0, 1F));
    }
}
