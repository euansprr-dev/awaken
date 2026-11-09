package dev.awaken.storage;

import dev.awaken.Awaken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Tidy and Undo on the server (SPEC §56): checked against the open menu, abort before writing, one step of Undo. */
public final class Tidying {
    private Tidying() {}

    public enum Section {
        CONTAINER, BAG;

        static Section of(int b) {
            return b == 1 ? BAG : CONTAINER;
        }
    }

    public enum Result { DONE, ALREADY, EMPTY, REFUSED, STALE, LIMITED, FAILED }

    /** What came of a request; from/to are menu slot numbers. */
    public record Outcome(Result result, int[] from, int[] to) {
        static final int[] NONE = new int[0];

        static Outcome of(Result r) {
            return new Outcome(r, NONE, NONE);
        }
    }

    private record Undo(int containerId, Section section, ItemStack[] before, ItemStack[] after, int[] from, int[] to) {}

    private static final Map<ServerPlayer, Undo> UNDO = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    /** Four a second a player. */
    private static final int MIN_TICKS = 5;

    /** Only menus checked by hand; other mods' screens are left alone. */
    public static boolean offered(AbstractContainerMenu m, Section s) {
        Class<?> c = m.getClass();
        if (s == Section.BAG && c == InventoryMenu.class) return true;
        if (c == ChestMenu.class) return realStore(((ChestMenu) m).getContainer());
        return c == ShulkerBoxMenu.class || c == DispenserMenu.class || c == HopperMenu.class;
    }

    /** A plain ChestMenu over something else is someone's button panel. */
    private static boolean realStore(Container c) {
        return c instanceof BaseContainerBlockEntity || c instanceof CompoundContainer || c instanceof PlayerEnderChestContainer || c instanceof ContainerEntity;
    }

    private static boolean ready(ServerPlayer p) {
        long t = p.level().getGameTime();
        Long last = LAST.get(p);
        if (last != null && t >= last && t - last < MIN_TICKS) return false;
        LAST.put(p, t);
        return true;
    }

    static List<Slot> slots(AbstractContainerMenu m, Section s, Inventory inv) {
        List<Slot> out = new ArrayList<>();
        for (Slot slot : m.slots) {
            boolean mine = slot.container == inv;
            if (s == Section.CONTAINER ? !mine && !(m instanceof InventoryMenu) : mine && slot.getContainerSlot() >= Inventory.getSelectionSize()
                    && slot.getContainerSlot() < Inventory.INVENTORY_SIZE) out.add(slot);
        }
        if (s == Section.BAG) out.sort(Comparator.comparingInt(Slot::getContainerSlot));
        return out;
    }

    private static int cols(AbstractContainerMenu m, Section s) {
        if (s == Section.CONTAINER && m instanceof HopperMenu) return 5;
        if (s == Section.CONTAINER && m instanceof DispenserMenu) return 3;
        return 9;
    }

    private static ItemStack[] read(List<Slot> slots) {
        ItemStack[] out = new ItemStack[slots.size()];
        for (int i = 0; i < out.length; i++) out[i] = slots.get(i).getItem();
        return out;
    }

    private static boolean[] locked(ServerPlayer p, List<Slot> slots, Section s) {
        boolean[] out = new boolean[slots.size()];
        if (s == Section.BAG) for (int i = 0; i < out.length; i++) out[i] = StorageApi.pinned(p, slots.get(i).getContainerSlot());
        return out;
    }

    public static Outcome tidy(ServerPlayer p, int containerId, int stateId, Section section) {
        AbstractContainerMenu m = p.containerMenu;
        if (m.containerId != containerId || !m.stillValid(p) || !offered(m, section)) return Outcome.of(Result.REFUSED);
        if (!ready(p)) return Outcome.of(Result.LIMITED);
        if (m.getStateId() != stateId) {
            m.broadcastFullState();
            return Outcome.of(Result.STALE);
        }
        List<Slot> slots = slots(m, section, p.getInventory());
        if (slots.isEmpty()) return Outcome.of(Result.REFUSED);
        ItemStack[] now = read(slots);
        boolean[] locked = locked(p, slots, section);
        boolean any = false;
        for (int i = 0; i < now.length; i++) any |= !locked[i] && !now[i].isEmpty();
        if (!any) return Outcome.of(Result.EMPTY);
        Tidy.Plan plan = Tidy.plan(now, locked, cols(m, section), slots.getFirst().getMaxStackSize());
        if (plan == null) {
            Awaken.LOG.warn("storage: a Tidy for {} wouldn't add up, nothing moved", p.getGameProfile().name());
            return Outcome.of(Result.FAILED);
        }
        if (!plan.changed()) return Outcome.of(Result.ALREADY);
        for (int i = 0; i < now.length; i++)
            if (!plan.after()[i].isEmpty() && !locked[i] && !slots.get(i).mayPlace(plan.after()[i])) return Outcome.of(Result.FAILED);

        ItemStack[] before = new ItemStack[now.length], after = new ItemStack[now.length];
        for (int i = 0; i < now.length; i++) {
            before[i] = now[i].copy();
            after[i] = plan.after()[i].copy();
        }
        write(slots, now, plan.after());
        m.broadcastChanges();
        int[] from = menuSlots(slots, plan.from()), to = menuSlots(slots, plan.to());
        UNDO.put(p, new Undo(containerId, section, before, after, from, to));
        return new Outcome(Result.DONE, from, to);
    }

    public static Outcome undo(ServerPlayer p, int containerId) {
        Undo u = UNDO.get(p);
        AbstractContainerMenu m = p.containerMenu;
        if (u == null || m.containerId != containerId || u.containerId != containerId || !m.stillValid(p)) return Outcome.of(Result.REFUSED);
        if (!ready(p)) return Outcome.of(Result.LIMITED);
        List<Slot> slots = slots(m, u.section, p.getInventory());
        ItemStack[] now = read(slots);
        if (now.length != u.after.length) return Outcome.of(Result.REFUSED);
        for (int i = 0; i < now.length; i++) {
            if (!ItemStack.matches(now[i], u.after[i])) { // something's changed since: Undo would lose it
                UNDO.remove(p);
                return Outcome.of(Result.STALE);
            }
        }
        write(slots, now, u.before);
        m.broadcastChanges();
        UNDO.remove(p);
        return new Outcome(Result.DONE, u.to, u.from);
    }

    static void closed(ServerPlayer p) {
        UNDO.remove(p);
    }

    private static void write(List<Slot> slots, ItemStack[] now, ItemStack[] after) {
        for (int i = 0; i < now.length; i++) if (!ItemStack.matches(now[i], after[i])) slots.get(i).set(after[i].copy());
    }

    private static int[] menuSlots(List<Slot> slots, int[] section) {
        int[] out = new int[section.length];
        for (int i = 0; i < out.length; i++) out[i] = slots.get(section[i]).index;
        return out;
    }

    static @Nullable BagPins pinToggle(ServerPlayer p, int slot) {
        if (slot < Inventory.getSelectionSize() || slot >= Inventory.INVENTORY_SIZE) return null;
        BagPins next = p.getData(StorageModule.PINS).toggled(slot);
        p.setData(StorageModule.PINS, next);
        return next;
    }
}
