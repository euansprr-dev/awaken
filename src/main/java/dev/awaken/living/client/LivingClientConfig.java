package dev.awaken.living.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for the band's combat layer ({@code config/awaken-living-client.toml}, SPEC §21.1). Read once a tick
 * into plain fields, so frame code never touches the config.
 */
public final class LivingClientConfig {
    private LivingClientConfig() {}

    public enum Numbers { FULL, CRITS, OFF }

    public enum Moment { FULL, COMPACT }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue PLATES = B
            .comment("Your band reads the creatures you look at and fight: a plate of Holo glass with name, level and health.")
            .define("plates", true);
    public static final ModConfigSpec.EnumValue<Numbers> DAMAGE_NUMBERS = B
            .comment("Damage you deal, written in light where it lands: FULL, CRITS (critical hits only) or OFF.")
            .defineEnum("damageNumbers", Numbers.FULL);
    public static final ModConfigSpec.BooleanValue XP_STREAM = B
            .comment("XP streams from what earned it into your band, with how much.")
            .define("xpStream", true);
    public static final ModConfigSpec.EnumValue<Moment> LEVEL_MOMENT = B
            .comment("Reaching a level: FULL (light rings, a column, the pane, a moment of stillness) or COMPACT (the pane only).")
            .defineEnum("levelMoment", Moment.FULL);
    public static final ModConfigSpec.BooleanValue DREAD = B
            .comment("A land far above your level cools the screen and brings in a low sound before any creature does (SPEC 21.10).")
            .define("dread", true);
    public static final ModConfigSpec SPEC = B.build();

    // cached once a tick
    static boolean plates = true, xpStream = true, dread = true;
    static Numbers numbers = Numbers.FULL;
    static Moment moment = Moment.FULL;

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-living-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Living Wilds client settings could not be registered; the defaults apply", e);
        }
    }

    static void read() {
        try {
            plates = PLATES.get();
            xpStream = XP_STREAM.get();
            dread = DREAD.get();
            numbers = DAMAGE_NUMBERS.get();
            moment = LEVEL_MOMENT.get();
        } catch (RuntimeException notLoaded) {
            // defaults until the config loads
        }
    }
}
