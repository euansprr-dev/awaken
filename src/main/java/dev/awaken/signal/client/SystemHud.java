package dev.awaken.signal.client;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.CoreModule;
import dev.awaken.core.api.Notices;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.signal.SignalWindow;
import dev.awaken.signal.net.SignalPayloads;
import dev.awaken.signal.ui.ChoiceDeck;
import dev.awaken.signal.ui.Rect;
import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.SignalText;
import dev.awaken.signal.ui.StoryPager;
import dev.awaken.signal.ui.ToastBurst;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * System v2 on screen: toasts (through vanilla's ToastManager), one choice card at a time, gate panels in the same
 * slot, and story subtitles above the hotbar. Positions come from {@link SignalLayout}; key rules from
 * {@link ChoiceDeck}. Nothing is dropped: whatever can't be shown in full is in the {@link RecentLog}.
 */
public final class SystemHud {
    private SystemHud() {}

    static final int OPEN_TICKS = 4, CLOSE_TICKS = 6, STORY_FADE = StoryPager.FADE_TICKS;
    private static final int MAX_STORIES = 8, MAX_PANELS = 2;

    record ToastItem(String title, String detail, int colour, boolean journal) {
        ToastItem(String title, String detail, int colour) { this(title, detail, colour, false); }
    }

    private static final class Story {
        final SignalWindow w;
        final StoryPager pager;
        List<String> lines = List.of();
        boolean titleChunk;
        int age, typeTicks, holdTicks, width;
        int closingAt = -1;
        boolean first = true;

        Story(SignalWindow w) {
            this.w = w;
            this.pager = w.dialogue()
                    ? new StoryPager("", w.title().getString() + w.body().map(b -> ": " + b.getString()).orElse("")
                            + w.footer().map(f -> " " + f.getString()).orElse(""))
                    : new StoryPager(w.title().getString(), w.body().map(Component::getString).orElse(""));
        }
    }

    private static final class Panel {
        final SignalWindow w;
        int age;
        int closingAt = -1;

        Panel(SignalWindow w) { this.w = w; }
    }

    static final ChoiceDeck DECK = new ChoiceDeck();
    private static final ToastBurst<ToastItem> BURST = new ToastBurst<>();
    private static final Deque<ToastBurst.Out<ToastItem>> TOAST_WAIT = new ArrayDeque<>();
    private static final List<long[]> LIVE_TOASTS = new ArrayList<>(); // {deadline tick}
    private static final Deque<SignalWindow> STORIES = new ArrayDeque<>();
    private static final Deque<SignalWindow> PANELS = new ArrayDeque<>();
    private static Story story;
    private static Panel panel;
    private static String lastCard = "";
    private static int cardAge;
    private static long ticks;
    private static long lastHurtTick = -1000;
    private static boolean hostileNear;

    // ---- intake ---------------------------------------------------------------------------------------------------

    /** How a window is shown in V2. A decision always gets its card and keys; a "choice" with no action is info. */
    static SignalWindow.Presentation effective(SignalWindow w) {
        if (w.action().isPresent()) return SignalWindow.Presentation.CHOICE;
        SignalWindow.Presentation p = SignalWindow.Presentation.resolve(w);
        return p == SignalWindow.Presentation.CHOICE ? SignalWindow.Presentation.TOAST : p;
    }

    static void show(SignalWindow w) {
        RecentLog.add(w);
        SignalWindow.Presentation p = effective(w);
        boolean critical = w.tier() == Notices.Tier.CRITICAL;
        switch (p) {
            case CHOICE -> {
                Herald.log("card", w.tier(), w.topic(), w.title().getString());
                DECK.offer(w, w.ttlTicks(), speaking()); // a card asked while someone talks waits for them to finish
                Herald.took();
                // a revelation that asks something ("Will you accept?") is also spoken in full beside its card
                if (w.kind() == SignalWindow.Kind.REVELATION) STORIES.addLast(w);
            }
            case STORY -> {
                STORIES.addLast(w);
                while (STORIES.size() > MAX_STORIES) STORIES.removeFirst(); // kept in the log and the Codex
                Herald.took();
            }
            case PANEL -> {
                if (!critical && !Herald.responding(w)) Herald.offer(w); // an info card is paced like a toast (SPEC §26)
                else if (inCombat() || DECK.current() != null) BURST.offer(ticks, item(w));
                else panelNow(w);
            }
            default -> {
                // decisions' neighbours and answers to what the player just did show at once; the rest are paced
                if (critical || Herald.responding(w)) {
                    Herald.log(critical ? "critical" : "response", w.tier(), w.topic(), w.title().getString());
                    BURST.offer(ticks, item(w));
                    if (!critical) Herald.took();
                } else {
                    Herald.offer(w);
                }
            }
        }
        // a decision's card plays its own sound when it opens
        if (w.kind() == SignalWindow.Kind.WARNING && w.action().isEmpty()) play(CoreModule.SIGNAL_WARNING.get(), 0.6f);
    }

    static void toast(Component text, int colour, Notices.Tier tier, String topic) {
        RecentLog.add(text);
        boolean response = tier == Notices.Tier.IMPORTANT && topic.isEmpty() && Herald.responding();
        if (tier == Notices.Tier.CRITICAL || response) {
            Herald.log(response ? "response" : "critical", tier, topic, text.getString());
            BURST.offer(ticks, new ToastItem(text.getString(), "", colour));
        }
        else Herald.offer(text, tier, topic, colour);
        if (response) Herald.took();
    }

    /** Already saved by the server: a small explanation, never an automatic panel or dialogue. */
    static void event(dev.awaken.core.api.NoticeEvents.Event event) {
        BURST.offer(ticks, new ToastItem(event.title(), event.summary(), event.colour(), true));
    }

    static void tickSavedEvents() {
        ticks++;
        tickToasts(Minecraft.getInstance());
    }

    /** An info panel the director let through. */
    static void panelNow(SignalWindow w) {
        PANELS.addLast(w);
        while (PANELS.size() > MAX_PANELS) PANELS.removeFirst();
    }

    /** Toasts the director let through: one, or several of a topic as one ("Mossback · +2"). */
    static void toastNow(List<Herald.Item> items) {
        if (items.size() == 1) {
            Herald.Item i = items.get(0);
            TOAST_WAIT.addLast(new ToastBurst.Out<>(List.of(new ToastItem(i.title(), i.detail(), i.colour()))));
            return;
        }
        List<String> names = new ArrayList<>();
        for (Herald.Item i : items) names.add(i.title());
        Herald.Item first = items.get(0);
        TOAST_WAIT.addLast(new ToastBurst.Out<>(List.of(new ToastItem(first.title() + " · +" + (items.size() - 1), String.join(" · ", names.subList(1, names.size())), first.colour()))));
    }

    /** A choice card or an info panel holds the card slot. */
    static boolean cardUp() {
        return DECK.current() != null || panel != null;
    }

    static void pending(List<SignalPayloads.PendingEntry> entries) {
        List<ChoiceDeck.Entry> list = new ArrayList<>(entries.size());
        for (var e : entries) list.add(new ChoiceDeck.Entry(e.window(), e.ticksLeft()));
        DECK.sync(list);
    }

    private static ToastItem item(SignalWindow w) {
        return new ToastItem(w.title().getString(), SignalText.toastDetail(w), accent(w));
    }

    static int accent(SignalWindow w) {
        TextColor c = w.title().getStyle().getColor();
        return c != null ? c.getValue() & 0xFFFFFF : ClassicHud.edgeFor(w.kind());
    }

    // ---- what the Status screen needs ---------------------------------------------------------------------------

    /** Every decision still waiting, oldest first. */
    public static List<ChoiceDeck.Choice> pendingChoices() {
        return DECK.pending();
    }

    /** Decisions waiting that aren't on screen right now. */
    public static int waiting() {
        return DECK.waiting();
    }

    /** Answers a waiting decision from the Status screen. */
    public static void answer(String action, boolean accepted) {
        ChoiceDeck.Choice c = null;
        for (ChoiceDeck.Choice p : DECK.pending()) if (p.action.equals(action)) c = p;
        ChoiceDeck.Answer a = DECK.answer(action, accepted);
        if (a != null) send(a, c == null ? null : c.window());
    }

    private static void send(ChoiceDeck.Answer a, SignalWindow w) {
        ClientPacketDistributor.sendToServer(new SignalPayloads.Answer(a.action(), a.accepted()));
        if (w != null) RecentLog.answered(a.action(), "You chose: " + (a.accepted() ? SignalText.yesLabel(w) : SignalText.noLabel(w)));
        dev.awaken.core.audio.client.AudioUi.answer(a.accepted());
    }

    public static boolean inCombat() {
        return ticks - lastHurtTick < 100 || hostileNear;
    }

    static double scale() {
        try {
            return SignalLayout.clampScale(AwakenConfig.Client.SYSTEM_SCALE.get());
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    static String key(KeyMapping k) {
        return k.getTranslatedKeyMessage().getString();
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
    }

    /** Drops what is on screen but keeps the pending decisions (they stay in the Status screen). */
    static void clearScreen() {
        BURST.clear();
        TOAST_WAIT.clear();
        STORIES.clear();
        PANELS.clear();
        story = null;
        panel = null;
    }

    static void clear() {
        Herald.clear();
        Journal.clear();
        Moment.clear();
        FindCue.clear();
        DECK.clear();
        BURST.clear();
        TOAST_WAIT.clear();
        LIVE_TOASTS.clear();
        STORIES.clear();
        PANELS.clear();
        story = null;
        panel = null;
        lastCard = "";
        choiceMemo = null;
        panelMemo = null;
    }

    // ---- tick -----------------------------------------------------------------------------------------------------

    static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.hurtTime > 0) lastHurtTick = ticks;
        if (ticks % 10 == 0) {
            hostileNear = !mc.player.level().getEntitiesOfClass(Mob.class, mc.player.getBoundingBox().inflate(8),
                    m -> m instanceof Enemy && m.isAlive()).isEmpty();
        }

        // keys: drained every tick so a press can only ever count for the card that is armed right now
        int yes = 0, no = 0;
        while (AwakenKeys.ACCEPT.consumeClick()) yes++;
        while (AwakenKeys.DISMISS.consumeClick()) no++;
        boolean paused = mc.gui.screen() != null;
        if (DECK.current() == null && panel != null && panel.closingAt < 0 && no > 0 && panel.age > ChoiceDeck.ARM_TICKS) {
            panel.closingAt = panel.age; // N puts an info card away
            no = 0;
        }
        ChoiceDeck.Choice before = DECK.current();
        ChoiceDeck.Answer a = DECK.tick(yes, no, paused, speaking());
        if (a != null) send(a, before == null ? null : before.window());
        ChoiceDeck.Choice cur = DECK.current();
        String action = cur == null ? "" : cur.action;
        if (!action.equals(lastCard)) {
            lastCard = action;
            cardAge = 0;
            if (cur != null) play(cur.window().kind() == SignalWindow.Kind.WARNING ? CoreModule.SIGNAL_WARNING.get() : CoreModule.SIGNAL_OPEN.get(), 0.6f);
        } else {
            cardAge++;
        }

        Herald.tick(mc);
        tickToasts(mc);
        tickPanel();
        tickStory(mc);
        Moment.tick();
        FindCue.tick();
    }

    /** How many of the System's toasts are on screen now (the task card steps down under them). */
    static int liveToasts() {
        return LIVE_TOASTS.size();
    }

    private static void tickToasts(Minecraft mc) {
        for (ToastBurst.Out<ToastItem> out : BURST.poll(ticks)) TOAST_WAIT.addLast(out);
        LIVE_TOASTS.removeIf(t -> t[0] <= ticks);
        if (mc.gui.screen() instanceof QuietScreen) return; // they wait (and merge) until the player is back
        boolean any = false;
        while (!TOAST_WAIT.isEmpty() && LIVE_TOASTS.size() < SignalLayout.MAX_TOASTS) {
            int free = SignalLayout.MAX_TOASTS - LIVE_TOASTS.size();
            ToastBurst.Out<ToastItem> next;
            if (TOAST_WAIT.size() > free && free == 1) { // more waiting than room: the rest arrive as one
                List<ToastItem> all = new ArrayList<>();
                while (!TOAST_WAIT.isEmpty()) all.addAll(TOAST_WAIT.pollFirst().items());
                next = new ToastBurst.Out<>(all);
            } else {
                next = TOAST_WAIT.pollFirst();
            }
            addToast(mc, next);
            any = true;
        }
        if (any) dev.awaken.core.audio.client.AudioUi.toast();
    }

    private static void addToast(Minecraft mc, ToastBurst.Out<ToastItem> out) {
        String title, detail;
        int colour;
        ToastItem explanation = out.items().stream().filter(ToastItem::journal).findFirst().orElse(null);
        if (out.merged() && explanation != null) {
            title = explanation.title();
            detail = explanation.detail();
            colour = explanation.colour();
        } else if (out.merged()) {
            title = out.items().size() + " new things (press " + key(AwakenKeys.STATUS) + ")";
            List<String> names = new ArrayList<>();
            for (ToastItem t : out.items()) names.add(t.title());
            detail = String.join(" · ", names);
            colour = Glass.EDGE;
        } else {
            ToastItem t = out.first();
            title = t.title();
            detail = t.detail();
            colour = t.colour();
        }
        double mult = mc.options.notificationDisplayTime().get();
        long[] live = {ticks + Math.round(SignalToast.BASE_MS * mult / 50.0) + 40};
        LIVE_TOASTS.add(live);
        boolean saved = explanation != null;
        String more = saved && out.merged() ? "this and " + (out.items().size() - 1) + " more" : "more";
        String hint = !saved ? "" : AwakenKeys.STATUS.isUnbound() ? "Set a Status key in Controls to read " + more + " in your Journal"
                : "Press " + key(AwakenKeys.STATUS) + " to read " + more + " in your Journal";
        mc.gui.toastManager().addToast(new SignalToast(title, detail, colour, hint, () -> live[0] = 0));
    }

    private static void tickPanel() {
        if (panel != null) {
            panel.age++;
            boolean expired = panel.age >= Math.max(60, panel.w.ttlTicks());
            if (panel.closingAt < 0 && (expired || DECK.current() != null)) panel.closingAt = panel.age;
            if (panel.closingAt >= 0 && panel.age - panel.closingAt >= CLOSE_TICKS) panel = null;
        }
        if (panel == null && DECK.current() == null && !PANELS.isEmpty()) {
            panel = new Panel(PANELS.pollFirst());
            play(CoreModule.SIGNAL_OPEN.get(), 0.45f);
        }
    }

    private static double typeSpeed() {
        try {
            return AwakenConfig.Client.SIGNAL_TYPE_SPEED.get();
        } catch (IllegalStateException notLoaded) {
            return 3.0;
        }
    }

    /** Whether a story line is on screen or waiting to be said. */
    static boolean speaking() {
        return story != null || !STORIES.isEmpty();
    }

    /** NPC speech on screen or next to be said, or a speaker's choice card (a press then waits for its keys). */
    static boolean dialogueActive() {
        return story != null && story.w.dialogue()
                || story == null && !STORIES.isEmpty() && STORIES.peekFirst().dialogue()
                || DECK.current() != null && DECK.current().window().dialogue();
    }

    /** One press: finish the line being typed, else the next page, else the next line (the server may send it). */
    static void advanceDialogue() {
        if (story == null) { tickStory(Minecraft.getInstance()); return; }
        if (!story.w.dialogue() || story.w.action().isPresent()) return;
        if (story.closingAt < 0 && story.age < story.typeTicks) { // the first press finishes the line being typed
            story.age = story.typeTicks;
            return;
        }
        if (!story.pager.done()) {
            nextChunk(Minecraft.getInstance());
            story.first = false;
            story.closingAt = -1;
        } else {
            String topic = story.w.topic();
            story = null;
            dev.awaken.core.api.Dialogue.finished(topic);
            tickStory(Minecraft.getInstance());
        }
    }

    private static void tickStory(Minecraft mc) {
        if (story == null) {
            if (STORIES.isEmpty()) return;
            story = new Story(STORIES.pollFirst());
            nextChunk(mc);
            if (story.lines.isEmpty()) {
                story = null;
                return;
            }
            dev.awaken.core.audio.client.AudioUi.story();
        }
        story.age++;
        if (story.closingAt >= 0) {
            if (story.age - story.closingAt >= STORY_FADE) story = null;
            return;
        }
        // a card came up and the line no longer fits beside it: cut the chunk again at the new width
        int width = storyWidth(mc, story.lines.size());
        if (width < story.width) {
            for (String l : story.lines) {
                if (mc.font.width(l) > width) {
                    story.pager.pushBack();
                    boolean wasFirst = story.first;
                    nextChunk(mc);
                    story.first = wasFirst;
                    story.age = story.typeTicks; // already typed: just re-flowed
                    break;
                }
            }
        }
        if (story.age >= story.typeTicks + story.holdTicks) {
            if (story.pager.done()) story.closingAt = story.age;
            else {
                nextChunk(mc);
                story.first = false;
                if (story.lines.isEmpty()) story.closingAt = story.age;
            }
        }
    }

    private static int storyWidth(Minecraft mc, int lines) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double s = scale();
        return SignalLayout.storyTextWidth(SignalLayout.story(w, h, s, Math.max(1, lines), slotRect(mc.font, w, h, s)));
    }

    private static void nextChunk(Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int max = StoryPager.maxLines(w, h, scale());
        int width = storyWidth(mc, max);
        story.titleChunk = story.pager.titleNext();
        story.lines = story.pager.next(width, max, mc.font::width);
        story.width = width;
        int chars = 0;
        for (String l : story.lines) chars += l.length();
        story.typeTicks = StoryPager.typeTicks(chars, typeSpeed());
        story.holdTicks = StoryPager.holdTicks(chars);
        story.age = 0;
    }

    // ---- layout of the card slot ----------------------------------------------------------------------------------

    private static String choiceBody(SignalWindow w, int width, Font font) {
        String body = w.body().map(Component::getString).map(t -> SignalText.cardBody(t, width, font::width)).orElse("");
        if (!body.isBlank()) return body;
        if (!w.rows().isEmpty()) return SignalText.collapseRows(w.rows());
        return w.footer().map(Component::getString).map(SignalText::stripKeyHints).orElse("");
    }

    /** The last layout worked out, per window and screen size: cards are drawn every frame but change rarely. */
    private record Memo(SignalWindow w, int sw, int sh, double s, SignalLayout.Card card, List<String> question,
                        List<String> body, List<PanelLine> lines, String footer) {
        boolean matches(SignalWindow w2, int sw2, int sh2, double s2) {
            return w == w2 && sw == sw2 && sh == sh2 && s == s2;
        }
    }

    private static Memo choiceMemo, panelMemo;

    private static Memo choiceMemo(Font font, SignalWindow w, int sw, int sh, double s) {
        if (choiceMemo != null && choiceMemo.matches(w, sw, sh, s)) return choiceMemo;
        int tw = SignalLayout.CARD_W - 12;
        int q = Math.min(2, Math.max(1, SignalText.wrap(w.title().getString(), tw, font::width).size()));
        String body = choiceBody(w, SignalLayout.CARD_W_BODY - 12, font);
        int b = body.isBlank() ? 0 : SignalText.wrap(body, tw, font::width).size();
        if (b > 1) b = Math.max(2, Math.min(SignalLayout.CHOICE_BODY_LINES, SignalText.wrap(body, SignalLayout.CARD_W_BODY - 12, font::width).size()));
        SignalLayout.Card card = SignalLayout.choice(sw, sh, s, q, b);
        int cw = card.textWidth();
        List<String> question = SignalText.wrapAtMost(w.title().getString(), cw, card.lines(), font::width);
        List<String> bodyLines = card.bodyLines() == 0 ? List.of() : SignalText.wrapAtMost(body, cw, card.bodyLines(), font::width);
        choiceMemo = new Memo(w, sw, sh, s, card, question, bodyLines, List.of(), "");
        return choiceMemo;
    }

    private static SignalLayout.Card choiceLayout(Font font, SignalWindow w, int sw, int sh, double s) {
        return choiceMemo(font, w, sw, sh, s).card();
    }

    private record PanelLine(String left, String right) {}

    private static List<PanelLine> panelLines(Font font, SignalWindow w) {
        int tw = SignalLayout.PANEL_W - 12;
        List<PanelLine> out = new ArrayList<>();
        w.body().map(Component::getString).filter(b -> !b.isBlank()).ifPresent(b -> {
            for (String l : SignalText.wrapAtMost(b.replace('\n', ' '), tw, 2, font::width)) out.add(new PanelLine(l, ""));
        });
        for (SignalWindow.Row r : w.rows()) out.add(new PanelLine(r.label().getString(), r.value().getString()));
        return out;
    }

    private static String panelFooter(SignalWindow w) {
        return w.footer().map(Component::getString).map(SignalText::stripKeyHints).orElse("");
    }

    private static Memo panelMemo(Font font, SignalWindow w, int sw, int sh, double s) {
        if (panelMemo != null && panelMemo.matches(w, sw, sh, s)) return panelMemo;
        List<PanelLine> lines = panelLines(font, w);
        String footer = panelFooter(w);
        SignalLayout.Card card = SignalLayout.panel(sw, sh, s, lines.size(), !footer.isBlank());
        panelMemo = new Memo(w, sw, sh, s, card, List.of(), List.of(), lines, footer);
        return panelMemo;
    }

    private static SignalLayout.Card panelLayout(Font font, SignalWindow w, int sw, int sh, double s) {
        return panelMemo(font, w, sw, sh, s).card();
    }

    /** The card slot's rect right now (choice or panel), or null when empty. */
    static Rect slotRect(Font font, int sw, int sh, double s) {
        ChoiceDeck.Choice c = DECK.current();
        if (c != null) return choiceLayout(font, c.window(), sw, sh, s).rect();
        if (panel != null) return panelLayout(font, panel.w, sw, sh, s).rect();
        return null;
    }

    // ---- render ---------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        double s = scale();
        float pt = dt.getGameTimeDeltaPartialTick(false);
        Rect slot = null;
        ChoiceDeck.Choice cur = DECK.current();
        if (cur != null) slot = drawChoice(g, font, cur, sw, sh, s, pt);
        else if (panel != null) slot = drawPanel(g, font, panel, sw, sh, s, pt);
        if (story != null) drawStory(g, font, sw, sh, s, slot, pt);
        Moment.render(g, dt);
        FindCue.render(g, dt);
    }

    private static Rect drawChoice(GuiGraphicsExtractor g, Font font, ChoiceDeck.Choice c, int sw, int sh, double s, float pt) {
        SignalWindow w = c.window();
        Memo m = choiceMemo(font, w, sw, sh, s);
        SignalLayout.Card card = m.card();
        Rect r = card.rect();
        float alpha = Math.min(1f, (cardAge + pt) / OPEN_TICKS);
        int w0 = card.textWidth() + 12, h0 = SignalLayout.choiceHeight(card.lines(), m.body().size());
        int tw = card.textWidth();
        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale((float) card.scale(), (float) card.scale());
        int accent = w.kind() == SignalWindow.Kind.WARNING ? Glass.WARN : Glass.AMBER;
        boolean holo = HudLook.holo();
        // projected: the card scans in over the same 4 ticks the flat one fades in; its text shows as the scan passes
        HudLook.card(g, 0, 0, w0, h0, accent, holo ? 1f : alpha, alpha, 0f);
        if (holo) g.enableScissor(0, 0, w0, revealed(h0, alpha));
        int tx = 7, ty = 4;
        for (String line : m.question()) {
            HudLook.text(g, font, line, tx, ty, Glass.a(Glass.TITLE, alpha), true);
            ty += SignalLayout.LINE;
        }
        for (String line : m.body()) {
            HudLook.text(g, font, line, tx, ty, Glass.a(Glass.TEXT, 0.8f * alpha), false);
            ty += SignalLayout.LINE;
        }
        ty += 2;
        float keys = alpha * (DECK.armed() ? 1f : 0.45f); // dim until presses count
        keyLine(g, font, key(AwakenKeys.ACCEPT), SignalText.yesLabel(w), tx, ty, tw, Glass.GOOD, keys);
        keyLine(g, font, key(AwakenKeys.DISMISS), SignalText.noLabel(w), tx, ty + SignalLayout.LINE, tw, Glass.LABEL, keys);
        float left = 1f - Math.min(1f, (c.age() + pt) / ChoiceDeck.FOLD_TICKS);
        g.horizontalLine(2, 2 + Math.round((w0 - 3) * left), h0 - 1, Glass.a(accent, 0.55f * alpha));
        if (holo) g.disableScissor();
        g.pose().popMatrix();
        return r;
    }

    /** How much of a card of height {@code h} the projected scan-in has drawn at {@code reveal} (its ease-out). */
    static int revealed(int h, float reveal) {
        float r = Math.min(1f, Math.max(0f, reveal));
        return (int) Math.ceil(h * (1f - (1f - r) * (1f - r) * (1f - r)));
    }

    private static void keyLine(GuiGraphicsExtractor g, Font font, String keyName, String label, int x, int y, int tw, int accent, float alpha) {
        String key = "[" + SignalText.ellipsize(keyName, 40, font::width) + "]";
        HudLook.text(g, font, key, x, y, Glass.a(accent, alpha), false);
        int lx = x + font.width(key) + 4;
        HudLook.text(g, font, SignalText.ellipsize(label, tw - (lx - x), font::width), lx, y, Glass.a(Glass.TITLE, alpha), false);
    }

    private static Rect drawPanel(GuiGraphicsExtractor g, Font font, Panel p, int sw, int sh, double s, float pt) {
        SignalWindow w = p.w;
        Memo m = panelMemo(font, w, sw, sh, s);
        SignalLayout.Card card = m.card();
        Rect r = card.rect();
        float age = p.age + pt;
        float alpha = Math.min(1f, age / OPEN_TICKS);
        float reveal = alpha, glitch = 0f;
        if (p.closingAt >= 0) {
            alpha = Math.max(0f, 1f - (age - p.closingAt) / CLOSE_TICKS);
            reveal = 1f;
            glitch = 1f - alpha;
        }
        if (alpha <= 0.01f) return r;
        boolean holo = HudLook.holo();
        int w0 = SignalLayout.PANEL_W, h0 = SignalLayout.panelHeight(card.lines(), card.extra());
        int tw = card.textWidth();
        int accent = accent(w);
        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale((float) card.scale(), (float) card.scale());
        HudLook.card(g, 0, 0, w0, h0, accent, holo ? 1f : alpha, reveal, glitch);
        if (holo) {
            g.pose().translate(HudLook.shake(glitch), 0);
            g.enableScissor(0, 0, w0, revealed(h0, reveal));
        }
        int tx = 7, ty = 4;
        HudLook.text(g, font, SignalText.ellipsize(w.title().getString(), tw, font::width), tx, ty, Glass.a(accent, alpha), true);
        ty += SignalLayout.LINE + 1;
        List<PanelLine> lines = m.lines();
        int room = card.lines();
        boolean cut = lines.size() > room;
        int show = cut ? Math.max(0, room - 1) : lines.size();
        for (int i = 0; i < show; i++) {
            PanelLine l = lines.get(i);
            if (l.right().isEmpty()) {
                HudLook.text(g, font, SignalText.ellipsize(l.left(), tw, font::width), tx, ty, Glass.a(Glass.TEXT, alpha), false);
            } else {
                String label = SignalText.ellipsize(l.left(), Math.max(30, tw / 2), font::width);
                int lw = font.width(label);
                String value = SignalText.ellipsize(l.right(), tw - lw - 6, font::width);
                HudLook.text(g, font, label, tx, ty, Glass.a(Glass.LABEL, alpha), false);
                HudLook.text(g, font, value, tx + tw - font.width(value), ty, Glass.a(Glass.TITLE, alpha), false);
            }
            ty += SignalLayout.LINE;
        }
        if (cut) {
            String more = "Press " + key(AwakenKeys.STATUS) + " to see " + (lines.size() - show) + " more";
            HudLook.text(g, font, SignalText.ellipsize(more, tw, font::width), tx, ty, Glass.a(Glass.LABEL, alpha), false);
            ty += SignalLayout.LINE;
        }
        if (card.extra()) {
            HudLook.text(g, font, SignalText.ellipsize(m.footer(), tw, font::width), tx, ty, Glass.a(Glass.EDGE, 0.85f * alpha), false);
        }
        if (p.closingAt < 0 && w.ttlTicks() > 0) {
            float left = 1f - Math.min(1f, age / Math.max(60, w.ttlTicks()));
            g.horizontalLine(2, 2 + Math.round((w0 - 3) * left), h0 - 1, Glass.a(accent, 0.5f * alpha));
        }
        if (holo) g.disableScissor();
        g.pose().popMatrix();
        return r;
    }

    private static void drawStory(GuiGraphicsExtractor g, Font font, int sw, int sh, double s, Rect slot, float pt) {
        Story st = story;
        if (st.lines.isEmpty()) return;
        SignalLayout.Card layout = SignalLayout.story(sw, sh, s, st.lines.size(), slot);
        Rect r = layout.rect();
        float age = st.age + pt;
        float alpha = st.first ? Math.min(1f, age / 3f) : 1f;
        float reveal = alpha, glitch = 0f;
        if (st.closingAt >= 0) {
            alpha = Math.max(0f, 1f - (age - st.closingAt) / STORY_FADE);
            reveal = 1f;
            glitch = 1f - alpha;
        }
        if (alpha <= 0.01f) return;
        int typed = (int) Math.floor(age * Math.max(0.5, typeSpeed()));
        int w0 = (int) Math.floor(r.w() / layout.scale());
        int tw = SignalLayout.storyTextWidth(layout);
        int widest = 0;
        for (String l : st.lines) widest = Math.max(widest, Math.min(tw, font.width(l)));
        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale((float) layout.scale(), (float) layout.scale());
        int h0 = SignalLayout.storyHeight(st.lines.size());
        int bx = (w0 - widest) / 2 - 4;
        int colour = st.titleChunk ? Glass.VIOLET : Glass.TITLE;
        if (HudLook.holo()) {
            // a projected strip behind the words (pages after the first keep it; only the words change)
            HudLook.card(g, bx, 0, widest + 8, h0, st.titleChunk ? Glass.VIOLET : Glass.EDGE, 1f, reveal, glitch);
            g.pose().translate(HudLook.shake(glitch), 0);
        } else {
            g.fill(bx, 0, bx + widest + 8, h0, Glass.a(0x05080F, 0.42f * alpha));
        }
        int ty = SignalLayout.STORY_PAD + 1;
        int left = typed;
        for (String line : st.lines) {
            String full = SignalText.ellipsize(line, tw, font::width);
            String part = full.substring(0, Math.max(0, Math.min(full.length(), left)));
            left -= line.length();
            int lx = (w0 - font.width(full)) / 2; // centred on the whole line so it doesn't shift while typing
            if (!part.isEmpty()) HudLook.text(g, font, part, lx, ty, Glass.a(colour, alpha), true);
            ty += SignalLayout.LINE;
        }
        // a speaker waits on you: a small bobbing chevron beside the last line, only while a press would go on
        if (left >= 0 && st.closingAt < 0 && st.w.dialogue() && dev.awaken.core.api.Dialogue.talking()) {
            double t = (st.age - st.typeTicks + pt) * 0.22;
            int c = Glass.a(Glass.EDGE, alpha * (0.6f + 0.4f * (float) Math.sin(t)));
            int cx = bx + widest + 12, cy = ty - SignalLayout.LINE + 2 + (int) Math.round(Math.sin(t));
            g.fill(cx - 2, cy, cx + 3, cy + 1, c);
            g.fill(cx - 1, cy + 1, cx + 2, cy + 2, c);
            g.fill(cx, cy + 2, cx + 1, cy + 3, c);
        }
        g.pose().popMatrix();
    }
}
