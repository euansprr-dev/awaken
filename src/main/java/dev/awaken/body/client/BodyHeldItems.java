package dev.awaken.body.client;

import dev.awaken.body.BodyContent;
import dev.awaken.body.BodyStow;
import dev.awaken.body.ascent.client.AscentMoves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The hands are empty while climbing; only the extracted appearance changes, never the player's inventory. */
final class BodyHeldItems {
    private BodyHeldItems() {}

    static void modify(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Player p) || !(state instanceof ArmedEntityRenderState hands) || !p.isAlive() || p.isSpectator()) return;
        boolean stowed = p == Minecraft.getInstance().player ? localClimb() : Boolean.TRUE.equals(p.getExistingDataOrNull(BodyContent.STOWED));
        if (!stowed) return;
        hands.rightHandItemState.clear();
        hands.leftHandItemState.clear();
        hands.rightHandItemStack = hands.leftHandItemStack = ItemStack.EMPTY;
        // Vanilla rebuilds these from current equipment next extraction, so release needs no cached item to restore.
    }

    private static boolean localClimb() {
        return AscentMoves.state().rope || ClientMoves.mantling
                || ClientMoves.vaulting && ClientMoves.vault != null && BodyStow.climbVault(ClientMoves.vault.kind())
                || switch (RunnerMoves.state) {
                    case HANG, PATH, GRIP -> true;
                    case RUN -> RunnerMoves.runClimb;
                    case NONE -> false;
                };
    }
}
