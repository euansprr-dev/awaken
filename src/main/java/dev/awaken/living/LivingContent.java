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

/**
 * The living module's registries: its game rule, attachments, sounds and the Wayfarers' camp's two blocks (SPEC §21,
 * §27.6). Its light is drawn as geometry.
 */
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
     * {@code /gamerule awaken:living_herds false}: herds spawn naturally again and the ledger keeps only packs and roosts
     * (SPEC §21.7). The GameTest server starts with it off, so the fauna tests see natural herds as they always did.
     */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> LIVING_HERDS = RULES.register("living_herds",
            () -> new GameRule<>(GameRuleCategory.SPAWNING, GameRuleType.BOOL, BoolArgumentType.bool(), (visitor, rule) -> visitor.visitBoolean(rule),
                    Codec.BOOL, b -> b ? 1 : 0, true, FeatureFlagSet.of()));

    /**
     * {@code /gamerule awaken:wild_forms false}: creatures keep their vanilla bodies and moves (SPEC §21.11). The GameTest server starts with it off, so the older
     * tests that kill a zombie outright in far land (where forms are the rule) see what they always did; the form tests roll and set forms themselves.
     */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> WILD_FORMS = RULES.register("wild_forms",
            () -> new GameRule<>(GameRuleCategory.SPAWNING, GameRuleType.BOOL, BoolArgumentType.bool(), (visitor, rule) -> visitor.visitBoolean(rule),
                    Codec.BOOL, b -> b ? 1 : 0, true, FeatureFlagSet.of()));

    /**
     * A drop worth a light pillar (SPEC §21.1): 0 none, 1 rare (blue), 2 epic (violet), 3 a Power Core (white-gold).
     * Set on the dropped item entity when a creature dies; synced to everyone who sees it, never saved.
     */
    public static final Supplier<AttachmentType<Integer>> LOOT_PILLAR = ATTACHMENTS.register("loot_pillar",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /** The wonders a player has found ("kind@x,z"): saved, kept through death, synced to its owner (the Atlas draws them). */
    public static final Supplier<AttachmentType<java.util.List<String>>> WONDERS = ATTACHMENTS.register("wonders",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of())
                    .serialize(com.mojang.serialization.Codec.STRING.listOf().fieldOf("wonders"), l -> !l.isEmpty())
                    .sync(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).copyOnDeath().build());

    /** The surface secrets a player has found ("kind@x,z"): saved, kept through death, synced to its owner. */
    public static final Supplier<AttachmentType<java.util.List<String>>> SECRETS = ATTACHMENTS.register("secrets",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of())
                    .serialize(com.mojang.serialization.Codec.STRING.listOf().fieldOf("secrets"), l -> !l.isEmpty())
                    .sync(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).copyOnDeath().build());

    /** The form a creature wears (SPEC §21.11): rolled once, saved on the creature, synced to everyone who sees it. */
    public static final Supplier<AttachmentType<dev.awaken.living.combat.Forms.Form>> FORM = ATTACHMENTS.register("wild_form",
            () -> AttachmentType.builder(() -> dev.awaken.living.combat.Forms.Form.UNSET)
                    .serialize(dev.awaken.living.combat.Forms.Form.CODEC.fieldOf("form"), f -> f.rung() >= 0)
                    .sync(dev.awaken.living.combat.Forms.Form.STREAM_CODEC).build());

    /** The Wayfarer chains a player took (SPEC §21, Phase C): saved, kept through death, synced to its owner. */
    public static final Supplier<AttachmentType<dev.awaken.living.quest.QuestState>> QUESTS = ATTACHMENTS.register("quests",
            () -> AttachmentType.builder(dev.awaken.living.quest.QuestState::new).serialize(dev.awaken.living.quest.QuestState.CODEC.fieldOf("quests"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(dev.awaken.living.quest.QuestState.CODEC))
                    .build());

    /** A player's Association notices (SPEC §21.0.9): saved, kept through death, synced to its owner. */
    public static final Supplier<AttachmentType<dev.awaken.living.bounty.BountyState>> BOUNTIES = ATTACHMENTS.register("bounties",
            () -> AttachmentType.builder(dev.awaken.living.bounty.BountyState::new).serialize(dev.awaken.living.bounty.BountyState.CODEC.fieldOf("bounties"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(dev.awaken.living.bounty.BountyState.CODEC))
                    .build());

    /** A player's crafts (SPEC §21.9): craft XP and kinds hunted, synced to them, kept through death. */
    public static final Supplier<AttachmentType<dev.awaken.living.craft.CraftState>> CRAFTS = ATTACHMENTS.register("crafts",
            () -> AttachmentType.builder(dev.awaken.living.craft.CraftState::new).serialize(dev.awaken.living.craft.CraftState.CODEC.fieldOf("crafts"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(dev.awaken.living.craft.CraftState.CODEC))
                    .build());

    /** What a hunter has learned (SPEC §41.3): research per kind and the beds, dens and kill sites found; synced, kept through death. */
    public static final Supplier<AttachmentType<dev.awaken.living.hunt.HuntState>> HUNT = ATTACHMENTS.register("hunt",
            () -> AttachmentType.builder(() -> dev.awaken.living.hunt.HuntState.EMPTY).serialize(dev.awaken.living.hunt.HuntState.CODEC.fieldOf("hunt"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(dev.awaken.living.hunt.HuntState.CODEC))
                    .build());

    /** A quarry's fight (SPEC §41.4): damage per part, parts broken, whether it limped, its hunters; saved with it. */
    public static final Supplier<AttachmentType<dev.awaken.living.hunt.Quarry.State>> QUARRY = ATTACHMENTS.register("quarry",
            () -> AttachmentType.builder(dev.awaken.living.hunt.Quarry.State::new)
                    .serialize(dev.awaken.living.hunt.Quarry.State.CODEC.fieldOf("quarry"), s -> !s.hunters.isEmpty() || !s.broken.isEmpty()).build());

    /** A quarry's carcass (SPEC §41.4): the game time it goes; saved, synced to everyone who sees it (it lies on its side). */
    public static final Supplier<AttachmentType<Long>> CARCASS = ATTACHMENTS.register("carcass",
            () -> AttachmentType.builder(() -> 0L).serialize(com.mojang.serialization.Codec.LONG.fieldOf("until"), t -> t > 0)
                    .sync(ByteBufCodecs.VAR_LONG).build());

    /** What a player has heard of happenings (SPEC §21.6): "?" marks on their map, synced to them, kept through death. */
    public static final Supplier<AttachmentType<dev.awaken.living.pulse.Rumours.State>> RUMOURS = ATTACHMENTS.register("rumours",
            () -> AttachmentType.builder(() -> dev.awaken.living.pulse.Rumours.State.EMPTY).serialize(dev.awaken.living.pulse.Rumours.State.CODEC.fieldOf("rumours"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(dev.awaken.living.pulse.Rumours.State.CODEC))
                    .build());

    static final net.neoforged.neoforge.registries.DeferredRegister.Entities ENTITIES = net.neoforged.neoforge.registries.DeferredRegister.createEntities(Awaken.MODID);
    /** A person out in the wilds (Phase C). Saved with its chunk; never despawns. */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<dev.awaken.living.wayfarer.Wayfarer>> WAYFARER =
            ENTITIES.registerEntityType("wayfarer", dev.awaken.living.wayfarer.Wayfarer::new, net.minecraft.world.entity.MobCategory.MISC,
                    b -> b.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(8));

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    /** The Wayfarers' camp (SPEC §27.6): a bedroll by the fire, and the pot hung over it. Laid by the camp; no items, no drops. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.awaken.living.wayfarer.Bedroll> BEDROLL = BLOCKS.registerBlock("wayfarer_bedroll",
            dev.awaken.living.wayfarer.Bedroll::new, p -> p.mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                    .sound(net.minecraft.world.level.block.SoundType.WOOL).strength(0.2f).noOcclusion().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.awaken.living.wayfarer.CampPot> CAMP_POT = BLOCKS.registerBlock("camp_pot",
            dev.awaken.living.wayfarer.CampPot::new, p -> p.mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .sound(net.minecraft.world.level.block.SoundType.LANTERN).strength(0.5f).noOcclusion().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));

    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN = sound("living.scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRIT = sound("living.crit");
    public static final DeferredHolder<SoundEvent, SoundEvent> XP_TICK = sound("living.xp_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = sound("living.level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> REGION_ENTER = sound("living.region_enter");
    /** The low bed under a land far above you (DreadFx; tools/art/specs/audio_living.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> DREAD = sound("living.dread");
    // happenings heard from afar (living.client.PulseSound; tools/art/specs/audio_world.py)
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_STAMPEDE = sound("living.tell.stampede");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_WINGS = sound("living.tell.wings");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_HORN = sound("living.tell.horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_TIDE = sound("living.tell.tide");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_BELLS = sound("living.tell.bells");
    // the Rolling Storm: its rain roaring from afar, and its thunder's long roll (living.client.StormSky)
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL_STORM = sound("living.tell.storm");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_ROLL = sound("living.storm.roll");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.feature.Feature>> FEATURES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.FEATURE_TYPE, Awaken.MODID);

    static void register(IEventBus modBus) {
        FEATURES.register("wild_signs", () -> dev.awaken.living.signs.WildSignsFeature.CODEC);
        FEATURES.register("wonders", () -> dev.awaken.living.wonder.WonderFeature.CODEC);
        FEATURES.register("secrets", () -> dev.awaken.living.wonder.SecretFeature.CODEC);
        FEATURES.register(modBus);
        RULES.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
    }
}
