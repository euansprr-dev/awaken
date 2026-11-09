package dev.awaken.delve.forged;

import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What the forged picks register (SPEC §49): the 24 items, the Delver's old head, and the story every pick carries. */
public final class DelveForged {
    private DelveForged() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PickForged>> PICK_FORGED = COMPONENTS.registerComponentType("pick_forged",
            b -> b.persistent(PickForged.CODEC).networkSynchronized(PickForged.STREAM_CODEC));

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    /** The tap of Sounding, and rock sounding hollow from one side. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUNDING_TAP = sound("pick.sounding_tap"), SOUNDING_HOLLOW = sound("pick.sounding_hollow");

    /** The Mattock leaving the hand, and landing back in it (§49.8). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MATTOCK_TOSS = sound("pick.mattock_toss"), MATTOCK_CATCH = sound("pick.mattock_catch");

    /** The Delver's old head: what the stone gives you now, the start of your first forged pick. */
    public static final DeferredItem<Item> OLD_HEAD = ITEMS.registerSimpleItem("delver_old_head", p -> p.stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());

    static {
        ForgedPicks.register(ITEMS);
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
