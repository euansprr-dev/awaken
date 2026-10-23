package dev.awaken.hearth.training;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.block.CheckpointRingBlockEntity;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Training Grounds: build a course from a Start Gate, Checkpoint Rings (passed in the order you placed them), a Finish
 * Gate and Target Dummies. Runs are timed to the tick; your best lap is kept per course and its ghost runs beside you.
 */
public final class Training {
    private Training() {}

    public enum Piece { START, RING, FINISH, DUMMY }

    public static final int LINK_RANGE = 64;
    public static final int MAX_TICKS = 20 * 60 * 10;
    public static final int MAX_GHOST_TICKS = 20 * 60 * 5;
    public static final String LAPS = "awaken:training_laps";
    public static final String RECORDS = "awaken:training_records";

    private static final class Run {
        final String course;
        long start;
        int nextRing;
        boolean ghostSpawned;
        final Set<BlockPos> dummiesHit = new HashSet<>();
        float[] path = new float[400];
        int samples;
        @Nullable UUID ghost;

        Run(String course, long start) {
            this.course = course;
            this.start = start;
        }

        void sample(ServerPlayer sp) {
            if (samples >= MAX_GHOST_TICKS) return;
            int i = samples * 4;
            if (i + 4 > path.length) path = java.util.Arrays.copyOf(path, path.length * 2);
            path[i] = (float) sp.getX();
            path[i + 1] = (float) sp.getY();
            path[i + 2] = (float) sp.getZ();
            path[i + 3] = sp.getYRot();
            samples++;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Training::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> cancel(e.getEntity().getUUID(), null));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) cancel(sp.getUUID(), sp);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> RUNS.clear());
    }

    static String dim(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    // ---- building courses -------------------------------------------------------------------------------------------

    public static void pieceAdded(ServerLevel level, BlockPos pos, Piece piece) {
        TrainingData d = TrainingData.get(level.getServer());
        String dim = dim(level);
        if (piece == Piece.START) {
            d.courses.putIfAbsent(TrainingData.Course.key(dim, pos), new TrainingData.Course(dim, pos.immutable()));
            d.setDirty();
            return;
        }
        TrainingData.Course c = nearestCourse(d, dim, pos);
        if (c == null) {
            level.players().stream().filter(p -> p.blockPosition().closerThan(pos, 8)).findFirst().ifPresent(p ->
                    p.sendOverlayMessage(Component.literal("Place a Start Gate within " + LINK_RANGE + " blocks first.")));
            return;
        }
        switch (piece) {
            case RING -> {
                c.rings.add(pos.immutable());
                if (level.getBlockEntity(pos) instanceof CheckpointRingBlockEntity be) be.setIndex(c.rings.size());
            }
            case FINISH -> c.finish = pos.immutable();
            case DUMMY -> c.dummies.add(pos.immutable());
            default -> {}
        }
        d.setDirty();
    }

    public static void pieceRemoved(ServerLevel level, BlockPos pos) {
        TrainingData d = TrainingData.get(level.getServer());
        String dim = dim(level);
        TrainingData.Course gone = d.courses.remove(TrainingData.Course.key(dim, pos));
        if (gone != null) {
            for (BlockPos r : gone.rings) if (level.getBlockEntity(r) instanceof CheckpointRingBlockEntity be) be.setIndex(0);
            d.setDirty();
            return;
        }
        for (TrainingData.Course c : d.courses.values()) {
            if (!c.dimension.equals(dim)) continue;
            boolean changed = c.dummies.remove(pos);
            if (pos.equals(c.finish)) {
                c.finish = null;
                changed = true;
            }
            if (c.rings.remove(pos)) {
                changed = true;
                for (int i = 0; i < c.rings.size(); i++) {
                    if (level.getBlockEntity(c.rings.get(i)) instanceof CheckpointRingBlockEntity be) be.setIndex(i + 1);
                }
            }
            if (changed) d.setDirty();
        }
    }

    static TrainingData.@Nullable Course nearestCourse(TrainingData d, String dim, BlockPos pos) {
        TrainingData.Course best = null;
        double bestD = (double) LINK_RANGE * LINK_RANGE;
        for (TrainingData.Course c : d.courses.values()) {
            if (!c.dimension.equals(dim)) continue;
            double dd = c.start.distSqr(pos);
            if (dd <= bestD) {
                bestD = dd;
                best = c;
            }
        }
        return best;
    }

    static TrainingData.@Nullable Course courseOf(TrainingData d, String dim, BlockPos piece) {
        for (TrainingData.Course c : d.courses.values()) {
            if (!c.dimension.equals(dim)) continue;
            if (c.start.equals(piece) || piece.equals(c.finish) || c.rings.contains(piece) || c.dummies.contains(piece)) return c;
        }
        return null;
    }

    public static void describe(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        TrainingData d = TrainingData.get(level.getServer());
        TrainingData.Course c = courseOf(d, dim(level), pos);
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Training Grounds"));
        if (c == null) {
            w.body(Component.literal("Not part of a course. Place a Start Gate, then rings in the order to run them, then a Finish Gate. Target Dummies make it a combat drill."));
        } else {
            w.row(Component.literal("Rings"), Component.literal(String.valueOf(c.rings.size())));
            w.row(Component.literal("Dummies"), Component.literal(String.valueOf(c.dummies.size())));
            w.row(Component.literal("Finish"), Component.literal(c.finish != null ? "yes" : c.dummies.isEmpty() ? "missing" : "last dummy"));
            Integer best = HearthService.player(sp).bests.get(c.key());
            w.row(Component.literal("Your best"), Component.literal(best == null ? "none yet" : formatTime(best)));
            w.footer(Component.literal(c.runnable() ? "Step on the Start Gate to run" : "Add a Finish Gate or a Target Dummy"));
        }
        Signal.send(sp, w.ttl(20 * 8).build());
    }

    // ---- running ----------------------------------------------------------------------------------------------------

    public static void onStartGate(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        TrainingData d = TrainingData.get(level.getServer());
        TrainingData.Course c = d.courses.get(TrainingData.Course.key(dim(level), pos));
        if (c == null) return;
        long now = level.getGameTime();
        Run cur = RUNS.get(sp.getUUID());
        if (cur != null && cur.course.equals(c.key()) && cur.nextRing == 0 && cur.dummiesHit.isEmpty()) {
            // Still on (or back on) the start with no progress: hold the clock at zero until they step off.
            cur.start = now;
            cur.samples = 0;
            return;
        }
        if (!c.runnable()) {
            if (now % 20 == 0) sp.sendOverlayMessage(Component.literal("This course needs a Finish Gate or a Target Dummy."));
            return;
        }
        cancel(sp.getUUID(), sp);
        Run run = new Run(c.key(), now);
        RUNS.put(sp.getUUID(), run);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.4f);
        Integer best = HearthService.player(sp).bests.get(c.key());
        String goal = (c.rings.isEmpty() ? "" : c.rings.size() + " rings") + (c.dummies.isEmpty() ? "" : (c.rings.isEmpty() ? "" : " · ") + c.dummies.size() + " dummies");
        sp.sendOverlayMessage(Component.literal("GO · " + (goal.isEmpty() ? "to the finish" : goal) + (best != null ? " · best " + formatTime(best) : ""))
                .withStyle(ChatFormatting.GREEN));
    }

    /** The ghost of the best lap sets off the moment the runner leaves the start. */
    private static void spawnGhost(ServerPlayer sp, ServerLevel level, Run run) {
        run.ghostSpawned = true;
        TrainingData d = TrainingData.get(level.getServer());
        int[] ghost = d.ghosts.get(sp.getUUID() + "|" + run.course);
        Integer best = HearthService.player(sp).bests.get(run.course);
        if (ghost == null || ghost.length < 8 || best == null) return;
        TrainingGhost g = HearthContent.TRAINING_GHOST.get().create(level, EntitySpawnReason.TRIGGERED);
        if (g == null) return;
        float[] f = new float[ghost.length];
        for (int i = 0; i < ghost.length; i++) f[i] = Float.intBitsToFloat(ghost[i]);
        g.snapTo(f[0], f[1], f[2], f[3], 0f);
        g.play(f);
        g.setCustomName(Component.literal("Best · " + formatTime(best)));
        level.addFreshEntity(g);
        run.ghost = g.getUUID();
    }

    public static void onCheckpoint(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        Run run = RUNS.get(sp.getUUID());
        if (run == null) return;
        TrainingData.Course c = TrainingData.get(level.getServer()).courses.get(run.course);
        if (c == null || !c.rings.contains(pos)) return;
        int idx = c.rings.indexOf(pos);
        if (idx < run.nextRing) return;
        if (idx > run.nextRing) {
            sp.sendOverlayMessage(Component.literal("Missed ring " + (run.nextRing + 1)).withStyle(ChatFormatting.RED));
            return;
        }
        run.nextRing++;
        long t = level.getGameTime() - run.start;
        level.playSound(null, pos, HearthContent.SND_CHECKPOINT.get(), SoundSource.PLAYERS, 1f, 0.8f + 0.1f * Math.min(8, run.nextRing));
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
        sp.sendOverlayMessage(Component.literal("Ring " + run.nextRing + "/" + c.rings.size() + " · " + formatTime((int) t)).withStyle(ChatFormatting.AQUA));
        if (c.finish == null && run.nextRing == c.rings.size() && run.dummiesHit.size() == c.dummies.size()) finish(sp, level, run, c);
    }

    public static void onDummyHit(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.2);
        Run run = RUNS.get(sp.getUUID());
        if (run == null) return;
        TrainingData.Course c = TrainingData.get(level.getServer()).courses.get(run.course);
        if (c == null || !c.dummies.contains(pos) || !run.dummiesHit.add(pos.immutable())) return;
        level.playSound(null, pos, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8f, 1.2f);
        long t = level.getGameTime() - run.start;
        sp.sendOverlayMessage(Component.literal("Dummy " + run.dummiesHit.size() + "/" + c.dummies.size() + " · " + formatTime((int) t)).withStyle(ChatFormatting.GOLD));
        if (c.finish == null && run.dummiesHit.size() == c.dummies.size() && run.nextRing == c.rings.size()) finish(sp, level, run, c);
    }

    public static void onFinishGate(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        Run run = RUNS.get(sp.getUUID());
        if (run == null) return;
        TrainingData.Course c = TrainingData.get(level.getServer()).courses.get(run.course);
        if (c == null || !pos.equals(c.finish)) return;
        if (run.nextRing < c.rings.size() || run.dummiesHit.size() < c.dummies.size()) {
            if (level.getGameTime() % 20 == 0) {
                sp.sendOverlayMessage(Component.literal("Not yet: " + (c.rings.size() - run.nextRing) + " rings, "
                        + (c.dummies.size() - run.dummiesHit.size()) + " dummies left").withStyle(ChatFormatting.RED));
            }
            return;
        }
        finish(sp, level, run, c);
    }

    /** Result of a finished lap compared with the old best (pure, tested). */
    public record LapResult(boolean first, boolean record, int best, int delta) {}

    public static LapResult compare(@Nullable Integer oldBest, int ticks) {
        if (oldBest == null) return new LapResult(true, false, ticks, 0);
        if (ticks < oldBest) return new LapResult(false, true, ticks, ticks - oldBest);
        return new LapResult(false, false, oldBest, ticks - oldBest);
    }

    private static void finish(ServerPlayer sp, ServerLevel level, Run run, TrainingData.Course c) {
        RUNS.remove(sp.getUUID());
        int ticks = (int) (level.getGameTime() - run.start);
        HearthPlayer hp = HearthService.player(sp);
        LapResult r = compare(hp.bests.get(c.key()), ticks);
        Progression.count(sp, LAPS);
        TrainingData d = TrainingData.get(level.getServer());
        if (r.first() || r.record()) {
            hp.bests.put(c.key(), ticks);
            int[] bits = new int[run.samples * 4];
            for (int i = 0; i < bits.length; i++) bits[i] = Float.floatToIntBits(run.path[i]);
            d.ghosts.put(sp.getUUID() + "|" + c.key(), bits);
            d.setDirty();
        }
        if (r.record()) {
            Progression.count(sp, RECORDS);
            Progression.markNewThing(sp, "training_record");
            level.playSound(null, sp.blockPosition(), HearthContent.SND_RECORD.get(), SoundSource.PLAYERS, 1f, 1f);
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("New record"))
                    .row(Component.literal("Lap"), Component.literal(formatTime(ticks)))
                    .row(Component.literal("Faster by"), Component.literal(formatTime(-r.delta())))
                    .footer(Component.literal("Your ghost runs this line now")).priority(3).build());
        } else if (r.first()) {
            level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("First lap"))
                    .row(Component.literal("Time"), Component.literal(formatTime(ticks)))
                    .footer(Component.literal("Run it again to race your ghost")).build());
        } else {
            level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1f);
            sp.sendOverlayMessage(Component.literal("Lap " + formatTime(ticks) + " · +" + formatTime(r.delta()) + " off your best").withStyle(ChatFormatting.YELLOW));
        }
        HearthViews.refresh(sp);
    }

    static void cancel(UUID player, @Nullable ServerPlayer sp) {
        Run run = RUNS.remove(player);
        if (run == null || run.ghost == null || sp == null) return;
        if (sp.level().getEntity(run.ghost) instanceof TrainingGhost g) g.discard();
    }

    private static void onTick(ServerTickEvent.Post e) {
        if (RUNS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        for (Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
            Run run = entry.getValue();
            if (sp == null) {
                it.remove();
                continue;
            }
            TrainingData.Course c = TrainingData.get(server).courses.get(run.course);
            long t = sp.level().getGameTime() - run.start;
            if (c == null || t > MAX_TICKS || !dim(sp.level()).equals(c.dimension) || sp.blockPosition().distSqr(c.start) > 160 * 160) {
                it.remove();
                if (run.ghost != null && sp.level().getEntity(run.ghost) instanceof TrainingGhost g) g.discard();
                sp.sendOverlayMessage(Component.literal("Run abandoned"));
                continue;
            }
            if (!run.ghostSpawned && t > 0 && !sp.blockPosition().equals(c.start)) spawnGhost(sp, sp.level(), run);
            run.sample(sp);
        }
    }

    public static boolean running(ServerPlayer sp) {
        return RUNS.containsKey(sp.getUUID());
    }

    /** m:ss.cc from ticks. */
    public static String formatTime(int ticks) {
        int t = Math.abs(ticks);
        int cs = (t % 20) * 5;
        int s = t / 20;
        return String.format("%d:%02d.%02d", s / 60, s % 60, cs);
    }

    static Optional<TrainingData.Course> course(MinecraftServer server, String key) {
        return Optional.ofNullable(TrainingData.get(server).courses.get(key));
    }
}
