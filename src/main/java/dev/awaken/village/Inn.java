package dev.awaken.village;

import dev.awaken.Awaken;
import dev.awaken.citizens.Lines;
import dev.awaken.city.CityPlan;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A bed at the inn (SPEC §28.6, PLAN 5.5): the innkeeper sells a room for the night ({@link #PRICE} emeralds, the key is
 * the receipt and a keepsake). The room's bed becomes where you wake if you fall, once: after that (or once the next
 * night is over) your own respawn point is given back. With the key come the night's rumours.
 */
final class Inn {
    private Inn() {}

    static final String ROOM = "inn_room";
    static final int PRICE = 3;

    /** The room: the inn's guest bed (upstairs first), your respawn there for one fall, a rumour. */
    static void grant(ServerPlayer p, VillageService.Live l, int rec) {
        ServerLevel level = (ServerLevel) p.level();
        BlockPos bed = bed(level, l.plan());
        if (bed == null) {
            // no bed found (the room was taken apart): the money back
            p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, PRICE));
            VillageService.say(p, l, rec, Lines.get().pick(p.level().getGameTime(), 0, "village.inn.full"), 80);
            return;
        }
        MinecraftServer server = level.getServer();
        VillageState st = VillageState.get(server);
        VillageState.Stay had = st.inn.get(p.getUUID());
        Optional<ServerPlayer.RespawnConfig> before = had != null ? had.before() : Optional.ofNullable(p.getRespawnConfig());
        st.inn.put(p.getUUID(), new VillageState.Stay(l.key(), bed.asLong(), before, VillageService.today(server)));
        st.setDirty();
        dev.awaken.core.api.HearthHooks.lendRespawn(p, new ServerPlayer.RespawnConfig(new LevelData.RespawnData(GlobalPos.of(level.dimension(), bed), p.getYRot(), 0f), false));
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        long seed = p.level().getGameTime() + rec;
        VillageService.say(p, l, rec, Lines.get().pick(seed, tod, "village.inn.room"), 100);
        // the night's rumours, a moment later in the chat (the innkeeper's line is over their head)
        String rumour = VillageService.fill(l, p, Lines.fill(Lines.get().pick(seed + 3, tod, "village.inn.rumour"), p.getName().getString(),
                l.plan().name, "", VillageService.hour(p)));
        if (!rumour.isEmpty()) p.sendSystemMessage(Component.translatable("awaken.village.inn.rumour", l.settlement().personName(rec), rumour)
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        VillageService.standing(p, l, 1, "a night at the inn");
        Awaken.LOG.info("[village] {} took a room at {}'s inn: bed {}", p.getName().getString(), l.plan().name, bed.toShortString());
    }

    /** The inn's guest bed: a bed's head in its plot, the highest storey first (the guests' rooms). */
    static @Nullable BlockPos bed(ServerLevel level, VillagePlan plan) {
        for (CityPlan.Plot q : plan.plots) {
            if (!q.use().equals("inn")) continue;
            BlockPos best = null;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int y = plan.baseY + q.lift() + q.eave(); y > plan.baseY + q.lift(); y--) {
                for (int x = q.x0() + 1; x < q.x1() && best == null; x++) {
                    for (int z = q.z0() + 1; z < q.z1() && best == null; z++) {
                        m.set(plan.ox + x, y, plan.oz + z);
                        if (!level.isLoaded(m)) continue;
                        BlockState s = level.getBlockState(m);
                        if (s.getBlock() instanceof AbstractBedBlock && s.getValue(AbstractBedBlock.PART) == BedPart.HEAD) best = m.immutable();
                    }
                }
                if (best != null) return best;
            }
        }
        return null;
    }

    /** You woke at the inn: the room is spent, your own respawn point is back. */
    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer p)) return;
        VillageState st = VillageState.get(p.level().getServer());
        VillageState.Stay stay = st.inn.remove(p.getUUID());
        if (stay == null) return;
        st.setDirty();
        restore(p, stay);
        p.sendSystemMessage(Component.translatable("awaken.village.inn.woke"));
    }

    /** A night passed without a fall: the room is given up the morning after next. */
    static void expire(MinecraftServer server) {
        VillageState st = VillageState.get(server);
        if (st.inn.isEmpty()) return;
        long today = VillageService.today(server);
        for (var e : java.util.List.copyOf(st.inn.entrySet())) {
            if (today - e.getValue().day() < 2) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            st.inn.remove(e.getKey());
            st.setDirty();
            restore(p, e.getValue());
        }
    }

    private static void restore(ServerPlayer p, VillageState.Stay stay) {
        ServerPlayer.RespawnConfig now = p.getRespawnConfig();
        // only if it still points at the inn's bed (sleeping in another bed since chose that one)
        if (now != null && now.respawnData().globalPos().pos().asLong() != stay.bed()) return;
        dev.awaken.core.api.HearthHooks.returnRespawn(p, stay.before().orElse(null));
    }

    static boolean has(Player p, MinecraftServer server) {
        return VillageState.get(server).inn.containsKey(p.getUUID());
    }
}
