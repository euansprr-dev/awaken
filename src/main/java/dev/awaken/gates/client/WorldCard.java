package dev.awaken.gates.client;

import dev.awaken.core.api.Notices;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * A world floor's title card (SPEC §38.7): the world's name surfacing letter by letter from the middle, a rule in its
 * tier's colour drawn out under it, then its tier, mood and floor, then its one plain line. A first-ever find gets a
 * header with its rarity, a glint along the rule and a longer hold. One card, never a toast: it asks the notification
 * director for the moment (after the journey down's dark lifts) and holds everything else while it's up; if no moment
 * comes it's filed in the Journal as "Arrived · name".
 */
final class WorldCard {
    private WorldCard() {}

    static final long HOLD_MS = 6500, FIRST_MS = 9500, OUT_MS = 1200;
    static final int WAIT = 240, FIRST_WAIT = 600;
    private static final Object HOLD = new Object();

    private static WorldPayloads.Arrival pending, showing;
    private static int ticks, since;
    private static long from;
    private static List<String> lines = List.of();

    static void arrive(WorldPayloads.Arrival a) {
        end();
        pending = a;
        since = ticks;
    }

    static void reset() {
        end();
        pending = null;
    }

    private static void end() {
        if (showing != null) Notices.client().hold(HOLD, false);
        showing = null;
    }

    static void tick() {
        ticks++;
        if (showing != null) {
            WorldPayloads.Arrival here = GateWorldClient.current();
            boolean left = here == null || !here.world().equals(showing.world()) || here.floor() != showing.floor();
            if (left || Util.getMillis() - from > hold(showing) + OUT_MS) end();
        }
        if (pending == null || WorldJourney.dark() || ticks % 5 != 0) return;
        if (GateWorldClient.current() != pending) { // left before it could show
            pending = null;
            return;
        }
        Notices.Client n = Notices.client();
        if (n.claim(Notices.Tier.IMPORTANT, "gate_world")) {
            showing = pending;
            pending = null;
            from = Util.getMillis();
            lines = List.of();
            n.hold(HOLD, true);
        } else if (ticks - since > (pending.first() ? FIRST_WAIT : WAIT)) {
            n.file(Notices.Tier.AMBIENT, Component.literal("Arrived · " + pending.name()), Component.literal(pending.line()));
            pending = null;
        }
    }

    private static long hold(WorldPayloads.Arrival a) {
        return a.first() ? FIRST_MS : HOLD_MS;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        WorldPayloads.Arrival a = showing;
        Minecraft mc = Minecraft.getInstance();
        if (a == null || mc.gui.hud.isHidden()) return;
        long age = Util.getMillis() - from;
        float out = age < hold(a) ? 1 : 1 - (age - hold(a)) / (float) OUT_MS;
        if (out <= 0) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int tier = tierColour(a.tier());
        float scale = Mth.clamp(w / 190F, 2.0F, 3.2F);
        int cy = (int) (h * 0.26F);
        int nameH = (int) (9 * scale);
        int top = cy - (a.first() ? 30 : 18), bottom = cy + nameH + 46;

        // a band of dark behind it, soft at both edges
        float band = out * ease(age / 500F) * 0.5F;
        int mid = (top + bottom) / 2;
        g.fillGradient(0, top - 16, w, mid, argb(0, 0), argb(band, 0x050404));
        g.fillGradient(0, mid, w, bottom + 16, argb(band, 0x050404), argb(0, 0));

        if (a.first()) {
            String head = "A NEW WORLD" + (a.rarity().isEmpty() ? "" : " · " + a.rarity().toUpperCase(Locale.ROOT));
            float ha = out * ease((age - 100) / 600F);
            int hw = spacedWidth(font, head, 2);
            spaced(g, font, head, w / 2 - hw / 2, cy - 24, 2, argb(ha, rarityColour(a.rarity())));
        }

        // the name, each letter surfacing from the middle outward
        String name = a.name();
        int nw = font.width(name);
        g.pose().pushMatrix();
        g.pose().translate(w / 2F, cy);
        g.pose().scale(scale, scale);
        int x = -nw / 2;
        float midIdx = (name.length() - 1) / 2F;
        for (int i = 0; i < name.length(); i++) {
            String c = String.valueOf(name.charAt(i));
            float k = ease((age - 120 - Math.abs(i - midIdx) * 38) / 280F);
            if (k > 0) g.text(font, c, x, (int) ((1 - k) * 3), argb(out * k, 0xF4ECD8), true);
            x += font.width(c);
        }
        g.pose().popMatrix();

        // the rule, drawn out from the middle, with a diamond at its heart
        int ruleY = cy + nameH + 6;
        int half = (int) ((nw * scale / 2 + 26) * ease((age - 300) / 520F));
        float ra = out * 0.8F;
        if (half > 0) {
            g.fill(w / 2 - half, ruleY, w / 2 + half, ruleY + 1, argb(ra, tier));
            diamond(g, w / 2, ruleY, argb(ra, tier));
            if (a.first()) { // a glint running along it
                float gx = ((age - 900) % 1600) / 1600F;
                if (age > 900) {
                    int px = w / 2 - half + (int) (gx * 2 * half);
                    g.fill(px - 6, ruleY, px + 6, ruleY + 1, argb(ra * (1 - Math.abs(gx - 0.5F) * 2), 0xFFFFFF));
                }
            }
        }

        // tier, mood and floor
        String sub = sub(a);
        float sa = out * ease((age - 480) / 500F);
        int sw = spacedWidth(font, sub, 1);
        spaced(g, font, sub, w / 2 - sw / 2, ruleY + 6, 1, argb(sa * 0.9F, tier));

        // the line
        if (lines.isEmpty() && !a.line().isEmpty()) lines = SignalText.wrap(a.line(), Math.min(300, w - 40), font::width);
        float la = out * ease((age - 900) / 600F);
        int ly = ruleY + 20;
        for (String l : lines) {
            g.text(font, l, w / 2 - font.width(l) / 2, ly, argb(la, 0xC9C2B4), true);
            ly += 11;
        }
    }

    /** "WONDER · TOLLING · FLOOR 3"; an everyday world names no tier. */
    static String sub(WorldPayloads.Arrival a) {
        StringBuilder s = new StringBuilder();
        String t = switch (a.tier()) {
            case "deep" -> "Deep";
            case "wonder" -> "Wonder";
            case "secret" -> "Secret";
            case "rare" -> "Rare world";
            default -> "";
        };
        for (String part : new String[]{t, a.moodName(), a.where()}) {
            if (part.isEmpty()) continue;
            if (!s.isEmpty()) s.append(" · ");
            s.append(part);
        }
        return s.toString().toUpperCase(Locale.ROOT);
    }

    static int tierColour(String tier) {
        return switch (tier) {
            case "deep" -> 0x8FA8E8;
            case "wonder" -> 0xE8C46A;
            case "secret" -> 0x9EE0B8;
            case "rare" -> 0xE88AA8;
            default -> 0xB4BCC6;
        };
    }

    static int rarityColour(String rarity) {
        return switch (rarity) {
            case "Uncommon" -> 0x7FD39A;
            case "Rare" -> 0x6FA8FF;
            case "Mythic" -> 0xE0A0FF;
            default -> 0xC9CED4;
        };
    }

    private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int argb) {
        g.fill(cx - 1, cy - 2, cx + 1, cy - 1, argb);
        g.fill(cx - 2, cy - 1, cx + 2, cy + 2, argb);
        g.fill(cx - 1, cy + 2, cx + 1, cy + 3, argb);
    }

    static float ease(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    static int spacedWidth(Font font, String s, int spacing) {
        int tw = 0;
        for (int i = 0; i < s.length(); i++) tw += font.width(String.valueOf(s.charAt(i))) + spacing;
        return Math.max(0, tw - spacing);
    }

    private static void spaced(GuiGraphicsExtractor g, Font font, String s, int x, int y, int spacing, int argb) {
        if ((argb >>> 24) < 4) return;
        int px = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            g.text(font, c, px, y, argb, true);
            px += font.width(c) + spacing;
        }
    }

    static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
