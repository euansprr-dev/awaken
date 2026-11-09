package dev.awaken.shaping.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.shaping.Looks;
import dev.awaken.shaping.Shaping;
import dev.awaken.shaping.ShapingPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Looks ring (SPEC §52.4): open while Sneak is held. Moving over a look shows it on the wall in front of you, on every
 * block it would change. Left click sets it on the one block, right click on all of them, and letting go of Sneak changes
 * nothing. It doesn't pause the world or dim it.
 */
final class LooksScreen extends Screen {
    private static final int TILE = 36, HOT = 46, RADIUS = 84, DEAD = 24;
    private static final int CHALK = 0xFFF2E6C9, DARK = 0xC8141216, TEXT = 0xFFF2E6C9, SOFT = 0xFFB9AE9A;

    private final BlockPos target;
    private final int runSize;
    private final List<List<Block>> pages;
    private int page, hovered = -1;

    private LooksScreen(BlockPos target, int runSize, List<List<Block>> pages) {
        super(Component.translatable("awaken.shaping.ring.title"));
        this.target = target;
        this.runSize = runSize;
        this.pages = pages;
    }

    static void open(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return;
        BlockState here = Looks.lookAt(mc.level, pos);
        if (here == null) return;
        List<List<BlockPos>> layers = Looks.run(mc.level, pos, Shaping.RUN_MAX);
        List<BlockPos> run = new ArrayList<>();
        for (List<BlockPos> l : layers) run.addAll(l);
        ShapingOverlay.begin(mc.level, pos, run);
        mc.gui.setScreen(new LooksScreen(pos.immutable(), run.size(), pages(here.getBlock())));
    }

    /** Its own family first, then what you've learned, then every other family. */
    private static List<List<Block>> pages(Block aimed) {
        List<List<Block>> out = new ArrayList<>();
        Looks.Family own = Looks.familyOf(aimed);
        if (own != null) split(own.members(), out);
        List<Block> learned = new ArrayList<>();
        for (Identifier id : ShapingClient.known()) learned.add(BuiltInRegistries.BLOCK.getValue(id));
        split(learned, out);
        for (Looks.Family f : Looks.families()) if (f != own) split(f.members(), out);
        return out;
    }

    private static void split(List<Block> members, List<List<Block>> out) {
        for (int i = 0; i < members.size(); i += Looks.PAGE) out.add(members.subList(i, Math.min(i + Looks.PAGE, members.size())));
    }

    @Override
    protected void init() {
        // opening a screen lets go of every key; stay crouched while it's open
        if (!this.minecraft.options.toggleCrouch().get()) this.minecraft.options.keyShift.setDown(true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    private static boolean canTellSneak(Minecraft mc) {
        return !mc.options.toggleCrouch().get() && mc.options.keyShift.getKey().getType() == InputConstants.Type.KEYBOARD;
    }

    @Override
    public void tick() {
        var key = this.minecraft.options.keyShift.getKey();
        if (canTellSneak(this.minecraft) && !InputConstants.isKeyDown(key.getValue())) onClose();
    }

    @Override
    public void removed() {
        ShapingOverlay.end();
        MalletInput.ringClosed();
    }

    private List<Block> shown() {
        return this.pages.get(this.page);
    }

    private int slotAt(int mouseX, int mouseY) {
        int dx = mouseX - this.width / 2, dy = mouseY - this.height / 2;
        if (dx * dx + dy * dy < DEAD * DEAD) return -1;
        int n = shown().size();
        double turn = (Math.atan2(dx, -dy) / (Math.PI * 2.0) + 1.0) % 1.0;
        return (int) Math.round(turn * n) % n;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int cx = this.width / 2, cy = this.height / 2;
        List<Block> show = shown();
        int at = slotAt(mouseX, mouseY);
        if (at != this.hovered) {
            this.hovered = at;
            ShapingOverlay.preview(at < 0 ? null : show.get(at).defaultBlockState());
        }
        for (int i = 0; i < show.size(); i++) {
            double turn = Math.PI * 2.0 * i / show.size();
            tile(g, show.get(i), cx + (int) Math.round(Math.sin(turn) * RADIUS), cy - (int) Math.round(Math.cos(turn) * RADIUS), i == at);
        }
        if (at >= 0) {
            centred(g, show.get(at).getName(), cy - 10, TEXT);
            centred(g, Component.translatable("awaken.shaping.ring.how", this.runSize), cy + 4, SOFT);
        } else {
            centred(g, Component.translatable("awaken.shaping.ring.point"), cy - 4, SOFT);
        }
        if (this.pages.size() > 1) centred(g, Component.translatable("awaken.shaping.ring.page", this.page + 1, this.pages.size()), cy + RADIUS + 38, SOFT);
    }

    private void centred(GuiGraphicsExtractor g, Component text, int y, int colour) {
        g.text(this.font, text, this.width / 2 - this.font.width(text) / 2, y, colour);
    }

    private static void tile(GuiGraphicsExtractor g, Block b, int cx, int cy, boolean hot) {
        LookMaterials.Look m = LookMaterials.of(b.defaultBlockState());
        int half = (hot ? HOT : TILE) / 2;
        g.fill(cx - half - 2, cy - half - 2, cx + half + 2, cy + half + 2, hot ? CHALK : DARK);
        if (m == null) return;
        TextureAtlasSprite s = m.side(Direction.SOUTH).sprite();
        g.blit(TextureAtlas.LOCATION_BLOCKS, cx - half, cy - half, cx + half, cy + half, s.getU0(), s.getU1(), s.getV0(), s.getV1());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 && event.button() != 1) return false;
        int at = slotAt((int) event.x(), (int) event.y());
        if (at < 0) return true;
        Identifier id = BuiltInRegistries.BLOCK.getKey(shown().get(at));
        ClientPacketDistributor.sendToServer(new ShapingPayloads.SetLook(this.target, id, event.button() == 1));
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0 || this.pages.size() < 2) return true;
        this.page = Math.floorMod(this.page + (scrollY < 0 ? 1 : -1), this.pages.size());
        this.hovered = -1;
        ShapingOverlay.preview(null);
        return true;
    }
}
