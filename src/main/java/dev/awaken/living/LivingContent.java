package dev.awaken.living;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** The living module's registries: its game rule, attachments and sounds (SPEC §21). Its light is drawn as geometry. */
public final class LivingContent {
    private LivingContent() {}

    static final DeferredRegister<GameRule<?>> RULES = DeferredRegister.create(Registries.GAME_RULE, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** {@code /gamerule awaken:pure_survival true}: no plates, numbers, XP stream, quests or bounties (SPEC §21.0.13). */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> PURE_SURVIVAL = RULES.register("pure_survival",
            () -> new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(), (visitor, rule) -> visitor.visitBoolean(rule),
                    Codec.BOOL, b -> b ? 1 : 0, false, FeatureFlagSet.of()));

    /**
     * A drop worth a light pillar (SPEC §21.1): 0 none, 1 rare (blue), 2 epic (violet), 3 a Power Core (white-gold).
     * Set on the dropped item entity when a creature dies; synced to everyone who sees it, never saved.
     */
    public static final Supplier<AttachmentType<Integer>> LOOT_PILLAR = ATTACHMENTS.register("loot_pillar",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /** The Wayfarer chains a player took (SPEC §21, Phase C): saved, kept through death, synced to its owner. */
    public static final Supplier<AttachmentType<dev.awaken.living.quest.QuestState>> QUESTS = ATTACHMENTS.register("quests",
            () -> AttachmentType.builder(dev.awaken.living.quest.QuestState::new).serialize(dev.awaken.living.quest.QuestState.CODEC.fieldOf("quests"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(dev.awaken.living.quest.QuestState.CODEC))
                    .build());

    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN = sound("living.scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRIT = sound("living.crit");
    public static final DeferredHolder<SoundEvent, SoundEvent> XP_TICK = sound("living.xp_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = sound("living.level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> REGION_ENTER = sound("living.region_enter");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        RULES.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
