package dev.awaken.delve.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the deep's drawn light (SPEC §28.3) and the strike (§34.3), in their own file ({@code config/awaken-delve-client.toml}). */
public final class DelveClientConfig {
    private DelveClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue HELD_LIGHT = B
            .comment("Held light: a torch, lantern or any light-giving block in either hand lights the ground, walls and ceiling",
                    "round whoever holds it (drawn, never placed: nothing is relit). Off, a held light gives no light, as in vanilla.")
            .define("held_light", true);
    public static final ModConfigSpec.BooleanValue STRIKE_PARTICLES = B
            .comment("The strike's particles: rock, ore and loose ground throw chips of their own texture when struck and broken,",
                    "with a breath of dust on the break (SPEC 34.3). Off, vanilla's crumbs come back.")
            .define("strike_particles", true);
    public static final ModConfigSpec.BooleanValue TRUE_STRIKES = B
            .comment("True strikes: a seam shows on an ore's face; a bite that lands on it counts extra (SPEC 34.3).",
                    "Off, no seam is drawn and every bite counts the same.")
            .define("true_strikes", true);
    public static final ModConfigSpec.BooleanValue CAVE_AIR = B
            .comment("Cave air: under the rock the air closes in (from about 8 blocks out to about 52), tinted by the band you are in,",
                    "so far tunnels fade into coloured dark (SPEC 34.5). Off, the caves keep the game's own fog.")
            .define("cave_air", true);
    public static final ModConfigSpec SPEC = B.build();

    // read every frame: cached, updated when the file loads or changes
    private static volatile boolean heldLight = true, strikeParticles = true, trueStrikes = true, caveAir = true;

    static void register(net.neoforged.bus.api.IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-delve-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Delve client settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            heldLight = HELD_LIGHT.get();
            strikeParticles = STRIKE_PARTICLES.get();
            trueStrikes = TRUE_STRIKES.get();
            caveAir = CAVE_AIR.get();
        } catch (RuntimeException e) {
            heldLight = strikeParticles = trueStrikes = caveAir = true;
        }
    }

    public static boolean heldLight() {
        return heldLight;
    }

    public static boolean strikeParticles() {
        return strikeParticles;
    }

    public static boolean caveAir() {
        return caveAir;
    }

    public static boolean trueStrikes() {
        return trueStrikes;
    }
}
