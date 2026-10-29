package dev.awaken.bells.client;

import dev.awaken.bells.BellNet;
import dev.awaken.progression.Rank;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Bell Book in the Codex (SPEC §43.7): The Hundred (a ten by ten board, each bell lit in its rank's colour once it's
 * yours, dim once someone you live with holds it, ringed in amber while you have a lead), Leads (in the System's words,
 * with Track) and Your Tower (what hangs, what's next, what your bells do, your peals, and the song to play back). The
 * server builds the words; the page asks for them while it's open, every two seconds.
 */
final class BellBookSection extends UiSection {
    static BellNet.@Nullable Page page;
    private static long asked;

    private static final int DIM = 0xFF5A6678;

    enum Part { HUNDRED, LEADS, TOWER }

    private final Part part;

    private BellBookSection(Part part) {
        this.part = part;
    }

    static void register() {
        for (Part p : Part.values()) StatusSections.add(StatusSections.Tab.CODEX, new BellBookSection(p));
    }

    static void ask() {
        long now = System.currentTimeMillis();
        if (now - asked < 2000) return;
        asked = now;
        ClientPacketDistributor.sendToServer(new BellNet.Ask());
    }

    static void forget() {
        page = null;
        asked = 0;
    }

    @Override
    public String title() {
        return switch (part) {
            case HUNDRED -> {
                BellNet.Page p = page;
                int n = 0;
                if (p != null) for (List<String> r : p.hundred()) if (r.get(3).equals("held")) n++;
                yield "THE HUNDRED BELLS · " + n + " / 100";
            }
            case LEADS -> "BELL LEADS";
            case TOWER -> "YOUR TOWER";
        };
    }

    @Override
    public int order() {
        return 200 + part.ordinal();
    }

    @Override
    public boolean visible() {
        if (part == Part.HUNDRED) ask();
        BellNet.Page p = page;
        if (p == null) return false;
        boolean any = false;
        for (List<String> r : p.hundred()) if (!r.get(3).isEmpty()) any = true;
        return switch (part) {
            case HUNDRED, TOWER -> any;
            case LEADS -> any && !p.leads().isEmpty();
        };
    }

    @Override
    public StatusSections.@Nullable Shelf shelf() {
        return null;
    }

    @Override
    protected void build(Ui ui) {
        BellNet.Page p = page;
        if (p == null) return;
        switch (part) {
            case HUNDRED -> hundred(ui, p.hundred());
            case LEADS -> leads(ui, p.leads());
            case TOWER -> tower(ui, p.tower());
        }
    }

    // ---- the hundred ------------------------------------------------------------------------------------------------

    private static void hundred(Ui ui, List<List<String>> rows) {
        int cols = 10, gap = 2, ch = 13;
        int cw = Math.max(14, (ui.width - gap * (cols - 1)) / cols);
        int top = ui.y();
        List<String> over = null;
        for (int i = 0; i < rows.size() && i < 100; i++) {
            List<String> r = rows.get(i);
            int cx = ui.x + (i % cols) * (cw + gap), cy = top + (i / cols) * (ch + gap);
            String st = r.get(3);
            int rank = rankColour(r.get(2));
            int bg = switch (st) {
                case "held" -> 0xF0000000 | mix(rank, 0x0F1724, 0.55);
                case "hung" -> 0xD0000000 | mix(rank, 0x0F1724, 0.25);
                case "lead" -> 0xD0000000 | 0x2A2416;
                default -> 0xA0000000 | 0x111826;
            };
            ui.fill(cx, cy, cx + cw, cy + ch, bg);
            if (st.equals("lead")) {
                int a = 0xFF000000 | Glass.AMBER;
                ui.fill(cx, cy, cx + cw, cy + 1, a);
                ui.fill(cx, cy + ch - 1, cx + cw, cy + ch, a);
                ui.fill(cx, cy, cx + 1, cy + ch, a);
                ui.fill(cx + cw - 1, cy, cx + cw, cy + ch, a);
            }
            boolean hover = ui.hover(cx, cy, cw, ch);
            if (hover) {
                over = r;
                ui.fill(cx, cy, cx + cw, cy + ch, 0x30FFFFFF);
            }
            String num = r.get(0).substring(1);
            int tc = st.equals("held") ? 0xFFFFFFFF : st.isEmpty() ? DIM : 0xFF000000 | Glass.LABEL;
            ui.textAt(num, cx + (cw - ui.font().width(num)) / 2, cy + 3, cw, tc, false);
        }
        ui.gap(10 * (ch + gap) + 2);
        // two lines under the board, always reserved, so hovering never moves the page
        String one, two;
        if (over == null) {
            // two short lines: the board is narrow on a small window, and a cut-off line hides the key
            one = "Hover a bell to see it. Lit bells are yours.";
            two = "Dim: a housemate has it. Ringed: you have a lead.";
        } else {
            String rank = Rank.valueOf(over.get(2)).label + "-rank";
            one = over.get(0) + " · " + (over.get(1).isEmpty() ? "unknown" : over.get(1)) + " · " + rank;
            two = switch (over.get(3)) {
                case "held" -> "Rung home " + over.get(4) + ".";
                case "hung" -> "Someone you live with rang it home. It hangs in your tower.";
                case "lead" -> "You have a lead on it. It's in your leads below.";
                default -> "Not found yet.";
            };
        }
        int y = ui.y();
        ui.textAt(one, ui.x, y, ui.width, 0xFF000000 | Glass.TEXT, false);
        ui.textAt(two, ui.x, y + Ui.LINE, ui.width, 0xFF000000 | Glass.LABEL, false);
        ui.gap(2 * Ui.LINE);
    }

    private static int rankColour(String rank) {
        try {
            return Rank.valueOf(rank).colour & 0xFFFFFF;
        } catch (IllegalArgumentException e) {
            return Glass.EDGE;
        }
    }

    /** {@code a} blended toward {@code b} ({@code t} of a), RGB. */
    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * t + ((b >> 16) & 255) * (1 - t));
        int g = (int) (((a >> 8) & 255) * t + ((b >> 8) & 255) * (1 - t));
        int bl = (int) ((a & 255) * t + (b & 255) * (1 - t));
        return r << 16 | g << 8 | bl;
    }

    // ---- leads ------------------------------------------------------------------------------------------------------

    private static void leads(Ui ui, List<List<String>> rows) {
        for (List<String> r : rows) {
            String no = r.get(0), title = r.get(1), lines = r.get(2), hows = r.get(3);
            boolean trackable = r.get(4).equals("1"), tracked = r.get(5).equals("1");
            ui.card(tracked ? Glass.AMBER : Glass.EDGE, body -> {
                body.text(no + " · " + title, 0xFF000000 | Glass.TITLE);
                if (!hows.isEmpty()) body.text(hows, 0xFF000000 | Glass.AMBER);
                for (String l : lines.split("\n")) if (!l.isEmpty()) body.text(l, 0xFF000000 | Glass.TEXT);
                if (trackable) {
                    Ui.Flow f = body.flow();
                    if (tracked) f.chip("Tracking", 0xFF000000 | Glass.AMBER);
                    else if (f.button("Track", Glass.EDGE, true)) ClientPacketDistributor.sendToServer(new BellNet.Track(Integer.parseInt(no.substring(1))));
                    f.end();
                }
            });
        }
    }

    // ---- the tower --------------------------------------------------------------------------------------------------

    private static void tower(Ui ui, List<List<String>> rows) {
        if (rows.isEmpty()) return;
        List<String> head = rows.get(0);
        int n = Integer.parseInt(head.get(0)), stage = Integer.parseInt(head.get(1));
        long lo = Long.parseLong(head.get(3)), hi = Long.parseLong(head.get(4));
        boolean frame = head.get(5).equals("1");
        String what = !frame ? "No bell frame set down yet." : switch (stage) {
            case 2 -> "A stone bell tower.";
            case 1 -> "A timber belfry.";
            default -> "A bell frame.";
        };
        ui.text(n + (n == 1 ? " bell hangs" : " bells hang") + " at home. " + what, 0xFF000000 | Glass.TITLE);
        ui.text(head.get(2), 0xFF000000 | Glass.LABEL);
        ui.text("It rings your song at dawn and dusk, every bell in its place and a dull knock where one is missing.", 0xFF000000 | Glass.LABEL);
        if (n > 0) {
            Ui.Flow f = ui.flow();
            if (SongPlayer.playing()) {
                if (f.button("Stop the song", Glass.EDGE, true)) SongPlayer.stop();
            } else if (f.button("Play the song", Glass.AMBER, true)) SongPlayer.start(new BellNet.Song(BlockPos.ZERO, lo, hi), true);
            f.end();
        }
        boolean voices = false;
        for (List<String> r : rows) {
            if (!r.get(0).equals("voice")) continue;
            if (!voices) {
                ui.gap(3);
                ui.text("What your bells do while they hang", 0xFF000000 | Glass.TITLE);
                voices = true;
            }
            int c = Integer.parseInt(r.get(2));
            ui.text("· " + r.get(1) + (c > 1 ? " (" + c + " bells)" : ""), 0xFF000000 | Glass.TEXT, 6, false);
        }
        boolean peals = false;
        for (List<String> r : rows) {
            if (!r.get(0).equals("peal")) continue;
            if (!peals) {
                ui.gap(3);
                ui.text("Peals for your handbell", 0xFF000000 | Glass.TITLE);
                peals = true;
            }
            boolean have = r.get(3).equals("have");
            ui.card(have ? Glass.GOOD : 0x3A4658, body -> {
                body.text(r.get(1), 0xFF000000 | (have ? Glass.TITLE : Glass.LABEL));
                body.text(r.get(2), 0xFF000000 | Glass.TEXT);
                body.text(have ? "Sneak and use your handbell to set it, then use it to ring." : r.get(3), 0xFF000000 | (have ? Glass.GOOD : Glass.AMBER));
            });
        }
    }
}
