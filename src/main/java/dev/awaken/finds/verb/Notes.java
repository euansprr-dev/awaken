package dev.awaken.finds.verb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Ring (SPEC §58.5): things that sound when struck, and an order to play them in. The order is the site's own (from
 * its seed); the wind plays it every so often while somebody stands near, and playing it back completes. A wrong note
 * starts you again. Numbers: 0 how many right so far. The clock times the wind's playing.
 */
public final class Notes implements Script {
    private static final int[] STEPS = {0, 3, 5, 7, 10, 12, 15, 17, 19};
    private final String part;
    private final int length;
    private boolean lowToHigh, byFoot;
    private final java.util.Map<java.util.UUID, Integer> on = new java.util.HashMap<>();

    private Notes(String part, int length) {
        this.part = part;
        this.length = length;
    }

    /** Play back {@code length} notes on the parts named {@code part}. */
    public static Notes of(String part, int length) {
        return new Notes(part, length);
    }

    /** The order is simply first part to last (icicles short to long), and nothing plays it for you. */
    public Notes lowToHigh() {
        lowToHigh = true;
        return this;
    }

    /** The notes are played by stepping onto the parts, not by striking them. */
    public Notes byFoot() {
        byFoot = true;
        return this;
    }

    /** The order this site wants: indices into the part list. */
    public static int[] order(long seed, int parts, int length) {
        RandomSource r = RandomSource.create(seed ^ 0x4E07E5L);
        int[] o = new int[length];
        for (int i = 0; i < length; i++) {
            o[i] = r.nextInt(parts);
            if (i > 0 && o[i] == o[i - 1]) o[i] = (o[i] + 1) % parts;
        }
        return o;
    }

    private int[] order(Ctx c) {
        if (!lowToHigh) return order(c.seed(), c.count(part), length);
        int[] o = new int[c.count(part)];
        for (int i = 0; i < o.length; i++) o[i] = i;
        return o;
    }

    private void sound(Ctx c, int i, boolean soft) {
        float pitch = (float) (0.5 * Math.pow(2, STEPS[i % STEPS.length] / 12.0));
        var at = c.pos(part, i);
        c.level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.BLOCKS, soft ? 0.5F : 1.0F, pitch);
        c.puff(at.above(), ParticleTypes.NOTE, 1, 0.1, 0);
    }

    private boolean strike(Ctx c, ServerPlayer p, String name, int i) {
        if (!name.equals(part)) return false;
        sound(c, i, false);
        int[] o = order(c);
        int at = c.v(0);
        if (o[at] == i) {
            if (at + 1 == o.length) c.complete(p);
            else c.v(0, at + 1);
        } else {
            c.v(0, o[0] == i ? 1 : 0);
            if (at > 0) c.level.playSound(null, c.pos(part, i), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 0.6F, 0.6F);
        }
        return true;
    }

    @Override public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        return strike(c, p, name, i);
    }

    @Override public boolean hit(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        return strike(c, p, name, i);
    }

    @Override
    public void tick(Ctx c) {
        if (byFoot) {
            for (ServerPlayer p : c.players(c.kind().reach() + 2)) {
                int at = -1;
                for (int i = 0; i < c.count(part); i++) if (p.blockPosition().equals(c.pos(part, i).above()) || p.blockPosition().equals(c.pos(part, i))) at = i;
                Integer was = on.put(p.getUUID(), at);
                if (at >= 0 && (was == null || was != at)) strike(c, p, part, at);
            }
        }
        if (lowToHigh || c.v(0) != 0) return;
        // the wind: every twelve seconds it plays the order through, one note every half second
        long t = c.now() - c.clock();
        int[] o = order(c);
        if (t >= 240 + o.length * 10L) {
            c.clock(c.now());
            return;
        }
        if (t >= 240 && (t - 240) % 10 < 5) sound(c, o[(int) ((t - 240) / 10)], true);
    }
}
