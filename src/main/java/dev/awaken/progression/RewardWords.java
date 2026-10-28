package dev.awaken.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rewards and perks in plain words, the way a person would say them out loud ("a stat point and a tenth of a level's
 * worth of XP"), shared by the Journal and the reward cards. Caps stay in, said briefly; no lists of numbers.
 */
public final class RewardWords {
    private RewardWords() {}
    public static String number(double n) { return n == Math.rint(n) ? Long.toString((long) n) : String.format(Locale.ROOT, "%.2f", n).replaceAll("0+$", ""); }

    /** XP worth a share of your current level: "a tenth of a level's worth of XP", "2 levels' worth of XP". */
    public static String levelsWorth(double levels) {
        String share = near(levels, 1) ? "a level's" : near(levels, 0.5) ? "half a level's" : near(levels, 0.25) ? "a quarter of a level's"
                : near(levels, 0.75) ? "three quarters of a level's" : near(levels, 0.1) ? "a tenth of a level's" : near(levels, 0.2) ? "a fifth of a level's"
                : levels > 1 ? number(levels) + " levels'" : number(levels * 100) + "% of a level's";
        return share + " worth of XP";
    }

    public static String perk(Perk p) {
        String n = number(p.amount()), pct = number(p.amount() * 100);
        return switch (p.id()) {
            case Perks.DAMAGE_ABOVE_HALF_HP -> pct + "% more damage while you're above half health";
            case Perks.SECRET_GLOW_RANGE -> "you sense spirits and secrets " + n + " blocks further (up to 160)";
            case Perks.HEARTH_SHIELD -> "gate breaks can't damage buildings near your Hearth while anyone in your household with this perk is online";
            case Perks.FLOW_DURATION -> "Flow lasts " + pct + "% longer";
            case Perks.CHEAT_DEATH_PER_GATE -> "once in each gate, you survive a hit that would have killed you";
            case Perks.EXTRA_DIRECTIVE_SLOT -> "room for " + n + " more directive" + (p.amount() == 1 ? "" : "s") + " at a time";
            case Perks.MOVE_SPEED -> "you move " + pct + "% faster";
            case Perks.MAX_HEALTH -> n + " more maximum health";
            case Perks.ENERGY_REGEN -> "energy comes back " + pct + "% faster";
            case Perks.COOLDOWN_REDUCTION -> "power cooldowns " + pct + "% shorter (up to half off in all)";
            case Perks.FALL_RESIST -> pct + "% less fall damage";
            case Perks.EXPLOSION_RESIST -> pct + "% less damage from explosions (up to 80% in all)";
            case Perks.BIND_CHANCE -> a(pct) + pct + "-point better chance to bind an Echo";
            case Perks.LUCIDITY_START -> "you start with " + n + " more Lucidity";
            case Perks.CORD_LENGTH -> "your projection cord reaches " + n + " blocks further (up to 1,024)";
            case Perks.CRIT_CHANCE -> a(pct) + pct + "-point better chance of a critical hit (up to 35% in all)";
            case Perks.XP_BONUS -> pct + "% more XP";
            case Perks.LOOT_BONUS -> a(pct) + pct + "-point better chance of extra loot in gates";
            case Perks.FORCE_AMP -> "elemental reactions hit " + pct + "% harder";
            case Perks.STEALTH, Perks.WOLF_FRIEND, Perks.SECOND_WIND -> name(p.id()) + " (it doesn't do anything yet)";
            default -> name(p.id()) + (p.amount() == 1 ? "" : " " + n);
        };
    }
    public static String perks(List<Perk> list) { return and(list.stream().map(RewardWords::perk).toList()); }

    /** Immediate grants only. Perks are activated by the owning Truth, title or latent, not RewardHooks.apply. */
    public static List<String> grants(Reward r) {
        List<String> out = new ArrayList<>();
        if (r.levels() > 0) out.add(levelsWorth(r.levels()));
        if (r.statPoints() > 0) out.add(r.statPoints() == 1 ? "a stat point" : r.statPoints() + " stat points");
        r.stat().ifPresent(s -> out.add("+" + Math.max(1, r.statAmount()) + " " + s.displayName));
        r.power().ifPresent(s -> out.add("the power " + RewardHooks.powerName.apply(s) + " (or a level in it, if you have it)"));
        r.item().ifPresent(s -> {
            Identifier id = Identifier.tryParse(s);
            if (id != null) BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> out.add(new ItemStack(item).getHoverName().getString()
                    + (r.itemCount() > 1 ? " ×" + r.itemCount() : "")));
        });
        return List.copyOf(out);
    }

    /** Everything a reward gives, as one phrase; "just the recognition" when it gives nothing you can count. */
    public static String describe(Reward r) {
        List<String> out = new ArrayList<>(grants(r));
        if (!r.perks().isEmpty()) out.add(perks(r.perks()));
        return out.isEmpty() ? "just the recognition" : and(out);
    }

    /** A phrase as a sentence of its own: capital first, full stop last. */
    public static String sentence(String phrase) {
        if (phrase.isEmpty()) return phrase;
        String s = Character.toUpperCase(phrase.charAt(0)) + phrase.substring(1);
        return s.endsWith(".") ? s : s + ".";
    }

    /** "a, b and c". */
    public static String and(List<String> parts) {
        if (parts.size() < 2) return parts.isEmpty() ? "" : parts.getFirst();
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }
    private static String a(String number) { return number.startsWith("8") || number.matches("1[18](\\..*)?") ? "an " : "a "; }
    private static String name(String id) {
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
