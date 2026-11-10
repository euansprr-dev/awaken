package dev.awaken.armour.client;

import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for the armour (SPEC §44.9), in their own file ({@code config/awaken-armour-client.toml}, listed in the
 * mod's settings screen): how far the sculpted look is drawn, whether the Forge Song is filmed or seen through your own
 * eyes, and whether a set's living parts move.
 */
public final class ArmourClientConfig {
    private ArmourClientConfig() {}

    /** The sculpted look: everywhere, only within {@link #NEAR} blocks of the camera, or never (the flat painted look). */
    public enum Sculpted { ON, NEAR, OFF }

    /** The Forge Song's camera: the cinematic shots, or your own eyes turning to the work. */
    public enum ForgeView { CINEMATIC, FIRST_PERSON }

    /** "Near only" draws the sculpted look this close to the camera; further off, the flat painted look. */
    public static final double NEAR = 24.0;

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.EnumValue<Sculpted> SCULPTED = B
            .comment("Sculpted armour: ON draws every armour set in its sculpted look; NEAR only within 24 blocks of you, and the",
                    "flat painted look further off (lighter with many armoured figures in view); OFF always the flat look.",
                    "The Fitting Stand always shows the sculpted suit and the work.")
            .defineEnum("sculpted_armour", Sculpted.ON);
    public static final ModConfigSpec.EnumValue<ForgeView> FORGE_VIEW = B
            .comment("The Forge Song's camera: CINEMATIC films the heat, the strikes, the quench and the fit from close shots;",
                    "FIRST_PERSON stays at your own eyes and turns to look at the work.")
            .defineEnum("forge_camera", ForgeView.CINEMATIC);
    public static final ModConfigSpec.BooleanValue MOTION = B
            .comment("Armour motion: the parts of a set that should not stand still (a halo, a marker, a plume, lanterns, wings)",
                    "move. false: every part is rigid on its bone.")
            .define("armour_motion", true);
    public static final ModConfigSpec.IntValue MOVE_SOUND = B
            .comment("Armour movement sound, 0 to 100: the soft sound a set makes at each stride, by what it is made of (hide",
                    "creaks, mail shifts, plate clinks). Low by default, silent when you sneak. 0 turns it off.")
            .defineInRange("armour_movement_sound", 60, 0, 100);
    public static final ModConfigSpec SPEC = B.build();

    // read on every armoured figure each frame: cached, updated when the file loads or changes
    private static volatile Sculpted sculpted = Sculpted.ON;
    private static volatile ForgeView view = ForgeView.CINEMATIC;
    private static volatile boolean motion = true;
    private static volatile int moveSound = 60;

    static void register(IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-armour-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Armour client settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            sculpted = SCULPTED.get();
            view = FORGE_VIEW.get();
            motion = MOTION.get();
            moveSound = MOVE_SOUND.get();
        } catch (RuntimeException e) {
            sculpted = Sculpted.ON;
            view = ForgeView.CINEMATIC;
            motion = true;
        }
    }

    /** Sets and saves armour motion (the command; tours). */
    static void motion(boolean on) {
        motion = on;
        if (!SPEC.isLoaded()) return;
        MOTION.set(on);
        SPEC.save();
    }

    /** Sets and saves the movement sound's level, 0-100 (the command; tours). */
    static void moveSound(int level) {
        moveSound = Math.max(0, Math.min(100, level));
        if (!SPEC.isLoaded()) return;
        MOVE_SOUND.set(moveSound);
        SPEC.save();
    }

    /** The movement sound's level, 0..1 (SPEC §44.12). */
    public static float moveSound() {
        return moveSound / 100F;
    }

    /** Whether a set's living parts move (SPEC §44.10). */
    public static boolean motion() {
        return motion;
    }

    /** Sets and saves (the commands; tours). */
    static void set(Sculpted s, ForgeView v) {
        sculpted = s;
        view = v;
        if (!SPEC.isLoaded()) return;
        SCULPTED.set(s);
        FORGE_VIEW.set(v);
        SPEC.save();
    }

    public static Sculpted sculpted() {
        return sculpted;
    }

    public static boolean firstPerson() {
        return view == ForgeView.FIRST_PERSON;
    }

    /** Whether this figure is drawn in its sculpted look now (the setting, and with NEAR its distance from the camera). */
    static boolean sculpts(Entity e) {
        Sculpted s = sculpted;
        if (s == Sculpted.ON) return true;
        if (s == Sculpted.OFF) return false;
        Entity cam = Minecraft.getInstance().getCameraEntity();
        return cam == null || e.distanceToSqr(cam) <= NEAR * NEAR;
    }
}
