package dev.awaken.enchant.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.awaken.enchant.Inscriptions;
import dev.awaken.enchant.Pages;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Item model select {@code awaken:page} (SPEC §45.15): a page shows the sign of the enchantment it carries (cases are
 * the enchantment's id path, "bane_of_crawlers"); a page of anything else keeps the plain leaf.
 */
record PageSelect() implements SelectItemModelProperty<String> {
    static final SelectItemModelProperty.Type<PageSelect, String> TYPE =
            SelectItemModelProperty.Type.create(MapCodec.unit(new PageSelect()), Codec.STRING);

    @Override
    public @Nullable String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
        List<Inscriptions.Entry> on = Pages.teaches(stack);
        return on.isEmpty() ? null : on.getFirst().id().getPath();
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public SelectItemModelProperty.Type<PageSelect, String> type() {
        return TYPE;
    }
}
