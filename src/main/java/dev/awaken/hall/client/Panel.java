package dev.awaken.hall.client;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The side panel's parts, the same for a weapon and a suit: its frame, the real things a piece takes, the button you hold. */
final class Panel {
    private Panel() {}

    static final float X0 = 1020F, X1 = 1684F, Y0 = 30F, Y1 = 930F, X = X0 + 32F, RIGHT = X1 - 32F, TEXT_W = RIGHT - X;
    static final float HOLD_SECONDS = .6F;
    static final int FORGE_FIRE = 0xFFF2B35F;
    static final int NEEDS_ACROSS = 4;
    static final float NEED_W = TEXT_W / NEEDS_ACROSS, NEED_H = 128F;
    /** The line under the row of materials that says where one comes from. */
    static final float SOURCE_H = 106F;

    record Act(String label, int op, boolean hold, boolean enabled, int key) {}

    /** What goes into the forge: the real item (a group's members in turn, so you know any of them when you find it), the count, its name and where to find it. */
    record Need(ItemStack[] icons, String count, String name, List<FormattedCharSequence> nameLines, boolean ok, String source) {}

    /** A need by item or by tag id ("namespace:path"). */
    static Need need(Stage s, boolean tag, String id, String label, int have, int need) {
        Identifier nid = Identifier.parse(id);
        List<ItemStack> icons = new ArrayList<>(1);
        String name = label;
        if (tag) {
            if (name.isEmpty()) name = Component.translatable("tag.item." + nid.getNamespace() + "." + nid.getPath()).getString();
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, nid))) icons.add(new ItemStack(h.value()));
        } else {
            Item it = BuiltInRegistries.ITEM.getValue(nid);
            ItemStack stack = new ItemStack(it);
            icons.add(stack);
            if (name.isEmpty()) name = it.getName(stack).getString();
        }
        boolean ok = have >= need;
        String key = tag ? "awaken.source.tag." + nid.getPath()
                : nid.getPath().endsWith("_blueprint") ? "awaken.source.blueprint" : "awaken.source." + nid.getNamespace() + "." + nid.getPath();
        String source = Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : "";
        return new Need(icons.toArray(new ItemStack[0]), Math.min(have, need) + "/" + need, name, s.wrap(name, NEED_W - 8F, 2F), ok, source);
    }

    static float needsHeight(int count) {
        int rows = (count + NEEDS_ACROSS - 1) / NEEDS_ACROSS;
        return count == 0 ? 0F : 34F + NEED_H * rows + SOURCE_H;
    }

    /** Draws the row of what it takes under its heading; returns the need the pointer is on, or -1. */
    static int needs(Stage s, String heading, List<Need> needs, float y, float mx, float my, boolean live, float time) {
        return needs(s, heading, needs, y, mx, my, live, time, true);
    }

    /** {@code hint} false leaves out the line under the row, for a panel too full to keep it: see {@link #sourceTip}. */
    static int needs(Stage s, String heading, List<Need> needs, float y, float mx, float my, boolean live, float time, boolean hint) {
        int hover = -1;
        s.text(heading, X, y, 3F, Stage.EDGE);
        y += 34F;
        float top = y;
        for (int i = 0; i < needs.size(); i++) {
            Need n = needs.get(i);
            float cx = X + (i % NEEDS_ACROSS) * NEED_W, cy = top + (i / NEEDS_ACROSS) * NEED_H, mid = cx + NEED_W / 2F;
            if (live && mx >= cx && mx < cx + NEED_W && my >= cy && my < cy + NEED_H) hover = i;
            if (n.icons.length > 0) icon(s, n.icons[(int) (time / 1.2F) % n.icons.length], mid - 32F, cy, 4F);
            s.centre(n.count, mid, cy + 68F, 3F, n.ok ? Stage.GOOD : Stage.BAD);
            for (int l = 0; l < Math.min(2, n.nameLines.size()); l++) {
                FormattedCharSequence line = n.nameLines.get(l);
                s.text(line, mid - s.font.width(line) * s.snap(2F) / 2F, cy + 94F + l * 18F, 2F, n.ok ? Stage.LABEL : Stage.TITLE);
            }
        }
        y = top + (needs.size() + NEEDS_ACROSS - 1) / NEEDS_ACROSS * NEED_H;
        Need shown = !hint ? null : hover >= 0 ? needs.get(hover) : firstShort(needs);
        if (shown != null) lines(s, s.wrap(shown.source.isEmpty() ? shown.name : shown.source, TEXT_W, 3F), y + 14F, 3, Stage.DIM);
        return hover;
    }

    /** Where the material you point at comes from, over whatever is below the row. Drawn last: it takes a stratum of its own. */
    static void sourceTip(Stage s, Need n, float y) {
        if (n.source.isEmpty()) return;
        List<FormattedCharSequence> text = s.wrap(n.source, TEXT_W, 3F);
        float y1 = y + text.size() * 30F + 20F;
        s.g.nextStratum();
        s.fill(X - 12F, y, RIGHT + 12F, y1, 0xF80A0E16);
        s.outline(X - 12F, y, RIGHT + 12F, y1, 2, 0x707FC3FF);
        lines(s, text, y + 10F, text.size(), Stage.LABEL);
    }

    private static Need firstShort(List<Need> needs) {
        for (Need n : needs) if (!n.ok) return n;
        return null;
    }

    static void frame(Stage s) {
        s.fill(X0, Y0, X1, Y1, 0xE60A0E16);
        s.outline(X0, Y0, X1, Y1, 2, 0x707FC3FF);
        s.fill(X0, Y0, X0 + 64F, Y0 + 3F, Stage.EDGE);
        s.fill(X0, Y0, X0 + 3F, Y0 + 64F, Stage.EDGE);
        s.fill(X1 - 64F, Y1 - 3F, X1, Y1, Stage.EDGE);
        s.fill(X1 - 3F, Y1 - 64F, X1, Y1, Stage.EDGE);
    }

    /** The item itself, as your inventory draws it: {@code n} sheet pixels to one of its 16. */
    static void icon(Stage s, ItemStack icon, float x, float y, float n) {
        if (icon.isEmpty()) return;
        float size = s.snap(n);
        s.g.pose().pushMatrix().translate(x, y).scale(size, size);
        s.g.item(icon, 0, 0);
        s.g.pose().popMatrix();
    }

    static float lines(Stage s, List<FormattedCharSequence> text, float y, int most, int colour) {
        for (int i = 0; i < Math.min(most, text.size()); i++) {
            s.text(text.get(i), X, y, 3F, colour);
            y += 30F;
        }
        return y;
    }

    /** The action a piece is made with: hold to commit, or a click where that setting is on. */
    static final class Hold {
        float amount;

        /** Draws the button; true on the frame the hold completes. */
        boolean draw(Stage s, float y0, float y1, String label, boolean enabled, boolean can, boolean pressed, boolean over, float dt) {
            return draw(s, X, RIGHT, y0, y1, label, enabled, can, pressed, over, dt);
        }

        boolean draw(Stage s, float x0, float x1, float y0, float y1, String label, boolean enabled, boolean can, boolean pressed, boolean over, float dt) {
            boolean fired = false;
            if (!HallConfig.clickToCommit() && pressed && can) {
                int was = (int) (amount * 3F);
                amount += dt / HOLD_SECONDS;
                if ((int) (amount * 3F) > was && amount < 1F) HallSounds.rising((int) (amount * 3F));
                if (amount >= 1F) {
                    amount = 0F;
                    fired = true;
                }
            } else {
                amount = Math.max(0F, amount - dt * 4F);
            }
            s.fill(x0, y0, x1, y1, enabled ? (over ? 0xFF241A10 : 0xFF16120D) : 0xFF10141C);
            if (amount > 0F) {
                float end = x0 + (x1 - x0) * amount;
                s.fill(x0, y0, end, y1, 0xFFB23A08);
                s.fill(Math.max(x0, end - 40F), y0, end, y1, 0xFFFF8A28);
                s.fill(Math.max(x0, end - 6F), y0, end, y1, 0xFFFFF0C0);
            }
            s.outline(x0, y0, x1, y1, 3, enabled ? FORGE_FIRE : 0xFF2A3446);
            s.centre(label, (x0 + x1) / 2F, y0 + (y1 - y0) / 2F - 12F, 3F, enabled ? 0xFFFFE9C4 : Stage.DIM);
            return fired;
        }
    }

    /** "forge it" as the button says it: "Hold to forge it", or "Forge it" where one click does. */
    static String holdLabel(String action) {
        return HallConfig.clickToCommit() ? action.substring(0, 1).toUpperCase(Locale.ROOT) + action.substring(1) : "Hold to " + action;
    }
}
