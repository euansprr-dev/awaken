package dev.awaken.powers;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Power sounds. Each points at vanilla sounds with pitch/volume changes (merge/powers/assets/awaken/sounds.json). */
public final class PowerSounds {
    private PowerSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FAIL = s("power.fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEARN = s("power.learn");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = s("power.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHOOSH = s("power.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUNCH = s("power.punch");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_UP = s("power.charge_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIP = s("power.rip");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = s("power.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCKWAVE = s("power.shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> BRACE = s("power.brace");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER = s("power.thunder");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZAP = s("power.zap");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOW_START = s("power.flow_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOW_END = s("power.flow_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOVER = s("power.hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY = s("power.gravity");
    public static final DeferredHolder<SoundEvent, SoundEvent> THROW = s("power.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM = s("power.beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOVA = s("power.nova");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRIER = s("power.barrier");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRIER_BREAK = s("power.barrier_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOT = s("power.shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> OVERLOAD = s("power.overload");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADOW = s("power.shadow");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOAK = s("power.cloak");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = s("power.summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND = s("power.mind");
    public static final DeferredHolder<SoundEvent, SoundEvent> POP = s("power.pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILD = s("power.build");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE = s("power.collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONSTRUCT_FADE = s("power.construct_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAPPLE = s("power.grapple");
    public static final DeferredHolder<SoundEvent, SoundEvent> JET = s("power.jet");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_SHOT = s("power.drone_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIL = s("power.rail");
    public static final DeferredHolder<SoundEvent, SoundEvent> NANITE = s("power.nanite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRIT = s("power.spirit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAL = s("power.heal");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE = s("power.eye");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASH = s("power.lash");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD = s("power.ward");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID = s("power.void");
    public static final DeferredHolder<SoundEvent, SoundEvent> DREAM = s("power.dream");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST = s("power.frost");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER = s("power.ember");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEAM = s("power.steam");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST = s("power.blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> STUN = s("power.stun");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHATTER = s("power.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> DANGER = s("power.danger");

    private static DeferredHolder<SoundEvent, SoundEvent> s(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }
}
