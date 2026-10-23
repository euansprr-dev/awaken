package dev.awaken.above.machine;

import dev.awaken.above.Above;
import dev.awaken.above.AboveData;
import dev.awaken.above.AboveItems;
import dev.awaken.above.AboveSounds;
import dev.awaken.above.MachineType;
import dev.awaken.above.frame.FrameForge;
import dev.awaken.above.net.AboveNet;
import dev.awaken.above.net.Panel;
import dev.awaken.above.power.EssenceCellItem;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.CoreApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Opens machine panels and runs their buttons. Every machine shares one glass screen (see {@link Panel}); the server
 * builds the rows and re-checks everything when a button comes back.
 */
public final class Machines {
    private Machines() {}

    public static final int CYAN = 0x7FC3FF;
    public static final int AMBER = 0xFFCF7A;
    public static final int BRASS = 0xCFA843;
    public static final int WARN = 0xFF8A7A;
    public static final int GOOD = 0x8FF0C0;
    public static final int PALE = 0xD9E4F2;
    public static final int LABEL = 0x8FA7C4;

    public static void open(ServerPlayer player, MachineBlockEntity be) {
        if (!AwakenConfig.ABOVE.get()) {
            player.sendOverlayMessage(Component.literal("The machine is silent. (The Above is turned off in this world.)"));
            return;
        }
        Panel panel = panel(player, be);
        if (panel != null) AwakenNet.send(player, new AboveNet.OpenPanel(panel));
    }

    static Panel panel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = be.type();
        if (t == null) return fabricatorPanel(player, be);
        return switch (t) {
            case REACTOR -> reactorPanel(player, be);
            case GATE_SCANNER -> GateMachines.scannerPanel(player, be);
            case GATE_STABILISER -> GateMachines.stabiliserPanel(player, be);
            case WAYPOINT_PYLON -> TravelMachines.pylonPanel(player, be);
            case DEEP_LIFT -> TravelMachines.liftPanel(player, be);
            case LAUNCH_RING -> LaunchRing.panel(player, be);
            case FRAME_FORGE -> FrameForge.panel(player, be);
            case CLONE_VAT -> CloneVats.panel(player, be);
            case DREAM_RECORDER -> HomeMachines.recorderPanel(player, be);
            case SIGNAL_RELAY -> HomeMachines.relayPanel(player, be);
            case RECALIBRATOR -> HomeMachines.recalibratorPanel(player, be);
            case OBSERVATORY -> HomeMachines.observatoryPanel(player, be);
        };
    }

    /** A panel button came back from the client. */
    public static void action(ServerPlayer player, BlockPos pos, String action, int index) {
        if (!AwakenConfig.ABOVE.get()) return;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 8 * 8) return;
        if (!player.level().isLoaded(pos) || !(player.level().getBlockEntity(pos) instanceof MachineBlockEntity be)) return;
        MachineType t = be.type();
        boolean keepOpen = true;
        if (t == null) {
            if (action.equals("build")) build(player, be, index);
        } else switch (t) {
            case REACTOR -> { if (action.equals("feed")) feedAll(player, be); }
            case GATE_SCANNER -> GateMachines.scannerAction(player, be, action, index);
            case GATE_STABILISER -> GateMachines.stabiliserAction(player, be, action, index);
            case WAYPOINT_PYLON -> keepOpen = !TravelMachines.pylonAction(player, be, action, index);
            case DEEP_LIFT -> keepOpen = !TravelMachines.liftAction(player, be, action, index);
            case LAUNCH_RING -> keepOpen = !LaunchRing.action(player, be, action, index);
            case FRAME_FORGE -> FrameForge.action(player, be, action, index);
            case CLONE_VAT -> CloneVats.action(player, be, action, index);
            case DREAM_RECORDER -> keepOpen = !HomeMachines.recorderAction(player, be, action, index);
            case SIGNAL_RELAY -> HomeMachines.relayAction(player, be, action, index);
            case RECALIBRATOR -> HomeMachines.recalibratorAction(player, be, action, index);
            case OBSERVATORY -> HomeMachines.observatoryAction(player, be, action, index);
        }
        if (keepOpen && !be.isRemoved()) open(player, be);
        else AwakenNet.send(player, new AboveNet.OpenPanel(closed(pos)));
    }

    /** A panel with no rows tells the client to close the screen. */
    static Panel closed(BlockPos pos) {
        return Panel.builder(pos, "", 0).build();
    }

    /** The shared opening of every powered machine. */
    public static Panel.Builder base(MachineBlockEntity be, MachineType t, boolean needsPower) {
        return Panel.builder(be.getBlockPos(), t.displayName, CYAN)
                .subtitle(be.label().isEmpty() ? "Precursor machine" : be.label())
                .power(be.poweredEssence(), be.reactorsInRange(), needsPower);
    }

    public static Panel noPower(MachineBlockEntity be, MachineType t) {
        return base(be, t, true)
                .text("No power", "Build an Essence Reactor within 32 blocks and feed it essence shards.", WARN)
                .footer(t.blurb).build();
    }

    // ---- inventory helpers ----------------------------------------------------------------------------------------

    public static int count(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** Removes {@code n} of {@code item}; cells go emptiest first so charge isn't wasted. Assumes they're there. */
    public static void remove(Player player, Item item, int n) {
        Inventory inv = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) slots.add(i);
        if (item == AboveItems.CELL.get()) slots.sort(Comparator.comparingInt(i -> EssenceCellItem.charge(inv.getItem(i))));
        int left = n;
        for (int slot : slots) {
            if (left <= 0) break;
            ItemStack s = inv.getItem(slot);
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        inv.setChanged();
    }

    public static boolean hasAll(Player player, List<MachineType.Part> parts) {
        if (player.isCreative()) return true;
        for (MachineType.Part p : parts) if (count(player, p.item().get()) < p.count()) return false;
        return true;
    }

    public static void consume(Player player, List<MachineType.Part> parts) {
        if (player.isCreative()) return;
        for (MachineType.Part p : parts) remove(player, p.item().get(), p.count());
    }

    public static List<ItemStack> stacks(List<MachineType.Part> parts) {
        List<ItemStack> out = new ArrayList<>();
        for (MachineType.Part p : parts) out.add(p.stack());
        return out;
    }

    public static List<Integer> have(Player player, List<MachineType.Part> parts) {
        List<Integer> out = new ArrayList<>();
        for (MachineType.Part p : parts) out.add(player.isCreative() ? p.count() : count(player, p.item().get()));
        return out;
    }

    public static void give(Player player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    // ---- Fabricator -----------------------------------------------------------------------------------------------

    static Panel fabricatorPanel(ServerPlayer player, MachineBlockEntity be) {
        AboveData d = Above.data(player);
        Panel.Builder b = Panel.builder(be.getBlockPos(), "Fabricator", BRASS).subtitle("Builds what you have scanned");
        boolean any = false;
        for (MachineType t : MachineType.values()) {
            if (!d.knows(t)) continue;
            any = true;
            boolean ok = hasAll(player, t.parts());
            b.parts(t.displayName, t.blurb, ok ? GOOD : PALE, stacks(t.parts()), have(player, t.parts()), "build", t.ordinal(),
                    "Build", ok);
        }
        if (!any) {
            b.text("No blueprints", "Find a broken machine in a Precursor ruin and read it with a Scanner. It will show up here.", AMBER);
        }
        return b.footer("Parts you carry are counted. Building consumes them.").build();
    }

    static void build(ServerPlayer player, MachineBlockEntity be, int index) {
        MachineType t = MachineType.byOrdinal(index);
        if (t == null || !Above.knows(player, t)) return;
        if (!hasAll(player, t.parts())) {
            player.sendOverlayMessage(Component.literal("Missing parts for the " + t.displayName));
            return;
        }
        consume(player, t.parts());
        give(player, new ItemStack(AboveItems.machine(t)));
        player.level().playSound(null, be.getBlockPos(), AboveSounds.BUILD.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        Above.recordBuilt(player, t);
        player.sendOverlayMessage(Component.literal("Built: " + t.displayName));
    }

    // ---- Essence Reactor ------------------------------------------------------------------------------------------

    static Panel reactorPanel(ServerPlayer player, MachineBlockEntity be) {
        int shards = count(player, CoreApi.ESSENCE_SHARD.get());
        int space = (MachineBlockEntity.REACTOR_CAPACITY - be.essence()) / EssenceCellItem.PER_SHARD;
        return Panel.builder(be.getBlockPos(), "Essence Reactor", AMBER)
                .subtitle("Powers every machine within 32 blocks")
                .text("Stored", String.format("%,d / %,d essence", be.essence(), MachineBlockEntity.REACTOR_CAPACITY), be.essence() > 0 ? GOOD : WARN)
                .button("Feed shards", "You carry " + shards + " essence shard" + (shards == 1 ? "" : "s") + ". Each is 100 essence.",
                        PALE, "feed", 0, "Feed all", shards > 0 && space > 0)
                .text("Cells", "Use a cell on the reactor to empty it in. Sneak-use to fill the cell from the reactor.", LABEL)
                .footer("No cables. No pipes. Machines in range just draw from it.").build();
    }

    static void feedAll(ServerPlayer player, MachineBlockEntity be) {
        int shards = count(player, CoreApi.ESSENCE_SHARD.get());
        int space = (MachineBlockEntity.REACTOR_CAPACITY - be.essence()) / EssenceCellItem.PER_SHARD;
        int n = Math.min(shards, space);
        if (n <= 0) return;
        remove(player, CoreApi.ESSENCE_SHARD.get(), n);
        be.addEssence(n * EssenceCellItem.PER_SHARD);
        player.level().playSound(null, be.getBlockPos(), AboveSounds.REACTOR.get(), SoundSource.BLOCKS, 1.0f, 1.3f);
    }

    /** Right-click with shards or a cell. PASS when the item isn't fuel. */
    public static InteractionResult feedReactor(Player player, MachineBlockEntity be, ItemStack stack) {
        boolean shard = stack.is(CoreApi.ESSENCE_SHARD.get());
        boolean cell = EssenceCellItem.isCell(stack);
        if (!shard && !cell) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (shard) {
            int space = (MachineBlockEntity.REACTOR_CAPACITY - be.essence()) / EssenceCellItem.PER_SHARD;
            int n = Math.min(stack.getCount(), space);
            if (n <= 0) {
                sp.sendOverlayMessage(Component.literal("The reactor is full"));
                return InteractionResult.FAIL;
            }
            be.addEssence(n * EssenceCellItem.PER_SHARD);
            if (!sp.isCreative()) stack.shrink(n);
        } else if (sp.isShiftKeyDown()) {
            int moved = EssenceCellItem.fill(stack, be.takeEssence(EssenceCellItem.CAPACITY - EssenceCellItem.charge(stack)));
            if (moved <= 0) sp.sendOverlayMessage(Component.literal("Nothing to draw"));
        } else {
            int space = MachineBlockEntity.REACTOR_CAPACITY - be.essence();
            int moved = be.addEssence(EssenceCellItem.drain(stack, space));
            if (moved <= 0) sp.sendOverlayMessage(Component.literal("The cell is empty"));
        }
        sp.level().playSound(null, be.getBlockPos(), AboveSounds.REACTOR.get(), SoundSource.BLOCKS, 0.9f, 1.4f);
        sp.sendOverlayMessage(Component.literal(String.format("Reactor %,d / %,d essence", be.essence(), MachineBlockEntity.REACTOR_CAPACITY)));
        return InteractionResult.SUCCESS;
    }
}
