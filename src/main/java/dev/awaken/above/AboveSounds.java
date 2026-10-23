package dev.awaken.above;

import dev.awaken.core.CoreModule;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Above sound events. They point at vanilla sound files with new pitch/volume in sounds.json (merge/above). */
public final class AboveSounds {
    private AboveSounds() {}

    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN = CoreModule.sound("above.scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN_FAIL = CoreModule.sound("above.scan_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> PING = CoreModule.sound("above.ping");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_HUM = CoreModule.sound("above.door_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_OPEN = CoreModule.sound("above.door_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILD = CoreModule.sound("above.build");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR = CoreModule.sound("above.reactor");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORT = CoreModule.sound("above.teleport");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH = CoreModule.sound("above.launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIATHAN = CoreModule.sound("above.leviathan");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOG = CoreModule.sound("above.log");
    public static final DeferredHolder<SoundEvent, SoundEvent> JET = CoreModule.sound("above.jet");
    public static final DeferredHolder<SoundEvent, SoundEvent> MODULE_OFF = CoreModule.sound("above.module_off");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = CoreModule.sound("above.ui_click");

    /** Touch the class so the holders register before the sound registry fires. */
    public static void init() {}
}
