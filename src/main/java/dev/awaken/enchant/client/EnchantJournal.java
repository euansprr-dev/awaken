package dev.awaken.enchant.client;

import dev.awaken.enchant.Inscriptions;
import dev.awaken.enchant.Known;
import dev.awaken.enchant.Pages;
import dev.awaken.progression.client.Catalogue;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * The Journal's ENCHANTMENTS category (SPEC §45.10): all thirty in order, each one you know with what it does, what it
 * goes on, how deep it can go, where you learned it and whether its mark is in your memory. The rest stay blank until
 * you learn them.
 */
final class EnchantJournal {
    private EnchantJournal() {}

    static void register() {
        Catalogue.fixed(StatusSections.Shelf.ENCHANTMENTS, EnchantJournal::slots);
    }

    private static List<Catalogue.Slot> slots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return List.of();
        Known k = Known.of(mc.player);
        var reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Catalogue.Slot> out = new ArrayList<>(Inscriptions.ALL.size());
        for (Inscriptions.Entry e : Inscriptions.ALL) {
            var h = reg.get(e.key());
            if (!k.knows(e.id()) || h.isEmpty()) {
                out.add(Catalogue.Slot.unknown());
                continue;
            }
            int top = Inscriptions.top(h.get());
            StringBuilder body = new StringBuilder(e.line())
                    .append("\n").append(e.family().label).append(". Goes on ").append(e.fits())
                    .append(top > 1 ? ", up to " + Inscriptions.numeral(top) + "." : ".");
            // what a bane won't touch is the entry's small print, not the first thing its line says (SPEC 45.15)
            if (e.family() == dev.awaken.enchant.Family.BANE) body.append("\nA Lord, a Warden or a named beast shrugs it off, and it never touches your own beasts.");
            Known.Learned l = k.learned(e.id());
            if (l != null && !l.where().isEmpty()) body.append("\n").append(l.where()).append('.');
            if (k.inMemory(e.id())) body.append("\nIts mark is in your memory: you can write it in one stroke.");
            out.add(new Catalogue.Slot(e.name(), true, body.toString(), Pages.page(h.get(), "")));
        }
        return out;
    }
}
