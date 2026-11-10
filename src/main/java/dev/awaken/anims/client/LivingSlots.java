package dev.awaken.anims.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Living slots (SPEC §64.7), on the game's own container screens and ours: a stack you send somewhere without carrying
 * it (a shift-click, a number key) travels from its slot to the one it lands in, a small "+n" lifts off where it lands,
 * and the stack on your pointer casts a soft shadow that drops away as you pick it up. All of it is drawn over the
 * screen from what the slots already hold: the move itself is vanilla's and has already happened, so no click ever
 * waits. A change of more than six slots in one tick (a Tidy, a sort) is left to whoever made it (SlotMoves).
 */
final class LivingSlots {
    private LivingSlots() {}

    private static final long FLIGHT_NS = 120_000_000L, PLUS_NS = 520_000_000L, LIFT_NS = 140_000_000L;
    private static final int FLIGHTS_MOST = 8;

    private record Flight(ItemStack item, int x0, int y0, int x1, int y1, long start) {}

    private record Plus(int x, int y, int n, long start) {}

    private static @Nullable AbstractContainerScreen<?> screen;
    private static ItemStack[] was = new ItemStack[0];
    private static ItemStack carriedWas = ItemStack.EMPTY;
    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static final List<Plus> PLUSES = new ArrayList<>();
    private static ItemStack[] now_ = new ItemStack[0];
    private static final int[] MOVES = new int[FLIGHTS_MOST * 3];
    private static long liftAt;

    static void init(IEventBus bus) {
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ScreenEvent.Render.Foreground.class, LivingSlots::draw);
    }

    /** The game's own container screens and ours; never the creative screen (its slots are a window onto a list). */
    private static boolean ours(AbstractContainerScreen<?> s) {
        if (s instanceof CreativeModeInventoryScreen) return false;
        String name = s.getClass().getName();
        return name.startsWith("net.minecraft.") || name.startsWith("dev.awaken.");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreen<?> now = mc.gui.screen() instanceof AbstractContainerScreen<?> s && ours(s) ? s : null;
        if (now == null) {
            screen = null;
            FLIGHTS.clear();
            PLUSES.clear();
            return;
        }
        List<Slot> slots = now.getMenu().slots;
        ItemStack carried = now.getMenu().getCarried();
        if (now != screen || was.length != slots.size()) {
            // a new screen: remember what it holds, move nothing
            screen = now;
            was = new ItemStack[slots.size()];
            for (int i = 0; i < was.length; i++) was[i] = slots.get(i).getItem().copy();
            carriedWas = carried.copy();
            FLIGHTS.clear();
            PLUSES.clear();
            return;
        }
        if (carriedWas.isEmpty() && !carried.isEmpty()) liftAt = System.nanoTime();
        if (now_.length != was.length) now_ = new ItemStack[was.length];
        for (int i = 0; i < was.length; i++) now_[i] = slots.get(i).getItem();
        // only what you sent without carrying it: a stack that came from or went to the pointer was in your hand
        int moves = dev.awaken.anims.station.SlotMoves.read(was, now_, !ItemStack.matches(carried, carriedWas), MOVES);
        long t = System.nanoTime();
        for (int m = 0; m < moves && FLIGHTS.size() < FLIGHTS_MOST; m++) {
            Slot from = slots.get(MOVES[m * 3]), to = slots.get(MOVES[m * 3 + 1]);
            if (!from.isActive() || !to.isActive()) continue;
            FLIGHTS.add(new Flight(to.getItem().copyWithCount(1), from.x, from.y, to.x, to.y, t));
            PLUSES.add(new Plus(to.x, to.y, MOVES[m * 3 + 2], t + FLIGHT_NS));
        }
        for (int i = 0; i < was.length; i++) {
            ItemStack b = slots.get(i).getItem();
            if (!ItemStack.matches(was[i], b)) was[i] = b.copy();
        }
        carriedWas = carried.copy();
    }

    private static void draw(ScreenEvent.Render.Foreground e) {
        if (screen == null || e.getScreen() != screen) return;
        GuiGraphicsExtractor g = e.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        int left = dev.awaken.anims.client.station.StationClient.slotLeft(screen), top = dev.awaken.anims.client.station.StationClient.slotTop(screen);
        for (int i = FLIGHTS.size() - 1; i >= 0; i--) {
            Flight f = FLIGHTS.get(i);
            float u = (now - f.start) / (float) FLIGHT_NS;
            if (u >= 1) {
                FLIGHTS.remove(i);
                continue;
            }
            float k = 1 - (1 - u) * (1 - u) * (1 - u);
            int x = Math.round(left + f.x0 + (f.x1 - f.x0) * k), y = Math.round(top + f.y0 + (f.y1 - f.y0) * k - 5F * (float) Math.sin(Math.PI * u));
            g.item(f.item, x, y);
        }
        for (int i = PLUSES.size() - 1; i >= 0; i--) {
            Plus p = PLUSES.get(i);
            if (now < p.start) continue;
            float u = (now - p.start) / (float) PLUS_NS;
            if (u >= 1) {
                PLUSES.remove(i);
                continue;
            }
            int a = Math.round(255 * (1 - u * u));
            String text = "+" + p.n;
            g.text(mc.font, text, left + p.x + 17 - mc.font.width(text), top + p.y - 2 - Math.round(7 * u), (a << 24) | 0xBFE3FF, true);
        }
        // the stack on the pointer: a soft shadow under it, falling away as it is picked up (on a station scene the
        // hand holds it in the world and there is no icon to shadow)
        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.isEmpty() && !dev.awaken.anims.client.station.StationClient.handCarries()) {
            float u = Math.min(1F, (now - liftAt) / (float) LIFT_NS);
            int off = 1 + Math.round(2 * u), x = e.getMouseX() - 8 + off, y = e.getMouseY() - 8 + off + 1;
            g.fill(x + 1, y + 1, x + 15, y + 15, 0x30000000);
            g.fill(x + 2, y + 2, x + 14, y + 14, 0x30000000);
        }
    }
}
