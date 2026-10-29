package dev.awaken.signal.client;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.Notices;
import dev.awaken.core.api.UiHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.SignalWindow;
import dev.awaken.signal.ui.Pace;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The notification director, client side (SPEC §26): every System message that isn't a decision passes through here
 * on its way to the screen, and so do other modules' own HUD lines ({@link Notices.Client}). It holds them while you
 * fight, read, craft or watch a scene, and in a new world's first minutes; spaces them out by the {@link Pace} of the
 * chosen density; shows a first-of-its-kind as a {@link Moment}; and files the rest in the {@link Journal} with the
 * {@link FindCue}. Ticked by the Signal client; nothing allocates while nothing arrives.
 */
public final class Herald {
    private Herald() {}

    /** A new world is quiet this long beyond its opening (world ticks). */
    static final long SETTLE = 20L * 60 * 5;
    /** Calm: no fight for this long (ticks), so the longer gap applies. */
    static final long CALM_AFTER = 20L * 60;
    /** An untagged message this soon after the player's own click or key answers it: shown at once (ticks). */
    static final long RESPONSE = 60;

    /** One thing to tell: a toast's lines, or a moment's (label from the window's footer). */
    record Item(String title, String detail, int colour, String label, String body, SignalWindow window) {}

    private static final Pace<Item> PACE = new Pace<>(Pace.Rules.of(Pace.Density.STANDARD));
    private static Pace.Density density = Pace.Density.STANDARD;
    private static long ticks, lastFight = -100_000, lastAct = -100_000;
    /** Scenes holding the director (identity set: a few at most). */
    private static final java.util.Set<Object> HOLDERS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    static Pace.Density density() {
        try {
            return switch (AwakenConfig.Client.NOTIFICATIONS.get()) {
                case MINIMAL -> Pace.Density.MINIMAL;
                case GUIDED -> Pace.Density.GUIDED;
                default -> Pace.Density.STANDARD;
            };
        } catch (IllegalStateException notLoaded) {
            return Pace.Density.STANDARD;
        }
    }

    static void install() {
        Notices.installClient(new Notices.Client() {
            @Override public boolean claim(Notices.Tier tier, String topic) {
                Minecraft mc = Minecraft.getInstance();
                if (!PACE.free(ticks, tier, held(mc), calm())) return false;
                PACE.mark(ticks);
                return true;
            }

            @Override public void file(Notices.Tier tier, Component title, Component line) {
                Herald.file(new Item(title.getString(), line.getString(), Glass.EDGE, "", "", null), tier, 1);
            }

            @Override public boolean quiet() {
                return held(Minecraft.getInstance());
            }

            @Override public void hold(Object owner, boolean on) {
                if (on) HOLDERS.add(owner);
                else HOLDERS.remove(owner);
            }
        });
    }

    // ---- intake ---------------------------------------------------------------------------------------------------

    /** A System message that the director paces (a toast, an info panel, a moment). */
    static void offer(SignalWindow w) {
        String body = w.body().map(Component::getString).orElse("");
        String label = w.footer().map(Component::getString).map(SignalText::stripKeyHints).orElse("");
        Item item = new Item(w.title().getString(), SignalText.toastDetail(w), SystemHud.accent(w), label.isBlank() ? "Discovery" : label, body, w);
        PACE.offer(ticks, item, w.tier(), w.topic(), w.moment(), w.immediate());
        log("offer", w.tier(), w.topic(), item.title());
    }

    /** A one-line toast. */
    static void offer(Component text, Notices.Tier tier, String topic, int colour) {
        PACE.offer(ticks, new Item(text.getString(), "", colour, "", "", null), tier, topic, false);
        log("offer", tier, topic, text.getString());
    }

    /** One line per decision in the debug log (tours count messages a minute from it: "[herald] show ..."). */
    static void log(String what, Notices.Tier tier, String topic, String title) {
        dev.awaken.Awaken.LOG.debug("[herald] {} {} {} t={} '{}'", what, tier, topic.isEmpty() ? "-" : topic, ticks, title);
    }

    /** The player clicked, used or pressed something (an input event): what comes back now is a response. */
    static void acted() {
        lastAct = ticks;
    }

    /** Whether an untagged message arriving now answers something the player just did. */
    static boolean responding(SignalWindow w) {
        return w.untagged() && ticks - lastAct <= RESPONSE;
    }

    static boolean responding() {
        return ticks - lastAct <= RESPONSE;
    }

    /** Something took the screen outside the director (a choice card, a story line): the next message waits its gap. */
    static void took() {
        PACE.mark(ticks);
    }

    // ---- the rules --------------------------------------------------------------------------------------------------

    /** Nothing paced shows now: {@link #blocked}, or a world still settling. */
    static boolean held(Minecraft mc) {
        return blocked(mc) || mc.level.getGameTime() < SETTLE;
    }

    /**
     * Nothing paced shows, not even an immediate find: a screen, a fight, a scene, a card or moment already up, or not
     * awakened yet. A settling world doesn't block: a creature studied in its first minutes still shows (SPEC §26.3).
     */
    static boolean blocked(Minecraft mc) {
        if (mc.player == null || mc.level == null) return true;
        if (mc.gui.screen() != null || Moment.active() || SystemHud.cardUp() || !HOLDERS.isEmpty()) return true;
        if (SystemHud.inCombat() || UiHooks.focus() > 0.2f) return true;
        return !Progression.profile(mc.player).awakened();
    }

    static boolean calm() {
        return ticks - lastFight > CALM_AFTER;
    }

    // ---- tick -------------------------------------------------------------------------------------------------------

    static void tick(Minecraft mc) {
        ticks++;
        Pace.Density d = density();
        if (d != density) {
            density = d;
            PACE.rules(Pace.Rules.of(d));
        }
        if (SystemHud.inCombat()) lastFight = ticks;
        Journal.tick(mc.gui.screen() != null);
        boolean blocked = blocked(mc);
        List<Pace.Out<Item>> out = PACE.poll(ticks, blocked, blocked || mc.level.getGameTime() < SETTLE, calm());
        if (out.isEmpty()) return;
        List<Item> filed = new ArrayList<>(0);
        for (Pace.Out<Item> o : out) {
            if (o.act() == Pace.Act.FILE) {
                for (Item i : o.items()) {
                    if (i.window() != null && i.window().fleeting()) {
                        log("drop", o.tier(), "", i.title()); // its moment passed: not news, and not for the Journal
                        continue;
                    }
                    Journal.add(i.title(), i.detail(), i.colour(), o.tier());
                    filed.add(i);
                    log("file", o.tier(), "", i.title());
                }
                continue;
            }
            log(o.moment() ? "moment" : "show", o.tier(), "", o.first().title() + (o.merged() ? " +" + (o.items().size() - 1) : ""));
            if (o.moment()) {
                Item i = o.first();
                Moment.show(i.label(), i.title(), i.body(), i.colour());
                Journal.add(i.title(), i.body(), i.colour(), o.tier());
            } else if (o.first().window() != null && SystemHud.effective(o.first().window()) == SignalWindow.Presentation.PANEL) {
                SystemHud.panelNow(o.first().window());
            } else {
                SystemHud.toastNow(o.items());
            }
        }
        if (!filed.isEmpty()) FindCue.cue(filed.get(0).title(), filed.get(0).colour(), filed.size());
    }

    static void clear() {
        PACE.clear();
        HOLDERS.clear();
        lastFight = -100_000;
    }

    /** Files an item directly (another module's line). */
    static void file(Item i, Notices.Tier tier, int count) {
        Journal.add(i.title(), i.detail(), i.colour(), tier);
        FindCue.cue(i.title(), i.colour(), count);
    }

    /** For the Status screen and tours: how many messages wait for their moment. */
    public static int waiting() {
        return PACE.waiting();
    }
}
