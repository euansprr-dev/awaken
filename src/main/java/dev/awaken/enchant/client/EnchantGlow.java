package dev.awaken.enchant.client;

import dev.awaken.enchant.Family;
import dev.awaken.enchant.Inscriptions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Which enchantment lights our weapons' and armour's script line now (SPEC §45.11): each in turn for a second and a
 * half, swelling and fading but never quite out, as a palette index the shaders know (arms_weapon.fsh, armour.fsh:
 * SCRIPT[]) and a brightness. A Marked weapon with nothing written keeps its violet glint (the shaders' foil).
 */
final class EnchantGlow {
    private EnchantGlow() {}

    /** Ticks each enchantment holds the line. */
    private static final float PERIOD = 30F;

    /** The shaders' palette: 1-5 the Forces' wards, 6 the other wards, 7 Clear Blood, 8 banes, 9 spoils, 10 carrying, 11 keeping, 12 anything else. */
    static int index(Holder<Enchantment> h) {
        Inscriptions.Entry e = Inscriptions.of(h);
        if (e == null) return 12;
        if (e.force() != null) return switch (e.force()) {
            case HEAT -> 1;
            case FROST -> 2;
            case CHARGE -> 3;
            case GRAVITY -> 4;
            case SPIRIT -> 5;
        };
        if (e.family() == Family.WARD) return e.colour() == Family.WARD.colour ? 6 : 7;
        return switch (e.family()) {
            case BANE -> 8;
            case SPOILS -> 9;
            case CARRYING -> 10;
            default -> 11;
        };
    }

    static int bits(ItemStack s) {
        ItemEnchantments on = s.getEnchantments();
        int n = on.size();
        if (n == 0) return 0;
        Minecraft mc = Minecraft.getInstance();
        float t = mc.level == null ? 0F : (mc.level.getGameTime() % 72000L + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / PERIOD;
        int turn = (int) t, which = Math.floorMod(turn, n);
        float f = t - turn;
        Holder<Enchantment> lit = null;
        int k = 0;
        for (Holder<Enchantment> h : on.keySet()) {
            if (k++ == which) {
                lit = h;
                break;
            }
        }
        if (lit == null) return 0;
        // alone it breathes; several hand the line on, each swelling and fading into the next
        int bright = Math.round(7F * (0.3F + 0.7F * Mth.sin(f * Mth.PI)));
        return index(lit) | Mth.clamp(bright, 1, 7) << 4;
    }
}
