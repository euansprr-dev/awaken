package dev.awaken.progression.client;

import dev.awaken.progression.Curve;
import dev.awaken.progression.Directives;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import dev.awaken.progression.net.ProgressionPayloads;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import dev.awaken.signal.net.SignalPayloads;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Status screen sections owned by progression. All of them lay out with {@link Ui}: text wraps to the width, rows
 * collapse to one column when narrow, and glossary words explain themselves on hover.
 */
final class CoreSections {
    private CoreSections() {}

    static Profile profile() {
        var p = Minecraft.getInstance().player;
        return p == null ? new Profile() : Progression.profile(p);
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    static void send(String kind, String arg) {
        ClientPacketDistributor.sendToServer(new ProgressionPayloads.StatusAction(kind, arg));
    }

    static void register() {
        StatusSections.add(StatusSections.Tab.STATUS, new Spine());
        StatusSections.add(StatusSections.Tab.STATUS, new Stats());
        StatusSections.add(StatusSections.Tab.JOURNAL, new WaitingSection());
        dev.awaken.core.api.MapMarks.register(WaitingSection::marks);
        StatusSections.add(StatusSections.Tab.TRUTHS, new RoadSection());
        StatusSections.add(StatusSections.Tab.JOURNAL, new DirectivesSection());
        StatusSections.add(StatusSections.Tab.JOURNAL, new RegimenSection());
        StatusSections.add(StatusSections.Tab.JOURNAL, new Records());
        StatusSections.add(StatusSections.Tab.TRUTHS, new PathChoice());
        StatusSections.add(StatusSections.Tab.TRUTHS, new TruthList(TruthList.Kind.DECLARED));
        StatusSections.add(StatusSections.Tab.TRUTHS, new TruthList(TruthList.Kind.OFFERED));
        StatusSections.add(StatusSections.Tab.TRUTHS, new TruthList(TruthList.Kind.TRUE));
        StatusSections.add(StatusSections.Tab.TRUTHS, new Titles());
        StatusSections.add(StatusSections.Tab.TRUTHS, new Latents());
        StatusSections.add(StatusSections.Tab.CODEX, new CodexBoard());
        CodexEntries.addSource(CoreSections::insightEntries);
    }

    private static String plural(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    // ---- Status tab -------------------------------------------------------------------------------------------------

    /** Who you are and what's next: level progress, points to spend, the way to the next rank, today's tally. */
    static final class Spine extends UiSection {
        public String title() { return "STATUS"; }
        public int order() { return 0; }

        @Override
        protected void build(Ui ui) {
            Profile p = profile();
            var mc = Minecraft.getInstance();
            Ui.Flow head = ui.flow();
            head.text(mc.player == null ? "" : mc.player.getName().getString(), Glass.TITLE);
            head.chip(p.rank().label + "-rank", p.rank().colour);
            var path = p.path().map(Content.PATHS::get).orElse(null);
            if (path != null) head.chip(path.name(), path.colour());
            var title = p.title().map(Content.TITLES::get).orElse(null);
            if (title != null) head.chip("“" + title.name() + "”", Glass.AMBER);
            head.end();

            if (!p.awakened()) {
                ui.text("You have not awakened. Something in the world is waiting for you to notice it.", Glass.LABEL);
                return;
            }
            double need = Curve.xpToNext(p.level());
            ui.pair("Level " + p.level(), String.format("%,.0f / %,.0f XP", p.xp(), need), Glass.TEXT, Glass.LABEL);
            ui.bar(p.xp() / need, Glass.EDGE);
            long toGo = (long) Math.ceil(Math.max(0, need - p.xp()));
            ui.text("◇ " + String.format("%,d", toGo) + " XP to level " + (p.level() + 1) + ".", Glass.TEXT);
            if (p.unspent() > 0) {
                ui.text("◇ " + plural(p.unspent(), "stat point") + " to spend: press + next to a stat below.", Glass.LANTERN);
            }
            Rank next = p.rank().next();
            if (next == null) ui.text("◇ There is no rank above yours.", Glass.TEXT);
            else if (p.reassessmentReady()) {
                ui.text("◇ Reassessment ready: take the " + next.label + "-rank trial at the Spire in your town or at a Reassessment Monolith.", Glass.LANTERN);
            } else {
                int left = Math.max(0, next.levelRequired - p.level());
                ui.text("◇ Next rank: " + next.label + " at level " + next.levelRequired + " (" + plural(left, "level") + " to go), then pass its Reassessment.", Glass.TEXT);
            }
            // the Far goal: what this player has met that the next rank opens (never a list of what they haven't)
            if (next != null && mc.player != null) {
                List<String> opens = WaitingSection.nextRankOpens(mc.player, next);
                if (!opens.isEmpty()) {
                    String list = String.join(" · ", opens.subList(0, Math.min(3, opens.size()))) + (opens.size() > 3 ? " · and more" : "");
                    ui.text("◇ " + next.label + "-rank opens what waits for you: " + list + ".", Glass.HORIZON);
                }
            }
            var level = mc.level;
            if (level != null && p.fractureUntil() > level.getGameTime()) {
                long min = Math.max(1, (p.fractureUntil() - level.getGameTime() + 1199) / 1200);
                ui.text("◇ Fractured for " + min + " min: about 15% weaker. Rest at your Hearth or meditate to heal faster.", Glass.WARN);
            }
            Profile.DayLog d = p.day();
            if (d.day() >= 0 && (d.kills() > 0 || d.gates() > 0 || p.level() > d.levelStart())) {
                String today = "Today: level " + d.levelStart() + (p.level() > d.levelStart() ? " → " + p.level() : "")
                        + " · " + plural(d.kills(), "kill") + " · " + plural(d.gates(), "gate")
                        + (d.best().isBlank() ? "" : " · best: " + d.best());
                ui.text(today, Glass.LABEL);
            }
        }
    }

    static final class Stats extends UiSection {
        public String title() { return "STATS"; }
        public int order() { return 10; }

        private static final String[] HINT = {"melee, Kinetic", "speed, Flow", "health, resistance", "energy, Energy/Tech",
                "crits, secrets, omens", "Veil, dreams, Echoes"};
        private static final int ROW = 16;

        @Override
        protected void build(Ui ui) {
            Profile p = profile();
            boolean spend = p.unspent() > 0;
            int cols = ui.width >= 300 ? 2 : 1;
            int colW = ui.width / cols;
            int rows = (Stat.values().length + cols - 1) / cols;
            int y0 = ui.y();
            for (Stat s : Stat.values()) {
                int i = s.ordinal();
                int sx = ui.x + (i % cols) * colW, sy = y0 + (i / cols) * ROW;
                ui.textAt(s.name(), sx, sy + 3, 26, Glass.LABEL, false);
                ui.textAt(String.valueOf(p.stat(s)), sx + 26, sy + 3, 24, Glass.TITLE, true);
                int hintW = colW - 52 - (spend ? 24 : 4);
                if (hintW > 20) ui.textAt(HINT[i], sx + 52, sy + 3, hintW, Glass.a(Glass.LABEL, 0.65f), false);
                if (spend && ui.buttonAt("+", sx + colW - 20, sy, 14, 13, Glass.EDGE, true)) send("spend", s.name());
            }
            ui.gap(rows * ROW);
            ui.text(spend ? plural(p.unspent(), "point") + " to spend" : "No points to spend", spend ? Glass.LANTERN : Glass.LABEL);
        }
    }

    static final class DirectivesSection extends UiSection {
        public String title() { return "DIRECTIVES"; }
        public int order() { return -15; } // the Journal page: right after what you track

        @Override
        protected void build(Ui ui) {
            Profile p = profile();
            if (p.directives().isEmpty()) {
                ui.text("Nothing offered right now. New Directives come with the morning or after something notable.", Glass.LABEL);
                return;
            }
            long today = clientPlayDay();
            List<Profile.DirectiveState> list = List.copyOf(p.directives());
            for (int i = 0; i < list.size(); i++) {
                Profile.DirectiveState d = list.get(i);
                var def = Content.DIRECTIVES.get(d.id());
                ui.text(def == null ? d.id() : def.text(), Glass.TITLE);
                long left = Math.max(0, d.expiresDay() - today + 1);
                String meta = (def == null ? "" : "Reward: " + Rewards.describe(def.reward()) + " · ") + "fades in " + plural(left, "play-day");
                ui.text(meta, Glass.LABEL, 4, false);
                if (d.accepted()) {
                    ui.gap(1);
                    ui.bar(Directives.progress(p, d), Glass.GOOD);
                } else {
                    ui.gap(1);
                    Ui.Flow row = ui.flow();
                    String action = Directives.ACTION + ":" + d.id();
                    if (row.button("Accept", Glass.GOOD, true)) ClientPacketDistributor.sendToServer(new SignalPayloads.Answer(action, true));
                    if (row.button("Dismiss", Glass.LABEL, true)) ClientPacketDistributor.sendToServer(new SignalPayloads.Answer(action, false));
                    row.end();
                }
                if (i < list.size() - 1) ui.rule();
            }
        }
    }

    static long clientPlayDay() {
        // The client doesn't know play-days exactly; directives store absolute days, so approximate with the profile's
        // day log which the server keeps current.
        return Math.max(0, profile().day().day());
    }

    static final class RegimenSection extends UiSection {
        public String title() { return "DAILY REGIMEN"; }
        public int order() { return -12; }
        public boolean visible() { return !profile().regimen().drills().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            var r = profile().regimen();
            for (String id : r.drills()) {
                var def = Content.DRILLS.get(id);
                boolean done = r.done().contains(id);
                ui.text((done ? "✓ " : "· ") + (def == null ? id : def.text()), done ? Glass.GOOD : Glass.TEXT);
            }
        }
    }

    /** Lifetime records from the counters, two columns when there's room, only what you've actually done. */
    static final class Records extends UiSection {
        private record Row(String label, String counter) {}

        private static final List<Row> ROWS = List.of(
                new Row("Time awake", "awaken:play_ticks"),
                new Row("Monsters slain", "awaken:kills"),
                new Row("Bosses felled", "awaken:bosses_killed"),
                new Row("…bare-handed", "awaken:bosses_killed_unarmed"),
                new Row("Gates cleared", "awaken:gates_cleared"),
                new Row("Floors cleared", "awaken:floors_cleared"),
                new Row("Gates scouted", "awaken:gates_scouted"),
                new Row("Twists seen", "awaken:twists_seen"),
                new Row("Breaks held at home", "awaken:breaks_held_at_hearth"),
                new Row("Deepest stratum", "awaken:depth_record"),
                new Row("Guardians", "awaken:guardians_defeated"),
                new Row("Your shadow, beaten", "awaken:shadows_defeated"),
                new Row("Sovereigns", "awaken:sovereigns_defeated"),
                new Row("Dreams", "awaken:dreams"),
                new Row("Lucid dreams", "awaken:lucid_dreams"),
                new Row("Nightmares beaten", "awaken:nightmares_beaten"),
                new Row("Spirits spoken to", "awaken:spirits_spoken"),
                new Row("Echoes bound", "awaken:echoes_bound"),
                new Row("Machines built", "awaken:machines_built"),
                new Row("Secrets found", "awaken:secrets_found"),
                new Row("Deaths", "awaken:deaths"));

        private static List<String[]> lines() {
            Profile p = profile();
            List<String[]> out = new ArrayList<>();
            for (Row r : ROWS) {
                long v = p.counter(r.counter());
                if (v <= 0) continue;
                String value = r.counter().equals("awaken:play_ticks")
                        ? (v / 72000 > 0 ? v / 72000 + " h " : "") + (v / 1200) % 60 + " min"
                        : String.format("%,d", v);
                out.add(new String[]{r.label(), value});
            }
            return out;
        }

        public String title() { return "RECORDS"; }
        public int order() { return 900; }
        public boolean visible() { return !lines().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            List<String[]> l = lines();
            int cols = ui.width >= 280 ? 2 : 1;
            int colW = ui.width / cols;
            int y0 = ui.y();
            Font f = ui.font();
            for (int i = 0; i < l.size(); i++) {
                int cx = ui.x + (i % cols) * colW, cy = y0 + (i / cols) * 11;
                String v = l.get(i)[1];
                int vw = f.width(v);
                int right = cx + colW - (cols > 1 && i % cols == 0 ? 10 : 0);
                ui.textAt(l.get(i)[0], cx, cy, right - cx - vw - 6, Glass.LABEL, false);
                ui.textAt(v, right - vw, cy, vw, Glass.TEXT, false);
            }
            ui.gap((l.size() + cols - 1) / cols * 11);
        }
    }

    // ---- Truths tab -------------------------------------------------------------------------------------------------

    static final class PathChoice extends UiSection {
        public String title() { return "SECOND AWAKENING · CHOOSE WHO YOU BECOME"; }
        public int order() { return 0; }
        public boolean visible() { return !profile().pathsOffered().isEmpty(); }

        static List<String> evidence(Defs.PathDef d) {
            Profile p = profile();
            List<String> out = new ArrayList<>();
            for (var e : d.evidence().entrySet()) {
                long v = p.counter(e.getKey());
                if (v > 0) out.add(String.format(e.getValue(), v));
            }
            return out;
        }

        @Override
        protected void build(Ui ui) {
            List<String> ids = List.copyOf(profile().pathsOffered());
            for (int i = 0; i < ids.size(); i++) {
                var d = Content.PATHS.get(ids.get(i));
                if (d == null) continue;
                int top = ui.y();
                ui.text(d.name(), d.colour(), 6, true);
                ui.text(d.description(), Glass.TEXT, 6, false);
                for (String ev : evidence(d)) ui.text("· " + ev, Glass.LABEL, 6, false);
                ui.gap(2);
                Ui.Flow row = ui.flow();
                if (row.button("Choose " + d.name(), d.colour(), true)) send("path", ids.get(i));
                row.end();
                ui.fill(ui.x, top, ui.x + 2, ui.y() - 3, Glass.a(d.colour(), 0.9f));
                if (i < ids.size() - 1) ui.rule();
            }
        }
    }

    static final class TruthList extends UiSection {
        enum Kind { DECLARED, OFFERED, TRUE }

        private final Kind kind;

        TruthList(Kind kind) {
            this.kind = kind;
        }

        List<String> ids() {
            Profile p = profile();
            return switch (kind) {
                case DECLARED -> List.copyOf(p.truthsDeclared());
                case OFFERED -> List.copyOf(p.truthsOffered());
                case TRUE -> new ArrayList<>(p.truthsTrue());
            };
        }

        public String title() {
            return switch (kind) {
                case DECLARED -> "DECLARED TRUTHS · " + profile().truthsDeclared().size() + " / 3";
                case OFFERED -> "TRUTHS WAITING TO BE DECLARED";
                case TRUE -> "TRUE";
            };
        }

        public int order() { return 10 + kind.ordinal() * 10; }

        public boolean visible() { return kind != Kind.OFFERED || !ids().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            Profile p = profile();
            List<String> ids = ids();
            if (ids.isEmpty()) {
                ui.text(kind == Kind.DECLARED ? "Declare who you're becoming from the Truths waiting below. Each one shows what it gives."
                        : "None yet. Live one of your declared Truths.", Glass.LABEL);
                return;
            }
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                var d = Content.TRUTHS.get(id);
                ui.text(d == null ? id : "“" + d.text() + "”", kind == Kind.TRUE ? Glass.GOOD : Glass.TITLE, 0, true);
                if (d != null && kind != Kind.TRUE) d.hint().ifPresent(h -> ui.text(h, Glass.LABEL, 4, false));
                if (d != null) {
                    ui.text((kind == Kind.TRUE ? "Granted: " : "Reward: ") + Rewards.describe(d.reward()), kind == Kind.TRUE ? Glass.LABEL : Glass.AMBER, 4, false);
                }
                switch (kind) {
                    case DECLARED -> {
                        if (d != null) ui.bar(d.condition().progress(p, null), Glass.AMBER);
                        Ui.Flow row = ui.flow();
                        row.chip("NOT TRUE YET", Glass.AMBER);
                        if (row.button("Withdraw", Glass.LABEL, true)) send("undeclare", id);
                        row.end();
                    }
                    case OFFERED -> {
                        boolean can = p.truthsDeclared().size() < 3;
                        Ui.Flow row = ui.flow();
                        if (row.button("Declare", Glass.AMBER, can)) send("declare", id);
                        if (!can) row.text("3 of 3 declared: withdraw one first", Glass.LABEL);
                        row.end();
                    }
                    case TRUE -> {
                        Ui.Flow row = ui.flow();
                        row.chip("TRUE", Glass.GOOD);
                        row.end();
                    }
                }
                if (i < ids.size() - 1) ui.rule();
            }
        }
    }

    static final class Titles extends UiSection {
        public String title() { return "TITLES · " + profile().titles().size(); }
        public int order() { return 40; }

        @Override
        protected void build(Ui ui) {
            Profile p = profile();
            if (p.titles().isEmpty()) {
                ui.text("Titles are earned by feats. You'll know when it happens.", Glass.LABEL);
                return;
            }
            for (String id : List.copyOf(p.titles())) {
                var d = Content.TITLES.get(id);
                boolean equipped = p.title().map(id::equals).orElse(false);
                ui.text(d == null ? id : d.name(), equipped ? Glass.AMBER : Glass.TITLE, 0, true);
                if (d != null) {
                    ui.text(d.description(), Glass.LABEL, 4, false);
                    if (!d.perks().isEmpty()) ui.text("Gives: " + Rewards.perks(d.perks()), Glass.TEXT, 4, false);
                }
                Ui.Flow row = ui.flow();
                if (row.button(equipped ? "Equipped" : "Equip", Glass.AMBER, !equipped)) send("title", id);
                row.end();
                ui.gap(2);
            }
        }
    }

    static final class Latents extends UiSection {
        public String title() { return "LATENTS FOUND · " + profile().latents().size() + " / ??"; }
        public int order() { return 50; }

        @Override
        protected void build(Ui ui) {
            Profile p = profile();
            if (p.latents().isEmpty()) {
                ui.text("Something in you is asleep. Do unusual things.", Glass.LABEL);
                return;
            }
            for (String id : List.copyOf(p.latents())) {
                var d = Content.LATENTS.get(id);
                ui.text(d == null ? id : d.name(), Glass.VIOLET, 0, true);
                if (d != null) ui.text(d.description(), Glass.LABEL, 4, false);
                ui.gap(2);
            }
        }
    }

    // ---- Codex tab --------------------------------------------------------------------------------------------------

    static List<CodexEntries.Entry> insightEntries() {
        List<CodexEntries.Entry> out = new ArrayList<>();
        Profile p = profile();
        for (var e : p.counters().entrySet()) {
            // insight is stored separately; read the boss kills counters for "met" bosses too
            if (e.getKey().startsWith("awaken:killed_by/") && e.getValue() > 0) {
                String who = e.getKey().substring("awaken:killed_by/".length());
                out.add(new CodexEntries.Entry("death/" + who, "Deaths", "Killed by " + pretty(who),
                        "It got you " + e.getValue() + " time" + (e.getValue() == 1 ? "" : "s") + ".", true, List.of()));
            }
        }
        return out;
    }

    static String pretty(String id) {
        return Ui.pretty(id);
    }

    /**
     * The mystery board: cards grouped by category, as many columns as fit, threads between linked cards. Cards the
     * player has only heard of show as "?". Hover a card for its full text.
     */
    static final class CodexBoard extends UiSection {
        private static final int MIN_CARD_W = 110, CARD_H = 34, GAP = 8;

        public String title() { return "THE BOARD"; }
        public int order() { return 0; }

        private static Map<String, List<CodexEntries.Entry>> grouped() {
            Map<String, List<CodexEntries.Entry>> m = new java.util.LinkedHashMap<>();
            for (var e : CodexEntries.all()) m.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
            return m;
        }

        public boolean visible() { return !CodexEntries.all().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            Font f = ui.font();
            int cols = Math.max(1, (ui.width + GAP) / (MIN_CARD_W + GAP));
            int cardW = (ui.width - (cols - 1) * GAP) / cols;
            Map<String, int[]> pos = new HashMap<>();
            List<CodexEntries.Entry> all = new ArrayList<>();
            for (var e : grouped().entrySet()) {
                ui.text(e.getKey().toUpperCase(java.util.Locale.ROOT), Glass.LANTERN);
                ui.gap(2);
                int y0 = ui.y();
                int i = 0;
                for (var entry : e.getValue()) {
                    pos.put(entry.id(), new int[]{ui.x + (i % cols) * (cardW + GAP), y0 + (i / cols) * (CARD_H + GAP)});
                    all.add(entry);
                    i++;
                }
                ui.gap(((e.getValue().size() + cols - 1) / cols) * (CARD_H + GAP) + 4);
            }
            if (!ui.drawing()) return;
            var g = ui.canvas();
            for (var entry : all) { // threads first, under the cards
                int[] a = pos.get(entry.id());
                for (String link : entry.links()) {
                    int[] b = pos.get(link);
                    if (b != null) line(g, a[0] + cardW / 2, a[1] + CARD_H / 2, b[0] + cardW / 2, b[1] + CARD_H / 2, Glass.a(Glass.LANTERN, 0.45f));
                }
            }
            for (var entry : all) {
                int[] a = pos.get(entry.id());
                boolean hover = ui.hover(a[0], a[1], cardW, CARD_H);
                Glass.panel(g, a[0], a[1], cardW, CARD_H, entry.found() ? Glass.EDGE : Glass.LABEL, hover ? 1f : 0.85f);
                String head = entry.found() ? entry.title() : "?";
                ui.textAt(head, a[0] + 5, a[1] + 4, cardW - 10, Glass.a(entry.found() ? Glass.TITLE : Glass.LABEL, 1f), false);
                String text = entry.found() ? entry.body() : entry.title();
                var body = SignalText.wrapAtMost(text, cardW - 10, 2, f::width);
                int ty = a[1] + 15;
                for (String l : body) {
                    g.text(f, l, a[0] + 5, ty, Glass.a(Glass.LABEL, 1f), false);
                    ty += 9;
                }
                boolean cut = SignalText.wrap(text, cardW - 10, f::width).size() > 2 || f.width(head) > cardW - 10;
                if (hover && cut) {
                    g.tooltip(f, f.split(Component.literal(head + "\n" + text), 220), ui.mouseX(), ui.mouseY());
                }
            }
        }

        /** A simple stepped line (GUI has no diagonal primitive). */
        private static void line(dev.awaken.signal.client.UiCanvas g, int x0, int y0, int x1, int y1, int col) {
            int mxp = (x0 + x1) / 2;
            g.hLine(Math.min(x0, mxp), Math.max(x0, mxp), y0, col);
            g.vLine(mxp, Math.min(y0, y1), Math.max(y0, y1), col);
            g.hLine(Math.min(mxp, x1), Math.max(mxp, x1), y1, col);
        }
    }
}
