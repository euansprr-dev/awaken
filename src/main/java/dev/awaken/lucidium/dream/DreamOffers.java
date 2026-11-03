package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.BellHooks;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.LucidiumWorld;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.data.Vision;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.trial.Course;
import dev.awaken.lucidium.trial.Trial;
import dev.awaken.lucidium.trial.TrialCompiler;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which dreams wait for whom (SPEC §9.2), tonight's plan from the first of them, the bed's pearl motes while one waits,
 * and the Tasks line. A dream waits for a reason the player can see coming: the level road's "Lucid dreams" step brings
 * the first (the Dreaming Bell's); then, one new dream a play-day, the next dream bell your rank has reached (in its
 * Room, or the Chase), each move family the body can do (its Run), a place worth finding (a Room hiding it), a fear that
 * killed you twice this week (the Chase), and your mentor every few nights once you have a Path (the Duel).
 */
public final class DreamOffers {
    private DreamOffers() {}

    /** The level road's flag (level 12) that brings the first dream. */
    static final String FLAG = "awaken:dreams";
    /** The trials with a course built so far, in the order they come to wait. */
    static final Set<Trial> BUILT = Set.of(Trial.FIRST, Trial.WALL, Trial.LEDGE, Trial.VAULT, Trial.CLIMB,
            Trial.WRONG, Trial.LOOK_AWAY, Trial.HUSH, Trial.LONG_FALL, Trial.FOLD, Trial.NO_DOOR, Trial.CHASE, Trial.DUEL);
    /** The Rooms a hidden place comes in, in turn (SPEC §9.5). */
    static final List<Trial> PLACE_ROOMS = List.of(Trial.FOLD, Trial.NO_DOOR, Trial.LOOK_AWAY, Trial.HUSH, Trial.WRONG);
    /** The Chase waits once one kind of death came this often this week; the Duel this many play-days after the last. */
    static final int FEAR_DEATHS = 2, DUEL_EVERY = 3;
    static final String BELL = "bell:", VISION = "vision:", FEAR = "fear:";
    /** A trial dream's runway: the wait for H, the longest clock with every second added, and the prize. */
    static final int DURATION = Trials.WAIT_MAX + 20 * 60 * 6;
    static final int EVERY = 100;

    private static final Map<UUID, Optional<BlockPos>> SENT = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> PENDING = new HashMap<>();

    // ---- what waits ---------------------------------------------------------------------------------------------------

    /**
     * Puts on the list whatever has come to wait. Old worlds are seeded once: the first dream, and up to two Runs
     * your body can already do (at most {@link DreamLedger#SEED_MAX} in all). Returns whether the list changed.
     */
    public static boolean check(ServerPlayer sp) {
        if (!enabled()) return false;
        Dreamer d = Dreams.dreamer(sp);
        DreamLedger l = d.ledger;
        long day = PlayDays.current(sp.level().getServer());
        if (!Progression.hasFlag(sp, FLAG)) return false;
        boolean changed = false;
        if (!l.seeded) {
            l.seeded = true;
            // someone who dreamed before this version: the new dream, and Runs they can do now, without waiting a day each
            if (d.dreams > 0) {
                changed |= offerFirst(sp, l, day, "first since the dreams changed");
                int runs = 0;
                for (Trial t : Trial.RUNS) {
                    if (runs >= DreamLedger.SEED_MAX - 1) break;
                    if (eligible(sp, l, t) && l.offer(t.id, day, "seeded: your body can do it", "", true)) runs++;
                }
                changed |= runs > 0;
            }
        }
        if (!l.finished(Trial.FIRST.id) && !l.isWaiting(Trial.FIRST.id)) changed |= offerFirst(sp, l, day, "the level road");
        if (l.finished(Trial.FIRST.id)) {
            changed |= dropStale(sp, d, l, day);
            // one new dream a play-day (the ledger keeps to it): a bell first, then a move, a place, a fear, the mentor
            changed |= offerBell(sp, l, day) || offerRun(sp, l, day) || offerPlace(d, l, day) || offerChase(sp, d, l, day) || offerDuel(sp, l, day);
        }
        if (changed) changed(sp);
        return changed;
    }

    /** The six dream bells' dreams: the Dreaming Bell's is the first Run; the rest each a Room's rule, or the Chase. */
    static @org.jspecify.annotations.Nullable Trial bellTrial(String bell) {
        return switch (bell) {
            case "dreaming" -> Trial.FIRST;
            case "daydream" -> Trial.LOOK_AWAY;
            case "lullaby" -> Trial.HUSH;
            case "falling" -> Trial.LONG_FALL;
            case "nightmare" -> Trial.CHASE;
            case "lucid" -> Trial.WRONG;
            default -> null;
        };
    }

    /** The next dream bell your rank has reached, in its dream (one bell waits at a time). */
    static boolean offerBell(ServerPlayer sp, DreamLedger l, long day) {
        String bell = BellHooks.dreamBellNext(sp);
        Trial t = bellTrial(bell);
        if (t == null || t == Trial.FIRST || l.given.contains(BELL + bell) || l.isWaiting(t.id)) return false;
        for (DreamLedger.Waiting w : l.waiting) if (w.ref().startsWith(BELL)) return false;
        return l.offer(t.id, day, "a bell", BELL + bell, false);
    }

    /** The next Run your body can do, in order. */
    static boolean offerRun(ServerPlayer sp, DreamLedger l, long day) {
        for (Trial t : Trial.RUNS) if (eligible(sp, l, t)) return l.offer(t.id, day, "your body can do it", "", false);
        return false;
    }

    /** Tonight's place worth finding (the visions finder's), hidden in the next Room in turn. */
    static boolean offerPlace(Dreamer d, DreamLedger l, long day) {
        Optional<Vision> v = d.pendingVision();
        if (v.isEmpty()) return false;
        for (DreamLedger.Waiting w : l.waiting) if (w.ref().startsWith(VISION)) return false;
        int shown = 0;
        for (String g : l.given) if (g.startsWith(VISION)) shown++;
        for (int i = 0; i < PLACE_ROOMS.size(); i++) {
            Trial t = PLACE_ROOMS.get((shown + i) % PLACE_ROOMS.size());
            if (!l.isWaiting(t.id)) return l.offer(t.id, day, "a hidden place", VISION + v.get().id(), false);
        }
        return false;
    }

    /** The fear that has killed you most this week, once it has twice. */
    static boolean offerChase(ServerPlayer sp, Dreamer d, DreamLedger l, long day) {
        if (l.isWaiting(Trial.CHASE.id)) return false;
        Map<Nightmares.Family, Integer> scores = Nightmares.scores(Map.copyOf(Progression.profile(sp).counters()), d.weeklyMarks(day));
        Optional<Nightmares.Family> top = Nightmares.top(scores);
        if (top.isEmpty() || scores.get(top.get()) < FEAR_DEATHS) return false;
        return l.offer(Trial.CHASE.id, day, "a fear", FEAR + top.get().id, false);
    }

    /** Your Path's mentor, every few nights. */
    static boolean offerDuel(ServerPlayer sp, DreamLedger l, long day) {
        if (Progression.profile(sp).path().isEmpty() || l.isWaiting(Trial.DUEL.id)) return false;
        DreamLedger.Finished f = l.finished.get(Trial.DUEL.id);
        if (f != null && day - f.day() < DUEL_EVERY) return false;
        return l.offer(Trial.DUEL.id, day, "your mentor", "", false);
    }

    /** Dreams that no longer hold what they waited for: a bell you hold now, a place already shown or gone, a fear let go. */
    static boolean dropStale(ServerPlayer sp, Dreamer d, DreamLedger l, long day) {
        boolean changed = false;
        String nextBell = BellHooks.dreamBellNext(sp);
        for (DreamLedger.Waiting w : List.copyOf(l.waiting)) {
            String ref = w.ref();
            boolean stale = false;
            if (ref.startsWith(BELL)) stale = !w.trial().equals(Trial.FIRST.id) && !ref.equals(BELL + nextBell);
            else if (ref.startsWith(VISION)) stale = d.vision(ref.substring(VISION.length())).map(v -> v.shown() || v.found()).orElse(true);
            else if (ref.startsWith(FEAR)) {
                Map<Nightmares.Family, Integer> scores = Nightmares.scores(Map.copyOf(Progression.profile(sp).counters()), d.weeklyMarks(day));
                stale = Nightmares.Family.byId(ref.substring(FEAR.length())).map(f -> scores.getOrDefault(f, 0) <= 0).orElse(true);
            }
            if (!stale) continue;
            l.waiting.remove(w);
            changed = true;
        }
        return changed;
    }

    private static boolean offerFirst(ServerPlayer sp, DreamLedger l, long day, String why) {
        String bell = BellHooks.dreamBellNext(sp);
        String ref = bell.equals("dreaming") && !l.given.contains("bell:dreaming") ? "bell:dreaming" : "";
        return l.offer(Trial.FIRST.id, day, why, ref, true);
    }

    /** A Run that can come to wait: built, not finished, not waiting, its move one the body can do. */
    static boolean eligible(ServerPlayer sp, DreamLedger l, Trial t) {
        if (!BUILT.contains(t) || l.finished(t.id) || l.isWaiting(t.id)) return false;
        return switch (t) {
            case WALL, LEDGE, VAULT -> true; // the Runner's moves are yours from the start
            case CLIMB -> dev.awaken.powers.Powers.owns(sp, dev.awaken.body.RunnerRules.STRIDE_POWER); // running up a face needs Wall Stride
            default -> false;
        };
    }

    /** The list changed: the planner starts again and the client hears whether a dream waits. */
    static void changed(ServerPlayer sp) {
        Planner.invalidate(sp);
        sync(sp, true);
    }

    /**
     * /awaken dream offer (tours): a built trial to the head of the list, whatever the rules say. {@code ref}: what it
     * carries ("bell:lucid", "vision" for the place the finder has ready, "fear:burning"), or "" to keep what it had.
     */
    public static boolean offerNow(ServerPlayer sp, String trial, String ref) {
        Trial t = Trial.byId(trial);
        if (t == null || !BUILT.contains(t)) return false;
        Dreamer d = Dreams.dreamer(sp);
        DreamLedger l = d.ledger;
        DreamLedger.Waiting old = l.waitingFor(t.id);
        if (old != null) l.waiting.remove(old);
        if (ref.equals("vision")) {
            Optional<Vision> v = d.pendingVision();
            if (v.isEmpty()) return false;
            ref = VISION + v.get().id();
        }
        String carries = !ref.isEmpty() ? ref : old != null ? old.ref() : "";
        l.waiting.addFirst(new DreamLedger.Waiting(t.id, PlayDays.current(sp.level().getServer()), "command", carries));
        changed(sp);
        return true;
    }

    /** /awaken dream ring (tours): to a ring of the trial you're in (0 the start in your room; past the last, the goal). */
    public static boolean toRing(ServerPlayer sp, int index) {
        ActiveDream ad = Dreams.active(sp);
        if (ad == null || !Trials.is(ad)) return false;
        Course c = ad.bp.course;
        Course.Ring r = c.ring(index);
        Dreams.state(sp).ring = r.index();
        if (index < c.rings().size()) Dreams.moveWithin(sp, r.at(), r.yaw());
        else {
            net.minecraft.world.phys.Vec3 end = Trials.end(c, ad);
            if (end == null) return false; // the rule hasn't shown it yet
            Dreams.moveWithin(sp, c.trial().run() ? new net.minecraft.world.phys.Vec3(c.goal().getCenter().x, c.goal().minY, c.goal().getCenter().z) : end, r.yaw());
        }
        return true;
    }

    /**
     * /awaken dream solve (tours and tests): one step of tonight's rule done the way a player would do it: the next
     * wrong thing looked at and named, the right wall's door made, the fold walked backwards, the fall looked up from,
     * the mentor worn down, the bell's carrier felled; then to the goal.
     */
    public static boolean solve(ServerPlayer sp) {
        ActiveDream ad = Dreams.active(sp);
        if (ad == null || !Trials.is(ad)) return false;
        return RoomRules.solve(sp, ad, Dreams.now(sp.level().getServer()));
    }

    // ---- tonight's plan -------------------------------------------------------------------------------------------------

    /** The plan for the dream at the head of the list (the Dream Journal's choice first), or empty when none waits. */
    public static Optional<DreamPlan> plan(ServerPlayer sp) {
        if (!enabled()) return Optional.empty();
        Dreamer d = Dreams.dreamer(sp);
        MinecraftServer server = sp.level().getServer();
        long day = PlayDays.current(server);
        DreamLedger.Waiting w = d.ledger.next(day);
        if (w == null) return Optional.empty();
        Optional<Vision> vision = w.ref().startsWith(VISION)
                ? d.vision(w.ref().substring(VISION.length())).filter(v -> !v.shown() && !v.found()) : Optional.empty();
        return Optional.of(plan(server.overworld().getSeed(), sp.getUUID(), day, d.dreams, w.trial(), w.ref(), d.snapshots, d.intention(day),
                vision, Trial.WRONG.id.equals(w.trial()) ? todayPlace(d.snapshots, day) : Optional.empty()));
    }

    // ---- replays (the Dream Recorder, the Stillpoint's In door) -----------------------------------------------------

    /** The dreams you can dream again: every trial you've finished, the one finished last first. */
    public static List<DreamLedger.Finished> replays(Dreamer d) {
        List<DreamLedger.Finished> out = new ArrayList<>(d.ledger.finished.values());
        java.util.Collections.reverse(out); // ties: the later finish first
        out.sort(java.util.Comparator.comparingLong(DreamLedger.Finished::day).reversed());
        out.removeIf(f -> Trial.byId(f.trial()) == null);
        return out;
    }

    /** "The wall: best 0:42, gold". */
    public static String replayLabel(DreamLedger.Finished f) {
        Trial t = Trial.byId(f.trial());
        return (t == null ? f.trial() : t.title) + ": best " + Trials.time(f.best())
                + (f.medal() > 0 ? ", " + Trials.medalName(f.medal()).toLowerCase() : "");
    }

    /** A finished trial dreamt again (SPEC §9.6): no bell, no place, no fear to let go; your time, medal and ghost count. */
    public static DreamPlan replayPlan(ServerPlayer sp, String trial) {
        Dreamer d = Dreams.dreamer(sp);
        long day = PlayDays.current(sp.level().getServer());
        return plan(sp.level().getServer().overworld().getSeed(), sp.getUUID(), day, d.dreams, trial, DreamLedger.REPLAY, d.snapshots,
                d.intention(day), Optional.empty(), Trial.WRONG.id.equals(trial) ? todayPlace(d.snapshots, day) : Optional.empty());
    }

    public static DreamPlan plan(long worldSeed, UUID player, long day, int dreamsSoFar, String trial, String ref,
                                 List<Snapshot> journal, String intention) {
        return plan(worldSeed, player, day, dreamsSoFar, trial, ref, journal, intention, Optional.empty(), Optional.empty());
    }

    /** What's wrong here's place: the latest the Day Journal kept today or yesterday, a home only if nothing else. */
    static Optional<Snapshot> todayPlace(List<Snapshot> journal, long day) {
        Snapshot home = null;
        for (int i = journal.size() - 1; i >= 0; i--) {
            Snapshot s = journal.get(i);
            if (s.day() < day - 1 || s.kind() == Snapshot.Kind.VISION) continue;
            if (s.kind() != Snapshot.Kind.HOME) return Optional.of(s);
            if (home == null) home = s;
        }
        return Optional.ofNullable(home);
    }

    /**
     * A trial's plan, pure: tonight's seed, your own dream sign and one more, your latest home to wake in; a Room's
     * hidden place ({@code vision}) and what's wrong here's place from today.
     */
    public static DreamPlan plan(long worldSeed, UUID player, long day, int dreamsSoFar, String trial, String ref,
                                 List<Snapshot> journal, String intention, Optional<Vision> vision, Optional<Snapshot> today) {
        long seed = DreamPlanner.seed(worldSeed, player, day, dreamsSoFar);
        RandomSource r = new XoroshiroRandomSource(seed);
        DreamSign personal = DreamSign.personal(player);
        List<DreamSign> others = new ArrayList<>(List.of(DreamSign.values()));
        others.remove(personal);
        DreamSign second = others.get(r.nextInt(others.size()));
        List<DreamPlan.Node> nodes = new ArrayList<>();
        for (int i = journal.size() - 1; i >= 0; i--) {
            Snapshot s = journal.get(i);
            if (s.kind() != Snapshot.Kind.HOME) continue;
            nodes.add(new DreamPlan.Node(DreamPlan.MEMORY, s.id(), s.label(), TrialCompiler.HOME, TrialCompiler.BASE_Y, 0, 0, false));
            break;
        }
        today.ifPresent(s -> nodes.add(new DreamPlan.Node(DreamPlan.MEMORY, s.id(), s.label(), TrialCompiler.TODAY, TrialCompiler.BASE_Y, 0, 0, false)));
        vision.ifPresent(v -> nodes.add(new DreamPlan.Node(DreamPlan.VISION, v.snapshotId(), v.label(), 0, TrialCompiler.BASE_Y, 0, 0, false)));
        int hue = 1 + r.nextInt(15);
        return new DreamPlan(seed, day, "pearl", hue, List.copyOf(nodes), List.of(), List.of(personal.name(), second.name()),
                Optional.empty(), vision.map(Vision::id), intention, DURATION, List.of(), trial, ref);
    }

    // ---- the bed's motes and the Tasks line ---------------------------------------------------------------------------

    /** Every few seconds per player: anything new to wait, the bed's motes, prizes held while the inventory was full. */
    static void tick(ServerPlayer sp) {
        if ((sp.tickCount + sp.getId()) % EVERY != 0) return;
        if (!Dreams.isDreaming(sp)) {
            check(sp);
            List<ItemStack> held = PENDING.get(sp.getUUID());
            if (held != null && sp.level().dimension() != LucidiumContent.LUCIDIUM) {
                PENDING.remove(sp.getUUID());
                for (ItemStack s : held) give(sp, s);
            }
        }
        sync(sp, false);
    }

    /** Your bed, while a dream waits for you (the client draws the motes at night). */
    static Optional<BlockPos> waitingBed(ServerPlayer sp) {
        if (!enabled() || Dreams.dreamer(sp).ledger.waiting.isEmpty()) return Optional.empty();
        Optional<GlobalPos> bed = LucidiumWorld.get(sp.level().getServer()).bed(sp.getUUID());
        return bed.filter(g -> g.dimension() == sp.level().dimension()).map(GlobalPos::pos);
    }

    static void sync(ServerPlayer sp, boolean force) {
        Optional<BlockPos> bed = waitingBed(sp);
        Optional<BlockPos> last = SENT.get(sp.getUUID());
        if (!force && bed.equals(last)) return;
        SENT.put(sp.getUUID(), bed);
        AwakenNet.send(sp, new LucidiumNet.DreamWaiting(bed));
    }

    static void forget(UUID id) {
        SENT.remove(id);
        PENDING.remove(id);
    }

    /** One line on the Tasks page while a dream waits (what it holds isn't said). */
    static List<Step> steps(ServerPlayer sp) {
        if (!enabled() || Dreams.isDreaming(sp)) return List.of();
        DreamLedger l = Dreams.dreamer(sp).ledger;
        if (l.waiting.isEmpty()) return List.of();
        boolean bell = false;
        for (DreamLedger.Waiting w : l.waiting) bell |= w.ref().startsWith("bell:");
        List<Step> out = new ArrayList<>();
        String sneak = "Sneak as you lie down if you'd rather sleep without it.";
        if (bell) out.add(Step.of("dreams/bell", Goal.BELLS, "A bell is waiting in a dream. Sleep in your bed tonight.")
                .why("Take control with H when you arrive, and find the bell before the dream fades. " + sneak).priority(30).build());
        if (!bell || l.waiting.size() > 1)
            out.add(Step.of("dreams/waiting", Goal.OUT, "A dream is waiting. Sleep in your bed tonight.")
                    .why("Take control with H when you arrive. " + sneak).priority(20).build());
        return out;
    }

    public static void registerGoals() {
        Goals.register(DreamOffers::steps);
    }

    // ---- prizes -------------------------------------------------------------------------------------------------------

    /** Into the pack; held for the next moment awake when it's full (never dropped into a dream). */
    static void give(ServerPlayer sp, ItemStack stack) {
        if (sp.level().dimension() != LucidiumContent.LUCIDIUM && sp.getInventory().add(stack) && stack.isEmpty()) return;
        if (stack.isEmpty()) return;
        if (sp.level().dimension() == LucidiumContent.LUCIDIUM) {
            PENDING.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>()).add(stack.copy());
            return;
        }
        sp.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    static boolean enabled() {
        try {
            return AwakenConfig.LUCIDIUM.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
