package dev.awaken.holo.band;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.HoloBand;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * The Holo band as a thing you wear (SPEC §16.4, "The Registry ceremony"; Euan, 2026-10-26: "you actually have to open
 * your inventory and put the Holo band on"). The band is an item ({@code awaken:holo_band}); the wrist is one slot
 * beside your figure in the inventory, kept on the player ({@link BandState}), never in the inventory menu itself: a
 * real slot there would need a mixin into {@code InventoryMenu} (26.3 and NeoForge have no API to add one), so the
 * client draws the slot and sends its clicks here ({@link BandNet}), and this side checks and moves the stack between
 * the menu's carried stack, the inventory and the wrist. Off the wrist the Holo is dark (no K menu, no projected look,
 * no wrist); {@link HoloBand} is how other modules ask. The band never drops on death and never despawns or burns.
 */
public final class BandModule {
    private BandModule() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    public static final DeferredItem<Item> HOLO_BAND = ITEMS.registerSimpleItem("holo_band",
            p -> p.stacksTo(1).rarity(Rarity.RARE).fireResistant());

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<BandState>> WRIST = ATTACHMENTS.register("wrist", () -> AttachmentType
            .builder(BandState::new)
            .serialize(BandState.CODEC.fieldOf("wrist"), s -> s.fitted || !s.kept.isEmpty())
            .copyOnDeath()
            .sync((holder, to) -> true, BandState.STREAM_CODEC)
            .build());

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, BandNet::register);
        var bus = NeoForge.EVENT_BUS;
        // after everything else has added its drops: the bands come off the pile and wait for the respawn
        bus.addListener(EventPriority.LOWEST, LivingDropsEvent.class, BandModule::onDrops);
        // before the opening's own login (LOW): a player who never had the slot (an old save, a classic world) finds
        // their band in it, worn; the opening takes it away again for a Runner who hasn't been to the Registry yet
        bus.addListener(EventPriority.HIGH, PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && !state(p).fitted) wear(p);
        });
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) handBack(p);
        });
        bus.addListener(EntityJoinLevelEvent.class, e -> {
            // a dropped or thrown band stays where it fell (no despawn; the item itself is fire resistant)
            if (!e.getLevel().isClientSide() && e.getEntity() instanceof ItemEntity ie && isBand(ie.getItem())) ie.setUnlimitedLifetime();
        });
        HoloBand.install(new HoloBand.Access() {
            @Override public boolean worn(Player p) {
                return state(p).worn();
            }
            @Override public boolean fitted(Player p) {
                return state(p).fitted;
            }
            @Override public void takeAway(ServerPlayer p) {
                BandModule.takeAway(p);
            }
            @Override public void wear(ServerPlayer p) {
                BandModule.wear(p);
            }
            @Override public boolean give(ServerPlayer p) {
                return BandModule.give(p);
            }
            @Override public boolean has(ServerPlayer p) {
                return BandModule.has(p);
            }
        });
        commands();
        BandTests.register();
    }

    public static boolean isBand(ItemStack s) {
        return !s.isEmpty() && s.is(HOLO_BAND.get());
    }

    /** The player's wrist (an empty, unfitted one if they never had the slot: the Holo is on). */
    public static BandState state(Player p) {
        return p.getExistingData(WRIST).orElseGet(BandState::new);
    }

    private static BandState own(Player p) {
        return p.getData(WRIST);
    }

    // ---- what the opening and commands do ---------------------------------------------------------------------------

    /** The Holo goes dark: the wrist slot is fitted and empty, and no band is left on the player (the opening hands
     *  one over at the Registry). */
    static void takeAway(ServerPlayer p) {
        BandState s = own(p);
        boolean was = s.worn();
        s.fitted = true;
        s.wrist = ItemStack.EMPTY;
        s.kept.clear();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isBand(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        if (isBand(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        changed(p, was, "taken away");
    }

    /** A band on the wrist now: the one in the inventory if there is one, else a new one. */
    static void wear(ServerPlayer p) {
        BandState s = own(p);
        boolean was = s.worn();
        if (s.fitted && !s.wrist.isEmpty()) return;
        ItemStack band = ItemStack.EMPTY;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && band.isEmpty(); i++) {
            if (isBand(inv.getItem(i))) band = inv.removeItemNoUpdate(i);
        }
        if (band.isEmpty() && isBand(p.containerMenu.getCarried())) {
            band = p.containerMenu.getCarried();
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (band.isEmpty()) band = new ItemStack(HOLO_BAND.get());
        s.fitted = true;
        s.wrist = band.copyWithCount(1);
        p.containerMenu.broadcastChanges();
        changed(p, was, "worn");
    }

    /** A band into the player's hands: the selected hotbar slot if it's free, else anywhere, else at their feet. */
    static boolean give(ServerPlayer p) {
        BandState s = own(p);
        boolean was = s.worn();
        s.fitted = true;
        ItemStack band = new ItemStack(HOLO_BAND.get());
        Inventory inv = p.getInventory();
        if (inv.getSelectedItem().isEmpty()) inv.setItem(inv.getSelectedSlot(), band);
        else if (!inv.add(band)) p.drop(band, false, net.minecraft.util.Prediction.SERVER_ONLY);
        p.containerMenu.broadcastChanges();
        changed(p, was, "handed a band");
        return true;
    }

    static boolean has(ServerPlayer p) {
        BandState s = state(p);
        if (!s.wrist.isEmpty() || !s.kept.isEmpty()) return true;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isBand(inv.getItem(i))) return true;
        return isBand(p.containerMenu.getCarried());
    }

    // ---- the wrist slot's clicks (from the client's inventory screen) ---------------------------------------------

    /** What a click on the wrist did (tests and the log). */
    enum Result { WORN, TAKEN_OFF, SWAPPED, REFUSED }

    /**
     * A click from the inventory screen: on the wrist slot itself (with the stack the cursor carries, as a slot click
     * would), the wrist slot with shift (off, into the inventory), or a shift-click on menu slot {@code slot} holding a
     * band (onto the wrist). Only the player's own inventory menu counts.
     */
    static Result click(ServerPlayer p, int action, int slot) {
        if (p.isSpectator() || p.containerMenu != p.inventoryMenu) return Result.REFUSED;
        AbstractContainerMenu menu = p.containerMenu;
        BandState s = own(p);
        boolean was = s.worn();
        Result r;
        switch (action) {
            case BandNet.CLICK -> {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty() && !isBand(carried)) return Result.REFUSED;
                if (carried.isEmpty() && s.wrist.isEmpty()) return Result.REFUSED;
                // the cursor's band goes on, the one that was on comes back on the cursor (bands stack to one)
                ItemStack off = s.wrist;
                s.wrist = carried.isEmpty() ? ItemStack.EMPTY : carried.split(1);
                if (carried.isEmpty()) menu.setCarried(off);
                r = s.wrist.isEmpty() ? Result.TAKEN_OFF : off.isEmpty() ? Result.WORN : Result.SWAPPED;
            }
            case BandNet.SHIFT_OFF -> {
                if (s.wrist.isEmpty()) return Result.REFUSED;
                ItemStack off = s.wrist;
                if (!p.getInventory().add(off)) return Result.REFUSED; // nowhere to put it: it stays on
                s.wrist = ItemStack.EMPTY;
                r = Result.TAKEN_OFF;
            }
            case BandNet.SHIFT_ON -> {
                if (!s.wrist.isEmpty() || slot < 0 || slot >= menu.slots.size()) return Result.REFUSED;
                Slot from = menu.slots.get(slot);
                if (!isBand(from.getItem()) || !from.mayPickup(p)) return Result.REFUSED;
                s.wrist = from.remove(1);
                from.setChanged();
                r = Result.WORN;
            }
            default -> {
                return Result.REFUSED;
            }
        }
        s.fitted = true;
        menu.broadcastChanges();
        changed(p, was, "wrist click " + r);
        return r;
    }

    private static void changed(ServerPlayer p, boolean was, String why) {
        p.syncData(WRIST);
        boolean now = state(p).worn();
        if (was != now) {
            DirectorLog.log(p, "holo", "band " + (now ? "on" : "off") + " (" + why + ")");
            Awaken.LOG.info("[holo] {}: band {} ({})", p.getPlainTextName(), now ? "on" : "off", why);
            HoloBand.fire(p, now);
        }
    }

    // ---- death -------------------------------------------------------------------------------------------------------

    /** Bands in the inventory at a death don't drop: they wait on the player (the worn one never left the wrist). */
    private static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        BandState s = null;
        for (Iterator<ItemEntity> it = e.getDrops().iterator(); it.hasNext(); ) {
            ItemEntity ie = it.next();
            if (!isBand(ie.getItem())) continue;
            if (s == null) s = own(p);
            s.kept.add(ie.getItem().copy());
            it.remove();
        }
    }

    /** After a respawn: the kept bands go back into the inventory. */
    private static void handBack(ServerPlayer p) {
        BandState s = state(p);
        if (s.kept.isEmpty()) return;
        for (ItemStack b : s.kept) if (!p.getInventory().add(b)) p.drop(b, false, net.minecraft.util.Prediction.SERVER_ONLY);
        s.kept.clear();
        p.syncData(WRIST);
    }

    // ---- commands ----------------------------------------------------------------------------------------------------

    private static void commands() {
        AwakenCommands.add((root, ctx) -> {
            var band = Commands.literal("band");
            band.then(Commands.literal("status").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                BandState s = state(p);
                c.getSource().sendSuccess(() -> Component.literal("Holo band: " + (s.worn() ? "on" : "dark") + ", wrist slot "
                        + (s.fitted ? (s.wrist.isEmpty() ? "empty" : "worn") : "not fitted (always on)") + ", band on you: " + has(p)), false);
                return 1;
            }));
            band.then(Commands.literal("wear").requires(AwakenCommands.admin()).executes(c -> {
                wear(c.getSource().getPlayerOrException());
                return 1;
            }));
            band.then(Commands.literal("dark").requires(AwakenCommands.admin()).executes(c -> {
                takeAway(c.getSource().getPlayerOrException());
                return 1;
            }));
            band.then(Commands.literal("give").requires(AwakenCommands.admin()).executes(c -> {
                give(c.getSource().getPlayerOrException());
                return 1;
            }));
            root.then(band);
        });
    }
}
