package dev.awaken.binoculars.client;

import dev.awaken.Awaken;
import dev.awaken.binoculars.GlassKey;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The binoculars' client settings (SPEC §39.9), in their own file ({@code config/awaken-binoculars-client.toml}). */
public final class GlassConfig {
    private GlassConfig() {}

    public enum Edge { TWIN, SINGLE, OFF }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Binoculars on the binoculars key (I). Off, the key does nothing and is free for something else.")
            .define("enabled", true);
    static final ModConfigSpec.EnumValue<GlassKey.Mode> KEY_MODE = B
            .comment("TAP_HOLD: a quick tap keeps them up, holding the key is a peek that drops when you let go.",
                    "HOLD: up only while you hold the key. TOGGLE: each press raises or lowers them.")
            .defineEnum("key_mode", GlassKey.Mode.TAP_HOLD);
    static final ModConfigSpec.IntValue LOOK_SPEED = B
            .comment("How fast the view turns while zoomed, in per cent. 100 matches the zoom exactly: things move across",
                    "the screen as fast as they do without the binoculars.")
            .defineInRange("look_speed", 100, 50, 150);
    static final ModConfigSpec.BooleanValue SWAY = B
            .comment("A slow breathing drift of the view while they're up (crouch to steady it). Off with reduced motion too.")
            .define("sway", true);
    static final ModConfigSpec.EnumValue<Edge> EDGE = B
            .comment("The lens edge: TWIN (two round fields side by side), SINGLE (one), or OFF (for screenshots).")
            .defineEnum("edge", Edge.TWIN);
    static final ModConfigSpec.BooleanValue READOUT = B
            .comment("The line on the glass: the step, how far away the middle of the view is, and which way you look.")
            .define("readout", true);
    static final ModConfigSpec.DoubleValue LAST_STEP = B
            .comment("The step the binoculars come up at (remembered from the last time).")
            .defineInRange("last_step", 4.0, 1.0, 16.0);
    static final ModConfigSpec SPEC = B.build();

    private static volatile boolean enabled = true, sway = true, readout = true;
    private static volatile GlassKey.Mode mode = GlassKey.Mode.TAP_HOLD;
    private static volatile Edge edge = Edge.TWIN;
    private static volatile float lookSpeed = 1F, lastStep = 0F;

    static void register(net.neoforged.bus.api.IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-binoculars-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Binoculars settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            enabled = ENABLED.get();
            mode = KEY_MODE.get();
            lookSpeed = LOOK_SPEED.get() / 100F;
            sway = SWAY.get();
            edge = EDGE.get();
            readout = READOUT.get();
            lastStep = LAST_STEP.get().floatValue();
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Binoculars settings could not be read; using defaults", e);
        }
    }

    static boolean enabled() {
        return enabled;
    }

    static GlassKey.Mode mode() {
        return mode;
    }

    static float lookSpeed() {
        return lookSpeed;
    }

    static boolean sway() {
        return sway;
    }

    static Edge edge() {
        return edge;
    }

    static boolean readout() {
        return readout;
    }

    /** The remembered step, or 0 before the file was read (the feel's first step then). */
    static float lastStep() {
        return lastStep;
    }

    static void rememberStep(float step) {
        if (step == lastStep) return;
        lastStep = step;
        if (!SPEC.isLoaded()) return;
        LAST_STEP.set((double) step);
        SPEC.save();
    }
}
