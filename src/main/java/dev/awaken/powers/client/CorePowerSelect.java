package dev.awaken.powers.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.content.PowerCoreItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Item model select {@code awaken:core_power}: a Power Core shows the crystal of its power (cases are power paths). */
record CorePowerSelect() implements SelectItemModelProperty<String> {
    static final SelectItemModelProperty.Type<CorePowerSelect, String> TYPE =
            SelectItemModelProperty.Type.create(MapCodec.unit(new CorePowerSelect()), Codec.STRING);

    @Override
    public @Nullable String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                                ItemDisplayContext context) {
        PowerType type = PowerCoreItem.typeOf(stack);
        return type == null ? null : type.path;
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public SelectItemModelProperty.Type<CorePowerSelect, String> type() {
        return TYPE;
    }
}
