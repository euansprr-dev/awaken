package dev.awaken.gates.realms.kit;

/**
 * A land built from a sheet: it writes {@link #draw}, and every chunk it draws is settled after, so a small stray (a
 * lantern on nothing, a plant in the air, a lone block) can't reach the world (SPEC §50.6). The lands made before this
 * keep their own paint and are not settled: their blocks must not move.
 */
public abstract class SheetLand<M extends Mats> extends KitLand<M> {
    /** What settling took out, for the bench to print: a count here means a cause to fix in {@link #draw}. */
    public final Settle.Tally tally = new Settle.Tally(this);

    protected SheetLand(Ctx ctx, long salt) {
        super(ctx, salt);
    }

    /** Draws the chunk this volume covers, margin included, every pass in order. Settling comes after and is not yours to call. */
    protected abstract void draw(V v);

    @Override
    public final void paint(V v) {
        draw(v);
        Settle.run(v, tally);
    }
}
