package dev.awaken.above;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Item data: essence stored in a cell, modules socketed in a Frame piece. */
public final class AboveComponents {
    private AboveComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);

    /** Essence held by an Essence Cell (0..{@link dev.awaken.above.power.EssenceCellItem#CAPACITY}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ESSENCE = COMPONENTS.registerComponentType(
            "essence", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Module ids socketed into a Frame piece, in slot order. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> FRAME_MODULES = COMPONENTS.registerComponentType(
            "frame_modules", b -> b.persistent(Codec.STRING.listOf()).networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));

    public static void init(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
