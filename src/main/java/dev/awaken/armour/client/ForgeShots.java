package dev.awaken.armour.client;

import dev.awaken.armour.FittingStandBlock;
import dev.awaken.armour.ForgeWork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Forge Song's shots (SPEC §44.4): wide on the stand, the anvil and the Forge while you choose; over the shoulder at
 * the fire; close above the metal for the strikes (still, so the marks hold); low beside it for the quench; down on the
 * works table by hand; a slow turn round the stand for the fit.
 */
final class ForgeShots {
    private ForgeShots() {}

    static Direction facing(BlockPos stand) {
        var level = Minecraft.getInstance().level;
        BlockState s = level == null ? null : level.getBlockState(stand);
        return s != null && s.hasProperty(FittingStandBlock.FACING) ? s.getValue(FittingStandBlock.FACING) : Direction.SOUTH;
    }

    /** The middle of what the smith uses (stand, anvil, Forge), framed right of centre for a list on the left. */
    static ForgeCamera.Shot wide(BlockPos stand, @Nullable BlockPos anvil, @Nullable BlockPos forge) {
        Vec3 a = Vec3.atCenterOf(stand).add(0, 0.5, 0);
        Vec3 c = a;
        int n = 1;
        if (anvil != null) {
            c = c.add(Vec3.atCenterOf(anvil));
            n++;
        }
        if (forge != null) {
            c = c.add(Vec3.atCenterOf(forge));
            n++;
        }
        Vec3 centre = c.scale(1.0 / n);
        double spread = Math.max(a.distanceTo(centre), Math.max(anvil == null ? 0 : Vec3.atCenterOf(anvil).distanceTo(centre),
                forge == null ? 0 : Vec3.atCenterOf(forge).distanceTo(centre)));
        double dist = Math.max(3.4, spread * 1.7 + 2.2), up = 1.3 + spread * 0.25;
        Direction f = facing(stand);
        double bearing = Math.toDegrees(Math.atan2(f.getStepX(), f.getStepZ())) + 28;
        return (t, out) -> {
            double b = Math.toRadians(bearing + Math.min(t, 900F) * 0.01);
            Vec3 eye = centre.add(Math.sin(b) * dist, up, Math.cos(b) * dist);
            double[] p = ForgeCamera.pose(eye, centre, 58);
            double yaw = Math.toRadians(p[3]);
            // look a little left of the middle, so it sits right of the list
            Vec3 at = centre.add(Math.cos(yaw) * dist * 0.22, 0, Math.sin(yaw) * dist * 0.22);
            System.arraycopy(ForgeCamera.pose(eye, at, 58), 0, out, 0, 6);
        };
    }

    /** The Forge's mouth, where the work heats: from beside and above, pushing slowly in. */
    static ForgeCamera.Shot heat(BlockPos forge) {
        var level = Minecraft.getInstance().level;
        BlockState s = level == null ? null : level.getBlockState(forge);
        Direction face = s != null && s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        Vec3 mouth = new Vec3(forge.getX() + 0.5 + face.getStepX() * 0.5, forge.getY() + 0.34, forge.getZ() + 0.5 + face.getStepZ() * 0.5);
        Vec3 side = new Vec3(-face.getStepZ(), 0, face.getStepX());
        Vec3 eye = mouth.add(face.getStepX() * 1.5, 0.55, face.getStepZ() * 1.5).add(side.scale(0.55));
        return ForgeCamera.push(eye, mouth, 52, 0.22, 140);
    }

    /** Close above the metal, from the stand's side: still, so the marks don't swim. */
    static ForgeCamera.Shot anvil(ForgeFrame f) {
        Vec3 top = f.top().add(0, ForgeFrame.FACE, 0);
        Vec3 eye = top.subtract(f.along().scale(0.62)).add(0, 1.12, 0);
        // looking a little short of the metal puts it in the frame's upper middle, clear of the beat lane below
        return ForgeCamera.look(eye, top.subtract(f.along().scale(0.14)), 48);
    }

    /** Low beside the work for the quench, the metal against the dark. */
    static ForgeCamera.Shot quench(ForgeFrame f) {
        Vec3 top = f.top().add(0, ForgeFrame.FACE, 0);
        Vec3 eye = top.add(f.across().scale(1.25)).subtract(f.along().scale(0.35)).add(0, 0.28, 0);
        return ForgeCamera.push(eye, top, 48, 0.15, 60);
    }

    /** Down on the works table for the cut and the rivets. */
    static ForgeCamera.Shot table(ForgeFrame f) {
        Vec3 top = f.top().add(0, ForgeFrame.FACE, 0);
        Vec3 eye = top.subtract(f.along().scale(0.35)).add(0, 1.2, 0);
        return ForgeCamera.look(eye, top.subtract(f.along().scale(0.08)), 48);
    }

    /** The fit: a slow turn round the suit on its stand, from its right to its left. */
    static ForgeCamera.Shot fit(BlockPos stand) {
        Direction f = facing(stand);
        double bearing = Math.toDegrees(Math.atan2(f.getStepX(), f.getStepZ()));
        return ForgeCamera.orbit(Vec3.atCenterOf(stand).add(0, 0.75, 0), 2.6, 0.25, bearing + 40, bearing - 40, 220, 50);
    }

    static ForgeCamera.Shot stage(ForgeWork w, BlockPos stand, int stage) {
        ForgeFrame f = ForgeFrame.of(stand, w.work);
        return switch (stage) {
            case dev.awaken.armour.ForgeSong.HEAT -> w.forge.isPresent() ? heat(w.forge.get()) : anvil(f);
            case dev.awaken.armour.ForgeSong.QUENCH -> quench(f);
            case dev.awaken.armour.ForgeSong.CUT, dev.awaken.armour.ForgeSong.RIVET -> table(f);
            default -> anvil(f);
        };
    }
}
