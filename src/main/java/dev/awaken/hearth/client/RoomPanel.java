package dev.awaken.hearth.client;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.player.RoomHere;
import dev.awaken.hearth.room.Reading;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The room panel (SPEC §28.2, PLAN 4.5): the room you stand in at home, read the way the Hearth reads it: its name and
 * grade, the six readings as pips, what it is for, and the one thing that would lift it most. Drawn into any
 * {@link Ui}: today the K Status page's first section while you stand in a room at home, and each room's card in the
 * Your Hearth screen; The Way Forward's glance hosts it later through {@link #visible()} and {@link #build(Ui)}.
 */
public final class RoomPanel {
    private RoomPanel() {}

    /** Bell-gold for the grade, by grade (Bare's is quiet, Grand's bright). */
    private static final int[] GRADE = {0xB9A27A, 0xF2C46B, 0xFFD88A, 0xFFF0C2};
    private static final int PIP_ON = 0xF2A541, PIP_OFF = 0x2B3446;

    /** The room the player stands in at a Hearth they belong to (synced), or {@link RoomHere#NONE}. */
    public static RoomHere here() {
        var p = Minecraft.getInstance().player;
        return p == null ? RoomHere.NONE : p.getData(HearthContent.HERE.get());
    }

    /** Whether the player stands in a read room of a Hearth they belong to. */
    public static boolean visible() {
        return here().in();
    }

    /** The room you stand in, as a card. */
    public static void build(Ui ui) {
        RoomHere h = here();
        if (!h.in()) return;
        card(ui, h.name(), h.grade(), h::reading, h.purpose(), h.lift(), true);
    }

    /** One room's card: name, grade, pips, purpose, the lift. {@code here}: it's the room you stand in. */
    static void card(Ui ui, String name, int grade, java.util.function.IntUnaryOperator reading, String purpose, String lift, boolean here) {
        int g = Math.max(0, Math.min(3, grade));
        String gradeLabel = Reading.Grade.values()[g].label.toUpperCase(java.util.Locale.ROOT);
        ui.card(GRADE[g], in -> {
            in.pair(capitalise(name), gradeLabel, Glass.a(Glass.TITLE, 1f), Glass.a(GRADE[g], 1f));
            if (!purpose.isEmpty()) in.text(purpose + (here ? " · you're in it" : ""), Glass.a(Glass.LABEL, 0.85f));
            in.gap(2);
            pips(in, reading);
            if (!lift.isEmpty()) {
                in.gap(1);
                in.text("Lift it: " + lift, Glass.a(Glass.AMBER, 1f));
            } else {
                in.gap(1);
                in.text("Nothing to lift: it's as good as a room gets.", Glass.a(Glass.GOOD, 0.9f));
            }
        });
    }

    /** The six readings in two rows of three: a name and three pips each. */
    private static void pips(Ui ui, java.util.function.IntUnaryOperator reading) {
        List<String> names = Reading.NAMES;
        int col = ui.width / 3;
        for (int row = 0; row < 2; row++) {
            int y = ui.y();
            for (int c = 0; c < 3; c++) {
                int i = row * 3 + c;
                int x = ui.x + c * col;
                ui.textAt(names.get(i), x, y + 1, col - 26, Glass.a(Glass.LABEL, 1f), false);
                int v = reading.applyAsInt(i);
                int px = x + col - 24;
                for (int k = 0; k < 3; k++) {
                    int x0 = px + k * 7;
                    ui.fill(x0, y + 2, x0 + 5, y + 7, Glass.a(k < v ? PIP_ON : PIP_OFF, k < v ? 1f : 0.9f));
                }
            }
            ui.gap(Ui.LINE);
        }
    }

    static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
