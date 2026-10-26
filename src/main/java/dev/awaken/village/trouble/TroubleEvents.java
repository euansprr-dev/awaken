package dev.awaken.village.trouble;

import dev.awaken.citizens.CitizensContent;
import dev.awaken.living.camp.Camps;
import dev.awaken.village.VillageService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The world's side of the troubles (SPEC §28.7): beating a roof's flames out, reading a clue, a raider felled. */
final class TroubleEvents {
    private TroubleEvents() {}

    static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = level.getBlockState(e.getPos());
        if (s.is(CitizensContent.CITY_FIRE.get()) && Burning.beat(level, p, e.getPos())) e.setCanceled(true);
    }

    /** A clue looked at: the cloth is someone's, the scuffs run a way. */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = e.getPos();
        BlockState s = level.getBlockState(pos);
        boolean cloth = s.is(TroubleContent.TORN_CLOTH.get()), scuff = s.is(TroubleContent.DRAG_MARKS.get());
        if (!cloth && !scuff) return;
        for (Trouble t : TroubleState.get(level.getServer()).troubles()) {
            if (!t.laid.contains(pos.asLong())) continue;
            VillageService.Live l = VillageService.live(t.village);
            String person = l != null && t.person >= 0 ? TroubleText.given(l.settlement().personName(t.person)) : "someone";
            BlockPos to = BlockPos.of(t.place);
            String dir = TroubleText.compass(to.getX() - pos.getX(), to.getZ() - pos.getZ());
            p.sendOverlayMessage(Component.literal(cloth ? "A strip of " + person + "'s clothes, caught here. The way runs " + dir + "."
                    : "Scuffed earth: someone was dragged this way, " + dir + "."));
            e.setCanceled(true);
            return;
        }
    }

    /** A raider felled by a player counts them in on the Burning (whether they said Help or not). */
    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !e.getEntity().entityTags().contains(Camps.RAIDER)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        for (Trouble t : TroubleState.get(level.getServer()).troubles()) {
            if (!t.kind.equals(TroubleKind.BURNING.id) || t.stage != Burning.RAID || t.helped(p.getUUID())) continue;
            t.helpers.add(p.getUUID());
            TroubleState.get(level.getServer()).setDirty();
        }
    }
}
