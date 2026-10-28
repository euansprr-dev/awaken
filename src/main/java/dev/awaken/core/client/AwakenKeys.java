package dev.awaken.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Every AWAKEN key in one place so they share a category and never collide. Modules read these in their own
 * client tick handlers (consumeClick / isDown).
 */
public final class AwakenKeys {
    private AwakenKeys() {}

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Awaken.id("awaken"));

    public static final KeyMapping POWER_1 = key("power_1", InputConstants.KEY_Z);
    public static final KeyMapping POWER_2 = key("power_2", InputConstants.KEY_X);
    public static final KeyMapping POWER_3 = key("power_3", InputConstants.KEY_C);
    public static final KeyMapping POWER_4 = key("power_4", InputConstants.KEY_V);
    public static final KeyMapping ULTIMATE = key("ultimate", InputConstants.KEY_R);
    /**
     * The movement power's slot (a dash, a blink). Its own key since the rope took Left Alt (SPEC §17.30): the two
     * shared it, and holding the rope silenced the slot.
     */
    public static final KeyMapping MOVEMENT = new KeyMapping("key.awaken.movement", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_4, CATEGORY);
    /** The rope (the Ascent's Tether): held to swing, let go to fly. Left Alt, the thumb's key. */
    public static final KeyMapping ROPE = key("rope", InputConstants.KEY_LALT);
    public static final KeyMapping STATUS = key("status", InputConstants.KEY_K);
    public static final KeyMapping FOCUS = key("focus", InputConstants.KEY_G);
    public static final KeyMapping REALITY_CHECK = key("reality_check", InputConstants.KEY_H);
    public static final KeyMapping ACCEPT = key("accept", InputConstants.KEY_Y);
    public static final KeyMapping DISMISS = key("dismiss", InputConstants.KEY_N);
    public static final KeyMapping BIND = key("bind", InputConstants.KEY_B);
    public static final KeyMapping PLAYTEST_FLAG = key("playtest_flag", InputConstants.KEY_J);

    public static final KeyMapping[] POWER_SLOTS = {POWER_1, POWER_2, POWER_3, POWER_4};

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.awaken." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, code, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        for (KeyMapping k : new KeyMapping[]{POWER_1, POWER_2, POWER_3, POWER_4, ULTIMATE, MOVEMENT, ROPE, STATUS, FOCUS,
                REALITY_CHECK, ACCEPT, DISMISS, BIND, PLAYTEST_FLAG}) {
            event.register(k);
        }
    }
}
