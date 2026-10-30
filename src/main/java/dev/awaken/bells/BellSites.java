package dev.awaken.bells;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The land's sleeping bells, retired (SPEC §43.16; Euan, 30 Oct: "I don't think we should just randomly place a bell in
 * the world"). Nothing is planned or built in open country any more and nothing out there hums. An old world's built
 * places are put back as land the next time anyone comes near: the grey bell goes, the grey ground turns green and the
 * thing that gathered round a kept one leaves. A bell already woken from one stays its waker's.
 */
public final class BellSites {
    private BellSites() {}

    /** The kinds an old world's sleeping places were saved with (kept so old saves read). */
    public enum Kind { BURIED, KEPT, SUNK, HIGH, SPIRIT }

    static final int NEAR = 96, PAINT = 14, SPIRIT_HANG = 7;

    /** The bells that lay in the land before (the gates, the villages' quests and the Veil's spirits have them now). */
    static final Set<String> RETIRED = Set.of("wren", "barrow", "mere", "wayside", "cairn", "spire", "reed", "thorn", "crag", "fen",
            "fenwick", "mill", "shepherd", "hill", "ford", "thaw", "boulder", "leviathan", "ghost", "whisper", "wake", "mourner", "veil");

    /** Every 5 s for each player in the overworld: an old built place within {@link #NEAR} is cleared. */
    static void tick(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        BellWorld w = BellWorld.get(level.getServer());
        if (w.sites.isEmpty()) return;
        for (Iterator<Map.Entry<String, BellWorld.Site>> it = w.sites.entrySet().iterator(); it.hasNext(); ) {
            BellWorld.Site s = it.next().getValue();
            if (s.state == BellWorld.Site.PLANNED || s.state == BellWorld.Site.GONE) {
                // planned and never built, or never could be: dropped
                it.remove();
                w.setDirty();
                continue;
            }
            if (!near(s.pos, p.blockPosition()) || !level.isLoaded(s.pos)) continue;
            clear(level, s);
            it.remove();
            w.setDirty();
        }
    }

    private static boolean near(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz <= (double) NEAR * NEAR;
    }

    /** Puts one old place back: the bell out, its keeper gone, and the land round it painted back, ring by ring. */
    static void clear(ServerLevel level, BellWorld.Site s) {
        BlockState st = level.getBlockState(s.pos);
        if (st.is(BellContent.SLEEPING_BELL.get()))
            level.setBlock(s.pos, (s.kind == Kind.SUNK || !level.getFluidState(s.pos.above()).isEmpty() ? Blocks.WATER : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
        if (s.keeper != null && level.getEntity(s.keeper) instanceof Entity k && k.isAlive()) k.discard();
        PAINTING.add(new Paint(s.kind == Kind.SPIRIT ? s.pos.below(SPIRIT_HANG) : s.pos, 0));
        Awaken.LOG.info("[bells] cleared an old sleeping place ({}) at {}", s.bell, s.pos.toShortString());
    }

    /** The land round a cleared place turns back, two rings a tick. Not saved: a restart leaves what's left grey. */
    private record Paint(BlockPos at, int ring) {}

    private static final List<Paint> PAINTING = new ArrayList<>();

    static void tickPaint(ServerLevel level) {
        if (PAINTING.isEmpty()) return;
        List<Paint> next = new ArrayList<>();
        for (Paint p : PAINTING) {
            if (!level.isLoaded(p.at)) continue;
            int r = p.ring;
            for (int k = 0; k < 2 && r <= PAINT; k++, r++) ring(level, p.at, r, level.getRandom());
            if (r <= PAINT) next.add(new Paint(p.at, r));
        }
        PAINTING.clear();
        PAINTING.addAll(next);
    }

    private static void ring(ServerLevel level, BlockPos c, int r, RandomSource rand) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r || d2 <= (r - 1) * (r - 1) && r > 0) continue;
                for (int dy = -6; dy <= 18; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    BlockState to = null;
                    if (s.is(Blocks.PALE_MOSS_BLOCK) || s.is(Blocks.COARSE_DIRT)) to = level.getBlockState(p.above()).isSolid() ? Blocks.DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    else if (s.is(Blocks.PALE_MOSS_CARPET)) to = Blocks.SHORT_GRASS.defaultBlockState();
                    else if (s.is(Blocks.DEAD_BUSH)) to = (rand.nextBoolean() ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState();
                    if (to != null) level.setBlock(p, to, Block.UPDATE_CLIENTS);
                }
            }
        if (r == 1) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(c).x, c.getY() + 1.5, Vec3.atCenterOf(c).z, 6, 2, 0.4, 2, 0);
    }

    /**
     * A Book from before (schema 3) loses its leads to bells that lay in the land: they weren't there after all. True if
     * any went, so the System can say so once.
     */
    static boolean closeOldLeads(BellBook b) {
        boolean any = false;
        for (String id : RETIRED) {
            BellCatalog.Def d = BellCatalog.of(id);
            if (d != null && b.leads.remove(d.no()) != null) {
                any = true;
                if (b.tracked == d.no()) b.tracked = -1;
            }
        }
        return any;
    }
}
