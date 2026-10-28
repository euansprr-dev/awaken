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
        Loadout.Owned sense = l.get("awaken:danger_sense");
        if (sense != null && sense.level() >= 2) dangerArcs(g, mc, w, h);

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
            Loadout.Owned owned = l.get(id);
            int level = owned == null ? 1 : owned.level();
            tier(g, x, y, level, t.family.colour, levelFlash(id, level));
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
            if (l.again(id, now)) {
                // a ladder step's second press is open: the slot beats in the family's colour until it closes
                int alpha = (int) (150 + 100 * Math.sin(ClientPowers.clientTicks * 0.6));
                g.outline(x - 1, y - 1, SLOT + 2, SLOT + 2, (alpha << 24) | (t.family.colour & 0xFFFFFF));
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

    /**
     * The slot's frame grows with the power's ladder (SPEC 34.7): corner ticks at level 2, side bars at 3, a double frame at 4,
     * and at 5 the frame glows in the family's colour. {@code flash} (1 -> 0) lights it up just after a level-up.
     */
    static void tier(GuiGraphicsExtractor g, int x, int y, int level, int family, float flash) {
        int fam = 0xFF000000 | family;
        if (level >= 2) {
            int t = 4;
            g.fill(x - 1, y - 1, x - 1 + t, y, fam);
            g.fill(x - 1, y - 1, x, y - 1 + t, fam);
            g.fill(x + SLOT + 1 - t, y - 1, x + SLOT + 1, y, fam);
            g.fill(x + SLOT, y - 1, x + SLOT + 1, y - 1 + t, fam);
            g.fill(x - 1, y + SLOT, x - 1 + t, y + SLOT + 1, fam);
            g.fill(x - 1, y + SLOT + 1 - t, x, y + SLOT + 1, fam);
            g.fill(x + SLOT + 1 - t, y + SLOT, x + SLOT + 1, y + SLOT + 1, fam);
            g.fill(x + SLOT, y + SLOT + 1 - t, x + SLOT + 1, y + SLOT + 1, fam);
        }
        if (level >= 3) {
            int m = SLOT / 2;
            g.fill(x - 1, y + m - 3, x, y + m + 3, fam);
            g.fill(x + SLOT, y + m - 3, x + SLOT + 1, y + m + 3, fam);
        }
        if (level >= 4) g.outline(x - 2, y - 2, SLOT + 4, SLOT + 4, (0x90 << 24) | (family & 0xFFFFFF));
        if (level >= 5) {
            int a = (int) (40 + 25 * Math.sin(ClientPowers.clientTicks * 0.12));
            g.fill(x - 3, y - 3, x + SLOT + 3, y - 2, (a << 24) | (family & 0xFFFFFF));
            g.fill(x - 3, y + SLOT + 2, x + SLOT + 3, y + SLOT + 3, (a << 24) | (family & 0xFFFFFF));
            g.fill(x - 3, y - 2, x - 2, y + SLOT + 2, (a << 24) | (family & 0xFFFFFF));
            g.fill(x + SLOT + 2, y - 2, x + SLOT + 3, y + SLOT + 2, (a << 24) | (family & 0xFFFFFF));
        }
        if (flash > 0) {
            int a = (int) (200 * flash);
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, (a << 24) | 0xFFFFFF);
            g.outline(x - 3, y - 3, SLOT + 6, SLOT + 6, ((int) (255 * flash) << 24) | (family & 0xFFFFFF));
        }
    }

    private static final java.util.Map<String, Integer> SEEN_LEVEL = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> FLASH_AT = new java.util.HashMap<>();

    /** 1 -> 0 over a second and a half after this power's level rose on this client; 0 otherwise. */
    private static float levelFlash(String id, int level) {
        Integer seen = SEEN_LEVEL.put(id, level);
        if (seen != null && level > seen) FLASH_AT.put(id, ClientPowers.clientTicks);
        Integer at = FLASH_AT.get(id);
        if (at == null) return 0;
        float t = (ClientPowers.clientTicks - at) / 30f;
        if (t >= 1) {
            FLASH_AT.remove(id);
            return 0;
        }
        return 1 - t;
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

    /**
     * Danger Sense's Edge Arc (SPEC 34): a short red arc at the screen's edge on the side each warned threat is on, brighter the
     * more it is behind you.
     */
    private static void dangerArcs(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var p = mc.player;
        var level = mc.level;
        if (p == null || level == null) return;
        float yaw = p.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        int cx = w / 2, cy = h / 2;
        double rx = w * 0.44, ry = h * 0.40;
        ClientPowers.eachDanger(id -> {
            var e = level.getEntity(id);
            if (e == null) return;
            double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.5) return;
            dx /= len;
            dz /= len;
            // the angle from straight ahead, clockwise: 0 is up the screen, pi is behind you (the bottom edge)
            double ahead = dx * fx + dz * fz;
            double right = dx * -fz + dz * fx;
            double a = Math.atan2(right, ahead);
            int alpha = (int) (110 + 120 * (1 - ahead) / 2);
            for (int k = -4; k <= 4; k++) {
                double t = a + k * 0.045;
                int x = cx + (int) Math.round(Math.sin(t) * rx);
                int y = cy - (int) Math.round(Math.cos(t) * ry);
                int fade = Math.max(30, alpha - Math.abs(k) * 22);
                g.fill(x - 2, y - 2, x + 2, y + 2, (fade << 24) | 0xFF3B3B);
            }
        });
    }
}
