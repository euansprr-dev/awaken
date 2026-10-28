package dev.awaken.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Authored reward semantics, shared by the Journal and the existing reward cards. */
public final class RewardWords {
    private RewardWords() {}
    public static String number(double n) { return n == Math.rint(n) ? Long.toString((long) n) : String.format(Locale.ROOT, "%.2f", n).replaceAll("0+$", ""); }
    public static String perk(Perk p) {
        String n = number(p.amount()), pct = number(p.amount() * 100);
        return switch (p.id()) {
            case Perks.DAMAGE_ABOVE_HALF_HP -> "+" + pct + "% damage while above half health";
            case Perks.SECRET_GLOW_RANGE -> "+" + n + " blocks of spirit awareness and secret sensing (awareness cap: 160 blocks)";
            case Perks.HEARTH_SHIELD -> "Protect buildings near your Hearth from gate-break damage while a household member with this perk is online";
            case Perks.FLOW_DURATION -> "+" + pct + "% Flow duration";
            case Perks.CHEAT_DEATH_PER_GATE -> "Survive one otherwise fatal hit per gate run";
            case Perks.EXTRA_DIRECTIVE_SLOT -> "+" + n + " active directive slots";
            case Perks.MOVE_SPEED -> "+" + pct + "% movement speed";
            case Perks.MAX_HEALTH -> "+" + n + " maximum health";
            case Perks.ENERGY_REGEN -> "+" + pct + "% energy regeneration";
            case Perks.COOLDOWN_REDUCTION -> "Reduce power cooldowns by " + pct + "% (combined cap: 50%)";
            case Perks.FALL_RESIST -> "Reduce fall damage by " + pct + "%";
            case Perks.EXPLOSION_RESIST -> "Add " + pct + "% explosion resistance (total resistance cap: 80%)";
            case Perks.BIND_CHANCE -> "+" + pct + " percentage points to Echo binding chance, subject to its chance cap";
            case Perks.LUCIDITY_START -> "+" + n + " starting Lucidity";
            case Perks.CORD_LENGTH -> "+" + n + " blocks of projection cord length (cap: 1,024 blocks)";
            case Perks.CRIT_CHANCE -> "+" + pct + " percentage points to melee critical chance (combined cap: 35%)";
            case Perks.XP_BONUS -> "+" + pct + "% progression XP";
            case Perks.LOOT_BONUS -> "+" + pct + " percentage points to the chance of an extra gate loot roll";
            case Perks.FORCE_AMP -> "+" + pct + "% force-reaction damage";
            case Perks.STEALTH, Perks.WOLF_FRIEND, Perks.SECOND_WIND -> "Recorded perk " + p.id() + ": its gameplay effect is not active in this build";
            default -> "Recorded perk " + p.id() + " (" + n + "); no effect description is registered";
        };
    }
    public static String perks(List<Perk> list) { return String.join("; ", list.stream().map(RewardWords::perk).toList()); }
    /** Immediate grants only. Perks are activated by the owning Truth, title or latent, not RewardHooks.apply. */
    public static List<String> grants(Reward r) {
        List<String> out = new ArrayList<>();
        if (r.levels() > 0) out.add(number(r.levels() * 100) + "% of a current level in progression XP (normal XP modifiers apply)");
        if (r.statPoints() > 0) out.add("+" + r.statPoints() + " unspent stat points; assign them on Status");
        r.stat().ifPresent(s -> out.add("+" + Math.max(1, r.statAmount()) + " " + s.name()));
        r.power().ifPresent(s -> out.add("Learn or upgrade " + RewardHooks.powerName.apply(s) + "; its power entry explains the resulting change"));
        r.item().ifPresent(s -> {
            Identifier id = Identifier.tryParse(s);
            if (id != null) BuiltInRegistries.ITEM.getOptional(id).ifPresent(item ->
                    out.add(Math.max(1, r.itemCount()) + " × " + new ItemStack(item).getHoverName().getString() + " added to your inventory (dropped nearby if full)"));
        });
        return List.copyOf(out);
    }
    public static String describe(Reward r) {
        List<String> out = new ArrayList<>(grants(r));
        if (!r.perks().isEmpty()) out.add(perks(r.perks()));
        return out.isEmpty() ? "No numeric or item reward" : String.join("; ", out);
    }
}
