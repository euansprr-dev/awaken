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
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Status screen sections owned by progression. */
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

    static List<FormattedCharSequence> wrap(String s, int w) {
        return font().split(Component.literal(s), w);
    }

    static void register() {
        StatusSections.add(StatusSections.Tab.STATUS, new Spine());
        StatusSections.add(StatusSections.Tab.STATUS, new Stats());
        StatusSections.add(StatusSections.Tab.STATUS, new DirectivesSection());
        StatusSections.add(StatusSections.Tab.STATUS, new RegimenSection());
        StatusSections.add(StatusSections.Tab.STATUS, new Records());
        StatusSections.add(StatusSections.Tab.TRUTHS, new PathChoice());
        StatusSections.add(StatusSections.Tab.TRUTHS, new TruthList(TruthList.Kind.DECLARED));
        StatusSections.add(StatusSections.Tab.TRUTHS, new TruthList(TruthList.Kind.OFFERED));
        StatusSections.add(StatusSections.Tab.TRUTHS, new TruthList(TruthList.Kind.TRUE));
        StatusSections.add(StatusSections.Tab.TRUTHS, new Titles());
        StatusSections.add(StatusSections.Tab.TRUTHS, new Latents());
        StatusSections.add(StatusSections.Tab.CODEX, new CodexBoard());
        CodexEntries.addSource(CoreSections::insightEntries);
    }

    // ---- Status tab -------------------------------------------------------------------------------------------------

    static final class Spine implements StatusSections.Section {
        public String title() { return "STATUS"; }
        public int order() { return 0; }
        public int height(int w) { return 58; }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Profile p = profile();
            Font f = font();
            var mc = Minecraft.getInstance();
            String name = mc.player == null ? "" : mc.player.getName().getString();
            g.text(f, name, x, y, Glass.a(Glass.TITLE, 1f), true);
            int cx = x + f.width(name) + 8;
            cx += Glass.chip(g, f, p.rank().label + "-rank", cx, y - 2, p.rank().colour) + 4;
            int pcx = cx;
            var path = p.path().map(Content.PATHS::get).orElse(null);
            if (path != null) pcx += Glass.chip(g, f, path.name(), pcx, y - 2, path.colour()) + 4;
            var title = p.title().map(Content.TITLES::get).orElse(null);
            if (title != null) Glass.chip(g, f, "“" + title.name() + "”", pcx, y - 2, Glass.AMBER);

            g.text(f, "Level " + p.level(), x, y + 14, Glass.a(Glass.TEXT, 1f), false);
            String xp = String.format("%,.0f / %,.0f", p.xp(), Curve.xpToNext(p.level()));
            g.text(f, xp, x + w - f.width(xp), y + 14, Glass.a(Glass.LABEL, 1f), false);
            Glass.bar(g, x, y + 25, w, 4, p.xp() / Curve.xpToNext(p.level()), Glass.EDGE, 1f);

            Rank next = p.rank().next();
            String spine;
            if (!p.awakened()) spine = "You have not awakened.";
            else if (next == null) spine = "There is no rank above yours.";
            else if (p.reassessmentReady()) spine = "Reassessment available · take the trial for " + next.label + "-rank";
            else spine = "Next rank: " + next.label + " · Reassessment at Level " + next.levelRequired + " (you're " + p.level() + ")";
            g.text(f, "◇ " + spine, x, y + 35, Glass.a(p.reassessmentReady() ? Glass.LANTERN : Glass.TEXT, 1f), false);

            List<String> flags = new ArrayList<>();
            var level = mc.level;
            if (level != null && p.fractureUntil() > level.getGameTime()) {
                flags.add("Fractured " + (p.fractureUntil() - level.getGameTime()) / 1200 + "m · rest at your Hearth");
            }
            if (!flags.isEmpty()) g.text(f, String.join("   ", flags), x, y + 47, Glass.a(Glass.WARN, 1f), false);
        }
    }

    static final class Stats implements StatusSections.Section {
        public String title() { return "STATS"; }
        public int order() { return 10; }
        public int height(int w) { return 3 * 16 + 14; }

        private static final String[] HINT = {"melee, Kinetic", "speed, Flow", "health, resistance", "energy, Energy/Tech",
                "crits, secrets, omens", "Veil, dreams, Echoes"};

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Profile p = profile();
            Font f = font();
            int col = w / 2;
            for (Stat s : Stat.values()) {
                int i = s.ordinal();
                int sx = x + (i % 2) * col, sy = y + (i / 2) * 16;
                g.text(f, s.name(), sx, sy + 3, Glass.a(Glass.LABEL, 1f), false);
                g.text(f, String.valueOf(p.stat(s)), sx + 26, sy + 3, Glass.a(Glass.TITLE, 1f), true);
                g.text(f, HINT[i], sx + 50, sy + 3, Glass.a(Glass.LABEL, 0.55f), false);
                if (p.unspent() > 0) {
                    int bx = sx + col - 20;
                    Glass.button(g, f, "+", bx, sy, 14, 13, Glass.inside(mx, my, bx, sy, 14, 13), true, Glass.EDGE);
                }
            }
            String pts = p.unspent() > 0 ? p.unspent() + " point" + (p.unspent() == 1 ? "" : "s") + " to spend" : "No points to spend";
            g.text(f, pts, x, y + 3 * 16 + 3, Glass.a(p.unspent() > 0 ? Glass.LANTERN : Glass.LABEL, 1f), false);
        }

        public boolean click(double mx, double my, int x, int y, int w) {
            if (profile().unspent() <= 0) return false;
            int col = w / 2;
            for (Stat s : Stat.values()) {
                int i = s.ordinal();
                int sx = x + (i % 2) * col, sy = y + (i / 2) * 16;
                if (Glass.inside(mx, my, sx + col - 20, sy, 14, 13)) {
                    send("spend", s.name());
                    return true;
                }
            }
            return false;
        }
    }

    static final class DirectivesSection implements StatusSections.Section {
        public String title() { return "DIRECTIVES"; }
        public int order() { return 20; }

        public int height(int w) {
            int n = profile().directives().size();
            return n == 0 ? 12 : n * 30;
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Profile p = profile();
            Font f = font();
            if (p.directives().isEmpty()) {
                g.text(f, "The Signal has nothing to suggest right now.", x, y, Glass.a(Glass.LABEL, 1f), false);
                return;
            }
            long today = clientPlayDay();
            for (Profile.DirectiveState d : p.directives()) {
                var def = Content.DIRECTIVES.get(d.id());
                String text = def == null ? d.id() : def.text();
                g.text(f, text, x, y, Glass.a(Glass.TITLE, 1f), false);
                long left = Math.max(0, d.expiresDay() - today + 1);
                if (d.accepted()) {
                    Glass.bar(g, x, y + 12, w - 90, 4, Directives.progress(p, d), Glass.GOOD, 1f);
                    g.text(f, "fades in " + left + "d", x + w - 84, y + 10, Glass.a(Glass.LABEL, 1f), false);
                } else {
                    Glass.button(g, f, "Accept", x, y + 10, 50, 13, Glass.inside(mx, my, x, y + 10, 50, 13), true, Glass.GOOD);
                    Glass.button(g, f, "Dismiss", x + 54, y + 10, 52, 13, Glass.inside(mx, my, x + 54, y + 10, 52, 13), true, Glass.LABEL);
                }
                y += 30;
            }
        }

        public boolean click(double mx, double my, int x, int y, int w) {
            for (Profile.DirectiveState d : profile().directives()) {
                if (!d.accepted()) {
                    if (Glass.inside(mx, my, x, y + 10, 50, 13)) {
                        ClientPacketDistributor.sendToServer(new SignalPayloads.Answer(Directives.ACTION + ":" + d.id(), true));
                        return true;
                    }
                    if (Glass.inside(mx, my, x + 54, y + 10, 52, 13)) {
                        ClientPacketDistributor.sendToServer(new SignalPayloads.Answer(Directives.ACTION + ":" + d.id(), false));
                        return true;
                    }
                }
                y += 30;
            }
            return false;
        }
    }

    static long clientPlayDay() {
        // The client doesn't know play-days exactly; directives store absolute days, so approximate with the profile's
        // day log which the server keeps current.
        return Math.max(0, profile().day().day());
    }

    static final class RegimenSection implements StatusSections.Section {
        public String title() { return "DAILY REGIMEN"; }
        public int order() { return 25; }
        public boolean visible() { return !profile().regimen().drills().isEmpty(); }
        public int height(int w) { return profile().regimen().drills().size() * 12; }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            var r = profile().regimen();
            for (String id : r.drills()) {
                var def = Content.DRILLS.get(id);
                boolean done = r.done().contains(id);
                g.text(font(), (done ? "✓ " : "· ") + (def == null ? id : def.text()), x, y, Glass.a(done ? Glass.GOOD : Glass.TEXT, 1f), false);
                y += 12;
            }
        }
    }

    /** Lifetime records from the counters, two columns, only what you've actually done. */
    static final class Records implements StatusSections.Section {
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
        public int height(int w) { return (lines().size() + 1) / 2 * 12; }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            List<String[]> l = lines();
            int col = w / 2;
            for (int i = 0; i < l.size(); i++) {
                int cx = x + (i % 2) * col, cy = y + (i / 2) * 12;
                String v = l.get(i)[1];
                g.text(font(), l.get(i)[0], cx, cy, Glass.a(Glass.LABEL, 1f), false);
                g.text(font(), v, cx + col - 8 - font().width(v), cy, Glass.a(Glass.TEXT, 1f), false);
            }
        }
    }

    // ---- Truths tab -------------------------------------------------------------------------------------------------

    static final class PathChoice implements StatusSections.Section {
        public String title() { return "SECOND AWAKENING · CHOOSE WHO YOU BECOME"; }
        public int order() { return 0; }
        public boolean visible() { return !profile().pathsOffered().isEmpty(); }

        public int height(int w) {
            int h = 0;
            for (String id : profile().pathsOffered()) h += cardHeight(id, w);
            return h;
        }

        private static int cardHeight(String id, int w) {
            var d = Content.PATHS.get(id);
            if (d == null) return 0;
            return 16 + wrap(d.description(), w - 80).size() * 10 + evidence(d).size() * 10 + 12;
        }

        static List<String> evidence(Defs.PathDef d) {
            Profile p = profile();
            List<String> out = new ArrayList<>();
            for (var e : d.evidence().entrySet()) {
                long v = p.counter(e.getKey());
                if (v > 0) out.add(String.format(e.getValue(), v));
            }
            return out;
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Font f = font();
            for (String id : profile().pathsOffered()) {
                var d = Content.PATHS.get(id);
                if (d == null) continue;
                int h = cardHeight(id, w);
                Glass.panel(g, x, y, w, h - 6, d.colour(), 1f);
                g.text(f, d.name(), x + 8, y + 5, Glass.a(d.colour(), 1f), true);
                int ty = y + 17;
                for (var line : wrap(d.description(), w - 80)) {
                    g.text(f, line, x + 8, ty, Glass.a(Glass.TEXT, 1f), false);
                    ty += 10;
                }
                for (String ev : evidence(d)) {
                    g.text(f, "· " + ev, x + 8, ty, Glass.a(Glass.LABEL, 1f), false);
                    ty += 10;
                }
                Glass.button(g, f, "Choose", x + w - 62, y + 5, 54, 14, Glass.inside(mx, my, x + w - 62, y + 5, 54, 14), true, d.colour());
                y += h;
            }
        }

        public boolean click(double mx, double my, int x, int y, int w) {
            for (String id : profile().pathsOffered()) {
                if (Glass.inside(mx, my, x + w - 62, y + 5, 54, 14)) {
                    send("path", id);
                    return true;
                }
                y += cardHeight(id, w);
            }
            return false;
        }
    }

    static final class TruthList implements StatusSections.Section {
        enum Kind { DECLARED, OFFERED, TRUE }

        private final Kind kind;

        TruthList(Kind kind) {
            this.kind = kind;
        }

        List<String> ids() {
            Profile p = profile();
            return switch (kind) {
                case DECLARED -> p.truthsDeclared();
                case OFFERED -> p.truthsOffered();
                case TRUE -> new ArrayList<>(p.truthsTrue());
            };
        }

        public String title() {
            return switch (kind) {
                case DECLARED -> "DECLARED · " + profile().truthsDeclared().size() + " / 3";
                case OFFERED -> "WAITING TO BE DECLARED";
                case TRUE -> "TRUE";
            };
        }

        public int order() { return 10 + kind.ordinal() * 10; }

        public boolean visible() { return kind != Kind.OFFERED || !ids().isEmpty(); }

        public int height(int w) {
            int n = ids().size();
            if (n == 0) return 12;
            return n * (kind == Kind.DECLARED ? 32 : 24);
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Font f = font();
            Profile p = profile();
            List<String> ids = ids();
            if (ids.isEmpty()) {
                String msg = kind == Kind.DECLARED ? "Declare who you're becoming from the Truths waiting below."
                        : "None yet. Live one of your declared Truths.";
                g.text(f, msg, x, y, Glass.a(Glass.LABEL, 1f), false);
                return;
            }
            for (String id : ids) {
                var d = Content.TRUTHS.get(id);
                String text = d == null ? id : "“" + d.text() + "”";
                g.text(f, text, x, y, Glass.a(kind == Kind.TRUE ? Glass.GOOD : Glass.TITLE, 1f), true);
                switch (kind) {
                    case DECLARED -> {
                        g.text(f, "NOT TRUE YET", x + w - f.width("NOT TRUE YET"), y, Glass.a(Glass.AMBER, 1f), false);
                        if (d != null) {
                            { final int yy = y; d.hint().ifPresent(h -> g.text(f, h, x, yy + 11, Glass.a(Glass.LABEL, 1f), false)); }
                            Glass.bar(g, x, y + 22, w - 70, 3, d.condition().progress(p, null), Glass.AMBER, 1f);
                        }
                        Glass.button(g, f, "Withdraw", x + w - 62, y + 18, 62, 12, Glass.inside(mx, my, x + w - 62, y + 18, 62, 12), true, Glass.LABEL);
                    }
                    case OFFERED -> {
                        if (d != null) { final int yy = y; d.hint().ifPresent(h -> g.text(f, h, x, yy + 11, Glass.a(Glass.LABEL, 1f), false)); }
                        boolean can = p.truthsDeclared().size() < 3;
                        Glass.button(g, f, "Declare", x + w - 56, y, 56, 13, Glass.inside(mx, my, x + w - 56, y, 56, 13), can, Glass.AMBER);
                    }
                    case TRUE -> g.text(f, "TRUE", x + w - f.width("TRUE"), y, Glass.a(Glass.GOOD, 1f), false);
                }
                y += kind == Kind.DECLARED ? 32 : 24;
            }
        }

        public boolean click(double mx, double my, int x, int y, int w) {
            for (String id : ids()) {
                if (kind == Kind.DECLARED && Glass.inside(mx, my, x + w - 62, y + 18, 62, 12)) {
                    send("undeclare", id);
                    return true;
                }
                if (kind == Kind.OFFERED && Glass.inside(mx, my, x + w - 56, y, 56, 13) && profile().truthsDeclared().size() < 3) {
                    send("declare", id);
                    return true;
                }
                y += kind == Kind.DECLARED ? 32 : 24;
            }
            return false;
        }
    }

    static final class Titles implements StatusSections.Section {
        public String title() { return "TITLES · " + profile().titles().size(); }
        public int order() { return 40; }

        public int height(int w) {
            int n = profile().titles().size();
            return n == 0 ? 12 : n * 22;
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Font f = font();
            Profile p = profile();
            if (p.titles().isEmpty()) {
                g.text(f, "Titles are earned by feats. You'll know when it happens.", x, y, Glass.a(Glass.LABEL, 1f), false);
                return;
            }
            for (String id : p.titles()) {
                var d = Content.TITLES.get(id);
                boolean equipped = p.title().map(id::equals).orElse(false);
                g.text(f, d == null ? id : d.name(), x, y, Glass.a(equipped ? Glass.AMBER : Glass.TITLE, 1f), true);
                if (d != null) g.text(f, d.description(), x, y + 10, Glass.a(Glass.LABEL, 1f), false);
                Glass.button(g, f, equipped ? "Equipped" : "Equip", x + w - 60, y, 60, 13,
                        Glass.inside(mx, my, x + w - 60, y, 60, 13), !equipped, Glass.AMBER);
                y += 22;
            }
        }

        public boolean click(double mx, double my, int x, int y, int w) {
            Profile p = profile();
            for (String id : p.titles()) {
                if (Glass.inside(mx, my, x + w - 60, y, 60, 13) && !p.title().map(id::equals).orElse(false)) {
                    send("title", id);
                    return true;
                }
                y += 22;
            }
            return false;
        }
    }

    static final class Latents implements StatusSections.Section {
        public String title() { return "LATENTS FOUND · " + profile().latents().size() + " / ??"; }
        public int order() { return 50; }

        public int height(int w) {
            int n = profile().latents().size();
            return n == 0 ? 12 : n * 22;
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Font f = font();
            Profile p = profile();
            if (p.latents().isEmpty()) {
                g.text(f, "Something in you is asleep. Do unusual things.", x, y, Glass.a(Glass.LABEL, 1f), false);
                return;
            }
            for (String id : p.latents()) {
                var d = Content.LATENTS.get(id);
                g.text(f, d == null ? id : d.name(), x, y, Glass.a(Glass.VIOLET, 1f), true);
                if (d != null) g.text(f, d.description(), x, y + 10, Glass.a(Glass.LABEL, 1f), false);
                y += 22;
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
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        s = s.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * The mystery board: cards grouped by category in columns, threads between linked cards. Cards the player has only
     * heard of show as "?".
     */
    static final class CodexBoard implements StatusSections.Section {
        private static final int CARD_W = 128, CARD_H = 34, GAP = 8;

        public String title() { return "THE BOARD"; }
        public int order() { return 0; }

        private static Map<String, List<CodexEntries.Entry>> grouped() {
            Map<String, List<CodexEntries.Entry>> m = new java.util.LinkedHashMap<>();
            for (var e : CodexEntries.all()) m.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
            return m;
        }

        public boolean visible() { return !CodexEntries.all().isEmpty(); }

        public int height(int w) {
            int cols = Math.max(1, w / (CARD_W + GAP));
            int rows = 0;
            for (var list : grouped().values()) rows += 1 + (list.size() + cols - 1) / cols;
            return rows * (CARD_H + GAP);
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
            Font f = font();
            int cols = Math.max(1, w / (CARD_W + GAP));
            Map<String, int[]> pos = new HashMap<>();
            List<CodexEntries.Entry> all = new ArrayList<>();
            int cy = y;
            for (var e : grouped().entrySet()) {
                g.text(f, e.getKey().toUpperCase(), x, cy + CARD_H - 10, Glass.a(Glass.LANTERN, 1f), false);
                cy += CARD_H + GAP - 20;
                int i = 0;
                for (var entry : e.getValue()) {
                    int cx = x + (i % cols) * (CARD_W + GAP);
                    int ry = cy + (i / cols) * (CARD_H + GAP);
                    pos.put(entry.id(), new int[]{cx, ry});
                    all.add(entry);
                    i++;
                }
                cy += ((e.getValue().size() + cols - 1) / cols) * (CARD_H + GAP) + 20;
            }
            // threads first, under the cards
            for (var entry : all) {
                int[] a = pos.get(entry.id());
                for (String link : entry.links()) {
                    int[] b = pos.get(link);
                    if (b == null) continue;
                    line(g, a[0] + CARD_W / 2, a[1] + CARD_H / 2, b[0] + CARD_W / 2, b[1] + CARD_H / 2, Glass.a(Glass.LANTERN, 0.45f));
                }
            }
            for (var entry : all) {
                int[] a = pos.get(entry.id());
                boolean hover = Glass.inside(mx, my, a[0], a[1], CARD_W, CARD_H);
                Glass.panel(g, a[0], a[1], CARD_W, CARD_H, entry.found() ? Glass.EDGE : Glass.LABEL, hover ? 1f : 0.85f);
                g.text(f, entry.found() ? entry.title() : "?", a[0] + 5, a[1] + 4, Glass.a(entry.found() ? Glass.TITLE : Glass.LABEL, 1f), false);
                var body = wrap(entry.found() ? entry.body() : entry.title(), CARD_W - 10);
                int ty = a[1] + 15;
                for (int k = 0; k < Math.min(2, body.size()); k++) {
                    g.text(f, body.get(k), a[0] + 5, ty, Glass.a(Glass.LABEL, 1f), false);
                    ty += 9;
                }
                if (hover && body.size() > 2) {
                    g.setTooltipForNextFrame(f, Component.literal(entry.body()), mx, my);
                }
            }
        }

        /** A simple stepped line (GUI has no diagonal primitive). */
        private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int col) {
            int mxp = (x0 + x1) / 2;
            g.horizontalLine(Math.min(x0, mxp), Math.max(x0, mxp), y0, col);
            g.verticalLine(mxp, Math.min(y0, y1), Math.max(y0, y1), col);
            g.horizontalLine(Math.min(mxp, x1), Math.max(mxp, x1), y1, col);
        }
    }
}
