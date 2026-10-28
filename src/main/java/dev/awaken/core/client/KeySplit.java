package dev.awaken.core.client;

import dev.awaken.Awaken;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The rope and the movement power once shared Left Alt (SPEC §17.30). A save from then keeps the movement power on
 * Left Alt beside the rope's new mapping, so once, on the first tick, a movement key still on Left Alt while the rope
 * is too goes to its new default, and a tip says where both keys are now. A marker file keeps it to once: a player who
 * binds them together again afterwards is left alone.
 */
final class KeySplit {
    private KeySplit() {}

    static final Identifier TIP = Awaken.id("first/keys_split");
    private static final String MARKER = "awaken-keys-split.txt";
    private static boolean checked;
    private static int tipTicks;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!checked) {
            checked = true;
            Path marker = FMLPaths.CONFIGDIR.get().resolve(MARKER);
            if (Files.exists(marker)) return;
            KeyMapping rope = AwakenKeys.ROPE, move = AwakenKeys.MOVEMENT;
            if (!move.isUnbound() && move.getKey().equals(rope.getKey())) {
                move.setKey(move.getDefaultKey());
                KeyMapping.resetMapping();
                mc.options.save();
                tipTicks = 1200;
                Awaken.LOG.info("Keys: the movement power left the rope's key for {}", move.getKey().getName());
            }
            try {
                Files.writeString(marker, "The rope and the movement power keys were split once (SPEC 17.30).\n");
            } catch (IOException e) {
                Awaken.LOG.warn("Keys: couldn't write {}", marker, e);
            }
        }
        // the tip, offered for a minute in a world (it shows once the director has room)
        if (tipTicks > 0 && mc.player != null && mc.gui.screen() == null) {
            tipTicks--;
            if (dev.awaken.core.api.TipHooks.client().wants(TIP)) dev.awaken.core.api.TipHooks.client().offer(TIP);
        }
    }
}
