package dev.awaken.above.machine;

import dev.awaken.above.Above;
import dev.awaken.above.AboveData;
import dev.awaken.above.MachineType;
import dev.awaken.above.halo.Halo;
import dev.awaken.above.net.Panel;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.DreamHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.progression.Directives;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;

import java.util.List;
import java.util.Optional;

/** Dream Recorder, Signal Relay, Recalibrator and Observatory. */
final class HomeMachines {
    private HomeMachines() {}

    static final int REPLAY_COST = 100;
    static final int RELAY_COST = 150;
    static final int RELAY_RANGE = 48;
    static final int SURVEY_RANGE = 3000;
    /** The Halo shows itself to B-rank eyes ("late in Act II"). */
    static final Rank HALO_RANK = Rank.B;

    // ---- Dream Recorder -------------------------------------------------------------------------------------------

    static boolean bedNear(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 1, 3))) {
            if (level.getBlockState(p).getBlock() instanceof BedBlock) return true;
        }
        return false;
    }

    static Panel recorderPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.DREAM_RECORDER;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        Panel.Builder b = Machines.base(be, t, true);
        if (!bedNear(be.getLevel(), be.getBlockPos())) {
            return b.text("No bed", "The recorder only works beside a bed (within 3 blocks).", Machines.WARN).footer(t.blurb).build();
        }
        int n = DreamHooks.recordedCount(player);
        if (n <= 0) b.text("Nothing recorded", "Sleep here. The recorder keeps what it can of your dreams.", Machines.AMBER);
        for (int i = 0; i < Math.min(8, n); i++) {
            b.button(i == 0 ? "Latest dream" : "Dream " + (i + 1), i == 0 ? "Recorded most recently" : "Recorded " + (i + 1) + " dreams ago",
                    Machines.PALE, "replay", i, "Replay (" + REPLAY_COST + ")", stored >= REPLAY_COST);
        }
        return b.footer("Do not record nightmares. They learned that the hard way.").build();
    }

    static boolean recorderAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("replay") || index < 0 || index >= DreamHooks.recordedCount(player)) return false;
        if (!bedNear(be.getLevel(), be.getBlockPos()) || be.poweredEssence() < REPLAY_COST) return false;
        if (!DreamHooks.replay(player, index)) {
            player.sendOverlayMessage(Component.literal("The recording won't play right now."));
            return false;
        }
        be.draw(REPLAY_COST);
        DirectorLog.log(player, "dream-recorder", "replay " + index);
        return true;
    }

    // ---- Signal Relay ---------------------------------------------------------------------------------------------

    static boolean nearHearth(ServerPlayer player, MachineBlockEntity be) {
        if (be.getLevel().dimension() != Level.OVERWORLD) return false;
        // any Hearth you own or share counts as home (SPEC "Together")
        for (BlockPos h : HearthHooks.hearthsOf(player)) if (h.distSqr(be.getBlockPos()) <= (double) RELAY_RANGE * RELAY_RANGE) return true;
        return false;
    }

    static Panel relayPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.SIGNAL_RELAY;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        Panel.Builder b = Machines.base(be, t, true);
        if (!nearHearth(player, be)) {
            return b.text("Out of range", "The relay needs your Hearth within " + RELAY_RANGE + " blocks.", Machines.WARN).footer(t.blurb).build();
        }
        long today = PlayDays.current(player.level().getServer());
        // A powered relay near your Hearth links for seven play-days: +1 Directive slot (see Above.RELAY_LINK).
        if (Progression.profile(player).counter(Above.RELAY_LINK) != today + 1) Progression.setCounter(player, Above.RELAY_LINK, today + 1);
        AboveData d = Above.data(player);
        Profile p = Progression.profile(player);
        boolean used = d.relayDay() == today;
        boolean full = p.directives().size() >= Directives.slots(player);
        String detail = used ? "Boosted today. It recharges with the next play-day."
                : full ? "Your Directives are full. Finish or let one fade first."
                : "Ask the Signal for a fresh Directive now.";
        b.button("Boost the Signal", detail, used || full ? Machines.LABEL : Machines.GOOD, "boost", 0, "Boost (" + RELAY_COST + ")",
                !used && !full && stored >= RELAY_COST && p.awakened());
        return b.footer("Linked: your Signal holds one more Directive while the relay stays powered.").build();
    }

    static void relayAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("boost") || !nearHearth(player, be)) return;
        long today = PlayDays.current(player.level().getServer());
        AboveData d = Above.data(player);
        if (d.relayDay() == today || Progression.profile(player).directives().size() >= Directives.slots(player)) return;
        if (!be.draw(RELAY_COST)) return;
        d.setRelayDay(today);
        Above.sync(player);
        int before = Progression.profile(player).directives().size();
        Directives.tryOffer(player, "signal_relay");
        if (Progression.profile(player).directives().size() == before) Signal.toast(player, Component.literal("The Signal had nothing new to say."));
    }

    // ---- Recalibrator ---------------------------------------------------------------------------------------------

    static Panel recalibratorPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.RECALIBRATOR;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        Panel.Builder b = Machines.base(be, t, true);
        Profile p = Progression.profile(player);
        StringBuilder line = new StringBuilder();
        for (Stat s : Stat.values()) {
            if (!line.isEmpty()) line.append(" · ");
            line.append(s.name()).append(' ').append(p.stat(s));
        }
        b.text("Your stats", line.toString(), Machines.PALE);
        int refund = Recalibrate.refund(Recalibrate.stats(p));
        int cost = Recalibrate.cost(refund);
        b.button("Recalibrate", refund == 0 ? "Nothing to refund. Every stat is at its start." :
                        "Returns every stat to " + Stat.START + " and gives back " + refund + " point" + (refund == 1 ? "" : "s") + ".",
                refund == 0 ? Machines.LABEL : Machines.AMBER, "respec", 0, "Recalibrate (" + cost + ")", refund > 0 && stored >= cost);
        return b.footer("Spend the points again from Status · K.").build();
    }

    static void recalibratorAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("respec")) return;
        int refund = Recalibrate.refund(Recalibrate.stats(Progression.profile(player)));
        int cost = Recalibrate.cost(refund);
        if (refund <= 0 || !be.draw(cost)) return;
        int n = Recalibrate.apply(player);
        Progression.syncNow(player);
        Signal.toast(player, Component.literal("Recalibrated · " + n + " points to spend"));
        DirectorLog.log(player, "recalibrator", "refunded " + n + " for " + cost);
    }

    // ---- Observatory ----------------------------------------------------------------------------------------------

    static Panel observatoryPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.OBSERVATORY;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        Panel.Builder b = Machines.base(be, t, true);
        ServerLevel level = (ServerLevel) be.getLevel();
        if (!level.canSeeSky(be.getBlockPos().above()) || level.dimension() != Level.OVERWORLD) {
            return b.text("No sky", "The Observatory needs open sky above it, in the overworld.", Machines.WARN).footer(t.blurb).build();
        }
        AboveData d = Above.data(player);
        b.header("Sky ruins");
        List<RuinLocator.Found> sky = RuinLocator.near(level, be.getBlockPos(), SURVEY_RANGE, List.of(RuinKind.SKY), d, false, 5);
        if (sky.isEmpty()) b.text("None", "No platforms within " + SURVEY_RANGE + " blocks.", Machines.LABEL);
        for (RuinLocator.Found f : sky) {
            b.text(f.discovered() ? "Sky Platform · found" : "Sky Platform", Travel.bearing(be.getBlockPos(), f.pos()) + " · above Y 260",
                    f.discovered() ? Machines.GOOD : Machines.CYAN);
        }
        b.header("Orbit");
        boolean seen = d.haloSeen() || Progression.profile(player).counter(Above.HALO_SEEN) > 0;
        if (seen) {
            b.text("The Halo", "A Precursor station in orbit. A Launch Ring can reach it.", Machines.AMBER);
        } else if (Progression.rank(player).ordinal() >= HALO_RANK.ordinal()) {
            b.button("Something in orbit", "A ring of light, too regular to be a star.", Machines.AMBER, "halo", 0, "Look closer", true);
        } else {
            b.text("Something glints", "Past the sky, too far to make out. Your eyes aren't ready yet (" + HALO_RANK.label + "-rank).", Machines.LABEL);
        }
        return b.footer("Sky platforms glow from below at night.").build();
    }

    static void observatoryAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("halo") || Progression.rank(player).ordinal() < HALO_RANK.ordinal()) return;
        Halo.reveal(player);
    }
}
