package dev.awaken.holo.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.holo.ui.HoloTimeline;
import dev.awaken.holo.ui.HoverTick;
import dev.awaken.holo.ui.InventoryWells;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * The inventory as a holo panel (docs/modules/holo.md "The inventory", proposal §7). Underneath it is vanilla's
 * {@link InventoryScreen} (swapped in on {@code ScreenEvent.Opening}), so every click, drag, shift-click, double-click,
 * number key, the recipe book, the creative switch and every other mod's container hook behave exactly as vanilla; only
 * the drawing changes:
 * <ul>
 *   <li>pressing E raises the wrist and a 3D panel grows out of its band along the beams, unfolding (the K menu's
 *       choreography at {@link HoloTimeline#INVENTORY_SPEED}: open in 180 ms), and lands exactly on this screen's
 *       rectangle, where this flat face takes over in a crossfade ({@link HoloTimeline#landed()}); closing hands back to
 *       the 3D panel, which folds into the band in 100 ms;</li>
 *   <li>the flat face is the panel's own glass ({@link HoloGuiLook#panel}), wells instead of printed slot squares, your
 *       figure on a projected floor, the title as light, the recipe button as a glass key ({@link HoloBookButton});</li>
 *   <li>no dim or blur: the world stays in view and Lens dims it through {@code UiHooks.focus}.</li>
 * </ul>
 * Clicks work from the first frame: the slots are where they always are, whatever is drawn.
 */
final class HoloInventoryScreen extends InventoryScreen {
    /** A hover id for the recipe button (slot indices are below it). */
    private static final int BOOK = 10_000;

    private final HoloPanel panel;
    private final HoloGraphics face = HoloGraphics.create();
    private final HoverTick hover = new HoverTick();
    /** InventoryScreen keeps the cursor privately for the figure; this screen draws the figure itself. */
    private float mouseX, mouseY;
    private long lastNanos;
    private @Nullable HoloBookButton book;

    HoloInventoryScreen(Player player) {
        super(player);
        panel = HoloClient.inventoryPanel();
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft.gui.screen() != this) return; // InventoryScreen.init sent a creative player to the creative screen
        restyleRecipeButton();
        hover.reset();
        HoloClient.openInventoryPanel(leftPos, topPos, imageWidth, imageHeight);
    }

    /** Vanilla's recipe button stays the one that is pressed; a glass key draws and takes clicks in its place. */
    private void restyleRecipeButton() {
        for (GuiEventListener l : children()) {
            if (l instanceof ImageButton vanilla) {
                removeWidget(vanilla);
                book = addRenderableWidget(new HoloBookButton(vanilla, this));
                return;
            }
        }
    }

    // ---- how far along: the 3D panel first, then this face -------------------------------------------------------

    /** Whether the 3D panel plays the unfold (its pipelines compiled, motion not reduced); else this face fades in. */
    private static boolean threeD() {
        return HoloPipelines.ok() && !HoloConfig.reducedMotion;
    }

    /** This face's opacity: up as the 3D panel lands (or a plain fade in place without it). */
    float faceAlpha() {
        HoloTimeline t = panel.timeline;
        if (!t.active()) return 1f;
        return threeD() ? t.landed() : t.glass(true);
    }

    /** Items, your figure and the title can't fade: they switch from the 3D panel to this face halfway through. */
    private boolean contentShown() {
        return faceAlpha() >= 0.5f;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        mouseX = mx;
        mouseY = my;
        panel.landOn(leftPos, topPos); // the recipe book moves the inventory
        if (panel.fade > 0f || !panel.timeline.fullyOpen()) recordFace();
        super.extractRenderState(g, mx, my, a);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // no dim and no blur (Screen's in-game background): the world stays in view, and Lens dims it
        minecraft.gui.hud.extractDeferredSubtitles();
        float k = faceAlpha();
        Slot over = slotAt(mx, my);
        tick(over, mx, my);
        HoloGuiLook look = HoloGuiLook.INSTANCE;
        look.panel(g, leftPos, topPos, imageWidth, imageHeight, k);
        g.pose().pushMatrix();
        g.pose().translate(leftPos, topPos);
        InventoryLook.furniture(GuiCanvas.of(g), menu, minecraft.player.getInventory(), k);
        g.pose().popMatrix();
        // light under the wells that matter: the cursor's, the hand's, a result waiting to be taken
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            InventoryWells.Kind kind = InventoryWells.kind(s);
            float glow = s == over ? 0.55f : InventoryWells.selected(s, minecraft.player.getInventory()) ? 0.25f
                    : kind == InventoryWells.Kind.RESULT && s.hasItem() ? 0.35f : 0f;
            if (glow <= 0f) continue;
            int n = InventoryWells.size(kind);
            look.glow(g, leftPos + InventoryWells.x(s, kind), topPos + InventoryWells.y(s, kind), n, n, Glass.EDGE, glow * k);
        }
        // your figure stands on a projected floor
        look.blob(g, leftPos + (InventoryLook.FIGURE_X0 + InventoryLook.FIGURE_X1) / 2f, topPos + InventoryLook.FIGURE_Y1 - 4f, 17f, 3.5f,
                Glass.EDGE, 0.30f * k);
        if (contentShown()) {
            extractEntityInInventoryFollowsMouse(g, leftPos + InventoryLook.FIGURE_X0, topPos + InventoryLook.FIGURE_Y0,
                    leftPos + InventoryLook.FIGURE_X1, topPos + InventoryLook.FIGURE_Y1, 30, 0.0625F, mouseX, mouseY, minecraft.player);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        if (!contentShown()) return;
        HoloGuiLook.INSTANCE.text(g, font, title.getVisualOrderText(), titleLabelX, titleLabelY, Glass.a(Glass.EDGE, faceAlpha()));
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor g, int mx, int my) {
        if (contentShown()) super.extractSlots(g, mx, my);
    }

    /** The 3D panel's face, recorded while it is on screen (opening) and once more as it folds back (closing). */
    private void recordFace() {
        if (!threeD()) return;
        panel.canvas.begin(imageWidth, imageHeight);
        InventoryLook.snapshot(face.begin(panel.canvas, imageWidth, imageHeight), font, menu, minecraft.player.getInventory(), title);
    }

    // ---- the cursor -----------------------------------------------------------------------------------------------

    /** The slot under the cursor this frame (vanilla's own test, so the glow is never a frame behind). */
    private @Nullable Slot slotAt(double mx, double my) {
        for (Slot s : menu.slots) if (s.isActive() && isHovering(s.x, s.y, 16, 16, mx, my)) return s;
        return null;
    }

    /** A glass tick as the cursor comes onto something: a slot with an item in it, or the recipe key. */
    private void tick(@Nullable Slot over, int mx, int my) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : (now - lastNanos) / 1e6f;
        lastNanos = now;
        int target = over != null && over.hasItem() ? over.index : book != null && book.isMouseOver(mx, my) ? BOOK : HoverTick.NONE;
        if (hover.update(target, dt, !contentShown() || isQuickCrafting)) HoloAudio.hover();
    }

    // ---- autopilot ------------------------------------------------------------------------------------------------

    /** A left click (with shift if asked) on menu slot {@code index}: pressed and released at its centre, through the
     *  screen's own input path, as a player's click would be. */
    String autopilotClick(int index, boolean shift) {
        if (index < 0 || index >= menu.slots.size()) return "no slot " + index;
        Slot s = menu.slots.get(index);
        press(leftPos + s.x + 8, topPos + s.y + 8, shift);
        return "slot " + index + " holds " + s.getItem() + ", carried " + menu.getCarried();
    }

    /** A click on the recipe key (the book opens or closes, the inventory moves). */
    String autopilotBook() {
        if (book == null) return "no recipe key";
        press(book.getX() + book.getWidth() / 2.0, book.getY() + book.getHeight() / 2.0, false);
        return "inventory at " + leftPos + "," + topPos;
    }

    private void press(double x, double y, boolean shift) {
        MouseButtonEvent e = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, shift ? InputConstants.MOD_SHIFT : 0));
        mouseClicked(e, false);
        mouseReleased(e);
    }

    @Override
    public void removed() {
        recordFace(); // the panel folds back holding what the inventory holds now
        HoloClient.closeInventoryPanel();
        super.removed();
    }
}
