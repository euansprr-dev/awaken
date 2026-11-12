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
 * The Armour goal on the Tasks page (SPEC §27.13): your rank's armour. Until a set of your rank or better is forged
 * whole, one step offers your rank's sets as choices, each with its pieces (the page shows them on a figure) and what
 * the pieces you don't own yet still take; your first set is on the main path, before your first gate. Then a step says
 * to put it on, and once it has been worn the step says when the next comes. Both are decided from {@link ArmouryMemory},
 * never from what is worn this second (§27.14). E sets are made at any Fitting Stand with a crafting table
 * near it; D and up need an anvil and the Forge (SPEC §44.4).
 */
final class ArmourGoals {
    private ArmourGoals() {}

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static void register() {
        Goals.register(ArmourGoals::steps);
        Goals.registerFooter(p -> {
            if (!Progression.profile(p).awakened()) return List.of();
            int next = Progression.rank(p).ordinal() + 1;
            return next >= Rank.values().length ? List.of() : List.of(new Goals.Footer(Goal.ARMOUR, "At " + Rank.values()[next].label + "-rank your next armour opens."));
        });
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        List<Goals.Step> out = new ArrayList<>(rankSteps(p));
        Goals.Step followed = tracked(p);
        if (followed != null) out.add(followed);
        return out;
    }

    /** The suit you chose to follow in the Hall (SPEC §52): what it still takes, until it is all made. */
    private static Goals.@org.jspecify.annotations.Nullable Step tracked(ServerPlayer p) {
        ArmouryMemory mem = ArmouryMemory.of(p);
        ArmourSet set = mem.tracked.isEmpty() ? null : ArmourSet.byId(mem.tracked);
        if (set == null) return null;
        Goals.Choice c = choice(p, set);
        if (c == null) return null;
        boolean all = true;
        for (ArmorType piece : ForgeSong.PIECES) all &= owns(p, ArmourForge.make(set, piece).getItem());
        if (all) {
            mem.tracked = "";
            return null;
        }
        boolean above = set.rank.ordinal() > Progression.rank(p).ordinal();
        return Goals.Step.of("armour/tracked", Goal.ARMOUR, "Make your " + set.setName + " armour")
                .why(above ? set.rank.label + "-rank armour opens at level " + set.rank.levelRequired + ". You chose to follow this one in the Hall of Arms."
                        : "You chose to follow this one in the Hall of Arms. It's made at a Fitting Stand.")
                .needs(c.needs()).priority(35).build();
    }

    /**
     * Your rank's armour (SPEC §27.13, §27.14). Done from memory, never from what you wear this second (Euan 2026-11-12:
     * he forged his first set in creative and the step stayed): once a set of your rank or better is forged whole, its
     * step is done and "Put on your ... armour" follows; once you have worn a suit of your rank or better head to foot,
     * that is done too and the step says when the next comes. Taking the suit off undoes neither.
     */
    private static List<Goals.Step> rankSteps(ServerPlayer p) {
        Rank rank = Progression.rank(p);
        ArmouryMemory mem = ArmouryMemory.of(p);
        int worst = Integer.MAX_VALUE;
        boolean wearing = false;
        for (EquipmentSlot slot : SLOTS) {
            ArmourLooks.Look look = ArmourLooks.of(p.getItemBySlot(slot));
            worst = Math.min(worst, look == null ? -1 : look.set().rank.ordinal());
            wearing |= look != null;
        }
        if (worst > mem.suited) mem.suited = worst; // a suit of that rank or better worn head to foot, remembered
        Stage stage = stage(mem, rank);
        if (stage.worn()) {
            if (rank.ordinal() + 1 >= Rank.values().length) return List.of();
            Rank next = Rank.values()[rank.ordinal() + 1];
            return List.of(Goals.Step.of("armour/next", Goal.ARMOUR, "Your next armour comes with " + next.label + "-rank").thread("armour.set", 20)
                    .why(next.label + "-rank opens at level " + next.levelRequired + ". Its sets are forged at a Fitting Stand with an anvil and the Forge near it.")
                    .priority(10).build());
        }
        boolean never = rank == Rank.E && mem.suited < 0 && mem.worn.isEmpty(); // no suit worn yet: this is your first armour
        if (stage.forged() != null) return List.of(putOn(p, stage.forged(), rank, never));
        boolean first = never && !wearing; // a piece already on at E-rank isn't a first choice

        Goals.Builder b = Goals.Step.of("armour/" + rank.name().toLowerCase(java.util.Locale.ROOT), Goal.ARMOUR,
                first ? "Choose your first armour" : "Make your " + rank.label + "-rank armour").thread("armour.set", 10);
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
        return List.of(b.why(first ? where + " Wear it before your first gate." : where)
                .path(first ? Goals.PATH_FIRST_ARMOUR : Goals.NO_PATH).priority(first ? 58 : 30).covers("armour:forge/").build());
    }

    /** Where your rank's armour stands, from memory alone: worn head to foot, or forged whole (the set), or neither. */
    record Stage(boolean worn, @org.jspecify.annotations.Nullable ArmourSet forged) {}

    static Stage stage(ArmouryMemory mem, Rank rank) {
        boolean worn = mem.suited >= rank.ordinal();
        ArmourSet forged = null;
        for (ArmourSet set : ArmourSet.values()) {
            if (set.rank.ordinal() < rank.ordinal()) continue;
            worn |= mem.worn.contains(set.id);
            if (forged == null && mem.forged.contains(set.id)) forged = set;
        }
        return new Stage(worn, worn ? null : forged);
    }

    /** The set is forged whole: now wear it. Where the pieces are is said plainly; the step is done once it's worn whole. */
    private static Goals.Step putOn(ServerPlayer p, ArmourSet set, Rank rank, boolean first) {
        int have = 0;
        String[] covers = new String[ForgeSong.PIECES.length]; // the Now line's "You can forge" for this set's pieces: it's made
        for (int i = 0; i < covers.length; i++) {
            ArmorType piece = ForgeSong.PIECES[i];
            if (owns(p, ArmourForge.make(set, piece).getItem())) have++;
            covers[i] = "armour:forge/" + set.id + "_" + ForgeSong.slot(piece);
        }
        String take = have >= ForgeSong.PIECES.length ? "All four pieces are with you. Wear them: helmet, chestplate, leggings and boots."
                : "It's standing on your Fitting Stand. Open the stand and take the suit (or sneak and use the stand), then wear all four pieces.";
        return Goals.Step.of("armour/wear/" + rank.name().toLowerCase(java.util.Locale.ROOT), Goal.ARMOUR, "Put on your " + set.setName + " armour")
                .thread("armour.set", 15)
                .why(take + (first ? " Wear it before your first gate." : ""))
                .path(first ? Goals.PATH_FIRST_ARMOUR : Goals.NO_PATH).priority(first ? 58 : 30).covers(covers).build();
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

    static boolean owns(ServerPlayer p, Item item) {
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
