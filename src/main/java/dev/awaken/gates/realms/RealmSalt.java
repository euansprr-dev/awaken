package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.run.Runs;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/**
 * Saltcomb Flats' one rule (SPEC 50.8): the crust over the brine sets hard for a minute in the cool wind, then goes soft for forty seconds when the sun comes back.
 * While it's hard, the three breaks in the road are something you run across. While it's soft, they're a slow swim, and so is anything that was standing on them.
 */
final class RealmSalt {
    static final int HARD = 20 * 60, SOFT = 20 * 40;

    private RealmSalt() {}

    /** Runs twice a second per player in a Realm; the clock in the Realm's state means it only happens once. */
    static void tick(ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st) {
        if (!"salt".equals(plan.bp().law().kind())) return;
        long now = level.getGameTime();
        if (st.saltAt == 0) st.saltAt = now + HARD;
        if (now < st.saltAt) return;
        st.saltSoft = !st.saltSoft;
        st.saltAt = now + (st.saltSoft ? SOFT : HARD);
        apply(level, ref, plan.land(), st.saltSoft ? "salt_soft" : "salt_hard");
        for (ServerPlayer p : players(level, g)) {
            BlockPos at = p.blockPosition();
            if (st.saltSoft) {
                level.playSound(null, at, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.45F, 0.6F);
                level.playSound(null, at, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8F, 0.55F);
                if (!st.saltTold) Signal.toast(p, Component.literal("The sun's come back and the salt has gone soft. It won't hold you until the wind gets up again."));
            } else {
                level.playSound(null, at, SoundEvents.CALCITE_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
                if (!st.saltHardTold && st.saltTold) Signal.toast(p, Component.literal("The wind's got up and the salt has set hard again. You've got about a minute."));
            }
        }
        if (st.saltSoft) st.saltTold = true;
        else if (st.saltTold) st.saltHardTold = true;
    }

    private static java.util.List<ServerPlayer> players(ServerLevel level, GateRecord g) {
        return level.players().stream().filter(p -> Runs.run(p).gateId == g.id).toList();
    }

    /** Salt never sets around anybody: a cell with something in it stays brine until the next turn. */
    private static void apply(ServerLevel level, RealmRef ref, Land land, String moment) {
        for (Land.Edit e : land.change(moment)) {
            BlockPos at = Realms.world(ref.cell(), e.x(), e.y(), e.z());
            if (!level.isLoaded(at)) continue;
            if (e.state().getFluidState().isEmpty() && !level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
            level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
        }
    }
}
