package dev.awaken.armour;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Armour goal on the Tasks page (SPEC §27.13): your rank's armour. Until every piece you wear is your rank's or
 * better, one step offers your rank's sets as choices, each with its pieces (the page shows them on a figure) and what
 * the pieces you don't own yet still take; your first set is on the main path, before your first gate. Once you wear
 * your rank's armour, the step says when the next comes. E sets are made at any Fitting Stand with a crafting table
 * near it; D and up need an anvil and the Forge (SPEC §44.4).
 */
final class ArmourGoals {
    private ArmourGoals() {}

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static void register() {
        Goals.register(ArmourGoals::steps);
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        Rank rank = Progression.rank(p);
        int worst = Integer.MAX_VALUE;
        boolean any = false;
        for (EquipmentSlot slot : SLOTS) {
            ArmourLooks.Look look = ArmourLooks.of(p.getItemBySlot(slot));
            worst = Math.min(worst, look == null ? -1 : look.set().rank.ordinal());
            any |= look != null;
        }
        if (worst >= rank.ordinal()) {
            if (rank.ordinal() + 1 >= Rank.values().length) return List.of();
            Rank next = Rank.values()[rank.ordinal() + 1];
            return List.of(Goals.Step.of("armour/next", Goal.ARMOUR, "Your next armour comes with " + next.label + "-rank")
                    .why(next.label + "-rank opens at level " + next.levelRequired + ". Its sets are forged at a Fitting Stand with an anvil and the Forge near it.")
                    .priority(10).build());
        }
        ArmouryMemory mem = ArmouryMemory.of(p);
        Goals.Builder b = Goals.Step.of("armour/" + rank.name().toLowerCase(java.util.Locale.ROOT), Goal.ARMOUR,
                any ? "Make your " + rank.label + "-rank armour" : "Choose your first armour");
        int sets = 0;
        for (ArmourSet set : ArmourSet.values()) {
            if (set.rank != rank || !set.made() || !ArmourForge.known(mem, set)) continue;
            Goals.Choice c = choice(p, set);
            if (c == null) continue;
            b.choice(c);
            sets++;
        }
        if (sets == 0) return List.of();
        String where = rank == Rank.E ? "Made at a Fitting Stand with a crafting table within 6 blocks."
                : "Forged at a Fitting Stand with an anvil and the Forge near it.";
        return List.of(b.why(any ? where : where + " Wear it before your first gate.")
                .path(any ? Goals.NO_PATH : Goals.PATH_FIRST_ARMOUR).priority(any ? 30 : 58).covers("armour:forge/").build());
    }

    /** One set: its name, role and armour, its pieces, and what the pieces you don't own yet take, summed. */
    static Goals.@org.jspecify.annotations.Nullable Choice choice(ServerPlayer p, ArmourSet set) {
        List<String> show = new ArrayList<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        double armour = 0;
        for (ArmorType piece : ForgeSong.PIECES) {
            ItemStack made = ArmourForge.make(set, piece);
            if (made.isEmpty()) return null;
            show.add(BuiltInRegistries.ITEM.getKey(made.getItem()).toString());
            armour += ArmourNow.armour(made, piece.getSlot());
            if (owns(p, made.getItem())) continue;
            var cost = ForgeSong.cost(set, piece);
            if (cost == null) return null;
            for (ForgeSong.Need n : cost) {
                String key = n.tag() != null ? "#" + n.tag().location() : n.item().toString();
                counts.computeIfAbsent(key, k -> new int[1])[0] += n.count();
                labels.putIfAbsent(key, n.tag() != null ? tagLabel(n.tag()) : BuiltInRegistries.ITEM.getValue(n.item()).getName(new ItemStack(BuiltInRegistries.ITEM.getValue(n.item()))).getString());
            }
        }
        List<Need> needs = new ArrayList<>();
        for (var e : counts.entrySet()) {
            String key = e.getKey();
            int have = key.startsWith("#")
                    ? Goals.count(p.getInventory(), TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse(key.substring(1))))
                    : Goals.count(p.getInventory(), BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(key)));
            needs.add(new Need(key, labels.get(key), have, e.getValue()[0]));
        }
        return new Goals.Choice(set.id, set.setName, set.archetype.label + " · " + (int) Math.round(armour) + " armour", show, needs);
    }

    private static boolean owns(ServerPlayer p, Item item) {
        for (EquipmentSlot slot : SLOTS) if (p.getItemBySlot(slot).is(item)) return true;
        return Goals.count(p.getInventory(), item) > 0;
    }

    /** "Hide", "Metal", "Binding", "Beast core", "Logs", "Wool": what a cost's tag is called on the page. */
    static String tagLabel(TagKey<Item> tag) {
        String path = tag.location().getPath();
        if (path.contains("hide")) return "Hide";
        if (path.contains("metal")) return "Metal";
        if (path.contains("bind")) return "Binding";
        if (path.contains("core")) return "Beast core";
        if (path.equals("logs")) return "Logs";
        if (path.equals("wool")) return "Wool";
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }
}
