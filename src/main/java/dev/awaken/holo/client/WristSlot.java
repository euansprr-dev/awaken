package dev.awaken.holo.client;

import dev.awaken.Awaken;
import dev.awaken.core.api.HoloBand;
import dev.awaken.holo.band.BandModule;
import dev.awaken.holo.band.BandNet;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * The wrist slot (SPEC §16.4, "The Registry ceremony"): one slot beside your figure in the survival inventory, just
 * above the off hand, for the Holo band. It isn't one of the menu's slots (that would take a mixin into InventoryMenu):
 * this draws it, in vanilla's look or the holo inventory's well, and sends its clicks to the server, which moves the
 * stack ({@code BandModule.click}). A click with the band on the cursor puts it on, a click on a worn band takes it onto
 * the cursor, shift-click moves it between the wrist and the inventory. Shown only to players who have the slot.
 */
public final class WristSlot {
    private WristSlot() {}

    /** Where it sits in the inventory image (vanilla's off-hand slot is at 77, 62). */
    public static final int X = 77, Y = 44;
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
    private static final Identifier EMPTY = Awaken.id("container/slot/wrist");
    /** When the band last went on (client ticks of the screen's life: a glow round the well, the boot's first light). */
    private static long wornAtNanos;
    private static boolean wasWorn;

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ScreenEvent.Render.Foreground.class, WristSlot::draw);
        bus.addListener(EventPriority.HIGH, ScreenEvent.MouseButtonPressed.Pre.class, WristSlot::click);
    }

    private static boolean shown(Object screen) {
        LocalPlayer p = Minecraft.getInstance().player;
        return screen instanceof InventoryScreen && p != null && HoloBand.fitted(p) && !p.hasInfiniteMaterials();
    }

    private static boolean over(InventoryScreen s, double mx, double my) {
        int x = s.getLeftPos() + X, y = s.getTopPos() + Y;
        return mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    private static void draw(ScreenEvent.Render.Foreground e) {
        if (!shown(e.getScreen())) return;
        InventoryScreen s = (InventoryScreen) e.getScreen();
        Minecraft mc = Minecraft.getInstance();
        GuiGraphicsExtractor g = e.getGuiGraphics();
        ItemStack worn = BandModule.state(mc.player).wrist();
        boolean on = !worn.isEmpty();
        if (on && !wasWorn) wornAtNanos = System.nanoTime();
        wasWorn = on;
        int x = s.getLeftPos() + X, y = s.getTopPos() + Y;
        boolean hover = over(s, e.getMouseX(), e.getMouseY());
        float since = (System.nanoTime() - wornAtNanos) / 1e9f;
        // the band in hand or on the cursor and the wrist bare: the slot breathes, so you know where it goes
        float call = !on && (BandModule.isBand(s.getMenu().getCarried()) || mc.player.getInventory().hasAnyMatching(BandModule::isBand))
                ? 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1e9 * Math.PI * 1.6) : 0f;
        if (s instanceof HoloInventoryScreen h) {
            float k = h.faceAlpha();
            if (k < 0.5f) return; // the 3D panel is still landing
            dev.awaken.signal.client.UiCanvas c = GuiCanvas.of(g);
            c.fill(x, y, x + 16, y + 16, Glass.a(0x040A16, 0.58f * k));
            c.outline(x - 1, y - 1, 18, 18, Glass.a(Glass.EDGE, (on ? 0.6f : 0.32f) * k));
            c.hLine(x, x + 15, y, Glass.a(Glass.EDGE, 0.12f * k));
            float glow = (hover ? 0.55f : 0f) + (on && since < 1.2f ? 0.9f * (1 - since / 1.2f) : 0f) + 0.6f * call;
            if (glow > 0) HoloGuiLook.INSTANCE.glow(g, x - 1, y - 1, 18, 18, Glass.EDGE, Math.min(1f, glow) * k);
        } else {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x - 1, y - 1, 18, 18);
            if (hover) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, x - 4, y - 4, 24, 24);
            if (on && since < 0.8f) g.fill(x, y, x + 16, y + 16, Glass.a(0x9ED2FF, 0.55f * (1 - since / 0.8f)));
            if (call > 0f) g.fill(x, y, x + 16, y + 16, Glass.a(0x9ED2FF, 0.12f + 0.22f * call));
        }
        if (on) {
            g.item(worn, x, y);
        } else {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, x, y, 16, 16);
        }
        if (hover && !(s instanceof HoloInventoryScreen)) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x - 4, y - 4, 24, 24);
        if (hover && s.getMenu().getCarried().isEmpty()) {
            if (on) g.setTooltipForNextFrame(mc.font, worn, e.getMouseX(), e.getMouseY());
            else g.setTooltipForNextFrame(mc.font, List.of(Component.translatable("gui.awaken.wrist").getVisualOrderText(),
                    Component.translatable("gui.awaken.wrist.empty").withColor(0xA8B4C4).getVisualOrderText()), e.getMouseX(), e.getMouseY());
        }
    }

    // ---- clicks ---------------------------------------------------------------------------------------------------------

    private static void click(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!shown(e.getScreen())) return;
        InventoryScreen s = (InventoryScreen) e.getScreen();
        Minecraft mc = Minecraft.getInstance();
        boolean shift = mc.hasShiftDown();
        if (over(s, e.getMouseX(), e.getMouseY())) {
            ItemStack carried = s.getMenu().getCarried();
            boolean worn = !BandModule.state(mc.player).wrist().isEmpty();
            e.setCanceled(true);
            if (shift && worn) send(BandNet.SHIFT_OFF, -1);
            else if (BandModule.isBand(carried) || carried.isEmpty() && worn) send(BandNet.CLICK, -1);
            return;
        }
        // shift on a band elsewhere in the inventory: onto the wrist (if it's free), not into the hotbar
        Slot over = s.getHoveredSlot();
        if (shift && over != null && BandModule.isBand(over.getItem()) && BandModule.state(mc.player).wrist().isEmpty()) {
            e.setCanceled(true);
            send(BandNet.SHIFT_ON, over.index);
        }
    }

    private static void send(int action, int slot) {
        ClientPacketDistributor.sendToServer(new BandNet.WristClick(action, slot));
        HoloAudio.hover();
    }
}
