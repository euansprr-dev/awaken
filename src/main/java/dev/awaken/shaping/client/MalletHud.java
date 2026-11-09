package dev.awaken.shaping.client;

import dev.awaken.shaping.MalletData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** With the Mallet out: what right click does, and the look it carries, beside the hotbar. */
final class MalletHud {
    private MalletHud() {}

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null || !MalletInput.held(mc)) return;
        MalletData data = MalletData.of(mc.player.getMainHandItem());
        Component mode = Component.translatable(data.mode().key());
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 100, y = mc.getWindow().getGuiScaledHeight() - 21;
        int textX = x + 4;
        Component second = null;
        LookMaterials.Look m = null;
        if (data.look().isPresent()) {
            Block b = BuiltInRegistries.BLOCK.getValue(data.look().get());
            second = b.getName();
            m = LookMaterials.of(b.defaultBlockState());
            if (m != null) textX = x + 22;
        }
        int width = Math.max(mc.font.width(mode), second == null ? 0 : mc.font.width(second));
        g.fill(x, y, textX + width + 4, y + 20, 0x90000000);
        if (m != null) {
            TextureAtlasSprite s = m.side(Direction.SOUTH).sprite();
            g.blit(TextureAtlas.LOCATION_BLOCKS, x + 2, y + 2, x + 18, y + 18, s.getU0(), s.getU1(), s.getV0(), s.getV1());
        }
        g.text(mc.font, mode, textX, second == null ? y + 6 : y + 2, 0xFFF2E6C9);
        if (second != null) g.text(mc.font, second, textX, y + 11, 0xFFB9AE9A);
    }
}
