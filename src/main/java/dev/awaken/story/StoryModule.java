package dev.awaken.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.CoreModule;
import dev.awaken.core.api.StoryHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.RankHooks;
import dev.awaken.signal.Signal;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The story: prologue, Revelations, the Rival, the Hollow Court, the Seed and the endings (docs/STORY.md). */
public final class StoryModule {
    private StoryModule() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    public static final Supplier<AttachmentType<StoryProgress>> PROGRESS = ATTACHMENTS.register("story", () -> AttachmentType
            .builder(StoryProgress::new)
            .serialize(StoryProgress.CODEC.fieldOf("story"))
            .copyOnDeath()
            .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodec(StoryProgress.CODEC))
            .build());

    public static final DeferredHolder<EntityType<?>, EntityType<VaelEntity>> VAEL = ENTITIES.registerEntityType(
            "vael", VaelEntity::new, MobCategory.MISC, b -> b.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).noLootTable());

    public static final DeferredItem<SpawnEggItem> VAEL_EGG = ITEMS.registerItem("vael_spawn_egg", SpawnEggItem::new, p -> p.spawnEgg(VAEL.get()));

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(StoryModule::attributes);
        modBus.addListener(StoryModule::payloads);
        modBus.addListener(StoryModule::tab);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(StoryModule::reload);
        bus.addListener(PlayerTickEvent.Post.class, Prologue::onTick);
        bus.addListener(PlayerTickEvent.Post.class, Beats::onTick);
        bus.addListener(BreakBlockEvent.class, Prologue::onBreak);
        bus.addListener(LivingIncomingDamageEvent.class, Prologue::onIncomingDamage);
        bus.addListener(EntityJoinLevelEvent.class, Court::onJoin);
        bus.addListener(LivingDeathEvent.class, Court::onDeath);
        bus.addListener(ServerTickEvent.Post.class, e -> {
            Rival.tick(e.getServer());
            Court.tick(e.getServer());
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Prologue.sync(p);
                sendSeen(p);
            }
        });
        Signal.onAction("story", Prologue::onAnswer);
        RankHooks.onRankUp(Rival::onRankUp);
        Progression.onNewThing(Beats::onNewThing);
        StoryHooks.install(new StoryHooks.State() {
            @Override public boolean gatesCalm(MinecraftServer server) { return StoryWorld.get(server).gatesCalm; }
            @Override public boolean colourRestored(MinecraftServer server) { return StoryWorld.get(server).colourRestored; }
            @Override public int ascension(MinecraftServer server) { return StoryWorld.get(server).ascension; }
        });
        commands();
        StoryTests.register();
    }

    private static void reload(AddServerReloadListenersEvent e) {
        StoryContent.addListeners(e);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(VAEL.get(), VaelEntity.createAttributes().build());
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(CoreModule.TAB_KEY)) e.accept(VAEL_EGG.get());
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(StoryPayloads.OpenEnding.TYPE, StoryPayloads.OpenEnding.STREAM_CODEC);
        r.playToClient(StoryPayloads.Credits.TYPE, StoryPayloads.Credits.STREAM_CODEC);
        r.playToClient(StoryPayloads.Seen.TYPE, StoryPayloads.Seen.STREAM_CODEC);
        r.playToServer(StoryPayloads.ChooseEnding.TYPE, StoryPayloads.ChooseEnding.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) Court.choose(p, msg.which());
        });
    }

    /** Revelation titles/bodies the player has seen, for the client Codex. */
    static void sendSeen(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (String k : Prologue.progress(p).seen) {
            if (!k.startsWith("revelation:")) continue;
            var r = StoryContent.revelation(k.substring("revelation:".length()));
            if (r != null) out.add(r.title() + "\u0001" + r.body());
        }
        AwakenNet.send(p, new StoryPayloads.Seen(out));
    }

    private static void commands() {
        AwakenCommands.add((root, ctx) -> {
            var story = Commands.literal("story");
            story.then(Commands.literal("skip").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                Prologue.skip(p);
                c.getSource().sendSuccess(() -> Component.literal("Prologue skipped. You are awakened."), false);
                return 1;
            }));
            story.then(Commands.literal("status").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                StoryProgress s = Prologue.progress(p);
                StoryWorld w = StoryWorld.get(p.level().getServer());
                c.getSource().sendSuccess(() -> Component.literal("Prologue " + s.prologue + " · seen " + s.seen.size()
                        + " · Sovereigns " + s.sovereignsDefeated.size() + "/8 · Seed open " + Court.seedOpen(p)
                        + " · ending '" + w.ending + "' · ascension " + w.ascension), false);
                return 1;
            }));
            var admin = Commands.literal("admin").requires(AwakenCommands.admin());
            var adminStory = Commands.literal("story");
            adminStory.then(Commands.literal("revelation").then(Commands.argument("key", StringArgumentType.word()).executes(c -> {
                Beats.fire(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "key"));
                return 1;
            })));
            adminStory.then(Commands.literal("rival").then(Commands.argument("rank", StringArgumentType.word()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                String r = StringArgumentType.getString(c, "rank").toUpperCase();
                Prologue.progress(p).seen.remove("rival:" + (r.equals("SOVEREIGN") ? "sovereign" : r.toLowerCase()));
                Rival.onRankUp(p, dev.awaken.progression.Rank.valueOf(r));
                return 1;
            })));
            adminStory.then(Commands.literal("ending").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                Prologue.progress(p).seen.add("seed:defeated");
                Prologue.progress(p).seen.remove("ending:chosen");
                AwakenNet.send(p, new StoryPayloads.OpenEnding());
                return 1;
            }));
            adminStory.then(Commands.literal("prologue").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                StoryProgress s = Prologue.progress(p);
                s.prologue = StoryProgress.Prologue.GLITCHED;
                s.prologueSince = p.level().getGameTime() - 20 * 60 * 10;
                Prologue.openFirstGate(p, s);
                return 1;
            }));
            admin.then(adminStory);
            root.then(story);
            root.then(admin);
        });
    }
}
