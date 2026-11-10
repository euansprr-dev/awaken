package dev.awaken.hall.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourSet;
import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A step on K > Tasks that offers a choice of armour (SPEC §52): the suits themselves, tall and side by side, each with what
 * it is and how close you are. Click one to bring it close and choose it there. Esc goes back to your tasks.
 */
public final class LineUpScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    /** What a step offers; read live, so a choice shows at once. */
    public interface Offer {
        List<ArmourSet> sets();

        String sub(int i);

        /** "1 of 3 materials ready", or how the choice stands. */
        String state(int i);

        boolean chosen(int i);

        void choose(int i);
    }

    private static final Identifier BACKDROP = Awaken.id("textures/gui/hall/terr_dark.png");
    private static final int KEY_LINE = 10;
    private static final float FEET = 726F, MOST_SCALE = 278F, MOST_TALL = 560F, STEP = 418F;

    private final Component heading;
    private final String why;
    private final Offer offer;
    private final Component[] names;
    private @Nullable Stage stage;
    private @Nullable SuitView close;
    private long last = System.nanoTime(), version;
    private float time, fly;
    private int hover = -1;
    private final float[] turn;

    private LineUpScreen(String title, String why, Offer offer) {
        super(Component.literal(title));
        this.heading = Stage.blackletter(title);
        this.why = why;
        this.offer = offer;
        List<ArmourSet> sets = offer.sets();
        names = new Component[sets.size()];
        turn = new float[sets.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = Stage.blackletter(sets.get(i).setName);
            turn[i] = -22F;
        }
    }

    /** Over the band, which comes back as it was when this closes. */
    public static void open(String title, String why, Offer offer) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketDistributor.sendToServer(new HallNet.Ask(HallNet.PEEK, "", BlockPos.ZERO));
        HallSounds.open();
        mc.gui.pushScreenLayer(new LineUpScreen(title, why, offer));
    }

    private float cx(int i) {
        return 854F + (i - (names.length - 1) / 2F) * Math.min(STEP, 1500F / names.length);
    }

    private float scale(ArmourSet set) {
        float[] b = TerraceLayout.bounds(set);
        return Math.min(MOST_SCALE, MOST_TALL / Math.max(.5F, b[1] - b[0]));
    }

    private float cy(ArmourSet set) {
        float[] b = TerraceLayout.bounds(set);
        return FEET - (b[1] - .5F) * scale(set);
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
        fly = Math.min(1F, fly + dt / .35F);
        if (stage == null) stage = new Stage(font);
        Stage s = stage;
        s.begin(g, width, height);
        SuitView c = close;
        if (c != null && c.advance(dt)) {
            close = null;
            c = null;
        }
        HallNet.Wall w = HallClient.wall;
        if (c == null || c.eased() < 1F) lineUp(s, dt, s.sx(mx), s.sy(my), c == null ? 1F : 1F + 2.2F * c.eased(), c);
        if (c != null && w != null) {
            s.g.nextStratum();
            s.fill(0F, 0F, Stage.W, Stage.H, Stage.argb(0x040609, Math.min(1F, c.eased() * 1.25F)));
            s.g.nextStratum();
            c.draw(s, w, dt, time, mx, my);
        }
        s.end(width, height);
    }

    private void lineUp(Stage s, float dt, float mx, float my, float zoom, @Nullable SuitView c) {
        var g = s.g;
        List<ArmourSet> sets = offer.sets();
        hover = c != null || fly < 1F ? -1 : at(mx, my);
        g.pose().pushMatrix();
        if (zoom != 1F) g.pose().translate(c.zoomX(), c.zoomY()).scale(zoom, zoom).translate(-c.zoomX(), -c.zoomY());
        g.blit(RenderPipelines.GUI_TEXTURED, BACKDROP, 0, 0, 0F, 0F, Stage.W, Stage.H, Stage.W, Stage.H, Stage.W, Stage.H, 0xFF6A6A70);
        s.fill(0F, 0F, Stage.W, Stage.H, 0x8C000000);
        s.glow(854F, 760F, 900F, 220F, 0xFF8A3A, .22F);
        Placed[] line = new Placed[sets.size()];
        for (int i = 0; i < line.length; i++) {
            ArmourSet set = sets.get(i);
            float want = hover == i ? (mx - cx(i)) * .08F - 8F : -22F + (float) Math.sin(time * .5F + i) * 5F;
            turn[i] += (want - turn[i]) * Math.min(1F, dt * 6F);
            if (i == hover || offer.chosen(i)) s.glow(cx(i), FEET - 280F, 260F, 380F, offer.chosen(i) ? 0x9FE6D6 : 0xFFCF7A, i == hover ? .3F : .2F);
            line[i] = Placed.suit(set, cx(i), cy(set), 0F, scale(set) * (.92F + .08F * fly), turn[i], -4F, Placed.all(Placed.FULL), fly);
        }
        version++;
        g.nextStratum();
        g.submitPictureInPictureRenderState(new PicState(KEY_LINE, 0, 0, Stage.W, Stage.H, s.rps, version, line, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, g.pose()));
        g.nextStratum();
        float title = Math.min(3F, 1400F / Math.max(1F, s.titleWidth(heading, 1F)));
        s.titleCentre(heading, 854F, 34F, Math.max(1F, (float) Math.floor(title)), Stage.TITLE);
        s.fitted(why, 854F, 124F, 1560F, 3F, Stage.LABEL, 0);
        for (int i = 0; i < names.length; i++) {
            float x = cx(i);
            s.titleCentre(names[i], x, 740F, 2F, offer.chosen(i) ? Stage.GOOD : Stage.TITLE);
            s.fitted(offer.sub(i), x, 802F, 400F, 3F, Stage.EDGE, 0);
            s.fitted(offer.state(i), x, 834F, 400F, 3F, offer.chosen(i) ? Stage.GOOD : Stage.LANTERN, 0);
        }
        s.hint("Esc", "Back to your tasks", 28F, 918F);
        s.hint("Click", "Bring one close", 1684F - 28F - s.width("Click", 1.5F) - 24F - s.width("Bring one close", 2F), 918F);
        g.pose().popMatrix();
    }

    private int at(float x, float y) {
        if (y < 150F || y > 880F) return -1;
        for (int i = 0; i < names.length; i++) if (Math.abs(x - cx(i)) < Math.min(STEP, 1500F / names.length) / 2F) return i;
        return -1;
    }

    private void open(int i) {
        HallSounds.reach();
        ArmourSet set = offer.sets().get(i);
        float sc = scale(set), h = sc / .482F;
        TerraceLayout.Fig from = new TerraceLayout.Fig(set, set.rank.ordinal(), TerraceLayout.column(set.archetype), cx(i), cy(set) + sc, h);
        close = new SuitView(from, -1, null, new SuitView.Choosing() {
            @Override public boolean chosen(ArmourSet s) {
                int at = offer.sets().indexOf(s);
                return at >= 0 && offer.chosen(at);
            }

            @Override public void choose(ArmourSet s) {
                int at = offer.sets().indexOf(s);
                if (at >= 0) offer.choose(at);
            }

            @Override public @Nullable ArmourSet step(ArmourSet s, int d) {
                List<ArmourSet> sets = offer.sets();
                int at = sets.indexOf(s);
                return at < 0 ? null : sets.get(Math.floorMod(at + d, sets.size()));
            }
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || stage == null) return super.mouseClicked(event, doubleClick);
        float x = stage.sx(event.x()), y = stage.sy(event.y());
        SuitView c = close;
        if (c != null) {
            if (!c.closing && !c.click(x, y)) c.dragging = c.fly >= 1F;
            return true;
        }
        int i = at(x, y);
        if (i >= 0 && HallClient.wall != null) open(i);
        else if (x < 340F && y >= 906F) onClose();
        return true;
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
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        SuitView c = close;
        if (key == InputConstants.KEY_ESCAPE) {
            if (c != null) c.closing = true;
            else onClose();
            return true;
        }
        if (c != null && !c.closing) {
            switch (key) {
                case InputConstants.KEY_LEFT -> c.step(-1, 0);
                case InputConstants.KEY_RIGHT -> c.step(1, 0);
                case InputConstants.KEY_SPACE -> c.space(true);
                default -> {
                    return c.key(key) || super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (close != null && event.key() == InputConstants.KEY_SPACE) close.space(false);
        return super.keyReleased(event);
    }
}
