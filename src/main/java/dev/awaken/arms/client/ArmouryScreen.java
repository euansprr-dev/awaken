package dev.awaken.arms.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.progression.Rank;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Armoury (docs/arms-progression/PLAN.md §3.2): every style on the left (the ones you know, then Hale's rack for
 * your rank), and on the right the chosen style's six tiers and what the chosen tier needs, with where each thing comes
 * from. Buy a pattern at Hale's rack, draw a copy at a Forge, and Track a weapon so its parts show on your screen. Draws
 * what the server sends; the server judges every price and door. Doesn't pause the game.
 */
final class ArmouryScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int EDGE = 0xFF7FC3FF, LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299, GOOD = 0xFF9FE6D6, BAD = 0xFFFF8A7A;
    private static final int ROW = 12;

    private int px, py, pw, ph, listW, dx, dw;
    private @Nullable String selected;
    private final int[] tierX = new int[6];
    private int tierY, buttonY;

    ArmouryScreen() {
        super(Component.translatable("screen.awaken.armoury"));
    }

    @Override
    protected void init() {
        pw = Math.min(430, width - 16);
        ph = Math.min(246, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listW = 122;
        dx = px + listW + 14;
        dw = pw - listW - 22;
    }

    private ArmsPayloads.@Nullable ArmouryView view() {
        return ArmouryCache.view;
    }

    private String style() {
        if (selected != null) return selected;
        var d = ArmouryCache.detail;
        return d != null ? d.style() : "longsword";
    }

    private static int rankColour(String tier) {
        for (Rank r : Rank.values()) if (r.label.equals(tier)) return 0xFF000000 | r.colour;
        return TITLE;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0x90050810, 0xB0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        var v = view();
        g.fill(px, py, px + pw, py + ph, 0xE80F1724);
        g.outline(px, py, pw, ph, 0xB07FC3FF);
        if (v == null) return;
        String door = Language.getInstance().getOrDefault("awaken.armoury.door." + (v.door() == ArmsArmoury.HALE ? "hale" : v.door() == ArmsArmoury.FORGE ? "forge" : "band"));
        g.text(font, Component.literal(title.getString() + " · " + door), px + 8, py + 7, TITLE, false);
        String ess = "Essence " + v.essence();
        g.text(font, ess, px + pw - 8 - font.width(ess), py + 7, LANTERN, false);
        g.fill(px + 6, py + 19, px + pw - 6, py + 20, 0x553A4A60);

        // the list
        int y = py + 24;
        for (var e : v.styles()) {
            boolean sel = e.id().equals(style());
            if (sel) g.fill(px + 6, y - 1, px + 6 + listW, y + ROW - 1, 0xF01A2436);
            Archetypes.Loaded st = Archetypes.get(e.id());
            String name = st == null ? e.id() : st.def().name();
            boolean known = e.state() == 0;
            g.text(font, name, px + 10, y + 1, known ? TITLE : LANTERN, false);
            String tag = known ? (ArmouryCache.view != null && v.tracked().startsWith(e.id() + ":") ? "tracked" : "") : e.price() + " essence";
            if (!tag.isEmpty()) g.text(font, tag, px + 6 + listW - 4 - font.width(tag), y + 1, known ? GOOD : (e.afford() ? LANTERN : DIM), false);
            y += ROW;
        }
        if (v.hidden() > 0) {
            String more = v.door() == ArmsArmoury.HALE && !v.hiddenRank().isEmpty()
                    ? "Hale keeps more patterns. He shows you more at " + v.hiddenRank() + "-rank."
                    : "More patterns wait in the world.";
            List<FormattedCharSequence> lines = font.split(Component.literal(more), listW - 4);
            y += 4;
            for (int i = 0; i < Math.min(3, lines.size()); i++) g.text(font, lines.get(i), px + 10, y + i * 10, DIM, false);
        }

        // the detail
        var d = ArmouryCache.detail;
        if (d == null) return;
        Archetypes.Loaded st = Archetypes.get(d.style());
        if (st != null) {
            g.text(font, st.def().name(), dx, py + 25, TITLE, false);
            String blurb = Language.getInstance().getOrDefault("awaken.style." + d.style() + ".blurb");
            List<FormattedCharSequence> lines = font.split(Component.literal(blurb), dw);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), dx, py + 36 + i * 10, LABEL, false);
        }
        tierY = py + 60;
        int cw = Math.min(40, (dw - 5 * 4) / 6);
        for (int i = 0; i < d.tiers().size(); i++) {
            var t = d.tiers().get(i);
            tierX[i] = dx + i * (cw + 4);
            boolean on = t.tier().equals(d.tier());
            int colour = rankColour(t.tier());
            g.fill(tierX[i], tierY, tierX[i] + cw, tierY + 20, on ? 0xF01A2436 : 0xE0101622);
            g.outline(tierX[i], tierY, cw, 20, on ? colour : (t.reached() ? 0xFF3A4A60 : 0xFF222C3C));
            g.centeredText(font, Component.literal(t.tier()), tierX[i] + cw / 2, tierY + 3, t.reached() ? colour : DIM);
            g.centeredText(font, Component.literal("Lv " + t.level()), tierX[i] + cw / 2, tierY + 11, DIM);
        }
        int y2 = tierY + 26;
        int tierIdx = Math.max(0, tierIndex(d.tier()));
        var weapon = BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id(prefix(tierIdx) + "_" + d.style()));
        if (weapon != net.minecraft.world.item.Items.AIR) {
            g.item(new ItemStack(weapon), dx, y2 - 1);
            g.text(font, weapon.getName(new ItemStack(weapon)), dx + 20, y2 + 4, rankColour(d.tier()), false);
        }
        y2 += 20;
        var cur = d.tiers().get(Math.min(tierIdx, d.tiers().size() - 1));
        boolean locked = !cur.reached();
        if (locked) {
            // the lock is seen before it opens: what it waits for once you've met it, else only the level
            String line = cur.pull().isEmpty() ? "This tier opens at level " + cur.level() + "." : cur.pull();
            List<FormattedCharSequence> lines = font.split(Component.literal(line), dw);
            for (int i = 0; i < Math.min(4, lines.size()); i++) g.text(font, lines.get(i), dx, y2 + i * 10, LANTERN, false);
            y2 += 44;
        }
        for (var n : locked && cur.pull().isEmpty() ? List.<ArmsPayloads.NeedRow>of() : d.needs()) {
            if (y2 > py + ph - 56) break;
            Identifier id = Identifier.parse(n.id());
            Component label = n.kind().equals("tag")
                    ? Component.translatable("tag.item." + id.getNamespace() + "." + id.getPath())
                    : BuiltInRegistries.ITEM.getValue(id).getName(new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            boolean ok = n.have() >= n.need();
            String count = Math.min(n.have(), n.need()) + "/" + n.need();
            g.text(font, count, dx, y2, ok ? GOOD : BAD, false);
            g.text(font, label, dx + 28, y2, ok ? LABEL : TITLE, false);
            String key = n.kind().equals("tag") ? "awaken.source.tag." + id.getPath()
                    : id.getPath().endsWith("_blueprint") ? "awaken.source.blueprint" : "awaken.source." + id.getNamespace() + "." + id.getPath();
            if (!ok && Language.getInstance().has(key)) {
                List<FormattedCharSequence> src = font.split(Component.translatable(key), dw - 28);
                if (!src.isEmpty()) g.text(font, src.get(0), dx + 28, y2 + 9, DIM, false);
                y2 += 9;
            }
            y2 += 11;
        }

        // the plaque: where else the pattern comes from (a pattern on offer at the rack, or one you hold)
        var home = dev.awaken.arms.ArmsHomes.of(d.style());
        if (home != null && v.door() != ArmouryCache.FORGE && (ArmouryCache.known(d.style()) || entry(d.style()) != null)) {
            String plaque = "Free: " + home.gate() + " teaches it, in " + home.rank().label + "-rank gates."
                    + (home.teacher() == null ? "" : " " + Character.toUpperCase(home.teacher().person().charAt(0)) + home.teacher().person().substring(1) + " teaches it early.")
                    + (home.leftChest() ? " Found early in a chest someone left behind." : "");
            List<FormattedCharSequence> lines = font.split(Component.literal(plaque), dw);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), dx, py + ph - 44 + i * 10, DIM, false);
        }

        // the buttons
        buttonY = py + ph - 24;
        boolean known = ArmouryCache.known(d.style());
        boolean tracked = ArmouryCache.tracked(d.style(), d.tier());
        button(g, dx, "Track", known && !tracked, mouseX, mouseY);
        if (tracked) button(g, dx, "Stop tracking", true, mouseX, mouseY);
        var entry = entry(d.style());
        if (entry != null && v.door() == ArmouryCache.HALE && entry.state() == 1) {
            button(g, dx + 88, "Buy pattern · " + entry.price() + " essence", entry.afford(), mouseX, mouseY);
        } else if (entry != null && v.door() == ArmouryCache.FORGE && entry.state() == 0 && !d.style().equals("longsword")) {
            button(g, dx + 88, "Draw a copy", entry.afford(), mouseX, mouseY);
        } else if (entry != null && entry.state() == 1) {
            g.text(font, "Buy it at Hale's rack", dx + 88, buttonY + 4, DIM, false);
        }
        if (!v.raise().isEmpty() && v.door() == ArmouryCache.FORGE) {
            String[] r = v.raise().split("\\|", 2);
            boolean ready = r.length > 1 && r[1].equals("ready");
            int by = buttonY;
            buttonY = by - 20;
            button(g, dx, "Raise it to " + r[0], ready, mouseX, mouseY);
            buttonY = by;
            int tx = dx + bw("Raise it to " + r[0]) + 8;
            List<FormattedCharSequence> lines = font.split(Component.literal(ready ? "The weapon in your hand is ready to raise." : "The weapon in your hand: " + (r.length > 1 ? r[1] : "")), dx + dw - tx);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), tx, py + ph - 46 + i * 10, ready ? GOOD : LANTERN, false);
        }
        for (int i = 0; i < v.finds().size(); i++) g.text(font, v.finds().get(i), px + 10, py + ph - 24 - (v.finds().size() - 1 - i) * 10, i == 0 ? LABEL : GOOD, false);
        g.text(font, "Esc: close", px + pw - 8 - font.width("Esc: close"), py + ph - 12, DIM, false);
    }

    private static String prefix(int tier) {
        return new String[] {"wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign"}[Math.min(5, tier)];
    }

    private static int tierIndex(String tier) {
        for (int i = 0; i < ArmsArmoury.TIERS.length; i++) if (ArmsArmoury.TIERS[i].equals(tier)) return i;
        return -1;
    }

    private ArmsPayloads.@Nullable StyleEntry entry(String id) {
        var v = view();
        if (v == null) return null;
        for (var e : v.styles()) if (e.id().equals(id)) return e;
        return null;
    }

    private int bw(String label) {
        return font.width(label) + 12;
    }

    private void button(GuiGraphicsExtractor g, int x, String label, boolean enabled, int mx, int my) {
        int w = bw(label);
        boolean over = enabled && mx >= x && mx < x + w && my >= buttonY && my < buttonY + 16;
        g.fill(x, buttonY, x + w, buttonY + 16, over ? 0xF02A3A56 : 0xE0101622);
        g.outline(x, buttonY, w, 16, enabled ? EDGE : 0xFF2A3446);
        g.text(font, label, x + 6, buttonY + 4, enabled ? TITLE : DIM, false);
    }

    private boolean buttonHit(int x, String label, double mx, double my) {
        int w = bw(label);
        return mx >= x && mx < x + w && my >= buttonY && my < buttonY + 16;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var v = view();
        var d = ArmouryCache.detail;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || v == null) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        // the list
        int y = py + 24;
        for (var e : v.styles()) {
            if (mx >= px + 6 && mx < px + 6 + listW && my >= y - 1 && my < y + ROW - 1) {
                selected = e.id();
                ArmouryCache.ask(ArmsArmoury.DETAIL, e.id(), "");
                return true;
            }
            y += ROW;
        }
        if (d == null) return super.mouseClicked(event, doubleClick);
        // the tiers
        for (int i = 0; i < d.tiers().size(); i++) {
            int cw = Math.min(40, (dw - 5 * 4) / 6);
            if (mx >= tierX[i] && mx < tierX[i] + cw && my >= tierY && my < tierY + 20) {
                ArmouryCache.ask(ArmsArmoury.DETAIL, d.style(), d.tiers().get(i).tier());
                return true;
            }
        }
        boolean known = ArmouryCache.known(d.style());
        boolean tracked = ArmouryCache.tracked(d.style(), d.tier());
        if (known && !tracked && buttonHit(dx, "Track", mx, my)) {
            ArmouryCache.ask(ArmsArmoury.TRACK, d.style(), d.tier());
            return true;
        }
        if (tracked && buttonHit(dx, "Stop tracking", mx, my)) {
            ArmouryCache.ask(ArmsArmoury.UNTRACK, d.style(), d.tier());
            return true;
        }
        if (!v.raise().isEmpty() && v.door() == ArmouryCache.FORGE) {
            String[] r = v.raise().split("\\|", 2);
            int by = buttonY;
            buttonY = by - 20;
            boolean hit = r.length > 1 && r[1].equals("ready") && buttonHit(dx, "Raise it to " + r[0], mx, my);
            buttonY = by;
            if (hit) {
                ArmouryCache.ask(ArmsArmoury.RAISE, d.style(), d.tier());
                return true;
            }
        }
        var entry = entry(d.style());
        if (entry != null && v.door() == ArmouryCache.HALE && entry.state() == 1 && entry.afford() && buttonHit(dx + 88, "Buy pattern · " + entry.price() + " essence", mx, my)) {
            ArmouryCache.ask(ArmsArmoury.BUY, d.style(), d.tier());
            return true;
        }
        if (entry != null && v.door() == ArmouryCache.FORGE && entry.state() == 0 && entry.afford() && !d.style().equals("longsword") && buttonHit(dx + 88, "Draw a copy", mx, my)) {
            ArmouryCache.ask(ArmsArmoury.DRAW, d.style(), d.tier());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
