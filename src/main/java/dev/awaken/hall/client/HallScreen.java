package dev.awaken.hall.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.client.ForgeClient;
import dev.awaken.armour.net.ArmourPayloads;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The Hall of Arms (SPEC §52), where the Armoury's doors and the Fitting Stand now open: the Wall of every weapon and the
 * terraces of every suit, and any one of them brought close. It draws what the server sends and asks the server to do
 * everything. Doesn't pause the game.
 */
public final class HallScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    public static final int ARMS = 0, ARMOUR = 1;
    private static final float TABS_Y = 32F, TAB_X = 30F, WING_FADE = .22F;

    private final WallView wall = new WallView();
    private final TerraceView terrace = new TerraceView();
    private final Embers embers = new Embers();
    private final Component armsTab = Stage.blackletter("Arms"), armourTab = Stage.blackletter("Armour");
    private @Nullable Stage stage;
    private @Nullable CloseView close;
    private int wing;
    private float wingFade = 1F;
    /** Whether this opening has already gone to the suit its stand is holding. */
    private boolean heldShown;
    private long last = System.nanoTime();
    private float time;
    private float aimX = 854F, aimY = 430F;
    private int cursorCol, cursorTier;
    private boolean cursorShown;
    private int ticks, inventoryHash;
    /** Until the mouse really moves the lantern stays where it is (a tour's cursor sits in a corner). */
    private boolean mouseMovedSinceKey;
    private float armsRight, armourLeft, armourRight;

    public HallScreen() {
        this(ARMS);
    }

    public HallScreen(int wing) {
        super(Component.translatable("screen.awaken.armoury"));
        this.wing = wing;
        HallSounds.open();
    }

    /** Turns to the other wing, with a moment's dark between. */
    void wing(int to) {
        if (to != wing) HallSounds.rank();
        if (wing == to) return;
        wing = to;
        wingFade = 0F;
        cursorShown = false;
        wall.hover = null;
        terrace.hover = null;
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
        CloseView c = close;
        if (c != null && c.advance(dt)) {
            close = null;
            c = null;
        }
        wingFade = Math.min(1F, wingFade + dt / WING_FADE);
        // a stand that is holding a suit opens on that suit: it is the one being built here (SPEC §44.14). Esc steps back
        // to the terraces to look at the rest.
        if (c == null && !heldShown && wing == ARMOUR && HallClient.suits != null) {
            heldShown = true;
            ArmourSet held = TerraceView.held(HallClient.suits);
            if (held != null) {
                TerraceLayout.Fig f = TerraceLayout.of(held);
                aimX = f.x();
                aimY = f.cy();
                openSuit(f, -1);
                c = close;
            }
        }
        if (c == null) {
            float sx = s.sx(mx), sy = s.sy(my);
            if (mouseMovedSinceKey) {
                if (wing == ARMS) wall.hover = HallLayout.at(sx, sy);
                else terrace.hover = TerraceLayout.at(sx, sy);
                aimX = sx;
                aimY = sy;
            }
            if (wing == ARMS) {
                wall.move(dt, aimX, aimY);
                wall.draw(s, w, 1F, 0F, 0F);
            } else {
                terrace.move(dt, aimX, aimY);
                terrace.draw(s, w, 1F, 0F, 0F);
            }
            s.g.nextStratum();
            tabs(s);
            s.g.nextStratum();
            if (wing == ARMS) wall.ui(s, w);
            else terrace.ui(s, w, sx, sy);
            s.g.nextStratum();
            embers.draw(s, dt, time, 0xFFB56A, 1F);
            if (wingFade < 1F) {
                s.g.nextStratum();
                s.fill(0F, 0F, Stage.W, Stage.H, Stage.argb(0, 1F - wingFade));
            }
            return;
        }
        float e = c.eased();
        if (e < 1F) {
            if (wing == ARMS) wall.draw(s, w, 1F + 2.2F * e, c.zoomX(), c.zoomY());
            else terrace.draw(s, w, 1F + 2.2F * e, c.zoomX(), c.zoomY());
        }
        s.g.nextStratum();
        s.fill(0F, 0F, Stage.W, Stage.H, Stage.argb(0x040609, Math.min(1F, e * 1.25F)));
        s.g.nextStratum();
        c.draw(s, w, dt, time, mx, my);
        s.g.nextStratum();
        embers.draw(s, dt, time, Stage.RANK[c.tier()], .55F * e);
    }

    private void tabs(Stage s) {
        s.title(armsTab, TAB_X, TABS_Y, 2F, wing == ARMS ? Stage.LANTERN : Stage.DIM);
        armsRight = TAB_X + s.titleWidth(armsTab, 2F);
        armourLeft = armsRight + 34F;
        s.title(armourTab, armourLeft, TABS_Y, 2F, wing == ARMOUR ? Stage.LANTERN : Stage.DIM);
        armourRight = armourLeft + s.titleWidth(armourTab, 2F);
    }

    private int tabAt(float x, float y) {
        if (y < TABS_Y - 8F || y > TABS_Y + 64F) return -1;
        if (x >= TAB_X - 8F && x < armsRight + 16F) return ARMS;
        if (x >= armourLeft - 16F && x < armourRight + 8F) return ARMOUR;
        return -1;
    }

    private void openPiece(int col, int tier) {
        HallSounds.reach();
        wall.hover = null;
        close = new PieceView(col, tier, HallLayout.cell(col, tier));
    }

    private void openSuit(TerraceLayout.Fig f, int slot) {
        HallSounds.reach();
        terrace.hover = null;
        close = new SuitView(f, slot, this::openLooks, null);
    }

    void openLooks() {
        HallSounds.reach();
        close = new LooksView(854F, 480F);
    }

    /** Dev: what a click would show. */
    void show(String what, int n) {
        if (what.equals("wing")) {
            wing(n);
            wingFade = 1F;
        } else if (what.equals("looks")) {
            openLooks();
        } else if (what.equals("hover")) {
            // tours can't move the pointer: the n-th suit (or the n-th kind's E-rank niche) as if pointed at
            mouseMovedSinceKey = false;
            if (wing == ARMOUR) {
                TerraceLayout.Fig[] figs = TerraceLayout.figs();
                TerraceLayout.Fig f = figs[Math.floorMod(n, figs.length)];
                terrace.hover = f;
                aimX = f.x();
                aimY = f.cy();
            } else {
                HallLayout.Cell cell = HallLayout.cell(Math.floorMod(n, HallNet.COLS), Math.min(2, HallNet.ROWS - 1));
                wall.hover = cell;
                aimX = cell.x();
                aimY = cell.cy();
            }
        } else if (what.startsWith("suit:")) {
            ArmourSet set = ArmourSet.byId(what.substring(5));
            if (set == null) return;
            wing(ARMOUR);
            wingFade = 1F;
            openSuit(TerraceLayout.of(set), n);
        } else {
            int col = HallLayout.styleColumn(what);
            if (col < 0) return;
            wing(ARMS);
            wingFade = 1F;
            openPiece(col, Math.clamp(n, 0, HallNet.ROWS - 1));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || stage == null) return super.mouseClicked(event, doubleClick);
        float x = stage.sx(event.x()), y = stage.sy(event.y());
        HallNet.Wall w = HallClient.wall;
        if (w == null) return true;
        CloseView c = close;
        if (c != null) {
            if (!c.closing && !c.click(x, y)) c.dragging = c.fly >= 1F;
            return true;
        }
        int tab = tabAt(x, y);
        if (tab >= 0) {
            wing(tab);
            return true;
        }
        if (wing == ARMOUR) {
            clickTerrace(x, y);
            return true;
        }
        int f = wall.filterAt(x, y);
        if (f >= 0) {
            wall.filter = f;
            return true;
        }
        int seal = wall.sealAt(w, x, y);
        if (seal >= 0) {
            close = new PieceView(LordForged.ALL.get(seal), x, y);
            return true;
        }
        HallLayout.Cell cell = HallLayout.at(x, y);
        if (cell != null) {
            aimX = cell.x();
            aimY = cell.cy();
            openPiece(cell.col(), cell.tier());
        }
        return true;
    }

    private void clickTerrace(float x, float y) {
        int f = terrace.filterAt(x, y);
        if (f >= 0) {
            terrace.filter = f;
            return;
        }
        if (terrace.looksAt(x, y)) {
            openLooks();
            return;
        }
        TerraceLayout.Fig fig = TerraceLayout.at(x, y);
        if (fig != null) {
            aimX = fig.x();
            aimY = fig.cy();
            openSuit(fig, -1);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (close != null) {
            close.dragging = false;
            close.holdMouse = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (close != null && close.dragging && stage != null) {
            close.drag((float) (dx / stage.k));
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
        CloseView c = close;
        if (c != null) {
            if (key == dev.awaken.holo.ui.StageKeys.BACK) {
                c.closing = true;
                return true;
            }
            if (c.closing) return true;
            switch (key) {
                case InputConstants.KEY_LEFT -> c.step(-1, 0);
                case InputConstants.KEY_RIGHT -> c.step(1, 0);
                case InputConstants.KEY_UP -> c.step(0, 1);
                case InputConstants.KEY_DOWN -> c.step(0, -1);
                case InputConstants.KEY_SPACE -> c.space(true);
                default -> {
                    return c.key(key) || super.keyPressed(event);
                }
            }
            return true;
        }
        if (key == InputConstants.KEY_L && wing == ARMOUR) {
            openLooks();
            return true;
        }
        switch (key) {
            case InputConstants.KEY_LEFT -> moveCursor(-1, 0);
            case InputConstants.KEY_RIGHT -> moveCursor(1, 0);
            case InputConstants.KEY_UP -> moveCursor(0, 1);
            case InputConstants.KEY_DOWN -> moveCursor(0, -1);
            case InputConstants.KEY_RETURN -> enter();
            case dev.awaken.holo.ui.StageKeys.NEXT -> {
                if (wing == ARMS) wall.filter = (wall.filter + 1) % 3;
                else terrace.filter = (terrace.filter + 1) % 3;
            }
            case dev.awaken.holo.ui.StageKeys.BESIDE_BACK, dev.awaken.holo.ui.StageKeys.BESIDE_ON -> wing(wing == ARMS ? ARMOUR : ARMS);
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    private void enter() {
        if (!cursorShown) {
            moveCursor(0, 0);
            return;
        }
        if (wing == ARMS) {
            openPiece(cursorCol, cursorTier);
        } else {
            ArmourSet set = TerraceLayout.set(Math.min(cursorCol, cursorTier == 0 ? 3 : 4), cursorTier);
            if (set != null) openSuit(TerraceLayout.of(set), -1);
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (close != null && event.key() == InputConstants.KEY_SPACE) close.space(false);
        return super.keyReleased(event);
    }

    private void moveCursor(int dc, int dt) {
        int cols = wing == ARMS ? HallNet.COLS : 5;
        if (cursorShown) {
            cursorCol = Math.clamp(cursorCol + dc, 0, cols - 1);
            cursorTier = Math.clamp(cursorTier + dt, 0, HallNet.ROWS - 1);
        }
        cursorCol = Math.min(cursorCol, cols - 1);
        cursorShown = true;
        mouseMovedSinceKey = false;
        if (wing == ARMS) {
            HallLayout.Cell c = HallLayout.cell(cursorCol, cursorTier);
            wall.hover = c;
            aimX = c.x();
            aimY = c.cy();
        } else {
            if (cursorTier == 0) cursorCol = Math.min(cursorCol, 3);
            ArmourSet set = TerraceLayout.set(cursorCol, cursorTier);
            if (set == null) return;
            TerraceLayout.Fig f = TerraceLayout.of(set);
            terrace.hover = f;
            aimX = f.x();
            aimY = f.cy();
        }
    }

    @Override
    public void tick() {
        // what you pick up while the Hall is open shows without closing it
        if (++ticks % 10 != 0) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int hash = 1;
        for (ItemStack it : player.getInventory().getNonEquipmentItems()) hash = 31 * hash + (it.isEmpty() ? 0 : it.getItem().hashCode() * 17 + it.getCount());
        for (EquipmentSlot slot : EquipmentSlot.values()) hash = 31 * hash + player.getItemBySlot(slot).getItem().hashCode();
        if (hash != inventoryHash) {
            boolean first = ticks == 10;
            inventoryHash = hash;
            if (first) return;
            PieceView p = close instanceof PieceView v ? v : null;
            ArmouryCache.ask(ArmsArmoury.REFRESH, p != null ? p.style : "longsword", p != null ? ArmsArmoury.TIERS[p.tier] : "");
            ClientPacketDistributor.sendToServer(new HallNet.Ask(HallNet.REFRESH, "", HallClient.standPos()));
        }
    }
}
