package dev.awaken.gates.realms.kit;

import dev.awaken.gates.realms.blockout.Blockout;

/**
 * A land built from a sheet: it writes {@link #draw}, and every chunk it draws is settled after, so a small stray (a
 * lantern on nothing, a plant in the air, a lone block) can't reach the world (SPEC §50.6). The lands made before this
 * keep their own paint and are not settled: their blocks must not move.
 *
 * <p>Given a {@link Blockout}, the land's ground, size, Mouth and sites are the blockout's, so a gate's own land writes
 * only {@code draw}, {@code biome} and what is special to it, and reads its big shapes by name from {@link #blockout}.
 */
public abstract class SheetLand<M extends Mats> extends KitLand<M> {
    /** What settling took out, for the bench to print: a count here means a cause to fix in {@link #draw}. */
    public final Settle.Tally tally = new Settle.Tally(this);
    /** The agreed big shapes, with their rough surfaces rolled from this land's seed; null for a land without a blockout. */
    public final Blockout blockout;

    /** A land that works out its own ground: it must override {@code ground}, {@code halfSize} and {@code mouthYaw}. */
    protected SheetLand(Ctx ctx, long salt) {
        super(ctx, salt);
        blockout = null;
    }

    protected SheetLand(Ctx ctx, long salt, Blockout blockout) {
        super(ctx, salt);
        this.blockout = blockout.seeded(ctx.seed());
        for (Blockout.SiteSpec s : blockout.sites()) site(s.kind(), s.x(), s.y(), s.z(), s.radius(), s.what());
    }

    private Blockout sheet() {
        if (blockout == null) throw new IllegalStateException(getClass().getSimpleName() + " has no blockout, so it must say its own ground, size and Mouth");
        return blockout;
    }

    @Override
    public int ground(int x, int z) {
        return sheet().ground(x, z);
    }

    @Override
    public int halfSize() {
        return sheet().half;
    }

    @Override
    public float mouthYaw() {
        return sheet().mouthYaw;
    }

    /** Draws the chunk this volume covers, margin included, every pass in order. Settling comes after and is not yours to call. */
    protected abstract void draw(V v);

    @Override
    public final void paint(V v) {
        draw(v);
        Settle.run(v, tally);
    }
}
