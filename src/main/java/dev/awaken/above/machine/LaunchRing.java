package dev.awaken.above.machine;

import dev.awaken.above.Above;
import dev.awaken.above.AboveBlocks;
import dev.awaken.above.MachineType;
import dev.awaken.above.halo.Halo;
import dev.awaken.above.net.Panel;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The Launch Ring: a core with sixteen alloy frames around it (the outline of a 5x5 square at the core's height).
 * Formed, powered and pointed at a known Halo, it launches whoever stands at it.
 */
public final class LaunchRing {
    private LaunchRing() {}

    public static final int LAUNCH_COST = 2000;
    public static final int FRAMES = 16;

    /** The sixteen frame positions around a core. */
    public static List<BlockPos> framePositions(BlockPos core) {
        List<BlockPos> out = new ArrayList<>(FRAMES);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) out.add(core.offset(dx, 0, dz));
            }
        }
        return out;
    }

    public static List<BlockPos> missing(Level level, BlockPos core) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : framePositions(core)) if (!level.getBlockState(p).is(AboveBlocks.RING_FRAME.get())) out.add(p);
        return out;
    }

    static boolean haloKnown(ServerPlayer player) {
        return Above.data(player).haloSeen() || Progression.profile(player).counter(Above.HALO_SEEN) > 0;
    }

    static Panel panel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.LAUNCH_RING;
        Panel.Builder b = Machines.base(be, t, true);
        List<BlockPos> missing = missing(be.getLevel(), be.getBlockPos());
        int placed = FRAMES - missing.size();
        b.text("Ring frames", placed + " / " + FRAMES + (missing.isEmpty() ? " · formed" : " · place Launch Ring Frames around the core (5x5 outline)"),
                missing.isEmpty() ? Machines.GOOD : Machines.WARN);
        if (!missing.isEmpty() && be.getLevel() instanceof ServerLevel sl) {
            for (BlockPos p : missing) {
                sl.sendParticles(player, ParticleTypes.END_ROD, true, false, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.0);
            }
        }
        boolean known = haloKnown(player);
        b.text("Destination", known ? "The Halo · in orbit" : "Nowhere yet. Something has to show you where to aim. Try an Observatory.",
                known ? Machines.AMBER : Machines.LABEL);
        int stored = be.poweredEssence();
        boolean ready = missing.isEmpty() && known && stored >= LAUNCH_COST;
        b.button("Launch", ready ? "Stand clear of the frames. You go alone." : "Needs a formed ring, a destination and " + LAUNCH_COST + " essence.",
                ready ? Machines.GOOD : Machines.LABEL, "launch", 0, "Launch (" + LAUNCH_COST + ")", ready);
        return b.footer(t.blurb).build();
    }

    /** Returns true if the player left. */
    static boolean action(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("launch")) return false;
        if (!missing(be.getLevel(), be.getBlockPos()).isEmpty() || !haloKnown(player)) return false;
        if (!be.draw(LAUNCH_COST)) return false;
        Halo.launch(player, (ServerLevel) be.getLevel(), be.getBlockPos());
        return true;
    }
}
