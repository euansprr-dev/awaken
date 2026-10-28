package dev.awaken.mobs.client.kit;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for our creatures' bodies ({@code config/awaken-mobs-client.toml}, SPEC §34.4). */
public final class KitConfig {
    private KitConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue KIT_BODIES = B
            .comment("Bosses wear their own Creature Kit bodies, moving through every move they make (SPEC 34). Off: their old bodies (the fight is the same). Takes effect on a resource reload (F3+T).")
            .define("kitBodies", true);
    public static final ModConfigSpec SPEC = B.build();

    public static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-mobs-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Creature kit client settings could not be registered; the defaults apply", e);
        }
    }

    public static boolean kitBodies() {
        try {
            return KIT_BODIES.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
