package dev.awaken.gates.client;

import dev.awaken.Awaken;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiCanvas;
import dev.awaken.signal.client.UiSection;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Atlas of Gates (SPEC §38.7), on the band's Codex page: a card for every world, grouped by theme. A world you've
 * found shows its painting, name, tier and rarity, its line, the moods you've seen it in, the secrets you've found and
 * the creature you've met there; one you haven't is a dark outline with only its rarity. Secret worlds have no card
 * until found. "Worlds found · 3 of 5" at the top.
 */
final class AtlasSection extends UiSection {
    /** Paintings are 128 x 72 and drawn pixel for pixel when the card has room. */
    static final int MIN_CARD_W = 136, GAP = 8, ART_W = 128, ART_H = 72;
    private static final Map<String, Boolean> PAINTED = new HashMap<>();

    public String title() { return "ATLAS OF GATES"; }
    public int order() { return 300; }
    public boolean visible() { return GateWorldClient.atlas().found() > 0; }

    @Override
    public String note() {
        var v = GateWorldClient.atlas();
        return "WORLDS FOUND · " + v.found() + " OF " + v.total();
    }

    @Override
    public int noteColour() { return 0xFFE8C46A; }

    @Override
    protected void build(Ui ui) {
        Font f = ui.font();
        int cols = Math.max(1, (ui.width + GAP) / (MIN_CARD_W + GAP));
        int cardW = (ui.width - (cols - 1) * GAP) / cols;
        int paintW = Math.min(ART_W, cardW - 8), paintH = paintW * ART_H / ART_W;
        int cardH = paintH + 60;
        Map<String, List<WorldPayloads.Card>> byTheme = new LinkedHashMap<>();
        for (var c : GateWorldClient.atlas().cards()) byTheme.computeIfAbsent(c.theme(), k -> new ArrayList<>()).add(c);
        for (var e : byTheme.entrySet()) {
            List<WorldPayloads.Card> cards = e.getValue();
            int found = 0;
            for (var c : cards) if (c.found()) found++;
            if (found == 0) continue; // a theme shows once you've found one of its worlds
            Theme t = Theme.byId(e.getKey());
            ui.pair((t == null ? Ui.pretty(e.getKey()) : t.displayName).toUpperCase(java.util.Locale.ROOT), found + " of " + cards.size(),
                    Glass.a(Glass.LANTERN, 1f), Glass.a(Glass.LABEL, 1f));
            ui.gap(3);
            int y0 = ui.y();
            for (int i = 0; i < cards.size(); i++) {
                int cx = ui.x + (i % cols) * (cardW + GAP), cy = y0 + (i / cols) * (cardH + GAP);
                if (ui.drawing()) card(ui, f, cards.get(i), cx, cy, cardW, cardH, paintW, paintH);
            }
            ui.gap(((cards.size() + cols - 1) / cols) * (cardH + GAP) + 4);
        }
    }

    private static void card(Ui ui, Font f, WorldPayloads.Card c, int x, int y, int w, int h, int pw, int ph) {
        UiCanvas g = ui.canvas();
        int px = x + (w - pw) / 2, py = y + 4;
        int rar = WorldCard.rarityColour(c.rarity()), tier = WorldCard.tierColour(c.tier());
        boolean hover = ui.hover(x, y, w, h);
        if (!c.found()) {
            // a dark outline: only its rarity is known
            g.fill(x, y, x + w, y + h, Glass.a(0x07080B, 0.85f));
            g.outline(x, y, w, h, Glass.a(rar, hover ? 0.55f : 0.32f));
            g.fill(px, py, px + pw, py + ph, Glass.a(0x020203, 0.9f));
            g.outline(px, py, pw, ph, Glass.a(rar, 0.18f));
            String q = "?";
            g.text(f, q, px + pw / 2 - f.width(q) / 2, py + ph / 2 - 4, Glass.a(rar, 0.45f), false);
            int ty = y + ph + 10;
            ui.textAt("Not found yet", x + 6, ty, w - 12, Glass.a(Glass.LABEL, 0.55f), false);
            ui.textAt(tierWord(c.tier(), c.rarity()), x + 6, ty + 11, w - 12, Glass.a(rar, 0.7f), false);
            return;
        }
        Glass.panel(g, x, y, w, h, rar, hover ? 1f : 0.85f);
        Identifier art = painting(c.world());
        if (art != null) g.texture(art, px, py, pw, ph, 0, 0, 1, 1, 0xFFFFFFFF);
        else g.gradient(px, py, px + pw, py + ph, Glass.a(tier, 0.35f), Glass.a(0x050404, 0.95f));
        g.outline(px, py, pw, ph, Glass.a(0x000000, 0.6f));
        if (c.keepsake()) g.text(f, "✦", px + pw - 9, py + 2, Glass.a(0xE8C46A, 1f), true);
        int ty = y + ph + 8;
        ui.textAt(c.name(), x + 6, ty, w - 12, Glass.a(Glass.TITLE, 1f), false);
        ui.textAt(tierWord(c.tier(), c.rarity()), x + 6, ty + 10, w - 12, Glass.a(tier, 1f), false);
        var body = SignalText.wrapAtMost(c.line(), w - 12, 2, f::width);
        int ly = ty + 21;
        for (String l : body) {
            g.text(f, l, x + 6, ly, Glass.a(Glass.LABEL, 0.85f), false);
            ly += 9;
        }
        String stats = "Moods " + c.moods().size() + "/" + c.moodsAll() + (c.secretsAll() > 0 ? " · Secrets " + c.secrets().size() + "/" + c.secretsAll() : "");
        ui.textAt(stats, x + 6, y + h - 11, w - 12, Glass.a(Glass.LABEL, 0.7f), false);
        if (hover) g.tooltip(f, f.split(Component.literal(details(c)), 240), ui.mouseX(), ui.mouseY());
    }

    /** "Wonder · Rare"; an everyday world shows only its rarity. */
    static String tierWord(String tier, String rarity) {
        String t = switch (tier) {
            case "deep" -> "Deep · ";
            case "wonder" -> "Wonder · ";
            case "secret" -> "Secret · ";
            case "rare" -> "Rare world · ";
            default -> "";
        };
        return t + rarity;
    }

    /** The hover: everything you know of it, in plain words. */
    static String details(WorldPayloads.Card c) {
        StringBuilder s = new StringBuilder(c.name()).append('\n').append(c.line()).append("\n\n");
        s.append(c.visits() == 1 ? "You've been here once." : "You've been here " + c.visits() + " times.");
        s.append("\nSeen ").append(c.moods().isEmpty() ? "in no mood yet" : String.join(", ", c.moods()).toLowerCase(java.util.Locale.ROOT));
        int left = c.moodsAll() - c.moods().size();
        if (left > 0) s.append(left == 1 ? ". It has one more mood." : ". It has " + left + " more moods.");
        else s.append(". You've seen all its moods.");
        if (c.secretsAll() > 0) {
            if (c.secrets().isEmpty()) s.append(c.secretsAll() == 1 ? "\nIt keeps a secret." : "\nIt keeps " + c.secretsAll() + " secrets.");
            else s.append("\nFound: ").append(String.join(", ", c.secrets()))
                    .append(c.secrets().size() < c.secretsAll() ? ". There's more." : ".");
        }
        if (!c.met().isEmpty()) s.append("\nYou've met ").append(c.met().startsWith("The ") ? "t" + c.met().substring(1) : c.met()).append(" here.");
        if (c.keepsake()) s.append("\nIts keepsake is yours.");
        return s.toString();
    }

    /** The world's painting ({@code textures/gui/gate_atlas/<theme>/<world>.png}), or null when the pack has none. */
    static Identifier painting(String world) {
        Identifier w = Identifier.tryParse(world);
        if (w == null) return null;
        Identifier id = Awaken.id("textures/gui/gate_atlas/" + w.getPath() + ".png");
        Boolean have = PAINTED.get(world);
        if (have == null) {
            have = Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
            PAINTED.put(world, have);
        }
        return have ? id : null;
    }
}
