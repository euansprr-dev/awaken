package dev.awaken.anims.client;

import dev.awaken.anims.AnimsClips;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Touches (Hands P4; SPEC §24): quick hand moments on the things you use, from the game's own interaction events, so
 * every module's doors, levers, chests and plants get them by what they are: a palm pushes a door or gate through its
 * swing, a lever is thrown, a button pressed, a chest's lid lifted with both hands, a plant pinched and plucked, a
 * creature stroked; a meal in a bowl is held up with both hands. The action never waits for them (they animate what
 * already happened); swinging cuts them. With the main hand full the off hand does it; with both full, nothing.
 */
final class Touches {
    private Touches() {}

    private static int lastAt = -100;
    private static int ticks;
    private static final boolean TRACE = System.getProperty("awaken.autopilot") != null;

    private static void trace(String what) {
        if (TRACE) dev.awaken.Awaken.LOG.info("[anims] touch: {}", what);
    }

    static void tick() {
        ticks++;
    }

    private static boolean on(LocalPlayer p) {
        return FirstPerson.touchesOn.getAsBoolean() && Minecraft.getInstance().options.getCameraType().isFirstPerson() && !p.isSpectator();
    }

    /** Plays a touch with the free hand (the main, else the off one's twin). */
    private static void play(LocalPlayer p, String clip) {
        if (ticks - lastAt < 4) return; // a held click repeats the event; once is enough
        lastAt = ticks;
        trace(clip + " (main " + p.getMainHandItem() + ", off " + p.getOffhandItem() + ")");
        if (p.getMainHandItem().isEmpty()) FirstPerson.play(clip);
        else if (p.getOffhandItem().isEmpty() && !clip.equals(AnimsClips.LID)) FirstPerson.play(clip + "_off");
    }

    static void rightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        trace("right click " + e.getLevel().isClientSide() + " " + e.getHand() + " " + e.getLevel().getBlockState(e.getPos()));
        if (!e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || e.getEntity() != p || !on(p)) return;
        if (p.isShiftKeyDown() && !p.getMainHandItem().isEmpty()) return; // a sneak-use places or uses the item
        String clip = forUse(e.getLevel().getBlockState(e.getPos()));
        if (clip != null) play(p, clip);
    }

    private static @Nullable String forUse(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return AnimsClips.PUSH;
        if (b instanceof LeverBlock) return AnimsClips.LEVER;
        if (b instanceof ButtonBlock) return AnimsClips.BUTTON;
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof EnderChestBlock || b instanceof ShulkerBoxBlock) return AnimsClips.LID;
        if (b instanceof SweetBerryBushBlock || b instanceof CaveVinesBlock) return AnimsClips.PLUCK;
        return null;
    }

    /** Breaking a plant that breaks at a touch (a flower, a fern, grass, a mushroom: the Living Wilds' herbs): plucked. */
    static void leftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (!e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || e.getEntity() != p || !on(p)) return;
        BlockState s = e.getLevel().getBlockState(e.getPos());
        if (!s.isAir() && s.getDestroySpeed(e.getLevel(), e.getPos()) == 0 && s.getCollisionShape(e.getLevel(), e.getPos()).isEmpty()
                && s.getFluidState().isEmpty()) {
            play(p, AnimsClips.PLUCK);
        }
    }

    /** An animal, with nothing in the hand: stroked (with food in it, vanilla feeds it). */
    static void entityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || e.getEntity() != p || !on(p) || !(e.getTarget() instanceof Animal)) return;
        if (p.getMainHandItem().isEmpty()) play(p, AnimsClips.PET);
    }

    /** A swing cuts any touch (the hands are needed). */
    static void interactionKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        for (String c : AnimsClips.TOUCHES) {
            FirstPerson.stop(c);
            FirstPerson.stop(c + "_off");
        }
    }

    /** A stance: eating a meal from a bowl, the free hand comes up under the bowl. */
    static void viewPose(float pt, FirstPerson.View v) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.isUsingItem() || !FirstPerson.touchesOn.getAsBoolean()) return;
        ItemStack using = p.getUseItem();
        if (using.getUseAnimation() != ItemUseAnimation.EAT && using.getUseAnimation() != ItemUseAnimation.DRINK) return;
        UseRemainder r = using.get(DataComponents.USE_REMAINDER);
        if (r == null || r.convertInto().item().value() != Items.BOWL) return;
        boolean mainUses = p.getUsedItemHand() == InteractionHand.MAIN_HAND;
        float k = Math.min(1F, (p.getTicksUsingItem() + pt) / 5F);
        float side = (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) == mainUses ? 1 : -1; // the free hand's side
        float[] fist = mainUses ? v.fistOff : v.fistMain;
        if ((mainUses ? p.getOffhandItem() : p.getMainHandItem()).isEmpty()) FirstPerson.View.fist(fist, k, side * 0.04F, -0.46F, -0.72F);
    }
}
