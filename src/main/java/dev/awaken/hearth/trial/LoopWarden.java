package dev.awaken.hearth.trial;

import dev.awaken.core.api.Promises;
import dev.awaken.core.api.StoryHooks;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The last Proving's Warden (SPEC §47.10): the Last Loop, for Sovereign. The bell rings for the last time, and you wake in
 * the Cradle's antechamber: a round white hall whose floor is a clock face, the Seed's door of rings shut in its north
 * wall. Walk into the Loop in the middle of the floor and the path you walked lights up behind you, and the Last Loop is
 * down on one knee where you stood three seconds before: you, as the Signal was. It stands up and comes for you. From
 * three quarters it replays the path you walked as a run of light (step off your own trail), and night sweeps round the
 * floor like a clock's hand; at half the outer ring of the floor falls away a mark at a time; from a quarter the Seed's
 * door turns and opens a ring at a time as it weakens. Its death opens the door, lays a path of light to it, and brings
 * the day back: the Seed is yours.
 */
final class LoopWarden extends Warden {
    static final LoopWarden WARDEN = new LoopWarden();
    static final String MOB = "awaken:proving_last_loop";
    /** Where everyone has walked: a sample every this many ticks, this many kept (four seconds). */
    static final int TRAIL_EVERY = 2, TRAIL = 40;
    /** It stands where you were this many samples ago (three seconds), once you walk into the Loop (or after a while). */
    static final int BACK = 30;
    /** Your path lights up for this long before it stands at its start. */
    static final int DRAW = 40;
    /** Its phases: night falls at three quarters, the loop closes at half, the Seed's door turns from a quarter. */
    static final float NIGHT_AT = 0.75F, CLOSE_AT = 0.5F, DOOR_AT = 0.25F;
    /** Night sweeps an hour of the clock every few ticks; the ring cracks, then falls an hour every few ticks. */
    static final int SWEEP_EVERY = 6, CRACK = 30, FALL_EVERY = 5;
    /** The door's rings turn an eighth every few ticks; its steps open: three in the last quarter, the fourth at its death. */
    static final int TURN_EVERY = 5, DOOR_STEPS = 4;
    /**
     * From night on, every so often it replays the path each of you walked in the last four seconds: the trail glows for
     * the tell, then a run of light goes along it from its far end to where you were when it set off. Anyone it passes
     * within reach takes a share of its blow (magic: not a blow to parry; step off your trail).
     */
    static final int REPLAY_FIRST = 100, REPLAY_EVERY = 300, REPLAY_TELL = 24, REPLAY_RUN = 20;
    static final double REPLAY_REACH = 1.4;
    /** A fall this soon after a replay caught you is put down to it. */
    static final int REPLAY_CAUGHT = 40;
    static final float REPLAY_SHARE = 0.8F;
    /** The hall is put back this many rows a tick after a reset. */
    static final int RESET_ROWS = 16;
    /** Under this you've gone over the edge: fallen. */
    static final int VOID = -10;
    /** How far from the middle it goes after you: the floor, the door and the foot of the arch. */
    static final int HOME = 30;

    private LoopWarden() {
        super(MOB, Rank.SOVEREIGN, Antechamber.HALL);
    }

    @Override
    String asked() {
        return "The bell has taken you five times now. This is the last time it will. Go when you're ready.";
    }

    @Override
    int ring(ServerPlayer m, boolean practice) {
        m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 0.5F);
        if (practice) return 40;
        dev.awaken.fault.Fault f = new dev.awaken.fault.Fault(dev.awaken.fault.Fault.Size.FAULT, "",
                List.of("MEASURING · MEASURING · MEASURING", "[ERROR] YOU ARE WHERE IT STARTED"), "",
                "The bell stopped.", null, null, 0, true);
        dev.awaken.fault.Faults.play(m, f);
        return f.length();
    }

    /** Where someone has walked: the last {@link #TRAIL} samples, in a ring. */
    static final class Trail {
        final double[] x = new double[TRAIL], y = new double[TRAIL], z = new double[TRAIL];
        int head, n;

        void add(Entity e) {
            x[head] = e.getX();
            y[head] = e.getY();
            z[head] = e.getZ();
            head = (head + 1) % TRAIL;
            n = Math.min(TRAIL, n + 1);
        }

        /** The i-th sample, oldest first. */
        Vec3 at(int i) {
            int k = Math.floorMod(head - n + i, TRAIL);
            return new Vec3(x[k], y[k], z[k]);
        }

        /** The last {@code count} samples (fewer if there aren't as many), oldest first. */
        List<Vec3> last(int count) {
            List<Vec3> out = new ArrayList<>();
            for (int i = Math.max(0, n - count); i < n; i++) out.add(at(i));
            return out;
        }
    }

    /** A path being replayed at someone: the tell, then the run of light along it. */
    static final class Replay {
        final UUID who;
        final long at;
        List<Vec3> path = List.of();
        final Set<UUID> hit = new HashSet<>();

        Replay(UUID who, long at) {
            this.who = who;
            this.at = at;
        }
    }

    /** What a run keeps of the antechamber: the trails, its coming, night, the ring, the door, the replays. */
    static final class Loop {
        final Map<UUID, Trail> trails = new HashMap<>();
        /** Its coming: when the path began to light, the path, where it stands and which way it faces. */
        long drawAt = -1;
        List<Vec3> drawn = List.of();
        Vec3 arrive = Vec3.ZERO;
        float arriveYaw;
        /** Night: when it began to sweep, hours swept; after its death the day coming back, hours given back. */
        long sweepAt = -1, dayAt = -1;
        int swept, dayed;
        /** The ring: when it cracked, hours fallen. */
        long crackAt = -1;
        int fallen;
        /** The Seed's door: steps open, its rings' turn, whether they're turning. */
        int door, turn;
        boolean turning;
        /** The replays: when the next is due, those running, when each of you was last caught by one. */
        long replayAt = -1;
        final List<Replay> replays = new ArrayList<>();
        final Map<UUID, Long> caught = new HashMap<>();
        Hall.@Nullable Repair reset;
    }

    @Override
    void before(Provings.Run run, ServerPlayer leader) {
        run.room = new Loop();
    }

    static Loop loop(Provings.Run run) {
        if (run.room instanceof Loop l) return l;
        Loop l = new Loop();
        run.room = l;
        return l;
    }

    /** At the south end of the floor, looking north to the Seed's door. */
    @Override
    BlockPos wakeAt(BlockPos centre) {
        return centre.offset(0, 1, Antechamber.WAKE_Z);
    }

    /** At the south end of the floor (a party side by side across it), looking north to the Seed's door. */
    @Override
    void wake(ServerPlayer m, Provings.Run run, boolean first) {
        int i = Math.max(0, List.copyOf(run.members).indexOf(m.getUUID())), row = Math.min(5, run.members.size());
        Vec3 at = new Vec3(run.centre.getX() + 0.5 + (i % 5 - (row - 1) / 2.0) * 1.2, run.centre.getY() + 1,
                run.centre.getZ() + Antechamber.WAKE_Z + 0.5 + (i / 5) * 1.5);
        m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, first ? 2F : 6F, TeleportTransition.DO_NOTHING));
        m.resetFallDistance();
        loop(run).trails.remove(m.getUUID());
        run.level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, first ? 0.6F : 0.75F);
        if (first) run.level.playSound(null, BlockPos.containing(at), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.4F, 0.6F);
    }

    // ---- before it comes ------------------------------------------------------------------------------------------------

    /**
     * Once someone walks into the Loop (or after a while), the path they walked lights up from where they were three
     * seconds before to where they stand; it'll stand at its start.
     */
    @Override
    void waiting(Provings.Run run, List<ServerPlayer> here, long now) {
        Loop l = loop(run);
        if (l.reset != null) return; // the hall still being put back
        if (l.drawAt < 0) {
            ServerPlayer who = null;
            for (ServerPlayer m : here) {
                if (m.level() != run.level || m.isSpectator()) continue;
                double dx = m.getX() - run.centre.getX() - 0.5, dz = m.getZ() - run.centre.getZ() - 0.5;
                if (Math.sqrt(dx * dx + dz * dz) < Antechamber.LOOP_R + 0.5 && m.getY() < run.centre.getY() + 4) who = m;
            }
            if (who == null && now - run.waitingSince >= Provings.COME_AFTER) {
                for (ServerPlayer m : here) if (m.level() == run.level && !m.isSpectator()) who = m;
            }
            if (who != null) draw(run, l, who, now);
            return;
        }
        // the path lighting up from its far end toward you, a footprint at a time; a light gathering at its start
        double k = Math.min(1, (now - l.drawAt) / (double) DRAW);
        int lit = (int) Math.ceil(k * l.drawn.size());
        if (now % 2 == 0) {
            for (int i = 0; i < lit; i++) {
                Vec3 p = l.drawn.get(i);
                run.level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.1, p.z, 1, 0.1, 0.02, 0.1, 0.0);
            }
        }
        run.level.sendParticles(ParticleTypes.END_ROD, l.arrive.x, l.arrive.y + 0.2 + k * 2, l.arrive.z, 2 + (int) (6 * k), 0.3 + k * 0.5, k, 0.3 + k * 0.5, 0.02);
        if ((now - l.drawAt) % 8 == 0) {
            run.level.playSound(null, BlockPos.containing(l.arrive), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2F, 0.6F + (float) k * 0.8F);
        }
    }

    /** Someone walked into the Loop: the path they walked is the way it comes. */
    private static void draw(Provings.Run run, Loop l, ServerPlayer who, long now) {
        Trail t = l.trails.get(who.getUUID());
        List<Vec3> path = t == null ? new ArrayList<>() : new ArrayList<>(t.last(BACK + 1));
        Vec3 here = who.position();
        Vec3 start = path.isEmpty() ? here : path.getFirst();
        if (start.distanceTo(here) < 4) {
            // hardly moved: it stands a little ahead of you instead
            Vec3 look = who.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, -1);
            start = here.add(flat.normalize().scale(6));
            path = new ArrayList<>(List.of(start, here));
        }
        l.drawn = path;
        l.arrive = new Vec3(start.x, run.centre.getY() + 1, start.z);
        l.arriveYaw = (float) Math.toDegrees(Math.atan2(-(here.x - start.x), here.z - start.z));
        l.drawAt = now;
        BlockPos at = BlockPos.containing(l.arrive);
        run.level.playSound(null, at, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5F, 0.6F);
        run.level.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2F, 0.5F);
    }

    @Override
    boolean comes(Provings.Run run, List<ServerPlayer> here, long now) {
        Loop l = loop(run);
        return l.drawAt >= 0 && now >= l.drawAt + DRAW;
    }

    @Override
    Vec3 arrive(Provings.Run run) {
        return loop(run).arrive;
    }

    @Override
    float arriveYaw(Provings.Run run) {
        return loop(run).arriveYaw;
    }

    @Override
    int home() {
        return HOME;
    }

    /** It's there, down on one knee where you stood: a burst of light, the bell, the glass ringing. */
    @Override
    void came(Provings.Run run, LivingEntity warden, List<ServerPlayer> here) {
        loop(run).drawAt = -1;
        double x = warden.getX(), y = warden.getY(), z = warden.getZ();
        run.level.sendParticles(ParticleTypes.END_ROD, x, y + 1.5, z, 70, 0.8, 1.5, 0.8, 0.12);
        run.level.sendParticles(ParticleTypes.FIREWORK, x, y + 1, z, 30, 0.6, 1, 0.6, 0.08);
        BlockPos at = warden.blockPosition();
        run.level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3F, 0.7F);
        run.level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3F, 0.5F);
        run.level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3F, 0.8F);
    }

    // ---- the hall turning ---------------------------------------------------------------------------------------------

    /** Night at three quarters (and the replays begin), the ring at half, the door from a quarter, a ring with each step. */
    @Override
    void fight(Provings.Run run, LivingEntity warden, long now) {
        Loop l = loop(run);
        float f = warden.getHealth() / warden.getMaxHealth();
        if (run.turns == 0 && f <= NIGHT_AT) {
            run.turns = 1;
            l.sweepAt = now;
            l.replayAt = now + REPLAY_FIRST;
            run.level.playSound(null, run.centre.above(6), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 4F, 0.5F);
            run.level.playSound(null, run.centre.above(6), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4F, 0.4F);
        }
        if (run.turns == 1 && f <= CLOSE_AT) {
            run.turns = 2;
            l.crackAt = now;
            run.level.playSound(null, run.centre.above(2), SoundEvents.CALCITE_BREAK, SoundSource.BLOCKS, 4F, 0.4F);
            run.level.playSound(null, run.centre.above(2), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4F, 0.4F);
        }
        if (run.turns == 2 && f <= DOOR_AT) {
            run.turns = 3;
            l.turning = true;
        }
        if (run.turns == 3) {
            int want = Math.max(1, Math.min(DOOR_STEPS - 1, 1 + (int) ((DOOR_AT - f) / DOOR_AT * (DOOR_STEPS - 1))));
            if (want > l.door) openTo(run, l, want);
        }
        if (l.replayAt >= 0 && now >= l.replayAt) {
            l.replayAt = now + REPLAY_EVERY;
            for (UUID u : run.members) {
                ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
                if (m != null && m.level() == run.level && !m.isSpectator() && m.isAlive()) l.replays.add(new Replay(u, now));
            }
            run.level.playSound(null, warden.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3F, 0.9F);
        }
    }

    @Override
    void step(Provings.Run run, long now) {
        Loop l = loop(run);
        if (l.reset != null && l.reset.step(RESET_ROWS)) l.reset = null;
        // everyone's path, a sample every other tick
        if (now % TRAIL_EVERY == 0 && run.stage != Provings.Stage.WON) {
            for (UUID u : run.members) {
                ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
                if (m == null || m.level() != run.level || m.isSpectator()) continue;
                l.trails.computeIfAbsent(u, k -> new Trail()).add(m);
            }
        }
        // night sweeping round like the clock's hand (only while it fights: a death mid-sweep stops it where it is);
        // after its death the day coming back the same way over the hours night took
        boolean fighting = run.stage == Provings.Stage.FIGHT;
        if (fighting && l.sweepAt >= 0 && l.swept < 12) {
            long k = now - l.sweepAt;
            hand(run, (l.swept + (k % SWEEP_EVERY) / (double) SWEEP_EVERY) * 30);
            if (k % SWEEP_EVERY == 0) hour(run, l.swept++, false);
        }
        if (l.dayAt >= 0 && l.dayed < l.swept) {
            long k = now - l.dayAt;
            if (k % SWEEP_EVERY == 0) hour(run, l.dayed++, true);
        }
        // the ring cracking, then falling an hour at a time from twelve round
        if (fighting && l.crackAt >= 0 && l.fallen < 12) {
            long k = now - l.crackAt;
            if (k < CRACK) {
                if (k % 5 == 0) crumble(run);
            } else if ((k - CRACK) % FALL_EVERY == 0) {
                fall(run, l.fallen++);
            }
        }
        // the door's rings turning
        if (l.turning && now % TURN_EVERY == 0) {
            l.turn++;
            render(run, l.door, l.turn);
        }
        if (l.door > 0 && now % 4 == 0) lightThrough(run, l);
        if (fighting) replay(run, l, now);
        else l.replays.clear();
        if (run.stage != Provings.Stage.OPENING) overTheEdge(run);
    }

    /** The clock's hand: a line of light from the middle out to the rim at {@code deg} round from twelve. */
    private static void hand(Provings.Run run, double deg) {
        double a = Math.toRadians(deg), ux = Math.sin(a), uz = -Math.cos(a);
        for (int k = 2; k <= 21; k += 2) {
            run.level.sendParticles(ParticleTypes.END_ROD, run.centre.getX() + 0.5 + ux * k, run.centre.getY() + 1.1, run.centre.getZ() + 0.5 + uz * k,
                    1, 0.05, 0.02, 0.05, 0.0);
        }
    }

    /** An hour of the floor goes to night (or back to day): its white, and the light over it. */
    private static void hour(Provings.Run run, int h, boolean day) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int[] p : Antechamber.hours()[h]) {
            at.set(run.centre.getX() + p[0], run.centre.getY(), run.centre.getZ() + p[1]);
            if (run.level.getBlockState(at).isAir()) continue; // fallen
            double r = Antechamber.r(p[0], p[1]);
            BlockState want = day ? Antechamber.face(p[0], p[1], r) : Antechamber.night(p[0], p[1], r);
            if (run.level.getBlockState(at) != want) run.level.setBlock(at, want, Antechamber.QUIET);
            at.setY(run.centre.getY() + 4);
            if (run.level.getBlockState(at).is(Blocks.LIGHT)) {
                run.level.setBlock(at, Antechamber.lightOf(day ? Antechamber.DAY_LIGHT : Antechamber.NIGHT_LIGHT), Antechamber.QUIET);
            }
        }
        double a = Math.toRadians(h * 30 + 15);
        BlockPos mark = BlockPos.containing(run.centre.getX() + 0.5 + Math.sin(a) * 19, run.centre.getY() + 1, run.centre.getZ() + 0.5 - Math.cos(a) * 19);
        run.level.playSound(null, mark, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 3F, day ? 0.5F + h * 0.06F : 1.2F - h * 0.06F);
        if (h == 11) run.level.playSound(null, run.centre.above(4), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4F, day ? 0.7F : 0.4F);
    }

    /** While the ring cracks: dust off its seam, the stone ticking. */
    private static void crumble(Provings.Run run) {
        RandomSource rnd = run.level.getRandom();
        for (int k = 0; k < 6; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Antechamber.RING_IN + rnd.nextDouble() * 5;
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), run.centre.getX() + 0.5 + Math.sin(a) * rr,
                    run.centre.getY() + 1.05, run.centre.getZ() + 0.5 - Math.cos(a) * rr, 6, 0.6, 0.05, 0.6, 0.05);
        }
        run.level.playSound(null, run.centre.offset(0, 1, (int) Antechamber.RING_IN + 2), SoundEvents.CALCITE_BREAK, SoundSource.BLOCKS, 2.5F, 0.5F + rnd.nextFloat() * 0.2F);
    }

    /** An hour of the outer ring falls away into the dark, a few of its stones falling as they go. */
    private static void fall(Provings.Run run, int h) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        RandomSource rnd = run.level.getRandom();
        for (int[] p : Antechamber.ring()[h]) {
            at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + p[2]);
            BlockState s = run.level.getBlockState(at);
            if (s.isAir()) continue;
            if (p[1] == 0 && rnd.nextInt(7) == 0) {
                FallingBlockEntity fb = FallingBlockEntity.fall(run.level, at.immutable(), s);
                fb.dropItem = false;
            } else {
                run.level.setBlock(at, Blocks.AIR.defaultBlockState(), Antechamber.QUIET);
            }
        }
        double a = Math.toRadians(h * 30 + 15);
        double x = run.centre.getX() + 0.5 + Math.sin(a) * 19, z = run.centre.getZ() + 0.5 - Math.cos(a) * 19;
        run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SMOOTH_QUARTZ.defaultBlockState()), x, run.centre.getY() + 0.5, z,
                30, 2.5, 0.8, 2.5, 0.1);
        run.level.sendParticles(ParticleTypes.END_ROD, x, run.centre.getY(), z, 12, 2.5, 0.3, 2.5, 0.05);
        BlockPos p = BlockPos.containing(x, run.centre.getY(), z);
        run.level.playSound(null, p, SoundEvents.CALCITE_BREAK, SoundSource.BLOCKS, 4F, 0.45F);
        run.level.playSound(null, p, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2F, 0.5F);
    }

    /** The Seed's door opens to {@code s} steps: its core and rings gone from the middle out, the light behind coming through. */
    private static void openTo(Provings.Run run, Loop l, int s) {
        l.door = s;
        render(run, s, l.turn);
        BlockPos door = run.centre.offset(0, Antechamber.DOOR_DY, Antechamber.DOOR_Z);
        run.level.playSound(null, door, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3F, 0.5F + s * 0.1F);
        run.level.playSound(null, door, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3F, 0.45F + s * 0.08F);
        run.level.sendParticles(ParticleTypes.END_ROD, door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 1.0, 30 * s, 1.5 * s, 1.5 * s, 0.3, 0.06);
    }

    /** The door's leaves as they stand: {@code open} parts gone, the rings turned {@code turn} eighths. */
    private static void render(Provings.Run run, int open, int turn) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int[] p : Antechamber.door()) {
            BlockState want = Antechamber.doorLeaf(p[0], p[1], open, turn);
            if (want == null) want = Blocks.AIR.defaultBlockState();
            at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + Antechamber.DOOR_Z);
            if (run.level.getBlockState(at) != want) run.level.setBlock(at, want, Antechamber.QUIET);
        }
    }

    /** Light coming through the Seed's door into the hall. */
    private static void lightThrough(Provings.Run run, Loop l) {
        double w = Antechamber.DOOR_RINGS[Math.min(3, l.door - 1)] * 0.6;
        run.level.sendParticles(ParticleTypes.END_ROD, run.centre.getX() + 0.5, run.centre.getY() + Antechamber.DOOR_DY + 0.5,
                run.centre.getZ() + Antechamber.DOOR_Z + 3, 3 * l.door, w, w, 2.0, 0.01);
    }

    /** The replays running: each one's trail glowing, then the run of light along it. */
    private static void replay(Provings.Run run, Loop l, long now) {
        LivingEntity warden = Provings.warden(run);
        if (warden == null) {
            l.replays.clear();
            return;
        }
        for (var it = l.replays.iterator(); it.hasNext(); ) {
            Replay r = it.next();
            long k = now - r.at;
            Trail t = l.trails.get(r.who);
            if (t == null || k > REPLAY_TELL + REPLAY_RUN) {
                it.remove();
                continue;
            }
            if (k < REPLAY_TELL) {
                // your trail glowing where you walked
                if (k % 3 == 0) {
                    for (Vec3 p : t.last(TRAIL)) run.level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.15, p.z, 1, 0.08, 0.02, 0.08, 0.0);
                }
                if (k == 0) {
                    ServerPlayer who = run.level.getServer().getPlayerList().getPlayer(r.who);
                    if (who != null) run.level.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.5F, 1.2F);
                }
                continue;
            }
            if (k == REPLAY_TELL) r.path = t.last(TRAIL); // the path as it stands when the run sets off: it ends where you were
            if (r.path.isEmpty()) continue;
            int i = (int) Math.min(r.path.size() - 1, (k - REPLAY_TELL) * r.path.size() / REPLAY_RUN);
            Vec3 p = r.path.get(i);
            run.level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.5, p.z, 8, 0.25, 0.4, 0.25, 0.05);
            run.level.sendParticles(ParticleTypes.FIREWORK, p.x, p.y + 0.5, p.z, 2, 0.2, 0.3, 0.2, 0.02);
            if ((k - REPLAY_TELL) % 4 == 0) {
                run.level.playSound(null, BlockPos.containing(p), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2F, 0.8F + (k - REPLAY_TELL) / (float) REPLAY_RUN);
            }
            float blow = (float) (warden.getAttributeValue(Attributes.ATTACK_DAMAGE) * REPLAY_SHARE);
            for (UUID u : run.members) {
                if (r.hit.contains(u)) continue;
                ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
                if (m == null || m.level() != run.level || m.isSpectator() || !m.isAlive()) continue;
                double dx = m.getX() - p.x, dz = m.getZ() - p.z;
                if (dx * dx + dz * dz > REPLAY_REACH * REPLAY_REACH || Math.abs(m.getY() - p.y) > 2) continue;
                r.hit.add(u);
                l.caught.put(u, now);
                m.hurtServer(run.level, run.level.damageSources().indirectMagic(warden, warden), blow);
            }
        }
    }

    /** Whoever goes over the edge where the ring fell has fallen; if it goes over, it loops back to where it stood. */
    private static void overTheEdge(Provings.Run run) {
        for (UUID u : List.copyOf(run.members)) {
            ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
            if (m == null || m.level() != run.level || m.isSpectator() || m.getY() >= run.centre.getY() + VOID) continue;
            Trials.Session s = Trials.SESSIONS.get(u);
            if (s != null) Provings.fell(m, s, run.level.damageSources().fellOutOfWorld());
        }
        LivingEntity w = Provings.warden(run);
        if (w != null && w.getY() < run.centre.getY() - 2) {
            double lx = w.getX() - run.centre.getX() - 0.5, lz = w.getZ() - run.centre.getZ() - 0.5;
            double r = Math.max(1, Math.sqrt(lx * lx + lz * lz)), in = Math.min(r, Antechamber.RING_IN - 4);
            run.level.sendParticles(ParticleTypes.END_ROD, w.getX(), w.getY() + 1.5, w.getZ(), 30, 0.6, 1.2, 0.6, 0.05);
            w.teleportTo(run.centre.getX() + 0.5 + lx / r * in, run.centre.getY() + 1, run.centre.getZ() + 0.5 + lz / r * in);
            w.setDeltaMovement(Vec3.ZERO);
            w.resetFallDistance();
            run.level.sendParticles(ParticleTypes.END_ROD, w.getX(), w.getY() + 1.5, w.getZ(), 50, 0.6, 1.2, 0.6, 0.1);
            run.level.playSound(null, w.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3F, 0.7F);
        }
    }

    /** Everyone fell: day, the ring and the door are put back (quickly), and it can come again. */
    @Override
    void reset(Provings.Run run) {
        Loop l = loop(run);
        l.reset = new Hall.Repair(Antechamber.HALL, run.level, run.centre, false);
        l.drawAt = -1;
        l.sweepAt = -1;
        l.swept = 0;
        l.dayAt = -1;
        l.dayed = 0;
        l.crackAt = -1;
        l.fallen = 0;
        l.door = 0;
        l.turn = 0;
        l.turning = false;
        l.replayAt = -1;
        l.replays.clear();
        l.caught.clear();
        l.trails.clear();
        run.turns = 0;
        run.turnAt = -1;
    }

    @Override
    void died(Provings.Run run, long now) {
        Entity w = run.boss != null ? run.level.getEntity(run.boss) : null;
        BlockPos at = w != null ? w.blockPosition() : run.centre.above();
        if (w != null) run.level.sendParticles(ParticleTypes.END_ROD, w.getX(), w.getY() + 1.5, w.getZ(), 80, 0.8, 1.5, 0.8, 0.1);
        run.level.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3F, 0.6F);
        run.level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3F, 0.5F);
        run.level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3F, 0.35F);
        Loop l = loop(run);
        l.replays.clear();
        l.replayAt = -1;
    }

    /**
     * After its death: the Seed's door opens the rest of the way and a path of light runs to it from the middle; the day
     * comes back round the clock; the Seed is set before you (the rank, given at the end, opens it).
     */
    @Override
    void outro(Provings.Run run, List<ServerPlayer> here, long t) {
        Loop l = loop(run);
        if (t == 10) {
            l.turning = false;
            openTo(run, l, DOOR_STEPS);
            path(run);
            BlockPos door = run.centre.offset(0, Antechamber.DOOR_DY, Antechamber.DOOR_Z);
            run.level.playSound(null, door, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4F, 0.5F);
            run.level.playSound(null, door, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 2F, 1.2F);
        }
        if (t == 20 && l.swept > 0) l.dayAt = run.level.getGameTime();
        if (t == 40 && !run.practice) {
            for (ServerPlayer m : here) {
                // the same lock the Descent's sealed way down makes; the rank given at the end answers it (while the Court
                // still holds the Seed shut, its own seal says so: nothing is promised here)
                if (m.level() == run.level && StoryHooks.seedSealed(m).isEmpty()) {
                    Promises.seen(m, "stratum:10", null, Promises.Need.rank(Rank.SOVEREIGN.label), "The Seed, in the Descent");
                }
            }
        }
    }

    /** A path of light down the middle of the floor from the boss to the door. */
    private static void path(Provings.Run run) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int z = -2; z > Antechamber.DOOR_Z; z--) {
            at.set(run.centre.getX(), run.centre.getY(), run.centre.getZ() + z);
            BlockState s = run.level.getBlockState(at);
            if (!s.isAir() && !s.is(Blocks.GOLD_BLOCK)) run.level.setBlock(at, Antechamber.PATH, Antechamber.QUIET);
            run.level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.02);
        }
    }

    /** Back out through the south arch; or, once it's open, through the Seed's door to the light. */
    @Override
    boolean out(Provings.Run run, ServerPlayer m) {
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ();
        if (lz >= Antechamber.OUT_Z && Math.abs(lx) <= 2.5 && m.getY() >= run.centre.getY() + 0.5) return true;
        return loop(run).door >= DOOR_STEPS && lz <= Antechamber.OUT_N && Math.abs(lx) <= 4.5;
    }

    /** Fallen just after your own path's light ran you down: say so, not its last blow. */
    @Override
    @Nullable String caught(Provings.Run run, ServerPlayer m) {
        Long at = loop(run).caught.get(m.getUUID());
        if (at == null || run.level.getGameTime() - at > REPLAY_CAUGHT) return null;
        return "Your own path keeps catching you. When the floor glows along where you've just walked, step off that line:"
                + " a run of light follows it from one end to the other, and nothing turns it aside.";
    }

    @Override
    String firstFall() {
        return "You wake by the arch you came in through. The way out is behind you.";
    }

    @Override
    String name() {
        return "The Last Loop";
    }
}
