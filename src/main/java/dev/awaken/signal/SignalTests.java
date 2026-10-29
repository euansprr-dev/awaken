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

        // the clear screen's Now line (SPEC §40.6): 8 s when new, then it fades while you walk toward the place; it comes back
        // when you look more than 70° away for 3 s, stand still 2 s or come within 50 m; it holds 3 s so it never blinks
        AwakenTests.register("hud_now_line_returns", 20, h -> {
            var now = new dev.awaken.signal.ui.CornerShow.NowLine();
            int fresh = dev.awaken.signal.ui.CornerShow.NowLine.FRESH, hold = dev.awaken.signal.ui.CornerShow.NowLine.HOLD;
            int t = 0;
            for (; t < fresh; t++) h.assertTrue(now.tick(t, 5, 400, true, false), "new: shown at " + t);
            for (int i = 0; i < hold; i++, t++) h.assertTrue(now.tick(t, 5, 400, true, false), "held after it went quiet, " + i);
            h.assertFalse(now.tick(t++, 5, 400, true, false), "walking toward it: gone");
            // turned away: back after 3 s, not before
            for (int i = 0; i < dev.awaken.signal.ui.CornerShow.NowLine.TURN_TICKS - 1; i++, t++)
                h.assertFalse(now.tick(t, 120, 400, true, false), "not yet back, " + i);
            h.assertTrue(now.tick(t++, 120, 400, true, false), "turned away 3 s: back");
            // let it go again, then stand still: back after 2 s
            for (int i = 0; i <= hold; i++, t++) now.tick(t, 5, 400, true, false);
            h.assertFalse(now.tick(t++, 5, 400, true, false), "walking on: gone again");
            boolean back = false;
            for (int i = 0; i < dev.awaken.signal.ui.CornerShow.NowLine.STILL_TICKS; i++, t++) back = now.tick(t, 5, 400, false, false);
            h.assertTrue(back, "stood still 2 s: back");
            // near the place it stays; in a fight it steps aside
            for (int i = 0; i < 200; i++, t++) h.assertTrue(now.tick(t, 5, 30, true, false), "within 50 m it stays");
            h.assertFalse(now.tick(t, 5, 30, true, true), "a fight: aside");
            h.succeed();
        });

        // the task card (SPEC §40.6): 8 s after a change; a step that teaches a key stays until the key is pressed, then 8 s
        AwakenTests.register("hud_task_card_teaches", 20, h -> {
            var card = new dev.awaken.signal.ui.CornerShow.TaskCard();
            int show = dev.awaken.signal.ui.CornerShow.TaskCard.SHOW;
            h.assertFalse(card.tick(false, false, false), "nothing changed: hidden");
            card.changed();
            for (int i = 0; i < show - 1; i++) h.assertTrue(card.tick(false, false, false), "shown after a change, " + i);
            h.assertFalse(card.tick(false, false, false), "gone at 8 s");
            card.changed();
            for (int i = 0; i < show * 3; i++) h.assertTrue(card.tick(true, false, true), "a lesson stays, even in a fight, " + i);
            h.assertTrue(card.tick(true, true, false), "pressed: still shown");
            for (int i = 0; i < show - 1; i++) h.assertTrue(card.tick(true, false, false), "8 s from the press, " + i);
            h.assertFalse(card.tick(true, false, false), "then gone");
            card.changed();
            h.assertFalse(card.tick(false, false, true), "a plain change steps aside in a fight");
            h.succeed();
        });

        // the task card wraps a long step instead of cutting it (Euan, 2026-10-27): no line wider than the card, the words and the
        // key kept in order, the count and compass on the last line or one of their own, an ellipsis only past the last line
        AwakenTests.register("signal_task_wrap", 20, h -> {
            java.util.function.ToIntFunction<String> w = str -> str.length() * 5;
            String long1 = "Run at a fence or a wall and vault it, then keep your speed on the far side.";
            var lines = dev.awaken.signal.ui.TaskWrap.wrap(long1, null, 0, "", 140, 3, w);
            h.assertTrue(lines.size() >= 2 && lines.size() <= 3 && lines.stream().allMatch(l -> l.stream().mapToInt(dev.awaken.signal.ui.TaskWrap.Seg::w).sum() <= 140), "a long step wraps within the card: " + lines);
            String joined = lines.stream().flatMap(l -> l.stream()).map(dev.awaken.signal.ui.TaskWrap.Seg::text).reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
            h.assertTrue(joined.equals(long1), "and keeps every word in order: '" + joined + "'");
            var keyed = dev.awaken.signal.ui.TaskWrap.wrap("Hold {key} while sprinting to slide.", "Left Shift", 70, "", 120, 3, w);
            long caps = keyed.stream().flatMap(l -> l.stream()).filter(x -> x.kind() == dev.awaken.signal.ui.TaskWrap.KEY).count();
            h.assertTrue(caps == 1 && keyed.size() >= 2 && keyed.stream().allMatch(l -> l.stream().mapToInt(dev.awaken.signal.ui.TaskWrap.Seg::w).sum() <= 120), "a key cap sits in the line like a word: " + keyed);
            var one = dev.awaken.signal.ui.TaskWrap.wrap("Build a room.", null, 0, "2/5", 200, 3, w);
            h.assertTrue(one.size() == 1 && one.getFirst().getLast().kind() == dev.awaken.signal.ui.TaskWrap.TAIL, "the tail follows a short step on its line");
            var own = dev.awaken.signal.ui.TaskWrap.wrap("Build a room with a bed in it.", null, 0, "  \u2197 120 m", 150, 3, w);
            h.assertTrue(own.getLast().size() == 1 && own.getLast().getFirst().kind() == dev.awaken.signal.ui.TaskWrap.TAIL, "a tail that does not fit has a line of its own: " + own);
            var cut = dev.awaken.signal.ui.TaskWrap.wrap(long1, null, 0, "", 60, 2, w);
            var last = cut.getLast().getLast();
            h.assertTrue(cut.size() == 2 && last.text().endsWith("\u2026") && cut.getLast().stream().mapToInt(dev.awaken.signal.ui.TaskWrap.Seg::w).sum() <= 60, "past the last line it ends in an ellipsis that fits: " + cut);
            var word = dev.awaken.signal.ui.TaskWrap.wrap("Supercalifragilisticexpialidocious", null, 0, "", 50, 3, w);
            h.assertTrue(word.size() == 1 && word.getFirst().getFirst().w() <= 50, "one word wider than the card is cut: " + word);
            h.succeed();
        });

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
            List<String> tabs = List.of("Status", "Powers", "Journal", "Truths", "Codex", "Map"); // the band's pages (SPEC §30)
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
                    "Flow", "Kinetic", "Truths", "Directive", "Latents")) {
                h.assertTrue(Glossary.lookup(w).isPresent(), "glossary explains " + w);
            }
            h.assertTrue(Glossary.lookup("Echoes").isEmpty(), "Echoes are gone (SPEC §43.4), so the glossary doesn't explain them");
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

        // SPEC §26.3 (Euan, 2026-10-29): a find made on purpose shows the moment it's made, not a calm gap later; it
        // waits only while nothing can show, and a world's settling minutes don't hold it
        AwakenTests.register("notice_find_shows_at_once", 20, h -> {
            Pace.Rules r = Pace.Rules.of(Pace.Density.STANDARD);
            Pace<String> pace = new Pace<>(r);
            pace.offer(0, "level", Notices.Tier.IMPORTANT, "", false);
            pace.poll(0, false, false, true); // something just took the screen
            pace.offer(1, "slow", Notices.Tier.IMPORTANT, "creature", false);
            pace.offer(2, "glimmerfish", Notices.Tier.IMPORTANT, "creature", false, true);
            List<Pace.Out<String>> out = pace.poll(2, false, false, true);
            h.assertTrue(out.size() == 1 && out.get(0).act() == Pace.Act.SHOW && out.get(0).first().equals("glimmerfish")
                    && out.get(0).items().size() == 1, "the find shows at once, ahead of an older paced one: " + out);
            for (long t = 3; t < r.calmGap(); t++) h.assertTrue(pace.poll(t, false, false, true).isEmpty(), "the paced one keeps its gap at " + t);
            h.assertTrue(pace.poll(2 + r.calmGap(), false, false, true).stream().anyMatch(o -> o.first().equals("slow")), "then the paced one");
            // settling: only the immediate shows; blocked (a screen, a fight): it waits, then goes first
            Pace<String> settling = new Pace<>(r);
            settling.offer(0, "paced", Notices.Tier.IMPORTANT, "", false);
            settling.offer(0, "lesson", Notices.Tier.IMPORTANT, "creature", true, true);
            h.assertTrue(settling.poll(0, true, true, true).isEmpty(), "blocked: nothing shows");
            out = settling.poll(1, false, true, true);
            h.assertTrue(out.size() == 1 && out.get(0).moment() && out.get(0).first().equals("lesson"), "settling: the lesson shows: " + out);
            for (long t = 2; t < 200; t++) h.assertTrue(settling.poll(t, false, true, true).isEmpty(), "settling holds the paced one at " + t);
            // the burst limit doesn't hold it either
            Pace<String> burst = new Pace<>(r);
            for (int i = 0; i < r.burst(); i++) burst.mark(i);
            burst.offer(5, "find", Notices.Tier.IMPORTANT, "creature", false, true);
            h.assertTrue(burst.poll(5, false, false, false).size() == 1, "a full burst doesn't hold it");
            // only a find made on purpose is immediate, and the flag survives the wire
            var looked = Finds.now(SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Glimmerfish")).tier(Notices.Tier.IMPORTANT).moment(),
                    Finds.Find.of("creature", "New creature · Glimmerfish", "Glimmerfish", "", 0xFFFFFF, true));
            var passed = Finds.now(SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Commons")).tier(Notices.Tier.IMPORTANT),
                    Finds.Find.of("biome", "New biome · Commons", "Commons", "", 0xFFFFFF, false));
            h.assertTrue(looked.immediate() && !passed.immediate(), "intent finds are immediate, the rest paced");
            var decoded = SignalWindow.CODEC.parse(NbtOps.INSTANCE, SignalWindow.CODEC.encodeStart(NbtOps.INSTANCE, looked).getOrThrow()).getOrThrow();
            h.assertTrue(decoded.immediate() && decoded.moment() && !decoded.fleeting(), "the flags survive the codec");
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

        AwakenTests.register("signal_dialogue_use_once", 20, h -> {
            var press = new dev.awaken.core.api.Dialogue.Press();
            var advance = dev.awaken.core.api.Dialogue.Press.Result.ADVANCE;
            var consume = dev.awaken.core.api.Dialogue.Press.Result.CONSUME;
            var pass = dev.awaken.core.api.Dialogue.Press.Result.PASS;
            h.assertTrue(press.use(true, false) == pass, "first NPC interaction reaches the server");
            press.capture();
            h.assertTrue(press.use(true, false) == consume, "holding cannot resend while its reply is in flight");
            press.release();
            h.assertTrue(press.use(true, false) == consume, "a fast second click waits for the server reply instead of restarting");
            h.assertTrue(press.use(false, true) == consume && press.use(true, true) == consume, "offhand and held reply do not skip its first line");
            press.release();
            StoryPager speech = new StoryPager("", "Hale: Take your time. Read the next line before taking the weapon.");
            List<String> first = speech.next(80, StoryPager.MAX_LINES, SignalText::approxWidth);
            int chunks = speech.chunksTaken();
            h.assertTrue(press.use(true, true) == advance, "fresh press advances");
            speech.next(80, StoryPager.MAX_LINES, SignalText::approxWidth);
            h.assertTrue(speech.chunksTaken() == chunks + 1 && !first.isEmpty(), "exactly one subtitle chunk advances");
            h.assertTrue(press.use(false, true) == consume && press.use(true, true) == consume, "neither second hand nor held repeats advance again");
            h.assertTrue(press.use(true, false) == consume, "last line cannot fall through into item use or conversation restart");
            press.release();
            h.assertTrue(press.use(true, false) == pass, "a new click after the conversation can interact normally");
            press.capture();
            press.release();
            for (int i = 0; i < 20; i++) press.tick();
            h.assertTrue(press.use(true, false) == pass, "a speaker with no reply cannot leave use locked");
            // the System's speech takes use only inside a conversation the player started (no sources on a server)
            dev.awaken.core.api.Dialogue.reset();
            h.assertFalse(dev.awaken.core.api.Dialogue.talking(), "speech that just arrives never takes the use key");
            dev.awaken.core.api.Dialogue.capture();
            h.assertTrue(dev.awaken.core.api.Dialogue.talking(), "using on a speaker opens a conversation");
            for (int i = 0; i < 100; i++) dev.awaken.core.api.Dialogue.tick();
            h.assertFalse(dev.awaken.core.api.Dialogue.talking(), "a conversation with no speech closes after its reply time");
            dev.awaken.core.api.Dialogue.reset();
            h.assertTrue(SignalWindow.Presentation.resolve(SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Hale: Hold on."))
                    .dialogue().build()) == SignalWindow.Presentation.STORY, "a spoken line is shown as speech");
            var choice = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Hale"))
                    .dialogue().choice("signaltest:weapon", "Take it", "Wait").build();
            var deck = new ChoiceDeck();
            deck.offer(choice, 200);
            h.assertTrue(choice.dialogue() && SignalWindow.Presentation.resolve(choice) == SignalWindow.Presentation.CHOICE
                    && deck.pending().size() == 1, "NPC speech marking preserves the explicit choice");
            var encoded = SignalWindow.CODEC.encodeStart(NbtOps.INSTANCE, choice).getOrThrow();
            var decoded = SignalWindow.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
            h.assertTrue(decoded.dialogue() && decoded.action().equals(choice.action()), "dialogue/choice semantics survive network codec");
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
                List<String> chunk = pager.next(150, 2, SignalText::approxWidth);
                h.assertTrue(!chunk.isEmpty() && chunk.size() <= 2, "1-2 lines per subtitle: " + chunk);
                for (String l : chunk) h.assertTrue(SignalText.approxWidth(l) <= 150, "line fits: " + l);
                if (!pushed && guard == 2) { // the width changes under a chunk: put it back and cut it narrower
                    pager.pushBack();
                    pushed = true;
                    continue;
                }
                String page = String.join(" ", chunk);
                // a page never stops mid-clause: it ends a sentence, or (a sentence longer than a page) at a comma or colon
                h.assertTrue(page.matches(".*[.!?,:;]$"), "a page ends at a sentence or clause: " + page);
                words.addAll(List.of(page.split(" ")));
            }
            String all = "The gates were always there. " + body;
            h.assertTrue(String.join(" ", words).equals(all), "every word, in order: " + String.join(" ", words));
            // with room, a page is whole sentences together and none is cut
            StoryPager wide = new StoryPager("", "Oren: I'm Oren, the smith. Most hunters buy cheap blades off a cart. They snap.");
            List<String> one = wide.next(290, 3, SignalText::approxWidth);
            h.assertTrue(wide.done() && String.join(" ", one).endsWith("They snap."), "short sentences share a page: " + one);
            h.succeed();
        });

        AwakenTests.register("signal_choice_waits_for_speech", 20, h -> {
            ChoiceDeck deck = new ChoiceDeck();
            deck.offer(choice("signaltest:offer"), 20 * 60, true); // asked while the giver is still talking
            for (int i = 0; i < 300; i++) {
                deck.tick(0, 0, false, true);
                h.assertTrue(deck.current() == null, "no card while the speech goes on (tick " + i + ")");
            }
            for (int i = 0; i < ChoiceDeck.GAP_TICKS - 1; i++) deck.tick(0, 0, false, false);
            h.assertTrue(deck.current() == null, "a beat after the last line first");
            deck.tick(0, 0, false, false);
            deck.tick(0, 0, false, false);
            h.assertTrue(deck.current() != null && deck.current().action.equals("signaltest:offer"), "the card comes once they've finished");
            // a warning never waits; a card asked in silence doesn't either
            ChoiceDeck urgent = new ChoiceDeck();
            urgent.offer(SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Run?")).choice("signaltest:warn", "Yes", "No").ttl(600).build(), 600, true);
            urgent.tick(0, 0, false, true);
            h.assertTrue(urgent.current() != null, "a warning shows over speech");
            ChoiceDeck quiet = new ChoiceDeck();
            quiet.offer(choice("signaltest:quiet"), 600, false);
            quiet.tick(0, 0, false, true);
            h.assertTrue(quiet.current() != null, "a card asked before the speech isn't held back by it");
            // speech that never ends can't hide a card for good
            ChoiceDeck stuck = new ChoiceDeck();
            stuck.offer(choice("signaltest:stuck"), ChoiceDeck.SPEECH_WAIT_TICKS + 100, true);
            for (int i = 0; i < ChoiceDeck.SPEECH_WAIT_TICKS + 2; i++) stuck.tick(0, 0, false, true);
            h.assertTrue(stuck.current() != null, "a card waits for speech at most a minute");
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

        // SPEC §27.11: the corner, top left, shows at most one place: the destination you chose, else the task step's
        AwakenTests.register("signal_corner_nothing", 20, h -> {
            var dim = net.minecraft.resources.Identifier.withDefaultNamespace("overworld");
            h.assertTrue(NowCorner.of(null, dev.awaken.progression.Checklist.payload(null, dim)) == null, "no task, no destination: nothing");
            var plain = new dev.awaken.progression.Checklist.Task("t", "Errand", List.of(
                    dev.awaken.progression.Checklist.Row.of("Done already", true), dev.awaken.progression.Checklist.Row.of("Craft a rope", false)));
            h.assertTrue(NowCorner.of(null, dev.awaken.progression.Checklist.payload(plain, dim)) == null, "a step with no place: nothing");
            var ledge = new dev.awaken.progression.Checklist.Task("moves", "Your body remembers", List.of(
                    new dev.awaken.progression.Checklist.Row("Press {key} at a ledge to climb it", "key.jump", false, 0, 0, new BlockPos(3, 64, 3), "")));
            h.assertTrue(NowCorner.of(null, dev.awaken.progression.Checklist.payload(ledge, dim)) == null, "a hint nearby (a ledge) is the card's own: nothing");
            h.succeed();
        });

        AwakenTests.register("signal_corner_task_place", 20, h -> {
            var dim = net.minecraft.resources.Identifier.withDefaultNamespace("overworld");
            BlockPos camp = new BlockPos(120, 70, -400);
            var task = new dev.awaken.progression.Checklist.Task("chain:x", "The Last Delivery", List.of(
                    new dev.awaken.progression.Checklist.Row("Leave the city through the High Gate", "", true, 0, 0, null, "High Gate"),
                    new dev.awaken.progression.Checklist.Row("Take the letters to the Wayfarer camp", "", false, 0, 0, camp, "Wayfarer camp"),
                    dev.awaken.progression.Checklist.Row.of("Deliver the letters", false)));
            NowCorner.Place at = NowCorner.of(null, dev.awaken.progression.Checklist.payload(task, dim));
            h.assertTrue(at != null && !at.destination() && at.name().equals("Wayfarer camp") && at.at().equals(camp) && at.dimension().equals(dim),
                    "the current step's place, named, where the task was sent: " + at);
            var unnamed = new dev.awaken.progression.Checklist.Task("trail:x", "A trail", List.of(
                    new dev.awaken.progression.Checklist.Row("Find the Well", "", false, 0, 0, camp)));
            at = NowCorner.of(null, dev.awaken.progression.Checklist.payload(unnamed, dim));
            h.assertTrue(at != null && at.name().equals("Find the Well"), "a step with a place and no name says its own words: " + at);
            h.succeed();
        });

        AwakenTests.register("signal_corner_destination_wins", 20, h -> {
            var dim = net.minecraft.resources.Identifier.withDefaultNamespace("overworld");
            var task = new dev.awaken.progression.Checklist.Task("chain:x", "Errand", List.of(
                    new dev.awaken.progression.Checklist.Row("Go to the camp", "", false, 0, 0, new BlockPos(5, 64, 5), "Wayfarer camp")));
            var gate = new dev.awaken.core.api.Destinations.Target("E-rank gate", dim, new BlockPos(-300, 64, 90), "gate");
            NowCorner.Place at = NowCorner.of(gate, dev.awaken.progression.Checklist.payload(task, dim));
            h.assertTrue(at != null && at.destination() && at.name().equals("E-rank gate") && at.at().equals(gate.at()), "the destination you chose wins: " + at);
            h.succeed();
        });

        AwakenTests.register("signal_destination_arrival", 20, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            var dim = p.level().dimension().identifier();
            BlockPos feet = p.blockPosition();
            dev.awaken.core.api.Destinations.set(p, new dev.awaken.core.api.Destinations.Target("Far off", dim, feet.offset(60, 0, 0), "spot"));
            h.assertFalse(Destination.check(p), "60 blocks off: not there yet");
            h.assertTrue(dev.awaken.core.api.Destinations.get(p).isPresent(), "and still set");
            dev.awaken.core.api.Destinations.set(p, new dev.awaken.core.api.Destinations.Target("Over your head", dim, feet.offset(2, 80, 0), "mark"));
            h.assertFalse(Destination.check(p), "right over you but 80 up: not there");
            dev.awaken.core.api.Destinations.set(p, new dev.awaken.core.api.Destinations.Target("Close by", dim,
                    feet.offset(dev.awaken.core.api.Destinations.ARRIVED - 4, 3, 5), "gate"));
            h.assertTrue(Destination.check(p), "within " + dev.awaken.core.api.Destinations.ARRIVED + " blocks: arrived");
            h.assertTrue(dev.awaken.core.api.Destinations.get(p).isEmpty(), "and it cleared itself");
            dev.awaken.core.api.Destinations.set(p, new dev.awaken.core.api.Destinations.Target("A rumour", dim, feet.offset(3, 120, 3), "rumour"));
            h.assertTrue(Destination.check(p), "a rumour's height is a guess: across is enough");
            var other = net.minecraft.resources.Identifier.withDefaultNamespace("the_nether");
            dev.awaken.core.api.Destinations.set(p, new dev.awaken.core.api.Destinations.Target("Elsewhere", other, feet, "spot"));
            h.assertFalse(Destination.check(p), "the same block in another dimension isn't there");
            dev.awaken.core.api.Destinations.clear(p);
            h.succeed();
        });

        AwakenTests.register("signal_destination_saved", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            var level = h.getLevel();
            BlockPos at = p.blockPosition().offset(200, 0, -150);
            // the Atlas's request: named, in the dimension you stand in, an unknown kind read as a place
            Destination.request(p, new dev.awaken.signal.net.SignalPayloads.SetDestination(true, "  Mossbank  ", at, "nonsense"));
            var set = dev.awaken.core.api.Destinations.get(p).orElse(null);
            h.assertTrue(set != null && set.name().equals("Mossbank") && set.at().equals(at) && set.kind().equals("place")
                    && set.dimension().equals(level.dimension().identifier()), "the request sets it: " + set);
            // the attachment codec on its own
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var tag = Destination.Data.MAP_CODEC.codec().encodeStart(ops, p.getData(Destination.DATA)).getOrThrow();
            Destination.Data back = Destination.Data.MAP_CODEC.codec().parse(ops, tag).getOrThrow();
            h.assertTrue(back.schemaVersion() == Destination.SCHEMA && back.target().map(t -> t.name().equals("Mossbank") && t.at().equals(at)).orElse(false),
                    "codec round trip keeps it: " + back);
            // the whole player, saved and loaded the way a relog does
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            p.saveWithoutId(out);
            CompoundTag saved = out.buildResult();
            saved.remove("UUID");
            ServerPlayer again = h.makeMockServerPlayerInLevel();
            again.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            var loaded = dev.awaken.core.api.Destinations.get(again).orElse(null);
            h.assertTrue(loaded != null && loaded.equals(set), "still set after save and load: " + loaded);
            Destination.request(again, new dev.awaken.signal.net.SignalPayloads.SetDestination(false, "", BlockPos.ZERO, ""));
            h.assertTrue(dev.awaken.core.api.Destinations.get(again).isEmpty(), "Clear destination clears it");
            Destination.request(p, new dev.awaken.signal.net.SignalPayloads.SetDestination(true, "   ", at, "spot"));
            h.assertTrue(dev.awaken.core.api.Destinations.get(p).map(t -> t.name().equals("Mossbank")).orElse(false), "a nameless request changes nothing");
            dev.awaken.core.api.Destinations.clear(p);
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
                for (int q = 1; q <= 2; q++) { // an offer: its body says in full what you agree to
                    SignalLayout.Card c = SignalLayout.choice(w, hgt, s, q, SignalLayout.CHOICE_BODY_LINES);
                    check(bad, at + " offer " + q, c.rect(), cross, w, hgt);
                    if (c.bodyLines() < SignalLayout.CHOICE_BODY_LINES) bad.add(at + " offer drops body lines " + c.bodyLines());
                    if (c.rect().bottom() > hgt - SignalLayout.POWER_BAR_H) bad.add(at + " offer reaches the power bar " + c.rect());
                    if (c.rect().h() > hgt * SignalLayout.MAX_HEIGHT_BODY + 1) bad.add(at + " offer taller than 30% " + c.rect());
                    if (SignalLayout.choiceHeight(c.lines(), c.bodyLines()) * c.scale() > c.rect().h() + 1) bad.add(at + " offer content taller than its rect");
                    for (Rect t : toasts) if (c.rect().intersects(t)) bad.add(at + " offer under a toast " + c.rect());
                    storyChecks(bad, at, w, hgt, s, c.rect(), cross);
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
            stackChecks(bad, w, hgt);
        }
        return bad;
    }

    /**
     * The top-right stack (SPEC §27.10): the run panel under the task card (or in the corner without one) never meets
     * the card, the System's toasts or a boss bar, and stays on the screen.
     */
    private static void stackChecks(List<String> bad, int w, int hgt) {
        for (int bars : new int[]{0, 17, 36, 55}) {
            Rect boss = new Rect(w / 2 - 91, 0, 182, bars);
            for (int toasts = 0; toasts <= SignalLayout.MAX_TOASTS; toasts++) {
                float top = SignalLayout.MARGIN + toasts * SignalLayout.TOAST_H;
                Rect toastsRect = new Rect(w - SignalLayout.TOAST_W, 0, SignalLayout.TOAST_W, toasts * SignalLayout.TOAST_H);
                for (int cardW : new int[]{0, 60, 110}) {
                    int cardH = cardW == 0 ? 0 : 40;
                    for (int runW : new int[]{50, 70, 110}) {
                        String at = w + "x" + hgt + " bars " + bars + " toasts " + toasts + " card " + cardW + " run " + runW;
                        float cy = SignalLayout.clearBossBars(top, w, cardW, bars);
                        Rect card = new Rect(w - SignalLayout.MARGIN - cardW, Math.round(cy), cardW, cardH);
                        float ry = SignalLayout.stackBelow(top, cardW, cardH, w, runW, bars);
                        Rect run = new Rect(w - SignalLayout.MARGIN - runW, Math.round(ry), runW, 35);
                        if (run.intersects(card)) bad.add(at + ": the run panel covers the task card " + run + " vs " + card);
                        if (run.intersects(toastsRect)) bad.add(at + ": the run panel covers a toast " + run);
                        if (bars > 0 && run.intersects(boss)) bad.add(at + ": the run panel covers a boss bar " + run);
                        if (bars > 0 && card.intersects(boss)) bad.add(at + ": the task card covers a boss bar " + card);
                        if (run.x() < 0 || run.right() > w) bad.add(at + ": the run panel leaves the screen " + run);
                        if (cardW == 0 && bars == 0 && Math.round(ry) != Math.round(top)) bad.add(at + ": the run panel doesn't take the empty corner " + run);
                    }
                }
            }
        }
    }

    private static void storyChecks(List<String> bad, String at, int w, int h, double s, Rect avoid, Rect cross) {
        for (int lines = 1; lines <= StoryPager.maxLines(w, h, s); lines++) {
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
