package dev.awaken.tasks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * The real item that stands for each goal on the band's stage when a step shows nothing of its own (SPEC §57.3): the lane's
 * emblem. Two ids each: AWAKEN's own, and a vanilla one should it ever be missing. Common code, so a test can check that
 * every one of them is a real item.
 */
public final class TaskPictures {
    private TaskPictures() {}

    /** By Goals.Goal's ordinal: Hearth, Armour, Weapons, Bells, Gates and rank, Out there. */
    public static final String[][] EMBLEM = {
            {"minecraft:campfire", "minecraft:campfire"}, {"awaken:wildhide_chestplate", "minecraft:leather_chestplate"},
            {"awaken:wildhide_longsword", "minecraft:iron_sword"}, {"minecraft:bell", "minecraft:bell"},
            {"awaken:core_shard", "minecraft:ender_eye"}, {"minecraft:compass", "minecraft:compass"}};

    /** Whether an item id names a registered item. */
    public static boolean real(String id) {
        Identifier at = Identifier.tryParse(id);
        return at != null && BuiltInRegistries.ITEM.containsKey(at);
    }
}
