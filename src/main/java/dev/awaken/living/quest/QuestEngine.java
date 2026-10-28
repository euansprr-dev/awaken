package dev.awaken.living.quest;

import dev.awaken.Awaken;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.living.LivingContent;
import dev.awaken.living.wayfarer.Scenes;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Requirements;
import dev.awaken.progression.RewardHooks;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Runs the Wayfarer chains (SPEC §21, Phase C): starting one the player found, moving each through its steps as the
 * player does what they ask, paying each step once (claim ids {@code quest/<chain>/<step>}), and finishing it with its
 * relic. Event-driven (kills, talks, ability reports) plus a check once a second for places and items.
 */
public final class QuestEngine {
    private QuestEngine() {}

    public static QuestState state(ServerPlayer p) {
        return p.getData(LivingContent.QUESTS.get());
    }

    private static void save(ServerPlayer p, QuestState s) {
        p.setData(LivingContent.QUESTS.get(), s); // re-set to sync to the owner
        journal(p);
    }

    /** Sends the player's Journal: the chains they took (tracked first) and the bounties on their slate. */
    public static void journal(ServerPlayer p) {
        QuestState s = state(p);
        List<dev.awaken.living.LivingNet.JournalEntry> out = new java.util.ArrayList<>();
        for (QuestState.Active a : s.active) {
            QuestDef d = QuestBook.get(a.chain());
            String l = line(a);
            if (d == null || l == null) continue;
            var e = new dev.awaken.living.LivingNet.JournalEntry(d.title(), l, a.chain().equals(s.tracked), false);
            if (e.tracked()) out.addFirst(e);
            else out.add(e);
        }
        var b = dev.awaken.living.bounty.Bounties.state(p);
        for (var t : b.taken()) {
            var d = dev.awaken.living.bounty.Bounties.all().get(t.id());
            if (d == null) continue;
            String count = d.objective().type().equals("collect") ? "" : " (" + Math.min(t.progress(), d.objective().count()) + "/" + d.objective().count() + ")";
            out.add(new dev.awaken.living.LivingNet.JournalEntry(d.title(), dev.awaken.living.bounty.Bounties.task(d) + count, false, true));
        }
        // (SPEC §28.7) other modules' rows: a village's trouble you took, what a village remembers of you
        for (var row : dev.awaken.living.api.Quests.journalRows(p)) {
            var e = new dev.awaken.living.LivingNet.JournalEntry(row.title(), row.line(), row.tracked(), false);
            if (row.tracked()) out.addFirst(e);
            else out.add(e);
        }
        while (out.size() > 16) out.removeLast(); // the payload's list holds sixteen
        dev.awaken.core.AwakenNet.send(p, new dev.awaken.living.LivingNet.Journal(out));
    }

    /** Takes a chain on (from a Wayfarer's choice card). False when it can't be taken now, and says why. */
    public static boolean start(ServerPlayer p, String chain) {
        return start(p, chain, 0L);
    }

    /**
     * Takes a chain on from a Wayfarer at a scene ({@code scene} its id; 0 for a resident, who gets a scene made for the
     * chain's places around the player).
     */
    public static boolean start(ServerPlayer p, String chain, long scene) {
        QuestDef d = QuestBook.get(chain);
        QuestState s = state(p);
        if (d == null || s.has(chain) || s.done.contains(chain)) return false;
        if (s.active.size() >= QuestState.MAX_ACTIVE) {
            Signal.toast(p, Component.literal("Your journal is full. Finish or drop a chain first."));
            return false;
        }
        if (!Requirements.met(p, d.requires())) return false;
        long id = scene != 0 ? scene : Scenes.sceneFor(p, chain);
        s.active.add(new QuestState.Active(chain, 0, 0, java.util.Optional.of(id), p.level().getGameTime()));
        if (s.tracked.isEmpty()) s.tracked = chain;
        save(p, s);
        Signal.toast(p, Component.literal("Journal · " + d.title()));
        begin(p, id, d.steps().getFirst());
        return true;
    }

    /** Drops a chain (the Journal's choice); it can be found again later. */
    public static boolean drop(ServerPlayer p, String chain) {
        QuestState s = state(p);
        s.active.stream().filter(a -> a.chain().equals(chain)).findFirst()
                .flatMap(QuestState.Active::scene).ifPresent(id -> Scenes.dropped(p.level().getServer(), id));
        boolean removed = s.active.removeIf(a -> a.chain().equals(chain));
        if (removed && s.tracked.equals(chain)) s.tracked = s.active.isEmpty() ? "" : s.active.getFirst().chain();
        if (removed) save(p, s);
        return removed;
    }

    public static void track(ServerPlayer p, String chain) {
        QuestState s = state(p);
        if (s.has(chain)) {
            s.tracked = chain;
            save(p, s);
        }
    }

    /**
     * Something happened that steps may be waiting for: {@code type} is an objective type, {@code target} what it was
     * (an entity type id, a Wayfarer id, a use id like {@code ascent_zip}, a discovery key), {@code amount} how much.
     */
    public static void advance(ServerPlayer p, String type, String target, int amount) {
        QuestState s = state(p);
        boolean changed = false;
        for (int i = 0; i < s.active.size(); i++) {
            QuestState.Active a = s.active.get(i);
            QuestDef d = QuestBook.get(a.chain());
            if (d == null || a.step() >= d.steps().size()) continue;
            QuestDef.Step step = d.steps().get(a.step());
            if (!step.objective().type().equals(type) || !matches(step.objective().target(), target)) continue;
            if (blocked(p, step)) continue;
            int prog = a.progress() + amount;
            if (prog >= step.objective().count()) {
                complete(p, s, i, d, step);
            } else {
                s.active.set(i, a.withProgress(prog));
            }
            changed = true;
        }
        if (changed) save(p, s);
    }

    /** Sets a step's progress outright (places reached, items held): the once-a-second check. */
    static void hold(ServerPlayer p, String type, String target, int have) {
        QuestState s = state(p);
        boolean changed = false;
        for (int i = 0; i < s.active.size(); i++) {
            QuestState.Active a = s.active.get(i);
            QuestDef d = QuestBook.get(a.chain());
            if (d == null || a.step() >= d.steps().size()) continue;
            QuestDef.Step step = d.steps().get(a.step());
            if (!step.objective().type().equals(type) || !matches(step.objective().target(), target) || blocked(p, step)) continue;
            if (have >= step.objective().count()) {
                complete(p, s, i, d, step);
                changed = true;
            } else if (have != a.progress()) {
                s.active.set(i, a.withProgress(have));
                changed = true;
            }
        }
        if (changed) save(p, s);
    }

    private static boolean matches(String want, String got) {
        if (want.isEmpty() || want.equals(got)) return true;
        if (want.startsWith("#") || want.endsWith("*")) return got.startsWith(want.replace("#", "").replace("*", ""));
        return false;
    }

    /**
     * A step that needs a tier or flag the player lacks waits (and says so from the poll). {@code tier:<need>} asks the
     * Horizon's reach answers (the Ascent's tiers, movement powers); anything else is a progression requirement.
     */
    public static boolean blocked(ServerPlayer p, QuestDef.Step step) {
        if (step.needs().isEmpty()) return false;
        String need = step.needs().get();
        if (need.startsWith("tier:")) return !HorizonHooks.can(p, need.substring(5));
        return !Requirements.met(p, List.of(need));
    }

    /** A step starts: its opening lines, and whatever it brings to the scene. */
    private static void begin(ServerPlayer p, long scene, QuestDef.Step step) {
        sayLines(p, step.start());
        step.spawn().ifPresent(sp -> Scenes.bring(p, scene, sp));
    }

    /**
     * A scene that stages a step's ending itself (the first thread's High Gate shot, Ilse by the fire): true when it took
     * the step's done lines (it says them in its own way), so the engine doesn't say them again.
     */
    public interface Stager {
        boolean stage(ServerPlayer p, String chain, QuestDef.Step step);
    }

    private static Stager stager = (p, chain, step) -> false;

    public static void stager(Stager s) {
        stager = s;
    }

    private static void complete(ServerPlayer p, QuestState s, int i, QuestDef d, QuestDef.Step step) {
        QuestState.Active a = s.active.get(i);
        String claim = "awaken:claim/quest/" + a.chain() + "/" + step.id();
        if (Progression.profile(p).counter(claim) == 0) {
            Progression.setCounter(p, claim, 1);
            RewardHooks.apply(p, step.reward(), Progression.XpSource.WORLD);
        }
        if (!stager.stage(p, a.chain(), step)) sayLines(p, step.done());
        step.move().ifPresent(m -> a.scene().ifPresent(id -> Scenes.move(p.level().getServer(), id, m)));
        int next = a.step() + 1;
        if (next >= d.steps().size()) {
            finish(p, s, i, d);
        } else {
            s.active.set(i, a.withStep(next));
            QuestDef.Step n = d.steps().get(next);
            begin(p, a.scene().orElse(0L), n);
            Signal.toast(p, Component.literal(d.title() + " · " + n.text()));
        }
    }

    private static void finish(ServerPlayer p, QuestState s, int i, QuestDef d) {
        String chain = s.active.get(i).chain();
        s.active.get(i).scene().ifPresent(id -> Scenes.done(p.level().getServer(), id));
        s.active.remove(i);
        s.done.add(chain);
        if (s.tracked.equals(chain)) s.tracked = s.active.isEmpty() ? "" : s.active.getFirst().chain();
        String claim = "awaken:claim/quest/" + chain + "/finish";
        if (Progression.profile(p).counter(claim) == 0) {
            Progression.setCounter(p, claim, 1);
            RewardHooks.apply(p, d.finish(), Progression.XpSource.WORLD);
            d.relic().ifPresent(relic -> giveRelic(p, s, chain, relic));
            dev.awaken.core.api.ArmsApi.questDone(p, chain); // whoever teaches a style at the end of a chain pays it (docs/arms-progression/PLAN.md §3.4)
        }
        Progression.count(p, "awaken:quests_done");
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Chain complete · " + d.title()))
                .body(Component.literal(d.summary().isEmpty() ? "Written in your journal." : d.summary()))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
    }

    private static void giveRelic(ServerPlayer p, QuestState s, String chain, String relic) {
        if (!s.relics.add(chain)) return;
        var item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(relic));
        if (item == net.minecraft.world.item.Items.AIR) {
            Awaken.LOG.warn("[living] relic {} of {} doesn't exist", relic, chain);
            return;
        }
        ItemStack stack = new ItemStack(item);
        p.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    /** Dev and tours: puts a chain (taken first if need be) at one of its steps, its claims from there on cleared. */
    public static boolean devStep(ServerPlayer p, String chain, String stepId) {
        QuestDef d = QuestBook.get(chain);
        if (d == null) return false;
        int at = -1;
        for (int i = 0; i < d.steps().size(); i++) if (d.steps().get(i).id().equals(stepId)) at = i;
        if (at < 0) return false;
        QuestState s = state(p);
        if (!s.has(chain)) {
            s.done.remove(chain);
            if (!start(p, chain)) return false;
        }
        for (int i = 0; i < s.active.size(); i++) if (s.active.get(i).chain().equals(chain)) s.active.set(i, s.active.get(i).withStep(at));
        for (int i = at; i < d.steps().size(); i++) Progression.setCounter(p, "awaken:claim/quest/" + chain + "/" + d.steps().get(i).id(), 0);
        s.tracked = chain;
        save(p, s);
        return true;
    }

    /** Wayfarer lines: "Name: text" shows as a story line. */
    public static void sayLines(ServerPlayer p, List<String> lines) {
        for (String l : lines) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(l))
                    .dialogue().ttl(20 * 5).build());
        }
    }

    public static void sayLine(ServerPlayer p, String line) {
        sayLines(p, List.of(line));
    }

    /** Hands over what a step asked for (an item and count); true when it was there and the step moved on. */
    public static boolean deliver(ServerPlayer p, String chain, String item, int count) {
        if (count(p, item) < count) return false;
        int left = count;
        var inv = p.getInventory();
        boolean tag = item.startsWith("#");
        var tagKey = tag ? net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse(item.substring(1))) : null;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (tag ? st.is(tagKey) : BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(item)) {
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
        QuestState s = state(p);
        for (int i = 0; i < s.active.size(); i++) {
            QuestState.Active a = s.active.get(i);
            if (!a.chain().equals(chain)) continue;
            QuestDef d = QuestBook.get(chain);
            if (d == null || a.step() >= d.steps().size()) return false;
            complete(p, s, i, d, d.steps().get(a.step()));
            save(p, s);
            return true;
        }
        return false;
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity dead = e.getEntity();
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
        if (!state(p).active.isEmpty()) advance(p, "kill", type, 1);
    }

    /** Once a second per player with chains: places reached, items held, discoveries made. */
    public static void poll(ServerPlayer p) {
        QuestState s = state(p);
        if (s.active.isEmpty()) return;
        for (QuestState.Active a : List.copyOf(s.active)) {
            QuestDef d = QuestBook.get(a.chain());
            if (d == null || a.step() >= d.steps().size()) continue;
            QuestDef.Objective o = d.steps().get(a.step()).objective();
            switch (o.type()) {
                case "reach" -> {
                    BlockPos at = a.scene().map(id -> Scenes.marker(p.level().getServer(), id, o.target())).orElse(null);
                    if (at == null || !p.blockPosition().closerThan(at, o.radius() + 16)) continue;
                    QuestDef.Step step = d.steps().get(a.step());
                    if (blocked(p, step)) waiting(p, step);
                    else if (p.blockPosition().closerThan(at, o.radius())) hold(p, "reach", o.target(), 1);
                }
                case "collect" -> hold(p, "collect", o.target(), count(p, o.target()));
                case "discover" -> {
                    if (Progression.profile(p).counter("awaken:discovered/" + o.target()) > 0) hold(p, "discover", o.target(), 1);
                }
                case "hearth" -> {
                    if (dev.awaken.core.api.HearthHooks.hearthOf(p).isPresent()) hold(p, "hearth", o.target(), 1);
                }
                default -> {}
            }
        }
    }

    /** A tier-gated step at its place says what it waits for: on arrival, then every 20 s at most (not on the minute). */
    private static void waiting(ServerPlayer p, QuestDef.Step step) {
        if (!blocked(p, step) || step.needsText().isEmpty() || !dev.awaken.core.Throttle.allow(p, "quest_needs", 400)) return;
        Signal.toast(p, Component.literal(step.needsText()));
    }

    static int count(ServerPlayer p, String target) {
        int n = 0;
        var inv = p.getInventory();
        boolean tag = target.startsWith("#");
        var tagKey = tag ? net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse(target.substring(1))) : null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (tag ? st.is(tagKey) : BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(target)) n += st.getCount();
        }
        return n;
    }

    /** The Journal line of a chain's current step, or null. */
    public static @Nullable String line(QuestState.Active a) {
        QuestDef d = QuestBook.get(a.chain());
        if (d == null || a.step() >= d.steps().size()) return null;
        QuestDef.Step st = d.steps().get(a.step());
        if (st.tally()) return st.text() + " · " + a.progress() + "/" + st.objective().count();
        String count = st.objective().count() > 1 ? " (" + a.progress() + "/" + st.objective().count() + ")" : "";
        return st.text() + count;
    }
}
