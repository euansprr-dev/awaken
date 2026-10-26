package dev.awaken.village;

import dev.awaken.Awaken;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/** The villages' own items: the inn's room key (bought from the innkeeper; a keepsake once the night is over). */
public final class VillageContent {
    private VillageContent() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    public static final DeferredItem<InnKey> INN_KEY = ITEMS.registerItem("inn_key", InnKey::new, p -> p.stacksTo(1));

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** The key's line in its tooltip: what it's for. */
    public static final class InnKey extends Item {
        public InnKey(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<net.minecraft.network.chat.Component> out,
                                    TooltipFlag flag) {
            out.accept(net.minecraft.network.chat.Component.translatable("item.awaken.inn_key.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
