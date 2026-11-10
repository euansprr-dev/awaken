package dev.awaken.finds;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.Sources;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.sets.AllSets;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Finds (SPEC §58, "Over Every Hill"): 144 kinds of small thing to find and do in the overworld, in eighteen sets,
 * placed from the seed with quiet land between them. See {@link Kind}, {@link Sites}, {@link FindHeartBlockEntity}.
 */
public final class FindsModule {
    private FindsModule() {}

    /** Props too heavy to run with. */
    public static final TagKey<Item> HEAVY = TagKey.create(Registries.ITEM, Awaken.id("finds/heavy"));

    public static boolean on() {
        try {
            return AwakenConfig.FINDS.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    public static void init(IEventBus modBus) {
        FindsContent.init(modBus);
        Props.init(modBus);
        AllSets.init();
        Kinds.seal();
        FindsEvents.init(NeoForge.EVENT_BUS);
        FindsCommands.init();
        FindsJournal.init();
        FindsGoals.register();
        FindsTests.init();
        Sources.declare("found at a find out in the world (finds.Reward), or picked up there", Props.ids());
        Sources.neverHeld("the world places it under a find; it has no item", "find_heart");
    }
}
