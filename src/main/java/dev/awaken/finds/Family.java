package dev.awaken.finds;

import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * The twelve families of monuments (SPEC §64.1): one idea each, five monuments of it found further and further out, and
 * one relic that grows a step with each of the five a player finishes. The four groups are the story's own (before the
 * split, up, in, down) and are never named to the player: the monuments say it a piece at a time.
 */
public enum Family {
    DISH("The Listening Dishes", Old.BEFORE, "The Listening Stone", () -> Relics.LISTENING_STONE.get()),
    ROAD("The Long Road", Old.BEFORE, "The Waystone", () -> Relics.WAYSTONE.get()),
    ARCHIVE("The Archives", Old.BEFORE, "The Reading Glass", () -> Relics.READING_GLASS.get()),
    STAIR("The Stairs", Old.UP, "The Counterweight", () -> Relics.COUNTERWEIGHT.get()),
    MAST("The Mooring Masts", Old.UP, "The Mooring Ring", () -> Relics.MOORING_RING.get()),
    ORRERY("The Orreries", Old.UP, "The Pocket Rings", () -> Relics.POCKET_RINGS.get()),
    HALL("The Sleepers' Halls", Old.IN, "The Sleeper's Lamp", () -> Relics.SLEEPERS_LAMP.get()),
    GARDEN("The Dream Gardens", Old.IN, "The Night Bloom", () -> Relics.NIGHT_BLOOM.get()),
    CLOCK("The Water Clocks", Old.IN, "The Float", () -> Relics.FLOAT.get()),
    SHAFT("The Sealed Shafts", Old.DOWN, "The Seal Shard", () -> Relics.SEAL_SHARD.get()),
    CHAINS("The Chain Works", Old.DOWN, "The Link", () -> Relics.LINK.get()),
    DOOR("The Doors in the Sand", Old.DOWN, "The Digger's Glass", () -> Relics.DIGGERS_GLASS.get());

    /** Who built it. Those who went down are met last: their first monument stands a rank further out than the others'. */
    public enum Old { BEFORE, UP, IN, DOWN }

    public final String display, relicName;
    public final Old old;
    private final Supplier<Item> relic;

    Family(String display, Old old, String relicName, Supplier<Item> relic) {
        this.display = display;
        this.old = old;
        this.relicName = relicName;
        this.relic = relic;
    }

    public Item relic() {
        return relic.get();
    }

    /** The rank at which the family's {@code tier} (1..5) first stands: 0..4, and one further for those who went down. */
    public int rankOf(int tier) {
        return Math.min(5, tier - 1 + (old == Old.DOWN ? 1 : 0));
    }

    /** How many of the family's five a player has finished: the step of its relic, 0..5. */
    public int done(FindsData d) {
        int n = 0;
        for (Kind k : Kinds.monuments()) if (k.family() == this && d.count(k.id()) > 0) n++;
        return n;
    }
}
