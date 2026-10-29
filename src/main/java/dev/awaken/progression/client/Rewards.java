package dev.awaken.progression.client;

import dev.awaken.progression.Perk;
import dev.awaken.progression.Reward;
import dev.awaken.signal.client.Ui;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Rewards and perks in plain words for the Status screen ("+2 stat points, Hunter's lantern, +5% move speed"). */
final class Rewards {
    private Rewards() {}

    static String describe(Reward r) { return dev.awaken.progression.RewardWords.describe(r); }
    static String perk(Perk p) { return dev.awaken.progression.RewardWords.perk(p); }
    static String perks(List<Perk> perks) { return dev.awaken.progression.RewardWords.perks(perks); }
}
