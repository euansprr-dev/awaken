package dev.awaken.powers.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.content.PowerCoreItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Item tint {@code awaken:power_family}: a Power Core's inner glow takes its power family's colour. */
record PowerCoreTint(int fallback) implements ItemTintSource {
    static final MapCodec<PowerCoreTint> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("default", 0xFF7FC3FF).forGetter(PowerCoreTint::fallback)
    ).apply(i, PowerCoreTint::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        PowerType type = PowerCoreItem.typeOf(stack);
        return type == null ? fallback : 0xFF000000 | type.family.colour;
    }

    @Override
    public MapCodec<PowerCoreTint> type() {
        return MAP_CODEC;
    }
}
