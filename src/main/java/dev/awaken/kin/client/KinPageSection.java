package dev.awaken.kin.client;

import dev.awaken.kin.KinKind;
import dev.awaken.kin.KinNet;
import dev.awaken.progression.client.Catalogue;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The band's Hearth page (SPEC §43.6): Home (what the Hearth can't do yet, each beast need with its rumour and Track; the
 * next tier; the Great Belfry, dim), Your beasts, and Kin. The server builds the words; the page asks for them while
 * it's open, every two seconds.
 */
final class KinPageSection extends UiSection {
    static KinNet.@Nullable Page page;
    private static long asked;

    private static final int DIM = 0xFF6E7C90;

    enum Part { HOME, BEASTS, KIN }

    private final Part part;

    private KinPageSection(Part part) {
        this.part = part;
    }

    static void register() {
        for (Part p : Part.values()) StatusSections.add(StatusSections.Tab.HEARTH, new KinPageSection(p));
    }

    static void ask() {
        long now = System.currentTimeMillis();
        if (now - asked < 2000) return;
        asked = now;
        ClientPacketDistributor.sendToServer(new KinNet.Ask());
    }

    /**
     * The Journal's Beasts category (SPEC §27.13): every kind that lives in the world, a "?" until you've met one, then
     * where you met it, the work it could do and what it wants once you've watched it.
     */
    static List<Catalogue.Slot> slots() {
        ask();
        KinNet.Page p = page;
        Map<String, List<String>> met = new HashMap<>();
        if (p != null) for (List<String> r : p.kin()) if (r.get(2).equals("1")) met.put(r.get(0), r);
        List<Catalogue.Slot> out = new ArrayList<>();
        for (KinKind k : KinKind.livingNow()) {
            List<String> r = met.get(k.id());
            if (r == null) {
                out.add(Catalogue.Slot.unknown());
                continue;
            }
            String wants = r.get(5).equals("?") ? "" : "\nWhat it wants: " + r.get(5).replace("\n", " ");
            out.add(new Catalogue.Slot(k.title, true, r.get(3) + "\n" + r.get(4) + wants, ItemStack.EMPTY));
        }
        return out;
    }

    static void forget() {
        page = null;
        asked = 0;
    }

    @Override
    public String title() {
        return switch (part) {
            case HOME -> "HOME";
            case BEASTS -> "YOUR BEASTS";
            case KIN -> "KIN";
        };
    }

    @Override
    public int order() {
        return part.ordinal() * 10;
    }

    @Override
    public boolean visible() {
        if (part == Part.HOME) ask();
        KinNet.Page p = page;
        if (p == null) return false;
        return switch (part) {
            case HOME -> !p.home().isEmpty();
            case BEASTS -> p.home().size() > 1;
            case KIN -> !p.kin().isEmpty();
        };
    }

    @Override
    public StatusSections.@Nullable Shelf shelf() {
        return null;
    }

    @Override
    protected void build(Ui ui) {
        KinNet.Page p = page;
        if (p == null) return;
        switch (part) {
            case HOME -> home(ui, p.home());
            case BEASTS -> beasts(ui, p.beasts());
            case KIN -> kin(ui, p.kin());
        }
    }

    private static void home(Ui ui, List<List<String>> rows) {
        for (List<String> r : rows) {
            String text = r.get(0), rumour = r.get(1), kind = r.get(2), state = r.get(3);
            switch (state) {
                case "head" -> ui.text(text, 0xFF000000 | Glass.TITLE);
                case "tier" -> {
                    ui.gap(3);
                    ui.text(text, 0xFF000000 | Glass.LABEL);
                }
                case "met" -> ui.text("✓ " + text, 0xFF000000 | Glass.GOOD, 6, false);
                case "dim" -> {
                    ui.gap(3);
                    ui.text(text, DIM);
                }
                default -> {
                    if (rumour.isEmpty() && kind.isEmpty()) {
                        ui.text("· " + text, 0xFF000000 | Glass.TEXT, 6, false);
                        continue;
                    }
                    ui.card(Glass.AMBER, body -> {
                        body.text(text, 0xFF000000 | Glass.TEXT);
                        if (!rumour.isEmpty()) body.text(rumour, 0xFF000000 | Glass.LABEL);
                        if (!kind.isEmpty()) {
                            Ui.Flow f = body.flow();
                            if (f.button("Track", Glass.EDGE, true)) ClientPacketDistributor.sendToServer(new KinNet.Track(kind));
                            f.end();
                        }
                    });
                }
            }
        }
    }

    private static void beasts(Ui ui, List<List<String>> rows) {
        if (rows.isEmpty()) {
            ui.text("Nobody lives with you yet. Beasts that trust you will follow you home if there's a nook they like.", 0xFF000000 | Glass.LABEL);
            return;
        }
        for (List<String> r : rows) {
            String title = r.get(1), doing = r.get(2), feeling = r.get(3), made = r.get(4);
            ui.card(Glass.GOOD, body -> {
                body.text(title, 0xFF000000 | Glass.TITLE);
                body.text("Now: " + doing + ".", 0xFF000000 | Glass.TEXT);
                body.text(cap(feeling) + ".", 0xFF000000 | Glass.LABEL);
                if (!made.isEmpty()) body.text(cap(made) + ".", 0xFF000000 | Glass.AMBER);
            });
        }
    }

    private static void kin(Ui ui, List<List<String>> rows) {
        for (List<String> r : rows) {
            String id = r.get(0), title = r.get(1), where = r.get(3), job = r.get(4), wants = r.get(5), step = r.get(6), line = r.get(7), rumour = r.get(8);
            boolean met = r.get(2).equals("1");
            int colour = parse(r.get(9));
            ui.card(met ? colour : 0x3A4658, body -> {
                body.text(met ? title : title + " · heard of", met ? 0xFF000000 | Glass.TITLE : DIM);
                body.text(where, 0xFF000000 | Glass.LABEL);
                body.text(job, 0xFF000000 | Glass.TEXT);
                if (met) {
                    body.text("What it wants:", 0xFF000000 | Glass.LABEL);
                    for (String w : wants.split("\n")) body.text(w, 0xFF000000 | Glass.TEXT, 6, false);
                    body.text("You and its kind: " + step, 0xFF000000 | Glass.AMBER);
                    if (!line.isEmpty()) body.text(line, 0xFF000000 | Glass.LABEL);
                }
                if (!rumour.isEmpty()) {
                    body.text(rumour, met ? 0xFF000000 | Glass.LABEL : DIM);
                    Ui.Flow f = body.flow();
                    if (f.button("Pin", Glass.EDGE, true)) ClientPacketDistributor.sendToServer(new KinNet.Track(id));
                    f.end();
                }
            });
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int parse(String hex) {
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return Glass.EDGE;
        }
    }
}
