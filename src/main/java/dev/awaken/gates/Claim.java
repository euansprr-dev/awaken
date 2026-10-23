package dev.awaken.gates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A reward on its way to a player (plan contract "Gate run and reward claim"). Written PENDING with the Satchel's
 * contents in the same tick the Satchel is emptied; delivered (items to the inventory, Satchel XP granted) when the
 * player is safely outside a gate; then marked DONE. A DONE id is never paid again, and a second claim with the same id
 * is refused, so every step can be replayed safely after a crash. Id = gateId:floor:player.
 */
public record Claim(String id, UUID player, int gateId, List<ItemStack> items, double xp, String kind, String note) {
    public static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Claim::id),
            UUIDUtil.CODEC.fieldOf("player").forGetter(Claim::player),
            Codec.INT.fieldOf("gate").forGetter(Claim::gateId),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(Claim::items),
            Codec.DOUBLE.fieldOf("xp").forGetter(Claim::xp),
            Codec.STRING.fieldOf("kind").forGetter(Claim::kind),
            Codec.STRING.fieldOf("note").forGetter(Claim::note)
    ).apply(i, Claim::new));

    public static String id(int gateId, String floor, UUID player) {
        return gateId + ":" + floor + ":" + player;
    }

    public Claim {
        items = List.copyOf(items);
    }
}
