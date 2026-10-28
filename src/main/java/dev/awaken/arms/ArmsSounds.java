package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.core.api.BodyMaterial;
import dev.awaken.core.api.Strikes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Arms' sounds (SPEC §25): the kit rendered by tools/art/specs/audio_arms.py, its sounds.json in src/main/merge/arms.
 * Which sound a blow makes is chosen here for both sides, so the fighter's own prediction and everyone else's world
 * hear the same thing.
 */
public final class ArmsSounds {
    private ArmsSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SWISH_LIGHT = sound("swish_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWISH_HEAVY = sound("swish_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWISH_THRUST = sound("swish_thrust");
    // Swing Studio's: a blow that lands within a tick of its press, and the katana's own cut (SPEC §25 "Swing Studio")
    public static final DeferredHolder<SoundEvent, SoundEvent> SWISH_QUICK = sound("swish_quick");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWISH_KATANA = sound("swish_katana");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_FLESH = sound("hit_flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_FLESH_HEAVY = sound("hit_flesh_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_ARMOR = sound("hit_armor");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_BONE = sound("hit_bone");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_WOOD = sound("hit_wood");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_STONE = sound("hit_stone");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_SPIRIT = sound("hit_spirit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_SLIME = sound("hit_slime");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT = sound("deflect");
    /** A shot deflected off the guard: a lighter, brighter ring than a blade's, and its ricochet whining away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLANCE = sound("glance");
    /** A shot blocked on the guard: the block's own sound, lighter (a smaller thing struck the steel). */
    public static final float SHOT_BLOCK_VOLUME = 0.8F, SHOT_BLOCK_PITCH = 1.2F;
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOCK = sound("block");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_BREAK = sound("guard_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK = sound("break");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXECUTION = sound("execution");
    public static final DeferredHolder<SoundEvent, SoundEvent> ART = sound("art");
    public static final DeferredHolder<SoundEvent, SoundEvent> PERFECT_DODGE = sound("perfect_dodge");
    public static final DeferredHolder<SoundEvent, SoundEvent> DODGE = sound("dodge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = sound("charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLINT = sound("glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_LIGHT = sound("tell_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_HEAVY = sound("tell_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_PERILOUS = sound("tell_perilous");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_GRAB = sound("tell_grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = sound("heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_BLUNT = sound("hit_blunt");

    /** From this damage multiplier a blow lands as a heavy one (the finishers, a heavy, a riposte). */
    static final float HEAVY_MOTION = 1.4F;
    private static final float SEMITONE = 1.0594631F;

    /** The blade through the air: the studio's sound for the move (FpSwings.swishSound) when it has one, else the row's. */
    public static SoundEvent swish(Archetype.MoveDef def, @Nullable String studio) {
        if ("quick".equals(studio)) return SWISH_QUICK.get();
        if ("katana".equals(studio)) return SWISH_KATANA.get();
        return swish(def);
    }

    /** The blade through the air. */
    public static SoundEvent swish(Archetype.MoveDef def) {
        return switch (def.swish()) {
            case LIGHT -> SWISH_LIGHT.get();
            case HEAVY -> SWISH_HEAVY.get();
            case THRUST -> SWISH_THRUST.get();
            case ART -> ART.get();
        };
    }

    /** Each rhythm step plays its swish a semitone higher, so a combo climbs. */
    public static float swishPitch(int cadence) {
        return (float) Math.pow(SEMITONE, cadence);
    }

    /** A blow by a blunt style (the Maul) lands blunt on every body but a spirit; any other style as its blade meets the body. */
    public static SoundEvent impact(Entity target, Archetype.@Nullable Move move, Archetype.@Nullable MoveDef def, @Nullable String style) {
        if (Arms.MAUL.equals(style) && move != Archetype.Move.EXECUTION && BodyMaterial.of(target) != BodyMaterial.SPIRIT) return HIT_BLUNT.get();
        return impact(target, move, def);
    }

    /** The blade landing: an Execution is a blow of its own; any other is what the body is made of. */
    public static SoundEvent impact(Entity target, Archetype.@Nullable Move move, Archetype.@Nullable MoveDef def) {
        if (move == Archetype.Move.EXECUTION) return EXECUTION.get();
        return switch (BodyMaterial.of(target)) {
            case FLESH -> heavy(def) ? HIT_FLESH_HEAVY.get() : HIT_FLESH.get();
            case ARMOR -> HIT_ARMOR.get();
            case BONE -> HIT_BONE.get();
            case WOOD -> HIT_WOOD.get();
            case STONE -> HIT_STONE.get();
            case SPIRIT -> HIT_SPIRIT.get();
            case SLIME -> HIT_SLIME.get();
        };
    }

    /** A heavy blow on a body without a heavy sound of its own lands a little lower. */
    public static float impactPitch(Entity target, Archetype.@Nullable MoveDef def) {
        return heavy(def) && BodyMaterial.of(target) != BodyMaterial.FLESH ? 0.9F : 1.0F;
    }

    private static boolean heavy(Archetype.@Nullable MoveDef def) {
        return def != null && def.motion() >= HEAVY_MOTION;
    }

    /** The sound of a creature telling a blow of this class (each class has one voice everywhere), or null for none. */
    static @Nullable SoundEvent tell(Strikes.Kind kind) {
        return switch (kind) {
            case LIGHT -> TELL_LIGHT.get();
            case HEAVY -> TELL_HEAVY.get();
            case PERILOUS -> TELL_PERILOUS.get();
            case GRAB -> TELL_GRAB.get();
            case NONE -> null;
        };
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("arms." + name, () -> SoundEvent.createVariableRangeEvent(Awaken.id("arms." + name)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
