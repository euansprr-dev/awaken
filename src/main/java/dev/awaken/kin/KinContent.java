package dev.awaken.kin;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** The kin's registrations: one entity type a kind that lives now, the player's Kinship, the kinds' sounds. */
public final class KinContent {
    private KinContent() {}

    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Map<KinKind, DeferredHolder<EntityType<?>, EntityType<KinBeast>>> TYPES = new EnumMap<>(KinKind.class);
    /** Each kind's voice: its idle call, its tell (the help was right), its worry (fear, a near miss). */
    public static final Map<KinKind, DeferredHolder<SoundEvent, SoundEvent>> CALL = new EnumMap<>(KinKind.class), TELL = new EnumMap<>(KinKind.class),
            WORRY = new EnumMap<>(KinKind.class);
    /** The band's soft chime when you got it right. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME = sound("kin.chime");

    static {
        for (KinKind k : KinKind.livingNow()) {
            float w = switch (k) {
                case LAMPWING -> 0.7F;
                case MOSSBACK -> 0.9F;
                case THATCHBACK, PUFFWOOL -> 0.8F;
                case EMBERKIT, PONDLING -> 0.5F;
                case DUMPLIN -> 0.45F;
                case CHIMEWREN -> 0.35F;
                default -> 0.6F;
            }, h = switch (k) {
                case BURROWBUN, SNUFFLET -> 0.55F;
                case TOTTER -> 0.45F;
                case PUFFWOOL -> 0.8F;
                case EMBERKIT -> 0.4F;
                case DUMPLIN -> 0.5F;
                case PONDLING, CHIMEWREN -> 0.35F;
                default -> 0.6F;
            };
            TYPES.put(k, ENTITIES.registerEntityType("kin_" + k.id(), KinBeast::new, MobCategory.CREATURE,
                    b -> k == KinKind.EMBERKIT ? b.sized(w, h).eyeHeight(h * 0.7F).clientTrackingRange(8).fireImmune()
                            : b.sized(w, h).eyeHeight(h * 0.7F).clientTrackingRange(8)));
            CALL.put(k, sound("kin." + k.id() + ".call"));
            TELL.put(k, sound("kin." + k.id() + ".tell"));
            WORRY.put(k, sound("kin." + k.id() + ".worry"));
        }
    }

    public static final Supplier<AttachmentType<Kinship>> KINSHIP = ATTACHMENTS.register("kinship",
            () -> AttachmentType.builder(Kinship::new).serialize(Kinship.CODEC).copyOnDeath().build());

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    /** The kind of an entity type (null for anything not kin). */
    public static KinKind kindOf(EntityType<?> type) {
        for (Map.Entry<KinKind, DeferredHolder<EntityType<?>, EntityType<KinBeast>>> e : TYPES.entrySet()) if (e.getValue().get() == type) return e.getKey();
        return null;
    }
}
