package dev.awaken.hearth.trial;

import dev.awaken.core.api.TrialHooks;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The first Proving's Warden (SPEC §46.4): the Ringer in the Drowned Belfry, for D. You wake knee-deep inside the door;
 * bells toll above; it drops into the light when you climb onto the chancel. At half its health the water runs off
 * toward the pit and the floor over it gives way. Its chain lies at your feet after: the Tether.
 */
final class RingerWarden extends Warden {
    static final RingerWarden WARDEN = new RingerWarden();
    static final String MOB = "awaken:proving_ringer";
    /** The water runs off this many rows a tick, north to south, toward the pit. */
    static final int DRAIN_ROWS = 2;
    /** The lid over the pit cracks when the water starts, then opens a ring every few ticks from the middle. */
    static final int LID_AFTER = 24, LID_RING_TICKS = 3;

    private RingerWarden() {
        super(MOB, Rank.D, Belfry.HALL);
    }

    @Override
    String asked() {
        return "The Registry will measure you again, the same as on your first day. Put your hand on the bell when you're ready.";
    }

    /** The Reading Bell rings again, lower, and doesn't stop; for the first time since the awakening the System breaks. */
    @Override
    int ring(ServerPlayer m, boolean practice) {
        m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 0.5F);
        if (practice) return 40;
        dev.awaken.fault.Fault ringer = new dev.awaken.fault.Fault(dev.awaken.fault.Fault.Size.GREAT, "ringer",
                List.of("[ERROR] … THAT'S NOT OUR BELL", "READING · RANK ▮ · RANK ▮ · RANK ▮"), "[ERROR] it rang for you last time too",
                "That isn't the Registry's bell. Something else is ringing it.", null, null, 0, true);
        return dev.awaken.fault.Faults.play(m, ringer) == dev.awaken.fault.Fault.Size.GREAT ? ringer.length() : 60;
    }

    @Override
    BlockPos wakeAt(BlockPos centre) {
        return centre.offset(0, 0, Belfry.WAKE_Z);
    }

    /** Into the water inside the door, facing the chancel: the first time, after a fall, after a reset. */
    @Override
    void wake(ServerPlayer m, Provings.Run run, boolean first) {
        Vec3 at = Vec3.atBottomCenterOf(wakeAt(run.centre));
        m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, 0F, TeleportTransition.DO_NOTHING));
        m.resetFallDistance();
        run.level.playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 0.8F);
        if (first) run.level.playSound(null, run.centre.offset(0, 20, 0), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3F, 0.5F);
    }

    /** Something up in the bells is swinging them: a toll from one hanging bell and then another, low and slow. */
    @Override
    void waiting(Provings.Run run, List<ServerPlayer> here, long now) {
        if (now < run.nextAmbient) return;
        int[] b = Belfry.HUNG[(int) Math.floorMod(now * 7 + run.cell, Belfry.HUNG.length)];
        BlockPos at = run.centre.offset(b[0], b[3] + 2, b[1]);
        float pitch = 0.5F + 0.08F * (b[2] == 2 ? 3 : 0) + run.level.getRandom().nextFloat() * 0.1F;
        run.level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3F, pitch);
        run.nextAmbient = now + 45 + run.level.getRandom().nextInt(30);
    }

    /** It drops when anyone climbs onto the chancel, or after half a minute anyway. */
    @Override
    boolean comes(Provings.Run run, List<ServerPlayer> here, long now) {
        for (ServerPlayer m : here) if (onChancel(run, m)) return true;
        return now - run.waitingSince >= Provings.COME_AFTER;
    }

    private static boolean onChancel(Provings.Run run, ServerPlayer m) {
        if (m.level() != run.level) return false;
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ() - 0.5;
        return Math.abs(lx) <= Belfry.CHANCEL_X + 0.5 && lz >= Belfry.CHANCEL_N && lz <= Belfry.CHANCEL_S + 0.5
                && m.getY() >= run.centre.getY() + 0.9 && m.getY() <= run.centre.getY() + 6;
    }

    /** Into the light at the chancel's middle, facing the door. */
    @Override
    Vec3 arrive(Provings.Run run) {
        return new Vec3(run.centre.getX() + 0.5, run.centre.getY() + 1, run.centre.getZ() + Belfry.DROP_Z + 0.5);
    }

    @Override
    float arriveYaw(Provings.Run run) {
        return 0F;
    }

    /** The door's water is no hiding place, the stair is the way out. */
    @Override
    int home() {
        return Belfry.SOUTH;
    }

    @Override
    void fight(Provings.Run run, LivingEntity warden, long now) {
        if (run.turns == 0 && warden.getHealth() <= warden.getMaxHealth() * 0.5F) drain(run, now);
    }

    @Override
    void died(Provings.Run run, long now) {
        if (run.turns == 0) drain(run, now);
    }

    /** Its second phase (at half its health) and its death: the water runs off toward the pit, and the floor over it gives way. */
    private static void drain(Provings.Run run, long now) {
        run.turns = 1;
        run.turnAt = now;
        run.turnRow = Belfry.NORTH + 1;
        BlockPos pit = run.centre.offset(0, 0, Belfry.PIT_Z);
        run.level.playSound(null, pit, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4F, 0.5F);
        run.level.playSound(null, pit, SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.BLOCKS, 4F, 0.6F);
        // the lid's ring cracks first: a second's warning before it goes
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -Belfry.PIT_R; x <= Belfry.PIT_R; x++) {
            for (int z = Belfry.PIT_Z - Belfry.PIT_R; z <= Belfry.PIT_Z + Belfry.PIT_R; z++) {
                if (!Belfry.lid(x, z)) continue;
                at.set(run.centre.getX() + x, run.centre.getY(), run.centre.getZ() + z);
                if (!run.level.getBlockState(at).isAir()) run.level.setBlock(at, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        run.level.playSound(null, pit, SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.BLOCKS, 3F, 0.5F);
    }

    @Override
    void step(Provings.Run run, long now) {
        if (run.turnAt < 0) return;
        long t = now - run.turnAt;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int n = 0; n < DRAIN_ROWS && run.turnRow < Belfry.SOUTH; n++, run.turnRow++) {
            int z = run.turnRow;
            for (int x = -Belfry.WALL_X + 1; x < Belfry.WALL_X; x++) {
                BlockState built = Belfry.block(x, 0, z);
                if (!Belfry.wet(built)) continue;
                at.set(run.centre.getX() + x, run.centre.getY(), run.centre.getZ() + z);
                BlockState now0 = run.level.getBlockState(at);
                if (Belfry.wet(now0)) run.level.setBlock(at, Belfry.dry(now0), Block.UPDATE_CLIENTS);
                if ((x & 7) == 0) run.level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 4, 0.4, 0.1, 0.4, 0.1);
            }
        }
        long ring = (t - LID_AFTER) / LID_RING_TICKS;
        if (t >= LID_AFTER && (t - LID_AFTER) % LID_RING_TICKS == 0 && ring <= Belfry.PIT_R) {
            // the floor gives way a ring at a time from the middle out
            for (int x = -Belfry.PIT_R; x <= Belfry.PIT_R; x++) {
                for (int z = Belfry.PIT_Z - Belfry.PIT_R; z <= Belfry.PIT_Z + Belfry.PIT_R; z++) {
                    if (!Belfry.lid(x, z) || (int) Math.floor(Belfry.pitR(x, z)) != ring) continue;
                    for (int dy = 0; dy >= -1; dy--) {
                        at.set(run.centre.getX() + x, run.centre.getY() + dy, run.centre.getZ() + z);
                        BlockState was = run.level.getBlockState(at);
                        if (was.isAir()) continue;
                        if (dy == 0 && ((x + z) & 1) == 0) run.level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, at, Block.getId(was));
                        run.level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        if (run.turnRow >= Belfry.SOUTH && ring > Belfry.PIT_R) run.turnAt = -1;
    }

    /** Everyone fell: the water comes back, the floor closes over the pit. */
    @Override
    void reset(Provings.Run run) {
        run.turns = 0;
        run.turnAt = -1;
        new Hall.Repair(hall, run.level, run.centre, false).finish();
    }

    @Override
    void outro(Provings.Run run, List<ServerPlayer> here, long t) {
        if (t == 50) chain(run, here);
        if (t == 90) run.level.playSound(null, run.centre.offset(0, Belfry.TENOR_LIP + 4, Belfry.PIT_Z), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4F, 0.5F);
    }

    /** Its chain is lying at your feet: the Tether, which nobody told you about. */
    private static void chain(Provings.Run run, List<ServerPlayer> here) {
        for (ServerPlayer m : here) {
            if (m.level() != run.level) continue;
            BlockPos feet = m.blockPosition();
            BlockState links = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, m.getDirection().getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            Direction across = m.getDirection().getClockWise();
            for (int k = -1; k <= 1; k++) {
                BlockPos p = feet.relative(m.getDirection(), 1).relative(across, k);
                if (run.level.getBlockState(p).isAir() && !run.level.getBlockState(p.below()).isAir()) run.level.setBlock(p, links, Block.UPDATE_CLIENTS);
            }
            run.level.playSound(null, feet, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2F, 0.7F);
            if (run.practice) {
                Signal.toast(m, Component.literal("The Ringer's chain is lying at your feet."));
            } else {
                Signal.toast(m, Component.literal("The Ringer's chain is lying at your feet. It's yours now."));
                TrialHooks.done(m, TrialHooks.RINGER, 0, 0);
            }
        }
    }

    /** The stair's top: climbed out. */
    @Override
    boolean out(Provings.Run run, ServerPlayer m) {
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ();
        return lz >= Belfry.LANDING_Z && Math.abs(lx) <= 3 && m.getY() >= run.centre.getY() + Belfry.LANDING_DY - 0.1;
    }

    @Override
    String firstFall() {
        return "You wake in the water by the door. The stair behind you climbs out.";
    }

    @Override
    String name() {
        return "The Ringer";
    }
}
