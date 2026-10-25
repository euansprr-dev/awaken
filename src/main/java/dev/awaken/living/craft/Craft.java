package dev.awaken.living.craft;

import java.util.List;

/**
 * The six crafts (SPEC §21.9): each levels 1-50 as you do it, with perks at 5, 10, 20, 30, 40 and 50 that change how
 * you play survival. {@code budget} is the source its share of the Hunter level pays through (the sim's budgets), and
 * {@code icon} the letter its toast and tab line show.
 */
public enum Craft {
    HUNTING("hunting", "Hunting", "hunt", 0xFF9A5A, List.of(
            new Perk(5, "field_dressing", "Field Dressing", "Animals you kill drop a hide or more meat a third of the time."),
            new Perk(10, "tracks", "Tracks", "A creature you've read leaves glowing tracks on the band for 30 seconds."),
            new Perk(20, "weak_spot", "Weak Spot", "Critical hits deal 15% more to kinds you've killed twenty of."),
            new Perk(30, "stalker", "Stalker", "Sneak attacks on a creature that hasn't seen you deal half again as much."),
            new Perk(40, "trophy", "Trophy", "Beasts drop their parts twice."),
            new Perk(50, "apex", "Apex", "10% more damage to elites and named beasts."))),
    FORAGING("foraging", "Foraging", "forage", 0x8FE07A, List.of(
            new Perk(5, "herb_glint", "Herb Glint", "Ripe wild herbs glint within 16 blocks."),
            new Perk(10, "wild_larder", "Wild Larder", "One more from berry bushes, glow berries and mushrooms."),
            new Perk(20, "root_sense", "Root Sense", "Twice as many plants are ripe for you."),
            new Perk(30, "seed_keeper", "Seed Keeper", "Grass sometimes gives rare seeds."),
            new Perk(40, "bloom", "Bloom", "Bloom Sprites come to you among the flowers."),
            new Perk(50, "wildheart", "Wildheart", "You heal slowly while you're in a forest."))),
    MINING("mining", "Mining", "mine", 0x9FC3E6, List.of(
            new Perk(5, "clean_strike", "Clean Strike", "Ores sometimes give one more."),
            new Perk(10, "ore_sense", "Ore Sense", "With a pick in hand, the band shows ore within 6 blocks through the stone."),
            new Perk(20, "vein_sight", "Vein Sight", "Ore Sense reaches 10 blocks."),
            new Perk(30, "deep_ear", "Deep Ear", "You mine stone and deepslate a tenth faster."),
            new Perk(40, "deep_vein", "Deep Vein", "Deepslate ores give one more a quarter of the time."),
            new Perk(50, "heart_of_stone", "Heart of Stone", "Below the surface (under y 0) you take less damage."))),
    FISHING("fishing", "Fishing", "fish", 0x6FC8E6, List.of(
            new Perk(5, "calm_water", "Calm Water", "One more fish a fifth of the time."),
            new Perk(10, "shimmer", "Shimmer", "Some catches bring up a treasure as well."),
            new Perk(20, "deep_lungs", "Deep Lungs", "Underwater, your breath lasts twice as long."),
            new Perk(30, "storm_caller", "Storm Caller", "In the rain, every catch brings one more."),
            new Perk(40, "pearl_diver", "Pearl Diver", "Catches sometimes bring up a prismarine shard or an ender pearl."),
            new Perk(50, "leviathan_line", "Leviathan Line", "Your catches feed the Hunter level twice as much."))),
    SMITHING("smithing", "Smithing", "smith", 0xFFC27A, List.of(
            new Perk(5, "patch", "Patch", "Repairs at an anvil cost a level less."),
            new Perk(10, "fine_edge", "Fine Edge", "Weapons and tools you craft come out with a little more durability."),
            new Perk(20, "field_forge", "Field Forge", "Use an iron ingot on a lit campfire to mend what you hold."),
            new Perk(30, "masterwork", "Masterwork", "A tenth of what you craft comes out with Unbreaking I."),
            new Perk(40, "keen", "Keen", "Weapons you craft hit a little harder (Sharpness I a fifth of the time)."),
            new Perk(50, "signature", "Signature", "Your name goes on everything you make."))),
    COOKING("cooking", "Cooking", "cook", 0xFFE08A, List.of(
            new Perk(5, "hearty", "Hearty", "Cooked food fills you a little more."),
            new Perk(10, "campfire_feast", "Campfire Feast", "Eating beside a lit campfire feeds those near you too."),
            new Perk(20, "hunters_stew", "Hunter's Stew", "Your meals' strength lasts half again as long."),
            new Perk(30, "long_meals", "Long Meals", "Food keeps you full for longer (less hunger)."),
            new Perk(40, "spirit_tea", "Spirit Tea", "After a meal, your energy comes back faster for a while."),
            new Perk(50, "banquet", "Banquet", "Every meal you cook is a feast: twice the saturation.")));

    /** A perk: the craft level it opens at, its id ({@code awaken:craft/<id>}), name and what it does. */
    public record Perk(int level, String id, String title, String text) {}

    public static final int MAX_LEVEL = 50;

    public final String id, title, budget;
    public final int colour;
    public final List<Perk> perks;

    Craft(String id, String title, String budget, int colour, List<Perk> perks) {
        this.id = id;
        this.title = title;
        this.budget = budget;
        this.colour = colour;
        this.perks = perks;
    }

    /** Craft XP from level c to c + 1: 40·c^1.3 + 40. */
    public static double toNext(int c) {
        return 40 * Math.pow(c, 1.3) + 40;
    }

    /** The level for a total of craft XP (1..50). */
    public static int levelFor(double xp) {
        int lv = 1;
        while (lv < MAX_LEVEL && xp >= toNext(lv)) {
            xp -= toNext(lv);
            lv++;
        }
        return lv;
    }

    /** XP into the current level, for the bar. */
    public static double intoLevel(double xp) {
        int lv = 1;
        while (lv < MAX_LEVEL && xp >= toNext(lv)) {
            xp -= toNext(lv);
            lv++;
        }
        return xp;
    }

    public static Craft byId(String id) {
        for (Craft c : values()) if (c.id.equals(id)) return c;
        return null;
    }
}
