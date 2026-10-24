package dev.awaken.powers.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The power bar: an energy bar over six key slots (Z X C V · R · Alt) beside the hotbar, with family-coloured icons,
 * a cooldown sweep, a glow while a power runs and a red flash when a press is refused. Hidden with F1 and until the
 * player owns a power.
 */
final class PowerHud {
    private PowerHud() {}

    static final Identifier LAYER = Awaken.id("power_bar");
    static final int SLOT = 20;
    static final int GAP = 2;

    static void register(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, PowerHud::render);
    }

    static Identifier icon(PowerType t) {
        return Awaken.id("power/" + t.path);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        Loadout l = ClientPowers.loadout();
        if (l == null || l.owned().isEmpty()) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = ClientPowers.now();

        int rowWidth = 6 * SLOT + 5 * GAP + 4;
        int hotbarRight = w / 2 + 91;
        boolean oneRow = w - hotbarRight >= rowWidth + 14;
        int x0;
        int y0;
        int cols = oneRow ? 6 : 3;
        int blockWidth = cols * SLOT + (cols - 1) * GAP + (oneRow ? 4 : 0);
        if (oneRow) {
            x0 = hotbarRight + 10;
            y0 = h - SLOT - 2;
        } else {
            x0 = w - blockWidth - 4;
            y0 = h - 2 * SLOT - GAP - 2;
        }

        // energy bar
        float energy = ClientPowers.energy();
        float max = Math.max(1, l.maxEnergy());
        int barY = y0 - 7;
        g.fill(x0 - 1, barY - 1, x0 + blockWidth + 1, barY + 4, 0xA0101622);
        int filled = Math.round(blockWidth * Math.min(1f, energy / max));
        boolean paused = now < l.pauseUntil();
        g.fillGradient(x0, barY, x0 + filled, barY + 3, paused ? 0xFF5E9CD6 : 0xFF9AD4FF, paused ? 0xFF2E6FB0 : 0xFF3C86CC);
        if (energy < max) {
            String num = String.valueOf((int) energy);
            g.pose().pushMatrix();
            g.pose().translate(x0 + blockWidth - font.width(num) * 0.6f, barY - 6);
            g.pose().scale(0.6f, 0.6f);
            g.text(font, Component.literal(num), 0, 0, 0xFFCFE6FF, true);
            g.pose().popMatrix();
        }

        for (int slot = 0; slot < Loadout.SLOTS; slot++) {
            int col = oneRow ? slot : slot % 3;
            int row = oneRow ? 0 : slot / 3;
            int sx = x0 + col * (SLOT + GAP) + (oneRow && slot >= 4 ? 4 : 0);
            int sy = y0 + row * (SLOT + GAP);
            drawSlot(g, font, l, slot, sx, sy, now, energy);
        }

        if (ClientPowers.messageUntil > ClientPowers.clientTicks && !ClientPowers.message.isEmpty()) {
            Component msg = Component.literal(ClientPowers.message);
            int mx = Math.min(x0 + blockWidth - font.width(msg), x0);
            g.text(font, msg, Math.max(2, mx), barY - 16, 0xFFFF8A7A, true);
        }
    }

    private static void drawSlot(GuiGraphicsExtractor g, Font font, Loadout l, int slot, int x, int y, long now, float energy) {
        String id = l.slot(slot);
        PowerType t = PowerRegistry.get(id);
        boolean flash = ClientPowers.flashUntil[slot] > ClientPowers.clientTicks;
        g.fill(x, y, x + SLOT, y + SLOT, flash ? 0xC0601818 : 0xA0101622);
        int edge = slot == Loadout.ULTIMATE ? 0xFFFFCF7A : slot == Loadout.MOVEMENT ? 0xFF9AD4FF : 0xFF3A4A60;
        if (t != null && l.isActive(id)) edge = 0xFF000000 | t.family.colour;
        g.outline(x, y, SLOT, SLOT, edge);
        if (t != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, icon(t), x + 2, y + 2, 16, 16);
            Loadout.Cooldown cd = l.cooldown(id);
            long left = cd == null ? 0 : cd.end() - now;
            if (left > 0 && cd.total() > 0) {
                int hgt = (int) Math.ceil(16.0 * Math.min(1.0, left / (double) cd.total()));
                g.fill(x + 2, y + 2 + (16 - hgt), x + 18, y + 18, 0xB0000000);
                if (left >= 20) {
                    String s = String.valueOf((left + 19) / 20);
                    g.centeredText(font, Component.literal(s), x + SLOT / 2, y + 6, 0xFFFFFFFF);
                }
            } else {
                Loadout.Owned o = l.get(id);
                float cost = o == null ? 0 : t.cost * t.variant(o.variant()).cost();
                if (energy + 0.5f < cost) g.fill(x + 2, y + 2, x + 18, y + 18, 0x802050A0);
            }
            if (l.isActive(id)) {
                int alpha = (int) (45 + 30 * Math.sin(ClientPowers.clientTicks * 0.3));
                g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, (alpha << 24) | (t.family.colour & 0xFFFFFF));
            }
        }
        // key label, bottom right, small
        String key = keyLabel(slot);
        g.pose().pushMatrix();
        g.pose().translate(x + SLOT - font.width(key) * 0.55f - 1, y + SLOT - 5);
        g.pose().scale(0.55f, 0.55f);
        g.text(font, Component.literal(key), 0, 0, 0xFFE0E6F0, true);
        g.pose().popMatrix();
    }

    /** A key's name short enough for a slot (4 characters at most): "Z", "M4", "LMB", "LAlt", "Spc". */
    static String keyLabel(int slot) {
        InputConstants.Key key = ClientCasting.key(slot).getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            // by button number, not by name (the name is "Button 4" and would cut to "Butt")
            return switch (key.getValue()) {
                case InputConstants.MOUSE_BUTTON_LEFT -> "LMB";
                case InputConstants.MOUSE_BUTTON_MIDDLE -> "MMB";
                case InputConstants.MOUSE_BUTTON_RIGHT -> "RMB";
                default -> "M" + key.getValue();
            };
        }
        String s = ClientCasting.key(slot).getTranslatedKeyMessage().getString();
        if (s.length() <= 3) return s.toUpperCase();
        String lower = s.toLowerCase();
        if (lower.startsWith("left ")) return "L" + abbreviate(s.substring(5), 3);
        if (lower.startsWith("right ")) return "R" + abbreviate(s.substring(6), 3);
        return abbreviate(s, 4);
    }

    private static String abbreviate(String s, int max) {
        String a = switch (s.toLowerCase()) {
            case "alt" -> "Alt";
            case "option" -> "Opt";
            case "command", "cmd" -> "Cmd";
            case "shift" -> "Sh";
            case "control", "ctrl" -> "Ctl";
            case "space" -> "Spc";
            case "caps lock" -> "Caps";
            case "backspace" -> "Bksp";
            case "enter", "return" -> "Ret";
            default -> s;
        };
        return a.length() > max ? a.substring(0, max) : a;
    }
}
