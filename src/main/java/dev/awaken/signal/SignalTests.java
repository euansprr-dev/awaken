package dev.awaken.signal;

import dev.awaken.core.api.Notices;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.signal.ui.ChoiceDeck;
import dev.awaken.signal.ui.Glossary;
import dev.awaken.signal.ui.Pace;
import dev.awaken.signal.ui.Rect;
import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.SignalText;
import dev.awaken.signal.ui.StatusLayout;
import dev.awaken.signal.ui.StoryPager;
import dev.awaken.signal.ui.ToastBurst;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** GameTests for System v2: layout bounds at every GUI size, text rules, key handling, bursts, pending decisions. */
final class SignalTests {
    private SignalTests() {}

    /** The GUI sizes the owner's screens produce (auto scale on his Mac is 378×246). */
    static final int[][] SIZES = {{378, 246}, {480, 270}, {640, 360}, {960, 540}};
    static final double[] SCALES = {0.75, 1.0, 1.5};

    private static final AtomicInteger ANSWERS = new AtomicInteger();
    private static final Map<UUID, List<NowHooks.Suggestion>> TEST_NOW = new ConcurrentHashMap<>();

    static void register() {
        Signal.onAction("signaltest", (p, action, accepted) -> ANSWERS.incrementAndGet());
        NowHooks.register(p -> TEST_NOW.getOrDefault(p.getUUID(), List.of()));

        // SPEC §27.7: tips, the pure rules: the context settles, the higher priority wins, a gap between tips, a cooldown
        // for the same one, retired after its dones or showings, the short text once done, and only if the director allows
        AwakenTests.register("signal_tip_rules", 20, h -> {
            var a = new dev.awaken.signal.ui.TipRules.Def("Hold {key.jump}", "{key.jump}", 60, 140, 2, 3, 600, "after");
            var b = new dev.awaken.signal.ui.TipRules.Def("Press K", "", 40, 140, 1, 2, 600, "any");
            java.util.Map<String, dev.awaken.signal.ui.TipRules.Def> defs = java.util.Map.of("a", a, "b", b);
            java.util.Map<String, dev.awaken.signal.ui.TipRules.Count> counts = new java.util.HashMap<>();
            java.util.Map<String, Long> since = new java.util.HashMap<>(java.util.Map.of("a", 100L, "b", 100L)), last = new java.util.HashMap<>();
            h.assertTrue(dev.awaken.signal.ui.TipRules.pick(103, since, defs, counts, last, -1000, true, x -> true) == null, "a context settles first");
            h.assertTrue("a".equals(dev.awaken.signal.ui.TipRules.pick(106, since, defs, counts, last, -1000, true, x -> true)), "the higher priority wins");
            h.assertTrue("b".equals(dev.awaken.signal.ui.TipRules.pick(106, since, defs, counts, last, -1000, false, x -> true)), "an 'after' tip waits for the awakening");
            h.assertTrue(dev.awaken.signal.ui.TipRules.pick(106, since, defs, counts, last, 50, true, x -> true) == null, "a gap after the last tip");
            h.assertTrue(dev.awaken.signal.ui.TipRules.pick(106, since, defs, counts, last, -1000, true, x -> false) == null, "never past the director");
            last.put("a", 106L);
            h.assertTrue("b".equals(dev.awaken.signal.ui.TipRules.pick(300, since, defs, counts, last, -1000, true, x -> true)), "a cooldown for the same tip");
            counts.put("a", new dev.awaken.signal.ui.TipRules.Count(1, 1));
            h.assertTrue(dev.awaken.signal.ui.TipRules.text(a, counts.get("a")).equals("{key.jump}"), "done once: the short text");
            counts.put("a", new dev.awaken.signal.ui.TipRules.Count(1, 2));
            h.assertTrue(dev.awaken.signal.ui.TipRules.retired(a, counts.get("a")), "done enough: retired");
            h.assertTrue(dev.awaken.signal.ui.TipRules.retired(b, new dev.awaken.signal.ui.TipRules.Count(2, 0)), "shown enough: retired");
            h.succeed();
        });

        AwakenTests.register("signal_layout_every_gui_size", 20, h -> {
            List<String> bad = layoutViolations();
            h.assertTrue(bad.isEmpty(), "layout: " + String.join("; ", bad.subList(0, Math.min(6, bad.size()))));
            h.succeed();
        });

        AwakenTests.register("signal_layout_toast_and_card_sizes", 20, h -> {
            for (int[] s : SIZES) {
                Rect t = SignalLayout.toast(s[0], 0);
                h.assertTrue(t.w() <= 160 && t.h() <= 32, "toast is vanilla-sized at " + s[0] + "x" + s[1]);
                SignalLayout.Card c = SignalLayout.choice(s[0], s[1], 1.0, 2, true);
                h.assertTrue(c.rect().h() <= s[1] * 0.20, "choice card ≤ 20% of height at " + s[0] + "x" + s[1] + ": " + c.rect());
                h.assertTrue(c.rect().w() <= 180, "choice card ~170 wide: " + c.rect());
                SignalLayout.Card p = SignalLayout.panel(s[0], s[1], 1.0, 9, true);
                h.assertTrue(p.rect().w() <= 200, "panel ≤ 200 wide: " + p.rect());
                h.assertTrue(p.lines() >= 2, "panel keeps at least two lines at " + s[0] + "x" + s[1]);
            }
            // the owner's screen: the card the old window was (260 x 127) must now be small
            SignalLayout.Card mac = SignalLayout.choice(378, 246, 1.0, 1, true);
            h.assertTrue(mac.rect().w() * mac.rect().h() < 378 * 246 * 0.10, "choice card under 10% of the screen: " + mac.rect());
            h.succeed();
        });

        AwakenTests.register("signal_status_screen_frame", 20, h -> {
            List<String> tabs = List.of("Status", "Truths", "Codex", "Gate Map");
            for (int[] s : SIZES) {
                String at = s[0] + "x" + s[1];
                StatusLayout.Frame f = StatusLayout.of(s[0], s[1], tabs, "K to close", SignalText::approxWidth);
                h.assertTrue(f.panel().insideScreen(s[0], s[1]), "panel inside the screen at " + at);
                Rect c = f.content();
                h.assertTrue(c.x() >= f.panel().x() && c.right() + StatusLayout.SCROLLBAR <= f.panel().right()
                        && c.y() >= f.panel().y() && c.bottom() <= f.panel().bottom(), "content inside the panel at " + at + ": " + c);
                h.assertTrue(c.w() >= 300 && c.h() >= 150, "content keeps a usable size at " + at + ": " + c);
                for (int i = 0; i < f.tabs().size(); i++) {
                    Rect t = f.tabs().get(i);
                    h.assertTrue(t.x() >= f.panel().x() && t.right() <= f.panel().right() && t.bottom() <= f.panel().bottom(), "tab inside the panel at " + at);
                    h.assertFalse(t.intersects(c), "tab clear of the content at " + at);
                    for (int j = i + 1; j < f.tabs().size(); j++) h.assertFalse(t.intersects(f.tabs().get(j)), "tabs don't overlap at " + at);
                    if (f.close() != null) h.assertFalse(t.intersects(f.close()), "close hint clear of the tabs at " + at);
                }
            }
            h.assertTrue(StatusLayout.of(378, 246, tabs, "K to close", SignalText::approxWidth).topTabs(), "the owner's screen gets tabs on top");
            h.succeed();
        });

        AwakenTests.register("signal_text_rules", 20, h -> {
            h.assertTrue(SignalText.stripKeyHints("[Y] Extract · [N] Stay").isEmpty(), "hint-only footer vanishes");
            h.assertTrue(SignalText.stripKeyHints("[G] speak to the dead · [Y] go back to your body").isEmpty(), "veil hints");
            h.assertTrue(SignalText.stripKeyHints("Rest here · [Y] Leave").equals("Rest here"), "keeps the words: '" + SignalText.stripKeyHints("Rest here · [Y] Leave") + "'");
            h.assertTrue(SignalText.stripKeyHints("Scouted · walk in to enter").equals("Scouted · walk in to enter"), "plain footer untouched");
            var plain = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Q")).action("x:y").build();
            h.assertTrue(SignalText.yesLabel(plain).equals("Accept") && SignalText.noLabel(plain).equals("Not now"), "fallback labels");
            var only = SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("Q")).action("story:accept_only").build();
            h.assertTrue(SignalText.noLabel(only).equals("Accept anyway"), "accept-only says N accepts too");
            var labelled = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Leave now?"))
                    .choice("gates:extract", "Leave with your loot", "Keep going").build();
            h.assertTrue(SignalText.yesLabel(labelled).equals("Leave with your loot") && SignalText.noLabel(labelled).equals("Keep going"), "choice labels");
            var info = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Rest Shrine"))
                    .row("Satchel", "6 slots").row("Floor", "1 of 3").build();
            h.assertTrue(SignalText.toastDetail(info).equals("Satchel 6 slots · Floor 1 of 3"), "rows collapse into the detail line");
            var bodied = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("T")).body(Component.literal("One. Two.")).build();
            h.assertTrue(SignalText.toastDetail(bodied).equals("One."), "first sentence");
            String e = SignalText.ellipsize("abcdefghijkl", 36, SignalText::approxWidth);
            h.assertTrue(SignalText.approxWidth(e) <= 36 && e.endsWith(SignalText.ELLIPSIS), "ellipsize fits: " + e);
            List<String> lines = SignalText.wrap("the quick brown fox jumps over the lazy dog", 60, SignalText::approxWidth);
            for (String l : lines) h.assertTrue(SignalText.approxWidth(l) <= 60, "wrapped line fits: " + l);
            h.assertTrue(String.join(" ", lines).equals("the quick brown fox jumps over the lazy dog"), "wrap keeps every word");
            h.assertTrue(SignalText.wrapAtMost("a b c d e f g h i j k l m n o p", 24, 2, SignalText::approxWidth).size() == 2, "wrapAtMost caps lines");
            h.succeed();
        });

        AwakenTests.register("signal_glossary_terms", 20, h -> {
            for (String w : List.of("Satchel", "Extract", "Extraction", "Seal", "play-days", "Veil", "Fractured", "Reassessment",
                    "Echoes", "Flow", "Kinetic", "Truths", "Directive", "Latents")) {
                h.assertTrue(Glossary.lookup(w).isPresent(), "glossary explains " + w);
            }
            h.assertTrue(Glossary.lookup("Flowers").isEmpty() && Glossary.lookup("Sealant").isEmpty(), "no false friends");
            h.succeed();
        });

        AwakenTests.register("signal_choice_keys_never_carry_over", 20, h -> {
            ChoiceDeck deck = new ChoiceDeck();
            deck.offer(choice("signaltest:a"), 20 * 60);
            deck.offer(choice("signaltest:b"), 20 * 60);
            h.assertTrue(deck.tick(1, 0, false) == null, "a press on the tick a card appears is ignored");
            h.assertTrue(deck.current() != null && deck.current().action.equals("signaltest:a"), "first card is a");
            // a key held or mashed from before never answers: each press restarts the quiet wait
            for (int i = 0; i < ChoiceDeck.ARM_TICKS * 3; i++) h.assertTrue(deck.tick(1, 1, false) == null, "presses before arming are ignored");
            h.assertFalse(deck.armed(), "not armed while presses keep coming");
            for (int i = 0; i < ChoiceDeck.ARM_TICKS; i++) h.assertTrue(deck.tick(0, 0, false) == null, "quiet ticks");
            h.assertTrue(deck.armed(), "armed after a quiet moment");
            ChoiceDeck.Answer a = deck.tick(1, 0, false);
            h.assertTrue(a != null && a.action().equals("signaltest:a") && a.accepted(), "Y answers a: " + a);
            // the player keeps pressing Y for two seconds: none of it may answer b
            for (int i = 0; i < 40; i++) {
                ChoiceDeck.Answer stray = deck.tick(1, 0, false);
                h.assertTrue(stray == null, "a stray Y answered " + (stray == null ? "" : stray.action()) + " at tick " + i);
            }
            h.assertTrue(deck.current() != null && deck.current().action.equals("signaltest:b"), "b is up");
            for (int i = 0; i < ChoiceDeck.ARM_TICKS; i++) deck.tick(0, 0, false);
            ChoiceDeck.Answer b = deck.tick(0, 1, false);
            h.assertTrue(b != null && b.action().equals("signaltest:b") && !b.accepted(), "N declines b: " + b);
            h.assertTrue(deck.pending().isEmpty(), "nothing pending");
            h.succeed();
        });

        AwakenTests.register("signal_choice_folds_and_waits", 20, h -> {
            ChoiceDeck deck = new ChoiceDeck();
            deck.offer(choice("signaltest:c"), 20 * 120);
            for (int i = 0; i < ChoiceDeck.FOLD_TICKS + 5; i++) h.assertTrue(deck.tick(0, 0, false) == null, "no answer by itself");
            h.assertTrue(deck.current() == null && deck.waiting() == 1, "folded into the tracker, still waiting");
            for (int i = 0; i < 40; i++) h.assertTrue(deck.tick(1, 0, false) == null, "keys do nothing to a folded decision");
            ChoiceDeck.Answer a = deck.answer("signaltest:c", true);
            h.assertTrue(a != null && a.accepted() && deck.pending().isEmpty(), "answered from the Status screen");
            // paused (a screen is open): the fold timer stops
            deck.offer(choice("signaltest:d"), 20 * 120);
            deck.tick(0, 0, false);
            for (int i = 0; i < ChoiceDeck.FOLD_TICKS + 5; i++) deck.tick(0, 0, true);
            h.assertTrue(deck.current() != null, "a card doesn't fold while a screen is open");
            // expiry: the server's ttl wins
            ChoiceDeck short_ = new ChoiceDeck();
            short_.offer(choice("signaltest:e"), 30);
            for (int i = 0; i < 31; i++) short_.tick(0, 0, false);
            h.assertTrue(short_.pending().isEmpty(), "expired choices go away");
            // a relog brings decisions back folded, never as cards
            ChoiceDeck relog = new ChoiceDeck();
            relog.sync(List.of(new ChoiceDeck.Entry(choice("signaltest:f"), 20 * 60)));
            for (int i = 0; i < 40; i++) relog.tick(0, 0, false);
            h.assertTrue(relog.current() == null && relog.waiting() == 1, "synced decisions wait folded");
            relog.sync(List.of());
            h.assertTrue(relog.pending().isEmpty(), "sync removes what the server dropped");
            // an urgent decision pushes an ordinary card back and goes first
            ChoiceDeck urgent = new ChoiceDeck();
            urgent.offer(choice("signaltest:question"), 20 * 60);
            urgent.tick(0, 0, false);
            h.assertTrue(urgent.current() != null && urgent.current().action.equals("signaltest:question"), "question up");
            urgent.offer(SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Your body is under attack."))
                    .choice("signaltest:body", "Go back to your body", "Stay").priority(9).build(), 160);
            urgent.tick(0, 0, false);
            h.assertTrue(urgent.current() != null && urgent.current().action.equals("signaltest:body"), "the warning pre-empts");
            for (int i = 0; i < ChoiceDeck.ARM_TICKS; i++) urgent.tick(0, 0, false);
            ChoiceDeck.Answer back = urgent.tick(1, 0, false);
            h.assertTrue(back != null && back.action().equals("signaltest:body"), "Y answers the warning");
            for (int i = 0; i <= ChoiceDeck.GAP_TICKS; i++) urgent.tick(0, 0, false);
            h.assertTrue(urgent.current() != null && urgent.current().action.equals("signaltest:question"), "the question comes back");
            // newest copy replaces the older one
            relog.offer(choice("signaltest:g"), 100);
            relog.offer(choice("signaltest:g"), 200);
            h.assertTrue(relog.pending().size() == 1 && relog.pending().get(0).ticksLeft() == 200, "one entry per action");
            h.succeed();
        });

        AwakenTests.register("signal_toast_bursts_merge", 20, h -> {
            ToastBurst<String> b = new ToastBurst<>();
            b.offer(0, "a");
            b.offer(0, "b");
            b.offer(1, "c");
            h.assertTrue(b.poll(2).isEmpty(), "waits for the burst to settle");
            var out = b.poll(1 + ToastBurst.QUIET);
            h.assertTrue(out.size() == 1 && out.get(0).merged() && out.get(0).items().size() == 3, "three at once become one: " + out);
            ToastBurst<String> two = new ToastBurst<>();
            two.offer(0, "a");
            two.offer(0, "b");
            var pair = two.poll(ToastBurst.QUIET);
            h.assertTrue(pair.size() == 2 && !pair.get(0).merged(), "two stay separate");
            two.offer(10, "c");
            two.offer(11, "d");
            int shownSoon = 0;
            for (long t = 12; t < ToastBurst.QUIET + ToastBurst.WINDOW - 1; t++) shownSoon += two.poll(t).size();
            h.assertTrue(shownSoon == 0, "no third toast inside the same two seconds");
            ToastBurst<String> trickle = new ToastBurst<>();
            for (int i = 0; i < 5; i++) trickle.offer(i, "t" + i);
            var merged = trickle.poll(4 + ToastBurst.QUIET);
            h.assertTrue(merged.size() == 1 && merged.get(0).items().size() == 5, "a trickle merges: " + merged);
            h.succeed();
        });

        // SPEC §26: the director's pace. A burst of paced messages comes out one at a time, a gap apart, at most the
        // burst limit per window; same-topic messages waiting merge into one
        AwakenTests.register("notice_pace_gap_and_burst", 20, h -> {
            Pace.Rules r = Pace.Rules.of(Pace.Density.STANDARD);
            Pace<String> pace = new Pace<>(r);
            for (int i = 0; i < 6; i++) pace.offer(0, "m" + i, Notices.Tier.IMPORTANT, "", false);
            List<Long> shownAt = new ArrayList<>();
            for (long t = 0; t < r.window(); t++) {
                for (Pace.Out<String> o : pace.poll(t, false, false)) if (o.act() == Pace.Act.SHOW) shownAt.add(t);
            }
            h.assertTrue(!shownAt.isEmpty() && shownAt.get(0) == 0, "the first shows at once: " + shownAt);
            for (int i = 1; i < shownAt.size(); i++) h.assertTrue(shownAt.get(i) - shownAt.get(i - 1) >= r.gap(), "a gap between messages: " + shownAt);
            h.assertTrue(shownAt.size() == r.burst(), "at most " + r.burst() + " in a window: " + shownAt);
            // calm exploration waits longer
            Pace<String> calm = new Pace<>(r);
            calm.offer(0, "a", Notices.Tier.IMPORTANT, "", false);
            calm.offer(0, "b", Notices.Tier.IMPORTANT, "", false);
            long second = -1;
            for (long t = 0; t < 2 * r.calmGap() && second < 0; t++) {
                for (Pace.Out<String> o : calm.poll(t, false, true)) if (o.act() == Pace.Act.SHOW && o.first().equals("b")) second = t;
            }
            h.assertTrue(second >= r.calmGap(), "the calm gap applies: " + second);
            // held: nothing paced shows
            Pace<String> held = new Pace<>(r);
            held.offer(0, "x", Notices.Tier.IMPORTANT, "", false);
            for (long t = 0; t < 100; t++) h.assertTrue(held.poll(t, true, false).isEmpty(), "held at " + t);
            // a topic merges while waiting
            Pace<String> topic = new Pace<>(r);
            topic.offer(0, "first", Notices.Tier.IMPORTANT, "", false);
            topic.poll(0, false, false);
            for (int i = 0; i < 3; i++) topic.offer(1, "craft" + i, Notices.Tier.IMPORTANT, "craft", false);
            Pace.Out<String> merged = null;
            for (long t = 1; t < r.gap() + 5 && merged == null; t++) {
                for (Pace.Out<String> o : topic.poll(t, false, false)) if (o.act() == Pace.Act.SHOW) merged = o;
            }
            h.assertTrue(merged != null && merged.items().size() == 3, "three of a topic arrive as one: " + merged);
            h.succeed();
        });

        AwakenTests.register("notice_critical_bypasses_queue", 20, h -> {
            Pace<String> pace = new Pace<>(Pace.Rules.of(Pace.Density.MINIMAL));
            for (int i = 0; i < 4; i++) pace.offer(0, "info" + i, Notices.Tier.IMPORTANT, "", true);
            pace.poll(0, false, false); // one moment takes the screen
            pace.offer(1, "danger", Notices.Tier.CRITICAL, "", false);
            List<Pace.Out<String>> out = pace.poll(1, true, false); // even held, even right after another
            h.assertTrue(out.size() == 1 && out.get(0).act() == Pace.Act.SHOW && out.get(0).first().equals("danger"),
                    "a critical message shows at once: " + out);
            h.assertTrue(pace.free(1, Notices.Tier.CRITICAL, true, false), "critical is always free");
            h.assertFalse(pace.free(1, Notices.Tier.IMPORTANT, false, false), "a paced one is not, right after");
            h.succeed();
        });

        AwakenTests.register("notice_ambient_files_to_journal", 20, h -> {
            Pace.Rules r = Pace.Rules.of(Pace.Density.STANDARD);
            Pace<String> pace = new Pace<>(r);
            for (int i = 0; i < 5; i++) pace.offer(0, "find" + i, Notices.Tier.AMBIENT, "creature", false);
            int filed = 0, shown = 0;
            for (Pace.Out<String> o : pace.poll(0, false, false)) {
                if (o.act() == Pace.Act.FILE) filed += o.items().size();
                else shown++;
            }
            h.assertTrue(filed == 5 && shown == 0, "ambient finds never interrupt at Standard: filed " + filed + ", shown " + shown);
            // GUIDED shows them (paced); a moment shows at any density
            Pace<String> guided = new Pace<>(Pace.Rules.of(Pace.Density.GUIDED));
            guided.offer(0, "find", Notices.Tier.AMBIENT, "creature", false);
            h.assertTrue(guided.poll(0, false, false).stream().anyMatch(o -> o.act() == Pace.Act.SHOW), "GUIDED shows ambient finds");
            Pace<String> minimal = new Pace<>(Pace.Rules.of(Pace.Density.MINIMAL));
            minimal.offer(0, "level", Notices.Tier.IMPORTANT, "", false);
            minimal.offer(0, "first", Notices.Tier.IMPORTANT, "", true);
            List<Pace.Out<String>> m = minimal.poll(0, false, false);
            h.assertTrue(m.stream().anyMatch(o -> o.act() == Pace.Act.FILE && o.first().equals("level")), "MINIMAL files the ordinary: " + m);
            h.assertTrue(m.stream().anyMatch(o -> o.act() == Pace.Act.SHOW && o.first().equals("first")), "MINIMAL still shows a first: " + m);
            // what waits too long to still be news is filed, not shown late
            Pace<String> stale = new Pace<>(r);
            stale.offer(0, "old", Notices.Tier.IMPORTANT, "", false);
            boolean filedLate = false;
            for (long t = 0; t <= r.maxWait() + 1; t++) {
                for (Pace.Out<String> o : stale.poll(t, true, false)) if (o.act() == Pace.Act.FILE && o.first().equals("old")) filedLate = t > r.maxWait() - 1;
            }
            h.assertTrue(filedLate, "a message held past its wait goes to the Journal");
            h.succeed();
        });

        AwakenTests.register("signal_story_pager_keeps_every_word", 20, h -> {
            String body = "You just couldn't see them. Now the world has noticed you back: the motes in the grass, the lines over the ore, the thing watching from the trees.";
            StoryPager pager = new StoryPager("The gates were always there.", body);
            h.assertTrue(pager.titleNext(), "title first");
            List<String> words = new ArrayList<>();
            int guard = 0;
            boolean pushed = false;
            while (!pager.done() && guard++ < 50) {
                List<String> chunk = pager.next(150, SignalText::approxWidth);
                h.assertTrue(!chunk.isEmpty() && chunk.size() <= StoryPager.MAX_LINES, "1-2 lines per subtitle");
                for (String l : chunk) h.assertTrue(SignalText.approxWidth(l) <= 150, "line fits: " + l);
                if (!pushed && guard == 2) { // the width changes under a chunk: put it back and cut it narrower
                    pager.pushBack(chunk, false);
                    pushed = true;
                    continue;
                }
                for (String l : chunk) words.addAll(List.of(l.split(" ")));
            }
            String all = "The gates were always there. " + body;
            h.assertTrue(String.join(" ", words).equals(all), "every word, in order: " + String.join(" ", words));
            h.succeed();
        });

        AwakenTests.register("signal_pending_choices_lifecycle", 60, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            int before = ANSWERS.get();
            Signal.send(p, choiceWindow("signaltest:one", 20 * 60));
            Signal.send(p, choiceWindow("signaltest:one", 20 * 60));
            Signal.send(p, choiceWindow("signaltest:two", 20 * 60));
            h.assertTrue(Signal.pending(p).size() == 2, "newest copy replaces, got " + Signal.pending(p).size());
            Signal.answer(p, "signaltest:one", true);
            h.assertTrue(!Signal.isPending(p, "signaltest:one") && ANSWERS.get() == before + 1, "answered and routed");
            h.assertTrue(Signal.withdraw(p, "signaltest:two") && Signal.pending(p).isEmpty(), "withdrawn");
            Signal.answer(p, "signaltest:loose", false);
            h.assertTrue(ANSWERS.get() == before + 2, "answers for non-pending actions still reach their handler");
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("info")).build());
            h.assertTrue(Signal.pending(p).isEmpty(), "information never waits");
            Signal.send(p, choiceWindow("signaltest:short", 20));
            h.assertTrue(Signal.isPending(p, "signaltest:short"), "pending at first");
            h.succeedWhen(() -> h.assertFalse(Signal.isPending(p, "signaltest:short"), "expires with its ttl"));
        });

        AwakenTests.register("signal_pending_survives_relog", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Signal.send(p, choiceWindow("signaltest:relog", 20 * 600));
            var level = h.getLevel();
            // the attachment codec on its own
            PendingChoices.Data data = p.getData(PendingChoices.PENDING);
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var tag = PendingChoices.Data.MAP_CODEC.codec().encodeStart(ops, data).getOrThrow();
            PendingChoices.Data back = PendingChoices.Data.MAP_CODEC.codec().parse(ops, tag).getOrThrow();
            h.assertTrue(back.schemaVersion() == PendingChoices.SCHEMA && back.entries().size() == 1
                    && back.entries().get(0).action().equals("signaltest:relog")
                    && back.entries().get(0).window().yesLabel().map(Component::getString).orElse("").equals("Leave with your loot"),
                    "codec round trip keeps the decision and its labels");
            // the whole player, saved and loaded the way a relog does
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            p.saveWithoutId(out);
            CompoundTag saved = out.buildResult();
            saved.remove("UUID");
            ServerPlayer again = h.makeMockServerPlayerInLevel();
            again.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            h.assertTrue(Signal.isPending(again, "signaltest:relog"), "still pending after save and load");
            h.succeed();
        });

        AwakenTests.register("signal_now_sync_only_when_changed", 20, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            UUID id = p.getUUID();
            try {
                TEST_NOW.put(id, List.of(NowHooks.Suggestion.at("signaltest:rift", 95, "Rift tear", BlockPos.ZERO)));
                h.assertTrue(NowSync.update(p), "first collection is sent");
                h.assertFalse(NowSync.update(p), "unchanged: nothing sent");
                List<NowHooks.Suggestion> got = NowHooks.collect(p, NowSync.MAX);
                h.assertTrue(!got.isEmpty() && got.get(0).id().equals("signaltest:rift"), "urgent suggestion first: " + got);
                TEST_NOW.put(id, List.of(NowHooks.Suggestion.at("signaltest:rift", 95, "Rift tear · closing", BlockPos.ZERO)));
                h.assertTrue(NowSync.update(p), "changed text is sent");
                TEST_NOW.remove(id);
                NowSync.update(p);
                h.assertFalse(NowSync.update(p), "an empty list is sent once, then nothing");
            } finally {
                TEST_NOW.remove(id);
                NowSync.forget(id);
            }
            h.succeed();
        });
    }

    private static SignalWindow choice(String action) {
        return choiceWindow(action, 20 * 60);
    }

    private static SignalWindow choiceWindow(String action, int ttl) {
        return SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Leave now?"))
                .body(Component.literal("Floor 1 cleared."))
                .choice(action, "Leave with your loot", "Keep going").ttl(ttl).build();
    }

    /** Every layout rule at every GUI size and scale; returns what broke. */
    static List<String> layoutViolations() {
        List<String> bad = new ArrayList<>();
        for (int[] size : SIZES) {
            int w = size[0], hgt = size[1];
            Rect cross = SignalLayout.crosshair(w, hgt);
            for (double s : SCALES) {
                String at = w + "x" + hgt + "@" + s;
                List<Rect> toasts = new ArrayList<>();
                for (int slot = 0; slot < SignalLayout.TOAST_SLOTS; slot++) {
                    Rect t = SignalLayout.toast(w, slot);
                    check(bad, at + " toast " + slot, t, cross, w, hgt);
                    if (t.w() > 160 || t.h() > 32) bad.add(at + " toast too big " + t);
                    if (slot < SignalLayout.MAX_TOASTS) toasts.add(t);
                }
                for (int q = 1; q <= 2; q++) {
                    for (boolean body : new boolean[]{false, true}) {
                        SignalLayout.Card c = SignalLayout.choice(w, hgt, s, q, body);
                        check(bad, at + " choice " + q + "/" + body, c.rect(), cross, w, hgt);
                        if (c.rect().h() > hgt * SignalLayout.MAX_HEIGHT) bad.add(at + " choice taller than 20% " + c.rect());
                        for (Rect t : toasts) if (c.rect().intersects(t)) bad.add(at + " choice under a toast " + c.rect());
                        storyChecks(bad, at, w, hgt, s, c.rect(), cross);
                    }
                }
                for (int n : new int[]{0, 1, 3, 8, 20}) {
                    for (boolean footer : new boolean[]{false, true}) {
                        SignalLayout.Card p = SignalLayout.panel(w, hgt, s, n, footer);
                        check(bad, at + " panel " + n, p.rect(), cross, w, hgt);
                        if (p.rect().h() > hgt * SignalLayout.MAX_HEIGHT) bad.add(at + " panel taller than 20% " + p.rect());
                        if (p.rect().w() > Math.round(SignalLayout.PANEL_W * s) + 1) bad.add(at + " panel too wide " + p.rect());
                        if (SignalLayout.panelHeight(p.lines(), p.extra()) * p.scale() > p.rect().h() + 1) bad.add(at + " panel content taller than its rect");
                        for (Rect t : toasts) if (p.rect().intersects(t)) bad.add(at + " panel under a toast " + p.rect());
                        storyChecks(bad, at, w, hgt, s, p.rect(), cross);
                    }
                }
                storyChecks(bad, at, w, hgt, s, null, cross);
                for (int lines = 1; lines <= 2; lines++) {
                    for (int textW : new int[]{0, 80, 400, 2000}) {
                        for (boolean lowered : new boolean[]{false, true}) {
                            Rect tr = SignalLayout.tracker(w, hgt, s, textW, lines, lowered);
                            check(bad, at + " tracker", tr, cross, w, hgt);
                            for (Rect t : toasts) if (tr.intersects(t)) bad.add(at + " tracker under a toast " + tr);
                            if (tr.intersects(SignalLayout.choice(w, hgt, s, 2, true).rect())) bad.add(at + " tracker meets the card");
                            // the Horizon line: under the tracker, or in its place when the tracker is empty
                            for (Rect above : new Rect[]{null, tr}) {
                                Rect hz = SignalLayout.horizon(w, hgt, s, textW, above, lowered);
                                check(bad, at + " horizon", hz, cross, w, hgt);
                                for (Rect t : toasts) if (hz.intersects(t)) bad.add(at + " horizon under a toast " + hz);
                                if (hz.intersects(SignalLayout.choice(w, hgt, s, 2, true).rect())) bad.add(at + " horizon meets the card");
                                if (above != null && hz.intersects(above)) bad.add(at + " horizon covers the tracker " + hz);
                            }
                        }
                    }
                }
            }
        }
        return bad;
    }

    private static void storyChecks(List<String> bad, String at, int w, int h, double s, Rect avoid, Rect cross) {
        for (int lines = 1; lines <= 2; lines++) {
            SignalLayout.Card st = SignalLayout.story(w, h, s, lines, avoid);
            check(bad, at + " story " + lines, st.rect(), cross, w, h);
            if (avoid != null && st.rect().intersects(avoid)) bad.add(at + " story under the card " + st.rect() + " vs " + avoid);
            if (SignalLayout.storyTextWidth(st) < 60) bad.add(at + " story too narrow " + st.rect());
        }
    }

    private static void check(List<String> bad, String what, Rect r, Rect cross, int w, int h) {
        if (r.intersects(cross)) bad.add(what + " covers the crosshair " + r);
        if (!r.insideScreen(w, h)) bad.add(what + " leaves the screen " + r);
    }
}
