package dev.awaken.signal.client;

import net.minecraft.client.gui.Font;

/** Shortens a line to a width with an ellipsis, at a word break where one is close (the task card's steps). */
final class SignalTextFit {
    private SignalTextFit() {}

    static String fit(Font font, String s, int room) {
        if (room <= 0) return "";
        if (font.width(s) <= room) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end).stripTrailing() + dots) > room) end--;
        // a word break within the last few characters reads better than a cut word
        int space = s.lastIndexOf(' ', end);
        if (space > 0 && end - space <= 6) end = space;
        return end <= 0 ? "" : s.substring(0, end).stripTrailing() + dots;
    }
}
