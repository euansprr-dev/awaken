package dev.awaken.living.quest;

import dev.awaken.Awaken;
import dev.awaken.living.LivingContent;
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
    }

    /** Takes a chain on (from a Wayfarer's choice card). False when it can't be taken now, and says why. */
    public static boolean start(ServerPlayer p, String chain) {
        QuestDef d = QuestBook.get(chain);
        QuestState s = state(p);
        if (d == null || s.has(chain) || s.done.contains(chain)) return false;
        if (s.active.size() >= QuestState.MAX_ACTIVE) {
            Signal.toast(p, Component.literal("Your journal is full. Finish or drop a chain first."));
            return false;
        }
        if (!Requirements.met(p, d.requires())) return false;
        s.active.add(new QuestState.Active(chain, 0, 0, java.util.Optional.empty(), p.level().getGameTime()));
        if (s.tracked.isEmpty()) s.tracked = chain;
        save(p, s);
        Signal.toast(p, Component.literal("Journal · " + d.title()));
        say(p, d.steps().getFirst().start());
        return true;
    }

    /** Drops a chain (the Journal's choice); it can be found again later. */
    public static boolean drop(ServerPlayer p, String chain) {
        QuestState s = state(p);
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

    /** A step that needs a tier or flag the player lacks waits (and says so, once a minute at most, from the poll). */
    static boolean blocked(ServerPlayer p, QuestDef.Step step) {
        return step.needs().isPresent() && !Requirements.met(p, List.of(step.needs().get()));
    }

    private static void complete(ServerPlayer p, QuestState s, int i, QuestDef d, QuestDef.Step step) {
        QuestState.Active a = s.active.get(i);
        String claim = "awaken:claim/quest/" + a.chain() + "/" + step.id();
        if (Progression.profile(p).counter(claim) == 0) {
            Progression.setCounter(p, claim, 1);
            RewardHooks.apply(p, step.reward(), Progression.XpSource.WORLD);
        }
        say(p, step.done());
        int next = a.step() + 1;
        if (next >= d.steps().size()) {
            finish(p, s, i, d);
        } else {
            s.active.set(i, a.withStep(next));
            QuestDef.Step n = d.steps().get(next);
            say(p, n.start());
            Signal.toast(p, Component.literal(d.title() + " · " + n.text()));
        }
    }

    private static void finish(ServerPlayer p, QuestState s, int i, QuestDef d) {
        String chain = s.active.get(i).chain();
        s.active.remove(i);
        s.done.add(chain);
        if (s.tracked.equals(chain)) s.tracked = s.active.isEmpty() ? "" : s.active.getFirst().chain();
        String claim = "awaken:claim/quest/" + chain + "/finish";
        if (Progression.profile(p).counter(claim) == 0) {
            Progression.setCounter(p, claim, 1);
            RewardHooks.apply(p, d.finish(), Progression.XpSource.WORLD);
            d.relic().ifPresent(relic -> giveRelic(p, s, chain, relic));
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

    /** Wayfarer lines: "Name: text" shows as a story line. */
    static void say(ServerPlayer p, List<String> lines) {
        for (String l : lines) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(l))
                    .presentation(SignalWindow.Presentation.STORY).ttl(20 * 5).build());
        }
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
                    BlockPos at = a.scene().map(BlockPos::of).orElse(null);
                    if (at != null && p.blockPosition().closerThan(at, o.radius())) hold(p, "reach", o.target(), 1);
                }
                case "collect" -> hold(p, "collect", o.target(), count(p, o.target()));
                case "discover" -> {
                    if (Progression.profile(p).counter("awaken:discovered/" + o.target()) > 0) hold(p, "discover", o.target(), 1);
                }
                default -> {}
            }
        }
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
        String count = st.objective().count() > 1 ? " (" + a.progress() + "/" + st.objective().count() + ")" : "";
        return st.text() + count;
    }
}
