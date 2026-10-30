package dev.awaken.above;

import dev.awaken.core.api.CoreApi;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Supplier;

/**
 * Every Precursor machine the player can scan and build (SPEC section 10). The order is stable: the Fabricator's
 * button ids and the broken-machine block state use it. Parts are what the Fabricator screen shows; there is no other
 * recipe for a machine.
 */
public enum MachineType implements StringRepresentable {
    REACTOR("essence_reactor", "Essence Reactor", 18,
            "Burns essence shards and cells. Powers every machine within 32 blocks. No cables.",
            part(() -> AboveItems.SCRAP.get(), 6), part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> AboveItems.VOID_GLASS.get(), 1), part(() -> CoreApi.ESSENCE_SHARD.get(), 4)),
    GATE_SCANNER("gate_scanner", "Gate Scanner", 14,
            "Lists every gate within 2,000 blocks with its rank, omen and when it breaks. Marks them on your Gate Map.",
            part(() -> AboveItems.SCRAP.get(), 4), part(() -> AboveItems.CIRCUIT.get(), 3), part(() -> Items.COMPASS, 1), part(() -> Items.AMETHYST_SHARD, 2)),
    GATE_STABILISER("gate_stabiliser", "Gate Stabiliser", 10,
            "Holds a gate you have scanned open longer. Each charge delays its break by up to three play-days.",
            part(() -> AboveItems.SCRAP.get(), 6), part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> AboveItems.CELL.get(), 1), part(() -> Items.CRYING_OBSIDIAN, 4)),
    WAYPOINT_PYLON("waypoint_pylon", "Waypoint Pylon", 12,
            "Fast travel between your pylons, your Hearth and the ruins you have scanned. Costs essence.",
            part(() -> AboveItems.SCRAP.get(), 4), part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> Items.ENDER_PEARL, 2), part(() -> AboveItems.VOID_GLASS.get(), 1)),
    DEEP_LIFT("deep_lift", "Deep Lift", 8,
            "Rides to any stratum where you lit a Waystone. No Strain from climbing.",
            part(() -> AboveItems.SCRAP.get(), 8), part(() -> AboveItems.CIRCUIT.get(), 4), part(() -> AboveItems.ALLOY.get(), 2), part(() -> Items.IRON_CHAIN, 6), part(() -> AboveItems.CELL.get(), 1)),
    FRAME_FORGE("frame_forge", "Frame Forge", 8,
            "Builds exosuit Frames and their modules, and sockets modules into Frame pieces.",
            part(() -> AboveItems.SCRAP.get(), 6), part(() -> AboveItems.CIRCUIT.get(), 3), part(() -> AboveItems.ALLOY.get(), 2), part(() -> Items.SMITHING_TABLE, 1)),
    CLONE_VAT("clone_vat", "Clone Vat", 6,
            "Once per play-day, when you die anywhere, you wake here instead. Your Satchel and Deep Pack come with you.",
            part(() -> AboveItems.SCRAP.get(), 6), part(() -> AboveItems.CIRCUIT.get(), 4), part(() -> AboveItems.VOID_GLASS.get(), 4), part(() -> AboveItems.CELL.get(), 2), part(() -> CoreApi.LORD_HEART.get(), 1)),
    DREAM_RECORDER("dream_recorder", "Dream Recorder", 8,
            "Place it by a bed. Replays a dream you recorded.",
            part(() -> AboveItems.SCRAP.get(), 4), part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> Items.AMETHYST_SHARD, 4), part(() -> Items.PHANTOM_MEMBRANE, 2)),
    SIGNAL_RELAY("signal_relay", "Signal Relay", 8,
            "Near your Hearth it listens: once a play-day it picks up the nearest bell you haven't found.",
            part(() -> AboveItems.SCRAP.get(), 4), part(() -> AboveItems.CIRCUIT.get(), 3), part(() -> Items.LIGHTNING_ROD.weathering().unaffected(), 1), part(() -> Items.COPPER_INGOT, 4)),
    RECALIBRATOR("recalibrator", "Recalibrator", 7,
            "Refunds every stat point you spent so you can place them again. Costs essence.",
            part(() -> AboveItems.SCRAP.get(), 6), part(() -> AboveItems.CIRCUIT.get(), 4), part(() -> AboveItems.CELL.get(), 1), part(() -> Items.DIAMOND, 2)),
    OBSERVATORY("observatory", "Observatory", 6,
            "Surveys the sky: shows sky ruins nearby, and something in orbit if your eyes are ready.",
            part(() -> AboveItems.SCRAP.get(), 6), part(() -> AboveItems.CIRCUIT.get(), 3), part(() -> Items.SPYGLASS, 1), part(() -> AboveItems.VOID_GLASS.get(), 4)),
    LAUNCH_RING("launch_ring", "Launch Ring", 4,
            "The core of a ring of sixteen frames. With enough essence it sends you up to the Halo.",
            part(() -> AboveItems.ALLOY.get(), 8), part(() -> AboveItems.CIRCUIT.get(), 6), part(() -> CoreApi.LORD_HEART.get(), 1), part(() -> AboveItems.CELL.get(), 4));

    /** One visible part of a blueprint. */
    public record Part(Supplier<? extends Item> item, int count) {
        public ItemStack stack() {
            return new ItemStack(item.get(), count);
        }
    }

    public final String id;
    public final String displayName;
    /** Relative chance this machine appears broken in a ruin. */
    public final int scanWeight;
    public final String blurb;
    private final Part[] parts;

    MachineType(String id, String displayName, int scanWeight, String blurb, Part... parts) {
        this.id = id;
        this.displayName = displayName;
        this.scanWeight = scanWeight;
        this.blurb = blurb;
        this.parts = parts;
    }

    public List<Part> parts() {
        return List.of(parts);
    }

    private static Part part(Supplier<? extends Item> item, int count) {
        return new Part(item, count);
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static MachineType byId(String id) {
        for (MachineType t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    public static MachineType byOrdinal(int i) {
        MachineType[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
