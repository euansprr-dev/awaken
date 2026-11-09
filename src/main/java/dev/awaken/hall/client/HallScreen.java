package dev.awaken.hall.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.client.ArmouryCache;
import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Hall of Arms (SPEC §52), where the Armoury's three doors now open: the Wall of every weapon, and any one of them
 * brought close. It draws what the server sends and asks the server to do everything. Doesn't pause the game.
 */
public final class HallScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private final WallView wall = new WallView();
    private final Embers embers = new Embers();
    private @Nullable Stage stage;
    private @Nullable PieceView piece;
    private long last = System.nanoTime();
    private float time;
    private double mouseX, mouseY;
    private float aimX = 854F, aimY = 700F;
    private int cursorCol = 0, cursorTier = 0;
    private boolean cursorShown;
    private int ticks, inventoryHash;

    public HallScreen() {
        super(Component.translatable("screen.awaken.armoury"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        long now = System.nanoTime();
        float dt = Math.min(.1F, (now - last) / 1_000_000_000F);
        last = now;
        time += dt;
        if (stage == null) stage = new Stage(font);
        Stage s = stage;
        HallNet.Wall w = HallClient.wall;
        s.begin(g, width, height);
        if (w != null) frame(s, w, dt, mx, my);
        s.end(width, height);
    }

    private void frame(Stage s, HallNet.Wall w, float dt, int mx, int my) {
        mouseX = mx;
        mouseY = my;
        PieceView p = piece;
        if (p != null && p.advance(dt)) {
            piece = null;
            p = null;
        }
        if (p == null) {
            float sx = s.sx(mx), sy = s.sy(my);
            if (mouseMovedSinceKey) {
                wall.hover = HallLayout.at(sx, sy);
                aimX = sx;
                aimY = sy;
            }
            wall.move(dt, aimX, aimY);
            wall.draw(s, w, 1F, 0F, 0F);
            s.g.nextStratum();
            wall.ui(s, w);
            s.g.nextStratum();
            embers.draw(s, dt, time, 0xFFB56A, 1F);
            return;
        }
        float e = p.eased();
        if (e < 1F) wall.draw(s, w, 1F + 2.2F * e, p.zoomX(), p.zoomY());
        s.g.nextStratum();
        s.fill(0F, 0F, Stage.W, Stage.H, Stage.argb(0x040609, Math.min(1F, e * 1.25F)));
        s.g.nextStratum();
        p.draw(s, w, dt, time, mx, my);
        s.g.nextStratum();
        embers.draw(s, dt, time, Stage.RANK[p.tier], .55F * e);
    }

    /** Until the mouse really moves the lantern stays where it is (a tour's cursor sits in a corner). */
    private boolean mouseMovedSinceKey;

    private void openPiece(int col, int tier) {
        wall.hover = null;
        piece = new PieceView(col, tier, HallLayout.cell(col, tier));
    }

    /** Dev: bring this weapon close, as a click on its niche does. */
    void show(String style, int tier) {
        int col = HallLayout.styleColumn(style);
        if (col >= 0) openPiece(col, Math.clamp(tier, 0, HallNet.ROWS - 1));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || stage == null) return super.mouseClicked(event, doubleClick);
        float x = stage.sx(event.x()), y = stage.sy(event.y());
        HallNet.Wall w = HallClient.wall;
        if (w == null) return true;
        if (piece != null) {
            if (!piece.closing && !piece.click(x, y)) piece.dragging = piece.fly >= 1F;
            return true;
        }
        int f = wall.filterAt(x, y);
        if (f >= 0) {
            wall.filter = f;
            return true;
        }
        int seal = wall.sealAt(w, x, y);
        if (seal >= 0) {
            piece = new PieceView(LordForged.ALL.get(seal), x, y);
            return true;
        }
        HallLayout.Cell c = HallLayout.at(x, y);
        if (c != null) {
            aimX = c.x();
            aimY = c.cy();
            openPiece(c.col(), c.tier());
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (piece != null) {
            piece.dragging = false;
            piece.holdMouse = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (piece != null && piece.dragging && stage != null) {
            piece.drag((float) (dx / stage.k));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void mouseMoved(double x, double y) {
        mouseMovedSinceKey = true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        PieceView p = piece;
        if (p != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                p.closing = true;
                return true;
            }
            if (p.closing) return true;
            switch (key) {
                case InputConstants.KEY_LEFT -> p.step(-1, 0);
                case InputConstants.KEY_RIGHT -> p.step(1, 0);
                case InputConstants.KEY_UP -> p.step(0, 1);
                case InputConstants.KEY_DOWN -> p.step(0, -1);
                case InputConstants.KEY_SPACE -> p.space(true);
                default -> {
                    return p.key(key) || super.keyPressed(event);
                }
            }
            return true;
        }
        switch (key) {
            case InputConstants.KEY_LEFT -> moveCursor(-1, 0);
            case InputConstants.KEY_RIGHT -> moveCursor(1, 0);
            case InputConstants.KEY_UP -> moveCursor(0, 1);
            case InputConstants.KEY_DOWN -> moveCursor(0, -1);
            case InputConstants.KEY_RETURN -> {
                if (cursorShown) openPiece(cursorCol, cursorTier);
                else moveCursor(0, 0);
            }
            case InputConstants.KEY_TAB -> wall.filter = (wall.filter + 1) % 3;
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (piece != null && event.key() == InputConstants.KEY_SPACE) piece.space(false);
        return super.keyReleased(event);
    }

    private void moveCursor(int dc, int dt) {
        if (cursorShown) {
            cursorCol = Math.clamp(cursorCol + dc, 0, HallNet.COLS - 1);
            cursorTier = Math.clamp(cursorTier + dt, 0, HallNet.ROWS - 1);
        }
        cursorShown = true;
        mouseMovedSinceKey = false;
        HallLayout.Cell c = HallLayout.cell(cursorCol, cursorTier);
        wall.hover = c;
        aimX = c.x();
        aimY = c.cy();
    }

    @Override
    public void tick() {
        // what you pick up while the Hall is open shows without closing it
        if (++ticks % 10 != 0) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int hash = 1;
        for (ItemStack it : player.getInventory().getNonEquipmentItems()) hash = 31 * hash + (it.isEmpty() ? 0 : it.getItem().hashCode() * 17 + it.getCount());
        if (hash != inventoryHash) {
            boolean first = ticks == 10;
            inventoryHash = hash;
            if (first) return;
            PieceView p = piece;
            ArmouryCache.ask(ArmsArmoury.REFRESH, p != null ? p.style : "longsword", p != null ? ArmsArmoury.TIERS[p.tier] : "");
        }
    }
}
