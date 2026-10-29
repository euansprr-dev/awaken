package dev.awaken.above.machine;

import dev.awaken.above.Above;
import dev.awaken.above.AboveData;
import dev.awaken.above.MachineType;
import dev.awaken.above.net.Panel;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.DescentHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Waypoint Pylon (fast travel) and Deep Lift (rides to lit Waystones, no Strain). */
final class TravelMachines {
    private TravelMachines() {}

    static final int LIFT_COST = 100;
    static final int CROSS_DIMENSION_COST = 400;

    /** Pylon jump cost: 50 + 1 per 32 blocks, capped at 400; other dimensions cost 400. */
    static int cost(GlobalPos from, GlobalPos to) {
        if (!from.dimension().equals(to.dimension())) return CROSS_DIMENSION_COST;
        double d = Math.sqrt(from.pos().distSqr(to.pos()));
        return (int) Math.min(CROSS_DIMENSION_COST, 50 + d / 32.0);
    }

    record Destination(String kind, String name, GlobalPos target, boolean exact) {}

    static List<Destination> destinations(ServerPlayer player, MachineBlockEntity be) {
        List<Destination> out = new ArrayList<>();
        GlobalPos here = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
        for (PylonNetwork.Entry e : PylonNetwork.get(player.level().getServer()).of(player.getUUID())) {
            if (e.pos().equals(here)) continue;
            out.add(new Destination("pylon", e.name(), e.pos(), false));
        }
        Optional<BlockPos> hearth = HearthHooks.hearthOf(player);
        hearth.ifPresent(h -> out.add(new Destination("hearth", "Your Hearth", GlobalPos.of(Level.OVERWORLD, h), false)));
        AboveData d = Above.data(player);
        for (AboveData.RuinMark r : d.ruins()) {
            RuinKind k = RuinKind.byId(r.kind());
            out.add(new Destination("ruin", k == null ? "Precursor ruin" : k.displayName, r.landing(), true));
        }
        return out;
    }

    static Panel pylonPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.WAYPOINT_PYLON;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        Panel.Builder b = Machines.base(be, t, true);
        GlobalPos here = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
        List<Destination> dests = destinations(player, be);
        if (dests.isEmpty()) {
            b.text("Nowhere yet", "Place another pylon, anchor a Hearth, or scan a machine in a ruin to add destinations.", Machines.AMBER);
        }
        String lastKind = "";
        for (int i = 0; i < dests.size(); i++) {
            Destination dst = dests.get(i);
            if (!dst.kind().equals(lastKind)) {
                b.header(switch (dst.kind()) { case "pylon" -> "Your pylons"; case "hearth" -> "Home"; default -> "Ruins you found"; });
                lastKind = dst.kind();
            }
            int cost = cost(here, dst.target());
            String where = dst.target().dimension().equals(here.dimension())
                    ? Travel.bearing(be.getBlockPos(), dst.target().pos())
                    : "another world";
            b.button(dst.name(), where, Machines.PALE, "go", i, "Go (" + cost + ")", stored >= cost);
        }
        return b.footer("Rename a pylon in an anvil before placing it to name it.").build();
    }

    /** Returns true if the player left (close the panel). */
    static boolean pylonAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("go")) return false;
        List<Destination> dests = destinations(player, be);
        if (index < 0 || index >= dests.size()) return false;
        Destination dst = dests.get(index);
        GlobalPos here = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
        int cost = cost(here, dst.target());
        ServerLevel target = player.level().getServer().getLevel(dst.target().dimension());
        if (target == null) return false;
        BlockPos to = dst.target().pos();
        boolean pylon = dst.kind().equals("pylon");
        // a pylon whose land is loaded is checked now; one whose land isn't, once it is (no load on the tick, SPEC §28.9)
        if (pylon && target.getChunkSource().getChunkNow(to.getX() >> 4, to.getZ() >> 4) != null && gone(player, target, dst)) return false;
        if (!be.draw(cost)) {
            player.sendOverlayMessage(Component.literal("Not enough essence"));
            return false;
        }
        // there once its land is made or read in (SPEC §28.9, "Arrivals")
        dev.awaken.core.api.Passage.go(player, target, to.getX() + 0.5, to.getZ() + 0.5, () -> {
            if (pylon && gone(player, target, dst)) {
                be.addEssence(cost);
                return;
            }
            Travel.teleport(player, target, dst.exact() ? to : Travel.safeSpot(target, to));
            Progression.count(player, Above.PYLON_JUMPS, 1);
            DirectorLog.log(player, "pylon", "to " + dst.kind() + " " + dst.name() + " for " + cost);
        });
        return true;
    }

    /** Whether the destination pylon no longer stands (then it's forgotten, and the player told). Its land must be loaded. */
    private static boolean gone(ServerPlayer player, ServerLevel target, Destination dst) {
        if (target.getBlockEntity(dst.target().pos()) instanceof MachineBlockEntity other && other.type() == MachineType.WAYPOINT_PYLON) return false;
        PylonNetwork.get(player.level().getServer()).removeAt(dst.target());
        player.sendOverlayMessage(Component.literal("That pylon is gone."));
        return true;
    }

    // ---- Deep Lift ------------------------------------------------------------------------------------------------

    static Panel liftPanel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.DEEP_LIFT;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        Panel.Builder b = Machines.base(be, t, true);
        List<Integer> strata = new ArrayList<>(DescentHooks.litWaystones(player));
        strata.sort(Integer::compare);
        if (strata.isEmpty()) {
            b.text("No Waystones lit", "Light a Waystone in the Descent and the lift will know the way.", Machines.AMBER);
        }
        for (int s : strata) {
            b.button("Stratum " + s, "Your Waystone camp · no Strain", Machines.PALE, "ride", s, "Ride (" + LIFT_COST + ")", stored >= LIFT_COST);
        }
        return b.footer("Climbing out on foot costs Strain. The lift doesn't.").build();
    }

    static boolean liftAction(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("ride") || !DescentHooks.litWaystones(player).contains(index)) return false;
        if (be.poweredEssence() < LIFT_COST) return false;
        if (!DescentHooks.ride(player, index)) {
            player.sendOverlayMessage(Component.literal("The lift won't go there right now."));
            return false;
        }
        be.draw(LIFT_COST);
        DirectorLog.log(player, "deep-lift", "to stratum " + index);
        return true;
    }
}
