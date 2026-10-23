package dev.awaken.descent;

import dev.awaken.core.AwakenNet;
import dev.awaken.descent.block.WaystoneBlock;
import dev.awaken.descent.net.DescentPayloads;
import dev.awaken.descent.player.DeepPacks;
import dev.awaken.descent.player.DescentData;
import dev.awaken.descent.player.Strain;
import dev.awaken.descent.world.Layout;
import dev.awaken.progression.Progression;
import dev.awaken.progression.ProgressionModule;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Waystones: one per camp. Lighting one is a checkpoint (you respawn there after dying in the Descent), counts
 * {@code awaken:waystones_lit} and adds it to fast travel. Resting at one makes the Deep Pack yours.
 */
public final class DescentWaystones {
    private DescentWaystones() {}

    /** Where a travel list was opened, so the answer can be checked (player must still be there). */
    private record Anchor(int depth, BlockPos pos, long time) {}

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();

    public static void use(ServerPlayer player, BlockPos pos, BlockState state, boolean sneaking) {
        int depth = Strata.depthAt(player.level().dimension(), pos);
        if (depth <= 0) {
            player.sendOverlayMessage(Component.literal("This Waystone is far from its stratum. It stays dark."));
            return;
        }
        DescentData d = DescentSync.data(player);
        boolean newlyLit = d.lit.add(depth);
        d.checkpoint = depth;
        if (!state.getValue(WaystoneBlock.LIT)) {
            player.level().setBlock(pos, state.setValue(WaystoneBlock.LIT, true), Block.UPDATE_ALL);
        }
        rest(player);
        if (newlyLit) {
            Progression.count(player, "awaken:waystones_lit");
            Progression.grantLevels(player, 0.1, Progression.XpSource.DESCENT);
            player.level().playSound(null, pos, DescentContent.SND_WAYSTONE_LIGHT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Waystone lit"))
                    .row(Component.literal("Camp"), Component.literal(Strata.label(depth)))
                    .row(Component.literal("Checkpoint"), Component.literal("You return here if you fall in the Descent"))
                    .footer(Component.literal("Use it again to travel between lit Waystones and the Well"))
                    .ttl(20 * 8).build());
        } else {
            player.level().playSound(null, pos, DescentContent.SND_WAYSTONE_REST.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            if (!sneaking) openTravel(player, depth, pos);
        }
        DescentSync.push(player);
    }

    /** Rest by a Waystone: the Deep Pack becomes yours, a little healing, and the checkpoint is set. */
    public static void rest(ServerPlayer player) {
        DeepPacks.bank(player, "You rest by the Waystone.");
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 0, true, true));
    }

    /** Destinations from a place: the Well (if not there) and every lit camp except this one. Pure. */
    public static List<Integer> destinations(Collection<Integer> lit, int here) {
        List<Integer> out = new ArrayList<>();
        if (here != 0) out.add(0);
        else out.add(DescentTravel.RIM_ENTRANCE);
        for (int depth : lit) if (depth != here && depth > 0) out.add(depth);
        out.sort(Integer::compare);
        return out;
    }

    /** Strain (fraction) a trip from here to a destination would add. Pure. */
    public static double strainFor(int here, int destination) {
        int to = destination == DescentTravel.RIM_ENTRANCE ? 1 : destination;
        return here > to ? Strain.PER_STRATUM * (here - to) : 0;
    }

    public static void openTravel(ServerPlayer player, int here, BlockPos anchor) {
        DescentData d = DescentSync.data(player);
        List<DescentPayloads.Destination> list = new ArrayList<>();
        for (int dest : destinations(d.lit, here)) {
            String label = dest == DescentTravel.RIM_ENTRANCE ? Strata.label(1) + " · arrival"
                    : dest == 0 ? "The Well · the surface" : Strata.label(dest) + " · camp";
            list.add(new DescentPayloads.Destination(dest, label, (int) Math.round(strainFor(here, dest) * 100)));
        }
        ANCHORS.put(player.getUUID(), new Anchor(here, anchor.immutable(), player.level().getGameTime()));
        if (AwakenNet.canReceive(player, DescentPayloads.OpenTravel.TYPE)) {
            AwakenNet.send(player, new DescentPayloads.OpenTravel(here, list));
        } else {
            // No client screen (e.g. a vanilla client): show the list as a Signal window instead.
            SignalWindow.Builder w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Waystones"));
            for (var dst : list) w.row(Component.literal(dst.label()), Component.literal(dst.strainPercent() > 0 ? "Strain " + dst.strainPercent() + "%" : ""));
            Signal.send(player, w.footer(Component.literal("/awaken descent travel <depth>")).build());
        }
    }

    /** The client picked a destination. */
    public static void travel(ServerPlayer player, int destination) {
        Anchor a = ANCHORS.remove(player.getUUID());
        if (a == null || player.level().getGameTime() - a.time() > 20 * 120) return;
        if (player.blockPosition().distSqr(a.pos()) > 8 * 8) {
            player.sendOverlayMessage(Component.literal("Stay by the Waystone to travel."));
            return;
        }
        DescentData d = DescentSync.data(player);
        if (destination == DescentTravel.RIM_ENTRANCE) {
            if (a.depth() == 0) DescentTravel.send(player, 1, DescentTravel.Arrival.ENTRANCE, false);
            return;
        }
        if (destination != 0 && !d.lit.contains(destination)) return;
        if (destination == a.depth()) return;
        int here = DescentTravel.depthOf(player);
        DescentTravel.send(player, destination, DescentTravel.Arrival.CAMP, destination < here);
    }

    /** World position of a depth's Waystone. */
    public static BlockPos waystonePos(int depth) {
        return Layout.world(depth, Layout.waystone(Strata.formOf(depth)));
    }

    static double currentStrain(ServerPlayer player) {
        return player.getData(ProgressionModule.STRAIN.get());
    }

    public static void forget(UUID id) {
        ANCHORS.remove(id);
    }
}
