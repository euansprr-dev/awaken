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

    static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    /** Picks held for their owner between dying and getting up: on the player, so a restart in between loses nothing. */
    public record KeptPicks(int schemaVersion, java.util.List<net.minecraft.world.item.ItemStack> items) {
        public static final int SCHEMA = 1;
        public static final com.mojang.serialization.Codec<KeptPicks> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                com.mojang.serialization.Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(KeptPicks::schemaVersion),
                net.minecraft.world.item.ItemStack.CODEC.listOf().optionalFieldOf("items", java.util.List.of()).forGetter(KeptPicks::items)).apply(i, KeptPicks::new));
        public static final KeptPicks NONE = new KeptPicks(SCHEMA, java.util.List.of());
    }

    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<KeptPicks>> KEPT = ATTACHMENTS.register("kept_picks",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> KeptPicks.NONE).serialize(KeptPicks.CODEC.fieldOf("v")).copyOnDeath().build());

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    /** The tap of Sounding, and rock sounding hollow from one side. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUNDING_TAP = sound("pick.sounding_tap"), SOUNDING_HOLLOW = sound("pick.sounding_hollow");

    /** The Mattock leaving the hand, and landing back in it (§49.8). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MATTOCK_TOSS = sound("pick.mattock_toss"), MATTOCK_CATCH = sound("pick.mattock_catch");

    /** A forged pick's bite, by form (§49.10), its ring in tune with the ore's note; the Mattock's broad end in soil and timber; a blunt pick's knock. */
    public static final java.util.Map<PickForm, DeferredHolder<SoundEvent, SoundEvent>> STRIKE = new java.util.EnumMap<>(PickForm.class);

    static {
        for (PickForm f : PickForm.values()) STRIKE.put(f, sound("pick.strike_" + f.id));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> STRIKE_EARTH = sound("pick.strike_earth"), STRIKE_WOOD = sound("pick.strike_wood"),
            STRIKE_BLUNT = sound("pick.strike_blunt");

    /** The Delver's old head: what the stone gives you now, the start of your first forged pick. */
    public static final DeferredItem<Item> OLD_HEAD = ITEMS.registerSimpleItem("delver_old_head", p -> p.stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());

    static {
        ForgedPicks.register(ITEMS);
    }

    private static void creativeTab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(dev.awaken.core.CoreModule.TAB_KEY)) return;
        event.accept(OLD_HEAD.get());
        for (PickForm f : PickForm.values()) for (int t = 0; t < PickStats.TIERS; t++) event.accept(ForgedPicks.get(f, t));
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(DelveForged::creativeTab);
    }
}
