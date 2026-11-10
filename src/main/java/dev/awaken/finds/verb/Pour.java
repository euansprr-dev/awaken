package dev.awaken.finds.verb;

import dev.awaken.finds.kit.PropBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pour (SPEC §58.5): bring water to a basin, or heat to something frozen. It takes a number of pours; a prop basin
 * shows how full it is by its state. Numbers: 0 pours so far.
 */
public final class Pour implements Script {
    private final String part;
    private final int pours;
    private boolean heat;

    private Pour(String part, int pours) {
        this.part = part;
        this.pours = pours;
    }

    public static Pour water(String part, int pours) {
        return new Pour(part, pours);
    }

    public static Pour heat(String part, int pours) {
        Pour p = new Pour(part, pours);
        p.heat = true;
        return p;
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return false;
        if (heat ? !Hands.flame(held) : !Hands.water(held)) return false;
        if (!heat && held.is(Items.WATER_BUCKET) && !p.isCreative()) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        int n = c.v(0) + 1;
        c.v(0, n);
        BlockState s = c.at(part, i);
        if (!heat && s.getBlock() instanceof PropBlock) c.set(part, i, s.setValue(PropBlock.STATE, Math.min(2, (n * 2 + pours - 1) / pours)));
        c.sound(c.pos(part, i), heat ? SoundEvents.FIRECHARGE_USE : SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
        c.puff(c.pos(part, i).above(), heat ? ParticleTypes.CLOUD : ParticleTypes.SPLASH, 10, 0.3, 0.05);
        if (n >= pours) c.complete(p);
        return true;
    }
}
