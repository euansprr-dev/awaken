package dev.awaken.storage;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** The one list of kinds of thing (SPEC §56), in the order the bag, chests, bays and the Ledger all use. */
public enum Sort {
    STONE("stone", "Stone and earth"),
    WOOD("wood", "Wood"),
    BUILDING("building", "Building and light"),
    METAL("metal", "Ore and metal"),
    GEMS("gems", "Gems"),
    FOOD("food", "Food"),
    PLANTS("plants", "Plants and seed"),
    DROPS("drops", "Beast and monster drops"),
    GATE("gate", "Gate loot"),
    GEAR("gear", "Gear"),
    BREWS("brews", "Books and brews"),
    BELLS("bells", "Bells and leads"),
    ODD("odd", "Odd things");

    public final String key;
    public final String label;
    /** {@code awaken:sort/<key>}: wins over every rule, so a wrong answer is one line in a tag file. */
    public final TagKey<Item> tag;

    Sort(String key, String label) {
        this.key = key;
        this.label = label;
        this.tag = TagKey.create(Registries.ITEM, Awaken.id("sort/" + key));
    }
}
