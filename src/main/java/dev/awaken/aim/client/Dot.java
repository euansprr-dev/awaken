package dev.awaken.aim.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * When the dot shows in place of the crosshair (SPEC §40.5): a block, tool, bucket, bow or crossbow in hand, the attack key
 * held on a block, or something usable in reach under the view (a door, a chest, a lever, a bed, a bell, anything with a
 * screen; a person or a creature that isn't fighting you). Never in a fight's lock, whose ring is the aim.
 */
final class Dot {
    private Dot() {}

    static boolean wanted(Minecraft mc, LocalPlayer p) {
        if (Lock.locked >= 0) return false;
        if (aims(p.getMainHandItem()) || aims(p.getOffhandItem())) return true;
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && mc.level != null) {
            if (mc.options.keyAttack.isDown()) return true;
            return usable(mc, bh.getBlockPos());
        }
        return hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && !Lock.foe(le);
    }

    /** What in hand is aimed at a block or a spot: a block, a tool, a bucket, a bow or crossbow. */
    private static boolean aims(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.getItem() instanceof BlockItem || s.getItem() instanceof BucketItem || s.getItem() instanceof ProjectileWeaponItem
                || s.has(DataComponents.TOOL);
    }

    private static boolean usable(Minecraft mc, BlockPos pos) {
        BlockState st = mc.level.getBlockState(pos);
        Block b = st.getBlock();
        return b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock
                || b instanceof BedBlock || b instanceof BellBlock || mc.level.getBlockEntity(pos) != null || st.getMenuProvider(mc.level, pos) != null;
    }
}
