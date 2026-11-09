package dev.awaken.enchant;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Enchanting on the Tasks page (SPEC §45.9; Euan 2026-11-02: "so you actually know this is possible"). Finding a page for
 * your armour and your weapon once you have them; reading a page you carry, making the table and writing your first
 * enchantment on the main path right after your first gate; then, while a piece you carry has room and you know something
 * that fits it, one row per tab; and setting the table in your Study once a piece could go deeper than II. Each leaves
 * the moment it's done.
 */
final class EnchantGoals {
    private EnchantGoals() {}

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Enchanting's line of work on the tab it's for (SPEC §53). */
    private static String thread(Goal g) {
        return g == Goal.ARMOUR ? "armour.enchant" : "arms.enchant";
    }

    static void register() {
        Goals.register(EnchantGoals::steps);
    }

    /** The tab an enchantment belongs to: Armour for one that fits armour, Weapons for weapons and tools. */
    static Goal tab(Inscriptions.Entry e) {
        return switch (e.fits()) {
            case "armour", "boots", "helmets", "leggings" -> Goal.ARMOUR;
            default -> Goal.WEAPONS;
        };
    }

    static Goal tab(ItemStack piece) {
        return EnchantRules.armour(piece) ? Goal.ARMOUR : Goal.WEAPONS;
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        if (!dev.awaken.progression.Progression.profile(p).awakened()) return List.of();
        Known k = Known.of(p);
        Registry<Enchantment> reg = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Goals.Step> out = new ArrayList<>(3);

        // finding a page, once you have armour or a weapon
        boolean knowsArmour = false, knowsWeapon = false;
        Inscriptions.Entry firstKnown = null;
        for (Inscriptions.Entry e : Inscriptions.ALL) {
            if (!k.knows(e.id())) continue;
            if (firstKnown == null) firstKnown = e;
            if (tab(e) == Goal.ARMOUR || e.fits().equals("anything")) knowsArmour = true;
            if (tab(e) == Goal.WEAPONS) knowsWeapon = true;
        }
        if (!knowsArmour && wearsArmour(p)) {
            out.add(Goals.Step.of("enchant/page/armour", Goal.ARMOUR, "Find an enchantment for your armour").thread("armour.enchant", 10).lead()
                    .why("Wards against the five Forces are written onto armour. Every kind of gate keeps its own enchantment, and its Lord carries the page.")
                    .need(Need.item(p, EnchantContent.PAGE.get(), 1)).optional().priority(70).build());
        }
        if (!knowsWeapon && carriesWeapon(p)) {
            out.add(Goals.Step.of("enchant/page/weapons", Goal.WEAPONS, "Find an enchantment for your weapon").thread("arms.enchant", 10).lead()
                    .why("Banes, Looting and Unbreaking are written onto weapons. Carillon's market sells a few pages, and gates keep the rest.")
                    .need(Need.item(p, EnchantContent.PAGE.get(), 1)).optional().priority(70).build());
        }

        // a page in the pack you haven't read
        Inscriptions.Entry unread = unreadPage(p, k);
        if (unread != null) {
            out.add(Goals.Step.of("enchant/read", tab(unread), "Read the page you're carrying").thread(thread(tab(unread)), 20)
                    .why("Reading it teaches you " + unread.name() + " for good.")
                    .need(Need.of("Read it", false)).path(Goals.PATH_FIRST_GATE + 1).priority(80).build());
        }
        if (firstKnown == null) return out;

        // the table
        if (!k.hadTable()) {
            out.add(Goals.Step.of("enchant/table", tab(firstKnown), "Make an Inscribing Table").thread(thread(tab(firstKnown)), 30)
                    .why("It's where you write what you've learned onto your own gear.")
                    .need(Need.item(p, Items.BOOK, 1)).need(Need.item(p, Items.LAPIS_LAZULI, 3))
                    .need(Need.item(p, CoreApi.ESSENCE_SHARD.get(), 1))
                    .need(Need.tag(p, ItemTags.PLANKS, "Planks", 2)).need(Need.tag(p, ItemTags.STONE_CRAFTING_MATERIALS, "Stone", 2))
                    .path(Goals.PATH_FIRST_GATE + 2).priority(78).build());
            return out;
        }

        // the first inscription, then a row per tab while there's room
        Fit fit = bestFit(p, k, reg, null);
        if (k.written() == 0) {
            if (fit != null) {
                int[] price = EnchantRules.price(fit.entry, fit.now, fit.now + 1);
                out.add(Goals.Step.of("enchant/first", tab(fit.piece), "Inscribe your first enchantment").thread(thread(tab(fit.piece)), 40)
                        .why(fit.entry.name() + " fits your " + fit.piece.getHoverName().getString() + ".")
                        .need(Need.of("A piece it fits", true))
                        .need(new Need("", "Levels", p.experienceLevel, price[0]))
                        .need(Need.item(p, Items.LAPIS_LAZULI, price[1]))
                        .path(Goals.PATH_FIRST_GATE + 3).priority(76).build());
            }
        } else {
            for (Goal g : new Goal[]{Goal.ARMOUR, Goal.WEAPONS}) {
                Fit f = bestFit(p, k, reg, g);
                if (f == null) continue;
                int[] price = EnchantRules.price(f.entry, f.now, f.now + 1);
                String piece = f.piece.getHoverName().getString();
                int free = EnchantRules.room(f.piece) - EnchantRules.used(f.piece);
                String why = free > 0
                        ? "Your " + piece + " has room for " + EnchantRules.count(free) + " more, and you know " + EnchantRules.count(f.fits) + " that " + (f.fits == 1 ? "fits" : "fit") + "."
                        : f.entry.name() + " on your " + piece + " can go deeper.";
                out.add(Goals.Step.of(g == Goal.ARMOUR ? "enchant/room/armour" : "enchant/room/weapons", g,
                                g == Goal.ARMOUR ? "Inscribe your armour" : "Inscribe your weapon").thread(thread(g), 50)
                        .why(why)
                        .need(new Need("", "Levels", p.experienceLevel, price[0]))
                        .need(Need.item(p, Items.LAPIS_LAZULI, price[1]))
                        .optional().priority(40).build());
            }
        }

        // the Study
        if (!k.studyTable()) {
            Fit deep = deeperInStudy(p, k, reg);
            if (deep != null) {
                out.add(Goals.Step.of("enchant/study", tab(deep.piece), "Set your Inscribing Table in your Study").thread(thread(tab(deep.piece)), 60)
                        .why("Written there, enchantments can go deeper than II. Your " + deep.piece.getHoverName().getString()
                                + " could take " + deep.entry.name() + " deeper.")
                        .need(Need.of("A Study", dev.awaken.core.api.HearthHooks.study(p)))
                        .need(Need.of("The table inside it", false))
                        .optional().priority(30).build());
            }
        }
        return out;
    }

    /** A piece you carry, an enchantment you know that it can take now, and how many you know that fit it. */
    record Fit(ItemStack piece, Inscriptions.Entry entry, int now, int fits) {}

    /** The best piece to write on next (worn first, then the hands, then the pack), on this tab or any. */
    static @Nullable Fit bestFit(ServerPlayer p, Known k, Registry<Enchantment> reg, @Nullable Goal only) {
        for (ItemStack s : carried(p)) {
            if (only != null && tab(s) != only) continue;
            Inscriptions.Entry first = null;
            int now = 0, fits = 0;
            for (Inscriptions.Entry e : Inscriptions.ALL) {
                if (!k.knows(e.id())) continue;
                Holder<Enchantment> h = reg.get(e.key()).orElse(null);
                if (h == null || EnchantRules.why(s, h, 0, k.studyTable()) != null) continue;
                fits++;
                if (first == null) {
                    first = e;
                    now = EnchantRules.level(s, h);
                }
            }
            if (first != null) return new Fit(s, first, now, fits);
        }
        return null;
    }

    /** A piece you carry that could take something you know deeper than II, in a Study. */
    static @Nullable Fit deeperInStudy(ServerPlayer p, Known k, Registry<Enchantment> reg) {
        for (ItemStack s : carried(p)) {
            if (EnchantRules.depth(EnchantRules.rank(s)) < 3) continue;
            for (Inscriptions.Entry e : Inscriptions.ALL) {
                if (!k.knows(e.id())) continue;
                Holder<Enchantment> h = reg.get(e.key()).orElse(null);
                if (h == null || !EnchantRules.fits(s, h)) continue;
                if (EnchantRules.deepestAnywhere(s, h, e) > EnchantRules.OUTSIDE_STUDY) return new Fit(s, e, EnchantRules.level(s, h), 1);
            }
        }
        return null;
    }

    /** What you carry that could be written on: worn armour, then both hands, then the pack's gear. */
    static List<ItemStack> carried(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>(8);
        for (EquipmentSlot slot : ARMOUR) if (!p.getItemBySlot(slot).isEmpty()) out.add(p.getItemBySlot(slot));
        if (!p.getMainHandItem().isEmpty()) out.add(p.getMainHandItem());
        if (!p.getOffhandItem().isEmpty()) out.add(p.getOffhandItem());
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s == p.getMainHandItem() || !(s.isEnchantable() || s.isEnchanted())) continue;
            out.add(s);
        }
        return out;
    }

    static boolean wearsArmour(ServerPlayer p) {
        for (EquipmentSlot slot : ARMOUR) if (!p.getItemBySlot(slot).isEmpty()) return true;
        return false;
    }

    static boolean carriesWeapon(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getItem(i).is(ItemTags.WEAPON_ENCHANTABLE)) return true;
        return false;
    }

    /** A page in the pack that would teach you something, or null. */
    static Inscriptions.@Nullable Entry unreadPage(ServerPlayer p, Known k) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof PageItem)) continue;
            for (Inscriptions.Entry e : Pages.teaches(s)) if (!k.knows(e.id())) return e;
        }
        return null;
    }
}
