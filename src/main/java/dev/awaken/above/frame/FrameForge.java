package dev.awaken.above.frame;

import dev.awaken.above.AboveItems;
import dev.awaken.above.AboveSounds;
import dev.awaken.above.MachineType;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.above.machine.Machines;
import dev.awaken.above.net.Panel;
import dev.awaken.progression.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.List;
import java.util.function.Supplier;

/**
 * The Frame Forge's screen: build Frame pieces and modules, and socket modules into the Frame pieces you wear.
 * Building costs a little essence; socketing is free.
 */
public final class FrameForge {
    private FrameForge() {}

    public static final int BUILD_COST = 50;
    static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public record Piece(String name, Supplier<? extends Item> item, List<MachineType.Part> parts) {}

    public static final List<Piece> PIECES = List.of(
            new Piece("Frame Helm", () -> AboveItems.FRAME_HELMET.get(), List.of(
                    part(() -> AboveItems.ALLOY.get(), 3), part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> AboveItems.SCRAP.get(), 4))),
            new Piece("Frame Chest", () -> AboveItems.FRAME_CHEST.get(), List.of(
                    part(() -> AboveItems.ALLOY.get(), 5), part(() -> AboveItems.CIRCUIT.get(), 3), part(() -> AboveItems.CELL.get(), 1))),
            new Piece("Frame Legs", () -> AboveItems.FRAME_LEGS.get(), List.of(
                    part(() -> AboveItems.ALLOY.get(), 4), part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> AboveItems.SCRAP.get(), 4))),
            new Piece("Frame Boots", () -> AboveItems.FRAME_BOOTS.get(), List.of(
                    part(() -> AboveItems.ALLOY.get(), 2), part(() -> AboveItems.CIRCUIT.get(), 2), part(() -> AboveItems.SCRAP.get(), 4))));

    private static MachineType.Part part(Supplier<? extends Item> item, int n) {
        return new MachineType.Part(item, n);
    }

    static String slotName(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> "Helm";
            case CHEST -> "Chest";
            case LEGS -> "Legs";
            default -> "Boots";
        };
    }

    public static Panel panel(ServerPlayer player, MachineBlockEntity be) {
        MachineType t = MachineType.FRAME_FORGE;
        int stored = be.poweredEssence();
        if (stored <= 0) return Machines.noPower(be, t);
        Panel.Builder b = Machines.base(be, t, true);
        b.header("Frames");
        for (int i = 0; i < PIECES.size(); i++) {
            Piece p = PIECES.get(i);
            boolean ok = Machines.hasAll(player, p.parts()) && stored >= BUILD_COST;
            b.parts(p.name(), "Exosuit piece with " + (i == 0 ? "1 module slot" : "2 module slots") + " · " + BUILD_COST + " essence",
                    ok ? Machines.GOOD : Machines.PALE, Machines.stacks(p.parts()), Machines.have(player, p.parts()), "frame", i, "Build", ok);
        }
        b.header("Modules");
        for (FrameModule m : FrameModule.values()) {
            boolean ok = Machines.hasAll(player, m.parts()) && stored >= BUILD_COST;
            b.parts(m.displayName, m.description, ok ? Machines.GOOD : Machines.PALE, Machines.stacks(m.parts()), Machines.have(player, m.parts()),
                    "module", m.ordinal(), "Build", ok);
        }
        b.header("Sockets");
        boolean wearing = false;
        for (int s = 0; s < SLOTS.length; s++) {
            ItemStack worn = player.getItemBySlot(SLOTS[s]);
            if (!(worn.getItem() instanceof FrameItem fi)) continue;
            wearing = true;
            List<String> mods = FrameItem.modules(worn);
            int slots = FrameModule.slots(fi.armorType());
            if (mods.isEmpty()) b.text(slotName(SLOTS[s]), "0 / " + slots + " slots used", Machines.LABEL);
            for (int k = 0; k < mods.size(); k++) {
                FrameModule m = FrameModule.byId(mods.get(k));
                b.button(slotName(SLOTS[s]) + " · " + (m == null ? mods.get(k) : m.displayName), (k + 1) + " / " + slots + " slots",
                        Machines.CYAN, "unsocket", s * 4 + k, "Remove", true);
            }
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.getItem() instanceof FrameModuleItem mi && FrameItem.canSocket(worn, mi.module())) {
                    b.button(mi.module().displayName + " → " + slotName(SLOTS[s]), mi.module().description, Machines.PALE,
                            "socket", i * 4 + s, "Socket", true);
                }
            }
        }
        if (!wearing) b.text("No Frame worn", "Wear Frame pieces to socket modules into them.", Machines.AMBER);
        return b.footer("Modules draw essence from the cells you carry.").build();
    }

    public static void action(ServerPlayer player, MachineBlockEntity be, String action, int index) {
        switch (action) {
            case "frame" -> {
                if (index < 0 || index >= PIECES.size()) return;
                Piece p = PIECES.get(index);
                if (!Machines.hasAll(player, p.parts()) || !be.draw(BUILD_COST)) return;
                Machines.consume(player, p.parts());
                Machines.give(player, new ItemStack(p.item().get()));
                built(player, be, p.name());
            }
            case "module" -> {
                FrameModule m = index >= 0 && index < FrameModule.values().length ? FrameModule.values()[index] : null;
                if (m == null || !Machines.hasAll(player, m.parts()) || !be.draw(BUILD_COST)) return;
                Machines.consume(player, m.parts());
                Machines.give(player, new ItemStack(AboveItems.MODULES.get(m).get()));
                built(player, be, m.displayName);
            }
            case "socket" -> {
                int invSlot = index / 4, s = index % 4;
                Inventory inv = player.getInventory();
                if (invSlot < 0 || invSlot >= inv.getContainerSize() || s < 0 || s >= SLOTS.length) return;
                ItemStack mod = inv.getItem(invSlot);
                ItemStack worn = player.getItemBySlot(SLOTS[s]);
                if (!(mod.getItem() instanceof FrameModuleItem mi) || !FrameItem.canSocket(worn, mi.module())) return;
                FrameItem.socket(worn, mi.module());
                mod.shrink(1);
                player.level().playSound(null, be.getBlockPos(), AboveSounds.BUILD.get(), SoundSource.BLOCKS, 0.7f, 1.6f);
                Progression.count(player, "awaken:frame_modules_socketed", 1);
            }
            case "unsocket" -> {
                int s = index / 4, k = index % 4;
                if (s < 0 || s >= SLOTS.length) return;
                ItemStack worn = player.getItemBySlot(SLOTS[s]);
                if (!(worn.getItem() instanceof FrameItem)) return;
                FrameModule m = FrameItem.unsocket(worn, k);
                if (m != null) Machines.give(player, new ItemStack(AboveItems.MODULES.get(m).get()));
            }
            default -> {}
        }
    }

    private static void built(ServerPlayer player, MachineBlockEntity be, String what) {
        player.level().playSound(null, be.getBlockPos(), AboveSounds.BUILD.get(), SoundSource.BLOCKS, 1.0f, 1.2f);
        player.sendOverlayMessage(Component.literal("Forged: " + what));
        Progression.count(player, "awaken:frames_forged", 1);
    }

    /** Armor type of a Frame piece index (0 helm .. 3 boots). */
    public static ArmorType typeOf(int index) {
        return switch (index) {
            case 0 -> ArmorType.HELMET;
            case 1 -> ArmorType.CHESTPLATE;
            case 2 -> ArmorType.LEGGINGS;
            default -> ArmorType.BOOTS;
        };
    }
}
