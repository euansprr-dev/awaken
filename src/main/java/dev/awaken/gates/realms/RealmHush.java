package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.realms.land.QuietBarrow;
import dev.awaken.gates.run.Runs;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The Quiet Barrow's one rule (SPEC 50.8): sound carries. Each of the nine sealed mounds sleeps until somebody sprints within earshot of it; then
 * its seal comes out of the doorway, the candles inside light, and what was under it comes out. Walking and sneaking never wake anything.
 *
 * <p>What has woken is kept in the Realm's saved set of woken things ("hush" + the mound, and "hush_told"), so it survives a restart. A sleeping
 * mound's den is held back by marking it woken before RealmLive looks at it, and let go again when the mound wakes.
 */
final class RealmHush {
    /** How near a runner has to be to a mound's middle to be heard. */
    static final int EARSHOT = 22;

    private RealmHush() {}

    /** Runs twice a second per player in a Realm, before the dens are looked at. */
    static void tick(ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st) {
        if (!"hush".equals(plan.bp().law().kind()) || !(plan.land() instanceof QuietBarrow land)) return;
        List<int[]> mounds = land.sleepers();
        boolean changed = false;
        if (st.woken.add("hush_held")) {
            for (int i = 0; i < mounds.size(); i++) st.woken.add("den" + i);
            changed = true;
        }
        for (ServerPlayer p : level.players()) {
            if (Runs.run(p).gateId != g.id || !p.isSprinting()) continue;
            int x = p.getBlockX() - RealmCells.centerX(ref.cell()), z = p.getBlockZ() - RealmCells.centerZ(ref.cell());
            for (int i = 0; i < mounds.size(); i++) {
                int dx = mounds.get(i)[0] - x, dz = mounds.get(i)[1] - z;
                if (dx * dx + dz * dz > EARSHOT * EARSHOT || !st.woken.add("hush" + i)) continue;
                wake(level, land, ref, i);
                st.woken.remove("den" + i); // RealmLive finds the den unwoken on this same pass and brings out what was in it
                if (st.woken.add("hush_told")) Signal.toast(p, Component.literal("Something under that mound heard you running. Walk past the others."));
                changed = true;
            }
        }
        if (changed) RealmData.get(level.getServer()).setDirty();
    }

    private static void wake(ServerLevel level, QuietBarrow land, RealmRef ref, int i) {
        BlockPos door = null;
        for (Land.Edit e : land.change("wake_" + i)) {
            BlockPos at = Realms.world(ref.cell(), e.x(), e.y(), e.z());
            if (!dev.awaken.core.api.Ready.loaded(level, at)) continue;
            level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
            if (door == null) door = at;
        }
        if (door == null) return;
        level.playSound(null, door, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.5F, 0.5F);
        level.playSound(null, door, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 2.0F, 0.5F);
        level.playSound(null, door, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1.6F, 0.6F);
    }
}
