package dev.awaken.bells;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * One of the hundred, as a thing you carry (SPEC §43.16, stage 2): its number in the {@code awaken:bell} component. Take it
 * home and use it on a hook (your Bell Frame, a Bell Beam, a Great Beam) to hang it. It doesn't burn, blow up or
 * despawn; if it falls out of the world it comes back to whoever dropped it, and it stays with you when you die.
 */
public final class BellItem extends Item {
    public BellItem(Item.Properties p) {
        super(p);
    }

    /** Bell #{@code no} as an item. */
    public static ItemStack of(int no) {
        ItemStack s = new ItemStack(BellContent.BELL.get());
        s.set(BellContent.BELL_NO.get(), no);
        return s;
    }

    /** The bell this stack is, or null when it isn't one (or carries no number). */
    public static BellCatalog.@Nullable Def def(ItemStack s) {
        if (!s.is(BellContent.BELL.get())) return null;
        Integer no = s.get(BellContent.BELL_NO.get());
        return no == null || no < 0 || no >= 100 ? null : BellCatalog.of(no);
    }

    @Override
    public Component getName(ItemStack stack) {
        BellCatalog.Def d = def(stack);
        return d == null ? super.getName(stack) : Component.literal(d.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        BellCatalog.Def d = def(stack);
        if (d == null) return;
        out.accept(Component.literal("Bell " + d.number() + " · " + d.rank().label + "-rank").withStyle(ChatFormatting.GOLD));
        if (!d.voice().words.isEmpty()) out.accept(Component.literal("While it hangs: " + d.voice().words).withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal(Hanging.great(d) ? "A Great Bell. Hang it on a Great Beam, 12 or more blocks above your fire."
                : "Use it on a hook by your fire to hang it: your Bell Frame, or a Bell Beam 8 or more above the fire.").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Used in the air: it rings in your hands, softly. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        BellCatalog.Def d = def(s);
        if (d == null || player.getCooldowns().isOnCooldown(s)) return InteractionResult.PASS;
        player.getCooldowns().addCooldown(s, 40);
        if (level instanceof ServerLevel sl)
            sl.playSound(null, player.getX(), player.getEyeY(), player.getZ(), BellContent.note(d.no()), SoundSource.PLAYERS, 0.8F, 1F);
        return InteractionResult.SUCCESS;
    }

    /** Used on a block that isn't a hook: say where it goes, now and then. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer p && !p.getCooldowns().isOnCooldown(ctx.getItemInHand())) {
            p.getCooldowns().addCooldown(ctx.getItemInHand(), 40);
            BellCatalog.Def d = def(ctx.getItemInHand());
            Bells.say(p, d != null && Hanging.great(d) ? "[A Great Bell hangs on a Great Beam, 12 or more blocks above your fire.]"
                    : "[Bells hang on hooks. Use it on your Bell Frame by your fire, or on a Bell Beam 8 or more above it.]");
        }
        return InteractionResult.SUCCESS;
    }

    // ---- it can't be lost -------------------------------------------------------------------------------------------

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return false;
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    /** Fallen out of the world: back to whoever dropped it, or up to the world's spawn. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getY() > level.getMinY() - 40) return false;
        if (entity.getOwner() instanceof ServerPlayer p && p.isAlive()) {
            Bells.giveBack(p, stack.copy());
            entity.discard();
            return true;
        }
        var spawn = level.getRespawnData().pos();
        entity.teleportTo(spawn.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()) + 1, spawn.getZ() + 0.5);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return true;
    }
}
