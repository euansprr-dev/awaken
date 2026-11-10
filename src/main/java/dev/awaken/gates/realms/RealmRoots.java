package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.realms.land.VineSeat;
import dev.awaken.gates.run.Runs;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Vine Seat's one rule (SPEC 50.8): the roots close in on whatever stands still. The curtains across its passages draw back for someone coming at them on
 * the move and shut again behind them; stand about anywhere else and the roots start up your legs.
 */
final class RealmRoots {
    /** In wake-ups, which come twice a second. */
    private static final int TAKES_HOLD = 8, BITES = 20;
    private static final int OPEN_FOR = 20 * 3;

    private RealmRoots() {}

    static void tick(ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st) {
        if (!"roots".equals(plan.bp().law().kind()) || !(plan.land() instanceof VineSeat land)) return;
        long now = level.getGameTime();
        if (now == st.rootsAt) return; // this runs once per player; the roots only need the once
        st.rootsAt = now;
        int n = land.curtainCount();
        if (st.rootOpen == null) {
            st.rootOpen = new long[n];
            st.rootShown = new boolean[n];
            for (int i = 0; i < n; i++) st.rootShown[i] = true; // drawn open, so the first pass shuts them
        }
        BlockPos origin = Realms.world(ref.cell(), 0, 0, 0);
        for (ServerPlayer p : level.players()) {
            if (Runs.run(p).gateId != g.id) continue;
            Vec3 pos = p.position(), last = st.rootLast.put(p.getUUID(), pos);
            boolean moving = last == null || pos.distanceTo(last) > 0.6;
            int lx = p.getBlockX() - origin.getX(), ly = p.getBlockY(), lz = p.getBlockZ() - origin.getZ();
            if (moving) {
                st.rootStill.remove(p.getUUID());
                for (int i = 0; i < n; i++) {
                    int[] c = land.curtainAt(i);
                    if (Math.abs(lx - c[0]) <= 7 && Math.abs(lz - c[2]) <= 7 && Math.abs(ly - c[1]) <= 6) st.rootOpen[i] = now + OPEN_FOR;
                }
            } else {
                int still = st.rootStill.merge(p.getUUID(), 1, Integer::sum);
                if (still >= TAKES_HOLD && !safe(land, lx, lz) && !p.isCreative() && !p.isSpectator()) hold(level, p, still, st);
            }
        }
        for (int i = 0; i < n; i++) {
            boolean open = st.rootOpen[i] > now;
            if (open == st.rootShown[i]) continue;
            st.rootShown[i] = open;
            apply(level, ref, land, (open ? "roots_open_" : "roots_shut_") + i, open);
        }
    }

    private static boolean safe(VineSeat land, int x, int z) {
        for (Land.Site s : land.sites(Land.SiteKind.WAYSTONE)) if (Math.abs(s.x() - x) <= 6 && Math.abs(s.z() - z) <= 6) return true;
        for (Land.Site s : land.sites(Land.SiteKind.VILLAGE)) if (Math.abs(s.x() - x) <= s.radius() && Math.abs(s.z() - z) <= s.radius()) return true;
        return false;
    }

    private static void hold(ServerLevel level, ServerPlayer p, int still, RealmState st) {
        int amp = Math.min(3, (still - TAKES_HOLD) / 4);
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, amp, false, false, false));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.4, 0.15, 0.4, 0.02);
        level.playSound(null, p.blockPosition(), SoundEvents.HANGING_ROOTS_PLACE, SoundSource.BLOCKS, 0.5F, 0.6F + amp * 0.1F);
        if (!st.rootsTold) {
            st.rootsTold = true;
            Signal.toast(p, Component.literal("The roots are climbing your legs. Keep moving and they'll let go."));
        }
        if (still >= BITES && still % 4 == 0) p.hurtServer(level, level.damageSources().magic(), 1.0F);
    }

    /** A curtain never shuts on anyone: a cell with somebody in it stays open until they've gone. */
    private static void apply(ServerLevel level, RealmRef ref, Land land, String moment, boolean open) {
        boolean first = true;
        for (Land.Edit e : land.change(moment)) {
            BlockPos at = Realms.world(ref.cell(), e.x(), e.y(), e.z());
            if (!level.isLoaded(at)) continue;
            if (!open && !level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
            level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
            if (first) level.playSound(null, at, open ? SoundEvents.MANGROVE_ROOTS_BREAK : SoundEvents.MANGROVE_ROOTS_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
            first = false;
        }
    }
}
