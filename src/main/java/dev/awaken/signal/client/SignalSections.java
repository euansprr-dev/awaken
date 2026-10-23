package dev.awaken.signal.client;

import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.SignalWindow;
import dev.awaken.signal.ui.ChoiceDeck;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The Signal's own Status sections: Now (top), Waiting for you, and Recent messages. */
final class SignalSections {
    private SignalSections() {}

    static void register() {
        StatusSections.add(StatusSections.Tab.STATUS, new NowSection());
        StatusSections.add(StatusSections.Tab.STATUS, new WaitingSection());
        StatusSections.add(StatusSections.Tab.STATUS, new RecentSection());
    }

    /** The Now card: up to three things you could do, best first, with a compass for the ones that have a place. */
    static final class NowSection extends UiSection {
        public String title() { return "NOW"; }
        public int order() { return -30; }

        @Override
        protected void build(Ui ui) {
            List<NowHooks.Suggestion> list = NowClient.suggestions();
            if (list.isEmpty()) {
                ui.text("Nothing pressing. Build, explore, or rest: the Signal will speak up when something comes near.", Glass.LABEL);
                return;
            }
            for (NowHooks.Suggestion s : list) {
                String where = NowClient.compass(s).map(c -> "  " + c).orElse("");
                int colour = s.priority() >= 90 ? Glass.WARN : s == list.get(0) ? Glass.TITLE : Glass.TEXT;
                ui.text("◇ " + s.text().getString() + where, colour);
            }
        }
    }

    /** Decisions that folded away (or arrived while you were offline), with a button for each answer. */
    static final class WaitingSection extends UiSection {
        public String title() { return "WAITING FOR YOU"; }
        public int order() { return -20; }
        public boolean visible() { return !SystemHud.pendingChoices().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            List<ChoiceDeck.Choice> list = List.copyOf(SystemHud.pendingChoices());
            for (int i = 0; i < list.size(); i++) {
                ChoiceDeck.Choice c = list.get(i);
                SignalWindow w = c.window();
                ui.text(w.title().getString(), Glass.TITLE, 0, true);
                w.body().map(Component::getString).filter(b -> !b.isBlank()).ifPresent(b -> ui.text(b, Glass.TEXT, 4, false));
                for (SignalWindow.Row r : w.rows()) ui.text(r.label().getString() + ": " + r.value().getString(), Glass.LABEL, 4, false);
                int secs = Math.max(0, c.ticksLeft() / 20);
                ui.text(secs >= 120 ? "Waits " + secs / 60 + " more min" : "Waits " + secs + " more s", Glass.LABEL, 4, false);
                ui.gap(2);
                Ui.Flow row = ui.flow();
                if (row.button(SignalText.yesLabel(w), Glass.GOOD, true)) SystemHud.answer(c.action, true);
                if (row.button(SignalText.noLabel(w), Glass.LABEL, true)) SystemHud.answer(c.action, false);
                row.end();
                if (i < list.size() - 1) ui.rule();
            }
        }
    }

    /** Everything the System said recently, in full. */
    static final class RecentSection extends UiSection {
        private static boolean all;
        private static final int SHORT = 6;

        public String title() { return "RECENT MESSAGES"; }
        public int order() { return 950; }
        public boolean visible() { return !RecentLog.entries().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            List<RecentLog.Entry> list = RecentLog.entries();
            int n = all ? list.size() : Math.min(SHORT, list.size());
            long now = System.currentTimeMillis();
            for (int i = 0; i < n; i++) {
                RecentLog.Entry e = list.get(i);
                ui.pair(e.title, ago(now - e.at), colour(e.kind), Glass.LABEL);
                for (String l : e.lines) ui.text(l, Glass.TEXT, 6, false);
                if (!e.outcome().isEmpty()) ui.text(e.outcome(), Glass.GOOD, 6, false);
                ui.gap(3);
            }
            if (list.size() > SHORT) {
                Ui.Flow row = ui.flow();
                if (row.button(all ? "Show fewer" : "Show all " + list.size(), Glass.EDGE, true)) all = !all;
                row.end();
            }
        }

        private static int colour(SignalWindow.Kind k) {
            return switch (k) {
                case WARNING -> Glass.WARN;
                case REVELATION -> Glass.VIOLET;
                case TRUTH -> Glass.AMBER;
                case REASSESSMENT, DAYS_END -> Glass.LANTERN;
                default -> Glass.TITLE;
            };
        }

        private static String ago(long ms) {
            long s = ms / 1000;
            if (s < 60) return "just now";
            if (s < 3600) return s / 60 + " min ago";
            return s / 3600 + " h ago";
        }
    }
}
