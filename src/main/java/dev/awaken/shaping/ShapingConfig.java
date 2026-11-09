package dev.awaken.shaping;

import dev.awaken.Awaken;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Made by Hand's switches (SPEC §52.8), in {@code config/awaken-shaping.toml}. */
public final class ShapingConfig {
    private ShapingConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue MALLET = B
            .comment("The Mallet's knocks and looks. Off, it does nothing; blocks already cut stay as they are.")
            .define("mallet", true);
    private static final ModConfigSpec.BooleanValue DRAW_SHAPES = B
            .comment("Draw cut blocks with their cut. Off, every cut block is drawn whole in its look. Chunks redraw when they next change.")
            .define("draw_shapes", true);
    private static final ModConfigSpec SPEC = B.build();

    // read while meshing, on worker threads
    private static volatile boolean mallet = true, drawShapes = true;

    static void register(IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SYNCED, SPEC, Awaken.MODID + "-shaping.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Shaping settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() != SPEC) return;
            try {
                mallet = MALLET.get();
                drawShapes = DRAW_SHAPES.get();
            } catch (RuntimeException ex) {
                mallet = true;
                drawShapes = true;
            }
        });
    }

    public static boolean mallet() {
        return mallet;
    }

    public static boolean drawShapes() {
        return drawShapes;
    }
}
