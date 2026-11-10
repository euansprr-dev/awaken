package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.run.Runs;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Wax Galleries' rule (SPEC 50.8): warm wax slows you, cold wax holds your weight. {@link WarmWaxBlock} does the dragging. This only says so, once, the first time someone is standing in it.
 */
final class RealmWax {
    private RealmWax() {}

    static void tick(ServerLevel level, GateRecord g, RealmPlan plan, RealmRef ref, RealmState st) {
        if (st.waxTold || !"wax".equals(plan.bp().law().kind())) return;
        for (ServerPlayer p : level.players()) {
            if (Runs.run(p).gateId != g.id || p.isSpectator()) continue;
            if (!level.getBlockState(p.blockPosition()).is(WarmWaxBlock.BLOCK.get())) continue;
            st.waxTold = true;
            Signal.toast(p, Component.literal("The warm wax will drag at you all the way across. The pale stuff is cold, and it will hold your weight."));
            return;
        }
    }
}
