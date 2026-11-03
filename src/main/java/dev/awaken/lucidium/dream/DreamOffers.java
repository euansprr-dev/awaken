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
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.lucidium.plan.DreamSign;
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
 * the first (the Dreaming Bell's), and each move family the body can do brings its Run, one new dream a play-day.
 */
public final class DreamOffers {
    private DreamOffers() {}

    /** The level road's flag (level 12) that brings the first dream. */
    static final String FLAG = "awaken:dreams";
    /** The trials with a course built so far, in the order they come to wait. */
    static final Set<Trial> BUILT = Set.of(Trial.FIRST, Trial.WALL, Trial.LEDGE, Trial.VAULT, Trial.CLIMB);
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
            for (Trial t : Trial.RUNS) {
                if (!eligible(sp, l, t)) continue;
                if (l.offer(t.id, day, "your body can do it", "", false)) changed = true;
                break; // in order, one new dream a play-day
            }
        }
        if (changed) changed(sp);
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

    /** /awaken dream offer (tours): a built trial to the head of the list, whatever the rules say. */
    public static boolean offerNow(ServerPlayer sp, String trial) {
        Trial t = Trial.byId(trial);
        if (t == null || !BUILT.contains(t)) return false;
        DreamLedger l = Dreams.dreamer(sp).ledger;
        DreamLedger.Waiting old = l.waitingFor(t.id);
        if (old != null) l.waiting.remove(old);
        l.waiting.addFirst(old != null ? old : new DreamLedger.Waiting(t.id, PlayDays.current(sp.level().getServer()), "command", ""));
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
        else Dreams.moveWithin(sp, new net.minecraft.world.phys.Vec3(c.goal().getCenter().x, c.goal().minY, c.goal().getCenter().z), r.yaw());
        return true;
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
        return Optional.of(plan(server.overworld().getSeed(), sp.getUUID(), day, d.dreams, w.trial(), w.ref(), d.snapshots, d.intention(day)));
    }

    /** A trial's plan, pure: tonight's seed, your own dream sign and one more, your latest home to wake in. */
    public static DreamPlan plan(long worldSeed, UUID player, long day, int dreamsSoFar, String trial, String ref,
                                 List<Snapshot> journal, String intention) {
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
            nodes.add(new DreamPlan.Node(DreamPlan.MEMORY, s.id(), s.label(), 0, TrialCompiler.BASE_Y, 0, 0, false));
            break;
        }
        int hue = 1 + r.nextInt(15);
        return new DreamPlan(seed, day, "pearl", hue, List.copyOf(nodes), List.of(), List.of(personal.name(), second.name()),
                Optional.empty(), Optional.empty(), intention, DURATION, List.of(), trial, ref);
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
                .why("Take control with H when you arrive, and follow the road to the bell. " + sneak).priority(30).build());
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
