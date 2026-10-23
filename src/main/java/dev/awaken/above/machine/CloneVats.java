package dev.awaken.above.machine;

import dev.awaken.above.Above;
import dev.awaken.above.AboveData;
import dev.awaken.above.MachineType;
import dev.awaken.above.net.Panel;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The Clone Vat: once per play-day, a death anywhere wakes you in your bound Vat instead of at your bed, and other
 * modules keep the Satchel / Deep Pack (they ask {@link Above#consumeCloneSave}). The Vat must have grown a clone
 * (300 essence from a reactor in range) since it was last used.
 */
public final class CloneVats {
    private CloneVats() {}

    public static void init() {
        // after every other module's death handling, so a cancelled death never spends the save
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, CloneVats::onDeath);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, CloneVats::onRespawn);
    }

    /** Pure rule: one save per play-day. */
    public static boolean canUse(long usedDay, long today) {
        return usedDay != today;
    }

    /** The player's bound Vat if it still exists (loads its chunk). */
    public static @Nullable MachineBlockEntity boundVat(ServerPlayer player) {
        Optional<GlobalPos> gp = Above.data(player).cloneVat();
        if (gp.isEmpty()) return null;
        ServerLevel level = player.level().getServer().getLevel(gp.get().dimension());
        if (level == null) return null;
        BlockPos pos = gp.get().pos();
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return level.getBlockEntity(pos) instanceof MachineBlockEntity be && be.type() == MachineType.CLONE_VAT ? be : null;
    }

    public static boolean available(ServerPlayer player, long today) {
        AboveData d = Above.data(player);
        if (d.clonePending()) return true;
        if (!canUse(d.cloneUsedDay(), today)) return false;
        MachineBlockEntity vat = boundVat(player);
        return vat != null && vat.cloneReady();
    }

    public static boolean consume(ServerPlayer player, long today) {
        AboveData d = Above.data(player);
        if (d.clonePending()) return true; // same death, asked again
        if (!canUse(d.cloneUsedDay(), today)) return false;
        MachineBlockEntity vat = boundVat(player);
        if (vat == null || !vat.cloneReady()) return false;
        vat.setCloneReady(false);
        d.setCloneUsedDay(today);
        d.setClonePending(true);
        Progression.count(player, Above.CLONE_SAVES, 1);
        DirectorLog.log(player, "clone-vat", "save used on play-day " + today);
        Above.sync(player);
        return true;
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) consume(sp, PlayDays.current(sp.level().getServer()));
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.isEndConquered()) return;
        AboveData d = Above.data(sp);
        if (!d.clonePending()) return;
        d.setClonePending(false);
        Optional<GlobalPos> gp = d.cloneVat();
        ServerLevel level = gp.isPresent() ? sp.level().getServer().getLevel(gp.get().dimension()) : null;
        if (level != null) {
            BlockPos feet = Travel.safeSpot(level, gp.get().pos());
            Travel.teleport(sp, level, feet);
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("You wake in the Clone Vat."))
                    .body(Component.literal("Same you. Mostly. What you carried out of the gate came with you."))
                    .footer(Component.literal("Next clone grows when the Vat has power")).ttl(20 * 8).build());
        }
        Above.sync(sp);
    }

    // ---- panel ----------------------------------------------------------------------------------------------------

    static Panel panel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.CLONE_VAT;
        Panel.Builder b = Machines.base(be, t, true);
        AboveData d = Above.data(player);
        GlobalPos here = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
        boolean mine = d.cloneVat().map(here::equals).orElse(false);
        b.button(mine ? "Bound to you" : "Not bound to you", mine ? "If you die, you wake here." :
                        d.cloneVat().isPresent() ? "You are bound to another Vat. Binding here replaces it." : "Bind it and it will catch you.",
                mine ? Machines.GOOD : Machines.AMBER, "bind", 0, mine ? "Bound" : "Bind to me", !mine);
        b.text("Clone", be.cloneReady() ? "Grown and waiting." : "Growing. Needs " + MachineBlockEntity.CLONE_COST + " essence from a reactor in range.",
                be.cloneReady() ? Machines.GOOD : Machines.WARN);
        long today = PlayDays.current(player.level().getServer());
        b.text("Today's save", canUse(d.cloneUsedDay(), today) ? "Available" : "Used this play-day. Back tomorrow.",
                canUse(d.cloneUsedDay(), today) ? Machines.GOOD : Machines.LABEL);
        return b.footer(t.blurb).build();
    }

    static void action(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        if (!action.equals("bind")) return;
        Above.data(player).setCloneVat(Optional.of(GlobalPos.of(be.getLevel().dimension(), be.getBlockPos().immutable())));
        be.setOwner(player.getUUID());
        Above.sync(player);
        Signal.toast(player, Component.literal("Clone Vat bound"));
    }
}
