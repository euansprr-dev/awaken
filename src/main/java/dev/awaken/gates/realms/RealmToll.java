package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GatesRegistry;
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
 * The Hour Crypt's one rule (SPEC 50.8): once a minute the great bell tolls, every door in the crypt drops on the stroke, and they lift again when the
 * ringing has died away. So a door is something you run for, and something that can part you from whatever's behind you, or shut you in with it.
 */
final class RealmToll {
    static final int PERIOD = 20 * 60, SHUT = 20 * 9;

    private RealmToll() {}

    /** Runs twice a second per player in a Realm; the clock in the Realm's state means it only happens once. */
    static void tick(ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st) {
        if (!"tolls".equals(plan.bp().law().kind())) return;
        long now = level.getGameTime();
        if (st.tollAt == 0) st.tollAt = now + PERIOD;
        if (st.tollOpenAt != 0 && now >= st.tollOpenAt) {
            st.tollOpenAt = 0;
            apply(level, ref, plan.land(), "toll_open");
            // the one door that works the other way round shuts again, unless someone has been through it: then it stays open for good
            if (!st.woken.contains("hidden_reliquary")) apply(level, ref, plan.land(), "relic_shut");
            for (ServerPlayer p : players(level, g)) level.playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 0.55F);
        }
        // the bell's tongue swings for as long as the doors are down, and each stroke after the first sounds again, quieter
        if (RealmClapper.tick(level, g, ref, plan.land(), st.tollOpenAt == 0 ? -1 : (int) (now - (st.tollOpenAt - SHUT)))) {
            for (ServerPlayer p : players(level, g)) level.playSound(null, p.blockPosition(), GatesRegistry.SND_WORLD_GREAT.get(), SoundSource.BLOCKS, 0.55F, 0.75F);
        }
        if (now < st.tollAt) return;
        st.tollAt = now + PERIOD;
        st.tollOpenAt = now + SHUT;
        apply(level, ref, plan.land(), "toll_shut");
        apply(level, ref, plan.land(), "relic_open");
        for (ServerPlayer p : players(level, g)) {
            level.playSound(null, p.blockPosition(), GatesRegistry.SND_WORLD_GREAT.get(), SoundSource.BLOCKS, 1.4F, 0.75F);
            level.playSound(null, p.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.5F);
            if (!st.tolled) Signal.toast(p, Component.literal("The bell has rung and every door in the crypt has dropped. They'll lift again when it stops."));
        }
        st.tolled = true;
        RealmClapper.tick(level, g, ref, plan.land(), 0);
    }

    private static java.util.List<ServerPlayer> players(ServerLevel level, GateRecord g) {
        return level.players().stream().filter(p -> Runs.run(p).gateId == g.id).toList();
    }

    /** A door never comes down on anyone: a cell with somebody in it stays open until the next stroke. */
    private static void apply(ServerLevel level, RealmRef ref, Land land, String moment) {
        for (Land.Edit e : land.change(moment)) {
            BlockPos at = Realms.world(ref.cell(), e.x(), e.y(), e.z());
            if (!dev.awaken.core.api.Ready.loaded(level, at)) continue;
            if (!e.state().isAir() && !level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
            level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
        }
    }
}
