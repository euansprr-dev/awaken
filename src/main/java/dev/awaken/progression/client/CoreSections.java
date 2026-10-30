package dev.awaken.progression.client;

import dev.awaken.progression.Directives;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
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
        // the Status page is a sheet about you: the hero, then the six stats (SPEC §30.3)
        StatusSections.add(StatusSections.Tab.STATUS, new StatusSheet.Hero());
        StatusSections.add(StatusSections.Tab.STATUS, new StatusSheet.StatLine());
        StatusSections.add(StatusSections.Tab.JOURNAL, new JournalFilters());
        StatusSections.add(StatusSections.Tab.JOURNAL, new WaitingSection());
        dev.awaken.core.api.MapMarks.register(WaitingSection::marks);
        // (SPEC §27.13) the Truths page's sections stand on Status until they become Status's own
        StatusSections.add(StatusSections.Tab.STATUS, StatusSections.placed(new RoadSection(), 735, null));
        StatusSections.add(StatusSections.Tab.JOURNAL, new Records());
        StatusSections.add(StatusSections.Tab.STATUS, StatusSections.placed(new PathChoice(), 700, null));
        StatusSections.add(StatusSections.Tab.STATUS, StatusSections.placed(new TruthList(TruthList.Kind.TRUE), 710, null));
        StatusSections.add(StatusSections.Tab.STATUS, StatusSections.placed(new TruthList(TruthList.Kind.NEAR), 720, null));
        StatusSections.add(StatusSections.Tab.STATUS, StatusSections.placed(new Titles(), 740, null));
        StatusSections.add(StatusSections.Tab.STATUS, StatusSections.placed(new Latents(), 750, null));
        // (SPEC §27.13) the Codex is the Journal's now: what you've found
        StatusSections.add(StatusSections.Tab.JOURNAL, StatusSections.placed(new CodexBoard(), 600, StatusSections.Shelf.LORE));
        Catalogue.count(StatusSections.Shelf.LORE, CodexBoard::found);
        // open-ended finds count; the ores are a fixed set (SPEC §27.13)
        Catalogue.count(StatusSections.Shelf.LANDS, () -> Catalogue.discovered("awaken:discovered/biome/"));
        Catalogue.count(StatusSections.Shelf.PLACES, () -> Catalogue.discovered("awaken:discovered/landmark/", "awaken:discovered/lair/",
                "awaken:discovered/wonder/", "awaken:discovered/secret/"));
        Catalogue.fixed(StatusSections.Shelf.ORES, CoreSections::ores);
        CodexEntries.addSource(CoreSections::insightEntries);
    }

    /** The Journal's Ores (SPEC §27.13): every ore in order, a "?" until you've mined one, then what it's for. */
    static List<Catalogue.Slot> ores() {
        Profile p = profile();
        List<Catalogue.Slot> out = new ArrayList<>();
        for (var o : dev.awaken.progression.ProgressionModule.OreXp.values()) {
            if (p.counter("awaken:discovered/ore/" + o.id()) <= 0) {
                out.add(Catalogue.Slot.unknown());
                continue;
            }
            out.add(new Catalogue.Slot(o.title, true, o.hint + ".", new net.minecraft.world.item.ItemStack(oreIcon(o))));
        }
        return out;
    }

    private static net.minecraft.world.item.Item oreIcon(dev.awaken.progression.ProgressionModule.OreXp o) {
        return switch (o) {
            case COAL -> net.minecraft.world.item.Items.COAL;
            case COPPER -> net.minecraft.world.item.Items.RAW_COPPER;
            case IRON -> net.minecraft.world.item.Items.RAW_IRON;
            case GOLD -> net.minecraft.world.item.Items.RAW_GOLD;
            case REDSTONE -> net.minecraft.world.item.Items.REDSTONE;
            case LAPIS -> net.minecraft.world.item.Items.LAPIS_LAZULI;
            case EMERALD -> net.minecraft.world.item.Items.EMERALD;
            case DIAMOND -> net.minecraft.world.item.Items.DIAMOND;
            case DEEPGLASS -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id("deepglass_shard"));
            case QUARTZ -> net.minecraft.world.item.Items.QUARTZ;
            case DEBRIS -> net.minecraft.world.item.Items.ANCIENT_DEBRIS;
            case AMETHYST -> net.minecraft.world.item.Items.AMETHYST_SHARD;
        };
    }

    private static String plural(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    // ---- Status tab -------------------------------------------------------------------------------------------------

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

        @Override
        public StatusSections.Shelf shelf() { return StatusSections.Shelf.STORY; }
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

    /**
     * The Truths on Status (SPEC §27.13): they come true by themselves, so there's nothing to declare. TRUE lists yours and
     * what each gave; CLOSEST TO TRUE shows the three you're nearest, with what each takes and gives.
     */
    static final class TruthList extends UiSection {
        enum Kind { TRUE, NEAR }

        /** Truths shown under CLOSEST TO TRUE. */
        static final int NEAR_SHOWN = 3;

        private final Kind kind;

        TruthList(Kind kind) {
            this.kind = kind;
        }

        List<String> ids() {
            Profile p = profile();
            if (kind == Kind.TRUE) return new ArrayList<>(p.truthsTrue());
            if (!dev.awaken.progression.Progression.truthsOpen(p)) return List.of();
            List<String> out = new ArrayList<>();
            for (var e : Content.TRUTHS.map.entrySet()) {
                String key = e.getKey().toString();
                var d = e.getValue();
                if (p.truthsTrue().contains(key) || p.rank().ordinal() < d.minRank().ordinal()) continue;
                if (d.emergent().isPresent() && !d.emergent().get().test(p, null)) continue; // a hidden one stays hidden
                out.add(key);
            }
            out.sort(java.util.Comparator.comparingDouble((String k) -> -Content.TRUTHS.get(k).condition().progress(p, null)).thenComparing(k -> k));
            return out.size() > NEAR_SHOWN ? List.copyOf(out.subList(0, NEAR_SHOWN)) : out;
        }

        public String title() {
            return kind == Kind.TRUE ? "TRUTHS · " + profile().truthsTrue().size() + " OF " + Content.TRUTHS.map.size() : "CLOSEST TO TRUE";
        }

        public int order() { return 10 + kind.ordinal() * 10; }

        public boolean visible() { return kind == Kind.TRUE || !ids().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            Profile p = profile();
            List<String> ids = ids();
            if (ids.isEmpty()) {
                ui.text(dev.awaken.progression.Progression.truthsOpen(p)
                        ? "None yet. A Truth comes true by itself when you live it; the closest are below."
                        : "Truths open at level 5. Anything you've done by then counts.", Glass.LABEL);
                return;
            }
            // each Truth on its own card (SPEC §30.6): the words in white, what it takes, what it gives; green is true
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                var d = Content.TRUTHS.get(id);
                boolean got = kind == Kind.TRUE;
                ui.card(got ? Glass.GOOD : Glass.AMBER, c -> {
                    c.text(d == null ? id : "“" + d.text() + "”", got ? Glass.GOOD : Glass.TITLE, 0, true);
                    if (d == null) return;
                    if (!got) d.hint().ifPresent(h -> c.text(h, Glass.LABEL));
                    c.text((got ? "Granted: " : "Gives: ") + Rewards.describe(d.reward()), got ? Glass.LABEL : Glass.AMBER);
                    if (!got) c.bar(d.condition().progress(p, null), Glass.AMBER);
                });
                if (i < ids.size() - 1) ui.gap(4);
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
            for (var e : CodexEntries.all()) if (Catalogue.onBoard(e.category())) m.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
            return m;
        }

        /** Lore and places found: the board's found entries (creatures and cave regions have their own categories). */
        static int[] found() {
            int n = 0;
            for (var e : CodexEntries.all()) if (e.found() && Catalogue.onBoard(e.category())) n++;
            return new int[]{n, -1};
        }

        public boolean visible() { return !grouped().isEmpty(); }

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
